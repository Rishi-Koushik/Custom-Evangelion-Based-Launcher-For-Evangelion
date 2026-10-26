package dev.nervos.home;

import android.view.Choreographer;

/** Spring physics on fixed 240 Hz steps (same model as the web demo), driven by the display's vsync. */
final class Spring implements Choreographer.FrameCallback {
    interface Listener { void onUpdate(float value); }

    private static final float STEP = 1 / 240f;
    private final Listener listener;
    private final float k, c;
    private float x, v, target, acc;
    private long last;
    private boolean running;

    Spring(float stiffness, float damping, Listener listener) {
        this.k = stiffness;
        this.c = damping;
        this.listener = listener;
    }

    float value() { return x; }

    /** Jump straight to a value, e.g. while a finger is dragging. */
    void set(float value) {
        stop();
        x = value;
        v = 0;
        listener.onUpdate(x);
    }

    /** Spring towards target, starting with velocity in units per second (from a fling). */
    void animateTo(float to, float velocity) {
        target = to;
        v = velocity;
        if (!running) {
            running = true;
            last = 0;
            acc = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    void stop() {
        if (running) Choreographer.getInstance().removeFrameCallback(this);
        running = false;
    }

    @Override
    public void doFrame(long nanos) {
        if (!running) return;
        if (last != 0) {
            acc += Math.min(0.064f, (nanos - last) / 1e9f);
            while (acc >= STEP) {
                v += (-k * (x - target) - c * v) * STEP;
                x += v * STEP;
                acc -= STEP;
            }
        }
        last = nanos;
        if (Math.abs(x - target) < 0.0005f && Math.abs(v) < 0.01f) {
            x = target;
            running = false;
            listener.onUpdate(x);
            return;
        }
        listener.onUpdate(x);
        Choreographer.getInstance().postFrameCallback(this);
    }
}
