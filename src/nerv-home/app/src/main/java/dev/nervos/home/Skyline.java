package dev.nervos.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.List;

/** The demo's Tokyo-3 dusk skyline (EVA-01 colours), drawn at the phone's full resolution. */
final class Skyline {
    private final Units.Unit u;
    private final int A, A2, DEEP, WARN, TOWER;
    private long seed = 11;

    private final boolean xd;

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private Skyline(Units.Unit u, boolean xdark) {
        this.u = u;
        this.xd = xdark;
        A = u.a;
        // extra dark keeps the unit's hues but pulls the bright sky and sunset down toward black
        A2 = xdark ? mix(u.a2, 0xFF000000, 0.62f) : u.a2;
        DEEP = xdark ? mix(u.deep, 0xFF000000, 0.5f) : u.deep;
        WARN = xdark ? mix(u.warn, 0xFF000000, 0.55f) : u.warn;
        TOWER = u.tower;
    }

    private float rnd() {
        seed = (seed * 16807) % 2147483647;
        return seed / 2147483647f;
    }

    private static int alpha(int colour, float a) {
        return (Math.round(a * 255) << 24) | (colour & 0xFFFFFF);
    }

    /** Full wallpaper (sky + towers), used for the system home and lock screens. */
    static Bitmap render(int width, int height, Units.Unit u, boolean xdark) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        new Skyline(u, xdark).sky(c, width, height);
        new Skyline(u, xdark).towers(c, width, height, 216);
        return bmp;
    }

    /** Sky layer only (smooth, so it can be rendered at reduced resolution). */
    static Bitmap sky(int width, int height, Units.Unit u, boolean xdark) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Skyline(u, xdark).sky(new Canvas(bmp), width, height);
        return bmp;
    }

    /** Tower layer on transparent, drawn in front of the depth clock; centralTop is in 393-wide units. */
    static Bitmap towers(int width, int height, float centralTop, Units.Unit u, boolean xdark) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Skyline(u, xdark).towers(new Canvas(bmp), width, height, centralTop);
        return bmp;
    }

    private void sky(Canvas c, int width, int height) {
        float s = width / 393f, W = 393, H = height / s;
        c.save();
        c.scale(s, s);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // sky and sun
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{u.skyEdge, DEEP, A2, WARN, u.skyLow, u.skyEdge},
                new float[]{0, 0.34f, 0.55f, 0.68f, 0.8f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W / 2, H * 0.66f, 170, new int[]{0xF2FFD696, alpha(WARN, 0.7f), 0x00000000},
                new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);

        // faint hex grid in the upper sky
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.7f);
        float hs = 22, hw = (float) (hs * Math.sqrt(3));
        for (int row = 0; row * hs * 1.5f < H * 0.6f; row++) {
            p.setColor(alpha(A, 0.09f * (1 - row * hs * 1.5f / (H * 0.6f))));
            for (int col = -1; col * hw < W + hw; col++) {
                float cx = col * hw + (row % 2) * hw / 2, cy = row * hs * 1.5f;
                Path hex = new Path();
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 6 + i * Math.PI / 3;
                    float x = cx + (float) (hs * Math.cos(a)), y = cy + (float) (hs * Math.sin(a));
                    if (i == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
                }
                hex.close();
                c.drawPath(hex, p);
            }
        }
        p.setStyle(Paint.Style.FILL);

        // distant ridge and low skyline
        Path ridge = new Path();
        ridge.moveTo(0, H);
        for (int x = 0; x <= W; x += 8)
            ridge.lineTo(x, H * 0.64f - (float) Math.sin(x / 40f) * 12 - (float) Math.sin(x / 13f) * 4 - rnd() * 3);
        ridge.lineTo(W, H);
        ridge.close();
        p.setColor(alpha(u.ridge, 0.851f));
        c.drawPath(ridge, p);
        p.setColor(u.low);
        for (float x = -5; x < W; x += 10 + rnd() * 14) {
            float h = 20 + rnd() * 90;
            c.drawRect(x, H * 0.7f - h, x + 8 + rnd() * 14, H, p);
        }
        c.restore();
    }

    private void towers(Canvas c, int width, int height, float centralTop) {
        seed = 29;
        float s = width / 393f, W = 393, H = height / s;
        c.save();
        c.scale(s, s);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // foreground towers with lit windows and aviation lights
        List<float[]> towers = new ArrayList<>(); // x, w, top, tall
        for (float x = -10; x < W + 10; x += 24 + rnd() * 26) towers.add(new float[]{x, 20 + rnd() * 26, H * (0.56f + rnd() * 0.2f), 0});
        towers.add(new float[]{W * 0.5f - 30, 60, centralTop, 1});
        towers.add(new float[]{W * 0.22f - 20, 42, centralTop + 102, 1});
        towers.add(new float[]{W * 0.78f - 22, 46, centralTop + 70, 1});
        // back to front: lower towers first, the three tall ones last
        towers.sort((a, b) -> a[3] != b[3] ? Float.compare(a[3], b[3]) : Float.compare(b[2], a[2]));
        for (float[] t : towers) {
            float x = t[0], w = t[1], top = t[2];
            boolean rimLeft = x + w / 2 < W / 2;
            p.setShader(new LinearGradient(x, 0, x + w, 0,
                    rimLeft ? new int[]{TOWER, TOWER, alpha(WARN, 0.35f)} : new int[]{alpha(WARN, 0.35f), TOWER, TOWER},
                    rimLeft ? new float[]{0, 0.92f, 1} : new float[]{0, 0.06f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(x, top, x + w, H, p);
            p.setShader(null);
            for (float y = top + 10; y < H - 10; y += 7)
                for (float wx = x + 4; wx < x + w - 4; wx += 6)
                    if (rnd() < 0.16f) {
                        p.setColor(rnd() < 0.5f ? alpha(A, 0.5f) : 0x73FFB46E);
                        c.drawRect(wx, y, wx + 2, y + 3, p);
                    }
            if (t[3] == 1) {
                p.setColor(TOWER);
                p.setStrokeWidth(2);
                c.drawLine(x + w / 2, top, x + w / 2, top - 34, p);
                p.setColor(0xFFFF2A2A);
                p.setShadowLayer(10, 0, 0, 0xFFFF2A2A);
                c.drawCircle(x + w / 2, top - 34, 2.2f, p);
                p.clearShadowLayer();
            }
        }
        // street-level glow where a black ground band used to be
        p.setShader(new LinearGradient(0, H * 0.8f, 0, H, new int[]{alpha(A2, 0), alpha(A2, 0.2f), alpha(WARN, 0.3f)},
                new float[]{0, 0.65f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, H * 0.8f, W, H, p);
        p.setShader(null);
        c.restore();
    }
}
