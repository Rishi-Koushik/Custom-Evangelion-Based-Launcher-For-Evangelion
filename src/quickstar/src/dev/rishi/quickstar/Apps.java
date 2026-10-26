package dev.rishi.quickstar;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Launchable app list shared by the tile editor and the launcher. */
final class Apps {
    static final class App {
        String label;
        ComponentName cn;

        String key() {
            return cn.flattenToString();
        }
    }

    interface Pick {
        void on(App app);
    }

    private Apps() {}

    static List<App> list(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(c.getPackageName())) continue;
            App a = new App();
            a.label = String.valueOf(ri.loadLabel(pm));
            a.cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            out.add(a);
        }
        Collections.sort(out, (x, y) -> x.label.toLowerCase(Locale.ROOT).compareTo(y.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    static App find(Context c, String flat) {
        ComponentName cn = ComponentName.unflattenFromString(flat);
        if (cn == null) return null;
        for (App a : list(c)) if (a.cn.equals(cn)) return a;
        return null;
    }

    static void pickDialog(Context c, String title, Pick cb) {
        final List<App> apps = list(c);
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i).label;
        new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setItems(names, (d, which) -> cb.on(apps.get(which)))
                .show();
    }
}
