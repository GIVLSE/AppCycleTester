# Sequmio — Google Play closed testing

Package ID: `com.sequmio.givlse`. Keep it unchanged after Play Console app creation.

## Google Play Console (manual; owner's developer account)

1. Visit https://play.google.com/console and create a **new app** named **Sequmio** in the existing developer account.
2. Complete app details, Data safety, privacy policy, target audience, store listing and **Accessibility API declaration**, including a video of in-app disclosure, consent, service activation and one cycle.
3. Enroll in **Play App Signing** with a Google-generated app signing key. Use a different, permanent upload key locally.
4. Under Testing → Closed testing create a track, add tester email addresses or a Google Group, and create a release with the signed AAB.
5. After review approval, send the opt-in link to testers. Testers install via Google Play.

Google Play review is required even for testing tracks. Closed testing does not eliminate Accessibility API policy obligations.

## Generate permanent upload key (on your PC)

In a JDK terminal:

```powershell
keytool -genkeypair -v -keystore appcycletester-upload.jks -storetype JKS -alias appcycletester-upload -keyalg RSA -keysize 3072 -validity 10000
```

**Keep the .jks and passwords offline. Never send them to chat, and never commit them.** Keep a secure backup so future releases use the same upload certificate.

Encode the local file (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("appcycletester-upload.jks")) | Set-Clipboard
```

Open GitHub → GIVLSE/AppCycleTester → Settings → Secrets and variables → Actions. Create these repository secrets:

| Secret | Value |
|---|---|
| ACT_UPLOAD_KEYSTORE_B64 | Base64 contents of upload .jks |
| ACT_UPLOAD_STORE_PASSWORD | Keystore password |
| ACT_UPLOAD_KEY_ALIAS | appcycletester-upload |
| ACT_UPLOAD_KEY_PASSWORD | Private key password |

Only the repo owner should configure these. GitHub Actions will reconstruct the keystore temporarily, sign the AAB and leave the keystore out of artifacts.

## Build for Play

Open GitHub → Actions → **Play closed-test AAB** → Run workflow. This separate manual workflow will fail before compiling if any of the signing secrets are missing. A successful run produces artifact `Sequmio-play-upload-aab` containing `Sequmio-play-upload.aab`.

The regular Android V1 CI continues building a debug APK for local QA. Debug APKs are not Play release bundles.

## Accessibility policy

V1 is **not** a disability-support accessibility tool; the project's `isAccessibilityTool=false` flag is intentional.

The Play reviewer must see the prominent disclosure/consent dialog before any use of Accessibility. The service observes foreground window package IDs and time; run history and the plan are kept on-device. There is no screen-text collection, screenshots, tap recording, analytics, advertising or server upload in V1. The consent screen must be updated when V2 functionality adds more capabilities.

Provide an in-console Accessibility declaration and video. The service performs deterministic, user-configured app switching (not autonomous decisions).

## After uploading

Google Play may sign generated APKs with a different key than locally installed debug APKs. Installing the Play test version over a sideloaded debug version may require uninstalling the debug APK (which deletes local settings). Updates delivered via Play should then work normally.

## Current public policy draft

https://github.com/GIVLSE/AppCycleTester/blob/main/docs/PRIVACY_POLICY.md

## Package migration

The final selected Google Play package ID is `com.sequmio.givlse` (not the prototype's old `io.github.givlse.appcycletester`). Use **com.sequmio.givlse** in the Play Console app creation form before uploading any AAB. Android treats it as a separate app; uninstall the old debug package if unnecessary. Repository stays `GIVLSE/AppCycleTester`.

## Important

This repository currently has no connected Google Play Console integration. The signed AAB is **not automatically uploaded to Play**; the owner must create the app and submit the bundle.
