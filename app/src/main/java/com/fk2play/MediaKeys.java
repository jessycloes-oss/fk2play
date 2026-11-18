package com.fk2play;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.RemoteControlClient;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.widget.Toast;

import com.fk2play.protocol.DongleSession;
import com.fk2play.protocol.Msg;

/**
 * Touches multimedia (volant / facade) -> commandes CarPlay.
 *
 * Deux chemins d'arrivee sur le Honda Connect :
 *  - l'ecran FK2Play a le focus : KeyEvent direct dans CarPlayActivity ;
 *  - sinon (ou si le systeme ne les donne pas a la fenetre) : diffusion
 *    ACTION_MEDIA_BUTTON, envoyee par Android a l'appli qui s'est declaree
 *    lecteur multimedia (focus audio + registerMediaButtonEventReceiver +
 *    RemoteControlClient). Avant la 1.7 FK2Play ne se declarait pas : les touches
 *    piste suivante / precedente du volant n'arrivaient jamais.
 */
public final class MediaKeys {
    private MediaKeys() {}

    static final String PREF_KEY_LOG = "key_log";

    /**
     * Codes propres au Honda Connect, releves en voiture le 16 nov. 2026 : une fois FK2Play
     * inscrit au service volant (adresse 260), le Honda envoie les touches du volant a la
     * fenetre FK2Play comme KeyEvent avec ces codes NEGATIFS.
     */
    // Verifie en voiture (build 12) : volume + = -65535, volume - = -65534 ; touches piste = -65532 / -65533.
    static final int HONDA_VOL_DOWN = -65534, HONDA_VOL_UP = -65535;
    static final int HONDA_PREV = -65532, HONDA_NEXT = -65533;

    static boolean isHondaVolume(int keyCode) {
        return keyCode == HONDA_VOL_DOWN || keyCode == HONDA_VOL_UP
                || keyCode == -HONDA_VOL_DOWN || keyCode == -HONDA_VOL_UP;
    }

    /**
     * Volume au volant : inscrit au service volant, le Honda ne le gere plus lui-meme.
     * FK2Play regle donc le volume du son CarPlay (flux musique Android).
     */
    @SuppressWarnings("deprecation")
    static void volume(final Context ctx, int keyCode) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean up = Math.abs(keyCode) == -HONDA_VOL_UP;
        if (p.getBoolean("volume_invert", false)) up = !up;
        String mode = p.getString("volume_mode", "honda");
        String how;
        CarPlayService svc = CarPlayService.get();
        if ("honda".equals(mode) && svc != null && svc.steering() != null) {
            // Par defaut : la touche est rendue au Honda (volume reel + affichage au tableau de bord).
            svc.steering().giveBackVolume(keyCode);
            how = Tr.s("rendu au Honda", "returned to Honda");
        } else if ("key".equals(mode)) {
            // Variante : touche volume Android standard, traitee par le systeme (Honda compris).
            final int k = up ? KeyEvent.KEYCODE_VOLUME_UP : KeyEvent.KEYCODE_VOLUME_DOWN;
            new Thread(new Runnable() {
                public void run() {
                    try { new android.app.Instrumentation().sendKeyDownUpSync(k); }
                    catch (Throwable t) { android.util.Log.w("FK2Play", "Volume (touche) impossible", t); }
                }
            }, "volume-key").start();
            how = Tr.s("touche volume", "volume key");
        } else {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            how = am.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        log(ctx, keyCode, "volume " + (up ? "+" : "-") + " " + how, -1);
    }
    private static final int KEY_LOG_MAX = 8;

    /** @return true si la touche a ete traduite en commande CarPlay. */
    public static boolean handle(Context ctx, int keyCode, String source) {
        CarPlayService svc = CarPlayService.get();
        DongleSession s = svc != null ? svc.session() : null;
        boolean inCall = svc != null && svc.isInCall();
        int cmd = commandFor(keyCode, inCall);
        if ((cmd == Msg.CMD_NEXT || cmd == Msg.CMD_PREV) && keyCode < 0
                && PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("track_invert", false)) {
            cmd = cmd == Msg.CMD_NEXT ? Msg.CMD_PREV : Msg.CMD_NEXT;
        }
        log(ctx, keyCode, source, cmd);
        if (cmd == 0 || s == null) return cmd != 0 && svc != null;
        s.sendCommand(cmd);
        return true;
    }

    static int commandFor(int keyCode, boolean inCall) {
        switch (keyCode) {
            case HONDA_NEXT:
            case -HONDA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return Msg.CMD_NEXT;
            case HONDA_PREV:
            case -HONDA_PREV:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return Msg.CMD_PREV;
            case KeyEvent.KEYCODE_MEDIA_PLAY: return Msg.CMD_PLAY;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return Msg.CMD_PAUSE;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                // Pendant un appel, la touche du kit mains libres raccroche.
                return inCall ? Msg.CMD_REJECT_PHONE : Msg.CMD_PLAY_PAUSE;
            case KeyEvent.KEYCODE_CALL: return Msg.CMD_ACCEPT_PHONE;
            case KeyEvent.KEYCODE_ENDCALL: return Msg.CMD_REJECT_PHONE;
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                return Msg.CMD_SIRI;
            default: return 0;
        }
    }

    /** Journal des dernieres touches (Reglages > Diagnostic) + bulle si demandee. */
    private static void log(Context ctx, int keyCode, String source, int cmd) {
        String name;
        switch (keyCode) {
            case HONDA_PREV: case -HONDA_PREV: name = Tr.s("precedent", "previous"); break;
            case HONDA_NEXT: case -HONDA_NEXT: name = Tr.s("suivant", "next"); break;
            case HONDA_VOL_DOWN: case HONDA_VOL_UP: case -HONDA_VOL_DOWN: case -HONDA_VOL_UP: name = "volume"; break;
            default: name = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "");
        }
        String line = keyCode + " " + name + " [" + source + "]"
                + (cmd > 0 ? " -> " + cmd : cmd < 0 || source.contains("rendue") || source.contains("returned to") || source.contains("return failed")
                        ? "" : Tr.s(" (ignoree)", " (ignored)"));
        android.util.Log.i("FK2Play", "Touche " + line);
        try {
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
            String old = p.getString(PREF_KEY_LOG, "");
            StringBuilder b = new StringBuilder(line);
            String[] prev = old.isEmpty() ? new String[0] : old.split("\n");
            for (int i = 0; i < prev.length && i < KEY_LOG_MAX - 1; i++) b.append('\n').append(prev[i]);
            p.edit().putString(PREF_KEY_LOG, b.toString()).apply();
            if (p.getBoolean("show_keycodes", false)) {
                Toast.makeText(ctx.getApplicationContext(), Tr.s("Touche ", "Key ") + line, Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------------------------------------------------------- Declaration lecteur multimedia

    private static RemoteControlClient rcc;
    private static boolean focused;

    private static final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                public void onAudioFocusChange(int change) {
                    android.util.Log.i("FK2Play", "Focus audio : " + change);
                    if (change == AudioManager.AUDIOFOCUS_LOSS) focused = false;
                    else if (change == AudioManager.AUDIOFOCUS_GAIN) focused = true;
                }
            };

    /**
     * Se declare lecteur multimedia actif : Android envoie alors les touches du volant
     * (ACTION_MEDIA_BUTTON) a FK2Play. A rappeler quand la musique CarPlay reprend, car la
     * radio Honda reprend la main quand on la selectionne.
     */
    @SuppressWarnings("deprecation")
    public static synchronized void claim(Context ctx) {
        if (!PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("media_keys", true)) return;
        Context app = ctx.getApplicationContext();
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        ComponentName rx = new ComponentName(app, MediaButtonReceiver.class);
        try {
            if (!focused) {
                focused = am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            // Re-enregistrer place FK2Play en tete de la pile des recepteurs.
            am.registerMediaButtonEventReceiver(rx);
            if (rcc == null) {
                Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(rx);
                rcc = new RemoteControlClient(PendingIntent.getBroadcast(app, 0, i, 0));
                rcc.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                        | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                        | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                        | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                        | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                        | RemoteControlClient.FLAG_KEY_MEDIA_STOP);
                am.registerRemoteControlClient(rcc);
                rcc.editMetadata(true)
                        .putString(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE, "CarPlay")
                        .apply();
            }
            rcc.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
        } catch (RuntimeException e) {
            android.util.Log.w("FK2Play", "Declaration lecteur multimedia impossible", e);
        }
    }

    @SuppressWarnings("deprecation")
    public static synchronized void release(Context ctx) {
        Context app = ctx.getApplicationContext();
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        try {
            if (rcc != null) { am.unregisterRemoteControlClient(rcc); rcc = null; }
            am.unregisterMediaButtonEventReceiver(new ComponentName(app, MediaButtonReceiver.class));
            am.abandonAudioFocus(focusListener);
        } catch (RuntimeException ignored) {
        }
        focused = false;
    }
}
