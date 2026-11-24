# Sequmio

Android QA runner and community test platform. GitHub repository remains **GIVLSE/AppCycleTester**.

**V1 Android QA utility** to run a user-selected sequence of installed apps, keeping each in the foreground for a configured time, then opening the next app.

Repository: https://github.com/GIVLSE/AppCycleTester

## V1 features

- Lists launchable installed apps (no QUERY_ALL_PACKAGES permission).
- Select apps, reorder, set a per-app timer (1 second–120 minutes; default 5 minutes).
- Repeat once, N times (1–999), or indefinitely.
- Optional overall duration cap (0 means unlimited).
- Start, pause, resume, stop; Stop action in the foreground-service notification (visible with notification permission).
- V1.1 interactive on-demand Accessibility onboarding with direct App info and Accessibility settings links and connected-status feedback; no programmatic permission grant.
- Persistent plan, local event log and per-run structured JSON/CSV session reports (export only on explicit user share).
- Uses explicitly user-enabled Accessibility Service to request app launches and check foreground window events.
- Timeout if an app's window is not confirmed within 8 seconds; retry a lost window up to twice. Other-app windows trigger recovery after 5 seconds; permissioncontroller dialogs receive up to 45 seconds for manual user action, never silent approval.
- Kotlin + Jetpack Compose, minSdk 26, targetSdk 36.

**Application ID:** `com.sequmio.givlse` · **Version:** `1.1.5` (`versionCode` 17).

**Important:** This is a new Android package compared to the prototype `io.github.givlse.appcycletester`; installing Sequmio will not update the previous package in place.

## Sequmio UI v1.1.0

The approved dark UI is implemented in Jetpack Compose with a Sequmio vector mark and launcher icon. Tabs: Runner, App List, My App, and History. The former Scenarios placeholder is moved under My App. A top-right gear opens Settings (Profile, permissions, About, Privacy, Terms, Support). The Accessibility onboarding is a first-class screen with explicit disclosure and consent. All test-plan and runner operations remain connected to the V1 engine. Source: `SequmioApp.kt`, `SequmioTheme.kt`, `SequmioDialogs.kt`, and `SequmioAccessSetup.kt`.

The CI produces both `Sequmio-debug` and `Sequmio-release-unsigned-AAB`. **The unsigned AAB is not upload-ready for Google Play.** Production/closed-track Play upload requires a signed AAB created with the app owner's permanent upload key via the manual Play workflow; do not use a disposable key for your first release.

## Build

Import into Android Studio (JDK 17, Android SDK 36). The GitHub Actions workflow runs Gradle 8.13 and executes:

    gradle :app:testDebugUnitTest :app:assembleDebug

The debug APK is published under **Actions → Android V1 → Artifacts → AppCycleTester-debug** when CI succeeds.

There is no gradle-wrapper.jar in this initial repository: Android Studio must use a locally installed Gradle or a configured distribution; CI installs the pinned Gradle version automatically.

## How to use

1. Install the debug APK on a test device.
2. Select the apps and press **Start**. If the accessibility service is unavailable, V1.1 opens an in-app setup guide instead of disabling Start.
   - For sideloaded installs on Android 13+, Android/HyperOS may restrict Accessibility; in the guide open **App info** and explicitly allow restricted settings if offered.
   - Open **Accessibility → Downloaded apps → Sequmio Runner** and enable the service. This permission is granted only by the device owner in Android Settings, never silently by the app.
   - Return to Sequmio: the guide detects when the service is connected and offers **Start test**.
3. Select multiple apps and set individual durations and ordering.
4. Set repeat mode and optional total duration cap.
5. Keep device unlocked with display on. On a dedicated phone, Developer options → **Stay awake while charging** is recommended.
6. Start the run. Check actual app windows, not just launch log events.
7. To pause/stop, reopen Sequmio and use the controls.

## Android limitations

- No root, forced package stop, background process kill, or screen-lock bypass. A bounded renewable PARTIAL wake lock keeps CPU available during user-started tests; it does not illuminate the display.
- Android/OEM policies may prevent activity launches from background. Device verification is essential.
- On Android 13+ sideloaded builds may need an additional manual **Allow restricted settings** confirmation in Settings → App info. Some ROMs hide/relocate this action; the app cannot force it.
- Android 11–16 behavior varies by OEM; UI guidance cannot guarantee access is granted.
- Android FLAG_KEEP_SCREEN_ON only applies to Sequmio's own Activity; it cannot keep OTHER apps on-screen. The runner stops with FAIL if screen-off is observed. Configure timeout/Stay awake while charging for reliable long tests.
- With V0.7 consent, Accessibility can inspect visible UI node text/IDs and perform deterministic actions in the selected foreground package. No screen recording, raw touch collection or automatic system permission grants.
- The Android process or Accessibility service may be stopped by the OS, ending the run. A pending report is marked INTERRUPTED on next launch; the app does not silently resume.
- V1 is a prototype, not a validated commercial release; the debug APK requires CI verification and Android device testing.
- A plan currently permits one entry per application package.

## Project

- app/src/main/java/com/sequmio/givlse/MainActivity.kt — Compose UI and app picker
- app/src/main/java/com/sequmio/givlse/runner/CycleAccessibilityService.kt — cycle runner
- app/src/main/java/com/sequmio/givlse/runner/RunPolicy.kt — deterministic navigation
- app/src/main/java/com/sequmio/givlse/data — package catalog and persistence
- QA_CHECKLIST.md — V1 real-device acceptance checks

## Later versions

V0.4: foreground-service notification, finite recovery, second-level timers, screen-off safeguards and local JSON/CSV reports.
V0.5: four-tab navigation, My App local draft, empty App List (no mock listings), Settings and optional Google/Firebase account integration. Public catalog, Join and scenario playback are not yet implemented.
V2: E2E recorder/player with accessibility node selectors and tap/swipe assertions.

## Release verification

Sequmio 0.5.0 preserves the native dark-mode UI and V0.4 runner. CI compiles the APK and release bundle and runs the cycle navigation unit tests. UI and long-running accessibility switching still require real-device QA before wider production rollout. Signed Play bundles use the owner's permanent upload key. Never commit this private key or its password.

## V0.4 Play Console requirement

Foreground service uses type `specialUse` for an explicitly user-started QA cycle while other apps are in front. It shows a Stop action and releases CPU wake lock at the end. Android 14+ requires a separate **Foreground service permissions declaration** in Play Console, including the use case. Users can deny POST_NOTIFICATIONS; the FGS can still run but the Stop notification may not appear in the notification shade (Android task manager may show it). The app must not claim it can keep third-party apps' screens on.

## V0.5 Google account and community foundation

Local Runner and History continue to work without sign-in. The new App List is intentionally empty until V0.6 introduces real published entries and a Join flow. My App saves a device-local draft (name, package, description, Google Group URL, tester opt-in URL, Google Play URL); publishing remains disabled until a public catalog and at least one runnable scenario exist. This prevents false claims of live listings or working test recordings.

Google sign-in is implemented with Android Credential Manager, Firebase Authentication and Cloud Firestore. **No Firebase project or OAuth client is checked into this repo.** Until the owner configures Firebase, the sign-in screen reports "not configured" instead of creating a mock user. When configured and used, Firebase Authentication validates Google credentials and an owner-scoped Firestore `users/{uid}` document stores display name, email, avatar URL and last login. Local test data is not uploaded. Security rules are supplied in `firestore.rules` but need deployment by the Firebase project owner.

Follow `docs/FIREBASE_SETUP.md` to configure the project, OAuth SHA-1 fingerprints, GitHub Actions variables, security rules and Play Console Data safety before enabling sign-in. The cloud configuration is injected into each Android build via `SEQ_*` environment variables; no private signing key or OAuth secret is embedded in repository code.

## V0.6: Cloud app catalog and self-attested join

- Public App List reads *real* published Firestore `apps` entries; no sample listings or fake join successes.
- My App stores its draft locally and publishes only with a signed-in owner, valid package name, 3 official HTTPS URLs and a valid deterministic scenario JSON containing a real action/assertion.
- Each user can edit or unpublish their own app. Other users cannot overwrite its entry (see `firestore.rules`).
- Join shows Group → Become a Tester → Install. Membership in Google Group or Play testing cannot be externally verified by Sequmio; the user explicitly self-confirms the first two steps. Install is checked against launchable apps installed on device.
- On Join, the selected app and its owner's scenario JSON are added to the local Runner plan. Executing UI actions from those JSON scenarios requires V0.7; current runner still performs timed foreground verification.
- This feature is *not operational* without Firebase/OAuth settings plus deployed Firestore security rules. Never treat CI PASS as cloud integration proof.

## V0.7: deterministic E2E scenario playback

The V1 JSON schema supports `WAIT`, `CLICK`, `SWIPE`, `INPUT_TEXT`, `SYSTEM_BACK`, `ASSERT_EXISTS` and `optional` steps. The player uses Accessibility node selectors before normalized coordinates, runs only against the expected package, rejects password-field text input and requires the user to start the run. It fails a scenario on a missing required UI element. The original timed runner still works without scenarios.

Because Android permission scope changed, explicit Accessibility consent was versioned to V2 and now explains UI text inspection and simulated gestures. Review the latest Play Console Accessibility declaration and record a new demo video before submitting. The on-device code has CI coverage only; device verification and OEM compatibility remain open. Recorder remains out of scope.

## V0.8: optional in-app UI event recorder

My App can start a local recorder for the exact installed package owned by the developer. A visible Accessibility overlay shows REC, Pause and Stop. Only UI click events, scroll events and editable-field events are converted into JSON steps; no raw touches or screenshots are recorded, no password fields are recorded, and actual typed text is **never copied**. Text fields become `REPLACE_WITH_TEST_TEXT` placeholders that the author must edit before publication. Editor validates actions before enabling publishing. The recorder saves locally only and returns to My App at Stop.

Limitations: Accessibility events are not a universal tap/swipe recorder. Secure windows, games, canvas-based UI and some Compose/Flutter views may not expose useful events or selectors. In those cases, edit the JSON manually and choose normalized coordinates. This is not a substitute for real-device QA.

## V0.9: joined-app synchronization, local QA progress and account deletion

- When App List opens or refreshes, signed-in joined apps are fetched from the server and their current scenario definitions are synchronized into the Runner plan **without changing manual order**. Manually selected local targets are not silently replaced. Offline tests continue using the last locally saved definition.
- PASS reports mark the local calendar date for any app with a confirmed foreground window, no failed launches and enough verified foreground time. App List shows the number of local QA days. This is **not verification by Google Play** and does not count opt-in enrollment.
- Settings → Profile provides a confirm-then-delete cloud account action. It removes the user's own app listings, self-confirmed memberships and profile document before deleting Firebase Auth identity. Firebase may require a recent Google login. Local files remain until clearing app storage or uninstall.
- For external account deletion requests, users can visit `https://givlse.github.io/AppCycleTester/delete-account.html`. This is an initial contact path and needs operational support handling before production.

## V1.0 visual E2E editor and device QA

My App includes a visual scenario editor with step creation, reordering, field editing, optional flags, selectors and normalized coordinates, plus Advanced JSON. The Recorder's scenarios can be reviewed without hand-editing JSON. Developers can add their own validated scenario to the local Runner without publishing. The Player retries delayed UI elements before failing.

**Important:** This is a 1.0 code milestone for user acceptance, not proof of production readiness. Offline Runner works without an account. Google Sign-In/App List require the owner's actual Firebase configuration, OAuth fingerprint setup, deployed Firestore rules, security validation and Google Play review. Device QA has not yet been performed.

## V1.1: distributed QA and quality-focused test suites

- Mandatory meaningful assertions for each scenario; ASSERT_EXISTS, ASSERT_NOT_EXISTS, ASSERT_ENABLED, ASSERT_TEXT, ASSERT_SCREEN. The runner records per-step PASS/FAIL/SKIPPED, duration and scenario version without storing on-screen text or typed values.
- My App → Test Suites: up to 12 test cases per app, category SMOKE / FEATURE / REGRESSION, guided creation, locally verified PASS prerequisite for promotion into REGRESSION. A multi-case bundle runs all cases sequentially in the selected app.
- Versioned scenario bundles are stored as immutable Firestore scenarioVersions snapshots when published.
- Settings → QA report sharing is OFF by default, requiring explicit opt-in. With Google sign-in, after each run WorkManager attempts to upload sanitized app/device/version metadata and step outcomes; offline retries are bounded and ID-based to avoid duplicates.
- My App → QA Dashboard loads genuine owner-scoped Firestore runs, step-level details, build comparison, suite summaries and previous UTC-day digests. There are no fabricated analytics figures.
- Cloud Functions 2nd gen schedule aggregates yesterday's UTC runs at 03:15 UTC and writes owner-specific dailyReports. Optional email is sent only when configured with a valid provider key/sender and the developer opts in to the daily digest.

**Critical external dependencies:** No Firebase project, deployed Firestore rules, deployed scheduled function or email service is provisioned by this repository commit. Local Runner and suite editing work without cloud; uploads, online Dashboard and daily digests work only after those services are configured. Device acceptance, Play Accessibility/FGS/Data Safety declarations and cloud security tests remain pending.

See docs/RELEASE_1_1.md and docs/FIREBASE_SETUP.md.

## v1.1.5 — dark neon UI redesign

Following the approved four-screen design mockup, v1.1.5 updates **presentation only**; it preserves the v1.1 QA runner, Firebase account/catalog logic, test cases, Recorder and reporting.

- Consistent Material outlined icons replace most Unicode placeholders, including bottom navigation, Settings and Test Plan controls. Teal/violet card outlines and gradient primary buttons refine the dark theme.
- Runner's **Start Plan** appears immediately below the Test Plan title. Real selected apps appear as individual rows with Android launcher icons, duration/source and Included/Running state. A local **Hide apps / Show apps** toggle collapses this list. Runner Status retains genuine accessibility state and latest test result.
- App List adds search plus separate Community and On this device views. Community contains **real, backend-returned listings only**; installed apps are never shown as verified community testers. On-device apps can be added to Runner directly.
- My App groups existing fields into App Details, Closed-test Links, QA Overview and Test Suites cards. Existing draft, publish, scenario editor, record and dashboard actions remain available.
- History displays the existing local reports as distinct PASS / FAIL / STOPPED cards, supports JSON/CSV sharing and expandable actual test steps, and shows an event timeline with a Show all switch.

This UI release does not make unavailable Firebase/Google Sign-In or scheduled QA emails operational, does not change app permissions, and is **not real-device visual QA acceptance**. Android API 26–36 and different display scaling must be checked on-device.

See `docs/UI_IMPLEMENTATION_1_1_5.md` and `docs/RELEASE_1_1_5.md`.
