package dev.casa;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * Le notizie a pagina intera: una in primo piano, le altre accanto.
 *
 * <h3>Com'e' fatta</h3>
 *
 * <pre>
 *   Notizie  [Italia] [Roma] [Calcio] [Napoli]          [ag] [rg] [X]
 *   +--------------------------------+  +----------------------------+
 *   | IN PRIMO PIANO   aggiornate ... |  | E POI                      |
 *   | la Repubblica · 25 min fa      |  | ANSA · 1 ora fa            |
 *   | Il titolo grande, fino a       |  | Un titolo su due righe     |
 *   | cinque righe                   |  | ---------------------      |
 *   | ------------------------------ |  | ...                        |
 *   | due titoli di mezzo            |  |                            |
 *   +--------------------------------+  +----------------------------+
 * </pre>
 *
 * <b>Otto notizie e niente da scorrere.</b> E' la regola dei luoghi del meteo:
 * un elenco che scorre, su un apparecchio appeso al muro, e' un elenco che
 * nessuno scorre. Chi ne vuole di piu' cambia linguetta.
 *
 * <b>Una grande e le altre piccole</b>, come la prima pagina di un giornale e
 * non come un elenco di posta: otto righe uguali non dicono da dove si comincia
 * a leggere, e la prima di Google e' davvero la piu' importante.
 *
 * <b>Il corpo lo sceglie il titolo.</b> Si prova quello grande, e se il titolo
 * non ci sta nelle righe che ci sono si scende di un gradino della scala: un
 * titolo intero un po' piu' piccolo si legge meglio di mezzo titolo grande.
 * Si decide quando arrivano i titoli, non a ogni fotogramma.
 *
 * <b>Toccarne una la apre in Chrome.</b> Leggere l'articolo dentro Casa vorrebbe
 * dire un browser nostro, cioe' la cosa piu' pesante che si possa mettere su un
 * tablet da un giga; Chrome c'e' gia', e il tasto indietro riporta qui.
 */
public class VelaNotizie extends View implements Telaio.Velata {

    /** Quello che la pagina chiede alla regia. */
    public interface Regia { void chiudiNotizie(); }

    /** I posti: uno in primo piano, due sotto di lui, cinque nell'elenco. */
    private static final int SOTTO = 2, RIGHE = 5;
    private static final int POSTI = 1 + SOTTO + RIGHE;
    private static final int MAX_LINGUETTE = 4;

    /** L'interlinea dei titoli, in corpi. */
    private static final float INTERLINEA = 1.22f;

    /** Il filo fra due notizie: una riga di luce, non un riquadro per notizia. */
    private static final int FILO = 0x1CFFFFFF;

    private static final int CHIUDI = 0, REGOLA = 1, AGGIORNA = 2, LINGUETTA = 10;

    private final Misure m;
    private Vetro vetro;
    private final Notizie notizie;
    private Regia regia;

    private final Paint pVelo   = new Paint();
    private final Paint pTitolo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTesto  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEtich  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIcona  = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** I fondi piatti dei comandi: segmenti, cerchi, capsule. */
    private final Paint pPieno  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF binario = new RectF();

    private final RectF rChiudi = new RectF(), rRegola = new RectF(), rAggiorna = new RectF();
    private final RectF[] linguette = new RectF[MAX_LINGUETTE];
    private final Testo.Riga[] rLinguetta = new Testo.Riga[MAX_LINGUETTE];
    private final float[] largoNome = new float[MAX_LINGUETTE];
    private int quanteLinguette;

    private final RectF cardPrimo = new RectF(), cardElenco = new RectF();
    private final RectF[] posti = new RectF[POSTI];
    private final Testo.Blocco[] bTitolo = new Testo.Blocco[POSTI];
    private final Testo.Riga[] rFonte = new Testo.Riga[POSTI];
    private final float[] corpo = new float[POSTI];
    private final int[] righe = new int[POSTI];
    private final String[] fonte = new String[POSTI];
    private final Testo.Blocco prova = new Testo.Blocco();
    private final Testo.Riga rStato = new Testo.Riga(), rVuoto = new Testo.Riga();

    private Notizie.Titolo[] titoli = new Notizie.Titolo[0];
    private String stato = "", vuoto = "";

    private int scelto;
    private int premuto = -1, premutoTasto = -1;
    private long entrata;
    private boolean tolta;

    private final PannelloScelte scelte = new PannelloScelte();

    /** Una volta al minuto si rifanno le scritte: « 25 min fa » deve
     *  diventare « 26 min fa » anche se nessuno tocca niente. */
    private final Runnable minuto = new Runnable() {
        @Override public void run() {
            if (tolta) return;
            componi();
            invalidate();
            postDelayed(this, 60000L);
        }
    };

    public VelaNotizie(Context c, Misure misure, Vetro v, Notizie n) {
        super(c);
        this.m = misure;
        this.vetro = v;
        this.notizie = n;
        setClickable(true);
        for (int i = 0; i < MAX_LINGUETTE; i++) {
            linguette[i] = new RectF();
            rLinguetta[i] = new Testo.Riga();
        }
        for (int i = 0; i < POSTI; i++) {
            posti[i] = new RectF();
            bTitolo[i] = new Testo.Blocco();
            rFonte[i] = new Testo.Riga();
        }
        pVelo.setColor(0xF2000000);
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pTesto.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pEtich.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        // Le etichette sono in frase normale e non si spaziano piu': il
        // maiuscolo spaziato era l'unica cosa che gridava, in queste pagine.
    }

    public void setRegia(Regia r) { regia = r; }

    @Override public void setVetro(Vetro v) { vetro = v; invalidate(); }
    @Override public boolean hasOverlappingRendering() { return false; }

    @Override public void suVelaTolta() {
        tolta = true;
        removeCallbacks(minuto);
        if (regia != null) regia.chiudiNotizie();
    }

    /** Entra in scena: le principali, e tutto quello che e' vecchio si chiede. */
    public void suEntrata() {
        entrata = Anima.ora();
        scelto = 0;
        notizie.aggiorna(true);
        disponi();
        componi();
        removeCallbacks(minuto);
        postDelayed(minuto, 60000L);
    }

    /** Sono arrivati titoli, o sono cambiate le scelte. */
    public void notizieCambiate() {
        disponi();
        componi();
        scelte.componi();
        invalidate();
    }

    private Notizie.Filone filone() {
        Notizie.Filone[] ff = notizie.filoni();
        if (ff.length == 0) return null;
        if (scelto >= ff.length) scelto = 0;
        return ff[scelto];
    }

    // ---- misure -------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        disponi();
        componi();
        scelte.misura(w, h);
        scelte.componi();
    }

    private void disponi() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float mg = m.margine;

        float altaTesta = Math.max(m.bersaglio, m.titolo * 1.3f);
        float alto = altaTesta * 0.86f;
        float largoTasto = m.bersaglio * 1.2f;
        rChiudi.set(w - mg - largoTasto, mg, w - mg, mg + alto);
        rRegola.set(rChiudi.left - m.s2 - largoTasto, mg, rChiudi.left - m.s2, mg + alto);
        rAggiorna.set(rRegola.left - m.s2 - largoTasto, mg, rRegola.left - m.s2, mg + alto);

        // Le linguette stanno nella riga del titolo, come nella pagina del
        // meteo: una fascia loro sotto sarebbe una riga tolta alle notizie per
        // dire una cosa che sta in mezza riga.
        pTitolo.setTextSize(m.titolo);
        float x = mg + pTitolo.measureText("Notizie") + m.s5;
        float spazio = rAggiorna.left - m.s4 - x;
        Notizie.Filone[] ff = notizie.filoni();
        quanteLinguette = Math.min(ff.length, MAX_LINGUETTE);
        pNota.setTextSize(m.nota);
        float lato = m.icona * 0.8f;
        float massimo = quanteLinguette == 0 ? 0f
                : (spazio - m.s1 * (quanteLinguette - 1)) / quanteLinguette;
        for (int i = 0; i < MAX_LINGUETTE; i++) {
            if (i >= quanteLinguette) { linguette[i].setEmpty(); continue; }
            float intorno = m.s3 + lato + m.s2 + m.s3;
            float naturale = intorno + pNota.measureText(ff[i].nome);
            float largo = Math.min(naturale, massimo);
            largoNome[i] = Math.max(0f, largo - intorno);
            linguette[i].set(x, mg, x + largo, mg + alto);
            x += largo + m.s1;
        }

        float cima = mg + altaTesta + m.s2;
        float taglio = w * 0.47f;
        cardPrimo.set(mg, cima, taglio - m.s2 / 2f, h - mg);
        cardElenco.set(taglio + m.s2 / 2f, cima, w - mg, h - mg);

        float banda = m.s3 + m.micro + m.s2;
        float y0 = cardPrimo.top + banda, y1 = cardPrimo.bottom - m.s2;
        float altoPrimo = (y1 - y0) * 0.54f;
        posti[0].set(cardPrimo.left + m.s2, y0, cardPrimo.right - m.s2, y0 + altoPrimo);
        float resto = (y1 - y0 - altoPrimo) / SOTTO;
        for (int k = 0; k < SOTTO; k++) {
            float a = y0 + altoPrimo + resto * k;
            posti[1 + k].set(cardPrimo.left + m.s2, a, cardPrimo.right - m.s2, a + resto);
        }
        float e0 = cardElenco.top + banda, e1 = cardElenco.bottom - m.s2;
        float alta = (e1 - e0) / RIGHE;
        for (int k = 0; k < RIGHE; k++) {
            float a = e0 + alta * k;
            posti[1 + SOTTO + k].set(cardElenco.left + m.s2, a, cardElenco.right - m.s2, a + alta);
        }
    }

    /**
     * Rifa' le scritte e sceglie i corpi dei titoli.
     *
     * Qui e non in onDraw: provare due corpi alloca, e si fa quando cambiano i
     * titoli - una volta ogni venti minuti - o quando passa un minuto.
     */
    private void componi() {
        if (getWidth() == 0) return;
        Notizie.Filone f = filone();
        titoli = notizie.titoli(f);
        long adesso = System.currentTimeMillis();
        for (int i = 0; i < POSTI; i++) {
            if (i >= titoli.length) { fonte[i] = null; continue; }
            Notizie.Titolo t = titoli[i];
            String quando = Notizie.fa(t.quando, adesso);
            fonte[i] = t.fonte.length() == 0 ? quando
                     : quando.length() == 0 ? t.fonte : t.fonte + "  ·  " + quando;
        }
        // Il corpo si sceglie per gruppo, non per titolo: due titoli uno sotto
        // l'altro a due grandezze diverse si leggono come un errore - visto
        // sulla Home, dove il primo scendeva di un gradino e il secondo no.
        // L'elenco di destra resta a corpo fisso: cinque righe che cambiano
        // grandezza a ogni giro sarebbero peggio di un titolo coi puntini.
        decidi(0, 0, m.titolo, m.voce);
        decidi(1, SOTTO, m.voce, m.corpo);
        decidi(SOTTO + 1, POSTI - 1, m.corpo, m.corpo);
        long q = notizie.quando(f);
        stato = f != null && notizie.inCorso(f) ? "aggiorno…"
              : q > 0 ? "aggiornate " + Notizie.fa(q, adesso) : "";
        String perche = notizie.perche(f);
        vuoto = f != null && notizie.inCorso(f) ? "sto cercando le notizie…"
              : perche != null ? perche : "ancora nessuna notizia";
    }

    /** Il corpo grande se tutti i titoli del gruppo ci stanno interi, se no
     *  quello sotto - per tutti. */
    private void decidi(int da, int a, float grande, float piccolo) {
        float c = grande;
        for (int i = da; i <= a && i < titoli.length; i++) {
            String s = titoli[i].testo;
            String[] r = prova.in(pTesto, s, posti[i].width() - m.s3 * 2f, grande, righeCon(i, grande));
            if (tagliato(r, s)) { c = piccolo; break; }
        }
        for (int i = da; i <= a && i < titoli.length; i++) {
            corpo[i] = c;
            righe[i] = righeCon(i, c);
        }
    }

    /** Quante righe di quel corpo stanno nel posto, sotto la riga della fonte. */
    private int righeCon(int i, float c) {
        float alto = posti[i].height() - m.s2 * 2f - m.nota * 1.45f;
        return Math.max(1, (int) (alto / (c * INTERLINEA)));
    }

    private static boolean tagliato(String[] r, String s) {
        if (r.length == 0) return false;
        return r[r.length - 1].endsWith("…") && !s.endsWith("…");
    }

    // ---- disegno ------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), pVelo);
        if (vetro == null || !vetro.vivo()) return;

        disegnaTesta(c);

        int s0 = c.save();
        scorri(c, 0);
        vetro.pannello(c, cardPrimo, m.raggio, Tinte.NOTIZIE, Tinte.VELO_QUIETO);
        etichetta(c, cardPrimo, "In primo piano");
        if (stato.length() > 0) {
            pNota.setTextAlign(Paint.Align.RIGHT);
            pNota.setTextSize(m.micro);
            pNota.setColor(Tinte.TESTO_TENUE);
            c.drawText(rStato.in(pNota, stato, cardPrimo.width() * 0.45f, m.micro),
                       cardPrimo.right - m.s4, cardPrimo.top + m.s3 + m.micro, pNota);
            pNota.setTextAlign(Paint.Align.LEFT);
        }
        if (titoli.length == 0) {
            pNota.setTextSize(m.corpo);
            pNota.setColor(Tinte.SPENTO);
            c.drawText(rVuoto.in(pNota, vuoto, cardPrimo.width() - m.s4 * 2f, m.corpo),
                       cardPrimo.left + m.s4, posti[0].top + m.corpo * 1.6f, pNota);
        }
        for (int i = 0; i <= SOTTO && i < titoli.length; i++) posto(c, i);
        c.restoreToCount(s0);

        s0 = c.save();
        scorri(c, 1);
        vetro.pannello(c, cardElenco, m.raggio, Tinte.NOTIZIE, Tinte.VELO_QUIETO);
        etichetta(c, cardElenco, "E poi");
        for (int i = SOTTO + 1; i < POSTI && i < titoli.length; i++) posto(c, i);
        c.restoreToCount(s0);

        if (scelte.aperto) scelte.disegna(c);

        Anima.continua(this, entrata, 2);
    }

    private void scorri(Canvas c, int quale) {
        float avanti = Anima.posa(Anima.entrata(entrata, quale));
        if (avanti < 1f) c.translate(0f, (1f - avanti) * m.s5);
    }

    private void etichetta(Canvas c, RectF card, String testo) {
        pEtich.setTextSize(m.nota);
        pEtich.setColor(Tinte.TESTO_TENUE);
        pEtich.setTextAlign(Paint.Align.LEFT);
        c.drawText(testo, card.left + m.s4, card.top + m.s3 + m.micro, pEtich);
    }

    /** Una notizia: chi e quando, e sotto il titolo. */
    private void posto(Canvas c, int i) {
        RectF b = posti[i];
        Notizie.Titolo t = titoli[i];
        if (premuto == i) {
            pPieno.setColor(Tinte.RIEMPIMENTO);
            c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pPieno);
        }

        // Il capello sopra, fra una notizia e la precedente dello stesso
        // pannello: rientrato a sinistra e fino al bordo a destra, come negli
        // elenchi di iOS.
        if (i != 0 && i != SOTTO + 1 && premuto != i && premuto != i - 1) {
            pIcona.setColor(Tinte.SEPARATORE);
            c.drawRect(b.left + m.s3, b.top, b.right,
                       b.top + Math.max(1f, m.densita * 0.75f), pIcona);
        }

        float x = b.left + m.s3, largo = b.width() - m.s3 * 2f;
        float y = b.top + m.s2 + m.nota;
        pNota.setTextAlign(Paint.Align.LEFT);
        pNota.setTextSize(m.nota);
        pNota.setColor(Tinte.con(Tinte.NOTIZIE, 0xE6));
        if (fonte[i] != null) c.drawText(rFonte[i].in(pNota, fonte[i], largo, m.nota), x, y, pNota);

        String[] r = bTitolo[i].in(pTesto, t.testo, largo, corpo[i], righe[i]);
        pTesto.setColor(Tinte.TESTO);
        float passo = corpo[i] * INTERLINEA;
        float yT = y + m.s2 + corpo[i];
        for (int k = 0; k < r.length; k++) c.drawText(r[k], x, yT + passo * k, pTesto);
    }

    private void disegnaTesta(Canvas c) {
        pTitolo.setTextSize(m.titolo);
        pTitolo.setColor(Tinte.TESTO);
        pTitolo.setTextAlign(Paint.Align.LEFT);
        c.drawText("Notizie", m.margine, rChiudi.centerY() + m.titolo * 0.35f, pTitolo);

        Notizie.Filone[] ff = notizie.filoni();
        float lato = m.icona * 0.8f;
        // Le linguette sono un controllo a segmenti di iOS: un binario grigio
        // unico, e sopra il segmento scelto pieno.
        int ultime = Math.min(quanteLinguette, ff.length);
        if (ultime > 0) {
            binario.set(linguette[0].left - m.s1, linguette[0].top - m.s1,
                        linguette[ultime - 1].right + m.s1, linguette[0].bottom + m.s1);
            pPieno.setColor(Tinte.RIEMPIMENTO);
            c.drawRoundRect(binario, binario.height() / 2f, binario.height() / 2f, pPieno);
        }
        for (int i = 0; i < quanteLinguette && i < ff.length; i++) {
            RectF r = linguette[i];
            boolean qui = i == scelto;
            if (qui || premutoTasto == LINGUETTA + i) {
                pPieno.setColor(qui ? Tinte.SEGMENTO_SCELTO : Tinte.RIEMPIMENTO);
                c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, pPieno);
            }
            float cx = r.left + m.s3 + lato / 2f;
            pIcona.setColor(qui ? Tinte.TESTO : Tinte.TESTO_MEDIO);
            Icone.disegna(c, ff[i].icona, cx, r.centerY(), lato, pIcona);
            pNota.setTextAlign(Paint.Align.LEFT);
            pNota.setColor(qui ? Tinte.TESTO : Tinte.TESTO_MEDIO);
            String nome = rLinguetta[i].in(pNota, ff[i].nome, largoNome[i], m.nota);
            c.drawText(nome, cx + lato / 2f + m.s2,
                       r.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
        }

        Notizie.Filone f = filone();
        boolean chiede = f != null && notizie.inCorso(f);
        tasto(c, rAggiorna, Icone.AGGIORNA, premutoTasto == AGGIORNA, chiede);
        tasto(c, rRegola, Icone.REGOLA, premutoTasto == REGOLA, false);
        tasto(c, rChiudi, Icone.CHIUDI, premutoTasto == CHIUDI, false);
    }

    /** Un cerchio grigio con l'icona, come i tasti piccoli di iOS. */
    private void tasto(Canvas c, RectF b, int icona, boolean giu, boolean acceso) {
        pPieno.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawCircle(b.centerX(), b.centerY(), Math.min(b.width(), b.height()) * 0.42f, pPieno);
        pIcona.setColor(acceso ? Tinte.NOTIZIE : Tinte.TESTO_MEDIO);
        Icone.disegna(c, icona, b.centerX(), b.centerY(), m.icona * 0.70f, pIcona);
    }

    // ---- il tocco -----------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (scelte.aperto) return scelte.tocco(e, x, y);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                premutoTasto = qualeTasto(x, y);
                premuto = premutoTasto >= 0 ? -1 : qualePosto(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP: {
                int tasto = premutoTasto, posto = premuto;
                premutoTasto = premuto = -1;
                if (tasto >= 0 && tasto == qualeTasto(x, y)) premi(tasto);
                else if (posto >= 0 && posto == qualePosto(x, y) && posto < titoli.length) {
                    leggi(titoli[posto]);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                premutoTasto = premuto = -1;
                invalidate();
                return true;
        }
        return true;
    }

    private int qualeTasto(float x, float y) {
        if (rChiudi.contains(x, y)) return CHIUDI;
        if (rRegola.contains(x, y)) return REGOLA;
        if (rAggiorna.contains(x, y)) return AGGIORNA;
        for (int i = 0; i < quanteLinguette; i++) {
            if (linguette[i].contains(x, y)) return LINGUETTA + i;
        }
        return -1;
    }

    private int qualePosto(float x, float y) {
        for (int i = 0; i < POSTI; i++) if (posti[i].contains(x, y)) return i;
        return -1;
    }

    private void premi(int tasto) {
        if (tasto == CHIUDI) {
            if (regia != null) regia.chiudiNotizie();
            return;
        }
        if (tasto == REGOLA) { scelte.apri(); return; }
        if (tasto == AGGIORNA) { notizie.rinfresca(); componi(); return; }
        int i = tasto - LINGUETTA;
        if (i >= 0 && i != scelto) {
            scelto = i;
            // Le due schede rientrano: e' il segno che quello che si vede e'
            // un altro elenco, e non lo stesso che si e' spostato.
            entrata = Anima.ora();
            notizie.aggiorna(true);
            componi();
        }
    }

    /**
     * Apre l'articolo.
     *
     * In Chrome se c'e', perche' il collegamento di Google News arriva
     * all'articolo con un salto fatto in JavaScript: un browser qualsiasi - e
     * sul tablet ce ne sono di mezzi morti, lasciati da chi lo aveva prima - si
     * fermerebbe sulla pagina del salto.
     */
    private void leggi(Notizie.Titolo t) {
        if (t == null || t.link.length() == 0) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(t.link));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setPackage("com.android.chrome");
            if (i.resolveActivity(getContext().getPackageManager()) == null) i.setPackage(null);
            getContext().startActivity(i);
        } catch (Exception e) {
            Log.w(MainActivity.TAG, "notizie: non riesco ad aprire " + t.link, e);
        }
    }

    // =========================================================================
    //  Le scelte: il posto, lo sport, la squadra
    // =========================================================================

    /**
     * Il pannello delle scelte, sopra la pagina.
     *
     * Dentro la vela e non una vela sua, per la stessa ragione dei luoghi del
     * meteo: il telaio ne tiene una alla volta, e aprirne una seconda
     * toglierebbe di scena questa. Si scrive con la tastiera di Casa
     * ({@link Tastierino}) e non con quella di sistema, che si prende meta'
     * schermo.
     *
     * <b>Una scelta vale subito</b>, senza un tasto « salva »: toccare « Tennis »
     * cambia la linguetta dietro il pannello mentre lo si guarda, e un tasto in
     * piu' sarebbe una cosa in piu' da dimenticarsi di premere.
     */
    private final class PannelloScelte {

        boolean aperto;

        static final int NIENTE = 0, LUOGO = 1, SQUADRA = 2;
        static final int P_CHIUDI = 1, P_LUOGO = 2, P_TOGLI_LUOGO = 3, P_SPORT = 4,
                         P_SQUADRA = 5, P_TOGLI_SQUADRA = 6, P_CHIP = 100;

        private int scrivendo = NIENTE;
        private String scritto = "";
        private final Tastierino tastierino = new Tastierino();

        private final RectF pannello = new RectF(), rChiudiP = new RectF(), barra = new RectF();
        private final RectF rLuogo = new RectF(), rTogliLuogo = new RectF();
        private final RectF rSport = new RectF();
        private final RectF rSquadra = new RectF(), rTogliSquadra = new RectF();
        private final RectF[] chip = new RectF[Notizie.DISCIPLINE.length];
        private final Testo.Riga[] rChip = new Testo.Riga[Notizie.DISCIPLINE.length];
        private final Testo.Riga rLuogoT = new Testo.Riga(), rSquadraT = new Testo.Riga();
        private final Testo.Riga rBarra = new Testo.Riga(), rNotaP = new Testo.Riga();
        private float yLuogo, ySport, ySquadra, yNota;
        private int premutoP = -1;

        private String testoLuogo = "", testoSquadra = "";

        PannelloScelte() {
            for (int i = 0; i < chip.length; i++) {
                chip[i] = new RectF();
                rChip[i] = new Testo.Riga();
            }
        }

        void misura(int w, int h) {
            float mg = m.margine;
            pannello.set(w * 0.16f, mg, w * 0.84f, h - mg);
            rChiudiP.set(pannello.right - m.s3 - m.bersaglio, pannello.top + m.s3,
                         pannello.right - m.s3, pannello.top + m.s3 + m.bersaglio);
            float x0 = pannello.left + m.s4, x1 = pannello.right - m.s4;
            float altaPill = Math.max(m.bersaglio, m.corpo * 2.1f);

            float y = rChiudiP.bottom + m.s3;
            yLuogo = y + m.micro;
            y += m.micro + m.s2;
            rLuogo.set(x0, y, x1, y + altaPill);
            rTogliLuogo.set(x1 - m.bersaglio - m.s1, y, x1, y + altaPill);
            y += altaPill + m.s5 * 0.8f;

            float altaTog = altaPill * 0.82f;
            rSport.set(x1 - m.bersaglio * 2.6f, y, x1, y + altaTog);
            ySport = rSport.centerY() + m.micro * 0.36f;
            y += altaTog + m.s3;

            int perRiga = 4;
            float gap = m.s2;
            float largo = (x1 - x0 - gap * (perRiga - 1)) / perRiga;
            float alta = altaPill * 1.05f;
            for (int i = 0; i < chip.length; i++) {
                int r = i / perRiga, c = i % perRiga;
                float a = y + (alta + gap) * r, l = x0 + (largo + gap) * c;
                chip[i].set(l, a, l + largo, a + alta);
            }
            int righe = (chip.length + perRiga - 1) / perRiga;
            y += alta * righe + gap * (righe - 1) + m.s5 * 0.8f;

            ySquadra = y + m.micro;
            y += m.micro + m.s2;
            rSquadra.set(x0, y, x1, y + altaPill);
            rTogliSquadra.set(x1 - m.bersaglio - m.s1, y, x1, y + altaPill);
            yNota = rSquadra.bottom + m.s4 + m.nota;

            float cimaScrittura = rChiudiP.bottom + m.s3 + m.micro + m.s2;
            barra.set(x0, cimaScrittura, x1, cimaScrittura + altaPill);
            float altaTastiera = h * 0.34f;
            tastierino.misura(m, x0, pannello.bottom - m.s3 - altaTastiera,
                              x1, pannello.bottom - m.s3);
        }

        /** Le scritte del pannello: si rifanno quando cambia una scelta. */
        void componi() {
            String luogo = notizie.localita();
            testoLuogo = luogo.length() > 0 ? Notizie.maiuscole(luogo)
                    : "come il meteo: " + Notizie.maiuscole(notizie.luogo());
            String sq = notizie.squadra();
            testoSquadra = sq.length() > 0 ? Notizie.maiuscole(sq) : "nessuna: toccala per scriverla";
        }

        void apri() {
            aperto = true;
            scrivendo = NIENTE;
            componi();
            invalidate();
        }

        void chiudi() {
            aperto = false;
            scrivendo = NIENTE;
            invalidate();
        }

        // ---- disegno --------------------------------------------------------

        void disegna(Canvas c) {
            pVelo.setColor(0xB3000000);
            c.drawRect(0, 0, getWidth(), getHeight(), pVelo);
            pVelo.setColor(0xF2000000);
            vetro.pannello(c, pannello, m.raggio, Tinte.NOTIZIE, Tinte.VELO_QUIETO);

            // Il titolo del foglio, come quello di un foglio di iOS.
            String titolo = scrivendo == LUOGO ? "Le notizie di qui"
                    : scrivendo == SQUADRA ? "La squadra di calcio" : "Le tue notizie";
            pEtich.setTextSize(m.voce);
            pEtich.setColor(Tinte.TESTO);
            pEtich.setTextAlign(Paint.Align.LEFT);
            c.drawText(titolo, pannello.left + m.s4, rChiudiP.centerY() + m.voce * 0.36f, pEtich);
            tasto(c, rChiudiP, Icone.CHIUDI, premutoP == P_CHIUDI, false);

            if (scrivendo != NIENTE) { disegnaScrittura(c); return; }

            etichettaP(c, "Di qui", yLuogo);
            pillola(c, rLuogo, Icone.POSIZIONE, testoLuogo, notizie.localita().length() > 0,
                    premutoP == P_LUOGO, rLuogoT);
            if (notizie.localita().length() > 0) croce(c, rTogliLuogo, premutoP == P_TOGLI_LUOGO);

            boolean sport = notizie.sport();
            etichettaP(c, "Lo sport", ySport);
            // L'interruttore di iOS al posto della capsula « acceso / spento ».
            float altaPista = Math.min(rSport.height() * 0.80f, rSport.width() / 1.65f);
            float largaPista = altaPista * 1.65f;
            binario.set(rSport.centerX() - largaPista / 2f, rSport.centerY() - altaPista / 2f,
                        rSport.centerX() + largaPista / 2f, rSport.centerY() + altaPista / 2f);
            pPieno.setColor(sport ? Tinte.RADIO : Tinte.RIEMPIMENTO_SCELTO);
            c.drawRoundRect(binario, altaPista / 2f, altaPista / 2f, pPieno);
            pPieno.setColor(premutoP == P_SPORT ? Tinte.TESTO_MEDIO : Tinte.TESTO);
            c.drawCircle(sport ? binario.right - altaPista / 2f : binario.left + altaPista / 2f,
                    binario.centerY(), altaPista * 0.43f, pPieno);

            String scelta = notizie.disciplina().chiave;
            float lato = m.icona * 0.9f;
            for (int i = 0; i < chip.length; i++) {
                Notizie.Disciplina d = Notizie.DISCIPLINE[i];
                RectF r = chip[i];
                boolean questa = sport && d.chiave.equals(scelta);
                // Scelta: piena del colore delle notizie, come un filtro
                // acceso di iOS; le altre grigie.
                pPieno.setColor(questa ? Tinte.NOTIZIE
                        : premutoP == P_CHIP + i ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
                c.drawRoundRect(r, m.raggioPiccolo, m.raggioPiccolo, pPieno);
                float cx = r.left + m.s3 + lato / 2f;
                pIcona.setColor(questa ? Tinte.TESTO : sport ? Tinte.TESTO_MEDIO
                                                               : Tinte.TESTO_TENUE);
                Icone.disegna(c, d.icona, cx, r.centerY(), lato, pIcona);
                pNota.setTextSize(m.nota);
                pNota.setColor(questa ? Tinte.TESTO : sport ? Tinte.TESTO_MEDIO : Tinte.TESTO_TENUE);
                float xs = cx + lato / 2f + m.s2;
                c.drawText(rChip[i].in(pNota, d.nome, r.right - m.s2 - xs, m.nota), xs,
                        r.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
            }

            etichettaP(c, "La squadra di calcio", ySquadra);
            pillola(c, rSquadra, Icone.CALCIO, testoSquadra, notizie.squadra().length() > 0,
                    premutoP == P_SQUADRA, rSquadraT);
            if (notizie.squadra().length() > 0) croce(c, rTogliSquadra, premutoP == P_TOGLI_SQUADRA);
        }

        private void disegnaScrittura(Canvas c) {
            boolean luogo = scrivendo == LUOGO;
            pPieno.setColor(Tinte.RIEMPIMENTO);
            c.drawRoundRect(barra, barra.height() / 2f, barra.height() / 2f, pPieno);
            float x = barra.left + m.s3;
            pIcona.setColor(Tinte.TESTO_MEDIO);
            Icone.disegna(c, luogo ? Icone.POSIZIONE : Icone.CALCIO, x + m.icona * 0.5f,
                    barra.centerY(), m.icona, pIcona);
            pTesto.setTextSize(m.corpo);
            pTesto.setColor(scritto.length() > 0 ? Tinte.TESTO : Tinte.TESTO_TENUE);
            String testo = scritto.length() > 0 ? scritto
                    : luogo ? "il nome del posto" : "il nome della squadra";
            c.drawText(rBarra.in(pTesto, testo, barra.right - m.s3 - x - m.icona - m.s2, m.corpo),
                    x + m.icona + m.s2,
                    barra.centerY() - (pTesto.descent() + pTesto.ascent()) / 2f, pTesto);

            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.TESTO_TENUE);
            pNota.setTextAlign(Paint.Align.LEFT);
            String aiuto = luogo
                    ? "La freccia in basso conferma. Vuoto: le notizie del posto del meteo."
                    : "La freccia in basso conferma. Vuoto: nessuna squadra.";
            c.drawText(rNotaP.in(pNota, aiuto, barra.width(), m.nota),
                    barra.left, barra.bottom + m.s3 + m.nota, pNota);

            tastierino.disegna(c, vetro, m, Tinte.NOTIZIE);
        }

        private void etichettaP(Canvas c, String testo, float y) {
            pEtich.setTextSize(m.nota);
            pEtich.setColor(Tinte.TESTO_TENUE);
            pEtich.setTextAlign(Paint.Align.LEFT);
            c.drawText(testo, pannello.left + m.s4, y, pEtich);
        }

        private void pillola(Canvas c, RectF r, int icona, String testo, boolean scritto,
                             boolean giu, Testo.Riga riga) {
            pPieno.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, pPieno);
            float x = r.left + m.s3;
            pIcona.setColor(scritto ? Tinte.NOTIZIE : Tinte.TESTO_TENUE);
            Icone.disegna(c, icona, x + m.icona * 0.5f, r.centerY(), m.icona, pIcona);
            pTesto.setTextSize(m.corpo);
            pTesto.setColor(scritto ? Tinte.TESTO : Tinte.TESTO_TENUE);
            float xs = x + m.icona + m.s2;
            c.drawText(riga.in(pTesto, testo, r.right - m.bersaglio - m.s2 - xs, m.corpo), xs,
                    r.centerY() - (pTesto.descent() + pTesto.ascent()) / 2f, pTesto);
        }

        private void croce(Canvas c, RectF r, boolean giu) {
            pIcona.setColor(giu ? Tinte.ALLARME : Tinte.TESTO_TENUE);
            Icone.disegna(c, Icone.CHIUDI, r.centerX(), r.centerY(), m.icona * 0.8f, pIcona);
        }

        // ---- tocco ----------------------------------------------------------

        boolean tocco(MotionEvent e, float x, float y) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (scrivendo != NIENTE && tastierino.area().contains(x, y)) {
                        tastierino.premi(x, y);
                        premutoP = -1;
                    } else {
                        premutoP = quale(x, y);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP: {
                    if (scrivendo != NIENTE && tastierino.area().contains(x, y)) {
                        tastierino.lascia();
                        scrivi(tastierino.tocco(x, y));
                        invalidate();
                        return true;
                    }
                    int p = premutoP;
                    premutoP = -1;
                    if (p >= 0 && p == quale(x, y)) premi(p);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    tastierino.lascia();
                    premutoP = -1;
                    invalidate();
                    return true;
            }
            return true;
        }

        private int quale(float x, float y) {
            if (rChiudiP.contains(x, y)) return P_CHIUDI;
            if (scrivendo != NIENTE) return -1;
            // Le crocette prima delle pillole: stanno dentro di loro.
            if (notizie.localita().length() > 0 && rTogliLuogo.contains(x, y)) return P_TOGLI_LUOGO;
            if (rLuogo.contains(x, y)) return P_LUOGO;
            if (rSport.contains(x, y)) return P_SPORT;
            for (int i = 0; i < chip.length; i++) if (chip[i].contains(x, y)) return P_CHIP + i;
            if (notizie.squadra().length() > 0 && rTogliSquadra.contains(x, y)) return P_TOGLI_SQUADRA;
            if (rSquadra.contains(x, y)) return P_SQUADRA;
            return -1;
        }

        private void premi(int p) {
            String luogo = notizie.localita(), squadra = notizie.squadra();
            boolean sport = notizie.sport();
            String disciplina = notizie.disciplina().chiave;
            if (p == P_CHIUDI) {
                // Mentre si scrive, la X torna alle scelte senza cambiare
                // niente; dalle scelte, chiude il pannello.
                if (scrivendo != NIENTE) { scrivendo = NIENTE; return; }
                chiudi();
                return;
            }
            if (p == P_LUOGO)   { scrivi(LUOGO, luogo); return; }
            if (p == P_SQUADRA) { scrivi(SQUADRA, squadra); return; }
            if (p == P_TOGLI_LUOGO)   { notizie.imposta("", sport, disciplina, squadra); return; }
            if (p == P_TOGLI_SQUADRA) { notizie.imposta(luogo, sport, disciplina, ""); return; }
            if (p == P_SPORT) { notizie.imposta(luogo, !sport, disciplina, squadra); return; }
            if (p >= P_CHIP) {
                // Scegliere uno sport accende anche la modalita': chi tocca
                // « Tennis » vuole le notizie del tennis, non una spiegazione
                // del perche' non compaiono.
                Notizie.Disciplina d = Notizie.DISCIPLINE[p - P_CHIP];
                notizie.imposta(luogo, true, d.chiave, squadra);
            }
        }

        private void scrivi(int cosa, String partenza) {
            scrivendo = cosa;
            scritto = partenza == null ? "" : partenza.toLowerCase(java.util.Locale.ITALIAN);
        }

        private void scrivi(int tasto) {
            if (tasto == Tastierino.NIENTE) return;
            if (tasto == Tastierino.CHIUDI) { conferma(); return; }
            if (tasto == Tastierino.CANCELLA) {
                if (scritto.length() > 0) scritto = scritto.substring(0, scritto.length() - 1);
            } else if (tasto == Tastierino.SPAZIO) {
                if (scritto.length() > 0 && !scritto.endsWith(" ")) scritto = scritto + " ";
            } else if (scritto.length() < 40) {
                scritto = scritto + (char) tasto;
            }
        }

        private void conferma() {
            String nuovo = scritto.trim();
            String luogo = notizie.localita(), squadra = notizie.squadra();
            boolean sport = notizie.sport();
            String disciplina = notizie.disciplina().chiave;
            if (scrivendo == LUOGO) {
                notizie.imposta(nuovo, sport, disciplina, squadra);
            } else if (scrivendo == SQUADRA) {
                // Una squadra scritta accende lo sport, per la stessa ragione
                // dello sport scelto: la si e' scritta per vederne le notizie.
                notizie.imposta(luogo, nuovo.length() > 0 || sport, disciplina, nuovo);
            }
            scrivendo = NIENTE;
        }
    }
}
