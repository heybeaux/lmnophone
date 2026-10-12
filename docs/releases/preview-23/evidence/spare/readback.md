# Build 23 on the spare (Lead, sole driver)

- APK SHA-256 `cbc829b9b9b74d43c64f23072bd92461f328cb2ef65aadc46b0e08dd04483afb` (gzip `02c52e18…6385`), signer cert SHA-256 `99edd5a3…11bd` (same as build 22).
- Installed in place 22→23 at 2026-10-12T10:06:02Z by the unchanged upgrade gate (`upgrade-gate.txt`, `exit=0`). firstInstallTime unchanged (2026-10-09). Readback: versionCode 23, `0.1.0-preview-23`, testOnly, Device Owner, lock task LOCKED.
- Gate: exactly 911 on More > Emergency and on Phone typed digits, zero current Telecom calls both times. No call placed, no SOS gesture.

## Exercised
- More > Phone settings with no change waiting: no grey box; Brightness, Sound, Battery and the counts switch are unchanged (`settings-no-wait.png`).
- The waiting-change box itself was not exercised on the spare: this preview line has no supported way to start a waiting change (every trusted-person menu action opens "Not in this test build yet"). The box, its PIN-free Cancel and its removal after cancel are emulator-proven only (PLA-402, case PLA-C84).
