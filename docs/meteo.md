# Il meteo

Il meteo sta in tre posti: una superficie nella **Home**, una **tessera nella
sezione App** e una **pagina intera** che si apre da tutte e due.

I file:
[`Meteo`](../tablet/src/dev/casa/Meteo.java) (il dato),
[`Cielo`](../tablet/src/dev/casa/Cielo.java) (il disegno che si muove),
[`VelaMeteo`](../tablet/src/dev/casa/VelaMeteo.java) (la pagina),
[`Fiducia`](../tablet/src/dev/casa/Fiducia.java) (i certificati in più).

## Il dato

Ogni **mezz'ora** l'app chiede a **Open-Meteo** (libero, senza chiave); dopo un
errore riprova dopo **due minuti**.

    https://api.open-meteo.com/v1/forecast
      ?latitude=…&longitude=…
      &current=temperature_2m,apparent_temperature,relative_humidity_2m,
               is_day,weather_code,wind_speed_10m
      &hourly=temperature_2m,weather_code,precipitation_probability
      &daily=weather_code,temperature_2m_max,temperature_2m_min,
             precipitation_probability_max,sunrise,sunset
      &hourly=... ,apparent_temperature,relative_humidity_2m,
               wind_speed_10m,wind_direction_10m
      &timezone=Europe%2FRome&forecast_days=7
      &past_hours=24&forecast_hours=24

- Chiedi **ventiquattro ore indietro e ventiquattro avanti**: la pagina mostra
  anche le fasce già passate.
- Cerca **sempre** l'indice dell'ora corrente confrontando le stringhe degli
  orari locali (`2026-09-08T14:00`): la copia su disco può essere vecchia.
- La risposta va **così com'è** in `meteo.json` (`getFilesDir()`, scrittura
  atomica di [`Archivio`](../tablet/src/dev/casa/Archivio.java)), e si rilegge
  con lo stesso codice della rete. All'avvio il meteo compare subito.

## I luoghi

- Nella pagina tocca la **pastiglia** col nome della città: si apre l'elenco dei
  luoghi.
- Per cercare, scrivi il nome con la tastiera dell'app
  ([`Tastierino`](../tablet/src/dev/casa/Tastierino.java)). Arrivano fino a
  cinque candidati, con **regione e paese sotto il nome**.
- Tocca un candidato: l'app ci va **e lo mette da parte** (al massimo
  **cinque**). La crocetta toglie un luogo, tranne quello attuale.

Da adb:

    adb shell am broadcast -a dev.casa.METEO --es citta "Milano"

## I certificati in più

Le radici di Let's Encrypt mancano su questo Android: l'app le porta in
`tablet/assets/radici/`.

| file | scade |
|---|---|
| `isrgrootx1.pem` (ISRG Root X1) | 2035 |
| `isrg-root-x2.pem` (ISRG Root X2) | 2040 |
| `root-yr.pem` (Root YR) | 2045 |
| `root-ye.pem` (Root YE) | 2045 |

In [`Fiducia`](../tablet/src/dev/casa/Fiducia.java): copia prima tutte le
radici di `AndroidCAStore`, poi aggiungi le nostre, e tieni la verifica
completa dei certificati. Nel registro:

    Casa: Fiducia: 148 radici di sistema + 4 nostre

## Il cielo che si muove

[`Cielo`](../tablet/src/dev/casa/Cielo.java) disegna sette scene (sereno,
velato, coperto, nebbia, pioggia, neve, temporale), di giorno e di notte. Le
tessere delle previsioni usano le icone del set (`sunny`, `partly_cloudy_day`,
`rainy`, `thunderstorm`, `weather_snowy`, `foggy`).

Regole per chi modifica il disegno:

- Ogni movimento è una funzione di `Anima.ora()`: zero oggetti per goccia, zero
  `ValueAnimator`, zero allocazioni in `disegna`.
- Le gocce stanno alla **parte frazionaria di un multiplo irrazionale**
  dell'indice.
- La nuvola è **un percorso solo**; l'alone del sole è un `RadialGradient`; la
  luna si ritaglia con `Path.op(..., DIFFERENCE)`. Le forme si costruiscono
  quando cambia la finestra e restano fisse (vedi « Le tre trappole » in
  [aspetto.md](aspetto.md)).
- Ridisegno: nella **Home** e nella tessera **App** `postInvalidateDelayed` a
  **50 ms** sul rettangolo del pannello, allargato di due pixel per parte;
  nella **pagina** fotogramma pieno sul rettangolo della scena. Senza dato, o
  fuori scena, il cielo si ferma.
- La tinta della scena (`Tinte.SOLE`, `ACQUA`, `GELO`, `NUVOLA`, `LAMPO`,
  `NOTTURNO`) va nel disegno, nella parola sotto i gradi e nelle icone delle
  ore; il velo del pannello resta quello della Home.

## Quello che si vede

### Nella Home

Una superficie dentro la scheda dell'ora: la scena, i gradi, com'è il tempo, la
massima e la minima di oggi, e **le prossime parti della giornata** (notte 0-6,
mattina 6-12, pomeriggio 12-18, sera 18-24), una per riga, con segno del tempo,
pioggia e gradi. Per ogni parte: gradi **medi**, tempo **più grave** delle sue
ore. Toccala per aprire la pagina.

Ogni dieci secondi la superficie mostra per altri dieci due notizie: vedi
[notizie.md](notizie.md).

### Nella sezione App

La prima tessera, con la scena animata. Toccala per aprire la pagina.

### La pagina

- **In alto**: titolo, pastiglia della città, linguette e tasti.
- **A sinistra, adesso**: scena grande, gradi, percepiti, umidità, vento,
  pioggia, tramonto.
- **A destra, ora per ora**: le linguette delle quattro parti di oggi (notte,
  mattina, pomeriggio, sera) e **una colonna per ora**, sei per volta, con ora,
  segno, gradi, pioggia, umidità, vento e direzione. Le ore passate sono
  smorte, quella in corso ha un velo bianco. La linguetta scelta col dito resta.
- **Sotto, i sette giorni** con la **barra delle temperature**.

## Come si prova

    # dove guarda
    adb shell am broadcast -a dev.casa.METEO --es citta "Milano"

    # cosa dice il registro (il tag è Casa, vedi MainActivity)
    adb logcat -s Casa:V | findstr /C:"Meteo" /C:"Fiducia"

    # la copia su disco
    adb shell run-as dev.casa cat files/meteo.json

## Al rientro

Con l'app sullo schermo il meteo controlla ogni cinque minuti se è passata la
mezz'ora (`Meteo.riprendi` e `sospendi`, da `onResume` e `onPause`). Quando
qualcuno torna, `Meteo.riprova` chiede subito: vedi
[notizie.md](notizie.md#al-rientro).

## Da fare

- **La voce**: « casa, che tempo fa » in
  [`Comandi`](../tablet/src/dev/casa/Comandi.java).
- **Un avviso** tipo « domani piove », con `precipitation_probability_max`.
- **Accenti nella ricerca**: la tastiera
  ([`Tastierino`](../tablet/src/dev/casa/Tastierino.java)) è senza accenti.
