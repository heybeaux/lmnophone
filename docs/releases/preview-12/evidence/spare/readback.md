# Preview 12 on the spare: install, readback, unlock timing (PLA-369)

Driver: the Lead, sole spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review: PLA-370 document `lead-review` (approve).

## Artifact
- Gunzipped PLA-370 attachment `096eb723…`: APK SHA-256 `b8834d1a678dfa942895c13a27e7fc531b5f87b251d86a67a7f53d463c1a243b`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 11), versionCode 12, `0.1.0-preview-12`, testOnly.

## Install
- `app/tools/preview-12/upgrade-check.sh` (`install -r -t`) installed at **2026-10-12T00:47:01Z** (`lastUpdateTime`), with installer `Success`.
- **Gate: `exit=0` on the first attempt** ([log](upgrade-check.txt)). The keyguard re-dismiss added in this build removed the build 11 flake. Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)).
- Before: versionCode 11, Device Owner `org.plainphone/.PlainphoneAdminReceiver`, LOCKED, `firstInstallTime=2026-10-09 10:26:07`, count switch on.
- After: versionCode 12, Device Owner kept, LOCKED, `firstInstallTime` unchanged.
- Safety ([after restart](safety-after-restart.json), preview 9 `spare_safety.py` `summary()`): **identical to build 11 in every field**. That covers the owner, restrictions, lock-task packages and features, the count switch, and package flags (hidden 42, suspended 0, disabled 116, same list hashes, 545 packages read).

## Unlock → Home after a restart (two samples, [sample.sh](sample.sh))
"Unlock" is `uc_finish_user_unlocked`; the spare has no PIN. Times are relative to unlock.

| | Build 12 sample 1 ([log](reboot-1.txt)) | Build 12 sample 2 ([log](reboot-2.txt)) | Build 11 (two samples) |
|---|---|---|---|
| `user-unlocked` full pass ends | +5.0 s | +5.3 s | +6.6 / +5.3 s |
| `locked-boot` | **skipped, covered by pass 4** (+5.0 s) | **skipped, covered by pass 4** (+5.3 s) | full pass +7.9 / +7.5 s |
| `boot` | **skipped, covered by pass 6** (+9.8 s) | **skipped, covered by pass 5** (+9.8 s) | full pass +17.2 / +17.5 s |
| first `home shown lockTask=LOCKED` | **+6.0 s** | **+5.9 s** | +7.4 / +6.1 s |
| full unlocked passes after unlock (in 110 s) | 6 | 4 | 7 / 7 |
| last pass ends | +27.4 s | +17.7 s | +21.1 / +21.6 s |

- **What changed:** each restart now runs two fewer full policy passes, as intended. Home came about 1 s sooner than build 11's mean. That is within build 10/11 run-to-run noise (+5.2 to +7.4 s), so it is not a proven speedup.
- **What remains:** `PACKAGE_CHANGED` events from system apps (wellbeing, carrier settings, the Motorola enterprise adapter) still trigger full passes until +17 to +27 s. Each pass re-publishes Home.
