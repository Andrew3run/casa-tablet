package dev.casa;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Radio via internet, con il MediaPlayer di sistema.
 *
 * Niente ExoPlayer: MediaPlayer regge gli stream MP3 e AAC in HTTP, che e'
 * quello che trasmettono tutte le radio, e non aggiunge una riga di
 * dipendenze. La catena aapt2 -&gt; javac -&gt; d8 resta quella che e'.
 *
 * Lo stream parte in modo asincrono: prepareAsync() invece di prepare(), o il
 * primo secondo di rete bloccherebbe il thread dell'interfaccia e l'orologio
 * scatterebbe.
 *
 * <b>La radio pubblica una MediaSession</b>, e da li' vengono tre cose senza
 * scriverne nessuna:
 * <ol>
 *   <li>la riga "ora in riproduzione" la comanda con lo stesso codice con cui
 *       comanda Spotify, senza un caso speciale per la roba di casa;
 *   <li>i tasti media - cuffie, tastiera Bluetooth - la fermano e la fanno
 *       ripartire;
 *   <li>precedente e successivo diventano <b>cambio di stazione</b>, che su una
 *       radio e' quello che quei due tasti vogliono dire.
 * </ol>
 */
public class Radio implements Sordina.Nostra {

    /** Una stazione: come la si chiama a voce, come si scrive, il suo logo, e
     *  dove sta il flusso. */
    public static final class Stazione {
        public final String chiave;   // per riconoscerla nel parlato
        public final String nome;     // per scriverla a schermo
        public final String sigla;    // il ripiego quando il logo non c'e'
        public final int    colore;   // la sua tinta nella tessera
        public final int    logo;     // R.drawable.logo_xxx, oppure 0
        /** Il nome del logo come lo scrive il PC ("logo_dj"), per la vetrina.
         *  Il numero qui sopra non si puo' rimandare indietro: cambia a ogni
         *  ricompilazione, e fuori dall'APK non vuol dire niente. */
        public final String logoNome;
        /** true se il logo e' scuro e vuole una tessera chiara sotto. */
        public final boolean logoSuChiaro;
        public final String flusso;

        Stazione(String chiave, String nome, String sigla, int colore,
                 int logo, boolean logoSuChiaro, String flusso) {
            this(chiave, nome, sigla, colore, logo, null, logoSuChiaro, flusso);
        }

        Stazione(String chiave, String nome, String sigla, int colore,
                 int logo, String logoNome, boolean logoSuChiaro, String flusso) {
            this.chiave = chiave; this.nome = nome; this.sigla = sigla;
            this.colore = colore; this.logo = logo; this.logoNome = logoNome;
            this.logoSuChiaro = logoSuChiaro; this.flusso = flusso;
        }

        /**
         * Come la scrive la vetrina, cioe' come il PC se la ritrova.
         *
         * Il logo esce col suo NOME e non col suo numero, e il colore in
         * esadecimale e non in decimale con segno: sono i due punti in cui il
         * dentro dell'APK e il fuori parlano lingue diverse, e tradurre qui
         * evita di doverlo fare in PowerShell.
         */
        public JSONObject json(String comeSiChiamaIlLogo) {
            JSONObject o = new JSONObject();
            try {
                o.put("chiave", chiave);
                o.put("nome", nome);
                o.put("sigla", sigla);
                o.put("colore", Tinte.scrivi(colore));
                o.put("logo", comeSiChiamaIlLogo == null ? "" : comeSiChiamaIlLogo);
                o.put("chiaro", logoSuChiaro);
                o.put("flusso", flusso);
            } catch (JSONException storto) {
                // Una stazione che non si scrive esce vuota dalla vetrina, e il
                // PC vede una riga sbagliata invece di non vedere l'elenco.
                Log.w(TAG, "radio: " + nome + " non si scrive in vetrina", storto);
            }
            return o;
        }

        /**
         * Una stazione come la manda il PC.
         *
         * Il logo arriva per nome e si cerca fra i disegni dell'APK: quelli
         * sono ventuno e stanno dentro, e il PC puo' solo sceglierne uno fra
         * quelli - l'elenco glielo dice la vetrina. Un nome che non c'e' non e'
         * un errore: si resta con la sigla, che e' il ripiego previsto fin
         * dall'inizio.
         */
        static Stazione da(Context c, JSONObject o) {
            String chiave = o.optString("chiave", "").trim().toLowerCase(Locale.ITALIAN);
            String nome = o.optString("nome", "").trim();
            String flusso = o.optString("flusso", "").trim();
            if (nome.length() == 0 || flusso.length() == 0) return null;
            if (chiave.length() == 0) chiave = nome.toLowerCase(Locale.ITALIAN);

            String sigla = o.optString("sigla", "").trim();
            if (sigla.length() == 0) sigla = nome.substring(0, Math.min(3, nome.length()));

            String logoNome = o.optString("logo", "").trim();
            int logo = 0;
            if (logoNome.length() > 0) {
                logo = c.getResources().getIdentifier(logoNome, "drawable", c.getPackageName());
                if (logo == 0) {
                    Log.w(TAG, "radio: " + nome + " chiede il logo \"" + logoNome
                            + "\", che in questo APK non c'e'");
                    logoNome = null;
                }
            } else {
                logoNome = null;
            }

            return new Stazione(chiave, nome, sigla,
                    Tinte.leggi(o.optString("colore", ""), Tinte.RADIO),
                    logo, logoNome, o.optBoolean("chiaro", false), flusso);
        }
    }

    // I loghi sono file veri, non le iniziali, perche' un marchio e' quello che
    // si riconosce da un metro senza leggere. Sono 21 PNG da 128 pixel per
    // 234 KB in tutto, decodificati a meta' misura e tenuti in una cache con un
    // tetto: vedi Loghi.java.
    //
    // Non vengono dal catalogo di Radio-Browser. Quel campo lo riempie chi
    // aggiunge la stazione, e per meta' delle italiane conteneva un'immagine
    // qualunque: per Radio Capital una foto di repertorio, per Radio Monte
    // Carlo la copertina di un programma. Sono stati ripresi dall'icona che
    // ogni sito dichiara per se' - apple-touch-icon e simili - e guardati uno
    // per uno.
    //
    // Una sola stazione un logo utilizzabile non ce l'ha: Radio Capital, il cui
    // sito e' tutto costruito a runtime e non pubblica il marchio da nessuna
    // parte raggiungibile. Per lei resta la sigla, che e' meglio di un logo
    // sbagliato.
    //
    // Anche la sigla e' scritta a mano e non ricavata dal nome. Ricavarla
    // sembrava furbo e non lo era: prendendo le iniziali, "Rai Radio 1",
    // "Rai Radio 2" e "Rai Radio 3" venivano tutte e tre "RR", e "RTL 102.5" e
    // "Radio 105" tutte e due "R".
    //
    // Ogni flusso qui sotto e' stato **provato uno per uno** prima di finire in
    // questo file: chiesti i primi duemila byte e controllato il content-type.
    // Un elenco di indirizzi scritto a memoria e' un elenco di stazioni mute.
    //
    // I tre Kiss Kiss stanno su ice08.fluidstream.net e non sull'indirizzo che
    // il catalogo indica. Quello reindirizza a kisskiss.fluidstream.eu in
    // HTTPS, e quel certificato e' Let's Encrypt con radice ISRG Root X1: un
    // Android 7.0 non ce l'ha nell'elenco delle autorita' - e' la rottura nota
    // del settembre 2021, quando e' scaduto il cross-sign di DST Root CA X3.
    // Il tablet quindi non puo' aprire quello stream, e MediaPlayer non lo dice
    // nemmeno: resta in attesa. La stessa emittente serve gli stessi programmi
    // in HTTP semplice, ed e' quello che usiamo.

    // I nomi qui sono quelli corti, non quelli ufficiali: il logo dice gia' di
    // che stazione si tratta, e "Radio Monte Carlo" scritto sotto il marchio di
    // Radio Monte Carlo finiva troncato in "Radio Monte...". La chiave per il
    // parlato resta invece quella lunga, perche' a voce la si chiama per intero.

    /**
     * Le italiane che si ascoltano davvero.
     *
     * Da quando l'elenco lo scrive il PC, questo e' il punto di partenza e non
     * piu' la verita': vale finche' nessuno ha mai deciso niente. E' la stessa
     * regola delle lampade e delle app - vedi {@link Configurazione#elenco} -
     * e la differenza fra "non l'ho mai deciso" e "ho deciso che non ce ne
     * sono" vale anche qui: chi toglie l'ultima stazione dal PC resta senza
     * radio, non si ritrova queste ventidue.
     */
    static final Stazione[] DI_FABBRICA = {
        new Stazione("rai radio 1", "Rai Radio 1", "R1", 0xFF4A90D9, R.drawable.logo_r1, true,
                "http://icestreaming.rai.it/1.mp3"),
        new Stazione("rai radio 2", "Rai Radio 2", "R2", 0xFFE0654B, R.drawable.logo_r2, true,
                "http://icestreaming.rai.it/2.mp3"),
        new Stazione("rai radio 3", "Rai Radio 3", "R3", 0xFF8E6BC8, R.drawable.logo_r3, true,
                "http://icestreaming.rai.it/3.mp3"),
        new Stazione("radio deejay", "Radio Deejay", "DJ", 0xFFE05C8A, R.drawable.logo_dj, true,
                "http://streamcdnb1-4c4b867c89244861ac216426883d1ad0.msvdn.net/radiodeejay/radiodeejay/play1.m3u8"),
        new Stazione("radio capital", "Capital", "CAP", 0xFFC8642E, 0, false,
                "https://StreamCdnG15-4c4b867c89244861ac216426883d1ad0.msvdn.net/radiocapital/radiocapital/master_ma.m3u8"),
        new Stazione("m2o", "m2o", "m2o", 0xFF4FC3C7, R.drawable.logo_m2o, false,
                "https://StreamCdnG19-4c4b867c89244861ac216426883d1ad0.msvdn.net/radiom2o/radiom2o/master_ma.m3u8"),
        new Stazione("rtl 102.5", "RTL 102.5", "RTL", 0xFF3FB89A, R.drawable.logo_rtl, true,
                "https://StreamCdnG20-dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/live/S97044836/tbbP8T1ZRPBL/playlist_audio.m3u8"),
        new Stazione("radio 105", "Radio 105", "105", 0xFFE8A33D, R.drawable.logo_105, true,
                "http://icecast.unitedradio.it/Radio105.mp3"),
        new Stazione("virgin radio", "Virgin Radio", "VR", 0xFFD14545, R.drawable.logo_vr, true,
                "http://icecast.unitedradio.it/Virgin.mp3"),
        new Stazione("r101", "R101", "101", 0xFF7BA7DC, R.drawable.logo_101, true,
                "http://icecast.unitedradio.it/r101_mp3"),
        new Stazione("radio monte carlo", "Monte Carlo", "RMC", 0xFF2E86C1, R.drawable.logo_rmc, false,
                "http://icecast.unitedradio.it/RMC.mp3"),
        new Stazione("radio italia", "Radio Italia", "RI", 0xFF5AAF6B, R.drawable.logo_ri, false,
                "https://radioitaliasmi.akamaized.net/hls/live/2093120/RISMI/stream01/streamPlaylist.m3u8"),
        new Stazione("kiss kiss italia", "Kiss Italia", "KKI", 0xFFE0407A, R.drawable.logo_kki, false,
                "http://ice08.fluidstream.net/KKItalia.mp3"),
        new Stazione("kiss kiss napoli", "Kiss Napoli", "KKN", 0xFF2FA6D6, R.drawable.logo_kkn, true,
                "http://ice08.fluidstream.net/KKNapoli.mp3"),
        new Stazione("radio kiss kiss", "Kiss Kiss", "KK", 0xFFD6356B, R.drawable.logo_kk, false,
                "http://ice08.fluidstream.net/KissKiss.mp3"),
        new Stazione("radio 24", "Radio 24", "24", 0xFF9AA7B4, R.drawable.logo_24, true,
                "http://shoutcast2.radio24.it:8000/;"),
        new Stazione("rds", "RDS", "RDS", 0xFFE86A2E, R.drawable.logo_rds, false,
                "https://stream.rds.radio/audio/rds.stream_aac64/chunklist.m3u8"),
        new Stazione("radio zeta", "Radio Zeta", "Z", 0xFF6C7BD1, R.drawable.logo_z, true,
                "https://streamingv2.shoutcast.com/radio-zeta_48.aac"),
        new Stazione("radiofreccia", "Radiofreccia", "RF", 0xFFB0483C, R.drawable.logo_rf, true,
                "https://StreamCdnG11-dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/live/S3160845/0tuSetc8UFkF/playlist_audio.m3u8"),
        new Stazione("radio subasio", "Subasio", "SUB", 0xFF57A88E, R.drawable.logo_sub, true,
                "http://icy.unitedradio.it/Subasio.mp3"),
        new Stazione("radio sportiva", "Sportiva", "SPO", 0xFF4E8B3A, R.drawable.logo_spo, true,
                "http://sportiva.inmystream.it/stream/sportiva"),
        new Stazione("radio norba", "Radio Norba", "NOR", 0xFFCB5AA0, R.drawable.logo_nor, true,
                "http://onair20.xdevel.com:8348/;stream.mp3"),
    };

    /**
     * L'elenco vero, quello che si vede e si ascolta.
     *
     * Comincia da quello di fabbrica e diventa quello del PC appena qualcuno
     * ne scrive uno. Non e' un array come prima: le stazioni adesso si
     * aggiungono e si tolgono da fuori, e un array vorrebbe dire ricrearlo a
     * mano ogni volta.
     */
    private List<Stazione> stazioni = new ArrayList<Stazione>(Arrays.asList(DI_FABBRICA));

    /**
     * Rilegge l'elenco dalla configurazione.
     *
     * La chiama {@link MainActivity} all'avvio e ogni volta che il PC manda
     * qualcosa. <b>La stazione accesa si ritrova per chiave</b>, non per
     * posizione: chi riordina l'elenco dal PC mentre la radio suona non deve
     * sentirsi cambiare stazione sotto le mani, e la posizione dopo un
     * riordino non vuol dire piu' niente.
     */
    public void rileggi(Context c) {
        contesto = c.getApplicationContext();
        List<Stazione> nuove = new ArrayList<Stazione>();
        JSONArray scritte = Configurazione.elenco(c, "radio");
        if (scritte == null) {
            nuove.addAll(Arrays.asList(DI_FABBRICA));
        } else {
            for (int i = 0; i < scritte.length(); i++) {
                JSONObject o = scritte.optJSONObject(i);
                if (o == null) continue;
                Stazione s = Stazione.da(c, o);
                if (s != null) nuove.add(s);
            }
        }
        stazioni = nuove;
        Log.i(TAG, "radio: " + stazioni.size() + " stazioni"
                + (scritte == null ? " (di fabbrica)" : " (dal PC)"));

        if (corrente != null) {
            Stazione ritrovata = trova(corrente.chiave);
            // Se la stazione accesa e' stata tolta dall'elenco si continua a
            // suonarla: fermare la musica in faccia a chi sta ascoltando per
            // una modifica fatta dall'altra stanza sarebbe la reazione
            // sbagliata. Sparisce dalla griglia, e basta.
            if (ritrovata != null) corrente = ritrovata;
        }
    }

    /** L'elenco di adesso. Chi disegna ci cammina sopra e non lo tiene. */
    public List<Stazione> stazioni() { return stazioni; }

    /** Come la vetrina la fa vedere al PC. */
    public JSONArray json() {
        JSONArray a = new JSONArray();
        for (Stazione s : stazioni) a.put(s.json(nomeLogo(s)));
        return a;
    }

    /** Quelle scritte nel codice, per il tasto « rimetti quelle di fabbrica »
     *  del PC. Non sono configurazione e non tornano indietro: e' un catalogo,
     *  come le risposte di fabbrica. */
    public JSONArray jsonDiFabbrica() {
        JSONArray a = new JSONArray();
        for (Stazione s : DI_FABBRICA) a.put(s.json(nomeLogo(s)));
        return a;
    }

    /**
     * Come si chiama il logo di questa stazione.
     *
     * Quelle di fabbrica il nome non ce l'hanno: nel codice il logo e' scritto
     * come {@code R.drawable.logo_dj}, cioe' un numero, e un numero fuori
     * dall'APK non vuol dire niente - cambia a ogni ricompilazione. Il nome si
     * ritrova dalle risorse, ed e' l'unico punto in cui serve: senza, la
     * vetrina manderebbe al PC ventidue stazioni senza logo, e il PC gliele
     * rimanderebbe indietro cosi'. Ventidue marchi persi per un giro di
     * andata e ritorno.
     */
    private String nomeLogo(Stazione s) {
        if (s.logoNome != null) return s.logoNome;
        if (s.logo == 0 || contesto == null) return "";
        try {
            return contesto.getResources().getResourceEntryName(s.logo);
        } catch (Exception nonCe) {
            return "";
        }
    }

    /**
     * Chi vuole sapere cosa sta suonando.
     *
     * Passa <b>il nome e basta</b>, non una frase gia' composta. Prima
     * all'accensione mandava {@code "Radiofreccia - mi collego"}, e quella
     * stringa era due informazioni incollate: il nome, che resta, e uno stato
     * di passaggio, che dura tre secondi. Chi la riceveva non poteva piu'
     * separarle - ne dargli corpi, colori e righe diverse - e a schermo
     * intero non ci stava.
     *
     * Lo stato non serve mandarlo: chi disegna ha in mano questa stessa Radio
     * e lo chiede a {@link #staAprendo()} e {@link #errore()} nel momento in
     * cui disegna, che e' anche l'unico momento in cui e' vero.
     */
    public interface Spia {
        void suRadio(String cosaSuona);   // null quando e' spenta
    }

    private static final String TAG = "Casa.Radio";

    /** Oltre questo si smette di aspettare. Quindici secondi sono lunghi per
     *  una rete che va e cortissimi per una che non va: sotto, le stazioni piu'
     *  lente non farebbero in tempo a partire. */
    private static final long ATTESA_MS = 15000;

    private final Spia spia;
    private final android.os.Handler ui =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private MediaPlayer lettore;
    private Stazione corrente;
    private MediaSession sessione;

    /** Quello dell'applicazione, non di un'Activity: serve a leggere la
     *  configurazione e a scrivere l'ultima stazione accesa, e vive quanto il
     *  processo. Arriva da {@link #rileggi(Context)} o da {@link #pubblica}. */
    private Context contesto;

    /** Cresce a ogni accensione. Serve a buttare via le risposte in ritardo:
     *  se nel frattempo si e' scelta un'altra stazione, quello che arriva dalla
     *  precedente non deve toccare niente. */
    private int tentativo;
    private Runnable scadenza;
    private String errore;

    /**
     * I ricollegamenti dopo un'interruzione.
     *
     * <b>Una radio non finisce mai, quindi se finisce e' caduta la rete.</b>
     * Visto sul tablet il 14 settembre: il Wi-Fi sparisce per sei secondi,
     * NuCachedSource2 brucia i suoi dieci tentativi (« 0 retries left ») e il
     * MediaPlayer chiude il flusso come se fosse arrivato in fondo - con
     * onCompletion, <b>non</b> con onError. Senza un ascolto sulla fine il
     * lettore restava li' aperto e muto, e per Casa la radio « suonava »
     * ancora. Adesso fine ed errore a flusso avviato portano a
     * {@link #interrotto}, che riprova qualche volta prima di arrendersi.
     */
    private static final int MAX_RICOLLEGAMENTI = 5;
    private static final long PASSO_RICOLLEGAMENTO_MS = 2000;
    /** Dopo quanto ascolto regolare il conto dei ricollegamenti si azzera:
     *  una rete che cade una volta all'ora non deve esaurirli in una sera. */
    private static final long ASCOLTO_BUONO_MS = 60000;
    private int ricollegamenti;
    private long partitaAlle;
    private Runnable ricollega;

    /** true fra l'accendi e il primo suono: serve a scrivere "in attesa" invece
     *  che il nome, perche' su una rete lenta passano anche tre secondi. */
    private boolean inApertura;

    /** Il volume voluto, da 0 a 1. Non e' sempre 1: mentre Casa ascolta o
     *  parla, la Sordina lo porta a un decimo. */
    private float volume = 1f;

    /** La musica di Spotify, quando c'e'. Radio e musica escono dallo stesso
     *  altoparlante: accendere una deve fermare l'altra, o si sentono insieme e
     *  il volume non basta piu' per nessuna delle due. Il legame e' reciproco -
     *  {@link Musica#suona} spegne la radio - e sta in queste due righe invece
     *  che sparso nei punti da cui si accende, che sono cinque: la sezione, la
     *  voce, i tasti media, la sessione e le routine. */
    private Musica musica;

    public Radio(Spia spia) { this.spia = spia; }

    public void setMusica(Musica m) { musica = m; }

    // ---- il telecomando ---------------------------------------------------

    /**
     * Pubblica la sessione media.
     *
     * Va fatto una volta sola e serve un Context, quindi non sta nel
     * costruttore: chi crea la radio non sempre ce l'ha sottomano.
     */
    public void pubblica(Context c) {
        if (contesto == null) contesto = c.getApplicationContext();
        if (sessione != null) return;
        try {
            sessione = new MediaSession(c, "Casa.Radio");
            sessione.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                            | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            sessione.setCallback(new MediaSession.Callback() {
                // inApertura nella guardia, non solo staSuonando: fra il
                // "accendi" e il primo suono passano dei secondi, e in quella
                // finestra un onPlay faceva ripartire l'apertura da capo -
                // due connessioni allo stesso flusso, e la seconda scavalcava
                // la prima proprio mentre stava per riuscire.
                @Override public void onPlay() {
                    if (!staSuonando() && !inApertura) accendi(corrente);
                }
                @Override public void onPause() { spegni(); }
                @Override public void onStop()  { spegni(); }
                // Su una radio, avanti e indietro vogliono dire cambiare
                // stazione: non c'e' un brano da saltare.
                @Override public void onSkipToNext()     { successiva(); }
                @Override public void onSkipToPrevious() { precedente(); }
            });
            sessione.setActive(true);
            aggiornaSessione();
        } catch (Exception e) {
            Log.w(MainActivity.TAG, "sessione media non pubblicata", e);
        }
    }

    private void aggiornaSessione() {
        if (sessione == null) return;
        try {
            boolean suona = staSuonando();
            sessione.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE,
                            corrente != null ? corrente.nome : "Radio")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Radio")
                    .build());
            sessione.setPlaybackState(new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                              | PlaybackState.ACTION_STOP
                              | PlaybackState.ACTION_SKIP_TO_NEXT
                              | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(suona ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_STOPPED,
                              PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build());
        } catch (Exception ignorato) { }
    }

    public void chiudi() {
        spegni();
        if (sessione != null) {
            try { sessione.setActive(false); sessione.release(); } catch (Exception ignorato) { }
            sessione = null;
        }
    }

    // ---- Sordina.Nostra ---------------------------------------------------
    //
    // Con la radio accesa, premere il microfono non serviva a niente: il
    // microfono sta a venti centimetri dall'altoparlante, e quello che sentiva
    // era la radio. Su questo hardware non c'e' cancellazione d'eco, quindi
    // l'unico rimedio e' abbassare la sorgente - e la radio e' nostra, quindi
    // la si abbassa esattamente invece di sperare nel focus audio.

    @Override
    public void abbassaVolume(float quanto) { volume = quanto; applicaVolume(); }

    @Override
    public void rialzaVolume() { volume = 1f; applicaVolume(); }

    private void applicaVolume() {
        if (lettore == null) return;
        try { lettore.setVolume(volume, volume); } catch (Exception ignorato) { }
    }

    // ---- stazioni ---------------------------------------------------------

    /**
     * Cerca la stazione nominata dentro la frase.
     * Torna null se non ne riconosce nessuna.
     */
    public Stazione riconosci(String frase) {
        // Senza accenti come la frase che arriva dai comandi: il perche' sta su
        // Testo.senzaAccenti. Vale anche per le chiavi, che le scrive il PC e
        // possono averne.
        String f = Testo.senzaAccenti(frase.toLowerCase(Locale.ITALIAN));

        // Prima le chiavi piu' lunghe: "kiss kiss napoli" deve vincere su
        // "kiss kiss", altrimenti chiedendo Napoli si accende la nazionale.
        Stazione migliore = null;
        for (Stazione s : stazioni) {
            if (f.contains(Testo.senzaAccenti(s.chiave))
                    && (migliore == null || s.chiave.length() > migliore.chiave.length())) {
                migliore = s;
            }
        }
        if (migliore != null) return migliore;

        // Come si dicono davvero, che non e' come sono scritte.
        if (f.contains("radio uno") || f.contains("radio 1")) return trova("rai radio 1");
        if (f.contains("radio due") || f.contains("radio 2")) return trova("rai radio 2");
        if (f.contains("radio tre") || f.contains("radio 3")) return trova("rai radio 3");
        if (f.contains("erre ti elle") || f.contains("rtl")) return trova("rtl 102.5");
        if (f.contains("monte carlo") || f.contains("emme ci")) return trova("radio monte carlo");
        if (f.contains("kiss")) return trova("radio kiss kiss");
        if (f.contains("erre di esse")) return trova("rds");
        if (f.contains("centocinque") || f.contains("cento cinque")) return trova("radio 105");
        if (f.contains("ventiquattro")) return trova("radio 24");
        if (f.contains("emme due o") || f.contains("m due o")) return trova("m2o");
        if (f.contains("freccia")) return trova("radiofreccia");
        return null;
    }

    /** La stazione con quella chiave, o null. E' anche il modo in cui
     *  {@link Ultimo} ritrova quella che si stava ascoltando ieri sera. */
    public Stazione trova(String chiave) {
        if (chiave == null) return null;
        for (Stazione s : stazioni) if (s.chiave.equals(chiave)) return s;
        return null;
    }

    public boolean staSuonando() { return lettore != null; }

    /**
     * La sessione audio del lettore, o zero se non sta suonando.
     *
     * Serve alle colonne del suono della Home: un effetto di visualizzazione si
     * aggancia a una sessione, e su questo ROM la sessione zero - il
     * mescolatore di uscita del sistema - non si lascia agganciare. Quella di
     * un MediaPlayer si'.
     */
    public int sessioneAudio() {
        MediaPlayer l = lettore;
        if (l == null) return 0;
        try {
            return l.getAudioSessionId();
        } catch (Exception spento) {
            return 0;
        }
    }
    public boolean staAprendo()  { return inApertura; }

    public String nomeCorrente() { return corrente != null ? corrente.nome : null; }

    public Stazione stazioneCorrente() { return corrente; }

    public int indiceCorrente() {
        for (int i = 0; i < stazioni.size(); i++) if (stazioni.get(i) == corrente) return i;
        return -1;
    }

    /** Stazione dopo, in tondo. Se non ne suona nessuna, la prima. */
    public void successiva() {
        if (stazioni.isEmpty()) return;
        int i = indiceCorrente();
        accendi(stazioni.get(i < 0 ? 0 : (i + 1) % stazioni.size()));
    }

    /** Stazione prima, in tondo. */
    public void precedente() {
        if (stazioni.isEmpty()) return;
        int i = indiceCorrente();
        accendi(stazioni.get(i <= 0 ? stazioni.size() - 1 : i - 1));
    }

    /**
     * Accende. Se non e' detto quale, la prima dell'elenco.
     *
     * Due cose che sembrano dettagli e non lo sono.
     *
     * <b>C'e' un limite di tempo.</b> Prima non c'era, e con certe stazioni
     * MediaPlayer non chiamava ne' onPrepared ne' onError: restava li'. A
     * schermo si leggeva "mi collego" per sempre, che e' il peggiore dei
     * messaggi - dice che sta succedendo qualcosa quando non succede piu'
     * niente. Dopo {@link #ATTESA_MS} si smette e lo si dice.
     *
     * <b>Le playlist si risolvono prima.</b> Diversi indirizzi Shoutcast
     * finiscono per {@code ;listen.pls} o {@code .m3u} e non sono flussi: sono
     * file di testo che contengono l'indirizzo del flusso. MediaPlayer il .pls
     * non lo apre, e il sintomo e' identico - resta in attesa. Si scarica, si
     * legge la prima riga utile, e si da' a MediaPlayer quella. L'HLS
     * ({@code .m3u8}) invece si lascia stare: quello MediaPlayer lo sa fare.
     */
    public void accendi(Stazione s) {
        if (s == null) s = stazioni.isEmpty() ? null : stazioni.get(0);
        if (s == null) {
            // Nessuna stazione in elenco: il PC le ha tolte tutte. Non e' un
            // guasto, e dirlo e' meglio che non fare niente in silenzio.
            errore = "nessuna stazione in elenco";
            spia.suRadio(null);
            return;
        }
        if (musica != null) musica.ferma();
        spegniSilenzioso();
        annullaScadenza();
        annullaRicollegamento();
        // Un'accensione chiesta da qualcuno riparte da zero; quella di
        // ricollega() rimette il suo conto subito dopo.
        ricollegamenti = 0;

        corrente = s;
        inApertura = true;
        errore = null;            // l'errore di prima non riguarda questa
        tentativo++;

        // Da qui in poi il tasto play della Home sa dove ripartire. Si scrive
        // adesso e non quando la stazione si e' aperta davvero: una stazione
        // che stasera non parte resta quella che si stava ascoltando, e domani
        // ha tutto il diritto di riprovarci.
        if (contesto != null) Ultimo.radio(contesto, s);

        final int mio = tentativo;
        final Stazione scelta = s;

        // Solo il nome: che ci si stia collegando lo dice inApertura, e chi
        // disegna lo legge da li'. Vedi il commento su Spia.
        spia.suRadio(s.nome);
        armaScadenza(mio, s);

        // Si risolve sempre, su un thread a parte. Costa una richiesta e due
        // decimi di secondo, che spariscono dentro il tempo di buffering, e
        // toglie di mezzo in un colpo solo i reindirizzamenti e le playlist.
        new Thread(new Runnable() {
            @Override public void run() {
                final String vero = risolvi(scelta.flusso);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (mio != tentativo) return;   // ne hanno scelta un'altra
                        apri(scelta, vero, mio);
                    }
                });
            }
        }, "Casa-radio-apri").start();
    }

    /** Crea il lettore e comincia ad aprire. */
    private void apri(final Stazione scelta, String indirizzo, final int mio) {
        lettore = new MediaPlayer();
        lettore.setAudioStreamType(AudioManager.STREAM_MUSIC);

        lettore.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                if (mio != tentativo) return;
                annullaScadenza();
                mp.setVolume(volume, volume);
                mp.start();
                inApertura = false;
                partitaAlle = android.os.SystemClock.elapsedRealtime();
                if (ricollegamenti > 0) Log.i(TAG, scelta.nome + ": ricollegata");
                spia.suRadio(scelta.nome);
                aggiornaSessione();
            }
        });
        lettore.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                if (mio != tentativo) return;
                Log.w(TAG, scelta.nome + ": il flusso si e' interrotto");
                interrotto(scelta);
            }
        });
        lettore.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int che, int extra) {
                if (mio != tentativo) return true;
                Log.w(TAG, scelta.nome + ": errore " + che + "/" + extra);
                // A flusso avviato, o mentre si sta gia' ricollegando, un errore
                // e' la rete: si riprova. Solo alla prima apertura vuol dire che
                // la stazione non risponde.
                if (!inApertura || ricollegamenti > 0) interrotto(scelta);
                else fallita(scelta, "non risponde");
                return true;
            }
        });

        try {
            Log.i(TAG, "apro " + scelta.nome + ": " + indirizzo);
            lettore.setDataSource(indirizzo);
            lettore.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, scelta.nome + ": flusso non aperto", e);
            fallita(scelta, "indirizzo non valido");
        }
    }

    /** Si arrende, e lo dice. Il silenzio senza spiegazione e' un guasto. */
    private void fallita(Stazione s, String perche) {
        annullaScadenza();
        spegniSilenzioso();
        corrente = null;
        inApertura = false;
        errore = s.nome + ": " + perche;
        Log.w(TAG, errore);
        spia.suRadio(null);
        aggiornaSessione();
    }

    /**
     * Il flusso che suonava si e' fermato: si riprova, con attese via via piu'
     * lunghe, e dopo {@link #MAX_RICOLLEGAMENTI} ci si arrende e lo si dice.
     *
     * Nell'attesa si legge « mi collego » come a una prima accensione, che e'
     * esattamente quello che sta succedendo.
     */
    private void interrotto(final Stazione s) {
        annullaScadenza();
        if (partitaAlle > 0
                && android.os.SystemClock.elapsedRealtime() - partitaAlle > ASCOLTO_BUONO_MS) {
            ricollegamenti = 0;
        }
        partitaAlle = 0;
        if (ricollegamenti >= MAX_RICOLLEGAMENTI) {
            fallita(s, "la rete si e' interrotta");
            return;
        }
        spegniSilenzioso();
        ricollegamenti++;
        inApertura = true;
        errore = null;
        spia.suRadio(s.nome);
        aggiornaSessione();

        final int mio = tentativo;
        final int quale = ricollegamenti;
        long attesa = PASSO_RICOLLEGAMENTO_MS * quale;
        Log.i(TAG, s.nome + ": ricollego fra " + attesa / 1000 + " s (" + quale
                + " di " + MAX_RICOLLEGAMENTI + ")");
        annullaRicollegamento();
        ricollega = new Runnable() {
            @Override public void run() {
                ricollega = null;
                // Nel frattempo qualcuno l'ha spenta o ha scelto altro.
                if (mio != tentativo || corrente != s) return;
                accendi(s);
                ricollegamenti = quale;
            }
        };
        ui.postDelayed(ricollega, attesa);
    }

    private void annullaRicollegamento() {
        if (ricollega != null) { ui.removeCallbacks(ricollega); ricollega = null; }
    }

    private void armaScadenza(final int mio, final Stazione s) {
        scadenza = new Runnable() {
            @Override public void run() {
                if (mio != tentativo || !inApertura) return;
                // Durante un ricollegamento la rete puo' metterci di piu' a
                // tornare: non e' la stazione che non risponde.
                if (ricollegamenti > 0) { interrotto(s); return; }
                fallita(s, "non risponde (oltre " + (ATTESA_MS / 1000) + " secondi)");
            }
        };
        ui.postDelayed(scadenza, ATTESA_MS);
    }

    private void annullaScadenza() {
        if (scadenza != null) { ui.removeCallbacks(scadenza); scadenza = null; }
    }

    /** L'ultimo motivo per cui una stazione non e' partita, da mostrare a
     *  schermo. null quando non c'e' niente da spiegare. */
    public String errore() { return errore; }

    public void dimenticaErrore() { errore = null; }

    // ---- playlist ---------------------------------------------------------

    /**
     * Riporta un indirizzo Shoutcast alla sua forma buona.
     *
     * Su Shoutcast il flusso sta a {@code host:porta/;} e tutto quello che
     * segue quel punto e virgola e' il nome di una <i>rappresentazione</i>:
     * {@code ;listen.pls} e' l'elenco, {@code ;stream.mp3} e' l'audio. Chi
     * cataloga le stazioni copia quasi sempre il primo, perche' e' quello che
     * il sito offre da scaricare.
     *
     * Kiss Kiss Napoli era esattamente questo caso:
     * {@code http://wma08.fluidstream.net:3612/;listen.pls}. MediaPlayer si
     * metteva ad aspettare un audio e riceveva un file di testo, e non diceva
     * niente - ne' onPrepared ne' onError. Tagliando dopo il {@code /;} si
     * ottiene il flusso, senza nemmeno una connessione in piu'.
     */
    private static String normalizzaShoutcast(String url) {
        int i = url.indexOf("/;");
        return i > 0 ? url.substring(0, i + 2) : url;
    }

    /**
     * L'indirizzo che MediaPlayer puo' davvero aprire.
     *
     * <b>Questo e' il pezzo che faceva restare le stazioni su "mi collego".</b>
     * Kiss Kiss Napoli sta catalogata come
     * {@code http://wma08.fluidstream.net:3612/;listen.pls}, e quel server
     * risponde {@code 301} verso {@code https://kisskiss.fluidstream.eu/...}.
     * MediaPlayer su API 24 <b>non segue i reindirizzamenti che cambiano
     * protocollo</b> da http a https: non apre, non fallisce, resta li'. Nessun
     * errore, nessun log, niente.
     *
     * Dieci stazioni su ventidue reindirizzano. Non si possono nemmeno cablare
     * gli indirizzi finali: quelli delle CDN (msvdn) puntano al nodo di turno e
     * cambiano da soli. L'unica strada e' seguirli qui, a ogni accensione.
     *
     * Nello stesso giro si sciolgono anche le playlist: {@code .pls} e
     * {@code .m3u} sono elenchi di indirizzi, non audio, e MediaPlayer si
     * comporta allo stesso modo - aspetta un suono, riceve del testo, e tace.
     * L'HLS ({@code .m3u8}) invece si lascia stare: quello lo sa aprire.
     *
     * Se qualcosa va storto si torna l'indirizzo di partenza invece di
     * arrendersi: al peggio scade il tempo, e almeno le stazioni che
     * funzionavano continuano a funzionare.
     */
    private static String risolvi(String partenza) {
        String url = normalizzaShoutcast(partenza);
        for (int giro = 0; giro < 6; giro++) {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                // A mano, non con setInstanceFollowRedirects(true): quello di
                // serie si ferma davanti al cambio di protocollo esattamente
                // come MediaPlayer, ed e' il caso che ci serve risolvere.
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "Casa/1.0");
                c.setRequestProperty("Icy-MetaData", "1");

                int codice;
                try {
                    codice = c.getResponseCode();
                } catch (Exception e) {
                    // I server Shoutcast di prima generazione rispondono
                    // "ICY 200 OK" invece di "HTTP/1.0 200 OK", e
                    // HttpURLConnection non sa leggerla. Se succede vuol dire
                    // che siamo arrivati a un flusso: e' quello giusto.
                    return url;
                }

                if (codice >= 300 && codice < 400) {
                    String dove = c.getHeaderField("Location");
                    if (dove == null) return url;
                    url = new java.net.URL(new java.net.URL(url), dove).toString();
                    continue;
                }

                String tipo = c.getContentType();
                tipo = tipo == null ? "" : tipo.toLowerCase(Locale.ITALIAN);
                boolean elenco = tipo.contains("scpls") || tipo.contains("mpegurl");
                if (elenco && !url.toLowerCase(Locale.ITALIAN).contains(".m3u8")) {
                    String dentro = primoIndirizzo(c.getInputStream());
                    if (dentro == null) return url;
                    url = dentro;
                    continue;
                }
                return url;

            } catch (Exception e) {
                Log.w(TAG, "non risolvo " + url + ": " + e);
                return url;
            } finally {
                if (c != null) try { c.disconnect(); } catch (Exception ignorato) { }
            }
        }
        return url;
    }

    /** Il primo indirizzo http dentro un .pls o un .m3u, o null. */
    private static String primoIndirizzo(java.io.InputStream dentro) {
        java.io.BufferedReader lettura = null;
        try {
            lettura = new java.io.BufferedReader(new java.io.InputStreamReader(dentro), 4096);
            String riga;
            int quante = 0;
            while ((riga = lettura.readLine()) != null && quante++ < 80) {
                riga = riga.trim();
                // Nel .pls le righe sono "File1=http://...", nel .m3u
                // l'indirizzo sta da solo e i commenti cominciano per #.
                int uguale = riga.indexOf('=');
                if (uguale > 0 && riga.toLowerCase(Locale.ITALIAN).startsWith("file")) {
                    riga = riga.substring(uguale + 1).trim();
                }
                if (riga.startsWith("http://") || riga.startsWith("https://")) return riga;
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (lettura != null) try { lettura.close(); } catch (Exception ignorato) { }
        }
    }

    public void spegni() {
        annullaScadenza();
        annullaRicollegamento();
        ricollegamenti = 0;
        tentativo++;              // le risposte in volo non contano piu'
        spegniSilenzioso();
        corrente = null;
        inApertura = false;
        errore = null;
        spia.suRadio(null);
        aggiornaSessione();
    }

    /** Chiude il lettore senza annunciare niente: serve al cambio di stazione,
     *  dove fra la vecchia e la nuova non deve comparire un "spenta". */
    private void spegniSilenzioso() {
        if (lettore == null) return;
        try { lettore.reset(); } catch (Exception ignorato) { }
        lettore.release();
        lettore = null;
    }
}
