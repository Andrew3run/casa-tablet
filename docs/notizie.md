# Le notizie

Le principali d'Italia, quelle della tua zona e, se vuoi, lo sport. Le trovi
**nella Home** (a turno col meteo), **sul riposo** (a turno con l'agenda) e in
una **pagina intera**. Le scegli dal tablet o da Gestione Home.

    dev/casa/Notizie.java       il dato: le richieste, i filoni, la copia su disco
    dev/casa/VelaNotizie.java   la pagina, e il pannello delle scelte
    dev/casa/SezioneHome.java   la superficie del meteo che si alterna
    dev/casa/VelaRiposo.java    l'angolo del riposo che si alterna
    pc/pagine/Notizie.ps1       la pagina « Le notizie » di Gestione Home

## Da dove

Da **Google News, in RSS**, senza chiave.

| filone | cosa si chiede | quando si chiede |
|---|---|---|
| **Italia** | le principali, `news.google.com/rss?hl=it&gl=IT` | sempre, ogni venti minuti |
| **di qui** | una ricerca: `"<posto>" when:7d` | aprendo la pagina |
| **lo sport** | una ricerca: `calcio serie a when:2d` | aprendo la pagina |
| **la squadra** | una ricerca: `"ssc napoli" calcio when:3d` | aprendo la pagina |

- Metti il posto **fra virgolette**.
- Dai a ogni sport **la sua domanda** (« calcio serie a »).
- Cerca la squadra **con « calcio » accanto**.
- Leggi con `XmlPullParser` a flusso e chiudi la connessione appena hai i
  titoli. Su disco (`notizie.json`) salva solo titolo, giornale, quando e
  indirizzo.

Le principali si aggiornano ogni venti minuti con l'app sullo schermo; gli
altri filoni all'apertura della pagina, se hanno più di venti minuti.

Le radici *GTS Root R1* e *R4* stanno in `tablet/assets/radici/`, e
[`Fiducia`](../tablet/src/dev/casa/Fiducia.java) le aggiunge a quelle di
sistema:

    Casa: Fiducia: 148 radici di sistema + 6 nostre

## Nella Home e sul riposo

- **Home**: ogni **dieci secondi** la superficie del meteo mostra per **altri
  dieci** due notizie principali (giornale, da quanto, titolo), poi torna il
  meteo. Il tocco apre quello che si sta guardando; al ritorno nella Home si
  riparte dal meteo.
- **Riposo**: in alto a destra, ogni **venti secondi**, l'agenda lascia il posto
  a **tre notizie principali**, e viceversa.

## La pagina

Dalla tessera **Notizie** della sezione App.

    Notizie  [Italia] [<posto>] [Calcio] [Ssc Napoli]     [ag] [rg] [X]
    +------------------------------+  +------------------------------+
    | IN PRIMO PIANO  aggiornate … |  | E POI                        |
    | la Repubblica  ·  25 min fa  |  | ANSA  ·  1 ora fa            |
    | Il titolo grande, fino a     |  | Un titolo su due righe       |
    | cinque righe                 |  | --------------------------   |
    | ---------------------------- |  | … cinque in tutto            |
    | due titoli di mezzo          |  |                              |
    +------------------------------+  +------------------------------+

- Otto notizie: una grande e le altre piccole.
- Linguette: **Italia** e **di qui**, più **lo sport** e **la squadra** con la
  modalità sport accesa.
- **Tocca un titolo per aprirlo in Chrome**; il tasto indietro riporta
  all'assistente.

### Le scelte, dal tablet

L'ingranaggio in alto apre il pannello delle scelte:

| | |
|---|---|
| **di qui** | il posto delle notizie locali. Vuoto: **la città del meteo**, e cambia con lei |
| **lo sport** | acceso o spento |
| **gli sport** | otto tessere: calcio, tennis, Formula 1, MotoGP, basket, pallavolo, ciclismo, sci |
| **la squadra di calcio** | il nome, o nessuna |

Scrivi con la tastiera dell'app
([`Tastierino`](../tablet/src/dev/casa/Tastierino.java)) e conferma con la
freccia in basso. **Ogni scelta vale subito.** Scegliere uno sport o una squadra
**accende la modalità sport**.

### Le scelte, dal PC

Apri **Le notizie** in Gestione Home: posto, sport (tendina con « spento » in
cima), squadra, e **Manda al tablet**. A destra, **cosa arriva sul tablet**: per
linguetta quanti titoli, da quanto, e il primo.

## La configurazione

In `casa.json`, accanto alla voce e al riposo:

```json
"notizie": { "localita": "", "sport": true, "disciplina": "calcio", "squadra": "ssc napoli" }
```

| | |
|---|---|
| `localita` | il posto delle notizie di qui; vuoto = la città del meteo |
| `sport` | la modalità sport |
| `disciplina` | `calcio`, `tennis`, `f1`, `motogp`, `basket`, `volley`, `ciclismo`, `sci` |
| `squadra` | una squadra di calcio, o vuoto |

Vale subito con `CONFIG --es cosa prendi`; mandare solo `{"notizie": {...}}`
cambia solo le notizie.

## Come si prova

    adb shell am broadcast -a dev.casa.NOTIZIE --es cosa stato      una riga per filone
    adb shell am broadcast -a dev.casa.NOTIZIE --es cosa aggiorna   rilegge tutto adesso
    adb shell am broadcast -a dev.casa.NOTIZIE --es cosa apri       apre la pagina

`stato` scrive nel registro, coi campi separati dalla barra:

    I/Casa: PC notizie scelte||<posto>|true|calcio|ssc napoli
    I/Casa: PC notizie filone|Italia|12|5 min fa|Addio a Emma Bonino: ...
    I/Casa: PC notizie filone|<posto>|12|3 min fa|L'alfabeto del mondo nelle foto ...

## Al rientro

- Titoli con **più di sei ore** (`Notizie.VECCHIE`) restano nascosti: la Home
  resta sul meteo, il riposo sull'agenda, e la pagina scrive « non riesco ad
  aggiornarle: le ultime sono di 3 giorni fa ».
- Notizie e meteo **si rinfrescano subito** (`MainActivity.tornaFresco`,
  `Notizie.riprova`) al primo tocco dopo mezz'ora di assenza, quando l'app torna
  in scena dopo mezz'ora, e tre secondi dopo il ritorno della rete.

## Da fare

- **La voce**: « casa, che notizie ci sono » in
  [`Comandi`](../tablet/src/dev/casa/Comandi.java).
- **Immagini** accanto ai titoli.
