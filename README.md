# Omarchy Auto

**An Omarchy-styled five-tile UI for Android Auto and Android phones.** The car
display is the primary target: a Car App Library **NavigationTemplate** app that
draws `Drive / Media / Comms / Vehicle / Home` on the Android Auto map surface.
The same Kotlin Canvas renderer powers a full-screen phone launcher.

![Omarchy Auto phone launcher at 1280×720](docs/img/launcher-1280x720.png)

*Phone launcher at 1280×720. The track is a local test WAV and the Shell
notification is synthetic test content; this is not Android Auto footage. The
car host has not been available yet.*

## Status — v0.1 redirect (2026-11-01)

The repository is **private until launch**; contributors are added explicitly.
The project pivoted from a Linux/Quickshell head unit to an Android target for
the fastest path to a real in-car demo. The Linux path remains in `shell/` and
in the docs archive as future work.

Built and checked on this machine:

- `./gradlew assembleDebug` produces two signed debug APKs.
- Phone launcher runs on an Android 15 emulator. Theme switch, live
  MediaSession, live notifications, a GPS fix, tile launch, and Back return
  were exercised at 1280×720 and 1080×2400.
- Android Auto APK compiles with the Car App Library navigation template and
  surface callback. **It has not been shown on a real Android Auto car screen.**
- The Desktop Head Unit was attempted, but the emulator image's Android Auto
  app is a Play Store stub and the image has no Play Store.

Live data is ordered as requested: clock + theme, now playing (MediaSession),
notifications (NotificationListener), GPS speed. The Vehicle tile is explicitly
`MOCK` until OBD-II.

## Build

Prerequisites: JDK 17+ and Android SDK platform 35.

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

APKs:

```text
auto/build/outputs/apk/debug/auto-debug.apk
launcher/build/outputs/apk/debug/launcher-debug.apk
```

They are debug-signed. Verify with:

```sh
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  auto/build/outputs/apk/debug/auto-debug.apk
```

A clean clone only needs `ANDROID_HOME` (or `local.properties` with
`sdk.dir=...`). `local.properties` is ignored.

## Install and run

```sh
adb install -r auto/build/outputs/apk/debug/auto-debug.apk
adb install -r launcher/build/outputs/apk/debug/launcher-debug.apk
```

### Phone launcher

```sh
adb shell am start -n com.omarchy.auto.launcher/.MainActivity
```

Or choose **Omarchy Auto Launcher** as the home app. It is a five-tile demo
launcher, not a full app drawer. Tapping:

| Tile | Opens |
|---|---|
| Drive | Google Maps |
| Media | Spotify, then YouTube Music |
| Comms | the default SMS app |
| Vehicle | Torque (mock data until OBD) |
| Home | Android Settings; theme chips cycle themes |

Back from the launched app returns to the launcher. The top-right `THEME`
button cycles Tokyo Night, Osaka Jade, Gruvbox, and Catppuccin.

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

### Android Auto

1. Install the `auto` APK.
2. Open **Omarchy Auto setup** on the phone and grant notification access,
   location, and the service notification. [Setup screen](docs/img/setup-1280x720.png).
3. In Android Auto settings, enable Developer mode, then enable **Unknown
   sources** (where available).
4. Connect the phone to the car over USB and launch **Omarchy Auto** from the
   Android Auto launcher.

The car app uses the Car App Library **NAVIGATION** category. It draws the five
tiles into `NavigationTemplate`'s map surface through
`AppManager.setSurfaceCallback`. Android Auto owns the surrounding template and
action strip; the app adds a **Theme** action there. The canvas also has a
top-bar `THEME` button for hosts that forward surface clicks.

> **Sideloading caveat:** current Google documentation says the Android Auto
> "Unknown sources" developer option applies to media, messaging, and parked
> apps, but *not* to Car App Library apps. There is also a 2026 field report of
> sideloaded navigation apps disappearing from the AA launcher. This debug build
> uses `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR`; if your AA build hides it, use
> Play Internal App Sharing / an Internal Test Track, or a dev phone with an AA
> version that still permits it. Do not claim the app works on a car until you
> have seen it there.

## Architecture

```text
core/                         shared Kotlin Android library
  Theme.kt                    4 Omarchy palettes
  Model.kt                    TileId, DashboardState, MediaSnapshot, NotificationItem
  TileRenderer.kt             one Canvas renderer for AA surface + phone View
  LiveDataStore / Controller  in-process state and source lifecycle
  MediaWatcher                MediaSessionManager + MediaController
  OmarchyNotificationListener NotificationListenerService
  LocationWatcher             GPS speed
  SetupActivity               permission setup

auto/                         Android Auto app (com.omarchy.auto)
  OmarchyCarAppService        CarAppService, foreground live-data service
  OmarchyScreen               NavigationTemplate + AppManager surface callback
  CarSurfaceRenderer          Surface.lockCanvas -> TileRenderer

launcher/                     phone launcher (com.omarchy.auto.launcher)
  MainActivity                immersive launcher, app intents
  TileView                    shared renderer + hit testing
```

No Compose or QML runtime is on the car surface. The decision and rejected
alternatives are in **ADR-0008**. The old Linux plan is in
`docs/archive/BRIEF-linux-2026-11-01.md`; `shell/` remains the QML prototype for
that future path.

## What works

| Area | Status |
|---|---|
| Five-tile layout, cut-out legends, mono type, 2px borders | Yes, shared renderer |
| Themes | 4 palettes; phone verified, AA action strip implemented |
| Clock | Live, every second |
| Media | Live MediaSession metadata/art/progress in launcher; AA app wired, not host-tested |
| Notifications | Live listener + existing-notification load; launcher verified |
| GPS speed | Location/GPS speed; launcher got a fix at 0 km/h on the bench |
| Vehicle | Mock, visible `MOCK` label |
| AA NavigationTemplate + SurfaceCallback | Compiles; real host unverified |
| Phone tile launch/back | Verified with YouTube Music |
| 1280×720 screenshot | `docs/img/launcher-1280x720.png` (phone launcher, not AA) |

## Verification

- `./gradlew assembleDebug` — passes; both APKs are debug-signed.
- Emulator: Android 15 Google APIs, API 35.
- Phone launcher: 1280×720 landscape and 1080×2400 portrait screenshots.
- Theme button changed Tokyo Night → Gruvbox.
- Media tile showed a real MediaSession from YouTube Music playing a local test
  WAV; progress/play state updated.
- Comms tile showed a real system notification posted with `cmd notification`
  plus existing notifications after listener reconnect.
- Tile-to-YouTube-Music and Back-to-launcher verified.
- GPS provider returned a fix; speed read zero while stationary.
- Android Auto host: **not verified**. The DHU was installed and runnable, but
  the emulator's AA package is a stub that tries to open a missing Play Store.

## Limitations

- A real AA car screen over USB remains the outstanding acceptance test.
- Sideloaded Car App Library availability is subject to Google's host policy.
- The AA app's action-strip theme button and surface hit testing are untested on
  a real host.
- Media control buttons are visual only; playback control commands are not sent.
- Notifications are not yet allow-listed; OTP/2FA filtering is future work.
- Vehicle data is mock until OBD-II.
- No Play Store submission, no production signing, no release build.
- Linux/Quickshell remains future work; it is not in the APKs.

## License

TBD. AndroidX/Car App Library is Apache-2.0. Omarchy themes are MIT. See
`docs/REFERENCES.md`.
