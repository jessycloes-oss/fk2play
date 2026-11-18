package com.fk2play;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.fujitsu_ten.displayaudio.steeringmenuservice.service.ISteeringMenuService;
import com.fujitsu_ten.displayaudio.steeringmenuservice.service.ISteeringMenuServiceCallback;

/**
 * Touches du volant propres au Honda Connect (piste suivante / precedente...).
 *
 * Le Honda Connect ne transmet pas ces touches aux applis par les voies Android
 * (ni KeyEvent, ni ACTION_MEDIA_BUTTON) : verifie en voiture avec la 1.7 build 9.
 * Il les envoie a l'appli qui s'inscrit aupres de son service volant
 * (SteeringMenuService, Fujitsu Ten) avec une « adresse » et annonce qu'elle est
 * affichee (notifySteeringMenuDispMode = 1). Methode decouverte par MaKi83
 * (OpenDroidAuto, XDA) ; implementation propre a FK2Play.
 *
 * Inscription seulement quand l'ecran CarPlay est affiche : en arriere-plan, les
 * touches reviennent au Honda (radio, menus).
 */
public final class HondaSteering {
    static final String PREF_STATUS = "honda_sw_status";
    static final String PREF_ADDRESS = "honda_sw_address";
    static final int DEFAULT_ADDRESS = 260; // « menu » ; autres : 258, 259, 263, 265, 266
    private static final String ACTION = ISteeringMenuService.class.getName();

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ISteeringMenuService service;
    private boolean bound;
    private boolean wanted;
    private boolean registered;
    private int address = DEFAULT_ADDRESS;

    private final ISteeringMenuServiceCallback.Stub callback = new ISteeringMenuServiceCallback.Stub() {
        @Override
        public boolean onSteeringSWDown(final int keyCode) {
            // Garde-fou : une touche que l'on est en train de rendre au Honda ne doit pas boucler.
            if (keyCode == injecting) return false;
            if (MediaKeys.commandFor(keyCode, false) != 0) {
                // Piste suivante / precedente... : commande CarPlay (journal sur le thread principal).
                main.post(new Runnable() {
                    public void run() { MediaKeys.handle(ctx, keyCode, Tr.s("volant Honda", "Honda wheel")); }
                });
            } else {
                // Volume (65532/65533) et autres : une fois inscrit, le Honda ne les traite plus
                // lui-meme. On les lui rend en les reinjectant dans notre fenetre (CarPlay est au
                // premier plan) : sa politique de touches les traite comme avant.
                giveBack(keyCode);
            }
            return true;
        }

        @Override
        public void onShowView() {
            main.post(new Runnable() {
                public void run() { if (wanted && registered && !"B".equals(activeMode)) notifyDisp(1); }
            });
        }

        @Override
        public boolean onFinishView(boolean flag, boolean anime) { return true; }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder b) {
            service = ISteeringMenuService.Stub.asInterface(b);
            setStatus(Tr.s("connecte (adresse ", "connected (address ") + address + ")");
            if (wanted) activate();
        }

        public void onServiceDisconnected(ComponentName name) {
            service = null;
            registered = false;
            setStatus(Tr.s("deconnecte", "disconnected"));
        }
    };

    HondaSteering(Context c) { ctx = c.getApplicationContext(); }

    private final java.util.concurrent.ExecutorService injector =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private volatile int injecting = -1;

    private void giveBack(final int keyCode) {
        injector.execute(new Runnable() {
            public void run() {
                String how = Tr.s("rendue au Honda", "returned to Honda");
                injecting = keyCode;
                try {
                    // Interdit sur le thread principal : d'ou le thread dedie.
                    new android.app.Instrumentation().sendKeyDownUpSync(keyCode);
                } catch (Throwable t) {
                    how = Tr.s("rendue impossible : ", "return failed: ") + t.getClass().getSimpleName();
                } finally {
                    injecting = -1;
                }
                final String src = Tr.s("volant Honda, ", "Honda wheel, ") + how;
                main.post(new Runnable() {
                    public void run() { MediaKeys.handle(ctx, keyCode, src); }
                });
            }
        });
    }

    /** A appeler une fois (creation du service CarPlay). */
    void bind() {
        if (bound) return;
        try {
            bound = ctx.bindService(new Intent(ACTION), conn, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException e) {
            bound = false;
        }
        if (!bound) setStatus(Tr.s("introuvable (pas un Honda Connect ?)", "not found (not a Honda Connect?)"));
    }

    void unbind() {
        setActive(false);
        if (bound) {
            try { ctx.unbindService(conn); } catch (RuntimeException ignored) {}
        }
        bound = false;
        service = null;
    }

    /** Vrai pendant qu'une touche volume est rendue au Honda : l'ecran ne doit pas la traiter. */
    volatile boolean givingBack;
    /** Vrai pendant la « fenetre volume » : desinscrit, le Honda gere le volume lui-meme. */
    volatile boolean released;

    /** L'ecran doit laisser passer les touches volume au systeme. */
    boolean volumeToSystem() { return givingBack || released; }

    private final Runnable reactivate = new Runnable() {
        public void run() {
            synchronized (HondaSteering.this) {
                released = false;
                if (wanted) activate();
            }
        }
    };

    /**
     * Volume au volant : inscrit au service volant, FK2Play recoit aussi le volume et le Honda
     * ne le gere plus. On se desinscrit, on renvoie la touche (le Honda la traite : volume reel,
     * affichage au tableau de bord), puis on reste desinscrit pendant la « fenetre volume »
     * (3 s par defaut) : les appuis suivants vont directement au Honda, sans delai. On se
     * reinscrit ensuite pour les touches piste.
     */
    void giveBackVolume(final int keyCode) {
        final long windowMs = readInt("volume_window", 3) * 1000L;
        main.removeCallbacks(reactivate);
        injector.execute(new Runnable() {
            public void run() {
                givingBack = true;
                try {
                    synchronized (HondaSteering.this) { deactivate(); }
                    sleep(40);
                    new android.app.Instrumentation().sendKeyDownUpSync(keyCode);
                    sleep(40);
                } catch (Throwable t) {
                    android.util.Log.w("FK2Play", "Volume rendu au Honda impossible", t);
                } finally {
                    if (windowMs > 0) {
                        released = true;
                        givingBack = false;
                        main.postDelayed(reactivate, windowMs);
                    } else {
                        synchronized (HondaSteering.this) { if (wanted) activate(); }
                        givingBack = false;
                    }
                }
            }
        });
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /** true quand l'ecran CarPlay est au premier plan. */
    synchronized void setActive(boolean on) {
        wanted = on;
        main.removeCallbacks(reactivate);
        released = false;
        if (!PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("media_keys", true)) on = false;
        if (on) activate();
        else deactivate();
    }

    /**
     * Mode d'inscription (Diagnostic, a tester en voiture) :
     *  A = registerCallbackEx + notifySteeringMenuDispMode (comme OpenDroidAuto) ;
     *  B = registerCallbackEx seul ; C = notifySteeringMenuDispMode seul.
     * Valide en voiture (16 nov. 2026) : C est le meilleur — le Honda garde le volume (instantane,
     * affichage au tableau de bord) et envoie les touches piste a l'ecran FK2Play. C par defaut.
     */
    private String activeMode;

    private synchronized void activate() {
        if (service == null) return;
        int a = readAddress();
        String mode = PreferenceManager.getDefaultSharedPreferences(ctx).getString("sw_reg_mode", "C");
        if (registered && (a != address || !mode.equals(activeMode))) deactivate();
        if (registered) { if (!"B".equals(activeMode)) notifyDisp(1); return; }
        address = a;
        activeMode = mode;
        try {
            if (!"C".equals(mode)) service.registerCallbackEx(callback, address);
            if (!"B".equals(mode)) notifyDisp(1);
            registered = true;
            setStatus(Tr.s("connecte (adresse ", "connected (address ") + address + ", mode " + mode + ")");
        } catch (Exception e) {
            registered = false;
            setStatus(Tr.s("erreur d'inscription : ", "registration error: ") + e);
        }
    }

    private synchronized void deactivate() {
        if (service == null || !registered) return;
        try {
            if (!"C".equals(activeMode)) service.unregisterCallbackEx(callback, address);
            if (!"B".equals(activeMode)) notifyDisp(0);
        } catch (Exception ignored) {
        }
        registered = false;
    }

    private int readInt(String key, int def) {
        try {
            return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx).getString(key, String.valueOf(def)));
        } catch (RuntimeException e) {
            return def;
        }
    }

    private void notifyDisp(int mode) {
        ISteeringMenuService s = service;
        if (s == null) return;
        try { s.notifySteeringMenuDispMode(address, mode); } catch (Exception ignored) {}
    }

    private int readAddress() {
        try {
            return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getString(PREF_ADDRESS, String.valueOf(DEFAULT_ADDRESS)));
        } catch (RuntimeException e) {
            return DEFAULT_ADDRESS;
        }
    }

    private void setStatus(String s) {
        android.util.Log.i("FK2Play", "Service volant Honda : " + s);
        try {
            PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(PREF_STATUS, s).apply();
        } catch (RuntimeException ignored) {}
    }
}
