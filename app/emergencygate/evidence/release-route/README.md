# PLA-299: emergency gate on the release APK through the emulator serial console (no adb)
Copyright 2026 LMNOPhone contributors. SPDX-License-Identifier: Apache-2.0

**Feasibility check only, not a release result.** One route (`home-phone`) and the `capabilities` check were run.
Emulator results are not proof that a real emergency call gets through. Emulator serials are written `<emulator>`.

## Setup

- Dedicated AVD `pla299-a35`, image `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug`, API 35.
- Started with the serial console on a host TCP socket (no window):

      emulator -avd pla299-a35 -port <port> -no-window -no-snapshot -no-audio -no-boot-anim \
               -shell-serial tcp::<serial-port>,server,nowait

  A host script connects to `127.0.0.1:<serial-port>`, sends one command line plus an `echo` end marker and reads
  until the marker comes back. Output comes back on the same socket; that is how result files leave the device.
- Release APK built from `ef92e489` (`:app:plainphone` release, non-debuggable; installed APK SHA-256 prefix
  `ab86908c275ac318`, versionName `0.1.0-dev`). Gate APK `emergencygate-debug.apk` (SHA-256 prefix
  `966a30a0cb77cdda`, same as the host build).
- `dpm set-device-owner org.plainphone/.PlainphoneAdminReceiver`; the release build applied its restrictions.

## Release state, read back over the serial console after the checks

- `settings get global adb_enabled` → `0`; `getprop init.svc.adbd` → `stopped`; the host shows `<emulator> offline`.
- `dpm list-owners` → `User 0: admin=org.plainphone/.PlainphoneAdminReceiver,DeviceOwner,Affiliated`.
- Effective restrictions include `no_debugging_features`, `no_factory_reset`, `no_safe_boot`,
  `no_install_unknown_sources_globally`, `no_usb_file_transfer`, `no_user_switch` (full list in the PLA-299
  comment). No restriction was changed by this check.

## What happened

| Step | Command (serial console) | Result |
| --- | --- | --- |
| Console identity | `id` | `uid=2000(shell) … context=u:r:shell:s0` |
| Gate as shell | `am instrument -w -e check capabilities org.plainphone.emergencygate/.EmergencyGate` | **refused**: `java.lang.SecurityException: Shell does not have permission to access user 0` (`UserController.handleIncomingUser` from `ActivityManagerService.startInstrumentation`): the `no_debugging_features` shell restriction |
| Root | `su 0 id` | `uid=0(root) … context=u:r:su:s0` |
| Gate as root, first try | `su 0 am instrument -w -e check capabilities …` | `INSTRUMENTATION_FAILED`; logcat `Unable to find instrumentation info`. The gate package was `installed=false` for user 0 (see limits) |
| Restore gate for user 0 | `su 0 pm install-existing --user 0 org.plainphone.emergencygate` | `installed=true` |
| Gate as root | `su 0 am instrument -w -e check capabilities …` | `GATE capabilities PASS control=none notBuilt=more-emergency ready=true` |
| Test number | `su 0 cmd phone emergency-number-test-mode -a 7357`, then `-p` | `[112, 7357, 911]` |
| Idle before | `dumpsys telephony.registry \| grep -m1 mCallState=` | `mCallState=0` |
| Route | `su 0 am instrument -w -e check home-phone … > hp.txt` | `GATE home-phone PASS com.google.android.dialer/com.android.dialer.main.impl.MainActivity lockTask=LOCKED` |
| Idle after | six reads 0.5 s apart; `dumpsys telecom` | `mCallState=0` ×6; `mCalls:` empty |
| Clear | `su 0 cmd phone emergency-number-test-mode -c`, then `-p` | `[112, 911]` (7357 absent) |

No call was placed by these checks (`home-phone` opens the phone app from Home; it does not dial). Only 7357 was
registered. The SOS gesture was never used.

## Verdict: feasible, with root

The serial console survives `no_debugging_features` (adb gone) and gives a shell. The gate cannot run as the
shell UID (`Shell does not have permission to access user 0`), but runs as root via `su 0` on a userdebug image:
`am instrument` (`capabilities` and the UI-driven `home-phone` passed; no UiAutomation refusal), `cmd phone
emergency-number-test-mode`, `dumpsys telephony.registry` and `dumpsys telecom` all worked, and results come back
over the serial socket. `tools/emergency-gate.sh` already runs `cmd phone` through `su 0`; a release run needs
`am instrument` and the reads through `su 0` too, and a serial transport instead of `adb shell`.

## Limits and open points

- **Gate APK staging.** The earlier (timed-out) run of this check sent the gate APK over the serial console
  (base64) and installed it as root **after** provisioning. It then read `installed=false` for user 0 and needed
  `pm install-existing --user 0`. The cause was not established (root install under the release restrictions, or
  the release policy). The release run should stage the gate APK **before** `set-device-owner`, as the brief says,
  and check `installed=true` for user 0 after provisioning.
- Root on a userdebug image is a different caller from the gate's debug runs (shell UID over adb). The release run
  needs to show that the controls are still detected with a root caller.
- Only `capabilities` and `home-phone` were run; no call-capable route, no controls, no cleanup pass.
- The release APK was signed locally for install by the earlier run; this is not a store-signed artifact.
