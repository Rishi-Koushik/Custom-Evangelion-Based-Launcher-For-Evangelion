package dev.rishi.quickstar;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Appearance and content of the control centre. */
final class ControlCfg {
    /** Every toggle that can sit in the round-button grid: id, label, icon. */
    static final String[][] CATALOG = {
            {"rotate", "Auto-rotate", "rotate"},
            {"flash", "Flashlight", "flash"},
            {"saver", "Battery saver", "saver"},
            {"location", "Location", "location"},
            {"data", "Mobile data", "data"},
            {"airplane", "Aeroplane", "airplane"},
            {"hotspot", "Hotspot", "hotspot"},
            {"cast", "Cast", "cast"},
            {"dnd", "Do not disturb", "dnd"},
            {"mute", "Sound mode", "mute"},
    };

    int accent = 0xFF4DA3FF;
    boolean dark = false;
    int blur = 90;
    int dim = 18;
    int opacity = 100;
    int radius = 100;
    boolean media = true;
    boolean sim = true;
    boolean bottom = true;
    String side = "right";
    String theme = "unit01";
    List<String> tiles = new ArrayList<>(Arrays.asList("rotate", "airplane", "flash", "data", "hotspot", "saver", "location", "cast"));

    static ControlCfg load(Context c) {
        SharedPreferences p = Sys.prefs(c);
        ControlCfg g = new ControlCfg();
        g.accent = p.getInt("c_accent", g.accent);
        g.dark = p.getBoolean("c_dark", g.dark);
        g.blur = p.getInt("c_blur", g.blur);
        g.dim = p.getInt("c_dim", g.dim);
        g.opacity = p.getInt("c_opacity", g.opacity);
        g.radius = p.getInt("c_radius", g.radius);
        g.media = p.getBoolean("c_media", g.media);
        g.sim = p.getBoolean("c_sim", g.sim);
        g.bottom = p.getBoolean("c_bottom", g.bottom);
        g.side = p.getString("c_side", g.side);
        g.theme = p.getString("c_theme", g.theme);
        String t = p.getString("c_tiles", null);
        if (t != null) {
            g.tiles = new ArrayList<>();
            for (String s : t.split(",")) if (!s.isEmpty()) g.tiles.add(s);
        }
        return g;
    }

    void save(Context c) {
        Sys.prefs(c).edit().putInt("c_accent", accent).putBoolean("c_dark", dark).putInt("c_blur", blur)
                .putInt("c_dim", dim).putInt("c_opacity", opacity).putInt("c_radius", radius)
                .putBoolean("c_media", media).putBoolean("c_sim", sim).putBoolean("c_bottom", bottom)
                .putString("c_side", side).putString("c_theme", theme).putString("c_tiles", android.text.TextUtils.join(",", tiles)).apply();
    }

    static String[] entry(String id) {
        for (String[] e : CATALOG) if (e[0].equals(id)) return e;
        return null;
    }
}
