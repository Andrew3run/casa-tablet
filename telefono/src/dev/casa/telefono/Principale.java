package dev.casa.telefono;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * La cornice dell'app: testata, contenuto, barra delle schede, e la rete.
 *
 * <h3>Niente passa sotto niente</h3>
 *
 * La testata sta sopra il contenuto e la barra sta sotto, <b>una dopo
 * l'altra in colonna</b>: il contenuto scorre nel suo riquadro e li' si
 * ferma. La versione di prima faceva galleggiare la barra sopra il contenuto,
 * e le righe le passavano sotto: con un vetro vero sarebbe un effetto, con un
 * vetro disegnato e' solo testo sovrapposto ad altro testo.
 *
 * <h3>Cinque schede, e il tablet dietro un tasto</h3>
 *
 * Casa, Radio, Spotify, Orologio, Liste: le cose che si fanno. L'abbinamento
 * col tablet si fa una volta, e sta nella rotella in alto a destra invece di
 * occupare una scheda per sempre. Cinque e' anche il massimo che una barra
 * in basso regge prima che i nomi diventino illeggibili.
 *
 * <h3>Un microfono solo</h3>
 *
 * Sta nella Home, dentro « Chiedi a Casa ». Tutto quello che il tablet capisce
 * a voce - timer, radio, liste, luci - passa da li': tre microfoni in tre
 * schermate erano tre modi di fare la stessa cosa.
 *
 * <h3>La rete</h3>
 *
 * Due code. Una con un filo solo per lo stato e i comandi, cosi' « accendi »
 * e « leggi lo stato » partono nell'ordine in cui li si fa. Una con due fili
 * per quello che passa da Internet - ricerca, brani di una playlist - che
 * puo' metterci dieci secondi e non deve tenere in coda un tocco su una luce.
 */
public final class Principale extends Activity {

    static final int CASA = 0, RADIO = 1, SPOTIFY = 2, OROLOGIO = 3, LISTE = 4, IMPOSTAZIONI = 5;
    private static final String[] NOMI = { "Casa", "Radio", "Spotify", "Orologio", "Liste" };
    private static final int[] ICONE = { R.drawable.ic_casa, R.drawable.ic_radio, R.drawable.ic_spotify,
            R.drawable.ic_orologio, R.drawable.ic_liste };
    private static final long OGNI_MS = 5000;
    private static final int ALTEZZA_BARRA = 68;
    private static final int VOCE = 1;

    final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService rete = Executors.newSingleThreadExecutor();
    private final ExecutorService lenta = Executors.newFixedThreadPool(2);

    Tablet tablet;
    Stile s;
    Immagini immagini;
    /** L'ultimo /stato buono, o null. */
    JSONObject ultimo;
    /** Perche' l'ultimo /stato non e' arrivato, o null. */
    String guastoRete;

    private Pagina[] pagine;
    private View[] viste;
    private int corrente = -1, primaDelleImpostazioni = CASA;
    private boolean chiedendo, davanti;

    private FrameLayout radice, contenuto;
    private Stile.Fondo fondo;
    private LinearLayout colonna, barra;
    private Stile.Limite cornice;
    private TextView titolo, rigaStato;
    private View puntoStato;
    private FrameLayout tastoIndietro, tastoImpostazioni;
    private final View[] pastiglie = new View[5];
    private final ImageView[] iconeBarra = new ImageView[5];
    private final TextView[] nomiBarra = new TextView[5];
    private LinearLayout avviso;
    private TextView testoAvviso;
    private View puntoAvviso;

    private int insSu, insGiu, insSx, insDx, insTastiera;

    private final Runnable giro = new Runnable() {
        @Override public void run() {
            if (!davanti) return;
            aggiornaStato();
            ui.postDelayed(this, OGNI_MS);
        }
    };

    private final Runnable battito = new Runnable() {
        @Override public void run() {
            if (!davanti) return;
            if (corrente >= 0) pagine[corrente].secondo();
            ui.postDelayed(this, 1000);
        }
    };

    private final Runnable aggiornaDopo = new Runnable() {
        @Override public void run() { aggiornaStato(); }
    };

    private final Runnable nascondiAvviso = new Runnable() {
        @Override public void run() {
            avviso.animate().alpha(0f).translationY(s.dp(8)).setDuration(180).withEndAction(new Runnable() {
                @Override public void run() { avviso.setVisibility(View.GONE); }
            });
        }
    };

    @Override protected void onCreate(Bundle stato) {
        super.onCreate(stato);
        tablet = new Tablet(this);
        s = new Stile(this);
        immagini = new Immagini(this, tablet);
        daBordoABordo();
        pagine = new Pagina[] {
                new PaginaCasa(this), new PaginaRadio(this), new PaginaSpotify(this),
                new PaginaOrologio(this), new PaginaListe(this), new PaginaTablet(this) };
        setContentView(schermata());
        vaiA(tablet.abbinato() ? CASA : IMPOSTAZIONI);
        if (tablet.segueLaSveglia()) Specchio.programma(this);
    }

    @Override protected void onResume() {
        super.onResume();
        davanti = true;
        fondo.avvia();
        ui.removeCallbacks(giro);
        ui.post(giro);
        ui.removeCallbacks(battito);
        ui.postDelayed(battito, 1000);
        if (corrente >= 0) pagine[corrente].entra();
    }

    @Override protected void onPause() {
        super.onPause();
        davanti = false;
        fondo.ferma();
        ui.removeCallbacks(giro);
        ui.removeCallbacks(battito);
        if (corrente >= 0) pagine[corrente].esce();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        rete.shutdownNow();
        lenta.shutdownNow();
    }

    @Override public void onBackPressed() {
        if (corrente >= 0 && pagine[corrente].indietro()) return;
        if (corrente == IMPOSTAZIONI) { vaiA(primaDelleImpostazioni); return; }
        if (corrente != CASA) { vaiA(CASA); return; }
        super.onBackPressed();
    }

    // ---- da bordo a bordo ----------------------------------------------------------

    private void daBordoABordo() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private static final class Api30 {
        static int[] leggi(WindowInsets ins) {
            android.graphics.Insets b = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets k = ins.getInsets(WindowInsets.Type.ime());
            return new int[] { b.top, b.bottom, b.left, b.right, k.bottom };
        }
    }

    @SuppressWarnings("deprecation")
    private void leggiInsets(WindowInsets ins) {
        if (Build.VERSION.SDK_INT >= 30) {
            int[] v = Api30.leggi(ins);
            insSu = v[0]; insGiu = v[1]; insSx = v[2]; insDx = v[3]; insTastiera = v[4];
        } else {
            insSu = ins.getSystemWindowInsetTop();
            insSx = ins.getSystemWindowInsetLeft();
            insDx = ins.getSystemWindowInsetRight();
            int giu = ins.getSystemWindowInsetBottom();
            if (giu > s.dp(120)) { insTastiera = giu; insGiu = 0; } else { insTastiera = 0; insGiu = giu; }
        }
    }

    /**
     * Testata sotto l'ora, barra sopra i tasti del telefono, e fra le due il
     * contenuto. Con la tastiera aperta la barra se ne va e la colonna si
     * accorcia fino alla tastiera: il rotolo cambia altezza e porta da solo il
     * campo col cursore in vista.
     */
    private void applicaInsets() {
        boolean tastiera = insTastiera > insGiu + s.dp(40);
        cornice.setPadding(cornice.getPaddingLeft(), insSu + s.dp(Stile.S3), cornice.getPaddingRight(), s.dp(Stile.S3));
        contenuto.setPadding(insSx, 0, insDx, 0);
        barra.setPadding(insSx, 0, insDx, insGiu);
        barra.setVisibility(tastiera ? View.GONE : View.VISIBLE);
        colonna.setPadding(0, 0, 0, tastiera ? insTastiera : 0);
        FrameLayout.LayoutParams la = (FrameLayout.LayoutParams) avviso.getLayoutParams();
        la.bottomMargin = (tastiera ? insTastiera : insGiu + s.dp(ALTEZZA_BARRA)) + s.dp(Stile.S3);
        avviso.setLayoutParams(la);
    }

    // ---- impalcatura ---------------------------------------------------------------

    private View schermata() {
        radice = new FrameLayout(this);
        fondo = new Stile.Fondo();
        radice.setBackground(fondo);

        colonna = s.verticale();
        colonna.addView(testata(), new LinearLayout.LayoutParams(-1, -2));

        contenuto = new FrameLayout(this);
        viste = new View[pagine.length];
        for (int i = 0; i < pagine.length; i++) {
            viste[i] = pagine[i].vista();
            viste[i].setVisibility(View.GONE);
            contenuto.addView(viste[i], new FrameLayout.LayoutParams(-1, -1));
        }
        colonna.addView(contenuto, new LinearLayout.LayoutParams(-1, 0, 1f));

        barra = barraSchede();
        colonna.addView(barra, new LinearLayout.LayoutParams(-1, -2));
        radice.addView(colonna, new FrameLayout.LayoutParams(-1, -1));
        radice.addView(avviso(), new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        radice.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                leggiInsets(ins);
                applicaInsets();
                return ins;
            }
        });
        return radice;
    }

    private View testata() {
        cornice = new Stile.Limite(this, s.dp(Stile.LARGHEZZA), s.dp(Stile.S4));
        LinearLayout t = s.orizzontale();

        tastoIndietro = s.tastoIcona(R.drawable.ic_indietro, 44, 24, Stile.FANTASMA, "indietro");
        tastoIndietro.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onBackPressed(); }
        });
        LinearLayout.LayoutParams li = s.lato(44);
        li.leftMargin = -s.dp(Stile.S2);
        li.rightMargin = s.dp(Stile.S1);
        t.addView(tastoIndietro, li);

        LinearLayout scritte = s.verticale();
        titolo = s.riga("Casa", Stile.T_GRANDE, Stile.TESTO, s.medio);
        titolo.setIncludeFontPadding(false);
        titolo.setLetterSpacing(-0.01f);
        scritte.addView(titolo);
        LinearLayout stato = s.orizzontale();
        puntoStato = s.punto(Stile.TERZO, 8);
        stato.addView(puntoStato, s.lato(8));
        rigaStato = s.riga("", Stile.T_PICCOLO, Stile.SECONDO, null);
        rigaStato.setPadding(s.dp(Stile.S2), 0, 0, 0);
        stato.addView(rigaStato, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(-1, -2);
        ls.topMargin = s.dp(6);
        scritte.addView(stato, ls);
        t.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));

        tastoImpostazioni = s.tastoIcona(R.drawable.ic_impostazioni, 44, 22, Stile.SECONDARIO, "tablet e abbinamento");
        tastoImpostazioni.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { vaiA(IMPOSTAZIONI); }
        });
        LinearLayout.LayoutParams lt = s.lato(44);
        lt.leftMargin = s.dp(Stile.S3);
        t.addView(tastoImpostazioni, lt);

        cornice.addView(t, new FrameLayout.LayoutParams(-1, -2));
        return cornice;
    }

    /**
     * La barra delle schede. Ogni tasto e' una colonna centrata: la pastiglia
     * con l'icona e il nome sotto, larghi quanto il tasto, quindi il centro
     * dell'icona e quello della scritta sono lo stesso punto per costruzione.
     */
    private LinearLayout barraSchede() {
        LinearLayout b = s.verticale();
        b.setBackground(new Stile.Fascia(Stile.BARRA, Stile.FILO));
        if (Build.VERSION.SDK_INT >= 31) b.setElevation(0);
        Stile.Limite l = new Stile.Limite(this, s.dp(520), 0);
        LinearLayout fila = new LinearLayout(this);
        for (int i = 0; i < 5; i++) {
            final int quale = i;
            LinearLayout t = s.verticale();
            t.setGravity(Gravity.CENTER);
            t.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x14FFFFFF), null, null));

            FrameLayout pastiglia = new FrameLayout(this);
            View fondoPastiglia = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(s.dp(16));
            g.setColor(i == SPOTIFY ? 0x331ED760 : 0x33F2D06B);
            fondoPastiglia.setBackground(g);
            pastiglia.addView(fondoPastiglia, new FrameLayout.LayoutParams(-1, -1));
            ImageView iv = new ImageView(this);
            pastiglia.addView(iv, new FrameLayout.LayoutParams(s.dp(24), s.dp(24), Gravity.CENTER));
            t.addView(pastiglia, new LinearLayout.LayoutParams(s.dp(60), s.dp(32)));

            TextView n = s.riga(NOMI[i], 11.5f, Stile.TERZO, s.medio);
            n.setGravity(Gravity.CENTER);
            n.setIncludeFontPadding(false);
            LinearLayout.LayoutParams ln = new LinearLayout.LayoutParams(-1, -2);
            ln.topMargin = s.dp(Stile.S1);
            t.addView(n, ln);

            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { vaiA(quale); }
            });
            pastiglie[i] = fondoPastiglia;
            iconeBarra[i] = iv;
            nomiBarra[i] = n;
            fila.addView(t, new LinearLayout.LayoutParams(0, s.dp(ALTEZZA_BARRA), 1f));
        }
        l.addView(fila, new FrameLayout.LayoutParams(-1, -2));
        b.addView(l, new LinearLayout.LayoutParams(-1, -2));
        return b;
    }

    private View avviso() {
        avviso = s.orizzontale();
        avviso.setPadding(s.dp(Stile.S4), s.dp(Stile.S3), s.dp(18), s.dp(Stile.S3));
        avviso.setBackground(new Stile.Vetro(0xF2181E27, Stile.FILO_ALTO, 0x14FFFFFF, s.dp(Stile.R_PANNELLO), s.dp(1)));
        avviso.setElevation(s.dp(12));
        puntoAvviso = s.punto(Stile.ACCENTO, 8);
        avviso.addView(puntoAvviso, s.lato(8));
        testoAvviso = s.testo("", Stile.T_CORPO - 1, Stile.TESTO, null);
        testoAvviso.setPadding(s.dp(10), 0, 0, 0);
        avviso.addView(testoAvviso);
        avviso.setVisibility(View.GONE);
        return avviso;
    }

    // ---- navigazione ----------------------------------------------------------------

    void vaiA(int quale) {
        if (quale == corrente) {
            pagine[quale].inCima();
            return;
        }
        if (quale == IMPOSTAZIONI && corrente >= 0 && corrente != IMPOSTAZIONI) primaDelleImpostazioni = corrente;
        if (corrente >= 0) {
            pagine[corrente].esce();
            viste[corrente].setVisibility(View.GONE);
        }
        corrente = quale;
        View v = viste[quale];
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.setTranslationY(s.dp(10));
        v.animate().alpha(1f).translationY(0).setDuration(200).start();

        titolo.setText(pagine[quale].titolo());
        tastoIndietro.setVisibility(quale == IMPOSTAZIONI ? View.VISIBLE : View.GONE);
        tastoImpostazioni.setVisibility(quale == IMPOSTAZIONI ? View.GONE : View.VISIBLE);
        for (int i = 0; i < 5; i++) {
            boolean q = i == quale;
            pastiglie[i].animate().alpha(q ? 1f : 0f).scaleX(q ? 1f : 0.6f).setDuration(200).start();
            int colore = q ? (i == SPOTIFY ? Stile.SPOTIFY : Stile.ACCENTO) : Stile.TERZO;
            iconeBarra[i].setImageDrawable(s.disegno(ICONE[i], colore));
            nomiBarra[i].setTextColor(q ? Stile.TESTO : Stile.TERZO);
        }
        nascondiTastiera();
        pagine[quale].entra();
        aggiornaCollegamento();
    }

    void nascondiTastiera() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && radice != null) imm.hideSoftInputFromWindow(radice.getWindowToken(), 0);
    }

    // ---- rete -------------------------------------------------------------------------

    interface Esito { void fatto(JSONObject risposta, String errore); }

    /**
     * Una richiesta al tablet, e la risposta sul thread dell'interfaccia.
     * {@code lenta} per quello che passa da Internet: sta in un'altra coda.
     */
    void chiedi(final String metodo, final String percorso, final JSONObject corpo, final int attesaMs,
                boolean lento, final Esito esito) {
        if (!tablet.abbinato()) {
            if (esito != null) esito.fatto(null, "Il telefono non è abbinato");
            return;
        }
        ExecutorService coda = lento ? lenta : rete;
        if (coda.isShutdown()) return;
        coda.execute(new Runnable() {
            @Override public void run() {
                Tablet.Risposta r = null;
                String errore = null;
                try {
                    r = "GET".equals(metodo) ? tablet.get(percorso, attesaMs) : tablet.post(percorso, corpo, attesaMs);
                    if (!r.ok()) errore = r.errore();
                } catch (Tablet.Irraggiungibile e) {
                    errore = e.getMessage();
                }
                final JSONObject json = r != null ? r.json : null;
                final String perche = errore;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (esito != null) esito.fatto(json, perche);
                    }
                });
            }
        });
    }

    void comanda(String percorso, JSONObject corpo, String fatto) {
        comanda(percorso, corpo, fatto, null);
    }

    /** Un comando, un messaggio se va, l'errore se non va, e dopo un momento
     *  lo stato: le lampade rispondono « ok » prima di essersi mosse. */
    void comanda(String percorso, JSONObject corpo, final String fatto, final Runnable poi) {
        chiedi("POST", percorso, corpo, 8000, false, new Esito() {
            @Override public void fatto(JSONObject risposta, String errore) {
                if (errore != null) { di(errore, true); return; }
                if (fatto != null) di(fatto, false);
                if (poi != null) poi.run();
                ui.removeCallbacks(aggiornaDopo);
                ui.postDelayed(aggiornaDopo, 1200);
            }
        });
    }

    /** Un decimo di volume su o giu', e il numero nuovo scritto in {@code dove}. */
    void volume(int passo, final TextView dove) {
        chiedi("POST", "/volume", Tablet.json("passo", passo), 6000, false, new Esito() {
            @Override public void fatto(JSONObject j, String errore) {
                if (errore != null) { di(errore, true); return; }
                int v = j != null ? j.optInt("volume", -1) : -1;
                if (dove != null && v >= 0) dove.setText(v + "%");
            }
        });
    }

    void aggiornaStato() {
        if (!tablet.abbinato() || tablet.indirizzo().length() == 0 || chiedendo) return;
        if (rete.isShutdown()) return;
        chiedendo = true;
        rete.execute(new Runnable() {
            @Override public void run() {
                Tablet.Risposta r = null;
                try {
                    r = tablet.get("/stato");
                } catch (Tablet.Irraggiungibile e) {
                    // L'indirizzo puo' essere cambiato: si cerca, con l'ultimo
                    // buono per primo.
                    if (tablet.trova(Principale.this, 4000)) {
                        try { r = tablet.get("/stato"); } catch (Tablet.Irraggiungibile ancora) { }
                    }
                }
                final Tablet.Risposta fatta = r;
                ui.post(new Runnable() {
                    @Override public void run() {
                        chiedendo = false;
                        if (fatta == null) {
                            ultimo = null;
                            guastoRete = "il tablet non risponde";
                        } else if (!fatta.ok()) {
                            ultimo = null;
                            guastoRete = fatta.errore();
                        } else {
                            ultimo = fatta.json;
                            guastoRete = null;
                            for (Pagina p : pagine) p.stato(fatta.json);
                        }
                        aggiornaCollegamento();
                    }
                });
            }
        });
    }

    /** La riga sotto il titolo: come sta il collegamento, col suo pallino. */
    void aggiornaCollegamento() {
        int colore;
        String come;
        if (!tablet.abbinato()) { colore = Stile.TERZO; come = "non abbinato"; }
        else if (ultimo != null) { colore = Stile.VERDE; come = "collegato a " + tablet.nomeTablet(); }
        else if (guastoRete != null) { colore = Stile.ERRORE; come = guastoRete; }
        else { colore = Stile.ACCENTO; come = "in attesa del tablet"; }
        ((GradientDrawable) puntoStato.getBackground()).setColor(colore);
        rigaStato.setText(come);
        ((PaginaTablet) pagine[IMPOSTAZIONI]).aggiorna(colore, come);
    }

    /** Un messaggio che passa: quello che e' appena successo. */
    void di(String messaggio, boolean errore) {
        if (messaggio == null || messaggio.length() == 0) return;
        testoAvviso.setText(messaggio);
        ((GradientDrawable) puntoAvviso.getBackground()).setColor(errore ? Stile.ERRORE : Stile.ACCENTO);
        ui.removeCallbacks(nascondiAvviso);
        if (avviso.getVisibility() != View.VISIBLE) {
            avviso.setAlpha(0f);
            avviso.setTranslationY(s.dp(8));
            avviso.setVisibility(View.VISIBLE);
        }
        avviso.animate().alpha(1f).translationY(0).setDuration(180).start();
        ui.postDelayed(nascondiAvviso, errore ? 4000 : 2400);
    }

    // ---- voce ---------------------------------------------------------------------

    /** Il riconoscimento vocale del telefono: quello che si dice va al tablet
     *  come una frase detta a lui. */
    void ascolta() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Cosa dico ad Assistente Home?");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(i, VOCE);
        } catch (ActivityNotFoundException nessuno) {
            di("su questo telefono non c'è il riconoscimento vocale", true);
        }
    }

    @Override protected void onActivityResult(int richiesta, int esito, Intent dati) {
        super.onActivityResult(richiesta, esito, dati);
        if (richiesta != VOCE || esito != RESULT_OK || dati == null) return;
        ArrayList<String> detto = dati.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (detto == null || detto.isEmpty() || detto.get(0).trim().length() == 0) return;
        String t = detto.get(0).trim();
        comanda("/frase", Tablet.json("testo", t), "« " + t + " »");
    }

    // ---- dialoghi ---------------------------------------------------------------------

    /** I dialoghi di sistema, vestiti di vetro, con la schermata sfocata dietro. */
    void mostra(AlertDialog.Builder b) {
        AlertDialog d = b.create();
        vetra(d);
        d.show();
    }

    void vetra(AlertDialog d) {
        Window w = d.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new InsetDrawable(new Stile.Vetro(0xF2121820, Stile.FILO_ALTO, Stile.FILO_BASSO,
                s.dp(28), s.dp(1)), s.dp(20)));
        if (Build.VERSION.SDK_INT >= 31) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams a = w.getAttributes();
            a.setBlurBehindRadius(s.dp(28));
            w.setAttributes(a);
        }
    }

    /** Sfoca una View col RenderEffect, dove c'e' (API 31+). */
    static void sfoca(View v, float raggio) {
        if (Build.VERSION.SDK_INT >= 31) {
            v.setRenderEffect(RenderEffect.createBlurEffect(raggio, raggio, Shader.TileMode.CLAMP));
        }
    }
}
