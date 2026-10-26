package dev.rishi.quickstar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Panel colour: seed colour, theme style, live preview, and font size. */
final class PanelScreen extends LinearLayout {
    private final Preview preview;
    private final ColorPicker palette;
    private final ColorPicker accent;
    private final LinearLayout accentBox;
    private final TextView accentToggle;
    private final List<TextView> styleChips = new ArrayList<>();
    private final TextView previewTag;
    private String style = "TONAL_SPOT";
    private boolean separateAccent;
    private boolean applied;
    private final Handler h = new Handler(Looper.getMainLooper());

    PanelScreen(Context c) {
        super(c);
        setOrientation(VERTICAL);

        String cur = Sys.getSecure(c, Sys.K_THEME);
        int seed = 0xFF00C2A8;
        String pal = PanelTheme.get(cur, "system_palette");
        String acc = PanelTheme.get(cur, "accent_color");
        String st = PanelTheme.get(cur, "theme_style");
        try {
            if (pal != null) seed = 0xFF000000 | Integer.parseInt(pal, 16);
        } catch (NumberFormatException ignored) {
            // keep default seed
        }
        if (st != null) style = st;

        previewTag = Ui.mono(c, "LIVE", 10, Ui.DIM);
        addView(sectionRow(c, "Preview", previewTag));
        preview = new Preview(c);
        addView(preview, Ui.lp(-1, Ui.dp(236), 0, 0, 0, 0));
        preview.colors = PanelTheme.read(c);

        addView(Ui.section(c, "NERV unit"));
        LinearLayout unitRow = new LinearLayout(c);
        final String curUnit = UnitSync.current(c);
        for (final Units.Unit u : Units.ALL) {
            TextView chip = Ui.chip(c, u.shortName(), u.id.equals(curUnit), null);
            chip.setOnClickListener(v -> applyUnit(u, unitRow));
            unitRow.addView(chip, Ui.lp(0, -2, 0, 0, 6, 0));
            ((LayoutParams) chip.getLayoutParams()).weight = 1;
        }
        addView(unitRow);
        addView(Ui.mono(c, "Recolours this panel, the shade, the control centre and NERV Home together.", 11, Ui.DIM),
                Ui.lp(-1, -2, 4, 8, 4, 0));
        final TextView sounds = Ui.chip(c, "NERV sounds", NervSounds.enabled(c), null);
        sounds.setOnClickListener(v -> {
            boolean on = !NervSounds.enabled(c);
            NervSounds.setEnabled(c, on);
            Ui.styleChip(sounds, on);
            Toast.makeText(c, on ? "Unit ringtone, notification and alarm on" : "Your own sounds are back", Toast.LENGTH_SHORT).show();
        });
        addView(sounds, Ui.lp(-2, -2, 0, 12, 0, 0));
        addView(Ui.mono(c, "Each unit's own ringtone, notification and alarm sound. Off puts back the sounds you had.", 11, Ui.DIM),
                Ui.lp(-1, -2, 4, 8, 4, 0));
        final TextView notifs = Ui.chip(c, "NERV notifications", NervNotifs.enabled(c), null);
        notifs.setOnClickListener(v -> {
            boolean on = !NervNotifs.enabled(c);
            NervNotifs.setEnabled(c, on);
            Ui.styleChip(notifs, on);
            if (on && !NervNotifs.listenerGranted(c)) {
                Toast.makeText(c, "Switch on NERV notifications here, then come back", Toast.LENGTH_LONG).show();
                c.startActivity(NervNotifs.grantIntent());
            } else if (on && !Sys.lockHoldEnabled(c)) {
                Toast.makeText(c, "Also switch on NERV lock-screen hold in Accessibility: it draws the banners", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(c, on ? "Notifications now arrive as NERV banners" : "Nothing's own pop-ups are back", Toast.LENGTH_SHORT).show();
            }
        });
        addView(notifs, Ui.lp(-2, -2, 0, 12, 0, 0));
        final TextView serif = Ui.chip(c, "Serif system font", PanelTheme.serifFont(c), null);
        serif.setOnClickListener(v -> {
            boolean on = !PanelTheme.serifFont(c);
            Sys.prefs(c).edit().putBoolean("serif_font", on).apply();
            Ui.styleChip(serif, on);
            boolean saver = c.getSystemService(android.os.PowerManager.class).isPowerSaveMode();
            UnitSync.apply(c, UnitSync.current(c), saver, false); // re-applies the theme with or without the font pack
            Toast.makeText(c, on ? "Serif headlines across the phone" : "Nothing's own fonts are back", Toast.LENGTH_SHORT).show();
        });
        addView(serif, Ui.lp(-2, -2, 0, 12, 0, 0));
        addView(Ui.mono(c, "Pop-up notifications as NERV banners in your unit's colours. Nothing's own pop-ups pause while "
                + "this runs and come back the moment it stops.", 11, Ui.DIM), Ui.lp(-1, -2, 4, 8, 4, 0));

        addView(Ui.section(c, "Panel colour"));
        LinearLayout card = Ui.card(c);
        palette = new ColorPicker(c);
        palette.setColor(seed, false);
        palette.setListener(rgb -> draftChanged());
        card.addView(palette);
        accentToggle = Ui.chip(c, "Separate accent colour", false, v -> toggleAccent());
        card.addView(accentToggle, Ui.lp(-2, -2, 0, 14, 0, 0));
        accentBox = new LinearLayout(c);
        accentBox.setOrientation(VERTICAL);
        accent = new ColorPicker(c);
        accent.setColor(acc != null && !acc.equals(pal) ? parse(acc, seed) : seed, false);
        accentBox.addView(accent, Ui.match(8, 0));
        accentBox.setVisibility(GONE);
        card.addView(accentBox);
        addView(card);

        addView(Ui.section(c, "Theme style"));
        LinearLayout styles = new LinearLayout(c);
        styles.setOrientation(VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < PanelTheme.STYLES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(c);
                styles.addView(row, Ui.match(0, 8));
            }
            final String s = PanelTheme.STYLES[i];
            TextView chip = Ui.chip(c, s.replace('_', ' '), s.equals(style), v -> {
                style = s;
                for (TextView t : styleChips) Ui.styleChip(t, t.getText().toString().equals(s.replace('_', ' ')));
                draftChanged();
            });
            styleChips.add(chip);
            row.addView(chip, Ui.lp(0, -2, 0, 0, i % 2 == 0 ? 8 : 0, 0));
            ((LayoutParams) chip.getLayoutParams()).weight = 1;
        }
        addView(styles);

        // the Dark / Extra dark choice is retired: each unit has one look
        if (false) addView(Ui.section(c, "Mode"));
        final boolean pure = UnitSync.xdark(c);
        final List<TextView> bases = new ArrayList<>();
        final TextView black = Ui.chip(c, "Extra dark", pure, null);
        final TextView soft = Ui.chip(c, "Dark", !pure, null);
        black.setOnClickListener(v -> setBase(true, black, soft));
        soft.setOnClickListener(v -> setBase(false, black, soft));
        bases.add(black);
        bases.add(soft);
        LinearLayout baseRow = new LinearLayout(c);
        baseRow.addView(black, Ui.lp(-2, -2, 0, 0, 8, 0));
        baseRow.addView(soft);
        if (false) addView(baseRow);

        LinearLayout buttons = new LinearLayout(c);
        TextView apply = Ui.button(c, "Apply to panel", true, v -> apply());
        TextView restore = Ui.button(c, "Restore original", false, v -> restore());
        buttons.addView(apply, Ui.lp(0, -2, 0, 0, 8, 0));
        ((LayoutParams) apply.getLayoutParams()).weight = 1;
        buttons.addView(restore, Ui.lp(0, -2, 0, 0, 0, 0));
        ((LayoutParams) restore.getLayoutParams()).weight = 1;
        addView(buttons, Ui.match(20, 0));

        addView(Ui.section(c, "Font size"));
        LinearLayout fcard = Ui.card(c);
        final TextView fval = Ui.mono(c, "", 13, Ui.TEXT);
        GradientBar fbar = new GradientBar(c);
        fbar.setColors(0xFF333333, 0xFFFFFFFF);
        float fs = Settings.System.getFloat(c.getContentResolver(), Settings.System.FONT_SCALE, 1f);
        fbar.setValue((fs - 0.85f) / 0.5f);
        fval.setText(String.format("%.2fx", fs));
        fbar.setListener(v -> {
            float f = Math.round((0.85f + v * 0.5f) * 20f) / 20f;
            fval.setText(String.format("%.2fx", f));
            h.removeCallbacksAndMessages("font");
            h.postAtTime(() -> setFont(f), "font", android.os.SystemClock.uptimeMillis() + 500);
        });
        fcard.addView(fval);
        fcard.addView(fbar);
        addView(fcard);

        draftChanged();
        previewTag.setText("LIVE");
    }

    private void applyUnit(Units.Unit u, LinearLayout row) {
        Context c = getContext();
        if (!Sys.canWriteSecure(c)) {
            Toast.makeText(c, "Grant WRITE_SECURE_SETTINGS first (see setup)", Toast.LENGTH_LONG).show();
            return;
        }
        boolean saver = c.getSystemService(android.os.PowerManager.class).isPowerSaveMode();
        UnitSync.apply(c, u.id, saver, true);
        for (int i = 0; i < row.getChildCount(); i++) {
            Ui.styleChip((TextView) row.getChildAt(i), Units.ALL[i].id.equals(u.id));
        }
        palette.setColor(0xFF000000 | (saver ? u.sv : UnitSync.panelSeed(u)), false);
        style = "VIBRANT";
        for (TextView t : styleChips) Ui.styleChip(t, t.getText().toString().equals("VIBRANT"));
        h.postDelayed(this::refreshLive, 1800);
        Toast.makeText(c, u.shortName() + " " + u.kanji, Toast.LENGTH_SHORT).show();
    }

    private void toggleAccent() {
        separateAccent = !separateAccent;
        Ui.styleChip(accentToggle, separateAccent);
        accentBox.setVisibility(separateAccent ? VISIBLE : GONE);
    }

    private static int parse(String hex, int def) {
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private LinearLayout sectionRow(Context c, String title, TextView tag) {
        LinearLayout r = new LinearLayout(c);
        TextView t = Ui.section(c, title);
        r.addView(t, Ui.lp(0, -2, 0, 0, 0, 0));
        ((LayoutParams) t.getLayoutParams()).weight = 1;
        tag.setPadding(0, Ui.dp(18), Ui.dp(4), 0);
        r.addView(tag);
        return r;
    }

    private void draftChanged() {
        if (preview == null) return;
        applied = false;
        previewTag.setText("DRAFT");
        preview.colors = PanelTheme.draft(palette.getColor(), style);
        preview.invalidate();
    }

    private void apply() {
        Context c = getContext();
        if (!Sys.canWriteSecure(c)) {
            Toast.makeText(c, "Grant WRITE_SECURE_SETTINGS first (see setup)", Toast.LENGTH_LONG).show();
            return;
        }
        Sys.ensureBackup(c);
        String json = PanelTheme.build(c, palette.getHex(), separateAccent ? accent.getHex() : palette.getHex(), style);
        if (Sys.putSecure(c, Sys.K_THEME, json)) {
            applied = true;
            h.postDelayed(this::refreshLive, 1800);
            Toast.makeText(c, "Applied. Pull down the panel to see it.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(c, "Phone rejected the change", Toast.LENGTH_LONG).show();
        }
    }

    private void setBase(boolean pureBlack, TextView black, TextView soft) {
        Context c = getContext();
        if (!Sys.canWriteSecure(c)) {
            Toast.makeText(c, "Grant WRITE_SECURE_SETTINGS first (see setup)", Toast.LENGTH_LONG).show();
            return;
        }
        UnitSync.setMode(c, pureBlack, true);
        Ui.styleChip(black, pureBlack);
        Ui.styleChip(soft, !pureBlack);
        h.postDelayed(this::refreshLive, 1800);
    }

    private void restore() {
        Context c = getContext();
        Sys.putSecure(c, Sys.K_EXTRA_DARK, "1");
        String clock = Sys.prefs(c).getString("bk_clock", null);
        if (clock != null) Sys.putSecure(c, UnitSync.K_CLOCK, clock);
        String orig = Sys.backup(c, "theme");
        if (orig.isEmpty() || !Sys.putSecure(c, Sys.K_THEME, orig)) {
            Toast.makeText(c, "Nothing to restore", Toast.LENGTH_SHORT).show();
            return;
        }
        String pal = PanelTheme.get(orig, "system_palette");
        if (pal != null) palette.setColor(parse(pal, palette.getColor()), false);
        h.postDelayed(this::refreshLive, 1800);
        Toast.makeText(c, "Original theme restored", Toast.LENGTH_SHORT).show();
    }

    private void refreshLive() {
        preview.colors = PanelTheme.read(getContext());
        preview.invalidate();
        previewTag.setText("LIVE");
    }

    private void setFont(float f) {
        Context c = getContext();
        if (!Sys.canWriteSystem(c)) {
            Toast.makeText(c, "Grant WRITE_SETTINGS (see setup)", Toast.LENGTH_LONG).show();
            return;
        }
        Settings.System.putFloat(c.getContentResolver(), Settings.System.FONT_SCALE, f);
    }

    /** Mini quick settings panel painted with either the real or the draft colours. */
    static final class Preview extends View {
        PanelTheme.Colors colors = new PanelTheme.Colors();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Preview(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), gap = Ui.dp(10);
            r.set(0, 0, w, getHeight());
            p.setColor(0xFF000000);
            c.drawRoundRect(r, Ui.dp(22), Ui.dp(22), p);
            float pad = Ui.dp(14);
            float cw = (w - 2 * pad - 3 * gap) / 4f;
            float th = Ui.dp(52);
            float y = pad;
            tile(c, pad, y, 2 * cw + gap, th, true, "Wi-Fi", true);
            tile(c, pad + 2 * (cw + gap), y, 2 * cw + gap, th, false, "Mobile data", true);
            y += th + gap;
            for (int i = 0; i < 4; i++) tile(c, pad + i * (cw + gap), y, cw, cw, i % 2 == 0, "", false);
            y += cw + gap + Ui.dp(6);
            r.set(pad, y, w - pad, y + Ui.dp(40));
            p.setColor(colors.tileOff);
            c.drawRoundRect(r, Ui.dp(20), Ui.dp(20), p);
            r.set(pad, y, pad + (w - 2 * pad) * 0.55f, y + Ui.dp(40));
            p.setColor(colors.tileOn);
            c.drawRoundRect(r, Ui.dp(20), Ui.dp(20), p);
        }

        private void tile(Canvas c, float x, float y, float w, float h, boolean on, String label, boolean pill) {
            r.set(x, y, x + w, y + h);
            p.setColor(on ? colors.tileOn : colors.tileOff);
            c.drawRoundRect(r, h / 2, h / 2, p);
            int fg = on ? colors.labelOn : colors.labelOff;
            p.setColor(fg);
            p.setAlpha(on ? 255 : 200);
            float cx = x + h / 2;
            c.drawCircle(cx, y + h / 2, Ui.dp(7), p);
            if (!label.isEmpty()) {
                p.setTextSize(Ui.dp(13));
                p.setTypeface(Typeface.DEFAULT);
                p.setColor(fg);
                c.drawText(label, x + h + Ui.dp(2), y + h / 2 + Ui.dp(5), p);
            }
            p.setAlpha(255);
        }
    }
}
