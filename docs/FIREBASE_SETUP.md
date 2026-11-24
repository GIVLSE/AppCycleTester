# Sequmio V0.5 — enabling Google sign-in and user profiles

**Current release builds without Firebase configuration**; its Profile screen explicitly says Google sign-in is unavailable. Do not publish a mock account or claim that community features are available.

## 1. Create the Firebase project

1. Open https://console.firebase.google.com/ and create a Firebase project without Analytics (not required by Sequmio).
2. Register Android app package \`com.sequmio.givlse\`.
3. Enable **Authentication → Sign-in method → Google**.
4. Create a **Cloud Firestore** database and deploy the repository \`firestore.rules\`. Rules allow each authenticated user to read/write only \`users/{uid}\` and deny all other documents for now.
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
