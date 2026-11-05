package com.fk2play.sim;

import android.util.Log;

import com.fk2play.protocol.Msg;
import com.fk2play.protocol.Transport;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;

/**
 * Faux dongle pour les tests (APK de test uniquement, jamais actif dans la version normale).
 * Rejoue un flux enregistre « dongle -> autoradio » au rythme reel. Format du fichier :
 * suite de [delai ms (int32 BE)][longueur (int32 BE)][octets]. Les envois de l'appli sont
 * journalises (tag FK2Sim) pour verifier la sequence d'ouverture, le tactile, le micro...
 */
public class SimTransport implements Transport {
    private static final String TAG = "FK2Sim";
    private final DataInputStream in;
    private byte[] record = new byte[0];
    private int pos;
    private volatile boolean closed;
    private int sentCount;

    public SimTransport(String path) throws IOException {
        InputStream f = new BufferedInputStream(new FileInputStream(path), 1 << 16);
        in = new DataInputStream(f);
        Log.i(TAG, "Simulation : lecture de " + path);
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
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Transport ferme");
            }
        }
        record = new byte[len];
        in.readFully(record);
        pos = 0;
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (closed) throw new IOException("Transport ferme");
        sentCount++;
        try {
            int[] tl = Msg.decodeHeader(data);
            if (data.length != Msg.HEADER_SIZE + tl[1]) throw new IOException("Longueur incoherente");
            if (tl[0] != Msg.HEARTBEAT && tl[0] != Msg.AUDIO_DATA) {
                int arg = tl[1] >= 4 ? (data[16] & 0xFF) | (data[17] & 0xFF) << 8
                        | (data[18] & 0xFF) << 16 | (data[19] & 0xFF) << 24 : -1;
                Log.i(TAG, "appli -> dongle : type 0x" + Integer.toHexString(tl[0]) + " len " + tl[1] + " arg " + arg);
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
        Log.i(TAG, "Simulation : transport ferme apres " + sentCount + " envois");
    }
}
