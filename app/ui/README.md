<!-- Copyright 2026 Plainphone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Plainphone UI

This first slice provides the Lexend theme and U1 home screen. Other screens remain
in the UI lane backlog. The source of truth is `docs/ux/design-tokens.md` and
`docs/ux/flows-user.md` at the repository root.

## Host contract

- Wrap `HomeScreen(state, onEvent)` in `PlainphoneTheme` and give it the safe
  content area after Android's status/navigation bars. The host owns bar colours
  and dark-icon configuration.
- Supply formatted time, full date, short date, ordered apps, and at most one
  selected `HomeNotice`. Notice priority and clocks belong to the view-model/policy
  lanes; this screen only formats the supplied notice using string resources.
- App IDs are opaque and should be unique. Duplicate IDs remain renderable;
  `OpenApp(id)` returns the supplied ID unchanged for either entry.
  `OpenPhone` means the ordinary system dialer; there is no home emergency event.
- `MonthlyCheck` takes the trusted people's names as a list, in supplied order.
  The UI joins names using resources; an empty list hides the dependent notice.
  `TurnOffWaiting` takes the formatted clock time plus `HomeNoticeDay` (date and
  supplied days-from-today). The UI adds tomorrow, a weekday for days 2–6, or a
  short date for day 7 onward. Same-day values display only the time. The host
  owns date arithmetic and refreshes; the screen never reads the clock.
- `OpenNotice` opens the corresponding detail screen, including the full notice
  text and any cancellation action. The home box may ellipsize its visible text;
  its text semantics retain the full label.
- `HomeNotice.Cancelling` displays “Cancelling…”. Its unchanged `OpenNotice`
  event must route to U9c, Still cancelling. The host owns saving and retrying.
- Only the other-apps list scrolls on supported viewports. Measurement probes are
  excluded from the semantics tree, and the chosen layout supplies the controls.
  The list starts at its bottom, with the last supplied app above Messages and
  a 12 dp gap separating the two groups. Positional list keys tolerate duplicate
  IDs. The fallback order preserves the three-line notice by moving the clock
  inline before reducing the notice to two lines.

The upstream Lexend file is unchanged in `res/font/lexend.ttf`. Its licence ships
in `assets/fonts/OFL.txt`; the `lexend_notice` string is available for About.

## Verification

From `app/`:

```sh
./gradlew :ui:testDebugUnitTest :ui:verifyPaparazziDebug :ui:lintDebug
./gradlew :ui:assembleDebugAndroidTest
```

The 35 committed goldens include 16 home cases at font scales 1.0 and 2.0 on a
360×640 device, reserving 32 dp for the status bar and 48 dp for navigation.
These cover cancelling, eight apps, duplicate IDs, singular/plural minutes,
zero/one/two trusted people, and same-day/tomorrow/weekday/short-date wording.
Three additional 200% fixtures at heights 560, 540 and 480 exercise the
two-line notice, half-row and whole-page fallbacks below the supported minimum.
The inline clock with a three-line notice is covered at 360×640 and 200%.
`verifyPaparazziDebug` compares pixels against those files; ordinary unit-test
execution alone is not the golden comparison command. To intentionally refresh
the files, run `:ui:recordPaparazziDebug`, inspect every changed image, and submit
the changes for review. Fixtures contain only synthetic display data.

`HomeScreenTest` is an emulator instrumentation test. Install the generated
`ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk` on a dedicated
lane-owned emulator using `adb -s <owned-emulator> install -r -t ...`, then run:

```sh
adb -s <owned-emulator> shell am instrument -w -r \
  org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
```

Run on a 360×640 dp emulator. All three tests force 200% text using
`DeviceConfigurationOverride.FontScale(2f)`, independent of system settings.
They check bottom anchoring, pinned actions, scroll behavior, duplicate IDs,
cancelling, absence of an emergency control, event routing, a unique full
accessible notice, and reaching pinned actions below the minimum viewport.
Never use a physical device or an
unqualified connected-device Gradle task for this lane.
