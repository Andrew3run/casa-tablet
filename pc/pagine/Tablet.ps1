# Pagina "Il tablet": come sta, e i tasti che si premono ogni giorno.
#
# Si carica col punto da GestioneHome.ps1 e gira nel suo scope: i colori, i
# costruttori, Adb, Registra e Avvia-Script vengono da li'.

$pagTablet = $pagine[$PAG_TABLET]

$pagTablet.Controls.Add((Nuova-Intestazione $PAG_TABLET))

$boxStato = Nuovo-Pannello 0 44 470 296
$boxStato.Padding = New-Object System.Windows.Forms.Padding(16)
$pagTablet.Controls.Add($boxStato)

$VOCI_STATO = @('collegamento', 'build', 'Android', 'batteria', 'spazio libero',
                'app installata', 'app in funzione', 'device owner', 'chiosco')
$valoriStato = @{}
# La colonna dei nomi stretta e quella dei valori larga: il nome piu' lungo e'
# "app in funzione" e sta in centoventi punti, mentre il valore piu' lungo e'
# l'identificativo della build, quarantacinque caratteri. Con le due colonne
# larghe uguali - com'erano - la build usciva tagliata.
for ($i = 0; $i -lt $VOCI_STATO.Count; $i++) {
    $y = 18 + $i * 29
    $boxStato.Controls.Add((Nuova-Etichetta $VOCI_STATO[$i] 16 $y 126 $cTenue $fTesto))
    $v = Nuova-Etichetta '-' 148 $y 306 $cTesto $fMedio
    $boxStato.Controls.Add($v)
    $valoriStato[$VOCI_STATO[$i]] = $v
}

# Nome, che cosa fa, icona. La seconda voce e' quella piena: installare e' il
# motivo per cui questa pagina si apre, ed e' l'unica cosa qui che dura mezzo
# minuto. Le altre sono tutte dello stesso rango, e averle tutte uguali e'
# giusto: nessuna e' piu' importante delle altre.
$AZIONI_TABLET = @(
    @('Aggiorna lo stato',            'aggiorna',      'aggiorna'),
    @('Compila e installa Assistente Home',      'installa',      'sincronizza'),
    @('Riavvia Assistente Home',                 'riavvia',       'accensione'),
    @('Impostazioni di Assistente Home', 'pannello',      'regola'),
    @('Schermata',                    'schermata',     'oggi'),
    @('Registro di Assistente Home',             'registro',      'cerca'),
    @('Play Store: apri',             'negozioApri',   'app_piena'),
    @('Play Store: chiudi',           'negozioChiudi', 'chiudi')
)
for ($i = 0; $i -lt $AZIONI_TABLET.Count; $i++) {
    $t = Nuovo-Tasto $AZIONI_TABLET[$i][0] 492 (44 + $i * 44) 300 36 $cMedio
    $t.Icona = $AZIONI_TABLET[$i][2]
    if ($AZIONI_TABLET[$i][1] -eq 'installa') { $t.Rango = [Casa.Rango]::Primario; $t.Tinta = $cBlu }
    $t.Tag = $AZIONI_TABLET[$i][1]
    $t.Add_Click({ Azione-Tablet ([string]$this.Tag) }.GetNewClosure())
    $pagTablet.Controls.Add($t)
}

# Un promemoria, non una pagina di aiuto: chi apre questa finestra la prima
# volta non sa che esiste Allestimento, e la sequenza giusta e' quella.
$eGuida = Nuovo-Paragrafo 492 400 300 (
    "Se il tablet e' nuovo - o se e' un tablet diverso da questo - " +
    "non partire da qui: la pagina Allestimento fa gli stessi passi " +
    "in ordine, dice quali mancano e spiega quelli che vanno fatti " +
    "sullo schermo del tablet.") $cTenue $fTesto
$pagTablet.Controls.Add($eGuida)

<##
 # LA DOMANDA E LA RISPOSTA SONO DUE COSE.
 #
 # Chiedere costa un secondo dentro adb; capire quello che ha risposto costa
 # niente. Finche' erano una funzione sola, l'unico modo di riempire questa
 # pagina era tenere ferma la finestra per quel secondo.
 #
 # Adesso la domanda e' una STRINGA - quindi si puo' dare a un filo a parte,
 # vedi Aspetta in GestioneHome.ps1 - e Mostra-Stato prende le righe tornate, da
 # dovunque vengano, e le scrive nelle caselle. L'avvio se ne serve: fa fare la
 # domanda al filo mentre la finestra resta viva, e quando la risposta arriva
 # chiama Mostra-Stato. Questa pagina di fili non sa niente.
 #
 # UNA SOLA ANDATA E RITORNO e non nove: ogni chiamata ad adb costa un decimo di
 # secondo di giro, e nove si sentono come un blocco della finestra.
 #>
$script:CHIEDI_STATO =
    'echo @@BUILD; getprop ro.build.display.id;' +
    'echo @@ANDROID; getprop ro.build.version.release;' +
    'echo @@API; getprop ro.build.version.sdk;' +
    'echo @@BATT; dumpsys battery | grep " level:";' +
    'echo @@DF; df /data | tail -n 1;' +
    'echo @@PKG; pm list packages dev.casa;' +
    'echo @@PID; pidof dev.casa;' +
    'echo @@DPM; dumpsys device_policy | head -8;' +
    'echo @@KIOSK; settings get global casa_chiosco;' +
    'echo @@FINE'

# Le caselle come le lascia un tablet che non c'e'.
function Svuota-Stato([string]$perche) {
    foreach ($k in $VOCI_STATO) { $valoriStato[$k].Text = '-'; $valoriStato[$k].ForeColor = $script:cTenue }
    $valoriStato['collegamento'].Text = $(if ($perche) { $perche } else { 'nessun E960 collegato' })
    $valoriStato['collegamento'].ForeColor = $script:cRosso
}

function Aggiorna-Tablet {
    if (-not (Trova-Tablet)) { Svuota-Stato; return }
    $r = Aspetta 'Chiedo al tablet come sta...' {
        param($adb, $seriale, $comando)
        & $adb -s $seriale shell $comando
    } @($script:adb, $script:seriale, $script:CHIEDI_STATO)
    Mostra-Stato $script:seriale $r
}

<##
 # Quello che il tablet ha stampato, scritto nelle caselle.
 #
 # Non tocca adb: si puo' chiamare con quello che ha portato indietro un filo a
 # parte, ed e' esattamente quello che fa l'avvio.
 #>
function Mostra-Stato([string]$seriale, $r) {
    if (-not $seriale) { Svuota-Stato; return }
    $valoriStato['collegamento'].Text = $seriale
    $valoriStato['collegamento'].ForeColor = $script:cVerde

    $valoriStato['build'].Text = ((Sezione $r 'BUILD') -join '').Trim()
    $valoriStato['Android'].Text = ((Sezione $r 'ANDROID') -join '').Trim() +
                                   '  (API ' + ((Sezione $r 'API') -join '').Trim() + ')'

    $batt = (Sezione $r 'BATT') -join ' '
    if ($batt -match 'level:\s*(\d+)') {
        $q = [int]$Matches[1]
        $valoriStato['batteria'].Text = "$q %"
        # Sotto il venti per cento vale la pena vederlo da lontano: un tablet
        # da muro che si scarica e' un tablet staccato dall'alimentatore.
        if ($q -le 20) { $valoriStato['batteria'].ForeColor = $script:cRosso }
        else { $valoriStato['batteria'].ForeColor = $script:cTesto }
    } else {
        $valoriStato['batteria'].Text = '-'
    }

    # Di "df" serve l'ULTIMA riga: Filesystem, blocchi, usati, DISPONIBILI,
    # percentuale, punto di innesto. Il quarto campo contando da uno.
    #
    # L'ultima riga si prende qui e non con "tail -1" sul tablet, e non e'
    # pignoleria: toybox accetta "tail -1" senza protestare e poi stampa
    # TUTTO, intestazione compresa. Il quarto campo diventava la parola
    # "Available" - cioe' il titolo della colonna - e a schermo si leggeva
    # "spazio libero: Available", che sembra una parola strana e invece era
    # il conto sbagliato. Sul tablet adesso c'e' "tail -n 1", che li'
    # funziona; questa riga vale lo stesso, per il giorno in cui cambia
    # qualcos'altro.
    $righeDf = @((Sezione $r 'DF') | Where-Object { $_.Trim().Length -gt 0 })
    if ($righeDf.Count -gt 0) {
        $df = ($righeDf[$righeDf.Count - 1]).Trim() -split '\s+'
        $kb = [int64]0
        if ($df.Count -ge 4 -and [int64]::TryParse($df[3], [ref]$kb)) {
            $valoriStato['spazio libero'].Text = '{0:N1} GB liberi' -f ($kb / 1MB)
        } else {
            $valoriStato['spazio libero'].Text = '-'
        }
    }

    $ce = ((Sezione $r 'PKG') -join ' ') -match 'package:dev\.casa'
    $valoriStato['app installata'].Text = $(if ($ce) { 'si' } else { 'no' })
    $valoriStato['app installata'].ForeColor = $(if ($ce) { $script:cVerde } else { $script:cRosso })

    # Non $pid: in PowerShell e' il numero di QUESTO processo, ed e' di sola
    # lettura - assegnarlo non fallisce con un errore visibile, riempie la
    # console di eccezioni che in una finestra senza console non legge nessuno.
    $processo = ((Sezione $r 'PID') -join ' ').Trim()
    $viva = $processo -match '^\d+$'
    $valoriStato['app in funzione'].Text = $(if ($viva) { "si  (pid $processo)" } else { 'no' })
    $valoriStato['app in funzione'].ForeColor = $(if ($viva) { $script:cVerde } else { $script:cAmbra })

    # Su API 24 "dpm list-owners" non esiste ancora: il device owner si legge
    # dal dump della politica, dove compare come "package=" sotto la voce
    # "Device Owner:".
    $dpm = (Sezione $r 'DPM') -join "`n"
    $owner = ($dpm -match 'Device Owner:') -and ($dpm -match 'package=dev\.casa')
    $valoriStato['device owner'].Text = $(if ($owner) { 'si' } else { 'no' })
    $valoriStato['device owner'].ForeColor = $(if ($owner) { $script:cVerde } else { $script:cAmbra })

    # "settings get" risponde "null" quando la voce non c'e' mai stata
    # scritta, non una stringa vuota.
    $chiosco = ((Sezione $r 'KIOSK') -join '').Trim()
    $valoriStato['chiosco'].Text = $(if ($chiosco -eq '1') { 'acceso' } else { 'spento' })
}

function Azione-Tablet([string]$che) {
    switch ($che) {
        'aggiorna'  { Aggiorna-Tablet; Registra 'Stato riletto.' }
        'installa'  {
            Avvia-Script (Join-Path $radice 'tablet\build.ps1') @('-Install') 'compilo e installo'
        }
        'riavvia'   {
            if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
            Sh 'am force-stop dev.casa' | Out-Null
            Sh 'monkey -p dev.casa -c android.intent.category.HOME 1' | Out-Null
            Registra 'Assistente Home riavviata.'
        }
        'pannello'  {
            if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
            Sh 'am broadcast -a dev.casa.PAROLA --es cosa pannello' | Out-Null
            Registra 'Pannello aperto sul tablet: le registrazioni si fanno di la''.'
        }
        'schermata' { Prendi-Schermata }
        'registro'  {
            if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
            # Un tag solo, e non sei: su questo ROM "log.tag = E" spegne
            # tutti i tag fuori lista, e "Casa" e' l'unico aperto. Il pezzo
            # sta nel messaggio - vedi MainActivity.TAG.
            $r = Adb 'logcat' '-d' '-t' '120' '-s' 'Casa:I'
            Registra ($r -join [Environment]::NewLine)
        }
        'negozioApri'  { Avvia-Script (Join-Path $radice 'tools\negozio.ps1') @('-Apri') 'apro il Play Store' }
        'negozioChiudi'{ Avvia-Script (Join-Path $radice 'tools\negozio.ps1') @('-Chiudi') 'chiudo il Play Store' }
    }
}

function Prendi-Schermata {
    if (-not (Trova-Tablet)) { Registra 'Tablet assente.'; return }
    # In due tempi, e non con "exec-out screencap -p > file": da PowerShell
    # quella strada antepone un BOM al PNG e il file esce corrotto.
    $suTablet = '/sdcard/gestione-home.png'
    Sh "screencap -p $suTablet" | Out-Null
    $cartella = Join-Path $pc 'schermate'
    if (-not (Test-Path $cartella)) { New-Item -ItemType Directory -Path $cartella -Force | Out-Null }
    $qui = Join-Path $cartella ('casa-{0:yyyyMMdd-HHmmss}.png' -f (Get-Date))
    Adb 'pull' $suTablet $qui | Out-Null
    Sh "rm -f $suTablet" | Out-Null
    if (Test-Path $qui) {
        Registra "Schermata in $qui"
        Invoke-Item $qui
    } else {
        Registra 'Schermata non riuscita.'
    }
}
