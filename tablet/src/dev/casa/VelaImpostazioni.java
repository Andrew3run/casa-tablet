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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Il pannello con cui si allestisce Casa.
 *
 * <h3>Non si apre dal tablet</h3>
 *
 * Non c'e' nessun tasto che porti qui. Si apre <b>solo</b> da Gestione Home sul
 * PC, con {@code dev.casa.PAROLA --es cosa pannello}. Casa sta appesa a un muro
 * e chi le passa davanti deve trovare sei sezioni e un microfono, non una via
 * per cambiare la soglia di riconoscimento: un ingranaggio accanto al microfono
 * e' un pulsante che prima o poi qualcuno preme per curiosita', e da li' si
 * cancellano i campioni.
 *
 * Chi allestisce ha il PC. E siccome i campioni di partenza arrivano dentro
 * l'app (vedi {@link Campioni#portaIBase}), un tablet appena installato
 * riconosce gia' la parola senza che nessuno apra niente.
 *
 * <h3>Due facce, secondo chi ascolta</h3>
 *
 * Da quando il rilevatore e' la {@link Rete} allenata su duemila voci, <b>i
 * campioni non servono piu'</b>: nessuno deve registrare niente, e registrare
 * altri "Hey Home" non cambierebbe una virgola di quello che Marvin sente.
 * Quindi con la rete in funzione il pannello non li mostra affatto - un tasto
 * che non serve a niente e' un tasto che qualcuno prima o poi preme - e al
 * loro posto mette la <b>prova</b>, che e' l'unica cosa che si viene a fare
 * qui: dire la parola e guardare il numero.
 *
 * Le due manopole diventano la soglia e le <b>finestre di fila</b>, che sono
 * quelle che decidono se Casa si sveglia troppo o troppo poco.
 *
 * Il pannello dei campioni non e' stato cancellato: ricompare intero se il
 * modello sparisse dagli assets e Casa tornasse al confronto a impronte. Il
 * codice c'e' e non lo si scopre rotto il giorno in cui serve.
 *
 * <h3>L'impaginazione: tre pannelli e una striscia</h3>
 *
 * <pre>
 *   Impostazioni                                            [X]
 *   +----------------+  +-----------------------------+
 *   |  LA PAROLA     |  |  I CAMPIONI                 |
 *   |                |  +-----------------------------+
 *   |                |  +-----------------------------+
 *   |                |  |  IL TABLET                  |
 *   +----------------+  +-----------------------------+
 *   +--------------------------------------------------+
 *   |  quello che sta succedendo, su tutta la larghezza |
 *   +--------------------------------------------------+
 * </pre>
 *
 * <b>La striscia in fondo e' larga quanto la vela, e non e' estetica.</b> Prima
 * il risultato della prova stava in coda alla colonna di sinistra, sotto gli
 * ultimi due tasti: i conti dicevano che la prima riga cadeva <i>esattamente</i>
 * sul bordo inferiore del pannello e le successive fuori dallo schermo. Il
 * punteggio veniva calcolato e non si vedeva - cioe' la prova sembrava non
 * funzionare, mentre funzionava benissimo. Le cose che si leggono <b>mentre</b>
 * si fa qualcos'altro vanno dove c'e' spazio, non dove avanza.
 *
 * Per la stessa ragione il percorso della cartella non sta piu' sotto il
 * titolo, dove finiva mezzo coperto dai pannelli: e' roba tecnica, e sta in
 * fondo al riquadro « IL TABLET » con il resto della roba tecnica.
 */
public class VelaImpostazioni extends View implements Telaio.Velata {

    /** L'unica cosa che il pannello chiede a chi lo ha aperto. */
    public interface Uscita { void suChiudi(); }

    // ---- gli stati del pannello -------------------------------------------

    private static final int NORMALE = 0, CONTO = 1, REGISTRA = 2, LAVORA = 3;

    /** Quanto dura una registrazione della parola, e una di rumore. */
    private static final int DURATA_PAROLA = 1600, DURATA_RUMORE = 6000;

    /** Gli estremi della soglia. Sotto 1,5 non aggancerebbe nemmeno la
     *  registrazione stessa; sopra 6 aggancia qualunque cosa. */
    private static final float SOGLIA_MIN = 1.5f, SOGLIA_MAX = 6.0f;

    /** Quanti esiti si ricordano nella striscia della prova. Otto perche' e'
     *  quante volte si dice una parola di fila prima di stufarsi, ed e'
     *  abbastanza per vedere se aggancia sempre o una volta su tre. */
    private static final int QUANTI_ESITI = 8;

    /** Dopo quanto la prova torna a dire « di' Hey Home » invece del numero. */
    private static final long PUNTEGGIO_VIVE_MS = 6000;

    private final Misure m;
    private final Campioni campioni;
    private final Risveglio risveglio;
    private final Uscita uscita;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Vetro vetro;
    private Campioni.Stato stato;
    private Runnable suChiusa;

    // ---- disegno -----------------------------------------------------------

    private final Paint pVelo      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitolo    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEtichetta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCorpo     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTasto     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIcona     = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** I fondi piatti dei comandi, alla iOS. */
    private final Paint pPieno     = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF pParola   = new RectF();
    private final RectF pElenco   = new RectF();
    private final RectF pTablet   = new RectF();
    private final RectF striscia  = new RectF();
    private final RectF rChiudi   = new RectF();
    private final RectF rInterruttore = new RectF();
    private final RectF rLivello  = new RectF();
    private final RectF rSoglia   = new RectF();
    private final RectF rEco      = new RectF();
    private final RectF[] tasti   = new RectF[4];
    private final RectF[] linguette = new RectF[2];
    /** Meno e piu' delle finestre di fila. Ci sono solo con la rete. */
    private final RectF rMeno = new RectF(), rPiu = new RectF();
    private final RectF appoggio  = new RectF();

    private final Testo.Riga rVoce     = new Testo.Riga();
    private final Testo.Riga rCartella = new Testo.Riga();
    private final Testo.Blocco bDetto  = new Testo.Blocco();
    private final Testo.Blocco bVuoto  = new Testo.Blocco();

    private static final String[] NOME_TASTO = {
        "Registra « Hey Home »", "Registra rumore", "Tara", "Prova"
    };
    private static final int T_PAROLA = 0, T_RUMORE = 1, T_TARA = 2, T_PROVA = 3;

    // ---- stato dell'interfaccia -------------------------------------------

    private int fase = NORMALE;
    private int tipoInElenco = Campioni.SI;
    private int premuto = -1;
    private boolean premutoChiudi, premutoInterruttore, premutoEco;
    private boolean premutoMeno, premutoPiu;
    private boolean trascinaSoglia;
    private int premutoCestino = -1;

    private float livello, livelloPicco;
    private boolean voce;

    private int contoRimasto;
    private int registraTipo;
    private int registraRimasti;
    private String messaggio;
    private String lavoro = "";

    /** L'ultimo punteggio, e gli ultimi otto esiti. */
    private float ultimoPunteggio = -1f, ultimaSoglia;
    /** La distanza dalle registrazioni di casa: il secondo stadio. Vale
     *  {@link ParolaChiave#LONTANO} se la rete aveva gia' detto di no. */
    private float ultimaDistanza = ParolaChiave.LONTANO;
    private int ultimeDiFila, massimeDiFila;
    private boolean ultimoPreso;
    private long quandoPunteggio;
    private final float[] esiti = new float[QUANTI_ESITI];
    private final boolean[] esitiPresi = new boolean[QUANTI_ESITI];
    private int quantiEsiti;

    private final List<Voce> elenco = new ArrayList<Voce>();
    private float scorrimento, scorrimentoMax;
    private float ultimaY;
    private boolean staScorrendo;

    /** Una riga dell'elenco. */
    private static final class Voce {
        final File file;
        final String nome, durata;
        final boolean diSerie;
        final Testo.Riga riga = new Testo.Riga();
        Voce(File f) {
            file = f;
            String n = f.getName();
            if (n.endsWith(".wav")) n = n.substring(0, n.length() - 4);
            // I campioni arrivati con l'app si chiamano si-base-1: si
            // riconoscono, cosi' chi guarda l'elenco sa quali sono i suoi.
            diSerie = n.contains("-base-");
            int taglio = n.indexOf('-');
            nome = taglio > 0 ? n.substring(taglio + 1) : n;
            durata = String.format(Locale.ITALIAN, "%.1f s", Onda.durataMs(f) / 1000f);
        }
    }

    public VelaImpostazioni(Context c, Misure misure, Vetro v,
                            Campioni campioni, Risveglio risveglio, Uscita uscita) {
        super(c);
        this.m = misure;
        this.vetro = v;
        this.campioni = campioni;
        this.risveglio = risveglio;
        this.uscita = uscita;
        this.stato = risveglio.stato();

        setClickable(true);
        for (int i = 0; i < tasti.length; i++) tasti[i] = new RectF();
        for (int i = 0; i < linguette.length; i++) linguette[i] = new RectF();

        // Quasi pieno: dietro non si deve leggere niente. A 0xF0 le schede
        // della Home si intravedevano ancora, e due testi sovrapposti si
        // leggono peggio di uno solo.
        pVelo.setColor(0xF8000000);
        pTitolo.setColor(Tinte.TESTO);
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pEtichetta.setColor(Tinte.TESTO_TENUE);
        pEtichetta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pCorpo.setColor(Tinte.TESTO);
        pCorpo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pNota.setColor(Tinte.TESTO_TENUE);
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pTasto.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pTasto.setTextAlign(Paint.Align.CENTER);

        rileggiElenco();
    }

    @Override public void setVetro(Vetro v) { vetro = v; invalidate(); }

    @Override public boolean hasOverlappingRendering() { return false; }

    public void setSuChiusa(Runnable r) { suChiusa = r; }

    // ---- entrata e uscita --------------------------------------------------

    /**
     * Apre il microfono e ferma il confronto.
     *
     * Acceso perche' serve alla barra del livello e alle registrazioni; fermo
     * perche' mentre si allestisce non deve agganciare: si dice « Hey Home »
     * dieci volte di fila per registrarlo, e ognuna sveglierebbe il
     * riconoscitore.
     */
    public void suEntrata() {
        risveglio.setMostraLivello(true);
        risveglio.accendiPerRegistrare();
        risveglio.setProva(false);
        invalidate();
    }

    /**
     * Rimette le cose come le vuole lo stato salvato.
     *
     * Arriva dal telaio, comunque il pannello sia uscito di scena. Qui dentro
     * si restituisce il microfono, che e' l'unica cosa che il pannello si
     * prende.
     */
    @Override public void suVelaTolta() {
        fase = NORMALE;
        ui.removeCallbacksAndMessages(null);
        risveglio.setMostraLivello(false);
        risveglio.setProva(false);
        risveglio.orecchio().annullaRegistrazione();
        risveglio.applica(stato);
        if (suChiusa != null) { suChiusa.run(); suChiusa = null; }
    }

    // ---- quello che arriva dal risveglio -----------------------------------

    public void setLivello(float l, boolean v) {
        livello = l;
        voce = v;
        if (l > livelloPicco) livelloPicco = l;
        else livelloPicco = Math.max(l, livelloPicco * 0.94f);
        // Ridisegna solo la barra: e' l'unica cosa che si muove cinquanta volte
        // al secondo, e rifare tutta la vela - tre pannelli di vetro e un
        // elenco - per una barra alta dieci pixel farebbe scattare tutto.
        invalidate((int) rLivello.left, (int) rLivello.top,
                   (int) Math.ceil(rLivello.right), (int) Math.ceil(rLivello.bottom));
    }

    public void setPunteggio(float punteggio, float soglia, int diFila,
                             float distanza, boolean preso) {
        ultimaDistanza = distanza;
        ultimeDiFila = diFila;
        if (diFila > massimeDiFila) massimeDiFila = diFila;
        ultimoPunteggio = punteggio;
        ultimaSoglia = soglia;
        ultimoPreso = preso;
        quandoPunteggio = System.currentTimeMillis();

        // Gli esiti scorrono: il piu' recente entra in fondo.
        if (quantiEsiti < QUANTI_ESITI) quantiEsiti++;
        else {
            System.arraycopy(esiti, 1, esiti, 0, QUANTI_ESITI - 1);
            System.arraycopy(esitiPresi, 1, esitiPresi, 0, QUANTI_ESITI - 1);
        }
        esiti[quantiEsiti - 1] = punteggio;
        esitiPresi[quantiEsiti - 1] = preso;
        invalidate();
    }

    // ---- misure ------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        float mg = m.margine;

        pTitolo.setTextSize(m.titolo);
        pEtichetta.setTextSize(m.micro);
        pCorpo.setTextSize(m.corpo);
        pNota.setTextSize(m.nota);
        pTasto.setTextSize(m.corpo);

        // La fascia del titolo e' alta quanto il titolo, non una frazione
        // indovinata: con h*0.115 il sottotitolo cadeva sotto il bordo dei
        // pannelli e si leggeva a meta'.
        float cima = mg + m.titolo + m.s3;
        rChiudi.set(w - mg - m.bersaglio, mg * 0.6f, w - mg, mg * 0.6f + m.bersaglio);

        // La striscia in fondo: un numero grande e una riga sotto.
        float altoStriscia = m.s3 + m.titolo + m.s1 + m.nota + m.s2;
        striscia.set(mg, h - mg - altoStriscia, w - mg, h - mg);

        float fondoPannelli = striscia.top - m.s2;
        float taglio = w * 0.42f;
        pParola.set(mg, cima, taglio, fondoPannelli);
        pElenco.set(taglio + m.s2, cima, w - mg, cima + (fondoPannelli - cima) * 0.56f);
        pTablet.set(taglio + m.s2, pElenco.bottom + m.s2, w - mg, fondoPannelli);

        // ---- la colonna di sinistra, dall'alto ----
        float x = pParola.left + m.s4, largo = pParola.width() - m.s4 * 2f;
        float y = pParola.top + m.s4 + m.voce + m.s3;

        float altoInterruttore = Math.max(m.bersaglio * 1.25f, m.corpo * 2.4f);
        rInterruttore.set(x, y, x + largo, y + altoInterruttore);
        y = rInterruttore.bottom + m.s3;

        rLivello.set(x, y, x + largo, y + m.s3);
        y = rLivello.bottom + m.s4;

        rSoglia.set(x, y, x + largo, y + m.bersaglio);
        y = rSoglia.bottom + m.s2;

        // Le finestre di fila: solo con la rete, e solo perche' e' la manopola
        // che conta piu' della soglia.
        if (conLaRete()) {
            float lato = Math.max(m.bersaglio, m.corpo * 2f);
            rMeno.set(x + largo - lato * 2f - m.s2, y, x + largo - lato - m.s2, y + lato);
            rPiu.set(x + largo - lato, y, x + largo, y + lato);
            y += lato + m.s3;
        } else {
            rMeno.setEmpty();
            rPiu.setEmpty();
        }

        rEco.set(x, y, x + largo, y + Math.max(m.bersaglio * 0.9f, m.corpo * 2f));
        y = rEco.bottom + m.s3;

        float altoTasto = Math.max(m.bersaglio * 1.15f, m.corpo * 2.2f);
        if (conLaRete()) {
            // Un tasto solo: registrare e tarare erano roba dei campioni.
            for (int i = 0; i < tasti.length; i++) tasti[i].setEmpty();
            tasti[T_PROVA].set(x, y, x + largo, y + altoTasto * 1.15f);
        } else {
            tasti[T_PAROLA].set(x, y, x + largo, y + altoTasto);
            y += altoTasto + m.s2;
            tasti[T_RUMORE].set(x, y, x + largo, y + altoTasto);
            y += altoTasto + m.s2;
            float mezzo = (largo - m.s2) / 2f;
            tasti[T_TARA].set(x, y, x + mezzo, y + altoTasto);
            tasti[T_PROVA].set(x + mezzo + m.s2, y, x + largo, y + altoTasto);
        }

        // ---- le due linguette dell'elenco ----
        float lx = pElenco.left + m.s4, ly = pElenco.top + m.s3;
        float lw = (pElenco.width() - m.s4 * 2f - m.s2) / 2f;
        float lh = Math.max(m.bersaglio * 0.9f, m.corpo * 1.9f);
        linguette[0].set(lx, ly, lx + lw, ly + lh);
        linguette[1].set(lx + lw + m.s2, ly, lx + lw * 2f + m.s2, ly + lh);

        aggiornaScorrimentoMax();
    }

    /** Comanda la rete allenata, o il confronto a impronte? Il pannello cambia
     *  faccia di conseguenza. */
    private boolean conLaRete() { return risveglio.conLaRete(); }

    private float altezzaRiga() { return Math.max(m.bersaglio, m.corpo * 2.1f); }

    private float cimaElenco() { return linguette[0].bottom + m.s3; }

    private void aggiornaScorrimentoMax() {
        float visibile = pElenco.bottom - m.s3 - cimaElenco();
        float totale = elenco.size() * (altezzaRiga() + m.s1);
        scorrimentoMax = Math.max(0f, totale - visibile);
        if (scorrimento > scorrimentoMax) scorrimento = scorrimentoMax;
        if (scorrimento < 0f) scorrimento = 0f;
    }

    // ---- disegno -----------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), pVelo);
        if (vetro == null || !vetro.vivo()) return;

        pTitolo.setTextSize(m.titolo);
        pTitolo.setColor(Tinte.TESTO);
        c.drawText("Impostazioni", m.margine, m.margine + m.titolo * 0.82f, pTitolo);

        chiudi(c);
        colonnaParola(c);
        colonnaElenco(c);
        colonnaTablet(c);
        strisciaInFondo(c);

        if (fase != NORMALE) sopraTutto(c);
    }

    private void chiudi(Canvas c) {
        // Un cerchio grigio con la x, come la chiusura di un foglio di iOS.
        pPieno.setColor(premutoChiudi ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawCircle(rChiudi.centerX(), rChiudi.centerY(),
                Math.min(rChiudi.width(), rChiudi.height()) * 0.46f, pPieno);
        pIcona.setColor(Tinte.TESTO_MEDIO);
        Icone.disegna(c, Icone.CHIUDI, rChiudi.centerX(), rChiudi.centerY(),
                rChiudi.height() * 0.46f, pIcona);
    }

    private void colonnaParola(Canvas c) {
        vetro.pannello(c, pParola, m.raggio, Tinte.HOME, Tinte.VELO_QUIETO);

        float x = pParola.left + m.s4, largo = pParola.width() - m.s4 * 2f;

        pCorpo.setTextSize(m.voce);
        pCorpo.setColor(risveglio.acceso() && stato.accesa ? Tinte.RADIO : Tinte.TESTO);
        c.drawText("« " + risveglio.frase() + " »", x, pParola.top + m.s4 + m.voce * 0.82f, pCorpo);

        // L'interruttore.
        boolean pronta = risveglio.pronta();
        int coloreOn = pronta ? Tinte.RADIO : Tinte.SPENTO;
        pPieno.setColor(premutoInterruttore ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(rInterruttore, m.raggioPiccolo, m.raggioPiccolo, pPieno);
        // <b>Dice cosa e', non cosa fa.</b> Prima leggeva "ascolta sempre" /
        // "solo col tocco": due descrizioni, nessuna delle quali si legge come
        // un interruttore. Il risultato e' che si puo' registrare, tarare e
        // provare - e la prova aggancia, perche' apre il microfono per conto
        // suo - restando convinti che la parola sia accesa mentre e' spenta.
        // Adesso c'e' scritto il nome della cosa e il suo stato.
        pCorpo.setTextSize(m.corpo);
        pCorpo.setColor(stato.accesa ? Tinte.TESTO : Tinte.TESTO_MEDIO);
        String testoInt = !pronta ? "Nessun campione: registra" : "Ascolto continuo";
        c.drawText(testoInt, rInterruttore.left + m.s3,
                rInterruttore.centerY() - (pCorpo.descent() + pCorpo.ascent()) / 2f, pCorpo);
        // L'interruttore di iOS: pista verde accesa, grigia spenta, pomello
        // bianco. Lo stato lo dice lui, non una scritta in maiuscolo.
        float altaPista = rInterruttore.height() * 0.56f, largaPista = altaPista * 1.65f;
        appoggio.set(rInterruttore.right - m.s3 - largaPista, rInterruttore.centerY() - altaPista / 2f,
                     rInterruttore.right - m.s3, rInterruttore.centerY() + altaPista / 2f);
        pPieno.setColor(stato.accesa ? coloreOn : Tinte.RIEMPIMENTO_SCELTO);
        c.drawRoundRect(appoggio, altaPista / 2f, altaPista / 2f, pPieno);
        pPieno.setColor(pronta ? Tinte.TESTO : Tinte.TESTO_TENUE);
        c.drawCircle(stato.accesa ? appoggio.right - altaPista / 2f : appoggio.left + altaPista / 2f,
                appoggio.centerY(), altaPista * 0.43f, pPieno);

        barraLivello(c);

        // La soglia. <b>I due rilevatori la contano al contrario</b>: per il
        // confronto a impronte e' una distanza (piu' bassa = piu' esigente),
        // per la rete e' un "quanto somiglia" da zero a uno (piu' alta = piu'
        // esigente). Scriverlo sbagliato vorrebbe dire far girare la manopola
        // dalla parte opposta a quella che si voleva.
        pEtichetta.setTextSize(m.micro);
        pEtichetta.setColor(Tinte.TESTO_TENUE);
        if (risveglio.conLaRete()) {
            c.drawText("Soglia " + ParolaChiave.arrotonda(stato.sogliaRete)
                            + "   (piu' alta = piu' esigente)   "
                            + stato.difila + " finestre di fila",
                    x, rSoglia.top - m.s1, pEtichetta);
            cursore(c, rSoglia, stato.sogliaRete);
        } else {
            c.drawText("Soglia " + ParolaChiave.arrotonda(stato.soglia)
                            + "   (piu' bassa = piu' esigente)",
                    x, rSoglia.top - m.s1, pEtichetta);
            cursore(c, rSoglia, (stato.soglia - SOGLIA_MIN) / (SOGLIA_MAX - SOGLIA_MIN));
        }

        // La cancellazione d'eco.
        boolean ecoCe = Orecchio.ecoDisponibile();
        pPieno.setColor(premutoEco ? Tinte.RIEMPIMENTO_SCELTO
                : stato.eco ? Tinte.con(Tinte.APP, 0x40) : Tinte.RIEMPIMENTO);
        c.drawRoundRect(rEco, m.raggioPiccolo, m.raggioPiccolo, pPieno);
        pNota.setTextSize(m.nota);
        pNota.setColor(ecoCe ? Tinte.TESTO_MEDIO : Tinte.SPENTO);
        c.drawText(ecoCe ? (stato.eco ? "cancellazione d'eco accesa"
                                      : "cancellazione d'eco spenta")
                         : "cancellazione d'eco non disponibile",
                rEco.left + m.s3, rEco.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);

        // Le finestre di fila, con meno e piu'.
        if (conLaRete()) {
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.TESTO_MEDIO);
            c.drawText("quante finestre di fila", x,
                    rMeno.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
            manopola(c, rMeno, "-", premutoMeno, stato.difila > 1);
            manopola(c, rPiu, "+", premutoPiu, stato.difila < 8);
        }

        for (int i = 0; i < tasti.length; i++) {
            if (tasti[i].isEmpty()) continue;
            boolean acceso = i == T_PROVA && risveglio.inProva();
            boolean spento = (i == T_TARA || i == T_PROVA) && !pronta;
            String nome = NOME_TASTO[i];
            if (i == T_PROVA) {
                nome = acceso ? "Prova accesa: di' « " + risveglio.frase() + " »"
                              : (conLaRete() ? "Prova: di' « " + risveglio.frase() + " »"
                                             : NOME_TASTO[i]);
            }
            tasto(c, tasti[i], nome,
                  i == T_PAROLA ? Tinte.HOME : (acceso ? Tinte.OROLOGIO : Tinte.TESTO_TENUE),
                  premuto == i, i == T_PAROLA || acceso, spento);
        }
    }

    private void barraLivello(Canvas c) {
        appoggio.set(rLivello);
        pPieno.setColor(Tinte.RIEMPIMENTO);
        c.drawRoundRect(appoggio, appoggio.height() / 2f, appoggio.height() / 2f, pPieno);
        float quanto = Math.min(1f, livello);
        if (quanto > 0.01f) {
            appoggio.set(rLivello.left, rLivello.top,
                    rLivello.left + rLivello.width() * quanto, rLivello.bottom);
            pIcona.setColor(voce ? Tinte.RADIO : Tinte.TESTO_TENUE);
            c.drawRoundRect(appoggio, appoggio.height() / 2f, appoggio.height() / 2f, pIcona);
        }
        // Il picco che scende piano: dice quanto forte si e' parlato un attimo
        // fa, che la barra da sola non fa in tempo a mostrare.
        float px = rLivello.left + rLivello.width() * Math.min(1f, livelloPicco);
        pIcona.setColor(Tinte.con(Tinte.TESTO, 0x88));
        c.drawRect(px - 2f, rLivello.top, px, rLivello.bottom, pIcona);
    }

    private void cursore(Canvas c, RectF area, float quanto) {
        float cy = area.centerY();
        float h = Math.max(4f, area.height() * 0.16f);
        appoggio.set(area.left, cy - h / 2f, area.right, cy + h / 2f);
        pPieno.setColor(Tinte.RIEMPIMENTO_SCELTO);
        c.drawRoundRect(appoggio, h / 2f, h / 2f, pPieno);
        float px = area.left + area.width() * Math.max(0f, Math.min(1f, quanto));
        appoggio.set(area.left, cy - h / 2f, px, cy + h / 2f);
        pIcona.setColor(Tinte.HOME);
        c.drawRoundRect(appoggio, h / 2f, h / 2f, pIcona);
        pIcona.setColor(Tinte.TESTO);
        c.drawCircle(px, cy, area.height() * 0.30f, pIcona);
    }

    /** Un tondo con dentro un segno: il meno e il piu' delle finestre. */
    private void manopola(Canvas c, RectF b, String segno, boolean giu, boolean vivo) {
        pPieno.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawCircle(b.centerX(), b.centerY(), Math.min(b.width(), b.height()) / 2f, pPieno);
        pTasto.setTextSize(m.voce);
        pTasto.setColor(vivo ? Tinte.TESTO : Tinte.SPENTO);
        c.drawText(segno, b.centerX(),
                b.centerY() - (pTasto.descent() + pTasto.ascent()) / 2f, pTasto);
    }

    private void tasto(Canvas c, RectF b, String testo, int colore,
                       boolean giu, boolean pieno, boolean spento) {
        // Capsule piatte alla iOS: quella principale piena del suo colore,
        // le altre grigie.
        if (spento) {
            pPieno.setColor(Tinte.con(Tinte.TESTO, 0x0C));
        } else if (pieno) {
            pPieno.setColor(giu ? Tinte.con(colore, 0xB0) : colore);
        } else {
            pPieno.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        }
        c.drawRoundRect(b, b.height() / 2f, b.height() / 2f, pPieno);
        pTasto.setTextSize(m.corpo);
        pTasto.setColor(spento ? Tinte.SPENTO : (pieno ? Tinte.TESTO : Tinte.TESTO));
        float largo = b.width() - m.s3 * 2f;
        while (pTasto.measureText(testo) > largo && pTasto.getTextSize() > m.corpo * 0.62f) {
            pTasto.setTextSize(pTasto.getTextSize() * 0.93f);
        }
        c.drawText(testo, b.centerX(),
                b.centerY() - (pTasto.descent() + pTasto.ascent()) / 2f, pTasto);
    }

    private void colonnaElenco(Canvas c) {
        if (conLaRete()) { colonnaProva(c); return; }
        vetro.pannello(c, pElenco, m.raggio, Tinte.HOME, Tinte.VELO_QUIETO);

        // Le due linguette sono un controllo a segmenti di iOS.
        appoggio.set(Math.min(linguette[0].left, linguette[1].left) - m.s1,
                     linguette[0].top - m.s1,
                     Math.max(linguette[0].right, linguette[1].right) + m.s1,
                     linguette[0].bottom + m.s1);
        pPieno.setColor(Tinte.RIEMPIMENTO);
        c.drawRoundRect(appoggio, m.raggioPiccolo + m.s1, m.raggioPiccolo + m.s1, pPieno);
        for (int i = 0; i < 2; i++) {
            boolean qui = tipoInElenco == i;
            if (qui) {
                pPieno.setColor(Tinte.SEGMENTO_SCELTO);
                c.drawRoundRect(linguette[i], m.raggioPiccolo, m.raggioPiccolo, pPieno);
            }
            pTasto.setTextSize(m.nota);
            pTasto.setColor(qui ? Tinte.TESTO : Tinte.TESTO_TENUE);
            String t = (i == Campioni.SI ? "« Hey Home »  " : "rumore e altro  ")
                     + campioni.quanti(i);
            c.drawText(t, linguette[i].centerX(),
                    linguette[i].centerY() - (pTasto.descent() + pTasto.ascent()) / 2f, pTasto);
        }

        float cima = cimaElenco(), fondo = pElenco.bottom - m.s3;
        if (elenco.isEmpty()) {
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.SPENTO);
            String vuoto = tipoInElenco == Campioni.SI
                    ? "Nessuna registrazione. Servono almeno tre « Hey Home »."
                    : "Nessun rumore registrato. Senza, la soglia non si puo' verificare.";
            String[] righe = bVuoto.in(pNota, vuoto, pElenco.width() - m.s4 * 2f, m.nota, 3);
            float yy = cima + m.s4;
            for (String riga : righe) {
                c.drawText(riga, pElenco.left + m.s4, yy, pNota);
                yy += m.nota * 1.4f;
            }
            return;
        }

        int salvato = c.save();
        c.clipRect(pElenco.left, cima, pElenco.right, fondo);

        float h = altezzaRiga(), passo = h + m.s1;
        int primo = Math.max(0, (int) (scorrimento / passo));
        int ultimo = Math.min(elenco.size() - 1, (int) ((scorrimento + (fondo - cima)) / passo));

        for (int i = primo; i <= ultimo; i++) {
            Voce v = elenco.get(i);
            float y = cima + i * passo - scorrimento;
            appoggio.set(pElenco.left + m.s4, y, pElenco.right - m.s4, y + h);
            // Righe d'elenco con un capello fra l'una e l'altra, senza fondo.
            if (i > 0) {
                pPieno.setColor(Tinte.SEPARATORE);
                c.drawRect(appoggio.left + m.s3, y - m.s1 / 2f, appoggio.right, y - m.s1 / 2f + 1f, pPieno);
            }

            float xCestino = appoggio.right - m.s3 - m.bersaglio / 2f;
            pNota.setTextSize(m.nota);
            float largoDurata = pNota.measureText("0,0 s") + m.s3;
            float spazioNome = xCestino - appoggio.left - m.s3 * 2f - largoDurata;

            pCorpo.setTextSize(m.corpo);
            pCorpo.setColor(v.diSerie ? Tinte.TESTO_MEDIO : Tinte.TESTO);
            c.drawText(v.riga.in(pCorpo, v.nome, spazioNome, m.corpo),
                    appoggio.left + m.s3,
                    appoggio.centerY() - (pCorpo.descent() + pCorpo.ascent()) / 2f, pCorpo);

            pNota.setColor(Tinte.TESTO_TENUE);
            c.drawText(v.durata, appoggio.left + m.s3 + spazioNome + m.s3,
                    appoggio.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);

            pIcona.setColor(premutoCestino == i ? Tinte.ALLARME : Tinte.TESTO_TENUE);
            Icone.disegna(c, Icone.CESTINO, xCestino, appoggio.centerY(), m.icona, pIcona);
        }
        c.restoreToCount(salvato);
    }

    /** Quante righe stanno nel riquadro « IL TABLET ». */
    private static final int RIGHE_TABLET = 4;

    /**
     * Il riquadro tecnico.
     *
     * <b>Le righe si distribuiscono nello spazio che resta, non si impilano
     * sperando che basti.</b> Prima ognuna stava a un passo fisso dalla
     * precedente e il percorso si scriveva a una distanza fissa dal fondo: i
     * due conti si incontravano a meta' strada, e l'ultima riga usciva scritta
     * <i>sopra</i> il percorso. Con il passo calcolato dallo spazio
     * disponibile la cosa non si ripresenta se domani si aggiunge una riga o
     * se cambiano i corpi del testo.
     */
    /**
     * Il riquadro grande, quando comanda la rete: la prova.
     *
     * E' l'unica cosa che si viene a fare in questo pannello da quando i
     * campioni non servono piu': si dice la parola e si guarda il numero. Per
     * questo sta nel riquadro grande e non in un angolo - la lezione di quando
     * il punteggio finiva sul bordo inferiore e non si vedeva affatto.
     */
    private void colonnaProva(Canvas c) {
        boolean inProva = risveglio.inProva();
        boolean fresco = System.currentTimeMillis() - quandoPunteggio < PUNTEGGIO_VIVE_MS;
        vetro.pannello(c, pElenco, m.raggio,
                inProva ? (fresco && ultimoPreso ? Tinte.RADIO : Tinte.OROLOGIO) : Tinte.HOME,
                inProva ? 0x1C : Tinte.VELO_QUIETO);

        float x = pElenco.left + m.s4;
        float largo = pElenco.width() - m.s4 * 2f;

        pEtichetta.setTextSize(m.nota);
        pEtichetta.setColor(Tinte.TESTO_TENUE);
        c.drawText("Prova", x, pElenco.top + m.s3 + m.micro, pEtichetta);

        if (!inProva) {
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.SPENTO);
            String[] righe = bVuoto.in(pNota,
                    "Premi Prova e di' « " + risveglio.frase() + " » da dove lo dirai "
                    + "davvero. Qui compare quanto somiglia e quante finestre di fila "
                    + "sono passate: se il numero e' alto ma le finestre restano poche, "
                    + "e' quella la manopola da girare, non la soglia.",
                    largo, m.nota, 5);
            float yy = pElenco.top + m.s3 + m.micro + m.s4 + m.nota;
            for (String riga : righe) { c.drawText(riga, x, yy, pNota); yy += m.nota * 1.4f; }
            return;
        }

        // Il numero, grandissimo: e' la risposta alla domanda "mi ha sentito?".
        float cy = pElenco.top + pElenco.height() * 0.42f;
        if (!fresco || ultimoPunteggio < 0f) {
            pCorpo.setTextSize(m.voce);
            pCorpo.setColor(Tinte.TESTO_TENUE);
            c.drawText("in ascolto: parla", x, cy, pCorpo);
        } else {
            int colore = ultimoPreso ? Tinte.RADIO : Tinte.ALLARME;
            pTitolo.setTextSize(m.cifra * 0.55f);
            pTitolo.setColor(colore);
            String numero = ParolaChiave.arrotonda(ultimoPunteggio);
            c.drawText(numero, x, cy, pTitolo);

            pCorpo.setTextSize(m.voce);
            pCorpo.setColor(colore);
            c.drawText(ultimoPreso ? "aggancia" : "non basta",
                    x + pTitolo.measureText(numero) + m.s4, cy - m.nota * 0.2f, pCorpo);

            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.TESTO_TENUE);
            // I due stadi sulla stessa riga: chi guarda vuole sapere quale
            // dei due ha detto di no, non il valore di uno solo.
            String nota = "rete >= " + ParolaChiave.arrotonda(ultimaSoglia);
            if (risveglio.conConferma()) {
                nota += "   -   impronte "
                      + (ultimaDistanza >= ParolaChiave.LONTANO
                         ? "non chieste"
                         : ParolaChiave.arrotonda(ultimaDistanza)
                           + " <= " + ParolaChiave.arrotonda(stato.soglia));
            } else {
                nota += "   -   " + ultimeDiFila + " di fila su " + stato.difila;
            }
            c.drawText(nota, x + pTitolo.measureText(numero) + m.s4,
                    cy + m.nota * 1.3f, pNota);
        }

        // La barra: dove sta il punteggio rispetto alla soglia. Un numero da
        // solo non dice se si e' andati vicini.
        float yBarra = pElenco.top + pElenco.height() * 0.60f;
        appoggio.set(x, yBarra, x + largo, yBarra + m.s3);
        pPieno.setColor(Tinte.RIEMPIMENTO);
        c.drawRoundRect(appoggio, appoggio.height() / 2f, appoggio.height() / 2f, pPieno);
        if (fresco && ultimoPunteggio >= 0f && ultimoPunteggio < ParolaChiave.LONTANO) {
            float q = Math.max(0f, Math.min(1f, ultimoPunteggio));
            appoggio.set(x, yBarra, x + largo * q, yBarra + m.s3);
            pIcona.setColor(ultimoPreso ? Tinte.RADIO : Tinte.ALLARME);
            c.drawRoundRect(appoggio, m.s3 / 2f, m.s3 / 2f, pIcona);
        }
        // Il segno della soglia, sulla barra.
        float xs = x + largo * Math.max(0f, Math.min(1f, ultimaSoglia > 0 ? ultimaSoglia
                                                                         : stato.sogliaRete));
        pIcona.setColor(Tinte.TESTO);
        c.drawRect(xs - 2f, yBarra - m.s1, xs + 2f, yBarra + m.s3 + m.s1, pIcona);

        // Gli ultimi otto esiti: dicono se aggancia sempre o una volta su tre.
        if (quantiEsiti > 0) {
            float raggio = m.nota * 0.42f;
            float passo = raggio * 3f;
            float xd = x + raggio;
            float yd = pElenco.bottom - m.s4 - raggio;
            for (int i = 0; i < quantiEsiti; i++) {
                pIcona.setColor(esitiPresi[i] ? Tinte.RADIO : Tinte.ALLARME);
                if (esitiPresi[i]) {
                    c.drawCircle(xd, yd, raggio, pIcona);
                } else {
                    pIcona.setStyle(Paint.Style.STROKE);
                    pIcona.setStrokeWidth(Math.max(2f, raggio * 0.3f));
                    c.drawCircle(xd, yd, raggio, pIcona);
                    pIcona.setStyle(Paint.Style.FILL);
                }
                xd += passo;
            }
            pEtichetta.setTextSize(m.nota);
            pEtichetta.setColor(Tinte.TESTO_TENUE);
            c.drawText("Ultimi " + quantiEsiti, x, yd - raggio - m.s2, pEtichetta);
        }
    }

    private void colonnaTablet(Canvas c) {
        vetro.pannello(c, pTablet, m.raggio, Tinte.HOME, Tinte.VELO_QUIETO);
        float x = pTablet.left + m.s4;
        float largo = pTablet.width() - m.s4 * 2f;

        pEtichetta.setTextSize(m.nota);
        pEtichetta.setColor(Tinte.TESTO_TENUE);
        c.drawText("Tablet", x, pTablet.top + m.s3 + m.micro, pEtichetta);

        // Il percorso si prende il suo posto per primo, in fondo: e' l'unica
        // riga che non si puo' accorciare.
        float fondoPercorso = pTablet.bottom - m.s3;
        float cimaRighe = pTablet.top + m.s3 + m.micro + m.s3;
        float fondoRighe = fondoPercorso - m.micro * 1.6f;
        float passo = (fondoRighe - cimaRighe) / RIGHE_TABLET;
        float y = cimaRighe + passo * 0.78f;

        riga(c, x, y, largo, "Chi riconosce",
                risveglio.conLaRete() ? "la rete: chiunque"
                                      : "le impronte: una voce");
        y += passo;
        if (conLaRete()) {
            Rete r = risveglio.rete();
            riga(c, x, y, largo, "Modello",
                    (r != null ? r.quantiPesi() : 0) + " pesi");
            y += passo;
            riga(c, x, y, largo, "Campioni (riserva)", campioni.riassunto());
        } else {
            riga(c, x, y, largo, "Campioni su disco",
                    campioni.riassunto() + ", " + campioni.kilobyte() + " KB");
            y += passo;
            riga(c, x, y, largo, "Durata della parola",
                    risveglio.parola().durataTipica() * Mfcc.PASSO_MS + " ms");
        }
        y += passo;
        riga(c, x, y, largo, "Eco / soppressore",
                (Orecchio.ecoDisponibile() ? "c'e'" : "no") + " / "
                        + (Orecchio.rumoreDisponibile() ? "c'e'" : "no"));

        // Il percorso, in fondo e in piccolo. Stava sotto il titolo, dove
        // finiva mezzo coperto dal bordo dei pannelli: e' roba tecnica, e sta
        // con il resto della roba tecnica.
        pEtichetta.setTextSize(m.micro);
        pEtichetta.setColor(Tinte.SPENTO);
        c.drawText(rCartella.in(pEtichetta, campioni.dove(), largo, m.micro),
                x, fondoPercorso, pEtichetta);
    }

    private void riga(Canvas c, float x, float y, float largo, String che, String quanto) {
        // Come una riga delle Impostazioni di iOS: il nome bianco a sinistra,
        // il valore grigio a destra.
        pNota.setTextSize(m.nota);
        pNota.setColor(Tinte.TESTO);
        c.drawText(che, x, y, pNota);
        pNota.setColor(Tinte.TESTO_TENUE);
        pNota.setTextAlign(Paint.Align.RIGHT);
        c.drawText(rVoce.in(pNota, quanto, largo * 0.55f, m.nota), x + largo, y, pNota);
        pNota.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * La striscia in fondo: durante la prova il punteggio, altrimenti l'esito
     * dell'ultima cosa fatta.
     *
     * E' larga quanto la vela perche' e' quello che si guarda <b>mentre</b> si
     * parla, e perche' prima stava in coda alla colonna di sinistra dove le
     * righe cadevano fuori dallo schermo.
     */
    private void strisciaInFondo(Canvas c) {
        // Con la rete la prova ha il suo riquadro grande: qui resta solo
        // l'ultima cosa fatta.
        boolean inProva = risveglio.inProva() && !conLaRete();
        boolean fresco = System.currentTimeMillis() - quandoPunteggio < PUNTEGGIO_VIVE_MS;

        int tinta = inProva ? (fresco && ultimoPreso ? Tinte.RADIO : Tinte.OROLOGIO) : Tinte.HOME;
        vetro.pannello(c, striscia, m.raggio, tinta, inProva ? 0x1C : Tinte.VELO_QUIETO);

        float x = striscia.left + m.s4;
        float largo = striscia.width() - m.s4 * 2f;

        if (!inProva) {
            pEtichetta.setTextSize(m.micro);
            pEtichetta.setColor(Tinte.TESTO_TENUE);
            c.drawText("Ultima cosa fatta", x, striscia.top + m.s3 + m.micro, pEtichetta);

            String detto = messaggio != null ? messaggio
                    : (conLaRete()
                        ? "Premi Prova e di' « " + risveglio.frase() + " »: se aggancia sempre "
                          + "sei a posto, se no gira le due manopole qui a sinistra."
                        : "Registra qualche « " + Campioni.FRASE + " », poi Tara, poi Prova.");
            pCorpo.setTextSize(m.corpo);
            pCorpo.setColor(messaggio != null ? Tinte.TESTO : Tinte.TESTO_TENUE);
            String[] righe = bDetto.in(pCorpo, detto, largo, m.corpo, 2);
            float yy = striscia.top + m.s3 + m.micro + m.s3 + m.corpo;
            for (String riga : righe) { c.drawText(riga, x, yy, pCorpo); yy += m.corpo * 1.35f; }
            return;
        }

        // ---- in prova ----
        pEtichetta.setTextSize(m.micro);
        pEtichetta.setColor(Tinte.TESTO_TENUE);
        c.drawText("Prova: di' « " + risveglio.frase() + " »",
                x, striscia.top + m.s3 + m.micro, pEtichetta);

        float yNumero = striscia.bottom - m.s3;

        if (!fresco || ultimoPunteggio < 0f) {
            pCorpo.setTextSize(m.voce);
            pCorpo.setColor(Tinte.TESTO_TENUE);
            c.drawText("in ascolto: parla, e qui compare quanto somiglia",
                    x, yNumero - m.nota * 0.4f, pCorpo);
        } else if (ultimoPunteggio >= ParolaChiave.LONTANO) {
            pCorpo.setTextSize(m.voce);
            pCorpo.setColor(Tinte.OROLOGIO);
            c.drawText("sentito, ma di durata troppo diversa dai campioni",
                    x, yNumero - m.nota * 0.4f, pCorpo);
        } else {
            int colore = ultimoPreso ? Tinte.RADIO : Tinte.ALLARME;

            // Il numero, grande: e' la risposta alla domanda « ha sentito? ».
            pTitolo.setTextSize(m.titolo);
            pTitolo.setColor(colore);
            String numero = ParolaChiave.arrotonda(ultimoPunteggio);
            c.drawText(numero, x, yNumero - m.nota * 1.15f, pTitolo);
            float dopoNumero = x + pTitolo.measureText(numero) + m.s4;

            pCorpo.setTextSize(m.corpo);
            pCorpo.setColor(colore);
            c.drawText(ultimoPreso ? "aggancia" : "non aggancia",
                    dopoNumero, yNumero - m.nota * 1.9f, pCorpo);

            pNota.setTextSize(m.nota);
            if (ultimoPreso && !stato.accesa) {
                // vedi sotto
                // Il momento in cui serve dirlo e' questo: la prova aggancia,
                // e chi guarda conclude di aver finito. Invece il microfono e'
                // aperto solo perche' c'e' la prova, e chiudendo il pannello
                // si richiude.
                pNota.setColor(Tinte.OROLOGIO);
                c.drawText("ma l'ascolto continuo e' spento: accendilo con "
                                + "l'interruttore in alto a sinistra",
                        dopoNumero, yNumero - m.nota * 0.35f, pNota);
            } else {
                pNota.setColor(Tinte.TESTO_TENUE);
                String verso = risveglio.conLaRete() ? " — serve piu' alto di cosi'"
                                                     : " — serve piu' basso di cosi'";
                c.drawText("soglia " + ParolaChiave.arrotonda(ultimaSoglia)
                                + (ultimoPreso ? "" : verso),
                        dopoNumero, yNumero - m.nota * 0.35f, pNota);
            }
        }

        // Gli ultimi otto esiti, a destra: dicono se aggancia sempre o una
        // volta su tre, che e' la cosa che un numero solo non dice.
        if (quantiEsiti > 0) {
            float raggio = m.nota * 0.42f;
            float passo = raggio * 3f;
            float xd = striscia.right - m.s4 - raggio;
            for (int i = quantiEsiti - 1; i >= 0; i--) {
                pIcona.setColor(esitiPresi[i] ? Tinte.RADIO : Tinte.ALLARME);
                if (esitiPresi[i]) {
                    c.drawCircle(xd, striscia.centerY(), raggio, pIcona);
                } else {
                    pIcona.setStyle(Paint.Style.STROKE);
                    pIcona.setStrokeWidth(Math.max(2f, raggio * 0.3f));
                    c.drawCircle(xd, striscia.centerY(), raggio, pIcona);
                    pIcona.setStyle(Paint.Style.FILL);
                }
                xd -= passo;
            }
            pEtichetta.setTextSize(m.micro);
            pEtichetta.setColor(Tinte.TESTO_TENUE);
            pEtichetta.setTextAlign(Paint.Align.RIGHT);
            c.drawText("Ultimi " + quantiEsiti, striscia.right - m.s4,
                    striscia.centerY() + m.nota * 1.6f, pEtichetta);
            pEtichetta.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * Il conto alla rovescia, la registrazione, l'attesa.
     *
     * Sopra tutto e con un velo quasi pieno: mentre si registra non c'e'
     * nient'altro da guardare, e soprattutto niente da toccare per sbaglio.
     */
    private void sopraTutto(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), pVelo);
        float w = getWidth(), h = getHeight();
        appoggio.set(w * 0.22f, h * 0.24f, w * 0.78f, h * 0.76f);
        int colore = fase == REGISTRA ? Tinte.ALLARME : Tinte.HOME;
        vetro.pannello(c, appoggio, m.raggio, colore, fase == REGISTRA ? 0x30 : Tinte.VELO_QUIETO);

        pTitolo.setTextAlign(Paint.Align.CENTER);
        pCorpo.setTextAlign(Paint.Align.CENTER);
        float cx = appoggio.centerX();

        if (fase == CONTO) {
            pCorpo.setTextSize(m.voce);
            pCorpo.setColor(Tinte.TESTO_MEDIO);
            c.drawText(registraTipo == Campioni.SI
                            ? "preparati a dire « " + Campioni.FRASE + " »"
                            : "sto per registrare il rumore della stanza",
                    cx, appoggio.top + appoggio.height() * 0.28f, pCorpo);
            pTitolo.setTextSize(h * 0.22f);
            pTitolo.setColor(Tinte.TESTO);
            c.drawText(String.valueOf(contoRimasto), cx,
                    appoggio.centerY() + h * 0.09f, pTitolo);
        } else if (fase == REGISTRA) {
            pTitolo.setTextSize(m.cifra * 0.6f);
            pTitolo.setColor(Tinte.TESTO);
            c.drawText(registraTipo == Campioni.SI ? "Parla" : "Zitti",
                    cx, appoggio.top + appoggio.height() * 0.40f, pTitolo);

            pCorpo.setTextSize(m.voce);
            pCorpo.setColor(Tinte.TESTO_MEDIO);
            c.drawText(registraTipo == Campioni.SI
                            ? "« " + Campioni.FRASE + " »"
                            : (registraRimasti / 1000 + 1) + " secondi",
                    cx, appoggio.top + appoggio.height() * 0.56f, pCorpo);

            // Il livello, grande: e' l'unico modo di accorgersi mentre si
            // registra che il microfono non sta prendendo niente. Dopo, sarebbe
            // un campione muto da buttare.
            float bw = appoggio.width() * 0.7f;
            float by = appoggio.bottom - appoggio.height() * 0.24f;
            appoggio.set(cx - bw / 2f, by, cx + bw / 2f, by + m.s3);
            pPieno.setColor(Tinte.RIEMPIMENTO);
            c.drawRoundRect(appoggio, appoggio.height() / 2f, appoggio.height() / 2f, pPieno);
            float q = Math.min(1f, livello);
            if (q > 0.01f) {
                appoggio.right = appoggio.left + bw * q;
                pIcona.setColor(q > 0.9f ? Tinte.ALLARME : Tinte.RADIO);
                c.drawRoundRect(appoggio, appoggio.height() / 2f, appoggio.height() / 2f, pIcona);
            }
        } else {
            pCorpo.setTextSize(m.voce);
            pCorpo.setColor(Tinte.TESTO);
            c.drawText(lavoro, cx, appoggio.centerY(), pCorpo);
        }

        pTitolo.setTextAlign(Paint.Align.LEFT);
        pCorpo.setTextAlign(Paint.Align.LEFT);
    }

    // ---- tocco -------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Mentre si registra non si tocca niente: un tocco a caso durante il
        // conto alla rovescia manderebbe via la schermata e lascerebbe una
        // registrazione a meta'.
        if (fase != NORMALE) return true;

        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ultimaY = y;
                staScorrendo = false;
                if (rChiudi.contains(x, y)) { premutoChiudi = true; invalidate(); return true; }
                if (rInterruttore.contains(x, y)) { premutoInterruttore = true; invalidate(); return true; }
                if (rEco.contains(x, y)) { premutoEco = true; invalidate(); return true; }
                if (!rMeno.isEmpty() && rMeno.contains(x, y)) { premutoMeno = true; invalidate(); return true; }
                if (!rPiu.isEmpty() && rPiu.contains(x, y)) { premutoPiu = true; invalidate(); return true; }
                if (rSoglia.contains(x, y)) { trascinaSoglia = true; muoviSoglia(x); return true; }
                for (int i = 0; i < tasti.length; i++) {
                    if (!tasti[i].isEmpty() && tasti[i].contains(x, y)) {
                        premuto = i; invalidate(); return true;
                    }
                }
                premutoCestino = cestinoSotto(x, y);
                if (premutoCestino >= 0) { invalidate(); return true; }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (trascinaSoglia) { muoviSoglia(x); return true; }
                if (pElenco.contains(x, y) && Math.abs(y - ultimaY) > m.dp(6)) {
                    staScorrendo = true;
                    premutoCestino = -1;
                    scorrimento = Math.max(0f, Math.min(scorrimentoMax, scorrimento - (y - ultimaY)));
                    ultimaY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (trascinaSoglia) { trascinaSoglia = false; salvaStato(); return true; }
                if (premutoChiudi && rChiudi.contains(x, y)) uscita.suChiudi();
                else if (premutoInterruttore && rInterruttore.contains(x, y)) accendiSpegni();
                else if (premutoEco && rEco.contains(x, y)) cambiaEco();
                else if (premutoMeno && rMeno.contains(x, y)) cambiaDiFila(-1);
                else if (premutoPiu && rPiu.contains(x, y)) cambiaDiFila(+1);
                else if (premuto >= 0 && !tasti[premuto].isEmpty()
                         && tasti[premuto].contains(x, y)) premi(premuto);
                else if (premutoCestino >= 0 && !staScorrendo
                         && cestinoSotto(x, y) == premutoCestino) elimina(premutoCestino);
                else {
                    for (int i = 0; i < 2; i++) {
                        if (linguette[i].contains(x, y)) { cambiaTipo(i); break; }
                    }
                }
                azzeraPressioni();
                return true;

            case MotionEvent.ACTION_CANCEL:
                trascinaSoglia = false;
                azzeraPressioni();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void azzeraPressioni() {
        premuto = -1;
        premutoCestino = -1;
        premutoChiudi = premutoInterruttore = premutoEco = false;
        premutoMeno = premutoPiu = false;
        staScorrendo = false;
        invalidate();
    }

    private int cestinoSotto(float x, float y) {
        if (!pElenco.contains(x, y) || y < cimaElenco()) return -1;
        float h = altezzaRiga(), passo = h + m.s1;
        int i = (int) ((y - cimaElenco() + scorrimento) / passo);
        if (i < 0 || i >= elenco.size()) return -1;
        // Solo la parte destra della riga: cosi' uno scorrimento cominciato
        // sopra una riga non cancella.
        return x > pElenco.right - m.s4 - m.bersaglio ? i : -1;
    }

    /**
     * Quante finestre di fila servono per svegliarsi.
     *
     * E' la manopola che conta piu' della soglia, ed e' misurata: su venti
     * minuti di parlato d'altri, una finestra sola da' 78 risvegli a vuoto
     * all'ora, due ne danno 21, quattro zero - e quattro costano solo il sette
     * per cento delle parole prese. Meno di due non si scende: sarebbe come
     * non averle.
     */
    private void cambiaDiFila(int quanto) {
        int nuovo = Math.max(1, Math.min(8, stato.difila + quanto));
        if (nuovo == stato.difila) return;
        stato.difila = nuovo;
        salvaStato();
        messaggio = "Adesso servono " + nuovo + " finestre di fila. Piu' alte, meno "
                  + "risvegli a vuoto e qualche parola persa; piu' basse il contrario.";
    }

    private void muoviSoglia(float x) {
        float q = Math.max(0f, Math.min(1f, (x - rSoglia.left) / rSoglia.width()));
        if (risveglio.conLaRete()) {
            stato.sogliaRete = q;
        } else {
            stato.soglia = SOGLIA_MIN + (SOGLIA_MAX - SOGLIA_MIN) * q;
            risveglio.parola().setSoglia(stato.soglia);
        }
        invalidate();
    }

    private void salvaStato() {
        campioni.scriviStato(stato);
        risveglio.parola().setSoglia(stato.soglia);
        invalidate();
    }

    private void accendiSpegni() {
        if (!risveglio.pronta()) {
            messaggio = "Prima servono dei campioni: tocca « registra « Hey Home » » "
                      + "tre o quattro volte.";
            invalidate();
            return;
        }
        stato.accesa = !stato.accesa;
        salvaStato();
    }

    private void cambiaEco() {
        if (!Orecchio.ecoDisponibile()) {
            messaggio = "Questo apparecchio non offre la cancellazione d'eco.";
            invalidate();
            return;
        }
        stato.eco = !stato.eco;
        campioni.scriviStato(stato);
        // Il microfono va riaperto: la sorgente si sceglie all'apertura, non
        // dopo. Mentre e' chiuso la barra del livello resta ferma per mezzo
        // secondo, ed e' giusto che si veda.
        boolean eraInProva = risveglio.inProva();
        risveglio.orecchio().spegni();
        risveglio.accendiPerRegistrare();
        if (eraInProva) risveglio.orecchio().pausa(false);
        messaggio = "Cancellazione d'eco " + (stato.eco ? "accesa" : "spenta")
                  + ": il microfono e' stato riaperto con l'altra sorgente.";
        invalidate();
    }

    private void cambiaTipo(int tipo) {
        if (tipoInElenco == tipo) return;
        tipoInElenco = tipo;
        scorrimento = 0f;
        rileggiElenco();
    }

    private void rileggiElenco() {
        elenco.clear();
        for (File f : campioni.elenco(tipoInElenco)) elenco.add(new Voce(f));
        aggiornaScorrimentoMax();
        invalidate();
    }

    private void premi(int quale) {
        switch (quale) {
            case T_PAROLA: avviaConto(Campioni.SI); break;
            case T_RUMORE: avviaConto(Campioni.NO); break;
            case T_TARA:   tara(); break;
            case T_PROVA:
                if (!risveglio.pronta()) return;
                boolean acceso = !risveglio.inProva();
                risveglio.setProva(acceso);
                risveglio.orecchio().pausa(!acceso);
                boolean qualcunoPreso = false;
                for (int k = 0; k < quantiEsiti; k++) if (esitiPresi[k]) qualcunoPreso = true;
                ultimoPunteggio = -1f;
                ultimaDistanza = ParolaChiave.LONTANO;
                ultimeDiFila = 0;
                massimeDiFila = 0;
                quantiEsiti = 0;
                if (!acceso) {
                    messaggio = qualcunoPreso && !stato.accesa
                            ? "La prova agganciava, ma l'ascolto continuo e' SPENTO: "
                              + "premi l'interruttore in alto, o Assistente Home non rispondera' "
                              + "alla voce."
                            : "Prova finita.";
                }
                invalidate();
                break;
        }
    }

    private void elimina(int i) {
        if (i < 0 || i >= elenco.size()) return;
        final File f = elenco.get(i).file;
        campioni.elimina(f);
        rileggiElenco();
        // I modelli cambiano: rifarli subito, o la parola continuerebbe a
        // rispondere a un campione che a schermo non c'e' piu'.
        ricarica("tolgo " + f.getName());
    }

    // ---- registrazione -----------------------------------------------------

    private void avviaConto(final int tipo) {
        registraTipo = tipo;
        contoRimasto = 3;
        fase = CONTO;
        risveglio.setProva(false);
        risveglio.orecchio().pausa(true);
        if (!risveglio.orecchio().acceso()) risveglio.accendiPerRegistrare();
        invalidate();
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (fase != CONTO) return;
                contoRimasto--;
                if (contoRimasto > 0) { invalidate(); ui.postDelayed(this, 800); }
                else registraOra(tipo);
            }
        }, 800);
    }

    private void registraOra(final int tipo) {
        fase = REGISTRA;
        registraRimasti = tipo == Campioni.SI ? DURATA_PAROLA : DURATA_RUMORE;
        livelloPicco = 0f;
        invalidate();

        risveglio.orecchio().registra(registraRimasti, new Orecchio.Presa() {
            @Override public void suAvanzamento(final int rimastiMs, float l, float picco) {
                ui.post(new Runnable() {
                    @Override public void run() {
                        registraRimasti = rimastiMs;
                        invalidate();
                    }
                });
            }
            @Override public void suPresa(final short[] suono, final int quanti) {
                ui.post(new Runnable() {
                    @Override public void run() { salvaPresa(tipo, suono, quanti); }
                });
            }
        });
    }

    private void salvaPresa(final int tipo, final short[] suono, final int quanti) {
        if (suono == null || quanti <= 0) {
            fase = NORMALE;
            messaggio = "Il microfono non ha dato niente.";
            invalidate();
            return;
        }
        fase = LAVORA;
        lavoro = "salvo…";
        invalidate();

        new Thread(new Runnable() {
            @Override public void run() {
                // Il rumore si salva intero: dentro ci deve stare tutto quello
                // che la stanza fa, silenzi compresi. La parola invece si
                // ritaglia sul parlato - vedi Orecchio.ritaglia, dove c'e' il
                // perche' un campione non ritagliato non aggancia mai.
                int da = 0, lung = quanti;
                if (tipo == Campioni.SI) {
                    int[] estremi = Orecchio.ritaglia(suono, quanti);
                    if (estremi == null) {
                        finito("Non ho sentito niente: parla piu' vicino, o piu' forte.");
                        return;
                    }
                    da = estremi[0];
                    lung = estremi[1];
                }
                File dove = campioni.prossimo(tipo, null);
                short[] pezzo;
                if (da == 0 && lung == quanti) {
                    pezzo = suono;
                } else {
                    pezzo = new short[lung];
                    System.arraycopy(suono, da, pezzo, 0, lung);
                }
                if (!Onda.scrivi(dove, pezzo, lung)) {
                    finito("Non sono riuscito a scrivere il file.");
                    return;
                }
                if (tipo == Campioni.SI) {
                    risveglio.ricaricaOra();
                    finito("Salvato: " + lung * 1000 / Onda.HZ + " ms di parlato. Adesso i "
                            + "« Hey Home » sono " + campioni.quanti(Campioni.SI)
                            + ". Quando ne hai almeno tre, TARA.");
                } else {
                    finito("Rumore salvato: " + lung * 1000 / Onda.HZ + " ms. "
                            + "Adesso TARA, e guarda la riga dei « falsi ».");
                }
            }
        }, "Casa-campione").start();
    }

    private void finito(final String detto) {
        ui.post(new Runnable() {
            @Override public void run() {
                fase = NORMALE;
                messaggio = detto;
                rileggiElenco();
                invalidate();
            }
        });
    }

    // ---- taratura ----------------------------------------------------------

    private void tara() {
        if (!risveglio.pronta()) return;
        fase = LAVORA;
        lavoro = "misuro i campioni…";
        invalidate();
        new Thread(new Runnable() {
            @Override public void run() {
                final ParolaChiave.Taratura t = risveglio.tara();
                ui.post(new Runnable() {
                    @Override public void run() {
                        fase = NORMALE;
                        stato.soglia = t.sogliaProposta;
                        salvaStato();
                        messaggio = t.presi + " « si » su " + t.quantiSi + " presi, "
                                  + t.falsi + " falsi su " + t.quantiNo + " « no ». "
                                  + "Soglia " + ParolaChiave.arrotonda(t.sogliaProposta)
                                  + ". " + t.racconto;
                        invalidate();
                    }
                });
            }
        }, "Casa-taratura").start();
    }

    private void ricarica(final String cosa) {
        fase = LAVORA;
        lavoro = cosa;
        invalidate();
        new Thread(new Runnable() {
            @Override public void run() {
                risveglio.ricaricaOra();
                ui.post(new Runnable() {
                    @Override public void run() { fase = NORMALE; invalidate(); }
                });
            }
        }, "Casa-modelli").start();
    }
}
