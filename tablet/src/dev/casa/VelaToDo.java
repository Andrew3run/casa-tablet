package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * La To-Do List: quello che c'e' da fare, scritto o dettato.
 *
 * <h3>Perche' e' separata dal calendario</h3>
 *
 * Sono due cose che si somigliano solo di lontano. Un impegno <b>ha un'ora e
 * arriva da fuori</b> - lo ha scritto qualcuno sul telefono, e qui si legge e
 * basta. Una cosa da fare <b>non ha un'ora e nasce qui</b>: « comprare il pane
 * » si dice mentre si cucina, e si spunta passando.
 *
 * Metterle nella stessa pagina voleva dire due colonne strette per due cose che
 * si guardano in momenti diversi; metterle in due voci della barra voleva dire
 * una barra da otto. Sono due tessere nella sezione App, e ognuna si prende lo
 * schermo intero quando la si apre.
 *
 * <h3>Si scrive con due dita o con la voce</h3>
 *
 * Il tastierino e' quello della sezione Musica ({@link Tastierino}): minuscole,
 * spazio, cancella. Per una lista della spesa basta. Per tutto il resto c'e'
 * <b>DETTA</b>, che apre il microfono e manda quello che sente qui invece che
 * ai comandi - ed e' il modo naturale di scrivere una riga con le mani in
 * pasta.
 *
 * <h3>Le spuntate non spariscono subito</b></h3>
 *
 * Un tocco spunta, un altro rimette in piedi. Le spuntate scendono in fondo,
 * sbarrate e smorte, e dopo tre giorni se ne vanno da sole ({@link Appunti});
 * il cestino in alto le porta via subito. Sparire all'istante sarebbe sbagliato:
 * su un tablet appeso al muro si spunta per sbaglio, e una riga che si dissolve
 * non si sa piu' com'era scritta.
 */
public class VelaToDo extends View implements Telaio.Velata {

    /** Quello che la pagina chiede alla regia. */
    public interface Regia {
        void chiudiToDo();
        /** Apre il microfono e manda quello che sente a questa lista, non ai
         *  comandi. */
        void dettaUnaNota(String lista);
    }

    private static final String TITOLO = "To-Do List";

    /** Quante righe si tengono pronte: piu' di cosi' non ci starebbero. */
    private static final int MAX_RIGHE = 8;

    private final Misure m;
    private Vetro vetro;
    private Appunti appunti;
    private Regia regia;

    private final Paint pTitolo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSegno  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTasto  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pVelo   = new Paint();

    private final RectF card      = new RectF();
    private final RectF rChiudi   = new RectF();
    private final RectF rCestino  = new RectF();
    private final RectF tScrivi   = new RectF();
    private final RectF tDetta    = new RectF();
    private final RectF tAggiungi = new RectF();
    /** Le due linguette: le cose da fare e la spesa. */
    private final RectF tCose     = new RectF();
    private final RectF tSpesa    = new RectF();
    private final RectF bozza     = new RectF();
    private final RectF appoggio  = new RectF();
    private final RectF[] righe   = new RectF[MAX_RIGHE];
    private final Testo.Riga[] rTesti = new Testo.Riga[MAX_RIGHE];
    private final Testo.Riga rVuoto = new Testo.Riga();
    private final Testo.Riga rBozza = new Testo.Riga();

    private final Tastierino tastierino = new Tastierino();
    private final StringBuilder scritta = new StringBuilder();
    private boolean scrivendo;

    private int premuta = -1, premutoTasto = -1;
    private long spuntata;
    private int spuntataQuale = -1;
    private long entrata;

    private float altaRiga;

    /**
     * Quale lista si guarda. Si apre sulle cose da fare, che sono quelle di
     * casa; la spesa e' a un tocco, nella linguetta accanto.
     *
     * Due linguette e non due tessere nella sezione App: la spesa e le cose da
     * fare si scrivono con lo stesso tastierino e la stessa voce, e chi sta
     * scrivendo « latte » nella lista sbagliata deve poter cambiare lista
     * senza chiudere e riaprire.
     */
    private String lista = Appunti.COSE;

    /** Le scritte delle linguette, col numero: rifatte quando la lista
     *  cambia, non a ogni fotogramma. */
    private String eCose = "Da fare", eSpesa = "Spesa";

    public VelaToDo(Context c, Misure misure, Vetro v) {
        super(c);
        this.m = misure;
        this.vetro = v;
        setClickable(true);
        for (int i = 0; i < MAX_RIGHE; i++) {
            righe[i] = new RectF();
            rTesti[i] = new Testo.Riga();
        }
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pTitolo.setTextAlign(Paint.Align.LEFT);
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pNota.setTextAlign(Paint.Align.LEFT);
        pSegno.setStrokeCap(Paint.Cap.ROUND);
        pTasto.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pTasto.setTextAlign(Paint.Align.CENTER);
    }

    public void setAppunti(Appunti a) { appunti = a; }
    public void setRegia(Regia r) { regia = r; }

    @Override public void setVetro(Vetro v) { vetro = v; invalidate(); }
    @Override public boolean hasOverlappingRendering() { return false; }

    @Override public void suVelaTolta() {
        chiudiTastierino();
        if (regia != null) regia.chiudiToDo();
    }

    public void suEntrata() {
        entrata = Anima.ora();
        chiudiTastierino();
        etichette();
        disponi();
        invalidate();
    }

    /** La lista e' cambiata: da fuori (una nota dettata, il telefono) o da
     *  qui. */
    public void risveglia() {
        etichette();
        disponi();
        invalidate();
    }

    /** Apre direttamente su una lista: la spesa, per chi l'ha chiesta. */
    public void mostra(String quale) {
        cambia(Appunti.quale(quale));
    }

    private void cambia(String quale) {
        if (lista.equals(quale)) return;
        lista = quale;
        spuntata = 0;
        spuntataQuale = -1;
        entrata = Anima.ora();
        etichette();
        disponi();
        invalidate();
    }

    private void etichette() {
        int cose = appunti == null ? 0 : appunti.quanteDaFare(Appunti.COSE);
        int spesa = appunti == null ? 0 : appunti.quanteDaFare(Appunti.SPESA);
        eCose = cose > 0 ? "Da fare  " + cose : "Da fare";
        eSpesa = spesa > 0 ? "Spesa  " + spesa : "Spesa";
    }

    private boolean nellaSpesa() { return Appunti.SPESA.equals(lista); }

    private void chiudiTastierino() {
        scrivendo = false;
        scritta.setLength(0);
        premutoTasto = -1;
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
        rCestino.set(rChiudi.left - m.s3 - m.bersaglio * 1.2f, rChiudi.top,
                     rChiudi.left - m.s3, rChiudi.bottom);

        // Le linguette subito dopo il titolo, alte come i tasti accanto: sono
        // bersagli da premere, non etichette da leggere.
        pTitolo.setTextSize(m.titolo);
        float xTab = m.margine + pTitolo.measureText(TITOLO) + m.s5;
        float largoTab = Math.max(m.bersaglio * 2.8f, w * 0.14f);
        tCose.set(xTab, rChiudi.top, xTab + largoTab, rChiudi.bottom);
        tSpesa.set(tCose.right + m.s2, rChiudi.top, tCose.right + m.s2 + largoTab, rChiudi.bottom);

        float cima = mg + altaTesta + m.s2;
        float fondo = h - mg;
        if (scrivendo) {
            float cimaTastiera = h * 0.58f;
            tastierino.misura(m, mg, cimaTastiera + m.s4, w - mg, h - mg);
            bozza.set(mg, cimaTastiera - m.s2 - m.bersaglio * 1.2f,
                      w - mg, cimaTastiera - m.s2);
            float largo = Math.max(m.bersaglio * 2.6f, w * 0.12f);
            tAggiungi.set(bozza.right - largo - m.s3, bozza.top + m.s2,
                          bozza.right - m.s3, bozza.bottom - m.s2);
            fondo = bozza.top - m.s3;
        } else {
            bozza.setEmpty();
            tAggiungi.setEmpty();
        }

        // La colonna sta al centro e non larga tutto: una riga di testo lunga
        // milleduecento pixel si legge male, e questa e' una lista da scorrere
        // con l'occhio.
        float largoCard = Math.min(w - mg * 2f, w * 0.72f);
        card.set((w - largoCard) / 2f, cima, (w + largoCard) / 2f, fondo);

        float altaTasti = Math.max(m.bersaglio, h * 0.105f);
        float cimaTasti = card.bottom - m.s3 - altaTasti;
        float mezzo = card.centerX();
        tScrivi.set(card.left + m.s3, cimaTasti, mezzo - m.s1, card.bottom - m.s3);
        tDetta.set(mezzo + m.s1, cimaTasti, card.right - m.s3, card.bottom - m.s3);

        altaRiga = Math.max(m.bersaglio, h * 0.098f);
        float y = card.top + m.s4;
        for (int i = 0; i < MAX_RIGHE; i++) {
            float basso = y + altaRiga;
            if (basso > cimaTasti - m.s2) { righe[i].setEmpty(); continue; }
            righe[i].set(card.left + m.s3, y, card.right - m.s3, basso);
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
        float avanti = Anima.posa(Anima.entrata(entrata, 0));
        if (avanti < 1f) c.translate(0f, (1f - avanti) * m.s5);
        vetro.pannello(c, card, m.raggio, Tinte.AGENDA, Tinte.VELO_QUIETO);
        disegnaLista(c);
        c.restoreToCount(s0);

        if (scrivendo) {
            vetro.pannello(c, bozza, m.raggioPiccolo, Tinte.AGENDA, Tinte.VELO_QUIETO);
            disegnaBozza(c);
            tastierino.disegna(c, vetro, m, Tinte.AGENDA);
        }

        Anima.continua(this, entrata, 1);
        if (spuntata > 0 && Anima.ora() - spuntata < Anima.STATO) postInvalidateOnAnimation();
    }

    private void disegnaTesta(Canvas c) {
        pTitolo.setTextSize(m.titolo);
        pTitolo.setColor(Tinte.TESTO);
        c.drawText(TITOLO, m.margine, rChiudi.centerY() + m.titolo * 0.35f, pTitolo);

        // Il numero delle righe ancora aperte sta dentro la linguetta: si vede
        // quanta spesa c'e' da fare anche guardando le cose di casa.
        // Le due liste sono un controllo a segmenti: un binario solo, e il
        // segmento scelto pieno sopra.
        appoggio.set(tCose.left - m.s1, tCose.top - m.s1, tSpesa.right + m.s1, tSpesa.bottom + m.s1);
        pSegno.setStyle(Paint.Style.FILL);
        pSegno.setColor(Tinte.RIEMPIMENTO);
        c.drawRoundRect(appoggio, m.raggioPiccolo + m.s1, m.raggioPiccolo + m.s1, pSegno);
        segmento(c, tCose, eCose, !nellaSpesa(), premutoTasto == 5);
        segmento(c, tSpesa, eSpesa, nellaSpesa(), premutoTasto == 6);

        if (ceFatte()) tasto(c, rCestino, Icone.CESTINO, null, premutoTasto == 2);
        tasto(c, rChiudi, Icone.CHIUDI, null, premutoTasto == 3);
    }

    private void disegnaLista(Canvas c) {
        List<Appunti.Nota> note = appunti == null
                ? new java.util.ArrayList<Appunti.Nota>() : appunti.tutte(lista);

        if (note.isEmpty()) {
            pNota.setTextSize(m.voce);
            pNota.setColor(Tinte.SPENTO);
            String vuoto = nellaSpesa() ? "niente da comprare" : "niente da fare";
            c.drawText(rVuoto.in(pNota, vuoto, card.width() - m.s4 * 2f, m.voce),
                       card.centerX() - pNota.measureText(vuoto) / 2f,
                       card.top + card.height() * 0.32f, pNota);
            pNota.setTextSize(m.corpo);
            pNota.setColor(Tinte.TESTO_TENUE);
            String come = "« Scrivi », o « Detta » e parla";
            c.drawText(come, card.centerX() - pNota.measureText(come) / 2f,
                       card.top + card.height() * 0.32f + m.voce * 1.4f, pNota);
        }

        for (int i = 0; i < MAX_RIGHE && i < note.size(); i++) {
            RectF r = righe[i];
            if (r.isEmpty()) {
                int restano = note.size() - i;
                if (i > 0 && restano > 0) {
                    RectF ultima = righe[i - 1];
                    pNota.setTextSize(m.nota);
                    pNota.setColor(Tinte.SPENTO);
                    c.drawText("e altre " + restano, ultima.left, ultima.bottom + m.nota * 1.1f,
                               pNota);
                }
                break;
            }
            Appunti.Nota n = note.get(i);
            float avanti = Anima.posa(Anima.entrata(entrata, i));
            if (avanti <= 0f) continue;

            int s0 = c.save();
            if (avanti < 1f) c.translate((1f - avanti) * m.s5, 0f);

            if (premuta == i) {
                appoggio.set(r.left - m.s2, r.top, r.right + m.s2, r.bottom);
                pSegno.setStyle(Paint.Style.FILL);
                pSegno.setColor(Tinte.RIEMPIMENTO);
                c.drawRoundRect(appoggio, m.raggioPiccolo, m.raggioPiccolo, pSegno);
            }

            float raggio = m.voce * 0.42f;
            float cx = r.left + raggio + m.s2, cy = r.centerY();
            float quanto = 1f;
            if (spuntataQuale == i && spuntata > 0) {
                quanto = Math.min(1f, (Anima.ora() - spuntata) / (float) Anima.STATO);
            }
            // Il cerchio dei Promemoria: vuoto e sottile da fare, pieno del
            // colore della lista con la spunta bianca quando e' fatta.
            if (n.fatta) {
                pSegno.setStyle(Paint.Style.FILL);
                pSegno.setColor(Tinte.con(Tinte.AGENDA, Math.round(255 * avanti)));
                c.drawCircle(cx, cy, raggio, pSegno);
                pSegno.setStyle(Paint.Style.STROKE);
                pSegno.setStrokeWidth(Math.max(2f, m.voce * 0.08f));
                pSegno.setColor(Tinte.con(Tinte.TESTO, Math.round(255 * avanti)));
                Icone.disegna(c, Icone.SPUNTA, cx, cy, raggio * 1.25f * Anima.posa(quanto), pSegno);
            } else {
                pSegno.setStyle(Paint.Style.STROKE);
                pSegno.setStrokeWidth(Math.max(1.5f, m.voce * 0.06f));
                pSegno.setColor(Tinte.con(Tinte.TESTO_TENUE, Math.round(255 * avanti)));
                c.drawCircle(cx, cy, raggio, pSegno);
            }

            float xt = cx + raggio + m.s3;
            float largo = r.right - xt - m.s2;
            pTitolo.setTextSize(m.voce);
            pTitolo.setColor(Tinte.con(n.fatta ? Tinte.SPENTO : Tinte.TESTO,
                                       Math.round(255 * avanti)));
            String reso = rTesti[i].adatta(pTitolo, n.testo, largo, m.voce, m.voce * 0.74f);
            float yTesto = cy - (pTitolo.descent() + pTitolo.ascent()) / 2f;
            c.drawText(reso, xt, yTesto, pTitolo);

            if (n.fatta) {
                pSegno.setStyle(Paint.Style.STROKE);
                pSegno.setStrokeWidth(Math.max(1.5f, m.voce * 0.055f));
                pSegno.setColor(Tinte.SPENTO);
                c.drawLine(xt, yTesto - m.voce * 0.28f,
                           xt + pTitolo.measureText(reso) * Anima.posa(quanto),
                           yTesto - m.voce * 0.28f, pSegno);
            }

            // Il capello fra una riga e l'altra, rientrato fin sotto il testo
            // come negli elenchi di iOS.
            if (i + 1 < note.size() && i + 1 < MAX_RIGHE && !righe[i + 1].isEmpty()
                    && premuta != i && premuta != i + 1) {
                pSegno.setStyle(Paint.Style.FILL);
                pSegno.setColor(Tinte.SEPARATORE);
                c.drawRect(xt, r.bottom - 1f, r.right, r.bottom, pSegno);
            }
            c.restoreToCount(s0);
        }

        tasto(c, tScrivi, -1, "Scrivi", premutoTasto == 0, false);
        tasto(c, tDetta, -1, "Detta", premutoTasto == 1, false);
    }

    private void disegnaBozza(Canvas c) {
        pTitolo.setTextSize(m.voce);
        pTitolo.setColor(scritta.length() == 0 ? Tinte.SPENTO : Tinte.TESTO);
        String testo = scritta.length() == 0
                ? (nellaSpesa() ? "scrivi cosa comprare" : "scrivi la cosa da fare")
                : scritta.toString();
        float largo = tAggiungi.left - bozza.left - m.s4 * 2f;
        c.drawText(rBozza.in(pTitolo, testo, largo, m.voce), bozza.left + m.s4,
                   bozza.centerY() - (pTitolo.descent() + pTitolo.ascent()) / 2f, pTitolo);
        tasto(c, tAggiungi, -1, "Aggiungi", premutoTasto == 4, true);
    }

    private void tasto(Canvas c, RectF b, int icona, String testo, boolean giu) {
        tasto(c, b, icona, testo, giu, false);
    }

    /**
     * Un tasto alla iOS. Con l'icona e' un cerchio grigio piccolo (chiudi,
     * cestino); con la scritta una capsula grigia, o piena del colore
     * d'accento se e' l'azione principale.
     */
    private void tasto(Canvas c, RectF b, int icona, String testo, boolean giu, boolean principale) {
        if (b.isEmpty()) return;
        pSegno.setStyle(Paint.Style.FILL);
        if (icona >= 0) {
            float r = Math.min(b.width(), b.height()) * 0.42f;
            pSegno.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
            c.drawCircle(b.centerX(), b.centerY(), r, pSegno);
            pSegno.setStyle(Paint.Style.STROKE);
            pSegno.setStrokeWidth(Math.max(2f, m.icona * 0.08f));
            pSegno.setColor(Tinte.TESTO_MEDIO);
            Icone.disegna(c, icona, b.centerX(), b.centerY(), r * 1.05f, pSegno);
            return;
        }
        if (principale) {
            pSegno.setColor(giu ? Tinte.con(Tinte.HOME, 0xB0) : Tinte.HOME);
        } else {
            pSegno.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        }
        c.drawRoundRect(b, b.height() / 2f, b.height() / 2f, pSegno);
        pTasto.setTextSize(m.corpo);
        pTasto.setColor(Tinte.TESTO);
        float largo = b.width() - m.s3;
        while (pTasto.measureText(testo) > largo && pTasto.getTextSize() > m.corpo * 0.6f) {
            pTasto.setTextSize(pTasto.getTextSize() * 0.92f);
        }
        c.drawText(testo, b.centerX(),
                b.centerY() - (pTasto.descent() + pTasto.ascent()) / 2f, pTasto);
    }

    /** Un segmento del controllo: pieno se e' quello scelto, trasparente sul
     *  binario se no. */
    private void segmento(Canvas c, RectF b, String testo, boolean scelto, boolean giu) {
        if (scelto || giu) {
            pSegno.setStyle(Paint.Style.FILL);
            pSegno.setColor(scelto ? Tinte.SEGMENTO_SCELTO : Tinte.RIEMPIMENTO);
            c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pSegno);
        }
        pTasto.setTextSize(m.corpo);
        pTasto.setColor(scelto ? Tinte.TESTO : Tinte.TESTO_MEDIO);
        c.drawText(testo, b.centerX(),
                b.centerY() - (pTasto.descent() + pTasto.ascent()) / 2f, pTasto);
    }

    // ---- il tocco -----------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (scrivendo && tastierino.area().contains(x, y)) {
                    tastierino.premi(x, y);
                    invalidate();
                    return true;
                }
                premutoTasto = qualeTasto(x, y);
                premuta = qualeRiga(x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (scrivendo && tastierino.area().contains(x, y)) {
                    batti(tastierino.tocco(x, y));
                    tastierino.lascia();
                    invalidate();
                    return true;
                }
                int tasto = qualeTasto(x, y);
                int riga = qualeRiga(x, y);
                if (tasto >= 0 && tasto == premutoTasto) premi(tasto);
                else if (riga >= 0 && riga == premuta) spunta(riga);
                premutoTasto = -1;
                premuta = -1;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                premutoTasto = -1;
                premuta = -1;
                tastierino.lascia();
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int qualeTasto(float x, float y) {
        if (tScrivi.contains(x, y)) return 0;
        if (tDetta.contains(x, y)) return 1;
        if (ceFatte() && rCestino.contains(x, y)) return 2;
        if (rChiudi.contains(x, y)) return 3;
        if (scrivendo && tAggiungi.contains(x, y)) return 4;
        if (tCose.contains(x, y)) return 5;
        if (tSpesa.contains(x, y)) return 6;
        return -1;
    }

    private int qualeRiga(float x, float y) {
        if (appunti == null) return -1;
        List<Appunti.Nota> note = appunti.tutte(lista);
        for (int i = 0; i < MAX_RIGHE && i < note.size(); i++) {
            if (!righe[i].isEmpty() && righe[i].contains(x, y)) return i;
        }
        return -1;
    }

    private boolean ceFatte() {
        if (appunti == null) return false;
        for (Appunti.Nota n : appunti.tutte(lista)) if (n.fatta) return true;
        return false;
    }

    private void premi(int tasto) {
        switch (tasto) {
            case 0:
                scrivendo = true;
                scritta.setLength(0);
                disponi();
                break;
            case 1:
                chiudiTastierino();
                disponi();
                if (regia != null) regia.dettaUnaNota(lista);
                break;
            case 2:
                if (appunti != null) appunti.pulisciFatte(lista);
                break;
            case 3:
                if (regia != null) regia.chiudiToDo();
                break;
            case 4:
                aggiungiLaBozza();
                break;
            case 5:
                cambia(Appunti.COSE);
                break;
            case 6:
                cambia(Appunti.SPESA);
                break;
        }
    }

    private void spunta(int quale) {
        if (appunti == null) return;
        List<Appunti.Nota> note = appunti.tutte(lista);
        if (quale < 0 || quale >= note.size()) return;
        Appunti.Nota n = note.get(quale);
        boolean diventaFatta = !n.fatta;
        appunti.inverti(n);
        spuntata = diventaFatta ? Anima.ora() : 0;
        spuntataQuale = diventaFatta ? quale : -1;
    }

    private void batti(int tasto) {
        if (tasto == Tastierino.CANCELLA) {
            if (scritta.length() > 0) scritta.setLength(scritta.length() - 1);
        } else if (tasto == Tastierino.SPAZIO) {
            if (scritta.length() > 0 && scritta.charAt(scritta.length() - 1) != ' ') {
                scritta.append(' ');
            }
        } else if (tasto == Tastierino.CHIUDI) {
            // Chiudere con qualcosa scritto la aggiunge: chi ha battuto dieci
            // lettere e poi chiude la tastiera non voleva buttarle.
            aggiungiLaBozza();
        } else if (tasto >= 0) {
            if (scritta.length() < 80) scritta.append((char) tasto);
        }
    }

    private void aggiungiLaBozza() {
        // Una riga per volta anche nella spesa: col tastierino si scrive una
        // cosa e si preme aggiungi, e « sale e pepe » scritto a mano resta come
        // l'ha voluto chi l'ha battuto.
        if (appunti != null && scritta.length() > 0) appunti.aggiungi(scritta.toString(), false, lista);
        chiudiTastierino();
        disponi();
        invalidate();
    }
}
