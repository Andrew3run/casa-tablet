# Il ciclo di Impostazioni

## Il problema

Un receiver di fabbrica in `com.android.settings`
(`com.mediatek.settings.RestoreRotationReceiver`) rispedisce a sé stesso
`BOOT_COMPLETED` all'infinito e occupa due core per tutto il tempo in cui il
tablet è acceso.

## Cosa fa l'app

L'assistente è device owner e tiene Impostazioni nascoste
(`DevicePolicyManager.setApplicationHidden`), così il receiver resta fermo.
Il codice sta in `tablet/src/dev/casa/Impostazioni.java`:

- **all'avvio** nasconde Impostazioni, prima di `BOOT_COMPLETED`;
- **la tessera « Impostazioni »** della sezione App le rende visibili e le apre
  (`MATCH_UNINSTALLED_PACKAGES` e intent esplicito);
- **al ritorno nell'app** (`onResume`) le nasconde e le chiude.

Con Impostazioni nascoste, la Home provvisoria dell'avvio è
`tablet/src/dev/casa/AvvioActivity.java` (`directBootAware="true"`, priorità
-1000, chiude su `ACTION_USER_UNLOCKED`). **Installa sempre una versione con
AvvioActivity**: senza, il tablet resta fermo sull'animazione di avvio.

Da sapere:

- Apri Impostazioni solo dalla tessera.
- Gli script scrivono le impostazioni con `settings put`.
- Per accoppiare un dispositivo Bluetooth passa dalla tessera.
- Senza device owner, `tools/sistema.ps1` spezza il ciclo dal PC con
  `disable-user`/`enable`.

## Verificare

- `tools/sistema.ps1 -Verifica` dice se il ciclo è in corso.
- Tocca la tessera: `hidden=false`, Impostazioni aperte. Premi Home:
  `hidden=true`, processo sparito.
- Per il carico guarda `dumpsys cpuinfo` o `top`: su questo ROM
  `/proc/loadavg` sta a 8 anche a tablet fermo.

Se il tablet resta fermo sull'animazione di avvio, dal PC:

    adb shell am start -n dev.casa/.AvvioActivity
