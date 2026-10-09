package dev.casa;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * L'orecchio e la bocca di Casa.
 *
 * Ascolto **a richiesta**, non continuo. Il riconoscitore di Android suona un
 * tono a ogni startListening, e riavviarlo in ciclo faceva "bip" ogni pochi
 * secondi. Peggio: mandava ai server di Google tutto il parlato della stanza,
 * non i soli comandi. Un vero wake word si riconosce a bordo; finche' non
 * c'e', si parla premendo il microfono. Il ciclo continuo resta in accendi(), spento.
 */
public class Voce implements RecognitionListener {

    /** Chi riceve quello che Casa ha sentito. */
    public interface Ascoltatore {
        /** Frase completa dopo la parola di attivazione. */
        void suComando(String frase);
        /** Cambio di stato da mostrare a schermo. */
        void suStato(String stato);

        /**
         * Il giro e' finito: Casa non ascolta piu' e non sta parlando.
         *
         * Esiste per il microfono, che su Android e' <b>di uno solo</b>: chi ha
         * ceduto il microfono al riconoscitore ha bisogno di sapere quando puo'
         * riprenderselo. Dedurlo da {@link #suStato} con un null andrebbe bene
         * quasi sempre, e le volte in cui non andasse bene si vedrebbero come
         * una parola di attivazione che smette di funzionare senza un perche'.
         */
        void suFine();

        /**
         * Casa sta parlando, o ha appena smesso.
         *
         * Serve a chi ascolta in continuazione: la voce di Casa esce da venti
         * centimetri sotto il microfono ed e' la cosa piu' forte della stanza,
         * cioe' il falso aggancio piu' facile che ci sia.
         */
        void suBocca(boolean parla);
    }

    /** Basta che la frase cominci con una di queste. */
    private static final String[] RICHIAMI = { "casa", "ehi casa", "ok casa" };

    private final Context contesto;
    private final Ascoltatore ascoltatore;
    private final Handler handler = new Handler();

    /**
     * Se il riconoscitore non si fa piu' vivo, dopo questo tempo il giro si
     * chiude a forza.
     *
     * <h3>Il difetto che cura</h3>
     *
     * {@code acceso} torna falso solo dentro una delle risposte del
     * riconoscitore - {@code onResults} o {@code onError}. Se quelle non
     * arrivano <b>mai</b>, resta vero per sempre, e da quel momento
     * {@code parla()} esce subito perche' crede che Casa stia gia' ascoltando:
     * <b>il microfono smette di rispondere al tocco e non riparte piu'</b>
     * finche' non si riavvia l'app.
     *
     * Non e' un caso di scuola: nel registro di questo tablet c'e'
     * {@code Fatal signal 11 (SIGSEGV) in tid readThread0} dentro il processo
     * del riconoscitore di Google. Quando quello muore mentre sta ascoltando,
     * nessuna risposta arriva, e Casa resta ad aspettarla per sempre.
     *
     * <b>Un Handler suo</b>, e non quello degli altri: {@code riprova()} e
     * {@code di()} fanno {@code removeCallbacksAndMessages(null)} sul loro, e
     * si porterebbero via anche la guardia - cioe' proprio nei momenti in cui
     * serve.
     *
     * Dodici secondi: il riconoscitore di Android smette da solo dopo cinque o
     * sei di silenzio, quindi qualunque giro sano e' finito da un pezzo. Se a
     * dodici non ha detto niente, non lo dira'.
     */
    private static final long SCADENZA_MS = 12000;

    private final Handler guardia = new Handler();

    /** Abbassa la radio e la musica degli altri mentre Casa ascolta o parla.
     *  Senza, con la radio accesa il microfono sente la radio e basta. */
    private final Sordina sordina;

    private SpeechRecognizer riconoscitore;
    private TextToSpeech sintesi;

    private boolean acceso;
    /** true = ciclo continuo (rumoroso, tutto va in rete). false = un colpo per tocco. */
    private boolean continuo;
    private boolean sintesiPronta;
    private boolean staParlando;

    public Voce(Context contesto, Ascoltatore ascoltatore, Sordina sordina) {
        this.contesto = contesto;
        this.ascoltatore = ascoltatore;
        this.sordina = sordina;

        sintesi = new TextToSpeech(contesto, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int esito) {
                if (esito == TextToSpeech.SUCCESS) {
                    sintesi.setLanguage(Locale.ITALIAN);
                    scegliVoce();
                    sintesi.setSpeechRate(ritmo);
                    sintesi.setPitch(tono);
                    sintesiPronta = true;
                    Log.i(MainActivity.TAG, "voce pronta");
                } else {
                    Log.w(MainActivity.TAG, "sintesi vocale non disponibile");
                }
            }
        });
    }

    /**
     * Sceglie una voce maschile italiana, se il sistema ne ha una.
     *
     * <h3>Perche' non basta setLanguage</h3>
     *
     * {@code setLanguage(ITALIAN)} prende la voce che il sistema ha per
     * l'italiano, che di serie e' femminile. La voce si sceglie una per una
     * con {@code setVoice}, e le voci disponibili dipendono da che cosa e'
     * stato scaricato su questo apparecchio: <b>non si possono indovinare, si
     * elencano</b>. Per questo qui sotto vengono tutte scritte nel registro -
     *
     *     adb logcat -d -s Casa:I | grep "voce disponibile"
     *
     * - e la scelta e' a punti invece che su un nome fisso, cosi' funziona
     * anche se un aggiornamento del sintetizzatore cambia l'elenco.
     *
     * <h3>I punti</h3>
     *
     * <ul>
     * <li><b>maschile</b> pesa piu' di tutto: e' quello che si sta cercando;</li>
     * <li><b>senza rete</b> pesa molto: Casa deve parlare anche con il
     *     collegamento giu', e una voce di rete darebbe una casa muta proprio
     *     quando qualcosa non va;</li>
     * <li>la qualita' decide fra pari.</li>
     * </ul>
     *
     * Se non c'e' niente di maschile non si forza nulla: meglio la voce di
     * serie che una voce di un'altra lingua.
     */
    private void scegliVoce() {
        try {
            Set<Voice> tutte = sintesi.getVoices();
            if (tutte == null || tutte.isEmpty()) {
                Log.i(MainActivity.TAG, "voce: il sintetizzatore non elenca voci");
                return;
            }

            // Se qualcuno ne ha scelta una dal PC, quella vince sui punti.
            // I punti servono a indovinare bene quando nessuno ha deciso; se
            // qualcuno ha deciso, non c'e' piu' niente da indovinare - e una
            // scelta fatta a orecchio vale piu' di qualunque punteggio, perche'
            // "quale voce non sembra un robot" non e' una cosa che si calcola.
            if (voceScelta != null && voceScelta.length() > 0) {
                for (Voice v : tutte) {
                    if (voceScelta.equals(v.getName())) {
                        sintesi.setVoice(v);
                        Log.i(MainActivity.TAG, "voce scelta a mano: " + v.getName());
                        return;
                    }
                }
                Log.i(MainActivity.TAG, "voce: \"" + voceScelta
                        + "\" non c'e' piu' su questo apparecchio, ne scelgo una io");
            }
            Voice migliore = null;
            int meglio = Integer.MIN_VALUE;
            for (Voice v : tutte) {
                Locale l = v.getLocale();
                if (l == null) continue;
                String lingua;
                try { lingua = l.getISO3Language(); } catch (Exception e) { continue; }
                if (!"ita".equalsIgnoreCase(lingua)) continue;

                String nome = v.getName() == null ? "" : v.getName().toLowerCase(Locale.US);
                boolean rete = v.isNetworkConnectionRequired();
                Log.i(MainActivity.TAG, "voce disponibile: " + v.getName()
                        + " qualita=" + v.getQuality() + " rete=" + rete);

                int punti = 0;
                if (nome.contains("male") && !nome.contains("female")) punti += 1000;
                if (nome.contains("female")) punti -= 1000;
                if (!rete) punti += 200;
                punti += v.getQuality();

                // A pari punti vince il nome che viene prima in ordine
                // alfabetico, e non e' pignoleria: su questo tablet le voci
                // maschili sono tre - male_1, male_2, male_3 - tutte locali e
                // tutte di qualita' 400, quindi pari. getVoices() torna un Set,
                // che non promette nessun ordine: senza questa riga la voce
                // poteva cambiare da un avvio all'altro. Una casa che si
                // sveglia con una voce diversa e' una casa che sembra rotta.
                boolean vince = punti > meglio
                        || (punti == meglio && migliore != null && migliore.getName() != null
                            && v.getName() != null
                            && v.getName().compareTo(migliore.getName()) < 0);
                if (vince) { meglio = punti; migliore = v; }
            }
            if (migliore != null) {
                sintesi.setVoice(migliore);
                Log.i(MainActivity.TAG, "voce scelta: " + migliore.getName());
            }
        } catch (Throwable t) {
            // Un sintetizzatore che non sa elencare le voci non e' un motivo
            // per restare muti: si tiene quella di serie.
            Log.w(MainActivity.TAG, "voce: scelta non riuscita", t);
        }
    }

    /**
     * Chiude il giro a forza quando il riconoscitore non risponde piu'.
     *
     * Fa esattamente quello che avrebbe fatto una risposta arrivata: rimette
     * su la sordina, azzera lo stato a schermo, e avvisa che il giro e'
     * finito - cosi' chi aveva ceduto il microfono se lo riprende.
     */
    private final Runnable scaduta = new Runnable() {
        @Override public void run() {
            if (!acceso) return;
            Log.w(MainActivity.TAG, "il riconoscitore non risponde da "
                    + (SCADENZA_MS / 1000) + " secondi: chiudo il giro a forza");
            acceso = false;
            staParlando = false;
            if (riconoscitore != null) {
                try { riconoscitore.destroy(); } catch (Exception ignorata) { }
                riconoscitore = null;
            }
            if (sordina != null) sordina.su();
            ascoltatore.suStato("non ho potuto ascoltare");
            ascoltatore.suFine();
        }
    };

    private void armaLaGuardia() {
        guardia.removeCallbacks(scaduta);
        guardia.postDelayed(scaduta, SCADENZA_MS);
    }

    private void disarmaLaGuardia() {
        guardia.removeCallbacks(scaduta);
    }

    /**
     * Quanto va veloce la voce.
     *
     * <b>Uno e' la velocita' di serie, ed e' quella di un annuncio in
     * stazione.</b> Va bene per leggere un orario a chi e' di fretta, non per
     * rispondere a qualcuno che sta in cucina a due passi. Nove decimi tolgono
     * quella fretta senza far sembrare che la voce sia rallentata - sotto
     * 0,85 si sente che e' un artificio.
     *
     * Il tono di serie e' squillante come tutte le voci sintetiche; un filo
     * piu' basso somiglia di piu' a una persona. Anche qui poco: sotto 0,9 la
     * voce diventa cupa e finta in un altro modo.
     */
    private static final float RITMO = 0.90f;
    private static final float TONO  = 0.96f;

    /** Il ritmo in uso: si puo' cambiare dal PC per provarlo a orecchio,
     *  che e' l'unico modo di sceglierlo. */
    private float ritmo = RITMO;
    private float tono = TONO;

    /**
     * La voce scelta dal PC, per nome. Vuota: sceglie {@link #scegliVoce}.
     *
     * <b>Perche' si sceglie a mano.</b> Il punteggio sceglie bene la CATEGORIA
     * - maschile, locale, di qualita' - ma dentro quella categoria le voci di
     * questo apparecchio sono tre, tutte pari, e fra loro cambia solo il timbro.
     * Quale delle tre sembri meno un robot non e' una cosa che si calcola: si
     * ascolta. Il PC le fa sentire una per una e si tiene quella che piace.
     */
    private String voceScelta = "";

    public float ritmo() { return ritmo; }
    public float tono() { return tono; }
    public String voceScelta() { return voceScelta; }

    /** Cambia la velocita' del parlato, e la fa sentire subito. */
    public void setRitmo(float v) {
        ritmo = Math.max(0.6f, Math.min(1.4f, v));
        if (sintesi != null && sintesiPronta) sintesi.setSpeechRate(ritmo);
        Log.i(MainActivity.TAG, "PC ritmo della voce: " + ritmo);
        di("Vado a questa velocita'.");
    }

    /**
     * Cambia il tono, e lo fa sentire subito.
     *
     * Il tetto e' stretto apposta. Sopra 1,2 la voce diventa stridula e sotto
     * 0,8 cavernosa: sono tutte e due il robot di prima con un difetto in piu'.
     * Il margine che serve davvero e' quel dieci per cento attorno all'uno in
     * cui una voce sintetica smette di suonare squillante.
     */
    public void setTono(float v) {
        tono = Math.max(0.8f, Math.min(1.2f, v));
        if (sintesi != null && sintesiPronta) sintesi.setPitch(tono);
        Log.i(MainActivity.TAG, "PC tono della voce: " + tono);
        di("E questo e' il tono.");
    }

    /**
     * Sceglie una voce per nome, e la fa sentire.
     *
     * Il nome vuoto rimette la scelta automatica: e' il modo di tornare
     * indietro senza doversi ricordare quale fosse quella di prima.
     */
    public void setVoce(String nome) {
        voceScelta = nome == null ? "" : nome.trim();
        if (sintesi != null && sintesiPronta) {
            scegliVoce();
            sintesi.setSpeechRate(ritmo);
            sintesi.setPitch(tono);
        }
        Log.i(MainActivity.TAG, "PC voce: " + (voceScelta.length() > 0 ? voceScelta : "(automatica)"));
        di("Ciao, sono " + Comandi.NOME + ". Ti sembro meno un robot, cosi'?");
    }

    /**
     * Scrive nel registro tutte le voci che il sintetizzatore ha, per il PC.
     *
     * Una riga per voce, con dentro tutto quello che serve a metterla in un
     * elenco: nome, qualita', se vuole la rete, e quale e' quella in uso
     * adesso. Non si possono indovinare - dipendono da che cosa e' stato
     * scaricato su questo apparecchio - quindi si elencano.
     */
    public void elencaVoci() {
        try {
            Set<Voice> tutte = sintesi == null ? null : sintesi.getVoices();
            if (tutte == null || tutte.isEmpty()) {
                Log.i(MainActivity.TAG, "PC voci: nessuna");
                return;
            }
            Voice adesso = null;
            try { adesso = sintesi.getVoice(); } catch (Throwable t) { }
            String inUso = adesso == null || adesso.getName() == null ? "" : adesso.getName();
            java.util.List<String> nomi = new java.util.ArrayList<String>();
            for (Voice v : tutte) {
                if (v.getLocale() == null) continue;
                String lingua = v.getLocale().getLanguage();
                if (!("it".equals(lingua) || "ita".equals(lingua))) continue;
                nomi.add(v.getName() + "|" + v.getQuality() + "|"
                        + v.isNetworkConnectionRequired() + "|"
                        + v.getName().equals(inUso));
            }
            java.util.Collections.sort(nomi);
            for (String n : nomi) Log.i(MainActivity.TAG, "PC voce " + n);
            Log.i(MainActivity.TAG, "PC voci " + nomi.size() + " ritmo=" + ritmo
                    + " tono=" + tono + " scelta=" + (voceScelta.length() > 0 ? voceScelta : "-"));
        } catch (Throwable t) {
            Log.w(MainActivity.TAG, "PC voci: non riesco a elencarle", t);
        }
    }

    /** Rimette voce, ritmo e tono come li ha scritti il PC, senza dire niente:
     *  la si chiama all'avvio, e una casa che parla da sola appena si accende
     *  non e' quello che si vuole. */
    public void applica(String nome, float ritmoNuovo, float tonoNuovo) {
        voceScelta = nome == null ? "" : nome.trim();
        if (ritmoNuovo > 0f) ritmo = Math.max(0.6f, Math.min(1.4f, ritmoNuovo));
        if (tonoNuovo > 0f) tono = Math.max(0.8f, Math.min(1.2f, tonoNuovo));
        if (sintesi != null && sintesiPronta) {
            scegliVoce();
            sintesi.setSpeechRate(ritmo);
            sintesi.setPitch(tono);
        }
    }

    /** Un solo ascolto, su richiesta. Nessun riavvio automatico. */
    public void ascoltaUnaVolta() {
        if (acceso) return;
        continuo = false;
        if (!disponibile()) return;
        acceso = true;
        creaRiconoscitore();
        ascolta();
    }

    private boolean disponibile() {
        if (SpeechRecognizer.isRecognitionAvailable(contesto)) return true;
        ascoltatore.suStato("riconoscimento vocale non disponibile");
        return false;
    }

    public boolean staAscoltando() { return acceso; }

    /** Vero fra un {@link #annulla()} e l'errore che il riconoscitore manda
     *  subito dopo, che va ignorato. */
    private boolean annullato;

    /**
     * Chiude l'ascolto perche' l'ha chiesto chi ascoltava, non per un errore.
     *
     * Differenza da {@link #spegni()}: qui il riconoscitore si <b>annulla</b>
     * e non si distrugge. {@code cancel()} chiude la sessione senza far
     * arrivare ne' un risultato ne' un errore - quindi senza che Casa dica
     * « non ho capito » a chi ha appena detto di lasciar perdere, che sarebbe
     * il modo piu' rapido di far sembrare l'annulla un guasto.
     */
    public void annulla() {
        if (!acceso) return;
        acceso = false;
        continuo = false;
        // Dopo cancel() il riconoscitore manda comunque il suo errore 5. E'
        // il rumore di una porta chiusa apposta, non un guasto: si zittisce
        // qui, o Casa risponderebbe « non ho potuto ascoltare » a chi ha
        // appena detto di lasciar perdere.
        annullato = true;
        disarmaLaGuardia();
        handler.removeCallbacksAndMessages(null);
        if (riconoscitore != null) riconoscitore.cancel();
        if (sordina != null) sordina.su();
        Log.i(MainActivity.TAG, "ascolto annullato");
    }

    public void accendi() {
        if (acceso) return;
        continuo = true;
        if (!SpeechRecognizer.isRecognitionAvailable(contesto)) {
            ascoltatore.suStato("riconoscimento vocale non disponibile");
            return;
        }
        acceso = true;
        creaRiconoscitore();
        ascolta();
    }

    public void spegni() {
        acceso = false;
        disarmaLaGuardia();
        if (sordina != null) sordina.su();
        handler.removeCallbacksAndMessages(null);
        if (riconoscitore != null) {
            riconoscitore.destroy();
            riconoscitore = null;
        }
    }

    public void chiudi() {
        spegni();
        if (sintesi != null) {
            sintesi.shutdown();
            sintesi = null;
        }
    }

    private void creaRiconoscitore() {
        if (riconoscitore != null) riconoscitore.destroy();
        riconoscitore = SpeechRecognizer.createSpeechRecognizer(contesto);
        riconoscitore.setRecognitionListener(this);
    }

    private void ascolta() {
        if (!acceso || staParlando || riconoscitore == null) return;

        // Prima di aprire il microfono, giu' tutto il resto. Se lo si facesse
        // dopo, il primo mezzo secondo di parlato - che e' quello che contiene
        // l'inizio del comando - arriverebbe coperto.
        if (sordina != null) sordina.giu();

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                   RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, contesto.getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            riconoscitore.startListening(i);
            armaLaGuardia();
        } catch (Exception e) {
            Log.w(MainActivity.TAG, "startListening", e);
            riprova(1500);
        }
    }

    /**
     * Riavvia l'ascolto dopo una pausa.
     *
     * La pausa non e' cosmetica: senza, un errore che si ripete (rete assente,
     * microfono occupato) diventa un ciclo stretto che scalda il tablet e
     * svuota la batteria in silenzio.
     */
    private void riprova(long fraQuanto) {
        // In modalita' a colpo singolo non si riparte: e' finito il giro, e
        // riavviare sarebbe di nuovo il ciclo che fa "bip" e manda in rete
        // tutto quello che si dice in casa.
        if (!continuo) {
            acceso = false;
            if (sordina != null) sordina.su();
            ascoltatore.suStato(null);
            ascoltatore.suFine();
            return;
        }
        if (!acceso) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                creaRiconoscitore();
                ascolta();
            }
        }, fraQuanto);
    }

    /** Dice una frase, e riprende ad ascoltare quando ha finito. */
    public void di(String frase) {
        ascoltatore.suStato(frase);
        if (!sintesiPronta) { riprova(300); return; }

        // Mentre parla non ascolta: altrimenti si sente da sola e prova a
        // eseguire le proprie risposte.
        staParlando = true;
        ascoltatore.suBocca(true);
        // Mentre Casa parla il riconoscitore e' fermo di proposito: la guardia
        // qui non deve scattare, o interromperebbe una risposta lunga.
        disarmaLaGuardia();
        if (riconoscitore != null) riconoscitore.cancel();

        // Anche mentre parla: la risposta deve arrivare sopra la radio, non
        // sotto. La sordina resta giu' dall'ascolto fino a fine risposta, cosi'
        // non si sente un su-e-giu' in mezzo alla frase.
        if (sordina != null) sordina.giu();

        sintesi.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "casa");

        // Il tempo di parlato si stima dalla lunghezza: bastano 90 ms a
        // carattere piu' un margine. Un listener sull'utterance sarebbe piu'
        // preciso ma qui l'errore non fa danni - al massimo riascolta un
        // attimo dopo.
        long durata = 800 + frase.length() * 90L;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                staParlando = false;
                ascoltatore.suBocca(false);
                if (!continuo) {
                    acceso = false;
                    if (sordina != null) sordina.su();
                    ascoltatore.suStato(null);
                    ascoltatore.suFine();
                    return;
                }
                creaRiconoscitore();
                ascolta();
            }
        }, durata);
    }

    /**
     * Toglie il richiamo dalla frase.
     * Torna null se la frase non comincia per "casa": non era per noi.
     */
    private String dopoIlRichiamo(String frase) {
        String f = frase.toLowerCase(Locale.ITALIAN).trim();
        for (String r : RICHIAMI) {
            if (f.startsWith(r)) {
                String resto = f.substring(r.length()).trim();
                // Togli una virgola iniziale: "casa, che ore sono".
                if (resto.startsWith(",")) resto = resto.substring(1).trim();
                return resto;
            }
        }
        return null;
    }

    // --- RecognitionListener --------------------------------------------

    @Override public void onReadyForSpeech(Bundle b) { ascoltatore.suStato(null); }
    @Override public void onBeginningOfSpeech() { ascoltatore.suStato("ti ascolto"); }
    @Override public void onRmsChanged(float v) { }
    @Override public void onBufferReceived(byte[] b) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onEvent(int t, Bundle b) { }
    @Override public void onPartialResults(Bundle b) { }

    @Override
    public void onResults(Bundle esiti) {
        disarmaLaGuardia();
        ArrayList<String> frasi =
                esiti.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (frasi != null && !frasi.isEmpty()) {
            // Col tocco il richiamo e' superfluo: chi ha premuto sta gia'
            // parlando a Casa. A voce invece serve, o risponderebbe a tutto.
            String comando = continuo ? dopoIlRichiamo(frasi.get(0))
                                      : frasi.get(0).trim().toLowerCase(Locale.ITALIAN);
            if (comando != null && !comando.isEmpty()) {
                ascoltatore.suComando(comando);
                return;   // chi esegue rispondera' con di(), che riprende l'ascolto
            }
        }
        ascoltatore.suStato(null);
        riprova(200);
    }

    /**
     * Un guasto del riconoscitore non deve restare muto.
     *
     * Prima, davanti a un errore vero, si scriveva una riga nel registro e si
     * azzerava lo stato a schermo: chi stava davanti al tablet vedeva il
     * microfono accendersi e spegnersi senza una parola. E' il modo in cui
     * {@code ERROR_AUDIO} - il microfono non ancora libero - sembrava « la
     * parola di attivazione non funziona », mentre la parola aveva agganciato
     * benissimo.
     */
    private static String perche(int codice) {
        switch (codice) {
            case SpeechRecognizer.ERROR_AUDIO:            return "il microfono non era libero";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:  return "senza rete non capisco le frasi";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:  return "il riconoscitore era occupato";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                                          return "manca il permesso del microfono";
            case SpeechRecognizer.ERROR_SERVER:           return "il servizio non ha risposto";
            default:                                      return "non ho potuto ascoltare";
        }
    }

    @Override
    public void onError(int codice) {
        disarmaLaGuardia();
        // L'errore che arriva subito dopo un annullamento voluto non e' un
        // errore: e' il rumore della porta chiusa apposta. Si butta senza
        // dire niente e senza riprovare.
        if (annullato) {
            annullato = false;
            return;
        }
        // NO_MATCH e SPEECH_TIMEOUT sono la normalita' in una stanza silenziosa:
        // si riparte subito. Gli altri sono guasti veri e meritano una pausa.
        boolean normale = codice == SpeechRecognizer.ERROR_NO_MATCH
                       || codice == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        if (!normale) {
            Log.w(MainActivity.TAG, "riconoscimento, errore " + codice
                    + " (" + perche(codice) + ")");
            ascoltatore.suStato(perche(codice));
        }
        riprova(normale ? 200 : 3000);
    }
}
