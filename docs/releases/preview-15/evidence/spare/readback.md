# Preview 15 on the spare: install, readback, unlock timing (PLA-382)

Driver: the Lead, the only spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review: PLA-382 comment (approve).

## Artifact
- Gunzipped PLA-382 attachment `fca6a83a…`: APK SHA-256 `9d13b678db2b6c2976f3ee1db21434d2b726ab25a3864de7e3f500e4ffbf128a`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 14), versionCode 15, `0.1.0-preview-15`.

## Install
- `app/tools/preview-15/upgrade-check.sh` (`install -r -t`). Installer `Success`; `lastUpdateTime` **2026-10-12T02:49:23Z**.
- **Gate `exit=0` on the first attempt** ([log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)). Device Owner, testOnly and LOCKED were all retained.
- Before: versionCode 14. After: versionCode 15. `firstInstallTime=2026-10-09 10:26:07` is unchanged.
- Safety ([after install](safety-after-install.json), preview 9 `spare_safety.py` with a fresh before-dump; package lists reduced to count + SHA-256 of the sorted list): build 14 before and build 15 after are **identical in every field**. The hidden/suspended/disabled hashes also equal the build 14 readback.

## Unlock → Home after a restart (two samples, [sample.sh](sample.sh))
Times are relative to `uc_finish_user_unlocked`; the spare has no PIN.

| | Build 15 sample 1 ([log](reboot-1.txt)) | Build 15 sample 2 ([log](reboot-2.txt)) | Build 14 | Build 13 |
|---|---|---|---|---|
| `user-unlocked` full pass ends | +3.9 s | +5.3 s | +5.7 / +4.2 s | +7.2 / +7.0 s |
| `locked-boot` covered skip | +3.9 s (`covered-by=4`) | +5.3 s (`covered-by=4`) | same, but cancelled the capture | — |
| first `home shown lockTask=LOCKED` | **+5.6 s** | **+6.5 s** | +6.8 / +5.1 s | +7.8 / +7.8 s |
| first `packageSnapshot` | **+6.8 s, pass 4**, 2841 ms | **+7.6 s, pass 4**, 2246 ms | +11.0 / +11.7 s, pass 6 | inside the pass |
| `PACKAGE_CHANGED` skipped `unchanged-since` | wellbeing, enterprise.adapter | enterprise.adapter | 1–2 packages | 3–4 packages |
| full unlocked passes after unlock (in 110 s) | **3** | **3** | 4 / 4 | 2 / 2 |
| extra full passes | `package-burst` +8.0 s, +18.4 s | `package-burst` +10.8 s, +18.5 s | carriersettingsext, actions, package-burst | — |
| last full pass ends | +18.4 s | +18.5 s | +19.8 / +19.4 s | +19.8 / +20.3 s |

- **What changed (confirmed on the spare):** the covered `locked-boot` skip no longer cancels the user-unlocked pass's capture. The first snapshot belongs to the `user-unlocked` pass (4) in both samples, about 1.1–1.2 s after Home. The single-package full passes from build 14 (`carriersettingsext`, `actions`) are gone. Home is at or under the +6.5 s target in both samples.
- **Target not fully met: 3 full passes, not ≤2.** Both extra passes are `package-burst`: two or more package events grouped while the dispatcher was busy. `PackageEventBurst` runs a group under the reason `package-burst`, and `BootPassCoverage` only checks unchanged packages for single `package:PACKAGE_CHANGED:<name>` reasons. So a group is always a full pass, even when every package in it would pass the unchanged check. This is fail-closed, performance only. The sample logs do not name the grouped packages; the next slice must log them before it changes anything.
