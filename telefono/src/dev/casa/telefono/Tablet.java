package dev.casa.telefono;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Il tablet, visto dal telefono: dove sta, con che chiave gli si parla, e la
 * richiesta firmata.
 *
 * <b>Tutto bloccante, e di proposito.</b> Lo chiamano solo l'Executor della
 * schermata e il job della sveglia, mai il thread dell'interfaccia: una
 * richiesta sincrona su un filo suo si legge dall'alto in basso, una catena di
 * callback no.
 *
 * <h3>La firma</h3>
 *
 * HMAC-SHA256 di {@code METODO \n percorso \n ora \n corpo} con la chiave avuta
 * all'abbinamento. La chiave non viaggia mai dopo quella volta, e l'ora dentro
 * la firma fa si' che una richiesta registrata non si possa rigiocare dopo
 * cinque minuti - e prima nemmeno, perche' il tablet ricorda le firme viste.
 */
public final class Tablet {

    public static final int PORTA = 8776;

    private static final String PREF = "tablet";

    /** Una risposta: il codice HTTP e il JSON, o null se il tablet non c'era. */
    public static final class Risposta {
        public final int codice;
        public final JSONObject json;
        Risposta(int codice, JSONObject json) { this.codice = codice; this.json = json; }
        public boolean ok() { return codice == 200 && json != null && json.optBoolean("ok", false); }
        public String errore() {
            if (codice == 401) return "il tablet non ci riconosce: abbina di nuovo";
            if (json != null && json.optString("errore", "").length() > 0) return json.optString("errore");
            return "risposta " + codice;
        }
    }

    /** Il tablet non ha risposto: fuori casa, tablet spento, indirizzo cambiato. */
    public static final class Irraggiungibile extends Exception {
        Irraggiungibile(String perche) { super(perche); }
    }

    private final SharedPreferences p;

    public Tablet(Context c) {
        p = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---- quello che si ricorda --------------------------------------------

    public String indirizzo()  { return p.getString("indirizzo", ""); }
    public String idTelefono() { return p.getString("telefono", ""); }
    public String nomeTablet() { return p.getString("nomeTablet", "Casa"); }
    public boolean abbinato()  { return idTelefono().length() > 0 && p.getString("chiave", "").length() == 64; }

    public void setIndirizzo(String ip) { p.edit().putString("indirizzo", ip == null ? "" : ip.trim()).apply(); }

    public void dissocia() {
        p.edit().remove("telefono").remove("chiave").apply();
    }

    public boolean segueLaSveglia() { return p.getBoolean("segui", false); }
    public void setSegueLaSveglia(boolean si) { p.edit().putBoolean("segui", si).apply(); }

    /** L'ultimo valore della sveglia arrivato davvero al tablet: -1 se mai. */
    public long svegliaInviata() { return p.getLong("inviata", -1L); }
    public void setSvegliaInviata(long quando) { p.edit().putLong("inviata", quando).apply(); }

    // ---- abbinamento --------------------------------------------------------

    public Risposta chiediCodice(String nome) throws Irraggiungibile {
        return manda("POST", "/abbina/chiedi", corpoNome(nome, null), false);
    }

    public Risposta abbina(String nome, String codice) throws Irraggiungibile {
        Risposta r = manda("POST", "/abbina", corpoNome(nome, codice), false);
        if (r.ok()) {
            String id = r.json.optString("telefono", "");
            String chiave = r.json.optString("chiave", "");
            if (id.length() > 0 && chiave.length() == 64) {
                p.edit().putString("telefono", id).putString("chiave", chiave)
                        .putString("nomeTablet", r.json.optString("tablet", "Casa")).apply();
            }
        }
        return r;
    }

    private static String corpoNome(String nome, String codice) {
        try {
            JSONObject o = new JSONObject();
            o.put("nome", nome);
            if (codice != null) o.put("codice", codice);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ---- richieste firmate ----------------------------------------------------

    /** Quanto si aspetta una risposta normale. La ricerca e i brani di una
     *  playlist passano da Internet e chiedono di piu': si passa l'attesa. */
    public static final int ATTESA = 6000;

    public Risposta get(String percorso) throws Irraggiungibile {
        return get(percorso, ATTESA);
    }

    public Risposta get(String percorso, int attesaMs) throws Irraggiungibile {
        return manda("GET", percorso, "", true, attesaMs);
    }

    public Risposta post(String percorso, JSONObject corpo) throws Irraggiungibile {
        return post(percorso, corpo, ATTESA);
    }

    public Risposta post(String percorso, JSONObject corpo, int attesaMs) throws Irraggiungibile {
        return manda("POST", percorso, corpo == null ? "{}" : corpo.toString(), true, attesaMs);
    }

    /** Un JSON al volo: {@code json("id", 12, "attiva", false)}. */
    public static JSONObject json(Object... coppie) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < coppie.length; i += 2) o.put((String) coppie[i], coppie[i + 1]);
        } catch (Exception ignorata) { }
        return o;
    }

    private Risposta manda(String metodo, String percorso, String corpo, boolean firmata)
            throws Irraggiungibile {
        return manda(metodo, percorso, corpo, firmata, ATTESA);
    }

    private Risposta manda(String metodo, String percorso, String corpo, boolean firmata, int attesaMs)
            throws Irraggiungibile {
        String ip = indirizzo();
        if (ip.length() == 0) throw new Irraggiungibile("non so dove sta il tablet");
        return manda(ip, metodo, percorso, corpo, firmata, attesaMs);
    }

    /** Anche verso un indirizzo che non e' ancora quello salvato: serve alla
     *  ricerca, che prova un candidato prima di adottarlo. */
    public Risposta manda(String ip, String metodo, String percorso, String corpo, boolean firmata)
            throws Irraggiungibile {
        return manda(ip, metodo, percorso, corpo, firmata, ATTESA);
    }

    public Risposta manda(String ip, String metodo, String percorso, String corpo, boolean firmata, int attesaMs)
            throws Irraggiungibile {
        HttpURLConnection c = null;
        try {
            byte[] dati = corpo.getBytes("UTF-8");
            c = (HttpURLConnection) new URL("http://" + ip + ":" + PORTA + percorso).openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(attesaMs);
            c.setUseCaches(false);
            c.setRequestMethod(metodo);
            c.setRequestProperty("Connection", "close");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (firmata) {
                if (!abbinato()) return new Risposta(401, null);
                String ora = String.valueOf(System.currentTimeMillis());
                c.setRequestProperty("X-Casa-Telefono", idTelefono());
                c.setRequestProperty("X-Casa-Ora", ora);
                c.setRequestProperty("X-Casa-Firma",
                        firma(p.getString("chiave", ""), metodo + "\n" + percorso + "\n" + ora + "\n" + corpo));
            }
            if ("POST".equals(metodo)) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(dati.length);
                OutputStream out = c.getOutputStream();
                out.write(dati);
                out.close();
            }
            int codice = c.getResponseCode();
            InputStream in = codice >= 400 ? c.getErrorStream() : c.getInputStream();
            JSONObject json = null;
            if (in != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                try { json = new JSONObject(b.toString("UTF-8")); } catch (Exception nonJson) { }
            }
            return new Risposta(codice, json);
        } catch (Exception e) {
            throw new Irraggiungibile("il tablet non risponde");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String firma(String chiaveHex, String messaggio) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(daHex(chiaveHex), "HmacSHA256"));
        byte[] h = mac.doFinal(messaggio.getBytes("UTF-8"));
        StringBuilder s = new StringBuilder(h.length * 2);
        for (byte x : h) {
            s.append(Character.forDigit((x >> 4) & 0xF, 16));
            s.append(Character.forDigit(x & 0xF, 16));
        }
        return s.toString();
    }

    private static byte[] daHex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    // ---- trovarlo ----------------------------------------------------------------

    /**
     * Si assicura che l'indirizzo salvato sia quello giusto, cercandolo se no.
     *
     * Prima si prova l'ultimo buono - e' quasi sempre lui, e costa una
     * connessione - poi la ricerca NSD. Torna vero se alla fine c'e' un tablet
     * che risponde.
     */
    public boolean trova(Context c, long attesaMs) {
        String ip = indirizzo();
        if (ip.length() > 0 && risponde(ip)) return true;
        String trovato = Scoperta.cerca(c, attesaMs);
        if (trovato == null || !risponde(trovato)) return false;
        setIndirizzo(trovato);
        return true;
    }

    /** Qualcuno risponde da Casa su quell'indirizzo? /abbina/chiedi no - fa
     *  comparire un codice sullo schermo - quindi si bussa con una richiesta
     *  qualunque: anche un 401 vuol dire che dall'altra parte c'e' Casa. */
    private boolean risponde(String ip) {
        try {
            Risposta r = manda(ip, "GET", "/stato", "", abbinato());
            return r.codice == 200 || r.codice == 401;
        } catch (Irraggiungibile no) {
            return false;
        }
    }
}
