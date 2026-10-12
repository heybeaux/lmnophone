# Preview 11 on the spare: install, readback, unlock timing (PLA-369)

Driver: the Lead, sole spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15). It has no working SIM. Call was never pressed.

## Artifact
- Gunzipped PLA-368 attachment `cf89f831…`: APK SHA-256 `9ec7321905ddf09b5638acee03f19c8fba06d6473760529d8414dc0977ffaa2f`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 10), versionCode 11, `0.1.0-preview-11`, testOnly.

## Install
- `upgrade-check.sh` (`install -r -t`) installed at 2026-10-12T00:06:07Z (`lastUpdateTime`), installer `Success`.
- **Gate attempt 1: `exit=1`** ([log](upgrade-check-attempt1.txt)). The install, restart, Home-after-readback, version, owner and LOCKED checks all passed. The run then failed at "find non-call tap target ^More$". The no-PIN keyguard had come back before the Home press (the screen timed out), and the UI dump held only SystemUI. This is a gate timing flake, not an app fault. The gate tool is unchanged since preview 10, which did not hit it.
- **Gate attempt 2: `exit=0`** (`SKIP_INSTALL=1`, fresh restart, [log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)).
- Before: versionCode 10, Device Owner `org.plainphone/.PlainphoneAdminReceiver`, LOCKED, `firstInstallTime=2026-10-09 10:26:07`.
- After: versionCode 11, Device Owner kept, LOCKED, `firstInstallTime` unchanged, `font_scale=1.0`.
- Safety ([after restart](safety-after-restart.json), preview 9's `spare_safety.py` `summary()`): every field is identical to build 10's after-restart record. That covers the owner, restriction names and sections, lock-task packages and features, LOCKED, `firstInstallTime`, the count switch, and package flags (hidden 42, suspended 0, disabled 116, same list hashes, 545 packages read). `no_debugging_features` is absent and adb is reachable.

## Unlock → Home after a restart (two samples, [sample.sh](sample.sh))
"Unlock" is `uc_finish_user_unlocked`; the spare has no PIN. Times are relative to unlock.

| | Build 11 sample 1 ([log](reboot-1.txt)) | Build 11 sample 2 ([log](reboot-2.txt)) | Build 10 (two samples) |
|---|---|---|---|
| Locked `start` pass ends | −3.1 s (1.1 s run) | −3.2 s (1.2 s run) | −4.0 / −3.2 s |
| `reapply reason=user-unlocked` ends | +6.6 s (4.0 s run) | +5.3 s (3.2 s run) | +4.2 / +4.5 s (3.6 / 3.8 s run) |
| `home shown lockTask=LOCKED` | **+7.4 s** | **+6.1 s** | +5.2 / +6.4 s |
| HIDES write + read + verify | 0.26 + 0.43 + 0.44 = **1.13 s** | 0.15 + 0.50 + 0.47 = **1.13 s** | 1.4–1.9 s |
| `livePackages` / `input` | 1.03 / 1.26 s | 0.64 / 0.77 s | 0.51 / 0.76 s (sample 1) |

- What build 11 changed: HIDES work in the user-unlocked pass fell from 1.4–1.9 s to 1.13 s in both samples, as intended.
- What the user sees: **no measured improvement.** Home came at +7.4 s and +6.1 s, within or above build 10's +5.2 / +6.4 s. Other stages vary from run to run more than the HIDES saving. In sample 1, `livePackages` took 1.0 s, against about 0.5 s on build 10. Two samples cannot separate the saving from that noise.
- Remaining cost: in the HIDES readback, the full-state shown-package reads (0.12–0.28 s) and the guard's shown reads (0.2–0.3 s per phase) remain. Verify still re-reads everything after the write pass.
