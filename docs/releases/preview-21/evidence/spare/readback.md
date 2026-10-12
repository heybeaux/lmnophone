# Build 21 on the spare (Lead, sole driver)

- APK SHA-256 `e4258a2549fe265c486ebfc4d759e9cea9628ff81092eabaa539d9440dae81b8` (gzip `7228579a…7b04`), signer cert SHA-256 `99edd5a3…11bd`.
- Installed in place 20→21 at 2026-10-12T08:37:23Z (`adb install -r -t`, firstInstallTime unchanged). Readback: versionCode 21, `0.1.0-preview-21`, testOnly, Device Owner, lock task LOCKED.
- Unchanged upgrade gate: `exit=0`, full read-back before Home after reboot, exactly 911 on More > Emergency and on Phone typed digits, zero current Telecom calls both times. No call placed, no SOS gesture.

## Exercised (speaker stream readback from `dumpsys audio`)
- Before: ring 5, media 0, ringer NORMAL, zen off.
- Ring +: 6, −: 5. Media +: 1, −: 0.
- Ring − four times: 1; − at 1: stays 1. Slider: tap right end 7, tap middle 4, drag to far left 1. Ringer mode stayed NORMAL.
- Ring + four times back to 5. After: ring 5, media 0 (original values restored).
- No "Volume couldn't be changed." line during any step.
- Brightness with automatic on (mode 1): no percentage shown. Not toggled on the spare.
- Battery at 100%, status 5 (FULL): "Charged".
- More subtitle: "Brightness, sound, battery".

Not exercised on the spare: media slider (emulator only), DND/silent failure path (host tests only).
