package dev.nervos.home;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NERV Home: the Evangelion front end for the Nothing Phone (3). Date + MAGI widgets with live stats,
 * NERV icon grid and dock, a "More" folder, a swipe-up app drawer with search, and a purple/black
 * battery-saver look with no wallpaper and no widgets. Pull down on the home screen for notifications.
 */
public class HomeActivity extends Activity {
    /** the active unit theme and the colour roles read from it */
    private Units.Unit unit;
    private boolean xdark;
    private int GREEN, SAVER, MUTED = Units.MUTED, WARN, SAVER_DIM;
    private static final List<String> GRID = Arrays.asList("com.nothing.gallery", "com.google.android.youtube",
            "com.google.android.apps.maps", "com.google.android.gm", "com.android.vending", "com.google.android.apps.photos",
            "com.google.android.calendar", "com.google.android.deskclock", "com.nothing.weather",
            "com.google.android.apps.nbu.paisa.user", "com.android.settings");
    private static final List<String> DOCK = Arrays.asList("com.google.android.dialer",
            "com.google.android.apps.messaging", "com.android.chrome", "com.nothing.camera");

    private Apps apps;
    private boolean saver;
    private int insetTop, insetBottom;
    private Root root;
    private LinearLayout home, folderPanel;
    private FrameLayout drawer, folder;
    private GridView drawerGrid;
    private EditText search;
    private View folderTile;
    private TextView wDay, wDate, wStepsLabel, wBatt, wBattLabel;
    /** battery saver's internal-power readout, and the drawer's MAGI status line */
    private TextView sPowerHead, sPower, sPowerSub, drawerStatus;
    /** saver changed while home wasn't in front: the city shifts when it is; quickstarSaver is what Quickstar was last told */
    private boolean cityPending, cityAnimating, quickstarSaver;
    private Steps steps;
    private final TextView[] magiVal = new TextView[3], magiLabel = new TextView[3], magiVerdict = new TextView[3];
    private final Spring drawerSpring = new Spring(260, 32, this::renderDrawer);
    private final Spring folderSpring = new Spring(230, 23, this::renderFolder);
    /** dot: Nothing's dot-matrix face, for MAGI readouts and terminal lines */
    private Typeface serif, cond, mono, dot;
    private boolean liveRegistered, resumed;
    /** Set when the screen turns off; the next return to home from the lock screen plays the ripple once. */
    private boolean rippleArmed;

    // own wallpaper stack: sky, depth clock, towers (so the clock sits behind the skyline and the ripple can refract it)
    private static final int CLOCK_DP = 150;
    private FrameLayout wall;
    private ImageView skyView, towerView;
    private TextView clockView;
    private View clockSlot;
    private Bitmap skyBmp, towerBmp;
    private float towerTop = -1;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver lockState = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            android.util.Log.i("NervHome", i.getAction() + " armed=" + rippleArmed + " focus=" + hasWindowFocus());
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                rippleArmed = true;
            } else if (rippleArmed) {
                // USER_PRESENT (~0.44 s after the fingerprint on Nothing OS, before home regains focus). Home stays
                // resumed behind the lock screen, so resumed means home is what the lock screen is revealing.
                // Otherwise another app was on top: disarm so a later Home press doesn't ripple.
                rippleArmed = false;
                if (resumed) unlocked();
            }
        }
    };

    /**
     * Nothing OS keeps home resumed behind the lock screen, so onResume can't mark the unlock. Window focus can:
     * the lock screen takes focus while it is up, and hands it back to home the moment it starts going away.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        boolean locked = getSystemService(KeyguardManager.class).isKeyguardLocked();
        android.util.Log.i("NervHome", "focus=" + hasFocus + " locked=" + locked + " armed=" + rippleArmed);
        if (!hasFocus && locked) rippleArmed = true;
        else if (hasFocus && rippleArmed && !locked) {
            rippleArmed = false;
            unlocked();
        }
        if (hasFocus && !locked && cityPending) applySaver();
    }

    private final BroadcastReceiver live = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(a)) applySaver();
            else if (Intent.ACTION_POWER_CONNECTED.equals(a) || Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
                umbilical(Intent.ACTION_POWER_CONNECTED.equals(a));
                updateWidgets(null);
            } else updateWidgets(Intent.ACTION_BATTERY_CHANGED.equals(a) ? i : null);
        }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            apps.reload();
            build();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        serif = Fonts.serif(this);
        cond = Fonts.cond(this);
        mono = Fonts.mono(this);
        dot = Fonts.dot(this);
        setUnit(UnitStore.current(this));
        xdark = UnitStore.xdark(this);
        apps = new Apps(this);
        steps = new Steps(this, this::showSteps);
        Steps.scheduleMidnight(this);
        if (!stepsAllowed()) requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 1);
        root = new Root(this);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            if (bars.top != insetTop || bars.bottom != insetBottom) {
                insetTop = bars.top;
                insetBottom = bars.bottom;
                build();
            }
            return insets;
        });
        setContentView(root);
        // start from the look last shown, so a saver change made while home was away still plays its city shift
        quickstarSaver = isSaver();
        saver = getSharedPreferences("nerv", MODE_PRIVATE).getBoolean("shown_saver", quickstarSaver);
        build();
        IntentFilter pf = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf, Context.RECEIVER_NOT_EXPORTED);
        // USER_PRESENT is sent by the lock screen (SystemUI), which a not-exported receiver ignores. Both actions are
        // protected broadcasts, so only the system can send them either way.
        IntentFilter lf = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        lf.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(lockState, lf, Context.RECEIVER_EXPORTED);
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::placeClock);
    }

    /** Line the depth clock up with its slot, and make the centre tower rise into the clock's lower half. */
    private void placeClock() {
        if (clockView == null || clockSlot == null || root.getWidth() == 0) return;
        // layout position (ignores the drawer's scale animation on the home layer)
        int y = home.getTop() + ((View) clockSlot.getParent()).getTop() + clockSlot.getTop();
        if (clockView.getTranslationY() != y) clockView.setTranslationY(y);
        float units = root.getWidth() / 393f, top = Math.round((y + clockSlot.getHeight() * 0.58f) / units);
        if (top != towerTop) {
            towerTop = top;
            renderWall(top);
        }
    }

    private void renderWall(float top) {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        worker.execute(() -> {
            Bitmap sky = skyBmp != null && skyBmp.getWidth() == w / 2 ? skyBmp : Skyline.sky(w / 2, h / 2, unit, xdark);
            Bitmap towers = Skyline.towers(w, h, top, unit, xdark);
            runOnUiThread(() -> {
                skyBmp = sky;
                towerBmp = towers;
                if (skyView != null) skyView.setImageBitmap(sky);
                if (towerView != null) towerView.setImageBitmap(towers);
            });
        });
    }

    private FrameLayout buildWall() {
        FrameLayout f = new FrameLayout(this);
        skyView = new ImageView(this);
        skyView.setScaleType(ImageView.ScaleType.FIT_XY);
        skyView.setImageBitmap(skyBmp);
        f.addView(skyView, match());
        clockView = new TextView(this);
        clockView.setTypeface(serif);
        clockView.setTextSize(132);
        clockView.setTextScaleX(0.84f);
        clockView.setLetterSpacing(-0.04f);
        clockView.setTextColor(0xFFF7F2E8);
        clockView.setIncludeFontPadding(false);
        clockView.setGravity(Gravity.CENTER);
        f.addView(clockView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CLOCK_DP)));
        towerView = new ImageView(this);
        towerView.setScaleType(ImageView.ScaleType.FIT_XY);
        towerView.setImageBitmap(towerBmp);
        f.addView(towerView, match());
        return f;
    }

    /* ---------- unlock effect: a wave of hexagons unfolds from the fingerprint sensor ---------- */

    private static final float RIPPLE_SPEED = 1.3f; // wave time per second: ~1.7 s in total (unchanged from the glass ripple)
    private static final long RIPPLE_HEAD_START_NS = 300_000_000L;
    private HexWave hexWave;

    /** Back from the lock screen: the day's first unlock opens on its episode title card, then the hex wave. */
    private void unlocked() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date());
        android.content.SharedPreferences p = getSharedPreferences("nerv", MODE_PRIVATE);
        if (saver || today.equals(p.getString("title_day", ""))) {
            ripple();
            return;
        }
        p.edit().putString("title_day", today).apply();
        titleCard();
    }

    private void titleCard() {
        if (root.getWidth() == 0) return;
        root.addView(new TitleCard(this, unit, insetTop, insetBottom, this::ripple), match());
    }

    private void ripple() {
        if (saver || cityAnimating || root.getWidth() == 0) return;
        android.util.Log.i("NervHome", "unlock hex wave");
        Haptics.unlock(this, unit);
        if (hexWave != null) {
            hexWave.cancel();
            root.removeView(hexWave);
        }
        // Phone (3)'s in-display sensor sits in the lower middle of the screen. The wave starts a little in already,
        // backdated to roughly when the lock screen began going away, as the ripple always did.
        final HexWave w = new HexWave(this, unit, root.getWidth() / 2f, root.getHeight() * 0.82f, root.getWidth() / 393f,
                RIPPLE_SPEED, RIPPLE_HEAD_START_NS, () -> {
                    root.removeView(hexWave);
                    hexWave = null;
                });
        hexWave = w;
        root.addView(w, match());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // live updates only while the home screen is visible: no background work
        IntentFilter f = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(live, f, Context.RECEIVER_NOT_EXPORTED);
        liveRegistered = true;
        if (stepsAllowed()) steps.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Units.Unit saved = UnitStore.current(this);
        boolean savedX = UnitStore.xdark(this);
        if (!saved.id.equals(unit.id) || savedX != xdark) {
            xdark = savedX;
            setUnitAndRebuild(saved);
        }
        applySaver();
        updateWidgets(null);
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (liveRegistered) unregisterReceiver(live);
        liveRegistered = false;
        steps.stop();
        drawerSpring.set(0);
        folderSpring.set(0);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        unregisterReceiver(lockState);
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // `am start ... --ez hexwave true` plays the unlock wave on demand, to preview it without locking the phone
        if (intent.getBooleanExtra("hexwave", false)) {
            ripple();
            return;
        }
        // `--ez titlecard true` previews the day's title card, `--ez umbilical true|false` the power banners
        if (intent.getBooleanExtra("titlecard", false)) {
            titleCard();
            return;
        }
        // `--ez cityshift true` previews the towers sinking and, since saver is really off, rising straight back
        if (intent.getBooleanExtra("cityshift", false)) {
            root.post(() -> { if (!saver && !cityAnimating) cityShift(true); });
            return;
        }
        if (intent.hasExtra("umbilical")) {
            boolean on = intent.getBooleanExtra("umbilical", false);
            root.post(() -> umbilical(on)); // new intents arrive while paused; the banner needs home resumed
            return;
        }
        closeOverlays(); // Home pressed while already home
    }

    @Override
    public void onBackPressed() {
        if (picker != null) closePicker(null);
        else closeOverlays();
    }

    private void closeOverlays() {
        if (picker != null) closePicker(null);
        if (folderSpring.value() > 0) folderSpring.animateTo(0, 0);
        if (drawerSpring.value() > 0) drawerSpring.animateTo(0, 0);
    }

    private boolean isSaver() {
        return getSystemService(PowerManager.class).isPowerSaveMode();
    }

    /**
     * Battery saver changed. Quickstar hears at once; the home screen plays Tokyo-3's city shift (towers sink into the
     * Geofront, or rise back out) the next time it is actually in front, so the animation is never missed.
     */
    private void applySaver() {
        boolean s = isSaver();
        if (s == saver) {
            cityPending = false;
            tellQuickstar(s);
            return;
        }
        if (cityAnimating) return; // re-checked when the running shift ends
        if (!hasWindowFocus()) {
            cityPending = true;
            tellQuickstar(s);
            return;
        }
        cityPending = false;
        cityShift(s); // Quickstar is told once the city has moved
    }

    /**
     * Quickstar restyles the system palette for saver, and that restarts this activity; so while the city is moving it
     * waits until the shift has finished.
     */
    private void tellQuickstar(boolean s) {
        if (s == quickstarSaver) return;
        quickstarSaver = s;
        UnitStore.notifyQuickstar(this, unit, xdark, s);
    }

    private void cityShift(boolean toSaver) {
        float drop = root.getHeight() * 0.75f;
        if (toSaver) {
            if (towerView == null) {
                saver = true;
                build();
                updateWidgets(null);
                tellQuickstar(isSaver());
                return;
            }
            cityAnimating = true;
            cityCaption("第3新東京市 · 戦闘形態移行", "TOKYO-3 · BATTLE CONFIGURATION", WARN);
            towerView.animate().translationY(drop).setDuration(1400).setInterpolator(new AccelerateInterpolator(1.6f))
                    .withEndAction(() -> {
                        cityAnimating = false;
                        saver = true;
                        build();
                        updateWidgets(null);
                        tellQuickstar(isSaver());
                        applySaver();
                    }).start();
        } else {
            saver = false;
            build();
            updateWidgets(null);
            if (towerView == null) {
                tellQuickstar(isSaver());
                return;
            }
            cityAnimating = true;
            towerView.setTranslationY(drop);
            cityCaption("第3新東京市 · 通常形態復帰", "TOKYO-3 · CITY RESTORED", GREEN);
            towerView.animate().translationY(0).setDuration(1500).setInterpolator(new DecelerateInterpolator(1.8f))
                    .withEndAction(() -> {
                        cityAnimating = false;
                        tellQuickstar(isSaver());
                        applySaver();
                    }).start();
        }
    }

    /** Two lines over the city while it moves; gone after the shift. */
    private void cityCaption(String jp, String en, int colour) {
        LinearLayout cap = new LinearLayout(this);
        cap.setOrientation(LinearLayout.VERTICAL);
        cap.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable bg = panel(0xE6000000, saver ? 0 : dp(10));
        bg.setStroke(dp(1), colour);
        cap.setBackground(bg);
        TextView a = text(jp, 14, colour, cond);
        a.setLetterSpacing(0.2f);
        a.setGravity(Gravity.CENTER);
        TextView b = text(en, 10, colour, dot);
        b.setLetterSpacing(0.3f);
        b.setGravity(Gravity.CENTER);
        cap.addView(a);
        cap.addView(b);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.topMargin = dp(40);
        root.addView(cap, lp);
        cap.setAlpha(0f);
        cap.animate().alpha(1f).setDuration(200).withEndAction(() -> cap.animate().alpha(0f).setStartDelay(1100)
                .setDuration(300).withEndAction(() -> root.removeView(cap)).start()).start();
    }

    /** Plugging in or out, Eva style: the umbilical cable banner slides down from the top for a few seconds. */
    private void umbilical(boolean connected) {
        android.content.SharedPreferences p = getSharedPreferences("nerv", MODE_PRIVATE);
        if (connected) p.edit().remove("unplug_ms").remove("unplug_level").apply();
        else p.edit().putLong("unplug_ms", System.currentTimeMillis()).putInt("unplug_level", batteryLevel()).apply();
        if (!resumed || root.getWidth() == 0) return;
        int colour = saver ? SAVER : connected ? GREEN : WARN;
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = panel(0xEB000000, saver ? 0 : dp(14));
        bg.setStroke(dp(1), colour);
        b.setBackground(bg);
        TextView t1 = text(connected ? "EXTERNAL POWER CONNECTED" : "UMBILICAL CABLE DISCONNECTED", 17, colour, cond);
        t1.setLetterSpacing(0.14f);
        TextView t2 = text(connected ? "外部電源接続 · CHARGING" : "アンビリカルケーブル切断 · INTERNAL POWER", 10, colour, dot);
        t2.setLetterSpacing(0.2f);
        b.addView(t1);
        b.addView(t2);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lp.setMargins(dp(12), insetTop + dp(8), dp(12), 0);
        root.addView(b, lp);
        b.setTranslationY(-dp(140));
        b.animate().translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).withEndAction(() ->
                b.animate().translationY(-dp(140)).setStartDelay(2600).setDuration(260)
                        .withEndAction(() -> root.removeView(b)).start()).start();
    }

    private int batteryLevel() {
        return getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /**
     * Time left on internal power. Android's own estimate when the phone has one; otherwise worked out from how fast the
     * level has fallen since unplugging (needs a couple of percent and ten minutes first). -1 while still calculating.
     */
    private long internalPowerLeft(int level) {
        java.time.Duration d = getSystemService(PowerManager.class).getBatteryDischargePrediction();
        if (d != null && !d.isNegative() && !d.isZero()) return d.toMillis();
        android.content.SharedPreferences p = getSharedPreferences("nerv", MODE_PRIVATE);
        long t0 = p.getLong("unplug_ms", 0), now = System.currentTimeMillis();
        int l0 = p.getInt("unplug_level", -1);
        if (t0 == 0 || l0 < 0 || l0 < level) {
            p.edit().putLong("unplug_ms", now).putInt("unplug_level", level).apply();
            return -1;
        }
        long elapsed = now - t0;
        if (l0 - level < 2 || elapsed < 10 * 60_000L) return -1;
        return elapsed * level / (l0 - level);
    }

    private View buildPower() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = panel(Color.BLACK, 0);
        bg.setStroke(dp(1), SAVER);
        box.setBackground(bg);
        sPowerHead = text("", 10, MUTED, dot);
        sPowerHead.setLetterSpacing(0.25f);
        sPower = text("", 64, SAVER, dot);
        sPower.setIncludeFontPadding(false);
        sPowerSub = text("", 10, MUTED, dot);
        sPowerSub.setLetterSpacing(0.2f);
        box.addView(sPowerHead);
        box.addView(sPower);
        box.addView(sPowerSub);
        return box;
    }

    private void updatePower(int level, boolean plugged) {
        if (sPower == null) return;
        if (plugged) {
            sPowerHead.setText("EXTERNAL POWER · 外部電源");
            sPower.setText(level + "%");
            sPowerSub.setText("CHARGING · SYNC RATIO STABLE");
            return;
        }
        long left = internalPowerLeft(level);
        sPowerHead.setText("INTERNAL POWER · 内部電源");
        if (left < 0) {
            sPower.setText(level + "%");
            sPowerSub.setText("活動限界まで · CALCULATING");
        } else {
            long mins = Math.min(99 * 60 + 59, left / 60_000);
            sPower.setText(String.format(Locale.ROOT, "%02d:%02d", mins / 60, mins % 60));
            sPowerSub.setText("活動限界まで · ACTIVITY LIMIT   " + level + "%");
        }
    }

    /* ---------- layout ---------- */

    private void build() {
        getSharedPreferences("nerv", MODE_PRIVATE).edit().putBoolean("shown_saver", saver).apply();
        root.removeAllViews();
        // NERV Home draws its own wallpaper layers, so the system wallpaper is never composited behind it.
        // In battery saver nothing is drawn behind the purple outlines: pure black.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        root.setBackgroundColor(Color.BLACK);
        wall = null;
        skyView = towerView = null;
        clockView = null;
        clockSlot = null;
        if (!saver) {
            wall = buildWall();
            root.addView(wall, match());
        }
        List<Apps.Entry> gridApps = new ArrayList<>(), dockApps = new ArrayList<>(), folderApps = new ArrayList<>();
        for (String p : GRID) { Apps.Entry e = apps.find(p); if (e != null) gridApps.add(e); }
        for (String p : DOCK) { Apps.Entry e = apps.find(p); if (e != null) dockApps.add(e); }
        for (Apps.Entry e : apps.all()) {
            String p = e.component.getPackageName();
            if (!GRID.contains(p) && !DOCK.contains(p)) folderApps.add(e);
        }
        home = buildHome(gridApps, dockApps, folderApps);
        home.setOnLongClickListener(v -> {
            openPicker();
            return true;
        });
        root.addView(home, match());
        folder = buildFolder(folderApps);
        root.addView(folder, match());
        drawer = buildDrawer();
        root.addView(drawer, match());
        renderDrawer(drawerSpring.value());
        renderFolder(folderSpring.value());
        updateWidgets(null); // fresh widget views start empty
    }

    private LinearLayout buildHome(List<Apps.Entry> grid, List<Apps.Entry> dock, List<Apps.Entry> folderApps) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(16), insetTop + dp(14), dp(16), insetBottom + dp(16));
        wDay = wDate = wBatt = wBattLabel = null;
        sPowerHead = sPower = sPowerSub = null;
        h.addView(saver ? buildPower() : buildWidgets());
        h.addView(new View(this), new LinearLayout.LayoutParams(1, dp(saver ? 24 : 20)));

        List<View> cells = new ArrayList<>();
        for (Apps.Entry e : grid) cells.add(iconCell(e, true));
        cells.add(folderCell(folderApps));
        for (int i = 0; i < cells.size(); i += 4) {
            LinearLayout row = new LinearLayout(this);
            for (int j = i; j < i + 4; j++) row.addView(j < cells.size() ? cells.get(j) : new View(this), weight());
            h.addView(row);
        }
        h.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout d = new LinearLayout(this);
        d.setGravity(Gravity.CENTER_VERTICAL);
        d.setPadding(dp(6), dp(10), dp(6), dp(10));
        d.setBackground(panel(saver ? Color.BLACK : surf(unit.dock, 0.4f), dp(30)));
        for (Apps.Entry e : dock) d.addView(iconCell(e, false), weight());
        h.addView(d);
        return h;
    }

    private View buildWidgets() {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.BOTTOM);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        wDay = text("", 13, GREEN, dot);
        wDay.setLetterSpacing(0.3f);
        // long days ("WEDNESDAY · 30 SEP") shrink to fit one line instead of wrapping into the clock
        wDay.setMaxLines(1);
        wDay.setAutoSizeTextTypeUniformWithConfiguration(8, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        // today's steps (the day-month number used to sit here and read like a second clock)
        LinearLayout stepRow = new LinearLayout(this);
        stepRow.setBaselineAligned(true);
        wDate = text("", 26, Color.WHITE, serif);
        wDate.setIncludeFontPadding(false);
        wDate.setShadowLayer(dp(10), 0, dp(1), 0x66000000);
        wStepsLabel = text("STEPS", 11, Color.WHITE, cond);
        wStepsLabel.setLetterSpacing(0.14f);
        wStepsLabel.setPadding(dp(8), 0, 0, 0);
        stepRow.addView(wDate);
        stepRow.addView(wStepsLabel);
        stepRow.setOnClickListener(v -> { if (!stepsAllowed()) requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 1); });
        left.addView(wDay);
        left.addView(stepRow);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        wBatt = text("", 26, Color.WHITE, serif);
        wBattLabel = text("SYNC RATIO", 11, Color.WHITE, cond);
        wBattLabel.setLetterSpacing(0.14f);
        right.addView(wBatt);
        right.addView(wBattLabel);
        top.addView(right);
        w.addView(top);
        // the depth clock is drawn in the wallpaper stack (behind the towers); this slot reserves its place
        clockSlot = new View(this);
        w.addView(clockSlot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CLOCK_DP)));

        LinearLayout magi = new LinearLayout(this);
        String[] names = {"MELCHIOR·1", "BALTHASAR·2", "CASPER·3"};
        for (int i = 0; i < 3; i++) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(panel(surf(unit.ink, 0.702f), dp(6)));
            box.setPadding(dp(9), 0, dp(9), dp(8));
            View bar = new View(this);
            bar.setBackgroundColor(GREEN);
            box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
            TextView name = text(names[i], 9, MUTED, dot);
            name.setPadding(0, dp(7), 0, 0);
            box.addView(name);
            magiVal[i] = text("–", 24, Color.WHITE, dot);
            box.addView(magiVal[i]);
            LinearLayout foot = new LinearLayout(this);
            magiLabel[i] = text("", 9, GREEN, dot);
            magiVerdict[i] = text("承認", 13, GREEN, dot);
            magiVerdict[i].setGravity(Gravity.END);
            foot.addView(magiLabel[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            foot.addView(magiVerdict[i]);
            box.addView(foot);
            LinearLayout.LayoutParams lp = weight();
            lp.setMargins(i == 0 ? 0 : dp(3), 0, i == 2 ? 0 : dp(3), 0);
            magi.addView(box, lp);
        }
        w.addView(magi);
        return w;
    }

    private FrameLayout buildFolder(List<Apps.Entry> folderApps) {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(saver ? 0xEB000000 : surf(unit.scrim, 0.549f));
        f.setOnClickListener(v -> folderSpring.animateTo(0, 0));
        folderPanel = new LinearLayout(this);
        folderPanel.setOrientation(LinearLayout.VERTICAL);
        folderPanel.setClickable(true);
        folderPanel.setPadding(dp(8), dp(22), dp(8), dp(18));
        GradientDrawable bg = panel(saver ? Color.BLACK : surf(unit.panel, 0.949f), saver ? 0 : dp(26));
        if (saver) bg.setStroke(dp(1), SAVER);
        folderPanel.setBackground(bg);
        TextView title = text("MORE", 30, saver ? SAVER : Color.WHITE, serif);
        title.setGravity(Gravity.CENTER);
        TextView sub = text("FOLDER · " + folderApps.size() + " APPS", 10, saver ? SAVER_DIM : MUTED, mono);
        sub.setGravity(Gravity.CENTER);
        sub.setLetterSpacing(0.2f);
        folderPanel.addView(title);
        folderPanel.addView(sub);
        GridView g = grid(folderApps);
        int rows = Math.min(5, (folderApps.size() + 3) / 4);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rows * dp(100));
        glp.topMargin = dp(16);
        folderPanel.addView(g, glp);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        plp.setMargins(dp(18), 0, dp(18), 0);
        f.addView(folderPanel, plp);
        return f;
    }

    private FrameLayout buildDrawer() {
        FrameLayout d = new FrameLayout(this);
        d.setBackgroundColor(saver ? Color.BLACK : surf(unit.ink, 0.922f));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, insetTop + dp(10), 0, 0);
        View handle = new View(this);
        handle.setBackground(panel(saver ? SAVER : 0x59FFFFFF, dp(2)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(44), dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(handle, hlp);
        // a MAGI console: prompt, block cursor, and a status line that reports each query's verdict
        int ink = saver ? SAVER : GREEN;
        LinearLayout console = new LinearLayout(this);
        console.setGravity(Gravity.CENTER_VERTICAL);
        console.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable sbg = panel(saver ? Color.BLACK : 0xCC000000, saver ? 0 : dp(6));
        sbg.setStroke(dp(1), saver ? SAVER_DIM : Units.Unit.alpha(GREEN, 0.5f));
        console.setBackground(sbg);
        TextView prompt = text("MAGI>", 14, ink, dot);
        console.addView(prompt);
        search = new EditText(this);
        search.setHint("QUERY · 検索");
        search.setHintTextColor(saver ? SAVER_DIM : MUTED);
        search.setTextColor(ink);
        search.setTypeface(dot);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setBackground(null);
        search.setPadding(dp(10), dp(14), 0, dp(14));
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(ink);
        cursor.setSize(dp(8), dp(18));
        search.setTextCursorDrawable(cursor);
        console.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(18), dp(24), dp(18), 0);
        col.addView(console, slp);
        drawerStatus = text("", 9, MUTED, dot);
        drawerStatus.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.setMargins(dp(22), dp(8), dp(18), dp(14));
        col.addView(drawerStatus, stlp);
        idleStatus();
        drawerGrid = grid(apps.all());
        drawerGrid.setPadding(dp(8), 0, dp(8), insetBottom + dp(40));
        drawerGrid.setClipToPadding(false);
        col.addView(drawerGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim();
                List<Apps.Entry> hits = apps.filtered(q);
                ((AppAdapter) drawerGrid.getAdapter()).setItems(hits, !q.isEmpty());
                verdict(q, hits.size());
            }
        });
        d.addView(col, match());
        return d;
    }

    private void idleStatus() {
        drawerStatus.removeCallbacks(verdictTick);
        drawerStatus.setTextColor(saver ? SAVER_DIM : MUTED);
        drawerStatus.setText("MAGI SYSTEM · 3/3 ONLINE · " + apps.all().size() + " ENTRIES");
    }

    private String pendingVerdict;
    private int pendingVerdictColour;
    private final Runnable verdictTick = () -> {
        drawerStatus.setText(pendingVerdict);
        drawerStatus.setTextColor(pendingVerdictColour);
    };

    /** "SEARCHING…" for a beat, then the three computers' verdict on the query. */
    private void verdict(String q, int hits) {
        if (q.isEmpty()) {
            idleStatus();
            return;
        }
        drawerStatus.removeCallbacks(verdictTick);
        drawerStatus.setTextColor(saver ? SAVER_DIM : MUTED);
        drawerStatus.setText("MELCHIOR · BALTHASAR · CASPER · SEARCHING…");
        pendingVerdict = hits > 0 ? hits + (hits == 1 ? " MATCH" : " MATCHES") + " · 承認 · APPROVED" : "NO MATCH · 否決 · DENIED";
        pendingVerdictColour = saver ? SAVER : hits > 0 ? GREEN : WARN;
        drawerStatus.postDelayed(verdictTick, 220);
    }

    private GridView grid(List<Apps.Entry> items) {
        GridView g = new GridView(this);
        g.setNumColumns(4);
        g.setVerticalSpacing(dp(14));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setSelector(new GradientDrawable());
        g.setVerticalScrollBarEnabled(false);
        g.setOverScrollMode(View.OVER_SCROLL_NEVER);
        g.setAdapter(new AppAdapter(items));
        return g;
    }

    private final class AppAdapter extends BaseAdapter {
        private List<Apps.Entry> items;
        /** search results carry a small 承認 under their label */
        private boolean approved;

        AppAdapter(List<Apps.Entry> items) { this.items = items; }

        void setItems(List<Apps.Entry> items, boolean approved) {
            this.items = items;
            this.approved = approved;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout cell = (LinearLayout) iconCell(items.get(i), true);
            if (approved) {
                TextView mark = text("承認", 10, GREEN, dot);
                mark.setGravity(Gravity.CENTER);
                cell.addView(mark, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            return cell;
        }
    }

    private View iconCell(Apps.Entry e, boolean label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, dp(8), 0, dp(8));
        ImageView img = new ImageView(this);
        img.setImageDrawable(apps.icon(e, saver, unit));
        cell.addView(img, new LinearLayout.LayoutParams(dp(60), dp(60)));
        if (label) {
            TextView t = text(e.label.toUpperCase(Locale.ROOT), 12, saver ? SAVER : Color.WHITE, cond);
            t.setLetterSpacing(0.08f);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(2), dp(6), dp(2), 0);
            if (!saver) t.setShadowLayer(dp(5), 0, dp(1), 0xCC000000);
            cell.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        press(cell, img);
        cell.setOnClickListener(v -> launch(img, e));
        cell.setOnLongClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", e.component.getPackageName(), null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        });
        return cell;
    }

    private View folderCell(List<Apps.Entry> folderApps) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, dp(8), 0, dp(8));
        Drawable[] minis = new Drawable[Math.min(4, folderApps.size())];
        for (int i = 0; i < minis.length; i++) minis[i] = apps.icon(folderApps.get(i), saver, unit);
        ImageView img = new ImageView(this);
        img.setImageDrawable(new OctTile(saver ? Color.BLACK : unit.tile, saver ? SAVER : 0, 0.31f, minis));
        cell.addView(img, new LinearLayout.LayoutParams(dp(60), dp(60)));
        TextView t = text("MORE", 12, saver ? SAVER : Color.WHITE, cond);
        t.setLetterSpacing(0.08f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(2), dp(6), dp(2), 0);
        if (!saver) t.setShadowLayer(dp(5), 0, dp(1), 0xCC000000);
        cell.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        press(cell, img);
        cell.setOnClickListener(v -> openFolder());
        folderTile = img;
        return cell;
    }

    /** Icons dip on touch and spring back on release. */
    private void press(View target, View img) {
        target.setOnTouchListener((v, ev) -> {
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) img.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).setInterpolator(null);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                img.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(3f));
            return false;
        });
    }

    private void launch(View from, Apps.Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        ActivityOptions o = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight());
        try {
            startActivity(i, o.toBundle());
        } catch (Exception ex) {
            Toast.makeText(this, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openFolder() {
        if (folderTile != null) {
            int[] at = new int[2];
            folderTile.getLocationInWindow(at);
            folderPanel.setPivotX(at[0] + folderTile.getWidth() / 2f - folderPanel.getLeft());
            folderPanel.setPivotY(at[1] + folderTile.getHeight() / 2f - folderPanel.getTop());
        }
        folderSpring.animateTo(1, 0);
    }

    /* ---------- motion ---------- */

    private void renderDrawer(float p) {
        if (drawer == null) return;
        float h = root.getHeight() > 0 ? root.getHeight() : 3000;
        drawer.setTranslationY((1 - clamp(p)) * h);
        drawer.setVisibility(p > 0.001f ? View.VISIBLE : View.INVISIBLE);
        home.setAlpha(1 - clamp(p));
        float s = 1 - 0.06f * clamp(p);
        home.setScaleX(s);
        home.setScaleY(s);
        if (p <= 0.001f && search != null && search.length() > 0) {
            search.setText("");
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
    }

    private void renderFolder(float p) {
        if (folder == null) return;
        folder.setVisibility(p > 0.002f ? View.VISIBLE : View.INVISIBLE);
        folder.setAlpha(Math.min(1f, Math.max(0f, p * 1.4f)));
        float s = 0.2f + 0.8f * p;
        folderPanel.setScaleX(s);
        folderPanel.setScaleY(s);
    }

    /** Home-screen gestures: up opens the drawer, down opens notifications, down at the top of the drawer closes it. */
    private final class Root extends FrameLayout {
        private static final int NONE = 0, OPEN = 1, SHADE = 2, CLOSE = 3, IGNORE = -1;
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private float sx, sy;
        private int mode;
        private VelocityTracker vt;

        Root(Context c) { super(c); }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) { return track(e); }

        @Override public boolean onTouchEvent(MotionEvent e) { track(e); return true; }

        private boolean track(MotionEvent e) {
            if (picker != null) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getRawX();
                    sy = e.getRawY();
                    mode = NONE;
                    if (vt != null) vt.recycle();
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    if (vt != null) vt.addMovement(e);
                    float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                    if (mode == NONE && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                        if (folderSpring.value() > 0.01f) mode = IGNORE;
                        else if (drawerSpring.value() < 0.5f) mode = dy < 0 ? OPEN : SHADE;
                        else mode = dy > 0 && !drawerGrid.canScrollVertically(-1) ? CLOSE : IGNORE;
                        if (mode == OPEN || mode == CLOSE) { sy = e.getRawY(); drawerSpring.stop(); }
                    }
                    if (mode == OPEN) drawerSpring.set(clamp(-(e.getRawY() - sy) / dist()));
                    else if (mode == CLOSE) drawerSpring.set(clamp(1 - (e.getRawY() - sy) / dist()));
                    return mode > 0;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float vy = 0;
                    if (vt != null) { vt.addMovement(e); vt.computeCurrentVelocity(1000); vy = vt.getYVelocity(); vt.recycle(); vt = null; }
                    boolean handled = mode > 0;
                    if (mode == OPEN || mode == CLOSE) {
                        float v = -vy / dist(), p = drawerSpring.value();
                        drawerSpring.animateTo(p + v * 0.18f > (mode == OPEN ? 0.35f : 0.65f) ? 1 : 0, v);
                    } else if (mode == SHADE && e.getRawY() - sy > dp(40)) expandShade();
                    mode = NONE;
                    return handled;
                }
                default:
                    return false;
            }
        }

        private float dist() { return Math.max(1, getHeight() * 0.7f); }
    }

    private void expandShade() {
        try {
            Object bar = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(bar);
        } catch (Exception ignored) {
            // not available on this build: the system's own top-edge swipe still works
        }
    }

    /* ---------- live widgets ---------- */

    private void updateWidgets(Intent battery) {
        if (sPower != null) updatePower(batteryLevel(), getSystemService(BatteryManager.class).isCharging()
                || isPlugged(battery));
        if (wDay == null) return;
        Calendar c = Calendar.getInstance();
        wDay.setText((c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH) + " · " + c.get(Calendar.DAY_OF_MONTH)
                + " " + c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH)).toUpperCase(Locale.ROOT));
        showSteps(stepsAllowed() ? Steps.cached(this) : -1);
        if (clockView != null) {
            boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
            int hour = h24 ? c.get(Calendar.HOUR_OF_DAY) : (c.get(Calendar.HOUR) == 0 ? 12 : c.get(Calendar.HOUR));
            clockView.setText(String.format(Locale.ROOT, h24 ? "%02d:%02d" : "%d:%02d", hour, c.get(Calendar.MINUTE)));
        }

        if (battery == null) battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = 0;
        boolean plugged = false;
        if (battery != null) {
            int raw = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = Math.round(raw * 100f / Math.max(1, scale));
            plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        }
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(mi);
        int mem = (int) (100 - mi.availMem * 100 / Math.max(1, mi.totalMem));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        int disk = (int) (100 - fs.getAvailableBytes() * 100 / Math.max(1, fs.getTotalBytes()));

        wBatt.setText(level + "%");
        wBattLabel.setText(plugged ? "SYNC RATIO · CHARGING" : "SYNC RATIO");
        magi(0, mem + "%", "MEMORY", mem < 92);
        magi(1, disk + "%", "STORAGE", disk < 90);
        magi(2, level + "%", plugged ? "CHARGING" : "BATTERY", plugged || level > 15);
    }

    private boolean isPlugged(Intent battery) {
        if (battery == null) battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private boolean stepsAllowed() {
        return checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    /** steps < 0: permission not given yet. */
    private void showSteps(int steps) {
        if (wDate == null) return;
        wDate.setText(steps < 0 ? "—" : String.format(Locale.ROOT, "%,d", steps));
        wStepsLabel.setText(steps < 0 ? "STEPS · TAP TO ALLOW" : "STEPS");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (stepsAllowed()) {
            steps.start();
            showSteps(Steps.cached(this));
        }
    }

    private void magi(int i, String value, String label, boolean ok) {
        magiVal[i].setText(value);
        magiLabel[i].setText(label);
        magiVerdict[i].setText(ok ? "承認" : "否決");
        magiVerdict[i].setTextColor(ok ? GREEN : WARN);
    }

    /* ---------- unit themes ---------- */

    private ThemePicker picker;

    private void setUnit(Units.Unit u) {
        unit = u;
        GREEN = u.a;
        WARN = u.warn;
        SAVER = u.sv;
        SAVER_DIM = u.svDim;
    }

    private void setUnitAndRebuild(Units.Unit u) {
        setUnit(u);
        skyBmp = null;
        towerBmp = null;
        towerTop = -1;
        build();
        updateWidgets(null);
    }

    private void openPicker() {
        if (picker != null) return;
        picker = new ThemePicker(this, unit, insetTop, insetBottom, new ThemePicker.Listener() {
            @Override
            public void onApply(Units.Unit u, boolean xd) {
                closePicker(() -> applyUnit(u, xd));
            }

            @Override
            public void onCancel() {
                closePicker(null);
            }
        });
        root.addView(picker, match());
    }

    private void closePicker(Runnable after) {
        ThemePicker p = picker;
        picker = null;
        if (p != null) p.dismiss(after);
    }

    /** Restyle everything this app owns, then ask Quickstar to restyle the system panel and control centre. */
    private void applyUnit(Units.Unit u, boolean xd) {
        if (u.id.equals(unit.id) && xd == xdark) {
            UnitStore.notifyQuickstar(this, u, xd, saver); // same unit: maybe just a new lock-clock face
            return;
        }
        UnitStore.save(this, u.id);
        UnitStore.saveMode(this, xd);
        xdark = xd;
        setUnitAndRebuild(u);
        worker.execute(() -> Walls.apply(this, u, xd));
        UnitStore.notifyQuickstar(this, u, xd, saver);
        Toast.makeText(this, u.name + " " + u.kanji, Toast.LENGTH_SHORT).show();
    }

    /* ---------- helpers ---------- */

    private TextView text(String s, float sp, int colour, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(saver && colour != Color.TRANSPARENT ? (colour == MUTED ? SAVER_DIM : SAVER) : colour);
        t.setTypeface(face);
        return t;
    }

    /** Surface colour: the unit's tint normally, deeper black in extra dark. */
    private int surf(int colour, float a) {
        return xdark ? Units.Unit.alpha(0xFF000000, Math.min(1f, a + 0.32f)) : Units.Unit.alpha(colour, a);
    }

    private GradientDrawable panel(int colour, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(radius);
        return g;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
