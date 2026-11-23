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
