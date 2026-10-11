# PLA-242 spike: lock task before the first unlock (throwaway evidence)

Question (PLA-241 finding 1): can Plainphone be in lock task before the first unlock, so that an
activity queued while the user is `RUNNING_LOCKED` cannot win at unlock?

Emulator: own AVD `pla242-a35` (emulator-5824), API 35 `google_apis` arm64, Device Owner = debug
build, no policy file, emulator-only PIN **1234** (recorded here; wipe the AVD rather than reuse it).
No call was placed. No physical device was used.

## What was tried

1. `MainActivity` gets `android:directBootAware="true"`. It already shows only the checking screen
   (Phone, Emergency call) until an unlocked re-apply is ready, and the policy runtime already uses only
   device-protected storage.
2. After the locked-boot re-apply restores the lock-task list, the boot receiver starts `MainActivity`
   again (`LockedHome`). `onNewIntent`/`onResume` call `startLockTask()`.

## Results

- **The system starts our Home before unlock** (Direct Boot aware, persistent preferred Home), instead
  of Settings' FallbackHome: `START u0 {act=MAIN cat=[HOME] ... cmp=org.plainphone/.MainActivity} ...
  from uid 0` (`evidence/locked-home-start.log`).
- **The Device Owner may start it from the receiver:** `... from uid 10207 (BAL_ALLOW_ALLOWLISTED_COMPONENT)
  result code=3`. AOSP android-15.0.0_r1 `BackgroundActivityStartController.java:859-863`:
  "don't abort if the callingUid is the device owner ... BAL_ALLOW_ALLOWLISTED_COMPONENT ... "Device Owner"".
- **First run broke the keyguard (fixed).** In the DROP scenario (list empty, features 16) the
  re-apply restored the list before the features. Home entered lock task in between, without the
  KEYGUARD feature, and Android dismissed the PIN keyguard while the user was still `RUNNING_LOCKED`
  (`evidence/run1-keyguard-lost-*`: `LOCKSCREEN -> GONE`, `isKeyguardShowing=false`). Restoring the
  features later did not bring it back. A plain reboot with features intact kept the keyguard.
  Fix: write the lock-task features before the membership, in the floor and in the lock-task stage.
- **After that fix, all three variants pass** (`app/plainphone/check/first-unlock-containment.sh`,
  DROP no_safe_boot + list + features 16, reboot, stimulus queued while `RUNNING_LOCKED`, PIN unlock,
  12 samples):
  - before unlock: `keyguard=true lockTask=LOCKED`, policy `mFlags=119`;
  - `explicit` (`am start -n com.android.settings/.Settings`): every sample from the first after unlock
    `lockTask=LOCKED top=BlockedAppActivity` ("Settings is not available right now"); a tap leaves it so;
  - `view` (`am start -a VIEW -d https://example.invalid/`): every sample `LOCKED`, top `MainActivity`;
  - `notification` (Plainphone-posted notification whose tap opens Settings, posted before unlock): every
    sample `LOCKED`; tapping it in the shade after unlock gives `BlockedAppActivity`.
- **Emergency under the keyguard still works.** Locked boot, PIN bouncer, its "Emergency" button opens
  `com.google.android.apps.safetyhub/.emergencydialer.ui.DialPadActivity` with `mKeyguardShowing=true`,
  `mLockTaskModeState=LOCKED`, user `RUNNING_LOCKED` (`evidence/emergency-*.png`). No call placed.

## Residual risk

- If lock task is ever entered with features that lack KEYGUARD, Android drops even a secure keyguard
  and does not re-show it when the features come back. Our writes now order features first; the DROP
  hook stands in for a power cut that lost both writes.
- One emulator image (API 35). API 30-34 and 36 are not run here.
