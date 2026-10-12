# Build 20 author verification

[PLA-395](/PLA/issues/PLA-395). Base `release/preview-19` at `9df8aa0c`.
Branch `release/preview-20`; tests-first checkpoint `ea940536`; implementation `fa736240`.

## Built

Version **20 / 0.1.0-preview-20**, `org.plainphone`, debug/testOnly.
APK SHA-256: `326be911572f9395c1ab18560b4545ad736b9149ab513c6ecfdb56dc1f4180a2`.
Gzip SHA-256: `215e5fbdf642ab0c9c56a9f8ca7eb139a8f80ac98c1eee342f309e2c11e162eb`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`, verified equal to build 19.

From `app/`, JDK 17:

```
./gradlew :device:testDebugUnitTest --tests '*BrightnessControllerTest' :plainphone:testDebugUnitTest :plainphone:assembleDebug
./gradlew :ui:recordPaparazziDebug --tests '*BasicSettingsScreenshotTest' --tests '*CommunicationCountsScreenshotTest.settings*'
./gradlew :ui:verifyPaparazziDebug --tests '*BasicSettingsScreenshotTest' --tests '*CommunicationCountsScreenshotTest.settings*' --tests '*AboutScreenScreenshotTest'
./gradlew :ui:lintDebug :device:lintDebug :plainphone:lintDebug
python3 -m unittest discover -s tools/preview-20 -p 'test_*.py'
```

Seven brightness tests pass: mapping/floor, no-owner, throwing/ignored/partial
writes, restore, auto/manual transition, and unavailable read. All 186 app host
tests pass, including battery mapping and navigation. UI: 80 test methods render
95 screenshots: 40 new basic-settings states, 20 intentionally changed Phone
settings fixtures, and 35 unchanged About fixtures. All five scales (1.0, 1.3,
1.5, 1.8, 2.0), 360×640 dp. Every new or changed golden visually inspected.
Semantics assertions check the failure line, known/unknown battery and charging
copy. Lint passes in all three affected modules. Nine unchanged gate host tests
pass. `openspec validate --all` passes. No checks disabled.

The red checkpoint precedes implementation: `:device:compileDebugUnitTestKotlin`
fails on the missing brightness API. Screenshot goldens were added only for
intentional new/changed screens. About goldens were not recorded.

## Installed

Owned API 35 AVD `pla363-policy-a35` / `emulator-5880` only. In-place **19→20** at
**2026-10-12T07:32:16Z**; readback confirms code 20/name `0.1.0-preview-20` and
preserved first-install time (`install.json`). No clear, reset or reprovision.
The gzip APK is attached to the issue. Physical-spare installation belongs to the Lead.

## Exercised

`basic_settings_route.py` passed on the installed APK. `basic-settings-route.json`
records every brightness readback: + 128, − 102, then 77/51/26; another − stays
26. Slider drag, maximum 255 and floor 26 passed. Automatic reads back 1, manual
controls stay inactive, and turning automatic off reads back 0. Done and Back
return to settings. Both pages were also exercised at 200% text on 360×640 dp.

Battery initially matches `dumpsys battery` at 100%, Charging. An emulator-only
synthetic update to level 64/status discharging appears on the same open page
as **Battery: 64% / Not charging**. The battery simulation was reset. Original
brightness 102/mode 0, font scale 1.0 and stock display dimensions were restored.
All seven resulting screenshots were inspected; they contain no personal data.

Early coordinate-harness attempts stopped short of slider endpoints; the final
script uses actual track taps and a middle-to-left-edge drag. The app code was unchanged; the final
script retains exact 26 and 255 endpoint assertions. No failed attempt is
represented as a pass; the final full script returned exit 0.

The unchanged preview upgrade gate (version strings only) passed **exit=0**:
reboot, Home after full readback, owner/testOnly retained, lock task LOCKED,
exactly 911 via More → Emergency prefill and Phone → keypad, and zero current
Telecom calls. Digits were cleared and Home restored. No call button, call intent
or SOS gesture was used. Existing owner-emulator sdcard EFAULT workaround is
unchanged; the host uses root after reboot while the application UID stays normal.

```
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py app/tools/preview-20 emulator-5880 app/plainphone/build/outputs/apk/debug/plainphone-debug.apk docs/releases/preview-20/evidence
```

## Android reference and behavior

Android's [DevicePolicyManager.setSystemSetting reference](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setSystemSetting(android.content.ComponentName,%20java.lang.String,%20java.lang.String))
lists `SCREEN_BRIGHTNESS`, `SCREEN_BRIGHTNESS_MODE`, and `SCREEN_OFF_TIMEOUT` as
the accepted keys. This build uses only the first two, in `:device`; it requests
no new permission and never opens Android Settings. The controller reads values
back and attempts to restore the pre-write pair if a write fails or is ignored.
If restoration itself fails, it reports failure and shows actual/unavailable
values; rollback is not promised in the face of a second platform failure.

Manual controls have a 26/255 floor (10% rounded upward). Automatic adjustment
is Android's algorithm and is not subject to the manual floor. Manual controls
are disabled while automatic is on; turning it off restores at least 26/255.
Battery is a sticky `ACTION_BATTERY_CHANGED` receiver registered only while the
page is open/resumed. Invalid level or scale is unknown; status is independent.

## Scope and handoff

Covers approved calm-home-screen "Built-in basic settings without Android
Settings" (brightness and battery subset), U7 and readable controls. New state/
event and adapter seams are recorded in `docs/mvp/lanes.md`. D1 policy, counts,
Home, About and emergency implementations are untouched. Wi-Fi, Bluetooth,
Sound and Text size remain out. The failure and unknown-level lines have
`TODO(copy)` in resources/flows because U7 did not previously specify them.

The Lead owns the existing single opposite-model review and subsequent spare
upgrade; restore original spare brightness and mode afterward. Author results
are not review approval or physical-device acceptance.

[Release notes case](/PLA/cases/PLA-C75), [emulator verification case](/PLA/cases/PLA-C76).
