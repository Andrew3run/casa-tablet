# Compila le app in codice nativo, perche' qui nessuno lo fa per noi.
#
# LA SCOPERTA. Spotify ci metteva venti secondi a diventare usabile, e aprirla
# rallentava tutto il resto. Non era Spotify:
#
#     dumpsys package com.spotify.music | grep compilation_filter
#     -> compilation_filter=interpret-only
#
# E non solo lei - Netflix, Chrome, Play Services e persino Casa erano tutte
# "interpret-only". Il tablet stava eseguendo **bytecode interpretato**, senza
# una riga compilata in nativo. Su un Cortex-A7 a 1,3 GHz e' la differenza fra
# secondi e decine di secondi.
#
# PERCHE'. Da Android 7 l'installazione non compila piu' subito: lascia le app
# in interpret-only e rimanda il lavoro a BackgroundDexOptService, che gira
# quando il dispositivo e' fermo e in carica. Su questo tablet non e' mai
# girato - e adesso che il Play Store non c'e' piu' non ha nemmeno chi lo
# solleciti. Quindi lo si fa a mano, ed e' un lavoro che va **rifatto dopo ogni
# adb install**.
#
# IL PREZZO. dex2oat e' lento e vorace: su Spotify ci ha messo nove minuti
# saturando i quattro core, e nel frattempo l'app stessa e' andata in ANR. Non
# e' un guasto - e' il compilatore che si prende la macchina. Va lanciato
# quando il tablet non serve a nessuno, non mentre qualcuno lo guarda.
#
# Si paga in spazio (l'.odex sta accanto all'APK) e si compra CPU. Su un
# apparecchio sempre attaccato alla corrente, con 11 GB liberi e 1 GB di RAM,
# e' lo scambio giusto in ogni caso.
#
#   .\compila.ps1                    compila l'elenco qui sotto
#   .\compila.ps1 -Verifica          dice solo come stanno, senza compilare
#   .\compila.ps1 -Tutto             compila OGNI app installata (lunghissimo)
#   .\compila.ps1 -Solo dev.casa     compila solo quella

param(
    [switch]$Verifica,
    [switch]$Tutto,
    [switch]$Rifai,      # ricompila anche quelle gia' a "speed"
    [string[]]$Solo
)

$ErrorActionPreference = 'Stop'

$adb = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
. (Join-Path $PSScriptRoot 'dispositivo.ps1')

$serial = Get-TabletDelProgetto -Adb $adb
if (-not $serial) { exit 1 }

function Chiedi {
    param([string]$Comando)
    return ((& $adb -s $serial shell $Comando) -join "`n").Trim()
}

# "c'e' ma non so in che stato" e "non c'e'" sono due cose diverse, e
# confonderle e' costato un giro a vuoto: dopo una compilazione interrotta
# dumpsys scrive "status: invalid[]" senza nessun compilation_filter, la
# funzione rispondeva "(non installata)" e lo script saltava proprio l'app che
# aveva piu' bisogno di essere ricompilata. L'esistenza si chiede a pm, che e'
# l'unico che la sa.
function Esiste {
    param([string]$Pacchetto)
    return [bool](Chiedi "pm list packages $Pacchetto" | Select-String -SimpleMatch "package:$Pacchetto")
}

function Filtro {
    param([string]$Pacchetto)
    if (-not (Esiste $Pacchetto)) { return '(non installata)' }
    $r = Chiedi "dumpsys package $Pacchetto"
    if ($r -match 'compilation_filter=([a-z-]+)') { return $Matches[1] }
    return 'sconosciuto'   # p.es. dopo una compilazione interrotta: da rifare
}

# Quelle che contano, in ordine di quanto si sente la differenza.
#
# gms sta in fondo ed e' la piu' lunga di tutte, ma e' anche quella che gira
# sempre: compilarla si sente su tutto il resto, non su una schermata sola.
$Elenco = @(
    'dev.casa'
    'com.spotify.music'
    'com.netflix.mediaclient'
    'com.android.chrome'
    'com.google.android.gms'
    'com.google.android.googlequicksearchbox'   # il riconoscimento vocale
    'com.google.android.tts'                     # la voce che risponde
)

if ($Solo) { $Elenco = $Solo }
if ($Tutto) {
    $Elenco = (Chiedi 'pm list packages -3') -split "`n" |
        ForEach-Object { ($_ -replace 'package:', '').Trim() } |
        Where-Object { $_ }
}

Write-Host ""
Write-Host "Tablet $serial" -ForegroundColor Cyan
Write-Host ""
Write-Host "Come stanno adesso:" -ForegroundColor Cyan
foreach ($p in $Elenco) {
    $f = Filtro $p
    $colore = if ($f -eq 'speed') { 'Green' } elseif ($f -eq 'interpret-only') { 'Yellow' } else { 'Gray' }
    Write-Host ("  {0,-34} {1}" -f $p, $f) -ForegroundColor $colore
}

if ($Verifica) {
    Write-Host ""
    Write-Host "Solo verifica: non ho compilato niente." -ForegroundColor Cyan
    exit 0
}

Write-Host ""
Write-Host "Compilo. Il tablet sara' lento finche' non ho finito - dex2oat si" -ForegroundColor Yellow
Write-Host "prende tutti e quattro i core, e qualche app puo' andare in ANR." -ForegroundColor Yellow

foreach ($p in $Elenco) {
    $prima = Filtro $p
    if ($prima -eq '(non installata)') {
        Write-Host ("  salto {0}: non c'e'" -f $p) -ForegroundColor DarkGray
        continue
    }
    # Gia' compilata: si salta. Rifarla costa quanto la prima volta - su
    # Spotify nove minuti - e non cambia niente. Con -Rifai la si forza,
    # che serve quando si sospetta un .odex rovinato.
    if ($prima -eq 'speed' -and -not $Rifai) {
        Write-Host ("  {0,-34} gia' speed, salto" -f $p) -ForegroundColor DarkGray
        continue
    }
    # Ferma l'app prima: compilarla mentre gira significa contenderle la RAM,
    # ed e' cosi' che si finisce in ANR.
    & $adb -s $serial shell "am force-stop $p" | Out-Null

    # Play Services e' troppo grosso per "speed" su questa CPU: dex2oat ha un
    # cane da guardia interno e muore dopo 570 secondi, lasciando l'odex
    # invalido (peggio di prima). "speed-profile" compila solo i metodi che il
    # JIT ha gia' visto caldi, chiude in ~9 minuti e l'odex risulta valido.
    $modo = if ($p -eq 'com.google.android.gms') { 'speed-profile' } else { 'speed' }
    $t0 = Get-Date
    Write-Host ("  {0,-34} " -f $p) -NoNewline
    $esito = Chiedi "cmd package compile -m $modo -f $p"
    $secondi = ((Get-Date) - $t0).TotalSeconds

    if ($esito -match 'Success') {
        Write-Host ("{0}  ({1:N0} s)" -f (Filtro $p), $secondi) -ForegroundColor Green
    } else {
        Write-Host ("fallito: {0}" -f $esito) -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Fatto. Da rifare dopo ogni 'adb install': una app appena messa" -ForegroundColor Cyan
Write-Host "torna interpret-only e si riporta dietro la lentezza." -ForegroundColor Cyan
