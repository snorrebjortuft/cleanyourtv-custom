package no.kongsnorre.tvstats;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Counts how long each show plays, from Android's media sessions, into WatchLog. Android keeps this listener
 * bound once approved (cmd notification allow_listener no.kongsnorre.tvstats/.WatchListener), so it follows
 * playback without a service of its own. No notification is read. The show is the session's subtitle or artist
 * (series, YouTube channel, music artist), else its title.
 */
public class WatchListener extends NotificationListenerService {

    private static final long FLUSH_MS = 60_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager sessions;
    private final List<MediaController> followed = new ArrayList<>();
    private String pkg = "", show = "";
    private long since;
    private SharedPreferences log;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::follow;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { evaluate(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { evaluate(); }
        @Override public void onSessionDestroyed() { evaluate(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            flush();
            main.postDelayed(this, FLUSH_MS);
        }
    };

    @Override
    public void onListenerConnected() {
        log = WatchLog.prefs(this);
        WatchLog.prune(log, LocalDate.now());
        sessions = getSystemService(MediaSessionManager.class);
        if (sessions == null) return;
        ComponentName me = new ComponentName(this, WatchListener.class);
        try {
            sessions.addOnActiveSessionsChangedListener(changed, me, main);
            follow(sessions.getActiveSessions(me));
        } catch (SecurityException e) {
            sessions = null;
            return;
        }
        main.postDelayed(tick, FLUSH_MS);
    }

    @Override
    public void onListenerDisconnected() {
        flush();
        pkg = show = "";
        main.removeCallbacks(tick);
        for (MediaController c : followed) c.unregisterCallback(callback);
        followed.clear();
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        sessions = null;
    }

    private void follow(List<MediaController> list) {
        for (MediaController c : followed) c.unregisterCallback(callback);
        followed.clear();
        if (list != null) {
            for (MediaController c : list) {
                c.registerCallback(callback, main);
                followed.add(c);
            }
        }
        evaluate();
    }

    /** Closes the running stretch when what plays changes, and opens the next one. */
    private void evaluate() {
        String p = "", s = "";
        for (MediaController c : followed) {
            PlaybackState st = c.getPlaybackState();
            if (st == null || st.getState() != PlaybackState.STATE_PLAYING) continue;
            p = c.getPackageName();
            s = showOf(c.getMetadata());
            break;
        }
        if (p.equals(pkg) && s.equals(show)) return;
        flush();
        pkg = p;
        show = s;
        since = SystemClock.elapsedRealtime();
    }

    private void flush() {
        if (log == null || pkg.isEmpty() || show.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        WatchLog.add(log, LocalDate.now(), pkg, show, (now - since) / 1000);
        since = now - (now - since) % 1000;
    }

    static String showOf(MediaMetadata m) {
        if (m == null) return "";
        for (String k : new String[]{MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                MediaMetadata.METADATA_KEY_TITLE}) {
            CharSequence v = m.getText(k);
            if (v != null && v.toString().trim().length() > 0) return v.toString().trim();
        }
        return "";
    }
}
