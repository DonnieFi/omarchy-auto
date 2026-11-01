# Contributing

This repository is private until launch. Do not publish the repository, builds,
or screenshots without the owner's approval until then.

This is a small prototype with a high bar for honesty. The car demo is the
point; screenshots and claims must match what actually ran.

## Build

```sh
export ANDROID_HOME=/path/to/android-sdk   # platform 35
./gradlew assembleDebug
```

APKs:

```text
auto/build/outputs/apk/debug/auto-debug.apk
launcher/build/outputs/apk/debug/launcher-debug.apk
```

## Before a PR

- Build both APKs.
- Run `./gradlew assembleDebug` from a clean clone if you changed Gradle files.
- Test the phone launcher on an emulator or device.
- If you changed the car surface/template, state whether you tested it on a
  real Android Auto host. "Compiles" is not "runs on a car".
- Include a 1280×720 capture for visual changes. Label the source: phone
  launcher, AA host, or renderer preview.
- Do **not** present test media, notifications, or mock tiles as real data.
  Label fixtures and mock content.
- Do not commit `local.properties`, keystores, APKs, or generated build output.

## PRs and reviews

Open a PR even for small changes. Keep `main` green. The initial scaffold was
committed directly; subsequent work uses branches/PRs. Reviews should be red on
unverified car claims, unlabeled mock data, or committed secrets.

## Android Auto caveat

Current Google documentation says Android Auto's "Unknown sources" setting
covers media, messaging, and parked apps, but not necessarily Car App Library
apps. A sideloaded navigation app may not appear in a current AA launcher. Test
the exact phone/car combination, or use Play Internal App Sharing, before
claiming support.

## Linux/QML path

`shell/` and `docs/archive/` are retained future work, not part of the Android
APKs. Do not remove them without an ADR.
