# Build 22 on the spare (Lead, sole driver)

- APK SHA-256 `3f6fe3e7448d7e51a69d8ce1f653fd7801f9fe2aa9053c32925d174ffae88c62` (gzip `e53b0f78…6dc8`), signer cert SHA-256 `99edd5a3…11bd` (same as build 21).
- Installed in place 21→22 at 2026-10-12T09:32:26Z by the unchanged upgrade gate (`upgrade-gate.txt`, `exit=0`). firstInstallTime unchanged. Readback: versionCode 22, `0.1.0-preview-22`, testOnly, Device Owner, lock task LOCKED.
- Gate: exactly 911 on More > Emergency and on Phone typed digits, zero current Telecom calls both times. No call placed, no SOS gesture.

## Exercised (stream readback from `dumpsys audio`)
- Before: ring 5, media 0, ringer NORMAL (no muted streams), zen off, brightness automatic.
- Spoken labels (UI hierarchy): "Lower/Raise ring volume", "Lower/Raise media volume", "Lower/Raise brightness"; sliders "Ring volume", "Media volume", "Brightness". Visible text stays − and +.
- Side button: on this Moto, VOLUME_UP controls media. Media 0→1 and the page showed 1 within 1.5 s with Sound open (`sound-side-button.png`).
- Ring outside change: `cmd media_session volume --stream 2 --set 6` did not change ring on the spare (system stayed 5); the page kept showing 5, which matched. Ring live refresh is proven on the emulator only.
- Vibrate (`cmd audio set-ringer-mode VIBRATE`): ring slider and both ring buttons disabled, the message "Ring volume can't be changed while the phone is on silent or vibrate." shown (`sound-vibrate.png`). Tapping Raise ring wrote nothing (ringer stayed vibrate). Media stayed usable: Lower media 1→0.
- Back to NORMAL: ring controls enabled again, ring 5 shown (`sound-restored.png`).
- After: ring 5, media 0, ringer NORMAL, zen off, brightness automatic (mode 1) — original values restored.
- No "Volume couldn't be changed." line at any step. The failure-line paths remain host/emulator-proven.
