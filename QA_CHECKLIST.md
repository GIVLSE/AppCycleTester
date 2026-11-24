# V1 device QA checklist

Planned test targets: Android 11, 14, and 16 (root not required).
Use a dedicated device with screen kept awake.

| ID | Action | Expected outcome |
|---|---|---|
| V1-01 | Open app chooser | Launchable installed apps listed |
| V1-02 | Choose 3 apps, reorder, restart controller | Order and timers persist |
| V1-03 | Run ONCE with 1/2/1 minutes | 3 apps opened in order, run finishes |
| V1-04 | Run COUNT=2 with 1/2/1 minutes | Exactly 2 passes, 8 minutes |
| V1-05 | Run ENDLESS, overall cap 5 minutes | Ends at cap |
| V1-06 | Pause 10 seconds, resume | Timers exclude pause |
| V1-07 | Stop and restart | Restarts at first app |
| V1-08 | Reopen controller during run | Shows state and event history |
| V1-09 | Disable Accessibility during run | Runner terminates |
| V1-10 | Uninstall selected app before start | Failed launch logged |
| V1-11 | Test OEM foreground policies | Missing window confirmation is FAIL |
| V1-12 | Multiple launcher icons for one package | One entry per package (V1 limitation) |

PASS is not established just by calling startActivity(): check actual foreground windows and duration.
These are test cases, NOT completed real-device test results.

## V1.1 onboarding tests

| ID | Action | Expected outcome |
|---|---|---|
| ONB-01 | Disable Accessibility, select apps, press Start | Setup dialog opens; Start is not blocked and no test starts |
| ONB-02 | In setup, tap App info | Opens **Sequmio** system app details |
| ONB-03 | In setup, tap Accessibility | Opens Android Accessibility settings |
| ONB-04 | Enable service and return | Dialog shows connected state and **Start test** |
| ONB-05 | Start test after enabling | Cycle begins, without manually restarting the app |
| ONB-06 | Disable service again | Subsequent Start requests setup again |
| ONB-07 | Android 13+ sideloaded installer | Guide explains restricted settings, user grants manually only if required |
| ONB-08 | Android 11/no restricted settings | User can skip App info and directly enable service |

No tests in this section are marked PASS without testing on-device.

## Rebrand verification

- [ ] App label displays `Sequmio`.
- [ ] `adb shell pm list packages com.sequmio.givlse` finds the installed package.
- [ ] Accessibility service is shown as `Sequmio Runner`.
- [ ] The Google Play bundle manifest has applicationId `com.sequmio.givlse`.

## V0.3.2 visual accessibility — contrast and typography

- [ ] All headings, metric values and body text render light on the dark background, even outside Cards.
- [ ] Onboard Accessibility disclosure, checkboxes and action labels are readable.
- [ ] Dialogs: Add Apps search input, dropdown labels, selection rows, duration picker and Cancel/Save.
- [ ] Controls: Start, Pause/Resume/Stop, disabled editing buttons, selected/unselected repeat chips, overall duration switch.
- [ ] Bottom navigation: selected/unselected tabs and history timestamps.
- [ ] Test with HyperOS dark mode AND light mode, Android 11/14/16, and large accessibility text size.
- [ ] Confirm system status/navigation bar icons are light on dark backgrounds.

New color tokens: content #F4F6FC; muted #ADBAD0; text links #B99AFF; filled buttons #7048B9 with white text. Normal-text contrast ratios against #1B263A Cards: content 14.03:1, muted 7.73:1, links 6.59:1; white on filled buttons 6.33:1 (WCAG AA ≥4.5:1).

## V0.3.3 synchronized Android permissions

- [ ] First launch: consent checkbox unchecked, system permission reflects Android state; tapping system permission cannot open Android settings until user accepts disclosure
- [ ] Agree & continue: consent checkmark becomes checked but Android access stays unchecked until separately enabled in Settings
- [ ] Android 13+ sideload restriction: optional App Info link is shown only when service isn't enabled; app does not silently bypass restricted settings
- [ ] Permission row opens Sequmio Runner details when ROM supports direct settings; otherwise falls back to Accessibility settings
- [ ] After returning from settings, system checkbox refreshes automatically; connected checkbox follows live Accessibility service connection
- [ ] Disable Sequmio Runner in Settings and return: system and connected state becomes unchecked; Start requires setup
- [ ] No bogus permissions or toggles for APIs not declared in AndroidManifest.xml
- [ ] Existing plan, execution and Play policy disclosure flows continue to work

## V0.4 Reliability device acceptance tests (not yet executed)

| ID | Test | Expected |
|---|---|---|
| REL-01 | Start 3 apps 5s / 10s / 5s | Correct ordering and second-level durations; PASS only after foreground confirmations |
| REL-02 | Open test while notifications are allowed | Persistent notification with Stop action; tap Stop, report status STOPPED |
| REL-03 | Deny POST_NOTIFICATIONS on Android 13+ | Runner still starts; in-app Stop works; optional permission shown as denied |
| REL-04 | Background app during run for 5 seconds | Up to 2 relaunch attempts; finite FAIL if target never returns |
| REL-05 | Target app launch fails / is uninstalled | Retry bounded, failed target logged; no false PASS |
| REL-06 | Permission Controller UI appears in another app | Wait for user action up to 45 seconds, never auto approve |
| REL-07 | Screen locks/turns off mid-test | FAIL, FGS stops, wake lock released; no PASS after screen wakes |
| REL-08 | Interrupt/disable Accessibility | FAIL, notification removed, report persisted |
| REL-09 | Force-stop/kill app process during run, reopen | Report marked INTERRUPTED (no auto resume) |
| REL-10 | Pause 5s then resume | Paused time excluded from per-app foreground time |
| REL-11 | Stop via foreground notification | Report STOPPED and no background cycling |
| REL-12 | History -> Share JSON/CSV | User-selected app receives only opted-in report; valid JSON and CSV columns |
| REL-13 | Continuous 30-minute run | Test Android 11, 14 and 16; no false foreground PASS; battery impact measured |
| REL-14 | Play Console FGS declaration | specialUse use case, permissions and review video completed before upload |

**Known limitation:** FLAG_KEEP_SCREEN_ON does not keep another app awake. Use a dedicated device, appropriate screen timeout or Stay awake while charging. Play Console must review the new specialUse FGS.

## V0.5 navigation, profile and draft acceptance (not yet executed)

| ID | Test | Expected |
|---|---|---|
| NAV-01 | Open Sequmio | Four bottom tabs: Runner, App List, My App, History |
| NAV-02 | Navigate to App List and My App during a test | Existing runner keeps cycling; no plan reset |
| NAV-03 | Switch to Runner -> Edit -> Save | Existing V0.4 plan remains intact |
| NAV-04 | Open gear from each main tab | Settings opens, Back returns to originating tab |
| NAV-05 | Settings -> Permissions -> Back | Returns to Settings, not a random tab |
| NAV-06 | Settings -> About links | Version 0.5.0, Privacy Policy, Terms, Support links open |
| APP-01 | Fill My App fields and Save | Draft persists after force-close/restart; no accidental publication |
| APP-02 | No Google configuration supplied | Sign-in clearly displays Not Configured, no simulated user |
| APP-03 | No community catalog backend | App List shows empty state, no fake apps or functional Join button |
| AUTH-01 | Configure real Firebase + Google OAuth and sign in | Real credential picker and Firebase-authenticated user; Firestore owner-only user document |
| AUTH-02 | Sign out and sign in with a different account | Firebase session cleared; new identity shown |
| AUTH-03 | Firestore rules unauthorized access | User A cannot read/write user B's profile |
| AUTH-04 | Play app-signing SHA-1 not yet added | Sign-in failure is surfaced; no false logged-in state |
| AUTH-05 | Signed Play AAB and debug app | Both OAuth signing certificate fingerprints accounted for when required |
| REG-01 | Repeat V0.4 3-app run, stop notification, History export | No functional regression in existing Runner/History |

## V0.6 community acceptance (not executed)

| ID | Check | Expected |
|---|---|---|
| CAT-01 | No Firebase config | App List shows honest unavailable status |
| CAT-02 | Authenticated developer creates My App | Required fields, links, valid scenario, Save Draft |
| CAT-03 | Developer publishes | Entry appears in `apps/{packageName}` with owner UID and published status |
| CAT-04 | Two users edit same package | Non-owner cannot override original listing |
| CAT-05 | Another user opens App List | Real published entry appears; no fake samples |
| CAT-06 | Join Group and Play opt-in only | No claim of verified participation |
| CAT-07 | Device has not installed app | Confirm Join disabled; install first |
| CAT-08 | Tester confirms all 3 stages | Firestore membership saved and app added to local Runner |
| CAT-09 | Developer unpublishes | Listing disappears from public query |
| CAT-10 | Android 11,14,16 | All external intents open safely; app list refreshing on return |
| CAT-11 | Account switches user | Cloud membership scoped by authenticated UID |
| CAT-12 | Without scenario | Publish disabled (no WAIT-only scripts) |

## V0.7 scenario execution (device tests pending)

| ID | Scenario | Expected |
|---|---|---|
| E2E-01 | WAIT + ASSERT_EXISTS visible selector | PASS recorded after entire scenario pass |
| E2E-02 | CLICK via view ID | Only selected app targeted; click dispatched |
| E2E-03 | CLICK with fallback x/y | Normalized tap within device display |
| E2E-04 | SWIPE relative coords | Android gesture completes before next action |
| E2E-05 | INPUT_TEXT editable text field | Predefined text entered; value not logged |
| E2E-06 | INPUT_TEXT password field | Step fails, input not injected |
| E2E-07 | ASSERT_EXISTS missing required element | FAIL; next app runs with failure flag |
| E2E-08 | Optional missing selector | Log skip and continue |
| E2E-09 | loop_steps true | Repeats until per-app timer expires |
| E2E-10 | App changes foreground mid scenario | No action injected into wrong package; recovery invoked |
| E2E-11 | Pause/resume during scenario | In-flight actions stop; replay restarts safely on resume |
| E2E-12 | Existing consent from v0.6 | V2 disclosure required before starting |
| E2E-13 | Play Accessibility declaration video | Shows consent, UI content access, taps and test execution |

## V0.8 recorder (device tests not executed)
| ID | Test | Expected |
|---|---|---|
| REC-01 | Open own app in My App and press Record | Own installed app launches, floating REC/Pause/Stop visible |
| REC-02 | Tap a visible accessible button | CLICK step with selector and normalized fallback |
| REC-03 | Scroll list | SWIPE step with direction and normalized coordinates |
| REC-04 | Type text in non-password field | INPUT_TEXT with placeholder only, actual typed text not stored |
| REC-05 | Type password | No recorded text or password event |
| REC-06 | Pause/Resume overlay | Events omitted while paused |
| REC-07 | Stop overlay | Scenario draft saved locally and Sequmio opens |
| REC-08 | Unedited INPUT_TEXT placeholder | Publishing disabled until replacement |
| REC-09 | Screen lock/service disabled | Recorder stops and removes overlay |
| REC-10 | Accessibility V2 consent from older version | V3 consent required before recorder |
| REC-11 | Accessibility event unavailable/custom canvas | No false claim all touches recorded |

## V0.9 integration tests (not yet executed)

| ID | Scenario | Expected |
|---|---|---|
| INT-01 | Join 2 published apps | Both appear in Runner, original local apps remain |
| INT-02 | Reorder imported apps and reopen App List | Runner order unchanged |
| INT-03 | Owner edits published scenario; tester refreshes App List | Imported target definition updates |
| INT-04 | Tester offline | Existing locally cached test plan remains functional |
| INT-05 | Complete PASS run for imported app | One local QA day recorded |
| INT-06 | Repeat on same local date | Still one local QA day |
| INT-07 | FAIL/STOPPED run | No local QA day added |
| INT-08 | Sign out and switch accounts | Other account cannot read private memberships |
| INT-09 | Delete cloud account and confirm dialog | Owned listings, memberships, user profile and Auth identity removed |
| INT-10 | Firebase requires recent login for delete | Visible error, no silent success |
| INT-11 | Access external deletion page | Public link loads and directs users to request deletion |

## V1.0 release candidate — manual acceptance still pending
| ID | Scenario | Expected |
|---|---|---|
| RC-01 | Visual editor add/reorder/remove CLICK/WAIT/SWIPE/INPUT_TEXT/ASSERT/BACK | JSON matches UI |
| RC-02 | Import recorded steps into visual editor | Selectors and delays editable |
| RC-03 | Advanced JSON → Visual | Valid JSON edits preserved |
| RC-04 | Invalid/WAIT-only scenario | Save/publish blocked |
| RC-05 | Add own scenario to Runner | Local plan updated; test starts only at user request |
| RC-06 | Delayed element appears after 2 seconds | Player retries then executes |
| RC-07 | Properly signed Play AAB | Existing upload certificate, versionCode 15 |
| RC-08 | Real Firebase backend + Android | Auth, App List, publishing, Join, sync, deletion tested |
| RC-09 | Real device matrix Android 11/14/16 | Accessibility, Recorder, FGS, History verified |
| RC-10 | Play Console Accessibility/FGS/Data Safety declarations | Updated and approved |
