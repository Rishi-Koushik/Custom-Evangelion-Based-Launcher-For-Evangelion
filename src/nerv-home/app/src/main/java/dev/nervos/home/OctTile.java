package dev.nervos.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Octagon tile holding other drawables: an app's own icon (apps without a NERV icon) or the 2x2 preview
 * of a folder. In saver mode it is a black tile with a thin purple outline.
 */
final class OctTile extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Drawable[] inner;
    private final float innerScale;

    OctTile(int fillColour, int strokeColour, float innerScale, Drawable... inner) {
        fill.setColor(fillColour);
        stroke.setColor(strokeColour);
        stroke.setStyle(Paint.Style.STROKE);
        this.inner = inner;
        this.innerScale = innerScale;
    }

    @Override
    protected void onBoundsChange(Rect full) {
        // same 2/48 margin as the NERV icon vectors, so framed tiles line up with them
        int m = Math.round(full.width() * 2 / 48f);
        Rect b = new Rect(full.left + m, full.top + m, full.right - m, full.bottom - m);
        float w = b.width(), h = b.height(), cw = w * 0.24f, ch = h * 0.24f;
        path.reset();
        path.moveTo(b.left + cw, b.top);
        path.lineTo(b.right - cw, b.top);
        path.lineTo(b.right, b.top + ch);
        path.lineTo(b.right, b.bottom - ch);
        path.lineTo(b.right - cw, b.bottom);
        path.lineTo(b.left + cw, b.bottom);
        path.lineTo(b.left, b.bottom - ch);
        path.lineTo(b.left, b.top + ch);
        path.close();
        stroke.setStrokeWidth(Math.max(1.5f, w * 0.025f));
        if (inner.length == 1) {
            int s = (int) (w * innerScale), l = b.left + (b.width() - s) / 2, t = b.top + (b.height() - s) / 2;
            inner[0].setBounds(l, t, l + s, t + s);
        } else {
            // up to four mini tiles in a 2x2 grid (folder preview)
            int s = (int) (w * innerScale), gap = (int) (w * 0.06f), l = b.left + (b.width() - 2 * s - gap) / 2,
                    t = b.top + (b.height() - 2 * s - gap) / 2;
            for (int i = 0; i < inner.length && i < 4; i++) {
                int x = l + (i % 2) * (s + gap), y = t + (i / 2) * (s + gap);
                inner[i].setBounds(x, y, x + s, y + s);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (fill.getAlpha() > 0) canvas.drawPath(path, fill);
        if (stroke.getAlpha() > 0) canvas.drawPath(path, stroke);
        for (int i = 0; i < inner.length && i < 4; i++) inner[i].draw(canvas);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
