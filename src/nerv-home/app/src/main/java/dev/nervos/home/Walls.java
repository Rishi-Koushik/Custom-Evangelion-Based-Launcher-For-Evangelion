package dev.nervos.home;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.view.WindowManager;

import java.io.IOException;

/**
 * Sets a unit's wallpapers: the Tokyo-3 skyline on the home screen, and the unit's poster (res/drawable-nodpi/lock_*,
 * made by nerv-os/tools/gen_lockscreens.py) on the lock screen. Without a poster the skyline goes on both.
 */
final class Walls {
    private Walls() {}

    /** Blocking; call off the main thread. */
    static boolean apply(Context c, Units.Unit u, boolean xdark) {
        Rect r = c.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        WallpaperManager wm = WallpaperManager.getInstance(c);
        try {
            Bitmap home = Skyline.render(r.width(), r.height(), u, xdark);
            Bitmap lock = poster(c, u, r.width(), r.height());
            if (lock == null) {
                wm.setBitmap(home, null, true, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
            } else {
                wm.setBitmap(home, null, true, WallpaperManager.FLAG_SYSTEM);
                // the crop hint says "show exactly this": without it Nothing OS scales the poster up to its 2800 x 2800
                // wallpaper square and the lock screen comes out zoomed in about 2x
                wm.setBitmap(lock, new Rect(0, 0, lock.getWidth(), lock.getHeight()), true, WallpaperManager.FLAG_LOCK);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** The unit's lock-screen poster, centre-cropped to the screen's shape and scaled to fill it; null if it has none. */
    private static Bitmap poster(Context c, Units.Unit u, int w, int h) {
        int id = c.getResources().getIdentifier("lock_" + u.id, "drawable", c.getPackageName());
        if (id == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap src = BitmapFactory.decodeResource(c.getResources(), id, o);
        if (src == null) return null;
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        Bitmap crop = Bitmap.createBitmap(src, (src.getWidth() - cw) / 2, (src.getHeight() - ch) / 2, cw, ch);
        return cw == w && ch == h ? crop : Bitmap.createScaledBitmap(crop, w, h, true);
    }
}
