package dev.rishi.quickstar;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * NERV notification banners, styled like NERV Home's umbilical-cable banner: black glass, a 1dp frame and type in the
 * active unit's colour, a kanji tag. Drawn as an accessibility overlay at the top of the screen (over apps and the lock
 * screen), touch-through everywhere except the banners. Tap opens the notification, swipe up dismisses; ordinary
 * banners leave after five seconds, an incoming call or alarm stays until it is answered or gone.
 * On the lock screen, private content is replaced by "CONTENT CLASSIFIED", as Nothing's own pop-up would hide it.
 */
final class BannerStack {
    static volatile BannerStack instance;

    private static final long SHOW_MS = 5000;
    private static final int MAX = 3;

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final LinearLayout stack;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, View> byKey = new LinkedHashMap<>();
    private boolean attached;

    BannerStack(AccessibilityService svc) {
        this.svc = svc;
        Ui.init(svc);
        wm = svc.getSystemService(WindowManager.class);
        stack = new LinearLayout(svc);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setClipChildren(false);
        stack.setClipToPadding(false);
    }

    void show(StatusBarNotification sbn, boolean urgent, NervNotifListener listener) {
        h.post(() -> {
            String key = sbn.getKey().intern();
            View old = byKey.remove(key);
            int at = old == null ? -1 : stack.indexOfChild(old);
            if (old != null) stack.removeView(old);
            View b = banner(sbn, urgent, listener);
            b.setTag(urgent);
            byKey.put(key, b);
            if (at >= 0) stack.addView(b, at, lp());
            else {
                stack.addView(b, lp());
                b.setTranslationY(-Ui.dp(60));
                b.setAlpha(0f);
                b.animate().translationY(0).alpha(1f).setDuration(280).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
            trim();
            attach();
            h.removeCallbacksAndMessages(key);
            if (!urgent) h.postAtTime(() -> remove(key), key, android.os.SystemClock.uptimeMillis() + SHOW_MS);
        });
    }

    void remove(String k) {
        final String key = k.intern(); // handler tokens match by identity
        h.post(() -> {
            h.removeCallbacksAndMessages(key);
            View b = byKey.remove(key);
            if (b == null) return;
            b.animate().translationY(-Ui.dp(60)).alpha(0f).setDuration(200).withEndAction(() -> {
                stack.removeView(b);
                if (stack.getChildCount() == 0) detach();
            }).start();
        });
    }

    void clear() {
        h.removeCallbacksAndMessages(null);
        byKey.clear();
        stack.removeAllViews();
        detach();
    }

    /** At most three on screen: the oldest ordinary banner makes room. */
    private void trim() {
        while (byKey.size() > MAX) {
            String drop = null;
            for (Map.Entry<String, View> e : byKey.entrySet()) {
                if (!Boolean.TRUE.equals(e.getValue().getTag())) { drop = e.getKey(); break; }
            }
            if (drop == null) drop = byKey.keySet().iterator().next();
            h.removeCallbacksAndMessages(drop);
            stack.removeView(byKey.remove(drop));
        }
    }

    private void attach() {
        if (attached) return;
        int top = wm.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.statusBars()).top;
        stack.setPadding(Ui.dp(10), top + Ui.dp(6), Ui.dp(10), Ui.dp(6));
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        try {
            wm.addView(stack, p);
            attached = true;
        } catch (RuntimeException e) {
            attached = false;
        }
    }

    private void detach() {
        if (!attached) return;
        attached = false;
        try {
            wm.removeView(stack);
        } catch (RuntimeException ignored) {
            // already gone
        }
    }

    private static LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(8);
        return lp;
    }

    /* ---------- one banner ---------- */

    private View banner(StatusBarNotification sbn, boolean urgent, NervNotifListener listener) {
        Context c = svc;
        Notification n = sbn.getNotification();
        Units.Unit u = Units.byId(UnitSync.current(c));
        boolean saver = c.getSystemService(PowerManager.class).isPowerSaveMode();
        boolean call = Notification.CATEGORY_CALL.equals(n.category), alarm = Notification.CATEGORY_ALARM.equals(n.category);
        int colour = saver ? u.sv : urgent ? u.warn : u.a;
        String app = appName(sbn);

        String head;
        if (urgent && call) head = "着信 · PATTERN BLUE · INCOMING CALL";
        else if (urgent && alarm) head = "警報 · EMERGENCY · ALARM";
        else head = "受信 · TRANSMISSION · " + app.toUpperCase(Locale.ROOT);

        // the lock screen hides private content unless the app marked it public (or it is a call / alarm)
        boolean locked = c.getSystemService(KeyguardManager.class).isKeyguardLocked();
        Notification shown = n;
        boolean classified = false;
        if (locked && !urgent && n.visibility != Notification.VISIBILITY_PUBLIC) {
            if (n.publicVersion != null) shown = n.publicVersion;
            else classified = true;
        }
        CharSequence title = shown.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = shown.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (TextUtils.isEmpty(text)) text = shown.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (classified) {
            title = app;
            text = "CONTENT CLASSIFIED · 機密 · UNLOCK TO VIEW";
        }
        if (TextUtils.isEmpty(title)) title = app;

        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        b.setBackground(Ui.shape(0xEB000000, colour, saver ? 0 : 14));
        b.setElevation(Ui.dp(6));

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Drawable icon = smallIcon(n);
        if (icon != null) {
            ImageView iv = new ImageView(c);
            icon.setTint(colour);
            iv.setImageDrawable(icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(13), Ui.dp(13));
            ilp.rightMargin = Ui.dp(7);
            top.addView(iv, ilp);
        }
        TextView tag = text(c, head, 10, colour, Fonts.dot(c));
        tag.setLetterSpacing(0.2f);
        tag.setSingleLine(true);
        tag.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(tag, new LinearLayout.LayoutParams(0, -2, 1f));
        b.addView(top);

        TextView t1 = text(c, title.toString().toUpperCase(Locale.ROOT), 17, colour,
                Fonts.cond(c));
        t1.setLetterSpacing(0.1f);
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        t1.setPadding(0, Ui.dp(5), 0, 0);
        b.addView(t1);
        if (!TextUtils.isEmpty(text)) {
            TextView t2 = text(c, text.toString(), 13, classified ? Ui.DIM : 0xE6FFFFFF, classified ? Fonts.mono(c) : null);
            t2.setMaxLines(2);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            t2.setPadding(0, Ui.dp(2), 0, 0);
            b.addView(t2);
        }

        // the notification's own buttons (answer / decline, snooze / dismiss, mark as read...); replies open the app
        if (!classified && n.actions != null && n.actions.length > 0) {
            LinearLayout row = new LinearLayout(c);
            row.setPadding(0, Ui.dp(10), 0, 0);
            int shownActions = 0;
            for (Notification.Action a : n.actions) {
                if (shownActions == 3 || a.actionIntent == null || a.title == null) continue;
                boolean reply = a.getRemoteInputs() != null && a.getRemoteInputs().length > 0;
                PendingIntent pi = reply ? n.contentIntent : a.actionIntent;
                if (pi == null) continue;
                boolean primary = urgent && shownActions == 0;
                TextView btn = text(c, a.title.toString().toUpperCase(Locale.ROOT), 11, primary ? 0xFF000000 : colour, Fonts.dot(c));
                btn.setLetterSpacing(0.15f);
                btn.setGravity(Gravity.CENTER);
                btn.setSingleLine(true);
                btn.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
                btn.setBackground(Ui.shape(primary ? colour : 0, primary ? 0 : colour, saver ? 0 : 8));
                btn.setOnClickListener(v -> {
                    send(pi);
                    remove(sbn.getKey());
                });
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, -2, 1f);
                if (shownActions > 0) blp.leftMargin = Ui.dp(8);
                row.addView(btn, blp);
                shownActions++;
            }
            if (shownActions > 0) b.addView(row);
        }

        b.setOnClickListener(v -> {
            if (n.contentIntent != null) send(n.contentIntent);
            if ((n.flags & Notification.FLAG_AUTO_CANCEL) != 0) listener.dismiss(sbn.getKey());
            remove(sbn.getKey());
        });
        swipeAway(b, sbn.getKey());
        return b;
    }

    /** Drag up to send it away; a short drag springs back and still counts as a tap. */
    private void swipeAway(View b, String key) {
        final float[] start = new float[1];
        final boolean[] dragging = new boolean[1];
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawY();
                    dragging[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getRawY() - start[0];
                    if (dy < -Ui.dp(8)) dragging[0] = true;
                    if (dragging[0]) {
                        v.setTranslationY(Math.min(0, dy));
                        v.setAlpha(Math.max(0.2f, 1 + dy / Ui.dp(140)));
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging[0]) return false;
                    if (e.getRawY() - start[0] < -Ui.dp(36)) remove(key);
                    else v.animate().translationY(0).alpha(1f).setDuration(160).start();
                    return true;
                default:
                    return false;
            }
        });
    }

    private void send(PendingIntent pi) {
        try {
            ActivityOptions o = ActivityOptions.makeBasic();
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                // accessibility services may open apps from the background; let the notification's intent use that
                o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            }
            Bundle opts = o.toBundle();
            pi.send(svc, 0, null, null, null, null, opts);
        } catch (PendingIntent.CanceledException ignored) {
            // the app withdrew it
        }
    }

    private String appName(StatusBarNotification sbn) {
        // system apps may post under a substitute name
        CharSequence sub = sbn.getNotification().extras.getCharSequence("android.substName");
        if (!TextUtils.isEmpty(sub)) return sub.toString();
        PackageManager pm = svc.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return sbn.getPackageName();
        }
    }

    private Drawable smallIcon(Notification n) {
        try {
            return n.getSmallIcon() == null ? null : n.getSmallIcon().loadDrawable(svc);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static TextView text(Context c, String s, float sp, int colour, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        if (face != null) t.setTypeface(face);
        return t;
    }
}
