package dev.casa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Le copertine: dalla rete, ridotte, con un tetto, e una copia su disco.
 *
 * E' il gemello di {@link Loghi} per le immagini che non stanno nell'APK.
 * Valgono le stesse due regole, per le stesse ragioni.
 *
 * <b>Il tetto.</b> Una copertina di Spotify e' 300x300 o 640x640: la seconda in
 * ARGB_8888 e' 1,6 MB <b>l'una</b>. Qui si decodificano alla misura in cui
 * verranno disegnate - {@code inSampleSize} arrotondato per difetto a una
 * potenza di due, l'unico caso in cui BitmapFactory e' esatto - e stanno in una
 * LruCache da un megabyte e mezzo in tutto. Su un tablet da 1 GB una griglia di
 * copertine e' esattamente il modo in cui un'interfaccia bella diventa un
 * apparecchio che si ferma.
 *
 * <b>La copia su disco.</b> Le copertine non cambiano mai: riscaricarle a ogni
 * apertura della sezione sarebbe mezzo megabyte di rete per niente, e la
 * griglia comparirebbe vuota per un secondo tutte le volte. Vanno in
 * {@code cacheDir/copertine}, che e' la cartella che il sistema puo' svuotare
 * da solo quando serve spazio - cioe' esattamente il comportamento giusto per
 * roba che si sa riscaricare.
 */
public final class Copertine {

    private static final String TAG = "Casa.Copertine";

    /** Un megabyte e mezzo: dodici copertine da 180 pixel, o due grandi e il
     *  resto piccole. Oltre non servono, perche' non ce ne stanno altre a
     *  schermo. */
    private static final int TETTO_KB = 1536;

    /** Quante ne restano sul disco. Sotto i due megabyte in tutto. */
    private static final int SU_DISCO = 40;

    public interface Pronta { void copertinaArrivata(); }

    private final File cartella;
    private final Handler lavoro;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    /** Quelle gia' in viaggio: senza, una griglia che si ridisegna dieci volte
     *  al secondo chiederebbe dieci volte la stessa immagine. */
    private final Set<String> inVolo = new HashSet<String>();

    private Pronta avviso;

    public Copertine(Context c) {
        cartella = new File(c.getCacheDir(), "copertine");
        if (!cartella.exists() && !cartella.mkdirs()) {
            Log.w(TAG, "cartella delle copertine non creata");
        }
        cache = new LruCache<String, Bitmap>(TETTO_KB) {
            @Override protected int sizeOf(String chiave, Bitmap b) {
                return Math.max(1, b.getByteCount() / 1024);
            }
        };
        HandlerThread t = new HandlerThread("Casa-copertine");
        t.start();
        lavoro = new Handler(t.getLooper());
    }

    /** Chi ridisegnare quando ne arriva una. */
    public void setAvviso(Pronta p) { avviso = p; }

    /**
     * La copertina, oppure null <b>per adesso</b>.
     *
     * null vuol dire "non ce l'ho ancora": la si va a prendere, e quando c'e'
     * arriva l'avviso. Chi disegna intanto mette il suo ripiego - un rettangolo
     * con la tinta della sezione - che e' meglio di un buco.
     */
    public Bitmap prendi(String indirizzo, int lato) {
        if (indirizzo == null || indirizzo.length() == 0) return null;
        String chiave = chiave(indirizzo, lato);
        Bitmap b = cache.get(chiave);
        if (b != null && !b.isRecycled()) return b;
        cerca(indirizzo, lato, chiave);
        return null;
    }

    private void cerca(final String indirizzo, final int lato, final String chiave) {
        synchronized (inVolo) {
            if (inVolo.contains(chiave)) return;
            inVolo.add(chiave);
        }
        lavoro.post(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try {
                    File copia = new File(cartella, nomeFile(indirizzo));
                    if (!copia.exists()) scarica(indirizzo, copia);
                    if (copia.exists()) b = decodifica(copia, lato);
                } catch (Throwable t) {
                    Log.w(TAG, "copertina non presa", t);
                }
                if (b != null) cache.put(chiave, b);
                synchronized (inVolo) { inVolo.remove(chiave); }
                if (b == null) return;
                final Pronta p = avviso;
                if (p == null) return;
                ui.post(new Runnable() {
                    @Override public void run() { p.copertinaArrivata(); }
                });
            }
        });
    }

    private void scarica(String indirizzo, File dove) throws Exception {
        HttpURLConnection c = null;
        InputStream in = null;
        FileOutputStream out = null;
        File temporaneo = new File(dove.getAbsolutePath() + ".tmp");
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return;
            in = c.getInputStream();
            out = new FileOutputStream(temporaneo);
            byte[] pezzo = new byte[8192];
            int n;
            while ((n = in.read(pezzo)) > 0) out.write(pezzo, 0, n);
            out.flush();
            out.close();
            out = null;
            if (!temporaneo.renameTo(dove)) temporaneo.delete();
            faiPosto();
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignorata) { }
            if (out != null) try { out.close(); } catch (Exception ignorata) { }
            if (temporaneo.exists()) temporaneo.delete();
            if (c != null) c.disconnect();
        }
    }

    /** Sopra il tetto si butta la piu' vecchia. Una alla volta basta: se ne
     *  aggiunge una per volta. */
    private void faiPosto() {
        File[] tutte = cartella.listFiles();
        if (tutte == null || tutte.length <= SU_DISCO) return;
        File piuVecchia = null;
        for (File f : tutte) {
            if (piuVecchia == null || f.lastModified() < piuVecchia.lastModified()) piuVecchia = f;
        }
        if (piuVecchia != null && !piuVecchia.delete()) {
            Log.w(TAG, "vecchia copertina non cancellata");
        }
    }

    /**
     * Decodifica alla misura giusta, in due passate.
     *
     * La prima con {@code inJustDecodeBounds} legge solo l'intestazione del
     * PNG - qualche decina di byte, nessuna memoria - e dice quanto e' grande.
     * Solo allora si sa di quanto ridurre. Decodificare a piena misura e poi
     * rimpicciolire vorrebbe dire allocare 1,6 MB per tenerne 130 KB, e su
     * questo tablet quello e' il modo in cui si arriva a un OutOfMemory.
     */
    private static Bitmap decodifica(File f, int lato) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int riduzione = 1;
        while (lato > 0 && o.outWidth / (riduzione * 2) >= lato) riduzione *= 2;

        BitmapFactory.Options vera = new BitmapFactory.Options();
        vera.inSampleSize = riduzione;
        // RGB_565: una copertina non ha trasparenza, e cosi' costa la meta'.
        // La banda del gradiente si vedrebbe su un cielo sfumato, non su una
        // copertina dentro un riquadro da tre centimetri.
        vera.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), vera);
    }

    private static String chiave(String indirizzo, int lato) {
        return lato + "|" + indirizzo;
    }

    /** Un nome di file da un indirizzo, senza dipendere da come e' fatto
     *  l'indirizzo. */
    private static String nomeFile(String indirizzo) {
        return Integer.toHexString(indirizzo.hashCode()) + "_" + indirizzo.length();
    }

    /** Butta le immagini in memoria. La copia su disco resta: e' li' apposta. */
    public void svuota() {
        cache.evictAll();
    }

    /**
     * Il colore di una copertina, per tingerci attorno.
     *
     * Si rimpicciolisce a otto per otto e si fa la media: sessantaquattro
     * pixel invece di centomila, e il risultato e' lo stesso, perche' quello
     * che si cerca e' proprio la media. Poi passa da
     * {@link Tinte#addomestica(int)}, che porta saturazione e luminosita' in
     * un intervallo utilizzabile: una copertina nera darebbe un nero, e un
     * pannello nero su fondo nero non e' un pannello.
     *
     * Si chiama una volta per brano, non a ogni fotogramma.
     */
    public static int tintaDi(Bitmap copertina, int riserva) {
        if (copertina == null || copertina.isRecycled()) return riserva;
        Bitmap piccola = null;
        try {
            piccola = Bitmap.createScaledBitmap(copertina, 8, 8, true);
            long r = 0, g = 0, b = 0;
            int[] pixel = new int[64];
            piccola.getPixels(pixel, 0, 8, 0, 0, 8, 8);
            for (int p : pixel) {
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
            }
            int medio = 0xFF000000 | ((int) (r / 64) << 16) | ((int) (g / 64) << 8) | (int) (b / 64);
            return Tinte.addomestica(medio);
        } catch (Throwable t) {
            return riserva;
        } finally {
            if (piccola != null && piccola != copertina) piccola.recycle();
        }
    }
}
