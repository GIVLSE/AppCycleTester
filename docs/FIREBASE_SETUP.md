# Sequmio V1.0 — configure sign-in and Firestore community backend

**Current release builds without Firebase configuration**; its Profile screen explicitly says Google sign-in is unavailable. Do not publish a mock account or claim that community features are available.

## 1. Create the Firebase project

1. Open https://console.firebase.google.com/ and create a Firebase project without Analytics (not required by Sequmio).
2. Register Android app package \`com.sequmio.givlse\`.
3. Enable **Authentication → Sign-in method → Google**.
4. Create a **Cloud Firestore** database and deploy the latest repository firestore.rules. Rules support owner-scoped users, published apps, private memberships and owner account deletion. Validate using two distinct users and the Firebase Emulator.
5. In the Google Cloud Console configure OAuth consent (external), a **Web application OAuth client** and Android OAuth clients for Sequmio. Google sign-in uses the **Web OAuth client ID**, not the Android client ID.
6. Register the **Play App Signing certificate SHA-1** from Play Console → Setup → App integrity. If testing a debug APK or sideloaded build, register each corresponding signing certificate SHA-1 too. Ensure branding, privacy URL and Terms URL are accurate.

## 2. Supply non-secret Firebase identifiers for CI

Open GitHub → GIVLSE/AppCycleTester → Settings → Secrets and variables → Actions → **Variables**. Set:

| GitHub Actions variable | Source |
|---|---|
| \`SEQ_GOOGLE_WEB_CLIENT_ID\` | OAuth 2.0 **Web** client ID |
| \`SEQ_FIREBASE_API_KEY\` | Android Firebase API key |
| \`SEQ_FIREBASE_APP_ID\` | Firebase Android App ID (e.g., \`1:...:android:...\`) |
| \`SEQ_FIREBASE_PROJECT_ID\` | Firebase Project ID |

These identifiers are not signing secrets. Do not put private upload keystores or passwords in code.

Both workflows (CI and signed AAB) pass these variables to Gradle. For local Android Studio builds, use Gradle properties \`sequmioGoogleWebClientId\`, \`sequmioFirebaseApiKey\`, \`sequmioFirebaseAppId\`, \`sequmioFirebaseProjectId\` in the developer's private \`~/.gradle/gradle.properties\`.

No \`google-services.json\` file is required for this integration: a named \`FirebaseApp\` is initialized explicitly from the four configured build fields. Do not commit \`google-services.json\`.

## 3. Verify real auth

- Install a properly signed build, open Settings → Profile → Continue with Google.
- The Google Credential Manager UI must let you choose an account. Firebase must authenticate it; the screen must show its actual name and email.
- Check Firestore \`users/{uid}\` contains \`displayName\`, \`email\`, \`photoUrl\`, \`lastLogin\`.
- Confirm security rules deny cross-user reads and writes.
- Sign out, sign in as a different tester, and verify the profile changes.
- Without Firebase configuration or internet, local Runner and History must still work.
- Update Play Console Data safety: account email, display name and user ID are used for account management and transmitted to Firebase/Google when enabled. Provide a cloud-account deletion request channel and complete any required account deletion functionality before public release.

## 4. Scope exclusions

App List intentionally has no remote entries and no fake Join actions in V0.5. My App saves a local draft only. Public app publishing, joining closed tests, and runnable E2E scenarios are separate V0.6–V0.8 features.

## V0.6: deploy catalog and memberships rules

Deploy the updated `firestore.rules` before exposing App List. It permits read-only queries of *published* apps and authenticated owner writes; membership entries are readable only by their own tester. Firestore reads are not filtered automatically, so the client queries `whereEqualTo("status", "published")` (see Firebase documentation). Test rules in Firebase Emulator with two distinct signed-in identities before enabling production data.

App document IDs are Android package names, memberships are `{uid}_{packageName}`. My App requires a valid scenario JSON and three HTTPS links. The "Join" status is **self-confirmed**: Google Groups and Google Play do not give Sequmio membership attestations. The app checks device installation, not actual opt-in.

When Firebase is not configured, catalog writes and login are disabled. No production cloud service was created by the app code alone.

## V0.9 online validation
Verify deployed Firestore rules allow an owner to query their own listings (including drafts), delete their account and memberships while preventing edits to another user's listings. The Android app still needs actual Firebase identifiers in GitHub Actions Variables. Cloud integration tests with two Google users and separate devices have not been executed.

## V1.0 reality check

Until all four GitHub Actions variables are configured and the server rules deployed, the app deliberately disables Google sign-in and real cloud catalog features. The developer must register the Play app-signing SHA-1 and validate account deletion, including the off-app request pathway, before declaring production readiness.

## V1.1 required cloud setup and deployment

The application can build locally without any cloud configuration. To use real sign-in, public listings and distributed QA, first configure all four GitHub Actions variables above, enable Google Authentication, register Play signing SHA-1 fingerprints, and deploy firestore.rules.

1. Install the Firebase CLI and select your own project; use `firebase deploy --only firestore:rules`. **Do not deploy before testing rules** with at least two distinct Firebase users and the Firebase Emulator Suite.
2. The Android app stores each consenting run under `testRuns/{runId}_{targetIndex}_{caseIndex}`. A record contains only technical metadata and StepResult statuses. Validate owner access, non-owner rejection and joined-member upload.
3. To schedule yesterday's UTC digest, enable billing as required by Cloud Functions v2/Cloud Scheduler. Install dependencies in `functions/` with `npm install`, and deploy `firebase deploy --only functions:dailyQaSummary`. The Cloud Function runs at 03:15 UTC. The app reads owner-specific `dailyReports` in My App → QA Dashboard.
4. Optional email summaries use Resend. Configure a verified sender in the private Firebase Functions environment as `SEQ_REPORT_SENDER` and set secret `SEQ_REPORT_EMAIL_API_KEY` with `firebase functions:secrets:set SEQ_REPORT_EMAIL_API_KEY`. This secret is bound to the scheduled function and is required for deployment in the current implementation. If email is not wanted, leave the sender unset and set an inert placeholder secret; the in-app summary still runs. Developers separately enable "Daily email QA digest" in Settings; default OFF.
5. Confirm scheduled runs, error logs, Firestore rules and delivery with real Android devices before inviting external users. Firebase resources **have not been provisioned by this code change**.

Watch costs: Cloud Scheduler, Firebase Function invocation, reads/writes, and optional mail provider charges may apply. Do not commit .env files, keystores, private service accounts or provider API keys.

Account deletion now also attempts to remove testRuns, dailyReports and published scenario version snapshots; the operator must validate this using two accounts. Full cloud deletion is separate from clearing local phone storage.
