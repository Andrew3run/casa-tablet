# Casa sull'emulatore, senza il tablet: per vedere l'interfaccia dal PC.
#
#   .\tools\anteprima.ps1            compila, avvia l'emulatore, installa, apre
#   .\tools\anteprima.ps1 -Foto x.png    ...e salva una cattura dello schermo
#   .\tools\anteprima.ps1 -Spegni    chiude l'emulatore
#
# L'emulatore e' « Casa_E960 »: Android 7.0 (API 24), 1280x800, 213 dpi, 1 GB,
# come il tablet, ma x86_64. Casa porta go-librespot per ARM, quindi qui si
# installa una copia con lo stesso demone compilato per x86_64, preso dalla
# stessa release di Android-LibreThing (l'APK universale): Spotify funziona
# davvero, col proprio account, e il primo accesso si approva dal telefono
# col QR come sul tablet. Il Casa.apk del tablet resta quello di build.ps1.
#
# Non passa da dispositivo.ps1 di proposito: parla solo con emulator-5580, che
# non puo' essere ne' l'E960 ne' l'SM-T210.

param(
    [switch]$NonCompilare,
    [string]$Foto,
    [switch]$Spegni
)

$ErrorActionPreference = 'Stop'

$root  = Split-Path $PSScriptRoot -Parent
$adb   = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
$sdk   = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$porta = 5580
$emu   = "emulator-$porta"
$avd   = 'Casa_E960'

function Adb { & $adb -s $emu @args }

if ($Spegni) { Adb emu kill | Out-Null; exit 0 }

if (-not (Test-Path "$env:USERPROFILE\.android\avd\$avd.ini")) {
    throw "Manca l'emulatore $avd. Serve l'immagine 'system-images;android-24;default;x86' e un AVD 1280x800 a 213 dpi."
}

# --- 1. compila -------------------------------------------------------------
if (-not $NonCompilare) {
    & (Join-Path $root 'tablet\build.ps1')
    if ($LASTEXITCODE) { throw "compilazione fallita" }
}
$apk = Join-Path $root 'tablet\build\Casa.apk'

# --- 2. la copia senza lib/ --------------------------------------------------
$bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
    Sort-Object { [version]($_.Name -replace '[^0-9.].*$','') } | Select-Object -Last 1
$cartella = Join-Path $root 'tablet\build\anteprima'
New-Item -ItemType Directory -Force $cartella | Out-Null
$grezza  = Join-Path $cartella 'grezza.apk'
$allin   = Join-Path $cartella 'allineata.apk'
$finale  = Join-Path $cartella 'CasaAnteprima.apk'
Copy-Item $apk $grezza -Force

Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

# Il demone per x86_64, una volta sola: l'APK universale pesa 80 MB.
$demone = Join-Path $cartella 'libgolibrespot-x86_64.so'
if (-not (Test-Path $demone)) {
    $universale = Join-Path $cartella 'librething-universale.apk'
    if (-not (Test-Path $universale)) {
        Write-Host "Scarico go-librespot per l'emulatore"
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing -OutFile $universale `
            'https://github.com/SEKY443/Android-LibreThing/releases/download/1.3.1/app-universal-release.apk'
    }
    $u = [System.IO.Compression.ZipFile]::OpenRead($universale)
    try {
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile(
            $u.GetEntry('lib/x86_64/libgolibrespot.so'), $demone, $true)
    } finally { $u.Dispose() }
    Remove-Item $universale -Force
}

$zip = [System.IO.Compression.ZipFile]::Open($grezza, 'Update')
try {
    foreach ($e in @($zip.Entries)) {
        if ($e.FullName -like 'lib/*' -or $e.FullName -like 'META-INF/*') { $e.Delete() }
    }
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, $demone, 'lib/x86_64/libgolibrespot.so')
} finally { $zip.Dispose() }

& (Join-Path $bt.FullName 'zipalign.exe') -f 4 $grezza $allin
if ($LASTEXITCODE) { throw "zipalign fallito" }
$ks = Join-Path $root 'tablet\keystore\casa.jks'
& (Join-Path $bt.FullName 'apksigner.bat') sign --ks $ks --ks-pass pass:casadev --key-pass pass:casadev `
    --min-sdk-version 24 --out $finale $allin
if ($LASTEXITCODE) { throw "firma fallita" }

# --- 3. l'emulatore -----------------------------------------------------------
$acceso = (& $adb devices) -match "^$emu\s+device"
if (-not $acceso) {
    Write-Host "Avvio l'emulatore $avd"
    Start-Process (Join-Path $sdk 'emulator\emulator.exe') `
        -ArgumentList "-avd $avd -port $porta -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect" `
        -WindowStyle Normal
    & $adb -s $emu wait-for-device
    do { Start-Sleep -Seconds 2 } until (((Adb shell getprop sys.boot_completed) -join '').Trim() -eq '1')
}

# --- 4. installa e apri -------------------------------------------------------
Adb install -r $finale | Out-Host
# I permessi che sul tablet si danno una volta: qui la finestra di Android
# coprirebbe la Home a ogni installazione pulita.
foreach ($p in 'RECORD_AUDIO', 'READ_CALENDAR') {
    Adb shell pm grant dev.casa "android.permission.$p" 2>$null | Out-Null
}
# Il cartello « schermo intero » di Android si prenderebbe il primo tocco.
Adb shell settings put secure immersive_mode_confirmations confirmed | Out-Null
Adb shell am force-stop dev.casa | Out-Null
Adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n dev.casa/.MainActivity | Out-Null
Write-Host "Assistente Home e' aperta sull'emulatore $emu" -ForegroundColor Green

if ($Foto) {
    Start-Sleep -Seconds 5
    # exec-out e non shell: shell converte i fine riga e il PNG esce rotto.
    $p = Start-Process $adb -ArgumentList "-s $emu exec-out screencap -p" `
        -RedirectStandardOutput $Foto -NoNewWindow -Wait -PassThru
    Write-Host "Cattura: $Foto"
}
