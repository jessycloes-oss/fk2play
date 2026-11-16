package com.fk2play.sim;

import android.util.Log;

import com.fk2play.protocol.Msg;
import com.fk2play.protocol.Transport;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;

/**
 * Faux dongle pour les tests (APK de test uniquement, jamais actif dans la version normale).
 * Rejoue un flux enregistre « dongle -> autoradio » au rythme reel. Format du fichier :
 * suite de [delai ms (int32 BE)][longueur (int32 BE)][octets]. Les envois de l'appli sont
 * journalises (tag FK2Sim) pour verifier la sequence d'ouverture, le tactile, le micro...
 *
 * Banc d'essai : si un fichier « &lt;flux&gt;.idr » existe, une demande d'image cle de l'appli
 * est servie comme par un vrai dongle (image cle de remplacement prise dans ce fichier),
 * et chaque ecriture USB coute ~1 ms comme un vrai transfert bulk.
 */
public class SimTransport implements Transport {
    private static final String TAG = "FK2Sim";
    private final DataInputStream in;
    private byte[] record = new byte[0];
    private int pos;
    private volatile boolean closed;
    private int sentCount;

    private RandomAccessFile idr;
    private final HashMap<Integer, long[]> idrIndex = new HashMap<Integer, long[]>();
    private int videoIndex = -1;
    private volatile boolean keyframeWanted;
    private final boolean realisticUsb;
    /** Horloge absolue du flux : comme un vrai dongle, il ne ralentit pas si l'appli prend du retard. */
    private long startMs, streamMs;

    public SimTransport(String path) throws IOException {
        InputStream f = new BufferedInputStream(new FileInputStream(path), 1 << 16);
        in = new DataInputStream(f);
        File side = new File(path + ".idr");
        if (side.exists()) {
            idr = new RandomAccessFile(side, "r");
            long p = 0, n = idr.length();
            while (p + 8 <= n) {
                idr.seek(p);
                int idx = idr.readInt(), len = idr.readInt();
                idrIndex.put(idx, new long[]{p + 8, len});
                p += 8 + len;
            }
        }
        realisticUsb = idr != null || path.contains("bench");
        startMs = android.os.SystemClock.uptimeMillis();
        Log.i(TAG, "Simulation : lecture de " + path + (idr != null ? " (" + idrIndex.size() + " images cles de secours)" : ""));
    }

    @Override
    public void readFully(byte[] buf, int off, int len) throws IOException {
        int done = 0;
        while (done < len) {
            if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Transport ferme");
            if (pos >= record.length) nextRecord();
            int n = Math.min(len - done, record.length - pos);
            System.arraycopy(record, pos, buf, off + done, n);
            pos += n;
            done += n;
        }
    }

    private void nextRecord() throws IOException {
        int delay, len;
        try {
            delay = in.readInt();
            len = in.readInt();
        } catch (EOFException e) {
            throw new IOException("Fin du flux simule (equivaut a un dongle debranche)");
        }
        streamMs += delay;
        long wait = realisticUsb ? startMs + streamMs - android.os.SystemClock.uptimeMillis() : delay;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Transport ferme");
            }
        }
        record = new byte[len];
        in.readFully(record);
        pos = 0;
        if (len > 36 && (record[8] & 0xFF) == Msg.VIDEO_DATA) {
            videoIndex++;
            if (keyframeWanted && idrIndex.containsKey(videoIndex)) {
                keyframeWanted = false;
                record = withKeyframe(record, idrIndex.get(videoIndex));
            }
        }
    }

    /** Remplace l'image H.264 du message video par l'image cle de secours (meme instant). */
    private byte[] withKeyframe(byte[] msg, long[] where) throws IOException {
        int au = (int) where[1];
        byte[] out = new byte[36 + au];
        System.arraycopy(msg, 0, out, 0, 36);
        idr.seek(where[0]);
        idr.readFully(out, 36, au);
        putLe(out, 4, 20 + au);   // longueur du message
        putLe(out, 28, au);       // longueur de l'image
        return out;
    }

    private static void putLe(byte[] b, int off, int v) {
        b[off] = (byte) v; b[off + 1] = (byte) (v >> 8); b[off + 2] = (byte) (v >> 16); b[off + 3] = (byte) (v >> 24);
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (closed) throw new IOException("Transport ferme");
        sentCount++;
        if (realisticUsb) {
            try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        try {
            int[] tl = Msg.decodeHeader(data);
            if (data.length != Msg.HEADER_SIZE + tl[1]) throw new IOException("Longueur incoherente");
            if (tl[0] != Msg.HEARTBEAT && tl[0] != Msg.AUDIO_DATA) {
                int arg = tl[1] >= 4 ? (data[16] & 0xFF) | (data[17] & 0xFF) << 8
                        | (data[18] & 0xFF) << 16 | (data[19] & 0xFF) << 24 : -1;
                if (tl[0] == Msg.COMMAND && arg == Msg.CMD_REQUEST_KEYFRAME) keyframeWanted = true;
                if (tl[0] != Msg.TOUCH) {
                    Log.i(TAG, "appli -> dongle : type 0x" + Integer.toHexString(tl[0]) + " len " + tl[1] + " arg " + arg);
                }
            } else if (tl[0] == Msg.AUDIO_DATA && sentCount % 25 == 0) {
                Log.i(TAG, "appli -> dongle : micro " + tl[1] + " octets");
            } else if (tl[0] == Msg.HEARTBEAT) {
                Log.i(TAG, "appli -> dongle : heartbeat");
            }
        } catch (Msg.ProtocolException e) {
            throw new IOException("Message invalide envoye : " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { in.close(); } catch (IOException ignored) { }
        try { if (idr != null) idr.close(); } catch (IOException ignored) { }
        Log.i(TAG, "Simulation : transport ferme apres " + sentCount + " envois");
    }
}
