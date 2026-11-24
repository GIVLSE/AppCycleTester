# Sequmio v1.1.0 — QA framework and distributed reporting

**Android package:** `com.sequmio.givlse`  
**Version:** 1.1.0, versionCode 16  
**Status:** Code changes staged for Android CI. Cloud deployment/device acceptance still required.

## All five requested capabilities

1. **Assertions & StepResult:** ASSERT_EXISTS, ASSERT_NOT_EXISTS, ASSERT_TEXT (exact text), ASSERT_ENABLED, ASSERT_SCREEN; wait/retry with bounded timeout, per-step PASS/FAIL/SKIPPED and elapsed milliseconds. Validation disallows action-only scenarios. Logs omit actual UI text and typed content.
2. **Automatic upload (consent-gated):** Explicit OFF-by-default toggle and confirmation in Settings; WorkManager uploads to `testRuns` after a run, using deterministic document IDs. Only authenticated owners or self-confirmed joined testers can submit; uploader verifies the Firestore listing. The record includes scenario name/version/suite, app version/build, device manufacturer/model/Android API, step outcomes and timings. No screen recording.
3. **Developer QA Dashboard & daily summaries:** My App → QA Dashboard loads actual owner-scoped runs from Firestore, detailed step outcomes, app build comparison, suite breakdown, and daily reports. A Firebase Cloud Functions v2 scheduler builds previous UTC-day digests at 03:15 UTC. Optional emails require a configured Resend API key/sender and developer opt-in; emails are not sent unless backend deployed.
4. **Smoke/Regression/Feature suites:** Up to 12 cases per application, each with a version and a suite type, saved as a JSON bundle. Sequential execution through Runner. Publisher stores immutable `scenarioVersions` revisions. Regression promotion requires recorded local PASS on the scenario, not just a tap.
5. **Guided Scenario Builder:** QA coaching text shows how to add an action, specify expected result, validate locally, and promote to regression; visible ASSERT workflow in the visual editor.

## Not yet accepted / production prerequisites

- Firebase Auth/OAuth, Firestore project, security rules, Cloud Scheduler and email provider have NOT been configured or deployed from this repository commit.
- Android 11/14/16 user acceptance, multiple OEMs, actual network retry and Google Play review remain to be performed.
- Current dashboard loads a bounded recent set of cloud runs (up to 250), not an unbounded historical warehouse.
- Cloud daily summary is UTC-day based; not yet a per-user timezone scheduler.
- A recording may omit custom graphics or Canvas views. Tests are limited to supported Accessibility UI nodes/gestures.
- Google Groups / Play opt-in are self-confirmed; local QA pass is not Google certification.

## Primary files

- `ScenarioSpec.kt`, `ScenarioPlayer.kt`, `StepExecutionResult.kt`, `RunReportStore.kt`
- `CloudRunUploader.kt`, `QaDashboardRepository.kt`, `SequmioQaDashboard.kt`
- `SequmioSuiteManager.kt`, `SequmioVisualScenarioEditor.kt`
- `CommunityRepository.kt`, `firestore.rules`, `functions/index.js`
- `QA_CHECKLIST.md`, `docs/FIREBASE_SETUP.md`, `docs/PLAY_CLOSED_TESTING.md`
