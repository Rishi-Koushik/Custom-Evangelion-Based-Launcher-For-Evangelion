package dev.rishi.quickstar;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Launcher and icon appearance settings. */
final class LCfg {
    static final String ORIGINAL = "original";
    static final String MONO = "mono";
    static final String[] SHAPES = {"circle", "squircle", "rounded", "square", "octagon", "hexagon"};

    int cols = 4;
    int size = 100;
    boolean labels = true;
    String style = MONO;
    String shape = "circle";
    String pack = "";
    int fg = 0xFFFFFFFF;
    int bg = 0xFF1A1A1A;

    static LCfg load(Context c) {
        SharedPreferences p = Sys.prefs(c);
        LCfg g = new LCfg();
        g.cols = p.getInt("l_cols", g.cols);
        g.size = p.getInt("l_size", g.size);
        g.labels = p.getBoolean("l_labels", g.labels);
        g.style = p.getString("l_style", g.style);
        g.shape = p.getString("l_shape", g.shape);
        g.pack = p.getString("l_pack", g.pack);
        g.fg = p.getInt("l_fg", g.fg);
        g.bg = p.getInt("l_bg", g.bg);
        return g;
    }

    void save(Context c) {
        Sys.prefs(c).edit().putInt("l_cols", cols).putInt("l_size", size).putBoolean("l_labels", labels)
                .putString("l_style", style).putString("l_shape", shape).putString("l_pack", pack)
                .putInt("l_fg", fg).putInt("l_bg", bg).apply();
    }

    /** Changes whenever anything that affects a rendered icon changes. */
    String signature() {
        return style + "|" + shape + "|" + pack + "|" + fg + "|" + bg;
    }

    static List<String> list(Context c, String key) {
        List<String> out = new ArrayList<>();
        String s = Sys.prefs(c).getString(key, "");
        if (!s.isEmpty()) for (String x : s.split("\n")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    static void putList(Context c, String key, List<String> v) {
        Sys.prefs(c).edit().putString(key, android.text.TextUtils.join("\n", v)).apply();
    }
}
