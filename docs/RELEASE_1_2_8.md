# 1.2.8 — accumulated fixes (unreleased)

## BUG-128-01 — enabled Accessibility but disconnected Runner
Severity: Blocker / regression, reported against 1.2.7 (28).
User confirms Android permission remains enabled, but Service connection becomes unchecked after closing/reopening Sequmio. Disabling and re-enabling Runner restores connection temporarily.

Changes: separate observable live-service registry from run snapshots, identity-safe detach, restore reference only on verified Android service callbacks, verify serviceInfo and permission on Activity focus/resume and Start, avoid redirect to permission setup when permission is already granted, guard accessibility-event exceptions, add PID and connection lifecycle diagnostics. The read-only connection checkmark is never a user-controlled permission. Retry connection rechecks framework state; it does not force a system rebind. No privileged or hidden APIs, no background keep-alive, no changes to manifest permissions or Firebase. Android owns the actual binding; if the OS kills/unbinds an enabled service and never reconnects, app code cannot guarantee recovery. Exact OS-level trigger not confirmed; collect adb logcat -s SequmioRunner AndroidRuntime AccessibilityManagerService and adb shell dumpsys accessibility if reproducible. 

Acceptance: enable once; launch test; return; Back/Home/reopen; swipe ordinary Activity from Recents/reopen; verify system permission and live service, start another run without toggling. Repeat on Xiaomi 15 and Samsung A26; verify real system disable blocks start and stale service callback cannot clear newer connection. Distinguish normal Recents dismissal from Force stop in system settings. No build requested yet. Version remains 1.2.7 (28) on staging branch; bump only for combined release.
