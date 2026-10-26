package dev.nervos.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Installed apps and their NERV icons (same mapping as the NERV Icons pack). */
final class Apps {
    static final class Entry {
        final ComponentName component;
        final String label;

        Entry(ComponentName component, String label) {
            this.component = component;
            this.label = label;
        }
    }

    private final Context context;
    private final Map<String, String> filter = new HashMap<>(); // "pkg/cls" -> drawable name
    private final List<Entry> all = new ArrayList<>();

    Apps(Context context) {
        this.context = context;
        try (XmlResourceParser p = context.getResources().getXml(R.xml.appfilter)) {
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
                if (e != XmlPullParser.START_TAG || !"item".equals(p.getName())) continue;
                String comp = p.getAttributeValue(null, "component"), drawable = p.getAttributeValue(null, "drawable");
                if (comp != null && drawable != null)
                    filter.put(comp.replace("ComponentInfo{", "").replace("}", ""), drawable);
            }
        } catch (Exception ignored) {
            // no mapping: every app gets the octagon frame
        }
        reload();
    }

    void reload() {
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        all.clear();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (context.getPackageName().equals(ri.activityInfo.packageName)) continue;
            all.add(new Entry(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), ri.loadLabel(pm).toString()));
        }
        all.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    List<Entry> all() { return all; }

    Entry find(String pkg) {
        for (Entry e : all) if (e.component.getPackageName().equals(pkg)) return e;
        return null;
    }

    List<Entry> filtered(String query) {
        if (query == null || query.isEmpty()) return all;
        String q = query.toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) if (e.label.toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        return out;
    }

    Drawable icon(Entry e, boolean saver, Units.Unit u) {
        String name = filter.get(e.component.getPackageName() + "/" + e.component.getClassName());
        if (name != null) {
            String base = saver ? name.replace("nerv_", "nerv_line_") : name;
            int id = context.getResources().getIdentifier(base + u.suffix, "drawable", context.getPackageName());
            if (id == 0) id = context.getResources().getIdentifier(base, "drawable", context.getPackageName());
            if (id != 0) return context.getDrawable(id);
        }
        // no NERV icon: the app's own icon inside the octagon frame
        Drawable own;
        try {
            own = context.getPackageManager().getActivityIcon(e.component).mutate();
        } catch (PackageManager.NameNotFoundException ex) {
            own = context.getPackageManager().getDefaultActivityIcon();
        }
        if (saver) {
            own.setColorFilter(new BlendModeColorFilter(u.sv, BlendMode.SRC_IN));
            return new OctTile(0xFF000000, u.sv, 0.6f, own);
        }
        return new OctTile(u.tile, 0, 0.66f, own);
    }
}
