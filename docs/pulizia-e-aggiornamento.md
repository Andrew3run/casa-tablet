# Pulire, alleggerire, aggiornare

## I passi

1. Aggiorna Chrome (fino alla 119) e Play Services (l'ultima per API 24),
   finché il Play Store funziona. Due strade:
   - account Google sul tablet e aggiornamento dal Play Store; al primo
     accesso il tablet resta occupato per circa un'ora, quindi aspetta prima di
     lanciare un `adb install`;
   - sideload degli APK per `armeabi-v7a` con `adb install`.
2. Spegni DuraSpeed (`com.mediatek.duraspeed`) e ricontrolla
   `setting.duraspeed.enabled` dopo ogni riavvio: può riaccendersi da solo.
3. Togli le app delle liste qui sotto con `alleggerisci.ps1`, a blocchi,
   riavviando fra un blocco e l'altro.
4. Misura `MemAvailable` prima e dopo.
5. Installa l'app.

## Togliere e rimettere le app

`alleggerisci.ps1` usa `pm uninstall -k --user 0`. Per rimettere un'app:

    pm install -r --user 0 /system/priv-app/Qualcosa/Qualcosa.apk

oppure `alleggerisci.ps1 -Rimetti`, che recupera anche le app aggiornate da
`/data/app`. Se dopo un `-Rimetti` ricompaiono app tolte, rilancia
`alleggerisci.ps1`.

### Via per primi

    com.adups.fota                 FOTA cinese
    com.baidu.map.location         geolocalizzazione Baidu
    com.sencatech.iwawa.iwawahome  launcher per bambini del vecchio padrone
    com.sencatech.spinguess        i tre giochi per bambini
    com.sencatech.learninganimals
    com.sencatech.littlepianist
    com.netflix.mediaclient        (se ti serve, tienilo)

### Avanzi di fabbrica e di collaudo

    com.mediatek.engineermode      com.mediatek.factorymode
    com.mediatek.mtklogger         com.mediatek.lbs.em2.ui
    com.mediatek.mdmlsample        com.mediatek.bluetooth.dtt
    com.mediatek.miravision.ui     com.android.egg
    com.example                    com.mediatek

### App inutili su un chiosco

    com.android.browser            com.google.android.gm
    com.android.calculator2        com.android.calendar
    com.android.gallery3d          com.android.music
    com.android.musicfx            com.android.soundrecorder
    com.mediatek.filemanager       com.mediatek.calendarimporter
    com.android.printspooler       com.android.dreams.basic
    com.android.wallpaper.livepicker
    com.android.wallpapercropper
    com.android.providers.partnerbookmarks
    com.android.bookmarkprovider
    com.google.android.printservice.recommendation
    com.google.android.syncadapters.contacts
    com.google.android.syncadapters.calendar
    com.android.contacts           com.mediatek.camera
    com.android.documentsui        com.android.deskclock

Tieni `com.android.fmradio`.

### La telefonia, con lo slot SIM vuoto

    com.android.dialer             com.android.mms
    com.android.stk                com.android.emergency
    com.android.calllogbackup      com.android.carrierconfig
    com.android.providers.blockednumber
    com.mediatek.omacp             com.mediatek.simprocessor
    com.mtk.telephony
    org.simalliance.openmobileapi.service
    org.simalliance.openmobileapi.uicc1terminal
    org.simalliance.openmobileapi.uicc2terminal
    org.simalliance.openmobileapi.eseterminal

### Secondo giro

`feedback`, `partnersetup`, `onetimeinitializer`, `configupdater`, `verifier`,
`location.fused`, `providers.applications`, `vpndialogs`,
`managedprovisioning`, `server.telecom`, `providers.contacts`.

### Da lasciare sempre

    android                        com.android.systemui
    com.android.settings           com.android.shell
    com.android.phone              com.android.providers.*
    com.google.android.gms         com.android.vending
    com.google.android.gsf         com.google.android.googlequicksearchbox
    com.google.android.tts         com.android.chrome
    com.android.inputmethod.latin  com.android.captiveportallogin
    com.android.certinstaller      com.android.keychain
    com.mediatek.connectivity      com.mediatek.thermalmanager
    com.android.providers.telephony
    com.google.android.backuptransport
    com.google.android.gsf.login

Tieni anche `com.android.launcher3` finché la Home dell'assistente è pronta.
L'elenco completo sta in `$MaiToccare` di `alleggerisci.ps1`.

## Il Play Store

Tienilo installato e spento:

- `tools/negozio.ps1 -Apri` lo accende e lo apre per un aggiornamento;
- `tools/negozio.ps1 -Chiudi` lo rispegne.

**Se chiede il login con l'account già presente**, rimetti
`com.google.android.backuptransport` da
`/system/priv-app/GoogleBackupTransport/`.

Per leggere gli avvisi di GMS nel registro, apri i tag (si richiudono al
riavvio):

    adb shell setprop log.tag.Auth VERBOSE
    adb shell setprop log.tag.GLSUser VERBOSE
    adb shell setprop log.tag.AccountManagerService VERBOSE

## Togliere Google

`alleggerisci.ps1 -SenzaGoogle` toglie `com.android.vending`,
`com.google.android.gms` e `com.google.android.gsf`, e con loro il
riconoscimento del parlato. Usalo quando c'è un riconoscitore a bordo.
