package dev.nervos.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.view.View;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

/**
 * The lock-screen clock face. Nothing OS 5 has four, picked by id in the clock-face setting (Quickstar writes it): the
 * default serif, Micrographics (terminal mono), Ndot (dot matrix) and Graphic (a dial). The choice is saved here and
 * sent along with every unit change, so it stays until it is changed again. Also a preview of each face for the
 * theme picker, in the unit's lock-clock colour.
 */
final class LockClock {
    static final String[] IDS = {"DEFAULT", "MICROGRAPHICS", "NDOT", "GRAPHIC"};
    static final String[] LABELS = {"SERIF", "TERMINAL", "DOT", "DIAL"};

    private LockClock() {}

    static String current(Context c) {
        return c.getSharedPreferences("nerv", Context.MODE_PRIVATE).getString("clock_face", "DEFAULT");
    }

    static void save(Context c, String id) {
        c.getSharedPreferences("nerv", Context.MODE_PRIVATE).edit().putString("clock_face", id).apply();
    }

    /** Same colours Quickstar gives the real clock: Misato green, Rei sky blue, Asuka orange, Mari pink. */
    static int colour(Units.Unit u) {
        switch (u.id) {
            case "unit00": return u.a2;
            case "unit08": return u.sv;
            default: return u.a;
        }
    }

    private static Typeface headlineFace, monoFace;

    /** Nothing's own font for a face, read from the phone (not shipped); the NERV fonts stand in elsewhere. */
    private static Typeface system(String file, Typeface fallback) {
        try {
            Font f = new Font.Builder(new File("/system/fonts/" + file)).build();
            return new Typeface.CustomFallbackBuilder(new FontFamily.Builder(f).build()).setSystemFallback("sans-serif").build();
        } catch (Exception e) {
            return fallback;
        }
    }

    /** A rough likeness of the lock-screen clock, laid out as Nothing does: date, then the time, top left. */
    static final class Preview extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface headline, mono, dot;
        private String face;
        private int colour;

        Preview(Context c, String face, int colour) {
            super(c);
            this.face = face;
            this.colour = colour;
            if (headlineFace == null) headlineFace = system("NType82-Headline.otf", Typeface.SERIF);
            if (monoFace == null) monoFace = system("LetteraMonoLL-Regular.otf", Fonts.mono(c));
            headline = headlineFace;
            mono = monoFace;
            dot = Fonts.dot(c);
        }

        void setFace(String face) {
            this.face = face;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, w = getWidth();
            Calendar cal = Calendar.getInstance();
            String hh = String.format(Locale.ROOT, "%d", cal.get(Calendar.HOUR) == 0 ? 12 : cal.get(Calendar.HOUR));
            if (android.text.format.DateFormat.is24HourFormat(getContext())) hh = String.format(Locale.ROOT, "%02d", cal.get(Calendar.HOUR_OF_DAY));
            String mm = String.format(Locale.ROOT, "%02d", cal.get(Calendar.MINUTE));
            String date = String.format(Locale.ENGLISH, "%ta %<td %<tb", cal);
            p.setColor(colour);
            p.setStyle(Paint.Style.FILL);
            float x = 18 * d;
            switch (face) {
                case "MICROGRAPHICS": {
                    p.setTypeface(mono);
                    p.setTextSize(22 * d);
                    String day = String.format(Locale.ENGLISH, "%ta %<td, %<tb", cal).toUpperCase(Locale.ROOT);
                    c.drawText(day, x, 34 * d, p);
                    String t = hh + ":" + mm;
                    c.drawText(t, x, 64 * d, p);
                    float cx = x + p.measureText(t) + 6 * d;
                    c.drawRect(cx, 46 * d, cx + 11 * d, 66 * d, p); // the terminal cursor
                    break;
                }
                case "NDOT": {
                    small(c, date, x, d);
                    p.setTypeface(dot);
                    p.setTextSize(96 * d);
                    c.drawText(hh, x, 120 * d, p);
                    p.setAlpha(170);
                    c.drawText(mm, x + p.measureText(hh + " "), 120 * d, p);
                    p.setAlpha(255);
                    break;
                }
                case "GRAPHIC": {
                    small(c, date, x, d);
                    dial(c, w / 2, 96 * d, 64 * d, cal, d);
                    break;
                }
                default: {
                    small(c, date, x, d);
                    p.setTypeface(headline);
                    p.setTextSize(104 * d);
                    c.drawText(hh + ":" + mm, x - 4 * d, 124 * d, p);
                }
            }
        }

        private void small(Canvas c, String date, float x, float d) {
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(15 * d);
            c.drawText(date, x, 22 * d, p);
        }

        /** Radial ticks, hour and minute hands, a red second hand. */
        private void dial(Canvas c, float cx, float cy, float r, Calendar cal, float d) {
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 60; i++) {
                double a = Math.PI * 2 * i / 60;
                boolean major = i % 5 == 0;
                float r0 = r * (major ? 0.78f : 0.86f);
                p.setStrokeWidth((major ? 2.4f : 1f) * d);
                p.setAlpha(major ? 255 : 120);
                c.drawLine(cx + (float) Math.sin(a) * r0, cy - (float) Math.cos(a) * r0,
                        cx + (float) Math.sin(a) * r, cy - (float) Math.cos(a) * r, p);
            }
            p.setAlpha(255);
            double ha = Math.PI * 2 * ((cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60.0) / 12);
            double ma = Math.PI * 2 * cal.get(Calendar.MINUTE) / 60;
            p.setStrokeWidth(4 * d);
            c.drawLine(cx, cy, cx + (float) Math.sin(ha) * r * 0.45f, cy - (float) Math.cos(ha) * r * 0.45f, p);
            p.setStrokeWidth(2.6f * d);
            c.drawLine(cx, cy, cx + (float) Math.sin(ma) * r * 0.7f, cy - (float) Math.cos(ma) * r * 0.7f, p);
            p.setColor(0xFFE0243A);
            p.setStrokeWidth(1.4f * d);
            double sa = Math.PI * 2 * cal.get(Calendar.SECOND) / 60;
            c.drawLine(cx, cy, cx + (float) Math.sin(sa) * r * 0.8f, cy - (float) Math.cos(sa) * r * 0.8f, p);
            c.drawCircle(cx, cy, 3.4f * d, p);
            p.setColor(colour);
        }
    }
}
