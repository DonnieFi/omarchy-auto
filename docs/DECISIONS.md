# Decisions

Short ADRs for the choices that shape the project. Add a new one when a decision
changes; don't rewrite history.

## ADR-0001 — Omarchy is the host; the phone is the bridge

**Status:** superseded for v1 by ADR-0008 (2026-11-01); retained as the
Linux/Quickshell future path

**Context:** "Omarchy on Android Auto" conflates a host UI with a projection
protocol. Android Auto does not allow third-party launchers, whole-screen custom
UI, or theme control. The only place Omarchy's UI can live is the head-unit side.

**Decision:** Build a Linux car shell in Omarchy's visual language. For v1, the
phone supplies notifications, media metadata, GPS, and navigation handoff
through a companion app over Wi-Fi. Do not require Android Auto.

**Consequences:** The shell can run on the tablet's Qt runtime; the prototype
itself is not validated there yet. Android Auto becomes an optional data source
later, not a dependency.

## ADR-0002 — Prototype as a portable Qt Quick app, not an Android APK

**Status:** superseded for v1 by ADR-0008 (2026-11-01); the QML shell remains
as the Linux/future prototype

**Context:** OmarchyTV is an Android TV/Google TV/webOS launcher ecosystem with a
PC/macOS bridge: a strong design reference, but its launcher is Android/web
code, not a QML shell. The existing ARMarchy work already proves Qt Quick and
Quickshell on the tablet. An Android APK prototype would fork the stack and lose
the Omarchy/Linux path.

**Decision:** The first artifact is a plain Qt Quick app (`shell/main.qml`) that
runs in a desktop window with `qmlscene`. A thin Quickshell wrapper
(`shell/quickshell.qml`) runs the same UI on the tablet or a Hyprland car
computer.

**Consequences:** Design iteration is fast and portable. Quickshell-specific
features (layer shell, bar integration) wait until the UI is proven.

## ADR-0003 — Fixed five-tile grid, not free tiling

**Status:** accepted

**Context:** Omarchy and OmarchyTV use tiling-window metaphors. Literal tiling
and window dragging are hostile in a moving car: small targets, hidden state,
and too many interactions.

**Decision:** Keep the visual grammar — 2px borders, cut-out btop legends,
monospace type, theme accents, a Waybar-style bar — but use a fixed five-tile
grid. One tile expands fullscreen; back returns.

**Consequences:** The UI reads as Omarchy without asking the driver to manage
windows. It also works on a small screen and with software rendering.

## ADR-0004 — Defer the Linux car hardware decision

**Status:** accepted for the Linux/future path; not needed for Android v1

**Context:** The Tab 4 is already ported and free to use, but it is a 2014
ARMv7 tablet with 1.3GB RAM, no DRM/GPU, low brightness, and a battery that
should not live on a hot dashboard. A Pi 5 or x86 mini PC is a better car
computer but adds cost and packaging work.

**Decision:** Use the Tab 4 as the bench prototype. Build the shell and daemon
so they run on any Linux/Qt host. Choose the car computer after Phase 4, when
the performance and packaging requirements are known. The leading candidates
are a Raspberry Pi 5 (best Omarchy fit) and an x86 N100 mini PC (easiest
performance and Android Auto decode).

**Consequences:** No hardware purchase blocks the prototype. The final build
will not use the Tab 4 as the car screen.

## ADR-0005 — Additive display, not a head-unit replacement

**Status:** accepted (v1)

**Context:** Replacing a factory head unit means CAN bus integration, amplifier
turn-on, climate controls, steering-wheel controls, reverse camera, and a custom
bezel. That is a different project with a much larger risk surface.

**Decision:** Use the car's existing Android Auto screen for the v1 demo and
keep the car's existing stereo for calls, radio, and amplification. Do not
replace or rewire the factory head unit. The Linux path may add a display later.

**Consequences:** The build is safer and cheaper. Calls and voice stay on the
car's Bluetooth, which is also the right scope cut.

## ADR-0006 — Android Auto as a data source before pixels

**Status:** deferred; not v1. V1 is a Car App Library app, not an AA receiver.

**Context:** A full AA receiver needs H.264 decode, USB/Wi-Fi handshakes, and
audio-focus work. The experimental Rust [`android-auto`](https://lib.rs/crates/android-auto)
crate exposes navigation, media/audio, input, sensor, and pluggable video
channels; `android-auto-client-rs` is an early reference. Native head-unit
stacks such as LIVI have also appeared. If those data channels are rich enough,
the shell can render Google's brain in Omarchy's UI without decoding video.

**Decision:** If Android Auto is added, first spike the data-plane approach:
run the receiver, ignore the video, consume nav/media/audio, and render the
existing tiles. Only add pixel projection if the data is insufficient and the
hardware can decode H.264.

**Consequences:** The most interesting integration is also the lightest. It
keeps the door open without betting v1 on an unofficial protocol. Re-check the
receiver landscape before Phase 6; these projects are young and can change
quickly.

## ADR-0007 — Phone bridge: head-unit server, phone hotspot, authenticated transport

**Status:** deferred; not v1. The Android v1 APKs do not use this bridge.

**Context:** The brief says "WebSocket over phone hotspot", the companion says
the head unit is the server, and the daemon says the transport is undecided.
Those are not yet contradictions, but they are not a design. Notifications also
carry private message content, so a bare JSON socket on a shared LAN is not
good enough once this leaves the bench.

**Decision:** For the first bridge spike:

- The phone hosts a dedicated WPA2 hotspot so it keeps cellular data and can
  serve maps; the head unit joins it once through its setup screen.
- `omarchy-drived` is the WebSocket server, bound to the bridge interface (the
  Wi-Fi link to the phone). It advertises `_omarchyauto._tcp` over mDNS. The
  phone is the client and owns reconnect/backoff.
- Pairing shows a token (and certificate fingerprint) on the head unit. The
  phone scans or enters it; the token is required in `hello`.
- Use `wss://` with a self-signed certificate pinned at pairing, or an
  equivalent authenticated-encryption layer. Plain `ws://` is bench-only, on a
  trusted wired/LAN link.
- Bind only to the bridge interface, reject unauthenticated clients, and never
  log notification bodies.

**Consequences:** The Android side is a client, which is simpler than hosting a
server; discovery and pairing stay on the car. If a phone hotspot proves
unstable, use the car's AP or an existing Wi-Fi network with the same
server/client contract. The bridge remains optional; the daemon and shell can
run with fake data.

## ADR-0008 — Android Auto is the v1 target; Kotlin Canvas renderer

**Status:** accepted (2026-11-01 redirect)

**Context:** The fastest path to a real, filmable in-car demo is the car's
existing Android Auto screen, not a new Linux head unit. Android Auto exposes a
navigation app's map via `NavigationTemplate` + `AppManager.setSurfaceCallback`,
but the host owns templates, action strips, and distraction rules. The app
cannot replace the launcher.

Rendering options:

- **Kotlin + Android Canvas**: direct `Surface.lockCanvas`; the same renderer
  can draw into a phone `View`. Smallest surface/lifecycle risk and fastest to a
  demo.
- **Kotlin + Compose Canvas**: pleasant phone UI, but the AA surface is raw
  `Surface`; Compose would need a host View/ComposeView bridge and a Compose
  runtime for no visual gain.
- **QML/Qt for Android**: reuse the existing QML design, but adds a Qt Android
  toolchain, QML runtime, Surface/TextureView bridge, APK size, and lifecycle
  debugging. It is slower to a working AA demo and moves away from the Android
  host.

**Decision:** Build two Android targets around one Kotlin core:

- `auto`: Car App Library `NAVIGATION` app, `NavigationTemplate`, surface
  callback, five tiles, AA action-strip theme action.
- `launcher`: full-screen phone Activity with the same renderer; tile taps open
  real apps; Back returns.
- `core`: theme/model/renderer, MediaSession, NotificationListener, GPS speed,
  setup.
- Four themes (Tokyo Night, Osaka Jade, Gruvbox, Catppuccin); 2px borders,
  cut-out btop legends, monospace, Waybar-style top bar.

**Consequences:** AA chrome/action strip stays host-controlled. Sideloaded
Car App Library availability must be validated against current AA policy. The
Linux/Quickshell path is paused but retained (`shell/`, archive). ADR-0001/0002
are superseded for v1, not deleted.

## ADR-0009 — Status and mock data are labeled

**Status:** accepted

**Context:** The earlier prototype footer said "BRIDGE READY" and "OBD · READY"
while every value was fake. That is misleading in screenshots and demos.

**Decision:** The UI labels mock data (the Vehicle tile is `MOCK`; the footer
says `MOCK VEHICLE`) and uses "WAIT"/"NEEDED" for missing live data. Live data
must come from a real source. Test content used for verification is labeled as
such in the docs.

**Consequences:** Demos stay honest. A connected state must be earned by a real
source, not hard-coded.

## ADR-0010 — Right-side tile rail plus one large content stage

**Status:** accepted (2026-11-01 UI revision)

**Context:** The equal-grid layout made the car content and phone content the
same size. Maps, now playing, and notification lists need more room when
engaged; the five fixed tiles still need to stay one tap away. Torque/OBD is not
ready for the demo.

**Decision:** Put all five small tiles in a narrow right-side, top-to-bottom
rail. The app starts with no tile selected; the stage is empty so the wallpaper
is visible. Tapping a rail tile selects it and fills the large left stage with
that tile's content; tapping it again clears the stage. The stage carries the primary action:
`OPEN MAPS` / `OPEN PLAYER` / `OPEN MESSAGES` / `OPEN SETTINGS` on the phone.
Media gets an in-app play/pause control through `MediaTransport`. The Vehicle
tile stays as a visibly mocked stage but Torque is not launched.

**Consequences:** The rail preserves one-tap access; the stage gives maps and
media a filmable size. Android Auto uses the same rail/stage geometry and
forwarded surface clicks; the host action strip remains theme-only.

## ADR-0011 — Roadster wallpaper is a local, uncommitted asset

**Status:** superseded by ADR-0013 (2026-11-02)

**Context:** The supplied Tom Ballard Tokyo Studio image (the owner calls it the
Tokyo Knight background) is the requested visual anchor, but the upstream
theme's CREDITS says the original photographer/source and media redistribution
licence were not established. The theme's MIT licence covers the
theme code/configuration, not the image.

**Decision:** Do not commit the wallpaper. `local-assets/` is ignored and a
licensed local copy can be embedded in a debug build as `assets/wallpaper.jpg`.
The renderer also reads a user-supplied `wallpaper.jpg` from app-private files.
Clean builds use an original, code-drawn neon-road fallback. The README
screenshot uses the fallback.

**Consequences:** The private demo can show the exact Roadster image on the
owner's phone without redistributing it. Before public launch, either clear the
media rights or keep the original fallback. See `docs/ASSETS.md`.

## ADR-0012 — Ten Omarchy themes with theme-matched backgrounds

**Status:** accepted (2026-11-01 theme expansion)

**Context:** The first build had four palettes and one fixed wallpaper. The
owner asked for more Omarchy themes and for the background to change with the
theme. A UI/UX review recommended dark, low-glare themes, stronger secondary-text
contrast, and a deliberate chooser instead of cycling through many themes.

**Decision:** Ship ten themes: Tokyo Night, Osaka Jade (Jade Horse palette),
Gruvbox, Catppuccin, Kanagawa, Everforest, Nord, Ethereal, Ristretto, and
Hackerman. Each has a matching 1600x900 WebP background resized from the
Omarchy theme tree. Tokyo Night uses Tom Ballard’s Tokyo Studio image, now
tracked with permission (ADR-0013); the other nine are Omarchy-derived.
The top-bar THEME button and the Android Auto action-strip Theme
action open the Home theme grid rather than cycling. Theme selection persists
by id, and the Home picker is a 5x2 grid. Scrim and stage opacity are per-theme;
secondary text uses contrast-adjusted `dim` values.

**Consequences:** The app is 1.2 MB larger from committed background assets and
the theme picker is denser than the old four-chip row. Omarchy's theme code is
MIT, but individual background images may have separate rights; they stay a
private demo until each source is cleared. A future picker overlay should add
larger theme cards and 48dp touch targets for compact displays.

## ADR-0013 — Tokyo Studio artwork is cleared and bundled

**Status:** accepted (2026-11-02)

**Context:** Tom Ballard has confirmed that the Tokyo Studio artwork is open
source and approved its inclusion in the repository, README, public site, and
app builds. ADR-0011 kept the image local-only because the upstream artwork
rights were unresolved.

**Decision:** Track the image as
`core/src/main/assets/themes/tokyo-night.webp` (1600×900, credited to Tom
Ballard) and use it as the default Tokyo Night background in normal debug and
release builds. Keep the hero at `docs/img/hero-tokyo-night.webp`. `local-assets/`
remains an opt-in private override path for other owner-specific assets.
`USE TOM BACKGROUND` copies the tracked theme image into app-private storage so
it survives theme changes; `USE THEME BACKGROUNDS` clears that override.

**Consequences:** ADR-0011 is superseded. The app no longer needs the private
local-assets flag to show Tom’s image. Other Omarchy-derived theme backgrounds
still carry their own per-image rights review.
