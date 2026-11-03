# Omarchy Auto

**An Omarchy-styled car UI for Android Auto and Android phones.** The car
display is the primary target: a Car App Library **NavigationTemplate** app that
draws a right-side rail (`Drive / Media / Comms / Home`) plus one large stage on
the Android Auto map surface. The Vehicle tile is hidden until its data source
is real. The same Kotlin Canvas renderer powers a full-screen phone launcher.

**Public pre-alpha site:** [omarchyauto.com](https://omarchyauto.com)

![Omarchy Auto rail-and-stage UI over Tom Ballard's Tokyo Studio artwork](docs/img/hero-tokyo-night.webp)

*Omarchy Auto with Tom Ballard's Tokyo Studio artwork, credited in
[`CREDITS.md`](CREDITS.md). Synthetic layout render, not real-car footage.
Project code is MIT-licensed; the artwork is third-party and credited
separately.*

## Status — public pre-alpha (2026-11-02)

Omarchy Auto is a pre-alpha project prepared for public source release. The
phone launcher and Android Auto dashboard are usable on a test device. The
routing alpha in `navigation-spike/` is a separate experimental app and is not
part of the dashboard release yet. The old Linux/Quickshell path remains in
`shell/` and the docs archive as future work.

Built and checked on this machine:

- `./gradlew assembleDebug` produces the dashboard APKs. The optional routing
  spike builds separately as `:navigation-spike:assembleDebug`.
- Phone launcher runs on an Android 15 emulator and a Samsung S24 FE (Android
  17). Theme switch, live MediaSession, live notifications, a GPS fix, stage
  selection, tile launch, Back return, and portrait/landscape rotation were
  exercised at 1280×720 and 1080×2400.
- The layout is now a **narrow right-side rail of small tiles** plus one large
  content stage. Tap a rail tile to engage it. Media has an in-app play/pause
  control; the stage `OPEN` button launches the real app.
- Android Auto 0.1.1 was verified on the owner's real Kia Android Auto screen
  over USB using Play Internal App Sharing. The 0.1.8 dashboard build is
  pending a car pass; the phone launcher has been used for map, theme and layout
  checks. The routing alpha passes its emulator tests, but live Stadia routing
  and a real Android Auto head unit are still unverified.
- The Desktop Head Unit was attempted, but the emulator image's Android Auto
  app is a Play Store stub and the image has no Play Store.

Live data is ordered as requested: clock + theme, now playing (MediaSession),
notifications (NotificationListener), GPS speed. The Vehicle tile is hidden
behind `FeatureFlags.SHOW_VEHICLE_TILE`; Torque is intentionally not wired up
yet. The car title and footer bars are doubled and the clock, now playing, and
speed are the largest items in them.

## Build

Prerequisites: JDK 17+ and Android SDK platform 37.

Toolchain: Gradle **9.8.2**, AGP **9.5.0**, built-in Kotlin (no separate KGP),
compileSdk **37**, targetSdk **36**. CI uses the current action runtimes
(`checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, `upload-artifact@v7`) on
`ubuntu-26.04`; there is no Node/JavaScript app in this repository.

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

Tom Ballard’s Tokyo Studio image is tracked as the Tokyo Night theme
background, so default builds include it. The private local-override flag is
only needed for an owner-specific image:

```sh
./gradlew assembleDebug -Pomarchy.includeLocalAssets=true
```

Default builds exclude `local-assets/`; that flag is for private overrides, not
for Tom’s image. Before a public-track release, run the asset guard:

```sh
./gradlew verifyPublicReleaseAssets
```

It builds the release archives and fails if any `local-assets/` file appears in
them. APKs:

```text
auto/build/outputs/apk/debug/auto-debug.apk
launcher/build/outputs/apk/debug/launcher-debug.apk
navigation-spike/build/outputs/apk/debug/navigation-spike-debug.apk
```

They are debug-signed. Verify with:

```sh
$ANDROID_HOME/build-tools/37.0.0/apksigner verify --print-certs \
  auto/build/outputs/apk/debug/auto-debug.apk
```

A clean clone only needs `ANDROID_HOME` (or `local.properties` with
`sdk.dir=...`). `local.properties` is ignored.

## Install and run

```sh
adb install -r auto/build/outputs/apk/debug/auto-debug.apk
adb install -r launcher/build/outputs/apk/debug/launcher-debug.apk
# Optional routing spike:
adb install -r navigation-spike/build/outputs/apk/debug/navigation-spike-debug.apk
```

### Phone launcher

```sh
adb shell am start -n com.omarchy.auto.launcher/.MainActivity
```

Or choose **Omarchy Auto Launcher** as the home app. It is a rail-and-stage
demo launcher, not a full app drawer.

The app opens with **all tiles minimized** — including Drive/navigation — so the
wallpaper is the first thing you see. Tap a rail tile to expand it; tap the same
tile again to minimize. The stage has an `OPEN` action for real apps:

| Rail tile | Stage | OPEN action |
|---|---|---|
| Drive | real MapLibre/OpenFreeMap map + live GPS; no turn-by-turn | Google Maps |
| Media | large now-playing/audio art + play/pause | selected in Setup: auto, Spotify, YouTube Music, YouTube, ChatGPT, Grok, etc. |
| Comms | large notification list | default SMS app |
| Vehicle | hidden until OBD/Torque is real | none — deferred |
| Home | clock, theme chips, setup | Android Settings |

Back from the launched app returns to the launcher. The top-right `THEME`
button opens the Home theme picker with ten themes: Tokyo Night, Osaka Jade,
Gruvbox, Catppuccin, Kanagawa, Everforest, Nord, Ethereal, Ristretto, and
Hackerman. Tokyo Night uses Tom Ballard’s Tokyo Studio image; the other themes
load their matching Omarchy background. Setup also offers
`CHOOSE BACKGROUND`, `USE TOM BACKGROUND` (private builds), and
`USE THEME BACKGROUNDS`; a custom choice overrides every theme until it is
reset. The Android Auto app and the phone launcher are separate packages, so
choose the background in each app's Setup if you use both. Notification text
size is per app for the same reason.

### Routing alpha (optional)

`:navigation-spike` is a separate experimental app (`com.omarchy.auto.navspike`)
for evaluating turn-by-turn with Ferrostar and hosted Valhalla. It is not part
of the dashboard APK and does not replace the Drive stage yet.

```sh
./gradlew :navigation-spike:assembleDebug
adb install -r navigation-spike/build/outputs/apk/debug/navigation-spike-debug.apk
adb shell am start -n com.omarchy.auto.navspike/.SpikeActivity
```

Use the owner's own Stadia Maps key at runtime for live routing; no API key is
embedded. The debug build also has a keyless recorded-route replay:

```sh
adb shell am start -n com.omarchy.auto.navspike/.ReplayActivity
```

Emulator tests prove the native engine, guidance events, off-route detection,
and replay UI. Live routing, audible speech, real GPS and the Android Auto head
unit remain follow-up validation. See
[`docs/NAVIGATION-SPIKE.md`](docs/NAVIGATION-SPIKE.md).

### Roadster background

Setup's background picker is the supported path. Tom Ballard’s Tokyo Studio
image is tracked at `core/src/main/assets/themes/tokyo-night.webp` and is the
default Tokyo Night background in normal debug and release builds. `USE TOM
BACKGROUND` locks that image across theme changes; `USE THEME BACKGROUNDS`
returns to the active theme’s bundled image. `local-assets/` remains optional
for owner-specific private overrides with
`-Pomarchy.includeLocalAssets=true`, but it is no longer required for Tom’s
image. See [`docs/ASSETS.md`](docs/ASSETS.md).

### Permissions

The phone launcher has a Home-tile `SETUP · PERMISSIONS` button. The Android
Auto APK also has a **Setup** launcher icon. The same grants can be made with
ADB:

```sh
adb shell cmd notification allow_listener \
  com.omarchy.auto/com.omarchy.auto.core.OmarchyNotificationListener
adb shell cmd notification allow_listener \
  com.omarchy.auto.launcher/com.omarchy.auto.core.OmarchyNotificationListener
adb shell pm grant com.omarchy.auto android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.omarchy.auto.launcher android.permission.ACCESS_FINE_LOCATION
```

Notification access enables both the Comms tile and MediaSession discovery.
Without it the UI says so instead of inventing data.

### Android Auto — test install

Current test build: **0.1.8** (versionCode 9). Build the signed AAB with:

```sh
./gradlew :auto:bundleRelease
```

Android Auto does not trust an ADB-sideloaded Car App Library app. The
documented test route is **Play Internal App Sharing**, which installs from
Play without a public listing or review. The owner-specific upload steps and
host caveats live in [`docs/AA-INTERNAL-SHARING.md`](docs/AA-INTERNAL-SHARING.md).
The 0.1.8 AAB includes MapLibre native libraries and the tracked Tokyo Studio
Tokyo Night background.

The car app uses the Car App Library **NAVIGATION** category. It draws the five
tiles into `NavigationTemplate`'s map surface through
`AppManager.setSurfaceCallback`. Android Auto owns the surrounding template and
action strip; the app adds a **Theme** action there. The canvas also has a
top-bar `THEME` button for hosts that forward surface clicks.

> **Sideloading caveat:** current Google documentation says the Android Auto
> "Unknown sources" developer option applies to media, messaging, and parked
> apps, but *not* to Car App Library apps. There is also a 2026 field report of
> sideloaded navigation apps disappearing from the AA launcher. Internal App
> Sharing / an Internal Test Track is the documented private test route. The
> debug build uses `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR`; the release build
> now allows the official AA host certificates. The car path has been seen on
> the owner's Kia with 0.1.1; use Internal App Sharing for each new build and
> verify the new surface changes on the car.

## Architecture

```text
core/                         shared Kotlin Android library
  Theme.kt                    10 Omarchy palettes + background ids
  Model.kt                    TileId, DashboardState, MediaSnapshot, NotificationItem
  TileRenderer.kt             one Canvas renderer for AA surface + phone View
  LiveDataStore / Controller  in-process state and source lifecycle
  MediaWatcher                MediaSessionManager + MediaController
  OmarchyNotificationListener NotificationListenerService
  LocationWatcher             GPS speed + held heading at low speed
  MapSnapshotController       MapLibre/OpenFreeMap snapshots at the stage size
  MapSnapshotStore / Viewport latest map bitmap, size + staleness
  SetupActivity               permission setup

auto/                         Android Auto app (com.omarchy.auto)
  OmarchyCarAppService        CarAppService, foreground live-data service
  OmarchyScreen               NavigationTemplate + AppManager surface callback
  CarSurfaceRenderer          Surface.lockCanvas -> TileRenderer

launcher/                     phone launcher (com.omarchy.auto.launcher)
  MainActivity                immersive launcher, app intents
  TileView                    shared renderer + hit testing
  LaunchTargetStore           persisted Media tile app target (core)
```

No Compose or QML runtime is on the car surface. The decision and rejected
alternatives are in **ADR-0008**. The old Linux plan is in
`docs/archive/BRIEF-linux-2026-11-01.md`; `shell/` remains the QML prototype for
that future path.

## What works

| Area | Status |
|---|---|
| Five-tile layout, cut-out legends, mono type, 2px borders | Yes, shared renderer |
| Themes | 10 Omarchy palettes + matching backgrounds (Tokyo Night: Tom Ballard); phone switch verified |
| Background | Custom image picker, bundled Tom background, or theme backgrounds; custom choice survives theme changes |
| Bars | Doubled car title/footer, large clock + now playing + speed; 1920×720 and 800×480 previews pass |
| Clock | Live; idle redraws align to the next minute, media progress stays 1 Hz |
| Battery | Footer icon + percentage; charging accent; state-driven redraw |
| Media | Live MediaSession metadata/art/progress; phone and car paths wired; 0.1.1 car test verified |
| Notifications | Live listener + existing-notification load; OTP/2FA and banking filter; optional package allow-list; bodies not logged |
| GPS speed | Location/GPS speed; launcher got a fix at 0 km/h on the bench |
| Vehicle | Hidden by `FeatureFlags.SHOW_VEHICLE_TILE` until OBD/Torque is real |
| Right rail + large stage | Verified on emulator and phone |
| Rotation | Portrait/landscape reflow verified on the S24 FE |
| Media play/pause/previous/next | Wired to MediaTransport; car pass pending |
| AA NavigationTemplate + SurfaceCallback | Verified on real AA (0.1.1); 0.1.8 car pass pending |
| AA host action strip | Removed; canvas header owns THEME; car/DHU recheck pending |
| Real map | OpenFreeMap dark style via MapLibre snapshots + live location puck; phone verified, car pending |
| Routing alpha | Separate Ferrostar + hosted Valhalla app; emulator replay/UI tests pass; live key/car validation pending |
| Notification detail | Full-screen card overlay; S/M/L text size in Setup; phone verified |
| Phone stage OPEN + Back | Verified with YouTube Music/Maps path |
| README hero | `docs/img/hero-tokyo-night.webp` (Tom Ballard Tokyo Studio render, credited) |

## Verification

- `./gradlew assembleDebug` — passes; both APKs are debug-signed.
- Emulator: Android 15 Google APIs, API 35.
- Phone launcher: 1280×720 landscape and 1080×2400 portrait screenshots.
- Car previews: rendered at 1920×720 and 800×480; rail, theme button, footer,
  10-theme grid, and doubled bars fit without clipping. Synthetic renders are
  captioned and are not car footage.
- Theme button changed Tokyo Night → Gruvbox/Hackerman.
- Background picker: selected an image, enabled it, changed the theme, and
  confirmed the custom image survived; reset to theme backgrounds.
- Notification text S/M/L applied to Comms rows; LARGE and SMALL previews
  checked at 800×480.
- Media tile showed a real MediaSession from YouTube Music playing a local test
  WAV; progress/play state updated.
- Comms tile showed a real system notification posted with `cmd notification`
  plus existing notifications after listener reconnect.
- Tile-to-YouTube-Music and Back-to-launcher verified.
- GPS provider returned a fix; speed read zero while stationary.
- Android Auto host: 0.1.1 verified on the owner's real Kia screen. The 0.1.8
  dashboard and the routing alpha still need the same car pass.
- Routing spike: `:navigation-spike:assembleDebug`, JVM tests, lint and native
  emulator tests pass. Live Stadia routing, audible speech, real GPS and the
  Android Auto head unit remain unverified. See
  [`docs/NAVIGATION-SPIKE.md`](docs/NAVIGATION-SPIKE.md).
- Real map: MapLibre snapshot of `https://tiles.openfreemap.org/styles/dark`
  with a live location puck and OpenStreetMap/OpenFreeMap attribution. No
  routing or turn-by-turn.

## Limitations

- The 0.1.8 theme/map/sizing build and this branch's car-UX follow-up still
  need a real-car acceptance pass. 0.1.1 was verified there.
- Sideloaded Car App Library availability is subject to Google's host policy;
  use Play Internal App Sharing for the trusted install.
- The AA host action strip was removed on this branch to stop the floating
  THEME pill and surface shift; it needs a car/DHU pass to confirm the
  NavigationTemplate fills the car screen.
- The routing alpha has not been validated with a live Stadia key, audible
  speech, real GPS, or a real Android Auto head unit. Its replay tests are not
  a claim of fresh routing quality.
- Media controls send play/pause, previous, and next through MediaController.
- Notification privacy is documented in
  [`docs/NOTIFICATION-PRIVACY.md`](docs/NOTIFICATION-PRIVACY.md); OTP/2FA and
  banking filtering plus the optional allow-list are implemented.
- Redraw budget and remaining 30-minute car measurements are documented in
  [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md).
- Location privacy: when the Drive stage is open, the phone's location is sent
  to OpenFreeMap tile servers to render the map. Omarchy Auto does not store a
  location history. There is no map-off switch yet.
- Vehicle data is mock and the tile is hidden until OBD-II; Torque is not
  wired up.
- The Tokyo Studio artwork is included in the README, public site, and app
  builds with Tom Ballard’s permission and credit. Other Omarchy-derived theme
  backgrounds still need per-image rights checks before a public release.
- No public Play listing, store review, or production launch yet. The public
  source release is being prepared; see the publication gates below.
- Linux/Quickshell remains future work; it is not in the APKs.

## Publication gates

Before flipping the repository or app to public:

- Confirm the media rights for the nine Omarchy-derived theme backgrounds
  (`omarchy-jnn.7`). Tokyo Studio is cleared with Tom Ballard's permission.
- Complete the real-car dashboard and routing-alpha passes and capture dated
  evidence.
- Finish the Android Auto host allowlist check and the public Play listing.
- Keep the routing alpha keyless by default; live routing needs each tester's
  own Stadia key or a separately designed server.
- Review [`THIRD_PARTY.md`](THIRD_PARTY.md), [`CREDITS.md`](CREDITS.md) and
  [`docs/ASSETS.md`](docs/ASSETS.md) in the public tree.

## License

Project code and original assets are released under the [MIT License](LICENSE).
See [`CREDITS.md`](CREDITS.md) for Tom Ballard's Tokyo Studio artwork, Omarchy
theme credits, and third-party asset notes. The MIT license does not relicense
third-party artwork. AndroidX / Car App Library is Apache-2.0. Full component
notices are in [`THIRD_PARTY.md`](THIRD_PARTY.md).
