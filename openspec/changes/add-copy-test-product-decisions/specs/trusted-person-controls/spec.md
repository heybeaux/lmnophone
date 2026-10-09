## ADDED Requirements

### Requirement: A phone has up to two trusted people, as equals
In **supervised mode**, a phone SHALL have one or two trusted people. Each trusted person SHALL have their own:
- name and phone number;
- PIN, with its own lockout counter and delay;
- recovery code, with its own counter;
- reset-protection Google `userId`.

Either trusted person SHALL be able to do every trusted-person action alone. Neither SHALL be able to see, change or reset the other's PIN or recovery code. A recovery code SHALL reset only its owner's PIN.

The PIN screen SHALL ask which trusted person is entering it before it takes the PIN. An entry SHALL be checked against that person's PIN only, and SHALL count only against that person's lockout. Two people MAY choose the same PIN value; Plainphone SHALL NOT compare the two PINs or reveal that they match.

Actions that the other trusted person can put back afterwards (approving or removing an app, changing an app's permissions, opening a supervised session) SHALL take effect at once; the last action stands. Installing a Plainphone update SHALL also take effect at once, and Plainphone SHALL NOT describe it as reversible. Actions listed as waiting changes SHALL follow "Waiting changes".

Setup SHALL tell both people, in plain words, that they are equals and that either can approve apps or turn Plainphone off.

#### Scenario: Two people approve and remove
- **WHEN** Beaux approves Spotify, and later the trusted person removes it
- **THEN** Spotify is hidden again, both actions are texted to both people, and both appear in the history list

#### Scenario: One person locked out
- **WHEN** Beaux's PIN is in a lockout delay
- **THEN** the trusted person can still enter her PIN at once, and Beaux's recovery code still works

#### Scenario: An entry is checked against the chosen person only
- **WHEN** Beaux and the trusted person have different PINs, and someone picks "the trusted person" and enters Beaux's PIN
- **THEN** the entry fails, only the trusted person's verifier and counter are used, and Beaux's counter does not change

### Requirement: Texts go to every trusted person, and actions are kept in a history list
Each privileged action, each waiting-change start, cancel, skip and completion, and each notice (app requests, the monthly check, lockout warnings, "Plainphone needs attention", system-update notices) SHALL be texted to **every** trusted person, including the one who did it. Routine receipts MAY be combined into one daily text; waiting-change texts SHALL be sent at once.

Each text SHALL have its own delivery status (waiting, sent, gave up), shown in trusted-person mode. A failed or late text SHALL NOT change what is enforced.

Plainphone SHALL have no server, analytics or account. The only data it sends SHALL be these texts, through the carrier.

Trusted-person mode SHALL keep a 90-day history list of privileged actions and waiting changes: which credential, what, when, and text status. It SHALL NOT hold app content, URLs or message text. It SHALL be kept in device-protected storage.

#### Scenario: Someone else used my PIN
- **WHEN** a child enters Beaux's PIN and approves a game
- **THEN** both Beaux and the trusted person get a text saying Beaux's PIN approved it

#### Scenario: A text fails
- **WHEN** a notice to the trusted person can't be sent because there is no signal
- **THEN** the action still takes effect, and her text shows "waiting" and later "sent" or "gave up"

### Requirement: Waits, countdowns and daily counts use one proven running-time clock
Plainphone SHALL keep a **proven running time** counter, P, in device-protected storage, as one atomic record holding: the total proven so far, the boot id, and the `SystemClock.elapsedRealtime()` reading at the last checkpoint.

- **Same boot:** a checkpoint SHALL add the time since the last checkpoint and store the new reading, in one atomic write. No interval SHALL be counted twice.
- **New boot:** time between the last checkpoint and the reboot SHALL NOT be counted. The first checkpoint after boot SHALL add the time since this boot started and store the new boot id.
- Checkpoints SHALL happen at least every 5 minutes, and at every waiting-change start, cancel and commit, and every elevation debit.
- Waiting changes and daily-limit debits SHALL record P when they start. Their age SHALL be P now minus that reading.
- Countdowns, Start windows and deadlines SHALL use `elapsedRealtime()` within one boot. A reboot SHALL end them.
- Wall-clock and time-zone changes SHALL have no effect on any of these.

**Damaged P.** Plainphone SHALL write a fresh P starting at zero and re-stamp every pending waiting change and every debit at zero. Nothing SHALL mature early and no debit SHALL be refunded. If that write fails, no waiting change SHALL mature and no elevation SHALL start until it succeeds, and "Plainphone needs attention" SHALL show.

The screens SHALL say that time while the phone is off doesn't count.

#### Scenario: Repeated process restarts
- **WHEN** Plainphone's process is killed and restarted ten times within one boot
- **THEN** P has grown by the real elapsed time of that boot, not more

#### Scenario: Reboot during a wait
- **WHEN** a turn-off is started, and after 10 hours the phone is switched off for 8 hours
- **THEN** the turn-off does not happen until at least 24 hours of proven running time have passed, so at least 32 hours after it started

#### Scenario: Clock moved forward
- **WHEN** someone moves the clock or time zone forward by a day during a wait
- **THEN** the wait does not end early

#### Scenario: Damaged counter
- **WHEN** P fails its checksum while a turn-off is pending and two debits are counted
- **THEN** the turn-off restarts its 24 hours, both debits count as just used, and nothing matures or is refunded

### Requirement: Waiting changes take 24 hours, one at a time, and are re-checked before they happen
These SHALL be **waiting changes**:
1. turning Plainphone off;
2. adding a trusted person, handing a trusted-person role over, or removing a trusted person;
3. switching mode, in either direction;
4. in self-managed mode, shortening the elevation countdown or raising the daily limit.

**One at a time.** At most one waiting change SHALL be pending, of any kind. Trying to start another SHALL show the pending one, with Cancel.

**Generations.** Plainphone SHALL keep a credential generation for each PIN holder, raised whenever that person's PIN or recovery code is replaced, and a structure generation, raised whenever the set of trusted people or the mode changes.

**Start.** A waiting change SHALL start only after a fresh PIN entry (a trusted person's in supervised mode, the user's in self-managed mode) and the confirmations that action already needs. Plainphone SHALL then write, in **one** atomic write to device-protected storage:
- the wait record: request id, kind, pending details, which credential started it, the starter's credential generation, the structure generation, and P;
- the text queue entries for the start notice, with the recipient list fixed at that moment.

Only after that write succeeds SHALL Plainphone show the change as waiting or send any text. If the write fails, the change SHALL NOT start. On every start, Plainphone SHALL send any queued text not yet marked sent.

**Texts.** The start text SHALL say what will happen, that it happens after 24 hours of the phone being on, and how to cancel. It SHALL mention replying only if Q15 passed.

**Cancel.** The pending change SHALL be cancelled by any of these:
- a trusted person's PIN, on the phone;
- anyone holding the phone, without a PIN;
- in self-managed mode, the user;
- a reply of `CANCEL` from a trusted person's number on record, **only if** feasibility question Q15 passes. It SHALL cancel the one pending change. A reply when nothing is pending, or after the change committed, SHALL do nothing and SHALL be logged. A reply SHALL NOT start, skip or shorten anything.

Plainphone SHALL write a **cancelled** state on the request durably before it shows "Cancelled". Every scheduled callback SHALL check the request's id and state before acting. If the cancel write fails, Plainphone SHALL NOT show "Cancelled", SHALL retry, and SHALL NOT commit the change while the cancel is unresolved. The cancel SHALL be texted to every trusted person, with who cancelled.

**Where Cancel is reachable.** A pending change and its PIN-free Cancel SHALL be shown on the home-screen notice line when it is the line shown, on the "Plainphone needs attention" screen, and on Plainphone's own settings screen.

**Skip.** In supervised mode, a trusted person's recovery code SHALL skip the wait, only through a screen that names the exact pending change and asks the person to confirm skipping it. Entering a recovery code to reset a forgotten PIN SHALL NOT skip anything. In self-managed mode, nothing SHALL skip the wait.

**Re-check.** When the wait matures (or is skipped), Plainphone SHALL check that the starter's credential generation (or, for a skip, the generation it was confirmed under) and the structure generation are unchanged, and that the starter's credential still exists. If any check fails, the change SHALL be cancelled and texted to every trusted person.

**Commit.** If the re-check passes, Plainphone SHALL write the request's state as **committing** in one atomic write, and start that change's **operation journal**. From then on, Cancel SHALL NOT be offered, and screens SHALL say "Already in progress". The operation journal SHALL resume after any restart, reading each step back, until **completed**; then Plainphone SHALL text every trusted person. The operation journal SHALL be kept in two copies. If both are damaged, Plainphone SHALL keep whichever credential set and policy are valid on disk, show "Plainphone needs attention", text every trusted person, and refuse new waiting changes until a helper has looked at it.

**Missing or damaged wait record.** No record means nothing is pending. A damaged record while pending SHALL cancel that change, leave the phone as it was, and text everyone on the last known list. A damaged record while committing SHALL resume from the operation journal.

During the wait, the lockdown SHALL stay fully in place.

#### Scenario: Crash before the start text is queued
- **WHEN** Plainphone is killed right after a turn-off is confirmed
- **THEN** either nothing was written and no change is pending, or the wait record and its queued texts were both written and the texts are sent after restart

#### Scenario: Owner replaces a seen PIN
- **WHEN** a child starts a handover with the trusted person's PIN, and the trusted person then resets her PIN with her recovery code
- **THEN** the PIN reset does not skip the handover, and when the wait matures the re-check finds the trusted person's credential generation changed and cancels it

#### Scenario: Turn-off started, then mode changed
- **WHEN** a turn-off is pending and someone tries to start a mode switch
- **THEN** the switch can't start until the turn-off is cancelled

#### Scenario: Cancelled by the user
- **WHEN** a child who saw a PIN starts a turn-off, and the user taps Cancel on the home screen
- **THEN** nothing changes, and both trusted people get a text saying someone holding the phone cancelled it

#### Scenario: Stale timer after cancel
- **WHEN** a turn-off is cancelled and a timer scheduled before the cancel fires later
- **THEN** the timer finds the request cancelled and does nothing

#### Scenario: Cancel hidden behind "needs attention"
- **WHEN** a turn-off is pending and "Plainphone needs attention" is the line on the home screen
- **THEN** the pending turn-off and its Cancel are on the needs-attention screen and in Plainphone's settings

#### Scenario: Damaged wait record
- **WHEN** the wait record for a pending trusted-person change fails its checksum after a reboot
- **THEN** the change is cancelled, the current trusted people keep their roles, and both are texted

#### Scenario: Late cancel text
- **WHEN** Q15 passed, and a `CANCEL` reply arrives after a turn-off has committed
- **THEN** turning off continues, and the reply is logged

### Requirement: Changing who the trusted people are needs a current trusted person
Adding a trusted person, handing a role over and removing a trusted person SHALL each need a current trusted person's PIN, and SHALL be waiting changes. The user alone SHALL NOT be able to change who the trusted people are.

**Incoming person.** Before the wait starts, the incoming person SHALL, in person:
- enter their name and phone number;
- set their own PIN;
- receive their own recovery code, shown once;
- enter their reset-protection `userId` using the guide's People API steps on their own device;
- confirm a test text from Plainphone.

A current trusted person or the helper SHALL record that the incoming person's account can be signed in to from another device, that its second factor is not only on a Plainphone phone, and that it is at least 72 hours old. These details SHALL be stored in the wait record and SHALL grant nothing until the change completes.

**Handing over** SHALL need the outgoing person's own PIN or recovery code.

**Removing.** Plainphone SHALL NOT remove the last trusted person. That is a switch to self-managed mode or turning off.

**Completing.** The operation journal SHALL run, each step read back:
1. set the reset-protection list to the new set of `userId` values and send the broadcast (device-lockdown);
2. only when step 1 reads back correctly, replace the credential set in one atomic write and raise the structure generation;
3. clear the outgoing person's data, mark the change completed, and text every trusted person.

If step 1 does not read back correctly, the change SHALL stay committing, the old trusted people SHALL stay in charge with their old credentials, Plainphone SHALL retry step 1 on every policy pass, and trusted-person mode SHALL show "Reset protection not updated – call the helper". Plainphone SHALL NOT report the change as done. A trusted person MAY cancel a stuck change with their PIN; Plainphone SHALL then restore the old reset-protection list, read it back, and only then mark it cancelled.

After an interruption, either the old set or the new set SHALL work, never neither.

#### Scenario: Handover
- **WHEN** Beaux hands his role to his sister with his PIN, she sets up her PIN, code and ID, and 24 hours pass without a cancel
- **THEN** the reset-protection list names her and not him, then she becomes a trusted person, and Beaux's PIN and code no longer work

#### Scenario: Someone who saw a PIN adds themselves
- **WHEN** a child enters the trusted person's PIN and starts adding themselves as a second trusted person
- **THEN** both current trusted people are texted at once, anyone can cancel during the wait, and the child's PIN grants nothing during it

#### Scenario: Reset protection can't be updated
- **WHEN** a handover matures and the new reset-protection list does not read back
- **THEN** Beaux stays a trusted person with his old PIN, his sister's PIN grants nothing, the change is not reported as done, and the helper is asked to look

#### Scenario: Restart while completing
- **WHEN** the phone restarts in the middle of completing a handover
- **THEN** either the outgoing person's PIN or the incoming person's PIN works afterwards, never neither

### Requirement: Self-managed mode is friction, not a lock
A phone SHALL be in **supervised mode** (one or two trusted people) or **self-managed mode**. In self-managed mode, the user holds a PIN and a recovery code and is their own trusted person.

The lockdown at rest SHALL be the same in both modes: Device Owner, hidden apps, lock task, restrictions, the emergency floor and web containment. General browsers SHALL stay hidden in both modes.

Setup and "What Plainphone can't stop" SHALL say, in plain words, that in self-managed mode Plainphone adds waits and limits, that it can't stop the user because the user holds the PIN, and that the user can always remove it by waiting 24 hours or by wiping the phone.

**Stricter at once, looser with friction.** In self-managed mode:
- removing an app, lengthening the elevation countdown, and lowering the daily limit SHALL take effect at once, with the PIN. A longer countdown SHALL also apply to a countdown already running;
- opening a Play or Settings session, approving an installed hidden app, and installing a Plainphone update SHALL need the PIN, then an elevation;
- shortening the countdown, raising the daily limit, turning off and switching to supervised SHALL be waiting changes.

**App requests.** There SHALL be no request flow. "Add an app" SHALL start a Play elevation.

**Monthly check.** The monthly-check line SHALL ask the user. The check SHALL use a Play elevation.

**Recovery code.** The recovery code SHALL reset the user's PIN only. It SHALL NOT skip a countdown, the daily limit or a waiting change.

#### Scenario: Self-managed user removes an app
- **WHEN** the user enters their PIN and removes a game
- **THEN** the game is hidden at once, with no wait

#### Scenario: Recovery code in self-managed mode
- **WHEN** the user enters their recovery code while a turn-off is waiting
- **THEN** they can set a new PIN, and the turn-off still waits

### Requirement: Elevation in self-managed mode is a single-use grant under the session contract
An **elevation** SHALL be a single-use grant with an id, one kind and a deadline:

| Kind | Allows | Deadline |
|---|---|---|
| Play | a Play session, and approving apps installed during it before the deadline | 10 minutes |
| Settings | a Settings session | 5 minutes |
| Approve | approving one installed hidden app; no packages added to lock task | 5 minutes |
| Update | one Plainphone update, under the existing updater requirement unchanged | 10 minutes |

1. **Countdown.** After the PIN and the choice of kind, Plainphone SHALL count down (default 10 minutes). The phone SHALL work normally during it. The user SHALL be able to cancel at any time.
2. **Start.** When the countdown ends, the user SHALL tap Start and confirm the kind within 5 minutes, or the elevation lapses without a debit.
3. **Debit and journal.** On Start, Plainphone SHALL write the debit and the grant's session journal entry in one atomic write before anything is relaxed. Starts SHALL be serialized so a double tap can't get two.
4. **Use.** The grant SHALL allow only its kind until its deadline, and SHALL follow every step of the session contract.
5. **End.** The grant SHALL be used up at its deadline, on Done, or on any Plainphone restart. An action confirmed after the deadline SHALL NOT complete.

At most one countdown or grant SHALL exist at a time.

**Daily limit.** At most a set number of debits (default 2) SHALL exist within 24 hours of proven running time. A debit SHALL NOT be cleared by a crash, a reboot or a failed write. A damaged debit list SHALL be replaced, durably, by one counting as no turns left from now; if that write fails, no elevation SHALL start.

**Never permanent.** No setting SHALL remove the countdown or the limit, or lengthen a deadline. The countdown SHALL be settable from 10 minutes to 24 hours, and the limit from 0 to 3 a day. The custody limit (a grant stays open if Plainphone can't run) SHALL be disclosed as the one exception, with nobody watching.

Changes made during an elevation SHALL persist, as they would for a trusted person.

#### Scenario: Elevation to add an app
- **WHEN** the user enters their PIN to add an app, waits 10 minutes, taps Start, installs the app and approves it before the deadline
- **THEN** a turn is counted, the Play session lasts at most 10 minutes, and the approved app stays approved after it closes

#### Scenario: Approval after the deadline
- **WHEN** the user installs an app in a Play elevation and opens the approval screen after the 10 minutes
- **THEN** the approval can't complete, and the app stays hidden until another elevation

#### Scenario: Daily limit reached
- **WHEN** the user has 2 debits within the last 24 hours of proven running time and tries a third
- **THEN** Plainphone says when the next one will be available, and nothing opens

#### Scenario: Reboot to reset the count
- **WHEN** the user reboots the phone right after using their second elevation
- **THEN** both turns are still counted after the reboot

#### Scenario: Killed during the countdown
- **WHEN** Plainphone is killed during the countdown
- **THEN** no turn is counted, nothing is relaxed, and the user starts again

#### Scenario: Two countdowns
- **WHEN** the user tries to start a second elevation while one is counting down
- **THEN** Plainphone shows the one in progress and starts nothing new

### Requirement: Switching between modes is a waiting change in both directions
- **Supervised to self-managed** SHALL be started with a trusted person's PIN. Before the wait starts, the user SHALL set their PIN, receive their recovery code and enter their own `userId`, with the same account checks as for a trusted person. Either trusted person or anyone holding the phone can cancel. A trusted person's recovery code can skip it.
- **Self-managed to supervised** SHALL be started with the user's PIN. The incoming trusted person SHALL set up as for adding a trusted person. The wait SHALL NOT be skippable. During the wait, self-managed rules SHALL stay in force, existing debits and countdowns SHALL carry on, and the pending credentials SHALL grant nothing.
- Completion SHALL follow the same steps as changing trusted people: new reset-protection list read back first, then the credential swap.
- A user on a supervised phone has no PIN and SHALL NOT be able to start either switch.

#### Scenario: User enrols a second identity as trusted person
- **WHEN** a self-managed user with no turns left starts a switch to supervised, enrolling an account and number they control
- **THEN** the switch waits 24 hours of proven running time, the new PIN and recovery code grant nothing until it completes, and no elevation is available meanwhile

#### Scenario: User on a supervised phone wants self-managed
- **WHEN** the user of a supervised phone looks for a way to switch to self-managed
- **THEN** there is none without a trusted person's PIN

## MODIFIED Requirements

### Requirement: Supervised sessions follow one custody contract
A **supervised session** SHALL be the only way Plainphone relaxes its lockdown. The MVP has two kinds.

| Kind | Purpose | Packages added to lock task | Deadline |
|---|---|---|---|
| Play | Add an app, or the monthly update check | `com.android.vending` only | 10 minutes |
| Settings | Change an Android setting Plainphone does not provide | the build's Settings package and any same-UID siblings recorded in the build inventory | 5 minutes |

A session SHALL be started by a trusted person in supervised mode, or by the user as an elevation grant in self-managed mode. A session SHALL NOT relax anything other than what this table names. Every session SHALL follow this contract:

1. **Start.** A fresh PIN or recovery-code entry is required. In self-managed mode, the elevation countdown, Start confirmation and daily limit also apply, and the debit is written in the same atomic write as the journal entry.
2. **Journal before anything relaxes.** Plainphone writes a journal entry to device-protected storage, with a checksum, naming the relaxation and the deadline.
3. **Unhide safely.** Any unhide during the session, at its start or later (for example approving an app installed in a Play session), SHALL follow device-lockdown's "Every unhide is saved at once", including its battery guard for discretionary unhides.
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

#### Scenario: Power lost after the save
- **WHEN** a session start unhides a package, the mitigation B flip completes and reads back, and then the phone loses power
- **THEN** the phone is expected to boot normally, and the journaled relaxation is revoked at boot. Task 1.3 tests this on API 35 and 36; the gap between the unhide and the flip is a disclosed residual risk.

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

Using a code SHALL replace its owner's PIN and code in one atomic step, and raise that person's credential generation. It SHALL NOT change the other trusted person's PIN or code. Plainphone SHALL explain that the code cannot unlock reset protection after a wipe.

In supervised mode, a trusted person's recovery code SHALL also be able to skip the wait of the pending waiting change, only through a screen that names that change and asks to confirm skipping it. Resetting a forgotten PIN with the code SHALL NOT skip anything. In self-managed mode, the code SHALL NOT skip anything.

#### Scenario: PIN locked out, code still works
- **WHEN** a trusted person's PIN entry is in a delay and they enter their correct recovery code
- **THEN** they can set a new PIN right away, and a new code is shown once

#### Scenario: Interrupted recovery
- **WHEN** the phone restarts in the middle of a recovery
- **THEN** either the old PIN and code work, or the new ones do. Never neither.

#### Scenario: Recovery code skips a turn-off wait
- **WHEN** in supervised mode, a turn-off is pending, and a trusted person enters their recovery code on the skip screen that says "Skip the wait and turn Plainphone off now?" and confirms
- **THEN** turning off commits at once, every trusted person is texted, and that person's PIN and code are replaced

#### Scenario: PIN reset does not skip
- **WHEN** a turn-off is pending and a trusted person uses their recovery code to reset a forgotten PIN
- **THEN** the turn-off is not skipped

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

### Requirement: Recovery when Plainphone cannot run
The setup guide and the disclosure screen SHALL describe the last-resort path, step by step:
1. the helper wipes the phone from recovery mode;
2. reset protection then requires a listed Google account: in supervised mode a trusted person's or the backup person's; in self-managed mode the user's own or the backup person's;
3. the phone comes back with no Plainphone, so setup has to be done again;
4. anything not backed up is lost.

The guide SHALL say that this path only works if the listed account can be signed in to without this phone (setup checks this for every listed account, including the user's own in self-managed mode).

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
- a hardware-button wipe, and what reset protection does about it;
- that losing power in the moment right after Plainphone unhides an app can still stop the phone from starting, and that recovery then needs a wipe;
- that Plainphone has no server, analytics or account, sends texts only to the trusted people through the carrier, and keeps its history on the phone; but the phone runs Google's Android with a Google account, and Google collects some data that Plainphone can't stop; the setup privacy steps reduce it;
- with two trusted people: that either can approve apps, open sessions, or turn Plainphone off, and that the 24-hour wait protects against someone who saw a PIN, not against the other trusted person;
- in self-managed mode: that Plainphone adds waits and limits but can't stop the user, who holds the PIN; that getting full control back takes at least 24 hours of the phone being on, or a wipe; that a recovery-mode wipe erases the phone and then asks for the user's own account; and that if Plainphone can't run during an elevation, it stays open with nobody watching.

#### Scenario: Trusted person reads the limits
- **WHEN** the trusted person opens "What Plainphone can't stop"
- **THEN** each risk is described in one or two plain sentences

#### Scenario: Self-managed user reads the limits
- **WHEN** a self-managed user opens "What Plainphone can't stop"
- **THEN** it says plainly that Plainphone is friction, not a lock, in this mode

### Requirement: Turning Plainphone off restores everything it changed
Turning Plainphone off SHALL be a waiting change. Starting it SHALL require a fresh PIN or recovery-code entry and two plain confirmations. When the wait matures and passes its re-check (or a trusted person's recovery code skips it, in supervised mode), Plainphone SHALL commit, and the turn-off journal SHALL be its operation journal: it records progress and resumes after a restart. If the battery is below 5% and not charging at that moment, the commit SHALL wait until the phone is charging or above 5%.

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
