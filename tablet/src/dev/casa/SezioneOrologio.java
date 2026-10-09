package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.MotionEvent;

import java.util.List;
import java.util.Locale;

/**
 * Timer e sveglie, attorno a una ghiera che scorre.
 *
 * <h3>Una ghiera al posto di due frecce</h3>
 *
 * La prima versione aveva <b>+</b> e <b>−</b> per le ore e per i minuti:
 * corrette, noiose e care. Portare una sveglia dalle 7 alle 22 erano quindici
 * tocchi, e su un apparecchio appeso al muro quindici tocchi sono quindici
 * occasioni di premere accanto. {@link Quadrante} fa lo stesso viaggio con un
 * gesto solo, e mentre lo fa <b>mostra dove si sta andando</b>: si vedono
 * passare le ore, quindi ci si ferma quando si e' arrivati invece di contare.
 *
 * E un cerchio di ore <b>e' gia' la forma di un orologio</b>: le ore stanno
 * dove ce le si aspetta, il bordo porta le sessanta tacche vere e un puntino
 * sul minuto di adesso. La stessa figura fa da orologio e da tastiera.
 *
 * <h3>Una ghiera sola per due mestieri</h3>
 *
 * Prima la schermata era divisa in due colonne, timer a sinistra e sveglie a
 * destra, e ognuna aveva i suoi comandi. Ma <b>un timer e una sveglia sono la
 * stessa domanda posta in due modi</b> - « fra quanto » e « a che ora » - e
 * meritavano lo stesso gesto. Adesso c'e' una ghiera sola: due schede in cima
 * dicono che cosa sta scegliendo, e a destra c'e' l'elenco di quello che e'
 * gia' in piedi.
 *
 * Nel modo <b>timer</b> l'anello esterno sono i minuti (sessanta scatti, un
 * numero ogni cinque: le tacche danno la precisione, i numeri la leggibilita')
 * e quello interno le ore. Nel modo <b>sveglie</b> l'esterno sono le
 * ventiquattro ore e l'interno i minuti a passi di cinque - le 6:47 non sono
 * un'ora a cui qualcuno voglia svegliarsi, e i passi grossi tolgono trenta
 * scatti a quella che si vuole davvero.
 *
 * <h3>Toccare una sveglia la porta nella ghiera</h3>
 *
 * Non c'e' un « modifica » e non c'e' una schermata a parte: si tocca la riga,
 * e la ghiera ci si porta sopra. Da li' girare cambia <i>quella</i> sveglia, e
 * la si vede cambiare anche nell'elenco mentre la si gira. Il pallino a destra
 * della riga resta l'interruttore, perche' accendere e spegnere e' la cosa che
 * si fa piu' spesso e non deve costare un viaggio nella ghiera.
 */
public class SezioneOrologio extends Sezione {

    private static final int TIMER = 0, SVEGLIE = 1;
    private static final String[] NOMI_SCHEDE = { "Timer", "Sveglie" };

    /** Le durate pronte, in minuti: la scorciatoia per quelle di tutti i
     *  giorni. La ghiera resta per tutte le altre. */
    private static final int[] PRONTI = { 1, 3, 5, 10, 15, 30 };
    private static final String[] NOMI_PRONTI = { "1", "3", "5", "10", "15", "30" };

    /** Quante righe si mostrano. Oltre, diventerebbero troppo basse per un
     *  dito: chi ne vuole di piu' ha un problema che non e' l'interfaccia. */
    private static final int MAX_TIMER = 3;
    private static final int MAX_SVEGLIE = 5;

    private static final String[] INIZIALI = { "D", "L", "M", "M", "G", "V", "S" };

    /**
     * Le etichette dei due anelli, composte una volta sola.
     *
     * Nel giro dei minuti del timer i {@code null} non sono buchi: sono
     * sessanta scatti di cui si scrivono solo i multipli di cinque. Le tacche
     * del bordo danno il minuto esatto, sessanta numeri darebbero solo una
     * corona illeggibile.
     */
    private static final String[] ETICHETTE_ORE = new String[24];
    private static final String[] ETICHETTE_MIN5 = new String[12];
    private static final String[] ETICHETTE_MIN60 = new String[60];
    private static final String[] ETICHETTE_ORE6 = new String[6];
    static {
        for (int i = 0; i < 24; i++) ETICHETTE_ORE[i] = String.format(Locale.ITALIAN, "%02d", i);
        for (int i = 0; i < 12; i++) ETICHETTE_MIN5[i] = String.format(Locale.ITALIAN, "%02d", i * 5);
        for (int i = 0; i < 60; i++) ETICHETTE_MIN60[i] = (i % 5 == 0) ? String.valueOf(i) : null;
        for (int i = 0; i < 6; i++) ETICHETTE_ORE6[i] = i + "h";
    }

    private final Paint pTitolo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrande = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSegno  = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** I fondi piatti: segmenti, pastiglie, il pulsante pieno. */
    private final Paint pFondo  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Quadrante ghiera = new Quadrante(this);

    // ---- aree ----
    private final RectF[] areeSchede = { new RectF(), new RectF() };
    private final RectF schede   = new RectF();     // la pastiglia che le contiene
    private final RectF azione   = new RectF();
    private final RectF cestino  = new RectF();
    private final RectF interoAzione = new RectF();
    private final RectF[] areeGiorni = new RectF[7];
    private final RectF[] areePronti = new RectF[PRONTI.length];
    private final RectF[] righe    = new RectF[Math.max(MAX_TIMER, MAX_SVEGLIE)];
    private final RectF[] pallini  = new RectF[Math.max(MAX_TIMER, MAX_SVEGLIE)];
    private final Testo.Riga[] rRighe = new Testo.Riga[Math.max(MAX_TIMER, MAX_SVEGLIE)];

    /**
     * I due pannelli della colonna di destra.
     *
     * Sono la correzione del difetto che si vedeva a occhio nudo: prima la
     * colonna era fatta di pastiglie appoggiate sullo sfondo, e l'intestazione
     * « nessun timer in corso » si calcolava a frazioni dell'altezza <b>senza
     * sapere dove finivano le pastiglie sopra di lei</b>. Le finiva addosso.
     * Adesso ogni blocco e' un pannello con dentro il suo titolo e le sue
     * cose, e il secondo comincia dove finisce il primo: la sovrapposizione
     * non e' stata aggiustata di qualche pixel, e' diventata impossibile.
     */
    private final RectF pannelloGhiera = new RectF();
    private final RectF pannelloPronti = new RectF();
    private final RectF pannelloElenco = new RectF();

    // ---- scritte gia' composte: dentro onDraw non si alloca ----
    private final String[] testoRiga = new String[Math.max(MAX_TIMER, MAX_SVEGLIE)];
    private final String[] testoSotto = new String[Math.max(MAX_TIMER, MAX_SVEGLIE)];
    private String testoCentro = "", testoUnita, testoAzione = "", testoCima = "";

    /** I corpi non stanno piu' qui: sono quelli di {@link Misure}, uguali in
     *  tutte le sezioni. Restano solo le misure che questa schermata calcola
     *  per se'. */
    private float xDestra, larghezzaDestra, passoRiga, altezzaRiga;

    private Orologio orologio;
    private int scheda = TIMER;

    /** La sveglia che la ghiera sta regolando. null = se ne sta componendo una
     *  nuova, che nascera' solo se qualcuno preme « aggiungi ». */
    private Orologio.Sveglia scelta;

    /** I giorni della sveglia ancora da creare. Sette bit come nelle altre. */
    private int giorniNuova = 0x3E;          // da lunedi a venerdi

    private int premutoCosa = -1, premutoIndice = -1;
    private static final int P_SCHEDA = 0, P_AZIONE = 1, P_CESTINO = 2, P_GIORNO = 3,
                             P_PRONTO = 4, P_RIGA = 5, P_PALLINO = 6;

    /**
     * Il puntino del minuto sul bordo si muove da solo, e il conto alla
     * rovescia scorre: finche' la sezione e' in scena si ridisegna al secondo.
     * Fuori scena si ferma - una schermata che nessuno guarda non ha motivo di
     * ridisegnarsi, ed e' la stessa regola del tick dell'orologio nella Home.
     */
    private final Handler battito = new Handler();
    private final Runnable tic = new Runnable() {
        @Override public void run() {
            componiTesti();
            invalidate();
            battito.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    public SezioneOrologio(Context c, Misure m) {
        super(c, m);
        for (int i = 0; i < 7; i++) areeGiorni[i] = new RectF();
        for (int i = 0; i < areePronti.length; i++) areePronti[i] = new RectF();
        for (int i = 0; i < righe.length; i++) {
            righe[i] = new RectF();
            pallini[i] = new RectF();
            rRighe[i] = new Testo.Riga();
        }

        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pGrande.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pSegno.setStyle(Paint.Style.STROKE);
        pSegno.setStrokeCap(Paint.Cap.ROUND);
        pSegno.setStrokeJoin(Paint.Join.ROUND);

        ghiera.setTinta(Tinte.OROLOGIO);
        ghiera.setCambio(new Quadrante.Cambio() {
            @Override public void suScatto() {
                // Mentre il dito gira: la sveglia cambia solo a schermo, cosi'
                // la si vede muoversi anche nell'elenco. Su disco non si scrive.
                if (scheda == SVEGLIE && scelta != null) {
                    scelta.ora = ghiera.esterno();
                    scelta.minuto = ghiera.interno() * 5;
                }
                componiTesti();
                invalidate();
            }
            @Override public void suFermata() {
                // Dito alzato e ghiera agganciata: adesso si scrive e si
                // riprogramma l'allarme.
                if (scheda == SVEGLIE && scelta != null && orologio != null) {
                    orologio.regola(scelta, ghiera.esterno(), ghiera.interno() * 5);
                }
                componiTesti();
                invalidate();
            }
        });
        anelliDellaScheda();
        ghiera.vaiA(5, 0);
    }

    @Override public String titolo() { return "Orologio"; }
    @Override public int tinta() { return Tinte.OROLOGIO; }

    /** Una sveglia. Contornata quando si e' altrove, piena quando si e' qui. */
    @Override public int icona()      { return Icone.SVEGLIA; }
    @Override public int iconaPiena() { return Icone.SVEGLIA_PIENA; }

    // ---- quello che arriva da fuori ----------------------------------------

    /** L'orologio arriva da MainActivity: e' lo stesso che comanda la voce e
     *  che tiene gli allarmi di sistema, non un secondo elenco. */
    public void setOrologio(Orologio o) {
        orologio = o;
        disponi();
        invalidate();
    }

    /** La chiama chi riceve il cambio di stato. */
    public void risveglia() {
        disponi();
        invalidate();
    }

    @Override
    public void suEntrata() {
        super.suEntrata();
        componiTesti();
        battito.removeCallbacks(tic);
        battito.postDelayed(tic, 1000 - (System.currentTimeMillis() % 1000));
    }

    @Override
    public void suUscita() {
        battito.removeCallbacks(tic);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        battito.removeCallbacks(tic);
    }

    /** L'indietro: prima lascia la sveglia che la ghiera aveva in mano, poi
     *  esce dalla sezione. */
    @Override
    public boolean suIndietro() {
        if (scelta == null) return false;
        scelta = null;
        componiTesti();
        invalidate();
        return true;
    }

    // ---- misure --------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        float mg = m.margine;

        pTitolo.setTextSize(m.micro);
        pGrande.setTextSize(m.titolo);
        pNota.setTextSize(m.corpo);
        pSegno.setStrokeWidth(Math.max(2f, h * 0.004f));

        // La colonna sinistra tiene la ghiera e il suo pulsante; la destra
        // quello che e' gia' in piedi. Il taglio sta prima di meta' schermo:
        // una ghiera piu' larga di cosi' non si usa meglio - la si gira con un
        // pollice - mentre le righe dell'elenco portano testo, e il testo lo
        // spazio se lo mangia davvero.
        float taglio = w * 0.455f;
        float xDestra0 = w * 0.485f;

        // Le due schede: un interruttore solo diviso in due, non due pastiglie
        // separate. Diviso in due si legge « o questo o quello »; separate si
        // leggevano come due pulsanti che potevano essere accesi tutti e due.
        //
        // Tutta la colonna sta in una scheda, come la destra: la ghiera
        // lasciata sullo sfondo galleggiava, l'unica cosa della schermata
        // senza un bordo. Dentro, l'interruttore e' largo quanto la scheda,
        // come il controllo a segmenti in cima a una schermata di iOS.
        pannelloGhiera.set(mg, mg, taglio, h - mg);
        float sx = pannelloGhiera.left + m.s3, dx = pannelloGhiera.right - m.s3;
        float altaScheda = Math.max(m.bersaglio, h * 0.062f);
        schede.set(sx, pannelloGhiera.top + m.s3, dx, pannelloGhiera.top + m.s3 + altaScheda);
        for (int i = 0; i < 2; i++) {
            areeSchede[i].set(schede.left + schede.width() / 2f * i + m.dp(3),
                              schede.top + m.dp(3),
                              schede.left + schede.width() / 2f * (i + 1) - m.dp(3),
                              schede.bottom - m.dp(3));
        }

        // Il pulsante in fondo, e - quando si sta regolando una sveglia che
        // esiste gia' - il cestino accanto. Il cestino e' piccolo e in fondo:
        // e' l'unico gesto che non si annulla, e non deve stare sulla strada
        // di quello che si preme sempre.
        float altaAzione = Math.max(m.bersaglio, h * 0.075f);
        float fondo = pannelloGhiera.bottom - m.s3;
        azione.set(sx, fondo - altaAzione, dx - altaAzione - m.s2, fondo);
        cestino.set(dx - altaAzione, azione.top, dx, azione.bottom);

        // I sette giorni, sopra il pulsante. Iniziali e non nomi: sette nomi in
        // una riga non ci stanno, e dalla seconda occhiata e' la posizione a
        // fare da nome.
        float altaGiorni = Math.max(m.bersaglio * 0.86f, h * 0.058f);
        float fondoGiorni = azione.top - m.s2;
        float passo = (dx - sx) / 7f;
        for (int i = 0; i < 7; i++) {
            areeGiorni[i].set(sx + passo * i + m.dp(2), fondoGiorni - altaGiorni,
                              sx + passo * (i + 1) - m.dp(2), fondoGiorni);
        }

        // La ghiera prende quel che resta, e resta della stessa misura nelle
        // due schede: se cambiasse fra timer e sveglie, cambiare scheda
        // sembrerebbe cambiare schermata. Lo spazio si conta sempre col posto
        // dei giorni occupato, anche nel modo timer dove i giorni non ci sono.
        float cimaGhiera = schede.bottom + m.s3;
        float fondoGhiera = areeGiorni[0].top - m.s3;
        float raggio = Math.min((dx - sx) / 2f, (fondoGhiera - cimaGhiera) / 2f);
        ghiera.posiziona((sx + dx) / 2f, (cimaGhiera + fondoGhiera) / 2f, raggio);

        // ---- la colonna di destra, in due pannelli ----
        xDestra = xDestra0;
        larghezzaDestra = w - mg - xDestra;

        float altaPronto = Math.max(m.bersaglio, h * 0.070f);
        float bandaTitolo = m.micro * 1.1f + m.s2;
        pannelloPronti.set(xDestra, mg, w - mg,
                mg + m.s3 + bandaTitolo + altaPronto * 2f + m.s2 + m.s3);
        float passoP = (larghezzaDestra - m.s3 * 2f) / 3f;
        for (int i = 0; i < PRONTI.length; i++) {
            int col = i % 3, riga = i / 3;
            float y = pannelloPronti.top + m.s3 + bandaTitolo + (altaPronto + m.s2) * riga;
            areePronti[i].set(xDestra + m.s3 + passoP * col + m.dp(3), y,
                              xDestra + m.s3 + passoP * (col + 1) - m.dp(3), y + altaPronto);
        }

        altezzaRiga = Math.max(m.bersaglio * 1.1f, h * 0.098f);
        passoRiga = altezzaRiga + m.s2;
        disponi();
    }

    /**
     * Le righe dell'elenco, che dipendono da quante cose ci sono adesso.
     *
     * <b>Il pannello dell'elenco comincia dove finisce quello sopra</b>, e non
     * a una frazione dell'altezza decisa a parte. E' tutta qui la correzione
     * della sovrapposizione: prima l'intestazione stava a
     * {@code cimaElenco - margine * 0.45f}, e {@code cimaElenco} era ricavato
     * dalle pastiglie ma la scritta veniva disegnata <i>sopra</i> quel confine,
     * dentro l'ultima fila. Adesso non c'e' piu' un confine da rispettare a
     * memoria: c'e' un pannello, e quello che gli sta dentro non puo' finire
     * fuori.
     *
     * Non alloca niente - sono {@code RectF.set} su rettangoli gia' fatti -
     * quindi rifarla a ogni cambio non costa.
     */
    private void disponi() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Nel modo sveglie le durate pronte non ci sono, e l'elenco si prende
        // anche il loro posto invece di lasciare un buco in cima.
        float cima = scheda == TIMER ? pannelloPronti.bottom + m.s3 : m.margine;
        pannelloElenco.set(xDestra, cima, w - m.margine, h - m.margine);

        float bandaTitolo = m.micro * 1.1f + m.s2;
        float dentro = pannelloElenco.top + m.s3 + bandaTitolo;
        int quante = scheda == TIMER ? MAX_TIMER : MAX_SVEGLIE;
        for (int i = 0; i < righe.length; i++) {
            float y = dentro + passoRiga * i;
            // Una riga che non ci sta nel pannello non si disegna: meglio
            // vederne quattro intere che cinque di cui l'ultima tagliata.
            if (i >= quante || y + altezzaRiga > pannelloElenco.bottom - m.s3) {
                righe[i].setEmpty(); pallini[i].setEmpty(); continue;
            }
            righe[i].set(pannelloElenco.left + m.s3, y,
                         pannelloElenco.right - m.s3, y + altezzaRiga);
            float lato = Math.max(m.bersaglio, altezzaRiga * 0.66f);
            pallini[i].set(righe[i].right - m.s2 - lato,
                           righe[i].centerY() - lato / 2f,
                           righe[i].right - m.s2,
                           righe[i].centerY() + lato / 2f);
        }
        componiTesti();
    }

    /** Tutto quello che onDraw scrive, composto qui una volta per cambiamento. */
    private void componiTesti() {
        if (orologio == null) return;

        if (scheda == TIMER) {
            int minuti = ghiera.esterno(), ore = ghiera.interno();
            if (ore > 0) {
                testoCentro = String.format(Locale.ITALIAN, "%d:%02d", ore, minuti);
                testoUnita = "ore e minuti";
            } else {
                testoCentro = String.valueOf(minuti);
                testoUnita = minuti == 1 ? "minuto" : "minuti";
            }
            testoAzione = "Avvia";
            int quanti = orologio.conti().size();
            testoCima = quanti == 0 ? "nessun timer in corso"
                      : quanti == 1 ? "un timer in corso" : quanti + " timer in corso";
        } else {
            testoCentro = String.format(Locale.ITALIAN, "%02d:%02d",
                    ghiera.esterno(), ghiera.interno() * 5);
            testoUnita = quando(scelta != null ? scelta.giorni : giorniNuova);
            testoAzione = scelta != null ? "Fatto" : "Aggiungi";
            String prossima = orologio.prossimaSveglia();
            testoCima = prossima != null ? "prossima " + prossima : "nessuna sveglia accesa";
        }

        List<Orologio.Conto> conti = orologio.conti();
        List<Orologio.Sveglia> sveglie = orologio.sveglie();
        for (int i = 0; i < testoRiga.length; i++) {
            if (scheda == TIMER && i < MAX_TIMER && i < conti.size()) {
                Orologio.Conto t = conti.get(i);
                testoRiga[i] = Orologio.scorrere(t.restano());
                testoSotto[i] = "di " + t.nome();
            } else if (scheda == SVEGLIE && i < MAX_SVEGLIE && i < sveglie.size()) {
                Orologio.Sveglia s = sveglie.get(i);
                testoRiga[i] = s.orario();
                testoSotto[i] = s.rinvio > System.currentTimeMillis() ? "rimandata di 5 minuti"
                              : s.attiva ? s.quando() : "spenta";
            } else {
                testoRiga[i] = testoSotto[i] = null;
            }
        }
    }

    /** Gli stessi giorni detti in una riga, per la scritta sotto la ghiera. */
    private static String quando(int giorni) {
        if (giorni == 0) return "una volta sola";
        if (giorni == 0x7F) return "tutti i giorni";
        if (giorni == 0x3E) return "da lunedi a venerdi";
        if (giorni == 0x41) return "sabato e domenica";
        return "nei giorni scelti";
    }

    private void anelliDellaScheda() {
        if (scheda == TIMER) ghiera.anelli(ETICHETTE_MIN60, ETICHETTE_ORE6);
        else ghiera.anelli(ETICHETTE_ORE, ETICHETTE_MIN5);
    }

    // ---- disegno ----------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (vetro == null || !vetro.vivo() || orologio == null) return;

        // La ghiera. L'arco che si consuma e' il primo timer che scade: fa
        // vedere quanto e' passato senza che nessuno legga un numero.
        float frazione = -1f;
        Orologio.Conto primo = orologio.primoConto();
        if (scheda == TIMER && primo != null && primo.durata > 0) {
            frazione = Math.max(0f, Math.min(1f, primo.restano() / (float) primo.durata));
        }
        vetro.pannello(c, pannelloGhiera, m.raggio, Tinte.OROLOGIO, Tinte.VELO_QUIETO);
        ghiera.disegna(c, testoCentro, testoUnita, frazione);

        disegnaSchede(c);

        if (scheda == SVEGLIE) {
            for (int i = 0; i < 7; i++) disegnaGiorno(c, i);
            disegnaCestino(c);
        } else {
            disegnaPannelloPronti(c);
        }
        disegnaAzione(c);
        disegnaPannelloElenco(c);
    }

    /**
     * Le due schede: un interruttore solo, diviso in due.
     *
     * La pastiglia scura sta ferma e dentro scorre quella accesa. Lo
     * scorrimento non e' un vezzo: <b>e' quello che dice che le due schede
     * sono due facce della stessa cosa</b>. Prima erano due pastiglie
     * separate che si accendevano e si spegnevano, e due pulsanti che si
     * accendono uno alla volta si leggono come due comandi, non come una
     * scelta fra due.
     */
    private void disegnaSchede(Canvas c) {
        // Il controllo a segmenti di iOS: un binario grigio piatto, e dentro il
        // segmento scelto pieno, piu' chiaro. Il colore della sezione qui non
        // serve: dice solo « quale delle due », non « sta andando ».
        float rb = Math.min(m.raggioPiccolo, schede.height() * 0.5f);
        pFondo.setColor(Tinte.RIEMPIMENTO);
        c.drawRoundRect(schede, rb, rb, pFondo);

        scorrimentoScheda.vaiA(scheda);
        if (scorrimentoScheda.passo()) postInvalidateOnAnimation();
        float dove = scorrimentoScheda.valore();
        float meta = schede.width() / 2f;
        float x = schede.left + m.dp(3) + meta * dove;
        RectF acceso = vetro.area(x, schede.top + m.dp(3),
                x + meta - m.dp(6), schede.bottom - m.dp(3));
        float rs = Math.max(0f, rb - m.dp(3));
        pFondo.setColor(Tinte.SEGMENTO_SCELTO);
        c.drawRoundRect(acceso, rs, rs, pFondo);

        for (int i = 0; i < 2; i++) {
            RectF b = areeSchede[i];
            float vicinanza = Math.max(0f, 1f - Math.abs(dove - i));
            int colore = Tinte.fondi(Tinte.TESTO_TENUE, Tinte.TESTO, vicinanza);
            if (premutoCosa == P_SCHEDA && premutoIndice == i) colore = Tinte.TESTO;

            float lato = m.icona * 0.86f;
            float largoTesto = pTitolo.measureText(NOMI_SCHEDE[i]);
            float partenza = b.centerX() - (lato + m.s1 + largoTesto) / 2f;

            pSegno.setColor(colore);
            Icone.disegna(c, i == TIMER ? Icone.TIMER : Icone.SVEGLIA_PIENA,
                    partenza + lato / 2f, b.centerY(), lato, pSegno);

            pTitolo.setColor(colore);
            pTitolo.setTextSize(m.nota);
            c.drawText(NOMI_SCHEDE[i], partenza + lato + m.s1,
                    b.centerY() - (pTitolo.descent() + pTitolo.ascent()) / 2f, pTitolo);
        }
    }

    /** Dove sta la pastiglia accesa delle due schede, in indici frazionari.
     *  Insegue invece di saltare, e il bersaglio si puo' cambiare a meta'
     *  strada senza che scatti niente. */
    private final Anima.Inseguito scorrimentoScheda = new Anima.Inseguito(TIMER, 70f);

    /** Il pulsante quando non c'e' il cestino accanto: si prende anche il suo
     *  posto invece di lasciare un buco. */
    private RectF pieno() {
        interoAzione.set(azione.left, azione.top, cestino.right, azione.bottom);
        return interoAzione;
    }

    private RectF areaAzione() {
        return scheda == SVEGLIE && scelta != null ? azione : pieno();
    }

    /** Il pulsante, con l'icona di quel che fa: si preme senza leggere, e la
     *  scritta accanto resta per la seconda volta che lo si guarda. */
    private void disegnaAzione(Canvas c) {
        boolean giu = premutoCosa == P_AZIONE;
        // Nel modo timer, con lo zero sotto l'indicatore il pulsante e' spento:
        // un « avvia » che non avvia niente e' un pulsante rotto.
        boolean vivo = scheda == SVEGLIE || ghiera.esterno() > 0 || ghiera.interno() > 0;
        RectF b = areaAzione();
        // Il pulsante pieno, nel colore della sezione: e' l'unica cosa ambra
        // della schermata, quindi si sa dove premere senza cercarlo. Spento,
        // torna un grigio piatto come gli altri comandi.
        pFondo.setColor(!vivo ? Tinte.RIEMPIMENTO
                              : giu ? Tinte.fondi(Tinte.OROLOGIO, 0xFF000000, 0.18f) : Tinte.OROLOGIO);
        c.drawRoundRect(b, b.height() * 0.5f, b.height() * 0.5f, pFondo);

        // Sull'ambra pieno il testo scuro si legge meglio del bianco.
        int colore = vivo ? Tinte.TESTO_SU_CHIARO : Tinte.SPENTO;
        int segno = scheda == TIMER ? Icone.AVVIA
                  : scelta != null ? Icone.SPUNTA : Icone.SVEGLIA_PIU;

        pNota.setTextSize(m.corpo);
        float lato = m.icona;
        float largo = pNota.measureText(testoAzione);
        float partenza = b.centerX() - (lato + m.s2 + largo) / 2f;

        pSegno.setColor(colore);
        Icone.disegna(c, segno, partenza + lato / 2f, b.centerY(), lato, pSegno);

        pNota.setColor(colore);
        c.drawText(testoAzione, partenza + lato + m.s2,
                b.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
    }

    private void disegnaCestino(Canvas c) {
        if (scelta == null) return;
        boolean giu = premutoCosa == P_CESTINO;
        pFondo.setColor(giu ? Tinte.con(Tinte.ALLARME, 0x55) : Tinte.con(Tinte.ALLARME, 0x26));
        c.drawRoundRect(cestino, cestino.height() * 0.5f, cestino.height() * 0.5f, pFondo);
        pSegno.setColor(Tinte.ALLARME);
        Icone.disegna(c, Icone.CESTINO, cestino.centerX(), cestino.centerY(),
                m.icona * 0.95f, pSegno);
    }

    private void disegnaGiorno(Canvas c, int i) {
        RectF b = areeGiorni[i];
        int giorni = scelta != null ? scelta.giorni : giorniNuova;
        boolean acceso = (giorni & (1 << i)) != 0;
        boolean giu = premutoCosa == P_GIORNO && premutoIndice == i;
        // Il giorno acceso e' pieno d'ambra, come i giorni di ripetizione
        // della Sveglia di iOS; spento, un grigio piatto.
        pFondo.setColor(acceso ? Tinte.OROLOGIO : (giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO));
        c.drawRoundRect(b, b.height() * 0.5f, b.height() * 0.5f, pFondo);
        pNota.setColor(acceso ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO_MEDIO);
        pNota.setTextSize(m.corpo);
        pNota.setTextAlign(Paint.Align.CENTER);
        c.drawText(INIZIALI[i], b.centerX(),
                b.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
        pNota.setTextAlign(Paint.Align.LEFT);
    }

    /** Il pannello delle durate pronte: il titolo, e sotto le sei pastiglie. */
    private void disegnaPannelloPronti(Canvas c) {
        vetro.pannello(c, pannelloPronti, m.raggio, Tinte.OROLOGIO, Tinte.VELO_QUIETO);
        etichetta(c, "Avvio rapido", pannelloPronti);
        for (int i = 0; i < PRONTI.length; i++) disegnaPronto(c, i);
    }

    /**
     * Il pannello di quel che e' gia' in piedi.
     *
     * Il titolo sta <b>dentro</b> il pannello, in una fascia sua: e' il punto
     * in cui si rompeva la schermata di prima, dove la stessa scritta veniva
     * piazzata a frazioni dell'altezza e finiva addosso alle pastiglie sopra.
     */
    private void disegnaPannelloElenco(Canvas c) {
        vetro.pannello(c, pannelloElenco, m.raggio, Tinte.OROLOGIO, Tinte.VELO_QUIETO);
        etichetta(c, maiuscola(testoCima), pannelloElenco);

        boolean niente = true;
        for (int i = 0; i < righe.length; i++) {
            if (righe[i].isEmpty() || testoRiga[i] == null) continue;
            niente = false;
            if (scheda == TIMER) disegnaRigaTimer(c, i);
            else disegnaRigaSveglia(c, i);
        }

        // Il vuoto lo dice gia' il titolino in cima; al centro resta solo
        // l'icona smorta di quello che ci andrebbe, perche' un pannello vuoto e
        // muto sembra una parte dell'interfaccia che non ha finito di caricare.
        if (niente) {
            pSegno.setColor(Tinte.con(Tinte.SPENTO, 0x66));
            Icone.disegna(c, scheda == TIMER ? Icone.CLESSIDRA : Icone.SVEGLIA,
                    pannelloElenco.centerX(), pannelloElenco.centerY() + m.s3,
                    m.icona * 1.9f, pSegno);
        }
    }

    /** Il titolino dentro un pannello, sempre allo stesso posto: e' il pettine
     *  che tiene allineate le due colonne. In frase normale, come le
     *  intestazioni dei widget di iOS: il maiuscolo spaziato gridava. */
    private void etichetta(Canvas c, String testo, RectF pannello) {
        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTextSize(m.nota);
        c.drawText(testo, pannello.left + m.s3, pannello.top + m.s3 + m.nota, pTitolo);
    }

    /** La prima lettera maiuscola: « prossima 7:00 » diventa un titolino.
     *  Si chiama a ogni fotogramma, ma su un testo gia' maiuscolo non crea
     *  niente; altrimenti una stringa corta, una volta al secondo. */
    private static String maiuscola(String t) {
        if (t == null || t.isEmpty() || Character.isUpperCase(t.charAt(0))) return t;
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Una durata pronta: il numero, e « min » accanto piu' piccolo. */
    private void disegnaPronto(Canvas c, int i) {
        RectF b = areePronti[i];
        boolean giu = premutoCosa == P_PRONTO && premutoIndice == i;
        pFondo.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pFondo);

        pGrande.setTextSize(m.voce);
        pNota.setTextSize(m.micro);
        String n = NOMI_PRONTI[i];
        float largo = pGrande.measureText(n) + m.s1 + pNota.measureText("min");
        float x = b.centerX() - largo / 2f;
        float base = b.centerY() - (pGrande.descent() + pGrande.ascent()) / 2f;

        pGrande.setColor(Tinte.TESTO);
        c.drawText(n, x, base, pGrande);
        pNota.setColor(Tinte.TESTO_TENUE);
        c.drawText("min", x + pGrande.measureText(n) + m.s1, base, pNota);
    }

    private void disegnaRigaTimer(Canvas c, int i) {
        RectF b = righe[i];
        Orologio.Conto t = orologio.conti().get(i);
        long restano = t.restano();
        // Sotto il minuto la riga si accende: e' il momento in cui vale la pena
        // guardarla, e su una schermata ferma il cambiamento attira l'occhio.
        boolean quasi = restano <= 60;
        pFondo.setColor(quasi ? Tinte.con(Tinte.OROLOGIO, 0x33) : Tinte.RIEMPIMENTO);
        c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pFondo);

        float lato = m.icona;
        float x = b.left + m.s3;
        pSegno.setColor(quasi ? Tinte.OROLOGIO : Tinte.TESTO_TENUE);
        Icone.disegna(c, Icone.TIMER, x + lato / 2f, b.centerY(), lato, pSegno);

        x += lato + m.s3;
        float largo = pallini[i].left - x - m.s2;
        pGrande.setColor(quasi ? Tinte.OROLOGIO : Tinte.TESTO);
        pGrande.setTextSize(m.titolo);
        c.drawText(rRighe[i].adatta(pGrande, testoRiga[i], largo, m.titolo, m.titolo * 0.66f),
                   x, b.centerY() - m.s1 * 0.2f, pGrande);

        pNota.setColor(Tinte.TESTO_TENUE);
        pNota.setTextSize(m.nota);
        c.drawText(testoSotto[i], x, b.bottom - m.s2, pNota);

        // La croce, che qui prende il posto dell'interruttore.
        RectF p = pallini[i];
        if (premutoCosa == P_PALLINO && premutoIndice == i) {
            pFondo.setColor(Tinte.RIEMPIMENTO_SCELTO);
            c.drawCircle(p.centerX(), p.centerY(), Math.min(p.width(), p.height()) * 0.5f, pFondo);
        }
        pSegno.setColor(Tinte.TESTO_MEDIO);
        Icone.disegna(c, Icone.CHIUDI, p.centerX(), p.centerY(), m.icona * 0.86f, pSegno);
    }

    /**
     * Una sveglia. Quella che la ghiera sta regolando porta un filo di luce a
     * sinistra: senza, girando la ghiera si vedrebbe cambiare una riga
     * dell'elenco e non si saprebbe perche' proprio quella.
     */
    private void disegnaRigaSveglia(Canvas c, int i) {
        RectF b = righe[i];
        Orologio.Sveglia s = orologio.sveglie().get(i);
        boolean inMano = s == scelta;
        boolean giu = premutoCosa == P_RIGA && premutoIndice == i;
        pFondo.setColor(inMano || giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pFondo);

        if (inMano) {
            pSegno.setStyle(Paint.Style.FILL);
            pSegno.setColor(Tinte.OROLOGIO);
            c.drawRoundRect(b.left, b.top + b.height() * 0.2f,
                    b.left + m.dp(3.5f), b.bottom - b.height() * 0.2f,
                    m.dp(2), m.dp(2), pSegno);
            pSegno.setStyle(Paint.Style.STROKE);
        }

        float x = b.left + m.s3;
        float largo = pallini[i].left - x - m.s2;
        pGrande.setColor(s.attiva ? Tinte.TESTO : Tinte.SPENTO);
        pGrande.setTextSize(m.titolo);
        c.drawText(rRighe[i].adatta(pGrande, testoRiga[i], largo, m.titolo, m.titolo * 0.66f),
                   x, b.centerY() - m.s1 * 0.2f, pGrande);

        pNota.setColor(s.attiva ? Tinte.TESTO_TENUE : Tinte.SPENTO);
        pNota.setTextSize(m.nota);
        c.drawText(testoSotto[i], x, b.bottom - m.s2, pNota);

        // L'interruttore: una levetta che scorre. Accendere e spegnere e' la
        // cosa che si fa piu' spesso, e non deve costare un viaggio nella
        // ghiera - ne' un momento speso a capire se il pallino vuoto vuol dire
        // spenta o « non lo so ».
        RectF p = pallini[i];
        float alta = Math.min(p.height() * 0.56f, m.icona * 1.05f);
        float larga = alta * 1.75f;
        RectF lev = vetro.area(p.centerX() - larga / 2f, p.centerY() - alta / 2f,
                               p.centerX() + larga / 2f, p.centerY() + alta / 2f);
        // L'interruttore di iOS: binario verde pieno quando e' accesa, grigio
        // quando e' spenta, e il pomello sempre bianco.
        pFondo.setColor(s.attiva ? Tinte.RADIO
                : (premutoCosa == P_PALLINO && premutoIndice == i ? Tinte.SEGMENTO_SCELTO
                                                                  : Tinte.RIEMPIMENTO_SCELTO));
        c.drawRoundRect(lev, alta * 0.5f, alta * 0.5f, pFondo);
        pSegno.setStyle(Paint.Style.FILL);
        pSegno.setColor(Tinte.TESTO);
        float r = alta * 0.42f;
        c.drawCircle(s.attiva ? lev.right - alta * 0.5f : lev.left + alta * 0.5f,
                lev.centerY(), r, pSegno);
        pSegno.setStyle(Paint.Style.STROKE);
    }

    // ---- tocco -----------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // La ghiera ha la precedenza: e' il pezzo grande al centro, e
                // chi la tocca sta girando, non premendo.
                if (ghiera.giu(x, y)) return true;
                trova(x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (ghiera.staTrascinando()) ghiera.muovi(x, y);
                return true;

            case MotionEvent.ACTION_UP: {
                if (ghiera.staTrascinando()) { ghiera.su(); return true; }
                int cosa = premutoCosa, indice = premutoIndice;
                trova(x, y);
                boolean stessoPosto = cosa == premutoCosa && indice == premutoIndice && cosa >= 0;
                premutoCosa = premutoIndice = -1;
                if (stessoPosto) agisci(cosa, indice);
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                ghiera.annulla();
                premutoCosa = premutoIndice = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void trova(float x, float y) {
        premutoCosa = premutoIndice = -1;
        if (orologio == null) return;

        for (int i = 0; i < 2; i++) {
            if (areeSchede[i].contains(x, y)) { premutoCosa = P_SCHEDA; premutoIndice = i; return; }
        }
        if (scheda == SVEGLIE) {
            if (scelta != null && cestino.contains(x, y)) { premutoCosa = P_CESTINO; return; }
            for (int i = 0; i < 7; i++) {
                if (areeGiorni[i].contains(x, y)) { premutoCosa = P_GIORNO; premutoIndice = i; return; }
            }
        } else {
            for (int i = 0; i < areePronti.length; i++) {
                if (areePronti[i].contains(x, y)) { premutoCosa = P_PRONTO; premutoIndice = i; return; }
            }
        }
        if (areaAzione().contains(x, y)) { premutoCosa = P_AZIONE; return; }

        // L'interruttore prima della riga che lo contiene, se no vince sempre
        // la riga.
        for (int i = 0; i < pallini.length; i++) {
            if (!pallini[i].isEmpty() && pallini[i].contains(x, y)) {
                premutoCosa = P_PALLINO; premutoIndice = i; return;
            }
        }
        for (int i = 0; i < righe.length; i++) {
            if (!righe[i].isEmpty() && righe[i].contains(x, y)) {
                premutoCosa = P_RIGA; premutoIndice = i; return;
            }
        }
    }

    private void agisci(int cosa, int i) {
        List<Orologio.Conto> conti = orologio.conti();
        List<Orologio.Sveglia> sveglie = orologio.sveglie();
        switch (cosa) {
            case P_SCHEDA:
                if (scheda != i) {
                    scheda = i;
                    scelta = null;
                    anelliDellaScheda();
                    // Cambiando scheda la ghiera parte da un valore che si usa
                    // davvero - cinque minuti, le sette - invece che dallo
                    // zero, che andrebbe comunque cambiato.
                    if (scheda == TIMER) ghiera.vaiA(5, 0); else ghiera.vaiA(7, 0);
                    disponi();
                }
                break;

            case P_AZIONE:
                if (scheda == TIMER) {
                    long durata = ghiera.interno() * 3600L + ghiera.esterno() * 60L;
                    if (durata > 0) orologio.avviaTimer(durata);
                } else if (scelta != null) {
                    // « Fatto »: la sveglia era gia' salvata a ogni aggancio,
                    // qui si lascia soltanto la presa.
                    scelta = null;
                } else {
                    scelta = orologio.aggiungiSveglia(ghiera.esterno(), ghiera.interno() * 5,
                            giorniNuova);
                }
                disponi();
                break;

            case P_CESTINO:
                if (scelta != null) {
                    orologio.togliSveglia(scelta);
                    scelta = null;
                    disponi();
                }
                break;

            case P_GIORNO:
                if (scelta != null) orologio.giorno(scelta, i);
                else giorniNuova ^= (1 << i);
                componiTesti();
                break;

            case P_PRONTO:
                orologio.avviaTimer(PRONTI[i] * 60L);
                disponi();
                break;

            case P_PALLINO:
                if (scheda == TIMER) {
                    if (i < conti.size()) orologio.fermaTimer(conti.get(i));
                } else if (i < sveglie.size()) {
                    Orologio.Sveglia s = sveglie.get(i);
                    orologio.accendiSveglia(s, !s.attiva);
                }
                disponi();
                break;

            case P_RIGA:
                // Toccare una sveglia la porta nella ghiera. Non c'e' un
                // « modifica »: la riga e' gia' il comando. Toccarla di nuovo
                // la lascia.
                if (scheda == SVEGLIE && i < sveglie.size()) {
                    Orologio.Sveglia s = sveglie.get(i);
                    scelta = (s == scelta) ? null : s;
                    if (scelta != null) ghiera.vaiA(scelta.ora, scelta.minuto / 5);
                    componiTesti();
                }
                break;
        }
    }
}
