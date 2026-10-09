# Il tablet di QUESTO progetto, e nessun altro.
#
# L'E960 non si puo' riconoscere dal seriale: il ROM cinese lascia il
# segnaposto MediaTek 0123456789ABCDEF. Nemmeno dal modello, che e' la parola
# "tablet". L'unica riga che lo identifica e' l'identificativo della build,
# E960V1.4_HXL_11_EN_BOE9881_WXGA_DHTP_20191126.
#
# Serve perche' l'altro progetto (l'SM-T210, nella cartella accanto) usa lo
# stesso adb e lo stesso cavo: senza questo controllo, un -Install lanciato
# dalla cartella sbagliata finisce sul tablet sbagliato.

$ProgettoBuild = 'E960'
$ProgettoNome  = 'DUODUOGO E960 (MT6580, Android 7.0)'

function Get-TabletDelProgetto {
    param([string]$Adb)

    $righe = & $Adb devices | Select-String -Pattern '\tdevice$'
    if (-not $righe) { return $null }

    $seriali = $righe | ForEach-Object { ($_ -split "`t")[0].Trim() }
    $altri = @()

    # Un altro tablet, scelto apposta: il seriale in CASA_TABLET (lo dice
    # « adb devices »). Serve a chi usa Casa su un tablet che non e' l'E960;
    # senza, si resta sull'E960 e su nient'altro.
    if ($env:CASA_TABLET) {
        if ($seriali -contains $env:CASA_TABLET) { return $env:CASA_TABLET }
        Write-Warning "CASA_TABLET=$($env:CASA_TABLET), ma quel dispositivo non e' collegato (o non e' autorizzato)."
        return $null
    }

    foreach ($s in $seriali) {
        $build = (& $Adb -s $s shell getprop ro.build.display.id 2>$null)
        $build = (($build -join '') -replace '\s', '')
        if ($build -like "$ProgettoBuild*") { return $s }
        $modello = (& $Adb -s $s shell getprop ro.product.model 2>$null)
        $altri += "$s ($(($modello -join '') -replace '\s',''))"
    }

    Write-Host ""
    Write-Warning "Questo e' il progetto $ProgettoNome e vuole una build $ProgettoBuild*."
    Write-Host "Collegati invece: $($altri -join ', ')"
    Write-Host "L'SM-T210 ha una cartella sua: '..\Tablet SM-T210'." -ForegroundColor Yellow
    return $null
}

# Il telefono di Casa (telefono\build.ps1).
#
# Un telefono non ha una riga che lo riconosca come « il nostro », quindi si va
# per esclusione: via l'E960 e l'SM-T210, e se resta un solo dispositivo e'
# lui. Con due telefoni attaccati non si tira a indovinare: si chiede -Serial.
function Get-TelefonoDelProgetto {
    param([string]$Adb, [string]$Serial)

    $righe = & $Adb devices | Select-String -Pattern '\tdevice$'
    $seriali = @($righe | ForEach-Object { ($_ -split "`t")[0].Trim() })

    if ($Serial) {
        if ($seriali -contains $Serial) { return $Serial }
        Write-Warning "Il dispositivo $Serial non e' collegato (o non e' autorizzato)."
        return $null
    }

    $telefoni = @()
    foreach ($s in $seriali) {
        $build = ((& $Adb -s $s shell getprop ro.build.display.id 2>$null) -join '') -replace '\s', ''
        $modello = ((& $Adb -s $s shell getprop ro.product.model 2>$null) -join '') -replace '\s', ''
        if ($build -like "$ProgettoBuild*") { continue }
        if ($modello -eq 'SM-T210') { continue }
        $telefoni += [pscustomobject]@{ Seriale = $s; Modello = $modello }
    }

    if ($telefoni.Count -eq 1) { return $telefoni[0].Seriale }

    Write-Host ""
    if ($telefoni.Count -eq 0) {
        Write-Warning "Nessun telefono collegato: attiva il debug USB e accetta la richiesta sul telefono."
    } else {
        Write-Warning "Piu' di un telefono collegato. Scegline uno con -Serial:"
        $telefoni | ForEach-Object { Write-Host "  $($_.Seriale)  $($_.Modello)" }
    }
    return $null
}
