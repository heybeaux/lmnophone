# Read-only handover check verification

Implemented from reviewed base `5f7428ab` on `pla11/handover-check`.
The N1–N4 Play follow-ups are separate from the handover implementation.

## Built, installed and exercised

- **Built:** debug APK, SHA-256 `4b003d02ad7e3d4df60ab1bc323c4f2f3d89cbb38264a3d99f8d01f78aab2a8f`.
- **Installed:** private API 35 Play-image emulator, using `app/tools/preview-2/setup.sh`.
  Latest install readback: 2026-10-15 12:42:24 UTC (05:42:24 America/Vancouver).
  The author branch retains versionCode 63 / `0.1.0-preview-63`; this is an emulator
  artifact, not the next numbered spare-device release. The assigned driver owns numbering and install.
- **Exercised:** `HandoverRuntimeTest` passed on the actual PIN-settings route in 10.836 seconds.
  Generated test credentials stayed on the private emulator and were never printed or captured.
  The screen required the existing PIN entry, then opened without another PIN. Native Tab and Enter
  activated Done and returned to PIN settings without another prompt.
- **Physical device:** not installed or exercised by this author.

| Checked item | App readback |
|---|---|
| LMNOPhone is in charge | OK |
| Only approved apps open | OK |
| Emergency calling | OK |
| USB debugging | Needs the helper (`adb_enabled = 1`) |
| Wireless debugging | OK (`adb_wifi_enabled = 0`) |

The screen reports **Not ready: 1 item needs the helper**. The six pending gates remain visible.
[Top of the installed page](emulator-top.png) and [scrolled page](emulator-bottom.png) were inspected
for readability and personal data before saving. Screenshot capture temporarily cleared FLAG_SECURE
only on this read-only test page; credential screens retained it. No dialer was opened and no call was placed.

The real accessibility tree confirms a heading title, one spoken stop per checked item including its state,
no actions on result rows, no spoken decorative glyphs, and a keyboard-operable Done action.
The page is tested at 1.0, 1.3, 1.5, 1.8 and 2.0 text in 12 inspected Paparazzi captures;
the tall 2.0 captures show the full body and all pending checks. Six additional inspected captures
cover the changed last-opening and singular used-opening copy at 1.0 and 2.0.

## Host verification

Tests precede implementation in checkpoints `69e82f70`, `78c1bb54` and `477e3baa`.
The first two also failed compilation against the absent production entry points before implementation.
The OS-floor correction has its own regression checkpoint `7017d106`.

| Suite | Result |
|---|---|
| `:policy:test` | 694 passed |
| `:device:testDebugUnitTest` | 1,232 passed |
| `:plainphone:testDebugUnitTest` | 358 passed |
| `:ui:testDebugUnitTest` | 1,447 passed |
| `:ui:testReleaseUnitTest` | 1,447 passed |
| `:ui:verifyPaparazziDebug` | 1,447 tests, exactly 11 established baseline failures |
| `:plainphone:assembleDebug` | Passed |
| `openspec validate --all` | 6 passed |

The unchanged screenshot failures are listed in [paparazzi-baseline.txt](paparazzi-baseline.txt).
No existing golden was replaced to hide a failure. The new handover and opening captures pass verification.

`HandoverCheckTest` covers all 243 combinations of five nullable facts and proves that none marks Ready.
`HandoverReaderTest` covers missing/throwing reads, extra/missing lock-task entries, visible hidden apps,
disabled/hidden/suspended emergency members, failed resolution and zero device writes. Its thrown-reader
case produces COULD_NOT_CHECK; the production mapper tests verify every item and every state, including
COULD_NOT_CHECK. The mixed-result screenshots render this as **Couldn't check**.

## Read-only boundary and platform evidence

The production request runs on the existing policy dispatcher. It reads fresh preview-plan facts,
DeviceOwnerReader, DevicePolicyPort readback, the live OS emergency floor and global debugging settings.
It never calls apply/repair, any DPM setter, any Settings write, a network operation or an activity launch.
The existing Boolean policy readback retains its prior behavior; `readBackOrNull` adds diagnostics only.

Two emulator findings were corrected before handoff:

- The preview's keep-visible exemptions include a factory-disabled Home handler, which is not an emergency
  component. Emergency readiness uses `osFloorRead` and rejects unreadable roles or disabled actual floor members.
- Generic keyguard emergency resolution has multiple valid system candidates on this image. The check uses
  EmergencyGate's candidate set, while requiring a direct system resolver for More's actual emergency action.
  Hidden and disabled manufacturer candidates remain included in the checks. This is resolve-only evidence,
  not proof that an emergency call can complete.

Android's [Settings.Global API](https://developer.android.com/reference/android/provider/Settings.Global)
documents USB debugging and the throwing `getInt` overload. The wireless key is `adb_wifi_enabled`,
`ADB_WIFI_ENABLED` in the SDK's `android/provider/Settings.java` source (hidden from the public API).
The local SDK source and [AOSP Settings.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/Settings.java)
identify that key; unreadable or missing values are never defaulted to off.
`app/emergencygate/src/main/kotlin/org/plainphone/emergencygate/EmergencyGate.kt` documents the same
system-only More/keyguard queries and aliases. The private emulator reproduced both resolution sets.

## Reproduction

Use a new, private API 35 AVD with no accounts. Build from `app/`, run the preview-2 setup script with
an explicitly selected emulator target, and build the instrumentation APK using
`-PpreviewTestRunner=androidx.test.runner.AndroidJUnitRunner`.
Install both APKs with `adb -s "$PRIVATE_EMULATOR" install -r -t ...`, then run:

```sh
adb -s "$PRIVATE_EMULATOR" shell am instrument -w \
  -e class org.plainphone.HandoverRuntimeTest \
  org.plainphone.test/androidx.test.runner.AndroidJUnitRunner
```

The test requires fresh emulator credentials, checks the emulator hardware fence, waits for Home's normal
lock-task startup, enrolls a generated test credential, then authenticates through the real PIN page.
Do not use `connectedAndroidTest` or an unqualified adb command. The standalone fixture was replaced with
assertions on this actual route because normal Home recovery displaced its generic test activity.

The check is a partial snapshot. It cannot mark handover Ready, change settings, repair failures, certify
calling, or complete attestation, reset protection, browser role, auto-update, preinstall or test-text gates.
