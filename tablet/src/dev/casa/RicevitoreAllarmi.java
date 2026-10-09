package dev.casa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Dove arrivano gli allarmi, e dove si rimettono in piedi quando il sistema li
 * ha buttati via.
 *
 * <h3>Le due volte in cui gli allarmi spariscono</h3>
 *
 * <b>BOOT_COMPLETED.</b> AlarmManager tiene gli allarmi in memoria, non su
 * disco: dopo un riavvio non ne resta nessuno. Questo lo sanno tutti e lo
 * scrivono tutti.
 *
 * <b>MY_PACKAGE_REPLACED.</b> Questo e' quello che ci si dimentica, ed e' il
 * peggiore: reinstallare l'app cancella i suoi allarmi <i>in silenzio</i>. In
 * sviluppo vuol dire che ogni {@code build.ps1 -Install} porta via tutte le
 * sveglie senza dire niente, e ci si accorge la mattina dopo - quando non ci si
 * sveglia, e si da' la colpa al codice della sveglia invece che alla build.
 *
 * In tutti e due i casi il lavoro e' lo stesso: costruire un {@link Orologio},
 * che legge {@code orologio.json} e nel costruttore rimette gli allarmi.
 *
 * <h3>Quando invece scatta davvero</h3>
 *
 * Casa e' la Home: sta in piedi praticamente sempre, e l'{@link Orologio} e'
 * uno per processo - quindi quello che si trova qui e' gia' quello che ha lo
 * schermo in mano e la voce. Se invece nessuno lo guarda (Android ha fatto
 * fuori il processo, o e' il primo avvio) l'Orologio nasce qui, comincia a
 * suonare subito, e si riaccende MainActivity: quando l'interfaccia arriva
 * trova lo stesso allarme gia' in corso, non un secondo.
 */
public class RicevitoreAllarmi extends BroadcastReceiver {

    private static final String TAG = "Casa.Allarmi";

    @Override
    public void onReceive(Context contesto, Intent intento) {
        String azione = intento.getAction();
        if (azione == null) return;

        if (Orologio.AZIONE.equals(azione)) {
            int id = intento.getIntExtra(Orologio.EXTRA_ID, -1);
            if (id < 0) return;

            // Orologio.di() e' uno per processo: se l'app e' viva questo e'
            // proprio il suo, con lo schermo e la voce gia' attaccati. Se non
            // lo e', nasce qui e comincia a suonare subito, senza aspettare il
            // tempo di avvio dell'interfaccia - e quando l'interfaccia arriva
            // trova lo stesso Orologio che sta gia' suonando.
            Orologio orologio = Orologio.di(contesto);
            boolean cera = orologio.haQualcunoCheGuarda();
            orologio.scatta(id);
            if (cera) return;

            Log.i(TAG, "allarme " + id + " senza schermo in ascolto: riaccendo Casa");
            Intent apri = new Intent(contesto, MainActivity.class);
            apri.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                contesto.startActivity(apri);
            } catch (Exception e) {
                Log.w(TAG, "non riesco a riaccendere Casa", e);
            }
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(azione)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(azione)
                || Intent.ACTION_TIME_CHANGED.equals(azione)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(azione)) {
            // Anche l'ora cambiata: un allarme e' un istante assoluto, e
            // spostare l'orologio del tablet di due ore sposterebbe la sveglia
            // delle sette alle cinque. Si ricalcola da ora e giorno, che sono
            // quello che una persona ha davvero chiesto.
            Orologio.di(contesto).riprogramma();
            Log.i(TAG, "allarmi rimessi in piedi dopo " + azione);
        }
    }
}
