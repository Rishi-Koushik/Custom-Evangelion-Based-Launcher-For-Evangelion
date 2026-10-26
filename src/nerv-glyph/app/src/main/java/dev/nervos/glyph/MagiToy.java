package dev.nervos.glyph;

import com.nothing.ketchum.GlyphToy;

/**
 * MAGI: Melchior (top), Balthasar (left) and Casper (right) take turns "thinking" as a breathing pulse.
 * Long press calls the vote: all three lock on and flash, then deliberation resumes.
 */
public class MagiToy extends NervToy {
    private static final long TURN = 700, DECISION = 2600;
    private long decidedAt = -1;

    @Override
    protected void onEvent(String event) {
        if (GlyphToy.EVENT_CHANGE.equals(event)) decidedAt = android.os.SystemClock.uptimeMillis();
    }

    @Override
    protected long draw(int[] px, long now) {
        int[] v = new int[3];
        if (decidedAt >= 0 && now - decidedAt < DECISION) {
            long t = now - decidedAt;
            int on = t < 900 && (t / 150) % 2 == 1 ? 90 : 255; // three quick flashes, then solid
            v[0] = v[1] = v[2] = on;
        } else {
            decidedAt = -1;
            int active = (int) ((now / TURN) % 3);
            float p = (now % TURN) / (float) TURN;
            for (int i = 0; i < 3; i++) v[i] = 45;
            v[active] = 45 + (int) (210 * Math.sin(Math.PI * p));
        }
        Px.triangle(px, 12, 2, 17.5f, 11, 6.5f, 11, v[0]);      // Melchior
        Px.triangle(px, 6.5f, 13, 12, 22, 1, 22, v[1]);          // Balthasar
        Px.triangle(px, 18, 13, 23.5f, 22, 12.5f, 22, v[2]);     // Casper
        return aod ? 0 : 50;
    }
}
