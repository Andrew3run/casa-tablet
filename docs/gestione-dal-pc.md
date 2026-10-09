# Gestione Home dal PC

Gestione Home è la finestra sul PC che allestisce il tablet: stato, compilazione
e installazione, luci, routine, app, radio, comandi, voce e notizie.

## Aprirla

    tools\collegamento.ps1          mette « Gestione Home » sul desktop
    tools\collegamento.ps1 -Togli   lo toglie

Poi apri l'icona col doppio clic.

## Dove stanno i file

    pc\GestioneHome.ps1     il telaio: colori, adb, registro, configurazione
    pc\pagine\*.ps1         una pagina per file (dieci)
    pc\Aspetto.cs           i pezzi disegnati a mano: pannelli, tasti, griglie
    pc\Icone.cs             gli stessi Material Symbols del tablet
    pc\Tuya.cs              annunci delle lampade, chiavi via QR, AES-GCM, QR
    pc\GestioneHome.ico     l'icona, disegnata da collegamento.ps1
    pc\config\casa.json     la configurazione, come sta sul PC
    pc\schermate\           le catture

`pc\Audio.cs`, `pc\voce\` e `pc\parola\` sono il banco della
[parola di attivazione](parola-di-attivazione.md), oggi spenta: lasciali al
loro posto, servono il giorno che la riaccendi.

## Come è fatta

- È una finestra WinForms in PowerShell; il lavoro sui byte e sui percorsi sta
  in C#: `Icone.cs`, `Aspetto.cs`, `Tuya.cs`.
- Ogni pagina sta nel suo file in `pc\pagine\`; `GestioneHome.ps1` tiene le
  parti comuni.
- Le pagine si caricano col punto e condividono lo scope del telaio: dai a ogni
  variabile `$script:` un nome unico in tutto il programma. Due `$eRiassunto` in
  due pagine si sovrascrivono.

## Il tablet giusto

`GestioneHome.ps1` trova il tablet con `tools\dispositivo.ps1`, come ogni script
del progetto: il seriale dell'E960 è il segnaposto `0123456789ABCDEF`.

Se il tablet manca, la spia in alto a sinistra diventa rossa e il registro dice
cosa ha trovato al suo posto. Fa eccezione la pagina Allestimento (vedi sotto).

## L'aspetto

Ricalca il tablet:

| | |
|---|---|
| i colori | da `Tinte.java` |
| la scala | da `Misure.java`: sei corpi e cinque spazi, tutto il resto è un multiplo |
| le icone | da `Icone.java`: gli stessi Material Symbols, estratti con uno script |
| i raggi | due: uno per i pannelli, uno per le pastiglie |

- Ogni pagina ha la tinta della sua sezione: giallo per le luci, azzurro per le
  app, viola per la voce, verde Spotify per Spotify.
- Il monospaziato è per quello che scrive la macchina: identificativi Tuya,
  pacchetti, percorsi, registro. Titoli, testi e tasti vanno nel proporzionale.
- Pannelli tondi, tasti, pastiglie e griglie stanno in `pc\Aspetto.cs`.

### Le icone

Le icone si estraggono con lo script da `tablet/src/dev/casa/Icone.java`, così
come sono. Il marchio Spotify resta l'originale, nel suo riquadro 24x24.

### Le finestrelle

Tutte le finestre di dialogo nascono dalle stesse funzioni:

    $fin = Nuova-Finestrella 'Una lampada nuova' 'quello che serve ...' \
                             'lampada_piena' $cGiallo 470
    $y = Riga-Modulo $fin.corpo 0 'Nome' $campo 'Come la chiami a voce' 470
    ...
    Apri-Finestrella $fin $y @($no, $ok)

- Intestazione con icona e tinta della sezione, corpo, banda dei tasti in fondo:
  tasti sempre a destra, l'ultimo è quello principale.
- Dai le misure a `ClientSize`, lo spazio dentro (invece di `Form.Size`).
  `Apri-Finestrella` somma da sola margine, intestazione, corpo e banda.
- Per i testi che vanno a capo usa `Casa.Testo`: `Adatta()` lo misura e
  restituisce la `y` della riga dopo. `Riga-Modulo` fa lo stesso per etichetta,
  campo e spiegazione. Lascia stare `AutoSize`: allarga anche la larghezza.
- Per le domande usa `Chiedi`, con tasti che dicono cosa fanno (« Togli la
  lampada », in rosso).
- Per l'altezza delle caselle di testo usa `Riga-Casella`, che arrotonda a righe
  intere.
- Per i campioni di colore usa `Tasto-Colore`: `Casa.Tasto` ignora `BackColor`.

## Le dieci pagine

### Il tablet

Mostra lo stato (collegamento, build, Android, batteria, spazio, app installata
e in funzione, device owner, chiosco) e ha i tasti: compila e installa, riavvia
l'app, apri il pannello, schermata, registro, Play Store.

- **Compila e installa** dura una trentina di secondi: gira in un processo a
  parte, e il registro si aggiorna mentre lavora.
- **Schermata** fa `screencap` sul tablet e poi `pull`. Da PowerShell evita
  `adb exec-out screencap -p > file.png`: aggiunge un BOM e rovina il PNG.

### Allestimento

Prepara un tablet da zero, passo per passo, anche un tablet diverso dall'E960.
I passi sono diciassette e ognuno dice nome, scopo, come si controlla e, quando
si può, ha il tasto che lo fa. Per gli altri c'è scritto cosa toccare sullo
schermo: il debug USB, per esempio, si accende solo a mano.

| | |
|---|---|
| Il cavo | se il PC vede nulla, prova un altro cavo: molti sono da sola ricarica |
| Debug USB | sette tocchi sul numero di build. Se l'app è già device owner le Impostazioni sono nascoste, e il tasto le riapre |
| Autorizzare il PC | la finestra con l'impronta RSA compare a schermo sbloccato |
| API 24 | il confine fra i due progetti. Il ROM dichiara 9.0, l'API dice 24 |
| `platforms;android-24` | sul PC |
| Schermo sempre acceso | `stay_on_while_plugged_in` |
| Installare | compila e installa sul dispositivo scelto in alto |
| Assistente Home è la Home | apre la scelta sul tablet |
| Zero account | serve per il passo dopo; se il device owner c'è già, risulta fatto |
| Device owner | con lui l'app cura il [ciclo di Impostazioni](ciclo-impostazioni.md) |
| Permesso notifiche | aggiunge l'app agli ascoltatori, tiene gli altri, e la riavvia |
| DuraSpeed | e il rimando a `tools\sistema.ps1` per la pulizia completa del ROM |
| Play Protect | il verificatore che va in crash sui nostri APK |
| Animazioni a metà | a zero il passaggio verso Spotify diventa uno scatto |
| I log dell'app | `persist.log.tag.Casa V`: il ROM nasce a livello Errore |
| Compilato in nativo | va rifatto dopo ogni `adb install` |
| Rotazione bloccata | per un apparecchio da muro |

Ogni controllo risponde **fatto**, **da fare** o **non lo so**.

#### Scegliere il dispositivo

Questa pagina serve anche per tablet che `getprop` riconosce ancora come
diversi dall'E960. Il dispositivo si sceglie a mano da un elenco, resta scritto
in alto per tutto il tempo e, se è un altro tablet, la riga diventa ambrata.
L'elenco mostra anche i dispositivi `unauthorized` e `offline`: sono proprio i
casi in cui la pagina serve.

### Le luci

- **Trovare**: ascolta per otto secondi gli annunci broadcast sulle porte 6666
  e 6667. Le lampade Tuya si presentano da sole con identificativo, indirizzo e
  versione del protocollo.
- **La chiave**: in Smart Life apri *Io → Impostazioni → Account e sicurezza →
  Codice utente*, premi « Rileva chiavi », inquadra il QR con l'app e conferma.
  Arrivano tutte le chiavi insieme, e il registro mostra anche la categoria di
  ogni apparecchio (lampada, presa…).
- **L'elenco**: nome, indirizzo, chiave, protocollo e tinta di ogni lampada.
  Una riga rossa è priva di chiave, e il tablet la salta.
- **« Accendi dal tablet »** manda una frase al tablet, ed è lui a parlare con
  la lampada.

### Routine

Un passo dice prima di tutto di che cosa parla:

| | |
|---|---|
| `luce` | una lampada per identificativo, o tutte. Accendi, spegni, inverti, luminosità, colore, bianco, temperatura del bianco |
| `frase` | una frase detta all'assistente: « metti rai radio 1 », « volume al 30 », « apri netflix ». Vale tutto quello che l'assistente capisce |
| `attesa` | tot secondi fermi, quando conta anche il tempo |

- Un passo `frase` passa da `Comandi.java`: provalo prima nella casella « prova
  una frase » della pagina I comandi, che è la stessa porta.
- Metti un'`attesa` fra la radio e il volume: così il volume va sulla radio già
  partita.
- **Prova sul tablet** manda la configurazione e fa partire la routine sul
  tablet.
- I passi girano in ordine, uno alla volta: una lampada Tuya accetta una
  connessione per volta. Un passo `frase` gira sul thread dell'interfaccia, e la
  routine lo aspetta al massimo cinque secondi.
- L'icona: scegline una dalla tendina, o lascia « dal nome » e la sceglie il
  tablet.

### Le app

Decide quali tessere compaiono nella sezione App del tablet.

- A sinistra le tessere, nell'ordine in cui compaiono; a destra le app
  installate. Scegli una riga a destra e portala a sinistra.
- Il nome scritto qui è quello della tessera: « Impostazioni » invece di
  « Settings ».
- Il Meteo è una pagina dell'assistente e nel file si scrive `@meteo`.
  Toglierlo dalla sezione App lascia la scheda del tempo nella Home.
- Compaiono solo le app installate: se Spotify o Netflix mancano, installali.

### La radio

Le stazioni stanno nella configurazione: se un flusso cambia indirizzo, lo
correggi da qui. Finché la configurazione è vuota il tablet usa le ventidue di
fabbrica.

- A sinistra l'elenco, nell'ordine del tablet (sei per riga, lo stesso di
  « stazione successiva »), con su, giù, nuova e togli. A destra la stazione
  scelta: nome, nome a voce, sigla, tinta, logo, indirizzo del flusso.
- I loghi sono i ventuno dentro l'APK, e la tendina li chiede al tablet
  (`loghiBase` nella vetrina). Nella configurazione il logo va per nome
  (`logo_dj`).
- **« Rimetti le ventidue di fabbrica »** prende l'elenco dal tablet
  (`radioBase`).
- **« Provala adesso »** manda la frase `metti <chiave>`: prova insieme il
  flusso e il nome a voce.
- Con un indirizzo `https://` il registro avvisa: su questo Android quei flussi
  restano in attesa (vedi [meteo.md](meteo.md)).

### Spotify

Due cose separate, con due pastiglie di stato in cima alla pagina:

- **l'accesso** è l'account dentro go-librespot, il demone che suona. Si fa una
  volta, dal tablet: la sezione Spotify mostra un QR, lo inquadri col telefono e
  approvi. Oppure scegli l'assistente fra gli apparecchi di Spotify Connect;
- **la chiave** è un'applicazione registrata a tuo nome su
  developer.spotify.com e serve solo per la ricerca.

Lo stato lo dà il tablet, con le prime e le ultime lettere del client id.

    adb shell am broadcast -a dev.casa.CHIAVE --es cosa stato
    adb shell am broadcast -a dev.casa.CHIAVE --es id '...' --es segreto '...'
    adb shell am broadcast -a dev.casa.CHIAVE --es cosa dimentica

- Il secret sta in un campo coperto e si svuota appena inviato.
- **« Dimentica l'accesso »** cancella solo `credentials.json`.

### I comandi

Un comando è fatto di tre elenchi:

| | |
|---|---|
| **quando** | le frasi che lo fanno scattare. Basta che quella detta ne contenga una |
| **fa** | i passi, gli stessi delle routine: una lampada, una frase all'assistente, un'attesa |
| **dice** | come risponde. Se sono più di una, ne sceglie una a caso |

- I comandi scritti qui vengono prima di quelli di fabbrica: così cambi anche
  una risposta che ti piace poco. Scegli parole precise in *quando*: « luce »
  prende anche « che luce c'è fuori ». Il registro dice quale regola ha preso la
  frase:

      I/Casa: comandi: "casa che si fa" la prende Che si fa (che si fa / come siamo messi)

- Nelle risposte puoi mettere i segnaposto `{ora}`, `{data}`, `{suona}`,
  `{luci}`, `{nome}`: l'assistente li riempie al momento di parlare. Uno scritto
  male resta com'è, come testo.
- **Prova questo** manda la configurazione e dice al tablet la prima frase della
  regola: così provi anche che la frase faccia scattare proprio quella.
- **Prova una frase**, in fondo alla pagina, manda una frase qualunque dritta a
  `Comandi`, saltando il riconoscitore. La risposta compare sotto
  la frase, letta dal registro:

      I/Casa: prova: spegni la radio
      I/Casa: dice: Ho spento la radio.

- **« Cosa non ha capito »** legge dal registro le frasi cadute nel vuoto
  (`PC nonCapito ...`) e ne fa un comando nuovo con la frase già dentro.
- I cinquanta comandi di fabbrica stanno dietro il tasto « Cosa Casa capisce
  già ».

#### Le risposte di fabbrica

Dietro il tasto **Le risposte di fabbrica** riscrivi le risposte fisse del
tablet. Ognuna ha un nome (`radio.spenta`, `musica.pausa`, `saluti.nome`):
riscrivila una volta e cambia in tutti i punti dove compare. L'elenco arriva dal
tablet (`risposteBase` nella vetrina).

Separa con una barra più modi di dire la stessa cosa; il tablet ne sceglie uno a
caso ed evita di ripetere lo stesso due volte di fila:

    Radio spenta. / Ho spento la radio. / Fatto, radio spenta.

- **Rimetti quella di serie** toglie la voce dalla configurazione: torna la
  risposta di fabbrica attuale.
- Le risposte composte (« Metto Levante. », « Sono le 7 e 20. ») si cambiano con
  un comando tuo e i segnaposto.

#### Un livello solo

Dentro un comando dell'utente, le frasi dei passi vanno solo ai comandi di
fabbrica. Così una regola « buonanotte » con il passo « buonanotte a tutti »
gira una volta sola.

### La voce

Qui scegli come suona l'assistente: voce, ritmo e tono, ascoltando ogni prova
dal tablet. La parola di attivazione è spenta; come riaccenderla sta in
[parola-di-attivazione.md](parola-di-attivazione.md).

- **Quale voce**: l'elenco lo dà il tablet (`PAROLA --es cosa voci`), una riga per voce:

      I/Casa: PC voce it-it-x-kda-local|400|no|si

  cioè nome, qualità, se vuole la rete, se è in uso. Le voci `network` suonano
  meglio e tacciono quando manca il Wi-Fi: la pagina lo scrive accanto al nome.
  « Automatica » è in cima.
- **Il ritmo**: 0,90 è più calmo di quello di serie.
- **Il tono** va da 0,8 a 1,2.

      adb shell am broadcast -a dev.casa.PAROLA --es cosa voce  --es valore it-it-x-kda-local
      adb shell am broadcast -a dev.casa.PAROLA --es cosa ritmo --ef valore 0.90
      adb shell am broadcast -a dev.casa.PAROLA --es cosa tono  --ef valore 0.95

A ogni cambio il tablet pronuncia subito una frase di prova. Le tre scelte
stanno nella configurazione e tornano al riavvio, in silenzio.

**Apri il pannello sul tablet** apre il pannello delle impostazioni: si apre
solo da qui.

### Le notizie

Le principali d'Italia ci sono sempre: nella Home a turno col meteo, sul riposo
a turno con l'agenda. Qui scegli il **posto** (vuoto: la città del meteo), lo
**sport** (tendina, « spento » in cima) e la **squadra** di calcio.

- Gli sport li dà il tablet (`sportBase` nella vetrina).
- Le stesse scelte si fanno dall'ingranaggio della pagina Notizie sul tablet:
  entrambe le strade scrivono la voce `notizie` della configurazione.
- A destra c'è cosa arriva sul tablet: per ogni linguetta quanti titoli, da
  quanto e il primo:

      adb shell am broadcast -a dev.casa.NOTIZIE --es cosa aggiorna
      adb shell am broadcast -a dev.casa.NOTIZIE --es cosa stato
      I/Casa: PC notizie filone|...|12|3 min fa|L'alfabeto del mondo ...

Il resto sta in [notizie.md](notizie.md).

## La configurazione

Luci, routine, app e il resto stanno in un file solo, che va e torna fra PC e
tablet:

    il tablet scrive   .../files/adesso.json       cosa sta usando adesso
    il PC scrive       .../files/da-mettere.json   cosa deve usare
    sul PC ne resta    pc\config\casa.json

La cartella è `/sdcard/Android/data/dev.casa/files/`.

- **Due file**: il tablet riscrive `adesso.json` (la vetrina) appena parte;
  `da-mettere.json` è la buca del PC, e il tablet la cancella dopo averla letta.
- **Il file sostituisce gli elenchi che nomina**: una lampada tolta qui sparisce
  anche dal tablet. Per svuotare un elenco, nominalo vuoto: `"app": []` vuol
  dire zero app.
- **Quello che il file tace resta com'era**: così restano al sicuro la voce,
  scelta dal tablet, e [il riposo](riposo.md).
- Se la configurazione manca, o il file è illeggibile (`Archivio` torna null),
  valgono le lampade e le app di fabbrica.
- Dopo il push parte un broadcast, e Gestione Home dice « fatto » quando il
  tablet ha letto.

      adb push casa.json /sdcard/Android/data/dev.casa/files/da-mettere.json
      adb shell am broadcast -a dev.casa.CONFIG --es cosa prendi
      adb shell am broadcast -a dev.casa.CONFIG --es cosa vetrina
      adb shell am broadcast -a dev.casa.CONFIG --es cosa routine --es nome Cinema
      adb shell am broadcast -a dev.casa.CONFIG --es cosa luci

Il registro conferma con una riga come
`config: presa dal PC - 2 luci, 4 routine, 4 app`.

## Tuya e QR, per chi modifica il codice

- Le chiamate a Tuya usano AES-GCM scritto a mano in `Tuya.cs`.
  `Casa.Gcm.Prova()` lo verifica all'avvio; se fallisce, la pagina delle luci
  blocca la richiesta delle chiavi.
- `Casa.Qr` è `Qr.java` tradotto riga per riga: se cambi l'uno, cambia l'altro
  e confronta le matrici per lo stesso testo.

## Trappole della shell di Android 7

La shell di Android 7 è **toybox**:

| scritto | cosa succede davvero |
|---|---|
| `tr -dc 0-9` | « Needs 1 argument » |
| `cut -d" " -f4` | le virgolette si perdono nel passaggio da PowerShell ad adb: « Needs -fcb » |
| `dpm list-owners` | su API 24 è assente: « unknown command » |
| `tail -1` | stampa tutto, intestazione compresa. Usa `tail -n 1` |

Da PowerShell usa gli **apici singoli** per il testo da passare alla shell del
tablet: le virgolette doppie spariscono prima di arrivare ad `adb`.

## Il registro

Il ROM nasce con `log.tag = E` e mostra solo i tag aperti a mano: scrivi tutto
sotto il tag `Casa` e metti il nome del pezzo nel messaggio (`config:`,
`PC `…). Per leggere: `logcat -s Casa:I | grep config`.

## Trappole di PowerShell

### GetNewClosure

Dentro una funzione, un gestore di evento va chiuso con `.GetNewClosure()`, e la
chiusura porta dentro solo le variabili locali: lì `$script:risposteBase` vale
`$null`. Copia prima in una variabile locale quello che serve:

    $base = @($script:risposteBase)
    $cfg  = $script:config          # lo stesso oggetto, non una copia

Al livello del file lascia stare `GetNewClosure`.

### Casa.Tasto

`Casa.Tasto` implementa `IButtonControl`: funziona con `DialogResult` e
`AcceptButton`.

## Il collegamento e l'icona

`tools\collegamento.ps1` disegna l'icona, crea il `.lnk` e scrive
`pc\avvia.vbs`; `-Togli` toglie tutto.

- L'icona è un `.ico` a mappe di bit: `System.Drawing.Icon` legge solo questo.
- Il collegamento lancia `wscript.exe`, così la console resta nascosta.
- La finestra si mostra con `ShowWindow(SW_SHOWNORMAL)` (`Casa.Finestra.Mostra`).
- Gli errori diventano finestre in italiano: `trap` per l'avvio,
  `Application.ThreadException` per i gestori di evento.

## L'avvio

- I sorgenti C# si compilano in `pc\bin\Casa.dll` e il programma ricompila da
  solo quando un `.cs` è più recente.
- Il lavoro lungo con adb va in un filo a parte con `Aspetta`
  (in `GestioneHome.ps1`) e riceve tutto per argomento:

      $r = Aspetta 'Chiedo al tablet come sta...' {
          param($adb, $seriale, $comando)
          & $adb -s $seriale shell $comando
      } @($script:adb, $script:seriale, $script:CHIEDI_STATO)

- Durante l'attesa blocca i clic con `EnableWindow` (Win32), lasciando stare
  `Form.Enabled`.
- Le pagine pesanti (Allestimento, Spotify) si caricano alla prima apertura.
