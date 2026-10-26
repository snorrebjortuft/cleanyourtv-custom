package no.kongsnorre.tvstats;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * TV-statistikk: CPU, memory, storage and uptime now, watch time per app for today, 7 or 30 days, and the shows
 * watched most. Opened from its card on Home. Reads nothing over the network.
 */
public final class StatsActivity extends Activity {

    private static final int BG = 0xFF0E1720, TILE = 0xFF1B2833, ACCENT = 0xFF5AA9E6, DIM = 0x99FFFFFF;
    private static final long NOW_MS = 2000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Device device = new Device();
    private HandlerThread worker;
    private Handler bg;
    private float d;
    private Typeface light, medium;
    private TextView cpu, cpuSub, mem, memSub, disk, diskSub, up;
    private volatile boolean cpuSeen;
    private volatile int cpuMisses;
    private LinearLayout appList, showList;
    private final TextView[] spans = new TextView[3];
    private int span = 0;

    private final Runnable nowTick = new Runnable() {
        @Override
        public void run() {
            Handler b = bg;
            if (b == null) return;
            b.post(() -> {
                int c = device.cpuPercent();
                long[] m = Device.memoryMb();
                File data = Environment.getDataDirectory();
                long free = data.getFreeSpace(), total = data.getTotalSpace();
                String u = Device.uptime();
                if (c >= 0) cpuSeen = true;
                else cpuMisses++;
                ui.post(() -> {
                    if (c >= 0) {
                        cpu.setText(c + " %");
                        cpuSub.setText("");
                    } else if (!cpuSeen && cpuMisses > 2) {
                        // Android 8 and later let many TVs hide every CPU counter from apps.
                        cpu.setText(getString(R.string.unknown));
                        cpuSub.setText(R.string.cpu_hidden);
                    }
                    if (m != null) {
                        mem.setText(String.format(Locale.ROOT, "%d %%", m[0] * 100 / Math.max(1, m[1])));
                        memSub.setText(m[0] + " av " + m[1] + " MB");
                    }
                    disk.setText(gb(free));
                    diskSub.setText("ledig av " + gb(total));
                    up.setText(u);
                });
            });
            ui.postDelayed(this, NOW_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(64), px(40), px(64), px(48));
        scroll.addView(col);
        setContentView(scroll);

        TextView title = text(col, getString(R.string.app_name), 34, Color.WHITE, light);
        title.setPadding(0, 0, 0, px(12));

        section(col, getString(R.string.now));
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(tiles, new LinearLayout.LayoutParams(-1, -2));
        TextView[] cs = new TextView[1];
        cpu = tile(tiles, getString(R.string.cpu), cs);
        cpuSub = cs[0];
        TextView[] m = new TextView[1];
        mem = tile(tiles, getString(R.string.memory), m);
        memSub = m[0];
        TextView[] s = new TextView[1];
        disk = tile(tiles, getString(R.string.storage), s);
        diskSub = s[0];
        up = tile(tiles, getString(R.string.uptime), null);

        section(col, getString(R.string.watch));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(row, new LinearLayout.LayoutParams(-2, -2));
        int[] names = {R.string.today, R.string.week, R.string.month};
        for (int i = 0; i < 3; i++) {
            final int which = i;
            TextView b = text(row, getString(names[i]), 18, Color.WHITE, medium);
            b.setPadding(px(22), px(10), px(22), px(10));
            b.setFocusable(true);
            b.setBackground(pill());
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
            lp.width = -2;
            lp.setMarginEnd(px(12));
            b.setOnClickListener(v -> choose(which));
            b.setOnFocusChangeListener((v, has) -> { if (has) choose(which); });
            spans[i] = b;
        }
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        col.addView(appList, new LinearLayout.LayoutParams(-1, -2));

        section(col, getString(R.string.top));
        showList = new LinearLayout(this);
        showList.setOrientation(LinearLayout.VERTICAL);
        col.addView(showList, new LinearLayout.LayoutParams(-1, -2));

        spans[0].requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        worker = new HandlerThread("tv-stats");
        worker.start();
        bg = new Handler(worker.getLooper());
        ui.post(nowTick);
        load();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacksAndMessages(null);
        bg = null;
        if (worker != null) worker.quitSafely();
        worker = null;
        super.onPause();
    }

    private void choose(int which) {
        for (int i = 0; i < 3; i++) spans[i].setSelected(i == which);
        if (which == span && appList.getChildCount() > 0) return;
        span = which;
        load();
    }

    private void load() {
        Handler b = bg;
        if (b == null) return;
        final int which = span;
        b.post(() -> {
            LocalDate today = LocalDate.now();
            int days = which == 0 ? 0 : which == 1 ? 6 : 29;
            LocalDate from = today.minusDays(days);
            long start = from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            boolean ok = AppUsage.granted(this);
            List<AppUsage.Row> apps = ok ? AppUsage.since(this, start, which == 0) : null;
            List<WatchLog.Entry> shows = WatchLog.top(WatchLog.prefs(this).getAll(), from, today, 10);
            ui.post(() -> {
                if (which != span) return;
                fillApps(apps);
                fillShows(shows);
            });
        });
    }

    private void fillApps(List<AppUsage.Row> rows) {
        appList.removeAllViews();
        if (rows == null) {
            note(appList, getString(R.string.no_usage));
            return;
        }
        if (rows.isEmpty()) {
            note(appList, getString(R.string.nothing));
            return;
        }
        long max = rows.get(0).seconds, sum = 0;
        for (AppUsage.Row r : rows) sum += r.seconds;
        TextView total = text(appList, getString(R.string.total, WatchLog.duration(sum)), 16, DIM, medium);
        total.setPadding(0, px(14), 0, px(4));
        PackageManager pm = getPackageManager();
        for (int i = 0; i < Math.min(rows.size(), 10); i++) {
            AppUsage.Row r = rows.get(i);
            barRow(appList, icon(pm, r.pkg), label(pm, r.pkg), WatchLog.duration(r.seconds), r.seconds / (float) max);
        }
    }

    private void fillShows(List<WatchLog.Entry> shows) {
        showList.removeAllViews();
        if (shows.isEmpty()) {
            note(showList, getString(R.string.no_shows));
            return;
        }
        PackageManager pm = getPackageManager();
        long max = shows.get(0).seconds;
        for (WatchLog.Entry e : shows) {
            barRow(showList, icon(pm, e.pkg), e.show + "  ·  " + label(pm, e.pkg), WatchLog.duration(e.seconds),
                    e.seconds / (float) max);
        }
    }

    /** Icon, name, a bar as long as its share of the top row, and the time. Focusable so the D-pad scrolls. */
    private void barRow(LinearLayout parent, Drawable icon, String name, String time, float share) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(12), px(8), px(16), px(8));
        row.setFocusable(true);
        row.setBackground(rowBg());
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = px(6);
        parent.addView(row, rl);

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(iv, new LinearLayout.LayoutParams(px(72), px(40)));

        TextView n = new TextView(this);
        n.setText(name);
        n.setTextColor(Color.WHITE);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        n.setTypeface(medium);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(420), -2);
        nl.setMarginStart(px(16));
        row.addView(n, nl);

        LinearLayout track = new LinearLayout(this);
        GradientDrawable t = new GradientDrawable();
        t.setColor(0x1FFFFFFF);
        t.setCornerRadius(px(4));
        track.setBackground(t);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, px(10), 1f);
        tl.setMarginStart(px(16));
        tl.setMarginEnd(px(16));
        row.addView(track, tl);
        View bar = new View(this);
        GradientDrawable b = new GradientDrawable();
        b.setColor(ACCENT);
        b.setCornerRadius(px(4));
        bar.setBackground(b);
        track.setWeightSum(1f);
        track.addView(bar, new LinearLayout.LayoutParams(0, -1, Math.max(0.02f, Math.min(1f, share))));

        TextView tv = new TextView(this);
        tv.setText(time);
        tv.setTextColor(DIM);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tv.setGravity(Gravity.END);
        row.addView(tv, new LinearLayout.LayoutParams(px(130), -2));
    }

    private TextView tile(LinearLayout parent, String label, TextView[] sub) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(px(20), px(16), px(20), px(16));
        GradientDrawable g = new GradientDrawable();
        g.setColor(TILE);
        g.setCornerRadius(px(14));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginEnd(px(14));
        parent.addView(t, lp);
        text(t, label.toUpperCase(Locale.getDefault()), 13, DIM, medium).setLetterSpacing(0.12f);
        TextView value = text(t, getString(R.string.unknown), 32, Color.WHITE, light);
        TextView s = text(t, "", 14, DIM, null);
        if (sub != null) sub[0] = s;
        return value;
    }

    private void section(LinearLayout parent, String name) {
        TextView t = text(parent, name.toUpperCase(Locale.getDefault()), 14, ACCENT, medium);
        t.setLetterSpacing(0.14f);
        t.setPadding(0, px(26), 0, px(10));
    }

    private void note(LinearLayout parent, String s) {
        TextView t = text(parent, s, 16, DIM, null);
        t.setPadding(0, px(10), 0, px(6));
    }

    private TextView text(ViewGroup parent, String s, int sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (face != null) t.setTypeface(face);
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private Drawable pill() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, round(0xFFFFFFFF & 0x59FFFFFF, px(22)));
        s.addState(new int[]{android.R.attr.state_selected}, round(0x3D5AA9E6 | 0x3D000000, px(22)));
        s.addState(new int[]{}, round(0x14FFFFFF, px(22)));
        return s;
    }

    private Drawable rowBg() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, round(0x26FFFFFF, px(12)));
        s.addState(new int[]{}, round(0x0AFFFFFF, px(12)));
        return s;
    }

    private static GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static Drawable icon(PackageManager pm, String pkg) {
        try {
            Drawable b = pm.getApplicationBanner(pkg);
            return b != null ? b : pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static String gb(long bytes) {
        return String.format(Locale.ROOT, "%.1f GB", bytes / 1e9).replace('.', ',');
    }

    private int px(int dp) {
        return (int) (dp * d);
    }
}
