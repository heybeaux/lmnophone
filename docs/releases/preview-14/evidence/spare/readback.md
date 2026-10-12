# Preview 14 on the spare: install, readback, unlock timing (PLA-379)

Driver: the Lead, the only spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review: PLA-379 comment (approve).

## Artifact
- Gunzipped PLA-379 attachment `9d95645e…`: APK SHA-256 `40b7e93249fc372c1f804625e51c94eec7bf477efc48983207b840e64632261a`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 13), versionCode 14, `0.1.0-preview-14`.

## Install
- `app/tools/preview-14/upgrade-check.sh` (`install -r -t`). Installer `Success`; `lastUpdateTime` **2026-10-12T02:24:37Z**.
- **Gate `exit=0` on the first attempt** ([log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)). Device Owner, testOnly and LOCKED were all retained.
- Before: versionCode 13. After: versionCode 14. `firstInstallTime=2026-10-09 10:26:07` is unchanged.
- Safety ([after install](safety-after-install.json), preview 9 `spare_safety.py`; package lists reduced to count + SHA-256 of the sorted list): build 13 before and build 14 after are **identical in every field**. That covers the owner, restrictions, lock-task packages and features, lock state, first install time, the count switch and package flags.

## Unlock → Home after a restart (two samples, [sample.sh](sample.sh))
Times are relative to `uc_finish_user_unlocked`; the spare has no PIN.

| | Build 14 sample 1 ([log](reboot-1.txt)) | Build 14 sample 2 ([log](reboot-2.txt)) | Build 13 | Build 12 |
|---|---|---|---|---|
| `user-unlocked` full pass ends | +5.7 s | +4.2 s | +7.2 / +7.0 s | +5.0 / +5.3 s |
| first `home shown lockTask=LOCKED` | **+6.8 s** | **+5.1 s** | +7.8 / +7.8 s | +6.0 / +5.9 s |
| first `packageSnapshot` (after Home) | +11.0 s, pass 6, 1164 ms | +11.7 s, pass 6, 2670 ms | inside the pass, before Home | none |
| `PACKAGE_CHANGED` skipped `unchanged-since` | wellbeing, enterprise.adapter | enterprise.adapter | 3–4 packages | none |
| full unlocked passes after unlock (in 110 s) | **4** | **4** | 2 / 2 | 6 / 4 |
| last full pass ends | +19.8 s (`package-burst`) | +19.4 s (`package-burst`) | +19.8 / +20.3 s | +27.4 / +17.7 s |

- **What changed:** the snapshot no longer delays Home. The first Home is 1.0–2.7 s earlier than build 13. Sample 2 is inside build 12's range; sample 1 is 0.3 s above it.
- **Found on the spare (fail-closed, performance only):** in both samples, the `locked-boot` broadcast reached the dispatcher straight after the `user-unlocked` pass (`skipped=covered-by=4`, 8–19 ms later). Every `run()` call increments the coverage generation, so this covered skip of the same pass cancelled pass 4's queued capture. With no snapshot, `carriersettingsext` and `actions` ran full passes (each refreshing Home), and the first snapshot came only after pass 6. This is why there are 4 full passes, not ≤2. The fix is a follow-up build: a covered skip that leaves `last` as the same pass must not cancel that pass's capture.
