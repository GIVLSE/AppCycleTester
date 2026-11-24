# Sequmio v1.0.0 — feature scope and acceptance

**Package:** com.sequmio.givlse  
**Version:** 1.0.0; versionCode 15  
**Status:** Code milestone. No completed real-device, Firebase production or Google Play review acceptance.

## Features

- Runner: local plans, timed foreground switching, repetitions, recovery, notification Stop, screen lock safeguards, JSON/CSV reports, local QA-day tracking.
- App List: real Firestore catalog, user self-confirmed Google Group + Play opt-in + installation, on-device install verification, community scenario sync to Runner.
- My App: owner listing with three testing links, mandatory validated scenario, publish/update/unpublish, explicit Accessibility event Recorder with Pause/Stop, visual and advanced JSON scenario editor.
- E2E: deterministic UI steps CLICK, WAIT, SWIPE, INPUT_TEXT, ASSERT_EXISTS and SYSTEM_BACK; bounded selector retries; no password entry; user-controlled test start.
- Settings: optional Google Credential Manager/Firebase sign-in, Privacy, Terms, Support, in-app deletion; no fake cloud users.
- Account deletion website: docs/delete-account.html (outside-app support workflow requires operational handling).

## External dependencies before acceptance

1. Create/configure Firebase Auth and Cloud Firestore; register OAuth and correct Play signing certificate SHA-1 fingerprints.
2. Deploy firestore.rules and verify cross-user access in the Firebase Emulator.
3. Configure GitHub Actions variables described in docs/FIREBASE_SETUP.md, rebuild a signed AAB.
4. Update Play Console Accessibility V3, Foreground Service specialUse and Data Safety, including the external account deletion URL.
5. Run all QA_CHECKLIST.md cases on actual Android 11/14/16 devices with multiple OEM skins.

Google Group membership and Play closed-test opt-in cannot be verified by Sequmio. The app reports self-confirmed Join only. Local QA days are not proof of Play testing compliance. Canvas/secure or custom UI may not support event recording, and screen lock cannot be bypassed.
