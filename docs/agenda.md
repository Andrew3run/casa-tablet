# Calendario e To-Do List

Due tessere nella sezione App, ognuna con la sua pagina intera.

|  | da dove viene | si scrive |
|---|---|---|
| **Calendario** | dal calendario Google dell'account del tablet | si legge e basta |
| **To-Do List** | da qui | col tastierino o **dettata** |

    dev/casa/VelaCalendario.java   il mese e il giorno scelto
    dev/casa/VelaToDo.java         la lista
    dev/casa/Calendario.java       la lettura del provider di sistema
    dev/casa/Appunti.java          le note, su disco

## Il calendario

Gli impegni li sincronizza Android; Assistente Home li legge dal provider di
sistema. Un impegno aggiunto dal telefono compare qui in pochi secondi.

Servono tre cose:

| | |
|---|---|
| `com.android.providers.calendar` | il magazzino |
| `com.google.android.syncadapters.calendar` | chi lo riempie |
| un account Google | sul tablet |

### Attivarlo

1. Lancia:

       adb shell am broadcast -a dev.casa.AGENDA --es cosa prepara   rimette e ricontrolla

   L'app rimette i due pacchetti da sola, da device owner (`enableSystemApp`).
2. **Riavvia il tablet una volta**: i provider entrano nell'elenco al riavvio.
3. Controlla:

       adb shell am broadcast -a dev.casa.AGENDA --es cosa stato     quanti impegni, e se manca qualcosa

Se manca uno dei tre pezzi, la pagina scrive **quale**.

## Il mese

    Settembre 2026                          [OGGI] [<] [>]  [X]
    +--------------------------------+  +---------------------+
    |  L   M   M   G   V   S   D     |  | Oggi, giovedì 10    |
    |  1   2   3   4   5   6   7     |  |                     |
    |  8   9  (10) 11  12  13  14    |  | • Dentista          |
    | 15  16  17  18  19  20  21     |  |   10:00 - 11:00     |
    +--------------------------------+  +---------------------+

Oggi è **pieno**, il giorno scelto è **cerchiato**, un puntino segna i giorni con
impegni. Il pallino accanto a ogni impegno ha il colore del suo calendario.

## La To-Do List

- Un tocco spunta, un altro riapre.
- Le spuntate scendono in fondo e **dopo tre giorni se ne vanno da sole**. Il
  cestino in alto le toglie subito.
- **SCRIVI** apre il tastierino.
- **DETTA** apre il microfono e scrive in lista quello che sente, comandi
  compresi.

### La spesa

In cima ci sono due linguette, **Da fare** e **Spesa**, ognuna col numero delle
righe aperte. Le due liste stanno nello stesso file, `note.json`.

- Nella spesa una frase detta diventa più righe: « latte, uova e il pane »
  diventa Latte, Uova, Pane.
- Una cosa già in lista torna da comprare, senza doppioni.
- Il riposo e « che ho da fare » mostrano solo le cose da fare.

Dal telefono vedi e tocchi tutte e due le liste: vedi
[telefono.md](telefono.md).

## A voce

    « prendi nota di chiamare l'idraulico » -> Preso nota.
    « ricordami di chiamare l'idraulico »   -> Segnato.
    « che ho da fare »                      -> Hai 3 cose da fare. …
    « ho fatto la spesa »                   -> Fatto.        (la cerca e la spunta)
    « che impegni ho »                      -> oggi alle 19 e 15: dentista. Poi…

    « aggiungi latte e uova alla spesa »    -> Aggiunto alla spesa.   (due righe)
    « metti il pane nella lista della spesa »
    « compra il detersivo »
    « cosa devo comprare »                  -> Da comprare: latte, uova e pane.
    « ho comprato il latte »                -> Fatto.        (lo spunta nella spesa)

- Per la spesa di' « alla spesa », « nella lista della spesa », « da comprare »
  o « compra ». « ricordami di fare la spesa » va fra le cose da fare.
- « ricordami » con una durata avvia un timer: « ricordami fra dieci minuti ».
- Leggendo la lista, l'assistente dice quattro righe al massimo e poi il totale.

## Sul riposo

In alto a destra sulla schermata di riposo compaiono le prossime due cose in
agenda e le prime tre da fare, a turno con le notizie: vedi
[notizie.md](notizie.md).

## Per chi modifica il codice

- Disegna il mese su sei righe fisse, con quarantadue `RectF` allocati una volta.
- Nella catena dei comandi controlla la spesa prima delle note, e le note prima
  di tutto il resto.
- Dai a ogni riga un numero fisso e mai riusato: il telefono spunta per numero.
- Fai entrare le tessere del riposo solo sui fotogrammi della dissolvenza fra due
  fotografie.
