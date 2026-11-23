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
