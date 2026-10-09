package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/**
 * Le impostazioni di Casa, quelle che si cambiano dal tablet.
 *
 * <h3>Tre « impostazioni », tre cose diverse</h3>
 *
 * <ul>
 *   <li>le <b>Impostazioni di Android</b>, che hanno la loro tessera e stanno
 *       nascoste per curare un bug del ROM ({@link Impostazioni});
 *   <li>il <b>pannello della parola</b>, che si apre solo dal PC perche' da li'
 *       si rompe il riconoscimento ({@link VelaImpostazioni});
 *   <li><b>questa pagina</b>: le cose di tutti i giorni che chi abita qui
 *       vuole poter cambiare senza il PC - a che ora comincia la notte, quanto
 *       si abbassa lo schermo, dopo quanto compare il riposo.
 * </ul>
 *
 * <h3>Due pannelli</h3>
 *
 * <pre>
 *   Impostazioni di Casa                                     [X]
 *   +-- MODALITA' NOTTE ------------+  +-- RIPOSO -----------+
 *   | Modalita' notte        [on]   |  | Riposo       [on]   |
 *   | Comincia alle   - 22:00 +     |  | Dopo      - 5 min + |
 *   | Finisce alle    - 07:00 +     |  |                     |
 *   | Luminosita'     -  15%  +     |  | spiegazione         |
 *   | [ PROVA LA LUMINOSITA' ]      |  |                     |
 *   | spiegazione                   |  |                     |
 *   +-------------------------------+  +---------------------+
 * </pre>
 *
 * Il riposo sta qui accanto perche' la notte senza riposo non fa niente: lo
 * schermo si abbassa trenta secondi dopo che compare la fotografia.
 *
 * <b>Si salva quando si alza il dito</b>, non a ogni scatto del meno e del piu':
 * tenendo premuto l'ora scorre di un quarto d'ora ogni decimo di secondo, e
 * scrivere {@code casa.json} dieci volte al secondo non serve a nessuno.
 */
public class VelaPreferenze extends View implements Telaio.Velata {

    public interface Regia { void chiudiPreferenze(); }

    private static final String TITOLO = "Impostazioni Assistente Home";

    /** I tasti, in un ordine solo per disegno e tocco. */
    private static final int CHIUDI = 0, NOTTE = 1, DA_MENO = 2, DA_PIU = 3,
            A_MENO = 4, A_PIU = 5, LUCE_MENO = 6, LUCE_PIU = 7, PROVA = 8,
            RIPOSO = 9, ATTESA_MENO = 10, ATTESA_PIU = 11, QUANTI = 12;

    /** I minuti di attesa che si possono scegliere: sotto i cinque conta il
     *  minuto, sopra conta la mezz'ora. */
    private static final int[] ATTESE = { 1, 2, 3, 5, 10, 15, 20, 30, 45, 60 };

    private static final int PASSO_ORA = 15, PASSO_LUCE = 5;

    /** Tenendo premuto: dopo quanto comincia a ripetere, e ogni quanto. */
    private static final long RIPETI_DOPO = 450, RIPETI_OGNI = 110;

    private final Misure m;
    private final Notte notte;
    private final Riposo riposo;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Vetro vetro;
    private Regia regia;

    private static final Typeface MEDIO   = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface NORMALE = Typeface.create("sans-serif", Typeface.NORMAL);

    private final Paint pVelo   = new Paint();
    private final Paint pTitolo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTesto  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTasto  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSegno  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF pNotte  = new RectF();
    private final RectF pRiposo = new RectF();
    private final RectF appoggio = new RectF();
    private final RectF[] tasti = new RectF[QUANTI];
    /** Dove si scrive il valore, fra meno e piu'. Uno per riga. */
    private final RectF vDa = new RectF(), vA = new RectF(), vLuce = new RectF(),
            vAttesa = new RectF();

    private final Testo.Blocco bNotte = new Testo.Blocco();
    private final Testo.Blocco bRiposo = new Testo.Blocco();

    // ---- i valori, com'erano all'entrata e come sono adesso ------------------

    private boolean nAcceso, rAcceso;
    private int da, a, luce, attesa;
    private boolean sporcaNotte, sporcoRiposo;

    private int premuto = -1;
    private long entrata;

    private final Runnable ripeti = new Runnable() {
        @Override public void run() {
            if (premuto < 0 || !ripetibile(premuto)) return;
            esegui(premuto);
            ui.postDelayed(this, RIPETI_OGNI);
        }
    };

    public VelaPreferenze(Context c, Misure misure, Vetro v, Notte notte, Riposo riposo) {
        super(c);
        this.m = misure;
        this.vetro = v;
        this.notte = notte;
        this.riposo = riposo;
        setClickable(true);
        for (int i = 0; i < QUANTI; i++) tasti[i] = new RectF();

        // Il fondo dei fogli di iOS: quasi nero, e lo sfondo appena sotto.
        pVelo.setColor(0xF2000000);
        pTitolo.setTypeface(MEDIO);
        pTesto.setTypeface(NORMALE);
        pTasto.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pTasto.setTextAlign(Paint.Align.CENTER);
    }

    public void setRegia(Regia r) { regia = r; }

    @Override public void setVetro(Vetro v) { vetro = v; invalidate(); }
    @Override public boolean hasOverlappingRendering() { return false; }

    public void suEntrata() {
        nAcceso = notte.acceso();
        da = notte.da();
        a = notte.a();
        luce = notte.luce();
        rAcceso = riposo.acceso();
        attesa = riposo.attesa();
        sporcaNotte = sporcoRiposo = false;
        entrata = Anima.ora();
        disponi();
        invalidate();
    }

    /** Comunque esca - la X, il tasto indietro, una sveglia - quello che si e'
     *  cambiato resta. */
    @Override public void suVelaTolta() {
        ui.removeCallbacksAndMessages(null);
        premuto = -1;
        salva();
        if (regia != null) regia.chiudiPreferenze();
    }

    private void salva() {
        if (sporcaNotte) notte.imposta(nAcceso, da, a, luce);
        if (sporcoRiposo) riposo.imposta(rAcceso, attesa);
        sporcaNotte = sporcoRiposo = false;
    }

    // ---- misure --------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        disponi();
    }

    private void disponi() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float mg = m.margine;

        float altaTesta = Math.max(m.bersaglio, m.titolo * 1.3f);
        tasti[CHIUDI].set(w - mg - m.bersaglio * 1.2f, mg, w - mg, mg + altaTesta * 0.86f);

        float cima = mg + altaTesta + m.s2;
        float fondo = h - mg;
        float taglio = mg + (w - mg * 2f) * 0.58f;
        pNotte.set(mg, cima, taglio, fondo);
        pRiposo.set(taglio + m.s3, cima, w - mg, fondo);

        float riga = Math.max(m.bersaglio, h * 0.088f);

        // ---- la notte ----
        float x0 = pNotte.left + m.s4, x1 = pNotte.right - m.s4;
        float y = primaRiga(pNotte);
        tasti[NOTTE].set(x0, y, x1, y + riga);
        y += riga + m.s2;
        rigaValore(y, riga, x1, DA_MENO, DA_PIU, vDa);
        y += riga + m.s2;
        rigaValore(y, riga, x1, A_MENO, A_PIU, vA);
        y += riga + m.s2;
        rigaValore(y, riga, x1, LUCE_MENO, LUCE_PIU, vLuce);
        y += riga + m.s3;
        tasti[PROVA].set(x0, y, x1, y + riga);

        // ---- il riposo ----
        x0 = pRiposo.left + m.s4;
        x1 = pRiposo.right - m.s4;
        y = primaRiga(pRiposo);
        tasti[RIPOSO].set(x0, y, x1, y + riga);
        y += riga + m.s2;
        rigaValore(y, riga, x1, ATTESA_MENO, ATTESA_PIU, vAttesa);
    }

    /** Sotto l'etichetta in maiuscolo del pannello. */
    private float primaRiga(RectF pannello) {
        return pannello.top + m.s3 + m.micro + m.s3;
    }

    /** Meno, valore e piu', allineati a destra: l'etichetta si prende quel che
     *  avanza a sinistra. */
    private void rigaValore(float y, float alta, float destra, int meno, int piu, RectF valore) {
        pTitolo.setTextSize(m.voce);
        float largoValore = Math.max(pTitolo.measureText("00:00") + m.s4 * 2f, alta * 1.8f);
        tasti[piu].set(destra - alta, y, destra, y + alta);
        valore.set(tasti[piu].left - largoValore, y, tasti[piu].left, y + alta);
        tasti[meno].set(valore.left - alta, y, valore.left, y + alta);
    }

    // ---- disegno -------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), pVelo);
        if (vetro == null || !vetro.vivo()) return;

        pTitolo.setTextSize(m.titolo);
        pTitolo.setColor(Tinte.TESTO);
        c.drawText(TITOLO, m.margine, tasti[CHIUDI].centerY() + m.titolo * 0.35f, pTitolo);
        tondo(c, tasti[CHIUDI], Icone.CHIUDI, Tinte.TESTO_TENUE, true, premuto == CHIUDI,
                tasti[CHIUDI].height() * 0.30f);

        int s0 = c.save();
        float avanti = Anima.posa(Anima.entrata(entrata, 0));
        if (avanti < 1f) c.translate(0f, (1f - avanti) * m.s5);
        disegnaNotte(c);
        c.restoreToCount(s0);

        s0 = c.save();
        avanti = Anima.posa(Anima.entrata(entrata, 1));
        if (avanti < 1f) c.translate(0f, (1f - avanti) * m.s5);
        disegnaRiposo(c);
        c.restoreToCount(s0);

        Anima.continua(this, entrata, 2);
    }

    private void disegnaNotte(Canvas c) {
        vetro.pannello(c, pNotte, m.raggio, Tinte.NOTTURNO, Tinte.VELO_QUIETO);
        etichetta(c, pNotte, "Notte");

        interruttore(c, tasti[NOTTE], "Modalita' notte", nAcceso, Tinte.NOTTURNO);
        valore(c, "Comincia alle", Notte.ora(da), vDa, DA_MENO, DA_PIU, nAcceso, true, true);
        valore(c, "Finisce alle", Notte.ora(a), vA, A_MENO, A_PIU, nAcceso, true, true);
        valore(c, "Luminosita'", luce + "%", vLuce, LUCE_MENO, LUCE_PIU, nAcceso,
                luce > Notte.LUCE_MIN, luce < Notte.LUCE_MAX);

        // Una capsula piatta, come i pulsanti grigi di iOS.
        RectF p = tasti[PROVA];
        pSegno.setColor(premuto == PROVA ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(p, p.height() / 2f, p.height() / 2f, pSegno);
        pTasto.setTextSize(m.corpo);
        pTasto.setColor(nAcceso ? Tinte.TESTO : Tinte.SPENTO);
        c.drawText("Prova la luminosità", p.centerX(),
                p.centerY() - (pTasto.descent() + pTasto.ascent()) / 2f, pTasto);

        String spiega;
        if (!nAcceso) {
            spiega = "Spenta: anche di notte il riposo resta alla luce di sempre.";
        } else if (da == a) {
            spiega = "Inizio e fine sono la stessa ora: la notte non comincia mai.";
        } else {
            spiega = "Dalle " + Notte.ora(da) + " alle " + Notte.ora(a)
                    + ", trenta secondi dopo che compare il riposo lo schermo scende al "
                    + luce + "%. Un tocco lo riaccende; quando il riposo torna, si riabbassa.";
            if (!rAcceso) spiega += " Ma il riposo e' spento, quindi per ora non succede.";
        }
        spiegazione(c, pNotte, p.bottom + m.s4, bNotte, spiega);
    }

    private void disegnaRiposo(Canvas c) {
        vetro.pannello(c, pRiposo, m.raggio, Tinte.APP, Tinte.VELO_QUIETO);
        etichetta(c, pRiposo, "Riposo");

        interruttore(c, tasti[RIPOSO], "Riposo", rAcceso, Tinte.APP);
        valore(c, "Dopo", attesa + " min", vAttesa, ATTESA_MENO, ATTESA_PIU, rAcceso,
                attesa > ATTESE[0], attesa < ATTESE[ATTESE.length - 1]);

        String spiega = rAcceso
                ? "La fotografia con l'ora compare dopo " + attesa
                  + (attesa == 1 ? " minuto" : " minuti")
                  + " che nessuno tocca niente, se non c'e' niente in corso."
                : "Spento: lo schermo resta sulla Home, sempre acceso.";
        spiegazione(c, pRiposo, tasti[ATTESA_MENO].bottom + m.s4, bRiposo, spiega);
    }

    private void etichetta(Canvas c, RectF pannello, String testo) {
        pTesto.setTextSize(m.nota);
        pTesto.setColor(Tinte.TESTO_TENUE);
        pTesto.setTypeface(MEDIO);
        c.drawText(testo, pannello.left + m.s4, pannello.top + m.s3 + m.micro, pTesto);
        pTesto.setTypeface(NORMALE);
    }

    private void interruttore(Canvas c, RectF r, String nome, boolean acceso, int tinta) {
        // Una riga d'elenco di iOS: il nome a sinistra, l'interruttore a destra,
        // e il colore solo nell'interruttore. Premuta, la riga si schiarisce.
        int idx = r == tasti[NOTTE] ? NOTTE : RIPOSO;
        if (premuto == idx) {
            pSegno.setColor(Tinte.RIEMPIMENTO);
            c.drawRoundRect(r, m.raggioPiccolo, m.raggioPiccolo, pSegno);
        }

        pTesto.setTextSize(m.voce);
        pTesto.setColor(acceso ? Tinte.TESTO : Tinte.TESTO_MEDIO);
        c.drawText(nome, r.left + m.s3, r.centerY() - (pTesto.descent() + pTesto.ascent()) / 2f,
                pTesto);

        // L'interruttore di iOS: pista verde quando e' acceso, grigia quando
        // e' spento, e un pomello bianco che quasi la riempie. La scritta
        // « acceso / spento » non serve: lo dice il pomello.
        float altaPista = r.height() * 0.50f, largaPista = altaPista * 1.65f;
        appoggio.set(r.right - m.s3 - largaPista, r.centerY() - altaPista / 2f,
                     r.right - m.s3, r.centerY() + altaPista / 2f);
        pSegno.setColor(acceso ? Tinte.RADIO : Tinte.RIEMPIMENTO_SCELTO);
        c.drawRoundRect(appoggio, altaPista / 2f, altaPista / 2f, pSegno);
        float raggio = altaPista * 0.43f;
        float cx = acceso ? appoggio.right - altaPista / 2f : appoggio.left + altaPista / 2f;
        pSegno.setColor(Tinte.TESTO);
        c.drawCircle(cx, appoggio.centerY(), raggio, pSegno);
    }

    private void valore(Canvas c, String nome, String quanto, RectF dove, int meno, int piu,
                        boolean vivo, boolean puoMeno, boolean puoPiu) {
        float cy = dove.centerY();
        pTesto.setTextSize(m.corpo);
        pTesto.setColor(vivo ? Tinte.TESTO_MEDIO : Tinte.SPENTO);
        // L'etichetta parte dal bordo del pannello in cui sta la riga.
        float xNome = (dove.left > pRiposo.left ? pRiposo.left : pNotte.left) + m.s4 + m.s3;
        c.drawText(nome, xNome, cy - (pTesto.descent() + pTesto.ascent()) / 2f, pTesto);

        tondo(c, tasti[meno], Icone.MENO, vivo ? Tinte.TESTO : Tinte.SPENTO, puoMeno,
                premuto == meno, tasti[meno].height() / 2f);
        tondo(c, tasti[piu], Icone.PIU, vivo ? Tinte.TESTO : Tinte.SPENTO, puoPiu,
                premuto == piu, tasti[piu].height() / 2f);

        pTitolo.setTextSize(m.voce);
        pTitolo.setColor(vivo ? Tinte.TESTO : Tinte.SPENTO);
        pTitolo.setTextAlign(Paint.Align.CENTER);
        c.drawText(quanto, dove.centerX(), cy - (pTitolo.descent() + pTitolo.ascent()) / 2f, pTitolo);
        pTitolo.setTextAlign(Paint.Align.LEFT);
    }

    private void tondo(Canvas c, RectF b, int icona, int colore, boolean puo, boolean giu,
                       float raggio) {
        // Un cerchio pieno e neutro, come i tasti della calcolatrice di iOS.
        pSegno.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(b, raggio, raggio, pSegno);
        pSegno.setColor(puo ? colore : Tinte.SPENTO);
        Icone.disegna(c, icona, b.centerX(), b.centerY(), b.height() * 0.46f, pSegno);
    }

    private void spiegazione(Canvas c, RectF pannello, float y, Testo.Blocco blocco, String testo) {
        float largo = pannello.width() - m.s4 * 2f;
        int righe = (int) Math.max(1f, (pannello.bottom - m.s3 - y) / (m.nota * 1.4f));
        if (righe <= 0) return;
        pTesto.setTextSize(m.nota);
        pTesto.setColor(Tinte.TESTO_TENUE);
        String[] linee = blocco.in(pTesto, testo, largo, m.nota, Math.min(righe, 5));
        float yy = y + m.nota;
        for (String l : linee) {
            c.drawText(l, pannello.left + m.s4, yy, pTesto);
            yy += m.nota * 1.4f;
        }
    }

    // ---- tocco ---------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                premuto = quale(x, y);
                if (premuto >= 0 && ripetibile(premuto)) {
                    // Meno e piu' rispondono subito, e tenendo premuto
                    // continuano: l'ora va da un capo all'altro della sera.
                    esegui(premuto);
                    ui.postDelayed(ripeti, RIPETI_DOPO);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (premuto >= 0 && !tasti[premuto].contains(x, y)) {
                    ui.removeCallbacks(ripeti);
                    premuto = -1;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                ui.removeCallbacks(ripeti);
                int su = quale(x, y);
                if (su >= 0 && su == premuto && !ripetibile(su)) esegui(su);
                premuto = -1;
                salva();
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                ui.removeCallbacks(ripeti);
                premuto = -1;
                salva();
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int quale(float x, float y) {
        for (int i = 0; i < QUANTI; i++) {
            if (!tasti[i].isEmpty() && tasti[i].contains(x, y)) return i;
        }
        return -1;
    }

    private static boolean ripetibile(int tasto) {
        return (tasto >= DA_MENO && tasto <= LUCE_PIU) || tasto == ATTESA_MENO || tasto == ATTESA_PIU;
    }

    private void esegui(int tasto) {
        switch (tasto) {
            case CHIUDI:
                if (regia != null) regia.chiudiPreferenze();
                return;
            case NOTTE:       nAcceso = !nAcceso; sporcaNotte = true; break;
            case DA_MENO:     da = scatta(da, -1); sporcaNotte = true; break;
            case DA_PIU:      da = scatta(da, +1); sporcaNotte = true; break;
            case A_MENO:      a = scatta(a, -1); sporcaNotte = true; break;
            case A_PIU:       a = scatta(a, +1); sporcaNotte = true; break;
            case LUCE_MENO:   luce = Math.max(Notte.LUCE_MIN, luce - PASSO_LUCE); sporcaNotte = true; break;
            case LUCE_PIU:    luce = Math.min(Notte.LUCE_MAX, luce + PASSO_LUCE); sporcaNotte = true; break;
            case PROVA:       if (nAcceso) notte.prova(luce); break;
            case RIPOSO:      rAcceso = !rAcceso; sporcoRiposo = true; break;
            case ATTESA_MENO: attesa = attesaVicina(-1); sporcoRiposo = true; break;
            case ATTESA_PIU:  attesa = attesaVicina(+1); sporcoRiposo = true; break;
        }
        invalidate();
    }

    /** Un quarto d'ora avanti o indietro, rimettendosi sul quarto se l'ora
     *  scritta a mano nel file non ci stava. Gira attorno alla mezzanotte. */
    private static int scatta(int minuti, int verso) {
        int resto = minuti % PASSO_ORA;
        int nuovo;
        if (verso < 0) nuovo = resto != 0 ? minuti - resto : minuti - PASSO_ORA;
        else nuovo = minuti - resto + PASSO_ORA;
        return ((nuovo % 1440) + 1440) % 1440;
    }

    private int attesaVicina(int verso) {
        if (verso > 0) {
            for (int v : ATTESE) if (v > attesa) return v;
            return ATTESE[ATTESE.length - 1];
        }
        for (int i = ATTESE.length - 1; i >= 0; i--) if (ATTESE[i] < attesa) return ATTESE[i];
        return ATTESE[0];
    }
}
