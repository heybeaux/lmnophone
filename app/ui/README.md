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
- App IDs are opaque. `OpenApp(id)` returns the supplied ID unchanged.
  `OpenPhone` means the ordinary system dialer; there is no home emergency event.
- `OpenNotice` opens the corresponding detail screen, including the full notice
  text and any cancellation action. The home box may ellipsize its visible text;
  its text semantics retain the full label.
- Only the other-apps list scrolls on supported viewports. Measurement probes are
  excluded from the semantics tree, and the chosen layout supplies the controls.

The upstream Lexend file is unchanged in `res/font/lexend.ttf`. Its licence ships
in `assets/fonts/OFL.txt`; the `lexend_notice` string is available for About.

## Verification

From `app/`:

```sh
./gradlew :ui:testDebugUnitTest :ui:verifyPaparazziDebug :ui:lintDebug
./gradlew :ui:assembleDebugAndroidTest
```

The 12 committed goldens cover six home states at font scales 1.0 and 2.0 on a
360×640 device, reserving 32 dp for the status bar and 48 dp for navigation.
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

Run once with system `font_scale=1.0` and once with `font_scale=2.0`, at 360×640 dp.
The test checks pinned actions, scroll behavior, absence of an emergency control,
event routing, and a unique accessible notice. Never use a physical device or an
unqualified connected-device Gradle task for this lane.
