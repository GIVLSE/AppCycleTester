# App Cycle Tester

**V1 Android QA utility** to run a user-selected sequence of installed apps, keeping each in the foreground for a configured time, then opening the next app.

Repository: https://github.com/GIVLSE/AppCycleTester

## V1 features

- Lists launchable installed apps (no QUERY_ALL_PACKAGES permission).
- Select apps, reorder, set a per-app timer (1–120 minutes; default 5).
- Repeat once, N times (1–999), or indefinitely.
- Optional overall duration cap (0 means unlimited).
- Start, pause, resume, stop.
- Persistent plan and local event log.
- Uses explicitly user-enabled Accessibility Service to request app launches and check foreground window events.
- Timeout if an app's window is not confirmed within 8 seconds. Reports FAIL after losing the foreground for over 5 seconds.
- Kotlin + Jetpack Compose, minSdk 26, targetSdk 36.

## Build

Import into Android Studio (JDK 17, Android SDK 36). The GitHub Actions workflow runs Gradle 8.13 and executes:

    gradle :app:testDebugUnitTest :app:assembleDebug

The debug APK is published under **Actions → Android V1 → Artifacts → AppCycleTester-debug** when CI succeeds.

There is no gradle-wrapper.jar in this initial repository: Android Studio must use a locally installed Gradle or a configured distribution; CI installs the pinned Gradle version automatically.

## How to use

1. Install the debug APK on a test device.
2. In the app open Android Accessibility settings and enable **App Cycle Runner**.
3. Select multiple apps and set individual durations and ordering.
4. Set repeat mode and optional total duration cap.
5. Keep device unlocked with display on. On a dedicated phone, Developer options → **Stay awake while charging** is recommended.
6. Start the run. Check actual app windows, not just launch log events.
7. To pause/stop, reopen App Cycle Tester and use the controls.

## Android limitations

- No root, forced package stop, background process kill, or wake-lock bypass.
- Android/OEM policies may prevent activity launches from background. Device verification is essential.
- Screen lock/automatic display timeout is not solved by this version.
- Accessibility is used to detect window changes only. This version does not capture UI content or gestures.
- The Android process or accessibility service may be stopped by the OS, ending the run.
- V1 is a prototype, not a validated commercial release; the debug APK requires CI verification and Android device testing.
- A plan currently permits one entry per application package.

## Project

- app/src/main/java/io/github/givlse/appcycletester/MainActivity.kt — Compose UI and app picker
- app/src/main/java/io/github/givlse/appcycletester/runner/CycleAccessibilityService.kt — cycle runner
- app/src/main/java/io/github/givlse/appcycletester/runner/RunPolicy.kt — deterministic navigation
- app/src/main/java/io/github/givlse/appcycletester/data — package catalog and persistence
- QA_CHECKLIST.md — V1 real-device acceptance checks

## Later versions

V1.1: better foreground recovery, profiles, export reports and second-level timers.
V2: E2E recorder/player with accessibility node selectors and tap/swipe assertions.
