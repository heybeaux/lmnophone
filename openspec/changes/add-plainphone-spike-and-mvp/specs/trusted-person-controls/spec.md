## ADDED Requirements

### Requirement: Supervised sessions follow one custody contract
A **supervised session** SHALL be the only way Plainphone relaxes its lockdown. The MVP has two kinds:
- **Play**: add an app or check updates.
- **Settings**: change an Android setting that Plainphone does not provide.

Every session SHALL follow this contract:
- **Starting it.** Starting a session SHALL require a fresh PIN or recovery-code entry.
- **Journal first.** Before relaxing anything, Plainphone SHALL write a journal entry to device-protected storage. The entry names the relaxation and a deadline (Play: 10 minutes; Settings: 5 minutes).
- **Narrow scope.** A session SHALL add only its named packages to the lock-task UI list. It SHALL NOT lift `DISALLOW_DEBUGGING_FEATURES`, `DISALLOW_FACTORY_RESET` or `DISALLOW_SAFE_BOOT`.
- **Ending it.** A session SHALL end on any of these: Done, the screen turning off, the deadline, or the user leaving the session's packages. Ending SHALL remove the relaxation, read the policy back, and only then clear the journal.
- **Revoke on every start.** On every Plainphone start, including `LOCKED_BOOT_COMPLETED` through a Direct-Boot-aware receiver, Plainphone SHALL first revoke any relaxation still in the journal, before doing anything else.
- **Custody.** The phone is in the trusted person's custody for the whole session. Plainphone SHALL say so on screen: "Stay with the phone until you tap Done."
- **Disclosure.** If Plainphone cannot run at all while a session is open, the relaxation stays until a helper fixes the phone. This SHALL be disclosed, and tested in Q12. Plainphone SHALL NOT claim an automatic close that does not depend on Plainphone running.

#### Scenario: Screen turns off mid-session
- **WHEN** the screen turns off during a Play session
- **THEN** the session ends, Play is unreachable, and the policy readback matches the at-rest policy

#### Scenario: Plainphone is killed mid-session
- **WHEN** Plainphone's process is killed during a session and restarts, or the phone reboots
- **THEN** the journaled relaxation is revoked before any other Plainphone work, including before the first unlock where Android allows it

#### Scenario: Plainphone cannot restart
- **WHEN** a test build crashes on every start while a Play session is journaled
- **THEN** the Q12 result records how long the relaxation stayed live, and the disclosure screen describes that case

### Requirement: Trusted-person PIN has an exact lockout schedule
Trusted-person mode SHALL require a PIN of at least 6 digits. The PIN SHALL be stored only as a PBKDF2-HMAC-SHA256 hash with at least 600,000 iterations and a random 16-byte salt.

**Lockout schedule:**
- Failures 1–4 cause no delay.
- Failure 5 starts a 30-second delay.
- Each later failure doubles the delay, up to a cap of 1 hour.
- Entries made during a delay SHALL be rejected without counting and without extending the delay.

**Stored state.** The failure count and the remaining delay SHALL be stored together, in one atomic write to device-protected storage. After a reboot, Plainphone SHALL resume the *remaining* delay, not restart it. Changing the clock SHALL have no effect.

**Other rules:**
- After 10 failures, Plainphone SHALL try to text the trusted person. Whether that text sends SHALL NOT change what is enforced.
- Wrong entries SHALL NOT wipe the device.
- No lockout SHALL gate calls or emergency access.

#### Scenario: Guessing across reboots
- **WHEN** someone enters 6 wrong PINs, then reboots partway through the 60-second delay
- **THEN** after the reboot only the remaining part of that delay applies, and the count continues from 6

#### Scenario: Entry during a delay
- **WHEN** a PIN is submitted during a delay
- **THEN** it is rejected without being checked, and neither the count nor the delay changes

### Requirement: Recovery code has its own counter
Setup SHALL generate a recovery code with at least 128 bits of randomness and show it once. Plainphone SHALL store only its hash. The helper SHALL confirm that the trusted person, and not the helper, has the code.

Recovery-code entry SHALL use its own failure counter, separate from the PIN's, with the same schedule. A PIN lockout SHALL NOT block the recovery code.

Using the code SHALL replace the PIN and the code in one atomic step. Plainphone SHALL explain that the code cannot unlock reset protection after a wipe.

#### Scenario: PIN locked out, code still works
- **WHEN** PIN entry is in a delay and the trusted person enters the correct recovery code
- **THEN** they can set a new PIN right away, and a new code is shown once

#### Scenario: Interrupted recovery
- **WHEN** the phone restarts in the middle of a recovery
- **THEN** either the old PIN and code work, or the new ones do. Never neither.

### Requirement: Privileged actions need fresh authority and no outside input
A fresh PIN or recovery-code entry SHALL be required for each of these actions: approving an app, removing an app, starting any supervised session, installing a Plainphone update, and turning Plainphone off.

Admin activities SHALL NOT be exported. No outside intent, extra or flag SHALL be accepted as proof of authentication. Admin screens SHALL use `FLAG_SECURE`. Backup SHALL be disabled. Secrets SHALL NOT be logged.

#### Scenario: Outside intent claims authority
- **WHEN** another app sends Plainphone an intent whose extras claim to approve an app or turn Plainphone off
- **THEN** nothing changes and no admin screen opens

### Requirement: App requests are untrusted text sent to a fixed recipient
The user SHALL be able to request an app, with a reason of up to 280 characters and at most 5 requests a day. Plainphone SHALL send the request by SMS to the trusted person's number from setup, and list it in trusted-person mode, shown as "From [user]:" in plain text.

Approving a request SHALL start a Play session. The approval SHALL bind to the package that was actually installed and its signing certificate, not to the user's text. If the SMS fails, Plainphone SHALL say so and keep the request listed.

#### Scenario: Misleading request
- **WHEN** a request names one app and a different package is installed during the session
- **THEN** the approval screen shows the installed package's real name and developer, and the trusted person approves that package

### Requirement: Plainphone updates come from the helper's computer over local Wi-Fi
Plainphone SHALL update itself only from an APK the helper serves on the local network. The update SHALL be installed with the Device Owner's silent install, after a fresh PIN entry. Before installing, Plainphone SHALL check two things:
- the APK's signing-certificate digest matches the digest pinned in Plainphone;
- its version code is higher than the installed one.

Release builds SHALL have `testOnly=false` and `debuggable=false`. The signing key SHALL be held by Beaux, or by someone he names, and never be stored on either phone.

If the updated Plainphone fails to start, recovery is the "Plainphone can't run" path.

Debugging SHALL stay off for the whole update.

#### Scenario: Update from the helper's laptop
- **WHEN** the trusted person enters the PIN and the URL the helper's computer shows, and the APK is signed with the pinned key and has a higher version
- **THEN** Plainphone installs it silently, and all policy and data survive

#### Scenario: Wrong signer
- **WHEN** the APK at that URL is signed with a different key
- **THEN** Plainphone refuses it and installs nothing

### Requirement: Recovery when Plainphone cannot run
The setup guide and the disclosure screen SHALL describe the last-resort path, step by step:
1. the helper wipes the phone from recovery mode;
2. reset protection then requires the trusted person's (or the backup person's) Google account;
3. the phone comes back with no Plainphone, so setup has to be done again;
4. anything not backed up is lost.

#### Scenario: Plainphone crash loop
- **WHEN** Plainphone cannot start at all
- **THEN** calls and 9-1-1 still work from the lock screen and the system dialer, and the guide's recovery steps return the phone to a usable state

### Requirement: Plain disclosure of leftover risk
Trusted-person mode SHALL include a "What Plainphone can't stop" screen, written in plain words. It SHALL cover each of these:
- web pages inside approved apps;
- Google account or help screens that testing found reachable;
- that a supervised session gives open access, and so needs custody;
- that a session stays open if Plainphone cannot run;
- that an unapproved app may run in the background, with no time limit, if hiding it fails;
- a hardware-button wipe, and what reset protection does about it.

#### Scenario: Trusted person reads the limits
- **WHEN** the trusted person opens "What Plainphone can't stop"
- **THEN** each risk is described in one or two plain sentences

### Requirement: Turning Plainphone off restores everything it changed
Turning Plainphone off SHALL require a fresh PIN or recovery-code entry and two plain confirmations. Plainphone SHALL journal its progress and resume after a restart.

Plainphone SHALL restore every change it recorded, and read each one back:
- user restrictions
- hidden apps
- lock-task packages and features
- persistent preferred activities
- permitted input methods and accessibility services
- keyguard disabled features
- the browser role
- default SMS and dialer, if they were changed
- the reset-protection policy
- Play app restrictions

Before removing Device Owner, Plainphone SHALL confirm that another home app and the system dialer resolve.

Plainphone SHALL report "Off" only when Device Owner is gone. If Plainphone finds it is no longer Device Owner, it SHALL treat turning off as complete.

If turning off fails, Plainphone SHALL report the failure and SHALL NOT fall back to a wipe on its own.

#### Scenario: Turn off
- **WHEN** the trusted person confirms turning off with fresh authority
- **THEN** the phone works as a normal Android phone, with its home app and dialer working, and photos and messages are kept. Device test A2 already confirmed that releasing the phone does not wipe it.

#### Scenario: Interrupted turn-off
- **WHEN** the phone restarts in the middle of turning off
- **THEN** Plainphone resumes at the next step that has not been read back, and does not report "Off" until Device Owner is gone
