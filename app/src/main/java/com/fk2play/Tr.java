package com.fk2play;

import java.util.Locale;

/**
 * Textes affiches par le code Java (etats, diagnostic, test du micro) en francais ou en anglais
 * selon la langue de l'autoradio. Les reglages, eux, utilisent res/values (anglais) et res/values-fr.
 *
 * Texts shown by the Java code in French or English, following the head unit language.
 */
public final class Tr {
    private Tr() {}

    /** true si l'autoradio est regle en francais. */
    public static boolean fr() {
        return "fr".equals(Locale.getDefault().getLanguage());
    }

    /** @return le texte francais si l'autoradio est en francais, sinon le texte anglais. */
    public static String s(String fr, String en) {
        return fr() ? fr : en;
    }
}
