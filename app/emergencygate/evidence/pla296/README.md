# PLA-296 fix round (PLA-298): emergency gate, debug qualification runs

Copyright 2026 LMNOPhone contributors. SPDX-License-Identifier: Apache-2.0

**These are debug qualification runs, not release results.** Emulator results are not proof that a real
emergency call gets through. Emulator serials are written `<emulator>`.

| Directory | Emulator | Image |
| --- | --- | --- |
| `api35/` | AVD pla140-a35 | google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug, API 35 |
| `api36/` | AVD pla231-a36 | google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.F3/13894323:userdebug, API 36 |

Each directory has one file per build (`debug.txt`, `ctl*.txt`), `summary.txt` (the gate's table, per-build
outcome, RESULT line and exit code), `gate-log.txt` (the gate's stderr) and `cleanup.txt` (the checked end
state). The `Raw lines: /tmp/pla298-runs/…` line in `summary.txt` is the run's original output directory;
these files are its redacted copy.

Command, per emulator (both in parallel, started 2026-10-11 17:05 UTC, APKs built once from `f6466c4`):

    tools/emergency-gate.sh -s <emulator> --no-build

Gate APK SHA-256 `72e0b7f6…23a2`; build APK hashes are in the header of each result file and are the same on
both emulators.

## Result

`RESULT QUALIFIED EXCEPT alert-shown INCONCLUSIVE` (exit 5) on both. All four controls DETECTED
(`ctlNoPhone`, `ctlNoDialerLockTask`, `ctlNoAlert`, `ctlNoAlertLockTask`). `alert-shown` still needs the board's
decision at release.

- **R1** (missing foreground sample) and **R4** (alert launch errors): the controls are still DETECTED with the
  stricter classification. Each control's alert launch FAIL is the established unavailable component
  (`unavailable: hidden (Error type 3, exit 1) … componentState=0`), not an ERROR.
- **R2** (call ended): every result file, on both emulators, has the new end-of-call evidence on the Telecom
  check, `afterHangUp=mCallState=0 ended(hold=0,0,0,0,0,0 mCalls= after=0)`, and the host line
  `# after telecom-emergency-call: mCallState=0 held 3 s, Telecom mCalls none live, modem call list empty,
  mCallState=0 after both`. No call came back, and no run stopped on a late call.
- **R3** (cleanup read back): `cleanup.txt` is `ok=1` with every item confirmed by a successful read:
  call ended (same evidence as above), test number 7357 absent, PIN cleared, Device Owner absent. Each build's
  removal read back `no owners` and `mLockTaskModeState=NONE`.

The bound on "ended" (written at `GateJudge.callEnded` and `tools/emergency-gate.sh` `call_ended`): an
observation over about 3.5 s, repeated before each call-capable step and at cleanup, not proof that the modem
never brings a call back later.

Coverage, controls and calls are as in [pla245](../pla245/README.md). Release behaviour is untested (PLA-296 D3).
