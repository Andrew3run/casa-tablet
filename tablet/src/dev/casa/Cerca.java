package dev.casa;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Cercare un brano o un artista per nome.
 *
 * <b>Perche' non basta il token del demone.</b> Tutto il resto della sezione
 * Musica - playlist, brani, preferiti, copertine - passa dall'API interna di
 * Spotify con il token della sessione di go-librespot. La ricerca no, e non per
 * un permesso mancante: nel protocollo di Spotify la ricerca vive dentro il
 * <b>canale Mercury</b> della sessione ({@code hm://searchview/km/v4/search/…},
 * si legge nel {@code SearchManager} di librespot-java), non sull'API HTTPS - e
 * go-librespot quel canale non lo espone. Sul mirror HTTPS quell'indirizzo
 * risponde 400 col corpo vuoto in ogni forma provata, e la Web API pubblica con
 * il token di sessione risponde 429 a qualunque richiesta: non ha quota.
 *
 * <b>Quindi si passa dalla porta principale.</b> La ricerca e' l'unica cosa
 * dell'API pubblica che <b>non</b> ha bisogno dell'account di nessuno: basta
 * un'applicazione registrata e il flusso <i>client credentials</i>. Si registra
 * una volta su developer.spotify.com, si mettono le due chiavi in
 * {@code spotify.json} dentro i file di Casa, e da li' in poi la ricerca
 * funziona con una quota vera invece che con un 429.
 *
 * <pre>
 *   tools\chiave-spotify.ps1 -Id ... -Segreto ...
 *   {"client_id": "...", "client_secret": "..."}   in spotify.json
 * </pre>
 *
 * <b>Senza il file non e' un guasto</b>: {@link #pronta()} torna false e chi
 * chiede se lo sente dire. Tutto il resto della sezione continua a funzionare,
 * che e' la regola di Casa - l'interfaccia degrada di un gradino invece di
 * svuotarsi.
 */
public final class Cerca {

    private static final String TAG = "Casa.Cerca";

    /** Il file con le due chiavi, dentro getFilesDir(). */
    private static final String FILE = "spotify.json";

    /** Quanto dura il gettone dell'applicazione. Spotify ne da' uno da un'ora;
     *  si rinnova con un minuto di anticipo per non farsi cogliere a meta'
     *  richiesta. */
    private static final long DURATA = 59 * 60 * 1000L;

    /** Che cosa e' un risultato. Un brano si suona; un artista e un album si
     *  aprono, e dentro c'e' un altro elenco. L'intestazione non e' niente di
     *  tutto questo: e' il titolo di una sezione dentro l'elenco. */
    public static final int BRANO = 0, ARTISTA = 1, ALBUM = 2, INTESTAZIONE = 3,
                            DISCOGRAFIA = 4, PLAYLIST = 5;

    /** Un risultato: quel che basta a mostrarlo, ad aprirlo e a suonarlo. */
    public static final class Trovato {
        public final String uri, titolo, sotto, immagine;
        public final int tipo;
        Trovato(String uri, String titolo, String sotto, String immagine, int tipo) {
            this.uri = uri; this.titolo = titolo; this.sotto = sotto;
            this.immagine = immagine; this.tipo = tipo;
        }
        Trovato(String uri, String titolo, String sotto, String immagine) {
            this(uri, titolo, sotto, immagine, BRANO);
        }
        public boolean apribile() {
            return tipo == ARTISTA || tipo == ALBUM || tipo == DISCOGRAFIA;
        }
        /** Una playlist si suona intera, come un album. */
        public boolean suonabile() { return tipo == BRANO || tipo == PLAYLIST; }
        public boolean intestazione() { return tipo == INTESTAZIONE; }
    }

    public interface Esito {
        /** L'elenco, oppure null col motivo. */
        void trovati(Trovato[] risultati, String perche);
    }

    private static final Trovato[] NIENTE = new Trovato[0];

    private final Context contesto;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler lavoro;

    /** La musica: serve per il <b>token della sessione</b>, che e' un'altra
     *  cosa dalla chiave dell'applicazione. La pagina di un artista si prende
     *  con quello, dall'API interna, e infatti funziona anche senza chiave. */
    private Musica musica;

    private String id, segreto;
    private String gettone;
    private long gettoneFinoA;

    /** L'esito dell'ultima richiesta HTTP, per poterlo scrivere a schermo:
     *  « non risponde » da solo non dice niente a chi deve capire perche'. */
    private volatile int ultimoCodice;

    public Cerca(Context c) {
        contesto = c.getApplicationContext();
        HandlerThread t = new HandlerThread("Casa-cerca");
        t.start();
        lavoro = new Handler(t.getLooper());
        leggiChiavi();
    }

    private void leggiChiavi() {
        JSONObject d = Archivio.leggi(contesto, FILE);
        if (d == null) {
            Log.i(TAG, "nessuna chiave: la ricerca resta spenta");
            return;
        }
        id = d.optString("client_id", null);
        segreto = d.optString("client_secret", null);
        if (id == null || segreto == null || id.length() == 0 || segreto.length() == 0) {
            id = segreto = null;
            Log.w(TAG, FILE + " senza client_id o client_secret");
        }
    }

    /** Rilegge il file: serve dopo averlo appena messo, senza riavviare Casa. */
    public void rileggi() { leggiChiavi(); }

    public void setMusica(Musica m) { musica = m; }

    public boolean pronta() { return id != null && segreto != null; }

    /**
     * Cerca brani e artisti, e risponde sul thread dell'interfaccia.
     *
     * Prima i brani: chi dice un nome a Casa quasi sempre vuole sentire quella
     * canzone, non aprire la pagina di chi l'ha scritta. L'artista viene dopo,
     * e serve a « metti i Placebo », che invece e' una richiesta di radio.
     */
    public void cerca(final String frase, final Esito esito) {
        if (frase == null || frase.trim().length() == 0) {
            rispondi(esito, null, "non ho capito cosa cercare");
            return;
        }
        if (!pronta()) {
            rispondi(esito, null, "per cercare serve la chiave: vedi docs/musica.md");
            return;
        }
        lavoro.post(new Runnable() {
            @Override public void run() {
                try {
                    String t = gettone();
                    if (t == null) { rispondi(esito, null, "la chiave non e' stata accettata"); return; }
                    String risposta = chiedi("https://api.spotify.com/v1/search?q="
                            + URLEncoder.encode(frase, "UTF-8")
                            + "&type=track,artist&limit=8&market=IT", t);
                    if (risposta == null) {
                        rispondi(esito, null, "la ricerca non risponde (" + ultimoCodice + ")");
                        return;
                    }
                    Trovato[] fuori = leggi(new JSONObject(risposta));
                    if (fuori.length == 0) rispondi(esito, NIENTE, "non ho trovato niente");
                    else rispondi(esito, fuori, null);
                } catch (Exception e) {
                    Log.w(TAG, "ricerca fallita", e);
                    rispondi(esito, null, "la ricerca non risponde");
                }
            }
        });
    }

    /**
     * La playlist <b>This Is</b> di un artista.
     *
     * <h3>Cos'e', e perche' e' la risposta giusta a "metti Levante"</h3>
     *
     * Spotify mantiene per ogni artista di un certo peso una playlist
     * editoriale che si chiama sempre allo stesso modo - « This Is Levante »,
     * « This Is Battisti » - con dentro le sue canzoni piu' ascoltate, tenuta
     * aggiornata da loro. Chi dice "metti Levante" non vuole una sola canzone
     * e non vuole aprire una pagina: vuole <b>sentire Levante</b>, cioe'
     * esattamente quella.
     *
     * <h3>Perche' si cerca fra le playlist e non fra gli artisti</h3>
     *
     * Con la Web API l'alternativa sarebbe {@code /artists/{id}/top-tracks},
     * che alle applicazioni registrate oggi risponde <b>403</b> (vedi la nota
     * su {@link #paginaArtista}). La ricerca fra le playlist invece risponde,
     * e queste playlist hanno un nome prevedibile.
     *
     * <h3>Le due condizioni che tengono fuori le imitazioni</h3>
     *
     * "This Is" e' un titolo che chiunque puo' dare a una sua playlist, e la
     * ricerca ne restituisce parecchie. Si tiene solo quella che:
     * <ul>
     * <li>e' <b>di Spotify</b> ({@code owner.id == "spotify"}) - le editoriali
     *     sono tutte sue, e questo da solo scarta le playlist degli utenti;</li>
     * <li>ha il <b>nome dell'artista dentro il titolo</b> - o cercando
     *     "Levante" si finirebbe con "This Is Battisti" solo perche' era il
     *     primo risultato.</li>
     * </ul>
     */
    public void questoE(final String artista, final Esito esito) {
        final String chi = artista == null ? "" : artista.trim();
        if (chi.length() == 0) { rispondi(esito, null, "non ho capito di chi"); return; }
        if (!pronta()) {
            rispondi(esito, null, "per cercare serve la chiave: vedi docs/musica.md");
            return;
        }
        lavoro.post(new Runnable() {
            @Override public void run() {
                try {
                    String t = gettone();
                    if (t == null) { rispondi(esito, null, "la chiave non e' stata accettata"); return; }
                    String risposta = chiedi("https://api.spotify.com/v1/search?q="
                            + URLEncoder.encode("This Is " + chi, "UTF-8")
                            + "&type=playlist&limit=10&market=IT", t);
                    if (risposta == null) {
                        rispondi(esito, null, "la ricerca non risponde (" + ultimoCodice + ")");
                        return;
                    }
                    Trovato v = questaE(new JSONObject(risposta), chi);
                    if (v == null) rispondi(esito, NIENTE, null);
                    else rispondi(esito, new Trovato[] { v }, null);
                } catch (Exception e) {
                    Log.w(TAG, "ricerca della playlist This Is fallita", e);
                    rispondi(esito, null, "la ricerca non risponde");
                }
            }
        });
    }

    /** Fra i risultati, la sola playlist editoriale di Spotify per questo
     *  artista. Null se non c'e'. */
    private static Trovato questaE(JSONObject risposta, String artista) {
        JSONObject playlist = risposta.optJSONObject("playlists");
        JSONArray elenco = playlist != null ? playlist.optJSONArray("items") : null;
        if (elenco == null) return null;
        String chi = artista.toLowerCase(java.util.Locale.ITALIAN);

        for (int i = 0; i < elenco.length(); i++) {
            JSONObject p = elenco.optJSONObject(i);
            // La ricerca di Spotify infila dei null in mezzo agli items quando
            // una playlist e' sparita: saltarli e' obbligatorio, non cortesia.
            if (p == null) continue;
            JSONObject proprietario = p.optJSONObject("owner");
            if (proprietario == null) continue;
            if (!"spotify".equalsIgnoreCase(proprietario.optString("id", ""))) continue;

            String nome = p.optString("name", "");
            String basso = nome.toLowerCase(java.util.Locale.ITALIAN);
            if (!basso.startsWith("this is")) continue;
            if (!basso.contains(chi)) continue;

            String uri = p.optString("uri", "");
            if (uri.length() == 0) continue;
            return new Trovato(uri, nome, "playlist di Spotify", immagine(p), PLAYLIST);
        }
        return null;
    }

    private static Trovato[] leggi(JSONObject risposta) {
        Trovato[] fuori = new Trovato[16];
        int quanti = 0;

        JSONObject brani = risposta.optJSONObject("tracks");
        JSONArray elenco = brani != null ? brani.optJSONArray("items") : null;
        if (elenco != null) {
            for (int i = 0; i < elenco.length() && quanti < fuori.length; i++) {
                JSONObject b = elenco.optJSONObject(i);
                if (b == null) continue;
                String uri = b.optString("uri", null);
                String nome = b.optString("name", null);
                if (uri == null || nome == null) continue;
                StringBuilder chi = new StringBuilder();
                JSONArray artisti = b.optJSONArray("artists");
                if (artisti != null) {
                    for (int k = 0; k < artisti.length(); k++) {
                        JSONObject a = artisti.optJSONObject(k);
                        if (a == null) continue;
                        if (chi.length() > 0) chi.append(", ");
                        chi.append(a.optString("name", ""));
                    }
                }
                fuori[quanti++] = new Trovato(uri, nome, chi.toString(),
                        immagine(b.optJSONObject("album")));
            }
        }

        JSONObject artisti = risposta.optJSONObject("artists");
        JSONArray elenco2 = artisti != null ? artisti.optJSONArray("items") : null;
        if (elenco2 != null) {
            for (int i = 0; i < elenco2.length() && quanti < fuori.length && i < 3; i++) {
                JSONObject a = elenco2.optJSONObject(i);
                if (a == null) continue;
                String uri = a.optString("uri", null);
                String nome = a.optString("name", null);
                if (uri == null || nome == null) continue;
                fuori[quanti++] = new Trovato(uri, nome, "artista", immagine(a), ARTISTA);
            }
        }

        Trovato[] esatti = new Trovato[quanti];
        System.arraycopy(fuori, 0, esatti, 0, quanti);
        return esatti;
    }

    /**
     * La pagina di un artista, <b>quella vera</b>.
     *
     * Non e' fatta con la Web API: e' {@code artistview/v1/artist/{id}}
     * sull'API interna, chiesta con il <b>token della sessione</b> - quindi
     * funziona anche senza la chiave dell'applicazione. Torna la stessa pagina
     * che si vede sul telefono, gia' divisa in sezioni: « Popular » con le
     * canzoni piu' ascoltate, « Popular releases » con gli album, e altro.
     *
     * <b>Perche' non la Web API.</b> Li' le canzoni piu' ascoltate
     * ({@code /artists/{id}/top-tracks}) rispondono <b>403</b> alle
     * applicazioni registrate oggi, e gli album arrivano tutti in un mucchio
     * senza sezioni. Questa strada da' di piu' e costa una richiesta sola.
     *
     * La struttura e' un elenco piatto di righe: ognuna dice che componente e'
     * ({@code glue:sectionHeader}, {@code glue:entityRow}, ...), il suo titolo,
     * e in {@code metadata.uri} che cosa suona. Si tengono le intestazioni e le
     * righe che hanno un URI di brano o di album; tutto il resto - caroselli,
     * bottoni, date dei concerti - si salta.
     */
    public void paginaArtista(String artistaUri, Esito esito) {
        pagina(artistaUri, esito, false);
    }

    /**
     * La discografia intera: quello che sul telefono si apre con « mostra
     * tutto ».
     *
     * E' un'altra pagina della stessa forma - {@code .../artist/{id}/releases}
     * - divisa in « ultima uscita », « album » e « singoli »: si legge con lo
     * stesso codice, cambia solo l'indirizzo.
     */
    public void discografia(String artistaUri, Esito esito) {
        pagina(artistaUri, esito, true);
    }

    private void pagina(final String artistaUri, final Esito esito, final boolean tutto) {
        if (artistaUri == null || musica == null) {
            rispondi(esito, null, "l'artista non e' raggiungibile");
            return;
        }
        musica.gettone(new Musica.Gettone() {
            @Override public void arrivato(final String sessione) {
                if (sessione == null) {
                    rispondi(esito, null, "la sessione non e' pronta");
                    return;
                }
                lavoro.post(new Runnable() {
                    @Override public void run() { leggiPagina(artistaUri, sessione, esito, tutto); }
                });
            }
        });
    }

    private void leggiPagina(String artistaUri, String sessione, Esito esito, boolean tutto) {
        try {
            String id = artistaUri.substring(artistaUri.lastIndexOf(':') + 1);
            // L'URI del bottone « mostra tutto » finisce per :releases: qui
            // conta solo l'identificativo, il resto lo dice il percorso.
            if (id.equals("releases")) {
                String senza = artistaUri.substring(0, artistaUri.lastIndexOf(':'));
                id = senza.substring(senza.lastIndexOf(':') + 1);
            }
            String risposta = chiedi("https://spclient.wg.spotify.com/artistview/v1/artist/"
                                     + id + (tutto ? "/releases" : ""), sessione);
            if (risposta == null) {
                rispondi(esito, null, "questo artista non risponde (" + ultimoCodice + ")");
                return;
            }
            JSONArray corpo = new JSONObject(risposta).optJSONArray("body");
            if (corpo == null) { rispondi(esito, NIENTE, "pagina vuota"); return; }

            // Larga il doppio delle righe: i caroselli portano dentro i loro
            // figli, e ognuno diventa una riga.
            Trovato[] fuori = new Trovato[corpo.length() * 2 + 64];
            int quanti = 0;
            String intestazioneInAttesa = null;
            for (int i = 0; i < corpo.length() && quanti < fuori.length - 2; i++) {
                JSONObject riga = corpo.optJSONObject(i);
                if (riga == null) continue;
                JSONObject comp = riga.optJSONObject("component");
                String quale = comp != null ? comp.optString("id", "") : "";
                JSONObject testo = riga.optJSONObject("text");
                String titolo = testo != null ? testo.optString("title", null) : null;
                JSONObject meta = riga.optJSONObject("metadata");
                String uri = meta != null ? meta.optString("uri", null) : null;

                if (quale.contains("sectionHeader")) {
                    // Si tiene da parte: un'intestazione senza righe sotto -
                    // « Live Events », che qui non si sa mostrare - non deve
                    // comparire da sola.
                    intestazioneInAttesa = titolo;
                    continue;
                }

                // Il bottone « mostra tutto »: porta alla discografia intera.
                if (uri != null && uri.endsWith(":releases")) {
                    if (intestazioneInAttesa != null) {
                        fuori[quanti++] = new Trovato(null, italiano(intestazioneInAttesa),
                                                      null, null, INTESTAZIONE);
                        intestazioneInAttesa = null;
                    }
                    fuori[quanti++] = new Trovato(uri, "Mostra tutte le uscite",
                                                  "album, singoli e altro", null, DISCOGRAFIA);
                    continue;
                }

                // Un carosello - « Compare in », « Anche a chi ascolta » - non
                // ha un URI suo: porta i figli, e ognuno e' una riga come le
                // altre. Senza questo pezzo meta' della pagina spariva.
                JSONArray figli = riga.optJSONArray("children");
                if (figli != null && figli.length() > 0) {
                    boolean primo = true;
                    for (int k = 0; k < figli.length() && quanti < fuori.length - 2; k++) {
                        JSONObject f = figli.optJSONObject(k);
                        if (f == null) continue;
                        Trovato t = daRiga(f);
                        if (t == null) continue;
                        if (primo) {
                            String nome = titolo != null ? titolo : intestazioneInAttesa;
                            if (nome != null) {
                                fuori[quanti++] = new Trovato(null, italiano(nome),
                                                              null, null, INTESTAZIONE);
                            }
                            intestazioneInAttesa = null;
                            primo = false;
                        }
                        fuori[quanti++] = t;
                    }
                    continue;
                }

                Trovato t = daRiga(riga);
                if (t == null) continue;
                if (intestazioneInAttesa != null) {
                    fuori[quanti++] = new Trovato(null, italiano(intestazioneInAttesa),
                                                  null, null, INTESTAZIONE);
                    intestazioneInAttesa = null;
                }
                fuori[quanti++] = t;
            }
            Trovato[] esatti = new Trovato[quanti];
            System.arraycopy(fuori, 0, esatti, 0, quanti);
            rispondi(esito, esatti, quanti == 0 ? "niente da mostrare" : null);
        } catch (Exception e) {
            Log.w(TAG, "pagina artista non letta", e);
            rispondi(esito, null, "questo artista non risponde");
        }
    }

    /**
     * Una riga della pagina, se e' qualcosa che si sa mostrare.
     *
     * Brani, album, artisti e playlist: tutto il resto - date di concerti,
     * bottoni, righe senza URI - torna null e viene saltato.
     */
    private static Trovato daRiga(JSONObject riga) {
        JSONObject meta = riga.optJSONObject("metadata");
        String uri = meta != null ? meta.optString("uri", null) : null;
        JSONObject testo = riga.optJSONObject("text");
        String titolo = testo != null ? testo.optString("title", null) : null;
        if (uri == null || titolo == null) return null;

        int tipo;
        if (uri.startsWith("spotify:track:")) tipo = BRANO;
        else if (uri.startsWith("spotify:album:")) tipo = ALBUM;
        else if (uri.startsWith("spotify:artist:")) tipo = ARTISTA;
        else if (uri.contains(":playlist:")) {
            // Le playlist arrivano nella forma vecchia
            // spotify:user:qualcuno:playlist:xxx; il lettore vuole quella
            // corta, e sono la stessa cosa.
            uri = "spotify:playlist:" + uri.substring(uri.lastIndexOf(':') + 1);
            tipo = BRANO;                 // si suona, non si apre
        } else return null;

        String sotto = testo.optString("subtitle", "");
        if (sotto.length() == 0) sotto = testo.optString("description", "");
        if (tipo == BRANO && uri.startsWith("spotify:track:")) sotto = ascolti(sotto);
        if (tipo == ARTISTA && sotto.length() == 0) sotto = "artista";

        JSONObject immagini = riga.optJSONObject("images");
        JSONObject principale = immagini != null ? immagini.optJSONObject("main") : null;
        return new Trovato(uri, titolo, sotto,
                           principale != null ? principale.optString("uri", null) : null, tipo);
    }

    /** I titoli delle sezioni arrivano in inglese: quelli che compaiono
     *  davvero si traducono, il resto passa com'e'. */
    private static String italiano(String titolo) {
        if (titolo == null) return null;
        if (titolo.equalsIgnoreCase("Popular")) return "Piu' ascoltate";
        if (titolo.equalsIgnoreCase("Popular releases")) return "Uscite";
        if (titolo.equalsIgnoreCase("Artist Pick")) return "Scelta dall'artista";
        if (titolo.equalsIgnoreCase("Discography")) return "Discografia";
        if (titolo.equalsIgnoreCase("Albums")) return "Album";
        if (titolo.equalsIgnoreCase("Singles and EPs")) return "Singoli ed EP";
        if (titolo.equalsIgnoreCase("Singles")) return "Singoli";
        if (titolo.equalsIgnoreCase("Latest release")) return "Ultima uscita";
        if (titolo.equalsIgnoreCase("Compilations")) return "Raccolte";
        if (titolo.equalsIgnoreCase("Appears on")) return "Compare in";
        if (titolo.equalsIgnoreCase("Fans also like")) return "Piace anche a chi lo ascolta";
        if (titolo.equalsIgnoreCase("Artist Playlists")) return "Playlist dell'artista";
        if (titolo.toLowerCase().startsWith("featuring")) return "Con " + titolo.substring(10);
        return titolo;
    }

    /** Il sottotitolo di un brano popolare e' il numero di ascolti, secco:
     *  « 11651324 ». Scritto cosi' non lo legge nessuno. */
    private static String ascolti(String grezzo) {
        if (grezzo == null) return "";
        String cifre = grezzo.replace(".", "").replace(",", "").trim();
        try {
            long n = Long.parseLong(cifre);
            if (n >= 1000000) return (n / 100000 / 10f) + " milioni di ascolti";
            if (n >= 1000) return (n / 1000) + " mila ascolti";
            return n + " ascolti";
        } catch (NumberFormatException nonEUnNumero) {
            return grezzo;
        }
    }

    /**
     * Gli album di un artista: e' la sua pagina.
     *
     * <b>Non le canzoni piu' ascoltate</b>, che sarebbero la cosa piu' ovvia:
     * {@code /artists/{id}/top-tracks} risponde <b>403</b> alle applicazioni
     * registrate oggi - e' fra gli indirizzi che Spotify ha chiuso ai nuovi
     * client a fine 2024, insieme a « artisti correlati » e « consigliati ».
     * Gli album invece rispondono, e per entrare nella pagina di qualcuno sono
     * anche l'elenco piu' onesto: la sua roba in ordine, non quella che va di
     * moda.
     */
    public void albumDi(final String artistaUri, final Esito esito) {
        chiediElenco(artistaUri, esito, true);
    }

    /** I brani di un album. */
    public void braniDi(final String albumUri, final Esito esito) {
        chiediElenco(albumUri, esito, false);
    }

    private void chiediElenco(final String uri, final Esito esito, final boolean album) {
        if (uri == null || !pronta()) {
            rispondi(esito, null, "per aprire serve la chiave");
            return;
        }
        lavoro.post(new Runnable() {
            @Override public void run() {
                try {
                    String t = gettone();
                    if (t == null) { rispondi(esito, null, "la chiave non e' stata accettata"); return; }
                    String id = uri.substring(uri.lastIndexOf(':') + 1);
                    // <b>Dieci album e non ventiquattro.</b> Con limit=20 o
                    // piu' questo indirizzo risponde 400 « Invalid limit » -
                    // il tetto per le applicazioni registrate oggi e' basso, e
                    // non e' quello scritto nella documentazione. Si scopre
                    // solo leggendo il corpo dell'errore: e' il motivo per cui
                    // adesso il codice HTTP finisce nel messaggio a schermo.
                    String indirizzo = album
                            ? "https://api.spotify.com/v1/artists/" + id
                              + "/albums?include_groups=album%2Csingle&market=IT&limit=10"
                            : "https://api.spotify.com/v1/albums/" + id
                              + "/tracks?market=IT&limit=40";
                    String risposta = chiedi(indirizzo, t);
                    if (risposta == null) {
                        rispondi(esito, null, (album ? "questo artista non risponde"
                                                     : "questo album non risponde")
                                              + " (" + ultimoCodice + ")");
                        return;
                    }
                    JSONArray elenco = new JSONObject(risposta).optJSONArray("items");
                    if (elenco == null || elenco.length() == 0) {
                        rispondi(esito, NIENTE, "qui dentro non c'e' niente");
                        return;
                    }
                    Trovato[] fuori = new Trovato[elenco.length()];
                    int quanti = 0;
                    for (int i = 0; i < elenco.length(); i++) {
                        JSONObject o = elenco.optJSONObject(i);
                        if (o == null) continue;
                        String u = o.optString("uri", null), nome = o.optString("name", null);
                        if (u == null || nome == null) continue;
                        if (album) {
                            String anno = o.optString("release_date", "");
                            if (anno.length() > 4) anno = anno.substring(0, 4);
                            String genere = o.optString("album_type", "album");
                            fuori[quanti++] = new Trovato(u, nome,
                                    anno.length() > 0 ? genere + " · " + anno : genere,
                                    immagine(o), ALBUM);
                        } else {
                            StringBuilder chi = new StringBuilder();
                            JSONArray artisti = o.optJSONArray("artists");
                            if (artisti != null) {
                                for (int k = 0; k < artisti.length(); k++) {
                                    JSONObject a = artisti.optJSONObject(k);
                                    if (a == null) continue;
                                    if (chi.length() > 0) chi.append(", ");
                                    chi.append(a.optString("name", ""));
                                }
                            }
                            // I brani di un album non portano la copertina:
                            // e' la stessa per tutti, ed e' gia' in cima.
                            fuori[quanti++] = new Trovato(u, nome, chi.toString(), null);
                        }
                    }
                    Trovato[] esatti = new Trovato[quanti];
                    System.arraycopy(fuori, 0, esatti, 0, quanti);
                    rispondi(esito, esatti, quanti == 0 ? "niente da sentire" : null);
                } catch (Exception e) {
                    Log.w(TAG, "elenco non letto", e);
                    rispondi(esito, null, "non risponde");
                }
            }
        });
    }

    /** L'ultima immagine dell'elenco: Spotify le mette dalla piu' grande alla
     *  piu' piccola, e qui una riga e' alta cinquanta pixel. */
    private static String immagine(JSONObject chi) {
        if (chi == null) return null;
        JSONArray immagini = chi.optJSONArray("images");
        if (immagini == null || immagini.length() == 0) return null;
        JSONObject ultima = immagini.optJSONObject(immagini.length() - 1);
        return ultima != null ? ultima.optString("url", null) : null;
    }

    /**
     * Il gettone dell'applicazione, tenuto finche' vale.
     *
     * E' il flusso <i>client credentials</i>: nessun utente, nessun consenso,
     * nessun browser - due chiavi e un POST. E' anche il motivo per cui questa
     * strada e' pulita: la ricerca nel catalogo non e' roba di nessun account.
     */
    private String gettone() {
        if (gettone != null && System.currentTimeMillis() < gettoneFinoA) return gettone;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://accounts.spotify.com/api/token").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            c.setDoOutput(true);
            String basica = Base64.encodeToString((id + ":" + segreto).getBytes("UTF-8"),
                                                  Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + basica);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] corpo = "grant_type=client_credentials".getBytes("UTF-8");
            c.setFixedLengthStreamingMode(corpo.length);
            OutputStream o = c.getOutputStream();
            o.write(corpo);
            o.flush();
            o.close();

            if (c.getResponseCode() != 200) {
                Log.w(TAG, "chiave rifiutata: " + c.getResponseCode());
                return null;
            }
            JSONObject d = new JSONObject(Musica.tutto(c.getInputStream()));
            gettone = d.optString("access_token", null);
            gettoneFinoA = System.currentTimeMillis() + DURATA;
            return gettone;
        } catch (Exception e) {
            Log.w(TAG, "gettone non ottenuto", e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * Una richiesta, con <b>un secondo tentativo se il guasto e' del
     * server</b>.
     *
     * L'API interna ogni tanto risponde 503 e alla richiesta dopo funziona:
     * capitato aprendo due volte di fila la stessa pagina di un artista. Un
     * errore 4xx invece e' colpa nostra e ritentarlo sarebbe solo un'altra
     * richiesta sbagliata.
     */
    private String chiedi(String indirizzo, String gettone) {
        String risposta = unaVolta(indirizzo, gettone);
        if (risposta == null && ultimoCodice >= 500) {
            try { Thread.sleep(600); } catch (InterruptedException ignorata) { }
            Log.i(TAG, "riprovo dopo " + ultimoCodice);
            risposta = unaVolta(indirizzo, gettone);
        }
        return risposta;
    }

    private String unaVolta(String indirizzo, String gettone) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Authorization", "Bearer " + gettone);
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            int esito = c.getResponseCode();
            ultimoCodice = esito;
            if (esito != 200) {
                Log.w(TAG, "HTTP " + esito + " da " + indirizzo);
                return null;
            }
            return Musica.tutto(c.getInputStream());
        } catch (Exception e) {
            ultimoCodice = -1;
            Log.w(TAG, "richiesta fallita", e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void rispondi(final Esito esito, final Trovato[] risultati, final String perche) {
        if (esito == null) return;
        ui.post(new Runnable() {
            @Override public void run() { esito.trovati(risultati, perche); }
        });
    }
}
