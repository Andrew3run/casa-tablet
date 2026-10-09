package dev.casa;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Le playlist di chi ascolta.
 *
 * <b>La Web API pubblica non serve, e non e' un'opinione.</b> La prima
 * versione chiedeva {@code api.spotify.com/v1/me/playlists} con il token che
 * il demone da' su {@code POST /token}. Risposta: <b>429, "API rate limit
 * exceeded"</b>, alla prima richiesta, sempre, anche a distanza di minuti - il
 * token di una sessione librespot su quell'API non ha nessuna quota. Provato,
 * non dedotto.
 *
 * <b>Quello che funziona e' l'API interna</b>, {@code spclient.wg.spotify.com}:
 * e' dove quel token e' nato ed e' dove l'app ufficiale va a prendere le stesse
 * cose. Due passi:
 *
 * <pre>
 *   /playlist/v2/user/{utente}/rootlist   l'elenco degli URI
 *   /playlist/v2/playlist/{id}/metadata   nome e copertina, uno per uno
 * </pre>
 *
 * Parla protobuf di suo; con {@code Accept: application/json} risponde in JSON,
 * che qui vuol dire {@code org.json} e nessuna libreria in piu'.
 *
 * <b>Le tessere compaiono mentre arrivano.</b> Una richiesta per playlist su un
 * Cortex-A7 sono un paio di secondi in tutto, e aspettarli tutti per mostrare
 * la griglia in blocco vorrebbe dire due secondi di schermo vuoto. Si avvisa
 * ogni poche playlist, e la griglia si riempie da sinistra.
 *
 * <b>E c'e' una copia su disco</b>, perche' la griglia deve esserci
 * <b>subito</b>: si apre la sezione e le tessere ci sono gia', semmai si
 * aggiornano un secondo dopo. Un apparecchio da muro che mostra un rettangolo
 * vuoto mentre pensa sembra rotto anche quando sta funzionando.
 */
public final class Preferiti {

    private static final String TAG = "Casa.Preferiti";

    private static final String FILE = "musica.json";
    private static final String SPCLIENT = "https://spclient.wg.spotify.com";

    /** Quante se ne chiedono. La griglia ne mostra dodici per volta e scorre;
     *  oltre le due dozzine si smette, perche' ogni nome e' una richiesta e a
     *  quel punto si sta scaricando una libreria per mostrarne una schermata. */
    private static final int QUANTE = 24;

    /** Ogni quante si avvisa chi disegna, mentre arrivano. */
    private static final int A_GRUPPI = 3;

    /** Quanti brani si leggono di una playlist. Trenta sono quattro schermate
     *  di elenco: oltre, si starebbe scaricando un archivio per scorrerlo con
     *  un dito. */
    private static final int QUANTI_BRANI = 30;

    /** Ogni quanto si torna a chiedere. Le playlist di casa non cambiano ogni
     *  minuto, e ogni richiesta e' una connessione TLS su un Cortex-A7. */
    private static final long SCADENZA = 6 * 60 * 60 * 1000L;

    /** Una playlist: quel che basta a disegnarla e a farla suonare. */
    public static final class Voce {
        public final String uri, nome, immagine;
        /** false per i brani che piacciono: il loro elenco non arriva dallo
         *  stesso posto delle playlist - vedi {@link #brani(String)}. */
        public final boolean elencabile;
        Voce(String uri, String nome, String immagine, boolean elencabile) {
            this.uri = uri; this.nome = nome; this.immagine = immagine;
            this.elencabile = elencabile;
        }
        Voce(String uri, String nome, String immagine) { this(uri, nome, immagine, true); }
    }

    /**
     * <b>I brani che ti piacciono</b>, che nel rootlist non ci sono.
     *
     * Non sono una playlist: sono la « collection » dell'account, e Spotify la
     * tiene da un'altra parte. Il lettore la suona senza storie - l'URI
     * {@code spotify:user:<chi>:collection} funziona - e anche l'elenco si
     * legge, ma non dove si cercherebbe: sta in
     * {@code /collection/collection/{utente}} (vedi {@link #elencaIPreferiti}),
     * non sotto i {@code /collection/v1} o {@code /v2}, che rispondono 404.
     * Da qui il flag {@code elencabile}: non vuol dire « non si puo' elencare »,
     * vuol dire « si elenca da un'altra parte ».
     *
     * Sta per prima perche' per quasi tutti e' l'elenco piu' usato, ed e'
     * proprio quello che mancava.
     */
    private static Voce brani(String utente) {
        return new Voce("spotify:user:" + utente + ":collection",
                        "Brani che ti piacciono", null, false);
    }

    /**
     * Un brano dentro una playlist.
     *
     * Porta il suo URI perche' e' quello che si manda al lettore per saltare
     * dritti a lui: {@code /player/play} accetta il contesto <b>e</b> il brano
     * da cui partire, quindi toccare la terza riga fa partire la playlist dalla
     * terza riga - non un brano isolato senza seguito.
     */
    public static final class Traccia {
        public final String uri, titolo, artista, immagine;
        public final long durata;
        Traccia(String uri, String titolo, String artista, String immagine, long durata) {
            this.uri = uri; this.titolo = titolo; this.artista = artista;
            this.immagine = immagine; this.durata = durata;
        }
    }

    public interface Ascolto { void preferitiCambiati(); }

    private static final Voce[] NESSUNA = new Voce[0];
    private static final Traccia[] NESSUNA_TRACCIA = new Traccia[0];

    private final Context contesto;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler lavoro;

    private volatile Voce[] voci = NESSUNA;
    private volatile long quando;
    private volatile String nota;
    private volatile boolean inCorso;

    /** La playlist aperta e i suoi brani. Vivono solo finche' e' aperta: sono
     *  decine di righe che non servono a nient'altro, e tenerle vorrebbe dire
     *  pagare memoria per una schermata che di solito non e' quella in scena. */
    private volatile String aperta;
    private volatile Traccia[] tracce = NESSUNA_TRACCIA;
    private volatile String notaTracce;
    private volatile int giroApertura;

    private Ascolto ascolto;

    public Preferiti(Context c) {
        contesto = c.getApplicationContext();
        HandlerThread t = new HandlerThread("Casa-preferiti");
        t.start();
        lavoro = new Handler(t.getLooper());
        rileggiDalDisco();
    }

    public void setAscolto(Ascolto a) { ascolto = a; }

    public Voce[] tutte() { return voci; }

    /** Perche' l'elenco e' vuoto, quando lo e'. null se non c'e' niente da
     *  spiegare. */
    public String nota() { return nota; }

    /** true mentre si sta chiedendo. Serve al tasto « aggiorna », che gira
     *  finche' dura: senza, premerlo non sembra fare niente - le playlist
     *  arrivano dopo un paio di secondi e le tessere che gia' c'erano non
     *  cambiano. */
    public boolean inCorso() { return inCorso; }

    /**
     * Rinfresca, se e' il caso.
     *
     * Si chiama entrando nella sezione: se l'elenco c'e' ed e' fresco non fa
     * niente, quindi entrare e uscire dieci volte non costa dieci richieste.
     */
    public void aggiorna(Musica musica) { vai(musica, false); }

    /**
     * Rinfresca <b>comunque</b>, anche se l'elenco e' fresco.
     *
     * E' il tasto « aggiorna » della sezione, e serve per una ragione che la
     * scadenza da sola non copre: le playlist si cambiano <b>da un altro
     * apparecchio</b> - dal telefono, dal computer - e Spotify non avvisa
     * nessuno quando succede. Senza questo, una playlist creata sul telefono
     * comparirebbe sul tablet solo dopo sei ore, e nel frattempo l'unica
     * spiegazione plausibile per chi guarda e' che Casa sia rotta.
     *
     * Sei ore restano la cadenza automatica: chi non tocca niente non paga
     * richieste, e chi ha appena cambiato qualcosa ha un tasto.
     */
    public void rinfresca(Musica musica) { vai(musica, true); }

    private void vai(final Musica musica, boolean forzato) {
        if (vetrina) return;
        if (musica == null || musica.stato() != Musica.PRONTA) return;
        if (inCorso) return;
        final String utente = musica.utente();
        if (utente == null) return;      // si sa solo dopo il primo /status
        // Fresco vuol dire due cose: preso da poco, <b>e</b> di questo account.
        // La prima tessera e' sempre i brani che piacciono, e il suo URI porta
        // dentro il nome dell'utente: se non corrisponde, quell'elenco e' di
        // un'altra sessione - o di una versione di Casa che quella tessera non
        // la metteva ancora - e va rifatto.
        boolean fresco = !forzato && voci.length > 0
                && System.currentTimeMillis() - quando < SCADENZA
                && voci[0].uri.equals(brani(utente).uri);
        if (fresco) return;
        inCorso = true;
        // Si avvisa <b>subito</b>, prima ancora di avere il gettone: e' quello
        // che fa partire la rotella nel momento in cui si preme, invece che un
        // secondo dopo.
        avvisa();
        musica.gettone(new Musica.Gettone() {
            @Override public void arrivato(String token) {
                if (token == null) {
                    inCorso = false;
                    if (voci.length == 0) dai("la sessione non e' ancora pronta");
                    else avvisa();
                    return;
                }
                chiedi(token, utente);
            }
        });
    }

    private void chiedi(final String token, final String utente) {
        lavoro.post(new Runnable() {
            @Override public void run() {
                try {
                    String[] uri = rootlist(token, utente);
                    if (uri == null) {
                        if (voci.length == 0) dai("non riesco a leggere le tue playlist");
                        return;
                    }
                    riempi(token, utente, uri);
                } catch (Exception e) {
                    Log.w(TAG, "playlist non lette", e);
                    if (voci.length == 0) dai("non riesco a leggere le tue playlist");
                } finally {
                    inCorso = false;
                    avvisa();
                }
            }
        });
    }

    /** L'elenco degli URI, nell'ordine in cui li tiene Spotify. */
    private static String[] rootlist(String token, String utente) throws Exception {
        String risposta = scarica(SPCLIENT + "/playlist/v2/user/" + utente
                + "/rootlist?decorate=revision,attributes,length,owner,capabilities", token);
        if (risposta == null) return null;

        JSONObject dati = new JSONObject(risposta);
        JSONObject dentro = dati.optJSONObject("contents");
        JSONArray elenco = dentro != null ? dentro.optJSONArray("items") : null;
        if (elenco == null) return new String[0];

        String[] fuori = new String[Math.min(elenco.length(), QUANTE)];
        int quante = 0;
        for (int i = 0; i < elenco.length() && quante < fuori.length; i++) {
            JSONObject v = elenco.optJSONObject(i);
            if (v == null) continue;
            String uri = v.optString("uri", null);
            // Le cartelle stanno nello stesso elenco come start-group /
            // end-group: non sono cose da suonare, e non hanno un nome da
            // chiedere.
            if (uri == null || !uri.startsWith("spotify:playlist:")) continue;
            fuori[quante++] = uri;
        }
        if (quante == fuori.length) return fuori;
        String[] esatte = new String[quante];
        System.arraycopy(fuori, 0, esatte, 0, quante);
        return esatte;
    }

    /** Nome e copertina, una per una, pubblicando man mano. */
    private void riempi(String token, String utente, String[] uri) throws Exception {
        Voce[] lette = new Voce[uri.length + 1];
        int quante = 0;
        lette[quante++] = brani(utente);
        pubblica(lette, quante);
        for (String u : uri) {
            String id = u.substring(u.lastIndexOf(':') + 1);
            String nome = null, immagine = null;
            String risposta = scarica(SPCLIENT + "/playlist/v2/playlist/" + id + "/metadata", token);
            if (risposta != null) {
                JSONObject attributi = new JSONObject(risposta).optJSONObject("attributes");
                if (attributi != null) {
                    nome = attributi.optString("name", null);
                    immagine = immagine(attributi);
                }
            }
            // Una playlist senza nome resta nell'elenco: si suona lo stesso, e
            // la tessera mostra un punto interrogativo invece di sparire.
            lette[quante++] = new Voce(u, nome != null && nome.length() > 0 ? nome : "senza nome",
                                       immagine);
            if (quante % A_GRUPPI == 0) pubblica(lette, quante);
        }
        pubblica(lette, quante);
        quando = System.currentTimeMillis();
        nota = null;
        salvaSulDisco();
    }

    private void pubblica(Voce[] lette, int quante) {
        Voce[] esatte = new Voce[quante];
        System.arraycopy(lette, 0, esatte, 0, quante);
        voci = esatte;
        avvisa();
    }

    /**
     * La copertina, quando c'e'.
     *
     * Le playlist fatte da Spotify - i Daily Mix, le radio - portano
     * {@code pictureSize}, un elenco di misure con il loro indirizzo. Quelle
     * fatte da una persona spesso non hanno niente: la loro copertina e' un
     * mosaico che il client compone da solo con le copertine dei brani, e
     * rifarlo qui vorrebbe dire scaricare l'intera playlist per disegnare un
     * quadratino di due centimetri. Meglio l'iniziale sul suo colore, che
     * {@link SezioneMusica} disegna quando qui torna null.
     */
    private static String immagine(JSONObject attributi) {
        JSONArray misure = attributi.optJSONArray("pictureSize");
        if (misure == null || misure.length() == 0) return null;
        JSONObject prima = misure.optJSONObject(0);
        return prima != null ? prima.optString("url", null) : null;
    }

    /** Come {@link #scarica}, ma i byte cosi' come sono: la collezione risponde
     *  in protobuf e l'{@code Accept: application/json} li' non lo ascolta. */
    private static byte[] scaricaByte(String indirizzo, String token) {
        HttpURLConnection c = null;
        java.io.InputStream in = null;
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(6000);
            c.setReadTimeout(12000);
            int esito = c.getResponseCode();
            if (esito != 200) {
                Log.w(TAG, "Spotify ha risposto " + esito + " a " + indirizzo);
                return null;
            }
            in = c.getInputStream();
            java.io.ByteArrayOutputStream tutto = new java.io.ByteArrayOutputStream(16384);
            byte[] pezzo = new byte[8192];
            int n;
            while ((n = in.read(pezzo)) > 0) tutto.write(pezzo, 0, n);
            return tutto.toByteArray();
        } catch (Exception e) {
            Log.w(TAG, "richiesta fallita", e);
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignorata) { }
            if (c != null) c.disconnect();
        }
    }

    private static String scarica(String indirizzo, String token) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Authorization", "Bearer " + token);
            // Senza questa, l'API interna risponde in protobuf.
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            int esito = c.getResponseCode();
            if (esito != 200) {
                Log.w(TAG, "Spotify ha risposto " + esito + " a " + indirizzo);
                return null;
            }
            return Musica.tutto(c.getInputStream());
        } catch (Exception e) {
            Log.w(TAG, "richiesta fallita", e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---- dentro una playlist -----------------------------------------------

    public String aperta() { return aperta; }
    public Traccia[] tracce() { return tracce; }
    public String notaTracce() { return notaTracce; }

    /** Chiude l'elenco e lascia andare i brani. */
    public void chiudiElenco() {
        giroApertura++;
        aperta = null;
        tracce = NESSUNA_TRACCIA;
        notaTracce = null;
    }

    /** Chi vuole i brani di una playlist senza aprirla nella sezione. */
    public interface Consegna { void brani(Traccia[] brani, String perche); }

    /**
     * I brani di una playlist per il telefono.
     *
     * <b>Non passa da {@link #apri}</b>: quella cambia la playlist aperta nella
     * sezione Spotify, e chi sfoglia dal telefono non deve cambiare quello che
     * si vede sul tablet. La strada e' la stessa - il token, l'elenco degli
     * URI, un metadato per brano - ma il risultato va solo a chi l'ha chiesto,
     * tutto insieme alla fine, sul filo dei preferiti.
     */
    public void braniPer(final Musica musica, final String uri, final boolean elencabile, final Consegna c) {
        if (musica == null || uri == null) { c.brani(null, "manca la playlist"); return; }
        musica.gettone(new Musica.Gettone() {
            @Override public void arrivato(final String token) {
                if (token == null) { c.brani(null, "la sessione non e' pronta"); return; }
                lavoro.post(new Runnable() {
                    @Override public void run() {
                        try {
                            String[] brani;
                            if (elencabile) {
                                String id = uri.substring(uri.lastIndexOf(':') + 1);
                                String risposta = scarica(SPCLIENT + "/playlist/v2/playlist/" + id, token);
                                if (risposta == null) { c.brani(null, "non riesco a leggere questa playlist"); return; }
                                JSONObject dentro = new JSONObject(risposta).optJSONObject("contents");
                                JSONArray elenco = dentro != null ? dentro.optJSONArray("items") : null;
                                int quanti = elenco != null ? elenco.length() : 0;
                                brani = new String[quanti];
                                for (int i = 0; i < quanti; i++) {
                                    JSONObject v = elenco.optJSONObject(i);
                                    brani[i] = v != null ? v.optString("uri", null) : null;
                                }
                            } else {
                                String utente = uri.substring("spotify:user:".length(), uri.lastIndexOf(':'));
                                byte[] risposta = scaricaByte(SPCLIENT + "/collection/collection/" + utente
                                        + "?allowonlytracks=true&complete=true", token);
                                if (risposta == null) { c.brani(null, "non riesco a leggere i tuoi preferiti"); return; }
                                String[] identificativi = collezione(risposta);
                                brani = new String[identificativi.length];
                                for (int i = 0; i < identificativi.length; i++) {
                                    brani[i] = "spotify:track:" + base62(identificativi[i]);
                                }
                            }
                            Traccia[] lette = new Traccia[Math.min(brani.length, QUANTI_BRANI)];
                            int quante = 0;
                            for (int i = 0; i < brani.length && quante < lette.length; i++) {
                                if (brani[i] == null || !brani[i].startsWith("spotify:track:")) continue;
                                Traccia t = traccia(token, brani[i]);
                                if (t != null) lette[quante++] = t;
                            }
                            Traccia[] esatte = new Traccia[quante];
                            System.arraycopy(lette, 0, esatte, 0, quante);
                            c.brani(esatte, quante == 0 ? "nessun brano leggibile" : null);
                        } catch (Exception e) {
                            Log.w(TAG, "brani per il telefono non letti", e);
                            c.brani(null, "non riesco a leggere questa playlist");
                        }
                    }
                });
            }
        });
    }

    /**
     * Apre una playlist ed elenca i suoi brani.
     *
     * <b>Una richiesta per brano</b>, e non e' una svista: l'elenco degli URI
     * arriva in un colpo solo, ma il nome di un brano sta nel suo metadato, e
     * un endpoint che li prenda tutti insieme in JSON non c'e' - le varianti
     * provate rispondono 400 o 405. Le righe compaiono man mano che arrivano,
     * che su venti brani vuol dire tre o quattro secondi con l'elenco che si
     * riempie sotto gli occhi invece di una schermata ferma.
     *
     * Il numero di giro serve a buttare via le risposte in ritardo: se nel
     * frattempo la playlist e' stata chiusa o se ne e' aperta un'altra, quello
     * che arriva dalla precedente non deve toccare niente. E' la stessa
     * guardia che {@link Radio} usa sulle stazioni.
     */
    public void apri(final Musica musica, final Voce playlist) {
        if (musica == null || playlist == null) return;
        final int mio = ++giroApertura;
        aperta = playlist.uri;
        tracce = NESSUNA_TRACCIA;
        notaTracce = null;

        avvisa();

        musica.gettone(new Musica.Gettone() {
            @Override public void arrivato(final String token) {
                if (token == null) { daiTracce(mio, "la sessione non e' pronta"); return; }
                if (mio != giroApertura) return;
                lavoro.post(new Runnable() {
                    @Override public void run() {
                        if (playlist.elencabile) elenca(token, playlist.uri, mio);
                        else elencaIPreferiti(token, playlist.uri, mio);
                    }
                });
            }
        });
    }

    private void elenca(String token, String playlistUri, int mio) {
        try {
            String id = playlistUri.substring(playlistUri.lastIndexOf(':') + 1);
            String risposta = scarica(SPCLIENT + "/playlist/v2/playlist/" + id, token);
            if (risposta == null) { daiTracce(mio, "non riesco a leggere questa playlist"); return; }

            JSONObject dentro = new JSONObject(risposta).optJSONObject("contents");
            JSONArray elenco = dentro != null ? dentro.optJSONArray("items") : null;
            if (elenco == null || elenco.length() == 0) { daiTracce(mio, "playlist vuota"); return; }

            int quanti = Math.min(elenco.length(), QUANTI_BRANI);
            Traccia[] lette = new Traccia[quanti];
            int quante = 0;
            for (int i = 0; i < quanti; i++) {
                if (mio != giroApertura) return;
                JSONObject v = elenco.optJSONObject(i);
                String uri = v != null ? v.optString("uri", null) : null;
                if (uri == null || !uri.startsWith("spotify:track:")) continue;
                Traccia t = traccia(token, uri);
                if (t != null) lette[quante++] = t;
                if (quante > 0 && quante % A_GRUPPI == 0) pubblicaTracce(mio, lette, quante);
            }
            pubblicaTracce(mio, lette, quante);
            if (quante == 0) daiTracce(mio, "nessun brano leggibile");
        } catch (Exception e) {
            Log.w(TAG, "brani non letti", e);
            daiTracce(mio, "non riesco a leggere questa playlist");
        }
    }

    /**
     * I brani che piacciono, che non sono una playlist.
     *
     * <b>Dove stanno.</b> Non sotto {@code /collection/v1/…} ne'
     * {@code /collection/v2/…} - quelli rispondono 404 - ma sotto
     * {@code /collection/collection/{utente}}, che e' l'indirizzo hermes
     * {@code hm://collection/collection/…} rispecchiato su HTTPS. Ci si arriva
     * cercando il posto giusto, non arrendendosi al primo 404.
     *
     * <b>Parla protobuf e basta</b>: qui l'{@code Accept: application/json} non
     * lo ascolta. Non serve una libreria per leggerlo - il formato e' fatto per
     * essere letto senza: ogni campo e' un numero e un tipo, e di questo
     * messaggio servono due campi soli. Ogni elemento porta il numero
     * identificativo del brano in sedici byte e il momento in cui e' stato
     * aggiunto; si tengono i piu' recenti, si riscrive l'identificativo in
     * base62 - com'e' negli URI - e da li' in poi e' la stessa strada dei brani
     * di una playlist.
     */
    private void elencaIPreferiti(String token, String collezione, int mio) {
        try {
            String utente = collezione.substring("spotify:user:".length(),
                                                 collezione.lastIndexOf(':'));
            byte[] risposta = scaricaByte(SPCLIENT + "/collection/collection/" + utente
                    + "?allowonlytracks=true&complete=true", token);
            if (risposta == null) { daiTracce(mio, "non riesco a leggere i tuoi preferiti"); return; }

            String[] identificativi = collezione(risposta);
            if (identificativi.length == 0) {
                daiTracce(mio, "non hai ancora brani preferiti");
                return;
            }

            int quanti = Math.min(identificativi.length, QUANTI_BRANI);
            Traccia[] lette = new Traccia[quanti];
            int quante = 0;
            for (int i = 0; i < quanti; i++) {
                if (mio != giroApertura) return;
                Traccia t = traccia(token, "spotify:track:" + base62(identificativi[i]));
                if (t != null) lette[quante++] = t;
                if (quante > 0 && quante % A_GRUPPI == 0) pubblicaTracce(mio, lette, quante);
            }
            pubblicaTracce(mio, lette, quante);
            if (quante == 0) daiTracce(mio, "nessun brano leggibile");
        } catch (Exception e) {
            Log.w(TAG, "preferiti non letti", e);
            daiTracce(mio, "non riesco a leggere i tuoi preferiti");
        }
    }

    private static Traccia traccia(String token, String uri) {
        try {
            String risposta = scarica(SPCLIENT + "/metadata/4/track/"
                    + gid(uri.substring(uri.lastIndexOf(':') + 1)), token);
            if (risposta == null) return null;
            JSONObject d = new JSONObject(risposta);
            String titolo = d.optString("name", null);
            if (titolo == null) return null;

            StringBuilder chi = new StringBuilder();
            JSONArray artisti = d.optJSONArray("artist");
            if (artisti != null) {
                for (int i = 0; i < artisti.length(); i++) {
                    JSONObject a = artisti.optJSONObject(i);
                    if (a == null) continue;
                    if (chi.length() > 0) chi.append(", ");
                    chi.append(a.optString("name", ""));
                }
            }
            return new Traccia(uri, titolo, chi.toString(),
                               copertinaDi(d.optJSONObject("album")),
                               d.optLong("duration", 0));
        } catch (Exception e) {
            Log.w(TAG, "brano non letto", e);
            return null;
        }
    }

    /** La copertina piu' piccola dell'album: in una riga alta cinquanta pixel,
     *  quella da trecento sarebbe nove volte i pixel che si vedono. */
    private static String copertinaDi(JSONObject album) {
        if (album == null) return null;
        JSONObject gruppo = album.optJSONObject("cover_group");
        JSONArray immagini = gruppo != null ? gruppo.optJSONArray("image") : null;
        if (immagini == null || immagini.length() == 0) return null;
        JSONObject scelta = null;
        for (int i = 0; i < immagini.length(); i++) {
            JSONObject im = immagini.optJSONObject(i);
            if (im == null) continue;
            if (scelta == null || im.optInt("width", 9999) < scelta.optInt("width", 9999)) {
                scelta = im;
            }
        }
        String file = scelta != null ? scelta.optString("file_id", null) : null;
        return file != null ? "https://i.scdn.co/image/" + file : null;
    }

    /**
     * Legge il protobuf della collezione: gli identificativi dei brani, dal
     * piu' recente.
     *
     * <b>Trenta righe invece di una libreria.</b> Il formato e' una sequenza di
     * coppie (numero di campo + tipo, valore): si legge un intero a lunghezza
     * variabile, si guardano gli ultimi tre bit per sapere di che tipo e', e si
     * salta o si prende. Di questo messaggio servono l'elemento (campo 1 del
     * messaggio esterno), dentro cui stanno l'identificativo in sedici byte
     * (campo 2) e il momento in cui e' stato aggiunto (campo 5). Tutto il resto
     * si salta senza sapere cos'e', che e' proprio la proprieta' per cui
     * questo formato e' fatto cosi'.
     */
    private static String[] collezione(byte[] dati) {
        String[] identificativi = new String[512];
        long[] quando = new long[512];
        int quanti = 0;
        int i = 0;
        while (i < dati.length && quanti < identificativi.length) {
            long[] chiave = varint(dati, i);
            if (chiave == null) break;
            int campo = (int) (chiave[0] >> 3), tipo = (int) (chiave[0] & 7);
            i = (int) chiave[1];
            if (tipo != 2) { i = salta(dati, i, tipo); if (i < 0) break; continue; }

            long[] lunghezza = varint(dati, i);
            if (lunghezza == null) break;
            int quanto = (int) lunghezza[0];
            i = (int) lunghezza[1];
            if (i + quanto > dati.length) break;

            if (campo == 1) {
                String id = null;
                long momento = 0;
                int j = i, fine = i + quanto;
                while (j < fine) {
                    long[] k2 = varint(dati, j);
                    if (k2 == null) break;
                    int c2 = (int) (k2[0] >> 3), t2 = (int) (k2[0] & 7);
                    j = (int) k2[1];
                    if (t2 == 2) {
                        long[] l2 = varint(dati, j);
                        if (l2 == null) break;
                        int q2 = (int) l2[0];
                        j = (int) l2[1];
                        if (c2 == 2 && q2 == 16) id = esadecimale(dati, j, q2);
                        j += q2;
                    } else if (t2 == 0) {
                        long[] v2 = varint(dati, j);
                        if (v2 == null) break;
                        if (c2 == 5) momento = v2[0];
                        j = (int) v2[1];
                    } else {
                        j = salta(dati, j, t2);
                        if (j < 0) break;
                    }
                }
                if (id != null) { identificativi[quanti] = id; quando[quanti] = momento; quanti++; }
            }
            i += quanto;
        }

        // Dal piu' recente: chi apre i preferiti cerca quello che ha salvato
        // ieri, non quello del 2015. Sono poche centinaia di elementi e
        // l'ordinamento a bolle basta e avanza; ordinare a mano evita di tirare
        // dentro Arrays.sort con un comparatore e una classe in piu'.
        for (int a = 0; a < quanti - 1; a++) {
            for (int b = 0; b < quanti - 1 - a; b++) {
                if (quando[b] < quando[b + 1]) {
                    long q = quando[b]; quando[b] = quando[b + 1]; quando[b + 1] = q;
                    String s = identificativi[b];
                    identificativi[b] = identificativi[b + 1];
                    identificativi[b + 1] = s;
                }
            }
        }

        String[] esatti = new String[quanti];
        System.arraycopy(identificativi, 0, esatti, 0, quanti);
        return esatti;
    }

    /** Un intero a lunghezza variabile: {valore, dove si riprende}. */
    private static long[] varint(byte[] b, int i) {
        long valore = 0;
        int spostamento = 0;
        while (i < b.length) {
            int x = b[i++] & 0xFF;
            valore |= ((long) (x & 0x7F)) << spostamento;
            if ((x & 0x80) == 0) return new long[] { valore, i };
            spostamento += 7;
            if (spostamento > 63) return null;
        }
        return null;
    }

    /** Salta un campo di cui non ci interessa il contenuto. */
    private static int salta(byte[] b, int i, int tipo) {
        if (tipo == 0) { long[] v = varint(b, i); return v == null ? -1 : (int) v[1]; }
        if (tipo == 5) return i + 4;
        if (tipo == 1) return i + 8;
        return -1;
    }

    private static String esadecimale(byte[] b, int da, int quanti) {
        final char[] cifre = "0123456789abcdef".toCharArray();
        char[] fuori = new char[quanti * 2];
        for (int i = 0; i < quanti; i++) {
            int v = b[da + i] & 0xFF;
            fuori[i * 2] = cifre[v >> 4];
            fuori[i * 2 + 1] = cifre[v & 0xF];
        }
        return new String(fuori);
    }

    /** L'inverso di {@link #gid(String)}: da esadecimale a base62, com'e'
     *  scritto negli URI di Spotify. */
    private static String base62(String esa) {
        final String alfabeto = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        BigInteger n = new BigInteger(esa, 16), base = BigInteger.valueOf(62);
        StringBuilder s = new StringBuilder();
        while (n.signum() > 0) {
            BigInteger[] q = n.divideAndRemainder(base);
            s.append(alfabeto.charAt(q[1].intValue()));
            n = q[0];
        }
        while (s.length() < 22) s.append('0');
        return s.reverse().toString();
    }

    /**
     * Da base62 a esadecimale.
     *
     * Gli URI di Spotify portano un identificativo in base62; l'API dei
     * metadati vuole gli stessi sedici byte scritti in esadecimale. Sono due
     * scritture dello stesso numero, e BigInteger sa passare dall'una all'altra
     * senza che si debba scrivere una divisione lunga a mano.
     */
    private static String gid(String base62) {
        final String alfabeto = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        BigInteger n = BigInteger.ZERO, base = BigInteger.valueOf(62);
        for (int i = 0; i < base62.length(); i++) {
            int cifra = alfabeto.indexOf(base62.charAt(i));
            if (cifra < 0) return base62;
            n = n.multiply(base).add(BigInteger.valueOf(cifra));
        }
        String esa = n.toString(16);
        while (esa.length() < 32) esa = "0" + esa;
        return esa;
    }

    private void pubblicaTracce(int mio, Traccia[] lette, int quante) {
        if (mio != giroApertura) return;
        Traccia[] esatte = new Traccia[quante];
        System.arraycopy(lette, 0, esatte, 0, quante);
        tracce = esatte;
        avvisa();
    }

    private void daiTracce(int mio, String perche) {
        if (mio != giroApertura) return;
        notaTracce = perche;
        avvisa();
    }

    // ---- la copia su disco --------------------------------------------------

    private void rileggiDalDisco() {
        JSONObject dati = Archivio.leggi(contesto, FILE);
        if (dati == null) return;
        quando = dati.optLong("quando", 0);
        JSONArray elenco = dati.optJSONArray("voci");
        if (elenco == null) return;
        Voce[] lette = new Voce[elenco.length()];
        int quante = 0;
        for (int i = 0; i < elenco.length(); i++) {
            JSONObject v = elenco.optJSONObject(i);
            if (v == null) continue;
            String uri = v.optString("uri", null);
            String nome = v.optString("nome", null);
            if (uri == null || nome == null) continue;
            lette[quante++] = new Voce(uri, nome,
                    v.isNull("immagine") ? null : v.optString("immagine", null),
                    v.optBoolean("elencabile", true));
        }
        if (quante == 0) return;
        Voce[] esatte = new Voce[quante];
        System.arraycopy(lette, 0, esatte, 0, quante);
        voci = esatte;
    }

    private void salvaSulDisco() {
        try {
            JSONArray elenco = new JSONArray();
            for (Voce v : voci) {
                JSONObject o = new JSONObject();
                o.put("uri", v.uri);
                o.put("nome", v.nome);
                if (v.immagine != null) o.put("immagine", v.immagine);
                if (!v.elencabile) o.put("elencabile", false);
                elenco.put(o);
            }
            JSONObject dati = new JSONObject();
            dati.put("quando", quando);
            dati.put("voci", elenco);
            Archivio.scrivi(contesto, FILE, dati);
        } catch (Exception e) {
            Log.w(TAG, "elenco non salvato", e);
        }
    }

    private void dai(String spiegazione) {
        nota = spiegazione;
        avvisa();
    }

    /** Le playlist finte della vetrina: vedi {@link Musica#vetrina}. */
    private volatile boolean vetrina;

    void vetrina(Voce[] finte) {
        vetrina = true;
        voci = finte;
        nota = null;
        quando = System.currentTimeMillis();
        avvisa();
    }

    private void avvisa() {
        final Ascolto a = ascolto;
        if (a == null) return;
        ui.post(new Runnable() {
            @Override public void run() { a.preferitiCambiati(); }
        });
    }
}
