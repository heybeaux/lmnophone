# PLA-311 spike: fallback containment by suspension (THROWAWAY)

Question (PLA-311 finding 1, Lead): when the lock-task features never read back with `KEYGUARD`, can the
Device Owner contain other apps with `DevicePolicyManager.setPackagesSuspended`, so that an explicit
intent, a VIEW intent and a notification tap can't open an unapproved app, while calling still works?

**Answer on API 35: no, not completely.** Suspension blocks Chrome on all three routes, but Android
refuses to suspend Settings and the permission controller, and both still open from an explicit intent
(Settings also from a notification tap). Calling is not broken.

## Setup
- Own AVD `pla242-a35` (emulator-5824), API 35 `google_apis` arm64, `org.plainphone` Device Owner,
  `mvp/device` debug APK already installed. Emulator only. No call was placed.
- `SuspendSpike.java` is an instrumentation that runs in `org.plainphone`'s process (the Device Owner),
  based on the PLA-307 Verifier's `FailureProbe`. It wraps the runtime's port so only
  `setLockTaskFeatures` is ignored, leaves features at 16 with the list kept (so Home stays out of lock
  task, the PLA-307 finding 1 state), suspends the requested packages through DPM, reads them back,
  holds, then lifts exactly that set and restores the port.
- Build: `ANDROID_HOME=… REVIEW_DEBUG_KEYSTORE=~/.android/debug.keystore bash build.sh`;
  run: `adb -s emulator-5824 shell am instrument -w -e hold 200 [-e pkgs a,b | -e pkgs ALL -e keep x,y] org.plainphone.spike311/.SuspendSpike`.

## Run 1: Settings and Chrome only (`evidence/route-*`, `dialer*`, `emergency*`)
`setPackagesSuspended` returned `refused=[com.android.settings]`; logcat:
`Cannot suspend package "com.android.settings": has OP_SYSTEM_EXEMPT_FROM_SUSPENSION set`.

| Route | Settings | Chrome |
|---|---|---|
| explicit intent | **opens** (`Settings` top, lock task `NONE`) | blocked: `ActionDisabledByAdminDialog` "Blocked by work policy" |
| VIEW `https://example.invalid/` | n/a | blocked: same dialog |
| notification tap (debug `TestHook`) | **opens** (`Settings` top) | blocked: same dialog |

## Run 2: everything outside the lock-task list, keyboard, SystemUI and `com.android.emergency` (228 packages; `evidence/sweep-*`)
Android refused 11 (`sweep-refusal-reasons.txt`): `com.android.settings`, `com.android.providers.settings`,
`com.android.keychain`, `com.android.location.fused`, `com.android.inputdevices`, `com.android.localtransport`,
`com.android.dynsystem`, `com.android.DeviceAsWebcam`, `com.android.emulator.multidisplay`
(all `OP_SYSTEM_EXEMPT_FROM_SUSPENSION`), `com.google.android.permissioncontroller` ("required for
permissions management") and `com.google.android.packageinstaller` ("required for package installation").

| Route (explicit intent) | Result |
|---|---|
| Settings | **opens** |
| Permission controller `MANAGE_APP_PERMISSIONS` | **opens** `ManagePermissionsActivity` |
| DeviceAsWebcam preview | did not open (Home stayed) |
| Chrome | blocked dialog |
| `DIAL` | Google Dialer opens (lock-task list, not suspended) |
| `EmergencyDialer.DIAL` | `com.android.phone/.EmergencyDialer` opens |

Keyboard (`com.google.android.inputmethod.latin`) stays default and unsuspended. Nothing was dialled.

## Not tested
- Hiding Settings instead: shell `pm hide` needs `MANAGE_USERS`, so it wasn't tried. A Device Owner
  `setApplicationHidden(Settings)` is untested. It would also remove the "Blocked by work policy" dialog,
  which lives in the Settings package (`hide-alternative.txt`).
- API 36, a physical device, a real (not injected) feature-write failure.

After both runs the AVD was restored: lift read back, Home `LOCKED`, flags 119, no package suspended.
