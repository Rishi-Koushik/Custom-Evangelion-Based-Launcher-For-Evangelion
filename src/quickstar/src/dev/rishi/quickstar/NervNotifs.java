package dev.rishi.quickstar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * NERV notifications: Nothing's own pop-ups are swapped for NERV banners. The banners are drawn by the lock-hold
 * accessibility service from what the NERV notification listener hands it, so Nothing's pop-ups are only switched off
 * while the setting is on and both services are actually running; if either stops, the pop-ups come straight back.
 */
final class NervNotifs {
    static final String KEY_ON = "nerv_notifs";
    private static final String K_HEADS_UP = "heads_up_notifications_enabled";
    static final String LISTENER = "dev.rishi.quickstar/dev.rishi.quickstar.NervNotifListener";

    /** set by the two services as they connect and disconnect (same process) */
    static volatile boolean listenerUp, bannerUp;

    private NervNotifs() {}

    static boolean enabled(Context c) {
        return Sys.prefs(c).getBoolean(KEY_ON, false);
    }

    static boolean listenerGranted(Context c) {
        String v = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return v != null && v.contains(LISTENER);
    }

    static void setEnabled(Context c, boolean on) {
        Sys.prefs(c).edit().putBoolean(KEY_ON, on).apply();
        sync(c);
    }

    /** Android's page for switching this one listener on. The user flips it there. */
    static Intent grantIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        new ComponentName("dev.rishi.quickstar", "dev.rishi.quickstar.NervNotifListener").flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Nothing's pop-ups off exactly while NERV banners can replace them; the original value is restored otherwise. */
    static synchronized void sync(Context c) {
        if (!Sys.canWriteSecure(c)) return;
        SharedPreferences p = Sys.prefs(c);
        boolean replace = enabled(c) && listenerUp && bannerUp;
        boolean swapped = p.getBoolean("heads_up_swapped", false);
        if (replace && !swapped) {
            String orig = Settings.Global.getString(c.getContentResolver(), K_HEADS_UP);
            p.edit().putString("bk_heads_up", orig == null ? "1" : orig).putBoolean("heads_up_swapped", true).apply();
            Settings.Global.putString(c.getContentResolver(), K_HEADS_UP, "0");
        } else if (!replace && swapped) {
            Settings.Global.putString(c.getContentResolver(), K_HEADS_UP, p.getString("bk_heads_up", "1"));
            p.edit().putBoolean("heads_up_swapped", false).apply();
        }
    }
}
