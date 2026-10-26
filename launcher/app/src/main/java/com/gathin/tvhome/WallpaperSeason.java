package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * wallpaper_seasons: pictures that take over the rotation on given days of every year, such as all of December or
 * 17 May. A theme writes {"12-01..12-31": ["xmas-1.jpg", ...], "05-17": ["flag.jpg"]}; a range may wrap the new
 * year ("12-20..01-06"). Outside its days a season's pictures stay out of the normal rotation.
 */
final class WallpaperSeason {

    final MonthDay from, to;
    final Set<String> names;

    WallpaperSeason(MonthDay from, MonthDay to, Set<String> names) {
        this.from = from;
        this.to = to;
        this.names = names;
    }

    boolean contains(MonthDay day) {
        if (!from.isAfter(to)) return !day.isBefore(from) && !day.isAfter(to);
        return !day.isBefore(from) || !day.isAfter(to);   // wraps the new year
    }

    /** {"MM-DD..MM-DD": [names], "MM-DD": [names]}; unreadable keys and non-image names are left out. */
    static List<WallpaperSeason> parse(JSONObject o) {
        if (o == null) return Collections.emptyList();
        List<WallpaperSeason> seasons = new ArrayList<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String key = it.next();
            String[] ends = key.split("\\.\\.", 2);
            MonthDay from = day(ends[0]), to = day(ends.length > 1 ? ends[1] : ends[0]);
            JSONArray list = o.optJSONArray(key);
            if (from == null || to == null || list == null) continue;
            Set<String> names = new HashSet<>();
            for (int i = 0; i < list.length(); i++) {
                String name = list.optString(i, "").trim();
                if (ThemeFiles.collectionImage(name)) names.add(name);
            }
            if (!names.isEmpty()) seasons.add(new WallpaperSeason(from, to, Collections.unmodifiableSet(names)));
        }
        return Collections.unmodifiableList(seasons);
    }

    private static MonthDay day(String mmdd) {
        try {
            return MonthDay.parse("--" + mmdd.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** The first season holding {@code day} that has pictures in {@code all}, or null. */
    static List<File> active(List<File> all, List<WallpaperSeason> seasons, MonthDay day) {
        for (WallpaperSeason s : seasons) {
            if (!s.contains(day)) continue;
            List<File> group = new ArrayList<>();
            for (File f : all) if (s.names.contains(f.getName())) group.add(f);
            if (!group.isEmpty()) return group;
        }
        return null;
    }

    /** {@code all} without any season's pictures, or {@code all} itself when nothing would be left. */
    static List<File> outOfSeason(List<File> all, List<WallpaperSeason> seasons) {
        if (seasons.isEmpty()) return all;
        List<File> rest = new ArrayList<>();
        outer:
        for (File f : all) {
            for (WallpaperSeason s : seasons) if (s.names.contains(f.getName())) continue outer;
            rest.add(f);
        }
        return rest.isEmpty() ? all : rest;
    }
}
