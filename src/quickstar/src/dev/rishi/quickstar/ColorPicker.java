package dev.rishi.quickstar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** Hue / saturation / value sliders with a hex field and preset swatches. */
final class ColorPicker extends LinearLayout {
    interface Listener {
        void onColor(int rgb);
    }

    static final int[] PRESETS = {
            0xFFD71921, 0xFF00C2A8, 0xFF2F6BFF, 0xFF8A5CFF, 0xFFFFB000, 0xFFFF4F8B, 0xFFA3FF12, 0xFF333333};

    private final float[] hsv = {0f, 1f, 1f};
    private final GradientBar hue;
    private final GradientBar sat;
    private final GradientBar val;
    private final EditText hex;
    private final View swatch;
    private Listener listener;
    private boolean updating;

    ColorPicker(Context c) {
        super(c);
        setOrientation(VERTICAL);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        swatch = new View(c);
        top.addView(swatch, Ui.lp(Ui.dp(44), Ui.dp(44), 0, 0, 14, 0));
        hex = new EditText(c);
        hex.setTypeface(Fonts.mono(c));
        hex.setTextColor(Ui.TEXT);
        hex.setTextSize(16);
        hex.setSingleLine(true);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6), new InputFilter.AllCaps()});
        hex.setBackground(Ui.shape(0, Ui.STROKE, 12));
        hex.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

            public void onTextChanged(CharSequence s, int a, int b, int d) {}

            public void afterTextChanged(Editable e) {
                if (updating || e.length() != 6) return;
                try {
                    Color.colorToHSV(0xFF000000 | Integer.parseInt(e.toString(), 16), hsv);
                    updating = true;
                    syncBars();
                    updating = false;
                    fire();
                } catch (NumberFormatException ignored) {
                    updating = false;
                }
            }
        });
        top.addView(hex, Ui.lp(Ui.dp(130), -2, 0, 0, 0, 0));
        addView(top, Ui.match(0, 4));

        hue = bar(c);
        hue.setColors(0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000);
        hue.setListener(v -> {
            hsv[0] = v * 360f;
            changed();
        });
        sat = bar(c);
        sat.setListener(v -> {
            hsv[1] = v;
            changed();
        });
        val = bar(c);
        val.setListener(v -> {
            hsv[2] = v;
            changed();
        });

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        for (int p : PRESETS) {
            View dot = new View(c);
            dot.setBackground(Ui.shape(p, Ui.STROKE, 20));
            dot.setOnClickListener(v -> setColor(p, true));
            row.addView(dot, Ui.lp(Ui.dp(36), Ui.dp(36), 0, 0, 10, 0));
        }
        hs.addView(row);
        addView(hs, Ui.match(8, 0));
        syncBars();
    }

    private GradientBar bar(Context c) {
        GradientBar b = new GradientBar(c);
        addView(b, Ui.match(0, 0));
        return b;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setColor(int rgb, boolean notify) {
        Color.colorToHSV(0xFF000000 | rgb, hsv);
        syncBars();
        if (notify) fire();
    }

    int getColor() {
        return Color.HSVToColor(hsv);
    }

    String getHex() {
        return String.format("%06X", getColor() & 0xFFFFFF);
    }

    private void changed() {
        syncBars();
        fire();
    }

    private void fire() {
        if (listener != null) listener.onColor(getColor());
    }

    private void syncBars() {
        boolean was = updating;
        updating = true;
        hue.setValue(hsv[0] / 360f);
        sat.setValue(hsv[1]);
        val.setValue(hsv[2]);
        sat.setColors(Color.HSVToColor(new float[]{hsv[0], 0f, hsv[2]}), Color.HSVToColor(new float[]{hsv[0], 1f, hsv[2]}));
        val.setColors(0xFF000000, Color.HSVToColor(new float[]{hsv[0], hsv[1], 1f}));
        swatch.setBackground(Ui.shape(getColor(), Ui.STROKE, 14));
        String h = getHex();
        if (!hex.getText().toString().equals(h)) hex.setText(h);
        updating = was;
    }
}
