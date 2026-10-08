## ADDED Requirements

### Requirement: Emergency calling can never be blocked
Plainphone SHALL treat these as protected packages: the system dialer, Telecom and Phone services, the cell broadcast receiver, Google Play services, and the default SMS app. The policy engine SHALL refuse any policy that would hide, suspend, disable or uninstall a protected package. The system dialer SHALL always be a lock task package, and `LOCK_TASK_FEATURE_KEYGUARD` SHALL always be set.

#### Scenario: Policy tries to hide the dialer
- **WHEN** any code path asks the policy engine to hide or suspend the system dialer
- **THEN** the request is refused and logged, and the dialer stays usable

#### Scenario: 911 from the lock screen
- **WHEN** the phone is locked and the user taps Emergency call on the lock screen
- **THEN** the system emergency dialer opens and can place the call

#### Scenario: 911 from the home screen
- **WHEN** the user taps Emergency on the Plainphone home screen
- **THEN** the system dialer opens with 911 filled in

### Requirement: Release gate for emergency access
The build SHALL include an instrumented test that fails if the dialer is hidden or suspended, if the dialer is missing from lock task packages, if the keyguard feature is missing, or if the Emergency button is absent. A failing test SHALL block release.

#### Scenario: Regression
- **WHEN** a code change drops the dialer from lock task packages
- **THEN** the emergency-access test fails and the build is not releasable

### Requirement: Calls and texts work like a normal phone
Google Messages SHALL be set as the default SMS app with `setDefaultSmsApplication`. Incoming calls, SMS, MMS and group texts SHALL work while in lock task. Wireless emergency alerts SHALL always be shown.

#### Scenario: Incoming call while another app is open
- **WHEN** a call arrives while the user is in an approved app
- **THEN** the incoming call screen appears and the call can be answered

#### Scenario: Group text with an iPhone user
- **WHEN** a group message including an iPhone user arrives
- **THEN** it appears in Messages (RCS or MMS, depending on carrier), with no Plainphone screen in the way
