package dev.rishi.quickstar;

import android.service.notification.NotificationListenerService;

/** Present only so the control centre may read active media sessions; it never looks at notifications. */
public class MediaListener extends NotificationListenerService {}
