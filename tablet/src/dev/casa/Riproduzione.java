package dev.casa;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import java.util.List;

/**
 * Cosa sta suonando su un'altra app, e come comandarla.
 *
 * <b>Il problema che risolve.</b> La Home sapeva solo della radio, che e' roba
 * sua. Aperta Spotify, la scheda "ora in riproduzione" continuava a dire
 * "niente in riproduzione" mentre dagli altoparlanti usciva della musica - e
 * per mettere in pausa bisognava andare in Spotify. Un apparecchio da muro che
 * non sa dire cosa sta suonando in casa sua ha poco senso.
 *
 * <b>Come si fa.</b> Qualunque app che suoni pubblica una
 * {@link android.media.session.MediaSession}: e' lo stesso meccanismo dei tasti
 * media delle cuffie, ed e' esattamente quello che usa Casa per la propria
 * radio. Da fuori si legge con {@link MediaSessionManager#getActiveSessions},
 * che da' titolo, artista, stato e i comandi - per <b>qualunque</b> app, senza
 * una riga di codice specifica per Spotify.
 *
 * <b>Le due strade, e perche' ce ne vogliono due.</b>
 *
 * <ol>
 *   <li><b>Con il permesso di ascoltare le notifiche</b> si sa tutto: titolo,
 *       artista, se sta suonando davvero. Il permesso si concede da adb -
 *       {@code tools/permesso-notifiche.ps1} - e la prova d'identita' e'
 *       {@link AscoltoNotifiche}. Non e' concedibile da codice: nemmeno da
 *       device owner.
 *   <li><b>Senza</b>, i tasti restano vivi lo stesso:
 *       {@link AudioManager#dispatchMediaKeyEvent} manda play, pausa, avanti e
 *       indietro a chi sta suonando e <b>non chiede nessun permesso</b>. Non si
 *       sa cosa suona, ma lo si comanda.
 * </ol>
 *
 * L'interfaccia degrada di un gradino invece di svuotarsi, che e' la regola che
 * questo progetto si e' dato altrove.
 *
 * <b>La nostra radio non conta come "altra app".</b> Anche Casa pubblica una
 * sessione, e senza escluderla la Home si sarebbe vista riflessa: avrebbe
 * mostrato se stessa come sorgente esterna e i tasti avrebbero comandato la
 * radio passando per il giro lungo.
 */
public final class Riproduzione {

    private static final String TAG = "Casa.Riproduzione";

    /** I nomi da scrivere in cima alla scheda. Un elenco corto e scritto a mano
     *  invece di chiedere l'etichetta a PackageManager: quella carica le
     *  risorse dell'altra app, e qui si e' su 1 GB. Chi non e' in elenco resta
     *  senza nome, che e' meglio di "com.qualcosa.player". */
    private static String nomeApp(String pacchetto) {
        if (pacchetto == null) return null;
        if (pacchetto.equals("com.spotify.music"))       return "SPOTIFY";
        if (pacchetto.equals("com.netflix.mediaclient")) return "NETFLIX";
        if (pacchetto.equals("com.android.chrome"))      return "CHROME";
        return null;
    }

    /** Chi vuole essere avvisato che e' cambiato qualcosa. */
    public interface Spia { void suRiproduzione(); }

    private final Context contesto;
    private final Spia spia;
    private final AudioManager audio;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private MediaSessionManager gestore;
    private ComponentName noi;

    /** La sessione di turno, di un'app che non e' Casa. */
    private MediaController controllore;

    private String titolo, artista, pacchetto;
    private boolean suona;

    /** false quando manca il permesso di ascoltare le notifiche: allora si sa
     *  solo comandare, non leggere. */
    private boolean sappiamoCosaSuona;

    private final MediaSessionManager.OnActiveSessionsChangedListener cambio =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override public void onActiveSessionsChanged(List<MediaController> sessioni) {
            scegli(sessioni);
        }
    };

    /** Titolo e stato cambiano <b>dentro</b> la stessa sessione: senza questa
     *  callback la scheda resterebbe ferma alla canzone di quando Spotify si e'
     *  aperta. */
    private final MediaController.Callback ascolto = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { leggi(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { leggi(); }
        @Override public void onSessionDestroyed() { stacca(); avvisa(); }
    };

    public Riproduzione(Context c, Spia spia) {
        this.contesto = c.getApplicationContext();
        this.spia = spia;
        this.audio = (AudioManager) contesto.getSystemService(Context.AUDIO_SERVICE);

        gestore = (MediaSessionManager) contesto.getSystemService(Context.MEDIA_SESSION_SERVICE);
        noi = new ComponentName(contesto, AscoltoNotifiche.class);
        aggancia();
    }

    /**
     * Prova a mettersi in ascolto. Se il permesso non c'e', si va avanti con i
     * soli tasti.
     *
     * Si puo' richiamare: e' quello che serve dopo aver concesso il permesso da
     * adb, o al rientro da fuori scena.
     */
    public void aggancia() {
        if (gestore == null) return;
        try {
            gestore.addOnActiveSessionsChangedListener(cambio, noi, ui);
            scegli(gestore.getActiveSessions(noi));
            sappiamoCosaSuona = true;
        } catch (SecurityException e) {
            // Nessun permesso: e' un caso previsto, non un guasto. Si scrive
            // una riga sola e non si riprova in ciclo.
            sappiamoCosaSuona = false;
            Log.i(TAG, "senza permesso notifiche: solo comandi, niente titolo");
        } catch (Exception e) {
            sappiamoCosaSuona = false;
            Log.w(TAG, "sessioni non leggibili", e);
        }
    }

    public void chiudi() {
        stacca();
        if (gestore != null && sappiamoCosaSuona) {
            try { gestore.removeOnActiveSessionsChangedListener(cambio); } catch (Exception ignorato) { }
        }
    }

    // ---- chi comanda -------------------------------------------------------

    /**
     * Sceglie la sessione da mostrare fra quelle attive.
     *
     * Chi sta suonando adesso vince su chi e' solo in pausa: con Spotify aperta
     * e la radio spenta ci sono due sessioni ferme, e prendere la prima
     * dell'elenco voleva dire mostrare a caso.
     */
    private void scegli(List<MediaController> sessioni) {
        MediaController migliore = null;
        if (sessioni != null) {
            for (MediaController c : sessioni) {
                if (c == null) continue;
                // La nostra radio la Home la conosce gia' per conto suo.
                if (contesto.getPackageName().equals(c.getPackageName())) continue;
                if (migliore == null) { migliore = c; continue; }
                if (staSuonando(c) && !staSuonando(migliore)) migliore = c;
            }
        }

        if (migliore == controllore) { leggi(); return; }
        stacca();
        controllore = migliore;
        if (controllore != null) controllore.registerCallback(ascolto, ui);
        leggi();
    }

    private void stacca() {
        if (controllore == null) return;
        try { controllore.unregisterCallback(ascolto); } catch (Exception ignorato) { }
        controllore = null;
    }

    private static boolean staSuonando(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Rilegge titolo, artista e stato, e avvisa solo se e' cambiato qualcosa:
     *  le callback arrivano a raffica mentre una traccia parte, e ridisegnare a
     *  ogni colpo su questo hardware si vede. */
    private void leggi() {
        String t = null, a = null, p = null;
        boolean s = false;

        if (controllore != null) {
            p = controllore.getPackageName();
            s = staSuonando(controllore);
            MediaMetadata m = controllore.getMetadata();
            if (m != null) {
                t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
                a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
        }

        if (uguali(t, titolo) && uguali(a, artista) && uguali(p, pacchetto) && s == suona) return;
        titolo = t; artista = a; pacchetto = p; suona = s;
        avvisa();
    }

    private void avvisa() {
        if (spia != null) spia.suRiproduzione();
    }

    private static boolean uguali(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ---- quello che la Home chiede -----------------------------------------

    /** true quando c'e' un'altra app che ha una sessione media: e' il segnale
     *  che la scheda deve diventare il suo telecomando invece che quello della
     *  radio. */
    public boolean ceQualcuno() { return controllore != null; }

    public boolean staSuonando() { return suona; }

    /** Il titolo del brano, oppure null - anche quando c'e' una sessione, se
     *  manca il permesso o l'app non pubblica i metadati. */
    public String titolo() { return titolo; }

    public String artista() { return artista; }

    /** "SPOTIFY", "NETFLIX", ... da scrivere in cima alla scheda. null per chi
     *  non e' in elenco. */
    public String nome() { return nomeApp(pacchetto); }

    /** false quando manca il permesso: la Home lo dice invece di far finta che
     *  non stia suonando niente. */
    public boolean sappiamoCosaSuona() { return sappiamoCosaSuona; }

    // ---- il telecomando ----------------------------------------------------

    /**
     * I comandi passano per la sessione quando ce l'abbiamo, e per i tasti
     * media quando no.
     *
     * I tasti sono la strada che funziona sempre: e' la stessa che usano le
     * cuffie con il pulsante, non chiede permessi, e la consegna chi ha il
     * focus audio - cioe' proprio l'app che sta suonando.
     */
    public void pausaRiprendi() {
        if (controllore != null) {
            if (suona) controllore.getTransportControls().pause();
            else controllore.getTransportControls().play();
            return;
        }
        tasto(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public void successivo() {
        if (controllore != null) { controllore.getTransportControls().skipToNext(); return; }
        tasto(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public void precedente() {
        if (controllore != null) { controllore.getTransportControls().skipToPrevious(); return; }
        tasto(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    /** Un tasto media vuole la coppia premuto/rilasciato: mandando solo il
     *  primo, molte app non fanno niente. */
    private void tasto(int codice) {
        if (audio == null) return;
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, codice));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, codice));
        } catch (Exception e) {
            Log.w(TAG, "tasto media non consegnato", e);
        }
    }
}
