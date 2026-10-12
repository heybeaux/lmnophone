# PLA-15 Q14: emergency gate on the merged `main`, debug qualification runs

Copyright 2026 LMNOPhone contributors. SPDX-License-Identifier: Apache-2.0

- **Tree:** `main` @ `a5e59eee`, which contains the D1 merge `f317c211` (`mvp/device` @ `dce36a94`).
- **Command:** `tools/emergency-gate.sh -s emulator-NNNN --no-build --out DIR`, after
  `./gradlew :emergencygate:assembleDebug :plainphone:assemble{Debug,CtlNoPhone,CtlNoDialerLockTask,CtlNoAlert,CtlNoAlertLockTask}`.
- **Emulators:** fresh private AVDs, `google_apis` arm64 userdebug, run with `-read-only`: API 35 (`api35/`) and API 36 (`api36/`).
  2026-10-12, 13:32–13:46 PDT. No spare-phone contact.
- **Result, both API levels:** `RESULT QUALIFIED EXCEPT alert-shown INCONCLUSIVE` (exit 5). The debug build passes every
  covered check. All four negative-control builds are DETECTED. The two summary tables are identical.
- **Cleanup on both:** no live call, test number 7357 removed, PIN cleared, Device Owner removed.
- The only number dialled is the test-mode emergency number 7357. No real emergency call, no SOS gesture.

What this does **not** show:
- `alert-shown` is INCONCLUSIVE, because the emulator cannot inject a cell broadcast. A release still needs the board's
  build-scoped exception or an alert observed on the target phone.
- These are debug qualification runs. The release build is not covered (adb is off there, D3).
- NOT_COVERED on this tree: `sos`, `setup`, `pin-lockout`, `supervised-session`, `updater`, `turn-off`.
- `more-emergency` is NOT_BUILT on `main`. The More → "Emergency 9-1-1" row exists only on the preview line
  (`release/preview-*`, spare screenshots in its preview evidence). It is not merged to `main` yet.
