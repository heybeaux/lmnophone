<!-- Copyright 2026 LMNOPhone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Home notice clearance after the LMNOPhone rename

The reported first-row clipping is a defect. The list viewport did not overlap
the notice bounds; the reversed list initially showed its last items, clipping
an earlier app flush against the notice. The previously reviewed fix from
PLA-156 was still only on `mvp/ui`, not on `origin/main`.

The Lead selected the existing `../plainphone-ui` worktree and `mvp/ui` branch
for this follow-up. Merge `9592573` brings in `origin/main` at `6974442`, including
the LMNOPhone strings. The existing Home fix at `12f27ff` is retained without
new production changes: forward list order, bottom alignment for short lists,
and the measured 12 dp gap below the top zone. Its gap participates in the
existing fallback order. No policy or module interface changes.

## Regression and verification

Test commit `1000254` extends the bounds regression to **40 combinations**:
font scales 1.0, 1.3, 1.5, 1.8 and 2.0, each with Cancelling, NeedsAttention,
MonthlyCheck (Sam), ModeChangeWaiting, and turn-off today/tomorrow/weekday/date.
Each case checks the complete initial first row below the notice, full scroll
reveal and click events for four apps, and pinned Messages, Phone and More.

- **Red:** temporarily substitute `origin/main`'s HomeScreen.kt while keeping
  the new tests and merged strings. On a fresh API 35 emulator at 360×640 /
  160 dpi, the bounds regression fails at its first 100% case: App 1 is absent
  from the initial viewport. The attention golden verification also fails
  (two existing image differences and three missing intermediate baselines).
  Missing goldens alone are not the proof; the bounds failure is.
- **Green:** restore exactly the existing fixed HomeScreen.kt. All five
  `HomeScreenTest` emulator tests pass, including all 40 combinations, platform
  accessibility forward scrolling, events and below-minimum fallback access.
- **Goldens:** Home now has 19 fixtures × five scales = 95 images, plus three
  short-screen fixtures. Re-recorded intentionally for the merged product name
  and the added scale matrix. Inspected all 67 changed/new Home images: ten
  renamed notice images and 57 intermediate-scale images. The first app clears
  the notice. A following row can be partial at the bottom of the scroll area;
  the interaction tests prove it can be fully revealed.
- **Merge resolution:** two `confirmSending` images also needed refreshing:
  the branch's Sending… state and main's LMNOPhone/Alex fixture had both changed
  their baselines. Inspected both refreshed images. No new non-Home UI behavior.
- **Full debug UI verification:** `:ui:verifyPaparazziDebug` passes all 172 JUnit
  cases (some trusted-screen cases render multiple images).
- **Lint:** `:ui:lintDebug` passes with no errors; existing warnings remain.

The dedicated `pla263-ui-a35` emulator (`emulator-5752`) was stopped and deleted.
No physical phone, other agent's emulator, network fetch, or policy application
was used. System bars reserve 32 + 48 dp in screenshots and the instrumented
content frame is 360×560 dp. All new image fixtures contain synthetic data.

## Reproduce

Run Gradle from `app/`, with JDK 17 and Android SDK platform 36:

```sh
./gradlew --offline :ui:assembleDebugAndroidTest
# On a dedicated, booted 360×640 / 160 dpi API 35 emulator:
adb -s emulator-5752 install -r -t ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
adb -s emulator-5752 shell am instrument -w -r -e class org.plainphone.ui.HomeScreenTest org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
./gradlew --offline :ui:verifyPaparazziDebug :ui:lintDebug
```

For the red checkpoint, use tests at `1000254` and substitute only HomeScreen.kt
from `6974442`. The author run kept/restored the fixed file in run-owned scratch;
no reverted production source was committed. Original tests-first evidence is
also in `pla156-home-notice.md` (historical pre-rewrite hashes).

This is author evidence, not approval. The Lead routes cross-model review and
owns merging the existing UI lane. These commits do not merge the fix to main.
