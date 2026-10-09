# Gestione Home - il banco di lavoro del tablet, dal PC.
#
# Dieci pagine:
#
#   Il tablet     come sta, compila e installa, schermata, registro
#   Allestimento  la guida passo per passo, anche su un tablet mai preparato
#   Le luci       chi c'e' in casa, le chiavi col QR, l'elenco che va sul tablet
#   Routine       le scene: luci, ma anche radio, musica, volume, app
#   Le app        quali tessere si vedono nella sezione App del tablet
#   La radio      le stazioni: quali ci sono, in che ordine, con che logo
#   Spotify       l'accesso, e la chiave che serve solo per cercare
#   I comandi     cosa Casa capisce, cosa esegue e come risponde
#   La voce       quale voce risponde, con che ritmo e che tono
#   Le notizie    il posto, lo sport e la squadra delle notizie sul tablet
#
# Si apre dal collegamento sul desktop (tools\collegamento.ps1 lo crea).
#
# PERCHE' WINFORMS E NON UN'APP VERA. Il primo progetto ha un'app .NET
# (pc\TabDeck.App) perche' li' il PC fa da client di uno schermo remoto: c'e'
# del disegno da fare. Qui il PC chiama adb, sposta dei file e disegna un codice
# QR, e una finestra che si apre senza compilare niente - senza SDK, senza
# ripristino di pacchetti, senza un passo di build che si rompe fra sei mesi -
# vale piu' di una che si apre meglio. Il conto vero e' su Icone.cs, Aspetto.cs
# e Tuya.cs, che stanno in C# perche' li' si toccano i singoli byte e i singoli
# punti: una cifratura fatta a mano, e i percorsi ridisegnati a ogni fotogramma.
#
# PERCHE' ADESSO E' PIU' DI UN FILE. Era uno solo di milleduecento righe, e per
# tre pagine andava bene. Con sette, e con la meta' del programma che e' fatta
# di elenchi da modificare, un file solo vuol dire scorrere per un minuto ogni
# volta che si cerca qualcosa. Ogni pagina sta nel suo file dentro pagine\, e
# questo tiene quello che le pagine si spartiscono: i colori, i costruttori, il
# registro, adb, e la configurazione che va e viene dal tablet.
#
# I file di pagine\ si caricano col punto, quindi girano in QUESTO scope: le
# funzioni e le variabili $script: che definiscono sono le stesse che si vedono
# da qui. Non e' un dettaglio da nascondere - e' il motivo per cui la divisione
# in file non ha richiesto di inventare un modo di passarsi le cose.
#
# Windows PowerShell 5.1: niente && , niente ?: , niente ?. - il parser di
# questa versione li rifiuta.
#
# E SOLO ASCII, come ogni altro script del progetto. Windows PowerShell 5.1
# legge un .ps1 senza BOM come ANSI, non come UTF-8: le virgolette basse, la
# lineetta lunga e il punto mediano arrivavano a schermo come coppie di
# caratteri storti. Le due cure sarebbero un BOM in testa al file o niente
# caratteri oltre il 127esimo; qui vale la seconda, perche' e' quella che gli
# altri script seguono gia' - ed e' anche il motivo per cui i documenti di
# questo progetto scrivono "e'" invece di "e" accentata.

param([switch]$Compatta)

Set-StrictMode -Off
$ErrorActionPreference = 'Continue'

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

<##
 # Se l'avvio va storto, si deve VEDERE.
 #
 # Da quando il collegamento passa da wscript non c'e' nessuna console, e senza
 # console un errore all'avvio non lo legge nessuno: il programma semplicemente
 # non si apre. E' successo davvero - una ricompilazione fallita, il motore
 # vecchio caricato, la finestra mai comparsa - e da fuori era indistinguibile
 # da un doppio clic che non aveva fatto niente.
 #
 # Un errore che ferma il programma adesso apre una finestra e dice cosa e'
 # successo e dove. Sono le righe che separano "non parte" da "non parte
 # PERCHE'".
 #>
trap {
    $dove = ''
    if ($_.InvocationInfo) { $dove = "`r`n`r`n" + $_.InvocationInfo.PositionMessage }
    [System.Windows.Forms.MessageBox]::Show(
        "Gestione Home non e' riuscita ad avviarsi.`r`n`r`n" + $_.Exception.Message + $dove,
        'Gestione Home', 'OK', 'Error') | Out-Null
    break
}

<##
 # E un errore DENTRO la finestra, dopo che si e' aperta, non deve diventare la
 # finestra di sistema.
 #
 # Il trap qui sopra prende quello che succede mentre lo script gira dritto. Un
 # errore dentro un gestore di evento - un tasto premuto, un cronometro che
 # scatta - non passa di li': arriva al ciclo dei messaggi di WinForms, e senza
 # nessuno che lo prenda Windows apre la finestra grigia del debug JIT, con lo
 # stack di System.Management.Automation dentro. E' successo, e chi la vede non
 # ha nessun modo di capire che cosa era andato storto.
 #
 # Con queste due righe l'errore diventa un messaggio in italiano, e il
 # programma resta aperto: quello che stava facendo e' fallito, il resto no.
 #>
[System.Windows.Forms.Application]::SetUnhandledExceptionMode(
    [System.Windows.Forms.UnhandledExceptionMode]::CatchException)
[System.Windows.Forms.Application]::add_ThreadException({
    param($chi, $e)
    # Avvisa e' la finestrella del programma, e c'e' solo dopo che i
    # costruttori sono stati letti. Prima di allora - e se dovesse essere lei a
    # rompersi - resta quella di sistema: brutta, ma sempre meglio della
    # finestra grigia del debug JIT con dentro lo stack di PowerShell.
    $testo = "Qualcosa e' andato storto." + [Environment]::NewLine + [Environment]::NewLine +
             $e.Exception.Message + [Environment]::NewLine + [Environment]::NewLine +
             "Il programma resta aperto: quello che stavi facendo non e' riuscito, il resto si'."
    try {
        Avvisa $testo 'Qualcosa e'' andato storto' $script:cAmbra 'campanella'
    } catch {
        [System.Windows.Forms.MessageBox]::Show($testo, 'Gestione Home', 'OK', 'Warning') | Out-Null
    }
})

# ---------------------------------------------------------------- percorsi --

$pc      = $PSScriptRoot
$radice  = Split-Path $pc -Parent
$adb     = Join-Path $radice 'tools\platform-tools\adb.exe'
$dirConf = Join-Path $pc 'config'
if (-not (Test-Path $dirConf)) { New-Item -ItemType Directory -Path $dirConf -Force | Out-Null }

# Le cartelle pcoce\ non si creano piu' da qui: erano per le registrazioni
# della parola di attivazione, che e' spenta. Quelle che ci sono restano - non
# si butta niente - ma un programma che crea cartelle vuote per una funzione che
# non ha piu' e' un programma che racconta una cosa che non e' vera.

# Il tablet giusto lo sceglie sempre e solo questo file: il seriale dell'E960 e'
# il segnaposto MediaTek 0123456789ABCDEF, e l'SM-T210 dell'altro progetto usa
# lo stesso adb e lo stesso cavo.
#
# L'unica pagina che puo' uscire da questa regola e' Allestimento, e lo fa
# scegliendo esplicitamente: vedi pagine\Allestimento.ps1.
. (Join-Path $radice 'tools\dispositivo.ps1')

<##
 # I quattro file C# si compilano UNA VOLTA e poi si caricano da bin\Casa.dll.
 #
 # PERCHE'. Add-Type compila chiamando csc.exe, cioe' un PROCESSO A PARTE, e un
 # processo a parte con una console apre una finestra nera - che lampeggiava a
 # ogni avvio, tre volte. In piu' erano otto decimi di secondo buttati ogni
 # volta per ricompilare un codice che non era cambiato.
 #
 # NON E' UN PASSO DI BUILD, e la differenza conta: non c'e' niente da lanciare
 # a mano e niente da ricordarsi. Il confronto fra le date lo fa il programma, e
 # se la copia manca o e' vecchia se la rifa' da solo. La regola del progetto
 # era "nessun passo di build che si rompe fra sei mesi", non "nessuna
 # compilazione": la compilazione c'e' sempre stata, succedeva a ogni avvio.
 #
 # TUTTI E QUATTRO INSIEME, e non uno per file: Add-Type fa un assembly per
 # chiamata, e due assembly separati non si vedono fra loro - Aspetto non
 # troverebbe Icone e la compilazione fallirebbe dicendo che il nome non esiste.
 #>
# AUDIO.CS NON E' PIU' IN ELENCO, E IL FILE RESTA DOV'E'. Dentro c'e' il motore
# che legge i WAV e genera le varianti per l'allenamento della parola di
# attivazione: da quando quella e' spenta non lo chiama piu' nessuno, e
# compilarlo a ogni cambiamento sarebbe lavoro per niente. Il file, le
# registrazioni in pcoce\ e docs/parola-di-attivazione.md restano al loro
# posto: il giorno che la parola si riaccende, si rimette questa riga.
$sorgentiCs = @('Icone.cs', 'Aspetto.cs', 'Tuya.cs') |
    ForEach-Object { Join-Path $pc $_ }
$binDir = Join-Path $pc 'bin'
$dllCasa = Join-Path $binDir 'Casa.dll'
if (-not (Test-Path $binDir)) { New-Item -ItemType Directory -Path $binDir -Force | Out-Null }

$daRifare = -not (Test-Path $dllCasa)
if (-not $daRifare) {
    $quandoDll = (Get-Item $dllCasa).LastWriteTimeUtc
    foreach ($f in $sorgentiCs) {
        if ((Get-Item $f).LastWriteTimeUtc -gt $quandoDll) { $daRifare = $true; break }
    }
}

# Si compila su un file temporaneo e poi si sposta: se qualcosa va storto a
# meta', quello che resta sul disco e' la copia di prima - che funziona - e non
# mezzo assembly che non si carica. E' la scrittura atomica di Archivio.java.
if ($daRifare) {
    # I resti delle volte andate storte. Se il programma viene chiuso fra la
    # compilazione e lo spostamento, il file provvisorio resta li': innocuo, ma
    # dopo qualche mese la cartella e' piena di copie da settantasettemila byte
    # con dei nomi che non dicono niente.
    Get-ChildItem $binDir -Filter 'Casa-*.dll' -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue

    $provvisorio = Join-Path $binDir ('Casa-' + [Guid]::NewGuid().ToString('N') + '.dll')
    try {
        Add-Type -Path $sorgentiCs -ReferencedAssemblies System.Drawing, System.Windows.Forms `
                 -OutputAssembly $provvisorio -OutputType Library
        Move-Item -Path $provvisorio -Destination $dllCasa -Force
    } catch {
        Remove-Item $provvisorio -ErrorAction SilentlyContinue
        if (-not (Test-Path $dllCasa)) { throw }
        # C'e' ancora la copia di prima: si va avanti con quella, e lo si dice
        # nel registro. Meglio il programma di ieri che nessun programma.
        $script:motoreVecchio = $_.Exception.Message
    }
}

<##
 # Il motore si carica DAI BYTE, non dal percorso.
 #
 # "Add-Type -Path" su un .dll lo apre e se lo tiene aperto per tutta la vita del
 # processo: il file resta bloccato. La volta dopo - programma ancora aperto, o
 # due copie aperte insieme - la ricompilazione non riesce a scrivere, e il
 # risultato era il peggiore possibile: la compilazione falliva, si caricava il
 # .dll VECCHIO, il codice nuovo non c'era, e siccome la console e' nascosta non
 # lo diceva nessuno. La finestra non compariva, e basta.
 #
 # Leggendo i byte e caricando quelli, il file non lo tiene aperto nessuno.
 #>
[Reflection.Assembly]::Load([IO.File]::ReadAllBytes($dllCasa)) | Out-Null

$script:seriale = $null
$script:cartellaTablet = '/sdcard/Android/data/dev.casa/files/parola'
$script:cartellaCasa   = '/sdcard/Android/data/dev.casa/files'
$script:parola = @{}

# ------------------------------------------------------- colori e caratteri --
#
# Vengono tutti da Aspetto.cs, che a sua volta li prende da Tinte.java e da
# Misure.java del tablet. Qui restano i nomi brevi con cui le pagine li
# chiamano: cambiare una tinta si fa in un posto solo, e quel posto e' di la'.

function Tinta([string]$hex) { [Casa.Aspetto]::Da($hex) }

$cFondo    = [Casa.Aspetto]::Fondo
$cPannello = [Casa.Aspetto]::Pannello
$cRilievo  = [Casa.Aspetto]::Rilievo
$cBordo    = [Casa.Aspetto]::Bordo
$cTesto    = [Casa.Aspetto]::Testo
$cMedio    = [Casa.Aspetto]::Medio
$cTenue    = [Casa.Aspetto]::Tenue
$cBlu      = [Casa.Aspetto]::Blu
$cVerde    = [Casa.Aspetto]::Verde
$cAmbra    = [Casa.Aspetto]::Ambra
$cRosso    = [Casa.Aspetto]::Rosso
$cViola    = [Casa.Aspetto]::Viola
$cAzzurro  = [Casa.Aspetto]::Azzurro
$cGiallo   = [Casa.Aspetto]::Giallo
$cSpotify  = [Casa.Aspetto]::Spotify
$cNotizie  = [Casa.Aspetto]::Notizie

$fTesto   = [Casa.Aspetto]::Corpo
$fMedio   = [Casa.Aspetto]::Forte
$fTitolo  = [Casa.Aspetto]::Titolo
$fVoce    = [Casa.Aspetto]::Voce
$fFisso   = [Casa.Aspetto]::Fisso
$fNota    = [Casa.Aspetto]::Nota

# ------------------------------------------------------------- costruttori --
#
# Le pagine chiamano questi e non i controlli: cosi' l'aspetto si cambia qui e
# di la' non si tocca niente. E' la stessa ragione per cui sul tablet le misure
# stanno in Misure e non sparse nelle sezioni.

function Nuova-Etichetta($testo, $x, $y, $largo, $colore, $font) {
    $l = New-Object System.Windows.Forms.Label
    # SE NON CI STA, TRE PUNTINI. Di serie la Label taglia di netto: la riga
    # della build - E960V1.4_HXL_11_EN_BOE9881_WXGA_DHTP_20191126 - finiva a
    # meta' di una cifra, e non c'era niente che dicesse che continuava. Tre
    # puntini costano un carattere e dicono "c'e' dell'altro".
    $l.AutoEllipsis = $true
    $l.Text = $testo
    $l.Location = New-Object System.Drawing.Point($x, $y)
    $l.Size = New-Object System.Drawing.Size($largo, 20)
    $l.ForeColor = $colore
    $l.BackColor = [System.Drawing.Color]::Transparent
    $l.Font = $font
    return $l
}

<##
 # Un tasto. Il colore che si passa e' quello del segno, e da quello si ricava
 # tutto il resto - il fondo sotto il cursore, il bordo, il pieno quando e'
 # primario.
 #
 # Il rosso non e' un colore fra gli altri: chi passa $cRosso sta dicendo che il
 # tasto toglie qualcosa, e il tasto lo sa. Cosi' non serve ricordarsi di
 # mettere anche il rango.
 #>
function Nuovo-Tasto($testo, $x, $y, $largo, $alto, $colore) {
    $b = New-Object Casa.Tasto
    $b.Text = $testo
    $b.Location = New-Object System.Drawing.Point($x, $y)
    $b.Size = New-Object System.Drawing.Size($largo, $alto)
    $b.Tinta = $colore
    if ($colore -eq $script:cRosso) { $b.Rango = [Casa.Rango]::Pericolo }
    elseif ($colore -eq $script:cTenue) { $b.Rango = [Casa.Rango]::Quieto }
    return $b
}

# L'azione principale della schermata: piena, una sola per pagina.
function Tasto-Primario($tasto, $icona) {
    $tasto.Rango = [Casa.Rango]::Primario
    if ($icona) { $tasto.Icona = $icona }
    return $tasto
}

function Nuovo-Pannello($x, $y, $largo, $alto) {
    $p = New-Object Casa.Pannello
    $p.Location = New-Object System.Drawing.Point($x, $y)
    $p.Size = New-Object System.Drawing.Size($largo, $alto)
    return $p
}

function Nuovo-Elenco($x, $y, $largo, $alto) {
    $e = New-Object Casa.Elenco
    $e.Location = New-Object System.Drawing.Point($x, $y)
    $e.Size = New-Object System.Drawing.Size($largo, $alto)
    return $e
}

<##
 # Un campo di testo dentro la sua cornice.
 #
 # Torna la CORNICE, non la casella: il testo pero' si legge e si scrive lo
 # stesso con .Text, perche' la cornice lo gira a chi sta dentro (vedi
 # Aspetto.cs). Chi ha bisogno degli eventi della casella - un invio che manda
 # la frase - la trova in .Dentro.
 #>
function Nuovo-Campo($x, $y, $largo) {
    $c = New-Object Casa.Cornice
    $c.Location = New-Object System.Drawing.Point($x, $y)
    $c.Size = New-Object System.Drawing.Size($largo, 28)
    $t = New-Object System.Windows.Forms.TextBox
    $t.BorderStyle = 'None'
    $t.BackColor = $script:cRilievo
    $t.ForeColor = $script:cTesto
    $t.Font = $script:fTesto
    $t.Height = 16
    $c.Metti($t)
    return $c
}

function Nuova-Tendina($x, $y, $largo) {
    $t = New-Object Casa.Tendina
    $t.Location = New-Object System.Drawing.Point($x, $y)
    $t.Size = New-Object System.Drawing.Size($largo, 24)
    return $t
}

<##
 # Un elenco a colonne.
 #
 # L'ultimo argomento dice quali colonne ha scritto la macchina - identificativi
 # Tuya, nomi di pacchetto - e vanno in monospaziato. Non e' un vezzo: e' il
 # segnale che quella cosa non l'abbiamo scritta noi e che si copia com'e'.
 #>
function Nuova-Griglia($x, $y, $largo, $alto, $colonne, $macchina) {
    $g = New-Object Casa.Griglia
    $g.Location = New-Object System.Drawing.Point($x, $y)
    $g.Size = New-Object System.Drawing.Size($largo, $alto)
    foreach ($c in $colonne) { [void]$g.Columns.Add($c[0], $c[1]) }
    if ($macchina) { $g.Macchina = [bool[]]$macchina }

    # L'ULTIMA COLONNA ARRIVA AL BORDO. La banda delle intestazioni la disegna
    # una finestra di Windows, e quella la ridipingiamo cella per cella: quello
    # che avanza a destra dell'ultima colonna non e' nessuna cella, resta come
    # l'ha lasciato il sistema, ed e' BIANCO. In mezzo a un pannello scuro si
    # vede come un quadratino di luce nell'angolo, e sembra un difetto di
    # disegno perche' lo e'.
    $usate = 0
    for ($i = 0; $i -lt $g.Columns.Count - 1; $i++) { $usate += $g.Columns[$i].Width }
    $resto = $largo - $usate - 4
    if ($resto -gt 40) { $g.Columns[$g.Columns.Count - 1].Width = $resto }
    return $g
}

function Nuova-Pastiglia($testo, $x, $y, $largo, $colore, $pieno) {
    $p = New-Object Casa.Pastiglia
    $p.Text = $testo
    $p.Location = New-Object System.Drawing.Point($x, $y)
    $p.Size = New-Object System.Drawing.Size($largo, 20)
    $p.Tinta = $colore
    $p.Pieno = [bool]$pieno
    return $p
}

<##
 # QUANTO E' ALTA UNA RIGA DENTRO UNA CASELLA DI TESTO.
 #
 # NON E' Font.Height, ed e' costato mezz'ora scoprirlo. Font.Height e'
 # l'interlinea tipografica del carattere - ascendente, discendente e lo spazio
 # che il disegnatore ci ha messo attorno - e per Segoe UI a otto punti vale
 # quindici. La casella di testo di Windows non usa quella: usa tmHeight, che
 # per lo stesso carattere vale TREDICI.
 #
 # Due punti a riga sembrano niente. Su una casella alta 153 vogliono dire
 # undici righe invece di dieci, e l'undicesima entra a meta': si vedono le
 # lettere senza le code, e sembra un testo rotto invece che un testo che
 # continua sotto. E' il difetto che si voleva togliere.
 #
 # Si misura chiedendo alla casella dove mette il primo carattere della seconda
 # riga, che e' l'unica risposta che viene da chi disegna davvero. Una volta per
 # carattere, e poi si ricorda.
 #>
$script:altezzeRiga = @{}

function Riga-Casella($font) {
    $chiave = $font.ToString()
    if ($script:altezzeRiga.ContainsKey($chiave)) { return $script:altezzeRiga[$chiave] }
    $quanto = $font.Height
    try {
        $f = New-Object System.Windows.Forms.Form
        $t = New-Object System.Windows.Forms.TextBox
        $t.Multiline = $true
        $t.Font = $font
        $t.Size = New-Object System.Drawing.Size(200, 100)
        $t.Text = "a`r`nb"
        $f.Controls.Add($t)
        $f.CreateControl()
        $t.CreateControl()
        $d = $t.GetPositionFromCharIndex(3).Y - $t.GetPositionFromCharIndex(0).Y
        if ($d -gt 0) { $quanto = $d }
        $f.Dispose()
    } catch { }
    $script:altezzeRiga[$chiave] = $quanto
    return $quanto
}

<##
 # Un blocco di testo che si legge e non si tocca: spiegazioni, elenchi,
 # istruzioni. Sono tanti, e scriverli a mano ogni volta erano otto righe.
 #
 # NIENTE A CAPO AUTOMATICO. Questi testi sono gia' impaginati a mano: hanno
 # elenchi numerati, righe rientrate, e righe vuote fra un capoverso e l'altro.
 # Lasciando andare a capo la casella, una riga di settantasei caratteri dentro
 # una casella da sessanta si spezza a meta' e la meta' finisce sotto, in mezzo
 # alla riga dopo - "un tablet appeso al / muro che si spegne". Si legge
 # malissimo e sembra un errore di dati, non di larghezza.
 #
 # IL CORPO PICCOLO e' il gradino della "nota" della scala, ed e' giusto due
 # volte: sono testi secondari - li legge chi non sa gia' - e a questo corpo
 # settantasei caratteri ci stanno.
 #
 # LA BARRA DI SCORRIMENTO SOLO SE SERVE. Una TextBox con ScrollBars a
 # "Vertical" la fa vedere SEMPRE, anche quando il testo ci sta tutto: mezza
 # dozzina di note di quattro righe avevano accanto una barra grigia chiara -
 # alta quanto loro, l'unica cosa chiara della schermata - che non scorreva
 # niente. Sembrava che ci fosse dell'altro testo sotto, e non c'era.
 #
 # Si contano le righe e si guarda se ci stanno. Se ci stanno, niente barra.
 #>
function Nuovo-Testo($x, $y, $largo, $alto, $contenuto) {
    $t = New-Object System.Windows.Forms.TextBox
    $t.Multiline = $true
    $t.ReadOnly = $true
    $t.WordWrap = $false
    #
    # E L'ALTEZZA SI ARROTONDA A RIGHE INTERE. Una casella alta 112 con righe da
    # 15 ne mostra sette e mezza: dell'ottava si vedono le lettere senza le code,
    # e non sembra un testo che continua sotto - sembra un testo rotto. Meglio
    # sette righe intere e la barra che dice che ce n'e' dell'altra.
    <##
     # IL RITORNO A CAPO INTERO, E IL GIORNO CHE E' COSTATO.
     #
     # La casella di testo di Windows va a capo su "`r`n" e SOLO su quello. Un
     # "`n" da solo non e' un a capo: e' un carattere che non sa disegnare, e la
     # casella mostra tutto su UNA RIGA. Non da' nessun errore, e da fuori
     # sembra un testo troncato.
     #
     # Da dove arriva un "`n" da solo: dai file di questo programma. Non hanno
     # tutti gli stessi fine riga - Radio.ps1 e GestioneHome.ps1 vanno a LF, gli
     # altri a CRLF - e un blocco @'...'@ porta dentro quelli del file in cui e'
     # scritto. La pagina "La radio" aveva tre spiegazioni ridotte alla loro
     # prima riga per questo, e nel codice non c'era niente da vedere.
     #
     # Si normalizza qui, dove passano tutte: prima si appiattisce a "`n", poi
     # si rimette il paio. Cosi' non importa piu' come e' salvato il file.
     #>
    $contenuto = (($contenuto -replace "`r`n", "`n") -replace "`n", "`r`n")
    $righe = @(($contenuto -split "`r`n")).Count
    $unaRiga = Riga-Casella $script:fNota
    $quante = [Math]::Max(1, [int](($alto - 2) / $unaRiga))
    $alto = $quante * $unaRiga + 2
    $t.ScrollBars = $(if ($righe -gt $quante) { 'Vertical' } else { 'None' })
    $t.Location = New-Object System.Drawing.Point($x, $y)
    $t.Size = New-Object System.Drawing.Size($largo, $alto)
    $t.BackColor = $script:cPannello
    $t.ForeColor = $script:cMedio
    $t.BorderStyle = 'None'
    $t.Font = $script:fNota
    $t.Text = $contenuto
    $t.TabStop = $false
    return $t
}

# ------------------------------------------------------- le finestrelle ----

<##
 # Un blocco di testo che va a capo e che si da' l'altezza che gli serve.
 #
 # E' il rimedio al difetto che si vedeva in mezza dozzina di finestrelle: una
 # spiegazione scritta come Nuova-Etichetta con un'altezza decisa a occhio, che
 # a schermo andava a capo una volta di piu' del previsto e finiva sotto la
 # casella di testo, tagliata a meta'. Non si legge, e non sembra un errore di
 # misura: sembra un programma rotto.
 #
 # Qui l'altezza la decide il testo (vedi Casa.Testo in Aspetto.cs).
 #>
function Nuovo-Paragrafo([int]$x, [int]$y, [int]$largo, [string]$contenuto, $colore, $font) {
    $t = New-Object Casa.Testo
    $t.Location = New-Object System.Drawing.Point($x, $y)
    $t.Width = $largo
    if ($colore) { $t.Tinta = $colore }
    if ($font)   { $t.Font = $font }
    $t.Text = $contenuto
    [void]$t.Adatta()
    return $t
}

<##
 # Un tasto largo quanto la sua scritta.
 #
 # "Rimetti quella di serie" dentro un tasto da centoventi punti si legge
 # "Rimetti quella di s...", e la larghezza dei tasti era decisa a occhio in
 # trenta punti diversi del programma. Il righello di Aspetto la misura.
 #>
function Adatta-Tasto($tasto, [int]$minimo) {
    if (-not $minimo) { $minimo = 96 }
    $largo = [Casa.Aspetto]::Largo($tasto.Text, $tasto.Font) + 26
    if ($tasto.Icona) { $largo += 24 }
    $tasto.Width = [Math]::Max($minimo, $largo)
    return $tasto
}

<##
 # Un tasto che mostra il colore che porta, e che apre la tavolozza.
 #
 # ERA ROTTO IN TRE POSTI UGUALI. Le lampade, le routine e le stazioni avevano
 # tutte e tre lo stesso tasto scritto a mano, e tutte e tre facevano
 # "$t.BackColor = <il colore>". Casa.Tasto pero' si disegna da se': il fondo se
 # lo calcola dal rango e dalla tinta, e BackColor non lo guarda nessuno. Il
 # risultato era un tasto grigio con dentro scritto "#F2D06B" - il colore c'era
 # solo come parola, ed e' esattamente la cosa che un campione di colore non
 # deve essere.
 #
 # Con il rango Primario il tasto e' PIENO della sua tinta e la scritta ci sta
 # sopra scura: il campione e' il tasto.
 #>
function Tasto-Colore([string]$partenza) {
    $t = New-Object Casa.Tasto
    $t.Height = 28
    $t.Width = 130
    $t.Rango = [Casa.Rango]::Primario
    $t.Font = $script:fFisso
    $t.Add_Click({
        $d = New-Object System.Windows.Forms.ColorDialog
        $d.FullOpen = $true
        try { $d.Color = Tinta $this.Text } catch { }
        if ($d.ShowDialog() -eq 'OK') {
            $this.Text = '#{0:X2}{1:X2}{2:X2}' -f $d.Color.R, $d.Color.G, $d.Color.B
            $this.Tinta = $d.Color
        }
    })
    Colore-Tasto $t $partenza
    return $t
}

# Il colore scritto e quello disegnato, sempre insieme: separarli vuol dire un
# tasto verde con scritto "#F2D06B".
function Colore-Tasto($tasto, [string]$hex) {
    if (-not $hex) { $hex = '#5A6C8C' }
    $tasto.Text = $hex
    try { $tasto.Tinta = Tinta $hex } catch { $tasto.Tinta = $script:cMedio }
}

# Un tasto della banda in fondo a una finestrella. La posizione la decide
# Apri-Finestrella: qui contano solo il nome, il rango e l'esito.
function Tasto-Fondo([string]$testo, $colore, [string]$icona, [string]$esito, [string]$rango) {
    $t = New-Object Casa.Tasto
    $t.Text = $testo
    $t.Height = 32
    $t.Tinta = $(if ($colore) { $colore } else { $script:cMedio })
    if ($icona) { $t.Icona = $icona }
    if ($esito) { $t.DialogResult = $esito }
    if ($rango) { $t.Rango = [Casa.Rango]::$rango }
    elseif ($colore -eq $script:cRosso) { $t.Rango = [Casa.Rango]::Pericolo }
    return (Adatta-Tasto $t 100)
}

<##
 # UNA FINESTRELLA, E SEMPRE LA STESSA.
 #
 # COM'ERANO. Otto finestre di dialogo, ognuna costruita da capo: la misura
 # della finestra scritta a mano in pixel, i controlli piazzati a coordinate
 # fisse, e i tasti "Va bene / Lascia stare" appoggiati in fondo al corpo, a
 # un'altezza indovinata. Il conto sbagliava in due modi, e tutti e due si
 # vedono solo aprendo la finestra:
 #
 #   - Form.Size NON E' lo spazio dentro. Comprende la cornice e la barra del
 #     titolo, che su Windows 11 sono trentanove punti in altezza: una finestra
 #     "alta 330" ne ha 291 di spazio utile, e i tasti messi a 274 con 30 di
 #     altezza finivano tredici punti sotto il bordo. Tagliati, e cliccabili a
 #     meta'. E' il difetto della finestrella delle lampade.
 #   - un testo va a capo dove decide il carattere, non dove si spera. Le
 #     spiegazioni con un'altezza fissa perdevano l'ultima riga.
 #
 # COM'E' ADESSO. Chi apre una finestrella dice il titolo e quanto e' larga; il
 # corpo lo riempie dall'alto in giu' e alla fine dichiara quanto e' venuto
 # alto. L'altezza della FINESTRA la calcola Apri-Finestrella sommando i pezzi
 # e chiedendola a ClientSize, che e' lo spazio DENTRO: cosi' non c'e' nessun
 # numero da indovinare e non c'e' modo che qualcosa resti fuori.
 #
 # I TASTI STANNO IN UNA BANDA, in fondo, sempre a destra e sempre nello stesso
 # ordine: l'ultimo dell'elenco e' quello che si vuole premere. Su otto
 # finestre diverse, sapere dove sono senza cercarli vale piu' di qualunque
 # altra cosa si possa fare al disegno.
 #>
function Nuova-Finestrella {
    param([string]$titolo,
          [string]$sottotitolo = '',
          [string]$icona = '',
          $tinta = $null,
          [int]$largo = 520,
          [switch]$Elastica)

    if (-not $tinta) { $tinta = $script:cBlu }

    $f = New-Object System.Windows.Forms.Form
    $f.Text = $titolo
    $f.FormBorderStyle = $(if ($Elastica) { 'Sizable' } else { 'FixedDialog' })
    $f.MaximizeBox = [bool]$Elastica
    $f.MinimizeBox = $false
    $f.ShowInTaskbar = $false
    $f.StartPosition = 'CenterParent'
    $f.BackColor = $script:cFondo
    $f.ForeColor = $script:cTesto
    $f.Font = $script:fTesto
    $f.KeyPreview = $true

    $intestazione = New-Object Casa.Intestazione
    $intestazione.Text = $titolo
    $intestazione.Sottotitolo = $sottotitolo
    $intestazione.Icona = $icona
    $intestazione.Tinta = $tinta
    $intestazione.Location = New-Object System.Drawing.Point(18, 18)
    $intestazione.Size = New-Object System.Drawing.Size($largo, 52)
    $f.Controls.Add($intestazione)

    # Il corpo e' un pannello e non la finestra stessa: cosi' chi lo riempie
    # conta da zero, e spostare tutto in giu' di dieci punti - perche'
    # l'intestazione e' cresciuta - non vuol dire ritoccare trenta coordinate.
    $corpo = New-Object System.Windows.Forms.Panel
    $corpo.Location = New-Object System.Drawing.Point(18, 82)
    $corpo.Width = $largo
    $corpo.BackColor = $script:cFondo
    $f.Controls.Add($corpo)

    $banda = New-Object Casa.Banda
    $banda.Dock = 'Bottom'
    $f.Controls.Add($banda)

    # elastico: chi fa una finestrella ridimensionabile ci mette il blocco che
    # rimpicciolisce quello che sta DENTRO il corpo. Riceve la larghezza e
    # l'altezza nuove. Vedi Apri-Finestrella.
    return [ordered]@{
        f = $f; corpo = $corpo; banda = $banda; testa = $intestazione
        largo = $largo; tinta = $tinta; elastico = $null
    }
}

<##
 # Chiude il conto e apre la finestrella. Torna quello che ha risposto.
 #
 # $alto e' quanto e' venuto alto il corpo: lo sa chi l'ha riempito, e nessun
 # altro puo' saperlo. I tasti si passano nell'ordine in cui si leggono, da
 # sinistra a destra: l'ultimo e' il principale e finisce all'estrema destra.
 #>
# I tasti in fila da destra, dentro la banda. Si rifa' a ogni cambio di misura,
# quindi e' una funzione e non tre righe dentro Apri-Finestrella.
function Metti-Tasti($tasti, [int]$altoBanda, [int]$largoTotale) {
    $x = $largoTotale - 18
    $tutti = @($tasti)
    for ($i = $tutti.Count - 1; $i -ge 0; $i--) {
        $t = $tutti[$i]
        $x -= $t.Width
        $t.Location = New-Object System.Drawing.Point($x, [int](($altoBanda - $t.Height) / 2))
        $x -= 10
    }
}

<##
 # Chiude il conto e apre la finestrella. Torna quello che ha risposto.
 #
 # $alto e' quanto e' venuto alto il corpo: lo sa chi l'ha riempito, e nessun
 # altro puo' saperlo. I tasti si passano nell'ordine in cui si leggono, da
 # sinistra a destra: l'ultimo e' il principale e finisce all'estrema destra.
 #
 # L'ALTEZZA SI DA' A ClientSize E NON A Size, ed e' la riga che mette fine al
 # difetto. Size comprende la cornice e la barra del titolo - trentanove punti
 # su Windows 11 - e chi scriveva "Size = 330" credeva di avere 330 punti di
 # spazio quando ne aveva 291. I tasti finivano sotto il bordo.
 #>
function Apri-Finestrella($fin, [int]$alto, $tasti) {
    $S4 = 18
    $altoBanda = 60

    $fin.corpo.Height = $alto
    $largoTotale = $fin.largo + $S4 * 2
    $altoTotale = $S4 + 52 + 12 + $alto + $S4 + $altoBanda
    $fin.f.ClientSize = New-Object System.Drawing.Size($largoTotale, $altoTotale)

    $tutti = @($tasti)
    foreach ($t in $tutti) {
        $fin.banda.Controls.Add($t)
        if ($t.DialogResult -eq [System.Windows.Forms.DialogResult]::Cancel) {
            $fin.f.CancelButton = $t
        }
    }
    $ultimo = $tutti[$tutti.Count - 1]
    if ($ultimo.DialogResult -ne [System.Windows.Forms.DialogResult]::None) {
        $fin.f.AcceptButton = $ultimo
    }
    Metti-Tasti $tutti $altoBanda $largoTotale

    # Le finestrelle elastiche: il corpo segue la finestra e i tasti restano
    # incollati a destra. Quello che c'e' DENTRO il corpo lo sistema chi l'ha
    # messo, con $fin.elastico - qui non si puo' sapere se e' una griglia da
    # allargare o un testo da lasciare com'e'.
    if ($fin.f.FormBorderStyle -eq 'Sizable') {
        $fin.f.MinimumSize = $fin.f.Size
        $fin.f.Add_Resize({
            $c = $this.ClientSize
            $largo = $c.Width - $S4 * 2
            $fin.testa.Width = $largo
            $fin.corpo.Width = $largo
            $fin.corpo.Height = [Math]::Max(40, $c.Height - ($S4 + 52 + 12) - $S4 - $altoBanda)
            if ($fin.elastico) { & $fin.elastico $largo $fin.corpo.Height }
            Metti-Tasti $tutti $altoBanda $c.Width
        }.GetNewClosure())
    }

    # Anche le finestrelle si fanno vedere: il programma e' stato lanciato
    # nascosto, e quello stato lo eredita ogni finestra che nasce dopo. E la
    # barra del titolo si fa scura qui, dove passano tutte.
    $fin.f.Add_Shown({
        [Casa.Finestra]::Scura($this.Handle)
        [Casa.Finestra]::Mostra($this.Handle)
    })
    return $fin.f.ShowDialog()
}

<##
 # Una riga di modulo: l'etichetta a sinistra, il campo a destra, e sotto la
 # riga che spiega a che cosa serve.
 #
 # Torna la y dove comincia la riga DOPO. Finche' si impagina cosi' - ogni riga
 # dice dove finisce, e la prossima parte da li' - un modulo non puo' piu'
 # uscire dal suo bordo: e' il conto che sbagliava a mano in Luci, in Routine e
 # nei passi.
 #>
function Riga-Modulo($dove, [int]$y, [string]$etichetta, $campo, [string]$nota, [int]$largo) {
    $xCampo = 112
    $e = Nuova-Etichetta $etichetta 0 ($y + 5) ($xCampo - 10) $script:cTenue $script:fTesto
    $dove.Controls.Add($e)
    # L'etichetta appena fatta, per chi deve ancora toccarla: nella finestrella
    # di un passo cambia nome - "Valore", "La frase", "Secondi" - a seconda di
    # quello che si sta facendo. Una funzione torna gia' la y della riga dopo,
    # e due valori di ritorno in PowerShell vogliono dire un array che il
    # chiamante deve spacchettare: peggio di questa riga.
    $script:etichettaFatta = $e

    $campo.Left = $xCampo
    $campo.Top = $y
    if ($campo.Width -le 1) { $campo.Width = $largo - $xCampo }
    $dove.Controls.Add($campo)

    $sotto = $y + $campo.Height + 4
    if ($nota) {
        $n = Nuovo-Paragrafo $xCampo $sotto ($largo - $xCampo) $nota $script:cTenue $script:fNota
        $dove.Controls.Add($n)
        $sotto += $n.Height
    }
    return ($sotto + 14)
}

<##
 # Il titolo e il resto, da una domanda scritta come si parla.
 #
 # Chi chiede qualcosa la scrive come una frase sola - "Tolgo X?" e poi due a
 # capo e la spiegazione - e non deve saperne niente di intestazioni. Qui la
 # frase si spezza dove si spezzerebbe leggendola.
 #
 # LA PRIMA RIGA DIVENTA IL TITOLO SOLO SE CI STA. Il titolo si scrive nel
 # corpo grande e su una riga sola: una domanda lunga - "Mando la
 # configurazione al tablet e poi gli dico ..." - li' diventerebbe tre puntini.
 # Quando non ci sta, il titolo e' quello generico e la domanda intera va nel
 # corpo, dove il testo va a capo e si legge tutta. Meglio un'intestazione
 # banale che una domanda troncata.
 #>
function Spezza-Domanda([string]$testo, [string]$diRipiego, [int]$largo) {
    $t = ($testo -replace "`r`n", "`n").Trim()
    $i = $t.IndexOf("`n`n")
    if ($i -lt 0) { $i = $t.IndexOf("`n") }
    $prima = $(if ($i -lt 0) { $t } else { $t.Substring(0, $i).Trim() })
    $dopo  = $(if ($i -lt 0) { '' } else { $t.Substring($i).Trim() })

    # Il titolo comincia dopo il quadratino dell'icona e non arriva al bordo.
    $sta = [Casa.Aspetto]::Largo($prima, [Casa.Aspetto]::Titolo) -le ($largo - 50)
    if ($sta) { return @($prima, $dopo) }
    return @($diRipiego, $t)
}

<##
 # Si' o no, con l'aspetto del programma.
 #
 # PERCHE' NON PIU' MessageBox. Quella di Windows e' una finestra bianca con un
 # cerchietto azzurro in mezzo a un programma che e' nero-blu dalla prima riga
 # all'ultima, e con due tasti che dicono "Si" e "No" a una domanda che ne
 # aveva gia' due migliori dentro. Ed e' l'unica finestra del programma in cui
 # una domanda lunga si legge in un carattere che non e' il nostro.
 #
 # IL TASTO DICE COSA FA. "Si" non dice niente: il tasto che toglie una lampada
 # si chiama "Togli", ed e' rosso perche' toglie. Chi legge solo i tasti - e
 # sono tanti - capisce lo stesso.
 #>
function Chiedi([string]$domanda, [string]$titolo, [string]$fai, [switch]$Pericolo) {
    if (-not $titolo) { $titolo = 'Confermi?' }
    if (-not $fai) { $fai = 'Va bene' }

    $largo = 500
    $pezzi = Spezza-Domanda $domanda $titolo $largo
    $fin = Nuova-Finestrella $pezzi[0] '' $(if ($Pericolo) { 'cestino' } else { 'spunta' }) `
                             $(if ($Pericolo) { $script:cRosso } else { $script:cBlu }) $largo

    $alto = 0
    if ($pezzi[1]) {
        $p = Nuovo-Paragrafo 0 0 $largo $pezzi[1] $script:cMedio $script:fTesto
        $fin.corpo.Controls.Add($p)
        $alto = $p.Height
    }

    $no = Tasto-Fondo 'Lascia stare' $script:cTenue '' 'Cancel' 'Quieto'
    $si = Tasto-Fondo $fai $(if ($Pericolo) { $script:cRosso } else { $script:cBlu }) `
                      $(if ($Pericolo) { 'cestino' } else { 'spunta' }) 'OK' 'Primario'

    return ((Apri-Finestrella $fin $alto @($no, $si)) -eq 'OK')
}

<##
 # Una riga di testo, chiesta come si deve.
 #
 # ERA LA FINESTRELLA PIU' ROTTA DEL PROGRAMMA, e la si vedeva a occhio nudo:
 # la spiegazione stava in un'etichetta alta cinquantasei punti, e quasi tutte
 # le domande che ci passano dentro ne occupano di piu'. L'ultima riga finiva
 # sotto la casella di testo, tagliata all'altezza della cintura delle lettere.
 # Adesso l'altezza la decide il testo, e la casella comincia dove il testo
 # finisce.
 #>
function Chiedi-Testo([string]$domanda, [string]$partenza, [string]$titolo, [string]$sotto) {
    if (-not $titolo) { $titolo = 'Scrivilo qui' }

    $largo = 500
    $pezzi = Spezza-Domanda $domanda $titolo $largo
    $fin = Nuova-Finestrella $pezzi[0] '' 'regola' $script:cBlu $largo

    $y = 0
    if ($pezzi[1]) {
        $p = Nuovo-Paragrafo 0 0 $largo $pezzi[1] $script:cMedio $script:fTesto
        $fin.corpo.Controls.Add($p)
        $y = $p.Height + 14
    }

    $c = Nuovo-Campo 0 $y $largo
    $c.Text = $partenza
    $fin.corpo.Controls.Add($c)
    $y += $c.Height

    if ($sotto) {
        $n = Nuovo-Paragrafo 0 ($y + 8) $largo $sotto $script:cTenue $script:fNota
        $fin.corpo.Controls.Add($n)
        $y += 8 + $n.Height
    }

    # Il fuoco sulla casella, e il testo gia' scelto: la domanda arriva quasi
    # sempre con un suggerimento dentro, e chi non lo vuole scrive e basta.
    $fin.f.Add_Shown({ $c.Dentro.Focus(); $c.Dentro.SelectAll() }.GetNewClosure())

    $no = Tasto-Fondo 'Lascia stare' $script:cTenue '' 'Cancel' 'Quieto'
    $ok = Tasto-Fondo 'Va bene' $script:cBlu 'spunta' 'OK' 'Primario'

    if ((Apri-Finestrella $fin $y @($no, $ok)) -ne 'OK') { return $null }
    return $c.Text
}

# Una cosa da leggere e basta. Serve a quello che prima era una MessageBox di
# avviso: un errore dentro la finestra non deve diventare la finestra di
# sistema.
function Avvisa([string]$testo, [string]$titolo, $tinta, [string]$icona) {
    if (-not $titolo) { $titolo = 'Gestione Home' }
    if (-not $tinta) { $tinta = $script:cAmbra }
    if (-not $icona) { $icona = 'campanella' }

    $largo = 520
    $pezzi = Spezza-Domanda $testo $titolo $largo
    $fin = Nuova-Finestrella $pezzi[0] '' $icona $tinta $largo

    $alto = 0
    if ($pezzi[1]) {
        $p = Nuovo-Paragrafo 0 0 $largo $pezzi[1] $script:cMedio $script:fTesto
        $fin.corpo.Controls.Add($p)
        $alto = $p.Height
    }
    $ok = Tasto-Fondo 'Ho capito' $tinta '' 'OK' 'Primario'
    [void](Apri-Finestrella $fin $alto @($ok))
}

# --------------------------------------------------------------- finestra ----

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Gestione Home'
$form.Size = New-Object System.Drawing.Size(1180, 840)
# Il minimo e' la misura di partenza, non meno: le pagine hanno posizioni
# fisse, e sotto questa larghezza il pannello di destra finirebbe oltre il
# bordo. Ingrandire va bene - avanza aria a destra - rimpicciolire no.
$form.MinimumSize = New-Object System.Drawing.Size(1180, 840)
$form.StartPosition = 'CenterScreen'
$form.BackColor = $cFondo
$form.ForeColor = $cTesto
$form.Font = $fTesto
$ico = Join-Path $pc 'GestioneHome.ico'
# L'icona la disegna tools\collegamento.ps1. Se manca o non si legge si tira
# dritto con quella di serie: una finestra senza icona funziona, una finestra
# che non si apre no.
if (Test-Path $ico) {
    try { $form.Icon = New-Object System.Drawing.Icon -ArgumentList $ico } catch { }
}

# ---- il registro, in fondo ---------------------------------------------------
#
# Ha una banda sua con un'etichetta e il tasto per svuotarlo. Prima era una
# casella di testo appoggiata sul fondo, e a colpo d'occhio sembrava una parte
# della pagina invece che il diario di quello che e' successo.

$pieDiPagina = New-Object System.Windows.Forms.Panel
$pieDiPagina.Dock = 'Bottom'
$pieDiPagina.Height = 158
$pieDiPagina.BackColor = $cFondo
$pieDiPagina.Padding = New-Object System.Windows.Forms.Padding(18, 0, 18, 12)

$cornicelog = New-Object Casa.Pannello
$cornicelog.Dock = 'Fill'
$cornicelog.Titolo = 'registro'
$pieDiPagina.Controls.Add($cornicelog)

$log = New-Object System.Windows.Forms.TextBox
$log.Multiline = $true
$log.ReadOnly = $true
$log.ScrollBars = 'Vertical'
$log.Location = New-Object System.Drawing.Point(18, 34)
$log.BackColor = $cPannello
$log.ForeColor = $cMedio
$log.Font = $fFisso
$log.BorderStyle = 'None'
$log.TabStop = $false
$cornicelog.Controls.Add($log)

$tSvuota = Nuovo-Tasto 'Svuota' 0 8 84 24 $cTenue
$cornicelog.Controls.Add($tSvuota)
$tSvuota.Add_Click({ $log.Clear(); Registra 'Registro svuotato.' })

# La casella non ha Dock: dentro un pannello disegnato, Dock='Fill' la
# metterebbe sopra gli angoli tondi e sopra l'etichetta. Si ridimensiona a mano
# quando la finestra cambia, che e' l'unica cosa che il Dock faceva per noi.
$cornicelog.Add_Resize({
    $log.Size = New-Object System.Drawing.Size(($cornicelog.Width - 36), ($cornicelog.Height - 46))
    $tSvuota.Left = $cornicelog.Width - 102
})

$form.Controls.Add($pieDiPagina)

function Registra([string]$testo) {
    if ($null -eq $testo) { return }
    foreach ($riga in ($testo -split "`r?`n")) {
        if ($riga.Trim().Length -eq 0) { continue }
        $log.AppendText(('{0:HH:mm:ss}  {1}{2}' -f (Get-Date), $riga.TrimEnd(), [Environment]::NewLine))
    }
    $log.SelectionStart = $log.TextLength
    $log.ScrollToCaret()
}

# ---- la colonna delle pagine -------------------------------------------------

$barra = New-Object System.Windows.Forms.Panel
$barra.Dock = 'Left'
$barra.Width = 196
$barra.BackColor = $cPannello
$form.Controls.Add($barra)

$titolo = Nuova-Etichetta 'Gestione Home' 18 20 170 $cTesto $fVoce
$titolo.Height = 24
$barra.Controls.Add($titolo)

$sottotitolo = Nuova-Etichetta 'DUODUOGO E960' 19 44 170 $cTenue $fNota
$sottotitolo.Height = 16
$barra.Controls.Add($sottotitolo)

$spia = Nuova-Pastiglia 'cerco il tablet...' 18 66 150 $cAmbra $true
$barra.Controls.Add($spia)

$contenuto = New-Object System.Windows.Forms.Panel
$contenuto.Dock = 'Fill'
$contenuto.BackColor = $cFondo
$contenuto.Padding = New-Object System.Windows.Forms.Padding(20, 16, 20, 6)
$form.Controls.Add($contenuto)
$contenuto.BringToFront()

<##
 # LA RIGA IN CIMA CHE DICE CHE SI STA LAVORANDO.
 #
 # Tre punti di altezza attraverso tutta la finestra, dello stesso blu della
 # Home. Non e' un ornamento: e' l'unica cosa che distingue "sta chiedendo
 # qualcosa al tablet" da "e' piantato", e le due cose da fuori si vedono
 # uguali. La si accende chi fa un lavoro lungo (vedi Aspetta e Occupato) e non
 # la si spegne mai a mano: la spegne il finally.
 #
 # Non e' agganciata a niente col Dock, perche' il Dock le farebbe rubare tre
 # punti a qualcuno anche da spenta: sta sopra tutti, alla riga zero, e si
 # rimette in riga quando la finestra cambia misura.
 #>
$barraLavoro = New-Object Casa.Avanzamento
$barraLavoro.Height = 3
$barraLavoro.Tinta = $cBlu
$barraLavoro.Location = New-Object System.Drawing.Point(0, 0)
$barraLavoro.Visible = $false
$form.Controls.Add($barraLavoro)
$barraLavoro.BringToFront()
$form.Add_Resize({ $script:barraLavoro.Width = $script:form.ClientSize.Width })
$barraLavoro.Width = $form.ClientSize.Width

<##
 # IL VELO: quello che si vede mentre una pagina si sta riempiendo.
 #
 # Le pagine che chiedono qualcosa al tablet alla prima apertura - Allestimento
 # con i suoi diciassette controlli, Spotify, la voce - ci mettono dai quattro
 # ai dieci secondi. Prima in quei secondi si vedeva la pagina VUOTA: le
 # intestazioni, i pannelli, i tasti, e dentro niente. E' la stessa cosa che
 # succedeva all'avvio, e produce lo stesso pensiero: non e' lenta, e' rotta.
 #
 # Il velo dice cosa sta aspettando, con lo stesso pallino e la stessa barra
 # della schermata d'avvio. Non si anima - il filo che lo dipingerebbe e' quello
 # fermo dentro adb, e non c'e' rimedio finche' quel lavoro resta qui - ma dire
 # "sto preparando Allestimento" e' gia' tutta la differenza fra un programma
 # che aspetta e un programma che non risponde.
 #>
$velo = New-Object System.Windows.Forms.Panel
$velo.Dock = 'Fill'
$velo.BackColor = $cFondo
$velo.Visible = $false
$contenuto.Controls.Add($velo)

$veloBlocco = New-Object System.Windows.Forms.Panel
$veloBlocco.Size = New-Object System.Drawing.Size(560, 60)
$veloBlocco.BackColor = $cFondo
$velo.Controls.Add($veloBlocco)

$veloPasso = New-Object Casa.PassoAvvio
$veloPasso.Stato = 1
$veloPasso.Tinta = $cBlu
$veloPasso.Location = New-Object System.Drawing.Point(0, 0)
$veloPasso.Size = New-Object System.Drawing.Size(560, 30)
$veloBlocco.Controls.Add($veloPasso)

$veloBarra = New-Object Casa.Avanzamento
$veloBarra.Location = New-Object System.Drawing.Point(0, 40)
$veloBarra.Size = New-Object System.Drawing.Size(560, 6)
$veloBarra.Tinta = $cBlu
$veloBarra.Quanto = 1
$veloBlocco.Controls.Add($veloBarra)

function Centra-Velo {
    $script:veloBlocco.Left = [Math]::Max(0,
        [int](($script:velo.Width - $script:veloBlocco.Width) / 2))
    $script:veloBlocco.Top = [Math]::Max(0,
        [int](($script:velo.Height - $script:veloBlocco.Height) / 2) - 40)
}
$velo.Add_Resize({ Centra-Velo })

# Nome, icona, tinta e la riga che dice a cosa serve. Le tinte sono quelle che
# il tablet usa per quelle sezioni: entrando in "Le luci" si ritrova il giallo
# della sezione Casa, e non un blu qualsiasi.
#
# Si chiama SCHEDE e non PAGINE, e non e' una preferenza: in PowerShell i nomi
# delle variabili NON distinguono maiuscole e minuscole, quindi $PAGINE e
# $pagine - l'array dei pannelli, poche righe piu' sotto - sarebbero la stessa
# variabile, e la seconda cancellerebbe la prima. Il sintomo era una fila di
# "impossibile eseguire l'indicizzazione in una matrice null" dentro una
# funzione che leggeva una tabella riempita venti righe prima.
$SCHEDE = @(
    @('Il tablet',    'accensione',    $cMedio,   'come sta, e i tasti di tutti i giorni'),
    @('Allestimento', 'regola',        $cAmbra,   'preparare un tablet da zero, un passo alla volta'),
    @('Le luci',      'lampada_piena', $cGiallo,  'chi c''e'' in casa, le chiavi, e l''elenco che va sul tablet'),
    @('Routine',      'avvia',         $cGiallo,  'un tocco che fa succedere piu'' cose, anche fuori dalle luci'),
    @('Le app',       'app_piena',     $cAzzurro, 'quali tessere si vedono nella sezione App del tablet'),
    @('La radio',     'radio_piena',   $cVerde,   'le stazioni, il loro ordine e il loro logo'),
    @('Spotify',      'spotify',       $cSpotify, 'l''accesso, e la chiave che serve solo per cercare'),
    @('I comandi',    'spunta',        $cBlu,     'cosa Assistente Home capisce, cosa esegue e come risponde'),
    @('La voce',      'microfono',     $cViola,   'quale voce risponde, con che ritmo e con che tono'),
    @('Le notizie',   'notizie',       $cNotizie, 'le notizie di qui, lo sport e la squadra')
)
$PAG_TABLET = 0
$PAG_ALLEST = 1
$PAG_LUCI   = 2
$PAG_ROUT   = 3
$PAG_APP    = 4
$PAG_RADIO  = 5
$PAG_SPOT   = 6
$PAG_CMD    = 7
$PAG_VOCE   = 8
$PAG_NOTIZIE = 9

$pagine = @()
$tastiPagina = @()

for ($i = 0; $i -lt $SCHEDE.Count; $i++) {
    $p = New-Object System.Windows.Forms.Panel
    $p.Dock = 'Fill'
    $p.BackColor = $cFondo
    $p.Visible = ($i -eq 0)
    $contenuto.Controls.Add($p)
    $pagine += $p

    $t = New-Object Casa.VoceBarra
    $t.Text = $SCHEDE[$i][0]
    $t.Icona = $SCHEDE[$i][1]
    $t.Tinta = $SCHEDE[$i][2]
    $t.Location = New-Object System.Drawing.Point(0, (100 + $i * 42))
    $t.Size = New-Object System.Drawing.Size(196, 38)
    $t.Tag = $i
    $barra.Controls.Add($t)
    $tastiPagina += $t
}

# L'intestazione di una pagina: la costruiscono le pagine stesse chiamando
# questa, cosi' il titolo, l'icona e la tinta vengono dalla stessa tabella che
# disegna la barra e non possono discordare.
function Nuova-Intestazione([int]$quale) {
    $h = New-Object Casa.Intestazione
    $h.Text = $SCHEDE[$quale][0]
    $h.Icona = $SCHEDE[$quale][1]
    $h.Tinta = $SCHEDE[$quale][2]
    $h.Sottotitolo = $SCHEDE[$quale][3]
    $h.Location = New-Object System.Drawing.Point(0, 0)
    $h.Size = New-Object System.Drawing.Size(940, 44)
    return $h
}

# La tinta di una pagina, per chi deve intonarci una griglia o un pannello.
function Tinta-Pagina([int]$quale) { return $SCHEDE[$quale][2] }

<##
 # Quello che una pagina fa la PRIMA volta che si apre.
 #
 # Ci si registra chi ha bisogno di chiedere qualcosa al tablet per riempirsi:
 # l'allestimento con i suoi diciassette controlli, Spotify con lo stato, la
 # voce con i campioni. Prima lo facevano tutte all'avvio, e chi apriva il
 # programma solo per mandare una routine pagava lo stesso sei secondi di
 # attesa per delle schermate che non avrebbe aperto.
 #
 # Una volta sola, non a ogni entrata: ogni pagina ha il suo tasto per
 # rileggere, e rileggere da capo a ogni giro renderebbe lento il passare da una
 # pagina all'altra, che e' la cosa che si fa piu' spesso.
 #>
$script:allApertura = @{}
$script:giaAperte = @{}

function Vai-A([int]$quale) {
    for ($i = 0; $i -lt $script:pagine.Count; $i++) {
        $script:pagine[$i].Visible = ($i -eq $quale)
        $script:tastiPagina[$i].Attiva = ($i -eq $quale)
    }
    if ($script:allApertura.ContainsKey($quale) -and -not $script:giaAperte.ContainsKey($quale)) {
        $script:giaAperte[$quale] = $true
        # Il ridisegno prima del lavoro: la pagina compare vuota e si riempie,
        # invece di far aspettare il cambio di pagina.
        #
        # ANCHE LA BARRA, e non e' un dettaglio: quello che sta per partire
        # tiene occupato il filo dell'interfaccia per qualche secondo, e senza
        # questa riga il segno di "sei qui" resta sulla pagina di prima per
        # tutto quel tempo. Chi guarda vede il contenuto nuovo e la barra che
        # dice il contrario, e la prima cosa che pensa e' di aver sbagliato
        # tasto.
        $script:contenuto.Refresh()
        $script:barra.Refresh()
        # E la riga in cima si accende: quello che sta per partire chiede al
        # tablet, e chiedere al tablet vuol dire qualche secondo di finestra
        # ferma. Almeno si vede che e' ferma per un motivo.
        Occupato ('Sto preparando ' + $script:SCHEDE[$quale][0] +
                  ': quello che chiede al tablet ci mette qualche secondo.') `
                 $script:allApertura[$quale]
    }
}

foreach ($t in $tastiPagina) {
    $t.Add_Click({ Vai-A ([int]$this.Tag) }.GetNewClosure())
}

# --------------------------------------------- i lavori che fanno aspettare --
#
# QUAL E' IL PROBLEMA. Ogni cosa che si chiede al tablet costa: un "am
# broadcast", misurato, e' UN SECONDO E MEZZO - torna solo quando il ricevitore
# ha finito, ed e' quello che lo rende affidabile. Chiedere lo stato, mandare la
# configurazione e rileggerla sono due, tre, cinque secondi ciascuna. Fatte
# dentro un gestore di evento sono secondi in cui il filo dell'interfaccia sta
# fermo dentro adb: la finestra non si ridisegna, Windows la sbianca se ci si
# passa sopra un'altra finestra, e dopo tre secondi il titolo diventa "non
# risponde".
#
# Non e' rotta. Ma non c'e' nessun modo di saperlo da fuori, ed e' la stessa
# cosa.
#
# COME SI RISOLVE. Il lavoro va in un filo a parte, e questo resta a far girare
# il ciclo dei messaggi finche' quello non ha finito. La finestra si ridisegna,
# la barra in cima si muove, e chi guarda vede un programma che sta lavorando
# invece di un programma piantato. Chi chiama pero' scrive una riga sola e
# riceve il risultato come se niente fosse: "$r = Aspetta ... { ... }". E' la
# parte che conta, perche' una soluzione che obbliga a spezzare in due ogni
# funzione che tocca adb non la userebbe nessuno.
#
# DoEvents E' UNA PORTA APERTA, e va chiusa. Mentre si gira in tondo, un clic
# su un tasto partirebbe davvero - dentro il lavoro che sta ancora andando - e
# due "manda la configurazione" sovrapposti finiscono con due file spinti sopra
# lo stesso file. Per questo la barra e il contenuto si spengono per tutta
# l'attesa: si vede che c'e' qualcosa in corso, e non si puo' cominciarne
# un'altra.
#
# IL LAVORO NON VEDE NIENTE DI QUA. Gira in uno spazio suo: non ha $adb, non ha
# $script:seriale, non ha Registra. Tutto quello che gli serve si passa per
# argomento. E' scomodo, ed e' anche l'unica cosa che rende sicuro farlo girare
# mentre l'interfaccia e' viva.

$script:filo = $null

function Filo-Pronto {
    if ($script:filo -and $script:filo.RunspaceStateInfo.State -eq 'Opened') { return $script:filo }
    $script:filo = [runspacefactory]::CreateRunspace()
    $script:filo.ApartmentState = 'STA'
    $script:filo.ThreadOptions = 'ReuseThread'
    $script:filo.Open()
    return $script:filo
}

<##
 # Fa fare un lavoro a un filo a parte e resta qui a tenere viva la finestra.
 # Torna quello che il lavoro ha prodotto.
 #
 # $mentre - se c'e' - si chiama a ogni battito: e' li' che la schermata
 # d'avvio legge a che punto e' arrivato il lavoro e spunta i suoi passi.
 #
 # SE IL FILO NON PARTE si fa lo stesso lavoro qui, bloccando. Non e' un caso
 # che ci si aspetti, ma un programma che non si apre perche' non ha potuto
 # aprire un runspace sarebbe la cura peggiore della malattia.
 #>
function Aspetta([string]$cosa, [scriptblock]$lavoro, $argomenti, [scriptblock]$mentre) {
    if ($cosa) { Registra $cosa }

    $ps = $null
    $atteso = $null
    try {
        $ps = [PowerShell]::Create()
        $ps.Runspace = Filo-Pronto
        [void]$ps.AddScript($lavoro)
        foreach ($a in @($argomenti)) { [void]$ps.AddArgument($a) }
        $atteso = $ps.BeginInvoke()
    } catch {
        if ($ps) { $ps.Dispose() }
        Registra "Non ho potuto usare un filo a parte ($($_.Exception.Message)): faccio da qui."
        return (& $lavoro @argomenti)
    }

    [Casa.Finestra]::Ascolta($script:form.Handle, $false)
    $script:barraLavoro.Quanto = 1
    $script:barraLavoro.Visible = $true
    $script:barraLavoro.BringToFront()
    $script:form.Cursor = [System.Windows.Forms.Cursors]::AppStarting
    try {
        while (-not $atteso.IsCompleted) {
            $script:barraLavoro.Batti()
            if ($mentre) { & $mentre }
            [System.Windows.Forms.Application]::DoEvents()
            Start-Sleep -Milliseconds 33
        }
        if ($mentre) { & $mentre }
        return $ps.EndInvoke($atteso)
    } finally {
        $ps.Dispose()
        $script:barraLavoro.Visible = $false
        [Casa.Finestra]::Ascolta($script:form.Handle, $true)
        $script:form.Cursor = [System.Windows.Forms.Cursors]::Default
    }
}

<##
 # Per quello che non si puo' mandare in un filo a parte: le pagine che si
 # riempiono alla prima apertura chiamano venti funzioni di questo scope, e
 # portarle tutte di la' vorrebbe dire riscriverle.
 #
 # Qui la finestra resta ferma lo stesso - non c'e' rimedio - ma almeno lo
 # DICE: la barra in cima si accende e il cursore diventa quello dell'attesa.
 # La differenza fra "e' rotto" e "sta lavorando" e' tutta li'.
 #>
function Occupato([string]$cosa, [scriptblock]$fai) {
    if ($cosa) { Registra $cosa }
    $script:barraLavoro.Quanto = 1
    $script:barraLavoro.Visible = $true
    $script:barraLavoro.BringToFront()
    $script:form.Cursor = [System.Windows.Forms.Cursors]::AppStarting

    # IL VELO SI DISEGNA PRIMA, e non e' un dettaglio di ordine: dopo non ci
    # sarebbe nessuno a disegnarlo. Il filo che dipinge e' lo stesso che sta per
    # fermarsi dentro adb, quindi tutto quello che si vuole far vedere durante
    # l'attesa va messo a schermo - e RIDISEGNATO - prima di cominciarla.
    if ($cosa) {
        $script:veloPasso.Text = $cosa
        $script:velo.Visible = $true
        $script:velo.BringToFront()
        Centra-Velo
        $script:velo.Refresh()
    }
    $script:barraLavoro.Refresh()
    try { & $fai }
    catch {
        # E SE VA STORTA, LO SI LEGGE. Quello che sta qui dentro riempie una
        # pagina intera, e se si ferma a meta' la pagina resta vuota: senza
        # questa riga si vedeva una schermata bianca e nessuna spiegazione,
        # da nessuna parte. Adesso il motivo e' nel registro, che e' il posto
        # dove chi usa la finestra puo' leggerlo.
        Registra ("Non ce l'ho fatta: " + $_.Exception.Message)
        if ($_.InvocationInfo) { Registra ('  ' + $_.InvocationInfo.PositionMessage.Trim()) }
    }
    finally {
        $script:velo.Visible = $false
        $script:barraLavoro.Visible = $false
        $script:form.Cursor = [System.Windows.Forms.Cursors]::Default
    }
}

# ------------------------------------------------------------------- adb -----

function Trova-Tablet {
    # Le eventuali spiegazioni di Get-TabletDelProgetto vanno sulla console, che
    # qui non c'e': il motivo per cui non lo si trova lo si riscrive nel
    # registro, che e' l'unico posto che chi usa la finestra puo' leggere.
    $script:seriale = Get-TabletDelProgetto -Adb $adb
    if ($script:seriale) {
        $spia.Text = 'collegato'
        # Tinta e non ForeColor: la pastiglia si disegna da se' e il colore del
        # testo di serie non lo guarda nessuno. Restava ambra a tablet
        # collegato, cioe' diceva "sto cercando" mentre l'aveva gia' trovato.
        $spia.Tinta = $script:cVerde
        return $true
    }
    $spia.Text = 'tablet assente'
    $spia.Tinta = $script:cRosso
    return $false
}

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Argomenti)
    if (-not $script:seriale) { return @() }
    $fuori = & $adb -s $script:seriale @Argomenti
    return $fuori
}

function Sh([string]$comando) { return (Adb 'shell' $comando) }

# Come Adb, ma su un seriale scelto a mano. La usa Allestimento, che lavora
# anche su un tablet che non e' questo - ed e' l'unica.
function AdbSu {
    param([string]$Seriale,
          [Parameter(ValueFromRemainingArguments = $true)][string[]]$Argomenti)
    if (-not $Seriale) { return @() }
    $fuori = & $adb -s $Seriale @Argomenti
    return $fuori
}

<##
 # Il pezzo di risposta fra due segnalini.
 #
 # PERCHE' IL TABLET MANDA IL TESTO GREZZO. La prima versione faceva fare tutto
 # alla shell di Android - "dumpsys battery | grep level | tr -dc 0-9" - e
 # sembrava piu' pulito. Non lo era: la shell di Android 7 e' toybox, non
 # coreutils, e la meta' di quelle opzioni non esiste. "tr -dc" rispondeva
 # "Needs 1 argument", "cut -d" "" perdeva le virgolette per strada nel
 # passaggio attraverso adb, e "dpm list-owners" su API 24 non e' ancora nato.
 # Tre modi diversi di non funzionare, tutti in silenzio, tutti con una casella
 # vuota a schermo come unico sintomo.
 #
 # Adesso il tablet stampa quello che sa e basta, in mezzo a dei segnalini, e a
 # capirlo ci pensa il PC - dove le espressioni regolari ci sono davvero e dove
 # un errore si vede subito.
 #>
function Sezione([string[]]$righe, [string]$nome) {
    $dentro = $false
    $fuori = @()
    foreach ($r in $righe) {
        $t = $r.Trim()
        if ($t -like '@@*') { $dentro = ($t -eq ('@@' + $nome)); continue }
        if ($dentro) { $fuori += $r }
    }
    return $fuori
}

# --------------------------------------------------- lavori lunghi, a parte --
#
# Compilare e installare sono trenta secondi: fatti dentro un gestore di evento
# congelerebbero la finestra, e una finestra congelata sembra rotta. Il processo
# parte a parte, scrive su un file temporaneo, e un cronometro travasa nel
# registro le righe nuove. Cosi' si vede l'avanzamento mentre succede.

$script:lavoro = $null
$script:lavoroFile = $null
$script:lavoroLetti = 0
$script:lavoroNome = ''
$script:lavoroPoi = $null

$cronometro = New-Object System.Windows.Forms.Timer
$cronometro.Interval = 250

function Avvia-Lavoro([string]$file, [string[]]$argomenti, [string]$nome, [scriptblock]$poi) {
    if ($script:lavoro -and -not $script:lavoro.HasExited) {
        Registra "C'e' gia' '$($script:lavoroNome)' in corso: aspetta che finisca."
        return
    }
    $script:lavoroFile = [System.IO.Path]::GetTempFileName()
    $script:lavoroLetti = 0
    $script:lavoroNome = $nome
    $script:lavoroPoi = $poi
    Registra "--- $nome ---"
    $script:lavoro = Start-Process -FilePath $file -ArgumentList $argomenti -PassThru `
        -NoNewWindow -RedirectStandardOutput $script:lavoroFile `
        -RedirectStandardError ($script:lavoroFile + '.err')
    $cronometro.Start()
}

# Un file .ps1 del progetto, lanciato come lavoro lungo. Tre righe uguali
# ripetute cinque volte erano cinque occasioni di sbagliare le virgolette.
function Avvia-Script([string]$percorso, [string[]]$argomenti, [string]$nome, [scriptblock]$poi) {
    $tutti = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $percorso + '"'))
    if ($argomenti) { $tutti += $argomenti }
    Avvia-Lavoro 'powershell.exe' $tutti $nome $poi
}

function Travasa {
    if (-not $script:lavoroFile) { return }
    try {
        # Aperto in condivisione: il processo ci sta ancora scrivendo dentro.
        $flusso = New-Object System.IO.FileStream($script:lavoroFile, 'Open', 'Read', 'ReadWrite')
        $lettore = New-Object System.IO.StreamReader($flusso)
        $tutto = $lettore.ReadToEnd()
        $lettore.Close()
        $flusso.Close()
        if ($tutto.Length -gt $script:lavoroLetti) {
            $nuovo = $tutto.Substring($script:lavoroLetti)
            $script:lavoroLetti = $tutto.Length
            Registra $nuovo
        }
    } catch { }
}

$cronometro.Add_Tick({
    Travasa
    if ($script:lavoro -and $script:lavoro.HasExited) {
        $cronometro.Stop()
        Travasa
        $err = $script:lavoroFile + '.err'
        if ((Test-Path $err) -and (Get-Item $err).Length -gt 0) {
            Registra (Get-Content $err -Raw)
        }
        $uscita = $script:lavoro.ExitCode
        Registra ("--- $($script:lavoroNome): uscito con " + $uscita + ' ---')
        Remove-Item $script:lavoroFile, $err -ErrorAction SilentlyContinue
        $script:lavoro = $null
        $script:lavoroFile = $null
        $poi = $script:lavoroPoi
        $script:lavoroPoi = $null
        if ($poi) { & $poi $uscita }
        Aggiorna-Tablet
    }
})

# ====================================================== la configurazione ====
#
# Le luci, le routine e le app di Casa stanno in un file solo, e quel file fa
# avanti e indietro fra il PC e il tablet.
#
#   il tablet scrive   .../files/adesso.json       cosa sta usando adesso
#   il PC scrive       .../files/da-mettere.json   cosa deve usare
#
# Le due direzioni hanno due nomi diversi apposta: con un file unico, il tablet
# che si riavvia riscriverebbe la vetrina sopra quello che il PC aveva appena
# spinto. Il perche' per esteso sta in tablet\src\dev\casa\Configurazione.java.
#
# Sul PC ne resta una copia in pc\config\casa.json. Non e' un doppione inutile:
# e' quello che permette di preparare la configurazione con il tablet staccato,
# ed e' un file di testo che si puo' guardare, copiare e mettere sotto
# controllo di versione.

<##
 # La configurazione comincia VUOTA, non nulla.
 #
 # Da quando l'avvio e' a passi, fra il momento in cui la finestra compare e
 # quello in cui la configurazione arriva dal tablet passano dei secondi - e in
 # quei secondi le pagine ci sono gia' e si possono premere. Con $config nulla,
 # "Nuovo comando" o "Aggiungi" rispondevano "impossibile chiamare un metodo su
 # un'espressione con valore null", che a schermo vuol dire "non funziona".
 #
 # Vuota invece funziona: si aggiunge una lampada, o un comando, e quando la
 # configurazione vera arriva la sostituisce. Il caso in cui si perde qualcosa -
 # scrivere un comando nei primi tre secondi e vederselo sostituire - e' meno
 # peggio di un programma che sembra rotto, e comunque il registro dice che sta
 # leggendo.
 #>
$script:config = [ordered]@{ luci = @(); routine = @(); app = @(); radio = @(); comandi = @(); risposte = [ordered]@{} }
$script:configFile = Join-Path $dirConf 'casa.json'

# Chi si ridisegna quando la configurazione cambia. Ogni pagina ci mette il suo
# blocco: cosi' "rileggi dal tablet" aggiorna tutte e tre le pagine senza che
# questa parte sappia quali sono.
$script:ridisegna = @()

# Il catalogo delle risposte di fabbrica, come lo racconta il tablet. Non e'
# configurazione - non si manda indietro - ed e' l'unico modo di avere l'elenco
# vero senza tenerne una copia sul PC che invecchia.
$script:risposteBase = @()

# I loghi delle stazioni che questo APK ha in casa, come li racconta il
# tablet. Non sono configurazione: non si rimandano indietro.
$script:loghiBase = @()

# Le stazioni di fabbrica, sempre raccontate dal tablet e sempre di sola
# lettura. Vedi pagine\Radio.ps1.
$script:radioBase = @()

# Gli sport che il tablet sa seguire, e la citta' del meteo: tutti e due
# raccontati dal tablet e di sola lettura. Vedi pagine\Notizie.ps1.
$script:sportBase = @()
$script:meteoCitta = ''

function Config-Vuota {
    return [ordered]@{ luci = @(); routine = @(); app = @(); radio = @(); comandi = @(); risposte = [ordered]@{} }
}

# Le liste arrivano da ConvertFrom-Json, che con un elemento solo non torna un
# array ma l'elemento: @() attorno e' quello che evita di scoprirlo il giorno in
# cui resta una lampada sola.
function Config-Normalizza($o) {
    $n = Config-Vuota
    if ($o) {
        if ($o.PSObject.Properties['luci'])    { $n.luci    = @($o.luci) }
        if ($o.PSObject.Properties['routine']) { $n.routine = @($o.routine) }
        if ($o.PSObject.Properties['app'])     { $n.app     = @($o.app) }
        if ($o.PSObject.Properties['radio'])   { $n.radio   = @($o.radio) }
        if ($o.PSObject.Properties['comandi']) { $n.comandi = @($o.comandi) }
        # Le risposte riscritte sono un oggetto, non un elenco: si copia com'e'.
        if ($o.PSObject.Properties['risposte']) { $n.risposte = $o.risposte }
        # Il catalogo di quelle di fabbrica arriva dal tablet e NON si rimanda
        # indietro: e' roba sua, e riscriverglielo vorrebbe dire congelare sul
        # PC una copia che invecchia al primo comando nuovo.
        if ($o.PSObject.Properties['risposteBase']) { $script:risposteBase = @($o.risposteBase) }
        # E i loghi che stanno DENTRO l'APK. Stessa storia delle risposte di
        # fabbrica: sono ventuno PNG compilati nell'app, il PC non li puo'
        # indovinare e non li deve rimandare indietro. Servono alla pagina La
        # radio, per non far scegliere un disegno che sul tablet non c'e'.
        if ($o.PSObject.Properties['loghiBase']) { $script:loghiBase = @($o.loghiBase) }
        # E le ventidue stazioni scritte nel codice del tablet, per il tasto
        # "rimetti quelle di fabbrica": una volta riscritto l'elenco, quelle
        # di partenza non esisterebbero piu' da nessuna parte raggiungibile.
        if ($o.PSObject.Properties['radioBase']) { $script:radioBase = @($o.radioBase) }
        # Le notizie: il posto, lo sport, la squadra. Un oggetto e non un
        # elenco, come la voce - ma questo si scrive da qui, quindi si tiene e
        # si rimanda. C'e' solo se la vetrina lo porta, e la vetrina lo porta
        # sempre: il tablet ci mette le scelte che sta usando.
        if ($o.PSObject.Properties['notizie'] -and $o.notizie) {
            $x = $o.notizie
            $n['notizie'] = [ordered]@{
                localita   = [string]$x.localita
                sport      = [bool]$x.sport
                disciplina = [string]$x.disciplina
                squadra    = [string]$x.squadra
            }
        }
        # Gli sport e la citta' del meteo invece NON si rimandano: sono del
        # tablet, come le risposte di fabbrica.
        if ($o.PSObject.Properties['sportBase']) { $script:sportBase = @($o.sportBase) }
        if ($o.PSObject.Properties['meteoCitta']) { $script:meteoCitta = [string]$o.meteoCitta }
    }
    return $n
}

# Scambia due voci di un elenco. La usano le routine, i loro passi e le app:
# in tutte e tre l'ordine e' quello in cui si vedono sul tablet, quindi
# "su" e "giu'" sono una funzione sola scritta una volta.
function Sposta-Voce($lista, [int]$da, [int]$verso) {
    $a = @($lista)
    if ($da -lt 0 -or $verso -lt 0 -or $verso -ge $a.Count) { return $null }
    $x = $a[$da]
    $a[$da] = $a[$verso]
    $a[$verso] = $x
    return $a
}

function Config-Cambiata {
    Salva-Config-Su-Disco
    foreach ($b in $script:ridisegna) { & $b }
}

function Salva-Config-Su-Disco {
    try {
        ($script:config | ConvertTo-Json -Depth 8) |
            Set-Content -Path $script:configFile -Encoding UTF8
    } catch {
        Registra "Non ho potuto salvare $($script:configFile): $($_.Exception.Message)"
    }
}

function Leggi-Config-Da-Disco {
    if (-not (Test-Path $script:configFile)) { return $false }
    try {
        $script:config = Config-Normalizza (Get-Content $script:configFile -Raw | ConvertFrom-Json)
        return $true
    } catch {
        Registra "pc\config\casa.json non si legge: $($_.Exception.Message)"
        return $false
    }
}

<##
 # Rilegge dal tablet quello che Casa sta usando davvero.
 #
 # Prima si chiede a Casa di rifare la vetrina, poi la si tira giu'. Il primo
 # passo non e' di troppo: il file c'e' gia' dall'ultimo avvio, ma gli indirizzi
 # delle lampade possono essere cambiati da allora, e leggere un file vecchio
 # per non spendere un broadcast e' il modo di rimandare al tablet degli
 # indirizzi che non valgono piu'.
 #>
<##
 # Il pezzo che tocca adb, staccato dal resto perche' possa girare in un filo a
 # parte: si passa tutto per argomento e non si guarda niente di questo scope.
 #
 # Lo usano in due - questa funzione e l'avvio - ed e' il motivo per cui e' una
 # variabile e non due copie della stessa sequenza. Due copie di tre chiamate ad
 # adb sono due posti in cui sbagliare il nome del file.
 #>
$script:PRENDI_VETRINA = {
    param($adb, $seriale, $cartellaCasa, $dove)
    & $adb -s $seriale shell 'am broadcast -a dev.casa.CONFIG --es cosa vetrina' | Out-Null
    if (Test-Path $dove) { Remove-Item $dove -Force -ErrorAction SilentlyContinue }
    & $adb -s $seriale pull ($cartellaCasa + '/adesso.json') $dove | Out-Null
    return (Test-Path $dove)
}

# La vetrina scaricata, letta. Non tocca adb: la chiamano sia chi l'ha appena
# tirata giu' sia l'avvio, che se l'e' fatta portare da un filo a parte.
function Config-Da-File([string]$percorso) {
    try {
        $script:config = Config-Normalizza (Get-Content $percorso -Raw | ConvertFrom-Json)
        return $true
    } catch {
        Registra "La vetrina del tablet non si legge: $($_.Exception.Message)"
        return $false
    }
}

# Che cosa c'e' dentro, in una riga. Si dice nel registro e sulla schermata
# d'avvio, e scriverla due volte vuol dire vederla divergere.
function Conta-Config {
    return ('{0} luci, {1} routine, {2} app, {3} stazioni, {4} comandi tuoi' -f `
        @($script:config.luci).Count, @($script:config.routine).Count,
        @($script:config.app).Count, @($script:config.radio).Count,
        @($script:config.comandi).Count)
}

function Leggi-Config-Dal-Tablet {
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return $false }
    $qui = Join-Path $dirConf 'adesso.json'
    $presa = @(Aspetta 'Chiedo ad Assistente Home la vetrina...' $script:PRENDI_VETRINA `
                       @($script:adb, $script:seriale, $script:cartellaCasa, $qui))[-1]
    if (-not $presa) {
        Registra 'Assistente Home non ha lasciato nessuna vetrina: e'' installata e in funzione?'
        return $false
    }
    if (-not (Config-Da-File $qui)) { return $false }
    Registra ('Dal tablet: ' + (Conta-Config) + '.')
    Config-Cambiata
    return $true
}

<##
 # Manda al tablet la configurazione di adesso, e aspetta che dica di averla
 # presa.
 #
 # Il broadcast torna solo quando il ricevitore ha finito, quindi quando siamo
 # qui la riga nel registro c'e' gia': non serve nessuna attesa a tempo, che
 # sarebbe o troppo corta o sprecata.
 #>
function Manda-Config-Al-Tablet {
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return $false }
    Salva-Config-Su-Disco

    # Spingere, dire di prendere, e leggere se ha preso: tre chiamate, due
    # secondi e mezzo. In un filo a parte, con la riga in cima che si muove.
    $esito = @(Aspetta 'Mando la configurazione al tablet...' {
        param($adb, $seriale, $file, $cartellaCasa)
        $spinta = & $adb -s $seriale push $file ($cartellaCasa + '/da-mettere.json')
        & $adb -s $seriale shell 'am broadcast -a dev.casa.CONFIG --es cosa prendi' | Out-Null
        return @{
            spinta = ($spinta | Select-Object -Last 1)
            righe  = (& $adb -s $seriale logcat -d -t 60 -s 'Casa:I')
        }
    } @($script:adb, $script:seriale, $script:configFile, $script:cartellaCasa))[-1]

    Registra $esito.spinta
    $righe = $esito.righe
    $ultima = $null
    foreach ($x in $righe) { if ($x -match 'PC config (.+)$') { $ultima = $Matches[1].Trim() } }
    if ($ultima) {
        Registra "Il tablet adesso ha: $ultima"
    } else {
        Registra 'Mandata, ma Assistente Home non ha confermato: e'' in funzione? (Riavvia Assistente Home nella pagina del tablet)'
    }
    return $true
}

# ------------------------------------------------------------- le pagine -----

. (Join-Path $pc 'pagine\Tablet.ps1')
. (Join-Path $pc 'pagine\Allestimento.ps1')
. (Join-Path $pc 'pagine\Luci.ps1')
. (Join-Path $pc 'pagine\Routine.ps1')
. (Join-Path $pc 'pagine\App.ps1')
. (Join-Path $pc 'pagine\Radio.ps1')
. (Join-Path $pc 'pagine\Spotify.ps1')
. (Join-Path $pc 'pagine\Comandi.ps1')
. (Join-Path $pc 'pagine\Voce.ps1')
. (Join-Path $pc 'pagine\Notizie.ps1')

# ------------------------------------------------------------------ avvio ---

Vai-A 0

<##
 # LA SCHERMATA D'AVVIO, E PERCHE' CE N'E' UNA.
 #
 # QUANTO CI METTE. Fra il doppio clic e la prima pagina utile passano dai
 # cinque agli otto secondi, e non c'e' niente da limare: due li prende il
 # broadcast che chiede a Casa di rifare la vetrina - "am broadcast" torna solo
 # quando il ricevitore ha finito, ed e' quello che lo rende affidabile - uno il
 # dumpsys che dice come sta il tablet, e il resto sono giri di adb. Sono
 # secondi VERI: si aspetta un apparecchio che sta dall'altra parte di un cavo.
 #
 # QUELLO CHE SI PUO' CAMBIARE E' COSA SI VEDE INTANTO. Prima si vedeva la
 # pagina "Il tablet" vuota, con dei trattini al posto dei valori, e nient'altro
 # per sei secondi. Non e' che sembrasse lenta: sembrava ROTTA. Un programma che
 # si apre e non fa niente e' indistinguibile da un programma che si e' piantato
 # nell'aprirsi, e chi guarda dopo tre secondi comincia a cliccare.
 #
 # ADESSO: un elenco di quattro passi che si spuntano uno per volta, con quello
 # che ognuno ha trovato scritto a destra - il seriale del tablet, "2 luci, 5
 # routine, 22 stazioni" - e una barra che si muove. Le tre cose insieme dicono
 # a che punto e', quanto manca, e che il tempo se lo sta prendendo qualcosa di
 # preciso. La stessa attesa, e non da' piu' fastidio.
 #
 # E LA BARRA SI MUOVE DAVVERO. E' il motivo per cui il lavoro sta in un filo a
 # parte (vedi Aspetta): una barra disegnata dal filo che e' fermo dentro adb
 # sarebbe una barra ferma, e una barra ferma dice "e' bloccato" piu' forte di
 # quanto lo direbbe una schermata vuota.
 #
 # LE PAGINE SI CARICANO QUANDO SI APRONO (vedi $script:allApertura). Qui
 # restano le due cose che servono a tutti: com'e' messo il tablet, e la
 # configurazione.
 #>
$PASSI_AVVIO = @(
    'Preparo il motore',
    'Cerco il tablet',
    'Chiedo ad Assistente Home come sta',
    'Prendo la vetrina di Assistente Home'
)

$avvioSchermo = New-Object System.Windows.Forms.Panel
$avvioSchermo.Dock = 'Fill'
$avvioSchermo.BackColor = $cFondo
$contenuto.Controls.Add($avvioSchermo)
$avvioSchermo.BringToFront()

# Un blocco di misura fissa, tenuto al centro: la finestra si puo' ingrandire, e
# quattro righe appiccicate nell'angolo in alto a sinistra di uno schermo largo
# non sono una schermata d'avvio, sono un residuo.
$avvioBlocco = New-Object System.Windows.Forms.Panel
$avvioBlocco.Size = New-Object System.Drawing.Size(560, 246)
$avvioBlocco.BackColor = $cFondo
$avvioSchermo.Controls.Add($avvioBlocco)

$avvioTesta = New-Object Casa.Intestazione
$avvioTesta.Text = 'Gestione Home'
$avvioTesta.Sottotitolo = 'sto aprendo il banco di lavoro'
$avvioTesta.Icona = 'sincronizza'
$avvioTesta.Tinta = $cBlu
$avvioTesta.Location = New-Object System.Drawing.Point(0, 0)
$avvioTesta.Size = New-Object System.Drawing.Size(560, 52)
$avvioBlocco.Controls.Add($avvioTesta)

$avvioBarra = New-Object Casa.Avanzamento
$avvioBarra.Location = New-Object System.Drawing.Point(0, 74)
$avvioBarra.Size = New-Object System.Drawing.Size(560, 6)
$avvioBarra.Tinta = $cBlu
$avvioBlocco.Controls.Add($avvioBarra)

$avvioPassi = @()
for ($i = 0; $i -lt $PASSI_AVVIO.Count; $i++) {
    $p = New-Object Casa.PassoAvvio
    $p.Text = $PASSI_AVVIO[$i]
    $p.Tinta = $cBlu
    $p.Location = New-Object System.Drawing.Point(0, (102 + $i * 34))
    $p.Size = New-Object System.Drawing.Size(560, 30)
    $avvioBlocco.Controls.Add($p)
    $avvioPassi += $p
}

# Al centro, e non nell'angolo. Si chiama anche subito e non solo sul
# ridimensionamento: un pannello che nasce gia' della misura giusta non riceve
# nessun Resize, e senza questa riga la schermata d'avvio restava incollata in
# alto a sinistra - dove sembra un pezzo di pagina rimasto indietro invece che
# una schermata.
function Centra-Avvio {
    $script:avvioBlocco.Left = [Math]::Max(0,
        [int](($script:avvioSchermo.Width - $script:avvioBlocco.Width) / 2))
    $script:avvioBlocco.Top = [Math]::Max(0,
        [int](($script:avvioSchermo.Height - $script:avvioBlocco.Height) / 2) - 40)
}
$avvioSchermo.Add_Resize({ Centra-Avvio })
Centra-Avvio

# Lo stato di un passo, e la barra che ne tiene conto da sola: chi racconta
# l'avvio dice una cosa sola per riga e non deve anche calcolare le frazioni.
function Passo-Avvio([int]$quale, [int]$stato, [string]$dettaglio) {
    if ($quale -lt 0 -or $quale -ge $script:avvioPassi.Count) { return }
    $script:avvioPassi[$quale].Stato = $stato
    if ($dettaglio) { $script:avvioPassi[$quale].Dettaglio = $dettaglio }
    $fatto = $quale + $(if ($stato -ge 2) { 1.0 } else { 0.35 })
    $script:avvioBarra.Quanto = $fatto / $script:avvioPassi.Count
}

<##
 # Tutto quello che all'avvio tocca il tablet, in un colpo solo e lontano da
 # qui.
 #
 # PERCHE' TUTTO INSIEME E NON TRE CHIAMATE. Ogni passaggio nel filo a parte
 # costa un giro; e soprattutto, spezzato in tre, fra un pezzo e l'altro
 # l'interfaccia tornerebbe viva con la configurazione a meta'. Cosi' invece o
 # c'e' tutto o non c'e' niente, e i passi che si spuntano a schermo li racconta
 # $passi, che e' l'unica cosa che il filo scrive mentre lavora.
 #
 # NON VEDE NIENTE DI QUA: ne' $adb, ne' Registra, ne' la configurazione. Gli
 # arriva tutto per argomento, e quello che trova torna in una tabella. E' la
 # condizione per poterlo far girare mentre la finestra e' viva.
 #>
$LAVORO_AVVIO = {
    param($adb, $dispositivo, $comandoStato, $cartellaCasa, $dove, $passi)
    $esito = @{ seriale = $null; stato = @(); vetrina = $null; guaio = '' }
    try {
        . $dispositivo
        $passi.quale = 1
        $esito.seriale = Get-TabletDelProgetto -Adb $adb
        if ($esito.seriale) {
            $passi.quale = 2
            $esito.stato = & $adb -s $esito.seriale shell $comandoStato
            $passi.quale = 3
            & $adb -s $esito.seriale shell 'am broadcast -a dev.casa.CONFIG --es cosa vetrina' | Out-Null
            if (Test-Path $dove) { Remove-Item $dove -Force -ErrorAction SilentlyContinue }
            & $adb -s $esito.seriale pull ($cartellaCasa + '/adesso.json') $dove | Out-Null
            if (Test-Path $dove) { $esito.vetrina = $dove }
        }
    } catch {
        $esito.guaio = $_.Exception.Message
    }
    $passi.quale = 4
    return $esito
}

function Avvia {
    Centra-Avvio
    $script:form.Refresh()

    # 1. IL MOTORE. La cifratura scritta a mano si prova prima di usarla, non
    # dopo: se sbagliasse, senza questa riga si vedrebbe soltanto un "il
    # servizio Tuya ha rifiutato la richiesta" tre schermate piu' in la'. Costa
    # un millisecondo e non tocca il tablet.
    Passo-Avvio 0 1
    $script:avvioBlocco.Refresh()
    $guaio = [Casa.Gcm]::Prova()
    if ($guaio) {
        Registra "ATTENZIONE - $guaio (la pagina Le luci non potra' prendere le chiavi)"
        Passo-Avvio 0 2 'con un guaio'
    } else {
        Passo-Avvio 0 2 'a posto'
    }

    # 2, 3 e 4: il tablet. Tutto in un filo a parte, e qui si guarda soltanto a
    # che punto e' arrivato.
    $passi = [hashtable]::Synchronized(@{ quale = 1 })
    $dove = Join-Path $dirConf 'adesso.json'
    $mentre = {
        $q = [int]$passi.quale
        for ($i = 1; $i -le 3; $i++) {
            if ($i -lt $q) { Passo-Avvio $i 2 }
            elseif ($i -eq $q) { Passo-Avvio $i 1 }
        }
    }.GetNewClosure()

    $esito = @(Aspetta 'Cerco il tablet...' $script:LAVORO_AVVIO `
        @($script:adb, (Join-Path $radice 'tools\dispositivo.ps1'), $script:CHIEDI_STATO,
          $script:cartellaCasa, $dove, $passi) $mentre)[-1]

    if ($esito.guaio) { Registra ("Durante l'avvio: " + $esito.guaio) }

    $script:seriale = $esito.seriale
    if ($script:seriale) {
        $spia.Text = 'collegato'
        $spia.Tinta = $script:cVerde
        Passo-Avvio 1 2 $script:seriale
        Mostra-Stato $script:seriale $esito.stato
        Passo-Avvio 2 2 'letto'
    } else {
        $spia.Text = 'tablet assente'
        $spia.Tinta = $script:cRosso
        Passo-Avvio 1 2 'non collegato'
        Passo-Avvio 2 3 'salto'
        Svuota-Stato
        Registra 'Collega il tablet e premi "Aggiorna lo stato".'
    }

    # LA CONFIGURAZIONE: prima quella del tablet, che e' la verita'; se il
    # tablet non c'e', quella salvata sul PC - cosi' si puo' preparare una
    # routine sul divano e mandarla domani.
    $presa = $false
    if ($esito.vetrina) { $presa = Config-Da-File $esito.vetrina }
    if ($presa) {
        Registra ('Dal tablet: ' + (Conta-Config) + '.')
        Passo-Avvio 3 2 (Conta-Config)
    } elseif (Leggi-Config-Da-Disco) {
        Registra 'Uso la copia in pc\config\casa.json.'
        Passo-Avvio 3 2 'dalla copia sul PC'
    } else {
        $script:config = Config-Vuota
        Passo-Avvio 3 3 'ancora niente'
    }
    Config-Cambiata
    Registra 'Pronto.'

    # Un istante con tutto spuntato prima di sparire: una schermata che si
    # toglie nel momento esatto in cui l'ultimo passo diventa verde e' una
    # schermata che quel verde non lo fa vedere, e il lavoro di raccontare
    # l'attesa si perde nell'ultimo decimo di secondo.
    $script:avvioBarra.Quanto = 1
    $script:avvioBlocco.Refresh()
    Start-Sleep -Milliseconds 280
    $script:avvioSchermo.Visible = $false
    $script:contenuto.Refresh()
}

$form.Add_Shown({
    # PRIMA DI TUTTO: farsi vedere.
    #
    # Il collegamento lancia il programma "senza finestre" per non far
    # lampeggiare la console nera, e Windows quello stato lo passa alla prima
    # finestra vera che il processo apre: senza questa riga il programma parte,
    # gira, non da' nessun errore e non si vede niente. E siccome la console e'
    # nascosta, non c'e' nemmeno un messaggio da leggere. Vedi Casa.Finestra.
    [Casa.Finestra]::Scura($form.Handle)
    [Casa.Finestra]::Mostra($form.Handle)
    [Casa.Finestra]::NascondiLaConsole()

    Registra 'Gestione Home.'
    if ($script:motoreVecchio) {
        Registra ("Non ho potuto ricompilare il motore, uso quello di prima: " + $script:motoreVecchio)
    }
    Avvia
})

$form.Add_FormClosing({
    if ($script:lavoro -and -not $script:lavoro.HasExited) {
        Registra 'Aspetto che finisca il lavoro in corso...'
    }
    $cronometro.Stop()
    # Il filo dei lavori lunghi e' un runspace vero, con un suo thread: se non
    # lo si chiude, il processo di PowerShell resta vivo dopo che la finestra e'
    # sparita. Da fuori e' il difetto peggiore che ci sia - non c'e' piu' niente
    # da vedere e non c'e' piu' niente da chiudere.
    if ($script:filo) {
        try { $script:filo.Close(); $script:filo.Dispose() } catch { }
        $script:filo = $null
    }
})

[void]$form.ShowDialog()

