# Peripheral Battery Companion — Android Stage A1

Native Kotlin / Jetpack Compose demo. All readings are synthetic; no PC connection,
networking, pairing, polling, notifications or device control exists. No INTERNET
permission is requested. Devices and Settings work locally; Refresh demo reloads
unchanged samples. Unknown, stale, device-disconnected and PC-offline presentation
are modeled; the default screen intentionally shows only the three available samples.

## Build and test

Open this existing checkout in Android Studio. Use JDK 17, Android SDK platform 35,
build-tools 35.0.0 and Gradle 8.9. AGP is pinned to 8.7.3, Kotlin/Compose compiler to
2.0.21, Compose BOM to 2024.12.01, lifecycle to 2.8.7 and activity to 1.9.3.
These are selected compatible versions, **not verified by a build in this session**.
Set ANDROID_HOME or create an ignored local.properties with sdk.dir. Accept SDK
licenses through the normal SDK manager. No secrets are required.

The environment has no Gradle distribution or wrapper JAR and cannot download one
while its proxy is unavailable. After connectivity recovery, install trusted Gradle
8.9 and run from this directory:

```sh
gradle wrapper --gradle-version 8.9 --distribution-type bin
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
# With an emulator or USB device available:
./gradlew connectedDebugAndroidTest
```

Verify the Gradle distribution against Gradle's official SHA-256 and set
`distributionSha256Sum` in wrapper properties before committing generated wrapper
files. Do not disable TLS, signature or checksum verification. Use this existing
checkout; cloud tasks are isolated and do not need another Git worktree.

Expected APK: `app/build/outputs/apk/debug/app-debug.apk`.
Application ID: `dev.peripheral.battery`; version `0.1.0-demo` (code 1).
No APK has been produced yet. Build, lint, unit and instrumented tests are pending.

## Structure

`domain/` contains immutable readings and a transport-neutral repository contract;
`data/` provides synthetic samples; `ui/` maps presentation and owns StateFlow state.
MainActivity renders responsive, scrolling Compose cards and local navigation.
The battery model validates 0–100 rather than silently accepting invalid values.
See [future API proposal](docs/battery-api-contract-v1.md).

## Assets and visual review

Original PNGs in assets/ are unchanged. Lossless WebP copies in drawable-nodpi use
at most 960×640 pixels, preserve alpha/aspect ratio, and render with ContentScale.Fit:

| Original | Resource |
| --- | --- |
| RGB Lightspeed Gaming Keyboard Cutout.png | keyboard.webp |
| Matte Black Gaming Mouse with Blue Glow (1).png | mouse.webp |
| HyperX-Style Black and Red Gaming Headset (1).png | headset.webp |

Reference: `references/Neon Peripheral Battery Companion UI.png`. It is guidance,
not a displayed background. Intentional differences: explicit demo banner, no fake
live timestamps or Connected status, no History destination, and scrolling adaptive
cards. Supplied imagery is illustrative; no hardware identity claim is made.

Rendered screenshots and visual comparison are pending a working Android runtime.
Compare a portrait capture with the reference for dark background, cyan edging,
three distinct uncropped images, hierarchy, spacing, progress bars and bottom safe
area. Check 320dp width, landscape, 200% font size and TalkBack. Confirm all content
scrolls into view and touch targets remain usable. Do not claim visual acceptance
until reviewing actual rendered screenshots.

## Xiaomi 17t physical checklist

- Install debug APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Launch; verify no crash and three distinct images with 99%, ~90%, estimate 76%.
- Confirm Demo mode · Not connected and Demo data; no live timestamps.
- Refresh demo: unchanged samples and visible reload confirmation.
- Open Settings from navigation and Advanced settings; return to Devices.
- Rotate, enlarge fonts, enable TalkBack, scroll all cards and check gesture insets.
- Repeat without Wi-Fi/mobile data; behavior should be unchanged.
- Record Android/HyperOS version, font scale, APK SHA-256, screenshots and any issues.

No physical-device testing, emulator launch or screenshot capture has been performed.
