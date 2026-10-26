package no.kongsnorre.tvstats;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Time each TV app was on screen, from Android's usage stats (granted once over ADB with appops). */
final class AppUsage {

    static final class Row {
        final String pkg;
        final long seconds;

        Row(String pkg, long seconds) {
            this.pkg = pkg;
            this.seconds = seconds;
        }
    }

    static boolean granted(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        return ops != null && ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** Apps with a TV launcher entry, minus home screens and this app: what counts as watching. */
    private static Set<String> tvApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> apps = new HashSet<>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) apps.add(r.activityInfo.packageName);
        apps.remove(c.getPackageName());
        apps.remove("com.gathin.tvhome");
        apps.remove("com.android.tv.settings");   // setting things up is not watching
        return apps;
    }

    /** From {@code start} until now, most time first. Today uses the exact events; longer spans the daily totals. */
    static List<Row> since(Context c, long start, boolean exact) {
        UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
        if (usm == null) return Collections.emptyList();
        long now = System.currentTimeMillis();
        Set<String> apps = tvApps(c);
        Map<String, Long> ms = new HashMap<>();
        if (exact) {
            Map<String, Long> open = new HashMap<>();
            UsageEvents events = usm.queryEvents(start, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                String p = e.getPackageName();
                if (!apps.contains(p)) continue;
                int type = e.getEventType();
                if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (!open.containsKey(p)) open.put(p, e.getTimeStamp());
                } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                    Long t = open.remove(p);
                    if (t != null) ms.put(p, ms.getOrDefault(p, 0L) + e.getTimeStamp() - t);
                }
            }
            for (Map.Entry<String, Long> o : open.entrySet()) {
                ms.put(o.getKey(), ms.getOrDefault(o.getKey(), 0L) + now - o.getValue());
            }
        } else {
            Map<String, UsageStats> agg = usm.queryAndAggregateUsageStats(start, now);
            for (Map.Entry<String, UsageStats> u : agg.entrySet()) {
                if (apps.contains(u.getKey())) ms.put(u.getKey(), u.getValue().getTotalTimeInForeground());
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Long> m : ms.entrySet()) {
            if (m.getValue() >= 60_000) rows.add(new Row(m.getKey(), m.getValue() / 1000));
        }
        Collections.sort(rows, (a, b) -> Long.compare(b.seconds, a.seconds));
        return rows;
    }
}
