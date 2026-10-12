# Preview 19 on the spare: install, readback, About copy (PLA-392, PLA-394)

Driver: the Lead, the only spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review (opposite model to the codex author): approved on PLA-392, diff `109370bf..9df8aa0c`.

## Artifact
- Gunzipped PLA-392 attachment `1ff6f044…` (gzip `e8cd43f9…3c7016`): APK SHA-256 `6d89b84e7a4bc340fac7e15c5b0c15b242586108c8bcb52afe49e3c12a656d4f`, versionCode 19, `0.1.0-preview-19`.

## Install
- `app/tools/preview-19/upgrade-check.sh` (`install -r -t`). Installer `Success`; `lastUpdateTime` **2026-10-12T07:03:56Z**.
- **Gate `exit=0` on the first attempt** ([log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)). Device Owner, testOnly and LOCKED kept through a restart.
- Before: versionCode 18. After: versionCode 19. `firstInstallTime=2026-10-09 10:26:07` unchanged.
- Safety ([after install](safety-after-install.json), fresh before-dump; package lists reduced to count + SHA-256): before and after are **identical in every field**. Hidden 42 / suspended 0 / disabled 116, hashes equal to build 18.

## About this phone (exercised)
- Home → More → "About this phone" ([top](about-top.png), [emergency section](about-emergency.png)). With the two preview trusted people it now reads "Sam and Jo help choose…", "needs a PIN from one of them", "one of their PINs is used", "so they know".
- The spare has no secure lock, so the "From the lock screen, tap Emergency call" line is no longer shown. The Phone and More → Emergency lines are shown.
- OK returns to More; lock task stays `LOCKED`.
- Not exercised on the device: the secure-lock case (it would need a PIN on the spare; covered by host screenshot tests).
