package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Il riposo: una fotografia a tutto schermo, l'ora sopra, e un tocco per
 * tornare.
 *
 * <h3>Perche' assomiglia a Windows</h3>
 *
 * Non per citarlo: perche' quella disposizione e' gia' stata risolta. Una
 * fotografia occupa tutto, l'ora sta <b>in basso a sinistra</b> - dove non
 * copre mai il soggetto, che in un paesaggio sta al centro o sull'orizzonte - e
 * la didascalia sta nell'angolo opposto, piccola. Sotto al testo passa una
 * sfumatura scura che non si vede come sfumatura ma senza la quale l'ora
 * bianca, su una foto di neve, sparisce.
 *
 * <h3>Un fotogramma al minuto</h3>
 *
 * Questa schermata sta accesa per ore. Ridisegnarla a sessanta fotogrammi al
 * secondo per un'ora che cambia una volta al minuto sarebbe corrente buttata,
 * ed e' la stessa regola dell'orologio della Home: il tempo si sveglia sul
 * secondo pieno, guarda se il testo e' cambiato, e quasi sempre torna a
 * dormire. Gli unici fotogrammi veri sono i novecento millisecondi della
 * dissolvenza fra una foto e l'altra.
 *
 * <h3>Di notte si abbassa</h3>
 *
 * Lo schermo di Casa non si spegne mai ({@code FLAG_KEEP_SCREEN_ON}), quindi
 * quello che si vede alle tre di notte e' proprio questo. Una fotografia a
 * piena luce in una stanza buia e' una lampada, non uno schermo: dalle dieci di
 * sera alle cinque del mattino ci passa sopra un velo scuro, e l'ora resta
 * leggibile ma smette di illuminare il corridoio. Le fasce sono le stesse di
 * {@link Sfondo}, che le usa gia' per lo sfondo generato: una sola idea di
 * "che ora e'" per tutta Casa.
 *
 * <h3>Il tocco</h3>
 *
 * Qualunque punto, e si va alla Home. Non c'e' un tasto, e non c'e' niente da
 * imparare: chi passa davanti tocca lo schermo e trova Casa dov'era.
 *
 * <b>Il gesto intero e' della vela</b>, dal dito che scende a quello che si
 * alza. Quando il dito scende la vela comincia a sparire e sotto compare la
 * Home, ma resta al suo posto - trasparente - finche' il dito non si alza, e
 * solo allora si toglie. La prima versione si toglieva al tocco che scende:
 * staccata a meta' gesto, il primo spostamento del dito la cancellava e il
 * resto del tocco finiva sulla Home, che premeva il tasto che c'era sotto -
 * il microfono, una lampada. Con {@code input tap} non succedeva (due eventi
 * e basta), con un dito vero si'.
 */
public class VelaRiposo extends View implements Telaio.Velata {

    /** Chi avvisare quando questa schermata se ne va, comunque sia successo:
     *  un dito, una sveglia che le passa sopra, il tasto indietro. */
    public interface Fine {
        /** Il dito si e' posato: sotto si prepara la Home. */
        void suPosato();
        /** Il dito si e' alzato: la vela se ne va. */
        void suTocco();
        /** E' uscita di scena: qui si restituisce quel che si e' preso. */
        void suUscita();
    }

    private static final String[] GIORNI = {
        "domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato"
    };
    private static final String[] MESI = {
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"
    };

    /** Quanto dura il passaggio da una foto all'altra. Lungo apposta: e'
     *  l'unica cosa che si muove su questa schermata, e una dissolvenza rapida
     *  fra due fotografie si legge come uno scatto. */
    private static final int DISSOLVENZA = 900;

    /** Quanto si stringono le cifre dell'ora: appena, perche' il carattere
     *  sottile ha gia' le forme aperte - a corpo grande la spaziatura normale
     *  fa quattro segni separati invece di due coppie. */
    private static final float STRETTA_ORA = -0.02f;

    /** L'ora del riposo e' piu' grande di quella della Home e sottile, come
     *  sulla schermata di blocco dell'iPad: qui e' l'unica cosa da leggere. */
    private static final float ORA_GRANDE = 1.3f;

    /** Quanto scurisce il velo della notte, per le quattro fasce di
     *  {@link Sfondo}: alba, giorno, sera, notte. */
    private static final int[] VELO_ORARIO = { 0x22, 0x00, 0x3C, 0xAA };

    private final Misure m;
    private final Paesaggi paesaggi;
    private final Fine fine;

    private final Paint pFoto  = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint pVelo  = new Paint();
    private final Paint pOra   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pData  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSegno = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Testo.Riga rLuogo   = new Testo.Riga();
    private final Testo.Riga rAutore  = new Testo.Riga();
    private final Testo.Riga rQuando  = new Testo.Riga();
    private final Testo.Riga rImpegno = new Testo.Riga();
    private final Testo.Riga[] rNote  = { new Testo.Riga(), new Testo.Riga(), new Testo.Riga() };
    private final android.graphics.RectF appoggio = new android.graphics.RectF();
    private final Calendar cal = Calendar.getInstance();
    private final Handler battito = new Handler(Looper.getMainLooper());

    /** Ogni quanti minuti cambia la foto. Zero vuol dire "non cambiarla". */
    private final int minutiFoto;

    private Meteo meteo;
    private Appunti appunti;
    private Calendario calendario;
    private Notizie notizie;

    /**
     * Che cosa sta nell'angolo in alto: l'agenda o le notizie.
     *
     * <b>Si alternano, non si sommano.</b> Due impegni, tre note e tre titoli
     * uno sotto l'altro sarebbero mezza fotografia coperta da tessere scure, e
     * una schermata che si guarda passando non si legge a colonne. Ogni venti
     * secondi una cede il posto all'altra: esce sfumando verso l'alto, e la
     * nuova entra con le stesse tessere sfasate con cui entra quando cambia la
     * fotografia.
     *
     * <b>Costa fotogrammi solo mentre succede</b>: mezzo secondo per uscire e
     * mezzo per entrare, ogni venti. Nel resto del tempo questa schermata resta
     * a un fotogramma al minuto. Se c'e' solo l'agenda non si alterna niente;
     * se ci sono solo le notizie, si gira pagina fra i titoli.
     */
    private static final int AGENDA = 0, GIORNALE = 1;
    private int blocco = AGENDA;
    private static final long ALTERNANZA = 20000L;
    private static final int USCITA = 520;
    private long uscita;

    /** Tre titoli per volta, e un giro sui primi nove: le principali sono
     *  una dozzina, e le ultime sono gia' le notizie di ieri. */
    private static final int QUANTE_NOTIZIE = 3, GIRO_NOTIZIE = 9;
    private final String[] nTitolo = new String[QUANTE_NOTIZIE];
    private final String[] nFonte = new String[QUANTE_NOTIZIE];
    private final float[] altaNotizia = new float[QUANTE_NOTIZIE];
    private final Testo.Blocco[] bNotizia = {
        new Testo.Blocco(), new Testo.Blocco(), new Testo.Blocco() };
    private final Testo.Riga[] rFonte = { new Testo.Riga(), new Testo.Riga(), new Testo.Riga() };
    private int quanteNotizie, giroNotizie;
    private final Paint pEtich = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Runnable alterna = new Runnable() {
        @Override public void run() {
            if (tolta) return;
            if (ceGiornale() && (ceAgenda() || blocco == GIORNALE)) {
                uscita = Anima.ora();
                invalidate();
            }
            battito.postDelayed(this, ALTERNANZA);
        }
    };

    /**
     * Quando sono entrate in scena le tessere dell'agenda.
     *
     * <b>E' tutta l'animazione che c'e', ed e' gratis.</b> Le tessere entrano
     * scorrendo da destra, sfasate di un soffio l'una dall'altra, e poi stanno
     * ferme: i fotogrammi che servono sono gli stessi che la dissolvenza fra
     * due fotografie chiede gia'. Fuori da quei momenti questa schermata
     * disegna <b>un fotogramma al minuto</b>, e le tessere non lo cambiano.
     */
    private long ingresso;

    private String testoOra = "", testoData = "";
    private long dissolvenza;           // quando e' cominciata, 0 = nessuna
    private float avanzamento = 1f;     // a che punto e', da 0 a 1
    private long ultimoCambio;
    private boolean tolta;

    /** La sfumatura sotto il testo e il fondale di quando non c'e' nessuna
     *  foto: due shader, fatti una volta in onSizeChanged. */
    private Shader sfumatura, fondale;

    public VelaRiposo(Context c, Misure misure, Paesaggi paesaggi, int minutiFoto, Fine fine) {
        super(c);
        this.m = misure;
        this.paesaggi = paesaggi;
        this.minutiFoto = minutiFoto;
        this.fine = fine;

        // Senza clickable Android non consegna ACTION_DOWN, e senza il DOWN non
        // arriva mai niente: la View sembrerebbe morta al tocco.
        setClickable(true);

        pOra.setColor(Tinte.TESTO);
        pOra.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        pOra.setTextAlign(Paint.Align.LEFT);
        pOra.setLetterSpacing(STRETTA_ORA);
        pData.setColor(Tinte.TESTO);
        pData.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pData.setTextAlign(Paint.Align.LEFT);
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pNota.setTextAlign(Paint.Align.RIGHT);
        pSegno.setStyle(Paint.Style.FILL);

        // L'ombra sotto le scritte, che si vede solo dove la foto e' chiara.
        // Sta sui Paint del testo e non sulle figure: con il disegno
        // accelerato lo shadowLayer e' garantito sul testo, non sul resto.
        float raggio = Math.max(2f, misure.altezza * 0.006f);
        pOra.setShadowLayer(raggio, 0f, raggio * 0.3f, 0xB0000000);
        pData.setShadowLayer(raggio * 0.7f, 0f, raggio * 0.25f, 0xA0000000);
        pNota.setShadowLayer(raggio * 0.7f, 0f, raggio * 0.25f, 0xA0000000);

        aggiornaOra();
        ingresso = Anima.ora();
        ultimoCambio = Anima.ora();
        paesaggi.setAvviso(new Paesaggi.Pronta() {
            @Override public void fotoPronta() { fotoCambiata(); }
        });
        paesaggi.apri();
        programmaIlMinuto();

        pEtich.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pEtich.setTextAlign(Paint.Align.LEFT);
        pEtich.setShadowLayer(raggio * 0.7f, 0f, raggio * 0.25f, 0xA0000000);
        battito.postDelayed(alterna, ALTERNANZA);
    }

    /** Il tempo che fa, se Casa ce l'ha: sotto la data, come sulla schermata di
     *  blocco di Windows. Se il meteo non e' mai arrivato, la riga non c'e' -
     *  non c'e' un trattino al posto di un numero. */
    public void setMeteo(Meteo meteo) { this.meteo = meteo; }

    /** Le notizie principali, che si alternano all'agenda. */
    public void setNotizie(Notizie n) { notizie = n; }

    /** Le note da fare e i prossimi impegni. */
    public void setAgenda(Appunti a, Calendario c) {
        appunti = a;
        calendario = c;
        rifaiIngresso();
    }

    /** Le tessere rientrano in scena. La chiama chi aggiunge una nota mentre
     *  lo schermo e' a riposo. */
    public void rifaiIngresso() {
        if (tolta) return;
        ingresso = Anima.ora();
        invalidate();
    }

    // ---- vela --------------------------------------------------------------

    /** Il vetro non serve: qui il fondale e' una fotografia, e un pannello
     *  smerigliato sopra sarebbe una finestra sopra una finestra. */
    @Override public void setVetro(Vetro v) { }

    @Override
    public void suVelaTolta() {
        if (tolta) return;
        tolta = true;
        battito.removeCallbacksAndMessages(null);
        paesaggi.setAvviso(null);
        // Le due bitmap se ne vanno adesso: quattro megabyte tenuti da una
        // schermata che non e' piu' sullo schermo sono quattro megabyte tolti
        // a chi lo schermo ce l'ha.
        paesaggi.chiudi();
        if (fine != null) fine.suUscita();
    }

    @Override
    public boolean hasOverlappingRendering() { return false; }

    // ---- il tempo ----------------------------------------------------------

    /**
     * Si sveglia sul minuto pieno.
     *
     * Non ogni sessanta secondi da adesso: sul <b>bordo</b> del minuto, come
     * l'orologio della Home. Un orologio che cambia numero a meta' minuto e'
     * sbagliato di trenta secondi in media, e su una schermata dove l'ora e'
     * l'unica cosa scritta si nota.
     */
    private void programmaIlMinuto() {
        long adesso = System.currentTimeMillis();
        long alProssimo = 60000L - (adesso % 60000L) + 200L;
        battito.postDelayed(new Runnable() {
            @Override public void run() {
                if (tolta) return;
                if (aggiornaOra()) invalidate();
                if (minutiFoto > 0
                        && Anima.ora() - ultimoCambio >= minutiFoto * 60000L) {
                    ultimoCambio = Anima.ora();
                    paesaggi.avanti();
                }
                programmaIlMinuto();
            }
        }, alProssimo);
    }

    private boolean aggiornaOra() {
        cal.setTimeInMillis(System.currentTimeMillis());
        String ora = String.format(Locale.ITALIAN, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        String data = String.format(Locale.ITALIAN, "%s %d %s",
                GIORNI[cal.get(Calendar.DAY_OF_WEEK) - 1],
                cal.get(Calendar.DAY_OF_MONTH),
                MESI[cal.get(Calendar.MONTH)]);
        boolean cambiato = !ora.equals(testoOra) || !data.equals(testoData);
        testoOra = ora;
        testoData = data;
        return cambiato;
    }

    /** E' arrivata una foto nuova: comincia la dissolvenza. Vale anche per la
     *  prima, che sale sopra il fondale invece di comparire di colpo. */
    private void fotoCambiata() {
        if (tolta) return;
        // Anche la prima: sale sopra il fondale invece di comparire di colpo.
        dissolvenza = Anima.ora();
        ultimoCambio = dissolvenza;
        // E le tessere rientrano insieme alla fotografia nuova: e' l'unico
        // momento in cui questa schermata si muove, e muoversi tutto insieme
        // costa gli stessi fotogrammi.
        ingresso = dissolvenza;
        invalidate();
    }

    // ---- disegno -----------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        pOra.setTextSize(m.cifra * ORA_GRANDE);
        pData.setTextSize(m.voce);
        pNota.setTextSize(m.nota);

        // La sfumatura parte poco sopra l'ora: piu' in alto si vedrebbe come
        // una macchia sul cielo della fotografia, piu' in basso non
        // coprirebbe l'ora, che e' alta un quinto dello schermo.
        //
        // <b>I numeri vengono da una foto chiara.</b> I primi erano stati
        // scelti su un tramonto, dove il fondo e' scuro di suo e qualunque
        // velo basta; su un prato verde in pieno sole la data e il credito si
        // leggevano a fatica. Le fotografie del giorno di Bing sono meta' e
        // meta', quindi il velo va tarato sulla peggiore delle due.
        sfumatura = new LinearGradient(0, h * 0.52f, 0, h,
                new int[] { 0x00000000, 0x66000000, 0xD4000000 },
                new float[] { 0f, 0.58f, 1f }, Shader.TileMode.CLAMP);
        fondale = new LinearGradient(0, 0, 0, h,
                Tinte.FONDO_ALTO, Tinte.FONDO_BASSO, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        boolean ceLaFoto = disegnaFondo(c, w, h);

        // Il velo della notte: sopra la foto, sotto le scritte. Cosi' l'ora
        // resta bianca e leggibile mentre la fotografia si spegne.
        int velo = VELO_ORARIO[Sfondo.variante(System.currentTimeMillis())];
        if (velo > 0) {
            pVelo.setShader(null);
            pVelo.setColor(Tinte.con(0xFF000000, velo));
            c.drawRect(0, 0, w, h, pVelo);
        }

        if (ceLaFoto && sfumatura != null) {
            pVelo.setShader(sfumatura);
            pVelo.setColor(0xFFFFFFFF);         // il colore lo mette lo shader
            c.drawRect(0, h * 0.52f, w, h, pVelo);
            pVelo.setShader(null);
        }

        disegnaOrologio(c, h);
        disegnaDidascalia(c, w, h);
        disegnaAngolo(c, w, h);
    }

    /**
     * L'angolo in alto a destra: l'agenda o le notizie, e il passaggio fra le
     * due. Vedi {@link #blocco}.
     */
    private void disegnaAngolo(Canvas c, int w, int h) {
        boolean agenda = ceAgenda(), giornale = ceGiornale();
        if (uscita == 0L) {
            // Una delle due puo' mancare, o arrivare dopo: le notizie non ci
            // sono ancora al primo minuto dopo l'accensione, e l'ultima nota si
            // spunta anche mentre il riposo e' in scena.
            if (blocco == AGENDA && !agenda && giornale) {
                blocco = GIORNALE;
                prossimeNotizie();
                ingresso = Anima.ora();
            } else if (blocco == GIORNALE && !giornale) {
                blocco = AGENDA;
                ingresso = Anima.ora();
            } else if (blocco == GIORNALE && quanteNotizie == 0) {
                prossimeNotizie();
            }
        }

        float spegni = 1f;
        if (uscita > 0L) {
            long t = Anima.ora() - uscita;
            if (t >= USCITA) {
                uscita = 0L;
                if (blocco == GIORNALE && agenda) {
                    blocco = AGENDA;
                } else if (giornale) {
                    blocco = GIORNALE;
                    prossimeNotizie();
                }
                // La nuova entra come entra tutto qui: tessere sfasate che
                // scorrono da destra e si accendono.
                ingresso = Anima.ora();
            } else {
                spegni = 1f - Anima.posa(t / (float) USCITA);
                postInvalidateOnAnimation();
            }
        }

        int s = c.save();
        // Uscendo sale di poco mentre sfuma: una dissolvenza ferma si legge
        // come una lampadina che si spegne, una che si alza come una pagina
        // che si gira.
        if (spegni < 1f) c.translate(0f, -(1f - spegni) * m.s4);
        if (blocco == GIORNALE) disegnaNotizie(c, w, spegni);
        else disegnaAgenda(c, w, h, spegni);
        c.restoreToCount(s);
    }

    private boolean ceAgenda() {
        return (calendario != null && calendario.prossimi().length > 0)
            || (appunti != null && appunti.quanteDaFare() > 0);
    }

    private boolean ceGiornale() {
        return notizie != null && notizie.principali().length > 0;
    }

    /**
     * I prossimi tre titoli del giro.
     *
     * Alloca - le scritte, e l'impaginazione dei titoli su piu' righe - ma
     * succede una volta ogni quaranta secondi, non a ogni fotogramma.
     */
    private void prossimeNotizie() {
        quanteNotizie = 0;
        if (notizie == null || getWidth() == 0) return;
        Notizie.Titolo[] t = notizie.principali();
        int giro = Math.min(t.length, GIRO_NOTIZIE);
        if (giro == 0) return;
        int primo = (giroNotizie * QUANTE_NOTIZIE) % giro;
        giroNotizie++;
        long adesso = System.currentTimeMillis();
        float dentro = getWidth() * 0.34f - m.s3 * 2f;
        for (int k = 0; k < QUANTE_NOTIZIE && primo + k < giro; k++) {
            Notizie.Titolo n = t[primo + k];
            nTitolo[k] = n.testo;
            String quando = Notizie.fa(n.quando, adesso);
            nFonte[k] = n.fonte.length() == 0 ? quando
                      : quando.length() == 0 ? n.fonte : n.fonte + "  ·  " + quando;
            int righe = bNotizia[k].in(pData, n.testo, dentro, m.corpo, 3).length;
            altaNotizia[k] = m.s2 + m.nota + m.s1 + righe * m.corpo * 1.2f + m.s2;
            quanteNotizie = k + 1;
        }
    }

    /**
     * Le notizie principali, nello stesso angolo e con le stesse tessere
     * dell'agenda: una schermata sola, un modo solo di mettere le cose.
     */
    private void disegnaNotizie(Canvas c, int w, float spegni) {
        if (quanteNotizie == 0) return;
        float largo = w * 0.34f;
        float destra = w - m.margine * 2f;
        float sinistra = destra - largo;
        float y = m.margine * 1.6f;

        float a0 = ingresso(0);
        int s0 = c.save();
        c.translate((1f - a0) * w * 0.05f, 0f);
        float lato = m.micro * 1.3f;
        pSegno.setStyle(Paint.Style.FILL);
        pSegno.setColor(Tinte.con(Tinte.NOTIZIE, Math.round(255 * a0 * spegni)));
        Icone.disegna(c, Icone.NOTIZIE, sinistra + lato / 2f, y + m.micro * 0.64f, lato, pSegno);
        pEtich.setTextSize(m.nota);
        pEtich.setColor(Tinte.con(Tinte.TESTO_MEDIO, Math.round(230 * a0 * spegni)));
        c.drawText("Notizie", sinistra + lato + m.s2, y + m.micro, pEtich);
        c.restoreToCount(s0);
        y += m.micro + m.s3;

        for (int i = 0; i < quanteNotizie; i++) {
            float avanti = ingresso(i + 1);
            if (avanti <= 0f) continue;
            float alta = altaNotizia[i];
            tessera(c, sinistra, y, destra, y + alta, avanti, spegni);

            s0 = c.save();
            c.translate((1f - avanti) * w * 0.05f, 0f);
            float x = sinistra + m.s3, dentro = destra - m.s3 - x;
            int alfa = Math.round(255 * avanti * spegni);
            pNota.setTextAlign(Paint.Align.LEFT);
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.con(Tinte.NOTIZIE, alfa));
            c.drawText(rFonte[i].in(pNota, nFonte[i], dentro, m.nota), x, y + m.s2 + m.nota, pNota);

            String[] righe = bNotizia[i].in(pData, nTitolo[i], dentro, m.corpo, 3);
            pData.setTextAlign(Paint.Align.LEFT);
            pData.setColor(Tinte.con(Tinte.TESTO, alfa));
            float yT = y + m.s2 + m.nota + m.s1 + m.corpo;
            for (int k = 0; k < righe.length; k++) {
                c.drawText(righe[k], x, yT + k * m.corpo * 1.2f, pData);
            }
            c.restoreToCount(s0);
            y += alta + m.s2;
        }

        if (Anima.inCorso(ingresso, quanteNotizie + 1)) postInvalidateOnAnimation();
    }

    /**
     * Le tessere dell'agenda, in alto a destra.
     *
     * Prima gli impegni, poi le cose da fare: quello che ha un'ora viene prima
     * di quello che si puo' fare quando capita.
     *
     * <b>Ogni tessera ha il suo fondo scuro</b> e non si appoggia alla
     * sfumatura come l'ora: la sfumatura sta in basso, e qui sopra c'e' la
     * fotografia intera - che puo' essere un cielo bianco. Un rettangolo
     * arrotondato a mezza opacita' costa una chiamata e rende leggibile
     * qualunque cosa ci sia sotto.
     *
     * <b>Quante.</b> Due impegni e tre note, non di piu': questa e' una
     * schermata che si guarda da lontano passando, non una lista da leggere. Se
     * ce ne sono altre lo dice l'ultima riga, con un numero.
     */
    private void disegnaAgenda(Canvas c, int w, int h, float spegni) {
        Calendario.Impegno[] impegni = calendario == null
                ? new Calendario.Impegno[0] : calendario.prossimi();
        java.util.List<Appunti.Nota> note = appunti == null
                ? null : appunti.daFare();

        int quantiImpegni = Math.min(2, impegni.length);
        int quanteNote = note == null ? 0 : Math.min(3, note.size());
        if (quantiImpegni == 0 && quanteNote == 0) return;

        float largo = w * 0.34f;
        float destra = w - m.margine * 2f;
        float sinistra = destra - largo;
        float y = m.margine * 1.6f;
        int quale = 0;

        for (int i = 0; i < quantiImpegni; i++) {
            Calendario.Impegno im = impegni[i];
            float avanti = ingresso(quale++);
            if (avanti <= 0f) continue;
            float alta = m.voce * 2.35f;
            tessera(c, sinistra, y, destra, y + alta, avanti, spegni);

            int s0 = c.save();
            c.translate((1f - avanti) * w * 0.05f, 0f);
            float x = sinistra + m.s3;
            float dentro = destra - m.s3 - x;

            pNota.setTextAlign(Paint.Align.LEFT);
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.con(im.adesso() ? Tinte.AGENDA : Tinte.TESTO_MEDIO,
                                     Math.round(255 * avanti * spegni)));
            c.drawText(rQuando.in(pNota, Calendario.quando(im), dentro, m.nota),
                       x, y + m.nota * 1.5f, pNota);

            pData.setTextAlign(Paint.Align.LEFT);
            pData.setTextSize(m.corpo);
            pData.setColor(Tinte.con(Tinte.TESTO, Math.round(255 * avanti * spegni)));
            c.drawText(rImpegno.adatta(pData, im.titolo, dentro, m.corpo, m.corpo * 0.8f),
                       x, y + m.nota * 1.5f + m.corpo * 1.25f, pData);
            c.restoreToCount(s0);

            y += alta + m.s2;
        }

        for (int i = 0; i < quanteNote; i++) {
            Appunti.Nota n = note.get(i);
            float avanti = ingresso(quale++);
            if (avanti <= 0f) continue;
            float alta = m.corpo * 2.1f;
            tessera(c, sinistra, y, destra, y + alta, avanti, spegni);

            int s0 = c.save();
            c.translate((1f - avanti) * w * 0.05f, 0f);
            float cx = sinistra + m.s3 + m.corpo * 0.3f;
            pSegno.setStyle(Paint.Style.STROKE);
            pSegno.setStrokeWidth(Math.max(2f, m.corpo * 0.1f));
            pSegno.setColor(Tinte.con(Tinte.AGENDA, Math.round(255 * avanti * spegni)));
            c.drawCircle(cx, y + alta / 2f, m.corpo * 0.3f, pSegno);

            float x = cx + m.corpo * 0.3f + m.s2;
            pData.setTextAlign(Paint.Align.LEFT);
            pData.setTextSize(m.corpo);
            pData.setColor(Tinte.con(Tinte.TESTO, Math.round(255 * avanti * spegni)));
            c.drawText(rNote[i].adatta(pData, n.testo, destra - m.s3 - x, m.corpo, m.corpo * 0.78f),
                       x, y + alta / 2f - (pData.descent() + pData.ascent()) / 2f, pData);
            c.restoreToCount(s0);

            y += alta + m.s1;
        }

        int restano = (note == null ? 0 : note.size()) - quanteNote;
        if (restano > 0) {
            float avanti = ingresso(quale);
            pNota.setTextAlign(Paint.Align.RIGHT);
            pNota.setTextSize(m.micro);
            pNota.setColor(Tinte.con(Tinte.TESTO_MEDIO, Math.round(200 * avanti * spegni)));
            c.drawText("e altre " + restano, destra, y + m.micro * 1.2f, pNota);
        }

        // Finche' le tessere entrano servono fotogrammi; dopo, nessuno.
        if (Anima.inCorso(ingresso, quale + 1)) postInvalidateOnAnimation();
    }

    /** A che punto e' l'ingresso della tessera numero i. */
    private float ingresso(int i) {
        return Anima.posa(Anima.entrata(ingresso, i));
    }

    /** Il fondo di una tessera: scuro, arrotondato, e nient'altro. */
    private void tessera(Canvas c, float sinistra, float alto, float destra, float basso,
                         float avanti, float spegni) {
        appoggio.set(sinistra + (1f - avanti) * (destra - sinistra) * 0.12f, alto, destra, basso);
        pVelo.setShader(null);
        pVelo.setColor(Tinte.con(0xFF000000, Math.round(0x6E * avanti * spegni)));
        c.drawRoundRect(appoggio, m.raggio, m.raggio, pVelo);
    }

    /**
     * La fotografia, o il fondale se non ce n'e' ancora nessuna.
     *
     * Sotto c'e' sempre qualcosa - la foto di prima, o il fondale generato -
     * e quella nuova le sale sopra. Non si incrociano le due opacita': a meta'
     * strada darebbero un istante slavato, che fra due fotografie si vede molto
     * piu' che fra due schermate. E' la stessa scelta del {@link Telaio} fra le
     * sezioni.
     *
     * Anche la prima foto entra cosi', salendo sopra il fondale invece di
     * comparire di colpo: e' la stessa dissolvenza, e non costa una riga in
     * piu'.
     */
    private boolean disegnaFondo(Canvas c, int w, int h) {
        Paesaggi.Foto ora = paesaggi.corrente();
        Paesaggi.Foto prima = paesaggi.uscente();

        avanzamento = 1f;
        if (dissolvenza > 0) {
            avanzamento = Math.min(1f, (Anima.ora() - dissolvenza) / (float) DISSOLVENZA);
        }

        if (prima != null && prima.viva()) {
            pFoto.setAlpha(255);
            c.drawBitmap(prima.immagine, 0, 0, pFoto);
        } else {
            // Il fondale generato a codice, come lo sfondo di Casa: e' quello
            // che si vede il primo minuto dopo un'installazione, e tutte le
            // sere in cui la rete non va e nessuno ha mai messo una foto sua.
            // Un orologio grande su un fondo scuro non e' un ripiego brutto.
            pVelo.setColor(Tinte.FONDO);
            if (fondale != null) { pVelo.setShader(fondale); pVelo.setColor(0xFFFFFFFF); }
            c.drawRect(0, 0, w, h, pVelo);
            pVelo.setShader(null);
        }

        boolean ceLaFoto = ora != null && ora.viva();
        if (ceLaFoto) {
            pFoto.setAlpha(Math.round(255 * Anima.posa(avanzamento)));
            c.drawBitmap(ora.immagine, 0, 0, pFoto);
            pFoto.setAlpha(255);
        }

        if (dissolvenza > 0) {
            if (avanzamento >= 1f) {
                dissolvenza = 0;
                // Adesso, e non prima: questo e' l'ultimo fotogramma in cui la
                // vecchia e' stata disegnata, e riciclare una bitmap ancora in
                // uso e' uno schermo nero senza spiegazione.
                paesaggi.dimenticaUscente();
            } else {
                postInvalidateOnAnimation();
            }
        }
        return ceLaFoto;
    }

    /** L'ora grande, la data, e il tempo che fa: appoggiate all'angolo in
     *  basso a sinistra, dove non coprono la fotografia. */
    private void disegnaOrologio(Canvas c, int h) {
        float x = m.margine * 2f;
        float y = h - m.margine * 1.6f;

        boolean ceIlMeteo = meteo != null && meteo.adesso() != null;
        if (ceIlMeteo) y -= m.voce * 1.5f;

        pData.setTextAlign(Paint.Align.LEFT);
        pData.setTextSize(m.voce);
        pData.setColor(Tinte.TESTO_MEDIO);
        c.drawText(testoData, x, y, pData);

        pOra.setTextSize(m.cifra * ORA_GRANDE);
        pOra.setColor(Tinte.TESTO);
        // Il gambo delle cifre e' tutto sopra la linea di base: si appoggia
        // l'ora alla data con l'interlinea giusta invece di centrare un blocco
        // che nessuno vede.
        c.drawText(testoOra, x, y - m.voce - m.s2, pOra);

        if (!ceIlMeteo) return;
        Meteo.Adesso a = meteo.adesso();
        float yM = h - m.margine * 1.6f;
        float lato = m.icona;
        pSegno.setColor(Cielo.tinta(a.codice, a.giorno));
        Icone.disegna(c, Cielo.icona(a.codice, a.giorno), x + lato / 2f, yM - m.voce * 0.32f,
                lato, pSegno);

        pData.setTextSize(m.corpo);
        pData.setColor(Tinte.TESTO_MEDIO);
        String riga = String.format(Locale.ITALIAN, "%.0f°", a.gradi);
        String tempo = Cielo.nome(a.codice);
        if (tempo != null && tempo.length() > 0) riga = riga + "  ·  " + tempo;
        c.drawText(riga, x + lato * 1.35f, yM, pData);
    }

    /**
     * Dove e' stata scattata, e da chi. In basso a destra, piccola.
     *
     * Il credito c'e' perche' e' di qualcuno: una fotografia presa
     * dall'archivio di Bing porta il nome di chi l'ha fatta, e toglierlo per
     * fare piu' pulito sarebbe togliere l'unica cosa che quella riga deve
     * dire. Le foto di casa non ne hanno, e la riga semplicemente non c'e'.
     */
    private void disegnaDidascalia(Canvas c, int w, int h) {
        Paesaggi.Foto f = paesaggi.corrente();
        if (f == null) return;
        float destra = w - m.margine * 2f;
        // Piu' di meta' schermo, misurato sul tablet: i posti di Bing hanno
        // nomi lunghi - « Coyote Buttes, Monumento Nazionale Vermilion Cliffs,
        // Arizona » - e con meno di cosi' finivano tutti nei puntini. A destra
        // lo spazio c'e': l'ora e la data, dall'altra parte, arrivano a poco
        // piu' di un terzo.
        float largo = w * 0.55f;
        float y = h - m.margine * 1.6f;

        // La didascalia entra insieme alla sua fotografia: scritta com'e'
        // scritto il posto della foto precedente, per novecento millisecondi,
        // sarebbe l'unica cosa sbagliata sullo schermo.
        int quanto = Math.round(255 * avanzamento);

        pNota.setTextAlign(Paint.Align.RIGHT);
        if (f.autore != null) {
            pNota.setTextSize(m.micro);
            pNota.setColor(Tinte.con(Tinte.TESTO_TENUE, quanto));
            c.drawText(rAutore.in(pNota, f.autore, largo, m.micro), destra, y, pNota);
            y -= m.micro * 1.6f;
        }
        if (f.luogo != null) {
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.con(Tinte.TESTO_MEDIO, quanto));
            c.drawText(rLuogo.in(pNota, f.luogo, largo, m.nota), destra, y, pNota);
        }
    }

    // ---- il tocco ----------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Si vede subito che il tocco e' arrivato: la Home sotto, e la
                // fotografia che svanisce sopra.
                if (fine != null) fine.suPosato();
                animate().alpha(0f).setDuration(160).start();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (fine != null) fine.suTocco();
                return true;
            default:
                // Anche gli spostamenti: nessun pezzo di questo gesto deve
                // arrivare a quel che c'e' sotto.
                return true;
        }
    }
}
