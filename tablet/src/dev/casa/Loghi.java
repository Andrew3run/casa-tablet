package dev.casa;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

/**
 * I loghi delle stazioni, decodificati a meta' misura e con un tetto.
 *
 * <b>Il tetto e' il punto.</b> Ventidue loghi da 128x128 in ARGB_8888 fanno
 * 1,4 MB, e su un tablet da 1 GB su cui devono girare anche Spotify e Netflix
 * quella e' memoria tolta a loro. Qui si decodificano a <b>64 pixel</b>
 * (inSampleSize 2, che BitmapFactory fa in modo esatto perche' e' una potenza
 * di due) e si tengono in una LruCache da 400 KB: entrano tutti quelli che
 * servono per una schermata, e i piu' vecchi escono da soli.
 *
 * A 213 dpi un logo da 64 pixel dentro un disco da settanta e' morbido di un
 * capello, e a un metro di distanza - che e' la distanza a cui si guarda un
 * apparecchio appeso - non si vede.
 *
 * {@link #svuota()} la chiama onTrimMemory: ricaricare un PNG dalle risorse
 * costa qualche millisecondo, tenerlo costa per sempre.
 */
public final class Loghi {

    private static final String TAG = "Casa.Loghi";

    /** 400 KB: circa venticinque loghi da 64x64. Sopra questo non serve a
     *  niente, perche' non ce ne sono altri da mostrare. */
    private static final int TETTO_KB = 400;

    /** 128 diviso 2. Non si scende a 32: li' le scritte dentro i loghi
     *  diventano una sbavatura. */
    private static final int RIDUZIONE = 2;

    private final Resources risorse;
    private final LruCache<Integer, Bitmap> cache;

    public Loghi(Context c) {
        risorse = c.getResources();
        cache = new LruCache<Integer, Bitmap>(TETTO_KB) {
            @Override protected int sizeOf(Integer chiave, Bitmap b) {
                return Math.max(1, b.getByteCount() / 1024);
            }
        };
    }

    /**
     * Il logo, o null se quella stazione non ne ha uno.
     *
     * null non e' un errore ed e' previsto: tre stazioni su ventidue il logo
     * non ce l'hanno in un formato utilizzabile, e per quelle chi disegna
     * ripiega sulla sigla.
     */
    public Bitmap prendi(int risorsa) {
        if (risorsa == 0) return null;
        Bitmap b = cache.get(risorsa);
        if (b != null && !b.isRecycled()) return b;

        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = RIDUZIONE;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;   // i loghi hanno il trasparente
            b = BitmapFactory.decodeResource(risorse, risorsa, o);
            if (b != null) cache.put(risorsa, b);
            return b;
        } catch (Throwable t) {
            // Un logo che non si decodifica non deve portarsi dietro la
            // schermata: si torna null, e si vede la sigla.
            Log.w(TAG, "logo " + risorsa + " non decodificato", t);
            return null;
        }
    }

    /** Butta tutto. La chiama onTrimMemory quando Casa non e' sullo schermo. */
    public void svuota() {
        cache.evictAll();
    }

    /** Quanti kilobyte sta tenendo, per le misure. */
    public int quantiKB() { return cache.size(); }
}
