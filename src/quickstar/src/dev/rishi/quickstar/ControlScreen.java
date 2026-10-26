package dev.rishi.quickstar;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Settings for the control centre: switch it on, trigger side, glass look, colours, toggles, sections. */
final class ControlScreen extends LinearLayout {
    private final ControlCfg cfg;
    private final TextView status;
    private final TextView power;

    ControlScreen(Context c) {
        super(c);
        setOrientation(VERTICAL);
        cfg = ControlCfg.load(c);

        addView(Ui.section(c, "Lock screen"));
        LinearLayout lock = Ui.card(c);
        final TextView lockStatus = Ui.mono(c, Sys.lockHoldEnabled(c) ? "\u25CF HOLD-TO-SWITCH ON" : "\u25CB HOLD-TO-SWITCH OFF", 13,
                Sys.lockHoldEnabled(c) ? 0xFF4DD964 : Ui.DIM);
        lock.addView(lockStatus);
        lock.addView(Ui.text(c, "Hold the lock screen and the NERV theme picker opens, like on the home screen. Needs one "
                + "accessibility switch, which only sees the lock screen's own \"Customise\" button and reads nothing else.", 12, Ui.DIM),
                Ui.match(8, 0));
        lock.addView(Ui.button(c, Sys.lockHoldEnabled(c) ? "Manage" : "Turn on", !Sys.lockHoldEnabled(c), v -> openLockHold()),
                Ui.match(12, 0));
        addView(lock);

        addView(Ui.section(c, "Control centre"));
        LinearLayout card = Ui.card(c);
        status = Ui.mono(c, "", 13, Ui.TEXT);
        card.addView(status);
        card.addView(Ui.text(c, "A glass-style quick panel with connectivity, toggles, sliders, media and SIM info. "
                + "Swipe down from the top corner to open it. It runs as an accessibility service because that is the only "
                + "way an app can draw above the status bar. It does not read your screen.", 12, Ui.DIM), Ui.match(8, 0));
        LinearLayout row = new LinearLayout(c);
        power = Ui.button(c, "", true, v -> toggleService());
        row.addView(power, Ui.lp(0, -2, 0, 0, 8, 0));
        ((LayoutParams) power.getLayoutParams()).weight = 1;
        TextView open = Ui.button(c, "Open now", false, v -> openNow());
        row.addView(open, Ui.lp(0, -2, 0, 0, 0, 0));
        ((LayoutParams) open.getLayoutParams()).weight = 1;
        card.addView(row, Ui.match(14, 0));
        addView(card);

        addView(Ui.section(c, "Permissions (each is your choice)"));
        LinearLayout perms = Ui.card(c);
        perms.addView(Ui.text(c, "Optional, for live info in the panel. Each opens Android's own screen or prompt.", 12, Ui.DIM));
        perms.addView(Ui.button(c, "Wi-Fi name, SIM, Bluetooth", false, v -> ((android.app.Activity) getContext()).requestPermissions(
                new String[]{"android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_PHONE_STATE",
                        "android.permission.BLUETOOTH_CONNECT"}, 1)), Ui.match(10, 0));
        perms.addView(Ui.button(c, "Now-playing media", false, v -> getContext().startActivity(
                new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))), Ui.match(8, 0));
        perms.addView(Ui.button(c, "Do not disturb button", false, v -> getContext().startActivity(
                new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))), Ui.match(8, 0));
        addView(perms);

        addView(Ui.section(c, "Theme"));
        String[] tn = new String[ControlTheme.CHOICES.length], tv = new String[tn.length];
        for (int i = 0; i < tn.length; i++) {
            tn[i] = ControlTheme.CHOICES[i][1];
            tv[i] = ControlTheme.CHOICES[i][0];
        }
        addView(chips(tn, tv, () -> cfg.theme, v -> {
            cfg.theme = v;
            changed();
            // a unit restyles the system panel and NERV Home as well, so all of it stays in step
            if (!v.equals("glass")) UnitSync.apply(getContext(), v, false, true);
        }));
        addView(Ui.mono(c, "Picking a unit also restyles the system panel and NERV Home. Glass uses the colour and light/dark choices below.", 11, Ui.DIM),
                Ui.lp(-1, -2, 4, 0, 4, 0));

        addView(Ui.section(c, "Swipe from"));
        addView(chips(new String[]{"Top right", "Top left", "Whole top edge"}, new String[]{"right", "left", "full"},
                () -> cfg.side, v -> {
                    cfg.side = v;
                    changed();
                    if (ControlA11yService.instance != null) ControlA11yService.instance.reinstall();
                }));

        addView(Ui.section(c, "Glass theme"));
        addView(chips(new String[]{"Light glass", "Dark glass"}, new String[]{"0", "1"}, () -> cfg.dark ? "1" : "0", v -> {
            cfg.dark = v.equals("1");
            changed();
        }));
        LinearLayout sl = Ui.card(c);
        slider(sl, "Background blur", 0, 150, cfg.blur, v -> cfg.blur = v);
        slider(sl, "Dim behind", 0, 60, cfg.dim, v -> cfg.dim = v);
        slider(sl, "Card opacity", 30, 100, cfg.opacity, v -> cfg.opacity = v);
        slider(sl, "Roundness", 70, 130, cfg.radius, v -> cfg.radius = v);
        addView(sl, Ui.match(10, 0));

        addView(Ui.section(c, "Glass accent colour"));
        LinearLayout ac = Ui.card(c);
        ColorPicker picker = new ColorPicker(c);
        picker.setColor(cfg.accent, false);
        picker.setListener(rgb -> {
            cfg.accent = rgb;
            changed();
        });
        ac.addView(picker);
        addView(ac);

        addView(Ui.section(c, "Toggle buttons"));
        addView(Ui.mono(c, "Tap to add or remove. They appear in the order you pick them.", 11, Ui.DIM), Ui.lp(-1, -2, 4, 0, 4, 8));
        final List<TextView> chips = new ArrayList<>();
        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(VERTICAL);
        LinearLayout r = null;
        for (int i = 0; i < ControlCfg.CATALOG.length; i++) {
            if (i % 2 == 0) {
                r = new LinearLayout(c);
                grid.addView(r, Ui.match(0, 8));
            }
            final String id = ControlCfg.CATALOG[i][0];
            final TextView chip = Ui.chip(c, ControlCfg.CATALOG[i][1], cfg.tiles.contains(id), null);
            chip.setOnClickListener(v -> {
                if (cfg.tiles.contains(id)) cfg.tiles.remove(id);
                else if (cfg.tiles.size() < 12) cfg.tiles.add(id);
                Ui.styleChip(chip, cfg.tiles.contains(id));
                changed();
            });
            chips.add(chip);
            r.addView(chip, Ui.lp(0, -2, 0, 0, i % 2 == 0 ? 8 : 0, 0));
            ((LayoutParams) chip.getLayoutParams()).weight = 1;
        }
        addView(grid);

        addView(Ui.section(c, "Sections"));
        addView(chips(new String[]{"Media", "SIM row", "Quick row + modes"}, new String[]{"media", "sim", "bottom"}, null, null),
                Ui.match(0, 0));
        refresh();
    }

    /** Chip row of independent on/off switches for the three optional sections. */
    private LinearLayout chips(String[] names, String[] values, Getter cur, Setter set) {
        final LinearLayout wrap = new LinearLayout(getContext());
        wrap.setOrientation(VERTICAL);
        final List<TextView> all = new ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < names.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(getContext());
                wrap.addView(row, Ui.match(0, 8));
            }
            final String val = values[i];
            final boolean section = cur == null;
            boolean selected = section ? sectionOn(val) : val.equals(cur.get());
            TextView chip = Ui.chip(getContext(), names[i], selected, null);
            chip.setOnClickListener(v -> {
                if (section) {
                    toggleSection(val);
                    Ui.styleChip((TextView) v, sectionOn(val));
                } else {
                    set.set(val);
                    for (int k = 0; k < all.size(); k++) Ui.styleChip(all.get(k), values[k].equals(cur.get()));
                }
            });
            all.add(chip);
            row.addView(chip, Ui.lp(-2, -2, 0, 0, 8, 0));
        }
        return wrap;
    }

    private interface Getter {
        String get();
    }

    private interface Setter {
        void set(String v);
    }

    private interface IntSet {
        void set(int v);
    }

    private boolean sectionOn(String s) {
        switch (s) {
            case "media": return cfg.media;
            case "sim": return cfg.sim;
            default: return cfg.bottom;
        }
    }

    private void toggleSection(String s) {
        switch (s) {
            case "media": cfg.media = !cfg.media; break;
            case "sim": cfg.sim = !cfg.sim; break;
            default: cfg.bottom = !cfg.bottom;
        }
        changed();
    }

    private void slider(LinearLayout parent, String label, final int min, final int max, int val, final IntSet set) {
        final TextView t = Ui.mono(getContext(), label + "  " + val, 12, Ui.TEXT);
        GradientBar b = new GradientBar(getContext());
        b.setColors(0xFF333333, 0xFFFFFFFF);
        b.setValue((val - min) / (float) (max - min));
        b.setListener(v -> {
            int x = min + Math.round(v * (max - min));
            set.set(x);
            t.setText(label + "  " + x);
            changed();
        });
        parent.addView(t, Ui.lp(-2, -2, 0, 6, 0, 0));
        parent.addView(b);
    }

    private void changed() {
        cfg.save(getContext());
    }

    private void refresh() {
        boolean on = Sys.a11yEnabled(getContext());
        status.setText(on ? "● ON" : "○ OFF");
        status.setTextColor(on ? 0xFF4DD964 : Ui.DIM);
        power.setText(on ? "Manage" : "Turn on");
        power.setBackground(Ui.shape(on ? 0 : Ui.RED, on ? Ui.STROKE : 0, 30));
    }

    /** Opens the service's own page in Android settings; the user flips the switch. */
    private void openLockHold() {
        Context c = getContext();
        android.content.Intent i;
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            i = new android.content.Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(android.content.Intent.EXTRA_COMPONENT_NAME,
                            new android.content.ComponentName(c, LockHoldService.class));
        } else {
            i = new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        }
        c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Opens Android's own screens; the user switches each permission on themselves. */
    private void toggleService() {
        Context c = getContext();
        c.startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        Toast.makeText(c, "Open Installed apps > Quickstar control centre and switch it on", Toast.LENGTH_LONG).show();
    }

    private void openNow() {
        ControlA11yService s = ControlA11yService.instance;
        if (s == null) {
            getContext().startActivity(new android.content.Intent(getContext(), PreviewActivity.class));
            return;
        }
        s.openPanel();
    }
}
