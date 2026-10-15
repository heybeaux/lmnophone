# Build 62 on the spare (PLA-603)

Built `release/preview-62` @ `d692368f` (reviewed tip `eeb52ffd`, PLA-602 APPROVE; serial forward fix `ad535bab`, PLA-599).
APK sha256 `bb98845f6607f8fbffe2afcdafed47464f943e50e82c167b0cd85f4c7036df72`, signer `99edd5a3…11bd`.

- Installed in place 2026-10-15 03:05:16Z: `gate.log` (246 PASS), `before.txt` vs `after-install.txt`.
- Keyboard (03:10–03:20Z, `input keyevent TAB/ENTER`, focus read from uiautomator `focused="true"`):
  - Enter PIN: empty field → Tab cycles field / Forgot PIN (Continue disabled, not focusable). After the PIN: field → Continue → Forgot PIN. Enter in the field opened PIN settings (one correct entry).
  - PIN settings menu: all 9 rows focus in order. Enter on Apps on this phone opened it; Maps, Move Maps down, Camera, Move Camera up, Add a hidden app, Go back focus (Messages/Phone "Always on" are text rows). Back returned to PIN settings.
  - More: Home, Emergency, Phone settings, Ask for an app, PIN settings, Practice voice messages, About focus in order. Enter on Phone settings opened it; More, Wi-Fi … Battery, counts switch focus; Back returned to More.
  - Home: all five focus, but the order is Maps → Messages → Phone → More → Camera (Camera is drawn between Maps and Messages). Seen twice. HomeScreen is not in this build's range.
- Text size 1.3 / 1.5 / 1.8 (`scale-*.txt`, `scale/*.png`; PIN settings and Apps on this phone are FLAG_SECURE, bounds only): on Home, More, Phone settings, About, PIN settings menu and Apps on this phone the last action and the last text end at or above y 2260 (nav bar starts at 2280); no text outside the screen width. Screenshots reviewed at 1.8: nothing cut off.
- About at 1.0: Version 0.1.0-preview-62 (62), "What's new in this version" with the Version 62 line, no "since" section (previous 61 = one step), Older versions starts at Version 61 (`scale/about-1.0-older.png`).
- After: font 1.0, `gate-after-exercise.log` (SKIP_INSTALL, 243 PASS, 911 prefill, zero calls). `after-exercise.txt`: only the authority A/B record changed (correct PIN entries, as in build 61).
