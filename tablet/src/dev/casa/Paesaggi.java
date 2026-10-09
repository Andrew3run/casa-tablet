package dev.casa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Le fotografie del riposo: dove stanno, da dove arrivano, e come entrano in
 * memoria senza mangiarsela.
 *
 * <h3>Due sorgenti, e la prima vince</h3>
 *
 * <ul>
 *   <li><b>Le foto di casa</b>, in {@code Android/data/dev.casa/files/paesaggi/}.
 *       Ci si arriva da adb - {@code adb push .\mare.jpg ...} - quindi sono
 *       quelle che si mettono dal PC. Se ce n'e' anche una sola, si mostrano
 *       <b>soltanto</b> quelle e dalla rete non si scarica piu' niente: chi ha
 *       messo le sue foto ha gia' detto cosa vuole vedere.
 *   <li><b>Le foto del giorno di Bing</b>, in {@code getFilesDir()/paesaggi/}.
 *       Sono le stesse che Windows mette sulla schermata di blocco, con la
 *       stessa didascalia - il posto e chi l'ha scattata - e sono paesaggi per
 *       mestiere. L'archivio ne pubblica otto giorni; qui se ne conservano
 *       {@link #QUANTE_AL_MASSIMO}, che sono una settimana e mezza di roba
 *       diversa.
 * </ul>
 *
 * <h3>Perche' in getFilesDir() e non nella cache</h3>
 *
 * Le copertine dei dischi stanno in {@code cacheDir} apposta: si sanno
 * riscaricare, e se il sistema ha bisogno di spazio fa bene a buttarle. Qui e'
 * l'opposto. Il riposo compare <b>quando non lo guarda nessuno</b>, e la sera
 * in cui il Wi-Fi non va e' proprio la sera in cui una cartella svuotata dal
 * sistema vorrebbe dire uno schermo vuoto tutta la notte. Meno di due megabyte
 * su undici gigabyte liberi sono il prezzo giusto per non dipendere dalla rete.
 *
 * <h3>La memoria: due immagini, mai tre</h3>
 *
 * Una foto entra in memoria <b>gia' tagliata alla misura dello schermo</b> -
 * 1280x800 in RGB_565, cioe' 2,0 MB - e non alla misura in cui e' stata
 * scaricata. Il taglio si fa una volta sola, sul thread di lavoro, e da li' in
 * poi disegnarla e' una copia uno a uno: su un Mali-400 e' la differenza fra un
 * fotogramma e un fotogramma con dentro una scalatura.
 *
 * <b>Le immagini sono due, e sono sempre le stesse due.</b> Non due alla
 * volta: proprio due bitmap, allocate alla prima fotografia e poi riusate
 * all'infinito - la foto nuova si disegna sopra quella vecchia. E' l'unica
 * forma che regge su questo ROM, dove i pixel di una bitmap che e' stata
 * disegnata non tornano piu' indietro nemmeno riciclandola: il perche', e la
 * misura da cui si e' capito, stanno su {@link #tele}.
 */
public final class Paesaggi {

    /** Sotto {@code Casa} come tutto il resto: su questo ROM un tag fuori
     *  lista non compare in logcat e non da' errore. Vedi MainActivity.TAG. */
    private static final String TAG = "Casa";

    /** La cartella, con lo stesso nome dentro e fuori: una sola parola da
     *  ricordare quando si va a mettere una foto da adb. */
    private static final String CARTELLA = "paesaggi";

    /** Le didascalie delle foto scaricate, in getFilesDir(). */
    private static final String FILE = "paesaggi.json";

    /**
     * L'archivio del giorno di Bing: otto giorni indietro, mercato italiano.
     *
     * Il {@code mkt} non e' un vezzo: cambia le didascalie - "Lago di Braies,
     * Dolomiti" invece di "Lake Braies, Dolomites" - e ogni tanto cambia anche
     * la foto, perche' in Italia Bing mette cose italiane.
     */
    private static final String ARCHIVIO =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=it-IT";

    /**
     * La misura che si chiede a Bing.
     *
     * L'archivio ne pubblica quattro, e questa e' l'unica che ha senso qui.
     * Misurata: 1366x768 sono 168 KB, e portarla a 1280x800 vuol dire tagliare
     * settanta pixel per lato e ingrandire del quattro per cento in altezza,
     * che su una fotografia non si vede. La 1920x1080 costerebbe il doppio di
     * rete e di disco per pixel che questo schermo non ha.
     */
    private static final String MISURA = "_1366x768.jpg";

    /** Quante se ne tengono sul disco. Misurate: 168 KB l'una a 1366x768,
     *  quindi meno di due megabyte in tutto. */
    private static final int QUANTE_AL_MASSIMO = 10;

    /** Ogni quanto si va a vedere se ce n'e' una nuova. Venti ore e non
     *  ventiquattro: cosi' il giro non capita sempre alla stessa ora del
     *  giorno, che potrebbe essere l'ora in cui il tablet e' spento. */
    private static final long OGNI_TANTO = 20L * 60 * 60 * 1000;

    /** Dopo un giro a vuoto non si ritenta per un'ora: senza rete, provarci a
     *  ogni comparsa del riposo sarebbe una richiesta ogni cinque minuti per
     *  tutta la notte. */
    private static final long DOPO_UN_BUCO = 60L * 60 * 1000;

    /** Una foto pronta da disegnare: l'immagine, e cosa c'e' scritto sotto. */
    public static final class Foto {
        public final Bitmap immagine;
        /** Dove e' stata scattata: "Lago di Braies, Dolomiti". Puo' mancare. */
        public final String luogo;
        /** Chi l'ha scattata: "© Nome/Getty Images". Puo' mancare. */
        public final String autore;

        Foto(Bitmap immagine, String luogo, String autore) {
            this.immagine = immagine;
            this.luogo = luogo;
            this.autore = autore;
        }

        public boolean viva() { return immagine != null && !immagine.isRecycled(); }
    }

    /** Chi ridisegnare quando ne arriva una. */
    public interface Pronta { void fotoPronta(); }

    private final Context contesto;
    private final Misure m;
    private final File dentro;          // le scaricate, in getFilesDir()
    private final File fuori;           // le nostre, in getExternalFilesDir()
    private final Handler lavoro;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random caso = new Random();

    private Pronta avviso;

    /** L'elenco di adesso e a che punto si e' arrivati. Si toccano solo dal
     *  thread di lavoro. */
    private File[] elenco = new File[0];
    private int indice = -1;

    private volatile Foto corrente;
    private volatile Foto uscente;

    private long ultimoGiro;            // quando si e' guardata la rete

    /** Se le foto si possono andare a prendere da Bing. Lo decide il PC, e chi
     *  lo spegne resta con le sue: e' l'unico interruttore di questa classe che
     *  qualcuno potrebbe voler girare. */
    private volatile boolean rete = true;

    /** Fra {@link #apri()} e {@link #chiudi()}. Quello che arriva fuori da
     *  questa finestra si scorda appena nato. */
    private volatile boolean aperto;

    /**
     * Le due tele su cui finiscono le fotografie: <b>sempre queste, mai una
     * nuova</b>.
     *
     * <h3>Perche', e come si e' scoperto</h3>
     *
     * La prima versione allocava una bitmap da 1280x800 per ogni foto e
     * riciclava la vecchia. Sembrava giusto, e sul tablet vero <b>perdeva 2,3
     * MB a ogni cambio di fotografia</b>: dopo mezz'ora Casa stava a cento
     * megabyte di heap nativo e il tablet strisciava.
     *
     * La misura che ha chiuso la questione, fatta in due giri uguali tranne che
     * per una cosa. <b>Con il riposo in scena</b>, ogni foto costava 2,3 MB che
     * non tornavano piu': ne' con {@code recycle()}, ne' con un
     * {@code onTrimMemory}, ne' togliendo il riposo di mezzo. <b>Con il riposo
     * spento</b> - stessa decodifica, stessa bitmap, ma buttata prima di finire
     * sullo schermo - la memoria restava ferma al kilobyte.
     *
     * Quindi il punto non e' la decodifica: e' che su questo ROM i pixel di una
     * bitmap <b>che e' stata disegnata</b> non tornano indietro. Il disegno
     * accelerato se ne tiene un riferimento suo, e {@code recycle()} da li' in
     * poi non libera niente.
     *
     * Contro una cosa cosi' non si vince riciclando meglio: si vince <b>non
     * allocando</b>. Le tele sono due perche' due ne servono - quella in scena
     * e quella che sta uscendo durante la dissolvenza - hanno la misura esatta
     * dello schermo, e la foto nuova ci si disegna sopra. Quattro megabyte, una
     * volta sola, per sempre.
     */
    private Bitmap[] tele;
    private int prossimaTela;

    public Paesaggi(Context c, Misure misure) {
        this.contesto = c.getApplicationContext();
        this.m = misure;
        this.dentro = new File(c.getFilesDir(), CARTELLA);
        File esterna = c.getExternalFilesDir(null);
        this.fuori = esterna == null ? null : new File(esterna, CARTELLA);
        HandlerThread t = new HandlerThread("Casa-paesaggi");
        t.start();
        lavoro = new Handler(t.getLooper());
    }

    public void setAvviso(Pronta p) { avviso = p; }

    /** Se andarle a prendere da Bing o accontentarsi di quelle che ci sono. */
    public void setRete(boolean r) { rete = r; }

    /** La foto in scena, o null se non ce n'e' ancora nessuna. */
    public Foto corrente() { return corrente; }

    /** Quella che sta uscendo, durante la dissolvenza. */
    public Foto uscente() { return uscente; }

    /** Quante ce ne sono da mostrare. Zero vuol dire che il riposo disegnera'
     *  il fondale generato a codice, che e' un ripiego onesto. */
    public int quante() { return elenco.length; }

    // ---- il giro delle foto -------------------------------------------------

    /**
     * Il riposo entra in scena: si guarda cosa c'e', e si comincia.
     *
     * <b>Da dove si comincia e' a caso, l'ordine no.</b> Le foto di Bing sono
     * in ordine di giorno, che e' gia' un ordine buono; ricominciare sempre
     * dalla piu' vecchia farebbe di ogni sera la sera prima. Partire da un
     * punto qualunque costa un numero a caso e basta.
     */
    public void apri() {
        aperto = true;
        lavoro.post(new Runnable() {
            @Override public void run() {
                rileggiCartelle();
                // Prima quella che c'e' gia' sul disco, poi la rete. Al
                // contrario, il primo riposo dopo un riavvio resterebbe un
                // fondale vuoto per tutto il tempo che Bing ci mette a
                // rispondere - con otto fotografie gia' li' a due centimetri.
                boolean ce = elenco.length > 0;
                if (ce) {
                    if (indice < 0) indice = caso.nextInt(elenco.length);
                    carica();
                }
                seServeScarica();
                if (!ce && elenco.length > 0) {
                    indice = caso.nextInt(elenco.length);
                    carica();
                }
            }
        });
    }

    /** La prossima. La chiama il riposo ogni tot minuti, e a mano il PC. */
    public void avanti() {
        lavoro.post(new Runnable() {
            @Override public void run() {
                if (elenco.length == 0) {
                    rileggiCartelle();
                    if (elenco.length == 0) return;
                }
                indice = (indice + 1) % elenco.length;
                carica();
            }
        });
    }

    /**
     * La dissolvenza e' finita: quella di prima non serve piu'.
     *
     * La chiama la vela, non un timer: e' l'unica che sa quando ha smesso di
     * disegnarla, e riciclare una bitmap che qualcuno sta ancora disegnando e'
     * uno schermo nero piu' una riga nel registro che non spiega niente.
     */
    public void dimenticaUscente() {
        // Non si ricicla niente: quella e' una delle due tele, e la prossima
        // foto ci andra' sopra. Basta smettere di dire che e' in scena.
        uscente = null;
    }

    /**
     * Il riposo esce di scena.
     *
     * <b>Le tele restano.</b> Riciclarle non restituirebbe niente - su questo
     * ROM i pixel di una bitmap gia' disegnata non tornano indietro, vedi
     * {@link #tele} - e rifarle alla comparsa dopo vorrebbe dire quattro
     * megabyte nuovi ogni volta: la stessa perdita di prima, con un passo piu'
     * lento. Quattro megabyte fermi sono il prezzo onesto di questa schermata,
     * e si pagano una volta.
     */
    public void chiudi() {
        aperto = false;
        corrente = null;
        uscente = null;
    }

    /**
     * Casa se ne va per davvero: si spegne anche il thread.
     *
     * {@link #chiudi()} restituisce le immagini e basta, perche' il riposo puo'
     * tornare un minuto dopo e rifare il thread costerebbe piu' che tenerlo.
     * Questa invece la chiama solo {@code onDestroy}: senza, un'Activity rifatta
     * ne lascerebbe indietro uno vivo ogni volta, in attesa di lavoro che non
     * arrivera' mai.
     */
    public void spegni() {
        chiudi();
        avviso = null;
        if (tele != null) {
            for (int i = 0; i < tele.length; i++) {
                if (tele[i] != null && !tele[i].isRecycled()) tele[i].recycle();
                tele[i] = null;
            }
            tele = null;
        }
        lavoro.getLooper().quitSafely();
    }

    // ---- il disco -----------------------------------------------------------

    /**
     * Che foto ci sono, e quali contano.
     *
     * Le nostre hanno la precedenza su tutto: se in {@code files/paesaggi/} c'e'
     * anche un solo file, quelle di Bing restano sul disco ma non si vedono.
     * Cancellarle sarebbe la cosa sbagliata - basta togliere le proprie per
     * riaverle - ma mescolarle sarebbe peggio: una foto di famiglia in mezzo a
     * otto cartoline non e' un album, e' un errore.
     */
    private void rileggiCartelle() {
        File[] nostre = immagini(fuori);
        elenco = nostre.length > 0 ? nostre : immagini(dentro);
        if (indice >= elenco.length) indice = elenco.length == 0 ? -1 : 0;
    }

    private static File[] immagini(File cartella) {
        if (cartella == null || !cartella.isDirectory()) return new File[0];
        File[] tutti = cartella.listFiles();
        if (tutti == null) return new File[0];
        File[] buoni = new File[tutti.length];
        int quanti = 0;
        for (File f : tutti) {
            if (f.isFile() && f.length() > 0 && immagine(f.getName())) buoni[quanti++] = f;
        }
        File[] esatti = new File[quanti];
        System.arraycopy(buoni, 0, esatti, 0, quanti);
        // In ordine di nome: per Bing e' l'ordine dei giorni (bing-20260910),
        // per le nostre e' l'ordine che ha voluto chi le ha messe li'.
        Arrays.sort(esatti);
        return esatti;
    }

    private static boolean immagine(String nome) {
        String n = nome.toLowerCase(Locale.ITALIAN);
        return n.endsWith(".jpg") || n.endsWith(".jpeg")
            || n.endsWith(".png") || n.endsWith(".webp");
    }

    // ---- la decodifica ------------------------------------------------------

    /** Sul thread di lavoro: legge, taglia, e consegna. */
    private void carica() {
        if (indice < 0 || indice >= elenco.length) return;
        File f = elenco[indice];
        Bitmap b = null;
        try {
            b = allaMisuraDelloSchermo(f);
        } catch (Throwable t) {
            Log.w(TAG, "riposo: " + f.getName() + " non si apre", t);
        }
        if (b == null) {
            // Un file rotto non deve fermare il giro: si passa oltre. Succede
            // con un push interrotto a meta'.
            if (elenco.length > 1) {
                elenco = senza(elenco, indice);
                if (elenco.length == 0) return;
                indice = indice % elenco.length;
                carica();
            }
            return;
        }
        JSONObject didascalie = Archivio.leggi(contesto, FILE);
        JSONObject sua = didascalie == null ? null : didascalie.optJSONObject(f.getName());
        String luogo  = sua != null ? sua.optString("luogo", "")  : titoloDalNome(f.getName());
        String autore = sua != null ? sua.optString("autore", "") : null;
        consegna(new Foto(b, vuoto(luogo) ? null : luogo, vuoto(autore) ? null : autore));
    }

    private static File[] senza(File[] a, int quale) {
        File[] b = new File[a.length - 1];
        for (int i = 0, j = 0; i < a.length; i++) if (i != quale) b[j++] = a[i];
        return b;
    }

    private void consegna(final Foto nuova) {
        ui.post(new Runnable() {
            @Override public void run() {
                // Chiuso nel frattempo: una decodifica partita un attimo prima
                // non deve lasciare due megabyte in mano a una schermata che
                // non c'e' piu'. Succede davvero - il dito arriva mentre il
                // thread di lavoro sta tagliando la foto.
                if (!aperto) return;      // la tela resta nostra, la foto si scorda
                // La terza non deve esistere: se una dissolvenza non era ancora
                // finita, quella di prima se ne va adesso.
                uscente = corrente;
                corrente = nuova;
                Pronta p = avviso;
                if (p != null) p.fotoPronta();
            }
        });
    }

    /**
     * Legge una foto e la porta alla misura esatta dello schermo, tagliando i
     * bordi che avanzano.
     *
     * Tre passate, e ognuna evita una spesa. La prima legge solo l'intestazione
     * ({@code inJustDecodeBounds}) e dice quanto e' grande: qualche decina di
     * byte, nessuna memoria. La seconda decodifica con {@code inSampleSize},
     * che e' esatto solo sulle potenze di due, arrotondato <b>per difetto</b>:
     * meglio decodificare qualche pixel di troppo e rimpicciolire, che
     * decodificarne meno e ingrandire una foto sfocata. La terza taglia al
     * centro e riempie.
     *
     * Il taglio al centro e non lo stiramento: una fotografia stirata si vede
     * subito, e questa e' una schermata che si guarda proprio come fotografia.
     */
    private Bitmap allaMisuraDelloSchermo(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;

        int giro = orientamento(f);
        // Con la foto girata di un quarto, il lato che dovra' coprire la
        // larghezza dello schermo e' l'altezza scritta nel file.
        boolean coricata = (giro == 90 || giro == 270);
        int largo = coricata ? o.outHeight : o.outWidth;
        int alto  = coricata ? o.outWidth  : o.outHeight;

        int riduzione = 1;
        while (largo / (riduzione * 2) >= m.larghezza && alto / (riduzione * 2) >= m.altezza) {
            riduzione *= 2;
        }

        BitmapFactory.Options vera = new BitmapFactory.Options();
        vera.inSampleSize = riduzione;
        // RGB_565: una fotografia non ha trasparenza, e cosi' costa la meta'.
        // Le bande sui cieli sfumati, che con le copertine erano il rischio,
        // qui non si vedono: sopra passa il velo scuro, che le rompe.
        vera.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap letta = BitmapFactory.decodeFile(f.getAbsolutePath(), vera);
        if (letta == null) return null;

        Bitmap schermo = null;
        try {
            schermo = tela();
            if (schermo == null) return null;
            Canvas c = new Canvas(schermo);
            // La tela ha gia' avuto una foto sopra: si copre tutta, o di una
            // piu' stretta si vedrebbero i bordi di quella di prima.
            c.drawColor(0xFF000000);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            float lw = coricata ? letta.getHeight() : letta.getWidth();
            float lh = coricata ? letta.getWidth()  : letta.getHeight();
            float scala = Math.max(m.larghezza / lw, m.altezza / lh);

            Matrix mx = new Matrix();
            if (giro != 0) {
                mx.postTranslate(-letta.getWidth() / 2f, -letta.getHeight() / 2f);
                mx.postRotate(giro);
                mx.postTranslate(lw / 2f, lh / 2f);
            }
            mx.postScale(scala, scala);
            mx.postTranslate((m.larghezza - lw * scala) / 2f, (m.altezza - lh * scala) / 2f);
            c.drawBitmap(letta, mx, p);
        } catch (Throwable t) {
            // La tela resta nostra anche quando il disegno va storto: ci si
            // riprova con la prossima foto.
            schermo = null;
            Log.w(TAG, "riposo: non ho potuto tagliare " + f.getName(), t);
        } finally {
            letta.recycle();
        }
        return schermo;
    }

    /**
     * La tela su cui disegnare la prossima foto: quella che non e' in scena.
     *
     * Si allocano alla prima foto e non nel costruttore: un tablet che non si
     * mette mai a riposo non deve pagarle. Se la memoria non basta si torna
     * null, e il riposo mostra il suo fondale - che e' meglio di un
     * OutOfMemory.
     */
    private Bitmap tela() {
        if (tele == null) tele = new Bitmap[2];
        for (int giro = 0; giro < 2; giro++) {
            int i = prossimaTela;
            prossimaTela = (prossimaTela + 1) % 2;
            if (occupata(tele[i])) continue;          // e' in scena, non si tocca
            if (tele[i] == null || tele[i].isRecycled()) {
                try {
                    tele[i] = Bitmap.createBitmap(m.larghezza, m.altezza, Bitmap.Config.RGB_565);
                } catch (Throwable senzaMemoria) {
                    Log.w(TAG, "riposo: niente memoria per la tela", senzaMemoria);
                    return null;
                }
            }
            return tele[i];
        }
        // Tutte e due in scena: succede solo se una dissolvenza non e' mai
        // finita. Si salta un cambio di foto, che non se ne accorge nessuno.
        return null;
    }

    /** Questa tela e' sullo schermo adesso? */
    private boolean occupata(Bitmap b) {
        if (b == null) return false;
        Foto a = corrente, u = uscente;
        return (a != null && a.immagine == b) || (u != null && u.immagine == b);
    }

    /**
     * Di quanto e' girata una foto, secondo l'EXIF.
     *
     * Serve alle foto di casa, non a quelle di Bing: una fotografia scattata
     * col telefono arriva quasi sempre dritta nei pixel e girata
     * nell'intestazione, e senza questa lettura si vedrebbe coricata. Chi l'ha
     * messa nella cartella non avrebbe nessun modo di capire perche'.
     */
    private static int orientamento(File f) {
        try {
            android.media.ExifInterface exif =
                    new android.media.ExifInterface(f.getAbsolutePath());
            switch (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL)) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90:  return 90;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Throwable t) {
            return 0;      // un PNG non ha EXIF, e non e' un errore
        }
    }

    /**
     * La didascalia di una foto di casa: il nome del file, se dice qualcosa.
     *
     * "lago-di-braies.jpg" diventa "lago di braies"; "IMG_20240815_120000.jpg"
     * non diventa niente, perche' un nome di fotocamera scritto sotto una
     * fotografia e' rumore. La regola e' grossolana apposta - se ci sono piu'
     * cifre che lettere non e' un nome - e sbagliarla costa una didascalia
     * mancata, non una foto.
     */
    private static String titoloDalNome(String nome) {
        int punto = nome.lastIndexOf('.');
        String s = punto > 0 ? nome.substring(0, punto) : nome;
        s = s.replace('_', ' ').replace('-', ' ').trim();
        int cifre = 0, lettere = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) cifre++;
            else if (Character.isLetter(ch)) lettere++;
        }
        if (lettere < 3 || cifre >= lettere) return null;
        return s;
    }

    private static boolean vuoto(String s) { return s == null || s.trim().length() == 0; }

    // ---- la rete ------------------------------------------------------------

    /**
     * Se e' ora, si va a vedere se Bing ne ha una nuova.
     *
     * Non si scarica niente quando ci sono le foto di casa: chi le ha messe ha
     * gia' detto cosa vuole vedere, e riempirgli il disco di cartoline che non
     * guardera' mai sarebbe solo spazio buttato.
     */
    private void seServeScarica() {
        if (immagini(fuori).length > 0) return;
        long adesso = System.currentTimeMillis();
        long scaduto = elenco.length == 0 ? DOPO_UN_BUCO : OGNI_TANTO;
        if (ultimoGiro != 0 && adesso - ultimoGiro < scaduto) return;
        ultimoGiro = adesso;
        scarica();
        rileggiCartelle();
        if (indice < 0 && elenco.length > 0) indice = caso.nextInt(elenco.length);
    }

    /** Il giro a mano: lo chiede il PC quando vuole vedere subito se funziona. */
    public void aggiorna() {
        lavoro.post(new Runnable() {
            @Override public void run() {
                ultimoGiro = System.currentTimeMillis();
                scarica();
                rileggiCartelle();
                if (indice < 0 && elenco.length > 0) indice = 0;
                carica();
            }
        });
    }

    private void scarica() {
        if (!rete) {
            Log.i(TAG, "riposo: le foto dalla rete sono spente in configurazione");
            return;
        }
        if (!dentro.exists() && !dentro.mkdirs()) {
            Log.w(TAG, "riposo: cartella " + dentro + " non creata");
            return;
        }
        String risposta = chiedi(ARCHIVIO);
        if (risposta == null) return;
        int nuove = 0;
        try {
            JSONArray immagini = new JSONObject(risposta).optJSONArray("images");
            if (immagini == null) return;
            JSONObject didascalie = Archivio.leggi(contesto, FILE);
            if (didascalie == null) didascalie = new JSONObject();
            for (int i = 0; i < immagini.length(); i++) {
                JSONObject img = immagini.optJSONObject(i);
                if (img == null) continue;
                String base = img.optString("urlbase", "");
                String giorno = img.optString("startdate", "");
                if (base.length() == 0 || giorno.length() == 0) continue;
                String nome = "bing-" + giorno + ".jpg";
                File dove = new File(dentro, nome);
                if (!dove.exists() && porta("https://www.bing.com" + base + MISURA, dove)) nuove++;
                if (!dove.exists()) continue;
                JSONObject sua = new JSONObject();
                sua.put("luogo", posto(img.optString("copyright", ""),
                                       img.optString("title", "")));
                String chi = autore(img.optString("copyright", ""));
                if (chi != null) sua.put("autore", chi);
                didascalie.put(nome, sua);
            }
            Archivio.scrivi(contesto, FILE, didascalie);
        } catch (Exception e) {
            Log.w(TAG, "riposo: l'archivio di Bing non si legge", e);
            return;
        }
        faiPosto();
        if (nuove > 0) Log.i(TAG, "riposo: " + nuove + " foto nuove");
    }

    /**
     * Il posto, dalla riga di copyright.
     *
     * Bing la scrive sempre nella stessa forma: "Lago di Braies, Dolomiti,
     * Italia (© Nome/Getty Images)". Quello che sta prima della parentesi e' il
     * posto, ed e' l'unica cosa che si legge da lontano. Se un giorno la forma
     * cambia - e prima o poi cambia - si ripiega sul titolo, che c'e' sempre.
     */
    private static String posto(String copyright, String titolo) {
        int p = copyright.indexOf("(©");
        if (p < 0) p = copyright.indexOf(" (");
        String s = (p > 0 ? copyright.substring(0, p) : copyright).trim();
        while (s.endsWith(",")) s = s.substring(0, s.length() - 1).trim();
        return s.length() > 0 ? s : titolo;
    }

    /** Chi l'ha scattata. E' un credito: si scrive com'e', senza abbellirlo. */
    private static String autore(String copyright) {
        int a = copyright.indexOf('(');
        int b = copyright.lastIndexOf(')');
        if (a < 0 || b <= a) return null;
        String s = copyright.substring(a + 1, b).trim();
        return s.length() > 0 ? s : null;
    }

    /** Sopra il tetto si buttano le piu' vecchie: il nome comincia con la data,
     *  quindi l'ordine alfabetico e' gia' l'ordine del tempo. */
    private void faiPosto() {
        File[] tutte = immagini(dentro);
        for (int i = 0; i < tutte.length - QUANTE_AL_MASSIMO; i++) {
            if (!tutte[i].delete()) {
                Log.w(TAG, "riposo: " + tutte[i].getName() + " non cancellata");
            }
        }
    }

    /** Una richiesta, con le radici che a questo Android mancano: il perche'
     *  sta per esteso in {@link Fiducia}. */
    private String chiedi(String indirizzo) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            Fiducia.applica(contesto, c);
            c.setRequestMethod("GET");
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) {
                Log.w(TAG, "riposo: Bing risponde " + c.getResponseCode());
                return null;
            }
            return Musica.tutto(c.getInputStream());
        } catch (Exception e) {
            Log.i(TAG, "riposo: niente rete per le foto (" + e.getMessage() + ")");
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * Porta un file sul disco, o non lo porta affatto.
     *
     * Si scrive accanto e si rinomina, come fa {@link Archivio} con la
     * configurazione e per la stessa ragione: qui la corrente se ne va davvero,
     * e mezza fotografia sul disco resterebbe nel giro per sempre.
     */
    private boolean porta(String indirizzo, File dove) {
        HttpURLConnection c = null;
        InputStream in = null;
        FileOutputStream out = null;
        File temporaneo = new File(dove.getAbsolutePath() + ".tmp");
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            Fiducia.applica(contesto, c);
            c.setConnectTimeout(6000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) return false;
            in = c.getInputStream();
            out = new FileOutputStream(temporaneo);
            byte[] pezzo = new byte[8192];
            int n;
            while ((n = in.read(pezzo)) > 0) out.write(pezzo, 0, n);
            out.flush();
            out.getFD().sync();
            out.close();
            out = null;
            return temporaneo.renameTo(dove);
        } catch (Exception e) {
            Log.w(TAG, "riposo: foto non scaricata", e);
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignorata) { }
            if (out != null) try { out.close(); } catch (Exception ignorata) { }
            if (temporaneo.exists()) temporaneo.delete();
            if (c != null) c.disconnect();
        }
    }

    /** Una riga per il registro, quando il PC chiede come sta. */
    public String stato() {
        int nostre = immagini(fuori).length;
        int scaricate = immagini(dentro).length;
        return (nostre > 0 ? nostre + " foto di casa" : scaricate + " foto scaricate")
                + (nostre > 0 && scaricate > 0 ? " (piu' " + scaricate + " di Bing da parte)" : "")
                + ", in scena la " + (indice + 1);
    }
}
