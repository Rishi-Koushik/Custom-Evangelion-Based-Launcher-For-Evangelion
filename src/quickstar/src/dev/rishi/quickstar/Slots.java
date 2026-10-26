package dev.rishi.quickstar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Configuration of the four custom quick settings tiles. */
final class Slots {
    static final String APP = "app";
    static final String URL = "url";
    static final String SETTINGS = "settings";

    /** Settings screens a tile can open: label, intent action. */
    static final String[][] SCREENS = {
            {"Wi-Fi", Settings.ACTION_WIFI_SETTINGS},
            {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS},
            {"Display", Settings.ACTION_DISPLAY_SETTINGS},
            {"Sound", Settings.ACTION_SOUND_SETTINGS},
            {"Battery saver", Settings.ACTION_BATTERY_SAVER_SETTINGS},
            {"Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS},
            {"Notifications", Settings.ACTION_APP_NOTIFICATION_SETTINGS},
            {"Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS},
            {"Default home app", Settings.ACTION_HOME_SETTINGS},
            {"Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS},
    };

    static final class Cfg {
        String label = "";
        String type = APP;
        String target = "";
        String glyph = "star";
        boolean customIcon;
    }

    private Slots() {}

    static Cfg get(Context c, int slot) {
        SharedPreferences p = Sys.prefs(c);
        Cfg g = new Cfg();
        g.label = p.getString("s" + slot + "_label", "");
        g.type = p.getString("s" + slot + "_type", APP);
        g.target = p.getString("s" + slot + "_target", "");
        g.glyph = p.getString("s" + slot + "_glyph", "star");
        g.customIcon = p.getBoolean("s" + slot + "_custom", false);
        return g;
    }

    static void put(Context c, int slot, Cfg g) {
        Sys.prefs(c).edit()
                .putString("s" + slot + "_label", g.label)
                .putString("s" + slot + "_type", g.type)
                .putString("s" + slot + "_target", g.target)
                .putString("s" + slot + "_glyph", g.glyph)
                .putBoolean("s" + slot + "_custom", g.customIcon)
                .apply();
    }

    static File iconFile(Context c, int slot) {
        return new File(c.getFilesDir(), "tile_icon_" + slot + ".png");
    }

    /** White alpha-mask icon for the tile: the picked image, or the chosen built-in glyph. */
    static Bitmap icon(Context c, int slot, Cfg g) {
        if (g.customIcon) {
            Bitmap b = BitmapFactory.decodeFile(iconFile(c, slot).getPath());
            if (b != null) return b;
        }
        return Glyphs.draw(g.glyph, 96);
    }

    static void saveCustomIcon(Context c, int slot, Bitmap src) throws IOException {
        Bitmap mask = Glyphs.toMask(src, 96);
        try (FileOutputStream out = new FileOutputStream(iconFile(c, slot))) {
            mask.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /** Intent that runs the tile's action, or null when it is not configured. */
    static Intent action(Context c, Cfg g) {
        Intent i = null;
        switch (g.type) {
            case APP:
                if (!g.target.isEmpty()) i = c.getPackageManager().getLaunchIntentForPackage(g.target);
                break;
            case URL:
                if (!g.target.isEmpty()) {
                    String u = g.target.contains("://") ? g.target : "https://" + g.target;
                    i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                }
                break;
            case SETTINGS:
                if (!g.target.isEmpty()) {
                    i = new Intent(g.target);
                    if (g.target.equals(Settings.ACTION_APP_NOTIFICATION_SETTINGS)) {
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
                    }
                }
                break;
            default:
        }
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }
}
