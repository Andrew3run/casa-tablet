package dev.casa.telefono;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Le liste del tablet: la To-Do List ("cose") e la spesa.
 *
 * AGGIORNAMENTO OTTIMISTA. Al tocco la riga cambia subito, poi parte la
 * richiesta: con un giro in rete per spunta, in corsia al supermercato ogni
 * riga premuta sarebbe mezzo secondo di dubbio. Le modifiche in volo stanno in
 * tre mappe e si sovrappongono a quello che dice /stato finche' /stato non le
 * racconta da solo: una lettura partita PRIMA del tocco farebbe altrimenti
 * tornare indietro la riga per cinque secondi. Se la richiesta fallisce si
 * tolgono dalle mappe, e la riga torna com'era.
 *
 * Il campo di testo non sta dentro l'elenco che si ricostruisce: il giro dei
 * cinque secondi rifa' le righe, non quello che si sta scrivendo.
 */
final class PaginaListe extends Pagina {

    private static final String[] CHIAVI = { "cose", "spesa" };
    private static final String[] NOMI = { "Da fare", "Spesa" };
    private static final long VITA_AGGIUNTA_MS = 15000;

    private JSONObject liste;
    private boolean statoArrivato;
    private String scelta = "cose";
    private Stile.Segmentato selettore;
    private LinearLayout elenco;
    private View pulisci;
    private EditText campo;
    private String firma = "";

    /** id -> come deve risultare (fatta o no). */
    private final HashMap<Integer, Boolean> inversioni = new HashMap<Integer, Boolean>();
    /** id tolti, in attesa che il tablet se ne dimentichi. */
    private final HashSet<Integer> tolti = new HashSet<Integer>();
    /** Le righe aggiunte da qui e non ancora viste in /stato. */
    private final ArrayList<Aggiunta> aggiunte = new ArrayList<Aggiunta>();

    /**
     * Una riga appena scritta, mostrata smorta finche' il tablet non la
     * racconta da solo. <b>Si riconosce dall'id, non dal testo</b>: il tablet
     * il testo lo sistema - « latte. » diventa « Latte ». Dopo quindici
     * secondi una riga provvisoria se ne va comunque: una riga fantasma e'
     * peggio di un attimo di vuoto.
     */
    private static final class Aggiunta {
        final String lista, testo;
        final long nata = SystemClock.uptimeMillis();
        int id = -1;
        Aggiunta(String lista, String testo) { this.lista = lista; this.testo = testo; }
    }

    private static final class Riga {
        final int id; final String testo; final boolean fatta;
        Riga(int id, String testo, boolean fatta) { this.id = id; this.testo = testo; this.fatta = fatta; }
    }

    PaginaListe(Principale app) { super(app); }

    @Override String titolo() { return "Liste"; }

    @Override View vista() {
        LinearLayout col = s.verticale();
        scelta = app.getSharedPreferences("schermata", android.content.Context.MODE_PRIVATE).getString("lista", "cose");

        selettore = s.segmentato(NOMI, new Stile.Segmentato.Scelta() {
            @Override public void scelto(int quale) {
                scelta = CHIAVI[quale];
                app.getSharedPreferences("schermata", android.content.Context.MODE_PRIVATE).edit().putString("lista", scelta).apply();
                campo.setHint(suggerimento());
                disegna();
            }
        });
        selettore.setScelto("spesa".equals(scelta) ? 1 : 0);
        col.addView(selettore, s.larga(-2, Stile.S1, 0));

        campo = s.campo(suggerimento(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        campo.setImeOptions(EditorInfo.IME_ACTION_DONE);
        // Invio aggiunge e il campo resta aperto: nella spesa si scrivono
        // cinque cose di fila.
        campo.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int azione, android.view.KeyEvent e) {
                aggiungi();
                return true;
            }
        });
        LinearLayout capsula = s.capsula(0, campo);
        FrameLayout piu = s.tastoIcona(R.drawable.ic_piu, 44, 24, Stile.PRIMARIO, "aggiungi");
        piu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { aggiungi(); }
        });
        capsula.addView(piu, s.lato(44));
        col.addView(capsula, s.larga(-2, Stile.S4, Stile.S4));

        elenco = s.verticale();
        col.addView(elenco);

        pulisci = s.bottone("Togli le spuntate", R.drawable.ic_cestino, Stile.FANTASMA);
        pulisci.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pulisciSpuntate(); }
        });
        col.addView(pulisci, s.larga(s.dp(Stile.TOCCO), Stile.S2, 0));
        disegna();
        return s.rotolo(col);
    }

    private String suggerimento() {
        return "spesa".equals(scelta) ? "Aggiungi alla spesa…" : "Aggiungi una cosa da fare…";
    }

    /** Il testo come lo scrive il tablet, per confrontarlo. */
    private static String comeSulTablet(String testo) {
        String t = testo == null ? "" : testo.trim();
        while (t.length() > 0 && ".,;:!".indexOf(t.charAt(t.length() - 1)) >= 0) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t.toLowerCase(java.util.Locale.ITALIAN);
    }

    private ArrayList<Riga> righe(String lista) {
        ArrayList<Riga> daFare = new ArrayList<Riga>(), fatte = new ArrayList<Riga>();
        HashSet<String> testi = new HashSet<String>();
        HashSet<Integer> ids = new HashSet<Integer>();
        JSONArray a = liste != null ? liste.optJSONArray(lista) : null;
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            int id = o.optInt("id");
            String testo = o.optString("testo", "");
            testi.add(comeSulTablet(testo));
            ids.add(id);
            if (tolti.contains(id)) continue;
            boolean fatta = o.optBoolean("fatta", false);
            Boolean voluta = inversioni.get(id);
            if (voluta != null) fatta = voluta;
            (fatta ? fatte : daFare).add(new Riga(id, testo, fatta));
        }
        for (Aggiunta x : aggiunte) {
            if (!x.lista.equals(lista)) continue;
            if (ids.contains(x.id) || testi.contains(comeSulTablet(x.testo))) continue;
            daFare.add(new Riga(-1, x.testo, false));
        }
        daFare.addAll(fatte);
        return daFare;
    }

    private void assorbi() {
        if (liste == null) return;
        HashMap<Integer, Boolean> sulTablet = new HashMap<Integer, Boolean>();
        HashSet<String> testi = new HashSet<String>();
        for (String chiave : CHIAVI) {
            JSONArray a = liste.optJSONArray(chiave);
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                sulTablet.put(o.optInt("id"), o.optBoolean("fatta", false));
                testi.add(chiave + "|" + comeSulTablet(o.optString("testo", "")));
            }
        }
        for (Iterator<Map.Entry<Integer, Boolean>> it = inversioni.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Boolean> e = it.next();
            Boolean ora = sulTablet.get(e.getKey());
            if (ora == null || ora.equals(e.getValue())) it.remove();
        }
        for (Iterator<Integer> it = tolti.iterator(); it.hasNext(); ) {
            if (!sulTablet.containsKey(it.next())) it.remove();
        }
        long adesso = SystemClock.uptimeMillis();
        for (Iterator<Aggiunta> it = aggiunte.iterator(); it.hasNext(); ) {
            Aggiunta x = it.next();
            if ((x.id >= 0 && sulTablet.containsKey(x.id))
                    || testi.contains(x.lista + "|" + comeSulTablet(x.testo))
                    || adesso - x.nata > VITA_AGGIUNTA_MS) {
                it.remove();
            }
        }
    }

    private void disegna() {
        assorbi();
        for (int i = 0; i < 2; i++) {
            int quante = 0;
            for (Riga r : righe(CHIAVI[i])) if (!r.fatta) quante++;
            selettore.setTesto(i, quante > 0 ? NOMI[i] + "  " + quante : NOMI[i]);
        }

        ArrayList<Riga> r = righe(scelta);
        StringBuilder f = new StringBuilder(scelta).append(liste == null).append(statoArrivato);
        int spuntate = 0;
        for (Riga x : r) {
            f.append('|').append(x.id).append(x.fatta).append(x.testo);
            if (x.fatta) spuntate++;
        }
        pulisci.setVisibility(spuntate > 0 ? View.VISIBLE : View.GONE);
        if (f.toString().equals(firma)) return;
        firma = f.toString();

        elenco.removeAllViews();
        if (r.isEmpty()) {
            elenco.addView(s.vuoto(R.drawable.ic_liste, !statoArrivato ? "Le liste arrivano appena il tablet risponde"
                    : liste == null ? "Il tablet non manda ancora le liste: va aggiornato"
                    : "spesa".equals(scelta) ? "La lista della spesa è vuota" : "Niente da fare"));
            return;
        }
        LinearLayout carta = s.verticale();
        carta.setBackground(s.pannello(Stile.R_PANNELLO));
        carta.setPadding(0, s.dp(Stile.S1), 0, s.dp(Stile.S1));
        for (int i = 0; i < r.size(); i++) {
            final Riga x = r.get(i);
            if (i > 0) {
                View filo = new View(s.c);
                filo.setBackgroundColor(0x0FFFFFFF);
                LinearLayout.LayoutParams lf = new LinearLayout.LayoutParams(-1, 1);
                lf.leftMargin = s.dp(56);
                carta.addView(filo, lf);
            }
            LinearLayout fila = s.orizzontale();
            fila.setPadding(s.dp(Stile.S4), s.dp(Stile.S3), s.dp(Stile.S4), s.dp(Stile.S3));
            fila.setMinimumHeight(s.dp(52));
            fila.setBackground(s.tocco(null, 0));

            FrameLayout cerchio = new FrameLayout(s.c);
            GradientDrawable tondo = new GradientDrawable();
            tondo.setShape(GradientDrawable.OVAL);
            tondo.setColor(x.fatta ? Stile.ACCENTO : 0);
            tondo.setStroke(s.dp(2), x.fatta ? Stile.ACCENTO : Stile.TERZO);
            cerchio.setBackground(tondo);
            if (x.fatta) {
                ImageView segno = s.icona(R.drawable.ic_spunta, Stile.SU_ACCENTO);
                cerchio.addView(segno, new FrameLayout.LayoutParams(s.dp(16), s.dp(16), Gravity.CENTER));
            }
            fila.addView(cerchio, s.lato(24));

            TextView t = s.testo(x.testo, Stile.T_NOME, x.fatta || x.id < 0 ? Stile.TERZO : Stile.TESTO, null);
            if (x.fatta) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            t.setPadding(s.dp(Stile.S4), 0, 0, 0);
            fila.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

            if (x.id >= 0) {
                fila.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { inverti(x); }
                });
                fila.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { chiediDiTogliere(x); return true; }
                });
            }
            carta.addView(fila);
        }
        elenco.addView(carta);
        TextView aiuto = s.testo("Tocca per spuntare, tieni premuto per togliere.", Stile.T_NOTA, Stile.TERZO, null);
        aiuto.setPadding(s.dp(Stile.S1), s.dp(Stile.S2), 0, 0);
        elenco.addView(aiuto);
    }

    private void aggiungi() {
        final String testo = campo.getText().toString().trim();
        if (testo.length() == 0) return;
        campo.setText("");
        final Aggiunta voce = new Aggiunta(scelta, testo);
        aggiunte.add(voce);
        disegna();
        comanda("/nota/aggiungi", Tablet.json("lista", scelta, "testo", testo), new Runnable() {
            @Override public void run() { aggiunte.remove(voce); }
        }, voce);
    }

    private void inverti(final Riga x) {
        inversioni.put(x.id, !x.fatta);
        disegna();
        comanda("/nota/inverti", Tablet.json("id", x.id), new Runnable() {
            @Override public void run() { inversioni.remove(x.id); }
        }, null);
    }

    private void chiediDiTogliere(final Riga x) {
        app.mostra(new AlertDialog.Builder(app)
                .setMessage("Togliere « " + x.testo + " »?")
                .setNegativeButton("No", null)
                .setPositiveButton("Togli", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        tolti.add(x.id);
                        disegna();
                        comanda("/nota/togli", Tablet.json("id", x.id), new Runnable() {
                            @Override public void run() { tolti.remove(x.id); }
                        }, null);
                    }
                }));
    }

    private void pulisciSpuntate() {
        final ArrayList<Integer> via = new ArrayList<Integer>();
        for (Riga r : righe(scelta)) if (r.fatta && r.id >= 0) via.add(r.id);
        if (via.isEmpty()) return;
        tolti.addAll(via);
        disegna();
        comanda("/nota/pulisci", Tablet.json("lista", scelta), new Runnable() {
            @Override public void run() { tolti.removeAll(via); }
        }, null);
    }

    /** Un comando con la marcia indietro: se il tablet dice di no, o non
     *  risponde, {@code annulla} toglie la modifica ottimista. */
    private void comanda(String percorso, JSONObject corpo, final Runnable annulla, final Aggiunta voce) {
        app.chiedi("POST", percorso, corpo, 8000, false, new Principale.Esito() {
            @Override public void fatto(JSONObject j, String errore) {
                if (errore != null) {
                    annulla.run();
                    app.di(errore, true);
                    firma = "";
                    disegna();
                    return;
                }
                if (voce != null && j != null) voce.id = j.optInt("id", -1);
                app.aggiornaStato();
            }
        });
    }

    @Override void stato(JSONObject st) {
        statoArrivato = true;
        liste = st.optJSONObject("liste");
        disegna();
    }
}
