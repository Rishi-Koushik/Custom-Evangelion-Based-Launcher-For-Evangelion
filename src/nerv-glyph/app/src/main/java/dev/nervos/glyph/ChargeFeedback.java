package dev.nervos.glyph;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** The plug-in sound and haptic for the active unit. Silent / vibrate ringer modes are respected. */
final class ChargeFeedback {
    private ChargeFeedback() {}

    static void play(Context c, Units.Unit u) {
        AudioManager am = c.getSystemService(AudioManager.class);
        int mode = am.getRingerMode();
        if (mode != AudioManager.RINGER_MODE_SILENT) vibrate(c, u);
        if (mode != AudioManager.RINGER_MODE_NORMAL) return;
        int res = c.getResources().getIdentifier("nerv_charge_" + u.id, "raw", c.getPackageName());
        if (res == 0) return;
        MediaPlayer mp = MediaPlayer.create(c, res, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(), am.generateAudioSessionId());
        if (mp == null) return;
        mp.setOnCompletionListener(MediaPlayer::release);
        mp.start();
    }

    /** Each unit powers up differently: Misato heavy, Rei soft, Asuka punchy, Mari playful. */
    private static void vibrate(Context c, Units.Unit u) {
        Vibrator v = c.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        VibrationEffect.Composition fx = VibrationEffect.startComposition();
        switch (u.id) {
            case "unit00":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.4f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 120);
                break;
            case "unit02":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.9f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 60)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 70);
                break;
            case "unit08":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.7f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 90)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 70);
                break;
            default:
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 40);
        }
        v.vibrate(fx.compose(), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK));
    }
}
