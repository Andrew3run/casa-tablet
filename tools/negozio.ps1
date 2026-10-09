# Il Play Store: c'e', ma sta zitto.
#
# LA DECISIONE, in due righe. Il negozio serve - senza, le app installate
# invecchiano e nessuno le aggiorna, ed e' esattamente quello che e' successo a
# Netflix, tornato all'APK del 2019 e rifiutato dai suoi stessi server. Ma da
# acceso costa **60 MB** su un gigabyte, e non li spende quando qualcuno lo usa:
# li spende all'accensione del tablet, da solo, per sempre.
#
#     com.android.vending              39 MB
#     com.android.vending:background   21 MB
#
# Misurato su questo tablet, subito dopo un riavvio e senza che nessuno avesse
# aperto niente: MemAvailable 383 MB con il negozio in piedi, 437 MB senza.
#
# Quindi resta installato e sta spento, e si accende per il tempo di un
# aggiornamento. E' l'unico modo di avere tutt'e due le cose: un tablet che si
# puo' aggiornare, e un tablet che non paga un negozio aperto sedici ore al
# giorno per due aggiornamenti l'anno.
#
# PERCHE' disable-user E NON uninstall. Toglierlo davvero (pm uninstall -k
# --user 0) funziona, ma poi rimetterlo e' la storia lunga raccontata in
# alleggerisci.ps1 - sessione multi-APK, --user 0, l'aggiornamento da ripescare
# in /data/app - e per due giorni ci ha fatto credere di averlo perso.
# disable-user lo lascia dov'e': zero processi, zero RAM, e si riaccende con
# una riga.
#
#   .\negozio.ps1            dice come sta
#   .\negozio.ps1 -Apri      lo accende e lo apre sul tablet
#   .\negozio.ps1 -Chiudi    lo ferma e lo rispegne

param(
    [switch]$Apri,
    [switch]$Chiudi
)

$ErrorActionPreference = 'Stop'

$adb = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
. (Join-Path $PSScriptRoot 'dispositivo.ps1')

$serial = Get-TabletDelProgetto -Adb $adb
if (-not $serial) { exit 1 }

$PACCHETTO = 'com.android.vending'

function Chiedi {
    param([string]$Comando)
    return ((& $adb -s $serial shell $Comando) -join "`n").Trim()
}

function MemoriaLibera {
    $riga = (Chiedi 'cat /proc/meminfo') -split "`n" | Where-Object { $_ -match 'MemAvailable' }
    return [int](($riga -replace '\D', '') / 1024)
}

function Stato {
    # "pm list packages" da solo NON elenca i disabilitati: senza -d si
    # concluderebbe che il negozio non c'e' piu', ed e' il modo piu' rapido di
    # rimettersi a reinstallarlo per niente.
    $installato = Chiedi "pm list packages -u $PACCHETTO"
    if (-not $installato) { return 'assente' }
    $spento = Chiedi "pm list packages -d $PACCHETTO"
    if ($spento) { return 'spento' }
    return 'acceso'
}

$stato = Stato
$inMemoria = Chiedi "ps | grep $PACCHETTO"

if (-not ($Apri -or $Chiudi)) {
    Write-Host ""
    Write-Host "Tablet $serial" -ForegroundColor Cyan
    Write-Host ("Play Store   : {0}" -f $stato)
    Write-Host ("in memoria   : {0}" -f $(if ($inMemoria) { 'SI' } else { 'no' }))
    Write-Host ("MemAvailable : {0:N0} MB" -f (MemoriaLibera))
    if ($stato -eq 'assente') {
        Write-Host ""
        Write-Host "Non e' installato. Si rimette con .\alleggerisci.ps1 -Rimetti" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "  .\negozio.ps1 -Apri     per aggiornare qualcosa" -ForegroundColor DarkGray
    Write-Host "  .\negozio.ps1 -Chiudi   quando hai finito" -ForegroundColor DarkGray
    exit 0
}

if ($stato -eq 'assente') {
    Write-Warning "Il Play Store non e' installato. Rimettilo con .\alleggerisci.ps1 -Rimetti"
    exit 1
}

if ($Chiudi) {
    Write-Host "Fermo e spengo il negozio..." -ForegroundColor Cyan
    # force-stop PRIMA di disable-user: disabilitare un pacchetto non ne uccide
    # i processi gia' vivi, e quelli restano in memoria fino al prossimo
    # riavvio - cioe' i 60 MB che si stavano cercando di liberare.
    Chiedi "am force-stop $PACCHETTO" | Out-Null
    Chiedi "pm disable-user --user 0 $PACCHETTO" | Out-Null
    # E si rimette lo schermo come stava: -Apri lo inchioda acceso per
    # sopravvivere a un'installazione lunga, e lasciarlo cosi' vorrebbe dire un
    # pannello acceso per sempre a spese di nessuno che lo guarda.
    Chiedi 'settings put global stay_on_while_plugged_in 0' | Out-Null
    Start-Sleep -Seconds 3
    Write-Host ("Fatto. Play Store: {0}. MemAvailable: {1:N0} MB" -f (Stato), (MemoriaLibera)) -ForegroundColor Green
    exit 0
}

# --- -Apri ------------------------------------------------------------------

Write-Host "Accendo il negozio..." -ForegroundColor Cyan
Chiedi "pm enable --user 0 $PACCHETTO" | Out-Null

# Lo schermo deve restare acceso: il Play Store non tiene FLAG_KEEP_SCREEN_ON
# come fa Casa, e durante un'installazione lunga il tablet si addormenta - i
# tocchi da adb non arrivano piu' e sembra bloccato.
Chiedi 'settings put global stay_on_while_plugged_in 3' | Out-Null

Chiedi "monkey -p $PACCHETTO -c android.intent.category.LAUNCHER 1" | Out-Null

Write-Host ""
Write-Host ("Play Store: {0}. Aprilo sul tablet e aggiorna quello che serve." -f (Stato)) -ForegroundColor Green
Write-Host ""
Write-Host "Quando hai finito:" -ForegroundColor Cyan
Write-Host "  .\compila.ps1            un'app appena aggiornata torna interpretata" -ForegroundColor DarkGray
Write-Host "  .\negozio.ps1 -Chiudi    rispegne il negozio e rimette lo schermo" -ForegroundColor DarkGray
