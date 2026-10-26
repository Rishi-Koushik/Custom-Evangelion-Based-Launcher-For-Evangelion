package dev.nervos.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hold the home screen to open this: swipe through the unit themes, each shown as a full-screen preview of its lock
 * screen (poster and clock), pick the lock-clock face, and apply. Applying restyles the wallpaper, the icons, the battery-saver look and, through
 * Quickstar, the system panel.
 */
final class ThemePicker extends FrameLayout {
    interface Listener {
        void onApply(Units.Unit unit, boolean xdark);

        void onCancel();
    }

    private final Listener listener;
    private final Units.Unit current;
    private final HorizontalScrollView pager;
    private final FrameLayout[] cards = new FrameLayout[Units.ALL.length];
    private final TextView apply;
    /** one page is the full screen width; each card sits inside it with half the gap on either side */
    private final int page, cardW, cardH, gap, insetTop, insetBottom;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private int focused;
    /** direction of the last fling (-1, 0, 1): a flick turns exactly one page */
    private int flingDir;
    private final boolean xdark;
    /** the lock-clock face being previewed; saved only on Apply */
    private String clockFace;
    private final LockClock.Preview[] previews = new LockClock.Preview[Units.ALL.length];
    private final TextView[] faceChips = new TextView[LockClock.IDS.length];

    ThemePicker(Context c, Units.Unit current, int insetTop, int insetBottom, Listener l) {
        super(c);
        this.listener = l;
        this.current = current;
        this.xdark = UnitStore.xdark(c);
        this.insetTop = insetTop;
        this.insetBottom = insetBottom;
        this.clockFace = LockClock.current(c);
        Rect screen = c.getSystemService(WindowManager.class).getCurrentWindowMetrics().getBounds();
        page = screen.width();
        gap = dp(10) & ~1; // even, so two half-gaps add back up to one page exactly
        cardW = page - gap;
        cardH = screen.height() - gap;
        setBackgroundColor(0xFF050308);
        setClickable(true);

        pager = new HorizontalScrollView(c) {
            @Override
            public void fling(int velocityX) {
                flingDir = Integer.signum(velocityX); // paging is done in snap()
            }
        };
        pager.setHorizontalScrollBarEnabled(false);
        pager.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(c);
        for (int i = 0; i < Units.ALL.length; i++) {
            cards[i] = card(c, Units.ALL[i], i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
            row.addView(cards[i], lp);
            if (Units.ALL[i].id.equals(current.id)) focused = i;
        }
        pager.addView(row);
        addView(pager, new LayoutParams(-1, -1));
        pager.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) flingDir = 0;
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                main.post(this::snap);
            }
            return false;
        });

        // the title floats over the previews; it takes no touches so swipes reach the pager
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(0, insetTop + dp(14), 0, dp(28));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC050308, 0x00050308}));
        TextView title = text(c, "THEME · テーマ", 24, Color.WHITE, Fonts.serif(c));
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text(c, "SWIPE · TAP APPLY", 10, Units.MUTED, Fonts.mono(c));
        hint.setGravity(Gravity.CENTER);
        hint.setLetterSpacing(0.2f);
        top.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        addView(top, new LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout buttons = new LinearLayout(c);
        apply = text(c, "APPLY", 15, Color.BLACK, Fonts.cond(c));
        apply.setGravity(Gravity.CENTER);
        apply.setLetterSpacing(0.2f);
        apply.setOnClickListener(v -> {
            LockClock.save(getContext(), clockFace); // kept until it is changed here again
            Haptics.apply(getContext(), Units.ALL[focused]);
            listener.onApply(Units.ALL[focused], xdark);
        });
        TextView cancel = text(c, "CANCEL", 15, Color.WHITE, Fonts.cond(c));
        cancel.setGravity(Gravity.CENTER);
        cancel.setLetterSpacing(0.2f);
        cancel.setBackground(pill(0x99050308, 0x66FFFFFF));
        cancel.setOnClickListener(v -> listener.onCancel());
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, -1, 1f);
        b1.setMargins(dp(24), 0, dp(8), 0);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, -1, 1f);
        b2.setMargins(dp(8), 0, dp(24), 0);
        buttons.addView(cancel, b1);
        buttons.addView(apply, b2);
        LayoutParams blp = new LayoutParams(-1, dp(52), Gravity.BOTTOM);
        blp.bottomMargin = insetBottom + dp(18);
        addView(buttons, blp);

        // the lock-screen clock face, the same for every unit
        LinearLayout faces = new LinearLayout(c);
        faces.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(c, "CLOCK", 10, Units.MUTED, Fonts.dot(c));
        label.setLetterSpacing(0.2f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.setMargins(dp(24), 0, dp(10), 0);
        faces.addView(label, llp);
        for (int i = 0; i < LockClock.IDS.length; i++) {
            final String id = LockClock.IDS[i];
            TextView chip = text(c, LockClock.LABELS[i], 11, Color.WHITE, Fonts.dot(c));
            chip.setGravity(Gravity.CENTER);
            chip.setLetterSpacing(0.1f);
            chip.setOnClickListener(v -> {
                clockFace = id;
                for (LockClock.Preview pv : previews) pv.setFace(id);
                refresh();
            });
            faceChips[i] = chip;
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, dp(36), 1f);
            fl.setMargins(0, 0, i == LockClock.IDS.length - 1 ? dp(24) : dp(6), 0);
            faces.addView(chip, fl);
        }
        LayoutParams flp = new LayoutParams(-1, dp(40), Gravity.BOTTOM);
        flp.bottomMargin = insetBottom + dp(18 + 52 + 12);
        addView(faces, flp);

        post(() -> {
            pager.scrollTo(focused * page, 0);
            refresh();
        });
        setAlpha(0f);
        animate().alpha(1f).setDuration(180).start();
    }

    /** A full-screen preview of the unit's lock screen: its poster and the lock clock in the chosen face, name below. */
    private FrameLayout card(Context c, Units.Unit u, int index) {
        FrameLayout f = new FrameLayout(c);
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(26));
            }
        });
        final ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(u.ink);
        f.addView(img, new LayoutParams(-1, -1));
        final int w = cardW / 2, h = cardH / 2;
        final int poster = c.getResources().getIdentifier("lock_" + u.id, "drawable", c.getPackageName());
        POOL.execute(() -> {
            Bitmap b = null;
            if (poster != 0) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2; // half size is plenty for a preview
                b = BitmapFactory.decodeResource(c.getResources(), poster, o);
            }
            final Bitmap shown = b != null ? b : Skyline.render(w, h, u, xdark);
            main.post(() -> img.setImageBitmap(shown));
        });

        previews[index] = new LockClock.Preview(c, clockFace, LockClock.colour(u));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, dp(150), Gravity.TOP);
        clp.topMargin = insetTop + dp(70);
        f.addView(previews[index], clp);

        // a scrim at the bottom keeps the name readable over any poster
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Units.Unit.alpha(u.scrim, 0.92f), Units.Unit.alpha(u.scrim, 0f)}));
        f.addView(scrim, new LayoutParams(-1, Math.round(cardH * 0.4f), Gravity.BOTTOM));

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(c, u.name, 28, Color.WHITE, Fonts.serif(c));
        name.setGravity(Gravity.CENTER);
        TextView kanji = text(c, u.kanji, 14, Units.MUTED, Fonts.cond(c));
        kanji.setGravity(Gravity.CENTER);
        kanji.setLetterSpacing(0.2f);
        TextView sub = text(c, u.sub, 10, u.a, Fonts.mono(c));
        sub.setGravity(Gravity.CENTER);
        sub.setLetterSpacing(0.2f);
        TextView state = text(c, u.id.equals(current.id) ? "ACTIVE" : " ", 10, Units.MUTED, Fonts.mono(c));
        state.setGravity(Gravity.CENTER);
        state.setLetterSpacing(0.2f);
        info.addView(name, new LinearLayout.LayoutParams(-1, -2));
        info.addView(kanji, new LinearLayout.LayoutParams(-1, -2));
        info.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        info.addView(state, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        ilp.bottomMargin = insetBottom + dp(18 + 52 + 12 + 40 + 14) - gap / 2;
        f.addView(info, ilp);
        return f;
    }

    private void scrollTo(int idx) {
        pager.smoothScrollTo(idx * page, 0);
        focused = idx;
        refresh();
    }

    private void snap() {
        float pos = pager.getScrollX() / (float) page;
        int idx = flingDir > 0 ? (int) Math.ceil(pos) : flingDir < 0 ? (int) Math.floor(pos) : Math.round(pos);
        flingDir = 0;
        scrollTo(Math.max(0, Math.min(Units.ALL.length - 1, idx)));
    }

    private void refresh() {
        int accent = Units.ALL[focused].a;
        apply.setBackground(pill(accent, 0));
        for (int i = 0; i < faceChips.length; i++) {
            boolean on = LockClock.IDS[i].equals(clockFace);
            faceChips[i].setBackground(pill(on ? accent : 0x99050308, on ? 0 : 0x55FFFFFF));
            faceChips[i].setTextColor(on ? Color.BLACK : Color.WHITE);
        }
    }

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(24));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }


    private TextView text(Context c, String s, float sp, int colour, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    void dismiss(Runnable after) {
        animate().alpha(0f).setDuration(150).withEndAction(() -> {
            ((ViewGroup) getParent()).removeView(this);
            if (after != null) after.run();
        }).start();
    }
}
