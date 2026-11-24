# Sequmio 1.2.3 (24)

User acceptance 1.2.2: JOIN-01 through JOIN-08 and UI-01 PASS; Samsung Galaxy A26 5G Accessibility blocked; actual One UI setting/service status not yet verified.

- Optional 96x96 PNG launcher icon captured only for the published app, stored as bounded Base64 in apps/{packageName}. Local icon preferred, remote fallback before installation. Existing listings need Update published app after Rules deployment; old clients can read the listing but cannot render remote icons and may remove the optional field when republishing.
- Individual step Confirm / Confirmed glass pills replace checkboxes; still self-attestation and reversible before joining. Final Confirm has no check icon. Cancel and Delete account use translucent red glass pills. Disabled final Confirm is silver.
- Standard public Android Accessibility settings action, explicit Samsung Installed apps guidance; user consent and real system status remain required. No new Android permissions.
- Firestore optional icon field requires updated Rules; owner authorization unchanged. Regression adds five cases to previous 12 publication tests. No Storage or Blaze requirement.
- Retest both accounts, actual A26 enablement and run, icon before install, update publication, and UI.
