package dev.nervos.glyph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * NERV Home tells this app which unit is active, so the charging screen, its sound and its haptics follow the theme.
 * Only the launcher's own uid is accepted: the system reports who sent the broadcast.
 */
public class UnitReceiver extends BroadcastReceiver {
    private static final String LAUNCHER = "dev.nervos.home";

    @Override
    public void onReceive(Context c, Intent i) {
        if (!"dev.nervos.glyph.SET_UNIT".equals(i.getAction()) || !fromLauncher(c)) return;
        String id = i.getStringExtra("unit");
        if (id != null) c.getSharedPreferences(ChargeWatchService.PREFS, Context.MODE_PRIVATE).edit().putString("unit", id).apply();
    }

    static Units.Unit current(Context c) {
        return Units.byId(c.getSharedPreferences(ChargeWatchService.PREFS, Context.MODE_PRIVATE).getString("unit", "unit01"));
    }

    private boolean fromLauncher(Context c) {
        int uid = getSentFromUid();
        if (uid < 0) return false;
        String[] pkgs = c.getPackageManager().getPackagesForUid(uid);
        if (pkgs == null) return false;
        for (String p : pkgs) if (LAUNCHER.equals(p)) return true;
        return false;
    }
}
