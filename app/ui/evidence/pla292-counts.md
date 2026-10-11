<!-- Copyright 2026 LMNOPhone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Optional communication counts — PLA-292

UI-only implementation of the approved `add-optional-communication-counts`
proposal/design D4, D6, D7 and calm-home-screen requirement at `091243b` on
`spec/communication-counts`. Base: `1090396`; owned branch: `mvp/home-counts-ui`.

- Red test commit: `31acad305ed13312b7c31c07bb81271f0f8f75a9`.
- Implementation/goldens: `8298d341f508a7ce30193c004a0bddd6e7cd0485`.
- Host interfaces are documented in `docs/mvp/lanes.md` and `app/ui/README.md`.
  Copy and the approved visual exception are recorded in the UX references.

## Delivered

Home receives nullable missed-call/unread-text counts. Null, zero and negative
values render no number; 1–99 render directly, then 99+. The number is regular
Lexend with tabular figures, in the label's ink and size. Label and number share
one action, with plural accessibility copy using the actual count. Other apps
have no count field or rendering route. Real bottom-zone measurement includes
wrapped numbers before selecting the existing compact Home layout.

`PhoneSettingsScreen` provides Back, one off-by-default switch, SMS/MMS/RCS
coverage copy and host-driven availability lines while on. It uses the existing
`app_name` resource key, declared in UI as well as the host so this independent
library can render the product name. No rename or other screens are changed.
More already emits `OpenPhoneSettings`; a UI host fixture verifies that route.
The host slice must wire the destination and own saving, permissions, sources
and immediate hiding on off/unavailable. This slice does not query or retain data.

## Tests and screenshots

Before implementation, `:ui:compileDebugUnitTestKotlin` failed because count
fields and the PhoneSettings API did not exist. The separate test commit records
that red checkpoint. During verification, the new strict `hasVisualOverflow`
assertion reported unused paragraph width as overflow (Phone: 98 px text node,
312 px cached paragraph). The test now checks actual rendered line edges,
ellipsis, vertical bounds and containment inside the complete row. This changed
no production layout. A compile typo in the test's DpRect height check was also
corrected after the red commit.

Full `:ui:verifyPaparazziDebug` passes **182 JUnit cases**, zero failures, errors
or skips, comparing **2,097 goldens**. Only the new count/settings suite was
recorded: **65 new goldens**, all visually inspected; **2,032 existing goldens
unchanged**. `:ui:lintDebug` passes with pre-existing warnings. OpenSpec validation
passes both changes present on this UI branch; the count spec is separately
owned on its spec branch.

Emulator: dedicated `pla292-ui-a35`, API 35, 360×640 at 160 dpi, serial
`emulator-5756`. Compose fixtures reserve 80 dp for system bars. The four new
communication tests cover count boundaries and spoken plurals, event routing,
one control per Home row, More → switch without PIN, host-controlled switch
state, unavailable text, scrollable settings at five font scales, and Home
bounds across **1.0/1.3/1.5/1.8/2.0 × no/longest notice × off/both 99+**.
All **10 emulator tests pass**, with no failures.
The six existing Home regressions are included, covering the larger notice/app
matrix, scrolling, accessibility scrolling and preserved More targets.

[Before/after evidence](/api/attachments/24c012a5-67ba-4337-b994-9caf0168919a/content)
contains 360×640 frames at 100% and 200%: base Home, counts off, both counts on,
switch off and switch on. Base frames come from `1090396`; counts-off frames
are pixel-identical to them. All fixtures use synthetic data. Settings can scroll
to both unavailable lines at 200%; no label is ellipsized or horizontally clipped.

## Reproduce

From `app/`, with JDK 17 and the Android SDK configured:

```sh
./gradlew --offline :ui:verifyPaparazziDebug :ui:lintDebug :ui:assembleDebugAndroidTest
adb -s emulator-5756 install -r -t ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
adb -s emulator-5756 shell am instrument -w -r -e class org.plainphone.ui.CommunicationCountsTest,org.plainphone.ui.HomeScreenTest org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
```

Use a dedicated emulator with that serial; never a physical device. No device
owner provisioning, real-phone operation, emergency call, preview integration
or publication was performed. The Lead owns cross-model review and later host
integration; these are author test results, not approval or a device delivery.
