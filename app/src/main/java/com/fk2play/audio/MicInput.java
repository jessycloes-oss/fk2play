package com.fk2play.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.preference.PreferenceManager;
import android.util.Log;

import com.fk2play.protocol.DongleSession;
import com.fk2play.protocol.Messages;
import com.fk2play.protocol.Msg;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Micro de l'autoradio pendant Siri et les appels.
 *
 * Deux threads : la capture remplit une file de trames de 40 ms (16 kHz mono), et l'envoi
 * transmet au dongle une trame toutes les 40 ms, du silence si la capture n'a rien fourni.
 * Le dongle recoit donc toujours un flux regulier : un micro muet ou bloque ne peut plus
 * figer l'appel (cas constate sur Civic FK2 : plus aucun son d'appel micro voiture coche).
 */
public class MicInput {
    private static final String TAG = "FK2Play";
    public static final int FRAME = 640;               // 40 ms a 16 kHz
    private static final short[] SILENCE = new short[FRAME];

    /** Sources essayees en mode automatique (valeurs de MediaRecorder.AudioSource). */
    public static final int[] AUTO_SOURCES = {1 /*MIC*/, 7 /*VOICE_COMMUNICATION*/,
            6 /*VOICE_RECOGNITION*/, 5 /*CAMCORDER*/, 0 /*DEFAULT*/};
    public static final int[] RATES = {16000, 48000, 8000};

    private final DongleSession session;
    private final int prefSource;   // -1 = automatique
    private final int prefRate;     // 0 = automatique
    private final ArrayBlockingQueue<short[]> frames = new ArrayBlockingQueue<short[]>(10);
    private volatile boolean running;
    private Thread capture, sender;

    public MicInput(Context ctx, DongleSession session) {
        this.session = session;
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefSource = parse(p.getString("mic_source", "auto"), -1);
        prefRate = parse(p.getString("mic_rate", "auto"), 0);
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        frames.clear();
        capture = new Thread(new Runnable() { public void run() { captureLoop(); } }, "mic-capture");
        sender = new Thread(new Runnable() { public void run() { sendLoop(); } }, "mic-send");
        capture.start();
        sender.start();
    }

    public synchronized void stop() {
        running = false;
        if (capture != null) capture.interrupt();
        if (sender != null) sender.interrupt();
        capture = sender = null;
    }

    /** Ouvre un AudioRecord ; null si la combinaison source/frequence est refusee. */
    public static AudioRecord open(int source, int rate) {
        try {
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) return null;
            AudioRecord r = new AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192));
            if (r.getState() == AudioRecord.STATE_INITIALIZED) return r;
            r.release();
        } catch (Exception ignored) { }
        return null;
    }

    private void captureLoop() {
        AudioRecord rec = null;
        int rate = 0, source = -1;
        int[] sources = prefSource >= 0 ? new int[]{prefSource} : AUTO_SOURCES;
        int[] rates = prefRate > 0 ? new int[]{prefRate} : RATES;
        outer:
        for (int s : sources) {
            for (int r : rates) {
                rec = open(s, r);
                if (rec != null) { source = s; rate = r; break outer; }
            }
        }
        if (rec == null) {
            // Aucun micro accessible : le dongle prend son propre micro (modele CCPA-MIC).
            Log.w(TAG, "Micro de la voiture indisponible -> micro du dongle");
            session.sendQuietly(Messages.command(Msg.CMD_MIC_BOX));
            return;   // l'envoi de silence continue : l'appel ne se fige pas
        }
        Log.i(TAG, "Micro ouvert : source " + source + ", " + rate + " Hz");
        short[] buf = new short[rate / 25];        // 40 ms a la frequence native
        try {
            rec.startRecording();
            while (running) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) continue;
                short[] f = to16k(buf, n, rate);
                if (!frames.offer(f)) { frames.poll(); frames.offer(f); }
            }
        } catch (Exception e) {
            Log.w(TAG, "Micro : " + e);
        } finally {
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
        }
    }

    /** Convertit 40 ms de son a la frequence native en une trame 16 kHz de 640 echantillons. */
    static short[] to16k(short[] in, int n, int rate) {
        short[] out = new short[FRAME];
        if (rate == 16000) {
            System.arraycopy(in, 0, out, 0, Math.min(n, FRAME));
        } else if (rate == 48000) {
            int m = Math.min(n / 3, FRAME);
            for (int i = 0; i < m; i++) out[i] = (short) ((in[3 * i] + in[3 * i + 1] + in[3 * i + 2]) / 3);
        } else if (rate == 8000) {
            int m = Math.min(n * 2, FRAME);
            for (int i = 0; i < m; i++) out[i] = in[i / 2];
        }
        return out;
    }

    private void sendLoop() {
        long next = System.nanoTime();
        while (running) {
            next += 40000000L;
            long wait = (next - System.nanoTime()) / 1000000L;
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
            } else if (wait < -400) {
                next = System.nanoTime();   // retard important : on se recale
            }
            short[] f = frames.poll();
            session.sendQuietly(Messages.micAudio(f != null ? f : SILENCE, FRAME));
        }
    }
}
