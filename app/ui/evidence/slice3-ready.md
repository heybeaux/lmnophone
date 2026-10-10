<!-- Copyright 2026 Plainphone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Slice 3 ready for review — PLA-112

Implementation and test evidence; approval remains with the Lead and Verifier.

- Branch `mvp/ui`, worktree `plainphone-ui`, based on local `main` `7ab89de`.
  Rebase used local refs only. Only the UI module changed.
- Covers spec task 4.8: T1–T8 trusted-person presentation; P1/P3–P6 sessions;
  M1–M8 people/waits/cancel and M4a self-managed stuck handover; U9/U10; L4.
  People selection supports one/two people, supervised/self-managed variants,
  fresh-authority navigation events, 24-hour wait/cancel/skip/stuck/stop copy,
  permission choices, approvals, session checks and turning-off presentation.
  Setup enrollment substeps reused by M2c/M6 and device effects remain host seams.
- U5/U5c/U6b disable submission and read **Sending…** while sending. Leaving
  a saved request remains enabled. Names use the shared bidi-control filter.
- PLA-130 copy is applied: self-managed PIN/permissions/app details/removal,
  wrong recovery code and separate code lockout with PIN-available/PIN-waiting/
  Skip routes, self-managed stuck handover and both Stop-confirmation variants.
- The approved fallback measures the band, title and actions at real text/display
  size. A crowded task scrolls as one page, with secondary above primary after
  the body. Ordinary actions remain pinned. Tests cover changing font size,
  keeping the layout/scroll stable during sending, and starting new tasks at top.
  Native Android scrollbar is visible in the emulator evidence below; Paparazzi
  does not render that native View scrollbar into its Compose snapshots.
- Tests first: the three Sending… tests failed before the original fix. Four
  completion regressions failed before the approved copy/layout change. The
  recovery tests initially failed compilation on the missing state/event seam.
- Required command from `app/` passed:
  `./gradlew --offline :ui:testDebugUnitTest :ui:verifyPaparazziDebug :ui:lintDebug`.
  113 JVM tests, zero failures/errors/skips. Lint: zero errors; warnings remain
  for dependency/version-catalog/plural-resource suggestions.
- **1,973 goldens** on 360×640 dp, reserving 32 dp status and 48 dp navigation.
  All fixture stages at 1.0/2.0; every named fixture additionally at
  1.3/1.5/1.8/2.0 × one/two people × short/long synthetic input. Pixel audit:
  every image is grayscale. No text shrinks or ellipsizes in task screens.
- **42 emulator tests passed**: UserScreensTest (23), TrustedScreensTest (12),
  SliceThreeCompletionTest (5), RecoveryCodeTest (2). The 80-case runtime name
  matrix checks bidi removal, occupied line widths/heights and no ellipsis.
  The screenshot-capture variant of the long-name scrolling test passed again.
- Dedicated API 35 emulator `emulator-5740`, 360×640 dp, no physical device.
  Screenshots contain synthetic names only. The Android status/navigation bars
  belong to the test host; the UI module receives its safe content area.
- Accessible headings, masked secret inputs, permission-group labels and action
  semantics remain in place. Compose owns accessible scrolling; the native
  scrollbar has no accessibility or touch action. This run verifies semantics
  and emulator interaction, not a new manual TalkBack gesture audit.

## Long name at 200%

[Initial page, complete title and band](long-pin-top.png) and
[scrolled to the secondary and primary actions](long-pin-actions.png).
Both are native emulator screenshots. The same whole-page fallback fixes the
long app/user confirmation title recorded in the earlier checkpoint.

## Integration boundaries

UI consumes immutable state and emits intent. PIN checks, recovery counters,
waits/clocks, eligibility, SMS, fresh-authority enforcement, durable cancellation,
FRP/device effects and activity security belong to the host. Full integration
obligations and event-routing notes are in `../README.md`.
