package dev.casa.telefono;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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

import java.util.Locale;

/**
 * La Home: chiedere a Casa, cosa suona, le luci, le routine, e un colpo
 * d'occhio sull'orologio. Si comincia a guardare dall'alto: prima la cosa
 * che si fa di piu' (dire qualcosa), poi quello che sta succedendo.
 */
final class PaginaCasa extends Pagina {

    private EditText frase;
    private FrameLayout tastoFrase;

    private LinearLayout carta;
    private ImageView cartaImmagine, cartaSegno;
    private TextView cartaTitolo, cartaSotto;
    private FrameLayout cartaTasto1, cartaTasto2;
    private LinearLayout scorciatoie;
    private int sorgente = -1;
    private boolean musicaSuona;

    private Stile.Griglia luci, routine;
    private View vuotoLuci, vuotoRoutine;
    private String firmaLuci = "", firmaRoutine = "";

    private LinearLayout rigaTimer, rigaSveglia;
    private TextView valoreTimer, valoreSveglia, nomeTimer, nienteOrologio;
    private long fineTimer;

    PaginaCasa(Principale app) { super(app); }

    @Override String titolo() { return "Casa"; }

    @Override View vista() {
        LinearLayout col = s.verticale();

        // ---- chiedi a Casa: un campo, e un tasto solo che fa da microfono
        // finche' il campo e' vuoto e da invio quando c'e' del testo.
        frase = s.campo("Chiedi ad Assistente Home…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        frase.setImeOptions(EditorInfo.IME_ACTION_SEND);
        LinearLayout capsula = s.capsula(0, frase);
        tastoFrase = s.tastoIcona(R.drawable.ic_microfono, 44, 22, Stile.PRIMARIO, "parla ad Assistente Home");
        tastoFrase.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (frase.getText().toString().trim().length() == 0) app.ascolta();
                else invia();
            }
        });
        capsula.addView(tastoFrase, s.lato(44));
        frase.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence t, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                boolean vuoto = e.toString().trim().length() == 0;
                Stile.cambiaIcona(tastoFrase, s.disegno(vuoto ? R.drawable.ic_microfono : R.drawable.ic_invia, Stile.SU_ACCENTO));
                tastoFrase.setContentDescription(vuoto ? "parla ad Assistente Home" : "invia");
            }
        });
        frase.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int azione, android.view.KeyEvent e) {
                invia();
                return true;
            }
        });
        col.addView(capsula, s.larga(-2, Stile.S2, 0));
        TextView nota = s.testo("Timer, radio, luci, liste: quello che Assistente Home capisce a voce. Risponde il tablet, ad alta voce.",
                Stile.T_NOTA, Stile.TERZO, null);
        nota.setPadding(s.dp(Stile.S5 - 4), s.dp(Stile.S2), s.dp(Stile.S4), 0);
        col.addView(nota);

        // ---- in riproduzione
        col.addView(s.titoloSezione("In riproduzione", null));
        col.addView(carta());

        // ---- luci
        col.addView(s.titoloSezione("Luci", null));
        luci = s.griglia(152, 2, 4);
        col.addView(luci);
        vuotoLuci = s.vuoto(R.drawable.ic_lampada, "Le luci arrivano appena il tablet risponde");
        col.addView(vuotoLuci);

        // ---- routine
        col.addView(s.titoloSezione("Routine", null));
        routine = s.griglia(152, 2, 4);
        col.addView(routine);
        vuotoRoutine = s.vuoto(0, "Le routine arrivano appena il tablet risponde");
        col.addView(vuotoRoutine);

        // ---- orologio
        TextView apri = s.riga("Apri", Stile.T_PICCOLO, Stile.SECONDO, s.medio);
        apri.setPadding(s.dp(Stile.S3), s.dp(Stile.S2), s.dp(Stile.S1), s.dp(Stile.S2));
        apri.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { app.vaiA(Principale.OROLOGIO); }
        });
        col.addView(s.titoloSezione("Orologio", apri));
        col.addView(orologio());

        return s.rotolo(col);
    }

    private void invia() {
        String t = frase.getText().toString().trim();
        if (t.length() == 0) return;
        frase.setText("");
        app.nascondiTastiera();
        app.comanda("/frase", Tablet.json("testo", t), "« " + t + " »");
    }

    // ---- in riproduzione ---------------------------------------------------------------

    private View carta() {
        LinearLayout blocco = s.verticale();

        carta = s.orizzontale();
        carta.setPadding(s.dp(Stile.S3), s.dp(Stile.S3), s.dp(Stile.S3), s.dp(Stile.S3));
        carta.setBackground(s.pannelloPremibile(Stile.R_PANNELLO));

        FrameLayout riquadro = new FrameLayout(s.c);
        cartaImmagine = s.immagine(Stile.R_IMMAGINE + 2, false);
        riquadro.addView(cartaImmagine, new FrameLayout.LayoutParams(-1, -1));
        cartaSegno = s.icona(R.drawable.ic_nota, Stile.TERZO);
        riquadro.addView(cartaSegno, new FrameLayout.LayoutParams(s.dp(26), s.dp(26), Gravity.CENTER));
        carta.addView(riquadro, s.lato(60));

        LinearLayout scritte = s.verticale();
        scritte.setPadding(s.dp(Stile.S3 + 2), 0, s.dp(Stile.S2), 0);
        cartaTitolo = s.riga("Niente in riproduzione", Stile.T_NOME, Stile.TESTO, s.medio);
        scritte.addView(cartaTitolo);
        cartaSotto = s.riga("Scegli una radio o una playlist", Stile.T_PICCOLO, Stile.SECONDO, null);
        LinearLayout.LayoutParams lsotto = new LinearLayout.LayoutParams(-1, -2);
        lsotto.topMargin = s.dp(2);
        scritte.addView(cartaSotto, lsotto);
        carta.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));

        cartaTasto1 = s.tastoIcona(R.drawable.ic_avanti, 44, 22, Stile.FANTASMA, "brano successivo");
        LinearLayout.LayoutParams l1 = s.lato(44);
        l1.rightMargin = s.dp(Stile.S1);
        carta.addView(cartaTasto1, l1);
        cartaTasto2 = s.tastoIcona(R.drawable.ic_play, 48, 26, Stile.PRIMARIO, "play");
        carta.addView(cartaTasto2, s.lato(48));

        carta.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                app.vaiA(sorgente == Principale.SPOTIFY ? Principale.SPOTIFY : Principale.RADIO);
            }
        });
        blocco.addView(carta);

        scorciatoie = new LinearLayout(s.c);
        LinearLayout radio = s.bottone("Radio", R.drawable.ic_radio, Stile.SECONDARIO);
        radio.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { app.vaiA(Principale.RADIO); }
        });
        scorciatoie.addView(radio, s.pesata(s.dp(Stile.TOCCO), Stile.S3));
        LinearLayout spotify = s.bottone("Spotify", R.drawable.ic_spotify, Stile.SECONDARIO);
        ((ImageView) spotify.getChildAt(0)).setImageDrawable(s.disegno(R.drawable.ic_spotify, Stile.SPOTIFY));
        spotify.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { app.vaiA(Principale.SPOTIFY); }
        });
        scorciatoie.addView(spotify, s.pesata(s.dp(Stile.TOCCO), 0));
        blocco.addView(scorciatoie, s.larga(-2, Stile.S3, 0));

        mostraNiente();
        return blocco;
    }

    private void mostraNiente() {
        sorgente = -1;
        app.immagini.carica(cartaImmagine, null, 0);
        cartaSegno.setVisibility(View.VISIBLE);
        cartaTitolo.setText("Niente in riproduzione");
        cartaSotto.setText("Scegli una radio o una playlist");
        cartaTasto1.setVisibility(View.GONE);
        cartaTasto2.setVisibility(View.GONE);
        scorciatoie.setVisibility(View.VISIBLE);
    }

    private void mostraRiproduzione(JSONObject st) {
        JSONObject radio = st.optJSONObject("radio");
        JSONObject musica = st.optJSONObject("musica");
        boolean radioAccesa = radio != null && radio.optBoolean("accesa", false);
        boolean ceUnBrano = musica != null && !musica.isNull("brano");

        if (radioAccesa) {
            sorgente = Principale.RADIO;
            String chiave = radio.isNull("chiave") ? null : radio.optString("chiave");
            app.immagini.carica(cartaImmagine, chiave != null ? "logo:" + chiave : null, s.dp(60));
            cartaSegno.setVisibility(chiave != null ? View.GONE : View.VISIBLE);
            cartaTitolo.setText(radio.optString("nome", "Radio"));
            cartaSotto.setText("Radio · in onda");
            cartaTasto1.setVisibility(View.GONE);
            cartaTasto2.setVisibility(View.VISIBLE);
            Stile.cambiaIcona(cartaTasto2, s.disegno(R.drawable.ic_stop, Stile.SU_ACCENTO));
            cartaTasto2.setBackground(s.tocco(s.tondo(Stile.ACCENTO), 24));
            cartaTasto2.setContentDescription("spegni la radio");
            cartaTasto2.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { app.comanda("/radio/spegni", new JSONObject(), "radio spenta"); }
            });
            scorciatoie.setVisibility(View.GONE);
        } else if (ceUnBrano) {
            sorgente = Principale.SPOTIFY;
            musicaSuona = musica.optBoolean("suona", false);
            String copertina = musica.isNull("copertina") ? null : musica.optString("copertina");
            app.immagini.carica(cartaImmagine, copertina, s.dp(60));
            cartaSegno.setVisibility(copertina != null ? View.GONE : View.VISIBLE);
            cartaTitolo.setText(musica.optString("brano"));
            cartaSotto.setText((musica.isNull("artista") ? "Spotify" : musica.optString("artista"))
                    + (musicaSuona ? "" : " · in pausa"));
            cartaTasto1.setVisibility(View.VISIBLE);
            cartaTasto1.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { app.comanda("/musica/successivo", new JSONObject(), null); }
            });
            cartaTasto2.setVisibility(View.VISIBLE);
            Stile.cambiaIcona(cartaTasto2, s.disegno(musicaSuona ? R.drawable.ic_pausa : R.drawable.ic_play, Stile.SU_SPOTIFY));
            cartaTasto2.setBackground(s.tocco(s.tondo(Stile.SPOTIFY), 24));
            cartaTasto2.setContentDescription(musicaSuona ? "pausa" : "riprendi");
            cartaTasto2.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    musicaSuona = !musicaSuona;
                    Stile.cambiaIcona(cartaTasto2, s.disegno(musicaSuona ? R.drawable.ic_pausa : R.drawable.ic_play, Stile.SU_SPOTIFY));
                    app.comanda("/musica/pausa", new JSONObject(), null);
                }
            });
            scorciatoie.setVisibility(View.GONE);
        } else {
            mostraNiente();
        }
    }

    // ---- luci e routine -----------------------------------------------------------------

    private void disegnaLuci(JSONArray elenco) {
        String firma = String.valueOf(elenco);
        if (firma.equals(firmaLuci)) return;
        firmaLuci = firma;
        luci.removeAllViews();
        for (int i = 0; elenco != null && i < elenco.length(); i++) {
            JSONObject l = elenco.optJSONObject(i);
            if (l != null) luci.addView(tesseraLuce(l));
        }
        boolean vuote = luci.getChildCount() == 0;
        luci.setVisibility(vuote ? View.GONE : View.VISIBLE);
        vuotoLuci.setVisibility(vuote ? View.VISIBLE : View.GONE);
        if (vuote) ((TextView) ((LinearLayout) vuotoLuci).getChildAt(1)).setText("Nessuna lampada configurata");
    }

    private View tesseraLuce(final JSONObject l) {
        final int tinta = colore(l.optString("colore", ""), Stile.ACCENTO);
        final boolean accesa = l.optBoolean("accesa", false);
        String guasto = l.isNull("guasto") ? null : l.optString("guasto", null);
        String dice = l.optBoolean("inCorso", false) ? "…"
                : guasto != null ? guasto
                : !l.optBoolean("letta", false) ? "non ancora letta"
                : accesa ? (l.optInt("luminosita", -1) > 0 ? "Accesa · " + l.optInt("luminosita") + "%" : "Accesa")
                : "Spenta";

        LinearLayout t = s.verticale();
        t.setPadding(s.dp(Stile.S4), s.dp(Stile.S4), s.dp(Stile.S4), s.dp(Stile.S4));
        t.setBackground(s.tocco(accesa
                ? new Stile.Vetro(Stile.conAlfa(tinta, 0x2E), Stile.conAlfa(tinta, 0xB0), Stile.conAlfa(tinta, 0x30), s.dp(Stile.R_PANNELLO), s.dp(1))
                : s.pannello(Stile.R_PANNELLO), Stile.R_PANNELLO));

        LinearLayout su = s.orizzontale();
        FrameLayout tondo = new FrameLayout(s.c);
        tondo.setBackground(s.tondo(accesa ? Stile.conAlfa(tinta, 0x40) : 0x14FFFFFF));
        tondo.addView(s.icona(R.drawable.ic_lampada, accesa ? tinta : Stile.TERZO),
                new FrameLayout.LayoutParams(s.dp(22), s.dp(22), Gravity.CENTER));
        su.addView(tondo, s.lato(40));
        su.addView(new View(s.c), new LinearLayout.LayoutParams(0, 1, 1f));
        su.addView(s.icona(R.drawable.ic_accensione, accesa ? tinta : Stile.TERZO), s.lato(20));
        t.addView(su);

        TextView nome = s.riga(l.optString("nome"), Stile.T_NOME, Stile.TESTO, s.medio);
        LinearLayout.LayoutParams ln = new LinearLayout.LayoutParams(-1, -2);
        ln.topMargin = s.dp(Stile.S4);
        t.addView(nome, ln);
        TextView come = s.riga(dice, Stile.T_PICCOLO, guasto != null ? Stile.ERRORE : accesa ? Stile.SECONDO : Stile.TERZO, null);
        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(-1, -2);
        lc.topMargin = s.dp(2);
        t.addView(come, lc);

        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                app.comanda("/luce", Tablet.json("id", l.optString("id"), "azione", "inverti"), null);
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { regolaLuce(l); return true; }
        });
        return t;
    }

    /** La luminosita': meno, il numero, piu', a passi del dieci per cento -
     *  come il volume sulla Home del tablet. */
    private void regolaLuce(final JSONObject l) {
        final int[] valore = { l.optInt("luminosita", -1) > 0 ? l.optInt("luminosita") : 100 };
        final boolean accesa = l.optBoolean("accesa", false);

        LinearLayout fila = s.orizzontale();
        fila.setGravity(Gravity.CENTER);
        fila.setPadding(0, s.dp(Stile.S5), 0, s.dp(Stile.S2));
        final TextView numero = s.testo(valore[0] + "%", 36, Stile.TESTO, s.leggero);
        numero.setGravity(Gravity.CENTER);
        FrameLayout meno = s.tastoIcona(R.drawable.ic_meno, 56, 26, Stile.SECONDARIO, "meno luce");
        FrameLayout piu = s.tastoIcona(R.drawable.ic_piu, 56, 26, Stile.SECONDARIO, "piu' luce");
        View.OnClickListener passo = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int arrotondato = Math.round(valore[0] / 10f) * 10;
                int dopo = Math.max(10, Math.min(100, arrotondato + (v.getTag() == Boolean.TRUE ? 10 : -10)));
                if (dopo == valore[0]) return;
                valore[0] = dopo;
                numero.setText(dopo + "%");
                app.comanda("/luce", Tablet.json("id", l.optString("id"), "azione", "luce", "valore", dopo), null);
            }
        };
        meno.setTag(Boolean.FALSE);
        piu.setTag(Boolean.TRUE);
        meno.setOnClickListener(passo);
        piu.setOnClickListener(passo);
        fila.addView(meno, s.lato(56));
        fila.addView(numero, new LinearLayout.LayoutParams(s.dp(120), -2));
        fila.addView(piu, s.lato(56));

        app.mostra(new AlertDialog.Builder(app)
                .setTitle(l.optString("nome"))
                .setView(fila)
                .setNegativeButton("Chiudi", null)
                .setPositiveButton(accesa ? "Spegni" : "Accendi", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        app.comanda("/luce", Tablet.json("id", l.optString("id"), "azione", accesa ? "off" : "on"), null);
                    }
                }));
    }

    private void disegnaRoutine(JSONArray elenco) {
        String firma = String.valueOf(elenco);
        if (firma.equals(firmaRoutine)) return;
        firmaRoutine = firma;
        routine.removeAllViews();
        for (int i = 0; elenco != null && i < elenco.length(); i++) {
            final JSONObject r = elenco.optJSONObject(i);
            if (r == null) continue;
            int tinta = colore(r.optString("colore", ""), Stile.ACCENTO);
            LinearLayout b = s.orizzontale();
            b.setPadding(s.dp(Stile.S4), 0, s.dp(Stile.S3), 0);
            b.setBackground(s.pannelloPremibile(Stile.R_COMANDO + 2));
            b.addView(s.punto(tinta, 10), s.lato(10));
            TextView n = s.riga(r.optString("nome"), Stile.T_CORPO, Stile.TESTO, s.medio);
            n.setPadding(s.dp(Stile.S3), 0, 0, 0);
            b.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    app.comanda("/routine", Tablet.json("nome", r.optString("nome")), r.optString("nome"));
                }
            });
            routine.addView(b, new FrameLayout.LayoutParams(-1, s.dp(52)));
        }
        boolean vuote = routine.getChildCount() == 0;
        routine.setVisibility(vuote ? View.GONE : View.VISIBLE);
        vuotoRoutine.setVisibility(vuote ? View.VISIBLE : View.GONE);
        if (vuote) ((TextView) ((LinearLayout) vuotoRoutine).getChildAt(0)).setText("Nessuna routine configurata");
    }

    // ---- orologio -------------------------------------------------------------------------

    private View orologio() {
        LinearLayout c = s.verticale();
        c.setPadding(s.dp(Stile.S4), s.dp(Stile.S1), s.dp(Stile.S4), s.dp(Stile.S1));
        c.setBackground(s.pannelloPremibile(Stile.R_PANNELLO));
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { app.vaiA(Principale.OROLOGIO); }
        });

        rigaTimer = s.orizzontale();
        rigaTimer.setMinimumHeight(s.dp(56));
        rigaTimer.addView(s.icona(R.drawable.ic_timer, Stile.ACCENTO), s.lato(22));
        nomeTimer = s.riga("Timer", Stile.T_CORPO, Stile.TESTO, null);
        nomeTimer.setPadding(s.dp(Stile.S3), 0, s.dp(Stile.S2), 0);
        rigaTimer.addView(nomeTimer, new LinearLayout.LayoutParams(0, -2, 1f));
        valoreTimer = s.riga("", Stile.T_TITOLO, Stile.TESTO, s.medio);
        valoreTimer.setFontFeatureSettings("tnum");
        rigaTimer.addView(valoreTimer);
        c.addView(rigaTimer);

        rigaSveglia = s.orizzontale();
        rigaSveglia.setMinimumHeight(s.dp(56));
        rigaSveglia.addView(s.icona(R.drawable.ic_sveglia, Stile.SECONDO), s.lato(22));
        TextView ns = s.riga("Prossima sveglia", Stile.T_CORPO, Stile.TESTO, null);
        ns.setPadding(s.dp(Stile.S3), 0, s.dp(Stile.S2), 0);
        rigaSveglia.addView(ns, new LinearLayout.LayoutParams(0, -2, 1f));
        valoreSveglia = s.riga("", Stile.T_CORPO, Stile.SECONDO, s.medio);
        rigaSveglia.addView(valoreSveglia);
        c.addView(rigaSveglia);

        nienteOrologio = s.riga("Nessun timer, nessuna sveglia in programma", Stile.T_PICCOLO, Stile.TERZO, null);
        nienteOrologio.setMinimumHeight(s.dp(56));
        nienteOrologio.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(nienteOrologio);

        rigaTimer.setVisibility(View.GONE);
        rigaSveglia.setVisibility(View.GONE);
        return c;
    }

    private void disegnaOrologio(JSONObject st) {
        JSONArray t = st.optJSONArray("timer");
        long primo = Long.MAX_VALUE;
        int quanti = t != null ? t.length() : 0;
        for (int i = 0; i < quanti; i++) {
            JSONObject x = t.optJSONObject(i);
            if (x != null) primo = Math.min(primo, x.optLong("restano"));
        }
        if (quanti > 0) {
            fineTimer = SystemClock.uptimeMillis() + primo * 1000L;
            nomeTimer.setText(quanti == 1 ? "Timer" : quanti + " timer, il primo");
            rigaTimer.setVisibility(View.VISIBLE);
            secondo();
        } else {
            rigaTimer.setVisibility(View.GONE);
        }
        String prossima = st.isNull("prossimaSveglia") ? null : st.optString("prossimaSveglia", null);
        rigaSveglia.setVisibility(prossima != null ? View.VISIBLE : View.GONE);
        if (prossima != null) valoreSveglia.setText(prossima);
        nienteOrologio.setVisibility(quanti == 0 && prossima == null ? View.VISIBLE : View.GONE);
    }

    @Override void secondo() {
        if (rigaTimer.getVisibility() != View.VISIBLE) return;
        long restano = Math.max(0L, (fineTimer - SystemClock.uptimeMillis() + 999) / 1000);
        valoreTimer.setText(PaginaOrologio.mmss(restano));
    }

    @Override void stato(JSONObject st) {
        disegnaLuci(st.optJSONArray("luci"));
        disegnaRoutine(st.optJSONArray("routine"));
        mostraRiproduzione(st);
        disegnaOrologio(st);
    }

    static int colore(String scritto, int riserva) {
        try {
            return Color.parseColor(scritto);
        } catch (Exception storto) {
            return riserva;
        }
    }

    static String minuscolo(String t) {
        return t == null ? "" : t.toLowerCase(Locale.ITALIAN);
    }
}
