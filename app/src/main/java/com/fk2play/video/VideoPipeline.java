package com.fk2play.video;

import android.os.Build;
import android.view.Surface;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * File de trames + thread de decodage. Si le decodeur prend du retard, la file est videe
 * et une image cle est redemandee au dongle (evite une latence qui s'accumule).
 */
public class VideoPipeline {
    public interface KeyframeRequester {
        void requestKeyframe();
    }

    public interface StatusSink {
        void onDecoderStatus(String s);
    }

    private static final int MAX_QUEUE = 8;

    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(MAX_QUEUE);
    private final KeyframeRequester requester;
    private final StatusSink status;
    private volatile Surface surface;
    private volatile boolean running;
    private Thread thread;
    private VideoDecoder decoder;
    private int width, height;
    private boolean waitingKeyframe = true;
    private long frames, lastFpsTime;

    public VideoPipeline(KeyframeRequester requester, StatusSink status) {
        this.requester = requester;
        this.status = status;
    }

    public synchronized void setSurface(Surface s) {
        stop();
        surface = s;
        queue.clear();
        waitingKeyframe = true;
        if (s != null) {
            start();
            requester.requestKeyframe();
        }
    }

    /** Appele depuis le thread USB. */
    public void submit(int w, int h, byte[] data, int off, int len) {
        if (!running) return;
        // Dimensions aberrantes (trame corrompue) : on ignore plutot que d'allouer un tampon geant.
        if (w < 16 || h < 16 || w > 1920 || h > 1088 || len <= 0) return;
        width = w; height = h;
        byte[] copy = new byte[len];
        System.arraycopy(data, off, copy, 0, len);
        if (!queue.offer(copy)) {
            queue.clear();
            waitingKeyframe = true;
            requester.requestKeyframe();
        }
    }

    private void start() {
        running = true;
        thread = new Thread(new Runnable() {
            public void run() { loop(); }
        }, "video-decoder");
        // Priorite normale : le decodage ne doit jamais affamer les services du Honda Connect
        // (camera de recul, radio, Bluetooth). Une image en retard est preferable.
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.start();
    }

    private void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(1000); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    private void loop() {
        int curW = 0, curH = 0;
        try {
            while (running) {
                byte[] f = queue.poll(500, TimeUnit.MILLISECONDS);
                if (f == null) continue;
                if (waitingKeyframe) {
                    if (!containsIdrOrSps(f)) continue;
                    waitingKeyframe = false;
                }
                if (decoder == null || curW != width || curH != height) {
                    if (decoder != null) decoder.release();
                    curW = width; curH = height;
                    decoder = createDecoder(surface, curW, curH);
                    if (decoder == null) {
                        status.onDecoderStatus("Aucun decodeur video disponible !");
                        return;
                    }
                    status.onDecoderStatus("Decodeur : " + decoder.name() + " " + curW + "x" + curH);
                }
                if (!decoder.decode(f, 0, f.length)) {
                    waitingKeyframe = true;
                    requester.requestKeyframe();
                }
                countFps();
            }
        } catch (InterruptedException ignored) {
        } finally {
            if (decoder != null) { decoder.release(); decoder = null; }
        }
    }

    private void countFps() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime > 5000) {
            if (lastFpsTime != 0) {
                status.onDecoderStatus(String.format("%.1f i/s", frames * 1000f / (now - lastFpsTime)));
            }
            frames = 0;
            lastFpsTime = now;
        }
    }

    private static VideoDecoder createDecoder(Surface s, int w, int h) {
        if (Build.VERSION.SDK_INT >= 16) {
            VideoDecoder d = new MediaCodecDecoder();
            if (d.init(s, w, h)) return d;
        }
        if (FfmpegDecoder.isAvailable()) {
            VideoDecoder d = new FfmpegDecoder();
            if (d.init(s, w, h)) return d;
        }
        return null;
    }

    /** Cherche une NAL SPS (7) ou IDR (5) dans un flux Annex-B. */
    static boolean containsIdrOrSps(byte[] d) {
        for (int i = 0; i + 3 < d.length; i++) {
            if (d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1) {
                int t = d[i + 3] & 0x1F;
                if (t == 5 || t == 7) return true;
                i += 2;
            }
        }
        return false;
    }
}
