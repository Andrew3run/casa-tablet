package dev.casa.telefono;

import android.view.View;

import org.json.JSONObject;

/**
 * Una schermata dell'app. La cornice ({@link Principale}) le da' la rete, lo
 * stile e un posto; lei disegna il suo contenuto e risponde a quattro cose:
 * lo stato che arriva ogni cinque secondi, l'entrata, l'uscita e il battito
 * del secondo mentre e' davanti.
 */
abstract class Pagina {

    final Principale app;
    final Stile s;

    Pagina(Principale app) {
        this.app = app;
        this.s = app.s;
    }

    abstract String titolo();

    /** Costruita una volta sola, all'avvio. */
    abstract View vista();

    /** Lo stato di /stato, ogni cinque secondi, anche quando non e' davanti:
     *  chi ci torna trova la schermata gia' giusta. */
    void stato(JSONObject st) { }

    void entra() { }

    void esce() { }

    /** Ogni secondo, solo mentre e' davanti e l'app e' aperta. */
    void secondo() { }

    /** Il tasto indietro: true se l'ha usato lei (una pagina dentro la pagina). */
    boolean indietro() { return false; }

    /** Toccata di nuovo la sua scheda: si torna in cima. */
    void inCima() { }
}
