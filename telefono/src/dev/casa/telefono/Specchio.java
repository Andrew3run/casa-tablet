package dev.casa.telefono;

import android.app.AlarmManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * La sveglia del telefono, riflessa sul tablet.
 *
 * Il telefono sa gia' qual e' la sua prossima sveglia, qualunque app l'abbia
 * messa: {@link AlarmManager#getNextAlarmClock()}. Qui la si manda al tablet,
 * che ne tiene una copia sola e la fa suonare anche lui.
 *
 * <b>Si manda solo quando cambia</b>, e « cambia » vuol dire diversa
 * dall'ultima arrivata davvero: se la rete non c'era, il valore vecchio resta
 * quello inviato e il job riprova.
 *
 * Due lavori: uno <b>subito</b>, quando la sveglia cambia, con qualsiasi rete;
 * e uno <b>ogni mezz'ora</b> col Wi-Fi, che copre il caso in cui la sveglia
 * l'hai cambiata fuori casa - il tablet la riceve appena rientri.
 */
final class Specchio {

    private static final String TAG = "CasaTelefono";

    static final int LAVORO_SUBITO = 1;
    static final int LAVORO_PERIODICO = 2;

    private Specchio() {}

    /** La prossima sveglia del telefono, 0 se non ce n'e'. */
    static long prossima(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return 0L;
        AlarmManager.AlarmClockInfo info = am.getNextAlarmClock();
        return info == null ? 0L : info.getTriggerTime();
    }

    /** Rimette in fila i lavori, o li toglie se lo specchio e' spento (dopo
     *  un'ultima spedizione con quando=0, che al tablet dice di toglierla). */
    static void programma(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        ComponentName chi = new ComponentName(c, LavoroSveglia.class);
        js.schedule(new JobInfo.Builder(LAVORO_SUBITO, chi)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(60000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build());
        if (new Tablet(c).segueLaSveglia()) {
            js.schedule(new JobInfo.Builder(LAVORO_PERIODICO, chi)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setPeriodic(30 * 60 * 1000L)
                    .setPersisted(false)
                    .build());
        } else {
            js.cancel(LAVORO_PERIODICO);
        }
    }

    /**
     * Bloccante: manda la sveglia se serve. Torna vero se non c'e' altro da
     * fare (inviata, o gia' allineata), falso se il tablet non si e' trovato.
     */
    static boolean allinea(Context c) {
        Tablet t = new Tablet(c);
        if (!t.abbinato()) return true;               // niente a cui mandarla
        long quando = t.segueLaSveglia() ? prossima(c) : 0L;
        if (quando == t.svegliaInviata()) return true;
        if (!t.trova(c, 5000)) return false;
        try {
            Tablet.Risposta r = t.post("/sveglia/telefono", Tablet.json("quando", quando));
            if (r.ok()) {
                t.setSvegliaInviata(quando);
                Log.i(TAG, "sveglia allineata: " + quando);
                return true;
            }
            // Un rifiuto non si risolve riprovando: il tablet non ci conosce.
            Log.w(TAG, "il tablet rifiuta la sveglia: " + r.errore());
            return true;
        } catch (Tablet.Irraggiungibile e) {
            return false;
        }
    }
}
