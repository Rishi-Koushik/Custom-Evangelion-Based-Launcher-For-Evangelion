package dev.rishi.quickstar;

import android.app.Notification;
import android.app.NotificationManager;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.Set;

/**
 * Hands new notifications to the NERV banner. It only passes along what Nothing's own pop-up would have shown
 * (title, text, icon, actions) and keeps nothing: no notification is stored or sent anywhere.
 */
public class NervNotifListener extends NotificationListenerService {
    /** keys already announced, so progress updates and "alert once" re-posts don't pop up again */
    private final Set<String> announced = new HashSet<>();

    @Override
    public void onListenerConnected() {
        NervNotifs.listenerUp = true;
        NervNotifs.sync(this);
    }

    @Override
    public void onListenerDisconnected() {
        NervNotifs.listenerUp = false;
        NervNotifs.sync(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankings) {
        if (!NervNotifs.enabled(this) || getPackageName().equals(sbn.getPackageName())) return;
        BannerStack stack = BannerStack.instance;
        if (stack == null) return;
        Notification n = sbn.getNotification();
        boolean urgent = n.fullScreenIntent != null
                && (Notification.CATEGORY_CALL.equals(n.category) || Notification.CATEGORY_ALARM.equals(n.category));
        if (urgent) {
            stack.show(sbn, true, this);
            return;
        }
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;
        String template = n.extras.getString(Notification.EXTRA_TEMPLATE, "");
        if (template.contains("MediaStyle")) return;
        Ranking r = new Ranking();
        if (rankings != null && rankings.getRanking(sbn.getKey(), r)) {
            // silent notifications and ones Do Not Disturb holds back never popped up either
            if (r.getImportance() < NotificationManager.IMPORTANCE_DEFAULT || !r.matchesInterruptionFilter() || r.isSuspended()) return;
        }
        boolean again = !announced.add(sbn.getKey());
        if (again && ((n.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0 || n.extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0)) return;
        if (!getSystemService(PowerManager.class).isInteractive()) return; // screen off: it waits in the shade as usual
        stack.show(sbn, false, this);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        announced.remove(sbn.getKey());
        BannerStack stack = BannerStack.instance;
        if (stack != null) stack.remove(sbn.getKey());
    }

    /** Called by the banner when a tapped notification should leave the shade, as tapping it there would. */
    void dismiss(String key) {
        try {
            cancelNotification(key);
        } catch (RuntimeException ignored) {
            // listener already disconnected
        }
    }
}
