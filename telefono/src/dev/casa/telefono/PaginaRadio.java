package dev.casa.telefono;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * La radio del tablet, dal telefono. Suona sempre il tablet: qui ci sono solo
 * i comandi.
 *
 * In cima il lettore - il logo, il nome, lo stato, i tre comandi e il volume -
 * e sotto le stazioni in una griglia di loghi, perche' un marchio si
 * riconosce prima di leggerlo.
 */
final class PaginaRadio extends Pagina {

    private FrameLayout riquadro;
    private ImageView logo;
    private TextView sigla, nome, statoRadio, volume;
    private FrameLayout precedente, principale, successiva;
    private Stile.Griglia griglia;
    private View vuoto;

    private final HashMap<String, View[]> tessere = new HashMap<String, View[]>();
    private String firma = "", corrente;
    private boolean accesa, apertura;
    private JSONObject stazioneCorrente;
    private int conta;

    PaginaRadio(Principale app) { super(app); }

    @Override String titolo() { return "Radio"; }

    @Override View vista() {
        LinearLayout col = s.verticale();

        // ---- il lettore
        LinearLayout lettore = s.verticale();
        lettore.setGravity(Gravity.CENTER_HORIZONTAL);
        lettore.setPadding(s.dp(Stile.S5), s.dp(Stile.S5), s.dp(Stile.S5), s.dp(Stile.S4));
        lettore.setBackground(s.pannello(Stile.R_PANNELLO + 4));

        riquadro = new FrameLayout(s.c);
        riquadro.setBackground(s.incavo(Stile.R_PANNELLO));
        riquadro.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        riquadro.setClipToOutline(true);
        riquadro.setElevation(s.dp(6));
        logo = new ImageView(s.c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setPadding(s.dp(Stile.S4), s.dp(Stile.S4), s.dp(Stile.S4), s.dp(Stile.S4));
        riquadro.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        sigla = s.testo("", 30, Stile.TESTO, s.medio);
        sigla.setGravity(Gravity.CENTER);
        riquadro.addView(sigla, new FrameLayout.LayoutParams(-1, -1));
        lettore.addView(riquadro, s.lato(128));

        nome = s.riga("Radio", Stile.T_TITOLO + 2, Stile.TESTO, s.medio);
        nome.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ln = new LinearLayout.LayoutParams(-1, -2);
        ln.topMargin = s.dp(Stile.S4);
        lettore.addView(nome, ln);
        statoRadio = s.riga("", Stile.T_PICCOLO, Stile.SECONDO, s.medio);
        statoRadio.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lst = new LinearLayout.LayoutParams(-1, -2);
        lst.topMargin = s.dp(Stile.S1);
        lettore.addView(statoRadio, lst);

        LinearLayout comandi = s.orizzontale();
        comandi.setGravity(Gravity.CENTER);
        precedente = s.tastoIcona(R.drawable.ic_indietro_brano, 52, 26, Stile.SECONDARIO, "stazione precedente");
        principale = s.tastoIcona(R.drawable.ic_play, 72, 34, Stile.PRIMARIO, "accendi");
        successiva = s.tastoIcona(R.drawable.ic_avanti, 52, 26, Stile.SECONDARIO, "stazione successiva");
        comandi.addView(precedente, s.lato(52));
        LinearLayout.LayoutParams lp = s.lato(72);
        lp.leftMargin = lp.rightMargin = s.dp(Stile.S6);
        comandi.addView(principale, lp);
        comandi.addView(successiva, s.lato(52));
        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(-1, -2);
        lc.topMargin = s.dp(Stile.S5);
        lettore.addView(comandi, lc);

        precedente.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { comanda("/radio/precedente", null); }
        });
        successiva.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { comanda("/radio/successiva", null); }
        });
        principale.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (accesa || apertura) comanda("/radio/spegni", null);
                else comanda("/radio/accendi", Tablet.json("chiave", corrente != null ? corrente : ""));
            }
        });

        lettore.addView(divisorio(), s.larga(s.dp(1), Stile.S5, Stile.S3));
        lettore.addView(filaVolume());
        col.addView(lettore, s.larga(-2, Stile.S2, 0));

        // ---- le stazioni
        col.addView(s.titoloSezione("Stazioni", null));
        griglia = s.griglia(96, 3, 6);
        col.addView(griglia);
        vuoto = s.vuoto(R.drawable.ic_radio, "Le stazioni arrivano appena il tablet risponde");
        col.addView(vuoto);

        mostraLettore();
        return s.rotolo(col);
    }

    private View divisorio() {
        View v = new View(s.c);
        v.setBackgroundColor(Stile.FILO);
        return v;
    }

    /** Il volume: meno, il numero, piu' - come sulla Home del tablet. */
    private View filaVolume() {
        LinearLayout f = s.orizzontale();
        f.addView(s.icona(R.drawable.ic_volume, Stile.SECONDO), s.lato(20));
        TextView etichetta = s.riga("Volume", Stile.T_CORPO, Stile.SECONDO, null);
        etichetta.setPadding(s.dp(Stile.S3), 0, 0, 0);
        f.addView(etichetta, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout meno = s.tastoIcona(R.drawable.ic_meno, 44, 22, Stile.SECONDARIO, "abbassa");
        volume = s.riga("—", Stile.T_NOME, Stile.TESTO, s.medio);
        volume.setGravity(Gravity.CENTER);
        volume.setFontFeatureSettings("tnum");
        FrameLayout piu = s.tastoIcona(R.drawable.ic_piu, 44, 22, Stile.SECONDARIO, "alza");
        meno.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { app.volume(-1, volume); }
        });
        piu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { app.volume(+1, volume); }
        });
        f.addView(meno, s.lato(44));
        f.addView(volume, new LinearLayout.LayoutParams(s.dp(64), -2));
        f.addView(piu, s.lato(44));
        return f;
    }

    private void comanda(String percorso, JSONObject corpo) {
        app.comanda(percorso, corpo != null ? corpo : new JSONObject(), null, new Runnable() {
            @Override public void run() {
                leggi();
                app.ui.postDelayed(new Runnable() { @Override public void run() { leggi(); } }, 1500);
            }
        });
    }

    @Override void entra() {
        conta = 0;
        leggi();
    }

    @Override void secondo() {
        if (++conta % 4 == 0) leggi();
    }

    private void leggi() {
        app.chiedi("GET", "/radio", null, 6000, false, new Principale.Esito() {
            @Override public void fatto(JSONObject j, String errore) {
                if (errore != null || j == null) return;
                mostra(j);
            }
        });
    }

    private void mostra(JSONObject j) {
        accesa = j.optBoolean("accesa", false);
        apertura = j.optBoolean("apertura", false);
        corrente = j.isNull("corrente") ? null : j.optString("corrente", null);
        int v = j.optInt("volume", -1);
        if (v >= 0) volume.setText(v + "%");

        JSONArray elenco = j.optJSONArray("stazioni");
        String nuova = String.valueOf(elenco);
        stazioneCorrente = null;
        for (int i = 0; elenco != null && i < elenco.length(); i++) {
            JSONObject x = elenco.optJSONObject(i);
            if (x != null && x.optString("chiave").equals(corrente)) stazioneCorrente = x;
        }
        if (!nuova.equals(firma)) {
            firma = nuova;
            griglia.removeAllViews();
            tessere.clear();
            for (int i = 0; elenco != null && i < elenco.length(); i++) {
                JSONObject x = elenco.optJSONObject(i);
                if (x != null) griglia.addView(tessera(x));
            }
        }
        boolean niente = griglia.getChildCount() == 0;
        griglia.setVisibility(niente ? View.GONE : View.VISIBLE);
        vuoto.setVisibility(niente ? View.VISIBLE : View.GONE);
        if (niente && j.optBoolean("pronta", false)) {
            ((TextView) ((LinearLayout) vuoto).getChildAt(1)).setText("Nessuna stazione in elenco");
        }
        for (java.util.Map.Entry<String, View[]> e : tessere.entrySet()) {
            boolean q = e.getKey().equals(corrente);
            e.getValue()[0].setVisibility(q ? View.VISIBLE : View.GONE);
            e.getValue()[1].setVisibility(q && accesa ? View.VISIBLE : View.GONE);
        }
        mostraLettore();
        String guasto = j.isNull("errore") ? null : j.optString("errore", null);
        if (guasto != null && !accesa && !apertura) {
            statoRadio.setText(guasto);
            statoRadio.setTextColor(Stile.ERRORE);
        }
    }

    private void mostraLettore() {
        if (stazioneCorrente != null) {
            nome.setText(stazioneCorrente.optString("nome"));
            riempi(riquadro, logo, sigla, stazioneCorrente, s.dp(128));
        } else {
            nome.setText("Radio");
            riquadro.setBackground(s.incavo(Stile.R_PANNELLO));
            app.immagini.carica(logo, null, 0);
            logo.setImageDrawable(s.disegno(R.drawable.ic_radio, Stile.TERZO));
            sigla.setText("");
        }
        statoRadio.setTextColor(accesa ? Stile.VERDE : apertura ? Stile.ACCENTO : Stile.SECONDO);
        statoRadio.setText(accesa ? "In onda sul tablet" : apertura ? "Si sta collegando…" : "Spenta");
        boolean attiva = accesa || apertura;
        Stile.cambiaIcona(principale, s.disegno(attiva ? R.drawable.ic_stop : R.drawable.ic_play, Stile.SU_ACCENTO));
        principale.setContentDescription(attiva ? "spegni" : "accendi");
    }

    /** Logo o sigla, sul fondo che il logo vuole: chiaro per i loghi scuri. */
    private void riempi(FrameLayout dove, ImageView im, TextView sg, JSONObject x, int lato) {
        boolean chiaro = x.optBoolean("chiaro", false);
        int tinta = PaginaCasa.colore(x.optString("colore", ""), Stile.ACCENTO);
        boolean conLogo = x.optBoolean("logo", false);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(dove == riquadro ? Stile.R_PANNELLO : Stile.R_COMANDO + 2));
        g.setColor(chiaro ? 0xFFF4F5F7 : conLogo ? 0xFF161B22 : Stile.conAlfa(tinta, 0x40));
        dove.setBackground(g);
        if (conLogo) {
            sg.setText("");
            app.immagini.carica(im, "logo:" + x.optString("chiave"), lato);
        } else {
            app.immagini.carica(im, null, 0);
            sg.setText(x.optString("sigla"));
            sg.setTextColor(chiaro ? 0xFF111111 : Stile.TESTO);
        }
    }

    private int dp(int v) { return s.dp(v); }

    private View tessera(final JSONObject x) {
        LinearLayout t = s.verticale();
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, 0, 0, s.dp(Stile.S1));

        Stile.Quadrato q = new Stile.Quadrato(s.c);
        FrameLayout fondo = new FrameLayout(s.c);
        fondo.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        fondo.setClipToOutline(true);
        ImageView im = new ImageView(s.c);
        im.setScaleType(ImageView.ScaleType.FIT_CENTER);
        im.setPadding(s.dp(Stile.S3), s.dp(Stile.S3), s.dp(Stile.S3), s.dp(Stile.S3));
        fondo.addView(im, new FrameLayout.LayoutParams(-1, -1));
        TextView sg = s.testo("", Stile.T_NOME + 2, Stile.TESTO, s.medio);
        sg.setGravity(Gravity.CENTER);
        fondo.addView(sg, new FrameLayout.LayoutParams(-1, -1));
        riempi(fondo, im, sg, x, s.dp(96));
        q.addView(fondo, new FrameLayout.LayoutParams(-1, -1));

        View anello = new View(s.c);
        GradientDrawable a = new GradientDrawable();
        a.setCornerRadius(s.dp(Stile.R_COMANDO + 2));
        a.setStroke(s.dp(2), Stile.ACCENTO);
        anello.setBackground(a);
        anello.setVisibility(View.GONE);
        q.addView(anello, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout inOnda = new FrameLayout(s.c);
        inOnda.setBackground(s.tondo(Stile.ACCENTO));
        inOnda.addView(s.icona(R.drawable.ic_onde, Stile.SU_ACCENTO), new FrameLayout.LayoutParams(s.dp(14), s.dp(14), Gravity.CENTER));
        inOnda.setVisibility(View.GONE);
        FrameLayout.LayoutParams lo = new FrameLayout.LayoutParams(s.dp(24), s.dp(24), Gravity.TOP | Gravity.END);
        lo.topMargin = lo.rightMargin = s.dp(6);
        q.addView(inOnda, lo);

        q.setForeground(s.tocco(null, Stile.R_COMANDO + 2));
        t.addView(q, new LinearLayout.LayoutParams(-1, -2));

        TextView n = s.riga(x.optString("nome"), Stile.T_PICCOLO, Stile.SECONDO, s.medio);
        n.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ln = new LinearLayout.LayoutParams(-1, -2);
        ln.topMargin = s.dp(Stile.S2);
        t.addView(n, ln);

        q.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                comanda("/radio/accendi", Tablet.json("chiave", x.optString("chiave")));
            }
        });
        tessere.put(x.optString("chiave"), new View[] { anello, inOnda });
        return t;
    }

    @Override void stato(JSONObject st) {
        JSONObject r = st.optJSONObject("radio");
        if (r == null) return;
        boolean ora = r.optBoolean("accesa", false);
        if (ora != accesa) {
            accesa = ora;
            mostraLettore();
        }
    }
}
