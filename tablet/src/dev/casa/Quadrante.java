package dev.casa;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * La ghiera che scorre: due anelli di numeri che girano attorno a un
 * indicatore fermo in cima.
 *
 * <h3>Perche' una ghiera e non due frecce</h3>
 *
 * Le frecce <b>+</b> e <b>−</b> costano un tocco per passo. Portare una sveglia
 * dalle 7 alle 22 sono quindici tocchi, e quindici tocchi su un apparecchio
 * appeso al muro sono quindici occasioni di premere accanto. Una ghiera fa lo
 * stesso viaggio con un gesto solo, e - questa e' la parte che conta - <b>mentre
 * lo fa mostra dove si sta andando</b>: si vedono passare le ore, quindi ci si
 * ferma quando si e' arrivati invece di contare.
 *
 * <h3>Perche' un cerchio e non un rullo verticale</h3>
 *
 * Il rullo (il selettore di Android, e quello di ogni telefono) e' un cerchio
 * visto di taglio: l'illusione della profondita' finge la curva che qui c'e'
 * davvero. Su uno schermo largo tenuto a un metro, quel finto rilievo non si
 * legge - e soprattutto un cerchio di ore <b>e' gia' la forma di un orologio</b>.
 * Le ore stanno dove ce le si aspetta, e il quadrante fa da orologio anche
 * mentre lo si usa da tastiera: il punto luminoso sul bordo e' il minuto vero,
 * adesso.
 *
 * <h3>Come si comporta</h3>
 *
 * <b>L'anello si trascina, non si spinge.</b> Il numero sotto il dito ci resta:
 * si converte lo spostamento angolare del dito in giri della ghiera, invece di
 * far scorrere di una quantita' inventata. E' la differenza fra girare una
 * manopola e premere un tasto che ripete.
 *
 * <b>Si aggancia allo scatto piu' vicino.</b> Al rilascio la ghiera si porta al
 * valore intero con un'animazione corta: senza, si resterebbe fra due ore e non
 * si saprebbe quale e' stata scelta.
 *
 * <b>Fa tic.</b> Un colpo di ritorno aptico a ogni scatto attraversato -
 * {@code CLOCK_TICK}, che e' proprio la costante fatta per questo. Su una
 * ghiera senza attrito il tic e' l'unica cosa che dice quanti passi si sono
 * fatti senza doverli leggere.
 *
 * <b>I numeri restano dritti.</b> Ruotare anche i glifi sarebbe piu' fedele a
 * una manopola vera e illeggibile a meta' giro: qui girano le <i>posizioni</i>,
 * non le cifre. Quelli lontani dall'indicatore sbiadiscono, cosi' l'occhio va
 * dove serve senza che il resto sparisca - e il cerchio resta un cerchio.
 *
 * Non e' una View: e' un pezzo di disegno che {@link SezioneOrologio} ospita
 * dentro la sua. Una View in piu' vorrebbe dire un livello in piu' da comporre
 * per il framework, e qui non ce n'e' bisogno - la disciplina del progetto e'
 * una View disegnata a mano per sezione.
 */
public final class Quadrante {

    /** Quanto dura l'aggancio allo scatto. Corto: e' una conferma, non
     *  un'animazione da guardare. */
    private static final int AGGANCIO_MS = 160;

    /** Oltre questa distanza dall'indicatore il numero e' quasi trasparente.
     *  Non del tutto: un cerchio con meta' numeri mancanti non sembra un
     *  cerchio, sembra un errore di disegno. */
    private static final int ALFA_VICINO = 0xFF, ALFA_LONTANO = 0x5C;

    /** Chi ospita la ghiera: serve a ridisegnare e a far vibrare. */
    private final View casa;

    private final Paint pNumero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTacca  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pArco   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCentro = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAnello = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF appoggio = new RectF();

    private float cx, cy, raggio;
    private float rEsterno, rInterno;
    private float corpoEsterno, corpoInterno, corpoCentro, corpoUnita;

    /** Quanti scatti ha ogni anello, e le scritte gia' pronte. */
    private int slotEsterni = 24, slotInterni = 12;
    private String[] etichetteEsterne = new String[0];
    private String[] etichetteInterne = new String[0];

    /**
     * La posizione delle due ghiere, in scatti <b>frazionari</b>.
     *
     * Frazionaria e non intera perche' durante il trascinamento la ghiera sta
     * fra due valori, ed e' proprio quello che la fa sembrare una manopola
     * invece di un elenco che salta.
     */
    private float posEsterna, posInterna;

    private int anelloTrascinato = -1;      // 0 esterno, 1 interno
    private float angoloPrecedente;
    private int ultimoScatto;
    private ValueAnimator aggancio;

    private int tinta = Tinte.OROLOGIO;

    /** Le due fasce: l'esterna un filo piu' chiara, perche' e' quella che sta
     *  sotto il pollice per prima. */
    private static final int FASCIA_ESTERNA = 0x1AFFFFFF;
    private static final int FASCIA_INTERNA = 0x0DFFFFFF;

    /** Chi vuole sapere che il valore e' cambiato. */
    public interface Cambio {
        /** Durante il trascinamento, a ogni scatto: solo per lo schermo. */
        void suScatto();
        /** Dito alzato e ghiera agganciata: adesso si puo' salvare. */
        void suFermata();
    }

    private Cambio cambio;

    public Quadrante(View casa) {
        this.casa = casa;
        pNumero.setTextAlign(Paint.Align.CENTER);
        pNumero.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pTacca.setStyle(Paint.Style.STROKE);
        pTacca.setStrokeCap(Paint.Cap.ROUND);
        pArco.setStyle(Paint.Style.STROKE);
        pArco.setStrokeCap(Paint.Cap.ROUND);
        pCentro.setTextAlign(Paint.Align.CENTER);
        pCentro.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        pAnello.setStyle(Paint.Style.STROKE);
    }

    public void setCambio(Cambio c) { cambio = c; }

    public void setTinta(int t) { tinta = t; }

    /** Dove sta e quanto e' grande. */
    public void posiziona(float cx, float cy, float raggio) {
        this.cx = cx;
        this.cy = cy;
        // Fuori dalle fasce resta il posto per l'anello del timer.
        this.raggio = raggio * 0.93f;
        raggio = this.raggio;
        // I due anelli, e l'aria fra loro.
        //
        // Prima stavano a 0,845 e 0,615 del raggio con numeri grandi il 11,5%
        // e il 10%: due corone larghe quasi quanto lo spazio che le separava,
        // e da un metro <b>i due giri di numeri si leggevano come uno solo</b>,
        // sessanta cifre mescolate a sei. Adesso l'esterno e' piu' fuori,
        // l'interno piu' dentro, e i numeri sono piu' piccoli: fra le due
        // corone resta un anello di vuoto largo quanto una cifra, che e' la
        // sola cosa che le fa leggere come due cose diverse.
        // 0,835 e non 0,880: a 0,880 i numeri arrivavano a sei pixel dalle
        // tacche, e da un metro un « 50 » con un trattino attaccato si legge
        // come un numero solo, sporco. Fra la corona dei numeri e quella delle
        // tacche ci vuole aria quanto mezza cifra.
        rEsterno = raggio * 0.835f;
        rInterno = raggio * 0.545f;
        corpoEsterno = raggio * 0.092f;
        corpoInterno = raggio * 0.080f;
        corpoCentro  = raggio * 0.330f;
        corpoUnita   = raggio * 0.082f;
        pTacca.setStrokeWidth(Math.max(1.5f, raggio * 0.010f));
        pArco.setStrokeWidth(Math.max(4f, raggio * 0.034f));
        pAnello.setStrokeWidth(Math.max(1f, raggio * 0.005f));
    }

    /**
     * Rifa' i due anelli. Le etichette arrivano gia' composte da chi chiama:
     * comporle qui vorrebbe dire allocare a ogni cambio di scheda, e per un
     * elenco che e' sempre lo stesso.
     */
    public void anelli(String[] esterne, String[] interne) {
        etichetteEsterne = esterne;
        etichetteInterne = interne;
        slotEsterni = esterne.length;
        slotInterni = interne.length;
        posEsterna = normalizza(posEsterna, slotEsterni);
        posInterna = normalizza(posInterna, slotInterni);
    }

    /** Porta la ghiera su due valori, senza animazione: la si usa quando si
     *  sceglie un'altra sveglia dall'elenco. */
    public void vaiA(int esterno, int interno) {
        fermaAggancio();
        posEsterna = normalizza(esterno, slotEsterni);
        posInterna = normalizza(interno, slotInterni);
    }

    public int esterno() { return (int) normalizza(Math.round(posEsterna), slotEsterni); }
    public int interno() { return (int) normalizza(Math.round(posInterna), slotInterni); }

    public boolean staTrascinando() { return anelloTrascinato >= 0; }

    // ---- disegno ------------------------------------------------------------

    /**
     * @param centro     il valore grande in mezzo, gia' composto
     * @param unita      la parola sotto (« min », i giorni), o null
     * @param frazione   quanto dell'arco esterno colorare, da 0 a 1: e' il
     *                   tempo che manca al timer. Sotto zero, niente arco.
     */
    public void disegna(Canvas c, String centro, String unita, float frazione) {
        // Due corone piene, come le rotelle di un selettore di iOS.
        //
        // Prima erano sessanta tacche sul bordo, un filo fra i due anelli e un
        // puntino per il minuto vero: tre segni per dire « qui ci sono due
        // giri », e da un metro si leggeva una corona di segni sparsi. Adesso
        // ogni giro di numeri sta sulla sua fascia, e due fasce di grigio
        // diverso si leggono come due cose da girare senza bisogno d'altro.
        float confine = (rEsterno + rInterno) / 2f;
        float esterna = raggio, interna = rInterno - (confine - rInterno);
        pAnello.setStyle(Paint.Style.STROKE);
        pAnello.setStrokeWidth(esterna - confine);
        pAnello.setColor(FASCIA_ESTERNA);
        c.drawCircle(cx, cy, (esterna + confine) / 2f, pAnello);
        pAnello.setStrokeWidth(confine - interna);
        pAnello.setColor(FASCIA_INTERNA);
        c.drawCircle(cx, cy, (confine + interna) / 2f, pAnello);

        // La banda di selezione: dove stanno i due numeri scelti, in cima,
        // come la riga evidenziata di un selettore. Prende il posto del cuneo,
        // che puntava una sola delle due corone.
        float largaBanda = Math.max(corpoEsterno, corpoInterno) * 1.9f;
        appoggio.set(cx - largaBanda / 2f, cy - esterna + raggio * 0.025f,
                     cx + largaBanda / 2f, cy - interna - raggio * 0.025f);
        pCentro.setColor(Tinte.RIEMPIMENTO_SCELTO);
        c.drawRoundRect(appoggio, largaBanda / 2f, largaBanda / 2f, pCentro);

        // Il tempo che manca, come l'anello del timer di iOS: una traccia
        // grigia tutta intorno e l'arco colorato che si consuma dall'alto.
        if (frazione >= 0f) {
            float r = esterna + pArco.getStrokeWidth() * 1.1f;
            appoggio.set(cx - r, cy - r, cx + r, cy + r);
            pArco.setColor(Tinte.RIEMPIMENTO);
            c.drawCircle(cx, cy, r, pArco);
            pArco.setColor(tinta);
            c.drawArc(appoggio, -90f, Math.max(0.5f, 360f * frazione), false, pArco);
        }

        anello(c, etichetteEsterne, posEsterna, rEsterno, corpoEsterno);
        anello(c, etichetteInterne, posInterna, rInterno, corpoInterno);

        // Il valore scelto, grande, al centro.
        pCentro.setColor(Tinte.TESTO);
        pCentro.setTextSize(corpoCentro);
        // Dentro il foro, con un po' d'aria: « 07:00 » a corpo pieno toccava
        // la corona delle ore.
        float foro = (rInterno - (rEsterno - rInterno) / 2f) * 1.6f;
        float largo = pCentro.measureText(centro);
        if (largo > foro) pCentro.setTextSize(corpoCentro * foro / largo);
        float spostamento = unita != null ? corpoUnita * 0.7f : 0f;
        c.drawText(centro, cx,
                cy - (pCentro.descent() + pCentro.ascent()) / 2f - spostamento, pCentro);
        if (unita != null) {
            pCentro.setColor(Tinte.TESTO_TENUE);
            pCentro.setTextSize(corpoUnita);
            c.drawText(unita, cx, cy + corpoCentro * 0.50f, pCentro);
        }
    }


    /**
     * Un anello di numeri. Quelli lontani dall'indicatore sbiadiscono e
     * rimpiccioliscono: e' il modo di dire « questo » senza cancellare gli
     * altri, e con la ghiera in movimento e' anche quello che rende visibile
     * la rotazione.
     */
    private void anello(Canvas c, String[] etichette, float pos, float r, float corpo) {
        if (etichette.length == 0) return;
        float passo = 360f / etichette.length;
        for (int i = 0; i < etichette.length; i++) {
            if (etichette[i] == null) continue;
            float gradi = (i - pos) * passo;
            // Portato in [-180, 180]: e' la distanza vera dall'indicatore, e
            // senza questo il numero 23 accanto allo 0 sembrerebbe lontanissimo.
            while (gradi > 180f) gradi -= 360f;
            while (gradi < -180f) gradi += 360f;

            float vicinanza = 1f - Math.min(1f, Math.abs(gradi) / 90f);
            int alfa = (int) (ALFA_LONTANO + (ALFA_VICINO - ALFA_LONTANO) * vicinanza * vicinanza);
            pNumero.setColor(Tinte.con(Math.abs(gradi) < passo * 0.5f ? tinta : Tinte.TESTO,
                    alfa));
            pNumero.setTextSize(corpo * (0.78f + 0.22f * vicinanza));

            float ang = (float) Math.toRadians(gradi);
            float x = cx + (float) Math.sin(ang) * r;
            float y = cy - (float) Math.cos(ang) * r;
            c.drawText(etichette[i], x, y - (pNumero.descent() + pNumero.ascent()) / 2f, pNumero);
        }
    }

    // ---- tocco ---------------------------------------------------------------

    /** Il dito e' dentro la ghiera? Lo chiede la sezione prima di guardare
     *  altrove. */
    public boolean dentro(float x, float y) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= raggio * raggio;
    }

    /** @return true se ha preso il tocco. */
    public boolean giu(float x, float y) {
        float dx = x - cx, dy = y - cy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        // Il confine fra i due anelli sta in mezzo fra i due raggi, non sul
        // raggio di uno dei due: cosi' ogni anello ha la sua meta' di corona e
        // non c'e' una fascia che non risponde a nessuno.
        float confine = (rEsterno + rInterno) / 2f;
        if (d > raggio * 1.05f) return false;
        if (d < rInterno * 0.55f) return false;      // il centro non e' ghiera
        fermaAggancio();
        anelloTrascinato = d >= confine ? 0 : 1;
        angoloPrecedente = angolo(x, y);
        ultimoScatto = anelloTrascinato == 0 ? esterno() : interno();
        return true;
    }

    public void muovi(float x, float y) {
        if (anelloTrascinato < 0) return;
        float adesso = angolo(x, y);
        float delta = adesso - angoloPrecedente;
        while (delta > 180f) delta -= 360f;
        while (delta < -180f) delta += 360f;
        angoloPrecedente = adesso;

        // Il numero sotto il dito ci resta: il dito gira di delta, la ghiera
        // gira di delta, quindi il valore in cima scala di delta/passo.
        if (anelloTrascinato == 0) {
            posEsterna = normalizza(posEsterna - delta / (360f / slotEsterni), slotEsterni);
        } else {
            posInterna = normalizza(posInterna - delta / (360f / slotInterni), slotInterni);
        }

        int adessoScatto = anelloTrascinato == 0 ? esterno() : interno();
        if (adessoScatto != ultimoScatto) {
            ultimoScatto = adessoScatto;
            // CLOCK_TICK e non una vibrazione a mano: e' la costante fatta
            // apposta per le ghiere, e su un ROM che non la implementa non fa
            // niente invece di ronzare.
            casa.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            if (cambio != null) cambio.suScatto();
        }
        casa.invalidate();
    }

    public void su() {
        if (anelloTrascinato < 0) return;
        final boolean esterna = anelloTrascinato == 0;
        anelloTrascinato = -1;
        float da = esterna ? posEsterna : posInterna;
        final float a = Math.round(da);
        agganciaA(esterna, da, a);
    }

    public void annulla() {
        anelloTrascinato = -1;
    }

    private void agganciaA(final boolean esterna, float da, final float a) {
        fermaAggancio();
        if (Math.abs(a - da) < 0.001f) {
            if (cambio != null) cambio.suFermata();
            casa.invalidate();
            return;
        }
        aggancio = ValueAnimator.ofFloat(da, a);
        aggancio.setDuration(AGGANCIO_MS);
        aggancio.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                float v = (Float) an.getAnimatedValue();
                if (esterna) posEsterna = normalizza(v, slotEsterni);
                else posInterna = normalizza(v, slotInterni);
                casa.invalidate();
            }
        });
        aggancio.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) {
                // Solo qui si salva. Durante il trascinamento la sveglia cambia
                // a schermo e basta: ogni scatto riscriverebbe il file e
                // riprogrammerebbe l'allarme, e mezzo giro di ghiera sono venti
                // scatti.
                if (cambio != null) cambio.suFermata();
            }
        });
        aggancio.start();
    }

    private void fermaAggancio() {
        if (aggancio != null) { aggancio.cancel(); aggancio = null; }
    }

    /** L'angolo del dito rispetto al centro: zero in cima, positivo in senso
     *  orario. E' la stessa convenzione con cui si disegnano i numeri, e
     *  tenerne una sola evita un segno sbagliato ogni volta che si tocca. */
    private float angolo(float x, float y) {
        return (float) Math.toDegrees(Math.atan2(x - cx, cy - y));
    }

    private static float normalizza(float v, int quanti) {
        if (quanti <= 0) return 0f;
        float r = v % quanti;
        return r < 0 ? r + quanti : r;
    }
}
