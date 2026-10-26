package no.kongsnorre.tvstats;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seconds watched per day, app and show, kept on the TV only (SharedPreferences "watch"), 60 days back.
 * A key is "2026-10-26\tno.nrk.tv\tShow name".
 */
final class WatchLog {

    static final int KEEP_DAYS = 60;

    static final class Entry {
        final String pkg, show;
        long seconds;

        Entry(String pkg, String show, long seconds) {
            this.pkg = pkg;
            this.show = show;
            this.seconds = seconds;
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    static String key(LocalDate day, String pkg, String show) {
        return day + "\t" + pkg + "\t" + show.replace('\t', ' ');
    }

    static void add(SharedPreferences p, LocalDate day, String pkg, String show, long seconds) {
        if (seconds <= 0 || pkg.isEmpty() || show.isEmpty()) return;
        String k = key(day, pkg, show);
        p.edit().putLong(k, p.getLong(k, 0) + seconds).apply();
    }

    /** The shows watched from {@code from} to {@code to} (both included), most seconds first, at most {@code n}. */
    static List<Entry> top(Map<String, ?> all, LocalDate from, LocalDate to, int n) {
        Map<String, Entry> sums = new HashMap<>();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            String[] parts = e.getKey().split("\t", 3);
            if (parts.length < 3 || !(e.getValue() instanceof Long)) continue;
            LocalDate day;
            try {
                day = LocalDate.parse(parts[0]);
            } catch (RuntimeException bad) {
                continue;
            }
            if (day.isBefore(from) || day.isAfter(to)) continue;
            String id = parts[1] + "\t" + parts[2];
            Entry s = sums.get(id);
            if (s == null) sums.put(id, s = new Entry(parts[1], parts[2], 0));
            s.seconds += (Long) e.getValue();
        }
        List<Entry> list = new ArrayList<>(sums.values());
        Collections.sort(list, (a, b) -> Long.compare(b.seconds, a.seconds));
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    static void prune(SharedPreferences p, LocalDate today) {
        LocalDate oldest = today.minusDays(KEEP_DAYS);
        SharedPreferences.Editor ed = null;
        for (String k : p.getAll().keySet()) {
            String day = k.split("\t", 2)[0];
            try {
                if (!LocalDate.parse(day).isBefore(oldest)) continue;
            } catch (RuntimeException bad) {
                // unreadable keys go too
            }
            if (ed == null) ed = p.edit();
            ed.remove(k);
        }
        if (ed != null) ed.apply();
    }

    /** "2 t 15 min", "45 min", "under 1 min". */
    static String duration(long seconds) {
        long min = seconds / 60;
        if (min < 1) return "under 1 min";
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " t" : h + " t " + m + " min";
    }
}
