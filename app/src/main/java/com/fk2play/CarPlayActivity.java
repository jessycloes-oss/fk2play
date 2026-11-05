package com.fk2play;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
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

import com.fk2play.audio.AudioOutput;
import com.fk2play.audio.MicInput;
import com.fk2play.protocol.DongleConfig;
import com.fk2play.protocol.DongleListener;
import com.fk2play.protocol.DongleSession;
import com.fk2play.protocol.Msg;
import com.fk2play.protocol.Transport;
import com.fk2play.sim.SimTransport;
import com.fk2play.usb.UsbTransport;
import com.fk2play.video.VideoPipeline;

/** Ecran principal : affiche CarPlay en plein ecran et relaie le tactile vers le dongle. */
public class CarPlayActivity extends Activity implements DongleListener, SurfaceHolder.Callback,
        VideoPipeline.KeyframeRequester, VideoPipeline.StatusSink {

    private static final String ACTION_USB_PERMISSION = "com.fk2play.USB_PERMISSION";
    private static final long RETRY_MS = 3000;

    private final Handler ui = new Handler();
    private UsbManager usb;
    private SurfaceView surfaceView;
    private TextView status;
    private DongleSession session;
    private VideoPipeline video;
    private AudioOutput audio;
    private MicInput mic;
    private DongleConfig config;
    private volatile boolean resumed;
    private boolean permissionPending;
    /** Une ouverture de session est en cours (evite deux connexions paralleles). */
    private boolean connecting;
    /** Derniere session, pour attendre la liberation du dongle avant de le rouvrir. */
    private DongleSession lastSession;
    /** Mode simulation (APK de test uniquement) : fichier de flux dongle enregistre. */
    private String simFile;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_carplay);

        usb = (UsbManager) getSystemService(Context.USB_SERVICE);
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

        // Simulation sans dongle : uniquement dans un APK de test (debuggable), jamais en version normale.
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            simFile = getIntent().getStringExtra("sim_file");
        }

        video = new VideoPipeline(this, this);
        audio = new AudioOutput();

        IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        surfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        config = loadConfig();
        connect();
    }

    @Override
    protected void onPause() {
        resumed = false;
        disconnect();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(usbReceiver);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- Connexion USB

    private void connect() {
        synchronized (this) {
            if (!resumed || session != null || connecting) return;
        }
        if (simFile != null) {
            startOpen(null);
            return;
        }
        UsbDevice dongle = findDongle();
        if (dongle == null) {
            setStatus("Branchez le dongle CarPlay sur le port USB...\n" + usbDiagnostic());
            ui.postDelayed(retry, RETRY_MS);
            return;
        }
        if (!usb.hasPermission(dongle)) {
            if (!permissionPending) {
                permissionPending = true;
                setStatus("Autorisez l'acces au dongle USB");
                PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                usb.requestPermission(dongle, pi);
            }
            return;
        }
        startOpen(dongle);
    }

    private void startOpen(final UsbDevice dev) {
        synchronized (this) { connecting = true; }
        new Thread(new Runnable() {
            public void run() { openSession(dev); }
        }, "connect").start();
    }

    private void openSession(UsbDevice dev) {
        DongleSession prev;
        synchronized (this) { prev = lastSession; }
        // Le dongle ne doit jamais etre ouvert deux fois : on attend la fin de l'ancienne session.
        if (prev != null) prev.awaitTermination(4000);
        Transport t = null;
        try {
            t = dev == null ? new SimTransport(simFile) : new UsbTransport(usb, dev);
            DongleSession s = new DongleSession(t, config, this);
            synchronized (this) {
                if (!resumed) { t.close(); return; }
                session = s;
                lastSession = s;
                mic = new MicInput(s);
            }
            s.start();
        } catch (Exception e) {
            if (t != null && session == null) t.close();
            onError(e);
        } finally {
            synchronized (this) { connecting = false; }
        }
    }

    private synchronized void disconnect() {
        ui.removeCallbacks(retry);
        if (mic != null) { mic.stop(); mic = null; }
        if (session != null) { session.stop(); session = null; }
        audio.stopAll();
    }

    /** Ce que voit Android sur le bus USB : sert a diagnostiquer le Honda Connect au premier essai. */
    private String usbDiagnostic() {
        try {
            boolean host = getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
            StringBuilder sb = new StringBuilder("[USB hote Android : ").append(host ? "oui" : "NON");
            int n = 0;
            for (UsbDevice d : usb.getDeviceList().values()) {
                if (n++ < 4) sb.append(n == 1 ? " | vus : " : ", ")
                        .append(String.format("%04x:%04x", d.getVendorId(), d.getProductId()));
            }
            if (n == 0) sb.append(" | aucun peripherique vu");
            return sb.append("]").toString();
        } catch (Exception e) {
            return "[USB : " + e + "]";
        }
    }

    private final Runnable retry = new Runnable() {
        public void run() { connect(); }
    };

    private UsbDevice findDongle() {
        for (UsbDevice d : usb.getDeviceList().values()) {
            if (UsbTransport.isDongle(d)) return d;
        }
        return null;
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (ACTION_USB_PERMISSION.equals(i.getAction())) {
                permissionPending = false;
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) connect();
                else setStatus("Permission USB refusee");
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(i.getAction())) {
                UsbDevice d = (UsbDevice) i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (UsbTransport.isDongle(d)) {
                    disconnect();
                    setStatus("Dongle debranche");
                    ui.postDelayed(retry, RETRY_MS);
                }
            }
        }
    };

    // ---------------------------------------------------------------- Ecran / tactile

    @Override
    public void surfaceCreated(SurfaceHolder h) { }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
        video.setSurface(h.getSurface());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        video.setSurface(null);
    }

    private boolean handleTouch(View v, MotionEvent e) {
        DongleSession s = session;
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
        DongleSession s = session;
        if (s != null) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_NEXT: s.sendCommand(Msg.CMD_NEXT); return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: s.sendCommand(Msg.CMD_PREV); return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY: s.sendCommand(Msg.CMD_PLAY); return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE: s.sendCommand(Msg.CMD_PAUSE); return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: s.sendCommand(Msg.CMD_PLAY); return true;
                case KeyEvent.KEYCODE_SEARCH:
                case KeyEvent.KEYCODE_VOICE_ASSIST:
                    s.sendCommand(Msg.CMD_SIRI); return true;
                default: break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- Evenements dongle

    @Override
    public void onPhoneConnected(int phoneType, boolean wireless) {
        setStatus(phoneType == 3 ? "CarPlay connecte" + (wireless ? " (sans fil)" : "") : "Telephone connecte");
        hideStatusSoon();
    }

    @Override
    public void onPhoneDisconnected() {
        audio.stopAll();
        setStatus("iPhone deconnecte - en attente...");
    }

    @Override
    public void onVideo(int w, int h, byte[] data, int off, int len) {
        video.submit(w, h, data, off, len);
    }

    @Override
    public void onAudio(int decodeType, byte[] data, int off, int len) {
        audio.write(decodeType, data, off, len);
    }

    @Override
    public void onAudioCommand(int cmd) {
        MicInput m = mic;
        switch (cmd) {
            case Msg.AUDIO_SIRI_START:
            case Msg.AUDIO_PHONECALL_START:
                if (config.useCarMic && m != null) m.start();
                break;
            case Msg.AUDIO_SIRI_STOP:
            case Msg.AUDIO_PHONECALL_STOP:
                if (m != null) m.stop();
                break;
            default:
                break;
        }
    }

    @Override
    public void onStatus(String message) {
        setStatus(message);
    }

    @Override
    public void onError(Exception e) {
        android.util.Log.w("FK2Play", "Erreur session", e);
        setStatus("Erreur : " + e.getMessage() + " - nouvelle tentative...");
        ui.post(new Runnable() {
            public void run() {
                disconnect();
                ui.postDelayed(retry, RETRY_MS);
            }
        });
    }

    @Override
    public void requestKeyframe() {
        DongleSession s = session;
        if (s != null) s.sendCommand(Msg.CMD_REQUEST_KEYFRAME);
    }

    @Override
    public void onDecoderStatus(String s) {
        android.util.Log.i("FK2Play", s);
        if (s.startsWith("Aucun")) setStatus(s);
    }

    // ---------------------------------------------------------------- Utilitaires

    private void setStatus(final String s) {
        ui.post(new Runnable() {
            public void run() {
                status.setText(s);
                status.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideStatusSoon() {
        ui.postDelayed(new Runnable() {
            public void run() { status.setVisibility(View.INVISIBLE); }
        }, 3000);
    }

    private DongleConfig loadConfig() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        DongleConfig c = new DongleConfig();
        String res = p.getString("resolution", "800x480");
        String[] wh = res.split("x");
        try {
            c.width = Integer.parseInt(wh[0]);
            c.height = Integer.parseInt(wh[1]);
            c.fps = Integer.parseInt(p.getString("fps", "30"));
        } catch (Exception ignored) { }
        c.handDrive = p.getBoolean("right_hand_drive", false) ? 1 : 0;
        c.nightMode = p.getBoolean("night_mode", false);
        c.wifi5g = p.getBoolean("wifi_5g", true);
        c.useCarMic = p.getBoolean("car_mic", true);
        c.audioOverUsb = p.getBoolean("audio_usb", true);
        return c;
    }
}
