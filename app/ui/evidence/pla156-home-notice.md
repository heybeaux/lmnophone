<!-- Copyright 2026 Plainphone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Home notice / app-list clearance

Base: `9db37c0` on `mvp/ui`. Regression checkpoint: `3243bd4`.

The top zone and list had separate bounds, but the reverse list opened at its
bottom. Its partially visible preceding row was clipped flush with the notice,
producing the apparent overlap in the four reported 200% goldens. Earlier apps
could be scrolled into view, but the initial presentation hid the first app.

The list now opens at the first supplied app when overflowing. Short lists
remain bottom-aligned. There is a 12 dp gap below the top zone, included in the
existing fallback measurements. Messages, Phone and More remain pinned; the
fallback order, font sizes, notice cap and all UI events are unchanged. A partial
following row may still appear at the scrolling viewport edge to indicate more
content; scrolling fully reveals it. This is not a promise that four apps fit
simultaneously alongside a three-line notice at 200%.

## Red / green

- Built the regression first and ran it on a fresh, dedicated API 35 emulator,
  `emulator-5744`, at 360×640 / 160 dpi. The Compose fixture reserves 80 dp for
  system bars and forces each font scale. No physical phone was used.
- Old implementation at `3243bd4`: `noticeLeavesACompleteFirstRowAndEveryAppCanBeRevealed`
  failed because App 1 was absent from the initial list viewport.
- After the fix, all five `HomeScreenTest` tests passed (`OK (5 tests)`). The new
  test covers four apps × four notices (Cancelling, NeedsAttention, MonthlyCheck
  with Sam, ModeChangeWaiting) × 1.3/1.5/1.8/2.0. It checks initial first-row bounds,
  notice clearance, full row bounds after scrolling, each OpenApp event, and
  pinned Messages/Phone/More. Notices exercise one through three visible lines.
- Updated the platform accessibility test to scroll forward through the normal
  list. Wait for the accessibility root before reading it; without that wait the
  fresh emulator intermittently returned null. The platform action reveals App 3.
- `:ui:verifyPaparazziDebug --tests 'org.plainphone.ui.Home*ScreenshotTest'` passed:
  38 Home cases (including the new four-app monthly fixture) plus 3 short cases.
- `:ui:lintDebug` and `:ui:assembleDebugAndroidTest` passed.
- Inspected the re-recorded 200% `oneTrustedPerson`, `longestWaitingNotice`,
  `overdueMonthlyCheck`, `pendingTurnOff`, and `fourAppsWithMonthlyNotice` images,
  plus the 100% monthly fixture. First app labels are complete below the notice.
  The dropped More description at 200% remains intentional per the tokens.

Commands from repository root, with JDK 17 and the local Android SDK:

```sh
./app/gradlew -p app --offline :ui:assembleDebugAndroidTest
adb -s emulator-5744 install -r app/ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
adb -s emulator-5744 shell am instrument -w -e class org.plainphone.ui.HomeScreenTest org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
./app/gradlew -p app --offline :ui:recordPaparazziDebug --tests 'org.plainphone.ui.Home*ScreenshotTest'
./app/gradlew -p app --offline :ui:verifyPaparazziDebug --tests 'org.plainphone.ui.Home*ScreenshotTest' :ui:lintDebug
```

Synthetic fixtures only. Independent review remains required; this report is
author evidence, not approval. The temporary emulator is removed after this run.
