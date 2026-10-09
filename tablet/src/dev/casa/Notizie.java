package dev.casa;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Le notizie: quelle d'Italia, quelle di qui, e - se si vuole - lo sport.
 *
 * <h3>Da dove, e perche' da li'</h3>
 *
 * Da <b>Google News, in RSS</b>. Come Open-Meteo per il meteo: niente chiave,
 * niente registrazione, e quindi niente segreto da tenere sul tablet. E' anche
 * l'unica sorgente senza chiave che sappia fare le quattro cose che servono con
 * <b>un solo formato</b>: le principali d'Italia, una ricerca per nome di posto
 * (« Roma »), una per sport e una per squadra. I feed dei giornali - ANSA ne
 * ha uno per regione - arrivano alla regione e non al paese, e per una squadra
 * non ce n'e' nessuno.
 *
 * Il prezzo e' il peso: una risposta sono <b>centocinquanta kilobyte</b>,
 * misurati, perche' ogni notizia si porta dietro in HTML l'elenco degli
 * articoli collegati. Per questo:
 *
 * <ul>
 *   <li><b>si legge a flusso</b>, con {@code XmlPullParser}, e ci si ferma
 *       appena si hanno i titoli che servono: il resto della risposta non
 *       entra mai in memoria, e chiudendo la connessione non arriva nemmeno;
 *   <li>su disco non va la risposta com'e' - che per il meteo e' la scelta
 *       giusta, due kilobyte - ma i soli titoli: dodici righe per filone invece
 *       di centocinquanta kilobyte di HTML che non si mostra;
 *   <li>si chiede <b>solo quello che si guarda</b>. Le principali servono alla
 *       Home e al riposo, e si tengono fresche da sole; le altre si chiedono
 *       quando si apre la pagina.
 * </ul>
 *
 * <h3>Il certificato</h3>
 *
 * news.google.com firma con Google Trust Services, e la catena arriva a
 * <i>GTS Root R1</i> passando per una firma incrociata di <i>GlobalSign Root
 * CA</i>. Oggi funziona anche senza di noi - quella radice il tablet ce l'ha,
 * {@code b0f3e76e.0} - ma scade il 28 gennaio 2028, e da quel giorno le notizie
 * smetterebbero di arrivare con un errore che parla di catene di certificati,
 * esattamente come il meteo il primo giorno. Le due radici GTS stanno in
 * {@code assets/radici/} accanto a quelle di Let's Encrypt: e' la stessa strada
 * di {@link Fiducia}, e la stessa regola - si aggiunge, non si spegne il
 * controllo.
 *
 * <h3>I filoni</h3>
 *
 * Quattro, e ognuno e' un indirizzo: <b>Italia</b> (le principali, nell'ordine
 * in cui le mette Google), <b>di qui</b> (una ricerca col nome del posto),
 * <b>lo sport</b> scelto e <b>la squadra</b>. Gli ultimi due ci sono solo con la
 * modalita' sport accesa. Le ricerche si ordinano per data, perche' Google le
 * ordina per pertinenza e la pertinenza di una partita di martedi' scorso non
 * interessa a nessuno.
 */
public final class Notizie {

    private static final String TAG = MainActivity.TAG;

    private static final String FILE = "notizie.json";

    private static final String FONTE = "https://news.google.com/rss";
    private static final String LINGUA = "hl=it&gl=IT&ceid=IT:it";

    public static final int ITALIA = 0, LOCALI = 1, SPORT = 2, SQUADRA = 3;

    /**
     * Ogni quanto si torna a chiedere.
     *
     * Venti minuti e non cinque: le principali di Google cambiano davvero
     * qualche volta all'ora, e ogni richiesta sono cinquanta kilobyte anche
     * leggendo solo i primi titoli. Su sedici ore di tablet acceso sono una
     * cinquantina di richieste, contro le duecento di un quarto d'ora.
     */
    private static final long SCADENZA = 20 * 60 * 1000L;

    /** Dopo un errore si aspetta: il Wi-Fi di un tablet appena acceso puo'
     *  metterci mezzo minuto ad agganciare, e insistere non lo fa arrivare. */
    private static final long RIPROVA = 2 * 60 * 1000L;

    /**
     * Oltre questa eta' i titoli non si mostrano piu', nemmeno se sono gli
     * unici che ci sono.
     *
     * Si era scelto il contrario - « con i titoli di mezz'ora fa a schermo,
     * dire non riesco sarebbe allarmare » - ed era giusto per la mezz'ora. Non
     * per tre giorni: tornando a casa la Home diceva « aggiornate 3 giorni fa »
     * e toccava premere aggiorna a mano. Una notizia di ieri presentata come
     * notizia e' un errore, non un ripiego: meglio il solo meteo, o una riga
     * che dice che non si riesce ad aggiornarle.
     */
    private static final long VECCHIE = 6 * 60 * 60 * 1000L;

    /** Ogni quanto si guarda se e' ora. Guardare e' confrontare due numeri;
     *  la richiesta vera parte solo a {@link #SCADENZA} passata. */
    private static final long CONTROLLO = 5 * 60 * 1000L;

    /** Quanti titoli si tengono per filone: quelli che la pagina mostra senza
     *  scorrere, e qualcuno per il giro della Home e del riposo. */
    private static final int TENUTI = 12;

    /** Quante notizie si leggono di una ricerca prima di ordinarle per data.
     *  Google le da' per pertinenza: le prime dodici non sono le piu' nuove. */
    private static final int LETTE = 40;

    public interface Ascolto {
        /** Thread dell'interfaccia: c'e' roba nuova, ridisegna. */
        void notizieCambiate();
    }

    /** Chi sa aprire la pagina intera: la regia, come per il meteo. */
    public interface Pagina { void apriNotizie(); }

    /** Una notizia: il titolo, chi l'ha scritta, quando, e dove leggerla. */
    public static final class Titolo {
        public final String testo, fonte, link;
        public final long quando;
        Titolo(String testo, String fonte, String link, long quando) {
            this.testo = testo; this.fonte = fonte; this.link = link; this.quando = quando;
        }
    }

    /**
     * Uno sport che si puo' seguire.
     *
     * {@code cerca} e' quello che si chiede a Google, e non e' sempre il nome:
     * « calcio » da solo porta anche il calcio che si prende con la vitamina D -
     * misurato, era la prima notizia - e « calcio serie a » no.
     */
    public static final class Disciplina {
        public final String chiave, nome, cerca;
        public final int icona;
        Disciplina(String chiave, String nome, String cerca, int icona) {
            this.chiave = chiave; this.nome = nome; this.cerca = cerca; this.icona = icona;
        }
    }

    /** Gli sport che si seguono di piu' in Italia. Otto, cioe' due righe da
     *  quattro nel pannello: con uno in piu' la terza riga avrebbe una tessera
     *  sola, che si legge come un errore di impaginazione. */
    public static final Disciplina[] DISCIPLINE = {
        new Disciplina("calcio",   "Calcio",    "calcio serie a", Icone.CALCIO),
        new Disciplina("tennis",   "Tennis",    "tennis",         Icone.TENNIS),
        new Disciplina("f1",       "Formula 1", "formula 1",      Icone.MOTORI),
        new Disciplina("motogp",   "MotoGP",    "motogp",         Icone.MOTO),
        new Disciplina("basket",   "Basket",    "basket",         Icone.BASKET),
        new Disciplina("volley",   "Pallavolo", "pallavolo",      Icone.PALLAVOLO),
        new Disciplina("ciclismo", "Ciclismo",  "ciclismo",       Icone.CICLISMO),
        new Disciplina("sci",      "Sci",       "sci",            Icone.SCI),
    };

    /**
     * Un filone di notizie, cosi' come si vede adesso.
     *
     * Non porta i titoli: quelli stanno in {@link #prese}, per indirizzo. Cosi'
     * cambiare squadra e poi tornare a quella di prima ritrova i titoli di
     * prima, invece di una pagina vuota per il tempo di una richiesta.
     */
    public static final class Filone {
        public final int tipo;
        public final String nome;
        public final int icona;
        final String indirizzo;
        Filone(int tipo, String nome, int icona, String indirizzo) {
            this.tipo = tipo; this.nome = nome; this.icona = icona; this.indirizzo = indirizzo;
        }
    }

    private static final class Presa {
        final Titolo[] titoli;
        final long quando;
        Presa(Titolo[] titoli, long quando) { this.titoli = titoli; this.quando = quando; }
    }

    private static final Titolo[] NESSUNO = new Titolo[0];
    private static final Filone[] NESSUN_FILONE = new Filone[0];

    private final Context contesto;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler lavoro;
    private final HandlerThread filo;

    /** Le risposte buone, per indirizzo. Si toccano da due fili: sempre sotto
     *  il loro stesso lucchetto. */
    private final HashMap<String, Presa> prese = new HashMap<String, Presa>();
    private final HashMap<String, Long> tentativi = new HashMap<String, Long>();
    private final HashMap<String, String> guasti = new HashMap<String, String>();
    private final HashSet<String> inCorso = new HashSet<String>();

    // ---- come si e' deciso ------------------------------------------------

    /** Il posto delle notizie di qui. Vuoto vuol dire « quello del meteo »:
     *  e' il posto dove sta il tablet, e chiederlo due volte sarebbe una
     *  seconda cosa da tenere allineata alla prima. */
    private String localita = "";
    private boolean sport;
    private String disciplina = "calcio";
    private String squadra = "";

    private volatile Filone[] visibili = NESSUN_FILONE;
    private Meteo meteo;
    private Ascolto ascolto;
    private boolean vivo;

    private final Runnable controllo = new Runnable() {
        @Override public void run() {
            aggiorna(false);
            ui.postDelayed(this, CONTROLLO);
        }
    };

    public Notizie(Context c) {
        contesto = c.getApplicationContext();
        filo = new HandlerThread("Casa-notizie");
        filo.start();
        lavoro = new Handler(filo.getLooper());
        applica(Configurazione.leggi(c));
        // La copia su disco si rilegge sul filo di lavoro: sono una ventina di
        // kilobyte - gli indirizzi degli articoli sono lunghi - e non devono
        // pesare sul primo fotogramma della Home, che ha gia' il meteo da dire.
        lavoro.post(new Runnable() {
            @Override public void run() { rileggiDalDisco(); avvisa(); }
        });
    }

    public void setAscolto(Ascolto a) { ascolto = a; }

    /** Il meteo, per sapere dove sta il tablet quando il posto non l'ha
     *  scritto nessuno. */
    public void setMeteo(Meteo m) {
        meteo = m;
        ricomponi();
    }

    /** Il meteo ha cambiato citta': se le notizie di qui la seguono, cambiano
     *  anche loro. */
    public void meteoCambiato() {
        if (localita.length() > 0) return;
        Filone[] v = visibili;
        String prima = v.length > 1 && v[1].tipo == LOCALI ? v[1].nome : null;
        String dopo = maiuscole(luogo());
        if (dopo.equals(prima)) return;
        ricomponi();
        avvisa();
    }

    // ---- la configurazione ------------------------------------------------

    /**
     * Com'e' stato deciso, dal PC o dal tablet:
     *
     * <pre>
     *   "notizie": { "localita": "", "sport": true, "disciplina": "calcio",
     *                "squadra": "napoli" }
     * </pre>
     */
    public void applica(JSONObject config) {
        JSONObject n = config == null ? null : config.optJSONObject("notizie");
        if (n != null) {
            localita = n.optString("localita", "").trim();
            sport = n.optBoolean("sport", false);
            String d = n.optString("disciplina", "calcio");
            disciplina = trova(d) != null ? d : "calcio";
            squadra = n.optString("squadra", "").trim();
        }
        ricomponi();
        avvisa();
        if (vivo) aggiorna(false);
    }

    /**
     * Cambia le scelte dal tablet, e le scrive nella configurazione.
     *
     * Scriverle li' e non in {@code notizie.json} e' quello che le fa arrivare
     * al PC: la vetrina e' la configurazione, e Gestione Home la rilegge prima
     * di mandare - una squadra scelta sul tablet non torna indietro al primo
     * « manda ».
     */
    public void imposta(String nuovaLocalita, boolean nuovoSport, String nuovaDisciplina,
                        String nuovaSquadra) {
        localita = nuovaLocalita == null ? "" : nuovaLocalita.trim();
        sport = nuovoSport;
        disciplina = trova(nuovaDisciplina) != null ? nuovaDisciplina : disciplina;
        squadra = nuovaSquadra == null ? "" : nuovaSquadra.trim();
        final JSONObject scelte = json();
        lavoro.post(new Runnable() {
            @Override public void run() { Configurazione.salvaNotizie(contesto, scelte); }
        });
        Log.i(TAG, "notizie: " + riassunto());
        ricomponi();
        avvisa();
        aggiorna(true);
    }

    /** Le scelte, come vanno in {@code casa.json}. */
    public JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("localita", localita);
            o.put("sport", sport);
            o.put("disciplina", disciplina);
            o.put("squadra", squadra);
        } catch (Exception ignorata) { }
        return o;
    }

    /** Quelle di un tablet appena installato: le principali e quelle del posto
     *  del meteo, niente sport. */
    public static JSONObject diFabbrica() {
        JSONObject o = new JSONObject();
        try {
            o.put("localita", "");
            o.put("sport", false);
            o.put("disciplina", "calcio");
            o.put("squadra", "");
        } catch (Exception ignorata) { }
        return o;
    }

    /**
     * Gli sport che questo tablet sa seguire, per il PC.
     *
     * Li dice il tablet, come le risposte di fabbrica e i loghi delle
     * stazioni: una tendina scritta sul PC inviterebbe a scegliere uno sport
     * che di qua non c'e'.
     */
    public static JSONArray catalogo() {
        JSONArray a = new JSONArray();
        try {
            for (Disciplina d : DISCIPLINE) {
                JSONObject o = new JSONObject();
                o.put("chiave", d.chiave);
                o.put("nome", d.nome);
                a.put(o);
            }
        } catch (Exception ignorata) { }
        return a;
    }

    public String localita()  { return localita; }
    public boolean sport()    { return sport; }
    public String squadra()   { return squadra; }
    public Disciplina disciplina() {
        Disciplina d = trova(disciplina);
        return d != null ? d : DISCIPLINE[0];
    }

    /** Il posto delle notizie di qui, com'e' davvero: quello scritto, o quello
     *  del meteo. */
    public String luogo() {
        if (localita.length() > 0) return localita;
        return meteo != null ? meteo.citta() : "";
    }

    private static Disciplina trova(String chiave) {
        if (chiave == null) return null;
        for (Disciplina d : DISCIPLINE) if (d.chiave.equals(chiave)) return d;
        return null;
    }

    private String riassunto() {
        return "di qui " + (localita.length() > 0 ? localita : "(" + luogo() + ", dal meteo)")
                + ", sport " + (sport ? disciplina().nome : "spento")
                + (squadra.length() > 0 ? ", squadra " + squadra : "");
    }

    /**
     * Rifa' l'elenco dei filoni da mostrare.
     *
     * Si chiama quando cambia una scelta, non a ogni fotogramma: e' l'unico
     * posto che alloca, e l'array che ne esce si pubblica intero - chi disegna
     * lo legge una volta e ci lavora sopra senza lucchetti.
     */
    private void ricomponi() {
        List<Filone> v = new ArrayList<Filone>(4);
        v.add(new Filone(ITALIA, "Italia", Icone.NOTIZIE, FONTE + "?" + LINGUA));
        String qui = luogo();
        if (qui.length() > 0) {
            // Fra virgolette: « Santa Maria Capua Vetere » e' un nome solo, e
            // senza le virgolette Google cerca quattro parole qualsiasi.
            v.add(new Filone(LOCALI, maiuscole(qui), Icone.POSIZIONE,
                    cerca("\"" + qui + "\" when:7d")));
        }
        if (sport) {
            Disciplina d = disciplina();
            v.add(new Filone(SPORT, d.nome, d.icona, cerca(d.cerca + " when:2d")));
            if (squadra.length() > 0) {
                // « calcio » accanto al nome, o « Napoli » porta le notizie
                // della citta' e « Inter » quelle di chiunque dica interno.
                v.add(new Filone(SQUADRA, maiuscole(squadra), Icone.CALCIO,
                        cerca("\"" + squadra + "\" calcio when:3d")));
            }
        }
        visibili = v.toArray(new Filone[v.size()]);
    }

    private static String cerca(String domanda) {
        try {
            return FONTE + "/search?q=" + URLEncoder.encode(domanda, "UTF-8") + "&" + LINGUA;
        } catch (Exception e) {
            return FONTE + "?" + LINGUA;
        }
    }

    /** « hellas verona » -> « Hellas Verona ». La tastiera di Casa scrive
     *  solo minuscole, e un nome proprio in minuscolo su una linguetta sembra
     *  un errore. */
    public static String maiuscole(String s) {
        if (s == null || s.length() == 0) return "";
        StringBuilder b = new StringBuilder(s.length());
        boolean inizio = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(inizio ? Character.toUpperCase(c) : c);
            inizio = c == ' ' || c == '\'' || c == '-';
        }
        return b.toString();
    }

    // ---- quello che sa ----------------------------------------------------

    /** I filoni da mostrare adesso, nell'ordine delle linguette. */
    public Filone[] filoni() { return visibili; }

    /** I titoli di un filone, solo se non sono troppo vecchi: vedi
     *  {@link #VECCHIE}. */
    public Titolo[] titoli(Filone f) {
        if (f == null) return NESSUNO;
        synchronized (prese) {
            Presa p = prese.get(f.indirizzo);
            return fresca(p) ? p.titoli : NESSUNO;
        }
    }

    /** Quando sono arrivati, 0 se mai - o se sono cosi' vecchi che non si
     *  mostrano, e allora « aggiornate 3 giorni fa » sopra un elenco vuoto
     *  non direbbe niente. */
    public long quando(Filone f) {
        if (f == null) return 0L;
        synchronized (prese) {
            Presa p = prese.get(f.indirizzo);
            return fresca(p) ? p.quando : 0L;
        }
    }

    private static boolean fresca(Presa p) {
        return p != null && System.currentTimeMillis() - p.quando < VECCHIE;
    }

    public boolean inCorso(Filone f) {
        if (f == null) return false;
        synchronized (prese) { return inCorso.contains(f.indirizzo); }
    }

    /** Perche' non c'e' niente, quando non c'e'. null quasi sempre. */
    public String perche(Filone f) {
        if (f == null) return null;
        synchronized (prese) {
            String g = guasti.get(f.indirizzo);
            if (g != null) return g;
            Presa p = prese.get(f.indirizzo);
            if (p != null && !fresca(p) && !inCorso.contains(f.indirizzo)) {
                return "non riesco ad aggiornarle: le ultime sono di "
                        + fa(p.quando, System.currentTimeMillis());
            }
            return null;
        }
    }

    /** Le principali d'Italia: quelle che girano nella Home e sul riposo. */
    public Titolo[] principali() {
        Filone[] v = visibili;
        return v.length > 0 ? titoli(v[0]) : NESSUNO;
    }

    public long quandoPrincipali() {
        Filone[] v = visibili;
        return v.length > 0 ? quando(v[0]) : 0L;
    }

    /**
     * Da quanto, in parole: « 12 min fa », « 3 ore fa », « ieri ».
     *
     * Alloca, quindi non si chiama dentro onDraw: chi disegna compone le sue
     * scritte quando cambiano i titoli, o una volta al minuto.
     */
    public static String fa(long quando, long adesso) {
        if (quando <= 0L) return "";
        long minuti = Math.max(0L, (adesso - quando) / 60000L);
        if (minuti < 2) return "adesso";
        if (minuti < 60) return minuti + " min fa";
        long ore = minuti / 60;
        if (ore < 24) return ore == 1 ? "un'ora fa" : ore + " ore fa";
        long giorni = ore / 24;
        return giorni == 1 ? "ieri" : giorni + " giorni fa";
    }

    // ---- le richieste -----------------------------------------------------

    /** Casa e' sullo schermo: le principali si tengono fresche da sole. */
    public void riprendi() {
        vivo = true;
        ui.removeCallbacks(controllo);
        ui.post(controllo);
    }

    /** Casa e' dietro: nessuno guarda, nessuno chiede. */
    public void sospendi() {
        vivo = false;
        ui.removeCallbacks(controllo);
    }

    /**
     * Rinfresca quel che e' vecchio.
     *
     * @param tutti false per la Home e il riposo, che mostrano solo le
     *        principali; true per la pagina, che le mostra tutte.
     */
    public void aggiorna(boolean tutti) {
        Filone[] v = visibili;
        long adesso = System.currentTimeMillis();
        for (int i = 0; i < v.length; i++) {
            if (!tutti && v[i].tipo != ITALIA) continue;
            Filone f = v[i];
            synchronized (prese) {
                if (inCorso.contains(f.indirizzo)) continue;
                Presa p = prese.get(f.indirizzo);
                if (p != null && adesso - p.quando < SCADENZA) continue;
                Long t = tentativi.get(f.indirizzo);
                if (t != null && adesso - t < RIPROVA) continue;
            }
            chiedi(f);
        }
    }

    /**
     * Si e' tornati: qualcuno tocca lo schermo dopo tanto, la rete e' tornata,
     * Casa e' di nuovo in scena. Rinfresca tutti i filoni che si vedono e che
     * sono scaduti, <b>senza aspettare</b> {@link #RIPROVA}.
     *
     * L'attesa dopo un errore serve a non bussare a ogni fotogramma su una
     * rete che non c'e'; ma se l'ultimo tentativo e' fallito un minuto prima
     * che il Wi-Fi tornasse, aspettarne altri due vuol dire far vedere a chi
     * rientra proprio quello che si voleva evitare. Qui c'e' un motivo per
     * credere che adesso vada, ed e' una chiamata sola, non un ciclo.
     */
    public void riprova() {
        synchronized (prese) { tentativi.clear(); }
        aggiorna(true);
    }

    /** Il tasto « aggiorna » della pagina: tutto, e subito. */
    public void rinfresca() {
        for (Filone f : visibili) {
            synchronized (prese) { if (inCorso.contains(f.indirizzo)) continue; }
            chiedi(f);
        }
    }

    private void chiedi(final Filone f) {
        synchronized (prese) {
            inCorso.add(f.indirizzo);
            tentativi.put(f.indirizzo, System.currentTimeMillis());
        }
        avvisa();
        lavoro.post(new Runnable() {
            @Override public void run() {
                Titolo[] letti = null;
                try {
                    letti = scarica(f.indirizzo, f.tipo != ITALIA);
                } catch (Throwable t) {
                    Log.w(TAG, "notizie: " + f.nome + " non lette", t);
                }
                synchronized (prese) {
                    inCorso.remove(f.indirizzo);
                    if (letti != null && letti.length > 0) {
                        prese.put(f.indirizzo, new Presa(letti, System.currentTimeMillis()));
                        guasti.remove(f.indirizzo);
                    } else if (!fresca(prese.get(f.indirizzo))) {
                        // Si spiega solo quando non c'e' niente da mostrare: con
                        // i titoli di mezz'ora fa a schermo, dire « non riesco »
                        // sarebbe allarmare per una cosa che non si vede. Quelli
                        // troppo vecchi invece non si vedono piu', e allora si.
                        guasti.put(f.indirizzo, letti == null
                                ? "non riesco a leggere le notizie" : "nessuna notizia trovata");
                    }
                }
                if (letti != null && letti.length > 0) {
                    Log.i(TAG, "notizie: " + f.nome + ", " + letti.length + " titoli");
                    salvaSulDisco();
                }
                avvisa();
            }
        });
    }

    /**
     * Una richiesta, letta a flusso.
     *
     * {@code XmlPullParser} e non un DOM: la risposta sono centocinquanta
     * kilobyte, e un DOM li terrebbe tutti in memoria per tirarne fuori dodici
     * righe. Qui ogni notizia diventa un oggetto quando si chiude il suo
     * {@code <item>}, e il resto - l'HTML degli articoli collegati - scorre via
     * senza fermarsi.
     *
     * @param ordina true per le ricerche, che Google da' per pertinenza: se ne
     *        leggono {@link #LETTE} e si tengono le piu' nuove. Le principali
     *        invece hanno gia' l'ordine giusto, ed e' proprio quello di Google.
     */
    private Titolo[] scarica(String indirizzo, boolean ordina) throws Exception {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            Fiducia.applica(contesto, c);
            c.setRequestMethod("GET");
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            int esito = c.getResponseCode();
            if (esito != 200) {
                Log.w(TAG, "notizie: risposta " + esito + " da " + indirizzo);
                return null;
            }
            in = c.getInputStream();
            XmlPullParser x = Xml.newPullParser();
            x.setInput(in, "UTF-8");

            SimpleDateFormat data = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            int quante = ordina ? LETTE : TENUTI;
            ArrayList<Titolo> letti = new ArrayList<Titolo>(quante);
            HashSet<String> visti = new HashSet<String>();
            boolean dentro = false;
            String titolo = null, link = null, quando = null, fonte = null;

            for (int ev = x.getEventType();
                 ev != XmlPullParser.END_DOCUMENT && letti.size() < quante; ev = x.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    String n = x.getName();
                    if ("item".equals(n)) {
                        dentro = true;
                        titolo = link = quando = fonte = null;
                    } else if (dentro) {
                        if ("title".equals(n)) titolo = x.nextText();
                        else if ("link".equals(n)) link = x.nextText();
                        else if ("pubDate".equals(n)) quando = x.nextText();
                        else if ("source".equals(n)) fonte = x.nextText();
                    }
                } else if (ev == XmlPullParser.END_TAG && "item".equals(x.getName())) {
                    dentro = false;
                    String testo = pulisci(titolo, fonte);
                    // La stessa notizia ripresa da due giornali ha spesso lo
                    // stesso titolo: due righe uguali in un elenco di sei sono
                    // una riga sprecata.
                    if (testo.length() == 0 || !visti.add(testo)) continue;
                    letti.add(new Titolo(testo, fonte != null ? fonte.trim() : "",
                            link != null ? link.trim() : "", leggiData(data, quando)));
                }
            }

            Titolo[] fuori = letti.toArray(new Titolo[letti.size()]);
            if (ordina) {
                Arrays.sort(fuori, new Comparator<Titolo>() {
                    @Override public int compare(Titolo a, Titolo b) {
                        return a.quando == b.quando ? 0 : (a.quando > b.quando ? -1 : 1);
                    }
                });
            }
            if (fuori.length > TENUTI) fuori = Arrays.copyOf(fuori, TENUTI);
            return fuori;
        } catch (Exception e) {
            Log.w(TAG, "notizie: richiesta fallita: " + e);
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignorata) { }
            // Anche a meta' risposta: quel che resta non lo legge nessuno.
            if (c != null) c.disconnect();
        }
    }

    /**
     * Il titolo senza il giornale in coda.
     *
     * Google scrive « Addio a Emma Bonino: ... - la Repubblica »: il giornale
     * c'e' gia' nel suo campo, e ripeterlo nel titolo sono venti caratteri tolti
     * a una riga che ne ha trenta. Si toglie solo se e' proprio quello: un
     * trattino nel mezzo di un titolo vero resta dov'e'.
     */
    static String pulisci(String titolo, String fonte) {
        if (titolo == null) return "";
        String t = titolo.trim();
        if (fonte != null && fonte.trim().length() > 0) {
            String coda = " - " + fonte.trim();
            if (t.endsWith(coda)) t = t.substring(0, t.length() - coda.length()).trim();
        }
        return t;
    }

    private static long leggiData(SimpleDateFormat f, String testo) {
        if (testo == null) return 0L;
        try {
            java.util.Date d = f.parse(testo.trim());
            return d != null ? d.getTime() : 0L;
        } catch (Exception storta) {
            return 0L;
        }
    }

    // ---- la copia su disco ------------------------------------------------

    /**
     * Rilegge i titoli di prima.
     *
     * Serve per la stessa ragione del meteo: la Home e il riposo devono avere
     * qualcosa da dire <b>subito</b>, e un tablet appena riacceso con la rete
     * che ancora non aggancia mostrerebbe altrimenti solo il meteo per minuti.
     */
    private void rileggiDalDisco() {
        JSONObject salvato = Archivio.leggi(contesto, FILE);
        if (salvato == null) return;
        JSONArray elenco = salvato.optJSONArray("prese");
        if (elenco == null) return;
        synchronized (prese) {
            for (int i = 0; i < elenco.length(); i++) {
                JSONObject o = elenco.optJSONObject(i);
                if (o == null) continue;
                String indirizzo = o.optString("indirizzo", null);
                JSONArray t = o.optJSONArray("titoli");
                if (indirizzo == null || t == null || prese.containsKey(indirizzo)) continue;
                Titolo[] titoli = new Titolo[t.length()];
                int quanti = 0;
                for (int k = 0; k < t.length(); k++) {
                    JSONObject r = t.optJSONObject(k);
                    if (r == null) continue;
                    titoli[quanti++] = new Titolo(r.optString("t", ""), r.optString("f", ""),
                            r.optString("l", ""), r.optLong("q", 0L));
                }
                prese.put(indirizzo, new Presa(Arrays.copyOf(titoli, quanti), o.optLong("quando", 0L)));
            }
        }
    }

    /**
     * Riscrive il file, con i soli filoni che si vedono adesso.
     *
     * Quelli di una squadra lasciata ieri non servono piu': il file resta
     * grande quanto quattro filoni, qualunque cosa si sia scelta nel frattempo.
     */
    private void salvaSulDisco() {
        try {
            JSONArray elenco = new JSONArray();
            Filone[] v = visibili;
            synchronized (prese) {
                for (Filone f : v) {
                    Presa p = prese.get(f.indirizzo);
                    if (p == null) continue;
                    JSONObject o = new JSONObject();
                    o.put("indirizzo", f.indirizzo);
                    o.put("quando", p.quando);
                    JSONArray t = new JSONArray();
                    for (Titolo x : p.titoli) {
                        JSONObject r = new JSONObject();
                        r.put("t", x.testo);
                        r.put("f", x.fonte);
                        r.put("l", x.link);
                        r.put("q", x.quando);
                        t.put(r);
                    }
                    o.put("titoli", t);
                    elenco.put(o);
                }
            }
            JSONObject fuori = new JSONObject();
            fuori.put("prese", elenco);
            Archivio.scrivi(contesto, FILE, fuori);
        } catch (Exception e) {
            Log.w(TAG, "notizie: non ho potuto salvare " + FILE, e);
        }
    }

    // ---- per il PC --------------------------------------------------------

    /**
     * Come sta, nel registro, una riga per filone:
     *
     * <pre>
     *   I/Casa: PC notizie scelte|localita|luogo|sport|disciplina|squadra
     *   I/Casa: PC notizie filone|Italia|12|8 min fa|Addio a Emma Bonino: ...
     * </pre>
     *
     * La barra e non gli spazi, perche' un posto e una squadra gli spazi li
     * hanno: « Santa Maria Capua Vetere » in una riga « chiave=valore »
     * diventerebbe tre chiavi senza valore.
     */
    public void stato() {
        Log.i(TAG, "PC notizie scelte|" + localita + "|" + luogo() + "|" + sport
                + "|" + disciplina + "|" + squadra);
        long adesso = System.currentTimeMillis();
        for (Filone f : visibili) {
            Titolo[] t = titoli(f);
            long q = quando(f);
            String primo = t.length > 0 ? t[0].testo
                    : inCorso(f) ? "(sto chiedendo)"
                    : perche(f) != null ? "(" + perche(f) + ")" : "";
            Log.i(TAG, "PC notizie filone|" + f.nome + "|" + t.length + "|"
                    + (q > 0 ? fa(q, adesso) : "mai") + "|" + primo.replace('|', '/'));
        }
    }

    /** Casa se ne va: si chiude il filo. Come il meteo, le notizie sono della
     *  finestra e non del processo. */
    public void chiudi() {
        sospendi();
        ascolto = null;
        lavoro.removeCallbacksAndMessages(null);
        filo.quit();
    }

    private void avvisa() {
        final Ascolto a = ascolto;
        if (a == null) return;
        ui.post(new Runnable() {
            @Override public void run() { a.notizieCambiate(); }
        });
    }
}
