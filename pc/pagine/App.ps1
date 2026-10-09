# Pagina "Le app": quali tessere si vedono nella sezione App del tablet.
#
# NON E' IL CASSETTO DELLE APPLICAZIONI. Il tablet non e' un telefono: e' un
# apparecchio che fa un elenco chiuso di cose, e chi ci passa davanti deve
# vedere quelle. L'elenco lo decide chi allestisce il tablet - cioe' qui - e non
# il sistema elencando tutto quello che dichiara di essere lanciabile.
#
# Fino a ieri quell'elenco era cinque righe dentro SezioneApp.java: togliere il
# Meteo voleva dire avere il progetto, l'SDK e una ricompilazione. Adesso e' un
# file, e questa pagina lo scrive.
#
# LE DUE COLONNE. A sinistra c'e' quello che si vede sul tablet, nell'ordine in
# cui si vede. A destra c'e' tutto quello che sul tablet e' installato: si
# sceglie una riga e si porta a sinistra. Le due meta' rispondono a due domande
# diverse - "cosa mostro" e "cosa c'e'" - e mescolarle vorrebbe dire un elenco
# solo con delle spunte, dove l'ordine non si vede piu'.
#
# QUELLO CHE NON E' UN'APP. La tessera del Meteo non apre niente: apre una
# pagina di Casa. Sta in elenco insieme alle altre perche' da qui si guarda come
# si guarda qualunque altra cosa - si tocca una tessera e si apre una schermata
# - e chi ci passa davanti non deve sapere che una di queste abita dentro Casa e
# le altre no. Nel file si scrive "@meteo", e il tasto qui sotto la rimette
# senza doverselo ricordare.

$pagApp = $pagine[$PAG_APP]

$script:appDelTablet = @()

$pagApp.Controls.Add((Nuova-Intestazione $PAG_APP))

# ---- quelle in elenco --------------------------------------------------------

$boxInElenco = Nuovo-Pannello 0 44 460 500
$pagApp.Controls.Add($boxInElenco)

$boxInElenco.Titolo = 'QUELLO CHE SI VEDE SUL TABLET'

$grigliaApp = Nuova-Griglia 16 36 428 300 @(@('nome', 140), @('pacchetto', 284))
$boxInElenco.Controls.Add($grigliaApp)

$tAppSu = Nuovo-Tasto 'Su' 16 346 104 30 $cTenue
$tAppSu.Icona = 'su'
$boxInElenco.Controls.Add($tAppSu)
$tAppGiu = Nuovo-Tasto 'Giu''' 128 346 104 30 $cTenue
$tAppGiu.Icona = 'giu'
$boxInElenco.Controls.Add($tAppGiu)
$tAppRinomina = Nuovo-Tasto 'Rinomina' 240 346 100 30 $cMedio
$boxInElenco.Controls.Add($tAppRinomina)
$tAppTogli = Nuovo-Tasto 'Togli' 348 346 96 30 $cRosso
$boxInElenco.Controls.Add($tAppTogli)

$tMeteo = Nuovo-Tasto 'Rimetti il Meteo (la pagina di Assistente Home)' 16 382 428 30 $cMedio
$boxInElenco.Controls.Add($tMeteo)

$boxInElenco.Controls.Add((Nuovo-Testo 16 412 428 66 @'
Le tessere non cambiano misura quando l'elenco si accorcia:
la larghezza resta quella di cinque colonne e le righe si
centrano. Se crescessero, togliere un'app sposterebbe tutte
le altre sotto il dito.
'@))

$tAppLeggi = Nuovo-Tasto 'Rileggi dal tablet' 16 466 210 30 $cMedio
$boxInElenco.Controls.Add($tAppLeggi)
$tAppManda = Tasto-Primario (Nuovo-Tasto 'Manda al tablet' 234 466 210 30 $cAzzurro) 'spunta'
$boxInElenco.Controls.Add($tAppManda)

# ---- quelle installate -------------------------------------------------------

$boxInstallate = Nuovo-Pannello 476 44 462 500
$pagApp.Controls.Add($boxInstallate)

$boxInstallate.Titolo = 'QUELLO CHE C''E'' SUL TABLET'

$eInstallate = Nuova-Etichetta '' 16 34 430 $cTenue $fTesto
$eInstallate.Height = 32
$eInstallate.Text = ("Le app che il tablet sa aprire. Sceglila e portala a sinistra: " +
                     "il nome si puo' cambiare dopo.")
$boxInstallate.Controls.Add($eInstallate)

$grigliaInstallate = Nuova-Griglia 16 70 430 300 @(@('pacchetto', 300), @('in elenco', 116))
$boxInstallate.Controls.Add($grigliaInstallate)

$tRileggiInstallate = Nuovo-Tasto 'Rileggi dal tablet' 16 380 210 30 $cMedio
$boxInstallate.Controls.Add($tRileggiInstallate)
$tAggiungiApp = Nuovo-Tasto 'Mettila in elenco' 234 380 212 30 $cAzzurro
$tAggiungiApp.Icona = 'avanti'
$boxInstallate.Controls.Add($tAggiungiApp)

$tScriviAMano = Nuovo-Tasto 'Aggiungi un pacchetto scritto a mano...' 16 416 430 30 $cTenue
$boxInstallate.Controls.Add($tScriviAMano)

$boxInstallate.Controls.Add((Nuovo-Testo 16 446 430 66 @'
Sul tablet si vedono solo le app INSTALLATE: una tessera di
un'app che manca non compare affatto. Una tessera spenta e'
un pulsante che non fa niente, e chi la preme la seconda
volta non si fida piu' nemmeno delle altre.
'@))

# =============================================================== il disegno ==

function Ridisegna-App {
    $grigliaApp.BeginUpdate()
    $grigliaApp.Items.Clear()
    foreach ($a in @($script:config.app)) {
        $riga = New-Object System.Windows.Forms.ListViewItem([string]$a.nome)
        [void]$riga.SubItems.Add([string]$a.pacchetto)
        if ([string]$a.pacchetto -eq '@meteo') { $riga.ForeColor = $script:cBlu }
        [void]$grigliaApp.Items.Add($riga)
    }
    $grigliaApp.EndUpdate()
    Segna-Installate
}

$script:ridisegna += { Ridisegna-App }

function Segna-Installate {
    $inElenco = @{}
    foreach ($a in @($script:config.app)) { $inElenco[[string]$a.pacchetto] = $true }
    $grigliaInstallate.BeginUpdate()
    $grigliaInstallate.Items.Clear()
    foreach ($p in $script:appDelTablet) {
        $riga = New-Object System.Windows.Forms.ListViewItem($p)
        [void]$riga.SubItems.Add($(if ($inElenco.ContainsKey($p)) { 'si' } else { '' }))
        if ($inElenco.ContainsKey($p)) { $riga.ForeColor = $script:cTenue }
        [void]$grigliaInstallate.Items.Add($riga)
    }
    $grigliaInstallate.EndUpdate()
}

function App-Scelta {
    if ($grigliaApp.SelectedIndices.Count -eq 0) { return -1 }
    return $grigliaApp.SelectedIndices[0]
}

<##
 # Le app che il tablet sa aprire.
 #
 # "pm list packages" da solo elenca anche le duecento cose senza schermata -
 # provider, servizi, pezzi di framework - e cercare Spotify in mezzo a quelle
 # e' peggio che scriverne il nome a mano. Qui si chiede invece chi risponde
 # all'intento LAUNCHER, che e' la stessa domanda che si fa il cassetto delle
 # applicazioni.
 #>
function Leggi-App-Installate {
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    $r = Sh 'pm query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 2>/dev/null | grep packageName'
    $pacchetti = @{}
    foreach ($riga in $r) {
        if ($riga -match 'packageName=([^\s]+)') { $pacchetti[$Matches[1]] = $true }
    }
    if ($pacchetti.Count -eq 0) {
        # Su API 24 "pm query-activities" non c'e' ancora: e' arrivato con
        # Android 9. Si ripiega su "pm list packages -3" piu' quelle di sistema
        # che ci interessano, che e' meno preciso ma esiste.
        Registra 'Il tablet non conosce "pm query-activities": uso l''elenco dei pacchetti.'
        $r = Sh 'pm list packages'
        foreach ($riga in $r) {
            if ($riga -match '^package:(.+)$') {
                $nome = $Matches[1].Trim()
                # Le librerie e i provider non si aprono: senza un filtro
                # qualunque, l'elenco sarebbe di duecento righe fra cui cercare.
                if ($nome -match '^(android$|com\.android\.(providers|inputdevices|location|internal|server|carrier|cts|se|backup|proxy|sharedstoragebackup|statementservice|managedprovisioning|externalstorage|htmlviewer|keychain|pacprocessor|printspooler|vpndialogs|wallpaperbackup)|com\.google\.android\.(gsf|ext|webview|packageinstaller|configupdater|partner|feedback)|com\.mediatek\.)') { continue }
                $pacchetti[$nome] = $true
            }
        }
    }
    $script:appDelTablet = @($pacchetti.Keys | Sort-Object)
    Registra "Sul tablet: $($script:appDelTablet.Count) pacchetti."
    Segna-Installate
}

# =============================================================== i tasti =====

function Salva-App($nuove) {
    $script:config.app = @($nuove)
    Config-Cambiata
}

$tAppTogli.Add_Click({
    $i = App-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una tessera.'; return }
    $tutte = @($script:config.app)
    $quale = $tutte[$i]
    if (-not (Chiedi ("Tolgo ""$($quale.nome)"" dalla sezione App del tablet?`n`n" +
                      "L'app resta installata: sparisce solo la tessera. Si rimette quando vuoi.") `
                     'Tolgo questa tessera?' 'Togli la tessera' -Pericolo)) { return }
    $restano = @()
    for ($x = 0; $x -lt $tutte.Count; $x++) { if ($x -ne $i) { $restano += $tutte[$x] } }
    Salva-App $restano
    Registra "Tolta la tessera di $($quale.nome). Mandala al tablet perche' sparisca davvero."
})

$tAppSu.Add_Click({
    $i = App-Scelta
    $a = Sposta-Voce $script:config.app $i ($i - 1)
    if ($null -eq $a) { return }
    Salva-App $a
    $grigliaApp.Items[$i - 1].Selected = $true
})

$tAppGiu.Add_Click({
    $i = App-Scelta
    $a = Sposta-Voce $script:config.app $i ($i + 1)
    if ($null -eq $a) { return }
    Salva-App $a
    $grigliaApp.Items[$i + 1].Selected = $true
})

<##
 # Il nome scritto qui e' quello che si legge a schermo, non quello che l'app
 # dichiara: "Impostazioni" e' meglio di "Settings", e "Meteo" e' meglio di
 # "@meteo".
 #>
$tAppRinomina.Add_Click({
    $i = App-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una tessera.'; return }
    $tutte = @($script:config.app)
    $nuovo = Chiedi-Testo 'Come si chiama sulla tessera?' ([string]$tutte[$i].nome)
    if ($null -eq $nuovo -or $nuovo.Trim().Length -eq 0) { return }
    $tutte[$i] = [ordered]@{ pacchetto = [string]$tutte[$i].pacchetto; nome = $nuovo.Trim() }
    Salva-App $tutte
})

$tMeteo.Add_Click({
    foreach ($a in @($script:config.app)) {
        if ([string]$a.pacchetto -eq '@meteo') { Registra 'Il Meteo c''e'' gia''.'; return }
    }
    Salva-App (@($script:config.app) + [ordered]@{ pacchetto = '@meteo'; nome = 'Meteo' })
    Registra 'Meteo rimesso in elenco.'
})

$tRileggiInstallate.Add_Click({ Leggi-App-Installate })

$tAggiungiApp.Add_Click({
    if ($grigliaInstallate.SelectedIndices.Count -eq 0) { Registra 'Scegli prima un pacchetto.'; return }
    $p = $script:appDelTablet[$grigliaInstallate.SelectedIndices[0]]
    foreach ($a in @($script:config.app)) {
        if ([string]$a.pacchetto -eq $p) { Registra "$p e' gia' in elenco."; return }
    }
    # Il nome di partenza e' l'ultimo pezzo del pacchetto, che nove volte su
    # dieci e' gia' il nome giusto: com.spotify.music -> Music. Si corregge
    # subito, ed e' meglio di una casella vuota.
    $suggerito = ($p -split '\.')[-1]
    $suggerito = $suggerito.Substring(0, 1).ToUpper() + $suggerito.Substring(1)
    $nome = Chiedi-Testo "Come si chiama sulla tessera?`n`n$p" $suggerito
    if ($null -eq $nome -or $nome.Trim().Length -eq 0) { return }
    Salva-App (@($script:config.app) + [ordered]@{ pacchetto = $p; nome = $nome.Trim() })
    Registra "Aggiunta $($nome.Trim())."
})

$tScriviAMano.Add_Click({
    $p = Chiedi-Testo 'Il nome del pacchetto, per esteso.' 'com.'
    if ($null -eq $p -or $p.Trim().Length -eq 0) { return }
    $nome = Chiedi-Testo 'Come si chiama sulla tessera?' ''
    if ($null -eq $nome -or $nome.Trim().Length -eq 0) { return }
    Salva-App (@($script:config.app) + [ordered]@{ pacchetto = $p.Trim(); nome = $nome.Trim() })
    Registra "Aggiunta $($nome.Trim()) ($($p.Trim())). Se non e' installata, sul tablet non si vedra'."
})

$tAppLeggi.Add_Click({ Leggi-Config-Dal-Tablet | Out-Null })
$tAppManda.Add_Click({ Manda-Config-Al-Tablet | Out-Null })
