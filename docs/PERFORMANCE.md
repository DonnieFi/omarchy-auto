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
- **Map:** snapshots only while Drive is the active stage; the renderer requests
  at most one snapshot per 2.5 s, requires 20 m movement or a 20° turn, and
  treats an image older than 6 s plus 30 m of movement as stale.
- **Backgrounds:** decoded off the draw thread with latest-request-wins; a
  background change triggers one redraw.

## Remaining measurements

- 30-minute drive CPU/battery sample.
- Memory growth while switching themes, maps, and media art repeatedly.
- Real-car confirmation that minute-aligned idle redraw is visually sufficient
  and that media progress remains smooth.

Until those are captured, treat this as a code-level redraw budget, not a
car-proven battery result.
