## ADDED Requirements

### Requirement: Plainphone runs as the Device Owner and the home screen
Plainphone SHALL be the device's Device Owner. It SHALL be the persistent preferred home activity, set with `addPersistentPreferredActivity` for `CATEGORY_HOME`. It SHALL block changes to default apps with `DISALLOW_CONFIG_DEFAULT_APPS` where the API level supports it.

#### Scenario: User tries to pick another home screen
- **WHEN** the user presses Home or tries to change the default launcher
- **THEN** the Plainphone home screen opens and no launcher chooser is shown

#### Scenario: User tries to uninstall Plainphone
- **WHEN** any path tries to uninstall or deactivate Plainphone without the trusted-person off switch
- **THEN** Android refuses, because a Device Owner cannot be uninstalled or deactivated by the user

### Requirement: Escape routes are closed by policy
Plainphone SHALL apply and keep applied, at minimum, these restrictions:
- `DISALLOW_SAFE_BOOT`, `DISALLOW_FACTORY_RESET`, `DISALLOW_DEBUGGING_FEATURES`
- `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH`, `DISALLOW_ADD_MANAGED_PROFILE`
- `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY`, `DISALLOW_USB_FILE_TRANSFER`
- `DISALLOW_CONFIG_VPN`, `DISALLOW_CONFIG_PRIVATE_DNS`, `DISALLOW_NETWORK_RESET`
- `DISALLOW_CONFIG_DATE_TIME`, `DISALLOW_SYSTEM_ERROR_DIALOGS`, `DISALLOW_CREATE_WINDOWS`
- `DISALLOW_MODIFY_ACCOUNTS`, outside supervised trusted-person sessions

It SHALL limit accessibility services and input methods to system-provided ones. It SHALL NOT set `DISALLOW_INSTALL_APPS` permanently (see approved-apps).

#### Scenario: Safe mode attempt
- **WHEN** the user long-presses power off to reboot into safe mode
- **THEN** the device does not enter safe mode

#### Scenario: OEM unlocking stays off
- **WHEN** `DISALLOW_FACTORY_RESET` is applied
- **THEN** the OEM unlocking setting cannot be turned on

#### Scenario: Debugging is unavailable after setup
- **WHEN** setup is complete
- **THEN** USB and wireless debugging cannot be enabled and adb cannot connect

### Requirement: Approved apps run in lock task mode
Plainphone SHALL run in multi-app lock task mode. The lock task packages SHALL be exactly Plainphone, the approved apps and the protected packages. Lock task features SHALL include `HOME`, `KEYGUARD`, `GLOBAL_ACTIONS`, `NOTIFICATIONS` and `SYSTEM_INFO`, and SHALL NOT include `OVERVIEW`. Settings, the Play Store, browsers and unapproved apps SHALL NOT be lock task packages, except during a supervised trusted-person session.

#### Scenario: An approved app tries to open Settings in a new task
- **WHEN** an approved app starts a Settings activity in a new task
- **THEN** the activity does not open

#### Scenario: Quick Settings
- **WHEN** the user pulls down the notification shade
- **THEN** notifications are shown and Quick Settings tiles are not usable

### Requirement: Lockdown survives restarts and drift
Plainphone SHALL re-apply the full policy, idempotently, on boot, on every package added, changed or removed, after every trusted-person action, and at least hourly. On boot it SHALL re-enter lock task mode without user action.

#### Scenario: Reboot
- **WHEN** the phone restarts
- **THEN** the Plainphone home screen appears in lock task mode with every restriction still in place

#### Scenario: A system update unhides a package
- **WHEN** an unapproved package becomes visible after an update
- **THEN** Plainphone hides it again within one policy pass
