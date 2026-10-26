package dev.nervos.glyph;

/** Tiny rasteriser for the 25x25 Glyph Matrix: brightness 0-255 per LED, row-major, brightest value wins. */
final class Px {
    static final int N = 25;
    static final float C = (N - 1) / 2f; // matrix centre

    private Px() {}

    static void plot(int[] px, int x, int y, int v) {
        if (x < 0 || y < 0 || x >= N || y >= N) return;
        int i = y * N + x;
        if (v > px[i]) px[i] = Math.min(255, v);
    }

    static void line(int[] px, float x0, float y0, float x1, float y1, int v) {
        int steps = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))) + 1;
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps;
            plot(px, Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t), v);
        }
    }

    /** Regular octagon outline with flat top, radius r from the matrix centre. */
    static void octagon(int[] px, float r, int v) {
        for (int k = 0; k < 8; k++) {
            double a0 = Math.PI / 8 + k * Math.PI / 4, a1 = a0 + Math.PI / 4;
            line(px, C + (float) (r * Math.cos(a0)), C + (float) (r * Math.sin(a0)),
                    C + (float) (r * Math.cos(a1)), C + (float) (r * Math.sin(a1)), v);
        }
    }

    /** Filled triangle via edge tests on pixel centres. */
    static void triangle(int[] px, float ax, float ay, float bx, float by, float cx, float cy, int v) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float d1 = edge(x, y, ax, ay, bx, by), d2 = edge(x, y, bx, by, cx, cy), d3 = edge(x, y, cx, cy, ax, ay);
            boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
            if (!(neg && pos)) plot(px, x, y, v);
        }
    }

    private static float edge(float px, float py, float ax, float ay, float bx, float by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

    /** Arc on a circle of radius r, clockwise from 12 o'clock, covering fraction (0-1). */
    static void arc(int[] px, float r, float fraction, int v) {
        int steps = (int) (2 * Math.PI * r * 2 * fraction);
        for (int s = 0; s <= steps; s++) {
            double a = -Math.PI / 2 + 2 * Math.PI * fraction * s / Math.max(1, steps);
            plot(px, Math.round(C + (float) (r * Math.cos(a))), Math.round(C + (float) (r * Math.sin(a))), v);
        }
    }

    // 3x5 digits, one string per row
    private static final String[][] DIGITS = {
            {"111", "101", "101", "101", "111"}, {"010", "110", "010", "010", "111"},
            {"111", "001", "111", "100", "111"}, {"111", "001", "111", "001", "111"},
            {"101", "101", "111", "001", "001"}, {"111", "100", "111", "001", "111"},
            {"111", "100", "111", "101", "111"}, {"111", "001", "001", "001", "001"},
            {"111", "101", "111", "101", "111"}, {"111", "101", "111", "001", "111"},
    };

    // 3x5 capitals, just the ones the toys spell
    private static final String LETTERS = "ABEGLNOPRTU";
    private static final String[][] GLYPHS = {
            {"010", "101", "111", "101", "101"}, {"110", "101", "110", "101", "110"}, {"111", "100", "110", "100", "111"},
            {"011", "100", "101", "101", "011"}, {"100", "100", "100", "100", "111"}, {"101", "111", "111", "111", "101"},
            {"010", "101", "101", "101", "010"}, {"110", "101", "110", "100", "100"}, {"110", "101", "110", "101", "101"},
            {"111", "010", "010", "010", "010"}, {"101", "101", "101", "101", "111"},
    };

    /** Width in LEDs of text drawn with {@link #text}: 4 per character (3 + a gap). */
    static int textWidth(String s) {
        return s.length() * 4 - 1;
    }

    /** Capitals, digits and spaces from column x, top at row y; columns outside the matrix are clipped. */
    static void text(int[] px, String s, int x, int y, int v) {
        for (char ch : s.toCharArray()) {
            String[] g = ch >= '0' && ch <= '9' ? DIGITS[ch - '0'] : LETTERS.indexOf(ch) >= 0 ? GLYPHS[LETTERS.indexOf(ch)] : null;
            if (g != null) {
                for (int r = 0; r < 5; r++) for (int c = 0; c < 3; c++) if (g[r].charAt(c) == '1') plot(px, x + c, y + r, v);
            }
            x += 4;
        }
    }

    /** One digit at column x, row y, scaled s x s. */
    static void digit(int[] px, int d, int x, int y, int s, int v) {
        String[] g = DIGITS[d];
        for (int r = 0; r < 5; r++) for (int c = 0; c < 3; c++) {
            if (g[r].charAt(c) != '1') continue;
            for (int dy = 0; dy < s; dy++) for (int dx = 0; dx < s; dx++) plot(px, x + c * s + dx, y + r * s + dy, v);
        }
    }

    /** Draws a number centred horizontally with its top at row y, each font pixel scaled to s x s LEDs. */
    static void number(int[] px, String digits, int y, int s, int v) {
        int w = digits.length() * 3 * s + (digits.length() - 1) * s;
        int x = Math.round(C - (w - 1) / 2f);
        for (char ch : digits.toCharArray()) {
            String[] g = DIGITS[ch - '0'];
            for (int r = 0; r < 5; r++) for (int c = 0; c < 3; c++) {
                if (g[r].charAt(c) != '1') continue;
                for (int dy = 0; dy < s; dy++) for (int dx = 0; dx < s; dx++) plot(px, x + c * s + dx, y + r * s + dy, v);
            }
            x += 4 * s;
        }
    }
}
