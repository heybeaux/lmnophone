<!-- Copyright 2026 LMNOPhone contributors; SPDX-License-Identifier: Apache-2.0 -->
# Calm Home: centered plain-text More

Implements the approved visual revision in [PLA-285](/PLA/issues/PLA-285).
The separate slice begins after `ec87399` on the existing owned `mvp/ui` branch.
`811412e` commits the regression before the implementation.

More is centered with no subtitle, caret or idle border. Its existing full-width
inset touch target (312 × 64 dp at 360 dp width), accessible name, button role,
pressed/focus feedback and `HomeEvent.OpenMore` remain. The obsolete subtitle
resource and subtitle-removal layout fallback are removed. Home already had no
horizontal divider components; whitespace continues to separate its elements.
No policy, interface or host navigation changes. The preview shell uses this
HomeScreen and still maps OpenMore to More, whose emergency route is unchanged.

Updated references: U1 flow and sketches, design tokens and short-screen fallback
order, four HTML Home mockups, UX overview, copy-test packet and the historical
copy-test result (marked superseded). No other screen was redesigned.

## Evidence

[Before/after screenshots](/api/attachments/f5061433-33ea-40bd-bd26-e272f9a8c802/content)
show first-look and long-notice Home at 100% and 200%, 360 × 640 dp, with 32/48 dp
system-bar space. Before images are the committed baselines at `ec87399`.
All fixtures use synthetic data.

- **Red:** the new More test fails against the old screen because the subtitle
  is still present. Emulator API 35, dedicated `pla285-ui-a35`, 360 × 640 / 160 dpi.
- **Green:** the five existing Home emulator tests pass, including the 40-case
  notice/text-size bounds matrix, scrolling, pinned actions, event routing and
  platform accessibility. The new test initially also selected the unmerged,
  cleared layout probes; after excluding these invisible probes, its targeted
  rerun passes at 100%, 130%, 150%, 180% and 200%. It checks absent subtitle/caret,
  centered label, exact accessible text, preserved target dimensions and OpenMore.
  Production code and the other five tests were unchanged for that rerun.
- **Screenshots:** intentional Home-only recording. Inspected every changed
  image: 95 regular fixtures plus two short-screen fixtures, 97 total. The third
  short-screen fixture is unchanged because More starts below the scroll viewport.
  Existing partial trailing app rows remain scrollable; the first row is complete
  at the supported minimum. Messages, Phone and More remain visible there.
- **Verification:** full `:ui:verifyPaparazziDebug` passes 172 JUnit cases,
  zero failures/errors/skips; `:ui:lintDebug` passes (existing warnings remain).
- **Cleanup:** only the dedicated emulator was used, then stopped and deleted.
  No physical-device I/O, device-owner provisioning or emergency call.

## Reproduce

From `app/`, JDK 17 and the configured Android SDK:

```sh
./gradlew --offline :ui:assembleDebugAndroidTest
# Dedicated 360 × 640 / 160 dpi API 35 emulator only:
adb -s emulator-5756 install -r -t ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
adb -s emulator-5756 shell am instrument -w -r -e class org.plainphone.ui.HomeScreenTest org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
./gradlew --offline :ui:verifyPaparazziDebug :ui:lintDebug
```

Author evidence only. The Lead performs the configured Anthropic review before
integration and owns the numbered preview build, install/readback and delivery
cadence in [PLA-286](/PLA/issues/PLA-286). This UI slice is not a device install.
