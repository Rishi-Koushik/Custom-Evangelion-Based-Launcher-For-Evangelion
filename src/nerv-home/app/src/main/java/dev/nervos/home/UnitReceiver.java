package dev.nervos.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Quickstar can change the unit too (from its own Panel tab); this keeps the home screen and wallpaper in step. */
public class UnitReceiver extends BroadcastReceiver {
    private static final String QUICKSTAR = "dev.rishi.quickstar";

    @Override
    public void onReceive(Context c, Intent i) {
        if (!"dev.nervos.home.SET_UNIT".equals(i.getAction()) || !fromQuickstar(c)) return;
        String id = i.getStringExtra("unit");
        if (id == null) return;
        Units.Unit u = Units.byId(id);
        boolean xd = i.getBooleanExtra("xdark", UnitStore.xdark(c));
        if (u.id.equals(UnitStore.current(c).id) && xd == UnitStore.xdark(c)) return;
        UnitStore.save(c, u.id);
        UnitStore.saveMode(c, xd);
        UnitStore.notifyGlyph(c, u);
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Walls.apply(c, u, xd);
            } finally {
                pending.finish();
            }
        }).start();
    }

    /** Only Quickstar's own uid may switch themes: the system tells us who sent the broadcast. */
    private boolean fromQuickstar(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 34) return false;
        int uid = getSentFromUid();
        if (uid < 0) return false;
        String[] pkgs = c.getPackageManager().getPackagesForUid(uid);
        if (pkgs == null) return false;
        for (String p : pkgs) {
            if (QUICKSTAR.equals(p)) {
                try {
                    return c.getPackageManager().getPackageInfo(p, 0) != null;
                } catch (PackageManager.NameNotFoundException e) {
                    return false;
                }
            }
        }
        return false;
    }
}
