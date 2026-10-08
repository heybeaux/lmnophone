## ADDED Requirements

### Requirement: Trusted-person PIN
Trusted-person mode SHALL require a PIN of at least 6 digits, stored only as a salted PBKDF2 hash. After 5 wrong attempts, Plainphone SHALL lock out PIN entry with a doubling delay, measured on monotonic time so that changing the clock has no effect. After 10 wrong attempts it SHALL text the trusted person. Wrong attempts SHALL NOT wipe the device.

#### Scenario: Guessing the PIN
- **WHEN** someone enters 10 wrong PINs
- **THEN** entry is locked for a growing period, the trusted person receives a text, and the phone is not wiped

### Requirement: Recovery code
Setup SHALL show a one-time recovery code and require the helper to confirm it was written down. The recovery code SHALL reset the PIN.

#### Scenario: Forgotten PIN
- **WHEN** the trusted person enters the recovery code
- **THEN** they can set a new PIN, and the old recovery code stops working and a new one is shown

### Requirement: App requests reach the trusted person
The user SHALL be able to request an app with a short free-text reason. Plainphone SHALL send the request to the trusted person by SMS from the phone. It SHALL also list pending requests in trusted-person mode, where each can be approved (starting a supervised session) or declined with an optional note shown to the user.

#### Scenario: Request an app
- **WHEN** the user asks for "Venmo – to pay Mom back"
- **THEN** the trusted person gets a text with that request, and the request appears in trusted-person mode

#### Scenario: Decline
- **WHEN** the trusted person declines with a note
- **THEN** the user sees the note the next time they open "My requests"

### Requirement: Plain disclosure of leftover risk
Trusted-person mode SHALL include a "What Plainphone can't stop" screen, in plain words. It SHALL list web pages inside approved apps, Wi-Fi sign-in pages, and a hardware-button factory reset, and say what each means.

#### Scenario: Trusted person reads limits
- **WHEN** the trusted person opens "What Plainphone can't stop"
- **THEN** each leftover risk is described in one or two plain sentences with no technical terms

### Requirement: Turning Plainphone off
The trusted person SHALL be able to turn Plainphone off after entering the PIN and confirming twice. Turning off SHALL clear Plainphone's restrictions, unhide apps, end lock task, and remove Device Owner, without erasing user data where Android allows it. If Android requires erasing data, Plainphone SHALL say so before the final confirmation.

#### Scenario: Turn off
- **WHEN** the trusted person confirms "Turn Plainphone off" twice
- **THEN** the phone behaves like a normal Android phone, and photos and messages are still there (subject to spike Q7)
