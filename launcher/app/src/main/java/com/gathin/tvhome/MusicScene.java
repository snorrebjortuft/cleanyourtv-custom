package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextClock;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Music mode for the clock screensaver (now_playing): while something plays, Home's wallpaper stays behind, the
 * time and date sit on the left, and a glass card on the right shows the cover, what plays and from which app.
 * The whole layout shifts a little every minute so nothing burns in. When the music stops, the drifting clock
 * comes back.
 */
final class MusicScene {

    private static final long MOVE_MS = 60_000, TICK_MS = 1000;

    private final FrameLayout root;
    private final View clockContent;
    private final NowPlaying source;
    private final LinearLayout layout;
    private final ImageView cover;
    private final TextView kicker, title, artist, date;
    private final Context local;
    private final DateTimeFormatter dateFormat;
    private final ProgressBar progress;
    private final float d;
    private final Random rnd = new Random();
    private final Runnable changed = this::refresh;
    private final Runnable move = new Runnable() {
        @Override
        public void run() {
            float r = 24 * d;
            layout.animate().translationX((rnd.nextFloat() * 2 - 1) * r).translationY((rnd.nextFloat() * 2 - 1) * r)
                    .setDuration(2000).start();
            layout.postDelayed(this, MOVE_MS);
        }
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            date.setText(dateFormat.format(LocalDate.now()));   // turns over at midnight
            if (source.isPlaying()) layout.postDelayed(this, TICK_MS);
        }
    };

    /**
     * {@code local} is a context in the launcher's language and {@code dateFormat} its date pattern: the
     * screensaver's own context follows the TV's language.
     */
    MusicScene(FrameLayout root, View clockContent, Context local, DateTimeFormatter dateFormat) {
        this.root = root;
        this.clockContent = clockContent;
        this.local = local;
        this.dateFormat = dateFormat;
        Context c = root.getContext();
        source = NowPlaying.get(c);
        d = c.getResources().getDisplayMetrics().density;
        Typeface extralight = c.getResources().getFont(R.font.outfit_extralight);
        Typeface regular = c.getResources().getFont(R.font.outfit_regular);
        Typeface medium = c.getResources().getFont(R.font.outfit_medium);

        layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(px(110), 0, px(110), 0);
        layout.setVisibility(View.GONE);
        root.addView(layout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Left: the time and the date, in the launcher's language.
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        layout.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextClock time = new TextClock(c);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 120);
        time.setTextColor(0xF2FFFFFF);
        time.setTypeface(extralight);
        time.setLetterSpacing(-0.04f);
        time.setIncludeFontPadding(false);
        time.setShadowLayer(12 * d, 0, 2 * d, 0x66000000);
        left.addView(time);
        date = new TextView(c);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        date.setTextColor(0xCCFFFFFF);
        date.setTypeface(regular);
        date.setLetterSpacing(0.14f);
        date.setAllCaps(true);
        date.setShadowLayer(8 * d, 0, 1 * d, 0x66000000);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.setMarginStart(px(6));
        dl.topMargin = px(8);
        left.addView(date, dl);

        // Right: the song card.
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(22), px(22), px(22), px(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xA6000000);
        bg.setCornerRadius(px(24));
        bg.setStroke(Math.max(1, px(1)), 0x26FFFFFF);
        card.setBackground(bg);
        layout.addView(card, new LinearLayout.LayoutParams(px(360), ViewGroup.LayoutParams.WRAP_CONTENT));

        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(0x33FFFFFF);
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 14 * d);
            }
        });
        cover.setClipToOutline(true);
        card.addView(cover, new LinearLayout.LayoutParams(px(316), px(316)));

        kicker = text(c, card, 12, 0x99FFFFFF, medium, 1, 18);
        kicker.setLetterSpacing(0.16f);
        kicker.setAllCaps(true);
        title = text(c, card, 24, 0xFFFFFFFF, medium, 2, 6);
        artist = text(c, card, 17, 0xB3FFFFFF, regular, 1, 4);

        progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(2 * d);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xE6FFFFFF);
        fill.setCornerRadius(2 * d);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{track,
                new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        progress.setProgressDrawable(layers);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(4));
        pl.topMargin = px(14);
        card.addView(progress, pl);
    }

    private int px(float dp) {
        return (int) (dp * d);
    }

    private TextView text(Context c, LinearLayout parent, int sp, int color, Typeface face, int lines, int top) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(top);
        parent.addView(t, lp);
        return t;
    }

    void start() {
        source.subscribe(changed);
        refresh();
    }

    void stop() {
        source.unsubscribe(changed);
        layout.removeCallbacks(move);
        layout.removeCallbacks(tick);
    }

    private void refresh() {
        if (!(source.hasTrack() && source.isPlaying())) {
            layout.removeCallbacks(move);
            layout.removeCallbacks(tick);
            layout.setVisibility(View.GONE);
            clockContent.setVisibility(View.VISIBLE);
            return;
        }
        String app = source.source();
        String state = local.getString(R.string.now_playing);
        date.setText(dateFormat.format(LocalDate.now()));
        kicker.setText(app.isEmpty() ? state : state + " · " + app);
        title.setText(source.title());
        artist.setText(source.artist());
        artist.setVisibility(source.artist().isEmpty() ? View.GONE : View.VISIBLE);
        if (source.art() != null) cover.setImageBitmap(source.art());
        else cover.setImageDrawable(null);
        updateProgress();
        clockContent.setVisibility(View.INVISIBLE);
        layout.removeCallbacks(tick);
        layout.postDelayed(tick, TICK_MS);
        if (layout.getVisibility() != View.VISIBLE) {
            layout.setVisibility(View.VISIBLE);
            layout.removeCallbacks(move);
            layout.postDelayed(move, MOVE_MS);
        }
    }

    private void updateProgress() {
        long dur = source.duration();
        progress.setVisibility(dur > 0 ? View.VISIBLE : View.INVISIBLE);
        if (dur > 0) progress.setProgress((int) (source.position() * 1000 / dur));
    }
}
