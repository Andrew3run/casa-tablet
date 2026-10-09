package dev.casa.telefono;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Il sistema di disegno dell'app: colori, spazi, corpi, raggi, e i pezzi
 * fatti con quelli. Ogni schermata prende da qui e nessuna si sceglie un
 * numero suo - e' il difetto da cui era partita Casa sul tablet (docs/aspetto.md).
 *
 * Le regole sono quelle di una web app curata:
 * <ul>
 * <li><b>spazi su una griglia da 4 dp</b>, sei gradini, dal filo fra due righe
 *     della stessa cosa (4) allo stacco fra due blocchi (32);</li>
 * <li><b>sei corpi</b> e due pesi, regolare e medio;</li>
 * <li><b>tre raggi</b>: immagini, comandi, pannelli - dal piu' piccolo al
 *     piu' grande, perche' quello che contiene e' piu' tondo di quello che
 *     sta dentro;</li>
 * <li><b>bersagli da 48 dp</b>;</li>
 * <li><b>una colonna di al massimo 640 dp</b>, centrata: sul telefono aperto
 *     o in orizzontale le righe non diventano lunghe un metro;</li>
 * <li><b>griglie che scelgono da sole quante colonne</b> ci stanno, come un
 *     {@code repeat(auto-fill, minmax(...))};</li>
 * <li><b>tre superfici</b> come sul tablet: il pannello raccoglie, il comando
 *     sporge ed e' piu' chiaro, l'incavo rientra ed e' piu' scuro;</li>
 * <li><b>il colore e' un'informazione</b>: l'accento vuol dire « scelto » o
 *     « sta andando », non « e' un bottone ».</li>
 * </ul>
 */
final class Stile {

    // ---- colori -----------------------------------------------------------------

    static final int FONDO      = 0xFF07090D;
    static final int BARRA      = 0xF20B0F15;
    static final int VETRO      = 0x0FFFFFFF;
    static final int VETRO_SU   = 0x1AFFFFFF;
    static final int INCAVO     = 0x47000000;
    static final int FILO       = 0x1AFFFFFF;
    static final int FILO_ALTO  = 0x2EFFFFFF;
    static final int FILO_BASSO = 0x0AFFFFFF;
    static final int TESTO      = 0xFFF3F5F8;
    static final int SECONDO    = 0xFFA8B2BD;
    static final int TERZO      = 0xFF6C7784;
    static final int ACCENTO    = 0xFFF2D06B;
    static final int SU_ACCENTO = 0xFF17130A;
    static final int VERDE      = 0xFF6EE7A8;
    static final int ERRORE     = 0xFFFF8A7A;
    static final int SPOTIFY    = 0xFF1ED760;
    static final int SU_SPOTIFY = 0xFF04140A;

    // ---- spazi, in dp -----------------------------------------------------------

    static final int S1 = 4, S2 = 8, S3 = 12, S4 = 16, S5 = 24, S6 = 32;

    // ---- raggi, in dp -----------------------------------------------------------

    static final int R_IMMAGINE = 10, R_COMANDO = 14, R_PANNELLO = 22;

    // ---- corpi, in sp -----------------------------------------------------------

    static final float T_GRANDE = 28, T_TITOLO = 20, T_NOME = 16, T_CORPO = 15, T_PICCOLO = 13, T_NOTA = 12;

    static final int TOCCO = 48;
    static final int LARGHEZZA = 640;

    static final int PRIMARIO = 0, SECONDARIO = 1, FANTASMA = 2, PERICOLO = 3, VERDE_SPOTIFY = 4;

    final Context c;
    final float densita;
    final Typeface normale, medio, leggero;

    Stile(Context c) {
        this.c = c;
        densita = c.getResources().getDisplayMetrics().density;
        normale = Typeface.create("sans-serif", Typeface.NORMAL);
        medio = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        leggero = Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    int dp(float v) {
        return Math.round(v * densita);
    }

    static int conAlfa(int colore, int alfa) {
        return (colore & 0x00FFFFFF) | (alfa << 24);
    }

    // ---- testo ------------------------------------------------------------------

    TextView testo(CharSequence t, float sp, int colore, Typeface f) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(colore);
        v.setTypeface(f != null ? f : normale);
        v.setLineSpacing(dp(2), 1f);
        return v;
    }

    /** Una riga sola, che si tronca coi puntini invece di andare a capo. */
    TextView riga(CharSequence t, float sp, int colore, Typeface f) {
        TextView v = testo(t, sp, colore, f);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }

    /** Le etichette maiuscole, spaziate: a corpo piccolo e senza aria una
     *  parola in maiuscolo si legge una lettera per volta. */
    TextView etichetta(String t) {
        TextView v = riga(t.toUpperCase(java.util.Locale.ITALIAN), 11.5f, TERZO, medio);
        v.setLetterSpacing(0.12f);
        return v;
    }

    /** Il titolo di una sezione, con a destra un comando se c'e'. L'aria sta
     *  sulla fila, non sul titolo: titolo e comando hanno lo stesso centro. */
    LinearLayout titoloSezione(String t, View destra) {
        LinearLayout fila = orizzontale();
        fila.setPadding(dp(S1), dp(S6), 0, dp(S3));
        fila.setMinimumHeight(dp(S6 + S3 + 36));
        fila.addView(riga(t, 18, TESTO, medio), new LinearLayout.LayoutParams(0, -2, 1f));
        if (destra != null) fila.addView(destra);
        return fila;
    }

    // ---- superfici --------------------------------------------------------------

    Drawable pannello(float raggioDp) {
        return new Vetro(VETRO, FILO_ALTO, FILO_BASSO, dp(raggioDp), dp(1));
    }

    Drawable comando(float raggioDp) {
        return new Vetro(VETRO_SU, 0x2BFFFFFF, 0x0DFFFFFF, dp(raggioDp), dp(1));
    }

    Drawable incavo(float raggioDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(INCAVO);
        g.setCornerRadius(dp(raggioDp));
        return g;
    }

    Drawable pieno(int colore, float raggioDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colore);
        g.setCornerRadius(dp(raggioDp));
        return g;
    }

    Drawable tondo(int colore) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(colore);
        return g;
    }

    /** L'onda del tocco, chiusa dentro la forma. */
    Drawable tocco(Drawable fondo, float raggioDp) {
        GradientDrawable maschera = new GradientDrawable();
        maschera.setColor(0xFFFFFFFF);
        maschera.setCornerRadius(dp(raggioDp));
        return new RippleDrawable(ColorStateList.valueOf(0x24FFFFFF), fondo, maschera);
    }

    /** Un pannello di vetro con dentro l'onda del tocco. */
    Drawable pannelloPremibile(float raggioDp) {
        return tocco(pannello(raggioDp), raggioDp);
    }

    // ---- icone ------------------------------------------------------------------

    Drawable disegno(int id, int colore) {
        Drawable d = c.getDrawable(id).mutate();
        d.setTint(colore);
        return d;
    }

    ImageView icona(int id, int colore) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(disegno(id, colore));
        return v;
    }

    View punto(int colore, int latoDp) {
        View v = new View(c);
        v.setBackground(tondo(colore));
        return v;
    }

    /** Un'immagine con gli angoli tondi davvero: taglia anche la bitmap, non
     *  solo il fondo. */
    ImageView immagine(float raggioDp, boolean cerchio) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setBackground(cerchio ? tondo(INCAVO) : incavo(raggioDp));
        v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        v.setClipToOutline(true);
        return v;
    }

    // ---- contenitori ------------------------------------------------------------

    LinearLayout verticale() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout orizzontale() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Il contenuto di una schermata: centrato, largo al massimo 640 dp, con
     *  16 dp di lato, e un rotolo attorno. */
    ScrollView rotolo(View contenuto) {
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        Limite l = new Limite(c, dp(LARGHEZZA), dp(S4));
        l.setPadding(0, dp(S1), 0, dp(S6));
        l.addView(contenuto, new FrameLayout.LayoutParams(-1, -2));
        sv.addView(l);
        return sv;
    }

    LinearLayout.LayoutParams larga(int altezzaPx) {
        return new LinearLayout.LayoutParams(-1, altezzaPx);
    }

    LinearLayout.LayoutParams larga(int altezzaPx, int suDp, int giuDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, altezzaPx);
        lp.topMargin = dp(suDp);
        lp.bottomMargin = dp(giuDp);
        return lp;
    }

    LinearLayout.LayoutParams lato(int latoDp) {
        return new LinearLayout.LayoutParams(dp(latoDp), dp(latoDp));
    }

    LinearLayout.LayoutParams pesata(int altezzaPx, int destraDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, altezzaPx, 1f);
        lp.rightMargin = dp(destraDp);
        return lp;
    }

    Griglia griglia(float cellaMinimaDp, int minimo, int massimo) {
        return new Griglia(c, dp(cellaMinimaDp), dp(S3), minimo, massimo);
    }

    // ---- comandi ----------------------------------------------------------------

    /** Un tasto: icona (se c'e') e scritta, centrate insieme. */
    LinearLayout bottone(String t, int icona, int tipo) {
        LinearLayout b = orizzontale();
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(S4), 0, dp(S4), 0);
        b.setMinimumHeight(dp(TOCCO));
        int colore;
        Drawable fondo;
        switch (tipo) {
            case PRIMARIO:
                colore = SU_ACCENTO;
                fondo = pieno(ACCENTO, R_COMANDO);
                break;
            case VERDE_SPOTIFY:
                colore = SU_SPOTIFY;
                fondo = pieno(SPOTIFY, R_COMANDO);
                break;
            case PERICOLO:
                colore = ERRORE;
                fondo = new Vetro(0x14FF8A7A, 0x4DFF8A7A, 0x1AFF8A7A, dp(R_COMANDO), dp(1));
                break;
            case FANTASMA:
                colore = SECONDO;
                fondo = null;
                break;
            default:
                colore = TESTO;
                fondo = comando(R_COMANDO);
        }
        b.setBackground(tocco(fondo, R_COMANDO));
        if (icona != 0) {
            ImageView iv = icona(icona, colore);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(20), dp(20));
            if (t != null) lp.rightMargin = dp(S2);
            b.addView(iv, lp);
        }
        if (t != null) {
            TextView tv = riga(t, T_CORPO, colore, medio);
            tv.setIncludeFontPadding(false);
            b.addView(tv);
        }
        return b;
    }

    /** Un tasto rotondo con l'icona al centro esatto. */
    FrameLayout tastoIcona(int icona, int latoDp, int iconaDp, int tipo, String descrizione) {
        FrameLayout f = new FrameLayout(c);
        int colore;
        Drawable fondo;
        switch (tipo) {
            case PRIMARIO:      colore = SU_ACCENTO; fondo = tondo(ACCENTO); break;
            case VERDE_SPOTIFY: colore = SU_SPOTIFY; fondo = tondo(SPOTIFY); break;
            case FANTASMA:      colore = SECONDO; fondo = null; break;
            case PERICOLO:      colore = ERRORE; fondo = tondo(0x1FFF8A7A); break;
            default:            colore = TESTO; fondo = new Vetro(VETRO_SU, 0x2BFFFFFF, 0x0DFFFFFF, dp(latoDp) / 2f, dp(1));
        }
        f.setBackground(tocco(fondo, latoDp / 2f));
        ImageView iv = icona(icona, colore);
        f.addView(iv, new FrameLayout.LayoutParams(dp(iconaDp), dp(iconaDp), Gravity.CENTER));
        f.setContentDescription(descrizione);
        return f;
    }

    static void cambiaIcona(FrameLayout tasto, Drawable d) {
        ((ImageView) tasto.getChildAt(0)).setImageDrawable(d);
    }

    /** Spento si vede spento: meno opaco, e non si preme. */
    static void abilita(View v, boolean si) {
        v.setEnabled(si);
        v.setClickable(si);
        v.setAlpha(si ? 1f : 0.38f);
    }

    Switch interruttore(boolean acceso) {
        Switch s = new Switch(c);
        s.setChecked(acceso);
        int[][] stati = { { android.R.attr.state_checked }, {} };
        s.setThumbTintList(new ColorStateList(stati, new int[] { ACCENTO, 0xFFC9D1D9 }));
        s.setTrackTintList(new ColorStateList(stati, new int[] { 0x80F2D06B, 0x33FFFFFF }));
        return s;
    }

    EditText campo(String suggerimento, int tipo) {
        EditText e = new EditText(c);
        e.setHint(suggerimento);
        e.setHintTextColor(TERZO);
        e.setTextColor(TESTO);
        e.setTextSize(T_NOME);
        e.setInputType(tipo);
        e.setSingleLine(true);
        e.setBackground(null);
        e.setPadding(0, 0, 0, 0);
        e.setGravity(Gravity.CENTER_VERTICAL);
        return e;
    }

    /** Un campo in una capsula di vetro. Chi la usa aggiunge a destra i suoi
     *  tasti, alti 44: la capsula e' alta 56, quindi hanno 6 dp di aria tutto
     *  intorno. */
    LinearLayout capsula(int iconaSinistra, EditText campo) {
        LinearLayout c = orizzontale();
        c.setBackground(pannello(28));
        c.setPadding(dp(iconaSinistra != 0 ? S3 + 2 : S5 - 4), 0, dp(6), 0);
        c.setMinimumHeight(dp(56));
        if (iconaSinistra != 0) {
            ImageView iv = icona(iconaSinistra, TERZO);
            LinearLayout.LayoutParams lp = lato(22);
            lp.rightMargin = dp(S3);
            c.addView(iv, lp);
        }
        c.addView(campo, new LinearLayout.LayoutParams(0, dp(44), 1f));
        return c;
    }

    /** Quando non c'e' niente: l'icona smorta di quel che ci andrebbe e una
     *  riga piccola, dentro un bordo tratteggiato. Il vuoto si scrive piccolo. */
    LinearLayout vuoto(int icona, String testo) {
        LinearLayout v = verticale();
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(S5), dp(S5), dp(S5), dp(S5));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(R_PANNELLO));
        g.setStroke(dp(1), 0x24FFFFFF, dp(6), dp(5));
        v.setBackground(g);
        if (icona != 0) {
            ImageView iv = icona(icona, TERZO);
            LinearLayout.LayoutParams lp = lato(26);
            lp.bottomMargin = dp(S2);
            v.addView(iv, lp);
        }
        TextView t = testo(testo, T_PICCOLO, SECONDO, null);
        t.setGravity(Gravity.CENTER);
        v.addView(t);
        return v;
    }

    /** Una riga di scheletro, mentre arriva un elenco: dice « sta arrivando
     *  qualcosa di questa forma » meglio di una rotella. */
    LinearLayout scheletro(int quante, boolean conImmagine) {
        LinearLayout l = verticale();
        for (int i = 0; i < quante; i++) {
            LinearLayout r = orizzontale();
            r.setPadding(dp(S2), dp(S2), dp(S2), dp(S2));
            if (conImmagine) {
                View im = new View(c);
                im.setBackground(pieno(0x14FFFFFF, R_IMMAGINE));
                LinearLayout.LayoutParams lp = lato(48);
                lp.rightMargin = dp(S3);
                r.addView(im, lp);
            }
            LinearLayout testi = verticale();
            View a = new View(c);
            a.setBackground(pieno(0x17FFFFFF, 6));
            testi.addView(a, new LinearLayout.LayoutParams(dp(140 + (i * 37) % 80), dp(12)));
            View b = new View(c);
            b.setBackground(pieno(0x0DFFFFFF, 6));
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(dp(90 + (i * 53) % 60), dp(10));
            lb.topMargin = dp(S2);
            testi.addView(b, lb);
            r.addView(testi);
            l.addView(r);
        }
        ValueAnimator pulsa = ValueAnimator.ofFloat(0.45f, 1f);
        pulsa.setDuration(900);
        pulsa.setRepeatMode(ValueAnimator.REVERSE);
        pulsa.setRepeatCount(ValueAnimator.INFINITE);
        final LinearLayout bersaglio = l;
        pulsa.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                if (!bersaglio.isAttachedToWindow()) { a.cancel(); return; }
                bersaglio.setAlpha((Float) a.getAnimatedValue());
            }
        });
        l.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { }
        });
        pulsa.setStartDelay(16);
        pulsa.start();
        return l;
    }

    Segmentato segmentato(String[] voci, Segmentato.Scelta scelta) {
        return new Segmentato(this, voci, scelta);
    }

    // ---- pezzi disegnati ----------------------------------------------------------

    /**
     * Il pannello di vetro: un fondo traslucido, un riflesso che scende
     * dall'alto e un bordo che da chiaro si fa quasi invisibile verso il basso.
     * I gradienti si fanno quando cambiano le misure, non a ogni disegno.
     */
    static final class Vetro extends Drawable {
        private final Paint pieno = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint riflesso = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bordo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF(), rb = new RectF();
        private final int alto, basso;
        private final float raggio, spessore;

        Vetro(int colore, int alto, int basso, float raggio, float spessore) {
            this.alto = alto;
            this.basso = basso;
            this.raggio = raggio;
            this.spessore = spessore;
            pieno.setColor(colore);
            bordo.setStyle(Paint.Style.STROKE);
            bordo.setStrokeWidth(spessore);
        }

        @Override protected void onBoundsChange(Rect b) {
            r.set(b);
            rb.set(r);
            rb.inset(spessore / 2f, spessore / 2f);
            float h = Math.max(1f, b.height());
            riflesso.setShader(new LinearGradient(0, b.top, 0, b.top + Math.min(h, raggio * 3),
                    0x0FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            bordo.setShader(new LinearGradient(0, b.top, 0, b.bottom, alto, basso, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas c) {
            c.drawRoundRect(r, raggio, raggio, pieno);
            c.drawRoundRect(r, raggio, raggio, riflesso);
            c.drawRoundRect(rb, raggio, raggio, bordo);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Il fondo: il nero di Casa con tre aloni che si spostano piano. Il
     * telefono ha il fiato per farlo; il giro dura quaranta secondi, cosi' il
     * movimento si sente e non si guarda.
     */
    static final class Fondo extends Drawable {
        private final Paint base = new Paint();
        private final Paint[] aloni = { new Paint(Paint.ANTI_ALIAS_FLAG), new Paint(Paint.ANTI_ALIAS_FLAG), new Paint(Paint.ANTI_ALIAS_FLAG) };
        private final int[] colori = { 0x38F2D06B, 0x303B82F6, 0x2614B8A6 };
        private float fase;
        private final ValueAnimator giro = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));

        Fondo() {
            base.setColor(FONDO);
            giro.setDuration(40000);
            giro.setRepeatCount(ValueAnimator.INFINITE);
            giro.setInterpolator(new LinearInterpolator());
            giro.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    fase = (Float) a.getAnimatedValue();
                    invalidateSelf();
                }
            });
        }

        void avvia() { if (!giro.isStarted()) giro.start(); }
        void ferma() { giro.cancel(); }

        @Override protected void onBoundsChange(Rect b) {
            float w = b.width();
            for (int i = 0; i < aloni.length; i++) {
                aloni[i].setShader(new RadialGradient(0, 0, w * 0.95f,
                        colori[i], colori[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            }
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            c.drawRect(b, base);
            float[][] centri = {
                    { w * (0.15f + 0.10f * (float) Math.sin(fase)), h * (0.04f + 0.03f * (float) Math.cos(fase)) },
                    { w * (1.00f + 0.08f * (float) Math.cos(fase * 1.3f)), h * (0.45f + 0.06f * (float) Math.sin(fase)) },
                    { w * (0.05f + 0.10f * (float) Math.sin(fase * 0.7f)), h * (0.95f + 0.03f * (float) Math.cos(fase)) },
            };
            for (int i = 0; i < aloni.length; i++) {
                c.save();
                c.translate(centri[i][0], centri[i][1]);
                c.drawRect(-centri[i][0], -centri[i][1], w - centri[i][0], h - centri[i][1], aloni[i]);
                c.restore();
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** La barra delle schede: un fondo scuro con il filo in alto. */
    static final class Fascia extends Drawable {
        private final Paint pieno = new Paint(), filo = new Paint();
        Fascia(int colore, int coloreFilo) { pieno.setColor(colore); filo.setColor(coloreFilo); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b, pieno);
            c.drawRect(b.left, b.top, b.right, b.top + 1, filo);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * La colonna di contenuto: larga al massimo {@code massimo}, centrata, e
     * mai piu' vicina di {@code lato} al bordo. E' il {@code max-width} con
     * {@code margin: auto} di una pagina web.
     */
    static final class Limite extends FrameLayout {
        private final int massimo, lato;

        Limite(Context c, int massimo, int lato) {
            super(c);
            this.massimo = massimo;
            this.lato = lato;
        }

        @Override protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            int margine = Math.max(lato, (w - massimo) / 2);
            if (getPaddingLeft() != margine || getPaddingRight() != margine) {
                setPadding(margine, getPaddingTop(), margine, getPaddingBottom());
            }
            super.onMeasure(ws, hs);
        }
    }

    /**
     * Una griglia che sceglie da sola quante colonne ci stanno, come
     * {@code repeat(auto-fill, minmax(cella, 1fr))}: le celle si allargano per
     * riempire la fila, e le celle di una stessa fila sono alte uguali.
     */
    static final class Griglia extends ViewGroup {
        private final int cella, spazio, minimo, massimo;
        private int colonne = 2, larghezzaCella;
        private int[] altezzeFile = new int[0];

        Griglia(Context c, int cella, int spazio, int minimo, int massimo) {
            super(c);
            this.cella = cella;
            this.spazio = spazio;
            this.minimo = minimo;
            this.massimo = massimo;
        }

        @Override protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            colonne = Math.max(minimo, Math.min(massimo, (w + spazio) / (cella + spazio)));
            larghezzaCella = (w - spazio * (colonne - 1)) / colonne;
            int esatta = MeasureSpec.makeMeasureSpec(larghezzaCella, MeasureSpec.EXACTLY);
            int visibili = 0;
            for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getVisibility() != GONE) visibili++;
            int file = (visibili + colonne - 1) / colonne;
            altezzeFile = new int[file];
            int k = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                LayoutParams lp = v.getLayoutParams();
                int altezza = lp != null && lp.height > 0
                        ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                        : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                v.measure(esatta, altezza);
                int f = k / colonne;
                altezzeFile[f] = Math.max(altezzeFile[f], v.getMeasuredHeight());
                k++;
            }
            k = 0;
            int totale = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                int f = k / colonne;
                if (v.getMeasuredHeight() != altezzeFile[f]) {
                    v.measure(esatta, MeasureSpec.makeMeasureSpec(altezzeFile[f], MeasureSpec.EXACTLY));
                }
                k++;
            }
            for (int f = 0; f < file; f++) totale += altezzeFile[f] + (f > 0 ? spazio : 0);
            setMeasuredDimension(w, totale);
        }

        @Override protected void onLayout(boolean cambiato, int l, int t, int r, int b) {
            int k = 0, y = 0, fila = -1;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                int f = k / colonne, col = k % colonne;
                if (f != fila) {
                    if (fila >= 0) y += altezzeFile[fila] + spazio;
                    fila = f;
                }
                int x = col * (larghezzaCella + spazio);
                v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
                k++;
            }
        }
    }

    /** Un riquadro alto quanto e' largo: copertine e loghi. */
    static final class Quadrato extends FrameLayout {
        Quadrato(Context c) { super(c); }
        @Override protected void onMeasure(int ws, int hs) {
            super.onMeasure(ws, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(ws), MeasureSpec.EXACTLY));
        }
    }

    /**
     * Il selettore a due o tre voci: un incavo con dentro la voce scelta che
     * sporge. Neutro, non giallo: sceglie cosa si guarda, non accende niente.
     */
    static final class Segmentato extends LinearLayout {
        interface Scelta { void scelto(int quale); }

        private final Stile s;
        private final TextView[] voci;
        private int scelto = -1;

        Segmentato(Stile s, String[] nomi, final Scelta scelta) {
            super(s.c);
            this.s = s;
            setPadding(s.dp(S1), s.dp(S1), s.dp(S1), s.dp(S1));
            GradientDrawable g = new GradientDrawable();
            g.setColor(INCAVO);
            g.setCornerRadius(s.dp(R_COMANDO + 4));
            g.setStroke(s.dp(1), FILO);
            setBackground(g);
            voci = new TextView[nomi.length];
            for (int i = 0; i < nomi.length; i++) {
                final int quale = i;
                TextView t = s.riga(nomi[i], T_CORPO, SECONDO, s.medio);
                t.setGravity(Gravity.CENTER);
                t.setIncludeFontPadding(false);
                t.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        if (quale == scelto) return;
                        setScelto(quale);
                        scelta.scelto(quale);
                    }
                });
                voci[i] = t;
                addView(t, new LayoutParams(0, s.dp(40), 1f));
            }
        }

        void setScelto(int quale) {
            scelto = quale;
            for (int i = 0; i < voci.length; i++) {
                boolean q = i == quale;
                voci[i].setTextColor(q ? TESTO : SECONDO);
                voci[i].setBackground(q ? new Vetro(0x29FFFFFF, 0x33FFFFFF, 0x0DFFFFFF, s.dp(R_COMANDO), s.dp(1)) : null);
            }
        }

        void setTesto(int quale, String t) { voci[quale].setText(t); }
    }

    /**
     * Una barra di avanzamento sottile. Con un {@link Spostata} si puo'
     * toccare e trascinare, e allora ha un pallino; senza, e' solo un filo che
     * si riempie.
     */
    static final class Barretta extends View {
        interface Spostata { void a(float frazione); }

        private final Paint traccia = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pieno = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pallino = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float spessore, raggioPallino;
        private float valore;
        private boolean tenuta;
        private Spostata spostata;

        Barretta(Stile s, int colore) {
            super(s.c);
            spessore = s.dp(4);
            raggioPallino = s.dp(6);
            traccia.setColor(0x24FFFFFF);
            pieno.setColor(colore);
            pallino.setColor(0xFFFFFFFF);
        }

        void setColore(int colore) { pieno.setColor(colore); invalidate(); }

        void setSpostata(Spostata s) { spostata = s; }

        void setValore(float v) {
            if (tenuta) return;
            valore = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float cy = getHeight() / 2f, x0 = raggioPallino, x1 = getWidth() - raggioPallino;
            r.set(x0, cy - spessore / 2, x1, cy + spessore / 2);
            c.drawRoundRect(r, spessore, spessore, traccia);
            float x = x0 + (x1 - x0) * valore;
            r.set(x0, cy - spessore / 2, Math.max(x0 + spessore, x), cy + spessore / 2);
            c.drawRoundRect(r, spessore, spessore, pieno);
            if (spostata != null) c.drawCircle(x, cy, tenuta ? raggioPallino * 1.3f : raggioPallino, pallino);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (spostata == null || !isEnabled()) return false;
            float x0 = raggioPallino, x1 = getWidth() - raggioPallino;
            float f = Math.max(0f, Math.min(1f, (e.getX() - x0) / Math.max(1f, x1 - x0)));
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    tenuta = true;
                    valore = f;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    valore = f;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    tenuta = false;
                    valore = f;
                    invalidate();
                    spostata.a(f);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    tenuta = false;
                    invalidate();
                    return true;
            }
            return false;
        }
    }
}
