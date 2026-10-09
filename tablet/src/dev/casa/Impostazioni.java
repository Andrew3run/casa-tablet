package dev.casa;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Le Impostazioni di sistema stanno nascoste, e si mostrano solo per aprirle.
 *
 * Non e' un vezzo da chiosco: e' la cura di un bug del ROM. Dentro
 * com.android.settings c'e' com.mediatek.settings.RestoreRotationReceiver,
 * che a ogni BOOT_COMPLETED rispedisce a se stesso l'intent appena ricevuto,
 * senza nessuna condizione. Ne nasce una tempesta di broadcast che dura
 * finche' il tablet e' acceso: Impostazioni al 40% di CPU, system_server al
 * 180%, due core su quattro persi per sempre. Era il motivo per cui "tutto
 * era lento" (docs/ciclo-impostazioni.md).
 *
 * Senza root il singolo receiver non si spegne: la shell di Android 7 cambia
 * solo pacchetti interi. Il device owner pero' puo' nascondere un pacchetto
 * (setApplicationHidden), e un pacchetto nascosto non riceve broadcast e non
 * gira. Casa lo nasconde appena parte - la Home si avvia prima che il sistema
 * mandi BOOT_COMPLETED - e lo rimostra giusto il tempo di aprirlo. Quando si
 * torna a Casa viene nascosto di nuovo, il che lo chiude anche.
 *
 * Un pacchetto nascosto resta nascosto anche dopo il riavvio, e questo ha un
 * prezzo che Android 7 presenta all'avvio: la Home provvisoria di serie,
 * FallbackHome, sta dentro Impostazioni. Senza, il tablet non parte. Per
 * questo esiste {@link AvvioActivity}: le due classi vanno insieme.
 *
 * Senza device owner non fa niente: le Impostazioni restano normali, e il
 * ciclo si spezza da adb con tools/sistema.ps1.
 */
final class Impostazioni {

    static final String PACCHETTO = "com.android.settings";
    private static final String TAG = "Casa";

    private Impostazioni() { }

    private static DevicePolicyManager dpm(Context c) {
        DevicePolicyManager d =
            (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return d != null && d.isDeviceOwnerApp(c.getPackageName()) ? d : null;
    }

    private static ComponentName admin(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    /** Nasconde le Impostazioni, se siamo device owner e non lo sono gia'. */
    static void nascondi(Context c) {
        DevicePolicyManager d = dpm(c);
        if (d == null) return;
        try {
            if (!d.isApplicationHidden(admin(c), PACCHETTO)) {
                d.setApplicationHidden(admin(c), PACCHETTO, true);
                Log.i(TAG, "Impostazioni nascoste");
            }
        } catch (Exception e) {
            Log.w(TAG, "non riesco a nascondere le Impostazioni", e);
        }
    }

    /** Le rimostra: da chiamare un attimo prima di aprirle, non prima. */
    static void mostra(Context c) {
        DevicePolicyManager d = dpm(c);
        if (d == null) return;
        try {
            if (d.isApplicationHidden(admin(c), PACCHETTO)) {
                d.setApplicationHidden(admin(c), PACCHETTO, false);
                Log.i(TAG, "Impostazioni mostrate per aprirle");
            }
        } catch (Exception e) {
            Log.w(TAG, "non riesco a mostrare le Impostazioni", e);
        }
    }

    /**
     * L'intent che le apre. Esplicito, perche' getLaunchIntentForPackage su un
     * pacchetto nascosto restituisce null: e' lo stesso motivo per cui
     * l'installazione si legge con {@link #info}.
     */
    static Intent apertura() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(PACCHETTO, "com.android.settings.Settings");
        return i;
    }

    /** L'ApplicationInfo anche da nascoste, per icona e presenza. Null se
     *  le Impostazioni non ci sono proprio. */
    static ApplicationInfo info(Context c) {
        try {
            return c.getPackageManager().getApplicationInfo(
                PACCHETTO, PackageManager.MATCH_UNINSTALLED_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
