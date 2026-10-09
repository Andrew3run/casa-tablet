package dev.casa;

import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Le misure dello schermo e la scala dell'interfaccia, calcolate una volta sola.
 *
 * <h3>Perche' non si usano i dp</h3>
 *
 * Su questo pannello - 1280x800 a 213 dpi dichiarati - i dp mentono sulla
 * grandezza reale delle cose: 48 dp di bersaglio, che su un telefono sono un
 * polpastrello, qui vengono 100 pixel su 800 di altezza, cioe' un ottavo dello
 * schermo. Per questo le misure seguono <b>frazioni dello schermo</b>, e i dp
 * restano solo per le cose che devono essere toccabili con un dito - dove il
 * dito e' un dito indipendentemente da quanti pixel ci stiano sotto.
 *
 * <h3>Una scala sola, e non piu' frazioni sparse</h3>
 *
 * Prima ogni sezione si sceglieva i suoi corpi: {@code h * 0.056f} qui,
 * {@code h * 0.072f} li', {@code h * 0.040f} in una terza. Il risultato era che
 * la stessa cosa - il nome di una lampada, il nome di una stazione - aveva tre
 * grandezze diverse in tre schermate, e che <b>tutto era grosso</b>: le frazioni
 * erano state scelte una alla volta, ognuna guardando solo la sua schermata
 * vuota, e nessuna guardando l'insieme.
 *
 * Qui ci sono <b>sei corpi e cinque spazi</b>, e tutto il resto ne e' un
 * multiplo. Sei perche' e' il numero oltre il quale due gradini vicini non si
 * distinguono piu' e la scala smette di voler dire qualcosa. La regola di
 * lettura e': <b>a un metro si legge {@link #voce} e sopra</b>; sotto si legge
 * avvicinandosi, quindi ci va solo quello che si guarda quando si sta gia'
 * toccando.
 *
 * <h3>La scala segue il lato corto</h3>
 *
 * I corpi sono frazioni dell'<b>altezza</b> e non della diagonale: e' l'altezza
 * la risorsa scarsa qui, ed e' quella che decide quante righe ci stanno. Ma
 * sono anche <b>limitati</b> in alto, perche' su uno schermo piu' grande di
 * questo un titolo non deve diventare un cartellone: oltre una certa misura si
 * smette di ingrandire e si comincia a respirare.
 */
public final class Misure {

    /** Larghezza e altezza in pixel, cosi' come sono adesso (orizzontale). */
    public final int larghezza, altezza;

    /** Pixel per dp. Su questo tablet vale circa 1,33. */
    public final float densita;

    /**
     * La barra di navigazione sta a sinistra, non in basso.
     *
     * Lo schermo e' 1280x800: l'altezza e' la risorsa scarsa, ed e' quella che
     * serve all'orologio grande e alle griglie. Una barra in basso da 130 px si
     * mangia il 16% dell'altezza; a sinistra si mangia il 10% della larghezza,
     * che e' quella che avanza. In piu' sei voci su 800 px di altezza fanno
     * 133 px per voce, contro i 190 px orizzontali che verrebbero in basso: il
     * bersaglio e' piu' comodo, non meno.
     */
    public final int barra;

    /** Il margine fra i pannelli e il bordo dello schermo. */
    public final int margine;

    /** Il raggio degli angoli: uno per i pannelli, uno per le pastiglie. Due e
     *  non tre: due raggi si leggono come due grandezze di cosa, tre si leggono
     *  come un errore. */
    public final float raggio, raggioPiccolo;

    /** Il bersaglio minimo di un comando. Sotto questo non si scende: sbagliare
     *  bersaglio al buio vuol dire accendere la lampada sbagliata. */
    public final int bersaglio;

    // ---- i sei corpi -------------------------------------------------------

    /** Etichette in maiuscolo, unita' di misura, il « min » accanto al numero. */
    public final float micro;
    /** La seconda riga: lo stato di una lampada, l'artista, la data breve. */
    public final float nota;
    /** Il testo normale, e le scritte dentro i pulsanti. */
    public final float corpo;
    /** I nomi: una stazione, una lampada, un brano. E' il gradino piu' piccolo
     *  che si legge da un metro. */
    public final float voce;
    /** L'intestazione di una schermata, e i numeri che contano - l'orario di
     *  una sveglia, il conto alla rovescia. */
    public final float titolo;
    /** L'ora nella Home, e nient'altro. */
    public final float cifra;

    // ---- i cinque spazi ----------------------------------------------------

    /** Il filo fra due cose che sono la stessa cosa: due righe di un blocco. */
    public final float s1;
    /** Fra due tessere della stessa griglia. */
    public final float s2;
    /** L'aria dentro una tessera piccola, fra il bordo e quel che contiene. */
    public final float s3;
    /** L'aria dentro un pannello. */
    public final float s4;
    /** Fra due blocchi che parlano di cose diverse. */
    public final float s5;

    /** La grandezza normale di un'icona dentro il testo. */
    public final float icona;

    public Misure(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        densita = dm.density;

        // getRealSize e non getDisplayMetrics: il secondo toglie la barra di
        // sistema e su questo tablet risponde 1280x736 invece di 1280x800.
        // Casa e' a schermo intero e la finestra li prende tutti e ottocento,
        // quindi con la misura sbagliata lo sfondo veniva composto per
        // un'altezza e disegnato su un'altra: stirato di sessantaquattro
        // pixel, poco ma visibile sui gradienti.
        Point vero = new Point();
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getRealSize(vero);
        int x = vero.x > 0 ? vero.x : dm.widthPixels;
        int y = vero.y > 0 ? vero.y : dm.heightPixels;

        // IL LATO LUNGO E' LA LARGHEZZA, SEMPRE.
        //
        // Casa e' orizzontale e basta - lo dice il manifesto con
        // userLandscape - ma getRealSize risponde con l'orientamento del
        // display <b>in quel momento</b>, e all'avvio del tablet quel momento
        // arriva prima che la rotazione sia applicata: torna 800x1280.
        //
        // Le misure pero' si calcolano una volta sola, in onCreate, e da li'
        // non cambiano piu' (configChanges tiene in vita l'Activity anche
        // quando lo schermo gira). Risultato, visto sul tablet dopo un
        // riavvio: barra larga 82 px invece di 132, orologio alto 243 invece
        // di 152, e i pannelli l'uno sopra l'altro. Tutto giusto - per uno
        // schermo verticale che non esiste.
        //
        // Non c'e' niente da indovinare: due numeri, il piu' grande e' la
        // larghezza.
        larghezza = Math.max(x, y);
        altezza   = Math.min(x, y);

        barra     = Math.round(larghezza * 0.103f);   // ~132 px su 1280
        margine   = Math.round(altezza   * 0.026f);   // ~21 px su 800
        // Gli angoli larghi delle schede di iPadOS: un pannello con l'angolo
        // stretto si legge come una finestra, con l'angolo largo come un oggetto.
        raggio        = altezza * 0.030f;             // ~24 px
        raggioPiccolo = altezza * 0.017f;             // ~14 px
        bersaglio = dp(42);

        // I corpi. Il tetto e' in dp e non in pixel: dice « piu' grande di
        // cosi' non serve a nessuno », e quel « cosi' » e' una grandezza
        // fisica, non un numero di pixel.
        micro  = corpo(0.0225f, 20f);
        nota   = corpo(0.0280f, 25f);
        this.corpo = corpo(0.0335f, 30f);
        voce   = corpo(0.0430f, 38f);
        titolo = corpo(0.0560f, 50f);
        cifra  = altezza * 0.190f;

        s1 = altezza * 0.0075f;    //  6
        s2 = altezza * 0.0150f;    // 12
        s3 = altezza * 0.0225f;    // 18
        s4 = altezza * 0.0325f;    // 26
        s5 = altezza * 0.0500f;    // 40

        icona = altezza * 0.036f;  // 29
    }

    /** Un corpo come frazione dell'altezza, ma non oltre un tetto in dp. */
    private float corpo(float frazione, float tettoDp) {
        return Math.min(altezza * frazione, tettoDp * densita);
    }

    public int dp(float quanti) {
        return Math.round(quanti * densita);
    }

    /** Il testo si misura in frazioni di altezza, per la ragione detta sopra.
     *  Resta per i casi fuori scala - l'unico e' il numero dentro la ghiera,
     *  che segue il raggio della ghiera e non lo schermo. */
    public float testo(float frazioneAltezza) {
        return altezza * frazioneAltezza;
    }
}
