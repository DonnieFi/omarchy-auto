# Contributing

Omarchy Auto is a pre-alpha Android Auto and phone dashboard with a high bar
for honesty. The car demo is the point; screenshots and claims must match what
actually ran.

## Build

```sh
export ANDROID_HOME=/path/to/android-sdk   # platform 37, build-tools 37.0.0
./gradlew assembleDebug
```

Toolchain: Gradle 9.8.2, AGP 9.5.0, built-in Kotlin, compileSdk 37, targetSdk
36, JDK 17+.

APKs:

```text
auto/build/outputs/apk/debug/auto-debug.apk
launcher/build/outputs/apk/debug/launcher-debug.apk
```

## Before a PR

- Build both APKs and run `./gradlew testDebugUnitTest verifyPublicReleaseAssets`.
- Run `./gradlew assembleDebug` from a clean clone if you changed Gradle files.
- Test the phone launcher on an emulator or device.
- If you changed the car surface/template, state whether you tested it on a
  real Android Auto host. "Compiles" is not "runs on a car".
- Include a 1280×720 capture for visual changes. Label the source: phone
  launcher, AA host, or renderer preview.
- Do **not** present test media, notifications, or mock tiles as real data.
  Label fixtures and mock content.
- Do not commit `local.properties`, keystores, APKs, or generated build output.
- Do not commit private wallpaper overrides from `local-assets/` (ignored);
  Tom Ballard's Tokyo Studio background is tracked at
  `core/src/main/assets/themes/tokyo-night.webp` with credit in
  [`CREDITS.md`](CREDITS.md) and [`docs/ASSETS.md`](docs/ASSETS.md).

## PRs and reviews

Open a PR even for small changes. Keep `main` green. Reviews should be red on
unverified car claims, unlabeled mock data, or committed secrets.

## Android Auto caveat

Current Google documentation says Android Auto's "Unknown sources" setting
covers media, messaging, and parked apps, but not necessarily Car App Library
apps. A sideloaded navigation app may not appear in a current AA launcher. Test
the exact phone/car combination, or use Play Internal App Sharing, before
claiming support.

## Linux/QML path

`shell/` is retained future work, not part of the Android APKs. Do not remove
it without an ADR.

## Who merges

Anyone can file issues and open PRs from a fork. Only the maintainer
(@DonnieFi) merges to `main`. `main` is protected: PR required, code-owner
approval, green CI, no force-push.

## Before you open a PR

1. **Bugs / tiny fixes:** PR directly, linking any existing issue.
2. **Features or anything non-trivial:** open an issue first and wait for a
   maintainer 👍. During alpha, unsolicited feature PRs may be closed.
3. **Not accepted:** refactor-only PRs, drive-by formatting, dependency bumps
   (Dependabot handles those), test/CI tweaks for failures already red on `main`.
4. One concern per PR. Keep it small. Leave **Allow edits by maintainers** on.
5. Fill in the PR template, including how you tested on a real AA host.

## AI-assisted contributions

Welcome, same bar as any PR. You must understand and be able to explain every
line, and have actually run it. Untested AI output or bulk AI PRs get closed.

## Never commit

Keystores, `local.properties`, signing configs with secrets, AAB/APK files,
Play credentials, API keys. Release signing happens outside this repo.

## Security

Do not file public issues for vulnerabilities. See [SECURITY.md](SECURITY.md).

## License

By contributing you agree your contribution is licensed under the repo's MIT
License.
