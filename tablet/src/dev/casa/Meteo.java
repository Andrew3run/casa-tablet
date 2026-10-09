package dev.casa;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;

/**
 * Il tempo che fa, e quello che fara'.
 *
 * <h3>Perche' Open-Meteo, e perche' nessuna libreria</h3>
 *
 * Questo progetto non ha Gradle: la catena e' {@code aapt2 -> javac -> d8}, e
 * ogni dipendenza va aperta a mano e tenuta viva a mano. Un SDK meteo qui non
 * sarebbe « una libreria leggera »: sarebbe un file da aggiornare a mano per
 * fare una richiesta HTTP e leggere un JSON - cose che il framework sa gia'
 * fare, con {@code HttpURLConnection} e {@code org.json}, e che qui stanno in
 * un file solo.
 *
 * La parte leggera vera e' la <b>sorgente</b>: Open-Meteo non vuole una
 * chiave, non vuole una registrazione, e risponde in JSON piatto. E' anche
 * l'unica scelta che non porta con se' un segreto da tenere sul tablet: la
 * chiave di Spotify passa da un broadcast apposta per non finire in un file
 * (vedi {@code tools/chiave-spotify.ps1}), e non volerne una seconda vale piu'
 * di qualunque confronto fra previsioni.
 *
 * Una richiesta intera - adesso, ventiquattro ore, sette giorni - sono
 * <b>2,2 kilobyte</b>, misurati. Ogni mezz'ora, su una rete di casa, e' niente.
 *
 * <h3>La copia su disco, e perche' c'e'</h3>
 *
 * Come per {@link Preferiti}: la Home deve mostrare il tempo <b>subito</b>,
 * non dopo che la rete ha risposto. All'avvio si rilegge {@code meteo.json} e
 * il pannello e' gia' pieno; la richiesta parte dietro e semmai aggiorna un
 * secondo dopo. Un apparecchio da muro che mostra un rettangolo vuoto mentre
 * pensa sembra rotto anche quando sta funzionando.
 *
 * Sul disco finisce la risposta <b>cosi' com'e'</b>, non i campi ricopiati uno
 * per uno in un formato nostro. Sono due chilobyte, si rileggono con lo stesso
 * codice che legge la rete, e non c'e' un secondo formato da tenere allineato
 * al primo - che e' il modo in cui questi file si rompono.
 *
 * <h3>Le ore sono ore di qui</h3>
 *
 * Si chiede {@code timezone=Europe/Rome} e si ricevono orari locali scritti per
 * esteso ({@code 2026-09-08T14:00}). Non si converte niente: l'ora di quel
 * testo <b>e'</b> quella che segna l'orologio della Home, che MainActivity
 * tiene sul fuso giusto rimettendolo a ogni avvio - questo ROM nasce a Pechino.
 * Confrontare due stringhe cosi' fatte e' anche il modo piu' corto di trovare
 * « adesso » dentro l'elenco delle ore: sono ordinate, e l'ordine alfabetico e'
 * quello cronologico.
 */
public final class Meteo {

    /** Il tag e' {@code Casa} come tutto il resto, col nome del pezzo nel
     *  messaggio: su questo ROM un tag fuori lista non si vede affatto, e il
     *  perche' per esteso sta su {@link MainActivity#TAG}. */
    private static final String TAG = MainActivity.TAG;

    private static final String FILE = "meteo.json";

    private static final String PREVISIONI = "https://api.open-meteo.com/v1/forecast";
    private static final String CERCA_POSTO = "https://geocoding-api.open-meteo.com/v1/search";

    /**
     * Dove sta il tablet, di fabbrica.
     *
     * E' un punto di partenza, non una scelta definitiva: {@link #vaiA} lo
     * cambia da un broadcast e lo scrive in {@code meteo.json}, quindi
     * spostare il tablet non vuol dire ricompilare Casa. Il nome scritto qui e'
     * anche quello che si legge a schermo, finche' non lo sostituisce quello
     * che risponde la ricerca.
     */
    private static final String CITTA_DI_FABBRICA = "Roma";
    private static final double LAT_DI_FABBRICA = 41.9028, LON_DI_FABBRICA = 12.4964;

    /**
     * Ogni quanto si torna a chiedere.
     *
     * Mezz'ora e non cinque minuti: Open-Meteo rifa' le sue previsioni a
     * intervalli molto piu' lunghi di cosi', e su un apparecchio acceso sedici
     * ore al giorno la differenza fra le due cadenze sono duecento richieste al
     * giorno che non dicono niente di nuovo.
     */
    private static final long SCADENZA = 30 * 60 * 1000L;

    /** Quanto si aspetta prima di riprovare, dopo che e' andata male. Non si
     *  insiste su una rete che non c'e': il Wi-Fi di un tablet appena acceso
     *  puo' metterci mezzo minuto ad agganciare. */
    private static final long RIPROVA = 2 * 60 * 1000L;

    /**
     * Quante ore si chiedono, avanti e indietro.
     *
     * <b>Anche indietro</b>, ed e' il motivo per cui ce ne sono ventiquattro di
     * passate: la pagina mostra la giornata <b>intera</b> divisa in quattro
     * fasce - come fa meteo.it - e alle dieci di mattina « notte » e la prima
     * meta' di « mattina » sono gia' successe. Senza le ore passate quelle due
     * linguette sarebbero vuote, o non ci sarebbero, e la giornata comincerebbe
     * ogni volta da un'ora diversa.
     *
     * Ventiquattro indietro e ventiquattro avanti coprono <b>sempre</b> tutto
     * il giorno di oggi, a qualunque ora si guardi: a mezzanotte e mezza le
     * passate arrivano a ieri, alle undici di sera le prossime arrivano a
     * domani. Sono quarantotto righe di JSON, cioe' cinque kilobyte.
     */
    private static final int ORE_AVANTI = 24, ORE_INDIETRO = 24;

    /** Quanti giorni. Sette e' quello che si legge in una schermata senza
     *  scorrere, ed e' anche il punto oltre il quale una previsione e' un
     *  auspicio. */
    private static final int GIORNI = 7;

    public interface Ascolto {
        /** Thread dell'interfaccia: c'e' roba nuova, ridisegna. */
        void meteoCambiato();
    }

    /**
     * Chi sa aprire la pagina intera del meteo.
     *
     * Ce l'hanno in due - la Home, toccando il pannello, e la sezione App,
     * toccando la tessera - e la sanno aprire tutte e due nello stesso modo,
     * cioe' chiedendolo a MainActivity, che e' l'unica che ha in mano il telaio.
     * Sta qui e non in una delle due sezioni perche' non e' di nessuna delle
     * due: e' del meteo.
     */
    public interface Pagina { void apriMeteo(); }

    /** Com'e' adesso. */
    public static final class Adesso {
        public float gradi, percepiti, vento;
        public int umidita, codice;
        public boolean giorno;
    }

    /**
     * Una delle prossime ore.
     *
     * Porta piu' di quello che serve alla Home apposta: la pagina ne fa una
     * colonna con dentro tutto - come le colonne orarie di meteo.it - e sei
     * campi in piu' su ventiquattro ore sono qualche centinaio di byte in una
     * risposta che ne pesa duemila.
     */
    public static final class Ora {
        public int ora;            // 0-23, ora di qui
        public float gradi;
        public float percepiti;
        public int codice;
        public int pioggia;        // probabilita', in centesimi
        public int umidita;
        public float vento;        // km/h
        public int direzione;      // gradi da cui viene, 0 = nord
        public boolean giorno;     // fra l'alba e il tramonto
        /** -1 ieri, 0 oggi, 1 domani. */
        public int quandoGiorno;
        /** Gia' passata: si disegna smorta, ma c'e'. */
        public boolean passata;
        /** L'ora in corso. */
        public boolean adesso;
    }

    /**
     * Una parte della giornata: pomeriggio, sera, notte.
     *
     * E' quello che serve alla Home. Le ore una per una sono la domanda della
     * pagina intera - « a che ora smette » - mentre chi passa davanti al tablet
     * la mattina si chiede <b>com'e' il pomeriggio</b>: quattro tessere con
     * scritto 10, 11, 12, 13 sono lo stesso dato dato peggio, perche' obbligano
     * a leggerle tutte e quattro per rispondere.
     *
     * I gradi sono la <b>media</b> della fascia e non la massima: la massima di
     * un pomeriggio e' l'istante piu' caldo, la media e' com'e' stato.
     */
    public static final class Fascia {
        public String nome;        // "pomeriggio", "sera", "dom. mattina"
        public float gradi;
        public int codice;
        public int pioggia;
        public boolean giorno;
        /** Dove stanno le sue ore dentro {@link #ore()}: serve alla pagina, che
         *  di una fascia mostra le ore una per una. */
        public int da, quante;
    }

    /** Un posto dove guardare il tempo. */
    public static final class Posto {
        public final String nome;   // "Roma"
        public final String dove;   // "Campania, Italia" - per distinguere due omonimi
        public final double lat, lon;
        public Posto(String nome, String dove, double lat, double lon) {
            this.nome = nome; this.dove = dove; this.lat = lat; this.lon = lon;
        }
        /** Due posti sono lo stesso se stanno nello stesso punto. Il confronto
         *  e' sulle coordinate e non sul nome: « Roma » e « Roma,
         *  Campania » sono la stessa citta' scritta in due modi. */
        public boolean uguale(Posto altro) {
            return altro != null
                && Math.abs(lat - altro.lat) < 0.01 && Math.abs(lon - altro.lon) < 0.01;
        }
    }

    /** L'esito di una ricerca di luoghi. */
    public interface Trovati {
        void luoghi(Posto[] posti, String perche);
    }

    /** Uno dei prossimi giorni. */
    public static final class Giorno {
        public String nome;        // "oggi", "domani", "gio"
        public int codice;
        public float min, max;
        public int pioggia;
        public String alba, tramonto;   // "06:36"
    }

    private static final Ora[] NESSUNA_ORA = new Ora[0];
    private static final Giorno[] NESSUN_GIORNO = new Giorno[0];
    private static final Fascia[] NESSUNA_FASCIA = new Fascia[0];
    private static final Posto[] NESSUN_POSTO = new Posto[0];

    /** Le quattro parti in cui si divide una giornata. Quattro e non sei: sono
     *  le parole con cui si parla del tempo in casa - « stasera », « domani
     *  mattina » - e ognuna in piu' e' una parola che va spiegata. */
    private static final String[] NOME_FASCIA = { "notte", "mattina", "pomeriggio", "sera" };

    /** Quanti luoghi si tengono da parte. Cinque, che sono quelli che stanno
     *  nel pannello senza scorrere: un elenco che scorre su un apparecchio da
     *  muro e' un elenco che nessuno scorre. */
    private static final int MAX_PREFERITI = 5;

    private static final String[] GIORNO_CORTO = {
        "dom", "lun", "mar", "mer", "gio", "ven", "sab"
    };

    private final Context contesto;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler lavoro;
    private final HandlerThread filo;

    private volatile Posto attuale =
            new Posto(CITTA_DI_FABBRICA, "Campania, Italia", LAT_DI_FABBRICA, LON_DI_FABBRICA);
    private volatile Posto[] preferiti = NESSUN_POSTO;

    private volatile Adesso adesso;
    private volatile Ora[] ore = NESSUNA_ORA;
    private volatile Giorno[] giorni = NESSUN_GIORNO;

    /** Quando e' arrivata l'ultima risposta buona, e quando si e' provato
     *  l'ultima volta: la prima dice se il dato e' fresco, la seconda impedisce
     *  di insistere. */
    private volatile long quando, ultimoTentativo;
    private volatile boolean inCorso;
    private volatile String perche;

    private Ascolto ascolto;

    public Meteo(Context c) {
        contesto = c.getApplicationContext();
        filo = new HandlerThread("Casa-meteo");
        filo.start();
        lavoro = new Handler(filo.getLooper());
        rileggiDalDisco();
        // Il posto in cui si e' sta sempre in elenco: aprendo i luoghi la prima
        // volta, « nessun luogo da parte » sotto il nome della citta' che si sta
        // guardando si legge come un difetto.
        if (preferiti.length == 0) preferiti = new Posto[] { attuale };
    }

    public void setAscolto(Ascolto a) { ascolto = a; }

    /**
     * Chiude il filo di lavoro.
     *
     * Il meteo appartiene alla finestra e non al processo - al contrario
     * dell'Orologio e della Musica, che devono sopravviverle: una sveglia non
     * e' di una finestra, e un brano nemmeno. Una previsione si', e senza
     * questa chiamata ogni ricostruzione dell'Activity lascerebbe dietro un
     * thread addormentato.
     */
    public void chiudi() {
        sospendi();
        ascolto = null;
        lavoro.removeCallbacksAndMessages(null);
        filo.quit();
    }

    // ---- quello che sa ------------------------------------------------------

    public String citta()    { return attuale.nome; }
    public Posto attuale()   { return attuale; }
    public Posto[] preferiti() { return preferiti; }
    public Adesso adesso()   { return adesso; }
    public Ora[] ore()       { return ore; }
    public Giorno[] giorni() { return giorni; }
    public boolean inCorso() { return inCorso; }
    public long quando()     { return quando; }

    /** Perche' non c'e' niente da mostrare, quando non c'e'. null se non c'e'
     *  niente da spiegare - cioe' quasi sempre. */
    public String nota() { return adesso == null ? perche : null; }

    /**
     * Le prossime parti della giornata, saltando quella in corso.
     *
     * Quella in corso non si mette perche' <b>e' « adesso »</b>, ed e' gia'
     * scritta grande accanto al disegno: ripeterla in una tessera vorrebbe dire
     * dare due volte lo stesso numero e togliere il posto a quello che ancora
     * non si sa.
     *
     * Il codice del tempo della fascia e' il <b>piu' grave</b> delle sue ore,
     * non il primo ne' il piu' frequente: un pomeriggio con cinque ore di sole
     * e un temporale e' un pomeriggio in cui piove, e dire « sereno » sarebbe
     * la sola cosa che nessuno perdona a un meteo.
     */
    public Fascia[] fasce(int quante) { return fasce(quante, true); }

    /**
     * Le parti della giornata che devono ancora venire.
     *
     * @param saltaLaCorrente true per la Home, che « adesso » ce l'ha gia'
     *        scritto grande accanto al disegno; false per chi vuole anche le
     *        ore che restano di questa mattina.
     */
    public Fascia[] fasce(int quante, boolean saltaLaCorrente) {
        Ora[] o = ore;
        if (o.length == 0 || quante <= 0) return NESSUNA_FASCIA;

        int i = 0;
        while (i < o.length && o[i].passata) i++;
        if (i >= o.length) return NESSUNA_FASCIA;
        if (saltaLaCorrente) {
            int inCorso = fasciaDi(o[i].ora), giorno = o[i].quandoGiorno;
            while (i < o.length && o[i].quandoGiorno == giorno
                   && fasciaDi(o[i].ora) == inCorso) i++;
        }
        return raggruppa(o, i, quante);
    }

    /**
     * Le quattro parti di <b>oggi</b>, dalla notte alla sera, passate comprese.
     *
     * E' la riga di linguette della pagina, ed e' il motivo per cui le ore
     * passate non si buttano: alle dieci di mattina « notte » e mezza
     * « mattina » sono gia' successe, e una giornata che comincia dall'ora in
     * cui uno guarda non e' una giornata.
     */
    public Fascia[] fasceDiOggi() {
        Ora[] o = ore;
        if (o.length == 0) return NESSUNA_FASCIA;
        int i = 0;
        while (i < o.length && o[i].quandoGiorno < 0) i++;
        if (i >= o.length || o[i].quandoGiorno > 0) return NESSUNA_FASCIA;
        return raggruppa(o, i, 4);
    }

    /**
     * Raggruppa le ore consecutive che stanno nella stessa parte dello stesso
     * giorno.
     *
     * Il codice del tempo della fascia e' il <b>piu' grave</b> delle sue ore,
     * non il primo ne' il piu' frequente: un pomeriggio con cinque ore di sole
     * e un temporale e' un pomeriggio in cui piove, e dire « sereno » sarebbe
     * la sola cosa che a un meteo non si perdona. I gradi sono la media - la
     * massima e' l'istante piu' caldo, la media e' com'e' stato.
     */
    private static Fascia[] raggruppa(Ora[] o, int i, int quante) {
        Fascia[] fuori = new Fascia[quante];
        int trovate = 0;
        while (i < o.length && trovate < quante) {
            int id = fasciaDi(o[i].ora), giorno = o[i].quandoGiorno;
            int partenza = i;
            float somma = 0f;
            int quanteOre = 0, pioggia = 0, peggiore = 0, gravita = -1;
            boolean diGiorno = false;
            while (i < o.length && o[i].quandoGiorno == giorno
                   && fasciaDi(o[i].ora) == id) {
                somma += o[i].gradi;
                quanteOre++;
                int g = gravita(o[i].codice);
                if (g > gravita) { gravita = g; peggiore = o[i].codice; }
                if (o[i].pioggia > pioggia) pioggia = o[i].pioggia;
                if (o[i].giorno) diGiorno = true;
                i++;
            }
            Fascia f = new Fascia();
            // La notte che segue la sera di oggi cade nel giorno dopo per il
            // calendario, ma per chi la nomina e' « stanotte »: « dom. notte »
            // e' giusto e si legge sbagliato.
            f.nome = (id == 0 && giorno == 1) ? "stanotte"
                   : giorno > 0 ? "dom. " + NOME_FASCIA[id] : NOME_FASCIA[id];
            f.gradi = somma / Math.max(1, quanteOre);
            f.codice = peggiore;
            f.pioggia = pioggia;
            f.giorno = diGiorno;
            f.da = partenza;
            f.quante = quanteOre;
            fuori[trovate++] = f;
        }
        if (trovate == quante) return fuori;
        Fascia[] esatte = new Fascia[trovate];
        System.arraycopy(fuori, 0, esatte, 0, trovate);
        return esatte;
    }

    private static int fasciaDi(int ora) {
        if (ora < 6) return 0;
        if (ora < 12) return 1;
        if (ora < 18) return 2;
        return 3;
    }

    /**
     * Quanto « conta » un codice del tempo.
     *
     * Non e' l'ordine dei numeri WMO, e va scritta a mano: la neve ha codici
     * piu' bassi dei rovesci di pioggia, e prendere il massimo del codice
     * direbbe che un pomeriggio di neve con un rovescio e' un pomeriggio di
     * rovesci.
     */
    /**
     * Da dove viene il vento, in due lettere.
     *
     * Otto punti e non sedici: « Est-SudEst » in una colonna larga due dita non
     * si legge, e su un apparecchio di casa la differenza fra est e est-sudest
     * non ha mai cambiato la giornata di nessuno.
     */
    public static String verso(int gradi) {
        final String[] rosa = { "N", "NE", "E", "SE", "S", "SO", "O", "NO" };
        int i = (int) Math.round(((gradi % 360) + 360) % 360 / 45.0) % 8;
        return rosa[i];
    }

    private static int gravita(int codice) {
        if (codice >= 95) return 6;   // temporale
        if (codice >= 85) return 5;   // rovesci di neve
        if (codice >= 80) return 4;   // rovesci
        if (codice >= 71) return 5;   // neve
        if (codice >= 61) return 4;   // pioggia
        if (codice >= 51) return 3;   // pioviggine
        if (codice >= 45) return 2;   // nebbia
        if (codice >= 2)  return 1;   // nuvole
        return 0;                     // sereno
    }

    /** Oggi, che porta minima, massima, alba e tramonto: le quattro cose che
     *  {@link Adesso} non sa di suo. */
    public Giorno oggi() {
        Giorno[] g = giorni;
        return g.length > 0 ? g[0] : null;
    }

    // ---- la richiesta -------------------------------------------------------

    /**
     * Rinfresca, se e' il caso.
     *
     * Si chiama entrando nella Home e aprendo la pagina: se il dato c'e' ed e'
     * fresco non fa niente, quindi entrare e uscire dieci volte non costa dieci
     * richieste. Dopo un errore si aspetta {@link #RIPROVA} prima di ritentare,
     * o un tablet senza rete busserebbe a ogni fotogramma.
     */
    public void aggiorna() {
        long ora = System.currentTimeMillis();
        if (inCorso) return;
        if (adesso != null && ora - quando < SCADENZA) return;
        if (ora - ultimoTentativo < RIPROVA) return;
        chiedi();
    }

    /**
     * Si e' tornati - dopo ore senza nessuno, con la rete appena ritornata:
     * rinfresca se e' vecchio, <b>senza aspettare</b> {@link #RIPROVA}. Il
     * perche' e' lo stesso di {@code Notizie.riprova}.
     */
    public void riprova() {
        ultimoTentativo = 0L;
        aggiorna();
    }

    // ---- il controllo mentre Casa e' in scena -----------------------------

    /** Ogni quanto si guarda se e' ora di chiedere. Guardare costa un
     *  confronto; la richiesta parte solo a {@link #SCADENZA} passata. */
    private static final long CONTROLLO = 5 * 60 * 1000L;

    /**
     * Il giro che mancava.
     *
     * {@link #aggiorna()} si chiamava solo <b>entrando</b> nella Home o nella
     * pagina. Ma la Home e' la schermata che resta in scena per giorni: nessuno
     * ci entra, perche' non ne e' mai uscito. Il meteo restava quello del
     * giorno in cui qualcuno aveva cambiato sezione l'ultima volta - e chi
     * tornava a casa dopo tre giorni lo trovava li'.
     */
    private final Runnable controllo = new Runnable() {
        @Override public void run() {
            aggiorna();
            ui.postDelayed(this, CONTROLLO);
        }
    };

    /** Casa e' sullo schermo: il meteo si tiene fresco da solo. */
    public void riprendi() {
        ui.removeCallbacks(controllo);
        ui.post(controllo);
    }

    /** Casa e' dietro: nessuno guarda, nessuno chiede. */
    public void sospendi() {
        ui.removeCallbacks(controllo);
    }

    /** Rinfresca comunque: e' il tasto « aggiorna » della pagina. */
    public void rinfresca() {
        if (inCorso) return;
        chiedi();
    }

    private void chiedi() {
        inCorso = true;
        ultimoTentativo = System.currentTimeMillis();
        avvisa();
        lavoro.post(new Runnable() {
            @Override public void run() {
                try {
                    String risposta = scarica(indirizzo());
                    if (risposta == null) {
                        if (adesso == null) perche = "non riesco a leggere il meteo";
                        return;
                    }
                    JSONObject dati = new JSONObject(risposta);
                    if (!leggi(dati)) {
                        if (adesso == null) perche = "la risposta non si capisce";
                        return;
                    }
                    quando = System.currentTimeMillis();
                    perche = null;
                    salvaSulDisco(dati);
                } catch (Exception e) {
                    Log.w(TAG, "Meteo: previsioni non lette", e);
                    if (adesso == null) perche = "non riesco a leggere il meteo";
                } finally {
                    inCorso = false;
                    avvisa();
                }
            }
        });
    }

    private String indirizzo() {
        Posto p = attuale;
        return PREVISIONI
            + "?latitude=" + p.lat + "&longitude=" + p.lon
            + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,"
            + "is_day,weather_code,wind_speed_10m"
            + "&hourly=temperature_2m,apparent_temperature,weather_code,"
            + "precipitation_probability,relative_humidity_2m,"
            + "wind_speed_10m,wind_direction_10m"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
            + "precipitation_probability_max,sunrise,sunset"
            + "&timezone=Europe%2FRome"
            + "&forecast_days=" + GIORNI
            + "&past_hours=" + ORE_INDIETRO
            + "&forecast_hours=" + ORE_AVANTI;
    }

    // ---- i luoghi -------------------------------------------------------------

    /**
     * Cerca un posto per nome.
     *
     * Il geocoder di Open-Meteo, che non vuole una chiave nemmeno lui. Torna
     * fino a sei candidati con la regione e il paese: senza quelli non si
     * distingue la Napoli della Campania da quella della Florida, ed e'
     * esattamente il caso in cui uno sceglie quella sbagliata e poi non capisce
     * perche' il tablet dice che nevica.
     */
    public void cerca(final String testo, final Trovati chi) {
        if (chi == null) return;
        final String cosa = testo == null ? "" : testo.trim();
        if (cosa.length() < 2) { rispondi(chi, NESSUN_POSTO, null); return; }
        lavoro.post(new Runnable() {
            @Override public void run() {
                try {
                    String risposta = scarica(CERCA_POSTO + "?name="
                            + java.net.URLEncoder.encode(cosa, "UTF-8")
                            + "&count=" + MAX_PREFERITI + "&language=it&format=json");
                    JSONArray elenco = risposta == null ? null
                            : new JSONObject(risposta).optJSONArray("results");
                    if (elenco == null || elenco.length() == 0) {
                        rispondi(chi, NESSUN_POSTO, "non trovo « " + cosa + " »");
                        return;
                    }
                    Posto[] fuori = new Posto[elenco.length()];
                    int quanti = 0;
                    for (int i = 0; i < elenco.length(); i++) {
                        Posto p = leggiPosto(elenco.optJSONObject(i));
                        if (p != null) fuori[quanti++] = p;
                    }
                    Posto[] esatti = new Posto[quanti];
                    System.arraycopy(fuori, 0, esatti, 0, quanti);
                    rispondi(chi, esatti, quanti == 0 ? "non trovo « " + cosa + " »" : null);
                } catch (Exception e) {
                    Log.w(TAG, "Meteo: ricerca di « " + cosa + " » fallita", e);
                    rispondi(chi, NESSUN_POSTO, "non riesco a cercare");
                }
            }
        });
    }

    /** Il nome, e sotto di che regione e di che paese e'. */
    private static Posto leggiPosto(JSONObject o) {
        if (o == null) return null;
        String nome = o.optString("name", null);
        if (nome == null) return null;
        String regione = o.optString("admin1", "");
        String paese = o.optString("country", "");
        StringBuilder dove = new StringBuilder();
        if (regione.length() > 0) dove.append(regione);
        if (paese.length() > 0) {
            if (dove.length() > 0) dove.append(", ");
            dove.append(paese);
        }
        return new Posto(nome, dove.toString(),
                o.optDouble("latitude", 0), o.optDouble("longitude", 0));
    }

    private void rispondi(final Trovati chi, final Posto[] posti, final String perche) {
        ui.post(new Runnable() {
            @Override public void run() { chi.luoghi(posti, perche); }
        });
    }

    /**
     * Va in un posto, e se lo ricorda.
     *
     * Il dato di prima e' di un'altra citta' e va buttato subito, o per qualche
     * secondo la pagina direbbe il tempo di Roma sotto il nome di Milano.
     */
    public void vaiA(Posto p) {
        if (p == null || p.uguale(attuale)) return;
        attuale = p;
        ricorda(p);
        adesso = null;
        ore = NESSUNA_ORA;
        giorni = NESSUN_GIORNO;
        quando = 0L;
        ultimoTentativo = 0L;
        Log.i(TAG, "Meteo: adesso guardo " + p.nome + " (" + p.lat + ", " + p.lon + ")");
        salvaPiuTardi();
        avvisa();
        rinfresca();
    }

    /**
     * Va in un posto dandone il nome: e' la strada del broadcast da adb.
     *
     *     adb shell am broadcast -a dev.casa.METEO --es citta "Milano"
     */
    public void vaiA(final String nome) {
        cerca(nome, new Trovati() {
            @Override public void luoghi(Posto[] posti, String perche) {
                if (posti.length > 0) vaiA(posti[0]);
                else Log.i(TAG, "Meteo: « " + nome + " » non l'ho trovata");
            }
        });
    }

    /** Mette un posto fra quelli tenuti da parte. Il piu' recente sta per
     *  primo: chi ne aggiunge uno lo sta usando adesso. */
    public void ricorda(Posto p) {
        if (p == null) return;
        Posto[] vecchi = preferiti;
        Posto[] nuovi = new Posto[Math.min(vecchi.length + 1, MAX_PREFERITI)];
        int quanti = 0;
        nuovi[quanti++] = p;
        for (Posto v : vecchi) {
            if (quanti >= nuovi.length) break;
            if (v.uguale(p)) continue;
            nuovi[quanti++] = v;
        }
        Posto[] esatti = new Posto[quanti];
        System.arraycopy(nuovi, 0, esatti, 0, quanti);
        preferiti = esatti;
        salvaPiuTardi();
        avvisa();
    }

    /** Toglie un posto dall'elenco. Quello in cui si sta non si toglie: sarebbe
     *  una riga che sparisce sotto il dito e un tablet che continua a mostrare
     *  un posto che non e' piu' in elenco. */
    public void dimentica(Posto p) {
        if (p == null || p.uguale(attuale)) return;
        Posto[] vecchi = preferiti;
        Posto[] nuovi = new Posto[vecchi.length];
        int quanti = 0;
        for (Posto v : vecchi) if (!v.uguale(p)) nuovi[quanti++] = v;
        Posto[] esatti = new Posto[quanti];
        System.arraycopy(nuovi, 0, esatti, 0, quanti);
        preferiti = esatti;
        salvaPiuTardi();
        avvisa();
    }

    /**
     * Riscrive il file dal filo di lavoro.
     *
     * Cambiare luogo e metterne uno da parte arrivano da un dito, cioe' dal
     * thread dell'interfaccia, e scrivere {@code meteo.json} vuol dire leggerlo,
     * ricomporlo e fare un {@code fsync} - roba da qualche millisecondo, ma
     * millisecondi tolti al fotogramma. Sul filo che c'e' gia' non costa niente
     * a nessuno.
     */
    private void salvaPiuTardi() {
        lavoro.post(new Runnable() {
            @Override public void run() { salvaSulDisco(null); }
        });
    }

    // ---- lettura della risposta ---------------------------------------------

    /** true se c'era abbastanza per riempire almeno « adesso ». */
    private boolean leggi(JSONObject dati) {
        JSONObject c = dati.optJSONObject("current");
        if (c == null) return false;

        Adesso a = new Adesso();
        a.gradi     = (float) c.optDouble("temperature_2m", 0);
        a.percepiti = (float) c.optDouble("apparent_temperature", a.gradi);
        a.umidita   = c.optInt("relative_humidity_2m", 0);
        a.vento     = (float) c.optDouble("wind_speed_10m", 0);
        a.codice    = c.optInt("weather_code", 0);
        a.giorno    = c.optInt("is_day", 1) == 1;

        // I giorni per primi: le ore chiedono a loro se sono di giorno o di
        // notte, e senza alba e tramonto ripiegherebbero su un sei-venti.
        giorni = leggiGiorni(dati.optJSONObject("daily"));
        ore    = leggiOre(dati.optJSONObject("hourly"));
        adesso = a;
        return true;
    }

    private Giorno[] leggiGiorni(JSONObject d) {
        if (d == null) return NESSUN_GIORNO;
        JSONArray quandi   = d.optJSONArray("time");
        JSONArray codice   = d.optJSONArray("weather_code");
        JSONArray massime  = d.optJSONArray("temperature_2m_max");
        JSONArray minime   = d.optJSONArray("temperature_2m_min");
        JSONArray pioggia  = d.optJSONArray("precipitation_probability_max");
        JSONArray alba     = d.optJSONArray("sunrise");
        JSONArray tramonto = d.optJSONArray("sunset");
        if (quandi == null) return NESSUN_GIORNO;

        int quanti = Math.min(quandi.length(), GIORNI);
        Giorno[] fuori = new Giorno[quanti];
        for (int i = 0; i < quanti; i++) {
            Giorno g = new Giorno();
            String data = quandi.optString(i, null);
            g.nome     = i == 0 ? "oggi" : i == 1 ? "domani" : nomeDelGiorno(data);
            g.codice   = codice  != null ? codice.optInt(i, 0) : 0;
            g.max      = massime != null ? (float) massime.optDouble(i, 0) : 0f;
            g.min      = minime  != null ? (float) minime.optDouble(i, 0) : 0f;
            g.pioggia  = pioggia != null ? pioggia.optInt(i, 0) : 0;
            g.alba     = oraDi(alba     != null ? alba.optString(i, null) : null);
            g.tramonto = oraDi(tramonto != null ? tramonto.optString(i, null) : null);
            fuori[i] = g;
        }
        return fuori;
    }

    /**
     * Le ore: tutte quelle che arrivano, passate comprese.
     *
     * <b>Le passate non si buttano.</b> La prima versione le tagliava via -
     * cercava « adesso » nell'elenco e partiva da li' - perche' sembrava ovvio
     * che di un'ora finita non importi a nessuno. Non e' ovvio: la pagina
     * mostra la giornata divisa in quattro fasce, e alle dieci di mattina meta'
     * della giornata e' gia' successa. Buttarla vuol dire due linguette vuote e
     * una giornata che comincia a un'ora diversa ogni volta che si guarda.
     *
     * Qui invece ogni ora si porta dietro <b>quando e'</b>: che giorno rispetto
     * a oggi, se e' passata, se e' quella in corso. Chi disegna decide cosa
     * farne - la pagina le smorza, la Home parte dalla prima che deve venire.
     */
    private Ora[] leggiOre(JSONObject h) {
        if (h == null) return NESSUNA_ORA;
        JSONArray quandi    = h.optJSONArray("time");
        JSONArray gradi     = h.optJSONArray("temperature_2m");
        JSONArray percepiti = h.optJSONArray("apparent_temperature");
        JSONArray codice    = h.optJSONArray("weather_code");
        JSONArray pioggia   = h.optJSONArray("precipitation_probability");
        JSONArray umidita   = h.optJSONArray("relative_humidity_2m");
        JSONArray vento     = h.optJSONArray("wind_speed_10m");
        JSONArray direzione = h.optJSONArray("wind_direction_10m");
        if (quandi == null || gradi == null) return NESSUNA_ORA;

        String adessoTesto = adessoInTesto();
        String oggi = dataDelGiorno(0);
        String ieri = dataDelGiorno(-1);

        int quanti = Math.min(quandi.length(), ORE_INDIETRO + ORE_AVANTI);
        Ora[] fuori = new Ora[quanti];
        for (int k = 0; k < quanti; k++) {
            Ora o = new Ora();
            String t = quandi.optString(k, "");
            o.ora       = t.length() >= 13 ? numero(t.substring(11, 13)) : 0;
            o.gradi     = (float) gradi.optDouble(k, 0);
            o.percepiti = percepiti != null ? (float) percepiti.optDouble(k, o.gradi) : o.gradi;
            o.codice    = codice    != null ? codice.optInt(k, 0) : 0;
            o.pioggia   = pioggia   != null ? pioggia.optInt(k, 0) : 0;
            o.umidita   = umidita   != null ? umidita.optInt(k, 0) : 0;
            o.vento     = vento     != null ? (float) vento.optDouble(k, 0) : 0f;
            o.direzione = direzione != null ? direzione.optInt(k, 0) : 0;
            o.giorno    = diGiorno(t);
            // Le due stringhe si confrontano come testo: sono scritte uguali e
            // l'ordine alfabetico e' quello cronologico. Nessun fuso, nessuna
            // conversione, nessun Calendar per quarantotto righe.
            String data = t.length() >= 10 ? t.substring(0, 10) : "";
            o.quandoGiorno = data.equals(oggi) ? 0 : data.equals(ieri) ? -1 : 1;
            int confronto = t.compareTo(adessoTesto);
            o.adesso  = confronto == 0;
            o.passata = confronto < 0;
            fuori[k] = o;
        }
        return fuori;
    }

    /**
     * Se quell'ora sta fra l'alba e il tramonto del suo giorno.
     *
     * Serve alle tessere della striscia: un sole alle undici di sera e'
     * sbagliato anche quando il codice del tempo dice « sereno ». L'alba e il
     * tramonto arrivano gia' coi giorni; qui si cerca a quale giorno quell'ora
     * appartiene, confrontando la data - che nelle due risposte e' scritta
     * uguale.
     */
    private boolean diGiorno(String quando) {
        if (quando == null || quando.length() < 16) return true;
        String data = quando.substring(0, 10);
        String ora  = quando.substring(11, 16);
        Giorno[] g = giorni;
        for (int i = 0; i < g.length; i++) {
            if (g[i].alba == null || g[i].tramonto == null) continue;
            if (!dataDelGiorno(i).equals(data)) continue;
            return ora.compareTo(g[i].alba) >= 0 && ora.compareTo(g[i].tramonto) < 0;
        }
        // Senza il giorno giusto, il ripiego onesto: sei-venti.
        int h = numero(ora.substring(0, 2));
        return h >= 6 && h < 20;
    }

    /** La data del giorno numero i contata da oggi. Accetta anche i negativi:
     *  serve a riconoscere le ore di ieri. */
    private static String dataDelGiorno(int i) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, i);
        return String.format(Locale.ITALIAN, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }

    private static String adessoInTesto() {
        Calendar cal = Calendar.getInstance();
        return String.format(Locale.ITALIAN, "%04d-%02d-%02dT%02d:00",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY));
    }

    /** Da "2026-09-08T06:36" a "06:36". */
    private static String oraDi(String iso) {
        return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : null;
    }

    /** Da "2026-09-08" a "mar". */
    private static String nomeDelGiorno(String data) {
        if (data == null || data.length() < 10) return "";
        Calendar cal = Calendar.getInstance();
        cal.set(numero(data.substring(0, 4)), numero(data.substring(5, 7)) - 1,
                numero(data.substring(8, 10)), 12, 0, 0);
        return GIORNO_CORTO[cal.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private static int numero(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException storto) { return 0; }
    }

    // ---- rete ---------------------------------------------------------------

    /**
     * Una richiesta, con le radici che a questo Android mancano.
     *
     * Non e' statica proprio per {@link Fiducia}, che ha bisogno del contesto
     * per leggere i certificati dagli asset. Senza quella riga qui si prende
     * uno {@code SSLHandshakeException} su ogni richiesta, perche' Open-Meteo
     * ha un certificato Let's Encrypt e questo tablet e' Android 7.0: il perche'
     * per esteso sta li'.
     */
    private String scarica(String indirizzo) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            Fiducia.applica(contesto, c);
            c.setRequestMethod("GET");
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            int esito = c.getResponseCode();
            if (esito != 200) {
                Log.w(TAG, "Meteo: risposta " + esito + " da " + indirizzo);
                return null;
            }
            return Musica.tutto(c.getInputStream());
        } catch (Exception e) {
            Log.w(TAG, "Meteo: richiesta fallita", e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---- la copia su disco ---------------------------------------------------

    private void rileggiDalDisco() {
        JSONObject salvato = Archivio.leggi(contesto, FILE);
        if (salvato == null) return;
        Posto p = daJson(salvato.optJSONObject("posto"));
        if (p != null) attuale = p;
        JSONArray elenco = salvato.optJSONArray("preferiti");
        if (elenco != null) {
            Posto[] letti = new Posto[elenco.length()];
            int quanti = 0;
            for (int i = 0; i < elenco.length(); i++) {
                Posto v = daJson(elenco.optJSONObject(i));
                if (v != null) letti[quanti++] = v;
            }
            Posto[] esatti = new Posto[quanti];
            System.arraycopy(letti, 0, esatti, 0, quanti);
            preferiti = esatti;
        }
        JSONObject dati = salvato.optJSONObject("dati");
        if (dati == null) return;
        // Il « quando » e' quello di allora: cosi' aggiorna() sa da sola se
        // quello che si e' riletto e' ancora buono o va rifatto subito.
        quando = salvato.optLong("quando", 0);
        try {
            leggi(dati);
        } catch (Exception e) {
            Log.w(TAG, "Meteo: la copia su disco non si legge", e);
        }
    }

    /**
     * Riscrive il file.
     *
     * Con {@code dati} a null si riscrivono solo il posto e i preferiti,
     * tenendo le previsioni che c'erano: cambiare citta' e mettere da parte un
     * luogo passano di qui, e non hanno previsioni da salvare - se non si
     * rileggessero quelle vecchie, il file resterebbe senza dati fino alla
     * prossima risposta della rete.
     */
    private void salvaSulDisco(JSONObject dati) {
        try {
            if (dati == null) {
                JSONObject prima = Archivio.leggi(contesto, FILE);
                if (prima != null) dati = prima.optJSONObject("dati");
            }
            JSONObject fuori = new JSONObject();
            fuori.put("quando", quando);
            fuori.put("posto", aJson(attuale));
            JSONArray elenco = new JSONArray();
            for (Posto v : preferiti) elenco.put(aJson(v));
            fuori.put("preferiti", elenco);
            if (dati != null) fuori.put("dati", dati);
            Archivio.scrivi(contesto, FILE, fuori);
        } catch (Exception e) {
            Log.w(TAG, "Meteo: non ho potuto salvare " + FILE, e);
        }
    }

    private static JSONObject aJson(Posto p) throws org.json.JSONException {
        JSONObject o = new JSONObject();
        o.put("nome", p.nome);
        o.put("dove", p.dove);
        o.put("lat", p.lat);
        o.put("lon", p.lon);
        return o;
    }

    private static Posto daJson(JSONObject o) {
        if (o == null) return null;
        String nome = o.optString("nome", null);
        if (nome == null) return null;
        return new Posto(nome, o.optString("dove", ""),
                o.optDouble("lat", 0), o.optDouble("lon", 0));
    }

    private void avvisa() {
        final Ascolto a = ascolto;
        if (a == null) return;
        ui.post(new Runnable() {
            @Override public void run() { a.meteoCambiato(); }
        });
    }
}
