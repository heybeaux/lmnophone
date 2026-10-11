# Preview 6 on the spare — install, readback, smoke, recovery (PLA-346)

Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15). It has no working SIM, so no counts or calls were exercised. Call was never pressed.

## Artifact
- Gunzipped attachment `045ed326…`: APK SHA-256 `4806bada7b7bd0f827312972b8ff4a98af361df5c5299d625fa25a4a41cd5ace`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`, versionCode 6, `0.1.0-preview-6`, testOnly. Both digests match.

## Install
- `upgrade-check.sh` (`install -r -t`) ran at 2026-10-11T18:37:39Z, and the installer reported `Success`. The gate restarted the phone and ran both 9-1-1 routes with zero current calls: **`exit=0`** ([upgrade-check.txt](upgrade-check.txt)).
- Pre-install readback: versionCode 5, Device Owner `org.plainphone/.PlainphoneAdminReceiver`, LOCKED, `firstInstallTime=2026-10-09 10:26:07`, `show_communication_counts=true`, `font_scale=1.0`.
- After the restart: versionCode 6, Device Owner kept, LOCKED, `firstInstallTime` unchanged.
- Safety comparison ([before](safety-before.json), [after restart](safety-after-restart.json), [after recovery](safety-after-recovery.json), from [spare_safety.py](spare_safety.py)):
  - User restrictions: the set of names is identical, and `no_debugging_features` is absent.
  - Packages: hidden 42, suspended 0, disabled 116 before and after, with an empty set difference (545 packages read).
  - Lock-task packages and features (53) are unchanged.
  - Package names are committed only as a count plus a SHA-256 of the sorted list, because the lists include user-installed apps. The raw dumps stay off the repo.
  - The count switch and owner are unchanged, and adb is reachable.
- The count switch stayed on throughout. The routes did not need it off.

## Smoke (font scale 1.0)
- [Home](home-1.0.png) and [More](more-1.0.png) look like build 5, with no separator lines.
- Gate routes: [More → Emergency 9-1-1](more-emergency-911.png) prefilled exactly `911`, and [Phone](phone-911.png) with 9, 1, 1 typed read `911`.
- `cmd package resolve-activity -a com.android.phone.EmergencyDialer.DIAL` resolves to `com.android.phone/com.android.phone.EmergencyDialer`, enabled and exported ([emergency-resolve.txt](emergency-resolve.txt)).

## Recovery check (no call; one attempt each)
- **Process kill while Phone is showing** ([log](process-kill.txt)): Phone was opened from Home, then `run-as org.plainphone kill -9 <pid>` was sent (rc 0).
  - At +1 s and +10 s: `pidof` was empty (old pid gone). The top activity was still the Google Dialer keypad (`MainActivityDialAlias`), and the state was `mLockTaskModeState=LOCKED` ([screenshot at +10 s](after-kill-10s.png)).
  - Pressing Home brought LMNOPhone Home back in a new process, LOCKED ([screenshot](home-after-kill.png)).
- **`adb reboot`, then the usual no-PIN swipe unlock** ([log](reboot.txt)): Home returned, LOCKED, with the count switch unchanged and `font_scale=1.0` ([Home](home-after-reboot.png)).
  - Observation: the [checking screen](checking-after-reboot-6s.png) (Phone and Emergency call buttons) showed for about 18 s after unlock. USER_UNLOCKED arrived at 11:45:34 (device local time). Android reported BOOT_COMPLETED delivery complete at 11:45:52.4, with a 17.8 s completion latency. LMNOPhone's first unlocked reapply (HOME applied) and `home shown lockTask=LOCKED` came at 11:45:52.6. The locked-boot run at 11:45:38 applied only the floor and guard, as designed.
  - Platform broadcast timing on this phone caused the delay, and calling stayed available. This is not a safety issue, but it is a visible wait.
- `font_scale` was 1.0 before and after, so nothing needed restoring. The readback is `1.0`.
