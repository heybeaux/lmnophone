# Preview-4 integration and emulator verification

Run date: 2026-10-11 UTC. Author: Policy Engineer; review owner: Plainphone Lead.
This is pre-device preparation for PLA-306. No physical phone was addressed.

## Source and artifact

- Branch: `release/preview-4`; exact base `ac2d56d1d6d515ef5d0e54bb162e4ea226fe46d2`.
- Build source: `354bcc109e985cb80ccb606c7fbebc072c8e3ed9`; later commits contain evidence, notes and the
  emulator check script only.
- Feature source: `feature/home-counts-host` at `6ba747d`.
- Cherry-picked all 22 counts commits from `ac544dd` through `6ba747d` in order,
  keeping the original test-first sequence. Skipped `5d4e66f` and `be90141`, whose
  calm-Home changes were already in preview-3.
- Two conflicts: MainActivity retained `runtime.refresh` and
  `LaunchedEffect(refresh)` from preview-3 while adding count lifecycle handling;
  build.gradle retained preview numbering and added the count instrumentation runner.
- Preview-3's HomePublisher, PolicyRuntime, preview plan, boot tests, :core,
  :policy and :links are byte-for-byte unchanged against `ac2d56d`.
  HomeScreen and all count sources match reviewed `6ba747d`.
- Application ID `org.plainphone`, versionCode **4**, versionName
  **0.1.0-preview-4**, `testOnly=true`.
- APK: `app/plainphone/build/outputs/apk/debug/plainphone-debug.apk`.
- APK SHA-256: `cbf8ad4a80f7cbb1a0eb3eb63b09a4f1d53e1b36de0a55e783db8fa31f4a5232`.
- Preview-3 APK SHA-256: `ec3e05bb28f1de9fa7858d42a8083849dada0a300e6edbcd54b135d329f720cb`. This is the saved
  `f33cd44` artifact, matching preview-3's release evidence.
- Both APKs pass `apksigner verify`; certificate SHA-256:
  `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
- Exact APK is also attached to PLA-306 as `preview-4.apk.gz` (lossless gzip,
  used to meet Paperclip's 10 MB attachment limit). Decompress and verify the
  **APK** digest above before installation; the gzip digest is different.

## Offline tests and build

JDK 17, installed SDK 36. From `app/`:

```sh
./gradlew --offline :plainphone:testDebugUnitTest :ui:testDebugUnitTest :ui:verifyPaparazziDebug :device:testDebugUnitTest --continue
./gradlew --offline :plainphone:assembleDebug
```

| Suite | Passed / total | Result |
|---|---:|---|
| :plainphone debug JVM | 83 / 83 | Pass, including count host tests and preview-3 HomePublisher tests |
| :ui debug JVM | 101 / 101 | Pass |
| :ui Paparazzi | verified | Pass; no re-record and no changed goldens |
| :device debug JVM | 488 / 493 | Only the five known baseline failures below |
| APK assembleDebug | — | Pass |

The combined test command exits nonzero for those five device failures. It is
not reported as an all-green run. Raw per-suite totals and exact failing test
names are in [test-totals.json](test-totals.json). The failures are:

- `Pla241ReviewTest.throwingJournalMustStillRepairEmergencyFloor`
- `Pla241ReviewTest.lockedBootFlipFailureMustReachFirstUnlockedHome`
- `Pla241ReviewTest.repairMustPreserveFlipFailureFromFirstAttempt`
- `Pla241ReviewTest.enabledStateChangingBeforeFinalReadbackMustBlockSetup`
- `Pla241ReviewTest.failedPlanMustStillRestoreFixedRestrictions`

These are the unchanged preview-3 baseline listed in its notes, not new count
failures. No other test failed. This candidate does not incorporate their later
fixes from the device branch.

## Emulator setup and in-place upgrade

Fresh owned AVD `pla306-policy-a35`, serial `emulator-5860`, Google APIs API 35,
1080×2400 at density 420. App versions, fingerprint and policy read-back are in
[device-metadata.json](emulator/device-metadata.json). No account was added.
No commands addressed reserved emulators 5590, 5604 or 5742.

1. Install the verified preview-3 APK and provision it through
   `app/tools/preview-2/setup.sh emulator-5860 <preview-3.apk>`.
   [setup-preview3.txt](emulator/setup-preview3.txt) records Device Owner, Home,
   starter-list plan and LOCKED read-back.
2. Run `app/tools/preview-4/upgrade-check.sh emulator-5860 <preview-4.apk> <out-dir>`.
   This preparation wrapper refuses physical and reserved serials. It derives
   from the existing preview-3 check and only opens dialers, never calls.
3. `install -r -t` ran at **05:29:47–05:29:48 UTC** with no uninstall, data clear,
   reset or reprovision during upgrade, and no `--restrict-permissions`.
4. Read-back: version **3 → 4**, same Device Owner, `TEST_ONLY`, lock task
   **LOCKED**, Home **org.plainphone/.MainActivity**. Starter entries remain
   Camera, Maps, Messages and Phone.
5. After restart, full successful read-back at emulator time **22:30:22.970**,
   first Home at **22:30:23.158**: **188 ms later**, in lock task. The earlier
   locked run did not publish Home. This is a no-PIN AVD; it does not substitute
   for the spare's D8(a) restart check.
6. More > Emergency and Home > Phone each reached a dial pad showing **911**.
   Both read-backs show an empty active-call list. No call button or call key was
   used. The two screenshots are distinct captures.

See [upgrade-check.txt](emulator/upgrade-check.txt). Emulator log times use the
AVD's America/Los_Angeles clock; the script's install times are UTC.

## Counts and permission read-back

[counts.json](emulator/counts.json) records the actual Home accessibility labels,
switch state, permission flags and Home PIDs. Only Home/Settings labels and
permission metadata are stored, never communication content or contact details.

- Upgrade default: switch unchecked; Phone and Messages labels have no numbers.
- After install: both permissions denied, with `RESTRICTION_INSTALLER_EXEMPT`.
- First switch on: both granted with `POLICY_FIXED` and installer exemption.
  Empty records show no number, not a visible zero.
- Console-only synthetic incoming call, then console cancellation: Phone **1**.
- Two console-only synthetic incoming SMS in the same conversation: Messages
  **2**, Phone remains **1**. No real communication was sent or received.
- With that positive baseline, off hides both numbers; on restores Phone **1**
  and Messages **2**. Home PID stays **942** throughout, including after off.
- Both permissions remain granted after off. The code and existing unit tests
  cover cancellation/unregister/no reads while off; PID alone is not proof of
  stopped reads.
- Font scale is read back as **2.0** for the 200% captures and restored to **1.0**
  afterward. Home remains usable with both numbers visible; Settings discloses
  coverage and retained permissions. See numbered PNGs.

The simulator operations use `adb -s emulator-5860 emu gsm call`, `emu gsm cancel`
and `emu sms send` with a fictional fixture address. These are emulator-console
injections, not telephony calls/texts. They exercise the emulator portion of D8;
they do not count as any D8(b) spare acceptance step.

## Image inspection and limits

Every new PNG was visually inspected before commit. Captures contain only this
fresh AVD, sample Sam/Jo labels, aggregate counts and the required emergency
digits. No account, contact, message body or fixture address is visible.
Package-change reasons in the boot log are redacted to `<pkg>`.
Before/after-install screenshots can share pixels because counts default off and
the clock minute is unchanged; each is captured by its own screencap invocation.

This run proves preview-3 → preview-4 integration on the recorded API 35 image.
The broader reviewed count provider fixtures and controlled scheduler tests are
carried in `docs/reviews/pla300/` and `pla302/`; their API 36/MMS results are not
new runs of this candidate. This run adds only console missed-call/SMS events,
not real MMS download, swipe, mark-read, retry, carrier voicemail or RCS evidence.
D8(a) spare results and D8(b) steps remain TODO in the draft notes. Counts stay
**in testing** until the tester supplies the required spare results.
