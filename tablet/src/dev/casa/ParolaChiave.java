package dev.casa;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Riconosce « Hey Home » a bordo, senza rete e senza chiavi.
 *
 * <h3>Cosa fa, in una riga</h3>
 *
 * Confronta l'impronta di quello che ha appena sentito con le impronte delle
 * registrazioni in {@code parola/si}, e se la piu' vicina e' abbastanza vicina
 * dice di si'.
 *
 * <h3>Perche' DTW e non una rete</h3>
 *
 * Una rete neurale come quella dietro « Hey Google » riconosce chiunque, anche
 * a quattro metri con la televisione accesa, e sta in poche centinaia di
 * kilobyte. Ma la sua bravura non e' nell'architettura: e' nelle migliaia di
 * persone che le hanno detto la frase in migliaia di stanze. Quelle qui non ci
 * sono, e la strada per averle - allenare su voce sintetica sul PC - e' un
 * pezzo a se' che comincia dai file che questa classe usa gia'.
 *
 * La DTW e' la versione povera e onesta: riconosce <b>chi ha registrato</b>,
 * in una stanza <b>come quella dove ha registrato</b>. In cambio non ha
 * bisogno di allenamento, sta in trecento righe, e funziona il pomeriggio in
 * cui la si scrive. I file che raccoglie sono gli stessi che serviranno alla
 * rete: nessuno di questi campioni si butta.
 *
 * <h3>Perche' la deformazione temporale serve davvero</h3>
 *
 * « Hey Home » detto in fretta dura mezzo secondo, detto piano ne dura uno. Le
 * due impronte hanno un numero diverso di righe, e allineare la prima riga con
 * la prima e l'ultima con l'ultima metterebbe la « h » di "Home" di una sopra
 * la « ey » dell'altra. La DTW cerca <b>l'allineamento migliore</b> fra i due,
 * lasciando che una riga di qua ne valga due di la' dove serve, e restituisce
 * quanto costa quel migliore allineamento. E' la distanza che ne esce.
 *
 * <h3>Le due strette che la rendono utilizzabile</h3>
 *
 * <ul>
 * <li><b>La banda di Sakoe-Chiba.</b> Senza vincoli, la DTW puo' allineare
 *     mezzo secondo di silenzio con due secondi di parlato: costa poco e
 *     accetta tutto. Limitando lo scarto fra gli indici, un allineamento
 *     sensato resta possibile e uno assurdo no. Costa anche molto meno:
 *     la matrice si riempie solo lungo la diagonale.</li>
 * <li><b>Il rifiuto per lunghezza.</b> Prima ancora di calcolare, se il pezzo
 *     dura meno di meta' o piu' del doppio di un modello, quel modello si
 *     salta. E' il novanta per cento dei confronti risparmiati su cose che non
 *     avevano speranza.</li>
 * </ul>
 *
 * <h3>La soglia non si indovina</h3>
 *
 * Il numero che esce dalla DTW non vuol dire niente da solo: dipende da quanti
 * cepstri, da come sono normalizzati, da come si conta il cammino. L'unico modo
 * di scegliere la soglia e' <b>misurarla</b> sui campioni veri, ed e' quello
 * che fa {@link #tara}: quanto valgono i « si » e quanto valgono i « no ».
 */
public final class ParolaChiave {

    /** Sotto {@code Casa} come tutto il resto, col nome del pezzo nel
     *  messaggio: il perche' sta su {@link MainActivity#TAG}. */
    private static final String TAG = MainActivity.TAG;

    /**
     * Quanto puo' scostarsi l'allineamento, in righe di impronta.
     *
     * Venticinque righe sono un quarto di secondo: dentro ci sta tutta la
     * differenza fra un "Hey Home" detto in fretta e uno detto piano, e non ci
     * sta l'allineamento fantasioso che accetterebbe qualunque cosa.
     */
    private static final int BANDA = 25;

    /** Oltre questo rapporto fra le durate non si confronta nemmeno. */
    private static final float RAPPORTO = 2.0f;

    /** Quanti modelli tenere in memoria. Sono cinque kilobyte l'uno; il tetto
     *  non e' per la memoria ma per il tempo: ogni modello e' una DTW in piu'
     *  su ogni pezzo di parlato che passa. */
    private static final int MASSIMO_MODELLI = 24;

    /** Un valore piu' alto di qualunque distanza vera: vuol dire « nessun
     *  confronto e' stato possibile ». */
    public static final float LONTANO = 99f;

    /** Un modello: l'impronta di una registrazione, e da quale file viene. */
    private static final class Modello {
        final float[][] impronta;
        final String nome;
        Modello(float[][] impronta, String nome) { this.impronta = impronta; this.nome = nome; }
    }

    /**
     * Le due righe di appoggio della DTW.
     *
     * Cresciute quanto basta e poi mai piu' riallocate: la DTW gira su ogni
     * pezzo di parlato che passa davanti al microfono, cioe' spesso.
     *
     * <b>Sono un oggetto e non due campi</b> perche' il rilevatore e la
     * taratura girano su due thread diversi - il primo sul thread del
     * microfono, la seconda su un thread suo che ci mette secondi - e
     * condividere le righe vorrebbe dire che un aggancio e una taratura
     * avviati insieme si scrivono addosso. Chi tara si porta i suoi.
     */
    private static final class Banchi {
        float[] prima = new float[0], dopo = new float[0];
    }

    private final Mfcc mfcc = new Mfcc();
    private final List<Modello> modelli = new ArrayList<Modello>();
    private final Banchi banchi = new Banchi();

    private float soglia = 3.2f;
    private float rincaro = 0.45f;

    // ---- i modelli ---------------------------------------------------------

    /**
     * Rilegge le registrazioni e ne rifa' le impronte.
     *
     * Costa: leggere venti WAV e calcolarne le MFCC sono qualche centinaio di
     * millisecondi. Si chiama quando i campioni cambiano - all'avvio, dopo una
     * registrazione, dopo uno scambio con il PC - non a ogni aggancio. <b>Non
     * dal thread dell'interfaccia.</b>
     */
    public synchronized int ricarica(Campioni campioni) {
        modelli.clear();
        List<File> file = campioni.elenco(Campioni.SI);
        // Dai piu' recenti: se sono piu' del tetto, quelli che restano fuori
        // devono essere i vecchi. Chi registra di nuovo lo fa perche' i vecchi
        // non andavano bene.
        for (int i = file.size() - 1; i >= 0 && modelli.size() < MASSIMO_MODELLI; i--) {
            File f = file.get(i);
            short[] suono = Onda.leggi(f);
            if (suono == null) continue;
            float[][] impronta = mfcc.impronta(suono);
            if (impronta == null) {
                Log.w(TAG, "parola: " + f.getName() + ": troppo corto per un'impronta");
                continue;
            }
            modelli.add(new Modello(impronta, f.getName()));
        }
        Log.i(TAG, "parola: modelli pronti: " + modelli.size() + " su " + file.size() + " campioni");
        return modelli.size();
    }

    public synchronized int quantiModelli() { return modelli.size(); }

    public synchronized boolean pronta() { return !modelli.isEmpty(); }

    /**
     * Quanto dura la parola, in righe di impronta - cioe' in centesimi di
     * secondo.
     *
     * <b>La mediana e non la media.</b> Fra i campioni ce n'e' sempre uno in
     * cui si e' tossito, o in cui si e' cominciato a parlare prima che partisse
     * la registrazione: e' lungo il doppio degli altri, e nella media pesa
     * quanto tutti gli altri messi insieme. La mediana non se ne accorge.
     *
     * Serve all'orecchio per sapere quanto lungo dev'essere il tratto da
     * consegnare: e' un numero che non si sceglie, si legge dai campioni.
     */
    public synchronized int durataTipica() {
        if (modelli.isEmpty()) return 0;
        int[] durate = new int[modelli.size()];
        for (int i = 0; i < modelli.size(); i++) durate[i] = modelli.get(i).impronta.length;
        java.util.Arrays.sort(durate);
        return durate[durate.length / 2];
    }

    public void setSoglia(float s) { soglia = s; }
    public float soglia() { return soglia; }
    public void setRincaro(float r) { rincaro = r; }

    /**
     * La soglia da usare adesso.
     *
     * Mentre suona qualcosa si alza l'asticella. Non e' pignoleria: la radio
     * che esce dall'altoparlante venti centimetri sotto il microfono somiglia a
     * parlato quanto basta, e un falso aggancio col telegiornale acceso vuol
     * dire che Casa si mette ad ascoltare da sola ogni due minuti.
     */
    public float sogliaAdesso(boolean qualcunoSuona) {
        return qualcunoSuona ? soglia - rincaro : soglia;
    }

    // ---- il confronto ------------------------------------------------------

    /**
     * Quanto somiglia alla parola: piu' basso, piu' somiglia.
     *
     * Torna {@link #LONTANO} se non c'e' nessun modello o se il pezzo non era
     * confrontabile con nessuno - troppo corto, troppo lungo.
     */
    public synchronized float punteggio(short[] suono, int da, int quanti) {
        return punteggio(mfcc.impronta(suono, da, quanti));
    }

    /** L'impronta di un pezzo di suono, con l'unico {@link Mfcc} di questo
     *  oggetto. Serve a chi vuole il punteggio e il nome del vincitore senza
     *  rifare due volte lo stesso calcolo. */
    public synchronized float[][] impronta(short[] suono, int da, int quanti) {
        return mfcc.impronta(suono, da, quanti);
    }

    public synchronized float punteggio(float[][] impronta) {
        if (impronta == null || modelli.isEmpty()) return LONTANO;
        float migliore = LONTANO;
        for (int i = 0; i < modelli.size(); i++) {
            float[][] m = modelli.get(i).impronta;
            if (!confrontabili(impronta.length, m.length)) continue;
            float d = dtw(banchi, impronta, m, migliore);
            if (d < migliore) migliore = d;
        }
        return migliore;
    }

    /** Il nome del campione piu' vicino, per la taratura e per il pannello. */
    public synchronized String chiHaVinto(float[][] impronta) {
        if (impronta == null || modelli.isEmpty()) return null;
        float migliore = LONTANO;
        String nome = null;
        for (int i = 0; i < modelli.size(); i++) {
            Modello mo = modelli.get(i);
            if (!confrontabili(impronta.length, mo.impronta.length)) continue;
            float d = dtw(banchi, impronta, mo.impronta, migliore);
            if (d < migliore) { migliore = d; nome = mo.nome; }
        }
        return nome;
    }

    private static boolean confrontabili(int a, int b) {
        if (a <= 0 || b <= 0) return false;
        return a <= b * RAPPORTO && b <= a * RAPPORTO;
    }

    /**
     * La distanza fra due impronte, normalizzata.
     *
     * <b>Il passo simmetrico, e perche' la normalizzazione e' esatta.</b> Da
     * ogni casella si puo' arrivare in diagonale, da sinistra o dall'alto. La
     * diagonale avanza di uno su tutte e due le impronte, le altre due di uno
     * su una sola: pesando la diagonale il doppio, <b>qualunque</b> cammino da
     * (0,0) a (n,m) ha peso totale n+m. Dividere per n+m da' quindi una
     * distanza media per riga, confrontabile fra pezzi di durata diversa -
     * senza dover portarsi dietro una seconda matrice per contare i passi.
     *
     * <b>Il taglio.</b> Se la riga migliore ha gia' superato il meglio trovato
     * finora - riportato alla lunghezza intera - il resto non potra' che
     * peggiorare, perche' i costi sono tutti positivi. Si esce. Con venti
     * modelli e' la differenza fra venti DTW intere e due.
     */
    private static float dtw(Banchi banchi, float[][] a, float[][] b, float megliaFinora) {
        int n = a.length, m = b.length;
        if (banchi.prima.length < m + 1) {
            banchi.prima = new float[m + 1];
            banchi.dopo = new float[m + 1];
        }
        float[] prima = banchi.prima, dopo = banchi.dopo;
        final float infinito = Float.MAX_VALUE / 4f;
        float tagliaA = megliaFinora >= LONTANO ? infinito : megliaFinora * (n + m);

        // La banda deve almeno contenere la differenza di lunghezza, o non
        // esiste nessun cammino da un angolo all'altro e la distanza torna
        // infinita anche fra due impronte simili di durata diversa.
        int banda = Math.max(BANDA, Math.abs(n - m) + 2);

        for (int j = 0; j <= m; j++) prima[j] = infinito;
        prima[0] = 0f;

        for (int i = 1; i <= n; i++) {
            int da = Math.max(1, i - banda), a2 = Math.min(m, i + banda);
            for (int j = 0; j < da; j++) dopo[j] = infinito;
            for (int j = a2 + 1; j <= m; j++) dopo[j] = infinito;
            dopo[0] = infinito;

            float migliorRiga = infinito;
            float[] ai = a[i - 1];
            for (int j = da; j <= a2; j++) {
                float costo = distanza(ai, b[j - 1]);
                float diagonale = prima[j - 1] + 2f * costo;
                float alto      = prima[j] + costo;
                float sinistra  = dopo[j - 1] + costo;
                float min = diagonale < alto ? diagonale : alto;
                if (sinistra < min) min = sinistra;
                dopo[j] = min;
                if (min < migliorRiga) migliorRiga = min;
            }
            if (migliorRiga >= tagliaA) return LONTANO;

            float[] scambio = prima; prima = dopo; dopo = scambio;
        }
        // Dopo lo scambio dell'ultimo giro e' prima[] a tenere la riga n.
        banchi.prima = prima; banchi.dopo = dopo;
        float totale = prima[m];
        if (totale >= infinito) return LONTANO;
        return totale / (n + m);
    }

    /**
     * La distanza fra due impronte, per chi vuole misurarla da fuori.
     *
     * Esiste per la <b>prova di parita'</b> (pc/parola/paritadtw.ps1): il
     * confronto e' scritto due volte, qui e in pc/parola/dtw.py, e le soglie
     * si scelgono sui numeri del secondo. Se le due scale non coincidessero,
     * la soglia misurata sul PC sarebbe un altro numero sul tablet - e il
     * sintomo non sarebbe un errore, sarebbe un tablet che non si sveglia.
     *
     * La prova gira <b>questo</b> codice, non una copia: una copia che
     * coincide con se stessa non dimostra niente.
     */
    public static float distanzaFra(float[][] a, float[][] b) {
        if (a == null || b == null) return LONTANO;
        if (!confrontabili(a.length, b.length)) return LONTANO;
        return dtw(new Banchi(), a, b, LONTANO);
    }

    /** Distanza euclidea fra due righe di impronta. */
    private static float distanza(float[] a, float[] b) {
        float somma = 0f;
        for (int d = 0; d < Mfcc.DIMENSIONI; d++) {
            float s = a[d] - b[d];
            somma += s * s;
        }
        return (float) Math.sqrt(somma);
    }

    // ---- la taratura -------------------------------------------------------

    /** Il risultato di una taratura, da mostrare e da salvare. */
    public static final class Taratura {
        public int quantiSi, quantiNo;
        /** Il punteggio di ogni « si », contro tutti i modelli tranne se stesso. */
        public float[] punteggiSi = new float[0];
        /** Il punteggio peggiore - cioe' piu' basso - trovato dentro ogni « no ». */
        public float[] punteggiNo = new float[0];
        public float sogliaProposta;
        /** Quanti « si » aggancerebbero con la soglia proposta. */
        public int presi;
        /** Quanti « no » aggancerebbero. Deve essere zero. */
        public int falsi;
        /** Il margine fra il peggiore « si » preso e il migliore « no ». Sotto
         *  0,2 la separazione e' fortuita. */
        public float margine;
        public String racconto = "";
    }

    /**
     * Misura quanto valgono i campioni, e propone una soglia.
     *
     * <h3>Uno alla volta, e mai contro se stesso</h3>
     *
     * Ogni « si » viene confrontato con tutti i modelli <b>tranne il proprio</b>.
     * Confrontarlo anche con se stesso darebbe distanza zero e una soglia
     * bassissima che poi non aggancia nessuna parola nuova: e' l'errore che si
     * fa una volta sola, e la prima volta sembra che funzioni benissimo.
     *
     * <h3>I « no » si percorrono tutti</h3>
     *
     * Di un « no » non basta un punteggio: dentro cinque secondi di
     * telegiornale c'e' un mezzo secondo che somiglia a "Hey Home" piu' di
     * tutto il resto, ed e' <b>quello</b> che farebbe scattare l'aggancio nella
     * vita vera. Quindi si fa scorrere una finestra lungo tutta la
     * registrazione e si tiene il punteggio <b>piu' basso</b>: il caso
     * peggiore, che e' l'unico che conta per una soglia.
     *
     * <h3>La soglia proposta</h3>
     *
     * La piu' alta che non aggancia nemmeno un « no », meno un margine di
     * sicurezza. Se non c'e' nessun « no » si ripiega sulla distribuzione dei
     * « si », dicendolo: una soglia scelta senza negativi e' una scommessa.
     *
     * <b>Non dal thread dell'interfaccia</b>: sono secondi, non millisecondi.
     */
    public Taratura tara(Campioni campioni) {
        Taratura t = new Taratura();
        // Tutto quello che serve alla taratura e' suo: cosi' puo' girare
        // mentre l'orecchio e' acceso, senza pestarsi i piedi con lui.
        Mfcc locale = new Mfcc();
        Banchi suoi = new Banchi();

        List<File> siFile = campioni.elenco(Campioni.SI);
        List<float[][]> si = new ArrayList<float[][]>();
        for (File f : siFile) {
            short[] suono = Onda.leggi(f);
            if (suono == null) continue;
            float[][] imp = locale.impronta(suono);
            if (imp != null) si.add(imp);
        }
        t.quantiSi = si.size();
        if (si.size() < 2) {
            t.racconto = "Servono almeno due « si »: con uno solo non c'e' niente da "
                       + "confrontare, e la soglia sarebbe un numero inventato.";
            t.sogliaProposta = soglia;
            return t;
        }

        // I « si », uno contro tutti gli altri.
        t.punteggiSi = new float[si.size()];
        for (int i = 0; i < si.size(); i++) {
            float migliore = LONTANO;
            for (int j = 0; j < si.size(); j++) {
                if (i == j) continue;
                if (!confrontabili(si.get(i).length, si.get(j).length)) continue;
                float d = dtw(suoi, si.get(i), si.get(j), migliore);
                if (d < migliore) migliore = d;
            }
            t.punteggiSi[i] = migliore;
        }

        // I « no », a finestra scorrevole.
        List<File> noFile = campioni.elenco(Campioni.NO);
        List<Float> peggiori = new ArrayList<Float>();
        int durataTipica = duratatipica(si);
        for (File f : noFile) {
            short[] suono = Onda.leggi(f);
            if (suono == null) continue;
            float p = peggiorCaso(suoi, locale, si, suono, durataTipica);
            if (p < LONTANO) peggiori.add(p);
        }
        t.quantiNo = peggiori.size();
        t.punteggiNo = new float[peggiori.size()];
        for (int i = 0; i < peggiori.size(); i++) t.punteggiNo[i] = peggiori.get(i);

        decidi(t);
        return t;
    }

    /** La durata tipica di un « si », in righe di impronta. */
    private static int duratatipica(List<float[][]> si) {
        int somma = 0;
        for (float[][] s : si) somma += s.length;
        return Math.max(20, somma / si.size());
    }

    /**
     * Il punteggio peggiore - cioe' piu' basso - dentro una registrazione
     * negativa, con una finestra lunga quanto la parola che scorre a passi di
     * dieci righe, cioe' un decimo di secondo.
     */
    private static float peggiorCaso(Banchi banchi, Mfcc locale, List<float[][]> modelli,
                                     short[] suono, int durata) {
        float[][] tutta = locale.impronta(suono);
        if (tutta == null) return LONTANO;
        if (tutta.length <= durata) return contro(banchi, modelli, tutta);

        // Le righe si prestano invece di ricopiarsi: una finestra e' un array
        // di riferimenti alle righe che gia' esistono dentro l'impronta
        // intera. Su cinque secondi di telegiornale sono cinquanta finestre, e
        // ricopiarle vorrebbe dire cinquanta volte tredicimila float.
        float[][] pezzo = new float[durata][];
        float peggiore = LONTANO;
        for (int inizio = 0; inizio + durata <= tutta.length; inizio += 10) {
            for (int r = 0; r < durata; r++) pezzo[r] = tutta[inizio + r];
            float d = contro(banchi, modelli, pezzo);
            if (d < peggiore) peggiore = d;
        }
        return peggiore;
    }

    private static float contro(Banchi banchi, List<float[][]> modelli, float[][] impronta) {
        float migliore = LONTANO;
        for (float[][] m : modelli) {
            if (!confrontabili(impronta.length, m.length)) continue;
            float d = dtw(banchi, impronta, m, migliore);
            if (d < migliore) migliore = d;
        }
        return migliore;
    }

    /**
     * Quanto sotto un punteggio vuol dire « e' lo stesso file due volte ».
     *
     * Due registrazioni diverse della stessa parola, anche dette di fila, non
     * scendono mai sotto qualche decimo: sotto questo valore non c'e' una
     * somiglianza, c'e' una copia.
     */
    private static final float SOSPETTO_COPIA = 0.05f;

    /** Da due elenchi di punteggi alla soglia, e alle parole per spiegarla. */
    private static void decidi(Taratura t) {
        float[] si = ordinata(t.punteggiSi);
        float[] no = ordinata(t.punteggiNo);

        // Le copie si segnalano, perche' avvelenano tutto quello che segue:
        // un campione che ne ha un gemello esatto ottiene distanza zero, il
        // novantesimo percentile crolla, e la soglia proposta diventa cosi'
        // bassa che poi non aggancia piu' nessuna parola nuova. Succede per
        // davvero: basta rimandare al tablet dal PC dei file che ci sono gia'
        // con un altro nome.
        int copie = 0;
        for (float p : si) if (p < SOSPETTO_COPIA) copie++;
        if (copie > 0) {
            t.racconto = copie + " campioni « si » sono identici a un altro: toglili, "
                       + "o la soglia viene fuori sbagliata. ";
        }

        // Il novantesimo percentile dei « si »: la soglia che ne prende nove su
        // dieci. Prenderli tutti vorrebbe dire farsi dettare la soglia dal
        // campione peggiore, che di solito e' quello in cui si e' tossito.
        float noveSuDieci = si.length == 0 ? 3.2f : si[Math.min(si.length - 1,
                (int) Math.ceil(si.length * 0.9f) - 1)];

        if (no.length == 0) {
            t.sogliaProposta = noveSuDieci;
            t.racconto += "Nessun « no » registrato: la soglia e' presa dai soli « si » e "
                       + "non e' verificata. Registra qualche minuto di rumore di casa e "
                       + "di parlato normale, poi ritara.";
        } else {
            float miglioreNo = no[0];               // il « no » piu' pericoloso
            // A meta' strada, in modo che ne' un « si » un po' storto ne' un
            // « no » un po' fortunato cadano dalla parte sbagliata.
            float proposta = Math.min(noveSuDieci, miglioreNo - 0.15f);
            if (proposta > noveSuDieci) proposta = noveSuDieci;
            t.sogliaProposta = proposta;
            t.margine = miglioreNo - noveSuDieci;

            if (t.margine <= 0f) {
                t.racconto += "I « no » entrano nel campo dei « si »: con questi campioni non "
                           + "esiste una soglia che prenda la parola senza prendere anche il "
                           + "resto. Servono piu' « si », detti come si dira' davvero.";
            } else if (t.margine < 0.2f) {
                t.racconto += "Separati per poco (" + arrotonda(t.margine) + "): funzionera' in "
                           + "silenzio e non con la radio accesa. Piu' « si » da lontano.";
            } else {
                t.racconto += "Separazione buona: " + arrotonda(t.margine) + " fra il peggior "
                           + "« si » preso e il « no » piu' vicino.";
            }
        }

        for (float p : t.punteggiSi) if (p <= t.sogliaProposta) t.presi++;
        for (float p : t.punteggiNo) if (p <= t.sogliaProposta) t.falsi++;
    }

    private static float[] ordinata(float[] v) {
        float[] c = new float[v.length];
        System.arraycopy(v, 0, c, 0, v.length);
        java.util.Arrays.sort(c);
        return c;
    }

    static String arrotonda(float v) {
        return String.format(java.util.Locale.ITALIAN, "%.2f", v);
    }
}
