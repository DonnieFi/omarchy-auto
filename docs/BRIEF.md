# Omarchy Auto — product brief

## Summary

A touch-first car head-unit UI in the visual language of Omarchy, built as a
portable Qt Quick app. Five tiles: Drive, Media, Comms, Vehicle, Home. The
phone supplies the data and connectivity through a companion app; the car
computer supplies the screen, sensors, and audio.

The project starts as a desktop window, moves to the ARMv7 Galaxy Tab 4 as a
bench prototype, and then to a Linux car computer (Raspberry Pi 5 or x86 mini
PC). Android Auto is not required for v1.

## The insight

"Omarchy on Android Auto" is the wrong direction. Android Auto is a projection
protocol with a Google/OEM-controlled template runtime. Third-party apps get
templates, not a launcher, whole-screen composition, or theme control.
Omarchy's pixels can
only live on the host side.

So the buildable version is **Android Auto inside Omarchy**:

- Omarchy owns the screen, the interaction model, and the visual language.
- The phone is a brain: media, notifications, GPS, connectivity.
- Android Auto, if used at all, is a data source behind the Omarchy UI — not
  the UI itself.

That is also the more interesting project: Google's brain, Omarchy's face.

## Users and use cases

Primary: the owner of an older car with a plain dashboard, who wants a modern,
glanceable car interface without buying a new head unit.

Scenarios:

- Daily commute: media, notifications, next turn, vehicle status.
- Weekend drive: navigation, weather, music, no phone fiddling.
- Parked: settings, theme changes, Home Assistant / garage controls.
- Bench: develop and demo the shell on a desk or tablet.

## v1 scope

In:

- A Qt Quick shell with five tiles and a Waybar-style top bar.
- Theme switching across a small Omarchy palette set.
- Responsive 1280×800 landscape and 800×1280 portrait layouts.
- A daemon that exposes media, notifications, GPS, and OBD-II as QML models.
- An Android companion app that streams notifications, media metadata,
  location, and navigation handoff over Wi-Fi.
- Power, mount, audio, and ignition/suspend behavior for a bench-to-car install.

Out (deliberately):

- Android Auto pixel projection.
- Phone calls and voice assistant routing through the head unit.
- Reverse camera, dashcam, steering-wheel controls.
- Full turn-by-turn routing on the head unit.
- A plugin marketplace. The five tiles are built in; a plugin API comes later.
- A head-unit replacement that integrates with the car's CAN bus, amplifier,
  climate controls, or factory display.

## Experience

### Design rules

- **Steal the language, not the window manager.** OmarchyTV's tiling grammar
  becomes a fixed five-tile grid. Free-floating windows are hostile in a moving
  car.
- 2px borders, btop-style cut-out legends, monospace type, sharp corners.
- A Waybar-style top bar: time, temperature, connectivity, phone battery,
  volume, theme.
- One expanded tile at a time; a clear back action.
- No blur, shadows, or shader effects in the v1 shell — the tablet renders
  with Qt Quick's software backend.
- 64dp minimum touch targets; primary controls in the lower half of the screen.
- Day and night palettes; dim at night; high contrast in sunlight.
- Parked-only settings, keyboard, and video.

### The five tiles

1. **Drive** — map, next maneuver, ETA, speed. v0.1 uses a drawn mock map.
   v0.2 uses phone GPS and a navigation handoff; later it can consume Android
   Auto's navigation channel.
2. **Media** — now playing, album art, transport, progress, source. v0.2 uses
   MPRIS on the head unit or MediaSession metadata from the companion.
3. **Comms** — notifications and messages, unread state, TTS readout, quick
   replies. v0.2 uses Android's NotificationListenerService.
4. **Vehicle** — speed, RPM, coolant, fuel/EV state of charge, range, DTCs.
   v0.2 uses an ELM327 OBD-II adapter.
5. **Home** — clock, weather, theme switcher, garage/Home Assistant scenes,
   reverse camera placeholder, settings.

### Safety

- Driving state comes from OBD speed (preferred) or GPS.
- Settings, keyboard, and non-essential scrolling are parked-only.
- No video playback while moving. No "video in motion" defeat.
- The screen must not block airbags or the driver's forward view.
- A lithium battery on a hot dashboard is a fire risk. The final build should
  use a car computer without a battery, or a properly protected UPS.

## Architecture

```
Android phone (companion APK)
  NotificationListener / MediaSession / Location / RemoteInput
        │ WSS over phone hotspot, mDNS, pairing token
        ▼
omarchy-drived (Python or Rust, on tablet / car computer)
  D-Bus: MPRIS, NetworkManager, UPower, BlueZ
  OBD-II (ELM327), GPS, GPIO ignition, reverse camera
        │ JSON-RPC / QML models
        ▼
Qt Quick / Quickshell shell (Omarchy theme, five tiles)
        │
        └── later: experimental AA receiver (android-auto / LIVI)
```

The head unit is the WebSocket server; the phone is the client. The phone hosts
the hotspot, the daemon advertises `_omarchyauto._tcp` over mDNS, and the phone
connects over pinned TLS. ADR-0007 records the proposal; the TLS and discovery
pieces are not built yet.

### Why a separate daemon

The current ARMarchy Quickshell build disables networking, PipeWire, Bluetooth,
and several optional services to stay light on ARMv7. Turning it into a system
integration layer would be painful. Instead:

- The QML shell is a view. It owns layout, theme, and touch.
- `omarchy-drived` owns D-Bus, BlueZ, NetworkManager, UPower, OBD-II, GPS, and
  the companion WebSocket. Python for speed of development; Rust if a static
  binary or tighter memory budget is needed.
- The boundary is a small JSON-RPC or property model (`MediaState`, `NavState`,
  `VehicleState`, `Notification`, `PhoneState`). That boundary is what lets the
  same shell later consume Android Auto data instead of the companion.

### Data model (draft)

```text
MediaState       title, artist, album, artUrl, playing, position, duration, source
NavState         active, maneuver, road, distance, eta, remaining, speedLimit
VehicleState     speed, rpm, coolant, fuel, evSoc, range, dtcs[], obdConnected
Notification     id, app, title, text, time, unread, canReply, actions[]
PhoneState       connected, battery, signal, name, hotspot
ThemeState       name, palette, dayNight
```

## Roadmap

### Phase 0 — decisions and spikes (3–5 days)

- S1: run the five-tile mock on the Tab 4; measure FPS, RAM, touch.
- S2: connect the experimental Rust `android-auto` receiver on a desktop and
  log what the navigation and media channels actually expose when
  Maps/Spotify run.
- S3: benchmark 480p/720p H.264 decode on the Tab 4.
- S4: companion proof — notifications, media metadata, GPS to a laptop over
  WebSocket for 30 minutes.
- S5: OBD-II proof — ELM327 speed/RPM/coolant at 1 Hz.
- S6: power/thermal proof — one hour in the car, ignition cycle.

### Phase 1 — shell skeleton (1–2 weeks)

- Five tiles, fake data, theme switch, day/night.
- Responsive 1280×800 and 800×1280.
- Runs on desktop, then the Tab 4.
- Exit: a demo video that already looks like the product.

### Phase 2 — daemon and live data (1–2 weeks)

- `omarchy-drived` with MPRIS, NetworkManager, UPower, OBD-II, GPS.
- QML models and a fake/live switch.
- Exit: media and vehicle tiles live on the bench.

### Phase 3 — companion app (2 weeks)

- Foreground service, WSS client, mDNS discovery, pinned pairing, reconnect.
- Notification listener, media session, location, quick reply.
- Navigation handoff: send a destination to the phone, display the active
  maneuver/ETA from its notification.
- Exit: phone in a pocket, shell shows notifications/media/GPS for an hour.

### Phase 4 — bench integration (1 week)

- Real phone, real car data, real audio.
- Measure FPS, memory, thermals; fix the worst.
- Exit: a one-hour bench run with no leaks or crashes.

### Phase 5 — car install (1–2 weeks)

- Fused 12V→5V supply, ignition sense, UPS/supercap, safe shutdown.
- Mount, screen brightness, audio path, dimming.
- Exit: a 30-minute drive with ignition on/off and resume.

### Phase 6 — optional Android Auto (2–4 weeks)

- Prefer AA as a data source: run the receiver, drop the video, consume the
  navigation/media/audio channels, render the Omarchy UI.
- Pixel projection is a separate mode for hardware that can decode H.264.
- Exit: Google Maps routing visible as Omarchy maneuvers; no video decode.

## Spikes

| Spike | Question | Pass | Fallback |
|---|---|---|---|
| S1 Shell on Tab 4 | Five-tile mock at 30fps, under 300MB, touch works | Usable | Drop effects; move to Pi |
| S2 AA data | Rust receiver connects; nav/media fields are usable | Enough for a tile | Companion nav handoff |
| S3 H.264 on Tab 4 | 480p30/720p30 decode benchmark | 480p usable | No AA pixels on tablet |
| S4 Companion | Notifications + media + GPS over WebSocket for 30 min | Stable | KDE Connect for media/notifications |
| S5 OBD | ELM327 at 1 Hz on tablet | Works | Phone OBD app → companion |
| S6 Power | One hour in car, ignition cycle | Stable | Redesign power |

## Risks

- **Android Auto receiver integration is unofficial.** It can break with a
  phone or Play Services update. Do not ship it as a certified product; respect
  distraction laws.
- **Nav is the hard part.** Notification parsing is not turn-by-turn. Full
  navigation needs AA pixels or a head-unit routing stack (Valhalla/OSRM plus
  raster/vector maps). Do not hand-wave it.
- **The Tab 4 is a 2014 ARMv7 tablet.** 1.3GB RAM, no DRM/GPU, low brightness,
  old kernel, battery heat. It is a bench target, not the final car computer.
- **Notification privacy.** The bridge carries message bodies. Pair, encrypt,
  bind to the car network, never log notification content, and exclude OTP/2FA
  and other sensitive categories by default. A token alone authenticates a
  client but does not hide traffic.
- **Hotspot and discovery.** Android hotspots vary by OEM, can sleep, and mDNS
  can be blocked. Keep a manual host/port fallback and test screen-off,
  reconnect, and roaming.
- **Audio is a project on its own.** A2DP latency, HFP calls, echo cancellation.
  Let the car stereo handle calls; use a USB DAC or AUX for media.
- **Power and heat.** Fused automotive DC-DC, ignition sense, safe shutdown,
  thermal cutoff. No Li-ion on the dash.
- **Boot and resume.** Cold boot on the tablet is slow; suspend/resume is a
  feature, not an afterthought.
- **Scope.** Five tiles is already a lot. Ship three live plus two placeholders.

## Success metrics

- Shell starts to a usable state in under 5 seconds from suspend.
- Five tiles reachable in one tap; no text entry while moving.
- Phone bridge stays connected for a one-hour drive.
- Media metadata and controls update within 500ms.
- Notifications appear within 2 seconds.
- OBD data updates at 1Hz without blocking the UI.
- No crash, leak, or thermal shutdown in a 30-minute drive.

## Open questions

1. Is the car's existing stereo staying? (Recommended: yes, additive display.)
2. Which phone, and does it allow a persistent WPA2 hotspot with mDNS clients?
3. Is the proposed phone-hotspot + head-unit-server direction workable, or
   should the car host the access point? (ADR-0007 is proposed, not accepted.)
4. Is full turn-by-turn navigation required in v1, or is maneuver/ETA enough?
5. Which car, and does it have OBD-II / an accessible 12V circuit?
6. Personal project or public release? (Affects AA, licensing, distribution,
   and whether TLS/pairing needs a real UX or a config file.)
