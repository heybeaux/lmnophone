# Preview 16 on the spare: install, readback, unlock timing (PLA-384)

Driver: the Lead, the only spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review: approve (PLA-384 `review` document); focused `org.plainphone.boot.*` and `PackageChangeReadback*` tests re-run by the Lead at `3454f0f4`: pass.

## Artifact
- Gunzipped PLA-384 attachment `f2a74fdf…`: APK SHA-256 `1cbe9a72bf4f02fb2b298284a08c730f1fdd036386587c72140fc3d3e006fa92`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 15), versionCode 16, `0.1.0-preview-16`.

## Install
- `app/tools/preview-16/upgrade-check.sh` (`install -r -t`). Installer `Success`; `lastUpdateTime` **2026-10-12T03:48:03Z**.
- **Gate `exit=0` on the first attempt** ([log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)). Device Owner, testOnly and LOCKED retained.
- Before: versionCode 15. After: versionCode 16. `firstInstallTime=2026-10-09 10:26:07` unchanged.
- Safety ([after install](safety-after-install.json), preview 9 `spare_safety.py`, fresh before-dump; package lists reduced to count + SHA-256): before and after are **identical in every field**; hidden 42 / suspended 0 / disabled 116 hashes equal build 15.

## Unlock → Home after a restart (two samples, [sample.sh](sample.sh))
Times relative to `uc_finish_user_unlocked`; the spare has no PIN.

| | Build 16 sample 1 ([log](reboot-1.txt)) | Build 16 sample 2 ([log](reboot-2.txt)) | Build 15 |
|---|---|---|---|
| `user-unlocked` full pass ends | +5.6 s | +6.1 s | +3.9 / +5.3 s |
| first `home shown lockTask=LOCKED` | +7.0 s | +7.1 s | +5.6 / +6.5 s |
| first `packageSnapshot` | +7.8 s, pass 4 | +8.0 s, pass 4 | +6.8 / +7.6 s, pass 4 |
| full unlocked passes after unlock | **4** | **4** | 3 / 3 |
| grouped full passes (now named) | +9.6 s `carriersettingsext, actions`; +20.6 s `wellbeing, gms, carriersettingsext` | +9.4 s `carriersettingsext, actions`; +19.8 s `gms, carriersettingsext` | unnamed `package-burst` ×2 |
| single full pass | +22.2 s `actions` | +21.4 s `actions` | — |
| `unchanged-since` skips | enterprise.adapter | wellbeing, enterprise.adapter | wellbeing / enterprise.adapter |
| last full pass ends | +22.2 s | +21.4 s | +18.4 / +18.5 s |

- **What the logs now show:** every group on the spare contains `com.motorola.carriersettingsext` or `com.google.android.gms`, and `com.motorola.actions` also full-passes alone. These packages fail the fresh per-package unchanged check, so the new group skip correctly does not fire. The change can only turn full passes into skips; the extra pass versus build 15 is event-timing variation, fail-closed and performance only.
- **Not met on the spare:** ≤2 full passes, and first Home at +7.0 / +7.1 s is above the ~+6.5 s target (build 14: +6.8 / +5.1 s; within the sample spread seen so far).
- **Next:** stop pass trimming here (as planned after build 16). Why `carriersettingsext`, `gms` and `actions` read as changed is a separate diagnostic, not a preview blocker.
