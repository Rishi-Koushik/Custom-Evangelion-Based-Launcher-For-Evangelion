package dev.rishi.quickstar;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/** Five tabs: control centre, panel colour, tile layout, custom tiles, launcher. */
public class MainActivity extends Activity {
    interface ImageCb {
        void on(Bitmap b);
    }

    private static final int REQ_IMAGE = 7;
    private static final String CMD = "adb shell pm grant dev.rishi.quickstar android.permission.WRITE_SECURE_SETTINGS\n"
            + "adb shell appops set dev.rishi.quickstar WRITE_SETTINGS allow";
    private static final String[] TABS = {"Control", "Panel", "Layout", "Tiles", "Launcher"};

    private final View[] screens = new View[5];
    private final TextView[] tabViews = new TextView[5];
    private FrameLayout content;
    private LinearLayout setupHost;
    private ScrollView scroll;
    private int current = -1;
    private ImageCb pending;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().setDecorFitsSystemWindows(false);
        Sys.ensureBackup(this);
        selfTest();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        Ui.applyInsets(root, true, true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.mono(this, "QUICKSTAR", 20, Ui.TEXT);
        title.setTypeface(Typeface.create(Fonts.mono(this), Typeface.BOLD));
        head.addView(title);
        TextView dot = Ui.text(this, "●", 10, Ui.RED);
        head.addView(dot, Ui.lp(-2, -2, 8, 0, 0, 0));
        TextView sub = Ui.mono(this, "for Nothing OS", 11, Ui.DIM);
        sub.setGravity(Gravity.END);
        head.addView(sub, new LinearLayout.LayoutParams(0, -2, 1f));
        head.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(6));
        root.addView(head);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(16), 0, Ui.dp(16), Ui.dp(24));
        setupHost = new LinearLayout(this);
        setupHost.setOrientation(LinearLayout.VERTICAL);
        body.addView(setupHost);
        content = new FrameLayout(this);
        body.addView(content);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(0xFF0A0A0A);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Ui.mono(this, TABS[i].toUpperCase(), 12, Ui.DIM);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(16), 0, Ui.dp(16));
            t.setTextSize(11);
            t.setOnClickListener(v -> show(idx));
            tabViews[i] = t;
            nav.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        root.addView(nav);
        setContentView(root);
        show(getIntent().getIntExtra("tab", 0));
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        show(i.getIntExtra("tab", current));
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildSetup();
    }

    private void show(int idx) {
        if (idx < 0 || idx > 4) idx = 0;
        if (screens[idx] == null) {
            switch (idx) {
                case 0: screens[0] = new ControlScreen(this); break;
                case 1: screens[1] = new PanelScreen(this); break;
                case 2: screens[2] = new LayoutScreen(this); break;
                case 3: screens[3] = new TilesScreen(this); break;
                default: screens[4] = new LauncherScreen(this);
            }
        }
        content.removeAllViews();
        content.addView(screens[idx]);
        current = idx;
        for (int i = 0; i < 5; i++) tabViews[i].setTextColor(i == idx ? Ui.RED : Ui.DIM);
        scroll.scrollTo(0, 0);
    }

    void refreshLayoutTab() {
        screens[2] = null;
    }

    /** One-time permission banner, shown until WRITE_SECURE_SETTINGS has been granted over USB. */
    private void buildSetup() {
        setupHost.removeAllViews();
        boolean secure = Sys.canWriteSecure(this), system = Sys.canWriteSystem(this);
        if (secure && system) return;
        LinearLayout card = Ui.card(this);
        card.setBackground(Ui.shape(0xFF1A0B0C, 0xFF5A1A1E, 22));
        card.addView(Ui.mono(this, "ONE-TIME SETUP", 11, Ui.RED));
        card.addView(Ui.text(this, "Quickstar edits system settings, which Android only allows after you grant "
                + "permission over USB. Run this on your PC with the phone plugged in:", 13, Ui.TEXT), Ui.match(8, 0));
        TextView cmd = Ui.mono(this, CMD, 10, Ui.TEXT);
        cmd.setBackground(Ui.shape(0xFF000000, Ui.STROKE, 12));
        cmd.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        cmd.setTextIsSelectable(true);
        card.addView(cmd, Ui.match(10, 0));
        LinearLayout row = new LinearLayout(this);
        row.addView(Ui.button(this, "Copy", false, v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("grant", CMD));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }), Ui.lp(-2, -2, 0, 0, 8, 0));
        row.addView(Ui.button(this, "Recheck", true, v -> buildSetup()));
        card.addView(row, Ui.match(12, 0));
        setupHost.addView(card, Ui.match(8, 4));
    }

    void pickImage(ImageCb cb) {
        pending = cb;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        startActivityForResult(i, REQ_IMAGE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMAGE || res != RESULT_OK || data == null || data.getData() == null || pending == null) return;
        Bitmap b = decode(this, data.getData(), 512);
        if (b != null) pending.on(b);
        else Toast.makeText(this, "Could not read that image", Toast.LENGTH_SHORT).show();
        pending = null;
    }

    static Bitmap decode(Context c, Uri uri, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int s = 1;
            while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Logs whether re-packing the phone's current grid reproduces it exactly (checks the model). */
    private void selfTest() {
        try {
            String nt = Sys.getSecure(this, Sys.K_NT_TILES);
            if (nt.isEmpty()) return;
            String re = TileLayout.toNt(TileLayout.load(this));
            Log.i("QS_SELFTEST", "grid roundtrip " + (re.equals(nt) ? "OK" : "DIFFERS"));
            if (!re.equals(nt)) Log.i("QS_SELFTEST", "orig=" + nt + "\nrepk=" + re);
        } catch (RuntimeException e) {
            Log.w("QS_SELFTEST", "failed", e);
        }
    }
}
