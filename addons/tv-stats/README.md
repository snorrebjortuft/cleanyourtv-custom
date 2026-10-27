# TV-statistikk

A module card for the launcher that shows how the TV is doing and what it is used for, in Norwegian:

- **Akkurat nå (right now):** CPU load, memory, free storage and uptime. CPU is read from `/proc/stat` and falls
  back to the cores' cpuidle counters; when the TV hides both from apps (Android 8+ on many sets) the tile says so.
- **Seertid (watch time):** time per app for today, the last 7 days or the last 30 days, with bars. Today comes from
  Android's usage events (resume/pause pairs), longer spans from its daily totals. Home screens and Settings are left out.
- **Mest sett (most watched):** the shows, channels and artists that played the longest, counted from media sessions
  by a notification listener while they play and kept on the TV for 60 days (`WatchLog`).

## Permissions

No `INTERNET`: nothing leaves the TV. Two accesses are granted once over ADB:

```bash
adb shell appops set no.kongsnorre.tvstats GET_USAGE_STATS allow
adb shell cmd notification allow_listener no.kongsnorre.tvstats/.WatchListener
```

## Build

JDK 17 and Android SDK 34, as for the launcher: `./gradlew testReleaseUnitTest assembleRelease` in this folder
(the wrapper reuses the launcher's). Install the APK from `app/build/outputs/apk/release/` with `pm install`.
