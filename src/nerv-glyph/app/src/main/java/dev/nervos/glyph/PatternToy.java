package dev.nervos.glyph;

import com.nothing.ketchum.GlyphToy;

/**
 * Pattern Blue: the Angel alert. A double octagon frame flashes while the pattern name scrolls across the middle.
 * Long press switches between PATTERN BLUE (confirmed: fast, hard flash) and PATTERN ORANGE (unidentified: slow pulse).
 */
public class PatternToy extends NervToy {
    private boolean orange;

    @Override
    protected void onEvent(String event) {
        if (GlyphToy.EVENT_CHANGE.equals(event)) orange = !orange;
    }

    @Override
    protected long draw(int[] px, long now) {
        String msg = orange ? "PATTERN ORANGE" : "PATTERN BLUE";
        if (orange) {
            // slow breathing frame
            float wave = 0.5f + 0.5f * (float) Math.sin(now / 700.0);
            Px.octagon(px, 11.8f, Math.round(60 + 195 * wave));
            Px.octagon(px, 10.3f, Math.round(30 * wave));
        } else {
            boolean lit = (now / 350) % 2 == 0;
            Px.octagon(px, 11.8f, lit ? 255 : 40);
            Px.octagon(px, 10.3f, lit ? 150 : 0);
        }
        // scroll right to left through the window inside the frame, one LED every 70 ms
        int span = Px.textWidth(msg) + 25;
        int x = 24 - (int) ((now / 70) % span);
        Px.text(px, msg, x, 10, 255);
        return 70;
    }
}
