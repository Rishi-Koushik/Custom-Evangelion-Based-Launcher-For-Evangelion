package dev.rishi.quickstar;

import android.graphics.Color;

/** Colours and shape language of the control centre. Unit palettes come from {@link Units}, shared with NERV Home. */
final class ControlTheme {
    /** id + label for every choice: the four units, then the plain glass look. */
    static final String[][] CHOICES = new String[Units.ALL.length + 1][];

    static {
        for (int i = 0; i < Units.ALL.length; i++) CHOICES[i] = new String[]{Units.ALL[i].id, Units.ALL[i].shortName()};
        CHOICES[Units.ALL.length] = new String[]{"glass", "Glass"};
    }

    boolean eva;
    boolean saver;
    int bg;
    int card;
    int stroke;
    int active;
    int warn;
    int onActive;
    int inactive;
    int onInactive;
    int badgeStroke;
    int text;
    int sub;
    int accent;
    int slider;
    int grid;

    static int blend(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static int alpha(int c, float a) {
        return Color.argb(Math.max(0, Math.min(255, Math.round(255 * a))), Color.red(c), Color.green(c), Color.blue(c));
    }

    static ControlTheme of(ControlCfg cfg, boolean batterySaver) {
        float a = cfg.opacity / 100f;
        ControlTheme t = new ControlTheme();
        if (cfg.theme.equals("glass")) {
            t.eva = false;
            t.accent = cfg.accent;
            t.active = cfg.accent;
            t.warn = cfg.accent;
            t.onActive = 0xFFFFFFFF;
            t.text = 0xFFFFFFFF;
            t.sub = 0xCCFFFFFF;
            t.onInactive = 0xFFFFFFFF;
            t.card = cfg.dark ? alpha(0xFF101014, 0.53f * a) : alpha(0xFFFFFFFF, 0.36f * a);
            t.inactive = cfg.dark ? alpha(0xFF3A3A40, 0.87f * a) : alpha(0xFF46484E, 0.53f * a);
            t.stroke = cfg.dark ? 0x22FFFFFF : 0x38FFFFFF;
            t.slider = Color.rgb((Color.red(cfg.accent) + 510) / 3 - 10, (Color.green(cfg.accent) + 510) / 3 - 10,
                    (Color.blue(cfg.accent) + 510) / 3 - 10);
            return t;
        }
        Units.Unit u = Units.byId(cfg.theme);
        t.eva = true;
        if (batterySaver) {
            // battery saver: pure black, one thin outline colour taken from the unit, nothing translucent or decorative
            t.saver = true;
            t.bg = 0xFF000000;
            t.card = 0xFF000000;
            t.stroke = u.sv;
            t.active = u.sv;
            t.warn = u.sv;
            t.onActive = 0xFF000000;
            t.inactive = 0xFF000000;
            t.badgeStroke = u.sv;
            t.onInactive = u.sv;
            t.text = u.sv;
            t.sub = u.svDim;
            t.accent = u.sv;
            t.slider = u.sv;
            t.grid = 0;
            return t;
        }
        int neon = u.a, second = u.a2, deep = u.deep;
        t.bg = alpha(deep, 0.90f);
        t.card = alpha(blend(deep, second, 0.30f), 0.82f * a);
        t.stroke = alpha(second, 0.85f);
        t.active = neon;
        t.warn = u.warn;
        t.onActive = deep;
        t.inactive = alpha(blend(deep, second, 0.52f), 0.92f * a);
        t.onInactive = blend(0xFFFFFFFF, second, 0.22f);
        t.text = 0xFFECE7F2;
        t.sub = 0xFFA89FB8;
        t.accent = neon;
        t.slider = neon;
        t.grid = alpha(second, 0.20f);
        return t;
    }
}
