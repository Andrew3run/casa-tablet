package dev.casa;

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.CalendarContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Gli impegni: quelli del calendario Google dell'account collegato al tablet.
 *
 * <h3>Non c'e' nessuna sincronizzazione, qui dentro</h3>
 *
 * E non e' una mancanza: e' la parte migliore. A tenere allineato il calendario
 * di Google con il tablet ci pensa il <b>sincronizzatore di sistema</b>
 * ({@code com.google.android.syncadapters.calendar}), che scrive dentro il
 * provider di Android. Casa <b>legge il provider</b>, e basta.
 *
 * Vuol dire niente OAuth, niente chiavi da custodire, niente richieste di rete
 * da fare a mano, niente token che scadono la sera che serve leggere l'ora della
 * visita dal dentista. E vuol dire che un impegno aggiunto dal telefono
 * <b>compare qui da solo</b>, perche' e' lo stesso meccanismo che lo fa comparire
 * nell'orologio o in qualunque altra app di calendario.
 *
 * Il prezzo e' che servono tre cose sul tablet, e questo tablet e' stato
 * alleggerito:
 *
 * <ol>
 *   <li>{@code com.android.providers.calendar} - il magazzino. Sta nella lista
 *       di quelli che {@code tools/alleggerisci.ps1} toglie;
 *   <li>{@code com.google.android.syncadapters.calendar} - chi lo riempie;
 *   <li>un account Google con la sincronizzazione del calendario accesa.
 * </ol>
 *
 * Se manca qualcosa non si finge: {@link #perche()} dice <b>quale</b> delle tre.
 * Le prime due Casa se le rimette da sola, da device owner - vedi
 * {@code MainActivity.preparaIlCalendario}. Un elenco vuoto senza spiegazione
 * sarebbe la cosa peggiore: chi guarda penserebbe di non avere impegni.
 *
 * <h3>Quando si rilegge</h3>
 *
 * Quando cambia, e non ogni tanto: il provider avvisa
 * ({@link ContentObserver}) appena il sincronizzatore porta qualcosa, e quella
 * e' l'unica sveglia che serve. Piu' una rilettura entrando nella sezione e una
 * quando Casa torna in primo piano, che costano una query locale da pochi
 * millisecondi e coprono il caso in cui l'avviso si sia perso mentre Casa non
 * c'era.
 */
public final class Calendario {

    private static final String TAG = "Casa";

    /** Quanto avanti si guarda. Due settimane: oltre, su una schermata da muro,
     *  non e' piu' « cosa c'e' » ma « cosa ci sara' ». */
    private static final long AVANTI = 14L * 24 * 60 * 60 * 1000;

    /** E quanto indietro: un impegno cominciato un'ora fa e' ancora in corso, e
     *  toglierlo dall'elenco appena scatta l'ora d'inizio vorrebbe dire farlo
     *  sparire proprio mentre serve. */
    private static final long INDIETRO = 60L * 60 * 1000;

    /** Quante se ne tengono. Nessuno legge trenta impegni da un tablet appeso
     *  al muro. */
    private static final int TETTO = 30;

    /** Perche' non c'e' niente da mostrare. */
    public static final int TUTTO_BENE = 0, SENZA_PERMESSO = 1, SENZA_PROVIDER = 2,
                            SENZA_CALENDARI = 3;

    /** Un impegno, gia' pronto da disegnare. */
    public static final class Impegno {
        public String titolo;
        public String dove;
        public long inizio, fine;
        public boolean tuttoIlGiorno;
        /** Il colore del calendario da cui viene: e' l'unica cosa che dice « e'
         *  di lavoro » o « e' di casa » senza scriverlo. */
        public int colore;

        /** In corso adesso? */
        public boolean adesso() {
            long o = System.currentTimeMillis();
            return inizio <= o && o < fine;
        }
    }

    public interface Ascolto { void impegniCambiati(); }

    /** La risposta a una domanda su un pezzo di tempo preciso. */
    public interface Esito { void impegni(Impegno[] trovati); }

    private final Context contesto;
    private final Handler lavoro;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Ascolto> chiGuarda = new ArrayList<Ascolto>();

    private volatile Impegno[] elenco = new Impegno[0];
    private volatile int guasto = TUTTO_BENE;

    private ContentObserver spia;
    private long ultimaLettura;

    public Calendario(Context c) {
        this.contesto = c.getApplicationContext();
        HandlerThread t = new HandlerThread("Casa-calendario");
        t.start();
        lavoro = new Handler(t.getLooper());
    }

    public void aggiungiAscolto(Ascolto a) {
        if (a != null && !chiGuarda.contains(a)) chiGuarda.add(a);
    }

    public void togliAscolto(Ascolto a) { chiGuarda.remove(a); }

    // ---- quello che si vede -------------------------------------------------

    /** Gli impegni da adesso in avanti, gia' in ordine di tempo. */
    public Impegno[] prossimi() {
        Impegno[] tutti = elenco;
        long adesso = System.currentTimeMillis();
        int quanti = 0;
        for (Impegno i : tutti) if (i.fine > adesso) quanti++;
        Impegno[] fuori = new Impegno[quanti];
        int j = 0;
        for (Impegno i : tutti) if (i.fine > adesso) fuori[j++] = i;
        return fuori;
    }

    /** Il primo che viene, o null. Lo guarda il riposo. */
    public Impegno prossimo() {
        Impegno[] p = prossimi();
        return p.length > 0 ? p[0] : null;
    }

    /** Quanti ce ne sono oggi, da adesso in poi. */
    public int quantiOggi() {
        int quanti = 0;
        for (Impegno i : prossimi()) if (oggi(i.inizio)) quanti++;
        return quanti;
    }

    /** Che cosa manca, se manca qualcosa. */
    public int guasto() { return guasto; }

    /** Perche' non c'e' niente, detto a chi guarda lo schermo. */
    public String perche() {
        switch (guasto) {
            case SENZA_PERMESSO:  return "manca il permesso di leggere il calendario";
            case SENZA_PROVIDER:  return "su questo tablet il calendario non c'e'";
            case SENZA_CALENDARI: return "nessun calendario: aggiungi l'account Google";
            default:              return "nessun impegno in vista";
        }
    }

    // ---- la lettura ---------------------------------------------------------

    /**
     * Rilegge, su un thread suo.
     *
     * Non ci sono difese contro le chiamate ravvicinate se non una: due letture
     * nello stesso secondo sono la stessa lettura, e la seconda si salta. Serve
     * perche' l'osservatore del provider spara piu' avvisi di fila quando il
     * sincronizzatore scarica una giornata intera.
     */
    public void aggiorna() {
        lavoro.post(new Runnable() {
            @Override public void run() {
                long adesso = android.os.SystemClock.uptimeMillis();
                if (adesso - ultimaLettura < 1000) return;
                ultimaLettura = adesso;
                leggi();
            }
        });
    }

    /**
     * Gli impegni fra due istanti, per chi ne vuole altri da quelli in arrivo.
     *
     * La usa la vista a mese: una query sola per tutto il mese, non una al
     * giorno. La risposta arriva sul thread dell'interfaccia, gia' pronta da
     * disegnare.
     */
    public void chiediFra(final long da, final long a, final Esito esito) {
        if (esito == null) return;
        lavoro.post(new Runnable() {
            @Override public void run() {
                final Impegno[] trovati = leggiFra(da, a);
                ui.post(new Runnable() {
                    @Override public void run() {
                        esito.impegni(trovati == null ? new Impegno[0] : trovati);
                    }
                });
            }
        });
    }

    private void leggi() {
        long da = System.currentTimeMillis() - INDIETRO;
        long a = da + INDIETRO + AVANTI;
        Impegno[] trovati = leggiFra(da, a);
        if (trovati == null) return;          // il guasto l'ha gia' scritto leggiFra
        int come = TUTTO_BENE;
        if (trovati.length == 0 && quantiCalendari() == 0) come = SENZA_CALENDARI;
        cambia(trovati, come);
    }

    /**
     * La query vera. Torna null quando non si e' potuto leggere - e in quel
     * caso ha gia' detto perche' a {@link #guasto}.
     */
    private Impegno[] leggiFra(long da, long a) {
        if (contesto.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            cambia(new Impegno[0], SENZA_PERMESSO);
            return null;
        }

        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, da);
        ContentUris.appendId(b, a);

        // "visible = 1" tiene fuori i calendari che qualcuno ha spento dal
        // telefono - quello dei compleanni, quello delle feste di un altro
        // paese. Se il ROM non avesse quella colonna la query fallirebbe: si
        // riprova senza, invece di restare senza impegni per una colonna.
        Cursor c = null;
        try {
            c = interroga(b.build(), CalendarContract.Instances.VISIBLE + " = 1");
            if (c == null) c = interroga(b.build(), null);
        } catch (SecurityException senzaPermesso) {
            cambia(new Impegno[0], SENZA_PERMESSO);
            return null;
        } catch (Throwable t) {
            // Il provider non c'e' proprio: era il caso di questo tablet finche'
            // Casa non se l'e' rimesso da sola (MainActivity.preparaIlCalendario).
            Log.i(TAG, "calendario: il provider non risponde (" + t + ")");
            cambia(new Impegno[0], SENZA_PROVIDER);
            return null;
        }

        if (c == null) {
            cambia(new Impegno[0], SENZA_PROVIDER);
            return null;
        }

        List<Impegno> trovati = new ArrayList<Impegno>();
        try {
            while (c.moveToNext() && trovati.size() < TETTO) {
                Impegno i = new Impegno();
                i.titolo = c.getString(0);
                if (i.titolo == null || i.titolo.trim().length() == 0) i.titolo = "(senza titolo)";
                i.inizio = c.getLong(1);
                i.fine = c.getLong(2);
                i.tuttoIlGiorno = c.getInt(3) != 0;
                i.dove = c.getString(4);
                i.colore = c.getInt(5);
                if (i.colore != 0) i.colore = 0xFF000000 | i.colore;
                if (i.fine <= i.inizio) i.fine = i.inizio + 60L * 60 * 1000;
                trovati.add(i);
            }
        } catch (Throwable t) {
            Log.w(TAG, "calendario: lettura interrotta", t);
        } finally {
            try { c.close(); } catch (Throwable ignorato) { }
        }

        return trovati.toArray(new Impegno[trovati.size()]);
    }

    private Cursor interroga(Uri uri, String dove) {
        return contesto.getContentResolver().query(uri, new String[] {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DISPLAY_COLOR,
        }, dove, null, CalendarContract.Instances.BEGIN + " ASC");
    }

    /**
     * Quanti calendari conosce il tablet.
     *
     * Serve solo a distinguere due silenzi che si somigliano: « non hai impegni
     * » e « non hai un calendario ». Il secondo si aggiusta, il primo no, e chi
     * guarda ha il diritto di sapere quale dei due sta guardando.
     */
    private int quantiCalendari() {
        Cursor c = null;
        try {
            c = contesto.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[] { CalendarContract.Calendars._ID }, null, null, null);
            return c == null ? 0 : c.getCount();
        } catch (Throwable t) {
            return 0;
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignorato) { }
        }
    }

    private void cambia(final Impegno[] nuovi, final int come) {
        final boolean diverso = come != guasto || !uguali(nuovi, elenco);
        elenco = nuovi;
        guasto = come;
        if (!diverso) return;
        Log.i(TAG, "calendario: " + nuovi.length + " impegni"
                + (come == TUTTO_BENE ? "" : " (" + perche() + ")"));
        ui.post(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < chiGuarda.size(); i++) chiGuarda.get(i).impegniCambiati();
            }
        });
    }

    /** Due elenchi che dicono la stessa cosa non fanno ridisegnare niente: il
     *  sincronizzatore avvisa anche quando non e' cambiato niente per noi. */
    private static boolean uguali(Impegno[] a, Impegno[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i].inizio != b[i].inizio || !a[i].titolo.equals(b[i].titolo)) return false;
        }
        return true;
    }

    // ---- l'avviso dal provider ----------------------------------------------

    /** Si mette in ascolto: da qui in poi il calendario si aggiorna da solo. */
    public void ascolta() {
        if (spia != null) return;
        spia = new ContentObserver(ui) {
            @Override public void onChange(boolean suoi) { aggiorna(); }
        };
        try {
            contesto.getContentResolver().registerContentObserver(
                    CalendarContract.CONTENT_URI, true, spia);
        } catch (Throwable t) {
            // Senza provider non c'e' niente da osservare: non e' un errore,
            // e' l'assenza gia' raccontata da perche().
            spia = null;
        }
        aggiorna();
    }

    public void chiudi() {
        if (spia != null) {
            try { contesto.getContentResolver().unregisterContentObserver(spia); }
            catch (Throwable ignorato) { }
            spia = null;
        }
        lavoro.getLooper().quitSafely();
    }

    // ---- come si scrive un orario -------------------------------------------

    private static final String[] GIORNI = {
        "dom", "lun", "mar", "mer", "gio", "ven", "sab"
    };

    public static boolean oggi(long quando) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTimeInMillis(quando);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean domani(long quando) {
        Calendar a = Calendar.getInstance();
        a.add(Calendar.DAY_OF_YEAR, 1);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(quando);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * L'ora come si dice guardandola: « 18:30 » se e' oggi, « domani 9:00 » se
     * e' domani, « gio 9:00 » piu' in la'.
     *
     * Il giorno si scrive <b>solo quando non e' oggi</b>: su una riga che sta in
     * una colonna stretta, « oggi » davanti a ogni impegno di oggi e' la parola
     * che si ripete di piu' e che dice di meno.
     */
    public static String quando(Impegno i) {
        if (i == null) return "";
        String ora = i.tuttoIlGiorno ? "tutto il giorno"
                : String.format(Locale.ITALIAN, "%tH:%tM", i.inizio, i.inizio);
        if (oggi(i.inizio)) return ora;
        if (domani(i.inizio)) return i.tuttoIlGiorno ? "domani" : "domani " + ora;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(i.inizio);
        String giorno = GIORNI[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH);
        return i.tuttoIlGiorno ? giorno : giorno + " " + ora;
    }

    /** Come lo direbbe la voce: « oggi alle 18 e 30 ». */
    public static String detto(Impegno i) {
        if (i == null) return "";
        StringBuilder b = new StringBuilder();
        if (oggi(i.inizio)) b.append("oggi");
        else if (domani(i.inizio)) b.append("domani");
        else {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(i.inizio);
            b.append(GIORNI[c.get(Calendar.DAY_OF_WEEK) - 1]).append(" ")
             .append(c.get(Calendar.DAY_OF_MONTH));
        }
        if (!i.tuttoIlGiorno) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(i.inizio);
            int ore = c.get(Calendar.HOUR_OF_DAY), min = c.get(Calendar.MINUTE);
            b.append(" alle ").append(ore);
            if (min > 0) b.append(" e ").append(min);
        }
        b.append(": ").append(i.titolo);
        return b.toString();
    }

    /** Una riga per il registro. */
    public String stato() {
        return elenco.length + " impegni, " + (guasto == TUTTO_BENE ? "tutto bene" : perche());
    }
}
