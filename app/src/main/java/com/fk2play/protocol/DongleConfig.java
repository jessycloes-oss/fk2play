package com.fk2play.protocol;

/** Reglages de session. Valeurs par defaut adaptees a l'ecran 7" (800x480) du Honda Connect. */
public class DongleConfig {
    public int width = 800;
    public int height = 480;
    /** 30 i/s : le decodage logiciel du Tegra sous Android 4.0.4 ne suivra pas 60 i/s. */
    public int fps = 30;
    public int dpi = 160;
    public boolean nightMode = false;
    /** 0 = conduite a gauche (Europe continentale), 1 = conduite a droite (UK). */
    public int handDrive = 0;
    public String boxName = "FK2Play";
    public boolean wifi5g = true;
    /** true = micro de l'autoradio (AudioRecord) ; false = micro integre au dongle s'il en a un. */
    public boolean useCarMic = true;
    /** true = audio CarPlay renvoye par USB et joue par l'application. */
    public boolean audioOverUsb = true;
    public int mediaDelayMs = 300;
}
