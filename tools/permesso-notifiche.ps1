# Concede a Casa l'ascolto delle notifiche.
#
# Non serve a leggere le notifiche di nessuno: serve a poter chiamare
# MediaSessionManager.getActiveSessions(), che pretende che il chiamante sia un
# ascoltatore abilitato e usa il nome del servizio come prova d'identita'.
# Senza, Casa non sa cosa sta suonando su Spotify: titolo, artista e copertina
# non arrivano.
#
# "cmd notification allow_listener" qui NON esiste: e' arrivato con API 26 e
# questo tablet e' fermo alla 24. Su Nougat la via e' Settings.Secure, che dalla
# shell si scrive perche' ha WRITE_SECURE_SETTINGS.
#
#   .\permesso-notifiche.ps1          concede
#   .\permesso-notifiche.ps1 -Togli   revoca (serve a provare il ripiego)

param(
    [switch]$Togli
)

$ErrorActionPreference = 'Stop'

$adb = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
. (Join-Path $PSScriptRoot 'dispositivo.ps1')

$serial = Get-TabletDelProgetto -Adb $adb
if (-not $serial) { exit 1 }

$NOSTRO = 'dev.casa/dev.casa.AscoltoNotifiche'
$CHIAVE = 'enabled_notification_listeners'

function Chiedi {
    param([string]$Comando)
    return ((& $adb -s $serial shell $Comando) -join "`n").Trim()
}

# LEGGERE PRIMA DI SCRIVERE non e' facoltativo: "settings put" sostituisce
# l'intera lista, e scriverci dentro solo noi disabiliterebbe in silenzio ogni
# altro ascoltatore gia' abilitato.
$prima = Chiedi "settings get secure $CHIAVE"
if ($prima -eq 'null') { $prima = '' }

Write-Host ""
Write-Host "Ascoltatori adesso:" -ForegroundColor Cyan
if ($prima) {
    ($prima -split ':') | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "  (nessuno)"
}

$voci = @()
if ($prima) { $voci = $prima -split ':' | Where-Object { $_ } }
$ceGia = $voci -contains $NOSTRO

if ($Togli) {
    if (-not $ceGia) {
        Write-Host ""
        Write-Host "Assistente Home non era abilitata: non c'e' niente da togliere." -ForegroundColor Yellow
        exit 0
    }
    $nuove = $voci | Where-Object { $_ -ne $NOSTRO }
} else {
    if ($ceGia) {
        Write-Host ""
        Write-Host "Assistente Home e' gia' abilitata." -ForegroundColor Green
        exit 0
    }
    $nuove = $voci + $NOSTRO
}

$valore = ($nuove -join ':')
if ($valore) {
    & $adb -s $serial shell "settings put secure $CHIAVE '$valore'" | Out-Null
} else {
    & $adb -s $serial shell "settings delete secure $CHIAVE" | Out-Null
}

# Il servizio si lega all'avvio dell'ascoltatore: senza riavviare il processo,
# Casa resta con la vecchia risposta in mano e continua a dire di non avere il
# permesso. force-stop e' piu' rapido di un riavvio del tablet.
& $adb -s $serial shell 'am force-stop dev.casa' | Out-Null

$dopo = Chiedi "settings get secure $CHIAVE"
Write-Host ""
Write-Host "Adesso:" -ForegroundColor Cyan
if ($dopo -and $dopo -ne 'null') {
    ($dopo -split ':') | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "  (nessuno)"
}

if ($Togli) {
    Write-Host ""
    Write-Host "Tolto. Assistente Home deve continuare a funzionare: i comandi passano da" -ForegroundColor Cyan
    Write-Host "dispatchMediaKeyEvent, che non chiede permessi. Sparisce solo il titolo." -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "Fatto. Riapri Assistente Home e metti su qualcosa: deve comparire cosa suona." -ForegroundColor Green
}
