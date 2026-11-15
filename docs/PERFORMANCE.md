# Redraw and battery budget

This is the implementation audit for `omarchy-133.6`. It is not a substitute
for a 30-minute real-car drive measurement.

## Current behaviour

- **Idle phone launcher:** redraws once at the next minute boundary, not every
  second. Clock changes and battery changes still request a redraw.
- **Idle Android Auto surface:** the fallback redraw is also minute-aligned.
  State changes (media, notifications, GPS, battery) request an immediate draw.
- **Media playing:** `MediaWatcher` publishes progress once per second so the
  progress bar and elapsed time stay live. It stops when playback stops.
- **GPS:** updates are capped at the platform's 1-second request interval.
  `LiveDataStore` drops a state update when the resulting immutable state is
  equal, which avoids redraws for stationary duplicate fixes.
- **Battery:** one sticky `ACTION_BATTERY_CHANGED` read at start, then only
  broadcast changes. The footer draws an icon, percentage, and charging tint.
- **Map:** snapshots while the map viewport is active. Follow mode requests at
  most one snapshot per 1.5 s after 10 m or 12°. Panning uses the 2.5 s interval
  with the same gate. The "updating map" badge follows that move or a zoom
  change of about 0.45. Age alone, while stopped, does not mark the bitmap
  stale. MapLibre snapshotting is the largest CPU cost on the head unit, so this
  cadence is the main throttle.
- **Wallpaper:** the scaled background, scrim, and vignette are composed once
  into a screen-sized frame and blitted 1:1 until the bitmap, size, or theme
  changes. Previously every GPS tick ran a 1600x900 source through a scale
  matrix.
- **Android Auto surface:** the canvas is the themed shell. The Drive tile
  draws the map, route, marker, moving-only speed box, and attribution. The
  host routing card is omitted; guidance stays on the canvas. Redraw requests
  are coalesced into one 16 ms frame, and the surface uses a hardware canvas
  when the host supports it, falling back to software once. There is no host
  map action strip, so zoom/pan controls are never drawn on top of the rail or
  another stage.
- **Backgrounds:** decoded off the draw thread with latest-request-wins; a
  background change triggers one redraw.

## Remaining measurements

- 30-minute drive CPU/battery sample.
- Memory growth while switching themes, maps, and media art repeatedly.
- Real-car confirmation that minute-aligned idle redraw is visually sufficient
  and that media progress remains smooth.

Until those are captured, treat this as a code-level redraw budget, not a
car-proven battery result.
