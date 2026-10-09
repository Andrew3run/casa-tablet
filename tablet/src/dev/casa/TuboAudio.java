package dev.casa;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;

/**
 * Il tubo per cui passa la musica: da go-librespot all'altoparlante.
 *
 * <b>Perche' esiste.</b> Su Android nessuna app puo' toccare ALSA -
 * {@code /dev/snd} non e' sua, e senza root non lo diventa - quindi il demone
 * non ci prova nemmeno: e' configurato con {@code audio_backend: pipe} e si
 * limita a scrivere PCM grezzo dentro una FIFO. Da questa parte del tubo c'e'
 * un {@link AudioTrack}, che e' l'unico modo che ha un'app di far uscire dei
 * campioni dal tablet.
 *
 * <pre>
 *   go-librespot  --scrive-->  FIFO  --leggiamo-->  AudioTrack
 *                  PCM s16le 44,1 kHz stereo
 * </pre>
 *
 * <b>La FIFO si legge senza bloccarsi.</b> Aperta in sola lettura e non
 * bloccante, poi {@code poll()} con un timeout corto: cosi' {@link #ferma()}
 * ritorna sempre in fretta invece di lasciare il thread fermo dentro una
 * {@code read()} che nessuno sbloccherebbe. Chiudere il descrittore da un altro
 * thread mentre una lettura bloccante e' in corso e' una corsa, non una
 * garanzia.
 *
 * <b>Una lettura di zero byte non e' la fine.</b> Il demone chiude la sua
 * estremita' fra una sessione e l'altra - quando qualcuno sposta la
 * riproduzione su un altro apparecchio - e ne apre una nuova quando si torna.
 * Quello zero e' quella chiusura, non il processo che muore: si riapre la FIFO
 * e si aspetta il prossimo scrittore. Senza, dalla seconda sessione in poi non
 * ci sarebbe piu' nessun lettore ad aspettare, e la musica resterebbe muta per
 * sempre senza un solo messaggio d'errore.
 *
 * <b>Il volume sta qui e non nel demone.</b> {@link #volume(float)} agisce
 * sull'AudioTrack: e' immediato ed esatto. Il demone moltiplicherebbe i
 * campioni <i>prima</i> del tubo, quindi quello che e' gia' nel buffer
 * uscirebbe ancora al volume di prima - un ritardo che si sente, e che sulla
 * sordina del microfono sarebbe proprio il momento sbagliato.
 */
public final class TuboAudio {

    private static final String TAG = "Casa.Tubo";

    /** Quello che scrive il demone, e non e' negoziabile: e' scritto nella sua
     *  configurazione ({@code audio_output_pipe_format: s16le}). */
    private static final int FREQUENZA = 44100;
    private static final int CANALI    = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int CAMPIONE  = AudioFormat.ENCODING_PCM_16BIT;

    /** Quanto si aspetta a ogni giro prima di rispondere a "fermati". */
    private static final int ATTESA_MS = 200;

    /**
     * Il buffer dell'uscita: mezzo secondo di musica, e non meno.
     *
     * <b>Misurato, non scelto.</b> Con il doppio del minimo di sistema - che su
     * questo tablet e' una manciata di millisecondi - il thread interno di
     * AudioTrack girava al <b>100% di un core</b> per tutta la riproduzione,
     * riempiendo il log di "AudioTrackThread::pauseInternal": e' quello che fa
     * un'uscita che va in vuoto a ogni giro, e su quattro core deboli e' un
     * quarto della macchina buttato. Con mezzo secondo di margine il thread si
     * addormenta fra un riempimento e l'altro.
     *
     * Il prezzo e' che una pausa o un cambio di volume si sentono fino a mezzo
     * secondo dopo: quello che e' gia' nel buffer esce comunque. Su un
     * apparecchio da muro e' uno scambio che si fa a occhi chiusi.
     */
    private static final int BUFFER_BYTE = 88200;      // 0,5 s a 44,1 kHz stereo 16 bit

    /** Quanto si legge per volta dalla FIFO. */
    private static final int PEZZO = 16384;

    private final File tubo;
    private volatile boolean vivo;
    private volatile float volume = 1f;
    private Thread lettore;
    private AudioTrack uscita;

    public TuboAudio(File tubo) {
        this.tubo = tubo;
    }

    /**
     * Crea la FIFO e mette in ascolto il lettore.
     *
     * La FIFO si crea <b>prima</b> che il demone parta - e' una sola chiamata
     * di sistema, si puo' fare qui in linea: lui la apre in scrittura appena
     * nasce, e se il nodo non c'e' ancora fallisce subito con ENOENT.
     */
    public void avvia() {
        if (vivo) return;
        if (!preparaFifo()) return;
        vivo = true;
        lettore = new Thread(new Runnable() {
            @Override public void run() { ciclo(); }
        }, "Casa-tubo");
        lettore.start();
    }

    public void ferma() {
        vivo = false;
        Thread t = lettore;
        lettore = null;
        if (t != null) {
            try { t.join(ATTESA_MS * 2L); } catch (InterruptedException ignorata) { }
        }
    }

    public boolean acceso() { return vivo; }

    /** Da 0 a 1. La sordina la usa per abbassare mentre Casa ascolta o parla. */
    public void volume(float quanto) {
        volume = Math.max(0f, Math.min(1f, quanto));
        AudioTrack a = uscita;
        if (a != null) {
            try { a.setVolume(volume); } catch (Exception ignorata) { }
        }
    }

    /**
     * Crea il nodo della FIFO, e <b>non lo ricrea se c'e' gia'</b>.
     *
     * Sembra un dettaglio e non lo e'. Il demone sopravvive al processo di
     * Casa - una reinstallazione, un riavvio dell'Activity - e quando Casa
     * torna si riattacca a quello che gia' gira. Ma il demone tiene aperto il
     * <i>nodo</i> che c'era allora: cancellarlo e rifarne uno uguale lascia lui
     * a scrivere dentro un inode che non ha piu' nome e noi ad ascoltarne un
     * altro. Nessun errore, da nessuna parte, e la musica muta per sempre.
     */
    private boolean preparaFifo() {
        try {
            File dove = tubo.getParentFile();
            if (dove != null && !dove.exists() && !dove.mkdirs()) {
                Log.w(TAG, "cartella della FIFO non creata");
            }
            if (tubo.exists()) return true;
            Os.mkfifo(tubo.getAbsolutePath(),
                      OsConstants.S_IRUSR | OsConstants.S_IWUSR);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "FIFO non creata", e);
            return false;
        }
    }

    private void ciclo() {
        int minimo = AudioTrack.getMinBufferSize(FREQUENZA, CANALI, CAMPIONE);
        if (minimo <= 0) minimo = 8192;

        AudioTrack a;
        try {
            a = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(FREQUENZA)
                            .setChannelMask(CANALI)
                            .setEncoding(CAMPIONE)
                            .build())
                    .setBufferSizeInBytes(Math.max(minimo * 4, BUFFER_BYTE))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (Exception e) {
            Log.w(TAG, "AudioTrack non creato", e);
            vivo = false;
            return;
        }

        uscita = a;
        byte[] pezzo = new byte[PEZZO];
        try {
            a.setVolume(volume);
            // <b>Non si fa play() qui.</b> Un AudioTrack avviato e senza
            // campioni resta in sofferenza: su questo ROM scrive
            // "AudioTrackThread::pauseInternal" nel log a ripetizione, mille
            // righe al minuto, e tiene sveglio un thread del sistema per non
            // suonare niente. Si parte al primo byte che arriva davvero.
            while (vivo) {
                FileDescriptor fd;
                try {
                    fd = Os.open(tubo.getAbsolutePath(),
                                 OsConstants.O_RDONLY | OsConstants.O_NONBLOCK, 0);
                } catch (Exception e) {
                    Log.w(TAG, "FIFO non apribile", e);
                    break;
                }
                try {
                    unaSessione(fd, a, pezzo);
                } finally {
                    try { Os.close(fd); } catch (Exception ignorata) { }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "lettura interrotta", e);
        } finally {
            uscita = null;
            try { a.stop(); } catch (Exception ignorata) { }
            a.release();
        }
    }

    /** Una sessione di scrittura: finisce sull'EOF (il demone ha chiuso) o
     *  quando ci hanno detto di fermarci. Il descrittore lo chiude chi chiama. */
    private void unaSessione(FileDescriptor fd, AudioTrack a, byte[] pezzo) {
        boolean partito = false;
        StructPollfd attesa = new StructPollfd();
        attesa.fd = fd;
        attesa.events = (short) OsConstants.POLLIN;
        StructPollfd[] uno = new StructPollfd[] { attesa };

        while (vivo) {
            int pronti;
            try {
                pronti = Os.poll(uno, ATTESA_MS);
            } catch (ErrnoException e) {
                pronti = 0;
            }
            if (pronti <= 0) continue;

            int quanti;
            try {
                quanti = Os.read(fd, pezzo, 0, pezzo.length);
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.EAGAIN) continue;
                Log.w(TAG, "lettura fallita", e);
                return;
            } catch (Exception e) {
                Log.w(TAG, "lettura fallita", e);
                return;
            }
            if (quanti <= 0) {
                // Lo scrittore ha chiuso: si mette in pausa l'uscita e si
                // riapre la FIFO ad aspettare la prossima sessione. Senza la
                // pausa, l'AudioTrack resterebbe in riproduzione a vuoto fino
                // alla prossima traccia.
                if (partito) { try { a.pause(); a.flush(); } catch (Exception ignorata) { } }
                return;
            }
            if (!partito) { a.play(); partito = true; }
            a.write(pezzo, 0, quanti);
        }
        if (partito) { try { a.pause(); } catch (Exception ignorata) { } }
    }

    /** Il flusso che il demone deve scrivere. Serve solo a chi scrive la
     *  configurazione, per non ripetere i numeri in due posti. */
    /**
     * La sessione audio dell'AudioTrack, o zero se non c'e'.
     *
     * Serve alle colonne del suono della Home, per lo stesso motivo per cui
     * serve alla radio: gli effetti di visualizzazione si agganciano a una
     * sessione, e la sessione zero su questo ROM non si lascia agganciare.
     */
    public int sessioneAudio() {
        AudioTrack a = uscita;
        if (a == null) return 0;
        try {
            return a.getAudioSessionId();
        } catch (Exception spento) {
            return 0;
        }
    }

    public static int frequenza() { return FREQUENZA; }

    /** Lo stream su cui esce: e' quello del volume dei tasti. */
    public static int flusso() { return AudioManager.STREAM_MUSIC; }
}
