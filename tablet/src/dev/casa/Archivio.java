package dev.casa;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Legge e scrive i file di Casa in getFilesDir().
 *
 * Due decisioni, e la seconda vale piu' della prima.
 *
 * <b>org.json e non una libreria.</b> Fa parte del framework: zero dipendenze,
 * zero dex in piu', ed e' gia' dentro il processo. I file di Casa stanno tutti
 * sotto i quattro kilobyte - sveglie, timer, lampade, stazioni - e per roba
 * cosi' un parser con la reflection sarebbe duecento kilobyte di codice per
 * risparmiare venti righe.
 *
 * <b>La scrittura e' atomica</b>, e questa non e' pignoleria. Casa sta appesa a
 * un muro, attaccata a un alimentatore che qualcuno puo' staccare: se la
 * corrente se ne va nel mezzo di una scrittura, un file troncato al riavvio
 * significa <i>tutte le sveglie cancellate</i>, in silenzio, e il primo che se
 * ne accorge e' chi non si e' svegliato. Si scrive su un file temporaneo, si
 * forza sul disco, e solo allora si rinomina: il rename e' l'unica operazione
 * che il filesystem promette indivisibile.
 */
public final class Archivio {

    private static final String TAG = "Casa.Archivio";

    private Archivio() {}

    /** Il contenuto, oppure null se il file non c'e' o non si legge. */
    public static JSONObject leggi(Context c, String nome) {
        File f = new File(c.getFilesDir(), nome);
        if (!f.exists()) return null;
        InputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            byte[] tutto = new byte[(int) f.length()];
            int letti = 0;
            while (letti < tutto.length) {
                int n = in.read(tutto, letti, tutto.length - letti);
                if (n < 0) break;
                letti += n;
            }
            return new JSONObject(new String(tutto, 0, letti, "UTF-8"));
        } catch (Exception e) {
            // Un file rotto non deve impedire a Casa di partire: si torna null,
            // e chi chiama riparte dai valori di fabbrica. E' preferibile un
            // elenco di stazioni vuoto a una schermata nera.
            Log.w(TAG, nome + " illeggibile, riparto da zero", e);
            return null;
        } finally {
            chiudi(in);
        }
    }

    /** Scrive, o lascia sul disco quello che c'era prima. Mai una via di mezzo. */
    public static boolean scrivi(Context c, String nome, JSONObject dati) {
        File definitivo = new File(c.getFilesDir(), nome);
        File temporaneo = new File(c.getFilesDir(), nome + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(temporaneo);
            out.write(dati.toString().getBytes("UTF-8"));
            out.flush();
            // Senza questo, i byte stanno ancora nella cache del kernel: il
            // rename riuscirebbe e il contenuto no.
            out.getFD().sync();
            out.close();
            out = null;

            if (definitivo.exists() && !definitivo.delete()) {
                Log.w(TAG, "non riesco a togliere il vecchio " + nome);
            }
            if (!temporaneo.renameTo(definitivo)) {
                Log.w(TAG, "rename fallito per " + nome);
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "non ho potuto scrivere " + nome, e);
            return false;
        } finally {
            chiudi(out);
            if (temporaneo.exists()) temporaneo.delete();
        }
    }

    /** Il file esiste? Serve ai marcatori, dove il contenuto non conta. */
    public static boolean ceLAbbiamo(Context c, String nome) {
        return new File(c.getFilesDir(), nome).exists();
    }

    private static void chiudi(java.io.Closeable qualcosa) {
        if (qualcosa == null) return;
        try { qualcosa.close(); } catch (Exception ignorata) { }
    }
}
