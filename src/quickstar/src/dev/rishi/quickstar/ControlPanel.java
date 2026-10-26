package dev.rishi.quickstar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.NotificationManager;
import android.bluetooth.BluetoothManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A glass-style control centre: connectivity pills, toggle grid, sliders, media, SIM and modes. */
final class ControlPanel extends FrameLayout {
    interface Host {
        void close();

        void start(Intent i);

        void power();
    }

    private final Context c;
    private final ControlCfg cfg;
    private final ControlTheme th;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Runnable> refreshers = new ArrayList<>();
    private final ScrollView scroll;
    private final float a;
    private final float rs;
    private final CameraManager camera;
    private boolean torchOn;
    private String torchId;
    private boolean closing;
    private float downY;
    private boolean sliderTouched;
    private final CameraManager.TorchCallback torchCb = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String id, boolean enabled) {
            if (id.equals(torchId)) {
                torchOn = enabled;
                refreshAll();
            }
        }
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshAll();
            h.postDelayed(this, 1000);
        }
    };

    ControlPanel(Context ctx, ControlCfg cfg, Host host) {
        super(ctx);
        this.c = ctx;
        this.cfg = cfg;
        this.th = ControlTheme.of(cfg, ctx.getSystemService(PowerManager.class).isPowerSaveMode());
        this.host = host;
        this.a = cfg.opacity / 100f;
        this.rs = cfg.radius / 100f;
        camera = ctx.getSystemService(CameraManager.class);
        try {
            for (String id : camera.getCameraIdList()) {
                Boolean flash = camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    torchId = id;
                    break;
                }
            }
        } catch (CameraAccessException ignored) {
            // no torch control
        }
        if (th.saver) setBackgroundColor(th.bg);
        else if (th.eva) setBackground(new Shapes2.Hex(th.bg, th.grid, Ui.dp(26)));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnClickListener(v -> dismiss());

        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        int top = statusBarHeight() + Ui.dp(6);
        col.setPadding(Ui.dp(16), top, Ui.dp(16), Ui.dp(48));
        scroll.addView(col);
        addView(scroll, new LayoutParams(-1, -1));

        buildHeader(col);
        buildPills(col);
        buildGrid(col);
        buildSlidersAndMedia(col);
        if (cfg.bottom) buildQuickRow(col);
        if (cfg.sim) buildSim(col);
        if (cfg.bottom) buildModes(col);
    }

    private int statusBarHeight() {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : Ui.dp(28);
    }

    // ---------- look ----------

    private boolean isWarn(String id) {
        return th.eva && (id.equals("flash") || id.equals("saver") || id.equals("airplane"));
    }

    private int contentColor(boolean active) {
        return active ? th.onActive : th.onInactive;
    }

    private Drawable glass(float radiusDp) {
        if (th.eva) return new Shapes2.Cut(th.card, th.stroke, Ui.dp(1), Ui.dp(radiusDp * 0.42f * rs), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(th.card);
        g.setCornerRadius(Ui.dp(radiusDp * rs));
        g.setStroke(Ui.dp(1), th.stroke);
        return g;
    }

    private Drawable badge(boolean active, boolean warn) {
        int fill = active ? (warn ? th.warn : th.active) : th.inactive;
        if (th.eva) return new Shapes2.Cut(fill, active ? 0 : th.badgeStroke, Ui.dp(1), 0, 0.29f);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (th.eva) {
            t.setTypeface(Typeface.create(Fonts.mono(c), bold ? Typeface.BOLD : Typeface.NORMAL));
            t.setLetterSpacing(0.04f);
            t.setAllCaps(true);
        } else {
            t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            t.setShadowLayer(3, 0, 1, 0x40000000);
        }
        return t;
    }

    private LinearLayout.LayoutParams weight(float w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, h, w);
        p.setMargins(Ui.dp(l), Ui.dp(t), Ui.dp(r), Ui.dp(b));
        return p;
    }

    // ---------- sections ----------

    private void buildHeader(LinearLayout col) {
        LinearLayout status = new LinearLayout(c);
        final TextView carrier = text("", 12, th.eva ? th.sub : 0xDDFFFFFF, false);
        final TextView battery = text("", 12, th.eva ? th.accent : 0xDDFFFFFF, true);
        status.addView(carrier, weight(1, -2, 4, 0, 0, 0));
        status.addView(battery);
        col.addView(status, Ui.match(0, 8));

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout when = new LinearLayout(c);
        when.setGravity(Gravity.BOTTOM);
        final TextView time = text("", 30, th.text, true);
        final TextView date = text("", 14, th.eva ? th.accent : 0xE6FFFFFF, false);
        when.addView(time);
        when.addView(date, Ui.lp(-2, -2, 10, 0, 0, 5));
        row.addView(when, weight(1, -2, 4, 0, 0, 0));
        row.addView(headerIcon("pencil", th.text, v -> host.start(new Intent(c, MainActivity.class).putExtra("tab", 0))));
        row.addView(headerIcon("power", th.eva ? th.warn : th.text, v -> host.power()));
        row.addView(headerIcon("gear", th.text, v -> host.start(new Intent(Settings.ACTION_SETTINGS))));
        col.addView(row, Ui.match(0, 14));

        refreshers.add(() -> {
            Date now = new Date();
            time.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            date.setText(new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now));
            TelephonyManager tm = c.getSystemService(TelephonyManager.class);
            String op = tm == null ? "" : tm.getNetworkOperatorName();
            String net = ssid();
            carrier.setText((op.isEmpty() ? "No service" : op) + (net.isEmpty() ? "" : "  •  " + net));
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                boolean ch = b.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING;
                int pct = lvl * 100 / Math.max(1, sc);
                battery.setText(th.eva ? "SYNC RATIO " + pct + "%" + (ch ? " · CHARGING" : "")
                        : (ch ? "⚡ " : "") + pct + "%");
            }
        });
    }

    private View headerIcon(String name, int color, OnClick l) {
        Icons.IconView v = new Icons.IconView(c, name).scale(0.52f);
        v.set(name, color);
        v.setOnClickListener(x -> l.on(x));
        v.setLayoutParams(Ui.lp(Ui.dp(42), Ui.dp(42), 0, 0, 0, 0));
        return v;
    }

    private interface OnClick {
        void on(View v);
    }

    private static final class Pill {
        String name;
        LinearLayout root;
        View circle;
        Icons.IconView icon;
        TextView title;
        TextView sub;
    }

    private Pill pill(String icon, String title, String sub) {
        final Pill p = new Pill();
        p.name = icon;
        p.root = new LinearLayout(c);
        p.root.setGravity(Gravity.CENTER_VERTICAL);
        p.root.setBackground(glass(32));
        p.root.setPadding(Ui.dp(10), 0, Ui.dp(12), 0);
        FrameLayout badge = new FrameLayout(c);
        p.circle = new View(c);
        p.circle.setBackground(badge(false, false));
        badge.addView(p.circle, new FrameLayout.LayoutParams(-1, -1));
        p.icon = new Icons.IconView(c, icon).scale(0.5f);
        p.icon.set(icon, contentColor(false));
        badge.addView(p.icon, new FrameLayout.LayoutParams(-1, -1));
        p.root.addView(badge, Ui.lp(Ui.dp(44), Ui.dp(44), 0, 0, 10, 0));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        p.title = text(title, 14, th.text, true);
        p.sub = text(sub, 11, th.sub, false);
        t.addView(p.title);
        if (sub != null) t.addView(p.sub);
        p.root.addView(t, weight(1, -2, 0, 0, 0, 0));
        return p;
    }

    private void setPill(Pill p, boolean active, String sub) {
        p.circle.setBackground(badge(active, false));
        p.icon.set(p.name, contentColor(active));
        if (sub != null) p.sub.setText(sub);
    }

    private void buildPills(LinearLayout col) {
        LinearLayout row = new LinearLayout(c);
        final Pill wifi = pill("wifi", "Wi-Fi", "");
        wifi.root.setOnClickListener(v -> host.start(new Intent("android.settings.panel.action.INTERNET_CONNECTIVITY")));
        wifi.root.setOnLongClickListener(v -> {
            host.start(new Intent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        final Pill bt = pill("bt", "Bluetooth", "");
        bt.root.setOnClickListener(v -> host.start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        row.addView(wifi.root, weight(1, Ui.dp(64), 0, 0, 5, 0));
        row.addView(bt.root, weight(1, Ui.dp(64), 5, 0, 0, 0));
        col.addView(row, Ui.match(0, 12));
        refreshers.add(() -> {
            WifiManager wm = c.getApplicationContext().getSystemService(WifiManager.class);
            boolean on = wm != null && wm.isWifiEnabled();
            String ss = ssid();
            setPill(wifi, on, !on ? "Off" : !ss.isEmpty() ? ss : wifiConnected() ? "Connected" : "Not connected");
            BluetoothManager bm = c.getSystemService(BluetoothManager.class);
            boolean bon = false;
            try {
                bon = bm != null && bm.getAdapter() != null && bm.getAdapter().isEnabled();
            } catch (SecurityException ignored) {
                // state unknown
            }
            setPill(bt, bon, bon ? "On" : "Off");
        });
    }

    private boolean wifiConnected() {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            for (android.net.Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String ssid() {
        try {
            WifiManager wm = c.getApplicationContext().getSystemService(WifiManager.class);
            WifiInfo i = wm.getConnectionInfo();
            String s = i == null ? null : i.getSSID();
            if (s == null || s.equals("<unknown ssid>") || s.equals("0x")) return "";
            return s.replace("\"", "");
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---------- toggles ----------

    private final List<Runnable> toggleRefreshers = new ArrayList<>();

    private View roundToggle(final String id, float sizeDp) {
        String[] e = ControlCfg.entry(id);
        FrameLayout f = new FrameLayout(c);
        final View bg = new View(c);
        final Icons.IconView ic = new Icons.IconView(c, e[2]).scale(0.5f);
        ic.set(e[2], contentColor(false));
        f.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        f.addView(ic, new FrameLayout.LayoutParams(-1, -1));
        bg.setBackground(badge(false, false));
        f.setOnClickListener(v -> {
            tap(id);
            h.postDelayed(this::refreshAll, 250);
        });
        f.setOnLongClickListener(v -> {
            hold(id);
            return true;
        });
        refreshers.add(() -> {
            boolean on = on(id);
            bg.setBackground(badge(on, isWarn(id)));
            ic.set(e[2], contentColor(on));
        });
        return f;
    }

    private void buildGrid(LinearLayout col) {
        if (cfg.tiles.isEmpty()) return;
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(glass(32));
        card.setPadding(Ui.dp(10), Ui.dp(14), Ui.dp(10), Ui.dp(8));
        for (int i = 0; i < cfg.tiles.size(); i += 4) {
            LinearLayout row = new LinearLayout(c);
            for (int k = 0; k < 4; k++) {
                FrameLayout cell = new FrameLayout(c);
                if (i + k < cfg.tiles.size()) {
                    View t = roundToggle(cfg.tiles.get(i + k), 56);
                    cell.addView(t, new FrameLayout.LayoutParams(Ui.dp(56), Ui.dp(56), Gravity.CENTER));
                }
                row.addView(cell, weight(1, Ui.dp(64), 0, 0, 0, 0));
            }
            card.addView(row);
        }
        View handle = new View(c);
        GradientDrawable hd = new GradientDrawable();
        hd.setColor(th.eva ? Color.argb(0xAA, Color.red(th.accent), Color.green(th.accent), Color.blue(th.accent)) : 0x88FFFFFF);
        hd.setCornerRadius(Ui.dp(2));
        handle.setBackground(hd);
        card.addView(handle, Ui.lp(Ui.dp(34), Ui.dp(4), 0, 4, 0, 4));
        ((LinearLayout.LayoutParams) handle.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(card, Ui.match(0, 12));
    }

    private boolean on(String id) {
        try {
            switch (id) {
                case "rotate":
                    return Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
                case "flash":
                    return torchOn;
                case "saver":
                    return c.getSystemService(PowerManager.class).isPowerSaveMode();
                case "location":
                    return c.getSystemService(LocationManager.class).isLocationEnabled();
                case "data":
                    return c.getSystemService(TelephonyManager.class).isDataEnabled();
                case "airplane":
                    return Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
                case "dnd":
                    return c.getSystemService(NotificationManager.class).getCurrentInterruptionFilter()
                            != NotificationManager.INTERRUPTION_FILTER_ALL;
                case "mute":
                    return c.getSystemService(AudioManager.class).getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void tap(String id) {
        try {
            switch (id) {
                case "rotate":
                    Settings.System.putInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, on(id) ? 0 : 1);
                    break;
                case "flash":
                    if (torchId != null) camera.setTorchMode(torchId, !torchOn);
                    break;
                case "saver":
                    Settings.Global.putInt(c.getContentResolver(), "low_power", on(id) ? 0 : 1);
                    break;
                case "location":
                    Settings.Secure.putInt(c.getContentResolver(), "location_mode", on(id) ? 0 : 3);
                    break;
                case "data":
                case "airplane":
                    host.start(new Intent("android.settings.panel.action.INTERNET_CONNECTIVITY"));
                    break;
                case "dnd": {
                    NotificationManager nm = c.getSystemService(NotificationManager.class);
                    if (nm.isNotificationPolicyAccessGranted()) {
                        nm.setInterruptionFilter(on(id) ? NotificationManager.INTERRUPTION_FILTER_ALL
                                : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    } else {
                        host.start(new Intent("android.settings.ZEN_MODE_SETTINGS"));
                    }
                    break;
                }
                case "mute": {
                    AudioManager am = c.getSystemService(AudioManager.class);
                    am.setRingerMode(on(id) ? AudioManager.RINGER_MODE_NORMAL : AudioManager.RINGER_MODE_VIBRATE);
                    break;
                }
                default:
                    hold(id);
            }
        } catch (CameraAccessException | RuntimeException e) {
            hold(id);
        }
    }

    private void hold(String id) {
        String action;
        switch (id) {
            case "rotate": action = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "saver": action = Settings.ACTION_BATTERY_SAVER_SETTINGS; break;
            case "location": action = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
            case "data": action = Settings.ACTION_WIRELESS_SETTINGS; break;
            case "airplane": action = Settings.ACTION_AIRPLANE_MODE_SETTINGS; break;
            case "hotspot": action = "android.settings.TETHER_SETTINGS"; break;
            case "cast": action = Settings.ACTION_CAST_SETTINGS; break;
            case "dnd": action = "android.settings.ZEN_MODE_SETTINGS"; break;
            case "mute": action = Settings.ACTION_SOUND_SETTINGS; break;
            default: return;
        }
        host.start(new Intent(action));
    }

    // ---------- sliders + media ----------

    private interface Change {
        void on(float v);
    }

    private final class VSlider extends View {
        private final String icon;
        private final Change change;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private float value;
        private boolean dragging;

        VSlider(String icon, Change change) {
            super(c);
            this.icon = icon;
            this.change = change;
        }

        void show(float v) {
            if (dragging) return;
            value = Math.max(0, Math.min(1, v));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = Ui.dp(32 * rs);
            clip.reset();
            if (th.eva) clip.set(Shapes2.chamfer(w, h, Ui.dp(26 * rs), false));
            else clip.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
            p.setStyle(Paint.Style.FILL);
            p.setColor(th.card);
            cv.drawPath(clip, p);
            cv.save();
            cv.clipPath(clip);
            p.setColor(th.slider);
            cv.drawRect(0, h * (1 - value), w, h, p);
            cv.restore();
            if (th.eva) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(1));
                p.setColor(th.stroke);
                cv.drawPath(clip, p);
            }
            float iconY = h - Ui.dp(34), edge = h * (1 - value);
            cv.save();
            cv.clipRect(0, edge, w, h);
            Icons.draw(cv, icon, w / 2, iconY, Ui.dp(28), th.eva ? th.onActive : th.text);
            cv.restore();
            cv.save();
            cv.clipRect(0, 0, w, edge);
            Icons.draw(cv, icon, w / 2, iconY, Ui.dp(28), th.text);
            cv.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    sliderTouched = true;
                    dragging = true;
                case MotionEvent.ACTION_MOVE:
                    value = Math.max(0, Math.min(1, 1 - e.getY() / getHeight()));
                    change.on(value);
                    invalidate();
                    return true;
                default:
                    dragging = false;
                    return true;
            }
        }
    }

    private MediaController activeController() {
        try {
            MediaSessionManager msm = c.getSystemService(MediaSessionManager.class);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(c, MediaListener.class));
            for (MediaController m : list) {
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return m;
            }
            return list.isEmpty() ? null : list.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void buildSlidersAndMedia(LinearLayout col) {
        LinearLayout row = new LinearLayout(c);
        final VSlider bright = new VSlider("sun", v -> {
            if (Settings.System.canWrite(c)) {
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, Math.round(v * 255)));
            }
        });
        final AudioManager am = c.getSystemService(AudioManager.class);
        final VSlider vol = new VSlider("volume", v -> am.setStreamVolume(AudioManager.STREAM_MUSIC,
                Math.round(v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0));
        row.addView(bright, weight(0, Ui.dp(196), 0, 0, 6, 0));
        row.addView(vol, weight(0, Ui.dp(196), 0, 0, 6, 0));
        ((LinearLayout.LayoutParams) bright.getLayoutParams()).width = Ui.dp(84);
        ((LinearLayout.LayoutParams) bright.getLayoutParams()).weight = 0;
        ((LinearLayout.LayoutParams) vol.getLayoutParams()).width = Ui.dp(84);
        ((LinearLayout.LayoutParams) vol.getLayoutParams()).weight = 0;
        refreshers.add(() -> {
            bright.show(Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) / 255f);
            vol.show(am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        });

        if (cfg.media) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(glass(32));
            card.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(14), Ui.dp(12));
            LinearLayout top = new LinearLayout(c);
            top.setGravity(Gravity.CENTER_VERTICAL);
            Icons.IconView note = new Icons.IconView(c, "note").scale(0.9f);
            note.set("note", th.text);
            top.addView(note, Ui.lp(Ui.dp(22), Ui.dp(22), 0, 0, 0, 0));
            View sp = new View(c);
            top.addView(sp, weight(1, 1, 0, 0, 0, 0));
            Icons.IconView out = new Icons.IconView(c, "mirror").scale(0.9f);
            out.set("mirror", th.text);
            out.setOnClickListener(v -> host.start(new Intent("android.settings.panel.action.VOLUME")));
            top.addView(out, Ui.lp(Ui.dp(26), Ui.dp(26), 0, 0, 0, 0));
            card.addView(top);
            final TextView title = text("Not playing", 14, th.text, true);
            final TextView artist = text("", 12, th.sub, false);
            card.addView(title, Ui.match(14, 0));
            card.addView(artist);
            View fill = new View(c);
            card.addView(fill, new LinearLayout.LayoutParams(1, 0, 1f));
            LinearLayout ctl = new LinearLayout(c);
            ctl.setGravity(Gravity.CENTER);
            final Icons.IconView prev = ctl(ctl, "prev");
            final Icons.IconView play = ctl(ctl, "play");
            final Icons.IconView next = ctl(ctl, "next");
            card.addView(ctl);
            prev.setOnClickListener(v -> withMedia(m -> m.getTransportControls().skipToPrevious()));
            next.setOnClickListener(v -> withMedia(m -> m.getTransportControls().skipToNext()));
            play.setOnClickListener(v -> withMedia(m -> {
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) m.getTransportControls().pause();
                else m.getTransportControls().play();
            }));
            row.addView(card, weight(1, Ui.dp(196), 0, 0, 0, 0));
            refreshers.add(() -> {
                MediaController m = activeController();
                if (m == null) {
                    title.setText("Not playing");
                    artist.setText("");
                    play.set("play", th.text);
                    return;
                }
                MediaMetadata md = m.getMetadata();
                CharSequence t = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
                CharSequence ar = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
                title.setText(t == null ? "Playing" : t);
                artist.setText(ar == null ? "" : ar);
                PlaybackState s = m.getPlaybackState();
                play.set(s != null && s.getState() == PlaybackState.STATE_PLAYING ? "pause" : "play", th.text);
            });
        } else {
            View filler = new View(c);
            row.addView(filler, weight(1, 1, 0, 0, 0, 0));
        }
        col.addView(row, Ui.match(0, 12));
    }

    private interface WithMedia {
        void on(MediaController m);
    }

    private void withMedia(WithMedia w) {
        MediaController m = activeController();
        if (m != null) {
            w.on(m);
            h.postDelayed(this::refreshAll, 300);
        }
    }

    private Icons.IconView ctl(LinearLayout row, String name) {
        Icons.IconView v = new Icons.IconView(c, name).scale(0.55f);
        v.set(name, th.text);
        row.addView(v, weight(1, Ui.dp(44), 0, 0, 0, 0));
        return v;
    }

    // ---------- lower rows ----------

    private void buildQuickRow(LinearLayout col) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dnd = roundToggle("dnd", 64);
        View mute = roundToggle("mute", 64);
        row.addView(dnd, Ui.lp(Ui.dp(64), Ui.dp(64), 0, 0, 10, 0));
        row.addView(mute, Ui.lp(Ui.dp(64), Ui.dp(64), 0, 0, 10, 0));
        Pill nearby = pill("mirror", "Nearby devices", null);
        nearby.root.setOnClickListener(v -> host.start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        row.addView(nearby.root, weight(1, Ui.dp(64), 0, 0, 0, 0));
        col.addView(row, Ui.match(0, 12));
    }

    private void buildSim(LinearLayout col) {
        LinearLayout card = new LinearLayout(c);
        card.setBackground(glass(28));
        card.setPadding(Ui.dp(6), Ui.dp(12), Ui.dp(6), Ui.dp(12));
        String[] heads = {"Calls", "Messages", "Mobile data"};
        for (int i = 0; i < heads.length; i++) {
            if (i > 0) {
                View d = new View(c);
                d.setBackgroundColor(th.eva ? th.stroke : 0x33FFFFFF);
                card.addView(d, Ui.lp(Ui.dp(1), Ui.dp(52), 0, 4, 0, 4));
            }
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            Icons.IconView sim = new Icons.IconView(c, "sim").scale(0.9f);
            sim.set("sim", th.accent);
            head.addView(sim, Ui.lp(Ui.dp(16), Ui.dp(16), 0, 0, 4, 0));
            head.addView(text(heads[i], 13, th.text, true));
            cell.addView(head);
            cell.addView(text("SIM 1", 12, th.text, false), Ui.lp(-2, -2, 0, 4, 0, 0));
            final TextView op = text("", 11, th.sub, false);
            op.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.addView(op, new LinearLayout.LayoutParams(-2, -2));
            refreshers.add(() -> {
                TelephonyManager tm = c.getSystemService(TelephonyManager.class);
                String n = tm == null ? "" : tm.getNetworkOperatorName();
                op.setText(n.isEmpty() ? "No service" : n);
            });
            cell.setOnClickListener(v -> host.start(new Intent(Settings.ACTION_WIRELESS_SETTINGS)));
            card.addView(cell, weight(1, -2, 0, 0, 0, 0));
        }
        col.addView(card, Ui.match(0, 12));
    }

    private void buildModes(LinearLayout col) {
        LinearLayout row = new LinearLayout(c);
        Pill view = pill("mirror", "Smart View", "Mirror screen");
        view.root.setOnClickListener(v -> host.start(new Intent(Settings.ACTION_CAST_SETTINGS)));
        Pill modes = pill("modes", "Modes", null);
        modes.root.setOnClickListener(v -> host.start(new Intent("android.settings.ZEN_MODE_SETTINGS")));
        row.addView(view.root, weight(1, Ui.dp(64), 0, 0, 5, 0));
        row.addView(modes.root, weight(1, Ui.dp(64), 5, 0, 0, 0));
        col.addView(row, Ui.match(0, 0));
    }

    // ---------- lifecycle ----------

    private void refreshAll() {
        for (Runnable r : refreshers) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // one broken tile must not blank the panel
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (torchId != null) camera.registerTorchCallback(torchCb, h);
        h.post(tick);
        requestFocus();
        setAlpha(0f);
        setTranslationY(-Ui.dp(40));
        animate().alpha(1f).translationY(0).setDuration(220).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null);
        if (torchId != null) camera.unregisterTorchCallback(torchCb);
        super.onDetachedFromWindow();
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        animate().alpha(0f).translationY(-Ui.dp(30)).setDuration(160).withEndAction(host::close).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            dismiss();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downY = e.getY();
            sliderTouched = false;
        }
        if (e.getAction() == MotionEvent.ACTION_UP && !sliderTouched && downY - e.getY() > Ui.dp(120)
                && !scroll.canScrollVertically(1)) {
            dismiss();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }
}
