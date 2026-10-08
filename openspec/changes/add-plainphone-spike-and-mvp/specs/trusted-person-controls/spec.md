## ADDED Requirements

### Requirement: Supervised sessions follow one custody contract
A **supervised session** SHALL be the only way Plainphone relaxes its lockdown. The MVP has two kinds.

| Kind | Purpose | Packages added to lock task | Deadline |
|---|---|---|---|
| Play | Add an app, or the monthly update check | `com.android.vending` only | 10 minutes |
| Settings | Change an Android setting Plainphone does not provide | the build's Settings package and any same-UID siblings recorded in the build inventory | 5 minutes |

A session SHALL NOT relax anything other than what this table names. Every session SHALL follow this contract:

1. **Start.** A fresh PIN or recovery-code entry is required.
2. **Journal before anything relaxes.** Plainphone writes a journal entry to device-protected storage, with a checksum, naming the relaxation and the deadline.
3. **End triggers.** The session ends on Done, screen off, an incoming call, the deadline, or the user leaving the session's packages.
4. **Closing.** On ending, Plainphone:
   - removes the relaxation;
   - runs the **protected-state check** (below);
   - restores or rejects any change that breaks a mandatory protection, while keeping ordinary setting changes such as ringtone, display or Wi-Fi;
   - reads the policy back;
   - only then clears the journal.
5. **Revoke on every start.** A Direct-Boot-aware receiver handles `LOCKED_BOOT_COMPLETED`, and every Plainphone start first revokes any journaled relaxation. This uses only device-protected data, with no credential-encrypted database or key.
6. **Damaged journal.** If the journal entry is missing its checksum or is invalid, Plainphone treats every session relaxation as active and revokes them all.
7. **Custody.** The phone is in the trusted person's custody for the whole session. The screen says: "Stay with the phone until you tap Done."
8. **Handback.** The phone SHALL NOT be handed back while a readback fails.

**Protected-state check.** It confirms all of the following, and reports or restores anything else it finds broken:
- every restriction applied;
- lock-task packages and features;
- the never-hide inventory is unhidden and enabled;
- Plainphone holds the browser role;
- Google Messages is the default SMS app;
- Plainphone's SMS permission is still granted;
- permitted input methods and accessibility services;
- emergency alert settings are present.

**Accepted limit.** If Plainphone cannot run while a relaxation is in place, nothing revokes it automatically. That applies whatever the cause: a bad update, a storage or startup fault, or a crash loop. The relaxation stays until a helper fixes the phone. This SHALL be disclosed, and Beaux SHALL explicitly accept it before the MVP is approved. Q12 measures this behaviour; it does not prove that the failure cannot happen. The disclosure SHALL also say that during a session, Settings or Play pages may show web content, such as help or account pages.

#### Scenario: Screen turns off mid-session
- **WHEN** the screen turns off during a Play or Settings session
- **THEN** the session ends, the session packages cannot be reached, and the protected-state check passes before the phone is usable

#### Scenario: Settings session changes a protection
- **WHEN** someone uses a Settings session to change the default SMS app or the permitted keyboards
- **THEN** the protected-state check restores the protected value, tells the trusted person, and keeps ordinary changes such as the ringtone

#### Scenario: Plainphone is killed mid-session
- **WHEN** Plainphone's process is killed during a session and restarts, or the phone reboots
- **THEN** the journaled relaxation is revoked before any other Plainphone work, including before the first unlock

#### Scenario: Plainphone cannot run
- **WHEN** a build that crashes on every start is installed while a session is journaled
- **THEN** Q12 records how long the relaxation stays reachable, and the result is presented against the board-accepted limit, not as a pass or fail of "cannot happen"

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
The user SHALL be able to request an app, with a reason of up to 280 characters and at most 5 requests a day. Plainphone SHALL send the request by SMS to the trusted person's number from setup. It SHALL list the request in trusted-person mode, shown as "From [user]:" in plain text.

**SMS setup.**
- Plainphone holds `SEND_SMS` as an allowlisted restricted permission, granted by the Device Owner, and sends with the SMS subscription chosen at setup.
- Setup proves this works end to end: a test text that the trusted person confirms they received.

**Approval.** Approving a request SHALL start a Play session. The approval SHALL be bound to the package actually installed and its signing certificate, not to the user's text.

**Failed sends.** If an SMS fails, Plainphone SHALL say so and keep the request listed.

#### Scenario: Misleading request
- **WHEN** a request names one app and a different package is installed in the session
- **THEN** the approval screen shows the installed package's real name and developer, and the trusted person approves that package

### Requirement: Plainphone updates come from the helper's computer over local Wi-Fi, bound to exact bytes and package
Plainphone SHALL update itself only from an APK served by the helper's computer on the local network, after a fresh PIN entry.

Before installing, Plainphone SHALL:
1. **Close open sessions.** Close any open supervised session and read the policy back.
2. **Download within limits.** Download to private storage only:
   - at most 100 MB;
   - a 2-minute timeout;
   - no redirects;
   - only from the host and port the trusted person entered.
3. **Verify the staged file.** Check all four of these on the file in private storage:
   - the package name is exactly Plainphone's;
   - the APK signature is valid;
   - the signer matches the certificate pinned in Plainphone;
   - its `longVersionCode` is strictly greater than the installed one.
4. **Install exactly those bytes.** Install the verified file through a PackageInstaller session bound with `setAppPackageName` to Plainphone's package.

**Outcomes:**
- **Failure before commit.** Any failure before the install commits leaves the installed build and policy unchanged.
- **Post-update check.** Install success and start-up success are separate. After an update, Plainphone SHALL run the handover self-check. If it fails, Plainphone SHALL show "Plainphone needs attention".
- **No rollback.** Plainphone SHALL NOT promise to roll back a signed update that is broken. Recovery is the "Plainphone can't run" path, explained to the trusted person before any update.

**Builds and keys:**
- Release builds SHALL have `testOnly=false` and `debuggable=false`.
- Releases SHALL use a dedicated signing key that is never stored on a phone. An offline backup SHALL be held by a custodian whom Beaux names when he approves the MVP.
- **Lost key:** no further updates are possible; moving to a new key means a full re-setup.
- **Compromised key:** turn Plainphone off on every phone, then re-set up with a new key.

Debugging SHALL stay off throughout.

#### Scenario: Update from the helper's laptop
- **WHEN** the downloaded APK is Plainphone's package, correctly signed by the pinned key, with a higher `longVersionCode`
- **THEN** Plainphone installs exactly the verified bytes, policy and data survive, and the post-update self-check passes

#### Scenario: Wrong signer, wrong package, or lower version
- **WHEN** the APK has a different signer, a different package name signed by the same key, or the same or a lower version
- **THEN** Plainphone refuses it and installs nothing

#### Scenario: Substituted or tampered input
- **WHEN** the server changes its response after validation, redirects elsewhere, or serves a truncated file
- **THEN** only the verified staged bytes can be installed, or nothing is installed, and the current build keeps running

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
