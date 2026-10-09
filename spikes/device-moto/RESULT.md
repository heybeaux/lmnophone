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
| A0 baseline | yes | Pre- and post-reset baselines recorded. Trusted reset done 2026-10-08 19:54Z through Settings. After setup, with no account, 7 apps were pushed (4 by Digital Turbine, 3 by Play) and 1 preload appeared (see A0). |
| A1 Q3 adb Device Owner | **yes** | Unapproved testOnly DPC set as Device Owner over adb on a certified Motorola. No allowlist message. |
| A2 way back | **yes** | `remove-active-admin` and the in-app RELEASE both return the phone to `no owners` with no wipe; provisioning again works each time. |
| A3 Q1 lock task | **partly** | Launch matrix done for BLOCK off/on with a no-lock-task control. Reboot: lock task comes back by itself. A non-always-allowed adb key **cannot be re-authorized while lock task is on**. Not tested: power menu (needs the physical button), lock-screen emergency, calls/SMS. |
| A4 Q5 baseline escapes | **partly** | Factory reset, OEM unlock, add user and Quick Settings are closed by their controls. `no_config_default_apps` does **not** stop a Home-app change (the DPC HOME pin does). **A DPC crash ends lock task and drops to the Moto launcher, unlocked, until Home is pressed.** Safe mode not tested (needs the physical buttons). |
| A5 Q13 Motorola extras | **yes** | 20 extras + 3 Facebook installer packages hide cleanly; hiding breaks nothing core; BLOCK off leaks same-task starts of 6 of them until hidden. Hiding the Moto launcher closes the A4 crash escape (≥60 s → ~0.5 s). Clone/private/managed profiles are blocked by DO base restrictions. Gestures not tested (physical). |
| A6 Q6 reboot survival | **yes** | 21 hidden apps, 4 restrictions and the HOME pin all survive a reboot. `no_config_default_apps` never takes effect on this build. |
| A7 Q7 release without wipe | **yes** | `clearDeviceOwnerApp()` from inside lock task: no owners, all unhidden, restrictions cleared, no wipe, both test photos byte-identical (MD5). |
| Phase B sign-in | done | **Test account, signed in by the agent** (board comment 9a1887c6), not Beaux's personal account. No 2-step prompt. All 7 sync items turned off. |
| S1 `DISALLOW_INSTALL_APPS` vs Play | **yes, it blocks** | Play auto-updates fail with `SecurityException: User restriction prevents installing` (Finsky status 5402); `adb install -r` also fails. |
| S2 clear → install → re-add | **yes** | DO install while restricted fails at `createSession`; clear → silent install (588 ms, no prompt) → re-add: about 0.83 s window on the phone. |
| S3 suspend Play | **no** | `setPackagesSuspended([com.android.vending])` is refused: Play is the required package verifier. |
| S4 hidden Play vs lock task | **lock task: yes; hidden: no** | Hidden Play: its jobs can't start, so nothing updates. Lock task with Play unhidden: `market://` is blocked, and **23 apps updated in the background within 6 min**. |
| B-Q1 lock task follow-ups (BLOCK on) | **yes** | Pre-grant works for camera, mic and location; Camera → Photos is closed; the emergency dialer screen opens; the image attaches through Messages' own picker; lock task comes back after a reboot. |
| B-Q4 must-have apps | **partly**: before sign-in done; signed-in checks in PLA-20 | All 5 apps probed under BLOCK before sign-in. **Outlook's in-app WebView browses off-site from its Privacy link.** Google sign-in in Slack/Spotify/Outlook needs GMS → blocked. Telegram loops until pre-granted. Signed-in checks wait for Beaux. |
| S7 install race | **partly** | DPC hid a newly installed app 4.6 s after `firstInstallTime`. Deep links failed for ~4 s (package frozen), but the app's **process started ~0.35 s before the hide**; no window was displayed. |

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

**Finding: apps install themselves after setup, with no Google account.** Within 8 minutes of first boot,
**7 apps were pushed and 1 preload appeared** (8 rows below):
- **4 games** pushed by `com.dti.motorola`;
- **3 Google apps** pushed by `com.android.vending`;
- **1 preload**, `com.facebook.katana`, with installer `null`.

*Count correction (PLA-7 R8):* my 20:07 issue comment said "8 installs, including 5 Digital Turbine games", and a run
summary said 9. Both were wrong. The recorded evidence names only the 8 packages below, with 4 games. I recorded no
fifth game, and this snapshot is the only post-reset install list taken.
Wi-Fi was already joined during setup, so this snapshot can't separate what arrives with no network from what
arrives only after Wi-Fi joins.

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

### Later snapshot: more pushes after the A0 table (recorded 2026-10-08T21:36Z, `evidence/a5/third-party-all.tsv`)

The A0 table above was taken at about 13:05 PDT. Listing **all** third-party packages later (`pm list packages -3 -u`
plus `dumpsys package <pkg>` installer and `firstInstallTime`) shows that pushes kept arriving until 13:08:11,
**before** Device Owner was set at 13:08:20:

| Package | Installer | First install (PDT) |
|---|---|---|
| `com.king.candycrushsodasaga` | `com.dti.motorola` | 13:06:49 |
| `com.google.android.apps.chromecast.app` (Google Home) | `com.android.vending` | 13:06:59 |
| `com.google.android.apps.adm` (Find My Device) | `com.android.vending` | 13:07:30 |
| `com.telus.checkup` | `com.android.vending` | 13:07:37 |
| `com.babylon.telushealth` | `com.android.vending` | 13:07:39 |
| `ai.perplexity.app.android` | `com.android.vending` | 13:07:52 |
| `com.inmobi.weather` | `com.android.vending` | 13:08:11 |

**Reconciled total (PLA-7 R8):** **14 pushed + 1 preload** within about 11 minutes of first boot, with no Google account:
- **5** Digital Turbine games: the four above plus Candy Crush Soda Saga. This also matches the "Candy Crush Soda Saga
  Installed" notification in `evidence/a3/locktask-block-off/shade-quicksettings.png`.
- **9** Play pushes: Google One, Fit, Wallet, Google Home, Find My Device, TELUS CheckUp, TELUS Health, Perplexity, InMobi Weather.
  These are carrier/OEM Play Auto Installs, with TELUS branding on a `retca` build.
- **1** preload: Facebook (installer `null`), plus the hidden-by-default `com.facebook.appmanager/system/services`.

The earlier "7 pushed + 1 preload" was correct only for the 13:05 snapshot.

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

### Reboot

**Run 1 (20:32:26Z), key not always-allowed.** `adb -s SPARE_DEVICE_SERIAL reboot` with lock task on (BLOCK off,
`com.android.phone` allowlisted). After boot adb showed `unauthorized`: the Mac's key had been allowed during setup
without "Always allow from this computer".
- Board observation from the phone (PLA-5 comment 4fe1cf66, Beaux via Scout): **lock task came back** after the
  reboot. Beaux couldn't leave the spike screen or reach Developer options. The "Allow USB debugging?" prompt never
  authorized the Mac, from 13:46 to 13:52 PDT, including after `adb reconnect`. Mac key fingerprint
  `AC:1D:CA:40:CA:66:86:F5:2F:58:BD:34:78:4A:EF:46`.
- Device-side record (`evidence/a3/reboot/usb-debugging-activity-events.txt`, from `pm dump com.android.systemui`
  usage events), all times PDT:
  - `com.android.systemui.usb.UsbDebuggingActivity` RESUMED, PAUSED and STOPPED all at 13:33:04 (first boot under lock task);
  - RESUMED 13:51:39, PAUSED 13:51:41; RESUMED 13:51:44, PAUSED and STOPPED 13:51:45, while Beaux was tapping;
  - the spike's `MainActivity` RESUMED at 13:51:45.
  
  So the dialog **does appear** under lock task, in its own SystemUI task, but it's paused within 1–2 s and the key
  never gets authorized. *Not proven:* whether the system pushes it out of lock task, or the Allow tap is filtered.
  The logcat from that boot rotated away before adb came back.
- Recovery: Beaux tapped **RELEASE** twice (A2 path 3). At 21:04Z: `no owners`, `mLockTaskModeState=NONE`,
  0 accounts, still 1 user (no wipe). `AdbDebuggingManager: Received a request to clear the adb authorizations`
  at 13:58:05 (revoked from Developer options), then this Mac was allowed with "Always allow". `dumpsys adb` now lists
  the key in `user_keys` and the keystore with `lastConnection` (`evidence/a3/reboot/dumpsys-adb-after-always-allow.txt`,
  keys and BSSID masked).
- Side note: after RELEASE the HOME role holder stayed `com.plainphone.spike.dpc` (`cmd role get-role-holders android.app.role.HOME`),
  because only the persistent preferred activity is cleared. A production release path should restore the previous launcher.

**Run 2 (21:05:06Z), key always-allowed.** Re-provisioned (`Success: Device owner set …`), same lock task config, `lt-start`,
then `adb reboot` (`evidence/a3/reboot/repeat.txt`, screenshot `after-reboot-repeat.png`):
- adb authorized again with no prompt: `device` at 21:05:42Z, `sys.boot_completed=1` at the same poll (36 s after the reboot command).
- At 21:06:19Z (uptime 1 min, `boot_count` 3): `mLockTaskModeState=LOCKED`, top = `com.plainphone.spike.dpc/.MainActivity`,
  `isKeyguardShowing=false`, Device Owner intact, policy unchanged. **Lock task comes back by itself after a reboot.**
- Negative check after reboot: launching Settings from the spike is still blocked
  (`Attempted Lock Task Mode violation r=… com.android.settings/.homepage.SettingsHomepageActivity`).
- Also seen at boot: `Attempted Lock Task Mode violation r=… com.android.settings/com.motorola.settings.connecteddevice.usb.UsbBottomChooseActivity`.
  Motorola's USB-mode chooser, which pops up when a cable is plugged in, is blocked under lock task
  (`evidence/a3/reboot/locktask-violations.txt`).

**Consequence (measured, narrow):** on this build, with lock task active, a host whose adb key isn't already
always-allowed can't be authorized. The only way back is RELEASE inside the DPC (or a wipe).
That supports dropping adb maintenance from the product design. It also means every spike step from here on keeps
this Mac's key always-allowed before any lock-task reboot.

### What this means for Plainphone (spike-level, not a spec)

- BLOCK on is the only setting here that closes App info, the share chooser and the Photos leak. But it also blocks
  runtime permission prompts, so the DPC must **pre-grant** permissions (`setPermissionGrantState` /
  `setPermissionPolicy`). Not yet tested on the device.
- BLOCK off leaks App info → Permissions/Notifications settings, the share chooser and Google Photos.
- The emergency dialer needs `com.android.phone` on the lock task allowlist.
- Photo sharing to Messages needs our own path (a direct `ACTION_SEND` with `setPackage(Messages)` works);
  the Camera → Photos → Share route fails in lock task.

## A4. Q5 baseline escapes, each restriction on its own

**Result: partly.** Fingerprint unchanged. Run 2026-10-08 21:09–21:20Z, Device Owner set, **lock task stopped**
except for the crash case, and **one restriction at a time** (rule 4; `DISALLOW_DEBUGGING_FEATURES` not used here).
Raw: `evidence/a4/*.txt` and screenshots. Helpers: `tools/pref-state.sh` (reads a Settings row's enabled/checked state;
never taps it), `tools/a4-crash.sh`, `tools/pp.sh tapx` (exact-text tap).

| Escape | Control (no restriction) | With the restriction / policy | Verdict |
|---|---|---|---|
| **OEM unlocking** toggle, `no_factory_reset` | Developer options > OEM unlocking: `enabled=true`, **`checked=true`** (someone at the phone had turned it on after the reset; `sys.oem_unlock_allowed=1`) | `checked=false enabled=false`, summary "Disabled by admin"; `sys.oem_unlock_allowed=0` | closed |
| | | After `restrict-clear no_factory_reset`: toggle stays **off** (`checked=false enabled=true`, `sys.oem_unlock_allowed=0`). The restriction switched it off and doesn't restore it. Left off; not re-enabled by the agent. | note |
| Factory reset entry, `no_factory_reset` | (not tapped in the control) | System > Reset options: row greyed (`title_enabled=false`); tap → `settings/.enterprise.ActionDisabledByAdminDialog` "Blocked by work policy … contact your IT admin" | closed (wording is enterprise-speak) |
| **Add user**, `no_add_user` | `pm create-user pp-spike-control` → `Success: created user id 10`, then `pm remove-user 10` → `Success`; "Allow multiple users" `checked=true enabled=true` | `pm create-user` → `Cannot add user: no_add_user is enabled.`; "Allow multiple users" greyed (`switch_enabled=false`) | closed |
| **Quick Settings**, `setStatusBarDisabled(true)` | `cmd statusbar expand-settings` → NotificationShade focused, 5 QS tile/gear descriptions | expand → focus stays on the spike, 0 tiles; manual swipe from the top edge → nothing | closed (also hides notifications; inside lock task, QS tiles are already hidden, see A3) |
| **Default launcher**, user path Settings > Default apps > Home app | no pin: tap "Moto App Launcher" → HOME opens `com.motorola.launcher3`, role = Moto | DPC `addPersistentPreferredActivity(HOME → spike)`: role switches to spike; user taps Moto again → HOME still opens the spike | closed by the **pin** |
| | | `no_config_default_apps` (API 34+), no pin: picker still opens, rows not greyed; tap Moto → **role = `com.motorola.launcher3`, HOME opens Moto** | **open**: this restriction does not cover the Home app here |
| **Crash dialogs** (spike crashes twice, `Commands.kt crash`) | first crash: no dialog; second: "Plainphone spike keeps stopping" with **App info** → `settings/.spa.SpaActivity` | Under lock task (BLOCK off and on): **no dialog**, but **lock task ends** (`mLockTaskModeState=NONE`) and the top becomes `com.motorola.launcher3` | **open**, see below |
| **Safe mode**, `no_safe_boot` | 0 entries in `dumpsys user` | `no_safe_boot` listed in `dumpsys user` and `getUserRestrictions` | restriction applies; **behaviour not tested**: no shell route to safe mode on this build, needs a physical long-press on Power off or Volume Down at boot |

### DPC crash ends lock task (`evidence/a4/crash-escape-window.txt`)

With lock task on (BLOCK on), HOME role = spike and the persistent preferred HOME = spike, one crash of the spike's activity gives:
`t+2s … t+60s: top=com.motorola.launcher3/…CustomizationPanelLauncher lockTask=NONE spikeProc=none`.
Nothing restarts the spike by itself. When the user presses **Home**, the spike comes back and re-locks (`onResume` → `startLockTask`).
During that window the full Moto launcher is usable (Chrome, Play Store and Settings icons are visible; see `launcher-no-config-tap.png`).
Hidden apps and user restrictions still apply, because they're Device Owner policy, not lock task.

Spike-level implications (not a spec): the Plainphone home/lock-task activity must not crash, or something must
bring it back with no user action. Things to try: hide the stock launcher (A5) so there's no fallback home; a
separate watchdog process; restarting on `BOOT`/package events. Feeds Q12 (crash-loop custody limit).

## A5. Q13 Motorola extras

**Result: yes** (gestures not tested). Fingerprint unchanged. Run 2026-10-08 21:23–21:39Z. Raw: `evidence/a5/`.
Scripts: `tools/a5-inventory.sh`, `tools/a3-matrix.sh <out> <cases-file>`.

Inventory (`inventory-before.tsv`): present are `securevault` (Secure folder; no launcher entry, reached via Moto Secure),
`securityhub` (Moto Secure), `moto`, `actions` (gestures), `gamemode` (Gametime), `freeform` (Sidebar), `spaces`,
`om`, `brapps` (disabled), `aura.oobe.motorola` (MotoApps), `mobiledesktop.core` (Smart Connect), `glance.lockscreenM` (disabled),
`genie`, `help`, `dti.motorola`, `chrome`, `facebook.katana`, `launcher3`.
**Not present:** `com.motorola.mobiledesktop`, `com.motorola.playautoinstallext`, `com.motorola.motodisplay`.
Facebook also ships `com.facebook.appmanager`, `com.facebook.system` and `com.facebook.services`.

**Reachable in lock task?** Each launcher entry was launched from the spike as an explicit component, new task and same task
(`lock-task-cases.txt`; allowlist = spike, Phone, Messages, Camera, `com.android.phone`):

| Entry | BLOCK on | BLOCK off, new task | BLOCK off, **same task** | BLOCK off, same task, **after hiding** |
|---|---|---|---|---|
| Moto Secure, Chrome, Device Help (`help`) | BlockedAppActivity | blocked | blocked | blocked |
| Moto (`moto`) | BlockedAppActivity | blocked | **opened** (`OnboardingActivity`) | blocked |
| Gametime | BlockedAppActivity | blocked | **opened** (`GameCenterActivity`) | blocked |
| Family Space (`spaces`) | BlockedAppActivity | blocked | **opened** (`OnBoardActivity`) | blocked |
| Moto Unplugged (`om`) | BlockedAppActivity | blocked | **opened** | blocked |
| Device Help chatbot (`genie`) | BlockedAppActivity | blocked | **opened** (`IntroScreensActivity`) | blocked |
| Facebook (`katana`) | BlockedAppActivity | blocked | **opened `com.facebook.appmanager/…StubInstallActivity`** (an installer) | blocked |

The same-task starts were made by our own spike code. A real user needs an allowed app that fires such intents.
Still, with BLOCK off **hiding is what closes them**.

**Can each be hidden?** `setApplicationHidden(admin, pkg, true)` returned `true` and `isApplicationHidden` → `true`
for all 20 extras plus the 3 Facebook system packages (`hide-results.txt`). They disappear from `pm list packages`,
and their launcher entries no longer resolve (`inventory-after-hide.tsv`).

**Does hiding break anything?** With all of them hidden, under lock task (BLOCK off), all of these worked (`breakage.txt`):
Phone (`GoogleDialtactsActivity`), Messages, Camera, a new photo (MediaStore 1 → 2), the emergency dialer screen
(`com.android.phone/.EmergencyDialer`; not dialed) and the notification shade. Over 60 s the only process death was a cached
`com.android.managedprovisioning`, which is normal. WebView is the standalone `com.google.android.webview` (127.0.6533.103),
so hiding Chrome does not affect WebView. **Not tested:** Moto Actions gestures (chop flashlight, twist camera, back-tap)
need physical motion. Hiding `com.motorola.actions` presumably removes them; unverified.

**Moto launcher hidden = crash fallback closed** (`crash-fallback.txt`). With `com.motorola.launcher3` hidden, the
only HOME candidates are the spike and `com.android.settings/.FallbackHome`. A spike crash under lock task gives
`Not force finishing home activity com.plainphone.spike.dpc/.MainActivity` → HOME restart of the spike at +0.12 s →
`startLockTask()` at +0.35 s → `lock task entering` at **+0.51 s**. The top stays on the spike, against ≥60 s on the open Moto
launcher in A4.

**Cloned Apps / Private Space / work profile** (`cloned-apps.txt`): `pm create-user --profileOf 0 --user-type
android.os.usertype.profile.CLONE` fails **even in the control**: `Cannot add user: no_add_clone_profile is enabled`.
`dumpsys user` lists `no_add_private_profile no_add_managed_profile no_add_clone_profile` as **base restrictions**
(`Device policy restrictions: none`). They're present while a Device Owner is set, gone after release (A7) and back on
re-provisioning, so the platform applies them for a fully managed device. Cloned Apps and Private Space are blocked
without any DPC policy. Secure folder is hidden; whether it would need one of these profile types wasn't tested.

**Install sweep, Wi-Fi off/on** (Lead request; `wifi-sweep.txt`). With `dti.motorola`, `aura.oobe.motorola`, `brapps`
and `facebook.appmanager` hidden (Play not hidden): 2 min with `svc wifi disable` (no active network) → no new packages;
5 min after `svc wifi enable` → no new packages (16 third-party, including hidden ones, before and after).
Limitation: this is **after** setup. Telling apart what arrives during setup with no network from what arrives after Wi-Fi joins
needs another trusted reset with Wi-Fi skipped in the wizard.

## A6. Q6 partly: do hidden apps and restrictions survive a reboot?

**Result: yes.** Raw: `evidence/a6/a6.txt`, `pkgs-*-before/after.txt`.
Before `adb reboot` (21:39:38Z): DO set, 21 hidden (the A5 list plus `launcher3`), restrictions `no_add_user no_factory_reset
no_install_unknown_sources no_safe_boot`, HOME pinned to the spike. After boot (`boot_count` 4):
- spike status identical: same 21 `hidden=…:true` and the same 4 restrictions; `dumpsys user` effective restrictions identical;
- `pm list packages` (visible) **byte-identical** to before; hidden count 21;
- HOME → spike, role = spike;
- `pm create-user` → `Cannot add user: no_add_user is enabled`;
- `am start -a VIEW -d https://example.com` → `unable to resolve Intent` (Chrome hidden, no handler).

**Finding:** `restrict-add no_config_default_apps` returns "OK", but the restriction **never appears** in
`getUserRestrictions` or the effective restrictions, before or after the reboot. On this build it is silently not applied.
That explains the A4 result where the Home app could still be changed.

## A7. Q7 partly: does `clearDeviceOwnerApp()` release the phone without a wipe?

**Result: yes.** Raw: `evidence/a7/a7.txt`, `after-release.png`. Released with the in-app RELEASE button
(`Release.run` → `clearDeviceOwnerApp()`) **while lock task was on** (BLOCK on).

| | Before | After |
|---|---|---|
| `dpm list-owners` | `DeviceOwner,Affiliated` | `no owners` |
| lock task | LOCKED | NONE (`lock task exiting`) |
| users / accounts | `0:Owner` / 0 | `0:Owner` / 0 |
| `IMG_20261008_132841212.jpg` (taken in A3) | md5 `916be6794c632213bdf49c4bfcfd8a61` | **same** |
| `IMG_20261008_142806273.jpg` (taken in A5) | md5 `373e3635de2c4ea25de16f9759d05cf7` | **same** |
| packages visible / all | 462 / 483 | 483 / 483 (all 21 unhidden) |
| effective restrictions | 4 DPC + 3 base profile restrictions | **none** |
| `boot_count`, uptime | 4, 1 min | 4, 3 min (no reboot, no wipe) |

Release message: `RELEASED deviceOwner=false; cleared no_add_user; cleared no_install_unknown_sources; cleared no_factory_reset;
cleared no_safe_boot; unhid …` (21 packages).
Not undone by release: the HOME role stays `com.plainphone.spike.dpc`, and OEM unlocking stays off (A4).

Harness notes (not device findings): the first two release attempts missed. First, the keyguard was showing after the A6 reboot.
Then Motorola's USB-mode chooser popped up, and the long status text moved the RELEASE button after the first tap, so the second
tap landed on "Start lock task". `tools/release-tap.sh` now arms, re-finds the button and double-taps the settled position.

## Phase B. Play sign-in (2026-10-08T22:47Z)

**Phase B ran on a Google test account that the agent signed in to, not on Beaux's personal account.**
Board comment `9a1887c6` (Beaux, Telegram 4453, relayed by Scout) authorized this for SPARE_DEVICE_SERIAL only,
because Beaux was away. The account is a Google Workspace account on Beaux's own domain. Its address is not recorded here.

- Path: `am start -a android.settings.ADD_ACCOUNT_SETTINGS --esa account_types com.google`, which opens GMS
  `MinuteMaidActivity`.
- **Finding:** because the phone is Device Owner-managed (`isOrganizationOwnedDevice=true`), Google's screen reads
  **"Sign in with your work account"** / "Work email" (`evidence/phase-b/signin-01-email-screen.png`). A consumer account
  wasn't tried.
- The email went in with `adb shell input text`. The password went in one character at a time with `adb shell input text`,
  read from the adapter env and never printed. Afterwards only the field length was checked (17 = 17, read from the masked a11y node).
  "Show password" stayed unticked. No screenshot was taken with characters in the field
  (`signin-02-password-screen-empty.png` was taken before typing; the account address is blacked out).
- Google asked for **no** 2-step verification, device prompt, SMS code or CAPTCHA. The next screen was the standard
  Terms "Welcome / I agree" (`signin-03-terms.png`, address blacked out); I tapped I agree.
- Result: `dumpsys account` → 1 account, `type=com.google`. Device Owner unchanged.
- **Sync:** Android offers per-item sync switches (Settings > Passwords, passkeys & accounts > account > Account sync).
  **All 7 were on, and the first sync had already run at 15:49–15:50 local**, before I could turn them off. That data
  (calendar, contacts, Drive, Gmail metadata) was not opened or read. I turned off all 7: Calendar, Contacts, Drive,
  Gmail, Google Calendar, Google Play Movies, Tasks in Calendar. Each now shows "Sync is OFF".
- Harness slips (not device findings):
  - My first "Next" tap landed on the keyboard's backspace and deleted the last character of the email. I retyped it,
    checked the field against the env value, and submitted with Enter. Only one password attempt was made.
  - `stay_on_while_plugged_in` had gone back to 0 by the time of sign-in (cause not traced). I set it to USB again
    (`svc power stayon usb`).
- Reset protection is now tied to this account. **Before any recovery wipe, remove the account from the phone first.**

### Update source

The plan was to roll 2–3 preinstalled Google apps back with `pm uninstall-system-updates`. That wasn't needed:
after the Settings reset, only `com.google.android.apps.restore` had been updated. Every other preinstalled Google app
was still at its factory version (for example Messages `20240806`, WebView 127, Photos 7.11), so Play had dozens of
real updates waiting as soon as the account was added.

## S1. Does `DISALLOW_INSTALL_APPS` block Play updates?

**Result: yes, it blocks them.** Evidence: `evidence/phase-b/s1/`.

- `restrict-add no_install_apps` at 22:52:05Z, then every `com.android.vending` job forced with
  `cmd jobscheduler run -f com.android.vending <id>` (8 jobs, IDs are dynamic).
- Play tried 5 auto-updates, and every one failed with `java.lang.SecurityException: User restriction prevents installing`,
  shown in Finsky as `state:INSTALL_ERROR, status_code=5402, reason=auto_update` (`jobs-forced.log`, 30 exceptions):
  - one ordinary app: `com.google.ar.core`;
  - four mainline/system items: `com.google.android.tzdata6`, `mainline_train_primary`, `com.google.mainline.telemetry`,
    `com.google.mainline.adservices`. So the restriction blocks Play-delivered mainline updates as well; this partly covers S6.
- Play > Manage apps & device stayed on "Checking for updates…" for over a minute under the restriction
  (`play-manage-restricted.png`). I didn't tap "Update all" because no list appeared.
- `adb install` (step 5, `adb-install.log`):
  - new install while restricted → `SecurityException: User restriction prevents installing` at
    `PackageInstallerService.createSessionInternal(PackageInstallerService.java:672)`;
  - control: the restriction cleared, then `adb install -t` v1 → `Success`;
  - update `adb install -r -t` v2 while restricted → the same `SecurityException`, and `versionCode` stays 1.
- Before the restriction (15:50 local), Play had already installed `com.google.android.contactkeys` with `reason=auto_update`.
  Play starts pushing within a minute of sign-in.

## S2. Device Owner install while restricted; clear → install → re-add

**Result: yes.** Evidence: `evidence/phase-b/s2/s2.log` (runs 1–3 were harness misses: the APK wasn't in the DPC's
`files/`, then the target was `testOnly`; I rebuilt the target without `testOnly`, because only the DPC needs it).

| Step | Result |
|---|---|
| DPC `PackageInstaller.createSession` with `no_install_apps` set | `SecurityException: User restriction prevents installing` |
| `clearUserRestriction` → DPC install v2 → `addUserRestriction` | `INSTALL_SUCCEEDED`, `versionCode=2`, **588 ms**, no user prompt |
| Window with the restriction lifted (on-phone logcat, clear at 15:58:44.755 → re-add at 15:58:45.583) | **about 0.83 s** (driven over adb, so a DPC doing it in-process would be shorter) |

Side check during S4 (`evidence/phase-b/s4/s4-hidden.log`): with Play **hidden**, the same DPC install still succeeds,
but takes **17.3 s** instead of 0.6 s. Play is the required verifier; the platform waits for verification to time out
(`Integrity check passed` 16:00:26.690 → `Continuing with installation` 16:00:43.696).

## S3. Can Play be suspended?

**Result: no.** Evidence: `evidence/phase-b/s3/s3.log`.

- `dumpsys package` → `Verifiers: Required: com.android.vending (uid=10270)`.
- `setPackagesSuspended([com.android.vending], true)` → returned `[com.android.vending]` (refused), and `dumpsys` shows
  `suspended=false`. The background-update half of S3 doesn't apply.
- I also suspended GMS (`com.google.android.gms`), which wasn't planned. It was **not** refused, and I unsuspended it straight
  away (`suspended=false` confirmed). Not followed up; note only that the DPC can suspend GMS on this build.

## S4. Hidden Play vs Play shut out by lock task

**Result: lock task, yes (Play keeps updating apps in the background); hidden Play, no (nothing updates).**
Evidence: `evidence/phase-b/s4/`.

**S4.1 hidden Play** (`s4-hidden.log`): `setApplicationHidden(com.android.vending, true)` → `true`. Forcing the 8 Play
jobs then gave `Unable to start service … PhoneskyJobServiceBackground … not found` and
`Error executing JobStatus` for each; no Play process was running. Hiding Play stops all Play updates.

**S4.2 Play unhidden, lock task on** (`s4-locktask.log`):
- Lock task packages: the spike and `com.plainphone.spike.target` only. Features `HOME|GLOBAL_ACTIONS|BLOCK` (84).
  `mLockTaskModeState=LOCKED`.
- `market://details?id=org.telegram.messenger` from the spike (new task, and same task) → system
  `BlockedAppActivity` "App is not available / Google Play Store is not available right now."
  (`market-link-under-locktask.png`).
- Forced Play's jobs at 23:01:55Z. Over the next 6 minutes, with lock task still `LOCKED` and the spike on top, Play
  **updated 23 apps in the background** (`updated-under-locktask.txt`). The first finished at +4 s, and the 12th of 23 at +2 min 3 s
  (16:03:58). Examples:
  - `com.google.android.apps.messaging` `20240806` → `20261002` (an app Plainphone allows);
  - `com.google.android.webview` 127 → 154;
  - Photos 7.11 → 7.96, Maps, Duo/Meet, Clock, TalkBack, TTS, and 2 Motorola apps.
  More were still installing when I stopped watching. One failed: `com.google.android.dialer`, `INSTALL_ERROR 1055`
  (not traced).
- **Bonus finding:** while Play was hidden and then unhidden, a "Moto App Launcher keeps stopping" dialog appeared
  over lock task. It was dismissed with Back, and lock task stayed `LOCKED`.

**What this means for Plainphone (spike-level):** "Play enabled and signed in, but shut out by lock task" keeps both apps
and GMS updating, with no user-visible Play. Hiding or suspending Play is not an option. Permanent `DISALLOW_INSTALL_APPS`
stops all Play updates, including mainline.

## S7. Remove-after-install race

**Result: partly. The DPC wins, but not before the app's process can start.** Evidence: `evidence/phase-b/s7/`.

Setup: lock task stopped (supervised window), DPC `watch-on` with an empty allowlist (hide any new, non-replacing
package on `ACTION_PACKAGE_ADDED`). A loop on the phone fired `am start -a VIEW -d tg://resolve?domain=telegram`
every ~250 ms (`deeplink-loop.out`). Telegram (`org.telegram.messenger` 12.10.6) was installed from Play through
`market://details?id=org.telegram.messenger` → Install. The same install is the Telegram install for B-Q4.

| Time (local) | Event |
|---|---|
| 16:10:57.448 | `firstInstallTime` |
| 16:10:57.567 → 16:11:01.6 | 25 deep-link starts resolve to `org.telegram.ui.LaunchActivity` (`result code=0`), but each is `Force removing` / `Force stopping …: start failure` (package still frozen by the install) |
| **16:11:01.738** | **`Start proc … org.telegram.messenger`**: the app's process starts |
| 16:11:01.953 | DPC receives `PACKAGE_ADDED` (4.5 s after `firstInstallTime`) |
| 16:11:02.075 | `setApplicationHidden` → `true` (the call took 127 ms); **4.6 s after `firstInstallTime`** |
| 16:11:02.101 | Telegram activity force-removed; later deep links get `result code=-91` (no resolve) |
| 16:11:02.962 | Finsky reports `INSTALLED` (after the hide) |

- No `Displayed org.telegram…` line appeared, so no Telegram window reached the screen. But the process ran for about
  0.35 s, so `Application.onCreate` and whatever the app starts there did run.
- Implication: a hide-on-`PACKAGE_ADDED` DPC leaves a gap of about 4–5 s. Most of it is platform latency before the
  broadcast, not DPC work. To close it, the DPC would have to stop new installs (S1/S2), not race them.

## B-Q1. Lock task follow-ups, all with BLOCK on

**Result: yes for all five.** Run 2026-10-08 23:17–23:22Z. Evidence: `evidence/phase-b/bq1/`.
Lock task packages: Phone (`com.google.android.dialer`), Messages, Camera (`com.motorola.camera3`), `com.android.phone`
and the spike. Features `SYSTEM_INFO|NOTIFICATIONS|HOME|GLOBAL_ACTIONS|BLOCK` (87). The spike is the persistent HOME.

| Follow-up | Result |
|---|---|
| **Pre-grant with `setPermissionGrantState`** (`permission-pregrant.log`) | **Works for camera, microphone and location** on this build. Camera, Messages and Telegram (third-party, no default grants): `CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE/COARSE_LOCATION`, `POST_NOTIFICATIONS`, `READ_MEDIA_IMAGES`, `READ_CONTACTS` all end `granted=true, flags=[POLICY_FIXED…]`. Quirk: right after granting, `getPermissionGrantState` returns 2 (DENIED) for location, while `checkPermission`, `dumpsys` and appops (`FINE_LOCATION: allow`) all show it granted. |
| Camera with BLOCK on, after the pre-grant | Opens with **no permission prompt** (in A3 the prompt was blocked). Photo taken: `DCIM/Camera/IMG_20261008_161844868.jpg` (1.8 MB). |
| **Camera thumbnail → Photos** | **Closed**: `BlockedAppActivity` "Photos is not available right now." (`camera-thumbnail-block-on.png`). In A3, with BLOCK off, this opened Google Photos. |
| **Emergency dialer screen**, `com.android.phone` allowlisted | **Opens** (`com.android.phone/.EmergencyDialer`, `emergency-dialer-block-on.png`). Backed out. **No call placed.** Its "Emergency information" button wasn't probed; that's a lead for the Verifier. |
| **Attach an image in Messages through its own picker** | **Works.** Compose to the test number 555-0100 → "Show attach media screen" opens Messages' in-app gallery inside the task. The 16:18 photo attached as a draft MMS. **Nothing sent**; the draft was removed. Messages' **"Folders"** button opens the system Photo Picker (`com.google.android.providers.media.module/…PhotoPickerGetContentActivity`), which BLOCK stops: "Media picker is not available right now." |
| **Reboot back into lock task** (key always-allowed) | **Yes** (`reboot.log`). adb was back in 38 s with no prompt. At +45 s, `mLockTaskModeState=LOCKED`, and DO, lock-task packages and features were unchanged. Settings is blocked. At boot, Motorola's `UsbBottomChooseActivity` is blocked again (as in A3). |

Also seen after the reboot: a second account, `type=com.google.android.apps.tachyon`. Google Meet/Duo creates this
local account after Play updated it (S4); it isn't a Google sign-in.

## B-Q4. Must-have apps (Slack, Spotify, Telegram, Gmail, Outlook)

**Result: partly. Telegram's screens before sign-in are done; the rest is waiting on the board** (see the end of this section).
Evidence: `evidence/phase-b/bq4/`.

### Telegram `org.telegram.messenger` 12.10.6 (versionCode 71122), from Play (the S7 install)

- Under lock task with BLOCK on, Telegram opens on its welcome screen. A "Web tabs" node on the welcome screen is an
  empty placeholder: tapping it does nothing, and no tabs exist before sign-in.
- **"Start Messaging" → loop of blocked screens.** Telegram asks for runtime permissions (`REQUEST_PERMISSIONS`) and
  opens Credential Manager (`com.android.credentialmanager/.CredentialSelectorActivity`). Under BLOCK both give
  `BlockedAppActivity` ("Permission controller / Credential Manager is not available right now"). Every OK resumes
  Telegram, and Telegram asks again: **6 requests in 30 s, with no way through.**
  The requested permissions were not granted: `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `CALL_PHONE`, `READ_CALL_LOG`.
- **Pre-granting those four with `setPermissionGrantState` breaks the loop** (0 new requests). Telegram reaches
  "Your phone number". So **Plainphone must pre-grant each allowed app's first-run permissions** (or allow the
  permission controller) before BLOCK can be on.
- **Privacy finding:** with `READ_PHONE_NUMBERS` pre-granted, Telegram **pre-filled a phone number** in the field
  (white text, not a placeholder), even though there's no SIM. The source wasn't traced. I didn't record the value: it's
  redacted in `telegram.log` and `telegram-06-after-pregrant.png`, and Telegram's data was cleared afterwards
  (`pm clear`). Pre-granting phone permissions gives an app the phone's number; Plainphone should only grant what each app needs.
- **Stopped there.** Signing in needs a phone number and code; that's Beaux's. Not tested yet: sign-in, signing in again,
  notifications, links, and **whether Telegram's in-app browser can reach arbitrary sites**. All of these need a
  signed-in Telegram.

### Board answer (card `16e9a46e`, 2026-10-09)

Install Slack, Spotify and Outlook from Play with the test account: **yes**. Open Gmail without opening any email: **yes**.
App sign-ins: **Beaux will do them later and say when.** Run 2026-10-09 00:10–00:29Z. Same setup as Telegram: lock task
with BLOCK on, every app allowlisted (`HOME|GLOBAL_ACTIONS|NOTIFICATIONS|SYSTEM_INFO|BLOCK`), launched from the spike.
Per-step logs and screenshots: `evidence/phase-b/bq4/<app>/` (`tools/bq4-probe.sh`; emails redacted by the script).

| App | Version (Play) | Installed (local) |
|---|---|---|
| Slack `com.Slack` | 26.09.50.0 (90016232) | 17:11:12 |
| Spotify `com.spotify.music` | 9.1.88.2204 (146816068) | 17:11:36 |
| Outlook `com.microsoft.office.outlook` | 5.2639.2 (72639116) | 17:12:11 |
| Gmail `com.google.android.gm` (system, updated by Play in S4) | 2026.09.21.992487546.Release (66054195) | preinstalled |

### Slack 26.09.50.0 (before sign-in)

| Route | Result under BLOCK |
|---|---|
| Launch → "Sign in" | In-app `AuthOptionsActivity`: email, Google, workspace URL, GovSlack, QR code. |
| **Google** | "There was a problem authenticating with Google." No activity start. **Same error with lock task off** (control, step 11), so lock task isn't the cause; it fails on this managed phone either way. Not traced. |
| Enter a workspace URL | In-app `SignInActivity`. Not submitted (needs a real workspace; SSO workspaces may open a browser, which is untested). |
| Email | Not submitted: Slack would email a code to the account. |
| **QR code** | First: `REQUEST_PERMISSIONS` → "Permission controller is not available". After pre-granting `CAMERA`, Slack still showed its own "go to device settings" dialog (it remembered the denial). After going back and re-entering, **the QR scanner opens**. Pre-grant before first launch. |
| GovSlack | In-app sheet (slack-gov.com / slack.mil); no web route. |
| Web routes found before sign-in | **None** reachable. |

### Spotify 9.1.88.2204 (before sign-in)

| Route | Result under BLOCK |
|---|---|
| Launch | In-app `LoginActivity` ("Sign up free", "Log in"). |
| "Log in" | First opens **Credential Manager** (`com.android.credentialmanager/.CredentialSelectorActivity`) → blocked. After OK, Spotify falls back to its own login screen (email, Facebook, Google). No loop. |
| **Facebook** | Facebook SDK → `CustomTabMainActivity` → **Chrome Custom Tab** `https://m.facebook.com/…` → **blocked** ("Chrome is not available right now"). |
| **Google** | `GOOGLE_SIGN_IN` → `com.google.android.gms/.auth.api.signin.ui.SignInActivity` → **blocked** ("Google Play services is not available"). |
| Email / Sign up | In-app. Not submitted (Spotify would email a code or create an account). |
| Web routes found before sign-in | **None** reachable (the only one, Facebook's Custom Tab, is blocked). |

So for Beaux, Spotify sign-in under BLOCK works only with email (and a code). Google and Facebook sign-in need
`com.google.android.gms`, or a browser, during a supervised window.

### Outlook 5.2639.2 (before sign-in)

| Route | Result under BLOCK |
|---|---|
| Launch → "Add account" | In-app `AddAccountActivity`: email field, ADD GOOGLE ACCOUNT, Help, PRIVACY AND TERMS, Scan QR code. |
| **PRIVACY AND TERMS → Privacy & Cookies** | **Opens Outlook's own WebView** (`com.acompli.acompli.GenericWebViewActivity`) with the live Microsoft Privacy Statement (`04b_privacy_webview_loaded.png`). |
| **…→ an off-site link in that page** ("U.S. Department of Commerce") | **Loads `dataprivacyframework.gov` inside the same WebView, still under lock task** (step 05). **Off-site browsing by following links works before sign-in.** I stopped there (no search engine tried). |
| **ADD GOOGLE ACCOUNT** | `OAuthActivity` → `InteractiveAuthActivity` → `GOOGLE_SIGN_IN` → GMS `SignInActivity` → **blocked**. After OK, Outlook returns to its splash screen. **No web fallback**, so adding the trusted person's Gmail account to Outlook fails under BLOCK. It needs GMS allowlisted, or a supervised window. |
| Help → Contact support | Asks to enable notifications (Cancel), then "Collect Diagnostics Data" consent (it would upload logs including account emails to Microsoft). **Cancelled; nothing sent.** |
| Microsoft-account sign-in (email field) | Not submitted. |

### Gmail 2026.09.21 (test account; no email opened)

Inbox screens were never dumped or screenshotted. `tools/gm-nav.sh` lists only a fixed set of navigation labels.

| Route | Result under BLOCK |
|---|---|
| First launch | In-app welcome tour → "Set up addresses" → **"Learn more"** → GMS `googlehelp.HelpActivity` → **blocked**. |
| Entering the inbox | `REQUEST_PERMISSIONS` (notifications) → **blocked** ("Permission controller is not available"). Pre-granting `POST_NOTIFICATIONS` lets the inbox open. |
| Tabs | **Mail and Meet only; no Chat tab** on this Workspace account. |
| **Meet tab** | In-app Meet (`…conference.ui.largescreen.HomeActivity`): asks for phone-state permission in-app (Cancel), then "Join a meeting" by code. A **video-call surface inside Gmail**, not a web route. Not joined. |
| Account menu → Manage your Google Account / Privacy Policy / Terms of Service | All go to GMS `accountsettings…SettingsLoaderActivity` → **blocked**. |
| Account menu → **Add another account → Outlook, Hotmail, and Live** | **Microsoft's live web sign-in page loads in a WebView inside Gmail** (`.setup.AccountSetupFinalGmail`, step 18). Its "Privacy and cookies" link is handed to **Chrome → blocked**. Whether the login page's own in-page links ("Forgot your username?", "Create an account") keep browsing inside the WebView wasn't tested. |
| Drawer → Help & feedback | GMS `HelpActivity` → **blocked**. |
| Drawer → Settings | In-app `Gmail2PreferenceActivity` (General settings, Add account). |
| **Links in HTML mail** | **Not tested**: it needs opening an email, which the board ruled out for the test account. Needs a known test message in Beaux's own account. |

### B-Q4 summary so far

- **Pattern:** every app's Google sign-in goes through GMS (`SignInActivity`), and every Help and Account link goes
  through GMS too. BLOCK stops all of these. Allowing `com.google.android.gms` would open the account sign-in UI,
  but also GMS Help and the account web pages: that is a large web surface. Plainphone should do Google sign-ins
  during a supervised setup window, not by allowlisting GMS.
- **Credential Manager and the permission controller** are blocked under BLOCK. Telegram loops on them, while Spotify and Slack recover.
  **Pre-grant first-run permissions** (Telegram phone permissions, Slack camera, Gmail notifications).
- **Web leaks found:**
  1. **Outlook's in-app WebView** (Privacy link before sign-in) follows off-site links. **This is a real escape**, so
     Outlook needs a mitigation, or the board needs to accept the risk.
  2. **Gmail's add-account flow** loads Microsoft's login page in a WebView. Off-page links go to Chrome (blocked);
     the page's own links are untested.
  3. Facebook (Spotify) and Help links use Custom Tabs or GMS, which are blocked.
- **Moved to [PLA-20](/PLA/issues/PLA-20)** (Lead's call, comment `25c667f5`; the sign-in card `b2e73c09` was withdrawn). Still to do once Beaux signs in: sign-in, signing in again, password reset,
  notifications and links for Telegram, Slack, Spotify and Outlook; Telegram's in-app browser; links in Gmail HTML mail
  and the Chat tab (absent on this account).

## Final end state at PLA-5 close (2026-10-09T00:37Z, checked again)

PLA-5 is closed; the Verifier ([PLA-6](/PLA/issues/PLA-6)) drives the phone next, then PLA-20.
- Spike DPC `com.plainphone.spike.dpc` is Device Owner, `testOnlyAdmin=true`, so `dpm remove-active-admin` works (§ A2).
  **No restrictions**, nothing hidden or suspended, lock task `NONE`, lock-task packages = the spike only.
- HOME = `com.motorola.launcher3`; the spike's persistent HOME is cleared.
- Accounts: the Google **test account** (sync off) + Meet's local `tachyon` account. **Remove the test account before any
  recovery wipe** (reset protection is tied to it).
- Installed and not signed in: Telegram 12.10.6 (data cleared), Slack 26.09.50.0, Spotify 9.1.88.2204, Outlook 5.2639.2.
  Gmail was opened once on the test account (no email opened). Policy-fixed pre-grants: Telegram (phone, camera, mic,
  location, contacts, notifications), Slack (camera, notifications), Gmail (notifications), Camera and Messages (B-Q1 set).
- adb over USB with this Mac's key always-allowed. Wireless debugging off (`adb_wifi_enabled=0`). `stay_on_while_plugged_in=2`.

## Current state (2026-10-09T00:29Z, after B-Q4 probes)

Same as below, plus Slack, Spotify and Outlook installed (not signed in), Gmail opened once on the test account (no email opened),
and `POST_NOTIFICATIONS` / `CAMERA` pre-granted to Gmail / Slack. Lock task off, HOME = Moto launcher, no restrictions
(`evidence/phase-b/end-state-2.txt`).

## State at 2026-10-08T23:26Z (after S1–S7, B-Q1)

`evidence/phase-b/end-state.txt`:
- Spike DPC is Device Owner (`testOnlyAdmin=true`, removable with `dpm remove-active-admin`). **No DPC restrictions**,
  nothing hidden, nothing suspended. **Lock task off** (`NONE`); only the spike is in the lock-task packages list.
- HOME role = `com.motorola.launcher3`, and the spike's persistent HOME is cleared. Play `53.4.34-34` is visible.
- **Accounts: the Google test account** (sync off for all 7 items) plus Meet's local `tachyon` account.
  Reset protection is now tied to the test account: **remove it from the phone (Settings > Passwords, passkeys & accounts >
  account > Remove account) before any recovery wipe.** The Settings reset path is still the only allowed one.
- Installed by Phase B: Telegram 12.10.6 (visible, data cleared, phone/camera/mic/location pre-granted with `POLICY_FIXED`),
  our target app v2, and Play's updates to at least 23 preinstalled apps (S4).
- adb works over USB, and this Mac's key is always-allowed (persistent keystore). After the B-Q1 reboot,
  `adb_wifi_enabled` = 0 (it was 1), so wireless debugging is now off. `stay_on_while_plugged_in` = 2 (USB). `boot_count` = 5.
- Return to unmanaged: as in § A2. If the board also wants the account gone, remove it through Settings first.

## State before Phase B (2026-10-08T21:44Z)

`evidence/phase-b-prep/state.txt`: spike DPC is Device Owner (`testOnly`, removable with `remove-active-admin`);
**no DPC restrictions** (only the 3 base profile restrictions that come with DO); nothing hidden; lock task off and no
lock task packages; HOME role = `com.motorola.launcher3` (set with `cmd role add-role-holder`) so Play and Settings are reachable;
0 accounts; Wi-Fi on; Play `53.4.34-34`; adb works with this Mac's key always-allowed; `stay_on_while_plugged_in` = USB;
wireless debugging on (set by whoever was at the phone); OEM unlocking **off** (see A4).
