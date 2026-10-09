# Il riposo

Quando nessuno usa il tablet, compare una fotografia a tutto schermo con
l'ora sopra.

    dev/casa/Riposo.java        quando compare e quando se ne va
    dev/casa/VelaRiposo.java    cosa si vede
    dev/casa/Paesaggi.java      le fotografie: dove stanno e da dove arrivano

## Quando compare

Dopo **cinque minuti** (si cambiano) senza tocchi, se l'app è libera. L'app è
occupata con:

- la radio accesa o la musica;
- un timer che scorre;
- una vela aperta (sveglia, meteo, impostazioni);
- il microfono aperto.

Se l'app è occupata, il riposo riprova un minuto dopo.

## Tornare indietro

- **Tocca un punto qualsiasi**: torni alla Home.
- **Tasto indietro**: resti nella sezione di prima.

## Cosa si vede

    +---------------------------------------------------+
    |                                                   |
    |                  la fotografia                    |
    |                                                   |
    |                                                   |
    |  21:40                                            |
    |  martedi 10 settembre                             |
    |  [icona] 18°  ·  poco nuvoloso     Olvera, Cadice |
    |                                    © Marco B./…   |
    +---------------------------------------------------+

In alto a destra, su tessere scure, si alternano ogni venti secondi:

- i prossimi due impegni e le prime tre cose da fare ([agenda.md](agenda.md));
- tre notizie principali ([notizie.md](notizie.md)).

## Di notte

Sopra la foto passa un velo scuro che segue le fasce di `Sfondo.variante`.

La **modalità notte** (`dev/casa/Notte.java`) abbassa anche la luce dello
schermo: nella fascia oraria (di fabbrica 22:00-07:00), trenta secondi dopo la
comparsa del riposo, scende alla luminosità scelta (di fabbrica 15%). Un tocco
la riporta piena.

Regolala nelle Impostazioni dell'assistente, sezione App
(`VelaPreferenze.java`), dove ci sono anche l'interruttore e l'attesa del
riposo. Nel file:

```json
"notte": { "acceso": true, "da": "22:00", "a": "07:00", "luminosita": 15 }
```

## Le fotografie

**Le tue foto**: copiale nella cartella e il riposo mostra solo quelle. Il nome
del file diventa la didascalia (`lago-di-braies.jpg` → « lago di braies »).
Per tornare alle foto di Bing svuota la cartella.

    adb push .\lago-di-braies.jpg /sdcard/Android/data/dev.casa/files/paesaggi/

**Le foto di Bing**: senza foto tue, l'app scarica le foto del giorno di Bing,
ne tiene dieci e cerca le nuove ogni venti ore.

    https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=it-IT

Senza nessuna foto si vede un fondale generato con l'ora sopra.

Regole per il codice:

- Disegna tutte le foto sulle **stesse due bitmap**, tenute per tutta la vita
  del processo: su questo ROM una bitmap già disegnata resta in memoria anche
  dopo `recycle()`.
- Tieni le foto in `getFilesDir()/paesaggi/`, fuori dalla cache.

## Configurare

Dal PC, in `casa.json`:

```json
"riposo": { "acceso": true, "attesa": 5, "foto": 3, "rete": true }
```

| | |
|---|---|
| `acceso` | `false` spegne il riposo |
| `attesa` | minuti di immobilità prima che compaia |
| `foto` | minuti fra una fotografia e l'altra; `0` la tiene ferma |
| `rete` | `false` blocca Bing: restano le foto già scaricate |

Vale subito, con `CONFIG --es cosa prendi`. Per ora Gestione Home manca di una
pagina per il riposo: scrivi la voce a mano
([gestione-dal-pc.md](gestione-dal-pc.md)).

## Provare

    adb shell am broadcast -a dev.casa.RIPOSO --es cosa adesso   subito in scena
    adb shell am broadcast -a dev.casa.RIPOSO --es cosa via      lo toglie
    adb shell am broadcast -a dev.casa.RIPOSO --es cosa foto     va a cercarne di nuove
    adb shell am broadcast -a dev.casa.RIPOSO --es cosa stato    quante ce ne sono e da dove

Lo stato, compreso quello della modalità notte, esce in `logcat -s Casa:I`.
