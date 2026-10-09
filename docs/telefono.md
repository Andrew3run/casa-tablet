# Il telefono come telecomando

L'app del telefono ha il package `dev.casa.telefono` e sta nella cartella [`telefono/`](../telefono/README.md). Con il telefono puoi:

- comandare **luci e routine** da lontano, con lo stato vero delle lampade;
- gestire **le sveglie del tablet** (vedere, aggiungere, spegnere, togliere) e far **seguire al tablet la sveglia del telefono**;
- **scrivere una frase all'assistente**: tutto quello che capisce a voce;
- usare **le liste**: la To-Do List e la lista della spesa, da vedere, scrivere e spuntare.

Il package è diverso da `dev.casa` perché due APK con lo stesso package si sovrascrivono.

## Collegamento

- Il tablet ascolta sulla **8776**. La 8775 resta del PC (`adb reverse`, la tiene aperta adbd).
- Il tablet si annuncia con NSD come `_casa._tcp`, nome « Casa ».
- Se l'annuncio manca (certi router lo bloccano), scrivi l'indirizzo a mano. L'app ricorda l'ultimo che ha funzionato.

Lato tablet è `Telefono.java`. Esegui sul thread dell'interfaccia tutto quello che tocca lampade o sveglie.

## Abbinare il telefono

1. Sul telefono, scheda **Tablet**: « chiedi il codice ».
2. Il tablet torna alla Home e scrive nella riga di stato **codice per Pixel 8: 482 913**. Vale tre minuti.
3. Scrivi il codice sul telefono: compare « Pixel 8 abbinato ».

- Il codice compare solo sullo schermo del tablet.
- Dopo cinque errori il codice scade.
- Una seconda richiesta con un codice ancora valido rimostra lo stesso.

Il telefono riceve **una chiave sua** (32 byte), salvata dal tablet in `telefoni.json`. Il tablet ricorda otto telefoni; il nono prende il posto del più vecchio.

## La firma

Ogni richiesta, tranne le due dell'abbinamento, porta tre intestazioni:

    X-Casa-Telefono: t1a2b3c4
    X-Casa-Ora:      1757851200000
    X-Casa-Firma:    hex( HMAC-SHA256( chiave, METODO \n percorso \n ora \n corpo ) )

- la chiave viaggia solo durante l'abbinamento;
- l'ora dev'essere entro **cinque minuti** da quella del tablet;
- ogni firma **vale una volta**: il tablet ricorda quelle già viste.

Firma assente o sbagliata: **401**. Un 401 da `/stato` vuol dire « qui c'è il tablet, ma ti manca l'abbinamento ».
## Le richieste

HTTP/1.1, una per connessione, corpo JSON. Per provarle usa `curl`; per le firmate serve la chiave in `telefoni.json` sul tablet.

| | | |
|---|---|---|
| `POST /abbina/chiedi` | `{"nome":"Pixel 8"}` | mostra il codice sul tablet |
| `POST /abbina` | `{"nome":"Pixel 8","codice":"482913"}` | `{"telefono","chiave","tablet"}` |
| `GET /stato` | | luci, routine, sveglie, timer, radio, prossima sveglia |
| `POST /frase` | `{"testo":"metti rai radio 1"}` | come se l'avessi detto |
| `POST /luce` | `{"id":"bf...","azione":"on\|off\|inverti\|luce","valore":40}` | |
| `POST /routine` | `{"nome":"Cinema"}` | |
| `POST /timer` | `{"secondi":600}` | |
| `POST /sveglia/aggiungi` | `{"ora":7,"minuto":30,"giorni":62}` | `{"id":12}` |
| `POST /sveglia/togli` | `{"id":12}` | |
| `POST /sveglia/accendi` | `{"id":12,"attiva":false}` | |
| `POST /sveglia/regola` | `{"id":12,"ora":7,"minuto":45,"giorni":62}` | |
| `POST /sveglia/telefono` | `{"quando":1757913600000}` | la sveglia specchio |
| `POST /nota/aggiungi` | `{"lista":"cose\|spesa","testo":"latte"}` | `{"id":31}`; se c'è già torna da fare, stesso id |
| `POST /nota/inverti` | `{"id":31}` | spunta, o rimette da fare |
| `POST /nota/togli` | `{"id":31}` | |
| `POST /nota/pulisci` | `{"lista":"spesa"}` | via le spuntate, `{"tolte":3}` |
| `POST /timer/ferma` | `{"id":4}` | |
| `POST /volume` | `{"passo":1}` o `-1` | un decimo, come i tasti della Home; `{"volume":40}` |
| `GET /radio` | | stazioni, corrente, accesa, apertura, errore, volume |
| `GET /radio/logo/<chiave>` | | `{"png":"<base64>"}`, il PNG com'è nell'APK |
| `POST /radio/accendi` | `{"chiave":"deejay"}` (vuota: l'ultima) | |
| `POST /radio/spegni` · `/successiva` · `/precedente` | | |
| `GET /musica` | | brano, artista, copertina, durata, posizione, mischia, pronta, cerca |
| `GET /musica/playlist` | | le playlist della sezione, « Brani che ti piacciono » per prima |
| `POST /musica/brani` | `{"uri","elencabile"}` | fino a 45 s: un metadato per brano |
| `POST /musica/cerca` | `{"testo":"placebo"}` | vuole la chiave di Spotify (docs/musica.md) |
| `POST /musica/apri` | `{"uri","tipo"}` | artista, discografia, album |
| `POST /musica/pausa` · `/successivo` · `/precedente` · `/mischia` · `/vai` · `/suona` | `{"uri","brano"}` per suona, `{"ms"}` per vai | suona sempre il tablet |

- `cerca`, `apri` e `brani` passano da Internet: partono sul thread dell'interfaccia e la porta le aspetta con un tetto. Il telefono le manda su una coda sua, così le luci rispondono subito.
- `/musica/brani` lascia intatta la playlist aperta sul tablet (salta `Preferiti.apri`).
- `/stato` porta anche le due liste, nell'ordine del tablet: `"liste": {"cose":[{"id","testo","fatta"}], "spesa":[...]}`. Gli id delle righe sono unici per sempre. Dal telefono una riga si scrive intera: « sale e pepe » resta una riga sola.
- `giorni` è quello di `Orologio`: bit 0 domenica ... bit 6 sabato, zero « una volta sola ».
- Le lampade rispondono subito `ok` e agiscono dopo (una Tuya può metterci secondi): il telefono rilegge `/stato` un attimo dopo.
- `/frase` fa **parlare il tablet**, come `dev.casa.DI`: la risposta si sente in casa. Tienilo a mente prima di scrivere « buonanotte » dall'ufficio.

## La sveglia che segue il telefono

Il telefono legge la sua **prossima sveglia di sistema** (`AlarmManager.getNextAlarmClock`, vale per qualunque app orologio) e la manda al tablet quando cambia (`NEXT_ALARM_CLOCK_CHANGED`) e ogni mezz'ora sul Wi-Fi.

Sul tablet diventa **una sveglia specchio**, una per telefono:

- suona in quell'**istante** preciso (per questo `Orologio.Sveglia` ha un campo `il`);
- nella sezione Orologio porta la scritta **« dal telefono »**;
- dopo aver suonato si spegne e aspetta la prossima dal telefono;
- con `quando` a zero lo specchio sparisce;
- la stessa sveglia ricevuta due volte resta com'è;
- **se la tocchi a mano sul tablet diventa del tablet**: spostarla, cambiarle i giorni o riaccenderla la stacca dal telefono, e la sincronizzazione ne crea una nuova.

Una sveglia spenta sul telefono fuori casa arriva al tablet solo quando il telefono torna sul Wi-Fi.

## Nel registro

    adb logcat -s Casa:I | findstr telefono

    I/Casa: telefono: in ascolto sulla 8776, 1 abbinati
    I/Casa: telefono: annunciata in rete come Casa
    I/Casa: telefono: Pixel 8 chiede di abbinarsi
    I/Casa: telefono: Pixel 8 abbinato come t1a2b3c4
    I/Casa: telefono: sveglia specchio alle 07:00
    W/Casa: telefono: firma fuori tempo di 412 s: orologi storti?
