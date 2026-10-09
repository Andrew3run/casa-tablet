package dev.casa;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Random;

/**
 * Quello che Casa risponde, e come lo si cambia senza ricompilare.
 *
 * <h3>Il problema</h3>
 *
 * Le risposte erano stringhe scritte dentro {@link Comandi}, in mezzo alle
 * regole: {@code rispondi("Radio spenta.")}. Funzionano, ma sono la cosa che si
 * sente venti volte al giorno, e la prima che si vuole cambiare - e per
 * cambiarne una bisognava avere il progetto, l'SDK e trenta secondi di
 * compilazione. Le due domande che ci si fa davanti a un assistente sono
 * "perche' non capisce questa frase" e "perche' risponde cosi'": alla prima ha
 * risposto {@link ComandiUtente}, a questa risponde questo file.
 *
 * <h3>Come e' fatto</h3>
 *
 * Ogni risposta fissa ha un <b>nome</b> - {@code radio.spenta}, {@code
 * musica.pausa} - un gruppo per ritrovarla in un elenco, e una o piu' varianti
 * di serie. Chi ne riscrive una dal PC mette le sue varianti in
 * {@code casa.json}, sotto {@code risposte}, e da quel momento vincono quelle.
 *
 * Il nome e non il testo, e la differenza conta: <b>"Radio spenta." compare in
 * tre punti diversi di Comandi</b>, ed e' la stessa risposta - riscriverla una
 * volta la cambia in tutti e tre. Se la chiave fosse stata il testo,
 * cambiandolo in un punto gli altri due sarebbero rimasti indietro senza dirlo.
 *
 * <h3>Piu' modi di dire la stessa cosa</h3>
 *
 * Alcune ne hanno gia' due o tre di serie, e la ragione e' scritta in Comandi:
 * una risposta corretta e sempre identica, sentita venti volte al giorno, e' la
 * cosa che piu' di ogni altra fa sembrare che dall'altra parte non ci sia
 * nessuno. Chi le riscrive puo' metterne quante vuole - o una sola, se
 * preferisce sapere sempre cosa aspettarsi.
 *
 * <h3>Quello che resta fuori</h3>
 *
 * <b>Quello che non si dice affatto.</b> Alzare il volume, mettere in pausa,
 * saltare al brano dopo, spegnere la radio: sono cose che <b>si sentono</b>, e
 * la voce che le annuncia arriva dopo il fatto abbassando proprio quello che si
 * stava ascoltando. Quelle risposte restano scritte sullo schermo e non hanno
 * una voce da riscrivere - {@code volume.su} e {@code volume.giu} erano in
 * questo elenco e sono state tolte. Il ragionamento per esteso sta su
 * {@code Comandi.segna}.
 *
 * Le risposte <b>composte</b> - "Metto Levante.", "Sono le 7 e 20." - non sono
 * qui, perche' non sono un testo: sono un testo piu' un pezzo che cambia ogni
 * volta. Per cambiare quelle c'e' l'altra strada, e copre tutto: un comando
 * scritto da te che prende la frase prima e risponde come vuoi, con i
 * segnaposto {ora}, {data}, {suona}, {luci}, {nome}.
 *
 * <b>Questo file e' generato</b>: il catalogo e' stato estratto da Comandi.java
 * con uno script, cosi' i testi di serie sono esattamente quelli che Casa
 * diceva prima. Chi aggiunge una risposta nuova aggiunge una riga qui e la
 * chiama per nome di la'.
 */
public final class Risposte {

    private static final String TAG = "Casa";

    /** nome, gruppo, e poi le varianti di serie. */
    private static final String[][] BASE = {
        { "luci.bianche", "Luci", "Luci bianche." },
        { "meteo.assente", "Meteo", "Il meteo non ce l'ho ancora: mi manca il collegamento." },
        { "musica.acaso", "Musica", "Vado a caso.", "Le mischio." },
        { "musica.avanti", "Musica", "Avanti.", "La prossima." },
        { "musica.ferma", "Musica", "Musica ferma.", "Fatto." },
        { "musica.indietro", "Musica", "Indietro.", "Quella di prima." },
        { "musica.accendo", "Musica", "Accendo la musica, un momento." },
        { "musica.approva", "Musica", "Devi ancora approvare il codice dal telefono." },
        { "musica.cosacerco", "Musica", "Cosa cerco?" },
        { "musica.niente", "Musica", "Non c'e' niente in riproduzione.", "Non sta suonando niente." },
        { "musica.nonparte", "Musica", "La musica non parte." },
        { "musica.pausa", "Musica", "In pausa." },
        { "niente.dafermare", "Il resto", "Non c'era niente in funzione.", "Non stava suonando niente.", "Era gia' tutto fermo." },
        { "musica.rimetto", "Musica", "Rimetto la musica." },
        { "musica.riprendo", "Musica", "Riprendo." },
        { "musica.scegli", "Musica", "Non so ancora cosa mettere: scegli una playlist." },
        { "musica.scollegata", "Musica", "La musica non e' collegata." },
        { "musica.senzachiave", "Musica", "Per cercare mi serve la chiave di Spotify. E' scritto in musica.md." },
        { "agenda.niente", "Agenda", "Non hai impegni in vista.", "Il calendario e' libero." },
        { "agenda.senza", "Agenda", "Il calendario non e' collegato.", "Non ho un calendario da guardare." },
        { "nota.cosa", "Agenda", "Cosa devo segnare?", "Dimmi cosa scrivere." },
        { "nota.nontrovo", "Agenda", "Non trovo quella nota.", "Quella non ce l'ho in lista." },
        { "nota.presa", "Agenda", "Segnato.", "Preso nota.", "E' in lista." },
        { "nota.spuntata", "Agenda", "Fatto.", "Spuntata.", "Tolta dalla lista." },
        { "nota.vuota", "Agenda", "Non hai niente da fare.", "La lista e' vuota." },
        { "spesa.presa", "Agenda", "Aggiunto alla spesa.", "E' nella lista della spesa.", "Segnato nella spesa." },
        { "spesa.cosa", "Agenda", "Cosa devo aggiungere alla spesa?" },
        { "spesa.vuota", "Agenda", "La lista della spesa e' vuota.", "Non c'e' niente da comprare." },
        { "radio.cambio", "Radio", "Cambio stazione." },
        { "radio.giaspenta", "Radio", "La radio era gia' spenta.", "Non c'era la radio." },
        { "radio.messa", "Radio", "Metto la radio." },
        { "radio.spenta", "Radio", "Radio spenta.", "Spengo la radio." },
        { "saluti.dove", "Chi e'", "Sono il tablet appeso qui.", "Qui, sul muro. Non e' che mi muova molto." },
        { "saluti.eta", "Chi e'", "Sto su questo muro da poco.", "Da poco. Sono arrivato da poco qui." },
        { "saluti.figurati", "Chi e'", "Di niente.", "Figurati.", "Quando vuoi." },
        // L'unica con un pezzo che cambia: {parola} e' la frase suggerita.
        { "saluti.forse", "Chi e'", "Non sono sicuro. Volevi dire {parola}?", "Intendevi {parola}?", "Forse volevi dire {parola}. Provo?" },
        { "saluti.grazie", "Chi e'", "Grazie. Faccio quel che so fare." },
        { "saluti.nome", "Chi e'", "Mi chiamo Home, e mi occupo della casa.", "Sono Home. Mi occupo della casa.", "Home. Sono io che tengo insieme le cose, qui." },
        { "saluti.nonso", "Chi e'", "Questa non la so fare. Chiedimi cosa so fare, se vuoi.", "Questa mi manca. Se vuoi ti dico cosa so fare.", "Non ci arrivo. Chiedimi cosa so fare e te lo elenco." },
        { "saluti.origine", "Chi e'", "Mi ha messo insieme chi vive qui, un pezzo alla volta.", "Sono fatto in casa. Un pezzo per volta." },
        { "saluti.prego", "Chi e'", "Figurati." },
        { "saluti.presente", "Chi e'", "Ti sento.", "Sono qui.", "Dimmi." },
        { "sveglia.acheora", "Timer e sveglie", "A che ora?", "Per che ora?" },
        { "sveglia.zitta", "Timer e sveglie", "Sveglia spenta.", "Fatto." },
        { "timer.diquanto", "Timer e sveglie", "Di quanto?" },
        { "timer.troppolungo", "Timer e sveglie", "Troppo lungo: al massimo un giorno." },
        { "tutto.fermo", "Il resto", "Fatto.", "Silenzio.", "Ecco." },
        { "tutto.spento", "Il resto", "Spengo tutto.", "Chiudo tutto.", "Buonanotte." },
        { "tutto.vabene", "Il resto", "Va bene.", "D'accordo.", "Fatto." },
        { "volume.bloccato", "Volume", "Non riesco a toccare il volume." },
        { "volume.fuoriscala", "Volume", "Il volume va da zero a cento." },
    };

    /** Le riscritte, per nome. Vuoto: valgono quelle di serie. */
    private final HashMap<String, String[]> mie = new HashMap<String, String[]>();

    /** L'ultima variante detta, per nome: serve a non ripetere due volte di
     *  fila la stessa quando ce n'e' piu' d'una. A caso puro capita, e capita
     *  proprio quando da' piu' fastidio. */
    private final HashMap<String, Integer> ultima = new HashMap<String, Integer>();
    private final Random sorte = new Random();

    /**
     * Rilegge quelle riscritte dal PC.
     *
     * Un nome che non esiste nel catalogo si salta dicendolo: vuol dire che il
     * PC ha una configurazione di una versione piu' nuova, o che qualcuno ha
     * scritto un nome a mano sbagliandolo. Meglio una riga nel registro che una
     * risposta che non si sa perche' non cambia.
     */
    public void carica(Context c) {
        mie.clear();
        JSONObject tutto = Configurazione.leggi(c);
        JSONObject o = tutto == null ? null : tutto.optJSONObject("risposte");
        if (o == null) return;
        java.util.Iterator<String> nomi = o.keys();
        while (nomi.hasNext()) {
            String nome = nomi.next();
            if (!ceNelCatalogo(nome)) {
                Log.i(TAG, "risposte: \"" + nome + "\" non e' una risposta che conosco");
                continue;
            }
            JSONArray a = o.optJSONArray(nome);
            if (a == null || a.length() == 0) continue;
            java.util.ArrayList<String> varianti = new java.util.ArrayList<String>();
            for (int i = 0; i < a.length(); i++) {
                String v = a.optString(i, "").trim();
                if (v.length() > 0) varianti.add(v);
            }
            if (!varianti.isEmpty()) {
                mie.put(nome, varianti.toArray(new String[varianti.size()]));
            }
        }
        if (!mie.isEmpty()) Log.i(TAG, "risposte: " + mie.size() + " riscritte dal PC");
    }

    /**
     * Che cosa dire, per nome.
     *
     * Se il nome non c'e' - non dovrebbe succedere, ma succede quando si
     * aggiunge una chiamata e ci si dimentica la riga nel catalogo - si
     * restituisce il nome stesso invece di restare muti: a schermo si legge
     * "musica.pausa" e si capisce subito cosa manca, mentre una risposta vuota
     * sembra un guasto.
     */
    public String di(String nome) {
        String[] varianti = mie.get(nome);
        if (varianti == null) varianti = diSerie(nome);
        if (varianti == null || varianti.length == 0) return nome;
        if (varianti.length == 1) return varianti[0];
        Integer prima = ultima.get(nome);
        int i = sorte.nextInt(varianti.length);
        if (prima != null && i == prima.intValue()) i = (i + 1) % varianti.length;
        ultima.put(nome, Integer.valueOf(i));
        return varianti[i];
    }

    private static boolean ceNelCatalogo(String nome) {
        for (String[] r : BASE) if (r[0].equals(nome)) return true;
        return false;
    }

    private static String[] diSerie(String nome) {
        for (String[] r : BASE) {
            if (!r[0].equals(nome)) continue;
            String[] v = new String[r.length - 2];
            System.arraycopy(r, 2, v, 0, v.length);
            return v;
        }
        return null;
    }

    /**
     * Il catalogo per il PC: nome, gruppo, quelle di serie e quelle riscritte.
     *
     * Va nella vetrina insieme al resto, cosi' la finestra sul PC puo' mostrare
     * l'elenco vero di quello che Casa sa dire senza tenerne una copia sua che
     * invecchia. E' la stessa ragione per cui le voci del sintetizzatore si
     * chiedono al tablet invece di indovinarle.
     */
    public JSONArray catalogo() {
        JSONArray fuori = new JSONArray();
        try {
            for (String[] r : BASE) {
                JSONObject o = new JSONObject();
                o.put("nome", r[0]);
                o.put("gruppo", r[1]);
                JSONArray serie = new JSONArray();
                for (int i = 2; i < r.length; i++) serie.put(r[i]);
                o.put("serie", serie);
                String[] riscritte = mie.get(r[0]);
                if (riscritte != null) {
                    JSONArray a = new JSONArray();
                    for (String v : riscritte) a.put(v);
                    o.put("tue", a);
                }
                fuori.put(o);
            }
        } catch (Exception e) {
            Log.w(TAG, "risposte: non ho potuto comporre il catalogo", e);
        }
        return fuori;
    }
}
