# Companion

An Android app that turns the phone into the car shell's data source. It is not
a launcher and it does not draw the car UI. It streams the things the phone
knows — notifications, media metadata, location, navigation state — to
`omarchy-drived` over the local network.

## Scope

In:

- Foreground service (`connectedDevice`/`dataSync`) with a persistent
  notification.
- WebSocket client to the head unit over WSS, mDNS discovery, pairing token,
  pinned certificate, reconnect with backoff.
- `NotificationListenerService`: app, title, text, time, actions, unread.
- `MediaSessionManager`: title, artist, album, art, playback state, position.
- `LocationManager`: GPS fixes at 1 Hz while driving.
- Quick reply via `RemoteInput` where the notification supports it.
- Navigation handoff: receive a destination and open it in the phone's map app;
  send back the active maneuver/ETA when available.
- Battery, signal, and hotspot state.

Out:

- Android Auto projection. That is a different API surface.
- Calls and voice assistant routing. Keep those on the car's Bluetooth.
- Rendering the Omarchy UI. The head unit owns pixels.
- Background location when the car is not connected.

## Permissions, grants, and privacy

- Notification access is a user-granted `NotificationListenerService` grant; it
  can read every notification. Bridge an allow-list (messages, media, calendar)
  and exclude OTP/2FA, banking, and password-manager notifications by default.
- Media session access uses the same notification-listener grant on modern
  Android; test the target Android version.
- Location needs fine-location permission and a foreground-service location
  type; do not request background location.
- Android 13+ needs notification permission for the persistent service notice.
- Ask for battery-optimization exemption, but test each OEM's background
  restrictions. A foreground service is not a guarantee that a vendor will let
  the socket live.
- Never log notification bodies, tokens, or location history.

## Why not just Android Auto?

Android Auto apps are template-based and hosted by Google's runtime. A third
party cannot replace the launcher, theme the UI, or control the whole screen.
The companion gets the same data without asking permission from the projection
protocol, and it works with any Linux head unit.

## Network and pairing

The proposed flow, recorded in ADR-0007:

1. The phone hosts a dedicated WPA2 hotspot. The head unit joins it once from
   its setup screen, so the phone keeps cellular data for maps and traffic.
2. `omarchy-drived` runs the WebSocket server, bound to the bridge interface
   (the Wi-Fi link to the phone), and advertises `_omarchyauto._tcp` with mDNS.
3. The head unit shows a pairing token and certificate fingerprint. The phone
   discovers the service, scans or enters the token, pins the certificate, and
   connects over `wss://`.
4. If mDNS is blocked, the phone falls back to a manually entered host and
   port. Reconnect with exponential backoff; never silently downgrade to
   plaintext outside the bench.

A token alone authenticates the client but does not protect notification
content from the network. The TLS/pinning layer is part of the design, not an
optional hardening pass. Never log message bodies.

## Protocol sketch

JSON messages over WebSocket, one connection per head unit, authenticated with
a shared token from QR pairing:

```json
{ "type": "hello", "token": "...", "device": "Pixel 8", "version": 1 }
{ "type": "media", "title": "...", "artist": "...", "playing": true, "position": 42 }
{ "type": "notification", "id": "...", "app": "Signal", "title": "Maya", "text": "..." }
{ "type": "location", "lat": 49.28, "lon": -123.12, "speed": 12.4, "heading": 271 }
{ "type": "nav", "active": true, "maneuver": "left", "road": "Market St", "distance": 300 }
```

Commands go the other way: `play`, `pause`, `next`, `reply`, `navigate`.

The head unit is the server; the phone connects to it. That keeps discovery,
pairing, and the token on the car side, and lets the phone own reconnect
policy. See ADR-0007 for the hotspot, mDNS, and TLS decision.

## Status

Empty. This directory is a placeholder for the Phase 3 app. No Android project
has been generated yet; the protocol and permission list above are the starting
point, not a contract.

## First spike

1. Empty Android app with a foreground service and a WebSocket client.
2. Send a hard-coded notification and media payload to a laptop listener for
   30 minutes, including screen-off and Wi-Fi roaming. Use plain `ws://` only
   on this bench link.
3. Repeat over `wss://` with a pinned self-signed certificate before adding
   real notification content.
4. Add `NotificationListenerService` and `MediaSessionManager`.
5. Add location only after the first two are stable.
