package com.fk2play.video;

import android.view.Surface;

/** Decodeur H.264 qui affiche sur une Surface. */
public interface VideoDecoder {
    /**
     * Resultats de decode() : rien encore ; image deposee pour le thread d'affichage ;
     * decodee sans affichage ; deposee en remplacant une image jamais affichee ; deja affichee.
     */
    int NOTHING = 0, QUEUED = 1, DECODED = 2, REPLACED = 3, SHOWN = 4;

    /** Prepare le decodeur ; surface peut etre null si keepsStateWithoutSurface(). */
    boolean init(Surface surface, int width, int height);

    /** true : le decodeur survit sans ecran et peut reafficher sa derniere image (FFmpeg). */
    boolean keepsStateWithoutSurface();

    /** Change d'ecran ; renvoie true si la derniere image a pu etre reaffichee tout de suite. */
    boolean setSurface(Surface surface, boolean showLast);

    /** Decode une unite d'acces Annex-B ; < 0 en cas d'erreur grave. */
    int decode(byte[] data, int off, int len, boolean show);

    /** Thread d'affichage : affiche l'image deposee par decode() ; true si affichee. */
    boolean renderPending();

    /** Mode allege (moins de calcul par image) quand l'autoradio n'arrive plus a suivre. */
    void setFast(boolean fast);

    /** La derniere image ne correspond plus a l'ecran de l'iPhone. */
    void forgetLast();

    void release();

    String name();
}
