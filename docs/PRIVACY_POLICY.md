# Sequmio Privacy Policy

**Effective date:** November 23, 2026

**App:** Sequmio (\`com.sequmio.givlse\`)

**Developer:** Vladislav Girskii (GIVLSE)

**Public web version:** https://givlse.github.io/AppCycleTester/privacy.html (requires GitHub Pages to be enabled)

Sequmio allows users to automate timed switching between selected Android applications for local QA testing.

## Data used locally
- Launchable installed applications: display names, package identifiers and launcher activity names for the app picker.
- Test plans: selection and order of apps, timers, repeat mode and total-duration limit.
- Active window package identifiers and timestamps, observed through Android Accessibility Service to verify foreground app changes.
- Execution log records with timestamps, and user consent acknowledgement.

These records are stored locally in Android SharedPreferences. They are used only for running the selected test plans and displaying results. The current version does not send them to developer servers or third parties, and has no analytics, ads, or user accounts.

## Accessibility permission
The user must explicitly consent to Sequmio's in-app disclosure and enable the Sequmio Runner service in Android Accessibility settings. The service is used to open apps and detect active app windows. It does not read screen text, record taps, capture screenshots, audio or video in the current version. The user can disable the service at any time.

## Data retention and deletion
Plans remain until modified or local app data is deleted. Run history is a rolling recent-event log. Clear Sequmio's app storage or uninstall it to remove local data.

## Children
Sequmio is intended for developers and testers, not children.

## Changes
If new features such as E2E recording, screenshots or sharing are introduced, the policy and in-app disclosure will be updated first.

## Privacy contact
Vladislav Girskii (GIVLSE): https://github.com/GIVLSE/AppCycleTester/issues
