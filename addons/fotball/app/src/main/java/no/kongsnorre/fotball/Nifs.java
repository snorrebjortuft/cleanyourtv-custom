package no.kongsnorre.fotball;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tables, fixtures and results from NIFS (api.nifs.no, open, no key), for the league each team plays in this
 * season. Each answer is also kept in the app's cache folder, so the screen still shows the last data offline.
 */
final class Nifs {

    private static final String API = "https://api.nifs.no";

    /** The three teams: Arsenal (Premier League), Bodø/Glimt (Eliteserien), Odd (1. divisjon / OBOS-ligaen). */
    static final Team[] TEAMS = {
            new Team("Arsenal", 17, 7, "Premier League"),
            new Team("Bodø/Glimt", 8, 5, "Eliteserien"),
            new Team("Odd", 14, 6, "OBOS-ligaen"),
    };

    static final class Team {
        final String name, league;
        final int id, tournament;

        Team(String name, int id, int tournament, String league) {
            this.name = name;
            this.id = id;
            this.tournament = tournament;
            this.league = league;
        }
    }

    static final class Row {
        int place, played, won, draw, lost, gd, points, id;
        String name, logo;
    }

    static final class Match {
        OffsetDateTime time;
        String home, away, homeLogo, awayLogo;
        int homeId, awayId;
        Integer homeScore, awayScore;
    }

    static final class Data {
        Team team;
        Row row;
        final List<Row> table = new ArrayList<>();
        final List<Match> played = new ArrayList<>();   // newest first
        Match next;
        boolean fromCache;
    }

    private final File cache;
    private boolean usedCache;

    Nifs(Context c) {
        cache = new File(c.getCacheDir(), "nifs");
        //noinspection ResultOfMethodCallIgnored
        cache.mkdirs();
    }

    /** Fetches one team's season; throws when neither the network nor the cache has it. */
    Data load(Team t) throws IOException, JSONException {
        usedCache = false;
        JSONArray stages = new JSONArray(get("/tournaments/" + t.tournament + "/stages/"));
        int stage = currentStage(stages);
        JSONArray rows = new JSONObject(get("/stages/" + stage + "/table/")).getJSONArray("teams");
        JSONArray matches = new JSONArray(get("/stages/" + stage + "/matches/"));

        Data d = new Data();
        d.team = t;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.getJSONObject(i);
            Row r = new Row();
            r.id = o.optInt("id");
            r.name = o.optString("name");
            r.place = o.optInt("place");
            r.played = o.optInt("played");
            r.won = o.optInt("won");
            r.draw = o.optInt("draw");
            r.lost = o.optInt("lost");
            r.gd = o.optInt("goalDifference");
            r.points = o.optInt("points");
            r.logo = logo(o);
            d.table.add(r);
            if (r.id == t.id) d.row = r;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<Match> mine = new ArrayList<>();
        for (int i = 0; i < matches.length(); i++) {
            JSONObject o = matches.getJSONObject(i);
            JSONObject h = o.optJSONObject("homeTeam"), a = o.optJSONObject("awayTeam");
            if (h == null || a == null || (h.optInt("id") != t.id && a.optInt("id") != t.id)) continue;
            Match m = new Match();
            m.time = OffsetDateTime.parse(o.getString("timestamp"));
            m.home = h.optString("name");
            m.away = a.optString("name");
            m.homeId = h.optInt("id");
            m.awayId = a.optInt("id");
            m.homeLogo = logo(h);
            m.awayLogo = logo(a);
            JSONObject res = o.optJSONObject("result");
            if (res != null && !res.isNull("homeScore90") && !res.isNull("awayScore90")) {
                m.homeScore = res.getInt("homeScore90");
                m.awayScore = res.getInt("awayScore90");
            }
            mine.add(m);
        }
        Collections.sort(mine, (x, y) -> x.time.compareTo(y.time));
        for (Match m : mine) {
            if (m.homeScore != null && m.time.isBefore(now)) d.played.add(0, m);
            else if (d.next == null && m.time.plusHours(2).isAfter(now)) d.next = m;
        }
        d.fromCache = usedCache;
        return d;
    }

    /** The newest league stage: highest yearStart, then highest id. */
    static int currentStage(JSONArray stages) throws JSONException {
        int best = -1, bestYear = Integer.MIN_VALUE;
        for (int i = 0; i < stages.length(); i++) {
            JSONObject s = stages.getJSONObject(i);
            int year = s.optInt("yearStart", 0), id = s.getInt("id");
            if (year > bestYear || (year == bestYear && id > best)) {
                bestYear = year;
                best = id;
            }
        }
        if (best < 0) throw new JSONException("no stage");
        return best;
    }

    private static String logo(JSONObject team) {
        JSONObject l = team.optJSONObject("logo");
        return l == null ? null : l.optString("url", null);
    }

    /** The answer from NIFS, saved to the cache; the cached copy when the network fails. */
    private String get(String path) throws IOException {
        File f = new File(cache, path.replaceAll("[^A-Za-z0-9]", "_"));
        try {
            String body = new String(fetch(API + path), StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return body;
        } catch (IOException e) {
            if (!f.exists()) throw e;
            usedCache = true;
            try (FileInputStream in = new FileInputStream(f)) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        }
    }

    static byte[] fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                return readAll(in);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** "S", "U" or "T" (seier, uavgjort, tap) for {@code teamId} in a played match. */
    static String outcome(Match m, int teamId) {
        int mine = m.homeId == teamId ? m.homeScore : m.awayScore;
        int theirs = m.homeId == teamId ? m.awayScore : m.homeScore;
        return mine > theirs ? "S" : mine == theirs ? "U" : "T";
    }
}
