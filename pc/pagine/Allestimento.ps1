# Pagina "Allestimento": preparare un tablet da zero, guidati.
#
# PERCHE' ESISTE. Tutto quello che questo progetto sa fare su un tablet e'
# scritto in una quindicina di documenti e in cinque script di tools\, e
# funziona - se si sa che ci sono, in che ordine vanno e che cosa fanno. Chi
# arriva con un tablet in mano non lo sa, e la prima meta' dei passi non si puo'
# nemmeno automatizzare: il debug USB si accende toccando sette volte il numero
# di build, e non c'e' nessun comando che lo faccia da fuori - perche' se ci
# fosse, sarebbe un buco di sicurezza e non una comodita'.
#
# Quindi qui non c'e' un pulsante "fai tutto". C'e' un elenco di passi, ognuno
# con: come si controlla se e' fatto, che cosa cambia se lo si fa, e - quando si
# puo' - il tasto che lo fa. Quando non si puo', c'e' scritto cosa toccare sullo
# schermo del tablet.
#
# PERCHE' QUI SI PUO' SCEGLIERE UN ALTRO DISPOSITIVO. La regola del progetto e'
# che ogni script passi da tools\dispositivo.ps1 e rifiuti tutto quello che non
# e' l'E960: senza, un "installa" lanciato dalla cartella sbagliata finisce
# sull'SM-T210 dell'altro progetto, in silenzio. Quella regola nasce per gli
# script che si lanciano al buio.
#
# Questa pagina e' il caso opposto: serve proprio a preparare un tablet che non
# e' ancora niente, e su cui getprop non dice ancora "E960" - o che e' un altro
# tablet del tutto. Allora la regola si rispetta in un altro modo: il
# dispositivo si sceglie a mano da un elenco, sta scritto in alto per tutto il
# tempo, e quando non e' l'E960 la riga diventa ambrata e lo dice. Il silenzio
# era il problema, non la scelta.

$pagAll = $pagine[$PAG_ALLEST]

$script:allSeriale = $null
$script:allInfo = @{}

$pagAll.Controls.Add((Nuova-Intestazione $PAG_ALLEST))

# ---- chi stiamo preparando ---------------------------------------------------

$boxChi = Nuovo-Pannello 0 44 938 92
$pagAll.Controls.Add($boxChi)

$boxChi.Titolo = 'DISPOSITIVO DA PREPARARE'

$tendinaDisp = Nuova-Tendina 16 34 520
$boxChi.Controls.Add($tendinaDisp)

$tCerca = Nuovo-Tasto 'Cerca i dispositivi' 548 33 180 26 $cMedio
$boxChi.Controls.Add($tCerca)

$tControlla = Tasto-Primario (Nuovo-Tasto 'Controlla tutto' 736 33 180 26 $cBlu) 'aggiorna'
$boxChi.Controls.Add($tControlla)

$eChi = Nuova-Etichetta '' 16 64 900 $cTenue $fTesto
$boxChi.Controls.Add($eChi)

# ---- i passi -----------------------------------------------------------------

$grigliaPassi = Nuova-Griglia 0 148 430 400 @(@('n.', 40), @('stato', 66), @('passo', 320))
$pagAll.Controls.Add($grigliaPassi)

$boxPasso = Nuovo-Pannello 446 148 492 400
$pagAll.Controls.Add($boxPasso)

$ePassoTitolo = Nuova-Etichetta '' 18 14 456 $cTesto $fVoce
$ePassoTitolo.Height = 46
$boxPasso.Controls.Add($ePassoTitolo)

$ePassoTesto = Nuovo-Testo 18 64 456 268 ''
$boxPasso.Controls.Add($ePassoTesto)

$tFaiPasso = Nuovo-Tasto 'Fai questo passo' 18 346 300 34 $cBlu
$tFaiPasso.Rango = [Casa.Rango]::Primario
$boxPasso.Controls.Add($tFaiPasso)
$tRicontrolla = Nuovo-Tasto 'Ricontrolla' 326 346 148 34 $cMedio
$tRicontrolla.Icona = 'aggiorna'
$boxPasso.Controls.Add($tRicontrolla)

$eRiassuntoPassi = Nuova-Etichetta '' 0 558 938 $cTenue $fTesto
$eRiassuntoPassi.Height = 40
$pagAll.Controls.Add($eRiassuntoPassi)

# =============================================================== i passi ======
#
# Ogni passo dice quattro cose: come si chiama, perche' esiste, come si
# controlla, e come si fa. "Come si fa" puo' essere null: allora e' una cosa che
# succede sullo schermo del tablet, e quello che si puo' offrire e' scriverlo
# bene.
#
# Il controllo riceve $i, cioe' tutto quello che si e' letto dal dispositivo in
# una volta sola, e risponde 'si', 'no' oppure '?' - che vuol dire "non lo so",
# ed e' diverso da "no": su un tablet che non e' un MediaTek, DuraSpeed non
# manca perche' l'abbiamo tolto, manca perche' non c'e' mai stato.

$script:PASSI = @(

  [ordered]@{
    chiave = 'cavo'
    titolo = 'Il cavo, e il dispositivo che si vede'
    spiega = @'
Il tablet deve comparire nell'elenco qui sopra. Se non c'e':

1. USA IL CAVO GIUSTO. Molti cavi da ricarica hanno solo i due fili
   dell'alimentazione: caricano benissimo e non portano nessun dato. E' la
   causa piu' comune, e non da' nessun segnale - il tablet si carica, e per
   il PC non esiste.

2. La porta USB del PC: provane un'altra, e direttamente sul computer, non
   su un hub.

3. Sul tablet, quando lo colleghi, tendina in alto -> "Ricarica tramite USB"
   -> scegli "Trasferimento file" (MTP). Su alcuni ROM il debug non parte in
   modalita' "solo ricarica".

4. Se lo stato qui sopra dice "offline": stacca e riattacca il cavo, e se
   non basta riavvia il tablet.

Manca il driver? Su Windows 10 e 11 quasi mai: il tablet si presenta come
apparecchio standard. Se in Gestione dispositivi compare con il punto
esclamativo, serve il driver USB del produttore.
'@
    controlla = { param($i) if ($i.stato -eq 'device') { 'si' } else { 'no' } }
    fai = $null
  }

  [ordered]@{
    chiave = 'debug'
    titolo = 'Opzioni sviluppatore e debug USB'
    spiega = @'
Questo non si puo' fare dal PC, e non e' una mancanza: se un comando potesse
accendere il debug USB da fuori, chiunque avesse un cavo potrebbe entrare in
un telefono. Va toccato sullo schermo.

  1. Impostazioni -> Informazioni sul tablet
  2. Tocca SETTE VOLTE la voce "Numero build" (su alcuni ROM sta sotto
     "Informazioni sul software"). Dopo il terzo tocco compare un conto alla
     rovescia: "Mancano 4 passaggi..."
  3. Torna indietro: e' comparso "Opzioni sviluppatore" (a volte dentro
     "Sistema" o "Altre impostazioni")
  4. Entra e accendi DEBUG USB

Su questo E960, che dichiara Android 9 ma e' un 7, le Opzioni sviluppatore
stanno in Impostazioni -> Sistema -> Informazioni sul tablet.

Se Assistente Home e' gia' installata e device owner, le Impostazioni sono NASCOSTE: si
riaprono da qui, col tasto "Fai questo passo".
'@
    controlla = { param($i) if ($i.stato -eq 'device') { 'si' }
                            elseif ($i.stato -eq 'unauthorized') { 'si' } else { 'no' } }
    fai = { Allestimento-ApriImpostazioni }
  }

  [ordered]@{
    chiave = 'autorizza'
    titolo = 'Autorizzare questo PC'
    spiega = @'
La prima volta che colleghi il tablet a un PC nuovo, il tablet chiede il
permesso con una finestra: "Consentire il debug USB?", con l'impronta della
chiave RSA del computer.

Va confermata SUL TABLET, e conviene spuntare "Consenti sempre da questo
computer": senza, la domanda torna a ogni riavvio del tablet.

Se lo stato dice "unauthorized" e la finestra non compare:

  - sblocca lo schermo del tablet (la richiesta non appare a schermo bloccato)
  - stacca e riattacca il cavo
  - se ancora niente: Opzioni sviluppatore -> "Revoca autorizzazioni debug
    USB", poi riattacca

Se il tablet e' gia' in chiosco e non si riesce a toccare niente, spegni il
chiosco: sta nella pagina "Il tablet".
'@
    controlla = { param($i) if ($i.stato -eq 'unauthorized') { 'no' }
                            elseif ($i.stato -eq 'device') { 'si' } else { '?' } }
    fai = $null
  }

  [ordered]@{
    chiave = 'android'
    titolo = 'Android abbastanza nuovo (API 24)'
    spiega = @'
Assistente Home e' compilata per API 24, cioe' Android 7.0. Su un Android piu' vecchio
non si installa affatto: il sistema rifiuta l'APK e dice "INSTALL_FAILED_
OLDER_SDK", che almeno e' chiaro.

E' anche il confine fra i due progetti di questa casa: il Samsung SM-T210 e'
un Android 4.4 (API 19) e ha un'app tutta sua, TabDeck, nella cartella
accanto. Se quello che hai collegato e' un API 19, non e' questa la finestra
giusta.

Attenzione: alcuni ROM cinesi MENTONO sulla versione. Questo E960 dichiara
Android 9.0 e in realta' e' un 7.0 - il numero che conta e' l'API, che qui
sotto e' quella vera perche' viene da ro.build.version.sdk.
'@
    controlla = { param($i)
        if (-not $i.api) { return '?' }
        if ([int]$i.api -ge 24) { 'si' } else { 'no' } }
    fai = $null
  }

  [ordered]@{
    chiave = 'sdk'
    titolo = 'Sul PC: la platform android-24'
    spiega = @'
Per compilare Assistente Home servono, sul PC: un JDK, l'SDK Android con i build-tools,
e la platform android-24 (il file android.jar contro cui si compila).

Compilare contro il 24 e non contro l'ultima non e' pigrizia: e' quello che
impedisce di chiamare per sbaglio un'API che su questo tablet non esiste. Un
errore in compilazione invece di un crash in cucina.

Se manca, si installa cosi' (una volta sola, dal prompt):

  %LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat "platforms;android-24"

Il resto della catena - aapt2, javac, d8, zipalign, apksigner - lo trova da
solo tablet\build.ps1.
'@
    controlla = { param($i) if ($i.platform24) { 'si' } else { 'no' } }
    fai = $null
  }

  [ordered]@{
    chiave = 'sveglio'
    titolo = 'Lo schermo resta acceso alla corrente'
    spiega = @'
Assistente Home e' una schermata da guardare, non un'app da aprire: un tablet appeso al
muro che si spegne dopo trenta secondi non e' un assistente di casa, e'
uno specchio nero.

Assistente Home tiene lo schermo acceso da sola quando e' in primo piano (FLAG_KEEP_
SCREEN_ON), ma questo vale solo mentre Assistente Home e' davanti. Questa impostazione
di sistema copre anche il resto - l'avvio, un'altra app aperta - e vale solo
quando il tablet e' attaccato alla corrente, che e' come sta sempre.

  settings put global stay_on_while_plugged_in 7

Il 7 sono i tre modi di alimentazione insieme (AC, USB, wireless): messo
cosi' vale comunque sia attaccato.
'@
    controlla = { param($i)
        if ($null -eq $i.stayOn -or $i.stayOn -eq 'null') { return '?' }
        if ([int]$i.stayOn -gt 0) { 'si' } else { 'no' } }
    fai = { Allestimento-Comando 'settings put global stay_on_while_plugged_in 7' 'schermo sempre acceso alla corrente' }
  }

  [ordered]@{
    chiave = 'installa'
    titolo = 'Compilare e installare Assistente Home'
    spiega = @'
Compila l'APK e lo installa SUL DISPOSITIVO SCELTO QUI SOPRA.

E' l'unico punto del programma che installa su un dispositivo che potrebbe non
essere l'E960, ed e' voluto: e' proprio quello che si vuole quando si prepara
un tablet nuovo. Il nome sta scritto in alto per tutto il tempo.

Due passi in uno: prima tablet\build.ps1 (senza toccare nessun dispositivo),
poi "adb -s <questo> install -r". Sono trenta secondi circa, e le righe della
compilazione compaiono nel registro qui sotto mentre succedono.

Se l'installazione fallisce con INSTALL_FAILED_UPDATE_INCOMPATIBLE, sul
tablet c'e' gia' un dev.casa firmato con un'altra chiave: va disinstallato
prima (adb uninstall dev.casa). Se dice INSTALL_FAILED_VERIFICATION_FAILURE,
e' Play Protect: vedi il passo "Il verificatore del Play Store".
'@
    controlla = { param($i) if ($i.casa) { 'si' } else { 'no' } }
    fai = { Allestimento-Installa }
  }

  [ordered]@{
    chiave = 'home'
    titolo = 'Assistente Home e'' la schermata Home'
    spiega = @'
Assistente Home non e' un'app che si apre: e' quello che il tablet mostra quando e'
acceso. Perche' lo diventi, Android deve saperlo.

Premendo "Fai questo passo" si apre sul tablet la scelta della Home: tocca
"Assistente Home" e poi "SEMPRE". Se hai gia' fatto device owner, Assistente Home si mette come
Home permanente da sola e questo passo risulta gia' fatto.

Il vecchio launcher non si disinstalla: si spegne. Resta la rete di
sicurezza - se un giorno Assistente Home non partisse, una riga da adb (pm enable
com.android.launcher3) ridarebbe una schermata al tablet.
'@
    controlla = { param($i) if ($i.homePreferita) { 'si' } else { 'no' } }
    fai = { Allestimento-Comando 'am start -a android.intent.action.MAIN -c android.intent.category.HOME' 'scelta della Home aperta sul tablet: tocca Assistente Home e poi SEMPRE' }
  }

  [ordered]@{
    chiave = 'account'
    titolo = 'Nessun account sul tablet'
    spiega = @'
Serve solo per il passo dopo, ed e' una regola di Android, non nostra: "dpm
set-device-owner" funziona SOLO su un dispositivo con zero account. E' cosi'
per impedire che qualcuno prenda il controllo del telefono di qualcun altro
mentre e' in uso.

  Impostazioni -> Account -> rimuovi tutti

Gli account si rimettono SUBITO DOPO aver impostato il device owner, e
restano: e' un vincolo del momento in cui lo si imposta, non uno stato in cui
il tablet deve vivere. Quello di Google serve per il Play Store, che qui
serve per tenere aggiornati Spotify e Netflix.

Per questo, se il device owner c'e' gia', questo passo risulta fatto anche con
degli account sul tablet: chiedere di toglierli a cose fatte sarebbe chiedere
una cosa che non serve piu' - e un elenco che segna "da fare" quello che non
c'e' da fare e' un elenco di cui non ci si fida.
'@
    controlla = { param($i)
        if ($i.owner) { return 'si' }
        if ($null -eq $i.account) { return '?' }
        if ([int]$i.account -eq 0) { 'si' } else { 'no' } }
    fai = { Allestimento-Comando 'am start -a android.settings.SYNC_SETTINGS' 'aperta la pagina degli account sul tablet' }
  }

  [ordered]@{
    chiave = 'owner'
    titolo = 'Assistente Home e'' device owner'
    spiega = @'
E' il passo che cambia di piu', e su questo tablet cura anche un difetto di
fabbrica.

Da device owner Assistente Home puo': nascondere le Impostazioni prima che il sistema
mandi BOOT_COMPLETED - e con quelle il receiver del ROM che si rimanda il
messaggio all'infinito, mangiandosi due core per tutto il tempo in cui il
tablet e' acceso (docs/ciclo-impostazioni.md); spegnere la schermata di
blocco; entrare in chiosco senza chiedere conferma ogni volta; concedersi da
sola i permessi che su API 24 andrebbero chiesti a mano.

Si toglie con: adb shell dpm remove-active-admin dev.casa/.AdminReceiver

Va fatto DOPO aver installato Assistente Home e con zero account. Se rispondesse "Not
allowed to set the device owner because there are already some accounts on
the device", e' il passo qui sopra che manca.
'@
    controlla = { param($i) if ($i.owner) { 'si' } else { 'no' } }
    fai = { Allestimento-DeviceOwner }
  }

  [ordered]@{
    chiave = 'notifiche'
    titolo = 'Il permesso di leggere le notifiche'
    spiega = @'
Senza, Assistente Home comanda lo stesso quello che suona - i tasti media non chiedono
niente a nessuno - ma non sa DIRE cosa sta suonando: la riga "cosa suona"
della Home resta vuota.

Il motivo e' che MediaSessionManager.getActiveSessions() pretende che chi
chiama sia un ascoltatore di notifiche abilitato, e usa il nome del servizio
come prova d'identita'. E' l'unica strada su API 24: MEDIA_CONTENT_CONTROL e'
riservato alle app di sistema, e nemmeno il device owner se lo concede.

Il permesso si da' da adb, una volta sola, aggiungendo Assistente Home all'elenco degli
ascoltatori. Assistente Home viene poi fermata e riaperta: il servizio si lega
all'avvio, e senza riaprirla resterebbe con la vecchia risposta in mano.
'@
    controlla = { param($i) if ($i.notifiche) { 'si' } else { 'no' } }
    fai = { Allestimento-Notifiche }
  }

  [ordered]@{
    chiave = 'duraspeed'
    titolo = 'DuraSpeed, che ammazza le app in secondo piano'
    spiega = @'
DuraSpeed e' di MediaTek e chiude le app che non sono in primo piano. Con lui
acceso, meta' di Assistente Home non funziona e non si capisce perche': la radio si
zittisce appena apri un'altra app, e le sveglie possono saltare.

Il tasto lo toglie per l'utente 0 e mette a zero anche il suo interruttore -
ci sono segnalazioni che si riaccenda da solo dopo un riavvio, e due
serrature sulla stessa porta costano niente.

Se il dispositivo non e' un MediaTek, questo passo dira' sempre "non c'e'", ed
e' giusto cosi'.

Sull'E960 c'e' molto altro da togliere - ADUPS, il FOTA di Shanghai, il
generatore di IMEI con i permessi SMS, la roba di chi aveva il tablet prima -
e lo fa tools\sistema.ps1, che va lanciato a parte perche' e' scritto per
QUESTO ROM e sa cosa non toccare.
'@
    controlla = { param($i)
        if (-not $i.letto) { return '?' }
        if ($i.duraspeed) { 'no' } else { 'si' } }
    fai = { Allestimento-Duraspeed }
  }

  [ordered]@{
    chiave = 'verificatore'
    titolo = 'Il verificatore del Play Store'
    spiega = @'
Play Protect controlla ogni APK che si installa, e sui nostri ci moriva sopra
a ogni volta:

  E Finsky: VerifyApps V31SignatureVerification: Failed to collect
            certificates for the package: dev.casa

Il sintomo era "Google Play Store has stopped" a ogni build, con la finestra
di sistema che copriva Assistente Home. La causa non e' l'APK - e' firmato v1 e v2, come
vuole API 24 - ma un verificatore che si aspetta uno schema di firma 3.1 e
degli split che qui non ci sono.

Su un apparecchio dedicato, dove le app si installano da qui e non dal
negozio, il controllo non serve. Si spegne, e le installazioni diventano
anche piu' rapide.

Per riaccenderlo: settings put global package_verifier_enable 1
'@
    controlla = { param($i)
        if ($null -eq $i.verificatore -or $i.verificatore -eq 'null') { return '?' }
        if ($i.verificatore -eq '0') { 'si' } else { 'no' } }
    fai = { Allestimento-Verificatore }
  }

  [ordered]@{
    chiave = 'animazioni'
    titolo = 'Animazioni a meta'' durata'
    spiega = @'
Mezza durata, non zero. A zero spariscono anche le dissolvenze e il passaggio
fra Assistente Home e Spotify diventa uno scatto a schermo: si perde il senso di dove si
sta andando.

Su un Cortex-A7 a 1,3 GHz meta' durata e' la differenza fra "reagisce" e "ci
pensa". Il WindowManager le rilegge al volo, senza riavvio.

Per tornare alle originali: settings put global window_animation_scale 1
(e le altre due).
'@
    controlla = { param($i)
        if ($null -eq $i.animazioni -or $i.animazioni -eq 'null') { return '?' }
        if ([double]$i.animazioni -lt 1.0) { 'si' } else { 'no' } }
    fai = { Allestimento-Animazioni }
  }

  [ordered]@{
    chiave = 'log'
    titolo = 'I log di Assistente Home, che questo ROM filtra'
    spiega = @'
Questo ROM nasce con log.tag = E: il livello di serie per qualunque tag e'
ERRORE, e tutto il resto viene buttato PRIMA di arrivare a logcat.

Il sintomo e' peggio di un registro assente, perche' logcat risponde e non
dice niente - sembra che l'app non stia girando. Ci sono volute mezz'ora e un
"log -t prova" per capirlo, ed e' anche il motivo per cui Assistente Home scrive tutto
sotto il tag "Casa" e mette il nome del pezzo nel messaggio.

Il tasto apre il tag Casa con persist.log.tag.Casa, che sopravvive al
riavvio. Non serve su un dispositivo normale, e li' non fa danno.
'@
    controlla = { param($i)
        if (-not $i.letto) { return '?' }
        if ($i.logCasa -match '^[VDI]$') { 'si' } else { 'no' } }
    fai = { Allestimento-Log }
  }

  [ordered]@{
    chiave = 'nativo'
    titolo = 'Assistente Home compilata in codice nativo'
    spiega = @'
Da Android 7 l'installazione non compila piu' subito: lascia l'app in
"interpret-only" e rimanda il lavoro a un servizio che gira quando il
dispositivo e' fermo e in carica. Su questo tablet non e' mai girato, e il
risultato era che TUTTO - Spotify, Netflix, Chrome, Assistente Home - girava
interpretato. Su un A7 a 1,3 GHz e' la differenza fra secondi e decine di
secondi.

Va rifatto DOPO OGNI adb install. Il tasto compila solo dev.casa, che e'
questione di secondi; per le app grosse c'e' tools\compila.ps1, e li' e' un
lavoro da lanciare quando il tablet non serve a nessuno: su Spotify ci ha
messo nove minuti saturando i quattro core.

Si paga in spazio (l'.odex sta accanto all'APK) e si compra CPU. Su un
apparecchio sempre attaccato alla corrente e' lo scambio giusto.
'@
    controlla = { param($i)
        if (-not $i.casa) { return '?' }
        if ($i.compilata -match 'speed|everything') { 'si' } else { 'no' } }
    fai = { Allestimento-Compila }
  }

  [ordered]@{
    chiave = 'rotazione'
    titolo = 'La rotazione bloccata'
    spiega = @'
Un apparecchio appeso al muro non deve girare se qualcuno lo urta.

Assistente Home e' gia' dichiarata "userLandscape", quindi verticale non diventa mai: le
due orizzontali pero' restano, e su questo pannello una delle due e'
capovolta rispetto a come sta appeso.

  settings put system accelerometer_rotation 0
  settings put system user_rotation 3        (0, 1, 2, 3)

Il tasto fa la prima. Il numero della seconda dipende da come e' appeso il
tuo: provale, sono quattro.
'@
    controlla = { param($i)
        if ($null -eq $i.rotazione -or $i.rotazione -eq 'null') { return '?' }
        if ($i.rotazione -eq '0') { 'si' } else { 'no' } }
    fai = { Allestimento-Comando 'settings put system accelerometer_rotation 0' 'rotazione automatica spenta' }
  }
)

# ============================================================ i dispositivi ==

<##
 # Tutti i dispositivi collegati, non solo il nostro.
 #
 # "adb devices -l" li elenca tutti con il loro stato, e lo stato e' meta'
 # dell'informazione: "unauthorized" vuol dire che manca una conferma sullo
 # schermo, "offline" che il cavo balla o che il tablet si e' appena riavviato.
 # Un elenco che mostrasse solo quelli pronti nasconderebbe proprio i due casi
 # in cui questa pagina serve.
 #>
function Cerca-Dispositivi {
    $tendinaDisp.Items.Clear()
    $righe = & $adb devices -l 2>$null
    $trovati = @()
    foreach ($r in $righe) {
        if ($r -match '^([^\s]+)\s+(device|unauthorized|offline|no permissions)') {
            $seriale = $Matches[1]
            $stato = $Matches[2]
            $modello = ''
            if ($r -match 'model:([^\s]+)') { $modello = $Matches[1] }
            $etichetta = $seriale
            if ($modello) { $etichetta += "  ($modello)" }
            if ($stato -ne 'device') { $etichetta += "  [$stato]" }
            $trovati += @{ seriale = $seriale; stato = $stato; etichetta = $etichetta }
        }
    }
    $script:allDispositivi = $trovati
    foreach ($d in $trovati) { [void]$tendinaDisp.Items.Add($d.etichetta) }

    if ($trovati.Count -eq 0) {
        $eChi.Text = 'Nessun dispositivo collegato. Comincia dal primo passo dell''elenco: il cavo.'
        $eChi.ForeColor = $script:cRosso
        $script:allSeriale = $null
        return
    }

    # Se c'e' l'E960 si sceglie quello, che nove volte su dieci e' quello che
    # si sta preparando. Se non c'e', il primo - ma scritto in alto, ambrato.
    $preferito = 0
    for ($i = 0; $i -lt $trovati.Count; $i++) {
        $b = (& $adb -s $trovati[$i].seriale shell getprop ro.build.display.id 2>$null) -join ''
        if ($b.Trim() -like 'E960*') { $preferito = $i; break }
    }
    $tendinaDisp.SelectedIndex = $preferito
}

function Scegli-Dispositivo {
    $i = $tendinaDisp.SelectedIndex
    if ($i -lt 0 -or $i -ge $script:allDispositivi.Count) { $script:allSeriale = $null; return }
    $script:allSeriale = $script:allDispositivi[$i].seriale
}

# ============================================================= che cosa sa ===

<##
 # Tutto quello che c'e' da sapere sul dispositivo, in una andata e ritorno
 # sola.
 #
 # Sedici passi con sedici controlli sarebbero sedici chiamate ad adb, cioe'
 # un paio di secondi di finestra ferma ogni volta che si preme "Controlla
 # tutto". Qui il ciclo gira nella shell del tablet e il PC legge il risultato,
 # che e' la stessa strada della pagina "Il tablet".
 #>
function Raccogli-Info {
    $i = @{ stato = 'assente'; letto = $false }

    Cerca-Dispositivi
    Scegli-Dispositivo
    $i.platform24 = Test-Path (Join-Path $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }) 'platforms\android-24\android.jar')

    if (-not $script:allSeriale) { $script:allInfo = $i; return }

    foreach ($d in $script:allDispositivi) {
        if ($d.seriale -eq $script:allSeriale) { $i.stato = $d.stato }
    }
    if ($i.stato -ne 'device') { $script:allInfo = $i; return }

    $c = 'echo @@BUILD; getprop ro.build.display.id;' +
         'echo @@API; getprop ro.build.version.sdk;' +
         'echo @@MODELLO; getprop ro.product.model;' +
         'echo @@STAYON; settings get global stay_on_while_plugged_in;' +
         'echo @@PKG; pm list packages dev.casa;' +
         'echo @@HOME; dumpsys package preferred-activities | grep dev.casa;' +
         # Gli apici SINGOLI, e non e' una preferenza: PowerShell toglie le
         # virgolette doppie prima di passare l'argomento ad adb, quindi la
         # shell del tablet riceveva "grep -c Account {" e cercava dentro un
         # file di nome "{". Con gli apici singoli il testo arriva intero e a
         # gestirlo e' la shell di la', che e' quello che si voleva.
         "echo @@ACCOUNT; dumpsys account | grep -c 'Account {';" +
         'echo @@DPM; dumpsys device_policy | head -8;' +
         'echo @@NOTIF; settings get secure enabled_notification_listeners;' +
         'echo @@DURA; pm list packages com.mediatek.duraspeed;' +
         'echo @@VERIF; settings get global package_verifier_enable;' +
         'echo @@ANIM; settings get global window_animation_scale;' +
         'echo @@LOG; getprop persist.log.tag.Casa;' +
         'echo @@DEX; dumpsys package dev.casa | grep -m 1 compilation_filter;' +
         'echo @@ROT; settings get system accelerometer_rotation;' +
         'echo @@FINE'
    $r = AdbSu $script:allSeriale 'shell' $c

    function Uno([string[]]$righe, [string]$nome) { return ((Sezione $righe $nome) -join ' ').Trim() }

    $i.letto     = $true
    $i.build     = Uno $r 'BUILD'
    $i.api       = Uno $r 'API'
    $i.modello   = Uno $r 'MODELLO'
    $i.stayOn    = Uno $r 'STAYON'
    $i.casa      = (Uno $r 'PKG') -match 'package:dev\.casa'
    $i.homePreferita = (Uno $r 'HOME') -match 'dev\.casa/\.MainActivity'
    $i.account   = (Uno $r 'ACCOUNT')
    $dpm         = (Sezione $r 'DPM') -join "`n"
    $i.owner     = ($dpm -match 'Device Owner:') -and ($dpm -match 'package=dev\.casa')
    $i.notifiche = (Uno $r 'NOTIF') -match 'dev\.casa'
    $i.duraspeed = (Uno $r 'DURA') -match 'duraspeed'
    $i.verificatore = Uno $r 'VERIF'
    $i.animazioni = Uno $r 'ANIM'
    $i.logCasa   = Uno $r 'LOG'
    $i.compilata = Uno $r 'DEX'
    $i.rotazione = Uno $r 'ROT'
    if ($i.account -notmatch '^\d+$') { $i.account = $null }

    $script:allInfo = $i
}

function Aggiorna-Allestimento {
    Raccogli-Info
    $i = $script:allInfo

    if (-not $script:allSeriale) {
        # Cerca-Dispositivi ha gia' scritto il motivo.
    } elseif ($i.stato -ne 'device') {
        $eChi.Text = "$($script:allSeriale): stato ""$($i.stato)"" - il dispositivo c'e' ma non risponde ai comandi."
        $eChi.ForeColor = $script:cRosso
    } elseif ($i.build -like 'E960*') {
        $eChi.Text = "$($script:allSeriale) - $($i.build)  |  e' il DUODUOGO E960 di questo progetto."
        $eChi.ForeColor = $script:cVerde
    } else {
        $eChi.Text = ("$($script:allSeriale) - $($i.modello) ($($i.build))  |  NON e' l'E960 di questo progetto: " +
                      "quello che fai qui lo fai su questo dispositivo.")
        $eChi.ForeColor = $script:cAmbra
    }

    $scelto = $grigliaPassi.SelectedIndices
    $indice = -1
    if ($scelto.Count -gt 0) { $indice = $scelto[0] }

    $grigliaPassi.BeginUpdate()
    $grigliaPassi.Items.Clear()
    $fatti = 0; $daFare = 0
    for ($k = 0; $k -lt $script:PASSI.Count; $k++) {
        $p = $script:PASSI[$k]
        $esito = & $p.controlla $i
        $segno = '?'
        $tinta = $script:cTenue
        if ($esito -eq 'si') { $segno = 'fatto'; $tinta = $script:cVerde; $fatti++ }
        elseif ($esito -eq 'no') { $segno = 'da fare'; $tinta = $script:cAmbra; $daFare++ }
        else { $segno = 'non lo so' }

        $riga = New-Object System.Windows.Forms.ListViewItem(('{0}.' -f ($k + 1)))
        [void]$riga.SubItems.Add($segno)
        [void]$riga.SubItems.Add($p.titolo)
        $riga.ForeColor = $tinta
        $riga.Tag = $k
        [void]$grigliaPassi.Items.Add($riga)
    }
    $grigliaPassi.EndUpdate()

    if ($indice -ge 0 -and $indice -lt $grigliaPassi.Items.Count) {
        $grigliaPassi.Items[$indice].Selected = $true
    } elseif ($grigliaPassi.Items.Count -gt 0) {
        # Il primo che manca: e' quello da cui si riparte, e mettercisi sopra da
        # soli risparmia a chi guarda di cercarlo con l'occhio.
        $primo = 0
        for ($k = 0; $k -lt $grigliaPassi.Items.Count; $k++) {
            if ($grigliaPassi.Items[$k].SubItems[1].Text -eq 'da fare') { $primo = $k; break }
        }
        $grigliaPassi.Items[$primo].Selected = $true
    }

    $eRiassuntoPassi.Text = "$fatti passi fatti, $daFare da fare, su $($script:PASSI.Count)."
    if ($daFare -eq 0 -and $script:allSeriale) {
        $eRiassuntoPassi.Text += '  Il tablet e'' allestito: passa alle luci, alle routine e alle app.'
        $eRiassuntoPassi.ForeColor = $script:cVerde
    } else {
        $eRiassuntoPassi.ForeColor = $script:cTenue
    }
}

function Mostra-Passo {
    $scelto = $grigliaPassi.SelectedIndices
    if ($scelto.Count -eq 0) { return }
    $k = [int]$grigliaPassi.Items[$scelto[0]].Tag
    $p = $script:PASSI[$k]
    $ePassoTitolo.Text = ('{0}. {1}' -f ($k + 1), $p.titolo)
    $ePassoTesto.Text = $p.spiega
    # Il tasto si spegne da solo quando il passo non si puo' fare da qui: un
    # pulsante acceso che non fa niente e' peggio di un pulsante spento, perche'
    # la prima volta lo si preme e la seconda non ci si fida piu' degli altri.
    if ($p.fai) {
        $tFaiPasso.Enabled = $true
        $tFaiPasso.Text = 'Fai questo passo'
        $tFaiPasso.Icona = 'spunta'
    } else {
        $tFaiPasso.Enabled = $false
        $tFaiPasso.Text = 'Va fatto sul tablet'
        $tFaiPasso.Icona = ''
    }
}

# ============================================================== le azioni ====
#
# Tutte passano da qui, e tutte scrivono nel registro che cosa hanno fatto: un
# tasto che non dice niente lascia in dubbio se sia successo qualcosa.

function Allestimento-Pronto {
    if (-not $script:allSeriale) { Registra 'Scegli prima un dispositivo.'; return $false }
    if ($script:allInfo.stato -ne 'device') {
        Registra "Il dispositivo e' in stato ""$($script:allInfo.stato)"": non accetta comandi."
        return $false
    }
    return $true
}

function Allestimento-Comando([string]$comando, [string]$detto) {
    if (-not (Allestimento-Pronto)) { return }
    $r = AdbSu $script:allSeriale 'shell' $comando
    if ($r) { Registra ($r -join ' ') }
    Registra $detto
    Aggiorna-Allestimento
}

function Allestimento-ApriImpostazioni {
    if (-not (Allestimento-Pronto)) { return }
    # Da device owner Casa nasconde le Impostazioni, e da nascoste il launcher
    # non le mostra piu': si riaprono per nome, che funziona lo stesso.
    AdbSu $script:allSeriale 'shell' 'pm unhide com.android.settings' | Out-Null
    AdbSu $script:allSeriale 'shell' 'am start -a android.settings.SETTINGS' | Out-Null
    Registra 'Impostazioni aperte sul tablet. Tornano nascoste al prossimo avvio di Assistente Home.'
}

function Allestimento-Installa {
    if (-not (Allestimento-Pronto)) { return }
    $dove = $script:allSeriale
    $apk = Join-Path $radice 'tablet\build\Casa.apk'
    Avvia-Script (Join-Path $radice 'tablet\build.ps1') @() 'compilo Assistente Home' {
        param($uscita)
        if ($uscita -ne 0) { Registra 'La compilazione non e'' andata: non installo niente.'; return }
        if (-not (Test-Path $apk)) { Registra "Non trovo $apk"; return }
        Registra "Installo su $dove ..."
        $r = AdbSu $dove 'install' '-r' $apk
        Registra ($r -join ' ')
        Aggiorna-Allestimento
    }.GetNewClosure()
}

function Allestimento-DeviceOwner {
    if (-not (Allestimento-Pronto)) { return }
    if ($script:allInfo.account -ne '0') {
        Registra "Ci sono $($script:allInfo.account) account sul tablet: il device owner si imposta solo a zero."
        return
    }
    $r = AdbSu $script:allSeriale 'shell' 'dpm set-device-owner dev.casa/.AdminReceiver'
    Registra ($r -join ' ')
    if (($r -join ' ') -match 'Success') {
        Registra 'Fatto. Adesso puoi rimettere l''account Google.'
    }
    Aggiorna-Allestimento
}

<##
 # Aggiunge Casa agli ascoltatori di notifiche, senza buttare via gli altri.
 #
 # La chiave e' una lista separata da due punti, e riscriverla di sana pianta
 # toglierebbe il permesso a chi ce l'aveva - cosa che non si nota subito e che
 # non e' affar nostro.
 #>
function Allestimento-Notifiche {
    if (-not (Allestimento-Pronto)) { return }
    $chiave = 'enabled_notification_listeners'
    $nostro = 'dev.casa/dev.casa.AscoltoNotifiche'
    $prima = ((AdbSu $script:allSeriale 'shell' "settings get secure $chiave") -join '').Trim()
    if ($prima -eq 'null') { $prima = '' }
    $voci = @()
    if ($prima) { $voci = @($prima -split ':' | Where-Object { $_ }) }
    if ($voci -contains $nostro) { Registra "Assistente Home era gia' abilitata."; return }
    $valore = (($voci + $nostro) -join ':')
    AdbSu $script:allSeriale 'shell' "settings put secure $chiave '$valore'" | Out-Null
    # Il servizio si lega all'avvio dell'ascoltatore: senza riavviare il
    # processo, Casa resta con la vecchia risposta in mano e continua a dire di
    # non avere il permesso.
    AdbSu $script:allSeriale 'shell' 'am force-stop dev.casa' | Out-Null
    Registra 'Permesso dato, e Assistente Home riavviata. Metti su qualcosa: deve comparire cosa suona.'
    Aggiorna-Allestimento
}

function Allestimento-Duraspeed {
    if (-not (Allestimento-Pronto)) { return }
    $r = AdbSu $script:allSeriale 'shell' 'pm uninstall -k --user 0 com.mediatek.duraspeed'
    Registra ('duraspeed: ' + ($r -join ' '))
    AdbSu $script:allSeriale 'shell' 'settings put global setting.duraspeed.enabled 0' | Out-Null
    if ($script:allInfo.build -like 'E960*') {
        Registra 'Per la pulizia completa di questo ROM c''e'' tools\sistema.ps1: la lancio adesso.'
        Avvia-Script (Join-Path $radice 'tools\sistema.ps1') @() 'sistemo il tablet'
    } else {
        Aggiorna-Allestimento
    }
}

function Allestimento-Verificatore {
    if (-not (Allestimento-Pronto)) { return }
    foreach ($c in @('settings put global package_verifier_enable 0',
                     'settings put global verifier_verify_adb_installs 0',
                     'settings put global package_verifier_user_consent -1')) {
        AdbSu $script:allSeriale 'shell' $c | Out-Null
    }
    Registra 'Verificatore spento. Per riaccenderlo: settings put global package_verifier_enable 1'
    Aggiorna-Allestimento
}

function Allestimento-Animazioni {
    if (-not (Allestimento-Pronto)) { return }
    foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
        AdbSu $script:allSeriale 'shell' "settings put global $k 0.5" | Out-Null
    }
    Registra 'Animazioni a meta'' durata.'
    Aggiorna-Allestimento
}

function Allestimento-Log {
    if (-not (Allestimento-Pronto)) { return }
    AdbSu $script:allSeriale 'shell' 'setprop persist.log.tag.Casa V' | Out-Null
    AdbSu $script:allSeriale 'shell' 'setprop log.tag.Casa V' | Out-Null
    Registra 'Tag Casa aperto: adesso "logcat -s Casa:I" dice qualcosa.'
    Aggiorna-Allestimento
}

function Allestimento-Compila {
    if (-not (Allestimento-Pronto)) { return }
    Registra 'Compilo dev.casa in nativo: qualche secondo.'
    $r = AdbSu $script:allSeriale 'shell' 'cmd package compile -m speed -f dev.casa'
    Registra ($r -join ' ')
    Aggiorna-Allestimento
}

# ---- i fili ------------------------------------------------------------------

$tCerca.Add_Click({ Aggiorna-Allestimento; Registra 'Dispositivi riletti.' })
$tControlla.Add_Click({ Aggiorna-Allestimento; Registra 'Controllati tutti i passi.' })
$tRicontrolla.Add_Click({ Aggiorna-Allestimento })
$tendinaDisp.Add_SelectedIndexChanged({ Scegli-Dispositivo })
$grigliaPassi.Add_SelectedIndexChanged({ Mostra-Passo })
$tFaiPasso.Add_Click({
    $scelto = $grigliaPassi.SelectedIndices
    if ($scelto.Count -eq 0) { return }
    $k = [int]$grigliaPassi.Items[$scelto[0]].Tag
    $p = $script:PASSI[$k]
    if ($p.fai) { & $p.fai }
})

# Questa pagina si riempie la prima volta che si apre, non all'avvio: quello
# che chiede al tablet costa secondi, e non li deve pagare chi apre il
# programma per fare altro.
$script:allApertura[$PAG_ALLEST] = { Aggiorna-Allestimento }
