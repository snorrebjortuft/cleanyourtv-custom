package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * What is playing on the TV right now (Spotify, YouTube Music, NRK Radio...), from Android's media sessions, for
 * the now-playing card on Home and music mode in the screensaver (now_playing). Reading other apps' sessions needs
 * this app's notification listener approved once over ADB:
 * {@code cmd notification allow_listener com.gathin.tvhome/.MediaListener}. Without it nothing is shown.
 * One process-wide instance; it follows the sessions only while something on screen is subscribed.
 */
final class NowPlaying {

    private static NowPlaying instance;

    static NowPlaying get(Context context) {
        if (instance == null) instance = new NowPlaying(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();
    private MediaSessionManager sessions;
    private MediaController controller;

    private String title = "", artist = "", source = "", sourcePackage = "";
    private Bitmap art;
    private int state = PlaybackState.STATE_NONE;
    private long position, positionAt, duration;
    private float speed = 1f;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::choose;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            readMetadata(metadata);
            notifyListeners();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            readState(s);
            // Another session may have taken over while this one paused.
            if (!isPlaying()) choose(null);
            else notifyListeners();
        }

        @Override
        public void onSessionDestroyed() {
            choose(null);
        }
    };

    private NowPlaying(Context app) {
        this.app = app;
    }

    /** Starts following the sessions for {@code r}, called on any change; false when the listener is not approved. */
    boolean subscribe(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
        if (sessions != null) return true;
        sessions = app.getSystemService(MediaSessionManager.class);
        if (sessions == null) return false;
        try {
            ComponentName me = new ComponentName(app, MediaListener.class);
            sessions.addOnActiveSessionsChangedListener(changed, me, main);
            choose(sessions.getActiveSessions(me));
            return true;
        } catch (SecurityException notApproved) {
            sessions = null;
            return false;
        }
    }

    void unsubscribe(Runnable r) {
        listeners.remove(r);
        if (!listeners.isEmpty() || sessions == null) return;
        sessions.removeOnActiveSessionsChangedListener(changed);
        sessions = null;
        follow(null);
    }

    /** The playing session first; else the first one with a title (paused). */
    private void choose(List<MediaController> list) {
        if (list == null) {
            if (sessions == null) return;
            try {
                list = sessions.getActiveSessions(new ComponentName(app, MediaListener.class));
            } catch (SecurityException e) {
                return;
            }
        }
        MediaController pick = null;
        for (MediaController c : list) {
            PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                pick = c;
                break;
            }
            if (pick == null && c.getMetadata() != null) pick = c;
        }
        follow(pick);
        notifyListeners();
    }

    private void follow(MediaController c) {
        if (controller != null && (c == null || !c.getSessionToken().equals(controller.getSessionToken()))) {
            controller.unregisterCallback(callback);
        }
        boolean fresh = c != null && (controller == null || !c.getSessionToken().equals(controller.getSessionToken()));
        controller = c;
        if (c == null) {
            title = artist = source = sourcePackage = "";
            art = null;
            state = PlaybackState.STATE_NONE;
            return;
        }
        if (fresh) c.registerCallback(callback, main);
        sourcePackage = c.getPackageName();
        source = label(sourcePackage);
        readMetadata(c.getMetadata());
        readState(c.getPlaybackState());
    }

    private void readMetadata(MediaMetadata m) {
        if (m == null) {
            title = artist = "";
            art = null;
            duration = 0;
            return;
        }
        title = text(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
        artist = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        art = b;
        duration = Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }

    private void readState(PlaybackState s) {
        if (s == null) {
            state = PlaybackState.STATE_NONE;
            return;
        }
        state = s.getState();
        position = s.getPosition();
        positionAt = s.getLastPositionUpdateTime();
        speed = s.getPlaybackSpeed();
    }

    private static String text(MediaMetadata m, String... keys) {
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.toString().trim().length() > 0) return v.toString().trim();
        }
        return "";
    }

    private String label(String pkg) {
        try {
            PackageManager pm = app.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void notifyListeners() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    boolean hasTrack() {
        return !title.isEmpty() && (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED
                || state == PlaybackState.STATE_BUFFERING);
    }

    boolean isPlaying() {
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING;
    }

    String title() { return title; }
    String artist() { return artist; }
    String source() { return source; }
    String sourcePackage() { return sourcePackage; }
    Bitmap art() { return art; }
    long duration() { return duration; }

    /** The position now, run forward from the session's last report while it plays. */
    long position() {
        long p = position;
        if (state == PlaybackState.STATE_PLAYING && positionAt > 0) {
            p += (long) ((SystemClock.elapsedRealtime() - positionAt) * speed);
        }
        return duration > 0 ? Math.max(0, Math.min(duration, p)) : Math.max(0, p);
    }
}
