# App updates under Device Owner lockdown

Researched 2026-10-08. Primary sources only: AOSP `frameworks/base` at `refs/heads/main` (line numbers as fetched on that date), developer.android.com, developers.google.com, and support.google.com. Confidence labels: **documented** (official docs say so), **code-confirmed** (AOSP code shows it), **inferred** (follows from code or docs but nobody has tested it), **unknown**.

## Summary

- **`DISALLOW_INSTALL_APPS` blocks updates too, including the Device Owner's own installs.** Every `PackageInstaller.createSession()` on a restricted user fails before the system checks whether the package is new or an update. This has been true from Android 5.0 through `main`. Play auto-updates therefore stop. Play itself only reaches this check through `createSession`, so that part is inferred.
- **Hiding Play with `setApplicationHidden` kills Play and makes it unresolvable**, so nothing drives auto-updates. Suspending Play is probably impossible on GMS devices, because the required package verifier cannot be suspended.
- **No API means "updates only, no new installs."** `DISALLOW_INSTALL_UNKNOWN_SOURCES` blocks sideloading and non-privileged stores (F-Droid/Obtainium) but not Play. Android 14 update ownership controls *who* may update, not *whether* new installs happen.
- **A Device Owner can install and update APKs silently** (API 23+). Google's DPC guide forbids using this to install apps downloaded from Google Play. Managed Google Play is closed to us: no new custom-DPC registrations, and the Android Management API (AMAPI) is limited to commercial EMMs and uses Google's DPC.
- **Recommended approach:** don't set `DISALLOW_INSTALL_APPS` permanently. Keep Play enabled so it can auto-update in the background. Block the Play UI with lock task mode or launcher allowlisting, and remove or suspend any package not on the allowlist. Add a DPC-owned silent updater for non-Play (F-Droid/GitHub) apps. An emulator spike has to confirm this before we commit.

## Findings

### 1. DISALLOW_INSTALL_APPS: what it covers

**1a. The javadoc says it also blocks the Device Owner.**
- Quote: "Specifies if a user is disallowed from installing applications. This user restriction also prevents device owners and profile owners installing apps." The javadoc never mentions updates.
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/UserManager.java#477 (same text at https://developer.android.com/reference/android/os/UserManager#DISALLOW_INSTALL_APPS)
- Confidence: documented

**1b. It is enforced at session creation for every caller and every install mode.**
- Quote (`PackageInstallerService.createSessionInternal`):
  ```java
  if (mPm.isUserRestricted(userId, UserManager.DISALLOW_INSTALL_APPS)) {
      throw new SecurityException("User restriction prevents installing");
  }
  ```
  This runs before the code checks whether the package already exists, so it does not distinguish a new install from an update. It makes no exception for the Device Owner, Play, or other privileged installers. Identical code exists in the `android-5.0.0_r1`, `10.0.0_r1`, `14.0.0_r1`, and `15.0.0_r1` tags.
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageInstallerService.java#681
- Confidence: code-confirmed

**1c. `installExistingPackage` is also blocked.**
- Quote: `if (mPm.isUserRestricted(userId, UserManager.DISALLOW_INSTALL_APPS)) { return Pair.create(PackageManager.INSTALL_FAILED_USER_RESTRICTED, intentSender); }`
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/InstallPackageHelper.java#684
- Confidence: code-confirmed

**1d. There is one narrow update carve-out, and it does not help us.** It applies only to multi-user `INSTALL_ALL_USERS` installs: "If the app is already installed for the currentUser, keep it as installed as we might be updating the app ... If not currently installed, check if the currentUser is restricted by DISALLOW_INSTALL_APPS". The session still has to be created first, in an unrestricted user, before this code runs.
- Source: InstallPackageHelper.java#2610 (same file as 1c)
- Confidence: code-confirmed

**1e. What this means for Play.** Play auto-updates should fail while the restriction is set. Play's installs only reach the 1b check if Play installs through `createSession`, and that part is inferred. A vendor lead (Hexnode, not primary) says its "Install apps" toggle "also prevents auto updating of Play Store apps."
- Confidence: inferred (strong). Needs the spike (S1).

**1f. Google's own AMAPI docs say no more about updates.** The AMAPI equivalent field reads "installAppsDisabled: Whether user installation of apps is disabled." It does not say whether updates are affected.
- Source: https://developers.google.com/android/management/reference/rest/v1/enterprises.policies
- Confidence: documented (silent on updates)

**1g. Workaround: the Device Owner can lift the restriction around its own installs.** The DPC calls `clearUserRestriction`, installs, then calls `addUserRestriction` again. No document describes this pattern. Two side effects follow from the code: Play could install queued items during that window, and Play's normal background updates stay blocked the rest of the time.
- Confidence: inferred. Needs the spike (S2).

### 2. Hiding, suspending, or simply leaving Play off the launcher

**2a. Hiding Play kills it and removes it from package resolution.**
- Quote, javadoc: "When a package is hidden it is unavailable for use, but the data and actual package file remain."
- Quote, code: on hide, PMS calls `killApplication(packageName, ..., "hiding pkg", ...)` and sends the app-hidden broadcast.
- Quote, code: `isAvailable` returns false when the package is hidden, unless the caller passes `MATCH_UNINSTALLED_PACKAGES`.
- Result: hidden Play cannot run jobs or receivers, so Play auto-update stops. Play also updates Google Play services, so GMS would gradually go stale.
- Sources: DevicePolicyManager.java#12637; PackageManagerService.java#6004; https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/pkg/PackageUserStateUtils.java#100
- Confidence: code-confirmed for "not runnable"; inferred for "no auto-update"

**2b. Suspending Play is probably refused.**
- Quote, javadoc: "A suspended package will not be able to start activities ... Some apps cannot be suspended, such as ... the required package verifier".
- Quote, code: `if (packageName.equals(requiredVerifierPackage)) { Slog.w(TAG, "Cannot suspend package ...`
- On GMS devices, Play Store is expected to be the required verifier, but no doc confirms it. The javadoc does not say whether a suspended app's background jobs keep running.
- Sources: DevicePolicyManager.java `setPackagesSuspended` (~line 10100); https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/SuspendPackageHelper.java#548
- Confidence: code-confirmed rule; whether Play is the verifier is unknown (S3)

**2c. Leaving Play off the launcher keeps updates working, but other apps can still open it.** Play stays enabled, so background auto-update keeps working. However, allowed apps can open Play through `market://` links. Lock task mode closes that gap: "Only apps that have been allowlisted by a device policy controller (DPC) can run when the system is in lock task mode" and "Other apps can't launch new activities." Lock task restricts which activities can start. The docs never say it limits background work.
- Source: https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode
- Confidence: documented for blocking the UI; inferred that background updates continue (S4)

### 3. Update paths the DPC could own

**3a. Silent install by the Device Owner.**
- Quote: "admins on fully managed devices can install (or uninstall) packages without user interaction" (Android 6.0 / API 23+ for fully managed devices). Also: "The Google Play Terms of Service doesn't allow you to download and install apps from Google Play using these APIs."
- In code, `noUserActionNecessary` includes `isInstallerDeviceOwnerOrAffiliatedProfileOwner()`. It is evaluated *before* the update-ownership and unknown-sources checks.
- Sources: https://developer.android.com/work/dpc/dedicated-devices/cookbook ; PackageInstallerSession.java#1136
- Confidence: documented + code-confirmed. Silent DO install is still blocked by `DISALLOW_INSTALL_APPS` (see 1a and 1b).

**3b. Managed Google Play and AMAPI are effectively closed to us.**
- Quote: "We are no longer accepting new registrations for the Play EMM API... All new EMM solutions should now use Android Management API, which comes with its own DPC provided by Google." (page last updated 2025-11-13)
- Quote, AMAPI usage rules: use is "restricted solely to commercial Enterprise Mobility Management (EMM) developers"; providers must "Have a direct agreement with its end customers"; using it to build "solutions developed exclusively for the purpose of device or user monitoring" is prohibited. The quota starts at up to 500 devices, which requires a business justification.
- Result: a self-hosted, open-source DPC can't get Managed Play. Going through AMAPI would make Google's Android Device Policy the Device Owner, not Plainphone.
- Sources: https://developers.google.com/android/work/play/emm-api/register ; https://developers.google.com/android/management/permissible-usage
- Confidence: documented

**3c. Play Store managed configuration without an EMM.** No primary doc publishes Play Store managed-config keys for a non-EMM DPC.
- Confidence: undocumented. Needs an emulator test (S5).

**3d. F-Droid / Obtainium-style updaters.**
- A non-privileged store can update silently on API 31+ if all of these hold: `USER_ACTION_NOT_REQUIRED` is set, the installer is the installer of record or update owner, it holds `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, and the target app's SDK is new enough. The javadoc lists the target-SDK floor per Android version and warns it "will advance in future Android versions."
- Under `DISALLOW_INSTALL_UNKNOWN_SOURCES`, `isInstallDisabledForPackage()` returns true, so these stores are forced into user action, which the restriction then blocks.
- Better option: the DPC fetches F-Droid repo or GitHub release APKs itself and installs them as Device Owner (3a).
- Sources: PackageInstaller.java `setRequireUserAction` (~line 3480); ComputerEngine.java#3951; PackageInstallerSession.java#1157
- Confidence: documented + code-confirmed

**3e. OS updates.**
- Quote: "A device owner can control when updates are installed by setting a local system update policy" (API 26+; freeze periods API 28+, up to 90 days, with a mandatory 60-day buffer between freezes). Google Play system updates "are automatically downloaded but require a device reboot to be installed."
- The page never mentions app updates.
- Source: https://developer.android.com/work/dpc/system-updates
- Confidence: documented. Whether `DISALLOW_INSTALL_APPS` also blocks staged Mainline sessions is unknown (S6).

### 4. Is there an "updates yes, new installs no" switch?

**4a. No.**
- `DISALLOW_INSTALL_APPS` (API 18) blocks everything (1b).
- `DISALLOW_INSTALL_UNKNOWN_SOURCES` (API 18) blocks only "Unknown sources... exclude adb and special apps such as trusted app stores." Privileged installers like Play skip the check (`isPermissionGranted` returns before it), so Play can still install *new* apps.
- `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` (API 29) is the device-wide version.
- `DISALLOW_UNINSTALL_APPS` and `setUninstallBlocked` only block removal.
- Sources: UserManager.java#611–651; PackageInstallerSession.java#1136–1170
- Confidence: code-confirmed

**4b. Update ownership (API 34).**
- Quote: "Once the update ownership enforcement is enabled, the other installers will need the user action to update the package even if the installers have been granted the INSTALL_PACKAGES permission... can only be enabled on initial installation." It needs `ENFORCE_UPDATE_OWNERSHIP`, a normal permission.
- Device Owner installs bypass the check (it comes after `noUserActionNecessary`). This decides *who* may update an app. It does not block new installs.
- Source: PackageInstaller.java#3549; core/res/AndroidManifest.xml (`ENFORCE_UPDATE_OWNERSHIP`, protectionLevel normal)
- Confidence: documented + code-confirmed

**4c. Other DPM APIs.**
- `setUserControlDisabledPackages` (API 30) stops users from force-stopping or clearing data, and exempts those apps from standby buckets. It has nothing to do with installs.
- `setKeepUninstalledPackages` / `installExistingPackage` (API 28) handle APK caching and re-enabling. Both are blocked by `DISALLOW_INSTALL_APPS` (1c).
- No DPM method takes an allowlist of installable packages.
- Confidence: documented

## Recommendation (ranked)

1. **Play stays enabled but locked away, plus remove anything not on the allowlist.** Never set `DISALLOW_INSTALL_APPS` permanently. Keep Play enabled and signed in so it can auto-update allowed apps and GMS. Block the Play UI with lock task mode, or with a persistent preferred activity that catches `market://`. On every package add, the DPC silently uninstalls or hides anything not on the allowlist (DO uninstall is silent, PackageInstallerService.java#1458). Set `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` to block sideloading.
   - Risk: remove-after-install is reactive. Lock task depends on the launcher holding the task.
2. **DPC-owned updater for non-Play apps** (F-Droid repo index, GitHub releases, or a Plainphone manifest) using silent Device Owner installs. Do this alongside #1.
3. **Permanent `DISALLOW_INSTALL_APPS` with the DPC lifting it during updates.** This is the strongest lockdown, but Play and GMS updates die unless the DPC also opens a scheduled window for Play. That needs S1 and S2 to be viable.
4. **AMAPI / Managed Play:** not available to us (3b).

## Spike needed

Setup: an emulator on an API 34 or 35 Google Play image, Play signed in with a test account, and the DPC test APK installed.
```
adb shell dpm set-device-owner com.plainphone.dpc/.AdminReceiver
```
Before each test, install an older version of a small Play app and turn on Play's auto-update.

- **S1. Does `DISALLOW_INSTALL_APPS` block Play updates?**
  1. Have the DPC call `addUserRestriction(DISALLOW_INSTALL_APPS)`.
  2. Trigger the update: open Play > Manage apps > Update, and also wait for auto-update (`adb shell cmd jobscheduler run -f com.android.vending <id>` after listing jobs with `dumpsys jobscheduler | grep vending`).
  3. Watch `adb logcat | grep -E "User restriction prevents installing|PackageInstaller|Finsky"`.
  4. Expected: SecurityException, and the version code doesn't change (check with `dumpsys package <pkg> | grep versionCode`).
  5. Repeat with `adb install -r`.
- **S2. Device Owner install while restricted.**
  1. With the restriction set, have the DPC install an APK and confirm the SecurityException.
  2. Then try the sequence clear → install → re-add, and confirm it updates silently.
- **S3. Can Play be suspended?**
  1. Run `adb shell dumpsys package | grep -A3 -i "verifiers"`.
  2. Have the DPC call `setPackagesSuspended([com.android.vending], true)`. A non-empty return array means it was refused.
  3. If it was suspended, repeat S1's update trigger to see whether background updates still run.
- **S4. Hidden Play vs Play blocked by lock task.**
  1. `setApplicationHidden(vending, true)`, then try triggering an update. Expect nothing happens. Check `dumpsys package com.google.android.gms | grep versionName` over time.
  2. Unhide Play, start lock task with only the Plainphone launcher and test app allowlisted, then open a `market://details?id=...` link from the test app. The activity should be blocked, and background auto-update should still complete.
- **S5. Play managed configuration.** Have the DPC call `getApplicationRestrictions` and `setApplicationRestrictions("com.android.vending", ...)`, then check whether Play reacts (logcat Finsky). This is low priority.
- **S6. Mainline / Play system updates.** With `DISALLOW_INSTALL_APPS` set, trigger Settings > Security > Google Play system update, then check `adb shell pm get-stagedsessions` and logcat for the restriction exception.
- **S7. Remove-after-install race.** With Play reachable, install a new app via `adb shell am start -a android.intent.action.VIEW -d market://details?id=...`. Measure the delay before the DPC removes it, and whether the app can run anything first.
