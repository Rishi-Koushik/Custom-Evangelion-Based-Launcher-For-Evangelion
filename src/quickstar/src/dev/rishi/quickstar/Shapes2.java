package dev.rishi.quickstar;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Cut-corner shapes and the hex-grid backdrop used by the Evangelion themes. */
final class Shapes2 {
    private Shapes2() {}

    /** Octagon-style cut on all four corners (frac of the short side) or a NERV two-corner cut. */
    static Path chamfer(float w, float h, float cut, boolean octagon) {
        float k = Math.min(cut, Math.min(w, h) / 2f);
        Path p = new Path();
        if (octagon) {
            p.moveTo(k, 0);
            p.lineTo(w - k, 0);
            p.lineTo(w, k);
            p.lineTo(w, h - k);
            p.lineTo(w - k, h);
            p.lineTo(k, h);
            p.lineTo(0, h - k);
            p.lineTo(0, k);
        } else {
            p.moveTo(k, 0);
            p.lineTo(w, 0);
            p.lineTo(w, h - k);
            p.lineTo(w - k, h);
            p.lineTo(0, h);
            p.lineTo(0, k);
        }
        p.close();
        return p;
    }

    static final class Cut extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float cutPx;
        private final float cutFrac;
        private final boolean octagon;
        private final float strokePx;
        private Path path = new Path();

        /** cutFrac > 0 makes an octagon sized from the shape; otherwise a two-corner cut of cutPx. */
        Cut(int fillColor, int strokeColor, float strokePx, float cutPx, float cutFrac) {
            fill.setColor(fillColor);
            line.setColor(strokeColor);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(strokePx);
            line.setStrokeJoin(Paint.Join.MITER);
            this.strokePx = strokePx;
            this.cutPx = cutPx;
            this.cutFrac = cutFrac;
            this.octagon = cutFrac > 0;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float in = strokePx / 2f;
            float w = b.width() - strokePx, h = b.height() - strokePx;
            float cut = octagon ? Math.min(w, h) * cutFrac : cutPx;
            path = chamfer(w, h, cut, octagon);
            path.offset(b.left + in, b.top + in);
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, fill);
            if (strokePx > 0 && line.getColor() != 0) c.drawPath(path, line);
        }

        @Override
        public void setAlpha(int a) {}

        @Override
        public void setColorFilter(ColorFilter f) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Dark scrim with a faint hex grid, like the NERV launcher wallpaper. */
    static final class Hex extends Drawable {
        private final Paint fill = new Paint();
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float r;
        private Path grid = new Path();

        Hex(int bg, int gridColor, float r) {
            fill.setColor(bg);
            line.setColor(gridColor);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(1f, r / 22f));
            this.r = r;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            grid = new Path();
            float hw = (float) (Math.sqrt(3) * r), step = r * 1.5f;
            int rows = (int) (b.height() / step) + 2, cols = (int) (b.width() / hw) + 2;
            for (int j = -1; j < rows; j++) {
                for (int i = -1; i < cols; i++) {
                    float cx = b.left + i * hw + (j % 2 == 0 ? 0 : hw / 2f), cy = b.top + j * step;
                    for (int k = 0; k < 6; k++) {
                        double a = Math.toRadians(60 * k - 30);
                        float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
                        if (k == 0) grid.moveTo(x, y);
                        else grid.lineTo(x, y);
                    }
                    grid.close();
                }
            }
        }

        @Override
        public void draw(Canvas c) {
            c.drawRect(getBounds(), fill);
            c.drawPath(grid, line);
        }

        @Override
        public void setAlpha(int a) {}

        @Override
        public void setColorFilter(ColorFilter f) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
