## ADDED Requirements

### Requirement: A phone has up to two trusted people, as equals
In **supervised mode**, a phone SHALL have one or two trusted people. Each trusted person SHALL have their own:
- name and phone number;
- PIN, with its own lockout counter and delay;
- recovery code, with its own counter;
- reset-protection Google `userId`.

Either trusted person SHALL be able to do every trusted-person action alone. Neither SHALL be able to see, change or reset the other's PIN or recovery code. A recovery code SHALL reset only its owner's PIN.

The PIN screen SHALL ask which trusted person is entering it before it takes the PIN. An entry SHALL be checked against that person's PIN only, and SHALL count only against that person's lockout.

Actions the other trusted person can undo afterwards (approving or removing an app, changing an app's permissions, opening a supervised session, installing a Plainphone update) SHALL take effect at once; the last action stands. Actions that can't be undone afterwards SHALL be waiting changes (see "Waiting changes").

Setup SHALL tell both people, in plain words, that they are equals and that either can approve apps or turn Plainphone off.

#### Scenario: Two people approve and remove
- **WHEN** Beaux approves Spotify, and later the trusted person removes it
- **THEN** Spotify is removed, each action is texted to the other person, and both appear in the history list

#### Scenario: One person locked out
- **WHEN** Beaux's PIN is in a lockout delay
- **THEN** the trusted person can still enter her PIN at once, and Beaux's recovery code still works

#### Scenario: An entry is not tried against both PINs
- **WHEN** someone picks "the trusted person" and enters Beaux's correct PIN
- **THEN** the entry fails and counts against the trusted person's lockout only

### Requirement: Every trusted person gets notices, and actions are kept in a history list
Each privileged action SHALL be texted to every trusted person who did not do it. App requests, the monthly check, lockout warnings, "Plainphone needs attention" and system-update notices SHALL go to every trusted person.

Each text SHALL have its own delivery status (waiting, sent, gave up), shown in trusted-person mode. A failed or late text SHALL NOT change what is enforced.

Trusted-person mode SHALL keep a 90-day history list of privileged actions and waiting changes: who, what, when and text status. It SHALL NOT hold app content, URLs or message text. It SHALL be kept in device-protected storage.

#### Scenario: A text fails
- **WHEN** a notice to the trusted person can't be sent because there is no signal
- **THEN** the action still takes effect, and her text shows "waiting" and later "sent" or "gave up"

### Requirement: Waiting changes take 24 hours and can be cancelled
These SHALL be **waiting changes**:
1. turning Plainphone off;
2. adding a trusted person, handing a trusted-person role over, or removing a trusted person;
3. switching a supervised phone to self-managed;
4. in self-managed mode, shortening the elevation wait or raising the daily limit.

**Start.** A waiting change SHALL start only after a fresh PIN entry (a trusted person's in supervised mode, the user's in self-managed mode) and the confirmations that action already needs.

**Record first.** Plainphone SHALL write a wait record, with a checksum, to device-protected storage before it queues any text or shows the change as waiting. The record SHALL hold the kind of change, its pending details, who started it, the boot id and the elapsed time at the start.

**Counting.** The change SHALL go ahead only after Plainphone can prove 24 hours have passed:
- Plainphone saves its elapsed time at least every 5 minutes;
- after a reboot, it counts only time up to the last save before the reboot, plus time since the new boot;
- wall-clock and time-zone changes SHALL have no effect.

Restarts and time with the phone switched off SHALL only make the wait longer. The screens SHALL say that time while the phone is off doesn't count.

**Texts.** At the start, Plainphone SHALL text every trusted person, saying what will happen, when, and how to cancel.

**Cancel.** A pending waiting change SHALL be cancelled by any of these:
- a trusted person's PIN, on the phone;
- anyone holding the phone, without a PIN;
- in self-managed mode, the user;
- a reply of `CANCEL` from a trusted person's number on record, **only if** feasibility question Q15 passes. A reply SHALL only ever cancel. It SHALL NOT start or shorten anything.

A cancel SHALL be texted to every trusted person, with who cancelled.

**Skip.** In supervised mode, a trusted person's recovery code SHALL skip the wait, at the start or during it. This counts as a recovery-code use. In self-managed mode, nothing SHALL skip the wait.

**End.** When the wait ends, the change SHALL go ahead without anyone confirming again, and Plainphone SHALL text every trusted person.

**Damaged record.** If a wait record fails its checksum, Plainphone SHALL treat that change as cancelled, leave the phone as it was, and text every trusted person.

**One at a time.** There SHALL be at most one pending waiting change of each kind. After a cancel, a new start SHALL begin a fresh 24 hours.

During the wait, the lockdown SHALL stay fully in place.

#### Scenario: Reboot during the wait
- **WHEN** a turn-off is started, and the phone is switched off for 8 hours after 10 hours
- **THEN** the turn-off does not happen until at least 24 hours of provable running time have passed, so at least 32 hours after it started

#### Scenario: Clock moved forward
- **WHEN** someone moves the clock forward by a day during a wait
- **THEN** the wait does not end early

#### Scenario: Cancelled by the user
- **WHEN** a child who saw a PIN starts a turn-off, and the user taps the home-screen line and cancels it
- **THEN** nothing changes, and both trusted people get a text saying someone holding the phone cancelled it

#### Scenario: Damaged wait record
- **WHEN** the wait record for a pending trusted-person change fails its checksum after a reboot
- **THEN** the change is cancelled, the current trusted people keep their roles, and both are texted

#### Scenario: Forged cancel text
- **WHEN** Q15 passed and a text reading `CANCEL` arrives that appears to come from a trusted person's number
- **THEN** the pending change is cancelled, nothing else changes, and every trusted person is told it was cancelled by text

### Requirement: Changing who the trusted people are needs a current trusted person
Adding a trusted person, handing a role over and removing a trusted person SHALL each need a current trusted person's PIN, and SHALL be waiting changes. The user alone SHALL NOT be able to change who the trusted people are.

**Incoming person.** Before the wait starts, the incoming person SHALL, in person:
- enter their name and phone number;
- set their own PIN;
- receive their own recovery code, shown once;
- enter their reset-protection `userId` using the guide's People API steps on their own device;
- confirm a test text from Plainphone.

These details SHALL be stored in the wait record and SHALL grant nothing until the wait ends.

**Handing over** removes the outgoing person's PIN, recovery code and `userId` when the change completes. Handing over SHALL need the outgoing person's own PIN or recovery code.

**Removing.** Plainphone SHALL NOT remove the last trusted person. That is a switch to self-managed mode or turning off.

**Completing.** In one journaled step, Plainphone SHALL write the new set of trusted people, then set the new reset-protection list and read it back (device-lockdown), and only then remove the outgoing person's credentials. After an interruption, either the old set or the new set SHALL work, never neither. If the reset-protection readback fails, the change SHALL still complete for PINs and texts, and trusted-person mode SHALL show "Reset protection not updated – call the helper".

#### Scenario: Handover
- **WHEN** Beaux hands his role to his sister with his PIN, she sets up her PIN, code and ID, and 24 hours pass without a cancel
- **THEN** she is a trusted person, Beaux's PIN and code no longer work, and the reset-protection list names her and not him

#### Scenario: Someone who saw a PIN adds themselves
- **WHEN** a child enters the trusted person's PIN and starts adding themselves as a second trusted person
- **THEN** both current trusted people are texted at once, either can cancel during the 24 hours, and the child's PIN grants nothing during the wait

#### Scenario: Restart while completing
- **WHEN** the phone restarts in the middle of completing a handover
- **THEN** either the outgoing person's PIN or the incoming person's PIN works afterwards, never neither

### Requirement: Self-managed mode is friction, not a lock
A phone SHALL be in **supervised mode** (one or two trusted people) or **self-managed mode**. In self-managed mode, the user holds a PIN and a recovery code and is their own trusted person.

The lockdown at rest SHALL be the same in both modes: Device Owner, hidden apps, lock task, restrictions, the emergency floor and web containment. General browsers SHALL stay hidden in both modes.

Setup and "What Plainphone can't stop" SHALL say, in plain words, that in self-managed mode Plainphone adds waits and limits, and can't stop the user, because the user holds the PIN.

**Stricter at once, looser with friction.** In self-managed mode:
- removing an app, lengthening the elevation wait, and lowering the daily limit SHALL take effect at once, with the PIN;
- opening a Play or Settings session, approving an installed hidden app, and installing a Plainphone update SHALL need the PIN, then an elevation;
- shortening the elevation wait and raising the daily limit SHALL be waiting changes;
- turning off SHALL be a waiting change.

**App requests.** There SHALL be no request flow. "Add an app" SHALL start an elevation.

**Monthly check.** The monthly-check line SHALL ask the user. The check SHALL use an elevation.

**Recovery code.** The recovery code SHALL reset the user's PIN only. It SHALL NOT skip an elevation wait, the daily limit or a waiting change.

#### Scenario: Self-managed user removes an app
- **WHEN** the user enters their PIN and removes a game
- **THEN** the game is hidden at once, with no wait

#### Scenario: Recovery code in self-managed mode
- **WHEN** the user enters their recovery code while a turn-off is waiting
- **THEN** they can set a new PIN, and the turn-off still waits the full 24 hours

### Requirement: Elevation in self-managed mode follows the session contract plus friction
An **elevation** SHALL be a supervised session started by the user in self-managed mode. It SHALL follow every step of the session contract, with these differences:

1. **Wait before start.** After the PIN, Plainphone SHALL count down a wait (default 10 minutes). The phone SHALL work normally during it. When the wait ends, the user SHALL tap Start within 5 minutes, or the elevation lapses without using a turn. The user SHALL be able to cancel at any time.
2. **Daily limit.** At most a set number of elevations (default 2) SHALL start in any 24 hours. A turn SHALL be counted, durably, when the user taps Start and before anything is relaxed. Starts SHALL be serialized so a double tap can't get two. A counted turn SHALL NOT be cleared by a crash, a reboot or a failed write. Ageing SHALL use the same counting rule as waiting changes. A damaged count SHALL be treated as no turns left, starting a fresh 24 hours from when it was found.
3. **Deadline.** The session table's deadlines SHALL apply (Play 10 minutes, Settings 5 minutes), with no extending.
4. **Never permanent.** No setting SHALL remove the wait or the limit, or lengthen a deadline. The wait SHALL be settable from 10 minutes to 24 hours, and the limit from 0 to 3 a day.
5. **Custody wording.** The session screen SHALL say when Plainphone will close it, instead of the custody wording.

Changes made during an elevation SHALL persist, as they would for a trusted person.

The custody limit SHALL apply: if Plainphone can't run during an elevation, nothing revokes it automatically. Self-managed setup SHALL disclose this.

#### Scenario: Elevation to add an app
- **WHEN** the user enters their PIN to add an app, waits 10 minutes, and taps Start
- **THEN** a turn is counted, a Play session opens for at most 10 minutes, and the approved app stays approved after it closes

#### Scenario: Daily limit reached
- **WHEN** the user has started 2 elevations in the last 24 hours and tries a third
- **THEN** Plainphone says when the next one will be available, and nothing opens

#### Scenario: Reboot to reset the count
- **WHEN** the user reboots the phone right after using their second elevation
- **THEN** both turns are still counted after the reboot

#### Scenario: Killed during the wait before start
- **WHEN** Plainphone is killed during the 10-minute countdown
- **THEN** no turn is counted, nothing is relaxed, and the user starts again

### Requirement: Switching between modes
- **Supervised to self-managed** SHALL be a waiting change, started with a trusted person's PIN. The user SHALL set their PIN, receive their recovery code and enter their own `userId` before the wait starts; these grant nothing until it ends. When it ends, Plainphone SHALL remove the trusted people's credentials and replace the reset-protection list, as for a handover.
- **Self-managed to supervised** SHALL take effect at once, with the user's PIN, after the new trusted person has set up in person as for adding a trusted person. The user's PIN and recovery code SHALL then be removed.
- A user on a supervised phone has no PIN and SHALL NOT be able to start either switch.

#### Scenario: User on a supervised phone wants self-managed
- **WHEN** the user of a supervised phone looks for a way to switch to self-managed
- **THEN** there is none without a trusted person's PIN

#### Scenario: Switch to supervised
- **WHEN** a self-managed user adds the trusted person as trusted person with their PIN, and the trusted person sets up her PIN, code, ID and test text
- **THEN** the phone is supervised at once, and the user's old PIN no longer works

## MODIFIED Requirements

### Requirement: Supervised sessions follow one custody contract
A **supervised session** SHALL be the only way Plainphone relaxes its lockdown. The MVP has two kinds.

| Kind | Purpose | Packages added to lock task | Deadline |
|---|---|---|---|
| Play | Add an app, or the monthly update check | `com.android.vending` only | 10 minutes |
| Settings | Change an Android setting Plainphone does not provide | the build's Settings package and any same-UID siblings recorded in the build inventory | 5 minutes |

A session SHALL be started by a trusted person in supervised mode, or by the user as an elevation in self-managed mode. A session SHALL NOT relax anything other than what this table names. Every session SHALL follow this contract:

1. **Start.** A fresh PIN or recovery-code entry is required. In self-managed mode, the elevation wait and daily limit also apply.
2. **Journal before anything relaxes.** Plainphone writes a journal entry to device-protected storage, with a checksum, naming the relaxation and the deadline.
3. **Unhide safely.** If starting the session unhides any package, Plainphone SHALL, straight after the unhide, flip one of its own components with `PackageManager.SYNCHRONOUS` (PLA-22 mitigation B), and read the hidden state back, before the session screen appears. A session that would unhide a package SHALL NOT start while the battery is below 5% and not charging.
4. **End triggers.** The session ends on Done, screen off, an incoming call, the deadline, or the user leaving the session's packages.
5. **Closing.** On ending, Plainphone:
   - removes the relaxation;
   - runs the **protected-state check** (below). Calls, Emergency and alerts are never gated on it;
   - restores or rejects any change that breaks a mandatory protection, while keeping ordinary setting changes such as ringtone, display or Wi-Fi;
   - reads the policy back;
   - only then clears the journal.
6. **Revoke on every start.** A Direct-Boot-aware receiver handles `LOCKED_BOOT_COMPLETED`, and every Plainphone start first revokes any journaled relaxation. This uses only device-protected data, with no credential-encrypted database or key.
7. **Damaged journal.** If the journal entry is missing its checksum or is invalid, Plainphone treats every session relaxation as active and revokes them all.
8. **Custody.** In supervised mode, the phone is in the trusted person's custody for the whole session. The screen says: "Stay with the phone until you tap Done." In self-managed mode, the screen says when Plainphone will close the session.
9. **Handback.** In supervised mode, the phone SHALL NOT be handed back while a readback fails.

**Protected-state check.** It covers exactly the invariants in this table and nothing else. Any setting not in the table, such as ringtone, font size, Wi-Fi networks, the chosen system keyboard or its language, is the user's choice and SHALL be kept.

| Invariant | Expected | Readback | Repair | If repair fails |
|---|---|---|---|---|
| User restrictions | the policy set | `getUserRestrictions` | re-add | non-handback state |
| Lock-task packages and features | policy list + emergency UI subset (not the whole floor); features incl. BLOCK | `getLockTaskPackages`/`Features` | re-set | non-handback state |
| Floor and never-hide packages | unhidden and enabled | `isApplicationHidden`, enabled state | unhide | non-handback state |
| Browser role | Plainphone | `RoleManager` | re-request role | non-handback state |
| Default SMS | Google Messages | `ROLE_SMS` holder | `setDefaultSmsApplication` | non-handback state |
| Plainphone `SEND_SMS` | granted and allowlisted | permission state | `setPermissionGrantState` | trusted people told; requests still listed |
| Permitted input methods | system-only, plus listed | `getPermittedInputMethods` | re-set (system IMEs stay available by Android's rule) | non-handback state |
| Permitted accessibility services | system-only | `getPermittedAccessibilityServices` | re-set | non-handback state |
| Approved apps' pre-granted permissions | as approved | `getPermissionGrantState` | re-set | trusted people told |
| Emergency alert configuration | `DISALLOW_CONFIG_CELL_BROADCASTS` set; cell broadcast floor packages unhidden and enabled | restriction and package state | re-add or unhide | non-handback state. If the build has no readback, the phone is held by the helper. It is never reported as passing. |

A **non-handback state** means: the trusted person (or, in self-managed mode, the user) sees "Not ready – call the helper". Calls, Emergency and alerts stay available throughout. No check SHALL gate them.

**Accepted limit.** If Plainphone cannot run while a relaxation is in place, nothing revokes it automatically. That applies whatever the cause: a bad update, a storage or startup fault, or a crash loop. The relaxation stays until a helper fixes the phone. This SHALL be disclosed, and Beaux SHALL explicitly accept it before the MVP is approved. In self-managed mode, nobody is watching when this happens; this SHALL be disclosed too. Q12 measures this behaviour; it does not prove that the failure cannot happen. The disclosure SHALL also say that during a session, Settings or Play pages may show web content, such as help or account pages.

#### Scenario: Screen turns off mid-session
- **WHEN** the screen turns off during a Play or Settings session
- **THEN** the session ends, the session packages cannot be reached, and the protected-state check passes before the phone is handed back. Calls and Emergency stay available throughout.

#### Scenario: Settings session changes a protection
- **WHEN** someone uses a Settings session to change the default SMS app or the permitted keyboards
- **THEN** the protected-state check restores the protected value, tells the trusted people, and keeps ordinary changes such as the ringtone

#### Scenario: Plainphone is killed mid-session
- **WHEN** Plainphone's process is killed during a session and restarts, or the phone reboots
- **THEN** the journaled relaxation is revoked before any other Plainphone work, including before the first unlock

#### Scenario: Plainphone cannot run
- **WHEN** a build that crashes on every start is installed while a session is journaled
- **THEN** Q12 records how long the relaxation stays reachable, and the result is presented against the board-accepted limit, not as a pass or fail of "cannot happen"

#### Scenario: Power lost right after a session unhides a package
- **WHEN** a session start unhides a package and the phone loses power within 10 seconds
- **THEN** the phone boots normally, because mitigation B saved the change at once, and the journaled relaxation is revoked at boot

#### Scenario: Nearly flat battery
- **WHEN** someone starts a session that would unhide a package at 3% battery, not charging
- **THEN** the session does not start, and Plainphone says to charge the phone first

### Requirement: Trusted-person PIN has an exact lockout schedule
Each PIN (each trusted person's, or the user's in self-managed mode) SHALL be at least 6 digits. Each SHALL be stored only as a PBKDF2-HMAC-SHA256 hash with at least 600,000 iterations and a random 16-byte salt.

**Lockout schedule, per PIN:**
- Failures 1–4 cause no delay.
- Failure 5 starts a 30-second delay.
- Each later failure doubles the delay, up to a cap of 1 hour.
- Entries made during a delay SHALL be rejected without counting and without extending the delay.

**Stored state.** For each PIN, the failure count and the remaining delay SHALL be stored together, in one atomic write to device-protected storage. After a reboot, Plainphone SHALL resume the *remaining* delay, not restart it. Changing the clock SHALL have no effect. One PIN's lockout SHALL NOT affect another PIN or any recovery code.

**Other rules:**
- After 10 failures on a PIN, Plainphone SHALL try to text every trusted person. Whether that text sends SHALL NOT change what is enforced.
- Wrong entries SHALL NOT wipe the device.
- No lockout SHALL gate calls or emergency access.

#### Scenario: Guessing across reboots
- **WHEN** someone enters 6 wrong PINs for one trusted person, then reboots partway through the 60-second delay
- **THEN** after the reboot only the remaining part of that delay applies, and that person's count continues from 6

#### Scenario: Entry during a delay
- **WHEN** a PIN is submitted during that PIN's delay
- **THEN** it is rejected without being checked, and neither the count nor the delay changes

### Requirement: Recovery code has its own counter
Setup SHALL generate a recovery code for each trusted person (or for the user in self-managed mode), with at least 128 bits of randomness, and show it once. Plainphone SHALL store only its hash. In supervised mode, the helper SHALL confirm that the trusted person, and not the helper, has the code.

Each recovery code SHALL use its own failure counter, separate from every PIN's, with the same schedule. A PIN lockout SHALL NOT block any recovery code.

Using a code SHALL replace its owner's PIN and code in one atomic step. It SHALL NOT change the other trusted person's PIN or code. Plainphone SHALL explain that the code cannot unlock reset protection after a wipe.

In supervised mode, a trusted person's recovery code SHALL also skip the wait of a waiting change. In self-managed mode, it SHALL NOT.

#### Scenario: PIN locked out, code still works
- **WHEN** a trusted person's PIN entry is in a delay and they enter their correct recovery code
- **THEN** they can set a new PIN right away, and a new code is shown once

#### Scenario: Interrupted recovery
- **WHEN** the phone restarts in the middle of a recovery
- **THEN** either the old PIN and code work, or the new ones do. Never neither.

#### Scenario: Recovery code skips a turn-off wait
- **WHEN** in supervised mode, a trusted person starts turning off with their recovery code
- **THEN** turning off starts at once, every trusted person is texted, and that person's PIN and code are replaced

### Requirement: Privileged actions need fresh authority and no outside input
A fresh PIN or recovery-code entry SHALL be required for each of these actions: approving an app, removing an app, starting any supervised session, installing a Plainphone update, turning Plainphone off, starting or skipping any waiting change, and changing self-managed friction settings.

Admin activities SHALL NOT be exported. No outside intent, extra or flag SHALL be accepted as proof of authentication. A received text SHALL never be accepted as authority, except as a cancel of a pending waiting change. Admin screens SHALL use `FLAG_SECURE`. Backup SHALL be disabled. Secrets SHALL NOT be logged.

#### Scenario: Outside intent claims authority
- **WHEN** another app sends Plainphone an intent whose extras claim to approve an app or turn Plainphone off
- **THEN** nothing changes and no admin screen opens

#### Scenario: A text claims authority
- **WHEN** a text from a trusted person's number says "APPROVE" or "TURN OFF"
- **THEN** nothing changes

### Requirement: App requests are untrusted text sent to a fixed recipient
In supervised mode, the user SHALL be able to request an app, with a reason of up to 280 characters and at most 5 requests a day. Plainphone SHALL send the request by SMS to every trusted person's number from setup. It SHALL list the request in trusted-person mode, shown as "From [user]:" in plain text. Either trusted person can act on it.

In self-managed mode there SHALL be no request flow; "Add an app" starts an elevation.

**SMS setup.**
- Plainphone holds `SEND_SMS` as an allowlisted restricted permission, granted by the Device Owner, and sends with the SMS subscription chosen at setup.
- Setup proves this works end to end: a test text that each trusted person confirms they received.

**Approval.** Approving a request SHALL start a Play session. The approval SHALL be bound to the package actually installed and its signing certificate, not to the user's text.

**Failed sends.** If an SMS fails, Plainphone SHALL say so and keep the request listed.

#### Scenario: Misleading request
- **WHEN** a request names one app and a different package is installed in the session
- **THEN** the approval screen shows the installed package's real name and developer, and the trusted person approves that package

#### Scenario: Request with two trusted people
- **WHEN** the user requests an app on a phone with two trusted people
- **THEN** both are texted, and the request is listed until one of them acts on it

### Requirement: Plain disclosure of leftover risk
Trusted-person mode SHALL include a "What Plainphone can't stop" screen, written in plain words. It SHALL cover each of these:
- web pages inside approved apps;
- Google account or help screens that testing found reachable;
- that a supervised session gives open access, and so needs custody;
- that a session stays open if Plainphone cannot run;
- that an unapproved app may run in the background, with no time limit, if hiding it fails;
- a hardware-button wipe, and what reset protection does about it;
- that Plainphone sends nothing anywhere, but the phone runs Google's Android with a Google account, and Google collects some data that Plainphone can't stop; the setup privacy steps reduce it;
- with two trusted people: that either can approve apps, open sessions, or turn Plainphone off, and that the 24-hour wait protects against someone who saw a PIN, not against the other trusted person;
- in self-managed mode: that Plainphone adds waits and limits but can't stop the user, who holds the PIN, and that a recovery-mode wipe gives the user a plain phone.

#### Scenario: Trusted person reads the limits
- **WHEN** the trusted person opens "What Plainphone can't stop"
- **THEN** each risk is described in one or two plain sentences

#### Scenario: Self-managed user reads the limits
- **WHEN** a self-managed user opens "What Plainphone can't stop"
- **THEN** it says plainly that Plainphone is friction, not a lock, in this mode

### Requirement: Turning Plainphone off restores everything it changed
Turning Plainphone off SHALL be a waiting change. Starting it SHALL require a fresh PIN or recovery-code entry and two plain confirmations. When the 24-hour wait ends (or a trusted person's recovery code skips it, in supervised mode), Plainphone SHALL start turning off, journal its progress and resume after a restart.

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
- **WHEN** a trusted person confirms turning off with fresh authority, and 24 hours pass without a cancel
- **THEN** the phone works as a normal Android phone, with its home app and dialer working, and photos and messages are kept. Device test A2 already confirmed that releasing the phone does not wipe it.

#### Scenario: Turn off cancelled
- **WHEN** Beaux starts turning off, and the trusted person cancels with her PIN 3 hours later
- **THEN** nothing is restored, the lockdown stays, and both are texted

#### Scenario: Interrupted turn-off
- **WHEN** the phone restarts in the middle of turning off
- **THEN** Plainphone resumes at the next step that has not been read back, and does not report "Off" until Device Owner is gone
