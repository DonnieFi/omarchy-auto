# The prompt

> **Historical note:** this was the Linux-first prompt review. On 2026-11-01
> the project redirected to Android for v1 (ADR-0008). Keep this as context for
> the future Linux/Quickshell path.

## What was originally asked

The shared ChatGPT conversation contains three user messages (the assistant's
replies did not save):

1. "Do you think it's possible to get Omarchy onto android auto"
2. "Take a look at omarchy tv. Im thinking something like that"
3. "Guess easy to start, an app that looks like it with NAV, music,
   notifications, other omarchy style plugins, we could pick 4 or 5"

## What's strong

- A clear aesthetic reference: OmarchyTV.
- A concrete feature list: navigation, music, notifications, 4–5 plugins.
- The right instinct: start with a small app, not a full head-unit replacement.

## What's missing

- **"Onto Android Auto" is three different projects.** Install Omarchy on the
  head unit? Build an app inside Android Auto? Project Android Auto into
  Omarchy? Each has a different ceiling.
- **Android Auto is not a skinable OS.** It is a projection protocol plus a
  Google/OEM-controlled template runtime. Third-party apps cannot replace the
  launcher or restyle the UI.
- **No target hardware.** Tablet, Pi, x86, or Android head unit?
- **No vehicle context.** Existing stereo? Additive display or replacement?
  OBD-II? 12V access? Airbag clearance?
- **No definition of "looks like it."** Theme only? Tiling layout? Plugin model?
  Workspaces?
- **No data sources.** Where do nav, music, notifications, and GPS come from?
- **No safety/legal constraints.** Driver distraction, video-in-motion, battery
  heat, mounting.
- **No definition of done.** A demo? A daily driver? A public project?

## The better prompt

```text
Act as a product architect and embedded/Linux engineer. I want to build a car
head-unit UI in the visual language of Omarchy/OmarchyTV for a 7-inch 1280x800
touchscreen. The screen is driven by Linux (Quickshell/Qt Quick; target hardware
is either an ARMv7 Galaxy Tab 4 for prototyping or a Raspberry Pi 5/x86 mini PC
for the car). I want five tiles: Drive/Nav, Media, Notifications/Messages,
Vehicle data, Home/Ambience. My phone is Android. I want the head unit to use the
phone for connectivity, GPS, media, notifications, and later Android Auto.

Constraints and assumptions:
- Android Auto apps are template-based; I am not trying to skin AA or replace its
  launcher.
- Evaluate three architectures: (A) Omarchy host + AA projection as a fullscreen
  app, (A') Omarchy host + AA receiver as a data source (nav/media channels, no
  video), (B) Omarchy host + Android companion bridge over Wi-Fi, (C) a Car App
  Library app.
- Prototype hardware is an ARMv7 tablet with 1.3 GB RAM, no DRM/GPU, software Qt
  rendering; car hardware can be a Pi 5/x86.
- Hobby project, ~$300 budget, part-time.

Deliver: a recommendation, a component diagram, a 6-8 week phased plan with exit
criteria, 5 de-risking spikes with pass/fail, a BOM, and the top 5 risks. Be
explicit about what is impossible or unsafe.
```

## Why this version is better

- It separates the host UI from the projection protocol.
- It names the three architectures and asks for a recommendation.
- It gives the hardware constraints that decide what is feasible.
- It asks for spikes and pass/fail, not just a feature list.
- It forces the impossible/unsafe parts into the open.
