package com.fk2play.video;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Decodage materiel, uniquement si Android >= 4.1 (API 16).
 * Sert sur un autoradio plus recent ou une tablette de test ; jamais utilise sur Android 4.0.4.
 */
@TargetApi(16)
public class MediaCodecDecoder implements VideoDecoder {
    private MediaCodec codec;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private long pts;

    @Override
    public boolean init(Surface surface, int width, int height) {
        try {
            MediaFormat f = MediaFormat.createVideoFormat("video/avc", width, height);
            codec = MediaCodec.createDecoderByType("video/avc");
            codec.configure(f, surface, null, 0);
            codec.start();
            return true;
        } catch (Exception e) {
            release();
            return false;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean decode(byte[] data, int off, int len) {
        try {
            int idx = codec.dequeueInputBuffer(20000);
            if (idx >= 0) {
                ByteBuffer in = codec.getInputBuffers()[idx];
                in.clear();
                in.put(data, off, len);
                pts += 33333;
                codec.queueInputBuffer(idx, 0, len, pts, 0);
            }
            int out;
            while ((out = codec.dequeueOutputBuffer(info, 0)) >= 0) {
                codec.releaseOutputBuffer(out, true);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void release() {
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) { }
            try { codec.release(); } catch (Exception ignored) { }
            codec = null;
        }
    }

    @Override
    public String name() {
        return "MediaCodec (materiel)";
    }
}
