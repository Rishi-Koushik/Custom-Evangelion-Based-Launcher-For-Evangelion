package dev.rishi.quickstar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal launcher: pinned apps, dock, searchable drawer, themed / reshaped / custom icons. */
public class HomeActivity extends Activity {
    private static final int REQ_ICON = 21;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private LCfg cfg;
    private List<Apps.App> all = new ArrayList<>();
    private FrameLayout root;
    private TextView clock;
    private TextView date;
    private LinearLayout pinned;
    private LinearLayout dock;
    private FrameLayout drawer;
    private LinearLayout drawerList;
    private EditText search;
    private boolean drawerOpen;
    private Apps.App iconTarget;
    private int insetBottom;

    private final BroadcastReceiver pkgs = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            reload();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            date.setText(new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now).toUpperCase());
            main.postDelayed(this, 20000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().setDecorFitsSystemWindows(false);
        root = new FrameLayout(this);
        setContentView(root);

        final LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        clock = Ui.mono(this, "", 64, Ui.TEXT);
        clock.setTypeface(Typeface.create(Fonts.mono(this), Typeface.BOLD));
        date = Ui.mono(this, "", 13, 0xCCFFFFFF);
        home.addView(clock, Ui.lp(-2, -2, 28, 36, 0, 0));
        home.addView(date, Ui.lp(-2, -2, 32, 0, 0, 0));
        pinned = new LinearLayout(this);
        pinned.setOrientation(LinearLayout.VERTICAL);
        ScrollView ps = new ScrollView(this);
        ps.setVerticalScrollBarEnabled(false);
        ps.addView(pinned);
        home.addView(ps, new LinearLayout.LayoutParams(-1, 0, 1f));
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(Ui.shape(0x66000000, 0, 34));
        dock.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        home.addView(dock, Ui.lp(-1, -2, 16, 6, 16, 18));
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        drawer = new FrameLayout(this);
        drawer.setBackgroundColor(0xFC000000);
        drawer.setVisibility(View.GONE);
        final LinearLayout dcol = new LinearLayout(this);
        dcol.setOrientation(LinearLayout.VERTICAL);
        search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setTypeface(Fonts.mono(this));
        search.setBackground(Ui.shape(0xFF161616, Ui.STROKE, 30));
        search.setPadding(Ui.dp(20), Ui.dp(12), Ui.dp(20), Ui.dp(12));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

            public void onTextChanged(CharSequence s, int a, int c, int d) {}

            public void afterTextChanged(Editable e) {
                fillDrawer();
            }
        });
        search.setOnEditorActionListener((v, id, e) -> {
            List<Apps.App> m = matches();
            if (!m.isEmpty()) launch(m.get(0));
            return true;
        });
        dcol.addView(search, Ui.lp(-1, -2, 16, 12, 16, 8));
        ScrollView ds = new ScrollView(this);
        ds.setVerticalScrollBarEnabled(false);
        drawerList = new LinearLayout(this);
        drawerList.setOrientation(LinearLayout.VERTICAL);
        ds.addView(drawerList);
        dcol.addView(ds, new LinearLayout.LayoutParams(-1, 0, 1f));
        drawer.addView(dcol, new FrameLayout.LayoutParams(-1, -1));
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            home.setPadding(i.left, i.top, i.right, i.bottom);
            dcol.setPadding(i.left, i.top, i.right, i.bottom);
            insetBottom = i.bottom;
            return WindowInsets.CONSUMED;
        });

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent a, MotionEvent e, float vx, float vy) {
                if (a != null && e != null && vy < -900 && a.getY() - e.getY() > Ui.dp(80)) {
                    openDrawer();
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                startActivity(new Intent(HomeActivity.this, MainActivity.class).putExtra("tab", 4));
            }
        });
        home.setOnTouchListener((v, e) -> gd.onTouchEvent(e));
        ps.setOnTouchListener((v, e) -> {
            gd.onTouchEvent(e);
            return false;
        });

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgs, f);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(pkgs);
        pool.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IconPacks.clear();
        reload();
        main.removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (Intent.ACTION_MAIN.equals(i.getAction())) closeDrawer();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) closeDrawer();
    }

    private void reload() {
        cfg = LCfg.load(this);
        all = Apps.list(this);
        IconEngine.clear();
        fillHome();
        fillDrawer();
    }

    private List<Apps.App> resolve(List<String> keys) {
        List<Apps.App> out = new ArrayList<>();
        for (String k : keys) for (Apps.App a : all) if (a.key().equals(k)) out.add(a);
        return out;
    }

    private void fillHome() {
        pinned.removeAllViews();
        grid(pinned, resolve(LCfg.list(this, "pins")), cfg.cols, 1f);
        dock.removeAllViews();
        List<Apps.App> d = resolve(LCfg.list(this, "dock"));
        for (Apps.App a : d) {
            View cell = cell(a, 1f, false);
            dock.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        dock.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);
        if (LCfg.list(this, "pins").isEmpty() && d.isEmpty()) {
            TextView hint = Ui.mono(this, "SWIPE UP FOR APPS\nLONG-PRESS AN APP TO PIN IT", 12, 0xAAFFFFFF);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, Ui.dp(60), 0, 0);
            pinned.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void fillDrawer() {
        drawerList.removeAllViews();
        grid(drawerList, matches(), Math.max(cfg.cols, 4), 1f);
    }

    private List<Apps.App> matches() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all;
        List<Apps.App> out = new ArrayList<>();
        for (Apps.App a : all) if (a.label.toLowerCase(Locale.ROOT).contains(q)) out.add(a);
        return out;
    }

    private void grid(LinearLayout parent, List<Apps.App> apps, int cols, float weight) {
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(cell(apps.get(i), weight, true), new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (row != null) {
            for (int i = apps.size() % cols; i != 0 && i < cols; i++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private View cell(final Apps.App a, float weight, boolean label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(0, Ui.dp(10), 0, Ui.dp(6));
        final int px = Ui.dp(56 * cfg.size / 100f);
        final ImageView iv = new ImageView(this);
        iv.setTag(a.key());
        c.addView(iv, new LinearLayout.LayoutParams(px, px));
        final LCfg snap = cfg;
        pool.execute(() -> {
            final Bitmap bmp = IconEngine.get(HomeActivity.this, a.cn, px, snap);
            main.post(() -> {
                if (a.key().equals(iv.getTag())) iv.setImageBitmap(bmp);
            });
        });
        if (label && cfg.labels) {
            TextView t = Ui.text(this, a.label, 11, Ui.TEXT);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(4, 0, 1, 0xFF000000);
            c.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        c.setOnClickListener(v -> launch(a));
        c.setOnLongClickListener(v -> {
            menu(a);
            return true;
        });
        return c;
    }

    private void launch(Apps.App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            return;
        }
        closeDrawer();
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        search.setText("");
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationY(root.getHeight());
        drawer.animate().translationY(0).setDuration(220).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        android.view.inputmethod.InputMethodManager im = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
        drawer.animate().translationY(root.getHeight()).setDuration(200)
                .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    private void menu(final Apps.App a) {
        final List<String> pins = LCfg.list(this, "pins"), dockList = LCfg.list(this, "dock");
        final String k = a.key();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (pins.contains(k)) {
            labels.add("Remove from home");
            acts.add(() -> {
                pins.remove(k);
                LCfg.putList(this, "pins", pins);
                fillHome();
            });
        } else {
            labels.add("Pin to home");
            acts.add(() -> {
                pins.add(k);
                LCfg.putList(this, "pins", pins);
                fillHome();
                closeDrawer();
            });
        }
        if (dockList.contains(k)) {
            labels.add("Remove from dock");
            acts.add(() -> {
                dockList.remove(k);
                LCfg.putList(this, "dock", dockList);
                fillHome();
            });
        } else if (dockList.size() < 5) {
            labels.add("Add to dock");
            acts.add(() -> {
                dockList.add(k);
                LCfg.putList(this, "dock", dockList);
                fillHome();
                closeDrawer();
            });
        }
        labels.add("Change icon…");
        acts.add(() -> {
            iconTarget = a;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
            startActivityForResult(i, REQ_ICON);
        });
        if (IconEngine.customFile(this, a.cn).exists()) {
            labels.add("Reset icon");
            acts.add(() -> {
                IconEngine.resetCustom(this, a.cn);
                fillHome();
                fillDrawer();
            });
        }
        labels.add("App info");
        acts.add(() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.cn.getPackageName()))));
        labels.add("Uninstall");
        acts.add(() -> startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.cn.getPackageName()))));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(a.label)
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ICON || res != RESULT_OK || data == null || data.getData() == null || iconTarget == null) return;
        Bitmap b = MainActivity.decode(this, data.getData(), 512);
        if (b != null) {
            try {
                IconEngine.saveCustom(this, iconTarget.cn, b);
            } catch (java.io.IOException ignored) {
                // could not store; the old icon stays
            }
            fillHome();
            fillDrawer();
        }
    }
}
