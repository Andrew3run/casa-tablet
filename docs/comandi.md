# I comandi

L'Assistente Home capisce a **regole**: i comandi di casa sono una ventina e si dicono sempre negli stessi modi (il perché sta in `Comandi.java`). Le regole stanno in una catena: una frase finisce nella prima che la prende.

## Fermare: prima il verbo, poi il bersaglio

| | |
|---|---|
| il verbo | spegni, spegnila, stop, stoppa, ferma, fermati, interrompi, basta, togli, chiudi, smetti, smettila, zitto, taci, silenzio, muto |
| il bersaglio | la radio (o il nome di una stazione), la musica (o canzone, brano, playlist, disco), niente |

- Con un bersaglio si ferma quello.
- Con il solo verbo (« stop », « basta », « zitto ») si ferma quello che fa rumore in quel momento.
- Una **sveglia che suona** viene prima di tutto: « basta » o « spegni la sveglia » la zittiscono.
- I **timer** restano al loro posto.
- Restano alle loro regole le luci, i timer e le sveglie chiamati per nome, e le **routine scritte da chi abita qui** (« Chiudi le tapparelle » contiene "chiudi").

## Parole intere

Le parole delicate si cercano **intere**, con l'apostrofo che conta come lettera: così `l'ora` resta distinta da `all'ora` e `un'ora`, e `basta` da `bastano`.

## Accenti

La frase si appiattisce **una volta sola, all'ingresso**: scrivi le regole come vengono, con o senza accenti. Lo stesso appiattimento passa sui nomi con cui la frase si confronta: stazioni, lampade, routine, playlist, comandi scritti dal PC.

## Luci e volume

- Le luci prendono **alza / abbassa** e fanno un passo di venti punti da dove sono.
- Il volume **si tira indietro** quando la frase nomina una lampada.
- Il volume si muove di **un decimo per volta**, come i tasti della Home.
- Se in quel momento tutto tace, alzare il volume fa il « tac » di sistema, lo stesso dei tasti sul fianco del tablet.

## Come risponde

    « alza il volume »
    -> (il volume si alza, e basta)

La domanda per ogni comando: *chi ha parlato se ne accorge da solo?*

| | quando |
|---|---|
| **a voce** | quando la risposta porta qualcosa di nuovo: l'ora, il nome della stazione che sta partendo, il fatto che era già tutto fermo, un « non ho capito » |
| **solo scritto** sulla riga della Home | quando il risultato si sente: volume, pausa, riprendi, brano successivo, tutto quello che si spegne |

Anche le risposte mute restano scritte a schermo e finiscono nel registro (`fa: Volume 60%`): è lì che le legge il PC e le controlla la batteria di prove.

## L'ora

A mezzanotte dice « mezzanotte », all'una « l'una », e la mezza la dice « mezza ».

## Le note vengono prima di tutto

Con la [To-Do List](agenda.md), la prima regola della catena è quella delle note: « prendi nota di spegnere la radio » finisce in lista, e la radio resta accesa.

## Provare i comandi

    tools\prova-comandi.ps1                 la batteria che non lascia strascichi
    tools\prova-comandi.ps1 -ConLaRadio     anche quelle che accendono davvero
    tools\prova-comandi.ps1 -Frase 'stop'   una sola

Passa da `dev.casa.DI`, che salta il riconoscitore vocale ed entra dritto in `Comandi`: prova la stessa catena di quando qualcuno parla. Per ogni frase dice cosa si aspettava e cosa è uscito.

La batteria di serie lascia il tablet com'era: le frasi che spengono si provano a impianto già spento (« la radio era già spenta » basta a dire che la frase è finita nella regola giusta) e il volume si alza e si riabbassa.

Lanciala per prima dopo ogni installazione.

## Aggiungere una regola

- **Vince la regola scritta prima**: metti sopra la più specifica.
- Chiediti **« quali frasi destinate ad altre regole potrebbe prendere? »**.
