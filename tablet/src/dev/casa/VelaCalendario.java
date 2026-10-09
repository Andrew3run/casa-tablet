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
 * Il calendario: il mese a sinistra, il giorno scelto a destra.
 *
 * <h3>Perche' un mese e non un elenco</h3>
 *
 * L'elenco dei prossimi impegni ce l'ha gia' il riposo, ed e' quello che serve
 * passando davanti: « cosa c'e' adesso ». Chi invece <b>apre</b> il calendario
 * sta facendo un'altra domanda - « quando sono libero », « che giorno cade il
 * 15 » - e a quella un elenco non risponde: bisogna vedere il mese, con i
 * giorni pieni e quelli vuoti tutti insieme.
 *
 * <h3>Sola lettura, e si vede</h3>
 *
 * Non c'e' nessun tasto per aggiungere. Gli impegni arrivano dal calendario di
 * Google attraverso il sincronizzatore di sistema ({@link Calendario}), e chi
 * ne vuole uno nuovo lo scrive dal telefono, dove c'e' una tastiera vera - poi
 * qui compare da solo. Mettere qui un « aggiungi » vorrebbe dire una tastiera a
 * schermo, un orario da comporre a tocchi e un evento da scrivere nel provider:
 * tre cose complicate per rifare peggio quella che si ha gia' in tasca.
 *
 * Le cose che invece <b>nascono qui</b> - la spesa, « chiamare l'idraulico » -
 * non sono impegni con un'ora: sono la lista, e la lista e' {@link VelaToDo}.
 *
 * <h3>Il mese si disegna, non si scorre</h3>
 *
 * Sette colonne per sei righe, calcolate da due numeri: che giorno della
 * settimana e' il primo del mese e quanti giorni ha. Nessuna lista, nessun
 * adattatore, nessuna cella che nasce e muore mentre si scorre - un mese e'
 * sempre lo stesso rettangolo, e le celle sono quarantadue RectF allocati una
 * volta sola.
 */
public class VelaCalendario extends View implements Telaio.Velata {

    /** Quello che la pagina chiede alla regia. */
    public interface Regia { void chiudiCalendario(); }

    /** Sei righe: un mese che comincia di domenica e ne ha trentuno le occupa
     *  tutte. Sempre sei, anche quando ne bastano cinque, o la griglia
     *  cambierebbe altezza da un mese all'altro. */
    private static final int RIGHE = 6, COLONNE = 7;

    private static final String[] INIZIALI = { "L", "M", "M", "G", "V", "S", "D" };
    private static final String[] MESI = {
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"
    };
    private static final String[] GIORNI = {
        "domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato"
    };

    /** Quanti impegni del giorno stanno nella colonna di destra. */
    private static final int MAX_RIGHE = 7;

    private final Misure m;
    private Vetro vetro;
    private Calendario calendario;
    private Regia regia;

    private final Paint pTitolo  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGiorno  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSegno   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pVelo    = new Paint();

    private final RectF cardMese   = new RectF();
    private final RectF cardGiorno = new RectF();
    private final RectF rChiudi    = new RectF();
    private final RectF rPrima     = new RectF();
    private final RectF rDopo      = new RectF();
    private final RectF rOggi      = new RectF();
    private final RectF appoggio   = new RectF();
    private final RectF[] celle    = new RectF[RIGHE * COLONNE];
    private final RectF[] righe    = new RectF[MAX_RIGHE];

    private final Testo.Riga[] rTitoli = new Testo.Riga[MAX_RIGHE];
    private final Testo.Riga[] rQuando = new Testo.Riga[MAX_RIGHE];
    private final Testo.Riga rVuoto = new Testo.Riga();

    private final Calendar conto = Calendar.getInstance();

    /** Il mese in scena, e il giorno scelto dentro quel mese. */
    private int anno, mese, giornoScelto;

    /** Quanti impegni per giorno del mese, e quelli del giorno scelto. */
    private final int[] quanti = new int[32];
    private Calendario.Impegno[] delGiorno = new Calendario.Impegno[0];

    private int premuta = -1;
    private int premutoTasto = -1;
    private long entrata;

    private float latoCella, corpoGiorno;

    public VelaCalendario(Context c, Misure misure, Vetro v) {
        super(c);
        this.m = misure;
        this.vetro = v;
        setClickable(true);
        for (int i = 0; i < celle.length; i++) celle[i] = new RectF();
        for (int i = 0; i < MAX_RIGHE; i++) {
            righe[i] = new RectF();
            rTitoli[i] = new Testo.Riga();
            rQuando[i] = new Testo.Riga();
        }
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pTitolo.setTextAlign(Paint.Align.LEFT);
        pGiorno.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pGiorno.setTextAlign(Paint.Align.CENTER);
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pNota.setTextAlign(Paint.Align.LEFT);
        pSegno.setStrokeCap(Paint.Cap.ROUND);

        Calendar oggi = Calendar.getInstance();
        anno = oggi.get(Calendar.YEAR);
        mese = oggi.get(Calendar.MONTH);
        giornoScelto = oggi.get(Calendar.DAY_OF_MONTH);
    }

    public void setCalendario(Calendario c) { calendario = c; }
    public void setRegia(Regia r) { regia = r; }

    @Override public void setVetro(Vetro v) { vetro = v; invalidate(); }
    @Override public boolean hasOverlappingRendering() { return false; }

    @Override public void suVelaTolta() {
        if (regia != null) regia.chiudiCalendario();
    }

    /** Entra in scena: si chiede il mese e si riparte da oggi. */
    public void suEntrata() {
        entrata = Anima.ora();
        Calendar oggi = Calendar.getInstance();
        anno = oggi.get(Calendar.YEAR);
        mese = oggi.get(Calendar.MONTH);
        giornoScelto = oggi.get(Calendar.DAY_OF_MONTH);
        chiediIlMese();
    }

    /** Il calendario ha detto che qualcosa e' cambiato. */
    public void risveglia() { chiediIlMese(); }

    // ---- i dati -------------------------------------------------------------

    /**
     * Chiede gli impegni del mese in scena.
     *
     * Una query sola per tutto il mese, non una al giorno: il provider e'
     * locale ma una query costa comunque un giro di binder, e trentuno giri per
     * disegnare una griglia sarebbero trentuno di troppo.
     */
    private void chiediIlMese() {
        if (calendario == null) return;
        conto.clear();
        conto.set(anno, mese, 1, 0, 0, 0);
        long da = conto.getTimeInMillis();
        conto.add(Calendar.MONTH, 1);
        long a = conto.getTimeInMillis();
        calendario.chiediFra(da, a, new Calendario.Esito() {
            @Override public void impegni(Calendario.Impegno[] trovati) {
                java.util.Arrays.fill(quanti, 0);
                for (Calendario.Impegno i : trovati) {
                    conto.setTimeInMillis(i.inizio);
                    if (conto.get(Calendar.YEAR) == anno && conto.get(Calendar.MONTH) == mese) {
                        int g = conto.get(Calendar.DAY_OF_MONTH);
                        if (g >= 1 && g < quanti.length) quanti[g]++;
                    }
                }
                delGiorno = soloDelGiorno(trovati, giornoScelto);
                disponi();
                invalidate();
            }
        });
    }

    private Calendario.Impegno[] soloDelGiorno(Calendario.Impegno[] tutti, int giorno) {
        int quante = 0;
        for (Calendario.Impegno i : tutti) if (eDi(i, giorno)) quante++;
        Calendario.Impegno[] fuori = new Calendario.Impegno[quante];
        int j = 0;
        for (Calendario.Impegno i : tutti) if (eDi(i, giorno)) fuori[j++] = i;
        return fuori;
    }

    private boolean eDi(Calendario.Impegno i, int giorno) {
        conto.setTimeInMillis(i.inizio);
        return conto.get(Calendar.YEAR) == anno
            && conto.get(Calendar.MONTH) == mese
            && conto.get(Calendar.DAY_OF_MONTH) == giorno;
    }

    /** Che colonna occupa il primo del mese, con la settimana che comincia di
     *  lunedi' come si fa qui. */
    private int primaColonna() {
        conto.clear();
        conto.set(anno, mese, 1);
        int g = conto.get(Calendar.DAY_OF_WEEK);      // 1 = domenica
        return (g + 5) % 7;                           // 0 = lunedi'
    }

    private int quantiGiorni() {
        conto.clear();
        conto.set(anno, mese, 1);
        return conto.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private boolean eOggi(int giorno) {
        Calendar o = Calendar.getInstance();
        return o.get(Calendar.YEAR) == anno && o.get(Calendar.MONTH) == mese
            && o.get(Calendar.DAY_OF_MONTH) == giorno;
    }

    // ---- misure -------------------------------------------------------------

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
        rChiudi.set(w - mg - m.bersaglio * 1.2f, mg, w - mg, mg + altaTesta * 0.86f);
        rDopo.set(rChiudi.left - m.s3 - m.bersaglio, rChiudi.top,
                  rChiudi.left - m.s3, rChiudi.bottom);
        rPrima.set(rDopo.left - m.s2 - m.bersaglio, rChiudi.top,
                   rDopo.left - m.s2, rChiudi.bottom);
        rOggi.set(rPrima.left - m.s3 - m.bersaglio * 1.8f, rChiudi.top,
                  rPrima.left - m.s3, rChiudi.bottom);

        float cima = mg + altaTesta + m.s2;
        float taglio = w * 0.60f;
        cardMese.set(mg, cima, taglio - m.s2, h - mg);
        cardGiorno.set(taglio + m.s2, cima, w - mg, h - mg);

        // La griglia: sette colonne quadrate quanto ci sta, sei righe.
        float dentroX = cardMese.left + m.s3, dentroY = cardMese.top + m.s3;
        float largo = cardMese.width() - m.s3 * 2f;
        float alto = cardMese.height() - m.s3 * 2f;
        corpoGiorno = m.corpo;
        float altaIniziali = corpoGiorno * 1.8f;
        // Le celle riempiono il pannello: larghe un settimo e alte un sesto di
        // quel che resta. Non quadrate - lo erano, e mezzo pannello restava
        // vuoto in fondo, che su una griglia si legge come « il mese finisce
        // qui ». Il tondo del giorno segue il lato corto, quindi resta tondo.
        float largaCella = largo / COLONNE;
        float altaCella = (alto - altaIniziali) / RIGHE;
        latoCella = Math.min(largaCella, altaCella);
        float scartoY = dentroY + altaIniziali;
        for (int r = 0; r < RIGHE; r++) {
            for (int c = 0; c < COLONNE; c++) {
                celle[r * COLONNE + c].set(
                        dentroX + c * largaCella, scartoY + r * altaCella,
                        dentroX + (c + 1) * largaCella, scartoY + (r + 1) * altaCella);
            }
        }

        float altaRiga = Math.max(m.bersaglio, h * 0.105f);
        float y = cardGiorno.top + m.s4 + m.voce + m.s3;
        for (int i = 0; i < MAX_RIGHE; i++) {
            float basso = y + altaRiga;
            if (basso > cardGiorno.bottom - m.s3) { righe[i].setEmpty(); continue; }
            righe[i].set(cardGiorno.left + m.s3, y, cardGiorno.right - m.s3, basso);
            y = basso;
        }
    }

    // ---- disegno ------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        pVelo.setColor(0xF2000000);
        c.drawRect(0, 0, getWidth(), getHeight(), pVelo);
        if (vetro == null || !vetro.vivo()) return;

        disegnaTesta(c);

        int s0 = c.save();
        scorri(c, 0);
        vetro.pannello(c, cardMese, m.raggio, Tinte.AGENDA, Tinte.VELO_QUIETO);
        disegnaMese(c);
        c.restoreToCount(s0);

        s0 = c.save();
        scorri(c, 1);
        vetro.pannello(c, cardGiorno, m.raggio, Tinte.AGENDA, Tinte.VELO_QUIETO);
        disegnaGiorno(c);
        c.restoreToCount(s0);

        Anima.continua(this, entrata, 2);
    }

    private void scorri(Canvas c, int quale) {
        float avanti = Anima.posa(Anima.entrata(entrata, quale));
        if (avanti < 1f) c.translate(0f, (1f - avanti) * m.s5);
    }

    private void disegnaTesta(Canvas c) {
        pTitolo.setTextSize(m.titolo);
        pTitolo.setColor(Tinte.TESTO);
        String testa = MESI[mese] + " " + anno;
        c.drawText(testa.substring(0, 1).toUpperCase(Locale.ITALIAN) + testa.substring(1),
                   m.margine, rChiudi.centerY() + m.titolo * 0.35f, pTitolo);

        tasto(c, rOggi, -1, "Oggi", premutoTasto == 3);
        tasto(c, rPrima, Icone.INDIETRO, null, premutoTasto == 0);
        tasto(c, rDopo, Icone.AVANTI, null, premutoTasto == 1);
        tasto(c, rChiudi, Icone.CHIUDI, null, premutoTasto == 2);
    }

    /** Un tasto alla iOS: un cerchio grigio con l'icona, o una capsula grigia
     *  con la scritta. */
    private void tasto(Canvas c, RectF b, int icona, String testo, boolean giu) {
        pSegno.setStyle(Paint.Style.FILL);
        pSegno.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        if (icona >= 0) {
            float r = Math.min(b.width(), b.height()) * 0.42f;
            c.drawCircle(b.centerX(), b.centerY(), r, pSegno);
        } else {
            c.drawRoundRect(b, b.height() / 2f, b.height() / 2f, pSegno);
        }
        pSegno.setStyle(Paint.Style.STROKE);
        pSegno.setStrokeWidth(Math.max(2f, m.icona * 0.08f));
        pSegno.setColor(Tinte.TESTO_MEDIO);
        if (icona >= 0) {
            Icone.disegna(c, icona, b.centerX(), b.centerY(), m.icona * 0.70f, pSegno);
        } else {
            pGiorno.setTextSize(m.corpo);
            pGiorno.setColor(Tinte.TESTO);
            c.drawText(testo, b.centerX(),
                    b.centerY() - (pGiorno.descent() + pGiorno.ascent()) / 2f, pGiorno);
        }
    }

    private void disegnaMese(Canvas c) {
        // Le iniziali dei giorni, sopra la griglia.
        pGiorno.setTextSize(m.micro);
        pGiorno.setColor(Tinte.TESTO_TENUE);
        for (int i = 0; i < COLONNE; i++) {
            RectF cella = celle[i];
            c.drawText(INIZIALI[i], cella.centerX(), cella.top - m.s2, pGiorno);
        }

        int prima = primaColonna(), giorni = quantiGiorni();
        for (int g = 1; g <= giorni; g++) {
            int indice = prima + g - 1;
            if (indice >= celle.length) break;
            RectF cella = celle[indice];
            boolean oggi = eOggi(g);
            boolean scelto = g == giornoScelto;

            if (scelto || oggi || premuta == indice) {
                float raggio = latoCella * 0.36f;
                appoggio.set(cella.centerX() - raggio, cella.centerY() - raggio,
                             cella.centerX() + raggio, cella.centerY() + raggio);
                // Come il Calendario di iOS: oggi e' un cerchio pieno del
                // colore dell'agenda - l'unico giorno che si trova senza
                // cercarlo - il giorno scelto un cerchio bianco col numero
                // scuro, quello sotto il dito un cerchio grigio.
                pSegno.setStyle(Paint.Style.FILL);
                if (oggi) {
                    pSegno.setColor(Tinte.AGENDA);
                } else if (scelto) {
                    pSegno.setColor(Tinte.TESSERA_ACCESA);
                } else {
                    pSegno.setColor(Tinte.RIEMPIMENTO);
                }
                c.drawOval(appoggio, pSegno);
            }

            pGiorno.setTextSize(corpoGiorno);
            pGiorno.setColor(oggi ? Tinte.TESTO
                    : (scelto ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO_MEDIO));
            c.drawText(String.valueOf(g), cella.centerX(),
                    cella.centerY() - (pGiorno.descent() + pGiorno.ascent()) / 2f, pGiorno);

            // Il pallino di « qui c'e' qualcosa »: uno solo, non uno per
            // impegno - da questa distanza tre pallini sono una macchia.
            if (quanti[g] > 0) {
                pSegno.setStyle(Paint.Style.FILL);
                pSegno.setColor(oggi ? Tinte.TESTO
                        : (scelto ? Tinte.TESTO_SU_CHIARO_TENUE : Tinte.TESTO_TENUE));
                c.drawCircle(cella.centerX(), cella.bottom - latoCella * 0.16f,
                             latoCella * 0.055f, pSegno);
            }
        }
    }

    private void disegnaGiorno(Canvas c) {
        float x = cardGiorno.left + m.s4;
        conto.clear();
        conto.set(anno, mese, giornoScelto);
        String testa = GIORNI[conto.get(Calendar.DAY_OF_WEEK) - 1] + " " + giornoScelto;
        if (eOggi(giornoScelto)) testa = "oggi, " + testa;

        pTitolo.setTextSize(m.voce);
        pTitolo.setColor(eOggi(giornoScelto) ? Tinte.AGENDA : Tinte.TESTO);
        c.drawText(testa.substring(0, 1).toUpperCase(Locale.ITALIAN) + testa.substring(1),
                   x, cardGiorno.top + m.s4 + m.voce, pTitolo);

        if (delGiorno.length == 0) {
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.SPENTO);
            String niente = calendario != null && calendario.guasto() != Calendario.TUTTO_BENE
                    ? calendario.perche() : "niente in programma";
            c.drawText(rVuoto.in(pNota, niente, cardGiorno.width() - m.s4 * 2f, m.nota),
                       x, cardGiorno.top + cardGiorno.height() * 0.30f, pNota);
            return;
        }

        for (int i = 0; i < MAX_RIGHE && i < delGiorno.length; i++) {
            RectF r = righe[i];
            if (r.isEmpty()) break;
            Calendario.Impegno im = delGiorno[i];

            // La stanghetta del colore del calendario, alta quanto le due
            // righe dell'impegno, come nel Calendario di iOS.
            float raggio = m.nota * 0.28f;
            float larga = Math.max(3f, m.s1 * 0.7f);
            pSegno.setStyle(Paint.Style.FILL);
            pSegno.setColor(im.colore != 0 ? im.colore : Tinte.AGENDA);
            appoggio.set(r.left, r.top + r.height() * 0.14f, r.left + larga, r.top + r.height() * 0.86f);
            c.drawRoundRect(appoggio, larga / 2f, larga / 2f, pSegno);

            // Il capello fra un impegno e l'altro.
            if (i > 0) {
                pSegno.setColor(Tinte.SEPARATORE);
                c.drawRect(r.left, r.top, r.right, r.top + 1f, pSegno);
            }

            float xt = r.left + raggio * 2f + m.s3;
            float largo = r.right - xt;
            pTitolo.setTextSize(m.corpo);
            pTitolo.setColor(Tinte.TESTO);
            c.drawText(rTitoli[i].adatta(pTitolo, im.titolo, largo, m.corpo, m.corpo * 0.78f),
                       xt, r.top + r.height() * 0.44f, pTitolo);

            String sotto = im.tuttoIlGiorno ? "tutto il giorno"
                    : String.format(Locale.ITALIAN, "%tH:%tM", im.inizio, im.inizio)
                      + " - " + String.format(Locale.ITALIAN, "%tH:%tM", im.fine, im.fine);
            if (im.dove != null && im.dove.trim().length() > 0) sotto += " · " + im.dove.trim();
            pNota.setTextSize(m.nota);
            pNota.setColor(im.adesso() ? Tinte.AGENDA : Tinte.TESTO_TENUE);
            c.drawText(rQuando[i].in(pNota, sotto, largo, m.nota),
                       xt, r.top + r.height() * 0.80f, pNota);
        }

        if (delGiorno.length > MAX_RIGHE) {
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.SPENTO);
            c.drawText("e altri " + (delGiorno.length - MAX_RIGHE), x,
                       cardGiorno.bottom - m.s3, pNota);
        }
    }

    // ---- il tocco -----------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                premutoTasto = qualeTasto(x, y);
                premuta = qualeCella(x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                int tasto = qualeTasto(x, y);
                int cella = qualeCella(x, y);
                if (tasto >= 0 && tasto == premutoTasto) premi(tasto);
                else if (cella >= 0 && cella == premuta) scegli(cella);
                premutoTasto = -1;
                premuta = -1;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                premutoTasto = -1;
                premuta = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int qualeTasto(float x, float y) {
        if (rPrima.contains(x, y)) return 0;
        if (rDopo.contains(x, y)) return 1;
        if (rChiudi.contains(x, y)) return 2;
        if (rOggi.contains(x, y)) return 3;
        return -1;
    }

    private int qualeCella(float x, float y) {
        int prima = primaColonna(), giorni = quantiGiorni();
        for (int g = 1; g <= giorni; g++) {
            int indice = prima + g - 1;
            if (indice < celle.length && celle[indice].contains(x, y)) return indice;
        }
        return -1;
    }

    private void premi(int tasto) {
        switch (tasto) {
            case 0: cambiaMese(-1); break;
            case 1: cambiaMese(+1); break;
            case 2: if (regia != null) regia.chiudiCalendario(); break;
            case 3: suEntrata(); break;
        }
    }

    private void cambiaMese(int quanti) {
        conto.clear();
        conto.set(anno, mese, 1);
        conto.add(Calendar.MONTH, quanti);
        anno = conto.get(Calendar.YEAR);
        mese = conto.get(Calendar.MONTH);
        // Cambiando mese si sceglie il primo, non il giorno di prima: il 31 non
        // esiste a febbraio, e « il giorno che c'era » sarebbe una scelta che
        // qualche volta sparisce.
        giornoScelto = 1;
        entrata = Anima.ora();
        chiediIlMese();
        invalidate();
    }

    private void scegli(int indice) {
        int giorno = indice - primaColonna() + 1;
        if (giorno < 1 || giorno > quantiGiorni()) return;
        giornoScelto = giorno;
        chiediIlMese();
        invalidate();
    }
}
