package com.fk2play;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Reglages (PreferenceActivity : disponible des Android 1.0).
 * Accessible par la touche MENU dans FK2Play et par sa propre icone « Reglages FK2Play ».
 */
public class SettingsActivity extends PreferenceActivity {
    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        addPreferencesFromResource(R.xml.preferences);
    }

    /** Recharge l'ecran au retour du test du micro, qui peut avoir change des reglages. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onResume() {
        super.onResume();
        setPreferenceScreen(null);
        addPreferencesFromResource(R.xml.preferences);
    }
}
