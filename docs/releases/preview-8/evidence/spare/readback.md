# Preview 8 on the spare — install, readback, unlock timing, smoke (PLA-360)

Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15). It has no working SIM, so no counts or calls were exercised. Call was never pressed.

## Artifact
- Gunzipped attachment `e8417827…`: APK SHA-256 `eca349611bb75566ed1e9f1dfb9b45f70085b94070ed66f9c40a98b19230715f`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 7), versionCode 8, `0.1.0-preview-8`, testOnly. Both digests match.

## Install
- `upgrade-check.sh` (`install -r -t`) installed at 2026-10-11T20:56:31Z (`lastUpdateTime`), and the installer reported `Success`. The gate restarted the phone and ran both 9-1-1 routes with zero current calls: **`exit=0`** ([upgrade-check.txt](upgrade-check.txt)).
- Pre-install readback: versionCode 7, Device Owner `org.plainphone/.PlainphoneAdminReceiver`, LOCKED, `firstInstallTime=2026-10-09 10:26:07`, `show_communication_counts=true`, `font_scale=1.0`.
- After the restart: versionCode 8, Device Owner kept, LOCKED, `firstInstallTime` unchanged.
- Safety comparison ([before](safety-before.json), [after restart](safety-after-restart.json), from [spare_safety.py](spare_safety.py), copied unchanged from preview 7): PASS.
  - User restriction names and active sections are identical, and `no_debugging_features` is absent.
  - Packages: hidden 42, suspended 0, disabled 116 before and after, with no new package in any list (545 packages read). The hidden and disabled list hashes equal build 7's.
  - Lock-task packages (9) and features (53) are unchanged.
  - Package names are committed only as a count plus a SHA-256 of the sorted list. The raw dumps stay off the repo.
  - The count switch (on) and the owner are unchanged, and adb is reachable.

## Unlock → Home after a restart (one sample)
"Unlock" is `uc_finish_user_unlocked` in the event log, as for builds 6 and 7. The spare has no PIN.

| | Build 8 ([log](reboot-1.txt)) | Build 7 samples |
|---|---|---|
| User unlocked | 0.0 s | 0.0 s |
| App's locked-boot pass (`reason=start`) ends | +4.6 s (8.7 s run) | +5.1 / +4.0 s |
| `reapply reason=user-unlocked` ends | +11.6 s (7.0 s run) | +9.0 / +8.1 s (3.8 s run) |
| `home shown lockTask=LOCKED` | **+12.8 s** | +9.3 / +8.5 s |

- This sample is about 3.5 s slower than build 7's, all of it in the user-unlocked reapply (7.0 s against 3.8 s). It ran straight after the locked-boot pass on the same thread. Build 8 does not change the boot path, so this is most likely run-to-run variance on the phone; one sample cannot show that. Still well under build 6's ~18 s.
- Home after the restart: [screenshot](home-after-reboot.png), LOCKED.

## Smoke (font scale 1.0)
- [Home](home-1.0.png) and [More](more-1.0.png) look like build 7.
- Phone opens from Home: the Google Dialer keypad is on top and the state stays LOCKED ([screenshot](phone-from-home.png)). Home returns LOCKED. Telecom showed no call.
- Gate routes: [More → Emergency 9-1-1](more-emergency-911.png) prefilled exactly `911`, and [Phone](phone-911.png) with 9, 1, 1 typed read `911`. Telecom showed zero current calls on both.
- **The change in this build (More → Emergency comes back with `911` after a crash) is emulator-checked only.** The proof needs a root `kill -9` while the keypad opens, and the stock spare has no root, so it was not tried here.
- `font_scale` was 1.0 before and after.
