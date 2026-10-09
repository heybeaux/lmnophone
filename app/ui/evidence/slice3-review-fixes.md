<!-- Copyright 2026 Plainphone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Slice 3 Lead review fixes — PLA-112

Follow-up to the Lead review of `5747208`, findings F1 and F2.

## F1: keep keyboard entry in the same layout

`TaskScreen` now measures the fallback outside `imePadding`. The selected layout
still consumes IME insets for placement. The keyboard cannot reduce the height
used for the pinned/whole-page decision and dispose the focused field.
Display/font/rotation changes still remeasure; keyboard and sending updates do not.

`TaskKeyboardTest` uses an edge-to-edge test activity and the emulator's actual
IME, waits for `WindowInsetsCompat.Type.ime()` to be visible and the inset
animation to settle, and injects Android key events with `sendStringSync`.
It checks focus, persistent keyboard visibility, host-received input and the
unchanged layout tag. Cases: Enter, Recovery and NewPin × one/two trusted people,
all at 200% text with a long trusted-person name. Both NewPin fields are tested.
There is no add-app search field in this UI slice: app review receives installed
identity and request text, while Play search remains a host navigation seam.

Before the fix, four of six keyboard cases (Enter and Recovery, one/two people)
lost focus. NewPin already passed. These are real keyboard regressions, not
`performTextInput` semantics-only tests.

## F2: remeasure when actions appear or disappear

The decision keys include `primary != null` and `secondary != null`, never their
labels. Stopping → StillStopping can now gain an OK action without retaining a
layout measured for no footer. `TaskActionPresenceTest` covers the transition
and its reverse at 200%, with a long name on a 360×300 dp short display, where
the new footer crosses the fit boundary. It failed before the fix.

## Validation

- Before the fix: 7 targeted tests ran, 5 failed (four focus regressions and
  the action-presence regression). After the fix: all 7 passed.
- Dedicated API 35 emulator, 360×640 dp at 160 dpi. Both tests force 200% text;
  the footer boundary fixture reserves a 360×300 dp short region within it.
- The existing UserScreensTest, TrustedScreensTest, SliceThreeCompletionTest
  and RecoveryCodeTest suite also passed: 42 tests including the 80-case name
  matrix. Total this review round: **49 emulator tests passed**.
- From `app/`: `./gradlew --offline :ui:testDebugUnitTest :ui:verifyPaparazziDebug :ui:lintDebug`
  passed: 113 JVM tests, zero failures/errors/skips, 1,973 unchanged goldens,
  zero lint errors. No golden refresh was necessary.
- Build the emulator test APK with `./gradlew --offline :ui:assembleDebugAndroidTest`.
  Targeted classes are `org.plainphone.ui.TaskKeyboardTest` and
  `org.plainphone.ui.TaskActionPresenceTest`, run through
  `org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner` on the owned emulator.
- No fetch, other-worktree edits or physical-device actions. The dedicated
  emulator was stopped and removed after verification.

The production change only affects task layout measurement and keys; it adds
no policy or secret state. The base slice evidence remains in `slice3-ready.md`.
