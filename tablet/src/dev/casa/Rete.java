package dev.casa;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * La rete che riconosce la parola di attivazione, allenata su voci che non sono
 * le nostre.
 *
 * <h3>Perche' esiste, e cosa sostituisce</h3>
 *
 * La {@link ParolaChiave} confronta quello che sente con le registrazioni di
 * chi ha allestito il tablet: funziona, ma riconosce <b>quella voce</b>. Basta
 * un raffreddore, o che parli qualcun altro di casa, e non aggancia piu' - e
 * non e' una soglia da tarare meglio, e' come funziona un confronto fra due
 * impronte.
 *
 * Questa e' allenata su <b>2.100 registrazioni di "marvin" dette da centinaia
 * di persone diverse</b> (Speech Commands, pubblicato da Google con licenza
 * libera), piu' ventimila registrazioni di altre parole come negativi.
 * Riconosce chiunque, e nessuno in casa deve registrare niente.
 *
 * <h3>Perche' convoluzionale, e non tre strati densi</h3>
 *
 * La prima versione appiattiva le 98 righe di impronta in un vettore da 1274 e
 * ci metteva sopra tre strati densi. Misurata: prende l'87,7% con il 2,76% di
 * falsi, che in una cucina sono centinaia di risvegli a vuoto al giorno.
 *
 * Il motivo e' strutturale. Una rete densa deve imparare la parola
 * <b>separatamente per ogni posizione</b> in cui puo' capitare: "marvin" che
 * comincia al decimo centesimo di secondo e "marvin" che comincia al
 * quindicesimo sono, per lei, due cose diverse. Una convoluzione fa scorrere
 * gli <b>stessi</b> pesi lungo il tempo: impara una volta com'e' fatta la "m"
 * di marvin e la riconosce dovunque capiti. Un quinto dei parametri, e un
 * risultato migliore.
 *
 * <h3>Perche' il passo avanti e' scritto a mano</h3>
 *
 * Due convoluzioni, due massimi su blocchi, due strati densi: trentaseimila
 * numeri in tutto. Portarsi dietro TFLite - un AAR da aprire a mano, una
 * libreria nativa in piu' nell'APK, e la speranza che esista una build per
 * armeabi-v7a - per fare questo sarebbe il genere di dipendenza che fra un
 * anno non si compila piu'.
 *
 * Il conto e' mezzo milione di moltiplicazioni per finestra, otto finestre al
 * secondo <b>e solo mentre in cucina si parla</b>: quattro milioni al secondo
 * nel caso peggiore, su quattro core a 1,3 GHz.
 *
 * <h3>Il formato del file</h3>
 *
 * Lo scrive {@code pc/parola/conv.py}. Niente compressione e niente
 * quantizzazione: un formato che si legge di seguito, senza saltare avanti e
 * indietro, vale piu' di qualche decina di kilobyte risparmiati. Tutto
 * <b>little-endian</b>, come lo scrive numpy e come lo legge un ARM.
 *
 * <pre>
 *   "MRVN"  int32 versione=2
 *   int32   righe, dimensioni
 *   int32   filtri1, largo1, pool1
 *   int32   filtri2, largo2, pool2
 *   int32   densi, appiattito
 *   float32 W1[largo1*dimensioni][filtri1], b1[filtri1]
 *   float32 W2[largo2*filtri1][filtri2],    b2[filtri2]
 *   float32 W3[appiattito][densi],          b3[densi]
 *   float32 W4[densi],                      b4
 * </pre>
 */
public final class Rete {

    /** Sotto {@code Casa} come tutto il resto, col nome del pezzo nel
     *  messaggio: il perche' sta su {@link MainActivity#TAG}. */
    private static final String TAG = MainActivity.TAG;

    private final int righe, dimensioni;
    private final int filtri1, largo1, pool1;
    private final int filtri2, largo2, pool2;
    private final int densi, appiattito;

    private final float[] W1, b1, W2, b2, W3, b3, W4;
    private final float b4;

    /** Tutti i banchi di lavoro, allocati una volta sola. Questo gira otto
     *  volte al secondo mentre qualcuno parla: allocare qui vorrebbe dire dare
     *  da mangiare al raccoglitore di memoria per niente, e su 1 GB il
     *  raccoglitore si fa sentire. */
    private final float[] uscita1, ridotto1, uscita2, ridotto2, denso;

    private final int tempo1, tempo1r, tempo2, tempo2r;

    private Rete(int[] m, float[] W1, float[] b1, float[] W2, float[] b2,
                 float[] W3, float[] b3, float[] W4, float b4) {
        this.righe = m[0]; this.dimensioni = m[1];
        this.filtri1 = m[2]; this.largo1 = m[3]; this.pool1 = m[4];
        this.filtri2 = m[5]; this.largo2 = m[6]; this.pool2 = m[7];
        this.densi = m[8]; this.appiattito = m[9];
        this.W1 = W1; this.b1 = b1; this.W2 = W2; this.b2 = b2;
        this.W3 = W3; this.b3 = b3; this.W4 = W4; this.b4 = b4;

        tempo1 = righe - largo1 + 1;
        tempo1r = tempo1 / pool1;
        tempo2 = tempo1r - largo2 + 1;
        tempo2r = tempo2 / pool2;

        uscita1 = new float[tempo1 * filtri1];
        ridotto1 = new float[tempo1r * filtri1];
        uscita2 = new float[tempo2 * filtri2];
        ridotto2 = new float[tempo2r * filtri2];
        denso = new float[densi];
    }

    /** Quante righe di impronta vuole: la finestra da consegnarle e' lunga
     *  cosi', non quanto pare. */
    public int righe() { return righe; }

    /** Quanto dura la finestra, in millisecondi. */
    public int finestraMs() { return righe * Mfcc.PASSO_MS; }

    public int quantiPesi() {
        return W1.length + b1.length + W2.length + b2.length
             + W3.length + b3.length + W4.length + 1;
    }

    // ---- lettura -----------------------------------------------------------

    /**
     * Legge il modello da {@code assets/}. Torna null se non c'e' o se non si
     * capisce: Casa deve partire lo stesso, con il confronto a campioni.
     */
    public static Rete daAsset(Context c, String nome) {
        InputStream in = null;
        try {
            in = c.getAssets().open(nome);
            java.io.ByteArrayOutputStream fuori = new java.io.ByteArrayOutputStream(1 << 20);
            byte[] blocco = new byte[16384];
            int n;
            while ((n = in.read(blocco)) > 0) fuori.write(blocco, 0, n);
            return daByte(fuori.toByteArray());
        } catch (Exception e) {
            Log.i(TAG, "rete: nessun modello in assets/" + nome + " (" + e.getMessage() + ")");
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignorata) { }
        }
    }

    /**
     * Il modello da byte gia' letti.
     *
     * Pubblico perche' la verifica di parita' sul PC deve poter far girare
     * QUESTO codice - non una copia - sullo stesso modello e sulla stessa
     * impronta con cui lo fa girare Python. Se le due uscite divergono, la
     * rete in casa sta calcolando un'altra cosa.
     */
    public static Rete daByte(byte[] dati) {
        if (dati.length < 48) { Log.w(TAG, "rete: file troppo corto"); return null; }
        ByteBuffer b = ByteBuffer.wrap(dati).order(ByteOrder.LITTLE_ENDIAN);

        // La magia si legge come quattro byte e non come un intero: cosi' il
        // controllo non dipende dall'ordine dei byte della macchina.
        if (b.get() != 'M' || b.get() != 'R' || b.get() != 'V' || b.get() != 'N') {
            Log.w(TAG, "rete: non e' un modello di Marvin");
            return null;
        }
        int versione = b.getInt();
        if (versione != 2) {
            Log.w(TAG, "rete: versione " + versione + ", ne so leggere solo la 2");
            return null;
        }

        int[] m = new int[10];
        for (int i = 0; i < m.length; i++) m[i] = b.getInt();
        int righe = m[0], dim = m[1], f1 = m[2], l1 = m[3], p1 = m[4];
        int f2 = m[5], l2 = m[6], p2 = m[7], densi = m[8], appiattito = m[9];

        if (dim != Mfcc.DIMENSIONI) {
            Log.w(TAG, "rete: il modello vuole " + dim + " numeri per riga, le MFCC ne danno "
                     + Mfcc.DIMENSIONI);
            return null;
        }
        int t1 = righe - l1 + 1, t1r = t1 / p1, t2 = t1r - l2 + 1, t2r = t2 / p2;
        if (righe <= 0 || f1 <= 0 || f2 <= 0 || densi <= 0 || t1 <= 0 || t2 <= 0
                || appiattito != t2r * f2) {
            Log.w(TAG, "rete: misure incoerenti");
            return null;
        }

        long attesi = (long) l1 * dim * f1 + f1
                    + (long) l2 * f1 * f2 + f2
                    + (long) appiattito * densi + densi
                    + densi + 1;
        FloatBuffer f = b.asFloatBuffer();
        if (f.remaining() < attesi) {
            Log.w(TAG, "rete: mancano dei pesi (" + f.remaining() + " su " + attesi + ")");
            return null;
        }

        float[] W1 = prendi(f, l1 * dim * f1), b1 = prendi(f, f1);
        float[] W2 = prendi(f, l2 * f1 * f2), b2 = prendi(f, f2);
        float[] W3 = prendi(f, appiattito * densi), b3 = prendi(f, densi);
        float[] W4 = prendi(f, densi);
        float b4 = f.get();

        Rete r = new Rete(m, W1, b1, W2, b2, W3, b3, W4, b4);
        Log.i(TAG, "rete: modello pronto, " + r.quantiPesi() + " pesi, finestra di "
                 + r.finestraMs() + " ms");
        return r;
    }

    private static float[] prendi(FloatBuffer f, int quanti) {
        float[] v = new float[quanti];
        f.get(v);
        return v;
    }

    // ---- il passo avanti ---------------------------------------------------

    /**
     * Quanto somiglia alla parola, da zero a uno. Piu' alto, piu' somiglia -
     * il contrario della distanza della {@link ParolaChiave}.
     *
     * L'impronta dev'essere alta almeno {@link #righe()}: se e' piu' alta si
     * prendono <b>le ultime</b> righe, perche' la parola sta in fondo a quello
     * che si e' appena sentito, non all'inizio.
     */
    public float punteggio(float[][] impronta) {
        if (impronta == null || impronta.length < righe) return 0f;
        final int da = impronta.length - righe;

        // ---- prima convoluzione: finestre di largo1 righe -> filtri1 canali
        //
        // I cicli sono ordinati per percorrere W1 in avanti: e' salvato per
        // righe, e saltare di filtri1 float a ogni passo su un Cortex-A7 - che
        // ha poca cache - costa piu' del conto stesso.
        for (int t = 0; t < tempo1; t++) {
            int fuori = t * filtri1;
            System.arraycopy(b1, 0, uscita1, fuori, filtri1);
            for (int k = 0; k < largo1; k++) {
                float[] riga = impronta[da + t + k];
                int baseK = k * dimensioni * filtri1;
                for (int d = 0; d < dimensioni; d++) {
                    float v = riga[d];
                    if (v == 0f) continue;
                    int base = baseK + d * filtri1;
                    for (int c = 0; c < filtri1; c++) uscita1[fuori + c] += v * W1[base + c];
                }
            }
            for (int c = 0; c < filtri1; c++) {
                if (uscita1[fuori + c] < 0f) uscita1[fuori + c] = 0f;
            }
        }
        massimo(uscita1, ridotto1, tempo1, filtri1, pool1);

        // ---- seconda convoluzione
        for (int t = 0; t < tempo2; t++) {
            int fuori = t * filtri2;
            System.arraycopy(b2, 0, uscita2, fuori, filtri2);
            for (int k = 0; k < largo2; k++) {
                int dentro = (t + k) * filtri1;
                int baseK = k * filtri1 * filtri2;
                for (int c1 = 0; c1 < filtri1; c1++) {
                    float v = ridotto1[dentro + c1];
                    if (v == 0f) continue;
                    int base = baseK + c1 * filtri2;
                    for (int c2 = 0; c2 < filtri2; c2++) uscita2[fuori + c2] += v * W2[base + c2];
                }
            }
            for (int c = 0; c < filtri2; c++) {
                if (uscita2[fuori + c] < 0f) uscita2[fuori + c] = 0f;
            }
        }
        massimo(uscita2, ridotto2, tempo2, filtri2, pool2);

        // ---- strato denso
        System.arraycopy(b3, 0, denso, 0, densi);
        for (int i = 0; i < appiattito; i++) {
            float v = ridotto2[i];
            if (v == 0f) continue;
            int base = i * densi;
            for (int j = 0; j < densi; j++) denso[j] += v * W3[base + j];
        }
        float z = b4;
        for (int j = 0; j < densi; j++) {
            if (denso[j] > 0f) z += denso[j] * W4[j];
        }

        // Sigmoide stabile: un exp di un numero grande e' infinito, e da li' in
        // poi e' tutto NaN - e un NaN confrontato con una soglia e' sempre
        // falso, quindi la parola smetterebbe di agganciare senza un errore.
        if (z >= 0f) return 1f / (1f + (float) Math.exp(-z));
        float e = (float) Math.exp(z);
        return e / (1f + e);
    }

    /** Il massimo su blocchi di {@code quanto} passi: e' quello che rende il
     *  riconoscimento indifferente a qualche millisecondo di scarto. */
    private static void massimo(float[] dentro, float[] fuori, int tempo, int canali, int quanto) {
        int blocchi = tempo / quanto;
        for (int t = 0; t < blocchi; t++) {
            int base = t * quanto * canali;
            int destinazione = t * canali;
            System.arraycopy(dentro, base, fuori, destinazione, canali);
            for (int k = 1; k < quanto; k++) {
                int riga = base + k * canali;
                for (int c = 0; c < canali; c++) {
                    float v = dentro[riga + c];
                    if (v > fuori[destinazione + c]) fuori[destinazione + c] = v;
                }
            }
        }
    }
}
