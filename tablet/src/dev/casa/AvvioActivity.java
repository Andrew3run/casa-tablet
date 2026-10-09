package dev.casa;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserManager;
import android.util.Log;

/**
 * La Home provvisoria: i secondi fra l'accensione e lo sblocco dell'utente.
 *
 * Android 7 sceglie la Home nel systemReady, prima di sbloccare l'utente, e
 * in quel momento accetta solo attivita' "direct boot aware". Di serie quel
 * lavoro lo fa com.android.settings/.FallbackHome, che aspetta lo sblocco e
 * si toglie di mezzo. Ma Casa tiene Impostazioni nascoste (vedi
 * {@link Impostazioni}), e con Impostazioni nascoste FallbackHome non esiste:
 * il sistema scrive "No home screen found", l'utente resta in BOOTING,
 * l'animazione di avvio non finisce mai. E' successo, il 4 settembre 2026.
 *
 * Questa attivita' e' la stessa cosa fatta in casa. Priorita' -1000 come
 * l'originale, cosi' a utente sbloccato la Home preferita resta MainActivity.
 * Non tocca niente che stia nella memoria cifrata - niente preferenze,
 * niente file - perche' finche' l'utente e' bloccato quella memoria non c'e'.
 */
public class AvvioActivity extends Activity {

    private static final String TAG = "Casa";

    private final Handler mano = new Handler();
    private final Runnable riprova = new Runnable() {
        @Override public void run() { forseFinisci(); }
    };
    private final BroadcastReceiver sbloccato = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { forseFinisci(); }
    };

    @Override
    protected void onCreate(Bundle stato) {
        super.onCreate(stato);
        registerReceiver(sbloccato, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        forseFinisci();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mano.removeCallbacks(riprova);
        unregisterReceiver(sbloccato);
    }

    /** Appena l'utente e' sbloccato e la Home vera risponde, ci si leva. */
    private void forseFinisci() {
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        if (um == null || !um.isUserUnlocked()) return;

        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, 0);
        ComponentName vera = ri == null || ri.activityInfo == null ? null
            : new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
        if (vera == null || vera.equals(getComponentName())) {
            Log.w(TAG, "utente sbloccato ma nessuna Home vera: riprovo fra mezzo secondo");
            mano.postDelayed(riprova, 500);
            return;
        }
        Log.i(TAG, "utente sbloccato: lascio il posto a " + vera.flattenToShortString());
        finish();
    }
}
