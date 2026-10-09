package dev.casa.telefono;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copertine di Spotify e loghi delle radio, con due cache: in memoria e su
 * disco.
 *
 * <b>Le copertine si scaricano dal telefono, non dal tablet.</b> Gli indirizzi
 * sono di {@code i.scdn.co}, pubblici: farle passare dal tablet vorrebbe dire
 * far lavorare un Cortex-A7 per un telefono che ha una rete migliore della
 * sua. I <b>loghi</b> invece stanno dentro l'APK del tablet, e si chiedono a
 * lui una volta: poi restano sul disco del telefono.
 *
 * Il telefono e' recente e ha memoria da spendere: la cache e' un ottavo di
 * quello che la macchina virtuale concede, che su un telefono di adesso sono
 * decine di megabyte.
 */
final class Immagini {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService fili = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> cache;
    private final File cartella;
    private final Tablet tablet;

    Immagini(Context c, Tablet tablet) {
        this.tablet = tablet;
        cartella = new File(c.getCacheDir(), "immagini");
        cartella.mkdirs();
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(kb) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
        };
    }

    /**
     * Mette nell'ImageView l'immagine di {@code indirizzo}: un https, oppure
     * {@code logo:<chiave>} per il logo di una stazione. Se nel frattempo
     * l'ImageView e' stata riusata per un'altra immagine, quella che arriva
     * in ritardo non la tocca.
     */
    void carica(final ImageView v, final String indirizzo, final int latoPx) {
        if (indirizzo == null || indirizzo.length() == 0) {
            v.setTag(null);
            v.setImageDrawable(null);
            return;
        }
        if (indirizzo.equals(v.getTag()) && v.getDrawable() != null) return;
        v.setTag(indirizzo);
        final String chiave = indirizzo + "@" + latoPx;
        Bitmap b = cache.get(chiave);
        if (b != null) {
            v.setImageBitmap(b);
            return;
        }
        v.setImageDrawable(null);
        fili.execute(new Runnable() {
            @Override public void run() {
                final Bitmap letta = leggi(indirizzo, latoPx);
                if (letta == null) return;
                cache.put(chiave, letta);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!indirizzo.equals(v.getTag())) return;
                        v.setImageBitmap(letta);
                        v.setAlpha(0f);
                        v.animate().alpha(1f).setDuration(180).start();
                    }
                });
            }
        });
    }

    private Bitmap leggi(String indirizzo, int lato) {
        try {
            File f = new File(cartella, Integer.toHexString(indirizzo.hashCode()) + "_" + indirizzo.length());
            byte[] dati = f.exists() ? leggiFile(f) : null;
            if (dati == null) {
                dati = indirizzo.startsWith("logo:") ? logo(indirizzo.substring(5)) : scarica(indirizzo);
                if (dati == null) return null;
                FileOutputStream out = new FileOutputStream(f);
                out.write(dati);
                out.close();
            }
            BitmapFactory.Options misure = new BitmapFactory.Options();
            misure.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(dati, 0, dati.length, misure);
            int campione = 1;
            while (misure.outWidth / (campione * 2) >= lato && misure.outHeight / (campione * 2) >= lato) campione *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = campione;
            return BitmapFactory.decodeByteArray(dati, 0, dati.length, o);
        } catch (Throwable t) {
            return null;
        }
    }

    private byte[] logo(String chiave) throws Exception {
        Tablet.Risposta r = tablet.get("/radio/logo/" + Uri.encode(chiave), 8000);
        if (!r.ok()) return null;
        return Base64.decode(r.json.optString("png", ""), Base64.DEFAULT);
    }

    private static byte[] scarica(String indirizzo) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(indirizzo).openConnection();
        try {
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return null;
            return tutto(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static byte[] leggiFile(File f) throws Exception {
        return tutto(new FileInputStream(f));
    }

    private static byte[] tutto(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream(32 * 1024);
            byte[] pezzo = new byte[8192];
            int n;
            while ((n = in.read(pezzo)) > 0) b.write(pezzo, 0, n);
            return b.toByteArray();
        } finally {
            in.close();
        }
    }
}
