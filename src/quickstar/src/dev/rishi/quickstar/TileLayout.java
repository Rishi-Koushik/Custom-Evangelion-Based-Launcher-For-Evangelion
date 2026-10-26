package dev.rishi.quickstar;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model of Nothing OS's quick settings grid. The phone stores one JSON object keyed by tile spec with
 * cell position, span and page; positions are fully determined by order + span via first-fit packing, so
 * we edit an ordered list and re-pack it on save.
 */
final class TileLayout {
    static final int W = 4;
    static final int H = 4;
    static final String CONTROL_SPEC = "custom(dev.rishi.quickstar/.ControlTile)";
    static final String THEME_SPEC = "custom(dev.nervos.home/.ThemeTile)";
    static final String[][] SIZES = {{"1x1", "1", "1"}, {"2x1", "2", "1"}, {"2x2", "2", "2"}};

    static final class Tile {
        String spec;
        int sx = 1;
        int sy = 1;

        Tile(String spec, int sx, int sy) {
            this.spec = spec;
            this.sx = sx;
            this.sy = sy;
        }
    }

    /** Placement of one tile after packing: page (1-based), column, row. */
    static final class Pos {
        int page;
        int x;
        int y;
    }

    private TileLayout() {}

    static List<Tile> parse(String nt, String list) {
        final Map<String, int[]> info = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        try {
            if (nt != null && nt.startsWith("{")) {
                JSONObject o = new JSONObject(nt);
                java.util.Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject t = o.getJSONObject(k);
                    info.put(k, new int[]{t.optInt("screenId", 1), t.optInt("cellY"), t.optInt("cellX"),
                            Math.max(1, t.optInt("spanX", 1)), Math.max(1, t.optInt("spanY", 1))});
                    keys.add(k);
                }
            }
        } catch (JSONException ignored) {
            info.clear();
            keys.clear();
        }
        Collections.sort(keys, (a, b) -> {
            int[] x = info.get(a), y = info.get(b);
            for (int i = 0; i < 3; i++) if (x[i] != y[i]) return x[i] - y[i];
            return 0;
        });
        List<Tile> out = new ArrayList<>();
        for (String k : keys) out.add(new Tile(k, info.get(k)[3], info.get(k)[4]));
        if (list != null && !list.isEmpty()) {
            for (String s : list.split(",")) {
                if (s.isEmpty() || info.containsKey(s)) continue;
                out.add(new Tile(s, 1, 1));
            }
        }
        return out;
    }

    static List<Pos> pack(List<Tile> tiles) {
        List<boolean[][]> pages = new ArrayList<>();
        List<Pos> out = new ArrayList<>();
        for (Tile t : tiles) {
            int sx = Math.min(t.sx, W), sy = Math.min(t.sy, H);
            Pos found = null;
            for (int p = 0; found == null; p++) {
                if (p == pages.size()) pages.add(new boolean[H][W]);
                boolean[][] occ = pages.get(p);
                for (int y = 0; y + sy <= H && found == null; y++) {
                    for (int x = 0; x + sx <= W && found == null; x++) {
                        if (free(occ, x, y, sx, sy)) {
                            for (int j = y; j < y + sy; j++) for (int i = x; i < x + sx; i++) occ[j][i] = true;
                            found = new Pos();
                            found.page = p + 1;
                            found.x = x;
                            found.y = y;
                        }
                    }
                }
            }
            out.add(found);
        }
        return out;
    }

    private static boolean free(boolean[][] occ, int x, int y, int sx, int sy) {
        for (int j = y; j < y + sy; j++) for (int i = x; i < x + sx; i++) if (occ[j][i]) return false;
        return true;
    }

    static String toNt(List<Tile> tiles) {
        List<Pos> pos = pack(tiles);
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i < tiles.size(); i++) {
                JSONObject t = new JSONObject();
                t.put("cellX", pos.get(i).x);
                t.put("cellY", pos.get(i).y);
                t.put("spanX", tiles.get(i).sx);
                t.put("spanY", tiles.get(i).sy);
                t.put("screenId", pos.get(i).page);
                o.put(tiles.get(i).spec, t);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return o.toString();
    }

    static String toList(List<Tile> tiles) {
        StringBuilder sb = new StringBuilder();
        for (Tile t : tiles) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t.spec);
        }
        return sb.toString();
    }

    static boolean save(Context c, List<Tile> tiles) {
        Sys.ensureBackup(c);
        boolean a = Sys.putSecure(c, Sys.K_TILES, toList(tiles));
        boolean b = Sys.putSecure(c, Sys.K_NT_TILES, toNt(tiles));
        return a && b;
    }

    static List<Tile> load(Context c) {
        return parse(Sys.getSecure(c, Sys.K_NT_TILES), Sys.getSecure(c, Sys.K_TILES));
    }

    /** Appends a 1x1 tile to the saved layout if it is not already there. */
    static boolean addSpec(Context c, String spec) {
        List<Tile> tiles = load(c);
        for (Tile t : tiles) if (t.spec.equals(spec)) return true;
        tiles.add(new Tile(spec, 1, 1));
        return save(c, tiles);
    }

    /** spec -> human title, from the phone's own tile catalogue plus a few fallbacks. */
    static Map<String, String> titles(Context c) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            JSONArray a = new JSONArray(Sys.getSecure(c, Sys.K_SEARCH));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                m.put(o.getString("spec"), o.optString("title", o.getString("spec")));
            }
        } catch (JSONException ignored) {
            // catalogue missing: titles fall back to the spec name
        }
        return m;
    }

    static String title(Context c, Map<String, String> titles, String spec) {
        String t = titles.get(spec);
        if (t != null) return t;
        if (spec.equals(CONTROL_SPEC)) return "Control centre";
        if (spec.equals(THEME_SPEC)) return "NERV theme";
        if (spec.startsWith("custom(")) {
            for (int i = 1; i <= 4; i++) {
                if (spec.equals(customSpec(i))) {
                    String l = Slots.get(c, i).label;
                    return l.isEmpty() ? "Quickstar " + i : l;
                }
            }
            String s = spec.substring(7, spec.length() - 1);
            s = s.substring(s.lastIndexOf('.') + 1).replace("TileService", "").replace("Tile", "");
            return s.isEmpty() ? spec : s;
        }
        return spec.substring(0, 1).toUpperCase() + spec.substring(1);
    }

    static String customSpec(int slot) {
        return "custom(dev.rishi.quickstar/.Tile" + slot + ")";
    }
}
