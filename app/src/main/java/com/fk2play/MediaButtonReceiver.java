package com.fk2play;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** Touches du volant envoyees par Android au lecteur multimedia actif (FK2Play). */
public class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
        KeyEvent e = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (e == null) return;
        // Une seule commande par appui (pas sur le relachement ni la repetition).
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() > 0) {
            if (isOrderedBroadcast() && MediaKeys.commandFor(e.getKeyCode(), false) != 0
                    && CarPlayService.get() != null) abortBroadcast();
            return;
        }
        if (MediaKeys.handle(ctx, e.getKeyCode(), "volant") && isOrderedBroadcast()) abortBroadcast();
    }
}
