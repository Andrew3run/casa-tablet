package dev.casa;

import android.graphics.Paint;

/**
 * Il testo che sta nel suo spazio.
 *
 * <b>Perche' esiste.</b> Prima ogni sezione si tagliava le stringhe da sola
 * contando i caratteri - {@code accorcia(s, 26)}, {@code accorcia(s, 44)},
 * {@code accorcia(s, 12)} - e contare i caratteri non e' misurare.
 * "Radiofreccia - mi collego" sono venticinque caratteri: passavano il
 * controllo dei ventisei, ed erano cinquecento pixel dentro una scheda che ne
 * aveva trecentottanta. La scritta usciva dal vetro e finiva sui comandi.
 *
 * Il numero indovinato una volta non regge nemmeno il resto del progetto: qui
 * i corpi del testo sono <b>frazioni dell'altezza dello schermo</b>, quindi lo
 * stesso ventisei sarebbe sbagliato in modo diverso su un altro pannello. E
 * una M e una i non sono larghe uguale.
 *
 * Qui invece si misura con lo <b>stesso Paint con cui si disegna</b>, che e'
 * l'unica cosa che sa quanto e' larga davvero una stringa.
 *
 * <b>Misurare dentro onDraw sarebbe la cosa da non fare</b>: {@code
 * measureText} costa e {@code substring} alloca, e questo progetto ha la regola
 * di non allocare niente dentro il disegno. Per questo non ci sono funzioni da
 * chiamare a ogni fotogramma ma <b>oggetti che si ricordano l'ultima
 * risposta</b>: finche' il testo, lo spazio e il corpo non cambiano - cioe'
 * quasi sempre, su una schermata che sta ferma per minuti - tornano la stringa
 * di prima senza toccare niente. Si misura quando cambia l'ora, non sessanta
 * volte al secondo.
 *
 * Chi disegna tiene una {@link Riga} per ogni scritta, come tiene un Paint.
 */
public final class Testo {

    private Testo() {}

    /** Un carattere solo, non tre punti: tre punti sono tre larghezze tolte al
     *  testo, ed e' proprio lo spazio che manca. */
    private static final String PUNTINI = "…";

    /**
     * Le vocali accentate, e la lettera che ci sta sotto.
     *
     * Sta qui perche' con il testo si fanno due cose - lo si disegna e lo si
     * <b>confronta</b> - e questa serve alla seconda.
     */
    private static final String ACCENTATE = "àáâäãèéêëìíîïòóôöõùúûüçñ";
    private static final String PIATTE    = "aaaaaeeeeiiiiooooouuuucn";

    /**
     * La stessa frase senza accenti: « che ora è » diventa « che ora e ».
     *
     * <b>Non e' un abbellimento, e' quello che teneva mezze regole spente.</b>
     * Le regole dei comandi sono scritte senza accenti - "che ora e", "piu'
     * forte", "vai avanti cosi" - mentre il riconoscitore vocale gli accenti li
     * scrive tutti. Le due scritture non si incontravano mai: chi diceva « che
     * ora è » veniva capito solo perche' un'altra regola, piu' in basso,
     * prendeva la frase per il rotto della cuffia, e « più forte » non veniva
     * capito affatto.
     *
     * Si appiattisce tutto una volta sola, all'ingresso, e da li' in poi
     * <b>tutte le regole possono essere scritte come vengono</b>. Le stesse due
     * righe passano sui nomi con cui si confronta - stazioni, lampade, routine,
     * playlist, comandi scritti dal PC - o una lampada chiamata « Veranda
     * ovest » starebbe bene e una chiamata « Perche' no » no.
     */
    public static String senzaAccenti(String s) {
        if (s == null) return "";
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int dove = ACCENTATE.indexOf(c);
            if (dove < 0) {
                if (b != null) b.append(c);
                continue;
            }
            // Solo quando ce n'e' davvero uno si alloca: quasi tutte le frasi
            // non ne hanno nemmeno uno, e questo gira a ogni comando.
            if (b == null) b = new StringBuilder(s.length()).append(s, 0, i);
            b.append(PIATTE.charAt(dove));
        }
        return b == null ? s : b.toString();
    }

    private static final String[] NESSUNA_RIGA = {};

    /**
     * Taglia con i puntini quel che non ci sta. Il Paint dev'essere gia' al
     * corpo con cui si disegnera'.
     *
     * E' pubblica perche' serve anche a {@link Blocco}, ma dentro onDraw non si
     * chiama mai da sola: si passa per una {@link Riga}, che se la ricorda.
     */
    public static String taglia(Paint p, String s, float spazio) {
        if (s == null || s.length() == 0 || spazio <= 0f) return "";
        if (p.measureText(s) <= spazio) return s;

        float segno = p.measureText(PUNTINI);
        if (spazio <= segno) return "";          // non ci sta nemmeno il segno

        int quanti = p.breakText(s, true, spazio - segno, null);
        // Lo spazio prima dei puntini si legge come un buco nella riga.
        while (quanti > 0 && s.charAt(quanti - 1) == ' ') quanti--;
        return quanti <= 0 ? PUNTINI : s.substring(0, quanti) + PUNTINI;
    }

    /**
     * Una scritta su una riga sola, che si ricorda l'ultimo taglio.
     *
     * Tutti e due i metodi <b>lasciano il Paint al corpo che hanno usato</b>,
     * quindi si disegna subito dopo senza rimetterlo a posto - e per la stessa
     * ragione ogni chiamata dice sempre il corpo che vuole, invece di fidarsi
     * di com'era rimasto il Paint dopo la scritta di prima.
     */
    public static final class Riga {

        private String sorgente;
        private float spazio = -1f, massimo = -1f, minimo = -1f;
        private String reso = "";
        private float corpo;

        /** A corpo fisso: quel che avanza si taglia. */
        public String in(Paint p, String s, float spazio, float corpo) {
            return calcola(p, s, spazio, corpo, corpo);
        }

        /**
         * A corpo variabile: prima si rimpicciolisce fino a {@code minimo}, e
         * solo se nemmeno li' ci sta si taglia.
         *
         * E' la strada giusta per le scritte che portano il significato - il
         * nome della stazione, quello che sta suonando: leggere "Radiofrecc..."
         * in grande e' peggio che leggere "Radiofreccia" un po' piu' piccolo.
         * Per le scritte di contorno basta {@link #in}.
         */
        public String adatta(Paint p, String s, float spazio, float massimo, float minimo) {
            return calcola(p, s, spazio, massimo, minimo);
        }

        /** Il corpo con cui e' stata resa: serve a impaginare quel che segue. */
        public float corpo() { return corpo; }

        private String calcola(Paint p, String s, float spazio, float massimo, float minimo) {
            if (s == null) s = "";
            if (spazio == this.spazio && massimo == this.massimo && minimo == this.minimo
                    && s.equals(sorgente)) {
                p.setTextSize(corpo);
                return reso;                     // la strada di tutti i fotogrammi
            }
            this.sorgente = s;
            this.spazio = spazio;
            this.massimo = massimo;
            this.minimo = minimo;

            // A passi dell'otto per cento: sotto non si vede la differenza e si
            // pagano misure in piu'. Dal massimo al minimo sono pochi giri.
            float dim = massimo;
            p.setTextSize(dim);
            while (dim > minimo && p.measureText(s) > spazio) {
                dim = Math.max(minimo, dim * 0.92f);
                p.setTextSize(dim);
            }
            corpo = dim;
            reso = taglia(p, s, spazio);
            return reso;
        }
    }

    /**
     * Una frase su piu' righe, che si ricorda l'ultimo impaginato.
     *
     * Serve a quello che dice una persona - "accendi la luce della cucina e
     * abbassa la radio". Tagliare quella con i puntini e' il taglio sbagliato:
     * chi legge "accendi la luce della cuc..." non sa se Casa ha capito tutta
     * la frase o meta'. Si manda a capo, e si taglia solo se nemmeno le righe
     * concesse bastano.
     *
     * L'impaginazione alloca, ma succede quando la frase cambia - una volta per
     * comando - non a ogni fotogramma.
     */
    public static final class Blocco {

        private String sorgente;
        private float spazio = -1f, corpo = -1f;
        private int massimoRighe = -1;
        private String[] righe = NESSUNA_RIGA;

        public String[] in(Paint p, String s, float spazio, float corpo, int massimoRighe) {
            if (s == null) s = "";
            if (spazio == this.spazio && corpo == this.corpo
                    && massimoRighe == this.massimoRighe && s.equals(sorgente)) {
                p.setTextSize(corpo);
                return righe;
            }
            this.sorgente = s;
            this.spazio = spazio;
            this.corpo = corpo;
            this.massimoRighe = massimoRighe;
            p.setTextSize(corpo);
            righe = impagina(p, s, spazio, massimoRighe);
            return righe;
        }

        private static String[] impagina(Paint p, String s, float spazio, int massimo) {
            if (s.length() == 0 || spazio <= 0f || massimo <= 0) return NESSUNA_RIGA;
            if (p.measureText(s) <= spazio) return new String[] { s };

            java.util.ArrayList<String> fuori = new java.util.ArrayList<String>(massimo);
            int i = 0, n = s.length();
            while (i < n && fuori.size() < massimo) {
                while (i < n && s.charAt(i) == ' ') i++;
                if (i >= n) break;

                // L'ultima riga concessa si porta tutto il resto, tagliato: e'
                // li' che i puntini vogliono dire "c'era dell'altro".
                if (fuori.size() == massimo - 1) {
                    fuori.add(taglia(p, s.substring(i), spazio));
                    break;
                }

                int quanti = p.breakText(s, i, n, true, spazio, null);
                if (quanti <= 0) quanti = 1;
                int fine = i + quanti;
                if (fine < n) {
                    // Si torna all'ultimo spazio: spezzare in mezzo a una parola
                    // si legge come un errore di stampa.
                    int ultimo = s.lastIndexOf(' ', fine);
                    if (ultimo > i) fine = ultimo;
                }
                fuori.add(s.substring(i, fine).trim());
                i = fine;
            }
            return fuori.toArray(new String[fuori.size()]);
        }
    }
}
