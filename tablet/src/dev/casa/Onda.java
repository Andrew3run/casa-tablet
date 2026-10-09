package dev.casa;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Un file WAV, letto e scritto a mano.
 *
 * <b>Perche' WAV e non un formato compresso.</b> Questi file esistono per
 * essere <i>guardati</i>: dal PC per ascoltarli e aumentarli, dal tablet per
 * ricavarne le impronte. Un formato con perdita toglierebbe proprio quello su
 * cui si lavora - le alte frequenze dove sta la « h » di "Hey" e la « m »
 * finale di "Home" - e uno senza perdita vorrebbe una libreria. Una
 * registrazione di un secondo a 16 kHz sono 32 kilobyte: cento campioni stanno
 * in tre megabyte, su undici gigabyte liberi.
 *
 * <b>Perche' 16 kHz e non 44,1.</b> E' la frequenza a cui lavorano tutti i
 * riconoscitori di parlato, ed e' quella che il riconoscitore di Android
 * accetta senza ricampionare. Sopra gli 8 kHz di banda utile non c'e' voce, e
 * ci sarebbe il triplo dei campioni da masticare a ogni fotogramma.
 *
 * Si legge e si scrive <b>solo</b> il caso che serve: PCM 16 bit, un canale,
 * 16000 Hz. Un WAV arrivato da fuori con un'altra forma viene rifiutato invece
 * di essere convertito male in silenzio: se il PC manda un file sbagliato,
 * meglio saperlo subito che scoprirlo come una parola che non aggancia piu'.
 */
public final class Onda {

    /** Sotto {@code Casa} come tutto il resto, col nome del pezzo nel
     *  messaggio: il perche' sta su {@link MainActivity#TAG}. */
    private static final String TAG = MainActivity.TAG;

    /** L'unica frequenza di tutto il progetto. Cambiarla qui vorrebbe dire
     *  rifare le impronte: i campioni vecchi non si confronterebbero piu' con
     *  i nuovi. */
    public static final int HZ = 16000;

    private Onda() {}

    // ---- scrittura ---------------------------------------------------------

    /**
     * Scrive {@code quanti} campioni in un WAV, in modo atomico.
     *
     * Atomico per la stessa ragione dell'{@link Archivio}: il tablet sta
     * appeso a un muro e la corrente puo' andarsene nel mezzo. Un WAV troncato
     * non e' un file mezzo buono: e' un'intestazione che dichiara una
     * lunghezza che non c'e', e chi lo rilegge trova rumore dove finisce il
     * file.
     */
    public static boolean scrivi(File destinazione, short[] campioni, int quanti) {
        if (campioni == null || quanti <= 0) return false;
        quanti = Math.min(quanti, campioni.length);

        File temporaneo = new File(destinazione.getParentFile(), destinazione.getName() + ".tmp");
        FileOutputStream out = null;
        try {
            File cartella = destinazione.getParentFile();
            if (cartella != null && !cartella.exists() && !cartella.mkdirs()) {
                Log.w(TAG, "onda: non riesco a creare " + cartella);
                return false;
            }
            out = new FileOutputStream(temporaneo);
            out.write(intestazione(quanti));

            // Un blocco alla volta invece di un array grande quanto tutto: qui
            // la memoria e' un gigabyte, e una registrazione di due secondi
            // convertita in byte sarebbe un secondo array da 64 KB per niente.
            byte[] blocco = new byte[2048];
            int scritti = 0;
            while (scritti < quanti) {
                int n = Math.min(quanti - scritti, blocco.length / 2);
                for (int i = 0; i < n; i++) {
                    short s = campioni[scritti + i];
                    blocco[i * 2]     = (byte) (s & 0xFF);
                    blocco[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
                }
                out.write(blocco, 0, n * 2);
                scritti += n;
            }
            out.flush();
            // Senza questo i byte stanno ancora nella cache del kernel: il
            // rename riuscirebbe e il contenuto no.
            out.getFD().sync();
            out.close();
            out = null;

            if (destinazione.exists() && !destinazione.delete()) {
                Log.w(TAG, "onda: non riesco a togliere il vecchio " + destinazione.getName());
            }
            if (!temporaneo.renameTo(destinazione)) {
                Log.w(TAG, "onda: rename fallito per " + destinazione.getName());
                return false;
            }
            return true;
        } catch (IOException e) {
            Log.w(TAG, "onda: non ho potuto scrivere " + destinazione.getName(), e);
            return false;
        } finally {
            chiudi(out);
            if (temporaneo.exists()) temporaneo.delete();
        }
    }

    /** I 44 byte di intestazione di un WAV PCM 16 bit mono a {@link #HZ}. */
    private static byte[] intestazione(int campioni) {
        int dati = campioni * 2;
        byte[] h = new byte[44];
        ascii(h, 0, "RIFF");
        intero(h, 4, 36 + dati);          // quanto resta dopo questi otto byte
        ascii(h, 8, "WAVE");
        ascii(h, 12, "fmt ");
        intero(h, 16, 16);                // lunghezza del blocco fmt
        breve(h, 20, (short) 1);          // 1 = PCM, senza compressione
        breve(h, 22, (short) 1);          // un canale
        intero(h, 24, HZ);
        intero(h, 28, HZ * 2);            // byte al secondo
        breve(h, 32, (short) 2);          // byte per campione, tutti i canali
        breve(h, 34, (short) 16);         // bit per campione
        ascii(h, 36, "data");
        intero(h, 40, dati);
        return h;
    }

    // ---- lettura -----------------------------------------------------------

    /**
     * Legge un WAV mono 16 bit a {@link #HZ}, oppure null.
     *
     * Non si fida della posizione dei blocchi: fra "fmt " e "data" un WAV puo'
     * avere di tutto - LIST, fact, i metadati che ci mette chi lo ha scritto -
     * e saltare al byte 44 funziona finche' non arriva il primo file scritto da
     * un altro programma. Qui i blocchi si percorrono.
     */
    public static short[] leggi(File sorgente) {
        FileInputStream in = null;
        try {
            long lunghezza = sorgente.length();
            if (lunghezza < 44) return null;
            if (lunghezza > 8L * 1024 * 1024) {
                Log.w(TAG, "onda: " + sorgente.getName() + ": troppo lungo, lo salto");
                return null;
            }
            in = new FileInputStream(sorgente);
            byte[] tutto = new byte[(int) lunghezza];
            int letti = 0;
            while (letti < tutto.length) {
                int n = in.read(tutto, letti, tutto.length - letti);
                if (n < 0) break;
                letti += n;
            }
            if (letti < 44 || !"RIFF".equals(stringa(tutto, 0))
                           || !"WAVE".equals(stringa(tutto, 8))) {
                Log.w(TAG, "onda: " + sorgente.getName() + ": non e' un WAV");
                return null;
            }

            int canali = 0, hz = 0, bit = 0;
            int p = 12;
            while (p + 8 <= letti) {
                String nome = stringa(tutto, p);
                int lung = leggiIntero(tutto, p + 4);
                int corpo = p + 8;
                if (lung < 0 || corpo + lung > letti) lung = letti - corpo;  // ultimo blocco troncato

                if ("fmt ".equals(nome) && lung >= 16) {
                    canali = leggiBreve(tutto, corpo + 2);
                    hz     = leggiIntero(tutto, corpo + 4);
                    bit    = leggiBreve(tutto, corpo + 14);
                } else if ("data".equals(nome)) {
                    if (canali != 1 || hz != HZ || bit != 16) {
                        Log.w(TAG, "onda: " + sorgente.getName() + ": vuole mono 16 bit a " + HZ
                                + " Hz, invece e' " + canali + " canali, " + bit
                                + " bit, " + hz + " Hz");
                        return null;
                    }
                    int quanti = lung / 2;
                    short[] campioni = new short[quanti];
                    for (int i = 0; i < quanti; i++) {
                        campioni[i] = (short) ((tutto[corpo + i * 2] & 0xFF)
                                             | (tutto[corpo + i * 2 + 1] << 8));
                    }
                    return campioni;
                }
                // I blocchi stanno su confini pari: uno di lunghezza dispari
                // porta un byte di riempimento che non conta nella lunghezza.
                p = corpo + lung + (lung & 1);
            }
            Log.w(TAG, "onda: " + sorgente.getName() + ": nessun blocco data");
            return null;
        } catch (Exception e) {
            Log.w(TAG, "onda: non ho potuto leggere " + sorgente.getName(), e);
            return null;
        } finally {
            chiudi(in);
        }
    }

    /** Quanto dura in millisecondi, senza leggere i campioni. */
    public static int durataMs(File f) {
        long dati = f.length() - 44;
        if (dati <= 0) return 0;
        return (int) (dati * 1000 / (HZ * 2));
    }

    // ---- byte --------------------------------------------------------------

    private static void ascii(byte[] b, int p, String s) {
        for (int i = 0; i < s.length(); i++) b[p + i] = (byte) s.charAt(i);
    }

    private static void intero(byte[] b, int p, int v) {
        b[p]     = (byte) (v & 0xFF);
        b[p + 1] = (byte) ((v >> 8) & 0xFF);
        b[p + 2] = (byte) ((v >> 16) & 0xFF);
        b[p + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static void breve(byte[] b, int p, short v) {
        b[p]     = (byte) (v & 0xFF);
        b[p + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private static String stringa(byte[] b, int p) {
        if (p + 4 > b.length) return "";
        return new String(b, p, 4, java.nio.charset.Charset.forName("US-ASCII"));
    }

    private static int leggiIntero(byte[] b, int p) {
        if (p + 4 > b.length) return -1;
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8)
             | ((b[p + 2] & 0xFF) << 16) | ((b[p + 3] & 0xFF) << 24);
    }

    private static int leggiBreve(byte[] b, int p) {
        if (p + 2 > b.length) return -1;
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
    }

    private static void chiudi(java.io.Closeable q) {
        if (q == null) return;
        try { q.close(); } catch (Exception ignorata) { }
    }
}
