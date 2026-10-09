package dev.casa.telefono;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * La sveglia del telefono e' cambiata, o il telefono si e' riacceso.
 *
 * Qui non si fa rete: un ricevitore ha pochi secondi e nessuna garanzia che il
 * Wi-Fi ci sia. Si passa la mano al JobScheduler, che aspetta la rete lui.
 */
public final class Ricevitore extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Specchio.programma(c.getApplicationContext());
    }
}
