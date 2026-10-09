# Microfono e parola di attivazione

## Parlare all'assistente

Premi la pastiglia in fondo alla scheda dell'ora. Mentre ascolta dice « ti ascolto » e mostra « annulla » per chiudere subito.

La parola di attivazione « Marvin » è spenta.

## Riaccendere « Marvin »

1. Metti a `true` l'interruttore `MainActivity.PAROLA_DI_ATTIVAZIONE` e reinstalla.
2. Apri il pannello con l'**ingranaggio accanto al microfono** nella Home.
3. Usa **prova**: mostra il punteggio a ogni frase e lascia l'assistente addormentato. Di' la parola da vari punti, anche con la radio accesa.
4. Regola `soglia` e `difila` (sotto) finché aggancia senza falsi risvegli.
5. **Accendi l'ascolto.**

Il modello è in `assets/parola/marvin.rete`. Se manca, l'assistente usa il confronto con le registrazioni di casa (« Hey Home »): segui [Tarare le registrazioni](#tarare-le-registrazioni).

## Tarare le registrazioni

Le registrazioni stanno qui:

    /sdcard/Android/data/dev.casa/files/parola/si/    « Hey Home »
    /sdcard/Android/data/dev.casa/files/parola/no/    tutto il resto

Per copiarle sul PC:

    adb pull /sdcard/Android/data/dev.casa/files/parola

Registra sempre dal pannello del tablet, nella stanza dove lo userai.

1. **Registra sei o otto « Hey Home »** da dove stai di solito, uno o due da più lontano.
2. **Registra due o tre « rumore »**: la cucina normale, e almeno uno con radio o televisione accesa e qualcuno che parla.
3. **TARA.** Leggi quanti « si » presi, quanti falsi, che margine.
4. **PROVA.** Di' la parola da vari punti e guarda il punteggio. Aggiusta la soglia se serve.
5. **Accendi l'ascolto.**

Con un margine sotto 0,2, registra altri campioni, soprattutto « rumore » con del parlato.

## Comandi da adb (e dal PC)

    adb shell am broadcast -a dev.casa.PAROLA --es cosa stato
    adb shell am broadcast -a dev.casa.PAROLA --es cosa accendi
    adb shell am broadcast -a dev.casa.PAROLA --es cosa spegni
    adb shell am broadcast -a dev.casa.PAROLA --es cosa ricarica
    adb shell am broadcast -a dev.casa.PAROLA --es cosa tara
    adb shell am broadcast -a dev.casa.PAROLA --es cosa pannello
    adb shell am broadcast -a dev.casa.PAROLA --es cosa prova    --ez valore true
    adb shell am broadcast -a dev.casa.PAROLA --es cosa eco      --ez valore true
    adb shell am broadcast -a dev.casa.PAROLA --es cosa soglia   --ef valore 0.90
    adb shell am broadcast -a dev.casa.PAROLA --es cosa impronte --ef valore 2.60
    adb shell am broadcast -a dev.casa.PAROLA --es cosa difila   --ei valore 1

- `soglia`: somiglianza secondo la rete, da 0 a 1, più alta più esigente.
- `impronte`: distanza dalle registrazioni di casa, più bassa più esigente.
- `difila`: quante finestre consecutive servono per l'aggancio.
- `eco`: tienila spenta, com'è di fabbrica.
- Manda i numeri **col punto** (`0.90`). A schermo compaiono con la virgola.

Le risposte arrivano nel registro sotto l'etichetta `Casa`. Per vederle apri il tag e riavvia l'app:

    adb shell setprop log.tag.Casa VERBOSE
    adb shell am force-stop dev.casa
    adb shell am start -n dev.casa/.MainActivity

`pc/parola/dalvivo.ps1` lo fa da solo. Tutto questo c'è già, più comodo, in [Gestione Home](gestione-dal-pc.md).

## Regole per chi tocca il codice

- Appena la parola aggancia, spegni l'orecchio e **poi** apri il riconoscitore: il microfono è di uno solo. `Orecchio.spegni()` aspetta che il thread lo molli.
- Per sapere quando riprendere il microfono usa `Voce.Ascoltatore.suFine()`.
- Mentre l'assistente parla, l'orecchio sospende il confronto (`Voce.Ascoltatore.suBocca`).
- Con la rete accendi sempre `Orecchio.setFinestraIntera(true)`: con la finestra accorciata `Rete.punteggio` restituisce 0 senza errori.
- Il ritaglio delle registrazioni c'è anche sul PC in `Audio.cs`: tieni le due regole uguali.
- Dopo ogni modifica a MFCC, rete o confronto lancia le prove di parità: `pc/parola/parita.ps1`, `paritarete.ps1`, `paritadtw.ps1`.
- Per riallenare la rete gli script sono in `pc/parola/`; misura sempre su un flusso continuo di parlato con `pc/parola/difila.py`.
