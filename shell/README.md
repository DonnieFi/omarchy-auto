# Shell

> **Linux/future path.** Android v1 lives in the repository root (`core/`,
> `auto/`, `launcher/`). This QML shell is retained as the Quickshell/Linux
> reference and is not part of either APK.

A plain Qt 6 / Qt Quick prototype of the Omarchy Auto five-tile shell. It uses
only Qt Quick primitives (`Item`, `Rectangle`, `Text`, `Canvas`, `Layouts`), so
it runs with `qmlscene` on a desktop or on the ARMv7 tablet. The Quickshell
wrapper is optional.

## Run

```sh
./run.sh
```

or explicitly:

```sh
qmlscene main.qml
```

On the tablet:

```sh
QT_QUICK_BACKEND=software /usr/lib/qt6/bin/qmlscene main.qml
```

`run.sh` sets the software backend automatically on ARMv6/ARMv7. The explicit
environment variable is useful when invoking `qmlscene` by hand or through
Quickshell.

On a machine with no QML runtime but a working PySide6 install:

```sh
python3 -m venv ../.venv
../.venv/bin/pip install -r requirements.txt
../.venv/bin/python preview.py
```

`preview.py` opens the same `main.qml` in a `QQuickView`. It can also render a
frame without a display:

```sh
../.venv/bin/python preview.py --screenshot /tmp/shell.png
../.venv/bin/python preview.py --size 800x1280 --screenshot /tmp/portrait.png
../.venv/bin/python preview.py --expanded Media --screenshot /tmp/media.png
```

On a Quickshell/Hyprland host:

```sh
qs quickshell.qml
```

## Layout

| Path | Purpose |
|---|---|
| `CarShell.qml` | Root layout: top bar, five-tile grid, footer, expanded overlay |
| `main.qml` | `qmlscene` entry point, 1280×800 |
| `preview.py` | PySide6 `QQuickView` runner and headless screenshot tool |
| `requirements.txt` | Optional PySide6 host dependency |
| `quickshell.qml` | Quickshell `FloatingWindow` wrapper |
| `theme/Theme.qml` | Palette singleton, mono font, spacing, theme cycle |
| `components/TopBar.qml` | Time, weather, connectivity, volume, theme button |
| `components/Tile.qml` | 2px bordered tile with a btop-style cut-out legend |
| `plugins/DriveTile.qml` | Mock map, route, next maneuver, ETA |
| `plugins/MediaTile.qml` | Mock album art, transport, progress |
| `plugins/CommsTile.qml` | Mock notification list and quick reply |
| `plugins/VehicleTile.qml` | Mock speed, RPM, coolant, fuel, range |
| `plugins/HomeTile.qml` | Mock weather, theme chips, garage/camera buttons |

## Current behavior

- Click the `↗` on a tile to expand it; click outside or `← BACK` to close.
- The top bar highlights the active tile and names it next to `OMARCHY`.
- Click the `◐` in the top bar to cycle themes.
- The Home tile also switches themes.
- All data is fake. The footer says `MOCK DATA`; nothing touches the network,
  D-Bus, or the phone yet.

## Validation

Validated on 2026-11-01 with Qt 6.12 (PySide6 6.12, software backend,
offscreen):

- `main.qml` loads with no QML warnings at 1280×800 and 800×1280.
- The five tiles, footer, theme switch, and expanded overlay render.
- 1280×800 uses four equal columns: Drive spans two columns and two rows; the
  other four tiles form a 2×2 block. 800×1280 uses two columns with Drive full
  width. Dynamic resize between the two relayouts cleanly.
- Scripted pointer checks pass: expand, click-outside close, back close, and
  theme-button palette change.
- `pyside6-qmllint` reports zero warnings or errors for the shell, components,
  and plugins.
- Not yet validated on the Tab 4's Qt 6.11 build or with Quickshell; the tablet
  was offline during the last check. Run `./run.sh` there before trusting the
  layout.

## Next shell steps

1. Critique the 1280×800 layout, then test 800×1280 portrait on the tablet.
2. Enlarge the expand, play, and theme hit areas to the 64dp driving minimum.
   They are demo-sized in this pass.
3. Give the expanded Media, Comms, and Home tiles dedicated detail layouts;
   they currently reuse the small tile at full size and look sparse.
4. Add a `state` object per tile so fake data can be swapped for live data.
5. Replace the mock `Canvas` map with phone GPS + a raster tile layer.
6. Add a `dayNight` palette and a brightness control.
7. Move the shell into a Quickshell layer-shell surface for the car build.
