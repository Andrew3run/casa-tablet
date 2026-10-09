package dev.casa;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

/**
 * Il microfono acceso sempre, e quello che ne esce.
 *
 * <h3>Sempre acceso, ma niente esce di qui</h3>
 *
 * E' l'opposto dell'ascolto continuo che era stato provato e tolto (vedi
 * {@link Voce}): quello riavviava il riconoscitore di Google, faceva « bip » e
 * mandava ai suoi server tutto il parlato della stanza. Questo apre il
 * microfono e basta: nessun tono, e <b>nessun byte lascia il tablet</b>. La
 * rete la tocca solo il riconoscitore, e solo dopo che la parola e' agganciata.
 *
 * <h3>Non tutto quello che si sente vale un confronto</h3>
 *
 * Confrontare in continuazione costerebbe un core. Davanti c'e' quindi un
 * <b>rilevatore di voce</b> che guarda solo l'energia: finche' quello che
 * arriva sta al livello del rumore di fondo, non si calcola niente. In una
 * cucina il parlato e' forse un decimo del tempo, e nove decimi del lavoro non
 * si fanno.
 *
 * Il rumore di fondo non e' un numero fisso - il frigorifero che parte, la
 * cappa, la finestra aperta - quindi si insegue: <b>giu' in fretta, su
 * piano</b>. Al contrario, una voce che comincia alzerebbe il pavimento fino a
 * coprirsi da sola.
 *
 * <h3>La finestra scorre, non aspetta la fine</h3>
 *
 * Aspettare il silenzio dopo la parola funziona solo se chi parla si ferma. Ma
 * si dice « Hey Home, accendi la luce » tutto attaccato, e li' il silenzio
 * arriva dopo « luce »: il pezzo da confrontare sarebbe lungo tre volte la
 * parola e non aggancerebbe mai.
 *
 * Quindi, <b>mentre</b> qualcuno parla, ogni {@link #PASSO_PROVA_MS} si
 * consegna l'ultimo tratto lungo quanto la parola. Chi ascolta lo confronta.
 * E' come lavora un riconoscitore di parola chiave vero: non cerca la fine
 * della frase, cerca la parola dentro un flusso.
 *
 * <h3>L'anello, e perche' il pezzo consegnato non si tiene</h3>
 *
 * Gli ultimi tre secondi stanno in un buffer circolare. Quando si consegna una
 * finestra, si consegna <b>l'array di lavoro dell'orecchio</b>, valido solo per
 * la durata della chiamata: dieci consegne al secondo, per sempre, ognuna con
 * il suo array da 32 KB, sarebbero trecento kilobyte al secondo dati in pasto
 * al raccoglitore di memoria. Chi ha bisogno di tenerselo se lo copia, e lo sa.
 */
public final class Orecchio {

    /** Sotto {@code Casa} come tutto il resto, col nome del pezzo nel
     *  messaggio: il perche' sta su {@link MainActivity#TAG}. */
    private static final String TAG = MainActivity.TAG;

    /** Il blocco di lettura: 20 ms. E' anche il passo del rilevatore di voce e
     *  del livello che si vede a schermo. */
    private static final int BLOCCO = Onda.HZ / 50;

    /** Quanto suono si tiene indietro. Tre secondi bastano per la finestra
     *  scorrevole piu' il preavvio, e sono 96 KB. */
    private static final int ANELLO = Onda.HZ * 3;

    /** Ogni quanto si consegna una finestra mentre qualcuno parla. */
    private static final int PASSO_PROVA_MS = 120;

    /** Quanto si prende <b>prima</b> dell'istante in cui la voce e' stata
     *  riconosciuta. Il rilevatore ha bisogno di tre blocchi per essere sicuro,
     *  e in quei sessanta millisecondi c'e' l'attacco della « H » di "Hey" -
     *  cioe' esattamente il pezzo che distingue la parola da un'altra. */
    private static final int PREAVVIO_MS = 220;

    /** Quanti blocchi sopra il pavimento per dire « qualcuno parla », e quanti
     *  sotto per dire « ha finito ». Asimmetrici di proposito: attaccare presto
     *  costa un confronto in piu', staccare presto taglia le parole a meta'. */
    private static final int BLOCCHI_ATTACCO = 3;      // 60 ms

    /**
     * Quanti blocchi di silenzio prima di dire « ha finito ».
     *
     * <b>Erano 320 ms, e non bastavano.</b> Le finestre si consegnano solo
     * mentre il rilevatore di voce dice che qualcuno parla, e ognuna contiene
     * <b>l'ultimo secondo</b>: quelle consegnate mentre la parola e' ancora a
     * meta' contengono meta' parola e meta' di quello che c'era prima, e non
     * assomigliano a niente. Le finestre buone - quelle con dentro la parola
     * INTERA - sono solo quelle consegnate DOPO che si e' finito di parlare.
     *
     * Con 320 ms di coda ne uscivano tre o quattro; chiederne quattro di fila
     * sopra soglia voleva dire non sbagliarne nemmeno una. Con 560 ms ne
     * escono sette o otto, e chiederne due o tre diventa una richiesta
     * ragionevole invece di un colpo di fortuna.
     *
     * Costa mezzo secondo di microfono aperto in piu' dopo ogni frase, che non
     * si sente in nessun modo.
     */
    private static final int BLOCCHI_CODA    = 28;     // 560 ms

    /** Quanto deve stare sopra il rumore di fondo per essere voce. */
    private static final float SOPRA_IL_FONDO = 3.2f;

    /** Sotto questo non e' voce nemmeno in una stanza muta: e' il rumore del
     *  convertitore. Su 32767 di fondo scala sono circa -52 dB. */
    private static final float MINIMO_ASSOLUTO = 80f;

    /** Cosa esce dall'orecchio. Tutto arriva sul thread del microfono. */
    public interface Ascolto {
        /**
         * Un tratto da confrontare con la parola.
         *
         * L'array <b>e' dell'orecchio</b> e vale solo dentro questa chiamata:
         * chi lo vuole tenere lo copia.
         */
        void suFinestra(short[] suono, int quanti);

        /** Il livello, da zero a uno, cinquanta volte al secondo. Serve al
         *  pannello: una barra ferma mentre si parla vuol dire microfono muto,
         *  e senza barra non lo si scopre. */
        void suLivello(float livello, boolean voce);
    }

    /** Una registrazione a comando: il pannello, o il PC. */
    public interface Presa {
        /** Finita. L'array e' una copia, si puo' tenere. */
        void suPresa(short[] suono, int quanti);
        /** Quanto manca, in millisecondi, e quanto forte si sta parlando. */
        void suAvanzamento(int rimastiMs, float livello, float picco);
    }

    private final Context contesto;
    private final Ascolto ascolto;

    private final short[] anello = new short[ANELLO];
    /**
     * Quanti campioni sono passati in tutto.
     *
     * <b>long, e non int.</b> Il microfono di questo tablet e' aperto sempre:
     * a sedicimila campioni al secondo un {@code int} finisce dopo
     * <b>trentasette ore</b>, e diventa negativo. Da li' in poi
     * {@code scritti % ANELLO} e' negativo, cioe' un indice fuori
     * dall'anello, cioe' {@code System.arraycopy} che solleva. Non e' un caso
     * limite da manuale: e' il secondo giorno di funzionamento.
     */
    private long scritti;

    /** L'array di lavoro con cui si consegnano le finestre. Grande quanto il
     *  piu' lungo pezzo che si consegnera' mai. */
    private final short[] lavoro = new short[Onda.HZ * 2];

    private volatile boolean acceso;
    private volatile boolean inPausa;
    private Thread thread;
    private AudioRecord microfono;
    private AcousticEchoCanceler eco;
    private NoiseSuppressor rumore;
    private AutomaticGainControl guadagno;

    /** Quanto dura la finestra da consegnare: la mette chi ha i modelli. */
    private volatile int finestraMs = 900;

    /**
     * Se la finestra va consegnata <b>sempre intera</b>, anche quando il
     * parlato e' cominciato un attimo fa.
     *
     * <b>Perche' esiste questa scelta.</b> Di norma la finestra si accorcia a
     * quanto parlato c'e' stato finora: allungarla dentro il silenzio di prima
     * aggiungerebbe righe di rumore all'inizio dell'impronta, e la DTW - che
     * confronta durate - le rifiuterebbe comunque per rapporto di lunghezza.
     *
     * Con la rete e' il contrario, ed e' costato una serata capirlo. La rete
     * vuole <b>esattamente</b> le sue righe: una riga in meno e risponde zero
     * secco, senza un errore, su ogni finestra. E il silenzio attorno alla
     * parola non la disturba - e' stata allenata su clip da un secondo dello
     * Speech Commands, che il silenzio attorno ce l'hanno tutte. Accorciare la
     * finestra non le toglie rumore: le toglie la risposta.
     */
    private volatile boolean finestraIntera;

    private volatile boolean vuoleEco;

    // ---- registrazione a comando ------------------------------------------

    private volatile Presa presa;
    private volatile int presaMs;
    private short[] presaBuffer;
    private int presaScritti;
    private float presaPicco;

    // ---- stato del rilevatore ---------------------------------------------

    private float pavimento = MINIMO_ASSOLUTO * 2f;
    private int sopra, sotto;
    private boolean parlando;
    private int daUltimaProva;
    /** Dov'era {@link #scritti} quando la voce e' cominciata. Anche questo
     *  long, o la differenza con scritti tornerebbe a essere sbagliata. */
    private long inizioParlato;

    public Orecchio(Context c, Ascolto a) {
        this.contesto = c;
        this.ascolto = a;
    }

    /** Quanto lungo dev'essere il tratto consegnato. Lo dice chi ha i modelli:
     *  confrontare un secondo e mezzo con modelli da mezzo secondo non
     *  aggancia niente, e la DTW lo rifiuterebbe comunque per lunghezza. */
    public void setFinestraMs(int ms) {
        finestraMs = Math.max(300, Math.min(1800, ms));
    }

    /** Consegna sempre {@link #finestraMs} interi, senza accorciare al
     *  parlato. Vedi {@link #finestraIntera} per il perche'. */
    public void setFinestraIntera(boolean intera) {
        finestraIntera = intera;
    }

    public boolean acceso() { return acceso; }

    /**
     * Se la cancellazione d'eco esiste su questo apparecchio.
     *
     * Su questo tablet il ROM la dichiara in {@code audio_effects.conf} e
     * {@code libwebrtc_audio_preprocessing.so} c'e' davvero - il che vuol dire
     * che e' quella vera di WebRTC e non un guscio. Se il riferimento le
     * arrivi anche dalla musica, e non solo dalla voce in chiamata, e' un'altra
     * domanda: si misura accendendola con la radio accesa e guardando se il
     * punteggio della parola peggiora o no.
     */
    public static boolean ecoDisponibile() {
        try { return AcousticEchoCanceler.isAvailable(); } catch (Throwable t) { return false; }
    }

    public static boolean rumoreDisponibile() {
        try { return NoiseSuppressor.isAvailable(); } catch (Throwable t) { return false; }
    }

    // ---- accensione --------------------------------------------------------

    public synchronized void accendi(boolean conEco) {
        vuoleEco = conEco;
        if (acceso) return;
        acceso = true;
        thread = new Thread(new Runnable() {
            @Override public void run() { gira(); }
        }, "Casa-orecchio");
        thread.start();
    }

    public synchronized void spegni() {
        acceso = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            try {
                // Si aspetta davvero che il thread molli AudioRecord: chi
                // chiama spegni() lo fa quasi sempre per <b>lasciare il
                // microfono a qualcun altro</b> - il riconoscitore di Google -
                // e su Android il microfono e' di uno solo. Tornare prima
                // vorrebbe dire un riconoscitore che parte e trova occupato,
                // una volta ogni tanto, senza un motivo visibile.
                t.join(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Ferma il confronto ma non chiude il microfono.
     *
     * Serve mentre Casa parla: la sua stessa voce e' il falso aggancio piu'
     * facile che ci sia, perche' esce da venti centimetri di distanza ed e' la
     * cosa piu' forte che il microfono senta.
     */
    public void pausa(boolean p) {
        inPausa = p;
        if (p) { parlando = false; sopra = 0; sotto = 0; }
    }

    /**
     * Registra un pezzo di durata fissa e lo consegna.
     *
     * Con il preavvio: si parte da {@link #PREAVVIO_MS} <b>prima</b> di adesso,
     * prendendoli dall'anello. Chi tocca « registra » e dice subito "Hey Home"
     * ha gia' cominciato quando il dito lascia lo schermo, e senza il preavvio
     * la « H » resterebbe fuori da tutti i campioni - cioe' proprio dal pezzo
     * che li distingue.
     */
    public synchronized void registra(int ms, Presa p) {
        if (!acceso || p == null) { if (p != null) p.suPresa(null, 0); return; }
        presaMs = Math.max(400, Math.min(4000, ms));
        int quanti = presaMs * Onda.HZ / 1000;
        presaBuffer = new short[quanti];
        presaScritti = 0;
        presaPicco = 0f;

        // Il preavvio, preso dall'anello.
        int preavvio = (int) Math.min(PREAVVIO_MS * Onda.HZ / 1000,
                                      Math.min(scritti, ANELLO));
        if (preavvio > 0) {
            leggiUltimi(preavvio, presaBuffer, 0);
            presaScritti = preavvio;
        }
        presa = p;
    }

    public boolean staRegistrando() { return presa != null; }

    public synchronized void annullaRegistrazione() {
        presa = null;
        presaBuffer = null;
    }

    // ---- il thread ---------------------------------------------------------

    private void gira() {
        if (!apri()) { acceso = false; return; }
        int scarti = 0;
        try {
            short[] blocco = new short[BLOCCO];
            while (acceso && !Thread.currentThread().isInterrupted()) {
                int letti = microfono.read(blocco, 0, BLOCCO);
                if (letti <= 0) {
                    // Un errore isolato capita quando qualcun altro prende il
                    // microfono per un attimo. Uno di fila all'altro vuol dire
                    // che non torna piu': si esce invece di girare a vuoto
                    // scaldando il tablet.
                    if (++scarti > 50) { Log.w(TAG, "orecchio: il microfono non risponde piu'"); break; }
                    continue;
                }
                scarti = 0;
                deposita(blocco, letti);
                float livello = rms(blocco, letti);
                if (presa != null) avanzaPresa(blocco, letti, livello);
                if (!inPausa) esamina(livello, letti);
                ascolto.suLivello(Math.min(1f, livello / 6000f), parlando);
            }
        } catch (Throwable t) {
            Log.w(TAG, "orecchio: si e' fermato", t);
        } finally {
            chiudi();
        }
    }

    private boolean apri() {
        // VOICE_RECOGNITION e' la sorgente pensata per questo: il sistema non
        // ci mette sopra il trattamento pensato per la telefonata, che
        // comprime la dinamica e sposta lo spettro - cioe' cambia proprio i
        // numeri su cui la parola si riconosce.
        //
        // VOICE_COMMUNICATION invece <b>accende la cancellazione d'eco</b>, e
        // vale il baratto quando dallo stesso tablet sta uscendo la radio.
        // Quale delle due convenga si misura, e per questo si sceglie.
        int sorgente = vuoleEco ? MediaRecorder.AudioSource.VOICE_COMMUNICATION
                                : MediaRecorder.AudioSource.VOICE_RECOGNITION;
        int minimo = AudioRecord.getMinBufferSize(Onda.HZ,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimo <= 0) minimo = Onda.HZ;      // qualche ROM risponde male
        // Mezzo secondo di margine: se il thread resta indietro per un
        // fotogramma lungo, il driver ha dove mettere il suono invece di
        // buttarlo. Un buco nel suono e' una parola persa.
        int buffer = Math.max(minimo * 4, Onda.HZ);

        try {
            microfono = new AudioRecord(sorgente, Onda.HZ,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer * 2);
            if (microfono.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "orecchio: microfono non inizializzato (sorgente " + sorgente + ")");
                chiudi();
                return false;
            }
            agganciaEffetti(microfono.getAudioSessionId());
            microfono.startRecording();
            Log.i(TAG, "orecchio: acceso, sorgente " + (vuoleEco ? "VOICE_COMMUNICATION" : "VOICE_RECOGNITION"));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "orecchio: microfono non aperto", t);
            chiudi();
            return false;
        }
    }

    /**
     * Cancellazione d'eco, soppressione del rumore, guadagno automatico.
     *
     * Si chiedono e non si pretendono: se il ROM non li ha, l'orecchio funziona
     * lo stesso, un po' peggio. Il guadagno automatico <b>non</b> si accende
     * quando l'eco e' spenta: alza il rumore di fondo nei silenzi, e il
     * rilevatore di voce vive proprio della differenza fra i due.
     */
    private void agganciaEffetti(int sessione) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                eco = AcousticEchoCanceler.create(sessione);
                if (eco != null) eco.setEnabled(vuoleEco);
            }
        } catch (Throwable t) { Log.w(TAG, "orecchio: eco non agganciata", t); }
        try {
            if (NoiseSuppressor.isAvailable()) {
                rumore = NoiseSuppressor.create(sessione);
                if (rumore != null) rumore.setEnabled(true);
            }
        } catch (Throwable t) { Log.w(TAG, "orecchio: soppressore non agganciato", t); }
        try {
            if (vuoleEco && AutomaticGainControl.isAvailable()) {
                guadagno = AutomaticGainControl.create(sessione);
                if (guadagno != null) guadagno.setEnabled(true);
            }
        } catch (Throwable t) { Log.w(TAG, "orecchio: guadagno non agganciato", t); }
    }

    private void chiudi() {
        rilascia(eco); eco = null;
        rilascia(rumore); rumore = null;
        rilascia(guadagno); guadagno = null;
        if (microfono != null) {
            try {
                if (microfono.getState() == AudioRecord.STATE_INITIALIZED) microfono.stop();
            } catch (Throwable ignorata) { }
            try { microfono.release(); } catch (Throwable ignorata) { }
            microfono = null;
        }
        Log.i(TAG, "orecchio: spento");
    }

    private static void rilascia(android.media.audiofx.AudioEffect e) {
        if (e == null) return;
        try { e.setEnabled(false); } catch (Throwable ignorata) { }
        try { e.release(); } catch (Throwable ignorata) { }
    }

    // ---- l'anello ----------------------------------------------------------

    private void deposita(short[] blocco, int quanti) {
        int p = (int) (scritti % ANELLO);
        int primo = Math.min(quanti, ANELLO - p);
        System.arraycopy(blocco, 0, anello, p, primo);
        if (quanti > primo) System.arraycopy(blocco, primo, anello, 0, quanti - primo);
        scritti += quanti;
    }

    /** Copia gli ultimi {@code quanti} campioni dell'anello in {@code dove}. */
    private void leggiUltimi(int quanti, short[] dove, int offset) {
        quanti = (int) Math.min(quanti, Math.min(scritti, ANELLO));
        int fine = (int) (scritti % ANELLO);
        int inizio = ((fine - quanti) % ANELLO + ANELLO) % ANELLO;
        int primo = Math.min(quanti, ANELLO - inizio);
        System.arraycopy(anello, inizio, dove, offset, primo);
        if (quanti > primo) System.arraycopy(anello, 0, dove, offset + primo, quanti - primo);
    }

    // ---- il rilevatore di voce --------------------------------------------

    private static float rms(short[] b, int quanti) {
        // In double: la somma dei quadrati di ottomila campioni a fondo scala
        // supera il miliardo, e in float si comincia a perdere i campioni
        // piccoli - cioe' proprio il rumore di fondo che si vuole misurare.
        double somma = 0;
        for (int i = 0; i < quanti; i++) { double x = b[i]; somma += x * x; }
        return (float) Math.sqrt(somma / Math.max(1, quanti));
    }

    private void esamina(float livello, int campioni) {
        // Il pavimento insegue: giu' in fretta, su piano. Al contrario, una
        // voce che comincia si tirerebbe dietro il pavimento fino a coprirsi.
        if (livello < pavimento) pavimento = pavimento * 0.90f + livello * 0.10f;
        else                     pavimento = pavimento * 0.9990f + livello * 0.0010f;
        if (pavimento < MINIMO_ASSOLUTO) pavimento = MINIMO_ASSOLUTO;

        boolean forte = livello > pavimento * SOPRA_IL_FONDO && livello > MINIMO_ASSOLUTO * 2f;

        if (forte) { sopra++; sotto = 0; } else { sotto++; sopra = 0; }

        if (!parlando) {
            if (sopra >= BLOCCHI_ATTACCO) {
                parlando = true;
                daUltimaProva = 0;
                inizioParlato = scritti;
            }
            return;
        }

        daUltimaProva += campioni;

        // Mentre parla, ogni tanto. E una volta ancora appena smette: la
        // parola detta da sola - senza niente attaccato dietro - finisce li'
        // dentro tutta intera, ed e' il caso migliore, quello che si vuole non
        // perdere.
        boolean finito = sotto >= BLOCCHI_CODA;
        boolean tocca = daUltimaProva >= PASSO_PROVA_MS * Onda.HZ / 1000;

        if (tocca || finito) {
            daUltimaProva = 0;
            consegna();
        }
        if (finito) {
            parlando = false;
            sopra = 0;
        }
    }

    /** Consegna l'ultima finestra lunga {@link #finestraMs}. */
    private void consegna() {
        int quanti = finestraMs * Onda.HZ / 1000;
        int tetto = (int) Math.min(lavoro.length, Math.min(ANELLO, scritti));
        if (!finestraIntera) {
            // Non piu' indietro di dove il parlato e' cominciato, meno il
            // preavvio: allungare la finestra dentro il silenzio di prima
            // aggiungerebbe righe di rumore all'inizio dell'impronta, e la
            // normalizzazione poi le spalmerebbe su tutto.
            tetto = (int) Math.min(tetto,
                    scritti - inizioParlato + PREAVVIO_MS * Onda.HZ / 1000);
        }
        quanti = Math.min(quanti, tetto);
        // Sotto la misura buona non si consegna niente. Con la finestra intera
        // la misura buona e' quella piena: consegnarne una piu' corta vuol dire
        // farsi rispondere zero e non capire perche'.
        int minimo = finestraIntera ? finestraMs * Onda.HZ / 1000
                                    : Mfcc.campioniPer(300);
        if (quanti < minimo) return;
        leggiUltimi(quanti, lavoro, 0);
        ascolto.suFinestra(lavoro, quanti);
    }

    // ---- ritaglio ----------------------------------------------------------

    /**
     * Dove comincia e dove finisce il parlato dentro una registrazione.
     *
     * <b>Serve, e non e' un abbellimento.</b> Chi tocca « registra » e dice
     * "Hey Home" riempie meno di un secondo di un buffer da un secondo e
     * mezzo: il resto e' silenzio prima e dopo. Salvare il buffer intero
     * vorrebbe dire un modello per meta' fatto di silenzio, e allora
     * <ul>
     * <li>la normalizzazione delle MFCC spalmerebbe su tutto le statistiche
     *     del silenzio, cambiando i numeri della parte parlata;</li>
     * <li>la durata del modello non somiglierebbe piu' a quella della finestra
     *     che l'orecchio consegna dal vivo, e il rifiuto per lunghezza
     *     scarterebbe il confronto prima ancora di farlo.</li>
     * </ul>
     * Cioe' i campioni sembrerebbero registrati bene e non aggancerebbero mai.
     *
     * La soglia e' <b>relativa al piu' forte</b> e non al rumore di fondo:
     * dentro una registrazione fatta apposta c'e' per forza la parola, quindi
     * il massimo e' la parola, e un ottavo del massimo e' il punto in cui
     * comincia. Un pavimento inseguito servirebbe se non si sapesse se c'e'
     * qualcosa; qui si sa.
     *
     * Torna {@code null} se dentro non c'e' niente di abbastanza forte: una
     * registrazione muta non e' un campione, e va detto a chi l'ha fatta
     * invece di salvarla e lasciare che rovini la taratura.
     */
    public static int[] ritaglia(short[] suono, int quanti) {
        if (suono == null || quanti < BLOCCO * 4) return null;
        int blocchi = quanti / BLOCCO;
        float[] livelli = new float[blocchi];
        float massimo = 0f;
        for (int b = 0; b < blocchi; b++) {
            livelli[b] = rmsDa(suono, b * BLOCCO, BLOCCO);
            if (livelli[b] > massimo) massimo = livelli[b];
        }
        if (massimo < MINIMO_ASSOLUTO * 3f) return null;

        float soglia = Math.max(massimo * 0.125f, MINIMO_ASSOLUTO * 1.5f);
        int primo = -1, ultimo = -1;
        for (int b = 0; b < blocchi; b++) {
            if (livelli[b] >= soglia) { if (primo < 0) primo = b; ultimo = b; }
        }
        if (primo < 0 || ultimo <= primo) return null;

        // Quattro blocchi di margine - 80 ms - da una parte e dall'altra. La
        // « H » di "Hey" e' un soffio che sta sotto la soglia: senza margine
        // si taglierebbe via proprio l'attacco.
        int margine = 4;
        int da = Math.max(0, (primo - margine) * BLOCCO);
        int a = Math.min(quanti, (ultimo + 1 + margine) * BLOCCO);
        if (a - da < Mfcc.campioniPer(250)) return null;
        return new int[] { da, a - da };
    }

    private static float rmsDa(short[] b, int da, int quanti) {
        double somma = 0;
        for (int i = 0; i < quanti; i++) { double x = b[da + i]; somma += x * x; }
        return (float) Math.sqrt(somma / Math.max(1, quanti));
    }

    // ---- la registrazione a comando ---------------------------------------

    private void avanzaPresa(short[] blocco, int letti, float livello) {
        Presa p = presa;
        short[] dove = presaBuffer;
        if (p == null || dove == null) return;

        int spazio = dove.length - presaScritti;
        int quanti = Math.min(letti, spazio);
        if (quanti > 0) {
            System.arraycopy(blocco, 0, dove, presaScritti, quanti);
            presaScritti += quanti;
        }
        if (livello > presaPicco) presaPicco = livello;

        int rimasti = (dove.length - presaScritti) * 1000 / Onda.HZ;
        p.suAvanzamento(rimasti, Math.min(1f, livello / 6000f),
                        Math.min(1f, presaPicco / 6000f));

        if (presaScritti >= dove.length) {
            presa = null;
            presaBuffer = null;
            p.suPresa(dove, presaScritti);
        }
    }
}
