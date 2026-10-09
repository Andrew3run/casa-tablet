package dev.casa;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/**
 * La modalita' notte: di sera, quando compare il riposo, lo schermo si abbassa.
 *
 * <h3>Come va</h3>
 *
 * Dentro la fascia scelta (di fabbrica dalle 22 alle 7), <b>trenta secondi dopo
 * che il riposo e' comparso</b> la luminosita' scende. Un tocco toglie il riposo
 * e la luce torna subito quella di prima; quando il riposo ricompare, altri
 * trenta secondi e si riabbassa.
 *
 * Trenta secondi e non subito: chi e' appena passato davanti e ha visto
 * comparire la fotografia la sta ancora guardando, e uno schermo che si spegne
 * sotto gli occhi sembra un guasto.
 *
 * <h3>La luce della finestra, non quella di sistema</h3>
 *
 * Si usa {@code WindowManager.LayoutParams.screenBrightness}, che vale solo
 * finche' la finestra di Casa e' in primo piano. Non chiede nessun permesso
 * (WRITE_SETTINGS vorrebbe una schermata di sistema da accettare a mano), non
 * tocca la luminosita' che l'utente ha scelto nelle Impostazioni di Android, e
 * se Casa si chiude o passa dietro a un'altra app la luce torna quella di prima
 * da sola: non c'e' un modo di lasciare il tablet al buio per sbaglio.
 *
 * <h3>Nessun controllo periodico</h3>
 *
 * Come il riposo, un appuntamento solo: al riposo in scena si guarda se si e'
 * nella fascia; se si', si abbassa e il prossimo appuntamento e' la fine della
 * fascia; se no, e' il suo inizio. Un riposo comparso alle 21:50 si abbassa da
 * solo alle 22:00, e uno rimasto su tutta la notte si rialza alle 7:00.
 */
public final class Notte {

    private static final String TAG = "Casa";

    /** Quanto resta a piena luce il riposo prima di abbassarsi. */
    static final long DOPO_MS = 30000L;

    /** Quanto dura la prova della luminosita' dalla pagina delle impostazioni. */
    static final long PROVA_MS = 3000L;

    /** La luminosita' di notte, in percento. Sotto il cinque questo pannello
     *  e' nero, e uno schermo nero non si distingue da un tablet spento. */
    static final int LUCE_MIN = 5, LUCE_MAX = 60;

    private static final int DA_DI_FABBRICA = 22 * 60, A_DI_FABBRICA = 7 * 60;
    private static final int LUCE_DI_FABBRICA = 15;

    private final Activity finestra;
    private final Handler orologio = new Handler(Looper.getMainLooper());

    private boolean acceso = true;
    /** Inizio e fine della fascia, in minuti dalla mezzanotte. */
    private int da = DA_DI_FABBRICA, a = A_DI_FABBRICA;
    private int luce = LUCE_DI_FABBRICA;

    private boolean riposoInScena;
    private boolean abbassato;

    private final Runnable controllo = new Runnable() {
        @Override public void run() { controlla(); }
    };

    private final Runnable fineProva = new Runnable() {
        @Override public void run() { if (!abbassato) luminosita(-1f); }
    };

    public Notte(Activity finestra) {
        this.finestra = finestra;
        applica(Configurazione.leggi(finestra));
    }

    // ---- configurazione ------------------------------------------------------

    /**
     * Com'e' scritta in {@code casa.json}:
     *
     * <pre>
     *   "notte": { "acceso": true, "da": "22:00", "a": "07:00", "luminosita": 15 }
     * </pre>
     *
     * Le ore sono scritte come ore e non come minuti: e' un file che a volte
     * legge una persona.
     */
    public void applica(JSONObject config) {
        JSONObject n = config == null ? null : config.optJSONObject("notte");
        if (n != null) {
            acceso = n.optBoolean("acceso", true);
            da = minuti(n.optString("da", ""), DA_DI_FABBRICA);
            a = minuti(n.optString("a", ""), A_DI_FABBRICA);
            luce = limita(n.optInt("luminosita", LUCE_DI_FABBRICA));
        }
        // Se il riposo e' gia' in scena - la configurazione arriva dal PC - vale
        // subito, senza aspettare che ricompaia.
        if (riposoInScena) {
            orologio.removeCallbacks(controllo);
            controlla();
        }
    }

    /** Cambia e mette per iscritto. La chiama la pagina delle impostazioni. */
    public void imposta(boolean acceso, int da, int a, int luce) {
        this.acceso = acceso;
        this.da = ((da % 1440) + 1440) % 1440;
        this.a = ((a % 1440) + 1440) % 1440;
        this.luce = limita(luce);
        try {
            JSONObject n = new JSONObject();
            n.put("acceso", acceso);
            n.put("da", ora(this.da));
            n.put("a", ora(this.a));
            n.put("luminosita", this.luce);
            Configurazione.metti(finestra, "notte", n);
        } catch (Exception e) {
            Log.w(TAG, "notte: non ho potuto salvare", e);
        }
        Log.i(TAG, "notte: " + stato());
    }

    public boolean acceso() { return acceso; }
    public int da() { return da; }
    public int a() { return a; }
    public int luce() { return luce; }

    // ---- il riposo -----------------------------------------------------------

    /** Il riposo e' comparso: fra trenta secondi si guarda se e' sera. */
    public void riposoComparso() {
        riposoInScena = true;
        orologio.removeCallbacks(controllo);
        orologio.postDelayed(controllo, DOPO_MS);
    }

    /** Il riposo se n'e' andato - un tocco, una sveglia, Casa dietro un'altra
     *  app: la luce torna subito. */
    public void riposoAndato() {
        riposoInScena = false;
        orologio.removeCallbacks(controllo);
        normale();
    }

    private void controlla() {
        if (!riposoInScena) return;
        int adesso = minutoDiOggi();
        if (acceso && dentro(adesso)) {
            if (!abbassato) Log.i(TAG, "notte: schermo al " + luce + "%");
            abbassato = true;
            orologio.removeCallbacks(fineProva);
            luminosita(luce / 100f);
            orologio.postDelayed(controllo, fraQuanto(a));
        } else {
            normale();
            if (acceso) orologio.postDelayed(controllo, fraQuanto(da));
        }
    }

    private void normale() {
        if (abbassato) Log.i(TAG, "notte: schermo a piena luce");
        abbassato = false;
        luminosita(-1f);
    }

    /**
     * Fa vedere per tre secondi com'e' la luce scelta.
     *
     * Serve perche' « 15% » non dice niente: su questo pannello il 5 e' quasi
     * nero e il 30 quasi giorno, e lo si scopre solo guardandolo.
     */
    public void prova(int percento) {
        if (abbassato) return;
        luminosita(limita(percento) / 100f);
        orologio.removeCallbacks(fineProva);
        orologio.postDelayed(fineProva, PROVA_MS);
    }

    /** Casa se ne va: niente appuntamenti, e la luce di sistema. */
    public void chiudi() {
        orologio.removeCallbacksAndMessages(null);
        riposoInScena = false;
        abbassato = false;
        luminosita(-1f);
    }

    public String stato() {
        return (acceso ? "accesa" : "spenta") + ", dalle " + ora(da) + " alle " + ora(a)
                + " al " + luce + "%" + (abbassato ? ", schermo abbassato" : "")
                + (riposoInScena ? ", riposo in scena" : "");
    }

    // ---- in basso ------------------------------------------------------------

    /** -1 vuol dire « quella del sistema ». */
    private void luminosita(float valore) {
        try {
            WindowManager.LayoutParams lp = finestra.getWindow().getAttributes();
            if (lp.screenBrightness == valore) return;
            lp.screenBrightness = valore < 0f
                    ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : valore;
            finestra.getWindow().setAttributes(lp);
        } catch (Exception e) {
            Log.w(TAG, "notte: non riesco a cambiare la luce", e);
        }
    }

    /** La fascia puo' passare la mezzanotte, ed e' il caso normale. */
    boolean dentro(int minuto) {
        if (da == a) return false;
        if (da < a) return minuto >= da && minuto < a;
        return minuto >= da || minuto < a;
    }

    private static int minutoDiOggi() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** Millisecondi da adesso al prossimo minuto {@code minuto} della
     *  giornata. Mai zero: un appuntamento a zero girerebbe in tondo. */
    private static long fraQuanto(int minuto) {
        Calendar c = Calendar.getInstance();
        long adesso = ((c.get(Calendar.HOUR_OF_DAY) * 60L + c.get(Calendar.MINUTE)) * 60L
                + c.get(Calendar.SECOND)) * 1000L + c.get(Calendar.MILLISECOND);
        long giorno = 24L * 3600L * 1000L;
        long ms = ((minuto * 60000L - adesso) % giorno + giorno) % giorno;
        return Math.max(1000L, ms);
    }

    private static int limita(int percento) {
        return Math.max(LUCE_MIN, Math.min(LUCE_MAX, percento));
    }

    static String ora(int minuti) {
        return String.format(Locale.ITALIAN, "%02d:%02d", minuti / 60, minuti % 60);
    }

    private static int minuti(String ora, int diFabbrica) {
        try {
            int due = ora.indexOf(':');
            if (due < 0) return diFabbrica;
            int h = Integer.parseInt(ora.substring(0, due).trim());
            int m = Integer.parseInt(ora.substring(due + 1).trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return diFabbrica;
            return h * 60 + m;
        } catch (Exception e) {
            return diFabbrica;
        }
    }
}
