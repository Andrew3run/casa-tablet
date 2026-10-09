package dev.casa;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Una routine: un tocco solo che fa succedere piu' cose, una dopo l'altra.
 *
 * "Buonanotte" spegne tutto; "Cinema" spegne la camera, mette il comodino
 * sull'arancione al venti per cento e apre Netflix. Sono le cose che si fanno
 * entrando o uscendo da una stanza, e farle una alla volta vuol dire cinque
 * tocchi e un'attesa fra l'uno e l'altro.
 *
 * <h3>Non sono piu' soltanto lampade</h3>
 *
 * Un passo aveva tre campi - quale luce, che azione, con che valore - e una
 * routine era per costruzione un fatto di lampadine. Ma "buonanotte" in una
 * casa vera spegne anche la radio, e "cinema" alza il volume: la sezione si
 * chiama Casa, non Luci, e la parte che restava fuori era proprio quella che
 * si sarebbe voluta.
 *
 * Adesso un passo dice prima di tutto <b>di che cosa parla</b>:
 *
 * <table>
 *   <tr><td>{@code luce}</td>
 *       <td>una lampada per identificativo, o tutte se il bersaglio e' vuoto.
 *           Azioni: on, off, inverti, luce, colore, bianco, bianchezza</td></tr>
 *   <tr><td>{@code frase}</td>
 *       <td>una frase detta a Casa, come se qualcuno l'avesse pronunciata:
 *           "metti rai radio 1", "spegni la radio", "volume al 30",
 *           "apri netflix". Vale <b>tutto</b> quello che Casa capisce</td></tr>
 *   <tr><td>{@code attesa}</td>
 *       <td>tot secondi fermi. Serve quando l'ordine non basta e conta anche
 *           il tempo: alzare il volume mentre la radio sta ancora partendo lo
 *           alza sulla cosa sbagliata</td></tr>
 * </table>
 *
 * <b>Perche' una frase e non una tabella di azioni.</b> La strada ovvia era
 * aggiungere i tipi uno per uno - radio, musica, volume, app - ognuno con i
 * suoi campi e il suo pezzo di codice che li esegue. Sarebbe stata una seconda
 * lingua per dire quello che {@link Comandi} sa gia' capire, da tenere
 * d'accordo con la prima per sempre: ogni comando nuovo sarebbe andato scritto
 * due volte, e la seconda ci si dimentica. Passando la frase a {@code Comandi}
 * si eredita tutto quello che Casa impara, il giorno stesso in cui lo impara,
 * e si prova scrivendola nella casella "prova una frase" del programma sul PC.
 *
 * I passi vanno <b>in ordine e uno per volta</b>: due comandi in parallelo
 * verso la stessa lampada finirebbero uno sopra l'altro, perche' una lampada
 * Tuya accetta una connessione sola alla volta. E "spegni tutto poi accendi il
 * comodino" deve succedere in quest'ordine, non a caso.
 *
 * Le routine si scrivono dal PC, in Gestione Home, e arrivano dentro
 * {@link Configurazione}. Quelle di fabbrica in {@link Luci} restano come punto
 * di partenza per un tablet appena installato.
 */
public final class Routine {

    /** Un passo che tocca una lampada. */
    public static final String LUCE = "luce";
    /** Un passo che dice una frase a Casa, come farebbe una persona. */
    public static final String FRASE = "frase";
    /** Un passo che aspetta e basta. */
    public static final String ATTESA = "attesa";

    public final String nome;
    public final int colore;
    /** Il nome dell'icona scelto dal PC, oppure vuoto: allora si indovina dal
     *  nome della routine (vedi {@code SezioneLuci.iconaRoutine}). */
    public final String icona;
    public final List<Passo> passi;

    /** Vero mentre la routine sta girando: la tessera si disegna in attesa. */
    public boolean inCorso;

    public Routine(String nome, int colore, String icona, List<Passo> passi) {
        this.nome = nome;
        this.colore = colore;
        this.icona = icona == null ? "" : icona;
        this.passi = passi;
    }

    /** Un passo: di che cosa parla, a chi, cosa fare, con che valore. */
    public static final class Passo {
        /** luce | frase | attesa */
        public final String cosa;
        /** Per {@code luce}: l'identificativo della lampada, o vuoto per dire
         *  "tutte". Per gli altri non serve. */
        public final String bersaglio;
        /** on | off | inverti | luce | colore | bianco | bianchezza */
        public final String azione;
        /** La percentuale per "luce", il colore #RRGGBB per "colore", la frase
         *  per "frase", i secondi per "attesa". */
        public final String valore;

        public Passo(String cosa, String bersaglio, String azione, String valore) {
            this.cosa = cosa == null || cosa.length() == 0 ? LUCE : cosa;
            this.bersaglio = bersaglio == null ? "" : bersaglio;
            this.azione = azione == null ? "" : azione;
            this.valore = valore == null ? "" : valore;
        }

        public boolean e(String quale) { return quale.equals(cosa); }

        /**
         * Un passo letto da JSON.
         *
         * <b>Legge anche il formato vecchio.</b> Prima un passo era
         * {@code {luce, azione, valore}} e non diceva di che cosa parlava,
         * perche' parlava sempre di lampade. Senza "cosa" si assume quello, e
         * il bersaglio si prende dal campo "luce": un {@code casa.json} scritto
         * dalla versione precedente continua a funzionare invece di
         * trasformarsi in una routine che non fa niente - che e' il modo in cui
         * un cambio di formato fa danno senza dare errori.
         */
        public static Passo da(JSONObject o) {
            String cosa = o.optString("cosa", "");
            String bersaglio = o.optString("bersaglio", o.optString("luce", ""));
            return new Passo(cosa, bersaglio,
                    o.optString("azione", ""),
                    o.optString("valore", ""));
        }

        public JSONObject json() throws org.json.JSONException {
            JSONObject o = new JSONObject();
            o.put("cosa", cosa);
            o.put("bersaglio", bersaglio);
            o.put("azione", azione);
            o.put("valore", valore);
            return o;
        }

        /** Come si legge in una riga, sul PC e nel registro. */
        public String descrizione() {
            if (e(FRASE)) return "di' \"" + valore + "\"";
            if (e(ATTESA)) return "aspetta " + valore + " s";
            String chi = bersaglio.length() == 0 ? "tutte" : bersaglio;
            return chi + " -> " + azione + (valore.length() > 0 ? " " + valore : "");
        }
    }

    public static Routine da(JSONObject o) {
        List<Passo> passi = new ArrayList<Passo>();
        JSONArray a = o.optJSONArray("passi");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject p = a.optJSONObject(i);
                if (p == null) continue;
                passi.add(Passo.da(p));
            }
        }
        return new Routine(
                o.optString("nome", ""),
                Tinte.leggi(o.optString("colore", ""), Tinte.LUCI),
                o.optString("icona", ""),
                passi);
    }

    public JSONObject json() throws org.json.JSONException {
        JSONArray a = new JSONArray();
        for (Passo p : passi) a.put(p.json());
        JSONObject o = new JSONObject();
        o.put("nome", nome);
        o.put("colore", Tinte.scrivi(colore));
        o.put("icona", icona);
        o.put("passi", a);
        return o;
    }
}
