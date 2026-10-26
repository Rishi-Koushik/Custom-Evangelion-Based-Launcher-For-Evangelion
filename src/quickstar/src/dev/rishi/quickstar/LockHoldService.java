package dev.rishi.quickstar;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;
import java.util.Locale;

/**
 * Hold the lock screen to switch NERV themes. Nothing's lock screen shows a "Customise lock screen" button when you
 * hold it; that button announces itself to accessibility services. This service listens for exactly that one event from
 * SystemUI (it is not allowed to see any other app, and it reads no screen content) and opens the NERV theme picker over
 * the lock screen in its place. It also hosts the NERV notification banners (BannerStack), drawn as its overlay.
 */
public class LockHoldService extends AccessibilityService {
    private long last;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // also the surface the NERV notification banners are drawn on (see BannerStack)
        BannerStack.instance = new BannerStack(this);
        NervNotifs.bannerUp = true;
        NervNotifs.sync(this);
    }

    @Override
    public boolean onUnbind(Intent i) {
        BannerStack s = BannerStack.instance;
        BannerStack.instance = null;
        if (s != null) s.clear();
        NervNotifs.bannerUp = false;
        NervNotifs.sync(this);
        return super.onUnbind(i);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        // only system UI is looked at; every other package is ignored before its text is touched
        if (e.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED || e.getPackageName() == null
                || !"com.android.systemui".contentEquals(e.getPackageName())) return;
        List<CharSequence> text = e.getText();
        if (text == null) return;
        StringBuilder sb = new StringBuilder();
        for (CharSequence t : text) sb.append(t).append(' ');
        String s = sb.toString().toLowerCase(Locale.ROOT);
        if (!s.contains("lock screen") || !(s.contains("customis") || s.contains("customiz"))) return;
        if (!getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        long now = SystemClock.uptimeMillis();
        if (now - last < 1500) return;
        last = now;
        Intent i = new Intent().setComponent(new ComponentName("dev.nervos.home", "dev.nervos.home.ThemeActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (RuntimeException ignored) {
            // NERV Home not installed
        }
    }

    @Override
    public void onInterrupt() {}
}
