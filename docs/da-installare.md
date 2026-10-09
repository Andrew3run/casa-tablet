# Cosa installare

## Sul PC

| Strumento | Versione |
|---|---|
| JDK (`javac`, `keytool`) | Oracle Java 25.0.2 LTS |
| .NET SDK | 10.0.201 |
| Android SDK build-tools | 35.0.0 e 36.1.0 (`aapt2`, `d8`, `zipalign`, `apksigner`) |
| `platform-tools` / `adb` | in `tools/platform-tools` |
| Platform `android-24` | installata il 3 settembre 2026 |

Per installare la platform su un PC nuovo:

    %LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat "platforms;android-24"

- Compila sempre contro `android-24`: così il compilatore ferma le API assenti
  su questo tablet.
- La catena è `aapt2 -> javac -> d8 -> zipalign -> apksigner`, lanciata da
  `build.ps1`, e dura un paio di secondi.
- Usa `javac -source 8 -target 8` (invece di `--release 8`, che userebbe le
  librerie del JDK al posto di `android.jar`). Con `minSdk 24` il `d8` di
  build-tools 36 fa il desugaring: lambda, `java.util.function` e metodi di
  default sono utilizzabili.

## Sul tablet

Il tablet è usato e contiene app e dati di chi lo aveva prima. Nell'ordine:

1. **Salva quello che serve** (foto, note di `fastnotepad`, chat) con `adb pull`
   di `/sdcard`.
2. **Reset di fabbrica**, per togliere account Google, notifiche e app.
3. **Riattiva il debug USB**: Impostazioni -> Info -> sette tocchi sul numero di
   build.
4. **Installa l'app** con `adb install` e scegli Assistente Home come Home.

Sul tablet basta l'app. Il Play Store funziona: usalo per aggiornare.

## Da lasciare com'è

- **Il sistema di fabbrica**: tieni il ROM originale.
- **I permessi normali**: SELinux resta Enforcing; Home dedicata, schermo intero
  e decodifica H.264 funzionano da app normale.
- **La WebView**: Chrome 92 fa già da WebView di sistema.
