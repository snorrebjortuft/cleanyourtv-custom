# Puts this fork on an Android TV in one go: the launcher, a theme, the add-ons, the access they need, and the
# clock screensaver. Switch USB debugging (or network debugging) on first and accept the prompt on the TV.
#
# Usage (PowerShell, from the repository root):
#   custom\push-all.ps1 -TvHost <tv ip> -Theme <your theme folder> [-RemoveSources]
# -Theme is your own theme folder (see custom\example-theme\README.md). Afterwards answer "Show card" on the TV for each new card,
# then switch debugging off again (README, "Close ADB afterwards").
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [Parameter(Mandatory = $true)][string]$Theme,
    [switch]$RemoveSources
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
$env:TV_HOST = $TvHost
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
function Tv([string]$cmd) { & $py -I (Join-Path $repo 'tools\tv.py') $cmd }

Write-Host '== Connecting (accept "Allow USB debugging?" on the TV if it asks)'
& $py -I tools\tv.py --probe | Select-Object -Last 2

Write-Host '== Launcher'
& .\launcher\build-and-install.ps1 -TvHost $TvHost | Select-Object -Last 2
Set-Location $repo

Write-Host "== Theme $Theme"
& .\tools\theme.ps1 -TvHost $TvHost $Theme | Select-String 'Theme'

Write-Host '== Add-ons'
foreach ($a in 'tv-stats', 'fotball', 'switch', 'photo-screensaver') {
    Set-Location (Join-Path $repo "addons\$a")
    & .\gradlew.bat --console=plain -q assembleRelease
    Set-Location $repo
    & $py -I tools\b64push.py "addons\$a\app\build\outputs\apk\release\app-release.apk" /data/local/tmp/addon.apk | Select-Object -Last 1
    Write-Host "$a`: $(Tv 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk')"
}

Write-Host '== Access'
Tv 'cmd notification allow_listener com.gathin.tvhome/.MediaListener'
Tv 'cmd notification allow_listener no.kongsnorre.tvstats/.WatchListener'
Tv 'appops set no.kongsnorre.tvstats GET_USAGE_STATS allow'
Tv 'appops set com.gathin.photodream AUTO_START allow 2>/dev/null; appops set com.gathin.photodream APP_AUTO_START allow 2>/dev/null; true' | Out-Null
if ($RemoveSources) {
    # Sources is a sideloaded add-on, not a system package, so it can simply be removed.
    Write-Host "Sources: $(Tv 'pm uninstall io.github.maxhayim.sources 2>&1')"
}
Tv 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
Tv 'input keyevent HOME'

Write-Host ''
Write-Host 'Done. On the TV: answer "Show card" for the new cards. Then switch debugging off:'
Write-Host '  Developer options > Revoke USB debugging authorisations, USB debugging off, restart the TV,'
Write-Host "  and check with: tools\venv\Scripts\python.exe tools\adb-closed.py $TvHost"
