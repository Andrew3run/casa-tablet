package dev.casa;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;

/**
 * Il suono di quando scade qualcosa.
 *
 * <h3>Sale piano</h3>
 *
 * Una sveglia che parte al massimo in una stanza silenziosa non sveglia: fa
 * saltare. E su un apparecchio appeso al muro, a due metri dal letto, il
 * volume che serve alle sette del mattino e' molto meno di quello che il
 * sistema ha in memoria dalla sera prima. Quindi si parte a un decimo e si sale
 * fino al pieno in mezzo minuto, un gradino al secondo. Chi si sveglia al primo
 * gradino non sente mai gli altri.
 *
 * La rampa e' sul {@link MediaPlayer}, non sul volume di sistema: alzare
 * {@code STREAM_ALARM} vorrebbe dire lasciarlo alzato per la prossima volta -
 * e la prossima volta partirebbe al massimo, che e' esattamente la cosa che si
 * sta evitando.
 *
 * <h3>STREAM_ALARM e non STREAM_MUSIC</h3>
 *
 * Sono due manopole diverse. La radio abbassata a zero la sera non deve poter
 * zittire la sveglia della mattina, e la sordina che Casa mette sulla radio
 * quando si apre il microfono non deve poter zittire niente di tutto questo.
 *
 * <h3>Il timer suona diverso</h3>
 *
 * Un timer che scade in cucina e' un avviso, non una levataccia: parte gia' a
 * meta' volume, non sale, e non fa vibrare niente. Chi l'ha messo e' nella
 * stanza accanto, non sta dormendo.
 */
public final class Suoneria {

    private static final String TAG = "Casa.Suoneria";

    /** Da qui parte la rampa della sveglia, e da qui parte e resta il timer. */
    private static final float VOLUME_INIZIALE = 0.10f;
    private static final float VOLUME_TIMER    = 0.55f;

    /** Un gradino al secondo per mezzo minuto. */
    private static final int PASSI = 30;
    private static final int PASSO_MS = 1000;

    /**
     * Dopo tanto smette da sola.
     *
     * Non e' una gentilezza: e' che questo apparecchio non ha una batteria che
     * conti e sta attaccato al muro. Una sveglia che suona per sempre perche'
     * non c'era nessuno in casa e' un apparecchio che urla a una stanza vuota
     * per otto ore, e i vicini se ne accorgono prima del padrone.
     */
    private static final int SMETTE_DA_SOLA_MS = 3 * 60 * 1000;

    /** Vibrazione: mezzo secondo si', mezzo no. */
    private static final long[] BATTITO = { 0, 500, 500 };

    private final Context contesto;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private MediaPlayer lettore;
    private Vibrator vibrazione;
    private int passo;

    public Suoneria(Context c) {
        contesto = c.getApplicationContext();
    }

    public void suona(final boolean sveglia) {
        taci();
        Uri suono = RingtoneManager.getDefaultUri(
                sveglia ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION);
        if (suono == null) suono = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (suono == null) {
            // Un ROM senza nemmeno una suoneria di serie: non e' un motivo per
            // far fallire la sveglia. Restano la schermata e la voce, che sono
            // gia' due terzi dell'avviso.
            Log.w(TAG, "nessuna suoneria di sistema: resta la schermata");
            return;
        }
        try {
            lettore = new MediaPlayer();
            lettore.setAudioStreamType(AudioManager.STREAM_ALARM);
            lettore.setDataSource(contesto, suono);
            lettore.setLooping(true);
            lettore.prepare();
            float partenza = sveglia ? VOLUME_INIZIALE : VOLUME_TIMER;
            lettore.setVolume(partenza, partenza);
            lettore.start();
        } catch (Exception e) {
            Log.w(TAG, "non riesco a suonare", e);
            taci();
            return;
        }

        if (sveglia) {
            passo = 0;
            ui.postDelayed(rampa, PASSO_MS);
            vibra();
        }
        ui.postDelayed(basta, SMETTE_DA_SOLA_MS);
    }

    private final Runnable rampa = new Runnable() {
        @Override public void run() {
            if (lettore == null) return;
            passo++;
            float v = VOLUME_INIZIALE + (1f - VOLUME_INIZIALE) * (passo / (float) PASSI);
            if (v > 1f) v = 1f;
            try {
                lettore.setVolume(v, v);
            } catch (Exception morto) {
                return;
            }
            if (passo < PASSI) ui.postDelayed(this, PASSO_MS);
        }
    };

    private final Runnable basta = new Runnable() {
        @Override public void run() {
            Log.i(TAG, "nessuno ha risposto: smetto da sola");
            taci();
        }
    };

    private void vibra() {
        try {
            vibrazione = (Vibrator) contesto.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrazione != null && vibrazione.hasVibrator()) vibrazione.vibrate(BATTITO, 0);
        } catch (Exception e) {
            vibrazione = null;
        }
    }

    public void taci() {
        ui.removeCallbacks(rampa);
        ui.removeCallbacks(basta);
        if (lettore != null) {
            try { lettore.stop(); } catch (Exception ignorata) { }
            try { lettore.release(); } catch (Exception ignorata) { }
            lettore = null;
        }
        if (vibrazione != null) {
            try { vibrazione.cancel(); } catch (Exception ignorata) { }
            vibrazione = null;
        }
    }
}
