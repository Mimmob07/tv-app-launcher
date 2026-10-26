# Handoff: Hyrule Home on the living-room TV

For whoever picks this up next, person or coding agent. Read this first, then
[launcher/WIDGETS.md](launcher/WIDGETS.md). Identifiers (the TV's address, the network) are in
[HANDOFF.local.md](HANDOFF.local.md).

**This repository is private and must stay private**: it holds Nintendo's and Mojang's
screenshots, the TV's settings snapshot and the household's details. The launcher's signing key
(`launcher/release.keystore`) is deliberately not in git; see below.

## What this is

A fork of [dumbtv](https://github.com/humblemuzzu/dumbtv) (its history is kept; `main` here is
this work, started on a local branch named `hyrule-home`):

1. **Stage 1, done:** dumbtv's cleanup of the household's Fire TV Edition television (Insignia
   F50, Fire OS 8.1.8, model AFTHY7ABBA, codename ginza). Answers given: no Prime Video, no
   Alexa, DNS filter on, dumbtv home on, YouTube replaced by SmartTube, no apps removed.
   `verify.sh` passed before and after a reboot.
2. **Stage 2, in progress:** the launcher in `launcher/` turned into "Hyrule Home": a widget
   board on the top 80% of the screen and one scrolling row of apps and inputs below, with
   four themes (Hyrule, Landscape, Minecraft, Matrix). Same package (`tv.dumbtv.home`), so
   dumbtv's `verify.sh`, `launcher.sh` and `restore.sh` keep working.

## State on 2026-10-26

| | On the TV | In the repository |
|---|---|---|
| Launcher | current build, including Minecraft/Matrix themes and live signed M-Bus requests; `verify.sh` passed | matches the deployed source |
| Theme pictures | Hyrule, Landscape and Minecraft installed | same three folders |
| Settings | Matrix theme; time/weather/northbound CCTC arrivals/six-day forecast in two rows of three | `launcher/themes/home.conf` is the seed; ignored `home.local.conf` supplies M-Bus settings |

M-Bus credentials are in the ignored `launcher/themes/home.local.conf`, not in Java source
or the tracked seed config.

Validation: 113 Python script tests, four Java host tests (including request-signature and
UTC-date checks), shell syntax, catalog validation, and the release manifest passed. The
installer undo tests passed after its config-file change. Final on-TV `verify.sh`
passed with 46 packages disabled, working DNS blocks, personalDNSfilter VPN, bound HOME guard
and a successful HOME-key takeover. Screen captures confirmed the northbound bus layout and
six-day forecast in all four themes, with no launcher errors in logcat.

Normal sleep behavior is restored: `adb shell svc power stayon false` reset
`stay_on_while_plugged_in` to `0` after testing.

The screensaver is now `tv.dumbtv.home/.Screensaver`: the same live widgets and theme
background, filling the screen without the app dock or controls. The first remote press
dismisses it. The TV's five-minute idle and fourteen-minute sleep timers are unchanged.
`launcher/screensaver.sh` selects it and saves the previous selection on the TV;
`launcher/screensaver.sh --undo`, `launcher/install.sh --undo` and `scripts/restore.sh`
restore that selection. Native automatic idle startup and remote dismissal were tested.
The hourly weather strip reserves separate font-measured bands for time labels, icons and
temperatures, with a clear gap below each icon. Home and screensaver layouts were visually
checked in Hyrule, Landscape, Minecraft and Matrix after the spacing fix.

## Next steps

1. Future source updates can use the same installer (see HANDOFF.local.md for the address):
   ```sh
   adb connect <tv-ip>:5555
   ADB_SERIAL=<tv-ip>:5555 ./launcher/install.sh --build
   ```
   It updates in place (same signing key) and pushes all theme folders. The owner's settings
   on the TV are kept; an unchanged settings patch is not applied again.
2. M-Bus credentials are now in `launcher/themes/home.local.conf` (git-ignored), with the
   four-widget layout. The public map's key also requires its signing key and timestamped
   HMAC headers; see WIDGETS.md. `install.sh` prefers this local settings patch so it preserves
   the TV's theme. Copy the file separately when moving computers. Live API checks confirmed
   CCTC stops C250 (Chemistry) and C251 (Ruthven Museum). The owner now wants only northbound
   arrivals: the local patch sets `mbus.direction=Northbound`, and the wide card shows up to
   six arrivals in two columns of three on home, or eight in two columns of four in the
   screensaver (balanced when fewer are available). Narrow forecast
   cards now show six days in two rows of three.
3. Time zone: the TV is on America/Chicago (set automatically, probably from the building's
   network) while the household is in Ann Arbor (Eastern). The owner fixes it in the TV's
   settings (Amazon's `com.amazon.device.settings.action.TIME_ZONE` screen); the clock widget
   follows the TV.

## Things learned the hard way

- **ADB after a reboot:** on this TV (ginza), port 5555 sometimes stays closed after a reboot;
  the owner toggles Developer Options › ADB debugging off and on. It came back by itself the
  second time.
- **DNS filter on this model:** the TV has no Android backup service (`service check backup`:
  not found), so `dnsfilter.sh`'s `adb restore` path cannot work. personalDNSfilter was set up
  by driving its screen over adb (`input tap`, `uiautomator dump`): untick the three default
  lists, edit the `<new>` row to
  `file:///storage/emulated/0/Android/media/dnsfilter.android/dumbtv-blocklist.txt`, tick
  Active, OK, collapse the settings, RESTART. Its log then says "Building index for 416
  entries". The Fire TV keyboard moves focus to the next field on BACK; check every field with
  a dump before pressing OK.
- **Appstore:** on Fire OS 8 the Appstore's launcher entry forwards to a tab of Amazon's home,
  which the HOME takeover then replaced. The launcher now opens
  `com.amazon.venezia/.ade.ADEHomeActivity`, the store's own home, so HOME from the store
  works. Fire OS sends its HOME-key broadcast only to holders of an Amazon permission, so that
  route is closed.
- **Fire OS 8 and adb file access:** `/sdcard/Android/data/<pkg>` is closed to adb; everything
  the launcher reads from adb goes in `/sdcard/Android/media/tv.dumbtv.home/`.
- **Memory:** about 120 MB with a photo wallpaper (the full picture plus its blur), against 60
  to 70 MB for stock dumbtv home on a black background. The TV had about 750 MB free.

## Private files (upstream ignores them)

| Path | Why |
|---|---|
| `launcher/release.keystore` (**not in git**) | signs the APK. Copy it to a new computer by hand (AirDrop, USB, or a password manager's file attachment). Without it the next install needs `adb uninstall tv.dumbtv.home`, which wipes the launcher's settings on the TV |
| `launcher/themes/hyrule/`, `launcher/themes/minecraft/` | Nintendo's and Mojang's screenshots: fine on the owner's TV, not ours to publish |
| `launcher/themes/home.conf` | the household's settings, weather location included |
| `dumbtv.conf`, `report/` | dumbtv's answers, and the settings snapshot `scripts/restore.sh` needs to undo stage 1 exactly |
| `HANDOFF.local.md` | identifiers |

To start on another computer: `git clone https://github.com/Mimmob07/tv-app-launcher.git`,
install the toolchain below, then `adb connect` to the TV and press Allow on the TV for the new
computer. To keep dumbtv's updates within reach:
`git remote add upstream https://github.com/humblemuzzu/dumbtv`.

## Toolchain (macOS)

```sh
brew install openjdk@17 android-platform-tools
brew install --cask android-commandlinetools
sdkmanager "platforms;android-25" "build-tools;34.0.0"
./launcher/build.sh
```

Host-side tests (no device needed):

```sh
JAR=/opt/homebrew/share/android-commandlinetools/platforms/android-25/android.jar
javac --release 8 -nowarn -d /tmp/host -cp "$JAR" launcher/src/tv/dumbtv/home/*.java launcher/tests/*.java
for t in BoardTest RecentTest MeterTest MBusTest; do java -cp "/tmp/host:$JAR" tv.dumbtv.home.$t; done
```

An Android 11 emulator at 1920x1080, density 320 (`avdmanager` device `tv_1080p`), is close
enough to the TV to check layouts while it is off; push a `home.conf` with `theme=...` to its
media folder and bring the launcher back to the front.

## How the owner likes to work

- Asks for changes in stages and verifies each on the TV before the next.
- Says when the TV is off: then build and test locally, do not push.
- Wants new widgets to be quick to add: one class plus one line in `Widgets.java`
  (WIDGETS.md).
