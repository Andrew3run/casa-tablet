package dev.casa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * La musica: quel che suona, e da cosa farla partire.
 *
 * <b>Non e' un telecomando per l'app di qualcun altro.</b> Sotto c'e'
 * {@link Musica}, cioe' go-librespot dentro Casa: il brano esce dagli
 * altoparlanti del tablet e questa schermata e' il lettore, non un rimando.
 *
 * <b>Come e' fatta.</b> Una schermata sola che cambia mestiere a seconda di
 * dove si e' arrivati:
 *
 * <ul>
 *   <li><b>si accende</b> - il motore sta partendo, e lo si dice;
 *   <li><b>l'accoppiamento</b> - la prima volta serve un'approvazione: un
 *       {@link Qr} da inquadrare col telefono, e accanto il codice scritto
 *       grande per chi preferisce ricopiarlo. Qui non si digita niente: una
 *       password di Spotify su una tastiera a schermo appesa al muro e' una
 *       pena;
 *   <li><b>il lettore</b> - copertina, brano, barra, comandi, e le playlist.
 * </ul>
 *
 * <b>Il colore lo da' il disco.</b> La copertina viene ridotta a otto per otto,
 * se ne fa la media e la si addomestica ({@link Tinte#addomestica}): quel
 * colore tinge il pannello, l'alone sotto il tasto e la barra della posizione.
 * E' lo stesso principio del resto di Casa - ogni cosa tinge quel che le sta
 * attorno - applicato a una tinta che cambia a ogni brano invece che a ogni
 * sezione.
 *
 * <b>Una View sola, disegnata a mano</b>, come tutte le altre: nessun albero di
 * widget, nessuna Drawable tenuta in memoria, e le uniche bitmap sono le
 * copertine, che hanno un tetto e si buttano uscendo di scena.
 */
public class SezioneMusica extends Sezione {

    /**
     * Due per riga, non sei come le stazioni.
     *
     * Una stazione si riconosce dal logo e il nome e' un di piu'; una playlist
     * <b>e'</b> il suo nome. Con sei colonne al nome restavano sessanta pixel
     * (« Daily… », « Scelti… », « Linki… »: quattro tessere indistinguibili),
     * con quattro ne restavano centosessanta e i nomi lunghi si troncavano
     * ancora. Con due, il nome ha mezzo schermo e non si tronca mai. Le righe
     * viste restano due, e le altre si raggiungono scorrendo: meglio scorrere
     * che leggere a meta'.
     */
    private static final int COLONNE = 2, RIGHE_VISTE = 2;

    /** Dentro una playlist le righe sono piu' larghe: due per riga. Quante
     *  righe ci stiano lo decide lo spazio, non un numero fisso - con la
     *  tastiera aperta ce n'e' meta'. */
    private static final int COLONNE_BRANI = 2;

    private final Paint pEtichetta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitolo    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSotto     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTempo     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pComando   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPieno     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCodice    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNomeTess  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIniziale  = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Il titolo grande della sezione, come nelle app di Apple. */
    private final Paint pGrande    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pImmagine  = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    /** La freccia « indietro », costruita nell'origine e rifatta solo se cambia
     *  la misura: un percorso modificato a ogni fotogramma e' un percorso
     *  nuovo per il disegno accelerato, che lo rasterizza daccapo e se lo
     *  tiene in cache per niente - vedi {@link Icone}. */
    private final Path percorso = new Path();
    private float percorsoLato = Float.NaN;

    private Path freccia(float lato) {
        if (percorsoLato != lato) {
            percorsoLato = lato;
            percorso.reset();
            percorso.moveTo(lato * 0.28f, -lato * 0.45f);
            percorso.lineTo(-lato * 0.22f, 0f);
            percorso.lineTo(lato * 0.28f, lato * 0.45f);
        }
        return percorso;
    }
    private final RectF appoggio = new RectF();

    /** Il pannello del lettore, la copertina dentro di esso, la barra della
     *  posizione. */
    private final RectF pannello = new RectF(), disco = new RectF(), scorrimento = new RectF();

    /** precedente, play/pausa, successivo, mischia, volume giu', volume su. */
    private final RectF[] comandi = new RectF[6];

    /** La finestra in cui vivono le tessere, e la tessera modello: le altre si
     *  ricavano spostandola, cosi' il numero di playlist non decide quanti
     *  rettangoli esistono in memoria. */
    private final RectF griglia = new RectF(), tessera = new RectF(), quadretto = new RectF();

    /** Il pannello grande dei momenti in cui non si suona: avvio, codice,
     *  guasto. */
    private final RectF avviso = new RectF(), riquadroQr = new RectF();

    private final Testo.Riga rTitolo = new Testo.Riga();
    private final Testo.Riga rSotto  = new Testo.Riga();
    private final Testo.Riga rAvviso = new Testo.Riga();
    private final Testo.Riga rNome   = new Testo.Riga();
    private final Testo.Blocco bSpiega = new Testo.Blocco();

    private Musica musica;
    private Preferiti preferiti;
    private Copertine copertine;
    private AudioManager audio;

    private int premutoComando = -1, premutaTessera = -1;
    private boolean premutoAvviso;

    private float corpoEtichetta, corpoTitolo, corpoSotto, corpoTempo, corpoNomeTess, corpoCodice;
    private float latoDisco, latoQuadretto, larghezzaTesto, larghezzaTessera, bordoTessera;
    private float passoX, passoY, vuoto;
    private float passoBraniX, passoBraniY, latoCopertina, bordoRiga, larghezzaRiga;

    /** La finestra cosi' com'e' adesso. */
    private int larghezza, altezza;

    /** true quando lo spazio e' meta': niente lettore, solo cio' che serve a
     *  scrivere e a scegliere. */
    private boolean stretto;

    /** La playlist aperta, quando se ne sta guardando dentro una. L'elenco dei
     *  brani sta in {@link Preferiti}; qui serve solo il suo nome, da scrivere
     *  in cima. */
    private Preferiti.Voce apertaVoce;

    /** Dove si tocca per tornare all'elenco delle playlist, e dove per farla
     *  partire tutta. */
    private final RectF indietro = new RectF(), riproduciTutto = new RectF();

    /**
     * Il tasto che rilegge le playlist da Spotify.
     *
     * Sta qui perche' le playlist si cambiano <b>da un altro apparecchio</b> -
     * dal telefono, dal computer - e Spotify non lo dice a nessuno: senza un
     * modo di chiedere « guarda di nuovo », una playlist creata cinque minuti
     * fa sul telefono comparirebbe sul tablet dopo sei ore, e nel frattempo
     * l'unica spiegazione per chi guarda e' che Casa sia rotta.
     *
     * E' in cima all'elenco, in fondo alla riga dell'etichetta: e' il posto
     * dove sta il titolo di quello che si sta guardando, quindi e' il posto
     * dove si cerca il modo di rifarlo.
     */
    private final RectF aggiorna = new RectF();
    private boolean premutoAggiorna;

    /** La X che svuota quello che si e' scritto, e il tasto che riporta alla
     *  schermata principale della Musica da qualunque profondita'. */
    private final RectF cancellaTesto = new RectF(), allaMusica = new RectF();

    /** La barra di ricerca, in cima alla griglia. */
    private final RectF barraRicerca = new RectF();

    /**
     * La tastiera, che e' nostra e non di sistema.
     *
     * Quella di Android su questo tablet si prende meta' schermo; questa un
     * terzo, e sopra ci stanno otto risultati invece di due. Vedi
     * {@link Tastierino}.
     */
    private final Tastierino tastierino = new Tastierino();

    private Cerca ricerca;

    /** Lo stato della ricerca: se e' in scena, cosa e' stato scritto, cosa e'
     *  tornato. */
    private boolean inRicerca;

    /** La tastiera si puo' abbassare senza uscire dalla ricerca: i risultati
     *  restano e si prendono lo spazio che lei lascia. Chiudere la tastiera e
     *  chiudere la ricerca erano la stessa cosa, e non lo sono. */
    private boolean tastieraGiu;
    private String scritto = "";
    private Cerca.Trovato[] risultati = new Cerca.Trovato[0];
    private String notaRicerca;

    /**
     * Dove si e' arrivati scendendo dentro i risultati: ricerca, poi un
     * artista, poi un suo album. Tre livelli bastano - piu' giu' non si va - e
     * una pila di tre caselle costa meno di una lista con i suoi oggetti.
     *
     * Il livello 0 sono i risultati della ricerca; salendo di indice si scende
     * nella navigazione. Di ogni livello si tiene cosa mostrava, come si
     * chiamava, e che cosa suona il tasto accanto al nome.
     */
    private static final int PILA = 3;
    private final Cerca.Trovato[][] pilaRisultati = new Cerca.Trovato[PILA][];
    private final String[] pilaTitolo = new String[PILA];
    private final String[] pilaUri = new String[PILA];
    private int profondita;

    /** Cresce a ogni apertura: le risposte che arrivano tardi, da qualcosa che
     *  nel frattempo e' stato chiuso, si buttano. */
    private int giroApertura;

    /**
     * Dove finisce ogni risultato nella griglia.
     *
     * Non basta « indice diviso due » da quando negli elenchi ci sono le
     * intestazioni delle sezioni: quelle prendono <b>una riga intera</b> e
     * spingono in basso tutto il resto. Le posizioni si calcolano una volta
     * quando l'elenco cambia, invece che a ogni fotogramma: la colonna -1
     * vuol dire « riga intera ».
     */
    private int[] rigaDi = new int[0], colonnaDi = new int[0];
    private int righeElenco;

    private void calcolaPosizioni() {
        int quanti = risultati.length;
        if (rigaDi.length < quanti) { rigaDi = new int[quanti]; colonnaDi = new int[quanti]; }
        int riga = 0, col = 0;
        for (int i = 0; i < quanti; i++) {
            if (risultati[i].intestazione()) {
                if (col != 0) { riga++; col = 0; }
                rigaDi[i] = riga; colonnaDi[i] = -1;
                riga++;
            } else {
                rigaDi[i] = riga; colonnaDi[i] = col;
                if (++col >= COLONNE_BRANI) { col = 0; riga++; }
            }
        }
        righeElenco = riga + (col > 0 ? 1 : 0);
    }

    /** Di quanto e' scorsa la griglia, e da dove e' cominciato il dito. */
    private float scorsa, dallaY, dallaScorsa;
    private boolean trascinando;
    private final int soglia;

    /** La tinta del disco che suona, ricalcolata solo quando cambia copertina.
     *  Farlo a ogni onDraw vorrebbe dire ridimensionare una bitmap sessanta
     *  volte al secondo per un colore che non cambia mai. */
    private String copertinaDellaTinta;
    private int tinta = Tinte.MUSICA;

    /** Il QR dell'accoppiamento, calcolato una volta per indirizzo. */
    private String indirizzoDelQr;
    private boolean[][] qr;

    public SezioneMusica(Context c, Misure m) {
        super(c, m);
        for (int i = 0; i < comandi.length; i++) comandi[i] = new RectF();

        pEtichetta.setColor(Tinte.TESTO_TENUE);
        pEtichetta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pTitolo.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        pSotto.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pTempo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pNomeTess.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pIniziale.setTextAlign(Paint.Align.CENTER);
        pIniziale.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pCodice.setTextAlign(Paint.Align.LEFT);
        // Il codice si ricopia carattere per carattere guardando un telefono:
        // a spaziatura fissa non si perde il segno.
        pCodice.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        pGrande.setColor(Tinte.TESTO);
        pGrande.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pComando.setStyle(Paint.Style.STROKE);
        pComando.setStrokeCap(Paint.Cap.ROUND);
        pComando.setStrokeJoin(Paint.Join.ROUND);

        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        soglia = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    // Si chiama Spotify perche' e' quello che c'e' dentro, e perche' « Musica »
    // accanto a « Radio » non diceva quale delle due suonasse cosa.
    @Override public String titolo() { return "Spotify"; }
    @Override public int tinta() { return Tinte.MUSICA; }

    /** Il marchio Spotify, quello vero e del suo verde: questa sezione
     *  <b>e'</b> Spotify, non una sezione musica che ci somiglia. */
    @Override public int icona()       { return Icone.SPOTIFY; }
    @Override public int iconaPiena()  { return Icone.SPOTIFY; }
    @Override public int coloreIcona() { return Tinte.SPOTIFY; }

    public void setMusica(Musica mu) { musica = mu; invalidate(); }
    public void setPreferiti(Preferiti p) { preferiti = p; invalidate(); }
    public void setCopertine(Copertine c) { copertine = c; invalidate(); }
    public void setCerca(Cerca c) { ricerca = c; }

    /**
     * Una lettera, uno spazio, una cancellazione.
     *
     * Ogni tasto rimanda la ricerca a fra poco invece di farla subito: si cerca
     * <b>mentre si scrive</b>, come nell'app vera, ma non a ogni lettera - ogni
     * ricerca e' una richiesta in rete su un Cortex-A7, e « rancore » ne
     * farebbe sette. Quattro decimi di pausa fra un tasto e l'altro vogliono
     * dire che si e' finito di scrivere.
     */
    private void scrivi(int tasto) {
        if (tasto == Tastierino.CANCELLA) {
            if (scritto.length() > 0) scritto = scritto.substring(0, scritto.length() - 1);
        } else if (tasto == Tastierino.SPAZIO) {
            if (scritto.length() > 0 && !scritto.endsWith(" ")) scritto += " ";
        } else if (tasto >= 0) {
            if (scritto.length() < 40) scritto += (char) tasto;
        } else {
            return;
        }
        rimandaLaRicerca();
        invalidate();
    }

    private final Runnable fraPoco = new Runnable() {
        @Override public void run() { cercaAdesso(); }
    };

    private void rimandaLaRicerca() {
        removeCallbacks(fraPoco);
        if (scritto.trim().length() >= 2) postDelayed(fraPoco, 400);
    }

    /** Cerca quello che c'e' scritto adesso. */
    public void cercaAdesso() {
        if (ricerca == null) return;
        // Una ricerca nuova riporta in cima: i risultati sostituiscono quello
        // che si stava guardando, e restare "dentro" un album di prima sarebbe
        // solo confusione.
        giroApertura++;
        profondita = 0;
        notaRicerca = "sto cercando…";
        risultati = new Cerca.Trovato[0];
        invalidate();
        final int mio = ++giroApertura;
        ricerca.cerca(scritto, new Cerca.Esito() {
            @Override public void trovati(Cerca.Trovato[] trovati, String perche) {
                // Le risposte fuori ordine si buttano: scrivendo in fretta
                // partono piu' ricerche, e l'ultima che risponde non e'
                // necessariamente quella dell'ultima parola scritta.
                if (mio != giroApertura) return;
                risultati = trovati != null ? trovati : new Cerca.Trovato[0];
                calcolaPosizioni();
                notaRicerca = perche;
                invalidate();
            }
        });
    }

    private void apriRicerca() {
        inRicerca = true;
        tastieraGiu = false;
        scritto = "";
        risultati = new Cerca.Trovato[0];
        notaRicerca = (ricerca != null && ricerca.pronta())
                ? null
                : "per cercare serve la chiave di Spotify: vedi docs/musica.md";
        scorsa = 0f;
        misura();
        invalidate();
    }

    /**
     * Scende dentro un risultato: un artista mostra i suoi album, un album i
     * suoi brani.
     *
     * Quello che c'era prima si mette da parte, cosi' l'indietro rimette
     * l'elenco esattamente com'era: perdere i risultati vorrebbe dire aver
     * perso la ricerca, e riscriverla su una tastiera a schermo.
     */
    private void apriDentro(final Cerca.Trovato chi) {
        if (ricerca == null || profondita + 1 >= PILA) return;
        pilaRisultati[profondita] = risultati;
        profondita++;
        // Il titolo del livello e' il nome di quello che si apre, tranne per
        // la discografia: li' « Mostra tutte le uscite » sarebbe il nome del
        // bottone che si e' premuto, non di quello che si sta guardando.
        pilaTitolo[profondita] = chi.tipo == Cerca.DISCOGRAFIA ? "Discografia" : chi.titolo;
        pilaUri[profondita] = chi.uri;
        risultati = new Cerca.Trovato[0];
        notaRicerca = chi.tipo == Cerca.BRANO ? null
                : (chi.tipo == Cerca.ALBUM ? "sto leggendo l'album…"
                                           : "sto guardando cosa ha fatto…");
        scorsa = 0f;
        // Entrando in una pagina la tastiera si abbassa da sola: si e' finito
        // di scrivere, e da qui in poi si guarda.
        tastieraGiu = true;
        final int mio = ++giroApertura;
        misura();
        invalidate();

        Cerca.Esito esito = new Cerca.Esito() {
            @Override public void trovati(Cerca.Trovato[] trovati, String perche) {
                if (mio != giroApertura) return;      // ne hanno aperto un altro
                risultati = trovati != null ? trovati : new Cerca.Trovato[0];
                calcolaPosizioni();
                notaRicerca = perche;
                misura();
                invalidate();
            }
        };
        if (chi.tipo == Cerca.ARTISTA) ricerca.paginaArtista(chi.uri, esito);
        else if (chi.tipo == Cerca.DISCOGRAFIA) ricerca.discografia(chi.uri, esito);
        else ricerca.braniDi(chi.uri, esito);
    }

    /** Risale di un livello. */
    private void tornaIndietro() {
        if (profondita == 0) return;
        giroApertura++;
        pilaTitolo[profondita] = null;
        pilaUri[profondita] = null;
        profondita--;
        risultati = pilaRisultati[profondita] != null
                ? pilaRisultati[profondita] : new Cerca.Trovato[0];
        calcolaPosizioni();
        pilaRisultati[profondita] = null;
        notaRicerca = null;
        scorsa = 0f;
        misura();
        invalidate();
    }

    private void chiudiRicerca() {
        inRicerca = false;
        tastieraGiu = false;
        giroApertura++;
        profondita = 0;
        for (int i = 0; i < PILA; i++) {
            pilaRisultati[i] = null; pilaTitolo[i] = null; pilaUri[i] = null;
        }
        scritto = "";
        risultati = new Cerca.Trovato[0];
        notaRicerca = null;
        scorsa = 0f;
        removeCallbacks(fraPoco);
        misura();
        invalidate();
    }

    /** La chiama chi riceve il cambio di stato. */
    public void risveglia() { invalidate(); }

    /**
     * Entrando si accende il motore.
     *
     * Non all'avvio di Casa: dodici megabyte e una connessione a Spotify
     * tenuti in piedi da quando il tablet si accende, per una musica che
     * magari oggi non si ascolta, sono dodici megabyte tolti a tutto il resto.
     * Qui invece l'ha chiesto qualcuno, entrando.
     *
     * E non si spegne uscendo: chi esce da questa schermata mentre suona vuole
     * guardare l'orologio, non fermare la musica.
     */
    @Override
    public void suEntrata() {
        super.suEntrata();
        if (musica != null) musica.avvia();
        if (preferiti != null) preferiti.aggiorna(musica);
    }

    @Override
    public void suUscita() {
        if (copertine != null) copertine.svuota();
        copertinaDellaTinta = null;
        // Uscendo si torna all'elenco delle playlist: rientrare e ritrovarsi
        // dentro a quella di ieri sera - o dentro una ricerca di ieri sera -
        // non e' quello che uno si aspetta, e sono decine di righe che non
        // serve tenere.
        chiudiRicerca();
        chiudiPlaylist();
    }

    // ---- misure -----------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        larghezza = w;
        altezza = h;
        misura();
    }

    /**
     * Rifa' i conti dello spazio.
     *
     * Non si chiama solo quando cambia la finestra: si chiama anche quando si
     * apre la tastiera e quando si entra o si esce dalla ricerca, perche' in
     * quei momenti lo spazio utile e' un altro e la schermata deve
     * riorganizzarsi invece di finire sotto la tastiera.
     */
    private void misura() {
        int w = larghezza, h = altezza;
        if (w <= 0 || h <= 0) return;
        float mg = m.margine;

        // In ricerca la schermata cambia forma: niente lettore, la tastiera in
        // fondo e i risultati in mezzo. La tastiera e' nostra, quindi lo spazio
        // che prende lo decidiamo noi - un terzo - invece di subirlo.
        stretto = inRicerca;

        corpoEtichetta = m.nota;
        corpoTitolo    = m.titolo;
        pGrande.setTextSize(m.titolo);
        corpoSotto     = m.corpo;
        corpoTempo     = m.micro;
        corpoNomeTess  = m.nota;
        corpoCodice    = h * 0.105f;
        pEtichetta.setTextSize(corpoEtichetta);
        pTempo.setTextSize(corpoTempo);
        pCodice.setTextSize(corpoCodice);

        // <b>La barra di ricerca sta in cima a tutto</b>, e non dove stanno le
        // playlist: e' il punto piu' lontano dalla tastiera, quindi l'unico
        // che resta visibile mentre si scrive, ed e' anche dove uno la cerca.
        float altaBarra = Math.max(m.bersaglio, h * 0.062f);
        // Dentro un artista o un album la barra si stringe: a sinistra ci va
        // la freccia per tornare, col nome di quello che si sta guardando.
        // Prima quella riga stava sotto e finiva addosso alla barra.
        // A sinistra della barra ci stanno: il tasto per tornare alla Musica
        // (sempre, mentre si cerca), e - solo dentro qualcosa - la freccia col
        // nome di quello che si sta guardando.
        float xBarra = mg + (inRicerca ? (profondita > 0 ? w * 0.34f : h * 0.20f) : 0f);
        barraRicerca.set(xBarra, mg * 0.5f, w - mg, mg * 0.5f + altaBarra);
        float sottoLaBarra = barraRicerca.bottom + mg * 0.55f;

        // Il lettore sotto, e piu' basso di prima: la copertina e i comandi si
        // erano presi meta' schermo, e quella meta' serve alle playlist. Le due
        // fasce non si ridimensionano comunque l'una con l'altra - i comandi
        // hanno un bersaglio sotto cui non si scende.
        pannello.set(mg, sottoLaBarra, w - mg, h * 0.46f);
        avviso.set(mg, sottoLaBarra, w - mg, h * 0.80f);

        latoDisco = pannello.height() - mg * 2f;
        disco.set(pannello.left + mg, pannello.top + mg,
                  pannello.left + mg + latoDisco, pannello.top + mg + latoDisco);

        float xTesto = disco.right + mg * 1.2f;
        larghezzaTesto = pannello.right - mg - xTesto;

        // <b>I comandi non possono sbordare</b>, e non basta sperarci: il
        // gruppo del brano si ancora a sinistra, quello del volume a destra, e
        // il lato si stringe se lo spazio fra i due non basta. Prima erano sei
        // tondi messi in fila da sinistra con un passo fisso, e il piu' a
        // destra finiva fuori dal pannello - e fuori dallo schermo.
        //
        // 0,24 e non 0,30: a un terzo dell'altezza del pannello i sei tondi
        // pesavano piu' del titolo del brano, ed erano la prima cosa che si
        // vedeva entrando. Un comando deve essere grande abbastanza da
        // prenderlo con un dito - da qui il bersaglio come minimo - non
        // abbastanza da comandare la schermata.
        float lato = Math.max(m.bersaglio, pannello.height() * 0.24f);
        lato = Math.min(lato, larghezzaTesto / 7.6f);
        float passo = lato * 1.22f;
        float y = pannello.bottom - mg - lato / 2f;

        float x = xTesto + lato / 2f;
        for (int i = 0; i <= 3; i++) {
            comandi[i].set(x - lato / 2f, y - lato / 2f, x + lato / 2f, y + lato / 2f);
            x += passo;
        }
        float xd = pannello.right - mg - lato / 2f;
        for (int i = 5; i >= 4; i--) {
            comandi[i].set(xd - lato / 2f, y - lato / 2f, xd + lato / 2f, y + lato / 2f);
            xd -= passo;
        }
        pComando.setStrokeWidth(Math.max(2f, lato * 0.055f));

        // La barra della posizione sta sopra i comandi, larga quanto il testo.
        float altezzaBarra = Math.max(m.dp(4), h * 0.009f);
        float yBarra = comandi[0].top - mg * 0.9f;
        scorrimento.set(xTesto, yBarra - altezzaBarra / 2f,
                        pannello.right - mg, yBarra + altezzaBarra / 2f);

        // La griglia: sei per riga, tessere orizzontali - immagine a sinistra e
        // nome accanto. Una tessera larga il doppio dell'altezza lascia al nome
        // lo spazio di leggersi, che con la copertina sopra e il nome sotto non
        // ci sarebbe.
        // In ricerca la griglia sale al posto del lettore e si ferma dove
        // comincia la tastiera; altrimenti sta sotto il lettore e arriva in
        // fondo.
        if (stretto) {
            // In ricerca l'elenco parte sempre sotto la barra: quello che
            // cambia e' dove finisce - sopra la tastiera, o in fondo allo
            // schermo quando la tastiera e' stata abbassata. Prima, con la
            // tastiera giu', si tornava alla misura del lettore e l'elenco
            // cominciava a meta' schermo con un buco sopra.
            if (tastieraGiu) {
                griglia.set(mg, sottoLaBarra + m.dp(6), w - mg, h - mg);
            } else {
                float altaTastiera = h * 0.34f;
                tastierino.misura(m, mg, h - altaTastiera, w - mg, h - mg * 0.4f);
                griglia.set(mg, sottoLaBarra + m.dp(6), w - mg,
                            tastierino.area().top - mg * 0.4f);
            }
        } else {
            griglia.set(mg, h * 0.53f, w - mg, h - mg);
        }
        passoX = griglia.width() / COLONNE;
        // Quante righe ci stiano lo decide l'altezza di una tessera, non un
        // numero fisso: con la tastiera aperta lo spazio e' la meta', e due
        // righe fisse davano tessere alte un palmo. La misura di riferimento e'
        // sempre lo schermo intero, cosi' una tessera resta grande uguale che
        // la tastiera ci sia o no.
        float altaTessera = Math.max(m.bersaglio * 1.4f, m.altezza * 0.115f);
        int righeTessere = Math.max(1, (int) (griglia.height() / altaTessera));
        passoY = griglia.height() / righeTessere;
        vuoto = mg * 0.4f;
        // La copertina alta quanto la tessera, meno un filo di bordo: come le
        // scorciatoie di Spotify. Piccola a meta' tessera - la versione di
        // prima - la griglia sembrava un elenco di nomi con un francobollo.
        bordoTessera = m.dp(6);
        latoQuadretto = (passoY - vuoto) - bordoTessera * 2f;
        // <b>Lo spazio del nome si misura, non si stima.</b> Prima era
        // "larghezza della tessera meno il quadretto meno due margini", che
        // dimenticava il bordo sopra e sotto il quadretto: veniva otto pixel
        // piu' larga del vero, e nomi come « Scostumatezza » uscivano dalla
        // tessera invece di accorciarsi.
        larghezzaTessera = Math.max(m.dp(20),
                (passoX - vuoto) - bordoTessera - latoQuadretto - m.dp(16) - m.dp(14));

        // L'elenco dei brani di una playlist vive nella stessa finestra della
        // griglia, ma con righe larghe il doppio: due colonne per quattro
        // righe, otto brani a schermo, e un titolo che ha lo spazio di
        // leggersi.
        passoBraniX = griglia.width() / COLONNE_BRANI;
        // Quante righe ci stanno, non quante ne avevamo deciso: con la tastiera
        // aperta lo spazio e' meno della meta', e righe alte un quarto di
        // quello spazio sarebbero due dita l'una.
        float altaRiga = Math.max(m.bersaglio * 1.35f, h * 0.086f);
        int righe = Math.max(2, (int) (griglia.height() / altaRiga));
        passoBraniY = griglia.height() / righe;
        latoCopertina = (passoBraniY - vuoto) * 0.74f;
        bordoRiga = ((passoBraniY - vuoto) - latoCopertina) / 2f;
        // 66 dp a destra: la durata piu' l'aria che la stacca dal titolo. Con
        // meno, « Where Is My Mind? - XFM Live Version » arrivava a toccare il
        // 3:44.
        larghezzaRiga = Math.max(m.dp(20), (passoBraniX - vuoto) - bordoRiga - latoCopertina
                - m.dp(10) - m.dp(66));

        // Il tasto « aggiorna », in fondo alla riga dell'etichetta.
        float latoAgg = m.bersaglio;
        float cyAgg = griglia.top - mg * 0.55f - corpoEtichetta * 0.35f;
        aggiorna.set(griglia.right - latoAgg, cyAgg - latoAgg / 2f,
                     griglia.right, cyAgg + latoAgg / 2f);

        // Il QR sta a destra dentro il pannello dell'accoppiamento, quadrato e
        // grande quanto l'altezza gli concede.
        float latoQr = Math.min(avviso.height() - mg * 2.4f, avviso.width() * 0.34f);
        riquadroQr.set(avviso.right - mg * 1.4f - latoQr, avviso.top + mg * 1.2f,
                       avviso.right - mg * 1.4f, avviso.top + mg * 1.2f + latoQr);
    }

    // ---- disegno ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (vetro == null || !vetro.vivo()) return;

        int stato = musica != null ? musica.stato() : Musica.SPENTA;
        aggiornaTinta();

        pEtichetta.setTextSize(corpoEtichetta);
        float yTitolo = barraRicerca.centerY() + corpoEtichetta * 0.36f;
        if (inRicerca) disegnaAllaMusica(c);
        if (inRicerca && profondita > 0) {
            // Freccia e nome sulla stessa riga della barra, a sinistra: e' il
            // posto dove si torna indietro in qualunque cosa si apra, ed e'
            // fuori dalla strada del dito che sta scrivendo.
            float xNome = disegnaIndietro(c, yTitolo, null, allaMusica.right + m.dp(6));
            pEtichetta.setColor(Tinte.TESTO);
            String titolo = pilaTitolo[profondita] != null ? pilaTitolo[profondita] : "";
            float spazio = barraRicerca.left - xNome - m.bersaglio - m.dp(10);
            String nome = rAvviso.in(pEtichetta, titolo,
                                     Math.max(m.dp(40), spazio), corpoEtichetta);
            c.drawText(nome, xNome, yTitolo, pEtichetta);

            float rTasto = corpoEtichetta * 0.5f;
            float xTasto = xNome + pEtichetta.measureText(nome) + m.dp(16) + rTasto;
            float cy = barraRicerca.centerY();
            riproduciTutto.set(xTasto - m.bersaglio * 0.5f, cy - m.bersaglio * 0.5f,
                               xTasto + m.bersaglio * 0.5f, cy + m.bersaglio * 0.5f);
            // « Riproduci tutto »: un tondo pieno del colore della sezione,
            // il tasto principale di questa schermata.
            pPieno.setColor(Tinte.MUSICA);
            c.drawCircle(xTasto, cy, rTasto * 1.7f, pPieno);
            pPieno.setColor(Tinte.TESTO);
            Icone.disegna(c, Icone.AVVIA, xTasto, cy, rTasto * 1.7f, pPieno);
        } else if (!inRicerca && stato != Musica.PRONTA) {
            // Il titolo grande della sezione, sulla riga della ricerca - ma
            // solo finche' la ricerca non c'e': quando c'e', la barra parte dal
            // margine e il titolo ci finirebbe sotto.
            c.drawText("Spotify", m.margine,
                    barraRicerca.centerY() - (pGrande.descent() + pGrande.ascent()) / 2f, pGrande);
        }
        if (stato == Musica.PRONTA) disegnaBarraRicerca(c);

        if (stato == Musica.PRONTA) {
            // Col poco spazio della tastiera il lettore non si disegna: sarebbe
            // una copertina schiacciata sopra due righe di risultati. Quello
            // che sta suonando lo si ritrova appena la tastiera si chiude.
            if (!stretto) disegnaLettore(c);
            if (inRicerca) { disegnaRicerca(c); if (!tastieraGiu) disegnaTastiera(c); }
            else if (dentroUnaPlaylist()) disegnaElenco(c);
            else disegnaGriglia(c);
        } else {
            disegnaAvviso(c, stato);
        }
    }

    // ---- la ricerca ---------------------------------------------------------

    /**
     * La barra di ricerca, in cima alla griglia, a destra.
     *
     * Sta a destra e non al centro perche' a sinistra c'e' gia' l'etichetta che
     * dice cosa si sta guardando, e perche' su uno schermo largo il pollice
     * destro ci arriva senza attraversare.
     */
    private void disegnaBarraRicerca(Canvas c) {
        // Il campo di ricerca di iOS: una capsula grigia piatta, non vetro.
        pPieno.setColor(inRicerca ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(barraRicerca, barraRicerca.height() / 2f,
                        barraRicerca.height() / 2f, pPieno);

        float r = corpoEtichetta * 0.42f;
        float cx = barraRicerca.left + m.dp(16) + r;
        float cy = barraRicerca.centerY();
        pComando.setColor(Tinte.TESTO_TENUE);
        Icone.disegna(c, Icone.CERCA, cx, cy, r * 2.6f, pComando);

        String testo;
        int colore;
        if (inRicerca && scritto.length() > 0) { testo = scritto; colore = Tinte.TESTO; }
        else if (inRicerca) { testo = "scrivi: cerco mentre scrivi"; colore = Tinte.TESTO_TENUE; }
        else { testo = "cerca una canzone o un artista"; colore = Tinte.TESTO_TENUE; }

        pSotto.setTextSize(corpoNomeTess);
        pSotto.setColor(colore);
        float xTesto = cx + r * 1.6f + m.dp(6);
        boolean conLaX = inRicerca && scritto.length() > 0;
        float fine = barraRicerca.right - m.dp(14) - (conLaX ? m.bersaglio : 0f);
        c.drawText(rSotto.in(pSotto, testo, fine - xTesto, corpoNomeTess),
                   xTesto, cy - (pSotto.descent() + pSotto.ascent()) / 2f, pSotto);
        if (conLaX) disegnaCancellaTesto(c);
        else cancellaTesto.setEmpty();
    }

    private void disegnaRicerca(Canvas c) {
        pEtichetta.setTextSize(corpoEtichetta);
        pEtichetta.setColor(Tinte.con(Tinte.MUSICA, 0xEE));

        if (risultati.length == 0) {
            String perche = notaRicerca != null ? notaRicerca
                    : "scrivi il nome di una canzone o di un artista";
            pSotto.setTextSize(corpoSotto);
            pSotto.setColor(Tinte.TESTO_TENUE);
            c.drawText(rSotto.in(pSotto, perche, griglia.width(), corpoSotto),
                       m.margine, griglia.top + corpoSotto * 1.6f, pSotto);
            return;
        }

        scorsa = Math.max(0f, Math.min(scorsa, scorsaMassimaRicerca()));
        c.save();
        c.clipRect(griglia);
        String inCorso = musica.ceUnBrano() ? musica.brano() : null;
        for (int i = 0; i < risultati.length; i++) {
            posizionaRiga(i);
            if (tessera.bottom < griglia.top || tessera.top > griglia.bottom) continue;
            Cerca.Trovato t = risultati[i];
            if (t.intestazione()) {
                pEtichetta.setTextSize(corpoEtichetta);
                pEtichetta.setColor(Tinte.TESTO_TENUE);
                c.drawText(t.titolo, tessera.left + m.dp(4),
                           tessera.centerY() + corpoEtichetta * 0.36f, pEtichetta);
                continue;
            }
            disegnaRiga(c, t.titolo, t.sotto, t.immagine, 0,
                        i == premutaTessera, inCorso != null && inCorso.equals(t.titolo));
        }
        c.restore();
        if (scorsaMassimaRicerca() > 0) disegnaScia(c, scorsaMassimaRicerca());
    }

    private void disegnaTastiera(Canvas c) {
        tastierino.disegna(c, vetro, m, Tinte.MUSICA);
    }

    private float scorsaMassimaRicerca() {
        return Math.max(0f, righeElenco * passoBraniY - griglia.height());
    }

    /** La freccia per tornare indietro, con l'etichetta accanto. La usano la
     *  playlist aperta e la ricerca. */
    private float disegnaIndietro(Canvas c, float yEtichetta, String etichetta) {
        return disegnaIndietro(c, yEtichetta, etichetta, m.margine);
    }

    private float disegnaIndietro(Canvas c, float yEtichetta, String etichetta, float da) {
        float lato = corpoEtichetta * 1.1f;
        float cx = da + lato * 0.5f, cy = yEtichetta - corpoEtichetta * 0.35f;
        indietro.set(da - m.dp(8), cy - m.bersaglio * 0.5f,
                     da + lato * 2f, cy + m.bersaglio * 0.5f);
        pComando.setColor(Tinte.MUSICA);
        pComando.setStyle(Paint.Style.STROKE);
        pComando.setStrokeWidth(Math.max(2f, lato * 0.14f));
        int s0 = c.save();
        c.translate(cx, cy);
        c.drawPath(freccia(lato), pComando);
        c.restoreToCount(s0);
        if (etichetta != null) {
            c.drawText(etichetta, da + lato * 1.8f, yEtichetta, pEtichetta);
        }
        return da + lato * 1.8f;
    }

    /**
     * Il tasto che riporta alla schermata principale della Musica.
     *
     * Da dentro un album dentro un artista dentro una ricerca, l'indietro
     * vorrebbe tre tocchi. Questo e' uno solo, ed e' la stessa nota della
     * barra delle sezioni: si riconosce senza leggerlo.
     */
    private void disegnaAllaMusica(Canvas c) {
        float r = corpoEtichetta * 0.62f;
        float cx = m.margine + r, cy = barraRicerca.centerY();
        allaMusica.set(cx - m.bersaglio * 0.5f, cy - m.bersaglio * 0.5f,
                       cx + m.bersaglio * 0.5f, cy + m.bersaglio * 0.5f);
        pPieno.setColor(Tinte.RIEMPIMENTO);
        c.drawCircle(cx, cy, r * 1.45f, pPieno);
        pComando.setColor(Tinte.MUSICA);
        Icone.disegna(c, Icone.MUSICA_PIENA, cx, cy, r * 1.5f, pComando);
    }

    /** La X dentro la barra: svuota quello che si e' scritto senza uscire. */
    private void disegnaCancellaTesto(Canvas c) {
        float r = corpoNomeTess * 0.42f;
        float cx = barraRicerca.right - m.dp(18) - r, cy = barraRicerca.centerY();
        cancellaTesto.set(cx - m.bersaglio * 0.5f, cy - m.bersaglio * 0.5f,
                          cx + m.bersaglio * 0.5f, cy + m.bersaglio * 0.5f);
        pComando.setColor(Tinte.TESTO_TENUE);
        Icone.disegna(c, Icone.CHIUDI, cx, cy, r * 2.4f, pComando);
    }

    /** true quando si sta guardando dentro una playlist invece dell'elenco. */
    private boolean dentroUnaPlaylist() {
        return preferiti != null && preferiti.aperta() != null;
    }

    /** L'indietro di sistema chiude la playlist aperta prima di fare
     *  qualunque altra cosa: e' quello che ci si aspetta da un elenco in cui si
     *  e' entrati. */
    @Override
    public boolean suIndietro() {
        if (profondita > 0) { tornaIndietro(); return true; }
        if (inRicerca) { chiudiRicerca(); return true; }
        if (!dentroUnaPlaylist()) return false;
        chiudiPlaylist();
        return true;
    }

    private void chiudiPlaylist() {
        if (preferiti != null) preferiti.chiudiElenco();
        apertaVoce = null;
        scorsa = 0f;
        invalidate();
    }

    /** La tinta del brano: quella della copertina se c'e', altrimenti quella
     *  della sezione. */
    private void aggiornaTinta() {
        String url = musica != null ? musica.copertina() : null;
        if (url == null) {
            tinta = Tinte.MUSICA;
            copertinaDellaTinta = null;
            return;
        }
        if (url.equals(copertinaDellaTinta)) return;
        Bitmap b = copertine != null ? copertine.prendi(url, (int) latoDisco) : null;
        if (b == null) return;              // arriva l'avviso, e si rifa' il giro
        tinta = Copertine.tintaDi(b, Tinte.MUSICA);
        copertinaDellaTinta = url;
    }

    // ---- il lettore --------------------------------------------------------

    private void disegnaLettore(Canvas c) {
        boolean suona = musica.staSuonando();
        boolean ceQualcosa = musica.ceUnBrano();

        vetro.pannello(c, pannello, m.raggio, tinta,
                       suona ? Tinte.VELO_ACCESO : Tinte.VELO_QUIETO);

        disegnaCopertina(c, ceQualcosa);

        float x = disco.right + m.margine * 1.2f;
        float y = disco.top + corpoTitolo * 0.9f;

        // Il vuoto si scrive piccolo. A corpo intero « niente in riproduzione »
        // era la cosa piu' vistosa della schermata: la sola frase grande, in
        // mezzo a un pannello, a dire che non stava succedendo niente.
        String titolo = ceQualcosa ? musica.brano() : "niente in riproduzione";
        float corpo = ceQualcosa ? corpoTitolo : corpoSotto;
        pTitolo.setColor(ceQualcosa ? Tinte.TESTO : Tinte.SPENTO);
        c.drawText(rTitolo.adatta(pTitolo, titolo, larghezzaTesto, corpo, corpo * 0.62f),
                   x, y, pTitolo);

        String sotto;
        if (ceQualcosa) {
            sotto = musica.artista();
            if (sotto == null) sotto = "";
            if (musica.album() != null && musica.album().length() > 0) {
                sotto = sotto.length() > 0 ? sotto + " · " + musica.album() : musica.album();
            }
        } else {
            sotto = "scegli una playlist, o manda qui la musica dal telefono";
        }
        pSotto.setColor(ceQualcosa ? Tinte.TESTO_MEDIO : Tinte.TESTO_TENUE);
        c.drawText(rSotto.in(pSotto, sotto, larghezzaTesto, corpoSotto),
                   x, y + corpoTitolo * 0.95f, pSotto);

        disegnaScorrimento(c, ceQualcosa);
        for (int i = 0; i < comandi.length; i++) disegnaComando(c, i, suona, ceQualcosa);
    }

    private void disegnaCopertina(Canvas c, boolean ceQualcosa) {
        Bitmap b = null;
        String url = musica.copertina();
        if (url != null && copertine != null) b = copertine.prendi(url, (int) latoDisco);

        float raggio = m.raggio * 0.9f;
        if (b != null) {
            // L'alone dietro il disco: e' quello che lo stacca dal pannello
            // senza disegnargli un bordo addosso.
            pPieno.setColor(Tinte.con(0xFF000000, 0x55));
            c.drawRoundRect(disco, raggio, raggio, pPieno);
            c.drawBitmap(b, null, disco, pImmagine);
        } else {
            // Senza copertina, un disco disegnato: due cerchi e un foro. Un
            // rettangolo vuoto al suo posto sembrerebbe un guasto.
            // Il segnaposto della copertina, grigio come quello di Musica.
            pPieno.setColor(0xFF2C2C2E);
            c.drawRoundRect(disco, raggio, raggio, pPieno);
            float cx = disco.centerX(), cy = disco.centerY();
            pComando.setStyle(Paint.Style.STROKE);
            pComando.setColor(Tinte.con(Tinte.TESTO_TENUE, 0x88));
            c.drawCircle(cx, cy, latoDisco * 0.30f, pComando);
            pPieno.setColor(Tinte.TESTO_TENUE);
            c.drawCircle(cx, cy, latoDisco * 0.06f, pPieno);
        }
    }

    private void disegnaScorrimento(Canvas c, boolean ceQualcosa) {
        float h = scorrimento.height();
        pPieno.setColor(Tinte.RIEMPIMENTO);
        c.drawRoundRect(scorrimento, h / 2f, h / 2f, pPieno);

        long durata = musica.durata();
        if (!ceQualcosa || durata <= 0) return;

        long posizione = musica.posizione();
        float quanto = Math.max(0f, Math.min(1f, posizione / (float) durata));
        appoggio.set(scorrimento.left, scorrimento.top,
                     scorrimento.left + scorrimento.width() * quanto, scorrimento.bottom);
        // Bianca, come la barra del brano nel Centro di Controllo.
        pPieno.setColor(Tinte.TESTO);
        c.drawRoundRect(appoggio, h / 2f, h / 2f, pPieno);
        // La pallina: e' quello che dice che la barra si puo' toccare.
        c.drawCircle(appoggio.right, scorrimento.centerY(), h * 1.3f, pPieno);

        pTempo.setColor(Tinte.TESTO_TENUE);
        pTempo.setTextAlign(Paint.Align.LEFT);
        c.drawText(orologio(posizione), scorrimento.left,
                   scorrimento.top - corpoTempo * 0.6f, pTempo);
        pTempo.setTextAlign(Paint.Align.RIGHT);
        c.drawText(orologio(durata), scorrimento.right,
                   scorrimento.top - corpoTempo * 0.6f, pTempo);
        pTempo.setTextAlign(Paint.Align.LEFT);
    }

    private static String orologio(long millisecondi) {
        long secondi = Math.max(0, millisecondi / 1000);
        return (secondi / 60) + ":" + (secondi % 60 < 10 ? "0" : "") + (secondi % 60);
    }

    private void disegnaComando(Canvas c, int i, boolean suona, boolean ceQualcosa) {
        RectF b = comandi[i];
        float cx = b.centerX(), cy = b.centerY(), r = b.width() * 0.22f;
        boolean principale = (i == 1);

        // Come il Centro di Controllo: il tasto di mezzo e' un cerchio bianco
        // pieno col segno nero, gli altri cerchi grigi col segno bianco.
        if (principale) {
            pPieno.setColor(premutoComando == 1 ? 0xFFD1D1D6 : 0xFFFFFFFF);
            c.drawCircle(cx, cy, b.width() * 0.5f, pPieno);
        } else {
            pPieno.setColor(premutoComando == i ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
            c.drawCircle(cx, cy, b.width() * 0.42f, pPieno);
        }

        boolean acceso = (i == 3) && musica.mischiata();
        int colore;
        if (principale) colore = Tinte.TESTO_SU_CHIARO;
        else if (acceso) colore = Tinte.MUSICA;
        else if (i <= 2 && !ceQualcosa) colore = Tinte.SPENTO;
        else colore = Tinte.TESTO;
        pComando.setColor(colore);
        pPieno.setColor(colore);

        // Le icone sono quelle dei Material Symbols, come nella Home e nella
        // Radio: lo stesso « avanti » in tutte e tre le schermate. Prima erano
        // tre disegni a mano diversi, e si vedeva.
        int segno;
        switch (i) {
            case 0:  segno = Icone.PRECEDENTE; break;
            // Due barre e non un quadrato: qui la pausa e' una pausa e il brano
            // riprende da dove stava. Il quadrato dice « stop », che e' un'altra
            // cosa - e infatti la radio, che e' un flusso in diretta, usa quello.
            case 1:  segno = suona ? Icone.PAUSA : Icone.AVVIA; break;
            case 2:  segno = Icone.SUCCESSIVO; break;
            case 3:  segno = Icone.CASUALE; break;
            case 4:  segno = Icone.VOLUME_MENO; break;
            default: segno = Icone.VOLUME_PIU; break;
        }
        Icone.disegna(c, segno, cx, cy, b.width() * (principale ? 0.44f : 0.36f), pComando);
        pComando.setStyle(Paint.Style.STROKE);
    }

    // ---- la griglia delle playlist -----------------------------------------

    private Preferiti.Voce[] voci() {
        return preferiti != null ? preferiti.tutte() : new Preferiti.Voce[0];
    }

    private float scorsaMassima() {
        int quante = voci().length;
        int righe = (quante + COLONNE - 1) / COLONNE;
        return Math.max(0f, righe * passoY - griglia.height());
    }

    private void disegnaGriglia(Canvas c) {
        Preferiti.Voce[] voci = voci();

        pEtichetta.setTextSize(corpoEtichetta);
        pEtichetta.setColor(Tinte.TESTO_TENUE);
        String etichetta = voci.length > 0
                ? "Le tue playlist · " + voci.length
                : "Playlist";
        c.drawText(etichetta, m.margine, griglia.top - m.margine * 0.55f, pEtichetta);
        disegnaAggiorna(c);

        if (voci.length == 0) {
            String perche = preferiti != null && preferiti.nota() != null
                    ? preferiti.nota() : "sto guardando cosa c'e' sul tuo account…";
            pSotto.setColor(Tinte.TESTO_TENUE);
            c.drawText(rSotto.in(pSotto, perche, getWidth() - m.margine * 2f, corpoSotto),
                       m.margine, griglia.top + corpoSotto * 1.6f, pSotto);
            return;
        }

        scorsa = Math.max(0f, Math.min(scorsa, scorsaMassima()));

        // Si ritaglia sulla finestra: le righe che escono in basso non devono
        // disegnarsi sopra il lettore quando la griglia scorre.
        c.save();
        c.clipRect(griglia);
        for (int i = 0; i < voci.length; i++) {
            posizionaTessera(i);
            if (tessera.bottom < griglia.top || tessera.top > griglia.bottom) continue;
            disegnaTessera(c, i, voci[i], i == premutaTessera);
        }
        c.restore();

        if (scorsaMassima() > 0) disegnaScia(c, scorsaMassima());
    }

    // ---- dentro una playlist ------------------------------------------------

    private float scorsaMassimaBrani() {
        int quanti = preferiti != null ? preferiti.tracce().length : 0;
        int righe = (quanti + COLONNE_BRANI - 1) / COLONNE_BRANI;
        return Math.max(0f, righe * passoBraniY - griglia.height());
    }

    private void disegnaElenco(Canvas c) {
        Preferiti.Traccia[] brani = preferiti.tracce();

        // In cima, la freccia e il nome: la freccia e' l'unica cosa che dice
        // che da qui si torna, e su una Home senza barre di sistema il tasto
        // indietro non lo vede nessuno.
        float yEtichetta = griglia.top - m.margine * 0.55f;
        pEtichetta.setTextSize(corpoEtichetta);
        pEtichetta.setColor(Tinte.TESTO);
        float lato = corpoEtichetta * 1.1f;
        float cx = m.margine + lato * 0.5f, cy = yEtichetta - corpoEtichetta * 0.35f;
        indietro.set(m.margine - m.dp(8), cy - m.bersaglio * 0.5f,
                     m.margine + lato * 2f, cy + m.bersaglio * 0.5f);
        pComando.setColor(Tinte.MUSICA);
        pComando.setStyle(Paint.Style.STROKE);
        pComando.setStrokeWidth(Math.max(2f, lato * 0.14f));
        int s0 = c.save();
        c.translate(cx, cy);
        c.drawPath(freccia(lato), pComando);
        c.restoreToCount(s0);

        String nome = apertaVoce != null ? apertaVoce.nome : "Playlist";
        String scritto = rSotto.in(pEtichetta, nome,
                                   griglia.width() * 0.6f, corpoEtichetta);
        float xNome = m.margine + lato * 1.8f;
        c.drawText(scritto, xNome, yEtichetta, pEtichetta);

        // « Riproduci tutta », subito dopo il nome: aprire una playlist non la
        // fa partire - si guarda senza interrompere quello che suona - quindi
        // ci vuole un posto dove dire "sì, tutta".
        float rTasto = corpoEtichetta * 0.5f;
        float xTasto = xNome + pEtichetta.measureText(scritto) + m.dp(18) + rTasto;
        riproduciTutto.set(xTasto - m.bersaglio * 0.5f, cy - m.bersaglio * 0.5f,
                           xTasto + m.bersaglio * 0.5f, cy + m.bersaglio * 0.5f);
        pPieno.setColor(Tinte.MUSICA);
        c.drawCircle(xTasto, cy, rTasto * 1.7f, pPieno);
        pPieno.setColor(Tinte.TESTO);
        Icone.disegna(c, Icone.AVVIA, xTasto, cy, rTasto * 1.7f, pPieno);

        // Anche qui dentro: se una playlist e' stata cambiata dal telefono,
        // quello che serve rileggere sono i suoi brani, non l'elenco.
        disegnaAggiorna(c);

        if (brani.length == 0) {
            String perche = preferiti.notaTracce() != null
                    ? preferiti.notaTracce() : "sto leggendo i brani…";
            pSotto.setTextSize(corpoSotto);
            pSotto.setColor(Tinte.TESTO_TENUE);
            c.drawText(rSotto.in(pSotto, perche, griglia.width(), corpoSotto),
                       m.margine, griglia.top + corpoSotto * 1.6f, pSotto);
            return;
        }

        scorsa = Math.max(0f, Math.min(scorsa, scorsaMassimaBrani()));

        c.save();
        c.clipRect(griglia);
        String inCorso = musica.ceUnBrano() ? musica.brano() : null;
        for (int i = 0; i < brani.length; i++) {
            posizionaRiga(i);
            if (tessera.bottom < griglia.top || tessera.top > griglia.bottom) continue;
            disegnaRiga(c, i, brani[i], i == premutaTessera,
                        inCorso != null && inCorso.equals(brani[i].titolo));
        }
        c.restore();

        if (scorsaMassimaBrani() > 0) disegnaScia(c, scorsaMassimaBrani());
    }

    /**
     * Il tasto « aggiorna »: un tondo con la freccia che gira mentre chiede.
     *
     * Gira ruotando il <b>Canvas</b> e non ridisegnando l'icona a mano. Qui
     * ruotare e' innocuo, mentre ingrandire non lo sarebbe: {@link Icone} porta
     * il percorso alla grandezza vera <i>prima</i> di disegnarlo, quindi quando
     * la rotazione lo tocca e' gia' largo trenta pixel - non un quadratino da
     * un pixel stirato, che su questa GPU sparisce (vedi il commento in
     * {@link Icone#disegna}).
     */
    private void disegnaAggiorna(Canvas c) {
        boolean chiede = preferiti != null && preferiti.inCorso();
        float cx = aggiorna.centerX(), cy = aggiorna.centerY();
        // Il tondo e' quasi mezzo bersaglio e l'icona e' quella della scala.
        // Legato al corpo dell'etichetta veniva un dischetto da trentaquattro
        // pixel con dentro un segno da diciotto: si prendeva - il bersaglio e'
        // sempre stato intero - ma non si vedeva, e un comando che non si vede
        // e' un comando che non c'e'.
        float r = m.bersaglio * 0.40f;

        pPieno.setColor(premutoAggiorna ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawCircle(cx, cy, r, pPieno);
        pComando.setColor(chiede ? Tinte.MUSICA : Tinte.TESTO);

        if (chiede) {
            int salvato = c.save();
            c.rotate((Anima.ora() % 900L) * 0.4f, cx, cy);
            Icone.disegna(c, Icone.SINCRONIZZA, cx, cy, m.icona * 0.92f, pComando);
            c.restoreToCount(salvato);
            postInvalidateOnAnimation();
        } else {
            Icone.disegna(c, Icone.SINCRONIZZA, cx, cy, m.icona * 0.92f, pComando);
        }
    }

    private void posizionaRiga(int i) {
        int col, riga;
        if (inRicerca && i < colonnaDi.length) { col = colonnaDi[i]; riga = rigaDi[i]; }
        else { col = i % COLONNE_BRANI; riga = i / COLONNE_BRANI; }

        float alto = griglia.top + passoBraniY * riga + vuoto / 2f - scorsa;
        if (col < 0) {
            // Un'intestazione: tutta la larghezza, e piu' bassa di una riga -
            // e' un'etichetta, non una cosa da toccare.
            tessera.set(griglia.left, alto, griglia.right, alto + passoBraniY - vuoto);
            return;
        }
        float sinistra = griglia.left + passoBraniX * col + vuoto / 2f;
        tessera.set(sinistra, alto, sinistra + passoBraniX - vuoto, alto + passoBraniY - vuoto);
    }

    private void disegnaRiga(Canvas c, int i, Preferiti.Traccia t, boolean premuta, boolean suona) {
        disegnaRiga(c, t.titolo, t.artista, t.immagine, t.durata, premuta, suona);
    }

    /** Una riga dell'elenco: copertina, titolo, sotto-titolo e durata. La usano
     *  i brani di una playlist e i risultati della ricerca, che a schermo sono
     *  la stessa cosa. */
    private void disegnaRiga(Canvas c, String titolo, String sotto, String immagine,
                             long durata, boolean premuta, boolean suona) {
        // Un riempimento piatto; il brano che suona diventa chiaro, come una
        // tessera accesa nella Casa di Apple.
        pPieno.setColor(suona ? Tinte.TESSERA_ACCESA
                : (premuta ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO));
        c.drawRoundRect(tessera, m.raggio * 0.7f, m.raggio * 0.7f, pPieno);

        quadretto.set(tessera.left + bordoRiga, tessera.top + bordoRiga,
                      tessera.left + bordoRiga + latoCopertina,
                      tessera.top + bordoRiga + latoCopertina);
        Bitmap b = immagine != null && copertine != null
                ? copertine.prendi(immagine, (int) latoCopertina) : null;
        float raggio = latoCopertina * 0.16f;
        if (b != null) {
            c.drawBitmap(b, null, quadretto, pImmagine);
        } else {
            pPieno.setColor(0xFF2C2C2E);
            c.drawRoundRect(quadretto, raggio, raggio, pPieno);
        }

        float x = quadretto.right + m.dp(10);
        float mezzo = tessera.centerY();
        pNomeTess.setColor(suona ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO);
        c.drawText(rNome.in(pNomeTess, titolo, larghezzaRiga, corpoNomeTess),
                   x, mezzo - corpoNomeTess * 0.15f, pNomeTess);

        pTempo.setTextSize(corpoTempo);
        pTempo.setColor(suona ? Tinte.TESTO_SU_CHIARO_TENUE : Tinte.TESTO_TENUE);
        pTempo.setTextAlign(Paint.Align.LEFT);
        c.drawText(rSotto.in(pTempo, sotto != null ? sotto : "", larghezzaRiga, corpoTempo),
                   x, mezzo + corpoTempo * 1.25f, pTempo);

        if (durata > 0) {
            pTempo.setTextSize(corpoTempo);
            pTempo.setTextAlign(Paint.Align.RIGHT);
            c.drawText(orologio(durata), tessera.right - m.dp(12),
                       mezzo + corpoTempo * 0.35f, pTempo);
            pTempo.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Dove sta la tessera i, tenuto conto di quanto si e' scorso. */
    private void posizionaTessera(int i) {
        int col = i % COLONNE, riga = i / COLONNE;
        float sinistra = griglia.left + passoX * col + vuoto / 2f;
        float alto = griglia.top + passoY * riga + vuoto / 2f - scorsa;
        tessera.set(sinistra, alto, sinistra + passoX - vuoto, alto + passoY - vuoto);
    }

    /** Una barretta a destra che dice che c'e' altro sotto. Un elenco che
     *  scorre senza dirlo e' un elenco che nessuno prova a scorrere. */
    private void disegnaScia(Canvas c, float massima) {
        float altezzaTotale = griglia.height() + massima;
        float alta = griglia.height() * (griglia.height() / altezzaTotale);
        float dove = griglia.top + (griglia.height() - alta) * (scorsa / massima);
        float larga = m.dp(3);
        appoggio.set(griglia.right - larga, dove, griglia.right, dove + alta);
        pPieno.setColor(Tinte.con(Tinte.TESTO, 0x55));
        c.drawRoundRect(appoggio, larga / 2f, larga / 2f, pPieno);
    }

    private void disegnaTessera(Canvas c, int i, Preferiti.Voce v, boolean premuta) {
        boolean suona = musica.ceUnBrano() && v.uri != null && v.uri.equals(musica.contesto());
        pPieno.setColor(suona ? Tinte.TESSERA_ACCESA
                : (premuta ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO));
        c.drawRoundRect(tessera, m.raggio * 0.8f, m.raggio * 0.8f, pPieno);

        // La copertina occupa quasi tutta l'altezza della tessera, come nelle
        // scorciatoie di Spotify: e' quella che si riconosce da lontano, il
        // nome si legge dopo.
        float bordo = m.dp(6);
        float lato = tessera.height() - bordo * 2f;
        quadretto.set(tessera.left + bordo, tessera.top + bordo,
                      tessera.left + bordo + lato, tessera.bottom - bordo);

        Bitmap b = v.immagine != null && copertine != null
                ? copertine.prendi(v.immagine, (int) latoQuadretto) : null;
        float raggio = m.raggioPiccolo * 0.8f;
        if (b != null) {
            c.drawBitmap(b, null, quadretto, pImmagine);
        } else {
            // Senza immagine, un quadrato con l'iniziale. Il colore non e'
            // sempre lo stesso: viene dal nome, cosi' due playlist senza
            // copertina restano due cose diverse invece di due rettangoli
            // uguali.
            int suo = Tinte.addomestica(coloreDa(v.nome));
            pPieno.setColor(suo);
            c.drawRoundRect(quadretto, raggio, raggio, pPieno);
            pIniziale.setTextSize(lato * 0.40f);
            pIniziale.setColor(Tinte.con(0xFF0B0F14, 0xDD));
            c.drawText(iniziale(v.nome), quadretto.centerX(),
                       quadretto.centerY() - (pIniziale.descent() + pIniziale.ascent()) / 2f,
                       pIniziale);
        }

        pNomeTess.setColor(suona ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO);
        String nome = rNome.in(pNomeTess, v.nome, larghezzaTessera, corpoNomeTess);
        float base = tessera.centerY() - (pNomeTess.descent() + pNomeTess.ascent()) / 2f;
        c.drawText(nome, quadretto.right + m.dp(16), base, pNomeTess);
    }

    private static String iniziale(String nome) {
        if (nome == null || nome.length() == 0) return "?";
        return nome.substring(0, 1).toUpperCase();
    }

    /** Un colore dal nome: stesso nome, stesso colore, sempre. */
    private static int coloreDa(String nome) {
        int h = nome == null ? 0 : nome.hashCode();
        float[] hsv = { Math.abs(h % 360), 0.6f, 0.9f };
        return Color.HSVToColor(hsv);
    }

    // ---- avvio, codice, guasto ---------------------------------------------

    private void disegnaAvviso(Canvas c, int stato) {
        boolean accoppia = stato == Musica.ACCOPPIA;
        int colore = stato == Musica.GUASTO ? Tinte.ALLARME : Tinte.MUSICA;
        vetro.pannello(c, avviso, m.raggio, colore, Tinte.VELO_QUIETO);

        float x = avviso.left + m.margine * 1.4f;
        float y = avviso.top + m.margine * 1.4f + corpoSotto;

        pEtichetta.setTextSize(corpoEtichetta);
        pEtichetta.setColor(colore);
        c.drawText(intestazione(stato), x, y, pEtichetta);

        if (accoppia) { disegnaAccoppiamento(c, x, y); return; }

        float larghezza = avviso.width() - m.margine * 2.8f;
        pTitolo.setColor(Tinte.TESTO);
        c.drawText(rAvviso.adatta(pTitolo, titoloAvviso(stato), larghezza,
                                  corpoTitolo, corpoTitolo * 0.6f),
                   x, y + corpoTitolo * 1.2f, pTitolo);

        pSotto.setTextSize(corpoSotto);
        pSotto.setColor(Tinte.TESTO_TENUE);
        String[] righe = bSpiega.in(pSotto, spiegazione(stato), larghezza, corpoSotto, 3);
        float yr = y + corpoTitolo * 2.3f;
        for (String riga : righe) {
            c.drawText(riga, x, yr, pSotto);
            yr += corpoSotto * 1.35f;
        }

        if (stato == Musica.GUASTO || stato == Musica.SPENTA) disegnaBottone(c, "riprova", colore);
    }

    private String intestazione(int stato) {
        switch (stato) {
            case Musica.ACCOPPIA: return "Ancora una cosa";
            case Musica.GUASTO:   return "Non ci siamo";
            default:              return "Spotify";
        }
    }

    private String titoloAvviso(int stato) {
        switch (stato) {
            case Musica.GUASTO: {
                String e = musica != null ? musica.errore() : null;
                return e != null ? e : "la musica non parte";
            }
            case Musica.AVVIO: return "Caricamento…";
            default:           return "Spotify e' spento";
        }
    }

    private String spiegazione(int stato) {
        switch (stato) {
            case Musica.GUASTO:
                return "Serve un account Spotify Premium. Se c'e', riprova.";
            case Musica.AVVIO:
                // Niente spiegazioni mentre carica: chi guarda vuole sapere
                // che sta arrivando, non come e' fatto dentro.
                return "";
            default:
                return "Tocca per accendere. La musica esce da qui, e non serve "
                     + "nessuna app.";
        }
    }

    /**
     * L'accoppiamento: il QR a destra, il codice a sinistra.
     *
     * <b>Due strade per la stessa cosa</b>, perche' costano poco tutte e due e
     * falliscono in momenti diversi. Il QR si inquadra e porta dritti alla
     * pagina con il codice gia' dentro: e' la strada di chi ha il telefono in
     * mano. Il codice scritto grande serve a chi il telefono ce l'ha di la',
     * o a chi la fotocamera non la vuole aprire - e comunque e' la conferma
     * che quello che si sta approvando e' proprio questo tablet.
     *
     * Succede una volta nella vita del tablet: dopo, le credenziali stanno in
     * {@code credentials.json} e questa schermata non si rivede piu'.
     */
    private void disegnaAccoppiamento(Canvas c, float x, float y) {
        String codice = musica.codice();
        String dove = musica.indirizzoCodice();
        float larghezza = riquadroQr.left - m.margine - x;

        pSotto.setTextSize(corpoSotto);
        pSotto.setColor(Tinte.TESTO);
        c.drawText(rAvviso.in(pSotto, "Inquadra il codice col telefono", larghezza, corpoSotto),
                   x, y + corpoSotto * 1.8f, pSotto);

        pSotto.setTextSize(corpoSotto * 0.85f);
        pSotto.setColor(Tinte.TESTO_TENUE);
        c.drawText("oppure apri spotify.com/pair e scrivi:",
                   x, y + corpoSotto * 3.2f, pSotto);

        if (codice != null) {
            pCodice.setColor(Tinte.TESTO);
            // Le lettere respirano: un codice tutto attaccato si ricopia male,
            // e questo si ricopia una volta sola ma va fatto giusto.
            pCodice.setLetterSpacing(0.18f);
            c.drawText(codice, x, y + corpoSotto * 3.2f + corpoCodice * 1.25f, pCodice);
            pCodice.setLetterSpacing(0f);
        }

        pSotto.setTextSize(corpoSotto * 0.8f);
        pSotto.setColor(Tinte.TESTO_TENUE);
        c.drawText("succede una volta sola: poi Assistente Home se lo ricorda.",
                   x, avviso.bottom - m.margine * 1.2f, pSotto);

        disegnaQr(c, dove);
    }

    private void disegnaQr(Canvas c, String indirizzo) {
        if (indirizzo == null) return;
        if (!indirizzo.equals(indirizzoDelQr)) {
            qr = Qr.per(indirizzo);
            indirizzoDelQr = indirizzo;
        }
        if (qr == null) return;

        // Fondo bianco pieno e un contorno di quiete: un QR disegnato sul vetro
        // scuro non lo legge nessuno - i lettori cercano nero su bianco, e il
        // margine attorno fa parte del codice quanto i quadratini.
        pPieno.setColor(0xFFFFFFFF);
        c.drawRoundRect(riquadroQr, m.raggio * 0.5f, m.raggio * 0.5f, pPieno);

        int quanti = qr.length;
        float quiete = riquadroQr.width() * 0.07f;
        float modulo = (riquadroQr.width() - quiete * 2f) / quanti;
        float x0 = riquadroQr.left + quiete, y0 = riquadroQr.top + quiete;

        pPieno.setColor(0xFF000000);
        for (int riga = 0; riga < quanti; riga++) {
            for (int col = 0; col < quanti; col++) {
                if (!qr[riga][col]) continue;
                // Mezzo pixel di sovrapposizione: senza, fra un modulo e
                // l'altro resta una riga chiara di antialiasing e il codice si
                // legge peggio da lontano.
                c.drawRect(x0 + col * modulo, y0 + riga * modulo,
                           x0 + col * modulo + modulo + 0.5f,
                           y0 + riga * modulo + modulo + 0.5f, pPieno);
            }
        }
    }

    /** Il bottone del riprova, in fondo al pannello dell'avviso. */
    private void disegnaBottone(Canvas c, String scritta, int colore) {
        RectF b = bottone();
        // Il bottone pieno di iOS: capsula del colore, scritta bianca.
        pPieno.setColor(premutoAvviso ? Tinte.con(colore, 0xB0) : colore);
        c.drawRoundRect(b, b.height() / 2f, b.height() / 2f, pPieno);
        pSotto.setTextSize(corpoSotto);
        pSotto.setColor(Tinte.TESTO);
        pSotto.setTextAlign(Paint.Align.CENTER);
        c.drawText(scritta, b.centerX(),
                   b.centerY() - (pSotto.descent() + pSotto.ascent()) / 2f, pSotto);
        pSotto.setTextAlign(Paint.Align.LEFT);
    }

    private RectF bottone() {
        float larghezza = Math.max(m.bersaglio * 3.4f, avviso.width() * 0.20f);
        float altezza = Math.max(m.bersaglio, avviso.height() * 0.14f);
        appoggio.set(avviso.left + m.margine * 1.4f,
                     avviso.bottom - m.margine * 1.2f - altezza,
                     avviso.left + m.margine * 1.4f + larghezza,
                     avviso.bottom - m.margine * 1.2f);
        return appoggio;
    }

    // ---- tocco --------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int stato = musica != null ? musica.stato() : Musica.SPENTA;
        float x = e.getX(), y = e.getY();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (inRicerca && !tastieraGiu && tastierino.area().contains(x, y)) {
                    tastierino.premi(x, y);
                    invalidate();
                    return true;
                }
                if (stato == Musica.PRONTA) {
                    premutoAggiorna = !inRicerca && aggiorna.contains(x, y);
                    premutoComando = stretto ? -1 : quale(comandi, x, y);
                    premutaTessera = premutoComando >= 0 ? -1 : qualeTessera(x, y);
                    trascinando = false;
                    dallaY = y;
                    dallaScorsa = scorsa;
                } else {
                    premutoAvviso = avviso.contains(x, y);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                // Si scorre solo dentro la griglia, e solo dopo che il dito si
                // e' mosso piu' della soglia di sistema: sotto quella e' un
                // tocco fermo, e trattarlo come trascinamento vorrebbe dire che
                // premere una tessera non la apre mai.
                if (stato == Musica.PRONTA && premutoComando < 0
                        && scorribile() > 0 && griglia.contains(x, dallaY)) {
                    if (!trascinando && Math.abs(y - dallaY) > soglia) trascinando = true;
                    if (trascinando) {
                        premutaTessera = -1;
                        scorsa = Math.max(0f, Math.min(scorribile(),
                                                       dallaScorsa - (y - dallaY)));
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (inRicerca && !tastieraGiu && tastierino.area().contains(x, y)) {
                    tastierino.lascia();
                    int tasto = tastierino.tocco(x, y);
                    if (tasto == Tastierino.CHIUDI) { tastieraGiu = true; misura(); invalidate(); }
                    else scrivi(tasto);
                    return true;
                }
                if (stato == Musica.PRONTA && !trascinando) {
                    int cmd = stretto ? -1 : quale(comandi, x, y);
                    int tess = qualeTessera(x, y);
                    if (cmd >= 0 && cmd == premutoComando) suComando(cmd);
                    else if (inRicerca && !cancellaTesto.isEmpty()
                             && cancellaTesto.contains(x, y)) {
                        scritto = "";
                        risultati = new Cerca.Trovato[0];
                        notaRicerca = null;
                        removeCallbacks(fraPoco);
                        tastieraGiu = false;
                        giroApertura++;
                        profondita = 0;
                        misura();
                    }
                    else if (inRicerca && allaMusica.contains(x, y)) chiudiRicerca();
                    else if (barraRicerca.contains(x, y)) {
                        // Toccare la barra mentre si sta gia' cercando svuota
                        // quello che c'e' scritto: e' il gesto con cui si
                        // ricomincia, e la tastiera e' gia' li' sotto.
                        if (inRicerca && tastieraGiu) { tastieraGiu = false; misura(); }
                        else if (inRicerca) { scritto = ""; risultati = new Cerca.Trovato[0]; }
                        else apriRicerca();
                    }
                    else if (inRicerca && indietro.contains(x, y)) {
                        if (profondita > 0) tornaIndietro(); else chiudiRicerca();
                    }
                    else if (inRicerca && profondita > 0 && riproduciTutto.contains(x, y)) {
                        // Il tasto accanto al nome suona quello che si sta
                        // guardando: la radio dell'artista, o l'album intero.
                        musica.suona(pilaUri[profondita]);
                    }
                    else if (dentroUnaPlaylist() && indietro.contains(x, y)) chiudiPlaylist();
                    else if (dentroUnaPlaylist() && riproduciTutto.contains(x, y)) {
                        if (apertaVoce != null) musica.suona(apertaVoce.uri);
                    }
                    else if (premutoAggiorna && aggiorna.contains(x, y)) rileggi();
                    else if (tess >= 0 && tess == premutaTessera) suTessera(tess);
                    else if (dentroLaBarra(x, y)) cerca(x);
                } else if (premutoAvviso && avviso.contains(x, y)
                           && (stato == Musica.SPENTA || stato == Musica.GUASTO)
                           && musica != null) {
                    // Tutto il pannello e' il bersaglio, non solo il bottone
                    // disegnato: e' un apparecchio che si tocca in piedi, e un
                    // bersaglio grande quanto lo spazio disponibile e' gratis.
                    musica.avvia();
                }
                premutoComando = premutaTessera = -1;
                premutoAvviso = premutoAggiorna = false;
                trascinando = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                premutoComando = premutaTessera = -1;
                premutoAvviso = premutoAggiorna = false;
                trascinando = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** La barra e' alta pochi pixel: il bersaglio e' una fascia attorno, o non
     *  la prende nessuno. */
    private boolean dentroLaBarra(float x, float y) {
        return musica.ceUnBrano() && musica.durata() > 0
                && x >= scorrimento.left && x <= scorrimento.right
                && Math.abs(y - scorrimento.centerY()) <= m.bersaglio * 0.5f;
    }

    private void cerca(float x) {
        float quanto = (x - scorrimento.left) / scorrimento.width();
        musica.vaiA((long) (Math.max(0f, Math.min(1f, quanto)) * musica.durata()));
    }

    private void suComando(int i) {
        if (musica == null) return;
        switch (i) {
            case 0: musica.precedente(); break;
            case 1: musica.pausaRiprendi(); break;
            case 2: musica.successivo(); break;
            case 3: musica.mischia(!musica.mischiata()); break;
            case 4: volume(AudioManager.ADJUST_LOWER); break;
            default: volume(AudioManager.ADJUST_RAISE); break;
        }
        invalidate();
    }

    /**
     * Un tocco su una tessera <b>apre</b> la playlist, non la fa partire.
     *
     * Nell'altro verso non si potrebbe guardare cosa c'e' dentro senza
     * interrompere quello che sta suonando, e guardare e' proprio la cosa per
     * cui si tocca. Per farla partire tutta c'e' il tasto accanto al nome,
     * dentro; per partire da un brano preciso, si tocca quel brano.
     */
    private void suTessera(int i) {
        if (musica == null) return;
        if (inRicerca) {
            if (i >= risultati.length) return;
            Cerca.Trovato t = risultati[i];
            if (t.intestazione()) return;
            // Un artista o un album non si suonano: si aprono, e dentro c'e' un
            // altro elenco - che e' quello che uno si aspetta toccando un nome.
            // Per sentirli e basta c'e' il tasto accanto al titolo.
            if (t.apribile()) { apriDentro(t); return; }
            // Dentro un album il brano si suona nel suo album: cosi' finito
            // quello continua col successivo invece di fermarsi li'.
            if (profondita > 0 && pilaUri[profondita] != null
                    && pilaUri[profondita].startsWith("spotify:album:")) {
                musica.suona(pilaUri[profondita], t.uri);
            } else {
                musica.suona(t.uri);
            }
            // Scelto il brano, la ricerca ha finito il suo mestiere: via la
            // tastiera, via l'elenco, e si torna a guardare quello che suona.
            // Restare con la tastiera aperta sopra una canzone appena avviata
            // e' il momento in cui uno cerca il tasto per uscire.
            chiudiRicerca();
            return;
        }
        if (dentroUnaPlaylist()) {
            Preferiti.Traccia[] brani = preferiti.tracce();
            if (i >= brani.length || apertaVoce == null) return;
            musica.suona(apertaVoce.uri, brani[i].uri);
        } else {
            Preferiti.Voce[] voci = voci();
            if (i >= voci.length || preferiti == null) return;
            apertaVoce = voci[i];
            scorsa = 0f;
            preferiti.apri(musica, voci[i]);
        }
        invalidate();
    }

    /** Di quanto si puo' scorrere quello che si sta guardando adesso. */
    private float scorribile() {
        if (inRicerca) return scorsaMassimaRicerca();
        return dentroUnaPlaylist() ? scorsaMassimaBrani() : scorsaMassima();
    }

    /**
     * Rilegge da Spotify quello che si sta guardando adesso.
     *
     * Dentro una playlist sono i suoi brani, fuori e' l'elenco delle playlist:
     * il tasto e' lo stesso e sta nello stesso posto, ma « aggiorna » vuol dire
     * quello che si ha davanti - chi ha appena aggiunto una canzone da telefono
     * sta guardando quella playlist, non l'elenco.
     */
    private void rileggi() {
        if (preferiti == null || musica == null) return;
        if (dentroUnaPlaylist() && apertaVoce != null) preferiti.apri(musica, apertaVoce);
        else preferiti.rinfresca(musica);
    }

    private void volume(int direzione) {
        if (audio == null) return;
        for (int k = 0; k < 2; k++) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direzione, 0);
        }
    }

    private int qualeTessera(float x, float y) {
        if (!griglia.contains(x, y)) return -1;
        if (inRicerca) {
            for (int i = 0; i < risultati.length; i++) {
                posizionaRiga(i);
                if (tessera.contains(x, y)) return i;
            }
            return -1;
        }
        if (dentroUnaPlaylist()) {
            int quanti = preferiti.tracce().length;
            for (int i = 0; i < quanti; i++) {
                posizionaRiga(i);
                if (tessera.contains(x, y)) return i;
            }
            return -1;
        }
        Preferiti.Voce[] voci = voci();
        for (int i = 0; i < voci.length; i++) {
            posizionaTessera(i);
            if (tessera.contains(x, y)) return i;
        }
        return -1;
    }

    private static int quale(RectF[] aree, float x, float y) {
        for (int i = 0; i < aree.length; i++) if (aree[i].contains(x, y)) return i;
        return -1;
    }
}
