package com.gathin.tvhome;

import org.junit.Test;
import java.io.File;
import java.time.MonthDay;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WallpaperSeasonTest {
    private static final WallpaperSeason DECEMBER = new WallpaperSeason(MonthDay.of(12, 1), MonthDay.of(12, 31),
            new HashSet<>(Arrays.asList("xmas-1.jpg", "xmas-2.jpg")));
    private static final WallpaperSeason MAY_17 = new WallpaperSeason(MonthDay.of(5, 17), MonthDay.of(5, 17),
            Collections.singleton("17mai.jpg"));
    private static final List<WallpaperSeason> SEASONS = Arrays.asList(DECEMBER, MAY_17);
    private static final List<File> ALL = Arrays.asList(new File("001.jpg"), new File("xmas-1.jpg"),
            new File("xmas-2.jpg"), new File("17mai.jpg"), new File("002.jpg"));

    @Test public void rangesHoldTheirDays() {
        assertTrue(DECEMBER.contains(MonthDay.of(12, 1)));
        assertTrue(DECEMBER.contains(MonthDay.of(12, 31)));
        assertFalse(DECEMBER.contains(MonthDay.of(11, 30)));
        assertTrue(MAY_17.contains(MonthDay.of(5, 17)));
        assertFalse(MAY_17.contains(MonthDay.of(5, 18)));
    }

    @Test public void aRangeCanWrapTheNewYear() {
        WallpaperSeason yule = new WallpaperSeason(MonthDay.of(12, 20), MonthDay.of(1, 6), Collections.singleton("a.jpg"));
        assertTrue(yule.contains(MonthDay.of(12, 24)));
        assertTrue(yule.contains(MonthDay.of(1, 2)));
        assertFalse(yule.contains(MonthDay.of(1, 7)));
    }

    @Test public void anActiveSeasonPlaysOnlyItsPictures() {
        assertEquals(Arrays.asList(new File("xmas-1.jpg"), new File("xmas-2.jpg")),
                WallpaperSeason.active(ALL, SEASONS, MonthDay.of(12, 10)));
        assertEquals(Collections.singletonList(new File("17mai.jpg")),
                WallpaperSeason.active(ALL, SEASONS, MonthDay.of(5, 17)));
        assertNull(WallpaperSeason.active(ALL, SEASONS, MonthDay.of(10, 26)));
    }

    @Test public void outOfSeasonTheSeasonalPicturesStayOut() {
        assertEquals(Arrays.asList(new File("001.jpg"), new File("002.jpg")), WallpaperSeason.outOfSeason(ALL, SEASONS));
    }
}
