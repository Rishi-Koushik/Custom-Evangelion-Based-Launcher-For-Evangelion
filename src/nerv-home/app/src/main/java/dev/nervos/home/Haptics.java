package dev.nervos.home;

import android.content.Context;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Per-unit haptics for the unlock wave and for applying a theme. Touch usage, so they follow the phone's own
 * "touch feedback" setting. Misato lands heavy, Rei is soft, Asuka is punchy, Mari bounces.
 */
final class Haptics {
    private Haptics() {}

    static void unlock(Context c, Units.Unit u) {
        VibrationEffect.Composition fx = VibrationEffect.startComposition();
        switch (u.id) {
            case "unit00":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.35f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 80);
                break;
            case "unit02":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 50)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 50);
                break;
            case "unit08":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.6f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 60)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 60);
                break;
            default:
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 30);
        }
        play(c, fx);
    }

    static void apply(Context c, Units.Unit u) {
        VibrationEffect.Composition fx = VibrationEffect.startComposition();
        switch (u.id) {
            case "unit00":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 90);
                break;
            case "unit02":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 70);
                break;
            case "unit08":
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.6f, 60);
                break;
            default:
                fx.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 60)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 60);
        }
        play(c, fx);
    }

    private static void play(Context c, VibrationEffect.Composition fx) {
        Vibrator v = c.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(fx.compose(), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
    }
}
