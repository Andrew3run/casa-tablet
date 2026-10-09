// L'aspetto di Gestione Home: i colori, la scala, e i pezzi disegnati a mano.
//
// PERCHE' NON SI E' RIFATTA IN REACT. La domanda e' stata posta, e la risposta
// non e' affezione a WinForms. Una finestra in React vuole Node, un npm
// install, un passo di build e - per parlare con adb - un processo di servizio
// dietro: quattro cose che oggi non ci sono e che fra sei mesi si rompono da
// sole, per un programma che deve aprirsi con un doppio clic mentre si ha un
// cavo in mano. Il ragionamento e' scritto in docs/gestione-dal-pc.md ed e' lo
// stesso che ha tenuto questo progetto senza Gradle.
//
// Quello che mancava non era il framework: era il DISEGNO. WinForms di serie da'
// dei rettangoli grigi perche' nessuno gli ha detto altro. Qui gli si dice.
//
// DA DOVE VIENE QUESTO ASPETTO. Non e' inventato: e' quello del tablet, portato
// a sessanta centimetri invece che a un metro.
//
//   i colori   da Tinte.java, gia' condivisi da prima
//   la scala   da Misure.java: sei corpi e cinque spazi, e tutto il resto e'
//              un multiplo. Prima ogni pagina si sceglieva le sue misure a
//              occhio, ed e' esattamente l'errore che sul tablet era gia' stato
//              fatto e corretto
//   le icone   da Icone.java, gli stessi Material Symbols (vedi Icone.cs)
//   due raggi  uno per i pannelli, uno per le pastiglie. Due e non tre: due
//              raggi si leggono come due grandezze di cosa, tre si leggono come
//              un errore
//
// Chi apre questa finestra ha il tablet davanti. Due iconografie e due scale
// diverse per le stesse sei sezioni sarebbero due apparecchi diversi.
//
// LA REGOLA DEL MONOSPAZIATO. Il carattere a larghezza fissa non e' una scelta
// estetica: dice "questo l'ha scritto la macchina". Identificativi Tuya, nomi
// di pacchetto, percorsi, registro. Tutto quello che diciamo noi - titoli,
// spiegazioni, tasti - sta nel proporzionale. Si impara in due schermate e poi
// si legge senza pensarci.
//
// C# per Windows PowerShell 5.1: niente interpolazione di stringhe, niente
// "out var", niente membri a freccia. E solo ASCII.

using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace Casa
{
    /// <summary>
    /// Far vedere la finestra, che sembra una cosa che succede da sola.
    ///
    /// NON SUCCEDE DA SOLA SE IL PROGRAMMA E' STATO LANCIATO NASCOSTO. Il
    /// collegamento sul desktop passa da wscript e chiede "nessuna finestra",
    /// per non far lampeggiare la console nera di PowerShell. Windows pero'
    /// quel "nessuna finestra" lo mette nei dati di avvio del processo, e li'
    /// resta: <b>la prima finestra vera che il programma apre se lo prende</b>,
    /// e nasce invisibile.
    ///
    /// Il sintomo e' il peggiore che ci sia: il processo parte, gira, non da'
    /// nessun errore, e non si vede niente. Cercandolo si trova vivo, con le
    /// sue finestre tutte a "visibile = falso". E siccome la console e'
    /// nascosta, non c'e' nemmeno un messaggio da leggere.
    ///
    /// Una riga di Win32 e passa: si dice esplicitamente di mostrarla.
    /// </summary>
    public static class Finestra
    {
        [DllImport("user32.dll")]
        private static extern bool ShowWindow(IntPtr finestra, int comando);

        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr finestra);

        [DllImport("kernel32.dll")]
        private static extern IntPtr GetConsoleWindow();

        [DllImport("dwmapi.dll")]
        private static extern int DwmSetWindowAttribute(IntPtr finestra, int quale,
                                                        ref int valore, int quanto);

        [DllImport("user32.dll")]
        private static extern bool EnableWindow(IntPtr finestra, bool si);

        private const int NASCONDI = 0;

        /// <summary>
        /// La barra del titolo scura.
        ///
        /// Tutto quello che c'e' sotto e' nero-blu, e sopra Windows ci mette la
        /// sua banda: se il sistema e' in tema chiaro, la finestra ha un
        /// cappello bianco che nel resto del programma non c'e' da nessuna
        /// parte. Non e' un dettaglio da poco su una finestrella piccola, dove
        /// il cappello e' un quinto di quello che si vede.
        ///
        /// DUE NUMERI E NON UNO. 20 e' l'attributo di Windows 11 e degli ultimi
        /// 10; le prime versioni che sapevano fare il tema scuro usavano il 19.
        /// Si prova il nuovo, e solo se non lo conosce si prova il vecchio.
        /// Un Windows che non conosce nessuno dei due risponde un errore e la
        /// finestra resta com'era: niente di rotto, solo un cappello chiaro.
        /// </summary>
        public static void Scura(IntPtr finestra)
        {
            if (finestra == IntPtr.Zero) return;
            try
            {
                int si = 1;
                if (DwmSetWindowAttribute(finestra, 20, ref si, 4) != 0)
                {
                    DwmSetWindowAttribute(finestra, 19, ref si, 4);
                }
            }
            catch (Exception) { }
        }

        /// <summary>
        /// SW_SHOWNORMAL, e non SW_SHOW. Misurato, perche' la differenza non si
        /// deduce da nessuna documentazione: su una finestra nata invisibile
        /// perche' il processo era stato lanciato nascosto, <b>ShowWindow con
        /// SW_SHOW (5) non fa niente</b> - torna vero e la finestra resta
        /// invisibile - mentre SW_SHOWNORMAL (1) la mostra. E' lo stato di
        /// avvio ereditato che vince sul primo comando, e solo un comando che
        /// dichiara anche la POSIZIONE della finestra glielo porta via.
        /// </summary>
        private const int MOSTRA_NORMALE = 1;

        /// <summary>La mostra e la mette davanti. Si chiama quando la finestra
        ///  e' gia' costruita: prima non c'e' nessun handle da mostrare.</summary>
        public static void Mostra(IntPtr finestra)
        {
            if (finestra == IntPtr.Zero) return;
            ShowWindow(finestra, MOSTRA_NORMALE);
            SetForegroundWindow(finestra);
        }

        /// <summary>
        /// La finestra smette di sentire il mouse e la tastiera - o ricomincia.
        ///
        /// PERCHE' NON Form.Enabled. Fa la stessa cosa e in piu' la RACCONTA:
        /// WinForms passa lo spento a tutti i figli, e i figli si ridisegnano
        /// smorti. Durante i due secondi in cui si manda la configurazione al
        /// tablet, mezza finestra sbiadiva e tornava - un lampeggio che dice
        /// "guarda qui" proprio mentre non c'e' niente da guardare.
        ///
        /// Questa e' la riga di Win32 che sta sotto, e si ferma li': la finestra
        /// non riceve piu' clic, e continua a disegnarsi esattamente com'era.
        /// E' quello che fa Windows alla finestra madre mentre una finestra di
        /// dialogo e' aperta.
        ///
        /// Serve perche' l'attesa di un lavoro lungo gira su DoEvents (vedi
        /// Aspetta): senza, un secondo clic partirebbe DENTRO il lavoro che sta
        /// ancora andando, e due "manda al tablet" sovrapposti spingono due
        /// file sopra lo stesso file.
        /// </summary>
        public static void Ascolta(IntPtr finestra, bool si)
        {
            if (finestra == IntPtr.Zero) return;
            EnableWindow(finestra, si);
        }

        /// <summary>
        /// Nasconde la console, se ce n'e' una.
        ///
        /// Serve a chi lancia il programma a mano da un prompt: la console
        /// resterebbe li' dietro, e chiuderla chiuderebbe il programma. Dal
        /// collegamento non c'e' nessuna console da nascondere, e questa non fa
        /// niente.
        /// </summary>
        public static void NascondiLaConsole()
        {
            IntPtr c = GetConsoleWindow();
            if (c != IntPtr.Zero) ShowWindow(c, NASCONDI);
        }
    }

    /// <summary>I colori, i corpi, gli spazi. Una volta sola, qui.</summary>
    public static class Aspetto
    {
        // ---- i colori, da Tinte.java ---------------------------------------

        public static readonly Color Fondo    = Da("#0B0F14");
        public static readonly Color Pannello = Da("#141C27");
        public static readonly Color Rilievo  = Da("#1D2836");
        public static readonly Color Bordo    = Da("#243244");
        public static readonly Color Testo    = Da("#F2F5F7");
        public static readonly Color Medio    = Da("#B9C6D2");
        public static readonly Color Tenue    = Da("#7C8B99");
        public static readonly Color Spento   = Da("#55636F");

        /// <summary>Le tinte delle sezioni, le stesse che il tablet usa per
        ///  quelle sezioni. Non sono decorazione: sono il modo in cui si
        ///  riconosce dove si e' senza leggere il titolo.</summary>
        public static readonly Color Blu      = Da("#3D7EFF");   // Home
        public static readonly Color Viola    = Da("#B48CFF");   // Musica
        public static readonly Color Verde    = Da("#5FD0A0");   // Radio
        public static readonly Color Azzurro  = Da("#6FD3F2");   // App
        public static readonly Color Ambra    = Da("#FFB454");   // Orologio
        public static readonly Color Giallo   = Da("#F2D06B");   // Luci
        public static readonly Color Rosso    = Da("#E06C75");   // allarme
        public static readonly Color Spotify  = Da("#1ED760");   // il marchio
        public static readonly Color Notizie  = Da("#FF8F6B");   // Notizie

        // ---- i sei corpi ---------------------------------------------------
        //
        // La regola di lettura del tablet e' "a un metro si legge voce e
        // sopra". Qui si legge da sessanta centimetri, quindi la scala e' la
        // stessa ma stretta: i rapporti fra un gradino e l'altro restano.

        public static readonly Font Micro  = new Font("Segoe UI", 7.5f, FontStyle.Bold);
        public static readonly Font Nota   = new Font("Segoe UI", 8f);
        public static readonly Font Corpo  = new Font("Segoe UI", 9f);
        public static readonly Font Forte  = new Font("Segoe UI Semibold", 9f);
        public static readonly Font Voce   = new Font("Segoe UI", 11.5f);
        public static readonly Font Titolo = new Font("Segoe UI Light", 17f);
        public static readonly Font Cifra  = new Font("Segoe UI Light", 24f);

        /// <summary>Quello che ha scritto la macchina.</summary>
        public static readonly Font Fisso  = new Font("Consolas", 8.5f);
        public static readonly Font FissoForte = new Font("Consolas", 8.5f, FontStyle.Bold);

        // ---- i cinque spazi ------------------------------------------------

        /// <summary>Il filo fra due cose che sono la stessa cosa.</summary>
        public const int S1 = 4;
        /// <summary>Fra due tessere della stessa griglia.</summary>
        public const int S2 = 8;
        /// <summary>L'aria dentro una tessera piccola.</summary>
        public const int S3 = 12;
        /// <summary>L'aria dentro un pannello.</summary>
        public const int S4 = 18;
        /// <summary>Fra due blocchi che parlano di cose diverse.</summary>
        public const int S5 = 28;

        /// <summary>I due raggi, e non ce n'e' un terzo.</summary>
        public const int Raggio = 10;
        public const int RaggioPiccolo = 6;

        // ---- gli attrezzi --------------------------------------------------

        public static Color Da(string hex)
        {
            return ColorTranslator.FromHtml(hex);
        }

        /// <summary>
        /// Il righello: una superficie di un pixel che serve solo a misurare.
        ///
        /// PERCHE' NON LA Graphics DEL CONTROLLO. Misurare vuol dire avere una
        /// Graphics, e quella di un controllo esiste solo dopo che il controllo
        /// ha una finestra vera. Ma un testo lo si vuole misurare PRIMA di
        /// decidere quanto spazio dargli - che e' tutto il punto - e in quel
        /// momento la finestra non c'e': CreateGraphics gliene fabbricherebbe
        /// una apposta per buttarla via subito dopo.
        ///
        /// Con lo stesso suggerimento del disegno vero: un carattere misurato
        /// in un modo e disegnato in un altro torna largo quello che non e', e
        /// il conto sbaglia sempre dalla parte che si vede.
        /// </summary>
        private static readonly Graphics righello = FaiIlRighello();

        private static Graphics FaiIlRighello()
        {
            Graphics g = Graphics.FromImage(new Bitmap(1, 1));
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            return g;
        }

        /// <summary>Quanto e' largo questo testo, su una riga sola.</summary>
        public static int Largo(string testo, Font f)
        {
            if (string.IsNullOrEmpty(testo)) return 0;
            lock (righello)
            {
                return (int)Math.Ceiling(righello.MeasureString(testo, f).Width) + 1;
            }
        }

        /// <summary>Quanto e' alto questo testo, andando a capo dentro una
        ///  larghezza data. E' la misura che impedisce a una spiegazione di
        ///  finire tagliata a meta' dalla casella che le sta sotto.</summary>
        public static int Alto(string testo, Font f, int largo)
        {
            if (string.IsNullOrEmpty(testo)) return 0;
            lock (righello)
            {
                SizeF s = righello.MeasureString(testo, f, largo);
                return (int)Math.Ceiling(s.Height) + 1;
            }
        }

        /// <summary>Lo stesso colore, piu' trasparente. Serve per i veli: sul
        ///  tablet il vetro smerigliato fa questo mestiere.</summary>
        public static Color Velo(Color c, int alfa)
        {
            return Color.FromArgb(alfa, c);
        }

        /// <summary>Il colore mescolato al fondo. Meglio della trasparenza dove
        ///  sotto non c'e' niente da far trasparire.</summary>
        public static Color Misto(Color sopra, Color sotto, float quanto)
        {
            return Color.FromArgb(
                (int)(sotto.R + (sopra.R - sotto.R) * quanto),
                (int)(sotto.G + (sopra.G - sotto.G) * quanto),
                (int)(sotto.B + (sopra.B - sotto.B) * quanto));
        }

        public static GraphicsPath Arrotonda(RectangleF r, float raggio)
        {
            GraphicsPath p = new GraphicsPath();
            float d = raggio * 2f;
            if (d <= 0 || r.Width <= d || r.Height <= d)
            {
                p.AddRectangle(r);
                return p;
            }
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        public static void Riempi(Graphics g, RectangleF r, float raggio, Color c)
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            using (GraphicsPath p = Arrotonda(r, raggio))
            using (SolidBrush b = new SolidBrush(c)) g.FillPath(b, p);
        }

        public static void Contorna(Graphics g, RectangleF r, float raggio, Color c, float spessore)
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            // Mezzo pixel dentro: un contorno disegnato sul bordo esatto viene
            // tagliato a meta' dal rettangolo del controllo, e si vede.
            RectangleF q = new RectangleF(r.X + spessore / 2f, r.Y + spessore / 2f,
                                          r.Width - spessore, r.Height - spessore);
            using (GraphicsPath p = Arrotonda(q, raggio))
            using (Pen pen = new Pen(c, spessore)) g.DrawPath(pen, p);
        }

        /// <summary>Il testo, con l'antialiasing giusto per il fondo scuro.</summary>
        public static void Scrivi(Graphics g, string testo, Font f, Color c,
                                  RectangleF dove, StringAlignment orizzontale,
                                  StringAlignment verticale, bool aCapo)
        {
            if (string.IsNullOrEmpty(testo)) return;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            using (StringFormat sf = new StringFormat())
            using (SolidBrush b = new SolidBrush(c))
            {
                sf.Alignment = orizzontale;
                sf.LineAlignment = verticale;
                sf.Trimming = StringTrimming.EllipsisCharacter;
                if (!aCapo) sf.FormatFlags |= StringFormatFlags.NoWrap;
                g.DrawString(testo, f, b, dove, sf);
            }
        }

        /// <summary>Un'etichetta di sezione: maiuscoletto tenue, spaziato.
        ///  Sul tablet e' la stessa cosa - IN CASA, IN RIPRODUZIONE, APRI - e
        ///  serve a dire di che cosa parla il pannello senza rubargli una riga
        ///  di titolo vero.</summary>
        public static void Etichetta(Graphics g, string testo, RectangleF dove, Color c)
        {
            if (string.IsNullOrEmpty(testo)) return;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            // Lo spazio fra le lettere si fa a mano: GDI+ non ha il tracking, e
            // una maiuscoletta stretta si legge peggio di una spaziata.
            //
            // Lo spazio VERO va misurato a parte. MeasureString con il formato
            // tipografico non conta gli spazi ai bordi della stringa, e per una
            // stringa di un carattere solo lo spazio E' tutto bordo: tornava
            // zero, e le etichette uscivano attaccate -
            // "QUELLECHEILTABLETCONOSCE". Si vede subito e non si capisce
            // subito, perche' il difetto sta nel misurare, non nel disegnare.
            float x = dove.X;
            using (SolidBrush b = new SolidBrush(c))
            {
                foreach (char ch in testo.ToUpperInvariant())
                {
                    if (ch == ' ')
                    {
                        x += g.MeasureString("n", Micro, PointF.Empty,
                                             StringFormat.GenericTypographic).Width + 1.1f;
                        continue;
                    }
                    string s = ch.ToString();
                    g.DrawString(s, Micro, b, x, dove.Y);
                    x += g.MeasureString(s, Micro, PointF.Empty,
                                         StringFormat.GenericTypographic).Width + 1.1f;
                }
            }
        }
    }

    // =====================================================================
    //  Il pannello: la tessera di vetro del tablet, vista da vicino
    // =====================================================================

    /// <summary>
    /// Un riquadro con gli angoli tondi, un'etichetta e un filo di tinta.
    ///
    /// Sul tablet i pannelli sono vetro smerigliato: la sfocatura si calcola
    /// una volta all'avvio e da li' costa un rettangolo con una texture. Qui
    /// sotto non c'e' niente da sfocare - il fondo e' piatto - quindi il vetro
    /// si riduce a quello che di lui si legge davvero da lontano: un piano
    /// leggermente piu' chiaro del fondo, con un bordo appena piu' chiaro
    /// ancora.
    /// </summary>
    public class Pannello : Panel
    {
        private string titolo = "";
        private Color tinta = Color.Empty;

        public Pannello()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            BackColor = Aspetto.Fondo;
            ForeColor = Aspetto.Testo;
            Font = Aspetto.Corpo;
        }

        /// <summary>L'etichetta in alto. Vuota: nessuna banda, e il contenuto
        ///  parte da sopra.</summary>
        public string Titolo
        {
            get { return titolo; }
            set { titolo = value == null ? "" : value; Invalidate(); }
        }

        /// <summary>La tinta della sezione. Vuota: nessun filo.</summary>
        public Color Tinta
        {
            get { return tinta; }
            set { tinta = value; Invalidate(); }
        }

        /// <summary>Quanto scende il contenuto per far posto all'etichetta.</summary>
        public int Banda
        {
            get { return titolo.Length == 0 ? 0 : Aspetto.S4 + Aspetto.S3; }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Aspetto.Fondo);
            RectangleF r = new RectangleF(0, 0, Width - 1, Height - 1);

            Aspetto.Riempi(g, r, Aspetto.Raggio, Aspetto.Pannello);
            Aspetto.Contorna(g, r, Aspetto.Raggio, Aspetto.Bordo, 1f);

            if (tinta != Color.Empty)
            {
                // Un filo, non una cornice: dice di che sezione si parla e
                // sparisce appena si smette di cercarlo.
                using (GraphicsPath p = Aspetto.Arrotonda(r, Aspetto.Raggio))
                {
                    Region prima = g.Clip;
                    g.SetClip(p);
                    using (SolidBrush b = new SolidBrush(tinta))
                    {
                        g.FillRectangle(b, 0, 0, Width, 2);
                    }
                    g.Clip = prima;
                }
            }

            if (titolo.Length > 0)
            {
                Aspetto.Etichetta(g, titolo,
                    new RectangleF(Aspetto.S4, Aspetto.S3 + 2, Width - Aspetto.S4 * 2, 14),
                    Aspetto.Tenue);
            }
        }
    }

    // =====================================================================
    //  Il tasto
    // =====================================================================

    public enum Rango
    {
        /// <summary>L'azione principale della schermata. Una per schermata.</summary>
        Primario,
        /// <summary>Tutto il resto.</summary>
        Normale,
        /// <summary>Quello che si fa di rado: contorno appena visibile.</summary>
        Quieto,
        /// <summary>Quello che toglie qualcosa.</summary>
        Pericolo
    }

    /// <summary>
    /// Un tasto disegnato a mano, con l'icona e i tre stati.
    ///
    /// Il Button di WinForms non sa fare gli angoli tondi ne' un passaggio di
    /// colore sotto il cursore, e "FlatStyle = Flat" da' un rettangolo con un
    /// bordo di un pixel che sembra un campo di testo. Sono cinquanta righe di
    /// disegno contro un pulsante che si riconosce come tale.
    /// </summary>
    public class Tasto : Control, IButtonControl
    {
        private bool sopra, giu;
        private Color tinta = Aspetto.Medio;
        private string icona = "";
        private Rango rango = Rango.Normale;

        public Tasto()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw
                     | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
            Font = Aspetto.Forte;
            Cursor = Cursors.Hand;
            Height = 32;
        }

        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }
        public string Icona { get { return icona; } set { icona = value == null ? "" : value; Invalidate(); } }
        public Rango Rango { get { return rango; } set { rango = value; Invalidate(); } }

        // ---- il tasto di una finestrella -----------------------------------
        //
        // PERCHE' IButtonControl. Questo e' un Control, non un Button, e la
        // differenza si vede solo quando lo si mette in una finestra di
        // dialogo: "$ok.DialogResult = 'OK'" falliva con « impossibile trovare
        // la proprieta' DialogResult », e "$f.AcceptButton = $ok" pure, perche'
        // il modulo vuole un IButtonControl. Il sintomo era che il tasto
        // « Chiudi » non chiudeva - e siccome l'errore arrivava mentre la
        // finestrella si costruiva, non si apriva affatto.
        //
        // Sono tre membri: l'esito, il "sei tu quello di default" (che qui non
        // cambia il disegno - a dire qual e' il tasto principale ci pensa
        // Rango.Primario, che si vede molto di piu' di un bordo), e la
        // pressione da tastiera.
        private DialogResult esito = DialogResult.None;

        public DialogResult DialogResult { get { return esito; } set { esito = value; } }

        public void NotifyDefault(bool valore) { }

        public void PerformClick() { if (Enabled) OnClick(EventArgs.Empty); }

        // Prima quello che il tasto doveva fare, poi la chiusura: e' l'ordine
        // del Button di WinForms, e conta - un tasto che salva e chiude deve
        // salvare mentre la finestra c'e' ancora.
        protected override void OnClick(EventArgs e)
        {
            base.OnClick(e);
            if (esito == DialogResult.None) return;
            Form f = FindForm();
            if (f != null) f.DialogResult = esito;
        }

        protected override void OnMouseEnter(EventArgs e) { sopra = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { sopra = false; giu = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnMouseDown(MouseEventArgs e) { giu = true; Invalidate(); base.OnMouseDown(e); }
        protected override void OnMouseUp(MouseEventArgs e) { giu = false; Invalidate(); base.OnMouseUp(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            Color perno = rango == Rango.Pericolo ? Aspetto.Rosso : tinta;
            RectangleF r = new RectangleF(0, 0, Width - 1, Height - 1);

            Color fondo, segno, bordo;
            if (rango == Rango.Primario)
            {
                // Il pieno: e' l'unico tasto della schermata che si trova senza
                // cercarlo, ed e' quello che si voleva premere.
                fondo = giu ? Aspetto.Misto(perno, Color.Black, 0.75f)
                            : (sopra ? Aspetto.Misto(perno, Color.White, 0.88f) : perno);
                segno = Aspetto.Fondo;
                bordo = Color.Empty;
            }
            else if (rango == Rango.Quieto)
            {
                fondo = sopra ? Aspetto.Rilievo : Color.Empty;
                segno = sopra ? Aspetto.Medio : Aspetto.Tenue;
                bordo = Color.Empty;
            }
            else
            {
                fondo = giu ? Aspetto.Misto(perno, Aspetto.Pannello, 0.22f)
                            : (sopra ? Aspetto.Misto(perno, Aspetto.Pannello, 0.12f) : Aspetto.Rilievo);
                segno = sopra ? Aspetto.Misto(perno, Aspetto.Testo, 0.55f) : perno;
                bordo = sopra ? perno : Aspetto.Misto(perno, Aspetto.Pannello, 0.35f);
            }

            if (!Enabled)
            {
                fondo = rango == Rango.Primario ? Aspetto.Rilievo : Color.Empty;
                segno = Aspetto.Spento;
                bordo = Aspetto.Bordo;
            }

            if (fondo != Color.Empty) Aspetto.Riempi(g, r, Aspetto.RaggioPiccolo, fondo);
            if (bordo != Color.Empty) Aspetto.Contorna(g, r, Aspetto.RaggioPiccolo, bordo, 1f);

            float x = Aspetto.S3;
            if (icona.Length > 0 && Icone.Ce(icona))
            {
                float lato = Math.Min(16f, Height - 12f);
                Icone.Disegna(g, icona, new RectangleF(x, (Height - lato) / 2f, lato, lato), segno);
                x += lato + Aspetto.S2;
            }

            // Il testo si centra su quello che resta dopo l'icona, non sul
            // tasto intero: con l'icona a sinistra e il testo centrato sul
            // tutto, la scritta sembra spostata a destra.
            RectangleF dove = new RectangleF(x, 0, Width - x - Aspetto.S3, Height);
            StringAlignment come = icona.Length > 0 ? StringAlignment.Near : StringAlignment.Center;
            Aspetto.Scrivi(g, Text, Font, segno, dove, come, StringAlignment.Center, false);
        }
    }

    // =====================================================================
    //  La pastiglia di stato
    // =====================================================================

    /// <summary>
    /// Uno stato, come pastiglia invece che come parola.
    ///
    /// "device owner: si" e' una riga da leggere; una pastiglia verde con
    /// scritto "si" si vede senza leggerla. Su una schermata che dice nove
    /// stati insieme, la differenza e' fra guardare e cercare.
    /// </summary>
    public class Pastiglia : Control
    {
        private Color tinta = Aspetto.Tenue;
        private bool pieno;

        public Pastiglia()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw
                     | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
            Font = Aspetto.Nota;
            Height = 20;
        }

        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }
        /// <summary>Pieno per quello che vale la pena vedere da lontano.</summary>
        public bool Pieno { get { return pieno; } set { pieno = value; Invalidate(); } }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            if (string.IsNullOrEmpty(Text)) return;
            RectangleF r = new RectangleF(0, 0, Width - 1, Height - 1);
            if (pieno)
            {
                Aspetto.Riempi(g, r, Height / 2f, Aspetto.Misto(tinta, Aspetto.Pannello, 0.22f));
                Aspetto.Contorna(g, r, Height / 2f, Aspetto.Misto(tinta, Aspetto.Pannello, 0.55f), 1f);
            }
            Aspetto.Scrivi(g, Text, Font, tinta, r, StringAlignment.Center,
                           StringAlignment.Center, false);
        }
    }

    // =====================================================================
    //  La voce della barra a sinistra
    // =====================================================================

    /// <summary>
    /// Una pagina nella colonna di sinistra: icona, nome, e la tessera piena
    /// quando ci si e' dentro.
    ///
    /// E' la stessa convenzione della barra del tablet - contornata quando si e'
    /// altrove, piena quando si e' qui - e la stessa che usano Android e iOS
    /// per dire "sei qui" senza scrivere niente.
    /// </summary>
    public class VoceBarra : Control
    {
        private bool sopra, attiva;
        private Color tinta = Aspetto.Blu;
        private string icona = "";

        public VoceBarra()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            BackColor = Aspetto.Pannello;
            Font = Aspetto.Corpo;
            Cursor = Cursors.Hand;
            Height = 38;
        }

        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }
        public string Icona { get { return icona; } set { icona = value == null ? "" : value; Invalidate(); } }
        public bool Attiva { get { return attiva; } set { attiva = value; Invalidate(); } }

        protected override void OnMouseEnter(EventArgs e) { sopra = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { sopra = false; Invalidate(); base.OnMouseLeave(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Aspetto.Pannello);
            RectangleF r = new RectangleF(6, 1, Width - 12, Height - 2);

            Color segno = Aspetto.Tenue;
            if (attiva)
            {
                Aspetto.Riempi(g, r, Aspetto.RaggioPiccolo,
                               Aspetto.Misto(tinta, Aspetto.Pannello, 0.16f));
                segno = tinta;
            }
            else if (sopra)
            {
                Aspetto.Riempi(g, r, Aspetto.RaggioPiccolo, Aspetto.Rilievo);
                segno = Aspetto.Medio;
            }

            if (attiva)
            {
                // La linguetta sul bordo sinistro: sul tablet e' quella che
                // dice "sei qui" anche di sfuggita, e costa tre pixel.
                using (SolidBrush b = new SolidBrush(tinta))
                using (GraphicsPath p = Aspetto.Arrotonda(
                           new RectangleF(0, r.Y + 7, 3, r.Height - 14), 1.5f))
                {
                    g.SmoothingMode = SmoothingMode.AntiAlias;
                    g.FillPath(b, p);
                }
            }

            float lato = 17f;
            Icone.Disegna(g, icona, new RectangleF(r.X + 11, (Height - lato) / 2f, lato, lato), segno);
            Aspetto.Scrivi(g, Text, attiva ? Aspetto.Forte : Font, segno,
                           new RectangleF(r.X + 11 + lato + 10, 0, r.Width - lato - 24, Height),
                           StringAlignment.Near, StringAlignment.Center, false);
        }
    }

    // =====================================================================
    //  L'intestazione di una pagina
    // =====================================================================

    /// <summary>
    /// Il titolo della pagina, e una riga che dice a che cosa serve.
    ///
    /// La riga sotto il titolo non e' decorazione: sette pagine che si chiamano
    /// "Le luci", "Routine", "Le app" si distinguono per nome, ma non
    /// dicono da sole che cosa ci si fa dentro. Una riga sola lo dice, e chi la
    /// sa gia' non la legge.
    /// </summary>
    public class Intestazione : Control
    {
        private string sottotitolo = "";
        private string icona = "";
        private Color tinta = Aspetto.Blu;

        public Intestazione()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            BackColor = Aspetto.Fondo;
            Height = 52;
        }

        public string Sottotitolo
        {
            get { return sottotitolo; }
            set { sottotitolo = value == null ? "" : value; Invalidate(); }
        }
        public string Icona { get { return icona; } set { icona = value == null ? "" : value; Invalidate(); } }
        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Aspetto.Fondo);

            float x = 0;
            if (icona.Length > 0 && Icone.Ce(icona))
            {
                float lato = 26f;
                // Il quadratino della tinta dietro l'icona: e' lo stesso segno
                // della voce attiva nella barra, e serve a legare la pagina
                // alla riga da cui ci si e' arrivati.
                Aspetto.Riempi(g, new RectangleF(0, 4, 38, 38), Aspetto.RaggioPiccolo,
                               Aspetto.Misto(tinta, Aspetto.Fondo, 0.16f));
                Icone.Disegna(g, icona, new RectangleF(6, 10, lato, lato), tinta);
                x = 38 + Aspetto.S3;
            }

            Aspetto.Scrivi(g, Text, Aspetto.Titolo, Aspetto.Testo,
                           new RectangleF(x, 0, Width - x, 30),
                           StringAlignment.Near, StringAlignment.Center, false);
            if (sottotitolo.Length > 0)
            {
                Aspetto.Scrivi(g, sottotitolo, Aspetto.Nota, Aspetto.Tenue,
                               new RectangleF(x, 28, Width - x, 20),
                               StringAlignment.Near, StringAlignment.Center, false);
            }
        }
    }

    // =====================================================================
    //  La cornice di un campo
    // =====================================================================

    /// <summary>
    /// Il piano su cui si scrive: un rettangolo tondo che si accende quando il
    /// campo dentro ha il fuoco.
    ///
    /// Una TextBox di WinForms non sa avere gli angoli tondi ne' un anello di
    /// fuoco. Metterla dentro un pannello che li disegna e' l'unico modo, e ha
    /// un vantaggio: <b>si vede dove si sta scrivendo</b> anche senza guardare
    /// il cursore, che su una schermata con dodici campi non e' poco.
    /// </summary>
    public class Cornice : Panel
    {
        private bool fuoco;
        private Color tinta = Aspetto.Blu;

        public Cornice()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            BackColor = Aspetto.Pannello;
        }

        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }

        /// <summary>Il campo che sta dentro. Serve a chi vuole agganciarsi ai
        ///  suoi eventi - un invio che manda la frase, per esempio.</summary>
        public TextBox Dentro;

        /// <summary>Il controllo da tenere dentro, gia' senza bordo suo.</summary>
        public void Metti(Control dentro)
        {
            dentro.Location = new Point(Aspetto.S2, (Height - dentro.Height) / 2);
            dentro.Width = Width - Aspetto.S2 * 2;
            dentro.GotFocus += new EventHandler(Acceso);
            dentro.LostFocus += new EventHandler(Spento);
            Controls.Add(dentro);
            Dentro = dentro as TextBox;
        }

        /// <summary>
        /// Il testo del campo dentro, non quello della cornice.
        ///
        /// La cornice e' un pannello, e un pannello ha un Text suo che non si
        /// vede da nessuna parte. Senza questa proprieta' chi scrive
        /// {@code $campo.Text} - cioe' tutte le pagine - scriverebbe in un posto
        /// che non esiste, e non se ne accorgerebbe: nessun errore, e il campo
        /// resta vuoto.
        /// </summary>
        public override string Text
        {
            get { return Dentro == null ? base.Text : Dentro.Text; }
            set { if (Dentro == null) base.Text = value; else Dentro.Text = value; }
        }

        private void Acceso(object mittente, EventArgs e) { fuoco = true; Invalidate(); }
        private void Spento(object mittente, EventArgs e) { fuoco = false; Invalidate(); }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(BackColor);
            RectangleF r = new RectangleF(0, 0, Width - 1, Height - 1);
            Aspetto.Riempi(g, r, Aspetto.RaggioPiccolo, Aspetto.Rilievo);
            Aspetto.Contorna(g, r, Aspetto.RaggioPiccolo,
                             fuoco ? tinta : Aspetto.Bordo, fuoco ? 1.4f : 1f);
        }
    }

    // =====================================================================
    //  L'elenco e la tendina
    // =====================================================================

    /// <summary>
    /// Un elenco a una colonna, disegnato.
    ///
    /// La ListBox di serie sceglie il blu di sistema per la riga selezionata:
    /// in mezzo a una tavolozza scelta una per una, e' l'unico colore che non
    /// viene da nessuna parte.
    /// </summary>
    public class Elenco : ListBox
    {
        private Color tinta = Aspetto.Blu;

        public Elenco()
        {
            DrawMode = DrawMode.OwnerDrawFixed;
            BorderStyle = BorderStyle.None;
            BackColor = Aspetto.Rilievo;
            ForeColor = Aspetto.Testo;
            Font = Aspetto.Fisso;
            IntegralHeight = false;
            ItemHeight = 18;
        }

        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }

        protected override void OnDrawItem(DrawItemEventArgs e)
        {
            if (e.Index < 0) return;
            bool scelta = (e.State & DrawItemState.Selected) == DrawItemState.Selected;
            Color fondo = scelta ? Aspetto.Misto(tinta, Aspetto.Rilievo, 0.20f) : Aspetto.Rilievo;
            e.Graphics.FillRectangle(new SolidBrush(fondo), e.Bounds);
            if (scelta)
            {
                using (SolidBrush b = new SolidBrush(tinta))
                {
                    e.Graphics.FillRectangle(b, e.Bounds.X, e.Bounds.Y, 2, e.Bounds.Height);
                }
            }
            Aspetto.Scrivi(e.Graphics, Items[e.Index].ToString(), Font,
                           scelta ? Aspetto.Testo : Aspetto.Medio,
                           new RectangleF(e.Bounds.X + Aspetto.S2, e.Bounds.Y,
                                          e.Bounds.Width - Aspetto.S2, e.Bounds.Height),
                           StringAlignment.Near, StringAlignment.Center, false);
        }
    }

    /// <summary>Una tendina a sola scelta, con le voci disegnate come tutto il
    ///  resto. La casella chiusa resta quella di Windows: il triangolino lo
    ///  disegna il sistema e non si tocca senza riscrivere il controllo da
    ///  capo, che per una tendina non vale la spesa.</summary>
    public class Tendina : ComboBox
    {
        public Tendina()
        {
            DropDownStyle = ComboBoxStyle.DropDownList;
            DrawMode = DrawMode.OwnerDrawFixed;
            FlatStyle = FlatStyle.Flat;
            BackColor = Aspetto.Rilievo;
            ForeColor = Aspetto.Testo;
            Font = Aspetto.Corpo;
            ItemHeight = 18;
        }

        protected override void OnDrawItem(DrawItemEventArgs e)
        {
            if (e.Index < 0) return;
            bool sopra = (e.State & DrawItemState.Selected) == DrawItemState.Selected;
            e.Graphics.FillRectangle(new SolidBrush(sopra ? Aspetto.Misto(Aspetto.Blu, Aspetto.Rilievo, 0.20f)
                                                          : Aspetto.Rilievo), e.Bounds);
            Aspetto.Scrivi(e.Graphics, Items[e.Index].ToString(), Font,
                           sopra ? Aspetto.Testo : Aspetto.Medio,
                           new RectangleF(e.Bounds.X + Aspetto.S2, e.Bounds.Y,
                                          e.Bounds.Width - Aspetto.S2, e.Bounds.Height),
                           StringAlignment.Near, StringAlignment.Center, false);
        }
    }

    // =====================================================================
    //  La griglia
    // =====================================================================

    /// <summary>
    /// Un elenco a colonne, disegnato a mano.
    ///
    /// La ListView di serie porta con se' l'aspetto di Windows: intestazioni in
    /// rilievo, riga selezionata blu di sistema, griglia grigia. In mezzo a
    /// pannelli scuri sembra una finestra di un altro programma incollata
    /// dentro - ed e' quello che era.
    ///
    /// Disegnandola: intestazione piatta in maiuscoletto, riga sotto il cursore
    /// appena piu' chiara, riga scelta con la tinta della pagina. La colonna
    /// degli identificativi va in monospaziato, come tutto quello che ha
    /// scritto la macchina.
    /// </summary>
    public class Griglia : ListView
    {
        private Color tinta = Aspetto.Blu;
        private int sotto = -1;

        public Griglia()
        {
            View = View.Details;
            FullRowSelect = true;
            MultiSelect = false;
            HideSelection = false;
            HeaderStyle = ColumnHeaderStyle.Nonclickable;
            OwnerDraw = true;
            BorderStyle = BorderStyle.None;
            BackColor = Aspetto.Rilievo;
            ForeColor = Aspetto.Testo;
            Font = Aspetto.Corpo;
            DoubleBuffered = true;
        }

        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }

        /// <summary>Le colonne da scrivere in monospaziato, per indice.</summary>
        public bool[] Macchina;

        protected override void OnMouseMove(MouseEventArgs e)
        {
            ListViewItem sopra = GetItemAt(e.X, e.Y);
            int quale = sopra == null ? -1 : sopra.Index;
            if (quale != sotto) { sotto = quale; Invalidate(); }
            base.OnMouseMove(e);
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            if (sotto != -1) { sotto = -1; Invalidate(); }
            base.OnMouseLeave(e);
        }

        protected override void OnDrawColumnHeader(DrawListViewColumnHeaderEventArgs e)
        {
            e.Graphics.FillRectangle(new SolidBrush(Aspetto.Pannello), e.Bounds);
            Aspetto.Etichetta(e.Graphics, e.Header.Text,
                new RectangleF(e.Bounds.X + Aspetto.S2, e.Bounds.Y + 5,
                               e.Bounds.Width, e.Bounds.Height),
                Aspetto.Tenue);
            using (Pen p = new Pen(Aspetto.Bordo))
            {
                e.Graphics.DrawLine(p, e.Bounds.Left, e.Bounds.Bottom - 1,
                                    e.Bounds.Right, e.Bounds.Bottom - 1);
            }
        }

        protected override void OnDrawItem(DrawListViewItemEventArgs e)
        {
            Graphics g = e.Graphics;
            bool scelta = e.Item.Selected;
            Color fondo = Aspetto.Rilievo;
            if (scelta) fondo = Aspetto.Misto(tinta, Aspetto.Rilievo, 0.20f);
            else if (e.ItemIndex == sotto) fondo = Aspetto.Misto(Aspetto.Testo, Aspetto.Rilievo, 0.05f);
            g.FillRectangle(new SolidBrush(fondo), e.Bounds);

            if (scelta)
            {
                using (SolidBrush b = new SolidBrush(tinta))
                {
                    g.FillRectangle(b, e.Bounds.X, e.Bounds.Y, 2, e.Bounds.Height);
                }
            }
            e.DrawDefault = false;
        }

        protected override void OnDrawSubItem(DrawListViewSubItemEventArgs e)
        {
            bool macchina = Macchina != null && e.ColumnIndex < Macchina.Length
                            && Macchina[e.ColumnIndex];
            Font f = macchina ? Aspetto.Fisso : Font;
            Color c = e.Item.ForeColor;
            if (c == Color.Empty || c == SystemColors.WindowText) c = Aspetto.Testo;
            // La prima colonna e' il nome della cosa; le altre sono i suoi
            // dettagli, e stanno un gradino sotto per non rubarle l'occhio.
            if (e.ColumnIndex > 0 && e.Item.ForeColor == Color.Empty) c = Aspetto.Medio;

            RectangleF r = new RectangleF(e.Bounds.X + Aspetto.S2, e.Bounds.Y,
                                          e.Bounds.Width - Aspetto.S2 * 2, e.Bounds.Height);
            Aspetto.Scrivi(e.Graphics, e.SubItem.Text, f, c, r,
                           StringAlignment.Near, StringAlignment.Center, false);
        }
    }
    // =====================================================================
    //  Il testo che si misura da solo
    // =====================================================================

    /// <summary>
    /// Un blocco di testo che va a capo e che si da' l'altezza che gli serve.
    ///
    /// PERCHE' NON UNA Label. La Label di WinForms va a capo, ma l'altezza se
    /// la deve dare chi la costruisce, a mano, contando le righe a occhio. Ed
    /// e' esattamente il conto che sbagliava: una spiegazione di due righe che
    /// a schermo ne diventava quattro finiva sotto la casella di testo,
    /// tagliata nel mezzo. Si vedeva la meta' superiore delle lettere
    /// dell'ultima riga - il difetto piu' brutto che ci sia, perche' non
    /// sembra un errore di misura: sembra un programma rotto.
    ///
    /// Qui l'altezza la decide il testo. Si chiama <see cref="Adatta"/> dopo
    /// aver dato la larghezza, e da quel momento nessuna riga puo' cadere
    /// fuori: se il testo cresce, cresce il controllo.
    ///
    /// AutoSize non e' la stessa cosa: fa crescere anche la LARGHEZZA, e in un
    /// modulo la larghezza e' l'unica misura che deve restare ferma.
    /// </summary>
    public class Testo : Control
    {
        private Color tinta = Aspetto.Medio;
        private bool aCapo = true;

        public Testo()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw
                     | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
            ForeColor = Aspetto.Medio;
            Font = Aspetto.Corpo;
            TabStop = false;
        }

        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }

        /// <summary>Falso: una riga sola, e quello che avanza si taglia con i
        ///  tre puntini invece che di netto.</summary>
        public bool ACapo { get { return aCapo; } set { aCapo = value; Invalidate(); } }

        /// <summary>Quanto sarebbe alto alla larghezza che ha adesso.</summary>
        public int Quanto()
        {
            if (Text.Length == 0) return 0;
            if (!aCapo) return Font.Height + 2;
            return Aspetto.Alto(Text, Font, Width) + 2;
        }

        /// <summary>Si da' l'altezza che gli serve, e torna quanto e' alto:
        ///  cosi' chi impagina sa dove comincia la riga dopo senza doverglielo
        ///  chiedere una seconda volta.</summary>
        public int Adatta()
        {
            Height = Quanto();
            return Height;
        }

        protected override void OnTextChanged(EventArgs e)
        {
            base.OnTextChanged(e);
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Aspetto.Scrivi(e.Graphics, Text, Font, tinta,
                           new RectangleF(0, 0, Width, Height),
                           StringAlignment.Near, StringAlignment.Near, aCapo);
        }
    }

    // =====================================================================
    //  La banda dei tasti, in fondo a una finestrella
    // =====================================================================

    /// <summary>
    /// Il fondo di una finestrella: un piano appena piu' chiaro, e un filo che
    /// lo stacca dal corpo.
    ///
    /// Serve a dire una cosa sola, ma importante: <b>qui sotto ci sono i tasti
    /// che chiudono</b>. Prima erano appoggiati in fondo al corpo come tutto il
    /// resto, e in una finestra piena di caselle "Va bene" era un tasto fra i
    /// tanti. Con la banda si trova senza cercarlo, ed e' sempre nello stesso
    /// posto in tutte le finestrelle del programma.
    /// </summary>
    public class Banda : Control
    {
        public Banda()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            BackColor = Aspetto.Pannello;
            Height = 60;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.Clear(Aspetto.Pannello);
            using (Pen p = new Pen(Aspetto.Bordo))
            {
                e.Graphics.DrawLine(p, 0, 0, Width, 0);
            }
        }
    }

    // =====================================================================
    //  La barra dell'avanzamento
    // =====================================================================

    /// <summary>
    /// Quanto manca, e - soprattutto - che si sta ancora lavorando.
    ///
    /// LA ProgressBar DI SERIE NON VA BENE PER DUE MOTIVI. Il primo e' che e'
    /// verde di sistema in mezzo a una tavolozza scelta una per una. Il secondo
    /// conta di piu': quella indeterminata scorre da sola perche' la muove
    /// Windows, e Windows la muove solo finche' il filo dell'interfaccia
    /// risponde. Nei secondi in cui il programma sta aspettando adb - cioe'
    /// esattamente quando si vorrebbe vedere qualcosa muoversi - resterebbe
    /// ferma, che e' peggio di niente: una barra ferma dice "e' bloccato".
    ///
    /// Questa si muove quando gliela si fa muovere, con <see cref="Batti"/>, e
    /// il lavoro lungo sta in un filo a parte apposta perche' quel battito
    /// arrivi. Vedi l'avvio in GestioneHome.ps1.
    ///
    /// L'ONDA C'E' ANCHE QUANDO LA BARRA E' DETERMINATA: un riflesso che passa
    /// lento sopra la parte gia' fatta. Serve a distinguere "sta lavorando e
    /// non ha ancora finito questo passo" da "e' rimasto li'", che con la sola
    /// lunghezza non si distinguono.
    /// </summary>
    public class Avanzamento : Control
    {
        private float quanto;
        private int onda;
        private Color tinta = Aspetto.Blu;

        public Avanzamento()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw
                     | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
            Height = 6;
            TabStop = false;
        }

        /// <summary>Da 0 a 1. Fuori si accomoda da solo.</summary>
        public float Quanto
        {
            get { return quanto; }
            set
            {
                float v = value < 0f ? 0f : (value > 1f ? 1f : value);
                if (v == quanto) return;
                quanto = v;
                Invalidate();
            }
        }

        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }

        /// <summary>Un battito dell'onda. La chiama un cronometro.</summary>
        public void Batti()
        {
            onda = (onda + 1) % 140;
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            RectangleF tutta = new RectangleF(0, 0, Width, Height);
            float raggio = Height / 2f;

            Aspetto.Riempi(g, tutta, raggio, Aspetto.Misto(tinta, Aspetto.Fondo, 0.14f));
            if (quanto <= 0f) return;

            float fatto = Width * quanto;
            if (fatto < Height) fatto = Height;

            using (GraphicsPath dentro = Aspetto.Arrotonda(
                       new RectangleF(0, 0, fatto, Height), raggio))
            {
                Region prima = g.Clip;
                g.SetClip(dentro);
                using (SolidBrush b = new SolidBrush(tinta))
                {
                    g.FillRectangle(b, 0, 0, fatto, Height);
                }

                // Il riflesso: una lente chiara che passa e ripassa. Sta dentro
                // il taglio, quindi non esce mai dalla parte gia' fatta.
                float x = (onda / 140f) * (fatto + 160f) - 80f;
                RectangleF lente = new RectangleF(x, 0, 80f, Height);
                using (LinearGradientBrush lb = new LinearGradientBrush(
                           lente, Color.White, Color.White, LinearGradientMode.Horizontal))
                {
                    ColorBlend mescola = new ColorBlend(3);
                    mescola.Colors = new Color[] {
                        Aspetto.Velo(Color.White, 0),
                        Aspetto.Velo(Color.White, 70),
                        Aspetto.Velo(Color.White, 0) };
                    mescola.Positions = new float[] { 0f, 0.5f, 1f };
                    lb.InterpolationColors = mescola;
                    g.FillRectangle(lb, lente);
                }
                g.Clip = prima;
            }
        }
    }

    // =====================================================================
    //  Un passo dell'avvio
    // =====================================================================

    /// <summary>
    /// Una riga della schermata d'avvio: il pallino, il nome del passo, e - se
    /// e' fatto - quello che ha trovato.
    ///
    /// PERCHE' UN ELENCO E NON UNA SCRITTA SOLA. "Sto caricando" non dice
    /// niente: non si sa quanto manca, e dopo tre secondi si comincia a
    /// pensare che sia piantato. Quattro righe che si spuntano una alla volta
    /// dicono tre cose insieme - a che punto e', quanto resta, e che il tempo
    /// che ci vuole se lo sta prendendo qualcosa di preciso.
    /// </summary>
    public class PassoAvvio : Control
    {
        /// <summary>0 da fare, 1 in corso, 2 fatto, 3 saltato.</summary>
        private int stato;
        private string dettaglio = "";
        private Color tinta = Aspetto.Blu;

        public PassoAvvio()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint
                     | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw
                     | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
            Font = Aspetto.Corpo;
            Height = 30;
            TabStop = false;
        }

        public int Stato { get { return stato; } set { stato = value; Invalidate(); } }
        public Color Tinta { get { return tinta; } set { tinta = value; Invalidate(); } }

        /// <summary>Quello che il passo ha trovato: "2 luci, 5 routine". Va a
        ///  destra, tenue, e compare solo quando c'e' qualcosa da dire.</summary>
        public string Dettaglio
        {
            get { return dettaglio; }
            set { dettaglio = value == null ? "" : value; Invalidate(); }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;

            float lato = 18f;
            float cy = (Height - lato) / 2f;
            RectangleF pallino = new RectangleF(0, cy, lato, lato);

            if (stato == 2)
            {
                using (SolidBrush b = new SolidBrush(
                           Aspetto.Misto(Aspetto.Verde, Aspetto.Fondo, 0.22f)))
                {
                    g.FillEllipse(b, pallino);
                }
                Icone.Disegna(g, "spunta",
                              new RectangleF(pallino.X + 4, pallino.Y + 4, lato - 8, lato - 8),
                              Aspetto.Verde);
            }
            else if (stato == 1)
            {
                using (SolidBrush b = new SolidBrush(Aspetto.Misto(tinta, Aspetto.Fondo, 0.22f)))
                {
                    g.FillEllipse(b, pallino);
                }
                using (SolidBrush b = new SolidBrush(tinta))
                {
                    g.FillEllipse(b, pallino.X + 6, pallino.Y + 6, lato - 12, lato - 12);
                }
            }
            else if (stato == 3)
            {
                using (Pen p = new Pen(Aspetto.Bordo, 1.4f))
                {
                    g.DrawEllipse(p, pallino.X + 3, pallino.Y + 3, lato - 6, lato - 6);
                }
                Icone.Disegna(g, "meno",
                              new RectangleF(pallino.X + 4, pallino.Y + 4, lato - 8, lato - 8),
                              Aspetto.Spento);
            }
            else
            {
                using (Pen p = new Pen(Aspetto.Bordo, 1.4f))
                {
                    g.DrawEllipse(p, pallino.X + 3, pallino.Y + 3, lato - 6, lato - 6);
                }
            }

            Color colore = stato == 0 || stato == 3 ? Aspetto.Spento
                         : (stato == 1 ? Aspetto.Testo : Aspetto.Medio);
            float x = lato + Aspetto.S3;
            int largoDettaglio = dettaglio.Length == 0 ? 0
                               : Aspetto.Largo(dettaglio, Aspetto.Nota) + Aspetto.S3;

            Aspetto.Scrivi(g, Text, stato == 1 ? Aspetto.Forte : Font, colore,
                           new RectangleF(x, 0, Width - x - largoDettaglio, Height),
                           StringAlignment.Near, StringAlignment.Center, false);

            if (dettaglio.Length > 0)
            {
                Aspetto.Scrivi(g, dettaglio, Aspetto.Nota, Aspetto.Tenue,
                               new RectangleF(Width - largoDettaglio, 0, largoDettaglio, Height),
                               StringAlignment.Far, StringAlignment.Center, false);
            }
        }
    }
}
