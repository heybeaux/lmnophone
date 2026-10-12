# Preview 31 — built-in ringtone chooser

Approved slice: [PLA-431](/PLA/issues/PLA-431), based on accepted build 30
at `165c7a10`. Code: `ba29fa30` on `release/preview-31`.

## Built / installed / exercised

- Built: `31` / `0.1.0-preview-31`.
- Final APK SHA-256: `b7e66e8153300729041d5a2acbeb590842023a958a2dcdb5537ee4e181573622`.
- Signer matches accepted 23–30:
  `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
- In-place 30 → 31 on owned emulator-5880 at **2026-10-12 16:43:12Z**;
  canonical-URI correction installed in place at **16:48:45Z** (installer
  finished at 16:48:46Z). No uninstall or reset.
- Initial and final upgrade/restart gates passed: both routes exactly 911,
  zero calls, Device Owner, TEST_ONLY and LOCKED retained. The final installed
  APK was pulled back and byte-compared with the candidate successfully.
- Ringtone gate passed on the final candidate: preview selection left the setting
  unchanged, Done saved Andromeda and Sound showed its confirmed title, then the
  chooser restored Flutey Phone with the exact original canonical URI.
  Ring volume 5, internal/external NORMAL mode, Bluetooth ON, Location 3 and
  saved networks matched the pre-upgrade baseline. WRITE_SETTINGS returned to
  its original `default` app-op. Zero calls; Device Owner/TEST_ONLY/LOCKED kept.
- Spare: **not installed by this author**. The Lead owns the single review
  and the in-place spare install.

## What changed / how to test

More → Phone settings → Sound now includes **Ringtone**, with the actual
read-back title or **Not set**. Open it to see built-in ringtones sorted by
title. The current choice has a check mark and selected accessibility state.
An absent/custom current choice stays unchanged until another is selected.

Tap a row to preview and select it. Preview stops after about five seconds,
on another selection, or on leaving/pausing. Existing ring volume and silent/
vibrate mode are kept. Done saves only a changed choice and requires matching
read-back before returning to Sound. Back discards the choice. Write/read-back
failure stays on the chooser with **Ringtone couldn't be changed.**

The existing WRITE_SETTINGS app-op is needed to save. Without it, rows remain
available for preview but Done is disabled. On the emulator the initial app-op
was `default`; the exercise temporarily grants it and restores `default`.
Setup integration is still deferred. No Android Settings or external picker
opens, and no permission was added. Custom sounds, Silent as a choice, alarm
and notification sounds remain outside this preview.

The adapter uses [RingtoneManager](https://developer.android.com/reference/android/media/RingtoneManager)
TYPE_RINGTONE, filters internal audio URIs, and writes with
setActualDefaultRingtoneUri. Settings.System.RINGTONE is independently read
back. Canonical URI storage preserves Android's title-based identity; both
catalog and read-back URIs are normalized for comparison. Playback uses
[Ringtone](https://developer.android.com/reference/android/media/Ringtone)
with ringtone audio attributes and an explicit five-second stop callback.

## Checks and limits

Tests-first checkpoint `f2437d2e` fails compilation on missing controller/port
before implementation. Twelve controller tests cover current-in-list,
unlisted/no default, empty list, unchanged Done, write success, write failure,
read-back failure/mismatch, grant loss, no-grant preview and stopping on leave.
UI/navigation tests also failed on missing screen/route types before wiring.

65 settings controller checks and all 203 host tests passed. The twelve ringtone
checks passed again after the canonical-URI correction. All 60 affected
Paparazzi checks pass at 1.0/1.3/1.5/1.8/2.0, including selected/unlisted/empty/
no-grant/long-title chooser states and scrolled short/long Sound titles.
All 41 new/changed goldens were inspected. UI lint passes; OpenSpec validates
all three changes. Existing baseline lint errors remain unchanged: five
MissingPermission errors in AndroidWifi and one StateFlowValueCalledInComposition
in PinAccessActivity. No checks were disabled or suppressed.

UIAutomator intermittently failed to produce a dump during transitions. The
ringtone exercise retries only that read up to three times, never a tap/write,
and still requires a fresh completed dump. An early save gate exposed plain
versus canonical URI storage and led to the adapter correction. The cleanup
script also now shell-quotes URI query strings. Initial failed gates are not
reported as passes.

Audible quality is not an emulator claim. Empty/custom/no-default and failing
platform-write states are controller/golden evidence. The Lead must exercise
choosing a different built-in ringtone on the spare, confirm read-back, restore
the original, and confirm unchanged ring volume and ringer mode.

## Handoff and reproduction

APK in assigned worktree `plainphone-policy-preview28`:
`app/plainphone/build/outputs/apk/debug/plainphone-debug.apk`.
Paperclip rejected the 27 MB APK at its 10 MB attachment limit; verify the
SHA-256 above before using the local artifact. Source and evidence are pushed
on `release/preview-31`.

From `app/`:

```sh
./gradlew :device:testDebugUnitTest --tests '*settings.*'
./gradlew :plainphone:testDebugUnitTest :plainphone:assembleDebug
./gradlew :ui:verifyPaparazziDebug --tests '*RingtoneScreenshotTest' --tests '*BasicSettingsScreenshotTest.sound*'
```

From the repository root, `app/tools/preview-31/upgrade_check.py` reuses the
accepted no-call/recovery gate. `ringtone_check.py before` captures build 30's
settings in `PAPERCLIP_RUN_SCRATCH_DIR`; `ringtone_check.py after` exercises and
restores the chooser on **emulator-5880 only**. Logs and inspected screenshots:
[evidence](../../app/tools/preview-31/evidence/).
