# Pagina "Routine": le scene di casa.
#
# UNA ROUTINE NON E' PIU' UN FATTO DI LAMPADINE. Un passo aveva tre campi -
# quale luce, che azione, con che valore - e questo bastava finche' le routine
# erano "buonanotte" e "cinema" intese come "spegni tutto" e "abbassa le luci".
# Ma buonanotte in una casa vera spegne anche la radio, e cinema alza il volume:
# la sezione sul tablet si chiama Casa, non Luci, e la parte che restava fuori
# era proprio quella che si sarebbe voluta.
#
# Adesso un passo puo' essere tre cose:
#
#   luce     una lampada per identificativo, o tutte. Accendi, spegni, inverti,
#            luminosita', colore, bianco, temperatura del bianco.
#
#   frase    una frase detta a Casa, come se qualcuno l'avesse pronunciata:
#            "metti rai radio 1", "spegni la radio", "volume al 30",
#            "apri netflix", "metti un timer di dieci minuti". Vale TUTTO
#            quello che Casa capisce - l'elenco sta nella pagina "I comandi".
#
#   attesa   tot secondi fermi, quando conta anche il tempo e non solo
#            l'ordine.
#
# PERCHE' UNA FRASE E NON UNA TABELLA DI AZIONI. La strada ovvia era aggiungere
# i tipi uno per uno - radio, musica, volume, app - ognuno coi suoi campi e il
# suo pezzo di codice sul tablet. Sarebbe stata una seconda lingua per dire
# quello che Comandi.java sa gia' capire, da tenere d'accordo con la prima per
# sempre: ogni comando nuovo andrebbe scritto due volte, e la seconda ci si
# dimentica. Passando la frase a Comandi si eredita tutto quello che Casa impara
# il giorno stesso in cui lo impara - e la si prova nella casella "prova una
# frase" della pagina "I comandi", che e' la stessa strada.

$pagRout = $pagine[$PAG_ROUT]

$pagRout.Controls.Add((Nuova-Intestazione $PAG_ROUT))

# Le icone che il tablet sa disegnare per una routine. L'elenco e' chiuso
# apposta: sono quelle che ci sono in Icone.java, e una tendina con dentro solo
# quelle e' l'unico modo perche' non si scriva un nome che poi sul tablet
# diventa una lampadina generica senza spiegare perche'.
$script:ICONE_ROUTINE = @('(dal nome)', 'notte', 'sole', 'lampada', 'accensione',
                          'tavolozza', 'luminosita', 'musica', 'radio', 'sveglia',
                          'timer', 'volume', 'casa')

$script:AZIONI_LUCE = @(
    @('on',         'accendi'),
    @('off',        'spegni'),
    @('inverti',    'inverti'),
    @('luce',       'luminosita'' (0-100)'),
    @('colore',     'colore (#RRGGBB)'),
    @('bianco',     'luce bianca'),
    @('bianchezza', 'bianco caldo/freddo (0-100)')
)

# ---- le routine --------------------------------------------------------------

$boxRout = Nuovo-Pannello 0 44 420 500
$pagRout.Controls.Add($boxRout)

$boxRout.Titolo = 'LE ROUTINE'

$grigliaRout = Nuova-Griglia 16 36 388 300 @(
    @('nome', 150), @('passi', 50), @('icona', 88), @('tinta', 74))
$boxRout.Controls.Add($grigliaRout)

$tRoutNuova = Nuovo-Tasto 'Nuova' 16 346 124 30 $cMedio
$boxRout.Controls.Add($tRoutNuova)
$tRoutModifica = Nuovo-Tasto 'Nome e aspetto' 148 346 124 30 $cMedio
$boxRout.Controls.Add($tRoutModifica)
$tRoutTogli = Nuovo-Tasto 'Togli' 280 346 124 30 $cRosso
$boxRout.Controls.Add($tRoutTogli)

$tRoutSu = Nuovo-Tasto 'Su' 16 382 110 28 $cTenue
$tRoutSu.Icona = 'su'
$boxRout.Controls.Add($tRoutSu)
$tRoutGiu = Nuovo-Tasto 'Giu''' 134 382 110 28 $cTenue
$tRoutGiu.Icona = 'giu'
$boxRout.Controls.Add($tRoutGiu)
$tRoutProva = Nuovo-Tasto 'Prova sul tablet' 252 382 152 28 $cAmbra
$tRoutProva.Icona = 'avvia'
$boxRout.Controls.Add($tRoutProva)

$eRoutNota = Nuova-Etichetta '' 16 416 388 $cTenue $fNota
$eRoutNota.Height = 46
$eRoutNota.Text = ("L'ordine e' quello in cui compaiono sul tablet, in cima alla sezione Casa. " +
                   "Due comandi non possono contendersi la stessa parola: la routine si " +
                   "riconosce per nome, quindi ""Film"" manderebbe a Netflix invece di " +
                   "abbassare le luci. Per questo si chiama ""Cinema"".")
$boxRout.Controls.Add($eRoutNota)

$tRoutLeggi = Nuovo-Tasto 'Rileggi dal tablet' 16 464 190 30 $cMedio
$boxRout.Controls.Add($tRoutLeggi)
$tRoutManda = Tasto-Primario (Nuovo-Tasto 'Manda al tablet' 214 464 190 30 $cGiallo) 'spunta'
$boxRout.Controls.Add($tRoutManda)

# ---- i passi -----------------------------------------------------------------

$boxPassi = Nuovo-Pannello 436 44 502 500
$pagRout.Controls.Add($boxPassi)

$ePassiTitolo = Nuova-Etichetta 'I PASSI' 16 12 400 $cTenue $fMedio
$boxPassi.Controls.Add($ePassiTitolo)

$grigliaPassiRout = Nuova-Griglia 16 36 470 300 @(
    @('#', 30), @('cosa', 66), @('a chi', 130), @('azione', 110), @('valore', 116))
$boxPassi.Controls.Add($grigliaPassiRout)

$tPassoNuovo = Nuovo-Tasto 'Aggiungi un passo' 16 346 226 30 $cMedio
$boxPassi.Controls.Add($tPassoNuovo)
$tPassoModifica = Nuovo-Tasto 'Modifica' 250 346 110 30 $cMedio
$boxPassi.Controls.Add($tPassoModifica)
$tPassoTogli = Nuovo-Tasto 'Togli' 368 346 118 30 $cRosso
$boxPassi.Controls.Add($tPassoTogli)

$tPassoSu = Nuovo-Tasto 'Su' 16 382 110 28 $cTenue
$tPassoSu.Icona = 'su'
$boxPassi.Controls.Add($tPassoSu)
$tPassoGiu = Nuovo-Tasto 'Giu''' 134 382 110 28 $cTenue
$tPassoGiu.Icona = 'giu'
$boxPassi.Controls.Add($tPassoGiu)

$boxPassi.Controls.Add((Nuovo-Testo 16 420 470 66 @'
I passi succedono in ordine, uno alla volta: una lampada Tuya accetta
una connessione per volta, e "spegni tutto poi accendi il comodino"
deve succedere in quest'ordine. Una lampada che dormiva puo' farsi
aspettare mezzo minuto, e in quel caso la routine aspetta lei.
'@))

# =============================================================== il disegno ==

function Ridisegna-Routine {
    $scelta = -1
    if ($grigliaRout.SelectedIndices.Count -gt 0) { $scelta = $grigliaRout.SelectedIndices[0] }

    $grigliaRout.BeginUpdate()
    $grigliaRout.Items.Clear()
    foreach ($r in @($script:config.routine)) {
        $riga = New-Object System.Windows.Forms.ListViewItem([string]$r.nome)
        [void]$riga.SubItems.Add([string]@($r.passi).Count)
        $icona = [string]$r.icona
        [void]$riga.SubItems.Add($(if ($icona) { $icona } else { '(dal nome)' }))
        [void]$riga.SubItems.Add([string]$r.colore)
        [void]$grigliaRout.Items.Add($riga)
    }
    $grigliaRout.EndUpdate()

    if ($scelta -ge 0 -and $scelta -lt $grigliaRout.Items.Count) {
        $grigliaRout.Items[$scelta].Selected = $true
    } elseif ($grigliaRout.Items.Count -gt 0) {
        $grigliaRout.Items[0].Selected = $true
    }
    # E poi si ridisegnano i passi comunque, invece di aspettare che lo faccia
    # l'evento della selezione. Una ListView dentro un pannello ancora
    # invisibile - e all'avvio sei pagine su sette lo sono - non ha ancora un
    # handle, e la selezione impostata da codice non fa scattare niente:
    # aprendo la pagina Routine per la prima volta l'elenco dei passi restava
    # vuoto finche' non si cliccava una routine gia' selezionata.
    Ridisegna-Passi
}

$script:ridisegna += { Ridisegna-Routine }

function Routine-Scelta {
    if ($grigliaRout.SelectedIndices.Count -eq 0) { return -1 }
    return $grigliaRout.SelectedIndices[0]
}

function Passo-Scelto {
    if ($grigliaPassiRout.SelectedIndices.Count -eq 0) { return -1 }
    return $grigliaPassiRout.SelectedIndices[0]
}

# Il nome di una lampada dal suo identificativo: nella griglia dei passi si
# legge "Comodino", non "id-lampada-1".
function Nome-Luce([string]$id) {
    if (-not $id) { return 'tutte' }
    foreach ($l in @($script:config.luci)) {
        if ([string]$l.id -eq $id) { return [string]$l.nome }
    }
    return "$id (non in elenco)"
}

function Nome-Azione([string]$a) {
    foreach ($x in $script:AZIONI_LUCE) { if ($x[0] -eq $a) { return $x[1] } }
    return $a
}

<##
 # Quale routine si sta guardando: quella scelta, o la prima se non c'e' ancora
 # una scelta.
 #
 # E' separata da Routine-Scelta apposta: per DISEGNARE, "nessuna scelta" e "la
 # prima" vogliono dire la stessa cosa; per TOGLIERE non lo vogliono affatto, e
 # far cadere una cancellazione sulla prima riga perche' nessuna era selezionata
 # e' il genere di comodita' che si paga una volta sola.
 #>
function Routine-Da-Mostrare {
    $i = Routine-Scelta
    if ($i -lt 0 -and @($script:config.routine).Count -gt 0) { return 0 }
    return $i
}

function Ridisegna-Passi {
    $i = Routine-Da-Mostrare
    $grigliaPassiRout.BeginUpdate()
    $grigliaPassiRout.Items.Clear()
    if ($i -lt 0) {
        $ePassiTitolo.Text = 'I PASSI'
        $grigliaPassiRout.EndUpdate()
        return
    }
    $r = $script:config.routine[$i]
    $ePassiTitolo.Text = ('I PASSI DI ' + ([string]$r.nome).ToUpper())
    $n = 1
    foreach ($p in @($r.passi)) {
        $cosa = [string]$p.cosa
        if (-not $cosa) { $cosa = 'luce' }
        $riga = New-Object System.Windows.Forms.ListViewItem([string]$n)
        [void]$riga.SubItems.Add($cosa)
        if ($cosa -eq 'luce') {
            # Il formato vecchio metteva l'identificativo in "luce": si legge
            # anche quello, o una routine scritta ieri comparirebbe come una
            # fila di passi senza bersaglio.
            $bersaglio = [string]$p.bersaglio
            if (-not $bersaglio -and $p.PSObject -and $p.PSObject.Properties['luce']) {
                $bersaglio = [string]$p.luce
            }
            [void]$riga.SubItems.Add((Nome-Luce $bersaglio))
            [void]$riga.SubItems.Add((Nome-Azione ([string]$p.azione)))
            [void]$riga.SubItems.Add([string]$p.valore)
        } elseif ($cosa -eq 'frase') {
            [void]$riga.SubItems.Add('ad Assistente Home')
            [void]$riga.SubItems.Add('dille')
            [void]$riga.SubItems.Add('"' + [string]$p.valore + '"')
        } else {
            [void]$riga.SubItems.Add('-')
            [void]$riga.SubItems.Add('aspetta')
            [void]$riga.SubItems.Add([string]$p.valore + ' s')
        }
        [void]$grigliaPassiRout.Items.Add($riga)
        $n++
    }
    $grigliaPassiRout.EndUpdate()
}

$grigliaRout.Add_SelectedIndexChanged({ Ridisegna-Passi })

# ============================================================ le finestrelle ==

function Finestra-Routine($routine) {
    $largo = 460
    $fin = Nuova-Finestrella $(if ($routine) { 'Questa routine' } else { 'Una routine nuova' }) `
        'un tocco che fa succedere piu'' cose' 'avvia' $cGiallo $largo

    $cNome = Nuovo-Campo 0 0 ($largo - 112)
    if ($routine) { $cNome.Text = [string]$routine.nome }
    $y = Riga-Modulo $fin.corpo 0 'Nome' $cNome `
        'Si preme sul tablet, e si dice a voce: "casa, buonanotte".' $largo

    $tIcona = Nuova-Tendina 0 0 ($largo - 112)
    foreach ($n in $script:ICONE_ROUTINE) { [void]$tIcona.Items.Add($n) }
    $scelta = '(dal nome)'
    if ($routine -and $routine.icona) { $scelta = [string]$routine.icona }
    if ($tIcona.Items.Contains($scelta)) { $tIcona.SelectedItem = $scelta } else { $tIcona.SelectedIndex = 0 }
    $y = Riga-Modulo $fin.corpo $y 'Icona' $tIcona `
        '"dal nome" la indovina il tablet: notte, cinema, tutte accese.' $largo

    $partenza = '#5A6C8C'
    if ($routine -and $routine.colore) { $partenza = [string]$routine.colore }
    $tinta = Tasto-Colore $partenza
    $y = Riga-Modulo $fin.corpo $y 'Tinta' $tinta `
        'Il colore della tessera sul tablet. Premi per cambiarlo.' $largo

    $no = Tasto-Fondo 'Lascia stare' $cTenue '' 'Cancel' 'Quieto'
    $ok = Tasto-Fondo 'Va bene' $cGiallo 'spunta' 'OK' 'Primario'
    if ((Apri-Finestrella $fin $y @($no, $ok)) -ne 'OK') { return $null }

    $nome = $cNome.Text.Trim()
    if ($nome.Length -eq 0) { Registra 'Una routine senza nome non si puo'' ne'' premere ne'' dire.'; return $null }
    $icona = [string]$tIcona.SelectedItem
    if ($icona -eq '(dal nome)') { $icona = '' }
    return @{ nome = $nome; icona = $icona; colore = $tinta.Text }
}

function Finestra-Passo($passo) {
    $largo = 500
    $fin = Nuova-Finestrella 'Un passo' `
        'una luce, una frase detta ad Assistente Home, o un''attesa' 'avanti' $cGiallo $largo

    $tCosa = Nuova-Tendina 0 0 ($largo - 112)
    [void]$tCosa.Items.AddRange(@('una luce', 'una frase detta ad Assistente Home', 'aspetta e basta'))
    $y = Riga-Modulo $fin.corpo 0 'Che cosa' $tCosa '' $largo

    $tChi = Nuova-Tendina 0 0 ($largo - 112)
    [void]$tChi.Items.Add('tutte le lampade')
    foreach ($l in @($script:config.luci)) { [void]$tChi.Items.Add([string]$l.nome) }
    $y = Riga-Modulo $fin.corpo $y 'A chi' $tChi '' $largo
    $lChi = $script:etichettaFatta

    $tAzione = Nuova-Tendina 0 0 ($largo - 112)
    foreach ($a in $script:AZIONI_LUCE) { [void]$tAzione.Items.Add($a[1]) }
    $y = Riga-Modulo $fin.corpo $y 'Fai' $tAzione '' $largo
    $lAzione = $script:etichettaFatta

    # Il valore vuol dire cose diverse a seconda del tipo, e per questo la sua
    # etichetta cambia: "Valore", "La frase", "Secondi". Il tasto della
    # tavolozza sta accanto e compare solo quando si sta scegliendo un colore.
    $cValore = Nuovo-Campo 0 0 ($largo - 112 - 118)
    $tTinta = Nuovo-Tasto 'scegli...' ($largo - 110) $y 110 28 $cMedio
    $tTinta.Icona = 'tavolozza'
    $tTinta.Add_Click({
        $d = New-Object System.Windows.Forms.ColorDialog
        $d.FullOpen = $true
        if ($d.ShowDialog() -eq 'OK') {
            $cValore.Text = '#{0:X2}{1:X2}{2:X2}' -f $d.Color.R, $d.Color.G, $d.Color.B
        }
    }.GetNewClosure())
    $fin.corpo.Controls.Add($tTinta)
    $yValore = $y
    $y = Riga-Modulo $fin.corpo $y 'Valore' $cValore '' $largo
    $lValore = $script:etichettaFatta

    <##
     # LA SPIEGAZIONE CAMBIA, LA FINESTRA NO.
     #
     # A ogni tipo di passo corrisponde un aiuto diverso, e sono lunghi diversi:
     # quello della frase e' il doppio di quello dell'attesa. Prima stavano in
     # una casella alta 76 punti scelti a occhio, e il piu' lungo ne voleva
     # novanta: le ultime due righe non c'erano.
     #
     # Adesso li si misura tutti e si tiene lo spazio del piu' alto. Costa un
     # po' d'aria bianca quando l'aiuto e' corto, e in cambio non c'e' nessun
     # caso in cui manchi del testo - e la finestra non cambia altezza sotto il
     # cursore mentre si sceglie, che e' il modo migliore per far premere la
     # cosa sbagliata.
     #>
    $AIUTI = @(
        "Quello che diresti ad Assistente Home: ""metti rai radio 1"", ""spegni la radio"", ""volume al 30"", ""apri netflix"".`r`nVale tutto quello che c'e' nella pagina ""I comandi"": la frase entra nello stesso posto in cui entra quello che dici a voce.",
        "Quanti secondi stare fermi prima del passo dopo. Serve quando conta anche il tempo: alzare il volume mentre la radio sta ancora partendo lo alza sulla cosa sbagliata.`r`nAl massimo 60.",
        "Da 0 a 100.`r`nPer il bianco: 0 e' caldo, 100 e' freddo.",
        "Il colore, come #RRGGBB. Il tasto qui accanto apre la tavolozza.",
        "Questa azione non vuole nessun valore."
    )
    $altoAiuto = 0
    foreach ($a in $AIUTI) {
        $q = [Casa.Aspetto]::Alto($a, $script:fNota, ($largo - 112))
        if ($q -gt $altoAiuto) { $altoAiuto = $q }
    }
    # Sotto il campo e non sotto l'etichetta: e' la spiegazione di quel campo,
    # come tutte le altre righe di questo modulo.
    $eAiuto = Nuovo-Paragrafo 112 $y ($largo - 112) $AIUTI[0] $cTenue $fNota
    $eAiuto.Height = $altoAiuto
    $fin.corpo.Controls.Add($eAiuto)
    $y += $altoAiuto

    # L'elenco delle azioni come variabile LOCALE, e non e' un vezzo: la
    # chiusura qui sotto si fa con GetNewClosure, che porta dentro le variabili
    # locali di questa funzione e SOLO quelle. Dentro, $script:AZIONI_LUCE non
    # e' l'elenco: e' niente, e il difetto si vede solo cambiando la tendina.
    $azioni = $script:AZIONI_LUCE

    # Quali caselle hanno senso dipende dal tipo, e spegnerle invece di
    # nasconderle tiene ferma la finestra: un modulo che cambia forma sotto il
    # cursore fa premere la cosa sbagliata.
    $aggiorna = {
        $tipo = $tCosa.SelectedIndex
        $luce = ($tipo -eq 0)
        $tChi.Enabled = $luce
        $tAzione.Enabled = $luce
        $lChi.Enabled = $luce
        $lAzione.Enabled = $luce
        $azione = ''
        if ($luce -and $tAzione.SelectedIndex -ge 0) { $azione = $azioni[$tAzione.SelectedIndex][0] }
        $tTinta.Visible = ($luce -and $azione -eq 'colore')
        if ($tipo -eq 1) {
            $lValore.Text = 'La frase'
            $eAiuto.Text = $AIUTI[0]
            $cValore.Enabled = $true
        } elseif ($tipo -eq 2) {
            $lValore.Text = 'Secondi'
            $eAiuto.Text = $AIUTI[1]
            $cValore.Enabled = $true
        } else {
            $lValore.Text = 'Valore'
            if ($azione -eq 'luce' -or $azione -eq 'bianchezza') { $eAiuto.Text = $AIUTI[2] }
            elseif ($azione -eq 'colore') { $eAiuto.Text = $AIUTI[3] }
            else { $eAiuto.Text = $AIUTI[4] }
            $cValore.Enabled = ($azione -eq 'luce' -or $azione -eq 'colore' -or $azione -eq 'bianchezza')
        }
    }.GetNewClosure()

    $tCosa.Add_SelectedIndexChanged($aggiorna)
    $tAzione.Add_SelectedIndexChanged($aggiorna)

    # -- quello che c'era prima
    $tCosa.SelectedIndex = 0
    $tChi.SelectedIndex = 0
    $tAzione.SelectedIndex = 0
    if ($passo) {
        $cosa = [string]$passo.cosa
        if (-not $cosa) { $cosa = 'luce' }
        if ($cosa -eq 'frase') { $tCosa.SelectedIndex = 1 }
        elseif ($cosa -eq 'attesa') { $tCosa.SelectedIndex = 2 }
        else {
            $tCosa.SelectedIndex = 0
            $bersaglio = [string]$passo.bersaglio
            if ($bersaglio) {
                $nome = Nome-Luce $bersaglio
                if ($tChi.Items.Contains($nome)) { $tChi.SelectedItem = $nome }
            }
            for ($k = 0; $k -lt $script:AZIONI_LUCE.Count; $k++) {
                if ($script:AZIONI_LUCE[$k][0] -eq [string]$passo.azione) { $tAzione.SelectedIndex = $k }
            }
        }
        $cValore.Text = [string]$passo.valore
    }
    & $aggiorna

    $no = Tasto-Fondo 'Lascia stare' $cTenue '' 'Cancel' 'Quieto'
    $ok = Tasto-Fondo 'Va bene' $cGiallo 'spunta' 'OK' 'Primario'
    if ((Apri-Finestrella $fin $y @($no, $ok)) -ne 'OK') { return $null }

    $tipo = $tCosa.SelectedIndex
    if ($tipo -eq 1) {
        $frase = $cValore.Text.Trim()
        if ($frase.Length -eq 0) { Registra 'Un passo "frase" senza frase non fa niente.'; return $null }
        return [ordered]@{ cosa = 'frase'; bersaglio = ''; azione = ''; valore = $frase }
    }
    if ($tipo -eq 2) {
        $s = 0
        if (-not [int]::TryParse($cValore.Text.Trim(), [ref]$s)) { $s = 1 }
        return [ordered]@{ cosa = 'attesa'; bersaglio = ''; azione = ''; valore = [string]([Math]::Min(60, [Math]::Max(1, $s))) }
    }
    $bersaglio = ''
    if ($tChi.SelectedIndex -gt 0) {
        $bersaglio = [string]@($script:config.luci)[$tChi.SelectedIndex - 1].id
    }
    $azione = $script:AZIONI_LUCE[$tAzione.SelectedIndex][0]
    $valore = ''
    if ($azione -eq 'luce' -or $azione -eq 'colore' -or $azione -eq 'bianchezza') {
        $valore = $cValore.Text.Trim()
    }
    return [ordered]@{ cosa = 'luce'; bersaglio = $bersaglio; azione = $azione; valore = $valore }
}

# =============================================================== i tasti =====

$tRoutNuova.Add_Click({
    $n = Finestra-Routine $null
    if (-not $n) { return }
    $script:config.routine = @($script:config.routine) + [ordered]@{
        nome = $n.nome; colore = $n.colore; icona = $n.icona; passi = @()
    }
    Registra "Nuova routine: $($n.nome). Adesso mettici i passi."
    Config-Cambiata
})

$tRoutModifica.Add_Click({
    $i = Routine-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una routine.'; return }
    $r = $script:config.routine[$i]
    $n = Finestra-Routine $r
    if (-not $n) { return }
    $tutte = @($script:config.routine)
    $tutte[$i] = [ordered]@{ nome = $n.nome; colore = $n.colore; icona = $n.icona; passi = @($r.passi) }
    $script:config.routine = $tutte
    Config-Cambiata
})

$tRoutTogli.Add_Click({
    $i = Routine-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una routine.'; return }
    $r = $script:config.routine[$i]
    if (-not (Chiedi ("Tolgo la routine ""$($r.nome)""?" + [Environment]::NewLine + [Environment]::NewLine +
                      "Sparisce dal tablet appena mandi la configurazione, con tutti i suoi passi.") `
                     'Tolgo questa routine?' 'Togli la routine' -Pericolo)) { return }
    # Per indice e non per confronto: due routine possono avere lo stesso nome
    # mentre le si sta scrivendo, e il confronto fra oggetti qui vuol dire
    # confronto di riferimenti - che regge finche' non si ricarica la
    # configurazione e poi smette di reggere senza dirlo.
    $tutte = @($script:config.routine)
    $restano = @()
    for ($x = 0; $x -lt $tutte.Count; $x++) { if ($x -ne $i) { $restano += $tutte[$x] } }
    $script:config.routine = $restano
    Registra "Tolta $($r.nome)."
    Config-Cambiata
})

$tRoutSu.Add_Click({
    $i = Routine-Scelta
    $a = Sposta-Voce $script:config.routine $i ($i - 1)
    if ($null -eq $a) { return }
    $script:config.routine = $a
    Config-Cambiata
    $grigliaRout.Items[$i - 1].Selected = $true
})

$tRoutGiu.Add_Click({
    $i = Routine-Scelta
    $a = Sposta-Voce $script:config.routine $i ($i + 1)
    if ($null -eq $a) { return }
    $script:config.routine = $a
    Config-Cambiata
    $grigliaRout.Items[$i + 1].Selected = $true
})

<##
 # Prova la routine facendola succedere sul tablet.
 #
 # E' l'unico modo onesto di provarla: quella che gira e' la routine vera, con i
 # suoi passi che parlano alle lampade dalla rete del tablet e i suoi passi che
 # entrano in Comandi. Una prova fatta dal PC direbbe se il PC ci riesce, che
 # non e' la domanda.
 #
 # Va mandata prima: sul tablet gira quella che il tablet ha, non quella che si
 # sta scrivendo qui.
 #>
$tRoutProva.Add_Click({
    $i = Routine-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una routine.'; return }
    $r = $script:config.routine[$i]
    if (-not (Chiedi ("Mando la configurazione al tablet e poi faccio partire ""$($r.nome)"".`n`n" +
                      "Sul tablet succede davvero: le luci cambiano e la radio parte.") `
                     'La faccio partire?' 'Falla partire')) { return }
    if (-not (Manda-Config-Al-Tablet)) { return }
    $nome = ([string]$r.nome).Replace("'", "'\''")
    Sh ("am broadcast -a dev.casa.CONFIG --es cosa routine --es nome '" + $nome + "'") | Out-Null
    Registra "Partita $($r.nome) sul tablet."
})

$tRoutLeggi.Add_Click({ Leggi-Config-Dal-Tablet | Out-Null })
$tRoutManda.Add_Click({ Manda-Config-Al-Tablet | Out-Null })

# ---- i passi ----

function Salva-Passi($nuovi) {
    $i = Routine-Scelta
    if ($i -lt 0) { return }
    $r = $script:config.routine[$i]
    $tutte = @($script:config.routine)
    $tutte[$i] = [ordered]@{
        nome = [string]$r.nome; colore = [string]$r.colore
        icona = [string]$r.icona; passi = @($nuovi)
    }
    $script:config.routine = $tutte
    Config-Cambiata
}

$tPassoNuovo.Add_Click({
    $i = Routine-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una routine.'; return }
    $p = Finestra-Passo $null
    if (-not $p) { return }
    Salva-Passi (@($script:config.routine[$i].passi) + $p)
})

$tPassoModifica.Add_Click({
    $i = Routine-Scelta
    $k = Passo-Scelto
    if ($i -lt 0 -or $k -lt 0) { Registra 'Scegli prima un passo.'; return }
    $p = Finestra-Passo @($script:config.routine[$i].passi)[$k]
    if (-not $p) { return }
    $tutti = @($script:config.routine[$i].passi)
    $tutti[$k] = $p
    Salva-Passi $tutti
})

$tPassoTogli.Add_Click({
    $i = Routine-Scelta
    $k = Passo-Scelto
    if ($i -lt 0 -or $k -lt 0) { Registra 'Scegli prima un passo.'; return }
    $tutti = @($script:config.routine[$i].passi)
    $restano = @()
    for ($x = 0; $x -lt $tutti.Count; $x++) { if ($x -ne $k) { $restano += $tutti[$x] } }
    Salva-Passi $restano
})

$tPassoSu.Add_Click({
    $i = Routine-Scelta
    $k = Passo-Scelto
    if ($i -lt 0) { return }
    $a = Sposta-Voce $script:config.routine[$i].passi $k ($k - 1)
    if ($null -eq $a) { return }
    Salva-Passi $a
    $grigliaPassiRout.Items[$k - 1].Selected = $true
})

$tPassoGiu.Add_Click({
    $i = Routine-Scelta
    $k = Passo-Scelto
    if ($i -lt 0) { return }
    $a = Sposta-Voce $script:config.routine[$i].passi $k ($k + 1)
    if ($null -eq $a) { return }
    Salva-Passi $a
    $grigliaPassiRout.Items[$k + 1].Selected = $true
})
