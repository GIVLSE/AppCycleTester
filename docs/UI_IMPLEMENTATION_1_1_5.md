# Sequmio v1.1.5 — UI implementation specification

## Goal
Implement approved neon-dark four-screen mockup without changing QA automation, data or Firebase functionality.

## Theme
Navy/indigo gradient, restrained cyan and violet accent outlines, 20–24dp rounded cards, primary gradient button, Material outlined navigation icons, real package-manager launcher icons for installed apps. Dark-text contrast and accessibility labels retained.

## Four tabs
- Runner: SequmioMark and title; three metric cards; Test Plan heading/Edit, **Start Plan** at top, expandable real app rows with package icons and durations; Runner Status preserves latest actual result.
- App List: search, published community cards with legitimate Join flow and installed-app icons, separate installed-device list if shown. No fake listings or pretend membership. Actual backend-unconfigured state preserved.
- My App: developer listing and QA dashboard entry; separate App Details, Closed-test Links and Test Suites/Recorder cards. All real form fields and actions retained.
- History: colored real PASS/FAIL/STOPPED cards, JSON/CSV share actions, expandable StepResult details and event timeline.
- Global header: SEQUMIO wordmark and outlined Settings gear; bottom navigation icons Runner=PlayArrow, App List=Layers, My App=Code, History=History; selected tab pill.

## Safety and QA
Do not change Session Player, Accessibility, permissions, WorkManager uploader, Firestore rules, cloud reports or test persistence. No new permissions or backend services.
Version 1.1.5 / code 17. Verify Android GitHub Actions, original upload keystore certificate, AAB release, and link results. Device acceptance is still user-side.

## Owner acceptance
[ ] Test Plan start at top
[ ] hide/show actual apps
[ ] app icons load; missing apps fall back to letter
[ ] active Pause/Resume/Stop
[ ] Runner Status latest real result
[ ] My App editing, links, scenario recording, publishing
[ ] App List search and real catalog or honest offline state
[ ] History reports and action exports
[ ] Bottom tabs and settings functionality
