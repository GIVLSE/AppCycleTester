# Sequmio Privacy Policy

**Effective date:** November 27, 2026

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

Local plans and run history are stored on-device. From v1.1, automatic upload of sanitized QA run metadata is optional and OFF by default. Only after explicit consent and Google sign-in does Sequmio send device model/manufacturer, Android API, tested app version/build, pseudonymous tester UID, scenario/suite/version, PASS/FAIL step statuses and timings to the application's developer via Firebase Cloud Firestore. No screen text, user-typed values, passwords or screenshots are included in automatic metadata reports. Optional screenshot evidence is separate. Without consent, local data is not automatically uploaded. The app has no advertising or behavioral analytics. Optional Google sign-in uses Firebase Authentication and stores the user's basic account profile in Firebase Cloud Firestore; Google/Firebase process identity information. Sign-in is not required for local Runner. Developers can export their received technical metadata as JSON/CSV from QA Dashboard.

## Accessibility permission
The user must explicitly consent to Sequmio's in-app disclosure and enable the Sequmio Runner service in Android Accessibility settings. The service is used to open apps, detect active windows, inspect visible UI node IDs/text/descriptions while a user-selected E2E scenario runs, and perform scripted taps, swipes, Back and predefined text input. V0.8 can optionally record UI accessibility events in the developer's selected app to create an editable scenario. Only selectors/approximate gestures and placeholders for typed text are stored locally; typed characters and password fields are excluded. The recorder does not record raw touch streams or screenshots/audio/video, and the service does not automatically approve system permission dialogs. Optional failure evidence requires separate consent. The user can disable the service at any time.

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

## Scenario security and storage
Published scenario definitions may contain predefined UI action parameters and selectors stored in Firestore; other testers can download them. Avoid placing secrets, passwords or personal data in published scenarios. Scenario execution runs locally, only inside the selected package, and does not upload the screen contents to a server.

## Local UI event recorder (V0.8)
Recording starts only after the developer explicitly selects Record UI actions in My App and ends with the floating Stop control. Captured action metadata is stored in the local My App draft and is not uploaded unless the developer subsequently publishes the scenario. The recorder cannot capture all gestures or custom-drawn UI, and typed contents are never copied into scenario fields.

## Account deletion (V0.9)
Use Settings → Profile → Delete my Sequmio account to request removal of your Firestore profile, published listings, self-confirmed memberships and Firebase authentication record. Cloud account deletion requires a valid sign-in and may require recent reauthentication. Local test reports and My App drafts are removed by clearing app storage or uninstalling Sequmio. To request deletion outside the app, visit https://givlse.github.io/AppCycleTester/delete-account.html.

## V1.1 distributed QA, optional cloud data and daily emails

The QA uploader uses Android WorkManager and Firebase. Automatic report sharing is OFF until a signed-in tester explicitly turns it on in Settings and confirms the disclosure. WorkManager may retry when connectivity is restored. The developer sees only technical run metadata and a tester UID, not the tester's account email. Automatic step reports never upload screen contents or typed text. Optional evidence sharing is separate.

Developer dashboards load test runs and build/version comparison statistics from Firestore. A separately deployed scheduled Cloud Function generates daily UTC summaries. Developers can opt into daily emails; email delivery requires a configured provider, and uses the developer's account email and aggregate QA statistics. Reports remain in Firebase until deleted or the operator adopts a retention process; clearing local Android data does not delete existing cloud reports. Cloud account deletion attempts to remove the user's submitted reports, owned reports, scenario snapshots, app listings, daily digests, and Firebase login. Secure deletion requests can also be initiated via the account deletion page.

Do not put credentials or personal information in published test scenarios. Assertions should check predictable non-sensitive UI test values.

## V1.3.5 QA progress, reminders and screenshot evidence

The local QA progress tracker stores per-account daily testing dates (confirmed PASS/FAIL) and reminder preferences. It does not verify Google Play participation or automatically start tests. Optional approximate local-time reminders notify the user about installed joined apps without a recorded test today.

Screenshot Evidence is OFF by default and requires separate explicit consent. On Android 14+ it captures only the tested app's active window at a confirmed failed assertion during a user-initiated run. Protected windows and detected editable/password nodes are skipped; other private pixels may still appear. No full-display capture or automatic screenshot upload. Up to 20 private local images per account, maximum 200 KB each, seven-day retention on subsequent evidence access. The user must review and explicitly confirm each individual upload to the tested report's developer; do not share private data. Firebase stores the image, report/app IDs, step, timestamp, owner and tester IDs. The reporter and report owner may access and delete shared images. Local and cloud removal are independent; shared copies remain until deletion/account removal/administrative cleanup. Account deletion includes the account's uploaded or received evidence. Existing reports remain metadata-only.
