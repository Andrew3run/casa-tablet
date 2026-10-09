// Il motore audio di Gestione Home.
//
// Sta in C# e non in PowerShell per una ragione sola: qui si tocca ogni
// singolo campione. Un file di un secondo a 16 kHz sono sedicimila campioni, e
// un ciclo PowerShell su sedicimila elementi ci mette piu' di quanto ci metta
// C# su un milione. Con ottanta file da generare la differenza e' fra un
// secondo e diversi minuti.
//
// Si compila a volo con Add-Type dentro GestioneHome.ps1, quindi deve stare
// nel C# che capisce il compilatore di Windows PowerShell 5.1: niente
// interpolazione di stringhe, niente "out var", niente membri a freccia.
//
// Il formato e' uno solo - PCM 16 bit, mono, 16000 Hz - lo stesso di
// Onda.java sul tablet. Quello che esce di qui deve poter tornare dentro
// parola/si senza che il tablet lo rifiuti.

using System;
using System.Collections.Generic;
using System.IO;

namespace Casa
{
    /// <summary>Un file WAV letto in memoria.</summary>
    public class Onda
    {
        public const int HZ = 16000;

        public short[] Campioni;
        public int Hz;

        public Onda(short[] campioni, int hz)
        {
            Campioni = campioni;
            Hz = hz;
        }

        public double Secondi
        {
            get { return Campioni.Length / (double)Hz; }
        }

        // ---- lettura -------------------------------------------------------

        /// <summary>
        /// Legge un WAV PCM 16 bit mono. Torna null se non lo e'.
        ///
        /// I blocchi si percorrono invece di saltare al byte 44: fra "fmt " e
        /// "data" un WAV puo' avere di tutto, e saltare funziona finche' non
        /// arriva il primo file scritto da un altro programma.
        /// </summary>
        public static Onda Leggi(string percorso)
        {
            byte[] tutto = File.ReadAllBytes(percorso);
            if (tutto.Length < 44) return null;
            if (Stringa(tutto, 0) != "RIFF" || Stringa(tutto, 8) != "WAVE") return null;

            int canali = 0, hz = 0, bit = 0;
            int p = 12;
            while (p + 8 <= tutto.Length)
            {
                string nome = Stringa(tutto, p);
                int lung = BitConverter.ToInt32(tutto, p + 4);
                int corpo = p + 8;
                if (lung < 0 || corpo + lung > tutto.Length) lung = tutto.Length - corpo;

                if (nome == "fmt " && lung >= 16)
                {
                    canali = BitConverter.ToInt16(tutto, corpo + 2);
                    hz = BitConverter.ToInt32(tutto, corpo + 4);
                    bit = BitConverter.ToInt16(tutto, corpo + 14);
                }
                else if (nome == "data")
                {
                    if (bit != 16) return null;
                    int quanti = lung / 2;
                    short[] letti = new short[quanti];
                    Buffer.BlockCopy(tutto, corpo, letti, 0, quanti * 2);
                    // Un file stereo arrivato per sbaglio si riduce a mono
                    // facendo la media dei due canali, invece di rifiutarlo:
                    // qui siamo sul PC, dove i file arrivano anche da fuori.
                    if (canali == 2)
                    {
                        short[] mono = new short[quanti / 2];
                        for (int i = 0; i < mono.Length; i++)
                            mono[i] = (short)((letti[i * 2] + letti[i * 2 + 1]) / 2);
                        letti = mono;
                    }
                    else if (canali != 1) return null;
                    return new Onda(letti, hz);
                }
                p = corpo + lung + (lung & 1);
            }
            return null;
        }

        private static string Stringa(byte[] b, int p)
        {
            if (p + 4 > b.Length) return "";
            return System.Text.Encoding.ASCII.GetString(b, p, 4);
        }

        // ---- scrittura -----------------------------------------------------

        public void Scrivi(string percorso)
        {
            string cartella = Path.GetDirectoryName(percorso);
            if (cartella != null && cartella.Length > 0 && !Directory.Exists(cartella))
                Directory.CreateDirectory(cartella);

            using (FileStream f = new FileStream(percorso, FileMode.Create, FileAccess.Write))
            using (BinaryWriter w = new BinaryWriter(f))
            {
                int dati = Campioni.Length * 2;
                w.Write(System.Text.Encoding.ASCII.GetBytes("RIFF"));
                w.Write(36 + dati);
                w.Write(System.Text.Encoding.ASCII.GetBytes("WAVE"));
                w.Write(System.Text.Encoding.ASCII.GetBytes("fmt "));
                w.Write(16);
                w.Write((short)1);
                w.Write((short)1);
                w.Write(Hz);
                w.Write(Hz * 2);
                w.Write((short)2);
                w.Write((short)16);
                w.Write(System.Text.Encoding.ASCII.GetBytes("data"));
                w.Write(dati);
                byte[] byteDati = new byte[dati];
                Buffer.BlockCopy(Campioni, 0, byteDati, 0, dati);
                w.Write(byteDati);
            }
        }

        // ---- misure --------------------------------------------------------

        public double Rms()
        {
            return RmsDi(Campioni, 0, Campioni.Length);
        }

        public static double RmsDi(short[] x, int da, int quanti)
        {
            if (quanti <= 0) return 0;
            double somma = 0;
            for (int i = 0; i < quanti; i++)
            {
                double v = x[da + i];
                somma += v * v;
            }
            return Math.Sqrt(somma / quanti);
        }

        public double PiccoDb()
        {
            int massimo = 1;
            for (int i = 0; i < Campioni.Length; i++)
            {
                int v = Math.Abs((int)Campioni[i]);
                if (v > massimo) massimo = v;
            }
            return 20.0 * Math.Log10(massimo / 32767.0);
        }

        /// <summary>Quanto e' saturo: la frazione di campioni al fondo scala.
        /// Sopra qualche millesimo la registrazione e' rovinata e va rifatta,
        /// non aggiustata.</summary>
        public double Saturazione()
        {
            int quanti = 0;
            for (int i = 0; i < Campioni.Length; i++)
                if (Campioni[i] >= 32700 || Campioni[i] <= -32700) quanti++;
            return Campioni.Length == 0 ? 0 : quanti / (double)Campioni.Length;
        }
    }

    /// <summary>
    /// Le trasformazioni con cui da poche registrazioni se ne ottengono molte.
    ///
    /// <para>A cosa servono davvero, che e' la domanda che conta. Con il
    /// confronto per deformazione temporale che gira adesso sul tablet, i
    /// modelli buoni sono <b>le registrazioni vere</b>: aggiungerne di
    /// artificiali come modelli allarga la rete e fa entrare anche quello che
    /// non doveva entrare.</para>
    ///
    /// <para>I file generati qui servono a due cose oneste:</para>
    /// <list type="number">
    /// <item>a <b>verificare</b> la soglia. "Hey Home" con la radio a sei
    /// decibel sopra la voce aggancia ancora? Si prende il campione vero, ci si
    /// mescola il rumore vero registrato in quella stanza, e si guarda. E' una
    /// risposta che nessuna soglia scelta a occhio puo' dare;</item>
    /// <item>a costruire il <b>materiale</b> per il passo dopo - una piccola
    /// rete allenata come si allena "Hey Google", che di esempi ne vuole
    /// migliaia e non dieci. Nessuno di questi file si butta.</item>
    /// </list>
    /// </summary>
    public class Aumenta
    {
        /// <summary>Una variante da generare: come si chiama e cosa fa.</summary>
        public class Ricetta
        {
            public string Nome;
            public string Che;      // guadagno | rumore | velocita | riverbero
            public double Valore;
            public Ricetta(string nome, string che, double valore)
            {
                Nome = nome; Che = che; Valore = valore;
            }
        }

        /// <summary>
        /// Le ricette di serie.
        ///
        /// Scelte per coprire quello che cambia davvero fra una volta e
        /// l'altra in cucina, e non per fare numero:
        /// <list type="bullet">
        /// <item><b>rumore</b> a tre rapporti - e' il caso vero, la radio
        /// accesa. Il rumore non e' bianco: e' quello registrato in quella
        /// stanza, che e' l'unico che somiglia al problema;</item>
        /// <item><b>guadagno</b> - la stessa parola detta da vicino e da
        /// lontano;</item>
        /// <item><b>velocita'</b> - detta in fretta e detta piano. Cambia
        /// anche l'intonazione, il che avvicina un po' a un'altra voce;</item>
        /// <item><b>riverbero</b> - la stanza vuota contro la stanza
        /// arredata.</item>
        /// </list>
        /// </summary>
        public static List<Ricetta> Serie()
        {
            List<Ricetta> r = new List<Ricetta>();
            r.Add(new Ricetta("snr18", "rumore", 18));
            r.Add(new Ricetta("snr12", "rumore", 12));
            r.Add(new Ricetta("snr06", "rumore", 6));
            r.Add(new Ricetta("piano", "guadagno", -8));
            r.Add(new Ricetta("forte", "guadagno", 4));
            r.Add(new Ricetta("lento", "velocita", 0.91));
            r.Add(new Ricetta("svelto", "velocita", 1.10));
            r.Add(new Ricetta("stanza", "riverbero", 0.28));
            return r;
        }

        /// <summary>Alza o abbassa, senza far saturare: se il picco andrebbe
        /// oltre il fondo scala si alza un po' meno, invece di tosare le
        /// creste - che sarebbe distorsione, non volume.</summary>
        public static short[] Guadagno(short[] x, double db)
        {
            double f = Math.Pow(10.0, db / 20.0);
            int picco = 1;
            for (int i = 0; i < x.Length; i++)
            {
                int v = Math.Abs((int)x[i]);
                if (v > picco) picco = v;
            }
            double tetto = 32000.0 / picco;
            if (f > tetto) f = tetto;

            short[] y = new short[x.Length];
            for (int i = 0; i < x.Length; i++) y[i] = Limita(x[i] * f);
            return y;
        }

        /// <summary>
        /// Mescola rumore vero alla voce, a un rapporto segnale-rumore dato.
        ///
        /// Il livello del rumore si calcola sul <b>parlato</b> e non su tutto
        /// il campione: se si prendesse la media dell'intero file, i silenzi
        /// prima e dopo abbasserebbero il conto e il rumore verrebbe messo
        /// piu' piano di quanto si e' chiesto - cioe' la prova risulterebbe
        /// piu' facile di quella che si voleva fare.
        /// </summary>
        public static short[] ConRumore(short[] voce, short[] rumore, double snrDb, Random caso)
        {
            if (rumore == null || rumore.Length < 1600) return null;

            int[] estremi = Estremi(voce);
            int da = estremi[0], quanti = estremi[1];
            double livelloVoce = Onda.RmsDi(voce, da, quanti);
            if (livelloVoce < 1) return null;

            // Un punto a caso del rumore: due varianti dello stesso campione
            // non devono portarsi dietro lo stesso pezzo di telegiornale.
            int inizio = rumore.Length > voce.Length
                       ? caso.Next(rumore.Length - voce.Length) : 0;
            double livelloRumore = Onda.RmsDi(rumore, inizio,
                                              Math.Min(voce.Length, rumore.Length - inizio));
            if (livelloRumore < 1) return null;

            double voluto = livelloVoce / Math.Pow(10.0, snrDb / 20.0);
            double f = voluto / livelloRumore;

            short[] y = new short[voce.Length];
            for (int i = 0; i < voce.Length; i++)
            {
                double r = rumore[(inizio + i) % rumore.Length] * f;
                y[i] = Limita(voce[i] + r);
            }
            return y;
        }

        /// <summary>
        /// Piu' svelta o piu' lenta, con l'intonazione che sale e scende
        /// insieme - come quando si accelera un nastro.
        ///
        /// Non e' un cambio di velocita' "pulito" e va bene cosi': quello che
        /// si vuole non e' la stessa voce piu' rapida, e' una voce un po'
        /// diversa. L'interpolazione lineare fra due campioni basta a queste
        /// distanze; una sinc sarebbe piu' fedele a un segnale che poi si
        /// riduce comunque a tredici numeri ogni centesimo di secondo.
        /// </summary>
        public static short[] Velocita(short[] x, double fattore)
        {
            if (fattore <= 0.5 || fattore >= 2.0) return null;
            int quanti = (int)(x.Length / fattore);
            short[] y = new short[quanti];
            for (int i = 0; i < quanti; i++)
            {
                double p = i * fattore;
                int j = (int)p;
                double q = p - j;
                double a = x[Math.Min(j, x.Length - 1)];
                double b = x[Math.Min(j + 1, x.Length - 1)];
                y[i] = Limita(a + (b - a) * q);
            }
            return y;
        }

        /// <summary>
        /// Un po' di stanza attorno alla voce: tre riflessioni corte.
        ///
        /// Tre e non una coda vera. Quello che cambia le MFCC di una parola
        /// corta sono le <b>prime</b> riflessioni - i venti o trenta
        /// millisecondi che tornano dal muro e dal tavolo - non il riverbero
        /// lungo, che a un secondo di distanza e' gia' sotto il rumore.
        /// </summary>
        public static short[] Riverbero(short[] x, double quanto)
        {
            int[] ritardi = { 17 * Onda.HZ / 1000, 29 * Onda.HZ / 1000, 43 * Onda.HZ / 1000 };
            double[] pesi = { quanto, quanto * 0.6, quanto * 0.35 };
            short[] y = new short[x.Length];
            for (int i = 0; i < x.Length; i++)
            {
                double v = x[i];
                for (int k = 0; k < ritardi.Length; k++)
                {
                    int j = i - ritardi[k];
                    if (j >= 0) v += x[j] * pesi[k];
                }
                y[i] = Limita(v);
            }
            return y;
        }

        /// <summary>
        /// Dove comincia e dove finisce il parlato: la stessa regola di
        /// Orecchio.ritaglia sul tablet - un ottavo del picco, con ottanta
        /// millisecondi di margine. Deve essere la stessa, o il PC e il tablet
        /// direbbero due cose diverse sullo stesso file.
        /// </summary>
        public static int[] Estremi(short[] x)
        {
            int blocco = Onda.HZ / 50;
            int blocchi = x.Length / blocco;
            if (blocchi < 4) return new int[] { 0, x.Length };

            double massimo = 0;
            double[] livelli = new double[blocchi];
            for (int b = 0; b < blocchi; b++)
            {
                livelli[b] = Onda.RmsDi(x, b * blocco, blocco);
                if (livelli[b] > massimo) massimo = livelli[b];
            }
            if (massimo < 240) return new int[] { 0, x.Length };

            double soglia = Math.Max(massimo * 0.125, 120);
            int primo = -1, ultimo = -1;
            for (int b = 0; b < blocchi; b++)
                if (livelli[b] >= soglia) { if (primo < 0) primo = b; ultimo = b; }
            if (primo < 0 || ultimo <= primo) return new int[] { 0, x.Length };

            int daP = Math.Max(0, (primo - 4) * blocco);
            int aP = Math.Min(x.Length, (ultimo + 5) * blocco);
            return new int[] { daP, aP - daP };
        }

        public static short[] Ritaglia(short[] x)
        {
            int[] e = Estremi(x);
            if (e[0] == 0 && e[1] == x.Length) return x;
            short[] y = new short[e[1]];
            Array.Copy(x, e[0], y, 0, e[1]);
            return y;
        }

        private static short Limita(double v)
        {
            if (v > 32767.0) return 32767;
            if (v < -32768.0) return -32768;
            return (short)Math.Round(v);
        }

        // ---- il giro completo ----------------------------------------------

        /// <summary>Quello che e' successo, da scrivere nel registro.</summary>
        public class Esito
        {
            public int Generati;
            public int Saltati;
            public List<string> Detto = new List<string>();
        }

        /// <summary>
        /// Genera le varianti di ogni campione in <paramref name="cartellaSi"/>,
        /// usando come rumore i file in <paramref name="cartellaNo"/>.
        ///
        /// La cartella di destinazione viene <b>svuotata</b> prima: le varianti
        /// sono roba derivata, e tenere in giro quelle di due generazioni fa
        /// vorrebbe dire non sapere piu' da quali originali vengono.
        /// </summary>
        public static Esito Genera(string cartellaSi, string cartellaNo, string destinazione,
                                   List<Ricetta> ricette, int seme)
        {
            Esito esito = new Esito();
            Random caso = new Random(seme);

            List<short[]> rumori = new List<short[]>();
            if (Directory.Exists(cartellaNo))
            {
                string[] file = Directory.GetFiles(cartellaNo, "*.wav");
                foreach (string f in file)
                {
                    Onda o = Onda.Leggi(f);
                    if (o != null && o.Hz == Onda.HZ && o.Campioni.Length > 1600)
                        rumori.Add(o.Campioni);
                }
            }
            if (rumori.Count == 0)
                esito.Detto.Add("Nessun rumore in no/: le varianti con rumore si saltano, "
                              + "e sono le uniche che dicono qualcosa sul caso vero.");

            if (Directory.Exists(destinazione))
                foreach (string vecchio in Directory.GetFiles(destinazione, "*.wav"))
                    File.Delete(vecchio);
            else
                Directory.CreateDirectory(destinazione);

            if (!Directory.Exists(cartellaSi))
            {
                esito.Detto.Add("Manca la cartella si/: scarica prima i campioni dal tablet.");
                return esito;
            }

            string[] originali = Directory.GetFiles(cartellaSi, "*.wav");
            foreach (string percorso in originali)
            {
                Onda o = Onda.Leggi(percorso);
                if (o == null || o.Hz != Onda.HZ)
                {
                    esito.Saltati++;
                    esito.Detto.Add(Path.GetFileName(percorso) + ": non e' mono 16 bit a 16 kHz.");
                    continue;
                }
                string radice = Path.GetFileNameWithoutExtension(percorso);

                foreach (Ricetta r in ricette)
                {
                    short[] y = null;
                    if (r.Che == "rumore")
                    {
                        if (rumori.Count == 0) continue;
                        y = ConRumore(o.Campioni, rumori[caso.Next(rumori.Count)], r.Valore, caso);
                    }
                    else if (r.Che == "guadagno") y = Guadagno(o.Campioni, r.Valore);
                    else if (r.Che == "velocita") y = Velocita(o.Campioni, r.Valore);
                    else if (r.Che == "riverbero") y = Riverbero(o.Campioni, r.Valore);

                    if (y == null) { esito.Saltati++; continue; }
                    string nome = radice + "--" + r.Nome + ".wav";
                    new Onda(y, Onda.HZ).Scrivi(Path.Combine(destinazione, nome));
                    esito.Generati++;
                }
            }
            return esito;
        }
    }
}
