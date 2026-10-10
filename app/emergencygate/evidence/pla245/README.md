# PLA-245: emergency gate, debug qualification runs

Copyright 2026 Plainphone contributors. SPDX-License-Identifier: Apache-2.0

**These are debug qualification runs, not release results.** Every result file starts with the run
label, the APK SHA-256, the variant, the control and the user restrictions read from the emulator after
provisioning. Emulator results are not proof that a real emergency call gets through.

| File | Emulator | Image |
| --- | --- | --- |
| `api35/` | AVD pla140-a35 (emulator-5770) | google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug, API 35 |
| `api36/` | AVD pla231-a36 (emulator-5800) | google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.F3/13894323:userdebug, API 36 |

Each directory has one file per build (`debug.txt`, `ctl*.txt`), `summary.txt` (the gate's table,
per-build outcome and RESULT line) and `cleanup.txt` (the checked end state).

Command, per emulator (both run in parallel, APKs built once from the same commit first):

    tools/emergency-gate.sh -s emulator-5770 --no-build
    tools/emergency-gate.sh -s emulator-5800 --no-build

The `+dirty` on the commit line is the then-uncommitted host test file `tools/tests/test_emergency_gate_host.py`
(committed next as `604a5ec`); no gate, app or script source differed from the commit.

## What a qualification run covers

- Real build (debug): Home → Phone, Messages → Phone, the keyguard's Emergency call (PIN set, and before
  the first unlock after a reboot), the held checking screen's Phone (`screen=checking`), the Telecom call
  to the test-mode number 7357, and "Plainphone can't start" (a root loop kills its process; the keyguard's
  Emergency call must still reach the emergency dial pad). Every route must reach the activity its intent
  resolves to, stay in front and show all ten dial keys.
- `alert-shown` is INCONCLUSIVE: these images cannot inject a cell broadcast alert. Release still needs the
  board's decision on it.
- Declared NOT_COVERED, never PASS: `sos` (by design: the power-button gesture is never triggered by a tool,
  AGENTS.md rule 3; Q14 real-device check) and the states not built yet: setup, PIN lockout, supervised
  session, updater and turn-off. `more-emergency` is NOT_BUILT, declared by the build's manifest.
- Controls: `ctlNoPhone` and `ctlNoDialerLockTask` must fail `home-phone` and `app-home-phone`;
  `ctlNoAlert` must fail `alert-display-launch` (`alert-display-ready` may also fail); `ctlNoAlertLockTask`
  must fail both alert checks. Every other check must behave as in the real build.
- **Surrogate:** the spec's "alert-display component disabled" control is covered by `ctlNoAlert`, which
  hides the cell broadcast packages (a Device Owner cannot disable another app's component). Like the real
  build, it is unverified for `alert-shown`.

## Calls

The only number dialed is 7357, registered with `su 0 cmd phone emergency-number-test-mode -a 7357` and
checked in the `-p` list before dialing; the APK checks the allowlist, `ro.kernel.qemu`/`ro.boot.qemu` and
the list again. Every result file has `# after telecom-emergency-call: mCallState=0`, and `cleanup.txt` shows
the end state (no call, test list back to `[112, 911]`, no PIN, no owner).

## Release artifact (D3)

Not tested on the release artifact. The release build applies `DISALLOW_DEBUGGING_FEATURES` (the debug
build withholds it: `withheld=[DISALLOW_FACTORY_RESET, DISALLOW_DEBUGGING_FEATURES]` in each re-apply line),
which turns adb off, and the gate is an adb-started instrumentation. No emulator-safe route was found:
the emulator console has no shell, UiAutomation is only given to an instrumentation started from the shell,
and changing the release build's restrictions is out of bounds. Release APK at this commit:
`plainphone-release-unsigned.apk` SHA-256 `499273002f08913b03913fb950f5272e3465de72f5713398c9f98e5164f67f96`.
"Release-artifact emergency check" is added to the real-device checks.
