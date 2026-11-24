# Sequmio 1.1.8 (20)

Runner returns to Sequmio on terminal completion (PASS, FAIL or STOPPED) when Android permits foreground activity launch and the device is unlocked. A prominent Run finished card shows actual status, reason, elapsed time and completed cycles. A completion notification is available when notifications are enabled; it opens the Runner or saved History if the live state is unavailable. No fake PASS, wake/unlock, artificial touches or automatic changes to Android timeout. For longer runs on a dedicated test device, use Android Developer options > Stay awake while charging.

Device QA: normal timed and E2E completion, failure and notification Stop, time cap, locked screen, notification denied, process recreation, no duplicate completion report, next run resets status. Android 11/14/16 OEM behavior must be verified.
