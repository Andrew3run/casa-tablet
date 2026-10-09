# Mette go-librespot in tablet\jniLibs\armeabi-v7a\libgolibrespot.so.
#
# Il demone che suona Spotify dentro Casa non e' nella repository: e' un
# programma GPLv3 di altri, e si prende dalla release pubblica di
# Android-LibreThing, che lo compila per Android con le due toppe che servono
# (vedi tablet\jniLibs\PROVENIENZA.md). Questo script scarica quell'APK, ne
# tira fuori il binario e controlla che sia proprio quello provato con Casa.
#
#   .\tools\prendi-librespot.ps1

$ErrorActionPreference = 'Stop'

$url      = 'https://github.com/SEKY443/Android-LibreThing/releases/download/1.3.1/app-armeabi-v7a-release.apk'
$dentro   = 'lib/armeabi-v7a/libgolibrespot.so'
$impronta = '71DA8FC03EBEC110E5454BA33EC363EF1121432AD1E6DA043D9F68405DA304C7'

$root    = Split-Path $PSScriptRoot -Parent
$destDir = Join-Path $root 'tablet\jniLibs\armeabi-v7a'
$dest    = Join-Path $destDir 'libgolibrespot.so'

if ((Test-Path $dest) -and (Get-FileHash $dest -Algorithm SHA256).Hash -eq $impronta) {
    Write-Host "go-librespot c'e' gia'." -ForegroundColor Green
    exit 0
}

$tmp = Join-Path $env:TEMP ("librespot-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $tmp | Out-Null
try {
    $apk = Join-Path $tmp 'librething.apk'
    Write-Host "Scarico $url"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $apk -UseBasicParsing

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $voce = $zip.GetEntry($dentro)
        if (-not $voce) { throw "Nell'APK non c'e' $dentro." }
        $estratto = Join-Path $tmp 'libgolibrespot.so'
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($voce, $estratto, $true)
    } finally { $zip.Dispose() }

    $hash = (Get-FileHash $estratto -Algorithm SHA256).Hash
    if ($hash -ne $impronta) {
        throw "L'impronta non torna ($hash): non lo copio. La release potrebbe essere cambiata."
    }
    New-Item -ItemType Directory -Force $destDir | Out-Null
    Copy-Item $estratto $dest -Force
    Write-Host "Fatto: $dest" -ForegroundColor Green
} finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
