# Sistema il tablet prima che ci giri Casa.
#
# Non e' pulizia estetica: DuraSpeed di MediaTek ammazza i processi in secondo
# piano, cioe' zittisce la radio appena si apre un'altra app e puo' far saltare
# le sveglie. Finche' c'e' lui, meta' di Casa non funziona e non si capisce
# perche'.
#
# Quasi tutto quello che fa e' reversibile, ma NON come dice la ricetta che si
# trova in giro: "pm uninstall -k --user 0" toglie l'app all'utente e lascia
# l'APK sulla partizione di sistema, pero' su API 24 **install-existing non
# esiste** - ne' "pm" ne' "cmd package". E' arrivato con Android 8/9. Qui si
# torna indietro reinstallando l'APK da /system:
#
#     pm install -r --user 0 /system/priv-app/Qualcosa/Qualcosa.apk
#
# E un'app di sistema che era stata AGGIORNATA puo' non tornare affatto: il
# sistema ricorda la versione nuova, l'uninstall ne ha cancellato l'APK da
# /data/app, e quello vecchio di /system viene rifiutato con
# INSTALL_FAILED_VERSION_DOWNGRADE. E' successo al Play Store.
#
# Nessun root e nessun rischio di brick, ma prima di togliere un'app aggiornata
# vale la pena sapere che potrebbe non tornare. Vedi toolslleggerisci.ps1.
#
#   .\sistema.ps1                sistema
#   .\sistema.ps1 -Verifica      dice solo come sta, senza toccare niente
#   .\sistema.ps1 -ConTelefonia  tiene dialer, SMS e SIM anche con lo slot vuoto

param(
    [switch]$Verifica,
    [switch]$ConTelefonia
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

function MemoriaLibera {
    $riga = (Chiedi 'cat /proc/meminfo') -split "`n" | Where-Object { $_ -match 'MemAvailable' }
    return [int](($riga -replace '\D', '') / 1024)
}

# ---- chi va via, diviso per motivo ---------------------------------------

# Il primo blocco e' l'unico che conta davvero per il funzionamento di Casa.
$Trappole = @(
    'com.mediatek.duraspeed'        # ammazza i processi in secondo piano
    'com.adups.fota'                # FOTA di Shanghai ADUPS, caso Kryptowire 2016
    'com.adups.fota.sysoper'
    'com.baidu.map.location'        # geolocalizzazione Baidu, come app di sistema
    'com.android.santoservice'      # vedi sotto
)

# com.android.santoservice sta in /system/app/AutoGenIMEI e si chiama cosi' per
# non farsi cercare. Genera l'IMEI, e per farlo il ROM gli ha concesso di serie
# READ_SMS, SEND_SMS, RECEIVE_SMS, READ_CALL_LOG, WRITE_CALL_LOG e
# WRITE_CONTACTS, tutti SYSTEM_FIXED. Su un tablet senza SIM non gli serve
# niente di tutto questo, e a un apparecchio acceso in casa tutto il giorno
# nemmeno.

# Roba di chi aveva il tablet prima. iWawa risulta gia' tolto per l'utente 0,
# ma il comando resta: se torna, torna via.
$VecchioPadrone = @(
    'com.sencatech.iwawa.iwawahome'
    'com.sencatech.spinguess'
    'com.sencatech.learninganimals'
    'com.sencatech.littlepianist'
)

$Fabbrica = @(
    'com.mediatek.engineermode',     'com.mediatek.factorymode'
    'com.mediatek.mtklogger',        'com.mediatek.lbs.em2.ui'
    'com.mediatek.mdmlsample',       'com.mediatek.bluetooth.dtt'
    'com.mediatek.miravision.ui',    'com.android.egg'
    'com.example',                   'com.mediatek'
)

# Quello che Casa rifa' da se', o che non serve a un apparecchio da muro.
$Inutili = @(
    'com.android.browser',           'com.google.android.gm'
    'com.android.calculator2',       'com.android.calendar'
    'com.android.gallery3d',         'com.android.music'
    'com.android.musicfx',           'com.android.soundrecorder'
    'com.mediatek.filemanager',      'com.mediatek.calendarimporter'
    'com.android.printspooler',      'com.android.dreams.basic'
    'com.android.providers.partnerbookmarks'
    'com.android.bookmarkprovider'
    'com.google.android.printservice.recommendation'
    'com.google.android.syncadapters.contacts'
    'com.google.android.syncadapters.calendar'
    'com.android.contacts',          'com.mediatek.camera'
    'com.android.documentsui',       'com.android.deskclock'
)

# I due che scelgono lo sfondo. Vanno via DOPO che Casa ha messo il suo:
# togliere chi fornisce lo sfondo attuale farebbe cadere il sistema sul
# default di fabbrica, non su niente.
$SceltaSfondo = @(
    'com.android.wallpaper.livepicker'
    'com.android.wallpapercropper'
)

# com.android.phone e com.android.providers.telephony NON sono qui: non sono
# l'app telefono, sono pezzi del framework, e senza il sistema traballa.
$Telefonia = @(
    'com.android.dialer',            'com.android.mms'
    'com.android.stk',               'com.android.emergency'
    'com.android.calllogbackup',     'com.android.carrierconfig'
    'com.android.providers.blockednumber'
    'com.mediatek.omacp',            'com.mediatek.simprocessor'
    'com.mtk.telephony'
    'org.simalliance.openmobileapi.service'
    'org.simalliance.openmobileapi.uicc1terminal'
    'org.simalliance.openmobileapi.uicc2terminal'
    'org.simalliance.openmobileapi.eseterminal'
)

# Rete di sicurezza vera: se un giorno uno di questi finisse in una delle liste
# qui sopra, lo script si ferma invece di rompere il tablet.
$MaiToccare = @(
    'android',                       'com.android.systemui'
    'com.android.settings',          'com.android.shell'
    'com.android.phone',             'com.android.launcher3'
    'com.google.android.gms',        'com.android.vending'
    'com.google.android.gsf',        'com.google.android.googlequicksearchbox'
    'com.google.android.tts',        'com.android.chrome'
    'com.android.inputmethod.latin', 'com.android.captiveportallogin'
    'com.android.certinstaller',     'com.android.keychain'
    'com.mediatek.connectivity',     'com.mediatek.thermalmanager'
    'com.android.fmradio',           'com.netflix.mediaclient'
    'dev.casa'
)

# ---- come sta adesso ------------------------------------------------------

Write-Host ""
Write-Host "Tablet $serial" -ForegroundColor Cyan

$simState = Chiedi 'getprop gsm.sim.state'
$memPrima = MemoriaLibera
$senzaSim = $simState -match 'ABSENT'

Write-Host ("SIM          : {0}" -f $(if ($senzaSim) { 'slot vuoto' } else { $simState }))
Write-Host ("MemAvailable : {0:N0} MB" -f $memPrima)

$duraspeed = Chiedi 'pm list packages com.mediatek.duraspeed'
if ($duraspeed) {
    Write-Host "DuraSpeed    : ANCORA PRESENTE (zittisce la radio in secondo piano)" -ForegroundColor Yellow
} else {
    Write-Host "DuraSpeed    : via" -ForegroundColor Green
}

$listener = Chiedi 'settings get secure enabled_notification_listeners'
if ($listener -match 'dev\.casa') {
    Write-Host "Notifiche    : Assistente Home abilitata" -ForegroundColor Green
} else {
    Write-Host "Notifiche    : Assistente Home non abilitata - senza, non sa cosa suona" -ForegroundColor Yellow
    Write-Host "               si concede con .\permesso-notifiche.ps1" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Sfondo di sistema:" -ForegroundColor Cyan
(Chiedi 'dumpsys wallpaper') -split "`n" |
    Where-Object { $_ -match 'mWallpaperComponent|mWidth|wallpaper state' } |
    ForEach-Object { Write-Host ("  " + $_.Trim()) }

# --- il ciclo di Impostazioni ----------------------------------------------
#
# com.mediatek.settings.RestoreRotationReceiver, dentro Impostazioni,
# rispedisce a se stesso BOOT_COMPLETED senza condizione: a ogni avvio ne
# nasce un ciclo che tiene Impostazioni al 40% di CPU e system_server al 180%
# finche' il tablet resta acceso. E' il bug che rendeva lento tutto, ed e' di
# fabbrica (docs/ciclo-impostazioni.md).
#
# La cura vera sta in Casa: da device owner nasconde Impostazioni prima che
# il sistema mandi BOOT_COMPLETED. Qui si vede come stanno le cose, e se il
# ciclo e' in corso lo si spezza dal PC: la shell non puo' spegnere il singolo
# receiver (Android 7 le lascia cambiare solo pacchetti interi), ma puo'
# disabilitare Impostazioni per l'utente e riabilitarla subito: il broadcast
# in coda viene buttato e la catena si rompe fino al prossimo avvio.
Write-Host ""
$nascoste = (Chiedi 'dumpsys package com.android.settings') -match 'hidden=true'
$tempesta = [int](Chiedi 'dumpsys activity broadcasts | grep -c "BOOT_COMPLETED.*cmp=com.android.settings"')
if ($nascoste) {
    Write-Host "Impostazioni : nascoste da Assistente Home, il ciclo del ROM non puo' partire" -ForegroundColor Green
} elseif ($tempesta -gt 5) {
    Write-Host "Impostazioni : IN CICLO ($tempesta BOOT_COMPLETED rispediti a se stessa)" -ForegroundColor Yellow
    Write-Host "               Assistente Home non e' device owner o non e' partita: senza -Verifica lo spezzo" -ForegroundColor DarkGray
} else {
    Write-Host "Impostazioni : visibili, nessun ciclo in corso adesso" -ForegroundColor Green
}

if ($Verifica) {
    Write-Host ""
    Write-Host "Solo verifica: non ho toccato niente." -ForegroundColor Cyan
    exit 0
}

if ($tempesta -gt 5 -and -not $nascoste) {
    Write-Host ""
    Write-Host "Spezzo il ciclo di Impostazioni..." -ForegroundColor Cyan
    & $adb -s $serial shell 'pm disable-user --user 0 com.android.settings' | Out-Null
    Start-Sleep -Seconds 3
    & $adb -s $serial shell 'pm enable com.android.settings' | Out-Null
    Start-Sleep -Seconds 3
    $ancora = Chiedi 'ps | grep -c com.android.settings$'
    if ([int]$ancora -eq 0) {
        Write-Host "  spezzato: Impostazioni riabilitata e ferma. Torna al prossimo avvio se Assistente Home non la nasconde." -ForegroundColor Green
    } else {
        Write-Host "  Impostazioni gira ancora: controlla con 'adb logcat | grep RestoreRotation'" -ForegroundColor Yellow
    }
}

# ---- si comincia ----------------------------------------------------------

$daTogliere = @()
$daTogliere += $Trappole
$daTogliere += $VecchioPadrone
$daTogliere += $Fabbrica
$daTogliere += $Inutili

Write-Host ""
if ($ConTelefonia) {
    Write-Host "Telefonia tenuta su richiesta." -ForegroundColor Yellow
} elseif ($senzaSim) {
    Write-Host "Slot SIM vuoto: tolgo anche la telefonia." -ForegroundColor Cyan
    Write-Host "  se un giorno ci metti una SIM, si rimette con:" -ForegroundColor DarkGray
    Write-Host "  adb shell pm install -r --user 0 /system/priv-app/Dialer/Dialer.apk" -ForegroundColor DarkGray
    $daTogliere += $Telefonia
} else {
    Write-Host "C'e' una SIM ($simState): la telefonia resta." -ForegroundColor Yellow
}

# Lo sfondo si tocca solo quando Casa ha gia' messo il suo: togliere adesso chi
# fornisce quello attuale farebbe ricomparire il default di fabbrica.
#
# Il segnale non puo' essere un file in /data/data/dev.casa: da adb quella
# cartella non si legge (Permission denied, ed e' giusto cosi'). Si guarda
# invece lo sfondo di BLOCCO: il ROM non ne aveva nessuno, e Casa e' l'unica
# che ne imposta uno. Se c'e', ce l'ha messo lei.
$sfondi = Chiedi 'dumpsys wallpaper'
$dopoIlBlocco = ($sfondi -split 'Lock wallpaper state:', 2)[1]
if ($dopoIlBlocco -match 'id=\d') {
    Write-Host "Assistente Home ha gia' messo il suo sfondo: tolgo anche chi lo sceglieva." -ForegroundColor Cyan
    $daTogliere += $SceltaSfondo
} else {
    Write-Host "Assistente Home non ha ancora messo lo sfondo: livepicker e wallpapercropper restano." -ForegroundColor Yellow
    Write-Host "  rilancia questo script dopo la prima installazione di Assistente Home." -ForegroundColor DarkGray
}

$daTogliere = $daTogliere | Select-Object -Unique
$intoccabili = $daTogliere | Where-Object { $MaiToccare -contains $_ }
if ($intoccabili) {
    throw "Nella lista da togliere c'e' roba intoccabile: $($intoccabili -join ', '). Mi fermo."
}

# Un solo giro di adb invece di cinquanta: ogni chiamata costa un decimo di
# secondo di andata e ritorno, e cinquanta chiamate sono cinque secondi buttati.
# Il ciclo gira nella shell del tablet e stampa il nome del pacchetto, poi
# l'esito di pm sulla riga dopo.
#
# Niente "$(pm uninstall ...)" dentro un echo: la sostituzione di comando non
# sopravvive al viaggio fino a /system/bin/sh, che finisce per provare a
# eseguire "Failure [not installed for 0]" come se fosse un comando, e ogni
# esito si perde. Due righe per pacchetto, nessuna sostituzione.
Write-Host ""
Write-Host "Tolgo $($daTogliere.Count) pacchetti..." -ForegroundColor Cyan
$ciclo = 'for p in ' + ($daTogliere -join ' ') + '; do echo "PKG $p"; pm uninstall -k --user 0 $p; done'
$esiti = (& $adb -s $serial shell $ciclo) -split "`n"

$tolti = 0
$giaVia = 0
$falliti = @()
$pacchetto = $null
foreach ($riga in $esiti) {
    $riga = $riga.Trim()
    if (-not $riga) { continue }
    if ($riga -match '^PKG (.+)$') { $pacchetto = $Matches[1]; continue }
    if (-not $pacchetto) { continue }
    if ($riga -match 'Success') {
        $tolti++
        Write-Host "  via   $pacchetto" -ForegroundColor Green
    } elseif ($riga -match 'not installed|Unknown package') {
        $giaVia++
    } else {
        $falliti += "$pacchetto -> $riga"
    }
    $pacchetto = $null
}

Write-Host ""
Write-Host "$tolti tolti adesso, $giaVia non c'erano gia' piu'." -ForegroundColor Green
if ($falliti) {
    Write-Host "Non tolti:" -ForegroundColor Yellow
    $falliti | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}

# DuraSpeed ha anche un interruttore suo, e ci sono segnalazioni che si
# riaccenda da solo dopo un riavvio. Lo mettiamo a zero comunque: costa niente
# ed e' la seconda serratura sulla stessa porta.
& $adb -s $serial shell 'settings put global setting.duraspeed.enabled 0' | Out-Null

# --- il vecchio launcher ---------------------------------------------------
#
# launcher3 era rimasto come rete di sicurezza, ma sul suo desktop c'era ancora
# la roba del ROM - fra cui una scorciatoia "DUODUOGO Shop" - e bastava un
# momento di confusione per finirci dentro. Adesso che Casa e' la Home
# preferita e regge, il vecchio launcher va spento.
#
# Spento, non disinstallato, e solo se Casa e' davvero registrata come Home:
# "pm disable-user" si annulla con "pm enable", e se un giorno Casa non
# partisse basterebbe una riga da adb per riavere una schermata. Prima si
# cancellano i suoi dati, che e' quello che porta via il desktop e le
# scorciatoie che ci stavano sopra.
$homePreferita = Chiedi 'dumpsys package preferred-activities'
if ($homePreferita -match 'dev\.casa/\.MainActivity') {
    Write-Host ""
    Write-Host "Assistente Home e' la Home preferita: spengo launcher3 e il suo desktop." -ForegroundColor Cyan
    & $adb -s $serial shell 'pm clear com.android.launcher3' | Out-Null
    $esitoLauncher = Chiedi 'pm disable-user --user 0 com.android.launcher3'
    if ($esitoLauncher -match 'disabled') {
        Write-Host "  launcher3 spento. Per riaverlo: adb shell pm enable com.android.launcher3" -ForegroundColor Green
    } else {
        Write-Host "  launcher3: $esitoLauncher" -ForegroundColor Yellow
    }
} else {
    Write-Host ""
    Write-Host "Assistente Home non risulta Home preferita: lascio launcher3 acceso." -ForegroundColor Yellow
    Write-Host "  senza di lui e senza Assistente Home, il tablet resterebbe senza schermata." -ForegroundColor DarkGray
}

# --- il verificatore del Play Store ----------------------------------------
#
# Play Protect controlla ogni APK che si installa, e sul nostro ci moriva
# sopra a ogni volta:
#
#   E Finsky: VerifyRequiredSplitTypesInstallTask ... Fatal signal 6 (SIGABRT)
#   E Finsky: VerifyApps V31SignatureVerification: Failed to collect
#             certificates for the package: dev.casa
#
# Il sintomo era "Google Play Store has stopped" a ogni build, con la finestra
# di sistema che copriva Casa. La causa non e' l'APK - e' firmato v1 e v2, come
# vuole API 24 - ma un verificatore che si aspetta uno schema di firma 3.1 e
# degli split che qui non ci sono.
#
# Su un apparecchio dedicato, dove le app si installano da qui e non dal
# negozio, il controllo non serve. Si spegne, e le installazioni diventano
# anche piu' rapide.
Write-Host ""
Write-Host "Spengo il verificatore di Play Protect (va in crash sui nostri APK)..." -ForegroundColor Cyan
& $adb -s $serial shell 'settings put global package_verifier_enable 0' | Out-Null
& $adb -s $serial shell 'settings put global verifier_verify_adb_installs 0' | Out-Null
& $adb -s $serial shell 'settings put global package_verifier_user_consent -1' | Out-Null
Write-Host "  fatto. Per riaccenderlo: settings put global package_verifier_enable 1" -ForegroundColor DarkGray

# --- animazioni a meta' -----------------------------------------------------
#
# Il WindowManager le rilegge al volo, senza riavvio. 0.5 e non 0: a zero
# spariscono anche le dissolvenze, e il passaggio fra Casa e Spotify diventa
# uno scatto a schermo. Su una A7 a 1,3 GHz meta' durata e' la differenza
# fra "reagisce" e "ci pensa".
Write-Host ""
Write-Host "Animazioni a meta' durata..." -ForegroundColor Cyan
foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
    & $adb -s $serial shell "settings put global $k 0.5" | Out-Null
}
Write-Host "  fatto. Per tornare alle originali: settings put global <chiave> 1" -ForegroundColor DarkGray

# --- i log di Casa ---------------------------------------------------------
#
# Questo ROM ha "log.tag = E": il sistema butta via tutto quello che non e' un
# errore, prima ancora che arrivi a logcat. Il sintomo e' peggio di un log
# assente, perche' logcat risponde e non dice niente - sembra che l'app non
# stia girando. Ci sono volute mezz'ora e un "log -t prova" per capirlo.
#
# Si sblocca tag per tag invece di aprire tutto: "log.tag V" da solo
# riverserebbe in logcat anche il chiacchiericcio di MediaTek, che qui e'
# abbondante. Con persist. davanti sopravvive al riavvio.
Write-Host ""
Write-Host "Sblocco i log di Assistente Home (questo ROM li filtra a livello Errore)..." -ForegroundColor Cyan
$tag = @('Casa', 'Casa.Sfondo', 'Casa.Vetro', 'Casa.Archivio', 'Casa.Tuya', 'Casa.Luci', 'Casa.Musica')
foreach ($t in $tag) {
    & $adb -s $serial shell "setprop persist.log.tag.$t V" | Out-Null
    & $adb -s $serial shell "setprop log.tag.$t V" | Out-Null
}
Write-Host "  $($tag.Count) tag aperti: adesso 'adb logcat -s Casa:V' dice qualcosa." -ForegroundColor Green

# ---- come sta dopo --------------------------------------------------------

$memDopo = MemoriaLibera
Write-Host ""
Write-Host ("MemAvailable : {0:N0} MB -> {1:N0} MB" -f $memPrima, $memDopo) -ForegroundColor Cyan
Write-Host "Il numero vero si legge dopo un riavvio: adesso quello che ho tolto e' ancora in memoria." -ForegroundColor DarkGray

Write-Host ""
Write-Host "App di terze parti rimaste:" -ForegroundColor Cyan
(Chiedi 'pm list packages -3') -split "`n" |
    Where-Object { $_ } |
    ForEach-Object { Write-Host ("  " + ($_ -replace 'package:', '').Trim()) }

Write-Host ""
Write-Host "Poi: riavvia il tablet e rilancia con -Verifica, per vedere se DuraSpeed resta via." -ForegroundColor Cyan
