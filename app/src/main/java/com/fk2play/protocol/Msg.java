package com.fk2play.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Format des messages du dongle Carlinkit (CPC200-CCPA / Autokit).
 * En-tete 16 octets little-endian : magic, longueur payload, type, ~type.
 * Protocole retro-ingenierie par la communaute (pycarplay, node-carplay).
 */
public final class Msg {
    public static final int MAGIC = 0x55AA55AA;
    public static final int HEADER_SIZE = 16;

    // Types de messages
    public static final int OPEN = 0x01;
    public static final int PLUGGED = 0x02;
    public static final int PHASE = 0x03;
    public static final int UNPLUGGED = 0x04;
    public static final int TOUCH = 0x05;
    public static final int VIDEO_DATA = 0x06;
    public static final int AUDIO_DATA = 0x07;
    public static final int COMMAND = 0x08;
    public static final int LOGO_TYPE = 0x09;
    public static final int BT_ADDRESS = 0x0A;
    public static final int BT_PIN = 0x0C;
    public static final int BT_DEVICE_NAME = 0x0D;
    public static final int WIFI_DEVICE_NAME = 0x0E;
    public static final int DISCONNECT_PHONE = 0x0F;
    public static final int BT_PAIRED_LIST = 0x12;
    public static final int MANUFACTURER_INFO = 0x14;
    public static final int CLOSE_DONGLE = 0x15;
    public static final int MULTI_TOUCH = 0x17;
    public static final int BOX_SETTINGS = 0x19;
    public static final int MEDIA_DATA = 0x2A;
    public static final int SEND_FILE = 0x99;
    public static final int HEARTBEAT = 0xAA;
    public static final int SOFTWARE_VERSION = 0xCC;

    // Commandes (message COMMAND, payload = uint32)
    public static final int CMD_SIRI = 5;
    public static final int CMD_MIC_CAR = 7;
    public static final int CMD_REQUEST_KEYFRAME = 12;
    public static final int CMD_MIC_BOX = 15;
    public static final int CMD_NIGHT_ON = 16;
    public static final int CMD_NIGHT_OFF = 17;
    public static final int CMD_AUDIO_TRANSFER_ON = 22;  // audio via Bluetooth
    public static final int CMD_AUDIO_TRANSFER_OFF = 23; // audio via USB (vers l'autoradio)
    public static final int CMD_WIFI_24G = 24;
    public static final int CMD_WIFI_5G = 25;
    public static final int CMD_LEFT = 100;
    public static final int CMD_RIGHT = 101;
    public static final int CMD_SELECT_DOWN = 104;
    public static final int CMD_SELECT_UP = 105;
    public static final int CMD_BACK = 106;
    public static final int CMD_HOME = 200;
    public static final int CMD_PLAY = 201;
    public static final int CMD_PAUSE = 202;
    public static final int CMD_NEXT = 204;
    public static final int CMD_PREV = 205;
    public static final int CMD_WIFI_ENABLE = 1000;
    public static final int CMD_WIFI_CONNECT = 1002;

    // Commandes audio (AUDIO_DATA avec payload de 1 octet)
    public static final int AUDIO_OUTPUT_START = 1;
    public static final int AUDIO_OUTPUT_STOP = 2;
    public static final int AUDIO_INPUT_CONFIG = 3;
    public static final int AUDIO_PHONECALL_START = 4;
    public static final int AUDIO_PHONECALL_STOP = 5;
    public static final int AUDIO_NAVI_START = 6;
    public static final int AUDIO_NAVI_STOP = 7;
    public static final int AUDIO_SIRI_START = 8;
    public static final int AUDIO_SIRI_STOP = 9;
    public static final int AUDIO_MEDIA_START = 10;
    public static final int AUDIO_MEDIA_STOP = 11;

    // Actions tactiles (message TOUCH)
    public static final int TOUCH_DOWN = 14;
    public static final int TOUCH_MOVE = 15;
    public static final int TOUCH_UP = 16;

    public final int type;
    public final byte[] payload;

    public Msg(int type, byte[] payload) {
        this.type = type;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Serialise en-tete + payload. */
    public byte[] encode() {
        ByteBuffer b = le(HEADER_SIZE + payload.length);
        b.putInt(MAGIC);
        b.putInt(payload.length);
        b.putInt(type);
        b.putInt(~type);
        b.put(payload);
        return b.array();
    }

    /** Decode un en-tete ; renvoie {type, longueur} ou leve une exception. */
    public static int[] decodeHeader(byte[] h) throws ProtocolException {
        if (h.length < HEADER_SIZE) throw new ProtocolException("En-tete trop court");
        ByteBuffer b = ByteBuffer.wrap(h, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int magic = b.getInt();
        int len = b.getInt();
        int type = b.getInt();
        int check = b.getInt();
        if (magic != MAGIC) throw new ProtocolException("Magic invalide: " + Integer.toHexString(magic));
        if (check != ~type) throw new ProtocolException("Controle de type invalide pour type " + type);
        if (len < 0 || len > 4 * 1024 * 1024) throw new ProtocolException("Longueur invalide: " + len);
        return new int[]{type, len};
    }

    public static class ProtocolException extends Exception {
        public ProtocolException(String m) { super(m); }
    }
}
