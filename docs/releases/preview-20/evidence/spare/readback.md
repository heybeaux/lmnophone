# Preview 20 on the spare: install, readback, Brightness and Battery (PLA-395, PLA-396)

Driver: the Lead, the only spare driver for this install. Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15), no working SIM. Call was never pressed. Lead review (opposite model to the codex author): approved on PLA-395, diff `9df8aa0c..429898d9`.

## Artifact
- Gunzipped PLA-395 attachment `b48228a2…` (gzip `215e5fbd…162eb`): APK SHA-256 `326be911572f9395c1ab18560b4545ad736b9149ab513c6ecfdb56dc1f4180a2`, versionCode 20, `0.1.0-preview-20`.

## Install
- `app/tools/preview-20/upgrade-check.sh` (`install -r -t`). Installer `Success`; `lastUpdateTime` **2026-10-12T08:02:39Z**.
- **Gate `exit=0` on the first attempt** ([log](upgrade-check.txt)). Both 9-1-1 routes gave exactly `911` with zero current calls ([More → Emergency](more-emergency-911.png), [Phone](phone-911.png)). Device Owner, testOnly and LOCKED kept through a restart.
- Before: versionCode 19. After: versionCode 20. `firstInstallTime=2026-10-09 10:26:07` unchanged.
- Safety ([after install](safety-after-install.json), fresh before-dump; package lists reduced to count + SHA-256): before and after are identical in every field except the timestamp. Hidden 42 / suspended 0 / disabled 116, hashes equal to build 19.

## Brightness (exercised)
Home → More → Phone settings ([rows](phone-settings.png)) → Brightness. Values are `settings get system screen_brightness` / `screen_brightness_mode` read after each step.

| Step | Screen | Setting read back |
|---|---|---|
| Open (spare was on automatic) | "0%", −/+ disabled ([image](brightness-auto.png)) | raw 1, mode 1 |
| Turn "Adjust automatically" off | 10% ([image](brightness-manual-floor.png)) | raw 26, mode 0 (floor) |
| + ×3 | 20%, 30%, 40% | raw 102 at 40% |
| + ×7 more | 100% ([image](brightness-100.png)) | raw 255 |
| − ×12 | 10% ([image](brightness-10.png)) | raw 26 (floor held) |
| Drag slider to the middle | 59% ([image](brightness-slider.png)) | raw 150 |
| Turn automatic back on, press + | 59% shown, + has no effect | raw 26, mode 1 |
| Done | back on Phone settings | — |

No "couldn't be changed" message appeared: every manual write read back exactly on this hardware. The spare was left on automatic, as it was found.

## Battery (exercised)
Phone settings → Battery ([image](battery.png)): "Battery: 100%", "Charging". `dumpsys battery`: level 100, status 5 (full), USB powered. Back returned to Phone settings; Home returned Home; lock task `LOCKED`.

## Findings (nonblocking, for a later slice)
1. In automatic mode the percentage is not the live value ("0%" on open; "59%" kept after turning automatic on while the setting read 26). Suggest hiding the number while automatic is on.
2. Battery full on USB (status 5) reads "Charging"; "Charged" would be more exact.
3. The More row subtitle still says "Wi-Fi, sound, text size", but this build has Brightness and Battery and none of those three.
