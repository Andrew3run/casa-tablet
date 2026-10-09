# Pagina "Le notizie": che cosa legge Casa, oltre alle principali.
#
# LE PRINCIPALI CI SONO SEMPRE. Quelle d'Italia girano nella Home, alternate al
# meteo, e sul riposo, alternate all'agenda: non si scelgono e non si tolgono.
# Qui si decide il resto - le notizie DI QUI, e lo SPORT con la sua squadra -
# che sul tablet sono le linguette della pagina Notizie.
#
# LE STESSE SCELTE SI FANNO DAL TABLET, dall'ingranaggio della pagina. Tutte e
# due le strade scrivono nella configurazione, nello stesso posto, e questa
# pagina parte sempre da quello che il tablet ha adesso (la vetrina): una
# squadra scelta di la' non torna indietro al primo "manda".
#
# GLI SPORT LI DICE IL TABLET (sportBase nella vetrina), come le risposte di
# fabbrica e i loghi delle stazioni: una tendina scritta qui inviterebbe a
# scegliere uno sport che di la' non c'e'.

$pagNotizie = $pagine[$PAG_NOTIZIE]

$pagNotizie.Controls.Add((Nuova-Intestazione $PAG_NOTIZIE))

# Mentre la pagina si riempie da sola i campi cambiano, e ogni cambio
# salverebbe la configurazione: questa dice "non sono io".
$script:riempiendoNotizie = $false

# ---- di qui --------------------------------------------------------------------

$boxQui = Nuovo-Pannello 0 44 460 190
$boxQui.Titolo = 'LE NOTIZIE DI QUI'
$boxQui.Tinta = $cNotizie
$pagNotizie.Controls.Add($boxQui)

$boxQui.Controls.Add((Nuova-Etichetta 'Localita''' 18 45 100 $cTenue $fTesto))
$campoLuogo = Nuovo-Campo 124 40 318
$boxQui.Controls.Add($campoLuogo)
$eLuogoNota = Nuova-Etichetta 'Vuota: la citta'' del meteo.' 124 72 318 $cTenue $fNota
$boxQui.Controls.Add($eLuogoNota)

$boxQui.Controls.Add((Nuovo-Testo 18 104 424 72 @'
Le notizie dell'ultima settimana che parlano di questo posto:
una ricerca su Google News con il nome fra virgolette, quindi
funziona anche per un paese piccolo - se qualcuno ne scrive.
Vuota, segue la citta' del meteo, e cambia con lei.
'@))

# ---- lo sport ------------------------------------------------------------------

$boxSport = Nuovo-Pannello 0 244 460 238
$boxSport.Titolo = 'LO SPORT'
$boxSport.Tinta = $cNotizie
$pagNotizie.Controls.Add($boxSport)

$boxSport.Controls.Add((Nuova-Etichetta 'Sport' 18 45 100 $cTenue $fTesto))
$tendinaSport = Nuova-Tendina 124 42 318
$boxSport.Controls.Add($tendinaSport)

$boxSport.Controls.Add((Nuova-Etichetta 'Squadra' 18 83 100 $cTenue $fTesto))
$campoSquadra = Nuovo-Campo 124 80 318
$boxSport.Controls.Add($campoSquadra)
$boxSport.Controls.Add((Nuova-Etichetta 'di calcio; vuota: nessuna' 124 112 318 $cTenue $fNota))

$boxSport.Controls.Add((Nuovo-Testo 18 140 424 86 @'
Con lo sport acceso, sul tablet compaiono due linguette in piu':
lo sport scelto, con le notizie degli ultimi due giorni, e la
squadra, con quelle degli ultimi tre. La squadra si cerca col
nome insieme alla parola "calcio": "Napoli" da solo porterebbe
le notizie della citta'.
'@))

$tNotizieLeggi = Nuovo-Tasto 'Rileggi dal tablet' 0 494 222 30 $cMedio
$tNotizieLeggi.Icona = 'aggiorna'
$pagNotizie.Controls.Add($tNotizieLeggi)
$tNotizieManda = Tasto-Primario (Nuovo-Tasto 'Manda al tablet' 238 494 222 30 $cNotizie) 'spunta'
$pagNotizie.Controls.Add($tNotizieManda)

# ---- cosa arriva ---------------------------------------------------------------

$boxArriva = Nuovo-Pannello 476 44 462 480
$boxArriva.Titolo = 'COSA ARRIVA SUL TABLET'
$boxArriva.Tinta = $cNotizie
$pagNotizie.Controls.Add($boxArriva)

$grigliaNotizie = Nuova-Griglia 16 40 430 188 @(
    @('linguetta', 104), @('titoli', 48), @('quando', 86), @('il primo', 188)) @($false, $false, $false, $false)
$grigliaNotizie.Tinta = $cNotizie
$boxArriva.Controls.Add($grigliaNotizie)

$tNotizieChiedi = Nuovo-Tasto 'Fai rileggere adesso' 16 238 206 28 $cMedio
$tNotizieChiedi.Icona = 'aggiorna'
$boxArriva.Controls.Add($tNotizieChiedi)
$tNotizieApri = Nuovo-Tasto 'Apri la pagina sul tablet' 230 238 216 28 $cMedio
$tNotizieApri.Icona = 'avanti'
$boxArriva.Controls.Add($tNotizieApri)

$boxArriva.Controls.Add((Nuovo-Testo 16 280 430 184 @'
DA DOVE ARRIVANO. Da Google News, in RSS: niente chiave e
niente account, come Open-Meteo per il meteo. Le principali si
rinfrescano ogni venti minuti mentre Assistente Home e' sullo schermo; le
altre linguette quando si apre la pagina.

"Fai rileggere adesso" le chiede tutte subito e dice, per ogni
linguetta, quanti titoli ha trovato e il primo. Se una ricerca
non trova niente - un paese troppo piccolo, una squadra scritta
male - si vede qui, prima di scoprirlo sul tablet.

Toccando un titolo sul tablet si apre l'articolo in Chrome;
il tasto indietro riporta ad Assistente Home.
'@))

# =============================================================== i dati =======

<##
 # Le scelte di adesso, come stanno nella configurazione.
 #
 # Se la configurazione non le ha - il PC non ha ancora parlato col tablet -
 # sono quelle di fabbrica: le principali e quelle del posto del meteo, niente
 # sport. E' la stessa regola del tablet (Notizie.diFabbrica).
 #>
function Scelte-Notizie {
    if ($script:config.Contains('notizie') -and $script:config['notizie']) {
        return $script:config['notizie']
    }
    return [ordered]@{ localita = ''; sport = $false; disciplina = 'calcio'; squadra = '' }
}

function Ridisegna-Notizie {
    $script:riempiendoNotizie = $true
    try {
        $n = Scelte-Notizie
        $base = @($script:sportBase)
        $tendinaSport.Items.Clear()
        [void]$tendinaSport.Items.Add('spento')
        foreach ($s in $base) { [void]$tendinaSport.Items.Add([string]$s.nome) }
        $quale = 0
        if ($n.sport) {
            for ($k = 0; $k -lt $base.Count; $k++) {
                if ([string]$base[$k].chiave -eq [string]$n.disciplina) { $quale = $k + 1 }
            }
        }
        $tendinaSport.SelectedIndex = $quale
        $campoLuogo.Text = [string]$n.localita
        $campoSquadra.Text = [string]$n.squadra
        if ($script:meteoCitta) {
            $eLuogoNota.Text = "Vuota: " + $script:meteoCitta + ", la citta' del meteo."
        }
    } finally {
        $script:riempiendoNotizie = $false
    }
}

$script:ridisegna += { Ridisegna-Notizie }

<##
 # Mette le scelte della pagina nella configurazione.
 #
 # SOLO SUL DISCO, e non Config-Cambiata: quella ridisegna tutte le pagine, e
 # ridisegnare questa mentre si scrive in un campo rimetterebbe il testo nel
 # campo e il cursore all'inizio a ogni lettera.
 #
 # Con gli sport ancora sconosciuti - la vetrina non e' arrivata - la tendina
 # ha solo "spento": in quel caso lo sport di prima resta com'era, invece di
 # spegnerlo senza che nessuno l'abbia chiesto.
 #>
function Salva-Notizie {
    if ($script:riempiendoNotizie) { return }
    $prima = Scelte-Notizie
    $base = @($script:sportBase)
    $sport = [bool]$prima.sport
    $disciplina = [string]$prima.disciplina
    if ($base.Count -gt 0) {
        $i = $tendinaSport.SelectedIndex
        $sport = ($i -gt 0)
        if ($i -gt 0 -and $i -le $base.Count) { $disciplina = [string]$base[$i - 1].chiave }
    }
    $script:config['notizie'] = [ordered]@{
        localita   = $campoLuogo.Text.Trim()
        sport      = $sport
        disciplina = $disciplina
        squadra    = $campoSquadra.Text.Trim()
    }
    Salva-Config-Su-Disco
}

<##
 # Chiede al tablet che cosa ha trovato, una riga per linguetta.
 #
 # Con -Rinfresca prima gli fa rileggere tutto, e aspetta: le richieste
 # partono sul tablet e tornano in un paio di secondi, e chiedere lo stato
 # subito direbbe "sto chiedendo" su ogni riga.
 #
 # Il registro si legge in UTF-8. I titoli hanno gli accenti, e adb li scrive
 # in UTF-8: letti con la codifica della console di Windows, "citta'" arriva
 # come due caratteri storti.
 #>
function Chiedi-Notizie([switch]$Rinfresca) {
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    $cosa = $(if ($Rinfresca) { 'Faccio rileggere le notizie al tablet...' } else { 'Chiedo al tablet le notizie...' })
    $esito = @(Aspetta $cosa {
        param($adb, $seriale, $rinfresca)
        if ($rinfresca) {
            & $adb -s $seriale shell 'am broadcast -a dev.casa.NOTIZIE --es cosa aggiorna' | Out-Null
            Start-Sleep -Seconds 7
        }
        & $adb -s $seriale shell 'am broadcast -a dev.casa.NOTIZIE --es cosa stato' | Out-Null
        $prima = $null
        try { $prima = [Console]::OutputEncoding; [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
        try {
            $righe = & $adb -s $seriale logcat -d -t 80 -s 'Casa:I'
        } finally {
            if ($prima) { try { [Console]::OutputEncoding = $prima } catch { } }
        }
        return @{ righe = $righe }
    } @($script:adb, $script:seriale, [bool]$Rinfresca))[-1]

    # Solo l'ultimo blocco: chi chiede due volte trova due blocchi nel
    # registro, e la riga "scelte" e' quella che ne apre uno.
    $blocco = @()
    $visto = $false
    foreach ($r in @($esito.righe)) {
        if ($r -match 'PC notizie scelte\|') { $blocco = @(); $visto = $true; continue }
        if ($r -match 'PC notizie filone\|([^|]*)\|(\d+)\|([^|]*)\|(.*)$') {
            $blocco += ,@($Matches[1], $Matches[2], $Matches[3], $Matches[4].Trim())
        }
    }
    if (-not $visto) {
        Registra 'Assistente Home non ha risposto sulle notizie: e'' in funzione, ed e'' la versione nuova?'
        return
    }

    $grigliaNotizie.BeginUpdate()
    $grigliaNotizie.Items.Clear()
    $riassunto = @()
    foreach ($b in $blocco) {
        $riga = New-Object System.Windows.Forms.ListViewItem([string]$b[0])
        [void]$riga.SubItems.Add([string]$b[1])
        [void]$riga.SubItems.Add([string]$b[2])
        [void]$riga.SubItems.Add([string]$b[3])
        if ([int]$b[1] -eq 0) { $riga.ForeColor = $script:cAmbra }
        [void]$grigliaNotizie.Items.Add($riga)
        $riassunto += ($b[0] + ' ' + $b[1])
    }
    $grigliaNotizie.EndUpdate()
    Registra ('Notizie sul tablet: ' + ($riassunto -join ', ') + '.')
}

# ================================================================ i tasti =====

$campoLuogo.Dentro.Add_TextChanged({ Salva-Notizie })
$campoSquadra.Dentro.Add_TextChanged({ Salva-Notizie })
$tendinaSport.Add_SelectedIndexChanged({ Salva-Notizie })

$tNotizieLeggi.Add_Click({ Leggi-Config-Dal-Tablet | Out-Null })

$tNotizieManda.Add_Click({
    Salva-Notizie
    if (Manda-Config-Al-Tablet) { Chiedi-Notizie -Rinfresca }
})

$tNotizieChiedi.Add_Click({ Chiedi-Notizie -Rinfresca })

$tNotizieApri.Add_Click({
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    Sh 'am broadcast -a dev.casa.NOTIZIE --es cosa apri' | Out-Null
    Registra 'Pagina delle notizie aperta sul tablet.'
})

# Alla prima apertura si chiede solo com'e' adesso, senza far rileggere niente:
# e' la domanda che costa un broadcast, non sette secondi.
$script:allApertura[$PAG_NOTIZIE] = { if ($script:seriale) { Chiedi-Notizie } }
