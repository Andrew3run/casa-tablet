package dev.casa;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Le registrazioni con cui Casa impara la parola di attivazione.
 *
 * <h3>Due cartelle, e la seconda conta quanto la prima</h3>
 *
 * <pre>
 *     parola/si/    "Hey Home" detto da chi vive qui
 *     parola/no/    tutto il resto: parlato normale, la radio, la cucina
 * </pre>
 *
 * I <b>si</b> diventano le impronte da confrontare. I <b>no</b> non entrano
 * mai in un'impronta: servono a scegliere la soglia. Senza di loro la soglia
 * si sceglie a occhio, e una soglia scelta a occhio o non aggancia mai o
 * aggancia il telegiornale. Un quarto d'ora di rumore di casa vale piu' di
 * altri dieci "Hey Home".
 *
 * <h3>Perche' stanno sulla memoria esterna e non in getFilesDir()</h3>
 *
 * Tutto il resto di Casa - sveglie, lampade, stazioni - vive in
 * {@code getFilesDir()}, dove non arriva nessun altro. Questi no, e di
 * proposito: <b>devono passare dal PC</b>. Si scaricano per ascoltarli, si
 * aumentano, e tornano indietro moltiplicati. Da {@code getFilesDir()} si
 * tirerebbero fuori solo con {@code run-as}, che vuole un APK debuggabile -
 * e questo e' firmato per stare appeso a un muro, non per essere debuggato.
 *
 * {@code getExternalFilesDir()} invece e' leggibile e scrivibile da
 * {@code adb} senza root e senza permessi, e Android la cancella insieme
 * all'app. Il prezzo e' che qualunque app con il permesso di lettura le
 * vedrebbe: sono registrazioni di due parole dette al muro, e vale la pena.
 *
 *     adb pull /sdcard/Android/data/dev.casa/files/parola
 *
 * <h3>Il nome dice tutto quello che serve</h3>
 *
 * {@code si-20260904-153012-cucina.wav}: il tipo, quando, e da dove viene.
 * L'ultimo pezzo lo mette chi registra - « cucina », « lontano », « radio » -
 * e serve a capire, guardando la taratura, <i>quali</i> campioni non
 * agganciano. Senza etichetta si scopre che la soglia non regge, con
 * l'etichetta si scopre che non regge da tre metri.
 */
public final class Campioni {

    /** Sotto {@code Casa} come tutto il resto, col nome del pezzo nel
     *  messaggio: il perche' sta su {@link MainActivity#TAG}. */
    private static final String TAG = MainActivity.TAG;

    /** La parola. Sta qui e non in tre punti diversi: e' scritta anche a
     *  schermo e detta anche nei documenti. */
    public static final String FRASE = "Hey Home";

    public static final int SI = 0, NO = 1;

    private static final String[] CARTELLA = { "si", "no" };

    /** Le impostazioni della parola: accesa, soglia, sorgente del microfono. */
    private static final String FILE_STATO = "parola.json";

    private final Context contesto;
    private final File radice;

    public Campioni(Context c) {
        this.contesto = c;
        File esterna = c.getExternalFilesDir(null);
        // Se la memoria esterna non c'e' - non succede su questo tablet, dove
        // e' emulata sulla stessa partizione, ma succede in astratto - si
        // ripiega su quella privata: meglio campioni che il PC non vede che
        // nessun campione.
        File base = esterna != null ? esterna : c.getFilesDir();
        radice = new File(base, "parola");
        for (String nome : CARTELLA) {
            File f = new File(radice, nome);
            if (!f.exists() && !f.mkdirs()) Log.w(TAG, "campioni: non riesco a creare " + f);
        }
    }

    /** Dove stanno, per scriverlo a schermo e nei documenti. */
    public String dove() { return radice.getAbsolutePath(); }

    // ---- la dotazione di serie ---------------------------------------------

    /** Il segno che i campioni di serie sono gia' stati messi. Basta che
     *  esista: il contenuto non conta. */
    private static final String FATTO = ".base-messi";

    /**
     * Copia in {@code parola/} le registrazioni che arrivano dentro l'app.
     *
     * <h3>Perche' l'app se le porta dietro</h3>
     *
     * Un tablet appena installato non ha campioni, quindi la parola di
     * attivazione e' spenta e non c'e' modo di accenderla senza registrarne -
     * e da quando il pannello si apre <b>solo</b> dal PC, senza il PC non si
     * arriva nemmeno a registrare. Con quattro « Hey Home » e due minuti di
     * rumore gia' dentro l'APK, un'installazione da zero riconosce la parola
     * appena finisce di avviarsi.
     *
     * Sono registrazioni fatte con <b>questo</b> microfono in <b>questa</b>
     * cucina, che e' esattamente la condizione in cui la DTW funziona. Su un
     * altro apparecchio non varrebbero niente - ma questo progetto e' di un
     * tablet solo.
     *
     * <h3>I « si » invece tornano sempre</h3>
     *
     * Il segno {@code .base-messi} vale per i rumori: chi li toglie li ha
     * tolti apposta, e ritrovarseli al riavvio sarebbe un'app che rifiuta di
     * dimenticare.
     *
     * <b>Per i « si » no, e il motivo e' cambiato per strada.</b> Finche' la
     * DTW era l'unico rilevatore, restare senza campioni voleva dire spegnere
     * una comodita'. Adesso i campioni sono meta' del rilevatore - la rete
     * filtra, loro confermano (vedi {@link Risveglio#conConferma}) - e
     * restare senza vuol dire che il tablet non si sveglia piu', oppure che
     * si sveglia trenta volte all'ora. Non e' una preferenza da rispettare:
     * e' un guasto. Quindi se la cartella dei « si » e' vuota, la dotazione
     * ci torna dentro, segno o non segno.
     *
     * Va chiamata <b>fuori dal thread dell'interfaccia</b>: sono trecento
     * kilobyte da copiare.
     */
    public int portaIBase() {
        File segno = new File(radice, FATTO);
        // Senza nemmeno un « si » il rilevatore non ha con cosa confermare, e
        // allora la dotazione torna comunque: e' l'unico caso in cui il segno
        // non conta.
        boolean senzaSi = quanti(SI) == 0;
        if (segno.exists() && !senzaSi) return 0;

        // Se qualcosa c'e' gia', non si aggiunge niente: la dotazione serve a
        // chi parte da zero. Aggiungerla a dei campioni gia' registrati
        // vorrebbe dire, molto probabilmente, metterci accanto le SUE STESSE
        // registrazioni con un altro nome - e due file identici danno distanza
        // zero, il che manda la taratura a proporre una soglia bassissima che
        // poi non aggancia piu' niente di nuovo.
        if (!senzaSi) {
            Log.i(TAG, "campioni: ce ne sono gia', la dotazione di serie non serve");
            marca(segno);
            return 0;
        }

        int messi = 0;
        for (int tipo = 0; tipo < CARTELLA.length; tipo++) {
            // I rumori si rimettono solo la prima volta; i « si » sempre,
            // perche' senza non si sveglia piu' nessuno.
            if (tipo != SI && segno.exists()) continue;
            String dentro = "parola/" + CARTELLA[tipo];
            String[] nomi;
            try {
                nomi = contesto.getAssets().list(dentro);
            } catch (Exception e) {
                Log.w(TAG, "campioni: non trovo gli asset in " + dentro, e);
                continue;
            }
            if (nomi == null) continue;
            for (String nome : nomi) {
                if (!nome.endsWith(".wav")) continue;
                File destinazione = new File(cartella(tipo), nome);
                if (destinazione.exists()) continue;
                if (copia(dentro + "/" + nome, destinazione)) messi++;
            }
        }
        marca(segno);
        if (messi > 0) Log.i(TAG, "campioni: messi " + messi + " campioni di serie");
        return messi;
    }

    /** Lascia il segno che la dotazione e' stata considerata. Senza, si
     *  riproverebbe a ogni avvio. */
    private void marca(File segno) {
        try {
            new java.io.FileOutputStream(segno).close();
        } catch (Exception e) {
            Log.w(TAG, "campioni: non riesco a scrivere " + FATTO, e);
        }
    }

    private boolean copia(String asset, File destinazione) {
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        File temporaneo = new File(destinazione.getParentFile(), destinazione.getName() + ".tmp");
        try {
            in = contesto.getAssets().open(asset);
            out = new java.io.FileOutputStream(temporaneo);
            byte[] blocco = new byte[8192];
            int n;
            while ((n = in.read(blocco)) > 0) out.write(blocco, 0, n);
            out.flush();
            out.close();
            out = null;
            return temporaneo.renameTo(destinazione);
        } catch (Exception e) {
            Log.w(TAG, "campioni: non ho copiato " + asset, e);
            return false;
        } finally {
            chiudi(in);
            chiudi(out);
            if (temporaneo.exists()) temporaneo.delete();
        }
    }

    private static void chiudi(java.io.Closeable q) {
        if (q == null) return;
        try { q.close(); } catch (Exception ignorata) { }
    }

    public File cartella(int tipo) { return new File(radice, CARTELLA[tipo]); }

    /**
     * I file di un tipo, dal piu' vecchio al piu' recente.
     *
     * In ordine e non come li da' il filesystem: l'elenco a schermo deve stare
     * fermo fra un'apertura e l'altra, o cancellare il terzo campione vuol dire
     * cancellare ogni volta uno diverso.
     */
    public List<File> elenco(int tipo) {
        File[] trovati = cartella(tipo).listFiles();
        List<File> fuori = new ArrayList<File>();
        if (trovati == null) return fuori;
        for (File f : trovati) {
            if (f.isFile() && f.getName().endsWith(".wav")) fuori.add(f);
        }
        // Per nome e non per data di modifica: il nome porta dentro l'istante
        // della registrazione, mentre la data cambia se un file viene ricopiato
        // dal PC - e allora i campioni si riordinerebbero da soli dopo ogni
        // scambio con il PC.
        java.util.Collections.sort(fuori, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return fuori;
    }

    public int quanti(int tipo) {
        File[] trovati = cartella(tipo).listFiles();
        if (trovati == null) return 0;
        int n = 0;
        for (File f : trovati) if (f.isFile() && f.getName().endsWith(".wav")) n++;
        return n;
    }

    /** Il file dove finira' la prossima registrazione di questo tipo. */
    public File prossimo(int tipo, String etichetta) {
        String quando = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ITALIAN)
                .format(new java.util.Date());
        String coda = pulisci(etichetta);
        String nome = CARTELLA[tipo] + "-" + quando + (coda.isEmpty() ? "" : "-" + coda) + ".wav";
        return new File(cartella(tipo), nome);
    }

    /** L'etichetta ridotta a quello che sta in un nome di file. */
    private static String pulisci(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 16; i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') b.append(c);
            else if (c == ' ' || c == '-' || c == '_') { if (b.length() > 0) b.append('-'); }
        }
        while (b.length() > 0 && b.charAt(b.length() - 1) == '-') b.setLength(b.length() - 1);
        return b.toString();
    }

    public boolean elimina(File f) {
        if (f == null || !f.exists()) return false;
        // Solo roba di casa nostra: questo metodo lo chiama anche un comando
        // che arriva da adb, e un percorso arrivato da fuori non deve poter
        // cancellare fuori da qui.
        if (!f.getAbsolutePath().startsWith(radice.getAbsolutePath())) {
            Log.w(TAG, "campioni: rifiuto di cancellare fuori da parola/: " + f);
            return false;
        }
        return f.delete();
    }

    /** Cancella tutto un tipo. Torna quanti ne ha tolti. */
    public int svuota(int tipo) {
        int n = 0;
        for (File f : elenco(tipo)) if (f.delete()) n++;
        return n;
    }

    // ---- lo stato ----------------------------------------------------------

    /**
     * Le impostazioni della parola.
     *
     * In {@code getFilesDir()} e non accanto ai campioni: questo e' stato di
     * Casa, non materiale di lavoro, e la scrittura atomica dell'{@link
     * Archivio} vale anche qui - una soglia scritta a meta' e' una parola che
     * non aggancia piu' e nessuno che capisca perche'.
     */
    public static final class Stato {
        /** L'ascolto continuo per la parola e' acceso. */
        public boolean accesa;
        /** Sotto questa distanza si aggancia. La sceglie la taratura. */
        public float soglia = 2.6f;
        /** true = si registra da VOICE_COMMUNICATION, che accende la
         *  cancellazione d'eco. Vedi {@link Orecchio}. */
        public boolean eco;
        /** Quanto alzare l'asticella mentre suona qualcosa. */
        public float rincaro = 0.45f;

        /**
         * La soglia della rete: un "quanto somiglia" da zero a uno, e piu'
         * ALTA e' piu' esigente. Va nel verso opposto a {@link #soglia}, che
         * e' una distanza.
         *
         * Il valore di fabbrica esce da pc/parola/difila.py, misurato su venti
         * minuti di parlato di sconosciuti con la parola detta da voci mai
         * sentite. Vedi {@link #difila}: le due manopole si scelgono insieme,
         * non una per volta.
         */
        public float sogliaRete = 0.999f;

        /**
         * Quante finestre di fila devono stare sopra soglia.
         *
         * <b>E' la difesa che vale piu' di tutte, ed e' misurata.</b> Il
         * rilevatore guarda una finestra ogni 120 ms: una parola vera ne
         * accende cinque o sei consecutive - dal vivo se ne sono contate otto
         * a punteggio pieno - mentre un falso allarme quasi sempre una o due.
         * Su venti minuti di parlato d'altri, con la parola detta da voci mai
         * sentite (pc/parola/difila.py):
         *
         * <pre>
         *   1 finestra sopra 0,999   12 risvegli a vuoto/ora   prende l'86%
         *   2 di fila                 0                        77%
         *   3 di fila sopra 0,99      3                        78%
         * </pre>
         *
         * <b>Due bastano solo da quando la rete e' indifferente alla
         * posizione.</b> Prima il punteggio saltava fra zero e uno spostando la
         * parola di un decimo di secondo, quindi le finestre buone non
         * capitavano mai consecutive e chiederne due era un colpo di fortuna -
         * chiederne quattro, come si faceva, era impossibile. Adesso una parola
         * vera accende una fila continua, e due di fila costano pochissimo.
         * Vedi riposiziona() in pc/parola/allena.py.
         *
         * Con qualcosa che suona se ne chiede una in piu'.
         */
        public int difila = 2;
    }

    public Stato leggiStato() {
        Stato s = new Stato();
        JSONObject j = Archivio.leggi(contesto, FILE_STATO);
        if (j == null) return s;
        s.accesa  = j.optBoolean("accesa", s.accesa);
        s.soglia  = (float) j.optDouble("soglia", s.soglia);
        s.eco     = j.optBoolean("eco", s.eco);
        s.rincaro = (float) j.optDouble("rincaro", s.rincaro);
        s.sogliaRete = (float) j.optDouble("sogliaRete", s.sogliaRete);
        s.difila = j.optInt("difila", s.difila);
        return s;
    }

    public void scriviStato(Stato s) {
        try {
            JSONObject j = new JSONObject();
            j.put("accesa", s.accesa);
            j.put("soglia", s.soglia);
            j.put("eco", s.eco);
            j.put("rincaro", s.rincaro);
            j.put("sogliaRete", s.sogliaRete);
            j.put("difila", s.difila);
            Archivio.scrivi(contesto, FILE_STATO, j);
        } catch (Exception e) {
            Log.w(TAG, "campioni: stato della parola non scritto", e);
        }
    }

    /** Un riassunto da scrivere a schermo e da mandare al PC. */
    public String riassunto() {
        return quanti(SI) + " si, " + quanti(NO) + " no";
    }

    /** Il totale in kilobyte, per la riga di stato: chi riempie la cartella
     *  dal PC deve vedere quanto pesa prima che pesi troppo. */
    public long kilobyte() {
        long tot = 0;
        for (int tipo = 0; tipo < CARTELLA.length; tipo++) {
            File[] f = cartella(tipo).listFiles();
            if (f != null) for (File x : f) tot += x.length();
        }
        return tot / 1024;
    }
}
