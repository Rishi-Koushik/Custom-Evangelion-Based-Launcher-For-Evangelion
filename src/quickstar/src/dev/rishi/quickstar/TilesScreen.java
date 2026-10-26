package dev.rishi.quickstar;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.service.quicksettings.TileService;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/** Four custom quick settings tiles: label, icon (built-in glyph or your own image) and action. */
final class TilesScreen extends LinearLayout {
    private final MainActivity host;

    TilesScreen(MainActivity host) {
        super(host);
        this.host = host;
        setOrientation(VERTICAL);
        addView(Ui.mono(host, "Each tile appears in the panel's tile list. Add it to the grid, then set its icon and action here.", 11, Ui.DIM),
                Ui.lp(-1, -2, 4, 14, 4, 0));
        for (int i = 1; i <= 4; i++) addView(new SlotCard(host, i), Ui.match(14, 0));
    }

    private final class SlotCard extends LinearLayout {
        private final int slot;
        private final Slots.Cfg cfg;
        private final ImageView iconView;
        private final TextView targetView;
        private final LinearLayout glyphRow;
        private final LinearLayout typeRow;

        SlotCard(Context c, int slot) {
            super(c);
            this.slot = slot;
            this.cfg = Slots.get(c, slot);
            setOrientation(VERTICAL);
            setBackground(Ui.shape(Ui.CARD, Ui.STROKE, 22));
            setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));

            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            iconView = new ImageView(c);
            iconView.setBackground(Ui.shape(0xFF2A2A2A, 0, 30));
            iconView.setPadding(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(12));
            head.addView(iconView, Ui.lp(Ui.dp(52), Ui.dp(52), 0, 0, 14, 0));
            EditText label = new EditText(c);
            label.setHint("Tile " + slot + " label");
            label.setHintTextColor(Ui.DIM);
            label.setTextColor(Ui.TEXT);
            label.setSingleLine(true);
            label.setInputType(InputType.TYPE_CLASS_TEXT);
            label.setText(cfg.label);
            label.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

                public void onTextChanged(CharSequence s, int a, int b, int d) {}

                public void afterTextChanged(Editable e) {
                    cfg.label = e.toString().trim();
                }
            });
            head.addView(label, Ui.lp(0, -2, 0, 0, 0, 0));
            ((LayoutParams) label.getLayoutParams()).weight = 1;
            addView(head);

            addView(Ui.section(c, "Icon"));
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            glyphRow = new LinearLayout(c);
            hs.addView(glyphRow);
            addView(hs);
            addView(Ui.button(c, "Use my own image", false, v -> host.pickImage(this::onImage)),
                    Ui.lp(-2, -2, 0, 10, 0, 0));

            addView(Ui.section(c, "When tapped"));
            typeRow = new LinearLayout(c);
            addView(typeRow);
            targetView = Ui.mono(c, "", 12, Ui.TEXT);
            targetView.setPadding(Ui.dp(4), Ui.dp(10), 0, Ui.dp(6));
            addView(targetView);
            addView(Ui.button(c, "Choose…", false, v -> chooseTarget()), Ui.lp(-2, -2, 0, 0, 0, 0));

            LinearLayout row = new LinearLayout(c);
            TextView save = Ui.button(c, "Save", true, v -> save());
            TextView add = Ui.button(c, "Add to panel", false, v -> addToPanel());
            row.addView(save, Ui.lp(0, -2, 0, 0, 8, 0));
            ((LayoutParams) save.getLayoutParams()).weight = 1;
            row.addView(add, Ui.lp(0, -2, 0, 0, 0, 0));
            ((LayoutParams) add.getLayoutParams()).weight = 1;
            addView(row, Ui.match(16, 0));
            rebuild();
        }

        private void rebuild() {
            Context c = getContext();
            Bitmap b = Slots.icon(c, slot, cfg);
            iconView.setImageBitmap(b);

            glyphRow.removeAllViews();
            for (String g : Glyphs.NAMES) {
                ImageView iv = new ImageView(c);
                iv.setImageBitmap(Glyphs.draw(g, 72));
                boolean sel = !cfg.customIcon && g.equals(cfg.glyph);
                iv.setBackground(Ui.shape(sel ? 0xFF3A1214 : 0xFF1E1E1E, sel ? Ui.RED : Ui.STROKE, 24));
                iv.setPadding(Ui.dp(9), Ui.dp(9), Ui.dp(9), Ui.dp(9));
                iv.setOnClickListener(v -> {
                    cfg.glyph = g;
                    cfg.customIcon = false;
                    rebuild();
                });
                glyphRow.addView(iv, Ui.lp(Ui.dp(44), Ui.dp(44), 0, 0, 8, 0));
            }

            typeRow.removeAllViews();
            String[][] types = {{Slots.APP, "App"}, {Slots.URL, "Link"}, {Slots.SETTINGS, "Settings"}};
            for (String[] t : types) {
                typeRow.addView(Ui.chip(c, t[1], cfg.type.equals(t[0]), v -> {
                    if (!cfg.type.equals(t[0])) cfg.target = "";
                    cfg.type = t[0];
                    rebuild();
                }), Ui.lp(-2, -2, 0, 0, 8, 0));
            }
            targetView.setText(describe());
        }

        private String describe() {
            if (cfg.target.isEmpty()) return "Nothing chosen yet";
            switch (cfg.type) {
                case Slots.APP:
                    try {
                        return getContext().getPackageManager().getApplicationLabel(
                                getContext().getPackageManager().getApplicationInfo(cfg.target, 0)).toString();
                    } catch (Exception e) {
                        return cfg.target;
                    }
                case Slots.SETTINGS:
                    for (String[] s : Slots.SCREENS) if (s[1].equals(cfg.target)) return s[0] + " settings";
                    return cfg.target;
                default:
                    return cfg.target;
            }
        }

        private void chooseTarget() {
            Context c = getContext();
            switch (cfg.type) {
                case Slots.APP:
                    Apps.pickDialog(c, "Open which app?", a -> {
                        cfg.target = a.cn.getPackageName();
                        if (cfg.label.isEmpty()) cfg.label = a.label;
                        rebuild();
                    });
                    break;
                case Slots.SETTINGS:
                    String[] names = new String[Slots.SCREENS.length];
                    for (int i = 0; i < names.length; i++) names[i] = Slots.SCREENS[i][0];
                    new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Open which settings screen?")
                            .setItems(names, (d, w) -> {
                                cfg.target = Slots.SCREENS[w][1];
                                rebuild();
                            }).show();
                    break;
                default:
                    final EditText in = new EditText(c);
                    in.setHint("https://example.com");
                    in.setText(cfg.target);
                    in.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                    new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Open which link?")
                            .setView(in)
                            .setPositiveButton("OK", (d, w) -> {
                                cfg.target = in.getText().toString().trim();
                                rebuild();
                            }).show();
            }
        }

        private void onImage(Bitmap src) {
            try {
                Slots.saveCustomIcon(getContext(), slot, src);
                cfg.customIcon = true;
                rebuild();
            } catch (IOException e) {
                Toast.makeText(getContext(), "Could not save that image", Toast.LENGTH_SHORT).show();
            }
        }

        private void save() {
            Context c = getContext();
            Slots.put(c, slot, cfg);
            try {
                Class<?> k = Class.forName("dev.rishi.quickstar.Tile" + slot);
                TileService.requestListeningState(c, new ComponentName(c, k));
            } catch (ClassNotFoundException ignored) {
                // cannot happen: the four classes ship in this app
            }
            Toast.makeText(c, "Tile " + slot + " saved", Toast.LENGTH_SHORT).show();
        }

        private void addToPanel() {
            save();
            Context c = getContext();
            if (!Sys.canWriteSecure(c)) {
                Toast.makeText(c, "Grant WRITE_SECURE_SETTINGS first (see setup)", Toast.LENGTH_LONG).show();
                return;
            }
            Sys.ensureBackup(c);
            if (TileLayout.addSpec(c, TileLayout.customSpec(slot))) {
                Sys.reloadSystemUi(c);
                host.refreshLayoutTab();
                Toast.makeText(c, "Added. Check the Layout tab to resize or move it.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
