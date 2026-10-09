package dev.casa;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cosa sa fare Casa.
 *
 * Riconoscimento a regole, non a modello: i comandi di casa sono una ventina e
 * si dicono sempre nello stesso modo. Un LLM servirebbe per capire le frasi
 * storte, e si potra' aggiungere dietro a questo - ma quando c'e' rete lenta o
 * assente, queste regole rispondono lo stesso e in un decimo del tempo.
 *
 * <b>Qui non c'e' piu' nessun timer.</b> Prima ce n'era uno, dentro questa
 * classe, su un {@code Handler}: uno solo, e vivo quanto il processo. Adesso i
 * timer e le sveglie sono di {@link Orologio}, che li registra in AlarmManager
 * e li ritrova dopo un riavvio. Qui resta soltanto il capire cosa e' stato
 * chiesto - che e' il mestiere di questa classe - e il girarlo a chi lo fa.
 */
public class Comandi {

    /**
     * Come si chiama.
     *
     * Sta qui e non in tre punti diversi perche' lo dice la voce quando
     * qualcuno glielo chiede.
     *
     * <b>Torna a essere « Home ».</b> Per un po' si e' chiamato Marvin, che
     * era il nome che sapeva riconoscere il rilevatore a bordo - una delle
     * trentacinque parole di Speech Commands. Da quando la parola di
     * attivazione e' spenta (vedi MainActivity.PAROLA_DI_ATTIVAZIONE) quel
     * vincolo non c'e' piu', e il nome puo' tornare a essere quello di casa.
     */
    public static final String NOME = "Home";

    /**
     * La parola che sveglia Casa adesso.
     *
     * Gliela dice {@link MainActivity} quando il rilevatore e' pronto, perche'
     * dipende da chi ascolta: la rete risponde a "Marvin", il confronto a
     * impronte alla frase registrata. Finche' non arriva, si dice il nome e
     * basta.
     */
    private String parolaChiave = NOME;

    public void setParolaChiave(String p) { if (p != null) parolaChiave = p; }

    /** Quello che l'interfaccia deve sapere per disegnarsi. */
    public interface Schermo {
        void suRadio(String cosaSuona);
        void suMessaggio(String testo);      // risposta scritta, senza voce
    }

    private final Context contesto;
    private final Voce voce;
    private final Schermo schermo;
    private final Radio radio;
    private final Orologio orologio;
    private final Luci luci;
    private final AudioManager audio;

    /** La musica di Casa e le playlist. Arrivano dopo la costruzione - il
     *  motore lo accende chi entra nella sezione, non chi costruisce i comandi -
     *  quindi non sono final e possono essere null. */
    /**
     * I comandi scritti dal PC, che vengono prima di tutti questi.
     *
     * Sta qui e non dentro l'elenco delle regole perche' non e' una regola in
     * piu': e' un ELENCO di regole che arriva da fuori e che ha la precedenza -
     * ed e' la precedenza a renderlo utile, perche' e' quello che permette di
     * cambiare una risposta che non piace senza toccare questo file.
     */
    private ComandiUtente utente;

    /**
     * Le risposte di fabbrica, e quelle riscritte da chi usa Casa.
     *
     * Prima erano stringhe dentro le righe qui sotto: per cambiare "Radio
     * spenta." bisognava avere il progetto e ricompilare. Adesso ogni risposta
     * fissa ha un nome, e chi non la sopporta piu' la riscrive dal PC.
     */
    private final Risposte risposte = new Risposte();

    /** Rilegge le risposte riscritte dal PC. */
    public void caricaRisposte(android.content.Context c) { risposte.carica(c); }

    public Risposte risposte() { return risposte; }

    private Musica musica;
    private Preferiti preferiti;
    private Cerca ricerca;

    /** Le note e gli impegni. Arrivano dalla regia, come la musica. */
    private Appunti appunti;
    private Calendario calendario;

    public void setAgenda(Appunti a, Calendario c) { appunti = a; calendario = c; }

    /**
     * Da dove e' arrivato l'ordine in corso.
     *
     * Casa risponde a voce solo a chi le ha parlato. Chi tocca una casella sta
     * gia' guardando lo schermo e ha appena visto la funzione accendersi: farle
     * dire "Metto la radio" ad ogni tocco e' rumore, non conferma. Alexa fa
     * cosi' anche lei - la voce risponde alla voce.
     */
    private boolean daVoce;

    /**
     * Risponde nel modo giusto per come e' arrivata la richiesta.
     *
     * Nel registro ci finisce sempre, anche quando la risposta e' a voce: e'
     * l'unico modo che ha il PC di far vedere <b>cosa risponde</b> a una frase
     * di prova, e serve a chi riscrive una risposta per controllare che sia
     * davvero quella nuova a uscire. Non stampa chi ha deciso il testo, solo
     * il testo: chi ha deciso lo dice gia' la riga di {@code comandi:}.
     */
    private void rispondi(String testo) {
        Log.i(MainActivity.TAG, "dice: " + testo);
        if (daVoce) voce.di(testo);
        else schermo.suMessaggio(testo);
    }

    /**
     * La conferma che non si dice: si scrive e basta.
     *
     * <b>Quando il risultato si sente, raccontarlo e' rumore.</b> Chi dice
     * « alza il volume » sente il volume alzarsi: la voce che risponde « alzo
     * il volume » arriva mezzo secondo dopo, <b>abbassa quello che si stava
     * ascoltando per parlarci sopra</b> - il sintetizzatore lo fa sempre - e
     * dice una cosa gia' successa. Vale per il volume, per la pausa, per il
     * salto di brano e per tutto quello che si spegne: il silenzio che arriva
     * e' una conferma migliore di qualunque frase.
     *
     * Sullo schermo pero' ci resta, perche' li' non copre niente e risponde
     * all'unica domanda che rimane - « mi ha sentito? ». E resta nel registro,
     * che e' come il PC vede cosa e' successo.
     */
    private void segna(String testo) {
        Log.i(MainActivity.TAG, "fa: " + testo);
        schermo.suMessaggio(testo);
    }

    /**
     * Una fra piu' risposte che vogliono dire la stessa cosa.
     *
     * <b>Perche' non basta la frase giusta.</b> Una risposta corretta e sempre
     * identica, sentita venti volte al giorno, e' la cosa che piu' di ogni
     * altra fa sembrare che dall'altra parte non ci sia nessuno: la seconda
     * volta si riconosce la registrazione, la terza la si finisce a memoria.
     * Bastano due o tre modi di dire la stessa cosa perche' smetta di
     * succedere - non serve che siano spiritosi, serve che non siano sempre lo
     * stesso.
     *
     * Si tiene memoria dell'ultima scelta per non ripetere due volte di fila
     * la stessa: a caso puro capita, e capita proprio quando da' piu' fastidio.
     */
    private final java.util.Random sorte = new java.util.Random();
    private final java.util.HashMap<String, Integer> ultimaScelta =
            new java.util.HashMap<String, Integer>();

    private String unaDi(String chiave, String... modi) {
        if (modi.length == 0) return "";
        if (modi.length == 1) return modi[0];
        Integer prima = ultimaScelta.get(chiave);
        int i = sorte.nextInt(modi.length);
        if (prima != null && i == prima.intValue()) i = (i + 1) % modi.length;
        ultimaScelta.put(chiave, Integer.valueOf(i));
        return modi[i];
    }

    /** Come sopra, ma risponde direttamente. */
    private void rispondiUnaDi(String chiave, String... modi) {
        rispondi(unaDi(chiave, modi));
    }

    /** La radio, per chi deve abbassarla: la Sordina la prende da qui. */
    public Radio radio() { return radio; }

    public void setUtente(ComandiUtente u) { utente = u; }

    public ComandiUtente utente() { return utente; }

    public Comandi(Context contesto, Voce voce, Schermo schermo,
                   Orologio orologio, Luci luci) {
        this.contesto = contesto;
        this.voce = voce;
        this.schermo = schermo;
        this.orologio = orologio;
        this.luci = luci;
        this.audio = (AudioManager) contesto.getSystemService(Context.AUDIO_SERVICE);
        this.radio = new Radio(new Radio.Spia() {
            @Override public void suRadio(String cosa) { schermo.suRadio(cosa); }
        });
    }

    /**
     * Il punto d'ingresso: una frase gia' ripulita del richiamo.
     *
     * <b>Minuscole e senza accenti</b>, una volta sola e per tutti: le regole
     * qui sotto sono scritte senza - "che ora e", "piu' forte", "vai avanti
     * cosi" - mentre il riconoscitore vocale gli accenti li scrive. Il perche'
     * per esteso, e cos'altro va appiattito con lei, sta su
     * {@link Testo#senzaAccenti}.
     */
    public void esegui(String frase) {
        daVoce = true;
        String f = Testo.senzaAccenti(frase.toLowerCase(Locale.ITALIAN).trim());

        // I comandi scritti dal PC, prima di tutto il resto. Vedi
        // ComandiUtente: e' la precedenza a renderli utili.
        if (provaUtente(f)) return;

        // Le note vengono PRIMA di tutto il resto, e non e' un capriccio: una
        // nota puo' contenere qualunque frase, comandi compresi. « prendi nota
        // di spegnere la radio » e' una riga da scrivere in lista, non una
        // radio da spegnere - e qualunque altro ordine di controllo la
        // spegnerebbe davvero.
        if (provaAgenda(f)) return;

        // Chi e', come sta, i saluti. Prima di tutto il resto: sono frasi
        // corte e piene di parole comuni - "ciao", "come va" - che piu' in
        // basso finirebbero dentro qualche regola per sbaglio.
        if (provaConversazione(f)) {
            // fatto tutto dentro

        // "l'ora" a parola intera, o « metti la sveglia all'ora di pranzo » -
        // che lo contiene - farebbe rispondere che ore sono.
        } else if (parole(f, "che ore sono", "che ora e", "dimmi l'ora", "l'ora")) {
            rispondi(oraParlata());

        } else if (contiene(f, "che giorno", "che data", "in che giorno")) {
            rispondi(dataParlata());

        // Prima della radio e della musica: contiene "suona", e quelle regole
        // sono generose.
        } else if (contiene(f, "cosa sta suonando", "che cosa suona", "cosa suona",
                            "che canzone e", "cosa c'e' in riproduzione",
                            "che stazione e")) {
            rispondi(cosaSuona());

        // "spegni tutto" prima di tutto il resto: contiene "spegni", e cadrebbe
        // nel ramo delle luci spegnendo solo quelle. Le luci pero' si nominano
        // anche con "tutte" - "spegni tutte le luci" - e quella frase deve
        // restare alle luci: e' l'unica cosa che la distingue da questa.
        } else if (contiene(f, "buonanotte casa")
                   || (chiedeDiSpegnere(f) && parole(f, "tutto", "tutti") && !nominaLuci(f))) {
            spegniTutto();

        // <b>Fermare viene prima di mettere.</b> Non e' una preferenza: e' la
        // cura di un comando che faceva l'esatto contrario di quello che gli
        // era stato chiesto. "stop radio" contiene la parola "radio", nessuna
        // delle frasi scritte a mano qui sopra lo prendeva, e cadeva fino alla
        // regola della radio - che e' generosa e accende. Vedi provaSpegni.
        } else if (provaSpegni(f)) {
            // fatto tutto dentro

        // Le luci vengono prima della radio e prima di Netflix, e chiedono un
        // verbo o un nome loro per prendersi la frase: senza quella cautela
        // "metti la radio in camera" finirebbe ad accendere una lampada.
        } else if (provaLuci(f)) {
            // fatto tutto dentro

        // "metti Levante su spotify", "musica di Battisti": la playlist
        // editoriale dell'artista. Sta PRIMA delle playlist di casa e della
        // radio perche' la frase nomina Spotify esplicitamente, e prima della
        // regola generica della musica - che, contenendo "spotify", se la
        // prenderebbe e metterebbe la prima playlist qualunque.
        } else if (provaArtista(f)) {
            // fatto tutto dentro

        // Una playlist chiamata per nome, come si fa con una stazione: "metti
        // scostumatezza". Sta prima della radio perche' e' piu' specifica -
        // sono i nomi che ci sono davvero su questo account - e Radio.riconosci
        // sotto e' generoso: cerca parole come "radio" o "kiss" dentro la
        // frase, e una playlist che si chiama "Radio Anni 90" finirebbe li'.
        } else if (playlistDetta(f) != null) {
            Preferiti.Voce v = playlistDetta(f);
            musica.suona(v.uri);
            rispondi("Metto " + v.nome + ".");

        // Basta il nome della stazione: "metti kiss kiss napoli" non contiene
        // la parola "radio" e prima cadeva fuori da tutte le regole, finendo
        // nel "non ho capito". A voce una stazione la si chiama per nome, non
        // la si annuncia come radio.
        //
        // Qui si accende e basta: chi voleva spegnerla e' gia' stato servito
        // molto piu' in alto, da provaSpegni.
        //
        // E non si prende le frasi che chiedono di <b>scorrere</b>: « cambia
        // stazione » nomina la stazione ma vuole la successiva, e finiva qui
        // ad accendere la prima dell'elenco. Se ne occupa provaRiproduzione,
        // poco piu' sotto.
        } else if (nominaLaRadio(f) && !chiedeDiScorrere(f)) {
            Radio.Stazione s = radio.riconosci(f);
            radio.accendi(s);
            // Il nome della stazione si dice: e' l'unica cosa che chi ha
            // parlato non sa gia', e lo stream ci mette qualche secondo ad
            // aprirsi - quei secondi, senza una parola, sembrano un comando
            // caduto nel vuoto.
            rispondi(s != null ? "Metto " + s.nome + "." : risposte.di("radio.messa"));

        // Annullare viene PRIMA di avviare, e non e' un dettaglio di stile:
        // "annulla il timer" contiene la parola "timer", quindi finiva nel ramo
        // che ne avvia uno, dove non c'e' nessun numero da trovare. Casa
        // rispondeva "Di quanti minuti?" e il timer continuava a scorrere: il
        // ramo qui sotto non veniva raggiunto mai, da nessuna frase.
        //
        // La regola, per le prossime: fra due regole in cui una frase cade
        // tutte e due, si mette prima la piu' specifica.
        } else if (contiene(f, "annulla il timer", "ferma il timer", "cancella il timer",
                            "annulla timer", "togli il timer")) {
            rispondi(orologio.fermaTuttiITimer() ? "Timer annullato." : "Non c'era nessun timer.");

        // Anche qui la piu' specifica per prima: "togli la sveglia" contiene
        // "sveglia", e finirebbe a metterne una nuova senza un orario.
        } else if (contiene(f, "annulla la sveglia", "togli la sveglia", "spegni la sveglia",
                            "cancella la sveglia")) {
            rispondi(spegniLeSveglie() ? "Sveglia spenta." : "Non c'era nessuna sveglia accesa.");

        } else if (contiene(f, "sveglia", "svegliami")) {
            metti(f);

        } else if (contiene(f, "timer", "conta", "avvisami fra", "avvisami tra")) {
            avviaTimer(f);

        // Avanti, indietro, pausa. Vanno DOPO la radio - "metti la prossima
        // stazione" deve poter nominare una stazione - e prima del volume.
        } else if (provaRiproduzione(f)) {
            // fatto tutto dentro

        // "volume al 50", "metti il volume a 7". Prima di alza/abbassa, che
        // prenderebbero la frase senza guardare il numero.
        } else if (contiene(f, "volume") && numeroIn(f) >= 0) {
            volumeA(numeroIn(f));

        // Alzare e abbassare non nominano niente, quindi qui ci finisce anche
        // "abbassa la luce" se le luci non se la sono presa prima. Il
        // controllo su nominaLuci e' quello che tiene separate due frasi che
        // per il resto sono identiche - e per un po' non c'era: chi diceva
        // « abbassa la luce » si sentiva abbassare la musica.
        } else if (!nominaLuci(f) && parole(f, "alza", "alzalo", "alzala", "aumenta",
                                            "piu forte", "piu' forte", "piu alto", "piu' alto")) {
            volumePasso(+1);

        } else if (!nominaLuci(f) && parole(f, "abbassa", "abbassalo", "abbassala",
                                            "diminuisci", "piu piano", "piu' piano",
                                            "piu basso", "piu' basso")) {
            volumePasso(-1);

        // La musica non apre piu' l'app di qualcun altro: suona qui dentro.
        // Vedi Musica. Se l'app ufficiale e' ancora installata la si raggiunge
        // dalla sezione App, che e' il posto delle app.
        } else if (contiene(f, "musica", "spotify", "metti la musica", "metti su spotify")) {
            metticiLaMusica();

        // "cerca X", "metti X di Y": la ricerca nel catalogo. Viene dopo le
        // playlist - un nome che si ha in casa vince su una ricerca in rete,
        // che costa una richiesta e puo' sbagliare - e prima di Netflix, o
        // "cerca il film" finirebbe li'.
        } else if (contiene(f, "cerca", "trova", "fammi sentire", "voglio sentire")) {
            cercaEMetti(dopoLaParola(f, "cerca", "trova", "fammi sentire", "voglio sentire"));

        } else if (contiene(f, "netflix", "film", "serie")) {
            apri("com.netflix.mediaclient", "Netflix");

        } else if (contiene(f, "cosa sai fare", "aiuto", "cosa puoi fare")) {
            rispondi("Sono " + NOME + ". Posso dirti l'ora, mettere timer e sveglie, "
                  + "prendere nota di quello che devi fare, tenere la lista della spesa "
                  + "e dirti che impegni hai, "
                  + "accendere la radio e la musica, saltare avanti e indietro, "
                  + "cercare una canzone o un artista, accendere le luci anche di "
                  + "un colore, regolare volume e luminosita, e spegnere tutto.");

        } else {
            nonHoCapito(f);
        }
    }

    // --- note e impegni -----------------------------------------------------

    /** I modi di dire « scrivilo »: quello che segue diventa una riga. */
    private static final String[] PRENDI_NOTA = {
        "prendi nota", "prendi appunto", "prendi un appunto", "segnati",
        "annota", "aggiungi alla lista", "metti in lista", "aggiungi in lista",
        "nota", "segna", "ricordami",
    };

    /**
     * Le note e gli impegni.
     *
     * <h3>L'ordine qui dentro conta piu' che altrove</h3>
     *
     * « ho fatto la spesa » contiene "fatto", « segna come fatto il pane »
     * contiene sia "segna" che "fatto": se si guardasse prima chi <b>aggiunge</b>,
     * quella frase finirebbe a scrivere in lista una riga che si chiama « come
     * fatto il pane ». Quindi prima si guarda chi spunta, poi chi aggiunge, e
     * per ultimo chi legge.
     *
     * <h3>« ricordami » vuol dire due cose</h3>
     *
     * « ricordami di comprare il pane » e' una nota. « ricordami fra dieci
     * minuti » e' un timer, e chi lo dice non vuole trovarsi una riga in lista
     * che dice « fra dieci minuti ». La differenza e' se la frase contiene una
     * durata, e la sa gia' dire {@link #durataIn}.
     */
    private boolean provaAgenda(String f) {
        // --- spuntare
        if (parole(f, "ho fatto", "spunta", "segna come fatto", "l'ho fatta", "l'ho fatto",
                   "ho comprato", "ho gia' comprato")) {
            // Cerca in tutte e due le liste: « ho comprato il latte » trova il
            // latte nella spesa, « ho fatto la spesa » la riga di casa.
            String cosa = dopoLaParola(f, "segna come fatto", "ho fatto", "ho gia' comprato",
                    "ho comprato", "spunta");
            Appunti.Nota n = appunti == null ? null : appunti.cerca(senzaArticoli(cosa));
            if (n != null) {
                appunti.inverti(n);
                rispondi(risposte.di("nota.spuntata"));
            } else {
                rispondi(risposte.di("nota.nontrovo"));
            }
            return true;
        }

        // --- la spesa: leggerla. Prima di aggiungere, perche' « leggi la lista
        // della spesa » nomina la spesa esattamente come « aggiungi il pane alla
        // lista della spesa ».
        if (parole(f, "cosa devo comprare", "che devo comprare", "cosa c'e' da comprare",
                   "che c'e' da comprare", "leggi la spesa")
                || (f.contains("lista della spesa")
                    && parole(f, "leggi", "leggimi", "dimmi", "cosa c'e'", "che c'e'"))) {
            rispondi(leggiLaSpesa());
            return true;
        }

        // --- la spesa: aggiungere. Prima delle note, perche' « segna il latte
        // nella spesa » contiene « segna » e finirebbe fra le cose da fare.
        if (perLaSpesa(f)) {
            String cosa = ritagliaSpesa(f);
            if (cosa.length() < 2) {
                rispondi(risposte.di("spesa.cosa"));
                return true;
            }
            if (appunti == null || appunti.aggiungiSpesa(cosa, daVoce) == 0) {
                rispondi(risposte.di("spesa.cosa"));
                return true;
            }
            rispondi(risposte.di("spesa.presa"));
            return true;
        }

        // --- aggiungere
        if (parole(f, PRENDI_NOTA)) {
            // « ricordami fra un'ora »: e' un timer, non una nota.
            if (parole(f, "ricordami") && durataIn(f) > 0) {
                avviaTimer(f);
                return true;
            }
            String cosa = ritaglia(f);
            if (cosa.length() < 2) {
                rispondi(risposte.di("nota.cosa"));
                return true;
            }
            if (appunti == null || appunti.aggiungi(cosa, daVoce) == null) {
                rispondi(risposte.di("nota.cosa"));
                return true;
            }
            rispondi(risposte.di("nota.presa"));
            return true;
        }

        // --- leggere la lista
        if (parole(f, "che devo fare", "cosa devo fare", "che ho da fare",
                   "cosa ho da fare", "che c'e' da fare", "leggi le note",
                   "le mie note", "la lista", "le cose da fare")) {
            rispondi(leggiLeNote());
            return true;
        }

        // --- leggere gli impegni
        if (parole(f, "impegni", "appuntamenti", "appuntamento", "in agenda",
                   "che ho oggi", "cosa ho oggi", "che c'e' oggi", "il calendario")) {
            rispondi(leggiGliImpegni());
            return true;
        }
        return false;
    }

    /** Quello che viene dopo la parola d'innesco, senza le paroline in mezzo. */
    private static String ritaglia(String f) {
        String cosa = dopoLaParola(f, "aggiungi alla lista", "metti in lista",
                "aggiungi in lista", "prendi un appunto", "prendi appunto",
                "prendi nota", "annota", "segnati", "ricordami", "nota", "segna");
        // « aggiungi il pane alla lista »: l'innesco sta in fondo, e quello che
        // conta e' quello che c'era prima.
        if (cosa.length() < 2 && f.contains("alla lista")) {
            cosa = f.substring(0, f.indexOf("alla lista"));
            cosa = dopoLaParola(cosa, "aggiungi", "metti");
        }
        return senzaArticoli(cosa);
    }

    /** Via le paroline d'attacco: « di comprare il pane » -> « comprare il pane ». */
    private static String senzaArticoli(String testo) {
        String t = testo.trim();
        while (t.startsWith(":") || t.startsWith(",")) t = t.substring(1).trim();
        for (String a : new String[] { "di ", "che ", "questo ", "questa " }) {
            if (t.startsWith(a)) { t = t.substring(a.length()).trim(); break; }
        }
        return t;
    }

    /**
     * I modi di dire « nella spesa ». Si guardano per pezzo e non per parola
     * sola: « spesa » da sola c'e' anche in « ricordami di fare la spesa »,
     * che e' una cosa da fare e non un prodotto da comprare.
     */
    private static final String[] NELLA_SPESA = {
        "alla lista della spesa", "nella lista della spesa", "sulla lista della spesa",
        "in lista della spesa", "lista della spesa", "alla spesa", "nella spesa",
        "per la spesa", "da comprare",
    };

    /** Le parole d'attacco da togliere davanti a quello che si compra. */
    private static final String[] ATTACCHI_SPESA = {
        "aggiungi", "aggiungere", "metti", "mettere", "segna", "segnati", "scrivi",
        "annota", "prendi nota", "ricordami", "ricordati", "devo", "dobbiamo", "di",
        "compra", "comprare", "manca", "mancano", "e' finito", "e' finita",
        "sono finiti", "sono finite", "anche",
    };

    /** La frase parla di spesa: nomina la lista, o chiede di comprare. */
    private static boolean perLaSpesa(String f) {
        for (String c : NELLA_SPESA) if (f.contains(c)) return true;
        return parole(f, "compra", "comprare", "comprami");
    }

    /**
     * Quello che c'e' da comprare, senza tutto il resto: « aggiungi il latte e
     * le uova alla lista della spesa » -> « il latte e le uova ». Gli articoli
     * e la divisione in righe li fa {@code Appunti.aggiungiSpesa}.
     */
    private static String ritagliaSpesa(String f) {
        String t = f;
        for (String c : NELLA_SPESA) {
            int dove = t.indexOf(c);
            if (dove >= 0) t = (t.substring(0, dove) + " " + t.substring(dove + c.length())).trim();
        }
        boolean tolto = true;
        while (tolto) {
            tolto = false;
            for (String a : ATTACCHI_SPESA) {
                if (t.equals(a)) return "";
                if (t.startsWith(a + " ")) {
                    t = t.substring(a.length()).trim();
                    tolto = true;
                }
            }
        }
        while (t.startsWith(":") || t.startsWith(",")) t = t.substring(1).trim();
        return t;
    }

    /**
     * La spesa, detta. Sei cose e non quattro come le note: sono una parola
     * l'una, e « latte, uova, pane » si ascolta tutto d'un fiato.
     */
    private String leggiLaSpesa() {
        java.util.List<Appunti.Nota> da = appunti == null
                ? new java.util.ArrayList<Appunti.Nota>() : appunti.daFare(Appunti.SPESA);
        if (da.isEmpty()) return risposte.di("spesa.vuota");
        StringBuilder b = new StringBuilder("Da comprare: ");
        int quante = Math.min(6, da.size());
        for (int i = 0; i < quante; i++) {
            if (i > 0) b.append(i == quante - 1 && da.size() == quante ? " e " : ", ");
            b.append(da.get(i).testo.toLowerCase(Locale.ITALIAN));
        }
        if (da.size() > quante) b.append(", e altre ").append(da.size() - quante).append(" cose");
        b.append(".");
        return b.toString();
    }

    /**
     * La lista, detta.
     *
     * <b>Quattro righe al massimo.</b> Una voce che elenca dieci cose non si
     * ascolta: si perde il filo alla terza e si va a guardare lo schermo, che e'
     * proprio quello che si stava cercando di evitare. Le altre le dice il
     * numero in fondo.
     */
    private String leggiLeNote() {
        java.util.List<Appunti.Nota> da = appunti == null
                ? new java.util.ArrayList<Appunti.Nota>() : appunti.daFare();
        if (da.isEmpty()) return risposte.di("nota.vuota");
        StringBuilder b = new StringBuilder();
        int quante = Math.min(4, da.size());
        b.append(quante == 1 ? "Una cosa: " : "Hai " + da.size() + " cose da fare. ");
        for (int i = 0; i < quante; i++) {
            if (i > 0) b.append(i == quante - 1 ? " e " : ", ");
            b.append(da.get(i).testo);
        }
        b.append(".");
        return b.toString();
    }

    /** Gli impegni, detti: i primi due, che sono quelli su cui si puo' fare
     *  ancora qualcosa. */
    private String leggiGliImpegni() {
        if (calendario == null) return risposte.di("agenda.senza");
        Calendario.Impegno[] p = calendario.prossimi();
        if (p.length == 0) {
            return calendario.guasto() == Calendario.TUTTO_BENE
                    ? risposte.di("agenda.niente") : risposte.di("agenda.senza");
        }
        StringBuilder b = new StringBuilder();
        int quanti = Math.min(2, p.length);
        for (int i = 0; i < quanti; i++) {
            if (i > 0) b.append(" Poi, ");
            b.append(Calendario.detto(p[i])).append(".");
        }
        return b.toString();
    }

    // --- conversazione ------------------------------------------------------

    /**
     * Le domande che non chiedono di fare niente.
     *
     * <b>Perche' ci sono.</b> Un apparecchio che risponde "non ho capito" a
     * "come ti chiami" non sembra limitato: sembra rotto. Sono cinque righe di
     * tabella, non costano niente, e sono le prime cose che chiunque prova
     * quando gli metti davanti una cosa che parla.
     *
     * Le risposte sono corte e vere. Niente battute preconfezionate su quanto
     * e' bravo: se una cosa non la sa fare - il meteo, per esempio - lo dice.
     */
    private boolean provaConversazione(String f) {
        if (contiene(f, "come ti chiami", "chi sei", "il tuo nome", "qual e il tuo nome",
                     "ti chiami")) {
            // Finche' il modello di Marvin non e' installato, la parola che
            // sveglia e' un'altra: dirlo qui evita l'unica domanda che
            // altrimenti si farebbe chiunque - "e allora perche' non risponde
            // se lo chiamo per nome?".
            rispondi(risposte.di("saluti.nome"));
            return true;
        }
        if (contiene(f, "chi ti ha fatto", "chi ti ha creato", "chi ti ha programmato",
                     "da dove vieni")) {
            rispondi(risposte.di("saluti.origine"));
            return true;
        }
        if (contiene(f, "quanti anni hai", "da quanto tempo", "da quanto sei qui")) {
            rispondi(risposte.di("saluti.eta"));
            return true;
        }
        if (contiene(f, "dove sei", "dove siamo", "dove ti trovi")) {
            rispondi(risposte.di("saluti.dove"));
            return true;
        }
        if (contiene(f, "come stai", "come va", "tutto bene", "come te la passi")) {
            rispondi(comeVa());
            return true;
        }
        if (contiene(f, "mi senti", "ci sei", "sei sveglio", "sei acceso")) {
            rispondi(risposte.di("saluti.presente"));
            return true;
        }
        if (contiene(f, "grazie", "ti ringrazio")) {
            rispondi(risposte.di("saluti.figurati"));
            return true;
        }
        if (contiene(f, "scusa", "scusami", "perdonami")) {
            rispondi(risposte.di("saluti.prego"));
            return true;
        }
        if (contiene(f, "ti voglio bene", "sei bravo", "sei brava", "bravo", "brava")) {
            rispondi(risposte.di("saluti.grazie"));
            return true;
        }
        // I saluti stanno in fondo fra i convenevoli: "buonanotte" da solo e'
        // un saluto, ma se c'e' una routine che si chiama cosi' vince quella -
        // ed e' piu' in basso nella catena, quindi qui non la si tocca.
        if (f.equals("ciao") || f.equals("ehi") || f.equals("buongiorno")
                || f.equals("buonasera") || f.equals("salve")) {
            rispondi(saluto());
            return true;
        }
        if (contiene(f, "che tempo fa", "che tempo c'e", "piove", "meteo",
                     "quanti gradi")) {
            rispondi(risposte.di("meteo.assente"));
            return true;
        }
        return false;
    }

    /** Un saluto che tiene conto dell'ora: dire buongiorno alle undici di sera
     *  e' la cosa che fa sembrare finta una voce. */
    private String saluto() {
        int ora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (ora < 5) return "Ciao. E' tardi.";
        if (ora < 13) return "Buongiorno.";
        if (ora < 18) return "Buon pomeriggio.";
        return "Buonasera.";
    }

    /** "Come va" con dentro quello che sta succedendo davvero in casa: e' una
     *  risposta, non una formula. */
    private String comeVa() {
        StringBuilder b = new StringBuilder("Tutto a posto");
        int accese = luci != null ? luci.accese() : 0;
        boolean suona = radio.staSuonando() || (musica != null && musica.staSuonando());
        if (accese > 0 && suona) {
            b.append(": ").append(accese == 1 ? "una luce accesa" : accese + " luci accese")
             .append(" e qualcosa che suona");
        } else if (accese > 0) {
            b.append(": ").append(accese == 1 ? "una luce accesa" : accese + " luci accese");
        } else if (suona) {
            b.append(", e c'e' della musica");
        }
        return b.append(".").toString();
    }

    // --- quando non si capisce ----------------------------------------------

    /**
     * Quello che si dice quando nessuna regola ha preso la frase.
     *
     * <b>Non "non ho capito" e basta.</b> Quella risposta non aiuta chi ha
     * parlato: non gli dice se il problema e' che ha detto una cosa che Casa
     * non sa fare, o che l'ha detta in un modo che non riconosce. Quasi sempre
     * e' la seconda, e spesso e' una parola sola storta - il riconoscitore
     * scrive "raddio", "sveglio", "tiner". Qui si cerca la parola nota piu'
     * vicina e si propone la frase giusta.
     *
     * <b>E la frase si scrive nel registro</b>, con l'etichetta che il
     * programma sul PC gia' guarda: cosi' quello che Casa non capisce si puo'
     * leggere dopo qualche giorno d'uso, invece di immaginarlo. Le regole
     * nuove si scrivono su quelle, non su quello che sembrava probabile.
     */
    private void nonHoCapito(String f) {
        android.util.Log.i(MainActivity.TAG, "PC nonCapito " + f);
        String consiglio = piuVicino(f);
        if (consiglio != null) {
            // {parola} e' la frase che si sta suggerendo. E' l'unica risposta
            // del catalogo che ha un pezzo che cambia, e va scritta cosi'
            // perche' senza il suggerimento non direbbe niente di utile: chi la
            // riscrive deve lasciarcelo dentro.
            rispondi(risposte.di("saluti.forse").replace("{parola}", consiglio));
        } else {
            rispondi(risposte.di("saluti.nonso"));
        }
    }

    /**
     * Le parole che Casa conosce, e la frase da suggerire per ognuna.
     * L'ordine non conta: vince la piu' vicina.
     */
    private static final String[][] VICINANZE = {
        { "radio",     "metti la radio" },
        { "stazione",  "metti la radio" },
        { "musica",    "metti la musica" },
        { "spotify",   "metti la musica" },
        { "canzone",   "cerca una canzone" },
        { "playlist",  "metti la musica" },
        { "luce",      "accendi la luce" },
        { "luci",      "spegni le luci" },
        { "lampada",   "accendi la luce" },
        { "timer",     "metti un timer di dieci minuti" },
        { "sveglia",   "sveglia alle sette" },
        { "volume",    "alza il volume" },
        { "netflix",   "apri netflix" },
        { "film",      "apri netflix" },
        { "silenzio",  "silenzio" },
        { "avanti",    "avanti" },
        { "giorno",    "che giorno e" },
        { "nota",      "prendi nota di comprare il pane" },
        { "lista",     "che ho da fare" },
        { "impegni",   "che impegni ho" },
        { "agenda",    "che impegni ho" },
    };

    /** La frase da suggerire, se una parola della richiesta somiglia a una che
     *  Casa conosce. Null se non somiglia a niente. */
    private static String piuVicino(String frase) {
        String[] pezzi = frase.split("[^a-zA-Zàèéìòù]+");
        String migliore = null;
        int minimo = Integer.MAX_VALUE;
        for (String pezzo : pezzi) {
            // Sotto le quattro lettere ogni parola somiglia a ogni altra, e i
            // suggerimenti diventerebbero casuali.
            if (pezzo.length() < 4) continue;
            for (String[] v : VICINANZE) {
                int d = distanza(pezzo, v[0]);
                // Uno o due caratteri di differenza sono un errore di
                // trascrizione; tre sono un'altra parola.
                if (d <= 2 && d < minimo) { minimo = d; migliore = v[1]; }
            }
        }
        return migliore;
    }

    /**
     * Quante lettere bisogna cambiare per passare da una parola all'altra.
     *
     * Due righe di appoggio invece della matrice intera: le parole qui sono
     * corte, ma questo gira dentro una risposta a voce e non c'e' motivo di
     * allocare un rettangolo per confrontare "raddio" con "radio".
     */
    private static int distanza(String a, String b) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > 2) return 99;      // troppo diverse in lunghezza
        int[] prima = new int[m + 1];
        int[] dopo = new int[m + 1];
        for (int j = 0; j <= m; j++) prima[j] = j;
        for (int i = 1; i <= n; i++) {
            dopo[0] = i;
            for (int j = 1; j <= m; j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dopo[j] = Math.min(Math.min(dopo[j - 1] + 1, prima[j] + 1), prima[j - 1] + costo);
            }
            int[] scambio = prima; prima = dopo; dopo = scambio;
        }
        return prima[m];
    }

    /**
     * La playlist nominata nella frase, se ce n'e' una.
     *
     * <b>Questa e' la ricerca che si puo' fare.</b> Cercare un brano per nome
     * nel catalogo di Spotify vorrebbe dire l'endpoint di ricerca, che con il
     * token della sessione risponde 400 vuoto - e' dietro il client token, che
     * il demone tiene per se'. Quello che invece si sa fare e' riconoscere i
     * nomi che si hanno gia' in casa, esattamente come si fa con le
     * ventidue stazioni.
     *
     * Vince il nome piu' lungo fra quelli contenuti nella frase: se ci fossero
     * « Rock » e « Rock lento », "metti rock lento" deve prendere il secondo.
     * Sotto i tre caratteri non si guarda nemmeno: una playlist chiamata « E »
     * si troverebbe dentro qualunque frase.
     */
    private Preferiti.Voce playlistDetta(String frase) {
        if (musica == null || preferiti == null || musica.stato() != Musica.PRONTA) return null;
        Preferiti.Voce migliore = null;
        for (Preferiti.Voce v : preferiti.tutte()) {
            if (v.nome == null || v.nome.length() < 3) continue;
            String n = Testo.senzaAccenti(v.nome.toLowerCase(Locale.ITALIAN));
            if (!frase.contains(n)) continue;
            if (migliore == null || n.length() > migliore.nome.length()) migliore = v;
        }
        return migliore;
    }

    /**
     * Cerca nel catalogo e mette il primo risultato.
     *
     * <b>Il primo e basta, a voce.</b> Chi parla non sta guardando un elenco:
     * leggergli otto titoli sarebbe peggio che mettere quello giusto nove volte
     * su dieci e lasciargli dire "avanti". Da dito invece l'elenco si vede, ed
     * e' la sezione Musica a mostrarlo.
     */
    private void cercaEMetti(final String cosa) {
        if (musica == null || ricerca == null) { rispondi(risposte.di("musica.scollegata")); return; }
        if (!ricerca.pronta()) {
            rispondi(risposte.di("musica.senzachiave"));
            return;
        }
        if (cosa == null || cosa.length() == 0) { rispondi(risposte.di("musica.cosacerco")); return; }
        final boolean parlato = daVoce;
        rispondi("Cerco " + cosa + ".");
        ricerca.cerca(cosa, new Cerca.Esito() {
            @Override public void trovati(Cerca.Trovato[] risultati, String perche) {
                daVoce = parlato;
                if (risultati == null || risultati.length == 0) {
                    rispondi(perche != null ? perche : "Non ho trovato niente.");
                    return;
                }
                Cerca.Trovato primo = risultati[0];
                musica.suona(primo.uri);
                rispondi("Metto " + primo.titolo
                         + (primo.sotto != null && primo.sotto.length() > 0
                            ? " di " + primo.sotto : "") + ".");
            }
        });
    }

    /**
     * "metti Levante su spotify", "riproduci Battisti", "musica di Vasco".
     *
     * Mette la playlist <b>This Is</b> dell'artista, che e' quella che Spotify
     * tiene aggiornata con le sue canzoni piu' ascoltate: chi dice "metti
     * Levante" non vuole una canzone sola, vuole sentire Levante.
     *
     * <b>Se quella playlist non c'e' si ripiega sulla ricerca normale</b>
     * invece di rispondere che non ha trovato niente: gli artisti piccoli non
     * hanno una This Is, ma hanno le canzoni - e "metti Tizio" con Tizio che
     * esiste deve suonare qualcosa.
     */
    private boolean provaArtista(String f) {
        String chi = null;

        // "metti/riproduci/suona X su spotify"
        Matcher m = Pattern.compile(
                "(?:metti|riproduci|suona|fammi sentire|voglio sentire)\\s+(.{2,40}?)\\s+su spotify")
                .matcher(f);
        if (m.find()) chi = m.group(1);

        // "musica di X", "canzoni di X"
        if (chi == null) {
            m = Pattern.compile("(?:musica|canzoni|brani|roba)\\s+di\\s+(.{2,40})$").matcher(f);
            if (m.find()) chi = m.group(1);
        }
        if (chi == null) return false;

        // Via gli articoli davanti al nome: "metti i Negramaro su spotify".
        chi = chi.trim();
        for (String a : new String[] { "i ", "gli ", "le ", "la ", "il ", "l'", "un ", "una " }) {
            if (chi.startsWith(a)) { chi = chi.substring(a.length()).trim(); break; }
        }
        if (chi.length() < 2) return false;

        if (musica == null || ricerca == null) { rispondi(risposte.di("musica.scollegata")); return true; }
        if (!ricerca.pronta()) {
            rispondi(risposte.di("musica.senzachiave"));
            return true;
        }

        final String artista = chi;
        final boolean parlato = daVoce;
        rispondi("Metto " + artista + ".");
        ricerca.questoE(artista, new Cerca.Esito() {
            @Override public void trovati(Cerca.Trovato[] risultati, String perche) {
                daVoce = parlato;
                if (risultati != null && risultati.length > 0) {
                    musica.suona(risultati[0].uri);
                    return;   // il "Metto X" l'abbiamo gia' detto
                }
                if (perche != null) { rispondi(perche); return; }
                // Nessuna This Is: si cerca normalmente, senza ridire niente.
                daVoce = parlato;
                cercaEMetti(artista);
            }
        });
        return true;
    }

    /** Quel che viene dopo la parola chiave: "cerca io non sono io" -> "io non
     *  sono io". Senza, si cercherebbe anche la parola "cerca". */
    private static String dopoLaParola(String frase, String... chiavi) {
        for (String c : chiavi) {
            int dove = frase.indexOf(c);
            if (dove >= 0) return frase.substring(dove + c.length()).trim();
        }
        return frase;
    }

    private boolean contiene(String frase, String... chiavi) {
        for (String c : chiavi) if (frase.contains(c)) return true;
        return false;
    }

    /**
     * Come {@link #contiene}, ma la parola dev'essere <b>intera</b>.
     *
     * Nasce da errori veri, tutti della stessa famiglia. La regola dell'ora
     * cercava "l'ora" dentro la frase, e « metti la sveglia all'ora di pranzo »
     * contiene "l'ora": Casa rispondeva che ore erano. Cercare un pezzo di
     * parola dentro una frase e' comodo finche' non capita la parola che ne
     * contiene un'altra, e allora il sintomo non e' un errore ma <b>un comando
     * che ne esegue un altro</b>.
     *
     * L'apostrofo conta come lettera, ed e' proprio il caso di "all'ora" e
     * "un'ora": senza, il confine cadrebbe li' in mezzo e non servirebbe a
     * niente.
     */
    private static boolean parole(String frase, String... chiavi) {
        for (String c : chiavi) if (parola(frase, c)) return true;
        return false;
    }

    private static boolean parola(String f, String p) {
        if (p.length() == 0) return false;
        int da = 0;
        while (da + p.length() <= f.length()) {
            int i = f.indexOf(p, da);
            if (i < 0) return false;
            int fine = i + p.length();
            boolean prima = i == 0 || !attaccata(f.charAt(i - 1));
            boolean dopo = fine >= f.length() || !attaccata(f.charAt(fine));
            if (prima && dopo) return true;
            da = i + 1;
        }
        return false;
    }

    private static boolean attaccata(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '\u2019';
    }

    // --- che cosa si vuole, e a che cosa ------------------------------------

    /**
     * Le parole con cui si chiede di <b>fermare</b> qualcosa.
     *
     * <b>Questo elenco e' la cura di un errore che faceva il contrario.</b>
     * Prima ogni comando era una frase intera scritta a mano - "spegni la
     * radio", "ferma la radio", "basta radio" - e bastava dirla in un modo non
     * previsto perche' cadesse piu' in basso, dove la regola della radio e'
     * generosa: prende qualunque frase che contenga la parola "radio" e
     * <b>accende</b>. « stop radio » accendeva la radio. « togli la musica »
     * la metteva.
     *
     * Il verbo si guarda per primo, e vale in tutte le sue forme: sono le sei
     * o sette parole con cui una persona chiede di smettere, non le venti
     * frasi in cui si possono infilare.
     */
    private static final String[] SPEGNERE = {
        "spegni", "spegnere", "spegnila", "spegnilo", "spegnili", "spegnile",
        "stop", "stoppa", "stoppala", "stoppalo",
        "ferma", "fermare", "fermala", "fermalo", "fermati", "interrompi",
        "basta", "togli", "toglila", "chiudi", "smetti", "smettila",
        "zitto", "zitta", "taci", "silenzio", "muto", "spenta", "spento",
    };

    private static boolean chiedeDiSpegnere(String f) { return parole(f, SPEGNERE); }

    /** La frase parla della radio? Basta il nome di una stazione: a voce una
     *  radio la si chiama per nome, non la si annuncia. */
    private boolean nominaLaRadio(String f) {
        return parole(f, "radio", "stazione", "stazioni") || radio.riconosci(f) != null;
    }

    private boolean nominaLaMusica(String f) {
        return parole(f, "musica", "spotify", "canzone", "canzoni", "brano",
                      "brani", "playlist", "disco", "album");
    }

    /** La frase parla di lampade? Le luci hanno le loro regole, e questa serve
     *  a lasciargliele: senza, « spegni la luce » spegnerebbe la radio e
     *  « abbassa la luce » abbasserebbe il volume. */
    private boolean nominaLuci(String f) {
        return contiene(f, "luce", "luci", "lampad", "abat", "faretto", "plafoniera")
            || (luci != null && luci.riconosci(f) != null);
    }

    /**
     * Tutti i modi di dire « smettila »: con un bersaglio o senza.
     *
     * <h3>Con un bersaglio</h3>
     *
     * « spegni la radio », « stop radio », « togli la musica », « basta con
     * Deejay »: si ferma quello che e' stato nominato.
     *
     * <h3>Senza</h3>
     *
     * « stop », « basta », « zitto », « silenzio »: si ferma <b>quello che sta
     * facendo rumore</b>, e nient'altro. Prima questa frase fermava anche i
     * timer, ed era una sorpresa: un timer non fa rumore finche' non scade, e
     * chi dice « basta » alla radio non si aspetta di ritrovarsi la pasta senza
     * conto alla rovescia. Se invece una sveglia sta suonando <b>proprio in
     * quel momento</b>, quella viene prima di tutto: e' l'unica cosa che chi
     * dice « basta » puo' avere in mente.
     *
     * <h3>Cosa lascia stare</h3>
     *
     * Le luci, i timer e le sveglie nominati per nome: hanno le loro regole,
     * che sanno spegnere le loro cose e hanno bisogno del resto della frase.
     * E le <b>routine scritte da chi abita qui</b>: una che si chiama « Chiudi
     * le tapparelle » contiene "chiudi", e senza questa riga finirebbe qui
     * invece che dove qualcuno l'ha scritta.
     *
     * <h3>Perche' quasi tutto quello che risponde e' scritto e non detto</h3>
     *
     * Perche' spegnere si sente. Vedi {@link #segna(String)}: l'unica risposta
     * a voce e' quella di quando non c'era proprio niente da fermare, che e'
     * l'unico caso in cui non succede niente che si possa sentire.
     */
    private boolean provaSpegni(String f) {
        if (!chiedeDiSpegnere(f)) return false;
        if (nominaLuci(f) || parole(f, "timer", "sveglia", "sveglie", "allarme")) return false;
        if (luci != null && luci.riconosciRoutine(f) != null) return false;

        if (nominaLaRadio(f)) {
            boolean cera = radio.staSuonando();
            radio.spegni();
            if (cera) segna(risposte.di("radio.spenta"));
            else rispondi(risposte.di("radio.giaspenta"));
            return true;
        }

        if (nominaLaMusica(f)) {
            boolean cera = musica != null && musica.staSuonando();
            if (musica != null) musica.ferma();
            if (cera) segna(risposte.di("musica.ferma"));
            else rispondi(risposte.di("musica.niente"));
            return true;
        }

        // Una sveglia che sta suonando adesso viene prima di qualunque altra
        // cosa: e' il rumore che ha fatto parlare.
        if (orologio.staSuonando()) {
            orologio.taci();
            segna(risposte.di("sveglia.zitta"));
            return true;
        }

        boolean qualcosa = radio.staSuonando() || (musica != null && musica.staSuonando());
        if (qualcosa) {
            radio.spegni();
            if (musica != null) musica.ferma();
            segna(risposte.di("tutto.fermo"));
            return true;
        }

        rispondi(risposte.di("niente.dafermare"));
        return true;
    }

    // --- luci ---------------------------------------------------------------

    /**
     * I colori che si possono dire a una lampada.
     *
     * <b>Il piu' lungo vince</b>, ed e' il motivo per cui "verde acqua" sta
     * prima di "verde": cercando in ordine, "accendi la camera verde acqua"
     * troverebbe prima "verde" e metterebbe il verde normale.
     *
     * Sono tinte sature, non quelle della tavolozza di {@link Tinte}: quelle
     * sono fatte per essere lette su uno schermo, queste per essere viste in
     * una stanza. Un rosso da interfaccia, messo su una lampadina, fa rosa.
     */
    private static final String[][] COLORI = {
        { "verde acqua",  "#00FFCC" },
        { "blu notte",    "#101080" },
        { "rosso",        "#FF0000" },
        { "verde",        "#00FF00" },
        { "blu",          "#0000FF" },
        { "giallo",       "#FFD400" },
        { "arancione",    "#FF7A00" },
        { "arancio",      "#FF7A00" },
        { "viola",        "#8A2BE2" },
        { "lilla",        "#C8A2C8" },
        { "rosa",         "#FF69B4" },
        { "fucsia",       "#FF00C8" },
        { "magenta",      "#FF00FF" },
        { "azzurro",      "#00BFFF" },
        { "celeste",      "#87CEEB" },
        { "turchese",     "#40E0D0" },
        { "indaco",       "#4B0082" },
        { "corallo",      "#FF7F50" },
        { "ambra",        "#FFBF00" },
        { "oro",          "#FFD700" },
        { "bordeaux",     "#800020" },
    };

    /** Il colore nominato nella frase, o zero. Vince il nome piu' lungo. */
    private static int coloreIn(String frase) {
        for (String[] c : COLORI) {
            if (frase.contains(c[0])) return Tinte.leggi(c[1], 0);
        }
        return 0;
    }

    /**
     * Le lampade e le routine. Torna vero se la frase era per loro.
     *
     * <b>Pretende un verbo o un nome</b>, e non basta la parola "luce": "che
     * luce c'e' fuori" non deve spegnere niente. E il verbo si guarda prima del
     * nome della routine, se no "spegni tutte le luci" - che contiene "tutte" -
     * finirebbe ad accenderle tutte.
     */
    private boolean provaLuci(String f) {
        if (luci == null) return false;

        Lampada quale = luci.riconosci(f);
        boolean parlaDiLuci = nominaLuci(f);
        boolean spegnere = contiene(f, "spegni", "spegnere", "spegnile");
        boolean accendere = contiene(f, "accendi", "accendere", "accendile");

        // Il colore viene prima di acceso/spento, o "accendi la camera di
        // rosso" si fermerebbe ad accenderla bianca: contiene "accendi", e
        // quella regola non guarda il resto della frase.
        if (parlaDiLuci || quale != null) {
            int rgb = coloreIn(f);
            if (rgb != 0 && !spegnere) {
                if (quale != null) {
                    if (quale.stato != null && quale.stato.raggiunta && !quale.stato.haColore) {
                        rispondi(quale.nome + " non fa i colori.");
                        return true;
                    }
                    luci.colore(quale, rgb);
                    rispondi(quale.nome + " di " + nomeColore(f) + ".");
                } else {
                    for (Lampada l : luci.elenco()) luci.colore(l, rgb);
                    rispondi("Luci di " + nomeColore(f) + ".");
                }
                return true;
            }

            // "bianco" non e' un colore da mandare come RGB: le lampade hanno
            // un canale bianco loro, che rende molto meglio di un RGB a
            // 255,255,255 - e su quelle che non fanno colori e' l'unico.
            if (contiene(f, "bianco", "bianca") && !spegnere) {
                if (quale != null) { luci.bianco(quale); rispondi(quale.nome + " bianca."); }
                else { for (Lampada l : luci.elenco()) luci.bianco(l); rispondi(risposte.di("luci.bianche")); }
                return true;
            }

            // Caldo e freddo sono la temperatura del bianco, non una tinta.
            boolean caldo = contiene(f, "calda", "caldo", "piu' calda", "piu calda");
            boolean freddo = contiene(f, "fredda", "freddo", "piu' fredda", "piu fredda");
            if ((caldo || freddo) && !spegnere) {
                int quanto = caldo ? 5 : 95;
                if (quale != null) { luci.temperatura(quale, quanto); rispondi(quale.nome + (caldo ? " calda." : " fredda.")); }
                else { for (Lampada l : luci.elenco()) luci.temperatura(l, quanto); rispondi(caldo ? "Luci calde." : "Luci fredde."); }
                return true;
            }
        }

        if ((parlaDiLuci || quale != null) && (spegnere || accendere)) {
            boolean on = accendere && !spegnere;
            if (quale != null) {
                luci.accendi(quale, on);
                rispondi((on ? "Accendo " : "Spengo ") + quale.nome + ".");
            } else {
                luci.tutte(on);
                rispondi(on ? "Accendo le luci." : "Spengo le luci.");
            }
            return true;
        }

        // "abbassa la luce", "alza la luce": un passo di venti punti da dov'e'
        // adesso.
        //
        // <b>Mancava, e mancava male.</b> La frase non trovava nessuna regola
        // qui dentro, cadeva fino in fondo alla catena e finiva nel volume, che
        // "abbassa" ce l'ha anche lui: chi diceva « abbassa la luce » si
        // sentiva abbassare la musica. Adesso e' il volume a tirarsi indietro
        // quando la frase nomina una lampada, ed e' questa a prendersela.
        if ((parlaDiLuci || quale != null)
                && parole(f, "alza", "alzala", "aumenta", "abbassa", "abbassala",
                          "diminuisci", "piu forte", "piu' forte", "piu piano",
                          "piu' piano", "piu alta", "piu' alta", "piu bassa", "piu' bassa")
                && numeroIn(f) < 0) {
            boolean su = parole(f, "alza", "alzala", "aumenta", "piu forte", "piu' forte",
                                "piu alta", "piu' alta");
            if (quale != null) {
                rispondi(passoLuce(quale, su));
            } else {
                for (Lampada l : luci.elenco()) passoLuce(l, su);
                rispondi(su ? "Luci piu' forti." : "Luci piu' basse.");
            }
            return true;
        }

        // "luce al trenta per cento", "comodino al cinquanta".
        if ((parlaDiLuci || quale != null) && contiene(f, "per cento", "%", " al ")) {
            int quanto = numeroIn(f);
            if (quanto > 0 && quanto <= 100) {
                if (quale != null) {
                    luci.luminosita(quale, quanto);
                    rispondi(quale.nome + " al " + quanto + " per cento.");
                } else {
                    for (Lampada l : luci.elenco()) luci.luminosita(l, quanto);
                    rispondi("Luci al " + quanto + " per cento.");
                }
                return true;
            }
        }

        Routine r = luci.riconosciRoutine(f);
        if (r != null) {
            luci.esegui(r);
            rispondi(r.nome + ".");
            return true;
        }
        return false;
    }

    /**
     * Una lampada di venti punti piu' su o piu' giu'.
     *
     * Venti e non dieci: sotto quella soglia, su una lampadina, la differenza
     * non si vede - e chi non vede niente lo ridice, ottenendo due passi che
     * insieme sarebbero stati uno solo giusto.
     *
     * Quando la lampada non ha ancora detto a che luminosita' sta - non e'
     * stata ancora interrogata, o non la regola affatto - non si indovina un
     * valore di partenza: si va a un terzo o al massimo, che sono i due posti
     * dove si voleva arrivare nove volte su dieci.
     */
    private String passoLuce(Lampada l, boolean su) {
        int adesso = (l.stato != null && l.stato.raggiunta) ? l.stato.luminosita : -1;
        int quanto;
        if (adesso < 0) quanto = su ? 100 : 30;
        else quanto = Math.max(5, Math.min(100, adesso + (su ? 20 : -20)));
        luci.luminosita(l, quanto);
        return l.nome + " al " + quanto + " per cento.";
    }

    /** Il nome del colore come lo si e' detto, per ripeterlo nella risposta. */
    private static String nomeColore(String frase) {
        for (String[] c : COLORI) if (frase.contains(c[0])) return c[0];
        return "colore";
    }

    // --- riproduzione ----------------------------------------------------

    /**
     * Avanti, indietro, pausa, riprendi. Torna vero se la frase era per loro.
     *
     * <b>Chi comanda e' quello che sta suonando.</b> "avanti" con la radio
     * accesa vuol dire la stazione dopo, con Spotify vuol dire il brano dopo:
     * sono due cose diverse che si dicono con la stessa parola, e l'unico modo
     * di non sbagliare e' guardare cosa c'e' in funzione invece di indovinare
     * dalla frase.
     */
    /**
     * Le parole con cui si scorre invece di mettere.
     *
     * Stanno qui e non dentro il metodo perche' le guarda anche la regola della
     * radio, che sta piu' in alto nella catena: « cambia stazione » nomina la
     * stazione, e senza questo controllo la radio se la prendeva e
     * <b>accendeva la prima dell'elenco</b> invece di passare alla successiva.
     * Un elenco solo, letto da tutti e due, e' l'unico modo perche' non
     * divergano il giorno che se ne aggiunge una.
     *
     * "skip" e "next" sono le uniche non italiane: si dicono lo stesso, e non
     * entrano dentro nessuna parola nostra.
     */
    private static final String[] SCORRI_AVANTI = {
        "avanti", "prossima", "prossimo", "successiv", "salta", "skip", "next",
        "cambia canzone", "cambia stazione", "cambia brano", "un'altra",
    };
    private static final String[] SCORRI_INDIETRO = {
        "indietro", "precedente", "torna indietro", "quella di prima", "rimetti quella",
    };
    private static final String[] SCORRI_PAUSA = { "pausa", "metti in pausa", "aspetta" };
    private static final String[] SCORRI_RIPRENDI = {
        "riprendi", "continua", "vai avanti cosi", "riparti",
    };
    private static final String[] SCORRI_CASO = { "casuale", "mischia", "a caso", "shuffle" };

    /** La frase chiede di muoversi dentro quello che gia' suona, invece di
     *  mettere qualcosa? */
    private boolean chiedeDiScorrere(String f) {
        return contiene(f, SCORRI_AVANTI) || contiene(f, SCORRI_INDIETRO)
            || contiene(f, SCORRI_PAUSA) || contiene(f, SCORRI_RIPRENDI)
            || contiene(f, SCORRI_CASO);
    }

    private boolean provaRiproduzione(String f) {
        boolean avanti = contiene(f, SCORRI_AVANTI);
        boolean indietro = contiene(f, SCORRI_INDIETRO);
        boolean pausa = contiene(f, SCORRI_PAUSA);
        boolean riprendi = contiene(f, SCORRI_RIPRENDI);
        boolean mischia = contiene(f, SCORRI_CASO);

        if (!avanti && !indietro && !pausa && !riprendi && !mischia) return false;

        boolean laRadio = radio.staSuonando();
        boolean laMusica = musica != null && musica.ceUnBrano();

        // Questa invece resta a voce: che l'ordine sia cambiato non si sente
        // finche' non finisce il brano che sta suonando.
        if (mischia && laMusica) {
            musica.mischia(true);
            rispondi(risposte.di("musica.acaso"));
            return true;
        }

        // Da qui in giu' quasi niente si dice a voce, e la ragione e' sempre
        // quella: la musica che riparte, che si ferma o che cambia brano <b>si
        // sente</b>. Una voce che lo annuncia arriva dopo il fatto e per
        // parlare abbassa proprio quello che si stava ascoltando. Vedi segna().
        if (riprendi && laMusica) {
            if (!musica.staSuonando()) musica.pausaRiprendi();
            segna(risposte.di("musica.riprendo"));
            return true;
        }

        if (pausa) {
            if (laMusica && musica.staSuonando()) { musica.pausaRiprendi(); segna(risposte.di("musica.pausa")); return true; }
            // La radio non si mette in pausa: e' un flusso, quando torni sei
            // comunque piu' avanti. Si spegne, che e' quello che si vuole.
            if (laRadio) { radio.spegni(); segna(risposte.di("radio.spenta")); return true; }
            rispondi(risposte.di("musica.niente"));
            return true;
        }

        if (avanti || indietro) {
            if (laRadio) {
                if (avanti) radio.successiva(); else radio.precedente();
                // La stazione invece si dice: il nome e' l'unica cosa che chi
                // ha parlato non puo' sapere, e sentirla non basta a
                // riconoscerla.
                rispondi(radio.nomeCorrente() != null
                         ? "Metto " + radio.nomeCorrente() + "."
                         : risposte.di("radio.cambio"));
                return true;
            }
            if (laMusica) {
                if (avanti) musica.successivo(); else musica.precedente();
                segna(risposte.di(avanti ? "musica.avanti" : "musica.indietro"));
                return true;
            }
            rispondi(risposte.di("musica.niente"));
            return true;
        }
        return false;
    }

    /** Cosa sta suonando adesso, per chi lo chiede. */
    private String cosaSuona() {
        if (radio.staSuonando()) {
            String n = radio.nomeCorrente();
            return n != null ? "Sta suonando " + n + "." : "C'e' la radio.";
        }
        if (musica != null && musica.ceUnBrano()) {
            String b = musica.brano();
            if (!musica.staSuonando()) {
                return b != null ? b + ", in pausa." : "La musica e' in pausa.";
            }
            return b != null ? "Sta suonando " + b + "." : "C'e' la musica.";
        }
        return "Non sta suonando niente.";
    }

    /** Radio, musica, timer e luci: tutto giu'. */
    private void spegniTutto() {
        radio.spegni();
        if (musica != null) musica.ferma();
        orologio.fermaTuttiITimer();
        if (luci != null) luci.tutte(false);
        rispondi(risposte.di("tutto.spento"));
    }

    // --- i comandi scritti dal PC ----------------------------------------

    /**
     * Quanto in profondita' siamo dentro un comando scritto dal PC.
     *
     * Serve contro un cerchio che si chiude da solo: un comando puo' avere fra
     * i suoi passi una <b>frase</b>, la frase rientra da {@link #esegui}, e se
     * quella frase contiene la parola che ha fatto scattare il comando, il
     * comando riparte. Non e' un caso di scuola: la regola « buonanotte » che
     * fra i passi dice « buonanotte a tutti » ci cade subito, e il sintomo
     * sarebbe il tablet che si blocca e la voce che ripete la stessa frase
     * finche' non lo si stacca.
     *
     * Un solo livello: dentro un comando dell'utente, le frasi passano ai
     * comandi scritti nel codice e non tornano piu' qui.
     */
    private int dentroUtente;

    private boolean provaUtente(String f) {
        if (utente == null || dentroUtente > 0) return false;
        ComandiUtente.Comando c = utente.riconosci(f);
        if (c == null) return false;

        Log.i(MainActivity.TAG, "comandi: \"" + f + "\" la prende "
                + ComandiUtente.descrizione(c));
        dentroUtente++;
        try {
            if (!c.azione.passi.isEmpty() && luci != null) luci.esegui(c.azione);
            String detto = utente.risposta(c);
            if (detto.length() > 0) rispondi(riempi(detto));
        } finally {
            dentroUtente--;
        }
        return true;
    }

    /**
     * I segnaposto dentro una risposta scritta dal PC.
     *
     * Sono pochi apposta: una risposta che sa dire l'ora copre il caso vero -
     * « che ore sono » detto in un altro modo - e ogni segnaposto in piu' e' una
     * cosa da spiegare a chi scrive la regola. Quelli che non esistono restano
     * scritti come sono: cosi' un errore di battitura si vede a schermo invece
     * di sparire.
     */
    private String riempi(String testo) {
        if (testo.indexOf('{') < 0) return testo;
        String s = testo;
        if (s.contains("{ora}"))   s = s.replace("{ora}", oraParlata());
        if (s.contains("{data}"))  s = s.replace("{data}", dataParlata());
        if (s.contains("{suona}")) s = s.replace("{suona}", cosaSuona());
        if (s.contains("{luci}"))  s = s.replace("{luci}", luci == null ? "" : luci.riassunto());
        if (s.contains("{nome}"))  s = s.replace("{nome}", NOME);
        return s;
    }

    // --- ora e data ------------------------------------------------------

    /**
     * L'ora, detta come la direbbe una persona.
     *
     * "Sono le 0 e 5" e "Sono le 1" non li dice nessuno, e li diceva Casa: a
     * mezzanotte e all'una l'italiano cambia forma, e sono proprio le due ore
     * in cui qualcuno chiede che ore sono perche' e' sveglio e non dovrebbe.
     * Anche la mezza si dice mezza.
     */
    private String oraParlata() {
        Calendar c = Calendar.getInstance();
        int ore = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);

        String quante;
        if (ore == 0) quante = "E' mezzanotte";
        else if (ore == 1) quante = "E' l'una";
        else quante = "Sono le " + ore;

        if (min == 0) return quante + (ore == 0 ? "." : " in punto.");
        if (min == 30) return quante + " e mezza.";
        if (min == 15) return quante + " e un quarto.";
        return quante + " e " + min + ".";
    }

    private String dataParlata() {
        Calendar c = Calendar.getInstance();
        String[] giorni = { "domenica", "lunedi", "martedi", "mercoledi",
                            "giovedi", "venerdi", "sabato" };
        String[] mesi = { "gennaio", "febbraio", "marzo", "aprile", "maggio",
                          "giugno", "luglio", "agosto", "settembre", "ottobre",
                          "novembre", "dicembre" };
        return "Oggi e " + giorni[c.get(Calendar.DAY_OF_WEEK) - 1] + " "
             + c.get(Calendar.DAY_OF_MONTH) + " " + mesi[c.get(Calendar.MONTH)] + ".";
    }

    // --- timer -----------------------------------------------------------

    /**
     * "timer di dieci minuti", "un'ora e mezza", "due minuti e trenta secondi",
     * "un quarto d'ora".
     *
     * <b>Le durate si sommano, non si sceglie un numero solo.</b> Prima si
     * prendeva il primo numero della frase e si guardava se da qualche parte
     * c'era scritto "or" o "second": "un'ora e mezza" diventava un timer di
     * un'ora, e i trenta minuti sparivano senza che nessuno lo dicesse.
     */
    private void avviaTimer(String frase) {
        long durata = durataIn(frase);
        if (durata <= 0) { rispondi(risposte.di("timer.diquanto")); return; }
        if (durata > 24 * 3600L) { rispondi(risposte.di("timer.troppolungo")); return; }
        orologio.avviaTimer(durata);
        rispondi("Timer di " + Orologio.durataInParole(durata) + ".");
    }

    /**
     * Quanti secondi dice questa frase.
     *
     * Si percorrono tutte le coppie numero-unita' e si sommano. L'unita' viene
     * <b>dopo</b> il numero, com'e' in italiano ("due minuti"), quindi a ogni
     * numero si guarda il pezzo di frase che lo segue fino al numero
     * successivo.
     *
     * <h3>Contano solo i numeri che hanno un'unita' addosso</h3>
     *
     * E' la regola che evita l'errore piu' stupido possibile, misurato sul
     * tablet: <i>"metti un timer di un'ora e mezza"</i> dava un timer di
     * <b>un'ora e trentuno</b>. L'articolo "un" di "un timer" era diventato la
     * cifra 1, quel numero non aveva nessuna unita' vicino, e la vecchia regola
     * - "un numero senza unita' vale minuti" - gli regalava sessanta secondi.
     *
     * Il numero senza unita' vale minuti <b>solo se e' l'unico</b>: e' il caso
     * di "timer di dieci", che vuol dire dieci minuti. Se nella frase c'e'
     * almeno un numero con la sua unita', gli altri sono articoli o rumore e si
     * lasciano perdere.
     */
    private long durataIn(String frase) {
        String f = frase;

        // "un quarto d'ora" e "mezz'ora" sono modi di dire, non conti: si
        // tolgono prima di guardare i numeri, o "un quarto" diventerebbe un
        // timer di un minuto.
        long fisse = 0;
        for (String q : new String[] { "un quarto d'ora", "un quarto d ora", "quarto d'ora" }) {
            if (f.contains(q)) { fisse += 15 * 60L; f = f.replace(q, " "); }
        }
        for (String q : new String[] { "mezz'ora", "mezz ora", "mezzora" }) {
            if (f.contains(q)) { fisse += 30 * 60L; f = f.replace(q, " "); }
        }

        f = inCifre(f);

        Matcher m = Pattern.compile("(\\d+)").matcher(f);
        long conUnita = 0;
        boolean qualcunaConUnita = false;
        int soloNumero = -1;
        int quantiNumeri = 0;

        int[] valori = new int[12];
        int[] dopo = new int[12];
        int quanti = 0;
        while (m.find() && quanti < valori.length) {
            try {
                valori[quanti] = Integer.parseInt(m.group(1));
                dopo[quanti] = m.end();
                quanti++;
            } catch (Exception storto) { }
        }

        for (int i = 0; i < quanti; i++) {
            int finePezzo = (i + 1 < quanti) ? dopo[i + 1] : f.length();
            // Il pezzo fra questo numero e il prossimo: li' dentro c'e' la sua
            // unita', se e' stata detta.
            String coda = f.substring(Math.min(dopo[i], f.length()),
                                      Math.min(Math.max(finePezzo, dopo[i]), f.length()));
            long unita;
            if (coda.contains("second")) unita = 1;
            else if (coda.contains("minut")) unita = 60;
            else if (coda.contains("or")) unita = 3600;      // ora, ore
            else unita = 0;                                   // nessuna unita'

            quantiNumeri++;
            if (unita == 0) { soloNumero = valori[i]; continue; }

            qualcunaConUnita = true;
            conUnita += valori[i] * unita;
            // "un'ora e mezza": il "mezza" senza numero vale meta' dell'unita'
            // che lo precede.
            if (coda.contains("mezza") || coda.contains("mezzo")) conUnita += unita / 2;
        }

        if (qualcunaConUnita) return conUnita + fisse;
        if (fisse > 0) return fisse;
        // Nessuna unita' detta: vale solo se il numero era uno solo.
        if (quantiNumeri == 1 && soloNumero > 0) return soloNumero * 60L;
        return -1;
    }

    /**
     * I numeri scritti a parole, in cifre.
     *
     * <b>"un" e "una" si convertono solo se subito dopo c'e' un'unita'</b>:
     * sono articoli molto piu' spesso che numeri, e "un timer" trasformato in
     * "1 timer" e' esattamente il minuto di troppo raccontato sopra.
     */
    private static String inCifre(String frase) {
        String f = frase;

        // "un'ora", "un ora", "una mezz'ora": qui "un" e' un numero.
        f = Pattern.compile("\\bun[a']?\\s*(or[ae]|minut[oi]|second[oi])")
                   .matcher(f).replaceAll("1 $1");

        // I piu' lunghi per primi: "quattordici" contiene "quattro", e
        // convertendo prima "quattro" resterebbe "4dici".
        String[][] parole = {
            { "diciassette", "17" }, { "quattordici", "14" }, { "diciannove", "19" },
            { "quindici", "15" }, { "diciotto", "18" }, { "cinquanta", "50" },
            { "quaranta", "40" }, { "sessanta", "60" }, { "settanta", "70" },
            { "ottanta", "80" }, { "novanta", "90" }, { "tredici", "13" },
            { "quattro", "4" }, { "sedici", "16" }, { "cinque", "5" },
            { "dodici", "12" }, { "undici", "11" }, { "trenta", "30" },
            { "dieci", "10" }, { "venti", "20" }, { "sette", "7" },
            { "nove", "9" }, { "otto", "8" }, { "sei", "6" },
            { "tre", "3" }, { "due", "2" },
        };
        for (String[] pz : parole) f = f.replace(pz[0], pz[1]);
        return f;
    }

    // --- sveglie -----------------------------------------------------------

    /**
     * "sveglia alle 7", "svegliami alle 6 e mezza", "sveglia alle 7:30".
     *
     * Si prende solo l'ora, non i giorni: dirli a voce vorrebbe una frase
     * lunga che nessuno ricorda, e i giorni sono proprio la cosa che si tocca
     * comoda nella sezione Orologio. Quindi la voce mette una sveglia " una
     * volta sola ", che e' anche il caso piu' frequente - domani mattina.
     */
    private void metti(String frase) {
        Matcher m = Pattern.compile("(\\d{1,2})\\s*(?::|\\.|\\se\\s)?\\s*(\\d{1,2})?").matcher(frase);
        int ora = -1, minuto = 0;
        if (m.find()) {
            try {
                ora = Integer.parseInt(m.group(1));
                if (m.group(2) != null) minuto = Integer.parseInt(m.group(2));
            } catch (Exception storto) {
                ora = -1;
            }
        }
        if (frase.contains("mezza") || frase.contains("mezzo")) minuto = 30;
        if (frase.contains("un quarto")) minuto = 15;
        if (ora < 0 || ora > 23 || minuto > 59) { rispondi(risposte.di("sveglia.acheora")); return; }

        // "sveglia alle 7" alle nove di sera vuol dire le sette di domattina,
        // non le sette di stasera che sono gia' passate: e' quandoSuona() a
        // portarla al primo momento buono, e qui non c'e' niente da indovinare.
        orologio.aggiungiSveglia(ora, minuto, 0);
        rispondi("Sveglia alle " + ora + (minuto == 0 ? "" : " e " + minuto) + ".");
    }

    /**
     * Spegne tutte le sveglie accese. Torna vero se ce n'era almeno una.
     *
     * <b>Quella che sta suonando adesso viene per prima.</b> Prima non c'era, e
     * il sintomo era il peggiore possibile: « spegni la sveglia » gridato a una
     * sveglia che suonava la disattivava per domani e la lasciava suonare
     * adesso.
     */
    private boolean spegniLeSveglie() {
        boolean qualcuna = false;
        if (orologio.staSuonando()) { orologio.taci(); qualcuna = true; }
        for (Orologio.Sveglia s : orologio.sveglie()) {
            if (!s.attiva) continue;
            orologio.accendiSveglia(s, false);
            qualcuna = true;
        }
        return qualcuna;
    }

    /**
     * Il primo numero dentro la frase, in cifre o in lettere.
     *
     * Serve alle cose che di numero ne vogliono <b>uno</b> - la luminosita', il
     * volume - non alle durate: quelle si sommano, e passano da
     * {@link #durataIn}.
     */
    private int numeroIn(String frase) {
        Matcher m = Pattern.compile("\\d+").matcher(inCifre(frase));
        if (m.find()) {
            try { return Integer.parseInt(m.group()); } catch (Exception ignorato) { }
        }
        if (frase.contains("mezz")) return 30;      // "mezz'ora"
        return -1;
    }

    // --- volume e app ----------------------------------------------------

    /**
     * "volume al 50", "metti il volume a 7".
     *
     * Due scale nella stessa frase: se il numero e' oltre il massimo dello
     * stream lo si legge come una percentuale, se no come una tacca. Sul
     * tablet il massimo e' quindici, quindi "volume a 7" e "volume al 50" sono
     * tutti e due sensati e vogliono dire cose vicine.
     */
    private void volumeA(int quanto) {
        if (audio == null) { rispondi(risposte.di("volume.bloccato")); return; }
        int massimo = audio.getStreamMaxVolume(TuboAudio.flusso());
        int tacca;
        if (quanto > massimo) {
            if (quanto > 100) { rispondi(risposte.di("volume.fuoriscala")); return; }
            tacca = Math.round(massimo * quanto / 100f);
        } else {
            tacca = quanto;
        }
        mettiVolume(Math.max(0, Math.min(massimo, tacca)));
    }

    /**
     * Un decimo su o giu', <b>come i due tasti della Home</b>.
     *
     * Prima la voce spostava due gradini di sistema - che qui sono quindici in
     * tutto, quindi tredici per cento - mentre il tasto ne sposta dieci. Due
     * comandi che fanno la stessa cosa con due misure diverse si notano piu' di
     * una scomodita' voluta: il decimo e' quello che si vede scritto sulla
     * Home, e adesso e' anche quello che si sente dicendolo.
     */
    private void volumePasso(int verso) {
        if (audio == null) { rispondi(risposte.di("volume.bloccato")); return; }
        int massimo = audio.getStreamMaxVolume(TuboAudio.flusso());
        if (massimo <= 0) { rispondi(risposte.di("volume.bloccato")); return; }
        int decine = Math.round(audio.getStreamVolume(TuboAudio.flusso()) * 10f / massimo);
        decine = Math.max(0, Math.min(10, decine + verso));
        mettiVolume(Math.round(massimo * decine / 10f));
    }

    /**
     * Mette il volume, e <b>lo fa sentire invece di dirlo</b>.
     *
     * Se qualcosa sta suonando, la prova che il comando e' arrivato e' quello
     * che si sta ascoltando: piu' forte, o piu' piano. Se non sta suonando
     * niente, alzare il volume non produce nessun suono - e allora si chiede al
     * sistema il suo « tac », che e' esattamente il verso che fanno i tasti sul
     * fianco del tablet e che nessuno deve imparare.
     *
     * A schermo resta il numero. A voce non si dice niente: il perche' sta su
     * {@link #segna(String)}.
     */
    private void mettiVolume(int tacca) {
        boolean qualcosaSuona = radio.staSuonando() || (musica != null && musica.staSuonando());
        try {
            audio.setStreamVolume(TuboAudio.flusso(), tacca,
                    qualcosaSuona ? 0 : AudioManager.FLAG_PLAY_SOUND);
        } catch (SecurityException vietato) {
            // Con « non disturbare » acceso il sistema puo' rifiutarsi. Questa
            // si dice: e' l'unico caso in cui il comando non ha fatto niente.
            rispondi(risposte.di("volume.bloccato"));
            return;
        }
        int massimo = audio.getStreamMaxVolume(TuboAudio.flusso());
        segna("Volume " + (massimo > 0 ? Math.round(tacca * 100f / massimo) : 0) + "%");
    }

    private void apri(String pacchetto, String nome) {
        Intent i = contesto.getPackageManager().getLaunchIntentForPackage(pacchetto);
        if (i == null) { rispondi(nome + " non e installato."); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        contesto.startActivity(i);
        rispondi("Apro " + nome + ".");
    }

    // --- le stesse cose, ma col dito ------------------------------------
    //
    // Il pannello chiama questi invece di passare da una finta frase: un
    // tocco e' gia' inequivocabile, farlo fingere di essere parlato
    // aggiungerebbe solo un giro di riconoscimento che puo' sbagliare.

    /** La musica, le playlist e la ricerca, quando MainActivity le ha
     *  collegate. */
    public void setMusica(Musica m, Preferiti p, Cerca c) {
        musica = m; preferiti = p; ricerca = c;
    }

    public boolean radioAccesa() { return radio.staSuonando(); }
    public boolean timerAttivo() { return orologio.qualcheTimer(); }
    public String  nomeRadio()   { return radio.nomeCorrente(); }

    public void toccaRadio() {
        daVoce = false;
        if (radio.staSuonando()) { radio.spegni(); rispondi(risposte.di("radio.spenta")); }
        else { radio.accendi(null); rispondi(risposte.di("radio.messa")); }
    }

    public void toccaMusica() {
        daVoce = false;
        metticiLaMusica();
    }

    /**
     * "Casa, metti la musica".
     *
     * Tre casi, in ordine di quanto e' probabile che sia quello giusto: se c'e'
     * gia' un brano in pausa si riprende da li'; se non c'e' ma il motore e'
     * pronto si mette la prima playlist; se il motore non e' ancora acceso lo
     * si accende e si manda avanti chi ha chiesto, perche' collegarsi a Spotify
     * dura qualche secondo e rispondere "un momento" e' meglio che restare
     * zitti.
     */
    private void metticiLaMusica() {
        if (musica == null) { rispondi(risposte.di("musica.scollegata")); return; }
        switch (musica.stato()) {
            case Musica.PRONTA:
                if (musica.ceUnBrano()) {
                    if (!musica.staSuonando()) musica.pausaRiprendi();
                    rispondi(risposte.di("musica.rimetto"));
                } else if (preferiti != null && preferiti.tutte().length > 0) {
                    Preferiti.Voce prima = preferiti.tutte()[0];
                    musica.suona(prima.uri);
                    rispondi("Metto " + prima.nome + ".");
                } else {
                    rispondi(risposte.di("musica.scegli"));
                }
                break;
            case Musica.ACCOPPIA:
                rispondi(risposte.di("musica.approva"));
                break;
            case Musica.GUASTO:
                rispondi(risposte.di("musica.nonparte"));
                break;
            default:
                musica.avvia();
                rispondi(risposte.di("musica.accendo"));
                break;
        }
    }
    public void toccaVideo()  { apri("com.netflix.mediaclient", "Netflix"); }

    public void chiudi() {
        // I timer NON si fermano qui. Prima si', perche' vivevano dentro questa
        // classe e sarebbero rimasti a girare a vuoto; adesso stanno in
        // AlarmManager, e un timer messo cinque minuti fa deve suonare anche se
        // l'Activity nel frattempo e' stata rifatta.
        //
        // chiudi() e non spegni() sulla radio: oltre a fermare lo stream
        // rilascia la sessione media. Lasciarla attiva su un processo morto
        // vuol dire che i tasti media del sistema continuano a puntare a un
        // fantasma.
        radio.chiudi();
    }
}
