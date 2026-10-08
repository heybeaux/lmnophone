# PLA-5 device spike on the spare Motorola (THROWAWAY)

> **Throwaway spike.** Not MVP code. The MVP proposal is not approved. Everything under
> `spikes/device-moto/` exists only to answer feasibility questions Q1, Q2, Q3, Q5, Q6, Q7, Q13.

- Device: moto g power 5G (2024), XT2415-1, adb serial `SPARE_DEVICE_SERIAL`, board-approved (TEAM-POLICY Gates §2).
- Build fingerprint: `motorola/cancunn_g_sys/cancunn:15/V1UDS35H.26-14-6-2-11/27ec35b-72bc9:user/release-keys`
- Driver: Android Platform Engineer only. Every adb command uses `-s SPARE_DEVICE_SERIAL` (the helper
  `tools/pp.sh` defaults to it and refuses `emulator-5590` / `emulator-5604`).
- Spike code:
  - `dpc/app`: spike DPC `com.plainphone.spike.dpc`, built with `android:testOnly="true"`.
    Home screen, in-app **RELEASE** button (tap twice), and a DUMP-protected adb command hook (`Commands.kt`).
  - `dpc/target`: tiny app we own (`com.plainphone.spike.target`), v1 and v2, used as an update source for S2.
  - `tools/pp.sh`: adb helpers (`cmd`, `top`, `shot`, `tap`, `ui`, `log`).
- Build: `cd dpc && ./gradlew :app:assembleDebug :target:assembleDebug` (AGP 8.11.0, Kotlin 2.1.20, compileSdk 35).

## Status

| Item | Result | Notes |
|---|---|---|
| A0 baseline | yes | Pre- and post-reset baselines recorded. Trusted reset done 2026-10-08 19:54Z through Settings. After setup, 8 third-party apps installed themselves with no account (see A0). |
| A1 Q3 adb Device Owner | **yes** | Unapproved testOnly DPC set as Device Owner over adb on a certified Motorola. No allowlist message. |
| A2 way back | **yes** | `remove-active-admin` and the in-app RELEASE both return the phone to `no owners` with no wipe; provisioning again works each time. |
| A3 Q1 lock task | **partly** | Launch matrix done for BLOCK off/on with a no-lock-task control. Reboot check incomplete: adb became `unauthorized` after the reboot (see A3). Power menu not shown via adb injection. |
| A4–A7 | not yet run | Need adb back first. |
| S1–S4, S7 | not yet run | Phase B, needs Beaux's sign-in. |

## A0. Baseline

**Result: yes** (pre-reset baseline, trusted reset, post-reset baseline).

Recorded 2026-10-08T19:27Z, before any change. Raw files: `evidence/a0/`.

| Property | Value |
|---|---|
| `ro.build.fingerprint` | `motorola/cancunn_g_sys/cancunn:15/V1UDS35H.26-14-6-2-11/27ec35b-72bc9:user/release-keys` |
| `ro.build.version.security_patch` | 2026-08-01 |
| Android / SDK | 15 / 35 (`ro.product.first_api_level`=34) |
| `ro.boot.verifiedbootstate` | green |
| `ro.boot.flash.locked` | 1 (`ro.boot.vbmeta.device_state`=locked) |
| `sys.oem_unlock_allowed` | 0 |
| `ro.carrier` | retca (Canadian retail) |
| `gsm.sim.state` | `ABSENT,LOADED` (no physical SIM; the second slot reports LOADED, but telephony shows OUT_OF_SERVICE / emergency-only) |
| Users | `UserInfo{0:Owner:4c13} running` (one user) |
| Accounts | `Accounts: 5` (count only; names not read) |
| `dpm list-owners` | `no owners` |
| Lock screen | `CredentialType: PIN` (from `dumpsys lock_settings`) |
| Preinstalled (system) packages | 467 (`evidence/a0/pre-reset-system-packages.txt`, with APK paths); 118 of them disabled |
| Role holders | HOME `com.motorola.launcher3`, DIALER `com.google.android.dialer`, SMS `com.google.android.apps.messaging` |

Commands:
```sh
adb -s SPARE_DEVICE_SERIAL shell getprop ro.build.fingerprint   # and the other props above
adb -s SPARE_DEVICE_SERIAL shell pm list users
adb -s SPARE_DEVICE_SERIAL shell dumpsys account | grep -E '^\s*Accounts:'
adb -s SPARE_DEVICE_SERIAL shell dpm list-owners
adb -s SPARE_DEVICE_SERIAL shell dumpsys lock_settings | grep CredentialType
adb -s SPARE_DEVICE_SERIAL shell pm list packages -s -f
adb -s SPARE_DEVICE_SERIAL shell cmd role get-role-holders android.app.role.HOME
```

Motorola packages from the evidence file that are present on this build (pre-reset):
`com.motorola.securevault` (Secure folder), `com.motorola.securityhub` (Moto Secure), `com.motorola.moto`,
`com.motorola.actions`, `com.motorola.gamemode`, `com.motorola.freeform`, `com.motorola.spaces`,
`com.motorola.om`, `com.motorola.brapps` (App Box), `com.aura.oobe.motorola` (MotoApps),
`com.glance.lockscreenM` (Glance), `com.motorola.mobiledesktop.core` (Smart Connect core),
`com.motorola.android.overlay.appclone` / `com.motorola.android.settings.overlay.appclone` (Cloned Apps overlays),
`com.motorola.genie`, `com.motorola.help`, `com.android.chrome`.
Not found: `com.motorola.motodisplay`, `com.motorola.playautoinstallext`.

### Trusted reset (2026-10-08, PLA-5 run after board comment db927708)

The board supplied the screen-lock PIN in an owner-only local file. The agent typed it with
`adb shell input text "$(cat <file>)"`, so the PIN was never printed or logged, and it is not recorded here.
Path: **Settings > System > Reset options > Erase all data (factory reset) > Erase all data > PIN > Erase all data**.
No recovery mode. Screenshots: `evidence/a0-reset/` (the PIN pad is `FLAG_SECURE`, so `01-pin-pad.png` is black).

- First attempt did nothing: the phone was in `mWakefulness=Dozing` and the digits never reached the bouncer
  (no `LockSettingsService` entry). Fix: `adb shell svc power stayon usb`, wake, swipe up, check with
  `uiautomator dump` that `keyguard_pin_view` is showing, then type. Result: `Successfully verified lockscreen credential for user 0`.
- The "Erase all data" confirmation screen lists the signed-in Google accounts. They appeared in one `uiautomator dump`.
  The screenshot of that screen was deleted, and the names are not recorded anywhere.
- Logcat at confirm (`evidence/a0-reset/reset-logcat.txt`):
  `PersistentDataBlockService: persistent partition now wiped and unwritable`,
  `MasterClear: !!! FACTORY RESET !!!`,
  `RecoverySystem.rebootWipeUserData(..., reason=MainClearConfirm, forceWipe=false, wipeEsims=false, ...)`.
  No eSIM checkbox was shown and eSIMs were not wiped.
- adb dropped at 12:55 PDT and came back at 13:03 PDT as `unauthorized`, then `device` once someone tapped Allow on
  the phone. A person at the phone completed the setup wizard and turned on USB debugging. They also turned on
  **Wireless debugging** (`adb_wifi_enabled=1`).

### Post-reset baseline (2026-10-08T20:05Z)

Raw: `evidence/a0-reset/post-reset-baseline.txt`, `post-reset-system-packages.txt`.

| Property | Value |
|---|---|
| Fingerprint, patch, verified boot, flash.locked | unchanged (same as above) |
| `sys.oem_unlock_allowed` | **1** (was 0 before the reset; probably because the FRP data was cleared. Checked in A4.) |
| `settings global boot_count` | 1 (fresh `/data`); uptime 9 min |
| Users / accounts / owners | 1 user (`0:Owner`), `Accounts: 0`, `no owners` |
| Lock screen | `CredentialType: NONE` (the reset removed the PIN; not set again) |
| System packages | 467, 118 disabled, identical list to before the reset |
| HOME role | `com.motorola.launcher3` |

**Finding: apps install themselves after setup, with no Google account.** Within 8 minutes of first boot:

| Package | Installer | First install (PDT) |
|---|---|---|
| `com.facebook.katana` | `null` (preload) | 12:57:13 |
| `com.block.juggle` | `com.dti.motorola` | 13:02:17 |
| `com.hungrystudio.mahjong` | `com.dti.motorola` | 13:03:37 |
| `com.google.android.apps.subscriptions.red` (Google One) | `com.android.vending` | 13:03:46 |
| `com.easybrain.crossword.puzzles` | `com.dti.motorola` | 13:04:00 |
| `com.google.android.apps.fitness` | `com.android.vending` | 13:04:08 |
| `com.google.android.apps.walletnfcrel` | `com.android.vending` | 13:04:17 |
| `com.peoplefun.wordcross` | `com.dti.motorola` | 13:04:31 |

(`evidence/a0-reset/post-reset-autoinstalls.txt`; command:
`adb -s SPARE_DEVICE_SERIAL shell dumpsys package <pkg> | grep -E 'installerPackageName|firstInstallTime'`.)
`com.dti.motorola` is Digital Turbine Ignite, Motorola's carrier/OEM app-push service. Play also pushed Google apps
(Play Auto Installs) with no account. For Plainphone, this means the DPC must hide or block installs **after**
provisioning and cannot rely on a "clean" phone after setup. Relevant to A5 (Q13) and S7.

### Pre-check on our own emulator (not the phone)

Before touching the phone, the spike DPC was checked on a throwaway AVD we created for this
(`pp-spike-pla5-api35`, Google Play image API 35, `emulator-5640`). This only checks the spike code works;
it is **not** evidence for any device question.
- `adb install -t` → `dpm set-device-owner` → `Success: Device owner set to package com.plainphone.spike.dpc/.AdminReceiver`
- `dpm remove-active-admin com.plainphone.spike.dpc/.AdminReceiver` → `Success: Admin removed …`, `dpm list-owners` → `no owners`
- Provision again, hide an app, add `no_add_user`, then `release` → `RELEASED deviceOwner=false; cleared no_add_user; unhid com.plainphone.spike.target`
- Provision again, lock task on, in-app RELEASE button tapped twice → lock task exits, `dpm list-owners` → `no owners`

## A1. Q3: adb setup of an unapproved DPC on a certified Motorola

**Result: yes.** Fingerprint `motorola/cancunn_g_sys/cancunn:15/V1UDS35H.26-14-6-2-11/27ec35b-72bc9:user/release-keys`.
Raw: `evidence/a1/a1-set-device-owner.txt`, `evidence/a1/a1-logcat.txt`. Run 2026-10-08T20:08Z, right after the reset
(0 accounts, 1 user, setup wizard finished).

```sh
adb -s SPARE_DEVICE_SERIAL install -t dpc/app/build/outputs/apk/debug/app-debug.apk
#   Success
adb -s SPARE_DEVICE_SERIAL shell dpm set-device-owner com.plainphone.spike.dpc/.AdminReceiver
#   Success: Device owner set to package com.plainphone.spike.dpc/.AdminReceiver
#   Active admin set to component com.plainphone.spike.dpc/.AdminReceiver
adb -s SPARE_DEVICE_SERIAL shell dpm list-owners
#   User  0: admin=com.plainphone.spike.dpc/.AdminReceiver,DeviceOwner,Affiliated
```
Logcat: `I/DevicePolicyManager: Device owner set: ComponentInfo{com.plainphone.spike.dpc/...AdminReceiver} on user 0`.
No allowlist, Play Integrity or "unsupported DPC" message appeared anywhere in logcat.

Scope of this answer: it covers the **adb** path only (`dpm set-device-owner` after setup with zero accounts).
It says nothing about QR or `afw#` provisioning in the setup wizard, which this spike did not test.
Precondition seen in practice: the Digital Turbine and Play auto-installs (A0) don't block it, because they add apps, not accounts.

## A2. Way back

**Result: yes.** Raw: `evidence/a2/a2-way-back.txt`, screenshots `evidence/a2/`.

| Step | Command / action | Observed |
|---|---|---|
| 1 | `adb -s SPARE_DEVICE_SERIAL shell dpm remove-active-admin com.plainphone.spike.dpc/.AdminReceiver` | `Success: Admin removed …`; `dpm list-owners` → `no owners` |
| 2 | `dpm set-device-owner …` | `Success: Device owner set …` |
| 3 | spike: `hide com.block.juggle`, `restrict-add no_add_user` | `hide … -> true`, `OK added no_add_user` |
| 4 | in-app **RELEASE** tapped twice within 3 s (`tools/release-tap.sh`) | `RELEASED deviceOwner=false; cleared no_add_user; unhid com.block.juggle`; `no owners`; `com.block.juggle` listed again; `no_add_user` gone from `dumpsys user` |
| 5 | `dpm set-device-owner …` | `Success`; status `restrictions=[] hidden=[]` |

`remove-active-admin` works because the DPC is `TEST_ONLY` (`dumpsys package` flags `[ DEBUGGABLE … TEST_ONLY … ]`).
A production DPC would not be `testOnly`, so only the in-app path (`clearDeviceOwnerApp()`) or a wipe would be available there.

Hiccups (not device findings): the first in-app attempt did nothing because the screen was off (the reset cleared
"stay awake"; fixed with `adb shell svc power stayon usb`). The second missed the 3 s confirm window because a
`uiautomator dump` between taps takes longer than 3 s; `tools/release-tap.sh` finds the button once and taps it twice.

### Tested "return to an unmanaged phone" procedure

1. Over adb (spike DPC only, because it is testOnly):
   `adb -s SPARE_DEVICE_SERIAL shell am broadcast -n com.plainphone.spike.dpc/.CommandReceiver -a com.plainphone.spike.CMD --es cmd release`
   (clears restrictions, unhides apps, ends lock task, calls `clearDeviceOwnerApp()`), then
   `adb -s SPARE_DEVICE_SERIAL shell dpm list-owners` → `no owners`.
2. If that fails: `adb -s SPARE_DEVICE_SERIAL shell dpm remove-active-admin com.plainphone.spike.dpc/.AdminReceiver`.
   Note this does **not** clear restrictions or unhide apps first, so prefer step 1.
3. Without adb: open the spike app (it is the home screen while provisioned) and tap **RELEASE** twice within 3 s.
4. Then `adb -s SPARE_DEVICE_SERIAL uninstall com.plainphone.spike.dpc`.
5. Last resort only: Settings > System > Reset options > Erase all data. Never recovery mode.

## A3. Q1 on the device: lock task

**Result: partly.** Fingerprint unchanged. Run 2026-10-08 20:15–20:32Z. Raw: `evidence/a3/` (`*/matrix.tsv`,
`*/checks.txt`, `*/probes.txt`, one screenshot per case). Scripts: `tools/a3-matrix.sh`, `tools/a3-probe.sh`.

Setup (spike commands; see `Commands.kt`):
```sh
tools/pp.sh cmd "lt-packages com.google.android.dialer,com.google.android.apps.messaging,com.motorola.camera3"
tools/pp.sh cmd "lt-features HOME|GLOBAL_ACTIONS|NOTIFICATIONS|SYSTEM_INFO"        # BLOCK off
tools/pp.sh cmd "lt-features HOME|GLOBAL_ACTIONS|NOTIFICATIONS|SYSTEM_INFO|BLOCK"  # BLOCK on
tools/pp.sh cmd home-set      # spike is the persistent preferred HOME
tools/pp.sh cmd lt-start      # MainActivity.startLockTask(); dumpsys shows mLockTaskModeState=LOCKED
```
Every launch is made **from the spike's own activity** (an allowed app) via `tools/pp.sh cmd "launch <spec>"`.
"same task" = no `FLAG_ACTIVITY_NEW_TASK` from an Activity context.

### Launch matrix

| Case | Control (no lock task) | Lock task, BLOCK **off** | Lock task, BLOCK **on** |
|---|---|---|---|
| Settings (`android.settings.SETTINGS`), new task | opened | blocked (spike stays) | BlockedAppActivity |
| Settings, same task | opened | blocked | BlockedAppActivity |
| Settings explicit component, new task | opened | blocked | BlockedAppActivity |
| Settings explicit component, same task | opened | blocked | BlockedAppActivity |
| **App info** (`APPLICATION_DETAILS_SETTINGS` for Phone), same task | opened | **opened** (`settings/.spa.SpaActivity`) | BlockedAppActivity |
| Internet panel, new / same task | opened (SystemUIDialog) | blocked | BlockedAppActivity |
| Volume panel, same task | opened | blocked | BlockedAppActivity |
| Play `market://details`, new / same task | opened | blocked | BlockedAppActivity |
| Play explicit component, new / same task | opened | blocked | BlockedAppActivity |
| Browser `https://example.com`, same task | opened (Chrome first run) | blocked | BlockedAppActivity |
| **Share chooser** (`ACTION_SEND` text, `createChooser`) | opened | **opened** (`intentresolver`) | BlockedAppActivity |
| Share direct to Messages (`setPackage`) | opened | opened (allowed app) | opened (allowed app) |

"blocked" with BLOCK off means nothing visible happens and the spike stays on top. With BLOCK on, the system shows
`android/com.android.internal.app.BlockedAppActivity` ("App is not available").

**App info leak (BLOCK off).** From the App info screen opened inside the locked task:
the "open" icon opens Phone (allowed); **Permissions** opens `permissioncontroller/.ManagePermissionsActivity`;
**Notifications** opens `settings/.SubSettings`; the up arrow returns to the spike. Lock task stayed `LOCKED`
throughout. So it is a foothold in Settings/permissions, but no route to a non-allowed app was found in these probes.
Deeper paths are left for the Verifier.

### In-use checks

| Check | BLOCK off | BLOCK on |
|---|---|---|
| Runtime permission prompt (spike asks RECORD_AUDIO) | **works** (`GrantPermissionsActivity`, "Allow Plainphone spike to record audio?") | **blocked**: "Permission controller is not available right now." Camera's first-run prompt is blocked too. |
| Emergency dialer **screen** from home (`com.android.phone.EmergencyDialer.DIAL`), `com.android.phone` not allowlisted | blocked silently | BlockedAppActivity |
| Same, with `com.android.phone` added to lock task packages | **opens** (`com.android.phone/.EmergencyDialer`); backed out without dialing | not re-run |
| Camera opens (allowed) | yes | yes (then its permission prompt is blocked) |
| Take a photo | **yes**: `DCIM/Camera/IMG_20261008_132841212.jpg` (2.2 MB, MediaStore row) | not run (permission prompt blocked) |
| Camera thumbnail | **opens Google Photos** (`photos/.pager.HostPhotoPagerActivity`), not allowlisted, inside the locked task | not run |
| Share Camera → Messages (via Photos > Share > Messages) | **fails**: chooser opens, tapping Messages logs `ChooserRefinement: Refinement IntentSender failed to send` (`share-refinement-logcat.txt`); image never reaches Messages | not run |
| Notification shade (`cmd statusbar expand-notifications`) | opens, notifications visible | (feature-controlled, same flags) |
| Quick Settings (`cmd statusbar expand-settings`) | **no tiles**: only the notification list shows | – |
| Tap a notification that targets a non-allowed app (Play services update; "Finish setting up") | blocked, spike stays | – |
| Power menu (`input keyevent --longpress` / `--duration 1500 KEYCODE_POWER`) | no menu appeared; injected key may not trigger it. **Not tested**: needs the physical button. | – |
| Emergency dialer from the **lock screen** | **not tested**: no secure lock screen after the reset, and the board asked not to set a PIN again | – |
| Calls / SMS | **not tested**: no SIM | – |
| Reboot | **incomplete**, see below | – |

### Reboot (incomplete)

`adb -s SPARE_DEVICE_SERIAL reboot` at 20:32:26Z with lock task on (BLOCK off, `com.android.phone` allowlisted).
After boot adb shows **`unauthorized`**: the Mac's adb key was allowed during setup without "Always allow from this
computer", so the approval did not survive the reboot. Whether lock task came back, and whether the "Allow USB
debugging?" prompt can show while lock task is active, needs someone to look at the phone. Asked on PLA-5.

### What this means for Plainphone (spike-level, not a spec)

- BLOCK on is the only setting here that closes App info, the share chooser and the Photos leak. But it also blocks
  runtime permission prompts, so the DPC must **pre-grant** permissions (`setPermissionGrantState` /
  `setPermissionPolicy`). Not yet tested on the device.
- BLOCK off leaks App info → Permissions/Notifications settings, the share chooser and Google Photos.
- The emergency dialer needs `com.android.phone` on the lock task allowlist.
- Photo sharing to Messages needs our own path (a direct `ACTION_SEND` with `setPackage(Messages)` works);
  the Camera → Photos → Share route fails in lock task.
