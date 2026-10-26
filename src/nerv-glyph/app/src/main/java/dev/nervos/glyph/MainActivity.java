package dev.nervos.glyph;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** One screen: what the toys do, and a button into Nothing's Glyph Toys manager to switch them on. */
public class MainActivity extends Activity {
    private static final int GREEN = Color.parseColor("#A3FF3A"), MUTED = Color.parseColor("#8D849B");
    private Button overlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(24);
        root.setPadding(pad, dp(72), pad, pad);

        root.addView(text("NERV GLYPH", 13, GREEN, Fonts.mono(this)));
        root.addView(text("Glyph Matrix toys", 34, Color.WHITE, Fonts.serif(this)));
        root.addView(text(" ", 8, MUTED, null));
        root.addView(item("A.T. FIELD", "Octagon rings pulse outward. Long press holds the field."));
        root.addView(item("MAGI", "Three triangles deliberate. Long press calls the vote."));
        root.addView(item("SYNC RATIO", "Battery level with a ring gauge, or the time on long press. "
                + "Flashes an emergency frame in battery saver. Works as the always-on toy."));
        root.addView(item("ACTIVITY LIMIT", "Five minutes of internal power counting down. Long press starts, pauses, refills."));
        root.addView(item("PATTERN BLUE", "The Angel alert, flashing. Long press switches to Pattern Orange."));

        Button add = new Button(this);
        add.setText("Add toys in Glyph Toys");
        add.setAllCaps(true);
        add.setTextColor(Color.BLACK);
        add.setBackgroundColor(GREEN);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(28);
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> openToysManager());
        root.addView(add);
        root.addView(text("Then short-press the Glyph Button on the back to cycle toys; long press to interact.", 14, MUTED, null));

        SharedPreferences prefs = getSharedPreferences(ChargeWatchService.PREFS, MODE_PRIVATE);
        Switch charge = new Switch(this);
        charge.setText("A.T. Field when charging");
        charge.setTextColor(Color.WHITE);
        charge.setTextSize(16);
        charge.setChecked(prefs.getBoolean(ChargeWatchService.KEY_CHARGE_FX, true));
        charge.setPadding(0, dp(28), 0, dp(4));
        charge.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(ChargeWatchService.KEY_CHARGE_FX, on).apply();
            ChargeWatchService.sync(this);
        });
        root.addView(charge);
        root.addView(text("Plays the A.T. Field on the screen and the Glyph Matrix for a few seconds when you plug in, with your "
                + "unit's colours, power-up sound and haptic. Idle until a charger is connected.", 14, MUTED, null));
        overlay = new Button(this);
        overlay.setAllCaps(true);
        overlay.setTextColor(Color.BLACK);
        overlay.setBackgroundColor(GREEN);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        olp.topMargin = dp(16);
        overlay.setLayoutParams(olp);
        overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        root.addView(overlay);
        setContentView(root);
        ChargeWatchService.sync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // the charging screen needs "Display over other apps" to open while the phone is locked
        boolean ok = Settings.canDrawOverlays(this);
        overlay.setText(ok ? "Charging screen allowed ✓" : "Allow charging screen");
        overlay.setEnabled(!ok);
        overlay.setAlpha(ok ? 0.5f : 1f);
    }

    private void openToysManager() {
        Intent intent = new Intent().setComponent(new ComponentName("com.nothing.thirdparty",
                "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private LinearLayout item(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(14), 0, dp(4));
        box.addView(text(title, 13, GREEN, Fonts.mono(this)));
        box.addView(text(body, 16, Color.WHITE, null));
        return box;
    }

    private TextView text(String s, int sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        if (face != null) t.setTypeface(face);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
