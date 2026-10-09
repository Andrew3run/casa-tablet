package dev.casa.telefono;

import android.graphics.drawable.GradientDrawable;
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
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Spotify del tablet, dal telefono: il lettore, le playlist, la ricerca, e
 * dentro artisti, album e playlist. Suona sempre il tablet; il telefono
 * sfoglia e comanda.
 *
 * <h3>Come si naviga</h3>
 *
 * La barra di ricerca sta sempre in cima. Sotto, a riposo, il lettore e le
 * playlist. Scrivendo, dopo quattro decimi di pausa, i risultati prendono il
 * posto di tutto il resto; da un risultato si scende - un artista, un album,
 * una playlist - e ogni livello si impila sul precedente. La freccia in cima
 * all'elenco (e il tasto indietro del telefono) risale di uno.
 *
 * <h3>Toccare un brano lo suona dentro il suo contesto</h3>
 *
 * Dentro un album o una playlist, un brano parte <b>da li'</b> e continua con
 * i successivi: e' la regola della sezione sul tablet, e un brano isolato che
 * finisce nel silenzio sembra un errore.
 */
final class PaginaSpotify extends Pagina {

    private static final int BRANO = 0, ARTISTA = 1, ALBUM = 2, INTESTAZIONE = 3, DISCOGRAFIA = 4, PLAYLIST = 5;
    private static final int RICERCA = 100, BRANI = 101;

    /** Un livello dell'elenco: una ricerca, un artista, un album, una playlist. */
    private static final class Livello {
        final int tipo;
        final String titolo, uri;
        final boolean elencabile;
        JSONArray righe;
        String errore;
        boolean inCorso = true;
        int scorrimento;
        Livello(int tipo, String titolo, String uri, boolean elencabile) {
            this.tipo = tipo; this.titolo = titolo; this.uri = uri; this.elencabile = elencabile;
        }
    }

    private final ArrayList<Livello> pila = new ArrayList<Livello>();

    private EditText cerca;
    private FrameLayout svuota;
    private FrameLayout contenuto;
    private ScrollView home, elenco;
    private LinearLayout righeElenco;

    // lettore
    private LinearLayout lettore, nonPronto;
    private ImageView copertina, segnoCopertina;
    private TextView brano, artista, tempoFatto, tempoTotale, volume, notaNonPronto;
    private FrameLayout mischia, precedente, principale, successivo;
    private Stile.Barretta avanzamento;
    private boolean suona, mischiata, pronta, cercaPronta = true, ceUnBrano;
    private long durata, posizione, misurataA;

    // playlist
    private Stile.Griglia griglia;
    private View vuotoPlaylist;
    private FrameLayout rileggi;
    private String firmaPlaylist = "";
    private boolean playlistLette;

    private int conta, giroRicerca;
    private final Runnable cercaOra = new Runnable() {
        @Override public void run() { avviaRicerca(); }
    };

    PaginaSpotify(Principale app) { super(app); }

    @Override String titolo() { return "Spotify"; }

    @Override View vista() {
        LinearLayout radice = s.verticale();

        // ---- la ricerca, sempre in cima
        Stile.Limite cima = new Stile.Limite(s.c, s.dp(Stile.LARGHEZZA), s.dp(Stile.S4));
        cima.setPadding(0, s.dp(Stile.S1), 0, s.dp(Stile.S3));
        cerca = s.campo("Brani, artisti, album", InputType.TYPE_CLASS_TEXT);
        cerca.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout capsula = s.capsula(R.drawable.ic_cerca, cerca);
        svuota = s.tastoIcona(R.drawable.ic_chiudi, 44, 20, Stile.FANTASMA, "svuota");
        svuota.setVisibility(View.GONE);
        svuota.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cerca.setText("");
                pila.clear();
                mostraLivello();
            }
        });
        capsula.addView(svuota, s.lato(44));
        cerca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence t, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                svuota.setVisibility(e.length() > 0 ? View.VISIBLE : View.GONE);
                app.ui.removeCallbacks(cercaOra);
                if (e.toString().trim().length() >= 2) app.ui.postDelayed(cercaOra, 400);
                else if (e.length() == 0 && !pila.isEmpty() && pila.get(0).tipo == RICERCA) { pila.clear(); mostraLivello(); }
            }
        });
        cerca.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int azione, android.view.KeyEvent e) {
                app.ui.removeCallbacks(cercaOra);
                avviaRicerca();
                app.nascondiTastiera();
                return true;
            }
        });
        cima.addView(capsula, new FrameLayout.LayoutParams(-1, -2));
        radice.addView(cima, new LinearLayout.LayoutParams(-1, -2));

        contenuto = new FrameLayout(s.c);
        home = s.rotolo(homeContenuto());
        contenuto.addView(home, new FrameLayout.LayoutParams(-1, -1));
        righeElenco = s.verticale();
        elenco = s.rotolo(righeElenco);
        elenco.setVisibility(View.GONE);
        contenuto.addView(elenco, new FrameLayout.LayoutParams(-1, -1));
        radice.addView(contenuto, new LinearLayout.LayoutParams(-1, 0, 1f));

        mostraLettore();
        return radice;
    }

    // ---- la schermata a riposo ------------------------------------------------------------

    private View homeContenuto() {
        LinearLayout col = s.verticale();

        nonPronto = s.vuoto(R.drawable.ic_spotify, "Spotify non è pronto sul tablet");
        notaNonPronto = (TextView) nonPronto.getChildAt(1);
        nonPronto.setVisibility(View.GONE);
        col.addView(nonPronto);

        lettore = s.verticale();
        lettore.setPadding(s.dp(Stile.S4), s.dp(Stile.S4), s.dp(Stile.S4), s.dp(Stile.S4));
        lettore.setBackground(s.pannello(Stile.R_PANNELLO + 4));

        LinearLayout su = s.orizzontale();
        su.setGravity(Gravity.TOP);
        FrameLayout riquadro = new FrameLayout(s.c);
        riquadro.setElevation(s.dp(8));
        riquadro.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        riquadro.setBackground(s.pieno(0xFF161B22, Stile.R_COMANDO));
        riquadro.setClipToOutline(true);
        copertina = new ImageView(s.c);
        copertina.setScaleType(ImageView.ScaleType.CENTER_CROP);
        riquadro.addView(copertina, new FrameLayout.LayoutParams(-1, -1));
        segnoCopertina = s.icona(R.drawable.ic_nota, Stile.TERZO);
        riquadro.addView(segnoCopertina, new FrameLayout.LayoutParams(s.dp(32), s.dp(32), Gravity.CENTER));
        su.addView(riquadro, s.lato(96));

        LinearLayout scritte = s.verticale();
        scritte.setPadding(s.dp(Stile.S4), s.dp(Stile.S1), 0, 0);
        brano = s.testo("", Stile.T_TITOLO - 2, Stile.TESTO, s.medio);
        brano.setMaxLines(2);
        brano.setEllipsize(android.text.TextUtils.TruncateAt.END);
        scritte.addView(brano);
        artista = s.riga("", Stile.T_CORPO, Stile.SECONDO, null);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(-1, -2);
        la.topMargin = s.dp(Stile.S1);
        scritte.addView(artista, la);
        su.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));

        mischia = s.tastoIcona(R.drawable.ic_casuale, 40, 22, Stile.FANTASMA, "ordine casuale");
        mischia.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mischiata = !mischiata;
                mostraLettore();
                comanda("/musica/mischia", Tablet.json("si", mischiata));
            }
        });
        LinearLayout.LayoutParams lm = s.lato(40);
        lm.leftMargin = s.dp(Stile.S2);
        su.addView(mischia, lm);
        lettore.addView(su);

        avanzamento = new Stile.Barretta(s, Stile.SPOTIFY);
        avanzamento.setSpostata(new Stile.Barretta.Spostata() {
            @Override public void a(float f) {
                if (durata <= 0) return;
                posizione = (long) (durata * f);
                misurataA = SystemClock.uptimeMillis();
                comanda("/musica/vai", Tablet.json("ms", posizione));
            }
        });
        lettore.addView(avanzamento, s.larga(s.dp(24), Stile.S4, 0));
        LinearLayout tempi = s.orizzontale();
        tempoFatto = s.riga("0:00", Stile.T_NOTA, Stile.TERZO, s.medio);
        tempoFatto.setFontFeatureSettings("tnum");
        tempi.addView(tempoFatto, new LinearLayout.LayoutParams(0, -2, 1f));
        tempoTotale = s.riga("0:00", Stile.T_NOTA, Stile.TERZO, s.medio);
        tempoTotale.setFontFeatureSettings("tnum");
        tempi.addView(tempoTotale);
        tempi.setPadding(s.dp(6), 0, s.dp(6), 0);
        lettore.addView(tempi);

        LinearLayout comandi = s.orizzontale();
        comandi.setGravity(Gravity.CENTER);
        precedente = s.tastoIcona(R.drawable.ic_indietro_brano, 52, 28, Stile.FANTASMA, "brano precedente");
        principale = s.tastoIcona(R.drawable.ic_play, 68, 34, Stile.VERDE_SPOTIFY, "play");
        successivo = s.tastoIcona(R.drawable.ic_avanti, 52, 28, Stile.FANTASMA, "brano successivo");
        comandi.addView(precedente, s.lato(52));
        LinearLayout.LayoutParams lp = s.lato(68);
        lp.leftMargin = lp.rightMargin = s.dp(Stile.S6);
        comandi.addView(principale, lp);
        comandi.addView(successivo, s.lato(52));
        lettore.addView(comandi, s.larga(-2, Stile.S2, 0));
        precedente.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { comanda("/musica/precedente", null); }
        });
        successivo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { comanda("/musica/successivo", null); }
        });
        principale.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                suona = !suona;
                misurataA = SystemClock.uptimeMillis();
                mostraLettore();
                comanda("/musica/pausa", null);
            }
        });

        View filo = new View(s.c);
        filo.setBackgroundColor(Stile.FILO);
        lettore.addView(filo, s.larga(s.dp(1), Stile.S4, Stile.S3));
        lettore.addView(filaVolume());
        col.addView(lettore, s.larga(-2, Stile.S1, 0));

        rileggi = s.tastoIcona(R.drawable.ic_aggiorna, 40, 20, Stile.FANTASMA, "rileggi le playlist");
        rileggi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.animate().rotationBy(360f).setDuration(600).start();
                leggiPlaylist();
            }
        });
        col.addView(s.titoloSezione("Le tue playlist", rileggi));
        griglia = s.griglia(150, 2, 4);
        col.addView(griglia);
        vuotoPlaylist = s.vuoto(R.drawable.ic_playlist, "Le playlist arrivano appena Spotify è pronto");
        col.addView(vuotoPlaylist);
        return col;
    }

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

    private void mostraLettore() {
        nonPronto.setVisibility(pronta ? View.GONE : View.VISIBLE);
        lettore.setVisibility(pronta ? View.VISIBLE : View.GONE);
        if (!ceUnBrano) {
            brano.setText("Niente in riproduzione");
            artista.setText("Scegli una playlist o cerca un brano");
            app.immagini.carica(copertina, null, 0);
            segnoCopertina.setVisibility(View.VISIBLE);
        }
        Stile.cambiaIcona(principale, s.disegno(suona ? R.drawable.ic_pausa : R.drawable.ic_play, Stile.SU_SPOTIFY));
        principale.setContentDescription(suona ? "pausa" : "riprendi");
        Stile.cambiaIcona(mischia, s.disegno(R.drawable.ic_casuale, mischiata ? Stile.SPOTIFY : Stile.TERZO));
        Stile.abilita(precedente, ceUnBrano);
        Stile.abilita(successivo, ceUnBrano);
        Stile.abilita(principale, ceUnBrano);
        Stile.abilita(mischia, ceUnBrano);
        avanzamento.setEnabled(ceUnBrano);
        aggiornaTempi();
    }

    private void aggiornaTempi() {
        long pos = posizione;
        if (suona) pos += SystemClock.uptimeMillis() - misurataA;
        if (durata > 0) pos = Math.min(pos, durata);
        avanzamento.setValore(durata > 0 ? pos / (float) durata : 0f);
        tempoFatto.setText(tempo(pos));
        tempoTotale.setText(tempo(durata));
    }

    static String tempo(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private void comanda(String percorso, JSONObject corpo) {
        app.comanda(percorso, corpo != null ? corpo : new JSONObject(), null, new Runnable() {
            @Override public void run() {
                app.ui.postDelayed(new Runnable() { @Override public void run() { leggi(); } }, 700);
            }
        });
    }

    // ---- lettura ---------------------------------------------------------------------------

    @Override void entra() {
        conta = 0;
        leggi();
        if (!playlistLette) leggiPlaylist();
    }

    @Override void secondo() {
        aggiornaTempi();
        if (++conta % 2 == 0) leggi();
    }

    private void leggi() {
        app.chiedi("GET", "/musica", null, 6000, false, new Principale.Esito() {
            @Override public void fatto(JSONObject j, String errore) {
                if (errore != null || j == null) return;
                boolean eraPronta = pronta;
                pronta = j.optBoolean("pronta", false);
                cercaPronta = j.optBoolean("cerca", true);
                suona = j.optBoolean("suona", false);
                mischiata = j.optBoolean("mischia", false);
                durata = j.optLong("durata", 0);
                posizione = j.optLong("posizione", 0);
                misurataA = SystemClock.uptimeMillis();
                ceUnBrano = !j.isNull("brano");
                int v = j.optInt("volume", -1);
                if (v >= 0) volume.setText(v + "%");
                if (ceUnBrano) {
                    brano.setText(j.optString("brano"));
                    artista.setText(j.isNull("artista") ? "" : j.optString("artista"));
                    String c = j.isNull("copertina") ? null : j.optString("copertina");
                    app.immagini.carica(copertina, c, s.dp(96) * 2);
                    segnoCopertina.setVisibility(c != null ? View.GONE : View.VISIBLE);
                }
                if (!pronta) {
                    String g = j.isNull("errore") ? null : j.optString("errore", null);
                    notaNonPronto.setText(g != null ? "Spotify non è pronto sul tablet: " + g : "Spotify non è pronto sul tablet");
                }
                mostraLettore();
                if (pronta && !eraPronta && !playlistLette) leggiPlaylist();
            }
        });
    }

    private void leggiPlaylist() {
        app.chiedi("GET", "/musica/playlist", null, 8000, false, new Principale.Esito() {
            @Override public void fatto(JSONObject j, String errore) {
                if (errore != null || j == null) {
                    if (errore != null && griglia.getChildCount() == 0) testoVuoto(vuotoPlaylist, errore);
                    return;
                }
                playlistLette = true;
                JSONArray elenco = j.optJSONArray("playlist");
                String firma = String.valueOf(elenco);
                if (!firma.equals(firmaPlaylist)) {
                    firmaPlaylist = firma;
                    griglia.removeAllViews();
                    for (int i = 0; elenco != null && i < elenco.length(); i++) {
                        JSONObject x = elenco.optJSONObject(i);
                        if (x != null) griglia.addView(tesseraPlaylist(x));
                    }
                }
                boolean niente = griglia.getChildCount() == 0;
                griglia.setVisibility(niente ? View.GONE : View.VISIBLE);
                vuotoPlaylist.setVisibility(niente ? View.VISIBLE : View.GONE);
                if (niente) {
                    String nota = j.isNull("nota") ? null : j.optString("nota", null);
                    testoVuoto(vuotoPlaylist, j.optBoolean("inCorso", false) ? "Sto leggendo le playlist…"
                            : nota != null ? nota : "Nessuna playlist");
                    if (j.optBoolean("inCorso", false)) {
                        app.ui.postDelayed(new Runnable() { @Override public void run() { leggiPlaylist(); } }, 2500);
                    }
                }
            }
        });
    }

    private static void testoVuoto(View vuoto, String t) {
        LinearLayout l = (LinearLayout) vuoto;
        ((TextView) l.getChildAt(l.getChildCount() - 1)).setText(t);
    }

    private View tesseraPlaylist(final JSONObject x) {
        LinearLayout t = s.verticale();
        Stile.Quadrato q = new Stile.Quadrato(s.c);
        q.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        q.setClipToOutline(true);
        boolean preferiti = !x.optBoolean("elencabile", true);
        if (preferiti) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] { 0xFF4A2BD8, 0xFF7FB8D6 });
            g.setCornerRadius(s.dp(Stile.R_COMANDO));
            q.setBackground(g);
            q.addView(s.icona(R.drawable.ic_cuore, 0xFFFFFFFF), new FrameLayout.LayoutParams(s.dp(40), s.dp(40), Gravity.CENTER));
        } else {
            q.setBackground(s.pieno(0xFF161B22, Stile.R_COMANDO));
            q.addView(s.icona(R.drawable.ic_playlist, Stile.TERZO), new FrameLayout.LayoutParams(s.dp(36), s.dp(36), Gravity.CENTER));
            ImageView im = new ImageView(s.c);
            im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            q.addView(im, new FrameLayout.LayoutParams(-1, -1));
            app.immagini.carica(im, x.isNull("immagine") ? null : x.optString("immagine"), s.dp(200));
        }
        q.setForeground(s.tocco(null, Stile.R_COMANDO));
        t.addView(q, new LinearLayout.LayoutParams(-1, -2));
        TextView n = s.testo(x.optString("nome"), Stile.T_PICCOLO + 1, Stile.TESTO, s.medio);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ln = new LinearLayout.LayoutParams(-1, -2);
        ln.topMargin = s.dp(Stile.S2);
        t.addView(n, ln);
        q.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                apri(new Livello(BRANI, x.optString("nome"), x.optString("uri"), x.optBoolean("elencabile", true)));
            }
        });
        return t;
    }

    // ---- ricerca e livelli -----------------------------------------------------------------

    private void avviaRicerca() {
        final String testo = cerca.getText().toString().trim();
        if (testo.length() < 2) return;
        if (!cercaPronta) {
            app.di("sul tablet la ricerca vuole la chiave di Spotify (docs/musica.md)", true);
            return;
        }
        pila.clear();
        final Livello l = new Livello(RICERCA, "Risultati per « " + testo + " »", null, false);
        pila.add(l);
        final int mio = ++giroRicerca;
        mostraLivello();
        app.chiedi("POST", "/musica/cerca", Tablet.json("testo", testo), 25000, true, new Principale.Esito() {
            @Override public void fatto(JSONObject j, String errore) {
                if (mio != giroRicerca) return;
                riempiLivello(l, j, errore, "risultati");
            }
        });
    }

    private void apri(final Livello l) {
        l.inCorso = true;
        if (!pila.isEmpty()) pila.get(pila.size() - 1).scorrimento = elenco.getScrollY();
        pila.add(l);
        mostraLivello();
        app.nascondiTastiera();
        if (l.tipo == BRANI) {
            app.chiedi("POST", "/musica/brani", Tablet.json("uri", l.uri, "elencabile", l.elencabile), 50000, true,
                    new Principale.Esito() {
                        @Override public void fatto(JSONObject j, String errore) { riempiLivello(l, j, errore, "brani"); }
                    });
        } else {
            app.chiedi("POST", "/musica/apri", Tablet.json("uri", l.uri, "tipo", l.tipo), 25000, true,
                    new Principale.Esito() {
                        @Override public void fatto(JSONObject j, String errore) { riempiLivello(l, j, errore, "risultati"); }
                    });
        }
    }

    private void riempiLivello(Livello l, JSONObject j, String errore, String chiave) {
        l.inCorso = false;
        l.errore = errore;
        l.righe = j != null ? j.optJSONArray(chiave) : null;
        if (l.errore == null && (l.righe == null || l.righe.length() == 0)) {
            l.errore = j != null && !j.isNull("nota") ? j.optString("nota") : "Niente da mostrare";
        }
        if (!pila.isEmpty() && pila.get(pila.size() - 1) == l) mostraLivello();
    }

    private void mostraLivello() {
        if (pila.isEmpty()) {
            elenco.setVisibility(View.GONE);
            home.setVisibility(View.VISIBLE);
            return;
        }
        final Livello l = pila.get(pila.size() - 1);
        home.setVisibility(View.GONE);
        elenco.setVisibility(View.VISIBLE);
        righeElenco.removeAllViews();

        // ---- la testa del livello: indietro, il titolo, e il tasto che suona tutto
        LinearLayout testa = s.orizzontale();
        testa.setMinimumHeight(s.dp(56));
        FrameLayout indietro = s.tastoIcona(R.drawable.ic_indietro, 44, 24, Stile.FANTASMA, "indietro");
        indietro.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { indietro(); }
        });
        LinearLayout.LayoutParams li = s.lato(44);
        li.leftMargin = -s.dp(Stile.S2);
        testa.addView(indietro, li);
        LinearLayout scritte = s.verticale();
        scritte.setPadding(s.dp(Stile.S1), 0, s.dp(Stile.S2), 0);
        scritte.addView(s.etichetta(etichettaDi(l.tipo)));
        TextView t = s.riga(l.titolo, Stile.T_TITOLO, Stile.TESTO, s.medio);
        scritte.addView(t);
        testa.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));
        if (l.uri != null && l.tipo != DISCOGRAFIA) {
            FrameLayout tutto = s.tastoIcona(R.drawable.ic_play, 52, 28, Stile.VERDE_SPOTIFY, "suona tutto");
            tutto.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    comanda("/musica/suona", Tablet.json("uri", l.uri));
                    app.di("suona sul tablet", false);
                }
            });
            testa.addView(tutto, s.lato(52));
        }
        righeElenco.addView(testa, s.larga(-2, 0, Stile.S3));

        if (l.inCorso) {
            righeElenco.addView(s.scheletro(8, true));
        } else if (l.errore != null) {
            righeElenco.addView(s.vuoto(R.drawable.ic_cerca, l.errore));
        } else {
            for (int i = 0; i < l.righe.length(); i++) {
                JSONObject x = l.righe.optJSONObject(i);
                if (x != null) righeElenco.addView(riga(l, x, i));
            }
        }
        final int dove = l.scorrimento;
        elenco.post(new Runnable() { @Override public void run() { elenco.scrollTo(0, dove); } });
    }

    private static String etichettaDi(int tipo) {
        switch (tipo) {
            case RICERCA: return "Ricerca";
            case ARTISTA: return "Artista";
            case ALBUM: return "Album";
            case DISCOGRAFIA: return "Discografia";
            default: return "Playlist";
        }
    }

    private View riga(final Livello l, final JSONObject x, int indice) {
        int tipo = l.tipo == BRANI ? BRANO : x.optInt("tipo", BRANO);
        if (tipo == INTESTAZIONE) {
            TextView e = s.etichetta(x.optString("titolo"));
            e.setPadding(s.dp(Stile.S2), s.dp(Stile.S5), 0, s.dp(Stile.S2));
            return e;
        }
        final String uri = x.isNull("uri") ? null : x.optString("uri");
        String titolo = x.optString("titolo");
        String sotto = l.tipo == BRANI
                ? (x.isNull("artista") ? "" : x.optString("artista")) + (x.optLong("durata") > 0 ? " · " + tempo(x.optLong("durata")) : "")
                : (x.isNull("sotto") ? "" : x.optString("sotto"));
        String immagine = x.isNull("immagine") ? null : x.optString("immagine");

        LinearLayout r = s.orizzontale();
        r.setPadding(s.dp(Stile.S2), s.dp(Stile.S2), s.dp(Stile.S1), s.dp(Stile.S2));
        r.setBackground(s.tocco(null, Stile.R_COMANDO));
        r.setMinimumHeight(s.dp(64));

        FrameLayout riquadro = new FrameLayout(s.c);
        boolean tondo = tipo == ARTISTA;
        riquadro.setBackground(tondo ? s.tondo(0xFF161B22) : s.pieno(0xFF161B22, Stile.R_IMMAGINE - 2));
        riquadro.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        riquadro.setClipToOutline(true);
        if (immagine == null && (l.tipo == ALBUM || l.tipo == BRANI && l.uri != null && l.uri.startsWith("spotify:album:"))) {
            TextView numero = s.testo(String.valueOf(indice + 1), Stile.T_CORPO, Stile.TERZO, s.medio);
            numero.setGravity(Gravity.CENTER);
            riquadro.setBackground(null);
            riquadro.addView(numero, new FrameLayout.LayoutParams(-1, -1));
        } else {
            int segno = tipo == ARTISTA ? R.drawable.ic_artista : tipo == ALBUM || tipo == DISCOGRAFIA ? R.drawable.ic_album
                    : tipo == PLAYLIST ? R.drawable.ic_playlist : R.drawable.ic_nota;
            riquadro.addView(s.icona(segno, Stile.TERZO), new FrameLayout.LayoutParams(s.dp(22), s.dp(22), Gravity.CENTER));
            ImageView im = new ImageView(s.c);
            im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            riquadro.addView(im, new FrameLayout.LayoutParams(-1, -1));
            app.immagini.carica(im, immagine, s.dp(96));
        }
        LinearLayout.LayoutParams lr = s.lato(48);
        lr.rightMargin = s.dp(Stile.S3);
        r.addView(riquadro, lr);

        LinearLayout scritte = s.verticale();
        scritte.addView(s.riga(titolo, Stile.T_CORPO, Stile.TESTO, s.medio));
        if (sotto.length() > 0) {
            TextView st = s.riga(sotto, Stile.T_PICCOLO, Stile.SECONDO, null);
            LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(-1, -2);
            ls.topMargin = s.dp(2);
            scritte.addView(st, ls);
        }
        r.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));

        final boolean apribile = tipo == ARTISTA || tipo == ALBUM || tipo == DISCOGRAFIA || tipo == PLAYLIST;
        ImageView fine = s.icona(apribile ? R.drawable.ic_freccia : R.drawable.ic_play, apribile ? Stile.TERZO : Stile.SPOTIFY);
        LinearLayout.LayoutParams lf = s.lato(apribile ? 22 : 24);
        lf.leftMargin = s.dp(Stile.S2);
        lf.rightMargin = s.dp(Stile.S2);
        r.addView(fine, lf);

        final int tipoFinale = tipo;
        final String titoloFinale = titolo;
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (uri == null) return;
                if (tipoFinale == PLAYLIST) {
                    apri(new Livello(BRANI, titoloFinale, uri, true));
                } else if (apribile) {
                    apri(new Livello(tipoFinale, titoloFinale, uri, false));
                } else if (l.uri != null && (l.tipo == BRANI || l.tipo == ALBUM)) {
                    // Dentro una playlist o un album: si parte da qui e si continua.
                    comanda("/musica/suona", Tablet.json("uri", l.uri, "brano", uri));
                    app.di("« " + titoloFinale + " » sul tablet", false);
                } else {
                    comanda("/musica/suona", Tablet.json("uri", uri));
                    app.di("« " + titoloFinale + " » sul tablet", false);
                }
            }
        });
        return r;
    }

    @Override boolean indietro() {
        if (pila.isEmpty()) return false;
        pila.remove(pila.size() - 1);
        if (pila.isEmpty() && cerca.getText().length() > 0) {
            cerca.setText("");
        }
        mostraLivello();
        return true;
    }

    @Override void inCima() {
        if (!pila.isEmpty()) {
            pila.clear();
            cerca.setText("");
            mostraLivello();
        } else {
            home.smoothScrollTo(0, 0);
        }
    }
}
