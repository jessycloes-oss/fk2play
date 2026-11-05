package com.fk2play.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.SparseArray;

/**
 * Lecture du PCM envoye par le dongle. Un AudioTrack par format (musique 44,1/48 kHz,
 * guidage, appel 8/16 kHz...), car CarPlay peut melanger plusieurs flux.
 */
public class AudioOutput {
    private final SparseArray<AudioTrack> tracks = new SparseArray<AudioTrack>();

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

    public synchronized void write(int decodeType, byte[] data, int off, int len) {
        AudioTrack t = tracks.get(decodeType);
        if (t == null) {
            int[] f = format(decodeType);
            if (f == null) return;
            int ch = f[1] == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
            int min = AudioTrack.getMinBufferSize(f[0], ch, AudioFormat.ENCODING_PCM_16BIT);
            t = new AudioTrack(AudioManager.STREAM_MUSIC, f[0], ch,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 4, 16384), AudioTrack.MODE_STREAM);
            if (t.getState() != AudioTrack.STATE_INITIALIZED) {
                t.release();
                return;
            }
            t.play();
            tracks.put(decodeType, t);
        }
        t.write(data, off, len & ~1);
    }

    /** Fin d'un flux : on libere les pistes pour rendre la main au Honda Connect. */
    public synchronized void stopAll() {
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack t = tracks.valueAt(i);
            try { t.pause(); t.flush(); t.release(); } catch (Exception ignored) { }
        }
        tracks.clear();
    }
}
