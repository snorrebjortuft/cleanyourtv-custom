package no.kongsnorre.fotball;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The card's screen: Arsenal large on the left (next match, last five, the table around them), Bodø/Glimt and Odd
 * on the right (place, next match, last three). OK on a team opens its whole table and every match (TeamActivity).
 * Fetches from NIFS when opened, and again every two minutes while it stays open.
 */
public final class FotballActivity extends Activity {

    private static final long REFRESH_MS = 120_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Ui u;
    private final LinearLayout[] panels = new LinearLayout[3];
    private TextView footer;
    private Thread loader;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            load();
            ui.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        u = new Ui(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(u.px(48), u.px(24), u.px(48), u.px(12));
        setContentView(root);

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(cols, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, 1.25f);
        left.setMarginEnd(u.px(24));
        panels[0] = panel(cols, left, 0);
        LinearLayout right = u.column(cols, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams top = new LinearLayout.LayoutParams(-1, 0, 1f);
        top.bottomMargin = u.px(16);
        panels[1] = panel(right, top, 1);
        panels[2] = panel(right, new LinearLayout.LayoutParams(-1, 0, 1f), 2);

        footer = u.text(root, getString(R.string.loading), 13, Ui.DIM, null);
        footer.setGravity(Gravity.END);
        footer.setPadding(0, u.px(10), 0, 0);
        panels[0].requestFocus();
    }

    private LinearLayout panel(LinearLayout parent, LinearLayout.LayoutParams lp, int team) {
        LinearLayout p = u.column(parent, lp);
        p.setPadding(u.px(28), u.px(16), u.px(28), u.px(14));
        p.setBackground(u.panel(true));
        p.setFocusable(true);
        p.setOnClickListener(v -> startActivity(new Intent(this, TeamActivity.class).putExtra("team", team)));
        Nifs.Team t = Nifs.TEAMS[team];
        u.text(p, t.name, team == 0 ? 34 : 26, Color.WHITE, u.light);
        u.text(p, getString(R.string.loading), 15, Ui.DIM, null);
        return p;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(refresh);
        if (loader != null) loader.interrupt();
        super.onPause();
    }

    private void load() {
        if (loader != null && loader.isAlive()) return;
        loader = new Thread(() -> {
            Nifs nifs = new Nifs(this);
            boolean anyCache = false, anyFail = false;
            for (int i = 0; i < 3; i++) {
                final int team = i;
                Nifs.Data d = null;
                for (int attempt = 0; attempt < 3 && d == null; attempt++) {
                    try {
                        d = nifs.load(Nifs.TEAMS[i]);
                    } catch (Exception e) {
                        android.util.Log.w("fotball", Nifs.TEAMS[i].name + ", attempt " + (attempt + 1), e);
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException stop) {
                            return;
                        }
                    }
                }
                if (d == null) {
                    anyFail = true;
                } else {
                    Nifs.Data done = d;
                    anyCache |= d.fromCache;
                    ui.post(() -> render(team, done));
                }
                if (Thread.interrupted()) return;
            }
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            String note = anyFail ? getString(R.string.failed) : anyCache ? getString(R.string.offline)
                    : getString(R.string.updated, time);
            ui.post(() -> footer.setText(note));
        }, "nifs");
        loader.start();
    }

    private void render(int team, Nifs.Data d) {
        LinearLayout p = panels[team];
        p.removeAllViews();
        boolean big = team == 0;
        LinearLayout head = u.row(p);
        u.logo(head, d.row != null ? d.row.logo : null, big ? 56 : 36);
        LinearLayout names = u.column(head, new LinearLayout.LayoutParams(0, -2, 1f));
        names.setPadding(u.px(14), 0, 0, 0);
        u.text(names, d.team.name, big ? 34 : 24, Color.WHITE, u.light);
        String place = d.row == null ? d.team.league
                : d.team.league + " · " + getString(R.string.place, d.row.place, d.row.points, d.row.played);
        u.text(names, place, big ? 16 : 13, Ui.DIM, null);

        if (big) {
            u.heading(p, getString(R.string.next), Ui.RED);
            if (d.next != null) {
                u.match(p, d.next, d.team.id, 17, false);
                u.text(p, Ui.when(d.next.time), 14, Ui.DIM, null).setGravity(android.view.Gravity.CENTER);
            } else {
                u.text(p, getString(R.string.no_next), 15, Ui.DIM, null);
            }
        } else {
            // One line in the small panels: "Neste: fre. 30. okt. kl. 19:00 · Viking (H)"
            String next = d.next == null ? getString(R.string.no_next) : getString(R.string.next) + ": "
                    + Ui.when(d.next.time) + " · " + (d.next.homeId == d.team.id ? d.next.away + " (H)" : d.next.home + " (B)");
            TextView n = u.text(p, next, 15, Color.WHITE, null);
            n.setPadding(0, u.px(10), 0, 0);
        }

        u.heading(p, getString(R.string.recent), Ui.RED);
        List<Nifs.Match> played = d.played;
        for (int i = 0; i < Math.min(played.size(), big ? 5 : 3); i++) {
            u.match(p, played.get(i), d.team.id, big ? 16 : 14, false);
        }

        if (big && d.row != null) {
            u.heading(p, getString(R.string.table), Ui.RED);
            int at = d.table.indexOf(d.row);
            int from = Math.max(0, Math.min(at - 3, d.table.size() - 7));
            for (int i = from; i < Math.min(d.table.size(), from + 7); i++) {
                u.tableRow(p, d.table.get(i), d.table.get(i) == d.row, 15);
            }
        }
    }
}
