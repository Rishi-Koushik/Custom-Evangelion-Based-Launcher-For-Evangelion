package dev.nervos.glyph;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphMatrixManager;

import java.util.Arrays;

/**
 * The charging A.T. Field: ~3.5 s of octagon rings over the lock screen when a charger is plugged in, mirrored on the
 * rear Glyph Matrix. Shown over the lock screen without unlocking (it only displays this animation), tap to dismiss.
 * Nothing only accepts app frames on the matrix while the app is in front, which is why this is an activity.
 */
public class ChargeFxActivity extends Activity {
    private static final String TAG = "ChargeFx";
    static final long DURATION = 3600;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] px = new int[Px.N * Px.N];
    private final Frames frames = new Frames();
    private GlyphMatrixManager gm;
    private boolean glyphReady;
    private long start;

    private final Runnable glyphTick = new Runnable() {
        @Override
        public void run() {
            if (!glyphReady || gm == null) return;
            Arrays.fill(px, 0);
            Frames.atField(px, SystemClock.uptimeMillis() - start);
            try {
                gm.setAppMatrixFrame(frames.render(getApplicationContext(), px));
            } catch (GlyphException e) {
                Log.w(TAG, "matrix frame rejected", e);
            }
            handler.postDelayed(this, 66);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setDecorFitsSystemWindows(false);
        View field = new FieldView(this, UnitReceiver.current(this), getSystemService(PowerManager.class).isPowerSaveMode());
        field.setOnClickListener(v -> finish());
        setContentView(field);
        getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
        start = SystemClock.uptimeMillis();
        handler.postDelayed(this::finish, DURATION);

        gm = GlyphMatrixManager.getInstance(getApplicationContext());
        gm.init(new GlyphMatrixManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName name) {
                if (gm == null) return;
                gm.register(Glyph.DEVICE_23112);
                glyphReady = true;
                glyphTick.run();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                glyphReady = false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        glyphReady = false;
        if (gm != null) {
            try {
                gm.closeAppMatrix();
            } catch (GlyphException ignored) {
            }
            gm.unInit();
            gm = null;
        }
        super.onDestroy();
    }

    /** Octagon rings from the centre in the unit's colours, battery level in the middle. Glow-free in battery saver. */
    private static final class FieldView extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
                big = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path oct = new Path();
        private final int ring, core, label;
        private final boolean saver;
        private final long start = SystemClock.uptimeMillis();
        private final String level;

        FieldView(Context c, Units.Unit u, boolean saver) {
            super(c);
            this.saver = saver;
            ring = saver ? u.sv : u.a2;
            core = saver ? u.svDim : u.a;
            label = saver ? u.sv : u.a;
            glow.setStyle(Paint.Style.STROKE);
            line.setStyle(Paint.Style.STROKE);
            big.setTypeface(Fonts.serif(c));
            big.setTextAlign(Paint.Align.CENTER);
            big.setTextScaleX(0.86f);
            small.setTypeface(Fonts.dot(c));
            small.setTextAlign(Paint.Align.CENTER);
            small.setLetterSpacing(0.3f);
            level = c.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%";
            setBackgroundColor(Color.BLACK);
        }

        private void octagon(float cx, float cy, float r) {
            oct.reset();
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 8 + i * Math.PI / 4;
                float x = cx + (float) (r * Math.cos(a)), y = cy + (float) (r * Math.sin(a));
                if (i == 0) oct.moveTo(x, y); else oct.lineTo(x, y);
            }
            oct.close();
        }

        @Override
        protected void onDraw(Canvas c) {
            long el = SystemClock.uptimeMillis() - start;
            float s = getWidth() / 393f, cx = getWidth() / 2f, cy = getHeight() * 0.46f;
            float fade = Math.max(0, Math.min(1, Math.min(el / 250f, (DURATION - el) / 500f)));
            setAlpha(fade);
            for (long b = 0; b < DURATION - 1000; b += 280) {
                float a = (el - b) / 1500f;
                if (a <= 0 || a >= 1) continue;
                float r = (30 + (1 - (float) Math.pow(1 - a, 3)) * 360) * s, al = (1 - a) * 0.9f;
                octagon(cx, cy, r);
                if (!saver) {
                    glow.setStrokeWidth((14 * (1 - a) + 4) * s);
                    glow.setColor(withAlpha(ring, al * 0.2f));
                    c.drawPath(oct, glow);
                }
                line.setStrokeWidth((2.4f * (1 - a) + 1) * s);
                line.setColor(withAlpha(ring, al));
                c.drawPath(oct, line);
                line.setStrokeWidth(1 * s);
                line.setColor(withAlpha(core, al * 0.6f));
                octagon(cx, cy, r * 0.93f);
                c.drawPath(oct, line);
            }
            big.setTextSize(92 * s);
            big.setColor(saver ? ring : 0xFFFFF3E4);
            c.drawText(level, cx, cy + 30 * s, big);
            small.setTextSize(11 * s);
            small.setColor(label);
            c.drawText("EXTERNAL POWER · CONNECTED", cx, cy + 62 * s, small);
            if (el < DURATION) postInvalidateOnAnimation();
        }

        private static int withAlpha(int colour, float a) {
            return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (colour & 0xFFFFFF);
        }
    }
}
