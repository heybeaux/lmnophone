# PLA-269 finding 1: Keyguard first, AVD evidence on the final APK

APK: `mvp/device` @ `7ea150b` (debug, SHA-256 `ff4193724f5d19c7efe12ee84b00397e3b6eabeb0e215fab06735ec70811442e`),
installed in place as Device Owner on own AVD `pla242-a35` (emulator-5824), API 35 `google_apis` arm64,
emulator-only PIN 1234. No call was placed. No physical device was used.

Check: `app/plainphone/check/first-unlock-containment.sh` (DROP `no_safe_boot` + features 16; `KEEP_LIST=1`
keeps the lock-task list so membership, including `org.plainphone`, survives; reboot; stimulus while
`RUNNING_LOCKED`; PIN; 12 samples; centre tap).

| Run | Membership | Before PIN | After PIN | Result |
|---|---|---|---|---|
| `control-bbb29fe-kept` (old APK, `if_whitelisted`) | kept | `keyguard=false`, `LOCKED`, `LOCKSCREEN -> GONE` at boot | n/a | **bug reproduced** |
| `keyguard-kept` | kept | 40 samples `keyguard=true`, `LOCKED`, flags 119 | `LOCKED`, top `MainActivity` | PASS |
| `keyguard` | lost | 40 samples `keyguard=true`, `LOCKED` | `LOCKED`, top `MainActivity` | PASS |
| `explicit-kept` / `explicit` | kept / lost | `keyguard=true`, `LOCKED` | Settings never top; `BlockedAppActivity` | PASS / PASS |
| `view-kept` / `view` | kept / lost | `keyguard=true`, `LOCKED` | Chrome never top | PASS / PASS |
| `notification-kept` / `notification` | kept / lost | `keyguard=true`, `LOCKED` | shade tap does not reach Settings | PASS / PASS |
| `emergency-kept` | kept | bouncer "Emergency" opens safetyhub `DialPadActivity`, `keyguard=true`, `LOCKED`, `RUNNING_LOCKED` | n/a | PASS (no number dialled) |

`*-logcat.txt` holds keyguard transitions and re-apply runs. On the final APK the only transition to
`GONE` is `PRIMARY_BOUNCER -> GONE` at PIN entry. Home enters `LOCKED` before unlock through its own
`startLockTask()` (manifest `lockTaskMode="normal"`), so explicit entry keeps the PLA-242 first-unlock
containment.

Not run on the AVD: features that never read back (no hook can make `setLockTaskFeatures` fail on a real
system). That path is covered on the JVM: `KeyguardFirstTest`, `KeyguardFirstEntryTest`, and the
Verifier probe `featureReadbackMustPrecedeNewHomeMembership`. One emulator image (API 35) only.
