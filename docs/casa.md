# Assistente Home

L'app che fa da Home sul tablet: parte all'accensione, a schermo intero, e si
comanda col dito o con la voce.

    dev.casa        package
    tablet/         sorgenti, build.ps1, keystore
    40 MB           il tetto di RAM (PSS) da rispettare

Sei sezioni, con la barra di navigazione a sinistra: Home, Radio, Spotify,
App, Orologio e la sezione Luci e routine (sullo schermo «Casa»).

Documenti collegati: [orologio.md](orologio.md), [luci.md](luci.md),
[meteo.md](meteo.md), [riposo.md](riposo.md), [agenda.md](agenda.md),
[parola-di-attivazione.md](parola-di-attivazione.md),
[gestione-dal-pc.md](gestione-dal-pc.md), [aspetto.md](aspetto.md),
[musica.md](musica.md), [ciclo-impostazioni.md](ciclo-impostazioni.md).

## Installare

    tablet\build.ps1 -Install

L'installazione finisce col tasto Home simulato, così l'app resta fuori dalle
app recenti:

    am start -a android.intent.action.MAIN -c android.intent.category.HOME

Usa sempre questa forma: con `am start -n dev.casa/.MainActivity` l'app compare
fra le recenti.

Dopo ogni `adb install` ricompila in nativo, o l'app resta lenta:

    .\tools\compila.ps1              compila le app che contano
    .\tools\compila.ps1 -Verifica    dice solo come stanno
    .\tools\compila.ps1 -Solo dev.casa

Lancialo quando il tablet è libero: dex2oat occupa tutti i core per minuti.

Note di build:

- Il keystore usa la password `casadev`: `keytool` chiede almeno 6 caratteri.
- Il parametro per la Home si chiama `-SetHome`: `$Home` è una variabile
  automatica di PowerShell.

## Device owner

    dpm set-device-owner dev.casa/.AdminReceiver     -> Success

Si imposta solo con zero account sul dispositivo; dopo, l'account Google si può
rimettere. Dà all'app:

- **Home permanente** (`addPersistentPreferredActivity`);
- **Lock task** senza conferme;
- il permesso del microfono, concesso da sola (`setPermissionGrantState`);
- schermata di blocco spenta (`dpm.setKeyguardDisabled`);
- area notifiche chiusa e calendario di sistema riattivato (vedi sotto).

`launcher3` resta installato come riserva, disattivato. Per riaverlo:

    adb shell pm enable com.android.launcher3

## Il chiosco

    adb shell settings put global casa_chiosco 1     accende
    adb shell settings put global casa_chiosco 0     spegne

Tienilo spento finché il tablet è in allestimento: col lock task attivo le
Impostazioni restano fuori portata. Accendilo quando l'app è finita.

## Sistemare il tablet

`tools/sistema.ps1` è idempotente; con `-Verifica` controlla e basta. Fa questo:

- toglie **DuraSpeed**, che zittisce la radio quando si apre un'altra app;
- toglie **`com.android.santoservice`** (`/system/app/AutoGenIMEI`);
- toglie la **telefonia**, dopo aver verificato che lo slot SIM è vuoto;
- svuota e disattiva **launcher3**, solo se l'app risulta la Home preferita;
- dimezza le **animazioni**;
- apre i tag di registro dell'app (`persist.log.tag.Casa` e compagni);
- con `-Verifica` dice anche se il ciclo di Impostazioni è in corso (vedi
  [ciclo-impostazioni.md](ciclo-impostazioni.md)).

Tutto si annulla reinstallando l'APK da `/system`: vedi
pulizia-e-aggiornamento.md.

`tools/alleggerisci.ps1` toglie all'utente 31 pacchetti, Play Store compreso.
`.\alleggerisci.ps1 -Rimetti` rimette tutto. `-SenzaGoogle` toglie anche Play
Services, ma così sparisce il riconoscimento del parlato: provalo e misura.

Tieni installati Chrome (è la WebView di sistema), l'app Google (fornisce
`SpeechRecognizer`) e `com.google.android.tts` (la voce).

## Ruotare e fissare lo schermo

Il manifest usa `userLandscape`: le due orizzontali, col sensore o con la
rotazione bloccata. Per fissarla:

    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation 3       (0 1 2 3)

## L'area notifiche

La tendina resta vuota per tutte le app. Ci pensa l'assistente, da device owner:

| | |
|---|---|
| `MainActivity.chiudiLAreaNotifiche()` | `setStatusBarDisabled`: via icone, tendina, finestrelle, suoni e vibrazioni delle notifiche |
| `AscoltoNotifiche` | cancella ogni notifica cancellabile appena arriva |

Per una manutenzione (Wi-Fi, impostazioni rapide):

    adb shell settings put global casa_area_notifiche 1     la riapre al prossimo avvio dell'app
    adb shell settings put global casa_area_notifiche 0     la richiude

poi riavvia l'app. Con l'area aperta l'app smette anche di cancellare. Nel
registro:

    I/Casa: area notifiche chiusa
    I/Casa: notifiche: tolte quelle di com.google.android.googlequicksearchbox

## Leggere il registro

Questo ROM scarta tutto tranne gli errori (`log.tag=E`):

    adb shell getprop log.tag     ->  E

`sistema.ps1` apre i tag dell'app; un tag nuovo va aggiunto lì. Quando
l'assistente parla, il sintetizzatore riempie logcat in pochi secondi: leggi con
`logcat -d` subito dopo il comando, oppure guarda lo schermo.

## Parlare all'assistente

Premi il microfono e parla: un ascolto per tocco. Mentre ascolta la pastiglia
mostra «annulla».

| Pezzo | Come |
|---|---|
| **Orecchio** | `SpeechRecognizer` a richiesta, italiano |
| **Parola di attivazione** | **spenta: si parla premendo.** Si riaccende con `MainActivity.PAROLA_DI_ATTIVAZIONE`. Vedi [parola-di-attivazione.md](parola-di-attivazione.md) |
| **Bocca** | `TextToSpeech` con `Locale.ITALIAN` |
| **Comandi** | regole a parole chiave in `Comandi.java` |
| **Radio** | `MediaPlayer` su stream MP3/AAC |
| **Timer** | conto alla rovescia a schermo, avviso a voce quando scade |

Tieni spento il ciclo di ascolto continuo (`accendi()`): manda a Google tutto il
parlato della stanza.

Regole del riconoscitore:

- **Mentre parla, il microfono resta chiuso.**
- **Pausa prima di riprovare.** `NO_MATCH` e `SPEECH_TIMEOUT` ripartono subito;
  gli altri errori aspettano tre secondi.
- **La sordina** (`Sordina.java`) abbassa quello che suona **prima** di
  `startListening` e lo rialza a fine risposta: la radio dell'app direttamente,
  le altre app col focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.

### Cosa capisce

    che ore sono  /  che giorno è
    come ti chiami  /  come stai  /  chi ti ha fatto  /  grazie  /  ciao

    metti un timer di dieci minuti        (anche "un'ora e mezza",
                                           "due minuti e trenta secondi",
                                           "un quarto d'ora")
    annulla il timer
    sveglia alle sette                    (anche "alle 6 e mezza")

    accendi la radio                      (o "metti rai radio 1", "virgin")
    metti la musica  /  metti su Spotify
    metti Levante su Spotify              (la playlist "This Is Levante")
    cerca <una canzone>
    avanti / skip / next / indietro       (sa se comanda la radio o Spotify
                                           da cosa sta suonando)
    pausa / riprendi / a caso
    cosa sta suonando

    accendi la luce  /  spegni la camera
    accendi la camera di rosso            (21 colori, più bianca/calda/fredda)
    comodino al trenta per cento

    alza il volume  /  volume al 40
    silenzio  /  spegni tutto
    apri Netflix
    cosa sai fare

### Quando risponde

- A voce, solo a chi ha parlato. Chi tocca vede una scritta per tre secondi e
  mezzo.
- Il timer che scade parla sempre.
- Con una parola storta propone la frase giusta:

        "metti la raddio"   ->  Non sono sicuro. Volevi dire metti la radio?

  e scrive nel registro la frase caduta:

        Casa: PC nonCapito fammi un frullato

  Gestione Home le raccoglie nella pagina **I comandi**.

### La voce

    PAROLA --es cosa voci                       elenca, e dice qual è in uso
    PAROLA --es cosa voce  --es valore <nome>   la sceglie (vuoto = automatica)
    PAROLA --es cosa ritmo --ef valore 0.90
    PAROLA --es cosa tono  --ef valore 0.95

Il tono va da 0,8 a 1,2. A ogni modifica il tablet pronuncia una frase di prova.
Senza una scelta, `Voce.scegliVoce` prende la prima voce maschile locale in
ordine alfabetico (`it-it-x-kda#male_1-local`).

## Aggiungere un comando

Tutto sta in `tablet/src/dev/casa/Comandi.java`, dentro `esegui()`. Una regola è
un ramo della catena:

    } else if (contiene(f, "che batteria", "quanta batteria", "sei carico")) {
        rispondi(quantaBatteria());

**1. Scegli le parole.** `contiene` cerca sottostringhe: mettine parecchie.
Parti dalle frasi vere nel riquadro *cosa non ha capito* della pagina **I
comandi** di Gestione Home.

**2. Mettila al posto giusto.** Vince la prima regola che prende la frase: la
più specifica va **prima** della più generica. Gli ordini già fissati:

- `annulla il timer` prima di quella che avvia un timer;
- `spegni tutto` prima delle luci;
- il **colore** (`accendi la camera di rosso`) prima di acceso/spento;
- `cosa sta suonando` prima della radio e della musica;
- `metti Levante su spotify` prima della regola generica della musica.

Se due comandi si contendono una parola e l'ordine da solo è insufficiente,
cambia nome al più giovane (per questo la routine si chiama **Cinema** e
**Tutte accese**).

**3. Usa gli aiuti che ci sono:**

| | |
|---|---|
| `contiene(f, "a", "b")` | vero se la frase contiene una delle parole |
| `numeroIn(f)` | il primo numero, in cifre o in lettere ("trenta" -> 30) |
| `durataIn(f)` | i secondi che dice la frase, sommando le parti |
| `dopoLaParola(f, "cerca")` | quel che viene dopo la parola chiave |
| `rispondi(testo)` | **usa sempre questo**: parla se la richiesta è arrivata a voce, scrive se è arrivata col dito |

**4. Aggiungi la parola a `VICINANZE`**, per il suggerimento: con
`{ "batteria", "che batteria hai" }`, «che batteris» diventa *«Volevi dire che
batteria hai?»*.

**5. Scrivila in due elenchi:**

    pc/GestioneHome.ps1     la variabile $ELENCO_COMANDI, pagina "I comandi"
    docs/casa.md            la lista qui sopra

**6. Compila, installa e prova** (vedi sotto).

## Provare senza parlare

In Gestione Home, pagina **I comandi**, scrivi la frase nel riquadro *prova una
frase* e premi invio. Da riga di comando:

    adb shell "am broadcast -a dev.casa.DI --es frase 'che batteria hai'"

Da PowerShell metti gli apici singoli *dentro* le doppie, o `am` prende un
pezzo della frase come nome di pacchetto. Nel registro trovi frase e risposta:

    I/Casa: prova: spegni la radio
    I/Casa: dice: Ho spento la radio.

## Comandi e risposte scritti dal PC

### Comandi dell'utente

`ComandiUtente` legge da `casa.json` comandi fatti di tre parti:

    quando   le frasi che lo fanno scattare
    passi    che cosa fa - gli stessi passi delle routine
    dici     come risponde, in più modi

- Vengono **prima** di quelli del codice. Ogni scatto scrive nel registro il
  nome della regola che ha vinto.
- Le risposte accettano `{ora}`, `{data}`, `{suona}`, `{luci}`, `{nome}`. Un
  segnaposto sbagliato resta scritto com'è, a schermo.
- Un passo `frase` scende ai comandi del codice di un livello solo.

### Risposte fisse

Stanno in `Risposte.java`, ognuna con un **nome**:

    { "radio.spenta", "Radio", "Radio spenta." },
    { "musica.pausa", "Musica", "In pausa." },

- Riscrivile da Gestione Home: finiscono in `casa.json` sotto `risposte`, e la
  nuova frase vale in tutti i punti che usano quel nome.
- Un nome assente dal catalogo viene saltato e segnalato nel registro.
- Le risposte composte («Sono le 7 e 20.») si cambiano con un comando
  dell'utente e i segnaposto.

## La configurazione dal PC

Luci, routine, app e comandi stanno in `casa.json`, scritto da Gestione Home.
Il giro completo è in [gestione-dal-pc.md](gestione-dal-pc.md).

    getFilesDir()/casa.json                      la configurazione vera
    getExternalFilesDir()/da-mettere.json        la buca: ci scrive il PC
    getExternalFilesDir()/adesso.json            la vetrina: ci scrive l'app

- Il PC spinge la buca con `adb push` e la annuncia con un broadcast; l'app la
  legge e la cancella.
- Il file **sostituisce** gli elenchi di fabbrica. Se è illeggibile, l'app
  riparte dai valori nel codice; un array vuoto vuol dire zero voci.

### Spotify: chiave e accesso

    adb shell am broadcast -a dev.casa.CHIAVE --es cosa stato
    -> PC spotify chiave=true id=<prime 4>...<ultime 4> accesso=false utente=-

`--es cosa dimentica` cancella solo `credentials.json`, per cambiare account.

## La Home

Orologio e data, la riga di quel che scorre o suona, il microfono, il meteo.

### Quel che suona

La scheda comanda la radio dell'app oppure qualunque app che pubblichi una
`MediaSession` (Spotify, Netflix). Per vedere titolo e artista abilita una volta
l'ascoltatore di notifiche:

    .\tools\permesso-notifiche.ps1

Anche senza, i tasti funzionano. Se suona la radio dell'app, i tasti vanno alla
radio; altrimenti all'app che suona.

A tablet in silenzio la scheda mostra **L'ULTIMO ASCOLTO** (`Ultimo.java`):
play fa ripartire l'ultima stazione o l'ultimo brano.

### Il volume

**Meno, numero, più**, a passi del dieci per cento, su `STREAM_MUSIC`: vale per
radio, musica e altre app. Segue anche i tasti sul fianco.

### Il meteo

Tocca il nome della città nella pagina Meteo per sceglierne un'altra: fino a
cinque posti salvati, più una ricerca. Dettagli in [meteo.md](meteo.md).

## HTTPS e certificati

Su questo Android mancano le radici di Let's Encrypt. Sintomo:
`Trust anchor for certification path not found`.

Le radici aggiunte sono in `tablet/assets/radici/` e le legge
[`Fiducia`](../tablet/src/dev/casa/Fiducia.java):

    Casa: Fiducia: 148 radici di sistema + 4 nostre

- Aggiungile alle radici di sistema, sempre in aggiunta.
- Tieni acceso il controllo dei certificati: niente `TrustManager` che accetta
  tutto.
- Per un nuovo servizio HTTPS con lo stesso errore, aggiungi lì la sua radice.

## La radio

Tocca una stazione per accenderla, toccala di nuovo per spegnerla. La barra ha
precedente, ferma/avvia, successiva e volume.

- Le **sigle** delle stazioni si scrivono a mano.
- `Radio.risolvi()` segue i reindirizzamenti, scioglie le playlist e taglia
  dopo `/;` a ogni accensione.
- Dopo **quindici secondi** senza audio la barra scrive l'errore.

### Aggiungere una stazione

Una stazione resta su «mi collego» per tre motivi:

1. reindirizzamento da `http://` a `https://`;
2. certificato Let's Encrypt;
3. indirizzo della playlist (`;listen.pls`) al posto del flusso (`host:porta/;`).

Prova ogni stazione prima dal PC, poi sul tablet a volume zero: nella
`MediaSession`, `state=3` vuol dire che suona. Fra le varianti preferisci:

    HTTP semplice   meglio di   HTTPS
    MP3             meglio di   AAC
    flusso diretto  meglio di   HLS

## Luci e routine

Tutto in [luci.md](luci.md). Lampade, routine e app si scrivono da Gestione Home
in `casa.json`.

- Le lampade si rileggono entrando nella sezione, con la freccia e dopo ogni
  comando: niente controlli a ciclo.

Un passo di routine ha un tipo:

    luce     una lampada, un'azione, un valore
    frase    una frase girata a Comandi, come se la dicessi
    attesa   una pausa

Usa `attesa` fra due passi che dipendono l'uno dall'altro (per esempio «metti
la radio» e «volume al 30»). L'icona di una routine si sceglie da una tendina
sul PC.

## L'orologio

Timer e sveglie stanno in `Orologio`, registrati in AlarmManager, e
sopravvivono a riavvii e reinstallazioni. Tutto in [orologio.md](orologio.md).

Per chi modifica il codice:

- `Orologio.di(context)` restituisce sempre la stessa istanza.
- Una funzione che deve valere a schermo spento va fuori dalle View.
- La sveglia che suona si mostra con `Telaio.mostra()`; `Telaio.adotta()` le
  passa il vetro.

## La sezione App

Tessere di fabbrica: Meteo, Chrome, Impostazioni. Cambia l'elenco dalla pagina
**Le app** di Gestione Home. Si vedono solo le app installate, e un'app
reinstallata ricompare da sola.

**Riavvia**, l'ultima tessera, spegne e riaccende l'app: tocca due volte (il
primo tocco scrive «premi ancora»). Resta sempre, fuori dalla configurazione.

## Il riposo

Dopo cinque minuti di immobilità, se niente è in corso, compare una foto a tutto
schermo con l'ora e il tempo. Un tocco riporta alla Home. Per usare le tue foto,
mettile in `files/paesaggi/` da adb. Dettagli in [riposo.md](riposo.md).

## Il calendario

L'app riattiva all'avvio `com.android.providers.calendar` e
`com.google.android.syncadapters.calendar`. **Dopo, riavvia il tablet una
volta**: prima del riavvio `content query` risponde «Could not find provider».
L'interfaccia è in [agenda.md](agenda.md).

## Sfondo e vetro

`Sfondo.java` compone lo sfondo a codice e lo installa con `WallpaperManager`,
per sistema e blocco.

- Componi della misura che chiede il sistema (`getDesiredMinimumWidth/Height`).
- Riscrivi lo sfondo solo se cambia la variante del giorno o l'id.
- Lascia stare `DISALLOW_SET_WALLPAPER`: bloccherebbe anche l'app.
- Calcola la sfocatura del vetro una volta all'avvio, in ARGB_8888.
- Evita `FLAG_SHOW_WALLPAPER`, `BlurMaskFilter` e `saveLayer` a schermo intero.

## Regole per il codice dell'interfaccia

La RAM è 1 GB: View disegnate a mano, `Paint` fissi, zero allocazioni in
`onDraw`. Vedi anche [aspetto.md](aspetto.md).

- Ridisegna solo quando il dato cambia.
- `setClickable(true)` su ogni View disegnata a mano che risponde al dito, o
  manca `ACTION_DOWN`.
- `onResume` arriva prima della misura: calcola il layout in `disponi(w, h)` e
  saltalo se la larghezza è zero.
- Misura il testo col Paint che lo disegna, con `Testo.Riga` e `Testo.Blocco`;
  tienine uno per scritta, anche per le parole corte.
- Ricava le misure dallo spazio disponibile e dalla scala di
  [`Misure`](../tablet/src/dev/casa/Misure.java).
- Passa lo stato separato dal nome (`staAprendo()`, `errore()`).
- Il lato lungo è la larghezza, sempre; usa `getRealSize()`.
- Una Bitmap alla volta: su API 24 i pixel stanno nell'heap Java.
- Dissolvenza: una View alla volta con alpha minore di uno, e
  `hasOverlappingRendering()` a `false` in `Sezione`.
- Il layout del telaio sta in `Telaio.onLayout`.
- L'elenco visibile delle tessere è una lista a parte, usata sia per disegnare
  sia per il tocco.
- Ogni comando deve vedersi, oltre ad avere il bersaglio intero.

## Netflix

Il Netflix di fabbrica viene rifiutato dai server:

    Sorry, we could not reach the Netflix service. Please try again later. (-14)

Installa la **8.143.5** (l'ultima per API 24) da un APK procurato fuori, con
`adb install`, poi `tools\compila.ps1`. Tienine una copia in locale.

## Se qualcosa va storto

| Sintomo | Cosa fare |
|---|---|
| logcat muto sull'app | Lancia `sistema.ps1`; se l'app ha parlato, leggi subito |
| Una radio resta su «mi collego» | Vedi «Aggiungere una stazione» |
| Netflix dà `(-14)` | Vedi «Netflix» |
| `Trust anchor for certification path not found` | Aggiungi la radice in `tablet/assets/radici/` |
| App lente | `.\tools\compila.ps1 -Verifica`, poi `compila.ps1` |
| L'app compare fra le recenti | Riaprila col tasto Home, `am start ... category.HOME` |
| Interfaccia gigante dopo un riavvio | Misure prese in verticale: lato lungo = larghezza |
| Calendario vuoto, «Could not find provider» | Riavvia il tablet una volta |
| Il tablet si addormenta durante un'installazione | `adb shell settings put global stay_on_while_plugged_in 3`, poi rimetti `0` |
| CPU alta di system_server | Ciclo di Impostazioni: `sistema.ps1 -Verifica` |
| Il microfono sente la radio | Controlla `Sordina.java` |

## Prossimi passi

- **Un LLM dietro le regole**, per le frasi che le regole lasciano cadere.
- **La presa a 192.168.1.2**: manca la chiave locale. Il giro è in
  [luci.md](luci.md).
- **Una sveglia che accende le luci** invece di suonare.
- **Cancellazione d'eco**: misura se riceve il riferimento anche dalla musica.
