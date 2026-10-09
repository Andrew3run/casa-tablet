package dev.casa.telefono;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Il lavoro che porta la sveglia al tablet.
 *
 * onStartJob gira sul thread principale: la rete va su un filo suo, e
 * jobFinished dice al sistema se riprovare. Fuori casa il tablet non si trova,
 * e il riprovare e' proprio quello che fa arrivare la sveglia quando si rientra.
 */
public final class LavoroSveglia extends JobService {

    private volatile Thread filo;

    @Override public boolean onStartJob(final JobParameters params) {
        filo = new Thread(new Runnable() {
            @Override public void run() {
                boolean fatto = Specchio.allinea(getApplicationContext());
                // Il periodico si ripete da solo: chiedergli di riprovare
                // aggiungerebbe un secondo giro al primo.
                boolean riprova = !fatto && params.getJobId() == Specchio.LAVORO_SUBITO;
                jobFinished(params, riprova);
            }
        }, "Casa-sveglia");
        filo.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        Thread f = filo;
        if (f != null) f.interrupt();
        return true;
    }
}
