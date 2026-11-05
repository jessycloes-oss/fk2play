package com.fk2play.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.fk2play.protocol.DongleSession;
import com.fk2play.protocol.Messages;
import com.fk2play.protocol.Msg;
import android.util.Log;

/** Capture le micro de l'autoradio (16 kHz mono) pendant Siri et les appels. */
public class MicInput {
    private static final int RATE = 16000;

    private final DongleSession session;
    private volatile boolean running;
    private Thread thread;

    public MicInput(DongleSession session) {
        this.session = session;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            public void run() { loop(); }
        }, "mic");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    /** Frequences essayees : 16 kHz natif, sinon 48 kHz (divise par 3) ou 8 kHz (double). */
    private static final int[] RATES = {16000, 48000, 8000};

    private static AudioRecord open(int rate) {
        try {
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) return null;
            AudioRecord r = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192));
            if (r.getState() == AudioRecord.STATE_INITIALIZED) return r;
            r.release();
        } catch (Exception ignored) { }
        return null;
    }

    private void loop() {
        AudioRecord rec = null;
        int rate = 0;
        for (int r : RATES) {
            rec = open(r);
            if (rec != null) { rate = r; break; }
        }
        if (rec == null) {
            // Micro de l'autoradio inaccessible : on bascule sur le micro du dongle (modele CCPA-MIC).
            Log.w("FK2Play", "Micro de la voiture indisponible -> micro du dongle");
            session.sendQuietly(Messages.command(Msg.CMD_MIC_BOX));
            running = false;
            return;
        }
        Log.i("FK2Play", "Micro ouvert a " + rate + " Hz");
        short[] buf = new short[rate / 25];      // 40 ms
        short[] out = new short[640];            // 40 ms a 16 kHz
        try {
            rec.startRecording();
            while (running) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) continue;
                int m;
                if (rate == 16000) {
                    session.sendQuietly(Messages.micAudio(buf, n));
                    continue;
                } else if (rate == 48000) {
                    m = n / 3;
                    for (int i = 0; i < m; i++) out[i] = (short) ((buf[3 * i] + buf[3 * i + 1] + buf[3 * i + 2]) / 3);
                } else {
                    m = Math.min(n * 2, out.length);
                    for (int i = 0; i < m; i++) out[i] = buf[i / 2];
                }
                session.sendQuietly(Messages.micAudio(out, m));
            }
        } catch (Exception e) {
            Log.w("FK2Play", "Micro : " + e);
        } finally {
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
        }
    }
}
