## ADDED Requirements

### Requirement: Plainphone never obstructs emergency access
These packages SHALL be on the **never-hide list**:
- the system dialer
- Telecom
- the cell broadcast receiver
- Google Play services
- the default SMS app

The policy engine SHALL refuse any policy that would hide, suspend, disable or uninstall a never-hide package, or disable any of its components. The system dialer SHALL always be on the lock-task UI list, and `LOCK_TASK_FEATURE_KEYGUARD` SHALL always be set. The home screen Emergency button SHALL send `ACTION_DIAL` for `tel:911`, explicitly to the system dialer. It SHALL check that the target resolves.

#### Scenario: Policy tries to hide the dialer
- **WHEN** any code path asks the policy engine to hide, suspend or disable the system dialer or one of its components
- **THEN** the request is refused and logged, and the dialer stays usable

#### Scenario: 911 from the lock screen
- **WHEN** the phone is locked and the user taps Emergency call on the lock screen
- **THEN** the system emergency dialer opens

#### Scenario: 911 from anywhere in Plainphone
- **WHEN** the user taps Emergency from home, from an approved app, mid-setup, during a PIN lockout, during a Play session, or after Plainphone restarts
- **THEN** the system dialer opens with 911 filled in

### Requirement: Emergency release gate is behavioral, with a negative control
The build SHALL include an instrumented UI test that does all of the following:
- taps Emergency in each state listed above, and checks the system dialer's activity is in front;
- dials an emulator-simulated emergency number through Telecom;
- checks that the never-hide list and lock-task settings are in place.

A test build whose Emergency button resolves to nothing SHALL fail this gate. A failing gate SHALL block release. Emulator results SHALL NOT be reported as proof of real 9-1-1 delivery. That needs a real phone Beaux has approved, and a safe test procedure agreed with the carrier.

#### Scenario: Broken click handler
- **WHEN** the negative-control build (Emergency resolves to nothing) runs the gate
- **THEN** the gate fails

#### Scenario: Regression
- **WHEN** a code change drops the dialer from the lock-task UI list
- **THEN** the gate fails, and the build cannot be released

### Requirement: Incoming calls are never filtered
Plainphone SHALL NOT block, screen, silence or hide incoming calls. In particular, Plainphone SHALL NOT interfere with E-Comm's automatic callback after a 9-1-1 hang-up.

#### Scenario: Callback after 911
- **WHEN** a call arrives from an unknown 604 number minutes after a 9-1-1 call
- **THEN** it rings and can be answered like any other call

### Requirement: Calls, texts and alerts work like a normal phone
Google Messages SHALL be set as the default SMS app with `setDefaultSmsApplication`. Incoming calls, SMS, MMS and group texts SHALL work while in lock task. Emergency alerts (Alert Ready / wireless public alerting) SHALL always be shown. Setup SHALL tell the helper that RCS and MMS need a plan with mobile data.

#### Scenario: Incoming call while another app is open
- **WHEN** a call arrives while the user is in an approved app
- **THEN** the incoming call screen appears, and the call can be answered

#### Scenario: Group text with an iPhone user
- **WHEN** a group message that includes an iPhone user arrives
- **THEN** it appears in Messages (as RCS or MMS, depending on the carrier), with no Plainphone screen in the way
