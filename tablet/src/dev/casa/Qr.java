package dev.casa;

/**
 * Un codice QR, generato a mano.
 *
 * <b>Perche' scriverlo.</b> Serve per una cosa sola: l'indirizzo con cui si
 * approva Casa su Spotify, che compare una volta nella vita del tablet e va
 * inquadrato col telefono. Le librerie che lo fanno - ZXing - sono qualche
 * centinaio di kilobyte di dex e una dipendenza da tenere aggiornata, in un
 * progetto che non ne ha nessuna e che si compila senza Gradle. L'algoritmo
 * invece e' un algoritmo: si scrive, e da li' in poi e' fermo per sempre.
 *
 * <b>Cosa fa e cosa non fa.</b> Modalita' <i>byte</i>, correzione <b>L</b>,
 * versioni da 1 a 9 - fino a 230 caratteri, e un indirizzo di Spotify ne ha
 * un centinaio. Non fa kanji, non fa la modalita' numerica compatta, non fa le
 * versioni grandi: sono tutte cose che qui non servono, e ognuna sarebbe altro
 * codice da tenere giusto senza mai vederlo funzionare.
 *
 * <b>Come e' fatto</b>, nell'ordine in cui succede:
 * <ol>
 *   <li>si sceglie la versione piu' piccola in cui il testo ci sta;
 *   <li>i byte diventano un flusso di bit con intestazione, terminatore e
 *       riempimento;
 *   <li>si calcola la correzione d'errore Reed-Solomon su GF(256), e i blocchi
 *       si interlacciano;
 *   <li>si disegnano i quadrati di riferimento, gli allineamenti, i tempi;
 *   <li>i bit si posano a zig-zag dal basso a destra;
 *   <li>si provano tutte e otto le maschere e si tiene quella con la penalita'
 *       piu' bassa, che e' quella che i lettori sbagliano meno.
 * </ol>
 *
 * Restituisce una matrice di booleani: nero e' true. Chi disegna decide quanto
 * e' grande un modulo - vedi {@link SezioneMusica}.
 */
public final class Qr {

    /** Byte di dati per versione, con correzione L. */
    private static final int[] DATI = { 19, 34, 55, 80, 108, 136, 156, 194, 232 };

    /** Byte di correzione per blocco, e quanti blocchi. Da 6 a 9 i blocchi sono
     *  due e <b>uguali</b>: e' il motivo per cui ci si ferma a 9 e non a 10,
     *  dove diventano quattro di due misure diverse e l'interlacciamento
     *  richiede il doppio del codice per una capienza che non serve. */
    private static final int[] CORREZIONE = { 7, 10, 15, 20, 26, 18, 20, 24, 30 };
    private static final int[] BLOCCHI    = { 1, 1, 1, 1, 1, 2, 2, 2, 2 };

    /** Dove stanno i centri degli allineamenti, versione per versione. */
    private static final int[][] ALLINEAMENTI = {
        { },                 // 1
        { 6, 18 },
        { 6, 22 },
        { 6, 26 },
        { 6, 30 },
        { 6, 34 },
        { 6, 22, 38 },
        { 6, 24, 42 },
        { 6, 26, 46 },       // 9
    };

    private final int lato;
    private final boolean[][] moduli;     // true = nero
    private final boolean[][] fisso;      // true = non ci vanno dati

    private Qr(int lato) {
        this.lato = lato;
        moduli = new boolean[lato][lato];
        fisso = new boolean[lato][lato];
    }

    /**
     * Il codice per questo testo, oppure null se non ci sta o se qualcosa va
     * storto.
     *
     * null non e' un guasto da segnalare: chi disegna mostra l'indirizzo
     * scritto, che si puo' sempre ricopiare a mano.
     */
    public static boolean[][] per(String testo) {
        if (testo == null || testo.length() == 0) return null;
        try {
            byte[] dati = testo.getBytes("UTF-8");
            int versione = -1;
            for (int v = 1; v <= 9; v++) {
                // 4 bit di modalita' + 8 di lunghezza = un byte e mezzo.
                if (dati.length + 2 <= DATI[v - 1]) { versione = v; break; }
            }
            if (versione < 0) return null;

            Qr q = new Qr(17 + 4 * versione);
            byte[] parole = q.componi(dati, versione);
            q.disegnaFisso(versione);
            q.posa(parole);
            int maschera = q.scegliMaschera();
            q.applicaMaschera(maschera);
            q.scriviFormato(maschera);
            return q.moduli;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- i bit --------------------------------------------------------------

    /**
     * Dal testo alle parole di codice, correzione compresa.
     *
     * L'interlacciamento in fondo e' la parte che sembra arbitraria e non lo
     * e': i byte dei due blocchi si alternano proprio perche' una macchia di
     * sporco sul codice colpisca un po' dell'uno e un po' dell'altro, invece
     * di distruggerne uno intero - che e' l'unico caso che la correzione non
     * saprebbe recuperare.
     */
    private byte[] componi(byte[] dati, int versione) {
        int capienza = DATI[versione - 1];
        byte[] flusso = new byte[capienza];
        int bit = 0;

        bit = scriviBit(flusso, bit, 0x4, 4);              // modalita' byte
        bit = scriviBit(flusso, bit, dati.length, 8);      // quanti byte
        for (byte b : dati) bit = scriviBit(flusso, bit, b & 0xFF, 8);

        // Terminatore: fino a quattro zeri, e non di piu' se lo spazio finisce.
        int restano = capienza * 8 - bit;
        bit = scriviBit(flusso, bit, 0, Math.min(4, restano));
        // Si arrotonda al byte, poi si riempie alternando i due valori che la
        // norma prescrive.
        if (bit % 8 != 0) bit = scriviBit(flusso, bit, 0, 8 - bit % 8);
        boolean primo = true;
        while (bit < capienza * 8) {
            bit = scriviBit(flusso, bit, primo ? 0xEC : 0x11, 8);
            primo = !primo;
        }

        int quantiBlocchi = BLOCCHI[versione - 1];
        int correzione = CORREZIONE[versione - 1];
        int perBlocco = capienza / quantiBlocchi;

        byte[][] blocchi = new byte[quantiBlocchi][];
        byte[][] code = new byte[quantiBlocchi][];
        for (int b = 0; b < quantiBlocchi; b++) {
            blocchi[b] = new byte[perBlocco];
            System.arraycopy(flusso, b * perBlocco, blocchi[b], 0, perBlocco);
            code[b] = reedSolomon(blocchi[b], correzione);
        }

        byte[] fuori = new byte[capienza + correzione * quantiBlocchi];
        int k = 0;
        for (int i = 0; i < perBlocco; i++)
            for (int b = 0; b < quantiBlocchi; b++) fuori[k++] = blocchi[b][i];
        for (int i = 0; i < correzione; i++)
            for (int b = 0; b < quantiBlocchi; b++) fuori[k++] = code[b][i];
        return fuori;
    }

    private static int scriviBit(byte[] dove, int posizione, int valore, int quanti) {
        for (int i = quanti - 1; i >= 0; i--) {
            int b = (valore >>> i) & 1;
            if (b != 0) dove[posizione >>> 3] |= (byte) (1 << (7 - (posizione & 7)));
            posizione++;
        }
        return posizione;
    }

    // ---- Reed-Solomon su GF(256) -------------------------------------------

    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];
    static {
        // Il campo di Galois con polinomio 0x11D, quello della norma QR. Le
        // tavole si costruiscono una volta all'avvio: 512 interi, e da li' in
        // poi moltiplicare e' una somma di logaritmi.
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    private static int moltiplica(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    private static byte[] reedSolomon(byte[] dati, int quanti) {
        // Il polinomio generatore: (x - a^0)(x - a^1)...(x - a^(quanti-1)).
        int[] generatore = new int[quanti + 1];
        generatore[0] = 1;
        for (int i = 0; i < quanti; i++) {
            for (int j = i + 1; j > 0; j--) {
                generatore[j] = generatore[j - 1] ^ moltiplica(generatore[j], EXP[i]);
            }
            generatore[0] = moltiplica(generatore[0], EXP[i]);
        }

        int[] resto = new int[quanti];
        for (byte b : dati) {
            int fattore = (b & 0xFF) ^ resto[0];
            System.arraycopy(resto, 1, resto, 0, quanti - 1);
            resto[quanti - 1] = 0;
            for (int i = 0; i < quanti; i++) {
                resto[i] ^= moltiplica(generatore[quanti - 1 - i], fattore);
            }
        }
        byte[] fuori = new byte[quanti];
        for (int i = 0; i < quanti; i++) fuori[i] = (byte) resto[i];
        return fuori;
    }

    // ---- il disegno ---------------------------------------------------------

    private void metti(int x, int y, boolean nero) {
        if (x < 0 || y < 0 || x >= lato || y >= lato) return;
        moduli[y][x] = nero;
        fisso[y][x] = true;
    }

    private void disegnaFisso(int versione) {
        // I tempi: la riga e la colonna a scacchi che dicono al lettore quanto
        // e' grande un modulo.
        for (int i = 0; i < lato; i++) {
            metti(6, i, i % 2 == 0);
            metti(i, 6, i % 2 == 0);
        }

        riferimento(3, 3);
        riferimento(lato - 4, 3);
        riferimento(3, lato - 4);

        int[] centri = ALLINEAMENTI[versione - 1];
        for (int i = 0; i < centri.length; i++) {
            for (int j = 0; j < centri.length; j++) {
                // I tre angoli sono gia' occupati dai quadrati di riferimento.
                boolean angolo = (i == 0 && j == 0)
                              || (i == 0 && j == centri.length - 1)
                              || (i == centri.length - 1 && j == 0);
                if (!angolo) allineamento(centri[i], centri[j]);
            }
        }

        // Si prenota lo spazio del formato con un valore qualunque: conta che
        // quelle caselle risultino occupate prima che i dati comincino a
        // cercare posto.
        scriviFormato(0);
        if (versione >= 7) scriviVersione(versione);
    }

    /** Il quadrato grande d'angolo: sette per sette con la cornice bianca. */
    private void riferimento(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int distanza = Math.max(Math.abs(dx), Math.abs(dy));
                metti(cx + dx, cy + dy, distanza != 2 && distanza != 4);
            }
        }
    }

    /** Il quadratino di allineamento: cinque per cinque. */
    private void allineamento(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                metti(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    /** I quindici bit che dicono correzione e maschera, in due copie: se un
     *  angolo si rovina, il lettore usa l'altra. */
    private void scriviFormato(int maschera) {
        int dati = (1 << 3) | maschera;          // 01 = correzione L
        int resto = dati;
        for (int i = 0; i < 10; i++) resto = (resto << 1) ^ ((resto >>> 9) * 0x537);
        int bit = ((dati << 10) | resto) ^ 0x5412;

        for (int i = 0; i <= 5; i++) metti(8, i, bitDi(bit, i));
        metti(8, 7, bitDi(bit, 6));
        metti(8, 8, bitDi(bit, 7));
        metti(7, 8, bitDi(bit, 8));
        for (int i = 9; i < 15; i++) metti(14 - i, 8, bitDi(bit, i));

        for (int i = 0; i < 8; i++) metti(lato - 1 - i, 8, bitDi(bit, i));
        for (int i = 8; i < 15; i++) metti(8, lato - 15 + i, bitDi(bit, i));
        metti(8, lato - 8, true);                // il modulo sempre nero
    }

    /** Dalla versione 7 in su il codice dichiara quanto e' grande. */
    private void scriviVersione(int versione) {
        int resto = versione;
        for (int i = 0; i < 12; i++) resto = (resto << 1) ^ ((resto >>> 11) * 0x1F25);
        int bit = (versione << 12) | resto;
        for (int i = 0; i < 18; i++) {
            boolean nero = bitDi(bit, i);
            int a = lato - 11 + i % 3, b = i / 3;
            metti(a, b, nero);
            metti(b, a, nero);
        }
    }

    private static boolean bitDi(int valore, int posizione) {
        return ((valore >>> posizione) & 1) != 0;
    }

    /**
     * I dati, a zig-zag da in basso a destra.
     *
     * Due colonne per volta, su e giu' alternando, saltando la colonna sei -
     * che e' quella dei tempi - e tutte le caselle gia' occupate.
     */
    private void posa(byte[] parole) {
        int i = 0;
        for (int destra = lato - 1; destra >= 1; destra -= 2) {
            if (destra == 6) destra = 5;
            for (int passo = 0; passo < lato; passo++) {
                for (int j = 0; j < 2; j++) {
                    int x = destra - j;
                    boolean versoAlto = ((destra + 1) & 2) == 0;
                    int y = versoAlto ? lato - 1 - passo : passo;
                    if (fisso[y][x]) continue;
                    boolean nero = false;
                    if (i < parole.length * 8) {
                        nero = ((parole[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                    }
                    moduli[y][x] = nero;
                    i++;
                }
            }
        }
    }

    private boolean maschera(int quale, int x, int y) {
        switch (quale) {
            case 0:  return (x + y) % 2 == 0;
            case 1:  return y % 2 == 0;
            case 2:  return x % 3 == 0;
            case 3:  return (x + y) % 3 == 0;
            case 4:  return (x / 3 + y / 2) % 2 == 0;
            case 5:  return (x * y) % 2 + (x * y) % 3 == 0;
            case 6:  return ((x * y) % 2 + (x * y) % 3) % 2 == 0;
            default: return ((x + y) % 2 + (x * y) % 3) % 2 == 0;
        }
    }

    private void applicaMaschera(int quale) {
        for (int y = 0; y < lato; y++) {
            for (int x = 0; x < lato; x++) {
                if (!fisso[y][x] && maschera(quale, x, y)) moduli[y][x] = !moduli[y][x];
            }
        }
    }

    /**
     * Si provano tutte e otto e si tiene la meno peggio.
     *
     * La maschera non cambia i dati: cambia come si vedono. Serve a evitare
     * che il disegno finisca per contenere grandi zone uniformi o qualcosa che
     * somigli ai quadrati d'angolo, che e' esattamente cio' che manda in
     * confusione un lettore. La penalita' e' la formula della norma.
     */
    private int scegliMaschera() {
        int migliore = 0, minima = Integer.MAX_VALUE;
        for (int q = 0; q < 8; q++) {
            applicaMaschera(q);
            int p = penalita();
            applicaMaschera(q);          // due volte e' come non averla messa
            if (p < minima) { minima = p; migliore = q; }
        }
        return migliore;
    }

    private int penalita() {
        int totale = 0;

        // Regola 1: file di cinque o piu' dello stesso colore.
        for (int y = 0; y < lato; y++) totale += filaPenalita(y, true);
        for (int x = 0; x < lato; x++) totale += filaPenalita(x, false);

        // Regola 2: quadrati due per due di un colore solo.
        for (int y = 0; y < lato - 1; y++) {
            for (int x = 0; x < lato - 1; x++) {
                boolean a = moduli[y][x];
                if (a == moduli[y][x + 1] && a == moduli[y + 1][x] && a == moduli[y + 1][x + 1]) {
                    totale += 3;
                }
            }
        }

        // Regola 3: la sequenza che somiglia a un quadrato d'angolo.
        for (int y = 0; y < lato; y++) {
            for (int x = 0; x < lato - 10; x++) {
                if (somiglia(x, y, true)) totale += 40;
            }
        }
        for (int x = 0; x < lato; x++) {
            for (int y = 0; y < lato - 10; y++) {
                if (somiglia(x, y, false)) totale += 40;
            }
        }

        // Regola 4: quanto ci si allontana da meta' neri e meta' bianchi.
        int neri = 0;
        for (int y = 0; y < lato; y++)
            for (int x = 0; x < lato; x++) if (moduli[y][x]) neri++;
        int percento = neri * 100 / (lato * lato);
        totale += Math.abs(percento - 50) / 5 * 10;

        return totale;
    }

    private int filaPenalita(int quale, boolean orizzontale) {
        int totale = 0, lunghezza = 1;
        boolean precedente = orizzontale ? moduli[quale][0] : moduli[0][quale];
        for (int i = 1; i < lato; i++) {
            boolean adesso = orizzontale ? moduli[quale][i] : moduli[i][quale];
            if (adesso == precedente) {
                lunghezza++;
            } else {
                if (lunghezza >= 5) totale += 3 + (lunghezza - 5);
                precedente = adesso;
                lunghezza = 1;
            }
        }
        if (lunghezza >= 5) totale += 3 + (lunghezza - 5);
        return totale;
    }

    /** Nero-bianco-nero-nero-nero-bianco-nero con quattro bianchi da una parte:
     *  e' il disegno del quadrato d'angolo, e non deve comparire altrove. */
    private boolean somiglia(int x, int y, boolean orizzontale) {
        final boolean[] motivo = { true, false, true, true, true, false, true,
                                   false, false, false, false };
        boolean avanti = true, indietro = true;
        for (int i = 0; i < 11; i++) {
            boolean m = orizzontale ? moduli[y][x + i] : moduli[y + i][x];
            if (m != motivo[i]) avanti = false;
            if (m != motivo[10 - i]) indietro = false;
        }
        return avanti || indietro;
    }
}
