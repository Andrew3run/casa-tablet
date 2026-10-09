# L'Assistente Home sul telefono

Il telecomando del tablet, e lo specchio della sveglia. Package `dev.casa.telefono`, cartella `telefono/`, codice tutto suo: parla con il tablet solo attraverso la rete.

## Le schede

| Scheda | |
|---|---|
| **Casa** | le lampade come tessere della loro tinta (tocco: accendi/spegni; pressione lunga: luminosità a meno/numero/più, passi del dieci), le routine, la radio con il tasto per spegnerla, i timer in corso col conto alla rovescia, i timer da 1, 3, 5, 10, 15, 30 minuti e uno su misura (« 11 » minuti, « 2:30 » minuti e secondi, o a voce: « un'ora e mezza » la capisce il tablet), e una casella per scrivere o dettare una frase all'assistente, che la esegue come se l'avessi detta |
| **Sveglie** | le sveglie del tablet: accendi, spegni, cambia ora e giorni, aggiungi, togli. In cima « Segui la sveglia del telefono » |
| **Liste** | la To-Do List e la spesa del tablet: si aggiunge di fila, un tocco spunta, una pressione lunga toglie, e « togli le spuntate » svuota il carrello |
| **Tablet** | trovarlo, abbinarlo, dissociarlo |

**Segui la sveglia del telefono**: la prossima sveglia del telefono, di qualunque app (`AlarmManager.getNextAlarmClock()`), arriva al tablet, che ne tiene una copia e suona anche lui. Parte quando cambia e ogni mezz'ora col Wi-Fi: una sveglia cambiata fuori casa arriva al tablet appena rientri.

## Collegamento

- TCP **8776** sul tablet, HTTP dentro la rete di casa. La 8775 resta del PC (`adb reverse`).
- Il tablet si annuncia con NSD come `_casa._tcp`; se la ricerca fallisce, scrivi l'indirizzo a mano.
- Ogni richiesta è firmata con HMAC-SHA256 (chiave avuta all'abbinamento, più l'ora); ogni firma vale una volta.

Il protocollo completo è in [../docs/telefono.md](../docs/telefono.md).

## Abbinare

1. Scheda **Tablet** → « Cerca » (o scrivi l'indirizzo a mano).
2. « Chiedi il codice »: sul tablet compare un codice di sei cifre, valido tre minuti.
3. Scrivilo nel telefono → « Abbina ».

## Compilare e installare

    telefono\build.ps1              compila (build\CasaTelefono.apk)
    telefono\build.ps1 -Install     installa sul telefono collegato
    telefono\build.ps1 -Install -Serial <seriale>

Si compila contro **android-36** (targetSdk 36, minSdk 24). Il telefono giusto lo sceglie `Get-TelefonoDelProgetto` in `tools\dispositivo.ps1`, che esclude i tablet.
