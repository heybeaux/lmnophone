# Location permission correction

Branch `pla-app-permissions/location-fix`, based on `6ce19b1c` (build 54's accepted app-permissions slice). Author evidence only; spare installation belongs to the Android Platform Engineer after the focused review.

## Cause and correction

The spare probe in `release/preview-54` at `34a83398`, `app/tools/preview-54/evidence/spare/location-readback-probe.txt`, showed accepted Location writes followed by DPM DENIED and a granted runtime permission. FINE had never been attempted: the previous COARSE write failed its readback.

Android 15's [DevicePolicyManagerService.getPermissionGrantStateForUser](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java#16936) checks POLICY_FIXED separately, but computes GRANTED/DENIED with PermissionChecker, including app-op state. Thus a non-DEFAULT result proves the fixed bit, while its effective access answer can differ from the stored Location grant.

The private API 35 emulator reproduced another distinction: after revoking COARSE while FINE remained granted, dumpsys showed COARSE granted=false, but PackageManager.checkPermission(COARSE) still returned true. PackageInfo.requestedPermissionsFlags also reports effective grants, so it is not a raw-grant workaround. Android 15's [PermissionService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/permission/java/com/android/server/permission/access/permission/PermissionService.kt#553) explicitly implements FINE as a fuller permission for COARSE, including in getGrantedPermissions. The correction revokes FINE before COARSE and grants COARSE before FINE, retaining Deny-before-Allow. Only unsuspended COARSE/FINE fixed DENIED states with an effective runtime grant are normalized to GRANTED; DEFAULT, unknown, and unrelated mismatches remain unchanged.

The visible permission window already existed and remains the containment boundary. No permission write runs until fresh PIN, exact manifest/identity checks, exclusion from Home and lock task, and unhide readback. This change does not add launch access or change the emergency floor.

Before writes, the transaction captures every requested permission's fixed state and effective runtime grant. Failure restores those values while excluded, then re-hides. DEFAULT needs special handling: restore its runtime grant before clearing the temporary fixed state. Failed hidden read/save reopens only through the same guarded window. A replacement identity never receives the old identity's restored grants. Failure readback is refreshed after restoration. Refused restoration returns CheckFailed and still re-hides; no success is claimed.

Limits: this is an in-process rollback, not a durable rollback journal. Process death keeps the existing startup containment behavior. The Android USER_SET/sensitivity bookkeeping flags are not reset; the saved runtime grants and POLICY_FIXED states are the rollback contract. Android refusing rollback, or a changed identity/role/manifest, returns attention rather than pretending to have restored anything.

## Reproduce

Use a private API 35 AVD with `-read-only -port 5964`, never a physical device. All commands below name `emulator-5964`. The author's AVD was `pla525-ui-a35`; no saved AVD state is modified.

Build from `app/` with JDK 17 and Android SDK 36:

```sh
./gradlew :device:testDebugUnitTest :plainphone:testDebugUnitTest :plainphone:assembleDebug :device:assembleDebugAndroidTest
```

From the repo root, build the synthetic fixture with `bash app/tools/pla564/build-fixture.sh`. Install the fixture and the device test APK with `adb -s emulator-5964 install -r -t`. On the disposable emulator, make `org.plainphone.device.test/org.plainphone.device.apply.CheckAdmin` Device Owner, set Location off with `adb -s emulator-5964 shell settings put secure location_mode 0`, and run:

```sh
adb -s emulator-5964 shell am instrument -w -e phase location org.plainphone.device.test/org.plainphone.device.storage.DirectBootJournalCheck
```

This invokes only the bounded `location` phase, never connectedAndroidTest. It checks all five groups, both choices, hidden readback, and a synthetic FINE refusal restoring saved fixed/runtime states. It does not call or send. For the production UI pass, remove this test admin, install the app APK as Device Owner, use preview SET_STARTER to include the fixture with the emulator's real Phone/Messages entries, and enroll the synthetic PIN `111111` through setup. For the recorded UI run, set `font_scale` to `2.0` and `show_ime_with_hard_keyboard` to `1` on this emulator. Save `synthetic-data` in the fixture's private `files/permission-marker`. Run `ANDROID_HOME=... python3 app/tools/pla564/emulator-check.py` for add/remove with both choices, per-permission dumpsys readback, restart, and saved-data checks. No contacts or real identities are seeded.

No UI layouts or copy resources change in this slice. Existing Paparazzi baseline failures are separate; no goldens are recorded.

## Recorded candidate

- Built: debug app SHA-256 `ce434227f6cbbe75672875180d55cebb2bff39e19e7af17c738892e3e6f63056`, retaining base versionCode 53 / `0.1.0-preview-53`, testOnly. The release driver assigns the next build number after the latest installed build and consumes `docs/releases/pending-location-fix.md`.
- Installed: private `emulator-5964`, in place at 2026-10-14 12:23:33 UTC (guest package lastUpdateTime 05:23:33 PDT). Spare: not installed by this lane.
- Host: final `:device:testDebugUnitTest` 1169 tests, zero failures/errors/skips; `:plainphone:testDebugUnitTest` 319 tests, zero failures/errors/skips. Debug app and device test APK build passed. No Paparazzi run or golden changes.
- Emulator adapter: `evidence/api35-matrix.txt`, API 35 with Location off. All five groups Allow and Deny, hidden readback, plus injected FINE refusal and restoration of previous fixed/effective runtime states. Eight runtime permissions, including Android's implicit READ_MEDIA_VISUAL_USER_SELECTED.
- Test-first evidence: `1e99e183` reproduces COARSE left fixed after FINE refusal (10 tests, one expected failure). `43a4ef93` adds foreground Location regression cases. `827fdb4d` adds rollback/cancellation/save/identity/denial-order cases; before the ordering correction, 13 focused tests had two failures. Final focused and full tests pass.

The UI replay uses the real permission-group label "Location (where the phone is)" and scrolls
inside the body bounds, so the pinned buttons remain outside the swipe. Earlier automation
attempts assumed a shorter label and a fixed swipe position; those did not complete the replay
and are not acceptance evidence. No app correction was needed for those driver assumptions.

Production UI result: both Allow and Don't allow completed through remove → fresh PIN →
Add a hidden app → five explicit choices → fresh PIN → Added, at 360×640 dp and font scale
2.0. `evidence/production-ui-2x.txt` contains per-permission dumpsys readback for both choices
and after a force-stop/restart. All eight fixed states match each choice. The fixture's private
`synthetic-data` marker survived both cycles and restart. The installed emulator `base.apk`
SHA-256 matches the built APK. No calls or sends occurred. This is an app process restart,
not a guest reboot. Spare installed/exercised: no; that remains the sole driver's review-approved step.

`evidence/readback-page-2x.txt` also confirms every production page group reads Not allowed after restart at 2.0. Author QA case: PLA-C141.
