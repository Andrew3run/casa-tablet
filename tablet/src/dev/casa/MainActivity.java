package dev.casa;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.view.WindowManager;
import android.Manifest;

/**
 * La regia di Casa.
 *
 * Non disegna piu' niente da se': monta {@link Telaio} e tiene i fili che non
 * appartengono a nessuna sezione - device owner, chiosco, sfondo di sistema,
 * permessi, barre di sistema, ciclo di vita.
 *
 * Se il device owner c'e', il chiosco si chiude davvero: lock task senza
 * conferme, e nessun modo di uscire. Se non c'e', l'app funziona lo stesso ma
 * resta una Home normale da cui si puo' uscire - utile in sviluppo, e utile
 * come rete di sicurezza se il device owner non e' ancora stato impostato.
 */
public class MainActivity extends Activity {

    /**
     * L'unico tag del registro di Casa. Il nome del pezzo sta nel messaggio,
     * non nel tag.
     *
     * <b>Non e' una preferenza di stile: su questo ROM un tag fuori lista non
     * si vede.</b> Il MediaTek nasce con {@code log.tag = E} - il livello di
     * serie per qualunque tag e' ERRORE - e passano solo i tag aperti a mano,
     * uno per uno, con {@code persist.log.tag.<nome>}. In lista c'erano
     * {@code Casa} e {@code Casa.App}, e basta.
     *
     * Vuol dire che tutto quello che era stato scritto sotto
     * {@code Casa.Luci}, {@code Casa.Tuya}, {@code Casa.Vetro} e
     * {@code Casa.Archivio} <b>non e' mai arrivato a nessuno</b>, e nessuno se
     * n'era accorto: il modo in cui un log sparisce e' non comparire. Lo si e'
     * scoperto perche' il programma sul PC chiedeva lo stato della parola di
     * attivazione e non riceveva mai risposta, pur essendo il ricevitore
     * registrato e il broadcast consegnato.
     *
     * Un prefisso nel messaggio fa per chi legge lo stesso lavoro del tag
     * ({@code logcat -s Casa:I | grep parola}) e non dipende da una proprieta'
     * di sistema che un ripristino di fabbrica porterebbe via. Per riaprire i
     * tag vecchi, se un giorno servisse:
     *
     * <pre>
     *     adb shell setprop persist.log.tag.Casa.Luci V
     * </pre>
     */
    static final String TAG = "Casa";

    /** L'ordine delle sezioni nella barra. Le costanti servono alle spie.
     *
     *  La Musica sta subito sotto la Home perche' e' la seconda cosa che si
     *  tocca in una giornata, e perche' la barra si percorre col pollice dal
     *  basso verso l'alto solo quando si e' seduti - appesa al muro si punta
     *  direttamente. */
    private static final int HOME = 0, MUSICA = 1, RADIO = 2, APP = 3, OROLOGIO = 4, LUCI = 5;

    private DevicePolicyManager dpm;
    private ComponentName admin;

    private Misure misure;
    private Loghi loghi;
    private Telaio telaio;
    private SezioneHome home;
    private SezioneMusica sezioneMusica;
    private SezioneRadio sezioneRadio;
    private SezioneOrologio sezioneOrologio;
    private SezioneLuci sezioneLuci;
    private SezioneApp sezioneApp;

    /** Il calendario e la lista: due pagine intere, aperte da due tessere della
     *  sezione App. Non sono sezioni della barra - sei voci sono il numero che
     *  ci sta comodo, e il perche' e' scritto su VelaToDo. */
    private VelaCalendario velaCalendario;
    private VelaToDo velaToDo;

    /** Gli impegni che arrivano da fuori e le note che si scrivono qui. Vivono
     *  fuori dalle sezioni perche' li guardano in tre: l'agenda, la voce e il
     *  riposo. */
    private Calendario calendario;
    private Appunti appunti;

    /**
     * Il tempo che fa: uno per tutta Casa.
     *
     * Lo guardano in tre - il pannello della Home, la tessera della sezione
     * App, e la pagina intera - e sarebbe facile darne uno a testa. Sarebbero
     * tre richieste alla rete ogni mezz'ora e tre copie su disco che si
     * riscrivono a vicenda, per la stessa risposta: e' lo stesso ragionamento
     * per cui i loghi delle stazioni sono un magazzino solo.
     */
    private Meteo meteo;
    private VelaMeteo velaMeteo;
    private BroadcastReceiver meteoGia;

    /** Le notizie: una per tutta Casa, come il meteo, e per la stessa ragione.
     *  Le guardano in tre - la Home, il riposo e la pagina - e sarebbero tre
     *  richieste alla rete per gli stessi titoli. */
    private Notizie notizie;
    private VelaNotizie velaNotizie;
    private BroadcastReceiver notizieGia;

    /** Timer, sveglie e lampade. Vivono fuori dalle sezioni perche' li
     *  comandano anche la voce e la Home, e perche' l'Orologio sopravvive
     *  all'Activity: una sveglia non appartiene a una finestra. */
    private Orologio orologio;
    private Luci luci;
    private Orologio.Spia spiaOrologio;

    /** Il motore di Spotify dentro Casa, le playlist e le copertine. Come
     *  l'Orologio, la {@link Musica} e' una per processo e non muore con la
     *  finestra: se Android rifa' l'Activity mentre suona, il brano non se ne
     *  accorge. */
    private Musica musica;
    private Preferiti preferiti;
    private Copertine copertine;
    private Cerca ricerca;
    private Musica.Spia spiaMusica;

    /** Il riposo: la fotografia che compare quando non c'e' nessuno e non c'e'
     *  niente in corso. Vive qui perche' le condizioni che lo tengono lontano -
     *  la radio, la musica, un timer, una vela aperta - sono sparse fra pezzi
     *  che solo la regia vede tutti insieme. */
    private Riposo riposo;

    /** La modalita' notte: abbassa lo schermo quando il riposo e' in scena di
     *  sera. E la pagina delle impostazioni di Casa, che la regola. */
    private Notte notte;
    private VelaPreferenze velaPreferenze;

    private Voce voce;
    private Comandi comandi;
    private ComandiUtente comandiUtente;
    private Sordina sordina;
    private Riproduzione riproduzione;
    private BroadcastReceiver provaGia;
    private BroadcastReceiver riposoGia;
    private BroadcastReceiver agendaGia;

    /** La parola di attivazione riconosciuta a bordo, e le registrazioni con
     *  cui impara. Vivono qui perche' il microfono e' uno solo per tutta
     *  l'app: chi lo cede al riconoscitore e chi se lo riprende devono essere
     *  la stessa mano. */
    private Campioni campioni;
    private Risveglio risveglio;
    private VelaImpostazioni impostazioni;
    private BroadcastReceiver parolaGia;
    private BroadcastReceiver configGia;

    private final android.os.Handler scadenzaMessaggio = new android.os.Handler();

    /** onCreate e onResume arrivano a mezzo secondo l'uno dall'altro: senza
     *  questa guardia comporrebbero due immagini da 1280x800 in parallelo, e su
     *  API 24 i pixel di una Bitmap stanno nell'heap Java. */
    private boolean sfondoInCorso;

    @Override
    protected void onCreate(Bundle stato) {
        super.onCreate(stato);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AdminReceiver.class);

        // Lo schermo di un apparecchio da muro non si spegne.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Prima di tutto il resto: l'orologio che si vede sulla Home deve
        // segnare l'ora di qui, non quella di fabbrica.
        rimettiIlFuso();

        // Se il PC ha lasciato una configurazione nella buca, si prende adesso:
        // prima che le sezioni si costruiscano, o le luci e le app nascerebbero
        // con quelle di ieri e cambierebbero sotto gli occhi un attimo dopo.
        Configurazione.importa(this);

        misure = new Misure(this);
        setContentView(schermata());
        telaio.preparaVetro(this);
        prendiLoSfondo();
        agganciaMusica();
        agganciaMeteo();
        agganciaMeteoDaAdb();
        agganciaNotizie();
        agganciaChiave();
        agganciaRiproduzione();
        agganciaOrologio();
        agganciaLuci();
        agganciaAgenda();
        agganciaRiposo();
        agganciaVetrina();
        agganciaTelefono();
        ascoltaLaRete();

        if (siamoDeviceOwner()) {
            diventaHomePermanente();
            spegniIlBlocco();
            chiudiLAreaNotifiche();
            // Prima di BOOT_COMPLETED, che il sistema manda dopo che la Home
            // e' in piedi: cosi' il receiver in loop del ROM non parte mai.
            Impostazioni.nascondi(this);

            if (chioscoRichiesto()) {
                // Con il device owner startLockTask() non chiede niente: entra
                // e basta. Senza, chiederebbe conferma ogni volta, che su un
                // apparecchio senza tastiera non ha senso.
                dpm.setLockTaskPackages(admin, new String[] { getPackageName() });
                startLockTask();
                Log.i(TAG, "chiosco attivo");
            } else {
                Log.i(TAG, "chiosco spento: si esce ancora da qui");
            }
        } else {
            Log.i(TAG, "device owner assente: Home normale, si puo' uscire");
        }

        avviaAssistente();
    }

    /** Barra a sinistra, sezione a destra, sfondo dietro tutto. */
    private View schermata() {
        loghi = new Loghi(this);
        home = new SezioneHome(this, misure);
        sezioneMusica = new SezioneMusica(this, misure);
        sezioneRadio = new SezioneRadio(this, misure);
        sezioneRadio.setLoghi(loghi);
        // La Home mostra il logo della stazione che suona: e' lo stesso
        // magazzino della sezione Radio, non un secondo.
        home.setLoghi(loghi);
        sezioneOrologio = new SezioneOrologio(this, misure);
        sezioneLuci = new SezioneLuci(this, misure);
        sezioneApp = new SezioneApp(this, misure);
        Sezione[] sezioni = {
            home,
            sezioneMusica,
            sezioneRadio,
            sezioneApp,
            sezioneOrologio,
            sezioneLuci,
        };
        telaio = new Telaio(this, misure, sezioni);

        // Nessun campo di testo e nessuna tastiera di sistema: la sezione
        // Musica ha la sua (vedi Tastierino), alta un terzo invece che meta'.
        FrameLayout radice = new FrameLayout(this);
        radice.addView(telaio, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        return radice;
    }

    /**
     * Casa si prende lo sfondo di sistema.
     *
     * Il tablet e' di seconda mano: sotto Casa si rivedeva ancora lo sfondo del
     * ROM di chi lo aveva prima - all'apertura di un'app, dietro le finestre
     * non opache, sul blocco. Quel pixel non e' di Casa, e' del sistema, quindi
     * l'unico modo di toglierlo di mezzo e' possederlo.
     *
     * Su un thread a parte perche' comporre l'immagine e scriverla costa
     * qualche centinaio di millisecondi, e questo e' il tempo che ci mette la
     * Home a comparire. Non e' un lavoro che si rifa' a ogni avvio: dentro
     * prendiPossesso c'e' il confronto che lo salta quando lo sfondo giusto
     * c'e' gia'.
     */
    private void prendiLoSfondo() {
        if (sfondoInCorso) return;
        sfondoInCorso = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Sfondo.prendiPossesso(MainActivity.this, misure,
                            Sfondo.variante(System.currentTimeMillis()));
                } catch (Throwable t) {
                    Log.w(TAG, "sfondo non impostato", t);
                } finally {
                    sfondoInCorso = false;
                }
            }
        }, "Casa-sfondo").start();
    }

    /**
     * Collega la musica.
     *
     * <b>Non la accende.</b> Il demone parte quando qualcuno entra nella
     * sezione (vedi {@link SezioneMusica#suEntrata()}): dodici megabyte e una
     * connessione a Spotify tenuti in piedi da quando il tablet si accende,
     * per una musica che magari oggi non si ascolta, sono dodici megabyte
     * tolti a tutto il resto.
     *
     * La sessione media invece si pubblica subito: e' quella che rende la
     * musica comandabile dai tasti, e non costa niente finche' non c'e' niente
     * da comandare.
     */
    private void agganciaMusica() {
        musica = Musica.di(this);
        copertine = new Copertine(this);
        preferiti = new Preferiti(this);
        ricerca = new Cerca(this);
        // La pagina di un artista si prende col token della sessione, non con
        // la chiave: la ricerca ha bisogno di sapere chi glielo da'.
        ricerca.setMusica(musica);

        spiaMusica = new Musica.Spia() {
            @Override public void suMusica() {
                sezioneMusica.risveglia();
                home.invalidate();
                aggiornaSpie();
                // Le playlist si possono chiedere solo da sessione aperta, e il
                // nome dell'utente arriva col primo stato: entrando nella
                // sezione non si sa ancora. Qui invece si e' appena saputo -
                // e la chiamata non costa niente quando l'elenco e' fresco.
                if (preferiti != null) preferiti.aggiorna(musica);
            }
        };
        musica.setSpia(spiaMusica);
        musica.pubblica(this);

        copertine.setAvviso(new Copertine.Pronta() {
            @Override public void copertinaArrivata() { sezioneMusica.risveglia(); }
        });
        preferiti.setAscolto(new Preferiti.Ascolto() {
            @Override public void preferitiCambiati() { sezioneMusica.risveglia(); }
        });

        sezioneMusica.setMusica(musica);
        sezioneMusica.setPreferiti(preferiti);
        sezioneMusica.setCopertine(copertine);
        // La Home mostra la copertina del disco che suona: stessa cache,
        // stessa bitmap, nessuno scaricamento in piu'.
        home.setCopertine(copertine);
        sezioneMusica.setCerca(ricerca);
        home.setMusica(musica);
    }

    /**
     * Collega la Home a quello che suona nelle altre app.
     *
     * Sta fuori da {@link #avviaAssistente()} di proposito: quello si ferma ad
     * aspettare il permesso del microfono, e senza microfono la Home deve
     * comunque sapere cosa sta suonando su Spotify. Erano due cose legate solo
     * dall'ordine in cui erano scritte.
     */
    private void agganciaRiproduzione() {
        riproduzione = new Riproduzione(this, new Riproduzione.Spia() {
            @Override public void suRiproduzione() {
                home.invalidate();
            }
        });
        home.setRiproduzione(riproduzione);
        if (!riproduzione.sappiamoCosaSuona()) {
            Log.i(TAG, "permesso notifiche assente: i tasti comandano lo stesso, "
                     + "il titolo no. Si concede con tools/permesso-notifiche.ps1");
        }
    }

    /**
     * Collega timer e sveglie.
     *
     * L'{@link Orologio} e' uno per processo e <b>non muore con l'Activity</b>:
     * se Android rifa' la finestra mentre un timer scorre, il timer non se ne
     * accorge. Qui si dice soltanto chi lo sta guardando adesso, e al momento
     * di attaccarsi lui racconta subito cosa c'e' in corso - compreso un
     * allarme che sta suonando proprio ora, che e' il caso in cui Casa e' stata
     * riaperta dal ricevitore.
     */
    private void agganciaOrologio() {
        orologio = Orologio.di(this);
        spiaOrologio = new Orologio.Spia() {
            @Override public void suCambio() {
                Orologio.Conto primo = orologio.primoConto();
                home.setTimer(primo != null ? primo.restano() : -1);
                home.setSveglia(orologio.prossimaSveglia());
                sezioneOrologio.risveglia();
                aggiornaSpie();
            }
            @Override public void suScatto(String titolo, boolean sveglia) {
                mostraAllarme(titolo, sveglia);
            }
            @Override public void suSilenzio() {
                if (telaio != null) telaio.nascondiVela();
            }
        };
        sezioneOrologio.setOrologio(orologio);
        orologio.setSpia(spiaOrologio);
    }

    /**
     * Collega le lampade, e le sveglia.
     *
     * <b>La prima versione non interrogava niente all'avvio</b>, col
     * ragionamento che all'accensione conta che la Home compaia, non che tre
     * lampadine addormentate vengano svegliate. Il ragionamento era giusto e la
     * conclusione sbagliata, e si e' visto alla prima prova vera: premere
     * « Camera » non faceva niente per mezzo minuto, mentre dopo aver premuto
     * una routine - che le lampade le aveva svegliate - lo stesso tocco
     * rispondeva subito.
     *
     * Il motivo e' quello scritto in {@link Tuya}: una lampada che dorme non
     * sente l'ARP, e il <b>primo</b> collegamento e' quello che paga fino a
     * mezzo minuto di insistenza. Quel mezzo minuto qualcuno lo paga comunque;
     * la scelta e' solo se lo paga il tablet appena acceso, quando non lo
     * guarda nessuno, o la persona che ha appena premuto un pulsante.
     *
     * Quindi si sveglia all'avvio, ma <b>dopo qualche secondo</b>: la Home
     * compare per prima, e il lavoro va comunque su un thread suo. Da li' in
     * avanti la voce resta nella tabella degli indirizzi e si rinfresca da
     * sola, quindi il tocco costa due decimi.
     */
    private void agganciaLuci() {
        luci = new Luci(this);
        luci.setAscolto(new Luci.Ascolto() {
            @Override public void luciCambiate() {
                home.luciCambiate();
                sezioneLuci.risveglia();
                aggiornaSpie();
            }
        });
        sezioneLuci.setLuci(luci);
        // E le lampade premibili dalla Home: accendere una luce e' la cosa che
        // si fa passando davanti al tablet, non dopo essere andati a cercarla
        // in un'altra sezione. La sezione resta a un tocco: la scheda si apre.
        home.setApparecchioLuci(luci, new Luci.Pagina() {
            @Override public void apriLuci() {
                if (telaio != null) telaio.vaiA(LUCI);
            }
        });

        // Il ritardo non e' scaramanzia: nei primi secondi il sistema sta
        // ancora avviando i servizi, e la rete di un tablet appena acceso puo'
        // non essere nemmeno agganciata al Wi-Fi. Bussare a una lampada prima
        // di avere un indirizzo vuol dire spendere l'insistenza per niente.
        risveglioLuci.postDelayed(new Runnable() {
            @Override public void run() {
                if (luci == null) return;
                luci.aggiorna();          // con gli indirizzi che gia' sappiamo
                luci.scopri();            // e intanto si ascolta chi si e' spostato
            }
        }, 4000);

        // La vetrina per il PC: quello che Casa sta usando adesso, comprese le
        // lampade e le app di fabbrica se nessuno ha ancora configurato niente.
        // Senza, Gestione Home su un tablet appena installato troverebbe la
        // cartella vuota e dovrebbe indovinare da dove partire.
        Configurazione.rispecchia(this, Configurazione.effettiva(this, luci, comandi, laRadio()));
    }

    // ---- il telefono ----------------------------------------------------------

    private Telefono telefono;

    /**
     * Il telefono che comanda Casa da lontano: le luci, le sveglie, una frase.
     * Il come sta in {@link Telefono} e in docs/telefono.md.
     *
     * Il codice di abbinamento si scrive nella riga di stato della Home, e per
     * vederlo la Home dev'essere davanti: si torna li', e se c'era il riposo si
     * toglie. Chi abbina un telefono e' in piedi davanti al tablet con il
     * telefono in mano, e deve leggere sei cifre.
     */
    private void agganciaTelefono() {
        telefono = new Telefono(this, new Telefono.Regia() {
            @Override public void frase(String testo) {
                if (comandi == null) return;
                home.setStato("“" + testo + "”");
                comandi.esegui(testo);
            }
            @Override public boolean radioAccesa() { return comandi != null && comandi.radioAccesa(); }
            @Override public String nomeRadio() { return comandi != null ? comandi.nomeRadio() : ""; }
            @Override public void codice(String codice, String nome) {
                scadenzaMessaggio.removeCallbacksAndMessages(null);
                if (codice == null) { home.setStato(null); return; }
                if (riposo != null && riposo.inScena()) { riposo.fermati(); riposo.riparti(); }
                if (telaio != null) telaio.vaiA(HOME);
                home.setStato("codice per " + nome + ": " + codice);
            }
            @Override public void abbinato(String nome) {
                mostraPerUnAttimo(nome + " abbinato");
            }
            @Override public Radio radio() { return laRadio(); }
            @Override public Musica musica() { return musica; }
            @Override public Preferiti preferiti() { return preferiti; }
            @Override public Cerca ricerca() { return ricerca; }
        }, luci, orologio, appunti);
        telefono.avvia();
    }

    // ---- il ritorno -------------------------------------------------------------

    /**
     * Quanto deve durare un'assenza perche' valga la pena rinfrescare tutto.
     *
     * Mezz'ora: sotto, meteo e notizie sono ancora dentro la loro scadenza e
     * {@code riprova} non farebbe niente comunque; sopra, chi rientra deve
     * trovare il tablet di adesso.
     */
    private static final long ASSENZA_MS = 30 * 60000L;

    /** L'ultimo tocco, e quando Casa e' andata dietro. In elapsedRealtime, che
     *  conta anche il tempo passato a schermo spento. */
    private long ultimoTocco = SystemClock.elapsedRealtime();
    private long dietroDal;

    private ConnectivityManager.NetworkCallback rete;

    /**
     * Si torna dopo tanto: tutto quello che invecchia si rinfresca adesso.
     *
     * <h3>Perche' non bastavano i giri</h3>
     *
     * Tornando dopo tre giorni la Home diceva « aggiornate 3 giorni fa », e il
     * meteo era quello di allora. I giri c'erano - le notizie ogni cinque
     * minuti - ma un giro che va a vuoto una volta aspetta il successivo, e il
     * meteo un giro non l'aveva proprio: si aggiornava solo entrando nella
     * Home, che e' la schermata da cui non si esce mai.
     *
     * Adesso il meteo ha il suo giro, e in piu' ci sono tre momenti in cui si
     * sa che qualcuno sta per guardare: il primo tocco dopo mezz'ora senza
     * nessuno - che e' anche il tocco che toglie il riposo - Casa che torna in
     * scena dopo mezz'ora dietro, e la rete che torna. In quei momenti si
     * chiede subito, senza rispettare l'attesa fra un errore e l'altro.
     *
     * Le lampade si rileggono senza insistere: il tocco che viene subito dopo
     * e' quello di chi rientra, e non deve trovarle occupate.
     */
    private void tornaFresco(String perche, boolean ancheLeLuci) {
        Log.i(TAG, "si rinfresca tutto: " + perche);
        if (meteo != null) meteo.riprova();
        if (notizie != null) notizie.riprova();
        if (calendario != null) calendario.aggiorna();
        if (ancheLeLuci && luci != null) luci.sbircia();
    }

    /**
     * La rete torna: meteo e notizie non aspettano il prossimo giro.
     *
     * Le luci hanno gia' un orecchio loro sulla stessa cosa ({@code Luci}), e
     * qui non si toccano. Tre secondi di ritardo perche' « rete disponibile »
     * arriva prima che il DNS risponda, e una richiesta partita in quel mezzo
     * secondo fallisce e fa aspettare il giro dopo - proprio quello che si
     * voleva evitare.
     */
    private void ascoltaLaRete() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            rete = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network quale) {
                    scadenzaRete.removeCallbacksAndMessages(null);
                    scadenzaRete.postDelayed(new Runnable() {
                        @Override public void run() { tornaFresco("la rete e' tornata", false); }
                    }, 3000);
                }
            };
            cm.registerDefaultNetworkCallback(rete);
        } catch (Exception e) {
            Log.w(TAG, "non riesco a farmi avvisare quando la rete torna: " + e.getMessage());
        }
    }

    private final android.os.Handler scadenzaRete = new android.os.Handler();

    /**
     * Collega l'agenda: gli impegni e le note.
     *
     * <b>Il calendario non si sincronizza qui.</b> A tenerlo allineato con
     * Google ci pensa il sincronizzatore di sistema; Casa legge il provider e
     * basta, e quando quello cambia il provider avvisa. Il perche' per esteso
     * sta in {@link Calendario}.
     *
     * Quello che invece tocca a Casa e' <b>rimettere il magazzino</b>: questo
     * tablet e' stato alleggerito e il provider del calendario era stato tolto
     * per l'utente. Da device owner si rimette senza chiedere niente a nessuno.
     */
    private void agganciaAgenda() {
        appunti = new Appunti(this);
        calendario = new Calendario(this);

        sezioneApp.setAppunti(appunti);
        sezioneApp.setPagine(new SezioneApp.Pagine() {
            @Override public void apriCalendario() { apriIlCalendario(); }
            @Override public void apriToDo() { apriLaLista(); }
            @Override public void apriNotizie() { apriLeNotizie(); }
            @Override public void apriPreferenze() { apriLePreferenze(); }
        });

        appunti.aggiungiAscolto(new Appunti.Ascolto() {
            @Override public void noteCambiate() {
                if (velaToDo != null) velaToDo.risveglia();
                if (sezioneApp != null) sezioneApp.invalidate();
                if (riposo != null) riposo.rinfresca();
            }
        });
        calendario.aggiungiAscolto(new Calendario.Ascolto() {
            @Override public void impegniCambiati() {
                if (velaCalendario != null) velaCalendario.risveglia();
                if (riposo != null) riposo.rinfresca();
            }
        });

        // Il permesso prima della prima lettura, o la prima lettura direbbe
        // "manca il permesso" e resterebbe li' fino al riavvio.
        preparaIlCalendario(false);
        calendario.ascolta();
        agganciaAgendaDaAdb();
    }

    /**
     * Rimette in piedi quello che serve al calendario, e non chiede niente.
     *
     * Tre cose, tutte da device owner:
     *
     * <ol>
     *   <li>{@code com.android.providers.calendar}, il magazzino senza
     *       interfaccia, che {@code tools/alleggerisci.ps1} aveva tolto.
     *       {@code enableSystemApp} e' l'unica strada: da adb, su questo
     *       Android, {@code pm install-existing} non esiste e {@code pm
     *       install} di un'app di sistema fallisce;
     *   <li>{@code com.google.android.syncadapters.calendar}, che lo riempie
     *       con quello che c'e' sull'account;
     *   <li>il permesso di leggerlo.
     * </ol>
     *
     * <b>L'app del calendario non si rimette.</b> Quella e'
     * {@code com.android.calendar} e resta disinstallata: l'interfaccia la
     * disegna Casa.
     *
     * @param dillo se scrivere nel registro anche quando non c'era niente da
     *              fare - lo vuole chi ha premuto il tasto, non l'avvio.
     */
    private void preparaIlCalendario(boolean dillo) {
        if (!siamoDeviceOwner()) {
            if (dillo) Log.i(TAG, "calendario: senza device owner non posso rimettere niente");
            return;
        }
        for (String pacchetto : new String[] {
                "com.android.providers.calendar",
                "com.google.android.syncadapters.calendar" }) {
            try {
                dpm.enableSystemApp(admin, pacchetto);
                Log.i(TAG, "calendario: " + pacchetto + " rimesso");
            } catch (Exception e) {
                // Gia' installato, oppure su questo ROM non c'e' proprio: in
                // tutti e due i casi non c'e' niente da fare qui.
                if (dillo) Log.i(TAG, "calendario: " + pacchetto + " non rimesso (" + e + ")");
            }
        }
        try {
            dpm.setPermissionGrantState(admin, getPackageName(),
                    Manifest.permission.READ_CALENDAR,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        } catch (Exception e) {
            Log.w(TAG, "calendario: permesso non concesso da solo", e);
        }
        // Il provider ci mette un momento a nascere: si rilegge dopo.
        if (calendario != null) {
            passoMicrofono.postDelayed(new Runnable() {
                @Override public void run() { if (calendario != null) calendario.aggiorna(); }
            }, 2500);
        }
    }

    /**
     * Il calendario, a pagina intera.
     *
     * Sopra il telaio come tutte le vele, e non in un'altra Activity: con il
     * lock task attivo una seconda Activity vorrebbe entrare nel task bloccato,
     * ed e' una scommessa inutile quando Casa e' gia' in primo piano.
     */
    private void apriIlCalendario() {
        if (telaio == null || velaCalendario != null) return;
        velaCalendario = new VelaCalendario(this, misure, telaio.vetro());
        velaCalendario.setCalendario(calendario);
        velaCalendario.setRegia(new VelaCalendario.Regia() {
            @Override public void chiudiCalendario() {
                velaCalendario = null;
                if (telaio != null) telaio.nascondiVela();
            }
        });
        telaio.mostra(velaCalendario);
        velaCalendario.suEntrata();
    }

    /** Le impostazioni di Casa, a pagina intera: la notte e il riposo. */
    private void apriLePreferenze() {
        if (telaio == null || velaPreferenze != null || notte == null || riposo == null) return;
        velaPreferenze = new VelaPreferenze(this, misure, telaio.vetro(), notte, riposo);
        velaPreferenze.setRegia(new VelaPreferenze.Regia() {
            @Override public void chiudiPreferenze() {
                velaPreferenze = null;
                if (telaio != null) telaio.nascondiVela();
            }
        });
        telaio.mostra(velaPreferenze);
        velaPreferenze.suEntrata();
    }

    /** La To-Do List, a pagina intera. */
    private void apriLaLista() {
        if (telaio == null || velaToDo != null) return;
        velaToDo = new VelaToDo(this, misure, telaio.vetro());
        velaToDo.setAppunti(appunti);
        velaToDo.setRegia(new VelaToDo.Regia() {
            @Override public void chiudiToDo() {
                velaToDo = null;
                if (telaio != null) telaio.nascondiVela();
            }
            @Override public void dettaUnaNota(String lista) { dettaturaNota(lista); }
        });
        telaio.mostra(velaToDo);
        velaToDo.suEntrata();
    }

    /**
     * Il microfono, ma per una nota.
     *
     * La stessa catena di « premi e parla », con una differenza sola: quello
     * che si sente non passa dai comandi, diventa una riga della lista. Chi ha
     * premuto « detta » nell'agenda ha gia' detto cosa vuole, e fargli
     * ripetere « prendi nota » davanti a ogni frase sarebbe una parola in piu'
     * per niente.
     */
    private void dettaturaNota(String lista) {
        if (voce == null) {
            Log.i(TAG, "nota: il microfono non e' pronto");
            return;
        }
        perLaNota = true;
        listaDellaNota = Appunti.quale(lista);
        if (voce.staAscoltando()) return;
        if (risveglio == null || !risveglio.acceso()) {
            voce.ascoltaUnaVolta();
            return;
        }
        risveglio.cedi();
        passoMicrofono.removeCallbacksAndMessages(null);
        passoMicrofono.postDelayed(new Runnable() {
            @Override public void run() {
                if (voce != null && !voce.staAscoltando()) voce.ascoltaUnaVolta();
            }
        }, PASSO_MICROFONO_MS);
    }

    /** Se la prossima frase riconosciuta e' una nota invece di un comando. */
    private boolean perLaNota;

    /** E in quale lista va: quella della linguetta da cui si e' premuto
     *  « detta ». */
    private String listaDellaNota = Appunti.COSE;

    /**
     * L'agenda da adb:
     *
     *     adb shell am broadcast -a dev.casa.AGENDA --es cosa stato
     *     adb shell am broadcast -a dev.casa.AGENDA --es cosa prepara
     *     adb shell am broadcast -a dev.casa.AGENDA --es cosa nota --es testo 'chiamare l idraulico'
     *     adb shell am broadcast -a dev.casa.AGENDA --es cosa nota --es lista spesa --es testo 'latte'
     *     adb shell am broadcast -a dev.casa.AGENDA --es cosa rileggi
     */
    private void agganciaAgendaDaAdb() {
        if (agendaGia != null) return;
        agendaGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String cosa = i.getStringExtra("cosa");
                if ("prepara".equals(cosa)) { preparaIlCalendario(true); return; }
                if ("rileggi".equals(cosa)) { if (calendario != null) calendario.aggiorna(); return; }
                if ("nota".equals(cosa)) {
                    String testo = i.getStringExtra("testo");
                    if (appunti != null && testo != null) {
                        appunti.aggiungi(testo, false, i.getStringExtra("lista"));
                    }
                    return;
                }
                Log.i(TAG, "agenda: "
                        + (calendario != null ? calendario.stato() : "senza calendario")
                        + " - " + (appunti != null ? appunti.stato() : "senza note"));
            }
        };
        registerReceiver(agendaGia, new IntentFilter("dev.casa.AGENDA"));
    }

    /**
     * Collega il riposo.
     *
     * Qui c'e' solo la risposta alla domanda « c'e' qualcosa in corso? », che
     * e' l'unica cosa che il riposo non puo' sapere da se': la radio la sa
     * {@link Comandi}, la musica la {@link Musica}, i timer l'{@link Orologio},
     * e quello che suona nelle altre app la {@link Riproduzione}. Nessuno di
     * loro conosce gli altri, ed e' giusto cosi' - la regia li vede tutti, e
     * questa e' la regia.
     */
    private void agganciaRiposo() {
        riposo = new Riposo(this, misure, telaio, new Riposo.Faccende() {
            @Override public boolean inCorso() { return qualcosaInCorso(); }
        });
        riposo.setMeteo(meteo);
        riposo.setNotizie(notizie);
        riposo.setAgenda(appunti, calendario);
        // La notte guarda il riposo e basta: compare, trenta secondi, e se e'
        // sera lo schermo scende; se ne va, e la luce torna.
        notte = new Notte(this);
        riposo.setPresenza(new Riposo.Presenza() {
            @Override public void comparso() { if (notte != null) notte.riposoComparso(); }
            @Override public void andato() { if (notte != null) notte.riposoAndato(); }
        });
        // Il ricevitore di prova sta qui e non in avviaAssistente(), che si
        // ferma ad aspettare il permesso del microfono: il riposo con il
        // microfono non c'entra niente, e su un tablet senza permessi
        // dev'essere comunque provabile dal PC.
        agganciaRiposoDaAdb();
    }

    /**
     * C'e' qualcosa che vale la pena di continuare a vedere?
     *
     * <b>Una vela aperta conta.</b> Non e' solo la sveglia che suona: sono
     * anche il meteo a pagina intera e le impostazioni, cioe' schermate che
     * qualcuno ha aperto apposta e che un riposo sopra farebbe sparire senza
     * che nessuno l'abbia chiesto.
     *
     * <b>Il microfono aperto conta.</b> Sono al massimo pochi secondi, ma sono
     * i secondi in cui una persona sta parlando al tablet, ed e' il momento
     * peggiore in assoluto per coprirlo con una fotografia.
     */
    private boolean qualcosaInCorso() {
        if (telaio == null || telaio.ceUnaVela()) return true;
        if (comandi != null && comandi.radioAccesa()) return true;
        if (musica != null && musica.staSuonando()) return true;
        if (riproduzione != null && riproduzione.staSuonando()) return true;
        if (orologio != null && (orologio.qualcheTimer() || orologio.staSuonando())) return true;
        if (voce != null && voce.staAscoltando()) return true;
        return false;
    }

    /**
     * Il riposo, da adb:
     *
     *     adb shell am broadcast -a dev.casa.RIPOSO --es cosa adesso
     *     adb shell am broadcast -a dev.casa.RIPOSO --es cosa via
     *     adb shell am broadcast -a dev.casa.RIPOSO --es cosa foto
     *     adb shell am broadcast -a dev.casa.RIPOSO --es cosa stato
     *
     * Serve perche' il riposo <b>si fa aspettare</b>: provarlo come lo vede
     * chi ci abita vorrebbe dire stare cinque minuti senza toccare lo schermo,
     * e poi altri tre per vedere la seconda fotografia. Con questo si guarda
     * subito, e {@code stato} dice quante foto ci sono e da dove vengono senza
     * doverle andare a cercare sul disco.
     */
    private BroadcastReceiver vetrinaGia;

    /**
     * La vetrina, da adb e <b>solo sull'emulatore</b>:
     *
     *     adb shell am broadcast -a dev.casa.VETRINA
     *
     * Mette un brano e sei playlist finti nella sezione Spotify, per
     * fotografarla dove go-librespot non gira (vedi {@link Musica#vetrina}).
     * Sul tablet il ricevitore non si registra nemmeno: li' c'e' la musica
     * vera, e un broadcast che la sostituisce con una finta non deve esistere.
     */
    private void agganciaVetrina() {
        if (vetrinaGia != null || !emulatore()) return;
        vetrinaGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if ("luci".equals(i.getStringExtra("cosa"))) { luciInVetrina(); return; }
                if (musica == null || preferiti == null) return;
                // Le copertine le serve il PC (adb reverse tcp:8099): un
                // sito d'immagini in https qui non passa, Android 7 non ne
                // conosce il certificato.
                String img = "http://127.0.0.1:8099/";
                musica.vetrina("Stanze vuote", "Marea Lenta", "Il cassetto",
                        img + "disco.jpg", 214000L, 83000L);
                preferiti.vetrina(new Preferiti.Voce[] {
                    new Preferiti.Voce("spotify:vetrina:1", "Brani che ti piacciono", null, false),
                    new Preferiti.Voce("spotify:vetrina:2", "Sabato mattina", img + "sabato.jpg"),
                    new Preferiti.Voce("spotify:vetrina:3", "In cucina", img + "cucina.jpg"),
                    new Preferiti.Voce("spotify:vetrina:4", "Concentrazione", img + "studio.jpg"),
                    new Preferiti.Voce("spotify:vetrina:5", "Giorni di pioggia", img + "pioggia.jpg"),
                    new Preferiti.Voce("spotify:vetrina:6", "Anni novanta", img + "novanta.jpg"),
                });
                Log.i(TAG, "vetrina: musica finta in scena");
            }
        };
        registerReceiver(vetrinaGia, new IntentFilter("dev.casa.VETRINA"));
    }

    /** Le lampade come risponderebbero a casa: dall'emulatore non si
     *  raggiungono, e la Home le disegnerebbe tutte « non risponde ». La prima
     *  accesa, le altre spente; al prossimo giro di Luci tornano vere. */
    private void luciInVetrina() {
        if (luci == null) return;
        java.util.List<Lampada> elenco = luci.elenco();
        for (int k = 0; k < elenco.size(); k++) {
            Tuya.Stato st = new Tuya.Stato();
            st.raggiunta = true;
            st.accesa = k == 0;
            st.luminosita = 80;
            st.haLuminosita = true;
            elenco.get(k).stato = st;
            elenco.get(k).inCorso = false;
        }
        home.luciCambiate();
        sezioneLuci.risveglia();
        aggiornaSpie();
    }

    private static boolean emulatore() {
        return android.os.Build.FINGERPRINT.startsWith("generic")
                || "goldfish".equals(android.os.Build.HARDWARE)
                || "ranchu".equals(android.os.Build.HARDWARE);
    }

    private void agganciaRiposoDaAdb() {
        if (riposoGia != null) return;
        riposoGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (riposo == null) return;
                String cosa = i.getStringExtra("cosa");
                if ("adesso".equals(cosa)) { riposo.subito(); return; }
                if ("via".equals(cosa)) { telaio.nascondiVela(); return; }
                if ("foto".equals(cosa)) { riposo.aggiornaFoto(); return; }
                Log.i(TAG, "riposo: " + riposo.stato()
                        + (notte != null ? " - notte " + notte.stato() : ""));
            }
        };
        registerReceiver(riposoGia, new IntentFilter("dev.casa.RIPOSO"));
    }

    /**
     * La radio, quando c'e' gia'.
     *
     * Non e' un campo perche' la radio e' di {@link Comandi} - una sola, la
     * stessa che comanda la voce - e questa e' solo la strada per arrivarci.
     * Il null conta: la vetrina si compone anche prima che i comandi esistano,
     * e in quel momento delle stazioni non si sa ancora niente.
     */
    private Radio laRadio() { return comandi != null ? comandi.radio() : null; }

    /** Il risveglio delle lampade, ritardato. Un Handler suo per poterlo
     *  togliere in onDestroy: se Casa se ne va prima, non ha senso partire. */
    private final android.os.Handler risveglioLuci = new android.os.Handler();

    /**
     * La schermata di quando suona: sopra tutto, barra compresa.
     *
     * Se e' un timer, Casa lo dice anche a voce - e' l'unica cosa che parla
     * senza che le sia stato parlato, perche' e' un avviso e chi l'ha messo
     * probabilmente non sta guardando lo schermo.
     */
    private void mostraAllarme(String titolo, final boolean sveglia) {
        if (telaio == null) return;
        telaio.mostra(new VelaAllarme(this, misure, telaio.vetro(), titolo, sveglia,
                new VelaAllarme.Scelta() {
                    @Override public void basta() { orologio.taci(); }
                    @Override public void rimanda() { orologio.rimanda(); }
                }));
        if (!sveglia && voce != null) voce.di("Il tempo e finito.");
    }

    /**
     * Niente schermata di blocco.
     *
     * Un apparecchio appeso a un muro non si sblocca: la schermata di blocco e'
     * solo un velo fra chi passa e l'orologio. Toglierla elimina in un colpo
     * anche il ritardo all'accensione dello schermo e il problema della sveglia
     * che dovrebbe comparirci sopra.
     */
    /**
     * Nell'area notifiche non arriva niente: ne' icone, ne' tendina, ne'
     * finestrelle, ne' suoni.
     *
     * E' una regola di chi abita qui, nata dalle storie di Discover che l'app
     * Google spinge da sola - e l'app Google non si toglie, perche' e' il
     * riconoscimento vocale di Casa. Quindi la regola la tiene Casa, per tutte
     * le app: {@code setStatusBarDisabled} chiude l'area, e su Android 7 lo
     * stesso segnale (DISABLE_NOTIFICATION_ALERTS) spegne anche suoni e
     * vibrazioni delle notifiche. Dietro l'area chiusa, {@link AscoltoNotifiche}
     * cancella quelle che si possono cancellare, perche' non si accumulino.
     *
     * Si rifa' a ogni avvio, anche se il sistema se lo ricorderebbe: un
     * ripristino o un device owner rifatto non devono riaprirla in silenzio.
     *
     * Per una manutenzione - le impostazioni rapide, il Wi-Fi dalla tendina:
     *
     *     adb shell settings put global casa_area_notifiche 1
     *
     * e si riavvia Casa; con 0, o togliendo la voce, si richiude.
     */
    private void chiudiLAreaNotifiche() {
        boolean aperta = Settings.Global.getInt(getContentResolver(), "casa_area_notifiche", 0) == 1;
        try {
            boolean fatto = dpm.setStatusBarDisabled(admin, !aperta);
            Log.i(TAG, "area notifiche " + (aperta ? "aperta per manutenzione" : "chiusa")
                    + (fatto ? "" : ": il sistema ha detto di no"));
        } catch (Exception e) {
            Log.w(TAG, "area notifiche: non ho potuto chiuderla", e);
        }
    }

    private void spegniIlBlocco() {
        try {
            dpm.setKeyguardDisabled(admin, true);
        } catch (Exception e) {
            Log.w(TAG, "blocco non disattivato", e);
        }
    }

    /**
     * Rimette il fuso orario.
     *
     * <b>Questo tablet nasce a Pechino.</b> Il ROM ha
     * {@code persist.sys.timezone = Asia/Shanghai}, non c'e' una SIM che
     * corregga il fuso dalla rete, e {@code auto_time_zone} resta acceso a
     * chiedere a un operatore che non c'e': il risultato e' un orologio che
     * segna sei ore avanti su un muro italiano. L'istante e' giusto - lo mette
     * a posto NTP - e' <i>il fuso</i> a essere sbagliato, che e' peggio,
     * perche' sembra che sia l'orologio a non funzionare.
     *
     * Da {@code adb} non si aggiusta: {@code setprop persist.sys.timezone}
     * vuole i permessi di sistema e la shell non li ha. Da qui si', perche' su
     * API 24 {@code SET_TIME_ZONE} ha protezione <i>normal</i> e arriva
     * all'installazione.
     *
     * Si rifa' a ogni avvio e non una volta sola: e' un {@code setTimeZone} che
     * costa niente quando il fuso e' gia' quello, e cosi' un ripristino di
     * fabbrica o un aggiornamento del ROM non riportano l'orologio a Pechino
     * senza che nessuno se ne accorga.
     */
    private void rimettiIlFuso() {
        try {
            if (FUSO.equals(java.util.TimeZone.getDefault().getID())) return;
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) am.setTimeZone(FUSO);
            Log.i(TAG, "fuso orario portato a " + FUSO);
        } catch (Throwable t) {
            // Senza il permesso o su un ROM che non lo concede si tira dritto:
            // un orologio con l'ora di Pechino e' un fastidio, non un motivo
            // per non far partire la Home.
            Log.w(TAG, "fuso orario non impostato", t);
        }
    }

    /** Il fuso di questa casa. Non e' una preferenza: e' dove sta il tablet. */
    private static final String FUSO = "Europe/Rome";

    /**
     * Accende orecchio e bocca.
     *
     * Il permesso del microfono su API 24 andrebbe chiesto con una finestra.
     * Da device owner si concede da soli: su un apparecchio da muro non c'e'
     * nessuno che tocchi "Consenti", e la finestra resterebbe li' per sempre.
     */
    private void avviaAssistente() {
        if (siamoDeviceOwner()) {
            try {
                dpm.setPermissionGrantState(admin, getPackageName(),
                        Manifest.permission.RECORD_AUDIO,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            } catch (Exception e) {
                Log.w(TAG, "microfono non concesso da solo", e);
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 1);
            return;
        }

        sordina = new Sordina(this);

        voce = new Voce(this, new Voce.Ascoltatore() {
            @Override public void suComando(String frase) {
                home.setInAscolto(false);
                home.setStato("“" + frase + "”");
                if (perLaNota) {
                    // Chi ha premuto « detta » nell'agenda voleva una nota, non
                    // un comando: la frase non passa nemmeno da Comandi, o
                    // « accendi la luce » finirebbe ad accendere la luce invece
                    // che in lista.
                    perLaNota = false;
                    if (velaToDo != null) velaToDo.risveglia();
                    // Nella spesa « latte uova e pane » sono tre righe, come
                    // quando lo si dice a Casa senza aprire la lista.
                    boolean spesa = Appunti.SPESA.equals(listaDellaNota);
                    boolean presa = appunti != null && (spesa
                            ? appunti.aggiungiSpesa(frase, true) > 0
                            : appunti.aggiungi(frase, true) != null);
                    if (presa && voce != null) {
                        String chiave = spesa ? "spesa.presa" : "nota.presa";
                        voce.di(comandi != null ? comandi.risposte().di(chiave) : "Segnato.");
                    }
                    return;
                }
                comandi.esegui(frase);
            }
            @Override public void suStato(String stato) {
                home.setStato(stato);
                home.setInAscolto(voce != null && voce.staAscoltando());
            }
            @Override public void suFine() {
                // Se non e' arrivato niente, la prossima frase torna a essere un
                // comando: una modalita' che resta accesa dopo un silenzio e' il
                // modo in cui « accendi la luce » finisce fra le note domani.
                perLaNota = false;
                // Il riconoscitore ha mollato il microfono: se la parola di
                // attivazione e' accesa, se lo riprende lei. Senza questa
                // riga Casa risponderebbe una volta sola dopo ogni riavvio.
                if (risveglio != null) risveglio.riprendi();
            }
            @Override public void suBocca(boolean parla) {
                // Mentre Casa parla non si cerca la parola: la sua voce esce
                // da venti centimetri sotto il microfono, ed e' il falso
                // aggancio piu' facile che ci sia.
                if (risveglio != null) risveglio.pausa(parla);
            }
        }, sordina);

        comandi = new Comandi(this, voce, new Comandi.Schermo() {
            @Override public void suRadio(String cosa) {
                home.setRadio(cosa);
                home.invalidate();
                sezioneRadio.risveglia();
                aggiornaSpie();
            }
            @Override public void suMessaggio(String testo) {
                mostraPerUnAttimo(testo);
            }
        }, orologio, luci);

        // Adesso che la radio esiste, la sordina sa cosa abbassare quando si
        // apre il microfono. Senza, con la radio accesa il riconoscitore
        // sentiva la radio e non chi parlava.
        sordina.aggiungi(comandi.radio());
        // La musica di Spotify adesso e' roba di Casa, non di un'altra app: il
        // focus audio non la tocca piu', quindi va abbassata direttamente come
        // la radio. Senza questa riga, premere il microfono con la musica
        // accesa vorrebbe dire parlare sopra la musica - che e' esattamente il
        // problema che la Sordina esiste per risolvere.
        sordina.aggiungi(musica);

        // Le stazioni le scrive il PC: si leggono PRIMA di consegnare la radio
        // alla sezione, perche' quella si misura sul numero di stazioni e con
        // l'elenco ancora vuoto disegnerebbe una griglia senza tessere.
        comandi.radio().rileggi(this);

        // La sezione comanda LA STESSA radio della voce, non una seconda: due
        // lettori sullo stesso altoparlante suonerebbero insieme, e "spegni la
        // radio" ne spegnerebbe uno solo.
        sezioneRadio.setRadio(comandi.radio());
        home.setSorgente(comandi.radio());

        // La sessione media rende la radio comandabile dai tasti media e dalla
        // riga "ora in riproduzione", con lo stesso codice che comanda Spotify.
        comandi.radio().pubblica(this);

        // Radio e musica escono dallo stesso altoparlante: accenderne una
        // spegne l'altra. Il legame si chiude qui, dove esistono tutte e due.
        comandi.radio().setMusica(musica);
        musica.setRadio(comandi.radio());
        comandi.setMusica(musica, preferiti, ricerca);

        // NIENTE ascolto continuo con il riconoscitore di Google: suona un tono
        // a ogni startListening, e manderebbe ai suoi server tutto il parlato
        // della stanza invece dei soli comandi. Chi ascolta sempre e' invece
        // il Risveglio qui sotto, che sta tutto a bordo e non manda niente a
        // nessuno: apre il riconoscitore solo dopo che « Hey Home » ha
        // agganciato. Il microfono resta comunque premibile, e resta l'unica
        // strada finche' non ci sono campioni.
        home.setBottone(new SezioneHome.Bottone() {
            @Override public void suMicrofono() { parla(); }
            @Override public void suAnnulla() { smettiDiAscoltare(); }
        });
        home.setStato(null);

        // I comandi scritti dal PC. Si caricano qui e si ricaricano quando
        // arriva una configurazione nuova: sono l'unica parte di quello che
        // Casa capisce che non sta nel codice.
        comandiUtente = new ComandiUtente();
        comandiUtente.carica(this);
        comandi.setUtente(comandiUtente);
        comandi.setAgenda(appunti, calendario);
        comandi.caricaRisposte(this);

        // Come parla, se qualcuno l'ha scelto dal PC. Senza dire niente: una
        // casa che si presenta da sola a ogni accensione stanca alla terza
        // volta.
        applicaComeParla();

        // Adesso che Comandi esiste, le routine sanno fare anche quello che
        // non e' una lampada: un passo « frase » entra da qui, cioe' dalla
        // stessa porta da cui entra una persona che parla.
        if (luci != null) {
            luci.setInterprete(new Luci.Interprete() {
                @Override public void frase(String testo) {
                    if (comandi == null) return;
                    Log.i(TAG, "routine: " + testo);
                    comandi.esegui(testo);
                }
            });
        }

        agganciaProva();
        agganciaParola();
        agganciaConfigurazione();
        if (PAROLA_DI_ATTIVAZIONE) avviaParolaDiAttivazione();
    }

    /**
     * Chiude l'ascolto perche' l'ha chiesto chi ha premuto « annulla ».
     *
     * Silenziosa di proposito: chi annulla ha gia' deciso, e sentirsi
     * rispondere qualcosa dopo aver detto di lasciar perdere e' fastidioso.
     * L'unica cosa che deve succedere e' che la pastiglia torni a dire
     * « parla ».
     */
    private void smettiDiAscoltare() {
        if (voce != null) voce.annulla();
        if (home != null) home.setInAscolto(false);
        // Se la parola di attivazione fosse accesa, il microfono torna a lei.
        if (risveglio != null && risveglio.acceso()) risveglio.riprendi();
    }

    /**
     * Prova dei comandi senza parlare:
     *
     *     adb shell am broadcast -a dev.casa.DI --es frase 'che ore sono'
     *
     * Serve in sviluppo, perche' il riconoscimento vocale vuole una voce vera
     * in una stanza vera e non si automatizza. Salta il riconoscitore ed entra
     * dritto in Comandi, quindi prova tutto il resto della catena: capire,
     * eseguire, rispondere a voce, aggiornare lo schermo.
     *
     * Da PowerShell servono gli apici SINGOLI attorno alla frase: con le
     * doppie, am spezza la frase e ne prende un pezzo come nome di pacchetto.
     */
    private void agganciaProva() {
        if (provaGia != null) return;
        provaGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String frase = i.getStringExtra("frase");
                if (frase == null || comandi == null) return;
                Log.i(TAG, "prova: " + frase);
                home.setStato("“" + frase + "”");
                comandi.esegui(frase);
            }
        };
        registerReceiver(provaGia, new IntentFilter("dev.casa.DI"));
    }

    /**
     * La configurazione mandata dal PC:
     *
     *     adb push casa.json /sdcard/Android/data/dev.casa/files/da-mettere.json
     *     adb shell am broadcast -a dev.casa.CONFIG --es cosa prendi
     *
     *     adb shell am broadcast -a dev.casa.CONFIG --es cosa vetrina
     *     adb shell am broadcast -a dev.casa.CONFIG --es cosa routine --es nome Cinema
     *
     * <b>Perche' un file piu' un broadcast e non il file da solo.</b> Il file
     * lo si potrebbe leggere a ogni entrata nella sezione, e sarebbe meno
     * codice. Ma allora il PC non saprebbe mai <b>quando</b> il tablet ha
     * capito: manderebbe, e poi si dovrebbe alzare per andare a guardare. Il
     * broadcast torna solo quando il ricevitore ha finito, quindi Gestione
     * Home puo' dire « fatto » sapendolo, e la riga nel registro dice che cosa
     * e' entrato.
     *
     * <b>Perche' « prova una routine » sta qui e non e' un pulsante sul PC.</b>
     * Una routine si scrive alla cieca, e l'unico modo di sapere se
     * « Buonanotte » fa la cosa giusta e' vederla succedere. Sull'altro
     * progetto il PC parlava alle lampade per conto suo; qui no, e non e' una
     * mancanza: eseguirla sul tablet vuol dire provare <b>quella vera</b>,
     * compreso il passo che accende la radio, che il PC non saprebbe fare.
     * Una sola implementazione, quella che poi girera' davvero.
     */
    private void agganciaConfigurazione() {
        if (configGia != null) return;
        configGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String cosa = i.getStringExtra("cosa");
                if (cosa == null) return;

                if ("prendi".equals(cosa)) {
                    if (!Configurazione.importa(MainActivity.this)) {
                        Log.i(TAG, "PC config: niente da prendere");
                        return;
                    }
                    if (luci != null) luci.rileggi();
                    if (comandiUtente != null) comandiUtente.carica(MainActivity.this);
                    if (comandi != null) comandi.caricaRisposte(MainActivity.this);
                    applicaComeParla();
                    // Quanto aspettare, ogni quanto cambiare foto, e se andarle
                    // a prendere: tre numeri che si cambiano dal PC e che
                    // devono valere subito, non al prossimo avvio.
                    if (riposo != null) riposo.applica(Configurazione.leggi(MainActivity.this));
                    if (notte != null) notte.applica(Configurazione.leggi(MainActivity.this));
                    // Il posto, lo sport e la squadra: anche questi valgono
                    // subito, e le linguette della pagina cambiano da sole.
                    if (notizie != null) notizie.applica(Configurazione.leggi(MainActivity.this));
                    // E la sezione Casa si rifa' le tessere: rileggere l'elenco
                    // non basta, perche' le aree premibili sono misurate a
                    // parte e restavano quante erano prima. Una routine o una
                    // lampada in piu' non si vedeva fino al riavvio.
                    if (sezioneLuci != null) sezioneLuci.ricomponi();
                    // E le stazioni, che dal PC si aggiungono e si tolgono come
                    // le lampade. Anche qui la griglia va rimisurata: le
                    // tessere sono tante quante le stazioni.
                    if (comandi != null) {
                        comandi.radio().rileggi(MainActivity.this);
                        if (sezioneRadio != null) sezioneRadio.ricomponi();
                    }
                    // La sezione App rilegge da se' a ogni entrata, ma se e'
                    // gia' sullo schermo nessuno ci entra piu': una tessera
                    // tolta resterebbe li' fino al prossimo giro di sezioni.
                    if (sezioneApp != null) sezioneApp.suEntrata();
                    Configurazione.rispecchia(MainActivity.this,
                            Configurazione.effettiva(MainActivity.this, luci, comandi, laRadio()));
                    stampaConfig();
                    return;
                }

                if ("vetrina".equals(cosa)) {
                    Configurazione.rispecchia(MainActivity.this,
                            Configurazione.effettiva(MainActivity.this, luci, comandi, laRadio()));
                    stampaConfig();
                    return;
                }

                if ("routine".equals(cosa)) {
                    String nome = i.getStringExtra("nome");
                    if (nome == null || luci == null) return;
                    for (Routine r : luci.routine()) {
                        if (r.nome.equalsIgnoreCase(nome.trim())) {
                            Log.i(TAG, "PC routine " + r.nome + ": " + r.passi.size() + " passi");
                            telaio.vaiA(LUCI);
                            luci.esegui(r);
                            return;
                        }
                    }
                    Log.i(TAG, "PC routine \"" + nome + "\": non ce n'e' nessuna con questo nome");
                    return;
                }

                if ("luci".equals(cosa)) {
                    if (luci == null) return;
                    luci.aggiorna();
                    luci.scopri();
                    Log.i(TAG, "PC luci: rilette");
                    return;
                }

                stampaConfig();
            }
        };
        registerReceiver(configGia, new IntentFilter("dev.casa.CONFIG"));
    }

    /** Rimette voce, ritmo e tono come li ha scelti chi usa il tablet. */
    private void applicaComeParla() {
        if (voce == null) return;
        org.json.JSONObject v = Configurazione.voce(this);
        if (v == null) return;
        voce.applica(v.optString("nome", ""),
                     (float) v.optDouble("ritmo", -1),
                     (float) v.optDouble("tono", -1));
        Log.i(TAG, "voce: rimessa come l'avevi scelta");
    }

    /** Mette per iscritto come parla, cosi' una reinstallazione non riporta la
     *  voce di serie: la scelta si fa a orecchio, e rifarla ogni volta e' il
     *  modo per non farla piu'. */
    private void salvaComeParla() {
        if (voce == null) return;
        Configurazione.salvaVoce(this, voce.voceScelta(), voce.ritmo(), voce.tono());
    }

    /** Una riga sola con quello che il PC vuole sapere dopo aver mandato. */
    private void stampaConfig() {
        StringBuilder b = new StringBuilder("PC config ");
        b.append(Configurazione.quante(Configurazione.effettiva(this, luci, comandi, laRadio())));
        if (comandiUtente != null) b.append(", ").append(comandiUtente.elenco().size()).append(" comandi");
        if (luci != null) {
            b.append(" accese=").append(luci.accese());
        }
        Log.i(TAG, b.toString());
    }

    // ---- la parola di attivazione -----------------------------------------

    /**
     * Apre il riconoscitore, da qualunque parte sia arrivata la richiesta: il
     * microfono premuto, oppure la parola detta.
     *
     * <b>L'ordine delle righe in mezzo non e' indifferente.</b> Su Android il
     * microfono e' di uno solo: finche' lo tiene l'orecchio della parola di
     * attivazione, il riconoscitore di Google parte e non sente niente - e non
     * da' nemmeno errore, scade e basta, che e' il modo peggiore di rompersi.
     * Quindi prima si cede, poi si ascolta. Se lo riprende {@code suFine}.
     */
    /**
     * Se Casa sta anche in ascolto della parola « Home », oltre che del tasto.
     *
     * <b>Spenta, e non e' una svista.</b> Il riconoscitore a bordo prende la
     * parola l'ottantatre per cento delle volte quando la si prova sul
     * dataset, ma in cucina, dal vivo, aggancia troppo di rado per essere
     * usabile: la storia intera - tre difetti trovati e corretti, e uno che
     * resta - sta in docs/parola-di-attivazione.md. Un assistente che va
     * chiamato due o tre volte e' peggio di uno che si preme.
     *
     * Il codice resta tutto - {@link Risveglio}, {@link Orecchio}, la rete
     * negli assets - perche' funziona e perche' il difetto che manca e'
     * identificato. Qui si spegne solo l'accensione automatica: rimetterla e'
     * cambiare questa riga.
     *
     * Da spenta il microfono non e' mai aperto se non quando lo si chiede, il
     * che vale anche mezzo watt e cinque megabyte di memoria in meno.
     */
    private static final boolean PAROLA_DI_ATTIVAZIONE = false;

    private void parla() {
        if (voce == null || voce.staAscoltando()) return;
        telaio.vaiA(HOME);
        home.setInAscolto(true);

        if (risveglio == null || !risveglio.acceso()) {
            voce.ascoltaUnaVolta();
            return;
        }
        risveglio.cedi();
        // E poi si aspetta un momento. Vedi PASSO_MICROFONO_MS.
        passoMicrofono.removeCallbacksAndMessages(null);
        passoMicrofono.postDelayed(new Runnable() {
            @Override public void run() {
                if (voce != null && !voce.staAscoltando()) voce.ascoltaUnaVolta();
            }
        }, PASSO_MICROFONO_MS);
    }

    /**
     * Quanto si aspetta fra il rilascio del microfono e l'apertura del
     * riconoscitore.
     *
     * <b>Misurato, non scelto.</b> Senza attesa, sul registro si leggeva:
     *
     * <pre>
     *   11:01:41.312  orecchio: spento
     *   11:01:41.510  riconoscimento, errore 3
     *   11:01:40.815  F libc: Fatal signal 11 (SIGSEGV) in tid readThread0
     * </pre>
     *
     * L'errore 3 e' {@code ERROR_AUDIO}, e il SIGSEGV e' del processo del
     * riconoscitore. Su questo MediaTek l'ingresso audio non e' pronto per un
     * secondo cliente nell'istante in cui il primo lo lascia: {@code
     * AudioRecord.release()} torna subito, ma il driver ci mette ancora
     * qualcosa a smontare la sessione. Il riconoscitore partiva su un ingresso
     * a meta' strada, non sentiva niente e moriva.
     *
     * Il sintomo, da fuori, era il peggiore possibile: la parola di
     * attivazione <b>agganciava</b> - il registro lo dice - e Casa non
     * rispondeva. Sembrava che non riconoscesse la parola, mentre il problema
     * stava tutto dopo.
     *
     * Trecentocinquanta millisecondi sono un ritardo che non si nota fra il
     * « din » e la scritta a schermo, e sono il doppio abbondante dei
     * centocinquanta in cui l'errore si presentava.
     */
    private static final long PASSO_MICROFONO_MS = 350;

    /** Il ritardo del passaggio del microfono. Un Handler suo, perche' non
     *  deve essere azzerato da chi cancella altri messaggi. */
    private final android.os.Handler passoMicrofono = new android.os.Handler();

    /**
     * Monta la parola di attivazione e, se e' accesa, la avvia.
     *
     * I modelli si leggono su un thread a parte - venti WAV e le loro impronte
     * sono qualche centinaio di millisecondi - perche' questo e' esattamente
     * il momento in cui l'orologio compare sullo schermo, cioe' quando si
     * guarda.
     */
    private void avviaParolaDiAttivazione() {
        campioni = new Campioni(this);
        risveglio = new Risveglio(this, campioni, sordina, new Risveglio.Sveglia() {
            @Override public void suParola() {
                parla();
            }
            @Override public void suLivello(float livello, boolean voce) {
                if (impostazioni != null) impostazioni.setLivello(livello, voce);
            }
            @Override public void suPunteggio(float punteggio, float soglia,
                                              int diFila, float distanza,
                                              boolean preso) {
                if (impostazioni != null) {
                    impostazioni.setPunteggio(punteggio, soglia, diFila, distanza, preso);
                }
            }
        });
        risveglio.carica(new Runnable() {
            @Override public void run() {
                if (risveglio == null) return;
                int quanti = risveglio.parola().quantiModelli();
                if (comandi != null) comandi.setParolaChiave(risveglio.frase());
                if (risveglio.stato().accesa) risveglio.accendi();
                Log.i(TAG, "parola di attivazione: " + quanti + " modelli, "
                        + (risveglio.acceso() ? "in ascolto" : "spenta"));
            }
        });
    }

    private void apriImpostazioni() {
        if (risveglio == null || telaio == null || impostazioni != null) return;
        impostazioni = new VelaImpostazioni(this, misure, telaio.vetro(), campioni, risveglio,
                new VelaImpostazioni.Uscita() {
                    @Override public void suChiudi() { telaio.nascondiVela(); }
                });
        // Il campo si azzera quando la vela esce di scena, comunque esca: il
        // telaio chiama suVelaTolta() anche per il tasto indietro. Senza,
        // riaprire le impostazioni dopo un indietro non funzionerebbe piu'.
        impostazioni.setSuChiusa(new Runnable() {
            @Override public void run() { impostazioni = null; }
        });
        telaio.mostra(impostazioni);
        impostazioni.suEntrata();
    }

    /**
     * Il governo della parola da adb, e quindi dal programma sul PC.
     *
     *     adb shell am broadcast -a dev.casa.PAROLA --es cosa stato
     *     adb shell am broadcast -a dev.casa.PAROLA --es cosa accendi
     *     adb shell am broadcast -a dev.casa.PAROLA --es cosa spegni
     *     adb shell am broadcast -a dev.casa.PAROLA --es cosa ricarica
     *     adb shell am broadcast -a dev.casa.PAROLA --es cosa tara
     *     adb shell am broadcast -a dev.casa.PAROLA --es cosa soglia --ef valore 3.1
     *
     * Passa da un broadcast e non da un file per la stessa ragione della
     * chiave di Spotify: nessun permesso da chiedere, e niente che resti in
     * giro dove lo vede chiunque.
     *
     * <b>Le risposte vanno nel registro</b>, sotto il tag {@code Casa} e con
     * il prefisso {@code PC}: un broadcast non ha un valore di ritorno, e
     * {@code logcat} e' il canale che c'e' gia' e che il PC sta gia' guardando.
     *
     * Il tag e' {@code Casa} e non {@code Casa.PC} perche' su questo ROM un tag
     * fuori lista non si vede: il MediaTek nasce con {@code log.tag = E}, e
     * passano solo i tag aperti a mano con {@code persist.log.tag.<nome>}. Con
     * un tag suo, il PC avrebbe chiesto lo stato e non avrebbe ricevuto mai
     * niente - senza un errore, senza un motivo visibile.
     */
    private void agganciaParola() {
        if (parolaGia != null) return;
        parolaGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String cosa = i.getStringExtra("cosa");
                if (cosa == null) return;

                // Questi valgono anche a parola di attivazione spenta, cioe'
                // quando il Risveglio non esiste proprio. Se uscissimo prima
                // per via del suo null, il PC resterebbe senza risposta a
                // « stato » e senza il modo di aprire il pannello - cioe'
                // Gestione Home smetterebbe di funzionare, e senza dire
                // perche'.
                if ("pannello".equals(cosa)) {
                    // Il pannello si apre solo da qui: sul tablet non c'e'
                    // nessun ingranaggio da premere. Registrare la parola deve
                    // pero' succedere davanti al tablet - il conto alla
                    // rovescia serve a chi parla, e il microfono e' quello -
                    // quindi dal PC lo si apre e basta, e chi si alza lo trova
                    // gia' li'.
                    telaio.vaiA(HOME);
                    apriImpostazioni();
                    stampaStato();
                    return;
                }
                if ("ritmo".equals(cosa)) {
                    float v = i.getFloatExtra("valore", -1f);
                    if (v > 0f && voce != null) voce.setRitmo(v);
                    salvaComeParla();
                    stampaStato();
                    return;
                }
                if ("tono".equals(cosa)) {
                    float v = i.getFloatExtra("valore", -1f);
                    if (v > 0f && voce != null) voce.setTono(v);
                    salvaComeParla();
                    stampaStato();
                    return;
                }
                if ("voce".equals(cosa)) {
                    String nome = i.getStringExtra("nome");
                    if (voce != null) voce.setVoce(nome);
                    salvaComeParla();
                    return;
                }
                if ("voci".equals(cosa)) {
                    if (voce != null) voce.elencaVoci();
                    return;
                }
                if ("stato".equals(cosa)) { stampaStato(); return; }

                if (risveglio == null || campioni == null) {
                    Log.i(TAG, "PC " + cosa + ": la parola di attivazione e' spenta");
                    return;
                }
                Campioni.Stato s = risveglio.stato();
                if ("accendi".equals(cosa) || "spegni".equals(cosa)) {
                    s.accesa = "accendi".equals(cosa);
                    risveglio.applica(s);
                } else if ("soglia".equals(cosa)) {
                    // Le due soglie vanno in versi opposti e stanno in due
                    // campi diversi: si tocca quella del rilevatore in
                    // funzione, o si girerebbe una manopola scollegata.
                    float v = i.getFloatExtra("valore", -1f);
                    if (v >= 0f) {
                        if (risveglio.conLaRete()) s.sogliaRete = Math.min(1f, v);
                        else s.soglia = v;
                        risveglio.applica(s);
                    }
                } else if ("impronte".equals(cosa)) {
                    // La soglia del SECONDO stadio, quello che confronta con
                    // le registrazioni di casa. E' una distanza, quindi piu'
                    // bassa e' piu' esigente - il contrario di « soglia », che
                    // e' la somiglianza vista dalla rete. Due manopole con due
                    // versi opposti hanno bisogno di due nomi.
                    float v = i.getFloatExtra("valore", -1f);
                    if (v > 0f) { s.soglia = v; risveglio.applica(s); }
                } else if ("difila".equals(cosa)) {
                    s.difila = Math.max(1, Math.min(8, i.getIntExtra("valore", s.difila)));
                    risveglio.applica(s);
                } else if ("prova".equals(cosa)) {
                    // Accende o spegne la prova da fuori: cosi' si puo' stare
                    // davanti al tablet a dire la parola mentre i punteggi si
                    // leggono dal PC.
                    boolean acceso = i.getBooleanExtra("valore", !risveglio.inProva());
                    risveglio.setProva(acceso);
                    risveglio.orecchio().pausa(false);
                    if (acceso) risveglio.accendiPerRegistrare();
                    risveglio.orecchio().pausa(!acceso && !s.accesa);
                    Log.i(TAG, "PC prova " + (acceso ? "accesa" : "spenta"));
                } else if ("eco".equals(cosa)) {
                    s.eco = i.getBooleanExtra("valore", !s.eco);
                    risveglio.applica(s);
                } else if ("ricarica".equals(cosa)) {
                    // Dopo che il PC ha spinto dei campioni nuovi: senza,
                    // resterebbero sul disco e fuori dai modelli.
                    risveglio.carica(new Runnable() {
                        @Override public void run() { stampaStato(); }
                    });
                    return;
                } else if ("tara".equals(cosa)) {
                    tara();
                    return;
                } else if ("registra".equals(cosa)) {
                    // Registra un campione da fuori. Serve alle misure che
                    // vogliono due registrazioni nelle stesse condizioni - la
                    // cancellazione d'eco, per esempio - dove premere un tasto
                    // due volte non e' "le stesse condizioni".
                    String tipo = i.getStringExtra("tipo");
                    final int quale = "si".equals(tipo) ? Campioni.SI : Campioni.NO;
                    int durata = i.getIntExtra("ms", quale == Campioni.SI ? 1600 : 6000);
                    final String etichetta = i.getStringExtra("etichetta");
                    Log.i(TAG, "PC registro " + (quale == Campioni.SI ? "si" : "no")
                            + " per " + durata + " ms"
                            + (etichetta != null ? " (" + etichetta + ")" : ""));
                    risveglio.registraCampione(quale, durata, etichetta,
                            new Risveglio.Presa() {
                                @Override public void suFinita(java.io.File dove, int ms,
                                                              String guaio) {
                                    if (guaio != null) {
                                        Log.i(TAG, "PC registrato guaio=" + guaio);
                                    } else {
                                        Log.i(TAG, "PC registrato file=" + dove.getName()
                                                + " ms=" + ms);
                                    }
                                    if (risveglio != null) risveglio.carica(null);
                                }
                            });
                    return;

                }
                stampaStato();
            }
        };
        registerReceiver(parolaGia, new IntentFilter("dev.casa.PAROLA"));
    }

    /** La taratura chiesta dal PC. Su un thread suo: sono secondi, non
     *  millisecondi, e qui c'e' un orologio che deve continuare a battere. */
    private void tara() {
        new Thread(new Runnable() {
            @Override public void run() {
                if (risveglio == null) return;
                ParolaChiave.Taratura t = risveglio.tara();
                Log.i(TAG, "PC taratura si=" + t.quantiSi + " no=" + t.quantiNo
                        + " presi=" + t.presi + " falsi=" + t.falsi
                        + " margine=" + ParolaChiave.arrotonda(t.margine)
                        + " soglia=" + ParolaChiave.arrotonda(t.sogliaProposta));
                Log.i(TAG, "PC taratura detto " + t.racconto);
                final float proposta = t.sogliaProposta;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (risveglio == null) return;
                        Campioni.Stato s = risveglio.stato();
                        s.soglia = proposta;
                        risveglio.applica(s);
                        stampaStato();
                    }
                });
            }
        }, "Casa-taratura-pc").start();
    }

    /** Lo stato della parola, in una riga sola che il PC sa leggere. */
    private void stampaStato() {
        if (risveglio == null || campioni == null) {
            // Senza Risveglio non c'e' nessuno stato del rilevatore da
            // raccontare, ma il PC deve comunque sapere che il tablet e' vivo
            // e che la parola e' spenta apposta.
            Log.i(TAG, "PC stato parolaspenta=true nome=" + Comandi.NOME
                    + " ritmo=" + (voce != null ? voce.ritmo() : 0f));
            return;
        }
        Campioni.Stato s = risveglio.stato();
        Log.i(TAG, "PC stato accesa=" + s.accesa
                + " rete=" + risveglio.conLaRete()
                + " parola=" + risveglio.frase()
                + " sogliaRete=" + ParolaChiave.arrotonda(s.sogliaRete)
                + " difila=" + s.difila
                + " soglia=" + ParolaChiave.arrotonda(s.soglia)
                + " eco=" + s.eco
                + " modelli=" + risveglio.parola().quantiModelli()
                + " si=" + campioni.quanti(Campioni.SI)
                + " no=" + campioni.quanti(Campioni.NO)
                + " ascolta=" + risveglio.acceso()
                + " ecoCe=" + Orecchio.ecoDisponibile()
                + " durata=" + risveglio.parola().durataTipica() * Mfcc.PASSO_MS
                + " cartella=" + campioni.dove());
    }

    /**
     * La chiave per la ricerca, consegnata da adb.
     *
     *     tools\chiave-spotify.ps1 -Id ... -Segreto ...
     *
     * Passa da un broadcast e non da un file su /sdcard di proposito: cosi' non
     * serve il permesso di leggere la memoria, la chiave non resta in giro dove
     * la vede chiunque, e finisce dritta nei file privati dell'app - che sono
     * l'unico posto su questo tablet dove nessun'altra app puo' arrivare.
     */
    /**
     * Collega il meteo.
     *
     * Parte subito, al contrario della musica: una richiesta da due chilobyte
     * ogni mezz'ora non e' un demone da dodici megabyte, e il pannello della
     * Home deve essere pieno <b>quando la Home compare</b> - che e' il momento
     * in cui la si guarda. La copia su disco fa gia' meta' del lavoro: le
     * scritte ci sono prima ancora che la rete risponda.
     */
    private void agganciaMeteo() {
        meteo = new Meteo(this);
        meteo.setAscolto(new Meteo.Ascolto() {
            @Override public void meteoCambiato() {
                home.meteoCambiato();
                if (sezioneApp != null) sezioneApp.meteoCambiato();
                if (velaMeteo != null) velaMeteo.meteoCambiato();
                // Le notizie di qui seguono la citta' del meteo, quando nessuno
                // ne ha scritta un'altra.
                if (notizie != null) notizie.meteoCambiato();
            }
        });
        Meteo.Pagina apri = new Meteo.Pagina() {
            @Override public void apriMeteo() { apriIlMeteo(); }
        };
        home.setMeteo(meteo, apri);
        if (sezioneApp != null) sezioneApp.setMeteo(meteo, apri);
        meteo.aggiorna();
    }

    /** La pagina intera del meteo, sopra tutto. Si apre dalla tessera della
     *  sezione App e toccando il pannello della Home. */
    private void apriIlMeteo() {
        if (telaio == null || velaMeteo != null) return;
        velaMeteo = new VelaMeteo(this, misure, telaio.vetro(), meteo,
                new VelaMeteo.Uscita() {
                    @Override public void suChiudi() { telaio.nascondiVela(); }
                });
        // Il campo si azzera comunque la vela esca di scena - il suo tasto, il
        // tasto indietro, un'altra vela che le va sopra - o riaprirla dopo un
        // indietro non funzionerebbe piu'. E' la stessa trappola delle
        // impostazioni.
        velaMeteo.setSuChiusa(new Runnable() {
            @Override public void run() { velaMeteo = null; }
        });
        telaio.mostra(velaMeteo);
        velaMeteo.suEntrata();
    }

    /**
     * Collega le notizie.
     *
     * Dopo il meteo, perche' le notizie di qui ne prendono la citta' quando
     * nessuno ne ha scritta un'altra; prima dell'agenda e del riposo, che le
     * ricevono gia' pronte.
     */
    private void agganciaNotizie() {
        notizie = new Notizie(this);
        notizie.setMeteo(meteo);
        notizie.setAscolto(new Notizie.Ascolto() {
            @Override public void notizieCambiate() {
                if (velaNotizie != null) velaNotizie.notizieCambiate();
            }
        });
        home.setNotizie(notizie, new Notizie.Pagina() {
            @Override public void apriNotizie() { apriLeNotizie(); }
        });
        agganciaNotizieDaAdb();
    }

    /** Le notizie a pagina intera. Si aprono dalla tessera della sezione App
     *  e toccando la superficie della Home mentre mostra i titoli. */
    private void apriLeNotizie() {
        if (telaio == null || velaNotizie != null || notizie == null) return;
        velaNotizie = new VelaNotizie(this, misure, telaio.vetro(), notizie);
        velaNotizie.setRegia(new VelaNotizie.Regia() {
            @Override public void chiudiNotizie() {
                velaNotizie = null;
                if (telaio != null) telaio.nascondiVela();
            }
        });
        telaio.mostra(velaNotizie);
        velaNotizie.suEntrata();
    }

    /**
     * Le notizie, da adb:
     *
     *     adb shell am broadcast -a dev.casa.NOTIZIE --es cosa stato
     *     adb shell am broadcast -a dev.casa.NOTIZIE --es cosa aggiorna
     *     adb shell am broadcast -a dev.casa.NOTIZIE --es cosa apri
     *
     * {@code stato} scrive nel registro una riga per filone - quanti titoli,
     * da quanto, e il primo - ed e' quello che legge la pagina « Le notizie »
     * di Gestione Home per dire se quello che si e' scelto porta qualcosa.
     */
    private void agganciaNotizieDaAdb() {
        if (notizieGia != null) return;
        notizieGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (notizie == null) return;
                String cosa = i.getStringExtra("cosa");
                if ("aggiorna".equals(cosa)) { notizie.rinfresca(); return; }
                if ("apri".equals(cosa)) { apriLeNotizie(); return; }
                notizie.stato();
            }
        };
        registerReceiver(notizieGia, new IntentFilter("dev.casa.NOTIZIE"));
    }

    /**
     * La citta' del meteo, da adb:
     *
     *     adb shell am broadcast -a dev.casa.METEO --es citta "Milano"
     *
     * Passa da un broadcast e non da un file per la stessa ragione della
     * chiave di Spotify: niente permessi da chiedere e niente da lasciare in
     * giro. Il nome si cerca su Open-Meteo, e da li' in poi resta in
     * {@code meteo.json} - spostare il tablet in un'altra casa non vuol dire
     * ricompilare Casa.
     */
    private void agganciaMeteoDaAdb() {
        if (meteoGia != null) return;
        meteoGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String citta = i.getStringExtra("citta");
                if (citta == null || meteo == null) return;
                Log.i(TAG, "PC: meteo, cerco « " + citta + " »");
                meteo.vaiA(citta);
                mostraPerUnAttimo("meteo: cerco " + citta);
            }
        };
        registerReceiver(meteoGia, new IntentFilter("dev.casa.METEO"));
    }

    private void agganciaChiave() {
        if (chiaveGia != null) return;
        chiaveGia = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                // Lo stato, per la pagina Spotify del programma sul PC.
                //
                // Serve perche' le due cose che quella pagina governa vivono
                // tutte e due nei file privati dell'app, dove da adb non si
                // guarda: la chiave della ricerca in spotify.json, e le
                // credenziali dell'accesso in credentials.json. Senza questa
                // risposta il PC potrebbe solo mandare alla cieca e sperare -
                // che e' esattamente il modo in cui non ci si accorge di aver
                // scritto la chiave sbagliata.
                if ("stato".equals(i.getStringExtra("cosa"))) {
                    org.json.JSONObject d = Archivio.leggi(MainActivity.this, "spotify.json");
                    String idScritto = d == null ? "" : d.optString("client_id", "");
                    boolean chiave = idScritto.length() > 0
                            && (d == null ? "" : d.optString("client_secret", "")).length() > 0;
                    Log.i(TAG, "PC spotify chiave=" + chiave
                            + " id=" + (chiave ? scorcia(idScritto) : "-")
                            + " accesso=" + (musica != null && musica.giaEntrati())
                            + " utente=" + (musica != null && musica.utente() != null
                                            ? musica.utente() : "-"));
                    return;
                }

                if ("dimentica".equals(i.getStringExtra("cosa"))) {
                    boolean fatto = musica != null && musica.dimenticaAccesso();
                    Log.i(TAG, "PC spotify dimentica=" + fatto);
                    mostraPerUnAttimo(fatto ? "accesso Spotify dimenticato"
                                            : "non c'era nessun accesso da dimenticare");
                    return;
                }

                String id = i.getStringExtra("id");
                String segreto = i.getStringExtra("segreto");
                if (id == null || segreto == null) return;
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("client_id", id);
                    d.put("client_secret", segreto);
                    boolean scritta = Archivio.scrivi(MainActivity.this, "spotify.json", d);
                    if (ricerca != null) ricerca.rileggi();
                    Log.i(TAG, "chiave di ricerca " + (scritta ? "salvata" : "NON salvata"));
                    mostraPerUnAttimo(scritta ? "chiave salvata: adesso posso cercare"
                                              : "chiave non salvata");
                } catch (Exception e) {
                    Log.w(TAG, "chiave non salvata", e);
                }
            }
        };
        registerReceiver(chiaveGia, new IntentFilter("dev.casa.CHIAVE"));
    }

    /** Le prime e le ultime lettere di una chiave, per dire QUALE chiave c'e'
     *  senza scriverla per intero nel registro: il client id non e' un segreto
     *  come il secret, ma un registro si copia e si incolla, e meta' delle
     *  chiavi finite in giro sono finite li'. Sei caratteri bastano a
     *  riconoscere se e' quella che si e' appena mandata. */
    private static String scorcia(String s) {
        if (s == null || s.length() <= 8) return "...";
        return s.substring(0, 4) + "..." + s.substring(s.length() - 4);
    }

    private BroadcastReceiver chiaveGia;

    @Override
    public void onRequestPermissionsResult(int codice, String[] permessi, int[] esiti) {
        if (esiti.length > 0 && esiti[0] == PackageManager.PERMISSION_GRANTED) {
            avviaAssistente();
        } else {
            home.setStato("senza microfono non posso ascoltare");
        }
    }

    /**
     * Scrive una risposta e la cancella da sola.
     *
     * E' la controparte muta di Voce.di(): quando l'ordine e' arrivato con un
     * dito, la conferma si legge invece di sentirla. Sparisce da sola perche'
     * su una schermata sempre accesa una scritta ferma diventa sporcizia.
     */
    private void mostraPerUnAttimo(final String testo) {
        home.setStato(testo);
        scadenzaMessaggio.removeCallbacksAndMessages(null);
        scadenzaMessaggio.postDelayed(new Runnable() {
            @Override public void run() { home.setStato(null); }
        }, 3500);
    }

    /** I pallini sulla barra: si vedono da qualunque sezione, ed e' il motivo
     *  per cui la barra sta sempre li'. */
    private void aggiornaSpie() {
        if (telaio == null) return;
        if (comandi != null) telaio.setSpia(RADIO, comandi.radioAccesa());
        if (musica != null) telaio.setSpia(MUSICA, musica.staSuonando());
        // Le spie non passano piu' da Comandi: quello sa della radio, ma timer
        // e lampade hanno un padrone loro, e chiedere a Comandi che ore sono
        // vorrebbe dire fargli da tramite per roba che non tocca.
        if (orologio != null) telaio.setSpia(OROLOGIO, orologio.qualcheTimer());
        if (luci != null) telaio.setSpia(LUCI, luci.accese() > 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Se si torna dalle Impostazioni, si rinascondono: le chiude e le
        // toglie di mezzo fino alla prossima volta che qualcuno le apre.
        Impostazioni.nascondi(this);
        // Se qualcuno ha cambiato lo sfondo, Casa se lo riprende. E' il
        // confronto di due interi: costa meno che difendersi con una
        // restrizione di sistema, e non rischia di bloccare anche noi.
        prendiLoSfondo();
        if (telaio != null && telaio.senzaVetro()) telaio.preparaVetro(this);
        // Tornando in scena si rilegge chi suona: mentre Casa era dietro,
        // qualcuno puo' aver messo o tolto della musica. E' anche il momento in
        // cui il permesso appena concesso da adb comincia a valere.
        if (riproduzione != null) riproduzione.aggancia();
        // Il conto del riposo riparte da adesso: quello di prima e' scaduto
        // mentre Casa era dietro a qualcun altro, e non conta.
        if (riposo != null) riposo.riparti();
        // Le principali si tengono fresche mentre Casa e' sullo schermo, e
        // solo allora: dietro Netflix non le legge nessuno.
        if (notizie != null) notizie.riprendi();
        // Il meteo, uguale: prima non aveva un giro suo, e la Home che resta in
        // scena per giorni non lo rinfrescava mai.
        if (meteo != null) meteo.riprendi();
        // Gli impegni: mentre Casa era dietro il sincronizzatore puo' aver
        // portato qualcosa, e l'avviso del provider si perde con la finestra.
        if (calendario != null) calendario.aggiorna();
        if (dietroDal > 0 && SystemClock.elapsedRealtime() - dietroDal > ASSENZA_MS) {
            tornaFresco("Casa torna in scena dopo " + (SystemClock.elapsedRealtime() - dietroDal) / 60000 + " minuti", true);
        }
        dietroDal = 0;
    }

    /**
     * Casa passa dietro: il riposo si ferma.
     *
     * Non e' solo il conto - che scattando dietro Netflix non si vedrebbe
     * comunque - sono le sue due bitmap da due megabyte l'una. Il momento in
     * cui Casa non e' piu' sullo schermo e' esattamente il momento in cui
     * quella memoria serve a chi lo schermo ce l'ha, ed e' la stessa ragione
     * per cui {@link #onTrimMemory} libera il vetro e i loghi.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (riposo != null) riposo.fermati();
        if (notizie != null) notizie.sospendi();
        if (meteo != null) meteo.sospendi();
        dietroDal = SystemClock.elapsedRealtime();
    }

    /**
     * Qualcuno ha toccato lo schermo o premuto un tasto: il conto ricomincia.
     *
     * Android la chiama <b>prima</b> di consegnare l'evento, e per questo qui
     * non si toglie niente: se il riposo e' in scena, e' lui a prendersi il
     * tocco e a non passarlo a nessuno. Vedi {@link Riposo#sveglia()}.
     */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (riposo != null) riposo.sveglia();
        // Il primo tocco dopo tanto e' quello di chi rientra: di solito toglie
        // il riposo, e la Home che compare sotto deve dire le cose di adesso.
        long adesso = SystemClock.elapsedRealtime();
        if (adesso - ultimoTocco > ASSENZA_MS) {
            tornaFresco("primo tocco dopo " + (adesso - ultimoTocco) / 60000 + " minuti", true);
        }
        ultimoTocco = adesso;
    }

    /**
     * Quando Casa non e' sullo schermo, restituisce quello che puo'.
     *
     * E' la riga che decide se Netflix gira. Su 1 GB veri, con Spotify e
     * Netflix accanto, quello che Casa tiene in memoria mentre non la guarda
     * nessuno e' memoria tolta a loro - e il sintomo, quando manca, non e' un
     * errore ma "la musica si e' fermata da sola".
     */
    @Override
    public void onTrimMemory(int livello) {
        super.onTrimMemory(livello);
        if (livello >= TRIM_MEMORY_UI_HIDDEN && telaio != null) {
            telaio.liberaVetro();
            if (loghi != null) loghi.svuota();
            if (copertine != null) copertine.svuota();
            Log.i(TAG, "vetro e loghi liberati (livello " + livello + ")");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voce != null) voce.chiudi();
        if (comandi != null) comandi.chiudi();
        if (riproduzione != null) riproduzione.chiudi();
        // La Musica resta accesa, per la stessa ragione dell'Orologio qui
        // sotto: se Android rifa' la finestra, il brano non si interrompe. Si
        // stacca solo chi guardava.
        if (musica != null) musica.staccati(spiaMusica);
        // L'Orologio resta in piedi: gli allarmi non appartengono alla
        // finestra. Si stacca solo chi guardava.
        if (orologio != null) orologio.staccati(spiaOrologio);
        risveglioLuci.removeCallbacksAndMessages(null);
        if (telefono != null) { telefono.chiudi(); telefono = null; }
        scadenzaRete.removeCallbacksAndMessages(null);
        if (rete != null) {
            try {
                ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(rete);
            } catch (Exception ignorato) { }
            rete = null;
        }
        if (luci != null) luci.chiudi();
        // Il meteo e' della finestra, non del processo: un timer o un brano le
        // sopravvivono, una previsione no.
        if (meteo != null) { meteo.chiudi(); meteo = null; }
        if (riposo != null) { riposo.chiudi(); riposo = null; }
        if (notte != null) { notte.chiudi(); notte = null; }
        if (notizie != null) { notizie.chiudi(); notizie = null; }
        if (calendario != null) { calendario.chiudi(); calendario = null; }
        if (agendaGia != null) { unregisterReceiver(agendaGia); agendaGia = null; }
        if (riposoGia != null) { unregisterReceiver(riposoGia); riposoGia = null; }
        if (vetrinaGia != null) { unregisterReceiver(vetrinaGia); vetrinaGia = null; }
        if (provaGia != null) { unregisterReceiver(provaGia); provaGia = null; }
        if (chiaveGia != null) { unregisterReceiver(chiaveGia); chiaveGia = null; }
        if (meteoGia != null) { unregisterReceiver(meteoGia); meteoGia = null; }
        if (notizieGia != null) { unregisterReceiver(notizieGia); notizieGia = null; }
        if (parolaGia != null) { unregisterReceiver(parolaGia); parolaGia = null; }
        if (configGia != null) { unregisterReceiver(configGia); configGia = null; }
        passoMicrofono.removeCallbacksAndMessages(null);
        // Il microfono si molla per ultimo, e sempre: lasciarlo aperto tiene
        // sveglio il servizio audio anche quando Casa non c'e' piu'.
        if (risveglio != null) { risveglio.chiudi(); risveglio = null; }
    }

    /**
     * Riavvia Casa: si spegne e si riaccende da sola.
     *
     * <b>Perche' non basta chiudersi.</b> Casa e' la Home, e una Home che muore
     * il sistema la rimette in piedi da solo - quasi sempre. « Quasi » non e'
     * abbastanza per un apparecchio appeso a un muro senza tastiera: se quel
     * giorno non ripartisse, chi ci abita si troverebbe davanti uno schermo
     * nero e nessun modo di fare niente. Quindi l'appuntamento per riaprirsi si
     * prende <b>prima</b> di morire, e lo tiene AlarmManager, che sopravvive al
     * processo.
     *
     * Mezzo secondo di ritardo: il tempo che il sistema si accorga che il
     * processo non c'e' piu'. Piu' corto e la nuova Activity nascerebbe mentre
     * la vecchia sta ancora morendo, che e' il modo in cui si finisce con due
     * Casa.
     */
    static void riavvia(Context c) {
        Log.i(TAG, "riavvio chiesto dalla tessera");
        try {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            android.app.PendingIntent poi = android.app.PendingIntent.getActivity(
                    c, 0, i, android.app.PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager sveglia = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (sveglia != null) {
                sveglia.setExact(AlarmManager.ELAPSED_REALTIME,
                        android.os.SystemClock.elapsedRealtime() + 500, poi);
            }
        } catch (Exception e) {
            Log.w(TAG, "riavvio: non ho potuto prendere l'appuntamento", e);
        }
        // E adesso si muore per davvero. finish() da solo non basta: il
        // processo resterebbe vivo con dentro tutto quello che si voleva
        // buttare via, che e' esattamente il motivo per cui si riavvia.
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private boolean siamoDeviceOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(getPackageName());
    }

    /**
     * Il chiosco si accende da adb, non da qui:
     *
     *     adb shell settings put global casa_chiosco 1
     *
     * Di proposito parte spento. Con il lock task attivo non si arriva piu'
     * alle Impostazioni, e finche' il tablet e' in allestimento - account
     * Google, Wi-Fi, permessi - servono ancora.
     */
    private boolean chioscoRichiesto() {
        return Settings.Global.getInt(getContentResolver(), "casa_chiosco", 0) == 1;
    }

    /**
     * Registra Casa come Home preferita in modo permanente.
     *
     * Serve perche' con due Home installate (Casa e launcher3) Android mostra
     * il selettore a ogni avvio. "cmd package set-home-activity" non funziona
     * su questo Android; questa e' la strada del device owner, e non chiede
     * niente a nessuno. launcher3 resta installato come rete di sicurezza: se
     * Casa sparisse, il tablet avrebbe ancora una schermata.
     */
    private void diventaHomePermanente() {
        IntentFilter filtro = new IntentFilter(Intent.ACTION_MAIN);
        filtro.addCategory(Intent.CATEGORY_HOME);
        filtro.addCategory(Intent.CATEGORY_DEFAULT);
        dpm.addPersistentPreferredActivity(admin, filtro,
                new ComponentName(this, MainActivity.class));
        Log.i(TAG, "Casa registrata come Home permanente");
    }

    @Override
    public void onWindowFocusChanged(boolean haFocus) {
        super.onWindowFocusChanged(haFocus);
        if (haFocus) nascondiBarre();
    }

    /**
     * IMMERSIVE_STICKY: le barre tornano a scomparire da sole dopo che qualcuno
     * ha strisciato dal bordo. Senza STICKY resterebbero visibili finche' non
     * si tocca lo schermo, e su una schermata che nessuno tocca vorrebbe dire
     * per sempre.
     */
    private void nascondiBarre() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Su una Home l'indietro non esce: al massimo chiude quello che si e'
     *  aperto, e in ultimo riporta alla schermata iniziale. */
    @Override
    public void onBackPressed() {
        if (telaio != null) telaio.indietro();
    }
}
