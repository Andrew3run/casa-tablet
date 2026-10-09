package dev.casa;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

/**
 * Il telaio di Casa: lo sfondo, la barra a sinistra, la sezione in scena.
 *
 * E' l'unico posto dove sta il layout. Se un giorno la barra dovesse andare in
 * basso invece che a sinistra, si cambia {@link #onLayout} e basta: le sezioni
 * non sanno dove sono.
 *
 * <b>La dissolvenza e' un fade-through, non un cross-fade.</b> La sezione che
 * esce va a zero in 90 ms, poi quella che entra sale da zero in 90 ms, con
 * dodici pixel di traslazione. Due ragioni: il cross-fade puro ha un istante di
 * doppia esposizione grigiastra in mezzo, e - soprattutto - cosi' <b>una sola
 * View alla volta ha alpha minore di uno</b>. Insieme a
 * {@code hasOverlappingRendering() == false} nelle sezioni, questo fa si' che
 * la transizione non allochi nessun livello fuori schermo: su un Mali-400 con
 * 1 GB, un layer da 3,9 MB per una transizione da 180 ms sarebbe il genere di
 * spesa che non si vede finche' non manca la memoria a qualcun altro.
 *
 * La tinta dell'ambiente viaggia sullo <b>stesso</b> animatore: entrando in
 * Radio il vetro vira al verde mentre il contenuto cambia, non dopo.
 */
public class Telaio extends ViewGroup {

    /**
     * Quello che una vela deve saper fare.
     *
     * Prima il telaio riconosceva la sveglia con un {@code instanceof} in
     * quattro punti, e andava bene finche' la vela era una sola. Con la
     * seconda - il pannello delle impostazioni - i punti sarebbero diventati
     * otto, e il primo che si dimentica e' quello che lascia il microfono in
     * mano a una vela che non c'e' piu'.
     */
    public interface Velata {
        /** Il vetro puo' arrivare, cambiare o sparire mentre la vela e' in
         *  scena: si compone su un altro thread e onTrimMemory lo libera. */
        void setVetro(Vetro v);

        /**
         * Sta uscendo di scena, comunque sia successo - il suo tasto, il
         * tasto indietro, un'altra vela che le va sopra.
         *
         * E' l'unico posto in cui una vela puo' restituire quello che si e'
         * presa. Il pannello delle impostazioni tiene il microfono aperto:
         * senza questa chiamata, uscire col tasto indietro lo lascerebbe
         * aperto per sempre, e nessuno saprebbe perche' la parola di
         * attivazione ha smesso di funzionare.
         */
        void suVelaTolta();
    }

    private static final int META_DISSOLVENZA = 90;

    private final Misure m;
    private final Sezione[] sezioni;
    private final BarraSezioni barra;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Vetro vetro;
    private int corrente = 0;
    private View vela;

    private ValueAnimator transizione;

    /**
     * Un vetro alla volta.
     *
     * onCreate e onResume arrivano a mezzo secondo l'uno dall'altro e
     * chiedevano il vetro tutti e due: due thread componevano due immagini da
     * 1280x800 - e su API 24 i pixel di una Bitmap stanno nell'heap Java,
     * quindi otto megabyte invece di quattro - e una delle due finiva riciclata
     * appena nata. Nei log si leggeva bene: "sfocatura in 75 ms" e subito dopo
     * "sfocatura in 7 ms".
     */
    private boolean vetroInCorso;

    public Telaio(Context c, Misure misure, Sezione[] sezioni) {
        super(c);
        this.m = misure;
        this.sezioni = sezioni;

        // Il fondo lo dipinge questa ViewGroup: senza, onDraw non verrebbe
        // nemmeno chiamata.
        setWillNotDraw(false);
        setBackgroundColor(Tinte.FONDO);

        barra = new BarraSezioni(c, misure, sezioni);
        barra.setCambio(new BarraSezioni.Cambio() {
            @Override public void suSezione(int indice) { vaiA(indice); }
        });
        addView(barra);

        for (int i = 0; i < sezioni.length; i++) {
            sezioni[i].setVisibility(i == 0 ? VISIBLE : GONE);
            addView(sezioni[i]);
        }
    }

    // ---- vetro ------------------------------------------------------------

    /**
     * Compone e sfoca lo sfondo fuori dal thread dell'interfaccia.
     *
     * Il picco e' di circa 8 MB per meno di mezzo secondo - l'immagine grande
     * piu' il contesto RenderScript - e finirebbe dritto sul primo fotogramma
     * se lo si facesse qui. Finche' non e' pronto, si vede il fondo piatto:
     * mezzo secondo di tinta unita al posto di mezzo secondo di schermo fermo.
     */
    public void preparaVetro(final Context ctx) {
        if (vetroInCorso) return;
        vetroInCorso = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final Vetro v = Vetro.crea(ctx, m, Sfondo.variante(System.currentTimeMillis()));
                ui.post(new Runnable() {
                    @Override public void run() {
                        adotta(v);
                        vetroInCorso = false;
                    }
                });
            }
        }, "Casa-vetro").start();
    }

    private void adotta(Vetro v) {
        if (vetro != null) vetro.rilascia();
        vetro = v;
        if (getWidth() > 0) vetro.adattaA(getWidth(), getHeight());
        vetro.setTinta(sezioni[corrente].tinta());
        barra.setVetro(vetro);
        for (Sezione s : sezioni) s.setVetro(vetro);
        // Anche la vela, se c'e': una sveglia puo' scattare prima che il vetro
        // sia pronto - il vetro si compone su un altro thread e ci mette un
        // decimo di secondo - e senza questa riga resterebbe una schermata
        // nera con due tasti invisibili per il resto della suonata.
        if (vela instanceof Velata) ((Velata) vela).setVetro(vetro);
        invalidate();
    }

    /** Chiamata da onTrimMemory: 64 KB non sono niente, ma rifarli costa venti
     *  millisecondi e nel frattempo Casa non e' nemmeno sullo schermo. */
    public void liberaVetro() {
        if (vetro == null) return;
        vetro.rilascia();
        vetro = null;
        barra.setVetro(null);
        for (Sezione s : sezioni) s.setVetro(null);
        if (vela instanceof Velata) ((Velata) vela).setVetro(null);
    }

    public boolean senzaVetro() { return vetro == null || !vetro.vivo(); }

    public Vetro vetro() { return vetro; }

    // ---- navigazione ------------------------------------------------------

    public int corrente() { return corrente; }

    public Sezione sezione(int i) { return sezioni[i]; }

    /** Va alla sezione con questa chiave, se c'e'. Serve alla voce: "casa, apri
     *  la radio". */
    public boolean vaiA(String titolo) {
        for (int i = 0; i < sezioni.length; i++) {
            if (sezioni[i].titolo().equalsIgnoreCase(titolo)) { vaiA(i); return true; }
        }
        return false;
    }

    public void vaiA(final int indice) {
        if (indice == corrente || indice < 0 || indice >= sezioni.length) return;
        if (transizione != null) transizione.cancel();

        final Sezione esce  = sezioni[corrente];
        final Sezione entra = sezioni[indice];
        final int tintaDa = esce.tinta();
        final int tintaA  = entra.tinta();

        corrente = indice;
        barra.setCorrente(indice);

        transizione = ValueAnimator.ofFloat(0f, 2f);
        transizione.setDuration(META_DISSOLVENZA * 2);
        transizione.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                if (t <= 1f) {
                    esce.setAlpha(1f - t);
                } else {
                    if (esce.getVisibility() == VISIBLE) {
                        esce.setVisibility(GONE);
                        esce.setAlpha(1f);
                        esce.suUscita();
                        entra.setAlpha(0f);
                        entra.setVisibility(VISIBLE);
                        entra.suEntrata();
                    }
                    float q = t - 1f;
                    entra.setAlpha(q);
                    entra.setTranslationY(m.dp(12) * (1f - q));
                }
                if (vetro != null) {
                    vetro.setTinta(Tinte.fondi(tintaDa, tintaA, t / 2f));
                    invalidate();
                    barra.invalidate();
                }
            }
        });
        transizione.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                entra.setAlpha(1f);
                entra.setTranslationY(0f);
                if (vetro != null) vetro.setTinta(tintaA);
            }
        });
        transizione.start();
    }

    public void setSpia(int indice, boolean accesa) { barra.setSpia(indice, accesa); }

    // ---- la vela ----------------------------------------------------------

    /**
     * Mette una schermata sopra tutto: la sveglia che suona, il dettaglio di una
     * lampada.
     *
     * Sopra il telaio e non in un'altra Activity. Con il lock task attivo,
     * lanciare una seconda Activity vuole che il pacchetto stia nei
     * lockTaskPackages e che quella entri nel task bloccato: e' una scommessa
     * inutile, quando Casa e' la Home e non esiste il caso in cui non sia gia'
     * in primo piano.
     */
    public void mostra(View nuovaVela) {
        nascondiVela();
        if (nuovaVela instanceof Velata) ((Velata) nuovaVela).setVetro(vetro);
        vela = nuovaVela;
        addView(vela);
        vela.setAlpha(0f);
        vela.animate().alpha(1f).setDuration(140).start();
        requestLayout();
    }

    public void nascondiVela() {
        if (vela == null) return;
        View usciva = vela;
        vela = null;
        removeView(usciva);
        // Dopo il removeView e con il campo gia' a null: una vela che
        // restituendo le sue cose ne aprisse un'altra non deve trovarsi
        // rimossa subito dopo da questa stessa chiamata.
        if (usciva instanceof Velata) ((Velata) usciva).suVelaTolta();
    }

    public boolean ceUnaVela() { return vela != null; }

    /** Indietro: prima la vela, poi quello che la sezione ha aperto, poi Home. */
    public boolean indietro() {
        if (vela != null) {
            // La sveglia che suona fa eccezione: si zittisce con il suo tasto e
            // basta. Un indietro - o una manica sul bordo dello schermo - non
            // deve poter spegnere una sveglia, e questa e' la stessa ragione
            // per cui il tocco a vuoto sulla vela non fa niente.
            if (vela instanceof VelaAllarme) return true;
            nascondiVela();
            return true;
        }
        if (sezioni[corrente].suIndietro()) return true;
        if (corrente != 0) { vaiA(0); return true; }
        return false;
    }

    // ---- disegno e misura -------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (vetro != null && vetro.vivo()) {
            vetro.disegnaSfondo(c, getWidth(), getHeight());
        }
    }

    @Override
    protected void onMeasure(int specW, int specH) {
        int w = MeasureSpec.getSize(specW);
        int h = MeasureSpec.getSize(specH);

        barra.measure(MeasureSpec.makeMeasureSpec(m.barra, MeasureSpec.EXACTLY),
                      MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));

        int larghezzaSezione = Math.max(0, w - m.barra);
        int specSezioneW = MeasureSpec.makeMeasureSpec(larghezzaSezione, MeasureSpec.EXACTLY);
        int specSezioneH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (Sezione s : sezioni) s.measure(specSezioneW, specSezioneH);

        // La vela copre tutto, barra compresa: quando suona una sveglia non
        // deve esserci nient'altro da toccare.
        if (vela != null) {
            vela.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                         MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean cambiato, int sinistra, int alto, int destra, int basso) {
        int w = destra - sinistra, h = basso - alto;
        // La finestra puo' essere piu' alta di quanto Misure prevedesse, per
        // esempio mentre la barra di sistema va e viene. Il vetro si adatta a
        // quello che c'e' davvero invece di stirarsi.
        if (vetro != null && vetro.vivo()) vetro.adattaA(w, h);
        barra.layout(0, 0, m.barra, h);
        for (Sezione s : sezioni) s.layout(m.barra, 0, w, h);
        if (vela != null) vela.layout(0, 0, w, h);
    }
}
