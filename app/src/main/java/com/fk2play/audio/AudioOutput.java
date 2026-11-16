package com.fk2play.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;
import android.util.SparseArray;

import com.fk2play.Stats;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Lecture du PCM envoye par le dongle. Un AudioTrack par format (musique 44,1/48 kHz,
 * guidage, appel 8/16 kHz...), car CarPlay peut melanger plusieurs flux.
 *
 * 1.7 : l'ecriture dans AudioTrack (bloquante quand son tampon est plein) se fait dans un
 * thread audio dedie. Avant, elle bloquait le thread USB, donc la video : apres une coupure
 * Wi-Fi (rafale de son) ou a cause du leger decalage d'horloge iPhone/autoradio, l'image
 * prenait du retard. Si le son s'accumule malgre tout, les blocs les plus anciens sont jetes.
 */
public class AudioOutput {
    /** ~0,6 s de son en attente au maximum (blocs de 20 a 60 ms). */
    private static final int POOL = 24;

    private static final class Chunk {
        byte[] d = new byte[8192];
        int len, type;
        boolean stop;
    }

    private final SparseArray<AudioTrack> tracks = new SparseArray<AudioTrack>();
    private final ArrayBlockingQueue<Chunk> queue = new ArrayBlockingQueue<Chunk>(POOL + 1);
    private final ArrayBlockingQueue<Chunk> free = new ArrayBlockingQueue<Chunk>(POOL + 1);
    private final Chunk stopMarker = new Chunk();
    private Thread thread;
    private volatile boolean running;

    public AudioOutput() {
        stopMarker.stop = true;
        for (int i = 0; i < POOL; i++) free.offer(new Chunk());
    }

    /** decodeType -> {frequence, canaux}. */
    private static int[] format(int decodeType) {
        switch (decodeType) {
            case 1: case 2: return new int[]{44100, 2};
            case 3: return new int[]{8000, 1};
            case 4: return new int[]{48000, 2};
            case 5: return new int[]{16000, 1};
            case 6: return new int[]{24000, 1};
            case 7: return new int[]{16000, 2};
            default: return null;
        }
    }

    /** Thread USB : ne bloque jamais. */
    public void write(int decodeType, byte[] data, int off, int len) {
        if (format(decodeType) == null) return;
        ensureThread();
        long t0 = System.nanoTime();
        Chunk c = free.poll();
        if (c == null) {
            // Le son a pris trop de retard : on jette le bloc le plus ancien.
            c = queue.poll();
            if (c == null || c.stop) {
                if (c != null) queue.offer(c);
                return;
            }
        }
        len &= ~1;
        if (c.d.length < len) c.d = new byte[len];
        System.arraycopy(data, off, c.d, 0, len);
        c.len = len;
        c.type = decodeType;
        if (!queue.offer(c)) free.offer(c);
        Stats.audioWrite(System.nanoTime() - t0);
    }

    /** Fin d'un flux : on libere les pistes pour rendre la main au Honda Connect. */
    public void stopAll() {
        Chunk c;
        while ((c = queue.poll()) != null) if (!c.stop) free.offer(c);
        if (running) queue.offer(stopMarker);
        else releaseTracks();
    }

    /** Fin du service : arret du thread audio. */
    public synchronized void release() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
        releaseTracks();
    }

    private synchronized void ensureThread() {
        if (thread != null) return;
        running = true;
        thread = new Thread(new Runnable() {
            public void run() { loop(); }
        }, "audio-out");
        thread.start();
    }

    private void loop() {
        // Priorite audio standard d'Android : tres peu de calcul, mais jamais en retard.
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        try {
            while (running) {
                Chunk c = queue.poll(500, TimeUnit.MILLISECONDS);
                if (c == null) continue;
                if (c.stop) { releaseTracks(); continue; }
                try {
                    AudioTrack t = track(c.type);
                    if (t != null) t.write(c.d, 0, c.len);
                } catch (Exception ignored) {
                } finally {
                    free.offer(c);
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    private AudioTrack track(int decodeType) {
        AudioTrack t;
        synchronized (tracks) { t = tracks.get(decodeType); }
        if (t != null) return t;
        int[] f = format(decodeType);
        int ch = f[1] == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int min = AudioTrack.getMinBufferSize(f[0], ch, AudioFormat.ENCODING_PCM_16BIT);
        t = new AudioTrack(AudioManager.STREAM_MUSIC, f[0], ch,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 4, 16384), AudioTrack.MODE_STREAM);
        if (t.getState() != AudioTrack.STATE_INITIALIZED) {
            t.release();
            return null;
        }
        t.play();
        synchronized (tracks) { tracks.put(decodeType, t); }
        return t;
    }

    private void releaseTracks() {
        synchronized (tracks) {
            for (int i = 0; i < tracks.size(); i++) {
                AudioTrack t = tracks.valueAt(i);
                try { t.pause(); t.flush(); t.release(); } catch (Exception ignored) { }
            }
            tracks.clear();
        }
    }
}
