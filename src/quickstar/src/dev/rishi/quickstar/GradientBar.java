package dev.rishi.quickstar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Horizontal slider whose track is a colour gradient. Value is 0..1. */
final class GradientBar extends View {
    interface Listener {
        void onChange(float value);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] colors = {0xFF000000, 0xFFFFFFFF};
    private float value;
    private Listener listener;

    GradientBar(Context c) {
        super(c);
        thumb.setColor(0xFFFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFF000000);
        ring.setStrokeWidth(Ui.dp(2));
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setColors(int... c) {
        colors = c;
        invalidate();
    }

    void setValue(float v) {
        value = Math.max(0f, Math.min(1f, v));
        invalidate();
    }

    float getValue() {
        return value;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(200), w), Ui.dp(36));
    }

    @Override
    protected void onDraw(Canvas c) {
        float pad = Ui.dp(16);
        float th = Ui.dp(14);
        float cy = getHeight() / 2f;
        track.setShader(new LinearGradient(pad, 0, getWidth() - pad, 0, colors, null, Shader.TileMode.CLAMP));
        rect.set(pad, cy - th / 2, getWidth() - pad, cy + th / 2);
        c.drawRoundRect(rect, th / 2, th / 2, track);
        float x = pad + value * (getWidth() - 2 * pad);
        c.drawCircle(x, cy, Ui.dp(12), thumb);
        c.drawCircle(x, cy, Ui.dp(11), ring);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
            case MotionEvent.ACTION_MOVE:
                float pad = Ui.dp(16);
                setValue((e.getX() - pad) / (getWidth() - 2 * pad));
                if (listener != null) listener.onChange(value);
                return true;
            default:
                return true;
        }
    }
}
