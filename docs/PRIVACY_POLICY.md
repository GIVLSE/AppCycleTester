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
- Optional Google account: Firebase user ID, display name, email, profile photo URL and last login (only after account sign-in); My App draft fields remain local.
- Execution log records, structured per-session JSON/CSV reports with app package IDs, timing, retry counts, PASS/FAIL status, and user consent acknowledgement.

These records are stored locally in Android SharedPreferences. They are used only for running the selected test plans and displaying results. Sequmio does not automatically send local testing data to servers or third parties, and has no analytics or ads. Optional Google sign-in uses Firebase Authentication and stores the user's basic account profile in Firebase Cloud Firestore; Google/Firebase process identity information. Sign-in is not required for local Runner. Export to other apps happens only when the user taps Share JSON/CSV for a specific report.

## Accessibility permission
The user must explicitly consent to Sequmio's in-app disclosure and enable the Sequmio Runner service in Android Accessibility settings. The service is used to open apps and detect active app windows. It does not read screen text, record taps, capture screenshots, audio or video in the current version. The user can disable the service at any time.

## Foreground service and display
Tests use a user-started foreground service (Android specialUse type), optional notification access to show a Stop action, and a temporary partial CPU wake lock. These do not bypass a locked screen or keep third-party apps visible.

## Data retention and deletion
Plans remain until modified or local app data is deleted. Run history is a rolling recent-event log, plus up to 40 structured session reports. Clear Sequmio's app storage or uninstall it to remove local data.

## Children
Sequmio is intended for developers and testers, not children.

## Changes
If new features such as E2E recording, screenshots or automatic sharing are introduced, the policy and in-app disclosure will be updated first.

## Privacy contact
Vladislav Girskii (GIVLSE): https://github.com/GIVLSE/AppCycleTester/issues

## Optional account management
Sign out in Settings to clear the session on the device. Cloud profile removal may be requested through the project support tracker. Public listings, joining tests and sharing scenarios are not implemented in V0.5. My App draft data is local only.

## Community app listings and memberships (V0.6)

If enabled by the owner, Firebase Firestore stores developer-submitted app names, package identifiers, public testing links, scenario JSON, owner user IDs and display names. Users can publish or unpublish from My App. Signed-in testers may self-confirm joining third-party tests. Sequmio stores a tester UID, app package name, join time and self-confirmed participation in Firestore; it does not independently verify Google Group membership, Play opt-in or actual testing. Local test execution logs remain on the device unless intentionally shared.
