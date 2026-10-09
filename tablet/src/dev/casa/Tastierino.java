package dev.casa;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * La tastiera di Casa: quattro righe, disegnate a mano.
 *
 * <b>Perche' non quella di sistema.</b> Su questo tablet la tastiera di Android
 * si prende <b>meta' schermo</b> - 390 pixel su 800 - e sopra ne restavano
 * quattro righe di risultati; e' fatta per scrivere messaggi, non per battere
 * cinque lettere e guardare un elenco. Questa ne occupa <b>un terzo scarso</b>,
 * lascia il resto ai risultati, e non porta con se' nient'altro: niente
 * suggerimenti, niente emoji, niente scorciatoia alle impostazioni - che su un
 * apparecchio da muro in chiosco sono anche una porta aperta.
 *
 * <b>Non serve altro.</b> Qui si scrive il nome di una canzone: lettere, uno
 * spazio, un tasto per cancellare. Niente maiuscole (la ricerca di Spotify non
 * le guarda), niente accenti, niente numeri. Ogni tasto in piu' e' un tasto
 * piu' piccolo per tutti gli altri.
 *
 * Le lettere stanno nell'ordine di una tastiera vera - QWERTY - e non in ordine
 * alfabetico: chi guarda cerca la lettera dove si aspetta di trovarla.
 */
public final class Tastierino {

    /** Quello che un tocco produce. */
    public static final int NIENTE = -1, CANCELLA = -2, SPAZIO = -3, CHIUDI = -4;

    private static final String[] RIGHE = { "qwertyuiop", "asdfghjkl", "zxcvbnm" };

    /** L'area di ogni tasto delle tre righe di lettere. */
    private final RectF[][] tasti = new RectF[RIGHE.length][];

    /** Cancella, spazio, chiudi. */
    private final RectF cancella = new RectF(), spazio = new RectF(), chiudi = new RectF();

    private final RectF area = new RectF();
    private final Paint pTasto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLettera = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSegno = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float raggio, corpo;
    private int premutoR = -1, premutoC = -1;
    private int premutoSpeciale = NIENTE;

    public Tastierino() {
        for (int r = 0; r < RIGHE.length; r++) {
            tasti[r] = new RectF[RIGHE[r].length()];
            for (int c = 0; c < tasti[r].length; c++) tasti[r][c] = new RectF();
        }
        pLettera.setTextAlign(Paint.Align.CENTER);
        pLettera.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        pSegno.setStyle(Paint.Style.STROKE);
        pSegno.setStrokeCap(Paint.Cap.ROUND);
        pSegno.setStrokeJoin(Paint.Join.ROUND);
    }

    public RectF area() { return area; }

    /**
     * Dispone i tasti dentro l'area data.
     *
     * Le righe corte - « asdfghjkl » ne ha nove, « zxcvbnm » sette - restano
     * <b>centrate</b> e con tasti larghi uguali a quelli della prima: e' cosi'
     * che sono fatte le tastiere vere, ed e' quello che rende riconoscibile la
     * posizione di una lettera senza leggerla.
     */
    public void misura(Misure m, float sinistra, float alto, float destra, float basso) {
        area.set(sinistra, alto, destra, basso);
        float vuoto = m.dp(4);
        float altaRiga = area.height() / 4f;
        float larga = (area.width() - vuoto * (RIGHE[0].length() + 1)) / RIGHE[0].length();
        raggio = Math.min(larga, altaRiga) * 0.16f;
        corpo = altaRiga * 0.42f;
        pLettera.setTextSize(corpo);
        pSegno.setStrokeWidth(Math.max(2f, corpo * 0.09f));

        for (int r = 0; r < RIGHE.length; r++) {
            int quanti = RIGHE[r].length();
            float larghezzaRiga = quanti * larga + (quanti - 1) * vuoto;
            float x = area.left + (area.width() - larghezzaRiga) / 2f;
            float y = area.top + altaRiga * r;
            for (int c = 0; c < quanti; c++) {
                tasti[r][c].set(x, y + vuoto / 2f, x + larga, y + altaRiga - vuoto / 2f);
                x += larga + vuoto;
            }
        }

        // L'ultima riga: cancella a sinistra, spazio in mezzo, chiudi a destra.
        // Lo spazio e' largo il doppio di una lettera perche' e' il tasto che si
        // preme senza guardare.
        float y = area.top + altaRiga * 3f;
        float alta = altaRiga - vuoto;
        float lato = Math.max(larga * 1.6f, m.bersaglio);
        cancella.set(area.left + vuoto, y + vuoto / 2f, area.left + vuoto + lato, y + alta);
        chiudi.set(area.right - vuoto - lato, y + vuoto / 2f, area.right - vuoto, y + alta);
        spazio.set(cancella.right + vuoto * 2f, y + vuoto / 2f,
                   chiudi.left - vuoto * 2f, y + alta);
    }

    public void disegna(Canvas c, Vetro vetro, Misure m, int tinta) {
        for (int r = 0; r < RIGHE.length; r++) {
            for (int col = 0; col < tasti[r].length; col++) {
                boolean premuto = (r == premutoR && col == premutoC);
                disegnaTasto(c, tasti[r][col], LETTERA, premuto);
                pLettera.setColor(Tinte.TESTO);
                RectF t = tasti[r][col];
                c.drawText(String.valueOf(RIGHE[r].charAt(col)), t.centerX(),
                           t.centerY() - (pLettera.descent() + pLettera.ascent()) / 2f, pLettera);
            }
        }

        disegnaTasto(c, cancella, SPECIALE, premutoSpeciale == CANCELLA);
        disegnaTasto(c, spazio, LETTERA, premutoSpeciale == SPAZIO);
        disegnaTasto(c, chiudi, SPECIALE, premutoSpeciale == CHIUDI);

        // Cancella: una freccia a sinistra con la crocetta dentro.
        pSegno.setColor(Tinte.TESTO);
        float cx = cancella.centerX(), cy = cancella.centerY(), r = corpo * 0.5f;
        c.drawLine(cx - r, cy, cx + r * 0.4f, cy, pSegno);
        c.drawLine(cx - r, cy, cx - r * 0.35f, cy - r * 0.55f, pSegno);
        c.drawLine(cx - r, cy, cx - r * 0.35f, cy + r * 0.55f, pSegno);

        // Spazio: una riga sottile, come su tutte le tastiere.
        c.drawLine(spazio.centerX() - corpo, spazio.centerY() + corpo * 0.25f,
                   spazio.centerX() + corpo, spazio.centerY() + corpo * 0.25f, pSegno);

        // Chiudi: la freccia in giu'.
        cx = chiudi.centerX(); cy = chiudi.centerY();
        c.drawLine(cx - r * 0.7f, cy - r * 0.3f, cx, cy + r * 0.45f, pSegno);
        c.drawLine(cx + r * 0.7f, cy - r * 0.3f, cx, cy + r * 0.45f, pSegno);
    }

    /** I tasti della tastiera scura di iOS: le lettere piu' chiare, i tasti
     *  speciali piu' scuri, e il premuto si schiarisce. Fondi piatti, non
     *  vetro: trenta pastiglie di vetro erano trenta fette d'alone diverse. */
    private static final int LETTERA  = 0x3DFFFFFF;
    private static final int SPECIALE = 0x1FFFFFFF;
    private static final int PREMUTO  = 0x66FFFFFF;

    private void disegnaTasto(Canvas c, RectF dove, int fondo, boolean premuto) {
        pTasto.setColor(premuto ? PREMUTO : fondo);
        c.drawRoundRect(dove, raggio, raggio, pTasto);
    }

    /** Segna quale tasto e' sotto il dito, per illuminarlo. */
    public void premi(float x, float y) {
        premutoR = premutoC = -1;
        premutoSpeciale = NIENTE;
        for (int r = 0; r < RIGHE.length; r++) {
            for (int col = 0; col < tasti[r].length; col++) {
                if (tasti[r][col].contains(x, y)) { premutoR = r; premutoC = col; return; }
            }
        }
        if (cancella.contains(x, y)) premutoSpeciale = CANCELLA;
        else if (spazio.contains(x, y)) premutoSpeciale = SPAZIO;
        else if (chiudi.contains(x, y)) premutoSpeciale = CHIUDI;
    }

    public void lascia() {
        premutoR = premutoC = -1;
        premutoSpeciale = NIENTE;
    }

    /**
     * Che cosa e' stato premuto: il codice di un carattere, oppure una delle
     * costanti. {@link #NIENTE} se il dito e' finito fra due tasti.
     */
    public int tocco(float x, float y) {
        for (int r = 0; r < RIGHE.length; r++) {
            for (int col = 0; col < tasti[r].length; col++) {
                if (tasti[r][col].contains(x, y)) return RIGHE[r].charAt(col);
            }
        }
        if (cancella.contains(x, y)) return CANCELLA;
        if (spazio.contains(x, y)) return SPAZIO;
        if (chiudi.contains(x, y)) return CHIUDI;
        return NIENTE;
    }
}
