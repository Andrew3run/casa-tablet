package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.view.MotionEvent;

import java.util.Calendar;
import java.util.Locale;

/**
 * La schermata che si vede senza toccare niente: che ore sono, cosa suona, cosa
 * e' acceso.
 *
 * Eredita da OrologioView le due cose che li' erano state fatte bene e che
 * qui valgono uguale: <b>nessun oggetto creato dentro onDraw</b>, e il tick al
 * secondo che ridisegna <b>solo quando il testo cambia davvero</b> - su uno
 * schermo acceso sedici ore al giorno, i redraw inutili sono batteria buttata -
 * riallineato al secondo pieno, altrimenti l'ora scatta con un ritardo che
 * cresce a ogni giro.
 *
 * L'orologio non sta al centro dello schermo ma nella meta' sinistra: la meta'
 * destra e' la colonna di quello che succede in casa. Un orologio centrato con
 * tre scritte sotto era la schermata di un apparecchio che fa una cosa sola.
 *
 * <b>Nessuna scritta esce dalla sua scheda.</b> Ogni testo di lunghezza non
 * prevedibile - il nome di quel che suona, un errore, la frase che ha detto
 * una persona - passa da una {@link Testo.Riga}, che lo misura con lo stesso
 * Paint con cui verra' disegnato. Prima si contavano i caratteri, e contare i
 * caratteri non e' misurare: "Radiofreccia - mi collego" ne ha venticinque,
 * stava sotto il limite di ventisei, ed era mezza scheda piu' largo dello
 * spazio che aveva. Il perche' per esteso sta in {@link Testo}.
 *
 * <b>Il volume sta qui, non solo dentro Spotify.</b> Era una coppia di tasti
 * piu' e meno dentro la sezione Musica, e quello e' il posto sbagliato per due
 * ragioni: comanda <i>tutto</i> quello che esce dall'altoparlante - radio
 * compresa, e Netflix - e chi vuole abbassare non ha voglia di cambiare
 * schermata per farlo. Qui e' un cursore, che dice anche <b>a che punto sta</b>
 * il volume: due tasti non lo dicono, e su un apparecchio senza schermo di
 * sistema non lo dice nessun altro.
 *
 * <b>E c'e' il tempo che fa.</b> Sta nel mezzo della colonna dell'orologio, che
 * era vuoto: fra il blocco dell'ora e il microfono avanzavano quasi
 * quattrocento pixel di niente, e la regola di questa interfaccia e' che il
 * vuoto si mette dentro un pannello, non si riempie ingrandendo quello che
 * c'e'. Il pannello e' animato - vedi {@link Cielo} - ma <b>a venti fotogrammi
 * al secondo e solo sul suo rettangolo</b>: su uno schermo acceso sedici ore al
 * giorno, sessanta fotogrammi a schermo intero per far cadere sei gocce sono
 * batteria buttata. Toccandolo si apre la pagina intera.
 *
 * <b>E il nome non porta piu' lo stato incollato dietro.</b> Il nome della
 * stazione resta, "mi collego" dura tre secondi: sono due cose, e stanno su
 * due righe con due corpi e due colori. Cosi' il nome si legge grande da
 * subito, e quando una stazione non parte la Home lo dice invece di limitarsi
 * a tacere.
 */
public class SezioneHome extends Sezione {

    /**
     * Il pulsante del microfono.
     *
     * <b>Ce n'e' uno solo, e c'era stato anche un ingranaggio accanto.</b> E'
     * durato poco: le impostazioni di Casa - i campioni della parola di
     * attivazione, la soglia, la cancellazione d'eco - si aprono adesso
     * soltanto da Gestione Home sul PC. Un ingranaggio su un apparecchio
     * appeso in cucina e' un pulsante che prima o poi qualcuno preme per
     * curiosita', e di la' si cancellano le registrazioni.
     */
    /**
     * I due gesti del microfono: aprire l'ascolto, e chiuderlo.
     *
     * <b>Annullare deve esserci.</b> Da quando la parola di attivazione e'
     * spenta, il microfono si apre solo premendo - e chi preme per sbaglio, o
     * cambia idea, resta altrimenti davanti a un tablet che ascolta finche'
     * non scade da solo. Aspettare che scada e' la peggiore delle risposte:
     * non si sa quanto duri e sembra bloccato.
     */
    public interface Bottone {
        void suMicrofono();
        void suAnnulla();
    }

    private static final String[] GIORNI = {
        "domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato"
    };
    private static final String[] MESI = {
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"
    };

    /**
     * Cosa c'e' scritto sopra il microfono quando non c'e' niente da dire.
     *
     * E' un comando che Casa capisce davvero - sta nell'elenco di
     * {@link Comandi} - e non una frase di esempio inventata: un suggerimento
     * che non funziona e' peggio del silenzio, perche' la prima volta che
     * qualcuno lo prova alla lettera si convince che il microfono sia rotto.
     */
    private static final String SUGGERIMENTO = "premi e parla: « accendi la luce »";

    /**
     * Quanto e' grande l'ora, rispetto al corpo che le da' {@link Misure}.
     *
     * <b>Meno di uno, adesso che il carattere e' di peso medio.</b> Il corpo
     * dice quanto e' alta una cifra, non quanto inchiostro ci sta dentro: le
     * stesse quattro cifre in Roboto Medium hanno la meta' piu' di nero di
     * quante ne avevano in Roboto Thin, e a corpo uguale l'orologio passava da
     * « si legge dall'altra stanza » a « non si vede altro ». Tolto un ottavo,
     * il peso a schermo torna quello di prima e la forma resta quella nuova.
     */
    private static final float GRANDE_ORA = 0.87f;

    /**
     * Quanto si stringono le cifre dell'ora.
     *
     * A centocinquanta pixel la spaziatura normale di Roboto e' quella giusta
     * per una riga di testo, non per un numero letto come una forma sola: le
     * due cifre dell'ora e quelle dei minuti si leggono come quattro segni
     * separati. Stringendole del tre per cento diventano due coppie, che e'
     * come si legge un orologio.
     */
    private static final float STRETTA_ORA = -0.03f;

    /** La spaziatura delle etichette maiuscole. Le maiuscole nascono per stare
     *  larghe: a corpo venti e senza aria fra le lettere « ORA IN
     *  RIPRODUZIONE » e' un blocco che si legge una lettera per volta. */
    private static final float LARGA_ETICHETTA = 0.14f;

    /** Il filo di luce che separa due righe di un elenco. Piu' chiaro di cosi'
     *  diventa un bordo, e un elenco di righe con il bordo e' una griglia. */
    private static final int FILO = 0x1EFFFFFF;

    private final Paint pOra    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pData   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStato  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitolo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pVoce   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMic    = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** I fondi pieni: tondi dei comandi, tessere delle lampade, il microfono. */
    private final Paint pFondo  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLogo   = new Paint(Paint.FILTER_BITMAP_FLAG);

    private final Calendar cal = Calendar.getInstance();
    private final Handler handler = new Handler();

    /**
     * Tre schede, non quattro: una che dice <b>adesso</b>, e due che si
     * premono.
     *
     * Prima erano quattro riquadri con lo stesso bordo, lo stesso raggio e lo
     * stesso velo, disposti due per colonna: nessuno era il primo, e la
     * schermata non diceva da dove si comincia a guardare. Peggio, i due tagli
     * orizzontali - quello fra ora e meteo a sinistra, quello fra musica e casa
     * a destra - stavano a due altezze diverse, perche' il contenuto delle due
     * colonne <i>vuole</i> due altezze diverse: due linee quasi allineate si
     * leggono come un allineamento sbagliato, mai come una scelta.
     *
     * Adesso a sinistra c'e' una scheda sola alta tutto lo schermo - l'ora, la
     * data, che tempo fa, e il tasto per parlare - e la linea di taglio della
     * colonna di destra non deve corrispondere a niente, perche' a sinistra non
     * c'e' piu' niente a cui corrispondere.
     */
    private final RectF cardOra    = new RectF();
    private final RectF cardMusica = new RectF();
    private final RectF cardCasa   = new RectF();

    /**
     * Il meteo: <b>una superficie dentro la scheda dell'ora</b>, non una scheda
     * accanto.
     *
     * Dentro perche' l'ora e il tempo che fa rispondono alla stessa domanda -
     * com'e' adesso, qui - e perche' la colonna dell'orologio era mezzo schermo
     * di niente con un numero in mezzo. Superficie e non scheda perche' un
     * pannello di vetro dentro un pannello di vetro sono due cornici per una
     * cosa sola: qui bastano un fondo appena piu' chiaro e il margine attorno,
     * che e' la stessa regola gia' scritta per il tasto « annulla » dentro il
     * microfono.
     *
     * Il rettangolo di appoggio serve all'invalidate parziale: e' un
     * {@link Rect} di interi perche' e' quello che vuole
     * {@code postInvalidateDelayed}, e allocarne uno a ogni fotogramma per
     * convertirlo sarebbe la cosa che si sta cercando di evitare.
     */
    private final RectF cardMeteo = new RectF();
    private final RectF scenaMeteo = new RectF();
    private final Rect  riquadroMeteo = new Rect();

    /**
     * Le parti della giornata accanto ai gradi: pomeriggio, sera, notte.
     *
     * <b>Non le ore una per una.</b> Quattro tessere con scritto 10, 11, 12, 13
     * sono lo stesso dato dato peggio: chi passa davanti al tablet la mattina
     * si chiede com'e' <i>il pomeriggio</i>, e con le ore deve leggerle tutte e
     * quattro per rispondersi. Le ore una per una sono la domanda della pagina
     * intera - « a che ora smette » - e li' ci sono tutte e ventiquattro.
     *
     * <b>Quattro, adesso che sono righe larghe tutta la superficie.</b> Erano
     * tre perche' stavano in una colonna stretta accanto al disegno, e
     * « pomeriggio » in tre dita di larghezza non ci sta. Con il meteo dentro
     * la scheda dell'ora la riga e' larga il doppio, e la quarta fascia e'
     * quella che vale di piu': e' « dom. mattina », cioe' com'e' domani quando
     * ci si alza - la sola domanda che un tablet appeso al muro riceve la sera.
     */
    private static final int MAX_FASCE = 4;
    private final RectF[] fasceMeteo = new RectF[MAX_FASCE];
    private int quanteFasce;

    /**
     * Il volume di quel che suona: l'altoparlante che azzera, e il cursore.
     *
     * Agisce su {@code STREAM_MUSIC}, che e' l'<b>unica</b> leva che vale per
     * tutti e tre: la radio ci esce con MediaPlayer, la musica di Casa con
     * AudioTrack (vedi {@link TuboAudio#flusso()}), e Netflix e chiunque altro
     * pure. Abbassare il MediaPlayer della radio - che pure si saprebbe fare,
     * lo fa {@link Sordina} - abbasserebbe solo la radio.
     */
    /**
     * Il volume: un meno, il numero, un più. Niente cursore.
     *
     * <b>Il cursore e' stato provato ed e' stato buttato.</b> Prima un filo con
     * una pallina sopra, poi una capsula piena larga tutta la scheda: la
     * seconda era peggio della prima - un rettangolo colorato lungo un palmo in
     * mezzo a una scheda di grigi, che si vedeva prima del nome di quello che
     * stava suonando ed era la cosa meno importante li' dentro.
     *
     * Un cursore vive di una cosa che qui non c'e': la <b>risoluzione</b>. Ha
     * senso quando i valori sono tanti e la posizione dice qualcosa a colpo
     * d'occhio. Qui i gradini del sistema sono <b>quindici</b>, il numero c'e'
     * scritto, e nove volte su dieci quello che si vuole e' « un po' meno ». Due
     * tasti e una cifra fanno esattamente quello, occupano un terzo del posto, e
     * non chiedono di prendere la mira su un apparecchio appeso al muro.
     */
    private final RectF tastoMeno = new RectF();
    private final RectF tastoPiu  = new RectF();
    private final RectF areaValore = new RectF();

    /**
     * Le colonne di quello che sta uscendo dall'altoparlante.
     *
     * <b>Sono i livelli veri</b> - vedi {@link Livelli} - e non un'animazione
     * che si muove da sola: delle barre che ballano identiche mentre la
     * stazione manda la pubblicita' si riconoscono al primo sguardo, e a quel
     * punto tutta la scheda diventa poco credibile. Il rettangolo di appoggio
     * serve all'invalidate parziale, come quello del meteo: e' un {@link Rect}
     * di interi perche' e' quello che vuole {@code postInvalidateDelayed}.
     */
    private final RectF bandaBarre = new RectF();
    private final Rect  riquadroBarre = new Rect();
    private final Livelli livelli = new Livelli();

    /**
     * Il microfono e' una pastiglia larga, non un tondo che galleggia.
     *
     * Il tondo di prima era un bersaglio da indovinare - un cerchio vuoto in
     * mezzo al niente, senza una parola che dicesse cosa fa - e il disegno
     * dentro era una capsula, un arco e un piedino messi a mano. Una pastiglia
     * con l'icona vera e la parola accanto si preme senza chiedersi se sia un
     * pulsante, ed e' il gesto piu' importante della schermata: qui si parla.
     */
    private final RectF micPill = new RectF();
    /** Il tasto « annulla », che esiste solo mentre ascolta. Sta accanto alla
     *  pastiglia, non dentro: dentro sarebbe un tasto che compare sotto il
     *  dito che ha appena premuto. */
    private final RectF annullaPill = new RectF();

    /** Il quadratino di chi sta suonando: il logo della stazione, il marchio
     *  Spotify, l'icona dell'app. Una copertina in miniatura fa capire cosa
     *  c'e' in ballo prima che si legga il titolo. */
    private final RectF sorgente = new RectF();

    /**
     * La riga della data, e in fondo a destra il timer che scorre.
     *
     * <b>Il timer sta qui e la sveglia no</b>, e prima ci stavano tutti e due.
     * Erano due pastiglie centrate sotto la data, e comparendo spostavano
     * l'ora: la cosa piu' ferma della schermata si alzava di mezzo centimetro
     * perche' era partito un timer. Adesso la riga della data c'e' sempre, alta
     * sempre uguale, e il timer ci compare dentro all'estremita' destra - dove
     * non c'era niente - senza muovere una virgola.
     *
     * La sveglia e' rimasta dov'era gia': nella scheda « in casa ». C'era in
     * tutte e due i posti, ed era la sola cosa scritta due volte in questa
     * schermata.
     */
    private final RectF rigaData = new RectF();

    /**
     * L'altezza vera delle cifre dell'ora, misurata sul carattere.
     *
     * Non calcolata da una frazione del corpo, e nemmeno da {@code ascent()}:
     * l'ascendente e' lo spazio che il carattere riserva alle accentate, che
     * in « 11:25 » non ci sono, e appoggiando quello l'ora resterebbe lontana
     * dal bordo senza che si capisca perche'. Si misura una volta, quando
     * cambia la finestra, e non a ogni fotogramma.
     */
    private final Rect misuraCifre = new Rect();
    private float altaCifre;

    /**
     * La fascia sopra il microfono: quello che si e' detto, o come si dice.
     *
     * <b>Ha un'altezza sua, e ce l'ha sempre.</b> Prima la frase detta a voce
     * compariva sotto la data e spingeva in giu' tutto il resto - due righe
     * significavano sessanta pixel di schermata che si muoveva mentre uno
     * parlava. Qui il posto e' riservato: quando non c'e' niente da dire ci sta
     * l'esempio di cosa si puo' chiedere, che su un apparecchio dove la parola
     * di attivazione e' spenta e' l'unica cosa che spiega a cosa serve il tasto
     * blu qui sotto.
     */
    private final RectF bandaStato = new RectF();

    /**
     * La riga di stato della scheda « in casa »: la sveglia.
     *
     * <b>Erano due</b>, e la prima diceva « nessuna accesa ». Sotto pero'
     * adesso c'e' l'elenco delle lampade con scritto accanto a ognuna se e'
     * accesa: il riassunto era la stessa cosa scritta una riga piu' su, e in
     * questa schermata quello che risultava scritto due volte e' sempre stato
     * tolto - e' successo alla sveglia, che stava anche nella riga della data.
     */
    private final RectF tessSveglia = new RectF();

    /**
     * Fino a tre lampade, accese e spente da qui.
     *
     * <b>Erano le routine.</b> Una routine si preme una volta al giorno -
     * « buonanotte » entrando in camera - e per il resto della giornata quelle
     * tre righe tenevano la scheda occupata senza fare niente, mentre la cosa
     * che si vuole passando davanti al tablet e' accendere una luce. Le
     * routine non sono sparite: stanno tutte nella sezione Casa, che si apre
     * premendo la scheda, esattamente come il meteo apre la sua pagina.
     *
     * Tre e non tutte: quello che non ci sta e' un motivo in piu' per entrare
     * nella sezione, e una fila lunga come l'elenco non e' piu' una
     * scorciatoia.
     */
    private static final int MAX_LUCI = 3;
    private final RectF[] areeLuci = new RectF[MAX_LUCI];
    private final Testo.Riga[] rNomeLuce  = new Testo.Riga[MAX_LUCI];
    private final Testo.Riga[] rStatoLuce = new Testo.Riga[MAX_LUCI];

    /**
     * Le bande in cui va impaginato il contenuto delle due schede di destra:
     * da sotto il titolo a sopra quello che viene dopo - i comandi nella
     * musica, il bordo nella casa.
     *
     * Calcolate qui e non a frazioni dentro onDraw, per la stessa ragione per
     * cui erano gia' calcolate le righe vive dell'orologio: appoggiarsi a
     * {@code centerY()} funziona finche' le righe sono una, e appena diventano
     * due la seconda finisce addosso ai tasti.
     */
    private final RectF bandaMusica = new RectF();

    /** Lo spazio in cui una scritta deve stare, dentro ogni scheda. */
    private float larghezzaOra, larghezzaMusica, larghezzaCasa;

    /** L'altezza di una pastiglia: la stessa in tutta la schermata, perche'
     *  due bersagli alti diversi in due schede vicine si leggono come due
     *  generi di comando. */
    private float altaPill;

    /** Una per scritta, come i Paint: si ricordano l'ultimo taglio e non
     *  misurano niente finche' non cambia qualcosa. */
    private final Testo.Riga rData         = new Testo.Riga();
    private final Testo.Riga rTitoloMusica = new Testo.Riga();
    private final Testo.Riga rTitoloCasa   = new Testo.Riga();
    private final Testo.Riga rNome         = new Testo.Riga();
    private final Testo.Riga rVuoto        = new Testo.Riga();
    private final Testo.Riga rNota         = new Testo.Riga();
    private final Testo.Riga rSveglia      = new Testo.Riga();
    private final Testo.Riga rMeteoDove    = new Testo.Riga();
    private final Testo.Riga rMeteoGradi   = new Testo.Riga();
    private final Testo.Riga rMeteoCond    = new Testo.Riga();
    private final Testo.Riga rMeteoNota    = new Testo.Riga();

    /** La frase detta a voce si manda a capo invece di tagliarla: chi legge
     *  "«accendi la luce della cuc…»" non sa se Casa ha capito tutto. */
    private final Testo.Blocco bStato = new Testo.Blocco();

    /** I tre comandi della radio dentro la scheda: indietro, ferma/avvia,
     *  avanti. Sono qui perche' la Home e' la schermata che si guarda per
     *  prima, e cambiare stazione non deve costare un viaggio in un'altra
     *  sezione. */
    private final RectF[] comandi = new RectF[3];
    private final Paint pComando = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int premutoComando = -1;
    private int premutaLuce = -1;
    private boolean premutoMic, premutoAnnulla;
    private boolean premutoMeno, premutoPiu, premutoMeteo;
    /** La scheda « in casa » premuta dove non c'e' una lampada: si apre la
     *  sezione, come il pannello del meteo apre la sua pagina. */
    private boolean premutaCasa;

    /** L'apparecchio radio. Si chiama cosi' e non "radio" perche' quel nome e'
     *  gia' preso dalla stringa che dice cosa sta suonando. */
    private Radio sorgenteRadio;

    /** Cosa suona fuori da Casa, e come comandarlo. null finche' MainActivity
     *  non l'ha collegata. */
    private Riproduzione riproduzione;

    /** La musica di Casa - Spotify, ma dentro di noi. Non arriva da
     *  {@link Riproduzione}: quella salta apposta le sessioni di Casa, o la
     *  Home vedrebbe se stessa riflessa. */
    private Musica musica;

    /** Chi hanno in mano i tre tasti della scheda. */
    private static final int COMANDA_RADIO = 0, COMANDA_FUORI = 1, COMANDA_MUSICA = 2;
    /**
     * Non sta suonando niente, ma c'e' qualcosa da rimettere.
     *
     * E' lo stato in cui il tablet passa la maggior parte della giornata, e
     * fino a ieri era quello in cui i tre tasti non servivano a niente: play
     * accendeva la prima stazione dell'elenco, che non e' quasi mai quella che
     * si stava ascoltando. Adesso ripartono dall'ultima cosa vera - una
     * stazione o un brano - e la scheda lo scrive prima che qualcuno prema.
     * Vedi {@link Ultimo}.
     */
    private static final int COMANDA_ULTIMO = 3;

    private String testoOra = "";
    private String testoData = "";

    private String stato;
    private String radio;
    private long   timer = -1;
    private String sveglia;

    /** Composte quando cambia il dato, non a ogni fotogramma: concatenare una
     *  stringa dentro onDraw e' un oggetto nuovo sessanta volte al secondo, e
     *  {@code String.format} del timer e' anche caro. */
    private String testoTimer;
    private String testoSveglia = "nessuna sveglia";

    private Bottone bottone;
    private boolean inAscolto;

    /** Il volume, da 0 a 1, e i gradini che il sistema concede. Su questo
     *  tablet sono quindici: il cursore e' continuo all'occhio ma si posa
     *  sempre su uno di quelli, e mostrare una percentuale che non corrisponde
     *  a nessun gradino vorrebbe dire un numero che non si puo' raggiungere. */
    private AudioManager audio;
    private int gradini = 15;
    private float volume;
    private String testoVolume = "0%";

    /** Il tempo che fa, e le scritte gia' composte: comporre una stringa dentro
     *  onDraw sarebbe un oggetto nuovo venti volte al secondo, e questo
     *  pannello si ridisegna proprio venti volte al secondo. */
    private Meteo meteo;
    private final Cielo cielo = new Cielo();
    private Meteo.Pagina pagina;
    private String mDove = "Meteo", mGradi, mCondizione, mMax, mMin;
    private Meteo.Fascia[] fasce = new Meteo.Fascia[0];

    /** Le notizie, e chi sa aprirne la pagina. Si alternano al meteo nella
     *  stessa superficie: vedi {@link #disegnaMeteo}. */
    private Notizie notizie;
    private Notizie.Pagina paginaNotizie;
    private static final long DURATA_METEO = 10000L, DURATA_NOTIZIE = 10000L;
    private static final int SPEGNE = 280, ACCENDE = 420;
    /** Due titoli per volta, e un giro sui primi otto. */
    private static final int QUANTE_NOTIZIE = 2, GIRO_NOTIZIE = 8;
    /** Che cosa c'e' nella superficie - o ci sara', alla fine del passaggio. */
    private boolean inNotizie;
    /** Quando e' cominciato il passaggio (0 = nessuno), e da quando si vede
     *  quel che si vede. */
    private long cambio, mostrata;
    private float cimaNotizie, fondoNotizie;
    private int quanteNotizie, giroNotizie;
    private String hStato = "";
    private final String[] hTitolo = new String[QUANTE_NOTIZIE];
    private final String[] hFonte = new String[QUANTE_NOTIZIE];
    private final float[] corpoNotizia = new float[QUANTE_NOTIZIE];
    private final int[] righeNotizia = new int[QUANTE_NOTIZIE];
    private final Testo.Blocco[] bNotizia = { new Testo.Blocco(), new Testo.Blocco() };
    private final Testo.Riga[] rNotizieFonte = { new Testo.Riga(), new Testo.Riga() };
    private final Testo.Riga rNotizieStato = new Testo.Riga();
    private final Testo.Blocco provaNotizia = new Testo.Blocco();
    /** Un Paint suo per i titoli: pData ha il corpo della data fissato una
     *  volta in onSizeChanged, e cambiarglielo qui lo cambierebbe anche li'. */
    private final Paint pNotizia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] mFasciaGradi   = new String[MAX_FASCE];
    private final String[] mFasciaPioggia = new String[MAX_FASCE];
    private final Testo.Riga[] rFascia    = new Testo.Riga[MAX_FASCE];

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            // Due cose cambiano da sole senza dirlo a nessuno: l'ora, e il
            // volume - che i tasti sul fianco del tablet spostano senza
            // avvisare. Si guardano tutte e due qui, e si ridisegna una volta.
            boolean cambiato = aggiornaOra();
            if (alterna()) cambiato = true;
            if (leggiVolume()) cambiato = true;
            if (cambiato) invalidate();
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    public SezioneHome(Context c, Misure m) {
        super(c, m);
        for (int i = 0; i < comandi.length; i++) comandi[i] = new RectF();
        for (int i = 0; i < MAX_LUCI; i++) {
            areeLuci[i] = new RectF();
            rNomeLuce[i] = new Testo.Riga();
            rStatoLuce[i] = new Testo.Riga();
        }
        for (int i = 0; i < MAX_FASCE; i++) {
            fasceMeteo[i] = new RectF();
            rFascia[i] = new Testo.Riga();
        }

        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (audio != null) {
            gradini = Math.max(1, audio.getStreamMaxVolume(TuboAudio.flusso()));
        }
        leggiVolume();
        pComando.setStrokeCap(Paint.Cap.ROUND);
        pComando.setStrokeJoin(Paint.Join.ROUND);

        // <b>L'ora non e' piu' sottile e centrata.</b> Roboto Thin a
        // centocinquanta pixel, in mezzo a una scheda vuota, con una lineetta
        // colorata sotto, e' l'orologio che Android mette da solo su qualunque
        // schermo spento: non era una scelta, era il valore che si ottiene
        // senza sceglierlo. Il peso medio e le lettere strette danno un numero
        // che sta in piedi da solo, e appoggiato all'angolo in alto a sinistra
        // sta sullo stesso filo di tutto il resto della schermata invece di
        // galleggiare per conto suo.
        pOra.setColor(Tinte.TESTO);
        pOra.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pOra.setTextAlign(Paint.Align.LEFT);
        pOra.setLetterSpacing(STRETTA_ORA);

        pData.setColor(Tinte.TESTO_TENUE);
        pData.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pData.setTextAlign(Paint.Align.LEFT);

        // Lo stato e' il piu' smorto: e' informazione di passaggio, non deve
        // rubare l'occhio all'ora.
        pStato.setColor(Tinte.TESTO_TENUE);
        pStato.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        pStato.setTextAlign(Paint.Align.LEFT);

        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        pVoce.setColor(Tinte.TESTO_MEDIO);
        pVoce.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // Le righe di contorno delle due schede di destra. Un Paint suo invece
        // di prendere in prestito pStato girandogli l'allineamento a meta'
        // disegno: quello e' centrato perche' sta sotto l'orologio, queste
        // vanno a sinistra come tutto il resto della colonna.
        pNota.setColor(Tinte.TESTO_TENUE);
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        pMic.setStyle(Paint.Style.STROKE);
        pMic.setStrokeCap(Paint.Cap.ROUND);

        aggiornaOra();
    }

    @Override public String titolo() { return "Home"; }
    @Override public int tinta() { return Tinte.HOME; }

    /** Una casetta. Contornata quando si e' altrove, piena quando si e' qui. */
    @Override public int icona()      { return Icone.HOME; }
    @Override public int iconaPiena() { return Icone.HOME_PIENA; }

    // ---- quello che arriva da fuori --------------------------------------

    public void setStato(String s)   { if (!uguali(stato, s))  { stato = s;  invalidate(); } }
    public void setBottone(Bottone b) { bottone = b; }

    /** Il nome di quel che suona. Lo stato - "mi collego", un errore - non
     *  arriva di qui: lo si chiede alla radio nel momento in cui si disegna. */
    public void setRadio(String r) {
        if (uguali(radio, r)) return;
        radio = r;
        invalidate();
    }

    public void setSveglia(String s) {
        if (uguali(sveglia, s)) return;
        sveglia = s;
        testoSveglia = s != null ? s : "nessuna sveglia";
        invalidate();
    }

    public void setTimer(long secondi) {
        if (timer == secondi) return;
        timer = secondi;
        testoTimer = secondi >= 0 ? mmss(secondi) : null;
        invalidate();
    }

    /** I loghi delle stazioni: gli stessi che usa la sezione Radio, con la
     *  stessa cache. Averne un secondo magazzino vorrebbe dire il doppio delle
     *  bitmap in memoria per le stesse ventidue immagini. */
    public void setLoghi(Loghi l) { loghi = l; invalidate(); }

    private Loghi loghi;

    /** Le copertine dei dischi: la stessa cache della sezione Spotify. Passare
     *  quella e non una seconda e' il punto - la copertina che si vede qui e'
     *  gia' stata scaricata li', e non costa un secondo scaricamento ne' una
     *  seconda bitmap. */
    public void setCopertine(Copertine c) { copertine = c; invalidate(); }

    private Copertine copertine;

    /**
     * Le lampade della scheda « in casa », e chi sa aprirne la sezione.
     *
     * E' lo stesso {@link Luci} di {@link SezioneLuci} e della voce: due
     * elenchi si scorderebbero cosa ha acceso l'altro, e la Home mostrerebbe
     * spenta una lampada che qualcuno ha appena acceso parlando.
     */
    public void setApparecchioLuci(Luci l, Luci.Pagina p) {
        apparecchioLuci = l;
        sezioneLuci = p;
        misureCasa();
        invalidate();
    }

    /**
     * Le lampade sono cambiate - accese, spente, o ha risposto una che prima
     * taceva.
     *
     * Si rifanno anche le misure, e non e' per scrupolo: qui dentro passa
     * pure il momento in cui il PC riscrive l'elenco, e una lampada in piu' o
     * in meno cambia quante righe ci stanno e quanto sono alte.
     */
    public void luciCambiate() {
        misureCasa();
        invalidate();
    }

    private Luci apparecchioLuci;
    private Luci.Pagina sezioneLuci;

    /** La stessa radio della sezione Radio e della voce, non una terza. */
    public void setSorgente(Radio r) { sorgenteRadio = r; invalidate(); }

    /** Quello che suona in un'altra app - Netflix, di solito, adesso che
     *  Spotify e' roba nostra. Quando c'e', la scheda diventa il suo
     *  telecomando: vedi {@link #telecomandoEsterno()}. */
    public void setRiproduzione(Riproduzione r) { riproduzione = r; invalidate(); }

    /** La stessa Musica della sezione Musica e della voce, non una seconda. */
    public void setMusica(Musica mu) { musica = mu; invalidate(); }

    /**
     * Il tempo che fa, e chi sa aprirne la pagina.
     *
     * E' lo stesso {@link Meteo} della sezione App: una seconda copia vorrebbe
     * dire due richieste alla rete e due copie su disco che si scavalcano.
     */
    public void setMeteo(Meteo mt, Meteo.Pagina p) {
        meteo = mt;
        pagina = p;
        aggiornaScritteMeteo();
        invalidate();
    }

    /** Il meteo e' cambiato: si rifanno le scritte una volta, non a ogni
     *  fotogramma. */
    public void meteoCambiato() {
        aggiornaScritteMeteo();
        invalidate();
    }

    /**
     * Le notizie, e chi sa aprirne la pagina.
     *
     * Non c'e' un « notizie cambiate » qui, ed e' voluto: i titoli in scena
     * restano quelli fino al giro dopo, e non cambiano sotto gli occhi di chi
     * li sta leggendo. Il giro dopo prende quelli nuovi da solo.
     */
    public void setNotizie(Notizie n, Notizie.Pagina p) {
        notizie = n;
        paginaNotizie = p;
    }

    public void setInAscolto(boolean b) {
        if (inAscolto == b) return;
        inAscolto = b;
        invalidate();
    }

    private static boolean uguali(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ---- misure -----------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        float mg = m.margine;

        // La colonna dell'ora si prende poco piu' di meta' schermo, e non i due
        // terzi di prima: l'ora e' rimpicciolita di un terzo, e una scheda che
        // le resta larga il doppio del necessario e' aria che manca alle due
        // schede accanto - quelle che portano il testo vero.
        float mezzeria = w * 0.545f;
        cardOra.set(mg, mg, mezzeria - m.s2, h - mg);

        // Il taglio della colonna di destra segue il contenuto, e adesso puo':
        // « ora in riproduzione » sono un nome, un cursore e tre tasti, « in
        // casa » sono la sveglia piu' le lampade da accendere. La seconda vuole
        // piu' posto, e non deve piu' corrispondere a un taglio di sinistra che
        // non esiste piu'.
        float taglio = h * 0.485f;
        cardMusica.set(mezzeria + m.s2, mg, w - mg, taglio - m.s2 / 2f);
        cardCasa.set(mezzeria + m.s2, taglio + m.s2 / 2f, w - mg, h - mg);

        pOra.setTextSize(m.cifra * GRANDE_ORA);
        pOra.getTextBounds("0", 0, 1, misuraCifre);
        altaCifre = -misuraCifre.top;
        pData.setTextSize(m.corpo);
        pStato.setTextSize(m.nota);
        pTitolo.setTextSize(m.nota);
        pVoce.setTextSize(m.voce);
        pNota.setTextSize(m.nota);

        larghezzaOra    = cardOra.width()    - m.s4 * 2f;
        larghezzaMusica = cardMusica.width() - m.s4 * 2f;
        larghezzaCasa   = cardCasa.width()   - m.s4 * 2f;

        altaPill    = Math.max(m.bersaglio * 0.86f, h * 0.058f);

        // ---- la scheda di adesso ----
        //
        // Si impagina dalle due estremita' verso il centro: l'ora e' appesa al
        // bordo di sopra, il microfono a quello di sotto, e il tempo che fa
        // prende quello che avanza in mezzo. E' il contrario di come stava
        // prima - tutto misurato a partire dall'alto - ed e' la ragione per cui
        // il tasto blu non si sposta mai di un pixel qualunque cosa succeda
        // sopra di lui.
        float x0 = cardOra.left + m.s4, x1 = cardOra.right - m.s4;
        // L'aria sopra l'ora e' piu' larga di quella di lato: un numero alto
        // centoventi pixel appoggiato al bordo di sopra con lo stesso margine
        // che ha a sinistra sembra scappato fuori dalla scheda. E' la sola
        // asimmetria di questa schermata, ed e' ottica, non geometrica.
        float sottoOra = cardOra.top + m.s5 + altaCifre;
        rigaData.set(x0, sottoOra + m.s3, x1, sottoOra + m.s3 + altaPill);

        // Il microfono: una barra larga quanto il contenuto, in fondo alla
        // scheda. Larga tutta e non una pastiglia centrata: e' il gesto per cui
        // esiste questo apparecchio, ed era il contorno piu' sottile della
        // schermata.
        float altaMic = Math.max(m.bersaglio * 1.25f, h * 0.085f);
        micPill.set(x0, cardOra.bottom - m.s4 - altaMic, x1, cardOra.bottom - m.s4);

        // « Annulla » sta DENTRO la barra, nella sua estremita' destra, e solo
        // mentre ascolta.
        //
        // Fuori sarebbe stato piu' ovvio, ed e' stato provato: ma allora o la
        // barra si sposta quando il tasto compare - e il microfono scappa da
        // sotto il dito che l'ha appena premuto - oppure le si riserva il posto
        // accanto, e a riposo resta storta rispetto alla scheda, che sembra uno
        // sbaglio. Dentro non si sposta niente e non avanza niente.
        //
        // <b>E sta dentro per davvero, con un margine su tutti i lati.</b> La
        // prima versione arrivava a filo del bordo destro: due pastiglie con
        // lo stesso raggio, una dentro l'altra e con i bordi coincidenti, non
        // si leggono come « un tasto dentro un tasto » - si leggono come due
        // curve accavallate. Il margine e' quello che dice che sono due cose
        // diverse.
        float rientro = altaMic * 0.15f;
        annullaPill.set(micPill.right - Math.min(micPill.width() * 0.30f, h * 0.19f),
                        micPill.top + rientro, micPill.right - rientro,
                        micPill.bottom - rientro);

        // Due righe di frase, riservate sempre: vedi bandaStato.
        float altaStato = m.nota * 1.32f * 2f;
        bandaStato.set(x0, micPill.top - m.s3 - altaStato, x1, micPill.top - m.s3);

        // ---- il meteo, la superficie in mezzo alla scheda di adesso ----
        cardMeteo.set(x0, rigaData.bottom + m.s4, x1, bandaStato.top - m.s4);

        // Il rettangolo sporco si allarga di due pixel per parte: quello vero
        // si arrotonda per difetto, e un bordo antialiasato resterebbe fuori
        // dalla riverniciatura lasciando una riga che non si aggiorna mai.
        riquadroMeteo.set((int) cardMeteo.left - 2, (int) cardMeteo.top - 2,
                          (int) cardMeteo.right + 2, (int) cardMeteo.bottom + 2);

        float xM0 = cardMeteo.left + m.s3, xM1 = cardMeteo.right - m.s3;
        float corpoM = cardMeteo.top + m.s3 + m.micro + m.s3;
        float fondoM = cardMeteo.bottom - m.s3;
        float altoM  = fondoM - corpoM;
        cimaNotizie = corpoM;
        fondoNotizie = fondoM;

        // A sinistra com'e' adesso, a destra come sara'. Il taglio a poco piu'
        // di un terzo e' quello che lascia alle righe la larghezza di cui hanno
        // bisogno: « dom. mattina », il segno del tempo, la pioggia e i gradi
        // vogliono trecento pixel, e sotto quelli si comincia a rimpicciolire
        // il nome - cioe' la parola che dice a cosa si riferisce il numero.
        float largoAdesso = (xM1 - xM0) * 0.36f;
        float xFasce = xM0 + largoAdesso + m.s4;

        quanteFasce = MAX_FASCE;
        float altaT = (altoM - m.s1 * (MAX_FASCE - 1)) / MAX_FASCE;
        for (int i = 0; i < MAX_FASCE; i++) {
            float y = corpoM + (altaT + m.s1) * i;
            fasceMeteo[i].set(xFasce, y, xM1, y + altaT);
        }

        // La scena, i gradi e la parola stanno incolonnati sullo stesso filo di
        // sinistra. Erano in fila - disegno, poi numero, poi parola accanto - e
        // la parola finiva in due dita di larghezza: « poco nuvoloso » c'era e
        // non si leggeva, che e' il peggiore dei due mondi.
        float altaGradi = m.titolo * 0.86f;
        float altaCond  = m.nota * 1.25f;
        float latoScena = Math.min(largoAdesso * 0.82f,
                altoM - altaGradi - altaCond - m.s2 - m.s1);
        float altoBlocco = latoScena + m.s2 + altaGradi + m.s1 + altaCond;
        float cimaBlocco = corpoM + Math.max(0f, (altoM - altoBlocco) / 2f);
        scenaMeteo.set(xM0, cimaBlocco, xM0 + latoScena, cimaBlocco + latoScena);

        // ---- la scheda di quel che suona ----
        //
        // Tre fasce e basta: l'etichetta, chi suona, i comandi. Ed e' la terza
        // versione, perche' le prime due erano impilate a pezzi - copertina,
        // nome, poi una fascia di colonne, poi una riga di volume, poi i tasti:
        // cinque bande orizzontali dentro mezza scheda, ognuna alta un dito,
        // con l'aria che avanzava spalmata in mezzo. Un elenco di componenti,
        // non un disegno.
        //
        // Adesso quello che riguarda <b>chi sta suonando</b> sta tutto in un
        // blocco solo - copertina a sinistra, e a destra nome, nota e le
        // colonne del suono incolonnate sul suo stesso filo - e quello che si
        // <b>preme</b> sta tutto nella riga in fondo.
        float bandaTitolo = m.s3 + m.micro + m.s3;
        float xm0 = cardMusica.left + m.s4, xm1 = cardMusica.right - m.s4;

        // ---- la riga dei comandi, in fondo ----
        //
        // I tre tasti del brano appoggiati al filo di sinistra, il volume
        // appoggiato a quello di destra. <b>Non centrati.</b> Erano centrati
        // sulla scheda, e per un errore di mezzo tasto - il centro della fila
        // veniva calcolato sul primo tondo invece che sul secondo - stavano
        // mezzo dito piu' a destra del centro: una simmetria mancata si vede
        // molto piu' di un'asimmetria voluta. Ai due filoni della scheda invece
        // non possono essere storti, perche' sono gli stessi due filoni
        // dell'etichetta sopra e della copertina.
        float lato = m.bersaglio;
        float cyCmd = cardMusica.bottom - m.s4 - lato / 2f;
        for (int i = 0; i < comandi.length; i++) {
            float cx = xm0 + lato / 2f + i * lato * 1.25f;
            comandi[i].set(cx - lato / 2f, cyCmd - lato / 2f,
                           cx + lato / 2f, cyCmd + lato / 2f);
        }

        float latoVol = m.bersaglio * 0.88f;
        float largoValore = m.voce * 2.2f;
        tastoPiu.set(xm1 - latoVol, cyCmd - latoVol / 2f, xm1, cyCmd + latoVol / 2f);
        areaValore.set(tastoPiu.left - m.s2 - largoValore, cyCmd - latoVol / 2f,
                       tastoPiu.left - m.s2, cyCmd + latoVol / 2f);
        tastoMeno.set(areaValore.left - m.s2 - latoVol, cyCmd - latoVol / 2f,
                      areaValore.left - m.s2, cyCmd + latoVol / 2f);

        // ---- chi suona: la copertina, il nome, e le colonne ----
        float cimaChi = cardMusica.top + bandaTitolo;
        float fondoChi = comandi[0].top - m.s4;
        float altoChi = fondoChi - cimaChi;

        // La copertina e' grande quanto il blocco che le sta accanto - nome,
        // nota e colonne - e non un terzo della scheda: due cose alte uguali
        // accanto si leggono come una cosa sola, che e' quello che sono.
        float altaBarre = Math.max(m.dp(26), h * 0.046f);
        float altoTesto = m.voce * 1.12f + m.nota * 1.32f + m.s3 + altaBarre;
        float latoSorg = Math.min(altoChi, Math.max(altoTesto, m.bersaglio * 1.8f));
        float cimaSorg = cimaChi + Math.max(0f, (altoChi - latoSorg) / 2f);
        sorgente.set(xm0, cimaSorg, xm0 + latoSorg, cimaSorg + latoSorg);

        bandaMusica.set(sorgente.right + m.s4, sorgente.top, xm1, sorgente.bottom);
        bandaBarre.set(bandaMusica.left, bandaMusica.bottom - altaBarre,
                       bandaMusica.right, bandaMusica.bottom);
        riquadroBarre.set((int) bandaBarre.left - 2, (int) bandaBarre.top - 2,
                          (int) bandaBarre.right + 2, (int) bandaBarre.bottom + 2);

        // ---- la scheda di casa ----
        //
        // Sta in un metodo suo perche' e' l'unico pezzo della schermata che
        // cambia disposizione senza che cambi la finestra: le lampade arrivano
        // da un file che riscrive il PC, e due lampade non si impaginano come
        // tre.
        bandaTitoloCasa = bandaTitolo;
        misureCasa();
    }

    /** Quanto lascia libero il titolo « IN CASA ». Tenuto perche' la scheda si
     *  reimpagina anche fuori da onSizeChanged. */
    private float bandaTitoloCasa;

    /**
     * La scheda di casa: la sveglia in cima, e sotto le lampade.
     *
     * <b>Lo stato non e' dentro delle tessere.</b> « Nessuna sveglia » stava in
     * un riquadro identico a quelli premibili qui sotto, stesso bordo e stesso
     * raggio - e non si preme. Un finto tasto sopra dei tasti veri: chi guarda
     * non ha modo di distinguerli finche' non li tocca e non succede niente.
     * E' una riga scritta, senza riquadro, e i soli riquadri rimasti nella
     * scheda sono le lampade, che si premono davvero.
     */
    private void misureCasa() {
        if (cardCasa.isEmpty()) return;

        float xc0 = cardCasa.left + m.s4, xc1 = cardCasa.right - m.s4;
        float yT = cardCasa.top + bandaTitoloCasa;
        float altaStatoCasa = m.nota * 1.6f;
        // Larga tutta la scheda, adesso che e' sola: era mezza perche' accanto
        // c'era il riassunto delle luci, che le lampade qui sotto dicono
        // meglio di quanto lo dicesse lui.
        tessSveglia.set(xc0, yT, xc1, yT + altaStatoCasa);

        int quante = quanteLuci();
        float yR = tessSveglia.bottom + m.s4;
        float spazioR = cardCasa.bottom - m.s4 - yR;
        for (int i = 0; i < MAX_LUCI; i++) areeLuci[i].setEmpty();
        if (quante == 0) return;

        // Le righe si dividono lo spazio che c'e': con due lampade sono piu'
        // alte, non due su tre di scheda piena e un terzo di niente.
        float altaR = Math.min(Math.max(m.bersaglio, getHeight() * 0.092f),
                (spazioR - m.s2 * (quante - 1)) / quante);
        float cimaR = yR + Math.max(0f,
                (spazioR - (altaR * quante + m.s2 * (quante - 1))) / 2f);
        for (int i = 0; i < quante; i++) {
            float y = cimaR + (altaR + m.s2) * i;
            // Una lampada che non ci sta nella scheda non si disegna: meglio
            // due righe intere che tre di cui l'ultima tagliata.
            if (altaR < m.bersaglio * 0.7f || y + altaR > cardCasa.bottom - m.s4) continue;
            areeLuci[i].set(xc0, y, xc1, y + altaR);
        }
    }

    /** Quante lampade stanno nella scheda: quelle che ci sono, fino a tre. */
    private int quanteLuci() {
        if (apparecchioLuci == null) return 0;
        return Math.min(MAX_LUCI, apparecchioLuci.elenco().size());
    }

    private boolean aggiornaOra() {
        cal.setTimeInMillis(System.currentTimeMillis());
        String ora = String.format(Locale.ITALIAN, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        String data = String.format(Locale.ITALIAN, "%s %d %s",
                GIORNI[cal.get(Calendar.DAY_OF_WEEK) - 1],
                cal.get(Calendar.DAY_OF_MONTH),
                MESI[cal.get(Calendar.MONTH)]);
        boolean cambiato = !ora.equals(testoOra) || !data.equals(testoData);
        testoOra = ora;
        testoData = data;
        return cambiato;
    }

    // ---- disegno ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (vetro == null || !vetro.vivo()) return;

        // Le tre schede entrano una dopo l'altra, scorrendo di poco. Si muove
        // la posizione e non l'opacita': sfumare un pannello di vetro vorrebbe
        // dire un livello fuori schermo per scheda, e la dissolvenza generale
        // della sezione ce l'ha gia' messa Telaio.
        int s0 = c.save();
        scorri(c, 0);
        vetro.pannello(c, cardOra, m.raggio, false);
        disegnaOrologio(c);
        disegnaMeteo(c);
        disegnaStato(c);
        disegnaMicrofono(c);
        c.restoreToCount(s0);

        s0 = c.save();
        scorri(c, 1);
        vetro.pannello(c, cardMusica, m.raggio, false);
        disegnaMusica(c);
        c.restoreToCount(s0);

        s0 = c.save();
        scorri(c, 2);
        // Premuta si schiarisce tutta: e' l'unica scheda che si apre, e senza
        // quel mezzo tono non ci sarebbe niente a dire che il dito e' stato
        // sentito - il meteo, che fa la stessa cosa, ha il suo incavo.
        vetro.pannello(c, cardCasa, m.raggio, premutaCasa);
        disegnaCasa(c);
        c.restoreToCount(s0);

        Anima.continua(this, entrata, 3);
        pulsaIlCielo();
    }

    /**
     * Chiede il prossimo fotogramma del meteo - <b>venti al secondo, e solo su
     * quel rettangolo</b>.
     *
     * Il resto di Casa si ridisegna quando cambia qualcosa: l'orologio una
     * volta al minuto, le tessere quando qualcuno le tocca. Un cielo animato
     * rompe quella regola per costruzione, e allora la si rompe il meno
     * possibile. Cinquanta millisecondi bastano a nuvole che ondeggiano e a
     * gocce che cadono - sono movimenti lenti, non un gioco - e il rettangolo
     * dice al disegno accelerato di limitare li' la riverniciatura invece di
     * ridipingere milleduecento per ottocento pixel.
     *
     * Non parte se non c'e' niente da animare, e si ferma da solo quando la
     * Home esce di scena: fuori scena la View e' {@code GONE} e {@code onDraw}
     * non viene piu' chiamata, quindi la catena non si rialimenta.
     */
    private void pulsaIlCielo() {
        if (meteo == null || meteo.adesso() == null) return;
        // Con le notizie in scena il cielo non si vede, e non si anima.
        if (inNotizie && cambio == 0L) return;
        postInvalidateDelayed(50, riquadroMeteo.left, riquadroMeteo.top,
                              riquadroMeteo.right, riquadroMeteo.bottom);
    }

    /** Quanto manca alla scheda numero i per essere arrivata al suo posto. */
    private void scorri(Canvas c, int i) {
        float t = Anima.posa(Anima.entrata(entrata, i));
        if (t < 1f) c.translate(0f, (1f - t) * m.s5);
    }

    /**
     * L'ora, la data, e sotto quello che e' vivo adesso.
     *
     * <b>L'ora e' scesa da trenta a diciannove centesimi dell'altezza.</b> A
     * trenta centesimi erano duecentoquaranta pixel di cifre: leggibili
     * dall'altra stanza, e talmente ingombranti che tutto il resto della
     * schermata sembrava una didascalia. A diciannove sono centocinquanta, che
     * a un metro si leggono con la coda dell'occhio lo stesso - e intanto
     * sotto ci sta quello che dice se c'e' un timer che scorre o una sveglia
     * che aspetta.
     *
     * Il corpo resta comunque <b>quel che avanza</b> e non una misura fissa:
     * con la frase detta a voce lunga due righe e un timer acceso, l'ora
     * rimpicciolisce invece di scavalcarli. E' lo stesso difetto delle scritte
     * che sfondavano le schede, solo in verticale.
     */
    private void disegnaOrologio(Canvas c) {
        float x = cardOra.left + m.s4;

        // L'ora sta dove sta e basta: appoggiata all'angolo, a corpo pieno,
        // sempre. Prima il corpo era « quel che avanza » e il blocco si
        // ricentrava fra quello che c'era sopra e sotto - cioe' la cosa piu'
        // ferma della schermata si spostava perche' era partito un timer o
        // perche' qualcuno aveva parlato. Adesso quello che varia ha il suo
        // posto riservato altrove, e l'ora non si muove piu'.
        pOra.setColor(Tinte.TESTO);
        pOra.setTextSize(m.cifra * GRANDE_ORA);
        c.drawText(testoOra, x, cardOra.top + m.s5 + altaCifre, pOra);

        pData.setTextSize(m.corpo);
        pData.setColor(Tinte.TESTO_TENUE);
        c.drawText(rData.adatta(pData, testoData, rigaData.width() * 0.62f,
                        m.corpo, m.corpo * 0.78f),
                x, rigaData.centerY() - (pData.descent() + pData.ascent()) / 2f, pData);

        if (timer >= 0 && testoTimer != null) disegnaTimer(c);
    }

    /**
     * Il timer che scorre: una pastiglia all'estremita' destra della riga della
     * data.
     *
     * <b>Li' perche' li' non c'era niente.</b> La data occupa poco piu' di
     * meta' riga, e il resto era vuoto; il timer stava sotto, centrato, e
     * comparendo spingeva in giu' l'orologio. Un conto alla rovescia e' la
     * seconda cosa piu' vicina a un orologio che ci sia in questa casa, e sta
     * bene sulla sua stessa riga.
     */
    private void disegnaTimer(Canvas c) {
        pNota.setTextSize(m.corpo);
        pNota.setTextAlign(Paint.Align.LEFT);
        float lato = m.icona * 0.86f;
        float larga = m.s3 + lato + m.s2 + pNota.measureText(testoTimer) + m.s3;
        float sinistra = Math.max(rigaData.centerX(), rigaData.right - larga);

        RectF b = vetro.area(sinistra, rigaData.top, rigaData.right, rigaData.bottom);
        vetro.controllo(c, b, b.height() / 2f, Tinte.OROLOGIO, 0x33);

        pComando.setColor(Tinte.OROLOGIO);
        Icone.disegna(c, Icone.TIMER, sinistra + m.s3 + lato / 2f, b.centerY(), lato, pComando);
        pNota.setColor(Tinte.TESTO);
        c.drawText(testoTimer, sinistra + m.s3 + lato + m.s2,
                b.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
    }

    /**
     * La fascia sopra il microfono: quello che si e' detto, o come si dice.
     *
     * <b>Il vuoto e' un invito, non un buco.</b> Da quando la parola di
     * attivazione e' spenta, il microfono si apre solo premendo - e un tasto
     * che dice « parla » non dice ancora <i>cosa</i> si puo' chiedere. Finche'
     * non c'e' una frase vera, qui ci sta un esempio che funziona davvero:
     * chi lo legge una volta sa a cosa serve il tasto sotto, e chi lo ha gia'
     * letto non lo vede piu' perche' e' scritto smorto e piccolo.
     */
    private void disegnaStato(Canvas c) {
        boolean suggerimento = stato == null;
        String frase = suggerimento ? SUGGERIMENTO : stato;

        // Al massimo due righe. Due e non tre: la terza arriverebbe sul
        // microfono, e una frase che non sta in due righe a meta' schermo non
        // e' un comando, e' un discorso.
        String[] righe = bStato.in(pStato, frase, bandaStato.width(), m.nota, 2);
        if (righe.length == 0) return;

        // Le righe stanno strette fra loro: spaziarle le farebbe leggere come
        // due messaggi invece che come una frase sola.
        float passo = m.nota * 1.32f;
        float alto = righe.length * passo;
        float y = bandaStato.bottom - (bandaStato.height() - alto) / 2f - alto;

        pStato.setTextSize(m.nota);
        pStato.setColor(suggerimento ? Tinte.SPENTO : Tinte.TESTO_MEDIO);
        for (int i = 0; i < righe.length; i++) {
            c.drawText(righe[i], bandaStato.left, y + m.nota * 0.85f + i * passo, pStato);
        }
    }

    /**
     * Il pannello del meteo: com'e' adesso, e le prossime ore.
     *
     * <b>La tinta la sceglie il tempo</b>, non la Home: ambra col sole, azzurra
     * con la pioggia, blu di notte. E' la stessa idea della sezione Musica, che
     * prende il colore della copertina - un pannello che cambia colore da solo
     * dice che tempo fa da un metro, prima che si legga un numero.
     *
     * A sinistra la scena animata e i gradi; a destra le prossime ore, che
     * partono dalla <b>seconda</b>: la prima e' adesso, ed e' gia' scritta
     * grande accanto al disegno.
     */
    private void disegnaTempo(Canvas c) {
        Meteo.Adesso a = meteo != null ? meteo.adesso() : null;
        int tinta = a != null ? Cielo.tinta(a.codice, a.giorno) : Tinte.HOME;

        // <b>Il velo e' quello della Home, non quello del tempo.</b> Provato
        // col secondo: l'ambra del sole sopra il blu dello sfondo dava un
        // pannello grigio-verde in mezzo a tre schede azzurre - una macchia
        // spenta accanto a colori vivi, che si notava prima di qualunque cosa
        // ci fosse scritta dentro.
        //
        // Il colore del tempo c'e' lo stesso, ma dove va: nel disegno, nella
        // parola sotto i gradi, nelle icone delle ore. Sono gli accenti
        // piccoli, ed e' li' che sta forte - altrove e' una velatura che si
        // nota appena. E' la regola di tutta Casa, scritta in {@link Tinte}, e
        // qui era stata dimenticata.
        // Una superficie, non un pannello: fondo piatto appena piu' chiaro del
        // vetro che ha attorno, e nessun bordo suo. Il bordo ce l'ha gia' la
        // scheda che la contiene, e due cornici concentriche a due centimetri
        // l'una dall'altra non si leggono come « una cosa dentro un'altra » -
        // si leggono come una cornice doppia.

        float x0 = cardMeteo.left + m.s3, x1 = cardMeteo.right - m.s3;
        float largo = x1 - x0;

        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTextSize(m.nota);
        float yT = cardMeteo.top + m.s3 + m.micro;
        c.drawText(rMeteoDove.in(pTitolo, mDove, largo * 0.55f, m.micro), x0, yT, pTitolo);

        if (mMax != null && mMin != null) disegnaMinMax(c, x1, yT);

        if (a == null) {
            // Il silenzio senza spiegazione fa sembrare rotto il tablet: e' la
            // stessa regola della stazione che non parte.
            String perche = meteo != null && meteo.nota() != null
                    ? meteo.nota() : "sto guardando che tempo fa…";
            pNota.setColor(Tinte.SPENTO);
            pNota.setTextSize(m.corpo);
            pNota.setTextAlign(Paint.Align.LEFT);
            c.drawText(rMeteoNota.in(pNota, perche, largo, m.corpo),
                       x0, cardMeteo.centerY(), pNota);
            return;
        }

        cielo.disegna(c, scenaMeteo, a.codice, a.giorno);

        // I gradi e la parola sotto il disegno, sullo stesso filo di sinistra.
        float largoG = scenaMeteo.left + Math.max(scenaMeteo.width(), largo * 0.34f)
                     - scenaMeteo.left;
        float yg = scenaMeteo.bottom + m.s1 + m.titolo * 0.72f;

        pVoce.setColor(Tinte.TESTO);
        c.drawText(rMeteoGradi.adatta(pVoce, mGradi, largoG, m.titolo, m.voce * 0.8f),
                   scenaMeteo.left, yg, pVoce);
        // Sul cielo colorato la parola e' bianca, come nei widget di Apple: il
        // tempo lo dice gia' il fondo.
        pNota.setColor(Tinte.TESTO);
        pNota.setTextSize(m.nota);
        pNota.setTextAlign(Paint.Align.LEFT);
        c.drawText(rMeteoCond.adatta(pNota, mCondizione, largoG, m.nota, m.nota * 0.62f),
                   scenaMeteo.left, yg + m.s1 + m.nota, pNota);

        disegnaFasce(c);
    }

    // ---- le notizie, che si alternano al meteo ------------------------------

    /**
     * La superficie del meteo, che ogni tanto cede il posto alle notizie.
     *
     * <b>Si alternano nella stessa superficie</b>, e non in una scheda in
     * piu': le schede della Home sono tre, e il perche' sta in cima a questa
     * classe. Dieci secondi per uno, meteo e notizie - scelti da chi abita qui -
     * e il passaggio e' una dissolvenza: quel che c'era
     * sfuma salendo di poco, poi il nuovo sale al suo posto. Una dopo l'altra e
     * non incrociate: due testi sovrapposti a mezza opacita' sono una macchia.
     *
     * <b>Mentre ci sono le notizie il cielo si ferma</b>, e con lui i venti
     * fotogrammi al secondo: la superficie resta ferma finche' il battito
     * dell'orologio non decide il giro dopo. Alternare, qui, fa risparmiare.
     */
    private void disegnaMeteo(Canvas c) {
        vetro.incavo(c, cardMeteo, m.raggio, Tinte.HOME, premutoMeteo ? 0x2E : 0);
        if (cambio > 0L) {
            long t = Anima.ora() - cambio;
            if (t < SPEGNE) {
                float quanto = 1f - Anima.posa(t / (float) SPEGNE);
                if (inNotizie) cielo(c, quanto);
                superficie(c, !inNotizie, quanto, -(1f - quanto) * m.s2);
            } else if (t < SPEGNE + ACCENDE) {
                float quanto = Anima.posa((t - SPEGNE) / (float) ACCENDE);
                if (!inNotizie) cielo(c, quanto);
                superficie(c, inNotizie, quanto, (1f - quanto) * m.s3);
            } else {
                cambio = 0L;
                mostrata = Anima.ora();
                if (!inNotizie) cielo(c, 1f);
                superficie(c, inNotizie, 1f, 0f);
                return;
            }
            postInvalidateOnAnimation(riquadroMeteo.left, riquadroMeteo.top,
                                      riquadroMeteo.right, riquadroMeteo.bottom);
            return;
        }
        if (!inNotizie) cielo(c, 1f);
        superficie(c, inNotizie, 1f, 0f);
    }

    // ---- il cielo dietro il meteo -------------------------------------------

    /** Il gradiente del cielo, e per quale cielo e quale rettangolo e' stato
     *  fatto: si rifa' solo quando cambia uno dei due, non a ogni fotogramma -
     *  e il meteo ne disegna venti al secondo. */
    private final Paint pCielo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int cieloFatto = -1;
    private float cieloAlto = -1f, cieloBasso = -1f;

    /** Le coppie di colori, alto e basso, come i widget Meteo di Apple:
     *  sereno, nuvoloso, pioggia, neve, temporale; di giorno e di notte. */
    private static final int[][] CIELI = {
        { 0xFF2D7FE0, 0xFF6DB3F2 }, { 0xFF0C1631, 0xFF2B3B66 },   // sereno
        { 0xFF52677F, 0xFF8798AC }, { 0xFF1B2230, 0xFF3A4556 },   // nuvole
        { 0xFF3D4D61, 0xFF62728A }, { 0xFF141B26, 0xFF323D4E },   // pioggia
        { 0xFF7189A6, 0xFFA9BBD0 }, { 0xFF2A3446, 0xFF4E5B70 },   // neve
        { 0xFF2C2F45, 0xFF4F5270 }, { 0xFF12131F, 0xFF2D2E46 },   // temporale
    };

    /** Il gruppo di un codice WMO: 0 sereno, 1 nuvole, 2 pioggia, 3 neve,
     *  4 temporale. */
    private static int gruppoCielo(int codice) {
        if (codice <= 1) return 0;
        if (codice >= 95) return 4;
        if ((codice >= 71 && codice <= 77) || codice == 85 || codice == 86) return 3;
        if (codice >= 51) return 2;
        return 1;
    }

    /** Il widget del meteo si colora del tempo che fa: e' la prima cosa che si
     *  legge da un metro, prima di qualunque numero. */
    private void cielo(Canvas c, float quanto) {
        Meteo.Adesso a = meteo != null ? meteo.adesso() : null;
        if (a == null) return;
        int quale = gruppoCielo(a.codice) * 2 + (a.giorno ? 0 : 1);
        if (quale != cieloFatto || cieloAlto != cardMeteo.top || cieloBasso != cardMeteo.bottom) {
            cieloFatto = quale;
            cieloAlto = cardMeteo.top;
            cieloBasso = cardMeteo.bottom;
            pCielo.setShader(new LinearGradient(0, cardMeteo.top, 0, cardMeteo.bottom,
                    CIELI[quale][0], CIELI[quale][1], Shader.TileMode.CLAMP));
        }
        pCielo.setAlpha(Math.round((premutoMeteo ? 0xB0 : 0xFF) * quanto));
        c.drawRoundRect(cardMeteo, m.raggio, m.raggio, pCielo);
    }

    /**
     * Una delle due, con la sua opacita' e il suo spostamento.
     *
     * Il livello fuori schermo c'e' solo durante il passaggio, ed e' grande
     * quanto la superficie: sette decimi di secondo ogni dieci. La regola di
     * Casa - si muove la posizione, non l'opacita' - vale per le tessere che
     * entrano tutte insieme; qui ce n'e' una, e quel che deve dire e' proprio
     * « questo se ne va, arriva altro ».
     */
    private void superficie(Canvas c, boolean giornale, float quanto, float dy) {
        int s = c.save();
        if (quanto < 0.999f) c.saveLayerAlpha(cardMeteo, Math.round(255 * quanto));
        c.clipRect(cardMeteo);
        if (dy != 0f) c.translate(0f, dy);
        if (giornale && quanteNotizie > 0) disegnaNotizie(c);
        else disegnaTempo(c);
        c.restoreToCount(s);
    }

    /**
     * E' ora di cambiare? Lo chiede il battito dell'orologio una volta al
     * secondo: confrontare due numeri non costa niente, e cosi' il giro non ha
     * bisogno di un Handler suo.
     */
    private boolean alterna() {
        long ora = Anima.ora();
        if (cambio > 0L) return false;
        if (mostrata == 0L) { mostrata = ora; return false; }
        boolean ceNe = notizie != null && notizie.principali().length > 0;
        if (!inNotizie && !ceNe) { mostrata = ora; return false; }
        if (ora - mostrata < (inNotizie ? DURATA_NOTIZIE : DURATA_METEO)) return false;
        // Non sotto il dito, e non fuori scena: un giro che scatta mentre si
        // preme la superficie aprirebbe la pagina sbagliata, e uno che scatta
        // mentre si guarda un'altra sezione e' un giro che nessuno vede.
        if (premutoMeteo || getVisibility() != VISIBLE) { mostrata = ora; return false; }
        if (!inNotizie) {
            prossimeNotizie();
            if (quanteNotizie == 0) { mostrata = ora; return false; }
        }
        inNotizie = !inNotizie;
        cambio = ora;
        return true;
    }

    /**
     * I prossimi due titoli del giro, e il loro corpo.
     *
     * Il corpo e' quello da un metro - {@code voce} - se tutti e due i titoli
     * ci stanno interi nelle loro righe, e un gradino sotto per tutti e due se
     * no: un titolo intero un po' piu' piccolo si legge meglio di mezzo titolo
     * grande, e due titoli uno sotto l'altro a due grandezze diverse - la
     * prima versione, vista sul tablet - si leggono come un errore. Alloca, e
     * succede una volta ogni venti secondi.
     */
    private void prossimeNotizie() {
        quanteNotizie = 0;
        if (notizie == null || cardMeteo.isEmpty()) return;
        Notizie.Titolo[] t = notizie.principali();
        int giro = Math.min(t.length, GIRO_NOTIZIE);
        if (giro == 0) return;
        int primo = (giroNotizie * QUANTE_NOTIZIE) % giro;
        giroNotizie++;
        long adesso = System.currentTimeMillis();
        float largo = cardMeteo.width() - m.s3 * 2f;
        float passo = (fondoNotizie - cimaNotizie - m.s2 * (QUANTE_NOTIZIE - 1)) / QUANTE_NOTIZIE;
        float alto = passo - m.nota * 1.3f - m.s1;
        for (int k = 0; k < QUANTE_NOTIZIE && primo + k < giro; k++) {
            Notizie.Titolo n = t[primo + k];
            hTitolo[k] = n.testo;
            String quando = Notizie.fa(n.quando, adesso);
            hFonte[k] = n.fonte.length() == 0 ? quando
                      : quando.length() == 0 ? n.fonte : n.fonte + "  ·  " + quando;
            quanteNotizie = k + 1;
        }
        float corpo = m.voce;
        int righeGrandi = Math.max(1, (int) (alto / (m.voce * 1.2f)));
        for (int k = 0; k < quanteNotizie; k++) {
            String[] r = provaNotizia.in(pNotizia, hTitolo[k], largo, m.voce, righeGrandi);
            if (r.length > 0 && r[r.length - 1].endsWith("…") && !hTitolo[k].endsWith("…")) {
                corpo = m.corpo;
                break;
            }
        }
        for (int k = 0; k < quanteNotizie; k++) {
            corpoNotizia[k] = corpo;
            righeNotizia[k] = Math.max(1, (int) (alto / (corpo * 1.2f)));
        }
        long q = notizie.quandoPrincipali();
        hStato = q > 0 ? "aggiornate " + Notizie.fa(q, adesso) : "";
    }

    /** Due titoli, uno sotto l'altro: chi, quando, e il titolo. Con un filo
     *  fra i due, come le parti della giornata del meteo. */
    private void disegnaNotizie(Canvas c) {
        float x0 = cardMeteo.left + m.s3, x1 = cardMeteo.right - m.s3;
        float largo = x1 - x0;
        float yT = cardMeteo.top + m.s3 + m.micro;

        float lato = m.micro * 1.3f;
        pComando.setColor(Tinte.NOTIZIE);
        Icone.disegna(c, Icone.NOTIZIE, x0 + lato / 2f, yT - m.micro * 0.36f, lato, pComando);
        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTextSize(m.nota);
        c.drawText("Le notizie", x0 + lato + m.s2, yT, pTitolo);
        if (hStato.length() > 0) {
            pNota.setTextSize(m.micro);
            pNota.setColor(Tinte.TESTO_TENUE);
            pNota.setTextAlign(Paint.Align.RIGHT);
            c.drawText(rNotizieStato.in(pNota, hStato, largo * 0.5f, m.micro), x1, yT, pNota);
            pNota.setTextAlign(Paint.Align.LEFT);
        }

        float passo = (fondoNotizie - cimaNotizie - m.s2 * (QUANTE_NOTIZIE - 1)) / QUANTE_NOTIZIE;
        for (int i = 0; i < quanteNotizie; i++) {
            float y = cimaNotizie + (passo + m.s2) * i;
            if (i > 0) {
                pComando.setColor(FILO);
                float yf = y - m.s2 * 0.5f;
                c.drawRect(x0, yf, x1, yf + Math.max(1f, m.densita * 0.75f), pComando);
            }
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.con(Tinte.NOTIZIE, 0xEE));
            pNota.setTextAlign(Paint.Align.LEFT);
            float yF = y + m.nota;
            c.drawText(rNotizieFonte[i].in(pNota, hFonte[i], largo, m.nota), x0, yF, pNota);

            float corpo = corpoNotizia[i];
            String[] r = bNotizia[i].in(pNotizia, hTitolo[i], largo, corpo, righeNotizia[i]);
            pNotizia.setColor(Tinte.TESTO);
            float yR = yF + m.s1 + corpo;
            for (int k = 0; k < r.length; k++) c.drawText(r[k], x0, yR + k * corpo * 1.2f, pNotizia);
        }
    }

    /** La superficie toccata: apre quello che si sta guardando. */
    private void apriSuperficie() {
        if (inNotizie && quanteNotizie > 0) {
            if (paginaNotizie != null) paginaNotizie.apriNotizie();
        } else if (pagina != null) {
            pagina.apriMeteo();
        }
    }

    /**
     * La massima e la minima di oggi, in cima a destra.
     *
     * <b>Con le due frecce, e non « 32° / 23° ».</b> Due numeri separati da una
     * barra sono due numeri: quale sia la massima lo sa chi gia' sa che la
     * massima si scrive per prima. Le frecce lo dicono senza convenzioni, e
     * sono le stesse due che indicano su e giu' in tutto il resto di Casa.
     */
    private void disegnaMinMax(Canvas c, float x1, float yT) {
        pNota.setTextSize(m.micro);
        pNota.setColor(Tinte.TESTO_TENUE);
        pNota.setTextAlign(Paint.Align.RIGHT);
        pComando.setColor(Tinte.TESTO_TENUE);

        float lato = m.micro * 1.2f;
        float cy = yT - m.micro * 0.34f;
        float x = x1;

        c.drawText(mMin, x, yT, pNota);
        x -= pNota.measureText(mMin) + m.s1 * 0.5f;
        Icone.disegna(c, Icone.GIU, x - lato / 2f, cy, lato, pComando);
        x -= lato + m.s3;

        c.drawText(mMax, x, yT, pNota);
        x -= pNota.measureText(mMax) + m.s1 * 0.5f;
        Icone.disegna(c, Icone.SU, x - lato / 2f, cy, lato, pComando);

        pNota.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * Le prossime parti della giornata: com'e' il pomeriggio, com'e' la sera.
     *
     * L'icona qui e' un Material Symbol e non una scena animata: a due
     * centimetri una scena che si muove e' una macchia, e quello che serve e'
     * un segno che si riconosce. La scena grande e' una sola, ed e' « adesso ».
     */
    private void disegnaFasce(Canvas c) {
        Meteo.Fascia[] ff = fasce;
        int quante = Math.min(quanteFasce, ff.length);
        for (int i = 0; i < quante; i++) {
            RectF b = fasceMeteo[i];
            if (b.isEmpty() || mFasciaGradi[i] == null) continue;
            Meteo.Fascia f = ff[i];

            // <b>Un filo fra una riga e l'altra, e nessun riquadro.</b> Quattro
            // riquadri impilati dentro una superficie che e' gia' un riquadro,
            // dentro una scheda che e' un terzo riquadro, sono due cornici di
            // troppo: quello che serve e' dire dove finisce una riga e comincia
            // la successiva, e per dirlo basta una riga di luce.
            if (i > 0) {
                pComando.setColor(FILO);
                c.drawRect(b.left, b.top - m.s1 * 0.5f, b.right,
                           b.top - m.s1 * 0.5f + Math.max(1f, m.densita * 0.75f), pComando);
            }

            float cy = b.centerY();
            float x = b.left;

            // <b>I numeri si ancorano a destra, il nome a sinistra, e l'icona
            // sta in mezzo.</b> Prima la pioggia si appoggiava all'icona e i
            // gradi al bordo: in una riga larga due dita e mezzo i due gruppi
            // si incontravano, e « 20% » finiva attaccato a « 32° » come se
            // fossero un numero solo. Partendo dal bordo destro non possono
            // toccarsi per costruzione.
            pNota.setTextSize(m.corpo);
            float largoGradi = pNota.measureText(mFasciaGradi[i]);
            float xGradi = b.right;
            pNota.setColor(Tinte.TESTO);
            pNota.setTextAlign(Paint.Align.RIGHT);
            c.drawText(mFasciaGradi[i], xGradi,
                       cy - (pNota.descent() + pNota.ascent()) / 2f, pNota);

            if (mFasciaPioggia[i] != null) {
                pNota.setTextSize(m.micro);
                pNota.setColor(0xFFBFE3FF);
                c.drawText(mFasciaPioggia[i], xGradi - largoGradi - m.s2,
                           cy - (pNota.descent() + pNota.ascent()) / 2f, pNota);
            }
            pNota.setTextAlign(Paint.Align.LEFT);

            // Il segno del tempo a meta' riga: sta nello stesso punto in tutte
            // e quattro, che e' quello che le fa leggere come un elenco invece
            // che come quattro righe diverse.
            float lato = m.icona;
            float xIcona = b.left + b.width() * 0.52f;
            pComando.setColor(Tinte.con(Cielo.tinta(f.codice, f.giorno), 0xEE));
            Icone.disegna(c, Cielo.icona(f.codice, f.giorno), xIcona, cy, lato, pComando);

            // Il nome si prende tutto quello che resta a sinistra dell'icona:
            // e' la prima cosa che si legge, ed e' quella che dice se il numero
            // accanto riguarda fra due ore o domani.
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.TESTO_MEDIO);
            float largoNome = Math.max(m.dp(30), xIcona - lato * 0.6f - m.s2 - x);
            c.drawText(rFascia[i].adatta(pNota, f.nome, largoNome, m.nota, m.nota * 0.74f),
                       x, cy - (pNota.descent() + pNota.ascent()) / 2f, pNota);
            pNota.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * Rifa' le scritte del meteo.
     *
     * Si chiama quando il dato cambia - una volta ogni mezz'ora - e non dentro
     * onDraw, che qui gira venti volte al secondo: {@code String.format} alloca,
     * e venti oggetti al secondo per scrivere « 24° » sono esattamente il
     * genere di spesa che su 1 GB si paga a fine giornata.
     */
    private void aggiornaScritteMeteo() {
        // Solo il nome della citta', e non " METEO . ROMA ": dentro un
        // riquadro che ha un sole disegnato grande come una mano, la parola
        // " meteo " non aggiunge niente a nessuno - e' un'etichetta che
        // ripete l'immagine che le sta sotto.
        mDove = meteo != null
                ? meteo.citta() : "Meteo";
        Meteo.Adesso a = meteo != null ? meteo.adesso() : null;
        if (a == null) {
            mGradi = mCondizione = mMax = mMin = null;
            fasce = new Meteo.Fascia[0];
            return;
        }
        mGradi = gradi(a.gradi);
        mCondizione = Cielo.nome(a.codice);
        Meteo.Giorno oggi = meteo.oggi();
        mMax = oggi != null ? gradi(oggi.max) : null;
        mMin = oggi != null ? gradi(oggi.min) : null;

        // Le fasce si calcolano qui e non dentro onDraw: raggruppare
        // ventiquattro ore alloca, e questo pannello si ridisegna venti volte
        // al secondo.
        Meteo.Fascia[] ff = meteo.fasce(MAX_FASCE);
        for (int i = 0; i < MAX_FASCE; i++) {
            if (i >= ff.length) { mFasciaGradi[i] = mFasciaPioggia[i] = null; continue; }
            mFasciaGradi[i] = gradi(ff[i].gradi);
            mFasciaPioggia[i] = ff[i].pioggia >= 20 ? ff[i].pioggia + "%" : null;
        }
        // Per ultimo: e' l'array che il disegno guarda per sapere quante
        // tessere ci sono, e va pubblicato quando le scritte ci sono gia'.
        fasce = ff;
    }

    private static String gradi(float g) {
        return String.format(Locale.ITALIAN, "%.0f°", g);
    }

    /**
     * La scheda di quel che suona: il nome, e sotto - se c'e' da dirlo - in che
     * stato e'.
     *
     * <b>Erano una stringa sola e non dovevano esserlo.</b> La radio mandava
     * "Radiofreccia - mi collego": venticinque caratteri che a corpo intero
     * fanno mezza scheda di troppo, e - peggio della larghezza - il nome della
     * stazione finiva mescolato a un avviso che dura tre secondi. Adesso il
     * nome sta grande sulla sua riga e ci resta, e sotto passa lo stato: "mi
     * collego" smorto, l'errore in rosso.
     */
    private void disegnaMusica(Canvas c) {
        int chi = chiComanda();

        // Il titolo della scheda dice CHI sta suonando. Con Spotify aperta si
        // legge SPOTIFY, e i tre tasti sotto sono i suoi: e' il modo piu' corto
        // per far capire che quella scheda ha appena cambiato mestiere.
        // « In riproduzione » e non « ora in riproduzione »: l'« ora » era la
        // parola piu' lunga dell'etichetta e non diceva niente che « niente in
        // riproduzione » due centimetri sotto non dicesse gia'.
        String intestazione = "In riproduzione";
        String cosa, nota;
        int colNota = Tinte.TESTO_TENUE;

        if (chi == COMANDA_MUSICA) {
            // La musica di Casa non passa da MediaSessionManager - la scheda
            // salta le sessioni di Casa apposta - quindi si legge dritta da
            // Musica, che di quel brano sa piu' di chiunque altro.
            intestazione = "Spotify";
            cosa = musica.brano();
            nota = musica.artista();
        } else if (chi == COMANDA_FUORI) {
            String brano = riproduzione.titolo();
            if (brano != null) {
                String app = riproduzione.nome();
                if (app != null) intestazione = app;
                cosa = brano;
                nota = riproduzione.artista();
            } else {
                // C'e' una sessione ma non il titolo: o manca il permesso di
                // ascoltare le notifiche, o quell'app non pubblica i metadati.
                // Si dice quel che si sa - i tasti funzionano comunque - invece
                // di scrivere "niente in riproduzione" mentre si sente musica.
                cosa = riproduzione.nome() != null ? riproduzione.nome() : "un'altra app";
                nota = null;
            }
        } else if (chi == COMANDA_ULTIMO) {
            // Il nome di quello che ripartirebbe, scritto prima che qualcuno
            // prema: un tasto play sopra una riga vuota e' un tasto di cui non
            // ci si fida, e chi non si fida va ad aprire Spotify.
            Ultimo u = ultimo();
            intestazione = "L'ultimo ascolto";
            cosa = u.titolo;
            nota = u.nota != null ? u.nota : "premi per riprendere";
        } else {
            cosa = radio;
            nota = notaSorgente();
            if (sorgenteRadio != null && !sorgenteRadio.staAprendo() && sorgenteRadio.errore() != null) {
                colNota = Tinte.ALLARME;
            }
        }

        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTextSize(m.nota);
        c.drawText(rTitoloMusica.in(pTitolo, intestazione, larghezzaMusica, m.nota),
                   cardMusica.left + m.s4, cardMusica.top + m.s3 + m.micro, pTitolo);

        disegnaSorgente(c, chi);

        float x = bandaMusica.left;
        float largo = bandaMusica.width();
        float primo = cosa != null ? m.voce : m.corpo;
        float passo = m.nota * 1.32f;
        // Il nome parte dall'alto del blocco, non dal suo centro: sotto ci
        // stanno la nota e le colonne, e un testo centrato su tutto il blocco
        // scavalcherebbe le une o le altre a seconda che l'artista ci sia.
        float y = bandaMusica.top + primo * 0.82f;

        if (cosa != null) {
            // Smorto quando e' un ricordo e non un suono: alla stessa
            // grandezza e dello stesso bianco, « l'ultimo ascolto » si
            // leggerebbe come « sta suonando ».
            pVoce.setColor(chi == COMANDA_ULTIMO ? Tinte.TESTO_MEDIO : Tinte.TESTO);
            pVoce.setTextSize(m.voce);
            c.drawText(rNome.adatta(pVoce, cosa, largo, m.voce, m.voce * 0.64f), x, y, pVoce);
        } else {
            // Il vuoto si scrive piccolo. Scritto grande com'era prima, "niente
            // in riproduzione" era la cosa piu' vistosa della schermata.
            pNota.setColor(Tinte.SPENTO);
            pNota.setTextSize(m.corpo);
            c.drawText(rVuoto.in(pNota, "niente in riproduzione", largo, m.corpo), x, y, pNota);
        }

        if (nota != null) {
            pNota.setColor(colNota);
            pNota.setTextSize(m.nota);
            c.drawText(rNota.in(pNota, nota, largo, m.nota), x, y + passo, pNota);
        }

        int colore = coloreDi(chi);
        disegnaBarre(c, colore, chi, staSuonando(chi));
        disegnaVolume(c);
        disegnaComandi(c, chi);
    }

    /** Se quello che ha in mano i tasti sta suonando davvero. */
    private boolean staSuonando(int chi) {
        if (chi == COMANDA_MUSICA) return musica.staSuonando();
        if (chi == COMANDA_FUORI) return riproduzione.staSuonando();
        return sorgenteRadio != null && sorgenteRadio.staSuonando();
    }

    /**
     * Le colonne del suono.
     *
     * <b>Simmetriche attorno alla loro linea, e non appoggiate a un
     * pavimento.</b> Un equalizzatore che cresce dal basso vuole un pavimento
     * per stare in piedi - una riga sotto, o un bordo - e quello sarebbe il
     * quarto contorno di questa scheda. Cresciute dal centro non hanno bisogno
     * di niente: la linea gliela fanno loro, ed e' anche quello che si vede
     * quando c'e' silenzio, perche' a livello zero restano una fila di
     * trattini alti quanto sono larghi.
     *
     * Il silenzio quindi ha un disegno suo e non e' un buco nella scheda. Ed e'
     * un disegno onesto: sta fermo perche' non sta uscendo niente.
     */
    private void disegnaBarre(Canvas c, int colore, int chi, boolean suona) {
        // Il visualizzatore vive solo mentre serve, e si aggancia alla sessione
        // di chi sta suonando: quella del MediaPlayer della radio, quella
        // dell'AudioTrack della musica di Casa. Col microfono aperto si stacca
        // - sull'ingresso audio di un MT6580 e' meglio non avere due cose
        // insieme - e con un'altra app (chi == COMANDA_FUORI) non c'e' nessuna
        // sessione da chiedere: le colonne restano a riposo, che e' la risposta
        // vera - quel suono non lo stiamo facendo noi.
        if (suona && !inAscolto) livelli.accendi(sessioneDi(chi)); else livelli.spegni();
        livelli.aggiorna();

        float cy = bandaBarre.centerY();
        float largo = bandaBarre.width();
        // Il vuoto e' largo piu' del doppio della linea: sono fili, non
        // colonne. Con le colonne larghe - il primo tentativo - lo spettro era
        // una fila di mattoni, e da fermo una fila di bolli grossi in mezzo
        // alla scheda.
        float larga = largo / (Livelli.BANDE + (Livelli.BANDE - 1) * 1.25f);
        float raggio = larga * 0.5f;
        float massima = bandaBarre.height() * 0.5f;

        // A riposo sono una <b>riga tratteggiata</b>, non una fila di pallini.
        // Coi pallini - alti quanto larghi - il silenzio erano sedici bolli
        // grossi in mezzo alla scheda, che si vedevano piu' delle colonne
        // quando suonavano: il riposo deve essere una linea sottile che si
        // dimentica, e il movimento deve essere l'unica cosa che si nota.
        float minima = Math.max(1.5f, m.densita * 1.5f);
        float x = bandaBarre.left;
        for (int i = 0; i < Livelli.BANDE; i++) {
            float q = livelli.livello(i);
            float mezza = minima + (massima - minima) * q;
            // Il colore segue l'altezza: le colonne basse restano nel grigio
            // del resto della scheda e solo quelle che salgono prendono il
            // colore di quel che suona. Senza, a musica ferma la fila di
            // trattini era una riga colorata sotto un nome spento.
            pComando.setColor(Tinte.con(colore, (int) (0x30 + 0xC0 * q)));
            float rr = Math.min(raggio, mezza);
            c.drawRoundRect(vetro.area(x, cy - mezza, x + larga, cy + mezza),
                    rr, rr, pComando);
            x += larga * 2.25f;
        }

        // Il prossimo fotogramma solo se c'e' ancora qualcosa che si muove, e
        // solo su questo rettangolo: e' la stessa disciplina del cielo del
        // meteo, e per la stessa ragione - sedici ore al giorno.
        if (livelli.qualcosaSiMuove() || (suona && !inAscolto)) {
            postInvalidateDelayed(50, riquadroBarre.left, riquadroBarre.top,
                                  riquadroBarre.right, riquadroBarre.bottom);
        }
    }

    /**
     * Il colore di chi sta suonando adesso.
     *
     * Serve al cursore del volume, che sta fuori dalla fila dei tasti ma
     * comanda la stessa cosa: se i tasti sono verdi perche' suona Spotify, il
     * volume che si sta per abbassare e' quello, e deve dirlo prima che il
     * dito lo tocchi.
     *
     * <b>E quando non suona niente non e' di nessun colore.</b> Prima era
     * comunque il verde della Radio, anche a radio spenta: su una schermata in
     * cui l'unica cosa colorata era il tasto « avvia », la cosa che si vedeva
     * per prima entrando in casa era un bottone verde acceso che significava
     * <i>non sta suonando niente</i>. Il colore dice « questo sta andando », e
     * quando non va niente non deve dire niente.
     */
    private int coloreDi(int chi) {
        if (chi == COMANDA_MUSICA) {
            return musica.staSuonando() ? Tinte.SPOTIFY : Tinte.TESTO_MEDIO;
        }
        if (chi == COMANDA_FUORI) {
            return riproduzione.staSuonando() ? Tinte.APP : Tinte.TESTO_MEDIO;
        }
        if (sorgenteRadio != null && sorgenteRadio.staSuonando()) {
            Radio.Stazione s = stazione();
            return s != null ? s.colore : Tinte.RADIO;
        }
        return Tinte.TESTO_MEDIO;
    }

    /**
     * Il volume: l'altoparlante che azzera, la barra, e la percentuale.
     *
     * <b>La percentuale c'e' apposta.</b> Un cursore lungo trecento pixel senza
     * numero non si rimette dov'era: si sposta di un gradino e non si sa se e'
     * andato su di uno o di tre. E i gradini sono quelli del sistema - quindici
     * su questo tablet - non un continuo: il cursore si posa su uno di quelli,
     * e il numero e' quello vero.
     */
    private void disegnaVolume(Canvas c) {
        boolean zitto = volume <= 0.001f;

        // Meno e piu', non due altoparlanti. I due segni dell'altoparlante -
        // quello con un'onda e quello con tre - dicono « basso » e « alto »,
        // non « togli » e « aggiungi »: accanto a un numero si leggono come lo
        // stato del volume, e uno si chiede perche' ce ne siano due.
        tastoTondo(c, tastoMeno, Icone.MENO, premutoMeno, zitto);
        tastoTondo(c, tastoPiu,  Icone.PIU,  premutoPiu,  volume >= 0.999f);

        // Il numero e' il pezzo grosso dei tre: e' quello che si legge da
        // lontano, e i due tasti servono solo a spostarlo. Scritto piccolo fra
        // due tondi grandi sarebbe l'accessorio di due pulsanti, che e' il
        // contrario di quello che sono.
        pVoce.setColor(zitto ? Tinte.TESTO_TENUE : Tinte.TESTO);
        pVoce.setTextSize(m.voce);
        pVoce.setTextAlign(Paint.Align.CENTER);
        c.drawText(testoVolume, areaValore.centerX(),
                areaValore.centerY() - (pVoce.descent() + pVoce.ascent()) / 2f, pVoce);
        pVoce.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * Un tasto tondo con dentro un segno: meno, piu'.
     *
     * <b>Contornato e non pieno.</b> Alzare il volume non e' l'azione di questa
     * scheda - l'azione e' quello che sta suonando - e due tondi pieni accanto
     * al tasto di avvio farebbero tre pulsanti pieni in una riga sola, cioe'
     * nessuno che spicca. Il fondo si accende solo sotto il dito.
     *
     * @param finito true quando da quella parte non c'e' piu' strada: il segno
     *               si smorza e il tasto resta dov'era. Sparire sposterebbe
     *               l'altro tasto e il numero, e un comando che si muove quando
     *               arriva a fondo scala e' peggio di uno che non fa niente.
     */
    private void tastoTondo(Canvas c, RectF b, int segno, boolean giu, boolean finito) {
        float r = b.height() * 0.5f;
        // Tondi grigi e icone bianche, come i comandi del Centro di Controllo.
        pFondo.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(b, r, r, pFondo);
        pComando.setColor(finito ? Tinte.SPENTO : Tinte.TESTO);
        Icone.disegna(c, segno, b.centerX(), b.centerY(), m.icona * 0.92f, pComando);
    }

    /** La sessione audio di chi sta suonando, per le colonne del suono. Zero
     *  quando non c'e' niente da agganciare: un'altra app non ce la da'. */
    private int sessioneDi(int chi) {
        if (chi == COMANDA_MUSICA) return musica != null ? musica.sessioneAudio() : 0;
        if (chi == COMANDA_RADIO) return sorgenteRadio != null ? sorgenteRadio.sessioneAudio() : 0;
        return 0;
    }

    // ---- il volume, dietro le quinte ---------------------------------------

    /**
     * Rilegge il volume dal sistema.
     *
     * Si richiama una volta al secondo dal tick, e non e' spreco: i tasti
     * fisici sul fianco del tablet cambiano il volume senza dirlo a nessuno, e
     * un cursore che resta dov'era mentre il suono cala e' peggio che non
     * averlo. Costa una chiamata al servizio audio al secondo, e ridisegna solo
     * se e' cambiato davvero.
     *
     * @return true se e' cambiato
     */
    private boolean leggiVolume() {
        if (audio == null) return false;
        float v;
        try {
            v = audio.getStreamVolume(TuboAudio.flusso()) / (float) gradini;
        } catch (Exception fuoriMano) {
            return false;
        }
        if (Math.abs(v - volume) < 0.0005f) return false;
        volume = v;
        // Se il volume non e' piu' quello che avevamo chiesto - i tasti sul
        // fianco del tablet lo spostano di un gradino senza dirlo a nessuno -
        // il decimo che stavamo mostrando non vale piu', e si torna al numero
        // vero. Mezzo gradino e' la soglia: il nostro stesso decimo, dopo
        // l'arrotondamento del sistema, ne dista al massimo mezzo.
        if (decine >= 0 && Math.abs(v - decine / 10f) > 0.55f / gradini) decine = -1;
        componiVolume();
        return true;
    }

    private void metti(float quanto) {
        float q = Math.max(0f, Math.min(1f, quanto));
        volume = q;
        componiVolume();
        if (audio == null) return;
        try {
            audio.setStreamVolume(TuboAudio.flusso(), Math.round(q * gradini), 0);
        } catch (SecurityException vietato) {
            // Con « non disturbare » acceso il sistema puo' rifiutarsi: si
            // rilegge quello vero, invece di lasciare a schermo un cursore che
            // dice una cosa e un altoparlante che ne fa un'altra.
            leggiVolume();
        }
    }

    /**
     * Un gradino su o giu'.
     *
     * <b>Dieci per cento a tocco, e il numero che si vede e' quello.</b> I
     * gradini del sistema sono quindici, e i decimi cadono fra due gradini una
     * volta su due: il piu' vicino e' al massimo tre per cento lontano, che in
     * una stanza non lo sente nessuno. Si mostra il decimo, e non il gradino,
     * perche' due tocchi uguali devono dare due volte lo stesso salto - un
     * tasto che a volte fa sette e a volte sei sembra rotto, anche quando e'
     * il piu' onesto dei due.
     *
     * Se il volume cambia da fuori - i tasti sul fianco del tablet - il decimo
     * si dimentica e si torna a mostrare il numero vero: vedi
     * {@link #leggiVolume()}.
     */
    private void sposta(int verso) {
        if (decine < 0) decine = Math.round(volume * 10f);
        decine = Math.max(0, Math.min(10, decine + verso));
        metti(decine / 10f);
        componiVolume();
    }

    /** Il decimo che si sta mostrando, o -1 per mostrare il volume vero. */
    private int decine = -1;

    private void componiVolume() {
        testoVolume = (decine >= 0 ? decine * 10 : Math.round(volume * 100f)) + "%";
    }

    /**
     * Il quadratino di chi sta suonando.
     *
     * Il logo della stazione se c'e' - sono gia' in memoria, li usa la sezione
     * Radio - il marchio Spotify per la musica di Casa, l'icona generica per
     * un'altra app. Costa un {@code drawBitmap} e dice, prima di qualunque
     * scritta, <b>da dove viene il suono</b>: che e' la domanda che ci si fa
     * guardando la Home da lontano.
     */
    private void disegnaSorgente(Canvas c, int chi) {
        boolean ultimoMusica = chi == COMANDA_ULTIMO && ultimo().cosa == Ultimo.MUSICA;
        int colore = chi == COMANDA_MUSICA || ultimoMusica ? Tinte.SPOTIFY
                   : chi == COMANDA_FUORI ? Tinte.APP : Tinte.RADIO;
        boolean ceQualcosa = chi != COMANDA_RADIO || stazione() != null;
        vetro.controllo(c, sorgente, m.raggioPiccolo, colore, ceQualcosa ? 0x24 : 0x00);

        // Se sta suonando un disco, la sua copertina: e' l'immagine piu'
        // informativa che ci sia in questa scheda, e a questa misura costa
        // qualche kilobyte in una cache che c'e' gia'.
        android.graphics.Bitmap logo = null;
        if (chi == COMANDA_MUSICA && copertine != null && musica.copertina() != null) {
            logo = copertine.prendi(musica.copertina(), (int) sorgente.width());
            if (logo != null) {
                c.drawBitmap(logo, null, sorgente, pLogo);
                return;
            }
        }

        // Anche l'ultimo ascolto ha il suo logo: e' quello che si riconosce da
        // un metro, e riconoscere che cosa ripartirebbe e' proprio il punto.
        Radio.Stazione s = chi == COMANDA_ULTIMO ? stazioneUltima() : stazione();
        if ((chi == COMANDA_RADIO || chi == COMANDA_ULTIMO) && s != null && loghi != null) {
            logo = loghi.prendi(s.logo);
        }
        if (logo != null) {
            float lato = sorgente.width() * 0.66f;
            RectF dove = vetro.area(sorgente.centerX() - lato / 2f,
                    sorgente.centerY() - lato / 2f,
                    sorgente.centerX() + lato / 2f, sorgente.centerY() + lato / 2f);
            c.drawBitmap(logo, null, dove, pLogo);
            return;
        }

        // Spento quando non c'e' niente: col colore della radio, a radio
        // ferma, il quadratino era una macchia verde accanto a « niente in
        // riproduzione » - di nuovo il colore addosso a una cosa che non sta
        // succedendo.
        pComando.setColor(ceQualcosa ? Tinte.con(colore, 0xEE) : Tinte.SPENTO);
        Icone.disegna(c, chi == COMANDA_MUSICA || ultimoMusica ? Icone.SPOTIFY
                       : chi == COMANDA_RADIO || chi == COMANDA_ULTIMO
                         ? Icone.RADIO_PIENA : Icone.MUSICA_PIENA,
                sorgente.centerX(), sorgente.centerY(), sorgente.width() * 0.5f, pComando);
    }

    /** La stazione accesa, o null. */
    private Radio.Stazione stazione() {
        return sorgenteRadio != null ? sorgenteRadio.stazioneCorrente() : null;
    }

    /**
     * Chi hanno in mano i tre tasti, adesso.
     *
     * <b>La musica di Casa viene prima</b>: e' quella che sta uscendo
     * dall'altoparlante di questo tablet, e i tasti devono comandare quello
     * che si sente, non quello che c'e' in pausa da qualche parte. Radio e
     * musica non suonano mai insieme - accenderne una spegne l'altra - ma il
     * controllo sulla radio resta, perche' il caso "radio accesa" e' quello in
     * cui il brano vecchio di Spotify sarebbe ancora in memoria.
     */
    private int chiComanda() {
        boolean radioAccesa = sorgenteRadio != null && sorgenteRadio.staSuonando();
        if (musica != null && musica.ceUnBrano() && !radioAccesa) return COMANDA_MUSICA;
        if (telecomandoEsterno()) return COMANDA_FUORI;
        // « Sta aprendo » conta come accesa, e non e' pignoleria: fra il tocco
        // e il primo suono passano dei secondi, e in quei secondi la scheda
        // deve continuare a dire « mi collego » su quella stazione - non
        // scivolare a proporre l'ultimo ascolto, che sarebbe la stessa cosa
        // scritta come se non stesse gia' succedendo.
        boolean radioViva = sorgenteRadio != null
                && (sorgenteRadio.staSuonando() || sorgenteRadio.staAprendo());
        if (!radioViva && ultimo().ceQualcosa()) return COMANDA_ULTIMO;
        return COMANDA_RADIO;
    }

    /** L'ultima cosa ascoltata. Sta in {@link Ultimo}, che la tiene in memoria
     *  e tocca il disco una volta sola. */
    private Ultimo ultimo() { return Ultimo.attuale(getContext()); }

    /** La stazione da rimettere, se l'ultima cosa era radio ed e' ancora in
     *  elenco. Null se il PC l'ha tolta nel frattempo. */
    private Radio.Stazione stazioneUltima() {
        Ultimo u = ultimo();
        if (u.cosa != Ultimo.RADIO || sorgenteRadio == null) return null;
        return sorgenteRadio.trova(u.chiave);
    }

    /**
     * Chi comandano i tre tasti in questo momento.
     *
     * Un'altra app con una sessione media ha la precedenza - premere pausa
     * mentre suona Spotify deve fermare Spotify, non accendere la radio - ma
     * <b>la nostra radio, se sta suonando, se li riprende</b>: e' quella che si
     * sente uscire, e Spotify aperta e in pausa dietro non deve rubarle i
     * tasti.
     */
    private boolean telecomandoEsterno() {
        if (riproduzione == null || !riproduzione.ceQualcuno()) return false;
        if (sorgenteRadio != null && sorgenteRadio.staSuonando()) return false;
        return true;
    }

    /**
     * Che cosa sta facendo la radio, quando non e' semplicemente suonare.
     *
     * Si chiede all'apparecchio nel momento in cui si disegna, che e' l'unico
     * momento in cui la risposta e' vera. L'errore si mostra anche qui e non
     * solo nella sezione Radio: una stazione che non parte lasciava la Home a
     * "niente in riproduzione", e il silenzio senza spiegazione fa sembrare
     * rotto il tablet invece della stazione.
     */
    private String notaSorgente() {
        if (sorgenteRadio == null) return null;
        if (sorgenteRadio.staAprendo()) return "mi collego…";
        return sorgenteRadio.errore();
    }

    /**
     * Indietro, ferma/avvia, avanti - qui e non solo nella sezione Radio.
     *
     * Cambiare stazione e' la cosa che si fa piu' spesso, e la Home e' la
     * schermata che si guarda per prima: farla costare due tocchi in piu' e'
     * il genere di attrito che porta a non usare piu' la funzione.
     */
    private void disegnaComandi(Canvas c, int chi) {
        if (chi == COMANDA_RADIO && sorgenteRadio == null) return;

        // Il colore dice a chi appartengono i tasti: e' l'unica cosa che si
        // nota da un metro, ed e' quella che dice se il tasto che si sta per
        // premere ferma la radio, la musica o Netflix.
        boolean suona;
        int colore;
        if (chi == COMANDA_ULTIMO) {
            // Niente sta suonando, quindi il tasto di mezzo e' « avvia » e non
            // e' pieno di colore: il colore dice « questo sta andando ». Il
            // colore serve pero' per il tocco, ed e' quello di cio' che
            // ripartirebbe - verde radio o verde Spotify.
            suona = false;
            colore = ultimo().cosa == Ultimo.MUSICA ? Tinte.SPOTIFY : Tinte.RADIO;
        } else if (chi == COMANDA_MUSICA) {
            suona = musica.staSuonando();
            colore = Tinte.SPOTIFY;
        } else if (chi == COMANDA_FUORI) {
            suona = riproduzione.staSuonando();
            colore = Tinte.APP;
        } else {
            suona = sorgenteRadio.staSuonando();
            colore = suona && sorgenteRadio.stazioneCorrente() != null
                    ? sorgenteRadio.stazioneCorrente().colore : Tinte.RADIO;
        }

        // Indietro e avanti hanno senso su una radio - sono le stazioni - e
        // non ne hanno su un brano che non e' ancora partito: li' non c'e'
        // nessun « precedente » di cui parlare. Un tasto che non fa niente si
        // vede che non lo fa, invece di lasciarlo provare.
        boolean lateraliVivi = chi != COMANDA_ULTIMO || ultimo().cosa == Ultimo.RADIO;

        for (int i = 0; i < comandi.length; i++) {
            RectF b = comandi[i];
            float cx = b.centerX(), cy = b.centerY();

            // Il tasto di mezzo e' pieno, gli altri no: e' quello che si preme,
            // e su una fila di tre uguali si perde mezzo secondo a cercarlo.
            // Pieno pero' non vuol dire colorato: finche' non suona niente e'
            // un tondo chiaro come tutti gli altri comandi della schermata, e
            // il colore arriva quando c'e' qualcosa che lo giustifica.
            // Alla Apple: il tasto di mezzo e' un tondo bianco pieno con il
            // segno scuro, e quando suona prende il colore di chi suona. I due
            // laterali sono solo il segno, e un tondo grigio sotto il dito.
            if (i == 1) {
                pFondo.setColor(suona ? colore : Tinte.TESTO);
                if (premutoComando == 1) pFondo.setAlpha(0xB0);
                c.drawCircle(cx, cy, b.width() * 0.5f, pFondo);
            } else if (premutoComando == i) {
                pFondo.setColor(Tinte.RIEMPIMENTO_SCELTO);
                c.drawCircle(cx, cy, b.width() * 0.5f, pFondo);
            }

            int segno;
            if (i == 0) segno = Icone.PRECEDENTE;
            else if (i == 2) segno = Icone.SUCCESSIVO;
            // Il quadrato dice « stop », e per la radio e' vero - un flusso in
            // diretta non si riprende - ma per un brano no: li' sono due barre,
            // e quello che si ferma riparte da dove stava.
            else if (!suona) segno = Icone.AVVIA;
            else segno = chi == COMANDA_RADIO ? Icone.FERMA : Icone.PAUSA;

            pComando.setColor(i == 1 ? (suona ? Tinte.TESTO : Tinte.TESTO_SU_CHIARO)
                    : !lateraliVivi ? Tinte.SPENTO : Tinte.TESTO);
            Icone.disegna(c, segno, cx, cy, b.width() * (i == 1 ? 0.46f : 0.40f), pComando);
        }
    }

    /**
     * La scheda di casa: la sveglia, e le lampade.
     *
     * Prima erano due righe di testo grande - « nessuna accesa », « nessuna
     * sveglia » - scritte a corpo cinquantadue in mezzo a una scheda vuota:
     * le due cose che <i>non</i> stavano succedendo erano la roba piu' vistosa
     * della schermata. Poi sono diventate due righe piccole con la loro icona,
     * e sotto tre routine da premere.
     *
     * <b>Adesso sotto ci sono le lampade</b>, e il riassunto delle luci non
     * c'e' piu': « nessuna accesa » sopra tre righe che dicono « spenta » e'
     * la stessa cosa scritta due volte, e la seconda si puo' anche premere.
     * Le routine stanno nella sezione, che si apre premendo la scheda.
     */
    private void disegnaCasa(Canvas c) {
        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTextSize(m.nota);
        c.drawText(rTitoloCasa.in(pTitolo, "In casa", larghezzaCasa, m.nota),
                   cardCasa.left + m.s4, cardCasa.top + m.s3 + m.micro, pTitolo);

        riga(c, tessSveglia, sveglia != null ? Icone.SVEGLIA_PIENA : Icone.SVEGLIA,
                testoSveglia, sveglia != null ? Tinte.OROLOGIO : Tinte.SPENTO, rSveglia);

        disegnaLuci(c);
    }

    /**
     * Una riga di stato della scheda di casa: l'icona, e accanto cosa succede.
     *
     * <b>Senza riquadro, e non e' una sottrazione.</b> Con il riquadro era
     * identica alle righe delle lampade qui sotto - stesso raggio, stesso
     * bordo, stessa altezza - e non si preme: un finto tasto sopra dei tasti
     * veri. In una schermata che si guarda da lontano e si tocca al buio,
     * l'unica cosa che distingueva quello che risponde da quello che non
     * risponde era provarci.
     */
    private void riga(Canvas c, RectF b, int segno, String testo, int colore,
                      Testo.Riga riga) {
        boolean vivo = colore != Tinte.SPENTO;
        float lato = m.icona * 0.92f;
        float x = b.left;
        pComando.setColor(colore);
        Icone.disegna(c, segno, x + lato / 2f, b.centerY(), lato, pComando);

        x += lato + m.s2;
        pNota.setColor(vivo ? Tinte.TESTO : Tinte.TESTO_TENUE);
        pNota.setTextSize(m.nota);
        pNota.setTextAlign(Paint.Align.LEFT);
        c.drawText(riga.adatta(pNota, testo == null ? "" : testo, b.right - x,
                        m.nota, m.nota * 0.74f),
                x, b.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
    }

    /**
     * Le lampade: fino a tre, accese e spente da qui.
     *
     * Al posto delle routine, che erano tre scorciatoie da premere una volta al
     * giorno. Il posto e' lo stesso e la riga si disegna allo stesso modo: e'
     * quello che ci si aspetta da una fila di righe premibili dentro una
     * scheda, e cambiare anche il disegno avrebbe fatto sembrare nuova una
     * cosa che e' solo diventata piu' utile.
     */
    private void disegnaLuci(Canvas c) {
        if (apparecchioLuci == null) return;
        java.util.List<Lampada> elenco = apparecchioLuci.elenco();
        for (int i = 0; i < MAX_LUCI; i++) {
            RectF b = areeLuci[i];
            if (b.isEmpty() || i >= elenco.size()) continue;
            Lampada l = elenco.get(i);
            boolean accesa = l.accesa();
            String guasto = l.guasto();
            boolean giu = premutaLuce == i;

            // <b>Il colore sta nell'icona, non addosso a tutta la riga.</b> E'
            // la regola che era gia' delle routine qui: un velo colorato su una
            // pastiglia lunga quanto la scheda si legge come una barra riempita
            // a meta'. Il velo resta, ma dice una cosa sola e vera - questa
            // lampada e' accesa - e per il resto la riga e' neutra come tutti
            // gli altri comandi della schermata.
            // Come le tessere della Casa di Apple: accesa diventa chiara, col
            // testo scuro, e si vede da un metro senza leggere niente; spenta
            // e' un grigio come ogni altro comando.
            boolean chiara = accesa && guasto == null;
            pFondo.setColor(chiara ? Tinte.TESSERA_ACCESA
                    : giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
            if (chiara && giu) pFondo.setAlpha(0xC8);
            c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pFondo);

            float lato = m.icona * 0.74f;
            float x = b.left + m.s3;
            // L'icona sta in un tondo: colorato della lampada se e' accesa.
            float rTondo = lato * 0.86f;
            pFondo.setColor(chiara ? Tinte.addomestica(l.colore) : Tinte.RIEMPIMENTO);
            c.drawCircle(x + rTondo, b.centerY(), rTondo, pFondo);
            x += rTondo - lato / 2f;
            // Addomesticato, non com'e' scritto nel file: il colore di una
            // lampada arriva da fuori dal codice e puo' essere qualunque cosa,
            // compreso un blu notte che su un fondo blu notte non si vede. Ne
            // esce sempre una tinta viva, e resta riconoscibile come la sua.
            pComando.setColor(guasto != null ? Tinte.ALLARME
                    : chiara ? Tinte.TESTO : Tinte.TESTO_MEDIO);
            Icone.disegna(c, accesa ? Icone.LAMPADA_PIENA : Icone.LAMPADA,
                    x + lato / 2f, b.centerY(), lato, pComando);

            // A destra, com'e'. « Spenta » e' informazione, il vuoto no - e' la
            // stessa riga che ogni tessera ha nella sezione - e quando la
            // lampada non risponde lo si scrive: il silenzio senza spiegazione
            // fa sembrare rotto il tablet invece della lampadina.
            String stato = l.inCorso ? "…"
                         : guasto != null ? guasto
                         : accesa ? "accesa" : "spenta";
            // <b>Tenue e non spento</b>, e questa e' misurata sullo schermo del
            // tablet: {@code SPENTO} e' 0x55636F, e il vetro della scheda di
            // casa - che pesca dal verde-azzurro del fondo - li' sotto e'
            // 0x386681. Sono due colori con la stessa luminosita': « spenta »
            // c'era, ed era invisibile. Nella sezione lo stesso grigio si legge
            // benissimo, perche' li' la tessera ha un fondo suo.
            pNota.setTextSize(m.nota);
            pNota.setColor(guasto != null ? Tinte.ALLARME
                    : chiara ? Tinte.TESTO_SU_CHIARO_TENUE : Tinte.TESTO_TENUE);
            String scritto = rStatoLuce[i].in(pNota, stato, b.width() * 0.42f, m.nota);
            float largoStato = pNota.measureText(scritto);
            pNota.setTextAlign(Paint.Align.RIGHT);
            c.drawText(scritto, b.right - m.s4,
                    b.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
            pNota.setTextAlign(Paint.Align.LEFT);

            x += lato / 2f + rTondo + m.s3;
            pNota.setColor(chiara ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO);
            pNota.setTextSize(m.corpo);
            c.drawText(rNomeLuce[i].adatta(pNota, l.nome,
                            b.right - m.s4 - largoStato - m.s3 - x,
                            m.corpo, m.corpo * 0.8f),
                    x, b.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
        }
    }

    /**
     * Il microfono: una pastiglia con l'icona vera e la parola accanto.
     *
     * Mentre ascolta l'anello attorno pulsa. Il battito e' calcolato dal tempo,
     * non da un animatore: costa un seno per fotogramma e si ferma da solo
     * quando l'ascolto finisce.
     */
    private void disegnaMicrofono(Canvas c) {
        float cx = micPill.centerX(), cy = micPill.centerY();

        // Mentre ascolta la barra respira. <b>Respira lei, non un alone
        // attorno</b>: un bagliore tondo in mezzo a una barra larga mezzo
        // schermo e' una macchia in mezzo a un rettangolo, e si vedeva. Il
        // battito e' calcolato dal tempo, non da un animatore: costa un seno
        // per fotogramma e si ferma da solo quando l'ascolto finisce.
        int velo = premutoMic ? 0xB0 : 0xFF;
        if (inAscolto) {
            float t = (Anima.ora() % 1600) / 1600f;
            float onda = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2f);
            velo = 0xB8 + (int) (0x47 * onda);
            postInvalidateOnAnimation();
        }
        // <b>Qui sta tutta l'audacia della schermata, e sta qui apposta.</b>
        // Era un contorno azzurrino su fondo azzurro - il velo piu' leggero di
        // tutta la Home - cioe' l'elemento meno visibile addosso al gesto per
        // cui questo apparecchio esiste: la parola di attivazione e' spenta, e
        // se non si preme qui Casa non ascolta mai. Adesso e' una barra piena
        // del blu della sezione, larga quanto il contenuto, ed e' la sola cosa
        // satura di una schermata che per il resto sta sui grigi.
        // Una capsula piena, come il tasto principale di una schermata iOS:
        // niente vetro sotto, il blu e' tutto suo.
        pFondo.setColor(Tinte.HOME);
        pFondo.setAlpha(velo);
        c.drawRoundRect(micPill, micPill.height() / 2f, micPill.height() / 2f, pFondo);

        int colore = Tinte.TESTO;
        String parola = inAscolto ? "ti ascolto" : "parla";
        pNota.setTextSize(m.voce);
        pNota.setTextAlign(Paint.Align.LEFT);
        float lato = m.icona * 1.15f;
        float largo = pNota.measureText(parola);

        // A riposo l'icona e la parola stanno al centro della pastiglia;
        // mentre ascolta si stringono nella meta' sinistra, per lasciare
        // all'annulla la destra.
        float cxTesto = inAscolto ? (micPill.left + annullaPill.left) / 2f : cx;
        float x = cxTesto - (lato + m.s3 + largo) / 2f;

        pMic.setColor(colore);
        Icone.disegna(c, Icone.MICROFONO, x + lato / 2f, cy, lato, pMic);
        pNota.setColor(colore);
        c.drawText(parola, x + lato + m.s3,
                cy - (pNota.descent() + pNota.ascent()) / 2f, pNota);
        pNota.setTextSize(m.nota);

        if (inAscolto) disegnaAnnulla(c);
    }

    /**
     * Il tasto per chiudere l'ascolto: tenue, perche' e' una via d'uscita e
     * non un'azione da invitare a fare.
     *
     * Non ha un bordo suo. Un tasto dentro un tasto, tutti e due con la loro
     * cornice, e' una cornice di troppo: qui bastano un fondo appena piu'
     * scuro e il margine attorno.
     */
    private void disegnaAnnulla(Canvas c) {
        float raggio = m.raggioPiccolo;
        pMic.setStyle(Paint.Style.FILL);
        pMic.setColor(premutoAnnulla ? 0x44000000 : 0x2A000000);
        c.drawRoundRect(annullaPill, raggio, raggio, pMic);

        pNota.setTextSize(m.nota);
        pNota.setTextAlign(Paint.Align.CENTER);
        pNota.setColor(premutoAnnulla ? Tinte.TESTO : Tinte.TESTO_MEDIO);
        c.drawText("annulla", annullaPill.centerX(),
                annullaPill.centerY() - (pNota.descent() + pNota.ascent()) / 2f, pNota);
        pNota.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                trova(x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                return true;

            case MotionEvent.ACTION_UP: {
                // Si agisce solo se il dito si alza dove si era posato: uno
                // strisciamento che parte da un tasto e finisce su un altro non
                // deve premere niente.
                int cmd = premutoComando, luce = premutaLuce;
                boolean mic = premutoMic, ann = premutoAnnulla;
                boolean meno = premutoMeno, piu = premutoPiu, met = premutoMeteo;
                boolean casa = premutaCasa;
                trova(x, y);
                boolean stesso = cmd == premutoComando && luce == premutaLuce
                                 && mic == premutoMic && ann == premutoAnnulla
                                 && meno == premutoMeno && piu == premutoPiu
                                 && met == premutoMeteo && casa == premutaCasa;
                premutoComando = premutaLuce = -1;
                premutoMic = premutoAnnulla = false;
                premutoMeno = premutoPiu = premutoMeteo = premutaCasa = false;
                if (stesso && ann && bottone != null) {
                    bottone.suAnnulla();
                    invalidate();
                    return true;
                }
                if (stesso) {
                    if (meno) sposta(-1);
                    else if (piu) sposta(1);
                    else if (met) apriSuperficie();
                    else if (cmd >= 0) suComando(cmd);
                    else if (luce >= 0 && apparecchioLuci != null
                             && luce < apparecchioLuci.elenco().size()) {
                        apparecchioLuci.inverti(apparecchioLuci.elenco().get(luce));
                    } else if (casa) {
                        if (sezioneLuci != null) sezioneLuci.apriLuci();
                    } else if (mic && bottone != null) {
                        bottone.suMicrofono();
                    }
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                premutoComando = premutaLuce = -1;
                premutoMic = premutoAnnulla = false;
                premutoMeno = premutoPiu = premutoMeteo = premutaCasa = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void trova(float x, float y) {
        premutoComando = premutaLuce = -1;
        premutoMic = premutoAnnulla = false;
        premutoMeno = premutoPiu = premutoMeteo = premutaCasa = false;

        if (inAscolto && annullaPill.contains(x, y)) { premutoAnnulla = true; return; }
        if (micPill.contains(x, y)) { premutoMic = true; return; }
        // I due tasti del volume hanno un bersaglio piu' largo del tondo che si
        // vede: sono i comandi piu' piccoli della schermata, e si premono di
        // sfuggita passando davanti al tablet.
        if (dentro(tastoMeno, x, y)) { premutoMeno = true; return; }
        if (dentro(tastoPiu, x, y))  { premutoPiu = true; return; }
        if (cardMeteo.contains(x, y)) { premutoMeteo = true; return; }
        if (chiComanda() != COMANDA_RADIO || sorgenteRadio != null) {
            for (int i = 0; i < comandi.length; i++) {
                if (comandi[i].contains(x, y)) { premutoComando = i; return; }
            }
        }
        for (int i = 0; i < MAX_LUCI; i++) {
            if (!areeLuci[i].isEmpty() && areeLuci[i].contains(x, y)) {
                premutaLuce = i; return;
            }
        }
        // Dove non c'e' una lampada, la scheda si apre: le routine, le altre
        // luci e la regolazione stanno tutte nella sezione Casa, e questa e' la
        // strada per arrivarci senza cercare la barra in fondo. Va per ultima,
        // dopo le lampade, o si prenderebbe anche i loro tocchi.
        if (cardCasa.contains(x, y)) { premutaCasa = true; return; }
    }

    /** Dentro, con un dito di tolleranza tutto attorno. */
    private boolean dentro(RectF b, float x, float y) {
        float aria = m.s2;
        return x >= b.left - aria && x <= b.right + aria
            && y >= b.top - aria && y <= b.bottom + aria;
    }

    private void suComando(int i) {
        // Gli stessi tre tasti, tre apparecchi diversi. Chi li prende lo decide
        // chiComanda(), e lo si vede dal colore prima di premere.
        int chi = chiComanda();
        if (chi == COMANDA_ULTIMO) { riprendi(i); return; }
        if (chi == COMANDA_MUSICA) {
            if (i == 0) musica.precedente();
            else if (i == 1) musica.pausaRiprendi();
            else musica.successivo();
            return;
        }
        if (chi == COMANDA_FUORI) {
            if (i == 0) riproduzione.precedente();
            else if (i == 1) riproduzione.pausaRiprendi();
            else riproduzione.successivo();
            return;
        }
        if (sorgenteRadio == null) return;
        if (i == 0) sorgenteRadio.precedente();
        else if (i == 1) {
            if (sorgenteRadio.staSuonando()) sorgenteRadio.spegni(); else sorgenteRadio.accendi(null);
        } else sorgenteRadio.successiva();
    }

    /**
     * Rimette l'ultima cosa ascoltata.
     *
     * <b>La radio riparte per chiave, non per posizione</b>: l'elenco lo
     * scrive il PC e fra ieri sera e stamattina puo' essere stato riordinato.
     * Se quella stazione non c'e' piu' si riparte dall'inizio dell'elenco, che
     * e' quello che faceva prima sempre.
     *
     * <b>La musica riparte dal suo contesto</b> - la playlist, l'album - e
     * salta al brano di allora. Col solo brano, finito quello finirebbe la
     * musica; col solo contesto ricomincerebbe dalla prima traccia. Quando il
     * contesto non si sa - un brano partito dal telefono via Spotify Connect,
     * dove Casa ha visto passare il titolo e non da dove venisse - si rimette
     * il brano da solo: una canzone e' meglio di niente.
     */
    private void riprendi(int i) {
        Ultimo u = ultimo();
        if (u.cosa == Ultimo.RADIO) {
            if (sorgenteRadio == null) return;
            if (i == 0) { sorgenteRadio.precedente(); return; }
            if (i == 2) { sorgenteRadio.successiva(); return; }
            sorgenteRadio.accendi(stazioneUltima());
            return;
        }
        if (u.cosa == Ultimo.MUSICA && musica != null && i == 1) {
            String da = u.contesto != null ? u.contesto : u.brano;
            if (da != null) musica.suona(da, u.brano);
        }
    }

    private static String mmss(long secondi) {
        long mi = secondi / 60, s = secondi % 60;
        if (mi >= 60) return String.format(Locale.ITALIAN, "%d:%02d:%02d", mi / 60, mi % 60, s);
        return String.format(Locale.ITALIAN, "%d:%02d", mi, s);
    }

    /** Entrando si guarda se il meteo e' ancora buono e se qualcuno ha
     *  cambiato il volume da fuori. Nessuna delle due cose costa qualcosa
     *  quando non serve: {@link Meteo#aggiorna()} torna subito se il dato e'
     *  fresco. */
    @Override
    public void suEntrata() {
        super.suEntrata();
        if (meteo != null) meteo.aggiorna();
        // Entrando si ricomincia dal meteo: chi torna alla Home - dal riposo,
        // da un'altra sezione - vuole prima di tutto sapere com'e' fuori.
        inNotizie = false;
        cambio = 0L;
        mostrata = Anima.ora();
        // Le lampade si rileggono entrando, e <b>una volta sola</b>: qui non si
        // chiama seguiDaVicino come fa la sezione. La Home e' la schermata che
        // sta in scena tutto il giorno, e tenere sveglie le lampade Tuya ogni
        // cinque secondi dalla mattina alla sera e' esattamente quello che la
        // nota in cima a Luci dice di non fare. Una lettura all'ingresso pero'
        // serve: le righe qui sono interruttori, e un interruttore che crede
        // spenta una lampada accesa la spegne invece di accenderla.
        if (apparecchioLuci != null) apparecchioLuci.aggiorna();
        leggiVolume();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tick);
        livelli.spegni();
    }

    /** Uscendo di scena si restituisce al sistema l'effetto audio: fuori dalla
     *  Home non c'e' nessuna barra da muovere, e un effetto agganciato al
     *  mescolatore di uscita che non serve a nessuno e' esattamente il genere
     *  di cosa che si paga a fine giornata. */
    @Override
    public void suUscita() {
        super.suUscita();
        livelli.spegni();
    }
}
