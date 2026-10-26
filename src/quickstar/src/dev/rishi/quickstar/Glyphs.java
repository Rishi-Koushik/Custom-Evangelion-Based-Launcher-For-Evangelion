package dev.rishi.quickstar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** Built-in tile icons, drawn as white shapes so SystemUI can tint them for on/off. */
final class Glyphs {
    static final String[] NAMES = {"star", "bolt", "heart", "home", "bell", "sun", "moon", "dots", "ring", "plus"};

    private Glyphs() {}

    static Bitmap draw(String name, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        float s = px / 24f;
        c.scale(s, s);
        switch (name) {
            case "bolt":
                c.drawPath(poly(13, 2, 5, 13.5f, 11, 13.5f, 10, 22, 19, 10, 13, 10, 13, 2), p);
                break;
            case "heart": {
                Path h = new Path();
                h.moveTo(12, 21);
                h.cubicTo(3, 14, 2, 9, 5.5f, 5.5f);
                h.cubicTo(8, 3.5f, 11, 5, 12, 7.5f);
                h.cubicTo(13, 5, 16, 3.5f, 18.5f, 5.5f);
                h.cubicTo(22, 9, 21, 14, 12, 21);
                c.drawPath(h, p);
                break;
            }
            case "home":
                c.drawPath(poly(12, 3, 2, 12, 5, 12, 5, 21, 10, 21, 10, 15, 14, 15, 14, 21, 19, 21, 19, 12, 22, 12, 12, 3), p);
                break;
            case "bell": {
                Path h = new Path();
                h.moveTo(5, 18);
                h.lineTo(5, 11);
                h.cubicTo(5, 7, 8, 4.5f, 12, 4.5f);
                h.cubicTo(16, 4.5f, 19, 7, 19, 11);
                h.lineTo(19, 18);
                h.lineTo(21, 19.5f);
                h.lineTo(3, 19.5f);
                h.close();
                c.drawPath(h, p);
                c.drawCircle(12, 21.2f, 1.8f, p);
                break;
            }
            case "sun":
                c.drawCircle(12, 12, 4.5f, p);
                p.setStrokeWidth(2);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(12 + (float) Math.cos(a) * 7.5f, 12 + (float) Math.sin(a) * 7.5f,
                            12 + (float) Math.cos(a) * 10, 12 + (float) Math.sin(a) * 10, p);
                }
                break;
            case "moon": {
                Path m = new Path();
                m.addCircle(12, 12, 9, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(17, 8, 8, Path.Direction.CW);
                m.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(m, p);
                break;
            }
            case "dots":
                for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) c.drawCircle(4.5f + x * 5, 4.5f + y * 5, 1.5f, p);
                break;
            case "ring":
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                c.drawCircle(12, 12, 8, p);
                break;
            case "plus":
                c.drawRect(10.5f, 3, 13.5f, 21, p);
                c.drawRect(3, 10.5f, 21, 13.5f, p);
                break;
            default:
                c.drawPath(poly(12, 2, 14.9f, 8.9f, 22, 9.6f, 16.6f, 14.4f, 18.2f, 22, 12, 18.2f, 5.8f, 22,
                        7.4f, 14.4f, 2, 9.6f, 9.1f, 8.9f, 12, 2), p);
        }
        return b;
    }

    private static Path poly(float... pts) {
        Path p = new Path();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        p.close();
        return p;
    }

    /**
     * Turns any picked image into a white alpha mask. Transparent images keep their alpha; opaque
     * ones (photos, screenshots) use luminance, flipped if the picture is mostly light.
     */
    static Bitmap toMask(Bitmap src, int px) {
        Bitmap b = Bitmap.createScaledBitmap(src, px, px, true);
        int[] pix = new int[px * px];
        b.getPixels(pix, 0, px, 0, 0, px, px);
        boolean hasAlpha = false;
        double lum = 0;
        for (int v : pix) {
            if (Color.alpha(v) < 250) hasAlpha = true;
            lum += (0.299 * Color.red(v) + 0.587 * Color.green(v) + 0.114 * Color.blue(v)) / 255.0;
        }
        boolean invert = lum / pix.length > 0.5;
        for (int i = 0; i < pix.length; i++) {
            int v = pix[i];
            int a;
            if (hasAlpha) {
                a = Color.alpha(v);
            } else {
                int l = (int) (0.299 * Color.red(v) + 0.587 * Color.green(v) + 0.114 * Color.blue(v));
                a = invert ? 255 - l : l;
            }
            pix[i] = Color.argb(a, 255, 255, 255);
        }
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        out.setPixels(pix, 0, px, 0, 0, px, px);
        return out;
    }
}
