package com.fk2play.video;

import android.view.Surface;

/** Decodeur H.264 qui affiche directement sur une Surface. */
public interface VideoDecoder {
    boolean init(Surface surface, int width, int height);

    /** Decode une unite d'acces Annex-B ; renvoie false en cas d'erreur grave. */
    boolean decode(byte[] data, int off, int len);

    void release();

    String name();
}
