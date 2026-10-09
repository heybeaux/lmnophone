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
| Q3 QR provisioning | **Inconclusive (not exercised on these images)** | Google Play emulator images boot already provisioned and have no setup wizard, so there is no QR entry point. The trusted-source intent needs a privileged permission. Must be done on the phone (PLA-5). |
| Q1 normal day, flag on | **Partly** | Calls, SMS, notifications, camera, power menu, Wi-Fi, emergency screen and reboot all work in lock task. Fixes needed: pre-grant all runtime permissions; pin the emergency intent to the system package; decide about `com.android.phone` screens (RadioInfo, Call settings). Image MMS could not be sent on the emulator (emulator limit). |
| Q1 blocked launches | **Yes for the cases run; re-run with the result detector in the PLA-23 revision** | Settings, Settings panels, Play, Photos, the share sheet and STK are blocked for same task, new task, explicit component and chooser, on API 35 and 36. |
| Q4 browser role | **No** | Hiding Chrome does not move the browser role. The role stays with the hidden Chrome. The in-app role request is blocked by lock task. Links still reach the link-catcher, because it becomes the only handler. |
| Q4 classifier | **Yes** | All fixtures are classified as the spec expects. The negative control shows Plainphone would hide itself without the identity check. |
| Q4 link matrix | **Partly; extended in the PLA-23 revision** | https links, unverified App Links and Custom Tabs go to the link-catcher. Explicit, `intent:` and Custom Tabs intents aimed at a hidden browser fail. Gap: an unapproved http-only app that stays visible turns `http:` links into a chooser, which lock task blocks. |
| Q4 CaptivePortalLogin hidden | **Partly** | An ordinary (open) network validates. A sign-in network shows Plainphone's message. WPA2, Forget and the notification control are in the PLA-23 revision. |
| Q4 hide Google app / Gemini | **Partly** | The tested Dialer/Messages opening and assistant-resolution checks passed: Assist, web search and voice command have no handler. Gemini is not on the image, so it was not tested. Lens/Circle to Search, real voice typing and the real app list were not covered. |
| Q4 floor trust (T1) | **Clean state only; see the PLA-23 revision for stale-entry replace** | The system-only floor rejects the fake emergency app (the naive query admits it). Separately, an implicit emergency intent turns into a blocked chooser once the fixture is installed, so the MVP must pin the package. |
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

### Q3b. QR provisioning: **inconclusive, not exercised on these images**

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
  - Hypothesis, not an isolated cause: the Pixel default sends long-press to the assistant, and the Google app is hidden. The setting change was a diagnostic and was restored with `settings delete`.
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

### UI exceptions list: **incomplete** (API 35; superseded by the PLA-23 revision, V1)

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
| Same-UID sibling: `com.android.stk` (UID 1001 with `com.android.phone`) | `BlockedAppActivity` | blocked. Narrowed: one explicit launch of `StkMain` was denied. That is not a claim about lock-task eligibility by UID; see PLA-23 V1. |

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

### CaptivePortalLogin hidden: **partly** (see PLA-23 V6)

`evidence/q4/a35-captive.txt`. Probe URLs were pointed at a host-side fake portal (`tools/portal.py`, `settings put global captive_portal_*_url http://10.0.2.2:8099/...`); mobile data was off; `cmd hide com.google.android.captiveportallogin -> true`.

| Network | Result |
|---|---|
| Ordinary (portal returns 204) | `wifi=true captive=false validated=true msg=null`; capabilities include `VALIDATED` (`shot a35-captive-ordinary.png`) |
| Sign-in (portal returns 302) | `NetworkMonitor: isCaptivePortal ... RedirectUrl=http://10.0.2.2:8099/login`; `captive=true`; Plainphone home shows "This Wi-Fi needs a sign-in page, which Plainphone doesn't allow. Use mobile data or another Wi-Fi." and a "Forget this network" button (`shot a35-captive-signin-home.png`) |

- Android still posts its own "Sign in to Wi-Fi network" notification.
- Tapping it in the shade did nothing (no activity start logged). It also did nothing with CaptivePortalLogin unhidden, so this control is **inconclusive** about the effect of hiding. Either way, nothing opened.
- The portal URL settings were test scaffolding on the emulator only.

### Hiding the Google app and Gemini: **the tested Dialer/Messages and assistant-resolution checks passed**

`evidence/q4/a35-google-app-hidden.txt`

- Gemini (`com.google.android.apps.bard`) is not installed on the API 35 image. Only the Google app was tested.
- With the Google app hidden:
  - `ASSIST`, `WEB_SEARCH` and `VOICE_COMMAND` have no handler.
  - `KEYCODE_ASSIST` and long-press HOME do nothing.
  - The `ASSISTANT` role still names the hidden package.
  - Speech recognition still resolves to `com.google.android.tts`.
  - Messages and Dialer open normally.
- Side effect: the default long-press POWER did not open the power menu (see Phone controls). The power + volume-up chord does.

### Floor trust (T1): **clean state only** (see PLA-23 V4)

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

---

## Revision after PLA-13 review (PLA-23, 2026-10-08)

This answers the Verifier's findings V1–V7 in `docs/reviews/PLA-13-emulator-spike-review.md`. Everything is still **throwaway spike work, on emulators only**. Device results stay in `spikes/device-moto/` and are not mixed in here. Evidence paths below are relative to `spikes/emulator/evidence/pla23/`.

**This harness is not the restricted-user product.** It is a test DPC with an adb-only command hook. adb stays on because it is the test channel, so `DISALLOW_DEBUGGING_FEATURES` is not applied. Every other intended restriction is applied (see V5 and V7).

### Emulators, provenance and setup (V7)

| Emulator | AVD | Image | Setup and read-back |
|---|---|---|---|
| emulator-5620 | `pp11-a35`, wiped | `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.036/12592187:user/release-keys` | `provenance-a35.txt` |
| emulator-5630 | `pp11-a36`, wiped | `google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.D1/13818094:user/release-keys` | `provenance-a36.txt` |

- Both emulators were wiped, then set up only with `tools/setup.sh <serial> <label>`. It runs the ordered steps and writes the provenance file:
  1. install the 6 APKs, recording the SHA-256 of each;
  2. `dpm set-device-owner`;
  3. persistent HOME, the approved list, lock-task features, and `floor-apply` (which replaces the list);
  4. permission pre-grants, read back;
  5. `classify` (hides Chrome, fx-browser and the Google app) and hides CaptivePortalLogin;
  6. restrictions;
  7. start lock task.

  The read-back records owners, the policy, the hidden state, and the version and path of 21 app packages (DPC, fixtures, Dialer, Messages, Telecom, Safety Hub, Chrome, WebView, Gboard, Permission controller, DocumentsUI, media provider, Settings).
- No account on either emulator (`accounts: 0`).
- **DPC versions.** a36 ran `emu-3-pla23` (sha256 `cd4a6d06…`) from setup. On a35 the DPC was updated in place during the run to `emu-4-pla23` (`65b4c793…`, logged at the end of `provenance-a35.txt`), to add the `es.` launch extras and the `classify-essential` hook. To install it I cleared `no_install_apps`, installed, and set the restriction again. The policy read-back after the update matches the read-back before it.
- **Two intended restrictions did not stick.** `no_config_private_dns` and `no_config_default_apps` returned "OK added" but are missing from the read-back on both images. Not investigated.
- **Policy per result.** Each detector row asserts the policy line before and after the case. Each matrix file starts and ends with the full policy line. The scripts that change the list (V3, V4, V6) print every change and read-back inline.
- I did not touch emulator-5590, emulator-5604, emulator-5640 (the Verifier's `pla25-review-a35`) or SPARE_DEVICE_SERIAL. After every hide or unhide the scripts wait 15 s (PLA-22). No emulator was rebooted after a hide.

### V2: result detector, and the matrix re-run

- **Detector.** `tools/detect.sh` reports each case as one of five outcomes. A case is `TEST_FAILED` when:
  - the policy line changes during the case, or the lock-task state differs from what the case expects;
  - the DPC never logs the launch;
  - the launch throws anything other than `ActivityNotFoundException`;
  - nothing resumes.

  Otherwise, in order:
  - `NO_HANDLER`: `ActivityNotFoundException`;
  - `LINK_CAUGHT`: the DPC's `LinkActivity` resumed;
  - `TARGET_OPENED`: anything else resumed, even briefly, apart from the home screen and `BlockedAppActivity`;
  - `POLICY_DENIED`: only `BlockedAppActivity` resumed.

  Activity events (`wm_set_resumed_activity`, all log buffers) are captured over the whole case. The old 1.5 s foreground sample is gone.
- **Matrix.** `tools/matrix2.sh` runs every case in `tools/matrix2-cases.txt`: the old Q1 cases, the old Q4 link cases and the new V3 `parseUri` cases, 43 in total.
  - **Protected (final policy, a35):** 3 modes per case, 129 rows. **89 `POLICY_DENIED`, 28 `NO_HANDLER`, 8 `LINK_CAUGHT`, 4 `TARGET_OPENED`, 0 `TEST_FAILED`.** File: `matrix-a35-on.tsv`.
    - The 4 `TARGET_OPENED` rows are `com.android.phone` `RadioInfo` and `CallFeaturesSetting`, same task and new task. Both are inside an allowed package; this is the known gap.
    - All 28 `NO_HANDLER` rows target a **hidden** package (Chrome, fx-browser, the Google app, assist/web-search, `APP_BROWSER`), or are among the 3 cases below that have no handler either way.
  - **Control (lock task stopped through the adb-only hook, hidden packages unhidden, new-task mode):** **40 of 43 targets open** (`TARGET_OPENED`). File: `matrix-a35-control.tsv`. The control script then restores the final policy; the end-of-file read-back shows it.
  - **3 cases fail both ways, so they do not test containment:**
    - `emergency-info`: `EDIT_EMERGENCY_INFO` has no handler on this image;
    - `intent-uri`: a raw `Uri.parse` of `intent:`, which the framework does not parse (see the `parse=` cases instead);
    - `parse-raw-fallback-url`: an app-side `parseUri` does not use `S.browser_fallback_url`.
- The old PLA-11 TSVs are kept as history. The old "BLOCKED" label is superseded by this detector. API 36 was not re-run with the full matrix; it only had the V1 set below.

### V1: UI exceptions (a36)

Full summary: `v1/SUMMARY.md`. Rows: `v1/walks.tsv`. Per route there is a screenshot and a text file. Tool: `tools/walk.sh`.

- **Start state.** The walk started from the read-back final allowlist (`v1/policy-start.txt`, identical to `v1/policy-end.txt`): Dialer, Messages, Camera, `com.android.phone`, `com.android.server.telecom`, Safety Hub, the DPC.
- **Tap discipline.** Before every tap the walk resets to a known menu and asserts that the node exists in a fresh dump. A screen that does not change counts as `TEST_FAILED`.
- **Result: no route opened an activity outside the allowlist.**
  - **Telecom, now on the list:** Dialer > Settings > Blocked numbers opens `telecom/.settings.BlockedNumbersActivity`, and Quick responses opens `telecom/.RespondViaSmsSettings`. Both are inside Telecom.
  - **Dialer Settings:** Calls opens `CallFeaturesSetting`, and Calling accounts opens `PhoneAccountSettingsActivity`. Accessibility and Voicemail > Advanced open `com.android.phone` screens. Help & feedback and Sounds and vibration are `POLICY_DENIED`.
  - **Messages:** Sign In (first run and account menu) and Help & feedback are `POLICY_DENIED`. Settings, Archived, Spam & blocked and Device pairing stay inside Messages.
  - **Safety Hub:** `LauncherActivity` opens its own account picker, and `AppActionsLauncherActivity` opens too. Both are inside Safety Hub.
  - **Pickers, account and phone settings:** `GET_CONTENT`, `OPEN_DOCUMENT`, `PICK_IMAGES`, `ADD_ACCOUNT_SETTINGS` and `NETWORK_OPERATOR_SETTINGS` are `POLICY_DENIED`. The control in `matrix-a36-v1-control.tsv` shows all of them open with protection off: photo picker, DocumentsUI, AddAccountSettings and MobileNetworkActivity.
  - **Shade exits (a35, `v5-shade-a35.txt`):** the shade's "Manage" button and the keyboard-layout notification both give `POLICY_DENIED` ("Settings is not available right now").
- **UID siblings** (`v1/uid-inventory.txt`):
  - Phone (UID 1001) shares with stk, ons, mms.service and providers.telephony.
  - Telecom (UID 1000, the system UID) shares with Settings, KeyChain, providers.settings and others.
  - Explicit launches of the siblings StkMain, the stk launcher, Settings, Wi-Fi settings and KeyChainActivity are all `POLICY_DENIED`, in the same task and in a new task (`matrix-a36-v1-on.tsv`). With protection off they all open (`matrix-a36-v1-control.tsv`).
- **Narrowed claim:** on API 35 and 36, lock task blocked *direct, explicit activity launches* of these non-allowlisted siblings, even when they share a UID with an allowlisted package. **Not tested:** a sibling activating lock task itself (Android documents shared-UID eligibility for that), siblings started through services or broadcasts, and siblings without a launchable activity. The old row "the allowlist is per package, not per UID" is narrowed in place.
- **Not covered (`TEST_FAILED` or not run):**
  - Messages Privacy Policy and Terms of Service: the reset did not recover after a blocked dialog.
  - The keyboard's gear, emoji/GIF search and voice input.
  - The Messages attach button in the UI.
  - Dialer Call history and Call Assist.
  - Safety Hub Help.
  - Inline "Learn more" links inside paragraphs.
  - The UI menu routes that were denied have no protection-off control. The denied direct launches do have one.

  These stay **open for the gate**.

### V3: Q4 link matrix additions (a35)

Files: `v345-a35.txt`, `v345-ui-a35.txt`, `v3-webview-a35.txt`, `v345-a35-ctclient-webview-rerun.txt`, and the matrix files above. Each case has a control.

| Case | Final policy | Control |
|---|---|---|
| **Verified App Link, approved app**. State set with `pm set-app-links ... STATE_APPROVED`: a simulated verification, with no assetlinks fetch. | `TARGET_OPENED` fx-applinks, which is intended for an approved app | lock task off: `TARGET_OPENED` |
| **Verified App Link, unapproved app still visible** | `POLICY_DENIED`: the user sees a blocked screen, not the link-catcher | as above |
| **Verified App Link, unapproved app hidden** (the MVP state) | `LINK_CAUGHT` | as above |
| `parseUri` raw, `package=com.android.chrome` (hidden) | `NO_HANDLER` | Chrome opens |
| `parseUri` Chrome-style sanitised (BROWSABLE, no component or selector) | `NO_HANDLER` | Chrome opens |
| `parseUri` raw, no package | `LINK_CAUGHT` | Chrome opens |
| `parseUri` raw `component=Settings`, and sanitised `action=SETTINGS` | `POLICY_DENIED` | Settings opens |
| `parseUri` raw to the unapproved `ppfx:` app | `POLICY_DENIED` | fx-applinks opens |
| **Real Custom Tabs client** (`androidx.browser` 1.8.0, `CustomTabsClient.getPackageName`, with `<queries>`) | `providers=[]` → `provider=null`. Fallback `launchUrl` gives `LINK_CAUGHT`. Fallback to its own WebView loads the page inside the approved app (re-run log). | `customtabs-to-chrome` with Chrome visible: Chrome opens |
| **WebView inside the approved fixture** (expected, disclosed leak) | With a WebViewClient, the page loads and a link tap navigates to `https://www.iana.org/` **inside the app**, under lock task (`lockTaskModeState=1`). Shot: `v345-a35/v3-webview-client1-after-tap.png`. Without a WebViewClient, the tap leaves the app as `VIEW` and gives `LINK_CAUGHT`. | Fixture not approved: `POLICY_DENIED` |
| **Essential package that matches the browser heuristic.** fx-browser was marked essential with the `classify-essential` hook. No real system package on this image matches the heuristic: the `https` probe is only fx-browser and the DPC. | The classifier reports `NEVER_HIDE_CONFLICT_REPORTED` and does not hide it. A general `https` link then becomes a chooser: `POLICY_DENIED`, and the link-catcher is lost. | Normal `classify` hides fx-browser again |

Spec implications for the Lead:
1. A verified App Link from an approved app goes straight to that app. That is correct, but it means approval covers the app's domains.
2. An unapproved app that stays visible turns its verified links into a blocked screen. Hiding fixes this.
3. The never-hide/browser conflict breaks link-catching for every web link, so it needs a decision (for example a persistent preferred activity for `VIEW http/https`), not just a report.
4. The WebView leak in approved apps is real and app-controlled, as the spec already says.

**Running total for the batch run.** In the batch run (`v345-a35.txt`), the Custom Tabs "fallback to WebView" case was recorded as `LINK_CAUGHT`. The isolated re-run (`v345-a35-ctclient-webview-rerun.txt`) shows the WebView fallback working. I did not find the cause of the difference, so treat that one batch row as unreliable.

### V4: floor replaces the list, and the Emergency button (a35)

Files: `v345-a35.txt`, `v345-ui-a35.txt`, `v345-a35/v4-emergency-button.png`. No call was placed: the keypad was only opened, then closed with BACK.

- **`floor-apply` now replaces the list.** The new list is approved + system-only floor + DPC (`Commands.kt`, `approved-set` / `floor-apply`).
  - Stale entry: fx-emergency was seeded into the lock-task list through the adb hook. **Before** `floor-apply` an explicit launch gives `TARGET_OPENED` (the stale authority works). **After** `floor-apply` the read-back no longer lists it, and the launch gives `POLICY_DENIED`.
  - Clean-list control: same launch, `POLICY_DENIED`.
- **The home Emergency button is pinned** to the system-only `EmergencyDialer.DIAL` package: `emergencyPackage=com.android.phone` (`MainActivity.kt`, `Floor.emergencyPackage`).
  - **Real button tap** with fx-emergency installed (it declares the same filters at priority 1000): `com.android.phone/.EmergencyDialer` resumes, `lockTaskModeState=1`, keypad visible.
  - Control: the old implicit intent gives `POLICY_DENIED` (blocked resolver). The pinned intent gives `TARGET_OPENED`.
  - The button was also tapped while a sign-in Wi-Fi was connected (V6): it opens EmergencyDialer.
- **Not tested:** unknown-build or corrupt-policy states, a previously hidden legitimate emergency component, and the lock-screen Emergency entry. Those Q6/T1 states stay **untested**.

### V5: harness boundary (a35)

- The "Stop lock task" button is removed from the home screen. Read-back: 0 nodes; the home texts are listed in `v345-ui-a35.txt`.
- Ops relayed to `MainActivity` now need a per-process token that only the adb-only (`DUMP`-guarded) command receiver has. `am start ... MainActivity --es op lt-stop` from outside gives `REJECTED op=lt-stop without the command-hook token`, and the state stays `lockTaskModeState=1`.
- The Verifier's route started with the Stop button. With it gone, the locked shade shows **no Quick Settings and no settings gear**: `expand-settings` dumps 0 such nodes (`v345-a35/v5-locked-shade-expand-settings.png`). The shade's Manage and keyboard notification are `POLICY_DENIED`.
- `setup.sh` applies the full intended restriction set apart from `no_debugging_features`. Read-back: `no_add_managed_profile, no_add_user, no_config_cell_broadcasts, no_config_date_time, no_config_vpn, no_create_windows, no_factory_reset, no_install_apps, no_install_unknown_sources_globally, no_modify_accounts, no_network_reset, no_safe_boot, no_system_error_dialogs, no_usb_file_transfer, no_user_switch`.
- Side effect: with this set, shell cannot read `/sdcard`. The UI dump helper moved to `/data/local/tmp`.
- Safe-mode and Settings-reset routes were not exercised.

### V6: Wi-Fi (a35)

Files: `v6-a35.txt`, `v6-a35/*.png`, `v6-a35/portal-requests.log`. The first attempt is kept in `v6-a35-run1-mobiledata-on.txt`. With mobile data on, the sign-in Wi-Fi is not the default network, so the DPC never showed its message. The re-run turns mobile data off, as PLA-11 did.

- **WPA2: inconclusive.** The emulator's only access point is the open `AndroidWifi` (scan flags `[ESS]`, no WPA2). A validated WPA2 connection cannot be made on this image. Validation was shown only for the open network.
- **Sign-in network** (host portal returns 302; the PLA-18 setup on port 8123):
  - DPC: `wifi=true captive=true validated=false`, and the home screen shows the sign-in message and "Forget this network".
  - **Incoming call while connected:** Telecom `state=RINGING`, shot `signin-incoming-call.png`. It was cancelled from the emulator console, not answered.
  - **Emergency button while connected:** `com.android.phone/.EmergencyDialer` resumes, `lockTaskModeState=1`.
  - **Sign-in notification, final policy (CaptivePortalLogin hidden):** the tap resumes nothing.
  - **Control** (CaptivePortalLogin unhidden and on the list, as in PLA-18 Q3b): the tap opens `CaptivePortalLoginActivity` under lock task. So the hidden result is a real difference, not a failed tap. Policy restored afterwards (hidden, list replaced).
  - **Forget:** before, `configured=[0:"AndroidWifi"]`, connected. Tapping "Forget this network" gives `configured=[]`; `cmd wifi list-networks` reads "No networks". Then the ordinary network was re-added and validated.

### Wording changes in this file

- Removed "go, with 3 fixes".
- QR is now **inconclusive, not exercised**, not "no". The repeated-owner control tests owner exclusivity only.
- "Nothing core breaks" now reads as what was tested: the Dialer/Messages opening and assistant-resolution checks. Gemini was absent; Lens/Circle to Search, voice typing and the real app list were not covered. The long-press power explanation is marked as a hypothesis.
- UI exceptions, captive handling and T1 are relabelled from "yes" to their measured scope, with pointers here.

### Image attachment (ADR #1)

Per the Lead's decision, the image attachment result for the gate is the **device** result: PLA-5 B-Q1, confirmed by the Verifier in PLA-6. On the emulator the attachment is rejected with lock task both on and off, which makes it an **emulator limitation**. No emulator MMS work was done in this revision.

### Still open after this revision

- V1 gaps: Messages Privacy and Terms, the keyboard surfaces, attach in the UI, Call history, Call Assist, Safety Hub Help, inline Learn more links, and protection-off controls for the denied UI menu routes.
- WPA2 validation (an emulator limitation).
- API 36 detector re-run of the full matrix.
- Lock-screen Emergency entry and the Q6 policy states.
- The two restrictions that did not stick (`no_config_private_dns`, `no_config_default_apps`).
- Safe mode and reset routes.
