package dev.casa;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * La barra delle sei sezioni.
 *
 * <b>Sta a sinistra, non in basso.</b> Lo schermo e' 1280x800 in orizzontale:
 * l'altezza e' la risorsa scarsa, ed e' quella che serve all'orologio grande e
 * alle griglie. Una barra in basso alta 130 px si mangia il 16% dell'altezza; a
 * sinistra larga altrettanto si mangia il 10% della larghezza, che e' quella
 * che avanza. E cinque voci distribuite su 800 px di altezza danno 160 px per
 * voce, contro i 230 px orizzontali che verrebbero in basso: il bersaglio e'
 * piu' comodo, non meno.
 *
 * L'indicatore non salta: scorre. Su una schermata che sta ferma tutto il
 * giorno, i due movimenti che ci sono devono essere fatti bene.
 */
public class BarraSezioni extends View {

    public interface Cambio { void suSezione(int indice); }

    private final Misure m;
    private final Sezione[] sezioni;
    private Vetro vetro;
    private Cambio cambio;

    private final Paint pIcona   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTesto   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAcceso  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSpia    = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF area      = new RectF();
    private final RectF indicatore= new RectF();

    private int corrente = 0;
    private int premuta  = -1;

    /** Dove sta l'indicatore adesso, in indici frazionari: 1.5 vuol dire a
     *  meta' strada fra la seconda e la terza voce. */
    private float posizione = 0f;
    private ValueAnimator scorrimento;

    /** Il pallino di "questa cosa e' in funzione": la radio che suona, il timer
     *  che scorre, una luce accesa. Si vede anche da un'altra sezione, ed e' il
     *  motivo per cui la barra e' sempre visibile. */
    private final boolean[] spie;

    private float passo, lato;

    /** Il corpo nominale del titolo, e lo spazio che ha nella colonna. */
    private float corpoTesto, larghezzaTesto;

    /** Un titolo per voce, misurato. La colonna e' larga centotrenta pixel: i
     *  cinque nomi di adesso ci stanno, ma il titolo di una sezione e' una
     *  parola che si cambia in una riga, e la colonna non si allarga con lei. */
    private final Testo.Riga[] rTitoli;

    public BarraSezioni(Context c, Misure misure, Sezione[] sezioni) {
        super(c);
        this.m = misure;
        this.sezioni = sezioni;
        this.spie = new boolean[sezioni.length];
        this.rTitoli = new Testo.Riga[sezioni.length];
        for (int i = 0; i < rTitoli.length; i++) rTitoli[i] = new Testo.Riga();
        setClickable(true);

        pIcona.setStyle(Paint.Style.STROKE);
        pIcona.setStrokeCap(Paint.Cap.ROUND);
        pTesto.setTextAlign(Paint.Align.CENTER);
        pTesto.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setVetro(Vetro v) { vetro = v; invalidate(); }
    public void setCambio(Cambio c) { cambio = c; }

    public void setSpia(int indice, boolean accesa) {
        if (indice < 0 || indice >= spie.length || spie[indice] == accesa) return;
        spie[indice] = accesa;
        invalidate();
    }

    /** Muove l'indicatore. La sezione la cambia Telaio, non la barra. */
    public void setCorrente(int indice) {
        if (indice == corrente) return;
        corrente = indice;
        if (scorrimento != null) scorrimento.cancel();
        scorrimento = ValueAnimator.ofFloat(posizione, indice);
        scorrimento.setDuration(220);
        scorrimento.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                posizione = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        scorrimento.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        passo = h / (float) sezioni.length;
        // L'icona segue la scala dell'app, non la sua fetta di barra: prima
        // era un quinto del passo e veniva grossa come il titolo che le stava
        // sotto, cosi' la voce sembrava tutta un blocco.
        lato = Math.min(m.icona * 1.15f, passo * 0.26f);
        corpoTesto = m.micro;
        larghezzaTesto = w * 0.86f;
        pTesto.setTextSize(corpoTesto);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (vetro == null || !vetro.vivo()) return;

        // Tutta la barra e' un pannello di vetro, dal bordo allo schermo, un
        // po' piu' scuro delle schede: come la barra laterale di iPadOS, sta
        // dietro al contenuto e non accanto.
        area.set(0, 0, w, h);
        vetro.pannello(c, area, 0f, 0xFF000000, 0x40);

        // L'indicatore: una pastiglia chiara e neutra dietro icona e nome. Il
        // colore della sezione resta all'icona, che si riempie: niente filo sul
        // bordo e niente alone, la pastiglia basta a dire « sei qui ».
        float cy = passo * (posizione + 0.5f);
        float meta = passo * 0.36f;
        indicatore.set(w * 0.12f, cy - meta, w * 0.88f, cy + meta);
        pAcceso.setColor(Tinte.RIEMPIMENTO);
        c.drawRoundRect(indicatore, m.raggio, m.raggio, pAcceso);

        for (int i = 0; i < sezioni.length; i++) {
            float centro = passo * (i + 0.5f);
            // Quanto questa voce e' "quella accesa": 1 quando l'indicatore le
            // sta sopra, 0 quando e' lontano. Cosi' colore e opacita' passano
            // da una voce all'altra insieme all'indicatore, invece di scattare
            // a meta' strada.
            float vicinanza = Math.max(0f, 1f - Math.abs(posizione - i));

            // Un'icona con un colore suo - il marchio Spotify - non prende
            // quello della sezione: si smorza quando e' altrove e torna piena
            // quando e' qui, ma resta sempre del suo verde.
            int suo = sezioni[i].coloreIcona();
            int colore = Tinte.fondi(Tinte.TESTO_TENUE,
                    suo != 0 ? suo : sezioni[i].tinta(), vicinanza);
            int coloreTesto = Tinte.fondi(Tinte.TESTO_TENUE, Tinte.TESTO, vicinanza);
            if (i == premuta) colore = coloreTesto = Tinte.TESTO;

            float cyIcona = centro - passo * 0.115f;

            // Contornata quando e' un altrove, piena quando e' qui. Il passaggio
            // avviene a meta' strada: durante lo scorrimento si vede la vecchia
            // svuotarsi e la nuova riempirsi, che e' il momento in cui si capisce
            // che la schermata sta cambiando.
            pIcona.setColor(colore);
            Icone.disegna(c, vicinanza > 0.5f ? sezioni[i].iconaPiena() : sezioni[i].icona(),
                    w / 2f, cyIcona, lato, pIcona);

            pTesto.setColor(coloreTesto);
            c.drawText(rTitoli[i].adatta(pTesto, sezioni[i].titolo(), larghezzaTesto,
                                         corpoTesto, corpoTesto * 0.75f),
                       w / 2f, centro + passo * 0.30f, pTesto);

            if (spie[i]) {
                pSpia.setColor(sezioni[i].tinta());
                c.drawCircle(w / 2f + lato * 0.62f, cyIcona - lato * 0.46f,
                        Math.max(2.5f, lato * 0.11f), pSpia);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                premuta = quale(e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                int i = quale(e.getY());
                if (i >= 0 && i == premuta && cambio != null) cambio.suSezione(i);
                premuta = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                premuta = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** Tutta la fascia e' sensibile, non la sola icona. */
    private int quale(float y) {
        if (passo <= 0) return -1;
        int i = (int) (y / passo);
        return (i >= 0 && i < sezioni.length) ? i : -1;
    }
}
