# Preview 17 on the spare: install, readback, PIN route, unlock timing (PLA-387)

Driver: the Lead, the only spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review: approved on PLA-387 (remerge-diff of `ae71cca3` + `c6ed292c` + `d48e7395`). The Lead re-ran the PIN enrollment/access tests and `:plainphone:testDebugUnitTest` at `8f8241be`: 193 tests, 0 failures.

## Artifact
- Gunzipped PLA-387 attachment `ad19ed97…`: APK SHA-256 `d662781d994c22134a6991f630eea82a1618ab939fa3c9faac1735f9d5c29f12`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 16), versionCode 17, `0.1.0-preview-17`.

## Install
- `app/tools/preview-17/upgrade-check.sh` (`install -r -t`). Installer `Success`; `lastUpdateTime` **2026-10-12T04:48:09Z**.
- **Gate `exit=0` on the first attempt** ([log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)). Device Owner, testOnly and LOCKED retained.
- Before: versionCode 16. After: versionCode 17. `firstInstallTime=2026-10-09 10:26:07` unchanged.
- Safety ([after install](safety-after-install.json), preview 9 `spare_safety.py`, fresh before-dump; package lists reduced to count + SHA-256): before and after are **identical in every field**. Hidden 42 / suspended 0 / disabled 116, with hashes equal to build 16.

## PIN route (exercised up to setup; no PIN created)
- Home → More → "For Sam and Jo" opens `PinAccessActivity` on "Choose a new PIN" with "Type it again" and Continue. Lock task stays `LOCKED`.
- The screen captures as black ([pin-setup.png](pin-setup.png)), so `FLAG_SECURE` works on the device.
- Back returns to `MainActivity`, still LOCKED. The app's `authority` directory is empty: **no PIN record exists on the spare**.
- The driver deliberately did not create a PIN. Setup is one-time and has no change or recovery route in this build, so the first PIN is the tester's. Wrong/right PIN, lockout and reboot persistence were exercised on the author's emulator (PLA-C69).

## Unlock → Home after a restart (one sample, [sample.sh](sample.sh), [log](reboot-1.txt))
Times relative to `uc_finish_user_unlocked`; the spare has no PIN.

| | Build 17 | Build 16 (two samples) |
|---|---|---|
| `user-unlocked` full pass ends | +5.2 s | +5.6 / +6.1 s |
| first `home shown lockTask=LOCKED` | +7.4 s | +7.0 / +7.1 s |
| first `packageSnapshot` | +8.1 s, pass 4 | +7.8 / +8.0 s, pass 4 |
| full unlocked passes after unlock | **3** (user-unlocked + 2 grouped bursts) | 4 / 4 |
| skips | locked-boot covered-by=4, boot covered-by=5, enterprise.adapter unchanged-since=6 | similar |
| `failed=` other than null / waiting / attention lines | none | — |

One sample, so this is not a performance claim. First Home is within the spread seen so far. The added authority read and checkpoint left no visible cost and no waiting or attention activity on a phone with no authority record.
