package dev.casa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;

/**
 * Il vetro smerigliato di Casa.
 *
 * <b>Il ragionamento che rende tutto questo sostenibile su un Mali-400.</b> Il
 * vetro costoso e' quello dinamico, che sfoca quello che si muove dietro. Qui
 * dietro non si muove niente: lo sfondo e' un'immagine ferma che generiamo noi.
 * Quindi la sfocatura si calcola <b>una volta sola all'avvio</b>, e per tutto
 * il resto della giornata un pannello di vetro costa un rettangolo con una
 * texture da 64 KB. Non e' un'approssimazione dell'effetto: e' l'effetto, fatto
 * quando serve farlo.
 *
 * La catena, con i numeri:
 * <ol>
 *   <li>{@link Sfondo#componi} da' 1280x800 ARGB_8888 = 3,9 MB, <i>temporanei</i>;
 *   <li>si riduce a 1/8, cioe' 160x100, dove la sfocatura costa un ottavo di
 *       niente;
 *   <li>tre passate di media mobile a raggio 16 su 16.000 pixel: qualche
 *       decina di millisecondi, una volta sola. A un ottavo di scala, raggio 16
 *       equivale a 128 px a piena risoluzione - vetro vero, non una sfumatura;
 *   <li>si tiene cosi' com'e', 160x100 ARGB_8888: <b>64 KB</b>. RGB_565 ne
 *       avrebbe usati 31, ma su un'immagine fatta di soli gradienti morbidi i
 *       cinque bit di rosso e blu si vedono: sul tablet vero comparivano bande
 *       diagonali larghe un dito. Trentatre kilobyte sono il prezzo giusto per
 *       toglierle;
 *   <li>la bitmap grande si ricicla subito: 3,9 MB che non devono restare in
 *       giro nemmeno un secondo;
 *   <li>si disegna ingrandita 8x con filtro bilineare, che <b>aggiunge
 *       morbidezza gratis</b> - un secondo blur pagato dalla GPU nel
 *       campionamento della texture.
 * </ol>
 *
 * <b>Quello che non si usa, e perche'.</b> FLAG_SHOW_WALLPAPER sulla finestra
 * darebbe lo stesso risultato a schermo, ma obbligherebbe il compositore a
 * comporre una superficie a schermo intero in piu' a ogni fotogramma: su
 * Mali-400 tre livelli fanno cadere la composizione da overlay a GPU, e la
 * banda di memoria di un MT6580 e' quello che manca per primo. Qui lo sfondo lo
 * disegna questa classe, l'app resta <b>opaca</b>, un livello solo.
 * BlurMaskFilter non e' accelerato e obbligherebbe la View a LAYER_TYPE_SOFTWARE,
 * cioe' una bitmap a schermo intero nell'heap: su 1 GB e' fuori discussione.
 *
 * <h3>E RenderScript, che c'era, non c'e' piu'</h3>
 *
 * La sfocatura la faceva {@code ScriptIntrinsicBlur}, con la media a mano come
 * ripiego. Funzionava, ed era la scelta giusta sulla carta: qualche
 * millisecondo invece di qualche decina.
 *
 * Sul tablet vero pero' <b>il contesto non si smonta</b>. Dopo un
 * {@code rs.destroy()} regolare, in Casa restano <b>quattro thread vivi</b>
 * (i lavoratori di RenderScript, che prendono il nome da chi li ha creati -
 * per questo si chiamano tutti « Casa-vetro ») piu' il RSMessageThread, e con
 * loro i due-quattro megabyte nativi del contesto. Restano li' per tutta la
 * vita del processo, per una sfocatura durata quaranta millisecondi.
 *
 * Su 16.000 pixel la media a mano costa qualche decina di millisecondi <b>una
 * volta sola all'avvio</b>, su un thread che finisce davvero. Il ripiego era
 * gia' scritto e provato: adesso e' la strada principale, e RenderScript non
 * entra piu' nel processo.
 */
public final class Vetro {

    private static final String TAG = "Casa.Vetro";

    /** Un ottavo. Sotto si vedono i quadrati, sopra si spreca. */
    private static final int RIDUZIONE = 8;

    /** Il massimo che ScriptIntrinsicBlur accetta e' 25. 16 basta e avanza,
     *  perche' sta gia' girando su un'immagine ridotta a un ottavo. */
    private static final float RAGGIO_SFOCATURA = 16f;

    /**
     * Di quanto il vetro di un pannello e' sfalsato rispetto allo sfondo.
     *
     * E' il trucco che fa la differenza. Ridisegnare la stessa immagine sfocata
     * nella stessa posizione darebbe un pannello invisibile; spostarla di pochi
     * pixel da' quello che l'occhio legge come rifrazione attraverso uno
     * spessore di vetro. Costa zero: e' solo un'altra matrice sullo stesso
     * shader.
     */
    private static final float SFALSAMENTO = 9f;

    private final Bitmap sfocato;
    private final BitmapShader shader;

    private final Matrix mFondo   = new Matrix();
    private final Matrix mPannello= new Matrix();

    private final Paint pFondo    = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint pVetro    = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pVelo     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBordo    = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF appoggio = new RectF();

    /** Il colore dell'ambiente: la sezione in scena, o la copertina del disco. */
    private int tinta = Tinte.HOME;

    private Vetro(Bitmap sfocato, int larghezza, int altezza) {
        this.sfocato = sfocato;
        shader = new BitmapShader(sfocato, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        adattaA(larghezza, altezza);

        pFondo.setShader(shader);
        pVetro.setShader(shader);
        pBordo.setStyle(Paint.Style.STROKE);
        pBordo.setStrokeWidth(1f);
        pBordo.setColor(Tinte.BORDO);
    }

    /**
     * Ricalcola l'ingrandimento per la finestra che c'e' davvero.
     *
     * Due matrici e nessuna allocazione, quindi si puo' chiamare da onLayout
     * senza pensarci. Serve perche' la finestra non e' sempre grande quanto
     * Misure prevedeva: la barra di sistema che va e viene in modalita'
     * immersiva sposta l'altezza di sessantaquattro pixel, e senza adattarsi
     * lo sfondo si stira.
     */
    public void adattaA(int larghezza, int altezza) {
        if (larghezza <= 0 || altezza <= 0 || sfocato == null || sfocato.isRecycled()) return;
        float scalaX = larghezza / (float) sfocato.getWidth();
        float scalaY = altezza   / (float) sfocato.getHeight();
        mFondo.setScale(scalaX, scalaY);
        mPannello.setScale(scalaX, scalaY);
        mPannello.postTranslate(-SFALSAMENTO, -SFALSAMENTO);
    }

    /**
     * Compone lo sfondo, lo sfoca, e butta via tutto il resto.
     *
     * Da chiamare su un thread di sfondo: l'immagine grande e la sfocatura
     * insieme fanno un picco di circa 8 MB per meno di mezzo secondo.
     */
    public static Vetro crea(Context ctx, Misure m, int variante) {
        Bitmap grande = Sfondo.componi(m.larghezza, m.altezza, variante, 1f);
        Bitmap piccolo = null;
        try {
            piccolo = Bitmap.createScaledBitmap(grande,
                    Math.max(1, m.larghezza / RIDUZIONE),
                    Math.max(1, m.altezza / RIDUZIONE), true);
        } finally {
            grande.recycle();
        }

        long inizio = System.currentTimeMillis();
        Bitmap morbido = sfoca(piccolo, RAGGIO_SFOCATURA);
        Log.i(TAG, "sfocatura " + piccolo.getWidth() + "x" + piccolo.getHeight()
                + " in " + (System.currentTimeMillis() - inizio) + " ms");

        return new Vetro(morbido, m.larghezza, m.altezza);
    }

    // ---- sfocatura --------------------------------------------------------

    /** La sfocatura: tre passate di media mobile, sul thread di chi chiama.
     *  Quanto ci mette lo scrive gia' crea(). */
    private static Bitmap sfoca(Bitmap piccolo, float raggio) {
        return sfocaAMano(piccolo, (int) raggio);
    }

    /** Tre passate di media mobile: il modo classico di ottenere una gaussiana
     *  senza calcolare una gaussiana. */
    private static Bitmap sfocaAMano(Bitmap b, int raggio) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int[] appoggio = new int[w * h];
        for (int passata = 0; passata < 3; passata++) {
            media(px, appoggio, w, h, raggio, true);
            media(appoggio, px, w, h, raggio, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    private static void media(int[] da, int[] a, int w, int h, int raggio, boolean orizzontale) {
        int lungo = orizzontale ? w : h;
        int corto = orizzontale ? h : w;
        for (int riga = 0; riga < corto; riga++) {
            for (int i = 0; i < lungo; i++) {
                int r = 0, g = 0, bl = 0, quanti = 0;
                for (int k = -raggio; k <= raggio; k++) {
                    int j = i + k;
                    if (j < 0 || j >= lungo) continue;
                    int c = orizzontale ? da[riga * w + j] : da[j * w + riga];
                    r  += (c >> 16) & 0xFF;
                    g  += (c >> 8)  & 0xFF;
                    bl += c & 0xFF;
                    quanti++;
                }
                int c = 0xFF000000 | ((r / quanti) << 16) | ((g / quanti) << 8) | (bl / quanti);
                if (orizzontale) a[riga * w + i] = c; else a[i * w + riga] = c;
            }
        }
    }

    // ---- disegno ----------------------------------------------------------

    /** La tinta dell'ambiente. La cambia Telaio durante la dissolvenza, e la
     *  cambia la copertina del disco che sta suonando. */
    public void setTinta(int colore) { tinta = colore; }

    public int tinta() { return tinta; }

    /** Lo sfondo, a schermo intero. Una texture da 31 KB ingrandita 8x. */
    public void disegnaSfondo(Canvas c, int larghezza, int altezza) {
        shader.setLocalMatrix(mFondo);
        c.drawRect(0, 0, larghezza, altezza, pFondo);
    }

    /**
     * Un pannello di vetro. Tre passate, e nessuna allocazione.
     *
     * @param acceso se il pannello e' quello in primo piano: il velo si fa piu'
     *               denso, che e' il modo di dire "questo" senza aggiungere un
     *               bordo colorato che griderebbe.
     */
    public void pannello(Canvas c, RectF area, float raggio, boolean acceso) {
        pannello(c, area, raggio, tinta, acceso ? Tinte.VELO_ACCESO : Tinte.VELO_QUIETO);
    }

    public void pannello(Canvas c, RectF area, float raggio, int colore, int opacitaVelo) {
        // 1. il vetro: la stessa immagine sfocata, spostata di pochi pixel.
        shader.setLocalMatrix(mPannello);
        c.drawRoundRect(area, raggio, raggio, pVetro);

        // 2. il materiale: un grigio neutro che copre quasi tutto. E' quello
        //    che fa di un rettangolo una scheda alla Apple - lo sfondo si
        //    intravede appena, e il pannello e' uguale in ogni sezione.
        pVelo.setColor(MATERIALE);
        c.drawRoundRect(area, raggio, raggio, pVelo);

        // 3. il colore, solo se c'e' qualcosa da dire: un pannello acceso, una
        //    stazione che suona. Sui pannelli tranquilli non si disegna.
        if (opacitaVelo > 0) {
            pVelo.setColor(Tinte.con(colore, opacitaVelo));
            c.drawRoundRect(area, raggio, raggio, pVelo);
        }

        // 4. un capello di bordo, uguale tutto intorno. Prima era un filo di
        //    luce in cima e d'ombra in fondo, con un LinearGradient nuovo a
        //    ogni pannello di ogni fotogramma: allocazioni per un effetto che
        //    adesso non si vuole piu'.
        c.drawRoundRect(area, raggio, raggio, pBordo);

        // Lo shader torna com'era per lo sfondo, altrimenti il prossimo
        // disegnaSfondo userebbe la matrice del pannello.
        shader.setLocalMatrix(mFondo);
    }

    /**
     * Un comando: fondo piatto, non vetro.
     *
     * <b>Il vetro e' per i pannelli, non per i tasti.</b> Il fondo di un
     * pannello di vetro e' l'immagine sfocata dello sfondo, che e' fatta di
     * aloni larghi mezzo schermo: su un rettangolo grande e' una velatura che
     * si legge come profondita', ma su una pastiglia alta cinquanta pixel e'
     * <b>una fetta di alone</b> - e la stessa pastiglia viene chiara a sinistra
     * e verde a destra a seconda di dove capita sullo schermo. Sulle tre
     * scorciatoie della Home si vedeva benissimo: sembravano tre barre di
     * avanzamento riempite a meta', e nessuno le aveva volute cosi'.
     *
     * Qui il fondo e' un bianco appena accennato, uguale ovunque, piu' il velo
     * del colore quando il comando e' vivo. Costa due rettangoli invece di
     * tre, non tocca lo shader, e - la cosa che conta - <b>due tasti uguali
     * hanno lo stesso aspetto anche se stanno in due punti diversi</b>.
     *
     * @param opacita quanto il colore tinge il comando; zero per un tasto
     *                neutro, che e' come deve stare quando non sta facendo
     *                niente.
     */
    public void controllo(Canvas c, RectF area, float raggio, int colore, int opacita) {
        pVelo.setColor(FONDO_COMANDO);
        c.drawRoundRect(area, raggio, raggio, pVelo);
        if (opacita > 0) {
            pVelo.setColor(Tinte.con(colore, opacita));
            c.drawRoundRect(area, raggio, raggio, pVelo);
        }
    }

    /**
     * Un incavo: una superficie <b>dentro</b> un pannello.
     *
     * Il contrario del comando. Un tasto e' una cosa che sporge, e sporge
     * perche' e' piu' chiara di quello che ha intorno; una superficie che
     * raccoglie del contenuto dentro un pannello e' una cosa che rientra, e
     * rientra perche' e' piu' scura. Fatta chiara - il primo tentativo - la
     * scheda del meteo dentro la scheda dell'ora diventava il rettangolo piu'
     * luminoso della schermata, cioe' esattamente quello che l'orologio non
     * doveva avere accanto.
     *
     * Non ha bordo, e non deve averlo: il bordo ce l'ha gia' il pannello che la
     * contiene, e due cornici concentriche a due centimetri l'una dall'altra si
     * leggono come una cornice doppia, mai come due cose.
     */
    public void incavo(Canvas c, RectF area, float raggio, int colore, int opacita) {
        pVelo.setColor(FONDO_INCAVO);
        c.drawRoundRect(area, raggio, raggio, pVelo);
        if (opacita > 0) {
            pVelo.setColor(Tinte.con(colore, opacita));
            c.drawRoundRect(area, raggio, raggio, pVelo);
        }
    }

    /** Il nero appena accennato dentro un incavo. */
    private static final int FONDO_INCAVO = 0x2A000000;

    /** Il materiale dei pannelli, pieno: la copertura e' in Tinte. */
    private static final int MATERIALE = Tinte.con(Tinte.MATERIALE, Tinte.MATERIALE_COPRE);

    /** Il riempimento di ogni comando, come i fill di sistema di iOS. */
    private static final int FONDO_COMANDO = Tinte.RIEMPIMENTO;

    /**
     * Un alone sotto un'icona accesa: adesso non si disegna.
     *
     * Era un RadialGradient nuovo a ogni chiamata, cioe' a ogni fotogramma di
     * ogni icona accesa, per un effetto che lo stile di adesso non vuole -
     * quello che e' acceso lo dice il suo colore pieno. Resta il metodo, cosi'
     * chi lo chiama non va toccato.
     */
    public void bagliore(Canvas c, float cx, float cy, float raggio, int colore) {
    }

    /** Il rettangolo di appoggio, per non allocarne uno a ogni onDraw. */
    public RectF area(float sinistra, float alto, float destra, float basso) {
        appoggio.set(sinistra, alto, destra, basso);
        return appoggio;
    }

    /**
     * Butta la texture.
     *
     * La chiama onTrimMemory quando Casa passa in secondo piano: 31 KB non sono
     * niente, ma rifarla costa una ventina di millisecondi e questa e' la
     * classe che sa come. Chi la usa deve tollerare che sia sparita.
     */
    public void rilascia() {
        if (sfocato != null && !sfocato.isRecycled()) sfocato.recycle();
    }

    public boolean vivo() {
        return sfocato != null && !sfocato.isRecycled();
    }
}
