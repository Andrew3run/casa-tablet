package dev.casa;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * « Hey Home » detto in cucina, e Casa che si mette ad ascoltare.
 *
 * Tiene insieme i due pezzi: l'{@link Orecchio}, che apre il microfono e
 * consegna tratti di parlato, e la {@link ParolaChiave}, che dice quanto
 * somigliano alla parola. Qui in mezzo ci sono le decisioni che non
 * appartengono a nessuno dei due.
 *
 * <h3>Il microfono e' di uno solo</h3>
 *
 * Su Android il microfono non si divide: finche' lo tiene l'orecchio, il
 * riconoscitore di Google non parte. Quindi appena la parola aggancia, la
 * sequenza e' <b>spegni l'orecchio, poi apri il riconoscitore</b>, e non il
 * contrario. Ed e' anche il motivo per cui {@link Orecchio#spegni()} aspetta
 * davvero che il suo thread abbia mollato la presa.
 *
 * <h3>Il tempo morto dopo un aggancio</h3>
 *
 * Dopo che la parola ha agganciato passa qualche secondo prima di ricominciare
 * a cercarla. Senza, la coda della stessa frase - « ...Home, accendi la luce »
 * - farebbe scattare un secondo aggancio mentre il riconoscitore sta gia'
 * lavorando sulla prima.
 *
 * <h3>Mentre Casa parla, l'orecchio e' in pausa</h3>
 *
 * La voce di Casa esce da venti centimetri sotto il microfono ed e' la cosa
 * piu' forte che si senta in tutta la stanza: e' il falso aggancio piu' facile
 * che esista. Non si spegne il microfono - riaccenderlo costa mezzo secondo -
 * si smette di confrontare.
 *
 * <h3>La prova</h3>
 *
 * In modalita' prova nessun aggancio sveglia niente: si consegna solo il
 * punteggio, per vederlo scorrere nel pannello mentre si dice la parola da
 * vicino e da lontano. E' l'unico modo di scegliere una soglia guardandola
 * invece di indovinarla.
 */
public final class Risveglio implements Orecchio.Ascolto {

    /** Sotto {@code Casa} come tutto il resto, col nome del pezzo nel
     *  messaggio: il perche' sta su {@link MainActivity#TAG}. */
    private static final String TAG = MainActivity.TAG;

    /** Quanto si sta zitti dopo un aggancio. */
    private static final long TEMPO_MORTO_MS = 4000;

    /** Cosa succede quando la parola aggancia. */
    public interface Sveglia {
        /** Detta. Da qui si apre il riconoscitore. Arriva sul thread
         *  dell'interfaccia. */
        void suParola();
        /** Il livello del microfono, per la barra del pannello. Sul thread
         *  dell'interfaccia, cinquanta volte al secondo. */
        void suLivello(float livello, boolean voce);
        /**
         * Ogni confronto: il punteggio della rete, la sua soglia, quante
         * finestre di fila sono passate, la distanza dalle registrazioni di
         * casa, e se ha agganciato.
         *
         * <b>I due numeri servono a capire di chi e' la colpa</b> quando non
         * aggancia. Se la rete non passa mai, il problema e' il primo stadio.
         * Se la rete passa e le impronte dicono di no, quella pronuncia non
         * assomiglia abbastanza a quelle registrate - e la cura non e' una
         * soglia, e' registrarne un'altra detta proprio cosi'. Con un numero
         * solo si girerebbe la manopola sbagliata.
         *
         * {@code distanza} vale {@link ParolaChiave#LONTANO} quando il secondo
         * stadio non e' stato nemmeno chiesto: la rete aveva gia' detto di no.
         */
        void suPunteggio(float punteggio, float soglia, int diFila,
                         float distanza, boolean preso);
    }

    private final Context contesto;
    private final Campioni campioni;
    private final Sordina sordina;
    private final Sveglia sveglia;
    private final Orecchio orecchio;
    private final ParolaChiave parola = new ParolaChiave();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Campioni.Stato stato;
    private volatile boolean inProva;
    private volatile long ultimoAggancio;

    /**
     * La rete allenata su voci che non sono le nostre.
     *
     * Quando c'e', comanda lei: riconosce chiunque, e i campioni in
     * {@code parola/si} restano solo come riserva - se un giorno il modello
     * sparisse dagli assets, Casa tornerebbe al confronto a impronte senza
     * smettere di funzionare.
     */
    private volatile Rete rete;

    /** Quante finestre di fila sono gia' passate sopra soglia. */
    private int diFila;

    /** Un tono corto quando aggancia. E' la risposta che dice « ti ho sentito »
     *  prima ancora che il riconoscitore sia pronto: senza, fra la parola e la
     *  scritta a schermo passa mezzo secondo di niente, e in quel mezzo secondo
     *  si ripete la parola. */
    private ToneGenerator tono;

    public Risveglio(Context c, Campioni campioni, Sordina sordina, Sveglia sveglia) {
        this.contesto = c;
        this.campioni = campioni;
        this.sordina = sordina;
        this.sveglia = sveglia;
        this.orecchio = new Orecchio(c, this);
        this.stato = campioni.leggiStato();
        parola.setSoglia(stato.soglia);
        parola.setRincaro(stato.rincaro);
    }

    public Campioni.Stato stato() { return stato; }
    public ParolaChiave parola() { return parola; }
    public Orecchio orecchio() { return orecchio; }
    public boolean acceso() { return orecchio.acceso(); }
    public boolean pronta() { return rete != null || parola.pronta(); }

    /**
     * Rilegge le impostazioni salvate e le applica a caldo.
     *
     * <b>Se cambia la cancellazione d'eco il microfono va riaperto.</b> La
     * sorgente - VOICE_RECOGNITION o VOICE_COMMUNICATION - si sceglie
     * nell'istante in cui si apre AudioRecord, e non si cambia dopo. Senza
     * questa chiusura, {@code accendi()} trovava l'orecchio gia' acceso e
     * usciva subito: l'impostazione risultava cambiata dappertutto - a
     * schermo, nel file, nella risposta al PC - e il microfono continuava a
     * registrare come prima. Il pannello lo sapeva e lo faceva a mano; il
     * comando che arriva dal PC no, e non cambiava niente in silenzio.
     */
    public void applica(Campioni.Stato nuovo) {
        boolean ecoCambiata = stato != null && stato.eco != nuovo.eco;
        stato = nuovo;
        campioni.scriviStato(nuovo);
        parola.setSoglia(nuovo.soglia);
        parola.setRincaro(nuovo.rincaro);
        if (ecoCambiata && orecchio.acceso()) {
            Log.i(TAG, "parola: eco " + (nuovo.eco ? "accesa" : "spenta")
                     + ", riapro il microfono");
            orecchio.spegni();
        }
        if (nuovo.accesa) accendi(); else spegni();
    }

    // ---- accensione --------------------------------------------------------

    /**
     * Carica i modelli fuori dal thread dell'interfaccia e poi accende.
     *
     * Leggere venti WAV e calcolarne le impronte sono qualche centinaio di
     * millisecondi: fatti qui sarebbero qualche centinaio di millisecondi di
     * orologio fermo, all'avvio, cioe' proprio quando si guarda.
     */
    public void carica(final Runnable poi) {
        new Thread(new Runnable() {
            @Override public void run() {
                // Prima i campioni che arrivano dentro l'app, se non ci sono
                // ancora: un tablet appena installato deve riconoscere la
                // parola senza che nessuno registri niente. Vedi
                // Campioni.portaIBase.
                campioni.portaIBase();
                if (rete == null) rete = Rete.daAsset(contesto, "parola/marvin.rete");
                parola.ricarica(campioni);
                adattaFinestra();
                if (poi != null) ui.post(poi);
            }
        }, "Casa-modelli").start();
    }

    /**
     * La finestra da confrontare, lunga quanto la parola registrata.
     *
     * Non e' un numero da scegliere: e' quanto dura « Hey Home » detto da chi
     * vive qui, e lo dicono i campioni. Un po' larga - un quinto in piu' - per
     * lasciare spazio a una ripetizione detta piu' piano del solito.
     */
    private void adattaFinestra() {
        // Con la rete la finestra non si sceglie: e' quella su cui e' stata
        // allenata, un secondo esatto. Darle qualcosa di piu' corto vorrebbe
        // dire darle meno righe di quelle che si aspetta, e lei risponde zero.
        if (rete != null) {
            orecchio.setFinestraMs(rete.finestraMs() + 30);
            orecchio.setFinestraIntera(true);
            return;
        }
        orecchio.setFinestraIntera(false);
        int righe = parola.durataTipica();
        if (righe <= 0) return;
        orecchio.setFinestraMs((int) (righe * Mfcc.PASSO_MS * 1.2f));
    }

    /**
     * Se dopo la rete c'e' anche il confronto a impronte.
     *
     * Vuole tutte e due le cose: la rete che filtra, e almeno un campione
     * registrato in casa con cui confrontare. Senza campioni il confronto
     * direbbe « lontano » a qualunque cosa e il tablet non si sveglierebbe
     * mai - quindi in quel caso comanda la sola rete, con le sue soglie.
     *
     * <h3>E di serie i campioni non ci sono, apposta</h3>
     *
     * Il secondo stadio toglie quasi tutti i risvegli a vuoto, ma li toglie
     * <b>legando la parola a una voce sola</b>: confronta con delle
     * registrazioni, quindi risponde a chi le ha fatte, con il tono con cui le
     * ha fatte. Chi passa di casa, un ospite, la stessa persona raffreddata o
     * di fretta - non lo sveglierebbero.
     *
     * La rete no: e' allenata su duemila voci di sconosciuti e non conosce
     * nessuno in particolare, che e' esattamente il motivo per cui e' stata
     * fatta. Prende l'83% delle parole con zero risvegli a vuoto all'ora, e
     * quel 83% vale per <b>chiunque</b>.
     *
     * Quindi la cascata resta scritta e funzionante, ma si accende solo se
     * qualcuno mette dei campioni nella cartella: e' una scelta - la voce di
     * uno contro la voce di tutti - non un valore di fabbrica.
     */
    public boolean conConferma() {
        return rete != null && parola.quantiModelli() > 0;
    }

    /** Comanda la rete, o il confronto a impronte? */
    public boolean conLaRete() { return rete != null; }

    /** La parola che sveglia Casa: dipende da chi ascolta. */
    public String frase() { return rete != null ? "Marvin" : Campioni.FRASE; }

    public Rete rete() { return rete; }

    public void accendi() {
        if (!pronta()) {
            Log.i(TAG, "parola: nessun campione: la parola resta spenta");
            return;
        }
        orecchio.accendi(stato.eco);
        // Toglie la pausa, che puo' essere rimasta da chi ha aperto il
        // microfono per registrare invece che per agganciare: senza questa
        // riga, chiudere il pannello delle impostazioni lascerebbe un
        // microfono acceso che non confronta niente - e la parola sembrerebbe
        // accesa a schermo e morta nei fatti.
        orecchio.pausa(false);
    }

    /**
     * Apre il microfono anche senza modelli.
     *
     * Lo usa il pannello: i primi campioni si registrano quando di modelli non
     * ce n'e' ancora nessuno, e {@link #accendi()} li' si rifiuterebbe - il che
     * renderebbe impossibile arrivare ad averne.
     */
    public void accendiPerRegistrare() {
        orecchio.accendi(stato.eco);
        orecchio.pausa(true);
    }

    /** Rifa' i modelli adesso, su questo thread. Non dal thread
     *  dell'interfaccia: sono qualche centinaio di millisecondi. */
    public void ricaricaOra() {
        parola.ricarica(campioni);
        adattaFinestra();
    }

    public ParolaChiave.Taratura tara() { return parola.tara(campioni); }

    /** Come e' andata una registrazione: il file, o il motivo per cui no. */
    public interface Presa {
        void suFinita(java.io.File dove, int millisecondi, String guaio);
    }

    /**
     * Registra un campione e lo salva, senza passare dal pannello.
     *
     * <b>Stava dentro la vela</b>, ed e' rimasto li' finche' l'unico modo di
     * registrare era toccare il tablet. Serve anche da fuori: la misura della
     * cancellazione d'eco vuole due registrazioni fatte nelle stesse
     * condizioni, una con l'eco accesa e una spenta, e a colpi di dito non
     * sarebbero mai le stesse condizioni.
     *
     * Il rumore si salva intero - dentro ci deve stare tutto quello che la
     * stanza fa, silenzi compresi - la parola invece si ritaglia sul parlato
     * (vedi {@link Orecchio#ritaglia}, dove c'e' il perche' un campione non
     * ritagliato non aggancia mai).
     */
    public void registraCampione(final int tipo, final int durataMs,
                                 final String etichetta, final Presa esito) {
        if (!orecchio.acceso()) accendiPerRegistrare();
        orecchio.registra(durataMs, new Orecchio.Presa() {
            @Override public void suAvanzamento(int rimastiMs, float livello, float picco) { }
            @Override public void suPresa(final short[] suono, final int quanti) {
                if (suono == null || quanti <= 0) {
                    finita(esito, null, 0, "il microfono non ha dato niente");
                    return;
                }
                new Thread(new Runnable() {
                    @Override public void run() {
                        int da = 0, lung = quanti;
                        if (tipo == Campioni.SI) {
                            int[] estremi = Orecchio.ritaglia(suono, quanti);
                            if (estremi == null) {
                                finita(esito, null, 0, "non ho sentito niente");
                                return;
                            }
                            da = estremi[0];
                            lung = estremi[1];
                        }
                        short[] pezzo;
                        if (da == 0 && lung == quanti) {
                            pezzo = suono;
                        } else {
                            pezzo = new short[lung];
                            System.arraycopy(suono, da, pezzo, 0, lung);
                        }
                        java.io.File dove = campioni.prossimo(tipo, etichetta);
                        if (!Onda.scrivi(dove, pezzo, lung)) {
                            finita(esito, null, 0, "non sono riuscito a scrivere");
                            return;
                        }
                        finita(esito, dove, lung * 1000 / Onda.HZ, null);
                    }
                }, "Casa-campione").start();
            }
        });
    }

    private void finita(final Presa esito, final java.io.File dove,
                        final int ms, final String guaio) {
        if (esito == null) return;
        ui.post(new Runnable() {
            @Override public void run() { esito.suFinita(dove, ms, guaio); }
        });
    }

    public void spegni() { orecchio.spegni(); }

    /** Ferma il confronto senza chiudere il microfono. */
    public void pausa(boolean p) {
        orecchio.pausa(p);
        // Le finestre di fila ricominciano da capo: un conteggio a meta'
        // ripreso mezzo minuto dopo non vuol dire niente.
        diFila = 0;
    }

    /**
     * Molla il microfono perche' lo prenda il riconoscitore, e lo riprende
     * dopo. Chi chiama passa il momento in cui ha finito.
     */
    public void cedi() {
        if (orecchio.acceso()) orecchio.spegni();
    }

    public void riprendi() {
        if (stato.accesa && parola.pronta() && !orecchio.acceso()) orecchio.accendi(stato.eco);
    }

    public void chiudi() {
        orecchio.spegni();
        if (tono != null) { try { tono.release(); } catch (Throwable ignorata) { } tono = null; }
    }

    // ---- la prova ----------------------------------------------------------

    public void setProva(boolean p) {
        inProva = p;
        ultimoAggancio = 0;
    }

    public boolean inProva() { return inProva; }

    // ---- quello che arriva dall'orecchio ----------------------------------

    @Override
    public void suFinestra(short[] suono, int quanti) {
        // Sul thread del microfono, di proposito: qui dentro c'e' la DTW, che
        // e' il calcolo piu' pesante di Casa. Sul thread dell'interfaccia
        // sarebbe un fotogramma perso ogni volta che qualcuno parla.
        if (!inProva && System.currentTimeMillis() - ultimoAggancio < TEMPO_MORTO_MS) return;

        boolean suona = sordina != null && sordina.qualcunoSuona();
        final float soglia;
        final float punteggio;
        final boolean sopra;
        final int righe;

        if (rete != null) {
            // Con la rete il punteggio va nel verso opposto: e' un "quanto
            // somiglia" da zero a uno, e serve che sia ALTO.
            float[][] impronta = parola.impronta(suono, 0, quanti);
            righe = impronta == null ? 0 : impronta.length;
            soglia = stato.sogliaRete;
            punteggio = impronta == null ? 0f : rete.punteggio(impronta);
            sopra = punteggio >= soglia;
        } else {
            righe = Mfcc.quanteFinestre(quanti);
            soglia = parola.sogliaAdesso(suona);
            punteggio = parola.punteggio(suono, 0, quanti);
            sopra = punteggio <= soglia;
        }

        // Quante finestre di fila. Una parola vera ne accende cinque o sei
        // consecutive, un falso allarme una o due: e' una differenza che
        // costa poco e toglie molti risvegli a vuoto, e con la musica accesa
        // se ne chiede una in piu'. Misurato in pc/parola/difila.py, non
        // dedotto - due finestre consecutive si sovrappongono all'88%, quindi
        // i loro errori NON sono indipendenti e il conto "falsi al quadrato"
        // e' una bugia.
        int quante = rete != null ? Math.max(1, stato.difila + (suona ? 1 : 0)) : 1;
        if (sopra) diFila++; else diFila = 0;
        boolean passa = sopra && diFila >= quante;

        // ---- il secondo controllo -------------------------------------
        //
        // La rete da sola non basta in questa casa, e non e' una soglia
        // girata male. Misurato onestamente - rifinendo su cinque
        // registrazioni e provando sulla sesta - o si sveglia da sola
        // trenta volte all'ora, o prende una parola su quattro. Sei
        // registrazioni non bastano a insegnarle una voce.
        //
        // Allora ne servono due, e devono sbagliare su cose diverse:
        //
        //   la RETE sa cos'e' « marvin » in generale (duemila voci) e non sa
        //   niente di chi vive qui; il CONFRONTO A IMPRONTE non sa niente di
        //   « marvin » in generale, ma sa tutto delle registrazioni fatte in
        //   questa cucina.
        //
        // Il primo filtra il mondo, il secondo riconosce la persona: perche'
        // un falso allarme passi devono sbagliare tutti e due sulla stessa
        // mezza parola. Misura in pc/parola/cascata.py: rete generosa piu'
        // impronte porta il richiamo al 100% e i risvegli a vuoto a zero,
        // dove la sola rete non passava il 27%.
        //
        // Il confronto lavora sul RITAGLIO, non sulla finestra intera: i
        // modelli sono registrazioni ritagliate strette sulla parola, e
        // confrontarle con un secondo pieno di stanza vuol dire farsi dire
        // di no sempre.
        float conferma = ParolaChiave.LONTANO;
        if (passa && conConferma()) {
            int da = 0, lung = quanti;
            int[] estremi = Orecchio.ritaglia(suono, quanti);
            if (estremi != null) { da = estremi[0]; lung = estremi[1]; }
            conferma = parola.punteggio(suono, da, lung);
            if (conferma > stato.soglia) passa = false;
        }

        final float distanza = conferma;
        final boolean preso = passa;
        if (preso) diFila = 0;

        final int quanteDiFila = diFila;
        // In prova si scrive ogni punteggio nel registro: e' l'unico modo di
        // vedere dal PC cosa sente il tablet in una stanza vera, senza essere
        // nella stanza.
        if (inProva) {
            // Le righe dell'impronta ci sono perche' e' stato proprio quel
            // numero a spiegare gli zeri: sotto le righe che la rete vuole,
            // risponde zero senza dire niente.
            Log.i(TAG, "PC prova punteggio=" + ParolaChiave.arrotonda(punteggio)
                     + " soglia=" + ParolaChiave.arrotonda(soglia)
                     + " difila=" + diFila + " righe=" + righe
                     + (rete != null ? "/" + rete.righe() : "")
                     + (conConferma()
                        ? " impronte=" + ParolaChiave.arrotonda(distanza)
                          + "/" + ParolaChiave.arrotonda(stato.soglia)
                        : "")
                     + " preso=" + preso);
        }
        if (inProva) {
            ui.post(new Runnable() {
                @Override public void run() {
                    sveglia.suPunteggio(punteggio, soglia, quanteDiFila, distanza, preso);
                }
            });
            return;
        }
        if (!preso) return;

        ultimoAggancio = System.currentTimeMillis();
        Log.i(TAG, "parola: agganciata: " + ParolaChiave.arrotonda(punteggio)
                 + (rete != null ? " sopra " : " sotto ") + ParolaChiave.arrotonda(soglia)
                 + ", " + quanteDiFila + " finestre di fila"
                 + (conConferma()
                    ? ", impronte " + ParolaChiave.arrotonda(distanza)
                      + " sotto " + ParolaChiave.arrotonda(stato.soglia)
                    : "")
                 + (suona ? " (con qualcosa che suona)" : ""));
        din();
        ui.post(new Runnable() {
            @Override public void run() {
                sveglia.suPunteggio(punteggio, soglia, quanteDiFila, distanza, true);
                sveglia.suParola();
            }
        });
    }

    @Override
    public void suLivello(final float livello, final boolean voce) {
        // Solo mentre qualcuno guarda: cinquanta post al secondo sul thread
        // dell'interfaccia, per sempre, sono cinquanta risvegli al secondo di
        // una coda che altrimenti dormirebbe.
        if (!ascoltatoreDelLivello) return;
        ui.post(new Runnable() {
            @Override public void run() { sveglia.suLivello(livello, voce); }
        });
    }

    /** Il pannello lo accende quando e' in scena, e lo spegne uscendo. */
    private volatile boolean ascoltatoreDelLivello;

    public void setMostraLivello(boolean b) { ascoltatoreDelLivello = b; }

    private void din() {
        try {
            if (tono == null) tono = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            tono.startTone(ToneGenerator.TONE_PROP_ACK, 90);
        } catch (Throwable t) {
            // Su qualche ROM ToneGenerator non si crea quando l'audio e'
            // occupato. Non e' un motivo per non ascoltare.
            Log.w(TAG, "parola: tono non suonato", t);
        }
    }
}
