package com.fk2play;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fk2play.audio.MicInput;

/**
 * Test du micro de la voiture : essaie chaque source audio d'Android a chaque frequence,
 * mesure le niveau pendant que l'utilisateur parle, et retient la meilleure combinaison.
 * Sert a savoir si le micro de l'autoradio est accessible aux applis (Honda Connect).
 */
public class MicTestActivity extends Activity {
    private static final String[] SOURCE_NAMES = {"DEFAULT", "MIC", "?", "?", "?", "CAMCORDER",
            "VOICE_RECOGNITION", "VOICE_COMMUNICATION"};
    private static final long LISTEN_MS = 1200;
    /** Niveau moyen (RMS) au-dessus duquel on considere qu'il y a vraiment un son. */
    private static final int MIN_RMS = 40;

    private final Handler ui = new Handler();
    private TextView log, verdict;
    private Button start;
    private volatile boolean running;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(Tr.s("Test du micro de la voiture", "Car microphone test"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = 16;
        root.setPadding(pad, pad, pad, pad);
        TextView intro = new TextView(this);
        intro.setTextSize(16);
        intro.setText(Tr.s("A l'arret. Appuyez sur le bouton puis PARLEZ SANS ARRET (comptez a voix haute) "
                + "pendant environ 20 secondes. FK2Play essaie toutes les facons d'ouvrir le micro "
                + "et garde celle qui vous entend le mieux.",
                "Parked only. Press the button then KEEP TALKING (count out loud) "
                + "for about 20 seconds. FK2Play tries every way of opening the microphone "
                + "and keeps the one that hears you best."));
        start = new Button(this);
        start.setText(Tr.s("Lancer le test du micro", "Start the microphone test"));
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { runTest(); }
        });
        verdict = new TextView(this);
        verdict.setTextSize(18);
        verdict.setPadding(0, 8, 0, 8);
        log = new TextView(this);
        log.setTextSize(14);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sv = new ScrollView(this);
        sv.addView(log);
        root.addView(intro);
        root.addView(start);
        root.addView(verdict);
        root.addView(sv);
        setContentView(root);
        // Tests automatises (APK de test uniquement) : lancement sans toucher l'ecran.
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && getIntent().getBooleanExtra("autostart", false)) {
            runTest();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
    }

    private void print(final String s) {
        ui.post(new Runnable() {
            public void run() { log.append(s + "\n"); }
        });
    }

    private void runTest() {
        if (running) return;
        running = true;
        start.setEnabled(false);
        log.setText("");
        verdict.setTextColor(0xFFFFD400);
        verdict.setText(Tr.s("PARLEZ MAINTENANT, sans arreter... (environ 20 secondes)", "TALK NOW, without stopping... (about 20 seconds)"));
        new Thread(new Runnable() {
            public void run() {
                int bestSource = -1, bestRate = 0, bestRms = 0;
                for (int src : MicInput.AUTO_SOURCES) {
                    for (int rate : MicInput.RATES) {
                        if (!running) return;
                        int[] r = measure(src, rate);
                        String name = src < SOURCE_NAMES.length ? SOURCE_NAMES[src] : String.valueOf(src);
                        String res;
                        if (r == null) res = Tr.s("refuse", "refused");
                        else if (r[0] < 0) res = Tr.s("bloque (aucune donnee)", "blocked (no data)");
                        else res = Tr.s("niveau ", "level ") + r[1] + (r[1] == 0 ? Tr.s(" (silence total)", " (total silence)") : "")
                                + ", " + r[0] + Tr.s("% des donnees", "% of data");
                        print(String.format("%-20s %5d Hz : %s", name, rate, res));
                        if (r != null && r[0] >= 50 && r[1] > bestRms) {
                            bestRms = r[1]; bestSource = src; bestRate = rate;
                        }
                    }
                }
                finish(bestSource, bestRate, bestRms);
            }
        }, "mic-test").start();
    }

    /** {pourcentage de donnees recues (-1 si bloque), niveau RMS}, ou null si refuse. */
    private int[] measure(final int src, final int rate) {
        final AudioRecord rec = MicInput.open(src, rate);
        if (rec == null) return null;
        final int[] result = {-1, 0};
        Thread t = new Thread(new Runnable() {
            public void run() {
                short[] buf = new short[rate / 25];
                long got = 0, sumSq = 0;
                try {
                    rec.startRecording();
                    long end = System.currentTimeMillis() + LISTEN_MS;
                    while (System.currentTimeMillis() < end) {
                        int n = rec.read(buf, 0, buf.length);
                        if (n <= 0) continue;
                        got += n;
                        for (int i = 0; i < n; i++) sumSq += (long) buf[i] * buf[i];
                    }
                    long expected = rate * LISTEN_MS / 1000;
                    result[0] = (int) Math.min(100, got * 100 / expected);
                    result[1] = got > 0 ? (int) Math.sqrt((double) sumSq / got) : 0;
                } catch (Exception ignored) {
                }
            }
        });
        t.start();
        try { t.join(LISTEN_MS + 1500); } catch (InterruptedException ignored) { }
        try { rec.stop(); } catch (Exception ignored) { }
        if (!t.isAlive()) rec.release();
        return result;
    }

    private void finish(final int src, final int rate, final int rms) {
        SharedPreferences.Editor e = PreferenceManager.getDefaultSharedPreferences(this).edit();
        final String msg;
        if (src >= 0 && rms >= MIN_RMS) {
            e.putString("mic_source", String.valueOf(src));
            e.putString("mic_rate", String.valueOf(rate));
            e.putBoolean("car_mic", true);
            String name = src < SOURCE_NAMES.length ? SOURCE_NAMES[src] : String.valueOf(src);
            msg = Tr.fr()
                    ? "\nMICRO TROUVE : " + name + " a " + rate + " Hz (niveau " + rms + ").\n"
                    + "Reglage enregistre et \"Utiliser le micro de la voiture\" active.\n"
                    + "Refaites un appel pour verifier. Merci d'envoyer une photo de cet ecran."
                    : "\nMICROPHONE FOUND: " + name + " at " + rate + " Hz (level " + rms + ").\n"
                    + "Setting saved and \"Use the car microphone\" enabled.\n"
                    + "Make a call to check. Please send a photo of this screen.";
        } else {
            e.putString("mic_source", "auto");
            e.putString("mic_rate", "auto");
            e.putBoolean("car_mic", false);
            msg = Tr.fr()
                    ? "\nAUCUN MICRO UTILISABLE : Android ne recoit pas le son du micro de la voiture.\n"
                    + "\"Utiliser le micro de la voiture\" a ete desactive (vous entendrez vos appels).\n"
                    + "Solution conseillee : cochez \"Appels via le Bluetooth de la voiture\" et gardez\n"
                    + "l'iPhone connecte a HandsFreeLink. Sinon : dongle avec micro (Carlinkit CCPA-MIC).\n"
                    + "Merci d'envoyer une photo de cet ecran."
                    : "\nNO USABLE MICROPHONE: Android does not receive sound from the car microphone.\n"
                    + "\"Use the car microphone\" has been disabled (you will still hear your calls).\n"
                    + "Recommended: tick \"Calls through the car Bluetooth\" and keep\n"
                    + "the iPhone connected to HandsFreeLink. Otherwise: a dongle with a mic (Carlinkit CCPA-MIC).\n"
                    + "Please send a photo of this screen.";
        }
        e.commit();
        final boolean ok = src >= 0 && rms >= MIN_RMS;
        ui.post(new Runnable() {
            public void run() {
                verdict.setTextColor(ok ? 0xFF4CD964 : 0xFFFF5A5A);
                verdict.setText(msg.trim());
                running = false;
                start.setEnabled(true);
                start.setText(Tr.s("Relancer le test", "Run the test again"));
            }
        });
    }
}
