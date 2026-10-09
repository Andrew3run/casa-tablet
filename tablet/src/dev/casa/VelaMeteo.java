package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * La pagina del meteo: com'e' adesso, le prossime ventiquattro ore, i sette
 * giorni, e i luoghi.
 *
 * <h3>Perche' una vela e non una settima sezione</h3>
 *
 * La barra ha sei voci e sono quelle che si toccano ogni giorno. Il meteo si
 * guarda una volta la mattina: nella Home ha la sua scheda - che e' quello che
 * si vede senza toccare niente - e questa e' la pagina intera, che si apre dalla
 * sezione App o toccando quella scheda. Una settima voce fissa nella barra
 * costerebbe a tutte le altre un settimo di bersaglio per una schermata che si
 * apre di rado.
 *
 * Sopra il telaio e non in un'altra Activity, per la stessa ragione della
 * sveglia e delle impostazioni: col lock task attivo lanciare una seconda
 * Activity e' una scommessa che qui non serve fare (vedi {@link Telaio#mostra}).
 *
 * <h3>Qui le ore sono ore</h3>
 *
 * La Home mostra <b>le parti della giornata</b> - com'e' il pomeriggio, com'e'
 * la sera - perche' e' la domanda di chi passa davanti al tablet. Qui invece ci
 * sono tutte e ventiquattro le ore, su due righe da dodici: la domanda di chi
 * apre questa pagina e' « a che ora smette », e a quella le fasce non
 * rispondono.
 *
 * <h3>Le scritte si compongono quando cambia il dato</h3>
 *
 * Questa schermata si ridisegna <b>a fotogramma pieno</b> - il cielo si muove,
 * ed e' l'unica cosa che si sta guardando - quindi vale piu' che altrove la
 * regola di non allocare dentro {@code onDraw}. Tutte le stringhe si compongono
 * in {@link #componi()}, che gira quando arriva roba nuova: una volta ogni
 * mezz'ora, non sessanta volte al secondo.
 *
 * E <b>ogni scritta passa da una {@link Testo.Riga}</b>, anche « 2 km/h » in una
 * tessera che sembra larga abbastanza. La prima versione ne dava per scontate
 * quattro - i valori e le etichette dei dettagli - e « TRAMONTO » usciva dalla
 * sua tessera: e' esattamente lo sbaglio che {@link Testo} esiste per non far
 * rifare.
 */
public class VelaMeteo extends View implements Telaio.Velata {

    public interface Uscita { void suChiudi(); }

    /**
     * Quante ore possono arrivare - ventiquattro passate e ventiquattro future
     * - e quante ne mostra una fascia.
     *
     * Le passate ci sono apposta: la giornata si guarda intera, e alle dieci di
     * mattina meta' e' gia' successa. Si disegnano smorte, ma ci sono.
     */
    private static final int ORE = 48, ORE_PER_FASCIA = 6;

    /** Quante linguette: notte, mattino, pomeriggio, sera. */
    private static final int FASCE = 4;

    /** I quattro dettagli sotto i gradi: quanto e' umido, quanto tira vento, se
     *  piove, quando fa buio. */
    private static final int DETTAGLI = 4;
    /** Le tessere dei dettagli: un grado piu' chiare della scheda. */
    private static final int TESSERA = 0x14FFFFFF;

    private static final String[] ETICHETTA = { "Umidità", "Vento", "Pioggia", "Tramonto" };

    /**
     * Quante righe di luoghi ci stanno nel pannello.
     *
     * Cinque e non sei. Con sei la riga veniva alta cinquantadue pixel per un
     * contenuto che ne vuole cinquantasette - il nome sopra e la regione sotto -
     * e le due scritte si toccavano. Il numero non si indovina: si conta quello
     * che ci deve stare, e se non ci sta si tolgono le righe, non i pixel al
     * testo.
     */
    private static final int RIGHE_LUOGHI = 5;

    private final Misure m;
    private final Meteo meteo;
    private final Uscita uscita;
    private final Cielo cielo = new Cielo();

    private Vetro vetro;
    private Runnable suChiusa;

    private final Paint pVelo   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitolo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEtich  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCorpo  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGradi  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIcona  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPieno  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF pAdesso = new RectF(), pOre = new RectF(), pGiorni = new RectF();
    private final RectF scena = new RectF();
    private final RectF rChiudi = new RectF(), rAggiorna = new RectF(), rCitta = new RectF();
    private final RectF[] tessOra = new RectF[ORE_PER_FASCIA];
    private final RectF[] rLinguetta = new RectF[FASCE];
    private final RectF[] tessDett = new RectF[DETTAGLI];
    private final RectF appoggio = new RectF();
    private final android.graphics.Rect riquadroScena = new android.graphics.Rect();

    private final Testo.Riga rCittaRiga = new Testo.Riga();
    private final Testo.Riga rCond  = new Testo.Riga();
    private final Testo.Riga rGradi = new Testo.Riga();
    private final Testo.Riga rNota  = new Testo.Riga();
    private final Testo.Riga[] rValore    = new Testo.Riga[DETTAGLI];
    private final Testo.Riga[] rEtichetta = new Testo.Riga[DETTAGLI];
    private final Testo.Riga[] rLing      = new Testo.Riga[FASCE];

    /** Le fasce della giornata, e quale si sta guardando. */
    private Meteo.Fascia[] fasce = new Meteo.Fascia[0];
    private int fasciaScelta, premutaLinguetta = -1;
    /** true da quando qualcuno ha scelto una linguetta con il dito: da li' in
     *  poi non si sposta piu' da sola quando arrivano dati nuovi. */
    private boolean sceltaDaMano;
    private float bandaOre;

    private float cimaGiorni, altaRigaGiorno;
    private float minSettimana, maxSettimana;

    private boolean premutoChiudi, premutoAggiorna, premutoCitta;

    // ---- le scritte, gia' composte -------------------------------------------
    private String sCitta = "", sGradi = "", sCondizione = "", sPercepiti = "";
    private final String[] sDettaglio = new String[DETTAGLI];
    private final String[] sOraQuando  = new String[ORE];
    private final String[] sOraGradi   = new String[ORE];
    private final String[] sOraPioggia = new String[ORE];
    private final String[] sOraUmidita = new String[ORE];
    private final String[] sOraVento   = new String[ORE];
    private String[] sGiornoNome = new String[0];
    private String[] sGiornoMin  = new String[0];
    private String[] sGiornoMax  = new String[0];
    private String[] sGiornoPioggia = new String[0];

    private final PannelloLuoghi luoghi = new PannelloLuoghi();

    public VelaMeteo(Context c, Misure misure, Vetro v, Meteo mt, Uscita u) {
        super(c);
        this.m = misure;
        this.vetro = v;
        this.meteo = mt;
        this.uscita = u;

        setClickable(true);
        for (int i = 0; i < ORE_PER_FASCIA; i++) tessOra[i] = new RectF();
        for (int i = 0; i < FASCE; i++) {
            rLinguetta[i] = new RectF();
            rLing[i] = new Testo.Riga();
        }
        for (int i = 0; i < DETTAGLI; i++) {
            tessDett[i] = new RectF();
            rValore[i] = new Testo.Riga();
            rEtichetta[i] = new Testo.Riga();
            sDettaglio[i] = "-";
        }

        // Quasi pieno: dietro non si deve leggere niente. E' lo stesso velo
        // delle impostazioni - due schermate sovrapposte si leggono peggio di
        // una sola.
        pVelo.setColor(0xF8000000);
        pTitolo.setColor(Tinte.TESTO);
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pEtich.setColor(Tinte.TESTO_TENUE);
        pEtich.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pCorpo.setColor(Tinte.TESTO);
        pCorpo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pNota.setColor(Tinte.TESTO_TENUE);
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        // Il numero grosso e' sottile: a quel corpo un carattere normale e' un
        // muro, e sottile si legge da piu' lontano perche' le forme restano
        // aperte.
        pGradi.setColor(Tinte.TESTO);
        pGradi.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

        componi();
    }

    @Override public void setVetro(Vetro v) { vetro = v; invalidate(); }

    @Override public boolean hasOverlappingRendering() { return false; }

    public void setSuChiusa(Runnable r) { suChiusa = r; }

    /** Aprendola si chiede il tempo, se quello che si ha non e' piu' fresco. */
    public void suEntrata() {
        if (meteo != null) meteo.aggiorna();
        componi();
        invalidate();
    }

    @Override public void suVelaTolta() {
        removeCallbacks(luoghi.fraPoco);
        if (suChiusa != null) suChiusa.run();
    }

    /** Chiamata da fuori quando arriva roba nuova. */
    public void meteoCambiato() {
        componi();
        requestLayout();     // il nome della citta' decide la larghezza della sua pastiglia
        invalidate();
    }

    // ---- misure ---------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        misura(w, h);
    }

    private void misura(int w, int h) {
        if (w <= 0 || h <= 0) return;
        float mg = m.margine;

        pTitolo.setTextSize(m.titolo);
        pEtich.setTextSize(m.micro);
        pCorpo.setTextSize(m.corpo);
        pNota.setTextSize(m.nota);

        // <b>Una riga sola per l'intestazione</b>: il titolo, la pastiglia
        // della citta' e i due tasti stanno tutti alla stessa altezza.
        //
        // Prima la pastiglia stava sotto il titolo, staccata di un filo: due
        // cose incolonnate a sei pixel l'una dall'altra e allineate allo stesso
        // margine si leggono come una scritta sola andata a capo male, non come
        // un titolo e un comando. Di fianco, e a un blocco di distanza, sono
        // due cose - e la fascia intera costa meno alta di quanto costavano le
        // due righe.
        float altaTesta = m.bersaglio;
        float yTesta = mg * 0.6f;
        rChiudi.set(w - mg - m.bersaglio, yTesta, w - mg, yTesta + altaTesta);
        rAggiorna.set(rChiudi.left - m.s2 - m.bersaglio, yTesta,
                      rChiudi.left - m.s2, yTesta + altaTesta);

        // La pastiglia e' larga quanto le serve - « Reggio nell'Emilia » non e'
        // « Bra » - ma non arriva mai addosso ai tasti.
        pTitolo.setTextSize(m.titolo);
        float dopoIlTitolo = mg + pTitolo.measureText("Meteo") + m.s5;
        float altaCitta = Math.max(m.bersaglio * 0.78f, m.nota * 2.1f);
        float largaCitta = m.s3 + m.icona + m.s2 + pNota.measureText(sCitta)
                         + m.s2 + m.icona * 0.7f + m.s3;
        float finoA = Math.max(dopoIlTitolo + m.bersaglio,
                Math.min(dopoIlTitolo + largaCitta, rAggiorna.left - m.s4));
        rCitta.set(dopoIlTitolo, yTesta + (altaTesta - altaCitta) / 2f,
                   finoA, yTesta + (altaTesta + altaCitta) / 2f);

        float cima = yTesta + altaTesta + m.s4;
        float fondo = h - mg;

        // La colonna di sinistra e' « adesso » e prende poco piu' di un terzo:
        // e' un numero grande e un disegno, non un elenco.
        float taglio = w * 0.36f;
        pAdesso.set(mg, cima, taglio, fondo);
        // Le ore si prendono quasi la meta': le colonne sono alte, perche'
        // dentro ognuna ci stanno sei cose.
        pOre.set(taglio + m.s2, cima, w - mg, cima + (fondo - cima) * 0.46f);
        pGiorni.set(taglio + m.s2, pOre.bottom + m.s2, w - mg, fondo);

        // ---- dentro « adesso », dal basso: le tessere sono l'ancora ----
        float altaDett = Math.max(m.bersaglio * 1.9f, (fondo - cima) * 0.16f);
        float fondoDett = pAdesso.bottom - m.s4;
        float larga = (pAdesso.width() - m.s4 * 2f - m.s2 * (DETTAGLI - 1)) / DETTAGLI;
        for (int i = 0; i < DETTAGLI; i++) {
            float x = pAdesso.left + m.s4 + (larga + m.s2) * i;
            tessDett[i].set(x, fondoDett - altaDett, x + larga, fondoDett);
        }

        // I testi stanno sopra le tessere, e la scena si prende quel che resta:
        // e' l'unica cosa che puo' rimpicciolire senza diventare illeggibile.
        float altoTesti = m.cifra * 0.60f + m.s1 + m.voce * 1.15f + m.s2 + m.corpo * 1.15f;
        float cimaTesti = tessDett[0].top - m.s4 - altoTesti;
        float latoScena = Math.max(m.bersaglio,
                Math.min(cimaTesti - pAdesso.top - m.s4, pAdesso.width() * 0.62f));
        scena.set(pAdesso.centerX() - latoScena / 2f,
                  cimaTesti - m.s3 - latoScena,
                  pAdesso.centerX() + latoScena / 2f,
                  cimaTesti - m.s3);
        riquadroScena.set((int) scena.left - 2, (int) scena.top - 2,
                          (int) scena.right + 2, (int) scena.bottom + 2);

        // ---- le ore: le linguette in cima, sotto sei colonne ----
        //
        // Le ventiquattro ore in fila erano ventiquattro francobolli con dentro
        // un numero e un segno: si vedevano tutte e non se ne leggeva nessuna.
        // Divise per fascia della giornata sono sei per volta, e in una colonna
        // larga due dita ci sta quello che di un'ora si vuole sapere davvero -
        // quanti gradi, se piove, quanto e' umido, che vento tira.
        float bandaTitolo = m.s3 + m.micro + m.s3;
        bandaOre = Math.max(bandaTitolo, m.bersaglio * 0.86f);
        float x0 = pOre.left + m.s3, x1 = pOre.right - m.s3;

        // Le linguette stanno <b>nella riga del titolo</b>, a destra: una fascia
        // loro sotto sarebbe cinquanta pixel tolti alle colonne per dire una
        // cosa che sta in mezza riga.
        pEtich.setTextSize(m.micro);
        float altaLing = Math.max(m.bersaglio * 0.66f, m.micro * 2.1f);
        float yLing = pOre.top + (bandaOre - altaLing) / 2f;
        float xL = x1;
        for (int i = FASCE - 1; i >= 0; i--) {
            if (i >= fasce.length) { rLinguetta[i].setEmpty(); continue; }
            float largaLing = pEtich.measureText(fasce[i].nome) + m.s3 * 2f;
            rLinguetta[i].set(xL - largaLing, yLing, xL, yLing + altaLing);
            xL -= largaLing + m.s1;
        }

        float cimaOre = pOre.top + bandaOre;
        float largoCol = (x1 - x0) / ORE_PER_FASCIA;
        for (int i = 0; i < ORE_PER_FASCIA; i++) {
            tessOra[i].set(x0 + largoCol * i, cimaOre,
                           x0 + largoCol * (i + 1) - m.s1, pOre.bottom - m.s3);
        }

        // ---- i sette giorni ----
        cimaGiorni = pGiorni.top + bandaTitolo;
        altaRigaGiorno = (pGiorni.bottom - m.s3 - cimaGiorni) / 7f;

        luoghi.misura(w, h);
    }

    @Override
    protected void onLayout(boolean cambiato, int l, int t, int r, int b) {
        super.onLayout(cambiato, l, t, r, b);
        // Il nome della citta' decide la larghezza della sua pastiglia, e il
        // nome cambia dopo la prima misura: senza questo, cambiando luogo la
        // pastiglia resterebbe della larghezza di quello di prima.
        misura(r - l, b - t);
    }

    // ---- disegno ----------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), pVelo);
        if (vetro == null || !vetro.vivo()) return;

        Meteo.Adesso a = meteo != null ? meteo.adesso() : null;
        int tinta = a != null ? Cielo.tinta(a.codice, a.giorno) : Tinte.APP;

        intestazione(c, tinta);
        pannelloAdesso(c, a, tinta);
        pannelloOre(c, tinta);
        pannelloGiorni(c, tinta);

        if (luoghi.aperto) luoghi.disegna(c);

        // Il cielo si muove: qui a fotogramma pieno, perche' questa schermata e'
        // aperta solo mentre qualcuno la sta guardando - e su quel solo
        // rettangolo, che e' un ottavo dello schermo. Col pannello dei luoghi
        // aperto la scena non si vede, e allora non si anima.
        if (a != null && !luoghi.aperto) {
            postInvalidateOnAnimation(riquadroScena.left, riquadroScena.top,
                                      riquadroScena.right, riquadroScena.bottom);
        }
        if (meteo != null && meteo.inCorso()) postInvalidateOnAnimation();
    }

    private void intestazione(Canvas c, int tinta) {
        pTitolo.setTextSize(m.titolo);
        pTitolo.setColor(Tinte.TESTO);
        // Il titolo si centra nella fascia insieme a tutto il resto, invece di
        // appoggiarsi al margine di sopra: e' quello che lo mette sulla stessa
        // riga della pastiglia e dei tasti.
        c.drawText("Meteo", m.margine,
                   rChiudi.centerY() + m.titolo * 0.35f, pTitolo);

        // La citta' e' una pastiglia, non una scritta: e' quella che si preme
        // per cambiarla, e una scritta non dice che si puo' premere. Lo spillo
        // dice « il tempo di qui », la freccetta dice « ce n'e' un elenco ».
        pPieno.setColor(premutoCitta ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawRoundRect(rCitta, rCitta.height() * 0.5f, rCitta.height() * 0.5f, pPieno);
        float cy = rCitta.centerY();
        float x = rCitta.left + m.s3;
        pIcona.setColor(tinta);
        Icone.disegna(c, Icone.POSIZIONE, x + m.icona * 0.5f, cy, m.icona, pIcona);

        x += m.icona + m.s2;
        pNota.setTextSize(m.nota);
        pNota.setColor(Tinte.TESTO);
        float spazio = rCitta.right - m.s3 - m.icona * 0.7f - m.s2 - x;
        c.drawText(rCittaRiga.in(pNota, sCitta, spazio, m.nota), x,
                   cy - (pNota.descent() + pNota.ascent()) / 2f, pNota);

        pIcona.setColor(Tinte.TESTO_TENUE);
        Icone.disegna(c, Icone.AVANTI, rCitta.right - m.s3 - m.icona * 0.35f, cy,
                      m.icona * 0.8f, pIcona);

        tasto(c, rAggiorna, Icone.SINCRONIZZA, premutoAggiorna, tinta,
              meteo != null && meteo.inCorso());
        tasto(c, rChiudi, Icone.CHIUDI, premutoChiudi, Tinte.APP, false);
    }

    /** Un tasto tondo dell'intestazione. Se {@code gira} e' vero l'icona ruota:
     *  e' l'unico modo di dire « sto chiedendo » senza aggiungere una scritta
     *  che poi va tolta. */
    private void tasto(Canvas c, RectF b, int segno, boolean premuto, int colore,
                       boolean gira) {
        // Un cerchio grigio, come i tasti piccoli di iOS.
        pPieno.setColor(premuto ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        c.drawCircle(b.centerX(), b.centerY(), Math.min(b.width(), b.height()) * 0.46f, pPieno);
        pIcona.setColor(premuto ? Tinte.TESTO : Tinte.TESTO_MEDIO);
        if (gira) {
            int salvato = c.save();
            // Si gira il Canvas e non il percorso: Icone porta il percorso alla
            // grandezza vera prima di disegnarlo, quindi qui non si sta
            // ingrandendo niente - e' solo una rotazione su una forma che e'
            // gia' larga trenta pixel.
            c.rotate((Anima.ora() % 900L) * 0.4f, b.centerX(), b.centerY());
            Icone.disegna(c, segno, b.centerX(), b.centerY(), b.height() * 0.46f, pIcona);
            c.restoreToCount(salvato);
        } else {
            Icone.disegna(c, segno, b.centerX(), b.centerY(), b.height() * 0.46f, pIcona);
        }
    }

    private void pannelloAdesso(Canvas c, Meteo.Adesso a, int tinta) {
        // L'unica scheda che prende il colore del tempo, appena: e' quella che
        // dice che tempo fa prima di qualunque numero.
        vetro.pannello(c, pAdesso, m.raggio, tinta, a != null ? 0x1C : Tinte.VELO_QUIETO);

        if (a == null) {
            String perche = meteo != null && meteo.nota() != null
                    ? meteo.nota() : "sto guardando che tempo fa…";
            pNota.setColor(Tinte.SPENTO);
            pNota.setTextSize(m.corpo);
            c.drawText(rNota.in(pNota, perche, pAdesso.width() - m.s4 * 2f, m.corpo),
                       pAdesso.left + m.s4, pAdesso.centerY(), pNota);
            return;
        }

        cielo.disegna(c, scena, a.codice, a.giorno);

        float cx = pAdesso.centerX();
        float largo = pAdesso.width() - m.s4 * 2f;
        float y = scena.bottom + m.s3 + m.cifra * 0.60f;

        pGradi.setTextAlign(Paint.Align.CENTER);
        pGradi.setColor(Tinte.TESTO);
        c.drawText(rGradi.adatta(pGradi, sGradi, largo, m.cifra * 0.78f, m.titolo),
                   cx, y, pGradi);

        pCorpo.setTextAlign(Paint.Align.CENTER);
        pCorpo.setColor(Tinte.con(tinta, 0xFF));
        y += m.s1 + m.voce * 0.9f;
        c.drawText(rCond.adatta(pCorpo, sCondizione, largo, m.voce, m.corpo * 0.8f),
                   cx, y, pCorpo);

        pNota.setTextAlign(Paint.Align.CENTER);
        pNota.setColor(Tinte.TESTO_TENUE);
        pNota.setTextSize(m.corpo);
        y += m.s2 + m.corpo * 0.9f;
        c.drawText(sPercepiti, cx, y, pNota);
        pNota.setTextAlign(Paint.Align.LEFT);
        pCorpo.setTextAlign(Paint.Align.LEFT);
        pGradi.setTextAlign(Paint.Align.LEFT);

        dettagli(c, tinta);
    }

    /**
     * Le quattro tessere: umidita', vento, pioggia di oggi, tramonto.
     *
     * Valore ed etichetta passano tutti e due da una {@link Testo.Riga}: qui
     * « TRAMONTO » usciva dalla tessera, e « 12 km/h » con due cifre ci sarebbe
     * arrivato subito dopo. Guardare a occhio se una scritta ci sta e' esattamente
     * quello che {@link Testo} esiste per non far rifare.
     */
    private void dettagli(Canvas c, int tinta) {
        final int[] segni = { Icone.UMIDITA, Icone.VENTO, Icone.GOCCIA, Icone.NOTTE };
        for (int i = 0; i < DETTAGLI; i++) {
            RectF b = tessDett[i];
            // Una tessera piatta, un grado piu' chiara della scheda.
            pPieno.setColor(TESSERA);
            c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pPieno);
            float spazio = b.width() - m.s2;

            pIcona.setColor(Tinte.TESTO_MEDIO);
            Icone.disegna(c, segni[i], b.centerX(), b.top + b.height() * 0.28f,
                    Math.min(b.width() * 0.38f, m.icona * 0.92f), pIcona);

            pCorpo.setTextAlign(Paint.Align.CENTER);
            pCorpo.setColor(Tinte.TESTO);
            c.drawText(rValore[i].adatta(pCorpo, sDettaglio[i], spazio,
                            m.corpo, m.corpo * 0.66f),
                    b.centerX(), b.top + b.height() * 0.66f, pCorpo);

            pEtich.setTextAlign(Paint.Align.CENTER);
            pEtich.setColor(Tinte.TESTO_TENUE);
            c.drawText(rEtichetta[i].adatta(pEtich, ETICHETTA[i], spazio,
                            m.micro, m.micro * 0.62f),
                    b.centerX(), b.bottom - m.s2, pEtich);
            pEtich.setTextAlign(Paint.Align.LEFT);
            pCorpo.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * Le ore, una fascia della giornata alla volta.
     *
     * <b>E' la forma delle previsioni orarie di meteo.it</b>, ed e' quella
     * giusta per un motivo che si vede solo mettendo le due cose accanto: le
     * ventiquattro ore in fila erano ventiquattro francobolli con dentro un
     * numero e un segno - si vedevano tutte e non se ne leggeva nessuna, e per
     * sapere com'era il pomeriggio bisognava contarle. Divise per fascia sono
     * sei per volta, e sei colonne larghe due dita hanno il posto per dire di
     * ogni ora <b>tutto quello che di un'ora si vuole sapere</b>: quanti gradi,
     * quanto sembrano, se piove, quanto e' umido, che vento tira e da dove.
     *
     * Le linguette sono le stesse fasce della Home, chieste pero' <b>senza
     * saltare quella in corso</b>: li' « adesso » e' gia' scritto grande, qui
     * le ore che restano di questa mattina servono.
     */
    private void pannelloOre(Canvas c, int tinta) {
        vetro.pannello(c, pOre, m.raggio, tinta, Tinte.VELO_QUIETO);
        pEtich.setTextSize(m.nota);
        pEtich.setColor(Tinte.TESTO_TENUE);
        pEtich.setTextAlign(Paint.Align.LEFT);
        c.drawText("Ora per ora", pOre.left + m.s3,
                   pOre.top + bandaOre / 2f + m.nota * 0.36f, pEtich);

        linguette(c, tinta);

        if (fasciaScelta >= fasce.length) return;
        Meteo.Fascia f = fasce[fasciaScelta];
        Meteo.Ora[] ore = meteo != null ? meteo.ore() : new Meteo.Ora[0];
        int quante = Math.min(f.quante, ORE_PER_FASCIA);

        // Una fascia in corso ha meno di sei ore: le colonne restano larghe
        // uguali e si centrano. Se si allargassero, cambiare linguetta
        // cambierebbe anche la misura di tutto, e sarebbe una schermata che si
        // rimonta a ogni tocco.
        float largoCol = tessOra[0].width() + m.s1;
        float scarto = (ORE_PER_FASCIA - quante) * largoCol / 2f;

        for (int k = 0; k < quante; k++) {
            int i = f.da + k;
            if (i >= ore.length || i >= ORE) break;
            // Le scritte si compongono sul thread dell'interfaccia e i dati
            // arrivano su quello di lavoro: per un fotogramma l'elenco puo'
            // essere nuovo e le scritte no.
            if (sOraQuando[i] == null || sOraGradi[i] == null) continue;
            colonna(c, tessOra[k], scarto, ore[i], i, tinta);
        }
    }

    /** Le quattro linguette, a destra nella riga del titolo. */
    private void linguette(Canvas c, int tinta) {
        // Un controllo a segmenti di iOS: un binario grigio sotto tutte le
        // linguette, e il segmento scelto pieno.
        int quante = Math.min(FASCE, fasce.length);
        if (quante > 0 && !rLinguetta[0].isEmpty() && !rLinguetta[quante - 1].isEmpty()) {
            appoggio.set(rLinguetta[0].left - m.s1, rLinguetta[0].top - m.s1,
                         rLinguetta[quante - 1].right + m.s1, rLinguetta[0].bottom + m.s1);
            pPieno.setColor(Tinte.RIEMPIMENTO);
            c.drawRoundRect(appoggio, appoggio.height() * 0.5f, appoggio.height() * 0.5f, pPieno);
        }
        for (int i = 0; i < FASCE && i < fasce.length; i++) {
            RectF b = rLinguetta[i];
            if (b.isEmpty()) continue;
            boolean scelta = i == fasciaScelta;
            if (scelta || premutaLinguetta == i) {
                pPieno.setColor(scelta ? Tinte.SEGMENTO_SCELTO : Tinte.RIEMPIMENTO);
                c.drawRoundRect(b, b.height() * 0.5f, b.height() * 0.5f, pPieno);
            }
            pEtich.setTextAlign(Paint.Align.CENTER);
            pEtich.setTextSize(m.micro);
            pEtich.setColor(scelta ? Tinte.TESTO : Tinte.TESTO_MEDIO);
            c.drawText(rLing[i].in(pEtich, fasce[i].nome, b.width() - m.s2, m.micro),
                    b.centerX(), b.centerY() - (pEtich.descent() + pEtich.ascent()) / 2f,
                    pEtich);
            pEtich.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * Una colonna: un'ora, dall'alto in basso.
     *
     * Tutto incolonnato <b>partendo dall'alto</b>, ogni riga sotto quella di
     * prima. Ancorare qualcosa al bordo di sotto e qualcos'altro a quello di
     * sopra funziona finche' non cambia l'altezza, e il giorno che cambia due
     * scritte si stampano una addosso all'altra - e' gia' successo qui.
     */
    private void colonna(Canvas c, RectF b, float scarto, Meteo.Ora o, int i, int tinta) {
        float sx = b.left + scarto, dx = b.right + scarto;
        appoggio.set(sx, b.top, dx, b.bottom);
        boolean adesso = o.adesso;
        // Le colonne non hanno un fondo: come nel Meteo di iOS stanno sulla
        // scheda e basta. Solo l'ora in corso ha una tessera chiara dietro -
        // bianca, non del colore del tempo, che sul pannello si sporcava.
        if (adesso) {
            pPieno.setColor(Tinte.RIEMPIMENTO);
            c.drawRoundRect(appoggio, m.raggioPiccolo, m.raggioPiccolo, pPieno);
        }

        float cx = (sx + dx) / 2f;
        float largo = dx - sx - m.s2;
        // Un'ora passata c'e' ma si e' spenta: si legge ancora - serve a dire
        // com'e' andata la mattina - e non compete con quelle che devono
        // ancora venire.
        boolean via = o.passata;
        int forte = via ? Tinte.SPENTO : Tinte.TESTO;
        int fioco = via ? Tinte.SPENTO : Tinte.TESTO_TENUE;
        float yOre = b.top + m.s3 + m.micro;
        float latoIcona = Math.min(largo * 0.44f, m.icona * 1.5f);
        float cyIcona = yOre + m.s2 + latoIcona / 2f;
        float yGradi = cyIcona + latoIcona / 2f + m.s2 + m.voce * 0.78f;

        pNota.setTextAlign(Paint.Align.CENTER);
        pNota.setTextSize(m.micro);
        pNota.setColor(adesso ? Tinte.TESTO : fioco);
        c.drawText(sOraQuando[i], cx, yOre, pNota);

        pIcona.setColor(Tinte.con(Cielo.tinta(o.codice, o.giorno), via ? 0x66 : 0xEE));
        Icone.disegna(c, Cielo.icona(o.codice, o.giorno), cx, cyIcona, latoIcona, pIcona);

        pCorpo.setTextAlign(Paint.Align.CENTER);
        pCorpo.setTextSize(m.voce);
        pCorpo.setColor(forte);
        c.drawText(sOraGradi[i], cx, yGradi, pCorpo);
        pCorpo.setTextAlign(Paint.Align.LEFT);

        // Le tre righe di sotto: pioggia, umidita', vento. Ognuna e' un segno e
        // un numero, e si centrano insieme - un'icona incolonnata a sinistra e
        // il numero al centro sarebbero due colonne dentro una colonna.
        float y = yGradi + m.s3 + m.micro;
        if (sOraPioggia[i] != null) {
            segnoENumero(c, cx, y, Icone.GOCCIA, sOraPioggia[i],
                    via ? Tinte.SPENTO : Tinte.ACQUA);
        }
        y += m.s2 + m.micro;
        segnoENumero(c, cx, y, Icone.UMIDITA, sOraUmidita[i], fioco);
        y += m.s2 + m.micro;
        segnoENumero(c, cx, y, Icone.VENTO, sOraVento[i], fioco);

        pNota.setTextAlign(Paint.Align.LEFT);
    }

    /** Un segno e un numero, centrati insieme sulla stessa riga. */
    private void segnoENumero(Canvas c, float cx, float y, int segno, String testo,
                              int colore) {
        if (testo == null) return;
        pNota.setTextAlign(Paint.Align.LEFT);
        pNota.setTextSize(m.micro);
        float lato = m.micro * 1.05f;
        float largo = lato + m.s1 + pNota.measureText(testo);
        float x = cx - largo / 2f;
        pIcona.setColor(Tinte.con(colore, 0xCC));
        Icone.disegna(c, segno, x + lato / 2f, y - m.micro * 0.35f, lato, pIcona);
        pNota.setColor(colore);
        c.drawText(testo, x + lato + m.s1, y, pNota);
    }

    /**
     * I sette giorni, con la barra delle temperature.
     *
     * La barra e' la cosa che rende leggibile una settimana: sette coppie di
     * numeri si guardano una per una, sette segmenti allineati sulla stessa
     * scala si confrontano con un'occhiata. Il colore del segmento viene dalla
     * massima di quel giorno - dall'azzurro dell'acqua all'ambra del sole - e si
     * ottiene fondendo due tinte, senza uno shader da costruire per riga.
     */
    private void pannelloGiorni(Canvas c, int tinta) {
        vetro.pannello(c, pGiorni, m.raggio, tinta, Tinte.VELO_QUIETO);
        pEtich.setTextSize(m.nota);
        pEtich.setColor(Tinte.TESTO_TENUE);
        c.drawText("Prossimi giorni", pGiorni.left + m.s3,
                   pGiorni.top + m.s3 + m.micro, pEtich);

        Meteo.Giorno[] gg = meteo != null ? meteo.giorni() : new Meteo.Giorno[0];
        // Stessa guardia della striscia delle ore, per la stessa ragione: qui
        // sarebbe un indice fuori dall'array delle scritte.
        int quanti = Math.min(Math.min(gg.length, sGiornoNome.length), 7);
        if (quanti == 0 || maxSettimana <= minSettimana) return;

        float x0 = pGiorni.left + m.s4, x1 = pGiorni.right - m.s4;
        float largoNome = (x1 - x0) * 0.16f;
        float latoIcona = Math.min(m.icona * 1.2f, altaRigaGiorno * 0.62f);
        float largoPioggia = (x1 - x0) * 0.09f;
        float largoGradi = (x1 - x0) * 0.09f;

        float xBarra0 = x0 + largoNome + latoIcona + m.s3 + largoPioggia + largoGradi + m.s2;
        float xBarra1 = x1 - largoGradi - m.s2;
        float alta = Math.max(m.dp(7), altaRigaGiorno * 0.18f);

        for (int i = 0; i < quanti; i++) {
            Meteo.Giorno g = gg[i];
            float cy = cimaGiorni + altaRigaGiorno * (i + 0.5f);
            float yTesto = cy - (pCorpo.descent() + pCorpo.ascent()) / 2f;

            // Il capello sopra ogni giorno, come nell'elenco del Meteo di iOS.
            pPieno.setColor(Tinte.SEPARATORE);
            float yFilo = cimaGiorni + altaRigaGiorno * i;
            c.drawRect(x0, yFilo, x1, yFilo + 1f, pPieno);

            pCorpo.setTextSize(m.corpo);
            pCorpo.setColor(i == 0 ? Tinte.TESTO : Tinte.TESTO_MEDIO);
            c.drawText(sGiornoNome[i], x0, yTesto, pCorpo);

            pIcona.setColor(Tinte.con(Cielo.tinta(g.codice, true), 0xEE));
            Icone.disegna(c, Cielo.icona(g.codice, true),
                    x0 + largoNome + latoIcona * 0.5f, cy, latoIcona, pIcona);

            if (sGiornoPioggia[i] != null) {
                pNota.setTextSize(m.nota);
                pNota.setColor(Tinte.ACQUA);
                c.drawText(sGiornoPioggia[i], x0 + largoNome + latoIcona + m.s3,
                           cy - (pNota.descent() + pNota.ascent()) / 2f, pNota);
            }

            pNota.setTextSize(m.corpo);
            pNota.setColor(Tinte.TESTO_TENUE);
            pNota.setTextAlign(Paint.Align.RIGHT);
            c.drawText(sGiornoMin[i], xBarra0 - m.s2,
                       cy - (pNota.descent() + pNota.ascent()) / 2f, pNota);
            pNota.setTextAlign(Paint.Align.LEFT);

            float largo = xBarra1 - xBarra0;
            appoggio.set(xBarra0, cy - alta / 2f, xBarra1, cy + alta / 2f);
            pPieno.setColor(Tinte.con(Tinte.TESTO, 0x1A));
            c.drawRoundRect(appoggio, alta / 2f, alta / 2f, pPieno);

            float da = (g.min - minSettimana) / (maxSettimana - minSettimana);
            float a  = (g.max - minSettimana) / (maxSettimana - minSettimana);
            appoggio.set(xBarra0 + largo * da, cy - alta / 2f,
                         Math.max(xBarra0 + largo * a, xBarra0 + largo * da + alta),
                         cy + alta / 2f);
            pPieno.setColor(Tinte.fondi(Tinte.ACQUA, Tinte.SOLE, caldo(g.max)));
            c.drawRoundRect(appoggio, alta / 2f, alta / 2f, pPieno);

            pCorpo.setColor(Tinte.TESTO);
            c.drawText(sGiornoMax[i], xBarra1 + m.s2, yTesto, pCorpo);
        }
    }

    /** Da gradi a « quanto e' caldo », fra zero e uno. Zero e' cinque gradi, uno
     *  e' trentacinque: sotto e sopra non serve distinguere, e' comunque freddo
     *  o comunque caldo. */
    private static float caldo(float gradi) {
        return Math.max(0f, Math.min(1f, (gradi - 5f) / 30f));
    }

    // ---- le scritte -------------------------------------------------------------

    private void componi() {
        sCitta = meteo != null ? meteo.citta() : "";
        Meteo.Adesso a = meteo != null ? meteo.adesso() : null;
        if (a == null) {
            sGradi = sCondizione = sPercepiti = "";
            for (int i = 0; i < DETTAGLI; i++) sDettaglio[i] = "-";
            for (int i = 0; i < ORE; i++) {
                sOraQuando[i] = sOraGradi[i] = sOraPioggia[i] = null;
                sOraUmidita[i] = sOraVento[i] = null;
            }
            fasce = new Meteo.Fascia[0];
            sGiornoNome = sGiornoMin = sGiornoMax = sGiornoPioggia = new String[0];
            return;
        }
        sGradi = gradi(a.gradi);
        sCondizione = Cielo.nome(a.codice);
        sPercepiti = "percepiti " + gradi(a.percepiti);

        Meteo.Giorno oggi = meteo.oggi();
        sDettaglio[0] = a.umidita + "%";
        sDettaglio[1] = Math.round(a.vento) + " km/h";
        sDettaglio[2] = oggi != null ? oggi.pioggia + "%" : "-";
        sDettaglio[3] = oggi != null && oggi.tramonto != null ? oggi.tramonto : "-";

        Meteo.Ora[] ore = meteo.ore();
        for (int i = 0; i < ORE; i++) {
            if (i >= ore.length) {
                sOraQuando[i] = sOraGradi[i] = sOraPioggia[i] = null;
                sOraUmidita[i] = sOraVento[i] = null;
                continue;
            }
            // « ore 09 » e non « 09 »: nella colonna sta accanto a un'altra
            // manciata di numeri con la percentuale, e due cifre da sole li'
            // dentro si leggono come un valore qualunque. E' anche come lo
            // scrive meteo.it, per la stessa ragione.
            sOraQuando[i] = ore[i].adesso ? "adesso"
                    : String.format(Locale.ITALIAN, "ore %02d", ore[i].ora);
            sOraGradi[i] = gradi(ore[i].gradi);
            sOraPioggia[i] = ore[i].pioggia >= 20 ? ore[i].pioggia + "%" : null;
            sOraUmidita[i] = ore[i].umidita + "%";
            // Con l'unita': « 3 SO » da solo, sotto un « 67% », si legge come
            // un altro numero qualunque.
            sOraVento[i] = Math.round(ore[i].vento) + " km/h "
                    + Meteo.verso(ore[i].direzione);
        }
        // Le fasce con dentro gli indici delle loro ore, senza saltare quella
        // in corso: e' la riga di linguette.
        // Le quattro parti di oggi, passate comprese: e' la riga di linguette.
        fasce = meteo.fasceDiOggi();
        if (fasciaScelta >= fasce.length) fasciaScelta = 0;
        if (!sceltaDaMano) laFasciaDiAdesso();

        Meteo.Giorno[] gg = meteo.giorni();
        sGiornoNome = new String[gg.length];
        sGiornoMin = new String[gg.length];
        sGiornoMax = new String[gg.length];
        sGiornoPioggia = new String[gg.length];
        minSettimana = Float.MAX_VALUE;
        maxSettimana = -Float.MAX_VALUE;
        for (int i = 0; i < gg.length; i++) {
            sGiornoNome[i] = gg[i].nome;
            sGiornoMin[i] = gradi(gg[i].min);
            sGiornoMax[i] = gradi(gg[i].max);
            sGiornoPioggia[i] = gg[i].pioggia >= 20 ? gg[i].pioggia + "%" : null;
            minSettimana = Math.min(minSettimana, gg[i].min);
            maxSettimana = Math.max(maxSettimana, gg[i].max);
        }
        // Una settimana tutta uguale darebbe una divisione per zero e sette
        // barre lunghe zero: si allarga la scala di un grado per parte.
        if (maxSettimana - minSettimana < 1f) { minSettimana -= 1f; maxSettimana += 1f; }
    }

    private static String gradi(float g) {
        return String.format(Locale.ITALIAN, "%.0f°", g);
    }

    /**
     * Si apre sulla parte della giornata in cui si e'.
     *
     * Finche' nessuno ha toccato una linguetta: e' quello che uno vuole vedere
     * aprendo la pagina, e alle nove di sera aprirla su « notte » perche' e' la
     * prima delle quattro sarebbe una schermata da correggere ogni volta.
     */
    private void laFasciaDiAdesso() {
        Meteo.Ora[] o = meteo != null ? meteo.ore() : new Meteo.Ora[0];
        for (int i = 0; i < fasce.length; i++) {
            Meteo.Fascia f = fasce[i];
            for (int k = 0; k < f.quante; k++) {
                int j = f.da + k;
                if (j < o.length && o[j].adesso) { fasciaScelta = i; return; }
            }
        }
    }

    // ---- tocco --------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (luoghi.aperto) return luoghi.tocco(e, x, y);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                premutoChiudi = rChiudi.contains(x, y);
                premutoAggiorna = rAggiorna.contains(x, y);
                premutoCitta = rCitta.contains(x, y);
                premutaLinguetta = -1;
                for (int i = 0; i < FASCE && i < fasce.length; i++) {
                    if (rLinguetta[i].contains(x, y)) { premutaLinguetta = i; break; }
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (premutoChiudi && rChiudi.contains(x, y) && uscita != null) {
                    uscita.suChiudi();
                } else if (premutoAggiorna && rAggiorna.contains(x, y) && meteo != null) {
                    meteo.rinfresca();
                } else if (premutoCitta && rCitta.contains(x, y)) {
                    luoghi.apri();
                } else if (premutaLinguetta >= 0
                           && rLinguetta[premutaLinguetta].contains(x, y)) {
                    fasciaScelta = premutaLinguetta;
                    sceltaDaMano = true;
                }
                premutoChiudi = premutoAggiorna = premutoCitta = false;
                premutaLinguetta = -1;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                premutoChiudi = premutoAggiorna = premutoCitta = false;
                premutaLinguetta = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    // =========================================================================
    //  I luoghi
    // =========================================================================

    /**
     * Il pannello dei luoghi: quelli tenuti da parte, e come aggiungerne.
     *
     * <b>Perche' sta dentro la vela e non e' una vela sua.</b> Il telaio tiene
     * una vela alla volta: aprirne una seconda toglierebbe di scena il meteo, e
     * chiudendola bisognerebbe riaprirlo - con una schermata che sfarfalla in
     * mezzo. Qui invece e' un pannello sopra la stessa View, che si accende e si
     * spegne con un booleano.
     *
     * <b>La tastiera e' quella di Casa</b> ({@link Tastierino}), la stessa della
     * ricerca di Spotify: quella di sistema si prende meta' schermo e porta con
     * se' suggerimenti, emoji e una scorciatoia alle impostazioni - che su un
     * apparecchio in chiosco e' anche una porta aperta.
     *
     * <b>Si cerca mentre si scrive</b>, ma non a ogni lettera: si aspettano
     * trecentocinquanta millisecondi di silenzio. Senza, scrivere « bologna »
     * sono sette richieste di cui sei buttate, e le risposte arrivano in ordine
     * sparso.
     */
    private final class PannelloLuoghi {

        boolean aperto;

        private final Tastierino tastierino = new Tastierino();
        private final RectF pannello = new RectF(), barra = new RectF();
        private final RectF rChiudiL = new RectF();
        private final RectF[] righe = new RectF[RIGHE_LUOGHI];
        private final RectF[] rTogli = new RectF[RIGHE_LUOGHI];
        private final Testo.Riga[] rNome = new Testo.Riga[RIGHE_LUOGHI];
        private final Testo.Riga[] rDove = new Testo.Riga[RIGHE_LUOGHI];
        private final Testo.Riga rVuoto = new Testo.Riga();

        private String scritto = "";
        private Meteo.Posto[] trovati = new Meteo.Posto[0];
        private String perche;
        private boolean cercando;
        private int premuta = -1, premutoTogli = -1;
        private boolean premutoChiudiL;

        /** Il giro di ricerca: le risposte in ritardo di una ricerca gia'
         *  superata non devono toccare niente. E' la stessa guardia che la
         *  Musica usa sulle playlist e la Radio sulle stazioni. */
        private int giro;

        final Runnable fraPoco = new Runnable() {
            @Override public void run() { cercaAdesso(); }
        };

        PannelloLuoghi() {
            for (int i = 0; i < RIGHE_LUOGHI; i++) {
                righe[i] = new RectF();
                rTogli[i] = new RectF();
                rNome[i] = new Testo.Riga();
                rDove[i] = new Testo.Riga();
            }
        }

        void misura(int w, int h) {
            float mg = m.margine;
            pannello.set(w * 0.17f, mg, w * 0.83f, h - mg);
            rChiudiL.set(pannello.right - m.s3 - m.bersaglio, pannello.top + m.s3,
                         pannello.right - m.s3, pannello.top + m.s3 + m.bersaglio);

            float bandaTitolo = m.s3 + m.micro + m.s3;
            float x0 = pannello.left + m.s4, x1 = pannello.right - m.s4;
            float y = pannello.top + bandaTitolo + m.s3;
            barra.set(x0, y, x1, y + Math.max(m.bersaglio, m.corpo * 2.1f));

            float altaTastiera = h * 0.32f;
            tastierino.misura(m, x0, pannello.bottom - m.s3 - altaTastiera,
                              x1, pannello.bottom - m.s3);

            float cima = barra.bottom + m.s3;
            float fondo = tastierino.area().top - m.s3;
            float alta = (fondo - cima) / RIGHE_LUOGHI;
            for (int i = 0; i < RIGHE_LUOGHI; i++) {
                righe[i].set(x0, cima + alta * i, x1, cima + alta * (i + 1) - m.s1);
                rTogli[i].set(righe[i].right - m.bersaglio,
                              righe[i].centerY() - m.bersaglio / 2f,
                              righe[i].right, righe[i].centerY() + m.bersaglio / 2f);
            }
        }

        void apri() {
            aperto = true;
            scritto = "";
            trovati = new Meteo.Posto[0];
            perche = null;
            cercando = false;
            giro++;
            invalidate();
        }

        void chiudi() {
            aperto = false;
            removeCallbacks(fraPoco);
            giro++;
            invalidate();
        }

        /** Quello che si vede adesso: i posti tenuti da parte, o quelli trovati. */
        private Meteo.Posto[] elenco() {
            if (scritto.trim().length() > 0) return trovati;
            return meteo != null ? meteo.preferiti() : new Meteo.Posto[0];
        }

        // ---- disegno --------------------------------------------------------

        void disegna(Canvas c) {
            // Un secondo velo sopra la pagina: sotto si intravede il meteo, e va
            // bene - dice che questo pannello e' una parentesi, non un'altra
            // schermata.
            pPieno.setColor(0xB3000000);
            c.drawRect(0, 0, getWidth(), getHeight(), pPieno);
            vetro.pannello(c, pannello, m.raggio, Tinte.APP, Tinte.VELO_QUIETO);

            pEtich.setTextSize(m.nota);
            pEtich.setColor(Tinte.TESTO_TENUE);
            pEtich.setTextAlign(Paint.Align.LEFT);
            c.drawText("Luoghi", pannello.left + m.s4,
                       pannello.top + m.s3 + m.micro, pEtich);
            tasto(c, rChiudiL, Icone.CHIUDI, premutoChiudiL, Tinte.APP, false);

            barraDiRicerca(c);

            Meteo.Posto[] elenco = elenco();
            boolean cercati = scritto.trim().length() > 0;
            int quanti = Math.min(RIGHE_LUOGHI, elenco.length);
            for (int i = 0; i < quanti; i++) riga(c, i, elenco[i], cercati);

            if (quanti == 0) {
                String vuoto = perche != null ? perche
                        : cercando ? "sto cercando…"
                        : cercati ? "scrivine almeno due lettere"
                        : "nessun luogo da parte: scrivi qui sopra per aggiungerne";
                pNota.setTextSize(m.corpo);
                pNota.setColor(Tinte.SPENTO);
                pNota.setTextAlign(Paint.Align.LEFT);
                c.drawText(rVuoto.in(pNota, vuoto, pannello.width() - m.s4 * 2f, m.corpo),
                           pannello.left + m.s4, righe[0].top + m.corpo * 1.4f, pNota);
            }

            tastierino.disegna(c, vetro, m, Tinte.APP);
        }

        private void barraDiRicerca(Canvas c) {
            // Il campo di ricerca di iOS: una capsula grigia con la lente.
            pPieno.setColor(Tinte.RIEMPIMENTO);
            c.drawRoundRect(barra, barra.height() / 2f, barra.height() / 2f, pPieno);
            float cy = barra.centerY();
            float x = barra.left + m.s3;
            pIcona.setColor(Tinte.TESTO_TENUE);
            Icone.disegna(c, Icone.CERCA, x + m.icona * 0.5f, cy, m.icona, pIcona);

            pCorpo.setTextAlign(Paint.Align.LEFT);
            pCorpo.setTextSize(m.corpo);
            pCorpo.setColor(scritto.length() > 0 ? Tinte.TESTO : Tinte.TESTO_TENUE);
            String testo = scritto.length() > 0 ? scritto : "cerca una citta'";
            c.drawText(testo, x + m.icona + m.s2,
                       cy - (pCorpo.descent() + pCorpo.ascent()) / 2f, pCorpo);
        }

        private void riga(Canvas c, int i, Meteo.Posto p, boolean cercato) {
            RectF b = righe[i];
            boolean qui = meteo != null && p.uguale(meteo.attuale());
            // Il posto in cui si sta si segna con un velo piu' denso, non con
            // un colore suo: l'ambra sopra l'azzurro del pannello dava una riga
            // grigio-verde che sembrava spenta invece che scelta. Il colore sta
            // nello spillo, che e' l'accento piccolo.
            if (premuta == i || qui) {
                pPieno.setColor(premuta == i ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
                c.drawRoundRect(b, m.raggioPiccolo, m.raggioPiccolo, pPieno);
            } else if (i > 0) {
                // Fra due luoghi qualunque, un capello e niente fondo.
                pPieno.setColor(Tinte.SEPARATORE);
                c.drawRect(b.left + m.s3 + m.icona + m.s3, b.top, b.right, b.top + 1f, pPieno);
            }

            float x = b.left + m.s3;
            pIcona.setColor(qui ? Tinte.con(Tinte.SOLE, 0xEE) : Tinte.TESTO_TENUE);
            Icone.disegna(c, Icone.POSIZIONE, x + m.icona * 0.5f, b.centerY(), m.icona, pIcona);
            x += m.icona + m.s3;

            // Il nome sopra e la regione sotto: senza la seconda riga, due
            // « Napoli » nell'elenco dei trovati sono indistinguibili, e chi ne
            // sceglie una a caso poi non capisce perche' il tablet dice che
            // nevica.
            boolean cestino = !cercato && !qui;
            float spazio = (cestino ? rTogli[i].left - m.s2 : b.right - m.s3) - x;
            pCorpo.setTextAlign(Paint.Align.LEFT);
            pCorpo.setTextSize(m.corpo);
            pCorpo.setColor(Tinte.TESTO);
            c.drawText(rNome[i].adatta(pCorpo, p.nome, spazio, m.corpo, m.corpo * 0.72f),
                       x, b.centerY() - m.s1 * 0.4f, pCorpo);
            pNota.setTextAlign(Paint.Align.LEFT);
            pNota.setTextSize(m.nota);
            pNota.setColor(Tinte.TESTO_TENUE);
            c.drawText(rDove[i].adatta(pNota, p.dove, spazio, m.nota, m.nota * 0.72f),
                       x, b.centerY() + m.nota * 1.05f, pNota);

            // Il cestino c'e' solo sui luoghi tenuti da parte, e non su quello
            // in cui si sta: sarebbe una riga che sparisce sotto il dito e un
            // tablet che continua a mostrare un posto fuori elenco.
            if (cestino) {
                pIcona.setColor(premutoTogli == i ? Tinte.ALLARME : Tinte.SPENTO);
                Icone.disegna(c, Icone.CHIUDI, rTogli[i].centerX(), rTogli[i].centerY(),
                              m.icona * 0.8f, pIcona);
            }
        }

        // ---- tocco ----------------------------------------------------------

        boolean tocco(MotionEvent e, float x, float y) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (tastierino.area().contains(x, y)) {
                        tastierino.premi(x, y);
                        invalidate();
                        return true;
                    }
                    premutoChiudiL = rChiudiL.contains(x, y);
                    premuta = premutoTogli = -1;
                    for (int i = 0; i < RIGHE_LUOGHI && i < elenco().length; i++) {
                        if (rTogli[i].contains(x, y) && scritto.trim().length() == 0) {
                            premutoTogli = i;
                            break;
                        }
                        if (righe[i].contains(x, y)) { premuta = i; break; }
                    }
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP: {
                    if (tastierino.area().contains(x, y)) {
                        tastierino.lascia();
                        scrivi(tastierino.tocco(x, y));
                        return true;
                    }
                    Meteo.Posto[] elenco = elenco();
                    if (premutoChiudiL && rChiudiL.contains(x, y)) { chiudi(); return true; }
                    if (premutoTogli >= 0 && rTogli[premutoTogli].contains(x, y)) {
                        if (premutoTogli < elenco.length && meteo != null) {
                            meteo.dimentica(elenco[premutoTogli]);
                        }
                    } else if (premuta >= 0 && righe[premuta].contains(x, y)
                               && premuta < elenco.length && meteo != null) {
                        // Sceglierlo lo mette anche fra quelli tenuti da parte:
                        // chi cerca una citta' e ci va, quella citta' la
                        // guardera' di nuovo.
                        meteo.ricorda(elenco[premuta]);
                        meteo.vaiA(elenco[premuta]);
                        chiudi();
                        return true;
                    }
                    premuta = premutoTogli = -1;
                    premutoChiudiL = false;
                    invalidate();
                    return true;
                }

                case MotionEvent.ACTION_CANCEL:
                    tastierino.lascia();
                    premuta = premutoTogli = -1;
                    premutoChiudiL = false;
                    invalidate();
                    return true;
            }
            return true;
        }

        private void scrivi(int tasto) {
            if (tasto == Tastierino.NIENTE) return;
            if (tasto == Tastierino.CHIUDI) { chiudi(); return; }
            if (tasto == Tastierino.CANCELLA) {
                if (scritto.length() > 0) scritto = scritto.substring(0, scritto.length() - 1);
            } else if (tasto == Tastierino.SPAZIO) {
                if (scritto.length() > 0) scritto = scritto + " ";
            } else {
                scritto = scritto + (char) tasto;
            }
            perche = null;
            rimanda();
            invalidate();
        }

        private void rimanda() {
            removeCallbacks(fraPoco);
            giro++;
            if (scritto.trim().length() < 2) {
                trovati = new Meteo.Posto[0];
                cercando = false;
                return;
            }
            cercando = true;
            postDelayed(fraPoco, 350);
        }

        private void cercaAdesso() {
            if (meteo == null) return;
            final int mio = ++giro;
            meteo.cerca(scritto, new Meteo.Trovati() {
                @Override public void luoghi(Meteo.Posto[] posti, String spiegazione) {
                    if (mio != giro) return;      // una ricerca piu' nuova ha gia' vinto
                    trovati = posti;
                    perche = spiegazione;
                    cercando = false;
                    invalidate();
                }
            });
        }
    }
}
