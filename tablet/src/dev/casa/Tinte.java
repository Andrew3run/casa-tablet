package dev.casa;

import android.graphics.Color;

/**
 * I colori di Casa, in un posto solo.
 *
 * Prima stavano come costanti private dentro OrologioView e Pannello, e i due
 * elenchi avevano gia' cominciato a divergere. Qui invece c'e' una tavolozza
 * sola, e le sezioni ci pescano dentro.
 *
 * La regola che tiene insieme l'aspetto: <b>ogni sezione ha un colore, e quel
 * colore sta negli accenti</b> - l'icona nella barra, il tasto principale,
 * quel che e' acceso. I pannelli sono di un grigio neutro uguale ovunque, come
 * i widget di iOS; i colori sono quelli di sistema di Apple, versione scura.
 */
public final class Tinte {

    private Tinte() {}

    // ---- fondo ----------------------------------------------------------

    /** Il nero-blu su cui si costruisce lo sfondo. Non e' nero puro: il nero
     *  puro su un pannello IPS a 213 dpi si legge come una macchia spenta. */
    public static final int FONDO      = 0xFF050507;
    public static final int FONDO_ALTO = 0xFF0E0F14;   // in cima allo sfondo
    public static final int FONDO_BASSO= 0xFF000000;   // in fondo, per il peso

    // ---- testo ----------------------------------------------------------

    public static final int TESTO      = 0xFFFFFFFF;
    public static final int TESTO_MEDIO= 0xFFC7C7CC;
    public static final int TESTO_TENUE= 0xFF8E8E93;
    public static final int SPENTO     = 0xFF5A5A5F;   // cio' che c'e' ma non e' disponibile

    // ---- le sei sezioni --------------------------------------------------
    //
    // Il viola della Musica e' l'unico che si vede poco: quando c'e' una
    // copertina il pannello prende il colore del disco (addomestica piu'
    // sotto), e il viola resta per il silenzio e per l'icona nella barra, che
    // invece deve stare ferma.

    public static final int HOME     = 0xFF0A84FF;   // blu
    public static final int MUSICA   = 0xFFBF5AF2;   // viola
    public static final int RADIO    = 0xFF30D158;   // verde
    public static final int APP      = 0xFF64D2FF;   // azzurro
    public static final int OROLOGIO = 0xFFFF9F0A;   // ambra
    public static final int LUCI     = 0xFFFFD60A;   // giallo

    /**
     * L'agenda: rosa cipria.
     *
     * E' l'unico posto della tavolozza rimasto libero. Blu, viola, verde,
     * azzurro, ambra e giallo sono presi, e il rosso non e' di nessuna sezione
     * apposta - e' degli allarmi, e deve restare l'unica cosa rossa che si
     * vede. Un rosa desaturato sta lontano da tutti e due.
     */
    public static final int AGENDA   = 0xFFFF7DAA;   // rosa

    /** Le notizie: un corallo. Non e' la tinta di una sezione della barra -
     *  le notizie sono una pagina, come l'agenda - ma e' quella che le
     *  riconosce nella Home e sul riposo, dove si alternano al meteo e agli
     *  impegni: ci vuole un colore che non sia di nessun altro, e l'ambra
     *  dell'orologio e il rosa dell'agenda gli stanno ai due lati. */
    public static final int NOTIZIE  = 0xFFFF8F6B;   // corallo

    /** Il rosso non e' di nessuna sezione: e' il video, e gli allarmi. */
    public static final int ALLARME  = 0xFFFF453A;

    /** Il verde di Spotify, quello del marchio: 1ED760, come lo pubblica
     *  Spotify nelle sue norme sul logo. Non e' un colore di Casa e non si
     *  accorda con la tavolozza - e' un marchio, e i marchi non si
     *  intonano. Serve solo dove si disegna il logo. */
    public static final int SPOTIFY  = 0xFF1ED760;

    // ---- il tempo che fa -------------------------------------------------
    //
    // Il meteo non e' una sezione e non ha un colore suo: ne ha sei, ed e' il
    // tempo a sceglierli. E' il secondo pannello di Casa la cui tinta cambia da
    // sola - il primo e' la Musica, che prende quella della copertina - e serve
    // a dire che tempo fa <b>prima</b> di qualunque numero, da un metro e senza
    // leggere. La corrispondenza sta in Cielo.tinta().

    public static final int SOLE     = 0xFFFFCB5C;   // il giorno sereno
    public static final int NOTTURNO = 0xFF8FA6D8;   // la notte serena
    public static final int LUNA     = 0xFFE3ECF7;   // la luna, e le stelle
    public static final int NUVOLA   = 0xFFC3D2E0;   // nuvole e nebbia
    public static final int ACQUA    = 0xFF57BFF0;   // la pioggia
    public static final int GELO     = 0xFFBFE8FF;   // la neve
    public static final int LAMPO    = 0xFFFFD166;   // il temporale

    // ---- vetro ----------------------------------------------------------
    //
    // Il materiale di Apple, non una lastra colorata: sopra lo sfondo sfocato
    // un grigio scuro neutro, uguale in tutte le sezioni. Il colore della
    // sezione resta negli accenti - l'icona nella barra, il tasto principale,
    // quel che e' acceso - e non tinge piu' i pannelli: sei schermate tinte
    // di sei colori sembravano sei app diverse.

    /** Il grigio del materiale (il systemGray6 scuro) e quanto copre: lo
     *  sfondo si intravede, il testo sopra si legge come su un fondo pieno. */
    public static final int MATERIALE    = 0xFF1C1C1E;
    public static final int MATERIALE_COPRE = 0xE6;

    /** Il velo colorato: zero sui pannelli tranquilli, un accenno su quello
     *  in primo piano. Chi passa un'opacita' sua la tiene. */
    public static final int VELO_QUIETO  = 0x00;
    public static final int VELO_ACCESO  = 0x24;

    /** Il filo attorno al pannello: un capello, uguale tutto intorno. Il filo
     *  di luce in cima voleva un gradiente nuovo a ogni disegno. */
    public static final int BORDO        = 0x10FFFFFF;

    /** I riempimenti dei comandi, come i fill di sistema di iOS: il tasto
     *  normale, e quello premuto o scelto. */
    public static final int RIEMPIMENTO  = 0x24FFFFFF;
    public static final int RIEMPIMENTO_SCELTO = 0x3DFFFFFF;

    /** Il separatore fra due righe di un elenco. */
    public static final int SEPARATORE   = 0x1FFFFFFF;

    /** Il segmento scelto di un controllo a segmenti (systemGray3 scuro). */
    public static final int SEGMENTO_SCELTO = 0xFF636366;

    /** Una tessera accesa, alla Casa di Apple: chiara, col testo scuro. Quel
     *  che e' acceso si vede da un metro senza leggere niente. */
    public static final int TESSERA_ACCESA = 0xFFF2F2F7;
    public static final int TESTO_SU_CHIARO = 0xFF1C1C1E;
    public static final int TESTO_SU_CHIARO_TENUE = 0xFF6C6C70;

    // ---- aiuti -----------------------------------------------------------

    /** Lo stesso colore con un'altra opacita'. */
    public static int con(int colore, int alpha) {
        return (colore & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Fonde due colori. Serve alla transizione fra sezioni: la tinta
     * dell'ambiente scorre da quella vecchia a quella nuova sullo stesso
     * ValueAnimator della dissolvenza, cosi' l'ambiente cambia insieme al
     * contenuto invece che scattare dopo.
     */
    public static int fondi(int da, int a, float quanto) {
        int alpha = (int) (Color.alpha(da) + (Color.alpha(a) - Color.alpha(da)) * quanto);
        int rosso = (int) (Color.red(da)   + (Color.red(a)   - Color.red(da))   * quanto);
        int verde = (int) (Color.green(da) + (Color.green(a) - Color.green(da)) * quanto);
        int blu   = (int) (Color.blue(da)  + (Color.blue(a)  - Color.blue(da))  * quanto);
        return Color.argb(alpha, rosso, verde, blu);
    }

    /**
     * Un colore scritto "#RRGGBB" o "#AARRGGBB", oppure la riserva se non si
     * capisce.
     *
     * Serve alle lampade: il loro colore arriva da un file, quindi da fuori dal
     * codice, e un file scritto a mano prima o poi contiene una riga storta. Un
     * colore illeggibile non deve far sparire una lampada dall'elenco - la
     * lampada c'e' lo stesso, e' solo la sua tinta che non si sa.
     */
    public static int leggi(String testo, int riserva) {
        if (testo == null) return riserva;
        String s = testo.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) return 0xFF000000 | (int) Long.parseLong(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException storto) {
        }
        return riserva;
    }

    /** L'inverso: come si scrive in un file. L'alpha non si salva - le tinte
     *  delle lampade sono tutte piene, e "#RRGGBB" e' quello che si sa
     *  riscrivere a mano senza sbagliare. */
    public static String scrivi(int colore) {
        return String.format("#%06X", colore & 0xFFFFFF);
    }

    /**
     * Porta un colore a una saturazione e a una luminosita' fisse.
     *
     * Serve alla copertina del disco: il colore dominante di una copertina puo'
     * essere qualunque cosa, compreso un grigio slavato o un fucsia accecante.
     * Passandolo di qui, qualunque disco da' una tinta che sta bene addosso
     * all'interfaccia, e le copertine spente danno comunque un colore vivo.
     */
    public static int addomestica(int colore) {
        float[] hsv = new float[3];
        Color.colorToHSV(colore, hsv);
        hsv[1] = Math.max(0.45f, Math.min(0.70f, hsv[1]));
        hsv[2] = 0.85f;
        return Color.HSVToColor(hsv);
    }
}
