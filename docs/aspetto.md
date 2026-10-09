# L'aspetto

Le regole dell'interfaccia di Assistente Home. Tre classi la reggono:
[`Misure`](../tablet/src/dev/casa/Misure.java) (la scala),
[`Icone`](../tablet/src/dev/casa/Icone.java) (i segni) e
[`Anima`](../tablet/src/dev/casa/Anima.java) (il movimento).

## Lo stile

- **Schede**: pannello grigio `#1C1C1E` quasi pieno (`Tinte.MATERIALE`,
  copertura `0xE6`) sopra lo sfondo sfocato, con un capello di bordo. Lo stesso
  grigio in tutte le sezioni.
- **Colore negli accenti**: blu `0A84FF`, verde `30D158`, arancio `FF9F0A`...
  Il tasto principale è una capsula piena (il microfono), i comandi sono tondi
  grigi, il tasto di mezzo è un tondo bianco pieno.
- **Lampade**: spenta è una tessera grigia, accesa diventa chiara col testo
  scuro; l'icona sta in un tondo del colore della lampada.
- **Meteo**: la scheda prende il colore del cielo, testi bianchi. Il gradiente
  si rifà solo quando cambia il cielo.
- **Controlli a segmenti e interruttori** per le scelte.
- **Ghiera dell'Orologio**: in una scheda, due fasce piene (una per corona) e
  una banda di selezione in cima. Il timer che corre è un anello colorato su una
  traccia grigia. Il numero al centro si stringe da solo per entrare nel foro.

Per provarlo sul PC lancia `tools\anteprima.ps1` (emulatore « Casa_E960 »).
Spotify funziona col tuo account. Solo sull'emulatore, per mostrare le lampade:

    adb shell am broadcast -a dev.casa.VETRINA --es cosa luci

## La scala: sei corpi e cinque spazi

Prendi sempre i corpi da `Misure`: frazioni dell'altezza, con un tetto in dp.

| | su 800 px | cos'è |
|---|---|---|
| `micro` | 18 | etichette maiuscole, unità di misura |
| `nota` | 22 | la seconda riga: lo stato di una lampada, l'artista |
| `corpo` | 27 | il testo normale, le scritte nei pulsanti |
| `voce` | 34 | i nomi. **Il gradino più piccolo che si legge da un metro** |
| `titolo` | 45 | intestazioni, e i numeri che contano |
| `cifra` | 152 | l'ora nella Home, e basta (che la disegna a 0,87) |

Gli spazi vanno da `s1` (6 px, fra due righe della stessa cosa) a `s5` (40 px,
fra due blocchi su cose diverse).

Sotto `voce` metti solo ciò che si guarda mentre si tocca.

## La Home

A sinistra **una scheda sola alta tutto lo schermo** (ora, data, meteo, il
tasto per parlare), a destra due schede impilate.

### Tre primitive in `Vetro`

Usa la primitiva giusta in [`Vetro`](../tablet/src/dev/casa/Vetro.java):

| | com'è fatta | cos'è |
|---|---|---|
| `pannello` | vetro sfocato + velo + filo di luce sul bordo | una **scheda**: raccoglie, ha un bordo |
| `controllo` | bianco 0x16 piatto + velo | un **comando**: sporge, quindi è più chiaro |
| `incavo` | nero 0x30 piatto, nessun bordo | una **superficie dentro un pannello**: rientra, quindi è più scura |

Il vetro sfocato va solo sui pannelli.

### Regole

- **Colore solo per ciò che è in corso**: il tasto « avvia » a radio spenta è
  neutro, le scorciatoie si colorano solo mentre la routine gira, e la tinta
  della routine sta sempre nel suo segno a sinistra.
- **Fascia riservata** di due righe sopra il microfono: la frase detta a voce, o
  un esempio di comando che l'assistente capisce alla lettera.
- **Timer** all'estremità destra della riga della data: la parte alta della
  schermata resta ferma.
- **Ogni cosa una volta sola**: la prossima sveglia sta in « in casa », con le
  luci.
- **L'ora**: peso medio, lettere strette del tre per cento, nell'angolo in alto
  a sinistra, corpo di un ottavo sotto `cifra`, più aria sopra che di lato.
- **Etichette** in frase normale (« In casa »), corpo `nota`, peso medio,
  grigio tenue.

## La scheda di quel che suona

- Due fasce: **chi sta suonando** (copertina a sinistra; nome, nota e colonne
  del suono a destra) e **quello che si preme**.
- Volume: **meno, numero, più**, a passi del **dieci per cento**. Se il volume
  cambia dai tasti sul fianco del tablet, torna il numero vero.
- I tre tasti sono appoggiati al filo sinistro della scheda, il volume al filo
  destro.

### Le colonne del suono

[`Livelli`](../tablet/src/dev/casa/Livelli.java) mostra i livelli veri dello
spettro: ventiquattro fili sottili, a riposo una riga tratteggiata, il colore
che cresce con l'altezza. Aggancia il visualizzatore alla sessione del
MediaPlayer o dell'AudioTrack di chi suona (la sessione zero è chiusa su questo
tablet), con `MODIFY_AUDIO_SETTINGS`. Mentre suona un'altra app le colonne
restano a riposo.

## Le icone

Usa i **Material Symbols** di Google (stile *rounded*, peso 400), scaricati da
`fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/<nome>/<fill>/24px.svg`,
più il **marchio Spotify ufficiale** da Simple Icons. In `Icone.java` copia le
stringhe `d="…"` **identiche** ai file scaricati. Ogni stringa diventa un `Path`
alla prima visualizzazione; la tinta è un colore sul Paint.

### Le tre trappole

1. **L'analizzatore SVG.** In `423.5-103.5` ci sono due numeri: il meno fa da
   separatore. Una lettera omessa ripete il comando di prima, ma dopo una `M`
   diventa `L`.
2. **Il Canvas ingrandito.** `c.scale(lato, lato)` su un percorso unitario dà
   una macchia. Porta il percorso alla grandezza vera con
   `Path.transform(matrice, …)` e sposta il Canvas solo con una traslazione.
3. **Un percorso modificato è un percorso nuovo.** Riscrivere un `Path` a ogni
   fotogramma manda il RenderThread al cento per cento dopo qualche ora, e
   i servizi di Google vanno in ANR.

Regola: **costruisci un `Path` per coppia (icona, misura) una volta,
nell'origine, e muovi il Canvas con una traslazione, mai con una scala.** Vale
anche per nuvole, fulmine, freccia « indietro » di Spotify e cuneo
dell'Orologio.

Per diagnosticare: `adb bugreport` (pila di tutti i thread, anche il
RenderThread) e `atrace -t 6 gfx view` (durata di ogni `DrawFrame`).

## Il movimento

In [`Anima`](../tablet/src/dev/casa/Anima.java) il tempo è **un numero solo**,
l'istante d'inizio: chi disegna chiede « a che punto siamo » (fra zero e uno)
e, finché è sotto uno, chiede un altro fotogramma con
`postInvalidateOnAnimation`. Evita i `ValueAnimator`.

Tre curve: `posa` per quel che arriva, `dolce` per quel che si sposta, `molla`
(rimbalzo piccolo) per quel che compare.

Le tessere entrano **una dopo l'altra**, sfasate di 38 ms (fino a quattordici),
scorrendo di quaranta pixel. Anima la posizione, mai l'opacità di un vetro.

Il cielo del meteo è l'eccezione: vedi [meteo.md](meteo.md).

## Il vuoto va dentro un bordo

Quando il contenuto occupa poco spazio, mettilo in un pannello con un titolo e
tieni le tessere alla loro misura: la sezione Luci e routine ha due pannelli
affiancati (`SCENE` e `LAMPADE`), l'Orologio due impilati (`AVVIO RAPIDO` e
l'elenco), le App uno solo. Le scritte del vuoto vanno piccole, con l'icona
smorta di quel che ci andrebbe.

Metti il titolo di un blocco **dentro** il suo pannello e fai cominciare il
pannello successivo dove finisce il precedente.

## Il fuso orario

Il tablet nasce con `persist.sys.timezone = Asia/Shanghai`.
`MainActivity.rimettiIlFuso()` rimette il fuso giusto a ogni avvio
(`SET_TIME_ZONE`, concesso all'installazione su API 24).
