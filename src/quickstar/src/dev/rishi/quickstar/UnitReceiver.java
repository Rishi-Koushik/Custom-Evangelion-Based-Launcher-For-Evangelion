package dev.rishi.quickstar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The NERV Home launcher tells Quickstar which unit was picked. Only that app's uid is accepted. */
public class UnitReceiver extends BroadcastReceiver {
    private static final String LAUNCHER = "dev.nervos.home";

    @Override
    public void onReceive(Context c, Intent i) {
        if (!"dev.rishi.quickstar.APPLY_UNIT".equals(i.getAction()) || !fromLauncher(c)) return;
        String id = i.getStringExtra("unit");
        if (id == null) return;
        String clock = i.getStringExtra("clock");
        if (clock != null) Sys.prefs(c).edit().putString("clock_face", clock).apply(); // kept until NERV Home sends another
        UnitSync.apply(c, id, i.getBooleanExtra("xdark", UnitSync.xdark(c)), i.getBooleanExtra("saver", false), false);
    }

    private boolean fromLauncher(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 34) return false;
        int uid = getSentFromUid();
        if (uid < 0) return false;
        String[] pkgs = c.getPackageManager().getPackagesForUid(uid);
        if (pkgs == null) return false;
        for (String p : pkgs) if (LAUNCHER.equals(p)) return true;
        return false;
    }
}
