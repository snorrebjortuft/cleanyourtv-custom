# Fotball stats

A module card with three teams' seasons on one screen: the main team large on the left (next match, last five
results with win/draw/loss chips, the table around it) and two more on the right (place, points, next match, last
three). OK on a team opens its whole league table and every match. Club crests come with the data.

The teams are Arsenal (Premier League), Bodø/Glimt (Eliteserien) and Odd (OBOS-ligaen); change `Nifs.TEAMS` for
others. The current season is found by itself each year (`Nifs.currentStage`).

## Data

[NIFS](https://api.nifs.no) (Norsk Internasjonal Fotballstatistikk), open and keyless: `/tournaments/{id}/stages/`,
`/stages/{id}/table/` and `/stages/{id}/matches/`. Fetched only while the screen is open, every two minutes, with
three tries per team; each answer is cached on the TV so the screen still shows the last data offline. League
matches only, no cups or European games.

Permissions: `INTERNET`, used for NIFS only.

## Build

`./gradlew testReleaseUnitTest assembleRelease` in this folder (JDK 17, Android SDK 34).

The card's picture is a drawn pitch. For your club's colours, put a 640x360 `banner.png` in
`app/src/main/res/drawable-nodpi/` and delete `res/drawable/banner.xml`; club badges are trademarks, so keep that
picture out of a public repository.
