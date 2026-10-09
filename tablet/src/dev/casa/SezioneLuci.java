package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;

import java.util.List;

/**
 * Le lampade e le routine.
 *
 * Si chiama SezioneLuci e non SezioneCasa: {@code dev.casa.SezioneCasa} sarebbe
 * illeggibile in uno stack trace. L'etichetta "Casa" vive solo in titolo().
 *
 * Le lampade sono Tuya sulla rete locale, comandate direttamente dal tablet
 * senza passare da nessun cloud: si accendono anche a PC spento e con internet
 * giu'. Chi parla con loro e' {@link Luci}; qui c'e' solo quello che si vede.
 *
 * <h3>Due schermate, e la divisione non e' estetica</h3>
 *
 * <b>La griglia</b> - routine in cima, lampade sotto - ha bersagli grandi
 * perche' si premono entrando in una stanza, al volo, spesso al buio e senza
 * fermarsi a leggere. Un tocco accende o spegne.
 *
 * <b>Il dettaglio</b> di una lampada sola ha bersagli fitti - sette livelli,
 * dodici tinte, tre bianchi - perche' li si guarda mentre si regola, con
 * l'attenzione addosso. Mettere tutto in una schermata voleva dire o una
 * griglia illeggibile o dei controlli che non si centrano.
 *
 * Ci si arriva dall'angolo « regola » della tessera, e si torna con l'indietro:
 * {@link #suIndietro()} chiude il dettaglio prima che il Telaio pensi a
 * cambiare sezione.
 *
 * <h3>I controlli non spariscono mai</h3>
 *
 * Quali cursori ha senso disegnare lo dice la lampada rispondendo, non lo
 * decidiamo noi: una lampadina a colori e una plafoniera bianca mandano numeri
 * diversi. Ma dopo un comando la lampada rimanda <b>solo quello che e'
 * cambiato</b>, e prendere quella risposta parziale per il quadro completo
 * voleva dire concludere che appena accesa non regola piu' niente - e vedersi
 * sparire i controlli sotto le dita proprio mentre li si usa. Lo stato completo
 * lo tiene {@link Tuya}, che lo aggiorna pezzo per pezzo.
 */
public class SezioneLuci extends Sezione {

    /** I livelli di luce pronti. Sette: piu' fitti non si centrano con un
     *  dito, meno non bastano a scegliere davvero. Lo zero non c'e' - una
     *  lampada al minimo resta accesa, per spegnerla c'e' l'interruttore. */
    private static final int[] LIVELLI = { 10, 25, 40, 55, 70, 85, 100 };

    /**
     * Le tinte pronte. Dodici perche' sei erano poche per scegliere davvero, e
     * ventiquattro sarebbero pastiglie troppo strette per un dito.
     */
    private static final int[] TINTE = {
        0xFFFF2D2D, 0xFFFF6A00, 0xFFFFB300, 0xFFFFE94D,
        0xFF8CD832, 0xFF2ED573, 0xFF1ABC9C, 0xFF00C2FF,
        0xFF2E7CF6, 0xFF6C5CE7, 0xFFB14BFF, 0xFFFF4FA3,
    };

    /** Il bianco: caldo, neutro, freddo. */
    private static final int[] BIANCHI = { 0, 50, 100 };
    private static final String[] NOMI_BIANCHI = { "caldo", "neutro", "freddo" };

    private final Paint pTitolo  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNome    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStato   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSegno   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPieno   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  percorso = new Path();

    private final RectF appoggio = new RectF();

    // ---- griglia ----
    private final RectF aggiornaArea = new RectF();

    /** I due pannelli: le scene a sinistra, le lampade a destra. Il vuoto
     *  che avanza dentro un pannello con un titolo si legge come posto per
     *  le prossime; lo stesso vuoto senza bordo si legge come una schermata
     *  rotta. */
    private final RectF pannelloScene   = new RectF();
    private final RectF pannelloLampade = new RectF();
    private RectF[] tessRoutine = new RectF[0];
    private RectF[] tessLampade = new RectF[0];
    private Testo.Riga[] rRoutine = new Testo.Riga[0];
    private Testo.Riga[] rLampade = new Testo.Riga[0];
    private Testo.Riga[] rStati   = new Testo.Riga[0];
    /**
     * « accesa al 40% » composta quando cambia, non a ogni fotogramma: la
     * concatenazione alloca, e qui vale la regola di tutto il progetto - dentro
     * il disegno non si crea niente. Cambia quando risponde una lampada, cioe'
     * qualche volta al giorno.
     */
    private String[] statiTesto = new String[0];
    /** L'angolo « regola » dentro ogni tessera di lampada. */
    private RectF[] regolaArea = new RectF[0];

    // ---- dettaglio ----
    private final RectF cardDettaglio = new RectF();
    private final RectF indietroArea  = new RectF();
    private final RectF interruttore  = new RectF();
    private final RectF[] areeLivelli = new RectF[LIVELLI.length];
    private final RectF[] areeTinte   = new RectF[TINTE.length];
    private final RectF[] areeBianchi = new RectF[BIANCHI.length];
    private final Testo.Riga rTitoloDettaglio = new Testo.Riga();
    private final Testo.Riga rStatoDettaglio  = new Testo.Riga();
    private final Testo.Riga[] rBianchi = new Testo.Riga[BIANCHI.length];
    private float yLivelli, yTinte, yBianchi, yEtichette;

    /** I corpi vengono da {@link Misure}: qui restano solo le larghezze che
     *  questa schermata calcola per se'. */
    private float larghezzaTessera, larghezzaDettaglio;

    private Luci luci;

    /** Se non e' null si sta guardando il dettaglio di questa lampada. */
    private Lampada aperta;

    /** Cosa e' premuto adesso: il tipo, e l'indice dentro il tipo. */
    private int premutoCosa = -1, premutoIndice = -1;
    private static final int P_ROUTINE = 0, P_LAMPADA = 1, P_REGOLA = 2, P_AGGIORNA = 3,
                             P_INDIETRO = 4, P_INTERRUTTORE = 5, P_LIVELLO = 6,
                             P_TINTA = 7, P_BIANCO = 8;

    public SezioneLuci(Context c, Misure m) {
        super(c, m);
        for (int i = 0; i < areeLivelli.length; i++) areeLivelli[i] = new RectF();
        for (int i = 0; i < areeTinte.length; i++)   areeTinte[i] = new RectF();
        for (int i = 0; i < areeBianchi.length; i++) { areeBianchi[i] = new RectF();
                                                       rBianchi[i] = new Testo.Riga(); }

        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pNome.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pPieno.setStyle(Paint.Style.FILL);
        pStato.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pSegno.setStyle(Paint.Style.STROKE);
        pSegno.setStrokeCap(Paint.Cap.ROUND);
        pSegno.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public String titolo() { return "Casa"; }
    @Override public int tinta() { return Tinte.LUCI; }

    /** Una lampadina. Contornata quando si e' altrove, piena quando si e' qui. */
    @Override public int icona()      { return Icone.LAMPADA; }
    @Override public int iconaPiena() { return Icone.LAMPADA_PIENA; }

    // ---- quello che arriva da fuori ---------------------------------------

    /** Le luci arrivano da MainActivity: sono le stesse che comanda la voce,
     *  non un secondo elenco che si scorderebbe cosa ha acceso l'altro. */
    public void setLuci(Luci l) {
        luci = l;
        ricomponi();
    }

    /**
     * Le tessere si rifanno: l'elenco e' cambiato sotto i piedi.
     *
     * La chiama {@link MainActivity} quando il PC ha appena mandato una
     * configurazione nuova. Serve perche' le aree premibili si calcolano una
     * volta - {@link #prepara()} girava solo entrando e a ogni cambio di
     * misura - e i cicli che disegnano sono limitati da quante ne erano state
     * calcolate: con tre routine gia' misurate, la quarta arrivata dal PC non
     * si disegnava <b>e non si premeva</b>, e riappariva soltanto al riavvio
     * di Casa. Da fuori sembrava che il PC ne avesse mandata una in meno.
     */
    public void ricomponi() {
        prepara();
        invalidate();
    }

    /** La chiama chi riceve il cambio di stato. */
    public void risveglia() {
        componiStati();
        invalidate();
    }

    /** Che cosa dice la riga sotto il nome di ogni lampada. Una riga sola, e
     *  dice sempre qualcosa: « spenta » e' informazione, il vuoto no. */
    private void componiStati() {
        if (luci == null) return;
        List<Lampada> lampade = luci.elenco();
        for (int i = 0; i < statiTesto.length && i < lampade.size(); i++) {
            Lampada l = lampade.get(i);
            String guasto = l.guasto();
            if (l.inCorso)            statiTesto[i] = "…";
            else if (guasto != null)  statiTesto[i] = guasto;
            else if (l.accesa() && l.stato.haLuminosita && l.stato.luminosita > 0)
                                      statiTesto[i] = "accesa al " + l.stato.luminosita + "%";
            else if (l.accesa())      statiTesto[i] = "accesa";
            else if (l.stato == null) statiTesto[i] = "non ancora interrogata";
            else                      statiTesto[i] = "spenta";
        }
    }

    /**
     * Entrando si rilegge, e si ascolta chi si e' spostato.
     *
     * E' l'unico momento in cui Casa interroga le lampade di sua iniziativa:
     * un ciclo che le sveglia ogni pochi secondi toglierebbe loro proprio il
     * risparmio di corrente che le fa durare, e su una schermata che nessuno
     * sta guardando non servirebbe a niente.
     */
    @Override
    public void suEntrata() {
        super.suEntrata();
        if (luci == null) return;
        luci.aggiorna();
        luci.scopri();
        // E si continua a rileggerle finche' qualcuno guarda: una lampada
        // accesa dall'app o dall'interruttore a muro non avvisa nessuno, e
        // senza questo la tessera resterebbe spenta davanti a una lampada
        // accesa - col risultato che premerla la spegne invece di accenderla.
        luci.seguiDaVicino(true);
    }

    /** Fuori scena si smette di chiedere: le lampade Tuya spengono la radio in
     *  ricezione per risparmiare, e svegliarle per una schermata che nessuno
     *  sta guardando e' il modo di accorciarne la vita senza guadagnarci
     *  niente. */
    @Override
    public void suUscita() {
        super.suUscita();
        if (luci != null) luci.seguiDaVicino(false);
    }

    /** L'indietro chiude il dettaglio prima che il Telaio cambi sezione. */
    @Override
    public boolean suIndietro() {
        if (aperta == null) return false;
        aperta = null;
        invalidate();
        return true;
    }

    // ---- misure ------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);

        pTitolo.setTextSize(m.micro);
        pNome.setTextSize(m.voce);
        pStato.setTextSize(m.nota);
        pSegno.setStrokeWidth(Math.max(2f, h * 0.004f));

        float mg = m.margine;
        float lato = Math.max(m.bersaglio, h * 0.062f);
        aggiornaArea.set(w - mg - lato, mg, w - mg, mg + lato);

        cardDettaglio.set(mg, mg, w - mg, h - mg);
        larghezzaDettaglio = cardDettaglio.width() - m.s4 * 2f;
        disponiDettaglio(h);
        prepara();
    }

    /**
     * La griglia, in due pannelli affiancati.
     *
     * <b>Perche' due colonne e non una fila di tessere.</b> La versione di
     * prima metteva routine e lampade una sotto l'altra a partire dall'angolo
     * in alto a sinistra, e con la casa vera - tre scene e due lampade - il
     * risultato era un blocco di roba in un quarto di schermo e due terzi di
     * vuoto sulla destra. Centrare il blocco non risolveva: spostava il vuoto.
     *
     * Il vuoto non si toglie ingrandendo le tessere (era il difetto originale:
     * tessere da mezzo schermo per un nome e una parola) ma <b>dandogli un
     * bordo</b>. Dentro un pannello con un titolo, lo spazio che avanza si
     * legge come posto per le prossime lampade; fuori, si legge come una
     * schermata che non ha finito di caricare.
     *
     * <b>Le tessere hanno comunque una misura massima.</b> Oltre la larghezza
     * di una mano il bersaglio e' gia' preso, e piu' grande non si preme
     * meglio.
     */
    private void prepara() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || luci == null) return;

        List<Routine> routine = luci.routine();
        List<Lampada> lampade = luci.elenco();

        if (tessRoutine.length != routine.size()) {
            tessRoutine = nuoveAree(routine.size());
            rRoutine = nuoveRighe(routine.size());
        }
        if (tessLampade.length != lampade.size()) {
            tessLampade = nuoveAree(lampade.size());
            regolaArea  = nuoveAree(lampade.size());
            rLampade = nuoveRighe(lampade.size());
            rStati   = nuoveRighe(lampade.size());
            statiTesto = new String[lampade.size()];
        }
        componiStati();

        float mg = m.margine;
        float cima = aggiornaArea.bottom + m.s3;
        float fondo = h - mg;
        float bandaTitolo = m.s3 + m.micro + m.s3;

        // La colonna delle scene e' stretta: sono pastiglie con un nome corto,
        // e allargarle non le rende ne' piu' leggibili ne' piu' premibili.
        float largaScene = routine.isEmpty() ? 0f : Math.min(w * 0.27f, h * 0.42f);
        if (routine.isEmpty()) {
            pannelloScene.setEmpty();
        } else {
            pannelloScene.set(mg, cima, mg + largaScene, fondo);
        }
        float xL = routine.isEmpty() ? mg : pannelloScene.right + m.s3;
        pannelloLampade.set(xL, cima, w - mg, fondo);

        float altaRoutine = Math.max(m.bersaglio, h * 0.075f);
        for (int i = 0; i < tessRoutine.length; i++) {
            float y = pannelloScene.top + bandaTitolo + (altaRoutine + m.s2) * i;
            if (y + altaRoutine > pannelloScene.bottom - m.s3) { tessRoutine[i].setEmpty(); continue; }
            tessRoutine[i].set(pannelloScene.left + m.s3, y,
                               pannelloScene.right - m.s3, y + altaRoutine);
        }

        float x0 = pannelloLampade.left + m.s3, x1 = pannelloLampade.right - m.s3;
        float y0 = pannelloLampade.top + bandaTitolo;
        float idealeT = h * 0.40f;
        int colonne = Math.max(1, (int) ((x1 - x0 + m.s2) / (idealeT + m.s2)));
        float largaT = Math.min(idealeT, (x1 - x0 - m.s2 * (colonne - 1)) / colonne);
        float altaT  = Math.min(h * 0.27f, largaT * 0.66f);
        for (int i = 0; i < tessLampade.length; i++) {
            int col = i % colonne, riga = i / colonne;
            float x = x0 + (largaT + m.s2) * col;
            float y = y0 + (altaT + m.s2) * riga;
            tessLampade[i].set(x, y, x + largaT, y + altaT);
            // « regola » sta nell'angolo in alto a destra, largo come un dito:
            // il resto della tessera accende e spegne, che e' quello che si fa
            // nove volte su dieci.
            float l = Math.max(m.bersaglio, altaT * 0.32f);
            regolaArea[i].set(tessLampade[i].right - m.s2 - l, y + m.s2,
                              tessLampade[i].right - m.s2, y + m.s2 + l);
        }
        larghezzaTessera = largaT - m.s3 * 2f;
    }

    private static RectF[] nuoveAree(int quante) {
        RectF[] a = new RectF[quante];
        for (int i = 0; i < quante; i++) a[i] = new RectF();
        return a;
    }

    private static Testo.Riga[] nuoveRighe(int quante) {
        Testo.Riga[] r = new Testo.Riga[quante];
        for (int i = 0; i < quante; i++) r[i] = new Testo.Riga();
        return r;
    }

    /** Le tre file di comandi del dettaglio: luce, tinte, bianco. */
    private void disponiDettaglio(int h) {
        float x0 = cardDettaglio.left + m.s4;
        float x1 = cardDettaglio.right - m.s4;

        float lato = Math.max(m.bersaglio, h * 0.070f);
        indietroArea.set(x0, cardDettaglio.top + m.s4, x0 + lato, cardDettaglio.top + m.s4 + lato);

        // L'interruttore e' il bersaglio piu' grande della schermata: e' quello
        // che si preme anche qui dentro, dove si e' entrati per regolare.
        interruttore.set(x1 - lato * 3.2f, indietroArea.top, x1, indietroArea.bottom);

        float y = indietroArea.bottom + m.s5;
        yEtichette = y;

        yLivelli = y + m.s3;
        float altezza = Math.max(m.bersaglio, h * 0.072f);
        float passo = (x1 - x0) / LIVELLI.length;
        for (int i = 0; i < LIVELLI.length; i++) {
            areeLivelli[i].set(x0 + passo * i + m.dp(3), yLivelli,
                               x0 + passo * (i + 1) - m.dp(3), yLivelli + altezza);
        }

        yTinte = yLivelli + altezza + m.s5;
        passo = (x1 - x0) / TINTE.length;
        for (int i = 0; i < TINTE.length; i++) {
            areeTinte[i].set(x0 + passo * i + m.dp(3), yTinte,
                             x0 + passo * (i + 1) - m.dp(3), yTinte + altezza);
        }

        yBianchi = yTinte + altezza + m.s5;
        passo = (x1 - x0) / 3f;
        for (int i = 0; i < BIANCHI.length; i++) {
            areeBianchi[i].set(x0 + passo * i + m.dp(3), yBianchi,
                               x0 + passo * (i + 1) - m.dp(3), yBianchi + altezza);
        }
    }

    // ---- disegno ------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (vetro == null || !vetro.vivo()) return;
        if (luci == null) { disegnaVuoto(c); return; }
        if (aperta != null) { disegnaDettaglio(c); return; }
        disegnaGriglia(c);
    }

    /** Nessuna lampada: si dice al centro, con l'icona smorta di quel che ci
     *  andrebbe. Un pannello vuoto e muto sembra un pezzo che non ha finito di
     *  caricare. */
    private void disegnaVuoto(Canvas c) {
        vetro.pannello(c, cardDettaglio, m.raggio, Tinte.LUCI, Tinte.VELO_QUIETO);
        float cx = cardDettaglio.centerX(), cy = cardDettaglio.centerY();
        pSegno.setColor(Tinte.con(Tinte.SPENTO, 0x66));
        Icone.disegna(c, Icone.LAMPADA, cx, cy - m.s4, m.icona * 2.2f, pSegno);
        pStato.setColor(Tinte.SPENTO);
        pStato.setTextSize(m.corpo);
        pStato.setTextAlign(Paint.Align.CENTER);
        c.drawText("Nessuna lampada collegata", cx, cy + m.s5, pStato);
        pStato.setTextAlign(Paint.Align.LEFT);
    }

    private void disegnaGriglia(Canvas c) {
        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTextSize(m.nota);
        c.drawText("Casa", m.margine, aggiornaArea.top + m.nota, pTitolo);

        // Sotto il titolo, in una riga sola, quello che c'e' da sapere senza
        // leggere le tessere: quante lampade e quante accese. E' l'unica cosa
        // che si guarda passando davanti al tablet senza fermarsi.
        pStato.setColor(Tinte.TESTO_MEDIO);
        pStato.setTextSize(m.nota);
        c.drawText(riepilogo(), m.margine, aggiornaArea.bottom - m.s1, pStato);

        disegnaAggiorna(c);

        List<Routine> routine = luci.routine();
        List<Lampada> lampade = luci.elenco();

        if (!pannelloScene.isEmpty()) {
            vetro.pannello(c, pannelloScene, m.raggio, Tinte.LUCI, Tinte.VELO_QUIETO);
            etichetta(c, "Scene", pannelloScene);
        }
        vetro.pannello(c, pannelloLampade, m.raggio, Tinte.LUCI, Tinte.VELO_QUIETO);
        etichetta(c, "Lampade", pannelloLampade);

        // <b>Le tessere entrano una dopo l'altra.</b> Non e' un vezzo: una
        // schermata che compare tutta insieme si legge come un'immagine, e
        // l'occhio deve poi cercarci dentro l'ordine; una che si costruisce
        // dice l'ordine mentre arriva. Costa una traslazione per tessera -
        // nessun animatore, nessun livello fuori schermo - e si ferma da sola
        // dopo mezzo secondo.
        int quanti = routine.size() + lampade.size();
        for (int i = 0; i < tessRoutine.length && i < routine.size(); i++) {
            if (tessRoutine[i].isEmpty()) continue;
            int salvato = entra(c, i);
            disegnaRoutine(c, i, routine.get(i));
            c.restoreToCount(salvato);
        }
        for (int i = 0; i < tessLampade.length && i < lampade.size(); i++) {
            int salvato = entra(c, routine.size() + i);
            disegnaLampada(c, i, lampade.get(i));
            c.restoreToCount(salvato);
        }
        Anima.continua(this, entrata, quanti);
    }

    /** Il titolino dentro un pannello, sempre allo stesso posto: e' il pettine
     *  che tiene allineate le due colonne. In frase normale, come le
     *  intestazioni dei widget di iOS. */
    private void etichetta(Canvas c, String testo, RectF pannello) {
        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTextSize(m.nota);
        c.drawText(testo, pannello.left + m.s3, pannello.top + m.s3 + m.nota, pTitolo);
    }

    /**
     * Sposta il disegno di quel che manca all'elemento per essere arrivato.
     *
     * Si muove la <b>posizione</b> e non l'opacita': far comparire un pannello
     * di vetro sfumandolo vorrebbe dire un livello fuori schermo per tessera,
     * cioe' una texture da rifare a ogni fotogramma su una GPU che gia' fatica.
     * Uno scorrimento di venti pixel racconta la stessa cosa e costa una
     * matrice - e la dissolvenza generale della sezione ce l'ha gia' messa
     * {@link Telaio}.
     */
    private int entra(Canvas c, int i) {
        int salvato = c.save();
        float t = Anima.posa(Anima.entrata(entrata, i));
        if (t < 1f) c.translate(0f, (1f - t) * m.s5);
        return salvato;
    }

    /** « due lampade, nessuna accesa ». Composta al volo perche' cambia a ogni
     *  interruttore, ed e' una riga corta una volta per disegno - non per
     *  tessera. */
    private String riepilogo() {
        List<Lampada> lampade = luci.elenco();
        int accese = 0;
        for (int i = 0; i < lampade.size(); i++) if (lampade.get(i).accesa()) accese++;
        String quante = lampade.size() == 1 ? "una lampada" : lampade.size() + " lampade";
        String acc = accese == 0 ? "nessuna accesa"
                   : accese == 1 ? "una accesa" : accese + " accese";
        return quante + " · " + acc;
    }

    /** La freccia che gira: rilegge lo stato di tutte. C'e' perche' lo stato
     *  non si aggiorna da solo, e senza un modo di chiederlo l'unica strada
     *  sarebbe uscire dalla sezione e rientrarci - cioe' un gesto che nessuno
     *  indovina. */
    private void disegnaAggiorna(Canvas c) {
        if (premutoCosa == P_AGGIORNA) {
            pPieno.setColor(Tinte.RIEMPIMENTO_SCELTO);
            c.drawCircle(aggiornaArea.centerX(), aggiornaArea.centerY(),
                    Math.min(aggiornaArea.width(), aggiornaArea.height()) * 0.5f, pPieno);
        }
        pSegno.setColor(Tinte.TESTO_MEDIO);
        Icone.disegna(c, Icone.AGGIORNA, aggiornaArea.centerX(), aggiornaArea.centerY(),
                m.icona, pSegno);
    }

    /** Una routine: l'icona di quel che fa, e il nome accanto. */
    private void disegnaRoutine(Canvas c, int i, Routine r) {
        RectF t = tessRoutine[i];
        boolean premuta = premutoCosa == P_ROUTINE && premutoIndice == i;
        // Una capsula piatta; mentre la routine va, piena della sua tinta e
        // col testo scuro sopra. Il colore resta all'icona finche' non parte.
        pPieno.setColor(r.inCorso ? r.colore
                : premuta ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(t, t.height() * 0.5f, t.height() * 0.5f, pPieno);

        float lato = m.icona;
        pNome.setTextSize(m.corpo);
        float x = t.left + m.s3;
        pSegno.setColor(r.inCorso ? Tinte.TESTO_SU_CHIARO : r.colore);
        Icone.disegna(c, iconaRoutine(r), x + lato / 2f, t.centerY(), lato, pSegno);

        x += lato + m.s2;
        pNome.setColor(r.inCorso ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO);
        String nome = rRoutine[i].adatta(pNome, r.nome, t.right - m.s3 - x,
                m.corpo, m.corpo * 0.75f);
        c.drawText(nome, x, t.centerY() - (pNome.descent() + pNome.ascent()) / 2f, pNome);
    }

    /**
     * L'icona di una routine: quella scelta dal PC, o indovinata dal nome.
     *
     * <b>Perche' tutte e due.</b> Prima si indovinava e basta, e per tre
     * routine di sole lampade bastava: le parole che compaiono davvero -
     * notte, cinema, tutte - coprivano quello che si scrive, e per il resto
     * c'era la lampadina, che in una sezione di lampade non e' mai sbagliata.
     *
     * Adesso una routine puo' accendere la radio o aprire Netflix, e la
     * lampadina diventa la risposta sbagliata proprio per le routine nuove.
     * Chi la scrive dal PC sceglie l'icona da una tendina; chi non sceglie
     * niente ricade nell'indovinello di prima, che per "Buonanotte" continua a
     * fare la cosa giusta senza chiedere niente a nessuno.
     */
    static int iconaRoutine(Routine r) {
        if (r == null) return Icone.LAMPADA_PIENA;
        if (r.icona != null && r.icona.length() > 0) {
            int scelta = perNome(r.icona);
            if (scelta >= 0) return scelta;
        }
        return iconaRoutine(r.nome);
    }

    /** Il nome che il PC scrive nel file, tradotto in un'icona. Meno di zero
     *  se non e' un nome che conosciamo: allora si indovina dal nome della
     *  routine, che e' meglio di un quadratino vuoto. */
    private static int perNome(String icona) {
        String n = icona.toLowerCase(java.util.Locale.ITALIAN);
        if (n.equals("notte")) return Icone.NOTTE;
        if (n.equals("sole")) return Icone.SOLE;
        if (n.equals("lampada")) return Icone.LAMPADA_PIENA;
        if (n.equals("accensione")) return Icone.ACCENSIONE;
        if (n.equals("tavolozza")) return Icone.TAVOLOZZA;
        if (n.equals("luminosita")) return Icone.LUMINOSITA;
        if (n.equals("musica")) return Icone.MUSICA_PIENA;
        if (n.equals("radio")) return Icone.RADIO_PIENA;
        if (n.equals("sveglia")) return Icone.SVEGLIA_PIENA;
        if (n.equals("timer")) return Icone.TIMER;
        if (n.equals("volume")) return Icone.VOLUME_PIU;
        if (n.equals("casa")) return Icone.HOME_PIENA;
        return -1;
    }

    static int iconaRoutine(String nome) {
        String n = nome == null ? "" : nome.toLowerCase(java.util.Locale.ITALIAN);
        if (n.contains("notte") || n.contains("dormi") || n.contains("buonanotte")) {
            return Icone.NOTTE;
        }
        if (n.contains("cinema") || n.contains("film")) return Icone.TAVOLOZZA;
        if (n.contains("tutte") || n.contains("accendi") || n.contains("giorno")
                || n.contains("sveglia")) {
            return Icone.SOLE;
        }
        if (n.contains("spegni")) return Icone.ACCENSIONE;
        return Icone.LAMPADA_PIENA;
    }

    /**
     * Una lampada: l'icona, il nome, in che stato e', e - se e' accesa e sa
     * regolarsi - quanta luce fa.
     *
     * La tessera accesa prende <b>la sua</b> tinta, non una generica: due
     * lampade accese non devono sembrare la stessa cosa vista due volte, e da
     * un metro il colore e' l'unica cosa che si legge senza mettere a fuoco.
     */
    private void disegnaLampada(Canvas c, int i, Lampada l) {
        RectF t = tessLampade[i];
        boolean accesa = l.accesa();
        boolean premuta = premutoCosa == P_LAMPADA && premutoIndice == i;
        String guasto = l.guasto();

        // Come le tessere della Casa di Apple: spenta e' un grigio piatto,
        // accesa diventa chiara col testo scuro. Da un metro si vede quali sono
        // accese senza leggere niente; il colore della lampada resta al cerchio.
        pPieno.setColor(accesa ? (premuta ? 0xFFD1D1D6 : Tinte.TESSERA_ACCESA)
                               : (premuta ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO));
        c.drawRoundRect(t, m.raggio, m.raggio, pPieno);

        float x = t.left + m.s3;
        float lato = m.icona * 1.25f;
        float cyIcona = t.top + m.s3 + lato / 2f;

        // L'icona sta in un cerchio: del colore della lampada se e' accesa,
        // grigio se e' spenta, rosso velato se non risponde.
        float rc = lato * 0.82f;
        float cxIcona = x + rc;
        pPieno.setColor(accesa ? l.colore
                : guasto != null ? Tinte.con(Tinte.ALLARME, 0x33) : Tinte.RIEMPIMENTO_SCELTO);
        c.drawCircle(cxIcona, cyIcona + (rc - lato / 2f), rc, pPieno);
        pSegno.setColor(accesa ? Tinte.TESTO_SU_CHIARO
                : (guasto != null ? Tinte.ALLARME : Tinte.TESTO_MEDIO));
        Icone.disegna(c, accesa ? Icone.LAMPADA_PIENA : Icone.LAMPADA,
                cxIcona, cyIcona + (rc - lato / 2f), lato * 0.78f, pSegno);

        disegnaRegola(c, i, accesa);

        // Il nome sta in basso, non sotto l'icona: cosi' le tessere hanno tutte
        // il nome sulla stessa riga e l'occhio le scorre invece di cercarle.
        pNome.setColor(accesa ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO);
        pNome.setTextSize(m.voce);
        float yStato = t.bottom - m.s3;
        float yNome = yStato - m.nota * 1.35f;
        c.drawText(rLampade[i].adatta(pNome, l.nome, larghezzaTessera, m.voce, m.voce * 0.7f),
                   x, yNome, pNome);

        // La riga di stato e' gia' composta: qui si sceglie solo il colore.
        // Quando non risponde lo si scrive - il silenzio senza spiegazione fa
        // sembrare rotto il tablet invece della lampadina.
        int col = guasto != null ? Tinte.ALLARME
                : accesa ? Tinte.TESTO_SU_CHIARO_TENUE : Tinte.TESTO_TENUE;
        String stato = i < statiTesto.length && statiTesto[i] != null ? statiTesto[i] : "";
        pStato.setColor(col);
        pStato.setTextSize(m.nota);
        c.drawText(rStati[i].in(pStato, stato, larghezzaTessera, m.nota), x, yStato, pStato);

        // Quanta luce fa, come filo pieno sotto il nome. Un numero dice il
        // valore, il filo dice la quantita': da un metro si legge solo il
        // secondo, e da un metro e' l'unica cosa che serve.
        if (accesa && l.stato != null && l.stato.haLuminosita && l.stato.luminosita > 0) {
            float y = t.bottom - m.dp(4);
            float largo = t.width() - m.s3 * 2f;
            float quanto = Math.max(0.04f, Math.min(1f, l.stato.luminosita / 100f));
            pPieno.setColor(0x1F000000);
            c.drawRoundRect(x, y, x + largo, y + m.dp(3), m.dp(2), m.dp(2), pPieno);
            pPieno.setColor(Tinte.TESTO_SU_CHIARO_TENUE);
            c.drawRoundRect(x, y, x + largo * quanto, y + m.dp(3), m.dp(2), m.dp(2), pPieno);
        }
    }

    /** I cursori in miniatura: e' il segno che sotto c'e' altro da regolare. */
    private void disegnaRegola(Canvas c, int i, boolean accesa) {
        RectF b = regolaArea[i];
        if (premutoCosa == P_REGOLA && premutoIndice == i) {
            pPieno.setColor(accesa ? 0x1F000000 : Tinte.RIEMPIMENTO_SCELTO);
            c.drawCircle(b.centerX(), b.centerY(), Math.min(b.width(), b.height()) * 0.5f, pPieno);
        }
        pSegno.setColor(accesa ? Tinte.TESTO_SU_CHIARO_TENUE : Tinte.TESTO_MEDIO);
        Icone.disegna(c, Icone.REGOLA, b.centerX(), b.centerY(), m.icona * 0.92f, pSegno);
    }

    // ---- dettaglio ----------------------------------------------------------

    private void disegnaDettaglio(Canvas c) {
        Lampada l = aperta;
        boolean accesa = l.accesa();
        String guasto = l.guasto();

        vetro.pannello(c, cardDettaglio, m.raggio, l.colore, Tinte.VELO_QUIETO);

        // Indietro: una freccia, non una scritta. Si preme senza leggere.
        if (premutoCosa == P_INDIETRO) {
            pPieno.setColor(Tinte.RIEMPIMENTO_SCELTO);
            c.drawCircle(indietroArea.centerX(), indietroArea.centerY(),
                    Math.min(indietroArea.width(), indietroArea.height()) * 0.5f, pPieno);
        }
        pSegno.setColor(Tinte.TESTO_MEDIO);
        Icone.disegna(c, Icone.INDIETRO, indietroArea.centerX(), indietroArea.centerY(),
                m.icona * 0.86f, pSegno);

        float x = indietroArea.right + m.s3;
        pNome.setColor(Tinte.TESTO);
        pNome.setTextSize(m.titolo);
        c.drawText(rTitoloDettaglio.adatta(pNome, l.nome,
                        interruttore.left - x - m.s3, m.titolo, m.titolo * 0.7f),
                   x, indietroArea.centerY() - (pNome.descent() + pNome.ascent()) / 2f, pNome);

        disegnaInterruttore(c, l, accesa);

        // Sotto il titolo, quello che la lampada ha da dire di se'.
        String stato = l.inCorso ? "…"
                     : guasto != null ? guasto
                     : accesa ? "Accesa" : "Spenta";
        pStato.setColor(guasto != null ? Tinte.ALLARME : (accesa ? l.colore : Tinte.TESTO_TENUE));
        pStato.setTextSize(m.nota);
        c.drawText(rStatoDettaglio.in(pStato, stato, larghezzaDettaglio, m.nota),
                   indietroArea.left, yEtichette - m.s4, pStato);

        boolean puoLuce  = l.stato != null && l.stato.haLuminosita;
        boolean puoColore = l.stato != null && l.stato.haColore;
        boolean puoBianco = l.stato != null && l.stato.haTemperatura;

        etichettaRiga(c, "Luce", yEtichette);
        for (int i = 0; i < LIVELLI.length; i++) {
            disegnaLivello(c, i, l, puoLuce);
        }

        if (puoColore) {
            etichettaRiga(c, "Tinta", yTinte - m.s3);
            for (int i = 0; i < TINTE.length; i++) disegnaTinta(c, i);
        }
        if (puoBianco) {
            etichettaRiga(c, "Bianco", yBianchi - m.s3);
            for (int i = 0; i < BIANCHI.length; i++) disegnaBianco(c, i, l);
        }
        if (!puoColore && !puoBianco && l.stato != null && l.stato.raggiunta) {
            pStato.setColor(Tinte.SPENTO);
            c.drawText("Luce unica",
                    cardDettaglio.left + m.s4, yTinte + m.corpo, pStato);
        }
    }

    /** Lo stesso titolino, ma dentro il dettaglio, dove la riga la decide
     *  chi chiama perche' le tre file non sono equidistanti. */
    private void etichettaRiga(Canvas c, String testo, float baseline) {
        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTextSize(m.nota);
        c.drawText(testo, cardDettaglio.left + m.s4, baseline, pTitolo);
    }

    /** L'interruttore grosso in cima: l'icona di accensione e la parola. */
    private void disegnaInterruttore(Canvas c, Lampada l, boolean accesa) {
        boolean premuto = premutoCosa == P_INTERRUTTORE;
        // Accesa: capsula piena del colore della lampada, testo scuro. Spenta:
        // il grigio piatto di ogni comando.
        pPieno.setColor(accesa ? (premuto ? Tinte.fondi(l.colore, 0xFF000000, 0.18f) : l.colore)
                               : (premuto ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO));
        c.drawRoundRect(interruttore, interruttore.height() * 0.5f,
                interruttore.height() * 0.5f, pPieno);
        int colore = accesa ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO;
        String parola = accesa ? "Spegni" : "Accendi";
        pStato.setTextSize(m.corpo);
        float lato = m.icona * 0.9f;
        float largo = pStato.measureText(parola);
        float partenza = interruttore.centerX() - (lato + m.s2 + largo) / 2f;

        pSegno.setColor(colore);
        Icone.disegna(c, Icone.ACCENSIONE, partenza + lato / 2f, interruttore.centerY(),
                lato, pSegno);
        pStato.setColor(colore);
        c.drawText(parola, partenza + lato + m.s2,
                interruttore.centerY() - (pStato.descent() + pStato.ascent()) / 2f, pStato);
    }

    /**
     * Un livello di luce. Quello in cui cade la lampada adesso e' pieno: senza,
     * si preme al buio senza sapere da dove si parte.
     */
    private void disegnaLivello(Canvas c, int i, Lampada l, boolean disponibile) {
        RectF b = areeLivelli[i];
        boolean qui = disponibile && l.stato != null && l.accesa()
                && vicino(l.stato.luminosita, LIVELLI[i]);
        boolean premuto = premutoCosa == P_LIVELLO && premutoIndice == i;

        // Il livello in cui cade la lampada e' chiaro, come un segmento scelto;
        // gli altri sono il grigio piatto dei comandi.
        pPieno.setColor(qui ? Tinte.TESSERA_ACCESA
                : premuto ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pPieno);

        pStato.setColor(disponibile ? (qui ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO) : Tinte.SPENTO);
        pStato.setTextSize(m.corpo);
        pStato.setTextAlign(Paint.Align.CENTER);
        c.drawText(LIVELLI[i] + "%", b.centerX(),
                b.centerY() - (pStato.descent() + pStato.ascent()) / 2f, pStato);
        pStato.setTextAlign(Paint.Align.LEFT);
    }

    /** Vicino a un livello vuol dire piu' vicino a questo che ai suoi vicini. */
    private static boolean vicino(int quanto, int livello) {
        if (quanto < 0) return false;
        int migliore = LIVELLI[0];
        for (int v : LIVELLI) {
            if (Math.abs(v - quanto) < Math.abs(migliore - quanto)) migliore = v;
        }
        return migliore == livello;
    }

    private void disegnaTinta(Canvas c, int i) {
        RectF b = areeTinte[i];
        boolean premuta = premutoCosa == P_TINTA && premutoIndice == i;
        float raggio = m.raggioPiccolo;

        // Le pastiglie del colore si disegnano piene, non velate: una tinta
        // vista attraverso il vetro non e' piu' quella tinta, e sceglierla
        // diventa indovinare.
        pPieno.setColor(TINTE[i]);
        c.drawRoundRect(b, raggio, raggio, pPieno);
        if (premuta) {
            // Premuta: un anello bianco attorno, come la scelta di un colore
            // nei pannelli di iOS.
            pPieno.setStyle(Paint.Style.STROKE);
            pPieno.setStrokeWidth(m.dp(2.5f));
            pPieno.setColor(Tinte.TESTO);
            c.drawRoundRect(b, raggio, raggio, pPieno);
            pPieno.setStyle(Paint.Style.FILL);
        }
    }

    private void disegnaBianco(Canvas c, int i, Lampada l) {
        RectF b = areeBianchi[i];
        boolean premuto = premutoCosa == P_BIANCO && premutoIndice == i;
        pPieno.setColor(Tinte.con(bianco(BIANCHI[i]), premuto ? 0x55 : 0x2E));
        c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pPieno);

        pStato.setColor(Tinte.TESTO);
        pStato.setTextSize(m.corpo);
        pStato.setTextAlign(Paint.Align.CENTER);
        c.drawText(rBianchi[i].in(pStato, NOMI_BIANCHI[i], b.width(), m.corpo),
                b.centerX(), b.centerY() - (pStato.descent() + pStato.ascent()) / 2f, pStato);
        pStato.setTextAlign(Paint.Align.LEFT);
    }

    /** Il colore con cui si velano le tre pastiglie del bianco: ambra per il
     *  caldo, azzurro per il freddo. Il nome da solo non basta - « neutro » e
     *  « freddo » si distinguono leggendo, e qui non si legge. */
    private static int bianco(int percento) {
        if (percento <= 25) return 0xFFFFC780;
        if (percento >= 75) return 0xFFBFE0FF;
        return 0xFFF2F5F7;
    }

    // ---- tocco ---------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                trova(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP: {
                // Si agisce solo se il dito si alza dove si era posato: uno
                // strisciamento che parte da una lampada e finisce su un'altra
                // non deve accendere niente.
                int cosa = premutoCosa, indice = premutoIndice;
                trova(x, y);
                boolean stessoPosto = cosa == premutoCosa && indice == premutoIndice && cosa >= 0;
                premutoCosa = premutoIndice = -1;
                if (stessoPosto) agisci(cosa, indice);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                premutoCosa = premutoIndice = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** Cosa c'e' sotto il dito. « regola » si cerca prima della tessera che lo
     *  contiene, se no vincerebbe sempre la tessera. */
    private void trova(float x, float y) {
        premutoCosa = premutoIndice = -1;
        if (luci == null) return;

        if (aperta != null) {
            if (indietroArea.contains(x, y)) { premutoCosa = P_INDIETRO; return; }
            if (interruttore.contains(x, y)) { premutoCosa = P_INTERRUTTORE; return; }
            int i;
            // I comandi che la lampada non ha non si premono nemmeno: senza
            // questa guardia un livello toccato su una plafoniera bianca
            // tornerebbe indietro come « non risponde », che e' una bugia.
            if (aperta.stato != null && aperta.stato.haLuminosita) {
                i = quale(areeLivelli, x, y);
                if (i >= 0) { premutoCosa = P_LIVELLO; premutoIndice = i; return; }
            }
            if (aperta.stato != null && aperta.stato.haColore) {
                i = quale(areeTinte, x, y);
                if (i >= 0) { premutoCosa = P_TINTA; premutoIndice = i; return; }
            }
            if (aperta.stato != null && aperta.stato.haTemperatura) {
                i = quale(areeBianchi, x, y);
                if (i >= 0) { premutoCosa = P_BIANCO; premutoIndice = i; return; }
            }
            return;
        }

        if (aggiornaArea.contains(x, y)) { premutoCosa = P_AGGIORNA; return; }
        int i = quale(regolaArea, x, y);
        if (i >= 0) { premutoCosa = P_REGOLA; premutoIndice = i; return; }
        i = quale(tessLampade, x, y);
        if (i >= 0) { premutoCosa = P_LAMPADA; premutoIndice = i; return; }
        i = quale(tessRoutine, x, y);
        if (i >= 0) { premutoCosa = P_ROUTINE; premutoIndice = i; }
    }

    private void agisci(int cosa, int i) {
        List<Lampada> lampade = luci.elenco();
        List<Routine> routine = luci.routine();
        switch (cosa) {
            case P_AGGIORNA:
                luci.aggiorna();
                luci.scopri();
                break;
            case P_ROUTINE:
                if (i < routine.size()) luci.esegui(routine.get(i));
                break;
            case P_LAMPADA:
                if (i < lampade.size()) luci.inverti(lampade.get(i));
                break;
            case P_REGOLA:
                if (i < lampade.size()) {
                    aperta = lampade.get(i);
                    // Entrando nel dettaglio si rilegge: i controlli da
                    // disegnare li decide la risposta della lampada, e se non
                    // le si e' mai parlato non ce n'e' ancora nessuna.
                    if (aperta.stato == null) luci.aggiorna();
                }
                break;
            case P_INDIETRO:
                aperta = null;
                break;
            case P_INTERRUTTORE:
                if (aperta != null) luci.accendi(aperta, !aperta.accesa());
                break;
            case P_LIVELLO:
                if (aperta != null) luci.luminosita(aperta, LIVELLI[i]);
                break;
            case P_TINTA:
                if (aperta != null) luci.colore(aperta, TINTE[i] & 0xFFFFFF);
                break;
            case P_BIANCO:
                if (aperta != null) luci.temperatura(aperta, BIANCHI[i]);
                break;
        }
    }

    private static int quale(RectF[] aree, float x, float y) {
        for (int i = 0; i < aree.length; i++) if (aree[i].contains(x, y)) return i;
        return -1;
    }
}
