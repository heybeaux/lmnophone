# Preview 16 author verification

[PLA-384](/PLA/issues/PLA-384). Own branch `release/preview-16` from preview 15
`f78f8ca5`; tests-first `05163d14`; implementation `818632ee`.
Only retained API 35 AVD `pla363-policy-a35`, `emulator-5880`, was driven.
The Lead owns the single opposite-model review and physical spare.

## Built

`:device:test :plainphone:test :plainphone:assembleDebug` passed. 655 device tests
per debug/release variant; 161 app tests per debug/release and four negative-control
variants. No failures/errors/skips or unrelated baseline failures. Existing compiler
warnings remain. All nine copied gate tests pass; comparison confirms only version
strings differ from preview 15. No UI, module interface or policy permission changed.
This is a scheduling fix under the existing device-lockdown boot/readback rules.

Focused tests: 23 tests, two expected failures before grouped skipping was enabled,
then all pass (`group-red.log`, `group-green.log`). The red run retained the old
single-only guard after adding the list transport and callback parameters. Tests
cover all unchanged packages with fresh repeated reads, no Home refresh, retained
pass and capture; changed/throwing second package; ADDED/REMOVED/REPLACED; one event
received before snapshot; locked, retry pending, missing coverage and empty group.
Existing single-event and captureAfterPublished tests remain passing.

## Installed

Build **15 → 16**, in place, **2026-10-12T03:05:43Z**. Readback: versionCode `16`,
`0.1.0-preview-16`, testOnly retained, same application ID `org.plainphone`.
First-install time remains `2026-10-11 14:05:10`. No uninstall/reset/reprovision.

Built APK, pulled installed APK and decompressed uploaded APK all match SHA-256:
`1cbe9a72bf4f02fb2b298284a08c730f1fdd036386587c72140fc3d3e006fa92`.
Unchanged signer SHA-256:
`99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
[Candidate APK, gzip](/api/attachments/f2a74fdf-f4c7-4aaa-b78f-3458adc080b9/content). See `artifact.json`.
The upload helper stalled after the server saved the attachment and artifact work
product. Both were read back through the API; the download was hash-checked before
stopping the stalled helper. No duplicate upload was attempted.

## Exercised

Unchanged gate **exit=0**: More → Emergency prefilled exactly `911`; Phone accepted
exactly `911`; Telecom had zero current calls on each route. Digits cleared and
Home restored. Both screenshots visually inspected; no personal data. No Call
button, call intent or SOS gesture used.

```sh
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py \
  app/tools/preview-16 emulator-5880 \
  app/plainphone/build/outputs/apk/debug/plainphone-debug.apk \
  docs/releases/preview-16/evidence
python3 docs/releases/preview-9/evidence/readback.py final emulator-5880
```

Use the Android SDK platform-tools on PATH. The existing root-host workaround for
this AVD's shell-user sdcard EFAULT was reused; app UID and gate assertions unchanged.
The second sample uses the same gate's `Gate.restart()` followed by a 20-second
observation period and PlainphoneBoot log capture, without injected events.

Natural emulator samples are evidence, **not a claimed startup speed improvement**:

- `boot-gate.txt`: **4 full unlocked passes**. A pre-snapshot `android` event replaces
  the user-unlocked pass before its capture; first snapshot is pass 3. Later
  `android` and Play services events also require full passes. No group occurs.
- `boot-sample2.txt`: **3 full unlocked passes**. First snapshot is user-unlocked
  pass 4, after Home. At `20:08:45.665`, the full-pass line lists the real group:
  `PACKAGE_CHANGED:com.google.android.gms` and
  `PACKAGE_CHANGED:com.google.android.partnersetup`. It falls back to full pass 6;
  these logs do not identify which eligibility/readback check failed.
  The earlier wellbeing event skips on pass 5. No natural all-eligible group skip
  occurred; that path is proven by the JVM tests only.

Final safety summary **exactly equals** pre-upgrade state: owner, lock-task list,
features/state, active restrictions, first-install time, counts preference, and all
hidden/disabled/suspended sets. Adb remains reachable. See the safety JSON files.

[Release notes](/PLA/cases/PLA-C65); [emulator gate and attachments](/PLA/cases/PLA-C66).
[Known limits](../notes.md) are retained. Review, spare installation, gate and two
natural unlock samples remain with the Lead. The spare targets (first Home about
+6.5 s, ≤2 full unlocked passes, user-unlocked first snapshot) are not yet measured.
