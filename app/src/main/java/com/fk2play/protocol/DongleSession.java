package com.fk2play.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pilote une session avec le dongle : sequence d'initialisation, battement de coeur
 * toutes les 2 s, lecture et distribution des messages.
 */
public class DongleSession {
    private static final long HEARTBEAT_MS = 2000;

    private final Transport transport;
    private final DongleConfig config;
    private final DongleListener listener;
    private final Object writeLock = new Object();

    private volatile boolean running;
    private Thread readerThread;
    private Thread heartbeatThread;
    private Thread closerThread;

    public DongleSession(Transport transport, DongleConfig config, DongleListener listener) {
        this.transport = transport;
        this.config = config;
        this.listener = listener;
    }

    public void start() throws IOException {
        running = true;
        sendInit();

        readerThread = new Thread(new Runnable() {
            public void run() { readLoop(); }
        }, "dongle-reader");
        readerThread.setPriority(Thread.MAX_PRIORITY);
        readerThread.start();

        heartbeatThread = new Thread(new Runnable() {
            public void run() { heartbeatLoop(); }
        }, "dongle-heartbeat");
        heartbeatThread.start();
    }

    /** Sequence d'ouverture (ordre repris de node-carplay). */
    void sendInit() throws IOException {
        DongleConfig c = config;
        send(Messages.sendNumber("/tmp/screen_dpi", c.dpi));
        send(Messages.open(c));
        send(Messages.sendNumber("/tmp/night_mode", c.nightMode ? 1 : 0));
        send(Messages.sendNumber("/tmp/hand_drive_mode", c.handDrive));
        send(Messages.sendNumber("/tmp/charge_mode", 1));
        send(Messages.sendString("/etc/box_name", c.boxName));
        send(Messages.boxSettings(c, System.currentTimeMillis() / 1000L));
        send(Messages.command(Msg.CMD_WIFI_ENABLE));
        send(Messages.command(c.wifi5g ? Msg.CMD_WIFI_5G : Msg.CMD_WIFI_24G));
        send(Messages.command(c.useCarMic ? Msg.CMD_MIC_CAR : Msg.CMD_MIC_BOX));
        send(Messages.command(c.audioOverUsb ? Msg.CMD_AUDIO_TRANSFER_OFF : Msg.CMD_AUDIO_TRANSFER_ON));
        listener.onStatus("Dongle initialise, en attente de l'iPhone...");
        // Demande de connexion auto au dernier iPhone apparie (sans fil)
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(1000);
                    if (running) send(Messages.command(Msg.CMD_WIFI_CONNECT));
                } catch (Exception ignored) { }
            }
        }, "dongle-autoconnect").start();
    }

    public void send(Msg m) throws IOException {
        byte[] data = m.encode();
        synchronized (writeLock) {
            transport.write(data);
        }
    }

    /** Envoi « sans echec » depuis l'UI (les erreurs remontent via onError). */
    public void sendQuietly(Msg m) {
        try {
            send(m);
        } catch (IOException e) {
            if (running) listener.onError(e);
        }
    }

    public void sendTouch(int action, float x, float y) {
        sendQuietly(Messages.touch(action, x, y));
    }

    public void sendCommand(int cmd) {
        sendQuietly(Messages.command(cmd));
    }

    /**
     * Arret non bloquant (appelable depuis le thread UI sans risque d'ANR).
     * Le lien USB n'est ferme qu'une fois le thread de lecture sorti : fermer la connexion
     * pendant qu'un autre thread est dans bulkTransfer() peut faire planter le natif d'Android 4.0.
     */
    public synchronized void stop() {
        if (closerThread != null) return;
        running = false;
        final Thread r = readerThread, h = heartbeatThread;
        if (h != null) h.interrupt();
        if (r != null) r.interrupt();
        closerThread = new Thread(new Runnable() {
            public void run() {
                try {
                    if (r != null) r.join(2000);
                    if (h != null) h.join(1500);
                } catch (InterruptedException ignored) { }
                synchronized (writeLock) {
                    transport.close();
                }
            }
        }, "dongle-closer");
        closerThread.start();
    }

    /** Attend la liberation complete du dongle (a appeler hors thread UI avant de rouvrir). */
    public void awaitTermination(long ms) {
        Thread c;
        synchronized (this) { c = closerThread; }
        if (c == null) return;
        try { c.join(ms); } catch (InterruptedException ignored) { }
    }

    public boolean isRunning() {
        return running;
    }

    private void heartbeatLoop() {
        while (running) {
            try {
                Thread.sleep(HEARTBEAT_MS);
                send(Messages.heartbeat());
            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                if (running) listener.onError(e);
                return;
            }
        }
    }

    private void readLoop() {
        byte[] header = new byte[Msg.HEADER_SIZE];
        byte[] payload = new byte[512 * 1024];
        while (running) {
            try {
                transport.readFully(header, 0, Msg.HEADER_SIZE);
                int[] tl = Msg.decodeHeader(header);
                int type = tl[0], len = tl[1];
                if (len > payload.length) payload = new byte[len];
                if (len > 0) transport.readFully(payload, 0, len);
                dispatch(type, payload, len);
            } catch (Msg.ProtocolException e) {
                // Desynchronisation : on signale et on continue (le prochain en-tete valide resynchronise
                // rarement ; en pratique il faut relancer la session)
                listener.onError(e);
                running = false;
            } catch (IOException e) {
                if (running) listener.onError(e);
                running = false;
            } catch (RuntimeException e) {
                listener.onError(e);
            }
        }
    }

    void dispatch(int type, byte[] p, int len) {
        ByteBuffer b = ByteBuffer.wrap(p, 0, len).order(ByteOrder.LITTLE_ENDIAN);
        switch (type) {
            case Msg.PLUGGED: {
                int phoneType = len >= 4 ? b.getInt() : 0;
                boolean wireless = len >= 8 && b.getInt() == 1;
                listener.onPhoneConnected(phoneType, wireless);
                break;
            }
            case Msg.UNPLUGGED:
                listener.onPhoneDisconnected();
                break;
            case Msg.VIDEO_DATA: {
                if (len <= 20) break;
                int w = b.getInt();
                int h = b.getInt();
                listener.onVideo(w, h, p, 20, len - 20);
                break;
            }
            case Msg.AUDIO_DATA: {
                if (len < 12) break;
                int decodeType = b.getInt();
                b.getFloat(); // volume
                b.getInt();   // audioType
                int rest = len - 12;
                if (rest == 1) {
                    listener.onAudioCommand(p[12] & 0xFF);
                } else if (rest == 4) {
                    // volumeDuration : ignore
                } else if (rest > 0) {
                    listener.onAudio(decodeType, p, 12, rest);
                }
                break;
            }
            case Msg.PHASE: {
                int phase = len >= 4 ? b.getInt() : -1;
                listener.onStatus("Phase dongle : " + phase);
                break;
            }
            case Msg.SOFTWARE_VERSION:
                listener.onStatus("Firmware dongle : " + new String(p, 0, len).trim());
                break;
            case Msg.WIFI_DEVICE_NAME:
            case Msg.BT_DEVICE_NAME:
                listener.onStatus("Appareil : " + new String(p, 0, len).trim());
                break;
            default:
                // HEARTBEAT, COMMAND, MEDIA_DATA, etc. : ignores
                break;
        }
    }
}
