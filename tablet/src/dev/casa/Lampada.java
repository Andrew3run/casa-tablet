package dev.casa;

import org.json.JSONObject;

/**
 * Una lampada di casa, come la conosce Casa.
 *
 * Identificativo, chiave locale e versione del protocollo non cambiano mai:
 * nascono quando la lampada viene accoppiata con l'app Smart Life e restano
 * quelli finche' non la si riaccoppia da capo. L'<b>indirizzo</b> invece
 * invecchia - basta un riavvio del router - ed e' l'unico campo non final:
 * {@link Luci} lo riallinea ascoltando gli annunci che le lampade mandano in
 * broadcast.
 *
 * Le lampade di partenza sono nell'elenco di fabbrica dentro {@link Luci}, con
 * le chiavi prese dall'altro progetto. L'elenco vero pero' e' quello scritto in
 * {@code casa.json} ({@link Configurazione}), che arriva dalla finestra sul PC
 * e che contiene anche gli indirizzi aggiornati.
 */
public final class Lampada {

    public final String nome;
    public final String id;
    public final String chiave;
    public final float versione;

    /** La tinta della sua tessera. Non e' decorazione: e' come si riconosce
     *  una lampada dall'altra senza leggere il nome. */
    public final int colore;

    public String ip;

    /** L'ultimo stato letto, oppure null se non le si e' ancora parlato. */
    public Tuya.Stato stato;

    /** Vero mentre un comando e' in volo: la tessera si disegna in attesa. */
    public boolean inCorso;

    /** Quando si e' sentito il suo ultimo annuncio, in uptime; 0 se mai. Li
     *  tocca solo il thread dell'interfaccia, come tutto il resto. */
    long sentita;

    /** Quando le si e' corso dietro l'ultima volta dopo un annuncio, e quante
     *  volte di fila e' andata a vuoto: vedi {@code Luci.sentita}. */
    long rincorsa;
    int rincorseAVuoto;

    private Tuya canale;

    public Lampada(String nome, String id, String ip, String chiave, float versione,
                   int colore) {
        this.nome = nome;
        this.id = id;
        this.ip = ip;
        this.chiave = chiave;
        this.versione = versione;
        this.colore = colore;
    }

    public static Lampada da(JSONObject o) {
        float v;
        try {
            v = Float.parseFloat(o.optString("versione", "3.3"));
        } catch (NumberFormatException storta) {
            v = 3.3f;
        }
        return new Lampada(
                o.optString("nome", ""),
                o.optString("id", ""),
                o.optString("ip", ""),
                o.optString("chiave", ""),
                v,
                Tinte.leggi(o.optString("colore", ""), Tinte.LUCI));
    }

    public JSONObject json() throws org.json.JSONException {
        JSONObject o = new JSONObject();
        o.put("nome", nome);
        o.put("id", id);
        o.put("ip", ip);
        o.put("chiave", chiave);
        o.put("versione", String.valueOf(versione));
        o.put("colore", Tinte.scrivi(colore));
        return o;
    }

    /**
     * Il canale verso questa lampada. Vive quanto la lampada e non quanto il
     * comando: dentro si deposita la mappa dei numeri scoperta alla prima
     * lettura - quale dp e' l'interruttore, quale la luminosita' - e rifarla a
     * ogni pressione sarebbe un giro in rete buttato.
     *
     * <b>Sincronizzato</b> perche' lo chiedono i thread delle corsie, e adesso
     * anche la bussata che tiene calda la strada: due che se lo costruiscono
     * insieme si porterebbero via a vicenda la mappa dei numeri appena
     * scoperta, e la lampada tornerebbe a sembrare piu' povera di com'e'.
     */
    public synchronized Tuya canale() {
        if (canale == null) {
            canale = new Tuya(id, ip, chiave, versione);
        } else if (!ip.equals(canale.ip())) {
            canale.setIp(ip);
        }
        return canale;
    }

    /** Accesa <b>per quanto ne sappiamo</b>: se non le si e' ancora parlato la
     *  risposta e' no, non "boh". La tessera spenta e' il caso giusto in cui
     *  cadere, perche' e' quello in cui premere accende. */
    public boolean accesa() {
        return stato != null && stato.raggiunta && stato.accesa;
    }

    /** Perche' non risponde, o null se risponde. */
    public String guasto() {
        if (stato == null || stato.raggiunta) return null;
        return stato.errore != null && stato.errore.length() > 0 ? stato.errore : "non risponde";
    }

    /** Utilizzabile solo se la chiave c'e': senza, non risponde a nessuno, e
     *  mettere in elenco una tessera che non puo' funzionare e' peggio che non
     *  metterla. */
    public boolean completa() {
        return id.length() > 0 && ip.length() > 0 && chiave.length() == 16;
    }
}
