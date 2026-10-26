package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so that Android lets NowPlaying read other apps' media sessions; no notification is read.
 * Approve with {@code cmd notification allow_listener com.gathin.tvhome/.MediaListener}.
 */
public class MediaListener extends NotificationListenerService {
}
