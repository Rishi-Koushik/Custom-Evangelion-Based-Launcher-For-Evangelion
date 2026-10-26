package dev.rishi.quickstar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * The NERV sound set: each unit has its own ringtone, notification and alarm sound (made by nerv-os/tools/gen_sounds.py,
 * shipped in res/raw). Picking a unit copies that unit's three files into the phone's Ringtones / Notifications / Alarms
 * folders (once) and makes them the defaults. The sounds the phone had before are kept and put back when this is off.
 */
final class NervSounds {
    private static final String KEY_ON = "nerv_sounds";

    private static final class Kind {
        final String file, label, dir, flag;
        final int type;
        final String setting;

        Kind(String file, String label, String dir, String flag, int type, String setting) {
            this.file = file; this.label = label; this.dir = dir; this.flag = flag; this.type = type; this.setting = setting;
        }
    }

    private static final Kind[] KINDS = {
            new Kind("ring", "ringtone", Environment.DIRECTORY_RINGTONES, MediaStore.Audio.Media.IS_RINGTONE,
                    RingtoneManager.TYPE_RINGTONE, Settings.System.RINGTONE),
            new Kind("notif", "notification", Environment.DIRECTORY_NOTIFICATIONS, MediaStore.Audio.Media.IS_NOTIFICATION,
                    RingtoneManager.TYPE_NOTIFICATION, Settings.System.NOTIFICATION_SOUND),
            new Kind("alarm", "alarm", Environment.DIRECTORY_ALARMS, MediaStore.Audio.Media.IS_ALARM,
                    RingtoneManager.TYPE_ALARM, Settings.System.ALARM_ALERT),
    };

    private NervSounds() {}

    static boolean enabled(Context c) {
        return Sys.prefs(c).getBoolean(KEY_ON, true);
    }

    /** Turn the set on (applies the current unit) or off (puts the phone's own sounds back). */
    static void setEnabled(Context c, boolean on) {
        Sys.prefs(c).edit().putBoolean(KEY_ON, on).apply();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            if (on) set(app, Units.byId(UnitSync.current(app)));
            else restore(app);
        }).start();
    }

    /** Make this unit's sounds the phone's defaults. Runs off the main thread; does nothing while the set is off. */
    static void apply(Context c, Units.Unit u) {
        if (!enabled(c)) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> set(app, u)).start();
    }

    private static synchronized void set(Context c, Units.Unit u) {
        if (!Sys.canWriteSystem(c)) return;
        SharedPreferences p = Sys.prefs(c);
        if (!p.getBoolean("sounds_backup_done", false)) {
            SharedPreferences.Editor e = p.edit();
            for (Kind k : KINDS) e.putString("bk_sound_" + k.file, Settings.System.getString(c.getContentResolver(), k.setting));
            e.putBoolean("sounds_backup_done", true).apply();
        }
        for (Kind k : KINDS) {
            Uri uri = ensure(c, k, u);
            if (uri != null) RingtoneManager.setActualDefaultRingtoneUri(c, k.type, uri);
        }
    }

    private static synchronized void restore(Context c) {
        if (!Sys.canWriteSystem(c)) return;
        SharedPreferences p = Sys.prefs(c);
        if (!p.getBoolean("sounds_backup_done", false)) return;
        for (Kind k : KINDS) {
            String orig = p.getString("bk_sound_" + k.file, null);
            if (orig != null) RingtoneManager.setActualDefaultRingtoneUri(c, k.type, Uri.parse(orig));
        }
    }

    /** The unit's file in the shared media store, copied from res/raw the first time it is needed. */
    private static Uri ensure(Context c, Kind k, Units.Unit u) {
        ContentResolver cr = c.getContentResolver();
        Uri coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String name = "NERV " + u.shortName() + " " + k.label + ".ogg";
        String path = k.dir + "/NERV/";
        try (Cursor cur = cr.query(coll, new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.DISPLAY_NAME + "=? AND " + MediaStore.Audio.Media.RELATIVE_PATH + "=?",
                new String[]{name, path}, null)) {
            if (cur != null && cur.moveToFirst()) return Uri.withAppendedPath(coll, String.valueOf(cur.getLong(0)));
        }
        int res = c.getResources().getIdentifier("nerv_" + k.file + "_" + u.id, "raw", c.getPackageName());
        if (res == 0) return null;
        ContentValues v = new ContentValues();
        v.put(MediaStore.Audio.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Audio.Media.TITLE, "NERV · " + u.shortName() + " " + k.label);
        v.put(MediaStore.Audio.Media.MIME_TYPE, "audio/ogg");
        v.put(MediaStore.Audio.Media.RELATIVE_PATH, path);
        v.put(k.flag, 1);
        v.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri uri = cr.insert(coll, v);
        if (uri == null) return null;
        try (InputStream in = c.getResources().openRawResource(res); OutputStream out = cr.openOutputStream(uri)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            Log.w(Sys.TAG, "copy " + name, e);
            cr.delete(uri, null, null);
            return null;
        }
        v.clear();
        v.put(MediaStore.Audio.Media.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        return uri;
    }
}
