# Task: get Omarchy Auto onto the owner's real Android Auto screen using Play Internal App Sharing

You are taking over the existing Omarchy Auto Android project. Do not rewrite the architecture. It is already a Car App Library navigation app; the missing piece is that Android Auto will not trust an ADB-sideloaded Car App Library app. Use the owner's Play developer account and Play Internal App Sharing to install it as a trusted Play app, then verify it on the real car screen.

- **Repo:** `/mnt/adata/opt/omarchyauto`
- **Remote:** https://github.com/DonnieFi/omarchy-auto — private, do not make it public.
- **Branch:** `feat/android-first`
- **PR:** #1, open. Do not merge to main without review.
- **Phone:** Samsung SM-S721W, Android 17, currently reachable over wireless ADB. Check `adb devices -l`; the endpoint may change.
- **Android Auto version on phone:** 17.9.664054-release as last checked.

> **Current private build (2026-11-02):** versionCode 9 / 0.1.8, signed AAB at
> `release/omarchy-auto-0.1.8-aa-release.aab` (SHA-256
> `5677400674114c9fe94b5e72d42429178487eb38cf515bbbef3298afd0f36cc9`).
> 0.1.1 was already installed through Internal App Sharing and verified on the
> owner's Kia; upload this 0.1.8 AAB to update it. The debug-APK steps below are
> retained as fallback history.

## Current app state

- Two APKs: `auto` (`com.omarchy.auto`) and `launcher` (`com.omarchy.auto.launcher`).
- The AA app is Car App Library NAVIGATION, `NavigationTemplate`, `AppManager.setSurfaceCallback`.
- UI: all tiles minimized at launch; narrow right-side vertical rail; tapping a tile opens a large stage; tapping again minimizes.
- Live: MediaSession, notifications, GPS speed. Vehicle is mock.
- Current Drive stage is a demo map, not MapLibre/real navigation.
- Notification refresh/dismiss controls and larger play/pause are in the latest branch.
- Tokyo Night uses the tracked Tom Ballard Tokyo Studio image at `core/src/main/assets/themes/tokyo-night.webp` (Tom confirmed open-source distribution on 2026-11-02). `local-assets/` is optional and ignored by git for owner-specific private overrides.
- Build toolchain: Gradle 9.8.2, AGP 9.5, built-in Kotlin, compileSdk 37, targetSdk 36.
- Current debug APK: `auto/build/outputs/apk/debug/auto-debug.apk`.

## Rules

- This is a private internal test, not a public Play release. No store listing, no review, no main push without PR/review.
- Do not commit keystores, Play credentials, `local.properties`, or APKs.
- Do not treat mock Vehicle/demo map/test media as real in screenshots or reports.
- Do not touch ports 18789/18799 or other agents' processes.
- If you cannot access Play Console, do not ask for the password/token in chat. Produce exact owner steps and stop; do not fake success.

## Steps

### 1. Build the latest private AAB

```sh
cd /mnt/adata/opt/omarchyauto
./gradlew :auto:bundleRelease
```

Use `release/omarchy-auto-0.1.8-aa-release.aab` (versionCode 9) and record its
SHA-256. The Tokyo Night Tom Ballard image is tracked and included; add
`-Pomarchy.includeLocalAssets=true` only for an owner-specific private override.
Run `./gradlew verifyPublicReleaseAssets` before handing a public-track archive
to CI/Play.

### 2. Create/use the Play Console app

- Package name: `com.omarchy.auto`
- Private app; no public listing needed.
- Use Internal app sharing (Test and release → Internal app sharing), not a public/internal test track unless necessary.
- Upload the signed AAB. Internal App Sharing re-signs it with a test certificate.

### 3. Install from the sharing link

A Play-installed 0.1.1 can update in place through the sharing link. If the phone
has an ADB-signed copy instead, uninstall it first so the signature can be
replaced:

```sh
adb -s <serial> uninstall com.omarchy.auto
```

Open the Play sharing link on the phone and install. Confirm the installer is Play:

```sh
adb -s <serial> shell dumpsys package com.omarchy.auto | grep -E 'installerPackageName|versionName'
```

### 4. Grant permissions

```sh
adb -s <serial> shell am start -n com.omarchy.auto/com.omarchy.auto.core.SetupActivity
adb -s <serial> shell cmd notification allow_listener com.omarchy.auto/com.omarchy.auto.core.OmarchyNotificationListener
adb -s <serial> shell pm grant com.omarchy.auto android.permission.ACCESS_FINE_LOCATION
adb -s <serial> shell pm grant com.omarchy.auto android.permission.POST_NOTIFICATIONS
```

Also open the setup screen on the phone and confirm the status checks.

### 5. Real car test

Connect USB. Android Auto should now list Omarchy Auto because it came from Play. Developer mode/Unknown sources should not be required.

Launch it and verify:

- The minimised rail appears on the AA screen.
- Tapping a rail tile opens the stage.
- Theme switch works.
- Media tile shows a live track with Spotify/YouTube Music.
- Comms shows real notifications; refresh/dismiss icons work.
- Drive shows GPS speed; map remains the labelled demo.
- Vehicle remains visibly mock.

Take a real photo/screenshot of the car screen. Label it as real AA footage. Do not use the phone launcher screenshot as car evidence.

### 6. If it still does not appear

- Confirm the app is installed from Play, not ADB.
- Reboot the phone/AA, retry USB.
- Capture:

  ```sh
  adb -s <serial> logcat -b all -v time > /tmp/omarchy-aa-logcat.txt
  ```

- Filter for `gearhead`, `CAR.AUTH`, `PlayGearheadService`, `Finsky`, `androidx.car.app`, `omarchy`.
- Record whether AA shows the app at all, and report the exact failure. Do not claim success without a real car screen.

### 7. Update repo

- Add the verified car result to `README.md` / `docs/BRIEF.md` only after it actually works.
- Open/update a PR; keep the repo private.
- Do not merge without review.

## Acceptance criteria

- `com.omarchy.auto` is installed from Play Internal App Sharing.
- Omarchy Auto appears on the real Android Auto screen in the car.
- Theme, media, notifications, and GPS are verified there.
- A real car screenshot/photo is captured.
- If it cannot appear, the agent reports the precise AA host/filter error instead of inventing a workaround.

## Out of scope for this task

- MapLibre/real navigation. The agreed V1 map is a separate follow-up: real basemap + live location, no turn-by-turn; use MapLibre/OpenFreeMap and reuse Omastorm's Mercator/style ideas, not its Rust radar engine.
- Public Play release/review.
- Rooting or patching the phone/AA app.
