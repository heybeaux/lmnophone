# Preview 10 on the spare — install, readback, unlock timing (PLA-367)

Driver: the Lead, sole spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15). It has no working SIM. Call was never pressed.

## Artifact
- Gunzipped PLA-366 attachment `5c56da8e…`: APK SHA-256 `5639b6387961d8b5dfaa319261cdce620091faf55635ad54746fb6f03d4f8910`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 9), versionCode 10, `0.1.0-preview-10`, testOnly.

## Install
- `upgrade-check.sh` (`install -r -t`) installed at 2026-10-11T23:06:38Z (`lastUpdateTime`), installer `Success`. The gate restarted the phone and ran both 9-1-1 routes with zero current calls: **`exit=0`** ([upgrade-check.txt](upgrade-check.txt), [More → Emergency](more-emergency-911.png), [Phone](phone-911.png)).
- Before: versionCode 9, Device Owner `org.plainphone/.PlainphoneAdminReceiver`, LOCKED, `firstInstallTime=2026-10-09 10:26:07`.
- After the restart: versionCode 10, Device Owner kept, LOCKED, `firstInstallTime` unchanged, `font_scale=1.0`.
- Safety ([after restart](safety-after-restart.json), from preview 9's `spare_safety.py` unchanged): every field is identical to build 9's after-restart record. That covers the owner, restriction names and sections, lock-task packages and features, LOCKED, `firstInstallTime`, the count switch, and package flags (hidden 42, suspended 0, disabled 116, same list hashes).

## Unlock → Home after a restart (two samples, [sample.sh](sample.sh))
"Unlock" is `uc_finish_user_unlocked`; the spare has no PIN. Times are relative to unlock. Run length is the pass's `totalMs`.

| | Build 10 sample 1 ([log](reboot-1.txt)) | Build 10 sample 2 ([log](reboot-2.txt)) | Build 9 (two samples) |
|---|---|---|---|
| Locked `start` pass ends | −4.0 s (1.1 s run) | −3.2 s (1.1 s run) | −0.2 / −0.1 s (3.9 / 4.0 s run) |
| `PACKAGE_CHANGED` passes before unlock | 0.8–0.85 s runs | 0.7–0.8 s runs | 3.7 / 4.1 s run |
| `reapply reason=user-unlocked` ends | +4.2 s (3.6 s run) | +4.5 s (3.8 s run) | +8.3 / +8.4 s (4.8 / 4.4 s run) |
| `home shown lockTask=LOCKED` | **+5.2 s** | **+6.4 s** | +8.8 / +9.8 s |

- `livePackages` now takes 0.45–0.76 s per pass, down from 2.8–3.8 s. The locked passes finish before unlock.
- What the user now waits on in the user-unlocked pass: `sessionStart` is 2.7–2.9 s, which is most of that pass. Inside it, HIDES write + read + verify take 1.4–1.9 s, and `input` takes 0.76–0.85 s. Two samples are not a proven speedup, but both are faster than every earlier build's sample.
