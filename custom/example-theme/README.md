# Example theme

The keys this fork adds, with every other theme key from [themes/README.md](../../themes/README.md):

| Key | What it does |
|---|---|
| `countdown_date`, `countdown_label` | "25 DAGER TIL JULAFTEN🎄" under the clock; after the date it counts to next year's |
| `countdown_party` | On that day: confetti, rising hearts and this text over Home, on the first Home of the day |
| `wallpaper_seasons` | `"MM-DD..MM-DD"` or `"MM-DD"` with picture names from `wallpapers/`: they take over the rotation on those days and stay out of it otherwise |
| `now_playing` | The now-playing card on Home and music mode in the screensaver; needs the notification listener approved once (see the root README) |

Copy this folder somewhere outside the repository (a theme is personal: your name, your dates, your pictures),
rename `config.example.json` to `config.json`, fill in your own values, and add a `wallpapers/` folder (1920x1080
JPEGs, for example free photos from Unsplash) with the names used in `wallpaper_moods` and `wallpaper_seasons`.
Then push it:

```powershell
tools\theme.ps1 -TvHost <tv ip> ..\my-theme
custom\push-all.ps1 -TvHost <tv ip> -Theme ..\my-theme     # or everything at once
```
