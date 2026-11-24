# Sequmio v1.2.0 (21) — Firebase integration candidate

Firebase project `sequmio`, Android package `com.sequmio.givlse`. The owner has created the project and default Firestore database, configured Google Authentication and registered signing fingerprints, published the rules, and provided the four GitHub Actions repository variables. The owner supplied Cloud Shell screenshots showing 12/12 and 24/24 emulator security tests passed against firestore.rules at 8e4b2a4d4f5edfefa1a77b2b3475a8076f5e9ecb.

This release enables the existing Firebase Auth / Firestore implementation through the existing build-time variable wiring. No private credentials, OAuth client secret, signing key, or google-services.json are committed. No new Android permissions. Existing Runner, Recorder and History remain local. Report sharing stays opt-in. Support remains a placeholder; scheduled email and Cloud Functions are not deployed.

## Acceptance gate

1. Install the Play-distributed build; Settings > Continue with Google must open the real account chooser and authenticate.
2. Verify Firebase Authentication user and matching private users/{uid} Firestore profile. Sign out, relaunch and sign in again; verify persistence and isolation with a second account.
3. Check guest catalog and authenticated catalog behavior. Validate initial publication, scenario revision, join and first report upload using actual client flows. Emulator tests validated rules with fixtures, not complete Android client transactions; in particular, initial get of a missing app/report document must be checked against the current rules before wider use.
4. Verify account deletion and privacy disclosures before public release.
5. Keep App Check enforcement off until the SDK is integrated and verified. Keep Spark plan; scheduled functions and email remain deferred.

Migration boundary: existing repository classes remain in place. A provider-neutral interface layer and export/migration design are follow-up work, not completed in this release.
