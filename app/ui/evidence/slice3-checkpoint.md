<!-- Copyright 2026 Plainphone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Slice 3 checkpoint — PLA-112

This is implementation evidence, not acceptance or a Slice ready handoff.

- Base: local `main` `09bae12`, rebased without fetch. Worktree `plainphone-ui`,
  branch `mvp/ui`. No files outside `:ui` changed.
- Sending regression: three emulator tests failed before the production label
  fix (U5, U5c, U6b). The failure was absence of `Sending…`; the U5 state first
  failed compilation because the `sending` input was absent.
- Trusted fixtures were written before their screen functions and failed
  compilation on those missing functions. The same approach covered the later
  app-detail, opening, update and turn-off screen entry points.
- Required command from `app/`:
  `./gradlew --offline :ui:testDebugUnitTest :ui:verifyPaparazziDebug :ui:lintDebug`.
  Result: green, 113 JVM tests, no failures/errors. Lint has no errors; warnings
  include existing dependency updates and plural-resource candidates.
- 1,737 committed goldens, including 1,648 for the new trusted-screen fixtures.
  New fixtures reserve 32 dp status + 48 dp navigation on 360×640 dp. Base cases
  run at 1.0/2.0. Named variants run at 1.3/1.5/1.8/2.0, one/two trusted people,
  short/long synthetic names. No account details or real contact data are used.
- Dedicated API 35 emulator, serial `emulator-5740`, 360×640 at 160 dpi. No
  physical device or sibling team's emulator was used.
- `UserScreensTest` + `TrustedScreensTest` (excluding the separately run matrix):
  **34 passed**. The additional matrix test: **1 passed**, covering 80 combinations
  across PIN lockout, app confirmation, people handover, stuck changes and Play
  session custody screens. It checks bidi removal, occupied line widths/heights,
  and absence of ellipsis. Paragraph-relative coordinates for centered labels
  are normalized to occupied widths; integer rounding allows one pixel.
- The matrix verifies text layout, not whole-viewport visibility. Long-name
  200% titles remain partly below the scroll viewport between the band and
  pinned actions. An example is attached to the issue. The proposed whole-page
  scrolling fallback is awaiting the designer's decision.

Outstanding copy questions on the issue's saved designer interaction:

1. Self-managed PIN entry and permissions: supervised copy implies the user
   cannot change permissions alone.
2. Recovery-code lockout: it needs its own state, without offering the same
   locked code as the alternative.
3. Self-managed → trusted, stuck reset-protection change: there are no current
   trusted people to substitute into the supervised body/stop confirmation.

The host contract and integration boundaries are in `../README.md`. Follow up
on the pending designer response, add those states/copy and the agreed layout,
refresh goldens, rerun checks, then post Slice ready for Lead/Verifier review.
