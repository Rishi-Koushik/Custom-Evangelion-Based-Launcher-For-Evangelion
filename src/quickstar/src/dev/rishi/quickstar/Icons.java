package dev.rishi.quickstar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Line icons drawn on a 24-unit grid, so the control centre needs no image assets. */
final class Icons {
    private Icons() {}

    static void draw(Canvas c, String name, float cx, float cy, float size, int color) {
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(size / 24f, size / 24f);
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setColor(color);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(1.9f);
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setStrokeJoin(Paint.Join.ROUND);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(color);
        switch (name) {
            case "wifi":
                for (float r : new float[]{5f, 10f, 15f}) c.drawArc(new RectF(12 - r, 19 - r, 12 + r, 19 + r), 232, 76, false, s);
                c.drawCircle(12, 19, 1.5f, f);
                break;
            case "bt":
                c.drawPath(poly(false, 7, 7, 17, 17, 12, 22, 12, 2, 17, 7, 7, 17), s);
                break;
            case "rotate":
                c.drawArc(new RectF(5, 5, 19, 19), 200, 250, false, s);
                c.drawPath(poly(true, 12, 15.5f, 12, 22.5f, 7, 19), f);
                break;
            case "airplane":
                c.drawPath(poly(true, 12, 2, 14, 9, 22, 14, 22, 16, 14, 13.5f, 13.5f, 19, 16, 21, 16, 22, 12, 21, 8, 22, 8, 21,
                        10.5f, 19, 10, 13.5f, 2, 16, 2, 14, 10, 9), f);
                break;
            case "flash":
                c.drawPath(poly(true, 9, 2.5f, 15, 2.5f, 15, 6, 16.5f, 8.5f, 16.5f, 21.5f, 7.5f, 21.5f, 7.5f, 8.5f, 9, 6), s);
                c.drawLine(12, 11, 12, 15, s);
                break;
            case "data":
                c.drawLine(8, 20, 8, 5, s);
                c.drawPath(poly(false, 4.5f, 8.5f, 8, 5, 11.5f, 8.5f), s);
                c.drawLine(16, 4, 16, 19, s);
                c.drawPath(poly(false, 12.5f, 15.5f, 16, 19, 19.5f, 15.5f), s);
                break;
            case "cast": {
                Path p = new Path();
                p.moveTo(3, 8);
                p.lineTo(3, 5);
                p.lineTo(21, 5);
                p.lineTo(21, 19);
                p.lineTo(14, 19);
                c.drawPath(p, s);
                c.drawArc(new RectF(-1, 15, 7, 23), 270, 90, false, s);
                c.drawArc(new RectF(-5, 11, 11, 27), 270, 90, false, s);
                c.drawCircle(3.5f, 19.5f, 1.2f, f);
                break;
            }
            case "saver":
                c.drawRoundRect(new RectF(6.5f, 4.5f, 17.5f, 21.5f), 2.5f, 2.5f, s);
                c.drawLine(10, 2.5f, 14, 2.5f, s);
                c.drawLine(12, 10, 12, 16, s);
                c.drawLine(9, 13, 15, 13, s);
                break;
            case "location": {
                Path p = new Path();
                p.moveTo(12, 22);
                p.cubicTo(12, 22, 5, 15, 5, 10);
                p.arcTo(new RectF(5, 3, 19, 17), 180, 180);
                p.cubicTo(19, 15, 12, 22, 12, 22);
                c.drawPath(p, s);
                c.drawCircle(12, 10, 2.6f, s);
                break;
            }
            case "moon": {
                Path m = new Path();
                m.addCircle(12, 12, 9, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(17, 8, 8, Path.Direction.CW);
                m.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(m, f);
                break;
            }
            case "mute":
                c.drawPath(poly(true, 4, 9, 8, 9, 13, 5, 13, 19, 8, 15, 4, 15), s);
                c.drawLine(16.5f, 9.5f, 21, 14.5f, s);
                c.drawLine(21, 9.5f, 16.5f, 14.5f, s);
                break;
            case "volume":
                c.drawPath(poly(true, 4, 9, 8, 9, 13, 5, 13, 19, 8, 15, 4, 15), s);
                c.drawArc(new RectF(10, 8, 18, 16), -50, 100, false, s);
                c.drawArc(new RectF(8, 4.5f, 21, 19.5f), -50, 100, false, s);
                break;
            case "sun":
                c.drawCircle(12, 12, 4, s);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(12 + (float) Math.cos(a) * 7, 12 + (float) Math.sin(a) * 7,
                            12 + (float) Math.cos(a) * 9.5f, 12 + (float) Math.sin(a) * 9.5f, s);
                }
                break;
            case "note":
                c.drawLine(15, 4, 15, 17, s);
                c.drawCircle(12, 17.5f, 3, f);
                c.drawPath(poly(false, 15, 4, 19.5f, 6.5f), s);
                break;
            case "prev":
                c.drawLine(6, 5.5f, 6, 18.5f, s);
                c.drawPath(poly(true, 19, 5.5f, 9, 12, 19, 18.5f), f);
                break;
            case "next":
                c.drawLine(18, 5.5f, 18, 18.5f, s);
                c.drawPath(poly(true, 5, 5.5f, 15, 12, 5, 18.5f), f);
                break;
            case "play":
                c.drawPath(poly(true, 7.5f, 4.5f, 19.5f, 12, 7.5f, 19.5f), f);
                break;
            case "pause":
                c.drawRoundRect(new RectF(6.5f, 5, 10.5f, 19), 1, 1, f);
                c.drawRoundRect(new RectF(13.5f, 5, 17.5f, 19), 1, 1, f);
                break;
            case "pencil":
                c.drawPath(poly(true, 4, 20, 4, 16, 15, 5, 19, 9, 8, 20), s);
                c.drawLine(13, 7, 17, 11, s);
                break;
            case "power":
                c.drawArc(new RectF(5, 5, 19, 19), -60, 300, false, s);
                c.drawLine(12, 3, 12, 11.5f, s);
                break;
            case "gear":
                c.drawCircle(12, 12, 3, s);
                c.drawCircle(12, 12, 7, s);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(12 + (float) Math.cos(a) * 7, 12 + (float) Math.sin(a) * 7,
                            12 + (float) Math.cos(a) * 10, 12 + (float) Math.sin(a) * 10, s);
                }
                break;
            case "sim":
                c.drawPath(poly(true, 7, 3, 15, 3, 19, 7, 19, 21, 7, 21), s);
                c.drawRoundRect(new RectF(10, 11.5f, 16, 17.5f), 1, 1, s);
                break;
            case "modes":
                c.drawCircle(12, 12, 8.5f, s);
                c.drawArc(new RectF(5.5f, 5.5f, 18.5f, 18.5f), -90, 90, true, f);
                break;
            case "dnd":
                c.drawCircle(12, 12, 8.5f, s);
                c.drawLine(8, 12, 16, 12, s);
                break;
            case "mirror":
                c.drawArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), 200, 250, false, s);
                c.drawPath(poly(true, 10, 9, 15.5f, 12, 10, 15), f);
                break;
            case "hotspot":
                c.drawCircle(12, 12, 1.8f, f);
                c.drawArc(new RectF(7.5f, 7.5f, 16.5f, 16.5f), 130, 100, false, s);
                c.drawArc(new RectF(7.5f, 7.5f, 16.5f, 16.5f), -50, 100, false, s);
                c.drawArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), 130, 100, false, s);
                c.drawArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), -50, 100, false, s);
                break;
            default:
                c.drawCircle(12, 12, 8, s);
        }
        c.restore();
    }

    private static Path poly(boolean close, float... pts) {
        Path p = new Path();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        if (close) p.close();
        return p;
    }

    /** A view that draws one icon centred in itself. */
    static final class IconView extends View {
        private String name;
        private int color = 0xFFFFFFFF;
        private float scale = 0.5f;

        IconView(Context c, String name) {
            super(c);
            this.name = name;
        }

        IconView scale(float s) {
            scale = s;
            return this;
        }

        void set(String n, int col) {
            name = n;
            color = col;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            Icons.draw(c, name, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) * scale / 0.5f * 0.5f, color);
        }
    }
}
