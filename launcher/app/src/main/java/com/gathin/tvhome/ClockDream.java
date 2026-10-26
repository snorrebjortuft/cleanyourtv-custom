package com.gathin.tvhome;

import android.content.Context;
import android.service.dreams.DreamService;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene).
 * No network, no images: cheap enough for a 1 GB TV. With now_playing, music mode takes its place while
 * something plays (MusicScene). Texts and the date follow the launcher's locale, not the TV's.
 */
public class ClockDream extends DreamService {

    private static final long MINUTE_MS = 60_000;

    private ClockScene scene;
    private MusicScene music;
    private TextView date;
    private DateTimeFormatter dateFormat;
    private final Runnable dateTick = new Runnable() {
        @Override
        public void run() {
            if (date == null) return;
            date.setText(dateFormat.format(LocalDate.now()));
            date.postDelayed(this, MINUTE_MS - System.currentTimeMillis() % MINUTE_MS + 50);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);
        scene = new ClockScene(getWindow().getDecorView());
        scene.useHomeWallpaper(this);
        Config cfg;
        try {
            cfg = Config.load(this);
        } catch (RuntimeException e) {
            cfg = null;
        }
        // Android puts the TV's language back when a screensaver starts, so the launcher's language is applied
        // here and used explicitly, instead of trusting the process default.
        Context local = LauncherLocale.wrap(this);
        Locale locale = cfg == null ? null : LauncherLocale.parse(cfg.locale);
        if (locale == null) locale = Locale.getDefault();
        dateFormat = DateTimeFormatter.ofPattern(local.getString(R.string.date_format), locale);
        date = findViewById(R.id.dreamDate);
        dateTick.run();
        if (cfg != null && cfg.nowPlaying) {
            FrameLayout root = (FrameLayout) findViewById(R.id.dreamAmbient).getParent();
            music = new MusicScene(root, findViewById(R.id.dreamContent), local, dateFormat);
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
        if (music != null) music.start();
    }

    @Override
    public void onDreamingStopped() {
        scene.stop();
        if (music != null) music.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (date != null) date.removeCallbacks(dateTick);
        if (scene != null) scene.stop();
        if (music != null) music.stop();
        super.onDetachedFromWindow();
    }
}
