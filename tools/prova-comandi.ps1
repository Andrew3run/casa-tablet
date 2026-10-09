# Fa dire a Casa una batteria di frasi, e guarda cosa risponde.
#
# I comandi sono regole scritte a mano, e le regole a mano si rompono nel modo
# piu' silenzioso che ci sia: una frase smette di cadere dove cadeva e finisce
# in un'altra regola, che fa un'altra cosa. E' successo davvero - « stop radio »
# accendeva la radio - e non lo dice nessun errore: bisogna dirle, le frasi, e
# guardare la risposta.
#
# Questo le dice tutte in venti secondi, senza parlare: passa da
# `dev.casa.DI`, che salta il riconoscitore ed entra dritto in Comandi, quindi
# prova esattamente la catena che gira quando qualcuno parla davvero.
#
#   .\prova-comandi.ps1                 la batteria che non lascia strascichi
#   .\prova-comandi.ps1 -ConLaRadio     anche quelle che accendono davvero
#   .\prova-comandi.ps1 -Frase 'stop'   una sola, e dice cosa risponde
#
# LA BATTERIA DI SERIE NON LASCIA STRASCICHI, ed e' una scelta: le frasi che
# spengono si provano a impianto gia' spento - la risposta « la radio era gia'
# spenta » dimostra lo stesso che la frase e' arrivata a provaSpegni e non alla
# regola che accende - e il volume torna dov'era perche' si alza e si riabbassa.
# Con -ConLaRadio invece la radio suona per un paio di secondi.

param(
    [switch]$ConLaRadio,
    [string]$Frase
)

$ErrorActionPreference = 'Stop'

$adb = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
. (Join-Path $PSScriptRoot 'dispositivo.ps1')

$serial = Get-TabletDelProgetto -Adb $adb
if (-not $serial) {
    Write-Host "Nessun tablet di questo progetto collegato." -ForegroundColor Yellow
    exit 1
}

# Le virgolette SINGOLE attorno alla frase non sono facoltative: con le doppie,
# `am` spezza la frase sugli spazi e si prende il primo pezzo per un nome di
# pacchetto. Costata mezz'ora la prima volta.
function Manda {
    param([string]$Testo)
    & $adb -s $serial shell "am broadcast -a dev.casa.DI --es frase '$Testo'" | Out-Null
}

# frase, e cosa ci si aspetta di sentire (espressione regolare, senza accenti
# perche' Casa risponde senza)
$batteria = @(
    @{ frase = 'che ore sono';        attesa = 'Sono le|E'' l''una|mezzanotte' },
    @{ frase = 'che giorno e';        attesa = 'Oggi e' },
    @{ frase = 'cosa sta suonando';   attesa = 'Non sta suonando|Sta suonando|pausa' },

    # Il cuore della faccenda: tutte queste devono FERMARE, e nessuna deve
    # rispondere « Metto ».
    @{ frase = 'stop radio';          attesa = 'gia'' spenta|Non c''era la radio|Radio spenta|Spengo la radio' },
    @{ frase = 'spegni la radio';     attesa = 'gia'' spenta|Non c''era la radio|Radio spenta|Spengo la radio' },
    @{ frase = 'ferma la radio';      attesa = 'gia'' spenta|Non c''era la radio|Radio spenta|Spengo la radio' },
    @{ frase = 'basta radio';         attesa = 'gia'' spenta|Non c''era la radio|Radio spenta|Spengo la radio' },
    @{ frase = 'togli la radio';      attesa = 'gia'' spenta|Non c''era la radio|Radio spenta|Spengo la radio' },
    @{ frase = 'chiudi la radio';     attesa = 'gia'' spenta|Non c''era la radio|Radio spenta|Spengo la radio' },
    @{ frase = 'stoppa la musica';    attesa = 'niente in riproduzione|Non sta suonando|Musica ferma|Fatto' },
    @{ frase = 'basta';               attesa = 'niente in funzione|Non stava suonando|gia'' tutto fermo|Silenzio|Fatto' },
    @{ frase = 'zitto';               attesa = 'niente in funzione|Non stava suonando|gia'' tutto fermo|Silenzio|Fatto' },
    @{ frase = 'silenzio';            attesa = 'niente in funzione|Non stava suonando|gia'' tutto fermo|Silenzio|Fatto' },

    # Il volume si alza e si riabbassa: alla fine e' dov'era. Non deve
    # rispondere a voce, deve solo scrivere « Volume NN% ».
    @{ frase = 'alza il volume';      attesa = '^Volume \d+%$' },
    @{ frase = 'abbassa il volume';   attesa = '^Volume \d+%$' },

    # Queste non fanno niente, ma devono cadere nella regola giusta.
    @{ frase = 'cosa sai fare';       attesa = 'Posso dirti' },
    @{ frase = 'come ti chiami';      attesa = 'Home' },
    @{ frase = 'annulla il timer';    attesa = 'Timer annullato|Non c''era nessun timer' }
)

if ($ConLaRadio) {
    $batteria += @{ frase = 'metti la radio';     attesa = 'Metto' }
    $batteria += @{ frase = 'cambia stazione';    attesa = 'Metto|Cambio stazione' }
    $batteria += @{ frase = 'spegni la radio';    attesa = 'Radio spenta|Spengo la radio' }
}

if ($Frase) { $batteria = @( @{ frase = $Frase; attesa = '' } ) }

Write-Host ""
Write-Host "Assistente Home: $($batteria.Count) frasi" -ForegroundColor Cyan
if (-not $ConLaRadio -and -not $Frase) {
    Write-Host "(niente suona e niente resta acceso: -ConLaRadio per provare anche quelle)" -ForegroundColor DarkGray
}
Write-Host ""

& $adb -s $serial logcat -c 2>$null | Out-Null

# Un marchio prima di tutto, e non ci si fida di « logcat -c ».
#
# Su questo ROM svuotare il registro ogni tanto non riesce (« failed to clear »),
# e allora le prove di mezz'ora fa sarebbero ancora li': accoppiando per ordine
# si leggerebbero le risposte VECCHIE e si direbbe che va tutto bene. Con il
# marchio si legge solo quello che viene dopo, e il caso peggiore diventa una
# riga in meno invece di un esito sbagliato.
$marchio = "controllo " + (Get-Random -Minimum 100000 -Maximum 999999)
Manda $marchio
Start-Sleep -Milliseconds 300

foreach ($p in $batteria) {
    Manda $p.frase
    Start-Sleep -Milliseconds 450
}
# L'ultima risposta puo' arrivare dopo il broadcast: la ricerca in rete e il
# demone di Spotify rispondono quando hanno finito, non quando glielo si chiede.
Start-Sleep -Milliseconds 800

$registro = (& $adb -s $serial logcat -d -s Casa:I) -join "`n" -split "`n"

# Ogni « prova: » apre un blocco; la prima « dice: » o « fa: » dopo di lui e' la
# sua risposta. Si accoppia per posizione e non per contenuto, perche' la stessa
# frase puo' comparire due volte con due risposte diverse - ed e' proprio il
# caso di « spegni la radio » con -ConLaRadio.
$risposte = @{}
$corrente = $null
$dopoIlMarchio = $false
foreach ($riga in $registro) {
    if (-not $dopoIlMarchio) {
        if ($riga -match [regex]::Escape($marchio)) { $dopoIlMarchio = $true }
        continue
    }
    if ($riga -match 'prova: (.+)$') {
        $corrente = $matches[1].Trim()
        continue
    }
    if ($corrente -and $riga -match '(?:dice|fa): (.+)$') {
        if (-not $risposte.ContainsKey($corrente)) { $risposte[$corrente] = @() }
        $risposte[$corrente] += $matches[1].Trim()
        $corrente = $null
    }
}

$usate = @{}
$storte = 0
foreach ($p in $batteria) {
    $detto = '(niente)'
    if ($risposte.ContainsKey($p.frase)) {
        $quale = 0
        if ($usate.ContainsKey($p.frase)) { $quale = $usate[$p.frase] }
        $elenco = @($risposte[$p.frase])
        if ($quale -lt $elenco.Count) { $detto = $elenco[$quale] }
        $usate[$p.frase] = $quale + 1
    }

    $esito = 'OK   '
    $colore = 'Green'
    if ($p.attesa -and $detto -notmatch $p.attesa) { $esito = 'STORTA'; $colore = 'Red'; $storte++ }
    elseif (-not $p.attesa) { $esito = '     '; $colore = 'Gray' }

    Write-Host ("  {0,-6} " -f $esito) -ForegroundColor $colore -NoNewline
    Write-Host ("{0,-24}" -f $p.frase) -NoNewline
    Write-Host " -> $detto" -ForegroundColor DarkGray
}

Write-Host ""
if ($storte -eq 0) {
    Write-Host "Tutte dove dovevano." -ForegroundColor Green
} else {
    Write-Host "$storte fuori posto. La regola che le prende sta in Comandi.esegui()," -ForegroundColor Yellow
    Write-Host "e fra due regole che prendono la stessa frase vince quella scritta prima." -ForegroundColor Yellow
}
Write-Host ""
