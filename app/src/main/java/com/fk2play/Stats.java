package com.fk2play;

import android.os.SystemClock;
import android.util.Log;

/**
 * Mesures de reactivite (journal « FK2Stat », toutes les 5 s pendant que CarPlay tourne).
 * Sert au banc d'essai et au diagnostic : images/s, latence d'affichage, a-coups,
 * temps passe par le tactile et par l'audio, temps de reprise de l'image.
 */
public final class Stats {
    private static final String TAG = "FK2Stat";
    private static final long PERIOD_MS = 5000;

    private Stats() {}

    private static long periodStart = SystemClock.uptimeMillis();
    // Video
    private static int shown;
    private static long latSum, latMax, decSum, decMax, gapMax, lastShown;
    private static int dropped, skipped;
    // Tactile (temps passe dans le thread UI pour envoyer un evenement)
    private static int touches;
    private static long touchSum, touchMax;
    // Audio (temps bloque dans l'ecriture, du point de vue du thread qui ecrit)
    private static long audioBlockSum, audioBlockMax;
    // Reprise de l'image
    private static long resumeAt, callEndAt;

    public static synchronized void frameShown(long arrivalNs, long decodeNs) {
        long now = SystemClock.uptimeMillis();
        long lat = (System.nanoTime() - arrivalNs) / 1000000L;
        shown++;
        latSum += lat;
        if (lat > latMax) latMax = lat;
        long dec = decodeNs / 1000L;
        decSum += dec;
        if (dec > decMax) decMax = dec;
        if (lastShown != 0 && now - lastShown > gapMax) gapMax = now - lastShown;
        lastShown = now;
        if (resumeAt != 0) {
            Log.i(TAG, "reprise_image=" + (now - resumeAt) + "ms");
            resumeAt = 0;
        }
        if (callEndAt != 0) {
            Log.i(TAG, "retour_apres_appel=" + (now - callEndAt) + "ms");
            callEndAt = 0;
        }
        flushIfDue(now);
    }

    public static synchronized void frameDropped(int n) { dropped += n; }

    /** Image decodee mais non affichee (rattrapage d'un retard). */
    public static synchronized void frameSkipped() { skipped++; }

    /** Derniere image reaffichee instantanement au retour sur l'ecran CarPlay. */
    public static synchronized void imageReshown() {
        long now = SystemClock.uptimeMillis();
        if (resumeAt != 0) {
            Log.i(TAG, "reprise_image=" + (now - resumeAt) + "ms (derniere image)");
            resumeAt = 0;
        }
        if (callEndAt != 0) {
            Log.i(TAG, "retour_apres_appel=" + (now - callEndAt) + "ms (derniere image)");
            callEndAt = 0;
        }
        lastShown = now;
    }

    public static synchronized void touchSent(long ns) {
        touches++;
        long us = ns / 1000L;
        touchSum += us;
        if (us > touchMax) touchMax = us;
    }

    public static synchronized void audioWrite(long ns) {
        long us = ns / 1000L;
        audioBlockSum += us;
        if (us > audioBlockMax) audioBlockMax = us;
    }

    /** L'ecran CarPlay redevient visible : on chronometre jusqu'a la premiere image. */
    public static synchronized void screenResumed() {
        resumeAt = SystemClock.uptimeMillis();
        lastShown = 0;
    }

    public static synchronized void callEnded() {
        callEndAt = SystemClock.uptimeMillis();
        Log.i(TAG, "fin_appel");
    }

    private static void flushIfDue(long now) {
        long dt = now - periodStart;
        if (dt < PERIOD_MS) return;
        Log.i(TAG, String.format(java.util.Locale.US,
                "video ips=%.1f lat_moy=%d lat_max=%d dec_moy=%.1f dec_max=%.1f ecart_max=%d pertes=%d sautees=%d"
                        + " | tactile n=%d moy=%dus max=%dus | audio bloque=%dms max=%dms",
                shown * 1000f / dt, shown > 0 ? latSum / shown : 0, latMax,
                shown > 0 ? decSum / 1000f / shown : 0f, decMax / 1000f, gapMax, dropped, skipped,
                touches, touches > 0 ? touchSum / touches : 0, touchMax,
                audioBlockSum / 1000, audioBlockMax / 1000));
        periodStart = now;
        shown = 0; latSum = 0; latMax = 0; decSum = 0; decMax = 0; gapMax = 0; dropped = 0; skipped = 0;
        touches = 0; touchSum = 0; touchMax = 0;
        audioBlockSum = 0; audioBlockMax = 0;
    }
}
