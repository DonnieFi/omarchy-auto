# References

Links and claims checked on 2026-11-01. Everything here can change; re-check
before Phase 6, especially Android Auto and aarch64 support.

## Design reference

- [OmarchyTV](https://github.com/piermark/omarchytv) — MIT. An Omarchy-styled
  launcher ecosystem for Android TV, Google TV, LG webOS, Samsung Tizen, Android
  phones, and a PC/macOS bridge. The launcher is Android/web code, not QML; use
  it for visual grammar, themes, and interaction patterns, not as a codebase to
  embed.

## Host and hardware

- [Omarchy releases](https://github.com/omacom/omarchy/releases) — upstream now
  publishes aarch64 images alongside x86_64.
- [Omarchy 4.5 RC1 and aarch64](https://www.phoronix.com/news/Omarchy-4.5-RC1) —
  initial upstream aarch64 support is centered on Snapdragon X and NVIDIA N1X
  laptops, not the Pi 5.
- [omarchy-pi](https://github.com/vincenth19/omarchy-pi) — MIT, alpha. Builds
  Omarchy 4.0.2 for aarch64 and Pi 5; the README says it was not yet tested on
  real Pi 5 hardware at the time it was checked.
- [Getting Omarchy Running on a Raspberry Pi 5](https://sam-burns.com/posts/omarchy-on-raspberry-pi-5)
  — a real Pi 5 port with an installer (0.2.0, 2026-10-09). Community work, not
  upstream support.

## Android Auto

- [Android for Cars App Library](https://developer.android.com/training/cars/apps/library)
  — template-based apps for Android Auto and Android Automotive OS. This is why
  a third-party Omarchy launcher cannot be installed inside Android Auto.
- [Car App Library templates](https://developers.google.com/cars/design/create-apps/apps-for-drivers/overview)
  — the host controls layout and styling; task flows are constrained.
- [android-auto](https://lib.rs/crates/android-auto) — LGPL-3.0-or-later, 0.3.8
  (April 2026). Rust implementation with navigation, media status, media/speech/
  system audio, input, sensor, and video channel traits, plus wired USB and
  Bluetooth/Wi-Fi wireless support. Young but active; the head-unit example uses
  `openh264` and `eframe`.
- [android-auto-client-rs](https://github.com/jwinarske/android-auto-client-rs) —
  MIT, early work-in-progress Rust head unit.
- [LIVI](https://github.com/f-io/LIVI) — GPL-3.0-or-later. A native CarPlay and
  Android Auto head unit in Rust + Slint, with hardware decode and its own Wi-Fi
  AP/Bluetooth pairing. Useful as an integration reference; do not copy code
  unless this project adopts GPL-compatible licensing.
- [Android Auto resolution field report](https://support.google.com/androidauto/thread/8844203/android-auto-scale-on-car-screen)
  — users report different negotiated modes (800×480, 720p, 1080p, widescreen,
  portrait). Treat 16:9 as a common default, not a guarantee. Verify against the
  actual receiver and phone.

## Tablet and Qt

- Local feasibility notes: `/mnt/adata/opt/omarchy_port_7/flash/omarchy-port-feasibility.md`
  and `/mnt/adata/opt/omarchy_port_7/omarchy-degaswifi/docs/`. These establish
  Qt 6.11.1 and Quickshell 0.3.2 on the Tab 4, not this shell specifically.
- [Qt Quick software adaptation](https://doc.qt.io/qt-6/qtquick-visualcanvas-adaptations-software.html)
  — what the software renderer supports and omits. The tablet probe used
  `QT_QUICK_BACKEND=software`.
