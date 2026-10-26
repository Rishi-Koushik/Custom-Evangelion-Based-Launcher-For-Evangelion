package dev.nervos.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * The first unlock of each day opens on an episode title card: white serif type on black, numbered by the day of the
 * month in the old-style numerals the show uses (第弐拾参話), with the unit's name in its accent colour.
 * Shown for about a second and a half; a tap skips it.
 */
final class TitleCard extends FrameLayout {
    private static final String[] DIGITS = {"", "壱", "弐", "参", "四", "伍", "六", "七", "八", "九"};

    /** 23 -> 弐拾参, 10 -> 拾, 31 -> 参拾壱 */
    static String numeral(int n) {
        String tens = n >= 20 ? DIGITS[n / 10] + "拾" : n >= 10 ? "拾" : "";
        return tens + DIGITS[n % 10];
    }

    TitleCard(Context c, Units.Unit u, int insetTop, int insetBottom, Runnable done) {
        super(c);
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_MONTH);
        Typeface serif = Fonts.serif(c);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(28), 0, dp(28), 0);
        TextView ep = text(c, "EPISODE:" + day, 15, serif);
        ep.setLetterSpacing(0.08f);
        col.addView(ep);
        TextView kanji = text(c, "第" + numeral(day) + "話", 54, serif);
        kanji.setPadding(0, dp(18), 0, 0);
        col.addView(kanji);
        TextView weekday = text(c, cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH).toUpperCase(Locale.ROOT), 70, serif);
        weekday.setTextScaleX(0.78f);
        weekday.setIncludeFontPadding(false);
        weekday.setPadding(0, dp(8), 0, 0);
        col.addView(weekday);
        TextView date = text(c, (day + " " + cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) + " "
                + cal.get(Calendar.YEAR)).toUpperCase(Locale.ROOT), 22, serif);
        date.setTextScaleX(0.86f);
        date.setPadding(0, dp(6), 0, 0);
        col.addView(date);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        TextView unit = text(c, u.sub + " · " + u.kanji, 11, Fonts.mono(c));
        unit.setTextColor(u.a);
        unit.setLetterSpacing(0.2f);
        LayoutParams ulp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        ulp.setMargins(0, 0, dp(28), insetBottom + dp(36));
        addView(unit, ulp);

        final Runnable finish = new Runnable() {
            private boolean ran;

            @Override
            public void run() {
                if (ran) return;
                ran = true;
                animate().alpha(0f).setDuration(280).withEndAction(() -> {
                    if (getParent() != null) ((ViewGroup) getParent()).removeView(TitleCard.this);
                }).start();
                done.run();
            }
        };
        setOnClickListener(v -> finish.run());
        setAlpha(0f);
        animate().alpha(1f).setDuration(120).start();
        postDelayed(finish, 1500);
    }

    private TextView text(Context c, String s, float sp, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(0xFFF7F2E8);
        t.setTypeface(face);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
