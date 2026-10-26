package no.kongsnorre.tvstats;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * CPU, memory and uptime, read the way the launcher's stats line does: /proc/stat where the TV allows it, else
 * the cores' cpuidle times (Android 8 and later hide /proc/stat from apps on many TVs).
 */
final class Device {

    private long totalPrev = -1, idlePrev = -1, wallPrev = -1, cpuIdlePrev = -1;
    private boolean statDead;

    /** Busy percent since the last call; -1 until a second sample. */
    int cpuPercent() {
        if (!statDead) {
            int v = fromProcStat();
            if (v != -2) return v;
            statDead = true;
        }
        return fromIdle();
    }

    private int fromProcStat() {
        String s = firstLine("/proc/stat");
        if (s == null || !s.startsWith("cpu")) return -2;
        String[] t = s.trim().split("\\s+");
        if (t.length < 5) return -2;
        try {
            long total = 0;
            for (int i = 1; i < Math.min(t.length, 8); i++) total += Long.parseLong(t[i]);
            long idle = Long.parseLong(t[4]) + (t.length > 5 ? Long.parseLong(t[5]) : 0);
            if (totalPrev < 0) {
                totalPrev = total;
                idlePrev = idle;
                return -1;
            }
            long dt = total - totalPrev, di = idle - idlePrev;
            totalPrev = total;
            idlePrev = idle;
            return dt <= 0 ? -1 : clamp((int) Math.round((dt - di) * 100.0 / dt));
        } catch (NumberFormatException e) {
            return -2;
        }
    }

    private int fromIdle() {
        long idle = 0;
        int cores = 0;
        for (int i = 0; i < 8; i++) {
            boolean any = false;
            for (int st = 0; st < 8; st++) {
                String v = firstLine("/sys/devices/system/cpu/cpu" + i + "/cpuidle/state" + st + "/time");
                if (v == null) break;
                try {
                    idle += Long.parseLong(v.trim());
                    any = true;
                } catch (NumberFormatException e) {
                    break;
                }
            }
            if (!any) break;
            cores++;
        }
        if (cores == 0) return -1;
        long wall = SystemClock.elapsedRealtime() * 1000L;
        if (cpuIdlePrev < 0) {
            cpuIdlePrev = idle;
            wallPrev = wall;
            return -1;
        }
        long di = idle - cpuIdlePrev, dw = (wall - wallPrev) * cores;
        cpuIdlePrev = idle;
        wallPrev = wall;
        return dw <= 0 ? -1 : clamp((int) Math.round(100 - di * 100.0 / dw));
    }

    /** {used MB, total MB} from MemTotal and MemAvailable, or null. */
    static long[] memoryMb() {
        long total = -1, avail = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null && (total < 0 || avail < 0)) {
                if (line.startsWith("MemTotal:")) total = kb(line);
                else if (line.startsWith("MemAvailable:")) avail = kb(line);
            }
        } catch (IOException e) {
            return null;
        }
        if (total <= 0 || avail < 0) return null;
        return new long[]{(total - avail) / 1024, total / 1024};
    }

    private static long kb(String line) {
        try {
            return Long.parseLong(line.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "3 d 4 t", "5 t 12 min", "12 min". */
    static String uptime() {
        long min = SystemClock.elapsedRealtime() / 60_000;
        long d = min / 1440, h = (min / 60) % 24, m = min % 60;
        if (d > 0) return d + " d " + h + " t";
        if (h > 0) return h + " t " + m + " min";
        return m + " min";
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static String firstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
