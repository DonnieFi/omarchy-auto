# Decisions

Short ADRs for the choices that shape the project. Add a new one when a decision
changes; don't rewrite history.

## ADR-0001 — Omarchy is the host; the phone is the bridge

**Status:** accepted (v1)

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

**Status:** accepted

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

## ADR-0004 — Defer the car hardware decision

**Status:** accepted

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

**Decision:** For v1, add a display and keep the car's existing stereo for
calls, radio, and amplification. Use AUX or Bluetooth for media audio. Revisit a
full replacement only after the shell is proven.

**Consequences:** The build is safer and cheaper. Calls and voice stay on the
car's Bluetooth, which is also the right scope cut.

## ADR-0006 — Android Auto as a data source before pixels

**Status:** proposed

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

**Status:** proposed

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

## ADR-0008 — Status and mock data are labeled

**Status:** accepted

**Context:** The prototype footer previously said "BRIDGE READY" and "OBD ·
READY" while every value was fake. That is misleading in screenshots and demos.

**Decision:** The shell labels mock data in the footer and uses "mock" for
unimplemented status. Live data replaces the label when the daemon is connected.

**Consequences:** Demos stay honest. A connected state must be earned by the
daemon, not hard-coded.
