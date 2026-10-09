package dev.casa;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

/**
 * Abbassa tutto il resto mentre Casa ascolta o parla.
 *
 * <b>Perche' serve.</b> Con la radio accesa, premere il microfono non serviva a
 * niente: il microfono del tablet e' a venti centimetri dall'altoparlante del
 * tablet, e quello che sentiva era la radio. Su questo hardware non c'e'
 * cancellazione d'eco, quindi non e' un problema che si risolve elaborando: si
 * risolve abbassando la sorgente.
 *
 * <b>Due leve, perche' i casi sono due.</b>
 *
 * Quello che suona Casa - la radio - lo abbassiamo <b>noi</b>, direttamente sul
 * MediaPlayer: e' immediato, e' esatto, e non dipende dalla buona volonta' di
 * nessuno.
 *
 * Quello che suona qualcun altro - Spotify, Netflix - si chiede col <b>focus
 * audio</b>, `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`. E' l'unica leva che
 * esiste su un'app di un altro, ed e' anche quella giusta: chi la rispetta si
 * abbassa e torna su da solo, e chi non la rispetta almeno non viene messo in
 * pausa a tradimento.
 *
 * <b>MAY_DUCK e non TRANSIENT.</b> TRANSIENT metterebbe Spotify in pausa vera,
 * e per un comando di tre secondi si perderebbe il filo del brano. Abbassare e
 * rialzare non si nota; una pausa si nota.
 */
public final class Sordina {

    /** Quanto resta della radio mentre Casa ascolta. Non zero: sentire che c'e'
     *  ancora, appena appena, dice che non si e' spenta da sola. */
    private static final float RESIDUO = 0.10f;

    /** Chi sa abbassare quello che Casa stessa sta suonando. */
    public interface Nostra {
        void abbassaVolume(float quanto);
        void rialzaVolume();
    }

    /** Quante sorgenti di Casa ci possono essere. Sono due - la radio e la
     *  musica - e non c'e' motivo di allocare una lista per due. */
    private static final int QUANTE = 4;

    private final AudioManager audio;
    private final AudioManager.OnAudioFocusChangeListener nulla =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override public void onAudioFocusChange(int cambio) {
                    // Casa non ha niente da abbassare quando lo chiede un altro:
                    // la radio la governa lei, e la voce dura tre secondi. Il
                    // listener esiste solo perche' requestAudioFocus lo pretende.
                }
            };

    private final Nostra[] nostre = new Nostra[QUANTE];
    private int quante;
    private boolean giu;

    public Sordina(Context c) {
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Aggiunge una sorgente di Casa da abbassare.
     *
     * Ce n'e' piu' d'una da quando la musica di Spotify gira dentro Casa
     * invece che dentro l'app di Spotify: il focus audio non la tocca piu' -
     * e' roba nostra - quindi va abbassata direttamente come la radio. Chi non
     * ne registra nessuna resta col solo focus, e funziona uguale.
     */
    public void aggiungi(Nostra n) {
        if (n == null || quante >= QUANTE) return;
        for (int i = 0; i < quante; i++) if (nostre[i] == n) return;
        nostre[quante++] = n;
    }

    /** Casa sta per ascoltare o per parlare: tutti giu'. */
    public void giu() {
        if (giu) return;
        giu = true;
        for (int i = 0; i < quante; i++) nostre[i].abbassaVolume(RESIDUO);
        if (audio == null) return;
        try {
            audio.requestAudioFocus(nulla, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        } catch (Exception e) {
            Log.w(MainActivity.TAG, "focus audio non ottenuto", e);
        }
    }

    /** Ha finito: si torna com'era. */
    public void su() {
        if (!giu) return;
        giu = false;
        for (int i = 0; i < quante; i++) nostre[i].rialzaVolume();
        if (audio == null) return;
        try {
            audio.abandonAudioFocus(nulla);
        } catch (Exception e) {
            Log.w(MainActivity.TAG, "focus audio non restituito", e);
        }
    }

    public boolean abbassata() { return giu; }

    /** C'e' qualcosa che suona, nostro o di altri? Serve alla parola di
     *  attivazione, che con la musica accesa deve essere piu' esigente. */
    public boolean qualcunoSuona() {
        return audio != null && audio.isMusicActive();
    }
}
