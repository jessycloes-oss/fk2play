package com.fk2play.protocol;

/** Evenements remontes par la session (appeles depuis le thread de lecture USB). */
public interface DongleListener {
    /** phoneType : 3 = CarPlay, 4 = Android Auto, 1 = Android mirror, 5 = iPhone mirror. */
    void onPhoneConnected(int phoneType, boolean wireless);

    void onPhoneDisconnected();

    /** Trame H.264 Annex-B (data[off..off+len]). */
    void onVideo(int width, int height, byte[] data, int off, int len);

    /** PCM 16 bits little-endian. */
    void onAudio(int decodeType, byte[] data, int off, int len);

    /** Commande audio (debut/fin media, appel, Siri, navigation...). */
    void onAudioCommand(int command);

    void onStatus(String message);

    void onError(Exception e);
}
