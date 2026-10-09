package dev.casa;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Quello che di Casa si decide dal PC: le luci, le routine, le app in elenco.
 *
 * <h3>Perche' un file e non il codice</h3>
 *
 * Fino a ieri le lampade stavano in {@code Luci.DI_FABBRICA}, le routine in
 * {@code Luci.DI_FABBRICA_ROUTINE} e le app in {@code SezioneApp.ELENCO}:
 * cambiare una tinta o togliere una tessera voleva dire ricompilare, cioe'
 * avere il progetto, l'SDK e trenta secondi. Sono le tre cose che cambiano
 * quando cambia la casa - una lampada nuova, una stanza in piu', un'app che
 * non interessa - ed erano esattamente quelle che chiedevano un compilatore.
 *
 * <h3>Le tre copie, e perche' sono tre</h3>
 *
 * <ul>
 *   <li><b>{@code casa.json} in getFilesDir()</b> e' la configurazione vera.
 *       Sta dentro, dove nessun altro la tocca e dove la scrittura e' atomica
 *       ({@link Archivio}).
 *   <li><b>{@code da-mettere.json} in getExternalFilesDir()</b> e' la buca
 *       delle lettere: il PC ci scrive con {@code adb push}, Casa la legge e
 *       <b>la cancella</b>. Cancellarla e' la parte importante: e' quello che
 *       distingue "il PC ha mandato qualcosa di nuovo" da "c'e' ancora il file
 *       di tre settimane fa", e senza quella distinzione ogni avvio
 *       rimetterebbe la configurazione vecchia sopra quella di adesso.
 *   <li><b>{@code adesso.json} in getExternalFilesDir()</b> e' la vetrina: la
 *       configurazione com'e' in questo momento, riscritta a ogni avvio e dopo
 *       ogni cambiamento. Il PC la legge con {@code adb pull} per sapere da
 *       dove parte, compresi gli indirizzi che le lampade hanno adesso.
 * </ul>
 *
 * Due nomi e non uno solo, perche' con un file unico le due direzioni si
 * pesterebbero i piedi: Casa riscrive la vetrina appena parte, e cancellerebbe
 * quello che il PC aveva appena spinto se il tablet si fosse riavviato nel
 * frattempo.
 *
 * <h3>Perche' la memoria esterna</h3>
 *
 * {@code getFilesDir()} da adb non si legge e non si scrive - "Permission
 * denied", ed e' giusto cosi'. {@code getExternalFilesDir()} invece si', e non
 * chiede nessun permesso all'app perche' e' roba sua: e' la stessa strada che
 * fanno gia' le registrazioni della parola ({@link Campioni}). Dentro non c'e'
 * niente di segreto tranne le chiavi locali delle lampade, che sono le stesse
 * scritte nell'app Smart Life di chi abita qui.
 */
public final class Configurazione {

    // Il tag e' "Casa" e non "Casa.Config" apposta: su questo ROM MediaTek
    // log.tag nasce a E, e un tag fuori lista non compare in logcat senza dare
    // errore. Il pezzo si scrive nel messaggio - vedi MainActivity.TAG.
    private static final String TAG = "Casa";

    /** La configurazione vera, in getFilesDir(). */
    public static final String FILE = "casa.json";

    /** La buca delle lettere: ci scrive il PC, Casa legge e cancella. */
    public static final String DA_METTERE = "da-mettere.json";

    /** La vetrina: ci scrive Casa, il PC legge. */
    public static final String ADESSO = "adesso.json";

    private Configurazione() {}

    // ---- lettura -----------------------------------------------------------

    /** La configurazione di adesso, o null se non ce n'e' ancora una. */
    public static JSONObject leggi(Context c) {
        return Archivio.leggi(c, FILE);
    }

    /**
     * L'elenco {@code nome} dentro la configurazione, o null se non c'e'.
     *
     * Null e non un array vuoto, e la differenza conta: <b>"non l'ho mai
     * deciso" e "ho deciso che non ce ne sono" sono due cose diverse</b>. Sulla
     * prima si riparte da quello che c'e' nel codice, sulla seconda si resta
     * senza - che e' proprio quello che si e' chiesto togliendo l'ultima riga
     * dall'elenco delle app.
     */
    public static JSONArray elenco(Context c, String nome) {
        JSONObject o = leggi(c);
        if (o == null) return null;
        return o.optJSONArray(nome);
    }

    // ---- scrittura ---------------------------------------------------------

    /**
     * Mette per iscritto la configurazione, dentro e in vetrina.
     *
     * La chiama {@link Luci} quando una lampada si e' spostata: cosi' il PC, la
     * volta dopo, parte dagli indirizzi veri e non da quelli del giorno in cui
     * la si e' configurata.
     */
    public static boolean salva(Context c, JSONObject dati) {
        boolean fatto = Archivio.scrivi(c, FILE, dati);
        rispecchia(c, dati);
        return fatto;
    }

    /**
     * Cambia una sola voce della configurazione lasciando stare le altre.
     *
     * Serve perche' i tre elenchi hanno tre padroni diversi - {@link Luci} le
     * luci e le routine, {@link SezioneApp} le app - e chi salva le sue non
     * deve nemmeno sapere che esistono le altre.
     */
    public static boolean metti(Context c, String nome, JSONArray valore) {
        try {
            JSONObject o = leggi(c);
            if (o == null) o = new JSONObject();
            o.put(nome, valore);
            return salva(c, o);
        } catch (Exception e) {
            Log.w(TAG, "config: non ho potuto salvare " + nome, e);
            return false;
        }
    }

    /**
     * Come {@link #metti(Context, String, JSONArray)}, per le voci che sono un
     * oggetto solo: il riposo e la notte, che la pagina delle impostazioni del
     * tablet riscrive intere.
     */
    public static boolean metti(Context c, String nome, JSONObject valore) {
        try {
            JSONObject o = leggi(c);
            if (o == null) o = new JSONObject();
            o.put(nome, valore);
            return salva(c, o);
        } catch (Exception e) {
            Log.w(TAG, "config: non ho potuto salvare " + nome, e);
            return false;
        }
    }

    /**
     * Come deve parlare Casa: quale voce, con che ritmo e che tono.
     *
     * Non e' un elenco come le altre tre voci della configurazione, e' un
     * oggetto solo - quindi ha il suo metodo invece di passare da
     * {@link #metti}. Vale la pena scriverlo perche' e' una scelta che si fa a
     * orecchio in cinque minuti e che poi non si vuole rifare a ogni
     * reinstallazione.
     */
    public static boolean salvaVoce(Context c, String nome, float ritmo, float tono) {
        try {
            JSONObject o = leggi(c);
            if (o == null) o = new JSONObject();
            JSONObject v = new JSONObject();
            v.put("nome", nome == null ? "" : nome);
            v.put("ritmo", ritmo);
            v.put("tono", tono);
            o.put("voce", v);
            return salva(c, o);
        } catch (Exception e) {
            Log.w(TAG, "config: non ho potuto salvare la voce", e);
            return false;
        }
    }

    /**
     * Le notizie: il posto, lo sport, la squadra.
     *
     * Le scrive il tablet quando si cambiano dalla pagina, e il PC quando si
     * cambiano da Gestione Home: tutte e due nello stesso posto, cosi' la
     * vetrina le porta di la' e nessuna delle due strade cancella l'altra.
     */
    public static boolean salvaNotizie(Context c, JSONObject scelte) {
        try {
            JSONObject o = leggi(c);
            if (o == null) o = new JSONObject();
            o.put("notizie", scelte);
            return salva(c, o);
        } catch (Exception e) {
            Log.w(TAG, "config: non ho potuto salvare le notizie", e);
            return false;
        }
    }

    /** Le impostazioni della voce, o null se non le ha mai scelte nessuno. */
    public static JSONObject voce(Context c) {
        JSONObject o = leggi(c);
        return o == null ? null : o.optJSONObject("voce");
    }

    // ---- il giro con il PC -------------------------------------------------

    /**
     * Se il PC ha lasciato qualcosa nella buca, lo prende.
     *
     * Torna vero soltanto quando ha cambiato davvero qualcosa: chi chiama usa
     * quel vero per rileggere gli elenchi, e rileggere per niente vorrebbe dire
     * far ripartire l'interrogazione di tre lampade a ogni avvio.
     *
     * <b>Un file storto non entra.</b> Si controlla che sia JSON e che porti
     * almeno uno dei tre elenchi prima di scriverlo dentro: mezzo push
     * interrotto - il cavo che balla, un Ctrl-C - non deve lasciare Casa senza
     * luci e senza app fino al prossimo giro di PC. In quel caso il file resta
     * nella buca, cosi' si vede che e' arrivato ed e' rotto.
     */
    public static boolean importa(Context c) {
        File buca = esterno(c, DA_METTERE);
        if (buca == null || !buca.exists()) return false;

        JSONObject arrivata = leggiFile(buca);
        // Basta che porti UNA delle cose che sappiamo leggere. L'elenco cresce
        // insieme a quello che si puo' configurare, e chi ne aggiunge una deve
        // aggiungerla anche qui: una configurazione fatta solo di comandi, o
        // solo della voce, altrimenti verrebbe rifiutata come se fosse rotta.
        if (arrivata == null
                || (!arrivata.has("luci") && !arrivata.has("routine")
                    && !arrivata.has("app") && !arrivata.has("comandi")
                    && !arrivata.has("radio")
                    && !arrivata.has("risposte") && !arrivata.has("voce")
                    && !arrivata.has("riposo") && !arrivata.has("notizie")
                    && !arrivata.has("notte"))) {
            Log.i(TAG, "config: " + DA_METTERE + " non si legge o non contiene niente, la lascio li'");
            return false;
        }

        JSONObject dascrivere = fondi(leggi(c), arrivata);
        if (!Archivio.scrivi(c, FILE, dascrivere)) return false;
        if (!buca.delete()) Log.w(TAG, "config: " + DA_METTERE + " scritto ma non cancellato");
        rispecchia(c, dascrivere);
        Log.i(TAG, "config: presa dal PC - " + quante(dascrivere));
        return true;
    }

    /**
     * Quello che il file arrivato non nomina resta com'era.
     *
     * Prima il file del PC <b>diventava</b> la configurazione, punto: tutto
     * quello che non c'era dentro spariva. Sembrava giusto e non lo era, perche'
     * non tutto quello che sta in {@code casa.json} viene dal PC. La scelta
     * della voce, per dirne una, la scrive il tablet ({@link #salvaVoce}) e
     * Gestione Home non la rimanda indietro: bastava toccare una lampada e
     * premere « manda » per riportare Casa alla voce di serie, senza che niente
     * lo dicesse. Adesso ci sta anche il riposo, che ha la stessa forma - un
     * oggetto scritto una volta e poi dimenticato - e avrebbe fatto la stessa
     * fine.
     *
     * <b>Togliere resta possibile</b>, e questo e' il punto delicato: il PC che
     * vuole svuotare un elenco manda {@code "app": []}, cioe' nomina la voce.
     * Sparisce quello che si e' deciso che sparisca, non quello di cui non si e'
     * parlato.
     */
    private static JSONObject fondi(JSONObject prima, JSONObject arrivata) {
        if (prima == null) return arrivata;
        try {
            java.util.Iterator<String> nomi = prima.keys();
            while (nomi.hasNext()) {
                String nome = nomi.next();
                if (!arrivata.has(nome)) arrivata.put(nome, prima.get(nome));
            }
        } catch (Exception e) {
            Log.w(TAG, "config: non ho potuto tenere quello di prima", e);
        }
        return arrivata;
    }

    /** Rimette in vetrina la configurazione di adesso, per il PC. */
    public static void rispecchia(Context c, JSONObject dati) {
        if (dati == null) return;
        File fuori = esterno(c, ADESSO);
        if (fuori == null) return;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(fuori);
            // Con l'indentazione: questo file lo legge una persona quando
            // qualcosa non torna, non solo il programma sul PC.
            out.write(dati.toString(2).getBytes("UTF-8"));
        } catch (Exception e) {
            // La vetrina e' una comodita' per il PC: se la memoria esterna non
            // c'e' - smontata, piena - Casa va avanti lo stesso.
            Log.w(TAG, "config: non ho potuto scrivere " + ADESSO + ": " + e.getMessage());
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignorata) { }
        }
    }

    /**
     * La configurazione com'e' davvero adesso, comprese le parti che nessuno
     * ha mai scritto e che quindi vengono dal codice.
     *
     * Ci va anche il catalogo delle risposte di fabbrica, che il PC non puo'
     * indovinare: e' la stessa ragione per cui le voci del sintetizzatore si
     * chiedono al tablet invece di tenerne una copia sul PC che invecchia.
     *
     * Serve per la vetrina, e la differenza con {@link #leggi} e' tutta li':
     * {@code leggi} dice che cosa e' stato deciso, questa dice che cosa Casa
     * sta usando. Su un tablet appena installato la prima torna null e la
     * seconda torna due lampade, tre routine e cinque app - cioe' proprio quel
     * che il PC deve poter modificare senza doverlo prima riscrivere da capo.
     */
    public static JSONObject effettiva(Context c, Luci luci, Comandi comandi, Radio radio) {
        JSONObject o = leggi(c);
        if (o == null) o = new JSONObject();
        try {
            // Le stazioni come Casa le sta usando: quelle scritte dal PC, o
            // quelle di fabbrica se non le ha mai scritte nessuno. E' l'unico
            // modo in cui il PC puo' partire dall'elenco vero senza tenerne
            // una copia che invecchia - la stessa regola delle lampade.
            if (radio != null) {
                o.put("radio", radio.json());
                // E quelle scritte nel codice, per il tasto « rimetti quelle di
                // fabbrica »: una volta che il PC ha riscritto l'elenco, le
                // ventidue di partenza non esisterebbero piu' da nessuna parte
                // che il PC possa raggiungere.
                o.put("radioBase", radio.jsonDiFabbrica());
            }
            // E quali loghi esistono in QUESTO APK: il PC non puo' saperlo, e
            // un elenco scritto di la' inviterebbe a scegliere un disegno che
            // qui non c'e'. Sono i PNG in res/drawable che si chiamano logo_*.
            o.put("loghiBase", loghi());
            if (luci != null) {
                JSONArray lampade = new JSONArray();
                for (Lampada l : luci.elenco()) lampade.put(l.json());
                o.put("luci", lampade);
                JSONArray routine = new JSONArray();
                for (Routine r : luci.routine()) routine.put(r.json());
                o.put("routine", routine);
            }
            if (comandi != null) o.put("risposteBase", comandi.risposte().catalogo());
            // Le notizie come le sta usando Casa, e gli sport che sa seguire:
            // il secondo e' di sola lettura, come le risposte di fabbrica. E il
            // posto del meteo, perche' il PC possa dire che cosa vuol dire
            // « localita' vuota » senza indovinarlo.
            if (!o.has("notizie")) o.put("notizie", Notizie.diFabbrica());
            o.put("sportBase", Notizie.catalogo());
            JSONObject meteo = Archivio.leggi(c, "meteo.json");
            JSONObject posto = meteo == null ? null : meteo.optJSONObject("posto");
            o.put("meteoCitta", posto != null ? posto.optString("nome", "Roma") : "Roma");
            if (!o.has("app")) {
                JSONArray app = new JSONArray();
                for (String[] riga : SezioneApp.DI_FABBRICA) {
                    JSONObject v = new JSONObject();
                    v.put("pacchetto", riga[0]);
                    v.put("nome", riga[1]);
                    app.put(v);
                }
                o.put("app", app);
            }
        } catch (Exception e) {
            Log.w(TAG, "config: non ho potuto comporre la vetrina", e);
        }
        return o;
    }

    /**
     * I loghi che questo APK ha in casa, per nome.
     *
     * Si leggono da {@code R.drawable} con la reflection invece di tenerne un
     * elenco scritto a mano accanto: un elenco a mano si dimentica di
     * aggiornarlo il giorno che si aggiunge un PNG, e il sintomo sarebbe una
     * stazione che sul PC non si puo' scegliere pur essendoci. Sono venti
     * campi e si guardano solo quando si compone la vetrina.
     */
    public static JSONArray loghi() {
        JSONArray a = new JSONArray();
        try {
            for (java.lang.reflect.Field f : R.drawable.class.getFields()) {
                if (f.getName().startsWith("logo_")) a.put(f.getName());
            }
        } catch (Exception e) {
            Log.w(TAG, "config: non ho potuto elencare i loghi", e);
        }
        return a;
    }

    /** Quante ne ha, in una riga da registro. */
    public static String quante(JSONObject o) {
        if (o == null) return "niente";
        return conta(o, "luci") + " luci, " + conta(o, "routine") + " routine, "
                + conta(o, "app") + " app, " + conta(o, "radio") + " stazioni";
    }

    private static int conta(JSONObject o, String nome) {
        JSONArray a = o.optJSONArray(nome);
        return a == null ? 0 : a.length();
    }

    // ---- i file, in basso --------------------------------------------------

    private static File esterno(Context c, String nome) {
        File dove = c.getExternalFilesDir(null);
        if (dove == null) return null;
        if (!dove.exists() && !dove.mkdirs()) return null;
        return new File(dove, nome);
    }

    private static JSONObject leggiFile(File f) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] tutto = new byte[(int) f.length()];
            int letti = 0;
            while (letti < tutto.length) {
                int n = in.read(tutto, letti, tutto.length - letti);
                if (n < 0) break;
                letti += n;
            }
            return new JSONObject(new String(tutto, 0, letti, "UTF-8"));
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignorata) { }
        }
    }
}
