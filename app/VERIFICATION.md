# LMNOPhone MVP verification (PLA-15)

Copyright 2026 LMNOPhone contributors. SPDX-License-Identifier: Apache-2.0

Section 3 acceptance and the PLA-30 extensions, mapped to the rows of `docs/verification-matrix.md`.
This file is living: each run adds its evidence here.

- **Revision 1, 2026-10-12.** Tree: `main` @ `a5e59eee`, which contains the D1 merge `f317c211`.
- **Tags:** **[m]** committed on `main`. **[p]** only on the preview line (`release/preview-33` @ `b13e2a35`).
  **emu** = emulator. **spare** = the spare Moto (Android 15).
- **Status words:**
  - **pass**: the matrix outcome was observed on the MVP app.
  - **partial**: some of the outcome was observed; the gap is listed.
  - **open**: no MVP evidence yet (spike or unit evidence only).
- Acceptance runs never gate a spare install (Telegram3215). When a run finds a defect, it becomes a focused fix issue.

## Row map

| Test | Matrix row(s) | Status | MVP evidence | Gap still to run |
|---|---|---|---|---|
| **Q14** | essential → never obstruct; behavioural release gate | **partial** | **New this revision:** [m] `emergencygate/evidence/pla15/` (gate on `main` @ `a5e59eee`, API 35 + 36 emu: `QUALIFIED EXCEPT alert-shown INCONCLUSIVE`, all 4 controls DETECTED). Earlier: [m] `emergencygate/evidence/pla231`, `pla245`, `pla296`. [p] spare per preview: Phone and More → Emergency show 911, no call placed (`app/tools/preview-33/evidence/spare/`). | `alert-shown` INCONCLUSIVE (needs an alert seen on the target phone, or the board's build-scoped exception). Release build not run (adb is off there). NOT_COVERED: sos, setup, pin-lockout, supervised-session, updater, turn-off. `more-emergency` is NOT_BUILT on `main` and built only on the preview line. |
| **Q14 [device]** | real-device checks before handover; alert observed | **open** | [p] spare: 911 dial screens only. | Lock-screen Emergency on the spare needs a lock PIN (board direction). Alert display on the real device. |
| **Q6** | first frame; safe storage and boot; build inventory; OTA and unknown build | **partial** | [m] `device/evidence/pla190/boot-paths-api35.txt` (damaged journal revokes before unlock; lost list/restriction read back before Home); `pla233/*` (locked boot touches only restrictions, floor, never-hide guard); `pla269`, `pla311`, `pla324` (process kill, in-call stand-in); `pla132`, `pla167` (update refusals). [p] preview-2 restart rows; spare Home after reboot in lock task. | No build inventory for any model, so the unknown-build and OTA path is not run. "Both policy copies corrupt → safe policy" not run on a device. Crash-on-start build → recovery path not run. No API 36 runs. |
| **Q5** | escape restrictions; forged intents; preinstall sweep; handover check | **partial** | [m] `pla190` (`no_safe_boot` re-applied before unlock); `pla186` (restriction list). | Safe mode refused, OEM unlock greyed out and adb refused are not shown on an MVP build (the debug build withholds factory-reset and debugging restrictions). No forged-intent run. No preinstall sweep or handover check. |
| **Q1** | in-task blocking; own settings; permissions at approval; floor in lock task | **partial** | [m] `pla269`, `pla311`, `pla324` (Settings and Chrome never open by start, link or notification, also after a kill); `pla186`, `pla86` (permission choices written and read back). [p] Wi-Fi join without Settings (emu); spare: "Show nearby networks" only. | Calls, SMS, camera and Photos not run with BLOCK on in the MVP. No API 36 MVP run. Wi-Fi join not run on the spare. |
| **Q7** | turning off | **open** | [p] preview-24: 24-hour wait start/cancel (emu + spare). [p] preview-2: `remove-active-admin` brings every hidden app back (spare). | The real turn-off is not in a build. Resume after a kill at every step, and "Off" only when complete. |
| **Q8** | calls, texts, alerts; no call filtering | **partial** | [p] preview-4: Dialer and Messages hold their roles on the spare; injected calls and SMS seen on emu. | No ringing call on a carrier in lock task (spare SIM out of service). MMS, RCS and iPhone groups not tested. |
| **Q10** | reset protection (G1 on emu) | **open** | [m] `ResetProtectionCheck` exists; no run output. | Emulators throw `UnsupportedOperationException`, so the emu run can only prove fail-closed. Readback and wipe test belong to the board-approved procedure on the target phone. |
| **Q12** | supervised session; PIN; recovery code; protected state; helper resume | **partial** | [m] `pla190` scenario 1 (session package revoked at locked boot, test-hook session). [p] preview-17: PIN survives reboot (emu). | No real Play or Settings session. Crash-on-every-start reachability not measured against the custody limit. Lockout delay, protected-state repair and helper resume not run on a device. |
| **Q2** | updates and monthly check | **open** | Spike only (`spikes/device-moto` §Q2). | MVP unattended run (not a gate, runs in parallel). Monthly text, retry, 7-day line and PIN completion on a device. |
| **Q4** | classifier, link matrix, Wi-Fi sign-in, signed-in web leaks | **open** | Spike only (`spikes/emulator` §Q4, `spikes/wifi-signin`). | MVP links module on a device. Signed-in must-have app checks (after the owner signs in). |
| **Q13** | manufacturer extras | **open** | Spike only (`spikes/device-moto` A5). | Build inventory with a hide/keep/test decision per extra. |

## PLA-30 extensions (`add-copy-test-product-decisions` tasks 1.1–1.4)

| Task | Status | Evidence | Gap |
|---|---|---|---|
| 1.1 Q15, receiving texts while not the default SMS app | **open** | None. Nearest: [p] preview-4, READ_SMS allowlisted and granted (a different permission). | All of it, API 35 and 36. |
| 1.2 Q12 fault injection at every write boundary | **partial** | Unit tests with `SimulatedCrash` (`ProvenRunningClockTest`, `WaitingChangeControllerTest`, `StoreCommitProtocolTest`, `ModeSwitchFaultTest` and others); [m] `device/evidence/pla*/jvm-*` red/green logs. | No `kill` or `emu kill` runs on a device. N1–N5 and the PLA-35 cases are not traced to tests one by one. |
| 1.3 Mitigation B at every unhide call site | **partial** | [p] preview-2 `power-cut-unhide.txt` (emu + spare); [m] `pla233` (real flips, API 35); `ReadbackPolicyApplierTest`. | Not every call site; not 10 trials per site on API 35 and 36; unhide-to-flip gap not measured; battery guard not checked. |
| 1.4 Q10 with three accounts | **open** | None. | Runs with Q10 on the target phone. |

## Run order (highest risk first)

1. Q6 on `main`, API 35 and 36: both policy copies corrupt, crash-on-start build, and an API 36 boot-path run.
2. Mitigation B (1.3) at each unhide call site, 10 power-cut trials per site, with the unhide-to-flip gap measured.
3. Q5 on a release-restrictions build: safe mode, OEM unlock, adb refused, and the forged-intent set.
4. Q15 (1.1) emulator spike.
5. Q12 crash-on-start reachability report against the custody limit.

Q7, Q2, Q4 and Q13 need features or an inventory that are not built yet. They run when those slices land on the preview line.
