## ADDED Requirements

### Requirement: Plainphone never obstructs emergency access
The **never-hide list** SHALL be the build inventory's never-hide dependencies (device-lockdown). It covers at least:
- the system dialer, Telecom and the in-call UI;
- the cell broadcast service, receiver and alert display;
- Google Play services;
- the default SMS app;
- every manufacturer emergency component found on that build by Q13, such as an SOS or emergency-information app on Motorola.

The policy engine SHALL refuse any policy that would hide, suspend, disable or uninstall a package on this list, or disable any of its components.

The system dialer SHALL always be on the lock-task UI list. `LOCK_TASK_FEATURE_KEYGUARD` and `LOCK_TASK_FEATURE_HOME` SHALL always be set.

**Invariant: 9-1-1 is always reachable, and never from an accidental tap on the home screen** (Beaux, Telegram 4487). The routes are:
- **Phone:** the system dialer, reached from Phone on the home screen. From inside an approved app, the user presses Home, then Phone. Dialling 9-1-1 there SHALL work under lock task with in-task launches blocked.
- **Lock screen:** stock Android's "Emergency call".
- **Power button:** the Emergency SOS gesture (press power 5 times), where the build has it **and** Q14 shows it works while Plainphone's lock task is active. Otherwise the setup guide SHALL NOT mention it.
- **Optional:** an "Emergency 9-1-1" entry inside More (calm-home-screen).

Any emergency intent Plainphone sends SHALL be `ACTION_DIAL` for `tel:911` sent explicitly to the system dialer package, after checking that the dialer resolves. The power-menu emergency entry, where the build has one, SHALL remain available.

#### Scenario: A policy tries to hide the dialer
- **WHEN** any code path asks the policy engine to hide, suspend or disable the system dialer or one of its components
- **THEN** the request is refused and logged, and the dialer stays usable

#### Scenario: 911 in every state
- **WHEN** the user tries to reach the emergency dialer in each of these states:
  - from the lock screen before the first unlock
  - from the lock screen
  - from home (through Phone)
  - from inside an approved app (Home, then Phone)
  - with the power-button SOS gesture, where Q14 confirmed it
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

Results SHALL be recorded honestly. Where the image cannot inject an alert, the alert check and its negative control SHALL be recorded as **inconclusive**. Inconclusive is never a pass, and it blocks release until a supported image or the device check below covers it.

The gate SHALL **fail** for each of these negative-control builds:
- Emergency resolves to nothing;
- the dialer is removed from the lock-task UI list;
- the alert-display component is disabled.

A failing gate SHALL block release. If Beaux records a build-scoped exception for unverified alert display, that exception SHALL name whether the disabled-alert negative control was also unverified. The exception releases only that build, and only for alerts. Emulator results SHALL NOT be presented as proof that a real 9-1-1 call gets through.

#### Scenario: Broken click handler
- **WHEN** the negative-control build in which Emergency resolves to nothing runs the gate
- **THEN** the gate fails

### Requirement: Real-device emergency checks before handover to a user
Before a phone is handed to Beaux or the trusted person for daily use, all of the following SHALL pass on that phone's model and build:

- **Emergency dialer.** It opens in every state listed above, without placing a call.
- **Incoming calls.** An incoming call can be answered under lock task, and its in-call keypad sends tones (DTMF) to the caller. E-Comm's automatic callback needs a keypress.
- **Alert display, observed.** An alert is actually seen on the phone, using one of these:
  - an Alert Ready scheduled public test that is observed on the phone;
  - a carrier- or manufacturer-supported test method that Beaux approves.

  Seeing that the receiver is present is not enough.
- **Manufacturer components.** The manufacturer emergency components found by Q13 are on the never-hide list.

**If alert display cannot be observed,** the phone SHALL do one of these:
- stay in supervised test custody;
- be handed over only after Beaux explicitly accepts, in a recorded decision, that alert display is unverified on that build. The promise is then narrowed to "alerts are not blocked by Plainphone's policy".

**Live 9-1-1 test call.** One SHALL be placed only under a separate procedure, agreed with E-Comm or the carrier and approved by Beaux.

#### Scenario: Callback keypad
- **WHEN** a test call comes in on the phone in lock task, and the user presses 1 on the in-call keypad
- **THEN** the caller hears the tone

#### Scenario: Alert not observable
- **WHEN** no Alert Ready test happens during the test window, and there is no approved test method
- **THEN** the phone is not handed over unless Beaux has recorded his acceptance of the narrowed promise

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

Plainphone SHALL NOT block emergency alerts (Alert Ready). On each supported build, alert display is verified as described in the real-device check above. Where Beaux has accepted a build-scoped exception, the promise is limited to "Plainphone does not block alerts".

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
