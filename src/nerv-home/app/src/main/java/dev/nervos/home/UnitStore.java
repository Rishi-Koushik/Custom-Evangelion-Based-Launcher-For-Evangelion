package dev.nervos.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** The unit theme the user picked, shared by the home screen, the wallpaper and (via a broadcast) Quickstar. */
final class UnitStore {
    private static final String QUICKSTAR = "dev.rishi.quickstar", GLYPH = "dev.nervos.glyph";

    private UnitStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nerv", Context.MODE_PRIVATE);
    }

    static Units.Unit current(Context c) {
        return Units.byId(prefs(c).getString("unit", "unit01"));
    }

    static void save(Context c, String id) {
        prefs(c).edit().putString("unit", id).apply();
    }

    /** Extra dark: pure-black surfaces and a darker skyline. Off means the regular dark look. */
    static boolean xdark(Context c) {
        return true; // single look per unit, and that look is extra dark for all four
    }

    static void saveMode(Context c, boolean xdark) {
        prefs(c).edit().putBoolean("xdark", xdark).apply();
    }

    /** Apply a unit from outside the home screen (the lock-screen picker): save, restyle the still wallpaper, tell Quickstar. */
    static void apply(Context c, Units.Unit u, boolean xdark, boolean saver) {
        save(c, u.id);
        saveMode(c, xdark);
        final Context app = c.getApplicationContext();
        new Thread(() -> Walls.apply(app, u, xdark)).start();
        notifyQuickstar(c, u, xdark, saver);
    }

    /**
     * Tells Quickstar to restyle the system panel and control centre for this unit. Explicit broadcast to one
     * receiver; Quickstar checks that the sender is this app before it changes anything.
     */
    static void notifyQuickstar(Context c, Units.Unit u, boolean xdark, boolean saver) {
        Intent i = new Intent("dev.rishi.quickstar.APPLY_UNIT")
                .setComponent(new ComponentName(QUICKSTAR, QUICKSTAR + ".UnitReceiver"))
                .putExtra("unit", u.id)
                .putExtra("xdark", xdark)
                .putExtra("saver", saver)
                .putExtra("clock", LockClock.current(c));
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                // opt in to sharing our identity so Quickstar can verify who sent this
                android.os.Bundle o = android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
                c.sendBroadcast(i, null, o);
            }
        } catch (RuntimeException ignored) {
            // Quickstar not installed: the home screen themes on its own
        }
        notifyGlyph(c, u);
    }

    /** Tells NERV Glyph the unit, so its charging screen, sound and haptic match. It checks the sender the same way. */
    static void notifyGlyph(Context c, Units.Unit u) {
        Intent i = new Intent("dev.nervos.glyph.SET_UNIT")
                .setComponent(new ComponentName(GLYPH, GLYPH + ".UnitReceiver"))
                .putExtra("unit", u.id);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                android.os.Bundle o = android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
                c.sendBroadcast(i, null, o);
            }
        } catch (RuntimeException ignored) {
            // NERV Glyph not installed
        }
    }
}
