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
- `DISALLOW_MODIFY_ACCOUNTS`

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
- the system dialer, and the in-call screen package if it is separate
- the minimum UI entries in the build inventory
- the packages of an open supervised session, while that session is open

Lock task features SHALL include `HOME`, `KEYGUARD`, `GLOBAL_ACTIONS`, `NOTIFICATIONS`, `SYSTEM_INFO` and `BLOCK_ACTIVITY_START_IN_TASK`. They SHALL NOT include `OVERVIEW`.

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

### Requirement: One reviewed inventory per supported build drives protection, sweeping and fallback
Plainphone SHALL ship, compiled into the APK, a versioned **build inventory** for each supported phone model and build fingerprint. Each inventory has two lists.

**(a) Never-hide dependencies.** These SHALL never be hidden, suspended or disabled:
- system dialer, Telecom and the in-call UI;
- cell broadcast service, receiver and alert display;
- Google Play services and the Play Store;
- the default SMS app;
- the selected keyboard;
- the permission controller;
- the WebView provider;
- setup and account components needed on that build;
- manufacturer emergency components found by Q13.

**(b) Minimum UI entries.** The packages whose screens lock task SHALL allow:
- Plainphone;
- the system dialer and in-call UI;
- Messages;
- any others Q1 proves necessary.

**Rules:**
- **Precedence.** Inventory entries outrank allowlist decisions and classifier results.
- **No inventory, no sweep.** If the running build has no inventory, or its inventory fails validation, Plainphone SHALL NOT run a broad hide sweep, and setup SHALL NOT report Ready.

#### Scenario: Unknown build
- **WHEN** Plainphone runs on a build fingerprint that has no inventory, for example after an OTA
- **THEN** it keeps the current hidden state, hides nothing new except packages that match the browser classifier, and shows "Plainphone needs attention: this phone software isn't reviewed yet"

### Requirement: Policy storage is atomic, validated, and falls back to an emergency-safe baseline
Plainphone SHALL write its policy atomically, check its schema version before applying it, and keep a last-known-good copy.

**Device-protected copy.** A minimal copy, protected by a checksum, SHALL be kept in device-protected storage. It holds:
- the setup-complete flag;
- the PIN and recovery counters;
- the supervised-session journal.

**Emergency-safe baseline.** The baseline SHALL be compiled into the APK and SHALL NOT depend on any copy that can change. It consists of:
- the build inventory's never-hide dependencies and minimum UI entries;
- Messages;
- every restriction.

**When the baseline applies.** If both the policy and its last-known-good copy are invalid, Plainphone SHALL apply the baseline and show "Plainphone needs attention".

**Order of application.** Plainphone SHALL apply policy in this order, reading back each step:
1. revoke the session journal (a damaged journal means revoke all relaxations);
2. restrictions;
3. never-hide guard;
4. hides;
5. lock-task packages and features;
6. home preference.

**Locked-boot path.** The path that runs at `LOCKED_BOOT_COMPLETED` SHALL use only device-protected data. It SHALL do only steps 1 and 2 and the never-hide guard, then read them back.

**When to re-apply.** Plainphone SHALL re-apply policy at locked boot and unlocked boot, on every package event, after every trusted-person action, and every 15 minutes.

**Limits that SHALL be disclosed:**
- The baseline helps only while Plainphone can run.
- When Plainphone cannot run, the phone relies on the system lock screen, the system dialer, and the recovery path.

#### Scenario: Corrupt policy and backup
- **WHEN** both the policy file and its last-known-good copy are truncated
- **THEN** Plainphone applies the emergency-safe baseline. Calls (incoming and outgoing), the in-call UI, Emergency, alerts and SMS keep working, and "Plainphone needs attention" is shown.

#### Scenario: Damaged journal
- **WHEN** the session journal's checksum fails
- **THEN** Plainphone removes every possible session package from lock task and runs the protected-state check before the user can use the phone

#### Scenario: An update unhides a package
- **WHEN** an unapproved package becomes visible after a system or app update on a build that has an inventory
- **THEN** Plainphone hides it again within 15 minutes, and lock task stops it opening in the meantime
