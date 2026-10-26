package dev.rishi.quickstar;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontStyle;

import java.io.File;
import java.io.IOException;

/**
 * The NERV fonts, shipped in res/font (made by nerv-os/tools/gen_fonts.py) so an OS update can't swap or break them:
 * Nothing OS 5 / Android 17 turned the system's bold condensed font into circled letters. Glyphs the bundled files
 * don't have (Japanese) fall back to the matching system family, so 第弐拾話 stays serif on the title card.
 * The system families are only a last resort if a font fails to load.
 */
final class Fonts {
    private static Typeface serif, cond, mono, dot;

    private Fonts() {}

    /** Bold serif: clocks, titles, the big numbers. */
    static Typeface serif(Context c) {
        if (serif == null) serif = load(c, R.font.nerv_serif, 700, "serif", Typeface.create(Typeface.SERIF, Typeface.BOLD));
        return serif;
    }

    /** Bold condensed sans: labels, buttons, verdicts. */
    static Typeface cond(Context c) {
        if (cond == null) cond = load(c, R.font.nerv_cond, 700, "sans-serif", Typeface.DEFAULT_BOLD);
        return cond;
    }

    /** Monospace: terminal lines, tags, small print. */
    static Typeface mono(Context c) {
        if (mono == null) mono = load(c, R.font.nerv_mono, 400, "monospace", Typeface.MONOSPACE);
        return mono;
    }

    /**
     * Nothing's dot-matrix face (Ndot 57), for MAGI readouts and terminal lines. It belongs to Nothing, so it is read
     * from the phone's own fonts folder rather than shipped; on any other phone it falls back to mono. It is Latin only
     * on purpose: small kanji in dots (承認) turn to mush, so Japanese falls back to the clean system face.
     */
    static Typeface dot(Context c) {
        if (dot == null) {
            try {
                Font font = new Font.Builder(new File("/system/fonts/Ndot-57.otf")).build();
                dot = new Typeface.CustomFallbackBuilder(new FontFamily.Builder(font).build()).setSystemFallback("sans-serif").build();
            } catch (IOException | RuntimeException e) {
                dot = mono(c);
            }
        }
        return dot;
    }

    private static Typeface load(Context c, int res, int weight, String systemFallback, Typeface fallback) {
        try {
            Font font = new Font.Builder(c.getResources(), res).setWeight(weight).build();
            return new Typeface.CustomFallbackBuilder(new FontFamily.Builder(font).build())
                    .setSystemFallback(systemFallback)
                    .setStyle(new FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT))
                    .build();
        } catch (IOException | RuntimeException e) {
            return fallback;
        }
    }
}
