package dev.casa;

import android.view.View;

/**
 * Le animazioni di Casa, senza oggetti che si animano.
 *
 * <h3>Perche' non ValueAnimator</h3>
 *
 * Un {@code ValueAnimator} per ogni cosa che si muove vuol dire un oggetto,
 * un listener, una registrazione nel {@code Choreographer} e una callback per
 * fotogramma - per <i>ognuno</i>. Su una schermata con dieci tessere che si
 * accendono sono dieci animatori vivi, e su 1 GB di RAM la parte cara non e' il
 * calcolo ma la spazzatura che lascia dietro.
 *
 * Qui il tempo e' <b>un numero solo</b>: il momento in cui e' cominciato
 * qualcosa. Chi disegna chiede « a che punto siamo » e ottiene un valore fra
 * zero e uno; finche' non e' uno, si richiede un altro fotogramma. Nessun
 * oggetto per elemento, nessuna callback, e la View si ferma da sola quando ha
 * finito - che e' la parte che conta su uno schermo acceso sedici ore al
 * giorno.
 *
 * <h3>Le curve</h3>
 *
 * Tre, e bastano. <b>{@link #posa}</b> parte veloce e si ferma piano: e' come
 * si muovono le cose che arrivano, ed e' quella giusta nove volte su dieci.
 * <b>{@link #dolce}</b> parte e finisce piano: serve a quel che si sposta
 * restando in scena, come un indicatore che scorre. <b>{@link #molla}</b>
 * supera di poco e torna: e' la sola che si nota, e proprio per questo si usa
 * solo dove qualcosa deve <i>comparire</i>, mai su cose che si muovono spesso.
 *
 * <h3>Lo sfasamento</h3>
 *
 * Quando entra una schermata le sue tessere non arrivano insieme: la seconda
 * parte quaranta millisecondi dopo la prima. E' quello che fa sembrare
 * l'interfaccia <b>costruita</b> invece che accesa tutta insieme, e costa un
 * moltiplicatore. Ma lo sfasamento e' <b>limitato</b>: oltre un certo numero di
 * elementi si smette di ritardare, se no l'ultima tessera di una griglia da
 * ventidue arriverebbe un secondo dopo la prima e la schermata sembrerebbe
 * lenta invece che viva.
 */
public final class Anima {

    private Anima() {}

    /** Quanto dura l'ingresso di un elemento. Corto: e' un modo di entrare,
     *  non uno spettacolo. */
    public static final int ENTRATA = 300;

    /** Il ritardo fra un elemento e il successivo. */
    public static final int SFASAMENTO = 38;

    /** Oltre questo numero di elementi non si ritarda piu': l'ultimo arriva
     *  insieme al quattordicesimo. */
    private static final int MAX_SFASATI = 14;

    /** Quanto dura il passaggio di uno stato: premuto, acceso, spento. */
    public static final int STATO = 160;

    // ---- il tempo ----------------------------------------------------------

    /** Adesso, nella stessa base di tempo delle animazioni. Monotono: non lo
     *  sposta il cambio dell'ora, che qui succede davvero - il fuso di questo
     *  tablet arriva sbagliato di fabbrica. */
    public static long ora() {
        return android.os.SystemClock.uptimeMillis();
    }

    /**
     * A che punto e' l'ingresso dell'elemento numero {@code i}, da 0 a 1.
     *
     * @param inizio quando e' cominciata l'entrata, da {@link #ora()}
     */
    public static float entrata(long inizio, int i) {
        if (inizio <= 0L) return 1f;
        long t = ora() - inizio - (long) Math.min(i, MAX_SFASATI) * SFASAMENTO;
        if (t <= 0L) return 0f;
        if (t >= ENTRATA) return 1f;
        return t / (float) ENTRATA;
    }

    /** true se c'e' ancora qualcosa da muovere, e quindi serve un altro
     *  fotogramma. */
    public static boolean inCorso(long inizio, int quanti) {
        if (inizio <= 0L) return false;
        long fine = (long) Math.min(Math.max(quanti - 1, 0), MAX_SFASATI) * SFASAMENTO + ENTRATA;
        return ora() - inizio < fine;
    }

    /** Chiede un altro fotogramma se serve. Postata sull'orologio del disegno,
     *  non su un Handler: cosi' il fotogramma successivo arriva quando lo
     *  schermo e' pronto, e non prima. */
    public static void continua(View v, long inizio, int quanti) {
        if (inCorso(inizio, quanti)) v.postInvalidateOnAnimation();
    }

    // ---- le curve ----------------------------------------------------------

    /** Parte veloce, si posa. La curva di quel che arriva. */
    public static float posa(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Parte piano e finisce piano. La curva di quel che si sposta. */
    public static float dolce(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /**
     * Supera di poco e torna. La curva di quel che compare.
     *
     * Il rimbalzo e' piccolo apposta - circa il tre per cento - perche' su un
     * apparecchio appeso al muro un'animazione che gigioneggia si nota la prima
     * volta e da' fastidio la centesima.
     */
    public static float molla(float t) {
        if (t >= 1f) return 1f;
        float u = 1f - t;
        return 1f - u * u * (float) Math.cos(t * 4.2f) * 1.0f;
    }

    // ---- gli aiuti ---------------------------------------------------------

    /** Da a a b secondo l'avanzamento. */
    public static float fra(float a, float b, float quanto) {
        return a + (b - a) * quanto;
    }

    /**
     * Un valore che insegue un bersaglio.
     *
     * Serve alle cose che non hanno un inizio e una fine - un cursore che segue
     * il dito, l'opacita' di un tasto premuto - dove un'animazione con una
     * durata sarebbe sempre quella sbagliata perche' il bersaglio cambia mentre
     * ci si sta andando. Qui invece si va sempre verso l'ultimo bersaglio, e
     * cambiarlo a meta' strada non fa saltare niente.
     *
     * Non alloca e non tiene handler: e' due float e un istante.
     */
    public static final class Inseguito {

        private float valore, bersaglio;
        private long ultimo;
        private final float tempo;      // costante di tempo, in millisecondi

        public Inseguito(float partenza, float millisecondi) {
            valore = bersaglio = partenza;
            tempo = Math.max(1f, millisecondi);
        }

        public void vaiA(float b) { bersaglio = b; }

        /** Portalo avanti fino ad adesso. @return true se si sta ancora
         *  muovendo, cioe' se serve un altro fotogramma. */
        public boolean passo() {
            long adesso = ora();
            if (ultimo == 0L) { ultimo = adesso; }
            float dt = Math.min(64f, adesso - ultimo);   // un salto lungo non teletrasporta
            ultimo = adesso;
            if (Math.abs(bersaglio - valore) < 0.002f) { valore = bersaglio; return false; }
            valore += (bersaglio - valore) * (1f - (float) Math.exp(-dt / tempo));
            return true;
        }

        public float valore() { return valore; }

        /** Mettilo li' senza animazione: quando la schermata non e' in scena,
         *  animare vorrebbe dire far vedere all'utente un movimento che e'
         *  gia' finito prima che guardasse. */
        public void subito(float v) { valore = bersaglio = v; ultimo = 0L; }
    }
}
