package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * The now-playing card on Home (now_playing): album art, "PLAYING NOW · SPOTIFY", title, artist and a thin progress
 * line, in a dark glass card under the top-right buttons. Shown only while a track is playing or paused; takes no
 * focus, so D-pad navigation is unchanged. The progress runs once a second only while Home is resumed and it plays.
 */
final class NowPlayingCard extends LinearLayout {

    private static final long TICK_MS = 1000;

    private final NowPlaying source;
    private final ImageView art;
    private final TextView kicker, title, artist;
    private final ProgressBar progress;
    private final Runnable changed = this::refresh;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (resumed && source.isPlaying()) postDelayed(this, TICK_MS);
        }
    };
    private boolean resumed;

    NowPlayingCard(Context context) {
        super(context);
        source = NowPlaying.get(context);
        float d = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        // Reached with Down from the top bar (MainActivity); OK opens the app that plays.
        setFocusable(true);
        int pad = (int) (12 * d);
        setPadding(pad, pad, (int) (18 * d), pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xA6000000);
        bg.setCornerRadius(18 * d);
        bg.setStroke(Math.max(1, (int) d), 0x26FFFFFF);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xCC1A1A1A);
        focused.setCornerRadius(18 * d);
        focused.setStroke(Math.max(2, (int) (2 * d)), 0xE6FFFFFF);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, bg);
        setBackground(states);
        setOnFocusChangeListener((v, has) -> animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                .setDuration(160).start());
        setOnClickListener(v -> openSource());
        setVisibility(GONE);

        art = new ImageView(context);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackgroundColor(0x33FFFFFF);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10 * d);
            }
        });
        art.setClipToOutline(true);
        int size = (int) (84 * d);
        addView(art, new LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        LayoutParams tl = new LayoutParams((int) (260 * d), LayoutParams.WRAP_CONTENT);
        tl.setMarginStart((int) (14 * d));
        addView(texts, tl);

        Typeface medium = getResources().getFont(R.font.outfit_medium);
        Typeface regular = getResources().getFont(R.font.outfit_regular);
        kicker = line(context, texts, 11, 0x99FFFFFF, medium);
        kicker.setLetterSpacing(0.16f);
        kicker.setAllCaps(true);
        title = line(context, texts, 19, 0xFFFFFFFF, medium);
        artist = line(context, texts, 15, 0xB3FFFFFF, regular);

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(2 * d);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xE6FFFFFF);
        fill.setCornerRadius(2 * d);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{
                track, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        progress.setProgressDrawable(layers);
        LayoutParams pl = new LayoutParams(LayoutParams.MATCH_PARENT, (int) (3 * d));
        pl.topMargin = (int) (10 * d);
        texts.addView(progress, pl);
    }

    private static TextView line(Context c, LinearLayout parent, int sp, int color, Typeface face) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (3 * c.getResources().getDisplayMetrics().density);
        parent.addView(t, lp);
        return t;
    }

    /** Home resumed: follow the sessions; false when the listener is not approved (the card stays gone). */
    boolean resume() {
        resumed = true;
        boolean ok = source.subscribe(changed);
        refresh();
        return ok;
    }

    void pause() {
        resumed = false;
        removeCallbacks(tick);
        source.unsubscribe(changed);
    }

    /** Runs when the card disappears while it has focus, so focus can go somewhere visible (the dock). */
    void setOnHiddenWhileFocused(Runnable r) {
        onHidden = r;
    }

    private Runnable onHidden;

    /** Opens the app that plays (Spotify, YouTube Music...) on its TV entry, as a tile would. */
    private void openSource() {
        String pkg = source.sourcePackage();
        if (pkg.isEmpty()) return;
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        android.content.Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        try {
            getContext().startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException ignored) {
            // the app went away between the session and the press
        }
    }

    private void refresh() {
        if (!source.hasTrack()) {
            boolean had = hasFocus();
            setVisibility(GONE);
            removeCallbacks(tick);
            if (had && onHidden != null) onHidden.run();
            return;
        }
        String app = source.source();
        String state = getResources().getString(source.isPlaying() ? R.string.now_playing : R.string.now_paused);
        kicker.setText(app.isEmpty() ? state : state + " · " + app);
        title.setText(source.title());
        artist.setText(source.artist());
        artist.setVisibility(source.artist().isEmpty() ? GONE : VISIBLE);
        if (source.art() != null) art.setImageBitmap(source.art());
        else art.setImageDrawable(new ColorDrawable(0x33FFFFFF));
        setVisibility(VISIBLE);
        updateProgress();
        removeCallbacks(tick);
        if (resumed && source.isPlaying()) postDelayed(tick, TICK_MS);
    }

    private void updateProgress() {
        long dur = source.duration();
        progress.setVisibility(dur > 0 ? VISIBLE : INVISIBLE);
        if (dur > 0) progress.setProgress((int) (source.position() * 1000 / dur));
    }
}
