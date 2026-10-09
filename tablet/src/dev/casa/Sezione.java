package dev.casa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Una delle sei schermate di Casa.
 *
 * <b>Perche' una View vera e non un "pittore" dentro una View sola.</b> La
 * seconda strada consumerebbe meno, ma Radio, App e Luci sono griglie con
 * scorrimento e stati di pressione: riscrivere a mano l'individuazione del
 * tocco e lo scorrimento dentro un pittore unico e' codice che il framework
 * regala. La disciplina resta comunque quella del progetto: ogni sezione e'
 * <b>una</b> View disegnata a mano, non un albero di widget.
 *
 * <b>hasOverlappingRendering() torna false</b>, e non e' un dettaglio. Con
 * quel false il framework applica l'alpha direttamente a ogni operazione di
 * disegno invece di allocare un livello fuori schermo: la dissolvenza fra
 * sezioni, che altrimenti costerebbe una texture da 1280x800x4 = 3,9 MB in
 * GPU, non costa niente. In cambio, per i 90 ms della transizione, le parti
 * che si sovrappongono (il testo sopra il vetro) compongono l'alpha due volte
 * e sono impercettibilmente piu' chiare. Su questo hardware e' lo scambio
 * giusto.
 */
public abstract class Sezione extends View {

    protected final Misure m;
    protected Vetro vetro;

    public Sezione(Context c, Misure misure) {
        super(c);
        this.m = misure;
        // Senza clickable Android non consegna ACTION_DOWN, e senza il DOWN non
        // arriva mai l'UP: la View sembra morta al tocco. Costato mezza giornata
        // la prima volta, sul microfono.
        setClickable(true);
    }

    /** Come si chiama nella barra. */
    public abstract String titolo();

    /** Il colore che tinge l'ambiente mentre questa sezione e' in scena. */
    public abstract int tinta();

    /**
     * La sua icona nella barra, contornata: un indice di {@link Icone}.
     *
     * Prima ogni sezione se la disegnava con {@code drawLine} e
     * {@code drawCircle}, e si vedeva - sei disegni con sei spessori e sei idee
     * di quanto grande fosse un'icona. Adesso e' un numero, e il disegno lo fa
     * chi le icone le sa fare.
     */
    public abstract int icona();

    /** La stessa icona piena, per quando questa sezione e' quella in scena. E'
     *  la convenzione di Android e di iOS per dire « sei qui » senza scrivere
     *  niente. Chi non ne ha una usa la stessa. */
    public int iconaPiena() { return icona(); }

    /**
     * Il colore fisso dell'icona, o zero se deve seguire la sezione.
     *
     * Ce l'ha solo Spotify: un marchio si disegna del suo colore o non lo si
     * disegna, e tingerlo del viola dell'ambiente vorrebbe dire avere un logo
     * Spotify viola, che e' peggio di non averlo.
     */
    public int coloreIcona() { return 0; }

    /**
     * Quando e' cominciata l'entrata in scena, per le animazioni di ingresso.
     * Zero vuol dire « ferma »: le tessere stanno dove devono, senza muoversi.
     */
    protected long entrata;

    /** Entra in scena: qui si riagganciano gli handler e si rileggono i dati. */
    public void suEntrata() { entrata = Anima.ora(); }

    /**
     * Esce di scena: qui si stacca <b>tutto</b> e si rilasciano le bitmap.
     *
     * Ricaricare l'icona di un'app da PackageManager costa cinque millisecondi;
     * tenerla in memoria costa per sempre. Su 1 GB, con Spotify e Netflix
     * accanto, vince il ricaricare.
     */
    public void suUscita() { }

    /** true se ha consumato l'indietro chiudendo qualcosa di suo. */
    public boolean suIndietro() { return false; }

    public void setVetro(Vetro v) {
        vetro = v;
        invalidate();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
