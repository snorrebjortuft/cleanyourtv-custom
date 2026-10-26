package no.kongsnorre.fotball;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The pieces both screens are built from: text, panels, match rows, table rows and result chips. */
final class Ui {

    static final int BG = 0xFF101418, PANEL = 0xFF1A2027, DIM = 0x99FFFFFF, RED = 0xFFEF0107;
    static final Locale NB = new Locale("nb", "NO");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d. MMM 'kl.' HH:mm", NB);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d. MMM", NB);

    final Context c;
    final float d;
    final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    Ui(Context c) {
        this.c = c;
        d = c.getResources().getDisplayMetrics().density;
    }

    int px(float dp) {
        return (int) (dp * d);
    }

    TextView text(ViewGroup parent, String s, float sp, int color, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (face != null) t.setTypeface(face);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    TextView heading(ViewGroup parent, String s, int color) {
        TextView t = text(parent, s.toUpperCase(NB), 13, color, medium);
        t.setLetterSpacing(0.14f);
        t.setPadding(0, px(12), 0, px(2));
        return t;
    }

    LinearLayout column(ViewGroup parent, LinearLayout.LayoutParams lp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        parent.addView(l, lp);
        return l;
    }

    LinearLayout row(ViewGroup parent) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(l, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    /** A rounded panel that lights up and grows a red edge when focused. */
    Drawable panel(boolean focusable) {
        GradientDrawable n = round(PANEL, px(18));
        if (!focusable) return n;
        GradientDrawable f = round(0xFF262E37, px(18));
        f.setStroke(px(3), RED);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, f);
        s.addState(new int[]{}, n);
        return s;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    ImageView logo(ViewGroup parent, String url, float dp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Logos.into(iv, url);
        parent.addView(iv, new LinearLayout.LayoutParams(px(dp), px(dp)));
        return iv;
    }

    /** S / U / T in green, grey or red. */
    TextView chip(ViewGroup parent, String outcome, float sp) {
        TextView t = new TextView(c);
        t.setText(outcome);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(medium);
        t.setGravity(Gravity.CENTER);
        int color = outcome.equals("S") ? 0xFF2E9E5B : outcome.equals("U") ? 0xFF6B7480 : 0xFFC0392B;
        t.setBackground(round(color, px(6)));
        int size = px(sp * 1.6f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(px(10));
        parent.addView(t, lp);
        return t;
    }

    /**
     * "Arsenal 2 – 1 Chelsea" with logos and the outcome chip. {@code showWhen} adds the kickoff time on the right,
     * for the next match and the full list; result rows in the small panels leave it out to keep room for names.
     */
    void match(ViewGroup parent, Nifs.Match m, int teamId, float sp, boolean showWhen) {
        LinearLayout r = row(parent);
        r.setPadding(0, px(2), 0, px(2));
        boolean played = m.homeScore != null;
        boolean live = played && m.time.plusHours(2).isAfter(OffsetDateTime.now());
        if (played && !live) chip(r, Nifs.outcome(m, teamId), sp);
        logo(r, m.homeLogo, sp * 1.5f);
        TextView home = text(r, m.home, sp, m.homeId == teamId ? Color.WHITE : DIM, m.homeId == teamId ? medium : null);
        home.setLayoutParams(weighted(0));
        home.setGravity(Gravity.END);
        TextView mid = text(r, played ? m.homeScore + "–" + m.awayScore : "–", sp + 1, Color.WHITE, medium);
        mid.setGravity(Gravity.CENTER);
        mid.setLayoutParams(new LinearLayout.LayoutParams(px(52), -2));
        TextView away = text(r, m.away, sp, m.awayId == teamId ? Color.WHITE : DIM, m.awayId == teamId ? medium : null);
        away.setLayoutParams(weighted(px(8)));
        logo(r, m.awayLogo, sp * 1.5f);
        if (live || showWhen) {
            // A played match only needs its day; the next one keeps weekday and kickoff time.
            String w0 = live ? c.getString(R.string.live) : played
                    ? m.time.atZoneSameInstant(ZoneId.systemDefault()).format(DAY) : when(m.time);
            TextView w = text(r, w0, sp - 3, live ? RED : DIM, live ? medium : null);
            w.setGravity(Gravity.END);
            w.setLayoutParams(new LinearLayout.LayoutParams(px(live ? 56 : played ? 80 : 170), -2));
        }
    }

    private LinearLayout.LayoutParams weighted(int marginEnd) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginStart(px(8));
        lp.setMarginEnd(marginEnd);
        return lp;
    }

    static String when(OffsetDateTime t) {
        String s = t.atZoneSameInstant(ZoneId.systemDefault()).format(WHEN);
        return s.substring(0, 1).toUpperCase(NB) + s.substring(1);
    }

    /** One table line: place, logo, name, played, goal difference, points; the team itself highlighted. */
    void tableRow(ViewGroup parent, Nifs.Row r, boolean mine, float sp) {
        LinearLayout l = row(parent);
        l.setPadding(px(10), px(4), px(10), px(4));
        if (mine) l.setBackground(round(0x40EF0107, px(8)));
        TextView p = text(l, r.place + ".", sp, DIM, null);
        p.setLayoutParams(new LinearLayout.LayoutParams(px(38), -2));
        logo(l, r.logo, sp * 1.5f);
        TextView n = text(l, r.name, sp, Color.WHITE, mine ? medium : null);
        n.setLayoutParams(weighted(0));
        cell(l, String.valueOf(r.played), sp, DIM);
        cell(l, (r.gd > 0 ? "+" : "") + r.gd, sp, DIM);
        cell(l, String.valueOf(r.points), sp, Color.WHITE);
    }

    private void cell(LinearLayout l, String s, float sp, int color) {
        TextView t = text(l, s, sp, color, color == Color.WHITE ? medium : null);
        t.setGravity(Gravity.END);
        t.setLayoutParams(new LinearLayout.LayoutParams(px(52), -2));
    }
}
