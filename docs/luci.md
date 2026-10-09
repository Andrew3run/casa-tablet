# Le luci

Le lampade sono Tuya, quelle dell'app **Smart Life**. Assistente Home le comanda
in locale, sulla rete di casa.

## Le lampade

Ogni lampada ha un nome, un indirizzo, un identificativo, una versione di
protocollo e una chiave locale. Esempio:

| Nome | Indirizzo | Identificativo | Protocollo | Chiave |
|---|---|---|---|---|
| Camera | `192.168.1.<n>` | `<identificativo>` | 3.3 | c'è |
| *(una presa)* | `192.168.1.<n>` | `<identificativo>` | 3.4 | **manca** |

Una lampada entra nell'elenco quando ha la sua chiave locale: una lampada Tuya
risponde solo a chi ce l'ha. L'elenco di partenza sta in `Luci.DI_FABBRICA`.

## Leggere le chiavi

La chiave nasce quando accoppi la lampada con Smart Life. Se riaccoppi una
lampada da zero, rileggila dalla pagina **Le luci** di
[Gestione Home](gestione-dal-pc.md):

1. In Smart Life: **Io → Impostazioni → Account e sicurezza → Codice utente**.
   Copialo nel campo *Codice utente*.
2. Premi **« Rileva chiavi »**: compare un codice QR.
3. In Smart Life tocca l'icona della scansione in alto a destra, inquadra e
   conferma.
4. Le chiavi compaiono nelle righe accanto ai nomi.

La pagina poi manda l'elenco al tablet. Internet serve solo per questi passi.

Per aggiungere una lampada nuova: premi « Ascolta », che la sente annunciarsi,
poi « Rileva chiavi ». Tieni giusto il numero di protocollo: lo riporta la pagina
dall'annuncio.

## La sezione Luci e routine

- **Griglia**: un tocco accende o spegne.
- **Dettaglio**, dall'angolo `[≡]`: sette livelli di luce, dodici tinte, tre
  bianchi.
- La **freccia che gira** in alto a destra rilegge lo stato.
- Mentre la sezione è sullo schermo, lo stato si rilegge ogni cinque secondi:
  vedi subito chi ha acceso da Smart Life o dal muro.

### Le routine

| Routine | Cosa fa |
|---|---|
| Buonanotte | spegne tutto |
| Cinema | spegne la Camera, mette il Comodino sull'arancione al 20% |
| Tutte accese | accende tutto |

Un passo può essere anche una frase per l'assistente (« metti rai radio 1 »,
« volume al 30 », « apri netflix ») o un'attesa di qualche secondo. Le routine si
scrivono da [Gestione Home](gestione-dal-pc.md#routine).

Dai a ogni routine un nome che nessun altro comando usa: per esempio *film* apre
Netflix, *tutte* compare in « spegni tutte le luci ».

## L'elenco sul tablet

Elenco e routine stanno in:

    /data/data/dev.casa/files/casa.json

Li scrive [Gestione Home](gestione-dal-pc.md). Il file sostituisce l'elenco
intero: una lampada tolta dal PC sparisce dal tablet. Se il file è illeggibile,
l'app riparte da `Luci.DI_FABBRICA` e `Luci.DI_FABBRICA_ROUTINE`.

## A voce

    casa, accendi la luce                 (tutte)
    casa, spegni le luci
    casa, accendi il comodino
    casa, spegni la camera
    casa, comodino al 30 per cento
    casa, buonanotte
    casa, cinema

## Le lampade nella Home

La scheda « IN CASA » elenca le lampade. Tocca una riga per accendere o spegnere;
tocca la scheda fuori dalle righe per aprire la sezione Luci e routine.
Luminosità, colore e temperatura si regolano nella sezione.

## Se qualcosa va storto

| sulla tessera | cosa è successo |
|---|---|
| connessione non riuscita - spenta al muro? | host irraggiungibile |
| non risponde in tempo | tempo scaduto |
| rifiuta il collegamento | collegamento rifiutato |
| il tablet non e' in rete | tablet fuori rete |
| connessione non riuscita | tutto il resto |

Una lampada che dorme può metterci mezzo minuto a rispondere. Il messaggio
completo sta nel registro, con il tag `Casa.Tuya`: leggilo con `adb logcat`.

## Per chi modifica il codice

- `Tuya.java` è copiato da un altro progetto: annota ogni correzione nella nota
  in testa al file.
- Intestazione di versione **dopo** la cifratura in 3.3, **prima** in 3.4.
- Salta i quattro byte di esito solo nelle risposte.
- Dopo un comando la lampada rimanda solo i valori cambiati: tieni lo stato
  completo in `Tuya` e aggiornalo pezzo per pezzo.
- Insisti solo sull'apertura della connessione (mezzo minuto per un tocco,
  `Tuya.leggiSvelto` per le letture automatiche), mai su un comando già partito.
- Manda i passi di una routine in ordine, uno alla volta, e chiudi sempre la
  socket: una lampada Tuya accetta una connessione per volta.
- Leggi lo stato a ciclo solo mentre la sezione è sullo schermo
  (`Luci.seguiDaVicino`), così le lampade possono dormire.
- Mostra a schermo i testi di `Tuya.fallita` e manda il messaggio di sistema al
  registro; cerca « No route to host » per esteso.
