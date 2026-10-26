package dev.nervos.glyph;

import com.nothing.ketchum.GlyphToy;

/** A.T. Field: octagon rings expand from the centre and fade. Long press holds a steady layered field. */
public class AtFieldToy extends NervToy {
    private boolean hold;

    @Override
    protected void onEvent(String event) {
        if (GlyphToy.EVENT_CHANGE.equals(event)) hold = !hold;
    }

    @Override
    protected long draw(int[] px, long now) {
        if (hold) {
            for (int r = 3; r <= 12; r += 3) Px.octagon(px, r, 255 - (r - 3) * 18);
            return 0;
        }
        Frames.atField(px, now);
        return 66; // ~15 fps is smooth on the matrix
    }
}
