package dev.casa;

import android.content.Context;
import android.media.AudioManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Il telefono che parla con Casa: l'app {@code dev.casa.telefono}, cartella
 * {@code telefono/}.
 *
 * <h3>Perche' qui il tablet fa da server</h3>
 *
 * Col PC e' il contrario - il PC ascolta sulla 8775 e il tablet ci arriva con
 * {@code adb reverse} - ma quello e' un cavo. Il telefono sta sul Wi-Fi e va e
 * viene: l'unico che c'e' sempre, allo stesso indirizzo, e' il tablet appeso al
 * muro. Quindi ascolta lui, sulla <b>8776</b>: la 8775 la tiene gia' adbd
 * quando il PC e' collegato, e le due non possono stare sulla stessa porta.
 *
 * Il telefono lo trova senza chiedere indirizzi a nessuno: Casa si annuncia in
 * NSD come {@code _casa._tcp}.
 *
 * <h3>Perche' HTTP scritto a mano</h3>
 *
 * Una richiesta per connessione, un corpo JSON, niente di piu'. Una libreria
 * da server sono megabyte su un tablet da 1 GB per leggere tre intestazioni;
 * cosi' e' un thread fermo in {@code accept()} e si prova con {@code curl}.
 *
 * <h3>Chi puo' parlare</h3>
 *
 * Solo un telefono abbinato. Abbinare vuol dire leggere un codice di sei cifre
 * <b>sullo schermo del tablet</b>: chi e' sulla rete di casa ma non in casa non
 * lo vede, e dopo cinque tentativi sbagliati il codice si brucia. Il telefono
 * riceve una chiave sua, e da li' ogni richiesta porta una firma HMAC-SHA256
 * di metodo, percorso, ora e corpo.
 *
 * La chiave <b>non viaggia mai</b> dopo l'abbinamento, e la firma vale una
 * volta sola e per cinque minuti: chi registra una richiesta sul Wi-Fi non puo'
 * rimandarla per riaccendere le luci alle tre di notte.
 *
 * Il protocollo per esteso sta in docs/telefono.md.
 */
public final class Telefono {

    private static final String TAG = MainActivity.TAG;

    public static final int PORTA = 8776;
    private static final String TIPO = "_casa._tcp";
    private static final String FILE = "telefoni.json";

    /** Quanto resta valido un codice di abbinamento, e quanti errori regge. */
    private static final long CODICE_MS = 3 * 60000L;
    private static final int ERRORI_MAX = 5;

    /** Quanto possono essere lontani gli orologi, e quanto si ricorda una
     *  firma per non accettarla due volte. Le due cose vanno insieme: una
     *  firma piu' vecchia della tolleranza e' rifiutata comunque. */
    private static final long TOLLERANZA_MS = 5 * 60000L;

    /** Quanti telefoni si ricordano: il piu' vecchio lascia il posto. */
    private static final int TELEFONI_MAX = 8;

    private static final int INTESTAZIONI_MAX = 8 * 1024;
    private static final int CORPO_MAX = 16 * 1024;

    /** Quanto il thread della richiesta aspetta l'interfaccia. Lampade e
     *  sveglie vivono li', e li' si toccano. */
    private static final long ATTESA_UI_MS = 3000;

    /** Quello che solo la regia sa fare. Tutto sul thread dell'interfaccia. */
    public interface Regia {
        void frase(String testo);
        boolean radioAccesa();
        String nomeRadio();
        /** Mostra il codice sullo schermo; null lo toglie. */
        void codice(String codice, String nome);
        void abbinato(String nome);
        /** Chi suona. Possono essere null finche' MainActivity non li ha
         *  collegati: nei primi secondi dopo l'avvio succede. */
        Radio radio();
        Musica musica();
        Preferiti preferiti();
        Cerca ricerca();
    }

    private final Context contesto;
    private final Regia regia;
    private final Luci luci;
    private final Orologio orologio;
    private final Appunti appunti;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** Due richieste insieme bastano: il telefono ne manda una alla volta, e
     *  la seconda corsia c'e' perche' una connessione lenta non tenga fuori
     *  quella dopo. */
    private final ExecutorService lavoro = Executors.newFixedThreadPool(2);
    private final SecureRandom caso = new SecureRandom();

    private volatile boolean acceso;
    private ServerSocket server;
    private NsdManager nsd;
    private NsdManager.RegistrationListener annuncio;

    /** id -> {nome, chiave, dal}. Sotto il suo lucchetto. */
    private final JSONObject telefoni;

    /** Le firme gia' viste, col momento: in ordine d'arrivo, per potare dalla
     *  testa. */
    private final LinkedHashMap<String, Long> firmeViste = new LinkedHashMap<String, Long>();

    // L'abbinamento in corso: uno solo per volta, sotto "this".
    private String codice, nomeInAttesa;
    private long codiceFinoA;
    private int errori;

    private final Runnable codiceScaduto = new Runnable() {
        @Override public void run() {
            synchronized (Telefono.this) {
                if (codice == null || System.currentTimeMillis() < codiceFinoA) return;
                codice = null;
            }
            regia.codice(null, null);
        }
    };

    public Telefono(Context c, Regia regia, Luci luci, Orologio orologio, Appunti appunti) {
        this.contesto = c.getApplicationContext();
        this.regia = regia;
        this.luci = luci;
        this.orologio = orologio;
        this.appunti = appunti;
        JSONObject salvati = Archivio.leggi(contesto, FILE);
        JSONObject t = salvati != null ? salvati.optJSONObject("telefoni") : null;
        telefoni = t != null ? t : new JSONObject();
    }

    // ---- accensione --------------------------------------------------------

    public void avvia() {
        if (acceso) return;
        acceso = true;
        Thread porta = new Thread(new Runnable() {
            @Override public void run() { accogli(); }
        }, "Casa-telefono");
        porta.setDaemon(true);
        porta.start();
    }

    private void accogli() {
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(PORTA));
            server = s;
        } catch (Exception e) {
            Log.w(TAG, "telefono: non ascolto sulla " + PORTA + ": " + e.getMessage());
            acceso = false;
            return;
        }
        Log.i(TAG, "telefono: in ascolto sulla " + PORTA + ", " + telefoni.length() + " abbinati");
        annunciati();
        while (acceso) {
            try {
                final Socket chi = server.accept();
                lavoro.execute(new Runnable() {
                    @Override public void run() { servi(chi); }
                });
            } catch (Exception e) {
                if (acceso) Log.w(TAG, "telefono: accept: " + e.getMessage());
            }
        }
    }

    /** Casa si fa trovare in rete col suo nome. Se l'NSD di questo ROM non
     *  risponde, si va avanti lo stesso: il telefono accetta anche un
     *  indirizzo scritto a mano. */
    private void annunciati() {
        try {
            nsd = (NsdManager) contesto.getSystemService(Context.NSD_SERVICE);
            if (nsd == null) return;
            NsdServiceInfo chi = new NsdServiceInfo();
            chi.setServiceName("Casa");
            chi.setServiceType(TIPO);
            chi.setPort(PORTA);
            annuncio = new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo i) {
                    Log.i(TAG, "telefono: annunciata in rete come " + i.getServiceName());
                }
                @Override public void onRegistrationFailed(NsdServiceInfo i, int errore) {
                    Log.w(TAG, "telefono: annuncio in rete fallito (" + errore + ")");
                }
                @Override public void onServiceUnregistered(NsdServiceInfo i) { }
                @Override public void onUnregistrationFailed(NsdServiceInfo i, int errore) { }
            };
            nsd.registerService(chi, NsdManager.PROTOCOL_DNS_SD, annuncio);
        } catch (Exception e) {
            Log.w(TAG, "telefono: niente annuncio in rete: " + e.getMessage());
        }
    }

    public void chiudi() {
        acceso = false;
        try { if (server != null) server.close(); } catch (Exception ignorato) { }
        try { if (nsd != null && annuncio != null) nsd.unregisterService(annuncio); } catch (Exception ignorato) { }
        ui.removeCallbacks(codiceScaduto);
        lavoro.shutdownNow();
    }

    // ---- una richiesta -----------------------------------------------------

    private static final class Richiesta {
        String metodo, percorso, corpo;
        final Map<String, String> intestazioni = new HashMap<String, String>();
        JSONObject json() {
            try {
                return corpo.length() > 0 ? new JSONObject(corpo) : new JSONObject();
            } catch (Exception storto) {
                return null;
            }
        }
    }

    private static final class Risposta {
        final int codice;
        final JSONObject corpo;
        Risposta(int codice, JSONObject corpo) { this.codice = codice; this.corpo = corpo; }
    }

    private void servi(Socket chi) {
        try {
            chi.setSoTimeout(5000);
            Richiesta r = leggi(chi.getInputStream());
            Risposta fuori = r == null ? errore(400, "richiesta illeggibile") : smista(r);
            scrivi(chi.getOutputStream(), fuori);
        } catch (Exception e) {
            Log.w(TAG, "telefono: richiesta andata storta: " + e.getMessage());
        } finally {
            try { chi.close(); } catch (Exception ignorato) { }
        }
    }

    private Risposta smista(Richiesta r) {
        String p = r.percorso;
        boolean post = "POST".equals(r.metodo);
        JSONObject j = r.json();
        if (j == null) return errore(400, "il corpo non e' JSON");

        if (post && "/abbina/chiedi".equals(p)) return chiediCodice(j);
        if (post && "/abbina".equals(p)) return abbina(j);

        final String telefono = verifica(r);
        if (telefono == null) return errore(401, "telefono non abbinato, o firma non valida");

        if ("GET".equals(r.metodo) && "/stato".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() throws Exception { return stato(); }
            });
        }
        if ("GET".equals(r.metodo) && "/radio".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() throws Exception { return statoRadio(); }
            });
        }
        if ("GET".equals(r.metodo) && p.startsWith("/radio/logo/")) {
            return logo(p.substring("/radio/logo/".length()));
        }
        if ("GET".equals(r.metodo) && "/musica".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() throws Exception { return statoMusica(); }
            });
        }
        if ("GET".equals(r.metodo) && "/musica/playlist".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() throws Exception { return playlist(); }
            });
        }
        if (!post) return errore(404, "non conosco " + r.metodo + " " + p);

        final JSONObject d = j;
        if ("/volume".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() throws Exception { return volume(d); }
            });
        }
        if (p.startsWith("/radio/")) {
            final String cosa = p.substring("/radio/".length());
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() { return radio(cosa, d); }
            });
        }
        if ("/musica/cerca".equals(p)) return cerca(d);
        if ("/musica/apri".equals(p)) return apriMusica(d);
        if ("/musica/brani".equals(p)) return brani(d);
        if (p.startsWith("/musica/")) {
            final String cosa = p.substring("/musica/".length());
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() { return musica(cosa, d); }
            });
        }
        if ("/timer/ferma".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() {
                    int id = d.optInt("id", -1);
                    for (Orologio.Conto c : orologio.conti()) {
                        if (c.id == id) { orologio.fermaTimer(c); return ok(); }
                    }
                    return no("questo timer non c'e' piu'");
                }
            });
        }
        if ("/frase".equals(p)) {
            final String testo = d.optString("testo", "").trim();
            if (testo.length() == 0) return errore(400, "manca il testo");
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() {
                    Log.i(TAG, "telefono: dice « " + testo + " »");
                    regia.frase(testo);
                    return ok();
                }
            });
        }
        if ("/luce".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() { return luce(d); }
            });
        }
        if ("/routine".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() {
                    String nome = d.optString("nome", "");
                    for (Routine x : luci.routine()) {
                        if (x.nome.equalsIgnoreCase(nome)) { luci.esegui(x); return ok(); }
                    }
                    return no("non c'e' una routine « " + nome + " »");
                }
            });
        }
        if ("/timer".equals(p)) {
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() {
                    long s = d.optLong("secondi", 0);
                    if (s <= 0 || s > 24 * 3600) return no("durata non valida");
                    orologio.avviaTimer(s);
                    return ok();
                }
            });
        }
        if (p.startsWith("/nota/")) {
            final String cosa = p.substring("/nota/".length());
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() throws Exception { return nota(cosa, d); }
            });
        }
        if (p.startsWith("/sveglia/")) {
            final String cosa = p.substring("/sveglia/".length());
            return sullaUi(new Lavoro() {
                @Override public JSONObject fai() throws Exception { return sveglia(cosa, d, telefono); }
            });
        }
        return errore(404, "non conosco " + p);
    }

    // ---- quello che si fa --------------------------------------------------

    private JSONObject stato() throws Exception {
        JSONObject o = ok();
        o.put("tablet", "Casa");
        o.put("ora", System.currentTimeMillis());
        String prossima = orologio.prossimaSveglia();
        o.put("prossimaSveglia", prossima != null ? prossima : JSONObject.NULL);

        JSONArray l = new JSONArray();
        for (Lampada x : luci.elenco()) {
            JSONObject a = new JSONObject();
            Tuya.Stato s = x.stato;
            a.put("id", x.id);
            a.put("nome", x.nome);
            a.put("colore", Tinte.scrivi(x.colore));
            a.put("letta", s != null);
            a.put("raggiunta", s != null && s.raggiunta);
            a.put("accesa", x.accesa());
            a.put("luminosita", s != null ? s.luminosita : -1);
            a.put("inCorso", x.inCorso);
            String g = x.guasto();
            a.put("guasto", g != null ? g : JSONObject.NULL);
            l.put(a);
        }
        o.put("luci", l);

        JSONArray r = new JSONArray();
        for (Routine x : luci.routine()) {
            JSONObject a = new JSONObject();
            a.put("nome", x.nome);
            a.put("colore", Tinte.scrivi(x.colore));
            r.put(a);
        }
        o.put("routine", r);

        JSONArray sv = new JSONArray();
        for (Orologio.Sveglia x : orologio.sveglie()) {
            JSONObject a = new JSONObject();
            a.put("id", x.id);
            a.put("ora", x.ora);
            a.put("minuto", x.minuto);
            a.put("giorni", x.giorni);
            a.put("attiva", x.attiva);
            a.put("telefono", x.dalTelefono());
            a.put("prossima", x.attiva ? orologio.prossima(x) : 0L);
            sv.put(a);
        }
        o.put("sveglie", sv);

        JSONArray t = new JSONArray();
        long adesso = System.currentTimeMillis();
        for (Orologio.Conto x : orologio.conti()) {
            JSONObject a = new JSONObject();
            a.put("id", x.id);
            a.put("restano", Math.max(0L, (x.scadenza - adesso) / 1000L));
            a.put("nome", x.nome());
            a.put("durata", x.durata);
            t.put(a);
        }
        o.put("timer", t);

        JSONObject radio = new JSONObject();
        radio.put("accesa", regia.radioAccesa());
        String nome = regia.nomeRadio();
        radio.put("nome", nome != null ? nome : "");
        Radio laRadio = regia.radio();
        Radio.Stazione corrente = laRadio != null ? laRadio.stazioneCorrente() : null;
        radio.put("chiave", corrente != null ? corrente.chiave : JSONObject.NULL);
        o.put("radio", radio);

        JSONObject musica = new JSONObject();
        Musica m = regia.musica();
        musica.put("pronta", m != null && m.stato() == Musica.PRONTA);
        musica.put("suona", m != null && m.staSuonando());
        musica.put("brano", m != null && m.brano() != null ? m.brano() : JSONObject.NULL);
        musica.put("artista", m != null && m.artista() != null ? m.artista() : JSONObject.NULL);
        musica.put("copertina", m != null ? immagine(m.copertina()) : JSONObject.NULL);
        o.put("musica", musica);
        o.put("volume", volume());

        JSONObject liste = new JSONObject();
        liste.put(Appunti.COSE, righe(Appunti.COSE));
        liste.put(Appunti.SPESA, righe(Appunti.SPESA));
        o.put("liste", liste);
        return o;
    }

    private JSONArray righe(String lista) throws Exception {
        JSONArray a = new JSONArray();
        if (appunti == null) return a;
        for (Appunti.Nota n : appunti.tutte(lista)) {
            JSONObject r = new JSONObject();
            r.put("id", n.id);
            r.put("testo", n.testo);
            r.put("fatta", n.fatta);
            a.put(r);
        }
        return a;
    }

    /**
     * Le liste dal telefono. Dal telefono si scrive una riga per volta, anche
     * nella spesa: sulla tastiera del telefono « sale e pepe » e' scritto
     * cosi' apposta, e dividerlo come si fa con una frase detta vorrebbe dire
     * correggere chi non ha sbagliato.
     */
    private JSONObject nota(String cosa, JSONObject d) throws Exception {
        if (appunti == null) return no("le liste non ci sono");
        if ("aggiungi".equals(cosa)) {
            String lista = Appunti.quale(d.optString("lista", Appunti.COSE));
            Appunti.Nota n = appunti.aggiungi(d.optString("testo", ""), false, lista);
            if (n == null) return no("manca il testo");
            JSONObject o = ok();
            o.put("id", n.id);
            return o;
        }
        if ("pulisci".equals(cosa)) {
            JSONObject o = ok();
            o.put("tolte", appunti.pulisciFatte(d.optString("lista", Appunti.COSE)));
            return o;
        }
        Appunti.Nota n = appunti.trova(d.optInt("id", -1));
        if (n == null) return no("questa riga non c'e' piu'");
        if ("inverti".equals(cosa)) { appunti.inverti(n); return ok(); }
        if ("togli".equals(cosa)) { appunti.togli(n); return ok(); }
        return no("non so fare « " + cosa + " » a una riga");
    }

    private JSONObject luce(JSONObject d) {
        String id = d.optString("id", "");
        Lampada quale = null;
        for (Lampada x : luci.elenco()) if (x.id.equals(id)) { quale = x; break; }
        if (quale == null) return no("non conosco questa lampada");
        String azione = d.optString("azione", "");
        if ("on".equals(azione)) luci.accendi(quale, true);
        else if ("off".equals(azione)) luci.accendi(quale, false);
        else if ("inverti".equals(azione)) luci.inverti(quale);
        else if ("luce".equals(azione)) luci.luminosita(quale, Math.max(1, Math.min(100, d.optInt("valore", 100))));
        else return no("azione « " + azione + " » sconosciuta");
        return ok();
    }

    // ---- il volume ---------------------------------------------------------

    /** Il volume in decine, come lo scrive la Home: 0, 10 ... 100. -1 se il
     *  sistema non lo dice. */
    private int volume() {
        AudioManager a = (AudioManager) contesto.getSystemService(Context.AUDIO_SERVICE);
        if (a == null) return -1;
        int massimo = a.getStreamMaxVolume(TuboAudio.flusso());
        if (massimo <= 0) return -1;
        return Math.round(a.getStreamVolume(TuboAudio.flusso()) * 10f / massimo) * 10;
    }

    /**
     * {@code {"passo": 1}} o {@code -1}: un decimo su o giu', <b>come i due
     * tasti della Home</b> e come « alza il volume » a voce. Tre comandi che
     * fanno la stessa cosa devono fare lo stesso salto.
     */
    private JSONObject volume(JSONObject d) throws Exception {
        AudioManager a = (AudioManager) contesto.getSystemService(Context.AUDIO_SERVICE);
        if (a == null) return no("il volume non si puo' toccare");
        int massimo = a.getStreamMaxVolume(TuboAudio.flusso());
        if (massimo <= 0) return no("il volume non si puo' toccare");
        int passo = d.optInt("passo", 0);
        if (passo == 0) return no("manca il passo");
        int decine = Math.round(a.getStreamVolume(TuboAudio.flusso()) * 10f / massimo) + (passo > 0 ? 1 : -1);
        decine = Math.max(0, Math.min(10, decine));
        try {
            a.setStreamVolume(TuboAudio.flusso(), Math.round(massimo * decine / 10f), 0);
        } catch (SecurityException vietato) {
            return no("adesso il sistema non lascia cambiare il volume");
        }
        JSONObject o = ok();
        o.put("volume", decine * 10);
        return o;
    }

    // ---- la radio ----------------------------------------------------------

    private JSONObject statoRadio() throws Exception {
        JSONObject o = ok();
        o.put("volume", volume());
        Radio radio = regia.radio();
        JSONArray elenco = new JSONArray();
        if (radio == null) {
            o.put("pronta", false);
            o.put("stazioni", elenco);
            return o;
        }
        o.put("pronta", true);
        o.put("accesa", radio.staSuonando());
        o.put("apertura", radio.staAprendo());
        Radio.Stazione corrente = radio.stazioneCorrente();
        o.put("corrente", corrente != null ? corrente.chiave : JSONObject.NULL);
        String guasto = radio.errore();
        o.put("errore", guasto != null ? guasto : JSONObject.NULL);
        for (Radio.Stazione s : radio.stazioni()) {
            JSONObject x = new JSONObject();
            x.put("chiave", s.chiave);
            x.put("nome", s.nome);
            x.put("sigla", s.sigla);
            x.put("colore", Tinte.scrivi(s.colore));
            x.put("chiaro", s.logoSuChiaro);
            x.put("logo", s.logo != 0);
            elenco.put(x);
        }
        o.put("stazioni", elenco);
        return o;
    }

    private JSONObject radio(String cosa, JSONObject d) {
        Radio radio = regia.radio();
        if (radio == null) return no("la radio non e' ancora pronta");
        if ("accendi".equals(cosa)) {
            String chiave = d.optString("chiave", "");
            Radio.Stazione s = chiave.length() > 0 ? radio.trova(chiave) : radio.stazioneCorrente();
            if (chiave.length() > 0 && s == null) return no("non conosco questa stazione");
            radio.accendi(s);
            return ok();
        }
        if ("spegni".equals(cosa)) { radio.spegni(); return ok(); }
        if ("successiva".equals(cosa)) { radio.successiva(); return ok(); }
        if ("precedente".equals(cosa)) { radio.precedente(); return ok(); }
        return no("non so fare « " + cosa + " » alla radio");
    }

    /**
     * Il logo di una stazione, <b>com'e' dentro l'APK</b>: il PNG, senza
     * decodificarlo. Decodificare e ricomprimere vorrebbe dire una bitmap in
     * piu' su un tablet da 1 GB per mandare gli stessi byte. Il telefono se lo
     * tiene in cache, quindi lo chiede una volta.
     */
    private Risposta logo(String chiaveScritta) {
        final String chiave;
        try {
            chiave = URLDecoder.decode(chiaveScritta, "UTF-8");
        } catch (Exception storta) {
            return errore(400, "chiave illeggibile");
        }
        Risposta trovata = sullaUi(new Lavoro() {
            @Override public JSONObject fai() throws Exception {
                Radio radio = regia.radio();
                Radio.Stazione s = radio != null ? radio.trova(chiave) : null;
                if (s == null || s.logo == 0) return no("questa stazione non ha un logo");
                JSONObject o = ok();
                o.put("risorsa", s.logo);
                return o;
            }
        });
        if (trovata.codice != 200) return trovata;
        InputStream in = null;
        try {
            in = contesto.getResources().openRawResource(trovata.corpo.optInt("risorsa"));
            ByteArrayOutputStream b = new ByteArrayOutputStream(16 * 1024);
            byte[] pezzo = new byte[4096];
            int n;
            while ((n = in.read(pezzo)) > 0) b.write(pezzo, 0, n);
            byte[] png = b.toByteArray();
            if (png.length < 8 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
                return errore(404, "questo logo non e' un PNG");
            }
            JSONObject o = ok();
            o.put("png", Base64.encodeToString(png, Base64.NO_WRAP));
            return new Risposta(200, o);
        } catch (Exception e) {
            return errore(500, "non riesco a leggere il logo");
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignorato) { }
        }
    }

    // ---- Spotify -----------------------------------------------------------

    /** Gli indirizzi delle immagini come li vuole un telefono: l'API interna
     *  di Spotify a volte scrive {@code spotify:image:...}, che fuori da
     *  Spotify non si apre. */
    private static Object immagine(String s) {
        if (s == null || s.length() == 0) return JSONObject.NULL;
        if (s.startsWith("spotify:image:")) return "https://i.scdn.co/image/" + s.substring("spotify:image:".length());
        return s;
    }

    private static Object testo(String s) {
        return s != null ? s : JSONObject.NULL;
    }

    private JSONObject statoMusica() throws Exception {
        JSONObject o = ok();
        o.put("volume", volume());
        Musica m = regia.musica();
        if (m == null) { o.put("pronta", false); return o; }
        o.put("pronta", m.stato() == Musica.PRONTA);
        o.put("errore", testo(m.errore()));
        o.put("suona", m.staSuonando());
        o.put("brano", testo(m.brano()));
        o.put("artista", testo(m.artista()));
        o.put("album", testo(m.album()));
        o.put("copertina", immagine(m.copertina()));
        o.put("contesto", testo(m.contesto()));
        o.put("durata", m.durata());
        o.put("posizione", m.posizione());
        o.put("mischia", m.mischiata());
        Cerca c = regia.ricerca();
        o.put("cerca", c != null && c.pronta());
        return o;
    }

    private JSONObject musica(String cosa, JSONObject d) {
        Musica m = regia.musica();
        if (m == null || m.stato() != Musica.PRONTA) return no("Spotify non e' pronto sul tablet");
        if ("pausa".equals(cosa)) { m.pausaRiprendi(); return ok(); }
        if ("successivo".equals(cosa)) { m.successivo(); return ok(); }
        if ("precedente".equals(cosa)) { m.precedente(); return ok(); }
        if ("mischia".equals(cosa)) { m.mischia(d.optBoolean("si", !m.mischiata())); return ok(); }
        if ("vai".equals(cosa)) { m.vaiA(Math.max(0L, d.optLong("ms", 0L))); return ok(); }
        if ("suona".equals(cosa)) {
            String uri = d.optString("uri", "");
            if (!uri.startsWith("spotify:")) return no("manca cosa suonare");
            String brano = d.optString("brano", "");
            m.suona(uri, brano.startsWith("spotify:") ? brano : null);
            return ok();
        }
        return no("non so fare « " + cosa + " » a Spotify");
    }

    private JSONObject playlist() throws Exception {
        Musica m = regia.musica();
        Preferiti p = regia.preferiti();
        if (m == null || p == null || m.stato() != Musica.PRONTA) return no("Spotify non e' pronto sul tablet");
        // Se l'elenco e' fresco non chiede niente: e' la stessa regola della
        // sezione, e il telefono che entra dieci volte non costa dieci giri.
        p.aggiorna(m);
        JSONObject o = ok();
        JSONArray elenco = new JSONArray();
        for (Preferiti.Voce v : p.tutte()) {
            JSONObject x = new JSONObject();
            x.put("uri", v.uri);
            x.put("nome", v.nome);
            x.put("immagine", immagine(v.immagine));
            x.put("elencabile", v.elencabile);
            elenco.put(x);
        }
        o.put("playlist", elenco);
        o.put("nota", testo(p.nota()));
        o.put("inCorso", p.inCorso());
        return o;
    }

    private Risposta cerca(JSONObject d) {
        final String frase = d.optString("testo", "").trim();
        if (frase.length() == 0) return errore(400, "manca cosa cercare");
        return aspetta(new Attesa() {
            @Override public void parti(final Fine fine) {
                Cerca c = regia.ricerca();
                if (c == null) { fine.fatto(no("la ricerca non e' pronta")); return; }
                c.cerca(frase, new Cerca.Esito() {
                    @Override public void trovati(Cerca.Trovato[] t, String perche) { fine.fatto(risultati(t, perche)); }
                });
            }
        }, 20000);
    }

    /** Dentro un risultato: la pagina di un artista, la sua discografia, i
     *  brani di un album. Le stesse tre strade della sezione. */
    private Risposta apriMusica(JSONObject d) {
        final String uri = d.optString("uri", "");
        final int tipo = d.optInt("tipo", -1);
        if (uri.length() == 0) return errore(400, "manca cosa aprire");
        return aspetta(new Attesa() {
            @Override public void parti(final Fine fine) {
                Cerca c = regia.ricerca();
                if (c == null) { fine.fatto(no("la ricerca non e' pronta")); return; }
                Cerca.Esito esito = new Cerca.Esito() {
                    @Override public void trovati(Cerca.Trovato[] t, String perche) { fine.fatto(risultati(t, perche)); }
                };
                if (tipo == Cerca.ARTISTA) c.paginaArtista(uri, esito);
                else if (tipo == Cerca.DISCOGRAFIA) c.discografia(uri, esito);
                else c.braniDi(uri, esito);
            }
        }, 20000);
    }

    private Risposta brani(JSONObject d) {
        final String uri = d.optString("uri", "");
        final boolean elencabile = d.optBoolean("elencabile", true);
        if (uri.length() == 0) return errore(400, "manca la playlist");
        return aspetta(new Attesa() {
            @Override public void parti(final Fine fine) {
                Musica m = regia.musica();
                Preferiti p = regia.preferiti();
                if (m == null || p == null) { fine.fatto(no("Spotify non e' pronto sul tablet")); return; }
                p.braniPer(m, uri, elencabile, new Preferiti.Consegna() {
                    @Override public void brani(Preferiti.Traccia[] t, String perche) {
                        if (t == null) { fine.fatto(no(perche != null ? perche : "niente")); return; }
                        try {
                            JSONObject o = ok();
                            JSONArray a = new JSONArray();
                            for (Preferiti.Traccia x : t) {
                                JSONObject r = new JSONObject();
                                r.put("uri", x.uri);
                                r.put("titolo", x.titolo);
                                r.put("artista", testo(x.artista));
                                r.put("immagine", immagine(x.immagine));
                                r.put("durata", x.durata);
                                a.put(r);
                            }
                            o.put("brani", a);
                            o.put("nota", testo(perche));
                            fine.fatto(o);
                        } catch (Exception e) {
                            fine.fatto(no("brani illeggibili"));
                        }
                    }
                });
            }
        }, 45000);
    }

    /** Non si chiama « trovati »: dentro una Cerca.Esito anonima quel nome
     *  sarebbe il metodo dell'interfaccia, che non restituisce niente. */
    private static JSONObject risultati(Cerca.Trovato[] t, String perche) {
        if (t == null) return no(perche != null ? perche : "niente");
        try {
            JSONObject o = ok();
            JSONArray a = new JSONArray();
            for (Cerca.Trovato x : t) {
                JSONObject r = new JSONObject();
                r.put("uri", testo(x.uri));
                r.put("titolo", testo(x.titolo));
                r.put("sotto", testo(x.sotto));
                r.put("immagine", immagine(x.immagine));
                r.put("tipo", x.tipo);
                a.put(r);
            }
            o.put("risultati", a);
            o.put("nota", testo(perche));
            return o;
        } catch (Exception e) {
            return no("risultati illeggibili");
        }
    }

    private interface Fine { void fatto(JSONObject o); }
    private interface Attesa { void parti(Fine fine); }

    /**
     * Come {@link #sullaUi}, ma per quello che risponde <b>dopo</b>: la
     * ricerca e i brani passano da Internet e arrivano con una callback.
     * Si fa partire sul thread dell'interfaccia, come tutto il resto, e questo
     * filo - che e' uno dei due della porta, non quello dell'interfaccia -
     * aspetta la consegna fino a un tetto.
     */
    private Risposta aspetta(final Attesa a, long tettoMs) {
        final JSONObject[] fuori = new JSONObject[1];
        final CountDownLatch fatto = new CountDownLatch(1);
        final Fine fine = new Fine() {
            @Override public void fatto(JSONObject o) {
                fuori[0] = o;
                fatto.countDown();
            }
        };
        ui.post(new Runnable() {
            @Override public void run() {
                try {
                    a.parti(fine);
                } catch (Exception e) {
                    Log.w(TAG, "telefono: " + e.getMessage());
                    fatto.countDown();
                }
            }
        });
        try {
            if (!fatto.await(tettoMs, TimeUnit.MILLISECONDS)) return errore(504, "Spotify ci mette troppo, riprova");
        } catch (InterruptedException fermato) {
            Thread.currentThread().interrupt();
            return errore(503, "interrotto");
        }
        JSONObject o = fuori[0];
        if (o == null) return errore(500, "qualcosa e' andato storto");
        return new Risposta(o.optBoolean("ok", false) ? 200 : 400, o);
    }

    private JSONObject sveglia(String cosa, JSONObject d, String telefono) throws Exception {
        if ("telefono".equals(cosa)) {
            Orologio.Sveglia s = orologio.svegliaDelTelefono(telefono, d.optLong("quando", 0L));
            Log.i(TAG, "telefono: sveglia specchio " + (s != null ? "alle " + s.orario() : "tolta"));
            JSONObject o = ok();
            o.put("id", s != null ? s.id : 0);
            return o;
        }
        if ("aggiungi".equals(cosa)) {
            int ora = d.optInt("ora", -1), minuto = d.optInt("minuto", -1);
            if (ora < 0 || ora > 23 || minuto < 0 || minuto > 59) return no("orario non valido");
            Orologio.Sveglia s = orologio.aggiungiSveglia(ora, minuto, d.optInt("giorni", 0) & 0x7F);
            JSONObject o = ok();
            o.put("id", s.id);
            return o;
        }
        Orologio.Sveglia s = orologio.trova(d.optInt("id", -1));
        if (s == null) return no("questa sveglia non c'e' piu'");
        if ("togli".equals(cosa)) { orologio.togliSveglia(s); return ok(); }
        if ("accendi".equals(cosa)) { orologio.accendiSveglia(s, d.optBoolean("attiva", true)); return ok(); }
        if ("regola".equals(cosa)) {
            int ora = d.optInt("ora", s.ora), minuto = d.optInt("minuto", s.minuto);
            if (ora < 0 || ora > 23 || minuto < 0 || minuto > 59) return no("orario non valido");
            orologio.regola(s, ora, minuto, d.optInt("giorni", s.giorni));
            return ok();
        }
        return no("non so fare « " + cosa + " » a una sveglia");
    }

    // ---- abbinamento -------------------------------------------------------

    /**
     * Il telefono chiede un codice: lo si mostra sul tablet.
     *
     * Se ce n'e' gia' uno valido si rimostra quello, non se ne fa un altro:
     * chiunque sulla rete puo' chiedere, e rifare il codice a ogni richiesta
     * vorrebbe dire che un altro apparecchio che chiede in continuazione
     * cambia le cifre sotto gli occhi di chi le sta copiando.
     */
    private Risposta chiediCodice(JSONObject d) {
        final String nome = nome(d);
        final String mostrato;
        synchronized (this) {
            long adesso = System.currentTimeMillis();
            if (codice == null || adesso >= codiceFinoA) {
                codice = String.format(Locale.ROOT, "%06d", caso.nextInt(1000000));
                codiceFinoA = adesso + CODICE_MS;
                errori = 0;
                nomeInAttesa = nome;
            }
            mostrato = codice.substring(0, 3) + " " + codice.substring(3);
        }
        Log.i(TAG, "telefono: " + nome + " chiede di abbinarsi");
        ui.post(new Runnable() {
            @Override public void run() { regia.codice(mostrato, nomeInAttesa); }
        });
        ui.removeCallbacks(codiceScaduto);
        ui.postDelayed(codiceScaduto, CODICE_MS + 500);
        return new Risposta(200, ok());
    }

    private Risposta abbina(JSONObject d) {
        final String nome = nome(d);
        String dato = d.optString("codice", "").replace(" ", "");
        synchronized (this) {
            if (codice == null || System.currentTimeMillis() >= codiceFinoA) {
                return errore(403, "nessun codice in corso: chiedine uno");
            }
            if (!MessageDigest.isEqual(codice.getBytes(), dato.getBytes())) {
                if (++errori >= ERRORI_MAX) {
                    codice = null;
                    ui.post(new Runnable() {
                        @Override public void run() { regia.codice(null, null); }
                    });
                    return errore(403, "troppi tentativi: chiedi un codice nuovo");
                }
                return errore(403, "codice sbagliato");
            }
            codice = null;
        }

        byte[] id = new byte[4], chiave = new byte[32];
        caso.nextBytes(id);
        caso.nextBytes(chiave);
        String quale = "t" + esadecimale(id);
        String segreto = esadecimale(chiave);
        try {
            synchronized (telefoni) {
                JSONObject t = new JSONObject();
                t.put("nome", nome);
                t.put("chiave", segreto);
                t.put("dal", System.currentTimeMillis());
                telefoni.put(quale, t);
                potaTelefoni();
                JSONObject tutto = new JSONObject();
                tutto.put("telefoni", telefoni);
                Archivio.scrivi(contesto, FILE, tutto);
            }
            JSONObject o = ok();
            o.put("telefono", quale);
            o.put("chiave", segreto);
            o.put("tablet", "Casa");
            Log.i(TAG, "telefono: " + nome + " abbinato come " + quale);
            ui.post(new Runnable() {
                @Override public void run() { regia.codice(null, null); regia.abbinato(nome); }
            });
            return new Risposta(200, o);
        } catch (Exception e) {
            return errore(500, "non riesco a salvare l'abbinamento");
        }
    }

    private void potaTelefoni() {
        while (telefoni.length() > TELEFONI_MAX) {
            String vecchio = null;
            long quando = Long.MAX_VALUE;
            Iterator<String> chiavi = telefoni.keys();
            while (chiavi.hasNext()) {
                String k = chiavi.next();
                long dal = telefoni.optJSONObject(k).optLong("dal", 0L);
                if (dal < quando) { quando = dal; vecchio = k; }
            }
            if (vecchio == null) return;
            telefoni.remove(vecchio);
        }
    }

    private static String nome(JSONObject d) {
        String n = d.optString("nome", "").trim();
        if (n.length() == 0) n = "un telefono";
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    // ---- la firma ----------------------------------------------------------

    /** Il telefono che ha firmato, o null. */
    private String verifica(Richiesta r) {
        String id = r.intestazioni.get("x-casa-telefono");
        String ora = r.intestazioni.get("x-casa-ora");
        String firma = r.intestazioni.get("x-casa-firma");
        if (id == null || ora == null || firma == null) return null;

        String chiave;
        synchronized (telefoni) {
            JSONObject t = telefoni.optJSONObject(id);
            chiave = t != null ? t.optString("chiave", "") : "";
        }
        if (chiave.length() != 64) return null;

        long quando;
        try {
            quando = Long.parseLong(ora.trim());
        } catch (NumberFormatException storto) {
            return null;
        }
        long adesso = System.currentTimeMillis();
        if (Math.abs(adesso - quando) > TOLLERANZA_MS) {
            Log.w(TAG, "telefono: firma fuori tempo di " + (adesso - quando) / 1000 + " s: orologi storti?");
            return null;
        }

        String attesa;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(daEsadecimale(chiave), "HmacSHA256"));
            String messaggio = r.metodo + "\n" + r.percorso + "\n" + ora.trim() + "\n" + r.corpo;
            attesa = esadecimale(mac.doFinal(messaggio.getBytes("UTF-8")));
        } catch (Exception e) {
            return null;
        }
        if (!MessageDigest.isEqual(attesa.getBytes(), firma.trim().toLowerCase(Locale.ROOT).getBytes())) {
            return null;
        }

        synchronized (firmeViste) {
            Iterator<Map.Entry<String, Long>> vecchie = firmeViste.entrySet().iterator();
            while (vecchie.hasNext() && adesso - vecchie.next().getValue() > 2 * TOLLERANZA_MS) {
                vecchie.remove();
            }
            if (firmeViste.containsKey(attesa)) return null;
            firmeViste.put(attesa, adesso);
        }
        return id;
    }

    // ---- HTTP, il minimo ---------------------------------------------------

    private static Richiesta leggi(InputStream in) throws Exception {
        Richiesta r = new Richiesta();
        String prima = riga(in);
        if (prima == null) return null;
        String[] pezzi = prima.split(" ");
        if (pezzi.length < 2) return null;
        r.metodo = pezzi[0].toUpperCase(Locale.ROOT);
        r.percorso = pezzi[1];

        int letti = prima.length();
        int lunghezza = 0;
        while (true) {
            String h = riga(in);
            if (h == null) return null;
            if (h.length() == 0) break;
            letti += h.length();
            if (letti > INTESTAZIONI_MAX) return null;
            int due = h.indexOf(':');
            if (due <= 0) continue;
            String nome = h.substring(0, due).trim().toLowerCase(Locale.ROOT);
            String valore = h.substring(due + 1).trim();
            r.intestazioni.put(nome, valore);
            if ("content-length".equals(nome)) {
                try { lunghezza = Integer.parseInt(valore); } catch (NumberFormatException storto) { return null; }
            }
        }
        if (lunghezza < 0 || lunghezza > CORPO_MAX) return null;
        byte[] corpo = new byte[lunghezza];
        int presi = 0;
        while (presi < lunghezza) {
            int n = in.read(corpo, presi, lunghezza - presi);
            if (n < 0) return null;
            presi += n;
        }
        r.corpo = new String(corpo, "UTF-8");
        return r;
    }

    /** Una riga fino a CRLF, in ASCII. null a connessione chiusa o riga
     *  troppo lunga. */
    private static String riga(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        while (true) {
            int c = in.read();
            if (c < 0) return null;
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > INTESTAZIONI_MAX) return null;
        }
        return b.toString("ISO-8859-1");
    }

    private static void scrivi(OutputStream out, Risposta r) throws Exception {
        byte[] corpo = r.corpo.toString().getBytes("UTF-8");
        String testa = "HTTP/1.1 " + r.codice + " " + motivo(r.codice) + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + corpo.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(testa.getBytes("ISO-8859-1"));
        out.write(corpo);
        out.flush();
    }

    private static String motivo(int codice) {
        switch (codice) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default:  return "Error";
        }
    }

    // ---- sul thread dell'interfaccia --------------------------------------

    private interface Lavoro { JSONObject fai() throws Exception; }

    /**
     * Esegue sul thread dell'interfaccia e aspetta la risposta.
     *
     * Lampade, routine e sveglie si toccano solo da li' - e' la regola di tutta
     * Casa, e le liste non hanno lucchetti. Il tetto di tre secondi c'e' perche'
     * un'interfaccia impegnata non deve lasciare appeso il telefono: meglio un
     * « occupato » e un nuovo tentativo.
     */
    private Risposta sullaUi(final Lavoro l) {
        final JSONObject[] fuori = new JSONObject[1];
        final CountDownLatch fatto = new CountDownLatch(1);
        ui.post(new Runnable() {
            @Override public void run() {
                try {
                    fuori[0] = l.fai();
                } catch (Exception e) {
                    Log.w(TAG, "telefono: " + e.getMessage());
                } finally {
                    fatto.countDown();
                }
            }
        });
        try {
            if (!fatto.await(ATTESA_UI_MS, TimeUnit.MILLISECONDS)) return errore(503, "Assistente Home e' occupato, riprova");
        } catch (InterruptedException fermato) {
            Thread.currentThread().interrupt();
            return errore(503, "interrotto");
        }
        JSONObject o = fuori[0];
        if (o == null) return errore(500, "qualcosa e' andato storto");
        return new Risposta(o.optBoolean("ok", false) ? 200 : 400, o);
    }

    private static JSONObject ok() {
        JSONObject o = new JSONObject();
        try { o.put("ok", true); } catch (Exception ignorato) { }
        return o;
    }

    private static JSONObject no(String perche) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", false);
            o.put("errore", perche);
        } catch (Exception ignorato) { }
        return o;
    }

    private static Risposta errore(int codice, String perche) {
        return new Risposta(codice, no(perche));
    }

    private static String esadecimale(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 0xFF));
        return s.toString();
    }

    private static byte[] daEsadecimale(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }
}
