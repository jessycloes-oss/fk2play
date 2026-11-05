package com.fk2play.video;

import android.view.Surface;

/**
 * Decodage logiciel FFmpeg via JNI (libfk2video.so).
 * Indispensable sur Android 4.0.4 (API 15) : MediaCodec n'existe qu'a partir de l'API 16.
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

    @Override
    public boolean init(Surface surface, int width, int height) {
        if (!loaded) return false;
        handle = nativeInit(surface, width, height, Runtime.getRuntime().availableProcessors());
        return handle != 0;
    }

    @Override
    public boolean decode(byte[] data, int off, int len) {
        return handle != 0 && nativeDecode(handle, data, off, len) >= 0;
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
        return "FFmpeg (logiciel)";
    }

    private static native long nativeInit(Surface surface, int width, int height, int threads);

    private static native int nativeDecode(long handle, byte[] data, int off, int len);

    private static native void nativeRelease(long handle);
}
