# Pagina "Le luci": chi c'e' in casa, come si prendono le chiavi, e l'elenco
# che finisce sul tablet.
#
# Tre cose, in tre riquadri, e sono tre mestieri diversi:
#
#   TROVARE   si sta in ascolto degli annunci in broadcast. Le lampade Tuya si
#             presentano da sole ogni pochi secondi sulle porte 6666 e 6667, e
#             dicono identificativo, indirizzo e versione del protocollo.
#
#   LA CHIAVE senza, una lampada non risponde a nessuno, e l'app Smart Life
#             non la mostra. Si passa da Tuya con un codice QR - la stessa
#             strada di Home Assistant - e si prendono tutte in un colpo.
#
#   L'ELENCO  quello che il tablet usera'. Si modifica qui e si manda di la'.
#
# PERCHE' DA QUI NON SI ACCENDE NIENTE. Sull'altro progetto la scheda Casa ha
# anche un pannello che comanda le lampade dal PC, e serviva: li' una routine si
# scriveva alla cieca. Qui una routine si prova mandandola al tablet e
# guardandola succedere (pagina Routine), cioe' provando quella vera - compresi
# i passi che accendono la radio, che il PC non saprebbe fare. Una sola
# implementazione del protocollo, ed e' quella che poi gira davvero in casa.

$pagLuci = $pagine[$PAG_LUCI]

$script:trovate = @()
$script:sessioneTuya = $null
$script:qrCronometro = $null

$pagLuci.Controls.Add((Nuova-Intestazione $PAG_LUCI))

# ---- l'elenco che va sul tablet ----------------------------------------------

$boxElenco = Nuovo-Pannello 0 44 560 306
$pagLuci.Controls.Add($boxElenco)

$boxElenco.Titolo = 'QUELLE CHE IL TABLET CONOSCE'

$grigliaLuci = Nuova-Griglia 16 36 528 186 @(
    @('nome', 110), @('indirizzo', 106), @('chiave', 66), @('vers', 40), @('tinta', 74), @('id', 110))
$boxElenco.Controls.Add($grigliaLuci)

$tLuceNuova = Nuovo-Tasto 'Aggiungi' 16 232 124 30 $cMedio
$boxElenco.Controls.Add($tLuceNuova)
$tLuceModifica = Nuovo-Tasto 'Modifica' 148 232 124 30 $cMedio
$boxElenco.Controls.Add($tLuceModifica)
$tLuceTogli = Nuovo-Tasto 'Togli' 280 232 124 30 $cRosso
$boxElenco.Controls.Add($tLuceTogli)
$tLuceProva = Nuovo-Tasto 'Accendi dal tablet' 412 232 132 30 $cMedio
$boxElenco.Controls.Add($tLuceProva)

$tLuciLeggi = Nuovo-Tasto 'Rileggi dal tablet' 16 268 200 30 $cMedio
$boxElenco.Controls.Add($tLuciLeggi)
$tLuciManda = Tasto-Primario (Nuovo-Tasto 'Manda al tablet' 224 268 320 30 $cGiallo) 'spunta'
$boxElenco.Controls.Add($tLuciManda)

# ---- chi si sente in casa ----------------------------------------------------

$boxTrova = Nuovo-Pannello 576 44 362 306
$pagLuci.Controls.Add($boxTrova)

$boxTrova.Titolo = 'CHI SI SENTE IN CASA'

# Un paragrafo e non un'etichetta con l'altezza scritta a mano: quarantasei
# punti erano due righe e mezzo di un testo che ne vuole tre, e l'ultima si
# leggeva "quindi serve q". Adesso l'altezza la decide il testo.
$eTrova = Nuovo-Paragrafo 16 34 330 (
    "Non si bussa a nessuno: si sta zitti e si sente chi parla. Le lampade " +
    "si annunciano ogni pochi secondi, quindi serve qualche secondo di attesa.") `
    $cTenue $fTesto
$boxTrova.Controls.Add($eTrova)

$grigliaTrovate = Nuova-Griglia 16 84 330 138 @(
    @('indirizzo', 100), @('vers', 40), @('gia''', 44), @('id', 140))
$boxTrova.Controls.Add($grigliaTrovate)

$tAscolta = Nuovo-Tasto 'Ascolta per 8 secondi' 16 232 330 30 $cGiallo
$boxTrova.Controls.Add($tAscolta)
$tAggiungiTrovata = Nuovo-Tasto 'Aggiungi all''elenco quella scelta' 16 268 330 30 $cMedio
$boxTrova.Controls.Add($tAggiungiTrovata)

# ---- le chiavi ---------------------------------------------------------------

# Ventisei punti piu' alto di prima, ed e' tutto quello che la pagina aveva
# ancora da dare. Servono a far entrare per intero i quattro passi numerati:
# con l'altezza di prima se ne vedevano due e mezzo, e il terzo - quello che
# dice di inquadrare il codice - stava sotto il bordo. Il resto (le due righe
# finali su quello che NON serve) puo' restare sotto la barra: e' un sollievo,
# non un'istruzione.
$boxChiavi = Nuovo-Pannello 0 360 938 240
$pagLuci.Controls.Add($boxChiavi)

$boxChiavi.Titolo = 'LE CHIAVI LOCALI, UNA VOLTA SOLA'

# LA SPIEGAZIONE CI STA TUTTA, adesso. E' lunga dodici righe e il pannello ne
# mostrava sette, con l'ottava tagliata a meta' dell'altezza delle lettere:
# sembrava un difetto di disegno invece che un testo che continua. I ventisei
# punti in piu' del pannello sono tutto quello che la pagina aveva ancora da
# dare, e bastavano.
$boxChiavi.Controls.Add((Nuovo-Testo 18 40 470 158 @'
Ogni lampada ha una chiave di sedici caratteri, generata da Tuya quando l'hai
accoppiata con l'app Smart Life. Senza quella non risponde a nessuno, e l'app
non la mostra. Serve internet solo per questi quattro passi:

  1. Nell'app Smart Life: Io -> Impostazioni -> Account e sicurezza ->
     Codice utente. Copialo qui sotto.
  2. Premi "Rileva chiavi": compare un codice QR.
  3. Nell'app, icona della scansione in alto a destra, inquadra, conferma.
  4. Le chiavi entrano nelle righe, insieme ai nomi che hai dato nell'app.

Nessun account da sviluppatore, nessun progetto cloud, nessun data center da
indovinare, nessuna prova che scade dopo un mese.
'@))

$boxChiavi.Controls.Add((Nuova-Etichetta 'Codice utente' 18 204 100 $cTenue $fTesto))
$campoUtente = Nuovo-Campo 118 200 200
$boxChiavi.Controls.Add($campoUtente)

$tChiavi = Nuovo-Tasto 'Rileva chiavi' 330 200 158 28 $cGiallo
$tChiavi.Icona = 'cerca'
$boxChiavi.Controls.Add($tChiavi)

$riquadroQr = New-Object System.Windows.Forms.PictureBox
$riquadroQr.Location = New-Object System.Drawing.Point(510, 40)
$riquadroQr.Size = New-Object System.Drawing.Size(150, 150)
$riquadroQr.BackColor = $cRilievo
$riquadroQr.SizeMode = 'Zoom'
$boxChiavi.Controls.Add($riquadroQr)

$eQr = Nuova-Etichetta 'Il codice compare qui dopo aver premuto "Rileva chiavi".' 680 40 240 $cTenue $fTesto
$eQr.Height = 150
$boxChiavi.Controls.Add($eQr)

# =============================================================== l'elenco ====

function Ridisegna-Luci {
    $grigliaLuci.BeginUpdate()
    $grigliaLuci.Items.Clear()
    foreach ($l in @($script:config.luci)) {
        $chiave = [string]$l.chiave
        $segnoChiave = $(if ($chiave.Length -eq 16) { 'c''e''' } else { 'MANCA' })
        $r = New-Object System.Windows.Forms.ListViewItem([string]$l.nome)
        [void]$r.SubItems.Add([string]$l.ip)
        [void]$r.SubItems.Add($segnoChiave)
        [void]$r.SubItems.Add([string]$l.versione)
        [void]$r.SubItems.Add([string]$l.colore)
        [void]$r.SubItems.Add([string]$l.id)
        # Una riga senza chiave non e' una lampada a meta': e' un pulsante che
        # dira' sempre "non risponde". Il tablet la salta, e qui si vede perche'.
        if ($chiave.Length -ne 16) { $r.ForeColor = $script:cRosso }
        [void]$grigliaLuci.Items.Add($r)
    }
    $grigliaLuci.EndUpdate()
    Segna-Trovate
}

$script:ridisegna += { Ridisegna-Luci }

function Luce-Scelta {
    if ($grigliaLuci.SelectedIndices.Count -eq 0) { return -1 }
    return $grigliaLuci.SelectedIndices[0]
}

<##
 # La finestrella per una lampada. Torna la lampada, o null se si annulla.
 #
 # E' una finestra e non sei caselle dentro la pagina perche' una lampada ha sei
 # campi e la pagina ne mostra gia' tre riquadri: mettere anche un modulo
 # avrebbe voluto dire o una pagina piena o dei campi cosi' stretti da non
 # poterci leggere dentro un identificativo di ventidue caratteri.
 #>
function Finestra-Lampada($lampada) {
    $largo = 470
    $fin = Nuova-Finestrella $(if ($lampada) { 'Questa lampada' } else { 'Una lampada nuova' }) `
        'quello che serve per parlarle in locale, senza passare da nessun cloud' `
        'lampada_piena' $cGiallo $largo

    $campi = @{}
    $VOCI = @(
        @('nome',     'Nome',           'Come la chiami a voce: "accendi la camera"'),
        @('id',       'Identificativo', 'Quello che non cambia mai. Lo trova il tasto "Ascolta"'),
        @('ip',       'Indirizzo',      'Il punto di partenza: se cambia, Assistente Home si riallinea da sola'),
        @('chiave',   'Chiave',         'Sedici caratteri, presi dal codice QR'),
        @('versione', 'Protocollo',     '3.3 oppure 3.4: la dice l''annuncio che la lampada manda in rete')
    )

    # Ogni riga dice dove finisce e la prossima parte da li'. Prima le altezze
    # erano un passo fisso di 44 punti, e la spiegazione piu' lunga - quella del
    # protocollo - finiva sotto la riga dopo.
    $y = 0
    foreach ($v in $VOCI) {
        $c = Nuovo-Campo 0 0 ($largo - 112)
        if ($lampada) { $c.Text = [string]$lampada.($v[0]) }
        $campi[$v[0]] = $c
        $y = Riga-Modulo $fin.corpo $y $v[1] $c $v[2] $largo
    }

    $partenza = '#F2D06B'
    if ($lampada -and $lampada.colore) { $partenza = [string]$lampada.colore }
    $tinta = Tasto-Colore $partenza
    $y = Riga-Modulo $fin.corpo $y 'Tinta' $tinta `
        'Come si riconosce da lontano, qui e sul tablet. Premi per cambiarla.' $largo

    $no = Tasto-Fondo 'Lascia stare' $cTenue '' 'Cancel' 'Quieto'
    $ok = Tasto-Fondo 'Va bene' $cGiallo 'spunta' 'OK' 'Primario'
    if ((Apri-Finestrella $fin $y @($no, $ok)) -ne 'OK') { return $null }

    $versione = $campi['versione'].Text.Trim()
    if ($versione -ne '3.4') { $versione = '3.3' }
    return [ordered]@{
        nome     = $campi['nome'].Text.Trim()
        id       = $campi['id'].Text.Trim()
        ip       = $campi['ip'].Text.Trim()
        chiave   = $campi['chiave'].Text.Trim()
        versione = $versione
        colore   = $tinta.Text
    }
}

$tLuceNuova.Add_Click({
    $nuova = Finestra-Lampada $null
    if (-not $nuova) { return }
    $script:config.luci = @($script:config.luci) + $nuova
    Registra "Aggiunta $($nuova.nome). Ricordati di mandarla al tablet."
    Config-Cambiata
})

$tLuceModifica.Add_Click({
    $i = Luce-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una lampada.'; return }
    $cambiata = Finestra-Lampada $script:config.luci[$i]
    if (-not $cambiata) { return }
    $tutte = @($script:config.luci)
    $tutte[$i] = $cambiata
    $script:config.luci = $tutte
    Registra "Cambiata $($cambiata.nome)."
    Config-Cambiata
})

$tLuceTogli.Add_Click({
    $i = Luce-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una lampada.'; return }
    $quale = $script:config.luci[$i]
    if (-not (Chiedi ("Tolgo $($quale.nome) dall'elenco?" + [Environment]::NewLine + [Environment]::NewLine +
                      "Sparisce dal tablet appena mandi la configurazione. La lampada resta dov'e', " +
                      "e la sua chiave non cambia: si puo' rimettere.") `
                     'Tolgo questa lampada?' 'Togli la lampada' -Pericolo)) { return }
    $script:config.luci = @(@($script:config.luci) | Where-Object { $_.id -ne $quale.id })
    Registra "Tolta $($quale.nome)."
    Config-Cambiata
})

<##
 # Accende e spegne la lampada scelta, dal tablet.
 #
 # Non dal PC: il protocollo sta di la', e la prova che conta e' "il tablet
 # riesce a parlarle", non "il PC ci riesce". Sono due cose diverse - il PC ha
 # sempre la tabella degli indirizzi hardware calda, il tablet ci arriva freddo
 # ogni volta che il Wi-Fi si riaggancia - ed e' esattamente la differenza che
 # ha fatto perdere mezza giornata sull'altro progetto.
 #>
$tLuceProva.Add_Click({
    $i = Luce-Scelta
    if ($i -lt 0) { Registra 'Scegli prima una lampada.'; return }
    $quale = $script:config.luci[$i]
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    $nome = ([string]$quale.nome).ToLower()
    Sh ("am broadcast -a dev.casa.DI --es frase 'accendi " + $nome + "'") | Out-Null
    Registra "Chiesto al tablet di accendere $($quale.nome). Se non succede niente, guarda il registro di Assistente Home."
})

$tLuciLeggi.Add_Click({ Leggi-Config-Dal-Tablet | Out-Null })
$tLuciManda.Add_Click({ Manda-Config-Al-Tablet | Out-Null })

# =============================================================== l'ascolto ===

function Segna-Trovate {
    $noti = @{}
    foreach ($l in @($script:config.luci)) { $noti[[string]$l.id] = $true }
    $grigliaTrovate.BeginUpdate()
    $grigliaTrovate.Items.Clear()
    foreach ($t in $script:trovate) {
        $r = New-Object System.Windows.Forms.ListViewItem([string]$t.ip)
        [void]$r.SubItems.Add([string]$t.versione)
        [void]$r.SubItems.Add($(if ($noti.ContainsKey([string]$t.id)) { 'si' } else { 'no' }))
        [void]$r.SubItems.Add([string]$t.id)
        if ($noti.ContainsKey([string]$t.id)) { $r.ForeColor = $script:cTenue }
        [void]$grigliaTrovate.Items.Add($r)
    }
    $grigliaTrovate.EndUpdate()
}

$tAscolta.Add_Click({
    $this.Enabled = $false
    $this.Text = 'sto ascoltando...'
    # Refresh e non un thread: otto secondi di finestra ferma sono accettabili
    # perche' chi ha premuto sa di stare aspettando, e un thread qui vorrebbe
    # dire portarsi dietro il marshalling verso l'interfaccia per una cosa che
    # si fa una volta ogni tanto.
    $form.Refresh()
    try {
        $annunci = [Casa.Annunci]::Ascolta(8)
    } catch {
        Registra "L'ascolto non e'' riuscito: $($_.Exception.Message)"
        $annunci = @()
    }
    $this.Enabled = $true
    $this.Text = 'Ascolta per 8 secondi'

    $viste = @{}
    $nuove = @()
    foreach ($a in $annunci) {
        try { $o = $a | ConvertFrom-Json } catch { continue }
        $id = ''
        if ($o.PSObject.Properties['gwId']) { $id = [string]$o.gwId }
        elseif ($o.PSObject.Properties['devId']) { $id = [string]$o.devId }
        if (-not $id) { continue }
        if ($viste.ContainsKey($id)) { continue }
        $viste[$id] = $true
        $versione = '3.3'
        if ($o.PSObject.Properties['version']) { $versione = [string]$o.version }
        $nuove += @{ id = $id; ip = [string]$o.ip; versione = $versione }
    }
    $script:trovate = $nuove
    Segna-Trovate
    if ($nuove.Count -eq 0) {
        Registra ("Non ho sentito nessuno. Il PC e' sulla stessa rete del Wi-Fi di casa? " +
                  "E il firewall di Windows lascia passare le porte 6666 e 6667 in entrata?")
    } else {
        Registra "Sentiti $($nuove.Count) apparecchi Tuya."
    }
})

$tAggiungiTrovata.Add_Click({
    if ($grigliaTrovate.SelectedIndices.Count -eq 0) { Registra 'Scegli prima una riga da "chi si sente in casa".'; return }
    $t = $script:trovate[$grigliaTrovate.SelectedIndices[0]]
    foreach ($l in @($script:config.luci)) {
        if ([string]$l.id -eq [string]$t.id) {
            # Non e' un errore: l'indirizzo puo' essere cambiato, ed e'
            # l'unica cosa che invecchia.
            $l.ip = $t.ip
            Registra "$($l.nome) c'era gia': ho aggiornato l'indirizzo a $($t.ip)."
            Config-Cambiata
            return
        }
    }
    $nuova = [ordered]@{
        nome = ''; id = [string]$t.id; ip = [string]$t.ip; chiave = ''
        versione = [string]$t.versione; colore = '#F2D06B'
    }
    $completata = Finestra-Lampada $nuova
    if (-not $completata) { return }
    $script:config.luci = @($script:config.luci) + $completata
    Registra "Aggiunta $($completata.nome)."
    Config-Cambiata
})

# =============================================================== le chiavi ===

function Disegna-Qr([string]$testo) {
    $m = [Casa.Qr]::Per($testo)
    if ($null -eq $m) { return $null }
    $lato = $m.GetLength(0)
    # Quattro moduli di margine bianco: senza quella cornice - la "zona quieta"
    # - meta' dei lettori non aggancia il codice, e sembra che il codice sia
    # sbagliato.
    $bordo = 4
    $passo = 4
    $misura = ($lato + $bordo * 2) * $passo
    $b = New-Object System.Drawing.Bitmap($misura, $misura)
    $g = [System.Drawing.Graphics]::FromImage($b)
    $g.Clear([System.Drawing.Color]::White)
    $pennello = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::Black)
    for ($y = 0; $y -lt $lato; $y++) {
        for ($x = 0; $x -lt $lato; $x++) {
            if ($m[$y, $x]) {
                $g.FillRectangle($pennello, ($x + $bordo) * $passo, ($y + $bordo) * $passo, $passo, $passo)
            }
        }
    }
    $pennello.Dispose()
    $g.Dispose()
    return $b
}

$tChiavi.Add_Click({
    $utente = $campoUtente.Text.Trim()
    if ($utente.Length -eq 0) {
        Registra 'Serve il codice utente: nell''app Smart Life, Io -> Impostazioni -> Account e sicurezza -> Codice utente.'
        return
    }
    $guaio = [Casa.Gcm]::Prova()
    if ($guaio) { Registra "Non posso chiedere le chiavi: $guaio"; return }

    $script:sessioneTuya = New-Object Casa.Sessione
    $errore = [Casa.Chiavi]::ChiediCodice($utente, $script:sessioneTuya)
    if ($errore) {
        Registra "Tuya ha detto di no: $errore"
        $eQr.Text = "Non ha funzionato: $errore"
        $eQr.ForeColor = $script:cRosso
        return
    }

    $testo = [Casa.Chiavi]::TestoQr($script:sessioneTuya.Codice)
    $riquadroQr.Image = Disegna-Qr $testo
    $eQr.ForeColor = $script:cAmbra
    $eQr.Text = ("Inquadra il codice con l'app Smart Life: icona della scansione in alto a " +
                 "destra, poi conferma sul telefono.`r`n`r`nAspetto. Il codice scade in " +
                 "pochi minuti: se scade, ripremi il tasto.")
    Registra 'Codice pronto: inquadralo con Smart Life.'

    # Si guarda ogni due secondi se qualcuno ha confermato. Un cronometro e non
    # un ciclo: durante l'attesa la finestra deve restare viva, perche' il
    # codice va guardato mentre si aspetta.
    if ($script:qrCronometro) { $script:qrCronometro.Stop() }
    $script:qrCronometro = New-Object System.Windows.Forms.Timer
    $script:qrCronometro.Interval = 2000
    $script:qrScadenza = (Get-Date).AddMinutes(4)
    $script:qrUtente = $utente
    $script:qrCronometro.Add_Tick({ Guarda-Se-Entrato })
    $script:qrCronometro.Start()
})

function Guarda-Se-Entrato {
    if ((Get-Date) -gt $script:qrScadenza) {
        $script:qrCronometro.Stop()
        $eQr.Text = 'Il codice e'' scaduto senza che nessuno lo inquadrasse. Ripremi "Rileva chiavi".'
        $eQr.ForeColor = $script:cRosso
        return
    }
    $entrato = $false
    $errore = [Casa.Chiavi]::Esito($script:sessioneTuya, $script:qrUtente, [ref]$entrato)
    if ($errore) {
        # Un errore di rete durante l'attesa non e' un no: si riprova al giro
        # dopo, e si dice solo nel registro.
        Registra "Mentre aspettavo: $errore"
        return
    }
    if (-not $entrato) { return }

    $script:qrCronometro.Stop()
    $eQr.ForeColor = $script:cVerde
    $eQr.Text = "Entrato come $($script:sessioneTuya.Utente). Chiedo l'elenco..."
    $form.Refresh()
    Prendi-Le-Chiavi
}

<##
 # L'elenco dei dispositivi dell'account, con dentro la chiave di ognuno.
 #
 # Due chiamate: prima le "case" dell'account, poi i dispositivi di ognuna. La
 # seconda vuole l'identificativo della casa, che si sa solo dopo la prima -
 # quindi non si possono fare insieme.
 #>
function Prendi-Le-Chiavi {
    $risultato = ''
    $errore = [Casa.Chiavi]::Chiamata($script:sessioneTuya, '/v1.0/m/life/users/homes', $null, [ref]$risultato)
    if ($errore) { Registra "Non sono riuscito a chiedere le case: $errore"; return }

    try { $case = @($risultato | ConvertFrom-Json) } catch {
        Registra 'La risposta con le case non si legge.'
        return
    }

    $tutti = @()
    foreach ($casa in $case) {
        $idCasa = ''
        if ($casa.PSObject.Properties['ownerId']) { $idCasa = [string]$casa.ownerId }
        if (-not $idCasa) { continue }
        $r2 = ''
        $e2 = [Casa.Chiavi]::Chiamata($script:sessioneTuya, '/v1.0/m/life/ha/home/devices',
                                      ('{"homeId":"' + $idCasa + '"}'), [ref]$r2)
        if ($e2) { Registra "Non sono riuscito a chiedere i dispositivi: $e2"; continue }
        try { $tutti += @($r2 | ConvertFrom-Json) } catch { }
    }

    if ($tutti.Count -eq 0) {
        $eQr.Text = 'L''account non ha dispositivi, o non me li ha voluti dire.'
        $eQr.ForeColor = $script:cAmbra
        return
    }

    $noti = @{}
    foreach ($l in @($script:config.luci)) { $noti[[string]$l.id] = $l }

    $aggiornate = 0; $aggiunte = 0
    $elenco = @($script:config.luci)
    foreach ($d in $tutti) {
        $id = [string]$d.id
        $chiave = [string]$d.local_key
        if (-not $id -or -not $chiave) { continue }
        # La categoria e' quella che dice se e' una lampada o una presa: nel
        # registro ci va, perche' e' cosi' che si e' scoperto che il terzo
        # apparecchio di questa casa e' una presa.
        Registra ("Tuya: {0}  ({1})  {2}  chiave presa" -f $d.name, $d.category, $id)
        if ($noti.ContainsKey($id)) {
            $noti[$id].chiave = $chiave
            if ($d.ip) { $noti[$id].ip = [string]$d.ip }
            $aggiornate++
        } else {
            $elenco += [ordered]@{
                nome     = [string]$d.name
                id       = $id
                ip       = [string]$d.ip
                chiave   = $chiave
                versione = '3.3'
                colore   = '#F2D06B'
            }
            $aggiunte++
        }
    }
    $script:config.luci = $elenco
    Config-Cambiata

    $eQr.ForeColor = $script:cVerde
    $eQr.Text = ("Fatto: $aggiornate chiavi rimesse, $aggiunte lampade nuove.`r`n`r`n" +
                 "Controlla nomi, tinte e protocollo nell'elenco, poi manda al tablet. " +
                 "La versione del protocollo la dice l'annuncio: usa 'Ascolta'.")
    Registra "Chiavi: $aggiornate aggiornate, $aggiunte aggiunte. Adesso mandale al tablet."
}
