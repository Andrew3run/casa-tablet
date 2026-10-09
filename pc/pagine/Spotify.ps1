# Pagina "Spotify": l'accesso, e la chiave che serve solo per cercare.
#
# DUE COSE DIVERSE, E SI CONFONDONO. Sono tutte e due "entrare in Spotify", ma
# non c'entrano niente l'una con l'altra:
#
#   L'ACCESSO e' il tuo account dentro go-librespot, che e' il demone che suona.
#   Si fa una volta sola, dal tablet, e da li' in poi Casa suona senza chiedere
#   piu' niente. Non passa da qui e non puo' passare da qui: e' un ingresso che
#   va approvato dal telefono.
#
#   LA CHIAVE e' un'applicazione registrata a tuo nome su developer.spotify.com,
#   e serve a UNA cosa sola: la ricerca. Playlist, preferiti e riproduzione
#   funzionano senza. Il perche' e' nel protocollo di Spotify - la ricerca vive
#   dentro il canale Mercury della sessione (hm://searchview/...), non sull'API
#   HTTPS, e go-librespot quel canale non lo espone - quindi si passa dall'API
#   pubblica, che una chiave la vuole.
#
# Chi apre questa pagina perche' "la ricerca non va" deve capire in dieci
# secondi che sono due cose, e quale delle due gli manca. Per questo lo stato
# sta in cima, con due pastiglie, prima di qualunque spiegazione.

$pagSpot = $pagine[$PAG_SPOT]

$pagSpot.Controls.Add((Nuova-Intestazione $PAG_SPOT))

# ---- come stanno le cose -----------------------------------------------------

$boxStatoSpot = Nuovo-Pannello 0 44 938 92
$boxStatoSpot.Titolo = 'come sta adesso'
$pagSpot.Controls.Add($boxStatoSpot)

$boxStatoSpot.Controls.Add((Nuova-Etichetta 'Accesso' 18 44 70 $cTenue $fTesto))
$pAccesso = Nuova-Pastiglia '?' 92 44 190 $cTenue $true
$boxStatoSpot.Controls.Add($pAccesso)

$boxStatoSpot.Controls.Add((Nuova-Etichetta 'Ricerca' 310 44 70 $cTenue $fTesto))
$pChiave = Nuova-Pastiglia '?' 384 44 220 $cTenue $true
$boxStatoSpot.Controls.Add($pChiave)

$tStatoSpot = Nuovo-Tasto 'Chiedi al tablet' 780 40 140 28 $cMedio
$tStatoSpot.Icona = 'aggiorna'
$boxStatoSpot.Controls.Add($tStatoSpot)

# ---- la chiave della ricerca -------------------------------------------------

$boxChiaveSpot = Nuovo-Pannello 0 148 560 432
$boxChiaveSpot.Titolo = 'la chiave della ricerca'
$boxChiaveSpot.Tinta = $cSpotify
$pagSpot.Controls.Add($boxChiaveSpot)

$boxChiaveSpot.Controls.Add((Nuovo-Testo 18 40 524 172 @'
Serve solo per "cerca <una canzone>". Tutto il resto - le playlist di casa, i
preferiti, "metti Levante su spotify", i tasti avanti e indietro - funziona
senza, perche' passa da servizi HTTPS veri.

Si registra un'applicazione a tuo nome, e non costa niente:

  1. developer.spotify.com/dashboard -> Create app. Nome e descrizione
     qualsiasi; fra le API scegli "Web API".
  2. Come Redirect URI metti esattamente:
         http://127.0.0.1:8888/callback
     Non verra' contattato mai - il flusso "client credentials" non
     redirige nessuno - ma il modulo lo pretende, e da inizio 2025 vuole il
     loopback numerico con la porta, non "localhost".
  3. Dentro l'app: Settings -> Client ID, e "View client secret".
  4. Copiali qui sotto e manda.

Le due chiavi viaggiano in un broadcast e Assistente Home le scrive nei suoi file
privati: non restano su /sdcard e non servono permessi di memoria.
'@))

$boxChiaveSpot.Controls.Add((Nuova-Etichetta 'Client ID' 18 226 90 $cTenue $fTesto))
$campoId = Nuovo-Campo 110 222 432
$boxChiaveSpot.Controls.Add($campoId)

$boxChiaveSpot.Controls.Add((Nuova-Etichetta 'Client secret' 18 264 90 $cTenue $fTesto))
$campoSegreto = Nuovo-Campo 110 260 432
$campoSegreto.Dentro.UseSystemPasswordChar = $true
$boxChiaveSpot.Controls.Add($campoSegreto)

$eSegreto = Nuova-Etichetta '' 110 292 432 $cTenue $fNota
$eSegreto.Height = 32
$eSegreto.Text = ("Il secret si copia e non si legge: resta coperto perche' questa finestra " +
                  "si apre anche con qualcuno dietro le spalle.")
$boxChiaveSpot.Controls.Add($eSegreto)

$tVediSegreto = Nuovo-Tasto 'Mostra' 18 292 84 26 $cTenue
$boxChiaveSpot.Controls.Add($tVediSegreto)

$tMandaChiave = Tasto-Primario (Nuovo-Tasto 'Manda la chiave al tablet' 18 336 300 34 $cSpotify) 'spunta'
$boxChiaveSpot.Controls.Add($tMandaChiave)

$tProvaCerca = Nuovo-Tasto 'Prova una ricerca sul tablet' 330 336 212 34 $cMedio
$boxChiaveSpot.Controls.Add($tProvaCerca)

$eDoveChiave = Nuova-Etichetta '' 18 382 524 $cTenue $fNota
$eDoveChiave.Height = 36
$eDoveChiave.Text = ("Assistente Home la scrive in spotify.json, dentro i suoi file privati. Da adb quella " +
                     "cartella non si legge, quindi lo stato qui sopra lo dice Assistente Home, non il PC.")
$boxChiaveSpot.Controls.Add($eDoveChiave)

# ---- l'accesso ---------------------------------------------------------------

$boxAccesso = Nuovo-Pannello 576 148 362 432
$boxAccesso.Titolo = 'l''accesso, che si fa dal tablet'
$pagSpot.Controls.Add($boxAccesso)

# Le righe sono corte perche' la colonna e' stretta: 54 caratteri e' quanto ci
# sta a questo corpo dentro 326 pixel. I blocchi di testo non vanno a capo da
# soli (vedi Nuovo-Testo), quindi la larghezza la decide chi scrive.
$boxAccesso.Controls.Add((Nuovo-Testo 18 40 326 262 @'
Spotify dentro Assistente Home e' go-librespot: un demone che
gira come processo figlio e suona da solo. Occupa
12 MB invece dei 290 dell'app ufficiale, ma per
suonare deve sapere chi sei.

Si entra in due modi, e li sceglie Assistente Home da sola:

  DAL CODICE. La prima volta il demone stampa un
  indirizzo: la sezione Spotify del tablet lo
  mostra come codice QR, lo si inquadra col
  telefono e si approva. Da li' in poi le
  credenziali restano sul tablet, e non le chiede
  piu' nessuno.

  DALL'APP. Con Assistente Home accesa e il telefono sulla
  stessa rete, Assistente Home compare fra gli apparecchi di
  Spotify Connect: si sceglie e si suona. Anche
  questo lascia le credenziali sul tablet.

Non si fa da qui, e non e' una mancanza: un
ingresso si approva dal telefono di chi entra. Da
qui si apre la sezione, e ci si alza.
'@))

$tApriSpotify = Nuovo-Tasto 'Apri la sezione Spotify sul tablet' 18 320 326 34 $cMedio
$boxAccesso.Controls.Add($tApriSpotify)

$tDimenticaAccesso = Nuovo-Tasto 'Dimentica l''accesso sul tablet' 18 362 326 30 $cRosso
$boxAccesso.Controls.Add($tDimenticaAccesso)

$eAccesso = Nuova-Etichetta '' 18 396 326 $cTenue $fNota
$eAccesso.Height = 30
$eAccesso.Text = 'Serve per entrare con un altro account: al prossimo avvio Assistente Home richiede il codice.'
$boxAccesso.Controls.Add($eAccesso)

# =============================================================== i fili ======

<##
 # Chiede a Casa come stanno le due cose.
 #
 # La risposta arriva nel registro, come tutte le altre: un broadcast non ha un
 # valore di ritorno, e logcat e' il canale che c'e' gia'. Quando "am broadcast"
 # torna, il ricevitore ha gia' finito e la riga c'e'.
 #>
function Aggiorna-Spotify {
    if (-not (Trova-Tablet)) {
        $pAccesso.Text = 'tablet assente'; $pAccesso.Tinta = $script:cTenue
        $pChiave.Text = 'tablet assente';  $pChiave.Tinta = $script:cTenue
        return
    }
    Sh 'am broadcast -a dev.casa.CHIAVE --es cosa stato' | Out-Null
    $righe = Adb 'logcat' '-d' '-t' '80' '-s' 'Casa:I'
    $ultima = $null
    foreach ($r in $righe) { if ($r -match 'PC spotify (.+)$') { $ultima = $Matches[1] } }
    if (-not $ultima) {
        $pAccesso.Text = 'Assistente Home non risponde'; $pAccesso.Tinta = $script:cRosso
        $pChiave.Text = 'Assistente Home non risponde';  $pChiave.Tinta = $script:cRosso
        Registra 'Assistente Home non ha risposto: e'' in funzione?'
        return
    }
    $v = @{}
    foreach ($m in [regex]::Matches($ultima, '(\w+)=([^\s]+)')) { $v[$m.Groups[1].Value] = $m.Groups[2].Value }

    if ($v['accesso'] -eq 'true') {
        $chi = $v['utente']
        $pAccesso.Text = $(if ($chi -and $chi -ne '-') { "fatto: $chi" } else { 'fatto' })
        $pAccesso.Tinta = $script:cSpotify
    } else {
        $pAccesso.Text = 'mai fatto'
        $pAccesso.Tinta = $script:cAmbra
    }

    if ($v['chiave'] -eq 'true') {
        $pChiave.Text = 'chiave presente (' + $v['id'] + ')'
        $pChiave.Tinta = $script:cSpotify
    } else {
        $pChiave.Text = 'senza chiave: non cerca'
        $pChiave.Tinta = $script:cAmbra
    }
    Registra "Spotify: $ultima"
}

$tStatoSpot.Add_Click({ Aggiorna-Spotify })

$tVediSegreto.Add_Click({
    $coperto = $campoSegreto.Dentro.UseSystemPasswordChar
    $campoSegreto.Dentro.UseSystemPasswordChar = -not $coperto
    $this.Text = $(if ($coperto) { 'Nascondi' } else { 'Mostra' })
})

$tMandaChiave.Add_Click({
    $id = $campoId.Text.Trim()
    $segreto = $campoSegreto.Text.Trim()
    if ($id.Length -eq 0 -or $segreto.Length -eq 0) {
        Registra 'Servono tutti e due: Client ID e Client secret.'
        return
    }
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    # Gli apici SINGOLI attorno ai valori devono sopravvivere fino alla shell
    # del tablet: con le doppie, PowerShell le toglie per strada e "am" spezza
    # la riga prendendo un pezzo per il nome di un pacchetto.
    Sh ("am broadcast -a dev.casa.CHIAVE --es id '" + $id + "' --es segreto '" + $segreto + "'") | Out-Null
    Registra 'Chiave mandata. Assistente Home lo conferma a schermo.'
    # Il segreto non resta nella casella: e' stato mandato, e una finestra
    # aperta tutto il giorno con dentro un secret e' il modo in cui i secret
    # finiscono nelle schermate.
    $campoSegreto.Text = ''
    $campoSegreto.Dentro.UseSystemPasswordChar = $true
    $tVediSegreto.Text = 'Mostra'
    Aggiorna-Spotify
})

$tProvaCerca.Add_Click({
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    Sh "am broadcast -a dev.casa.DI --es frase 'cerca bella ciao'" | Out-Null
    Registra 'Chiesto al tablet di cercare "bella ciao": guarda la sezione Musica.'
})

$tApriSpotify.Add_Click({
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    Sh "am broadcast -a dev.casa.DI --es frase 'metti la musica'" | Out-Null
    Registra 'Aperta la sezione Musica sul tablet.'
})

<##
 # Cancella le credenziali del demone.
 #
 # Il file sta nei dati privati dell'app e da adb non si tocca, quindi la strada
 # e' "pm clear"? No: quello porterebbe via anche le sveglie, le lampade e la
 # configurazione. Si passa da Casa, che sa dove sono i suoi file.
 #>
$tDimenticaAccesso.Add_Click({
    if (-not (Chiedi ("Faccio dimenticare ad Assistente Home l'accesso a Spotify?`n`n" +
                      "Al prossimo avvio della sezione Musica chiedera' di nuovo il codice. " +
                      "Non tocca ne' la chiave della ricerca ne' il resto.") `
                     'Dimentico l''accesso?' 'Dimenticalo' -Pericolo)) { return }
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    Sh 'am broadcast -a dev.casa.CHIAVE --es cosa dimentica' | Out-Null
    Registra 'Chiesto ad Assistente Home di dimenticare l''accesso.'
    Aggiorna-Spotify
})

# Questa pagina si riempie la prima volta che si apre, non all'avvio: quello
# che chiede al tablet costa secondi, e non li deve pagare chi apre il
# programma per fare altro.
$script:allApertura[$PAG_SPOT] = { Aggiorna-Spotify }
