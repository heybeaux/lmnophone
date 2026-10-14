# Person-side TalkBack — slot 7e

Source: [PLA-579](/PLA/issues/PLA-579). Base `3e048c0c`. Author branch:
`pla-a11y/talkback-person`. This is UI presentation and test coverage only.
No policy, permission, persistence, send, or emergency-action code changed.

## Audit

| Surface | Finding and disposition |
|---|---|
| Home app, Phone, Messages, More | Already one Button action per row. Reliable positive counts already use plural resources and replace child text semantics; zero/null are silent. Verified through the production shell and `ShellUiMapper`, not only a hand-built UI state. |
| Home notice `›` | Already cleared. Added one polite live region for changing notice text. |
| Home Family symbol | Already decorative; one named button. No new Home emergency action. |
| More `›`, including Emergency | Already cleared. Emergency retains its single spoken description and existing event; tests record the event without launching a dialer. |
| Phone settings `›` | Cleared six row arrows. Pending-change box already clears its whole subtree and retains its action; unchanged. Added the existing “Phone settings” copy as a visible heading. |
| Sound / Ringtone row `›` | Cleared the arrow; title and current ringtone merge into one button. |
| Bluetooth discovery `›` | Already cleared, including the shared Pair a new device row. |
| Ringtone `✓` | Already cleared; selected state remains on the radio choice. |
| Brightness / Sound / Text size `−` and `+` | Already replaced by meaningful action labels, with button role and disabled state. |
| Shared Back links `‹` | Keep visible words and arrow; spoken Text excludes the leading decorative arrow. The containing Button owns the action. |
| Family and practice pictures / play, record, stop, send symbols | Canvas symbols and decorative initials already cleared. Added Button role to the tappable picture. Cleared duplicate visible-text semantics from the Messages-with action, whose existing spoken description includes its words. |
| `voice_sent` suffix `✓` | Visible copy unchanged; spoken Text drops only the resource's final decorative suffix, retaining heading and polite announcement. Configured names are not rewritten. |
| `ask_delivered`, `ask_added` suffix `✓` | Legacy request-history presentation retains visible copy; spoken Text omits the final decoration. Current production Requests has no tick strings. |
| Shared `TrustedComponents` `›` | Already cleared; no change needed. |
| `admin_request_sent`, `history_sent`, `admin_choice_selected` | PIN-only surfaces, outside this person's-side task. Audited references and left unchanged. No claim that all trusted-person tick semantics are fixed. |
| Titles / results | Existing TaskScreen, More, About, Wi-Fi, Bluetooth and Family headings retained. Production request results opt into one polite title region; sending controls opt in only while busy. |
| Wait/failure messages | Added polite regions to settings failures, Bluetooth discovery progress, Wi-Fi join/forget feedback, unavailable counts, and Family Messages failures. Existing voice recording/results/notices remain polite. No imperative announcement effect or recomposition timer added. |

The existing voice speaker checks touch exploration before automatic speech
(`AndroidVoice.speakText`); this slice does not change that adapter. Emulator
semantics are not an acoustic TalkBack exercise on the spare.

## Verification

Tests were committed before implementation (`2e03dfa1`). The initial emulator run
reproduced six semantic gaps: missing settings heading, ringtone arrow in merged
text, unannounced Home notice and production request result, and spoken ticks in
voice/legacy request results. The first test run also contained two fixture issues
(subsequently corrected); these are not counted as product regressions.

Private emulator: `emulator-5960`, `-avd pla525-ui-a35 -read-only`, API 35.
Both 5960/5961 ports were free before launch; `emu avd name` read back
`pla525-ui-a35`. Only explicitly targeted commands were used. No spare commands,
`connectedAndroidTest`, messages, or calls.

Commands run from `app/`, JDK 17 and the local Android SDK:

```sh
./gradlew :ui:assembleDebugAndroidTest :plainphone:testDebugUnitTest \
  :plainphone:assembleDebug :plainphone:assembleDebugAndroidTest \
  -PpreviewTestRunner=androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5960 shell am instrument -w -r \
  -e class org.plainphone.ui.PersonTalkBackTest,org.plainphone.ui.CommunicationCountsTest,org.plainphone.ui.UserScreensTest \
  org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5960 shell am instrument -w -r \
  -e class org.plainphone.shell.PersonShellTalkBackTest \
  org.plainphone.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :ui:verifyPaparazziDebug
```

Only Phone settings intentionally changes visually. Recorded exactly its affected
fixtures, then inspected all 35 changed images (100/130/150/180/200%, counts states
and absent/short/long waiting notice). The first full comparison found 31 failed
tests: 20 expected settings test cases plus the 11 established baseline failures.
No baseline golden was changed.

```sh
./gradlew :ui:recordPaparazziDebug \
  --tests 'org.plainphone.ui.CommunicationCountsScreenshotTest.settings*' \
  --tests 'org.plainphone.ui.PhoneSettingsWaitingScreenshotTest.*'
```

The emulator matrix uses 360×560 dp content, reserving 80 dp of the 360×640 dp
minimum for system bars. At 1.3, 1.5 and 2.0, it scrolls the last action into the
safe area and checks full bounds and text layout. This is conservative emulator
layout evidence, not a measurement of the spare's y=2280 navigation boundary.
The driver owns that final device readback and spoken traversal after review.

Final results:

| Check | Result |
|---|---|
| Debug app and both instrumentation APKs | Built successfully. Release version bump remains with the driver; the supplied code base retains preview-57 metadata. |
| `PersonTalkBackTest`, `CommunicationCountsTest`, `UserScreensTest` | **38/38 passed** on the private emulator, including 20 screen states × 1.3/1.5/2.0 (60 layout cases). |
| `PersonShellTalkBackTest` | **1/1 passed**, production shell/mapper with reliable positive counts and cleared zero/unavailable counts. |
| `:plainphone:testDebugUnitTest` | **332/332 passed**. |
| `:ui:verifyPaparazziDebug` | **1,363 passed; 11 established baseline failures** out of 1,374. No other failures. |
| Golden inspection | All 35 changed Phone settings images inspected; no changes to the 11 baseline images. |
| Spare | **Not installed or exercised by this lane.** After approval, Lead routes build 59 to the sole driver for install/readback and TalkBack traversal. |

Baseline failures: `BrandingScreenshotTest.attention` and `.turnOff` at 1.3/1.5/1.8
(six); `PreviewTurnOffScreenshotTest.maturityNotice` at 1.0/1.3/1.5/1.8/2.0 (five).
The full verify command therefore returns nonzero; it is not reported as all-green.

Built debug APK SHA-256:
`1c51626be09ca8c71e592051667d67587aa3d9874945c86b1f1d8828c1a1e52c`.
This is an author build, not the driver's numbered release artifact.

The regression test updates preserve their checks: receipt assertions now query
spoken words without ticks; the counts preference and Back action scroll into
view before tapping. No test was removed or disabled. Existing approved device
and emergency evidence stays with the release route; no live send/call was made.

