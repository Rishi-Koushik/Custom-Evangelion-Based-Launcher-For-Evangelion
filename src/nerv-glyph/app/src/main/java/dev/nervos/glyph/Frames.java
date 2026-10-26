package dev.nervos.glyph;

import android.content.Context;
import android.graphics.Bitmap;

import com.nothing.ketchum.GlyphMatrixFrame;
import com.nothing.ketchum.GlyphMatrixObject;

/** Turns a 25x25 brightness buffer into Glyph Matrix frame data. Reuses its bitmap between frames. */
final class Frames {
    private final int[] argb = new int[Px.N * Px.N];
    private final Bitmap bitmap = Bitmap.createBitmap(Px.N, Px.N, Bitmap.Config.ARGB_8888);

    int[] render(Context context, int[] px) {
        for (int i = 0; i < px.length; i++) argb[i] = 0xFF000000 | px[i] << 16 | px[i] << 8 | px[i];
        bitmap.setPixels(argb, 0, Px.N, 0, 0, Px.N, Px.N);
        GlyphMatrixObject image = new GlyphMatrixObject.Builder().setImageSource(bitmap).setPosition(0, 0).build();
        return new GlyphMatrixFrame.Builder().addTop(image).build(context).render();
    }

    /** A.T. Field: three octagon rings expanding from the centre and fading. Shared by the toy and the charger. */
    static void atField(int[] px, long now) {
        final long period = 1500;
        for (int i = 0; i < 3; i++) {
            float phase = ((now + i * period / 3) % period) / (float) period;
            Px.octagon(px, 1 + phase * 11.5f, (int) (255 * Math.pow(1 - phase, 1.4)));
        }
    }
}
