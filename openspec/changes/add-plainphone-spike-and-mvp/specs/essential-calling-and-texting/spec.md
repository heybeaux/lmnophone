## ADDED Requirements

### Requirement: Plainphone never obstructs emergency access
The **never-hide list** SHALL contain:
- the system dialer
- Telecom
- the cell broadcast receiver, along with any separate alert-display package
- Google Play services
- the default SMS app
- every manufacturer emergency component found on the supported build by Q13, such as an SOS or emergency-information app on Motorola

The policy engine SHALL refuse any policy that would hide, suspend, disable or uninstall a package on this list, or disable any of its components.

The system dialer SHALL always be on the lock-task UI list. `LOCK_TASK_FEATURE_KEYGUARD` and `LOCK_TASK_FEATURE_HOME` SHALL always be set.

The home screen's Emergency button SHALL send `ACTION_DIAL` for `tel:911` explicitly to the system dialer, after checking that the dialer resolves. From inside an approved app, the user SHALL reach Emergency by pressing Home. The power-menu emergency entry, where the build has one, SHALL remain available.

#### Scenario: A policy tries to hide the dialer
- **WHEN** any code path asks the policy engine to hide, suspend or disable the system dialer or one of its components
- **THEN** the request is refused and logged, and the dialer stays usable

#### Scenario: 911 in every state
- **WHEN** the user tries to reach the emergency dialer in each of these states:
  - from the lock screen before the first unlock
  - from the lock screen
  - from home
  - from inside an approved app (by pressing Home)
  - during setup
  - during a PIN lockout
  - during a supervised session
  - during a Plainphone update
  - while turning Plainphone off
  - after Plainphone restarts
  - while Plainphone cannot start
- **THEN** the system emergency dialer opens each time. The last two states rely on the lock screen and system dialer, not on Plainphone.

### Requirement: The emergency release gate is behavioral, with negative controls
The build SHALL include an instrumented test of the emergency flow. It SHALL:
- go through every state listed above that the emulator can produce, and check that the system dialer's activity is in the foreground;
- dial an emulator-simulated emergency number through Telecom;
- check that an emulator-injected cell broadcast alert is displayed, where the image supports injection.

Results SHALL be recorded honestly. Where injection is not possible, the alert check SHALL be recorded as "not verified", never as "pass".

The gate SHALL **fail** for each of these negative-control builds:
- Emergency resolves to nothing;
- the dialer is removed from the lock-task UI list;
- the alert-display component is disabled.

A failing gate SHALL block release. Emulator results SHALL NOT be presented as proof that a real 9-1-1 call gets through.

#### Scenario: Broken click handler
- **WHEN** the negative-control build in which Emergency resolves to nothing runs the gate
- **THEN** the gate fails

### Requirement: Real-device emergency checks before handover to a user
Before a phone is handed to Beaux or the trusted person for daily use, each of these SHALL pass on that phone's model and build:
- the emergency dialer opens in every state above, without placing a call;
- an incoming call can be answered, and its in-call keypad sends tones (DTMF) to the caller during lock task. E-Comm's automatic callback menu needs a keypress;
- the alert receiver and its settings are present and not hidden;
- the manufacturer emergency components found by Q13 are on the never-hide list.

A live 9-1-1 test call SHALL be placed only under a separate procedure agreed with E-Comm or the carrier and approved by Beaux.

An Alert Ready message that could not be observed SHALL be recorded as "not verified".

Until all of these checks pass, the phone stays in supervised test custody.

#### Scenario: Callback keypad
- **WHEN** a test call comes in on the phone in lock task, and the user presses 1 on the in-call keypad
- **THEN** the caller hears the tone

### Requirement: Incoming calls are never filtered
Plainphone SHALL NOT block, screen, silence or hide incoming calls, and SHALL NOT interfere with E-Comm's automatic callback after a 9-1-1 hang-up.

#### Scenario: Callback after 911
- **WHEN** a call from an unknown 604 number arrives minutes after a 9-1-1 call
- **THEN** it rings, can be answered, and its keypad works

### Requirement: Calls, texts and alerts work like a normal phone
Google Messages SHALL be set as the default SMS app, using `setDefaultSmsApplication`.

While in lock task, all of these SHALL work:
- incoming calls
- SMS
- MMS
- group texts

Emergency alerts (Alert Ready) SHALL always be shown.

The setup guide SHALL say:
- RCS works over Wi-Fi or mobile data.
- A plan with data is recommended so that RCS and MMS work away from Wi-Fi.
- MMS needs mobile data on most carriers.

#### Scenario: Incoming call while another app is open
- **WHEN** a call arrives while the user is in an approved app
- **THEN** the incoming call screen appears, and the call can be answered

#### Scenario: Group text with an iPhone user
- **WHEN** a group message that includes an iPhone user arrives
- **THEN** it appears in Messages, with no Plainphone screen in the way
