# Widgets and themes

Hyrule Home is dumbtv home with a widget board and switchable themes. The top 80% of the
screen is the board; the bottom 20% is the dock, one scrolling row of apps followed by the
HDMI inputs, with the focused tile's name above it.

## Arranging widgets

The board is a grid of 12 columns by 6 rows. `home.conf` lists the widgets in order:

```ini
widgets=time:5x4,weather:7x4,forecast:12x2
```

| Entry | Means |
|---|---|
| `time` | the widget at its default size, packed into the first free spot |
| `time:5x4` | five columns by four rows |
| `memory:4x2+8+4` | four by two, pinned to column 8, row 4 (both count from 0) |

Pinned widgets claim their cells first; the rest fill the first spot they fit, scanning rows
top to bottom and columns left to right. A widget that fits nowhere, or an unknown id, is left
out and logged under the `dumbtv` tag.

On Fire OS 8, push a changed file to the media folder; it applies the next time the home
screen comes back:

```sh
adb push home.conf /sdcard/Android/media/tv.dumbtv.home/home.conf
```

| Id | Default | Shows | Settings |
|---|---|---|---|
| `time` | 5x4 | time, weekday, date | `clock=auto\|24h\|12h` |
| `weather` | 7x4 | conditions now and the next 18 hours | `weather.place`, `weather.lat`, `weather.lon`, `weather.units=f\|c` |
| `forecast` | 12x2 | up to six days; narrow, tall cards use two rows of three | the same as `weather` |
| `memory` | 4x2 | free memory | none |
| `mbus` | 7x3 | next University of Michigan buses at one stop, one column per direction | `mbus.key` (required), `mbus.signing-key` (website client), `mbus.stop`, `mbus.stops`, `mbus.direction`, `mbus.title`, `mbus.demo=on` |

The weather comes from [Open-Meteo](https://open-meteo.com) (free, no account, no key). It
refreshes every 15 minutes while the home screen is showing, never in the background, and the
last answer is kept on the TV so the widgets fill in at once after a reboot.

The buses come from the Magic Bus real-time system (Clever Devices BusTime API v3 at
mbus.ltp.umich.edu). Set `mbus.key` to a developer API key, or use the public map's client
key together with its `mbus.signing-key`. The website client requires `X-Date` and
`X-Request-ID` (HMAC-SHA256 of the API path, encoded query and UTC date); the token alone
returns "No API access permitted". Signed requests synchronize with the server's `gettime`
endpoint once per refresh, so an incorrect TV clock or time zone does not break authentication.
These client values were verified against the public map's
[JavaScript bundle](https://mbus.ltp.umich.edu/main.eb1163d0abab9c11.js) on 2026-10-26.

Keep credentials and the `widgets=` layout in `launcher/themes/home.local.conf` (git-ignored).
When that file exists, `install.sh` pushes it instead of the seed `themes/home.conf`.
Include only settings you want to change on an already configured TV: the launcher merges
those keys and preserves the owner's theme and other options. Copy this local file separately
when moving to another computer. A website update may rotate its client credentials.

On first use the widget looks through every route's
stops for names containing `mbus.stop` (default `Central Campus Transit Center|CCTC`), keeps
the stop ids it found, then asks for arrivals on the home screen's minute tick (with a
30-second freshness guard). `mbus.stops=C250,C251` skips the lookup and pins the verified
CCTC Chemistry and Ruthven Museum stops. `mbus.direction=Northbound` shows only northbound
arrivals, arranged in two columns of up to three buses each on a wide card. The earliest
arrivals read down the left column, then down the right; fewer than six are balanced between
the columns. Omit the direction filter to show both directions. `mbus.demo=on` shows made-up buses, marked SAMPLE,
for placing the widget before the key arrives. With the bus widget, a layout that fits
everything is `widgets=time:5x3,weather:7x3,mbus:7x3,forecast:5x3` (a 3-row weather card drops
its hourly strip).

Weather and buses are the launcher's only network use.

## Adding a widget

1. Make `src/tv/dumbtv/home/<Name>Widget.java`, a subclass of `Widget`:

   ```java
   final class ClockWidget extends Widget {
       ClockWidget(Board board) {
           super(board);
       }

       @Override
       void onMinute() {
           invalidate();
       }

       @Override
       protected void content(Canvas c, RectF area) {
           TextPaint p = paint(theme().display, 48, theme().text);
           c.drawText("Hello", area.left, area.centerY(), p);
       }
   }
   ```

2. Register it in `Widgets.java`: `add("clock", "Clock", 4, 2, ClockWidget::new);` (id, name,
   default columns and rows, constructor).
3. Add it to `widgets=` in `home.conf`, then `./launcher/install.sh --build`.

What a widget gets from `Widget`:

| | |
|---|---|
| the card | frosted glass over the wallpaper, its edge and the theme's ornaments are drawn for you; draw only inside `area` |
| `onShow()` / `onHide()` | the home screen came to the front or left it: start and stop timers or subscriptions here |
| `onMinute()` | once a minute while showing, and when the clock or time zone changes |
| `onConfig()` | `home.conf` or the theme changed |
| `theme()` | colors (`text`, `muted`, `faint`, `accent`, `line`, `glow`, `warm`, `cold`) and faces (`display`, `label`, `body`, `light`) |
| `cfg()` | `home.conf`: `cfg().get("mywidget.something", "default")` |
| `paint()`, `label()`, `fit()`, `shrink()`, `rule()`, `diamond()` | text in the theme's faces, small capitals, ellipsized text, fitting a size, a divider, an ornament |
| `padding()` | override for a tighter or looser card, in dp |

Data that several widgets share belongs in its own class, as `Weather` is for `weather` and
`forecast`: one request, many widgets. Anything that needs the network uses the INTERNET
permission the manifest already has. A widget that needs a new Android permission needs it in
`AndroidManifest.xml` and, on Fire OS, usually a grant over adb.

Widgets are not focusable yet: the remote moves through the dock. A widget that should react
to OK would need `setFocusable(true)` and a key listener, and the dock's focus search taught to
go up to it.

## Themes

`theme=hyrule`, `landscape`, `minecraft` or `matrix`, or Options › Theme.

| Theme | Cards | Type | Background |
|---|---|---|---|
| Hyrule | deep teal Sheikah-slate glass, cyan corner brackets and diamonds, gold focus brackets on tiles | Cinzel (OFL) | Breath of the Wild screenshots |
| Landscape | clean frosted glass, no ornaments, yellow focus ring | Roboto | landscape photographs from Wikimedia Commons, credited on screen |
| Minecraft | square dim panels with a pixel bevel, text with a hard shadow, white hotbar-style focus, blocky weather icons | Monocraft (OFL) | biome screenshots from minecraft.wiki |
| Matrix | black glass with green edges, green phosphor type | Share Tech Mono (OFL) | animated digital rain, drawn live (no pictures) |

The rain draws mirrored half-width katakana and digits from a 14 KB subset of Noto Sans JP
(OFL) at 20 frames a second into a half-resolution buffer, and stops whenever the home screen
leaves the front. The fonts' licenses are next to them in `assets/fonts/`.

A theme's pictures are not in the APK. `install.sh` pushes `launcher/themes/<id>/` to
`/sdcard/Android/media/tv.dumbtv.home/themes/<id>/`. They rotate following Options › Picture
rotation (hourly by default). A `credits.txt` next to them holds `file|credit` lines, plus an
optional `*|credit` for the rest; the credit for the picture on screen shows in the dock.

`launcher/themes/hyrule/` and `launcher/themes/minecraft/` hold Nintendo's and Mojang's screenshots:
fine on your own TV and in this private repository, not to be published.

To add a theme: its id and name in `Theme.IDS` and `Theme.NAMES`, a factory in `Theme.java` that
sets its colors, faces and style switches (`ornaments`, `bevel`, `shadow`, `pixel`, `rain`), a
case in `Theme.of`, a cover color in `Theme.cover`, and a `launcher/themes/<id>/` folder of
1920x1080 pictures (not needed for an animated background like `rain`).

`background=image` shows your own pictures from `wallpapers/` in the media folder instead, with
the theme's cards; `background=black` shows none.
