# Sequmio — Google Play closed testing

Package ID: `com.sequmio.givlse`. Keep it unchanged after Play Console app creation.

## Google Play Console (manual; owner's developer account)

1. Visit https://play.google.com/console and create a **new app** named **Sequmio** in the existing developer account.
2. Complete app details, Data safety, privacy policy, target audience, store listing and **Accessibility API declaration**, including a video of in-app disclosure, consent, service activation and one cycle.
3. Enroll in **Play App Signing** with a Google-generated app signing key. Use a different, permanent upload key locally.
4. Under Testing → Closed testing create a track, add tester email addresses or a Google Group, and create a release with the signed AAB.
5. After review approval, send the opt-in link to testers. Testers install via Google Play.

Google Play review is required even for testing tracks. Closed testing does not eliminate Accessibility API policy obligations.

## Upload signing key

The first v0.3.0 release bundle was signed with a dedicated **Sequmio upload key**.
The owner must keep a secure, offline backup of that keystore and use the
**same key** for subsequent uploads. The private key is never committed to this
repository. If you already registered a different upload key with Play Console,
sign future bundles with the key registered there instead.

### If you already have the upload-key backup

Use the provided `sequmio-upload.jks` and the corresponding password from its private backup.
Do not generate a replacement key for updates.

### Generating a new key locally (only if this app has no registered upload key)

In a JDK terminal:

```powershell
keytool -genkeypair -v -keystore sequmio-upload.jks -storetype PKCS12 -alias sequmio-upload -keyalg RSA -keysize 3072 -validity 10000
```

**Keep the .jks and passwords offline. Never send them to chat, and never commit them.** Keep a secure backup so future releases use the same upload certificate.

Encode the local file (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sequmio-upload.jks")) | Set-Clipboard
```

Open GitHub → GIVLSE/AppCycleTester → Settings → Secrets and variables → Actions. Create these repository secrets:

| Secret | Value |
|---|---|
| ACT_UPLOAD_KEYSTORE_B64 | Base64 contents of upload .jks |
| ACT_UPLOAD_STORE_PASSWORD | Keystore password |
| ACT_UPLOAD_KEY_ALIAS | sequmio-upload |
| ACT_UPLOAD_KEY_PASSWORD | Private key password |

Only the repo owner should configure these. GitHub Actions will reconstruct the keystore temporarily, sign the AAB and leave the keystore out of artifacts.

## Build for Play

Open GitHub → Actions → **Sequmio Play closed-test AAB** → Run workflow. This separate manual workflow will fail before compiling if any of the signing secrets are missing. A successful run produces artifact `Sequmio-play-upload-aab` containing `Sequmio-play-upload.aab`.

The regular Sequmio CI builds a debug APK and an **unsigned** release AAB for verification. These unsigned artifacts are not Play uploads. Use the manual signing workflow with the registered permanent upload key for subsequent releases.

## V0.4 foreground-service declaration (mandatory for Play review)

The V0.4 manifest declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK` and `POST_NOTIFICATIONS`, and uses service type `specialUse`.

Explain in Google Play Console: "Sequmio runs a user-initiated, time-limited Android QA sequence that launches and verifies multiple user-selected third-party apps. A persistent foreground-service notification offers a Stop action, and the process requires continued operation while another app is foreground. There is no location, media, sync or data-transfer use case. Service terminates on completion, failure or user Stop."

Show a video of the user starting a test, the notification and Stop action, and service termination. The FGS declaration is additional to the Accessibility API declaration. Its approval is not guaranteed.

## Accessibility policy

V1 is **not** a disability-support accessibility tool; the project's `isAccessibilityTool=false` flag is intentional.

The Play reviewer must see the prominent disclosure/consent dialog before any use of Accessibility. The service observes foreground window package IDs and time; run history and the plan are kept on-device. In V0.7 the service can inspect visible UI node text/IDs during an active scenario and inject deterministic test actions (taps, swipes, text entry). It does not record screen content or raw taps, take screenshots, or upload UI content. The existing V1 disclosure video and Play declaration must be updated to demonstrate these new capabilities. The consent screen must be updated when V2 functionality adds more capabilities.

Provide an in-console Accessibility declaration and video. The service performs deterministic, user-configured app switching (not autonomous decisions).

## After uploading

Google Play may sign generated APKs with a different key than locally installed debug APKs. Installing the Play test version over a sideloaded debug version may require uninstalling the debug APK (which deletes local settings). Updates delivered via Play should then work normally.

## Current public policy draft

https://github.com/GIVLSE/AppCycleTester/blob/main/docs/PRIVACY_POLICY.md

## Package migration

The final selected Google Play package ID is `com.sequmio.givlse` (not the prototype's old `io.github.givlse.appcycletester`). Use **com.sequmio.givlse** in the Play Console app creation form before uploading any AAB. Android treats it as a separate app; uninstall the old debug package if unnecessary. Repository stays `GIVLSE/AppCycleTester`.

## Important

This repository currently has no connected Google Play Console integration. The signed AAB is **not automatically uploaded to Play**; the owner must create the app and submit the bundle.

## V0.8 Accessibility recorder disclosure
The service now also observes click, scroll and text-field-change accessibility event types while the user explicitly records their own app. It stores UI selectors, approximate scrolls and safe text-input placeholders locally, not actual typed content. The service uses a visible Accessibility overlay with Pause/Stop. Re-record the Play Console Accessibility demonstration covering explicit V3 disclosure, Recorder controls, local save, and opt-in publishing. Review policy and user data answers before upload.
