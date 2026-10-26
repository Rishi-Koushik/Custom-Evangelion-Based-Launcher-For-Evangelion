package dev.rishi.quickstar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** Settings access, first-run backup, and the SystemUI reload nudge. */
final class Sys {
    static final String TAG = "Quickstar";
    static final String K_THEME = "theme_customization_overlay_packages";
    static final String K_NT_TILES = "nt_sysui_qs_tiles";
    static final String K_TILES = "sysui_qs_tiles";
    static final String K_SEARCH = "qs_search_widget_settings";
    static final String K_EXTRA_DARK = "theme_extra_darkmode";

    private Sys() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("quickstar", Context.MODE_PRIVATE);
    }

    static boolean canWriteSecure(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    static boolean canWriteSystem(Context c) {
        return Settings.System.canWrite(c);
    }

    static String getSecure(Context c, String key) {
        String v = Settings.Secure.getString(c.getContentResolver(), key);
        return v == null ? "" : v;
    }

    static boolean putSecure(Context c, String key, String value) {
        try {
            return Settings.Secure.putString(c.getContentResolver(), key, value);
        } catch (SecurityException e) {
            Log.w(TAG, "putSecure " + key + " denied", e);
            return false;
        }
    }

    /** Remember the phone's original panel settings the first time we run, so "Restore" always works. */
    static void ensureBackup(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("backup_done", false)) return;
        p.edit()
                .putString("bk_theme", getSecure(c, K_THEME))
                .putString("bk_nt", getSecure(c, K_NT_TILES))
                .putString("bk_tiles", getSecure(c, K_TILES))
                .putBoolean("backup_done", true)
                .apply();
    }

    static String backup(Context c, String name) {
        return prefs(c).getString("bk_" + name, "");
    }

    /**
     * SystemUI reads Nothing's tile grid when it (re)inflates the shade. A tiny font-scale change makes
     * it do that without a shell; the original scale is put back a moment later.
     */
    static void reloadSystemUi(Context c) {
        if (!canWriteSystem(c)) return;
        final ContentResolver cr = c.getContentResolver();
        final float orig = Settings.System.getFloat(cr, Settings.System.FONT_SCALE, 1f);
        Settings.System.putFloat(cr, Settings.System.FONT_SCALE, orig + 0.01f);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> Settings.System.putFloat(cr, Settings.System.FONT_SCALE, orig), 1800);
    }

    static final String A11Y = "dev.rishi.quickstar/dev.rishi.quickstar.ControlA11yService";
    static final String LISTENER = "dev.rishi.quickstar/dev.rishi.quickstar.MediaListener";

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s != null) for (String x : s.split(":")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    /** Read-only: is our accessibility service switched on? The user switches it on in Android settings. */
    static boolean a11yEnabled(Context c) {
        return split(getSecure(c, "enabled_accessibility_services")).contains(A11Y);
    }

    static final String LOCKHOLD = "dev.rishi.quickstar/dev.rishi.quickstar.LockHoldService";

    static boolean lockHoldEnabled(Context c) {
        return split(getSecure(c, "enabled_accessibility_services")).contains(LOCKHOLD);
    }

    static boolean mediaAccessGranted(Context c) {
        return split(getSecure(c, "enabled_notification_listeners")).contains(LISTENER);
    }
}
