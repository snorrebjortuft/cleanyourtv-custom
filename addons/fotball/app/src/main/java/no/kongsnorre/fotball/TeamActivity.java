package no.kongsnorre.fotball;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** One team: the whole league table on the left, the next match and every played match on the right. */
public final class TeamActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Ui u;
    private LinearLayout table, matches, head;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        u = new Ui(this);
        int team = Math.max(0, Math.min(2, getIntent().getIntExtra("team", 0)));
        Nifs.Team t = Nifs.TEAMS[team];

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(u.px(48), u.px(32), u.px(48), u.px(24));
        setContentView(root);
        head = u.column(root, new LinearLayout.LayoutParams(-1, -2));
        u.text(head, t.name, 34, Color.WHITE, u.light);
        u.text(head, getString(R.string.loading), 15, Ui.DIM, null);

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, 0, 1f);
        cl.topMargin = u.px(16);
        root.addView(cols, cl);

        table = scrollingPanel(cols, 1f, u.px(24));
        matches = scrollingPanel(cols, 1.1f, 0);

        new Thread(() -> {
            try {
                Nifs.Data d = new Nifs(this).load(t);
                ui.post(() -> render(d));
            } catch (Exception e) {
                ui.post(() -> {
                    head.removeViewAt(1);
                    u.text(head, getString(R.string.failed), 15, Ui.DIM, null);
                });
            }
        }, "nifs").start();
    }

    /** A panel inside a focusable ScrollView, so Left/Right picks a side and Up/Down scrolls it. */
    private LinearLayout scrollingPanel(LinearLayout parent, float weight, int marginEnd) {
        ScrollView s = new ScrollView(this);
        s.setFocusable(true);
        s.setBackground(u.panel(true));
        s.setPadding(u.px(20), u.px(14), u.px(20), u.px(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
        lp.setMarginEnd(marginEnd);
        parent.addView(s, lp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        s.addView(col);
        return col;
    }

    private void render(Nifs.Data d) {
        head.removeAllViews();
        LinearLayout r = u.row(head);
        u.logo(r, d.row != null ? d.row.logo : null, 56);
        LinearLayout names = u.column(r, new LinearLayout.LayoutParams(0, -2, 1f));
        names.setPadding(u.px(16), 0, 0, 0);
        u.text(names, d.team.name, 34, Color.WHITE, u.light);
        u.text(names, d.row == null ? d.team.league : d.team.league + " · "
                + getString(R.string.place, d.row.place, d.row.points, d.row.played), 16, Ui.DIM, null);

        u.heading(table, getString(R.string.table), Ui.RED);
        for (Nifs.Row row : d.table) u.tableRow(table, row, row == d.row, 16);

        u.heading(matches, getString(R.string.next), Ui.RED);
        if (d.next != null) u.match(matches, d.next, d.team.id, 16, true);
        else u.text(matches, getString(R.string.no_next), 15, Ui.DIM, null);
        u.heading(matches, getString(R.string.recent), Ui.RED);
        for (Nifs.Match m : d.played) u.match(matches, m, d.team.id, 15, true);
    }
}
