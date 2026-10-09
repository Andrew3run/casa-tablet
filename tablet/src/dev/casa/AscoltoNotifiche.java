package dev.casa;

import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.HashSet;

/**
 * L'ascoltatore di notifiche: la prova d'identita' per le sessioni media, e lo
 * spazzino dell'area notifiche.
 *
 * <h3>Perche' esiste</h3>
 *
 * {@link android.media.session.MediaSessionManager#getActiveSessions} pretende
 * che chi chiama sia un ascoltatore di notifiche abilitato, e usa il nome di
 * questo servizio come prova d'identita'. E' l'unica strada su API 24 - il
 * permesso {@code MEDIA_CONTENT_CONTROL} e' riservato alle app di sistema, e
 * nemmeno il device owner se lo puo' concedere. Il permesso si concede da adb,
 * una volta sola, con {@code tools/permesso-notifiche.ps1} - il nome che quello
 * script scrive in {@code enabled_notification_listeners} e' esattamente
 * {@code dev.casa/dev.casa.AscoltoNotifiche}, quindi <b>questa classe non si
 * rinomina</b> senza cambiare anche lo script.
 *
 * <h3>Nessuna notifica deve arrivare</h3>
 *
 * Fino all'11 settembre 2026 qui dentro non c'era niente, di proposito: il
 * servizio serviva a esistere. Poi l'area notifiche del tablet si e' riempita
 * delle « storie » di Discover che l'app Google spinge da sola - notizie che
 * nessuno aveva chiesto, su un apparecchio che le notizie le mostra gia' dove
 * servono - e la regola e' diventata: <b>nell'area notifiche non arriva
 * niente</b>, di nessuna app.
 *
 * La tiene in due modi, e servono tutti e due:
 *
 * <ul>
 *   <li><b>MainActivity chiude l'area</b>, da device owner
 *       ({@code setStatusBarDisabled}): niente icone, niente tendina, niente
 *       finestrelle - e su Android 7 lo stesso segnale arriva al gestore delle
 *       notifiche e ne spegne suoni e vibrazioni. E' quello che si vede;
 *   <li><b>questo servizio le cancella</b> appena arrivano. Senza, dietro
 *       l'area chiusa si accumulerebbero: il giorno che la si riapre per una
 *       manutenzione ci si troverebbero tre settimane di Discover.
 * </ul>
 *
 * Si cancella solo quello che si puo' cancellare: le notifiche fisse del
 * sistema - « debug USB collegato », un servizio in primo piano - il gestore
 * non le lascia togliere a nessuno, ed e' giusto. Quelle le nasconde l'area
 * chiusa.
 *
 * <b>Le sessioni media non c'entrano</b>: vivono per conto loro, e cancellare
 * la notifica di un lettore non ferma la musica ne' toglie la sessione a
 * {@link Riproduzione}. Anzi, le notifiche dei lettori sono fisse, e restano.
 *
 * Con l'area riaperta per manutenzione ({@code casa_area_notifiche = 1}, vedi
 * MainActivity) si smette anche di cancellare: chi l'ha riaperta vuole vedere.
 */
public class AscoltoNotifiche extends NotificationListenerService {

    private static final String TAG = MainActivity.TAG;

    /** Di chi si e' gia' detto nel registro: una riga per app e per avvio,
     *  non una per notifica - l'app Google ne manda anche dieci al giorno. */
    private final HashSet<String> giaDetti = new HashSet<String>();

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (areaAperta()) return;
        // Quelle arrivate prima che Casa ci fosse: all'accensione il sistema
        // ne ha spesso gia' qualcuna in fila.
        try {
            cancelAllNotifications();
        } catch (Exception e) {
            Log.w(TAG, "notifiche: non ho potuto toglierle", e);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !sbn.isClearable() || areaAperta()) return;
        try {
            cancelNotification(sbn.getKey());
            if (giaDetti.add(sbn.getPackageName())) {
                Log.i(TAG, "notifiche: tolte quelle di " + sbn.getPackageName());
            }
        } catch (Exception e) {
            Log.w(TAG, "notifiche: non ho potuto toglierne una di " + sbn.getPackageName(), e);
        }
    }

    private boolean areaAperta() {
        return Settings.Global.getInt(getContentResolver(), "casa_area_notifiche", 0) == 1;
    }
}
