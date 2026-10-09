# Timer e sveglie

## Usare la sezione

    ┌──────────────────────────────────────────────────────────────┐
    │ [TIMER] [SVEGLIE]           prossima domani alle 07:00        │
    │                                                               │
    │           ▼                 ┌───────────────────────────┐     │
    │      ╭─────────╮            │▎07:00                 ◉   │     │
    │    05│  06 07 08│09         │  da lunedi a venerdi      │     │
    │   04 │ ╭───────╮ │10        └───────────────────────────┘     │
    │   03 │ │ 07:00 │ │11        ┌───────────────────────────┐     │
    │   02 │ │       │ │12        │ 08:30                 ○   │     │
    │      ╰─┤ 45 50 ├─╯          │  spenta                   │     │
    │        ╰───────╯            └───────────────────────────┘     │
    │                                                               │
    │  [D][L][M][M][G][V][S]                                        │
    │  [      FATTO      ][🗑]                                       │
    └──────────────────────────────────────────────────────────────┘

- **Scegli la scheda** in alto: Timer o Sveglie.
- **Gira la ghiera** col dito fino al valore che vuoi.
- **Premi il pulsante grande**: `AVVIA`, `AGGIUNGI` o `FATTO`.
- **Per modificare una sveglia** toccane la riga; toccala di nuovo per
  lasciarla.
- **Il pallino a destra** accende e spegne la sveglia; nei timer la ferma.
- **Il cestino** compare mentre modifichi una sveglia.
- **Le durate pronte** (1, 3, 5, 10, 15, 30 minuti) stanno a destra.

| | anello esterno | anello interno |
|---|---|---|
| **timer** | minuti, 60 scatti, un numero ogni 5 | ore, 0–5 |
| **sveglie** | ore, 0–23 | minuti, a passi di 5 |

## A voce

    casa, metti un timer di 5 minuti      (anche "mezz'ora", "dieci secondi")
    casa, annulla il timer
    casa, sveglia alle 7
    casa, svegliami alle 6 e mezza
    casa, sveglia alle 7:30
    casa, togli la sveglia

A voce la sveglia è « una volta sola »; i giorni si scelgono nella sezione.

## Quando suona

Premi **« Basta »** per spegnerla o **« ancora cinque minuti »** per
rimandarla (solo sveglie). La sveglia parte piano e sale al pieno in mezzo
minuto; il timer suona a metà volume e dice « Il tempo è finito ». Dopo tre
minuti smettono da soli.

## Regole per il codice

- Fai suonare con `AlarmManager`: `setAlarmClock` per le sveglie,
  `setExactAndAllowWhileIdle` per i timer.
- Riprogramma su `BOOT_COMPLETED` e `MY_PACKAGE_REPLACED`; ricalcola su
  `TIME_SET` e `TIMEZONE_CHANGED`.
- Usa un solo Orologio per processo, `Orologio.di(context)`; in `onDestroy`
  chiama solo `staccati`.
- Scrivi `orologio.json` in `getFilesDir()` con `Archivio`, in modo atomico.
- Salva e riprogramma al rilascio della ghiera.
- Ridisegna al secondo solo con la sezione in scena; `suUscita` lo ferma.
- Fai la rampa sul `MediaPlayer` e suona su `STREAM_ALARM`.
- Nelle regole a voce metti le più specifiche prima (« togli la sveglia »
  prima di « sveglia »).

## Impostare il fuso orario

Il tablet esce di fabbrica su `Asia/Shanghai`. Impostalo una volta sola, prima
di fidarti della prima sveglia:

**Assistente Home -> App -> Impostazioni -> Data e ora -> Fuso orario**

La tessera « Impostazioni » rende visibili le Impostazioni di Android, e
l'assistente le nasconde di nuovo quando torni indietro. Da adb il fuso resta
bloccato.

## Da fare

- Una sveglia che accende le luci.
- La scelta della suoneria.
