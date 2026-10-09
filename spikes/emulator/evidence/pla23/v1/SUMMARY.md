# PLA-23 V1: UI route walk on emulator-5630 (pp11-a36, API 36), throwaway

Written by the walk sub-run; saved by the Android Platform Engineer. The control batch was added afterwards: `../matrix-a36-v1-on.tsv` and `../matrix-a36-v1-control.tsv`.

No route opened an activity outside the allowlist (0 escapes). Coverage is incomplete because of the 25-minute cap.
The policy at the start equals the policy at the end: deviceOwner=true, lockTaskModeState=1, the same 7 lockTaskPackages and the same restrictions (`policy-start.txt`, `policy-end.txt`).

Tool: `tools/walk.sh`.
- Before every tap it checks that the node is in a fresh UI dump.
- It clears logcat before the final tap.
- It records the resumed activities, the top activity, the screen texts and a screenshot.
- If the activity and the texts are the same as before the tap, the row is TEST_FAILED "NO CHANGE".
- `ui()` dumps to `/data/local/tmp`, because a dump written to `/sdcard` can't be read back on this image under the full restrictions.

## UI walks
- **TARGET_OPENED, all in allowed packages:**
  - Dialer Settings > Caller ID & spam, Assisted dialing, Display options and Voicemail stay in the Dialer.
  - Calls opens `com.android.phone/.CallFeaturesSetting`. Its nested Calling accounts link opens `PhoneAccountSettingsActivity`.
  - Voicemail > Advanced Settings opens `com.android.phone/.settings.VoicemailSettingsActivity`.
  - Accessibility opens `com.android.phone/.settings.AccessibilitySettingsActivity`.
  - Blocked numbers opens `telecom/.settings.BlockedNumbersActivity`. Quick responses opens `telecom/.RespondViaSmsSettings`.
  - Messages: "use without an account" opens `ConversationListActivity`. The account menu, Archived, Spam & blocked, Device pairing (a sign-in screen), Messages settings and Start chat all stay in Messages.
- **POLICY_DENIED:**
  - Dialer: Help & feedback, and Settings > Sounds and vibration.
  - Messages: Sign In (from the first-run screen and from the account menu), and Help & feedback.

## Direct launches (run_case, lock task on)
- **TARGET_OPENED, allowed:**
  - Safety Hub `LauncherActivity` (goes on to `SafetyHubSelectAccountActivity`) and `AppActionsLauncherActivity`.
  - `com.android.phone` RadioInfo and CallFeaturesSetting.
  - Telecom `BlockedNumbersActivity` and `RespondViaSmsSettings`.
- **POLICY_DENIED:**
  - `NETWORK_OPERATOR_SETTINGS`, `GET_CONTENT image/*`, `OPEN_DOCUMENT */*`, `PICK_IMAGES`, `ADD_ACCOUNT_SETTINGS`.
  - Siblings: the stk launcher and the Settings launcher (new task and same task), `settings/.Settings`, `Settings$WifiSettingsActivity`, `stk/.StkMain`, `keychain/.KeyChainActivity`.
- **NO_HANDLER:**
  - `EDIT_EMERGENCY_INFO` and `com.android.phone/.settings.MobileNetworkSettings` don't exist on this image.
  - 4 Safety Hub rows are a script mistake: they are receivers or services, not activities.
- **TEST_FAILED:** Safety Hub CarCrashOnboarding and CarCrashSettings (SecurityException: not exported).

## UID inventory (`uid-inventory.txt`)
- Phone, UID 1001: phone, ons, mms.service, providers.telephony, stk.
- Telecom, UID 1000 (system): android, inputdevices, emulator.multidisplay, providers.settings, keychain, location.fused, dynsystem, DeviceAsWebcam, localtransport, settings, server.telecom.
- Dialer, Messages, Camera, Safety Hub and the DPC each have their own UID.

**Narrowed claim:** on API 36, lock task blocks *direct activity launches* by package, not by UID. Explicit launches of siblings that are not on the allowlist (stk, settings, keychain) were denied both with NEW_TASK and in the same task.

Not tested:
- a sibling turning on lock task itself;
- a sibling started through a service or broadcast;
- siblings that have no launcher activity.

## TEST_FAILED / not covered
- Dialer: Call history (`d-callhistory`, NO NODE) and Call Assist (`d-set-callassist`, NO CHANGE).
- Messages: "Manage in Settings" (`m-managesettings`) and the spam "Learn more" link (`m-spamlearnmore`). The spam banner had gone.
- Messages: Privacy Policy and Terms of Service (`m-menu-privacypolicy`, `m-menu-termsofservice`). The reset fails after a BlockedAppActivity case.
- Keyboard: settings gear, emoji/GIF search and voice input (`m-kbd` failed).
- The Messages attach button in the UI.
- Safety Hub help.
- Inline "Learn more" links: the tap hits the middle of the paragraph, not the link.
- No lock-task-off reachability control for the denied *UI menu* routes. The denied *direct launches* have a control in `../matrix-a36-v1-control.tsv`.
- `walks.tsv` keeps the superseded first-batch rows. Use the latest row for each id.
