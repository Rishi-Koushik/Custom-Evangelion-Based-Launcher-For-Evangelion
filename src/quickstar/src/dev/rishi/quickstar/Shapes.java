package dev.rishi.quickstar;

import android.graphics.Path;
import android.graphics.RectF;

/** Icon outlines. All paths fill a square of side s. */
final class Shapes {
    private Shapes() {}

    static Path path(String name, float s) {
        Path p = new Path();
        switch (name) {
            case "squircle": {
                int n = 96;
                float half = s / 2f;
                for (int i = 0; i <= n; i++) {
                    double t = 2 * Math.PI * i / n;
                    double ct = Math.cos(t), st = Math.sin(t);
                    float x = half + (float) (Math.signum(ct) * Math.pow(Math.abs(ct), 0.5)) * half;
                    float y = half + (float) (Math.signum(st) * Math.pow(Math.abs(st), 0.5)) * half;
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                p.close();
                break;
            }
            case "rounded":
                p.addRoundRect(new RectF(0, 0, s, s), s * 0.3f, s * 0.3f, Path.Direction.CW);
                break;
            case "square":
                p.addRoundRect(new RectF(0, 0, s, s), s * 0.08f, s * 0.08f, Path.Direction.CW);
                break;
            case "octagon": {
                float k = s * 0.29f;
                poly(p, k, 0, s - k, 0, s, k, s, s - k, s - k, s, k, s, 0, s - k, 0, k);
                break;
            }
            case "hexagon": {
                float q = s * 0.25f;
                poly(p, s / 2, 0, s, q, s, s - q, s / 2, s, 0, s - q, 0, q);
                break;
            }
            default:
                p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW);
        }
        return p;
    }

    private static void poly(Path p, float... pts) {
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        p.close();
    }
}
