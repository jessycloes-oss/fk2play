package com.fk2play.video;

import android.view.Surface;

/**
 * Decodage logiciel FFmpeg via JNI (libfk2video.so).
 * Indispensable sur Android 4.0.4 (API 15) : MediaCodec n'existe qu'a partir de l'API 16.
 * Le decodeur est independant de l'ecran : il peut decoder sans afficher et reafficher
 * instantanement sa derniere image quand l'ecran CarPlay revient.
 */
public class FfmpegDecoder implements VideoDecoder {
    private static boolean loaded;

    static {
        try {
            System.loadLibrary("fk2video");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
    }

    public static boolean isAvailable() {
        return loaded;
    }

    private long handle;
    /** Diagnostic : 0 = automatique (mesure sur l'appareil), 1 = RGB565, 2 = RGBX. */
    public static volatile int forcedFormat;

    @Override
    public boolean init(Surface surface, int width, int height) {
        if (!loaded) return false;
        handle = nativeInit(width, height, Runtime.getRuntime().availableProcessors(), forcedFormat);
        if (handle != 0 && surface != null) nativeSetWindow(handle, surface, false);
        return handle != 0;
    }

    @Override
    public boolean keepsStateWithoutSurface() { return true; }

    @Override
    public boolean setSurface(Surface surface, boolean showLast) {
        return handle != 0 && nativeSetWindow(handle, surface, showLast) == 1;
    }

    @Override
    public int decode(byte[] data, int off, int len, boolean show) {
        return handle == 0 ? -1 : nativeDecode(handle, data, off, len, show);
    }

    @Override
    public boolean renderPending() {
        return handle != 0 && nativeRenderPending(handle) == 1;
    }

    @Override
    public void setFast(boolean fast) {
        if (handle != 0) nativeSetFast(handle, fast);
    }

    @Override
    public void forgetLast() {
        if (handle != 0) nativeForgetLast(handle);
    }

    @Override
    public void release() {
        if (handle != 0) {
            nativeRelease(handle);
            handle = 0;
        }
    }

    @Override
    public String name() {
        return com.fk2play.Tr.s("FFmpeg (logiciel)", "FFmpeg (software)");
    }

    private static native long nativeInit(int width, int height, int threads, int format);

    private static native int nativeSetWindow(long handle, Surface surface, boolean showLast);

    private static native int nativeDecode(long handle, byte[] data, int off, int len, boolean show);

    private static native int nativeRenderPending(long handle);

    private static native void nativeSetFast(long handle, boolean fast);

    private static native void nativeForgetLast(long handle);

    private static native void nativeRelease(long handle);
}
