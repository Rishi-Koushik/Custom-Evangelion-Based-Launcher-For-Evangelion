package dev.nervos.glyph;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.util.Log;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphToy;

import java.util.Arrays;

/**
 * Base Glyph Toy. The system binds the service while the toy is on the matrix and unbinds it when the
 * user moves on, so frames are only drawn while someone can see them: nothing runs in the background.
 */
abstract class NervToy extends Service {
    private static final String TAG = "NervToy";

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) { super.handleMessage(msg); return; }
            String event = msg.getData().getString(GlyphToy.MSG_GLYPH_TOY_DATA);
            if (GlyphToy.EVENT_AOD.equals(event)) aod = true; // always-on: one static frame per minute
            onEvent(event);
            redraw();
        }
    };
    private final Messenger messenger = new Messenger(handler);
    private final int[] px = new int[Px.N * Px.N];
    private final Frames frames = new Frames();
    private GlyphMatrixManager gm;
    private boolean ready;
    /** True while the toy is shown as the always-on toy; animate nothing then. */
    protected boolean aod;

    private final Runnable tick = this::redraw;

    @Override
    public IBinder onBind(Intent intent) {
        aod = false;
        gm = GlyphMatrixManager.getInstance(getApplicationContext());
        gm.init(new GlyphMatrixManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName name) {
                if (gm == null) return;
                gm.register(Glyph.DEVICE_23112);
                ready = true;
                onStart();
                redraw();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                ready = false;
            }
        });
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(tick);
        ready = false;
        if (gm != null) {
            gm.turnOff();
            gm.unInit();
            gm = null;
        }
        return false;
    }

    private void redraw() {
        handler.removeCallbacks(tick);
        if (!ready || gm == null) return;
        Arrays.fill(px, 0);
        long next = draw(px, SystemClock.uptimeMillis());
        try {
            gm.setMatrixFrame(frames.render(getApplicationContext(), px));
        } catch (GlyphException e) {
            Log.w(TAG, "frame rejected", e);
        }
        if (next > 0 && !aod) handler.postDelayed(tick, next);
    }

    /** Called once the matrix is ready. */
    protected void onStart() {}

    /** Glyph Button events: GlyphToy.EVENT_CHANGE (long press), EVENT_ACTION_DOWN/UP, EVENT_AOD. */
    protected void onEvent(String event) {}

    /** Fill px (brightness 0-255) for time now; return ms until the next frame, or 0 to stop animating. */
    protected abstract long draw(int[] px, long now);
}
