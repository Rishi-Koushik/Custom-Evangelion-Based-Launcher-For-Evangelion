package dev.rishi.quickstar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads ADW/Nova style icon packs (appfilter.xml) installed on the phone. */
final class IconPacks {
    static final class Pack {
        String pkg;
        String label;
    }

    private static final class Filter {
        final Map<String, String> byComponent = new HashMap<>();
        final Map<String, String> byPackage = new HashMap<>();
    }

    private static final Map<String, Filter> FILTERS = new HashMap<>();

    private IconPacks() {}

    static List<Pack> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Pack> out = new LinkedHashMap<>();
        for (String action : new String[]{"org.adw.launcher.THEMES", "com.novalauncher.THEME"}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action), 0)) {
                Pack p = new Pack();
                p.pkg = ri.activityInfo.packageName;
                p.label = String.valueOf(ri.loadLabel(pm));
                out.put(p.pkg, p);
            }
        }
        return new ArrayList<>(out.values());
    }

    static void clear() {
        FILTERS.clear();
    }

    static Drawable icon(Context c, String pack, ComponentName cn) {
        Filter f = FILTERS.get(pack);
        if (f == null) {
            f = parse(c, pack);
            FILTERS.put(pack, f);
        }
        String name = f.byComponent.get(cn.flattenToString());
        if (name == null) name = f.byPackage.get(cn.getPackageName());
        if (name == null) return null;
        try {
            Resources r = c.getPackageManager().getResourcesForApplication(pack);
            int id = r.getIdentifier(name, "drawable", pack);
            if (id == 0) id = r.getIdentifier(name, "mipmap", pack);
            return id == 0 ? null : r.getDrawable(id, null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Filter parse(Context c, String pack) {
        Filter f = new Filter();
        try {
            Resources r = c.getPackageManager().getResourcesForApplication(pack);
            XmlPullParser xp;
            int id = r.getIdentifier("appfilter", "xml", pack);
            if (id != 0) {
                xp = r.getXml(id);
            } else {
                InputStream in = r.getAssets().open("appfilter.xml");
                xp = XmlPullParserFactory.newInstance().newPullParser();
                xp.setInput(in, "UTF-8");
            }
            for (int ev = xp.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = xp.next()) {
                if (ev != XmlPullParser.START_TAG || !"item".equals(xp.getName())) continue;
                String comp = xp.getAttributeValue(null, "component");
                String draw = xp.getAttributeValue(null, "drawable");
                if (comp == null || draw == null || !comp.startsWith("ComponentInfo{")) continue;
                ComponentName cn = ComponentName.unflattenFromString(comp.substring(14, comp.length() - 1));
                if (cn == null) continue;
                f.byComponent.put(cn.flattenToString(), draw);
                if (!f.byPackage.containsKey(cn.getPackageName())) f.byPackage.put(cn.getPackageName(), draw);
            }
        } catch (Exception ignored) {
            // unreadable pack: no icons from it
        }
        return f;
    }
}
