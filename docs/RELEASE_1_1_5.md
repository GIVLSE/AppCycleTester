# Sequmio v1.1.5 — visual redesign release

**Package:** `com.sequmio.givlse`  
**Version:** 1.1.5 · **versionCode:** 17  
**Source:** `GIVLSE/AppCycleTester`, main

## Scope

Approved four-screen dark-neon mockup is implemented natively in Jetpack Compose. No Android permissions, user data, Firebase database model, E2E execution engine or Google Play package identifier changes are included.

### Navigation and theme
Outlined vector Material icons replace font symbols. Cards use subtle cyan/violet border, deep navy background and gradient buttons. Selected bottom tab receives a distinct elevated pill.

### Runner
Retain Sequmio logo/brand, Apps–Duration–Cycles metrics, and Runner Status genuine last run. Test Plan now shows **Start Plan at the top** and rows for selected installed apps with the OS launcher icon, label, duration, origin and status. Hide apps/Show apps collapses these rows. While running, Pause/Resume and Stop occupy the same primary action area.

### App List
Search by name/package/description. Community mode lists real published Firestore listings and authentic Join status, keeping original three-link Join behavior. On this device is clearly separate and lists actual installed launchable apps with Add to Runner; it never implies Google opt-in is verified.

### My App
Grouped App Details, Closed-test Links, QA Overview and Test Suites, with preserved edit/save/publish, record, local scenario preview and dashboard actions.

### History
Tonal PASS/FAIL/STOPPED cards, JSON/CSV shares, expand-on-demand step details and event timeline.

## Release validation
Code must pass `Sequmio Android` workflow (Gradle unit tests, debug APK and unsigned release AAB), then sign the AAB with the existing private upload key and compare certificate to 1.1.0. No real-device visual QA or Firebase deployment is asserted. Use QA_CHECKLIST.md UI115 tests.
