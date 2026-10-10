## ADDED Requirements

### Requirement: Plainphone runs as the Device Owner and the home screen
Plainphone SHALL be the device's Device Owner. It SHALL also be the persistent preferred home activity, set with `addPersistentPreferredActivity` for `CATEGORY_HOME`. Plainphone SHALL block changes to default apps with `DISALLOW_CONFIG_DEFAULT_APPS` where the API level supports it.

#### Scenario: User tries to pick another home screen
- **WHEN** the user presses Home or tries to change the default launcher
- **THEN** the Plainphone home screen opens and no launcher chooser is shown

#### Scenario: User tries to uninstall Plainphone
- **WHEN** any path tries to uninstall or deactivate Plainphone, other than the trusted-person off switch, on a release build (`testOnly=false`)
- **THEN** Android refuses, because a Device Owner cannot be uninstalled or deactivated by the user

### Requirement: Escape routes are closed by policy
Plainphone SHALL apply, and keep applied, at least these restrictions:
- `DISALLOW_SAFE_BOOT`, `DISALLOW_FACTORY_RESET`, `DISALLOW_DEBUGGING_FEATURES`
- `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH`, `DISALLOW_ADD_MANAGED_PROFILE`
- `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY`, `DISALLOW_USB_FILE_TRANSFER`
- `DISALLOW_CONFIG_VPN`, `DISALLOW_CONFIG_PRIVATE_DNS`, `DISALLOW_NETWORK_RESET`
- `DISALLOW_CONFIG_DATE_TIME`, `DISALLOW_SYSTEM_ERROR_DIALOGS`, `DISALLOW_CREATE_WINDOWS`
- `DISALLOW_MODIFY_ACCOUNTS`, `DISALLOW_CONFIG_CELL_BROADCASTS`

A supervised session SHALL NOT lift `DISALLOW_SAFE_BOOT`, `DISALLOW_FACTORY_RESET` or `DISALLOW_DEBUGGING_FEATURES`. Accessibility services and input methods SHALL be limited to system-provided ones. Plainphone SHALL NOT set `DISALLOW_INSTALL_APPS` permanently.

#### Scenario: Safe mode attempt
- **WHEN** the user long-presses power off to reboot into safe mode
- **THEN** the device does not enter safe mode

#### Scenario: OEM unlocking stays off
- **WHEN** `DISALLOW_FACTORY_RESET` is applied, including on a phone where the reset left `sys.oem_unlock_allowed=1`
- **THEN** the OEM unlocking setting cannot be turned on

#### Scenario: Debugging is unavailable after setup
- **WHEN** setup is complete
- **THEN** USB and wireless debugging cannot be enabled, and a previously authorised adb host cannot connect

### Requirement: Lock task blocks other apps' screens, including within a task
Plainphone SHALL run in multi-app lock task mode.

The lock task packages SHALL be exactly the **lock-task UI list**:
- Plainphone
- the approved apps
- the **emergency UI subset** from the runtime emergency floor: the system dialer, the system emergency-dialer package, and the system dialer's in-call UI package
- the minimum UI entries in the build inventory
- the packages of an open supervised session, while that session is open

Lock task features SHALL include `HOME`, `KEYGUARD`, `GLOBAL_ACTIONS`, `NOTIFICATIONS`, `SYSTEM_INFO` and `BLOCK_ACTIVITY_START_IN_TASK`. Device test PLA-5 A3 on the XT2415, with `BLOCK_ACTIVITY_START_IN_TASK` **off**, found three screens opening inside the locked task:
- App info, leading on to the Permissions and Notifications settings;
- the generic share chooser;
- Google Photos, opened from the Camera thumbnail.

With the flag **on**, App info and the share chooser were blocked. The Photos route was **not run** with the flag on (Q1 follow-up). They SHALL NOT include `OVERVIEW`.

Each minimum UI entry, apart from Plainphone and approved apps, SHALL have a negative test showing that Settings, the Play Store and web content cannot be reached through it. Packages that share a UID with a lock-task package SHALL be listed and covered by the same tests.

#### Scenario: An approved app opens Settings in a new task
- **WHEN** an approved app starts a Settings activity with `FLAG_ACTIVITY_NEW_TASK`
- **THEN** the activity does not open

#### Scenario: An approved app opens Settings or Play inside its own task
- **WHEN** an approved app starts a Settings, Settings-panel or Play activity without `FLAG_ACTIVITY_NEW_TASK`, whether by implicit intent, explicit component, or the share chooser
- **THEN** the activity does not open

#### Scenario: Quick Settings
- **WHEN** the user pulls down the notification shade
- **THEN** notifications are shown and Quick Settings tiles cannot be used

### Requirement: Reset protection names the trusted person and a backup person
Plainphone SHALL call `setFactoryResetProtectionPolicy` with FRP enabled and a list of one or two Google `userId` values:
- the trusted person's;
- optionally, a backup person's.

It SHALL then send `com.google.android.gms.auth.FRP_CONFIG_CHANGED` explicitly to Google Play services. Plainphone SHALL read the policy back and confirm it matches. Neither account SHALL be signed in on the phone.

If the policy cannot be set or read back, setup SHALL NOT report Ready. The one exception is an explicit, recorded acceptance by Beaux.

FRP does not reinstall Plainphone, and the disclosure SHALL say so.

#### Scenario: Reset protection configured
- **WHEN** setup reaches the handover check
- **THEN** the readback lists exactly the entered `userId` values, and the check shows green

#### Scenario: Wipe from recovery mode
- **WHEN** the phone is wiped from recovery mode and someone tries to set it up with the user's own Google account
- **THEN** setup requires a listed account instead. This is test Q10, which needs a separate board-approved procedure on the device being tested.

### Requirement: No unapproved app can be opened from the first interactive frame
After boot, the Plainphone home activity SHALL start lock task before it draws its first interactive frame.

Restrictions, hidden apps and lock-task package settings are kept by Android across reboots. Plainphone SHALL NOT clear them during recovery.

From unlock until lock task starts, no unapproved app SHALL be openable from:
- a notification
- an explicit intent
- a deep link

#### Scenario: Immediate unlock after reboot
- **WHEN** the user unlocks right after a reboot and taps a queued notification from an unapproved app
- **THEN** the app does not open

### Requirement: A runtime emergency floor, from OS-managed identities only, that does not depend on any inventory or policy file
At every policy pass, Plainphone SHALL compute an **emergency floor** from the live system.

**Who can join the floor.** Only these may be floor members:
- packages named by an OS-managed identity;
- packages that answer an OS query restricted to system packages.

A package that merely declares a matching intent filter SHALL NOT join the floor. A package that is not a system app (`ApplicationInfo.FLAG_SYSTEM` unset) SHALL NOT join the floor by any route.

**Floor members (never hidden, suspended or disabled):**

| Member | How it is identified |
|---|---|
| System dialer | `TelecomManager.getSystemDialerPackage()` |
| Default dialer | `ROLE_DIALER` holder, only if it is a system app |
| Emergency dialer package | Resolves `android.intent.action.CALL_EMERGENCY` or the emergency-dialer intent, queried with `MATCH_SYSTEM_ONLY`. On the XT2415 this is `com.android.phone` (PLA-5 A3, with the flag off) |
| In-call UI | The `InCallService` package of the system dialer |
| SMS app | The `ROLE_SMS` holder, only if it is a system app |
| Cell broadcast receive and display | Resolved with `MATCH_SYSTEM_ONLY` |
| WebView provider | `WebView.getCurrentWebViewPackage()` |
| Keyboards | Enabled system input methods (`InputMethodInfo` for system apps) |
| Permission controller | Resolves `android.content.pm.action.REQUEST_PERMISSIONS` with `MATCH_SYSTEM_ONLY` |

**Emergency UI subset.** Only these floor members SHALL be added to the lock-task UI list:
- the system dialer;
- the system emergency-dialer package;
- the system dialer's in-call UI package.

The permission controller, keyboards and the WebView provider are never-hide only. They are not lock-task UI entries, unless Q1 shows one must be, and it then gets its own negative tests.

The floor SHALL be computed without any inventory or policy file, and SHALL outrank every other list.

#### Scenario: Emergency dialer package not in the inventory
- **WHEN** a build's system emergency dialer lives in a package the inventory doesn't name
- **THEN** the floor adds that package to the lock-task UI list, and the emergency dialer opens from home

#### Scenario: Untrusted app advertises an emergency filter
- **WHEN** an unapproved user-installed app declares an activity with the exact emergency-dialer action and category
- **THEN** it does not join the floor, it is still hidden, and it cannot be opened in-task or as a new task. This holds on supported builds, unknown builds, and unknown builds with corrupt policy.

#### Scenario: Previously hidden system emergency component
- **WHEN** after an OTA a hidden **system** package becomes the `MATCH_SYSTEM_ONLY` resolver for the emergency dialer
- **THEN** Plainphone unhides it and adds it to the lock-task UI list, including on the locked-boot path

### Requirement: One reviewed inventory per supported build drives sweeping and minimum UI
Plainphone SHALL ship, compiled into the APK, a versioned **build inventory** for each supported model and build fingerprint. The inventory SHALL add to the emergency floor.

**(a) Never-hide dependencies.** Packages that SHALL never be hidden, suspended or disabled:
- Google Play services and the Play Store;
- setup and account components that the build needs;
- manufacturer emergency components found by Q13;
- every package the floor would compute on that build.

**(b) Minimum UI entries.** Screens that lock task SHALL allow, beyond the floor: Messages, plus any screens Q1 proves necessary.

**Precedence.** The floor outranks the inventory. The inventory outranks allowlist decisions and classifier results.

#### Scenario: Supported build
- **WHEN** the running fingerprint has a valid inventory
- **THEN** the full sweep, classifier hiding and minimum UI entries apply

### Requirement: System updates follow a maintained lifecycle, and unknown builds fail safe
**Delaying updates.** Plainphone SHALL set `SystemUpdatePolicy.createPostponeInstallPolicy()`, which delays updates up to Android's limit. The disclosure SHALL say that some security updates may install anyway.

**When an update is pending.** On `onSystemUpdatePending`, Plainphone SHALL text the trusted person and the helper: "A phone software update is coming. Plainphone needs a review."

**Ownership.** The Plainphone team owns reviewing each new build and delivering its inventory. The Android Platform Engineer prepares it; the Verifier reviews it. Delivery is a signed Plainphone update over local Wi-Fi.

**On a fingerprint with no inventory (an unknown build), Plainphone SHALL:**
1. keep lock task running, with the previous UI list plus the emergency UI subset;
2. unhide any previously hidden package that the floor now computes, so emergency dependencies fail open. Only the emergency UI subset is added to lock task; other floor members (keyboards, WebView, permission controller) are unhidden but get no UI access;
3. hide nothing new except user-installed (non-system) packages. It SHALL NOT hide newly classified system packages.
4. record which packages were added or changed compared with the last supported build, and show them in trusted-person mode under "Phone software changed – review pending";
5. text the trusted person and the helper.

**What still holds on an unknown build.**
- Foreground eligibility remains limited to the lock-task UI list and Android's shared-UID extensions, subject to the residual risks below.
- New system packages are neither hidden nor approved. They cannot be opened in the foreground unless Android treats them as lock-task-eligible because they share a UID with an allowed package. That exception is the residual risk below.
- Residual risk: a new route inside an already-allowed package, or a new package that shares a UID with one. This SHALL be disclosed, and Beaux SHALL accept it as part of the custody and leftover-risk acceptance.

**Back to normal.** The phone returns to normal support only when all three are done:
- a reviewed inventory for the new build is installed;
- the handover self-check passes;
- on that build, the emergency dialer and incoming-call keypad checks from essential-calling-and-texting are re-run, and alert display is re-observed on **that** build. The alternative is a new build-scoped exception recorded by Beaux for that build. An earlier build's exception SHALL NOT carry over, even if the alert stack's version is unchanged.

#### Scenario: OTA to an unknown build
- **WHEN** the phone updates to a fingerprint that has no inventory
- **THEN** lock task stays on, emergency floor packages are unhidden, only the emergency UI subset is added to lock task, no system package is newly hidden, the trusted person and helper are texted, and trusted-person mode lists the changed packages

#### Scenario: A previously hidden package becomes an emergency dependency
- **WHEN** after an OTA, a **system** package Plainphone had hidden now resolves the emergency dialer intent under `MATCH_SYSTEM_ONLY`
- **THEN** Plainphone unhides it and adds it to the lock-task UI list on the next policy pass

### Requirement: Policy storage is atomic, validated, and falls back to an emergency-safe baseline
Plainphone SHALL write its policy atomically, check its schema version before applying it, and keep a last-known-good copy.

**Device-protected copy.** A minimal copy, protected by a checksum, SHALL be kept in device-protected storage. It holds:
- the setup-complete flag;
- the PIN and recovery counters;
- the supervised-session journal;
- the last supported build fingerprint.

**Emergency-safe baseline.** The baseline SHALL be compiled into the APK. It consists of:
- the runtime emergency floor;
- the matching build inventory, if one exists;
- Messages;
- Plainphone;
- every restriction.

**When the baseline applies.** If both the policy and the last-known-good copy are invalid, Plainphone SHALL apply the baseline and show "Plainphone needs attention".

**Unknown build and corrupt policy together.** If the build is unknown **and** both policy copies are invalid, the baseline SHALL be the floor plus Messages, Plainphone and every restriction. No sweep SHALL run. Existing hidden state SHALL be kept, except where the floor needs a package unhidden. Plainphone SHALL text the trusted person and the helper.

**Order of application.** Plainphone SHALL apply policy in this order, reading back each step:
1. revoke the session journal (a damaged journal means revoking every relaxation);
2. restrictions;
3. emergency floor;
4. never-hide guard;
5. hides;
6. lock-task packages and features;
7. home preference.

**Locked-boot path.** At `LOCKED_BOOT_COMPLETED`, Plainphone SHALL use only device-protected data. It SHALL do only these steps, then read them back:
- steps 1–3, where step 3 includes restoring the emergency UI subset's lock-task membership and keeping Plainphone itself in the lock-task list, so the home activity can start lock task at the first frame even if the list was lost;
- the lock-task features listed under *Lock task blocks other apps' screens*, including `BLOCK_ACTIVITY_START_IN_TASK`. They are fixed, so they need no policy file. Without them, an allowed app can open Settings inside its own task even while lock task is on (PLA-232 finding 9);
- the never-hide guard.

Emergency repair SHALL NOT depend on the session revoke succeeding. If step 1 fails, Plainphone SHALL keep the relaxations revoke-pending and SHALL NOT hand control back, but it SHALL still run steps 2–3, the lock-task features and the never-hide guard, and read them back.

**When to re-apply.** Plainphone SHALL re-apply policy at locked and unlocked boot, on package events, after trusted-person actions, and every 15 minutes.

**Disclosure.** The baseline helps only while Plainphone can run. If it cannot, the system lock screen, the system dialer and the recovery path remain.

#### Scenario: Corrupt policy and backup
- **WHEN** both the policy file and its last-known-good copy are truncated on a supported build
- **THEN** Plainphone applies the baseline. Calls, the in-call UI, Emergency, alerts and SMS keep working, and "Plainphone needs attention" is shown.

#### Scenario: Unknown build plus corrupt policy
- **WHEN** the build is unknown and both policy copies are invalid
- **THEN** Plainphone applies the floor-plus-Messages baseline, runs no sweep, keeps Emergency and calls working, and texts the trusted person and the helper

#### Scenario: Damaged journal
- **WHEN** the session journal's checksum fails
- **THEN** Plainphone removes every possible session package from lock task and runs the protected-state check, without blocking calls or Emergency
