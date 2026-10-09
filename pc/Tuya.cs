// Le lampade di casa, viste dal PC: come si trovano e come si prendono le
// chiavi.
//
// COSA C'E' QUI E COSA NON C'E'. Qui non si accende nessuna lampada. Il
// protocollo di comando - la busta 55AA, la stretta di mano 3.4, i dp - sta
// sul tablet, in Tuya.java, ed e' l'unica copia che ne esiste in questo
// progetto: provare una routine vuol dire mandarla al tablet e guardarla
// succedere, non rifarla qui. Sull'altro progetto il PC parlava alle lampade
// perche' era gia' in mezzo a tutto il resto; qui il tablet e' autonomo, e una
// seconda implementazione dello stesso protocollo sarebbe una seconda cosa da
// tenere giusta - per giunta quella che non gira mai in casa.
//
// Restano le due cose che il tablet non puo' fare da solo:
//
//   1. TROVARE. Le lampade Tuya si annunciano in broadcast sulle porte 6666 e
//      6667. Il tablet ascolta gia' quegli annunci, ma solo per riallineare
//      l'indirizzo di quelle che conosce: per SCOPRIRNE di nuove serve un
//      posto dove scrivere quello che si trova, e quel posto e' il PC.
//
//   2. LE CHIAVI. Senza chiave locale una lampada non risponde a nessuno, e
//      l'app Smart Life non la mostra. Si passa da Tuya, entrando con un
//      codice QR: e' la strada che usa Home Assistant dal 2024, e non chiede
//      nessun account da sviluppatore.
//
// Si compila a volo con Add-Type dentro GestioneHome.ps1, quindi vale la
// stessa regola di Audio.cs: il C# che capisce Windows PowerShell 5.1, cioe'
// niente interpolazione di stringhe, niente "out var", niente membri a
// freccia. E la stessa regola sui caratteri: solo ASCII.
//
// Il JSON non si analizza qui. Torna come stringa e lo legge PowerShell con
// ConvertFrom-Json, che c'e' gia' ed e' fatto per quello: in C# vorrebbe dire
// o un analizzatore scritto a mano o System.Web.Extensions, e nessuno dei due
// e' meglio di una riga di PowerShell.

using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;

namespace Casa
{
    // =====================================================================
    //  AES-GCM, perche' .NET Framework non ce l'ha
    // =====================================================================

    /// <summary>
    /// AES in modalita' GCM, scritta a mano.
    ///
    /// Non e' una scelta: la classe AesGcm di .NET esiste solo da .NET Core
    /// 3.0, e Windows PowerShell 5.1 gira sul Framework 4.x. Le alternative
    /// erano chiamare Windows a basso livello (bcrypt.dll, tre struct e sei
    /// P/Invoke) o scrivere le sessanta righe di GHASH. La seconda si prova.
    ///
    /// E infatti si prova: <see cref="Prova"/> ricalcola un vettore di
    /// riferimento del NIST e dice se torna. Chi la usa la chiama una volta
    /// all'avvio - se una cifratura fatta in casa sbaglia, deve dirlo qui e
    /// non tre schermate dopo sotto forma di "il servizio Tuya ha rifiutato la
    /// richiesta".
    /// </summary>
    public static class Gcm
    {
        /// <summary>Cifra e mette la firma di 16 byte in coda al risultato.</summary>
        public static byte[] Cifra(byte[] chiave, byte[] nonce, byte[] chiaro)
        {
            byte[] h = BloccoZero(chiave);
            byte[] j0 = ContatoreIniziale(nonce);

            byte[] cifrato = Contatore(chiave, Incrementa(j0), chiaro);
            byte[] firma = Firma(chiave, h, j0, cifrato);

            byte[] tutto = new byte[cifrato.Length + 16];
            Buffer.BlockCopy(cifrato, 0, tutto, 0, cifrato.Length);
            Buffer.BlockCopy(firma, 0, tutto, cifrato.Length, 16);
            return tutto;
        }

        /// <summary>
        /// Decifra, con la firma negli ultimi 16 byte. Solleva se non torna:
        /// una risposta manomessa non deve arrivare a chi la legge.
        /// </summary>
        public static byte[] Decifra(byte[] chiave, byte[] nonce, byte[] cifratoPiuFirma)
        {
            if (cifratoPiuFirma.Length < 16) throw new CryptographicException("cifrato troppo corto");
            int quanti = cifratoPiuFirma.Length - 16;
            byte[] cifrato = new byte[quanti];
            byte[] firmaAttesa = new byte[16];
            Buffer.BlockCopy(cifratoPiuFirma, 0, cifrato, 0, quanti);
            Buffer.BlockCopy(cifratoPiuFirma, quanti, firmaAttesa, 0, 16);

            byte[] h = BloccoZero(chiave);
            byte[] j0 = ContatoreIniziale(nonce);
            byte[] firma = Firma(chiave, h, j0, cifrato);

            int diverso = 0;
            for (int i = 0; i < 16; i++) diverso |= firma[i] ^ firmaAttesa[i];
            if (diverso != 0) throw new CryptographicException("firma GCM sbagliata");

            return Contatore(chiave, Incrementa(j0), cifrato);
        }

        /// <summary>
        /// Le prove. Torna "" se tutto torna, altrimenti che cosa non e'
        /// venuto.
        ///
        /// La prima e' il <b>caso 3 del NIST</b> per AES-128 GCM: chiave, nonce
        /// e chiaro pubblicati, cifrato e firma da riprodurre carattere per
        /// carattere. Copre AES, il contatore, GHASH e la firma - ma il chiaro
        /// e' di 64 byte, cioe' quattro blocchi tondi.
        ///
        /// La seconda copre <b>il blocco a meta'</b>, che e' il caso normale
        /// qui dentro: i parametri mandati a Tuya sono JSON corti, e la
        /// probabilita' che siano un multiplo di sedici byte e' una su sedici.
        /// Nel NIST un vettore senza dati aggiuntivi e con il chiaro spaiato non
        /// c'e', ma non serve: il contatore e' un flusso, quindi cifrando i
        /// primi 60 byte dello stesso chiaro i primi 60 byte del cifrato devono
        /// venire identici a prima. Se il riempimento a zero dell'ultimo blocco
        /// fosse sbagliato, si vedrebbe li'.
        ///
        /// La terza e' il giro completo: si decifra e si deve riavere il
        /// chiaro, firma compresa - cioe' anche la parte che nella seconda non
        /// si puo' confrontare con nessuno.
        /// </summary>
        public static string Prova()
        {
            byte[] k = Byte("feffe9928665731c6d6a8f9467308308");
            byte[] iv = Byte("cafebabefacedbaddecaf888");
            byte[] p = Byte("d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72"
                          + "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255");
            string atteso = "42831ec2217774244b7221b784d0d49ce3aa212f2c02a4e035c17e2329aca12e"
                          + "21d514b25466931c7d8f6a5aac84aa051ba30b396a0aac973d58e091473f5985"
                          + "4d5c2af327cd64a62cf35abd2ba6fab4";
            if (Esa(Cifra(k, iv, p)) != atteso) return "GCM: il vettore del NIST non torna";

            byte[] spaiato = new byte[60];
            Buffer.BlockCopy(p, 0, spaiato, 0, 60);
            string corto = Esa(Cifra(k, iv, spaiato));
            if (corto.Substring(0, 120) != atteso.Substring(0, 120))
            {
                return "GCM: l'ultimo blocco a meta' esce sbagliato";
            }

            byte[] tornato = Decifra(k, iv, Byte(corto));
            if (Esa(tornato) != Esa(spaiato)) return "GCM: decifrando non si torna al chiaro";
            return "";
        }

        // ---- i pezzi -------------------------------------------------------

        private static byte[] BloccoZero(byte[] chiave)
        {
            return Aes(chiave, new byte[16]);
        }

        private static byte[] ContatoreIniziale(byte[] nonce)
        {
            // Con un nonce di 12 byte - l'unico caso che serve qui - il primo
            // contatore e' il nonce piu' un uno a 32 bit. Con misure diverse
            // andrebbe passato per GHASH, e non e' scritto apposta: sarebbe
            // codice che non gira mai e che nessuno vedrebbe sbagliare.
            if (nonce.Length != 12) throw new CryptographicException("nonce non di 12 byte");
            byte[] j0 = new byte[16];
            Buffer.BlockCopy(nonce, 0, j0, 0, 12);
            j0[15] = 1;
            return j0;
        }

        private static byte[] Incrementa(byte[] blocco)
        {
            byte[] fuori = (byte[])blocco.Clone();
            for (int i = 15; i >= 12; i--)
            {
                fuori[i]++;
                if (fuori[i] != 0) break;
            }
            return fuori;
        }

        private static byte[] Contatore(byte[] chiave, byte[] partenza, byte[] dati)
        {
            byte[] fuori = new byte[dati.Length];
            byte[] ctr = (byte[])partenza.Clone();
            for (int p = 0; p < dati.Length; p += 16)
            {
                byte[] maschera = Aes(chiave, ctr);
                int quanti = Math.Min(16, dati.Length - p);
                for (int i = 0; i < quanti; i++) fuori[p + i] = (byte)(dati[p + i] ^ maschera[i]);
                ctr = Incrementa(ctr);
            }
            return fuori;
        }

        private static byte[] Firma(byte[] chiave, byte[] h, byte[] j0, byte[] cifrato)
        {
            byte[] s = new byte[16];
            for (int p = 0; p < cifrato.Length; p += 16)
            {
                byte[] blocco = new byte[16];
                Buffer.BlockCopy(cifrato, p, blocco, 0, Math.Min(16, cifrato.Length - p));
                for (int i = 0; i < 16; i++) s[i] ^= blocco[i];
                s = Moltiplica(s, h);
            }
            // Le due lunghezze in bit, a 64 bit ciascuna: dati aggiuntivi (qui
            // nessuno) e cifrato.
            byte[] code = new byte[16];
            long bit = (long)cifrato.Length * 8L;
            for (int i = 0; i < 8; i++) code[15 - i] = (byte)(bit >> (8 * i));
            for (int i = 0; i < 16; i++) s[i] ^= code[i];
            s = Moltiplica(s, h);

            return Contatore(chiave, j0, s);
        }

        /// <summary>Prodotto in GF(2^128), con l'ordine dei bit del GCM.</summary>
        private static byte[] Moltiplica(byte[] x, byte[] y)
        {
            byte[] z = new byte[16];
            byte[] v = (byte[])y.Clone();
            for (int i = 0; i < 128; i++)
            {
                if ((x[i >> 3] & (0x80 >> (i & 7))) != 0)
                {
                    for (int j = 0; j < 16; j++) z[j] ^= v[j];
                }
                bool ultimo = (v[15] & 1) != 0;
                for (int j = 15; j > 0; j--) v[j] = (byte)((v[j] >> 1) | ((v[j - 1] & 1) << 7));
                v[0] = (byte)(v[0] >> 1);
                if (ultimo) v[0] ^= 0xE1;
            }
            return z;
        }

        private static byte[] Aes(byte[] chiave, byte[] blocco)
        {
            using (AesManaged a = new AesManaged())
            {
                a.Key = chiave;
                a.Mode = CipherMode.ECB;
                a.Padding = PaddingMode.None;
                using (ICryptoTransform t = a.CreateEncryptor())
                {
                    return t.TransformFinalBlock(blocco, 0, blocco.Length);
                }
            }
        }

        internal static string Esa(byte[] b)
        {
            StringBuilder s = new StringBuilder(b.Length * 2);
            foreach (byte x in b) s.Append(x.ToString("x2", CultureInfo.InvariantCulture));
            return s.ToString();
        }

        internal static byte[] Byte(string esa)
        {
            byte[] b = new byte[esa.Length / 2];
            for (int i = 0; i < b.Length; i++)
            {
                b[i] = byte.Parse(esa.Substring(i * 2, 2), NumberStyles.HexNumber,
                                  CultureInfo.InvariantCulture);
            }
            return b;
        }
    }

    // =====================================================================
    //  Gli annunci in broadcast
    // =====================================================================

    /// <summary>
    /// Chi c'e' in casa, ascoltando invece di chiedere.
    ///
    /// Le lampade Tuya si presentano da sole ogni pochi secondi, in broadcast
    /// sulle porte 6666 (le vecchie) e 6667 (dalla 3.3 in poi). Nel pacchetto
    /// c'e' l'identificativo, l'indirizzo e la versione del protocollo: tutto
    /// quello che serve per una riga di configurazione, tranne la chiave.
    ///
    /// <b>Non e' una scansione della rete.</b> Non si bussa a duecentocinque
    /// indirizzi sperando che qualcuno risponda: si sta zitti e si sente chi
    /// parla. Costa niente, non sveglia nessuno - e le lampade Tuya la radio in
    /// ricezione la spengono apposta per risparmiare - e trova anche quelle di
    /// cui non si sapeva niente.
    ///
    /// Il prezzo e' l'attesa: bisogna restare in ascolto qualche secondo, e una
    /// lampada che ha appena parlato non riparla subito.
    /// </summary>
    public static class Annunci
    {
        /// <summary>
        /// La chiave con cui sono cifrati gli annunci: e' la stessa per tutte
        /// le lampade del mondo, quindi non protegge niente. Serve solo a
        /// togliere di mezzo chi non sa che formato sia.
        /// </summary>
        private const string ChiaveAnnunci = "yGAdlopoPVldABfn";

        private const uint Prefisso = 0x000055AA;

        /// <summary>
        /// Ascolta per tot secondi e torna un JSON per apparecchio, senza
        /// ripetizioni. Le porte si ascoltano tutte e due insieme, ognuna sul
        /// suo socket: una lampada che parla solo sulla 6666 non deve aspettare
        /// che sia finito il turno della 6667.
        /// </summary>
        public static string[] Ascolta(int secondi)
        {
            List<string> trovati = new List<string>();
            HashSet<string> visti = new HashSet<string>();
            List<UdpClient> prese = new List<UdpClient>();

            foreach (int porta in new int[] { 6666, 6667 })
            {
                try
                {
                    UdpClient u = new UdpClient();
                    u.ExclusiveAddressUse = false;
                    u.Client.SetSocketOption(SocketOptionLevel.Socket,
                                             SocketOptionName.ReuseAddress, true);
                    u.Client.Bind(new IPEndPoint(IPAddress.Any, porta));
                    u.Client.ReceiveTimeout = 400;
                    prese.Add(u);
                }
                catch (Exception)
                {
                    // Porta gia' occupata da qualcun altro: si va avanti con
                    // l'altra invece di non trovare niente.
                }
            }
            if (prese.Count == 0) return new string[0];

            DateTime fine = DateTime.UtcNow.AddSeconds(Math.Max(1, secondi));
            try
            {
                while (DateTime.UtcNow < fine)
                {
                    foreach (UdpClient u in prese)
                    {
                        IPEndPoint chi = new IPEndPoint(IPAddress.Any, 0);
                        byte[] dati;
                        try { dati = u.Receive(ref chi); }
                        catch (SocketException) { continue; }   // nessuno ha parlato
                        catch (Exception) { continue; }

                        string testo = InChiaro(dati);
                        if (testo == null) continue;
                        if (visti.Contains(testo)) continue;
                        visti.Add(testo);
                        trovati.Add(testo);
                    }
                }
            }
            finally
            {
                foreach (UdpClient u in prese) { try { u.Close(); } catch (Exception) { } }
            }
            return trovati.ToArray();
        }

        /// <summary>
        /// Il JSON dentro un annuncio, o null se il pacchetto non e' di una
        /// lampada Tuya. Sulle stesse porte parlano anche telefoni e altre
        /// marche: quello che non si capisce non e' un errore, e' rumore.
        ///
        /// E' la stessa funzione di Tuya.annuncioInChiaro sul tablet, e i due
        /// devono restare d'accordo: se un giorno una lampada nuova parlasse un
        /// formato diverso, il PC la troverebbe e il tablet no.
        /// </summary>
        public static string InChiaro(byte[] dati)
        {
            try
            {
                byte[] chiave;
                using (MD5 md5 = MD5.Create())
                {
                    chiave = md5.ComputeHash(Encoding.UTF8.GetBytes(ChiaveAnnunci));
                }

                int da = 0, lunghezza = dati.Length;

                // Gli annunci viaggiano nella stessa busta dei comandi. Se c'e'
                // si salta l'intestazione piu' i quattro byte di esito, e si
                // taglia la firma in coda.
                if (dati.Length > 24 && Intero(dati, 0) == Prefisso)
                {
                    int dichiarata = (int)Intero(dati, 12);
                    int fine = Math.Min(dati.Length, 16 + dichiarata) - 8;
                    da = 20;
                    lunghezza = fine - da;
                    if (lunghezza <= 0) return null;
                }

                // Qualcuna manda l'annuncio in chiaro: se e' gia' JSON non c'e'
                // niente da decifrare.
                if (dati[da] == (byte)'{') return Encoding.UTF8.GetString(dati, da, lunghezza);

                byte[] cifrato = new byte[lunghezza];
                Buffer.BlockCopy(dati, da, cifrato, 0, lunghezza);

                using (AesManaged a = new AesManaged())
                {
                    a.Key = chiave;
                    a.Mode = CipherMode.ECB;
                    a.Padding = PaddingMode.PKCS7;
                    using (ICryptoTransform t = a.CreateDecryptor())
                    {
                        byte[] chiaro = t.TransformFinalBlock(cifrato, 0, cifrato.Length);
                        return Encoding.UTF8.GetString(chiaro);
                    }
                }
            }
            catch (Exception)
            {
                return null;
            }
        }

        private static uint Intero(byte[] b, int da)
        {
            return ((uint)b[da] << 24) | ((uint)b[da + 1] << 16)
                 | ((uint)b[da + 2] << 8) | b[da + 3];
        }
    }

    // =====================================================================
    //  Le chiavi locali, entrando con un QR
    // =====================================================================

    /// <summary>Quel che serve sapere fra un passo e l'altro dell'accesso.</summary>
    public class Sessione
    {
        public string Codice = "";          // il testo dietro il QR
        public string AccessToken = "";
        public string RefreshToken = "";
        public string Portale = "";
        public string Utente = "";
    }

    /// <summary>
    /// Prende le chiavi locali delle lampade entrando con un codice QR, senza
    /// nessun account da sviluppatore.
    ///
    /// E' la strada che usa Home Assistant dal 2024: si mostra un codice, lo si
    /// inquadra con l'app Smart Life, si conferma, e Tuya consegna l'elenco dei
    /// dispositivi con dentro la chiave di ognuno. Al posto di Access ID e
    /// Access Secret - progetto cloud, data center giusto da indovinare, prova
    /// che scade dopo un mese - serve solo il <b>codice utente</b>, che sta
    /// scritto nell'app.
    ///
    /// Ci si presenta con l'identificativo pubblico dell'integrazione di Home
    /// Assistant: e' quello che rende superfluo l'account da sviluppatore. Non
    /// e' un'interfaccia documentata da Tuya, quindi un giorno potrebbe
    /// smettere di funzionare - ma le chiavi servono una volta sola, e quelle
    /// gia' prese restano buone per sempre.
    ///
    /// Serve internet solo per questi quattro passi. Dopo, le lampade si
    /// comandano in casa e senza cloud.
    /// </summary>
    public static class Chiavi
    {
        private const string Identificativo = "HA_3y9q4ak7g4ephrvke";
        private const string Schema = "haauthorize";
        private const string Portale = "https://apigw.iotbing.com";

        static Chiavi()
        {
            // Senza questa riga il Framework negozia ancora TLS 1.0 e la
            // chiamata muore con "connessione chiusa", che non dice niente a
            // nessuno.
            ServicePointManager.SecurityProtocol =
                (SecurityProtocolType)3072 | SecurityProtocolType.Tls;
        }

        /// <summary>Il testo da mettere nel QR: l'app riconosce questo indirizzo.</summary>
        public static string TestoQr(string codice)
        {
            return "tuyaSmart--qrLogin?token=" + codice;
        }

        /// <summary>
        /// Chiede a Tuya un codice da mostrare. Scade in pochi minuti. Torna ""
        /// se e' andata, altrimenti il motivo.
        /// </summary>
        public static string ChiediCodice(string codiceUtente, Sessione s)
        {
            try
            {
                string url = Portale + "/v1.0/m/life/home-assistant/qrcode/tokens"
                           + "?clientid=" + Identificativo
                           + "&usercode=" + Uri.EscapeDataString(codiceUtente)
                           + "&schema=" + Schema;
                string risposta = Chiama("POST", url, null);
                if (!Riuscito(risposta)) return Perche(risposta);
                s.Codice = Campo(risposta, "qrcode");
                return s.Codice.Length > 0 ? "" : "Tuya non ha mandato nessun codice";
            }
            catch (Exception e)
            {
                return e.Message;
            }
        }

        /// <summary>
        /// Guarda se il codice e' stato inquadrato e confermato.
        ///
        /// Finche' non lo e' risponde "non ancora" senza che sia un errore: e'
        /// cosi' che si aspetta, e distinguere le due cose e' quello che
        /// permette a chi guarda la finestra di sapere se deve inquadrare o se
        /// deve rifare il codice.
        /// </summary>
        public static string Esito(Sessione s, string codiceUtente, out bool entrato)
        {
            entrato = false;
            try
            {
                string url = Portale + "/v1.0/m/life/home-assistant/qrcode/tokens/" + s.Codice
                           + "?clientid=" + Identificativo
                           + "&usercode=" + Uri.EscapeDataString(codiceUtente);
                string risposta = Chiama("GET", url, null);
                if (!Riuscito(risposta)) return "";      // non ancora: si riprova

                s.AccessToken = Campo(risposta, "access_token");
                s.RefreshToken = Campo(risposta, "refresh_token");
                s.Portale = Campo(risposta, "endpoint").TrimEnd('/');
                s.Utente = Campo(risposta, "username");
                entrato = s.AccessToken.Length > 0;
                return "";
            }
            catch (Exception e)
            {
                return e.Message;
            }
        }

        /// <summary>
        /// Una chiamata firmata e cifrata, e il JSON in chiaro che ne torna.
        ///
        /// La chiave di cifratura nasce dal numero casuale della richiesta e dal
        /// token di rinnovo, quindi cambia ogni volta: una richiesta registrata
        /// non si puo' rimandare dopo.
        ///
        /// I parametri arrivano gia' scritti come JSON da PowerShell, che sa
        /// farlo meglio di una concatenazione di stringhe fatta qui.
        /// </summary>
        public static string Chiamata(Sessione s, string percorso, string parametriJson,
                                      out string risultato)
        {
            risultato = "";
            try
            {
                string rid = Guid.NewGuid().ToString();
                string hashKey;
                using (MD5 md5 = MD5.Create())
                {
                    hashKey = Gcm.Esa(md5.ComputeHash(
                        Encoding.UTF8.GetBytes(rid + s.RefreshToken)));
                }
                string segreto;
                using (HMACSHA256 h = new HMACSHA256(Encoding.UTF8.GetBytes(hashKey)))
                {
                    segreto = Gcm.Esa(h.ComputeHash(Encoding.UTF8.GetBytes(rid))).Substring(0, 16);
                }

                string encdata = "";
                string url = (s.Portale.Length > 0 ? s.Portale : Portale) + percorso;
                if (!string.IsNullOrEmpty(parametriJson))
                {
                    encdata = Cifra(parametriJson, segreto);
                    url += "?encdata=" + Uri.EscapeDataString(encdata);
                }

                long adesso = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1)).TotalMilliseconds;

                // La firma copre le intestazioni non vuote, in quest'ordine,
                // piu' il contenuto cifrato. X-sid qui e' sempre vuoto e quindi
                // non entra.
                string daFirmare = "X-appKey=" + Identificativo + "||X-requestId=" + rid
                                 + "||X-time=" + adesso.ToString(CultureInfo.InvariantCulture)
                                 + "||X-token=" + s.AccessToken + encdata;
                string firma;
                using (HMACSHA256 h = new HMACSHA256(Encoding.UTF8.GetBytes(hashKey)))
                {
                    firma = Gcm.Esa(h.ComputeHash(Encoding.UTF8.GetBytes(daFirmare)));
                }

                Dictionary<string, string> intestazioni = new Dictionary<string, string>();
                intestazioni["X-appKey"] = Identificativo;
                intestazioni["X-requestId"] = rid;
                intestazioni["X-time"] = adesso.ToString(CultureInfo.InvariantCulture);
                intestazioni["X-token"] = s.AccessToken;
                intestazioni["X-sign"] = firma;

                string risposta = Chiama("GET", url, intestazioni);
                if (!Riuscito(risposta)) return Perche(risposta);

                string cifrato = Campo(risposta, "result");
                if (cifrato.Length == 0) return "risposta senza risultato";
                risultato = Decifra(cifrato, segreto);
                return "";
            }
            catch (Exception e)
            {
                return e.Message;
            }
        }

        // ---- cifratura ----

        /// <summary>
        /// L'alfabeto del numero usa-e-getta: niente lettere che si confondono
        /// fra loro, perche' viaggia come testo e non come byte.
        /// </summary>
        private const string AlfabetoNonce =
            "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";

        private static string Cifra(string chiaro, string segreto)
        {
            byte[] nonce = new byte[12];
            byte[] sorte = new byte[12];
            using (RNGCryptoServiceProvider r = new RNGCryptoServiceProvider())
            {
                r.GetBytes(sorte);
            }
            for (int i = 0; i < 12; i++)
            {
                nonce[i] = (byte)AlfabetoNonce[sorte[i] % AlfabetoNonce.Length];
            }

            byte[] fuori = Gcm.Cifra(Encoding.UTF8.GetBytes(segreto), nonce,
                                     Encoding.UTF8.GetBytes(chiaro));

            // Due base64 attaccati, non uno solo: il primo e' sempre di 16
            // caratteri, ed e' cosi' che l'altro capo ritrova il confine.
            return Convert.ToBase64String(nonce) + Convert.ToBase64String(fuori);
        }

        private static string Decifra(string base64, string segreto)
        {
            byte[] tutto = Convert.FromBase64String(base64);
            byte[] nonce = new byte[12];
            byte[] resto = new byte[tutto.Length - 12];
            Buffer.BlockCopy(tutto, 0, nonce, 0, 12);
            Buffer.BlockCopy(tutto, 12, resto, 0, resto.Length);
            return Encoding.UTF8.GetString(
                Gcm.Decifra(Encoding.UTF8.GetBytes(segreto), nonce, resto));
        }

        // ---- la rete, in basso ----

        private static string Chiama(string metodo, string url,
                                     Dictionary<string, string> intestazioni)
        {
            HttpWebRequest r = (HttpWebRequest)WebRequest.Create(url);
            r.Method = metodo;
            r.Timeout = 30000;
            r.UserAgent = "GestioneHome";
            if (intestazioni != null)
            {
                foreach (KeyValuePair<string, string> i in intestazioni) r.Headers.Add(i.Key, i.Value);
            }
            if (metodo == "POST") r.ContentLength = 0;

            try
            {
                using (WebResponse w = r.GetResponse())
                using (StreamReader s = new StreamReader(w.GetResponseStream()))
                {
                    return s.ReadToEnd();
                }
            }
            catch (WebException e)
            {
                // Tuya risponde con un JSON anche quando dice di no, e quel
                // JSON e' l'unica cosa che spiega il motivo: buttarlo per
                // tenere "(400) Richiesta non valida" vorrebbe dire mostrare a
                // schermo il codice invece della causa.
                if (e.Response != null)
                {
                    using (StreamReader s = new StreamReader(e.Response.GetResponseStream()))
                    {
                        return s.ReadToEnd();
                    }
                }
                throw;
            }
        }

        // ---- il minimo di JSON che serve qui -------------------------------
        //
        // Non e' un analizzatore: sono tre funzioni che pescano un campo di
        // primo livello da una risposta di Tuya, che ha sempre la stessa forma.
        // Il JSON vero lo legge PowerShell, dove ConvertFrom-Json c'e' gia'.

        private static bool Riuscito(string risposta)
        {
            return risposta != null && risposta.Contains("\"success\":true");
        }

        private static string Perche(string risposta)
        {
            string msg = Campo(risposta, "msg");
            if (msg.Length > 0) return msg;
            return "il servizio Tuya ha rifiutato la richiesta";
        }

        /// <summary>Il valore testuale di "nome", ovunque stia. Vuoto se non c'e'.</summary>
        private static string Campo(string json, string nome)
        {
            if (string.IsNullOrEmpty(json)) return "";
            string cerca = "\"" + nome + "\"";
            int i = json.IndexOf(cerca, StringComparison.Ordinal);
            if (i < 0) return "";
            i = json.IndexOf(':', i + cerca.Length);
            if (i < 0) return "";
            i++;
            while (i < json.Length && char.IsWhiteSpace(json[i])) i++;
            if (i >= json.Length || json[i] != '"') return "";
            i++;
            StringBuilder b = new StringBuilder();
            while (i < json.Length && json[i] != '"')
            {
                if (json[i] == '\\' && i + 1 < json.Length)
                {
                    i++;
                    if (json[i] == 'n') b.Append('\n');
                    else if (json[i] == 't') b.Append('\t');
                    else if (json[i] == 'u' && i + 4 < json.Length)
                    {
                        b.Append((char)int.Parse(json.Substring(i + 1, 4),
                                 NumberStyles.HexNumber, CultureInfo.InvariantCulture));
                        i += 4;
                    }
                    else b.Append(json[i]);
                }
                else b.Append(json[i]);
                i++;
            }
            return b.ToString();
        }
    }

    // =====================================================================
    //  Il codice QR
    // =====================================================================

    /// <summary>
    /// Un codice QR, generato a mano.
    ///
    /// E' la stessa cosa che c'e' sul tablet in Qr.java - li' serve per entrare
    /// in Spotify, qui per entrare in Tuya - e viaggia con lo stesso motivo per
    /// cui c'e' li': le librerie che lo fanno sono qualche centinaio di
    /// kilobyte e una dipendenza da tenere aggiornata, l'algoritmo invece e' un
    /// algoritmo, e una volta scritto sta fermo per sempre.
    ///
    /// Modalita' byte, correzione L, versioni da 1 a 9 - fino a 230 caratteri.
    /// Il testo di Tuya ne ha un'ottantina.
    /// </summary>
    public sealed class Qr
    {
        /// <summary>Byte di dati per versione, con correzione L.</summary>
        private static readonly int[] Dati = { 19, 34, 55, 80, 108, 136, 156, 194, 232 };

        /// <summary>Byte di correzione per blocco, e quanti blocchi. Da 6 a 9 i
        ///  blocchi sono due e uguali: e' il motivo per cui ci si ferma a 9 e
        ///  non a 10, dove diventano quattro di due misure diverse.</summary>
        private static readonly int[] Correzione = { 7, 10, 15, 20, 26, 18, 20, 24, 30 };
        private static readonly int[] Blocchi = { 1, 1, 1, 1, 1, 2, 2, 2, 2 };

        private static readonly int[][] Allineamenti = new int[][] {
            new int[] { },
            new int[] { 6, 18 },
            new int[] { 6, 22 },
            new int[] { 6, 26 },
            new int[] { 6, 30 },
            new int[] { 6, 34 },
            new int[] { 6, 22, 38 },
            new int[] { 6, 24, 42 },
            new int[] { 6, 26, 46 },
        };

        private static readonly int[] Exp = new int[512];
        private static readonly int[] Log = new int[256];

        static Qr()
        {
            int x = 1;
            for (int i = 0; i < 255; i++)
            {
                Exp[i] = x;
                Log[x] = i;
                x <<= 1;
                if ((x & 0x100) != 0) x ^= 0x11D;
            }
            for (int i = 255; i < 512; i++) Exp[i] = Exp[i - 255];
        }

        private readonly int lato;
        private readonly bool[,] moduli;
        private readonly bool[,] fisso;

        /// <summary>La matrice: nero e' true. Null se il testo non ci sta.</summary>
        public static bool[,] Per(string testo)
        {
            if (string.IsNullOrEmpty(testo)) return null;
            try
            {
                byte[] byteTesto = Encoding.UTF8.GetBytes(testo);
                int versione = -1;
                for (int v = 1; v <= 9; v++)
                {
                    // 4 bit di modalita' + 8 di lunghezza = un byte e mezzo.
                    if (byteTesto.Length + 2 <= Dati[v - 1]) { versione = v; break; }
                }
                if (versione < 0) return null;

                Qr q = new Qr(17 + 4 * versione);
                byte[] parole = q.Componi(byteTesto, versione);
                q.DisegnaFisso(versione);
                q.Posa(parole);
                int maschera = q.ScegliMaschera();
                q.ApplicaMaschera(maschera);
                q.ScriviFormato(maschera);
                return q.moduli;
            }
            catch (Exception)
            {
                return null;
            }
        }

        private Qr(int lato)
        {
            this.lato = lato;
            moduli = new bool[lato, lato];
            fisso = new bool[lato, lato];
        }

        // ---- i bit ---------------------------------------------------------

        /// <summary>
        /// Dal testo alle parole di codice, correzione compresa.
        ///
        /// L'interlacciamento in fondo sembra arbitrario e non lo e': i byte dei
        /// due blocchi si alternano proprio perche' una macchia di sporco sul
        /// codice colpisca un po' dell'uno e un po' dell'altro, invece di
        /// distruggerne uno intero - che e' l'unico caso che la correzione non
        /// saprebbe recuperare.
        /// </summary>
        private byte[] Componi(byte[] dati, int versione)
        {
            int capienza = Dati[versione - 1];
            byte[] flusso = new byte[capienza];
            int bit = 0;

            bit = ScriviBit(flusso, bit, 0x4, 4);                 // modalita' byte
            bit = ScriviBit(flusso, bit, dati.Length, 8);         // quanti byte
            foreach (byte b in dati) bit = ScriviBit(flusso, bit, b & 0xFF, 8);

            // Terminatore: fino a quattro zeri, e non di piu' se lo spazio finisce.
            int restano = capienza * 8 - bit;
            bit = ScriviBit(flusso, bit, 0, Math.Min(4, restano));
            if (bit % 8 != 0) bit = ScriviBit(flusso, bit, 0, 8 - bit % 8);
            bool primo = true;
            while (bit < capienza * 8)
            {
                bit = ScriviBit(flusso, bit, primo ? 0xEC : 0x11, 8);
                primo = !primo;
            }

            int quantiBlocchi = Blocchi[versione - 1];
            int correzione = Correzione[versione - 1];
            int perBlocco = capienza / quantiBlocchi;

            byte[][] blocchi = new byte[quantiBlocchi][];
            byte[][] code = new byte[quantiBlocchi][];
            for (int b = 0; b < quantiBlocchi; b++)
            {
                blocchi[b] = new byte[perBlocco];
                Buffer.BlockCopy(flusso, b * perBlocco, blocchi[b], 0, perBlocco);
                code[b] = ReedSolomon(blocchi[b], correzione);
            }

            byte[] fuori = new byte[capienza + correzione * quantiBlocchi];
            int k = 0;
            for (int i = 0; i < perBlocco; i++)
                for (int b = 0; b < quantiBlocchi; b++) fuori[k++] = blocchi[b][i];
            for (int i = 0; i < correzione; i++)
                for (int b = 0; b < quantiBlocchi; b++) fuori[k++] = code[b][i];
            return fuori;
        }

        private static int ScriviBit(byte[] dove, int posizione, int valore, int quanti)
        {
            for (int i = quanti - 1; i >= 0; i--)
            {
                if (((valore >> i) & 1) != 0)
                {
                    dove[posizione >> 3] |= (byte)(0x80 >> (posizione & 7));
                }
                posizione++;
            }
            return posizione;
        }

        private static int MoltiplicaGf(int a, int b)
        {
            if (a == 0 || b == 0) return 0;
            return Exp[Log[a] + Log[b]];
        }

        private static byte[] ReedSolomon(byte[] dati, int quanti)
        {
            // Il polinomio generatore: (x - a^0)(x - a^1)...(x - a^(quanti-1)).
            int[] generatore = new int[quanti + 1];
            generatore[0] = 1;
            for (int i = 0; i < quanti; i++)
            {
                for (int j = i + 1; j > 0; j--)
                {
                    generatore[j] = generatore[j - 1] ^ MoltiplicaGf(generatore[j], Exp[i]);
                }
                generatore[0] = MoltiplicaGf(generatore[0], Exp[i]);
            }

            int[] resto = new int[quanti];
            foreach (byte b in dati)
            {
                int fattore = (b & 0xFF) ^ resto[0];
                Array.Copy(resto, 1, resto, 0, quanti - 1);
                resto[quanti - 1] = 0;
                for (int i = 0; i < quanti; i++)
                {
                    resto[i] ^= MoltiplicaGf(generatore[quanti - 1 - i], fattore);
                }
            }
            byte[] fuori = new byte[quanti];
            for (int i = 0; i < quanti; i++) fuori[i] = (byte)resto[i];
            return fuori;
        }

        // ---- il disegno ----------------------------------------------------

        private void Metti(int x, int y, bool acceso)
        {
            if (x < 0 || y < 0 || x >= lato || y >= lato) return;
            moduli[y, x] = acceso;
            fisso[y, x] = true;
        }

        private void DisegnaFisso(int versione)
        {
            // I tempi: la riga e la colonna a scacchi che dicono al lettore
            // quanto e' grande un modulo.
            for (int i = 0; i < lato; i++)
            {
                Metti(6, i, i % 2 == 0);
                Metti(i, 6, i % 2 == 0);
            }

            Riferimento(3, 3);
            Riferimento(lato - 4, 3);
            Riferimento(3, lato - 4);

            int[] centri = Allineamenti[versione - 1];
            for (int i = 0; i < centri.Length; i++)
            {
                for (int j = 0; j < centri.Length; j++)
                {
                    // I tre angoli sono gia' occupati dai quadrati di riferimento.
                    bool angolo = (i == 0 && j == 0)
                               || (i == 0 && j == centri.Length - 1)
                               || (i == centri.Length - 1 && j == 0);
                    if (!angolo) Allineamento(centri[i], centri[j]);
                }
            }

            // Si prenota lo spazio del formato con un valore qualunque: conta
            // che quelle caselle risultino occupate PRIMA che i dati comincino a
            // cercare posto. Senza questa riga i bit dei dati ci finirebbero
            // dentro, verrebbero coperti dal formato vero alla fine, e tutto
            // quello che viene dopo sarebbe sfasato di qualche posizione: il
            // codice si disegnerebbe benissimo e nessun lettore lo leggerebbe.
            ScriviFormato(0);
            if (versione >= 7) ScriviVersione(versione);
        }

        /// <summary>Il quadrato grande d'angolo: sette per sette con la cornice bianca.</summary>
        private void Riferimento(int cx, int cy)
        {
            for (int dy = -4; dy <= 4; dy++)
            {
                for (int dx = -4; dx <= 4; dx++)
                {
                    int distanza = Math.Max(Math.Abs(dx), Math.Abs(dy));
                    Metti(cx + dx, cy + dy, distanza != 2 && distanza != 4);
                }
            }
        }

        /// <summary>Il quadratino di allineamento: cinque per cinque.</summary>
        private void Allineamento(int cx, int cy)
        {
            for (int dy = -2; dy <= 2; dy++)
            {
                for (int dx = -2; dx <= 2; dx++)
                {
                    Metti(cx + dx, cy + dy, Math.Max(Math.Abs(dx), Math.Abs(dy)) != 1);
                }
            }
        }

        /// <summary>I quindici bit che dicono correzione e maschera, in due
        ///  copie: se un angolo si rovina, il lettore usa l'altra.</summary>
        private void ScriviFormato(int maschera)
        {
            int dati = (1 << 3) | maschera;          // 01 = correzione L
            int resto = dati;
            for (int i = 0; i < 10; i++) resto = (resto << 1) ^ ((resto >> 9) * 0x537);
            int bit = ((dati << 10) | resto) ^ 0x5412;

            for (int i = 0; i <= 5; i++) Metti(8, i, BitDi(bit, i));
            Metti(8, 7, BitDi(bit, 6));
            Metti(8, 8, BitDi(bit, 7));
            Metti(7, 8, BitDi(bit, 8));
            for (int i = 9; i < 15; i++) Metti(14 - i, 8, BitDi(bit, i));

            for (int i = 0; i < 8; i++) Metti(lato - 1 - i, 8, BitDi(bit, i));
            for (int i = 8; i < 15; i++) Metti(8, lato - 15 + i, BitDi(bit, i));
            Metti(8, lato - 8, true);                // il modulo sempre nero
        }

        /// <summary>Dalla versione 7 in su il codice dichiara quanto e' grande.</summary>
        private void ScriviVersione(int versione)
        {
            int resto = versione;
            for (int i = 0; i < 12; i++) resto = (resto << 1) ^ ((resto >> 11) * 0x1F25);
            int bit = (versione << 12) | resto;
            for (int i = 0; i < 18; i++)
            {
                bool acceso = BitDi(bit, i);
                int a = lato - 11 + i % 3, b = i / 3;
                Metti(a, b, acceso);
                Metti(b, a, acceso);
            }
        }

        private static bool BitDi(int valore, int posizione)
        {
            return ((valore >> posizione) & 1) != 0;
        }

        /// <summary>
        /// I dati, a zig-zag da in basso a destra.
        ///
        /// Due colonne per volta, su e giu' alternando, saltando la colonna sei
        /// - che e' quella dei tempi - e tutte le caselle gia' occupate.
        /// </summary>
        private void Posa(byte[] parole)
        {
            int i = 0;
            for (int destra = lato - 1; destra >= 1; destra -= 2)
            {
                if (destra == 6) destra = 5;
                for (int passo = 0; passo < lato; passo++)
                {
                    for (int j = 0; j < 2; j++)
                    {
                        int x = destra - j;
                        bool versoAlto = ((destra + 1) & 2) == 0;
                        int y = versoAlto ? lato - 1 - passo : passo;
                        if (fisso[y, x]) continue;
                        bool acceso = false;
                        if (i < parole.Length * 8)
                        {
                            acceso = ((parole[i >> 3] >> (7 - (i & 7))) & 1) != 0;
                        }
                        moduli[y, x] = acceso;
                        i++;
                    }
                }
            }
        }

        private bool Maschera(int quale, int x, int y)
        {
            switch (quale)
            {
                case 0: return (x + y) % 2 == 0;
                case 1: return y % 2 == 0;
                case 2: return x % 3 == 0;
                case 3: return (x + y) % 3 == 0;
                case 4: return (x / 3 + y / 2) % 2 == 0;
                case 5: return (x * y) % 2 + (x * y) % 3 == 0;
                case 6: return ((x * y) % 2 + (x * y) % 3) % 2 == 0;
                default: return ((x + y) % 2 + (x * y) % 3) % 2 == 0;
            }
        }

        private void ApplicaMaschera(int quale)
        {
            for (int y = 0; y < lato; y++)
            {
                for (int x = 0; x < lato; x++)
                {
                    if (!fisso[y, x] && Maschera(quale, x, y)) moduli[y, x] = !moduli[y, x];
                }
            }
        }

        /// <summary>
        /// Si provano tutte e otto e si tiene la meno peggio.
        ///
        /// La maschera non cambia i dati: cambia come si vedono. Serve a evitare
        /// che il disegno finisca per contenere grandi zone uniformi o qualcosa
        /// che somigli ai quadrati d'angolo, che e' esattamente cio' che manda
        /// in confusione un lettore.
        /// </summary>
        private int ScegliMaschera()
        {
            int migliore = 0, minima = int.MaxValue;
            for (int q = 0; q < 8; q++)
            {
                ApplicaMaschera(q);
                int p = Penalita();
                ApplicaMaschera(q);          // due volte e' come non averla messa
                if (p < minima) { minima = p; migliore = q; }
            }
            return migliore;
        }

        private int Penalita()
        {
            int totale = 0;

            // Regola 1: file di cinque o piu' dello stesso colore.
            for (int y = 0; y < lato; y++) totale += FilaPenalita(y, true);
            for (int x = 0; x < lato; x++) totale += FilaPenalita(x, false);

            // Regola 2: quadrati due per due di un colore solo.
            for (int y = 0; y < lato - 1; y++)
            {
                for (int x = 0; x < lato - 1; x++)
                {
                    bool a = moduli[y, x];
                    if (a == moduli[y, x + 1] && a == moduli[y + 1, x] && a == moduli[y + 1, x + 1])
                    {
                        totale += 3;
                    }
                }
            }

            // Regola 3: la sequenza che somiglia a un quadrato d'angolo.
            for (int y = 0; y < lato; y++)
            {
                for (int x = 0; x < lato - 10; x++) if (Somiglia(x, y, true)) totale += 40;
            }
            for (int x = 0; x < lato; x++)
            {
                for (int y = 0; y < lato - 10; y++) if (Somiglia(x, y, false)) totale += 40;
            }

            // Regola 4: quanto ci si allontana da meta' neri e meta' bianchi.
            int neri = 0;
            for (int y = 0; y < lato; y++)
                for (int x = 0; x < lato; x++) if (moduli[y, x]) neri++;
            int percento = neri * 100 / (lato * lato);
            totale += Math.Abs(percento - 50) / 5 * 10;

            return totale;
        }

        private int FilaPenalita(int quale, bool orizzontale)
        {
            int totale = 0, lunghezza = 1;
            bool precedente = orizzontale ? moduli[quale, 0] : moduli[0, quale];
            for (int i = 1; i < lato; i++)
            {
                bool adesso = orizzontale ? moduli[quale, i] : moduli[i, quale];
                if (adesso == precedente)
                {
                    lunghezza++;
                }
                else
                {
                    if (lunghezza >= 5) totale += 3 + (lunghezza - 5);
                    precedente = adesso;
                    lunghezza = 1;
                }
            }
            if (lunghezza >= 5) totale += 3 + (lunghezza - 5);
            return totale;
        }

        /// <summary>Nero-bianco-nero-nero-nero-bianco-nero con quattro bianchi
        ///  da una parte: e' il disegno del quadrato d'angolo, e non deve
        ///  comparire altrove.</summary>
        private bool Somiglia(int x, int y, bool orizzontale)
        {
            bool[] motivo = { true, false, true, true, true, false, true,
                              false, false, false, false };
            bool avanti = true, indietro = true;
            for (int i = 0; i < 11; i++)
            {
                bool m = orizzontale ? moduli[y, x + i] : moduli[y + i, x];
                if (m != motivo[i]) avanti = false;
                if (m != motivo[10 - i]) indietro = false;
            }
            return avanti || indietro;
        }
    }
}
