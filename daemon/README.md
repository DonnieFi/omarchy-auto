# Daemon

> **Future Linux path.** Android v1 does not use this daemon; the two APKs read
> live data directly. This is retained for the Quickshell/car-computer path.

`omarchy-drived` is the system integration layer between the car shell and the
outside world. It does not draw anything. It owns the things a QML view should
not touch directly: D-Bus, BlueZ, NetworkManager, UPower, OBD-II, GPS, and the
phone bridge.

## Why a daemon

- **One connection, many views.** The shell can restart without dropping the
  phone WebSocket or the OBD adapter.
- **Blocking I/O belongs in a process that can block.** Serial reads, D-Bus
  timeouts, and reconnects should not stutter the UI thread.
- **The data boundary outlives the transport.** The same `MediaState`,
  `NavState`, `VehicleState`, and `Notification` models can be fed by MPRIS,
  by the companion app, or — later — by an Android Auto receiver.

## Planned surface

A small JSON-RPC or property-model server over a local socket or D-Bus:

```text
MediaState     title, artist, album, artUrl, playing, position, duration, source
NavState       active, maneuver, road, distance, eta, remaining, speedLimit
VehicleState   speed, rpm, coolant, fuel, evSoc, range, dtcs[], obdConnected
Notification   id, app, title, text, time, unread, canReply, actions[]
PhoneState     connected, battery, signal, name, hotspot
```

Planned adapters:

| Adapter | Source | Notes |
|---|---|---|
| Media | MPRIS over D-Bus | Playerctl-compatible; works with local players |
| Phone media | Companion WebSocket | Metadata + transport for phone playback |
| Notifications | Companion WebSocket | `NotificationListenerService` |
| GPS | gpsd / serial NMEA / companion | Prefer a USB u-blox in the car |
| Vehicle | ELM327 over Bluetooth serial | 1 Hz polling, cached values |
| Power | UPower + GPIO ignition sense | Safe shutdown |
| Network | NetworkManager | Bridge link state and signal |

## Phone bridge

Per the proposed ADR-0007, the daemon is the WebSocket server for the phone
bridge. Bind to the bridge interface (the Wi-Fi link to the phone) only,
advertise `_omarchyauto._tcp` over mDNS, require the paired token, and use
pinned TLS. The phone is a client and owns reconnect
backoff; the daemon accepts one authenticated companion and drops stale
connections. Do not log notification bodies.

If mDNS is unavailable, the phone can use a manually entered host and port; the
protocol does not change.

## Boundary rules

- Never send raw D-Bus or serial data to QML.
- Every value has a timestamp and a `stale` flag. The UI shows stale data
  differently from live data.
- Writes (play/pause, reply, scene activation) are commands with explicit
  results, not fire-and-forget.
- The fake adapter is a first-class implementation. The shell must run with
  `--fake` and no hardware.
- Treat the phone link as untrusted input: validate message sizes and schemas,
  and never execute a command from the network without an allow-list.

## Status

Empty. This directory is a placeholder for the Phase 2 daemon. The contract is
in `docs/BRIEF.md`. The phone bridge transport is WebSocket, per ADR-0007. The
shell-side transport is still open: a Unix socket with JSON lines (simplest),
D-Bus (best desktop integration), or WebSocket (fewer protocols).

## First spike

1. MPRIS → JSON lines on a Unix socket at 2 Hz.
2. A QML `MediaModel` reading it behind a `fake` switch.
3. Then add the companion WebSocket as a second source for the same model.
