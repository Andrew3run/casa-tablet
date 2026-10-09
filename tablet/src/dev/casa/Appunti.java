package dev.casa;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le note e le cose da fare, e la lista della spesa: quello che qualcuno ha
 * scritto o detto a Casa.
 *
 * <h3>Una lista sola, non due - e poi la spesa</h3>
 *
 * « Nota » e « cosa da fare » sembrano due cose e in una cucina sono la stessa:
 * « chiamare l'idraulico » e' una nota se la si legge e una cosa da fare se la
 * si spunta, e chi la detta non sta scegliendo fra due categorie. Quindi una
 * lista sola, e ogni riga si puo' spuntare: quelle spuntate scendono in fondo,
 * smorte, e dopo qualche giorno se ne vanno da sole.
 *
 * <b>La spesa invece e' un'altra lista</b>, e la differenza non e' di
 * categoria ma di <b>momento</b>: le cose da fare si guardano in casa, la spesa
 * si guarda al supermercato, dal telefono, e si spunta una riga dopo l'altra
 * davanti allo scaffale. Mescolate, « latte » starebbe fra « chiamare
 * l'idraulico » e « rinnovare la carta d'identita' », e il riposo leggerebbe
 * « uova » come una cosa da fare. Stanno nello stesso file e si comportano
 * uguali; cambia solo in quale linguetta si vedono ({@link #COSE},
 * {@link #SPESA}).
 *
 * <b>Le spuntate non spariscono subito</b>, ed e' voluto: spuntare per sbaglio
 * capita - un dito su un tablet appeso al muro - e una riga che si dissolve
 * all'istante non si sa piu' com'era scritta. Restano li', spente, e un secondo
 * tocco le rimette in piedi.
 *
 * <h3>Dove stanno</h3>
 *
 * In {@code note.json}, dentro {@link Archivio}, con la scrittura atomica di
 * tutto il resto - e qui conta piu' che altrove: una nota si scrive una volta
 * sola, e se si perde nessuno la riscrive perche' nessuno si accorge che manca.
 * Il file e' minuscolo e si rilegge solo all'avvio: in memoria c'e' la lista, ed
 * e' quella che comanda.
 *
 * Ogni riga ha un <b>numero suo</b>, che non cambia: serve al telefono, che
 * spunta « la riga 12 » e non « la terza riga » - fra la sua lettura e il suo
 * tocco la lista sul tablet puo' essersi riordinata.
 */
public final class Appunti {

    private static final String TAG = "Casa";
    private static final String FILE = "note.json";

    /** Le due liste. Sono anche le parole che viaggiano verso il telefono. */
    public static final String COSE = "cose", SPESA = "spesa";

    /** Quante righe si tengono per lista. Oltre non e' una lista, e' un
     *  archivio - e questo e' un tablet da muro, non un archivio. */
    private static final int TETTO = 60;

    /** Dopo quanto una riga spuntata se ne va da sola. Tre giorni: il tempo di
     *  accorgersi di averla spuntata per sbaglio, senza che la lista diventi un
     *  cimitero di cose gia' fatte. */
    private static final long QUANTO_RESTA_FATTA = 3L * 24 * 60 * 60 * 1000;

    /** Una riga della lista. */
    public static final class Nota {
        public final int id;
        /** {@link #COSE} o {@link #SPESA}. */
        public final String lista;
        public String testo;
        /** Spuntata? */
        public boolean fatta;
        /** Quando e' nata, e quando e' stata spuntata (0 se e' ancora da fare). */
        public long quando, finita;
        /** E' arrivata a voce. Serve solo a chi disegna: una nota dettata porta
         *  la punteggiatura del riconoscitore e non quella di chi scrive. */
        public boolean detta;

        Nota(int id, String testo, boolean detta, String lista) {
            this.id = id;
            this.testo = testo;
            this.detta = detta;
            this.lista = lista;
            this.quando = System.currentTimeMillis();
        }

        public boolean di(String quale) { return lista.equals(quale); }

        JSONObject json() throws org.json.JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("testo", testo);
            if (SPESA.equals(lista)) o.put("lista", SPESA);
            if (fatta) o.put("fatta", true);
            o.put("quando", quando);
            if (finita > 0) o.put("finita", finita);
            if (detta) o.put("detta", true);
            return o;
        }

        /** Dal file. Le righe scritte prima che le liste fossero due non hanno
         *  ne' numero ne' lista: prendono il numero di riserva e sono cose da
         *  fare, che e' quello che erano. */
        static Nota da(JSONObject o, int idDiRiserva) {
            String t = o.optString("testo", "").trim();
            if (t.length() == 0) return null;
            Nota n = new Nota(o.optInt("id", idDiRiserva), t, o.optBoolean("detta", false),
                    quale(o.optString("lista", COSE)));
            n.fatta = o.optBoolean("fatta", false);
            n.quando = o.optLong("quando", System.currentTimeMillis());
            n.finita = o.optLong("finita", 0);
            return n;
        }
    }

    /** Chi ridisegnare quando la lista cambia. Ce n'e' piu' d'uno - la sezione
     *  e il riposo - quindi si tengono in elenco invece di sostituirsi a
     *  vicenda: l'ultimo che si aggancia non deve spegnere il primo. */
    public interface Ascolto { void noteCambiate(); }

    private final Context contesto;
    private final List<Nota> elenco = new ArrayList<Nota>();
    private final List<Ascolto> chiGuarda = new ArrayList<Ascolto>();

    /** Le due liste gia' separate, rifatte a ogni cambio e non a ogni
     *  fotogramma: la pagina le chiede dentro onDraw, e filtrare li' vorrebbe
     *  dire una lista nuova sessanta volte al secondo. */
    private final List<Nota> cose = new ArrayList<Nota>();
    private final List<Nota> spesa = new ArrayList<Nota>();

    private int prossimoId = 1;

    public Appunti(Context c) {
        this.contesto = c.getApplicationContext();
        carica();
    }

    public void aggiungiAscolto(Ascolto a) {
        if (a != null && !chiGuarda.contains(a)) chiGuarda.add(a);
    }

    public void togliAscolto(Ascolto a) { chiGuarda.remove(a); }

    private void avvisa() {
        for (int i = 0; i < chiGuarda.size(); i++) chiGuarda.get(i).noteCambiate();
    }

    /** Una parola qualsiasi diventa una delle due liste: quel che non e' la
     *  spesa e' una cosa da fare. */
    public static String quale(String lista) {
        return SPESA.equals(lista) ? SPESA : COSE;
    }

    // ---- la lista ----------------------------------------------------------

    /**
     * Le righe di una lista, nell'ordine in cui si guardano: prima quelle da
     * fare - le piu' vecchie in cima, perche' sono quelle che aspettano da piu'
     * tempo - poi quelle spuntate. Non va modificata da fuori.
     */
    public List<Nota> tutte(String lista) {
        return SPESA.equals(lista) ? spesa : cose;
    }

    /** Solo le cose da fare ancora aperte. La usano il riposo e la voce, che
     *  della spesa non parlano. */
    public List<Nota> daFare() { return daFare(COSE); }

    public List<Nota> daFare(String lista) {
        List<Nota> fuori = new ArrayList<Nota>();
        for (Nota n : tutte(lista)) if (!n.fatta) fuori.add(n);
        return fuori;
    }

    public int quanteDaFare() { return quanteDaFare(COSE); }

    public int quanteDaFare(String lista) {
        int quante = 0;
        for (Nota n : tutte(lista)) if (!n.fatta) quante++;
        return quante;
    }

    public Nota trova(int id) {
        for (Nota n : elenco) if (n.id == id) return n;
        return null;
    }

    /** Una cosa da fare. */
    public Nota aggiungi(String testo, boolean detta) {
        return aggiungi(testo, detta, COSE);
    }

    /**
     * Ne aggiunge una. Torna la nota, o null se non c'era niente da aggiungere.
     *
     * <b>Le doppie non entrano.</b> Chi detta due volte la stessa cosa - e
     * capita, perche' la prima volta non si e' sicuri che Casa abbia sentito -
     * non vuole due righe uguali: si rimette in cima quella che c'e' gia'. E
     * nella spesa e' la regola che conta di piu': « latte » spuntato la
     * settimana scorsa torna da comprare, invece di stare due volte in lista.
     */
    public Nota aggiungi(String testo, boolean detta, String lista) {
        if (testo == null) return null;
        String t = pulisci(testo);
        if (t.length() == 0) return null;
        String l = quale(lista);

        for (Nota n : elenco) {
            if (!n.di(l) || !n.testo.equalsIgnoreCase(t)) continue;
            // C'era gia': se era spuntata torna da fare, e in ogni caso e'
            // quella che si sta guardando adesso.
            n.fatta = false;
            n.finita = 0;
            n.quando = System.currentTimeMillis();
            ordina();
            salva();
            avvisa();
            return n;
        }

        Nota n = new Nota(prossimoId++, t, detta, l);
        elenco.add(n);
        int inLista = 0;
        for (Nota x : elenco) if (x.di(l)) inLista++;
        while (inLista-- > TETTO) elenco.remove(piuVecchiaFatta(l));
        ordina();
        salva();
        avvisa();
        Log.i(TAG, (SPESA.equals(l) ? "spesa" : "nota") + ": \"" + t + "\"" + (detta ? " (dettata)" : ""));
        return n;
    }

    /**
     * Una frase della spesa, che puo' essere piu' cose: « latte, uova e il
     * pane » sono tre righe.
     *
     * Al supermercato si spunta una cosa per volta, e « Latte, uova e il pane »
     * su una riga sola si spunta quando si e' preso tutto - cioe' mai, perche'
     * il pane era finito. Gli articoli se ne vanno: « il pane » in lista si
     * legge « Pane ».
     *
     * Il prezzo e' « sale e pepe », che diventano due. Due righe si spuntano
     * lo stesso; una riga sola con tre cose dentro no.
     *
     * @return quante righe sono entrate.
     */
    public int aggiungiSpesa(String frase, boolean detta) {
        if (frase == null) return 0;
        String[] pezzi = frase.replace(" e ", ",").replace(" ed ", ",").split(",");
        int quante = 0;
        for (String p : pezzi) {
            String t = senzaArticolo(p.trim());
            if (t.length() >= 2 && aggiungi(t, detta, SPESA) != null) quante++;
        }
        return quante;
    }

    private static final String[] ARTICOLI = {
        "un po' di ", "un po di ", "po' di ", "po di ", "anche ",
        "dell'", "dello ", "della ", "degli ", "delle ", "dei ", "del ",
        "un'", "uno ", "una ", "un ", "l'", "il ", "lo ", "la ", "gli ", "le ", "i ",
    };

    private static String senzaArticolo(String testo) {
        String t = testo;
        boolean tolto = true;
        while (tolto) {
            tolto = false;
            String basso = t.toLowerCase(Locale.ITALIAN);
            for (String a : ARTICOLI) {
                if (basso.startsWith(a) && t.length() > a.length()) {
                    t = t.substring(a.length()).trim();
                    tolto = true;
                    break;
                }
            }
        }
        return t;
    }

    /** Spuntata diventa da fare, e viceversa. */
    public void inverti(Nota n) {
        if (n == null) return;
        n.fatta = !n.fatta;
        n.finita = n.fatta ? System.currentTimeMillis() : 0;
        ordina();
        salva();
        avvisa();
    }

    public void togli(Nota n) {
        if (n == null) return;
        elenco.remove(n);
        ordina();
        salva();
        avvisa();
    }

    /** Via tutte quelle spuntate di una lista. Torna quante ne ha tolte. */
    public int pulisciFatte(String lista) {
        String l = quale(lista);
        int quante = 0;
        for (int i = elenco.size() - 1; i >= 0; i--) {
            Nota n = elenco.get(i);
            if (n.fatta && n.di(l)) { elenco.remove(i); quante++; }
        }
        if (quante > 0) { ordina(); salva(); avvisa(); }
        return quante;
    }

    /**
     * La prima da fare, in una lista qualsiasi, che contiene queste parole.
     * Serve alla voce: « ho fatto la spesa » deve trovare « fare la spesa »
     * senza che siano scritte uguali, e « ho comprato il latte » deve trovare
     * « Latte » nella spesa.
     */
    public Nota cerca(String pezzo) {
        if (pezzo == null) return null;
        String p = Testo.senzaAccenti(pezzo.toLowerCase(Locale.ITALIAN)).trim();
        if (p.length() < 3) return null;
        Nota migliore = null;
        for (Nota n : elenco) {
            if (n.fatta) continue;
            String t = Testo.senzaAccenti(n.testo.toLowerCase(Locale.ITALIAN));
            if (t.contains(p) || p.contains(t)) {
                // Vince la piu' corta: fra « pane » e « comprare il pane e il
                // latte », chi dice « pane » intende la prima.
                if (migliore == null || t.length() < migliore.testo.length()) migliore = n;
            }
        }
        return migliore;
    }

    /** Da fare in cima, e dentro ogni gruppo la piu' vecchia per prima. Poi
     *  si rifanno le due liste separate. */
    private void ordina() {
        java.util.Collections.sort(elenco, new java.util.Comparator<Nota>() {
            @Override public int compare(Nota a, Nota b) {
                if (a.fatta != b.fatta) return a.fatta ? 1 : -1;
                return a.quando < b.quando ? -1 : (a.quando > b.quando ? 1 : 0);
            }
        });
        cose.clear();
        spesa.clear();
        for (Nota n : elenco) (n.di(SPESA) ? spesa : cose).add(n);
    }

    private int piuVecchiaFatta(String lista) {
        int piuVecchiaDaFare = -1;
        for (int i = elenco.size() - 1; i >= 0; i--) {
            Nota n = elenco.get(i);
            if (!n.di(lista)) continue;
            if (n.fatta) return i;
            piuVecchiaDaFare = i;
        }
        // nessuna fatta: se ne va la piu' vecchia da fare
        return Math.max(0, piuVecchiaDaFare);
    }

    /**
     * Il testo come va scritto in una lista.
     *
     * Il riconoscitore vocale scrive la prima lettera maiuscola e non mette il
     * punto; chi batte sul tastierino di Casa scrive tutto minuscolo, perche'
     * quella tastiera le maiuscole non le ha. Qui si pareggiano: prima lettera
     * grande, niente punto finale. Una lista in cui una riga su tre comincia
     * per minuscola si legge male, e non e' colpa di chi l'ha scritta.
     */
    private static String pulisci(String testo) {
        String t = testo.trim();
        while (t.endsWith(".") || t.endsWith(",")) t = t.substring(0, t.length() - 1).trim();
        if (t.length() == 0) return t;
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    // ---- il disco ----------------------------------------------------------

    private void carica() {
        elenco.clear();
        JSONObject o = Archivio.leggi(contesto, FILE);
        if (o != null) {
            prossimoId = Math.max(1, o.optInt("prossimoId", 1));
            JSONArray a = o.optJSONArray("note");
            long adesso = System.currentTimeMillis();
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject riga = a.optJSONObject(i);
                if (riga == null) continue;
                Nota n = Nota.da(riga, 0);
                if (n == null) continue;
                // Le spuntate da troppo tempo non si rileggono nemmeno: e' qui
                // che la lista si tiene corta da sola, senza che nessuno faccia
                // pulizia.
                if (n.fatta && n.finita > 0 && adesso - n.finita > QUANTO_RESTA_FATTA) continue;
                elenco.add(n);
            }
            for (Nota n : elenco) prossimoId = Math.max(prossimoId, n.id + 1);
            // Le righe di prima delle due liste arrivano senza numero: se ne
            // da' uno adesso, e il salvataggio qui sotto lo rende per sempre.
            boolean numerate = false;
            for (int i = 0; i < elenco.size(); i++) {
                Nota n = elenco.get(i);
                if (n.id > 0) continue;
                Nota nuova = new Nota(prossimoId++, n.testo, n.detta, n.lista);
                nuova.fatta = n.fatta;
                nuova.quando = n.quando;
                nuova.finita = n.finita;
                elenco.set(i, nuova);
                numerate = true;
            }
            if (numerate) salva();
        }
        ordina();
        if (!elenco.isEmpty()) Log.i(TAG, "note: " + stato());
    }

    private void salva() {
        try {
            JSONArray a = new JSONArray();
            for (Nota n : elenco) a.put(n.json());
            JSONObject o = new JSONObject();
            o.put("prossimoId", prossimoId);
            o.put("note", a);
            Archivio.scrivi(contesto, FILE, o);
        } catch (Exception e) {
            Log.w(TAG, "note: non ho potuto salvare", e);
        }
    }

    /** Una riga per il registro, per il PC. */
    public String stato() {
        return cose.size() + " note (" + quanteDaFare(COSE) + " da fare), "
                + spesa.size() + " nella spesa (" + quanteDaFare(SPESA) + " da comprare)";
    }
}
