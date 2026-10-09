package dev.casa;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Enumeration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Le radici che questo Android non ha.
 *
 * <h3>Il sintomo</h3>
 *
 * Qualunque richiesta a un sito con certificato Let's Encrypt muore qui:
 *
 * <pre>
 *   javax.net.ssl.SSLHandshakeException:
 *     java.security.cert.CertPathValidatorException:
 *       Trust anchor for certification path not found.
 * </pre>
 *
 * Non e' un errore di rete, non e' un firewall, e non e' il codice: e' che il
 * tablet <b>non conosce chi ha firmato</b>. E siccome il messaggio parla di
 * catene di certificati e non di date, la prima idea che viene e' che sia
 * sbagliata l'URL o il codice - mentre lo stesso identico codice contro
 * {@code spclient.wg.spotify.com}, che ha un certificato DigiCert, funziona.
 *
 * <h3>Il perche', ed e' una data</h3>
 *
 * Questo tablet e' <b>Android 7.0</b>. Le radici di Let's Encrypt sono entrate
 * nel magazzino di Android a partire da <b>7.1.1</b>, e i certificati odierni
 * non sono nemmeno piu' quelli di allora: dal settembre 2025 Let's Encrypt
 * firma con le radici <i>Root YR</i> e <i>Root YE</i>, che sono nate anni dopo
 * l'ultimo aggiornamento che questo ROM abbia mai visto. Nessun magazzino di
 * sistema le conterra' mai: il ROM e' quello e non si aggiorna (vedi
 * {@code docs/cambiare-sistema.md}).
 *
 * Non c'e' niente da « aggiustare » lato nostro se non <b>portarsele dietro</b>.
 * Sono file pubblici, si scaricano da letsencrypt.org, e stanno in
 * {@code assets/radici/} - quattro certificati per una decina di kilobyte.
 *
 * <h3>Perche' si aggiunge invece di sostituire</h3>
 *
 * Il modo corto sarebbe un magazzino con dentro solo le nostre quattro radici.
 * Funzionerebbe oggi e si romperebbe il giorno in cui uno di questi siti cambia
 * autorita': la connessione fallirebbe con lo stesso identico messaggio, e la
 * ricerca ricomincerebbe da capo. Qui invece si <b>copiano prima tutte le
 * radici di sistema</b> ({@code AndroidCAStore}) e poi si aggiungono le nostre:
 * quello che il tablet gia' sa fare continua a farlo, e le quattro nuove sono
 * un di piu'.
 *
 * <b>E non si spegne il controllo.</b> La strada che si trova per prima
 * cercando questo errore e' un {@code TrustManager} che dice sempre di si', e
 * sarebbe un tablet appeso al muro che si beve qualunque certificato di
 * chiunque stia sulla stessa rete. Qui il controllo resta quello vero: si
 * aggiungono quattro firme, non si toglie la serratura.
 */
public final class Fiducia {

    private static final String TAG = MainActivity.TAG;

    /** La cartella degli asset con i certificati in formato PEM. */
    private static final String RADICI = "radici";

    private Fiducia() {}

    /** Si costruisce una volta per processo: leggere quattro certificati e
     *  ricopiare centocinquanta radici di sistema costa qualche decina di
     *  millisecondi, e il risultato non cambia mai. */
    private static volatile SSLSocketFactory presa;
    private static volatile boolean giaProvata;

    /**
     * Applica le nostre radici a una connessione, se e' in HTTPS.
     *
     * Da chiamare prima di {@code getResponseCode()}. Se qualcosa e' andato
     * storto nel costruire il magazzino, non fa niente: la connessione usa il
     * magazzino di sistema e semmai fallisce come prima, che e' meglio di non
     * partire affatto.
     */
    public static void applica(Context c, HttpURLConnection connessione) {
        if (!(connessione instanceof HttpsURLConnection)) return;
        SSLSocketFactory f = presa(c);
        if (f != null) ((HttpsURLConnection) connessione).setSSLSocketFactory(f);
    }

    private static SSLSocketFactory presa(Context c) {
        if (giaProvata) return presa;
        synchronized (Fiducia.class) {
            if (giaProvata) return presa;
            giaProvata = true;
            presa = costruisci(c);
            return presa;
        }
    }

    private static SSLSocketFactory costruisci(Context c) {
        try {
            KeyStore nostro = KeyStore.getInstance(KeyStore.getDefaultType());
            nostro.load(null, null);

            int diSistema = copiaLeRadiciDiSistema(nostro);
            int nuove = copiaLeNostre(c, nostro);
            if (nuove == 0) {
                // Senza le nostre non c'e' niente da aggiungere, e vale il
                // magazzino di sistema: meglio non toccare la connessione.
                Log.w(TAG, "Fiducia: nessuna radice in assets/" + RADICI);
                return null;
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(nostro);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            Log.i(TAG, "Fiducia: " + diSistema + " radici di sistema + " + nuove + " nostre");
            return ctx.getSocketFactory();
        } catch (Throwable t) {
            Log.w(TAG, "Fiducia: magazzino non costruito, resta quello di sistema", t);
            return null;
        }
    }

    private static int copiaLeRadiciDiSistema(KeyStore dentro) {
        int quante = 0;
        try {
            KeyStore sistema = KeyStore.getInstance("AndroidCAStore");
            sistema.load(null, null);
            for (Enumeration<String> nomi = sistema.aliases(); nomi.hasMoreElements(); ) {
                String nome = nomi.nextElement();
                Certificate cert = sistema.getCertificate(nome);
                if (cert == null) continue;
                dentro.setCertificateEntry(nome, cert);
                quante++;
            }
        } catch (Exception e) {
            // Si va avanti con le sole nostre: e' peggio di prima per gli altri
            // siti, ma questo magazzino lo usa solo chi lo chiede.
            Log.w(TAG, "Fiducia: radici di sistema non copiate", e);
        }
        return quante;
    }

    private static int copiaLeNostre(Context c, KeyStore dentro) throws Exception {
        String[] nomi = c.getAssets().list(RADICI);
        if (nomi == null) return 0;
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        int quante = 0;
        for (String nome : nomi) {
            InputStream in = null;
            try {
                in = c.getAssets().open(RADICI + "/" + nome);
                dentro.setCertificateEntry("casa-" + nome, cf.generateCertificate(in));
                quante++;
            } catch (Exception e) {
                // Un certificato storto non deve far cadere gli altri tre.
                Log.w(TAG, "Fiducia: " + nome + " non si legge", e);
            } finally {
                if (in != null) try { in.close(); } catch (Exception ignorata) { }
            }
        }
        return quante;
    }
}
