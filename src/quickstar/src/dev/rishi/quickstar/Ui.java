package dev.rishi.quickstar;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small view-building toolkit in a Nothing-ish black / white / red / mono style. */
final class Ui {
    static final int BG = 0xFF000000;
    static final int CARD = 0xFF121212;
    static final int STROKE = 0xFF2B2B2B;
    static final int TEXT = 0xFFFFFFFF;
    static final int DIM = 0xFF8C8C8C;
    static final int RED = 0xFFD71921;

    private static float density = 3f;

    private Ui() {}

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static GradientDrawable shape(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    static TextView mono(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(Fonts.mono(c));
        t.setLetterSpacing(0.06f);
        return t;
    }

    static TextView section(Context c, String s) {
        TextView t = mono(c, s.toUpperCase(), 11, DIM);
        t.setPadding(dp(4), dp(18), 0, dp(8));
        return t;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(shape(CARD, STROKE, 22));
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        return l;
    }

    static TextView button(Context c, String s, boolean primary, View.OnClickListener l) {
        TextView t = mono(c, s.toUpperCase(), 12, primary ? 0xFFFFFFFF : TEXT);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(primary ? RED : 0, primary ? 0 : STROKE, 30));
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setOnClickListener(l);
        return t;
    }

    static TextView chip(Context c, String s, boolean selected, View.OnClickListener l) {
        TextView t = mono(c, s, 12, TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setOnClickListener(l);
        styleChip(t, selected);
        return t;
    }

    static void styleChip(TextView t, boolean selected) {
        t.setBackground(shape(selected ? TEXT : 0, selected ? 0 : STROKE, 30));
        t.setTextColor(selected ? BG : TEXT);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    static LinearLayout.LayoutParams match(float t, float b) {
        return lp(-1, -2, 0, t, 0, b);
    }

    /** Pads a root view by the system bar insets (the app is edge-to-edge on API 35+). */
    static void applyInsets(View root, boolean top, boolean bottom) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, top ? i.top : 0, i.right, bottom ? i.bottom : 0);
            return WindowInsets.CONSUMED;
        });
    }
}
