package dev.casa;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Le luci di casa: chi sono, dove stanno, come stanno.
 *
 * <b>Funziona a PC spento e senza internet.</b> Le lampade sono Tuya - quelle
 * dell'app Smart Life - e parlano in locale: e' quello che fa l'app quando il
 * telefono e' in casa. Casa fa lo stesso, direttamente, sulla rete di casa.
 * Niente cloud, niente ponte, niente account da riautenticare ogni tanto.
 *
 * <b>Lo stato si chiede solo quando qualcuno guarda.</b> Si rilegge entrando
 * nella sezione, premendo « aggiorna », dopo ogni comando - dove torna gratis
 * sulla connessione gia' aperta - e <b>a ciclo mentre la sezione Luci e' in
 * scena</b> ({@link #seguiDaVicino}). Fuori di li' non si interroga niente: un
 * apparecchio appeso al muro che ogni cinque secondi sveglia tre lampadine e'
 * un apparecchio che le tiene sveglie, e le lampade Tuya spengono la radio in
 * ricezione proprio per risparmiare corrente.
 *
 * Il ciclo mentre si guarda serve perche' <b>una lampada si accende anche in
 * altri modi</b> - l'app Smart Life, un altro telefono, l'interruttore a muro -
 * e Tuya non avvisa nessuno quando lo stato cambia. Senza, la tessera resta
 * spenta davanti a una lampada accesa.
 *
 * <h3>Quello che invece gira sempre, e perche' non e' la stessa cosa</h3>
 *
 * Non chiedere niente per ore ha un prezzo che non si vede subito: fra una
 * pressione e l'altra verso una lampada non passa <b>nessun</b> pacchetto, e
 * la voce nella tabella degli indirizzi hardware del tablet invecchia. Per
 * rifarla bisogna chiedere « chi ha questo indirizzo? » in broadcast, che e'
 * l'unica domanda che una lampada addormentata non sente: da qui il tablet che
 * la sera accende in due decimi e la mattina dopo ci mette mezzo minuto, o non
 * ci arriva - « come se si fossero staccate ».
 *
 * Percio' due cose girano comunque, e nessuna delle due interroga le lampade:
 *
 * <ul>
 *   <li>una <b>bussata ogni due minuti e mezzo</b> ({@link #RISCALDO_MS}): si
 *       apre il collegamento e lo si chiude subito, senza dire niente. Non e'
 *       un comando e non e' una lettura - risponde il pezzo di rete della
 *       lampada, non il suo programma - e tiene viva la voce in tabella;</li>
 *   <li>un orecchio su <b>quando il Wi-Fi va e torna</b>
 *       ({@link #ascoltaLaRete()}): a ogni riaggancio la tabella si svuota di
 *       colpo e il router puo' aver rimescolato gli indirizzi, e quello e' il
 *       momento giusto per rifare il giro invece di scoprirlo al primo tocco
 *       andato a vuoto.</li>
 * </ul>
 *
 * <b>Anche l'insistenza ha due misure.</b> Il mezzo minuto con cui si bussa a
 * una lampada che dorme lo paga volentieri chi ha appena premuto un pulsante;
 * non lo puo' pagare una lettura automatica, perche' finche' una lampada e'
 * occupata il tocco di chi passa davanti al tablet viene lasciato cadere. Le
 * letture che nessuno ha chiesto usano {@code Tuya.leggiSvelto}.
 */
public final class Luci {

    /**
     * <b>Il tag e' {@code Casa}, non {@code Casa.Luci}.</b> Questo ROM nasce
     * con {@code log.tag = E}: passano solo i tag aperti a mano uno per uno
     * con {@code persist.log.tag.<nome>}, e in lista non c'e' mai stato altro
     * che {@code Casa}. Tutto quello che questa classe ha scritto finora non
     * l'ha letto nessuno - proprio le righe che servono a capire perche' una
     * lampada non risponde. Il nome del pezzo sta all'inizio del messaggio:
     * per chi legge, {@code logcat -s Casa:I | grep luci} fa lo stesso lavoro,
     * e non dipende da una proprieta' che un ripristino porterebbe via.
     */
    private static final String TAG = "Casa";

    /**
     * Le lampade di un tablet appena installato, con le chiavi prese
     * dall'altro progetto ({@code Tablet SM-T210/config/luci.json}).
     *
     * <b>Sono il punto di partenza, non l'elenco.</b> L'elenco vero sta in
     * {@link Configurazione} e lo scrive il PC: queste righe valgono finche'
     * nessuno ha ancora configurato niente, cioe' sul tablet appena
     * installato e nel primo file che Casa mette in vetrina perche' Gestione
     * Home abbia da dove cominciare.
     *
     * <b>La chiave e' di fabbrica della lampada, non del tablet</b>: Tuya la
     * genera quando la lampada viene accoppiata con l'app Smart Life, e resta
     * quella per tutte e due i tablet, per il PC e per chiunque altro sia in
     * casa. Non c'era niente da « rifare » qui: era gia' stata tirata fuori
     * una volta, col codice utente dell'app e un QR da inquadrare, ed e' quella.
     * Se un giorno una lampada viene riaccoppiata da zero, la chiave cambia e
     * si rifa' quel giro - descritto in docs/luci.md.
     *
     * L'indirizzo qui e' solo il punto di partenza: se il router lo cambia, gli
     * annunci in broadcast lo riallineano da soli e {@code casa.json} se lo
     * ricorda.
     *
     * C'e' un <b>terzo apparecchio</b> in casa, a 192.168.1.2 (id
     * {@code id-lampada-2}, protocollo 3.4): risponde, ma la sua
     * chiave non e' mai stata raccolta e senza quella non parla con nessuno.
     * Non sta qui apposta - una tessera che non puo' funzionare e' peggio di
     * una tessera che non c'e'. Adesso pero' non serve piu' ricompilare per
     * aggiungerlo: si prende la chiave dalla pagina "Le luci" di Gestione Home,
     * dove il QR di Smart Life le tira fuori tutte insieme.
     */
    private static final String[][] DI_FABBRICA = {
        // nome, id, indirizzo di partenza, chiave locale, protocollo, tinta
    };

    /**
     * Le routine che si usano davvero. Stanno in cima alla sezione perche' sono
     * quello che si preme entrando in una stanza, senza fermarsi a leggere.
     *
     * <b>Si chiama « Cinema » e non « Film », e il motivo e' la voce.</b>
     * {@link Comandi} manda a Netflix qualunque frase che contenga "film", e le
     * routine si riconoscono per nome: chiamandola Film, « casa, metti un film »
     * avrebbe abbassato le luci invece di aprire Netflix - e chi l'ha detto
     * avrebbe dato la colpa a Netflix. Due comandi non possono contendersi la
     * stessa parola: quando succede, quello che cambia nome e' il piu' giovane.
     *
     * Lo stesso vale per « Tutte accese » invece di « Tutte »: « spegni tutte
     * le luci » contiene "tutte", e una routine di sole accensioni che si
     * prende quella frase e' un interruttore che fa il contrario.
     */
    private static final String[][] DI_FABBRICA_ROUTINE = {
        // nome, colore, e poi terne luce/azione/valore
        { "Buonanotte",   "#5A6C8C", "", "off", "" },
        { "Cinema",       "#FF7A45",
          "id-lampada-3", "off", "",
          "id-lampada-1", "colore", "#FF7A20",
          "id-lampada-1", "luce",   "20" },
        { "Tutte accese", "#F2D06B", "", "on", "" },
    };

    /**
     * Le lampade si annunciano in chiaro su queste due porte: la 6666 e' delle
     * vecchie, la 6667 delle 3.3 in poi. Serve solo a ritrovare l'indirizzo di
     * una lampada che il router ha spostato: l'identificativo non cambia mai.
     */
    private static final int[] PORTE_ANNUNCI = { 6666, 6667 };

    /** Quanto si sta in ascolto degli annunci. Le lampade parlano ogni ~5 s. */
    private static final int ASCOLTO_MS = 6000;

    public interface Ascolto {
        /** Thread UI: qualcosa e' cambiato, ridisegna. */
        void luciCambiate();
    }

    /** Chi sa portare alla sezione Casa. Ce l'ha la Home, che dalla sua scheda
     *  accende e spegne le lampade ma le routine le lascia dove sono: la
     *  scheda premuta apre la sezione, com'e' gia' per il meteo. */
    public interface Pagina { void apriLuci(); }

    private final Context contesto;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /**
     * Tre corsie: le lampade si interrogano in parallelo, cosi' una spenta al
     * muro - che ora, insistendo, costa fino a mezzo minuto di attesa - non
     * trattiene le altre. Tre perche' tre sono gli apparecchi in casa: con una
     * corsia a testa nessuna aspetta il turno di un'altra.
     */
    private final ExecutorService lavoro = Executors.newFixedThreadPool(3);

    private final List<Lampada> elenco = new ArrayList<Lampada>();
    private final List<Routine> routine = new ArrayList<Routine>();
    private Ascolto ascolto;
    private volatile boolean cercando;

    public Luci(Context contesto) {
        this.contesto = contesto;
        carica();
        ascoltaLaRete();
        ascoltaGliAnnunci();
        ui.postDelayed(riscaldo, GIRO_RISCALDO_MS);
    }

    public void setAscolto(Ascolto a) { ascolto = a; }

    public List<Lampada> elenco() { return elenco; }

    public List<Routine> routine() { return routine; }

    public boolean vuoto() { return elenco.isEmpty(); }

    // ---- elenco ------------------------------------------------------------

    /**
     * Legge le lampade e le routine da {@link Configurazione}, o dal codice se
     * non c'e' ancora una configurazione.
     *
     * <b>Il file sostituisce l'elenco, non lo integra.</b> Prima faceva il
     * contrario - si partiva dalle righe di fabbrica e il file ne aggiornava
     * solo gli indirizzi - e finche' l'elenco stava nel codice era la scelta
     * giusta: un file rotto non poteva lasciare Casa senza luci. Adesso pero'
     * l'elenco lo scrive il PC, e con la vecchia regola <b>una lampada tolta da
     * Gestione Home sarebbe ricomparsa a ogni avvio</b>, insieme a una che non
     * c'e' piu' in casa. Togliere deve poter togliere.
     *
     * La rete di sicurezza resta, e sta in {@link Archivio}: un file illeggibile
     * torna null, e da null si riparte da {@link #DI_FABBRICA}. I due casi
     * distinti sono "non c'e' nessuna configurazione" - le lampade di fabbrica -
     * e "c'e' e dice zero lampade", che vuol dire zero lampade.
     */
    private void carica() {
        JSONArray luciScritte = Configurazione.elenco(contesto, "luci");
        if (luciScritte == null) {
            for (String[] l : DI_FABBRICA) elenco.add(nuova(l));
        } else {
            for (int i = 0; i < luciScritte.length(); i++) {
                JSONObject l = luciScritte.optJSONObject(i);
                if (l == null) continue;
                Lampada quale = Lampada.da(l);
                // Una riga senza chiave o senza identificativo non e' una
                // lampada a meta': e' un pulsante che rispondera' sempre "non
                // risponde". Meglio non disegnarla.
                if (quale.completa()) elenco.add(quale);
                else Log.i(TAG, "luci: salto " + quale.nome + ", le manca chiave o indirizzo");
            }
        }

        JSONArray routineScritte = Configurazione.elenco(contesto, "routine");
        if (routineScritte == null) {
            for (String[] r : DI_FABBRICA_ROUTINE) routine.add(componi(r));
        } else {
            for (int i = 0; i < routineScritte.length(); i++) {
                JSONObject r = routineScritte.optJSONObject(i);
                if (r == null) continue;
                Routine quale = Routine.da(r);
                if (quale.nome.length() > 0) routine.add(quale);
            }
        }
        Log.i(TAG, "luci: " + elenco.size() + " lampade, " + routine.size() + " routine"
                + (luciScritte == null ? " (di fabbrica)" : " (dal PC)"));
    }

    /**
     * Rilegge tutto: la configurazione e' cambiata mentre Casa era in funzione.
     *
     * La chiama {@link MainActivity} quando il PC ha appena mandato una
     * configurazione nuova. Riparte dagli elenchi vuoti perche' e' l'unico modo
     * di far sparire una lampada tolta: aggiornare quelle che ci sono gia'
     * lascerebbe a schermo quelle che non ci sono piu'.
     */
    public void rileggi() {
        elenco.clear();
        routine.clear();
        carica();
        avvisa();
        aggiorna();
    }

    /**
     * Una lampada dalla riga di {@link #DI_FABBRICA}.
     *
     * Si costruisce qui e non nella costante, perche' una Lampada porta dentro
     * lo stato letto e l'indirizzo aggiornato: tenerne una copia sola in una
     * static vorrebbe dire che due Casa nello stesso processo - o due giri di
     * onCreate - si scambierebbero lo stato sotto i piedi.
     */
    private static Lampada nuova(String[] campi) {
        float v;
        try {
            v = Float.parseFloat(campi[4]);
        } catch (NumberFormatException storta) {
            v = 3.3f;
        }
        return new Lampada(campi[0], campi[1], campi[2], campi[3], v,
                Tinte.leggi(campi[5], Tinte.LUCI));
    }

    /** Una routine di fabbrica, scritta come sequenza piatta: nome, colore,
     *  poi terne. Sono tutte di lampade, quindi i passi sono tutti
     *  {@link Routine#LUCE}: le routine miste si scrivono dal PC, dove c'e' un
     *  elenco a tendina invece di un array di stringhe. */
    private static Routine componi(String[] campi) {
        List<Routine.Passo> passi = new ArrayList<Routine.Passo>();
        for (int i = 2; i + 2 < campi.length; i += 3) {
            passi.add(new Routine.Passo(Routine.LUCE, campi[i], campi[i + 1], campi[i + 2]));
        }
        return new Routine(campi[0], Tinte.leggi(campi[1], Tinte.LUCI), "", passi);
    }

    /**
     * Rimette per iscritto gli indirizzi di adesso.
     *
     * Si chiama solo quando uno e' cambiato davvero: una scrittura per un
     * riavvio del router, non una a ogni tocco.
     *
     * Scrive <b>solo</b> la voce "luci" e lascia stare le altre: le app e le
     * routine non sono affar suo, e riscrivere tutto vorrebbe dire che una
     * lampada spostata puo' portarsi via l'elenco delle app se qualcosa qui
     * dentro va storto. Da qui la configurazione esce anche in vetrina, cosi'
     * il PC riparte dagli indirizzi veri.
     */
    private void risalva() {
        try {
            JSONArray a = new JSONArray();
            for (Lampada l : elenco) a.put(l.json());
            Configurazione.metti(contesto, "luci", a);
        } catch (Exception e) {
            Log.w(TAG, "luci: non riesco a risalvare gli indirizzi", e);
        }
    }

    // ---- cosa sa Casa, per la Home ------------------------------------------

    /** Quante ne risultano accese. */
    public int accese() {
        int n = 0;
        for (Lampada l : elenco) if (l.accesa()) n++;
        return n;
    }

    /**
     * La riga « IN CASA » della Home, in italiano e al plurale giusto.
     *
     * Dice « nessuna accesa » anche quando non si e' ancora parlato con
     * nessuna, e non « non lo so »: la Home si guarda di sfuggita, e una riga
     * che ammette di non sapere non aiuta nessuno a decidere niente. Chi vuole
     * la verita' entra nella sezione, che e' anche il momento in cui si
     * rilegge.
     */
    public String riassunto() {
        int n = accese();
        if (n == 0) return "nessuna accesa";
        if (n == 1) {
            for (Lampada l : elenco) if (l.accesa()) return l.nome.toLowerCase(Locale.ITALIAN) + " accesa";
        }
        return n + " luci accese";
    }

    // ---- riconoscimento a voce ---------------------------------------------

    /** La lampada nominata nella frase, o null. */
    public Lampada riconosci(String frase) {
        for (Lampada l : elenco) {
            if (frase.contains(Testo.senzaAccenti(l.nome.toLowerCase(Locale.ITALIAN)))) return l;
        }
        return null;
    }

    /** La routine nominata nella frase, o null. */
    public Routine riconosciRoutine(String frase) {
        for (Routine r : routine) {
            if (frase.contains(Testo.senzaAccenti(r.nome.toLowerCase(Locale.ITALIAN)))) return r;
        }
        return null;
    }

    // ---- comandi ------------------------------------------------------------

    public void aggiorna() {
        for (Lampada l : elenco) accoda(l, Azione.LEGGI, 0, false);
    }

    /** Come {@link #aggiorna()}, ma senza insistere: la usa chi rilegge per
     *  conto suo, che non ha nessuno che aspetta e non deve tenere occupata
     *  una lampada mentre qualcuno prova a premerla. Anche chi torna a casa
     *  dopo ore: il suo primo tocco non deve trovare le lampade occupate. */
    public void sbircia() {
        for (Lampada l : elenco) accoda(l, Azione.LEGGI, 0, true);
    }

    // ---- rilettura mentre qualcuno guarda ----------------------------------

    /** Ogni quanto si rileggono le lampade mentre la sezione e' in scena. */
    private static final long OGNI_MS = 5000;

    private boolean daVicino;

    private final Runnable giro = new Runnable() {
        @Override public void run() {
            if (!daVicino) return;
            sbircia();
            ui.postDelayed(this, OGNI_MS);
        }
    };

    /**
     * Rilegge le lampade a ciclo, ma <b>solo mentre la sezione Luci e' sullo
     * schermo</b>.
     *
     * <h3>Perche' serve</h3>
     *
     * Una lampada la si accende anche in altri modi: dall'app Smart Life, da
     * un altro telefono, dall'interruttore a muro. Casa non ne sa niente -
     * Tuya non manda a nessuno un avviso quando lo stato cambia, e il
     * pacchetto che le lampade diffondono ogni pochi secondi porta l'indirizzo
     * e la versione, <b>non</b> se sono accese. L'unico modo di saperlo e'
     * chiedere.
     *
     * Prima si chiedeva solo entrando nella sezione: chi accendeva dal
     * telefono stando davanti al tablet vedeva la tessera spenta e la premeva,
     * spegnendo la lampada che aveva appena acceso.
     *
     * <h3>Perche' solo di fronte, e non sempre</h3>
     *
     * Resta valida la ragione per cui non c'era nessun ciclo (vedi la nota in
     * cima alla classe): le lampade Tuya spengono la radio in ricezione per
     * risparmiare, e svegliarle ogni cinque secondi tutto il giorno toglie
     * loro proprio la cosa che le fa durare. Ma « tutto il giorno » e « per i
     * venti secondi in cui qualcuno sta guardando le lampade » sono due cose
     * diverse, e il secondo e' esattamente il momento in cui lo stato deve
     * essere vero.
     *
     * Non si accavalla con se stesso: {@code accoda} lascia cadere una
     * richiesta su una lampada che sta gia' rispondendo, quindi una lampada
     * spenta al muro - che si fa aspettare mezzo minuto - non accumula una
     * coda di richieste dietro di se'.
     */
    public void seguiDaVicino(boolean si) {
        if (daVicino == si) return;
        daVicino = si;
        ui.removeCallbacks(giro);
        if (si) ui.postDelayed(giro, OGNI_MS);
    }

    public void inverti(Lampada l) { accoda(l, Azione.INVERTI, 0, false); }

    public void accendi(Lampada l, boolean on) {
        accoda(l, on ? Azione.ACCENDI : Azione.SPEGNI, 0, false);
    }

    public void luminosita(Lampada l, int percento) { accoda(l, Azione.LUMINOSITA, percento, false); }

    public void colore(Lampada l, int rgb) { accoda(l, Azione.COLORE, rgb, false); }

    public void bianco(Lampada l) { accoda(l, Azione.BIANCO, 0, false); }

    public void temperatura(Lampada l, int percento) { accoda(l, Azione.TEMPERATURA, percento, false); }

    public void tutte(boolean on) {
        for (Lampada l : elenco) accoda(l, on ? Azione.ACCENDI : Azione.SPEGNI, 0, false);
    }

    private enum Azione { LEGGI, RITROVA, ACCENDI, SPEGNI, INVERTI, LUMINOSITA, COLORE, BIANCO, TEMPERATURA }

    /**
     * Un comando su una lampada, fuori dal thread dell'interfaccia. Su Android
     * la rete sul thread principale e' vietata, e comunque mezzo minuto di
     * insistenza su una lampada spenta al muro congelerebbe tutto il resto -
     * compreso l'orologio, che e' quello che si guarda mentre si aspetta.
     */
    private void accoda(final Lampada l, final Azione azione, final int valore,
                        final boolean svelto) {
        if (l.inCorso) return;                 // doppio tocco: il primo basta
        l.inCorso = true;
        avvisa();
        lavoro.execute(new Runnable() {
            @Override public void run() {
                final Tuya.Stato s;
                Tuya t = l.canale();
                switch (azione) {
                    case ACCENDI:     s = t.accendi(true); break;
                    case SPEGNI:      s = t.accendi(false); break;
                    case INVERTI:     s = t.inverti(); break;
                    case LUMINOSITA:  s = t.luminosita(valore); break;
                    case COLORE:      s = t.colore(valore); break;
                    case BIANCO:      s = t.bianco(); break;
                    case TEMPERATURA: s = t.temperatura(valore); break;
                    case RITROVA:     s = t.leggiRitrovata(); break;
                    default:          s = svelto ? t.leggiSvelto() : t.leggi(); break;
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        l.inCorso = false;
                        l.stato = s;
                        if (s.raggiunta) l.rincorseAVuoto = 0;
                        avvisa();
                        // Una lampada che non risponde puo' essersi solo
                        // spostata: il router riassegna gli indirizzi, e
                        // l'ultimo che sappiamo puo' essere di ieri. Chiedere
                        // agli annunci costa dodici secondi su un thread suo e
                        // si fa una volta per volta ({@code cercando}), quindi
                        // due lampade mute non diventano due ricerche.
                        if (!s.raggiunta) scopri();
                    }
                });
            }
        });
    }

    /**
     * Chi sa fare le cose che non sono lampade.
     *
     * Lo mette {@link MainActivity}, che e' l'unico posto dove stanno insieme
     * la radio, la musica, il volume, le app e la voce. {@link Luci} non li
     * conosce e non deve conoscerli: sa che un passo di tipo "frase" si gira a
     * qualcuno, e quel qualcuno e' {@link Comandi}.
     */
    public interface Interprete {
        /** Esegue una frase come se qualcuno l'avesse detta. Sul thread UI. */
        void frase(String testo);
    }

    private Interprete interprete;

    public void setInterprete(Interprete i) { interprete = i; }

    /**
     * Esegue una routine: i passi in ordine, uno alla volta, su un thread solo.
     *
     * Non e' pigrizia: due comandi verso la stessa lampada si pesterebbero i
     * piedi - una lampada Tuya accetta una connessione per volta - e « spegni
     * tutto poi accendi il comodino » deve succedere in quest'ordine.
     *
     * <b>Anche i passi che non sono lampade rispettano la fila.</b> Una frase
     * si esegue sul thread dell'interfaccia, quindi va spedita li' e poi
     * aspettata: senza aspettarla, "metti la radio" e "volume al 30"
     * partirebbero insieme e il volume finirebbe sulla cosa sbagliata - che e'
     * esattamente il difetto che il passo "attesa" esiste per curare.
     */
    public void esegui(final Routine r) {
        if (r.inCorso) return;
        r.inCorso = true;
        avvisa();
        lavoro.execute(new Runnable() {
            @Override public void run() {
                for (Routine.Passo passo : r.passi) {
                    if (passo.e(Routine.FRASE)) { diLaFrase(passo.valore); continue; }
                    if (passo.e(Routine.ATTESA)) { aspetta(passo.valore); continue; }
                    for (Lampada l : bersagli(passo.bersaglio)) {
                        final Tuya.Stato s = applica(l, passo);
                        final Lampada quale = l;
                        ui.post(new Runnable() {
                            @Override public void run() {
                                quale.stato = s;
                                avvisa();
                            }
                        });
                    }
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        r.inCorso = false;
                        avvisa();
                    }
                });
            }
        });
    }

    /** Un passo "frase": lo esegue chi sa farlo, sul suo thread, e qui si
     *  aspetta che abbia finito. Il tempo massimo c'e' perche' una routine non
     *  puo' restare appesa per sempre a un comando che non torna: dopo cinque
     *  secondi si tira avanti col passo dopo. */
    private void diLaFrase(final String testo) {
        if (interprete == null || testo == null || testo.trim().length() == 0) return;
        final CountDownLatch fatta = new CountDownLatch(1);
        ui.post(new Runnable() {
            @Override public void run() {
                try {
                    interprete.frase(testo);
                } catch (Exception e) {
                    Log.w(TAG, "luci: routine, la frase \"" + testo + "\" e' andata storta", e);
                } finally {
                    fatta.countDown();
                }
            }
        });
        try {
            fatta.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException fermato) {
            Thread.currentThread().interrupt();
        }
    }

    /** Un passo "attesa": tot secondi, al massimo un minuto. Il tetto non e'
     *  diffidenza verso chi scrive le routine, e' che questo thread e' lo
     *  stesso che serve le lampade: un'attesa lunga le terrebbe ferme tutte. */
    private static void aspetta(String secondi) {
        int s = numero(secondi, 1);
        if (s <= 0) return;
        try {
            Thread.sleep(Math.min(s, 60) * 1000L);
        } catch (InterruptedException fermato) {
            Thread.currentThread().interrupt();
        }
    }

    /** Le lampade toccate da un passo: una sola, oppure tutte se non e' detto. */
    private List<Lampada> bersagli(String id) {
        if (id == null || id.length() == 0) return new ArrayList<Lampada>(elenco);
        List<Lampada> una = new ArrayList<Lampada>();
        for (Lampada l : elenco) if (l.id.equals(id)) una.add(l);
        return una;
    }

    private static Tuya.Stato applica(Lampada l, Routine.Passo passo) {
        Tuya t = l.canale();
        String a = passo.azione;
        if ("on".equals(a)) return t.accendi(true);
        if ("off".equals(a)) return t.accendi(false);
        if ("inverti".equals(a)) return t.inverti();
        if ("bianco".equals(a)) return t.bianco();
        if ("luce".equals(a)) return t.luminosita(numero(passo.valore, 100));
        if ("bianchezza".equals(a)) return t.temperatura(numero(passo.valore, 50));
        if ("colore".equals(a)) return t.colore(Tinte.leggi(passo.valore, 0xFFFFFFFF) & 0xFFFFFF);
        return t.leggi();
    }

    private static int numero(String testo, int riserva) {
        try {
            return Integer.parseInt(testo.trim());
        } catch (Exception storto) {
            return riserva;
        }
    }

    private void avvisa() {
        if (ascolto != null) ascolto.luciCambiate();
    }

    // ---- la strada che si raffredda ----------------------------------------

    /**
     * Ogni quanto si bussa a una lampada senza chiederle niente.
     *
     * Due minuti e mezzo non e' un ciclo di lettura travestito: non chiede lo
     * stato, non aggiorna niente sullo schermo, e alla lampada non arriva
     * nessun comando - risponde il suo pezzo di rete, come farebbe a un
     * qualsiasi pacchetto. Serve solo a non far scadere la voce nella tabella
     * degli indirizzi hardware del tablet, che e' quello che si perde stando
     * fermi e che costa mezzo minuto rifare.
     */
    private static final long RISCALDO_MS = 150000;

    /**
     * Il passo del giro: ogni trenta secondi si bussa alle sole lampade
     * <b>mute</b>, e ogni cinque giri - i due minuti e mezzo di sopra - a tutte.
     *
     * Bussare a una lampada spenta al muro non sveglia niente e costa un
     * pacchetto che torna indietro subito, « host irraggiungibile ». Ma e' la
     * rete di sicurezza dell'orecchio sugli annunci ({@link #ascoltaGliAnnunci}):
     * se il Wi-Fi del tablet un giorno smettesse di consegnare i broadcast, una
     * lampada riaccesa si ritroverebbe comunque entro mezzo minuto, invece che
     * al primo ingresso nella sezione.
     */
    private static final long GIRO_RISCALDO_MS = 30000;
    private static final int GIRI_PER_RISCALDO = (int) (RISCALDO_MS / GIRO_RISCALDO_MS);
    private int giroRiscaldo;

    private final Runnable riscaldo = new Runnable() {
        @Override public void run() {
            ui.postDelayed(this, GIRO_RISCALDO_MS);
            // Mentre la sezione e' aperta ci pensa gia' il ciclo, e in piu' e'
            // il momento in cui qualcuno preme: due connessioni insieme verso
            // una lampada che ne accetta una sola sono un tocco perso.
            if (daVicino) return;
            giroRiscaldo = (giroRiscaldo + 1) % GIRI_PER_RISCALDO;
            bussa(giroRiscaldo == 0);
        }
    };

    /**
     * Bussa a ogni lampada, una volta, senza dire niente e senza far cambiare
     * niente sullo schermo.
     *
     * <b>E' la cura per « dopo un po' si staccano ».</b> Casa apre una
     * connessione per comando e la chiude: giusto, ma vuol dire che fra una
     * pressione e l'altra verso una lampada non passa <b>niente</b>. La voce
     * nella tabella degli indirizzi hardware del tablet invecchia, e per
     * rifarla bisogna chiedere « chi ha questo indirizzo? » in broadcast - che
     * e' proprio quello che una lampada addormentata non sente. Da fuori si
     * vede un tablet che la sera comanda le luci in due decimi e la mattina
     * dopo ci mette mezzo minuto, o non ci arriva.
     *
     * Se una lampada non apre, l'indirizzo puo' non essere piu' il suo: si
     * ascoltano gli annunci ({@link #scopri()}), che e' l'unico modo di
     * accorgersi di un router che ha rimescolato le carte mentre nessuno
     * guardava.
     */
    private void bussa(boolean tutte) {
        for (final Lampada l : elenco) {
            if (l.inCorso) continue;           // c'e' gia' chi le sta parlando
            final boolean muta = l.stato != null && !l.stato.raggiunta;
            if (!tutte && !muta) continue;
            lavoro.execute(new Runnable() {
                @Override public void run() {
                    if (l.canale().bussa()) {
                        // <b>La porta si e' riaperta.</b> Prima qui si tornava
                        // e basta: la strada era calda, ma la tessera restava
                        // su « non risponde » finche' qualcuno non entrava
                        // nella sezione. Una lampada riaccesa al muro stava
                        // accesa in camera e spenta sul tablet.
                        if (muta) {
                            ui.post(new Runnable() {
                                @Override public void run() {
                                    Log.i(TAG, "luci: " + l.nome + " riapre la porta: la si rilegge");
                                    accoda(l, Azione.RITROVA, 0, false);
                                }
                            });
                        }
                        return;
                    }
                    if (muta) return;                  // lo sapevamo gia'
                    Log.i(TAG, "luci: " + l.nome + " non apre piu' su " + l.ip + ": si ascolta chi si e' spostato");
                    ui.post(new Runnable() {
                        @Override public void run() { scopri(); }
                    });
                }
            });
        }
    }

    // ---- l'orecchio sempre aperto ------------------------------------------

    /**
     * Quanto silenzio fa pensare che una lampada sia stata spenta e riaccesa.
     *
     * Le lampade si annunciano ogni cinque secondi circa: otto annunci persi di
     * fila non sono un pacchetto caduto, sono una lampada che non c'era. E
     * una lampada che torna dopo essere stata spenta al muro <b>si riaccende
     * per conto suo</b> - e' fatta cosi', perche' l'interruttore a muro deve
     * funzionare anche senza app - quindi lo stato che Casa ricorda, «
     * spenta », e' diventato falso senza che nessuno le abbia detto niente.
     */
    private static final long SILENZIO_MS = 40000;

    /** La prima rincorsa dopo un annuncio, e il tetto a cui arriva
     *  raddoppiando quando va a vuoto. */
    private static final long RINCORSA_MS = 15000, RINCORSA_MAX_MS = 5 * 60000L;

    private volatile boolean inAscolto;
    private Selector selettore;

    /**
     * Ascolta gli annunci delle lampade <b>sempre</b>, non solo per i dodici
     * secondi di {@link #scopri()}.
     *
     * <h3>Perche'</h3>
     *
     * La scena che si voleva curare: si torna a casa, si riattacca la corrente,
     * le lampade si agganciano al Wi-Fi in una manciata di secondi - e Casa se
     * ne accorgeva quando ne aveva voglia. Nessuno le cercava: la bussata
     * apriva la porta e non guardava lo stato, la rilettura partiva solo
     * entrando nella sezione. Il comodino, che sta dove il segnale arriva
     * peggio, poteva restare « non risponde » per un'ora.
     *
     * Eppure la lampada lo dice da sola: appena agganciata comincia ad
     * annunciarsi in broadcast ogni cinque secondi, con identificativo e
     * indirizzo. Ascoltare costa un thread fermo in {@code select()} - zero
     * risvegli quando nessuno parla, e uno ogni cinque secondi per lampada
     * quando parlano - contro una lettura TCP che sveglia la lampada.
     *
     * <h3>Cosa si fa con un annuncio</h3>
     *
     * {@link #sentita}: se la lampada era muta, se ha cambiato indirizzo, o se
     * e' stata zitta abbastanza da essere stata spenta e riaccesa, la si
     * rilegge subito. Altrimenti niente: l'annuncio di una lampada che sta gia'
     * bene non chiede nulla, e <b>non diventa mai una lettura a ciclo</b>.
     *
     * Un {@link Selector} e due canali invece di due thread fermi in
     * {@code receive()}: e' un thread solo, e chiuderlo e' una {@code wakeup}.
     */
    private void ascoltaGliAnnunci() {
        final Selector sel;
        final List<DatagramChannel> canali = new ArrayList<DatagramChannel>(PORTE_ANNUNCI.length);
        try {
            sel = Selector.open();
        } catch (Exception e) {
            Log.w(TAG, "luci: niente orecchio sugli annunci: " + e.getMessage());
            return;
        }
        for (int porta : PORTE_ANNUNCI) {
            DatagramChannel ch = null;
            try {
                ch = DatagramChannel.open();
                // Riusabile: scopri() e Gestione Home sul PC ascoltano le
                // stesse porte, e un broadcast arriva a tutti quelli che le
                // hanno aperte cosi'.
                ch.socket().setReuseAddress(true);
                ch.socket().bind(new InetSocketAddress(porta));
                ch.configureBlocking(false);
                ch.register(sel, SelectionKey.OP_READ);
                canali.add(ch);
            } catch (Exception e) {
                Log.w(TAG, "luci: non ascolto la " + porta + ": " + e.getMessage());
                if (ch != null) try { ch.close(); } catch (Exception ignorato) { }
            }
        }
        if (canali.isEmpty()) {
            try { sel.close(); } catch (Exception ignorato) { }
            return;
        }
        selettore = sel;
        inAscolto = true;
        Thread orecchio = new Thread(new Runnable() {
            @Override public void run() {
                // Un buffer solo per tutta la vita del thread: gli annunci
                // arrivano ogni pochi secondi per sempre, e un array nuovo a
                // pacchetto e' spazzatura che non serve.
                ByteBuffer buf = ByteBuffer.allocate(2048);
                try {
                    while (inAscolto) {
                        if (sel.select() == 0) continue;
                        Iterator<SelectionKey> chi = sel.selectedKeys().iterator();
                        while (chi.hasNext()) {
                            SelectionKey k = chi.next();
                            chi.remove();
                            buf.clear();
                            if (((DatagramChannel) k.channel()).receive(buf) == null) continue;
                            senti(buf.array(), buf.position());
                        }
                    }
                } catch (Exception e) {
                    if (inAscolto) Log.w(TAG, "luci: l'orecchio sugli annunci si e' chiuso: " + e.getMessage());
                } finally {
                    inAscolto = false;
                    for (DatagramChannel ch : canali) try { ch.close(); } catch (Exception ignorato) { }
                    try { sel.close(); } catch (Exception ignorato) { }
                }
            }
        }, "Casa-luci-annunci");
        orecchio.setDaemon(true);
        orecchio.start();
    }

    /** Sul thread dell'orecchio: si capisce chi e', e il resto si fa sull'UI,
     *  che e' dove vivono le lampade. */
    private void senti(byte[] dati, int quanti) {
        try {
            String testo = Tuya.annuncioInChiaro(dati, quanti);
            if (testo == null) return;
            JSONObject o = new JSONObject(testo);
            final String id = o.optString("gwId", o.optString("devId", ""));
            final String ip = o.optString("ip", "");
            if (id.length() == 0) return;
            ui.post(new Runnable() {
                @Override public void run() { sentita(id, ip); }
            });
        } catch (Exception rumore) {
            // Sulle stesse porte parlano anche altre marche: non e' un errore.
        }
    }

    /** Thread UI: una lampada si e' appena fatta sentire. */
    private void sentita(String id, String ip) {
        for (Lampada l : elenco) {
            if (!l.id.equals(id)) continue;
            long adesso = SystemClock.uptimeMillis();
            long prima = l.sentita;
            l.sentita = adesso;

            boolean spostata = ip.length() > 0 && !ip.equals(l.ip);
            if (spostata) {
                Log.i(TAG, "luci: " + l.nome + " si e' spostata: " + l.ip + " -> " + ip);
                l.ip = ip;
                risalva();
            }
            boolean muta = l.stato == null || !l.stato.raggiunta;
            boolean tornata = prima > 0 && adesso - prima > SILENZIO_MS;
            if (!spostata && !muta && !tornata) return;
            if (l.inCorso) return;

            // Una lampada che si annuncia ma non si lascia leggere - una chiave
            // cambiata, un protocollo nuovo - non va inseguita ogni quindici
            // secondi per sempre: ogni rincorsa la tiene occupata, e un tocco
            // su una lampada occupata cade. Si raddoppia l'attesa a ogni giro
            // a vuoto, fino a cinque minuti; una lettura buona azzera il conto.
            long attesa = Math.min(RINCORSA_MAX_MS, RINCORSA_MS << Math.min(l.rincorseAVuoto, 5));
            if (!spostata && !tornata && l.rincorsa > 0 && adesso - l.rincorsa < attesa) return;
            l.rincorsa = adesso;
            l.rincorseAVuoto++;
            Log.i(TAG, "luci: " + l.nome + " si e' fatta sentire ("
                    + (spostata ? "nuovo indirizzo" : tornata ? "dopo " + (adesso - prima) / 1000 + " s di silenzio" : "era muta")
                    + "): la si rilegge");
            accoda(l, Azione.RITROVA, 0, false);
            return;
        }
    }

    private void smettiDiAscoltareGliAnnunci() {
        inAscolto = false;
        Selector sel = selettore;
        selettore = null;
        if (sel != null) sel.wakeup();
    }

    /**
     * Quando il Wi-Fi va e torna, si ricomincia da capo.
     *
     * Un tablet appeso al muro il Wi-Fi lo riaggancia da solo piu' volte al
     * giorno - il router si riavvia, il canale cambia, l'access point
     * riparte - e a ogni riaggancio il sistema <b>svuota la tabella degli
     * indirizzi hardware</b>: da quel momento ogni lampada e' di nuovo fredda,
     * e il router puo' anche averle spostate. E' esattamente il momento in cui
     * conviene rifare il giro, ed e' l'unico che si puo' sapere invece di
     * scoprirlo al primo tocco andato a vuoto.
     *
     * Non serve nessun permesso in piu': {@code ACCESS_NETWORK_STATE} c'e'
     * gia' per la musica.
     */
    private ConnectivityManager.NetworkCallback rete;

    private void ascoltaLaRete() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    contesto.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            rete = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network quale) {
                    // Arriva su un thread suo: da qui in poi si sta sull'UI,
                    // che e' dove vivono l'elenco e le tessere.
                    ui.post(new Runnable() {
                        @Override public void run() {
                            Log.i(TAG, "luci: la rete e' tornata: si rilegge e si riascolta");
                            aggiorna();
                            scopri();
                        }
                    });
                }
            };
            cm.registerDefaultNetworkCallback(rete);
        } catch (Exception e) {
            // Senza, si torna a scoprirlo al primo tocco: peggio, non rotto.
            Log.w(TAG, "luci: non riesco a farmi avvisare quando la rete torna: " + e.getMessage());
        }
    }

    private void smettiDiAscoltareLaRete() {
        if (rete == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    contesto.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(rete);
        } catch (Exception ignorato) {
        }
        rete = null;
    }

    // ---- riscoperta degli indirizzi -----------------------------------------

    /**
     * Ascolta gli annunci che le lampade mandano in broadcast e riallinea gli
     * indirizzi salvati.
     *
     * Serve perche' l'indirizzo e' l'unica cosa che invecchia: basta un riavvio
     * del router e la lampada risponde altrove. L'identificativo invece resta
     * quello per sempre, quindi si riconosce da quello e si aggiorna l'IP.
     */
    public void scopri() {
        // Con l'orecchio sempre aperto gli indirizzi si riallineano da soli,
        // un annuncio alla volta: una seconda coppia di socket sulle stesse
        // porte per dodici secondi non sentirebbe niente di piu'.
        if (inAscolto) return;
        if (cercando || elenco.isEmpty()) return;
        cercando = true;
        // Un thread suo e non una corsia del pool: sta dodici secondi fermo ad
        // ascoltare, e se prendesse un posto in coda terrebbe fuori una lampada
        // proprio nel momento in cui la si sta aspettando.
        Thread cerca = new Thread(new Runnable() {
            @Override public void run() {
                boolean cambiato = false;
                for (int porta : PORTE_ANNUNCI) cambiato |= ascolta(porta);
                final boolean rinfrescare = cambiato;
                ui.post(new Runnable() {
                    @Override public void run() {
                        cercando = false;
                        if (rinfrescare) {
                            risalva();
                            avvisa();
                            aggiorna();
                        }
                    }
                });
            }
        }, "Casa-luci-scopri");
        cerca.setDaemon(true);
        cerca.start();
    }

    private boolean ascolta(int porta) {
        DatagramSocket socket = null;
        boolean cambiato = false;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.setSoTimeout(1000);
            socket.bind(new java.net.InetSocketAddress(porta));

            long fine = System.currentTimeMillis() + ASCOLTO_MS;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < fine) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(p);
                } catch (Exception scaduto) {
                    continue;                  // nessuno ha parlato: si riprova
                }
                cambiato |= annuncio(buf, p.getLength());
            }
        } catch (Exception e) {
            Log.w(TAG, "luci: ascolto sulla " + porta + " fallito: " + e.getMessage());
        } finally {
            if (socket != null) socket.close();
        }
        return cambiato;
    }

    /** Un annuncio: se e' di una lampada che conosciamo, ne prende l'indirizzo. */
    private boolean annuncio(byte[] buf, int quanti) {
        try {
            String testo = Tuya.annuncioInChiaro(buf, quanti);
            if (testo == null) return false;
            JSONObject o = new JSONObject(testo);
            String id = o.optString("gwId", o.optString("devId", ""));
            String ip = o.optString("ip", "");
            if (id.length() == 0 || ip.length() == 0) return false;
            for (Lampada l : elenco) {
                if (l.id.equals(id) && !ip.equals(l.ip)) {
                    Log.i(TAG, "luci: " + l.nome + " si e' spostata: " + l.ip + " -> " + ip);
                    l.ip = ip;
                    return true;
                }
            }
        } catch (Exception rumore) {
            // Sulla stessa porta parlano anche telefoni e altre marche: un
            // pacchetto che non si capisce non e' un errore, e' rumore.
        }
        return false;
    }

    public void chiudi() {
        seguiDaVicino(false);
        ui.removeCallbacks(riscaldo);
        smettiDiAscoltareLaRete();
        smettiDiAscoltareGliAnnunci();
        lavoro.shutdownNow();
    }
}
