package dev.casa;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le app che si aprono da qui.
 *
 * <b>Non e' il cassetto delle applicazioni del sistema.</b> Questo non e' un
 * telefono: e' un apparecchio che fa un elenco chiuso di cose, e chi ci passa
 * davanti deve vedere quelle. L'elenco lo decide chi allestisce il tablet -
 * dalla pagina "Le app" di Gestione Home, sul PC - non il sistema elencando
 * tutto quello che dichiara di essere lanciabile.
 *
 * <b>L'elenco arriva da {@link Configurazione}</b>, e si rilegge a ogni
 * entrata come si rileggono le icone: togliere il Meteo dal PC e trovarlo via
 * senza riavviare Casa e' il minimo che ci si aspetta da un elenco che si
 * modifica altrove. Le cinque voci qui sotto valgono soltanto finche' nessuno
 * ha configurato niente.
 *
 * <b>Si vedono soltanto le app installate.</b> Prima quelle mancanti restavano
 * a schermo spente, con scritto "non installata", e il ragionamento era che chi
 * ha messo Spotify nell'elenco deve capire che manca l'APK invece di credere
 * che la sezione sia rotta. Il ragionamento sbagliava destinatario: <b>chi ha
 * scritto l'elenco non e' chi sta davanti al tablet</b>. Per chi ci passa
 * davanti una tessera spenta e' un pulsante che non fa niente - la preme, non
 * succede nulla, e la prossima volta non si fida piu' nemmeno delle altre.
 *
 * Il diagnostico serviva quando le app si mettevano una per una da adb e
 * un'assenza voleva dire un errore di allestimento. Adesso il Play Store e'
 * tornato: un'app che manca si reinstalla, non si diagnostica da una tessera
 * grigia. E appena e' installata ricompare da sola, perche' l'elenco si rilegge
 * a ogni entrata.
 *
 * Le tessere <b>non cambiano misura</b> quando l'elenco si accorcia: la
 * larghezza resta quella di cinque colonne e le righe si centrano. Se
 * crescessero, disinstallare un'app sposterebbe tutte le altre sotto il dito.
 *
 * Le icone sono quelle vere delle app, prese da PackageManager, ricaricate a
 * ogni entrata - cosi' Spotify compare da solo appena installato - e
 * <b>rilasciate all'uscita</b>: tenerle costa memoria per tutto il tempo in cui
 * nessuno le guarda.
 */
public class SezioneApp extends Sezione {

    /** Una colonna per voce: una riga sola, senza una seconda riga con una
     *  tessera spaiata in fondo a sinistra. Se l'elenco crescesse, la griglia
     *  va a capo da se' e il passo verticale resta limitato: vedi
     *  {@link #onSizeChanged}. */
    private static final int COLONNE = 5;
    // Cinque e non quattro: le voci sono cinque e ci stanno tutte su una riga.
    // Con quattro colonne la quinta finiva da sola sulla seconda riga, e una
    // tessera sola in mezzo al vuoto si legge come un errore di impaginazione.

    /**
     * L'elenco di partenza. Il nome scritto qui e' quello che si vuole leggere
     * a schermo, non sempre quello che l'app dichiara: "Impostazioni" e'
     * meglio di "Settings".
     *
     * <b>Cinque voci</b>, di cui una non e' un'app: il Meteo, che e' una
     * schermata di Casa (vedi {@link VelaMeteo}). Google e Radio FM sono
     * usciti: cercare su Google non
     * e' una cosa che si fa da un apparecchio appeso in cucina - si prende il
     * telefono - e la Radio FM di sistema fa la stessa cosa della sezione
     * Radio, peggio e senza i loghi. Su una schermata che si guarda di sfuggita
     * ogni tessera in piu' e' una da scartare con l'occhio.
     *
     * Il <b>Play Store e' installato ma non ha una tessera</b>, e il perche' sta
     * nel commento qui sotto.
     */
    /**
     * Il meteo non e' un'app installata: e' una pagina di Casa.
     *
     * Sta nell'elenco lo stesso, e prima delle altre, perche' da qui si guarda
     * come si guarda qualunque altra cosa - si tocca una tessera e si apre una
     * schermata. Chi ci passa davanti non deve sapere che una di queste cinque
     * abita dentro Casa e le altre no.
     */
    static final String METEO = "@meteo";

    /**
     * Riavvia Casa. Non e' un'app, come il meteo, ma qui ci sta per la stessa
     * ragione: da questa sezione si tocca una tessera e succede qualcosa.
     *
     * <b>Perche' esiste.</b> Quando qualcosa va storto - una schermata che non
     * si aggiorna, la memoria che si e' mangiata tutto - la cura e' riavviare,
     * e fino a ieri l'unico modo era il cavo e {@code adb shell am force-stop}.
     * Su un apparecchio appeso al muro, in cucina, quello vuol dire staccare la
     * corrente: che e' proprio il gesto che a Casa fa perdere le cose scritte a
     * meta'.
     *
     * <b>Vuole due tocchi.</b> Il primo scrive « premi ancora » sulla tessera,
     * il secondo riavvia; passati tre secondi si dimentica. E' l'unica tessera
     * che fa sparire lo schermo per due secondi, e una mano di passaggio non
     * deve poterlo fare per curiosita'.
     */
    /** Il calendario e la lista: due pagine di Casa, non due app installate.
     *  Stanno qui per la stessa ragione del meteo - da questa sezione si tocca
     *  una tessera e si apre una schermata - e sono due e non una perche' sono
     *  due cose diverse: il perche' sta su {@link VelaToDo}. */
    static final String CALENDARIO = "@calendario";
    static final String TODO = "@todo";

    /** Le notizie: una pagina di Casa come il calendario, e per la stessa
     *  ragione fuori dalla configurazione - vedi {@link VelaNotizie}. */
    static final String NOTIZIE = "@notizie";

    /** Le impostazioni di Casa: la modalita' notte e il riposo. Non sono le
     *  Impostazioni di Android, che hanno la loro tessera, e non sono il
     *  pannello della parola, che si apre solo dal PC - vedi
     *  {@link VelaPreferenze}. */
    static final String PREFERENZE = "@preferenze";

    static final String RIAVVIA = "@riavvia";

    /** Quanto dura la conferma del riavvio. */
    private static final long CONFERMA = 3000;

    /** Quando e' stato chiesto il riavvio la prima volta, 0 se nessuno lo ha
     *  chiesto. */
    private long chiestoRiavvio;

    static final String[][] DI_FABBRICA = {
        { METEO,                     "Meteo"        },
        { "com.android.chrome",      "Chrome"       },
        { "com.android.settings",    "Impostazioni" },
    };

    // SPOTIFY E NETFLIX NON SONO PIU' QUI, E SONO USCITI PER DUE MOTIVI DIVERSI.
    //
    // Spotify perche' non e' piu' un'app: e' dentro Casa. Il demone
    // go-librespot suona da solo, occupa 12 MB invece di 290, e ha una sezione
    // sua nella barra. Una tessera che apre l'app ufficiale sarebbe una seconda
    // strada per la stessa cosa - e la strada peggiore, visto quanto pesa.
    //
    // Netflix perche' non e' installato. Se un giorno torna, la tessera si
    // rimette dalla pagina "Le app" di Gestione Home con due tasti: non c'e'
    // piu' bisogno di ricompilare per aggiungere una voce.
    //
    // Il comando a voce "apri netflix" resta in Comandi: se l'app c'e', si apre
    // lo stesso. Un comando che parla di un'app che manca lo dice, e non fa
    // danno.

    // IL PLAY STORE NON E' IN ELENCO, ED E' INSTALLATO. Non e' una svista.
    //
    // Installato perche' senza, le app invecchiano e non le aggiorna piu'
    // nessuno: e' successo a Netflix, tornato all'APK del 2019 e rifiutato dai
    // suoi stessi server. Non in elenco perche' un negozio su una schermata di
    // casa non e' una cosa che si apre di sfuggita - si apre quando c'e' da
    // aggiornare qualcosa, cioe' due volte l'anno, e allora lo si apre da adb:
    //
    //     adb shell monkey -p com.android.vending -c android.intent.category.LAUNCHER 1
    //
    // Su una schermata che si guarda di sfuggita ogni tessera in piu' e' una
    // da scartare con l'occhio, e questa e' l'unica che non serve mai a chi sta
    // in cucina.

    /** Una voce dell'elenco, con quello che il sistema ne sa adesso. */
    private static final class Voce {
        final String pacchetto, nome;
        /** Il nome misurato per la tessera. Sta qui e non in un array a parte
         *  perche' e' roba della voce quanto il suo nome. */
        final Testo.Riga riga = new Testo.Riga();
        Drawable icona;
        Intent   apertura;
        boolean  installata;
        Voce(String pacchetto, String nome) { this.pacchetto = pacchetto; this.nome = nome; }
    }

    private final List<Voce> voci = new ArrayList<Voce>();

    /**
     * Quelle che si disegnano: le installate, e basta.
     *
     * Una lista a parte invece di un filtro dentro onDraw, perche' l'indice di
     * una tessera deve voler dire la stessa cosa per il disegno e per il tocco.
     * Con il filtro nel disegno, la terza tessera a schermo e la terza voce
     * dell'elenco sarebbero due app diverse appena una manca - e si aprirebbe
     * quella sbagliata.
     */
    private final List<Voce> visibili = new ArrayList<Voce>();

    /** Un rettangolo per tessera visibile. Cresce con l'elenco invece di
     *  essere lungo quanto le voci di fabbrica: adesso le voci le decide il PC
     *  e possono essere piu' di cinque. */
    private RectF[] tessere = new RectF[0];

    /** Il pannello che tiene la griglia: con due app installate il bordo e'
     *  la sola cosa che distingue « c'e' posto per altre » da « manca un
     *  pezzo di schermata ». */
    private final RectF pannello = new RectF();
    private final Rect    riquadro = new Rect();

    private final Paint pTitolo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNome   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota   = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** I fondi piatti: la tessera, e il quadrato dell'icona alla iOS. */
    private final Paint pFondo  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF icona   = new RectF();

    private int premuta = -1;
    private float latoIcona, cyIcona, yNome;

    /** Il cielo animato dentro la tessera del meteo, e chi ne sa aprire la
     *  pagina. La tessera mostra il tempo che fa <b>adesso</b>: e' l'unica di
     *  questa schermata che dice qualcosa prima di essere premuta. */
    private final Cielo cielo = new Cielo();
    private final RectF quadretto = new RectF();
    private final android.graphics.Rect riquadroMeteo = new android.graphics.Rect();
    private Meteo meteo;
    private Meteo.Pagina pagina;

    /** Chi sa aprire le due pagine di Casa che stanno qui dentro. */
    public interface Pagine {
        void apriCalendario();
        void apriToDo();
        void apriNotizie();
        void apriPreferenze();
    }

    private Pagine pagine;
    private Appunti appunti;

    public void setPagine(Pagine p) { pagine = p; }

    /** Le note servono solo a scrivere il numero sulla tessera. */
    public void setAppunti(Appunti a) { appunti = a; }

    /**
     * Lo spazio che il nome ha nella tessera, e i corpi nominali.
     *
     * Qui il nome non veniva accorciato affatto: "Impostazioni" ci sta per
     * poco, e l'elenco - dice il commento in cima - un giorno lo decidera' il
     * PC. Il primo nome un po' piu' lungo sarebbe uscito dalla tessera e
     * sarebbe finito sopra quella accanto, senza che nessuno avesse cambiato
     * una riga di questo file.
     */
    private float larghezzaNome, corpoNome, corpoNota;

    /** "non installata" e' la stessa per tutte le tessere: una Riga basta. */
    private final Testo.Riga rNota = new Testo.Riga();

    public SezioneApp(Context c, Misure m) {
        super(c, m);
        rileggiElenco();

        pTitolo.setColor(Tinte.TESTO);
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pNome.setTextAlign(Paint.Align.CENTER);
        pNome.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pNota.setTextAlign(Paint.Align.CENTER);
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        pNota.setColor(Tinte.SPENTO);
    }

    /** Il meteo e chi sa aprirne la pagina: gli stessi della Home, non una
     *  seconda copia. */
    public void setMeteo(Meteo mt, Meteo.Pagina p) {
        meteo = mt;
        pagina = p;
        invalidate();
    }

    /** Il meteo e' cambiato: la tessera cambia disegno. */
    public void meteoCambiato() { invalidate(); }

    @Override public String titolo() { return "App"; }
    @Override public int tinta() { return Tinte.APP; }

    /** Quattro riquadri. Contornata quando si e' altrove, piena quando si e' qui. */
    @Override public int icona()      { return Icone.APP; }
    @Override public int iconaPiena() { return Icone.APP_PIENA; }

    // ---- quello che il sistema sa ----------------------------------------

    /**
     * Rilegge l'elenco dal sistema.
     *
     * A ogni entrata, non una volta sola nel costruttore: cosi' un'app appena
     * installata compare da sola, e una appena tolta si spegne, senza riavviare
     * Casa.
     */
    @Override
    public void suEntrata() {
        super.suEntrata();
        rileggiElenco();
        if (meteo != null) meteo.aggiorna();
        PackageManager pm = getContext().getPackageManager();
        for (Voce v : voci) {
            try {
                if (METEO.equals(v.pacchetto)) {
                    // Non e' installata da nessuna parte: e' una schermata di
                    // Casa, e c'e' sempre.
                    v.installata = true;
                    v.icona = null;
                    v.apertura = null;
                    continue;
                }
                if (Impostazioni.PACCHETTO.equals(v.pacchetto)) {
                    // Stanno nascoste di proposito (vedi Impostazioni), e il
                    // PackageManager le darebbe per assenti: si guardano da
                    // dietro il velo, e si scoprono solo in apri().
                    ApplicationInfo ai = Impostazioni.info(getContext());
                    v.installata = ai != null;
                    v.apertura = v.installata ? Impostazioni.apertura() : null;
                    v.icona = v.installata ? pm.getApplicationIcon(ai) : null;
                    continue;
                }
                v.apertura = pm.getLaunchIntentForPackage(v.pacchetto);
                v.installata = v.apertura != null;
                v.icona = v.installata ? pm.getApplicationIcon(v.pacchetto) : null;
            } catch (Exception e) {
                v.installata = false;
                v.icona = null;
                v.apertura = null;
            }
        }
        visibili.clear();
        for (Voce v : voci) if (v.installata) visibili.add(v);
        // Le pagine di Casa e il riavvio stanno in fondo e non passano dalla
        // configurazione: non sono app, non si possono disinstallare, e il
        // giorno in cui servono non si puo' dipendere da chi si e' ricordato di
        // metterle in elenco dal PC.
        Voce calendario = new Voce(CALENDARIO, "Calendario");
        calendario.installata = true;
        visibili.add(calendario);
        Voce todo = new Voce(TODO, "To-Do List");
        todo.installata = true;
        visibili.add(todo);
        Voce giornale = new Voce(NOTIZIE, "Notizie");
        giornale.installata = true;
        visibili.add(giornale);
        Voce preferenze = new Voce(PREFERENZE, "Assistente Home");
        preferenze.installata = true;
        visibili.add(preferenze);
        Voce riavvia = new Voce(RIAVVIA, "Riavvia");
        riavvia.installata = true;
        visibili.add(riavvia);
        if (tessere.length < visibili.size()) {
            tessere = new RectF[visibili.size()];
            for (int i = 0; i < tessere.length; i++) tessere[i] = new RectF();
        }
        disponi();
        invalidate();
    }

    /**
     * Rilegge quali app vanno in elenco: quelle scritte dal PC, o le cinque di
     * fabbrica se nessuno ha ancora deciso niente.
     *
     * Un elenco scritto e vuoto vuol dire <b>vuoto</b>, e non "riparti da
     * quelle di prima": chi toglie l'ultima tessera dal PC ha chiesto una
     * sezione senza tessere, e rimettercene cinque sarebbe il programma che
     * discute con chi lo usa.
     */
    private void rileggiElenco() {
        List<Voce> nuove = new ArrayList<Voce>();
        org.json.JSONArray scritte = Configurazione.elenco(getContext(), "app");
        if (scritte == null) {
            for (String[] riga : DI_FABBRICA) nuove.add(new Voce(riga[0], riga[1]));
        } else {
            for (int i = 0; i < scritte.length(); i++) {
                org.json.JSONObject a = scritte.optJSONObject(i);
                if (a == null) continue;
                String pacchetto = a.optString("pacchetto", "");
                if (pacchetto.length() == 0) continue;
                String nome = a.optString("nome", "");
                nuove.add(new Voce(pacchetto, nome.length() > 0 ? nome : pacchetto));
            }
        }
        // Si sostituisce solo se e' cambiato qualcosa: le Voci portano dentro
        // l'icona gia' letta e la misura del nome, e rifarle a ogni entrata
        // vorrebbe dire ricaricare cinque Drawable per niente.
        if (uguali(nuove)) return;
        voci.clear();
        voci.addAll(nuove);
    }

    private boolean uguali(List<Voce> altre) {
        if (altre.size() != voci.size()) return false;
        for (int i = 0; i < altre.size(); i++) {
            if (!altre.get(i).pacchetto.equals(voci.get(i).pacchetto)) return false;
            if (!altre.get(i).nome.equals(voci.get(i).nome)) return false;
        }
        return true;
    }

    /** Le icone di un'app sono Drawable vere e possono pesare: fuori scena non
     *  servono a nessuno. Rientrando si rileggono, e costa qualche
     *  millisecondo. */
    @Override
    public void suUscita() {
        for (Voce v : voci) v.icona = null;
    }

    // ---- misure -----------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        corpoNome = m.corpo;
        corpoNota = m.nota;
        pTitolo.setTextSize(m.micro);
        // L'elenco sta dentro un pannello, come in Casa e nell'Orologio: con
        // due app installate la griglia occupa un quarto di schermo, e senza un
        // bordo attorno quel che avanza si legge come una schermata rotta
        // invece che come posto per le prossime.
        pannello.set(m.margine, h * 0.115f, w - m.margine, h - m.margine);
        pNome.setTextSize(corpoNome);
        pNota.setTextSize(corpoNota);
        disponi();
    }

    /**
     * La griglia, che dipende da quante app ci sono adesso.
     *
     * Si rifa' sia quando cambia la misura sia quando cambia l'elenco, e i due
     * arrivano in ordine imprevedibile: {@code suEntrata} puo' precedere la
     * prima misura, e li' la larghezza e' ancora zero. E' la lezione che la
     * barra delle app aveva gia' imparato su onResume.
     */
    private void disponi() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float mg = m.margine;

        int quante = visibili.size();
        if (quante == 0) return;

        float bandaTitolo = m.s3 + m.micro + m.s3;
        float cima = pannello.top + bandaTitolo;
        float fondo = pannello.bottom - m.s3;
        int righe = (quante + COLONNE - 1) / COLONNE;

        // La larghezza di una tessera resta quella di quattro colonne anche
        // quando le app sono due: se le tessere si allargassero, disinstallare
        // un'app sposterebbe tutte le altre sotto il dito.
        float passoX = (pannello.width() - m.s3 * 2f) / COLONNE;
        float gap = m.s2;

        // <b>Le righe si dividono l'altezza, ma fino a un certo punto.</b> Con
        // sette app le righe erano due e il conto tornava; con quattro diventa
        // una sola, e dividere l'altezza per uno dava tessere larghe
        // duecentosessanta e alte seicentocinquanta - una colonna, non una
        // tessera, con l'icona persa in mezzo al vuoto.
        //
        // Sopra una volta e un quinto la larghezza la tessera smette di
        // sembrare un pulsante, quindi li' ci si ferma e quel che avanza
        // diventa aria: la griglia si centra in mezzo invece di stirarsi.
        float passoY = Math.min((fondo - cima) / righe, passoX * 0.95f);
        // In alto, non in mezzo: dentro un pannello con un titolo, una griglia
        // che parte dall'alto si legge come un elenco con posto per altre voci;
        // la stessa griglia centrata sembra sospesa nel niente.
        float cimaGriglia = cima;

        for (int i = 0; i < quante; i++) {
            int riga = i / COLONNE, col = i % COLONNE;
            // Ogni riga si centra per conto suo: con cinque app la seconda riga
            // ne ha una sola, e lasciarla incollata a sinistra sembrerebbe un
            // errore di impaginazione invece di una riga corta.
            int inQuestaRiga = Math.min(quante - riga * COLONNE, COLONNE);
            float sinistra = pannello.left + m.s3
                    + ((pannello.width() - m.s3 * 2f) - passoX * inQuestaRiga) / 2f;
            tessere[i].set(sinistra + passoX * col + gap / 2f,
                           cimaGriglia + passoY * riga + gap / 2f,
                           sinistra + passoX * (col + 1) - gap / 2f,
                           cimaGriglia + passoY * (riga + 1) - gap / 2f);
        }

        // Le stesse tre bande della sezione Radio: aria, icona, nome. Calcolate
        // e non indovinate, o il nome finisce sotto l'icona.
        float tessH = passoY - gap, tessW = passoX - gap;
        float aria = tessH * 0.10f;
        float altezzaNome = corpoNome * 1.4f;
        larghezzaNome = tessW * 0.92f;
        float banda = tessH - aria * 2f - altezzaNome;
        latoIcona = Math.min(banda, tessW * 0.40f);
        cyIcona = aria + banda / 2f;
        yNome = tessH - aria - corpoNota * 0.9f;
    }

    // ---- disegno ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (vetro == null || !vetro.vivo()) return;

        // Il titolo grande della sezione, come nelle app di Apple.
        pTitolo.setTextSize(m.titolo);
        c.drawText("App", m.margine, pannello.top - m.s3, pTitolo);
        vetro.pannello(c, pannello, m.raggio, Tinte.APP, Tinte.VELO_QUIETO);

        if (visibili.isEmpty()) {
            // Una schermata vuota senza spiegazione fa sembrare rotto il
            // tablet. E' la stessa regola della stazione che non parte.
            pNota.setTextAlign(Paint.Align.LEFT);
            pNota.setColor(Tinte.SPENTO);
            c.drawText(rNota.in(pNota, "nessuna app installata", pannello.width() - m.s3 * 2f,
                            corpoNota),
                       pannello.left + m.s3, pannello.top + m.s5, pNota);
            pNota.setTextAlign(Paint.Align.CENTER);
            return;
        }

        for (int i = 0; i < visibili.size(); i++) {
            Voce v = visibili.get(i);
            RectF t = tessere[i];
            boolean giu = (i == premuta);

            // Le tessere arrivano una dopo l'altra, scorrendo di poco: si
            // muove la posizione e non l'opacita', che vorrebbe dire un
            // livello fuori schermo per tessera.
            int salvato = c.save();
            float avanti = Anima.posa(Anima.entrata(entrata, i));
            if (avanti < 1f) c.translate(0f, (1f - avanti) * m.s5);

            // Un riempimento piatto, non vetro: la tessera e' un tasto.
            pFondo.setColor(giu ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
            c.drawRoundRect(t, m.raggio, m.raggio, pFondo);

            float cx = t.centerX(), cy = t.top + cyIcona;

            // Le tessere di Casa hanno l'icona alla iOS: un quadrato pieno del
            // loro colore, angoli larghi, il segno bianco sopra. Le app
            // installate portano gia' la loro.
            float mezzaIcona = latoIcona * 0.5f;
            icona.set(cx - mezzaIcona, cy - mezzaIcona, cx + mezzaIcona, cy + mezzaIcona);
            float angolo = latoIcona * 0.22f;
            float segno = latoIcona * 0.56f;

            if (CALENDARIO.equals(v.pacchetto)) {
                pFondo.setColor(Tinte.AGENDA);
                c.drawRoundRect(icona, angolo, angolo, pFondo);
                pNota.setColor(Tinte.TESTO);
                Icone.disegna(c, Icone.OGGI, cx, cy, segno, pNota);
            } else if (TODO.equals(v.pacchetto)) {
                pFondo.setColor(Tinte.AGENDA);
                c.drawRoundRect(icona, angolo, angolo, pFondo);
                pNota.setColor(Tinte.TESTO);
                Icone.disegna(c, Icone.SPUNTA, cx, cy, segno, pNota);
                // Quante ne restano da fare, sopra l'icona: e' l'unica tessera
                // che ha qualcosa da dire prima di essere toccata, come il
                // meteo.
                int quante = appunti == null ? 0 : appunti.quanteDaFare();
                if (quante > 0) {
                    pNota.setTextSize(m.nota);
                    pNota.setColor(Tinte.TESTO);
                    String n = String.valueOf(quante);
                    // Il pallino rosso in alto a destra, come i numeri sulle
                    // icone di iOS.
                    float raggio = m.nota * 0.85f;
                    float bx = icona.right - raggio * 0.35f, by = icona.top + raggio * 0.35f;
                    pNota.setColor(Tinte.ALLARME);
                    c.drawCircle(bx, by, raggio, pNota);
                    pNota.setColor(Tinte.TESTO);
                    pNota.setTextAlign(Paint.Align.CENTER);
                    c.drawText(n, bx, by - (pNota.descent() + pNota.ascent()) / 2f, pNota);
                    pNota.setTextAlign(Paint.Align.LEFT);
                }
            } else if (NOTIZIE.equals(v.pacchetto)) {
                pFondo.setColor(Tinte.NOTIZIE);
                c.drawRoundRect(icona, angolo, angolo, pFondo);
                pNota.setColor(Tinte.TESTO);
                Icone.disegna(c, Icone.NOTIZIE, cx, cy, segno, pNota);
            } else if (PREFERENZE.equals(v.pacchetto)) {
                // Grigio, come l'icona delle Impostazioni su iOS.
                pFondo.setColor(Tinte.TESTO_TENUE);
                c.drawRoundRect(icona, angolo, angolo, pFondo);
                pNota.setColor(Tinte.TESTO);
                Icone.disegna(c, Icone.REGOLA, cx, cy, segno, pNota);
            } else if (RIAVVIA.equals(v.pacchetto)) {
                boolean aspetta = aspettaConferma();
                pFondo.setColor(aspetta ? Tinte.ALLARME : 0xFF3A3A3C);
                c.drawRoundRect(icona, angolo, angolo, pFondo);
                pNota.setColor(Tinte.TESTO);
                Icone.disegna(c, Icone.ACCENSIONE, cx, cy, segno, pNota);
            } else if (METEO.equals(v.pacchetto)) {
                // Qui il disegno e' la scena animata, non un'icona: e' l'unica
                // tessera che ha qualcosa da dire prima di essere toccata.
                Meteo.Adesso ad = meteo != null ? meteo.adesso() : null;
                float mezzo = latoIcona * 0.62f;
                // Il quadrato azzurro dell'app Meteo, e sopra il cielo vero.
                pFondo.setColor(0xFF1C6FD1);
                c.drawRoundRect(icona, angolo, angolo, pFondo);
                mezzo = latoIcona * 0.42f;
                quadretto.set(cx - mezzo, cy - mezzo, cx + mezzo, cy + mezzo);
                riquadroMeteo.set((int) t.left - 2, (int) t.top - 2,
                                  (int) t.right + 2, (int) t.bottom + 2);
                if (ad != null) {
                    cielo.disegna(c, quadretto, ad.codice, ad.giorno);
                } else {
                    pNota.setColor(Tinte.TESTO);
                    Icone.disegna(c, Icone.NUVOLA, cx, cy, segno, pNota);
                }
            } else if (v.icona != null) {
                int lato = Math.round(latoIcona);
                riquadro.set(Math.round(cx - lato / 2f), Math.round(cy - lato / 2f),
                             Math.round(cx + lato / 2f), Math.round(cy + lato / 2f));
                v.icona.setBounds(riquadro);
                v.icona.setAlpha(255);
                v.icona.draw(c);
            } else {
                // Installata ma senza icona in mano: succede fra l'entrata e il
                // caricamento, e all'uscita, quando le Drawable si rilasciano.
                // Un riquadro del colore della sezione al posto del vuoto.
                pFondo.setColor(Tinte.RIEMPIMENTO);
                c.drawRoundRect(icona, angolo, angolo, pFondo);
            }

            // Il riavvio dice cosa aspetta, al posto del suo nome: e' l'unica
            // tessera che chiede due tocchi, e chi ha dato il primo deve
            // vedere sulla tessera stessa che e' stato sentito.
            boolean chiede = RIAVVIA.equals(v.pacchetto) && aspettaConferma();
            pNome.setColor(chiede ? Tinte.ALLARME : Tinte.TESTO);
            // Prima si rimpicciolisce, e solo se non basta si taglia: un nome
            // di un'app e' quello che dice cos'e' la tessera, e leggerlo
            // intero un corpo piu' piccolo vale piu' che leggerne meta' grande.
            c.drawText(v.riga.adatta(pNome, chiede ? "premi ancora" : v.nome,
                            larghezzaNome, corpoNome, corpoNome * 0.72f),
                       cx, t.top + yNome, pNome);
            c.restoreToCount(salvato);
        }
        Anima.continua(this, entrata, visibili.size());
        // La tessera del meteo si muove, e come nella Home lo fa a venti
        // fotogrammi al secondo e solo sul suo rettangolo: una schermata di
        // scorciatoie non e' il posto dove spendere la GPU.
        if (meteo != null && meteo.adesso() != null && !riquadroMeteo.isEmpty()) {
            postInvalidateDelayed(50, riquadroMeteo.left, riquadroMeteo.top,
                                  riquadroMeteo.right, riquadroMeteo.bottom);
        }
    }

    // ---- tocco ------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                premuta = quale(e.getX(), e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                int i = quale(e.getX(), e.getY());
                if (i >= 0 && i == premuta) apri(visibili.get(i));
                premuta = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                premuta = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int quale(float x, float y) {
        for (int i = 0; i < tessere.length && i < visibili.size(); i++) {
            if (tessere[i].contains(x, y)) return i;
        }
        return -1;
    }

    /** Il primo tocco e' gia' arrivato, e non e' ancora scaduto? */
    private boolean aspettaConferma() {
        return chiestoRiavvio > 0 && Anima.ora() - chiestoRiavvio < CONFERMA;
    }

    private void apri(Voce v) {
        if (CALENDARIO.equals(v.pacchetto)) {
            if (pagine != null) pagine.apriCalendario();
            return;
        }
        if (TODO.equals(v.pacchetto)) {
            if (pagine != null) pagine.apriToDo();
            return;
        }
        if (NOTIZIE.equals(v.pacchetto)) {
            if (pagine != null) pagine.apriNotizie();
            return;
        }
        if (PREFERENZE.equals(v.pacchetto)) {
            if (pagine != null) pagine.apriPreferenze();
            return;
        }
        if (RIAVVIA.equals(v.pacchetto)) {
            if (aspettaConferma()) {
                chiestoRiavvio = 0;
                MainActivity.riavvia(getContext());
            } else {
                chiestoRiavvio = Anima.ora();
                invalidate();
                // Fra tre secondi la tessera torna a dire « Riavvia »: senza
                // questo resterebbe li' ad aspettare per sempre un secondo
                // tocco che non arriva piu'.
                postDelayed(new Runnable() {
                    @Override public void run() { invalidate(); }
                }, CONFERMA + 50);
            }
            return;
        }
        if (METEO.equals(v.pacchetto)) {
            if (pagina != null) pagina.apriMeteo();
            return;
        }
        if (v.apertura == null) return;
        try {
            if (Impostazioni.PACCHETTO.equals(v.pacchetto)) {
                Impostazioni.mostra(getContext());
            }
            // NEW_TASK perche' si parte da una View e non da un contesto di
            // Activity garantito, e CLEAR_TOP perche' tornando a un'app gia'
            // aperta si vuole quella dov'era, non una seconda copia.
            v.apertura.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                              | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            getContext().startActivity(v.apertura);
        } catch (Exception e) {
            Log.w("Casa", "non riesco ad aprire " + v.pacchetto, e);
        }
    }
}
