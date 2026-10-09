package dev.casa;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Il cielo che si muove: sole, luna, nuvole, pioggia, neve, nebbia, fulmini.
 *
 * <h3>Perche' questo si disegna a mano, e le icone no</h3>
 *
 * La regola di questo progetto e' che <b>le icone non si disegnano a mano</b>:
 * sono Material Symbols, perche' un'icona e' un segno che si legge in un
 * decimo di secondo e per arrivarci ci vuole chi le disegna di mestiere (il
 * ragionamento per esteso sta in {@link Icone}). Quella regola vale ancora, e
 * infatti le tessere delle previsioni - le ore, i giorni - usano le icone vere.
 *
 * Questo pero' non e' un'icona: e' una <b>scena</b>, e la differenza e' che si
 * muove. Un simbolo statico dice « pioggia »; la pioggia che scende dice che il
 * pannello e' vivo e che quel dato e' di adesso. Un file animato non c'e' - i
 * Material Symbols sono percorsi fermi - e mettere in casa una libreria di
 * animazioni vettoriali per far cadere sei gocce sarebbe la dipendenza piu'
 * cara del progetto.
 *
 * <h3>Come si muove, senza animatori</h3>
 *
 * Come tutto il resto di Casa (vedi {@link Anima}): il tempo e' <b>un numero
 * solo</b>, {@code Anima.ora()}, e ogni cosa che si muove e' una funzione di
 * quel numero. Nessun oggetto per goccia, nessun {@code ValueAnimator},
 * nessuna callback. Le posizioni « a caso » delle gocce non vengono da un
 * {@code Random} - che andrebbe seminato, tenuto, e darebbe una pioggia diversa
 * a ogni fotogramma - ma dalla <b>parte frazionaria di un multiplo
 * irrazionale</b> dell'indice: sempre la stessa goccia allo stesso posto,
 * distribuite come se fossero a caso, e zero memoria.
 *
 * <b>Dentro {@code disegna} non si alloca niente</b>: i Paint e i due percorsi
 * sono di questa istanza, e i percorsi si riscrivono con {@code reset()}, che
 * riusa il buffer che ha gia'.
 *
 * <h3>Perche' i percorsi e non i cerchi sovrapposti</h3>
 *
 * Una nuvola sono tre tondi e una base. Disegnarli uno per uno funziona finche'
 * sono opachi; appena la nuvola e' semitrasparente - e qui lo e' sempre, perche'
 * sta sopra un vetro - le sovrapposizioni si vedono come macchie piu' chiare.
 * Un percorso solo si compone una volta e si riempie una volta.
 */
public final class Cielo {

    // ---- le scene ----------------------------------------------------------

    public static final int SERENO    = 0;   // sole o luna, e basta
    public static final int VELATO    = 1;   // sole o luna con una nuvola
    public static final int NUVOLE    = 2;   // coperto
    public static final int NEBBIA    = 3;
    public static final int PIOGGIA   = 4;
    public static final int NEVE      = 5;
    public static final int TEMPORALE = 6;

    /**
     * Da codice WMO a scena.
     *
     * I codici sono quelli dell'Organizzazione meteorologica mondiale, che e'
     * quello che parla Open-Meteo. Sono una novantina e qui diventano sette
     * scene: la differenza fra « pioggia moderata » e « pioggia forte » sta
     * nella <b>scritta</b> (vedi {@link #nome}), non nel disegno - sei gocce e
     * otto gocce non si contano, e un pannello che cambia disegno per una
     * sfumatura di intensita' fa credere che sia cambiato il tempo.
     */
    public static int scena(int codice) {
        if (codice >= 95) return TEMPORALE;
        if (codice >= 85) return NEVE;
        if (codice >= 80) return PIOGGIA;
        if (codice >= 71) return NEVE;
        if (codice >= 51) return PIOGGIA;
        if (codice >= 45) return NEBBIA;
        if (codice >= 3)  return NUVOLE;
        if (codice >= 1)  return VELATO;
        return SERENO;
    }

    /** Come si dice, in italiano e in poche parole: e' una riga sotto un
     *  numero grande, non una spiegazione. */
    public static String nome(int codice) {
        switch (codice) {
            case 0:  return "sereno";
            case 1:  return "poco nuvoloso";
            case 2:  return "nuvoloso";
            case 3:  return "coperto";
            case 45: return "nebbia";
            case 48: return "nebbia gelata";
            case 51: return "pioviggine debole";
            case 53: return "pioviggine";
            case 55: return "pioviggine fitta";
            case 56: case 57: return "pioviggine gelata";
            case 61: return "pioggia debole";
            case 63: return "pioggia";
            case 65: return "pioggia forte";
            case 66: case 67: return "pioggia gelata";
            case 71: return "neve debole";
            case 73: return "neve";
            case 75: return "neve forte";
            case 77: return "nevischio";
            case 80: return "rovesci deboli";
            case 81: return "rovesci";
            case 82: return "rovesci forti";
            case 85: return "rovesci di neve";
            case 86: return "rovesci di neve forti";
            case 95: return "temporale";
            case 96: case 99: return "temporale con grandine";
            default: return "";
        }
    }

    /** L'icona vera per le tessere piccole: li' non c'e' spazio per una scena,
     *  e un segno che si riconosce vale piu' di un disegno rimpicciolito. */
    public static int icona(int codice, boolean giorno) {
        switch (scena(codice)) {
            case SERENO:    return giorno ? Icone.SOLE : Icone.NOTTE;
            case VELATO:    return giorno ? Icone.SOLE_NUVOLE : Icone.LUNA_NUVOLE;
            case NUVOLE:    return Icone.NUVOLA;
            case NEBBIA:    return Icone.NEBBIA;
            case PIOGGIA:   return codice < 60 ? Icone.PIOVIGGINE : Icone.PIOGGIA;
            case NEVE:      return Icone.NEVE;
            default:        return Icone.TEMPORALE;
        }
    }

    /**
     * Il colore che quel tempo mette addosso al pannello.
     *
     * Serve al velo del vetro: la stessa regola di tutto il resto di Casa - il
     * colore sta forte solo negli accenti piccoli, e altrove e' una velatura
     * che si nota appena. Un pannello meteo che vira all'azzurro quando piove e
     * all'ambra quando c'e' il sole si legge da lontano <b>prima</b> di
     * qualunque numero.
     */
    public static int tinta(int codice, boolean giorno) {
        switch (scena(codice)) {
            case SERENO:    return giorno ? Tinte.SOLE : Tinte.NOTTURNO;
            case VELATO:    return giorno ? Tinte.SOLE : Tinte.NOTTURNO;
            case NUVOLE:    return Tinte.NUVOLA;
            case NEBBIA:    return Tinte.NUVOLA;
            case PIOGGIA:   return Tinte.ACQUA;
            case NEVE:      return Tinte.GELO;
            default:        return Tinte.LAMPO;
        }
    }

    // ---- i tempi -----------------------------------------------------------

    /**
     * Quanto ci mette il sole a fare un giro coi suoi raggi.
     *
     * Era quaranta secondi, col ragionamento che su un apparecchio appeso al
     * muro un movimento che si nota e' un movimento che dopo una settimana da'
     * fastidio. Il ragionamento resta giusto e il numero era sbagliato: a
     * quaranta secondi il sole gira di nove gradi al secondo, e nel pannello
     * della Home - dove il disco e' grande un pollice - <b>non si vedeva
     * affatto</b>. Un'animazione che non si vede ha tutti i costi
     * dell'animazione e nessuno dei suoi vantaggi.
     *
     * Sedici secondi sono ventidue gradi al secondo: si vede che gira
     * guardandolo, e non lo si nota mentre si guarda l'ora.
     */
    private static final float GIRO_SOLE = 16000f;

    /** Quanto ci mette una goccia a cadere. */
    private static final float CADUTA_PIOGGIA = 900f;
    private static final float CADUTA_NEVE = 3400f;

    /** Ogni quanto lampeggia. */
    private static final float FRA_UN_LAMPO = 3200f;

    private static final int QUANTE_GOCCE = 9;
    private static final int QUANTI_FIOCCHI = 11;
    private static final int RAGGI = 8;

    // ---- disegno -----------------------------------------------------------

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLinea = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF appoggio = new RectF();

    /**
     * Le forme che si muovono - le due nuvole e il fulmine - stanno in percorsi
     * <b>fissi, costruiti nell'origine</b>, e a muoversi e' il Canvas.
     *
     * Prima c'era un percorso d'appoggio solo, rifatto a ogni fotogramma con
     * le coordinate del momento. Per il disegno accelerato un percorso
     * modificato e' un percorso nuovo: lo rasterizzava daccapo a ogni
     * fotogramma, ne teneva la texture in una cache che non sarebbe mai piu'
     * servita, e la sera dopo il RenderThread passava il suo tempo a ripulire
     * quella cache. Il perche' per esteso sta in {@link Icone}, dove il danno
     * era piu' grosso. Qui una nuvola cambia forma solo quando cambia la
     * scena: la texture si fa una volta, e l'ondeggiare costa una traslazione.
     */
    private final Path[] nuvole = { new Path(), new Path() };
    private final float[] nuvolaLargo = { Float.NaN, Float.NaN };
    private final Path lampo = new Path();
    private float lampoLato = Float.NaN;

    /**
     * L'alone attorno al sole e alla luna, e il perche' e' uno shader.
     *
     * La prima versione erano tre cerchi concentrici sempre piu' trasparenti,
     * per non costruire un oggetto dentro il disegno. Funzionava nel pannello
     * della Home, dove il sole e' grande un pollice; nella pagina intera, dove
     * il disco arriva a centoventi pixel, i tre bordi si vedevano tutti e tre -
     * un bersaglio da tiro a segno attorno al sole.
     *
     * Uno shader vero non si puo' evitare, ma si puo' <b>costruire una volta
     * sola</b>: dipende solo da dove sta e da quanto e' grande il sole, che
     * cambiano quando cambia la finestra e mai piu'. Si tiene quello di prima e
     * si rifa' solo se qualcosa di quei tre numeri e' cambiato - in pratica una
     * volta nella vita del pannello, non sessanta volte al secondo.
     */
    private RadialGradient alone;
    private float aloneX = Float.NaN, aloneY, aloneR;
    private int aloneColore;

    /**
     * La mezzaluna gia' ritagliata, e il disco d'ombra che la ritaglia.
     *
     * Due percorsi loro e non {@link #forma}: quello lo riusano le nuvole e la
     * neve, che lo azzerano a ogni fotogramma, e la luna invece la sua forma se
     * la tiene fra un fotogramma e l'altro.
     */
    private final Path falce = new Path();
    private final Path ombra = new Path();
    private float falceX = Float.NaN, falceY, falceR;

    public Cielo() {
        pLinea.setStyle(Paint.Style.STROKE);
        pLinea.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * La scena, dentro il quadrato piu' grande che ci sta in {@code dove}.
     *
     * Chi la disegna deve chiedere il fotogramma successivo: qui non si tocca
     * la View, perche' la stessa scena la disegnano il pannello della Home - a
     * venti fotogrammi al secondo, per non tenere la GPU sveglia tutto il
     * giorno - e la pagina intera, a fotogramma pieno.
     */
    public void disegna(Canvas c, RectF dove, int codice, boolean giorno) {
        float lato = Math.min(dove.width(), dove.height());
        if (lato <= 0f) return;
        float x = dove.centerX() - lato / 2f;
        float y = dove.centerY() - lato / 2f;
        long t = Anima.ora();

        switch (scena(codice)) {
            case SERENO:
                if (giorno) sole(c, x + lato * 0.5f, y + lato * 0.48f, lato * 0.21f, t);
                else        luna(c, x + lato * 0.5f, y + lato * 0.48f, lato * 0.21f, t);
                break;

            case VELATO:
                if (giorno) sole(c, x + lato * 0.38f, y + lato * 0.34f, lato * 0.17f, t);
                else        luna(c, x + lato * 0.38f, y + lato * 0.34f, lato * 0.17f, t);
                nuvola(c, x + lato * 0.58f, y + lato * 0.66f, lato * 0.62f, 0xE6, t, 1);
                break;

            case NUVOLE:
                nuvola(c, x + lato * 0.36f, y + lato * 0.38f, lato * 0.54f, 0x66, t, 0);
                nuvola(c, x + lato * 0.54f, y + lato * 0.58f, lato * 0.78f, 0xEE, t, 1);
                break;

            case NEBBIA:
                nuvola(c, x + lato * 0.50f, y + lato * 0.34f, lato * 0.72f, 0x9A, t, 0);
                bandeDiNebbia(c, x, y, lato, t);
                break;

            case PIOGGIA:
                nuvola(c, x + lato * 0.50f, y + lato * 0.34f, lato * 0.76f, 0xEE, t, 1);
                pioggia(c, x, y, lato, t, QUANTE_GOCCE, Tinte.ACQUA);
                break;

            case NEVE:
                nuvola(c, x + lato * 0.50f, y + lato * 0.34f, lato * 0.76f, 0xEE, t, 1);
                neve(c, x, y, lato, t);
                break;

            default:
                nuvola(c, x + lato * 0.50f, y + lato * 0.32f, lato * 0.76f, 0xEE, t, 1);
                pioggia(c, x, y, lato, t, 5, Tinte.ACQUA);
                fulmine(c, x, y, lato, t);
                break;
        }
    }

    // ---- il sole -----------------------------------------------------------

    /**
     * Il sole: un alone, otto raggi che girano, e il disco che respira.
     *
     * L'alone sono tre cerchi concentrici sempre piu' trasparenti, e non un
     * {@code RadialGradient}: uno shader va costruito, e costruirlo dentro il
     * disegno vuol dire un oggetto per fotogramma. Tre cerchi costano tre
     * riempimenti e su un Mali-400 non si sente.
     */
    private void sole(Canvas c, float cx, float cy, float r, long t) {
        p.setStyle(Paint.Style.FILL);
        alone(c, cx, cy, r * 2.3f, Tinte.SOLE, 0x4E);

        // I raggi girano piano e « respirano »: il seno sfasato per raggio fa
        // sembrare che pulsino uno per volta invece che tutti insieme.
        float giro = fra(t / GIRO_SOLE) * 360f;
        pLinea.setColor(Tinte.con(Tinte.SOLE, 0xCC));
        pLinea.setStrokeWidth(Math.max(2f, r * 0.14f));
        for (int i = 0; i < RAGGI; i++) {
            double a = Math.toRadians(giro + i * (360f / RAGGI));
            float respiro = 0.5f + 0.5f * (float) Math.sin(t / 1100.0 + i * 0.8);
            float da = r * 1.34f;
            float a1 = r * (1.62f + 0.24f * respiro);
            float sx = (float) Math.cos(a), sy = (float) Math.sin(a);
            c.drawLine(cx + sx * da, cy + sy * da, cx + sx * a1, cy + sy * a1, pLinea);
        }

        float battito = 1f + 0.025f * (float) Math.sin(t / 1400.0);
        p.setColor(Tinte.SOLE);
        c.drawCircle(cx, cy, r * battito, p);
    }

    /**
     * La luna: un cerchio a cui se ne toglie un altro.
     *
     * <h3>Perche' non basta la regola pari-dispari</h3>
     *
     * Il pezzo mancante non si ottiene disegnando un secondo cerchio del colore
     * del fondo: qui dietro c'e' il vetro, e un cerchio « del colore del fondo »
     * sarebbe una macchia opaca su un pannello trasparente. Fin qui giusto.
     *
     * La prima versione allora metteva i due cerchi <b>nello stesso percorso</b>
     * con {@code EVEN_ODD}, e li' c'era l'errore: pari-dispari non toglie, tiene
     * quello che sta sotto <b>uno solo</b> dei due. La mezzaluna veniva bene,
     * ma il pezzo di cerchio d'ombra che sporgeva oltre il bordo della luna sta
     * sotto un cerchio solo pure lui - e quindi si accendeva anche quello. Sullo
     * schermo si vedeva un disco morsicato con una gobba luminosa attaccata di
     * fianco: una luna in eclissi, non una luna in fase. Bastava che il cerchio
     * d'ombra sporgesse, e sporgeva sempre.
     *
     * Adesso si toglie davvero, con {@link Path#op}: quel che esce dalla luna
     * non viene disegnato, e il cerchio d'ombra puo' essere piu' grande della
     * luna - che e' proprio quello che serve, perche' e' la sua curvatura a fare
     * il taglio fra luce e ombra. Con un'ombra piu' <b>piccola</b> il taglio si
     * incurva dalla parte sbagliata e le punte restano spuntate: e' l'altra
     * meta' del difetto, ed e' quella che si vede anche a fase disegnata bene.
     *
     * <h3>Perche' si tiene da parte</h3>
     *
     * {@code Path#op} e' un'operazione booleana su due curve, non un
     * riempimento: costa, e questa forma dipende solo da dove sta e da quanto e'
     * grande la luna - cioe' cambia quando cambia la finestra e mai piu'. Si
     * tiene quella di prima come si fa per l'alone, e a ogni fotogramma resta un
     * percorso da riempire.
     */
    private void luna(Canvas c, float cx, float cy, float r, long t) {
        p.setStyle(Paint.Style.FILL);
        alone(c, cx, cy, r * 2.1f, Tinte.LUNA, 0x3A);

        falce(cx, cy, r);
        p.setColor(Tinte.LUNA);
        c.drawPath(falce, p);

        // Tre stelle che si accendono a turno. Tre e non venti: sopra un
        // pannello di vetro un cielo stellato diventa sporcizia.
        for (int i = 0; i < 3; i++) {
            float sx = cx + r * (i == 0 ? -1.7f : i == 1 ? 1.5f : -0.6f);
            float sy = cy + r * (i == 0 ? -1.2f : i == 1 ? 1.3f : 1.8f);
            float luce = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t / 900.0 + i * 2.1));
            p.setColor(Tinte.con(Tinte.LUNA, (int) (0xDD * luce)));
            c.drawCircle(sx, sy, r * 0.075f, p);
        }
    }

    /**
     * Ricalcola la mezzaluna, e solo se serve.
     *
     * <b>I tre numeri sono misurati, non scelti a occhio.</b> Il disco d'ombra
     * e' appena piu' grande della luna ({@code 1.05}) perche' il taglio fra
     * luce e ombra deve incurvarsi <b>verso</b> la parte illuminata: e' cosi'
     * che si guarda una luna vera, ed e' quello che fa le punte appuntite. Sta
     * spostato di poco piu' di mezzo raggio in diagonale, e da li' viene lo
     * spessore della falce - {@code raggio - (1.05 - 0.62)}, cioe' poco piu' di
     * mezza luna: abbastanza da leggersi da un metro, non tanto da sembrare un
     * disco a cui manca un morso.
     */
    private void falce(float cx, float cy, float r) {
        if (cx == falceX && cy == falceY && r == falceR) return;
        falceX = cx; falceY = cy; falceR = r;

        falce.reset();
        falce.setFillType(Path.FillType.WINDING);
        falce.addCircle(cx, cy, r, Path.Direction.CW);

        ombra.reset();
        ombra.addCircle(cx + r * 0.50f, cy - r * 0.37f, r * 1.05f, Path.Direction.CW);

        falce.op(ombra, Path.Op.DIFFERENCE);
    }

    // ---- le nuvole ---------------------------------------------------------

    /**
     * Una nuvola: tre gobbe e una base, in un percorso solo.
     *
     * Non scorre da un bordo all'altro - servirebbe un ritaglio, e una nuvola
     * che entra ed esce chiede all'occhio di seguirla. Ondeggia: e' il
     * movimento che si nota solo se lo si guarda, che e' quello che deve fare
     * un pannello appeso al muro.
     */
    private void nuvola(Canvas c, float cx, float cy, float largo, int opacita,
                        long t, int corsia) {
        float onda = (float) Math.sin(t / (corsia == 0 ? 5600.0 : 4000.0) + corsia * 1.7);
        cx += onda * largo * 0.085f;

        Path forma = nuvola(corsia, largo);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Tinte.con(Tinte.NUVOLA, opacita));
        int s = c.save();
        c.translate(cx, cy);
        c.drawPath(forma, p);
        c.restoreToCount(s);
    }

    /** La forma della nuvola di una corsia, centrata nell'origine. Si rifa'
     *  solo se cambia la larghezza, cioe' quando cambia la scena. */
    private Path nuvola(int corsia, float largo) {
        Path forma = nuvole[corsia];
        if (nuvolaLargo[corsia] == largo) return forma;
        nuvolaLargo[corsia] = largo;
        float r = largo * 0.24f;
        forma.reset();
        forma.setFillType(Path.FillType.WINDING);
        forma.addCircle(-largo * 0.22f, r * 0.10f, r * 0.86f, Path.Direction.CW);
        forma.addCircle(largo * 0.02f, -r * 0.38f, r * 1.10f, Path.Direction.CW);
        forma.addCircle(largo * 0.26f, r * 0.06f, r * 0.92f, Path.Direction.CW);
        appoggio.set(-largo * 0.42f, -r * 0.10f, largo * 0.42f, r * 0.98f);
        forma.addRoundRect(appoggio, r * 0.9f, r * 0.9f, Path.Direction.CW);
        return forma;
    }

    /** La nebbia: quattro strisce che scivolano avanti e indietro a velocita'
     *  diverse. E' l'unica scena in cui non cade e non gira niente, e va bene
     *  cosi': la nebbia sta ferma. */
    private void bandeDiNebbia(Canvas c, float x, float y, float lato, long t) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            float yy = y + lato * (0.58f + i * 0.105f);
            float onda = (float) Math.sin(t / (2600.0 + i * 900.0) + i * 1.3);
            float mezzo = lato * (0.40f - i * 0.04f);
            float cx = x + lato * 0.5f + onda * lato * 0.10f;
            float alto = lato * 0.045f;
            appoggio.set(cx - mezzo, yy - alto / 2f, cx + mezzo, yy + alto / 2f);
            p.setColor(Tinte.con(Tinte.NUVOLA, 0x88 - i * 0x14));
            c.drawRoundRect(appoggio, alto / 2f, alto / 2f, p);
        }
    }

    // ---- quello che cade ---------------------------------------------------

    /**
     * La pioggia.
     *
     * Ogni goccia e' un trattino che parte da sotto la nuvola e si spegne in
     * fondo. La posizione orizzontale e la fase vengono dalla parte frazionaria
     * di un multiplo dell'indice: sempre la stessa goccia allo stesso posto,
     * sparse come se fossero a caso, e nessun numero da tenere in memoria.
     */
    private void pioggia(Canvas c, float x, float y, float lato, long t,
                         int quante, int colore) {
        float cima = y + lato * 0.56f;
        float caduta = lato * 0.40f;
        pLinea.setStrokeWidth(Math.max(1.6f, lato * 0.022f));
        for (int i = 0; i < quante; i++) {
            float gx = x + lato * (0.17f + fra(i * 0.6180339f) * 0.66f);
            float fase = fra(t / CADUTA_PIOGGIA + fra(i * 0.3819660f));
            float gy = cima + fase * caduta;
            // Si spegne alle due estremita': una goccia che compare di colpo a
            // meta' schermo si vede comparire, e allora si guarda quella invece
            // della pioggia.
            float vita = Math.min(1f, Math.min(fase, 1f - fase) * 5f);
            pLinea.setColor(Tinte.con(colore, (int) (0xDD * vita)));
            c.drawLine(gx, gy, gx - lato * 0.018f, gy + lato * 0.075f, pLinea);
        }
    }

    /** La neve: tondini che scendono piano e ondeggiano, come fa la neve. */
    private void neve(Canvas c, float x, float y, float lato, long t) {
        float cima = y + lato * 0.54f;
        float caduta = lato * 0.42f;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < QUANTI_FIOCCHI; i++) {
            float fase = fra(t / CADUTA_NEVE + fra(i * 0.3819660f));
            float gx = x + lato * (0.15f + fra(i * 0.6180339f) * 0.70f)
                     + (float) Math.sin(fase * 6.2831853 + i) * lato * 0.035f;
            float gy = cima + fase * caduta;
            float vita = Math.min(1f, Math.min(fase, 1f - fase) * 5f);
            p.setColor(Tinte.con(Tinte.GELO, (int) (0xEE * vita)));
            c.drawCircle(gx, gy, lato * (0.017f + 0.010f * fra(i * 0.7548776f)), p);
        }
    }

    /**
     * Il fulmine: un lampo corto e raro.
     *
     * Dura un decimo di secondo ogni tre secondi. E' l'unica cosa di tutta Casa
     * che compare all'improvviso, ed e' voluto: un temporale che non lampeggia
     * mai si legge come pioggia, e un temporale che lampeggia in continuazione
     * si legge come un guasto.
     */
    private void fulmine(Canvas c, float x, float y, float lato, long t) {
        float fase = fra(t / FRA_UN_LAMPO);
        // Due guizzi vicini, come succede davvero: 0,00-0,04 e 0,07-0,11.
        float forza = fase < 0.04f ? 1f - fase / 0.04f
                    : (fase > 0.07f && fase < 0.11f) ? 1f - (fase - 0.07f) / 0.04f
                    : 0f;

        float cx = x + lato * 0.52f, cy = y + lato * 0.60f, h = lato * 0.30f;
        if (lampoLato != lato) {
            lampoLato = lato;
            lampo.reset();
            lampo.setFillType(Path.FillType.WINDING);
            lampo.moveTo(h * 0.26f, 0f);
            lampo.lineTo(-h * 0.16f, h * 0.52f);
            lampo.lineTo(h * 0.02f, h * 0.52f);
            lampo.lineTo(-h * 0.24f, h * 1.00f);
            lampo.lineTo(h * 0.30f, h * 0.40f);
            lampo.lineTo(h * 0.08f, h * 0.40f);
            lampo.close();
        }

        p.setStyle(Paint.Style.FILL);
        if (forza > 0f) {
            p.setColor(Tinte.con(Tinte.LAMPO, (int) (0x55 * forza)));
            c.drawCircle(cx, cy + h * 0.5f, h * 1.1f, p);
        }
        // Il fulmine si vede sempre, ma acceso solo nell'istante del lampo:
        // senza, fra un lampo e l'altro la scena sarebbe pioggia e basta.
        p.setColor(Tinte.con(Tinte.LAMPO, (int) (0x77 + 0x88 * forza)));
        int s = c.save();
        c.translate(cx, cy);
        c.drawPath(lampo, p);
        c.restoreToCount(s);
    }

    /** L'alone: un solo cerchio sfumato, con lo shader tenuto da parte. */
    private void alone(Canvas c, float cx, float cy, float r, int colore, int opacita) {
        if (alone == null || cx != aloneX || cy != aloneY || r != aloneR
                || colore != aloneColore) {
            alone = new RadialGradient(cx, cy, r, Tinte.con(colore, opacita),
                    Tinte.con(colore, 0x00), Shader.TileMode.CLAMP);
            aloneX = cx; aloneY = cy; aloneR = r; aloneColore = colore;
        }
        p.setShader(alone);
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
    }

    /** La parte dopo la virgola: da' un numero fra zero e uno che si ripete
     *  sempre uguale per lo stesso ingresso. */
    private static float fra(float x) {
        return x - (float) Math.floor(x);
    }
}
