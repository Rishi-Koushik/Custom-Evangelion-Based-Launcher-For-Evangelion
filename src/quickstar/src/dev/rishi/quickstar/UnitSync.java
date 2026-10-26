package dev.rishi.quickstar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Applies one NERV unit everywhere Quickstar owns: the system palette (panel, shade, settings), the control
 * centre theme, and, when asked, the NERV Home launcher.
 */
final class UnitSync {
    private static final String LAUNCHER = "dev.nervos.home";

    private UnitSync() {}

    /**
     * The single colour the system palette is built from (Vibrant style). Since Nothing OS 5 the panel's tiles, slider
     * and active icons take its hue at a fixed dark tone, so only the hue matters: purple, navy blue, red, magenta-pink.
     * Android still ignores a separate accent colour, so the icons themselves stay white / dark.
     */
    static int panelSeed(Units.Unit u) {
        switch (u.id) {
            case "unit00": return 0x4FA8E8;
            case "unit02": return 0xF0452A;
            case "unit08": return 0xFF5FA8;
            default: return u.a2;
        }
    }

    static final String K_CLOCK = "lock_screen_custom_clock_face";

    /** The lock-screen clock and date colour: Misato's neon green, Rei's sky blue, Asuka's orange, Mari's pink. */
    static int lockClockColour(Units.Unit u) {
        switch (u.id) {
            case "unit00": return u.a2;
            case "unit08": return u.sv;
            default: return u.a;
        }
    }

    /**
     * Nothing OS 5's lock-screen clock: the face picked in NERV Home's theme picker (DEFAULT, MICROGRAPHICS, NDOT or
     * GRAPHIC; kept until changed) and the unit's colour as "seedColor". The original setting is saved once.
     */
    static void lockClock(Context c, int colour) {
        String cur = Sys.getSecure(c, K_CLOCK);
        android.content.SharedPreferences p = Sys.prefs(c);
        if (!p.contains("bk_clock")) p.edit().putString("bk_clock", cur).apply();
        try {
            org.json.JSONObject o = cur.isEmpty() ? new org.json.JSONObject().put("clockId", "DEFAULT") : new org.json.JSONObject(cur);
            o.put("clockId", p.getString("clock_face", o.optString("clockId", "DEFAULT"))); // face picked in NERV Home
            o.put("seedColor", 0xFF000000 | colour);
            org.json.JSONObject meta = o.optJSONObject("metadata");
            if (meta == null) meta = new org.json.JSONObject();
            meta.put("appliedTimestamp", System.currentTimeMillis());
            // with Nothing's "DEFAULT" colour choice its wallpaper app resets seedColor on every wallpaper change; an id of
            // our own makes it leave the colour alone
            meta.put("metadataSelectedColorId", "NERV");
            o.put("metadata", meta);
            if (!o.has("axes")) o.put("axes", new org.json.JSONArray());
            Sys.putSecure(c, K_CLOCK, o.toString());
        } catch (org.json.JSONException e) {
            android.util.Log.w(Sys.TAG, "lock clock", e);
        }
    }

    static String current(Context c) {
        return Sys.prefs(c).getString("unit", "unit01");
    }

    /**
     * @param saver        battery saver is on: the system palette follows the unit's saver colour instead of its body colour
     * @param tellLauncher false when the launcher itself asked for this, so the two never echo each other
     */
    static boolean xdark(Context c) {
        return true; // single look per unit: extra dark for all four
    }

    /** Dark or extra dark for the current unit; the launcher is told so its surfaces and wallpaper follow. */
    static void setMode(Context c, boolean xdark, boolean tellLauncher) {
        apply(c, current(c), xdark, c.getSystemService(android.os.PowerManager.class).isPowerSaveMode(), tellLauncher);
    }

    static boolean apply(Context c, String unitId, boolean saver, boolean tellLauncher) {
        return apply(c, unitId, xdark(c), saver, tellLauncher);
    }

    static boolean apply(Context c, String unitId, boolean xdark, boolean saver, boolean tellLauncher) {
        Units.Unit u = Units.byId(unitId);
        Sys.prefs(c).edit().putString("unit", u.id).putBoolean("xdark", xdark).apply();

        ControlCfg cfg = ControlCfg.load(c);
        cfg.theme = u.id;
        cfg.save(c);
        NervSounds.apply(c, u);

        boolean ok = true;
        if (Sys.canWriteSecure(c)) {
            Sys.ensureBackup(c);
            String seed = String.format("%06X", (saver ? u.sv : panelSeed(u)) & 0xFFFFFF);
            ok = Sys.putSecure(c, Sys.K_THEME, PanelTheme.build(c, seed, seed, "VIBRANT"));
            // Nothing OS 5 keeps the unit colour on the panel tiles even in its pure-black extra dark
            ok &= Sys.putSecure(c, Sys.K_EXTRA_DARK, xdark ? "1" : "0");
            lockClock(c, saver ? u.sv : lockClockColour(u));
        } else {
            ok = false;
        }
        if (tellLauncher) {
            Intent i = new Intent("dev.nervos.home.SET_UNIT")
                    .setComponent(new ComponentName(LAUNCHER, LAUNCHER + ".UnitReceiver"))
                    .putExtra("unit", u.id)
                    .putExtra("xdark", xdark);
            try {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    // opt in to sharing our identity so the launcher can verify who sent this
                    android.os.Bundle o = android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
                    c.sendBroadcast(i, null, o);
                }
            } catch (RuntimeException ignored) {
                // launcher not installed
            }
        }
        return ok;
    }
}
