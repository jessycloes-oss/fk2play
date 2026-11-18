package com.fk2play;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Surface;

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

/**
 * Garde la session avec le dongle ouverte en arriere-plan (service au premier plan).
 *
 * Avant la 1.5, quitter l'ecran CarPlay fermait la session : le dongle, prive de battement
 * de coeur, redemarrait apres ~15-20 s et son rebranchement USB relancait FK2Play par-dessus
 * l'appli ouverte. Ici la session vit tant que le dongle est branche : musique, appels et
 * battement de coeur continuent pendant que l'on navigue dans le Honda Connect.
 */
public class CarPlayService extends Service implements DongleListener,
        VideoPipeline.KeyframeRequester, VideoPipeline.StatusSink {

    /** Ecran CarPlay (null quand il n'est pas affiche). */
    public interface Ui {
        void onServiceStatus(String message, boolean permanent, boolean force);
        void onVideoStarted();
    }

    static final String EXTRA_SIM_FILE = "sim_file";
    private static final String ACTION_USB_PERMISSION = "com.fk2play.USB_PERMISSION";
    private static final long RETRY_MS = 3000;
    private static final int NOTIF_ID = 1;

    private static volatile CarPlayService instance;

    public static CarPlayService get() { return instance; }

    private final Handler handler = new Handler();
    private UsbManager usb;
    private VideoPipeline video;
    private AudioOutput audio;
    private HondaSteering steering;
    private volatile DongleSession session;
    private volatile MicInput mic;
    private volatile DongleConfig config;
    private volatile Ui ui;
    private volatile boolean videoActive;
    private volatile boolean inCall;
    private volatile boolean callOnCarBluetooth;
    /** L'ecran CarPlay etait affiche (ou l'etait il y a peu) au debut de l'appel : on y revient a la fin. */
    private volatile boolean returnAfterCall;
    /** Dernier moment ou l'ecran CarPlay etait affiche (uptime ms). */
    private volatile long lastUiShown;
    /** Le Honda peut afficher son ecran telephone juste avant que l'appel ne nous soit signale. */
    private static final long RECENT_UI_MS = 60000;
    /** Seconde tentative si le Honda reprend la main juste apres notre retour. */
    private static final long BRING_BACK_RETRY_MS = 1500;
    /** Decodage en arriere-plan conserve quelques secondes apres l'appel, le temps du retour. */
    private static final long BACKGROUND_DECODE_GRACE_MS = 4000;
    private volatile String baseStatus = Tr.s("Connexion au dongle...", "Connecting to the dongle...");
    private boolean permissionPending;
    private boolean connecting;
    private DongleSession lastSession;
    private String simFile;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        video = new VideoPipeline(this, this);
        audio = new AudioOutput();
        config = loadConfig(this);
        video.setAdaptive(config.adaptiveVideo);
        steering = new HondaSteering(this);
        steering.bind();

        IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, f);
        goForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra(EXTRA_SIM_FILE) != null) {
            simFile = intent.getStringExtra(EXTRA_SIM_FILE);
        }
        connect();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        MediaKeys.release(this);
        steering.unbind();
        unregisterReceiver(usbReceiver);
        disconnect();
        video.release();
        audio.release();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    /** Service au premier plan : Android (et le Honda Connect) ne le tue pas en arriere-plan. */
    @SuppressWarnings("deprecation")
    private void goForeground() {
        Intent open = new Intent(this, CarPlayActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, 0);
        Notification n = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("FK2Play")
                .setContentText(Tr.s("CarPlay actif - toucher pour revenir", "CarPlay active - tap to return"))
                .setContentIntent(pi)
                .setOngoing(true)
                .getNotification();
        startForeground(NOTIF_ID, n);
    }

    // ---------------------------------------------------------------- Ecran CarPlay

    public void attachUi(Ui u) {
        ui = u;
        applyConfig(loadConfig(this));
        MediaKeys.claim(this);
        // Touches piste suivante / precedente du volant : service propre au Honda Connect.
        steering.setActive(true);
        connect();
    }

    public void detachUi(Ui u) {
        if (ui == u) {
            ui = null;
            steering.setActive(false);
            lastUiShown = android.os.SystemClock.uptimeMillis();
        }
    }

    public void setSurface(Surface s) { video.setSurface(s); }

    public DongleSession session() { return session; }
    public HondaSteering steering() { return steering; }
    public boolean isVideoActive() { return videoActive; }
    public boolean isInCall() { return inCall; }
    public String baseStatus() { return baseStatus; }

    /**
     * Nouveaux reglages : ceux envoyes au dongle a l'ouverture imposent de rouvrir la session,
     * les autres (appels via la voiture...) s'appliquent tout de suite.
     */
    private synchronized void applyConfig(DongleConfig c) {
        DongleConfig old = config;
        config = c;
        video.setAdaptive(c.adaptiveVideo);
        if (session != null && !initKey(old).equals(initKey(c))) {
            android.util.Log.i("FK2Play", "Reglages modifies : reouverture de la session");
            disconnect();
            handler.postDelayed(retry, 500);
        }
    }

    private static String initKey(DongleConfig c) {
        return c.width + "x" + c.height + "@" + c.fps + "/" + c.nightMode + "/" + c.handDrive
                + "/" + c.wifi5g + "/" + c.useCarMic + "/" + c.audioOverUsb;
    }

    // ---------------------------------------------------------------- Connexion USB

    private void connect() {
        synchronized (this) {
            if (session != null || connecting) return;
        }
        handler.removeCallbacks(retry);
        if (simFile != null) {
            startOpen(null);
            return;
        }
        UsbDevice dongle = findDongle();
        if (dongle == null) {
            status(Tr.s("Branchez le dongle CarPlay sur le port USB...\n", "Plug the CarPlay dongle into the USB port...\n") + usbDiagnostic(), true, false);
            handler.postDelayed(retry, RETRY_MS);
            return;
        }
        if (!usb.hasPermission(dongle)) {
            if (!permissionPending) {
                permissionPending = true;
                status(Tr.s("Autorisez l'acces au dongle USB", "Allow access to the USB dongle"), true, false);
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
        boolean opened = false;
        try {
            t = dev == null ? new SimTransport(simFile) : new UsbTransport(usb, dev);
            DongleSession s = new DongleSession(t, config, this);
            synchronized (this) {
                if (instance != this) { t.close(); return; }
                session = s;
                lastSession = s;
                mic = new MicInput(this, s);
                opened = true;
            }
            s.start();
        } catch (Exception e) {
            if (t != null && !opened) t.close();
            onError(e);
        } finally {
            synchronized (this) { connecting = false; }
        }
    }

    private synchronized void disconnect() {
        handler.removeCallbacks(retry);
        videoActive = false;
        inCall = false;
        callOnCarBluetooth = false;
        returnAfterCall = false;
        video.setBackgroundDecode(false);
        video.reset();
        if (mic != null) { mic.stop(); mic = null; }
        if (session != null) { session.stop(); session = null; }
        audio.stopAll();
    }

    /**
     * Fin d'appel : sans attendre que le Honda Connect quitte son ecran telephone (plusieurs
     * secondes), on remet tout de suite l'ecran CarPlay au premier plan. L'image a ete tenue
     * a jour pendant l'appel : elle s'affiche instantanement.
     */
    private final Runnable claimMediaKeys = new Runnable() {
        public void run() { MediaKeys.claim(CarPlayService.this); }
    };

    private final Runnable bringBack = new Runnable() {
        public void run() {
            if (ui != null || session == null || inCall) return;
            android.util.Log.i("FK2Play", "Fin d'appel : retour a l'ecran CarPlay");
            startActivity(new Intent(CarPlayService.this, CarPlayActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
    };

    private final Runnable stopBackgroundDecode = new Runnable() {
        public void run() {
            if (!inCall) video.setBackgroundDecode(false);
        }
    };

    private final Runnable retry = new Runnable() {
        public void run() { connect(); }
    };

    private UsbDevice findDongle() {
        for (UsbDevice d : usb.getDeviceList().values()) {
            if (UsbTransport.isDongle(d)) return d;
        }
        return null;
    }

    /** Ce que voit Android sur le bus USB : sert a diagnostiquer le Honda Connect. */
    private String usbDiagnostic() {
        try {
            boolean host = getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
            StringBuilder sb = new StringBuilder(Tr.s("[USB hote Android : ", "[Android USB host: ")).append(host ? Tr.s("oui", "yes") : Tr.s("NON", "NO"));
            int n = 0;
            for (UsbDevice d : usb.getDeviceList().values()) {
                if (n++ < 4) sb.append(n == 1 ? Tr.s(" | vus : ", " | seen: ") : ", ")
                        .append(String.format("%04x:%04x", d.getVendorId(), d.getProductId()));
            }
            if (n == 0) sb.append(Tr.s(" | aucun peripherique vu", " | no device seen"));
            return sb.append("]").toString();
        } catch (Exception e) {
            return "[USB : " + e + "]";
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (ACTION_USB_PERMISSION.equals(i.getAction())) {
                permissionPending = false;
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) connect();
                else status(Tr.s("Permission USB refusee", "USB permission denied"), true, false);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(i.getAction())) {
                UsbDevice d = (UsbDevice) i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (UsbTransport.isDongle(d)) {
                    disconnect();
                    if (ui == null) {
                        // Dongle retire pendant que FK2Play est en arriere-plan : on s'arrete.
                        // Le rebranchement relancera FK2Play automatiquement.
                        stopSelf();
                    } else {
                        status(Tr.s("Dongle debranche", "Dongle unplugged"), true, false);
                        handler.postDelayed(retry, RETRY_MS);
                    }
                }
            }
        }
    };

    // ---------------------------------------------------------------- Evenements dongle

    @Override
    public void onPhoneConnected(int phoneType, boolean wireless) {
        String s = phoneType == 3 ? Tr.s("CarPlay connecte", "CarPlay connected") + (wireless ? Tr.s(" (sans fil)", " (wireless)") : "")
                : Tr.s("Telephone connecte", "Phone connected");
        baseStatus = null;
        status(s, false, true);
    }

    @Override
    public void onPhoneDisconnected() {
        audio.stopAll();
        videoActive = false;
        status(Tr.s("iPhone deconnecte - en attente...", "iPhone disconnected - waiting..."), true, false);
    }

    @Override
    public void onVideo(int w, int h, byte[] data, int off, int len) {
        if (!videoActive) {
            videoActive = true;
            baseStatus = null;
            Ui u = ui;
            if (u != null) u.onVideoStarted();
        }
        // Sans ecran (FK2Play en arriere-plan), les images sont simplement ignorees.
        video.submit(w, h, data, off, len);
    }

    @Override
    public void onAudio(int decodeType, byte[] data, int off, int len) {
        audio.write(decodeType, data, off, len);
    }

    @Override
    public void onAudioCommand(int cmd) {
        MicInput m = mic;
        DongleConfig c = config;
        switch (cmd) {
            case Msg.AUDIO_PHONECALL_START:
                if (!inCall) {
                    returnAfterCall = ui != null
                            || android.os.SystemClock.uptimeMillis() - lastUiShown < RECENT_UI_MS;
                    handler.removeCallbacks(stopBackgroundDecode);
                    // L'image CarPlay reste a jour pendant l'appel (peu de calcul : ecran d'appel).
                    if (returnAfterCall && c.returnAfterCall) video.setBackgroundDecode(true);
                }
                inCall = true;
                if (c.callsViaCarBluetooth && c.audioOverUsb) {
                    // L'appel part sur le Bluetooth de la voiture (micro et touches d'origine).
                    DongleSession s = session;
                    if (s != null) s.sendCommand(Msg.CMD_AUDIO_TRANSFER_ON);
                    callOnCarBluetooth = true;
                } else if (c.useCarMic && m != null) {
                    m.start();
                }
                break;
            case Msg.AUDIO_PHONECALL_STOP:
                Stats.callEnded();
                inCall = false;
                if (m != null) m.stop();
                if (callOnCarBluetooth) {
                    callOnCarBluetooth = false;
                    DongleSession s = session;
                    if (s != null) s.sendCommand(Msg.CMD_AUDIO_TRANSFER_OFF);
                }
                if (returnAfterCall && c.returnAfterCall) {
                    handler.post(bringBack);
                    handler.postDelayed(bringBack, BRING_BACK_RETRY_MS);
                }
                returnAfterCall = false;
                handler.postDelayed(stopBackgroundDecode, BACKGROUND_DECODE_GRACE_MS);
                break;
            case Msg.AUDIO_MEDIA_START:
                // La radio Honda a pu reprendre la main : FK2Play redevient le lecteur actif
                // pour recevoir piste suivante / precedente du volant.
                handler.post(claimMediaKeys);
                break;
            case Msg.AUDIO_SIRI_START:
                if (c.useCarMic && m != null) m.start();
                break;
            case Msg.AUDIO_SIRI_STOP:
                if (m != null) m.stop();
                break;
            default:
                break;
        }
    }

    @Override
    public void onStatus(String message) {
        android.util.Log.i("FK2Play", message);
        if (message.startsWith(DongleSession.READY_PREFIX_FR) || message.startsWith(DongleSession.READY_PREFIX_EN)) {
            status(message, true, false);
            handler.post(claimMediaKeys);
        }
        else status(message, false, false);
    }

    @Override
    public void onError(Exception e) {
        android.util.Log.w("FK2Play", "Erreur session", e);
        videoActive = false;
        inCall = false;
        status(Tr.s("Erreur : ", "Error: ") + e.getMessage() + Tr.s(" - nouvelle tentative...", " - retrying..."), true, false);
        handler.post(new Runnable() {
            public void run() {
                disconnect();
                handler.postDelayed(retry, RETRY_MS);
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
        if (s.startsWith(VideoPipeline.NO_DECODER_FR) || s.startsWith(VideoPipeline.NO_DECODER_EN)) status(s, true, false);
    }

    private void status(String s, boolean permanent, boolean force) {
        if (permanent) baseStatus = s;
        Ui u = ui;
        if (u != null) u.onServiceStatus(s, permanent, force);
    }

    static DongleConfig loadConfig(Context ctx) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
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
        c.callsViaCarBluetooth = p.getBoolean("calls_car_bt", false);
        c.returnAfterCall = p.getBoolean("return_after_call", true);
        c.adaptiveVideo = p.getBoolean("adaptive_video", true);
        String dt = p.getString("display_thread", "auto");
        VideoPipeline.preferHardware = !"sw".equals(p.getString("video_decoder", "auto"));
        VideoPipeline.forceDisplayThread = "on".equals(dt) ? Boolean.TRUE : "off".equals(dt) ? Boolean.FALSE : null;
        try {
            com.fk2play.video.FfmpegDecoder.forcedFormat = Integer.parseInt(p.getString("video_format", "0"));
        } catch (NumberFormatException ignored) { }
        return c;
    }
}
