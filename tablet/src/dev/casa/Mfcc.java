package dev.casa;

/**
 * Da un pezzo di suono alla sua impronta.
 *
 * <h3>Cosa sono le MFCC, e perche' non si confronta il suono</h3>
 *
 * Due "Hey Home" detti dalla stessa persona a due secondi di distanza non
 * hanno <b>un solo campione</b> in comune: la forma d'onda dipende dalla fase,
 * dal volume, da dove sta la testa. Confrontarle campione per campione da'
 * sempre « diverse ».
 *
 * Quello che <i>e'</i> uguale e' come si distribuisce l'energia fra le
 * frequenze, e come quella distribuzione cambia nel tempo: la « e » di "Hey" ha
 * le sue due formanti dove le ha sempre. Le MFCC sono la ricetta con cui tutto
 * il riconoscimento del parlato riassume quella distribuzione in una manciata
 * di numeri per ogni centesimo di secondo:
 *
 * <ol>
 * <li><b>preenfasi</b> - si alza il registro acuto, che nel parlato esce dalla
 *     bocca gia' piu' debole di sei decibel per ottava. Senza, le consonanti
 *     pesano niente accanto alle vocali, e "Hey Home" e "Hey Rome" diventano la
 *     stessa cosa;</li>
 * <li><b>finestre da 25 ms ogni 10</b> - dentro 25 ms la voce si puo'
 *     considerare ferma; il passo di 10 e' meta' finestra e serve a non perdere
 *     quello che cade a cavallo di due;</li>
 * <li><b>finestra di Hamming</b> - tagliare di netto un pezzo di suono
 *     introduce due gradini, e i gradini nello spettro sono rumore su tutte le
 *     frequenze;</li>
 * <li><b>spettro di potenza</b> con una FFT da 512;</li>
 * <li><b>banchi mel</b> - ventisei filtri triangolari, fitti in basso e radi in
 *     alto, come l'orecchio: fra 200 e 300 Hz sentiamo una differenza che fra
 *     5000 e 5100 non sentiamo affatto;</li>
 * <li><b>logaritmo</b> - il volume diventa una somma invece che un fattore, ed
 *     e' cosi' che al punto dopo si toglie;</li>
 * <li><b>DCT</b> - i ventisei numeri sono molto correlati fra loro; la DCT li
 *     riassume in tredici quasi indipendenti, e mette nei primi la forma
 *     generale dello spettro, che e' quella che dice <i>quale suono e'</i>.</li>
 * </ol>
 *
 * <h3>La normalizzazione e' meta' del lavoro</h3>
 *
 * Alla fine si toglie a ogni coefficiente la sua media e lo si divide per la
 * sua deviazione, <b>calcolate sulla singola registrazione</b>. Sembra un
 * dettaglio ed e' quello che fa la differenza fra funzionare e no:
 *
 * <ul>
 * <li>togliere la media toglie <b>il canale</b> - il microfono, la stanza, la
 *     distanza dalla bocca. Nel logaritmo il canale e' una costante additiva,
 *     quindi sparisce esattamente. Chi parla da mezzo metro e chi parla da due
 *     danno la stessa impronta;</li>
 * <li>dividere per la deviazione toglie <b>il volume</b>, che e' l'altra
 *     cosa che cambia a ogni ripetizione e non vuol dire niente.</li>
 * </ul>
 *
 * Senza questi due passaggi le impronte registrate vicino al tablet non
 * aggancerebbero mai una parola detta dall'altra parte della cucina.
 *
 * <h3>Non e' condivisibile fra thread</h3>
 *
 * Tiene dentro i suoi banchi di appoggio per non allocare a ogni finestra: cento
 * finestre al secondo, per sempre, sono cento allocazioni al secondo che il
 * raccoglitore di memoria verrebbe a riprendersi nel mezzo di un fotogramma.
 * Chi ne ha bisogno se ne tiene uno suo: l'orecchio ha il suo, la taratura il
 * suo.
 */
public final class Mfcc {

    /** Quanti numeri per finestra: dodici cepstri piu' l'energia. */
    public static final int DIMENSIONI = 13;

    /** Ogni quanto si prende una finestra. Da qui si converte fra righe
     *  dell'impronta e millisecondi. */
    public static final int PASSO_MS = 10;

    private static final int FINESTRA = 400;      // 25 ms a 16 kHz
    private static final int PASSO    = 160;      // 10 ms
    private static final int FFT      = 512;      // la potenza di due sopra 400
    private static final int FILTRI   = 26;
    private static final int CEPSTRI  = 13;       // c0 compreso; c0 viene scartato

    private static final float PREENFASI = 0.97f;
    private static final float HZ_MIN = 20f;
    private static final float HZ_MAX = 7800f;    // sotto Nyquist, che qui e' 8000

    /** Il pavimento del logaritmo. Senza, una finestra di silenzio digitale
     *  perfetto darebbe meno infinito e da li' in poi l'impronta sarebbe tutta
     *  NaN - e un NaN nella DTW vince ogni confronto, silenziosamente. */
    private static final float MINIMO = 1e-10f;

    // ---- tabelle, calcolate una volta per oggetto --------------------------

    private final float[] finestra = new float[FINESTRA];
    private final int[]   inizioFiltro = new int[FILTRI];
    private final int[]   fineFiltro   = new int[FILTRI];
    private final float[][] pesoFiltro = new float[FILTRI][];
    private final float[][] dct = new float[CEPSTRI][FILTRI];

    // ---- banchi di appoggio, riusati a ogni finestra -----------------------

    private final float[] reale = new float[FFT];
    private final float[] immaginaria = new float[FFT];
    private final float[] potenza = new float[FFT / 2 + 1];
    private final float[] banchi = new float[FILTRI];
    private final int[]   rovescio = new int[FFT];
    private final float[] cos = new float[FFT / 2];
    private final float[] sin = new float[FFT / 2];

    public Mfcc() {
        // Hamming: 0,54 - 0,46 cos. Non Hann, che va a zero esatto agli
        // estremi: qui le finestre si sovrappongono a meta', e i due zeri
        // toglierebbero peso proprio ai campioni che l'altra finestra ha al
        // centro.
        for (int i = 0; i < FINESTRA; i++) {
            finestra[i] = (float) (0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (FINESTRA - 1)));
        }

        preparaFiltri();
        preparaDct();
        preparaFft();
    }

    /**
     * I ventisei triangoli, distesi sulla scala mel.
     *
     * mel = 2595 log10(1 + hz/700): e' la formula del 1937 che dice quanto una
     * differenza di frequenza si <i>sente</i>. Si distribuiscono ventotto punti
     * a distanza uguale su quella scala, si torna in hertz - e in hertz
     * risultano fitti in basso e radi in alto - e ogni filtro e' il triangolo
     * che sale dal punto i al punto i+1 e ridiscende al punto i+2.
     */
    private void preparaFiltri() {
        float melMin = mel(HZ_MIN), melMax = mel(HZ_MAX);
        int[] confine = new int[FILTRI + 2];
        for (int i = 0; i < confine.length; i++) {
            float m = melMin + (melMax - melMin) * i / (FILTRI + 1);
            float hz = hz(m);
            confine[i] = Math.round(hz * FFT / Onda.HZ);
            if (confine[i] > FFT / 2) confine[i] = FFT / 2;
        }
        for (int f = 0; f < FILTRI; f++) {
            int sinistra = confine[f], cima = confine[f + 1], destra = confine[f + 2];
            // Con 512 punti su 8 kHz i primi triangoli sono larghi due o tre
            // bin, e due confini possono cadere sullo stesso: senza questo, il
            // filtro sarebbe largo zero e la divisione qui sotto diventerebbe
            // uno zero su zero.
            if (cima <= sinistra) cima = sinistra + 1;
            if (destra <= cima) destra = cima + 1;
            if (destra > FFT / 2) destra = FFT / 2;

            inizioFiltro[f] = sinistra;
            fineFiltro[f] = destra;
            int quanti = Math.max(1, destra - sinistra + 1);
            pesoFiltro[f] = new float[quanti];
            for (int b = sinistra; b <= destra && b <= FFT / 2; b++) {
                float p;
                if (b <= cima) p = cima == sinistra ? 1f : (b - sinistra) / (float) (cima - sinistra);
                else           p = destra == cima ? 1f : (destra - b) / (float) (destra - cima);
                pesoFiltro[f][b - sinistra] = Math.max(0f, p);
            }
        }
    }

    /** La DCT-II, scritta come matrice: tredici righe per ventisei colonne. */
    private void preparaDct() {
        for (int k = 0; k < CEPSTRI; k++) {
            for (int n = 0; n < FILTRI; n++) {
                dct[k][n] = (float) Math.cos(Math.PI * k * (n + 0.5) / FILTRI);
            }
        }
    }

    /** Tabella di inversione dei bit e radici dell'unita', per la FFT. */
    private void preparaFft() {
        int bit = Integer.numberOfTrailingZeros(FFT);
        for (int i = 0; i < FFT; i++) {
            int r = 0;
            for (int b = 0; b < bit; b++) if ((i & (1 << b)) != 0) r |= 1 << (bit - 1 - b);
            rovescio[i] = r;
        }
        for (int i = 0; i < FFT / 2; i++) {
            cos[i] = (float) Math.cos(-2.0 * Math.PI * i / FFT);
            sin[i] = (float) Math.sin(-2.0 * Math.PI * i / FFT);
        }
    }

    private static float mel(float hz) { return (float) (2595.0 * Math.log10(1.0 + hz / 700.0)); }
    private static float hz(float mel) { return (float) (700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)); }

    // ---- l'impronta --------------------------------------------------------

    /** Quante finestre vengono fuori da un tot di campioni. */
    public static int quanteFinestre(int campioni) {
        if (campioni < FINESTRA) return 0;
        return (campioni - FINESTRA) / PASSO + 1;
    }

    /** Quanti campioni servono per un tot di millisecondi di impronta. */
    public static int campioniPer(int ms) {
        return FINESTRA + Math.max(0, ms - 25) * Onda.HZ / 1000;
    }

    public float[][] impronta(short[] campioni) {
        return impronta(campioni, 0, campioni == null ? 0 : campioni.length);
    }

    /**
     * L'impronta di un pezzo di suono: una riga da {@link #DIMENSIONI} numeri
     * ogni {@link #PASSO_MS} millisecondi, gia' normalizzata.
     *
     * Torna null se il pezzo e' piu' corto di una finestra: meglio niente che
     * un'impronta da una riga, che nella DTW sarebbe un confronto vinto in
     * partenza da qualunque cosa.
     */
    public float[][] impronta(short[] campioni, int da, int quanti) {
        if (campioni == null) return null;
        quanti = Math.min(quanti, campioni.length - da);
        int righe = quanteFinestre(quanti);
        if (righe <= 0) return null;

        float[][] fuori = new float[righe][DIMENSIONI];
        for (int r = 0; r < righe; r++) {
            unaFinestra(campioni, da + r * PASSO, fuori[r]);
        }
        normalizza(fuori);
        return fuori;
    }

    /** Una finestra: dai campioni ai tredici numeri, senza allocare niente. */
    private void unaFinestra(short[] campioni, int da, float[] fuori) {
        // Preenfasi e finestratura in un giro solo. Il campione prima del
        // primo serve alla preenfasi; a inizio buffer non c'e' e vale zero,
        // che e' esattamente quello che si vuole dire.
        for (int i = 0; i < FINESTRA; i++) {
            int p = da + i;
            float x = campioni[p];
            float prima = p > 0 ? campioni[p - 1] : 0f;
            reale[i] = (x - PREENFASI * prima) * finestra[i];
            immaginaria[i] = 0f;
        }
        // Il resto della FFT e' riempimento a zero: 400 campioni in 512 punti
        // danno una griglia di frequenze piu' fitta senza inventarsi niente.
        for (int i = FINESTRA; i < FFT; i++) { reale[i] = 0f; immaginaria[i] = 0f; }

        fft();

        for (int b = 0; b <= FFT / 2; b++) {
            potenza[b] = reale[b] * reale[b] + immaginaria[b] * immaginaria[b];
        }

        // I banchi mel, in logaritmo.
        for (int f = 0; f < FILTRI; f++) {
            float somma = 0f;
            float[] pesi = pesoFiltro[f];
            int inizio = inizioFiltro[f], fine = fineFiltro[f];
            for (int b = inizio; b <= fine; b++) somma += potenza[b] * pesi[b - inizio];
            banchi[f] = (float) Math.log(Math.max(MINIMO, somma));
        }

        // La DCT. c0 e' la somma dei banchi, cioe' il volume: si scarta, e al
        // suo posto va l'energia della finestra - che dice la stessa cosa ma
        // dopo la normalizzazione resta confrontabile fra registrazioni.
        for (int k = 1; k < CEPSTRI; k++) {
            float somma = 0f;
            float[] riga = dct[k];
            for (int n = 0; n < FILTRI; n++) somma += banchi[n] * riga[n];
            fuori[k - 1] = somma;
        }

        float energia = 0f;
        for (int i = 0; i < FINESTRA; i++) {
            float x = campioni[da + i];
            energia += x * x;
        }
        fuori[DIMENSIONI - 1] = (float) Math.log(Math.max(MINIMO, energia / FINESTRA));
    }

    /**
     * Toglie media e deviazione, colonna per colonna.
     *
     * E' il passaggio che rende un'impronta confrontabile con un'altra fatta
     * un altro giorno, a un'altra distanza, con la radio accesa piu' o meno
     * forte. Vedi la nota in cima alla classe: qui c'e' meta' del
     * funzionamento.
     */
    private static void normalizza(float[][] impronta) {
        int righe = impronta.length;
        for (int d = 0; d < DIMENSIONI; d++) {
            float somma = 0f;
            for (int r = 0; r < righe; r++) somma += impronta[r][d];
            float media = somma / righe;

            float quadrati = 0f;
            for (int r = 0; r < righe; r++) {
                float scarto = impronta[r][d] - media;
                quadrati += scarto * scarto;
            }
            // Il piu' uno evita di dividere per quasi zero su una colonna
            // piatta - che succede davvero, sull'energia di una registrazione
            // di silenzio - e trasformerebbe il rumore di fondo in un segnale
            // grande come una parola.
            float deviazione = (float) Math.sqrt(quadrati / righe) + 1e-3f;

            for (int r = 0; r < righe; r++) impronta[r][d] = (impronta[r][d] - media) / deviazione;
        }
    }

    // ---- FFT ---------------------------------------------------------------

    /**
     * FFT a base due, sul posto, iterativa.
     *
     * Iterativa e non ricorsiva: la ricorsiva alloca due array a ogni
     * chiamata, e qui si chiama cento volte al secondo per sempre. Cosi'
     * invece non alloca niente - i due banchi sono campi dell'oggetto - e su
     * 512 punti sono nove passate da 256 farfalle.
     */
    private void fft() {
        for (int i = 0; i < FFT; i++) {
            int j = rovescio[i];
            if (j > i) {
                float t = reale[i]; reale[i] = reale[j]; reale[j] = t;
                t = immaginaria[i]; immaginaria[i] = immaginaria[j]; immaginaria[j] = t;
            }
        }
        for (int lunghezza = 2; lunghezza <= FFT; lunghezza <<= 1) {
            int mezzo = lunghezza >> 1;
            int passo = FFT / lunghezza;
            for (int i = 0; i < FFT; i += lunghezza) {
                for (int j = 0; j < mezzo; j++) {
                    int k = j * passo;
                    int a = i + j, b = i + j + mezzo;
                    float cr = cos[k], ci = sin[k];
                    float tr = reale[b] * cr - immaginaria[b] * ci;
                    float ti = reale[b] * ci + immaginaria[b] * cr;
                    reale[b] = reale[a] - tr;
                    immaginaria[b] = immaginaria[a] - ti;
                    reale[a] += tr;
                    immaginaria[a] += ti;
                }
            }
        }
    }
}
