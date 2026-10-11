# Preview 7 on the spare — install, readback, unlock timing, smoke (PLA-354)

Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15). It has no working SIM, so no counts or calls were exercised. Call was never pressed.

## Artifact
- Gunzipped attachment `03f0bd9f…`: APK SHA-256 `cb6aaa42d843badb6e7dfead15675760b1270e37dc9a7aafd0831ebaa2fb4fd3`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`, versionCode 7, `0.1.0-preview-7`, testOnly. Both digests match.

## Install
- `upgrade-check.sh` (`install -r -t`) installed at 2026-10-11T19:39:50Z, and the installer reported `Success`. The gate restarted the phone and ran both 9-1-1 routes with zero current calls: **`exit=0`** ([upgrade-check.txt](upgrade-check.txt)).
- Pre-install readback: versionCode 6, Device Owner `org.plainphone/.PlainphoneAdminReceiver`, LOCKED, `firstInstallTime=2026-10-09 10:26:07`, `show_communication_counts=true`, `font_scale=1.0`.
- After the restart: versionCode 7, Device Owner kept, LOCKED, `firstInstallTime` unchanged.
- Safety comparison ([before](safety-before.json), [after restart](safety-after-restart.json), from [spare_safety.py](spare_safety.py)):
  - User restriction names and active sections are identical, and `no_debugging_features` is absent.
  - Packages: hidden 42, suspended 0, disabled 116 before and after, with no new package in any list (545 packages read). The hidden-list hash equals build 6's.
  - Lock-task packages (9) and features are unchanged.
  - Package names are committed only as a count plus a SHA-256 of the sorted list. The raw dumps stay off the repo.
  - The count switch (on) and the owner are unchanged, and adb is reachable.

## Unlock → Home after a restart (the change in this build)
The spare has no PIN, so Android unlocks user storage as soon as boot finishes. "Unlock" below is `uc_finish_user_unlocked` in the event log (build 6 used the same USER_UNLOCKED point).

| | Sample 1 ([log](reboot-1.txt)) | Sample 2 ([log](reboot-2.txt)) | Build 6 |
|---|---|---|---|
| User unlocked | 0.0 s | 0.0 s | 0.0 s |
| App's locked-boot pass (`reason=start`) | +5.1 s | +4.0 s | +4 s |
| `reapply reason=user-unlocked` | +9.0 s | +8.1 s | (none) |
| `home shown lockTask=LOCKED` | **+9.3 s** | **+8.5 s** | **+18.6 s** |
| `reapply reason=boot` (BOOT_COMPLETED) | +16.9 s | not captured (log rotated) | +18.6 s |
| Android finished BOOT_COMPLETED | +19.1 s | +18.9 s | +18.4 s |

- The `user-unlocked` reapply now comes about 8 s **before** the BOOT_COMPLETED pass, as designed, and Home follows it within 0.4 s.
- The checking screen is still visible. In sample 2 (keyguard dismissed at once, screenshots every ~0.5 s) the keyguard was still fading at +0.4 s, and the checking screen showed from the next frame at +2.1 s to +7.8 s ([screenshot at +6 s](checking-after-reboot-6s.png)), and Home was drawn at +8.5 s. Phone and Emergency call stayed on it the whole time.
- What remains: Android starts the LMNOPhone process only about 4–5 s after unlock on this phone, and the user-unlocked reapply then takes about 4 s. Those are the next places to look if the wait still feels long.
- Home after the restart: [screenshot](home-after-reboot.png), LOCKED, count switch unchanged.

## Smoke (font scale 1.0)
- [Home](home-1.0.png) and [More](more-1.0.png) look like build 6.
- Phone opens from Home: the Google Dialer keypad is on top and the state stays LOCKED ([screenshot](phone-from-home.png)). Home returns LOCKED.
- Gate routes: [More → Emergency 9-1-1](more-emergency-911.png) prefilled exactly `911`, and [Phone](phone-911.png) with 9, 1, 1 typed read `911`. Telecom showed zero current calls on both.
- The checking-screen emergency fallback (PLA-347) was not exercised. This phone has the preferred emergency dialer, so that path is host-tested only.
- `font_scale` was 1.0 before and after, so nothing needed restoring. The readback is `1.0`.
