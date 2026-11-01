# Hardware

> **Nov 1, 2026 redirect:** v1 runs on the car's existing Android Auto screen
> from an Android phone. No Linux hardware is required for the demo. This
> document is retained for the future Linux/Quickshell path.

## Current answer

V1 is **Android**: a Car App Library navigation app on the phone, projected to
the car's existing Android Auto screen over USB, plus a phone launcher APK. See
[`BRIEF.md`](BRIEF.md) and ADR-0008. The Linux hardware discussion below is
future work.

The earlier "like OmarchyTV as an app, prototype it, then take it to the head
unit" question was answered with the portable Qt Quick path. The redirect
changes that for v1: Android is faster to a real car demo.

If "like OmarchyTV as an app" means an actual Android launcher APK, that is the
**Android head unit** path: a different project, with Android underneath instead
of Omarchy. It is the easiest route to Android Auto, but it is not this mashup.

## The options

| Option | Pros | Cons | Use it for |
|---|---|---|---|
| **Tab 4 as the final screen** | Free; already ported; 7" touch; Wi-Fi/BT; battery backup | 2014 ARMv7, 1.3GB RAM, no DRM/GPU, 800×1280, dim panel, old 3.10 kernel, battery heat/swelling risk; no AA pixels | Bench prototype only |
| **Tab 4 bench → Pi 5 car** | Reuses the port; Pi 5 can run Arch Linux ARM + Hyprland/Quickshell; community Omarchy Pi images exist (alpha); good CPU/GPU; cheap; active community | Needs screen, DC-DC, audio, UPS, mount; Pi 5 Omarchy images are alpha; AA pixel decode is software/limited; no built-in battery | **Recommended car target** |
| **x86 N100 mini PC** | Easiest performance; software H.264 decode is trivial; runs full Omarchy; ample RAM/storage; good for AA | Bigger, more power, needs 12V DC-DC and thermal design; no battery | Best if AA pixels or heavy nav are priorities |
| **Android head unit / tablet** | Easiest AA (Headunit Reloaded, etc.); cheap; mature car launchers | Not Omarchy/Linux; Android app constraints; launcher/AA limitations; less hackable | A different product |

## Recommended path

### Bench (now)

- Samsung Galaxy Tab 4 7.0 (`SM-T230NU`), postmarketOS + LXQt/Xorg.
- Qt 6.11.1, Quickshell 0.3.2, software rendering.
- Run `shell/main.qml` with
  `QT_QUICK_BACKEND=software /usr/lib/qt6/bin/qmlscene`.
- Use it to prove layout, touch targets, theme, and the five-tile interaction.
- Not yet run on this tablet as of 2026-11-01; the tablet was offline for the
  last host-side check.

### Car (after the prototype)

**Option A — Raspberry Pi 5 (8GB)**

Upstream Omarchy now publishes aarch64 images (4.5+), but Pi 5 is still a
community target: `omarchy-pi` is alpha and was not tested on real Pi 5
hardware when checked; Sam Burns' Pi 5 port is further along. Validate a Pi 5
image before buying the car hardware.

- 7" or 8" 1280×800 HDMI touchscreen, 1000-nit class, matte/anti-glare.
- Automotive 12V→5V 5A DC-DC with ignition sense; fuse at the battery.
- UPS/supercapacitor HAT or a small 12V UPS for clean shutdown.
- USB DAC (PCM2704/PCM5102) or the screen's 3.5mm output → car AUX.
- USB GPS (u-blox) and ELM327 OBD-II adapter.
- Active cooling; the car cabin can exceed 60°C.

**Option B — x86 N100 mini PC**

- Same screen, DC-DC, UPS, DAC, GPS, OBD.
- More headroom for Android Auto decode, MapLibre/Valhalla, and a browser.
- Larger enclosure and higher idle power; use a proper 12V→19V/5V supply.

### Do not

- Do not leave the Tab 4's lithium battery on a hot dashboard. If the tablet is
  used in the car at all, remove it from the final build or use it only while
  the cabin is cool.
- Do not tap airbag circuits or unfused battery positives.
- Do not replace the factory head unit in v1.

## Power and thermal notes

- Use an automotive-grade DC-DC (9–36V input), not a cheap buck converter.
- Add a fuse at the battery; keep wiring away from airbags and moving parts.
- Ignition sense → GPIO/ADC → safe shutdown after a delay.
- A UPS/supercap prevents corruption on cranking and gives a clean shutdown.
- Measure cabin temperature; add a fan and a thermal cutoff.
- Test cold start, cranking, accessory mode, and shutdown.

## Audio notes

- v1: car stereo handles calls and radio; the head unit sends media via AUX or
  Bluetooth.
- A2DP latency is fine for music, bad for video; do not build a video player.
- A USB DAC is usually cleaner than the tablet's headphone jack.
- HFP calls through Linux are a later project; keep them on the car's Bluetooth.

## Screen notes

- Android Auto negotiates a resolution and aspect ratio with the receiver.
  Common modes are 800×480, 1280×720, and 1920×1080; widescreen modes exist,
  and some cars letterbox a 16:9 stream into a wider panel. Do not assume a
  1280×800 screen maps 1:1; test the receiver and expect either letterboxing or
  a custom layout if AA pixels are added later.
- 800×480 is 5:3; the shell should also work there for smaller screens.
- Brightness matters more than resolution in a car. 1000 nits and anti-glare
  beat a dim 1440p panel.
- Capacitive touch with glove-friendly targets (64dp minimum).

## BOM sketch

| Part | Notes | Rough cost |
|---|---|---|
| Raspberry Pi 5 8GB | Or N100 mini PC | $80–$120 |
| 7–8" 1280×800 HDMI touchscreen | 1000-nit class, matte | $80–$150 |
| Automotive 12V→5V 5A DC-DC | Ignition sense preferred | $20–$40 |
| Fuse + wiring + connectors | Safety first | $15–$30 |
| UPS/supercap HAT | Clean shutdown | $25–$60 |
| USB DAC | PCM2704/PCM5102 | $15–$30 |
| USB GPS | u-blox 7/8 | $15–$30 |
| ELM327 OBD-II | Bluetooth or USB | $15–$30 |
| Mount/bezel | RAM mount for prototype; 3D print later | $20–$80 |

Budget: roughly $250–$500 depending on screen and UPS. The Tab 4 prototype is
free.

## The one-line rule

**Prototype on the tablet, ship on a Pi 5 or x86 box, keep the car's stereo.**
