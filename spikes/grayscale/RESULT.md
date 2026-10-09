# PLA-38 greyscale spike: results (THROWAWAY)

This is spike work, not MVP code. Everything under `spikes/grayscale/` is throwaway.

## Setup

- AVD `pp38-a35`, serial emulator-5650, `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.036/12592187:user/release-keys` (Google Play image, API 35). No Google account. We never touched emulator-5590, emulator-5604, emulator-5640 or the spare phone.
- DPC: `apps/dpc` (`com.plainphone.spike.grey`), v1 built `testOnly`. It is the Device Owner and the home app, and it runs lock task. Test hook: `tools/pp.sh` (`cmd …` sends a broadcast guarded by `android.permission.DUMP`, so only adb can call it).
- "Grey on" = `Settings.Secure.putInt(accessibility_display_daltonizer, 0)` then `putInt(accessibility_display_daltonizer_enabled, 1)`. "Grey off" = `accessibility_display_daltonizer_enabled` = 0.

## Summary

| Question | Answer | One line |
|---|---|---|
| Before the grant | **No** | A Device Owner alone can't do it: `putInt` throws, `setSecureSetting` throws, and `setPermissionGrantState(WRITE_SECURE_SETTINGS)` returns `false`. |
| Q1 grey on and off | **Yes at system level, not seen on the emulator screen** | With the grant both writes succeed. SurfaceFlinger's colour matrix becomes greyscale and goes back to identity, each within 0.05 s of the command returning. The emulator's display doesn't draw that matrix, so screenshots stay in colour. |
| Q2 grant survives | **Yes** | Survives two reboots and two self-updates through a `PackageInstaller` session (v1→v2→v3, same key). |
| Q3 setting survives | **Yes** | Stays on through reboot, lock task and both self-updates. It can be toggled inside lock task. |
| Q4 wider reach | **Some; restrictions still hold** | Keys guarded by user restrictions can't be changed. Unguarded keys can: accessibility services, default keyboard, `development_settings_enabled`. Two changes recommended (end of Q4). |
| Extra | **Grey outlives Plainphone** | After removing Device Owner and uninstalling, the phone stays grey. The "turn Plainphone off" flow must switch grey off first. |

## Before the grant (`evidence/q1-setup.txt`)

```
$ cmd grey-on        -> SecurityException: Permission denial, must have one of: [android.permission.WRITE_SECURE_SETTINGS]
$ cmd dpm-grant      -> setPermissionGrantState(WRITE_SECURE_SETTINGS, GRANTED) -> false
$ cmd dpm-secure accessibility_display_daltonizer_enabled 1
                     -> SecurityException: Permission denial: Device owners cannot update accessibility_display_daltonizer_enabled
$ adb shell pm grant com.plainphone.spike.grey android.permission.WRITE_SECURE_SETTINGS   -> exit 0
$ dumpsys package    -> android.permission.WRITE_SECURE_SETTINGS: granted=true
```

So the grant **needs adb**. QR or NFC setup without a computer can't give it, and the Device Owner can't grant it to itself. It also has to happen **before** `DISALLOW_DEBUGGING_FEATURES` is set, because that restriction switches adb off at once (seen in Q4).

## Q1. Grey on and off (`evidence/q1-toggle.txt`, `evidence/q1-display.txt`)

```
$ cmd grey-on  -> putInt mode=true enabled=true -> accessibility_display_daltonizer_enabled=1 accessibility_display_daltonizer=0
   colorTransformMatrix=[[0.213,0.715,0.072,0][0.213,0.715,0.072,0][0.213,0.715,0.072,0][0,0,0,1]]   (after 0.04 s)
$ cmd grey-off -> accessibility_display_daltonizer_enabled=0
   colorTransformMatrix=[[1,0,0,0][0,1,0,0][0,0,1,0][0,0,0,1]]                                        (after 0.04 s)
```

- Repeated twice each way. Each row of the matrix holds the Rec. 709 brightness weights (0.2126, 0.7152, 0.0722), so every pixel becomes its own brightness. That is greyscale.
- Settings → Developer options → "Simulate color space" then reads "Monochromacy". That option uses the same keys.
- **What the emulator doesn't show:** `screencap` (`shots/q1-1-grey-on.png`) and the emulator's own display capture (`shots/q1-3-…`, `shots/q1-4-…`) stay in colour. That held even after switching on "Disable HW overlays", which forces GPU composition. The emulator's display path ignores the colour matrix. A real phone's display hardware or GPU is expected to apply it, but that hasn't been checked here. Confirming that takes about two minutes on the spare moto g (set the two keys, look at the screen). **That needs the Lead's go-ahead and wasn't done.**

## Q2. Does the grant survive? (`evidence/q2q3-reboot.txt`, `evidence/q2-self-update.txt`)

| Step | `dumpsys package` | Grey still writable |
|---|---|---|
| Reboot | `granted=true` | yes |
| Self-update v1→v2 (`PackageInstaller` session, `USER_ACTION_NOT_REQUIRED`, no prompt as Device Owner) | `versionCode=2 … granted=true` | yes |
| Self-update v2→v3 | `versionCode=3 … granted=true` | yes |
| Reboot after updates | `versionCode=3 … granted=true` | yes |

- `logcat`: `install status=0 msg=INSTALL_SUCCEEDED`, then `MY_PACKAGE_REPLACED: version=grey-2(2) deviceOwner=true WRITE_SECURE_SETTINGS=true lockTask=1`.
- Side finding: a `testOnly` APK can't update itself through a session (`INSTALL_FAILED_TEST_ONLY`: an app can't pass `-t`). So v2 and v3 were built with `testOnly=false`, as real releases will be. It doesn't change the answer.
- **Not covered:** an uninstall and reinstall, a factory reset, or a change of signing key. Each is expected to lose the grant (not tested), and it could only come back over adb. So Plainphone should check `checkSelfPermission(WRITE_SECURE_SETTINGS)` and hide the "Grey screen" row when the grant is missing.

## Q3. Does the setting survive? (`evidence/q2q3-reboot.txt`, `evidence/q2-self-update.txt`)

- Inside lock task (`lockTask=1`), grey off and grey on both work, and the matrix follows.
- After a reboot: `enabled=1 mode=0`, greyscale matrix, `lockTask=1`. Android restores the setting on its own, with no help from Plainphone at boot.
- After each self-update: the setting is unchanged.

## Q4. Does the grant widen what Plainphone could change? (`evidence/q4-scope.txt`, `evidence/q4-removal.txt`)

`WRITE_SECURE_SETTINGS` covers all of `Settings.Secure` and `Settings.Global`, not just the two grey keys. Probed with the app's own `put` (each value restored afterwards):

| Key | Guarding restriction set? | Result |
|---|---|---|
| `install_non_market_apps` = 1 | `DISALLOW_INSTALL_UNKNOWN_SOURCES` | `putString` returns `true`, value **stays 0** |
| `location_mode` = 3 | `DISALLOW_SHARE_LOCATION` | returns `true`, value **stays 0** |
| `adb_enabled` = 1 | `DISALLOW_DEBUGGING_FEATURES` | returns `true`, value **stays 0** |
| `development_settings_enabled` = 1 | `DISALLOW_DEBUGGING_FEATURES` | **changed to 1** |
| `adb_enabled` = 1 | none (restriction just cleared) | changed to 1 |
| `enabled_accessibility_services`, `accessibility_enabled` | none | **changed** |
| `default_input_method` | none | **changed** |
| `accessibility_display_inversion_enabled` | none | changed |

What this means for the protected-state check:

1. **User restrictions still win.** The settings our restrictions guard (adb, unknown sources, location) can't be changed through the grant. Note that the write returns `true` even when nothing changed, so code must read the value back.
2. **The grant adds little that a Device Owner couldn't already do.** A buggy or compromised Plainphone can already clear restrictions and install apps. The new reach is the unguarded keys: enabling an accessibility service, changing the default keyboard, or turning colour inversion on. **No new check rows are needed for this.** The existing rows read the *policy*, though (`getPermittedAccessibilityServices`, `getPermittedInputMethods`), not what is *enabled*. Whether Android refuses to bind a non-permitted service listed in `enabled_accessibility_services` wasn't tested. It would need a third-party service fixture.
3. **Recommended, if grey ships:**
   - Add **one** protected-state row: "Grey screen", expected = the trusted person's choice, readback = the two keys, repair = write them. During a Settings session the user can reach Accessibility → Colour correction and switch grey off. The default Quick Settings tiles don't include colour correction, and the accessibility shortcut is unset (`evidence/q4-scope.txt` e). Whether the "edit tiles" panel can add it during lock task wasn't tested.
   - Put every secure-settings write in **one small class that can only write the two grey keys**, and add a unit test for it. That keeps the wider reach from being used by accident.
4. **Grey outlives Plainphone** (`evidence/q4-removal.txt`). After `dpm remove-active-admin` and uninstall, the phone stays grey (`enabled=1`, greyscale matrix). The "turn Plainphone off" flow and the removal instructions must switch grey off first.

## Not done / follow-ups

- Visual check on a real screen (spare moto g, about 2 minutes). Needs the Lead's go-ahead. It also answers whether Motorola's build honours the setting.
- Android 16 (API 36) not run; only API 35 was asked for.
- No MVP code. If the Lead wants the feature, it goes into the OpenSpec change first (setup script line, the row, the protected-state row, the turn-off step).
