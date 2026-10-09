# Secondo giro: gli avanzi rimasti dopo sistema.ps1.
#
# sistema.ps1 toglie le trappole e la roba del vecchio padrone. Questo va oltre.
#
# IL PLAY STORE ADESSO RESTA, ed e' un cambio di idea con un motivo preciso.
# La prima versione lo toglieva: 142 MB fra i suoi due processi, il costo fisso
# piu' grosso dopo Play Services. Poi Netflix ha smesso di funzionare - il suo
# APK era tornato quello di fabbrica del 2019, e i server rifiutano i client
# vecchi - e senza negozio non c'era modo di aggiornarlo. Su un apparecchio da
# muro che deve durare anni **le app installate invecchiano**, e un tablet che
# non le puo' aggiornare le perde una alla volta.
#
# Quindi il negozio resta installato, ma:
#   - non compare in Casa: la tessera e' stata tolta da SezioneApp, e lo si
#     apre da adb quando serve davvero;
#   - il suo corteo se ne va comunque - feedback, partnersetup,
#     onetimeinitializer, configupdater servono a Google, non a installare
#     un APK;
#   - da chiuso non tiene processi in piedi, e -Verifica lo dice.
#
# Chi cambia idea di nuovo ha -SenzaNegozio. Ma prima legga qui sotto come si
# rimette, perche' la prima volta ci si e' quasi rimasti senza.
#
# QUELLO CHE RESTA, e perche':
#
#   com.google.android.gms   Play Services. NON si tocca qui: da lui passa
#                            SpeechRecognizer, cioe' l'orecchio di Casa.
#                            Toglierlo e' un'altra decisione, piu' grossa, e
#                            va misurata a parte - vedi -SenzaGoogle.
#   com.android.chrome       e' la WebView del sistema, non un browser
#   com.google.android.tts   la voce che risponde
#   com.mediatek.providers.drm   serve a Netflix
#
# REVERSIBILE, E ADESSO SI SA COME. Su API 24 "install-existing" non esiste
# davvero - e' arrivato con Android 8 - e reinstallare l'APK di /system
# fallisce per le app che erano state AGGIORNATE: il sistema ricorda la
# versione nuova e rifiuta con INSTALL_FAILED_VERSION_DOWNGRADE. E' quello che
# ha fatto credere per due giorni che il Play Store fosse perso.
#
# Non lo era, e l'errore era nostro: si stava reinstallando l'APK sbagliato.
# "pm uninstall -k --user 0" NON cancella l'aggiornamento, lo lascia dov'e' in
# /data/app/<pacchetto>-N/, leggibile da tutti. Si rimette da li', con due
# accortezze che non stanno scritte da nessuna parte:
#
#   1. --user 0 su install-create. Senza, la sessione riesce, l'APK viene
#      ristanziato, e il pacchetto resta installed=false per l'utente: dice
#      "Success" e in "pm list packages" non compare niente.
#   2. una sessione multi-APK. Il Play Store e' diviso in base.apk piu' gli
#      split (armeabi_v7a, en), e un "pm install" del solo base non basta.
#
#   .\alleggerisci.ps1                 toglie gli avanzi, il negozio resta
#   .\alleggerisci.ps1 -Essenziale     toglie anche il resto che non serve
#   .\alleggerisci.ps1 -Verifica       dice solo come sta
#   .\alleggerisci.ps1 -SenzaNegozio   toglie ANCHE il Play Store
#   .\alleggerisci.ps1 -SenzaGoogle    toglie ANCHE Play Services (vedi sotto)
#   .\alleggerisci.ps1 -Rimetti        rimette tutto quello che ha tolto

param(
    [switch]$Verifica,
    [switch]$Essenziale,
    [switch]$SenzaNegozio,
    [switch]$SenzaGoogle,
    [switch]$Rimetti
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

# IL CARICO SU QUESTO TABLET NON MISURA IL LAVORO, e ci ha ingannati per due
# giorni: /proc/loadavg dice stabilmente 8 su quattro core, e da quel numero
# erano nate frasi come "carico 10-12" usate come misura di salute.
#
# Il carico di Linux conta i processi in stato R **e in stato D**, e su questo
# kernel MediaTek ci sono otto thread di driver che dormono per sempre in D:
#
#     fuse_log  hps_main  ddp_irq_log_kth  display_esd_che
#     decouple_trigge  disp_idlemgr  hang_detect  bat_thread_kthr
#
# Otto thread, carico otto. Consumano ZERO CPU - "dumpsys cpuinfo" nello stesso
# momento diceva 5,9% totale su un tablet fermo. Il numero non e' sbagliato:
# misura una cosa diversa da quella che sembra, ed e' inutilizzabile qui.
#
# Quindi si legge la CPU vera, che e' l'ultima riga di dumpsys cpuinfo.
function CpuVera {
    $righe = (Chiedi 'dumpsys cpuinfo') -split "`n"
    $totale = $righe | Where-Object { $_ -match 'TOTAL:' } | Select-Object -Last 1
    if ($totale -match '([\d.]+)%\s*TOTAL') { return $Matches[1] + '%' }
    return 'n.d.'
}

# ---- chi va via -----------------------------------------------------------

# Il corteo del Play Store. Sono cose che servono a Google, non a noi:
# feedback manda i rapporti di crash, partnersetup dice a Google chi ha venduto
# il tablet, onetimeinitializer ha fatto il suo mestiere la prima volta che il
# tablet si e' acceso nel 2019, configupdater aggiorna liste che qui non
# guarda piu' nessuno da anni.
#
# **Il negozio funziona lo stesso senza di loro**: per scaricare e aggiornare
# un APK gli bastano se stesso e Play Services.
$CorteoNegozio = @(
    'com.google.android.feedback'
    'com.google.android.partnersetup'
    'com.google.android.onetimeinitializer'
    'com.google.android.configupdater'
    'com.google.android.verifier'
)

# Il negozio, dietro il suo interruttore. Di regola resta: senza, le app
# installate invecchiano e nessuno le puo' aggiornare - che e' esattamente
# quello che e' successo a Netflix.
$Negozio = @(
    'com.android.vending'
)

# Il backup su Google. Un apparecchio da muro non ha niente da salvare nel
# cloud: la sua configurazione sta nel PC, in questa cartella.
#
# ATTENZIONE: com.google.android.backuptransport NON e' in questo elenco, e non
# ci deve tornare. Il nome dice "backup" e sembra il primo da buttare; in realta'
# **da lui passa l'autenticazione del Play Store**. Togliendolo, il negozio
# risponde
#
#     Authentication is required. You need to sign in to your Google Account.
#
# con l'account regolarmente presente e la rete funzionante. Il perche' si legge
# solo aprendo i tag di log di Auth, che questo ROM tiene chiusi:
#
#     W Auth: [ChimeraGetToken] exception while trying to fetch auth tokens for
#             app=com.google.android.backuptransport,
#             scope=oauth2:https://www.googleapis.com/auth/googleplay
#     W Auth: [LegacyTokenProcessor] Couldn't fetch token for package:
#             com.google.android.backuptransport. Package info could not be found.
#     Caused by: PackageManager$NameNotFoundException
#
# GMS conia il token dello scope "googleplay" **a nome di quel pacchetto**. Se
# il pacchetto non esiste, getApplicationInfo solleva, l'authenticator
# restituisce ERROR, e il negozio non sa perche'. Sta in $MaiToccare, sotto.
$Backup = @(
    'com.android.wallpaperbackup'
    'com.android.sharedstoragebackup'
    'com.android.backupconfirm'
)

# Avanzi che sistema.ps1 non prendeva.
$Avanzi = @(
    'com.android.fmradio'            # la sezione Radio fa la stessa cosa, meglio
    'com.android.htmlviewer'         # apre file .html locali: mai
    'com.android.bluetoothmidiservice'
    'com.android.cts.ctsshim'        # segnaposto per la suite di test di Android
    'com.android.cts.priv.ctsshim'
    'com.android.providers.calendar' # il calendario e' gia' andato via
    'com.android.providers.userdictionary'
    'com.android.providers.downloads.ui'
    'com.android.provision'          # procedura di primo avvio, gia' fatta
    'com.android.statementservice'   # verifica i link delle app: nessuna qui
    'com.android.mms.service'        # SMS, e non c'e' la SIM
    'com.android.inputdevices'       # layout di tastiere fisiche
    'com.android.mtp'                # trasferimento file via USB: si usa adb
    'com.android.pacprocessor'       # proxy automatico
    'com.android.proxyhandler'
    'com.mediatek.schpwronoff'       # accensione programmata di MediaTek
    'com.mediatek.sensorhub.ui'
    'com.mediatek.ygps'              # collaudo del GPS
    'com.mediatek.atci.service'      # comandi AT verso il modem
    'com.mediatek.dataprotection'
    'com.mediatek.batterywarning'
)

# Il terzo giro: quel che resta e non serve a Casa, Spotify, Netflix o Chrome.
#
# Guadagna poco in RAM - sono quasi tutti provider e finestre di dialogo che
# non girano finche' nessuno li chiama - ma toglie servizi che partono
# all'avvio e riduce quello che il sistema deve tenere in piedi. Su un
# apparecchio che fa quattro cose, e' roba che non fara' mai nessuna delle
# quattro.
# NOME DIVERSO DALLO SWITCH, e non e' pignoleria: si chiamava $Essenziale come
# il parametro -Essenziale, e PowerShell tipizza la variabile dal blocco param.
# Assegnare un array a uno [switch] solleva
#
#     Impossibile convertire il valore "System.Object[]" nel tipo
#     "System.Management.Automation.SwitchParameter"
#
# e lo script moriva QUI, prima di togliere qualunque cosa - con qualunque
# parametro, -Verifica compreso. E' il motivo per cui il corteo del Play Store
# era ancora installato mesi dopo: lo script non ci era mai arrivato.
$NonEssenziale = @(
    'com.android.location.fused'     # nessuna app qui chiede dove siamo
    'com.android.providers.applications'  # provider di ricerca fra le app
    'com.android.vpndialogs'         # nessuna VPN
    'com.android.managedprovisioning'# il device owner e' gia' impostato
    'com.android.server.telecom'     # chiamate, e non c'e' la SIM
    'com.android.providers.contacts' # nessuna rubrica, nessuna app che la legga
)

# com.google.android.gsf.login era in quell'elenco, e ne e' uscito quando il
# negozio e' tornato: e' l'autenticatore dell'account Google di questo tablet -
# quello aggiunto anni fa da chi l'aveva prima - e il Play Store senza account
# non scarica niente. Da fermo non costa nulla, non gira: toglierlo era un
# rischio preso gratis.

# Il bluetooth non e' in quell'elenco di proposito: un altoparlante senza fili
# in cucina e' una cosa che si puo' volere domani, e il servizio da fermo costa
# poco. Si toglie a mano se si decide che non serve:
#   adb shell pm uninstall -k --user 0 com.android.bluetooth

# La decisione grossa, dietro un interruttore suo perche' ROMPE l'assistente.
#
# Play Services e' il costo fisso piu' alto della macchina - 250-350 MB e CPU
# di sottofondo, per sempre. Ma da lui passa SpeechRecognizer: senza, Casa
# smette di capire il parlato, e resta un apparecchio che si comanda solo col
# dito finche' non c'e' un motore a bordo. Netflix inoltre potrebbe non
# gradire.
#
# Si prova e si misura, invece di decidere a tavolino: -Rimetti annulla tutto.
$Google = @(
    'com.google.android.gms'
    'com.google.android.gsf'
    'com.google.android.gsf.login'
)

# Rete di sicurezza: se uno di questi finisse in una lista qui sopra, lo script
# si ferma invece di rompere il tablet.
$MaiToccare = @(
    'android',                        'com.android.systemui'
    'com.android.settings',           'com.android.shell'
    'com.android.phone',              'com.android.launcher3'
    'com.android.providers.settings', 'com.android.providers.media'
    'com.android.providers.downloads','com.android.providers.telephony'
    'com.android.externalstorage',    'com.android.defcontainer'
    'com.google.android.packageinstaller'
    'com.google.android.googlequicksearchbox'
    'com.google.android.tts',         'com.android.chrome'
    'com.android.inputmethod.latin',  'com.android.captiveportallogin'
    'com.android.certinstaller',      'com.android.keychain'
    'com.mediatek.connectivity',      'com.mediatek.thermalmanager'
    'com.mediatek.providers.drm'
    'com.netflix.mediaclient',        'com.spotify.music'
    'com.google.android.backuptransport'   # l'autenticazione del Play Store
    'dev.casa'
)

# ---- come sta adesso ------------------------------------------------------

Write-Host ""
Write-Host "Tablet $serial" -ForegroundColor Cyan
$memPrima = MemoriaLibera
Write-Host ("MemAvailable : {0:N0} MB" -f $memPrima)
Write-Host ("CPU          : {0} (il carico di loadavg qui non vuol dire niente:" -f (CpuVera))
Write-Host  "               sono otto thread MediaTek fermi in stato D)"      -ForegroundColor DarkGray
Write-Host ("lanciabili   : {0}" -f ((Chiedi 'pm list packages') -split "`n").Count)

# "pm list packages" elenca anche i disabilitati, quindi da solo direbbe
# "presente" di un negozio spento - che e' proprio la distinzione che qui
# interessa. Serve il confronto con -d.
$store = Chiedi 'pm list packages com.android.vending'
$storeSpento = Chiedi 'pm list packages -d com.android.vending'
$storeVivo = Chiedi 'ps | grep com.android.vending'
$comeSta = if (-not $store) { 'via' }
           elseif ($storeVivo) { 'ACCESO E IN MEMORIA' }
           elseif ($storeSpento) { 'installato ma spento (negozio.ps1 -Apri)' }
           else { 'acceso, ma fermo' }
Write-Host ("Play Store   : {0}" -f $comeSta)
$gms = Chiedi 'pm list packages com.google.android.gms'
Write-Host ("Play Services: {0}" -f $(if ($gms) { 'presente' } else { 'via' }))

if ($Verifica) {
    Write-Host ""
    Write-Host "Solo verifica: non ho toccato niente." -ForegroundColor Cyan
    exit 0
}

# ---- rimettere ------------------------------------------------------------

$tutti = $Negozio + $CorteoNegozio + $Backup + $Avanzi + $NonEssenziale + $Google

if ($Rimetti) {
    # COME SI RIMETTE, per davvero.
    #
    # Tutta la documentazione di questo progetto (e mezzo internet) diceva che
    # "pm uninstall -k --user 0" si annulla con "pm install-existing". Su API 24
    # non esiste:
    #
    #     Error: unknown command 'install-existing'
    #
    # E' arrivato con Android 8. Da qui si era concluso che l'unica strada
    # fosse reinstallare l'APK di /system - e per un'app di sistema che era
    # stata AGGIORNATA quella strada e' chiusa: il sistema ricorda la versione
    # nuova e risponde
    #
    #     Failure [INSTALL_FAILED_VERSION_DOWNGRADE]
    #
    # (e -d non aiuta: su una build "user" vale solo per i pacchetti
    # debuggable). Da li' era nata la convinzione che il Play Store fosse perso
    # per sempre.
    #
    # Era sbagliata, e l'errore era nostro: si stava reinstallando l'APK
    # sbagliato. "pm uninstall -k --user 0" **non cancella l'aggiornamento**:
    # lo lascia in /data/app/<pacchetto>-N/, con i permessi 644 e le cartelle
    # attraversabili, quindi la shell lo legge benissimo. Basta reinstallare
    # QUELLO invece di quello di /system - stessa versione, nessun downgrade.
    #
    # Due accortezze, e senza nemmeno una delle due non funziona:
    #
    #   1. --user 0 su install-create. Senza, la sessione riesce e l'APK viene
    #      ristanziato, ma il pacchetto resta installed=false per l'utente:
    #      "Success" e in "pm list packages" non c'e' niente. E' il pezzo che
    #      e' costato piu' tempo, perche' il comando dice di essere riuscito.
    #   2. una sessione multi-APK. Le app moderne sono divise in base.apk piu'
    #      gli split (l'ABI, la lingua): il Play Store ne ha tre, e installare
    #      il solo base non basta.
    #
    # Quindi si prova prima /data/app, e solo se non c'e' si ripiega su
    # /system - che e' la strada giusta per le app mai aggiornate.
    Write-Host "Rimetto $($tutti.Count) pacchetti dagli APK di sistema..." -ForegroundColor Cyan

    $cartelle = (Chiedi 'ls -d /system/priv-app/*/ /system/app/*/ 2>/dev/null') -split "`n" |
                ForEach-Object { $_.Trim() } | Where-Object { $_ }

    # Le cartelle degli aggiornamenti rimasti in /data/app. "ls /data/app" da'
    # "Permission denied" alla shell - la cartella e' drwxrwx--x - ma il nome
    # si legge lo stesso da "dumpsys package", e con quello dentro ci si entra.
    function CartellaAggiornamento {
        param([string]$Pacchetto)
        $righe = (Chiedi "dumpsys package $Pacchetto") -split "`n"
        foreach ($r in $righe) {
            if ($r -match 'codePath=(/data/app/[^\s]+)') { return $Matches[1] }
        }
        return $null
    }

    # Una sessione multi-APK. Restituisce l'esito di install-commit.
    function RimettiDaCartella {
        param([string]$Pacchetto, [string]$Cartella)
        $elenco = (Chiedi "ls $Cartella/*.apk 2>/dev/null") -split "`n" |
                  ForEach-Object { $_.Trim() } | Where-Object { $_ -match '\.apk$' }
        if (-not $elenco) { return 'nessun APK in ' + $Cartella }

        $creata = Chiedi "pm install-create -r -g --user 0 -i $Pacchetto"
        if ($creata -notmatch '\[(\d+)\]') { return "sessione non creata: $creata" }
        $id = $Matches[1]

        foreach ($apk in $elenco) {
            # Il nome dello split e' il nome del file senza .apk: base,
            # split_config.armeabi_v7a, split_config.en. Deve essere diverso
            # per ognuno, se no il secondo sovrascrive il primo.
            $nome = [System.IO.Path]::GetFileNameWithoutExtension($apk)
            $misura = (Chiedi "stat -c %s $apk").Trim()
            $esito = Chiedi "pm install-write -S $misura $id $nome $apk"
            if ($esito -notmatch 'Success') {
                Chiedi "pm install-abandon $id" | Out-Null
                return "install-write fallita su $nome : $esito"
            }
        }
        return Chiedi "pm install-commit $id"
    }

    $rimessi = 0; $nonTornati = @()
    foreach ($p in $tutti) {
        if (Chiedi "pm list packages $p") { continue }   # c'e' gia'

        # 1. l'aggiornamento, se e' rimasto dov'era.
        $cartella = CartellaAggiornamento $p
        if ($cartella) {
            $esito = RimettiDaCartella $p $cartella
            if ($esito -match 'Success') {
                $rimessi++
                Write-Host "  rimesso  $p (dall'aggiornamento in $cartella)" -ForegroundColor Green
                continue
            }
            $nonTornati += "$p da /data/app -> $esito"
        }

        # 2. l'APK di sistema. Va per le app mai aggiornate; per le altre
        #    finisce in INSTALL_FAILED_VERSION_DOWNGRADE, ed e' giusto cosi'.
        $trovato = $null
        foreach ($c in $cartelle) {
            $apk = (Chiedi "ls $c*.apk 2>/dev/null") -split "`n" | Select-Object -First 1
            if (-not $apk) { continue }
            # Il nome della cartella non e' il nome del pacchetto: si chiede
            # all'APK chi e', invece di indovinare da "Phonesky" -> "vending".
            $chi = Chiedi "pm dump $p 2>/dev/null"
            if ($chi -match [regex]::Escape($c.TrimEnd('/'))) { $trovato = $apk.Trim(); break }
        }

        if (-not $trovato) { $nonTornati += "$p (nessun APK ne' in /data/app ne' in /system)"; continue }
        $esito = Chiedi "pm install -r --user 0 $trovato"
        if ($esito -match 'Success') {
            $rimessi++
            Write-Host "  rimesso  $p (da /system)" -ForegroundColor Green
        } else {
            $nonTornati += "$p da /system -> $esito"
        }
    }

    Write-Host ""
    Write-Host "$rimessi rimessi." -ForegroundColor Green
    if ($nonTornati) {
        Write-Host "NON tornati:" -ForegroundColor Yellow
        $nonTornati | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
    }
    Write-Host "Riavvia il tablet: adb -s $serial shell reboot" -ForegroundColor Cyan
    exit 0
}

# ---- togliere -------------------------------------------------------------

$daTogliere = $CorteoNegozio + $Backup + $Avanzi
if ($Essenziale) {
    Write-Host ""
    Write-Host "-Essenziale: tolgo anche quel che non serve alle nostre quattro app." -ForegroundColor Cyan
    $daTogliere += $NonEssenziale
}
if ($SenzaNegozio) {
    Write-Host ""
    Write-Host "-SenzaNegozio: tolgo anche il Play Store." -ForegroundColor Yellow
    Write-Host "  Da qui in poi le app si aggiornano solo con 'adb install', una" -ForegroundColor Yellow
    Write-Host "  per una - e quelle che invecchiano smettono di funzionare da" -ForegroundColor Yellow
    Write-Host "  sole: e' successo a Netflix. Si annulla con -Rimetti." -ForegroundColor DarkGray
    $daTogliere += $Negozio
}
if ($SenzaGoogle) {
    Write-Host ""
    Write-Host "-SenzaGoogle: tolgo anche Play Services." -ForegroundColor Yellow
    Write-Host "  Assistente Home smettera' di capire il parlato finche' non c'e' un motore" -ForegroundColor Yellow
    Write-Host "  a bordo. Si annulla con .\alleggerisci.ps1 -Rimetti" -ForegroundColor DarkGray
    $daTogliere += $Google
}

$daTogliere = $daTogliere | Select-Object -Unique
$intoccabili = $daTogliere | Where-Object { $MaiToccare -contains $_ }
if ($intoccabili) {
    throw "Nella lista da togliere c'e' roba intoccabile: $($intoccabili -join ', '). Mi fermo."
}

# Un solo giro di adb invece di quaranta. Due righe per pacchetto e nessuna
# sostituzione di comando: la sostituzione non sopravvive al viaggio fino a
# /system/bin/sh - vedi il commento lungo in sistema.ps1.
Write-Host ""
Write-Host "Tolgo $($daTogliere.Count) pacchetti..." -ForegroundColor Cyan
$ciclo = 'for p in ' + ($daTogliere -join ' ') + '; do echo "PKG $p"; pm uninstall -k --user 0 $p; done'
$esiti = (& $adb -s $serial shell $ciclo) -split "`n"

$tolti = 0; $giaVia = 0; $falliti = @(); $pacchetto = $null
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

# Il negozio resta installato, ma NON deve restare acceso.
#
# Misurato dopo un riavvio, senza che nessuno avesse aperto niente: con il Play
# Store in piedi MemAvailable era 383 MB, senza 437. Sessantaquattro megabyte
# che il tablet spendeva da solo, all'accensione, per un negozio che serve due
# volte l'anno:
#
#     com.android.vending              39 MB
#     com.android.vending:background   21 MB
#
# disable-user e non uninstall: cosi' resta dov'e' e si riaccende con una riga,
# invece di dover ripescare l'aggiornamento da /data/app come e' toccato fare
# una volta. Lo riaccende .\negozio.ps1 -Apri quando c'e' da aggiornare
# qualcosa, e lo rispegne -Chiudi.
#
# force-stop PRIMA di disable-user: disabilitare un pacchetto non ne uccide i
# processi gia' vivi, e quelli resterebbero in memoria fino al riavvio - cioe'
# proprio i megabyte che si stanno cercando di liberare.
Write-Host ""
Write-Host "Fermo quello che e' ancora in memoria..." -ForegroundColor Cyan
foreach ($p in @('com.android.vending', 'com.google.android.feedback')) {
    & $adb -s $serial shell "am force-stop $p" 2>$null | Out-Null
}
if (-not $SenzaNegozio) {
    & $adb -s $serial shell "pm disable-user --user 0 com.android.vending" 2>$null | Out-Null
    Write-Host "  Play Store installato ma spento (negozio.ps1 -Apri per usarlo)" -ForegroundColor DarkGray
}

Start-Sleep -Seconds 3
$memDopo = MemoriaLibera
Write-Host ""
Write-Host ("MemAvailable : {0:N0} MB -> {1:N0} MB  ({2:+#;-#;0} MB)" -f `
    $memPrima, $memDopo, ($memDopo - $memPrima)) -ForegroundColor Green
Write-Host ("lanciabili   : {0}" -f ((Chiedi 'pm list packages') -split "`n").Count)
Write-Host ""
Write-Host "Il numero vero si legge dopo un riavvio, a sistema assestato:" -ForegroundColor Cyan
Write-Host "  adb -s $serial shell reboot" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Le app si mettono con un APK:" -ForegroundColor Cyan
Write-Host "  adb -s $serial install -r qualcosa.apk" -ForegroundColor DarkGray
Write-Host "...oppure dal negozio, che si accende per il tempo che serve:" -ForegroundColor Cyan
Write-Host "  .\negozio.ps1 -Apri   ...   .\negozio.ps1 -Chiudi" -ForegroundColor DarkGray
