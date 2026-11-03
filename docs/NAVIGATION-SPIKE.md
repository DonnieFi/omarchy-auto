# Routing alpha spike — omarchy-2gd (from research omarchy-984)

The owner approved implementation in chat on 2026-11-02. Routing is the objective; weather comes later. Existing map code may be replaced.

## Implementation

`:navigation-spike` is an installable Android application (`com.omarchy.auto.navspike`, label **Omarchy Routing Spike**) alongside the existing dashboard. It replaces the dashboard snapshot path for this experiment with Ferrostar's live MapLibre navigation UI. This isolates the routing evaluation before choosing the dashboard integration seam.

- Ferrostar 0.59.0 native guidance engine and Android Auto car-app modules.
- Stadia Maps hosted Valhalla, `auto` profile, kilometers, English Canadian instructions.
- OpenFreeMap dark basemap; no Google Navigation SDK or Google location dependency.
- Phone coordinate destination entry, real GPS mode, explicit 2× simulated mode, spoken guidance, stop/mute, rerouting, and arrival.
- Android Auto surface UI with navigation templates, NavigationManager bridge, turn notifications, safe areas, zoom/recenter, and attribution. Surface teardown preserves the process-scoped route so a reconnect can reattach. Process death does not restore navigation.
- A simulated wrong-turn control offsets the recorded route by 120 m, reports a confident fix, and resumes on the replacement route once Valhalla responds.
- Runtime API-key entry in private app preferences; backups disabled; Forget API key removes it. No key is embedded in the APK. Public distribution should use each tester's own key or a separately designed server setup.

**Compatibility finding:** Ferrostar's `ui-maplibre-car-app:0.59.0` requires Android 10/API 29. Only the spike module raises the minimum; the dashboard retains its existing minimum.

## Run

```sh
./gradlew :navigation-spike:assembleDebug
adb install -r navigation-spike/build/outputs/apk/debug/navigation-spike-debug.apk
adb shell am start -n com.omarchy.auto.navspike/.SpikeActivity
```

1. Create a free noncommercial Stadia Maps account at https://client.stadiamaps.com/ and create an API key.
2. Open **Omarchy Routing Spike** on the phone while parked. Allow precise location and notifications. Android's foreground location service requires location permission even in this simulated test.
3. Paste your key. Leave the Halifax coordinates or enter another destination as `latitude, longitude`.
4. Leave simulation enabled to test at a desk, or disable it for real GPS. Live mode waits up to 30 seconds for a fix no older than 30 seconds, with accuracy at most 100 m. Do not enter a simulation origin for a live drive.
5. Start route. The phone displays the live route, maneuvers, and progress; open the separate spike app in Android Auto to attach the car screen.
6. In simulation, tap **Simulate wrong turn** to request a replacement route. Stop navigation from the phone or car when done.

A normal ADB installation is enough for phone/emulator tests. The repository's previously documented Android Auto Play-install trust requirement still applies on real head units. This separate application ID needs its own trusted installation path; a compiled car service does not prove that it will appear in the owner's car. See `AA-INTERNAL-SHARING.md`. This spike has not been published or uploaded to Play.

## Verification

```sh
./gradlew :navigation-spike:assembleDebug :navigation-spike:testDebugUnitTest :navigation-spike:lintDebug
./gradlew :navigation-spike:connectedDebugAndroidTest
```

Automated coverage:

- Valid coordinates and malformed/out-of-range/non-finite input.
- Native Ferrostar engine parses a recorded Valhalla response and reaches arrival with spoken-instruction callbacks.
- Accurate off-route positions cause another route request and route replacement.
- HTTP 401 does not start navigation.

The emulator integration tests use a BSD-licensed upstream route fixture and an in-memory HTTP response provider, not live Stadia requests. Spoken callbacks prove timing, not audible audio output. No API key is needed for these tests.

## What remains to establish

Live Stadia routing with the owner's new key, audible TTS, actual Halifax routing quality, GPS behavior with the phone screen off, and Android Auto head-unit rendering/reconnect still require hands-on verification. No real-road success is claimed. Keep omarchy-2gd open for this validation.

The alpha deliberately has coordinate entry rather than address search, no weather overlay, no offline routing, and no live traffic. It is an evaluation module, not a Play-ready navigation product. Android Auto review auto-drive/intents and production host validation need completion before public distribution.

Stadia's free noncommercial allowance is 200,000 shared credits/month; standard routes cost 20 credits/request. Reroutes consume requests. At most 10,000 routes fit if nothing else uses the pool. Live traffic requires a paid plan. OpenFreeMap basemaps avoid consuming Stadia tile credits. Provider limits may change; see https://stadiamaps.com/pricing and https://docs.stadiamaps.com/limits/.

## Recommendation

The owner accepted this first test on 2026-11-02 and chose to proceed with Ferrostar + hosted Valhalla. Merge is deferred until the other branch is finished; key-backed and in-car checks remain follow-up validation. The native integration works in the emulator, but dashboard composition and real head-unit trust/rendering remain unproven. Keep the hosted provider replaceable with self-hosted Valhalla; no self-hosting is required for this alpha.

## Debug replay and emulator screenshots

The debug APK includes a recorded **driving** route from Ferrostar's fixtures (New Jersey), replayed at 12×. Start it without a Stadia key:

```sh
adb shell pm grant com.omarchy.auto.navspike android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.omarchy.auto.navspike android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.omarchy.auto.navspike android.permission.POST_NOTIFICATIONS
adb shell am start -n com.omarchy.auto.navspike/.ReplayActivity
```

The replay activity and recording are under `src/debug` and absent from release builds. Its header explicitly says RECORDED REPLAY. Route responses use the fixture; OpenFreeMap tiles still load over the network. This verifies real on-device map/guidance rendering, not Stadia availability, live driving quality, or real head-unit projection. The separate native speech-trigger test uses an upstream walking-route fixture containing recorded spoken instructions.

Landscape and portrait map/guidance screens were visually checked on the API 35 emulator. System-bar spacing and readable status text were corrected after the first capture. Build, lint, two JVM tests and three native emulator tests pass.

## Accepted direction and deferred merge handoff

Owner decision, 2026-11-02: **go with this stack**. The first emulator test is accepted. Keep `feat/ferrostar-routing-spike` available and wait until the current `feat/tom-open-launch-targets` work is finished and merged into the intended destination (normally `main`). Do not merge now or overwrite the other checkout's uncommitted changes. If that work ends up on a different branch, use its final merged destination instead of assuming the name below.

The routing implementation starts at commit `463ebb7`; subsequent commits on the same branch include this handoff. Merge the branch tip, not just the first commit.

After the other branch lands, use a **clean integration checkout**. For example, from the repository:

```sh
git fetch origin
git worktree add -b integrate/ferrostar-routing ../omarchy-auto-routing-integration origin/main
cd ../omarchy-auto-routing-integration
git merge --no-ff origin/feat/ferrostar-routing-spike
```

If a merge conflict occurs, resolve it before continuing. `settings.gradle.kts` must preserve all destination-branch modules and add `include(":navigation-spike")` exactly once. The standalone spike does not require replacing `:auto`, `:core`, or `:launcher` yet. Preserve the other branch's dashboard, notifications, build guards, and assets. The original working checkout contains earlier uncommitted copies of the spike; do not stage those again as unrelated feature work. Compare against the pushed routing branch before removing or overwriting any local copies.

Configure `local.properties` with the local Android SDK path (do not commit it), then run:

```sh
./gradlew :auto:assembleDebug :launcher:assembleDebug :core:testDebugUnitTest \
  :navigation-spike:assembleDebug :navigation-spike:testDebugUnitTest :navigation-spike:lintDebug
./gradlew :navigation-spike:connectedDebugAndroidTest
```

Run the connected tests with a test emulator available. They install/uninstall the spike, so do not run a manual APK installation at the same time. Reinstall the debug APK afterward to inspect the replay in landscape and portrait. Confirm the destination branch's existing quality checks and CI pass too.

Once integration checks pass, push `integrate/ferrostar-routing` and open a PR to `main` for the deferred merge. Do not force-push either feature branch or publish to Play as part of this handoff. After merging, record the merge commit and validation in `omarchy-2gd`.

Acceptance of this first test is not a claim that the fixture calculates fresh routes: local off-route detection works, but replay returns the recorded response again. Verify real rerouting with a Stadia key (or a configured self-hosted Valhalla service), audible speech, live GPS, and the actual Android Auto head unit before calling live navigation validated. Weather remains later work.
