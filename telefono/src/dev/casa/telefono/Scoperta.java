package dev.casa.telefono;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Cerca il tablet sulla rete di casa, con NSD.
 *
 * Il tablet si annuncia come {@code _casa._tcp.} con un nome che comincia per
 * « Casa ». Qui si ascolta per un po' e si risolve il primo che risponde.
 *
 * <b>Un resolve alla volta.</b> Sulle versioni vecchie di Android un secondo
 * resolve mentre il primo e' in corso fallisce con FAILURE_ALREADY_ACTIVE: con
 * un solo tablet in casa basta risolvere il primo trovato e fermarsi.
 *
 * <b>Il multicast lock</b> serve perche' mDNS e' multicast, e molti telefoni
 * con lo schermo acceso lo filtrano lo stesso per risparmiare: senza, la
 * ricerca non trova niente e non dice perche'.
 */
final class Scoperta {

    private static final String TIPO = "_casa._tcp.";

    private Scoperta() {}

    /** Bloccante: l'indirizzo IPv4 del tablet, o null dopo {@code attesaMs}. */
    static String cerca(Context c, long attesaMs) {
        final Context app = c.getApplicationContext();
        final NsdManager nsd = (NsdManager) app.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return null;

        WifiManager wifi = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = null;
        if (wifi != null) {
            lock = wifi.createMulticastLock("casa-ricerca");
            lock.setReferenceCounted(false);
            lock.acquire();
        }

        final CountDownLatch fatto = new CountDownLatch(1);
        final String[] trovato = new String[1];
        final boolean[] risolvendo = new boolean[1];

        NsdManager.DiscoveryListener ascolto = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String t, int e) { fatto.countDown(); }
            @Override public void onStopDiscoveryFailed(String t, int e) { }
            @Override public void onDiscoveryStarted(String t) { }
            @Override public void onDiscoveryStopped(String t) { }
            @Override public void onServiceLost(NsdServiceInfo s) { }

            @Override public void onServiceFound(NsdServiceInfo s) {
                if (s.getServiceName() == null || !s.getServiceName().startsWith("Casa")) return;
                synchronized (risolvendo) {
                    if (risolvendo[0]) return;
                    risolvendo[0] = true;
                }
                nsd.resolveService(s, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo s, int e) {
                        synchronized (risolvendo) { risolvendo[0] = false; }
                    }
                    @Override public void onServiceResolved(NsdServiceInfo s) {
                        InetAddress a = s.getHost();
                        if (a instanceof Inet4Address) {
                            trovato[0] = a.getHostAddress();
                            fatto.countDown();
                        } else {
                            synchronized (risolvendo) { risolvendo[0] = false; }
                        }
                    }
                });
            }
        };

        try {
            nsd.discoverServices(TIPO, NsdManager.PROTOCOL_DNS_SD, ascolto);
            fatto.await(attesaMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignorata) {
        } finally {
            try { nsd.stopServiceDiscovery(ascolto); } catch (Exception ignorata) { }
            if (lock != null) lock.release();
        }
        return trovato[0];
    }
}
