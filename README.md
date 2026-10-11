# F1 Schedule Pro

The Formula 1 season on your phone: every session in your time zone, live countdowns, standings, news, reminders before sessions start, and home-screen widgets. A native Android app (Kotlin + Jetpack Compose).

- **Schedule**: every round of the season with Done, Live and Next markers; the current race is opened straight away. Tap a race for its sessions, a countdown to the next one, circuit stats (length, turns, lap record) and, once a session is over, a link to its results on formula1.com. Swipe left and right between races. Pull down to refresh.
- **Live header**: a running clock in your chosen time zone and, under it, either the session on track right now or a flip-clock countdown to the next one.
- **Standings**: drivers and constructors, with the gap to the leader. Tap a driver or team for their formula1.com page.
- **News**: the latest from RACER, Autosport, Motorsport.com and The Race in one list, sortable by newest, oldest or source, plus quick links to F1 TV, YouTube, Reddit and the FIA rules.
- **Reminders**: 15 minutes, 30 minutes or an hour before every session, only qualifying and races, or only races. They're scheduled on the phone with exact alarms, so they arrive with the app closed and need no server.
- **Widgets**: a 2×2 countdown to the next session (it ticks by itself in the last 24 hours) and a 4×2 card with the whole race weekend.
- **Settings**: Dark, Light or System theme; your time zone or a fixed one; 12- or 24-hour clock; haptics on or off.
- **Works offline**: calendar, standings and news are kept on the phone and shown straight away.

## Data

| Source | Used for |
| --- | --- |
| [Jolpica F1 API](https://github.com/jolpica/jolpica-f1) (Ergast successor) | Calendar and session times, this season and next |
| [f1api.dev](https://f1api.dev) | Driver and constructor standings |
| RACER, Autosport, Motorsport.com, The Race (RSS) | News |

The calendar refreshes every 6 hours and when the app opens. A copy ships inside the app (and is refreshed from Jolpica on every build), so a fresh install shows the real calendar even offline. When a season ends, the next one takes over as soon as it's published.

## Install and updates

Every push to `main` builds a signed APK and publishes it as a GitHub Release (`v2.0.N`). Open the latest release on your phone and tap `f1pro.apk`.

After that the app updates itself: it checks GitHub when it opens, when you pull down on the schedule, and about every 6 hours in the background. When a new version is out, a banner shows at the top (and a notification). Tap it to download, tap again to install; it installs over the old one and keeps your settings.

Builds are signed with one key kept in the repo's Actions secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`), never in the code.

## Layout

```
app/src/main/java/app/f1pro/
  data/     feeds, parsers, calendar logic (Planner), on-disk cache
  ui/       Compose screens, theme, haptics, flags
  widget/   Glance widgets
  work/     reminder alarms (Ticker), background refresh, updater
app/src/main/assets/   bundled calendar, circuit stats, official names
```

Unit tests cover the parsers, the calendar and the reminder timing (`./gradlew testReleaseUnitTest`). CI also runs a live check against every feed and adds the result to the release notes.

## History

This started as a PWA (React + Vite, with a small web-push server). Its last version is kept on the [`pwa-final`](../../tree/pwa-final) branch.

## Credits

Icons from [Lucide](https://lucide.dev) (ISC), flags from [country-flag-icons](https://gitlab.com/catamphetamine/country-flag-icons) (MIT), fonts Orbitron and Inter (SIL OFL); licences in `licenses/`. Not affiliated with Formula 1.
