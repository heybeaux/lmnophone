# Preview 13 on the spare: install, readback, unlock timing (PLA-375)

Driver: the Lead, sole spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review: PLA-375 document `lead-review` (approve).

## Artifact
- Gunzipped PLA-375 attachment `0fc45d1f…`: APK SHA-256 `03d099128f7acdbf11992362c169eea88796c37d65c7e348ef7d0f670bd5ac12`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 12), versionCode 13, `0.1.0-preview-13`.

## Install
- `app/tools/preview-13/upgrade-check.sh` (`install -r -t`), installer `Success`, `lastUpdateTime` **2026-10-12T01:51:24Z**.
- **Gate `exit=0` on the first attempt** ([log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)). Device Owner, testOnly and LOCKED were all retained.
- Before: versionCode 12, `firstInstallTime=2026-10-09 10:26:07`. After: versionCode 13, `firstInstallTime` unchanged.
- Safety ([after install](safety-after-install.json), preview 7 `spare_safety.py`): build 12 before vs build 13 after are **identical in every field**. That covers the owner, restrictions, lock-task packages and features, lock state, first install time, the count switch and package flags.

## Unlock → Home after a restart (two samples, [sample.sh](sample.sh))
Times are relative to `uc_finish_user_unlocked`; the spare has no PIN.

| | Build 13 sample 1 ([log](reboot-1.txt)) | Build 13 sample 2 ([log](reboot-2.txt)) | Build 12 (two samples) |
|---|---|---|---|
| `user-unlocked` full pass ends | +7.2 s (6.3 s pass, `packageSnapshot` 3.1 s) | +7.0 s (5.3 s pass, `packageSnapshot` 2.1 s) | +5.0 / +5.3 s |
| `PACKAGE_CHANGED` skipped `unchanged-since` | carriersettingsext, actions, enterprise.adapter | same three + wellbeing | none |
| first `home shown lockTask=LOCKED` | +7.8 s | +7.8 s | +6.0 / +5.9 s |
| full unlocked passes after unlock (in 110 s) | **2** | **2** | 6 / 4 |
| last full pass ends | +19.8 s (`package-burst`) | +20.3 s (`package-burst`) | +27.4 / +17.7 s |

- **What changed:** single unchanged system-app `PACKAGE_CHANGED` events now skip as intended. Full unlocked passes after unlock fell from 4–6 to 2, and the Home refreshes went with them.
- **Regression:** the new targeted-readback snapshot runs inside the `user-unlocked` pass before Home is published. It costs 2.1–3.1 s and moves the first Home from about +6.0 s to +7.8 s. It needs to happen after Home is published.
- **Still full:** one mixed `package-burst` at about +20 s, which is correct by design (mixed names never skip).
