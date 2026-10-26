package dev.nervos.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A still NERV skyline for one unit. Picking it in the system's wallpaper customiser (hold the lock screen >
 * Customise) makes that unit the active theme: NERV Home, the icons and, through Quickstar, the system panel follow.
 * It draws once per surface and never animates, so it costs nothing while idle.
 */
public abstract class UnitWallpaper extends WallpaperService {
    abstract String unitId();

    @Override
    public Engine onCreateEngine() {
        return new SkylineEngine();
    }

    private final class SkylineEngine extends Engine {
        private final Handler main = new Handler(Looper.getMainLooper());
        private final ExecutorService pool = Executors.newSingleThreadExecutor();
        private Bitmap bmp;
        private int width, height;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(false);
            // the preview inside the picker is not a choice yet; the real wallpaper is
            if (!isPreview()) activate();
        }

        private void activate() {
            Units.Unit u = Units.byId(unitId());
            if (UnitStore.current(UnitWallpaper.this).id.equals(u.id)) return;
            UnitStore.save(UnitWallpaper.this, u.id);
            UnitStore.notifyQuickstar(UnitWallpaper.this, u, UnitStore.xdark(UnitWallpaper.this),
                    getSystemService(PowerManager.class).isPowerSaveMode());
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            if (w == width && h == height && bmp != null) {
                draw();
                return;
            }
            width = w;
            height = h;
            final Units.Unit u = Units.byId(unitId());
            pool.execute(() -> {
                final Bitmap b = Skyline.render(w, h, u, UnitStore.xdark(UnitWallpaper.this));
                main.post(() -> {
                    bmp = b;
                    draw();
                });
            });
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) draw();
        }

        private void draw() {
            if (bmp == null) return;
            SurfaceHolder h = getSurfaceHolder();
            Canvas c = null;
            try {
                c = h.lockCanvas();
                if (c != null) c.drawBitmap(bmp, 0, 0, null);
            } catch (IllegalStateException | IllegalArgumentException ignored) {
                // surface went away mid-draw
            } finally {
                if (c != null) h.unlockCanvasAndPost(c);
            }
        }

        @Override
        public void onDestroy() {
            pool.shutdownNow();
            bmp = null;
            super.onDestroy();
        }
    }
}
