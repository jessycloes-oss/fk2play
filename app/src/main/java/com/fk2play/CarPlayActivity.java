package com.fk2play;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.fk2play.protocol.DongleSession;
import com.fk2play.protocol.Msg;

/**
 * Ecran principal : affiche CarPlay en plein ecran et relaie le tactile et les touches.
 * La session avec le dongle vit dans {@link CarPlayService} : quitter cet ecran (reglages,
 * radio, navigation Honda...) ne coupe plus CarPlay.
 */
public class CarPlayActivity extends Activity implements SurfaceHolder.Callback, CarPlayService.Ui {

    private final Handler ui = new Handler();
    private SurfaceView surfaceView;
    private TextView status;
    /** Des images CarPlay arrivent : aucun message ne doit couvrir l'ecran. */
    private volatile boolean videoActive;
    /** Reglage de diagnostic : afficher le code des touches inconnues. */
    private boolean showKeyCodes;
    /** Message permanent a reafficher apres un message passager (null = ecran libre). */
    private String baseStatus;
    private CarPlayService service;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_carplay);

        surfaceView = (SurfaceView) findViewById(R.id.surface);
        status = (TextView) findViewById(R.id.status);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) { return handleTouch(v, e); }
        });
        // Appui long sur le texte d'etat -> reglages
        status.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                startActivity(new Intent(CarPlayActivity.this, SettingsActivity.class));
                return true;
            }
        });

        Intent svc = new Intent(this, CarPlayService.class);
        // Simulation sans dongle : uniquement dans un APK de test (debuggable), jamais en version normale.
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            String simFile = getIntent().getStringExtra(CarPlayService.EXTRA_SIM_FILE);
            if (simFile != null) svc.putExtra(CarPlayService.EXTRA_SIM_FILE, simFile);
        }
        startService(svc);
    }

    @Override
    protected void onResume() {
        super.onResume();
        surfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        showKeyCodes = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("show_keycodes", false);
        startService(new Intent(this, CarPlayService.class));
        ui.post(attach);
    }

    /** Le service est cree par startService juste avant : on s'y accroche des qu'il existe. */
    private final Runnable attach = new Runnable() {
        public void run() {
            CarPlayService s = CarPlayService.get();
            if (s == null) { ui.postDelayed(this, 100); return; }
            service = s;
            videoActive = s.isVideoActive();
            baseStatus = videoActive ? null : s.baseStatus();
            if (baseStatus != null) {
                status.setText(baseStatus);
                status.setVisibility(View.VISIBLE);
            } else {
                status.setVisibility(View.INVISIBLE);
            }
            s.attachUi(CarPlayActivity.this);
            if (surface != null) s.setSurface(surface);
        }
    };

    @Override
    protected void onPause() {
        ui.removeCallbacks(attach);
        // On ne coupe plus la session : seul l'affichage s'arrete, CarPlay continue en arriere-plan.
        if (service != null) {
            service.detachUi(this);
            service.setSurface(null);
            service = null;
        }
        super.onPause();
    }

    // ---------------------------------------------------------------- Ecran / tactile

    private android.view.Surface surface;

    @Override
    public void surfaceCreated(SurfaceHolder h) { }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
        surface = h.getSurface();
        if (service != null) service.setSurface(surface);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        surface = null;
        if (service != null) service.setSurface(null);
    }

    private boolean handleTouch(View v, MotionEvent e) {
        CarPlayService svc = service;
        DongleSession s = svc != null ? svc.session() : null;
        if (s == null) return false;
        int action;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: action = Msg.TOUCH_DOWN; break;
            case MotionEvent.ACTION_MOVE: action = Msg.TOUCH_MOVE; break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: action = Msg.TOUCH_UP; break;
            default: return true;
        }
        s.sendTouch(action, e.getX() / v.getWidth(), e.getY() / v.getHeight());
        return true;
    }

    /** Touches multimedia (volant / facade) -> commandes CarPlay. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        CarPlayService svc = CarPlayService.get();
        DongleSession s = svc != null ? svc.session() : null;
        boolean inCall = svc != null && svc.isInCall();
        // Honda Connect : la touche « raccrocher » du volant arrive comme RETOUR (code 4).
        // Pendant un appel, elle raccroche au lieu de quitter FK2Play.
        if (keyCode == KeyEvent.KEYCODE_BACK && s != null && inCall) {
            s.sendCommand(Msg.CMD_REJECT_PHONE);
            return true;
        }
        if (s != null) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_NEXT: s.sendCommand(Msg.CMD_NEXT); return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: s.sendCommand(Msg.CMD_PREV); return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY: s.sendCommand(Msg.CMD_PLAY); return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE: s.sendCommand(Msg.CMD_PAUSE); return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    // Pendant un appel, la touche du kit mains libres decroche / raccroche.
                    if (inCall) s.sendCommand(Msg.CMD_REJECT_PHONE);
                    else s.sendCommand(Msg.CMD_PLAY_PAUSE);
                    return true;
                case KeyEvent.KEYCODE_CALL: s.sendCommand(Msg.CMD_ACCEPT_PHONE); return true;
                case KeyEvent.KEYCODE_ENDCALL: s.sendCommand(Msg.CMD_REJECT_PHONE); return true;
                case KeyEvent.KEYCODE_SEARCH:
                case KeyEvent.KEYCODE_VOICE_ASSIST:
                    s.sendCommand(Msg.CMD_SIRI); return true;
                default: break;
            }
        }
        if (showKeyCodes) {
            // Diagnostic : affiche le code des touches que FK2Play ne connait pas encore.
            android.widget.Toast.makeText(this, "Touche " + keyCode + " (" + KeyEvent.keyCodeToString(keyCode) + ")",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
        android.util.Log.i("FK2Play", "Touche non geree : " + keyCode);
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- Messages du service

    @Override
    public void onServiceStatus(String s, boolean permanent, boolean force) {
        if (permanent) setStatus(s);
        else showInfo(s, force);
    }

    @Override
    public void onVideoStarted() {
        // Premiere image CarPlay : on libere l'ecran de tout message.
        videoActive = true;
        ui.post(new Runnable() {
            public void run() {
                baseStatus = null;
                ui.removeCallbacks(restoreStatus);
                status.setVisibility(View.INVISIBLE);
            }
        });
    }

    // ---------------------------------------------------------------- Utilitaires

    /** Message permanent (attente, erreur, diagnostic) : reste affiche jusqu'au suivant. */
    private void setStatus(final String s) {
        ui.post(new Runnable() {
            public void run() {
                videoActive = false;
                baseStatus = s;
                ui.removeCallbacks(restoreStatus);
                status.setText(s);
                status.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Message passager : affiche 3 s puis retour au message permanent (ou rien).
     * Ignore pendant que CarPlay s'affiche, sauf si force (ex. « CarPlay connecte »).
     */
    private void showInfo(final String s, final boolean force) {
        ui.post(new Runnable() {
            public void run() {
                if (videoActive && !force) return;
                status.setText(s);
                status.setVisibility(View.VISIBLE);
                ui.removeCallbacks(restoreStatus);
                ui.postDelayed(restoreStatus, 3000);
            }
        });
    }

    private final Runnable restoreStatus = new Runnable() {
        public void run() {
            if (baseStatus != null && !videoActive) {
                status.setText(baseStatus);
                status.setVisibility(View.VISIBLE);
            } else {
                status.setVisibility(View.INVISIBLE);
            }
        }
    };
}
