# PLA-22 result: Android 15/16 boot loop after unhide + reboot

**THROWAWAY SPIKE. Emulator only. Not MVP code.** No physical device was used. SPARE_DEVICE_SERIAL, emulator-5590 and emulator-5604 were not touched.

## Bottom line

**It reproduces, and the cause is understood.**

- **Trigger:** a Device Owner *unhides* an app with `setApplicationHidden(admin, pkg, false)`, and the phone loses power or reboots without the framework shutdown **within about 10 s**.
- **Result:** system_server crashes on every boot and the phone never starts again.
  - This happened on API 35 and on API 36.
  - It happened for the Wi-Fi sign-in app, Settings and the Play Store.
- **Why:** PackageManager saves the hidden flag up to 10 s late (`WRITE_SETTINGS_DELAY = 10*1000`). The device-policy engine saves its copy of the policy at once.
  - At boot the engine re-applies "not hidden" at boot phase 500.
  - Unhiding a package sends a broadcast that is not allowed before boot completes, which throws inside system_server.
  - The same mismatch is still on disk at the next boot, so the crash repeats on every boot.
- **What does not trigger it:**
  - a reboot 15 s or more after the change;
  - a reboot that goes through the framework shutdown (`svc power reboot`, the power-menu Restart);
  - the opposite change (hiding an app, then rebooting quickly).
- **Inside the window it is a race, not a certainty:** at 5 s, 2 of 3 trials looped.
- **Recovery:** none short of a wipe. A non-technical user sees the boot animation forever. See Q6.
- **AOSP:** I found no fix in android16-qpr2-release or main. See Q7.

**What this means for the design (Lead and Verifier decide):**

- Any unhide at the start of a session (Play, Settings, Wi-Fi sign-in) opens a window of up to 10 s. If the phone dies or is force-restarted in that window, it is bricked until a recovery wipe, and that wipe triggers FRP.
- The window is short. To fall into it, the user must hold the power button for a hardware force-restart, or the battery must die, in the seconds after a session starts.
- Normal restarts are safe. That includes the power-menu Restart, and the low-battery shutdown, which goes through the framework.
- Unhiding is the only dangerous direction. Hiding an app is safe.

## Setup

- **API 35 AVD:** `pp11-a35`, wiped with `-wipe-data`.
  - Image: API 35 Google Play, `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.036/12592187:user/release-keys`.
  - Port 5620, no boot animation (`-no-boot-anim`), no window.
- **API 36 AVD:** `pp11-a36`, wiped.
  - Image: `emu64a:16/BE2A.250530.026.D1/13818094:user/release-keys`.
  - Port 5622.
- **DPC:** `apps/` is a minimal testOnly DPC, `com.plainphone.spike.bootloop`. It has only an admin receiver and an adb command receiver: `hide`, `unhide`, `suspend`, `unsuspend` and `state`. It has no home activity and no boot receiver, so nothing of ours runs at boot.
- **Device Owner:** set with `adb shell dpm set-device-owner com.plainphone.spike.bootloop/.AdminReceiver`.
- **User-installed app:** the PLA-18 spike APK, `com.plainphone.spike.emu`, installed with `adb install -t`. It is not an admin.
- **Restoring between trials:** I took an emulator snapshot (`do-clean2`) after Device Owner setup and reloaded it before each trial. `tools/bl.sh` `reset` does this:
  1. load the snapshot;
  2. set the starting state (hide or suspend);
  3. wait 15 s, which is past the 10 s write delay, so the starting state is on disk.
- **One trial:** `tools/bl.sh` `trial` runs these steps:
  1. make the change;
  2. `sleep <delay>`;
  3. reboot;
  4. wait for a new `boot_id`;
  5. wait up to 90 s for `sys.boot_completed=1`;
  6. count `FATAL EXCEPTION IN SYSTEM PROCESS` in `logcat -b crash`.

  `LOOP` means boot never completed and system_server crashed again and again. Each trial log is in `evidence/<trial>.txt`.
- **Reboot kinds:**
  - `adb`: `adb reboot`, which skips the framework shutdown.
  - `svc`: `adb shell svc power reboot`, which goes through `PowerManager.reboot` and the framework shutdown.
  - `kill`: `adb emu kill`, then a cold start. This is the "battery died" case.

Rebuild and run:

```
cd spikes/bootloop-repro/apps && ./gradlew :dpc:assembleDebug
bash spikes/bootloop-repro/tools/matrix.sh spikes/bootloop-repro/tools/matrix1.txt
```

## Trial table

TRIALS_TABLE

## Q1 Reproduce: yes

On API 35, unhiding `com.google.android.captiveportallogin` and running `adb reboot` 5 s later looped in t01 and t03. t04 booted normally. The 2 s case (t05) also looped.

This is the same stack as PLA-18 (`evidence/t01-stack.txt`):

```
FATAL EXCEPTION IN SYSTEM PROCESS: PackageManager
java.lang.IllegalStateException: Cannot broadcast before boot completed
  at ActivityManagerService.verifyBroadcastLocked(ActivityManagerService.java:16263)
  at BroadcastHelper.sendBootCompletedBroadcastToSystemApp(BroadcastHelper.java:264)
  at BroadcastHelper.lambda$sendPackageAddedForNewUsers$3(BroadcastHelper.java:656)
...
Failed to boot service DevicePolicyManagerService$Lifecycle: onBootPhase threw an exception during phase 500
Caused by: IllegalStateException: Cannot broadcast before boot completed
```

## Q2 Timing: about 10 s window; the AOSP constant is `WRITE_SETTINGS_DELAY`

- **Observed:**
  - LOOP at 2 s and 3 s.
  - LOOP in 2 of 3 trials at 5 s.
  - 8 s and 12 s: see the table.
  - BOOTED at 15 s, 20 s, 30 s and 60 s.
- **The constant:** `frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java` line 940 (android15-release) has `static final int WRITE_SETTINGS_DELAY = 10*1000;  // 10 seconds`.
  - The value is the same on android16-qpr2-release (L922) and main (L942).
  - `setApplicationHiddenSettingAsUser` ends with `scheduleWritePackageRestrictions(userId)` (around L5924). That method marks the user dirty and posts `WRITE_DIRTY_PACKAGE_RESTRICTIONS` after `WRITE_SETTINGS_DELAY` (L1590–1610).
  - The policy engine writes its file at once: `DevicePolicyEngine.write()` → `writeToFileLocked()`.
- **Why a 5 s reboot sometimes survives:** the delayed message is only posted if one is not already pending. If another package-restriction change happened just before, its pending write flushes our change sooner.
  - This is an inference from the code; I did not trace it per trial.
  - Either way, the window is "anywhere from 0 to 10 s", and it is not predictable.

## Q3 Reboot kind

- **`adb reboot`** (skips the framework shutdown): loops. See the table.
- **`svc power reboot`** (framework shutdown) at 2 s: **booted** (t09). The app was not hidden after boot.
  - The framework shutdown saves PackageManager's settings. Power-menu Restart and the low-battery shutdown take the same `ShutdownThread` path. That last point is from code reading; I did not test it.
- **Hard power-off** (`emu kill`, then a cold start) at 2 s: see the table, t16.
  - This is the realistic "battery pulled / force restart (hold power) / battery died without a clean shutdown" case.

## Q4 Direction: hiding is safe

`hide` then `adb reboot` 2 s later: **booted** (t10). After boot the app was hidden, because the engine re-applied it at boot without crashing.

The difference is in the broadcasts:

- Hiding sends PackageManager's package broadcasts, which are allowed before boot completes.
- Unhiding sends broadcasts that are not allowed before boot completes:
  - API 35: `sendBootCompletedBroadcastToSystemApp`;
  - API 36: `sendSessionCommitBroadcast`.

So first-time setup and the end of a session (re-hide) are safe. **Session start (unhide) is the dangerous step.**

## Q5 Which apps

All of these looped at 2 s (t11, t12):

- system apps: Settings (`com.android.settings`), Play Store (`com.android.vending`) and the sign-in app;
- user-installed app (`com.plainphone.spike.emu`): see the table, t13.

## Q6 What the user sees, and recovery

- **Screen:** I booted the emulator without the boot animation, so the screen was black (`evidence/q6-loop-screen.png`). On a real phone the boot animation would loop forever, because system_server restarts every few seconds (24 to 44 crashes in 90 to 150 s).
  - A non-technical user sees **a phone stuck on the startup logo**.
- **Rescue Party:** on these images Rescue Party is turned off (`persist.sys.disable_rescue=true`), and with a user build I cannot turn it back on without root. **Not observed.**
  - On a real phone, Rescue Party is expected to eventually show the recovery screen "Can't load Android system. Your data may be corrupt" with *Try again* / *Factory data reset*.
  - *Try again* cannot help, because the same mismatch is still on disk.
  - *Factory data reset* is a recovery wipe. Per TEAM-POLICY, that triggers FRP for whatever Google account was on the phone. (Expected behaviour from Rescue Party docs and code reading; I did not test it.)
- **Self-recovery:** none seen. The emulator never booted in up to 150 s.
  - From the code: the crash happens at phase 500. PackageManager's 10 s delayed write never runs before system_server dies, so the mismatch on disk is never fixed.
- **Safe mode:** not tested. Safe mode only disables third-party apps. DevicePolicyManagerService and the policy engine are system services and still run `reapplyAllPoliciesOnBootLocked` (code reading), so I expect no help.
- **Recovery short of a wipe:**
  - With root, you could edit `/data/system/users/0/package-restrictions.xml` or the policy-engine state file. Production phones have no root.
  - Without root, adb works during the loop (`getprop` and `logcat` worked), but `/data/system` is not readable (`Permission denied`), and `pm`/`dpm` need the services that are crashing.
  - Plainphone's planned `DISALLOW_DEBUGGING_FEATURES` would also remove adb.
  - **So in practice: a wipe.**
- **Emulator recovery:** `adb emu avd snapshot load <snapshot>` restored the emulator. That is not available on a phone.

## Q7 Other versions and newer AOSP

- **API 36 emulator** (`BE2A.250530.026.D1`): **loops** (a36-t01: unhide sign-in app, `adb reboot` at 2 s, 33 crashes).
  - The stack is the same, with the throw moved to `BroadcastController.verifyBroadcastLocked(BroadcastController.java:1898)` via `BroadcastHelper.sendSessionCommitBroadcast` ← `sendPackageAddedForUser` ← `setApplicationHiddenSettingAsUser` ← `PolicyEnforcerCallbacks.setApplicationHidden` ← `DevicePolicyEngine.reapplyAllPoliciesOnBootLocked(DevicePolicyEngine.java:1834)`.
  - See `evidence/a36-stack.txt`.
- **AOSP source** (android.googlesource.com frameworks/base; android15-release, android16-qpr2-release, main):
  - `reapplyAllPoliciesOnBootLocked` is still called from `PHASE_SYSTEM_SERVICES_READY` (phase 500) in DPMS: android15 L3347–3351, android16-qpr2 L3449–3452, main L3385–3388.
  - `APPLICATION_HIDDEN` (`PolicyDefinition.java` L280–290) lacks `POLICY_FLAG_SKIP_ENFORCEMENT_IF_UNCHANGED`, so it is always re-applied.
  - `PolicyEnforcerCallbacks.setApplicationHidden` has **no boot-completed check** on any branch.
  - The throw is `if (!mProcessesReady && (flags & FLAG_RECEIVER_REGISTERED_ONLY[_BEFORE_BOOT]) == 0) throw new IllegalStateException("Cannot broadcast before boot completed")`. On android15 it is in `ActivityManagerService.verifyBroadcastLocked` (L16258–16267); on main it is in `BroadcastController.java:1876`.
  - `mProcessesReady` is only set in AMS `systemReady()`, which runs after phase 500.
  - Nothing catches the exception: `SystemServiceManager.startBootPhase` wraps it and re-throws it.
  - **No fix found in android16-qpr2-release or main.**
  - **Unverified:** commit history. Gitiles `+log` returned 403, so I could not search for a bug ID or a pending fix.
  - Nobody has tested the API 37 image (`pp11-a37` exists).

## Q8 Mitigations

MITIGATIONS

## Emulator end state

END_STATE
