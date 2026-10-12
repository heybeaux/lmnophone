# Preview-4 gate correction, Lead review round 2

Only gate tooling and notes changed. The APK and accepted app checks carry
forward. The notes-only base `21416e8` was merged without rebasing pushed history.
Application-diff command (empty):

```sh
git diff 354bcc1 HEAD -- app ':!app/tools'
```

APK SHA-256 remains
`cbf8ad4a80f7cbb1a0eb3eb63b09a4f1d53e1b36de0a55e783db8fa31f4a5232`.

## Actual checks

Owned API 35 AVD `pla306-policy-a35`, `emulator-5860`, same installed preview-4
and provisioning as the accepted initial run. No spare/device I/O. Reproduce
from the repo root with the SDK's adb on PATH:

```sh
SKIP_INSTALL=1 bash app/tools/preview-4/upgrade-check.sh emulator-5860 \
  app/plainphone/build/outputs/apk/debug/plainphone-debug.apk <positive-output-dir>
```

The positive log `upgrade-check.txt` contains PASS assertions for:
- version 4, testOnly, Device Owner and lock task LOCKED;
- Home after full read-back following restart;
- More > Emergency's actual prefill exactly `911`, then clear/read empty;
- Phone clear/read empty, tap nine/one/one, then exactly `911`;
- exactly one complete Telecom current-call section, empty after each route.

The log ends `exit=0`; the invoking shell independently recorded `shell_exit=0`.
Both new PNGs were visually inspected: only emergency digits, no contact or
communication content. No call key, call intent or Call-button tap was sent.

**Empty field detail:** this Google Phone version's numeric EditText exposes its
empty hint, `Phone Number`, as the XML text. `dialtext()` returns the raw text
of exactly one matching node; it never strips or normalizes a populated field.
Only the empty-field check recognizes the exact observed English hint or an
empty string. Unknown/localized hints fail closed. The 911 assertions accept
only literal `911`, so `911-911` cannot pass. The first corrected attempt stopped
being useful at this hint mismatch and was terminated; it is not a passing gate.
The recorded positive log is the complete rerun after the explicit hint fix.

## Negative control

```sh
SKIP_INSTALL=1 PLAINPHONE_CHECK_SELF_TEST=1 EXPECT_DIAL=912 \
  LOG=negative-control.txt bash app/tools/preview-4/upgrade-check.sh \
  emulator-5860 app/plainphone/build/outputs/apk/debug/plainphone-debug.apk \
  <negative-output-dir>
```

This changes **only the expected value**, never the input: the script always
types nine/one/one, and Emergency still prefills 911. The wrong expectation is
permitted only on an owned emulator with the explicit self-test opt-in. It is
rejected for the spare. The actual recorded failure is
`FAIL: More > Emergency prefill exactly 912`, with `exit=1` and `shell_exit=1`.
This proves failure propagation through the actual wrapper and tee.

## Tool regression tests

`python3 app/tools/preview-4/test_upgrade_check.py`: seven tests pass. Written
first (initial missing-module red checkpoint `5bf7202`; hint test also shown red
before its implementation). Covers zero/duplicate/malformed dial fields,
non-normalized duplicate digits, exact empty hint handling, current calls versus
history, call controls and missing taps, device/negative-control guards, and a
failing fake adb propagated through the real shell wrapper and tee.

## Driver use

The sole assigned spare driver may use `PLAINPHONE_SPARE_DRIVER=1` with
`PLAINPHONE_SPARE_SERIAL=<spare-serial>` set to the approved spare serial. All other physical serials and reserved emulators
5590/5604/5742 are refused. This author used emulator-5860 only. See the draft
notes' TODO(spare) and Recovery sections. Lead review must complete before the
driver handoff; the target delivery time imposes no additional hold.
