package com.fk2play;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/** Reglages (PreferenceActivity : disponible des Android 1.0). */
public class SettingsActivity extends PreferenceActivity {
    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        addPreferencesFromResource(R.xml.preferences);
    }
}
