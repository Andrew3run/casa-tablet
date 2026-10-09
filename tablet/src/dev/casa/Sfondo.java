package dev.casa;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.Log;

import org.json.JSONObject;

import java.util.Calendar;

/**
 * Lo sfondo di Casa: lo compone, e se lo prende.
 *
 * <b>Perche' esiste questa classe.</b> Il tablet e' di seconda mano, e lo
 * sfondo di sistema e' ancora quello del ROM del proprietario precedente: si
 * rivede a ogni animazione di apertura di un'app, dietro le finestre non
 * opache, e sul blocco. Nascondere il problema mettendo un rettangolo dentro
 * Casa non serve, perche' quel pixel non e' dentro Casa - e' del sistema.
 * L'unica cura e' <b>possedere lo sfondo</b>, e da API 24 si puo' fare da soli,
 * sia quello di sistema sia quello di blocco.
 *
 * <b>Una funzione sola per due usi.</b> {@link #componi} produce sia l'immagine
 * che il vetro sfoca per l'interfaccia, sia quella che finisce come wallpaper:
 * non possono divergere, perche' e' la stessa funzione. L'unica differenza e'
 * un parametro - il wallpaper si genera un po' piu' scuro, perche' sopra ci
 * finiscono le icone bianche del sistema e le finestre di dialogo, che hanno
 * bisogno di contrasto.
 *
 * <b>Niente file.</b> L'immagine e' codice: due o tre aloni larghi su un fondo
 * scuro. Un JPEG da mezzo megabyte nell'APK darebbe lo stesso risultato e
 * costerebbe mezzo megabyte, e soprattutto non potrebbe cambiare con l'ora del
 * giorno.
 */
public final class Sfondo {

    private static final String TAG  = "Casa.Sfondo";
    private static final String FILE = "sfondo.json";

    /** Cambiala quando cambia il disegno: fa rigenerare lo sfondo a tutti i
     *  tablet che avevano gia' quello vecchio. */
    private static final int VERSIONE = 4;

    /** Il wallpaper viene un 15% piu' scuro dell'immagine dell'interfaccia. */
    private static final float SOTTO_LE_ICONE = 0.85f;

    private Sfondo() {}

    // ---- le quattro ore del giorno ---------------------------------------

    /**
     * Quattro varianti dello stesso disegno. Non e' vezzo: un apparecchio
     * acceso ventiquattr'ore che alle tre di notte ha la stessa luce delle tre
     * del pomeriggio sembra spento e in avaria. Cambiare gli aloni costa 200 ms
     * ogni poche ore.
     */
    public static final int ALBA = 0, GIORNO = 1, SERA = 2, NOTTE = 3;

    /**
     * I tre aloni di ogni variante.
     *
     * La prima versione era troppo scura e troppo smorta: sul tablet vero
     * veniva una macchia marrone su nero, e di "colorato" non c'era niente.
     * Questi sono saturi davvero, e il colore si vede - resta comunque un
     * fondale, perche' gli aloni sono sfocati a centoventotto pixel e non si
     * leggono mai come forme.
     */
    private static final int[][] ALONI = {
        // alba: blu freddo che cede all'oro, con un viola in mezzo
        { 0xFF3B6FD4, 0xFFD98A45, 0xFF7A4E9E },
        // giorno: blu vivo e il verde della radio, il piu' chiaro dei quattro
        { 0xFF2F7BE8, 0xFF23A37A, 0xFF3E5FA8 },
        // sera: magenta e ambra, la luce delle lampade accese
        { 0xFF8E3F86, 0xFFD07A32, 0xFF2B3F7A },
        // notte: solo blu profondi, niente che chiami l'occhio al buio
        { 0xFF1E3E7A, 0xFF2A2F6B, 0xFF14284A }
    };

    private static final String[] NOMI = { "alba", "giorno", "sera", "notte" };

    /** Che ora e' per lo sfondo. */
    public static int variante(long quando) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(quando);
        int ora = c.get(Calendar.HOUR_OF_DAY);
        if (ora >= 5  && ora < 9)  return ALBA;
        if (ora >= 9  && ora < 17) return GIORNO;
        if (ora >= 17 && ora < 22) return SERA;
        return NOTTE;
    }

    /** Come si chiama lo sfondo che dovrebbe esserci adesso. */
    public static String firma(int variante) {
        return NOMI[variante] + "-v" + VERSIONE;
    }

    // ---- il disegno -------------------------------------------------------

    /**
     * Compone l'immagine.
     *
     * Costa 1280x800x4 = 3,9 MB e qualche decina di millisecondi: va chiamata
     * da un thread di sfondo, e la bitmap va <b>riciclata appena usata</b>. In
     * memoria non deve restarci: quello che resta e' solo la copia sfocata da
     * 31 KB che tiene {@link Vetro}.
     *
     * @param luminosita 1.0 per l'interfaccia, {@link #SOTTO_LE_ICONE} per il
     *                   wallpaper di sistema.
     */
    public static Bitmap componi(int larghezza, int altezza, int variante, float luminosita) {
        Bitmap b = Bitmap.createBitmap(larghezza, altezza, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 1. il fondo, con un gradiente verticale che da' peso alla parte bassa
        p.setShader(new LinearGradient(0, 0, 0, altezza,
                scala(Tinte.FONDO_ALTO, luminosita),
                scala(Tinte.FONDO_BASSO, luminosita),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, larghezza, altezza, p);

        // 2. tre aloni larghi. Il raggio e' piu' grande dello schermo apposta:
        //    quello che si vede e' solo il cuore della macchia, e non si legge
        //    mai come un cerchio - che e' il difetto per cui i gradienti radiali
        //    di solito si notano.
        int[] colori = ALONI[variante];
        alone(c, p, larghezza * 0.18f, altezza * 0.12f, altezza * 1.15f, colori[0], luminosita);
        alone(c, p, larghezza * 0.88f, altezza * 0.90f, altezza * 0.95f, colori[1], luminosita);
        alone(c, p, larghezza * 0.55f, altezza * 0.55f, altezza * 1.30f, colori[2], luminosita);

        // 3. una vignettatura appena accennata: tiene lo sguardo al centro e
        //    fa staccare i pannelli di vetro dagli angoli.
        p.setShader(new RadialGradient(larghezza / 2f, altezza / 2f, altezza * 0.95f,
                new int[] { 0x00000000, 0x00000000, 0x4A000000 },
                new float[] { 0f, 0.58f, 1f },
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, larghezza, altezza, p);

        p.setShader(null);
        return b;
    }

    private static void alone(Canvas c, Paint p, float cx, float cy, float raggio,
                              int colore, float luminosita) {
        int pieno = scala(colore, luminosita);
        p.setShader(new RadialGradient(cx, cy, raggio,
                // 0xD8 era troppo: sul tablet i tre aloni si sommavano al
                // centro e venivano una foschia chiara su cui i pannelli di
                // vetro non staccavano piu'. Il colore si deve vedere e basta.
                new int[] { Tinte.con(pieno, 0xAA), Tinte.con(pieno, 0x4C), Tinte.con(pieno, 0x00) },
                new float[] { 0f, 0.38f, 1f },
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, raggio, p);
    }

    private static int scala(int colore, float quanto) {
        if (quanto >= 0.999f) return colore;
        return Color.argb(Color.alpha(colore),
                (int) (Color.red(colore)   * quanto),
                (int) (Color.green(colore) * quanto),
                (int) (Color.blue(colore)  * quanto));
    }

    // ---- prendersi lo sfondo ---------------------------------------------

    /**
     * Se lo sfondo di sistema non e' il nostro, lo diventa. Torna true se ha
     * scritto davvero.
     *
     * <b>Non si rifa' a ogni avvio.</b> setBitmap scrive su disco e costa
     * centinaia di millisecondi, e su una Home che viene rilanciata spesso
     * sarebbe un ritardo a ogni ritorno. Si riscrive solo se e' cambiata la
     * variante dell'ora, o se qualcun altro ha cambiato lo sfondo: e' quello
     * che dice il confronto degli id.
     */
    public static boolean prendiPossesso(Context ctx, Misure m, int variante) {
        WallpaperManager wm = WallpaperManager.getInstance(ctx);
        if (wm == null || !wm.isWallpaperSupported() || !wm.isSetWallpaperAllowed()) {
            Log.w(TAG, "il sistema non ci lascia impostare lo sfondo");
            return false;
        }

        String vogliamo = firma(variante);
        JSONObject salvato = Archivio.leggi(ctx, FILE);
        if (salvato != null
                && vogliamo.equals(salvato.optString("firma"))
                && salvato.optInt("idSistema", -1) == wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)) {
            return false;   // c'e' gia' il nostro, e nessuno l'ha toccato
        }

        Bitmap immagine = null;
        try {
            // Senza questo, molti ROM chiedono il doppio della larghezza per lo
            // scorrimento fra le pagine del launcher, e stirano l'immagine.
            // Casa non scorre: uno schermo, un'immagine.
            wm.suggestDesiredDimensions(m.larghezza, m.altezza);

            // Il suggerimento non e' un ordine. Su questo tablet il pannello e'
            // fisicamente verticale (800x1280) e il sistema vuole comunque un
            // wallpaper alto quanto il lato lungo: chiesto 1280x800, risponde
            // 1280x1280. Dandogli 1280x800 lo stirerebbe. Quindi si compone
            // della misura che chiede lui, e gli aloni - che sono posizionati
            // in frazioni, non in pixel - vengono giusti comunque.
            int larghezza = Math.max(m.larghezza, wm.getDesiredMinimumWidth());
            int altezza   = Math.max(m.altezza,   wm.getDesiredMinimumHeight());
            Log.i(TAG, "compongo lo sfondo " + larghezza + "x" + altezza
                    + " (schermo " + m.larghezza + "x" + m.altezza + ")");

            immagine = componi(larghezza, altezza, variante, SOTTO_LE_ICONE);

            // I due flag sono indipendenti e vanno impostati tutti e due. Quello
            // di sistema sostituisce anche un live wallpaper attivo, che e'
            // esattamente quello che serve su un tablet usato.
            int idSistema = wm.setBitmap(immagine, null, true, WallpaperManager.FLAG_SYSTEM);
            int idBlocco  = wm.setBitmap(immagine, null, true, WallpaperManager.FLAG_LOCK);
            wm.forgetLoadedWallpaper();

            JSONObject nuovo = new JSONObject();
            nuovo.put("firma", vogliamo);
            nuovo.put("idSistema", idSistema);
            nuovo.put("idBlocco", idBlocco);
            Archivio.scrivi(ctx, FILE, nuovo);

            Log.i(TAG, "sfondo " + vogliamo + " installato (sistema=" + idSistema
                    + " blocco=" + idBlocco + ")");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "non sono riuscito a impostare lo sfondo", e);
            return false;
        } finally {
            if (immagine != null) immagine.recycle();
        }
    }
}
