# Pagina "I comandi": cosa Casa capisce, cosa esegue e come risponde.
#
# ERA UN ELENCO DA LEGGERE. Fino a ieri questa pagina mostrava, in una casella
# di testo, le cinquanta cose che Casa sa fare: utile per sapere cosa dire,
# inutile per cambiare qualcosa. Le due domande che ci si fa davanti a un
# assistente sono pero' "perche' non capisce questa frase" e "perche' risponde
# cosi'", e a tutte e due la risposta era: si ricompila.
#
# ADESSO SI SCRIVONO. Un comando qui e' fatto di tre elenchi, e sono esattamente
# le tre cose che servono:
#
#   QUANDO   le frasi che lo fanno scattare. Basta che quella detta ne
#            contenga una.
#   FA       i passi, gli stessi delle routine: una lampada, una frase girata
#            a Casa, un'attesa. Quindi da qui si comanda tutto quello che Casa
#            sa fare, non solo le luci.
#   DICE     come risponde. Piu' di una, e ne sceglie una a caso - una risposta
#            sempre identica, sentita venti volte al giorno, e' la cosa che piu'
#            fa sembrare che dall'altra parte non ci sia nessuno.
#
# VENGONO PRIMA DI QUELLI DI FABBRICA, ed e' quello che li rende utili davvero:
# una regola scritta qui prende la frase prima che ci arrivi quella del codice,
# quindi si puo' cambiare una risposta senza toccare il codice. Il prezzo e' che
# una regola larga ruba frasi che non voleva, e per questo ogni scatto finisce
# nel registro col nome della regola che ha vinto.
#
# I COMANDI DI FABBRICA RESTANO, e stanno dietro il tasto "Cosa capisce gia'":
# sono cinquanta righe che si leggono una volta, non una cosa da tenere davanti
# mentre si lavora.

$pagCmd = $pagine[$PAG_CMD]

$pagCmd.Controls.Add((Nuova-Intestazione $PAG_CMD))

# ---- i comandi scritti qui ---------------------------------------------------

$boxCmd = Nuovo-Pannello 0 44 420 500
$boxCmd.Titolo = 'i comandi scritti da te'
$pagCmd.Controls.Add($boxCmd)

$grigliaCmd = Nuova-Griglia 16 36 388 232 @(
    @('nome', 170), @('frasi', 50), @('fa', 40), @('dice', 50))
$grigliaCmd.Tinta = $cBlu
$boxCmd.Controls.Add($grigliaCmd)

$tCmdNuovo = Tasto-Primario (Nuovo-Tasto 'Nuovo comando' 16 278 200 30 $cBlu) 'piu'
$boxCmd.Controls.Add($tCmdNuovo)
$tCmdRinomina = Nuovo-Tasto 'Rinomina' 224 278 90 30 $cMedio
$boxCmd.Controls.Add($tCmdRinomina)
$tCmdTogli = Nuovo-Tasto 'Togli' 322 278 82 30 $cRosso
$boxCmd.Controls.Add($tCmdTogli)

$tCmdSu = Nuovo-Tasto 'Su' 16 314 110 28 $cTenue
$tCmdSu.Icona = 'su'
$boxCmd.Controls.Add($tCmdSu)
$tCmdGiu = Nuovo-Tasto 'Giu''' 134 314 110 28 $cTenue
$tCmdGiu.Icona = 'giu'
$boxCmd.Controls.Add($tCmdGiu)
$tCmdProva = Nuovo-Tasto 'Prova questo' 252 314 152 28 $cAmbra
$tCmdProva.Icona = 'avvia'
$boxCmd.Controls.Add($tCmdProva)

$boxCmd.Controls.Add((Nuovo-Testo 16 348 388 66 @'
L'ordine conta: la prima regola che riconosce la
frase se la prende, e le altre non la vedono piu'.
Una regola larga - "luce" - mettila in fondo, o si
prendera' anche "che luce c'e' fuori".
'@))

$tCmdBase = Nuovo-Tasto 'Cosa capisce gia''' 16 416 190 30 $cTenue
$tCmdBase.Icona = 'cerca'
$boxCmd.Controls.Add($tCmdBase)

$tCmdRisposte = Nuovo-Tasto 'Le risposte di fabbrica' 214 416 190 30 $cTenue
$tCmdRisposte.Icona = 'regola'
$boxCmd.Controls.Add($tCmdRisposte)

$tCmdLeggi = Nuovo-Tasto 'Rileggi dal tablet' 16 456 190 30 $cMedio
$boxCmd.Controls.Add($tCmdLeggi)
$tCmdManda = Tasto-Primario (Nuovo-Tasto 'Manda al tablet' 214 456 190 30 $cBlu) 'spunta'
$boxCmd.Controls.Add($tCmdManda)

# ---- il comando scelto -------------------------------------------------------

$boxDettaglio = Nuovo-Pannello 436 44 502 500
$pagCmd.Controls.Add($boxDettaglio)

$eCmdTitolo = Nuova-Etichetta 'nessun comando scritto' 18 14 460 $cTesto $fVoce
$eCmdTitolo.Height = 26
$boxDettaglio.Controls.Add($eCmdTitolo)

# QUANDO
$boxDettaglio.Controls.Add((Nuova-Etichetta 'QUANDO SI DICE' 18 48 110 $cTenue $fMedio))
$boxDettaglio.Controls.Add((Nuova-Etichetta 'basta che la frase detta contenga una di queste' 132 48 348 $cTenue $fNota))
$elencoQuando = Nuovo-Elenco 18 70 300 84
$elencoQuando.Tinta = $cBlu
$boxDettaglio.Controls.Add($elencoQuando)
$tQuandoPiu = Nuovo-Tasto 'Aggiungi' 326 70 152 26 $cMedio
$boxDettaglio.Controls.Add($tQuandoPiu)
$tQuandoMeno = Nuovo-Tasto 'Togli' 326 102 152 26 $cRosso
$boxDettaglio.Controls.Add($tQuandoMeno)

# FA
$boxDettaglio.Controls.Add((Nuova-Etichetta 'CHE COSA FA' 18 168 90 $cTenue $fMedio))
$boxDettaglio.Controls.Add((Nuova-Etichetta 'gli stessi passi delle routine' 112 168 360 $cTenue $fNota))
$grigliaFa = Nuova-Griglia 18 190 300 96 @(
    @('cosa', 56), @('a chi', 100), @('azione', 66), @('valore', 60))
$grigliaFa.Tinta = $cBlu
$boxDettaglio.Controls.Add($grigliaFa)
$tFaPiu = Nuovo-Tasto 'Aggiungi' 326 190 152 26 $cMedio
$boxDettaglio.Controls.Add($tFaPiu)
$tFaModifica = Nuovo-Tasto 'Modifica' 326 222 152 26 $cMedio
$boxDettaglio.Controls.Add($tFaModifica)
$tFaMeno = Nuovo-Tasto 'Togli' 326 254 152 26 $cRosso
$boxDettaglio.Controls.Add($tFaMeno)

# DICE
$boxDettaglio.Controls.Add((Nuova-Etichetta 'COME RISPONDE' 18 300 110 $cTenue $fMedio))
$boxDettaglio.Controls.Add((Nuova-Etichetta 'piu'' di una: ne sceglie una a caso' 132 300 348 $cTenue $fNota))
$elencoDice = Nuovo-Elenco 18 322 300 84
$elencoDice.Tinta = $cBlu
$boxDettaglio.Controls.Add($elencoDice)
$tDicePiu = Nuovo-Tasto 'Aggiungi' 326 322 152 26 $cMedio
$boxDettaglio.Controls.Add($tDicePiu)
$tDiceMeno = Nuovo-Tasto 'Togli' 326 354 152 26 $cRosso
$boxDettaglio.Controls.Add($tDiceMeno)

$boxDettaglio.Controls.Add((Nuovo-Testo 18 412 460 80 @'
Nelle risposte si possono mettere dei segnaposto, e li riempie Assistente Home:
  {ora}   "Sono le 7 e 20."       {luci}   "due luci accese"
  {data}  "Oggi e martedi 8..."   {nome}   come si chiama
  {suona} che cosa sta suonando
Uno scritto male resta com'e', invece di sparire senza dire niente.
'@))

# =============================================================== il disegno ==

function Ridisegna-Comandi {
    $scelto = -1
    if ($grigliaCmd.SelectedIndices.Count -gt 0) { $scelto = $grigliaCmd.SelectedIndices[0] }

    $grigliaCmd.BeginUpdate()
    $grigliaCmd.Items.Clear()
    foreach ($c in @($script:config.comandi)) {
        $r = New-Object System.Windows.Forms.ListViewItem([string]$c.nome)
        [void]$r.SubItems.Add([string]@($c.quando).Count)
        [void]$r.SubItems.Add([string]@($c.passi).Count)
        [void]$r.SubItems.Add([string]@($c.dici).Count)
        # Una regola che non fa e non dice niente non scatta mai, e il tablet la
        # salta dicendolo nel registro: qui si vede prima di scoprirlo di la'.
        if (@($c.passi).Count -eq 0 -and @($c.dici).Count -eq 0) { $r.ForeColor = $script:cRosso }
        elseif (@($c.quando).Count -eq 0) { $r.ForeColor = $script:cRosso }
        [void]$grigliaCmd.Items.Add($r)
    }
    $grigliaCmd.EndUpdate()

    if ($scelto -ge 0 -and $scelto -lt $grigliaCmd.Items.Count) {
        $grigliaCmd.Items[$scelto].Selected = $true
    } elseif ($grigliaCmd.Items.Count -gt 0) {
        $grigliaCmd.Items[0].Selected = $true
    }
    Ridisegna-Dettaglio
}

$script:ridisegna += { Ridisegna-Comandi }

function Comando-Scelto {
    if ($grigliaCmd.SelectedIndices.Count -eq 0) { return -1 }
    return $grigliaCmd.SelectedIndices[0]
}

# Come per le routine: per DISEGNARE, "nessuna scelta" e "la prima" vogliono
# dire la stessa cosa; per TOGLIERE non lo vogliono affatto.
function Comando-Da-Mostrare {
    $i = Comando-Scelto
    if ($i -lt 0 -and @($script:config.comandi).Count -gt 0) { return 0 }
    return $i
}

function Ridisegna-Dettaglio {
    $i = Comando-Da-Mostrare
    $elencoQuando.Items.Clear()
    $elencoDice.Items.Clear()
    $grigliaFa.Items.Clear()
    if ($i -lt 0) {
        $eCmdTitolo.Text = 'nessun comando scritto'
        $eCmdTitolo.ForeColor = $script:cTenue
        return
    }
    $c = @($script:config.comandi)[$i]
    $eCmdTitolo.Text = [string]$c.nome
    $eCmdTitolo.ForeColor = $script:cTesto

    foreach ($q in @($c.quando)) { [void]$elencoQuando.Items.Add([string]$q) }
    foreach ($d in @($c.dici))   { [void]$elencoDice.Items.Add([string]$d) }

    $grigliaFa.BeginUpdate()
    foreach ($p in @($c.passi)) {
        $cosa = [string]$p.cosa
        if (-not $cosa) { $cosa = 'luce' }
        $r = New-Object System.Windows.Forms.ListViewItem($cosa)
        if ($cosa -eq 'luce') {
            [void]$r.SubItems.Add((Nome-Luce ([string]$p.bersaglio)))
            [void]$r.SubItems.Add((Nome-Azione ([string]$p.azione)))
            [void]$r.SubItems.Add([string]$p.valore)
        } elseif ($cosa -eq 'frase') {
            [void]$r.SubItems.Add('ad Assistente Home')
            [void]$r.SubItems.Add('dille')
            [void]$r.SubItems.Add('"' + [string]$p.valore + '"')
        } else {
            [void]$r.SubItems.Add('-')
            [void]$r.SubItems.Add('aspetta')
            [void]$r.SubItems.Add([string]$p.valore + ' s')
        }
        [void]$grigliaFa.Items.Add($r)
    }
    $grigliaFa.EndUpdate()
}

$grigliaCmd.Add_SelectedIndexChanged({ Ridisegna-Dettaglio })

# =============================================================== i tasti =====

function Salva-Comando($i, $c) {
    $tutti = @($script:config.comandi)
    $tutti[$i] = $c
    $script:config.comandi = $tutti
    Config-Cambiata
}

<##
 # Una copia modificabile del comando scelto.
 #
 # Quello che arriva da ConvertFrom-Json e' un PSCustomObject, e alle sue liste
 # non si aggiunge niente: sono array di lunghezza fissa. Ricostruirlo ha anche
 # un secondo effetto buono - il formato che si riscrive e' sempre lo stesso,
 # qualunque cosa sia arrivata dal tablet.
 #>
function Copia-Comando($c) {
    return [ordered]@{
        nome   = [string]$c.nome
        quando = @(@($c.quando) | ForEach-Object { [string]$_ })
        passi  = @($c.passi)
        dici   = @(@($c.dici) | ForEach-Object { [string]$_ })
    }
}

$tCmdNuovo.Add_Click({
    $nome = Chiedi-Testo ("Come si chiama questo comando?" + "`n`n" +
                          "E' solo un'etichetta per te e per il registro: a farlo scattare " +
                          "sono le frasi, che si aggiungono dopo.") ''
    if ($null -eq $nome -or $nome.Trim().Length -eq 0) { return }
    $script:config.comandi = @($script:config.comandi) + [ordered]@{
        nome = $nome.Trim(); quando = @(); passi = @(); dici = @()
    }
    Registra "Nuovo comando: $($nome.Trim()). Adesso dagli almeno una frase."
    Config-Cambiata
    if ($grigliaCmd.Items.Count -gt 0) { $grigliaCmd.Items[$grigliaCmd.Items.Count - 1].Selected = $true }
})

$tCmdRinomina.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0) { Registra 'Scegli prima un comando.'; return }
    $c = Copia-Comando @($script:config.comandi)[$i]
    $nome = Chiedi-Testo 'Come si chiama?' $c.nome
    if ($null -eq $nome -or $nome.Trim().Length -eq 0) { return }
    $c.nome = $nome.Trim()
    Salva-Comando $i $c
})

$tCmdTogli.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0) { Registra 'Scegli prima un comando.'; return }
    $tutti = @($script:config.comandi)
    if (-not (Chiedi ("Tolgo il comando ""$($tutti[$i].nome)""?" + [Environment]::NewLine +
                      [Environment]::NewLine +
                      "Sparisce dal tablet appena mandi la configurazione. Le frasi che " +
                      "faceva scattare tornano a quello che ne fa Assistente Home di suo.") `
                     'Tolgo questo comando?' 'Togli il comando' -Pericolo)) { return }
    $restano = @()
    for ($x = 0; $x -lt $tutti.Count; $x++) { if ($x -ne $i) { $restano += $tutti[$x] } }
    $script:config.comandi = $restano
    Config-Cambiata
})

$tCmdSu.Add_Click({
    $i = Comando-Scelto
    $a = Sposta-Voce $script:config.comandi $i ($i - 1)
    if ($null -eq $a) { return }
    $script:config.comandi = $a
    Config-Cambiata
    $grigliaCmd.Items[$i - 1].Selected = $true
})

$tCmdGiu.Add_Click({
    $i = Comando-Scelto
    $a = Sposta-Voce $script:config.comandi $i ($i + 1)
    if ($null -eq $a) { return }
    $script:config.comandi = $a
    Config-Cambiata
    $grigliaCmd.Items[$i + 1].Selected = $true
})

# ---- le tre liste ----

$tQuandoPiu.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0) { Registra 'Scegli prima un comando.'; return }
    $f = Chiedi-Testo ("Una frase che fa scattare questo comando." + "`n`n" +
                       "Basta che quella detta la contenga: ""buonanotte"" prende anche " +
                       """casa, buonanotte a tutti"".") ''
    if ($null -eq $f -or $f.Trim().Length -eq 0) { return }
    $c = Copia-Comando @($script:config.comandi)[$i]
    $c.quando = @($c.quando) + $f.Trim().ToLower()
    Salva-Comando $i $c
})

$tQuandoMeno.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0 -or $elencoQuando.SelectedIndex -lt 0) { Registra 'Scegli prima una frase.'; return }
    $c = Copia-Comando @($script:config.comandi)[$i]
    $k = $elencoQuando.SelectedIndex
    $restano = @()
    for ($x = 0; $x -lt @($c.quando).Count; $x++) { if ($x -ne $k) { $restano += @($c.quando)[$x] } }
    $c.quando = $restano
    Salva-Comando $i $c
})

$tDicePiu.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0) { Registra 'Scegli prima un comando.'; return }
    $d = Chiedi-Testo ("Una risposta." + "`n`n" +
                       "Si possono usare i segnaposto {ora}, {data}, {suona}, {luci}, {nome}.") ''
    if ($null -eq $d -or $d.Trim().Length -eq 0) { return }
    $c = Copia-Comando @($script:config.comandi)[$i]
    $c.dici = @($c.dici) + $d.Trim()
    Salva-Comando $i $c
})

$tDiceMeno.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0 -or $elencoDice.SelectedIndex -lt 0) { Registra 'Scegli prima una risposta.'; return }
    $c = Copia-Comando @($script:config.comandi)[$i]
    $k = $elencoDice.SelectedIndex
    $restano = @()
    for ($x = 0; $x -lt @($c.dici).Count; $x++) { if ($x -ne $k) { $restano += @($c.dici)[$x] } }
    $c.dici = $restano
    Salva-Comando $i $c
})

# I passi sono gli stessi delle routine, e la finestrella e' la stessa
# (Finestra-Passo, in Routine.ps1): due editor per la stessa cosa sarebbero due
# posti in cui sbagliare, e uno dei due invecchierebbe.
$tFaPiu.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0) { Registra 'Scegli prima un comando.'; return }
    $p = Finestra-Passo $null
    if (-not $p) { return }
    $c = Copia-Comando @($script:config.comandi)[$i]
    $c.passi = @($c.passi) + $p
    Salva-Comando $i $c
})

$tFaModifica.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0 -or $grigliaFa.SelectedIndices.Count -eq 0) { Registra 'Scegli prima un passo.'; return }
    $k = $grigliaFa.SelectedIndices[0]
    $c = Copia-Comando @($script:config.comandi)[$i]
    $p = Finestra-Passo @($c.passi)[$k]
    if (-not $p) { return }
    $tutti = @($c.passi)
    $tutti[$k] = $p
    $c.passi = $tutti
    Salva-Comando $i $c
})

$tFaMeno.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0 -or $grigliaFa.SelectedIndices.Count -eq 0) { Registra 'Scegli prima un passo.'; return }
    $k = $grigliaFa.SelectedIndices[0]
    $c = Copia-Comando @($script:config.comandi)[$i]
    $restano = @()
    for ($x = 0; $x -lt @($c.passi).Count; $x++) { if ($x -ne $k) { $restano += @($c.passi)[$x] } }
    $c.passi = $restano
    Salva-Comando $i $c
})

# ---- provare, mandare, e l'elenco di fabbrica ----

<##
 # Prova il comando dicendo al tablet la sua prima frase.
 #
 # Prima si manda la configurazione: sul tablet gira quella che il tablet ha,
 # non quella che si sta scrivendo qui. E si manda la FRASE invece di chiedere
 # "esegui il comando numero tre", perche' cosi' si prova anche la parte che
 # conta di piu': che la frase lo faccia scattare davvero, e che non se la
 # prenda prima qualcun altro. Il registro dice chi l'ha presa.
 #>
$tCmdProva.Add_Click({
    $i = Comando-Scelto
    if ($i -lt 0) { Registra 'Scegli prima un comando.'; return }
    $c = @($script:config.comandi)[$i]
    $frasi = @($c.quando)
    if ($frasi.Count -eq 0) { Registra 'Questo comando non ha nessuna frase: non puo'' scattare.'; return }
    if (-not (Chiedi ("Mando la configurazione al tablet e poi gli dico ""$($frasi[0])"".`n`n" +
                      "Sul tablet succede davvero: quello che il comando fa, lo fa.") `
                     'Lo provo sul tablet?' 'Provalo')) { return }
    if (-not (Manda-Config-Al-Tablet)) { return }
    Manda-Frase ([string]$frasi[0])
    $righe = @(Adb 'logcat' '-d' '-t' '40' '-s' 'Casa:I')
    $chi = $null
    foreach ($r in $righe) { if ($r -match 'comandi: ".+" la prende (.+)$') { $chi = $Matches[1] } }
    if ($chi) { Registra "L'ha presa: $chi" }
    else { Registra 'Nessuna regola tua l''ha presa: se l''e'' presa una di fabbrica, o non ha capito.' }
})

$tCmdLeggi.Add_Click({ Leggi-Config-Dal-Tablet | Out-Null })
$tCmdManda.Add_Click({ Manda-Config-Al-Tablet | Out-Null })
$tCmdBase.Add_Click({ Mostra-Comandi-Di-Fabbrica })
$tCmdRisposte.Add_Click({ Mostra-Risposte })

# ---- provare una frase, e cosa non ha capito ---------------------------------

$boxProva = Nuovo-Pannello 0 552 938 32
$pagCmd.Controls.Add($boxProva)

$boxProva.Controls.Add((Nuova-Etichetta 'Prova una frase' 18 6 100 $cTenue $fTesto))
$campoFrase = Nuovo-Campo 122 2 380
$campoFrase.Text = 'che ore sono'
$boxProva.Controls.Add($campoFrase)

$tMandaFrase = Tasto-Primario (Nuovo-Tasto 'Dilla al tablet' 512 3 160 26 $cBlu) 'avvia'
$boxProva.Controls.Add($tMandaFrase)

$tLeggiBuchi = Nuovo-Tasto 'Cosa non ha capito' 682 3 240 26 $cMedio
$tLeggiBuchi.Icona = 'cerca'
$boxProva.Controls.Add($tLeggiBuchi)

# Qui non c'e' un tasto per il pannello delle impostazioni del tablet, e prima
# c'era: lo stesso tasto sta gia' nella pagina "Il tablet" e in Allestimento, e
# tre pulsanti uguali in tre schermate diverse sono tre posti da tenere
# d'accordo per una cosa che si preme due volte l'anno.

function Manda-Frase([string]$frase) {
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    $f = $frase.Trim()
    if ($f.Length -eq 0) { Registra 'Scrivi prima una frase.'; return }
    # L'apostrofo si protegge nel modo POSIX: si chiude la stringa, se ne mette
    # uno con l'accento inverso, e si riapre. Senza, "un'ora" spezzerebbe il
    # comando a meta' sulla shell del tablet.
    $sicura = $f.Replace("'", "'\''")
    Sh ("am broadcast -a dev.casa.DI --es frase '" + $sicura + "'") | Out-Null
    Registra ("detta al tablet: " + $f)
    $detto = Cosa-Ha-Risposto $f
    if ($detto) { Registra ("  risponde: " + $detto) }
    else { Registra '  non ha risposto niente: o non ha capito, o e'' una cosa che fa e basta.' }
}

<##
 # Cosa ha risposto il tablet all'ultima frase provata.
 #
 # PERCHE' DAL REGISTRO E NON DA UNA RISPOSTA. Il broadcast non torna indietro
 # con niente: torna quando il ricevitore ha finito. Il tablet pero' scrive
 # sempre nel registro quello che dice - anche quando lo dice a voce - e quella
 # riga e' l'unica prova di CHE COSA e' uscito davvero. Serve soprattutto a chi
 # ha appena riscritto una risposta: senza, si sente parlare il tablet
 # dall'altra stanza e si resta col dubbio che fosse ancora quella vecchia.
 #
 # Si parte dalla riga "prova:" della frase appena mandata e si guarda solo
 # quello che viene dopo: prendere l'ultima "dice:" del registro darebbe la
 # risposta di dieci minuti fa tutte le volte che questa non c'e'.
 #>
function Cosa-Ha-Risposto([string]$frase) {
    $righe = @(Adb 'logcat' '-d' '-t' '40' '-s' 'Casa:I')
    $da = -1
    for ($i = 0; $i -lt $righe.Count; $i++) {
        if ($righe[$i] -match ('prova: ' + [regex]::Escape($frase) + '\s*$')) { $da = $i }
    }
    if ($da -lt 0) { return $null }
    $dette = @()
    for ($i = $da + 1; $i -lt $righe.Count; $i++) {
        if ($righe[$i] -match 'dice: (.+)$') { $dette += $Matches[1].Trim() }
    }
    if ($dette.Count -eq 0) { return $null }
    # Piu' di una succede: "Di quanto?" e poi la conferma. Si mostrano tutte,
    # in ordine, perche' l'ordine e' la conversazione.
    return ($dette -join '  >  ')
}

<##
 # Le frasi cadute nel vuoto, dal registro del tablet.
 #
 # Sono la cosa piu' utile che ci sia per scrivere una regola nuova: si scrive
 # su quello che e' stato detto davvero, non su quello che sembrava probabile. E
 # da li' si fa un comando in un colpo, con quella frase gia' dentro.
 #>
function Leggi-Buchi {
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    $righe = Adb 'logcat' '-d' '-t' '600' '-s' 'Casa:I'
    $viste = @{}
    $trovate = @()
    foreach ($r in $righe) {
        if ($r -match 'PC nonCapito (.+)$') {
            $frase = $Matches[1].Trim()
            if ($frase.Length -gt 0 -and -not $viste.ContainsKey($frase)) {
                $viste[$frase] = $true
                $trovate += $frase
            }
        }
    }
    if ($trovate.Count -eq 0) {
        Registra 'Nessuna frase caduta nel vuoto nel registro recente.'
        return
    }
    Registra ("" + $trovate.Count + " frasi che Assistente Home non ha capito.")
    Finestra-Buchi $trovate
}

function Finestra-Buchi($frasi) {
    $largo = 560
    $fin = Nuova-Finestrella 'Cosa non ha capito' `
        'le frasi cadute nel fondo della catena, lette dal registro del tablet' `
        'cerca' $cBlu $largo
    $f = $fin.f

    $p = Nuovo-Paragrafo 0 0 $largo (
        'Le regole nuove si scrivono su queste: sono quello che e'' stato detto ' +
        'davvero, non quello che si immagina che verra'' detto. Scegline una e ' +
        'falle un comando, oppure ridilla al tablet per vedere di nuovo che ' +
        'cosa risponde.') $cMedio $fTesto
    $fin.corpo.Controls.Add($p)
    $y = $p.Height + 14

    $e = Nuovo-Elenco 0 $y $largo 260
    foreach ($x in $frasi) { [void]$e.Items.Add($x) }
    $fin.corpo.Controls.Add($e)
    $y += 260

    # UNA COPIA LOCALE DELLA CONFIGURAZIONE, e non e' pignoleria.
    #
    # I gestori qui sotto si chiudono con GetNewClosure, che porta dentro le
    # variabili LOCALI di questa funzione - e solo quelle. Dentro la chiusura
    # $script:config non e' la configurazione del programma: e' niente, e
    # scriverci sopra non da' errore e non cambia niente. Un tasto che sembra
    # premuto e non fa nulla e' il difetto piu' difficile da trovare che ci sia.
    #
    # $cfg e' lo STESSO oggetto, non una copia dei dati: cambiargli un campo
    # cambia la configurazione vera.
    $cfg = $script:config

    $tChiudi = Tasto-Fondo 'Chiudi' $cTenue '' 'Cancel' 'Quieto'
    $tRiprova = Tasto-Fondo 'Ridilla al tablet' $cMedio 'avvia'
    $tFai = Tasto-Fondo 'Fai un comando con questa frase' $cBlu 'piu' 'OK' 'Primario'

    $tFai.Add_Click({
        if ($e.SelectedIndex -lt 0) { Registra 'Scegli prima una frase.'; return }
        $frase = [string]$e.SelectedItem
        $cfg.comandi = @($cfg.comandi) + [ordered]@{
            nome = $frase; quando = @($frase); passi = @(); dici = @()
        }
        Registra "Comando nuovo da ""$frase"": adesso digli cosa fare e cosa rispondere."
        Config-Cambiata
    }.GetNewClosure())

    $tRiprova.Add_Click({
        if ($e.SelectedIndex -ge 0) { Manda-Frase ([string]$e.SelectedItem) }
        else { Registra 'Scegli prima una frase.' }
    }.GetNewClosure())

    # La prima gia' scelta: quasi sempre e' quella che si stava cercando, e in
    # ogni caso un elenco in cui non e' scelto niente fa premere un tasto che
    # non puo' funzionare.
    if ($e.Items.Count -gt 0) { $e.SelectedIndex = 0 }

    [void](Apri-Finestrella $fin $y @($tChiudi, $tRiprova, $tFai))
    if ($grigliaCmd.Items.Count -gt 0) {
        $grigliaCmd.Items[$grigliaCmd.Items.Count - 1].Selected = $true
    }
}

$tMandaFrase.Add_Click({ Manda-Frase $campoFrase.Text })
$campoFrase.Dentro.Add_KeyDown({
    if ($_.KeyCode -eq 'Enter') { $_.SuppressKeyPress = $true; Manda-Frase $campoFrase.Text }
})
$tLeggiBuchi.Add_Click({ Leggi-Buchi })

# ---- l'elenco di fabbrica, in una finestra a parte ---------------------------
#
# E' una stringa e non una griglia di proprieta': si legge, si copia, e quando
# si aggiunge un comando a Comandi.java si aggiunge una riga qui - che e'
# l'unico modo perche' i due restino d'accordo senza un generatore.

$ELENCO_COMANDI = @'
  COME SI CHIAMA
    Si preme la pastiglia del microfono e si parla. L'attivazione a voce
    e' spenta: vedi la pagina "La voce".

  CHI E'
    come ti chiami                chi sei, il tuo nome
    come stai                     guarda com'e' la casa davvero
    chi ti ha fatto               dove sei, quanti anni hai
    ciao / buongiorno / buonasera cambia col passare delle ore
    grazie / scusa
    cosa sai fare                 l'elenco, detto a voce

  ORA E DATA
    che ore sono
    che giorno e'

  TIMER E SVEGLIE
    metti un timer di dieci minuti
    timer di un'ora e mezza       le durate si sommano
    timer di due minuti e trenta secondi
    un quarto d'ora / mezz'ora
    annulla il timer
    sveglia alle sette            anche "alle 6 e mezza", "alle 7:30"
    togli la sveglia

  RADIO
    metti la radio
    metti rai radio 1             basta il nome: 22 stazioni
    virgin / rtl / kiss kiss ...
    spegni la radio

  MUSICA (Spotify dentro Assistente Home)
    metti la musica
    metti Levante su spotify      la playlist "This Is Levante"
    musica di Battisti            lo stesso, detto in un altro modo
    cerca <una canzone>           l'unica cosa che vuole la chiave
    metti <nome di una playlist>  quelle che hai gia' in casa
    avanti / skip / next / salta  sa se comanda la radio o Spotify
    indietro / precedente         da cosa sta suonando
    pausa / riprendi
    a caso / casuale
    cosa sta suonando

  LUCI
    accendi la luce               tutte
    accendi la camera             una sola, per nome
    spegni le luci
    accendi la camera di rosso    21 colori: rosso, verde, blu, giallo,
                                  arancione, viola, lilla, rosa, fucsia,
                                  magenta, azzurro, celeste, turchese,
                                  indaco, corallo, ambra, oro, bordeaux,
                                  verde acqua, blu notte
    luce bianca                   il canale bianco, non un RGB
    luce calda / luce fredda      la temperatura del bianco
    comodino al trenta per cento  la luminosita'

    I nomi delle lampade sono quelli scritti nella pagina "Le luci":
    cambiarli li' cambia anche quello che si dice a voce.

  ROUTINE
    buonanotte / cinema / tutte accese
    Il nome e' quello della pagina "Routine". Due comandi non possono
    contendersi la stessa parola: "Film" finirebbe a Netflix invece che
    alle luci, e "Tutte" verrebbe presa da "spegni tutte le luci".

  VOLUME
    alza il volume / piu' forte
    abbassa il volume / piu' piano
    volume al 40                  a un livello preciso

  IL RESTO
    silenzio                      ferma quello che suona
    spegni tutto                  radio, musica, timer e luci
    apri netflix

  QUANDO NON CAPISCE
    Non risponde "non ho capito" e basta: cerca la parola nota piu' vicina
    e propone la frase giusta. "metti la raddio" -> "Volevi dire metti la
    radio?". E scrive la frase nel registro, dove la trova il tasto
    "Cosa non ha capito".
'@

function Mostra-Comandi-Di-Fabbrica {
    $largo = 640
    $alto = 520
    $fin = Nuova-Finestrella 'Cosa Assistente Home capisce gia''' `
        'i comandi che stanno nel codice, in Comandi.java' 'spunta' $cBlu $largo -Elastica

    # DENTRO UN PANNELLO, e non appoggiato sul fondo della finestra.
    #
    # Prima era una casella di testo nuda su fondo nero, larga quanto la
    # finestra: centodieci righe di monospaziato senza margini e senza un bordo
    # che dicesse dove cominciano e dove finiscono. L'ultima riga visibile
    # restava tagliata a meta' dal bordo di sotto - la casella era alta 580 e le
    # righe non ci stanno per un multiplo intero - e sembrava che mancasse del
    # testo.
    #
    # Il pannello da' il margine e il piano piu' chiaro; l'altezza della casella
    # si arrotonda all'altezza di una riga, cosi' quello che si vede si vede
    # sempre intero.
    $pan = Nuovo-Pannello 0 0 $largo $alto
    $fin.corpo.Controls.Add($pan)

    $t = New-Object System.Windows.Forms.TextBox
    $t.Multiline = $true
    $t.ReadOnly = $true
    $t.WordWrap = $false
    $t.ScrollBars = 'Both'
    $t.BackColor = $cPannello
    $t.ForeColor = $cMedio
    $t.BorderStyle = 'None'
    $t.Font = $fFisso
    # Il ritorno a capo intero: vedi Nuovo-Testo in GestioneHome.ps1. Questo
    # file va a CRLF, ma non e' una cosa da sapere per leggere questa riga.
    $t.Text = (($ELENCO_COMANDI -replace "`r`n", "`n") -replace "`n", "`r`n")
    $t.TabStop = $false
    $pan.Controls.Add($t)

    # L'altezza a righe intere: quanto e' alta una riga lo dice il carattere, non
    # un numero scritto a mano. E la barra di scorrimento orizzontale sta DENTRO
    # la casella: se non la si toglie dal conto si mangia l'ultima riga, ed e'
    # esattamente la riga tagliata a meta' che si vedeva in fondo.
    $riga = Riga-Casella $fFisso
    $sotto = [System.Windows.Forms.SystemInformation]::HorizontalScrollBarHeight
    $sistema = {
        param($l, $a)
        $t.Location = New-Object System.Drawing.Point(16, 14)
        # I quattro punti in piu' sono il margine che la casella si tiene sopra
        # il testo: senza toglierli il conto delle righe torna giusto per uno di
        # troppo, e dell'ultima si vedono le lettere senza le code.
        $utile = $a - 28 - $sotto - 2
        $altoCasella = [Math]::Max($riga, [int]($utile / $riga) * $riga) + $sotto
        $t.Size = New-Object System.Drawing.Size(($l - 32), $altoCasella)
    }.GetNewClosure()

    $fin.elastico = {
        param($l, $a)
        $pan.Size = New-Object System.Drawing.Size($l, $a)
        & $sistema $l $a
    }.GetNewClosure()
    & $sistema $largo $alto

    $tChiudi = Tasto-Fondo 'Chiudi' $cBlu 'spunta' 'OK' 'Primario'
    [void](Apri-Finestrella $fin $alto @($tChiudi))
}

# ---- le risposte di fabbrica ------------------------------------------------
#
# QUELLO CHE CASA DICE, E COME SI CAMBIA. Le risposte fisse - "Radio spenta.",
# "In pausa.", i saluti - erano stringhe dentro Comandi.java: per cambiarne una
# bisognava avere il progetto e ricompilare. Adesso ognuna ha un nome, e chi non
# la sopporta piu' la riscrive da qui.
#
# L'ELENCO LO DICE IL TABLET, non il PC. Arriva nella vetrina insieme al resto
# (risposteBase), e cosi' e' sempre quello vero: se un giorno Casa impara a dire
# una cosa in piu', compare qui senza che nessuno aggiorni una copia.
#
# PIU' MODI DI DIRE LA STESSA COSA. Alcune ne hanno gia' due o tre di serie, e
# la ragione e' che una risposta corretta e sempre identica, sentita venti volte
# al giorno, e' la cosa che piu' fa sembrare che dall'altra parte non ci sia
# nessuno. Riscrivendole se ne possono mettere quante si vuole - o una sola, se
# si preferisce sapere sempre cosa aspettarsi.
#
# QUELLE COMPOSTE NON SONO QUI: "Metto Levante.", "Sono le 7 e 20." non sono un
# testo, sono un testo piu' un pezzo che cambia. Per cambiare quelle c'e'
# l'altra strada, che copre tutto - un comando scritto da te che prende la frase
# prima e risponde come vuoi, con i segnaposto.

function Risposte-Tue([string]$nome) {
    if (-not $script:config.risposte) { return $null }
    $p = $script:config.risposte.PSObject.Properties[$nome]
    if (-not $p) { return $null }
    return @($p.Value)
}

function Mostra-Risposte {
    if (@($script:risposteBase).Count -eq 0) {
        Registra 'Il catalogo delle risposte non e'' ancora arrivato: premi "Rileggi dal tablet".'
        Avvisa ("Il catalogo delle risposte non e' ancora arrivato dal tablet." +
                [Environment]::NewLine + [Environment]::NewLine +
                "Sono le frasi che Assistente Home dice di serie, e le racconta il tablet insieme " +
                "al resto della vetrina. Collega il tablet e premi ""Rileggi dal tablet"".") `
               'Ancora non le so' $cAmbra 'campanella'
        return
    }

    $largo = 820
    $fin = Nuova-Finestrella 'Le risposte di fabbrica' `
        'quello che Assistente Home dice quando risponde, e come si riscrive' 'regola' $cBlu $largo -Elastica

    $p = Nuovo-Paragrafo 0 0 $largo (
        'Scegline una e riscrivila: da quel momento vale la tua, e il tablet la usa ' +
        'in tutti i punti in cui diceva quella di serie. Quelle che hai riscritto sono ' +
        'in blu.') $cMedio $fTesto
    $fin.corpo.Controls.Add($p)
    $y = $p.Height + 14

    $altoGriglia = 360
    $g = Nuova-Griglia 0 $y $largo $altoGriglia @(
        @('gruppo', 110), @('nome', 160), @('cosa dice', 520)) @($false, $true, $false)
    $g.Tinta = $cBlu
    $fin.corpo.Controls.Add($g)
    $y += $altoGriglia + 12

    $spiega = Nuovo-Paragrafo 0 $y $largo (
        'Piu'' modi di dire la stessa cosa si separano con una barra - "Radio spenta. / ' +
        'Ho spento la radio. / Fatto, radio spenta." - e Assistente Home ne sceglie una a caso, ' +
        'senza mai ripetere due volte di fila la stessa.') $cTenue $fNota
    $fin.corpo.Controls.Add($spiega)
    $y += $spiega.Height

    # Il catalogo e la tinta come variabili LOCALI: dentro una chiusura fatta
    # qui, $script:risposteBase non esiste - e il sintomo era un elenco vuoto
    # con una riga bianca, perche' @($null) e' un elenco di uno.
    $base = @($script:risposteBase)
    $tintaTua = $cBlu

    $riempi = {
        $g.BeginUpdate()
        $g.Items.Clear()
        foreach ($r in $base) {
            $tue = Risposte-Tue ([string]$r.nome)
            $quali = $(if ($tue) { $tue } else { @($r.serie) })
            $riga = New-Object System.Windows.Forms.ListViewItem([string]$r.gruppo)
            [void]$riga.SubItems.Add([string]$r.nome)
            [void]$riga.SubItems.Add(($quali -join '  /  '))
            if ($tue) { $riga.ForeColor = $tintaTua }
            [void]$g.Items.Add($riga)
        }
        $g.EndUpdate()
    }.GetNewClosure()
    & $riempi

    # Quando la finestra cresce, cresce la griglia: e' l'unica cosa qui dentro
    # che guadagna qualcosa ad essere piu' grande.
    $altoFisso = $y - $altoGriglia
    $fin.elastico = {
        param($l, $a)
        $g.Width = $l
        $g.Height = [Math]::Max(120, $a - $altoFisso)
        $spiega.Width = $l
        $spiega.Top = $g.Bottom + 12
        $p.Width = $l
    }.GetNewClosure()

    $scelta = {
        if ($g.SelectedIndices.Count -eq 0) { return $null }
        return $base[$g.SelectedIndices[0]]
    }.GetNewClosure()

    $tChiudi = Tasto-Fondo 'Chiudi' $cTenue '' 'Cancel' 'Quieto'
    $tRimetti = Tasto-Fondo 'Rimetti quella di serie' $cMedio 'aggiorna'
    $tManda = Tasto-Fondo 'Manda al tablet' $cMedio 'sincronizza'
    $tCambia = Tasto-Fondo 'Riscrivi questa' $cBlu 'regola' '' 'Primario'

    $tCambia.Add_Click({
        $r = & $scelta
        if (-not $r) { Registra 'Scegli prima una risposta.'; return }
        $tue = Risposte-Tue ([string]$r.nome)
        $adesso = $(if ($tue) { $tue -join ' / ' } else { @($r.serie) -join ' / ' })
        $nuovo = Chiedi-Testo ('Che cosa deve dire?' + [Environment]::NewLine + [Environment]::NewLine +
                               'Piu'' modi separati da una barra: Assistente Home ne sceglie uno a caso ogni volta.') `
                              $adesso 'Riscrivi la risposta' `
                              ('Di serie: ' + (@($r.serie) -join '  /  '))
        if ($null -eq $nuovo) { return }
        $varianti = @($nuovo -split '/' | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 })
        if ($varianti.Count -eq 0) { Registra 'Una risposta vuota non si puo'' dire.'; return }
        Metti-Risposta ([string]$r.nome) $varianti
        & $riempi
    }.GetNewClosure())

    $tRimetti.Add_Click({
        $r = & $scelta
        if (-not $r) { Registra 'Scegli prima una risposta.'; return }
        Metti-Risposta ([string]$r.nome) $null
        & $riempi
    }.GetNewClosure())

    $tManda.Add_Click({ Manda-Config-Al-Tablet | Out-Null }.GetNewClosure())

    [void](Apri-Finestrella $fin $y @($tChiudi, $tRimetti, $tManda, $tCambia))
}

<##
 # Scrive - o toglie - una risposta riscritta.
 #
 # Toglierla non vuol dire scriverci dentro quella di serie: vuol dire togliere
 # la voce. Cosi' se un giorno Casa cambia la sua, quella tolta non resta
 # congelata a com'era ieri.
 #>
function Metti-Risposta([string]$nome, $varianti) {
    $tutte = [ordered]@{}
    if ($script:config.risposte) {
        foreach ($p in $script:config.risposte.PSObject.Properties) {
            if ($p.Name -ne $nome) { $tutte[$p.Name] = @($p.Value) }
        }
    }
    if ($varianti) {
        $tutte[$nome] = @($varianti)
        Registra ('Risposta "' + $nome + '": ' + (@($varianti) -join ' / '))
    } else {
        Registra ('Risposta "' + $nome + '": rimessa quella di serie.')
    }
    $script:config.risposte = [PSCustomObject]$tutte
    Config-Cambiata
}
