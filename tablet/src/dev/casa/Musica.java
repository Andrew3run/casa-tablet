package dev.casa;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spotify senza Spotify.
 *
 * <b>Il problema.</b> L'app ufficiale costa 260-290 MB di RAM e duecento
 * thread su un tablet che di RAM ne ha 1024 in tutto: un quarto della memoria
 * della casa per suonare della musica. Non e' un difetto di configurazione, e'
 * il prezzo dell'app su questo hardware, e non c'e' pulizia che lo cambi.
 *
 * <b>La cura.</b> Il protocollo Spotify e' implementato per intero da
 * <a href="https://github.com/devgianlu/go-librespot">go-librespot</a>, un
 * demone di diciannove megabyte che qui dentro sta in <b>dodici megabyte di
 * RSS</b> - ventiquattro volte meno - e non dipende da Play Services. Casa lo
 * lancia come processo figlio e gli parla in due modi:
 *
 * <pre>
 *   audio     demone --FIFO--&gt; {@link TuboAudio} --&gt; AudioTrack
 *   comandi   Casa   --HTTP--&gt; 127.0.0.1:24879
 * </pre>
 *
 * Il binario viaggia dentro l'APK sotto {@code lib/armeabi-v7a/}, travestito da
 * libreria: e' l'unico posto da cui l'installer estrae un file <b>con il bit di
 * esecuzione</b>, e un'app non puo' fare chmod. Vedi {@code jniLibs/PROVENIENZA.md}.
 *
 * <b>L'ingresso si fa una volta sola, e senza tastiera.</b> Digitare una
 * password di Spotify su un tablet appeso al muro e' una pena; con
 * {@code credentials.type: device_auth} il demone stampa un indirizzo e un
 * codice di sei caratteri, la sezione Musica li mostra grandi, e si approva dal
 * telefono. Da li' in poi le credenziali stanno in {@code credentials.json} e
 * non le chiede piu' nessuno.
 *
 * <b>Zeroconf resta acceso quando si sa su che interfaccia.</b> Cosi' Casa
 * compare anche nell'elenco Connect del telefono e ci si puo' mandare la
 * musica da fuori. Il demone da solo l'interfaccia non la trova - SELinux nega
 * netlink alle app, e leggere {@code /proc/net} e' negato uguale - quindi
 * gliela diciamo noi, che dall'app si sa chiedere a
 * {@link ConnectivityManager}.
 *
 * <b>Uno per processo</b>, come l'{@link Orologio}: la musica non appartiene a
 * una finestra, e se Android rifa' l'Activity il brano non si interrompe.
 */
public final class Musica implements Sordina.Nostra {

    private static final String TAG = "Casa.Musica";

    /** Come compare nell'elenco Connect del telefono. */
    private static final String NOME_APPARECCHIO = "Casa";

    /** 160 kbps e non 320: la decodifica Vorbis la fa un Cortex-A7 del 2019, e
     *  l'altoparlante di un tablet non distingue le due cose. */
    private static final int QUALITA = 160;

    private static final String INDIRIZZO_API = "http://127.0.0.1:24879";

    /** Ogni quanto si richiede lo stato. Non serve di piu': la posizione fra
     *  una lettura e l'altra si conta da soli, e la barra scorre liscia. */
    private static final long GIRO_SUONA = 2000, GIRO_FERMO = 6000, GIRO_AVVIO = 700;

    // ---- gli stati in cui puo' essere ---------------------------------------

    public static final int SPENTA    = 0;   // non e' mai partita, o l'hanno chiusa
    public static final int AVVIO     = 1;   // il demone c'e', non e' ancora pronto
    public static final int ACCOPPIA  = 2;   // serve l'approvazione dal telefono
    public static final int PRONTA    = 3;   // si puo' suonare
    public static final int GUASTO    = 4;   // e' andata male, e sappiamo dire perche'

    public interface Spia { void suMusica(); }

    // ---- l'unica istanza ----------------------------------------------------

    private static Musica unica;

    public static synchronized Musica di(Context c) {
        if (unica == null) unica = new Musica(c.getApplicationContext());
        return unica;
    }

    private final Context contesto;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler lavoro;

    private final File casaSua;      // config_dir: config.yml, credentials.json, state.json
    private final File tubo;         // la FIFO dell'audio
    private final TuboAudio audio;

    /** La sessione audio di quello che sta uscendo, per le colonne del suono
     *  della Home. Zero quando non c'e' niente da agganciare. */
    public int sessioneAudio() { return audio != null ? audio.sessioneAudio() : 0; }

    private Process demone;
    private Thread guardiano;
    private MediaSession sessione;
    private Spia spia;
    private Radio radio;

    private volatile int stato = SPENTA;
    private volatile String codice, indirizzoCodice, errore;

    /** Quello che suona adesso. Scritto dal thread di lavoro, letto dal
     *  disegno: volatile, che qui basta - sono valori indipendenti e una
     *  schermata mezza aggiornata dura sedici millisecondi. */
    private volatile String brano, artista, album, copertina, uri;
    private volatile String utente;

    /** L'ultima cosa che abbiamo chiesto di suonare noi - una playlist, un
     *  album. Lo stato del demone non lo dice (dice il brano, non da dove
     *  viene), e serve solo a una cosa: accendere la tessera giusta nella
     *  griglia. Chi manda musica dal telefono non passa di qui, e infatti in
     *  quel caso nessuna tessera si accende, che e' la verita'. */
    private volatile String daDove;

    /** L'ultima cosa suonata, come sta scritta sul disco. Si tiene qui per non
     *  rileggere il file a ogni giro dello stato: serve solo a sapere se
     *  quello che sta suonando adesso e' gia' quello scritto. */
    private volatile Ultimo ultimo;
    private volatile boolean suona, fermo = true;
    private volatile long durataMs, posizioneMs, misurataA;
    private volatile boolean mischia;

    private Musica(Context c) {
        contesto = c;
        casaSua = new File(c.getFilesDir(), "librespot");
        tubo = new File(casaSua, "audio.pipe");
        audio = new TuboAudio(tubo);

        HandlerThread t = new HandlerThread("Casa-musica");
        t.start();
        lavoro = new Handler(t.getLooper());
    }

    public void setSpia(Spia s) { spia = s; }
    public void staccati(Spia s) { if (spia == s) spia = null; }

    /** La radio e la musica escono dallo stesso altoparlante: quando parte una,
     *  l'altra si ferma. Senza, suonerebbero insieme. */
    public void setRadio(Radio r) { radio = r; }

    // ---- accensione ---------------------------------------------------------

    /**
     * Accende il demone, se non c'e' gia'.
     *
     * <b>Prima si bussa.</b> Il processo figlio sopravvive al processo padre:
     * se Android ha ucciso Casa mentre la musica suonava, il demone e' ancora
     * li' con la sua porta e la sua FIFO, e lanciarne un secondo darebbe due
     * demoni e un "address already in use". Se l'API risponde ci si riattacca a
     * quello che c'e' - basta rimettere in ascolto il tubo.
     */
    public void avvia() {
        if (vetrina) return;
        if (stato == AVVIO || stato == ACCOPPIA || stato == PRONTA) return;
        cambia(AVVIO, null);
        lavoro.post(new Runnable() {
            @Override public void run() { avviaDavvero(); }
        });
    }

    private void avviaDavvero() {
        File binario = new File(contesto.getApplicationInfo().nativeLibraryDir, "libgolibrespot.so");
        if (!binario.exists()) {
            cambia(GUASTO, "il motore non e' nell'app");
            Log.w(TAG, "manca " + binario);
            return;
        }

        if (!casaSua.exists() && !casaSua.mkdirs()) {
            cambia(GUASTO, "non riesco a scrivere la configurazione");
            return;
        }

        boolean giaVivo = chiedi("GET", "/", null) != null;
        if (!giaVivo) {
            scriviConfigurazione();
            if (!accendiTubo()) return;
            if (!lancia(binario)) return;
        } else {
            Log.i(TAG, "un demone era gia' vivo: mi riattacco");
            if (!accendiTubo()) return;
        }
        giraIlControllo(GIRO_AVVIO);
    }

    private boolean accendiTubo() {
        audio.avvia();
        if (!audio.acceso()) {
            cambia(GUASTO, "l'uscita audio non si apre");
            return false;
        }
        return true;
    }

    private boolean lancia(File binario) {
        try {
            ProcessBuilder p = new ProcessBuilder(
                    binario.getAbsolutePath(), "--config_dir", casaSua.getAbsolutePath());
            p.redirectErrorStream(true);
            p.directory(casaSua);
            // Il demone calcola il valore di fabbrica di --config_dir con
            // os.UserConfigDir() PRIMA di guardare l'opzione, e quella funzione
            // fallisce se non ci sono ne' HOME ne' XDG_CONFIG_HOME. Nel processo
            // di un'app Android non c'e' ne' l'uno ne' l'altro: senza questa
            // riga muore all'avvio per un valore che poi non userebbe.
            p.environment().put("HOME", casaSua.getAbsolutePath());
            demone = p.start();
        } catch (Exception e) {
            cambia(GUASTO, "il motore non parte");
            Log.w(TAG, "processo non avviato", e);
            return false;
        }

        // Qualcuno deve svuotare l'uscita del processo sempre: il buffer della
        // pipe e' 64 KB, e quando e' pieno il demone si ferma a scrivere.
        guardiano = new Thread(new Runnable() {
            @Override public void run() { ascoltaIlDemone(); }
        }, "Casa-musica-log");
        guardiano.start();
        return true;
    }

    /**
     * Legge quello che il demone racconta.
     *
     * Serve a tre cose sole, e tutte e tre valgono la lettura: il codice da
     * mostrare per l'accoppiamento, il motivo quando l'ingresso non riesce, e
     * accorgersi che il processo e' morto.
     */
    private void ascoltaIlDemone() {
        // Il codice non si prende con \S+: logrus mette fra virgolette tutto il
        // messaggio quando contiene spazi, e il codice e' l'ultima parola della
        // riga - quindi si portava dietro la virgoletta di chiusura, e a
        // schermo compariva « ABCD-EFGH" ». Si accettano solo i caratteri di
        // cui un codice e' fatto, e la virgoletta resta fuori.
        Pattern conCodice = Pattern.compile(
                "visit (\\S+) and, if prompted, enter code ([A-Za-z0-9._-]+)");
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(demone.getInputStream(), "UTF-8"), 4096);
            String riga;
            while ((riga = r.readLine()) != null) {
                Matcher m = conCodice.matcher(riga);
                if (m.find()) {
                    indirizzoCodice = m.group(1);
                    codice = m.group(2);
                    cambia(ACCOPPIA, null);
                    continue;
                }
                if (riga.contains("PremiumAccountRequired")) {
                    cambia(GUASTO, "questo account non e' Premium");
                    continue;
                }
                if (riga.contains("level=fatal") || riga.contains("login failed")) {
                    Log.w(TAG, riga);
                    cambia(GUASTO, "l'ingresso a Spotify non riesce");
                    continue;
                }
                if (riga.contains("level=error")) Log.w(TAG, riga);
                else Log.i(TAG, riga);
            }
        } catch (Exception e) {
            Log.i(TAG, "fine del racconto del demone");
        } finally {
            chiudiPiano(r);
        }

        // Se si arriva qui il processo ha chiuso la sua uscita, cioe' e' morto.
        if (stato != SPENTA) {
            cambia(GUASTO, "il motore si e' fermato");
            audio.ferma();
        }
    }

    /**
     * La configurazione del demone.
     *
     * Si riscrive a ogni avvio di proposito: e' generata, non modificata a
     * mano, e cosi' un cambio di percorso o di interfaccia di rete entra in
     * vigore senza che nessuno debba ricordarsi di cancellare un file.
     * {@code credentials.json} e {@code state.json}, che invece contengono
     * quello che non si puo' rigenerare, stanno accanto e non si toccano.
     */
    private void scriviConfigurazione() {
        Rete rete = interfacciaAttiva();
        StringBuilder s = new StringBuilder(1024);
        s.append("log_level: \"info\"\n");
        s.append("log_disable_timestamp: true\n");
        s.append("device_name: ").append(virgolette(NOME_APPARECCHIO)).append('\n');
        s.append("device_type: \"speaker\"\n");
        s.append("bitrate: ").append(QUALITA).append('\n');
        // 4070 e' la porta di casa dell'access point, e su parecchie reti
        // domestiche e' chiusa in uscita. Provare prima 443 e 80 non costa
        // niente dove invece e' aperta.
        s.append("prefer_firewall_friendly_ports: true\n");

        s.append("audio_backend: \"pipe\"\n");
        s.append("audio_output_pipe: ").append(virgolette(tubo.getAbsolutePath())).append('\n');
        s.append("audio_output_pipe_format: \"s16le\"\n");
        // Il demone aspetta che dall'altra parte ci sia qualcuno prima di
        // scrivere: senza, la prima scrittura fallirebbe e la sessione
        // resterebbe muta.
        s.append("audio_output_pipe_wait_for_reader: true\n");
        // Il volume lo fa l'AudioTrack (vedi TuboAudio): al demone si lascia il
        // massimo, cosi' i campioni arrivano interi e l'attenuazione e' una
        // sola invece di due in fila.
        s.append("volume_steps: 100\n");
        s.append("initial_volume: 100\n");
        s.append("ignore_last_volume: false\n");
        // Risponde subito al comando invece di aspettare la conferma
        // dell'uscita audio: su questo hardware sono due decimi di differenza
        // fra il dito e il silenzio.
        s.append("optimistic_playback_replies: true\n");
        s.append("disable_autoplay: false\n");

        s.append("zeroconf_enabled: ").append(rete != null).append('\n');
        s.append("zeroconf_backend: \"builtin\"\n");
        s.append("zeroconf_port: 0\n");
        if (rete != null) {
            s.append("android_net_iface_name: ").append(virgolette(rete.nome)).append('\n');
            s.append("android_net_iface_index: ").append(rete.indice).append('\n');
            s.append("android_net_ip: ").append(virgolette(rete.ip)).append('\n');
            s.append("android_net_prefix_len: ").append(rete.prefisso).append('\n');
        }

        s.append("credentials:\n");
        s.append("  type: \"device_auth\"\n");
        s.append("  zeroconf:\n");
        s.append("    persist_credentials: true\n");

        s.append("server:\n");
        s.append("  enabled: true\n");
        s.append("  address: \"127.0.0.1\"\n");
        s.append("  port: 24879\n");

        s.append("cache:\n");
        s.append("  enabled: true\n");
        s.append("  dir: ").append(virgolette(new File(contesto.getCacheDir(), "musica").getAbsolutePath())).append('\n');
        // Un tetto basso: 11 GB liberi ci sono, ma una cache che cresce per
        // sempre su un apparecchio che non si guarda mai e' un problema che si
        // scopre tardi.
        s.append("  size_limit: \"128MB\"\n");

        File f = new File(casaSua, "config.yml");
        OutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(s.toString().getBytes("UTF-8"));
            out.flush();
        } catch (Exception e) {
            Log.w(TAG, "configurazione non scritta", e);
        } finally {
            chiudiPiano(out);
        }
    }

    private static String virgolette(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Nome, indice, indirizzo e prefisso dell'interfaccia con cui il tablet sta
     *  in rete adesso. */
    private static final class Rete {
        final String nome, ip; final int indice, prefisso;
        Rete(String n, int i, String a, int p) { nome = n; indice = i; ip = a; prefisso = p; }
    }

    /**
     * Su che interfaccia siamo, chiedendolo a chi lo sa.
     *
     * Il demone non puo' scoprirlo da solo: {@code net.Interfaces()} in Go
     * passa da netlink, e SELinux nega a un'app non di sistema perfino di
     * aprire quel socket; leggere {@code /sys/class/net} o {@code /proc/net} e'
     * negato allo stesso modo. Dall'app invece si chiede a
     * {@link ConnectivityManager}, che risponde passando dal system server
     * invece che dal kernel - e quello funziona.
     *
     * Se non si sa, si torna null e lo zeroconf resta spento: Casa non comparira'
     * nell'elenco Connect del telefono, ma tutto il resto - ingresso col codice,
     * riproduzione, comandi - funziona uguale.
     */
    private Rete interfacciaAttiva() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    contesto.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;
            Network rete = cm.getActiveNetwork();
            if (rete == null) return null;
            LinkProperties lp = cm.getLinkProperties(rete);
            if (lp == null) return null;

            String nome = lp.getInterfaceName();
            if (nome == null) return null;

            String ip = null;
            int prefisso = 24;
            List<LinkAddress> indirizzi = lp.getLinkAddresses();
            if (indirizzi != null) {
                for (LinkAddress a : indirizzi) {
                    InetAddress i = a.getAddress();
                    if (i instanceof Inet4Address && !i.isLoopbackAddress()) {
                        ip = i.getHostAddress();
                        prefisso = a.getPrefixLength();
                        break;
                    }
                }
            }
            if (ip == null) return null;

            NetworkInterface ni = NetworkInterface.getByName(nome);
            if (ni == null) return null;
            return new Rete(nome, ni.getIndex(), ip, prefisso);
        } catch (Throwable t) {
            Log.i(TAG, "interfaccia di rete non risolta: zeroconf spento");
            return null;
        }
    }

    // ---- il giro di controllo ----------------------------------------------

    private final Runnable controllo = new Runnable() {
        @Override public void run() { unGiro(); }
    };

    private void giraIlControllo(long fra) {
        lavoro.removeCallbacks(controllo);
        lavoro.postDelayed(controllo, fra);
    }

    private void unGiro() {
        if (vetrina || stato == SPENTA || stato == GUASTO) return;

        String radice = chiedi("GET", "/", null);
        if (radice == null) {
            // L'API non risponde ancora: durante l'avvio e' normale, il demone
            // deve ancora risolvere l'access point.
            giraIlControllo(GIRO_AVVIO);
            return;
        }
        boolean pronta = radice.contains("\"playback_ready\":true");
        if (!pronta) {
            // Pronto a rispondere ma non a suonare: o sta ancora entrando, o
            // aspetta che qualcuno approvi il codice.
            if (stato != ACCOPPIA) cambia(AVVIO, null);
            giraIlControllo(GIRO_AVVIO);
            return;
        }

        if (stato != PRONTA) { codice = null; cambia(PRONTA, null); }
        leggiStato();
        giraIlControllo(suona ? GIRO_SUONA : GIRO_FERMO);
    }

    /** Rilegge cosa suona e avvisa solo se e' cambiato qualcosa: ridisegnare a
     *  vuoto ogni due secondi su questo hardware si vede. */
    private void leggiStato() {
        String risposta = chiedi("GET", "/status", null);
        if (risposta == null || risposta.length() == 0) { svuota(); return; }
        try {
            JSONObject s = new JSONObject(risposta);
            String chi = s.optString("username", null);
            if (chi != null && chi.length() > 0) utente = chi;
            boolean nuovoFermo = s.optBoolean("stopped", true);
            boolean nuovoSuona = !nuovoFermo && !s.optBoolean("paused", true);
            boolean nuovoMischia = s.optBoolean("shuffle_context", false);

            String t = null, a = null, al = null, cop = null, u = null;
            long dur = 0, pos = 0;
            JSONObject b = s.optJSONObject("track");
            if (b != null) {
                u = b.optString("uri", null);
                t = b.optString("name", null);
                al = b.optString("album_name", null);
                cop = b.isNull("album_cover_url") ? null : b.optString("album_cover_url", null);
                dur = b.optLong("duration", 0);
                pos = b.optLong("position", 0);
                JSONArray artisti = b.optJSONArray("artist_names");
                if (artisti != null && artisti.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < artisti.length(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(artisti.optString(i));
                    }
                    a = sb.toString();
                }
            }

            suona = nuovoSuona; fermo = nuovoFermo; mischia = nuovoMischia;
            uri = u; brano = t; artista = a; album = al; copertina = cop;
            durataMs = dur; posizioneMs = pos; misurataA = SystemClock.elapsedRealtime();

            // Da qui in poi il tasto play della Home sa dove ripartire domani
            // mattina. Solo se sta suonando davvero: un brano in pausa da tre
            // giorni nella sessione del demone non e' "l'ultima cosa
            // ascoltata", e' un residuo.
            //
            // Il confronto con quello gia' scritto non e' un'ottimizzazione da
            // niente: questo giro si ripete due volte al secondo, e senza
            // sarebbero centinaia di scritture sul disco per una sola canzone.
            if (nuovoSuona && u != null && t != null) {
                if (ultimo == null) ultimo = Ultimo.leggi(contesto);
                if (!ultimo.loStesso(Ultimo.MUSICA, u)) {
                    ultimo = Ultimo.musica(contesto, daDove, u, t, a);
                }
            }

            // Si avvisa a ogni giro anche quando il brano e' lo stesso: la
            // barra della posizione scorre, ed e' comunque un ridisegno ogni
            // due secondi, non a ogni fotogramma.
            avvisa();
        } catch (Exception e) {
            Log.w(TAG, "stato illeggibile", e);
        }
        aggiornaSessione();
    }

    private void svuota() {
        if (brano == null && fermo) return;
        brano = artista = album = copertina = uri = daDove = null;
        suona = false; fermo = true; durataMs = posizioneMs = 0;
        avvisa();
        aggiornaSessione();
    }

    // ---- i comandi ----------------------------------------------------------

    /** Manda in riproduzione un URI di Spotify: una playlist, un album, un brano. */
    public void suona(String spotifyUri) {
        suona(spotifyUri, null);
    }

    /**
     * La stessa cosa, ma partendo da un brano preciso.
     *
     * <b>Il contesto resta la playlist</b>, e non e' un dettaglio: mandando il
     * solo brano, finito quello finirebbe la musica. Con
     * {@code skip_to_uri} si sceglie da dove cominciare e si continua con tutto
     * il resto dietro, che e' quello che si aspetta chi tocca la terza riga di
     * un elenco.
     */
    public void suona(final String spotifyUri, final String brano) {
        if (spotifyUri == null) return;
        if (radio != null) radio.spegni();
        daDove = spotifyUri;
        String corpo = "{\"uri\":" + JSONObject.quote(spotifyUri)
                + (brano != null ? ",\"skip_to_uri\":" + JSONObject.quote(brano) : "")
                + "}";
        manda("/player/play", corpo);
    }

    public void pausaRiprendi() {
        if (vetrina) {
            posizioneMs = posizione();
            misurataA = SystemClock.elapsedRealtime();
            suona = !suona;
            avvisa();
            return;
        }
        if (suona) { manda("/player/pause", "{}"); }
        else { if (radio != null) radio.spegni(); manda("/player/resume", "{}"); }
        // Si presume la risposta e si ridisegna subito: il ritorno del demone
        // arriva qualche decimo dopo, e un pulsante che non reagisce sembra
        // rotto anche quando ha funzionato.
        suona = !suona;
        avvisa();
    }

    public void successivo()  { manda("/player/next", "{}"); }
    public void precedente()  { manda("/player/prev", "{}"); }

    public void mischia(boolean si) {
        mischia = si;
        manda("/player/shuffle_context", "{\"shuffle_context\":" + si + "}");
        avvisa();
    }

    public void vaiA(long millisecondi) {
        posizioneMs = millisecondi;
        misurataA = SystemClock.elapsedRealtime();
        manda("/player/seek", "{\"position\":" + millisecondi + "}");
        avvisa();
    }

    /** Ferma e chiude la sessione, ma lascia il demone in piedi: riaprirlo
     *  costerebbe l'intero giro di collegamento a Spotify. */
    public void ferma() {
        if (stato != PRONTA) return;
        manda("/player/pause", "{}");
        suona = false;
        avvisa();
    }

    private void manda(final String percorso, final String corpo) {
        if (stato != PRONTA) return;
        lavoro.post(new Runnable() {
            @Override public void run() {
                chiedi("POST", percorso, corpo);
                giraIlControllo(250);
            }
        });
    }

    // ---- il gettone per la Web API ------------------------------------------

    public interface Gettone { void arrivato(String token); }

    /**
     * Un token di accesso della sessione in corso.
     *
     * E' la porta grande che l'app ufficiale non ha mai dato: con questo si
     * chiede alla Web API di Spotify l'elenco delle proprie playlist senza
     * registrare un'applicazione e senza il suo limite di cinque utenti. Vedi
     * {@link Preferiti}.
     */
    public void gettone(final Gettone chi) {
        lavoro.post(new Runnable() {
            @Override public void run() {
                String r = chiedi("POST", "/token", "{}");
                String t = null;
                if (r != null && r.length() > 0) {
                    try { t = new JSONObject(r).optString("token", null); }
                    catch (Exception e) { Log.w(TAG, "token illeggibile", e); }
                }
                final String finale = t;
                ui.post(new Runnable() {
                    @Override public void run() { chi.arrivato(finale); }
                });
            }
        });
    }

    // ---- HTTP sul loopback --------------------------------------------------

    /**
     * Una chiamata all'API del demone. Torna il corpo, oppure null.
     *
     * Sempre dal thread di lavoro: e' loopback e costa un millisecondo, ma un
     * millisecondo sul thread del disegno e' comunque un fotogramma perso, e
     * quando il demone e' morto sarebbero due secondi di timeout.
     */
    private String chiedi(String metodo, String percorso, String corpo) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(INDIRIZZO_API + percorso).openConnection();
            c.setRequestMethod(metodo);
            c.setConnectTimeout(1500);
            c.setReadTimeout(4000);
            c.setUseCaches(false);
            if (corpo != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                byte[] b = corpo.getBytes("UTF-8");
                c.setFixedLengthStreamingMode(b.length);
                OutputStream o = c.getOutputStream();
                o.write(b);
                o.flush();
                o.close();
            }
            int codice = c.getResponseCode();
            if (codice == 204) return "";
            if (codice < 200 || codice >= 300) return null;
            return tutto(c.getInputStream());
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String tutto(InputStream in) throws Exception {
        if (in == null) return "";
        try {
            byte[] pezzo = new byte[4096];
            StringBuilder s = new StringBuilder();
            int n;
            while ((n = in.read(pezzo)) > 0) s.append(new String(pezzo, 0, n, "UTF-8"));
            return s.toString();
        } finally {
            in.close();
        }
    }

    // ---- la sessione media --------------------------------------------------

    /**
     * Pubblica la sessione, e da li' vengono i tasti delle cuffie e il
     * telecomando di sistema, con lo stesso codice con cui li ha la radio.
     */
    public void pubblica(Context c) {
        if (sessione != null) return;
        try {
            sessione = new MediaSession(c, "Casa.Musica");
            sessione.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                            | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            sessione.setCallback(new MediaSession.Callback() {
                @Override public void onPlay()  { if (!suona) pausaRiprendi(); }
                @Override public void onPause() { if (suona) pausaRiprendi(); }
                @Override public void onStop()  { ferma(); }
                @Override public void onSkipToNext()     { successivo(); }
                @Override public void onSkipToPrevious() { precedente(); }
                @Override public void onSeekTo(long dove) { vaiA(dove); }
            });
        } catch (Exception e) {
            Log.w(TAG, "sessione media non pubblicata", e);
            sessione = null;
        }
    }

    private void aggiornaSessione() {
        if (sessione == null) return;
        try {
            boolean qualcosa = brano != null;
            sessione.setActive(qualcosa && !fermo);
            if (qualcosa) {
                sessione.setMetadata(new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, brano)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST,
                                   artista != null ? artista : "Spotify")
                        .putString(MediaMetadata.METADATA_KEY_ALBUM, album != null ? album : "")
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, durataMs)
                        .build());
            }
            sessione.setPlaybackState(new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                              | PlaybackState.ACTION_SKIP_TO_NEXT
                              | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                              | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                    .setState(suona ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                              posizione(), 1f)
                    .build());
        } catch (Exception e) {
            Log.w(TAG, "sessione non aggiornata", e);
        }
    }

    // ---- la sordina ---------------------------------------------------------

    @Override public void abbassaVolume(float quanto) { audio.volume(quanto); }
    @Override public void rialzaVolume() { audio.volume(1f); }

    // ---- quello che la schermata chiede -------------------------------------

    public int stato() { return stato; }

    /**
     * L'accesso e' gia' stato fatto una volta?
     *
     * Serve a non spaventare nessuno all'avvio: chi ha gia' approvato dal
     * telefono non deve rileggere la spiegazione dell'accoppiamento ogni volta
     * che apre la sezione - per lui quei secondi sono solo un caricamento. Il
     * segno e' il file delle credenziali, che il demone scrive dopo il primo
     * ingresso riuscito e da li' in poi si rilegge da solo.
     */
    public boolean giaEntrati() {
        return new File(casaSua, "credentials.json").exists();
    }

    /**
     * Dimentica chi era entrato: al prossimo avvio del demone si ricomincia dal
     * codice.
     *
     * Serve per cambiare account, ed e' l'unica cosa dell'accesso che si possa
     * fare da fuori - entrare no, quello si approva dal telefono di chi entra.
     * Il file sta nei dati privati dell'app, quindi da adb non si tocca: ci
     * deve pensare Casa.
     *
     * Si cancella soltanto credentials.json e non tutta la cartella: dentro ci
     * sono anche config.yml, che Casa riscrive a ogni avvio, e state.json, che
     * ricorda volume e coda. Portarseli via per cambiare account sarebbe come
     * spegnere il contatore per cambiare lampadina.
     */
    public boolean dimenticaAccesso() {
        File f = new File(casaSua, "credentials.json");
        if (!f.exists()) return false;
        boolean fatto = f.delete();
        Log.i(TAG, "accesso Spotify " + (fatto ? "dimenticato" : "NON dimenticato"));
        return fatto;
    }
    public String codice() { return codice; }
    public String indirizzoCodice() { return indirizzoCodice; }
    public String errore() { return errore; }

    public boolean staSuonando() { return suona; }
    public boolean ceUnBrano() { return brano != null; }
    public String brano() { return brano; }
    public String artista() { return artista; }
    public String album() { return album; }
    public String copertina() { return copertina; }
    public boolean mischiata() { return mischia; }

    /** Chi ha fatto l'accesso. Serve a {@link Preferiti} per chiedere le sue
     *  playlist, e si sa solo dopo il primo /status. */
    public String utente() { return utente; }

    /** Da dove viene quel che suona, se l'abbiamo messo noi. */
    public String contesto() { return daDove; }
    public long durata() { return durataMs; }

    /**
     * Dove siamo nel brano.
     *
     * Fra una lettura e l'altra passano due secondi, e una barra che scatta
     * ogni due secondi si vede: qui il tempo trascorso si somma da soli.
     * elapsedRealtime e non currentTimeMillis, che qualcuno puo' spostare -
     * cambiare l'ora del tablet non deve far saltare la barra a fine brano.
     */
    public long posizione() {
        long p = posizioneMs;
        if (suona) p += SystemClock.elapsedRealtime() - misurataA;
        return durataMs > 0 ? Math.min(p, durataMs) : p;
    }

    // ---- la vetrina -----------------------------------------------------------

    /** Un brano finto al posto del demone: vedi {@link #vetrina}. */
    private volatile boolean vetrina;

    /**
     * Un brano finto, per fotografare la sezione dove il demone non gira.
     *
     * Sull'emulatore go-librespot non c'e' (l'immagine e' x86, il binario e'
     * ARM) e senza la sezione resta ferma su « il motore non e' nell'app ». Le
     * catture per il blog vogliono quel che si vede quando suona: qui si mette
     * lo stato a mano, e da li' in poi il demone non viene piu' cercato. La
     * chiama solo MainActivity, e solo su un emulatore.
     */
    public void vetrina(String brano, String artista, String album, String copertina,
                        long durata, long posizione) {
        vetrina = true;
        this.brano = brano;
        this.artista = artista;
        this.album = album;
        this.copertina = copertina;
        utente = "vetrina";
        durataMs = durata;
        posizioneMs = posizione;
        misurataA = SystemClock.elapsedRealtime();
        suona = true;
        fermo = false;
        codice = null;
        cambia(PRONTA, null);
        avvisa();
    }

    // ---- chiusura -----------------------------------------------------------

    /**
     * Spegne tutto, demone compreso.
     *
     * Non si chiama alla fine dell'Activity: la musica non appartiene a una
     * finestra. Si chiama quando qualcuno spegne la musica per davvero.
     */
    public void spegni() {
        lavoro.removeCallbacks(controllo);
        cambia(SPENTA, null);
        svuota();
        if (sessione != null) {
            try { sessione.setActive(false); sessione.release(); } catch (Exception ignorata) { }
            sessione = null;
        }
        audio.ferma();
        Process p = demone;
        demone = null;
        if (p != null) {
            try { p.destroy(); } catch (Exception ignorata) { }
        }
    }

    private void cambia(int nuovo, String perche) {
        String nuovoErrore = (nuovo == GUASTO) ? perche : null;
        if (stato == nuovo && uguali(nuovoErrore, errore)) return;
        stato = nuovo;
        errore = nuovoErrore;
        avvisa();
    }

    private void avvisa() {
        final Spia s = spia;
        if (s == null) return;
        ui.post(new Runnable() {
            @Override public void run() { s.suMusica(); }
        });
    }

    private static boolean uguali(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static void chiudiPiano(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignorata) { }
    }
}
