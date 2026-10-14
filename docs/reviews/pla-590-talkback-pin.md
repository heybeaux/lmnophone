# PIN settings TalkBack — slot 7g

Source: [PLA-590](/PLA/issues/PLA-590). Base `d1ed7cc0`.
Branch: `pla-a11y/talkback-pin`. UI presentation only; no PIN checking,
recovery issuance, permissions, policy, send, emergency or saved-data changes.

## Runtime audit

| Reachable screen / control | Disposition |
|---|---|
| Enter PIN, fresh PIN (including New recovery code), Change PIN, recovery entry | Already named from visible labels: PIN, Type it again, Recovery code. Existing password transformation supplies Password and masked EditableText. Tests preserve these; no raw secret copied into a spoken description. The new-PIN page visibly labels its first field PIN, so its accessible name stays PIN. |
| Wrong PIN, PIN wait, wrong recovery code, recovery wait, mismatched new PIN | Added one polite region on the existing message. No imperative announcement or recomposition timer. |
| Change PIN result | Added polite status, success and failure. |
| Menu / Needs you | Already one merged Button per row; headings already present. Decorative shared chevron already cleared. Tested supplied Needs you and production menu. |
| Apps on this phone | Each app remains one named Button. Move buttons already named the app; cleared their duplicate child text. Stable app keys retain focus through reordering; row and move buttons expose updated position. Messages/Phone remain without move/remove controls. |
| App details / What the app can use | Headings and Remove/Back already present. Unreadable permission status gets a polite region. |
| Add hidden app list | One named row per app; image is decorative. Unreadable list now polite. |
| App review / not-checked warning / confirm / remove / keep hidden | Existing headings, action roles, and merged buttons retained. Visible copy unchanged. |
| App added / removed result | Added a polite region on the result heading only, after confirming both routes in PinAccessActivity. |
| What can the app use? | Selected state already present; removed tick-bearing and duplicate child text from spoken choices. One Button names group and decision, with selected semantics. Check failure is polite. |
| Add permission failure / Remove save failure | Existing truthful message now polite; actions unchanged. |
| Requests / Not now / Remove / Keep it | Production uses RequestsScreen, not TrustedRequestsScreen. Existing status strings contain no ticks. Remove failure now polite. |
| Who gets requests: setup, edit, confirm, saved details, remove, read/save failure | Visible Name (optional) and Phone number already name the fields. Existing heading/buttons retained. Read/save failures now polite. No contact or recipient logic changes. |
| New recovery code: code shown | Visible grouped code unchanged. Spoken text separates characters and groups, once in the tree, only when focused; no automatic secret announcement. |
| Shared screen titles / Back | Already headings / Button actions. No changes needed. |
| More → Emergency | Production-path test retains the named clickable row; no launch/call performed. |
| Legacy TrustedRequestsScreen `admin_request_sent`, HistoryScreen `history_sent` | Not reachable from the installed production PIN route; left unchanged. |
| Who are you, trusted-person switching, supervised Play/Settings, monthly check, updater, wait settings, full legacy app details | Not reachable in this preview; left unchanged. |
| Family/practice and turn-off | Reachable but outside this bounded PIN-settings accessibility scope. No dedicated screen edits; unchanged accepted evidence applies. Shared Choice semantics cleanup is inherited where used. |

## Verification

Tests committed first at `fd96c2f1`. Baseline emulator reproduced eight
accessibility gaps. Three initial test assumptions were corrected after reading
the actual semantics (Requests from label, already-pinned action, internal
InputText versus accessible masked EditableText); they are not product defects.

Private emulator: `emulator-5940`, API 35, `-avd pla525-ui-a35 -read-only
-no-snapshot`. Ports 5940/5941 checked free first; `emu avd name` read back
`pla525-ui-a35`. Test-only display override: 1080×2400 pixels, density 480.
No spare commands, `connectedAndroidTest`, messages, calls or real data.

Large-text matrix uses Android FontScale override at 1.3, 1.5 and 2.0, with
360×560 dp content reserving 80 dp of the 360×640 minimum for bars. It checks
text layout, scroll reachability and full final-action bounds. Emulator window
bounds below y=2280 are not a physical-spare measurement; that readback and
spoken traversal belong to the sole driver after review.

The initial app instrumentation invocation used the default counts-probe runner
and crashed before tests. The repeat uses the existing
`-PpreviewTestRunner=androidx.test.runner.AndroidJUnitRunner` override.


Commands (from `app/`, JDK 17):

```sh
./gradlew :ui:assembleDebugAndroidTest :plainphone:testDebugUnitTest \
  :plainphone:assembleDebug :plainphone:assembleDebugAndroidTest \
  -PpreviewTestRunner=androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5940 shell am instrument -w \
  -e class org.plainphone.ui.PinSettingsTalkBackTest,org.plainphone.ui.TrustedScreensTest,org.plainphone.ui.RecoveryCodeTest \
  org.plainphone.ui.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5940 shell am instrument -w \
  -e class org.plainphone.PinSettingsProductionTalkBackTest \
  org.plainphone.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :ui:verifyPaparazziDebug
./gradlew :ui:verifyPaparazziDebug \
  --tests '*PreviewAppsScreenshotTest*' --tests '*AppPermissionsScreenshotTest*' \
  --tests '*ChangePinScreenshotTest*' --tests '*RecoveryPreviewScreenshotTest*' \
  --tests '*RequestsScreenshotTest*' --tests '*RequestRemovalScreenshotTest*' \
  --tests '*NeedsYouScreenshotTest*' --tests '*PhoneNameScreenshotTest*'
```

Layout assertions compare actual line spans with the rounded text box (one pixel
rounding tolerance), and reject vertical overflow and ellipsis. Using the raw
paragraph right edge falsely flags centered text; that test-oracle mistake was
corrected before the final run. No product check was disabled and no golden
image was recorded or modified.

| Check | Result |
|---|---|
| Final focused PIN semantics | 13/13 passed, including 31 screen states × 1.3/1.5/2.0 = 93 layout cases. Final actions end at emulator window y=1792, inside the safe content area and below y=2280. |
| Existing trusted/recovery regressions | 14/14 passed in the preceding 26-test run. Final change only opts added/removed titles into existing live-region support; final focused tests cover that delta. |
| Production mapper / actual activity AdminContext / More Emergency | 2/2 passed on private emulator. No device effects. |
| App JVM tests | 338/338 passed. |
| Debug APK and both test APKs | Built; installed only on private emulator for instrumentation. |
| Full Paparazzi | 1,398 passed, 11 established baseline failures, 1,409 total. Nonzero result, not all-green. |
| Final focused Paparazzi | 212/212 passed across eight changed-path classes. |
| Added/removed title delta Paparazzi | 2/2 parameterized tests passed; every fixture in TrustedScreensScreenshotTest unchanged. |
| Physical spare | Not installed or exercised by this lane. Driver owns numbered build, install/readback and acoustic TalkBack traversal after the existing focused review. |

The eleven baseline failures are Branding attention/turnOff at 1.3, 1.5 and 1.8,
and PreviewTurnOff maturityNotice at 1.0, 1.3, 1.5, 1.8 and 2.0.

Debug APK SHA-256:
`6270e50da5c5b50e63db7601ff72f931c250e7c912402a483ddcd34a3821f2c0`.
This author artifact retains base version metadata; it is not the driver's
numbered release. Semantics evidence establishes the accessibility tree and
masked values, not acoustic TalkBack behavior on the spare.

Evidence: [test logs and bounds](pla-590-evidence/). The added/removed result assertion was also observed failing before its fix (`result-red.txt`).
