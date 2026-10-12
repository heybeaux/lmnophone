# Preview 18 on the spare: install, readback, About screen (PLA-389, PLA-391)

Driver: the Lead, the only spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review (opposite model to the codex author): approved on PLA-389, diff `8f8241be..109370bf`. The Lead re-ran `ShellNavigatorTest`, `TestBuildNavigatorTest` and `AboutScreenScreenshotTest` (`verifyPaparazziDebug`) at `109370bf`: 13 + 9 + 15 tests, 0 failures.

## Artifact
- Gunzipped PLA-389 attachment `6fac006c…`: APK SHA-256 `2dcf99555ac91dce374699333cb578269cc9581c31aa7212f93d672b18d6deaf`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 17), versionCode 18, `0.1.0-preview-18`.

## Install
- `app/tools/preview-18/upgrade-check.sh` (`install -r -t`). Installer `Success`; `lastUpdateTime` **2026-10-12T06:04:27Z**.
- **Gate `exit=0` on the first attempt** ([log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)). Device Owner, testOnly and LOCKED retained.
- Before: versionCode 17. After: versionCode 18. `firstInstallTime=2026-10-09 10:26:07` unchanged.
- Safety ([after install](safety-after-install.json), fresh before-dump; package lists reduced to count + SHA-256): before and after are **identical in every field**. Hidden 42 / suspended 0 / disabled 116, with hashes equal to builds 16 and 17.

## About this phone (exercised)
- Home → More → "About this phone" opens U8 ([top](about-top.png), [emergency section](about-emergency.png)). The first line is "Test build 0.1.0-preview-18. Some things described here are not in this build yet."
- OK returns to More. Android Back returns to More. Lock task stays `LOCKED` throughout.
- Font scale 2.0: all text reachable by scrolling, OK pinned and visible ([end of page](about-2.0-end.png)). Font scale restored to 1.0 afterwards.

## Known limitations (deferred, not blocking this preview)
- Two trusted people: the U8 copy is written for one person, so it reads "Sam and Jo helps choose…", "Sam and Jo's PIN", "so Sam and Jo knows". Each person has their own PIN, so "Sam and Jo's PIN" is also misleading.
- "From the lock screen, tap Emergency call" is accurate only with a PIN/pattern lock. The spare has a no-PIN keyguard.
- Most of the About text describes the full product (texts to the trusted person, updates over Wi-Fi, 90-day history). The first line tells the reader that.
