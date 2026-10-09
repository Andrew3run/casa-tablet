# Pagina "La voce": come suona Casa quando risponde.
#
# QUI DENTRO NON C'E' PIU' LA PAROLA DI ATTIVAZIONE. C'erano due elenchi di
# registrazioni, la generazione delle varianti e i "rinforzi da mandare al
# tablet": tutta la macchina del "Hey Home", che e' spenta da settembre. Una
# pagina che chiede di scaricare campioni per una cosa che non gira e' peggio di
# una pagina in meno - fa credere che serva.
#
# NON E' STATO BUTTATO NIENTE. La rete e il riconoscitore restano nell'app, le
# registrazioni restano in pc\voce\, il motore che genera le varianti resta in
# pc\Audio.cs, e come si riaccende sta in docs/parola-di-attivazione.md. Quello
# che e' sparito e' l'interfaccia: il giorno che la si riaccende si riscrive, ed
# e' meno lavoro che tenerla in piedi finta per mesi.
#
# QUELLO CHE RESTA E' COME SUONA, e adesso e' molto piu' di prima: quale voce,
# con che ritmo e che tono. Tutte e tre si scelgono a orecchio, premendo e
# ascoltando dal tablet - che e' l'unico modo di sceglierle.

$pagVoce = $pagine[$PAG_VOCE]

$pagVoce.Controls.Add((Nuova-Intestazione $PAG_VOCE))

$script:vociTablet = @()

# ---- come si parla a Home ----------------------------------------------------

$boxParola = Nuovo-Pannello 0 44 420 220
$boxParola.Titolo = 'come si parla a Home'
$pagVoce.Controls.Add($boxParola)

$eFrase = Nuova-Etichetta 'si preme, e si parla' 18 38 380 $cTesto $fVoce
$eFrase.Height = 26
$boxParola.Controls.Add($eFrase)

$eRiassunto = Nuova-Etichetta 'stato sconosciuto' 18 66 384 $cTenue $fTesto
$eRiassunto.Height = 32
$boxParola.Controls.Add($eRiassunto)

$boxParola.Controls.Add((Nuovo-Testo 18 100 384 81 @'
L'attivazione a voce e' spenta: il riconoscitore a bordo
prendeva la parola l'83% delle volte sul dataset, ma dal vivo
agganciava troppo di rado. Un assistente da chiamare due o tre
volte e' peggio di uno da premere. Codice, rete e registrazioni
restano dove sono, pronti da riaccendere.
'@))

$tPannello2 = Nuovo-Tasto 'Apri le impostazioni sul tablet' 18 178 384 30 $cMedio
$tPannello2.Icona = 'regola'
$boxParola.Controls.Add($tPannello2)

# ---- il ritmo e il tono ------------------------------------------------------

$boxSuono = Nuovo-Pannello 0 274 420 322
$boxSuono.Titolo = 'come suona'
$boxSuono.Tinta = $cViola
$pagVoce.Controls.Add($boxSuono)

$boxSuono.Controls.Add((Nuova-Etichetta 'Velocita''' 18 44 80 $cTenue $fTesto))
$nRitmo = New-Object System.Windows.Forms.NumericUpDown
$nRitmo.Location = New-Object System.Drawing.Point(104, 40)
$nRitmo.Size = New-Object System.Drawing.Size(76, 26)
$nRitmo.DecimalPlaces = 2
$nRitmo.Increment = 0.05
$nRitmo.Minimum = 0.60
$nRitmo.Maximum = 1.40
$nRitmo.Value = 0.90
$nRitmo.BackColor = $cRilievo
$nRitmo.ForeColor = $cTesto
$nRitmo.BorderStyle = 'FixedSingle'
$boxSuono.Controls.Add($nRitmo)
$tRitmo = Nuovo-Tasto 'Prova' 190 40 96 26 $cMedio
$tRitmo.Icona = 'avvia'
$boxSuono.Controls.Add($tRitmo)
$boxSuono.Controls.Add((Nuova-Etichetta '1,00 = di serie' 294 44 112 $cTenue $fNota))

$boxSuono.Controls.Add((Nuova-Etichetta 'Tono' 18 80 80 $cTenue $fTesto))
$nTono = New-Object System.Windows.Forms.NumericUpDown
$nTono.Location = New-Object System.Drawing.Point(104, 76)
$nTono.Size = New-Object System.Drawing.Size(76, 26)
$nTono.DecimalPlaces = 2
$nTono.Increment = 0.02
$nTono.Minimum = 0.80
$nTono.Maximum = 1.20
$nTono.Value = 0.96
$nTono.BackColor = $cRilievo
$nTono.ForeColor = $cTesto
$nTono.BorderStyle = 'FixedSingle'
$boxSuono.Controls.Add($nTono)
$tTono = Nuovo-Tasto 'Prova' 190 76 96 26 $cMedio
$tTono.Icona = 'avvia'
$boxSuono.Controls.Add($tTono)
$boxSuono.Controls.Add((Nuova-Etichetta 'piu'' basso = meno finto' 294 80 150 $cTenue $fNota))

$boxSuono.Controls.Add((Nuovo-Testo 18 116 384 194 @'
Si scelgono a orecchio, non a numero: il tablet dice la frase
appena riceve il valore, e si sente da dove si sta.

La velocita' di serie e' quella di un annuncio in stazione: va
bene per leggere un orario a chi ha fretta, non per rispondere
a chi sta in cucina a due passi. Sotto 0,85 pero' si sente che
e' rallentata.

Il tono di serie e' squillante come tutte le voci sintetiche;
un filo piu' basso somiglia di piu' a una persona. Sotto 0,90
diventa cupa, e finta in un altro modo.
'@))

# ---- quale voce --------------------------------------------------------------

$boxVoci = Nuovo-Pannello 436 44 502 552
$boxVoci.Titolo = 'quale voce'
$boxVoci.Tinta = $cViola
$pagVoce.Controls.Add($boxVoci)

$grigliaVoci = Nuova-Griglia 18 40 466 222 @(
    @('voce', 220), @('qualita''', 62), @('rete', 48), @('in uso', 60)) @($true, $false, $false, $false)
$grigliaVoci.Tinta = $cViola
$boxVoci.Controls.Add($grigliaVoci)

$tVociLeggi = Nuovo-Tasto 'Chiedi al tablet' 18 272 150 28 $cMedio
$tVociLeggi.Icona = 'aggiorna'
$boxVoci.Controls.Add($tVociLeggi)
$tVoceProva = Tasto-Primario (Nuovo-Tasto 'Fai sentire questa' 176 272 180 28 $cViola) 'avvia'
$boxVoci.Controls.Add($tVoceProva)
$tVoceAuto = Nuovo-Tasto 'Scegli tu' 364 272 120 28 $cTenue
$boxVoci.Controls.Add($tVoceAuto)

$boxVoci.Controls.Add((Nuovo-Testo 18 312 466 224 @'
QUELLO CHE C'E' SU QUESTO TABLET. Il sintetizzatore e' Google TTS 3.11.12, e
le sue voci italiane sono tutte della serie "it-it-x-kda": tre maschili, tre
femminili, piu' una di rete. E' la generazione vecchia, quella prima delle voci
neurali, ed e' il motivo per cui suona come suona. Fra loro cambia il timbro,
non la tecnologia: si provano tutte e si tiene la meno robotica.

SE VUOI CAMBIARE DAVVERO, si aggiorna il sintetizzatore. Le versioni recenti di
Google TTS hanno voci italiane neurali, che sono un'altra cosa:

  1. pagina "Il tablet" -> Play Store: apri
  2. cerca "Sintesi vocale di Google" e aggiorna
  3. Play Store: chiudi
  4. torna qui e premi "Chiedi al tablet": se sono arrivate voci nuove,
     compaiono nell'elenco

Il Play Store dara' la versione piu' recente compatibile con Android 7: puo'
darsi che sia ancora questa, e allora non si va oltre senza cambiare motore.

LA VOCE DI RETE ("-network") di solito e' migliore, ma richiede internet: con
il collegamento giu' Assistente Home resterebbe muta proprio quando qualcosa non va, ed e'
il motivo per cui la scelta automatica preferisce quelle locali.
'@))

# =============================================================== il tablet ===

function Chiedi-Parola {
    if (-not (Trova-Tablet)) { return $false }
    # "am broadcast" aspetta che il ricevitore abbia finito prima di tornare,
    # quindi quando siamo qui la riga nel registro c'e' gia': niente attese a
    # tempo, che sarebbero o troppo corte o sprecate.
    Sh 'am broadcast -a dev.casa.PAROLA --es cosa stato' | Out-Null
    $righe = Adb 'logcat' '-d' '-t' '200' '-s' 'Casa:I'
    $ultima = $null
    # "PC stato" e basta, e non "PC stato accesa=". Con la parola di attivazione
    # spenta il tablet scrive "PC stato parolaspenta=true ...": niente "accesa=",
    # perche' non c'e' piu' niente da accendere.
    foreach ($r in $righe) { if ($r -match 'PC stato\s+\w+=') { $ultima = $r } }
    if (-not $ultima) {
        Registra 'Assistente Home non ha risposto: e'' in funzione? (Riavvia Assistente Home nella pagina del tablet)'
        return $false
    }
    $script:parola = @{}
    foreach ($m in [regex]::Matches($ultima, '(\w+)=([^\s]+)')) {
        $script:parola[$m.Groups[1].Value] = $m.Groups[2].Value
    }
    return $true
}

function Numero([string]$s, [double]$riserva) {
    if (-not $s) { return $riserva }
    # Casa scrive i decimali all'italiana - "3,20" - perche' li scrive per lo
    # schermo di una cucina italiana. Qui vanno riletti come numeri.
    $pulito = $s.Replace(',', '.')
    $v = 0.0
    if ([double]::TryParse($pulito, [Globalization.NumberStyles]::Float,
                           [Globalization.CultureInfo]::InvariantCulture, [ref]$v)) { return $v }
    return $riserva
}

function Aggiorna-Parola {
    if (-not (Chiedi-Parola)) {
        $eRiassunto.Text = 'Nessuna risposta dal tablet.'
        $eRiassunto.ForeColor = $script:cRosso
        return
    }
    $p = $script:parola
    $eRiassunto.ForeColor = $script:cTenue
    $nome = $p['nome']
    if (-not $nome) { $nome = 'Home' }
    $eRiassunto.Text = "Si chiama $nome. Il microfono si apre premendo la pastiglia, e si chiude con annulla."
    Leggi-Voci
}

<##
 # Le voci che il sintetizzatore ha su questo tablet.
 #
 # Non si possono indovinare: dipendono da che cosa e' stato scaricato
 # sull'apparecchio. Si chiedono, e Casa le scrive nel registro una per riga.
 #>
function Leggi-Voci {
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    Sh 'am broadcast -a dev.casa.PAROLA --es cosa voci' | Out-Null
    $righe = Adb 'logcat' '-d' '-t' '120' '-s' 'Casa:I'
    # SOLO L'ULTIMO ELENCO, non tutti quelli che sono nel registro.
    #
    # Il tablet scrive una riga per voce e poi una di riepilogo, e il registro
    # tiene le ultime centoventi righe: chi preme "Chiedi al tablet" due volte
    # trova nel registro due elenchi interi, e sommandoli si vedrebbero le voci
    # doppie - con la riga di riepilogo che dice nove mentre l'elenco ne mostra
    # diciotto. La riga di riepilogo chiude il blocco: quello che c'era prima si
    # butta, e vale l'ultimo elenco completo.
    $trovate = @()
    $blocco = @()
    $riassunto = $null
    foreach ($r in $righe) {
        if ($r -match 'PC voce ([^|]+)\|(\d+)\|(\w+)\|(\w+)\s*$') {
            $blocco += @{ nome = $Matches[1]; qualita = $Matches[2]
                          rete = ($Matches[3] -eq 'true'); inUso = ($Matches[4] -eq 'true') }
        }
        if ($r -match 'PC voci (\d+) ritmo=([\d.]+) tono=([\d.]+) scelta=(\S+)') {
            $riassunto = $Matches
            $trovate = $blocco
            $blocco = @()
        }
    }
    $script:vociTablet = $trovate

    $grigliaVoci.BeginUpdate()
    $grigliaVoci.Items.Clear()
    foreach ($v in $trovate) {
        $riga = New-Object System.Windows.Forms.ListViewItem([string]$v.nome)
        [void]$riga.SubItems.Add([string]$v.qualita)
        [void]$riga.SubItems.Add($(if ($v.rete) { 'si' } else { '' }))
        [void]$riga.SubItems.Add($(if ($v.inUso) { 'questa' } else { '' }))
        if ($v.inUso) { $riga.ForeColor = $script:cViola }
        elseif ($v.rete) { $riga.ForeColor = $script:cTenue }
        [void]$grigliaVoci.Items.Add($riga)
    }
    $grigliaVoci.EndUpdate()

    if ($riassunto) {
        $t = Numero $riassunto[3] 0.96
        if ($t -ge [double]$nTono.Minimum -and $t -le [double]$nTono.Maximum) { $nTono.Value = [decimal]$t }
        $r = Numero $riassunto[2] 0.90
        if ($r -ge [double]$nRitmo.Minimum -and $r -le [double]$nRitmo.Maximum) { $nRitmo.Value = [decimal]$r }
        Registra ("Voci sul tablet: " + $riassunto[1] + ", scelta a mano: " + $riassunto[4])
    } elseif ($trovate.Count -eq 0) {
        Registra 'Assistente Home non ha elencato nessuna voce: e'' in funzione?'
    }
}

# ---- i fili ------------------------------------------------------------------

$tRitmo.Add_Click({
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    # Il punto e non la virgola: dall'altra parte lo legge Float.parseFloat,
    # che non sa niente di come si scrivono i numeri in italiano.
    $v = [string]::Format([Globalization.CultureInfo]::InvariantCulture, '{0:0.00}', $nRitmo.Value)
    Sh "am broadcast -a dev.casa.PAROLA --es cosa ritmo --ef valore $v" | Out-Null
    # ${v} e non $v: dopo la variabile c'e' un due punti, che PowerShell
    # leggerebbe come l'inizio di uno scope.
    Registra "Velocita' a ${v}: il tablet te la fa sentire."
})

$tTono.Add_Click({
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    $v = [string]::Format([Globalization.CultureInfo]::InvariantCulture, '{0:0.00}', $nTono.Value)
    Sh "am broadcast -a dev.casa.PAROLA --es cosa tono --ef valore $v" | Out-Null
    Registra "Tono a ${v}: il tablet te lo fa sentire."
})

$tVociLeggi.Add_Click({ Leggi-Voci })

$tVoceProva.Add_Click({
    if ($grigliaVoci.SelectedIndices.Count -eq 0) { Registra 'Scegli prima una voce dall''elenco.'; return }
    $v = $script:vociTablet[$grigliaVoci.SelectedIndices[0]]
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    Sh ("am broadcast -a dev.casa.PAROLA --es cosa voce --es nome '" + $v.nome + "'") | Out-Null
    Registra ("Voce: " + $v.nome + ". Il tablet si presenta, senti come suona.")
    Leggi-Voci
})

<##
 # Torna alla scelta automatica.
 #
 # Il nome vuoto vuol dire "decidi tu": sul tablet il punteggio riprende a
 # scegliere - maschile, locale, di qualita' - ed e' il modo di tornare indietro
 # senza doversi ricordare quale fosse quella di partenza.
 #>
$tVoceAuto.Add_Click({
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    Sh "am broadcast -a dev.casa.PAROLA --es cosa voce --es nome ''" | Out-Null
    Registra 'Scelta automatica rimessa: decide il tablet.'
    Leggi-Voci
})

$tPannello2.Add_Click({ Azione-Tablet 'pannello' })

# Questa pagina si riempie la prima volta che si apre, non all'avvio: quello che
# chiede al tablet costa secondi, e non li deve pagare chi apre il programma per
# fare altro.
$script:allApertura[$PAG_VOCE] = { if ($script:seriale) { Aggiorna-Parola } }
