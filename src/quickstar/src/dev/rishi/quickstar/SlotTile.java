package dev.rishi.quickstar;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** Shared behaviour of the four user-configurable quick settings tiles. */
abstract class SlotTile extends TileService {
    abstract int slot();

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
        Slots.Cfg g = Slots.get(this, slot());
        t.setLabel(g.label.isEmpty() ? "Quickstar " + slot() : g.label);
        t.setIcon(Icon.createWithBitmap(Slots.icon(this, slot(), g)));
        t.setState(Slots.action(this, g) == null ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() {
        Intent i = Slots.action(this, Slots.get(this, slot()));
        if (i == null) {
            Toast.makeText(this, "Open Quickstar to set this tile up", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, slot(), i, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(i);
            }
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }
}
