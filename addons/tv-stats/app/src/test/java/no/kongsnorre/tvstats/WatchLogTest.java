package no.kongsnorre.tvstats;

import org.junit.Test;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public class WatchLogTest {

    @Test public void topSumsAShowAcrossDaysWithinTheSpan() {
        Map<String, Object> all = new HashMap<>();
        all.put(WatchLog.key(LocalDate.of(2026, 10, 25), "no.nrk.tv", "Exit"), 1800L);
        all.put(WatchLog.key(LocalDate.of(2026, 10, 26), "no.nrk.tv", "Exit"), 2400L);
        all.put(WatchLog.key(LocalDate.of(2026, 10, 26), "com.netflix.ninja", "Wednesday"), 3000L);
        all.put(WatchLog.key(LocalDate.of(2026, 10, 1), "no.nrk.tv", "Old"), 9999L);
        all.put("junk", 5L);
        List<WatchLog.Entry> top = WatchLog.top(all, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 26), 10);
        assertEquals(2, top.size());
        assertEquals("Exit", top.get(0).show);
        assertEquals(4200, top.get(0).seconds);
        assertEquals("Wednesday", top.get(1).show);
    }

    @Test public void durationsReadNorwegian() {
        assertEquals("under 1 min", WatchLog.duration(30));
        assertEquals("45 min", WatchLog.duration(45 * 60));
        assertEquals("2 t", WatchLog.duration(2 * 3600));
        assertEquals("2 t 15 min", WatchLog.duration(2 * 3600 + 15 * 60));
    }
}
