package dev.nervos.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Choreographer;
import android.view.View;

/**
 * Unlock effect: a wave of hexagons that unfold outward from the fingerprint sensor, on the same grid as the wallpaper's
 * hex pattern and with the same timing the old glass ripple had (front speed, head start, and 2.2 s of wave time).
 * Each hexagon opens from nothing as the front reaches it, glows as the front passes, then fades away behind it.
 */
final class HexWave extends View {
    interface Done {
        void onDone();
    }

    /** Wave-time units after which everything is gone; same as the ripple's cut-off. */
    private static final float END = 2.2f;

    private final Units.Unit unit;
    private final float cx, cy, s, speed;
    private final long start;
    private final Done done;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hex = new Path();
    private boolean running = true;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            if (!running) return;
            if (waveTime() > END) {
                running = false;
                done.onDone();
                return;
            }
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    HexWave(Context c, Units.Unit unit, float cx, float cy, float scale, float speed, long headStartNs, Done done) {
        super(c);
        this.unit = unit;
        this.cx = cx;
        this.cy = cy;
        this.s = scale;
        this.speed = speed;
        this.done = done;
        this.start = System.nanoTime() - headStartNs;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.MITER);
        stroke.setStrokeWidth(Math.max(2f, 1.6f * scale));
        fill.setStyle(Paint.Style.FILL);
        setClickable(false);
        Choreographer.getInstance().postFrameCallback(frame);
    }

    void cancel() {
        running = false;
    }

    private float waveTime() {
        return (System.nanoTime() - start) / 1e9f * speed;
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    private static int alpha(int colour, float a) {
        return (Math.round(255f * Math.max(0f, Math.min(1f, a))) << 24) | (colour & 0xFFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = Math.max(0f, waveTime());
        float front = t * 420f * s; // same front speed as the ripple it replaces
        float life = 1f - smooth(0.2f, 2.1f, t);
        if (life <= 0f) return;

        // the wallpaper's own grid (see Skyline): radius 22 units, pointy-top, odd rows shifted half a cell
        float r = 22f * s, hw = (float) (r * Math.sqrt(3)), rowH = r * 1.5f;
        int rows = (int) (getHeight() / rowH) + 2, cols = (int) (getWidth() / hw) + 2;
        for (int row = -1; row < rows; row++) {
            for (int col = -1; col < cols; col++) {
                float x = col * hw + (row % 2 == 0 ? 0 : hw / 2f), y = row * rowH;
                float d = (float) Math.hypot(x - cx, y - cy);
                float ahead = d - front; // > 0: the front has not got here yet
                float open = smooth(0f, 1f, (150f * s - ahead) / (170f * s)); // unfolds as the front approaches
                if (open <= 0.01f) continue;
                float behind = Math.max(0f, -ahead) / (150f * s);
                float trail = (float) Math.exp(-behind * behind); // lingers, then fades behind the front
                float glow = (float) Math.exp(-(ahead * ahead) / (2200f * s * s)); // brightest right at the front
                float a = life * trail * open;
                if (a < 0.02f) continue;

                float scale = r * (0.2f + 0.8f * open);
                float spin = (1f - open) * (float) (Math.PI / 3);
                hex.reset();
                for (int i = 0; i < 6; i++) {
                    double ang = Math.PI / 6 + i * Math.PI / 3 + spin;
                    float px = x + (float) (scale * Math.cos(ang)), py = y + (float) (scale * Math.sin(ang));
                    if (i == 0) hex.moveTo(px, py);
                    else hex.lineTo(px, py);
                }
                hex.close();
                fill.setColor(alpha(unit.a2, 0.30f * a + 0.20f * glow * life));
                c.drawPath(hex, fill);
                stroke.setColor(alpha(unit.a, Math.min(1f, 0.55f * a + 0.60f * glow * life)));
                c.drawPath(hex, stroke);
            }
        }
    }
}
