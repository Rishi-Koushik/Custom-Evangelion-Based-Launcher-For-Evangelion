package dev.rishi.quickstar;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Edits Nothing's quick settings tile grid: order, size, add and remove. */
final class LayoutScreen extends LinearLayout {
    private List<TileLayout.Tile> tiles;
    private Map<String, String> titles;
    private int selected = -1;
    private final Pages pages;
    private final TextView selTitle;
    private final LinearLayout sizeRow;
    private final List<TextView> sizeChips = new ArrayList<>();
    private final LinearLayout actions;

    LayoutScreen(Context c) {
        super(c);
        setOrientation(VERTICAL);
        titles = TileLayout.titles(c);
        tiles = TileLayout.load(c);

        addView(Ui.section(c, "Tile grid"));
        pages = new Pages(c);
        addView(pages, Ui.match(0, 0));

        addView(Ui.section(c, "Selected tile"));
        LinearLayout card = Ui.card(c);
        selTitle = Ui.mono(c, "Tap a tile above", 14, Ui.TEXT);
        card.addView(selTitle);
        sizeRow = new LinearLayout(c);
        for (String[] sz : TileLayout.SIZES) {
            final int sx = Integer.parseInt(sz[1]), sy = Integer.parseInt(sz[2]);
            TextView chip = Ui.chip(c, sz[0], false, v -> {
                if (selected < 0) return;
                tiles.get(selected).sx = sx;
                tiles.get(selected).sy = sy;
                refresh();
            });
            sizeChips.add(chip);
            sizeRow.addView(chip, Ui.lp(-2, -2, 0, 0, 8, 0));
        }
        card.addView(sizeRow, Ui.match(12, 0));
        actions = new LinearLayout(c);
        actions.addView(Ui.button(c, "Earlier", false, v -> move(-1)), Ui.lp(-2, -2, 0, 0, 8, 0));
        actions.addView(Ui.button(c, "Later", false, v -> move(1)), Ui.lp(-2, -2, 0, 0, 8, 0));
        actions.addView(Ui.button(c, "Remove", false, v -> remove()), Ui.lp(-2, -2, 0, 0, 0, 0));
        card.addView(actions, Ui.match(12, 0));
        addView(card);

        LinearLayout row = new LinearLayout(c);
        TextView add = Ui.button(c, "Add tile", false, v -> addDialog());
        TextView save = Ui.button(c, "Apply to panel", true, v -> save());
        row.addView(add, Ui.lp(0, -2, 0, 0, 8, 0));
        ((LayoutParams) add.getLayoutParams()).weight = 1;
        row.addView(save, Ui.lp(0, -2, 0, 0, 0, 0));
        ((LayoutParams) save.getLayoutParams()).weight = 1;
        addView(row, Ui.match(18, 0));

        LinearLayout row2 = new LinearLayout(c);
        TextView reload = Ui.button(c, "Reload from phone", false, v -> {
            tiles = TileLayout.load(getContext());
            selected = -1;
            refresh();
        });
        TextView restore = Ui.button(c, "Restore original", false, v -> restore());
        row2.addView(reload, Ui.lp(0, -2, 0, 0, 8, 0));
        ((LayoutParams) reload.getLayoutParams()).weight = 1;
        row2.addView(restore, Ui.lp(0, -2, 0, 0, 0, 0));
        ((LayoutParams) restore.getLayoutParams()).weight = 1;
        addView(row2, Ui.match(10, 0));

        addView(Ui.mono(c, "The panel re-reads the grid when you apply; the screen may flicker for a second.", 11, Ui.DIM),
                Ui.lp(-1, -2, 4, 14, 4, 0));
        refresh();
    }

    private void refresh() {
        pages.positions = TileLayout.pack(tiles);
        pages.requestLayout();
        pages.invalidate();
        boolean has = selected >= 0 && selected < tiles.size();
        selTitle.setText(has ? name(selected) : "Tap a tile above");
        sizeRow.setVisibility(has ? VISIBLE : GONE);
        actions.setVisibility(has ? VISIBLE : GONE);
        if (has) {
            TileLayout.Tile t = tiles.get(selected);
            for (int i = 0; i < sizeChips.size(); i++) {
                String[] sz = TileLayout.SIZES[i];
                Ui.styleChip(sizeChips.get(i), t.sx == Integer.parseInt(sz[1]) && t.sy == Integer.parseInt(sz[2]));
            }
        }
    }

    private String name(int i) {
        return TileLayout.title(getContext(), titles, tiles.get(i).spec);
    }

    private void move(int d) {
        int j = selected + d;
        if (selected < 0 || j < 0 || j >= tiles.size()) return;
        Collections.swap(tiles, selected, j);
        selected = j;
        refresh();
    }

    private void remove() {
        if (selected < 0) return;
        tiles.remove(selected);
        selected = -1;
        refresh();
    }

    private void addDialog() {
        final List<String> specs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        outer:
        for (String spec : availableSpecs()) {
            for (TileLayout.Tile t : tiles) if (t.spec.equals(spec)) continue outer;
            specs.add(spec);
            names.add(TileLayout.title(getContext(), titles, spec));
        }
        if (specs.isEmpty()) {
            Toast.makeText(getContext(), "Every known tile is already on the panel", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add tile")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    tiles.add(new TileLayout.Tile(specs.get(which), 1, 1));
                    selected = tiles.size() - 1;
                    refresh();
                })
                .show();
    }

    private List<String> availableSpecs() {
        List<String> out = new ArrayList<>(titles.keySet());
        out.add(TileLayout.CONTROL_SPEC);
        out.add(TileLayout.THEME_SPEC);
        for (int i = 1; i <= 4; i++) out.add(TileLayout.customSpec(i));
        return out;
    }

    private void save() {
        Context c = getContext();
        if (!Sys.canWriteSecure(c)) {
            Toast.makeText(c, "Grant WRITE_SECURE_SETTINGS first (see setup)", Toast.LENGTH_LONG).show();
            return;
        }
        if (TileLayout.save(c, tiles)) {
            Sys.reloadSystemUi(c);
            Toast.makeText(c, "Layout applied", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(c, "Phone rejected the change", Toast.LENGTH_LONG).show();
        }
    }

    private void restore() {
        Context c = getContext();
        String nt = Sys.backup(c, "nt"), list = Sys.backup(c, "tiles");
        if (nt.isEmpty() && list.isEmpty()) {
            Toast.makeText(c, "Nothing to restore", Toast.LENGTH_SHORT).show();
            return;
        }
        Sys.putSecure(c, Sys.K_TILES, list);
        Sys.putSecure(c, Sys.K_NT_TILES, nt);
        Sys.reloadSystemUi(c);
        tiles = TileLayout.load(c);
        selected = -1;
        refresh();
        Toast.makeText(c, "Original layout restored", Toast.LENGTH_SHORT).show();
    }

    /** Draws every page as a 4x4 grid and reports taps on tiles. */
    private final class Pages extends View {
        List<TileLayout.Pos> positions = new ArrayList<>();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final List<RectF> hit = new ArrayList<>();

        Pages(Context c) {
            super(c);
        }

        private float cell() {
            return (getWidth() - 3 * Ui.dp(8)) / 4f;
        }

        private float cellH(float cell) {
            return cell * 0.78f;
        }

        private int pageCount() {
            int n = 1;
            for (TileLayout.Pos q : positions) n = Math.max(n, q.page);
            return n;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = resolveSize(Ui.dp(300), wSpec);
            float gap = Ui.dp(8);
            float cell = (w - 3 * gap) / 4f;
            float ph = TileLayout.H * cellH(cell) + (TileLayout.H - 1) * gap;
            setMeasuredDimension(w, (int) (pageCount() * (ph + Ui.dp(34))));
        }

        @Override
        protected void onDraw(Canvas c) {
            float gap = Ui.dp(8), cell = cell(), ch = cellH(cell);
            float ph = TileLayout.H * ch + (TileLayout.H - 1) * gap;
            hit.clear();
            for (int pg = 1; pg <= pageCount(); pg++) {
                float top = (pg - 1) * (ph + Ui.dp(34));
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.DIM);
                p.setTypeface(Fonts.mono(getContext()));
                p.setTextSize(Ui.dp(11));
                c.drawText("PAGE " + pg, Ui.dp(4), top + Ui.dp(16), p);
                top += Ui.dp(26);
                for (int y = 0; y < TileLayout.H; y++) {
                    for (int x = 0; x < TileLayout.W; x++) {
                        r.set(x * (cell + gap), top + y * (ch + gap), x * (cell + gap) + cell, top + y * (ch + gap) + ch);
                        p.setColor(0xFF0B0B0B);
                        c.drawRoundRect(r, ch / 2, ch / 2, p);
                    }
                }
            }
            for (int i = 0; i < tiles.size() && i < positions.size(); i++) {
                TileLayout.Tile t = tiles.get(i);
                TileLayout.Pos q = positions.get(i);
                float top = (q.page - 1) * (ph + Ui.dp(34)) + Ui.dp(26);
                float l = q.x * (cell + gap), tp = top + q.y * (ch + gap);
                RectF rr = new RectF(l, tp, l + t.sx * cell + (t.sx - 1) * gap, tp + t.sy * ch + (t.sy - 1) * gap);
                hit.add(rr);
                boolean sel = i == selected;
                p.setStyle(Paint.Style.FILL);
                p.setColor(sel ? 0xFF3A1214 : 0xFF1E1E1E);
                float rad = Math.min(ch / 2, Math.min(rr.width(), rr.height()) / 2);
                c.drawRoundRect(rr, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(sel ? 2 : 1));
                p.setColor(sel ? Ui.RED : Ui.STROKE);
                c.drawRoundRect(rr, rad, rad, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.TEXT);
                p.setTextSize(Ui.dp(t.sx == 1 ? 9 : 12));
                p.setTypeface(Typeface.DEFAULT);
                String label = name(i);
                float maxw = rr.width() - Ui.dp(10);
                while (p.measureText(label) > maxw && label.length() > 2) label = label.substring(0, label.length() - 2) + "…";
                c.drawText(label, rr.centerX() - p.measureText(label) / 2, rr.centerY() + Ui.dp(4), p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                for (int i = 0; i < hit.size(); i++) {
                    if (hit.get(i).contains(e.getX(), e.getY())) {
                        selected = selected == i ? -1 : i;
                        refresh();
                        break;
                    }
                }
            }
            return true;
        }
    }
}
