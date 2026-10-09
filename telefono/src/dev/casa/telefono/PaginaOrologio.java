package dev.casa.telefono;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Timer e sveglie, attorno alla ghiera: la stessa figura dell'Orologio sul
 * tablet, due schede in cima e sotto quello che e' gia' in piedi.
 *
 * Le durate pronte sono <b>tre</b>, non sei: sul tablet stanno in una colonna
 * che altrimenti sarebbe vuota, qui sarebbero due file di bottoni sopra la
 * cosa che conta. Uno, cinque e dieci minuti sono le tre che si usano davvero;
 * per gli undici della pasta c'e' la ghiera, che ci arriva con un gesto.
 */
final class PaginaOrologio extends Pagina {

    private static final int TIMER = 0, SVEGLIE = 1;
    private static final int[] PRONTI = { 1, 5, 10 };
    private static final String[] LETTERE = { "L", "M", "M", "G", "V", "S", "D" };
    /** Lunedi' per primo, come un calendario italiano; i bit partono dalla domenica. */
    private static final int[] BIT = { 1, 2, 3, 4, 5, 6, 0 };

    private int scheda = TIMER;
    private Stile.Segmentato schede;
    private Ghiera ghiera;

    private LinearLayout parteTimer, parteSveglie;
    private LinearLayout avvia;
    private TextView testoAvvia;
    private LinearLayout elencoTimer, elencoSveglie;
    private View vuotoTimer, vuotoSveglie;
    private TextView prossimaTablet, prossimaTelefono;
    private Switch segui;

    private final TextView[] giorni = new TextView[7];
    private int giorniScelti = 0x3E;
    private LinearLayout azioniNuova, azioniModifica;
    private JSONObject inMano;

    private String firmaTimer = "", firmaSveglie = "";
    private JSONArray sveglieUltime;

    private static final class Conto {
        final long fine, durata;
        final TextView resta;
        final Stile.Barretta barra;
        Conto(long fine, long durata, TextView resta, Stile.Barretta barra) {
            this.fine = fine; this.durata = durata; this.resta = resta; this.barra = barra;
        }
    }

    private final ArrayList<Conto> conti = new ArrayList<Conto>();

    PaginaOrologio(Principale app) { super(app); }

    @Override String titolo() { return "Orologio"; }

    @Override View vista() {
        LinearLayout col = s.verticale();

        schede = s.segmentato(new String[] { "Timer", "Sveglie" }, new Stile.Segmentato.Scelta() {
            @Override public void scelto(int quale) { vaiA(quale); }
        });
        schede.setScelto(TIMER);
        col.addView(schede, s.larga(-2, Stile.S1, 0));

        // ---- la ghiera, in un pannello suo
        LinearLayout pannello = s.verticale();
        pannello.setPadding(s.dp(Stile.S4), s.dp(Stile.S5), s.dp(Stile.S4), s.dp(Stile.S4));
        pannello.setBackground(s.pannello(Stile.R_PANNELLO + 4));
        ghiera = new Ghiera(s);
        ghiera.setAscolto(new Ghiera.Ascolto() {
            @Override public void cambiata(boolean finito) { aggiornaAvvia(); }
        });
        pannello.addView(ghiera, new LinearLayout.LayoutParams(-1, -2));

        // i giorni, solo per le sveglie
        LinearLayout filaGiorni = new LinearLayout(s.c);
        for (int i = 0; i < 7; i++) {
            final int quale = i;
            FrameLayout cella = new FrameLayout(s.c);
            TextView g = s.riga(LETTERE[i], Stile.T_PICCOLO + 1, Stile.SECONDO, s.medio);
            g.setGravity(Gravity.CENTER);
            g.setIncludeFontPadding(false);
            g.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    giorniScelti ^= 1 << BIT[quale];
                    disegnaGiorni();
                }
            });
            giorni[i] = g;
            cella.addView(g, new FrameLayout.LayoutParams(s.dp(40), s.dp(40), Gravity.CENTER));
            filaGiorni.addView(cella, new LinearLayout.LayoutParams(0, s.dp(44), 1f));
        }
        parteSveglie = s.verticale();
        parteSveglie.addView(filaGiorni, s.larga(-2, Stile.S4, 0));
        TextView aiuto = s.testo("Nessun giorno scelto = una volta sola", Stile.T_NOTA, Stile.TERZO, null);
        aiuto.setGravity(Gravity.CENTER);
        parteSveglie.addView(aiuto, s.larga(-2, Stile.S1, 0));
        pannello.addView(parteSveglie);

        // le azioni
        avvia = s.bottone("Avvia", R.drawable.ic_play, Stile.PRIMARIO);
        testoAvvia = (TextView) avvia.getChildAt(1);
        avvia.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { azionePrincipale(); }
        });
        azioniNuova = new LinearLayout(s.c);
        azioniNuova.addView(avvia, new LinearLayout.LayoutParams(0, s.dp(52), 1f));
        pannello.addView(azioniNuova, s.larga(-2, Stile.S5, 0));

        azioniModifica = s.orizzontale();
        FrameLayout elimina = s.tastoIcona(R.drawable.ic_cestino, 52, 22, Stile.PERICOLO, "togli la sveglia");
        elimina.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togli(); }
        });
        azioniModifica.addView(elimina, s.lato(52));
        LinearLayout annulla = s.bottone("Annulla", 0, Stile.SECONDARIO);
        annulla.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { lascia(); }
        });
        LinearLayout.LayoutParams lann = new LinearLayout.LayoutParams(0, s.dp(52), 1f);
        lann.leftMargin = s.dp(Stile.S3);
        azioniModifica.addView(annulla, lann);
        LinearLayout salva = s.bottone("Salva", R.drawable.ic_spunta, Stile.PRIMARIO);
        salva.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { salva(); }
        });
        LinearLayout.LayoutParams lsal = new LinearLayout.LayoutParams(0, s.dp(52), 1.4f);
        lsal.leftMargin = s.dp(Stile.S3);
        azioniModifica.addView(salva, lsal);
        pannello.addView(azioniModifica, s.larga(-2, Stile.S5, 0));
        col.addView(pannello, s.larga(-2, Stile.S4, 0));

        // ---- timer: avvio rapido e quelli in corso
        parteTimer = s.verticale();
        parteTimer.addView(s.titoloSezione("Avvio rapido", null));
        LinearLayout pronti = new LinearLayout(s.c);
        for (int i = 0; i < PRONTI.length; i++) {
            final int m = PRONTI[i];
            LinearLayout b = s.bottone(m + " min", R.drawable.ic_timer, Stile.SECONDARIO);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    app.comanda("/timer", Tablet.json("secondi", m * 60), m == 1 ? "timer di un minuto" : "timer di " + m + " minuti");
                }
            });
            pronti.addView(b, s.pesata(s.dp(Stile.TOCCO), i < PRONTI.length - 1 ? Stile.S3 : 0));
        }
        parteTimer.addView(pronti);
        parteTimer.addView(s.titoloSezione("In corso", null));
        elencoTimer = s.verticale();
        parteTimer.addView(elencoTimer);
        vuotoTimer = s.vuoto(R.drawable.ic_timer, "Nessun timer in corso");
        parteTimer.addView(vuotoTimer);
        col.addView(parteTimer);

        // ---- sveglie: quelle del tablet, e quella del telefono
        LinearLayout elencoParte = s.verticale();
        elencoParte.addView(s.titoloSezione("Sul tablet", null));
        prossimaTablet = s.testo("", Stile.T_PICCOLO, Stile.SECONDO, null);
        prossimaTablet.setPadding(s.dp(Stile.S1), 0, 0, s.dp(Stile.S3));
        elencoParte.addView(prossimaTablet);
        elencoSveglie = s.verticale();
        elencoParte.addView(elencoSveglie);
        vuotoSveglie = s.vuoto(R.drawable.ic_sveglia, "Nessuna sveglia");
        elencoParte.addView(vuotoSveglie);

        elencoParte.addView(s.titoloSezione("Dal telefono", null));
        LinearLayout specchio = s.orizzontale();
        specchio.setPadding(s.dp(Stile.S4), s.dp(Stile.S3), s.dp(Stile.S3), s.dp(Stile.S3));
        specchio.setBackground(s.pannello(Stile.R_PANNELLO));
        FrameLayout tondo = new FrameLayout(s.c);
        tondo.setBackground(s.tondo(0x26F2D06B));
        tondo.addView(s.icona(R.drawable.ic_sveglia, Stile.ACCENTO), new FrameLayout.LayoutParams(s.dp(22), s.dp(22), Gravity.CENTER));
        specchio.addView(tondo, s.lato(44));
        LinearLayout scritte = s.verticale();
        scritte.setPadding(s.dp(Stile.S3 + 2), 0, s.dp(Stile.S2), 0);
        scritte.addView(s.testo("Segui la sveglia del telefono", Stile.T_NOME - 1, Stile.TESTO, s.medio));
        prossimaTelefono = s.testo("", Stile.T_NOTA + 0.5f, Stile.SECONDO, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = s.dp(2);
        scritte.addView(prossimaTelefono, lp);
        specchio.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));
        segui = s.interruttore(app.tablet.segueLaSveglia());
        segui.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean si) {
                app.tablet.setSegueLaSveglia(si);
                Specchio.programma(app);
                aggiornaProssimaTelefono();
            }
        });
        specchio.addView(segui);
        elencoParte.addView(specchio);
        parteSveglie.setTag(elencoParte);
        col.addView(elencoParte);

        vaiA(TIMER);
        return s.rotolo(col);
    }

    // ---- schede ---------------------------------------------------------------------

    private void vaiA(int quale) {
        scheda = quale;
        schede.setScelto(quale);
        inMano = null;
        boolean timer = quale == TIMER;
        ghiera.setModo(timer ? Ghiera.TIMER : Ghiera.SVEGLIA);
        if (timer) ghiera.set(10, 0);
        else ghiera.set(7, 0);
        giorniScelti = 0x3E;
        parteTimer.setVisibility(timer ? View.VISIBLE : View.GONE);
        parteSveglie.setVisibility(timer ? View.GONE : View.VISIBLE);
        ((View) parteSveglie.getTag()).setVisibility(timer ? View.GONE : View.VISIBLE);
        azioniModifica.setVisibility(View.GONE);
        azioniNuova.setVisibility(View.VISIBLE);
        disegnaGiorni();
        aggiornaAvvia();
        aggiornaProssimaTelefono();
        if (!timer && sveglieUltime != null) { firmaSveglie = ""; disegnaSveglie(sveglieUltime, null); }
    }

    private void aggiornaAvvia() {
        if (scheda == TIMER) {
            long secondi = ghiera.interno() * 3600L + ghiera.esterno() * 60L;
            testoAvvia.setText(secondi == 0 ? "Scegli la durata" : "Avvia " + durataBreve(secondi));
            ((android.widget.ImageView) avvia.getChildAt(0)).setImageDrawable(s.disegno(R.drawable.ic_play, Stile.SU_ACCENTO));
            // Con lo zero sotto la finestra il tasto e' spento: un « avvia »
            // che non avvia niente e' un tasto rotto.
            Stile.abilita(avvia, secondi > 0);
        } else {
            testoAvvia.setText(String.format(Locale.ROOT, "Aggiungi sveglia alle %02d:%02d", ghiera.esterno(), ghiera.interno() * 5));
            ((android.widget.ImageView) avvia.getChildAt(0)).setImageDrawable(s.disegno(R.drawable.ic_piu, Stile.SU_ACCENTO));
            Stile.abilita(avvia, true);
        }
    }

    private void azionePrincipale() {
        if (scheda == TIMER) {
            long secondi = ghiera.interno() * 3600L + ghiera.esterno() * 60L;
            if (secondi <= 0) return;
            app.comanda("/timer", Tablet.json("secondi", secondi), "timer di " + durataBreve(secondi));
        } else {
            final int ora = ghiera.esterno(), minuto = ghiera.interno() * 5;
            app.comanda("/sveglia/aggiungi", Tablet.json("ora", ora, "minuto", minuto, "giorni", giorniScelti),
                    String.format(Locale.ROOT, "sveglia alle %02d:%02d", ora, minuto));
        }
    }

    private void disegnaGiorni() {
        for (int i = 0; i < 7; i++) {
            boolean si = (giorniScelti & (1 << BIT[i])) != 0;
            giorni[i].setTextColor(si ? Stile.SU_ACCENTO : Stile.SECONDO);
            giorni[i].setBackground(si ? s.tondo(Stile.ACCENTO) : s.tondo(0x12FFFFFF));
        }
    }

    // ---- una sveglia in mano ---------------------------------------------------------------

    /** Toccare una sveglia la porta nella ghiera; toccarla di nuovo la lascia. */
    private void prendi(JSONObject x) {
        if (inMano != null && inMano.optInt("id") == x.optInt("id")) { lascia(); return; }
        inMano = x;
        ghiera.set(x.optInt("ora"), Math.round(x.optInt("minuto") / 5f));
        giorniScelti = x.optInt("giorni", 0);
        disegnaGiorni();
        azioniNuova.setVisibility(View.GONE);
        azioniModifica.setVisibility(View.VISIBLE);
        firmaSveglie = "";
        disegnaSveglie(sveglieUltime, null);
        ((android.widget.ScrollView) ((View) ghiera.getParent().getParent().getParent()).getParent()).smoothScrollTo(0, 0);
    }

    private void lascia() {
        inMano = null;
        ghiera.set(7, 0);
        giorniScelti = 0x3E;
        disegnaGiorni();
        azioniModifica.setVisibility(View.GONE);
        azioniNuova.setVisibility(View.VISIBLE);
        aggiornaAvvia();
        firmaSveglie = "";
        disegnaSveglie(sveglieUltime, null);
    }

    private void salva() {
        if (inMano == null) return;
        int minuto = ghiera.interno() * 5;
        app.comanda("/sveglia/regola", Tablet.json("id", inMano.optInt("id"), "ora", ghiera.esterno(),
                "minuto", minuto, "giorni", giorniScelti),
                String.format(Locale.ROOT, "sveglia spostata alle %02d:%02d", ghiera.esterno(), minuto));
        lascia();
    }

    private void togli() {
        if (inMano == null) return;
        final JSONObject x = inMano;
        app.mostra(new AlertDialog.Builder(app)
                .setMessage(String.format(Locale.ROOT, "Togliere la sveglia delle %02d:%02d?", x.optInt("ora"), x.optInt("minuto")))
                .setNegativeButton("No", null)
                .setPositiveButton("Togli", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        app.comanda("/sveglia/togli", Tablet.json("id", x.optInt("id")), "sveglia tolta");
                        lascia();
                    }
                }));
    }

    // ---- elenchi --------------------------------------------------------------------------

    private void disegnaTimer(JSONArray t) {
        StringBuilder firma = new StringBuilder();
        for (int i = 0; t != null && i < t.length(); i++) {
            JSONObject x = t.optJSONObject(i);
            if (x != null) firma.append(x.optInt("id")).append(',');
        }
        long adesso = SystemClock.uptimeMillis();
        if (!firma.toString().equals(firmaTimer)) {
            firmaTimer = firma.toString();
            elencoTimer.removeAllViews();
            conti.clear();
            for (int i = 0; t != null && i < t.length(); i++) {
                final JSONObject x = t.optJSONObject(i);
                if (x == null) continue;
                LinearLayout c = s.verticale();
                c.setPadding(s.dp(Stile.S4), s.dp(Stile.S3), s.dp(Stile.S3), s.dp(Stile.S4));
                c.setBackground(s.pannello(Stile.R_PANNELLO));
                LinearLayout fila = s.orizzontale();
                LinearLayout scritte = s.verticale();
                TextView resta = s.riga("", 32, Stile.TESTO, s.medio);
                resta.setFontFeatureSettings("tnum");
                resta.setIncludeFontPadding(false);
                scritte.addView(resta);
                TextView nome = s.riga("Timer di " + x.optString("nome", ""), Stile.T_PICCOLO, Stile.SECONDO, null);
                LinearLayout.LayoutParams ln = new LinearLayout.LayoutParams(-1, -2);
                ln.topMargin = s.dp(Stile.S1);
                scritte.addView(nome, ln);
                fila.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));
                FrameLayout ferma = s.tastoIcona(R.drawable.ic_chiudi, 44, 22, Stile.SECONDARIO, "ferma il timer");
                ferma.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        app.comanda("/timer/ferma", Tablet.json("id", x.optInt("id")), "timer fermato");
                    }
                });
                fila.addView(ferma, s.lato(44));
                c.addView(fila);
                Stile.Barretta barra = new Stile.Barretta(s, Stile.ACCENTO);
                c.addView(barra, s.larga(s.dp(12), Stile.S3, 0));
                elencoTimer.addView(c, s.larga(-2, 0, Stile.S3));
                conti.add(new Conto(adesso + x.optLong("restano") * 1000L, Math.max(1, x.optLong("durata", 1)), resta, barra));
            }
        } else {
            for (int i = 0, k = 0; t != null && i < t.length() && k < conti.size(); i++) {
                JSONObject x = t.optJSONObject(i);
                if (x == null) continue;
                Conto vecchio = conti.get(k);
                conti.set(k++, new Conto(adesso + x.optLong("restano") * 1000L, vecchio.durata, vecchio.resta, vecchio.barra));
            }
        }
        vuotoTimer.setVisibility(conti.isEmpty() ? View.VISIBLE : View.GONE);
        secondo();
    }

    private void disegnaSveglie(JSONArray sv, String prossima) {
        if (prossima != null || sveglieUltime == null) {
            prossimaTablet.setText(prossima != null ? "Il tablet suona " + prossima : "");
            prossimaTablet.setVisibility(prossima != null ? View.VISIBLE : View.GONE);
        }
        sveglieUltime = sv;
        String firma = String.valueOf(sv) + (inMano != null ? inMano.optInt("id") : -1);
        if (firma.equals(firmaSveglie)) return;
        firmaSveglie = firma;
        elencoSveglie.removeAllViews();
        for (int i = 0; sv != null && i < sv.length(); i++) {
            final JSONObject x = sv.optJSONObject(i);
            if (x == null) continue;
            final boolean delTelefono = x.optBoolean("telefono", false);
            boolean attiva = x.optBoolean("attiva", true);
            boolean inManoOra = inMano != null && inMano.optInt("id") == x.optInt("id");

            LinearLayout c = s.orizzontale();
            c.setPadding(s.dp(Stile.S5 - 4), s.dp(Stile.S3), s.dp(Stile.S3), s.dp(Stile.S3));
            c.setBackground(s.tocco(inManoOra
                    ? new Stile.Vetro(0x1FF2D06B, 0xCCF2D06B, 0x66F2D06B, s.dp(Stile.R_PANNELLO), s.dp(1.5f))
                    : s.pannello(Stile.R_PANNELLO), Stile.R_PANNELLO));
            LinearLayout scritte = s.verticale();
            TextView ora = s.riga(String.format(Locale.ROOT, "%02d:%02d", x.optInt("ora"), x.optInt("minuto")),
                    34, attiva ? Stile.TESTO : Stile.TERZO, s.leggero);
            ora.setFontFeatureSettings("tnum");
            ora.setIncludeFontPadding(false);
            scritte.addView(ora);
            TextView quando = s.riga(delTelefono ? "dal telefono" : giorni(x.optInt("giorni")), Stile.T_PICCOLO,
                    delTelefono ? Stile.ACCENTO : Stile.SECONDO, delTelefono ? s.medio : null);
            LinearLayout.LayoutParams lq = new LinearLayout.LayoutParams(-1, -2);
            lq.topMargin = s.dp(Stile.S1);
            scritte.addView(quando, lq);
            c.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));

            if (delTelefono) {
                c.addView(s.icona(R.drawable.ic_sveglia, Stile.TERZO), s.lato(22));
                c.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        app.di("questa la decide la sveglia del telefono", false);
                    }
                });
            } else {
                Switch acc = s.interruttore(attiva);
                acc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean si) {
                        app.comanda("/sveglia/accendi", Tablet.json("id", x.optInt("id"), "attiva", si), null);
                    }
                });
                c.addView(acc);
                c.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { prendi(x); }
                });
            }
            elencoSveglie.addView(c, s.larga(-2, 0, Stile.S3));
        }
        vuotoSveglie.setVisibility(elencoSveglie.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void aggiornaProssimaTelefono() {
        long q = Specchio.prossima(app);
        String dice = q == 0 ? "Il telefono non ha sveglie in programma"
                : "Prossima: " + DateFormat.format("EEE d MMM, HH:mm", new Date(q));
        if (app.tablet.segueLaSveglia() && q != 0) {
            dice += app.tablet.svegliaInviata() == q ? " · arrivata al tablet" : " · non ancora al tablet";
        }
        prossimaTelefono.setText(dice);
    }

    @Override void secondo() {
        long adesso = SystemClock.uptimeMillis();
        for (Conto c : conti) {
            long restano = Math.max(0L, (c.fine - adesso + 999) / 1000);
            c.resta.setText(mmss(restano));
            c.barra.setValore(restano / (float) c.durata);
        }
    }

    @Override void entra() {
        aggiornaProssimaTelefono();
    }

    @Override void stato(JSONObject st) {
        disegnaTimer(st.optJSONArray("timer"));
        String prossima = st.isNull("prossimaSveglia") ? null : st.optString("prossimaSveglia", null);
        prossimaTablet.setText(prossima != null ? "Il tablet suona " + prossima : "Nessuna sveglia in programma");
        prossimaTablet.setVisibility(View.VISIBLE);
        disegnaSveglie(st.optJSONArray("sveglie"), null);
        aggiornaProssimaTelefono();
    }

    // ---- testi ------------------------------------------------------------------------------

    static String mmss(long secondi) {
        long h = secondi / 3600, m = (secondi % 3600) / 60, x = secondi % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, x) : String.format(Locale.ROOT, "%02d:%02d", m, x);
    }

    private static String durataBreve(long secondi) {
        long h = secondi / 3600, m = (secondi % 3600) / 60;
        if (h > 0 && m > 0) return h + " h " + m + " min";
        if (h > 0) return h == 1 ? "1 ora" : h + " ore";
        return m == 1 ? "1 minuto" : m + " minuti";
    }

    private static final String[] NOMI_GIORNI = { "lun", "mar", "mer", "gio", "ven", "sab", "dom" };

    static String giorni(int g) {
        if (g == 0) return "una volta sola";
        if (g == 0x7F) return "tutti i giorni";
        if (g == 0x3E) return "da lunedì a venerdì";
        if (g == 0x41) return "sabato e domenica";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((g & (1 << BIT[i])) == 0) continue;
            if (b.length() > 0) b.append(' ');
            b.append(NOMI_GIORNI[i]);
        }
        return b.toString();
    }
}
