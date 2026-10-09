package dev.casa;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * L'aggancio del device owner.
 *
 * Non fa quasi niente ed e' giusto cosi': serve a esistere, perche'
 * "dpm set-device-owner dev.casa/.AdminReceiver" ha bisogno di un componente
 * a cui attaccarsi. Le capacita' vere (chiosco, barre spente, installazioni
 * silenziose) si chiedono dal codice tramite DevicePolicyManager, non da qui.
 */
public class AdminReceiver extends DeviceAdminReceiver {

    static final String TAG = "Casa";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "device admin attivo");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Assistente Home smettera' di comportarsi da apparecchio: il tablet torna un tablet normale.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "device admin disattivato");
    }
}
