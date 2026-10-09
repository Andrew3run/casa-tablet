package dev.casa;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * I comandi scritti da chi usa Casa, non da chi l'ha programmata.
 *
 * <h3>Perche' esistono</h3>
 *
 * {@link Comandi} sa fare una cinquantina di cose e risponde in un modo
 * deciso una volta per tutte dentro il codice. Va bene finche' le cose da dire
 * sono quelle e le risposte piacciono. Non va piu' bene nel momento in cui
 * qualcuno vuole che « spegni tutto » spenga anche la presa del corridoio, o
 * che a « buongiorno » Casa risponda con una frase sua invece che con una delle
 * tre scritte qui dentro: sono le due cose che una casa vera chiede subito, e
 * tutte e due volevano una ricompilazione.
 *
 * Un comando di questi dice tre cose, e sono esattamente le tre che servono:
 *
 * <ul>
 *   <li><b>quando</b> - le frasi che lo fanno scattare. Basta che la frase
 *       detta ne contenga una;
 *   <li><b>i passi</b> - che cosa fa. Sono gli stessi passi delle routine
 *       ({@link Routine.Passo}): una lampada, una frase girata a Casa, o
 *       un'attesa;
 *   <li><b>dici</b> - come risponde. Piu' di una, e ne sceglie una a caso.
 * </ul>
 *
 * <h3>Vengono prima di quelli scritti nel codice</h3>
 *
 * E' quello che li rende utili davvero: <b>si puo' cambiare una risposta che
 * non piace</b> senza toccare il codice, perche' la regola nuova prende la
 * frase prima che ci arrivi quella vecchia. Il prezzo e' che una regola scritta
 * male puo' rubare frasi che non voleva - scrivere « luce » in « quando »
 * significa prendersi anche « che luce c'e' fuori » - e per questo ogni scatto
 * finisce nel registro col nome della regola che ha vinto. Quando qualcosa
 * risponde in un modo che non ci si aspetta, la riga dice chi e' stato.
 *
 * <h3>Le risposte sanno dire l'ora</h3>
 *
 * Una risposta fissa basta per « buonanotte », non per « che ore sono ». Dentro
 * il testo si possono mettere dei segnaposto - {@code {ora}}, {@code {data}},
 * {@code {suona}}, {@code {luci}}, {@code {nome}} - e li riempie
 * {@link Comandi} al momento di parlare, con le stesse funzioni che usa per le
 * sue risposte.
 *
 * <h3>Perche' non un linguaggio</h3>
 *
 * La strada lunga era un piccolo linguaggio di regole - condizioni, variabili,
 * se-allora - e sarebbe stato piu' potente. Ma un linguaggio si impara, si
 * sbaglia e va spiegato, e qui chi scrive le regole lo fa da una finestra con
 * tre elenchi: frasi, passi, risposte. Tre elenchi si riempiono senza sapere
 * niente, e coprono quello che una casa chiede davvero.
 */
public final class ComandiUtente {

    private static final String TAG = "Casa";

    /** Un comando scritto dal PC. */
    public static final class Comando {
        public final String nome;
        public final List<String> quando;
        public final List<String> dici;
        public final Routine azione;

        Comando(String nome, List<String> quando, List<String> dici, Routine azione) {
            this.nome = nome;
            this.quando = quando;
            this.dici = dici;
            this.azione = azione;
        }

        /** La frase detta contiene una delle parole d'innesco? */
        boolean prende(String frase) {
            for (String q : quando) {
                if (q.length() > 0 && frase.contains(q)) return true;
            }
            return false;
        }
    }

    private final List<Comando> elenco = new ArrayList<Comando>();

    /** L'ultima risposta scelta, per comando: serve a non ripetere due volte di
     *  fila la stessa quando ce ne sono piu' d'una. Stessa regola di
     *  {@code Comandi.unaDi}: una risposta corretta e sempre identica, sentita
     *  venti volte, e' la cosa che piu' fa sembrare che dall'altra parte non ci
     *  sia nessuno. */
    private final java.util.HashMap<String, Integer> ultima =
            new java.util.HashMap<String, Integer>();
    private final java.util.Random sorte = new java.util.Random();

    public List<Comando> elenco() { return elenco; }

    public boolean vuoto() { return elenco.isEmpty(); }

    /**
     * Rilegge i comandi dalla configurazione.
     *
     * Una regola senza « quando » non si puo' far scattare, e una senza passi
     * ne' risposte non farebbe niente: tutte e due si saltano dicendolo. Meglio
     * una riga nel registro che una regola che c'e' ma non succede mai.
     */
    public void carica(android.content.Context c) {
        elenco.clear();
        JSONArray a = Configurazione.elenco(c, "comandi");
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String nome = o.optString("nome", "");
            List<String> quando = lista(o.optJSONArray("quando"));
            List<String> dici = lista(o.optJSONArray("dici"));
            // Le frasi d'innesco si confrontano con quella detta, che arriva
            // gia' in minuscolo: una scritta con la maiuscola non scatterebbe
            // mai, e a guardarla nell'elenco sembrerebbe giusta.
            for (int k = 0; k < quando.size(); k++) quando.set(k, pulita(quando.get(k)));
            Routine azione = Routine.da(o);
            if (quando.isEmpty()) {
                Log.i(TAG, "comandi: salto \"" + nome + "\", non ha nessuna frase che lo faccia scattare");
                continue;
            }
            if (dici.isEmpty() && azione.passi.isEmpty()) {
                Log.i(TAG, "comandi: salto \"" + nome + "\", non fa niente e non dice niente");
                continue;
            }
            elenco.add(new Comando(nome, quando, dici, azione));
        }
        if (!elenco.isEmpty()) Log.i(TAG, "comandi: " + elenco.size() + " scritti dal PC");
    }

    /** Le stringhe di un array JSON, in minuscolo e senza le vuote. */
    private static List<String> lista(JSONArray a) {
        List<String> fuori = new ArrayList<String>();
        if (a == null) return fuori;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (s.length() > 0) fuori.add(s);
        }
        return fuori;
    }

    /** Il primo comando che prende questa frase, o null. La frase arriva gia'
     *  in minuscolo, come la passa {@link Comandi}. */
    public Comando riconosci(String frase) {
        for (Comando c : elenco) {
            if (c.prende(frase)) return c;
        }
        return null;
    }

    /** Una delle risposte, mai due volte la stessa di fila. Vuoto se il comando
     *  non risponde: fare senza dire niente e' una scelta legittima - un
     *  « spegni la luce » non ha bisogno di commento. */
    public String risposta(Comando c) {
        if (c.dici.isEmpty()) return "";
        if (c.dici.size() == 1) return c.dici.get(0);
        Integer prima = ultima.get(c.nome);
        int i = sorte.nextInt(c.dici.size());
        if (prima != null && i == prima.intValue()) i = (i + 1) % c.dici.size();
        ultima.put(c.nome, Integer.valueOf(i));
        return c.dici.get(i);
    }

    /** Come si legge in una riga, per il registro. */
    public static String descrizione(Comando c) {
        StringBuilder b = new StringBuilder(c.nome);
        b.append(" (");
        for (int i = 0; i < c.quando.size(); i++) {
            if (i > 0) b.append(" / ");
            b.append(c.quando.get(i));
        }
        b.append(")");
        return b.toString();
    }

    /** In minuscolo, come le confronta {@link Comando#prende}. */
    static String pulita(String s) {
        // Senza accenti, come la frase che arrivera' a confrontarcisi: chi
        // scrive « accendi il camino perché fa freddo » come innesco deve
        // essere riconosciuto lo stesso. Vedi Testo.senzaAccenti.
        return s == null ? "" : Testo.senzaAccenti(s.toLowerCase(Locale.ITALIAN).trim());
    }
}
