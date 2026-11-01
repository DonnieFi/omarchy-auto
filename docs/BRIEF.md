# Omarchy Auto — product brief (Android v1)

**Redirect, 2026-11-01.** The v1 target is Android, not a Linux head unit. The
Linux/Quickshell plan is preserved in
[`archive/BRIEF-linux-2026-11-01.md`](archive/BRIEF-linux-2026-11-01.md) and in
`../shell/` as future work. ADR-0008 records the switch and the renderer choice.

## Summary

A real, filmable demo of an OmarchyTV-styled five-tile UI on a car's Android
Auto screen over USB, plus the same UI as an Android phone launcher. A Kotlin
Canvas renderer is shared by both targets so themes, layout, and live data stay
identical.

The car UI is a Car App Library **navigation app**: it gets Android Auto's map
surface and draws the five tiles itself. The host still owns the template chrome
and action strip. It is not possible to replace Android Auto's launcher or skin
the host UI.

## v1 scope

In:

- Android Auto app (`com.omarchy.auto`) with a
  `NavigationTemplate` + `AppManager.setSurfaceCallback`.
- Phone launcher APK (`com.omarchy.auto.launcher`) with the same five tiles,
  launching real apps and returning on Back.
- Five tiles: Drive, Media, Comms, Vehicle, Home.
- Four Omarchy themes, one-tap switch from the AA action strip, the canvas top
  bar, and the Home theme chips.
- Live clock and theme.
- Live now playing from MediaSession, including metadata, art, progress, and
  play/pause state (read-only).
- Live notifications from NotificationListenerService.
- Live GPS speed from LocationManager.
- Vehicle tile stays mock with a visible `MOCK` label until OBD-II.
- Debug-signed APKs from one documented command.

Out:

- Play Store submission, production signing, release distribution.
- Calls/voice routing, notification quick reply, media transport commands.
- OBD-II, reverse camera, CAN bus, steering-wheel controls.
- Linux head-unit installation in v1.
- Pixel projection of Google Maps inside the Omarchy UI.

## Rendering decision

**Kotlin + Android Canvas, shared by the car surface and the phone View.**

- `SurfaceCallback` hands us a raw `Surface`. `Surface.lockCanvas` is the direct,
  low-risk drawing path.
- A plain `View` on the phone calls the same `TileRenderer.draw`.
- Compose would need an extra host/View bridge to reach the raw surface and adds
  runtime/compiler surface area for no visual gain.
- QML/Qt for Android would add a Qt Android deployment, a QML runtime, and a
  Surface/TextureView bridge, plus a much larger APK and more lifecycle risk.
  That is the opposite of speed-to-demo.

ADR-0008 records the full trade-off.

## Architecture

```text
Android phone
  Android Auto host  <── USB ──  car head unit
    └─ OmarchyCarAppService (NAVIGATION)
         NavigationTemplate + AppManager.setSurfaceCallback
         CarSurfaceRenderer -> Surface.lockCanvas -> TileRenderer
  Omarchy Launcher Activity
         TileView -> TileRenderer

  shared core/
    Theme + ThemeStore
    DashboardState + LiveDataStore
    MediaWatcher (MediaSessionManager)
    OmarchyNotificationListener
    LocationWatcher (GPS speed)
    SetupActivity
```

The car app and launcher are separate APKs/processes. They share source, not
runtime state. Notification access and media-session access are per app; the
Setup screen explains and requests them.

## Live data

| Data | Source | Status |
|---|---|---|
| Clock | system time, 1 Hz redraw | working |
| Theme | in-app ThemeStore/SharedPreferences | working |
| Now playing | MediaSessionManager + MediaController | working in launcher; AA wired |
| Artwork | MediaMetadata bitmap | working when provided |
| Notifications | NotificationListenerService | working in launcher |
| GPS speed | LocationManager GPS_PROVIDER | working; bench speed 0 |
| Vehicle | mock | labeled mock |

MediaSession discovery requires notification-listener access on modern Android.
If access is missing, the Media tile says so. The Vehicle tile never pretends to
be live.

## Android Auto constraints

- The app must be in the `androidx.car.app.category.NAVIGATION` category to use
  `NavigationTemplate` and draw a surface.
- Declare `androidx.car.app.NAVIGATION_TEMPLATES` and
  `androidx.car.app.ACCESS_SURFACE`.
- The host controls the template, status/action strip, and distraction rules.
- The app's surface must respect the stable/visible area callbacks.
- The debug build uses `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` because it is
  not Play-distributed. This is not a production security posture.
- Sideloading Car App Library apps is not guaranteed by current Android Auto
  policy. The README documents the caveat and the Play Internal App Sharing
  fallback. Real hardware verification is still required.

## Roadmap

### Now / v0.1

- Both APKs build and install.
- Phone launcher demo with live media/notifications/GPS.
- AA template/surface implementation compiled.
- 1280×720 phone-launcher capture in `docs/img/`.

### v0.2 — car proof

- Run on a real AA phone + car over USB.
- Confirm the NavigationTemplate surface size/safe area and action strip.
- Capture/photo the real AA screen; label it as such.
- Verify theme action and Media tile with Spotify.
- Fix any host-specific surface/touch behaviour.

### v0.3 — robustness

- Notification allow-list and OTP/2FA exclusion.
- Media transport commands (play/pause/next) through MediaController.
- Permission/onboarding polish; reconnect on process death.
- Unit tests for renderer layout/hit testing; instrumentation for permissions.

### Future — Linux/Quickshell

Keep `shell/` and `docs/archive/BRIEF-linux-2026-11-01.md`. Revisit only after
the Android car demo is real. ADR-0001/0002 are superseded for v1 but remain
useful history.

## Acceptance checklist for the redirect

- [x] Git repo created, public, initial scaffold pushed.
- [x] Two Android targets share one tile model/theme/renderer.
- [x] `./gradlew assembleDebug` builds signed debug APKs.
- [x] Phone launcher runs, theme switch works, live MediaSession and
      notifications work, GPS fix obtained, tile launch/back works.
- [ ] Real Android Auto car screen over USB.
- [ ] Real AA theme switch + live Media tile on the car screen.
- [x] 1280×720 capture in `docs/img/` (phone launcher, clearly labeled).
- [x] Vehicle mock visibly labeled.

## Risks

- **Sideload policy.** Android Auto may hide a Car App Library app that was not
  installed from Play. Validate before promising a car demo.
- **Host variance.** Surface insets, action strip placement, click forwarding,
  and DPI differ across cars. The renderer uses stable-area callbacks but is
  untested on hardware.
- **Android background limits.** The car service attempts foreground mode for
  location; if the OS blocks it, speed may stop while the screen is off.
- **Media permissions.** Notification-listener access is a broad grant; the app
  must not log message bodies and should add an allow-list.
- **Scope.** One DPI/layout target (1280×720 landscape) is the demo; portrait
  phone layout is a secondary target.

## Open questions

1. Can the target AA phone/version still show a sideloaded Car App Library app?
2. Which real car/phone will be used for the acceptance capture?
3. Which media app should the demo use (Spotify package, YouTube Music, local)?
4. What notification allow-list is acceptable for the demo?
5. Is the phone launcher intended as a default home replacement or a demo app?
6. When, if ever, should the Linux/Quickshell path resume?
