## ADDED Requirements

### Requirement: Plainphone runs as the Device Owner and the home screen
Plainphone SHALL be the device's Device Owner and the persistent preferred home activity, set with `addPersistentPreferredActivity` for `CATEGORY_HOME`. Plainphone SHALL block changes to default apps with `DISALLOW_CONFIG_DEFAULT_APPS` where the API level supports it.

#### Scenario: User tries to pick another home screen
- **WHEN** the user presses Home or tries to change the default launcher
- **THEN** the Plainphone home screen opens and no launcher chooser is shown

#### Scenario: User tries to uninstall Plainphone
- **WHEN** any path tries to uninstall or deactivate Plainphone outside the trusted-person off switch
- **THEN** Android refuses, because a Device Owner cannot be uninstalled or deactivated by the user

### Requirement: Escape routes are closed by policy
Plainphone SHALL apply, and keep applied, at least these restrictions:
- `DISALLOW_SAFE_BOOT`, `DISALLOW_FACTORY_RESET`, `DISALLOW_DEBUGGING_FEATURES`
- `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH`, `DISALLOW_ADD_MANAGED_PROFILE`
- `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY`, `DISALLOW_USB_FILE_TRANSFER`
- `DISALLOW_CONFIG_VPN`, `DISALLOW_CONFIG_PRIVATE_DNS`, `DISALLOW_NETWORK_RESET`
- `DISALLOW_CONFIG_DATE_TIME`, `DISALLOW_SYSTEM_ERROR_DIALOGS`, `DISALLOW_CREATE_WINDOWS`
- `DISALLOW_MODIFY_ACCOUNTS`

Lifting a restriction is allowed only during the authenticated session that needs it (the Play session or maintenance mode), and it SHALL be restored and read back when that session ends. Accessibility services and input methods SHALL be limited to system-provided ones. Plainphone SHALL NOT set `DISALLOW_INSTALL_APPS` permanently.

#### Scenario: Safe mode attempt
- **WHEN** the user long-presses power off to reboot into safe mode
- **THEN** the device does not enter safe mode

#### Scenario: OEM unlocking stays off
- **WHEN** `DISALLOW_FACTORY_RESET` is applied
- **THEN** the OEM unlocking setting cannot be turned on

#### Scenario: Debugging is unavailable after setup
- **WHEN** setup is complete
- **THEN** USB and wireless debugging cannot be enabled, and a previously authorised adb host cannot connect

### Requirement: Lock task blocks other apps' screens, including inside a task
Plainphone SHALL run in multi-app lock task mode.

The lock task packages SHALL be exactly the **lock-task UI list**:
- Plainphone
- the approved apps
- the system dialer, and the in-call screen package if it is separate
- the reviewed UI exceptions list

Lock task features SHALL include `HOME`, `KEYGUARD`, `GLOBAL_ACTIONS`, `NOTIFICATIONS`, `SYSTEM_INFO` and `BLOCK_ACTIVITY_START_IN_TASK`. They SHALL NOT include `OVERVIEW`.

Each UI exception SHALL have a negative test showing that Settings, the Play Store and web content cannot be reached through it. Packages sharing a UID with a lock-task package SHALL be listed and covered by the same tests.

#### Scenario: An approved app opens Settings in a new task
- **WHEN** an approved app starts a Settings activity with `FLAG_ACTIVITY_NEW_TASK`
- **THEN** the activity does not open

#### Scenario: An approved app opens Settings or Play inside its own task
- **WHEN** an approved app starts a Settings, Settings-panel or Play activity without `FLAG_ACTIVITY_NEW_TASK`, by implicit intent, explicit component, or via the share chooser
- **THEN** the activity does not open

#### Scenario: Quick Settings
- **WHEN** the user pulls down the notification shade
- **THEN** notifications are shown and Quick Settings tiles are not usable

### Requirement: Reset protection names the trusted person
Plainphone SHALL set `setFactoryResetProtectionPolicy` with the trusted person's Google `userId` list and FRP enabled, then send `com.google.android.gms.auth.FRP_CONFIG_CHANGED` explicitly to Google Play services. Plainphone SHALL read the policy back and confirm the accounts match. The trusted person's account SHALL NOT be left signed in on the phone. If the policy cannot be set or read back, setup SHALL NOT report Ready. In that case the only way forward is for Beaux to accept the gap explicitly in writing.

#### Scenario: Reset protection configured
- **WHEN** setup reaches the handover check
- **THEN** the reset-protection readback lists exactly the trusted person's `userId`, and the check shows green

#### Scenario: Wipe from recovery mode
- **WHEN** the phone is wiped from recovery mode and the user tries to set it up with their own Google account
- **THEN** setup requires the trusted person's Google account instead (test Q10)

### Requirement: Policy storage and boot are safe and bounded
Plainphone SHALL store its policy with an atomic write, validate the schema version before applying it, and keep a last-known-good copy. A minimal copy SHALL be kept in device-protected storage: the setup-complete flag, the never-hide list, the lock-task UI list and the PIN attempt state.

Plainphone SHALL apply the policy in this order:
1. restrictions
2. never-hide guard
3. hides
4. lock task packages and features
5. home preference

Each step SHALL be read back. A step that fails SHALL be retried, then the last-known-good policy SHALL be used. A persistent failure SHALL be shown to the trusted person as "Plainphone needs attention".

Plainphone SHALL re-apply the policy on `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, every package added, changed or removed, every trusted-person action, and every 15 minutes. Within 60 seconds of `BOOT_COMPLETED` it SHALL be in lock task with the full policy in place.

#### Scenario: Reboot
- **WHEN** the phone restarts and the user unlocks it
- **THEN** within 60 seconds the Plainphone home screen is in lock task with every restriction in place, and 9-1-1 works throughout

#### Scenario: Corrupt policy
- **WHEN** the policy file is truncated or carries an unknown schema version
- **THEN** Plainphone applies the last-known-good policy, never touches a never-hide package, and shows "Plainphone needs attention" in trusted-person mode

#### Scenario: An update unhides a package
- **WHEN** an unapproved package becomes visible after a system or app update
- **THEN** Plainphone hides it again within 15 minutes, and lock task stops it from opening in the meantime
