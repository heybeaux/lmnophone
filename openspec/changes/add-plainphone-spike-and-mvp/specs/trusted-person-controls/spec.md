## ADDED Requirements

### Requirement: Trusted-person PIN resists guessing without permanent lockout
Trusted-person mode SHALL require a PIN of at least 6 digits. The PIN SHALL be stored only as a PBKDF2-HMAC-SHA256 hash, with at least 600,000 iterations and a random 16-byte salt.

The failed-attempt count SHALL be stored in device-protected storage, so it survives restarts and reboots. After 5 failures, each further failure SHALL add a delay that doubles, capped at 1 hour. A reboot SHALL restart the current delay in full.

After 10 failures Plainphone SHALL try to text the trusted person. Whether that text sends SHALL NOT affect enforcement. Wrong attempts SHALL NOT wipe the device.

#### Scenario: Guessing across reboots
- **WHEN** someone enters 4 wrong PINs, reboots, and keeps guessing
- **THEN** the count continues from 4, delays apply after the 5th failure, and no delay exceeds 1 hour

#### Scenario: No SIM or no signal
- **WHEN** the 10th failure happens with no SIM or no signal
- **THEN** lockout still applies, and the failed alert is recorded for trusted-person mode

### Requirement: Recovery code is high-entropy, single-use, and held by the trusted person
Setup SHALL generate a recovery code with at least 128 bits of randomness and show it once. Plainphone SHALL store only its hash. The helper SHALL confirm that the trusted person has the code. Code entry SHALL be rate-limited the same way as the PIN.

Using the code SHALL replace the PIN and the code in one atomic step: the new PIN, a new code, and invalidating the old code happen together or not at all. Setup and trusted-person mode SHALL explain that the code cannot unlock reset protection after a wipe. Only the trusted person's Google account can do that.

#### Scenario: Forgotten PIN
- **WHEN** the trusted person enters the recovery code
- **THEN** they set a new PIN, the old code stops working, and a new code is shown once

#### Scenario: Interrupted recovery
- **WHEN** the phone restarts in the middle of a recovery
- **THEN** either the old PIN and old code still work, or the new ones do. Never neither.

### Requirement: Privileged actions need fresh authority and no outside input
These actions SHALL each require a fresh PIN or recovery-code entry:
- approve an app
- remove an app
- start a Play session
- start maintenance mode
- turn Plainphone off

Admin activities SHALL NOT be exported. Plainphone SHALL NOT accept any outside intent, extra or flag as proof of authentication. Admin screens SHALL use `FLAG_SECURE`. Backup SHALL be disabled. Secrets SHALL NOT be logged.

#### Scenario: External intent claims authority
- **WHEN** another app sends Plainphone an intent with extras that claim to approve an app or turn Plainphone off
- **THEN** nothing changes, and no admin screen opens

### Requirement: App requests are untrusted text, sent to a fixed recipient
The user SHALL be able to request an app with a reason of up to 280 characters, at most 5 requests a day. Plainphone SHALL send the request by SMS to the trusted person's number set at setup, and list it in trusted-person mode. The request text SHALL be shown as "From [user]:", as plain unformatted text.

Approving a request SHALL start a supervised Play session, and SHALL bind the approval to the package that was actually installed and its signing certificate, not to the text the user wrote. If the SMS is not sent, Plainphone SHALL say so and keep the request listed.

#### Scenario: Request an app
- **WHEN** the user asks for "Venmo – to pay Mom back"
- **THEN** the trusted person gets a text with that request, and it is listed in trusted-person mode

#### Scenario: Misleading request
- **WHEN** a request names one app, and a different package is installed in the session
- **THEN** the approval screen shows the installed package's real name and developer, and the trusted person approves that package, not the text

### Requirement: Maintenance mode for Plainphone's own updates
After a fresh PIN entry, maintenance mode SHALL lift `DISALLOW_DEBUGGING_FEATURES` for at most 30 minutes, so a helper can install a newer Plainphone build signed with the same key. Maintenance mode SHALL follow the same end-of-session rules as the Play session. On ending, it SHALL restore and read back the policy.

#### Scenario: Plainphone update
- **WHEN** a helper installs a same-key Plainphone update during maintenance mode
- **THEN** the update keeps all policy and data, and debugging is off again when the session ends

### Requirement: Plain disclosure of leftover risk
Trusted-person mode SHALL include a screen called "What Plainphone can't stop". It SHALL list, in plain words:
- web pages inside approved apps
- any Google account or help screens found reachable during testing
- the Wi-Fi sign-in page, when the trusted person allows it
- background activity of an unapproved app before it is hidden
- a hardware-button wipe, and what reset protection does about it

#### Scenario: Trusted person reads limits
- **WHEN** the trusted person opens "What Plainphone can't stop"
- **THEN** each leftover risk is described in one or two plain sentences, with no technical terms

### Requirement: Turning Plainphone off is authenticated, resumable, and restores only its own changes
Turning off SHALL require a fresh PIN or recovery-code entry and two plain confirmations. Plainphone SHALL record which steps are done, and SHALL resume after a restart.

The steps SHALL be idempotent, and each SHALL be read back:
1. clear Plainphone's restrictions
2. unhide the apps Plainphone hid
3. end lock task
4. clear the persistent home preference
5. clear the reset-protection policy
6. remove Device Owner

Plainphone SHALL change only what it recorded changing. It SHALL report "Off" only after every step has been confirmed. If Android requires a data wipe, that SHALL be a separate choice, with data-loss wording shown before it.

#### Scenario: Turn off
- **WHEN** the trusted person confirms turning off with fresh authority
- **THEN** the phone works like a normal Android phone, and photos and messages are kept (subject to test Q7)

#### Scenario: Interrupted turn-off
- **WHEN** the phone restarts after step 3 of turning off
- **THEN** Plainphone resumes at step 4, and does not report "Off" until step 6 is confirmed
