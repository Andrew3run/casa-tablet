# Compila Casa senza Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
#
# Stessa catena del primo progetto, con android-24 al posto di android-19.
# Qui pero' il d8 fa il desugaring (minSdk 24), quindi lambda e metodi di
# default sono utilizzabili: sul T210 non lo erano.
#
#   .\build.ps1                 compila
#   .\build.ps1 -Install        compila e installa sul tablet giusto
#   .\build.ps1 -Install -SetHome  ...e apre la scelta della Home
#   .\build.ps1 -DeviceOwner    imposta Casa come device owner (zero account!)

param(
    [switch]$Install,
    [switch]$SetHome,   # non $Home: e' una variabile automatica di PowerShell
    [switch]$DeviceOwner,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

# keytool, apksigner e adb scrivono avvisi e avanzamento su stderr anche quando
# finiscono bene. Con 'Stop', PowerShell 5.1 li trasformerebbe in errori
# terminanti. Qui la preferenza si abbassa per la sola chiamata, e a decidere
# resta il codice di uscita. Out-Host tiene le righe fuori dalla pipeline:
# senza, la funzione restituirebbe testo + codice, cioe' un array.
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

$PACCHETTO = 'dev.casa'
$ADMIN     = "$PACCHETTO/.AdminReceiver"
$API       = 24

# --- device owner: non compila niente, esce subito ------------------------
if ($DeviceOwner) {
    $serial = Get-TabletDelProgetto -Adb $adb
    if (-not $serial) { exit 1 }

    $conti = (& $adb -s $serial shell "dumpsys account | grep -c 'Account {'") -join ''
    if ($conti.Trim() -ne '0') {
        Write-Warning "Ci sono $($conti.Trim()) account sul tablet. Il device owner si imposta solo a zero account."
        Write-Host "Impostazioni -> Account -> rimuovili, poi rilancia. Si rimettono dopo." -ForegroundColor Yellow
        exit 1
    }

    Write-Host "Imposto $ADMIN come device owner"
    $esito = (& $adb -s $serial shell "dpm set-device-owner $ADMIN") -join "`n"
    Write-Host $esito
    if ($esito -notmatch 'Success') { throw "device owner non impostato" }
    Write-Host "Fatto. Ora puoi rimettere l'account Google." -ForegroundColor Green
    exit 0
}

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

# keytool serve solo alla prima build, per creare il keystore. Non e' nel PATH:
# il javapath di Oracle espone java.exe ma non il resto del JDK.
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
Write-Host "platform    : android-$API (compilare contro il $API impedisce di chiamare API che qui non esistono)"

# --- pulizia ---------------------------------------------------------------
if ($Clean -and (Test-Path $out)) { Remove-Item $out -Recurse -Force }
foreach ($d in @('res','gen','classes','dex','apk')) {
    New-Item -ItemType Directory -Force (Join-Path $out $d) | Out-Null
}

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
    --min-sdk-version $API `
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
# Argfile di javac: niente BOM (verrebbe letto come parte del primo percorso) e
# backslash raddoppiati dentro le virgolette, dove javac li tratta da escape.
$quoted = $sources | ForEach-Object { '"' + ($_ -replace '\\', '\\') + '"' }
[System.IO.File]::WriteAllLines($srcList, $quoted, (New-Object System.Text.UTF8Encoding($false)))

# -source/-target 8 invece di --release 8: --release imporrebbe le librerie del
# JDK, mentre qui la piattaforma e' android.jar e basta.
& javac -source 8 -target 8 -nowarn -encoding UTF-8 `
    -bootclasspath $androidJar -classpath $androidJar `
    -d (Join-Path $out 'classes') "@$srcList"
if ($LASTEXITCODE) { throw "javac fallito" }

# --- 3. dex ----------------------------------------------------------------
Write-Host "[4/6] conversione in DEX"
# Le classi entrano in d8 dentro un jar invece che una per una: ogni classe
# interna e anonima e' un file a se', e tutte insieme sfondano gli 8191
# caratteri di Windows. L'argfile "@lista" con d8 non funziona (non toglie le
# virgolette). Il jar lo fa .NET: "jar" del JDK non e' nel PATH, e un jar e'
# uno zip.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Join-Path $out 'classes.jar'
if (Test-Path $jar) { Remove-Item $jar }
[System.IO.Compression.ZipFile]::CreateFromDirectory((Join-Path $out 'classes'), $jar)
& $d8 --min-api $API --lib $androidJar --output (Join-Path $out 'dex') $jar
if ($LASTEXITCODE) { throw "d8 fallito" }

# --- 4. impacchettamento ---------------------------------------------------
Write-Host "[5/6] inserimento classes.dex e binari nativi nell'APK"
$zip = [System.IO.Compression.ZipFile]::Open($baseApk, 'Update')
try {
    $existing = $zip.GetEntry('classes.dex')
    if ($existing) { $existing.Delete() }
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, (Join-Path $out 'dex\classes.dex'), 'classes.dex')

    # jniLibs -> lib/<abi>/. Dentro non c'e' nessuna libreria: c'e' il demone
    # go-librespot, che e' un eseguibile ELF (vedi jniLibs\PROVENIENZA.md). Sta
    # li' perche' l'installer estrae SOLO quello che trova sotto lib/<abi>/ in
    # nativeLibraryDir, e lo estrae con il bit di esecuzione: un file messo in
    # assets/ arriverebbe senza, e su un'app non si puo' chmod.
    #
    # Compresso e non STORED: senza extractNativeLibs="false" nel manifest -
    # e su API 24 il valore di fabbrica e' true - l'installer lo estrae
    # comunque, quindi comprimerlo costa solo qualche secondo di installazione
    # e risparmia una decina di megabyte di APK.
    $jni = Join-Path $root 'jniLibs'
    if (Test-Path $jni) {
        foreach ($abi in Get-ChildItem $jni -Directory) {
            foreach ($f in Get-ChildItem $abi.FullName -File) {
                $dentro = "lib/$($abi.Name)/$($f.Name)"
                $vecchia = $zip.GetEntry($dentro)
                if ($vecchia) { $vecchia.Delete() }
                [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                    $zip, $f.FullName, $dentro)
                Write-Host ("      {0} ({1:N1} MB)" -f $dentro, ($f.Length / 1MB))
            }
        }
    }

    # assets/ -> assets/<percorso>. Dentro ci stanno le registrazioni di serie
    # della parola di attivazione (vedi Campioni.portaIBase).
    #
    # A MANO E NON CON "aapt2 link -A", che pure esiste. Su Windows aapt2
    # scrive i nomi dentro lo zip con le barre ROVESCE - assets/parola\si\
    # si-base-1.wav - mentre AssetManager cerca assets/parola/si/... Risultato:
    # gli asset erano nell'APK, pesavano trecento kilobyte, e list() non ne
    # trovava nemmeno uno. Qui il separatore lo scriviamo noi.
    #
    # A differenza di lib/<abi>/ non serve nessun bit di esecuzione: un asset
    # non viene estratto, si legge dall'APK dove sta.
    $assets = Join-Path $root 'assets'
    if (Test-Path $assets) {
        $quanti = 0
        foreach ($f in Get-ChildItem $assets -Recurse -File) {
            $relativo = $f.FullName.Substring($assets.Length + 1).Replace('\', '/')
            $dentro = "assets/$relativo"
            $vecchia = $zip.GetEntry($dentro)
            if ($vecchia) { $vecchia.Delete() }
            [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $zip, $f.FullName, $dentro)
            $quanti++
        }
        Write-Host ("      assets: {0} file" -f $quanti)
    }
} finally { $zip.Dispose() }

$aligned = Join-Path $out 'apk\aligned.apk'
& $zipalign -f 4 $baseApk $aligned
if ($LASTEXITCODE) { throw "zipalign fallito" }

# --- 5. firma --------------------------------------------------------------
Write-Host "[6/6] firma"
$ks = Join-Path $root 'keystore\casa.jks'
if (-not (Test-Path $ks)) {
    if (-not $keytool) { throw "keytool non trovato: serve un JDK per creare il keystore di firma." }
    Write-Host "      genero un keystore di sviluppo"
    # Su una copia appena scaricata la cartella non c'e', e keytool non la crea.
    New-Item -ItemType Directory -Force (Split-Path $ks -Parent) | Out-Null
    $esito = Invoke-Tollerante { & $keytool -genkeypair -keystore $ks -alias casa -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass casadev -keypass casadev -dname 'CN=Casa, O=Personale' | Out-Null }
    if ($esito) { throw "keytool fallito" }
}

$final = Join-Path $out 'Casa.apk'
$esito = Invoke-Tollerante { & $apksigner sign --ks $ks --ks-pass pass:casadev --key-pass pass:casadev `
    --v1-signing-enabled true --v2-signing-enabled true `
    --min-sdk-version $API --out $final $aligned }
if ($esito) { throw "apksigner fallito" }

$size = [math]::Round((Get-Item $final).Length / 1KB, 1)
Write-Host "`nFatto: $final ($size KB)" -ForegroundColor Green

# --- installazione ---------------------------------------------------------
if ($Install) {
    # Il tablet giusto, non il primo: con l'SM-T210 dell'altro progetto attaccato
    # allo stesso PC, "il primo" e' una lotteria che si perde in silenzio.
    $serial = Get-TabletDelProgetto -Adb $adb
    if (-not $serial) {
        Write-Warning "Nessun E960 autorizzato. Attiva il debug USB e accetta la richiesta sul tablet."
        exit 1
    }
    Write-Host "`nInstallazione sul tablet $serial"
    $esito = Invoke-Tollerante { & $adb -s $serial install -r $final }
    if ($esito) { throw "installazione fallita" }

    # Compilare in nativo, subito. Da Android 7 "install" lascia l'app in
    # interpret-only e rimanda il lavoro a BackgroundDexOptService, che su
    # questo tablet non e' mai girato - e adesso che il Play Store non c'e'
    # piu' non ha nemmeno chi lo solleciti. Senza questa riga ogni build
    # tornerebbe a girare interpretata su un Cortex-A7 a 1,3 GHz.
    #
    # Casa e' piccola e ci mette pochi secondi. Per le app grosse - Spotify ci
    # ha messo nove minuti - c'e' tools\compila.ps1.
    Write-Host "Compilo in nativo..."
    & $adb -s $serial shell "cmd package compile -m speed -f $PACCHETTO" | Out-Null

    if ($SetHome) {
        Write-Host "Apro la scelta della Home: seleziona Assistente Home e conferma 'Sempre'."
        & $adb -s $serial shell am start -a android.intent.action.MAIN -c android.intent.category.HOME | Out-Null
    } else {
        # CON L'INTENTO DI HOME, NON CON "-n dev.casa/.MainActivity".
        #
        # I due sembrano la stessa cosa e non lo sono: "-n" apre l'Activity
        # come farebbe un'app qualunque, e il task che ne esce NON e' il task
        # della Home. Risultato: Casa compare fra le applicazioni recenti, dove
        # una Home non deve stare - e ci resta finche' qualcuno non la scaccia.
        #
        # Cosi' invece si chiede al sistema « portami alla Home », che e'
        # esattamente quello che fa il tasto: se Casa e' la Home preferita si
        # apre lei, nel task giusto. Ed e' anche una prova in piu' che la
        # registrazione come Home permanente e' ancora al suo posto.
        & $adb -s $serial shell am start -a android.intent.action.MAIN -c android.intent.category.HOME | Out-Null
    }
}
