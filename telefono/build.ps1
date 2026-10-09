# Compila Casa per il telefono senza Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
#
# La stessa catena di tablet\build.ps1, con due differenze che contano:
# si compila contro android-36 (il telefono e' recente, e con un targetSdk
# vecchio Android 14+ rifiuta l'installazione o la riempie di avvisi), ma il
# minSdk resta 24 - lo stesso Android del tablet, cosi' l'app gira anche su un
# telefono vecchio di casa.
#
#   .\build.ps1                 compila
#   .\build.ps1 -Install        compila e installa sul telefono collegato
#   .\build.ps1 -Install -Serial R58M...   ...su quel telefono

param(
    [switch]$Install,
    [string]$Serial,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

# Vedi tablet\build.ps1: gli strumenti scrivono su stderr anche quando va bene.
function Invoke-Tollerante {
    param([scriptblock]$Comando)
    $prima = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $Comando | Out-Host } finally { $ErrorActionPreference = $prima }
    return $LASTEXITCODE
}

$root = $PSScriptRoot
$out  = Join-Path $root 'build'
$adb  = Join-Path $root '..\tools\platform-tools\adb.exe'
. (Join-Path $root '..\tools\dispositivo.ps1')

$PACCHETTO  = 'dev.casa.telefono'
$API_MINIMA = 24
$API        = 36

# --- individuazione degli strumenti ---------------------------------------
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path $sdk)) { throw "SDK Android non trovato in '$sdk'. Imposta ANDROID_HOME." }

$buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
    Sort-Object { [version]($_.Name -replace '[^0-9.].*$','') } | Select-Object -Last 1
if (-not $buildTools) { throw "Nessun build-tools nell'SDK." }

$androidJar = Join-Path $sdk "platforms\android-$API\android.jar"
if (-not (Test-Path $androidJar)) {
    throw "Manca la platform android-$API. Installala con:`n  $sdk\cmdline-tools\latest\bin\sdkmanager.bat ""platforms;android-$API"""
}

$aapt2     = Join-Path $buildTools.FullName 'aapt2.exe'
$d8        = Join-Path $buildTools.FullName 'd8.bat'
$zipalign  = Join-Path $buildTools.FullName 'zipalign.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'

$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $probe = @()
    $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($javaExe) {
        $real = (Get-Item $javaExe).Target
        if ($real) { $probe += (Split-Path $real -Parent) }
        $probe += (Split-Path $javaExe -Parent)
    }
    if ($env:JAVA_HOME) { $probe += (Join-Path $env:JAVA_HOME 'bin') }
    $probe += (Get-ChildItem 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
               Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' })
    $keytool = $probe |
        Where-Object { $_ -and (Test-Path (Join-Path $_ 'keytool.exe')) } |
        Select-Object -First 1 |
        ForEach-Object { Join-Path $_ 'keytool.exe' }
}

Write-Host "build-tools : $($buildTools.Name)"
Write-Host "platform    : android-$API (minSdk $API_MINIMA)"

# --- pulizia ---------------------------------------------------------------
if ($Clean -and (Test-Path $out)) { Remove-Item $out -Recurse -Force }
foreach ($d in @('res','gen','classes','dex','apk')) {
    New-Item -ItemType Directory -Force (Join-Path $out $d) | Out-Null
}
# Le classi di una build vecchia non devono finire nel jar di questa.
Get-ChildItem (Join-Path $out 'classes') -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force

# --- 1. risorse ------------------------------------------------------------
Write-Host "`n[1/6] compilazione risorse"
$resFiles = Get-ChildItem (Join-Path $root 'res') -Recurse -File
& $aapt2 compile -o (Join-Path $out 'res') $resFiles.FullName
if ($LASTEXITCODE) { throw "aapt2 compile fallito" }

Write-Host "[2/6] link risorse e manifest"
$flat = Get-ChildItem (Join-Path $out 'res') -Filter *.flat -File
$baseApk = Join-Path $out 'apk\base.apk'
& $aapt2 link `
    -I $androidJar `
    --manifest (Join-Path $root 'AndroidManifest.xml') `
    --java (Join-Path $out 'gen') `
    --min-sdk-version $API_MINIMA `
    --target-sdk-version $API `
    -o $baseApk `
    $flat.FullName
if ($LASTEXITCODE) { throw "aapt2 link fallito" }

# --- 2. java ---------------------------------------------------------------
Write-Host "[3/6] compilazione Java"
$sources = @()
$sources += (Get-ChildItem (Join-Path $root 'src') -Recurse -Filter *.java).FullName
$sources += (Get-ChildItem (Join-Path $out 'gen') -Recurse -Filter *.java -ErrorAction SilentlyContinue).FullName
$srcList = Join-Path $out 'sources.txt'
$quoted = $sources | ForEach-Object { '"' + ($_ -replace '\\', '\\') + '"' }
[System.IO.File]::WriteAllLines($srcList, $quoted, (New-Object System.Text.UTF8Encoding($false)))

& javac -source 8 -target 8 -nowarn -encoding UTF-8 `
    -bootclasspath $androidJar -classpath $androidJar `
    -d (Join-Path $out 'classes') "@$srcList"
if ($LASTEXITCODE) { throw "javac fallito" }

# --- 3. dex ----------------------------------------------------------------
Write-Host "[4/6] conversione in DEX"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Join-Path $out 'classes.jar'
if (Test-Path $jar) { Remove-Item $jar }
[System.IO.Compression.ZipFile]::CreateFromDirectory((Join-Path $out 'classes'), $jar)
& $d8 --min-api $API_MINIMA --lib $androidJar --output (Join-Path $out 'dex') $jar
if ($LASTEXITCODE) { throw "d8 fallito" }

# --- 4. impacchettamento ---------------------------------------------------
Write-Host "[5/6] inserimento classes.dex nell'APK"
$zip = [System.IO.Compression.ZipFile]::Open($baseApk, 'Update')
try {
    $existing = $zip.GetEntry('classes.dex')
    if ($existing) { $existing.Delete() }
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, (Join-Path $out 'dex\classes.dex'), 'classes.dex')
} finally { $zip.Dispose() }

$aligned = Join-Path $out 'apk\aligned.apk'
& $zipalign -f 4 $baseApk $aligned
if ($LASTEXITCODE) { throw "zipalign fallito" }

# --- 5. firma --------------------------------------------------------------
Write-Host "[6/6] firma"
$ks = Join-Path $root 'keystore\telefono.jks'
if (-not (Test-Path $ks)) {
    if (-not $keytool) { throw "keytool non trovato: serve un JDK per creare il keystore di firma." }
    New-Item -ItemType Directory -Force (Split-Path $ks -Parent) | Out-Null
    Write-Host "      genero un keystore di sviluppo"
    $esito = Invoke-Tollerante { & $keytool -genkeypair -keystore $ks -alias telefono -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass casadev -keypass casadev -dname 'CN=Casa Telefono, O=Personale' | Out-Null }
    if ($esito) { throw "keytool fallito" }
}

$final = Join-Path $out 'CasaTelefono.apk'
$esito = Invoke-Tollerante { & $apksigner sign --ks $ks --ks-key-alias telefono --ks-pass pass:casadev --key-pass pass:casadev `
    --v1-signing-enabled true --v2-signing-enabled true `
    --min-sdk-version $API_MINIMA --out $final $aligned }
if ($esito) { throw "apksigner fallito" }

$size = [math]::Round((Get-Item $final).Length / 1KB, 1)
Write-Host "`nFatto: $final ($size KB)" -ForegroundColor Green

# --- installazione ---------------------------------------------------------
if ($Install) {
    # Il telefono, non « il primo dispositivo »: sullo stesso adb possono stare
    # anche l'E960 e l'SM-T210, e installare questa app su un tablet non
    # darebbe errori - solo un'icona in piu' dove non serve.
    $serialeTelefono = Get-TelefonoDelProgetto -Adb $adb -Serial $Serial
    if (-not $serialeTelefono) { exit 1 }
    Write-Host "`nInstallazione sul telefono $serialeTelefono"
    $esito = Invoke-Tollerante { & $adb -s $serialeTelefono install -r $final }
    if ($esito) { throw "installazione fallita" }
    & $adb -s $serialeTelefono shell am start -n "$PACCHETTO/.Principale" | Out-Null
}
