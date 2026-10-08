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
| A1–A7 | not yet run | Waiting for the board to confirm they're done at the phone (setup wizard, USB debugging). |
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
