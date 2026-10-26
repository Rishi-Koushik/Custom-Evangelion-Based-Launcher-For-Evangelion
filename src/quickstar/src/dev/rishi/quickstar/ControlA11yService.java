package dev.rishi.quickstar;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hosts the control centre. An accessibility overlay can sit above the status bar, which a normal
 * overlay cannot, so a thin strip along the top edge catches a swipe down and opens the panel.
 * The service reads no screen content; it only draws these two windows and opens the power menu.
 */
public class ControlA11yService extends AccessibilityService {
    static ControlA11yService instance;

    private WindowManager wm;
    private View strip;
    private ControlPanel panel;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Ui.init(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        installStrip();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        // Another app coming to the front (Home, an app launched from the panel) closes the panel.
        if (panel != null && e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && e.getPackageName() != null && !getPackageName().contentEquals(e.getPackageName())
                && !"com.android.systemui".contentEquals(e.getPackageName())) {
            closePanel();
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent i) {
        closePanel();
        removeStrip();
        instance = null;
        return super.onUnbind(i);
    }

    /** Rebuilds the trigger strip after the trigger side changes. */
    void reinstall() {
        removeStrip();
        installStrip();
    }

    private void removeStrip() {
        if (strip != null) {
            try {
                wm.removeView(strip);
            } catch (RuntimeException ignored) {
                // already gone
            }
            strip = null;
        }
    }

    private void installStrip() {
        ControlCfg cfg = ControlCfg.load(this);
        int sbId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int sb = sbId > 0 ? getResources().getDimensionPixelSize(sbId) : Ui.dp(28);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int w = "full".equals(cfg.side) ? screenW : Math.round(screenW * 0.42f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, sb + Ui.dp(6),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | ("left".equals(cfg.side) ? Gravity.START : Gravity.END);
        strip = new View(this);
        strip.setOnTouchListener(new View.OnTouchListener() {
            private float y0;
            private boolean fired;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        y0 = e.getRawY();
                        fired = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!fired && e.getRawY() - y0 > Ui.dp(9)) {
                            fired = true;
                            openPanel();
                            hideShadeSoon();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        wm.addView(strip, lp);
    }

    void openPanel() {
        if (panel != null) return;
        ControlCfg cfg = ControlCfg.load(this);
        panel = new ControlPanel(this, cfg, new ControlPanel.Host() {
            @Override
            public void close() {
                closePanel();
            }

            @Override
            public void start(Intent i) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (RuntimeException ignored) {
                    // no handler for this settings screen on this build
                }
                closePanel();
            }

            @Override
            public void power() {
                closePanel();
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            }
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                PixelFormat.TRANSLUCENT);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.dimAmount = cfg.dim / 100f;
        lp.setBlurBehindRadius(cfg.blur);
        try {
            wm.addView(panel, lp);
        } catch (RuntimeException e) {
            panel = null;
        }
    }

    /** Used by the quick settings tile: dismiss the system shade first, then show our panel. */
    void openAfterShade() {
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::openPanel, 350);
    }

    /** The gesture that opened our panel also starts SystemUI's shade underneath; collapse it. */
    private void hideShadeSoon() {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE), 140);
        h.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE), 520);
    }

    void closePanel() {
        if (panel == null) return;
        try {
            wm.removeView(panel);
        } catch (RuntimeException ignored) {
            // already removed
        }
        panel = null;
    }

    boolean isOpen() {
        return panel != null;
    }
}
