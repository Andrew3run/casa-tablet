# Mette "Gestione Home" sul desktop.
#
#   .\collegamento.ps1            crea (o rifa') il collegamento e l'icona
#   .\collegamento.ps1 -Togli     lo toglie
#
# L'icona si disegna qui invece di stare come file nel progetto: sono poche
# righe contro un binario da tenere in casa, e cosi' si cambia scrivendo invece
# che aprendo un programma di disegno.
#
# IL FORMATO E' QUELLO VECCHIO, E NON E' NOSTALGIA. Dentro un .ico ci puo'
# stare un PNG - lo ammettono Windows da Vista in poi ed e' molto piu' corto -
# ma GDI+ no: System.Drawing.Icon su un .ico fatto di PNG solleva
# "L'intervallo richiesto supera la fine della matrice". Explorer mostrerebbe
# il collegamento benissimo, e a rompersi sarebbe la riga che mette l'icona
# sulla finestra del programma. Quindi mappe di bit vere, a cinque misure:
# centomila byte contro cinquemila, e funziona in tutti e due i posti.

param([switch]$Togli)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$radice   = Split-Path $PSScriptRoot -Parent
$pc       = Join-Path $radice 'pc'
$script   = Join-Path $pc 'GestioneHome.ps1'
$icona    = Join-Path $pc 'GestioneHome.ico'
$desktop  = [Environment]::GetFolderPath('Desktop')
$scorciatoia = Join-Path $desktop 'Gestione Home.lnk'

if ($Togli) {
    $tolto = $false
    if (Test-Path $scorciatoia) { Remove-Item $scorciatoia; Write-Host "Tolto: $scorciatoia"; $tolto = $true }
    # Anche l'avviatore: e' un file che esiste solo per il collegamento, e
    # lasciarlo li' vorrebbe dire un .vbs orfano dentro pc\ che fra un anno
    # nessuno sa piu' cosa sia.
    $avviatore = Join-Path $pc 'avvia.vbs'
    if (Test-Path $avviatore) { Remove-Item $avviatore; Write-Host "Tolto: $avviatore"; $tolto = $true }
    if (-not $tolto) { Write-Host "Non c'era niente da togliere." }
    return
}

if (-not (Test-Path $script)) { throw "Manca $script" }

# ---------------------------------------------------------------- l'icona ---

function Disegna-Quadro([int]$lato) {
    $bmp = New-Object System.Drawing.Bitmap -ArgumentList $lato, $lato, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.Clear([System.Drawing.Color]::Transparent)

    $u = $lato / 100.0    # tutto in centesimi del lato, cosi' vale a ogni misura

    # Il fondo: il quadrato con gli angoli tondi della tavolozza di Casa.
    $fondo = [System.Drawing.ColorTranslator]::FromHtml('#141C27')
    $r = 22 * $u
    $percorso = New-Object System.Drawing.Drawing2D.GraphicsPath
    $percorso.AddArc(0, 0, $r * 2, $r * 2, 180, 90)
    $percorso.AddArc($lato - $r * 2, 0, $r * 2, $r * 2, 270, 90)
    $percorso.AddArc($lato - $r * 2, $lato - $r * 2, $r * 2, $r * 2, 0, 90)
    $percorso.AddArc(0, $lato - $r * 2, $r * 2, $r * 2, 90, 90)
    $percorso.CloseFigure()
    $pennelloFondo = New-Object System.Drawing.SolidBrush -ArgumentList $fondo
    $g.FillPath($pennelloFondo, $percorso)

    # La casa: tetto e corpo, del blu della sezione Home.
    $blu = [System.Drawing.ColorTranslator]::FromHtml('#3D7EFF')
    $spessore = [Math]::Max(1.0, 7 * $u)
    $penna = New-Object System.Drawing.Pen -ArgumentList $blu, $spessore
    $penna.StartCap = 'Round'; $penna.EndCap = 'Round'; $penna.LineJoin = 'Round'

    $tetto = @(
        (New-Object System.Drawing.PointF -ArgumentList ((20 * $u), (48 * $u))),
        (New-Object System.Drawing.PointF -ArgumentList ((50 * $u), (24 * $u))),
        (New-Object System.Drawing.PointF -ArgumentList ((80 * $u), (48 * $u)))
    )
    $g.DrawLines($penna, [System.Drawing.PointF[]]$tetto)

    $corpo = @(
        (New-Object System.Drawing.PointF -ArgumentList ((29 * $u), (46 * $u))),
        (New-Object System.Drawing.PointF -ArgumentList ((29 * $u), (76 * $u))),
        (New-Object System.Drawing.PointF -ArgumentList ((71 * $u), (76 * $u))),
        (New-Object System.Drawing.PointF -ArgumentList ((71 * $u), (46 * $u)))
    )
    $g.DrawLines($penna, [System.Drawing.PointF[]]$corpo)

    # Le tre onde della voce dentro la casa: e' quello che questo programma fa
    # davvero, e da lontano distingue l'icona da una casetta qualunque.
    # Sotto i trentadue pixel si saltano: tre trattini in nove pixel non sono
    # tre trattini, sono una macchia, e la casa si legge meglio senza.
    if ($lato -ge 32) {
        $verde = [System.Drawing.ColorTranslator]::FromHtml('#5FD0A0')
        $spessoreOnda = [Math]::Max(1.0, 5.5 * $u)
        $pennaOnda = New-Object System.Drawing.Pen -ArgumentList $verde, $spessoreOnda
        $pennaOnda.StartCap = 'Round'; $pennaOnda.EndCap = 'Round'
        $g.DrawLine($pennaOnda, (41 * $u), (60 * $u), (41 * $u), (66 * $u))
        $g.DrawLine($pennaOnda, (50 * $u), (54 * $u), (50 * $u), (72 * $u))
        $g.DrawLine($pennaOnda, (59 * $u), (58 * $u), (59 * $u), (68 * $u))
    }

    $g.Dispose()
    return $bmp
}

<##
 # Una immagine dentro un .ico, nel formato classico: l'intestazione DIB, i
 # pixel, e la maschera.
 #
 # Tre cose che si sbagliano sempre, e ognuna da' un risultato diverso:
 #   - l'altezza nell'intestazione va scritta DOPPIA, perche' conta i pixel
 #     piu' la maschera che sta sotto;
 #   - le righe stanno dal basso verso l'alto, come in ogni BMP;
 #   - la maschera c'e' anche quando non serve - qui e' tutta a zero, cioe'
 #     "tieni tutto" - e le sue righe si allineano a quattro byte. Senza,
 #     Windows legge i pixel spostati e l'icona esce a strisce.
 #>
function Componi-Immagine([System.Drawing.Bitmap]$bmp) {
    $w = $bmp.Width; $h = $bmp.Height
    $dati = $bmp.LockBits(
        (New-Object System.Drawing.Rectangle -ArgumentList 0, 0, $w, $h),
        [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $pixel = New-Object 'System.Byte[]' ($dati.Stride * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($dati.Scan0, $pixel, 0, $pixel.Length)
    $bmp.UnlockBits($dati)

    $rigaMaschera = [int](([Math]::Floor(($w + 31) / 32)) * 4)
    $flusso = New-Object System.IO.MemoryStream
    $w2 = New-Object System.IO.BinaryWriter -ArgumentList $flusso

    $w2.Write([UInt32]40)              # BITMAPINFOHEADER
    $w2.Write([Int32]$w)
    $w2.Write([Int32]($h * 2))         # pixel + maschera
    $w2.Write([UInt16]1)               # piani
    $w2.Write([UInt16]32)              # bit per pixel
    $w2.Write([UInt32]0)               # nessuna compressione
    $w2.Write([UInt32]($w * $h * 4 + $rigaMaschera * $h))
    $w2.Write([Int32]0); $w2.Write([Int32]0)
    $w2.Write([UInt32]0); $w2.Write([UInt32]0)

    for ($y = $h - 1; $y -ge 0; $y--) {
        $w2.Write($pixel, $y * $dati.Stride, $w * 4)
    }
    $vuota = New-Object 'System.Byte[]' $rigaMaschera
    for ($y = 0; $y -lt $h; $y++) { $w2.Write($vuota, 0, $rigaMaschera) }

    $w2.Flush()
    $fuori = $flusso.ToArray()
    $w2.Close()
    return ,$fuori
}

function Disegna-Icona([string]$destinazione, [int[]]$misure) {
    $immagini = @()
    foreach ($lato in $misure) {
        $bmp = Disegna-Quadro $lato
        $immagini += ,(Componi-Immagine $bmp)
        $bmp.Dispose()
    }

    $flusso = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter -ArgumentList $flusso
    $w.Write([UInt16]0)                        # riservato
    $w.Write([UInt16]1)                        # 1 = icona
    $w.Write([UInt16]$misure.Count)

    # Le immagini cominciano dopo l'indice: sei byte piu' sedici per ognuna.
    $scorrimento = 6 + 16 * $misure.Count
    for ($i = 0; $i -lt $misure.Count; $i++) {
        $lato = $misure[$i]
        # Duecentocinquantasei si scrive zero: nel formato la misura e' un
        # byte solo, e duecentocinquantasei non ci sta.
        $misura = 0
        if ($lato -lt 256) { $misura = $lato }
        $w.Write([Byte]$misura); $w.Write([Byte]$misura)
        $w.Write([Byte]0); $w.Write([Byte]0)   # tavolozza, riservato
        $w.Write([UInt16]1); $w.Write([UInt16]32)
        $w.Write([UInt32]$immagini[$i].Length)
        $w.Write([UInt32]$scorrimento)
        $scorrimento += $immagini[$i].Length
    }
    foreach ($im in $immagini) { $w.Write($im, 0, $im.Length) }
    $w.Flush()
    [System.IO.File]::WriteAllBytes($destinazione, $flusso.ToArray())
    $w.Close()
}

# Fino a 128 e non a 256: la mappa di bit non e' compressa, e la sola misura
# grande peserebbe 262 KB su 361 di file. Windows ingrandisce la 128 per la
# vista "icone molto grandi", che per un collegamento sul desktop non si
# usa mai.
Disegna-Icona $icona @(16, 32, 48, 64, 128)
Write-Host "Icona: $icona ($([math]::Round((Get-Item $icona).Length/1KB)) KB, cinque misure)"

# ----------------------------------------------------------- il collegamento -

# IL COLLEGAMENTO NON CHIAMA POWERSHELL, CHIAMA WSCRIPT.
#
# Prima ci andava dritto, con -WindowStyle Hidden. Non basta, e il motivo e' che
# quel "Hidden" arriva TARDI: Windows apre la console perche' powershell.exe e'
# un programma da console, poi PowerShell parte, legge l'opzione e la nasconde.
# Fra le due cose passano dei decimi di secondo, e in quei decimi la finestra
# nera si vede. Chi apre il programma vede lampeggiare un terminale.
#
# wscript.exe invece non e' un programma da console: una console non la crea
# affatto, e lancia PowerShell chiedendo esplicitamente "nessuna finestra".
# Niente da nascondere, quindi niente da vedere.
#
# Le righe del .vbs si scrivono qui invece di stare come file nel progetto, per
# la stessa ragione dell'icona: cosi' seguono la cartella se il progetto si
# sposta, e si cambiano scrivendo.
#
# -NoProfile perche' il profilo di chi usa il PC non ha voce in capitolo su un
# programma di servizio, e caricarlo costa un secondo all'avvio.
$avviatore = Join-Path $pc 'avvia.vbs'

# La riga si compone in una variabile e poi si mette nell'array. Scriverla
# dentro l'array come somma di pezzi su due righe sembrava piu' corto, e usciva
# spezzata in quattro righe dentro il file: fra la virgola dell'array e il piu'
# della concatenazione decide la virgola, e il .vbs cosi' non parte.
#
# Il percorso si passa con Chr(34) invece che con le virgolette raddoppiate:
# dentro una stringa VBScript le virgolette si raddoppiano, dentro una stringa
# PowerShell pure, e due livelli di raddoppio sulla stessa riga sono il modo
# piu' rapido per scrivere qualcosa che nessuno dei due legge come si voleva.
$riga = 'guscio.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -File "'
$riga += ' & Chr(34) & "' + $script + '" & Chr(34), 0, False'

$vbs = @(
    "' Apre Gestione Home senza far comparire nessuna finestra nera.",
    "'",
    "' Lo scrive tools\collegamento.ps1: non si modifica a mano, si rilancia quello.",
    "' Il terzo argomento di Run e' lo stile della finestra, e lo zero vuol dire",
    "' ""nessuna""; il quarto e' ""non aspettare che finisca"", o wscript",
    "' resterebbe in memoria per tutto il tempo in cui il programma e' aperto.",
    'Set guscio = CreateObject("WScript.Shell")',
    $riga
)
Set-Content -Path $avviatore -Value $vbs -Encoding ASCII
Write-Host "Avviatore: $avviatore (perche' la console non compaia affatto)"

$shell = New-Object -ComObject WScript.Shell
$c = $shell.CreateShortcut($scorciatoia)
$c.TargetPath = (Join-Path $env:SystemRoot 'System32\wscript.exe')
$c.Arguments = '"' + $avviatore + '"'
$c.WorkingDirectory = $pc
$c.IconLocation = $icona + ',0'
$c.Description = 'Il banco di lavoro del tablet di casa'
$c.WindowStyle = 1
$c.Save()

Write-Host "Collegamento: $scorciatoia" -ForegroundColor Green
Write-Host "Per toglierlo:  .\collegamento.ps1 -Togli"
