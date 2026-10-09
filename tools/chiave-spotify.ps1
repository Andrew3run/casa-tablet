# Consegna a Casa la chiave con cui cerca su Spotify.
#
# La ricerca e' l'unica cosa della sezione Musica che non si puo' fare con il
# token della sessione di go-librespot, e non per un permesso mancante: nel
# protocollo di Spotify la ricerca vive dentro il canale Mercury della sessione
# (hm://searchview/...), non sull'API HTTPS - e go-librespot quel canale non lo
# espone. Playlist, brani e preferiti invece sono servizi HTTPS veri, e infatti
# funzionano senza nessuna chiave. Vedi docs/musica.md.
#
# La strada pulita e' un'applicazione registrata a proprio nome:
#
#   1. developer.spotify.com/dashboard -> Create app: nome e descrizione
#      qualsiasi, fra le API "Web API", e come Redirect URI
#      http://127.0.0.1:8888/callback - non verra' contattato mai (il flusso
#      client credentials non redirige nessuno), ma il modulo lo pretende e da
#      inizio 2025 vuole il loopback esplicito con la porta, non "localhost".
#   2. dentro l'app: Settings -> Client ID e "View client secret"
#   3.   .\tools\chiave-spotify.ps1 -Id <client id> -Segreto <client secret>
#
# Le due chiavi viaggiano in un broadcast e Casa le scrive nei suoi file
# privati: non restano su /sdcard e non servono permessi di memoria.
#
#   .\tools\chiave-spotify.ps1 -Verifica    dice solo se la chiave c'e'

param(
    [string]$Id,
    [string]$Segreto,
    [switch]$Verifica
)

$ErrorActionPreference = 'Stop'
$adb = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
. (Join-Path $PSScriptRoot 'dispositivo.ps1')

$serial = Get-TabletDelProgetto -Adb $adb
if (-not $serial) { exit 1 }

if ($Verifica) {
    # Il file sta nei dati privati dell'app e da adb non si legge: si chiede a
    # Casa, che lo dice nel log all'avvio della ricerca.
    Write-Host "Apri la sezione Musica e tocca 'cerca su Spotify'." -ForegroundColor Yellow
    Write-Host "Se la chiave manca, la barra lo scrive al posto dei risultati."
    exit 0
}

if (-not $Id -or -not $Segreto) {
    Write-Warning "Servono -Id e -Segreto. In cima a questo file c'e' dove prenderli."
    exit 1
}

# Gli apici singoli attorno ai valori devono sopravvivere fino alla shell del
# tablet: con le doppie, am spezza la riga e prende un pezzo per un pacchetto.
$comando = "am broadcast -a dev.casa.CHIAVE --es id '$Id' --es segreto '$Segreto'"
$esito = (& $adb -s $serial shell $comando) -join "`n"

if ($esito -match 'result=0|Broadcast completed') {
    Write-Host "Chiave consegnata. Assistente Home lo conferma a schermo." -ForegroundColor Green
} else {
    Write-Warning "Il broadcast non e' arrivato:`n$esito"
    Write-Host "Assistente Home deve essere in primo piano: e' l'Activity che lo riceve." -ForegroundColor Yellow
    exit 1
}
