package dev.casa;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Timer e sveglie: quelli veri, non il conto alla rovescia che viveva dentro
 * {@link Comandi}.
 *
 * Quello era uno solo, di cinque minuti fissi, e girava su un {@code Handler}:
 * bastava che Android facesse fuori il processo - o che qualcuno staccasse
 * l'alimentatore - perche' sparisse senza dire niente. Un apparecchio a cui si
 * chiede di svegliare qualcuno non puo' dipendere da un ciclo di messaggi che
 * vive quanto la sua finestra.
 *
 * <h3>Le tre cose che li rendono veri</h3>
 *
 * <b>AlarmManager, non un Handler.</b> Il conto alla rovescia sullo schermo
 * resta un tick al secondo - serve a farlo vedere - ma quello che <i>sveglia</i>
 * e' un allarme registrato nel sistema, che scatta anche a processo morto e
 * anche a schermo spento.
 *
 * <b>{@code setAlarmClock} per le sveglie, {@code setExactAndAllowWhileIdle}
 * per i timer.</b> Non sono sinonimi: dalla 23 in poi il sistema, quando il
 * dispositivo sta fermo, raggruppa e rimanda gli allarmi per risparmiare
 * corrente. Solo questi due modi restano esatti, e {@code setAlarmClock} e' in
 * piu' l'unico che il sistema tratta come « una persona ha chiesto di essere
 * svegliata »: non lo sposta mai, e lo mostra come prossima sveglia.
 *
 * <b>Si riprogramma su BOOT_COMPLETED <i>e</i> su MY_PACKAGE_REPLACED.</b> Gli
 * allarmi registrati muoiono in tutti e due i casi. Il primo lo scrivono tutti;
 * il secondo e' quello che ci si dimentica, e in sviluppo cancella ogni sveglia
 * a ogni reinstallazione - in silenzio, e ci si accorge la mattina dopo. Se ne
 * occupa {@link RicevitoreAllarmi}.
 *
 * <h3>Lo stato sta su disco, non in memoria</h3>
 *
 * {@code orologio.json}, scritto con {@link Archivio}, cioe' in modo atomico:
 * la corrente che se ne va nel mezzo di una scrittura non deve poter lasciare
 * un file troncato, perche' un file troncato qui vuol dire tutte le sveglie
 * cancellate, e il primo che se ne accorge e' chi non si e' svegliato.
 */
public final class Orologio {

    private static final String TAG = "Casa.Orologio";

    private static final String FILE = "orologio.json";

    /** L'intento che il sistema rimanda indietro quando un allarme scatta. */
    public static final String AZIONE = "dev.casa.ALLARME";
    public static final String EXTRA_ID = "id";

    /** Di quanto sposta il « ancora un po' ». Cinque minuti: dieci sono un
     *  secondo sonno, due non bastano ad alzarsi. */
    private static final long RINVIO_MS = 5 * 60 * 1000L;

    private static final String[] GIORNI_BREVI = { "dom", "lun", "mar", "mer", "gio", "ven", "sab" };

    /**
     * Chi guarda l'orologio: la sezione che si ridisegna, e la regia che apre
     * la schermata di quando suona.
     */
    public interface Spia {
        /** Qualcosa e' cambiato: ridisegna. Arriva anche a ogni secondo, ma
         *  solo finche' c'e' un timer che scorre. */
        void suCambio();
        /** Sta suonando. Il titolo e' quello da mostrare e da dire. */
        void suScatto(String titolo, boolean sveglia);
        /** Ha smesso: via la schermata. */
        void suSilenzio();
    }

    /** Un conto alla rovescia. */
    public static final class Conto {
        public final int id;
        public final long durata;        // secondi
        public long scadenza;            // ms epoch

        Conto(int id, long durata, long scadenza) {
            this.id = id;
            this.durata = durata;
            this.scadenza = scadenza;
        }

        public long restano() {
            return Math.max(0, (scadenza - System.currentTimeMillis() + 999) / 1000);
        }

        /** Il nome del timer e' la sua durata: « 10 minuti ». Chiedere di
         *  battezzarlo sarebbe una tastiera in mezzo a un gesto che deve
         *  costare un tocco. */
        public String nome() { return durataInParole(durata); }
    }

    /** Una sveglia: ora, giorni, e se e' accesa. */
    public static final class Sveglia {
        public final int id;
        public int ora, minuto;
        /** Bit 0..6 = domenica..sabato. Zero vuol dire « una volta sola ». */
        public int giorni;
        public boolean attiva = true;
        /** Quando e' stata rimandata, il momento a cui risuona. Zero se no. */
        public long rinvio;

        /**
         * Il momento esatto, per la sveglia che fa da specchio a quella di un
         * telefono. Zero per tutte le altre.
         *
         * Serve perche' ora e minuto non bastano: la prossima sveglia del
         * telefono puo' essere lunedi' alle sette, e una « una volta sola » alle
         * sette suonerebbe domattina, che e' sabato.
         */
        public long il;

        /** L'identificativo del telefono di cui e' lo specchio, o vuoto. */
        public String telefono = "";

        Sveglia(int id, int ora, int minuto, int giorni) {
            this.id = id;
            this.ora = ora;
            this.minuto = minuto;
            this.giorni = giorni;
        }

        public boolean ripete() { return giorni != 0; }

        public boolean dalTelefono() { return telefono.length() > 0; }

        /** Toccata a mano sul tablet: da quel momento e' una sveglia sua, e la
         *  prossima sincronizzazione del telefono non se la riprende. */
        void diventaDelTablet() {
            il = 0;
            telefono = "";
        }

        public String orario() {
            return String.format(Locale.ITALIAN, "%02d:%02d", ora, minuto);
        }

        /** « lun mar mer », « tutti i giorni », « una volta sola ». */
        public String quando() {
            if (dalTelefono()) return "dal telefono";
            if (giorni == 0) return "una volta sola";
            if (giorni == 0x7F) return "tutti i giorni";
            if (giorni == 0x3E) return "da lunedi a venerdi";
            if (giorni == 0x41) return "sabato e domenica";
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if ((giorni & (1 << i)) != 0) {
                    if (b.length() > 0) b.append(' ');
                    b.append(GIORNI_BREVI[i]);
                }
            }
            return b.toString();
        }
    }

    private final Context contesto;
    private final AlarmManager allarmi;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Spia spia;

    private final List<Conto> conti = new ArrayList<Conto>();
    private final List<Sveglia> sveglie = new ArrayList<Sveglia>();
    private int prossimoId = 1;

    private Sveglia sveglaChesuona;
    private Conto contoChesuona;
    private final Suoneria suoneria;

    /**
     * Uno per processo, e non e' pigrizia.
     *
     * Un allarme puo' arrivare a {@link RicevitoreAllarmi} mentre l'Activity e'
     * viva, oppure con Casa appena riaccesa dal sistema. Se ognuno dei due si
     * costruisse il suo Orologio ci sarebbero due elenchi che si sovrascrivono
     * lo stesso file, e - peggio - due suonerie: una in mano al ricevitore che
     * suona, l'altra in mano allo schermo, dove il tasto « basta » zittirebbe
     * quella sbagliata. Con uno solo, chi arriva dopo trova quello che sta gia'
     * suonando.
     */
    private static Orologio istanza;

    public static synchronized Orologio di(Context c) {
        if (istanza == null) istanza = new Orologio(c);
        return istanza;
    }

    private Orologio(Context contesto) {
        this.contesto = contesto.getApplicationContext();
        this.allarmi = (AlarmManager) this.contesto.getSystemService(Context.ALARM_SERVICE);
        this.suoneria = new Suoneria(this.contesto);
        carica();
        riprogramma();
        battito();
    }

    /**
     * Chi guarda adesso. Si stacca in onDestroy e si riattacca in onCreate:
     * l'Orologio sopravvive all'Activity, perche' un allarme deve poter suonare
     * anche mentre l'interfaccia non c'e'.
     */
    public void setSpia(Spia s) {
        spia = s;
        if (s != null) {
            // Chi si riattacca deve sapere subito se c'e' qualcosa in corso:
            // se Casa e' stata riaperta proprio dall'allarme che sta suonando,
            // la schermata dev'esserci gia' al primo fotogramma.
            if (staSuonando()) {
                s.suScatto(sveglaChesuona != null
                        ? "Sveglia delle " + sveglaChesuona.orario()
                        : "Timer di " + contoChesuona.nome(),
                        sveglaChesuona != null);
            }
            s.suCambio();
            battito();
        }
    }

    /** C'e' un'interfaccia attaccata? Lo chiede il ricevitore per decidere se
     *  riaccendere Casa o se c'e' gia' chi mostra la schermata. */
    public boolean haQualcunoCheGuarda() { return spia != null; }

    public List<Conto> conti() { return conti; }

    public List<Sveglia> sveglie() { return sveglie; }

    // ---- timer ---------------------------------------------------------------

    /** Ne avvia uno. Piu' di uno alla volta si puo': la pasta e il forno sono
     *  due cose diverse e scadono in momenti diversi. */
    public Conto avviaTimer(long secondi) {
        if (secondi <= 0) return null;
        Conto c = new Conto(prossimoId++, secondi,
                System.currentTimeMillis() + secondi * 1000L);
        conti.add(c);
        salva();
        programmaConto(c);
        battito();
        cambiato();
        return c;
    }

    public void fermaTimer(Conto c) {
        if (c == null) return;
        annulla(c.id);
        conti.remove(c);
        salva();
        cambiato();
    }

    /** Ferma tutti quelli che scorrono. Torna vero se ce n'era almeno uno: e'
     *  quello che serve a « annulla il timer » per sapere se rispondere
     *  « annullato » o « non ce n'era nessuno ». */
    public boolean fermaTuttiITimer() {
        if (conti.isEmpty()) return false;
        for (Conto c : conti) annulla(c.id);
        conti.clear();
        salva();
        cambiato();
        return true;
    }

    /** Il primo che scade, o null. E' quello che la Home mostra: piu' di un
     *  numero grande sotto l'orologio non ci sta, e quello che interessa e'
     *  sempre il piu' vicino. */
    public Conto primoConto() {
        Conto migliore = null;
        for (Conto c : conti) {
            if (migliore == null || c.scadenza < migliore.scadenza) migliore = c;
        }
        return migliore;
    }

    public boolean qualcheTimer() { return !conti.isEmpty(); }

    // ---- sveglie --------------------------------------------------------------

    public Sveglia aggiungiSveglia(int ora, int minuto, int giorni) {
        Sveglia s = new Sveglia(prossimoId++, ora, minuto, giorni);
        sveglie.add(s);
        salva();
        programmaSveglia(s);
        cambiato();
        return s;
    }

    public void togliSveglia(Sveglia s) {
        annulla(s.id);
        sveglie.remove(s);
        salva();
        cambiato();
    }

    public void accendiSveglia(Sveglia s, boolean on) {
        s.attiva = on;
        s.rinvio = 0;
        // Uno specchio gia' suonato e riacceso a mano: il suo istante e' nel
        // passato, e un allarme nel passato scatta subito. Da qui e' una
        // « una volta sola » alla stessa ora, come chi la riaccende si aspetta.
        if (on && s.il > 0 && s.il <= System.currentTimeMillis()) s.diventaDelTablet();
        salva();
        if (on) programmaSveglia(s); else annulla(s.id);
        cambiato();
    }

    /**
     * Mette l'ora esatta. La usa il quadrante, che non sposta di un passo ma
     * porta direttamente al valore sotto l'indicatore.
     *
     * Si chiama <b>alla fine del trascinamento</b>, non a ogni scatto: ogni
     * chiamata riscrive il file e riprogramma l'allarme, e girare la ghiera di
     * mezzo giro sono venti scatti. Durante il trascinamento la sveglia cambia
     * solo a schermo.
     */
    public void regola(Sveglia s, int ora, int minuto) {
        if (s == null) return;
        s.ora = Math.max(0, Math.min(23, ora));
        s.minuto = Math.max(0, Math.min(59, minuto));
        s.rinvio = 0;
        s.diventaDelTablet();
        salva();
        if (s.attiva) programmaSveglia(s);
        cambiato();
    }

    /** Sposta l'ora di tanti minuti, girando a mezzanotte. */
    public void sposta(Sveglia s, int minuti) {
        int totale = ((s.ora * 60 + s.minuto + minuti) % 1440 + 1440) % 1440;
        s.ora = totale / 60;
        s.minuto = totale % 60;
        s.rinvio = 0;
        s.diventaDelTablet();
        salva();
        if (s.attiva) programmaSveglia(s);
        cambiato();
    }

    /** Accende o spegne un giorno della settimana (0 = domenica). */
    public void giorno(Sveglia s, int giorno) {
        s.giorni ^= (1 << giorno);
        s.rinvio = 0;
        s.diventaDelTablet();
        salva();
        if (s.attiva) programmaSveglia(s);
        cambiato();
    }

    /** La sveglia con questo identificativo, o null. */
    public Sveglia trova(int id) {
        for (Sveglia s : sveglie) if (s.id == id) return s;
        return null;
    }

    /** Regola ora, minuto e giorni in un colpo: la usa il telefono, che manda
     *  la sveglia gia' decisa invece di girare una ghiera. */
    public void regola(Sveglia s, int ora, int minuto, int giorni) {
        if (s == null) return;
        s.giorni = giorni & 0x7F;
        regola(s, ora, minuto);
    }

    /** Il momento in cui suonera', per chi lo chiede da fuori. */
    public long prossima(Sveglia s) {
        return quandoSuona(s);
    }

    /**
     * La sveglia specchio di un telefono: una sola per telefono, che segue la
     * sua prossima sveglia di sistema.
     *
     * <b>Un istante, non un'ora.</b> Il telefono manda il momento esatto, e la
     * sveglia qui suona in quel momento e basta: dopo, come le « una volta
     * sola », si spegne e aspetta che il telefono dica la prossima.
     *
     * {@code quando} a zero, o nel passato, vuol dire che il telefono non ne
     * ha piu': lo specchio se ne va. Se invece e' la stessa di prima non si
     * tocca niente - il telefono la rimanda a ogni giro, e riscrivere il file
     * e riprogrammare l'allarme ogni mezz'ora per niente e' lavoro buttato.
     *
     * @return la sveglia, o null se non ce n'e' piu'.
     */
    public Sveglia svegliaDelTelefono(String telefono, long quando) {
        if (telefono == null || telefono.length() == 0) return null;
        Sveglia s = null;
        for (Sveglia x : sveglie) if (telefono.equals(x.telefono)) { s = x; break; }
        if (quando <= System.currentTimeMillis()) {
            if (s != null) togliSveglia(s);
            return null;
        }
        if (s != null && s.il == quando && s.attiva) return s;

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(quando);
        if (s == null) {
            s = new Sveglia(prossimoId++, 0, 0, 0);
            s.telefono = telefono;
            sveglie.add(s);
        }
        s.ora = c.get(Calendar.HOUR_OF_DAY);
        s.minuto = c.get(Calendar.MINUTE);
        s.giorni = 0;
        s.il = quando;
        s.attiva = true;
        s.rinvio = 0;
        salva();
        programmaSveglia(s);
        cambiato();
        return s;
    }

    /**
     * La prossima che suonera', detta in italiano. null se non ce n'e'.
     *
     * Il risultato si tiene: la Home lo chiede a ogni battito del timer, cioe'
     * una volta al secondo, e il conto sotto costa quattro Calendar - che
     * allocano - per una risposta che cambia una volta al giorno. Si ricalcola
     * quando le sveglie cambiano (lo azzera {@link #salva()}) e comunque non
     * piu' di una volta al minuto, che e' anche la grana con cui la risposta
     * puo' cambiare da sola.
     */
    public String prossimaSveglia() {
        long adesso = System.currentTimeMillis();
        if (adesso < prossimaValidaFino) return prossimaDetta;
        prossimaValidaFino = adesso + 60000L;
        prossimaDetta = calcolaProssima();
        return prossimaDetta;
    }

    private String prossimaDetta;
    private long prossimaValidaFino;

    private String calcolaProssima() {
        long migliore = 0;
        Sveglia quale = null;
        for (Sveglia s : sveglie) {
            if (!s.attiva) continue;
            long q = quandoSuona(s);
            if (quale == null || q < migliore) { migliore = q; quale = s; }
        }
        if (quale == null) return null;

        Calendar ora = Calendar.getInstance();
        Calendar poi = Calendar.getInstance();
        poi.setTimeInMillis(migliore);
        long giorniDiDifferenza = giorniFra(ora, poi);
        String prefisso;
        if (giorniDiDifferenza == 0) prefisso = "oggi";
        else if (giorniDiDifferenza == 1) prefisso = "domani";
        else prefisso = GIORNI_BREVI[poi.get(Calendar.DAY_OF_WEEK) - 1];
        return prefisso + " alle " + quale.orario();
    }

    private static long giorniFra(Calendar a, Calendar b) {
        Calendar x = (Calendar) a.clone(), y = (Calendar) b.clone();
        azzeraOre(x); azzeraOre(y);
        return Math.round((y.getTimeInMillis() - x.getTimeInMillis()) / 86400000.0);
    }

    private static void azzeraOre(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    /**
     * Il prossimo momento in cui questa sveglia suona.
     *
     * Si parte da oggi e si va avanti al massimo di otto giorni: sette per fare
     * il giro della settimana, piu' uno perche' una sveglia che ripete solo di
     * domenica e chiesta di domenica alle nove per le otto deve cadere fra sette
     * giorni, non fra zero.
     */
    private long quandoSuona(Sveglia s) {
        if (s.rinvio > System.currentTimeMillis()) return s.rinvio;
        // Lo specchio del telefono suona nel suo istante. Se e' gia' passato
        // si ricade sull'ora, come una « una volta sola »: un allarme
        // programmato nel passato scatterebbe adesso.
        if (s.il > System.currentTimeMillis()) return s.il;

        Calendar c = Calendar.getInstance();
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.HOUR_OF_DAY, s.ora);
        c.set(Calendar.MINUTE, s.minuto);

        long adesso = System.currentTimeMillis();
        for (int i = 0; i <= 8; i++) {
            boolean giornoBuono = s.giorni == 0
                    || (s.giorni & (1 << (c.get(Calendar.DAY_OF_WEEK) - 1))) != 0;
            if (giornoBuono && c.getTimeInMillis() > adesso) return c.getTimeInMillis();
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    // ---- quando scatta ---------------------------------------------------------

    /**
     * Chiamata dal ricevitore. Trova chi era, lo fa suonare, e avvisa lo
     * schermo.
     *
     * Una sveglia che ripete si riprogramma <b>subito</b>, prima ancora di
     * suonare: se qualcuno spegne il tablet mentre suona, quella di domani
     * dev'esserci lo stesso.
     */
    public void scatta(int id) {
        // Con l'indice e non con il for-each: qui dentro si toglie dalla lista
        // su cui si sta girando, e un for-each che continuasse dopo la remove
        // solleverebbe. Funziona per via del return subito dopo, ed e' proprio
        // il genere di cosa che si rompe alla prima riga aggiunta in mezzo.
        for (int i = 0; i < conti.size(); i++) {
            Conto c = conti.get(i);
            if (c.id != id) continue;
            conti.remove(i);
            salva();
            contoChesuona = c;
            suoneria.suona(false);
            if (spia != null) spia.suScatto("Timer di " + c.nome(), false);
            cambiato();
            return;
        }
        for (int i = 0; i < sveglie.size(); i++) {
            Sveglia s = sveglie.get(i);
            if (s.id != id) continue;
            s.rinvio = 0;
            if (s.ripete()) {
                programmaSveglia(s);
            } else {
                // Una sveglia « una volta sola » ha finito il suo mestiere: si
                // spegne invece di sparire, cosi' domani si riaccende con un
                // tocco invece di rifarla da capo.
                s.attiva = false;
            }
            salva();
            sveglaChesuona = s;
            suoneria.suona(true);
            if (spia != null) spia.suScatto("Sveglia delle " + s.orario(), true);
            cambiato();
            return;
        }
        Log.w(TAG, "e' scattato l'allarme " + id + " ma non lo conosco piu'");
    }

    public boolean staSuonando() {
        return sveglaChesuona != null || contoChesuona != null;
    }

    /** Vero solo per le sveglie: il timer che scade non si rimanda, si e' gia'
     *  bruciata la pasta. */
    public boolean siPuoRimandare() { return sveglaChesuona != null; }

    /** Basta. */
    public void taci() {
        suoneria.taci();
        sveglaChesuona = null;
        contoChesuona = null;
        if (spia != null) spia.suSilenzio();
        cambiato();
    }

    /** Ancora cinque minuti. */
    public void rimanda() {
        Sveglia s = sveglaChesuona;
        suoneria.taci();
        sveglaChesuona = null;
        contoChesuona = null;
        if (s != null) {
            s.rinvio = System.currentTimeMillis() + RINVIO_MS;
            s.attiva = true;
            salva();
            programmaSveglia(s);
        }
        if (spia != null) spia.suSilenzio();
        cambiato();
    }

    // ---- il tick dello schermo --------------------------------------------------

    /**
     * Un colpo al secondo, ma <b>solo finche' c'e' un timer che scorre</b>.
     *
     * Senza la condizione sarebbe un risveglio al secondo per sempre su un
     * apparecchio acceso sedici ore al giorno: le sveglie non hanno niente da
     * far scorrere sullo schermo, e chiedere di ridisegnare per mostrare lo
     * stesso identico numero e' batteria buttata.
     */
    private void battito() {
        ui.removeCallbacks(tick);
        if (!conti.isEmpty()) ui.postDelayed(tick, 1000 - (System.currentTimeMillis() % 1000));
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            // Un timer scaduto che il sistema non ha ancora consegnato: lo si
            // toglie dallo schermo subito, il suono arriva da AlarmManager.
            cambiato();
            battito();
        }
    };

    private void cambiato() {
        if (spia != null) spia.suCambio();
    }

    // ---- allarmi di sistema -------------------------------------------------------

    private PendingIntent busta(int id) {
        Intent i = new Intent(contesto, RicevitoreAllarmi.class);
        i.setAction(AZIONE);
        i.putExtra(EXTRA_ID, id);
        // Il codice di richiesta e' l'id: due allarmi diversi devono essere due
        // buste diverse, se no il secondo sostituisce il primo e resta una
        // sveglia sola in piedi.
        return PendingIntent.getBroadcast(contesto, id, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void annulla(int id) {
        if (allarmi != null) allarmi.cancel(busta(id));
    }

    private void programmaConto(Conto c) {
        if (allarmi == null) return;
        allarmi.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.scadenza, busta(c.id));
    }

    private void programmaSveglia(Sveglia s) {
        if (allarmi == null) return;
        if (!s.attiva) { annulla(s.id); return; }
        long quando = quandoSuona(s);
        // setAlarmClock e non setExact: e' l'unico modo che il sistema tratta
        // come « una persona ha chiesto di essere svegliata ». Non lo rimanda
        // mai, nemmeno a dispositivo fermo da ore.
        Intent mostra = new Intent(contesto, MainActivity.class);
        PendingIntent apri = PendingIntent.getActivity(contesto, s.id, mostra,
                PendingIntent.FLAG_UPDATE_CURRENT);
        allarmi.setAlarmClock(new AlarmManager.AlarmClockInfo(quando, apri), busta(s.id));
    }

    /**
     * Rimette in piedi tutti gli allarmi. La chiama il costruttore e la chiama
     * il ricevitore dopo un riavvio o una reinstallazione.
     */
    public void riprogramma() {
        for (Conto c : conti) programmaConto(c);
        for (Sveglia s : sveglie) programmaSveglia(s);
    }

    // ---- disco ----------------------------------------------------------------------

    private void carica() {
        JSONObject o = Archivio.leggi(contesto, FILE);
        if (o == null) return;
        prossimoId = Math.max(1, o.optInt("prossimoId", 1));

        long adesso = System.currentTimeMillis();
        JSONArray a = o.optJSONArray("timer");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject t = a.optJSONObject(i);
                if (t == null) continue;
                long scadenza = t.optLong("scadenza", 0);
                // Un timer scaduto mentre il tablet era spento non si fa
                // suonare adesso: « il tempo e' finito » due ore dopo non e'
                // un avviso, e' una seccatura. Se ne va zitto.
                if (scadenza <= adesso) continue;
                conti.add(new Conto(t.optInt("id", prossimoId++),
                        t.optLong("durata", 0), scadenza));
            }
        }
        JSONArray b = o.optJSONArray("sveglie");
        if (b != null) {
            for (int i = 0; i < b.length(); i++) {
                JSONObject s = b.optJSONObject(i);
                if (s == null) continue;
                Sveglia sv = new Sveglia(s.optInt("id", prossimoId++),
                        s.optInt("ora", 7), s.optInt("minuto", 0), s.optInt("giorni", 0));
                sv.attiva = s.optBoolean("attiva", true);
                sv.il = s.optLong("il", 0L);
                sv.telefono = s.optString("telefono", "");
                // Il rinvio non sopravvive a un riavvio: se il tablet si e'
                // spento mentre la sveglia era rimandata, si torna all'orario
                // vero invece di suonare a un'ora che nessuno ha scelto.
                sveglie.add(sv);
            }
        }
        for (Sveglia s : sveglie) prossimoId = Math.max(prossimoId, s.id + 1);
        for (Conto c : conti) prossimoId = Math.max(prossimoId, c.id + 1);
    }

    private void salva() {
        try {
            JSONArray a = new JSONArray();
            for (Conto c : conti) {
                JSONObject t = new JSONObject();
                t.put("id", c.id);
                t.put("durata", c.durata);
                t.put("scadenza", c.scadenza);
                a.put(t);
            }
            JSONArray b = new JSONArray();
            for (Sveglia s : sveglie) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("ora", s.ora);
                o.put("minuto", s.minuto);
                o.put("giorni", s.giorni);
                o.put("attiva", s.attiva);
                if (s.il > 0) o.put("il", s.il);
                if (s.dalTelefono()) o.put("telefono", s.telefono);
                b.put(o);
            }
            JSONObject tutto = new JSONObject();
            tutto.put("prossimoId", prossimoId);
            tutto.put("timer", a);
            tutto.put("sveglie", b);
            Archivio.scrivi(contesto, FILE, tutto);
            // Le sveglie sono cambiate: la frase « domani alle 7 » va rifatta.
            prossimaValidaFino = 0;
        } catch (Exception e) {
            Log.w(TAG, "non riesco a salvare " + FILE, e);
        }
    }

    /**
     * L'Activity se ne va. L'Orologio <b>resta</b>: le sveglie non appartengono
     * alla finestra, e quello che sta suonando non deve zittirsi perche'
     * qualcuno ha chiuso una schermata. Si stacca solo chi guardava.
     */
    public void staccati(Spia chi) {
        if (spia == chi) spia = null;
    }

    // ---- parole ---------------------------------------------------------------------

    /** « 10 minuti », « un'ora e mezza », « 45 secondi ». Serve a dirlo a voce
     *  e a scriverlo sulla tessera: due posti, una forma sola. */
    public static String durataInParole(long secondi) {
        if (secondi < 60) return secondi + (secondi == 1 ? " secondo" : " secondi");
        long minuti = secondi / 60;
        if (minuti < 60) return minuti + (minuti == 1 ? " minuto" : " minuti");
        long ore = minuti / 60, resto = minuti % 60;
        String testoOre = ore == 1 ? "un'ora" : ore + " ore";
        if (resto == 0) return testoOre;
        if (resto == 30) return testoOre + " e mezza";
        return testoOre + " e " + resto;
    }

    /** Il conto alla rovescia come si legge: 1:29:57 oppure 4:05. */
    public static String scorrere(long secondi) {
        long mi = secondi / 60, s = secondi % 60;
        if (mi >= 60) return String.format(Locale.ITALIAN, "%d:%02d:%02d", mi / 60, mi % 60, s);
        return String.format(Locale.ITALIAN, "%d:%02d", mi, s);
    }
}
