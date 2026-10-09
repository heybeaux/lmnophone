# PLA-11 emulator go/no-go spike: results (THROWAWAY)

This is spike work, not MVP code. Everything under `spikes/emulator/` is throwaway.

## Setup

| AVD | Serial | Image build | Used for |
|---|---|---|---|
| `pp11-a35` | emulator-5620 | `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.036/12592187:user/release-keys` (Google Play, API 35) | Q1, Q3 adb, Q4 (all items) |
| `pp11-a36` | emulator-5630 | `google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.D1/13818094:user/release-keys` (Google Play, API 36), fresh `-wipe-data` boot | Q3 adb and QR path, Q1 matrix and Q4 repeated |
| `pp11-a37` | (crashed during startup) | `system-images/android-37.0/google_apis_playstore` | Not used: the emulator exited during first boot. API 36 is the newest image that ran. |

- No Google account on any emulator (`dumpsys account` count = 0). We never touched emulator-5590, emulator-5604 or SPARE_DEVICE_SERIAL.
- DPC: `apps/dpc` (`com.plainphone.spike.emu`), built `testOnly`. It is the Device Owner, the home app, the lock-task owner and the link-catcher. Test hook: `tools/pp.sh` (`cmd …` sends a broadcast to `CommandReceiver`, guarded by `android.permission.DUMP` so only adb can call it).
- Lock-task setup (`tools/setup.sh`): packages = Dialer, Messages, Camera (`com.android.camera2`), `com.android.phone`, plus the system-only emergency floor (`com.android.server.telecom`, `com.google.android.apps.safetyhub`). Features = `SYSTEM_INFO|NOTIFICATIONS|HOME|GLOBAL_ACTIONS|KEYGUARD|BLOCK_ACTIVITY_START_IN_TASK` ("flag on").
- Fixtures (`apps/fx-*`, user-installed): `fx-browser` (general browser), `fx-applinks` (one domain with `autoVerify` plus custom scheme `ppfx:`), `fx-httponly`, `fx-urlbar` (in-app URL bar and WebView, no web filter), `fx-emergency` (declares `EmergencyDialer.DIAL`, `CALL_EMERGENCY` and `DIAL_EMERGENCY` at priority 1000).

## Summary

| Question | Answer | One line |
|---|---|---|
| Q3 adb `dpm set-device-owner` | **Yes** | Test-only DPC becomes Device Owner on API 35 and 36. No "App blocked to protect your device". |
| Q3 QR provisioning | **No (not testable on emulator)** | Google Play emulator images boot already provisioned and have no setup wizard, so there is no QR entry point. The trusted-source intent needs a privileged permission. Must be done on the phone (PLA-5). |
| Q1 normal day, flag on | **Partly (go, with 3 fixes)** | Calls, SMS, notifications, camera, power menu, Wi-Fi, emergency screen and reboot all work in lock task. Fixes needed: pre-grant all runtime permissions; pin the emergency intent to the system package; decide about `com.android.phone` screens (RadioInfo, Call settings). Image MMS could not be sent on the emulator (emulator limit). |
| Q1 blocked launches | **Yes** | Settings, Settings panels, Play, Photos, the share sheet and STK are blocked for same task, new task, explicit component and chooser, on API 35 and 36. |
| Q4 browser role | **No** | Hiding Chrome does not move the browser role. The role stays with the hidden Chrome. The in-app role request is blocked by lock task. Links still reach the link-catcher, because it becomes the only handler. |
| Q4 classifier | **Yes** | All fixtures are classified as the spec expects. The negative control shows Plainphone would hide itself without the identity check. |
| Q4 link matrix | **Yes, with one gap** | https links, unverified App Links and Custom Tabs go to the link-catcher. Explicit, `intent:` and Custom Tabs intents aimed at a hidden browser fail. Gap: an unapproved http-only app that stays visible turns `http:` links into a chooser, which lock task blocks. |
| Q4 CaptivePortalLogin hidden | **Yes** | An ordinary network validates. A sign-in network shows Plainphone's message. |
| Q4 hide Google app / Gemini | **Yes, nothing core breaks** | Assist, web search and voice command have no handler. Dialer and Messages still work. Gemini is not on the image. |
| Q4 floor trust (T1) | **Yes, and found a bug in the naive approach** | The system-only floor rejects the fake emergency app (the naive query admits it). Separately, an implicit emergency intent turns into a blocked chooser once the fixture is installed, so the MVP must pin the package. |
| Q9 DNS filter | Deferred | See end. |
| Q11 GrapheneOS emulator | Deferred | See end. |

---

## Q3. Provisioning (emulator)

### Q3a. adb `dpm set-device-owner`: **yes**

API 35 (`evidence/q3/a35-adb.txt`) and API 36 (`evidence/q3/a36-qr-and-adb.txt`):

```
$ dumpsys account | grep -c "Account {"      -> 0
$ adb install -t dpc-debug.apk               -> Success      (APK manifest: testOnly=true)
$ adb shell dpm set-device-owner com.plainphone.spike.emu/.AdminReceiver
Success: Device owner set to package com.plainphone.spike.emu/.AdminReceiver
$ adb shell dpm list-owners
User  0: admin=com.plainphone.spike.emu/.AdminReceiver,DeviceOwner,Affiliated
logcat: I DevicePolicyManager: Device owner set: ComponentInfo{com.plainphone.spike.emu/...AdminReceiver} on user 0
```

- **"App blocked to protect your device": not seen** on either API level. We checked the screen text after provisioning, and grepped logcat for `App blocked|protect your device|EnhancedConfirmation`. Nothing matched.
- Negative control (API 36): a second `dpm set-device-owner`, whether for the fixture `com.plainphone.fx.applinks` or the DPC again, fails with `IllegalStateException: Trying to set the device owner (...), but device owner (com.plainphone.spike.emu/.AdminReceiver) is already set.` So the adb path is one-shot and cannot be repeated by another app.

### Q3b. QR provisioning: **no, not testable on these images**

API 36, fresh `-wipe-data` boot (`evidence/q3/a36-qr-and-adb.txt`):

```
$ settings get global device_provisioned      -> 1
$ settings get secure user_setup_complete     -> 1
$ pm list packages | grep -iE "setupwizard|provision"
package:com.android.managedprovisioning      (no setup wizard package)
$ am start -a android.app.action.PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE
java.lang.SecurityException: Permission Denial ... requires android.permission.DISPATCH_PROVISIONING_MESSAGE
```

- The emulator boots straight to the Pixel launcher (`evidence/q3/a36-welcome.png`; the file name is wrong, it shows the launcher). So there is no welcome screen to tap six times for the QR reader.
- The only other way into ManagedProvisioning needs a privileged permission that adb shell does not hold.
- We built the QR payload anyway (`tools/qr.py`, image `evidence/q3/qr-testonly.png`; checksum = SHA-256 of the debug signing cert, base64url). We booted API 36 with it as a virtual-scene camera poster. It can be reused on the phone.
- **Next step:** QR provisioning of the test-only DPC has to be tried on SPARE_DEVICE_SERIAL in PLA-5, which owns the phone.

---

## Q1. A normal day with `BLOCK_ACTIVITY_START_IN_TASK` on

All on API 35 with the flag on unless stated. Status line before the day:
`deviceOwner=true lockTaskModeState=1 lockTaskPackages=[camera2, dialer, emu, safetyhub, server.telecom, messaging, phone] lockTaskFeatures=SYSTEM_INFO|NOTIFICATIONS|HOME|GLOBAL_ACTIONS|KEYGUARD|BLOCK`

### Calls and messages: **yes**

`evidence/q1/day-a35-calls.txt`, `day-a35-calls-incoming-retry.txt`, `day-a35-sms-notif.txt`, `day-a35-attach.txt`

| Step | Command | Result |
|---|---|---|
| Incoming call | `adb emu gsm call 6505550100` | Telecom `state=RINGING`. Dialer shows a heads-up call notification (category=call, importance 4) over Plainphone home, with Answer and Decline (`shots/a35-call-incoming-shade.png`). |
| Answer | tap "Answer" in the notification | `TelephonyConnection ... state:ACTIVE`; `START ... dialer/.../InCallActivity` opens inside lock task (`shots/a35-call-active.png`). |
| Outgoing call | `cmd launch "action=android.intent.action.DIAL data=tel:5551234 newtask=1"`, then tap dial | `START ... dialer/.../InCallActivity` (`shots/a35-call-outgoing.png`). |
| Incoming SMS | `adb emu sms send 5551234 "Hello from the spike"` | Messages notification posted (`incoming_message`, importance 4) (`shots/a35-sms-notification.png`). |
| Outgoing SMS | type and send in Messages | `content://sms` row `type=2, body=Outgoing from spike` (`shots/a35-sms-outgoing.png`). |

- Emulator quirk: `adb emu gsm cancel` did not always end the outgoing call. A call "On hold" survived a reboot and had to be ended in the in-call screen. This is about the emulator modem, not lock task.

### Notifications: **yes**

- The shade opens (feature `NOTIFICATIONS`). Tapping the SMS notification opened `com.google.android.apps.messaging/.ui.ConversationListActivity` on the conversation (`shots/a35-sms-opened.png`).
- New finding for UX: after the DPC grants location, Permission controller posts "Your organization allows Phone/Messages to access your location" notifications. These are system disclosures, so expect them on the phone.

### Permissions: **yes, and pre-granting is required**

`evidence/q1/day-a35-permissions.txt`

- `canAdminGrantSensorsPermissions=true`.
- **Negative control:** we set camera2 `CAMERA` to `default`, revoked it, and opened Camera. The runtime prompt is **blocked by lock task**: `BlockedAppActivity`, "Permission controller is not available right now." (`shots/a35-perm-control-camera.png`). Without a pre-grant, a user cannot even say yes.
- After `setPermissionGrantState(..., GRANTED)` for CAMERA, RECORD_AUDIO, ACCESS_FINE/COARSE_LOCATION, READ_MEDIA_*, READ_CONTACTS, CALL_PHONE, SEND_SMS and POST_NOTIFICATIONS on Camera, Messages and Dialer, Camera opened with **no system prompt** (`shots/a35-perm-granted-camera.png`). Camera2's own in-app "Remember photo locations?" card appeared. That is the app's own screen, not a permission prompt.
- Caveat for the MVP: `setPermissionGrantState` returns `true` even for permissions the app never requested (the state stays `0`). Check `getPermissionGrantState`, not the return value.

### Camera and photos: **partly**

`evidence/q1/day-a35-camera.txt`, `day-a35-attach.txt`

- Photo taken in lock task: tap Shutter, which wrote `/sdcard/Pictures/IMG_20261008_164730.jpg`.
- In Messages, "Show attach media screen" opens **Messages' own picker** inside the task. The new photo is listed ("Select this image taken at Thu, Oct 8, 2026, 4:47 PM") (`shots/a35-attach-picker.png`).
- Selecting it gives "Attachments are not supported in this conversation", so we could not send it as an image. **Diagnostic control:** the same message appears with lock task stopped (`lockTaskModeState=0`), so this is the emulator SIM's MMS support (carrier config is not changeable on a user build: `cmd phone cc` → Permission denied), not lock task.
- **Next step:** sending an image MMS must be finished on the phone (PLA-5).

### Phone controls: **yes**

`evidence/q1/day-a35-controls.txt`, `day-a35-reboot.txt`

- Power menu: the power + volume-up chord shows "Emergency / Power off / Restart" (`shots/a35-power-menu-chord.png`). Long-press POWER showed nothing with the image default, but showed the same menu once `power_button_long_press=1` (`shots/a35-power-menu.png`).
  - Likely cause: the Pixel default sends long-press to the assistant, and the Google app is hidden. The setting change was a diagnostic and was restored with `settings delete`.
  - **Follow-up:** check what long-press power does on the Moto.
- Reboot: `adb reboot`, then `boot_completed` after 15 s. The DPC home (persistent preferred HOME) called `startLockTask()` before `LOCKED_BOOT_COMPLETED`. Logcat: `lock task entering com.plainphone.spike.emu`, and status shows `lockTaskModeState=1 locked=true` (`shots/a35-after-reboot.png`).

### Settings: own Wi-Fi screen: **yes**

- Plainphone's Wi-Fi screen (`WifiActivity`) opens in lock task (`shots/a35-wifi-screen.png`). `WifiManager.addNetwork` as Device Owner: `addNetwork(PlainphoneTest, psk) -> netId=1 enableNetwork -> true`. Joining the real open network `addNetwork(AndroidWifi, open) -> netId=0`, then `wifi=true validated=true`.
- `getConfiguredNetworks()` returned `[]` until the DPC self-granted `ACCESS_FINE_LOCATION`. Then it listed `0:"AndroidWifi", 1:"PlainphoneTest"`. The MVP must grant itself location to show saved networks.
- `enableNetwork(id, true)` on a network that isn't in range drops the current Wi-Fi for a few seconds (`wifi=false` right after). The UI should not attempt-to-connect while adding.
- Negative control: the system Wi-Fi settings and Wi-Fi panel stay blocked (`wifi-settings` and `panel-wifi` rows below).

### Emergency dialer screen: **yes, only when the package is pinned**

`evidence/q4/a35-floor-t1.txt`. We never placed a call.

- `cmd launch "action=com.android.phone.EmergencyDialer.DIAL pkg=com.android.phone newtask=1"` opens `com.android.phone/.EmergencyDialer` in lock task (keypad visible, `shots/a35-emergency-dialer.png`).
- See T1 below. Without `pkg=`, a user-installed app with the same filter turns this into a blocked chooser.

### Blocked launches: **yes** (API 35 and 36)

`tools/q1-matrix.sh`, cases in `tools/q1-cases.txt`. Each case is launched from inside the locked task in three ways: same task, `FLAG_ACTIVITY_NEW_TASK`, and wrapped in `Intent.createChooser`. Explicit-component cases are included.

| Case | Flag ON, API 35 (`matrix-a35-flag-on.tsv`) | Flag ON, API 36 (`matrix-a36-flag-on.tsv`) | Flag OFF, diagnostic (`matrix-a35-flag-off.tsv`) |
|---|---|---|---|
| Settings (action and explicit) | blocked ×3 | blocked ×3 | blocked |
| Wi-Fi settings, app info, default apps, accessibility | blocked ×3 | blocked ×3 | **opened** in same task and via chooser |
| Settings panels (internet, volume, Wi-Fi, NFC) | blocked ×3 | blocked ×3 | blocked |
| Play (`market:`, web, explicit) | blocked ×3 | blocked ×3 | blocked |
| Share sheet (`ACTION_SEND`) | blocked ×3 | blocked ×3 | **opened** (resolver/chooser) |
| Photos (explicit) | blocked ×3 | blocked ×3 | **opened** |
| STK (same UID as `com.android.phone`) | blocked ×3 | blocked ×3 | blocked |
| Mobile network settings | blocked ×3 | blocked ×3 | **opened** via chooser |
| `com.android.phone/.settings.RadioInfo` (explicit) | **opened** (same, new task) | **opened** | opened |
| Call settings (`SHOW_CALL_SETTINGS`) | **opened** (same, new task) | **opened** | opened |

"Blocked" means the system `BlockedAppActivity` ("… is not available right now") or `ActivityNotFoundException`. The flag-off column shows the flag is what stops Settings sub-screens, the share sheet and Photos opening inside an allowed task.

**Gap:** `com.android.phone` must be allowlisted for the emergency dialer and in-call. That also lets any approved app, and the Dialer code `*#*#4636#*#*`, open RadioInfo and the full Call settings (forwarding, FDN, calling accounts) (`shots/a35-on-dialer-4636.png`, `a35-on-radioinfo.png`, `a35-on-callsettings.png`). **Follow-up:** test whether `DISALLOW_CONFIG_MOBILE_NETWORKS` / `DISALLOW_CONFIG_CELL_BROADCASTS` close these screens, or accept and document them.

### UI exceptions list: **yes** (API 35)

`evidence/q1/exceptions-dialer-a35.txt`, `exceptions-messages-a35.txt`. We walked every settings and menu item in the two approved apps with the flag on.

| Entry point | Lands in | Verdict |
|---|---|---|
| Dialer > Settings > Caller ID & spam, Assisted dialing, Display options, Voicemail | Dialer's own screens | allowed (in-package) |
| Dialer > Settings > Accessibility (TTY) | `com.android.phone/.settings.AccessibilitySettingsActivity` | allowed (com.android.phone) |
| Dialer > Settings > Calls | `com.android.phone/.CallFeaturesSetting` | allowed. **Gap above.** |
| Dialer > Settings > Blocked numbers, Quick responses | `BlockedAppActivity` "Phone Calls is not available" | blocked when `com.android.server.telecom` was not allowlisted. **Re-check now that the floor adds telecom.** |
| Dialer > Settings > Sounds and vibration | `BlockedAppActivity` "Settings is not available" | blocked |
| Dialer > Voicemail > Notifications | `BlockedAppActivity` | blocked |
| Dialer "Learn more" links | Chrome (`IntentDispatcher`), then `BlockedAppActivity` | blocked (it goes to the link-catcher once Chrome is hidden) |
| Messages first run > Sign in, account menu > Sign in | `ADD_ACCOUNT_SETTINGS`, then `BlockedAppActivity` | blocked |
| Messages > Use without an account, Device pairing, Help, Privacy, Terms, Spam & blocked, Archived, Settings | Messages' own screens | allowed (in-package) |
| Same-UID sibling: `com.android.stk` (UID 1001 with `com.android.phone`) | `BlockedAppActivity` | blocked: the allowlist is per package, not per UID |

---

## Q4. Web containment (no account needed)

API 35 is the main run (`evidence/q4/a35.txt`); API 36 repeated it (`evidence/q4/a36.txt`) with identical classifier, resolve, role and Custom Tabs results.

### Browser role: **no, it does not go to the link-catcher**

```
role holder before hiding:              com.android.chrome
cmd classify  -> com.android.chrome: GENERAL_BROWSER(heuristic) hide->true
role holder after hiding:               com.android.chrome      (hidden app still holds the role)
cmd browser-role-request (RoleManager.createRequestRoleIntent(ROLE_BROWSER)) while locked
  -> BlockedAppActivity "Permission controller is not available right now."
```

- The link-catcher still gets https links, because after hiding it is the only resolver: `resolve[data=https://example.com/] default=com.plainphone.spike.emu/.LinkActivity`.
- So the MVP should **not** rely on the role. Rely on resolution. If the role matters (for example for "open by default"), request it during setup, before lock task starts.

### Classifier fixtures: **yes**

| Package | `classify-dry` | Negative control `classify-noidentity` |
|---|---|---|
| `com.plainphone.spike.emu` (Plainphone, cert matches) | `SELF_SKIPPED` | `GENERAL_BROWSER(heuristic)`: it would hide itself |
| `com.android.chrome` | `GENERAL_BROWSER(heuristic)`, then hidden | same |
| `com.plainphone.fx.browser` | `GENERAL_BROWSER(heuristic)`, then hidden | same |
| `com.plainphone.fx.applinks` (one domain) | `NOT_BROWSER` | same |
| `com.plainphone.fx.httponly` | `NOT_BROWSER(one scheme only)` | same |
| `com.plainphone.fx.urlbar` | `FLAG_CATALOG_REVIEW` (not hidden) | same |
| `com.google.android.googlequicksearchbox` | `NAMED_HIDE`, then hidden | same |
| `com.google.android.gms`, `com.android.vending`, `com.google.android.dialer` | `NEVER_HIDE` | `NOT_BROWSER`: the never-hide protection is gone |

Probe: `http=[chrome, fx.browser, fx.httponly, emu] https=[chrome, fx.browser, emu]`.

### Link matrix: **yes, with one gap** (API 35 and 36)

Cases are in `tools/q4-links.txt`; results in `evidence/q1/matrix-a35-q4-links.tsv` and `matrix-a36-q4-links.tsv`. They ran after `classify`, from inside the locked task.

| Case | Same / new task | Via chooser |
|---|---|---|
| `https://example.com` | link-catcher (`LinkActivity`) | blocked |
| App Link `https://bank.plainphone-fx.example` (unverified, state 1024) | link-catcher | blocked |
| Same App Link with `setPackage(fx.applinks)` (unapproved app) | blocked (`BlockedAppActivity`) | blocked |
| Custom scheme `ppfx://open` (unapproved app) | blocked | blocked |
| `intent://…#Intent;scheme=https;package=com.android.chrome;end` | `ActivityNotFoundException` (the framework does not parse `intent:`) | blocked |
| Custom Tabs, no package | link-catcher; `CustomTabsService providers=[]` after hiding | blocked |
| Custom Tabs `setPackage(com.android.chrome)` | `ActivityNotFoundException` | blocked |
| Explicit component to hidden Chrome or hidden fx-browser | `ActivityNotFoundException` | blocked |
| `MAIN` + `APP_BROWSER`, `WEB_SEARCH`, `ASSIST`, Google app explicit | `ActivityNotFoundException` | blocked |
| `http://example.com` | **blocked: a chooser** between link-catcher and the still-visible `fx-httponly` | blocked |

- Gap: any visible non-approved app with a web filter makes links ambiguous, so the user sees a blocked chooser instead of the link-catcher.
- In the MVP every non-approved app is hidden, which removes this. But an **approved** http-only or App Links app will cause the same chooser.
- **Follow-up for the spec:** when an approved app shares a web filter, the link-catcher needs a persistent preferred activity (`addPersistentPreferredActivity` for `VIEW http/https`) or a decision to send that domain to the approved app.

### CaptivePortalLogin hidden: **yes**

`evidence/q4/a35-captive.txt`. Probe URLs were pointed at a host-side fake portal (`tools/portal.py`, `settings put global captive_portal_*_url http://10.0.2.2:8099/...`); mobile data was off; `cmd hide com.google.android.captiveportallogin -> true`.

| Network | Result |
|---|---|
| Ordinary (portal returns 204) | `wifi=true captive=false validated=true msg=null`; capabilities include `VALIDATED` (`shot a35-captive-ordinary.png`) |
| Sign-in (portal returns 302) | `NetworkMonitor: isCaptivePortal ... RedirectUrl=http://10.0.2.2:8099/login`; `captive=true`; Plainphone home shows "This Wi-Fi needs a sign-in page, which Plainphone doesn't allow. Use mobile data or another Wi-Fi." and a "Forget this network" button (`shot a35-captive-signin-home.png`) |

- Android still posts its own "Sign in to Wi-Fi network" notification.
- Tapping it in the shade did nothing (no activity start logged). It also did nothing with CaptivePortalLogin unhidden, so this control is **inconclusive** about the effect of hiding. Either way, nothing opened.
- The portal URL settings were test scaffolding on the emulator only.

### Hiding the Google app and Gemini: **nothing core breaks**

`evidence/q4/a35-google-app-hidden.txt`

- Gemini (`com.google.android.apps.bard`) is not installed on the API 35 image. Only the Google app was tested.
- With the Google app hidden:
  - `ASSIST`, `WEB_SEARCH` and `VOICE_COMMAND` have no handler.
  - `KEYCODE_ASSIST` and long-press HOME do nothing.
  - The `ASSISTANT` role still names the hidden package.
  - Speech recognition still resolves to `com.google.android.tts`.
  - Messages and Dialer open normally.
- Side effect: the default long-press POWER did not open the power menu (see Phone controls). The power + volume-up chord does.

### Floor trust (T1): **yes**

`evidence/q4/a35-floor-t1.txt`, `a35.txt`, `a36.txt`

- `floor` (MATCH_SYSTEM_ONLY plus a system-app check) gives `emergencyDialer=[com.android.phone, com.android.server.telecom, com.google.android.apps.safetyhub]`. **fx-emergency is not included.**
- Negative control `floor-naive` (no system filter) includes `com.plainphone.fx.emergency` in `emergencyDialer` and in `uiSubset`. It also adds `com.google.android.as`, `bluetooth` and `gms` as "in-call UI".
- `floor-apply` keeps fx-emergency out of `lockTaskPackages`. An explicit launch of fx-emergency gives `BlockedAppActivity` "FX fake emergency is not available right now." So it can't be opened.
- **New finding:** with fx-emergency installed, an *implicit* `EmergencyDialer.DIAL` or `DIAL_EMERGENCY` resolves to `android/com.android.internal.app.ResolverActivity`, which lock task blocks ("Android System is not available right now").
  - So a user-installed app can break the emergency button just by declaring the filter.
  - With `setPackage(com.android.phone)` taken from the floor, the real emergency dialer opens.
  - **The MVP must launch emergency with an explicit package from the system-only floor.** It should also hide non-approved apps that declare emergency filters.

---

## Q9 and Q11: deferred

- **Q9 (DNS filter):** deferred. A fair test needs a real filtering resolver and a check of which apps ignore Private DNS (DoH inside apps), which costs more than this run had left. The DPC API for it, `DevicePolicyManager.setGlobalPrivateDnsModeSpecifiedHost` (API 29+), was not exercised here. It is a short follow-up spike.
- **Q11 (GrapheneOS emulator):** deferred. There is no prebuilt GrapheneOS emulator image; you have to build it from source (a full AOSP-sized build). That is over the one-day limit set in `docs/feasibility-questions.md`. It is also only relevant if Beaux picks a Pixel.

## Follow-ups for the Lead

1. On the phone (PLA-5):
   - QR provisioning of the test-only DPC
   - sending a photo as MMS from Messages' picker
   - long-press power behaviour with the Google app hidden
2. Spec, device-lockdown:
   - pre-grant every runtime permission (prompts are blocked in lock task)
   - pin the emergency intent to the system floor package
   - decide on the `com.android.phone` screens (RadioInfo, Call settings)
3. Spec, web-containment:
   - don't depend on the browser role
   - add a persistent preferred activity for http/https to the link-catcher, or a rule for approved apps with web filters
4. Re-run the Dialer "Blocked numbers" and "Quick responses" exceptions now that the floor adds `com.android.server.telecom`.

## Files

- `apps/`: DPC and fixtures (Gradle; `./gradlew assembleDebug`; add `-PtestOnly=false` for a non-test-only DPC).
- `tools/`:
  - `pp.sh` (adb helpers; refuses the three foreign serials)
  - `setup.sh`
  - `q1-matrix.sh` with `q1-cases.txt` and `q4-links.txt`
  - `q4.sh`
  - `portal.py`
  - `qr.py`
- `evidence/q1`, `evidence/q3`, `evidence/q4`: logs, TSVs and screenshots named above.
