# Assistente Home

Assistente Home è un'app Android che prende il posto della schermata Home di un
tablet vecchio e lo trasforma in un pannello da muro o da mobile: ora e meteo,
notizie, radio, Spotify, timer e sveglie, le lampade di casa e una voce a cui
chiedere le cose. Gira su un tablet da 10 pollici del 2019 con **Android 7 e
1 GB di RAM**.

## Cosa fa

| | |
|---|---|
| **Home** | l'ora grande, il meteo (a turno con le notizie), quel che suona coi comandi, le lampade da accendere al volo e il tasto « parla » |
| **Radio** | stazioni italiane coi loro loghi; l'elenco si sceglie dal PC |
| **Spotify** | dentro l'app: playlist, brani che ti piacciono, ricerca, Spotify Connect |
| **App** | le app che servono, più Meteo, Calendario, To-Do List e Notizie fatti dentro l'assistente |
| **Orologio** | timer a un tocco e sveglie vere, che suonano anche dopo un riavvio |
| **Luci e routine** | lampade e routine Tuya (Smart Life) in locale, sulla rete di casa |

Quando la stanza è vuota parte il **riposo**: una foto a tutto schermo con
l'ora, che di sera si abbassa da sola. Tocca e torni alla Home.

## Le tre parti

La repository contiene i sorgenti; le app si compilano sul tuo PC.

| | cartella | come si ottiene |
|---|---|---|
| **Assistente Home**, l'app del tablet | `tablet/` | `tablet\build.ps1 -Install` |
| **Gestione Home**, il programma per Windows | `pc/` | `tools\collegamento.ps1` gli mette l'icona sul desktop; al primo avvio si prepara da solo |
| **L'app del telefono**, per comandare il tablet da fuori | `telefono/` | `telefono\build.ps1 -Install` |

Gestione Home sceglie stazioni, app, lampade, routine, voce e notizie, e le
manda al tablet collegato via USB, senza ricompilare.

## Cosa ti serve

- un tablet Android **7.0 o successivo**, processore ARM;
- un PC Windows con l'**Android SDK** (build-tools, `platforms;android-24`,
  platform-tools) e un **JDK**. La compilazione la fa `tablet\build.ps1` con
  aapt2, javac, d8 e apksigner, senza Gradle;
- `adb` in `tools\platform-tools\` (dalle
  [platform-tools di Google](https://developer.android.com/tools/releases/platform-tools));
- un cavo USB dati e il debug USB acceso sul tablet;
- per Spotify, un account **Premium**.

## Installare

1. Collega il tablet. Se è diverso dal tablet originale, indica agli script il
   seriale che vedi in `adb devices`:

       $env:CASA_TABLET = '<seriale>'

2. Lancia in ordine:

       .\tools\prendi-librespot.ps1        scarica go-librespot (per Spotify)
       .\tools\sistema.ps1 -Verifica       guarda come sta il tablet, senza toccare
       .\tools\sistema.ps1                 lo sistema (servizi che uccidono la radio
                                           e le sveglie in secondo piano)
       .\tools\alleggerisci.ps1 -Essenziale   spegne le app che non servono
       .\tablet\build.ps1 -Install -SetHome   compila, installa, Assistente Home come Home
       .\tools\collegamento.ps1            mette « Gestione Home » sul desktop
       .\telefono\build.ps1 -Install       (facoltativo) l'app sul telefono

   `sistema.ps1` e `alleggerisci.ps1` sono tarati su un tablet MediaTek: su un
   altro tablet leggili prima e parti sempre da `-Verifica`. Aggiornare un'app
   di sistema può essere irreversibile: è spiegato in testa a `tools\sistema.ps1`.

3. Facoltativo: chiudi il tablet come un chiosco (spegne anche le notifiche).
   Serve un tablet con zero account:

       .\tablet\build.ps1 -DeviceOwner

4. Conserva la chiave di firma creata alla prima compilazione in
   `tablet\keystore\`: serve per aggiornare l'app.

Per provare tutto sull'emulatore lancia `.\tools\anteprima.ps1`: usa un AVD
« Casa_E960 » (Android 7.0, 1280x800, 213 dpi, x86_64) con go-librespot per
x86_64.

## Spotify

L'assistente usa [go-librespot](https://github.com/devgianlu/go-librespot), che
parla con Spotify come un altoparlante Connect.

- È un client alternativo, fuori dai termini d'uso di Spotify: funziona oggi,
  e Spotify può cambiare le regole.
- go-librespot è **GPLv3** e arriva dalla release di
  [Android-LibreThing](https://github.com/SEKY443/Android-LibreThing):
  `tools\prendi-librespot.ps1` lo scarica e controlla che sia la versione
  provata. Origine e licenza:
  [tablet/jniLibs/PROVENIENZA.md](tablet/jniLibs/PROVENIENZA.md).

**Primo accesso**: l'app mostra un QR, inquadralo col telefono e approva col
tuo account (oppure apri spotify.com/pair e scrivi il codice). Da lì il tablet
compare anche nell'elenco Connect del telefono.

**Ricerca per nome**: richiede una chiave tua. Playlist, preferiti e artisti
funzionano già.

1. Vai su [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
   e crea un'app: nome e descrizione a piacere, fra le API scegli **Web API**,
   come Redirect URI metti `http://127.0.0.1:8888/callback` (il modulo lo
   chiede).
2. Nell'app apri Settings e copia **Client ID** e **Client secret**.
3. Col tablet collegato:

       .\tools\chiave-spotify.ps1 -Id <client id> -Segreto <client secret>

Tieni la chiave solo sul tablet, fuori dalla repository e dalle issue. Se
finisce in giro, rigenerala dalla dashboard (« Rotate »).

## Le lampade

Lampade Tuya, quelle dell'app Smart Life. L'assistente le comanda sulla rete di
casa con la loro **chiave locale**: prendila una volta da Gestione Home › Le
luci, inquadrando un QR con Smart Life. Le chiavi restano nella configurazione
sul PC e sul tablet. Dettagli: [docs/luci.md](docs/luci.md).

## Cosa crei tu

Restano fuori dalla repository e li creano o scaricano gli script qui sopra:
APK, chiavi delle lampade, chiave Spotify, configurazioni di Gestione Home,
chiave di firma, binario di go-librespot, registrazioni della parola di
attivazione (spenta: si parla premendo).

## Documenti

- [musica.md](docs/musica.md): la sezione Spotify
- [aspetto.md](docs/aspetto.md): com'è fatta l'interfaccia
- [riposo.md](docs/riposo.md): la foto a schermo intero
- [orologio.md](docs/orologio.md): timer e sveglie
- [meteo.md](docs/meteo.md): il meteo
- [notizie.md](docs/notizie.md): le notizie
- [comandi.md](docs/comandi.md): cosa dire all'assistente
- [gestione-dal-pc.md](docs/gestione-dal-pc.md): Gestione Home
- [telefono.md](docs/telefono.md): l'app del telefono
- [ciclo-impostazioni.md](docs/ciclo-impostazioni.md): il bug di fabbrica che rallentava tutto

I documenti parlano del tablet originale, un MediaTek MT6580. Porte: **8775**
(PC, via `adb reverse`) e **8776** (tablet, per il telefono).
