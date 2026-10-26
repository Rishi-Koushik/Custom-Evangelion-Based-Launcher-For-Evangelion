package dev.nervos.home;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/**
 * The theme picker as its own screen. It is allowed to show over the lock screen, so the quick-settings tile can open
 * it from a locked phone: choosing a unit never needs the phone unlocked.
 */
public class ThemeActivity extends Activity {
    private ThemePicker picker;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        getWindow().setDecorFitsSystemWindows(false);
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF050308);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (picker == null) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                show(root, bars.top, bars.bottom);
            }
            return insets;
        });
        // some lock-screen states deliver no insets: fall back to none so the picker always appears
        root.post(() -> {
            if (picker == null) show(root, 0, 0);
        });
    }

    private void show(FrameLayout root, int top, int bottom) {
        picker = new ThemePicker(this, UnitStore.current(this), top, bottom, new ThemePicker.Listener() {
            @Override
            public void onApply(Units.Unit u, boolean xdark) {
                UnitStore.apply(ThemeActivity.this, u, xdark, getSystemService(PowerManager.class).isPowerSaveMode());
                finishAndRemoveTask();
            }

            @Override
            public void onCancel() {
                finishAndRemoveTask();
            }
        });
        root.addView(picker, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    public void onBackPressed() {
        finishAndRemoveTask();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // leaving (screen off, home) closes the picker rather than leaving it parked over the lock screen
        if (!isFinishing()) finishAndRemoveTask();
    }
}
