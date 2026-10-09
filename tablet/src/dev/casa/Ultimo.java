package dev.casa;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

/**
 * L'ultima cosa che e' uscita dall'altoparlante, per poterla far ripartire.
 *
 * <h3>Il problema</h3>
 *
 * I tre tasti della Home comandano quello che sta suonando <b>adesso</b>. A
 * silenzio - la mattina, dopo un riavvio, dopo aver premuto stop - non c'era
 * niente da comandare: la scheda diceva "niente in riproduzione" e il tasto di
 * mezzo accendeva la prima stazione dell'elenco, che non e' quasi mai quella
 * che si stava ascoltando. Chi passa davanti al tablet e vuole rimettere
 * quello di prima doveva entrare in Radio o in Spotify e ricominciare da capo.
 *
 * Un apparecchio da muro con un tasto play deve fare quello che fa il tasto
 * play di qualunque apparecchio: <b>rimettere l'ultima cosa</b>.
 *
 * <h3>Che cosa si ricorda</h3>
 *
 * Due sole forme, perche' due sono le sorgenti di casa:
 *
 * <ul>
 *   <li><b>radio</b>: la chiave della stazione. Non l'indirizzo del flusso -
 *       quello cambia quando il PC riscrive l'elenco, e ripartire su un
 *       indirizzo morto sarebbe peggio che non ripartire. La chiave e' anche
 *       il nome con cui la si chiama a voce, quindi e' l'unica cosa che
 *       sopravvive a un cambio di configurazione.
 *   <li><b>musica</b>: il contesto (la playlist, l'album) <b>e</b> il brano.
 *       Servono tutti e due: col solo brano, finito quello finisce la musica;
 *       col solo contesto si riparte dalla prima traccia invece che da quella
 *       che si stava ascoltando. E' la stessa coppia che usa
 *       {@link Musica#suona(String, String)}.
 * </ul>
 *
 * Il titolo si porta dietro perche' la scheda deve poter scrivere <i>che
 * cosa</i> ripartirebbe prima che qualcuno prema: un tasto play sopra una riga
 * vuota e' un tasto di cui non ci si fida.
 *
 * <h3>Perche' un file e non le SharedPreferences</h3>
 *
 * Perche' {@link Archivio} c'e' gia', scrive in modo atomico, e questo dato ha
 * la stessa vita degli altri - deve sopravvivere a un riavvio e non deve
 * sopravvivere a una disinstallazione. Una seconda strada per salvare quattro
 * campi sarebbe una seconda strada da ricordarsi.
 */
public final class Ultimo {

    private static final String TAG = "Casa";
    private static final String FILE = "ultimo.json";

    public static final int NIENTE = 0;
    public static final int RADIO  = 1;
    public static final int MUSICA = 2;

    public final int cosa;
    /** Per la radio: la chiave della stazione. */
    public final String chiave;
    /** Per la musica: il contesto (playlist o album) e il brano dentro. */
    public final String contesto, brano;
    /** Come si scrive a schermo, e la riga sotto. */
    public final String titolo, nota;

    private Ultimo(int cosa, String chiave, String contesto, String brano,
                   String titolo, String nota) {
        this.cosa = cosa; this.chiave = chiave;
        this.contesto = contesto; this.brano = brano;
        this.titolo = titolo; this.nota = nota;
    }

    public boolean ceQualcosa() { return cosa != NIENTE && titolo != null; }

    // ---- lettura -----------------------------------------------------------

    /**
     * Quello che c'e' adesso, senza toccare il disco piu' di una volta.
     *
     * La chiama chi disegna, cioe' sessanta volte al secondo nel caso
     * peggiore: leggere un file a ogni fotogramma per quattro campi sarebbe
     * assurdo. Il file si legge la prima volta e poi basta - da li' in poi la
     * copia in memoria la aggiornano i due che scrivono, che sono gli unici
     * che possono cambiarla.
     */
    public static Ultimo attuale(Context c) {
        Ultimo u = attuale;
        if (u == null) {
            u = leggi(c);
            attuale = u;
        }
        return u;
    }

    private static volatile Ultimo attuale;

    /** Quello che c'era scritto, o un « niente » se non c'e' mai stato niente. */
    public static Ultimo leggi(Context c) {
        JSONObject o = Archivio.leggi(c, FILE);
        if (o == null) return vuoto();
        String tipo = o.optString("cosa", "");
        if ("radio".equals(tipo)) {
            return new Ultimo(RADIO, nonVuoto(o, "chiave"), null, null,
                    nonVuoto(o, "titolo"), null);
        }
        if ("musica".equals(tipo)) {
            return new Ultimo(MUSICA, null, nonVuoto(o, "contesto"), nonVuoto(o, "brano"),
                    nonVuoto(o, "titolo"), nonVuoto(o, "nota"));
        }
        return vuoto();
    }

    public static Ultimo vuoto() {
        return new Ultimo(NIENTE, null, null, null, null, null);
    }

    // ---- scrittura ---------------------------------------------------------

    /**
     * Si e' accesa una stazione.
     *
     * <b>Si scrive quando parte, non quando si spegne</b>, e non e' lo stesso:
     * spegnendo il tablet dalla presa - che e' come finisce davvero una serata
     * - il momento dello spegnimento non arriva mai.
     */
    public static Ultimo radio(Context c, Radio.Stazione s) {
        if (s == null) return leggi(c);
        Ultimo u = new Ultimo(RADIO, s.chiave, null, null, s.nome, null);
        salva(c, u);
        return u;
    }

    /** Sta suonando un brano di Spotify, dentro il suo contesto. */
    public static Ultimo musica(Context c, String contesto, String brano,
                                String titolo, String artista) {
        if (titolo == null) return leggi(c);
        Ultimo u = new Ultimo(MUSICA, null, contesto, brano, titolo, artista);
        salva(c, u);
        return u;
    }

    /**
     * Se quello che sta per essere scritto e' gia' scritto.
     *
     * Serve a chi riceve lo stato del demone di Spotify due volte al secondo:
     * senza questo controllo lo stesso brano si riscriverebbe sul disco per
     * tutta la sua durata, cioe' quattrocento scritture per una canzone.
     */
    public boolean loStesso(int cosa, String chiaveOBrano) {
        if (this.cosa != cosa) return false;
        String mio = cosa == RADIO ? chiave : brano;
        return mio != null && mio.equals(chiaveOBrano);
    }

    private static void salva(Context c, Ultimo u) {
        // Prima in memoria e poi sul disco: chi disegna deve vedere la cosa
        // nuova subito, anche se la scrittura del file dovesse fallire.
        attuale = u;
        try {
            JSONObject o = new JSONObject();
            o.put("cosa", u.cosa == RADIO ? "radio" : "musica");
            if (u.chiave != null)   o.put("chiave", u.chiave);
            if (u.contesto != null) o.put("contesto", u.contesto);
            if (u.brano != null)    o.put("brano", u.brano);
            if (u.titolo != null)   o.put("titolo", u.titolo);
            if (u.nota != null)     o.put("nota", u.nota);
            Archivio.scrivi(c, FILE, o);
        } catch (Exception e) {
            // Non riuscire a ricordarsi l'ultima canzone non e' un guasto: si
            // perde una comodita', non si rompe niente.
            Log.w(TAG, "ultimo: non ho potuto scrivere " + FILE + ": " + e.getMessage());
        }
    }

    private static String nonVuoto(JSONObject o, String nome) {
        String s = o.optString(nome, "");
        return s.length() == 0 ? null : s;
    }
}
