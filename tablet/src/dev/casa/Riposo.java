package dev.casa;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

/**
 * Chi decide quando Casa si mette a riposo, e quando torna.
 *
 * <h3>Quando compare</h3>
 *
 * Dopo qualche minuto che <b>nessuno tocca niente</b> e che <b>non c'e' niente
 * in corso</b>. Le due condizioni sono diverse e servono tutte e due: la prima
 * dice che non c'e' nessuno davanti, la seconda dice che quello che si vede
 * sullo schermo serve ancora a qualcuno anche se nessuno lo sta toccando.
 *
 * Sono in corso, e quindi tengono sveglia Casa: una <b>radio accesa</b> - con
 * il suo logo e le colonne del suono, che e' proprio quello che si vuole
 * vedere dall'altra parte della cucina - la <b>musica</b>, dentro o fuori da
 * Casa, un <b>timer che scorre</b> (un conto alla rovescia coperto da una
 * fotografia e' un conto alla rovescia inutile), una <b>vela gia' aperta</b> -
 * la sveglia che suona, il meteo, le impostazioni - e il <b>microfono
 * aperto</b>, perche' qualcuno sta parlando proprio adesso.
 *
 * <h3>Perche' un timer solo, e che si sposta</h3>
 *
 * Non c'e' nessun controllo periodico: c'e' <b>un</b> appuntamento, spostato
 * piu' avanti ogni volta che qualcuno tocca lo schermo
 * ({@code onUserInteraction} in {@link MainActivity}). Su un apparecchio che
 * sta fermo per ore, un controllo ogni dieci secondi sarebbe qualche migliaio
 * di risvegli al giorno per non fare niente.
 *
 * Quando l'appuntamento arriva e Casa e' occupata, non si rinuncia: si sposta
 * di un minuto e si riprova. Cosi' la radio spenta alle undici di sera fa
 * comparire il riposo un minuto dopo, senza che nessuno debba toccare niente.
 *
 * <h3>Il ritorno</h3>
 *
 * Un tocco qualunque, e si torna <b>alla Home</b> - non alla sezione dov'era
 * rimasta Casa un'ora prima. Chi tocca lo schermo di un tablet appeso al muro
 * vuole l'orologio, il tempo che fa e le luci: se voleva la sezione Radio, e'
 * a un dito di distanza nella barra. La sezione di due ore fa, invece, non l'ha
 * scelta nessuno - c'e' rimasta.
 */
public final class Riposo {

    /** Sotto {@code Casa} come tutto il resto: il perche' sta su
     *  {@link MainActivity#TAG}. */
    private static final String TAG = "Casa";

    /** Quanto si aspetta prima di mettersi a riposo, se non lo dice il PC. */
    private static final int ATTESA_DI_FABBRICA = 5;

    /** Ogni quanti minuti cambia la fotografia. */
    private static final int FOTO_DI_FABBRICA = 3;

    /** Quando l'ora e' arrivata ma Casa e' occupata, si riprova fra un minuto:
     *  cosi' il riposo compare da solo appena finisce quel che c'era. */
    private static final long RIPROVA = 60000L;

    /** Chi sa dire se in questo momento c'e' qualcosa in corso. La risposta
     *  cambia di minuto in minuto, quindi non e' un valore ma una domanda. */
    public interface Faccende { boolean inCorso(); }

    /** Chi vuole sapere quando il riposo compare e quando se ne va: la
     *  {@link Notte}, che abbassa lo schermo trenta secondi dopo. */
    public interface Presenza { void comparso(); void andato(); }

    private Presenza presenza;

    public void setPresenza(Presenza p) { presenza = p; }

    private final Context contesto;
    private final Misure m;
    private final Telaio telaio;
    private final Faccende faccende;
    private final Handler orologio = new Handler(Looper.getMainLooper());

    private Paesaggi paesaggi;
    private Meteo meteo;
    private Appunti appunti;
    private Calendario calendario;
    private Notizie notizie;
    private VelaRiposo vela;

    private boolean acceso = true;
    private boolean rete = true;
    private int attesa = ATTESA_DI_FABBRICA;
    private int minutiFoto = FOTO_DI_FABBRICA;

    /** Casa e' sullo schermo? Fra onPause e onResume il conto e' fermo: nessuno
     *  puo' vedere un riposo che compare dietro Netflix. */
    private boolean inScena;

    private final Runnable scatto = new Runnable() {
        @Override public void run() { eOra(); }
    };

    public Riposo(Context c, Misure misure, Telaio telaio, Faccende faccende) {
        this.contesto = c;
        this.m = misure;
        this.telaio = telaio;
        this.faccende = faccende;
        applica(Configurazione.leggi(c));
    }

    public void setMeteo(Meteo meteo) {
        this.meteo = meteo;
        if (vela != null) vela.setMeteo(meteo);
    }

    /** Le notizie principali, che si alternano all'agenda nell'angolo in
     *  alto: vedi {@link VelaRiposo}. */
    public void setNotizie(Notizie n) {
        this.notizie = n;
        if (vela != null) vela.setNotizie(n);
    }

    /** Le note e gli impegni, che il riposo mostra insieme all'ora. */
    public void setAgenda(Appunti a, Calendario c) {
        this.appunti = a;
        this.calendario = c;
        if (vela != null) vela.setAgenda(a, c);
    }

    /**
     * Qualcosa e' cambiato in agenda mentre il riposo e' in scena.
     *
     * Non fa niente se non c'e' nessuno a guardare, ed e' il punto: una nota
     * aggiunta a voce con lo schermo a riposo <b>compare</b>, con la stessa
     * animazione con cui sarebbe entrata all'inizio.
     */
    public void rinfresca() {
        if (vela != null) vela.rifaiIngresso();
    }

    /**
     * Com'e' stato configurato dal PC:
     *
     * <pre>
     *   "riposo": { "acceso": true, "attesa": 5, "foto": 3, "rete": true }
     * </pre>
     *
     * {@code attesa} e {@code foto} sono minuti; {@code rete} dice se le foto
     * si possono andare a prendere da Bing, e serve a chi non vuole che il
     * tablet chieda niente a nessuno.
     */
    public void applica(JSONObject config) {
        JSONObject r = config == null ? null : config.optJSONObject("riposo");
        if (r != null) {
            acceso = r.optBoolean("acceso", true);
            attesa = Math.max(1, r.optInt("attesa", ATTESA_DI_FABBRICA));
            minutiFoto = Math.max(0, r.optInt("foto", FOTO_DI_FABBRICA));
            rete = r.optBoolean("rete", true);
            if (paesaggi != null) paesaggi.setRete(rete);
        }
        if (!acceso && vela != null) via();
        riprogramma();
    }

    /**
     * Cambia e mette per iscritto: la chiama la pagina delle impostazioni del
     * tablet. Le foto e la rete restano come le ha scritte il PC.
     */
    public void imposta(boolean acceso, int attesa) {
        this.acceso = acceso;
        this.attesa = Math.max(1, attesa);
        try {
            JSONObject r = new JSONObject();
            r.put("acceso", acceso);
            r.put("attesa", this.attesa);
            r.put("foto", minutiFoto);
            r.put("rete", rete);
            Configurazione.metti(contesto, "riposo", r);
        } catch (Exception e) {
            Log.w(TAG, "riposo: non ho potuto salvare", e);
        }
        if (!acceso && vela != null) via();
        riprogramma();
        Log.i(TAG, "riposo: " + stato());
    }

    public boolean acceso() { return acceso; }
    public int attesa() { return attesa; }

    // ---- il conto ----------------------------------------------------------

    /** Casa e' tornata sullo schermo: il conto riparte da adesso. */
    public void riparti() {
        inScena = true;
        riprogramma();
    }

    /** Casa non e' piu' sullo schermo: niente conto, e se il riposo era in
     *  scena se ne va - cosi' le sue due bitmap tornano indietro subito, che e'
     *  proprio il momento in cui servono a qualcun altro. */
    public void fermati() {
        inScena = false;
        orologio.removeCallbacks(scatto);
        if (vela != null) via();
    }

    /**
     * Qualcuno ha toccato lo schermo: si ricomincia a contare.
     *
     * <b>Non toglie il riposo</b>, di proposito. Questa la chiama
     * {@code onUserInteraction}, che arriva <i>prima</i> che il tocco venga
     * consegnato: togliendo la vela qui, il dito che voleva solo svegliare il
     * tablet finirebbe dritto sul pulsante che c'era sotto. A togliere il
     * riposo e' la vela stessa, che si prende il tocco intero - fino al dito
     * che si alza - e non lo passa a nessuno.
     */
    public void sveglia() {
        if (vela != null) return;
        riprogramma();
    }

    private void riprogramma() {
        orologio.removeCallbacks(scatto);
        if (!acceso || !inScena || vela != null) return;
        orologio.postDelayed(scatto, attesa * 60000L);
    }

    private void eOra() {
        if (!acceso || !inScena || vela != null) return;
        if (faccende != null && faccende.inCorso()) {
            // C'e' qualcosa in corso: non si rinuncia, si riprova fra poco.
            orologio.postDelayed(scatto, RIPROVA);
            return;
        }
        mostra();
    }

    // ---- la vela -----------------------------------------------------------

    /** Adesso, senza aspettare: la chiama il PC per vedere com'e' venuto. */
    public void subito() {
        if (vela == null) mostra();
    }

    private void mostra() {
        if (telaio == null || telaio.ceUnaVela()) return;
        if (paesaggi == null) paesaggi = magazzino();
        vela = new VelaRiposo(contesto, m, paesaggi, minutiFoto, new VelaRiposo.Fine() {
            @Override public void suPosato() {
                // Alla Home, non dove Casa era rimasta: il perche' sta in cima
                // a questa classe. Si cambia sotto la vela, che il tocco lo
                // tiene lei finche' il dito non si alza.
                telaio.vaiA(0);
            }
            @Override public void suTocco() {
                via();
            }
            @Override public void suUscita() {
                // Comunque sia uscita - il dito, il tasto indietro, una sveglia
                // che le e' passata sopra - da qui in poi si ricomincia a
                // contare.
                vela = null;
                if (presenza != null) presenza.andato();
                riprogramma();
            }
        });
        vela.setMeteo(meteo);
        vela.setNotizie(notizie);
        vela.setAgenda(appunti, calendario);
        telaio.mostra(vela);
        if (presenza != null) presenza.comparso();
        Log.i(TAG, "riposo: in scena"
                + (paesaggi.quante() > 0 ? " con " + paesaggi.quante() + " foto"
                                         : " senza foto, per adesso"));
    }

    private void via() {
        if (vela == null) return;
        // nascondiVela chiama suVelaTolta, che chiama suUscita: il campo lo
        // azzera quello, e riprogramma() riparte di li'.
        telaio.nascondiVela();
    }

    public boolean inScena() { return vela != null; }

    /** Il magazzino delle foto, quando serve davvero: costa un thread, e un
     *  tablet che non si mette mai a riposo non deve pagarlo. */
    private Paesaggi magazzino() {
        Paesaggi p = new Paesaggi(contesto, m);
        p.setRete(rete);
        return p;
    }

    /** Va a vedere se ci sono foto nuove. La chiede il PC. */
    public void aggiornaFoto() {
        if (paesaggi == null) paesaggi = magazzino();
        paesaggi.aggiorna();
    }

    /** Come sta, in una riga da registro. */
    public String stato() {
        return (acceso ? "acceso" : "spento")
                + ", dopo " + attesa + (attesa == 1 ? " minuto" : " minuti")
                + ", foto ogni " + (minutiFoto == 0 ? "mai" : minutiFoto + " min")
                + (vela != null ? ", in scena" : "")
                + (paesaggi != null ? " - " + paesaggi.stato() : " - foto non ancora guardate");
    }

    /** Casa se ne va: si restituisce tutto, thread delle foto compreso. */
    public void chiudi() {
        orologio.removeCallbacks(scatto);
        vela = null;
        if (paesaggi != null) { paesaggi.spegni(); paesaggi = null; }
    }
}
