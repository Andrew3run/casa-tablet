package dev.casa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.view.MotionEvent;

/**
 * Le stazioni, e il telecomando della radio.
 *
 * Qui la radio smette di essere un interruttore acceso/spento e diventa una
 * sorgente come le altre: si sceglie la stazione, si va avanti e indietro, si
 * ferma, si alza e si abbassa il volume.
 *
 * Le tessere portano il <b>logo vero</b> della stazione, perche' un marchio si
 * riconosce da un metro senza leggere. Il fondo sotto il logo non e' sempre lo
 * stesso: e' deciso a monte misurando la luminosita' del marchio, perche' una
 * scritta nera su trasparente sparirebbe sul fondo scuro e una bianca
 * sparirebbe sul chiaro.
 *
 * Le tre stazioni che un logo utilizzabile non ce l'hanno tengono il disco con
 * la sigla. Meglio una sigla onesta di un rettangolo vuoto.
 */
public class SezioneRadio extends Sezione {

    /** Sei per riga. Con ventidue stazioni, quattro colonne davano sei righe e
     *  le tessere venivano piu' alte che larghe; sei ne danno quattro, e la
     *  tessera resta un rettangolo in cui il logo sta comodo. */
    private static final int COLONNE = 6;

    private final Paint pSigla    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNome     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitolo   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pOra      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNota     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pComando  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDisco    = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Il fondo piatto di tessere e comandi: riempimenti, non vetro. */
    private final Paint pFondo    = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Il titolo grande della sezione, in alto a sinistra. */
    private final Paint pGrande   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLogo     = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF tessLogo  = new RectF();

    /** Una per stazione, e le stazioni le decide il PC: quante sono si sa solo
     *  a configurazione letta. Vedi {@link #ricomponi()}. */
    private RectF[] tessere = new RectF[0];
    private final RectF   barra   = new RectF();
    /** precedente, play/stop, successiva, volume giu', volume su. */
    private final RectF[] comandi = new RectF[5];

    private final RectF appoggio = new RectF();

    private Radio radio;
    private Loghi loghi;
    private AudioManager audio;
    private int premuta = -1;        // indice tessera
    private int premutoComando = -1;

    /** Il lato del logo, e dove sta il suo centro rispetto alla cima della
     *  tessera. Calcolati per bande invece che a frazioni: le frazioni a occhio
     *  facevano finire il logo addosso al nome. */
    private float latoLogo, cyLogo, yNome;

    /**
     * La fascia in cui sta quel che suona, dentro la barra: da sotto la
     * scritta IN ONDA a sopra il bordo, e larga fino a dove cominciano i
     * comandi.
     */
    private final RectF bandaBarra = new RectF();

    /** Lo spazio che una scritta ha davvero, in una tessera e nella barra. */
    private float larghezzaTessera, larghezzaBarra;

    /** I corpi nominali: le Riga rimpiccioliscono il Paint quando serve, quindi
     *  ogni scritta riparte da qui. */
    private float corpoNome, corpoTitolo, corpoOra, corpoNota;

    /**
     * Una Riga per ogni nome di stazione, piu' quelle della barra.
     *
     * Contavano i caratteri e non bastava. Nella barra era la peggiore:
     * quarantaquattro caratteri a corpo intero sono quasi mille pixel, e lo
     * spazio prima dei comandi ne ha meno di seicento - "Radio Norba: non
     * risponde (oltre 15 secondi)" ne ha quarantatre, quindi non veniva
     * nemmeno tagliata e finiva a passare sotto i tasti del volume.
     */
    private Testo.Riga[] rNomi = new Testo.Riga[0];
    private final Testo.Riga rNomeBarra = new Testo.Riga();
    private final Testo.Riga rNotaBarra = new Testo.Riga();

    public SezioneRadio(Context c, Misure m) {
        super(c, m);
        // Le tessere e le righe dei nomi non si creano qui: sono tante quante
        // le stazioni, e a questo punto la radio non e' ancora arrivata.
        for (int i = 0; i < comandi.length; i++) comandi[i] = new RectF();

        pSigla.setTextAlign(Paint.Align.CENTER);
        pSigla.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pNome.setTextAlign(Paint.Align.CENTER);
        pNome.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pTitolo.setColor(Tinte.TESTO_TENUE);
        pTitolo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pOra.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        pGrande.setColor(Tinte.TESTO);
        pGrande.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        // La riga di sotto: "mi collego", o il motivo per cui non parte. Un
        // Paint suo perche' e' un'altra cosa dal nome, e si deve vedere che lo
        // e' - piu' piccola, e rossa quando e' un guasto.
        pNota.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pComando.setStyle(Paint.Style.STROKE);
        pComando.setStrokeCap(Paint.Cap.ROUND);
        pComando.setStrokeJoin(Paint.Join.ROUND);

        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override public String titolo() { return "Radio"; }
    @Override public int tinta() { return Tinte.RADIO; }

    /** Un apparecchio radio. Contornata quando si e' altrove, piena quando si e' qui. */
    @Override public int icona()      { return Icone.RADIO; }
    @Override public int iconaPiena() { return Icone.RADIO_PIENA; }

    /** La radio arriva da fuori: e' la stessa che comanda la voce, non una
     *  seconda. Due lettori sullo stesso altoparlante suonerebbero insieme. */
    public void setRadio(Radio r) { radio = r; ricomponi(); }

    /** La cache dei loghi e' una sola per tutta l'app: due copie vorrebbero
     *  dire due volte la memoria per le stesse immagini. */
    public void setLoghi(Loghi l) { loghi = l; invalidate(); }

    /** Fuori scena si buttano i loghi: ricaricare un PNG costa qualche
     *  millisecondo, tenerlo in memoria costa per sempre. */
    @Override
    public void suUscita() {
        if (loghi != null) loghi.svuota();
    }

    /** La chiama chi riceve il cambio di stato: la sezione non fa da sola il
     *  giro per accorgersi che qualcosa e' cambiato. */
    public void risveglia() { invalidate(); }

    // ---- misure -----------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int vw, int vh) {
        super.onSizeChanged(w, h, vw, vh);
        misura(w, h);
    }

    /**
     * Rifa' le misure perche' l'elenco e' cambiato, non lo schermo.
     *
     * Da quando le stazioni le scrive il PC, quante sono e' una cosa che
     * cambia mentre Casa e' in funzione: le tessere sono tante quante le
     * stazioni, e senza questa chiamata resterebbero quante erano al momento
     * dell'ultimo cambio di misura - cioe' all'avvio. E' lo stesso motivo per
     * cui {@link SezioneLuci#ricomponi()} esiste.
     */
    public void ricomponi() {
        misura(getWidth(), getHeight());
        invalidate();
    }

    private void misura(int w, int h) {
        if (w <= 0 || h <= 0) return;
        float mg = m.margine;

        // Le tessere sono tante quante le stazioni di adesso. Si ricreano solo
        // quando il numero cambia: riordinare l'elenco non tocca le misure.
        int quante = radio != null ? radio.stazioni().size() : 0;
        if (tessere.length != quante) {
            tessere = new RectF[quante];
            rNomi = new Testo.Riga[quante];
            for (int i = 0; i < quante; i++) {
                tessere[i] = new RectF();
                rNomi[i] = new Testo.Riga();
            }
        }
        // La barra di riproduzione sta in fondo e ha altezza fissa: e' quella
        // che si tocca al buio, e non deve rimpicciolirsi quando le stazioni
        // aumentano. Si misura anche a elenco vuoto: il volume e il tasto di
        // avvio restano al loro posto, e una griglia senza tessere non e' una
        // schermata senza comandi.
        float altezzaBarra = h * 0.165f;
        barra.set(mg, h - mg - altezzaBarra, w - mg, h - mg);

        float cimaGriglia = h * 0.125f;
        float fondoGriglia = barra.top - mg;
        int righe = Math.max(1, (quante + COLONNE - 1) / COLONNE);

        float passoX = (w - mg * 2f) / COLONNE;
        float passoY = (fondoGriglia - cimaGriglia) / righe;
        float gap = mg * 0.45f;

        for (int i = 0; i < tessere.length; i++) {
            int col = i % COLONNE, riga = i / COLONNE;
            tessere[i].set(mg + passoX * col + gap / 2f,
                           cimaGriglia + passoY * riga + gap / 2f,
                           mg + passoX * (col + 1) - gap / 2f,
                           cimaGriglia + passoY * (riga + 1) - gap / 2f);
        }

        // Le tre bande della tessera - aria, logo, nome - si calcolano invece
        // di indovinarle. Prima il logo stava a una frazione fissa dell'altezza
        // e il nome a un'altra, e con ventidue stazioni le due frazioni si
        // sovrapponevano: il nome finiva sotto il logo.
        corpoNome = m.nota;
        pNome.setTextSize(corpoNome);
        float tessH = passoY - gap;
        float tessW = passoX - gap;
        float aria  = tessH * 0.09f;
        float altezzaNome = corpoNome * 1.35f;
        float banda = tessH - aria * 2f - altezzaNome;

        latoLogo = Math.min(banda, tessW * 0.34f);
        cyLogo   = aria + banda / 2f;              // dalla cima della tessera
        yNome    = tessH - aria;                   // base del testo
        larghezzaTessera = tessW * 0.94f;          // un filo di aria ai lati

        corpoTitolo = m.nota;
        pGrande.setTextSize(m.titolo);
        corpoOra    = m.titolo;
        corpoNota   = m.nota;
        pSigla.setTextSize(latoLogo * 0.42f);
        pTitolo.setTextSize(corpoTitolo);
        pOra.setTextSize(corpoOra);
        pNota.setTextSize(corpoNota);

        // I cinque comandi, in fila a destra dentro la barra. Ognuno e' largo
        // almeno quanto un dito: sotto quella misura si accende la stazione
        // sbagliata, e in cucina con le mani sporche capita.
        float lato = Math.max(m.bersaglio, barra.height() * 0.52f);
        float y = barra.centerY();
        float x = barra.right - m.margine - lato / 2f;
        for (int i = comandi.length - 1; i >= 0; i--) {
            comandi[i].set(x - lato / 2f, y - lato / 2f, x + lato / 2f, y + lato / 2f);
            x -= lato * 1.15f;
        }
        pComando.setStrokeWidth(Math.max(2f, lato * 0.055f));

        // Adesso che si sa dove cominciano i comandi, si sa anche quanto spazio
        // resta alla scritta. Prima era un numero di caratteri deciso a occhio,
        // che non teneva conto ne' dei comandi ne' del corpo del testo.
        bandaBarra.set(barra.left + m.margine,
                       barra.top + m.margine + corpoTitolo * 1.4f,
                       comandi[0].left - m.margine * 0.75f,
                       barra.bottom - m.margine * 0.5f);
        larghezzaBarra = Math.max(0f, bandaBarra.width());
    }

    // ---- disegno ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (vetro == null || !vetro.vivo()) return;

        // Il titolo grande, come nelle app di Apple: dice dove si e', e fa da
        // intestazione alla griglia senza un'etichetta in piu'.
        c.drawText("Radio", m.margine, getHeight() * 0.095f, pGrande);

        int acceso = radio != null ? radio.indiceCorrente() : -1;
        for (int i = 0; i < tessere.length; i++) {
            disegnaTessera(c, i, i == acceso, i == premuta);
        }
        disegnaBarra(c);
    }

    private void disegnaTessera(Canvas c, int i, boolean acceso, boolean premuta) {
        Radio.Stazione s = radio.stazioni().get(i);
        RectF t = tessere[i];

        // La tessera accesa diventa chiara, come una tessera accesa nella Casa
        // di Apple; le altre sono un riempimento piatto. Quale suona si vede da
        // un metro senza leggere, e senza tingere mezza schermata.
        pFondo.setColor(acceso ? Tinte.TESSERA_ACCESA
                : (premuta ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO));
        c.drawRoundRect(t, m.raggio, m.raggio, pFondo);

        float cx = t.centerX();
        float cy = t.top + cyLogo;

        Bitmap logo = loghi != null ? loghi.prendi(s.logo) : null;
        if (logo != null) {
            // Il fondo sotto il logo non e' sempre lo stesso, ed e' deciso a
            // monte misurando la luminosita' del marchio: una scritta nera su
            // trasparente sparirebbe sul fondo scuro, una bianca sparirebbe sul
            // chiaro. Ogni logo si porta dietro la risposta.
            float lato = latoLogo;
            tessLogo.set(cx - lato / 2f, cy - lato / 2f, cx + lato / 2f, cy + lato / 2f);
            // Il fondo del logo e' un'icona alla iOS: piena, angoli larghi.
            pDisco.setColor(s.logoSuChiaro ? 0xFFFFFFFF : 0xFF2C2C2E);
            c.drawRoundRect(tessLogo, lato * 0.22f, lato * 0.22f, pDisco);

            float dentro = lato * 0.86f;
            tessLogo.set(cx - dentro / 2f, cy - dentro / 2f, cx + dentro / 2f, cy + dentro / 2f);
            pLogo.setAlpha(255);
            c.drawBitmap(logo, null, tessLogo, pLogo);
        } else {
            // Tre stazioni su ventidue il logo non ce l'hanno. Per quelle il
            // disco con la sigla, che almeno le distingue.
            pDisco.setColor(acceso ? s.colore : Tinte.con(s.colore, 0x66));
            c.drawCircle(cx, cy, latoLogo / 2f, pDisco);

            // Tre lettere in un tondo largo quanto due vanno rimpicciolite, o
            // "RTL" esce dal disco.
            pSigla.setTextSize(latoLogo * (s.sigla.length() >= 3 ? 0.30f : 0.42f));
            pSigla.setColor(acceso ? 0xFF0B0F14 : Tinte.con(0xFF0B0F14, 0xCC));
            c.drawText(s.sigla, cx, cy - (pSigla.descent() + pSigla.ascent()) / 2f, pSigla);
        }

        pNome.setColor(acceso ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO);
        c.drawText(rNomi[i].in(pNome, s.nome, larghezzaTessera, corpoNome),
                   cx, t.top + yNome, pNome);
    }

    private void disegnaBarra(Canvas c) {
        boolean suona = radio != null && radio.staSuonando();
        int colore = suona && radio.stazioneCorrente() != null
                ? radio.stazioneCorrente().colore : Tinte.RADIO;

        // La scheda resta neutra anche quando suona: lo dice gia' il nome in
        // bianco pieno e la tessera chiara nella griglia.
        vetro.pannello(c, barra, m.raggio, colore, Tinte.VELO_QUIETO);

        float x = barra.left + m.margine;
        pTitolo.setColor(suona ? colore : Tinte.TESTO_TENUE);
        c.drawText(suona ? "In onda" : "Ferma", x, barra.top + m.margine + pTitolo.getTextSize(), pTitolo);

        // Quando una stazione non parte, lo si scrive. Il silenzio senza
        // spiegazione fa sembrare rotto il tablet invece della stazione.
        //
        // Il nome e lo stato stanno su due righe e non piu' su una sola. Prima
        // erano concatenati - "Radiofreccia - mi collego", "Radio Norba: non
        // risponde (oltre 15 secondi)" - e quella riga sola doveva essere
        // insieme grande abbastanza da leggersi da un metro e corta abbastanza
        // da non finire sui comandi. Non poteva essere tutte e due.
        String cosa, nota;
        int colTesto, colNota;
        if (radio == null) {
            cosa = "-";                 colTesto = Tinte.SPENTO;
            nota = null;                colNota  = Tinte.TESTO_TENUE;
        } else if (radio.staAprendo()) {
            cosa = radio.nomeCorrente(); colTesto = Tinte.TESTO_MEDIO;
            nota = "mi collego…";        colNota  = Tinte.TESTO_TENUE;
        } else if (suona) {
            cosa = radio.nomeCorrente(); colTesto = Tinte.TESTO;
            nota = null;                 colNota  = Tinte.TESTO_TENUE;
        } else if (radio.errore() != null) {
            cosa = "nessuna stazione";   colTesto = Tinte.SPENTO;
            nota = radio.errore();       colNota  = Tinte.ALLARME;
        } else {
            cosa = "nessuna stazione";   colTesto = Tinte.SPENTO;
            nota = null;                 colNota  = Tinte.TESTO_TENUE;
        }

        // Il nome prende il corpo che la fascia gli concede: se sotto c'e' una
        // riga di stato, ne resta di meno e si rimpicciolisce da solo invece di
        // uscire in basso.
        float passoNota = corpoNota * 1.3f;
        float spazioNome = bandaBarra.height() - (nota != null ? passoNota : 0f);
        // Il vuoto si scrive piccolo: « nessuna stazione » a corpo intero era
        // la scritta piu' grande della schermata, e diceva che non succede nulla.
        float tetto = (radio != null && (suona || radio.staAprendo())) ? corpoOra : corpoSotto();
        float massimoNome = Math.max(corpoNota, Math.min(tetto, spazioNome));
        float y = bandaBarra.top + massimoNome * 0.82f;

        pOra.setColor(colTesto);
        c.drawText(rNomeBarra.adatta(pOra, cosa, larghezzaBarra, massimoNome, massimoNome * 0.7f),
                   x, y, pOra);

        if (nota != null) {
            pNota.setColor(colNota);
            c.drawText(rNotaBarra.in(pNota, nota, larghezzaBarra, corpoNota),
                       x, y + passoNota, pNota);
        }

        for (int i = 0; i < comandi.length; i++) {
            disegnaComando(c, i, colore);
        }
    }

    /** Il corpo del « niente »: piu' piccolo del nome di una stazione. */
    private float corpoSotto() { return m.voce; }

    private void disegnaComando(Canvas c, int i, int colore) {
        RectF b = comandi[i];
        float cx = b.centerX(), cy = b.centerY();

        boolean suona = radio != null && radio.staSuonando();
        boolean principale = (i == 1);

        // Come il Centro di Controllo: il tasto di mezzo e' un cerchio bianco
        // pieno col segno nero, gli altri cerchi grigi col segno bianco. Quello
        // che si preme si trova senza cercarlo.
        float raggioTondo = b.width() * (principale ? 0.5f : 0.42f);
        if (principale) {
            pFondo.setColor(premutoComando == 1 ? 0xFFD1D1D6 : 0xFFFFFFFF);
        } else {
            pFondo.setColor(premutoComando == i ? Tinte.RIEMPIMENTO_SCELTO : Tinte.RIEMPIMENTO);
        }
        c.drawCircle(cx, cy, raggioTondo, pFondo);

        pComando.setColor(principale ? Tinte.TESTO_SU_CHIARO : Tinte.TESTO);
        pComando.setStyle(Paint.Style.STROKE);

        // Le icone sono quelle dei Material Symbols: gli stessi segni che si
        // vedono in ogni lettore, riconosciuti senza impararli. Prima erano
        // triangoli e altoparlanti disegnati a mano qui dentro, e fra questa
        // schermata e la Home i due « avanti » non erano lo stesso disegno.
        int segno;
        switch (i) {
            case 0:  segno = Icone.PRECEDENTE; break;
            case 1:  segno = suona ? Icone.FERMA : Icone.AVVIA; break;
            case 2:  segno = Icone.SUCCESSIVO; break;
            case 3:  segno = Icone.VOLUME_MENO; break;
            default: segno = Icone.VOLUME_PIU; break;
        }
        Icone.disegna(c, segno, cx, cy, b.width() * (principale ? 0.44f : 0.36f), pComando);
        pComando.setStyle(Paint.Style.STROKE);
    }

    // ---- tocco ------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                premuta = quale(tessere, e.getX(), e.getY());
                premutoComando = premuta >= 0 ? -1 : quale(comandi, e.getX(), e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                int t = quale(tessere, e.getX(), e.getY());
                int cmd = quale(comandi, e.getX(), e.getY());
                if (t >= 0 && t == premuta) suTessera(t);
                else if (cmd >= 0 && cmd == premutoComando) suComando(cmd);
                premuta = premutoComando = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                premuta = premutoComando = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void suTessera(int i) {
        if (radio == null) return;
        // Toccare la stazione che gia' suona la spegne: e' il gesto che tutti
        // provano per primo, e senza fa la figura del pulsante rotto.
        if (i == radio.indiceCorrente() && radio.staSuonando()) radio.spegni();
        else radio.accendi(radio.stazioni().get(i));
        invalidate();
    }

    private void suComando(int i) {
        if (radio == null) return;
        switch (i) {
            case 0: radio.precedente(); break;
            case 1: if (radio.staSuonando()) radio.spegni(); else radio.accendi(null); break;
            case 2: radio.successiva(); break;
            case 3: volume(AudioManager.ADJUST_LOWER); break;
            default: volume(AudioManager.ADJUST_RAISE); break;
        }
        invalidate();
    }

    private void volume(int direzione) {
        if (audio == null) return;
        // Due tacche per tocco: una sola su questo tablet non si sente, e si
        // finisce per premere sei volte.
        for (int k = 0; k < 2; k++) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direzione, 0);
        }
    }

    private static int quale(RectF[] aree, float x, float y) {
        for (int i = 0; i < aree.length; i++) if (aree[i].contains(x, y)) return i;
        return -1;
    }
}
