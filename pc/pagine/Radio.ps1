# Pagina "La radio": quali stazioni ci sono sul tablet, in che ordine, con che
# logo.
#
# Fino a ieri erano ventidue righe dentro Radio.java: cambiare un indirizzo che
# ha smesso di funzionare - e succede, le radio spostano i flussi - voleva dire
# avere il progetto, l'SDK e una ricompilazione. Adesso e' un elenco nel file di
# configurazione, e questa pagina lo scrive. E' la stessa strada delle lampade,
# delle routine e delle app.
#
# I LOGHI RESTANO DENTRO L'APK, e non e' una mezza misura. Un marchio e' un PNG:
# mandarne uno nuovo dal PC vorrebbe dire una seconda strada per far arrivare
# dei file sul tablet, e un posto dove tenerli che non e' la configurazione. Qui
# si sceglie fra i ventuno che ci sono gia', e l'elenco di quali siano lo dice
# il tablet nella vetrina - non una copia scritta di qua che invecchia al primo
# disegno aggiunto. Chi mette una stazione senza logo tiene la sigla, che e' il
# ripiego previsto dall'inizio.
#
# LE DUE META'. A sinistra l'elenco nell'ordine in cui si vede sul tablet - sei
# per riga, in quell'ordine - e i tasti per riordinarlo. A destra la stazione
# scelta, campo per campo. Sono due domande diverse: "quali e in che ordine" e
# "com'e' fatta questa". Con una griglia sola bisognerebbe modificare dentro le
# celle, che per un indirizzo di duecento caratteri non funziona.

$pagRadio = $pagine[$PAG_RADIO]

$pagRadio.Controls.Add((Nuova-Intestazione $PAG_RADIO))

# Quale riga si sta modificando a destra. -1 quando non se ne modifica nessuna.
$script:radioScelta = -1

# ---- l'elenco ---------------------------------------------------------------

$boxStazioni = Nuovo-Pannello 0 44 460 520
$pagRadio.Controls.Add($boxStazioni)
$boxStazioni.Titolo = 'LE STAZIONI, NELL''ORDINE DEL TABLET'

$grigliaRadio = Nuova-Griglia 16 36 428 306 @(@('nome', 168), @('sigla', 62), @('logo', 190)) @($false, $false, $true)
$boxStazioni.Controls.Add($grigliaRadio)

$tRadioSu = Nuovo-Tasto 'Su' 16 350 104 30 $cTenue
$tRadioSu.Icona = 'su'
$boxStazioni.Controls.Add($tRadioSu)
$tRadioGiu = Nuovo-Tasto 'Giu''' 128 350 104 30 $cTenue
$tRadioGiu.Icona = 'giu'
$boxStazioni.Controls.Add($tRadioGiu)
$tRadioNuova = Nuovo-Tasto 'Nuova' 240 350 100 30 $cMedio
$tRadioNuova.Icona = 'piu'
$boxStazioni.Controls.Add($tRadioNuova)
$tRadioTogli = Nuovo-Tasto 'Togli' 348 350 96 30 $cRosso
$boxStazioni.Controls.Add($tRadioTogli)

$tRadioFabbrica = Nuovo-Tasto 'Rimetti le ventidue di fabbrica' 16 386 428 30 $cTenue
$boxStazioni.Controls.Add($tRadioFabbrica)

$boxStazioni.Controls.Add((Nuovo-Testo 16 416 428 51 @'
L'ordine e' quello della griglia sul tablet, sei per riga, e
anche quello di "stazione successiva" sul telecomando della
Home: le prime sei sono quelle che si trovano senza cercare.
'@))

$tRadioLeggi = Nuovo-Tasto 'Rileggi dal tablet' 16 466 210 30 $cMedio
$boxStazioni.Controls.Add($tRadioLeggi)
$tRadioManda = Tasto-Primario (Nuovo-Tasto 'Manda al tablet' 234 466 210 30 $cVerde) 'spunta'
$boxStazioni.Controls.Add($tRadioManda)

# ---- la stazione ------------------------------------------------------------

$boxStazione = Nuovo-Pannello 476 44 462 520
$pagRadio.Controls.Add($boxStazione)
$boxStazione.Titolo = 'LA STAZIONE'

function Riga-Campo([string]$etichetta, [int]$y) {
    $boxStazione.Controls.Add((Nuova-Etichetta $etichetta 16 ($y + 5) 96 $cTenue $fTesto))
}

Riga-Campo 'Nome' 40
$cRadioNome = Nuovo-Campo 118 40 326
$boxStazione.Controls.Add($cRadioNome)

Riga-Campo 'A voce' 76
$cRadioChiave = Nuovo-Campo 118 76 326
$boxStazione.Controls.Add($cRadioChiave)

Riga-Campo 'Sigla' 112
$cRadioSigla = Nuovo-Campo 118 112 120
$boxStazione.Controls.Add($cRadioSigla)

Riga-Campo 'Tinta' 148
$tRadioTinta = Tasto-Colore '#5FD0A0'
$tRadioTinta.Location = New-Object System.Drawing.Point(118, 148)
$tRadioTinta.Size = New-Object System.Drawing.Size(120, 26)
$boxStazione.Controls.Add($tRadioTinta)

Riga-Campo 'Logo' 184
$tendinaLogo = Nuova-Tendina 118 184 326
$boxStazione.Controls.Add($tendinaLogo)

Riga-Campo 'Fondo' 220
$tendinaFondo = Nuova-Tendina 118 220 326
[void]$tendinaFondo.Items.Add('il marchio e'' chiaro: si vede sul vetro scuro')
[void]$tendinaFondo.Items.Add('il marchio e'' scuro: mettilo su un disco chiaro')
$tendinaFondo.SelectedIndex = 0
$boxStazione.Controls.Add($tendinaFondo)

Riga-Campo 'Flusso' 256
$cRadioFlusso = Nuovo-Campo 118 256 326
$boxStazione.Controls.Add($cRadioFlusso)

$boxStazione.Controls.Add((Nuovo-Testo 16 292 428 119 @'
A VOCE e' come la si chiama parlando, tutto minuscolo: e' la
riga con cui "metti radio deejay" trova questa e non
un'altra. Se sono due che cominciano uguale vince la piu'
lunga, quindi "radio kiss kiss napoli" batte "radio kiss
kiss".

IL FLUSSO deve essere l'indirizzo del suono, non della pagina
del sito. Un .pls o un .m3u vanno bene - il tablet li apre e
ne tira fuori l'indirizzo vero - e l'HLS (.m3u8) pure.
'@))

$tRadioSalva = Tasto-Primario (Nuovo-Tasto 'Salva questa stazione' 16 422 260 30 $cVerde) 'spunta'
$boxStazione.Controls.Add($tRadioSalva)
$tRadioProva = Nuovo-Tasto 'Provala adesso' 284 422 160 30 $cMedio
$tRadioProva.Icona = 'avvia'
$boxStazione.Controls.Add($tRadioProva)

$boxStazione.Controls.Add((Nuovo-Testo 16 460 428 41 @'
"Provala adesso" la fa partire sul tablet, con la voce che
la annuncia: e' la prova che l'indirizzo e' vivo. La stazione
va salvata e mandata prima, o si prova quella di prima.
'@))

# =============================================================== il disegno ==

<##
 # L'HTTPS che questo Android non sa aprire.
 #
 # Il tablet e' un 7.0 e non ha nell'elenco delle autorita' la radice ISRG Root
 # X1 di Let's Encrypt: un flusso servito da un certificato firmato di li' non
 # parte, e - questa e' la parte cattiva - MediaPlayer non lo dice. Resta in
 # attesa finche' scadono i quindici secondi, e a schermo si legge "non
 # risponde", che manda a cercare il guasto dalla parte sbagliata.
 #
 # Non si puo' sapere di sicuro da qui quale certificato usera' un indirizzo:
 # si puo' pero' dire che gli HTTP semplici quel problema non ce l'hanno mai, e
 # che quando una stazione non parte quello e' il primo posto dove guardare.
 # E' scritto in docs/meteo.md, ed e' costato piu' tempo di qualunque altra cosa
 # in questo progetto.
 #>
function Radio-Sospetta([string]$flusso) {
    return ($flusso -match '^https://')
}

function Ridisegna-Radio {
    $grigliaRadio.BeginUpdate()
    $grigliaRadio.Items.Clear()
    foreach ($s in @($script:config.radio)) {
        $riga = New-Object System.Windows.Forms.ListViewItem([string]$s.nome)
        [void]$riga.SubItems.Add([string]$s.sigla)
        $logo = [string]$s.logo
        if ($logo.Length -eq 0) { $logo = '(la sigla)' }
        [void]$riga.SubItems.Add($logo)
        # Senza logo si vede subito quale, e non e' un rimprovero: Radio Capital
        # il marchio non lo pubblica da nessuna parte, e la sigla e' la scelta
        # giusta per lei.
        if ([string]$s.logo -eq '') { $riga.ForeColor = $script:cTenue }
        [void]$grigliaRadio.Items.Add($riga)
    }
    $grigliaRadio.EndUpdate()
    Riempi-Loghi
    if ($script:radioScelta -ge 0 -and $script:radioScelta -lt $grigliaRadio.Items.Count) {
        $grigliaRadio.Items[$script:radioScelta].Selected = $true
    }
}

$script:ridisegna += { Ridisegna-Radio }

<##
 # La tendina dei loghi.
 #
 # L'elenco arriva dal tablet insieme alla configurazione ($script:loghiBase, in
 # GestioneHome.ps1): sono i PNG che stanno DENTRO l'APK, e sceglierne uno che
 # non c'e' vorrebbe dire una stazione senza marchio senza sapere perche'. Se
 # il tablet non e' ancora stato letto la tendina resta con la sola voce
 # "nessuno", e la riga qui sotto dice cosa fare.
 #>
function Riempi-Loghi {
    $prima = [string]$tendinaLogo.Text
    $tendinaLogo.Items.Clear()
    [void]$tendinaLogo.Items.Add('(nessuno: resta la sigla)')
    foreach ($l in @($script:loghiBase)) { [void]$tendinaLogo.Items.Add([string]$l) }
    if ($prima.Length -gt 0) { $tendinaLogo.Text = $prima } else { $tendinaLogo.SelectedIndex = 0 }
}

function Radio-Scelta {
    if ($grigliaRadio.SelectedIndices.Count -eq 0) { return -1 }
    return $grigliaRadio.SelectedIndices[0]
}

function Mostra-Stazione([int]$i) {
    $tutte = @($script:config.radio)
    if ($i -lt 0 -or $i -ge $tutte.Count) { return }
    $script:radioScelta = $i
    $s = $tutte[$i]
    $cRadioNome.Text = [string]$s.nome
    $cRadioChiave.Text = [string]$s.chiave
    $cRadioSigla.Text = [string]$s.sigla
    $cRadioFlusso.Text = [string]$s.flusso

    $tinta = [string]$s.colore
    if ($tinta.Length -eq 0) { $tinta = '#5FD0A0' }
    Colore-Tasto $tRadioTinta $tinta

    $logo = [string]$s.logo
    if ($logo.Length -eq 0) { $tendinaLogo.SelectedIndex = 0 } else { $tendinaLogo.Text = $logo }

    $chiaro = $false
    if ($s.PSObject.Properties['chiaro']) { $chiaro = [bool]$s.chiaro }
    $tendinaFondo.SelectedIndex = $(if ($chiaro) { 1 } else { 0 })
}

$grigliaRadio.Add_SelectedIndexChanged({ Mostra-Stazione (Radio-Scelta) })

# =============================================================== i tasti =====

function Salva-Radio($nuove) {
    $script:config.radio = @($nuove)
    Config-Cambiata
}

<##
 # Quello che c'e' nei campi, come lo vuole il file.
 #
 # Il nome e il flusso sono gli unici due obbligatori, e il tablet la pensa
 # uguale (vedi Stazione.da): senza uno dei due la riga viene saltata di la',
 # quindi tanto vale non lasciarla scrivere di qua.
 #>
function Stazione-Dai-Campi {
    $nome = $cRadioNome.Text.Trim()
    $flusso = $cRadioFlusso.Text.Trim()
    if ($nome.Length -eq 0) { Registra 'Il nome non puo'' restare vuoto.'; return $null }
    if ($flusso.Length -eq 0) { Registra 'Senza flusso la stazione non suona: mettici l''indirizzo.'; return $null }

    $chiave = $cRadioChiave.Text.Trim().ToLower()
    if ($chiave.Length -eq 0) { $chiave = $nome.ToLower() }
    $sigla = $cRadioSigla.Text.Trim()
    if ($sigla.Length -eq 0) {
        $sigla = $nome.Substring(0, [Math]::Min(3, $nome.Length))
    }
    $logo = [string]$tendinaLogo.Text
    if ($tendinaLogo.SelectedIndex -eq 0 -or $logo -like '(*') { $logo = '' }

    return [ordered]@{
        chiave = $chiave
        nome   = $nome
        sigla  = $sigla
        colore = [string]$tRadioTinta.Text
        logo   = $logo
        chiaro = ($tendinaFondo.SelectedIndex -eq 1)
        flusso = $flusso
    }
}

$tRadioSalva.Add_Click({
    $nuova = Stazione-Dai-Campi
    if ($null -eq $nuova) { return }
    $tutte = @($script:config.radio)
    if ($script:radioScelta -ge 0 -and $script:radioScelta -lt $tutte.Count) {
        $tutte[$script:radioScelta] = $nuova
        Salva-Radio $tutte
        Registra "Salvata $($nuova.nome). Mandala al tablet perche' ci arrivi."
    } else {
        Salva-Radio ($tutte + $nuova)
        $script:radioScelta = $tutte.Count
        Ridisegna-Radio
        Registra "Aggiunta $($nuova.nome). Mandala al tablet perche' ci arrivi."
    }
    if (Radio-Sospetta $nuova.flusso) {
        Registra ("Nota: e' un indirizzo https. Questo Android non conosce Let's Encrypt, " +
                  "e con quei certificati il flusso non parte e non lo dice - resta in attesa. " +
                  "Se non suona, cerca la stessa stazione in http semplice.")
    }
})

$tRadioNuova.Add_Click({
    # Non si aggiunge una riga vuota all'elenco: si svuotano i campi e la riga
    # nasce al Salva. Una riga vuota in griglia e' una stazione che sul tablet
    # esisterebbe gia', senza nome e senza suono.
    $script:radioScelta = -1
    $grigliaRadio.SelectedIndices.Clear()
    $cRadioNome.Text = ''
    $cRadioChiave.Text = ''
    $cRadioSigla.Text = ''
    $cRadioFlusso.Text = 'http://'
    Colore-Tasto $tRadioTinta '#5FD0A0'
    $tendinaLogo.SelectedIndex = 0
    $tendinaFondo.SelectedIndex = 0
    Registra 'Riempi i campi a destra e premi "Salva questa stazione".'
})

$tRadioTogli.Add_Click({
    $i = Radio-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una stazione.'; return }
    $tutte = @($script:config.radio)
    $quale = $tutte[$i]
    if (-not (Chiedi ("Tolgo ""$($quale.nome)"" dalle stazioni del tablet?`n`n" +
                      "Si rimette quando vuoi, ma l'indirizzo del flusso va riscritto.") `
                     'Tolgo questa stazione?' 'Togli la stazione' -Pericolo)) { return }
    $restano = @()
    for ($x = 0; $x -lt $tutte.Count; $x++) { if ($x -ne $i) { $restano += $tutte[$x] } }
    $script:radioScelta = -1
    Salva-Radio $restano
    Registra "Tolta $($quale.nome). Mandala al tablet perche' sparisca davvero."
})

$tRadioSu.Add_Click({
    $i = Radio-Scelta
    $a = Sposta-Voce $script:config.radio $i ($i - 1)
    if ($null -eq $a) { return }
    $script:radioScelta = $i - 1
    Salva-Radio $a
    $grigliaRadio.Items[$i - 1].Selected = $true
})

$tRadioGiu.Add_Click({
    $i = Radio-Scelta
    $a = Sposta-Voce $script:config.radio $i ($i + 1)
    if ($null -eq $a) { return }
    $script:radioScelta = $i + 1
    Salva-Radio $a
    $grigliaRadio.Items[$i + 1].Selected = $true
})

<##
 # Le ventidue di partenza.
 #
 # Arrivano dal tablet nella vetrina ($script:radioBase), non da una copia
 # scritta qui: sono provate una per una dentro Radio.java, e tenerne un
 # doppione in PowerShell vorrebbe dire due elenchi da correggere il giorno che
 # una stazione sposta il flusso.
 #>
$tRadioFabbrica.Add_Click({
    if (@($script:radioBase).Count -eq 0) {
        Registra 'Non so quali siano: premi prima "Rileggi dal tablet".'
        return
    }
    if (-not (Chiedi ("Rimetto le $(@($script:radioBase).Count) stazioni di fabbrica?`n`n" +
                      "Quello che hai adesso in elenco viene sostituito, e non c'e' modo di " +
                      "riaverlo: le stazioni che hai aggiunto tu spariscono.") `
                     'Rimetto quelle di fabbrica?' 'Rimettile' -Pericolo)) { return }
    $script:radioScelta = -1
    Salva-Radio @($script:radioBase)
    Registra "Rimesse le stazioni di fabbrica. Mandale al tablet perche' ci arrivino."
})

<##
 # Provare una stazione.
 #
 # Si passa dalla frase, non da un comando apposta: "metti <nome>" e' quello
 # che Casa capisce gia', ed e' la stessa strada che fa la voce. Prova quindi
 # due cose in un colpo - che il flusso sia vivo, e che la chiave scritta qui
 # sopra sia quella con cui la stazione si chiama davvero.
 #>
$tRadioProva.Add_Click({
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    $chiave = $cRadioChiave.Text.Trim()
    if ($chiave.Length -eq 0) { $chiave = $cRadioNome.Text.Trim() }
    if ($chiave.Length -eq 0) { Registra 'Scegli prima una stazione.'; return }
    Sh ("am broadcast -a dev.casa.DI --es frase 'metti " + $chiave.ToLower() + "'") | Out-Null
    Registra "Chiesto al tablet: ""metti $chiave"". Se annuncia un'altra stazione, la chiave e' di un'altra."
})

$tRadioLeggi.Add_Click({ Leggi-Config-Dal-Tablet | Out-Null })
$tRadioManda.Add_Click({ Manda-Config-Al-Tablet | Out-Null })
