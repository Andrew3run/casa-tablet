package dev.casa.telefono;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Il tablet: dove sta, l'abbinamento, e il modo di dimenticarlo. Si apre dalla
 * rotella in alto, perche' e' una cosa che si fa una volta.
 */
final class PaginaTablet extends Pagina {

    private TextView nome, come, spiega;
    private ImageView icona;
    private GradientDrawable tondo;
    private EditText indirizzo, codice;
    private View dissocia;

    PaginaTablet(Principale app) { super(app); }

    @Override String titolo() { return "Tablet"; }

    @Override View vista() {
        LinearLayout col = s.verticale();

        LinearLayout carta = s.orizzontale();
        carta.setPadding(s.dp(Stile.S4), s.dp(Stile.S4), s.dp(Stile.S4), s.dp(Stile.S4));
        carta.setBackground(s.pannello(Stile.R_PANNELLO));
        FrameLayout riquadro = new FrameLayout(s.c);
        tondo = new GradientDrawable();
        tondo.setShape(GradientDrawable.OVAL);
        riquadro.setBackground(tondo);
        icona = new ImageView(s.c);
        riquadro.addView(icona, new FrameLayout.LayoutParams(s.dp(26), s.dp(26), Gravity.CENTER));
        carta.addView(riquadro, s.lato(52));
        LinearLayout scritte = s.verticale();
        scritte.setPadding(s.dp(Stile.S4), 0, 0, 0);
        nome = s.riga("", Stile.T_NOME + 2, Stile.TESTO, s.medio);
        scritte.addView(nome);
        come = s.testo("", Stile.T_PICCOLO, Stile.SECONDO, null);
        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(-1, -2);
        lc.topMargin = s.dp(2);
        scritte.addView(come, lc);
        carta.addView(scritte, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(carta, s.larga(-2, Stile.S2, 0));

        col.addView(s.titoloSezione("Indirizzo", null));
        indirizzo = s.campo("192.168.1.x", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        indirizzo.setText(app.tablet.indirizzo());
        col.addView(s.capsula(R.drawable.ic_wifi, indirizzo));
        LinearLayout fila = new LinearLayout(s.c);
        LinearLayout cerca = s.bottone("Cerca in casa", R.drawable.ic_cerca, Stile.SECONDARIO);
        cerca.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cerca(); }
        });
        LinearLayout usa = s.bottone("Usa questo", 0, Stile.SECONDARIO);
        usa.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                app.tablet.setIndirizzo(indirizzo.getText().toString());
                app.nascondiTastiera();
                app.di("indirizzo salvato", false);
                app.aggiornaStato();
            }
        });
        fila.addView(cerca, s.pesata(s.dp(Stile.TOCCO), Stile.S3));
        fila.addView(usa, s.pesata(s.dp(Stile.TOCCO), 0));
        col.addView(fila, s.larga(-2, Stile.S3, 0));

        col.addView(s.titoloSezione("Abbinamento", null));
        spiega = s.testo("", Stile.T_PICCOLO, Stile.SECONDO, null);
        spiega.setPadding(s.dp(Stile.S1), 0, s.dp(Stile.S1), 0);
        col.addView(spiega);
        LinearLayout chiedi = s.bottone("Chiedi il codice", 0, Stile.SECONDARIO);
        chiedi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chiediCodice(); }
        });
        col.addView(chiedi, s.larga(s.dp(Stile.TOCCO), Stile.S3, Stile.S3));

        codice = s.campo("······", InputType.TYPE_CLASS_NUMBER);
        codice.setFilters(new InputFilter[] { new InputFilter.LengthFilter(6) });
        codice.setGravity(Gravity.CENTER);
        codice.setTextSize(26);
        codice.setLetterSpacing(0.35f);
        codice.setTypeface(s.medio);
        codice.setBackground(s.pannello(Stile.R_PANNELLO));
        codice.setImeOptions(EditorInfo.IME_ACTION_DONE);
        codice.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int azione, android.view.KeyEvent e) {
                abbina();
                return true;
            }
        });
        col.addView(codice, s.larga(s.dp(64)));

        LinearLayout abbina = s.bottone("Abbina", R.drawable.ic_abbina, Stile.PRIMARIO);
        abbina.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { abbina(); }
        });
        col.addView(abbina, s.larga(s.dp(52), Stile.S3, 0));

        dissocia = s.bottone("Dissocia questo telefono", R.drawable.ic_dissocia, Stile.PERICOLO);
        dissocia.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                app.mostra(new AlertDialog.Builder(app)
                        .setTitle("Dissociare il telefono?")
                        .setMessage("Per comandare di nuovo il tablet servirà un nuovo codice.")
                        .setNegativeButton("No", null)
                        .setPositiveButton("Dissocia", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                app.tablet.dissocia();
                                app.tablet.setSvegliaInviata(-1);
                                app.ultimo = null;
                                app.guastoRete = null;
                                app.di("telefono dissociato", false);
                                app.aggiornaCollegamento();
                            }
                        }));
            }
        });
        col.addView(dissocia, s.larga(s.dp(Stile.TOCCO), Stile.S6, 0));
        return s.rotolo(col);
    }

    /** Lo chiama la cornice ogni volta che cambia lo stato del collegamento. */
    void aggiorna(int colore, String testo) {
        if (nome == null) return;
        String dove = app.tablet.indirizzo().length() > 0 ? app.tablet.indirizzo() : "indirizzo sconosciuto";
        nome.setText(app.tablet.abbinato() ? app.tablet.nomeTablet() : "Nessun tablet");
        come.setText(testo + " · " + dove);
        tondo.setColor(Stile.conAlfa(colore, 0x2E));
        icona.setImageDrawable(s.disegno(R.drawable.ic_tablet, colore));
        dissocia.setVisibility(app.tablet.abbinato() ? View.VISIBLE : View.GONE);
        spiega.setText(app.tablet.abbinato()
                ? "Già abbinato. Rifallo solo se il tablet non ti riconosce più."
                : "Chiedi il codice: compare sullo schermo del tablet per tre minuti.");
    }

    private void cerca() {
        app.di("cerco il tablet in casa…", false);
        new Thread(new Runnable() {
            @Override public void run() {
                final String ip = Scoperta.cerca(app, 6000);
                app.ui.post(new Runnable() {
                    @Override public void run() {
                        if (ip == null) {
                            app.di("non l'ho trovato: sei sul Wi-Fi di casa?", true);
                            return;
                        }
                        app.tablet.setIndirizzo(ip);
                        indirizzo.setText(ip);
                        app.di("trovato: " + ip, false);
                        app.aggiornaStato();
                        app.aggiornaCollegamento();
                    }
                });
            }
        }).start();
    }

    private interface Passo {
        Tablet.Risposta fai() throws Tablet.Irraggiungibile;
        void poi(Tablet.Risposta r);
    }

    /** L'abbinamento non e' firmato, quindi non passa da Principale.chiedi,
     *  che vuole un telefono gia' abbinato. */
    private void esegui(final Passo p) {
        new Thread(new Runnable() {
            @Override public void run() {
                Tablet.Risposta r = null;
                String guasto = null;
                try {
                    r = p.fai();
                } catch (Tablet.Irraggiungibile e) {
                    guasto = e.getMessage();
                }
                final Tablet.Risposta fatta = r;
                final String perche = guasto;
                app.ui.post(new Runnable() {
                    @Override public void run() {
                        if (fatta == null) { app.di(perche, true); return; }
                        p.poi(fatta);
                    }
                });
            }
        }).start();
    }

    private void chiediCodice() {
        app.tablet.setIndirizzo(indirizzo.getText().toString());
        esegui(new Passo() {
            @Override public Tablet.Risposta fai() throws Tablet.Irraggiungibile {
                return app.tablet.chiediCodice(Build.MODEL);
            }
            @Override public void poi(Tablet.Risposta r) {
                if (r.ok()) {
                    app.di("guarda il tablet: c'è il codice", false);
                    codice.requestFocus();
                } else {
                    app.di(r.errore(), true);
                }
            }
        });
    }

    private void abbina() {
        final String scritto = codice.getText().toString().trim();
        if (scritto.length() != 6) { app.di("il codice ha sei cifre", true); return; }
        esegui(new Passo() {
            @Override public Tablet.Risposta fai() throws Tablet.Irraggiungibile {
                return app.tablet.abbina(Build.MODEL, scritto);
            }
            @Override public void poi(Tablet.Risposta r) {
                if (r.ok() && app.tablet.abbinato()) {
                    codice.setText("");
                    app.nascondiTastiera();
                    app.di("abbinato a " + app.tablet.nomeTablet(), false);
                    app.guastoRete = null;
                    app.tablet.setSvegliaInviata(-1);
                    if (app.tablet.segueLaSveglia()) Specchio.programma(app);
                    app.vaiA(Principale.CASA);
                    app.aggiornaStato();
                } else {
                    app.di(r.errore(), true);
                }
            }
        });
    }

    @Override void entra() {
        indirizzo.setText(app.tablet.indirizzo());
    }

    @Override void stato(JSONObject st) { }
}
