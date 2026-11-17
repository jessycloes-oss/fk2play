package com.fk2play.video;

import android.os.SystemClock;
import android.view.Surface;

/**
 * Decodage H.264 MATERIEL du Tegra 3 (OMX via libstagefright d'Android 4.0.4), rendu direct a l'ecran.
 *
 * Base sur le module omxvideocodec d'OpenDroidAuto, (c) MaKi983 / MaKi83, reutilise dans FK2Play avec
 * son accord (XDA, 16 nov. 2026). Bibliotheque native separee (libfk2omx.so) : si elle ne se charge pas
 * ou si le decodeur materiel echoue, FK2Play repasse automatiquement sur FFmpeg.
 *
 * Lie a son ecran : il ne survit pas sans Surface (pas de decodage en arriere-plan pendant un appel ;
 * dans ce cas FFmpeg prend le relais, puis le decodeur materiel reprend a la prochaine image cle).
 */
public class OmxDecoder implements VideoDecoder {
    private static boolean loaded;
    private static String loadError;
    /** Le decodeur materiel a echoue sur cet autoradio : plus d'essai jusqu'au redemarrage de FK2Play. */
    private static volatile boolean broken;
    private static volatile String brokenReason;

    static {
        try {
            System.loadLibrary("fk2omx");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            loadError = t.getClass().getSimpleName() + " : " + t.getMessage();
            android.util.Log.w("FK2Play", "Decodeur materiel indisponible : " + loadError);
        }
    }

    public static boolean isAvailable() { return loaded && !broken; }

    /** Tests dans l'emulateur uniquement (APK debug) : accepte le decodeur OMX logiciel d'Android. */
    public static volatile boolean allowSoftwareOmxForTests;

    /** Pour le diagnostic : pourquoi le decodeur materiel n'est pas utilise. */
    public static String unavailableReason() {
        if (!loaded) return "bibliotheque non chargee (" + loadError + ")";
        if (broken) return brokenReason;
        return null;
    }

    static void markBroken(String why) {
        broken = true;
        brokenReason = why;
        android.util.Log.w("FK2Play", "Decodeur materiel abandonne : " + why);
    }

    /** Images recues sans aucun affichage au-dela desquelles on considere que le decodeur ne marche pas. */
    private static final int MAX_FRAMES_WITHOUT_OUTPUT = 90;
    private static final long MAX_MS_WITHOUT_OUTPUT = 4000;

    private long handle;
    private int fedSinceOutput;
    private long firstFedWithoutOutput;
    private boolean everRendered;
    /** Le decodeur a jete des images (retard) : VideoPipeline demande une image cle. */
    volatile boolean keyframeWanted;

    @Override
    public boolean init(Surface surface, int width, int height) {
        if (!isAvailable() || surface == null) return false;
        try {
            handle = nativeCreate(surface, width, height, 30, !allowSoftwareOmxForTests);
        } catch (Throwable t) {
            markBroken("erreur a l'initialisation : " + t);
            handle = 0;
        }
        if (handle == 0 && !broken) markBroken("aucun decodeur H.264 materiel sur cet autoradio");
        return handle != 0;
    }

    @Override
    public boolean keepsStateWithoutSurface() { return false; }

    @Override
    public boolean setSurface(Surface surface, boolean showLast) { return false; }

    @Override
    public int decode(byte[] data, int off, int len, boolean show) {
        if (handle == 0) return -1;
        int r = nativeDecode(handle, data, off, len, SystemClock.elapsedRealtime() * 1000L);
        if (r < 0) {
            markBroken("le decodeur materiel ne repond plus");
            return -1;
        }
        if ((r & 2) != 0) keyframeWanted = true;
        if ((r & 1) != 0) {
            everRendered = true;
            fedSinceOutput = 0;
            return SHOWN;
        }
        // Garde-fou : des images entrent mais rien ne sort -> repli sur FFmpeg.
        long now = SystemClock.uptimeMillis();
        if (fedSinceOutput++ == 0) firstFedWithoutOutput = now;
        if (!everRendered && fedSinceOutput > MAX_FRAMES_WITHOUT_OUTPUT
                && now - firstFedWithoutOutput > MAX_MS_WITHOUT_OUTPUT) {
            markBroken("aucune image affichee par le decodeur materiel");
            return -1;
        }
        return NOTHING;
    }

    /** Le decodeur materiel affiche lui-meme ses images. */
    @Override
    public boolean renderPending() { return false; }

    @Override
    public void setFast(boolean fast) { }

    @Override
    public void forgetLast() { }

    public String info() {
        return handle == 0 ? "" : nativeInfo(handle);
    }

    @Override
    public void release() {
        if (handle != 0) {
            long h = handle;
            handle = 0;
            nativeRelease(h);
        }
    }

    @Override
    public String name() {
        return "Materiel (Tegra)";
    }

    private static native long nativeCreate(Surface surface, int width, int height, int fps, boolean hwOnly);

    private static native int nativeDecode(long handle, byte[] data, int off, int len, long timeUs);

    private static native String nativeInfo(long handle);

    private static native void nativeRelease(long handle);
}
