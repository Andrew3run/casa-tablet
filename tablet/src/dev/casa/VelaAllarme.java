package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * La schermata di quando qualcosa suona.
 *
 * Copre <b>tutto</b>, barra di navigazione compresa, e non e' un vezzo: mentre
 * suona una sveglia non deve esserci nient'altro da toccare. Chi si e' appena
 * svegliato preme quello che vede, e se accanto al « basta » c'e' il tasto
 * della radio lo preme.
 *
 * Sta sopra il {@link Telaio} e non in un'altra Activity. Con il lock task
 * attivo, lanciare una seconda Activity vuole che il pacchetto stia nei
 * lockTaskPackages e che quella entri nel task bloccato: e' una scommessa
 * inutile, quando Casa e' la Home e non esiste il caso in cui non sia gia' in
 * primo piano.
 *
 * <h3>Due tasti, e uno e' grande il doppio</h3>
 *
 * « Basta » e « ancora cinque minuti » non hanno lo stesso peso: il primo e' la
 * cosa che si vuole quasi sempre, il secondo e' la scappatoia. Farli uguali
 * vuol dire premere quello sbagliato a occhi chiusi. E per il timer il secondo
 * non c'e' proprio: la pasta rimandata di cinque minuti non e' rimandata, e'
 * scotta.
 *
 * <h3>Il tocco a vuoto non zittisce</h3>
 *
 * Toccare il vetro fuori dai tasti non fa niente, di proposito: una manica che
 * sfiora lo schermo non deve poter spegnere una sveglia. Ci vuole un tasto.
 */
public class VelaAllarme extends View implements Telaio.Velata {

    /** Cosa succede quando si preme. */
    public interface Scelta {
        void basta();
        void rimanda();
    }

    private final Misure m;
    private final Paint pVelo   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitolo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pOra    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTasto  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSegno  = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** I fondi piatti: il cerchio della campanella e i due tasti. */
    private final Paint pFondo  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF card    = new RectF();
    private final RectF tBasta  = new RectF();
    private final RectF tAncora = new RectF();

    private final Testo.Riga rTitolo = new Testo.Riga();
    private final Calendar cal = Calendar.getInstance();

    private Vetro vetro;
    private final String titolo;
    private final boolean sveglia;
    private final Scelta scelta;

    private float corpoTitolo, corpoOra, corpoTasto;
    private int premuto = -1;

    public VelaAllarme(Context c, Misure misure, Vetro v, String titolo, boolean sveglia,
                       Scelta scelta) {
        super(c);
        this.m = misure;
        this.vetro = v;
        this.titolo = titolo;
        this.sveglia = sveglia;
        this.scelta = scelta;

        // Senza clickable Android non consegna ACTION_DOWN, e senza il DOWN non
        // arriva mai l'UP: la View sembra morta al tocco.
        setClickable(true);

        pVelo.setColor(0xE60B0F14);        // quasi opaco: quello che c'era sotto
                                           // resta intuibile, non leggibile
        pTitolo.setColor(Tinte.TESTO_MEDIO);
        pTitolo.setTextAlign(Paint.Align.CENTER);
        pTitolo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pOra.setColor(Tinte.TESTO);
        pOra.setTextAlign(Paint.Align.CENTER);
        pOra.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        pTasto.setTextAlign(Paint.Align.CENTER);
        pTasto.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pSegno.setStyle(Paint.Style.STROKE);
        pSegno.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override public void setVetro(Vetro v) { vetro = v; invalidate(); }

    /** Non si e' presa niente da restituire: suona, e chi la ferma la ferma
     *  con il suo tasto. */
    @Override public void suVelaTolta() { }

    @Override
    public boolean hasOverlappingRendering() { return false; }

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        float mg = m.margine;
        card.set(w * 0.16f, h * 0.13f, w * 0.84f, h * 0.87f);

        corpoTitolo = h * 0.050f;
        corpoOra    = h * 0.190f;
        corpoTasto  = h * 0.045f;
        pTitolo.setTextSize(corpoTitolo);
        pOra.setTextSize(corpoOra);
        pTasto.setTextSize(corpoTasto);
        pSegno.setStrokeWidth(Math.max(3f, h * 0.007f));

        float altezzaTasto = Math.max(m.bersaglio * 1.6f, h * 0.145f);
        float fondo = card.bottom - mg * 1.4f;
        if (sveglia) {
            // Due terzi al « basta », un terzo scarso al rinvio: la differenza
            // di misura e' quello che li distingue a occhi socchiusi.
            float taglio = card.left + card.width() * 0.60f;
            tBasta.set(card.left + mg, fondo - altezzaTasto, taglio - mg * 0.4f, fondo);
            tAncora.set(taglio + mg * 0.4f, fondo - altezzaTasto, card.right - mg, fondo);
        } else {
            tBasta.set(card.left + mg, fondo - altezzaTasto, card.right - mg, fondo);
            tAncora.setEmpty();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), pVelo);
        if (vetro == null || !vetro.vivo()) return;

        int colore = sveglia ? Tinte.OROLOGIO : Tinte.ALLARME;
        vetro.pannello(c, card, m.raggio, colore, Tinte.VELO_QUIETO);

        float cx = card.centerX();

        // La campanella in un cerchio velato del suo colore: e' il segnale che
        // si legge prima delle parole, e da lontano e' l'unico. Un cerchio
        // piatto al posto dell'alone, che era un gradiente nuovo a fotogramma.
        float cyCampana = card.top + m.margine * 1.6f + card.height() * 0.13f;
        pFondo.setColor(Tinte.con(colore, 0x2E));
        c.drawCircle(cx, cyCampana, card.height() * 0.16f, pFondo);
        campanella(c, cx, cyCampana + card.height() * 0.012f, card.height() * 0.085f, colore);

        pTitolo.setColor(Tinte.TESTO_MEDIO);
        float yTitolo = cyCampana + card.height() * 0.20f;
        c.drawText(rTitolo.adatta(pTitolo, titolo, card.width() - m.margine * 2f,
                        corpoTitolo, corpoTitolo * 0.7f),
                   cx, yTitolo, pTitolo);

        // L'ora vera, grande: chi si sveglia di soprassalto la prima cosa che
        // vuole sapere e' che ore sono, non che c'e' una sveglia.
        cal.setTimeInMillis(System.currentTimeMillis());
        String ora = String.format(Locale.ITALIAN, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        pOra.setColor(Tinte.TESTO);
        pOra.setTextSize(corpoOra);
        float yOra = (yTitolo + tBasta.top) / 2f;
        c.drawText(ora, cx, yOra - (pOra.descent() + pOra.ascent()) / 2f, pOra);

        tasto(c, tBasta, "Basta", colore, premuto == 0, true);
        if (sveglia) tasto(c, tAncora, "Ancora 5 minuti", Tinte.TESTO_TENUE, premuto == 1, false);
    }

    private void tasto(Canvas c, RectF b, String testo, int colore, boolean giu, boolean pieno) {
        // Capsule piene, come i tasti della sveglia di iOS: « basta » del
        // colore dell'allarme, il rinvio grigio. Premuti si scuriscono.
        int fondo = pieno ? colore : Tinte.RIEMPIMENTO_SCELTO;
        if (giu) fondo = pieno ? Tinte.fondi(colore, 0xFF000000, 0.2f) : Tinte.SEGMENTO_SCELTO;
        pFondo.setColor(fondo);
        c.drawRoundRect(b, b.height() * 0.5f, b.height() * 0.5f, pFondo);
        // Sull'ambra della sveglia il testo scuro; sul rosso del timer e sul
        // grigio, bianco.
        pTasto.setColor(pieno && sveglia ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO);
        pTasto.setTextSize(corpoTasto);
        // Il nome del tasto si rimpicciolisce se non ci sta, invece di uscire:
        // « Ancora 5 minuti » in un terzo di scheda e' proprio il caso.
        float largo = b.width() - m.margine;
        while (pTasto.measureText(testo) > largo && pTasto.getTextSize() > corpoTasto * 0.6f) {
            pTasto.setTextSize(pTasto.getTextSize() * 0.92f);
        }
        c.drawText(testo, b.centerX(),
                b.centerY() - (pTasto.descent() + pTasto.ascent()) / 2f, pTasto);
    }

    /** Una campanella: la campana, il battaglio e il manico. */
    private void campanella(Canvas c, float cx, float cy, float r, int colore) {
        pSegno.setColor(colore);
        pSegno.setStyle(Paint.Style.STROKE);
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 200f, 140f, false, pSegno);
        c.drawLine(cx - r, cy + r * 0.34f, cx + r, cy + r * 0.34f, pSegno);
        c.drawLine(cx - r * 0.94f, cy + r * 0.34f, cx - r * 0.94f, cy - r * 0.10f, pSegno);
        c.drawLine(cx + r * 0.94f, cy + r * 0.34f, cx + r * 0.94f, cy - r * 0.10f, pSegno);
        pSegno.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy + r * 0.62f, r * 0.17f, pSegno);
        c.drawCircle(cx, cy - r * 0.96f, r * 0.12f, pSegno);
        pSegno.setStyle(Paint.Style.STROKE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                premuto = quale(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                int su = quale(x, y);
                if (su >= 0 && su == premuto) {
                    if (su == 0) scelta.basta();
                    else scelta.rimanda();
                }
                premuto = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                premuto = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int quale(float x, float y) {
        if (tBasta.contains(x, y)) return 0;
        if (sveglia && tAncora.contains(x, y)) return 1;
        return -1;
    }
}
