<!-- Copyright 2026 LMNOPhone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Needs-attention presentation — PLA-377

Base: `mvp/device` at `b150c6f`. Branch: `ui/pla377-needs-attention`.

Covers calm-home-screen “Turn-off pending under needs attention” presentation
and design C2/C8 cancellation reachability. The host integration and settings
route remain PLA-92 work; this does not complete all of spec task 2.9.

## Tests first

Before implementation, this command failed in `:ui:compileDebugUnitTestKotlin`:

```sh
./gradlew :ui:testDebugUnitTest --tests '*NeedsAttentionScreenshotTest'
```

The errors were unresolved references to `NeedsAttentionScreen`,
`NeedsAttentionUiState`, `PendingChange`, and `PendingChangeKind` in the new test.
The screen and types were then added.

## Passing checks

Run from `app/`, JDK 17:

```sh
./gradlew :ui:recordPaparazziDebug --tests '*NeedsAttentionScreenshotTest'
./gradlew :ui:verifyPaparazziDebug --tests '*NeedsAttentionScreenshotTest' :ui:assembleDebugAndroidTest :ui:lintDebug
```

All 35 new goldens were inspected: no pending change, pending turn-off with
tomorrow and a longer date, pending people/setup, and committing turn-off/people,
each at font scales 1.0, 1.3, 1.5, 1.8 and 2.0. Every fixture is 360×640 dp,
reserving 32 dp above and 48 dp below for system bars. Existing goldens were not
re-recorded. Longer progress bodies scroll; Cancel and OK remain pinned.
The reused `wait_off_progress` resource now uses the approved LMNOPhone name.

Dedicated API 35 emulator `pla377-ui-a35`, `emulator-5784`, at 360×640 dp:

```sh
adb -s emulator-5784 install -r -t ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
adb -s emulator-5784 shell am instrument -w -r -e class org.plainphone.ui.NeedsAttentionScreenTest org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
```

Result: `OK (3 tests)` (10.363 seconds).
- 20 scale/kind combinations: Cancel and OK fully visible without scrolling;
  clicking emits CancelChange and Back respectively.
- Host pending-to-committing transitions remove Cancel and “Tap to cancel” for
  every supplied kind; progress wording and OK remain reachable.
- No-pending case shows title, reassurance and OK without cancellation.

Built: UI test APK. Installed/exercised: UI test APK on that emulator only.
No integrated app candidate or physical-device installation. These tests prove
presentation/events, not persisted cancellation, phone recovery or emergency
access. No calls were placed. Host must route CancelChange to ConfirmCancel
with null context and recheck the live change before acting.
