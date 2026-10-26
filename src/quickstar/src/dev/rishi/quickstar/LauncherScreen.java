package dev.rishi.quickstar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Settings for the Quickstar launcher and its icon look. */
final class LauncherScreen extends LinearLayout {
    private final LCfg cfg;
    private final LinearLayout previewRow;
    private final List<Apps.App> sample = new ArrayList<>();
    private final LinearLayout colorBox;

    LauncherScreen(Context c) {
        super(c);
        setOrientation(VERTICAL);
        cfg = LCfg.load(c);
        List<Apps.App> all = Apps.list(c);
        for (int i = 0; i < Math.min(5, all.size()); i++) sample.add(all.get(i));

        addView(Ui.section(c, "Preview"));
        LinearLayout pcard = Ui.card(c);
        previewRow = new LinearLayout(c);
        previewRow.setGravity(Gravity.CENTER);
        pcard.addView(previewRow);
        addView(pcard);

        addView(Ui.section(c, "Icon style"));
        addView(chips(new String[]{"Monochrome", "Original"}, new String[]{LCfg.MONO, LCfg.ORIGINAL},
                () -> cfg.style, v -> {
                    cfg.style = v;
                    changed();
                }));

        addView(Ui.section(c, "Icon shape"));
        addView(chips(LCfg.SHAPES, LCfg.SHAPES, () -> cfg.shape, v -> {
            cfg.shape = v;
            changed();
        }));

        List<IconPacks.Pack> packs = IconPacks.installed(c);
        addView(Ui.section(c, "Icon pack"));
        String[] pn = new String[packs.size() + 1], pv = new String[packs.size() + 1];
        pn[0] = "None";
        pv[0] = "";
        for (int i = 0; i < packs.size(); i++) {
            pn[i + 1] = packs.get(i).label;
            pv[i + 1] = packs.get(i).pkg;
        }
        addView(chips(pn, pv, () -> cfg.pack, v -> {
            cfg.pack = v;
            changed();
        }));
        if (packs.isEmpty()) {
            addView(Ui.mono(c, "No ADW/Nova icon packs installed. Install one from the Play Store and it shows up here.", 11, Ui.DIM),
                    Ui.lp(-1, -2, 4, 8, 4, 0));
        }

        addView(Ui.section(c, "Monochrome colours"));
        colorBox = new LinearLayout(c);
        colorBox.setOrientation(VERTICAL);
        LinearLayout fc = Ui.card(c);
        fc.addView(Ui.mono(c, "ICON", 11, Ui.DIM));
        ColorPicker fg = new ColorPicker(c);
        fg.setColor(cfg.fg, false);
        fg.setListener(rgb -> {
            cfg.fg = rgb;
            changed();
        });
        fc.addView(fg);
        LinearLayout bc = Ui.card(c);
        bc.addView(Ui.mono(c, "BACKGROUND", 11, Ui.DIM));
        ColorPicker bg = new ColorPicker(c);
        bg.setColor(cfg.bg, false);
        bg.setListener(rgb -> {
            cfg.bg = rgb;
            changed();
        });
        bc.addView(bg);
        colorBox.addView(fc);
        colorBox.addView(bc, Ui.match(10, 0));
        addView(colorBox);

        addView(Ui.section(c, "Home grid"));
        addView(chips(new String[]{"4 columns", "5 columns", "6 columns"}, new String[]{"4", "5", "6"},
                () -> String.valueOf(cfg.cols), v -> {
                    cfg.cols = Integer.parseInt(v);
                    changed();
                }));
        LinearLayout sizeCard = Ui.card(c);
        final TextView sizeVal = Ui.mono(c, "Icon size " + cfg.size + "%", 12, Ui.TEXT);
        GradientBar sizeBar = new GradientBar(c);
        sizeBar.setColors(0xFF333333, 0xFFFFFFFF);
        sizeBar.setValue((cfg.size - 70) / 60f);
        sizeBar.setListener(v -> {
            cfg.size = 70 + Math.round(v * 12) * 5;
            sizeVal.setText("Icon size " + cfg.size + "%");
            changed();
        });
        sizeCard.addView(sizeVal);
        sizeCard.addView(sizeBar);
        addView(sizeCard, Ui.match(10, 0));
        addView(chips(new String[]{"Labels on", "Labels off"}, new String[]{"1", "0"},
                () -> cfg.labels ? "1" : "0", v -> {
                    cfg.labels = v.equals("1");
                    changed();
                }), Ui.match(10, 0));

        LinearLayout buttons = new LinearLayout(c);
        TextView open = Ui.button(c, "Open launcher", true, v -> {
            Intent i = new Intent(getContext(), HomeActivity.class);
            getContext().startActivity(i);
        });
        TextView def = Ui.button(c, "Set as default", false, v ->
                getContext().startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)));
        buttons.addView(open, Ui.lp(0, -2, 0, 0, 8, 0));
        ((LayoutParams) open.getLayoutParams()).weight = 1;
        buttons.addView(def, Ui.lp(0, -2, 0, 0, 0, 0));
        ((LayoutParams) def.getLayoutParams()).weight = 1;
        addView(buttons, Ui.match(20, 0));
        addView(Ui.mono(c, "Long-press an app in the launcher's drawer to pin it, or to give it its own icon.", 11, Ui.DIM),
                Ui.lp(-1, -2, 4, 14, 4, 0));
        changed();
    }

    private interface Getter {
        String get();
    }

    private interface Setter {
        void set(String v);
    }

    private LinearLayout chips(String[] names, String[] values, Getter cur, Setter set) {
        final LinearLayout wrap = new LinearLayout(getContext());
        wrap.setOrientation(VERTICAL);
        final List<TextView> all = new ArrayList<>();
        LinearLayout row = null;
        int perRow = 3;
        for (int i = 0; i < names.length; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(getContext());
                wrap.addView(row, Ui.match(0, 8));
            }
            final String val = values[i];
            TextView chip = Ui.chip(getContext(), names[i], val.equals(cur.get()), null);
            chip.setOnClickListener(v -> {
                set.set(val);
                for (int k = 0; k < all.size(); k++) Ui.styleChip(all.get(k), values[k].equals(cur.get()));
            });
            all.add(chip);
            row.addView(chip, Ui.lp(-2, -2, 0, 0, 8, 0));
        }
        return wrap;
    }

    private void changed() {
        Context c = getContext();
        cfg.save(c);
        IconEngine.clear();
        previewRow.removeAllViews();
        int px = Ui.dp(52 * cfg.size / 100f);
        for (Apps.App a : sample) {
            ImageView iv = new ImageView(c);
            Bitmap b = IconEngine.get(c, a.cn, px, cfg);
            iv.setImageBitmap(b);
            previewRow.addView(iv, Ui.lp(px, px, 6, 0, 6, 0));
        }
        colorBox.setAlpha(cfg.style.equals(LCfg.MONO) && cfg.pack.isEmpty() ? 1f : 0.4f);
    }
}
