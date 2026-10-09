# Spotify nell'app

Assistente Home suona Spotify con **go-librespot**, un demone che gira dentro
l'app. L'interfaccia è la sezione **Spotify**; le classi si chiamano `Musica` e
`SezioneMusica`. Serve un account Spotify **Premium**.

## Come è fatta

    ┌── Assistente Home ─────────────────────────────────────┐
    │                                                        │
    │  SezioneMusica ──── Musica ────HTTP──► 127.0.0.1:24879 │
    │       │                │                     ▲         │
    │       │                │ ProcessBuilder      │         │
    │       │                ▼                     │         │
    │       │        libgolibrespot.so ────────────┘         │
    │       │                │                               │
    │       │                │ PCM s16le 44,1 kHz            │
    │       │                ▼                               │
    │       │           audio.pipe (FIFO)                    │
    │       │                │                               │
    │       └── TuboAudio ───┴──► AudioTrack ──► altoparlante │
    └────────────────────────────────────────────────────────┘

| file | cosa fa |
|---|---|
| `Musica.java` | accende e sorveglia il demone, scrive la configurazione, parla con la sua API, pubblica la MediaSession |
| `TuboAudio.java` | legge la FIFO e versa i campioni in un `AudioTrack` |
| `Preferiti.java` | playlist, brani di una playlist, brani preferiti |
| `Copertine.java` | le immagini: ridotte, con un tetto, con copia su disco |
| `Cerca.java` | ricerca, pagina dell'artista e dell'album |
| `Tastierino.java` | la tastiera dell'app |
| `Qr.java` | il codice QR dell'accoppiamento |
| `SezioneMusica.java` | tutto quello che si vede |
| `jniLibs/armeabi-v7a/libgolibrespot.so` | il demone. Vedi `jniLibs/PROVENIENZA.md` |

## Installare o aggiornare go-librespot

Il demone è il binario `lib/armeabi-v7a/libgolibrespot.so` dell'APK di
[SEKY443/Android-LibreThing](https://github.com/SEKY443/Android-LibreThing)
(file `app-armeabi-v7a-release.apk`), un ELF ARM a 32 bit.

1. Scarica l'APK `app-armeabi-v7a-release.apk` dalle release del progetto.
2. Apri l'APK come uno zip ed estrai `lib/armeabi-v7a/libgolibrespot.so`.
3. Copialo in `tablet/jniLibs/armeabi-v7a/libgolibrespot.so` e aggiorna
   `jniLibs/PROVENIENZA.md`.
4. Ricompila e installa l'app.

L'installer estrae il file con il bit di esecuzione, e l'app lo lancia come
processo figlio con `ProcessBuilder`.

Il binario deve avere le due toppe di Android-LibreThing:

- `android-zeroconf.patch`: l'app passa al demone l'interfaccia di rete in
  quattro campi di configurazione. Controlla che nelle stringhe del binario ci
  siano i campi `android_net_iface_*`.
- `android-clienttoken.patch`: il demone si presenta come Linux a Spotify.

Tieni l'allineamento a 4 KB: con 16 KB il demone si ferma all'avvio.

All'avvio il linker di Android 7.0 stampa un avviso innocuo:

    WARNING: linker: unsupported flags DT_FLAGS_1=0x8000001

Per **modificare** il demone compilalo con `scripts/build-go-native.sh` di
Android-LibreThing: NDK + vcpkg (libvorbis/libflac/mpg123) +
`GOOS=android GOARCH=arm GOARM=7 CGO_ENABLED=1`, API 24. Lo script gira su Linux:
da Windows usa WSL.

Il demone è GPLv3: lancialo sempre come programma separato.

## Il primo accesso

1. Apri la sezione Spotify: compare un **QR** con il codice scritto grande.
2. Inquadralo col telefono e approva.

Le credenziali restano in `credentials.json` (`credentials.type: device_auth`).

L'app compare anche nell'**elenco Connect** del telefono: puoi mandarle la
musica da lì.

## Attivare la ricerca

La ricerca vuole un'applicazione registrata a tuo nome.

1. Su **developer.spotify.com/dashboard** → *Create app*. Nome e descrizione
   qualsiasi; fra le API, **Web API**. Come Redirect URI:

       http://127.0.0.1:8888/callback

2. Dentro l'app: *Settings* → **Client ID** e *View client secret*.
3. Dal PC, con Assistente Home in primo piano sul tablet:

       .\tools\chiave-spotify.ps1 -Id <client id> -Segreto <client secret>

Le chiavi finiscono in `spotify.json`, nei file privati dell'app, e restano
valide finché le tieni. Se pensi che siano finite in giro, rigenerale col tasto
« Rotate » del dashboard e rilancia lo script.

## Usare la sezione

- **Il lettore**: copertina, brano, artista e album, barra della posizione da
  toccare per spostarsi, sei comandi.
- **Le playlist**: la prima è sempre **Brani che ti piacciono**. Un tocco apre
  la playlist; toccare un brano fa partire la playlist da lì.
- **Il tasto che rilegge**, in fondo alla riga dell'etichetta: premilo dopo aver
  cambiato una playlist dal telefono o dal computer. Dentro una playlist
  rilegge i suoi brani.
- **La ricerca**: la barra cerca mentre scrivi. A voce: *« casa, cerca io non
  sono io »*. **Chiudi** abbassa la tastiera e tiene i risultati.
- Dai risultati apri artisti e album; un brano suona dentro il suo album.
- In cima: la **nota** torna alla schermata principale, la **freccia** risale di
  un livello, il **triangolo** suona quello che stai guardando, la **X** svuota
  la barra.

Radio e musica si alternano: accenderne una spegne l'altra. La sordina abbassa
anche la musica. La Home mostra cosa suona e i suoi tre tasti comandano la
musica. Uscendo dalla sezione la musica continua.

## Provare dal PC

    # cosa sta suonando adesso
    adb -s <seriale> forward tcp:24879 tcp:24879
    curl http://127.0.0.1:24879/status

    # un comando a voce, senza parlare
    adb -s <seriale> shell "am broadcast -a dev.casa.DI --es frase 'metti la musica'"

    # quanto pesa davvero
    adb -s <seriale> shell "top -n 1 | grep -E 'golibrespot|dev.casa'"

## Per chi modifica il codice

- Playlist, brani, preferiti, artisti: usa `spclient.wg.spotify.com` con il token
  di `POST /token`. Su `api.spotify.com` quel token risponde 429.
- I preferiti stanno su `/collection/collection/{utente}`, in protobuf
  (`Preferiti.collezione()`).
- La ricerca passa da `api.spotify.com/v1/search` con le chiavi di
  `spotify.json` (flusso *client credentials*, un token nuovo a ogni ricerca).
- Su `/v1/artists/{id}/albums` usa `limit` sotto 20.
- Mostra il codice HTTP nel messaggio a schermo.
- La sordina abbassa la musica direttamente: `Sordina` ne tiene più d'una.
- Radio e musica si escludono con `Radio.setMusica` e `Musica.setRadio`.
- La Home legge da `Musica`, perché `MediaSessionManager` salta le sessioni
  dell'app.
- `suUscita()` libera copertine ed elenco aperto; il demone resta acceso.

## Da fare

- **Album e artisti nella griglia principale**: oggi ci si arriva dalla ricerca.
- **Gli eventi**: usa il WebSocket `/events` del demone al posto della domanda
  sullo stato ogni due secondi. È la prima da fare.
- **Una prova d'ascolto automatica**: il segnale indiretto è la posizione del
  brano che avanza con `audio_output_pipe_wait_for_reader: true`.
