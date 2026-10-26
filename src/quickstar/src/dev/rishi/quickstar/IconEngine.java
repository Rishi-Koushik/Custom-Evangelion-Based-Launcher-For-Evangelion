package dev.rishi.quickstar;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Renders app icons in the chosen shape and style: original, monochrome themed, icon pack, or custom. */
final class IconEngine {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(400);

    private IconEngine() {}

    static void clear() {
        CACHE.evictAll();
    }

    static File customFile(Context c, ComponentName cn) {
        File dir = new File(c.getFilesDir(), "icons");
        dir.mkdirs();
        return new File(dir, cn.flattenToShortString().replace('/', '_') + ".png");
    }

    static void saveCustom(Context c, ComponentName cn, Bitmap src) throws IOException {
        Bitmap b = Bitmap.createScaledBitmap(src, 192, 192, true);
        try (FileOutputStream out = new FileOutputStream(customFile(c, cn))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        clear();
    }

    static void resetCustom(Context c, ComponentName cn) {
        customFile(c, cn).delete();
        clear();
    }

    static Bitmap get(Context c, ComponentName cn, int px, LCfg cfg) {
        String key = cn.flattenToString() + "|" + px + "|" + cfg.signature() + "|" + customFile(c, cn).lastModified();
        Bitmap b = CACHE.get(key);
        if (b == null) {
            b = render(c, cn, px, cfg);
            CACHE.put(key, b);
        }
        return b;
    }

    private static Bitmap render(Context c, ComponentName cn, int px, LCfg cfg) {
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Path shape = Shapes.path(cfg.shape, px);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        File cf = customFile(c, cn);
        if (cf.exists()) {
            Bitmap custom = BitmapFactory.decodeFile(cf.getPath());
            if (custom != null) {
                cv.clipPath(shape);
                cv.drawBitmap(custom, null, new android.graphics.Rect(0, 0, px, px), new Paint(Paint.FILTER_BITMAP_FLAG));
                return out;
            }
        }
        if (!cfg.pack.isEmpty()) {
            Drawable d = IconPacks.icon(c, cfg.pack, cn);
            if (d != null) {
                d.setBounds(0, 0, px, px);
                d.draw(cv);
                return out;
            }
        }

        Drawable d;
        try {
            d = c.getPackageManager().getActivityIcon(cn);
        } catch (PackageManager.NameNotFoundException e) {
            d = c.getPackageManager().getDefaultActivityIcon();
        }

        cv.save();
        cv.clipPath(shape);
        if (LCfg.ORIGINAL.equals(cfg.style)) {
            if (d instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
                layer(cv, ad.getBackground(), px, 0);
                layer(cv, ad.getForeground(), px, 0);
            } else {
                fill.setColor(0xFF1A1A1A);
                cv.drawRect(0, 0, px, px, fill);
                inset(cv, d, px, 0.14f, false);
            }
        } else {
            fill.setColor(cfg.bg);
            cv.drawRect(0, 0, px, px, fill);
            Drawable mono = d instanceof AdaptiveIconDrawable ? ((AdaptiveIconDrawable) d).getMonochrome() : null;
            if (mono != null) {
                layer(cv, mono, px, cfg.fg);
            } else {
                inset(cv, d, px, 0.2f, true);
            }
        }
        cv.restore();
        return out;
    }

    /** Draws one adaptive-icon layer: layers are 108dp wide with the visible 72dp in the middle. */
    private static void layer(Canvas cv, Drawable layer, int px, int tint) {
        if (layer == null) return;
        Drawable.ConstantState st = layer.getConstantState();
        Drawable d = st != null ? st.newDrawable().mutate() : layer.mutate();
        if (tint != 0) d.setTint(tint);
        int pad = Math.round(px * 0.25f);
        d.setBounds(-pad, -pad, px + pad, px + pad);
        d.draw(cv);
    }

    private static void inset(Canvas cv, Drawable d, int px, float frac, boolean gray) {
        Bitmap src = toBitmap(d, px);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (gray) {
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(0f);
            p.setColorFilter(new ColorMatrixColorFilter(m));
        }
        int pad = Math.round(px * frac);
        cv.drawBitmap(src, null, new android.graphics.Rect(pad, pad, px - pad, px - pad), p);
    }

    private static Bitmap toBitmap(Drawable d, int px) {
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) return ((BitmapDrawable) d).getBitmap();
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, px, px);
        d.draw(c);
        return b;
    }
}
