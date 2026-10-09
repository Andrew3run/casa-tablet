package dev.casa.telefono;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * La ghiera di Casa, portata sul telefono: la stessa figura che sul tablet
 * sceglie timer e sveglie (docs/orologio.md, « Una ghiera che scorre »).
 *
 * <ul>
 * <li><b>Due anelli.</b> Timer: fuori i minuti (sessanta scatti, un numero
 *     ogni cinque), dentro le ore da zero a cinque. Sveglia: fuori le ore,
 *     dentro i minuti a passi di cinque.</li>
 * <li><b>Si trascina, non si spinge</b>: il numero sotto il dito ci resta.</li>
 * <li><b>Fa tic</b> a ogni scatto, con {@code CLOCK_TICK}.</li>
 * <li><b>Si aggancia</b> allo scatto piu' vicino in 180 ms al rilascio.</li>
 * <li><b>Un tocco su un numero ci va</b>: su un telefono il numero lo si
 *     vede, e girare fino a li' sarebbe un gesto in piu'.</li>
 * <li><b>Girano le posizioni, non le cifre</b>: i numeri restano dritti, e
 *     quelli lontani dalla finestra in alto sbiadiscono.</li>
 * </ul>
 *
 * Nessun {@code Path}: cerchi, linee e testo.
 */
final class Ghiera extends View {

    static final int TIMER = 0, SVEGLIA = 1;

    interface Ascolto { void cambiata(boolean finito); }

    private final Stile s;
    private int modo = -1;
    private int nEst, nInt, passoEtichetteEst;
    private float offEst, offInt;
    private String[] etichetteEst, etichetteInt;
    private Ascolto ascolto;

    private final Paint pCifra = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTacca = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAnello = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFilo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIncavo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCentro = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pValore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pUnita = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFinestra = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF finestraEst = new RectF(), finestraInt = new RectF();

    private float cx, cy, rEst, rInt, rCentro, rEtichetteEst, rEtichetteInt;

    private int anello = -1, ultimoScatto;
    private double angoloPrima;
    private float giaMosso, xDown, yDown;
    private ValueAnimator aggancio;

    Ghiera(Stile s) {
        super(s.c);
        this.s = s;
        pCifra.setTextAlign(Paint.Align.CENTER);
        pCifra.setTypeface(s.medio);
        pTacca.setStrokeCap(Paint.Cap.ROUND);
        pAnello.setColor(Stile.VETRO);
        pFilo.setStyle(Paint.Style.STROKE);
        pFilo.setStrokeWidth(s.dp(1));
        pFilo.setColor(Stile.FILO);
        pIncavo.setColor(0x33000000);
        pCentro.setColor(0x14FFFFFF);
        pValore.setTextAlign(Paint.Align.CENTER);
        pValore.setTypeface(s.medio);
        pValore.setColor(Stile.TESTO);
        pValore.setLetterSpacing(-0.02f);
        pUnita.setTextAlign(Paint.Align.CENTER);
        pUnita.setTypeface(s.medio);
        pUnita.setColor(Stile.SECONDO);
        pUnita.setLetterSpacing(0.1f);
        pFinestra.setColor(0x2EF2D06B);
        setHapticFeedbackEnabled(true);
        setModo(TIMER);
    }

    void setAscolto(Ascolto a) { ascolto = a; }

    void setModo(int m) {
        if (m == modo) return;
        modo = m;
        if (m == TIMER) {
            nEst = 60; passoEtichetteEst = 5; nInt = 6;
            etichetteEst = new String[60];
            for (int i = 0; i < 60; i++) etichetteEst[i] = String.valueOf(i);
            etichetteInt = new String[] { "0", "1", "2", "3", "4", "5" };
        } else {
            nEst = 24; passoEtichetteEst = 1; nInt = 12;
            etichetteEst = new String[24];
            for (int i = 0; i < 24; i++) etichetteEst[i] = String.valueOf(i);
            etichetteInt = new String[12];
            for (int i = 0; i < 12; i++) etichetteInt[i] = String.format(java.util.Locale.ROOT, "%02d", i * 5);
        }
        offEst = offInt = 0;
        invalidate();
    }

    int modo() { return modo; }

    void set(int esterno, int interno) {
        if (aggancio != null) aggancio.cancel();
        offEst = giro(esterno, nEst);
        offInt = giro(interno, nInt);
        invalidate();
    }

    int esterno() { return giro(Math.round(offEst), nEst); }
    int interno() { return giro(Math.round(offInt), nInt); }

    private static int giro(int v, int n) { return ((v % n) + n) % n; }

    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        int lato = Math.min(w, s.dp(320));
        setMeasuredDimension(w, lato);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        rEst = h / 2f - s.dp(2);
        rEtichetteEst = rEst - s.dp(26);
        rInt = rEst - s.dp(52);
        rEtichetteInt = rInt - s.dp(20);
        rCentro = rInt - s.dp(42);
        float a = s.dp(17);
        finestraEst.set(cx - a, cy - rEtichetteEst - a, cx + a, cy - rEtichetteEst + a);
        float b = s.dp(15);
        finestraInt.set(cx - b, cy - rEtichetteInt - b, cx + b, cy - rEtichetteInt + b);
        pValore.setTextSize(rCentro * 0.62f);
        pUnita.setTextSize(s.dp(11));
    }

    @Override protected void onDraw(Canvas c) {
        c.drawCircle(cx, cy, rEst, pAnello);
        c.drawCircle(cx, cy, rEst - s.dp(0.5f), pFilo);
        c.drawCircle(cx, cy, rInt, pIncavo);
        c.drawCircle(cx, cy, rCentro, pCentro);

        c.drawRoundRect(finestraEst, s.dp(17), s.dp(17), pFinestra);
        c.drawRoundRect(finestraInt, s.dp(15), s.dp(15), pFinestra);

        // Le tacche dell'anello esterno.
        for (int i = 0; i < nEst; i++) {
            double ang = (i - offEst) * 2 * Math.PI / nEst;
            float vicino = vicinanza(ang);
            boolean lunga = i % passoEtichetteEst == 0;
            float r0 = rEst - s.dp(lunga ? 9 : 5), r1 = rEst - s.dp(2);
            float sn = (float) Math.sin(ang), cs = (float) Math.cos(ang);
            pTacca.setStrokeWidth(s.dp(lunga ? 1.6f : 1f));
            pTacca.setColor(Stile.conAlfa(0xFFFFFF, (int) (40 + 120 * vicino)));
            c.drawLine(cx + sn * r0, cy - cs * r0, cx + sn * r1, cy - cs * r1, pTacca);
        }

        int sceltoEst = esterno(), sceltoInt = interno();
        disegnaEtichette(c, etichetteEst, nEst, offEst, rEtichetteEst, passoEtichetteEst,
                modo == TIMER ? s.dp(15) : s.dp(13), sceltoEst);
        disegnaEtichette(c, etichetteInt, nInt, offInt, rEtichetteInt, 1, s.dp(13), sceltoInt);

        String valore, unita;
        if (modo == TIMER) {
            int ore = sceltoInt, minuti = sceltoEst;
            valore = ore > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d", ore, minuti) : String.valueOf(minuti);
            unita = ore > 0 ? "ORE" : (minuti == 1 ? "MINUTO" : "MINUTI");
        } else {
            valore = String.format(java.util.Locale.ROOT, "%02d:%02d", sceltoEst, sceltoInt * 5);
            unita = "SVEGLIA";
        }
        c.drawText(valore, cx, cy + pValore.getTextSize() * 0.3f, pValore);
        c.drawText(unita, cx, cy + pValore.getTextSize() * 0.3f + s.dp(20), pUnita);
    }

    private void disegnaEtichette(Canvas c, String[] etichette, int n, float off, float r,
                                  int passo, float corpo, int scelto) {
        for (int i = 0; i < n; i += passo) {
            double ang = (i - off) * 2 * Math.PI / n;
            float vicino = vicinanza(ang);
            pCifra.setTextSize(corpo * (0.82f + 0.18f * vicino));
            boolean q = i == scelto;
            pCifra.setColor(q ? Stile.ACCENTO : Stile.conAlfa(Stile.TESTO, (int) (70 + 185 * vicino)));
            float x = cx + (float) Math.sin(ang) * r;
            float y = cy - (float) Math.cos(ang) * r + pCifra.getTextSize() * 0.36f;
            c.drawText(etichette[i], x, y, pCifra);
        }
    }

    /** 1 sotto la finestra in alto, 0 dall'altra parte del cerchio. */
    private static float vicinanza(double ang) {
        double a = Math.abs(Math.IEEEremainder(ang, 2 * Math.PI));
        return (float) (1 - a / Math.PI);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX() - cx, y = e.getY() - cy;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                double r = Math.hypot(x, y);
                if (r > rEst + s.dp(12) || r < rCentro) return false;
                anello = r >= rInt ? 0 : 1;
                angoloPrima = Math.atan2(x, -y);
                giaMosso = 0;
                xDown = e.getX();
                yDown = e.getY();
                ultimoScatto = anello == 0 ? esterno() : interno();
                if (aggancio != null) aggancio.cancel();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (anello < 0) return false;
                giaMosso = Math.max(giaMosso, (float) Math.hypot(e.getX() - xDown, e.getY() - yDown));
                double a = Math.atan2(x, -y);
                double delta = a - angoloPrima;
                while (delta > Math.PI) delta -= 2 * Math.PI;
                while (delta < -Math.PI) delta += 2 * Math.PI;
                angoloPrima = a;
                if (anello == 0) offEst -= (float) (delta * nEst / (2 * Math.PI));
                else offInt -= (float) (delta * nInt / (2 * Math.PI));
                int scatto = anello == 0 ? esterno() : interno();
                if (scatto != ultimoScatto) {
                    ultimoScatto = scatto;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (ascolto != null) ascolto.cambiata(false);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (anello < 0) return false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (e.getActionMasked() == MotionEvent.ACTION_UP && giaMosso < s.dp(8)) {
                    // Un tocco: si va al numero toccato, se era su un numero.
                    double a = Math.atan2(x, -y);
                    int n = anello == 0 ? nEst : nInt;
                    float off = anello == 0 ? offEst : offInt;
                    float dove = off + (float) (a * n / (2 * Math.PI));
                    int passo = anello == 0 ? passoEtichetteEst : 1;
                    int bersaglio = Math.round(dove / passo) * passo;
                    aggancia(anello, bersaglio);
                } else {
                    aggancia(anello, Math.round(anello == 0 ? offEst : offInt));
                }
                anello = -1;
                return true;
            }
        }
        return false;
    }

    private void aggancia(final int quale, float bersaglio) {
        final float da = quale == 0 ? offEst : offInt;
        if (aggancio != null) aggancio.cancel();
        aggancio = ValueAnimator.ofFloat(da, bersaglio);
        aggancio.setDuration(180);
        aggancio.setInterpolator(new DecelerateInterpolator());
        aggancio.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float v = (Float) a.getAnimatedValue();
                if (quale == 0) offEst = v; else offInt = v;
                invalidate();
            }
        });
        aggancio.addListener(new AnimatorListenerAdapter() {
            private boolean annullato;
            @Override public void onAnimationCancel(Animator a) { annullato = true; }
            @Override public void onAnimationEnd(Animator a) {
                if (annullato) return;
                int scatto = quale == 0 ? esterno() : interno();
                if (scatto != ultimoScatto) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (ascolto != null) ascolto.cambiata(true);
            }
        });
        aggancio.start();
    }
}
