package dev.rishi.quickstar;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the Material "theme customization" JSON SystemUI regenerates its palette from. */
final class PanelTheme {
    static final String[] STYLES = {
            "TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "SPRITZ", "RAINBOW", "FRUIT_SALAD", "CONTENT", "MONOCHROMATIC"};
    private static final String P = "android.theme.customization.";

    private PanelTheme() {}

    /** The phone's built-in font pack: Noto Serif headlines, Source Sans body (Eva title-card serif, phone-wide). */
    static final String SERIF_FONT = "com.android.theme.font.notoserifsource";

    static boolean serifFont(Context c) {
        return Sys.prefs(c).getBoolean("serif_font", true);
    }

    /**
     * Same unquoted shape this phone already stores; the stock Settings parser accepts it. The font pack rides in the
     * same setting because SystemUI's theme controller owns that overlay category and switches off anything not listed.
     */
    static String build(Context c, String paletteHex, String accentHex, String style) {
        return "{" + P + "system_palette:" + paletteHex
                + "," + P + "accent_color:" + accentHex
                + ",_applied_timestamp:" + System.currentTimeMillis()
                + "," + P + "color_source:preset"
                + "," + P + "color_index:1"
                + "," + P + "theme_style:" + style
                + (serifFont(c) ? "," + P + "font:" + SERIF_FONT : "") + "}";
    }

    static String get(String json, String key) {
        Matcher m = Pattern.compile(Pattern.quote(P + key) + "\"?:\"?([^,}\"]+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static final class Colors {
        int tileOn = 0xFFC6C6C6;
        int tileOff = 0xFF1B1B1B;
        int labelOn = 0xFF111111;
        int labelOff = 0xFFE0E0E0;
        boolean real;
    }

    /** The colours SystemUI is really using right now, read from its own resources. */
    static Colors read(Context c) {
        Colors out = new Colors();
        try {
            Context sui = c.createPackageContext("com.android.systemui", 0);
            Resources r = sui.getResources();
            out.tileOn = color(r, "qs_tile_active_background_color", out.tileOn);
            out.tileOff = color(r, "qs_tile_inactive_background_color", out.tileOff);
            out.labelOn = color(r, "qs_tile_label_active_color", out.labelOn);
            out.labelOff = color(r, "qs_tile_label_inactive_color", out.labelOff);
            out.real = true;
        } catch (Exception ignored) {
            // fall back to defaults
        }
        return out;
    }

    private static int color(Resources r, String name, int fallback) {
        int id = r.getIdentifier(name, "color", "com.android.systemui");
        if (id == 0) return fallback;
        try {
            return r.getColor(id, null);
        } catch (Resources.NotFoundException e) {
            return fallback;
        }
    }

    /** Rough preview of what a seed colour will produce, shown until it is applied. */
    static Colors draft(int seed, String style) {
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float chroma;
        switch (style) {
            case "MONOCHROMATIC": chroma = 0f; break;
            case "SPRITZ": chroma = 0.03f; break;
            case "EXPRESSIVE": chroma = 0.2f; break;
            case "VIBRANT": chroma = 0.15f; break;
            default: chroma = 0.1f;
        }
        Colors out = new Colors();
        out.tileOn = Color.HSVToColor(new float[]{hsv[0], chroma, 0.79f});
        out.tileOff = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, chroma * 1.3f), 0.13f});
        out.labelOn = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, chroma * 2f), 0.1f});
        out.labelOff = Color.HSVToColor(new float[]{hsv[0], chroma * 0.6f, 0.88f});
        return out;
    }
}
