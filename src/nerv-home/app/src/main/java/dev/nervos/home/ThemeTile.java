package dev.nervos.home;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick-settings tile that opens the NERV theme picker, including over the lock screen. */
public class ThemeTile extends TileService {
    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onTileAdded() {
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        Units.Unit u = UnitStore.current(this);
        t.setLabel("NERV theme");
        t.setSubtitle(u.shortName());
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_theme));
        t.setState(Tile.STATE_ACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() {
        Intent i = new Intent(this, ThemeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
