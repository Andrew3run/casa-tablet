package dev.casa;

import android.media.audiofx.Visualizer;
import android.util.Log;

/**
 * Quanto forte sta suonando, banda per banda.
 *
 * <b>Sono i livelli veri, non un'animazione che finge.</b> Delle barre che si
 * muovono da sole mentre esce del suono qualunque sono una decorazione, e si
 * riconoscono subito: continuano a ballare identiche quando la stazione va in
 * pubblicita' o quando il brano finisce in dissolvenza. Qui la fonte e'
 * {@link Visualizer}, che legge lo spettro di quello che sta uscendo davvero.
 *
 * <b>Agganciato alla sessione di chi suona, e non al mescolatore.</b> La strada
 * che si trova scritta dappertutto e' {@code new Visualizer(0)}, cioe' la
 * sessione zero, che e' il mescolatore di uscita del sistema: una fonte sola
 * per la radio, per la musica di Casa e per Netflix. Su questo tablet <b>non
 * funziona</b>, ed e' stato misurato: {@code AudioFlinger could not create
 * effect, status: -1}, che diventa un errore -3 e in Java una
 * {@code RuntimeException} che parla d'altro. Non e' un permesso mancante -
 * provato anche con {@code MODIFY_AUDIO_SETTINGS}, che pure serve e adesso c'e'
 * - e' il mescolatore di questo MT6580 che non si lascia agganciare.
 *
 * La sessione di un MediaPlayer o di un AudioTrack invece si'. Costa sapere chi
 * sta suonando - {@link Radio#sessioneAudio()},
 * {@link Musica#sessioneAudio()} - e in cambio le barre si muovono. Quello che
 * si perde e' il suono delle <i>altre</i> app: mentre suona Netflix la sessione
 * e' sua e non ce la da' nessuno, e le colonne restano a riposo. E' la risposta
 * onesta - quel suono non lo stiamo facendo noi - ed e' meglio di barre che
 * ballano su una musica che non stanno sentendo.
 *
 * <b>Si legge quando si disegna, e non c'e' nessun thread.</b> Visualizer ha un
 * ascoltatore con la sua callback su un altro thread - il modo che si trova
 * scritto dappertutto - ed e' esattamente quello che qui non serve: le barre le
 * guarda solo chi disegna, venti volte al secondo, e {@code getFft} si puo'
 * chiamare quando si vuole. Niente thread, niente listener da sganciare, niente
 * dati che arrivano mentre la Home non c'e' piu'.
 *
 * <b>Il permesso c'e' gia'.</b> Il visualizzatore vuole {@code RECORD_AUDIO},
 * che Casa chiede per il microfono. Non fa la stessa cosa - non ascolta la
 * stanza, legge quello che il sistema sta mandando all'altoparlante - ma per
 * Android e' un ascolto, e ha ragione a chiederlo.
 *
 * <b>E se una sessione non si lascia agganciare, non si insiste su quella.</b>
 * Si segna qual era e non la si riprova - venti tentativi al secondo per sedici
 * ore sarebbero venti eccezioni al secondo - ma la prossima sessione si prova
 * lo stesso: e' un'altra stazione, un altro lettore, un'altra storia. Le barre
 * restano ferme sulla linea di riposo, che e' una schermata onesta, non una
 * rotta.
 */
public final class Livelli {

    /** Sotto {@code Casa} come tutto il resto: il perche' sta su
     *  {@link MainActivity#TAG}. */
    private static final String TAG = MainActivity.TAG;

    /**
     * Quante colonne.
     *
     * Ventiquattro. Erano sedici, cioe' colonne larghe un centimetro: un
     * livello disegnato con dei mattoni, che da fermo era una fila di bolli e
     * in movimento sembrava un gioco da telefono. Ventiquattro fili sottili in
     * mezza scheda si leggono come <b>una forma sola che ondeggia</b>, che e'
     * quello che uno spettro dovrebbe essere, e non come ventiquattro cose che
     * salgono e scendono ognuna per conto suo.
     */
    public static final int BANDE = 24;

    /**
     * Quanti campioni si fa dare dal sistema.
     *
     * <b>Non il minimo.</b> Il minimo che il framework accetta e' 128, cioe'
     * sessantaquattro righe di spettro larghe trecentoquaranta hertz l'una: a
     * quella grossolanita' tutta la musica sta nella prima riga e le altre
     * quindici sono ferme. Con 512 le righe sono larghe ottantasei hertz, e le
     * sedici colonne hanno ognuna qualcosa da dire. Costa mezzo kilobyte letto
     * venti volte al secondo.
     */
    private static final int CAMPIONI = 512;

    /** Quanto in fretta una colonna sale, e quanto piano scende. Salgono di
     *  scatto perche' un colpo di batteria e' un istante; scendono piano
     *  perche' una colonna che torna giu' alla stessa velocita' con cui e'
     *  salita non si vede - lampeggia e basta. */
    private static final float SALITA   = 0.60f;
    private static final float RILASCIO = 0.055f;

    private Visualizer visual;
    private byte[] spettro;

    /** La sessione a cui siamo agganciati, e quella che ha rifiutato. Zero vuol
     *  dire nessuna delle due. */
    private int sessione;
    private int sessioneRifiutata;

    private final float[] livello = new float[BANDE];
    private final int[] daRiga = new int[BANDE];
    private final int[] aRiga  = new int[BANDE];

    /**
     * Aggancia il visualizzatore alla sessione di chi sta suonando.
     *
     * Chiamabile a ogni fotogramma: se e' gia' agganciato a quella sessione non
     * fa niente, se la sessione e' cambiata si stacca e si riaggancia, e se
     * quella sessione ha gia' rifiutato una volta non ci riprova.
     *
     * @param quale la sessione, o zero quando non c'e' niente da agganciare
     */
    public void accendi(int quale) {
        if (quale == 0) { spegni(); return; }
        if (visual != null && sessione == quale) return;
        if (quale == sessioneRifiutata) return;
        spegni();
        try {
            Visualizer v = new Visualizer(quale);
            int[] limiti = Visualizer.getCaptureSizeRange();
            int quanti = Math.max(limiti[0], Math.min(CAMPIONI, limiti[1]));
            v.setCaptureSize(quanti);
            v.setEnabled(true);
            spettro = new byte[quanti];
            dividiInBande(quanti / 2);
            visual = v;
            sessione = quale;
            Log.i(TAG, "livelli: agganciato alla sessione " + quale
                    + " su " + quanti + " campioni");
        } catch (Throwable niente) {
            sessioneRifiutata = quale;
            Log.w(TAG, "livelli: la sessione " + quale
                    + " non si lascia agganciare, le colonne restano giu'", niente);
        }
    }

    /**
     * Lo spegne e lo restituisce al sistema.
     *
     * Va chiamato appena non serve - la Home esce di scena, la musica si ferma,
     * il microfono si apre - e non e' pignoleria: un effetto audio acceso resta
     * agganciato al mescolatore di uscita, e questo apparecchio sta acceso
     * sedici ore al giorno davanti a una schermata che per la maggior parte del
     * tempo non sta suonando niente.
     */
    public void spegni() {
        Visualizer v = visual;
        visual = null;
        spettro = null;
        sessione = 0;
        if (v == null) return;
        try {
            v.setEnabled(false);
            v.release();
        } catch (Throwable ignorata) {
        }
    }

    public boolean acceso() { return visual != null; }

    /**
     * Rilegge lo spettro e muove le colonne di un passo.
     *
     * Da chiamare una volta per fotogramma, mentre si disegna. Se non c'e'
     * niente da leggere le colonne scendono comunque: una barra che resta
     * congelata a mezza altezza quando la musica si ferma e' peggio di una
     * barra che non c'e'.
     */
    public void aggiorna() {
        Visualizer v = visual;
        byte[] s = spettro;
        if (v == null || s == null) { cala(); return; }

        int esito;
        try {
            esito = v.getFft(s);
        } catch (Throwable perso) {
            esito = Visualizer.ERROR;
        }
        if (esito != Visualizer.SUCCESS) { cala(); return; }

        for (int b = 0; b < BANDE; b++) {
            float somma = 0f;
            int quante = 0;
            for (int k = daRiga[b]; k <= aRiga[b]; k++) {
                // Lo spettro arriva come coppie parte reale / parte
                // immaginaria, con byte con segno: il modulo e' l'ipotenusa.
                float re = s[k * 2];
                float im = s[k * 2 + 1];
                somma += (float) Math.sqrt(re * re + im * im);
                quante++;
            }
            float media = quante > 0 ? somma / quante : 0f;

            // Due correzioni, e servono tutte e due.
            //
            // La radice quadrata perche' l'orecchio sente i decibel e non i
            // volt: senza, una colonna passa da zero a fondo scala nel giro di
            // niente e sta giu' tutto il resto del tempo.
            //
            // Il peso che cresce con la frequenza perche' nella musica
            // l'energia sta quasi tutta sotto i cinquecento hertz: senza, le
            // prime due colonne sono sempre in cima e le altre quattordici
            // sempre in fondo, che e' il disegno di un dato solo.
            float v0 = (float) Math.sqrt(media / 26f) * peso(b);
            if (v0 > 1f) v0 = 1f;

            livello[b] = v0 > livello[b]
                    ? livello[b] + (v0 - livello[b]) * SALITA
                    : Math.max(v0, livello[b] - RILASCIO);
        }
    }

    /** Quanto e' alta la colonna, da zero a uno. */
    public float livello(int banda) {
        return banda >= 0 && banda < BANDE ? livello[banda] : 0f;
    }

    /** true se c'e' ancora qualcosa che si muove: serve a smettere di chiedere
     *  fotogrammi quando le colonne sono tutte a terra. */
    public boolean qualcosaSiMuove() {
        for (int i = 0; i < BANDE; i++) if (livello[i] > 0.004f) return true;
        return false;
    }

    private void cala() {
        for (int i = 0; i < BANDE; i++) {
            livello[i] = Math.max(0f, livello[i] - RILASCIO);
        }
    }

    private static float peso(int banda) {
        return 0.62f + 1.05f * banda / (float) (BANDE - 1);
    }

    /**
     * Spartisce le righe dello spettro fra le colonne, <b>a ottave</b>.
     *
     * In parti uguali non funziona: fra la prima riga e la decima ci sono tre
     * ottave, fra la centesima e la centodecima nemmeno un semitono. Una
     * progressione geometrica da' a ogni colonna la stessa fetta di tastiera,
     * che e' come si sente la musica e come si guarda un equalizzatore.
     */
    private void dividiInBande(int righe) {
        float ultima = Math.max(2, righe - 1);
        float passo = (float) Math.pow(ultima, 1.0 / BANDE);
        float x = 1f;
        for (int b = 0; b < BANDE; b++) {
            daRiga[b] = Math.max(1, (int) x);
            x *= passo;
            aRiga[b] = Math.max(daRiga[b], Math.min((int) x - 1, (int) ultima));
        }
    }
}
