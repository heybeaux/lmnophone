## ADDED Requirements

### Requirement: A phone has up to two trusted people, as equals
In **supervised mode**, a phone SHALL have one or two trusted people. Each trusted person SHALL have their own:
- name and phone number;
- PIN, with its own lockout counter and delay;
- recovery code, with its own counter;
- reset-protection Google `userId`.

Either trusted person SHALL be able to do every trusted-person action alone. Neither SHALL be able to see, change or reset the other's PIN or recovery code. A recovery code SHALL reset only its owner's PIN.

The PIN screen SHALL ask which trusted person is entering it before it takes the PIN. An entry SHALL be checked against that person's PIN only, and SHALL count only against that person's lockout. Two people MAY choose the same PIN value; LMNOPhone SHALL NOT compare the two PINs or reveal that they match.

Actions that the other trusted person can put back afterwards (approving or removing an app, changing an app's permissions, opening a supervised session) SHALL take effect at once; the last action stands. Installing an LMNOPhone update SHALL also take effect at once, and LMNOPhone SHALL NOT describe it as reversible. Actions listed as waiting changes SHALL follow "Waiting changes".

Setup SHALL tell both people, in plain words, that they are equals and that either can approve apps or turn LMNOPhone off.

#### Scenario: Two people approve and remove
- **WHEN** the first trusted person approves Spotify, and later the second trusted person removes it
- **THEN** Spotify is hidden again, both actions are texted to both people, and both appear in the history list

#### Scenario: One person locked out
- **WHEN** the first trusted person's PIN is in a lockout delay
- **THEN** the second trusted person can still enter her PIN at once, and the first trusted person's recovery code still works

#### Scenario: An entry is checked against the chosen person only
- **WHEN** the first trusted person and the second trusted person have different PINs, and someone picks "the second trusted person" and enters the first trusted person's PIN
- **THEN** the entry fails, only the second trusted person's verifier and counter are used, and the first trusted person's counter does not change

### Requirement: Texts go to every trusted person, and actions are kept in a history list
Each privileged action, each waiting-change start, cancel, skip and completion, and each notice (app requests, the monthly check, lockout warnings, "LMNOPhone needs attention", system-update notices) SHALL be texted to **every** trusted person, including the one who did it. Routine receipts MAY be combined into one daily text; waiting-change texts SHALL be sent at once.

Each text SHALL have its own delivery status (waiting, sent, gave up), shown in trusted-person mode. A failed or late text SHALL NOT change what is enforced.

LMNOPhone SHALL have no LMNOPhone-run server, no analytics or telemetry, and no account. It SHALL send data only as these texts, through the carrier, and over local Wi-Fi to the helper's computer when updating itself.

Trusted-person mode SHALL keep a 90-day history list of privileged actions and waiting changes: which credential, what, when, and text status. It SHALL NOT hold app content, URLs or message text. It SHALL be kept in device-protected storage.

#### Scenario: Someone else used my PIN
- **WHEN** a child enters the first trusted person's PIN and approves a game
- **THEN** both the first trusted person and the second trusted person get a text saying the first trusted person's PIN approved it

#### Scenario: A text fails
- **WHEN** a notice to the second trusted person can't be sent because there is no signal
- **THEN** the action still takes effect, and her text shows "waiting" and later "sent" or "gave up"

### Requirement: Waits, countdowns and daily counts use one proven running-time clock
LMNOPhone SHALL keep a **proven running time** counter, P, in device-protected storage, as one atomic record holding: the **epoch**, the total proven so far in that epoch, the boot id, and the `SystemClock.elapsedRealtime()` reading at the last checkpoint.

- **Same boot:** a checkpoint SHALL add the time since the last checkpoint and store the new reading, in one atomic write. No interval SHALL be counted twice.
- **New boot:** time between the last checkpoint and the reboot SHALL NOT be counted. The first checkpoint after boot SHALL add the time since this boot started and store the new boot id.
- Checkpoints SHALL happen at least every 5 minutes, at every operation state change, and at every elevation debit.
- Every record that stores a P reading SHALL also store the epoch. A reading from any epoch other than the current one SHALL count as taken at the start of the current epoch.
- A waiting change SHALL mature, and a debit SHALL expire, when P has grown by at least 24 hours since its reading.
- Countdowns, Start windows and grant deadlines SHALL use `elapsedRealtime()` within one boot. A reboot SHALL end them. A clock repair SHALL NOT change them.
- Wall-clock and time-zone changes SHALL have no effect on any of these.

**Damaged, missing or inconsistent P** (a bad checksum, an unreadable boot id, or an `elapsedRealtime()` lower than the stored reading within the same boot): LMNOPhone SHALL write a new epoch with total 0, anchored at the current boot id and the current `elapsedRealtime()`. Nothing SHALL mature early and no debit SHALL be refunded. If that write fails, no waiting change SHALL mature and no elevation SHALL start until it succeeds, and "LMNOPhone needs attention" SHALL show.

The screens SHALL say that time while the phone is off doesn't count.

#### Scenario: Repeated process restarts
- **WHEN** LMNOPhone's process is killed and restarted ten times within one boot
- **THEN** P has grown by the real elapsed time of that boot, not more

#### Scenario: Reboot during a wait
- **WHEN** a turn-off is started, and after 10 hours the phone is switched off for 8 hours
- **THEN** the turn-off does not happen until at least 24 hours of proven running time have passed, so at least 32 hours after it started

#### Scenario: Clock moved forward
- **WHEN** someone moves the clock or time zone forward by a day during a wait
- **THEN** the wait does not end early

#### Scenario: Counter damaged late in a long boot
- **WHEN** the phone has been up for 30 hours, P is found damaged, and a turn-off and two debits exist
- **THEN** the new epoch starts at the current moment, the turn-off and both debits need a fresh 24 hours of proven running time from then, and the boot's earlier 30 hours are not credited

#### Scenario: Interrupted repair
- **WHEN** LMNOPhone writes a new epoch and is killed before anything else
- **THEN** every record with the old epoch counts as taken at the start of the new epoch, so nothing matures early

### Requirement: Waiting changes take 24 hours, one at a time, and are recoverable
These SHALL be **waiting changes**:
1. turning LMNOPhone off;
2. adding a trusted person, handing a trusted-person role over, or removing a trusted person;
3. switching mode, in either direction;
4. in self-managed mode, shortening the elevation countdown or raising the daily limit.

**Credential store.** The mode, each PIN holder's slot (including a credential generation, raised whenever that slot's PIN or recovery code is replaced), the backup person's `userId` if any, the current self-managed friction settings, and a structure generation (raised whenever the set of people or the mode changes) SHALL be one atomic record in device-protected storage.

**Operation record.** Each waiting change SHALL have one operation record, written atomically with a revision number that rises on every write, kept in two copies in device-protected storage. LMNOPhone SHALL use the valid copy with the highest revision. It SHALL hold: the request id; the kind; the immutable inputs, including the credential patch for changes of people or mode; the dependencies (each slot it starts from, removes or replaces, with its credential generation, and the structure generation); the starting credential; a cancel code, unique to that request and never reused on that phone; the P reading and epoch; the state; and the texts owed for that state, with recipients fixed when queued.

**States:** pending, committing, rolling back, completed, cancelled. Completed and cancelled are terminal.

**One at a time.** An operation SHALL hold the single change slot from creation until a terminal state. No other waiting change SHALL start meanwhile; trying SHALL show the one in progress.

**One write per state change.** Each state change and the texts that go with it SHALL be one atomic write. LMNOPhone SHALL show a new state only after that write succeeds, and SHALL send any queued text not yet marked sent on every start. Delivery SHALL NOT be a condition for anything.

**Idempotent, directed steps.** Committing and rolling back SHALL run steps that are safe to repeat and are read back. Every retry, timer and policy pass SHALL re-read the operation record and act only if the request id and direction match what it was scheduled for. A restart SHALL NOT turn a rollback into a forward run.

**Start.** A waiting change SHALL start only after a fresh PIN entry (a trusted person's in supervised mode, the user's in self-managed mode) and the confirmations that action already needs, by creating its record in state pending with the start texts queued. If the write fails, the change SHALL NOT start.

**Texts.** The start text SHALL say what will happen, that it happens after at least 24 hours of the phone being on, and how to cancel. If Q15 passed, it SHALL include the reply `CANCEL` with that request's cancel code.

**Dependencies.** Before commit, if a dependency changes (a slot it depends on gets a new PIN or code, or the structure generation changes), the operation SHALL be cancelled at once and texted to every trusted person. A credential change in a slot it doesn't depend on SHALL NOT affect it.

**Cancel, while pending.** The pending change SHALL be cancelled by any of these:
- a trusted person's PIN, on the phone;
- anyone holding the phone, without a PIN;
- in self-managed mode, the user;
- a reply of `CANCEL` and that request's cancel code from a trusted person's number on record, **only if** feasibility question Q15 passes. A reply whose code doesn't match the pending request SHALL do nothing and SHALL be logged. A reply SHALL NOT start, skip, approve or shorten anything.

The cancelled state and its texts SHALL be written before LMNOPhone shows "Cancelled". If that write fails, LMNOPhone SHALL NOT show "Cancelled", SHALL retry, and SHALL NOT commit while the cancel is unresolved.

**Where Cancel is reachable.** A pending change and its PIN-free Cancel SHALL be on the home-screen notice line when that line is shown, on the "LMNOPhone needs attention" screen, and on LMNOPhone's own settings screen.

**Skip.** In supervised mode, a trusted person SHALL be able to skip the wait with their recovery code, only through a screen that names the exact pending change. After they confirm and set a new PIN, one atomic write of the credential store and the operation record SHALL replace their PIN and code, raise their credential generation, record them as the skipper, and move the operation to committing. The skipper's authority replaces the starter's; the other dependencies SHALL still be checked. Entering a recovery code only to reset a forgotten PIN SHALL NOT skip anything. In self-managed mode, nothing SHALL skip the wait.

**Commit.** When the wait matures, LMNOPhone SHALL re-check the dependencies and the mode against the credential store, then move the operation to committing in one write, or cancel it. From committing on, the PIN-free Cancel SHALL NOT be offered, and screens SHALL say "Already in progress". After commit, a credential change SHALL NOT stop the operation.

**Lost operation record.** If no valid copy can be read, LMNOPhone SHALL reconcile the same way whatever the lost change's kind:
- if LMNOPhone is no longer Device Owner, it SHALL report "Off", as in "Turning LMNOPhone off";
- otherwise, treating the credential store as the truth, it SHALL re-apply the complete active policy, set the reset-protection list to every PIN holder's `userId` plus the backup person's and read it back, and use the friction settings in the credential store. It SHALL NOT adopt pending credentials or pending friction values.
- It SHALL then free the slot, log it, and text every trusted person the people, mode and state it recovered. The text SHALL NOT say the change was cancelled or didn't finish.

If the credential store is invalid, or the reconcile can't read back, the slot SHALL stay held, "LMNOPhone needs attention" SHALL show, and nothing SHALL be guessed. A current PIN holder SHALL be able to run Repair with their PIN, which retries exactly this reconcile and shows what failed. Repair SHALL NOT skip a wait, start a change or adopt pending values.

No part of this SHALL gate calls, Emergency or alerts. During the wait, the lockdown SHALL stay fully in place.

#### Scenario: Crash before the start is written
- **WHEN** LMNOPhone is killed right after a turn-off is confirmed
- **THEN** either no record exists and nothing is pending, or the pending record and its queued texts both exist and the texts are sent after restart

#### Scenario: Owner replaces a seen PIN
- **WHEN** a child starts a handover with the second trusted person's PIN, and the second trusted person then resets her PIN with her recovery code
- **THEN** the PIN reset does not skip anything, and the handover is cancelled at once because a slot it depends on changed

#### Scenario: Other person's PIN change
- **WHEN** the first trusted person starts handing his role over, and the second trusted person replaces her PIN during the wait
- **THEN** the handover is not cancelled, and when it completes the second trusted person's new PIN still works

#### Scenario: Skip with the other person's code
- **WHEN** the first trusted person started a turn-off, and the second trusted person skips the wait with her recovery code
- **THEN** one write replaces the second trusted person's PIN and code, records her as the skipper and starts committing; a crash leaves either none or all of that done

#### Scenario: Second change while one is committing
- **WHEN** a handover is committing and stuck, and someone tries to start a turn-off
- **THEN** the turn-off can't start until the handover reaches completed or cancelled

#### Scenario: Cancelled by the user
- **WHEN** a child who saw a PIN starts a turn-off, and the user taps Cancel on the home screen
- **THEN** nothing changes, and both trusted people get a text saying someone holding the phone cancelled it

#### Scenario: Stale timer after cancel
- **WHEN** a turn-off is cancelled and a timer scheduled before the cancel fires later
- **THEN** the timer finds the request cancelled and does nothing

#### Scenario: Delayed reply cancels the wrong request
- **WHEN** Q15 passed, the second trusted person replied `CANCEL` with request A's code, A was cancelled on the phone, request B was started, and then the second trusted person's reply arrives
- **THEN** B is not cancelled, because the code doesn't match, and the reply is logged

#### Scenario: Copies disagree
- **WHEN** one copy of the operation record says committing at revision 7 and the other says rolling back at revision 8
- **THEN** LMNOPhone continues rolling back

#### Scenario: Both copies lost during a handover
- **WHEN** both copies of a committing handover's record are unreadable after a restart
- **THEN** LMNOPhone re-applies the policy, sets reset protection to the people in the credential store plus the backup person, reads it back, frees the slot and texts everyone who is now in charge

#### Scenario: Record lost after the credential patch
- **WHEN** a handover's credential patch was applied and both copies are then lost before it is marked completed
- **THEN** the new person stays in charge, and the text says who is in charge now, not that the change didn't finish

#### Scenario: Record lost during a turn-off
- **WHEN** both copies are lost after some turn-off steps, while LMNOPhone is still Device Owner
- **THEN** LMNOPhone re-applies the full lockdown, and reports what it recovered

### Requirement: Changing who the trusted people are needs a current trusted person
Adding a trusted person, handing a role over and removing a trusted person SHALL each need a current trusted person's PIN, and SHALL be waiting changes. The user alone SHALL NOT be able to change who the trusted people are.

**Incoming person.** Before the wait starts, the incoming person SHALL, in person:
- enter their name and phone number;
- set their own PIN;
- receive their own recovery code, shown once;
- enter their reset-protection `userId` using the guide's People API steps on their own device;
- confirm a test text from LMNOPhone.

A current trusted person or the helper SHALL record that the incoming person's account can be signed in to from another device, that its second factor is not only on an LMNOPhone, and that it is at least 72 hours old. These details SHALL be stored in the operation record and SHALL grant nothing until the change completes.

**Handing over** SHALL need the outgoing person's own PIN or recovery code.

**Removing.** LMNOPhone SHALL NOT remove the last trusted person. That is a switch to self-managed mode or turning off.

**Patch.** The operation SHALL store a patch that names only the slots it adds, removes or replaces. Applying it SHALL NOT change any other slot.

**Completing.** The forward steps SHALL be, each read back:
1. set the reset-protection list to the `userId` values the credential store will hold after the patch, plus the backup person's, and send the broadcast (device-lockdown);
2. only when step 1 reads back correctly, apply the patch to the credential store in one atomic write and raise the structure generation;
3. move to completed, with texts to every trusted person.

If step 1 does not read back correctly, the operation SHALL stay committing and retry on every policy pass, the people in charge SHALL keep their current credentials, trusted-person mode SHALL show "Reset protection not updated – call the helper", and LMNOPhone SHALL NOT report the change as done or claim which account a wipe would ask for.

**Stopping a stuck change.** While step 1 is failing and step 2 has not happened, any current trusted person SHALL be able to stop the change with their PIN from the needs-attention screen. LMNOPhone SHALL first write the rolling-back state, then set the reset-protection list back to the people in the credential store and read it back, then move to cancelled. If that readback fails, the operation SHALL stay rolling back and retry. Once step 2 has happened, stopping SHALL NOT be offered.

After an interruption, either the old people or the new people SHALL be in charge, never neither.

#### Scenario: Handover
- **WHEN** the first trusted person hands his role to his sister with his PIN, she sets up her PIN, code and ID, and 24 hours of proven running time pass without a cancel
- **THEN** the reset-protection list names her and not him, then she becomes a trusted person, and the first trusted person's PIN and code no longer work

#### Scenario: Someone who saw a PIN adds themselves
- **WHEN** a child enters the second trusted person's PIN and starts adding themselves as a second trusted person
- **THEN** both current trusted people are texted at once, anyone can cancel during the wait, and the child's PIN grants nothing during it

#### Scenario: Reset protection can't be updated
- **WHEN** a handover matures and the new reset-protection list does not read back
- **THEN** the first trusted person stays a trusted person with his current PIN, his sister's PIN grants nothing, the change is not reported as done, and the helper is asked to look

#### Scenario: Stopping a stuck handover, interrupted
- **WHEN** the second trusted person stops a stuck handover, LMNOPhone restores the old reset-protection list, and the phone restarts before the change is marked cancelled
- **THEN** LMNOPhone continues rolling back and does not resume the handover

#### Scenario: Restart while completing
- **WHEN** the phone restarts in the middle of completing a handover
- **THEN** either the outgoing person's PIN or the incoming person's PIN works afterwards, never neither

### Requirement: Self-managed mode is friction, not a lock
A phone SHALL be in **supervised mode** (one or two trusted people) or **self-managed mode**. In self-managed mode, the user holds a PIN and a recovery code and is their own trusted person.

The lockdown at rest SHALL be the same in both modes: Device Owner, hidden apps, lock task, restrictions, the emergency floor and web containment. General browsers SHALL stay hidden in both modes.

Setup and "What LMNOPhone can't stop" SHALL say, in plain words, that in self-managed mode LMNOPhone adds waits and limits, that it can't stop the user because the user holds the PIN, and that the user can remove it by waiting at least 24 hours of the phone being on (longer if the change is cancelled or interrupted) or by wiping the phone.

**Stricter at once, looser with friction.** In self-managed mode:
- removing an app, lengthening the elevation countdown, and lowering the daily limit SHALL take effect at once, with the PIN. A longer countdown SHALL also apply to a countdown already running;
- opening a Play or Settings session, approving an installed hidden app, and installing an LMNOPhone update SHALL need the PIN, then an elevation;
- shortening the countdown, raising the daily limit, turning off and switching to supervised SHALL be waiting changes.

**App requests.** There SHALL be no request flow. "Add an app" SHALL start a Play elevation.

**Monthly check.** The monthly-check line SHALL ask the user: "Time for your monthly phone check" (calm-home-screen). The check SHALL use a Play elevation.

**Recovery code.** The recovery code SHALL reset the user's PIN only. It SHALL NOT skip a countdown, the daily limit or a waiting change.

#### Scenario: Self-managed user removes an app
- **WHEN** the user enters their PIN and removes a game
- **THEN** the game is hidden at once, with no wait

#### Scenario: Recovery code in self-managed mode
- **WHEN** the user enters their recovery code while a turn-off is waiting
- **THEN** they can set a new PIN, the turn-off is cancelled at once because the PIN that started it was replaced, and nothing is skipped

### Requirement: Elevation in self-managed mode is a single-use grant, separate from the package access it opens
An **elevation** SHALL be a **grant** with an id, one kind, a deadline and a binding:

| Kind | Package access | Allows | Bound to | Deadline |
|---|---|---|---|---|
| Play | a Play session | installing in Play, then approving each app installed during that session, until the deadline | each approval to the installed package and signer | 10 minutes |
| Settings | a Settings session | changing settings | – | 5 minutes |
| Approve | none | approving one installed hidden app | the chosen package and signer; used up by that approval | 5 minutes |
| Update | none | one LMNOPhone update, under the existing updater requirement unchanged | the verified staged bytes and version; used up by that install | 10 minutes |

1. **Countdown.** After the PIN and the choice of kind, LMNOPhone SHALL count down (default 10 minutes). This PIN entry SHALL be the fresh authority for the grant. The phone SHALL work normally during the countdown, and the user SHALL be able to cancel it.
2. **Start.** When the countdown ends, the user SHALL tap Start and confirm the kind within 5 minutes, or the elevation lapses without a debit.
3. **Debit.** On Start, LMNOPhone SHALL write the debit and the grant (with the session journal entry, for Play and Settings) in one atomic write before anything is relaxed. Starts SHALL be serialized so a double tap can't get two.
4. **Package access** SHALL end on the session contract's triggers. For a Play grant, returning to LMNOPhone to approve SHALL end Play access but SHALL keep the grant until its deadline.
5. **The grant** SHALL end at its deadline, on Done, or on any LMNOPhone restart. Nothing SHALL extend or restore it.
6. **Last check.** Immediately before the irreversible step (the approval write, or the PackageInstaller commit), LMNOPhone SHALL check that the grant is live, of the right kind, not used up, and that the binding matches, and SHALL apply its effect in the same step: an Approve or Update grant is used up by its one action; a Play grant records that package's approval and stays live, until its deadline, for other apps installed in that session. If the check fails, nothing SHALL happen. An install committed before the deadline MAY finish after it; no rollback is promised.
7. **Updates.** The updater's closing of open sessions SHALL end package access only, not the Update grant.

At most one countdown or grant SHALL exist at a time.

**Daily limit.** At most a set number of debits (default 2) SHALL exist within 24 hours of proven running time. A debit SHALL NOT be cleared by a crash, a reboot or a failed write. A damaged debit list SHALL be replaced, durably, by one counting as no turns left from now; if that write fails, no elevation SHALL start.

**Never permanent.** No setting SHALL remove the countdown or the limit, or lengthen a deadline. The countdown SHALL be settable from 10 minutes to 24 hours, and the limit from 0 to 3 a day. The custody limit (package access stays open if LMNOPhone can't run) SHALL be disclosed as the one exception, with nobody watching.

Changes made with a grant SHALL persist, as they would for a trusted person.

#### Scenario: Elevation to add an app
- **WHEN** the user enters their PIN to add an app, waits 10 minutes, taps Start, installs the app, returns to LMNOPhone and approves it before the deadline
- **THEN** a turn is counted, Play access ended when they left Play, the approval completes, and the app stays approved

#### Scenario: Approval after the deadline
- **WHEN** the user installs an app in a Play elevation and confirms the approval after the 10 minutes
- **THEN** the approval doesn't happen, and the app stays hidden until another elevation

#### Scenario: One Play grant, two apps
- **WHEN** the user installs apps A and B in one Play elevation, returns to LMNOPhone and approves both before the deadline
- **THEN** both approvals complete, each bound to its own package and signer

#### Scenario: One Approve grant, two apps
- **WHEN** the user approves one hidden app with an Approve grant and then tries a second
- **THEN** the second approval doesn't happen, because the grant was used up

#### Scenario: Update delayed past the deadline
- **WHEN** an Update grant's download and checks finish after its 10 minutes
- **THEN** the install is not committed

#### Scenario: Daily limit reached
- **WHEN** the user has 2 debits within the last 24 hours of proven running time and tries a third
- **THEN** LMNOPhone says when the next one will be available, and nothing opens

#### Scenario: Reboot to reset the count
- **WHEN** the user reboots the phone right after using their second elevation
- **THEN** both turns are still counted after the reboot

#### Scenario: Killed during the countdown
- **WHEN** LMNOPhone is killed during the countdown
- **THEN** no turn is counted, nothing is relaxed, and the user starts again

#### Scenario: Two countdowns
- **WHEN** the user tries to start a second elevation while one is counting down
- **THEN** LMNOPhone shows the one in progress and starts nothing new

### Requirement: Switching between modes is a waiting change in both directions
- **Supervised to self-managed** SHALL be started with a trusted person's PIN. Before the wait starts, the user SHALL set their PIN, receive their recovery code and enter their own `userId`, with the same account checks as for a trusted person. Either trusted person or anyone holding the phone can cancel. A trusted person's recovery code can skip it.
- **Self-managed to supervised** SHALL be started with the user's PIN. The incoming trusted person SHALL set up as for adding a trusted person. The wait SHALL NOT be skippable. During the wait, self-managed rules SHALL stay in force, existing debits and countdowns SHALL carry on, and the pending credentials SHALL grant nothing.
- Completion SHALL follow the same steps as changing trusted people: new reset-protection list read back first, then the credential swap.
- A user on a supervised phone has no PIN and SHALL NOT be able to start either switch.

#### Scenario: User enrols a second identity as trusted person
- **WHEN** a self-managed user with no turns left starts a switch to supervised, enrolling an account and number they control
- **THEN** the switch waits 24 hours of proven running time, the new PIN and recovery code grant nothing until it completes, and no elevation is available through the pending credentials; the user's own daily limit keeps ageing as normal

#### Scenario: User on a supervised phone wants self-managed
- **WHEN** the user of a supervised phone looks for a way to switch to self-managed
- **THEN** there is none without a trusted person's PIN

## MODIFIED Requirements

### Requirement: Supervised sessions follow one custody contract
A **supervised session** SHALL be the only way LMNOPhone opens package access to Play or Settings. Approving apps, installing LMNOPhone updates and turning off follow their own authority requirements. The MVP has two kinds of session.

| Kind | Purpose | Packages added to lock task | Deadline |
|---|---|---|---|
| Play | Add an app, or the monthly update check | `com.android.vending` only | 10 minutes |
| Settings | Change an Android setting LMNOPhone does not provide | the build's Settings package and any same-UID siblings recorded in the build inventory | 5 minutes |

A session SHALL be started by a trusted person in supervised mode, or by the user as an elevation grant in self-managed mode. A session SHALL NOT relax anything other than what this table names. Every session SHALL follow this contract:

1. **Start.** A fresh PIN or recovery-code entry is required. In self-managed mode, the elevation countdown, Start confirmation and daily limit also apply, and the debit is written in the same atomic write as the journal entry.
2. **Journal before anything relaxes.** LMNOPhone writes a journal entry to device-protected storage, with a checksum, naming the relaxation and the deadline.
3. **Unhide safely.** Any unhide during the session, at its start or later (for example approving an app installed in a Play session), SHALL follow device-lockdown's "Every unhide is saved at once", including its battery guard for discretionary unhides.
4. **End triggers.** The session ends on Done, screen off, an incoming call, the deadline, or the user leaving the session's packages.
5. **Closing.** On ending, LMNOPhone:
   - removes the relaxation;
   - runs the **protected-state check** (below). Calls, Emergency and alerts are never gated on it;
   - restores or rejects any change that breaks a mandatory protection, while keeping ordinary setting changes such as ringtone, display or Wi-Fi;
   - reads the policy back;
   - only then clears the journal.
6. **Revoke on every start.** A Direct-Boot-aware receiver handles `LOCKED_BOOT_COMPLETED`, and every LMNOPhone start first revokes any journaled relaxation. This uses only device-protected data, with no credential-encrypted database or key.
7. **Damaged journal.** If the journal entry is missing its checksum or is invalid, LMNOPhone treats every session relaxation as active and revokes them all.
8. **Custody.** In supervised mode, the phone is in the trusted person's custody for the whole session. The screen says: "Stay with the phone until you tap Done." In self-managed mode, the screen says when LMNOPhone will close the session.
9. **Handback.** In supervised mode, the phone SHALL NOT be handed back while a readback fails.

**Protected-state check.** It covers exactly the invariants in this table and nothing else. Any setting not in the table, such as ringtone, font size, Wi-Fi networks, the chosen system keyboard or its language, is the user's choice and SHALL be kept.

| Invariant | Expected | Readback | Repair | If repair fails |
|---|---|---|---|---|
| User restrictions | the policy set | `getUserRestrictions` | re-add | non-handback state |
| Lock-task packages and features | policy list + emergency UI subset (not the whole floor); features incl. BLOCK | `getLockTaskPackages`/`Features` | re-set | non-handback state |
| Floor and never-hide packages | unhidden and enabled | `isApplicationHidden`, enabled state | unhide | non-handback state |
| Browser role | LMNOPhone | `RoleManager` | re-request role | non-handback state |
| Default SMS | Google Messages | `ROLE_SMS` holder | `setDefaultSmsApplication` | non-handback state |
| LMNOPhone `SEND_SMS` | granted and allowlisted | permission state | `setPermissionGrantState` | trusted people told; requests still listed |
| Permitted input methods | system-only, plus listed | `getPermittedInputMethods` | re-set (system IMEs stay available by Android's rule) | non-handback state |
| Permitted accessibility services | system-only | `getPermittedAccessibilityServices` | re-set | non-handback state |
| Approved apps' pre-granted permissions | as approved | `getPermissionGrantState` | re-set | trusted people told |
| Emergency alert configuration | `DISALLOW_CONFIG_CELL_BROADCASTS` set; cell broadcast floor packages unhidden and enabled | restriction and package state | re-add or unhide | non-handback state. If the build has no readback, the phone is held by the helper. It is never reported as passing. |

A **non-handback state** means: the trusted person (or, in self-managed mode, the user) sees "Not ready – call the helper". Calls, Emergency and alerts stay available throughout. No check SHALL gate them.

**Accepted limit.** If LMNOPhone cannot run while a relaxation is in place, nothing revokes it automatically. That applies whatever the cause: a bad update, a storage or startup fault, or a crash loop. The relaxation stays until a helper fixes the phone. This SHALL be disclosed, and the board SHALL explicitly accept it before the MVP is approved. In self-managed mode, nobody is watching when this happens; this SHALL be disclosed too. Q12 measures this behaviour; it does not prove that the failure cannot happen. The disclosure SHALL also say that during a session, Settings or Play pages may show web content, such as help or account pages.

#### Scenario: Screen turns off mid-session
- **WHEN** the screen turns off during a Play or Settings session
- **THEN** the session ends, the session packages cannot be reached, and the protected-state check passes before the phone is handed back. Calls and Emergency stay available throughout.

#### Scenario: Settings session changes a protection
- **WHEN** someone uses a Settings session to change the default SMS app or the permitted keyboards
- **THEN** the protected-state check restores the protected value, tells the trusted people, and keeps ordinary changes such as the ringtone

#### Scenario: LMNOPhone is killed mid-session
- **WHEN** LMNOPhone's process is killed during a session and restarts, or the phone reboots
- **THEN** the journaled relaxation is revoked before any other LMNOPhone work, including before the first unlock

#### Scenario: LMNOPhone cannot run
- **WHEN** a build that crashes on every start is installed while a session is journaled
- **THEN** Q12 records how long the relaxation stays reachable, and the result is presented against the board-accepted limit, not as a pass or fail of "cannot happen"

#### Scenario: Power lost after the save
- **WHEN** a session start unhides a package, the mitigation B flip completes and reads back, and then the phone loses power
- **THEN** the phone is expected to boot normally, and the journaled relaxation is revoked at boot. Task 1.3 tests this on API 35 and 36; the gap between the unhide and the flip is a disclosed residual risk.

#### Scenario: Nearly flat battery
- **WHEN** someone starts a session that would unhide a package at 3% battery, not charging
- **THEN** the session does not start, and LMNOPhone says to charge the phone first

### Requirement: Trusted-person PIN has an exact lockout schedule
Each PIN (each trusted person's, or the user's in self-managed mode) SHALL be at least 6 digits. Each SHALL be stored only as a PBKDF2-HMAC-SHA256 hash with at least 600,000 iterations and a random 16-byte salt.

**Lockout schedule, per PIN:**
- Failures 1–4 cause no delay.
- Failure 5 starts a 30-second delay.
- Each later failure doubles the delay, up to a cap of 1 hour.
- Entries made during a delay SHALL be rejected without counting and without extending the delay.

**Stored state.** For each PIN, the failure count and the remaining delay SHALL be stored together, in one atomic write to device-protected storage. After a reboot, LMNOPhone SHALL resume the *remaining* delay, not restart it. Changing the clock SHALL have no effect. One PIN's lockout SHALL NOT affect another PIN or any recovery code.

**Other rules:**
- After 10 failures on a PIN, LMNOPhone SHALL try to text every trusted person. Whether that text sends SHALL NOT change what is enforced.
- Wrong entries SHALL NOT wipe the device.
- No lockout SHALL gate calls or emergency access.

#### Scenario: Guessing across reboots
- **WHEN** someone enters 6 wrong PINs for one trusted person, then reboots partway through the 60-second delay
- **THEN** after the reboot only the remaining part of that delay applies, and that person's count continues from 6

#### Scenario: Entry during a delay
- **WHEN** a PIN is submitted during that PIN's delay
- **THEN** it is rejected without being checked, and neither the count nor the delay changes

### Requirement: Recovery code has its own counter
Setup SHALL generate a recovery code for each trusted person (or for the user in self-managed mode), with at least 128 bits of randomness, and show it once. LMNOPhone SHALL store only its hash. In supervised mode, the helper SHALL confirm that the trusted person, and not the helper, has the code.

Each recovery code SHALL use its own failure counter, separate from every PIN's, with the same schedule. A PIN lockout SHALL NOT block any recovery code.

Using a code SHALL replace its owner's PIN and code in one atomic step, and raise that person's credential generation. It SHALL NOT change the other trusted person's PIN or code. LMNOPhone SHALL explain that the code cannot unlock reset protection after a wipe.

In supervised mode, a trusted person's recovery code SHALL also be able to skip the wait of the pending waiting change, only through a screen that names that change and asks to confirm skipping it. Resetting a forgotten PIN with the code SHALL NOT skip anything. In self-managed mode, the code SHALL NOT skip anything.

#### Scenario: PIN locked out, code still works
- **WHEN** a trusted person's PIN entry is in a delay and they enter their correct recovery code
- **THEN** they can set a new PIN right away, and a new code is shown once

#### Scenario: Interrupted recovery
- **WHEN** the phone restarts in the middle of a recovery
- **THEN** either the old PIN and code work, or the new ones do. Never neither.

#### Scenario: Recovery code skips a turn-off wait
- **WHEN** in supervised mode, a turn-off is pending, and a trusted person enters their recovery code on the skip screen that says "Skip the wait and turn LMNOPhone off now?" and confirms
- **THEN** turning off commits at once, every trusted person is texted, and that person's PIN and code are replaced

#### Scenario: PIN reset does not skip
- **WHEN** a turn-off is pending and a trusted person uses their recovery code to reset a forgotten PIN
- **THEN** the turn-off is not skipped

### Requirement: Privileged actions need fresh authority and no outside input
A fresh PIN or recovery-code entry SHALL be required for each of these actions: approving an app, removing an app, starting any supervised session, installing an LMNOPhone update, turning LMNOPhone off, starting or skipping any waiting change, and changing self-managed friction settings.

Admin activities SHALL NOT be exported. No outside intent, extra or flag SHALL be accepted as proof of authentication. A received text SHALL never be accepted as authority, except as a cancel of a pending waiting change. Admin screens SHALL use `FLAG_SECURE`. Backup SHALL be disabled. Secrets SHALL NOT be logged.

#### Scenario: Outside intent claims authority
- **WHEN** another app sends LMNOPhone an intent whose extras claim to approve an app or turn LMNOPhone off
- **THEN** nothing changes and no admin screen opens

#### Scenario: A text claims authority
- **WHEN** a text from a trusted person's number says "APPROVE" or "TURN OFF"
- **THEN** nothing changes

### Requirement: App requests are untrusted text sent to a fixed recipient
In supervised mode, the user SHALL be able to request an app, with a reason of up to 280 characters and at most 5 requests a day. LMNOPhone SHALL send the request by SMS to every trusted person's number from setup. It SHALL list the request in trusted-person mode, shown as "From [user]:" in plain text. Either trusted person can act on it.

In self-managed mode there SHALL be no request flow; "Add an app" starts an elevation.

**SMS setup.**
- LMNOPhone holds `SEND_SMS` as an allowlisted restricted permission, granted by the Device Owner, and sends with the SMS subscription chosen at setup.
- Setup proves this works end to end: a test text that each trusted person confirms they received.

**Approval.** Approving a request SHALL start a Play session. The approval SHALL be bound to the package actually installed and its signing certificate, not to the user's text.

**Failed sends.** If an SMS fails, LMNOPhone SHALL say so and keep the request listed.

#### Scenario: Misleading request
- **WHEN** a request names one app and a different package is installed in the session
- **THEN** the approval screen shows the installed package's real name and developer, and the trusted person approves that package

#### Scenario: Request with two trusted people
- **WHEN** the user requests an app on a phone with two trusted people
- **THEN** both are texted, and the request is listed until one of them acts on it

### Requirement: Recovery when LMNOPhone cannot run
The setup guide and the disclosure screen SHALL describe the last-resort path, step by step:
1. the helper wipes the phone from recovery mode;
2. reset protection then requires a listed Google account: in supervised mode a trusted person's or the backup person's; in self-managed mode the user's own or the backup person's;
3. the phone comes back with no LMNOPhone, so setup has to be done again;
4. anything not backed up is lost.

The guide SHALL say that this path only works if the listed account can be signed in to without this phone (setup checks this for every listed account, including the user's own in self-managed mode).

This requirement covers failures of LMNOPhone itself on a running Android system. It does not cover a system that can't start, such as the PLA-22 boot loop, where there is no working dialer until a recovery wipe.

#### Scenario: LMNOPhone crash loop
- **WHEN** LMNOPhone cannot start at all, while Android itself starts normally
- **THEN** calls and 9-1-1 still work from the lock screen and the system dialer, and the guide's recovery steps return the phone to a usable state

### Requirement: Plain disclosure of leftover risk
Trusted-person mode SHALL include a "What LMNOPhone can't stop" screen, written in plain words. It SHALL cover each of these:
- web pages inside approved apps;
- Google account or help screens that testing found reachable;
- that a supervised session gives open access, and so needs custody;
- that someone who knows a PIN can keep starting changes, and anyone holding the phone can keep cancelling them, which can delay a legitimate change;
- that a session stays open if LMNOPhone cannot run;
- that an unapproved app may run in the background, with no time limit, if hiding it fails;
- a hardware-button wipe, and what reset protection does about it;
- that losing power in the moment right after LMNOPhone unhides an app can still stop the phone from starting, and that recovery then needs a wipe;
- that LMNOPhone has no server of its own, no analytics or telemetry and no account; that it sends texts to the trusted people through the carrier, and connects over local Wi-Fi to the helper's computer only to update itself; and that it keeps its history on the phone; but the phone runs Google's Android with a Google account, and Google collects some data that LMNOPhone can't stop; the setup privacy steps reduce it;
- with two trusted people: that either can approve apps, open sessions, or turn LMNOPhone off, and that the 24-hour wait protects against someone who saw a PIN, not against the other trusted person;
- in self-managed mode: that LMNOPhone adds waits and limits but can't stop the user, who holds the PIN; that getting full control back takes at least 24 hours of the phone being on (longer if the change is cancelled or interrupted), or a wipe; that a recovery-mode wipe erases the phone and then asks for the user's own account; and that if LMNOPhone can't run during an elevation, it stays open with nobody watching.

#### Scenario: Trusted person reads the limits
- **WHEN** the trusted person opens "What LMNOPhone can't stop"
- **THEN** each risk is described in one or two plain sentences

#### Scenario: Self-managed user reads the limits
- **WHEN** a self-managed user opens "What LMNOPhone can't stop"
- **THEN** it says plainly that LMNOPhone is friction, not a lock, in this mode

### Requirement: Turning LMNOPhone off restores everything it changed
Turning LMNOPhone off SHALL be a waiting change. Starting it SHALL require a fresh PIN or recovery-code entry and two plain confirmations. When the wait matures and passes its re-check (or a trusted person's recovery code skips it, in supervised mode), LMNOPhone SHALL commit. The forward steps SHALL be the restore steps below, recorded in the operation record and resumed after a restart. A turn-off SHALL NOT roll back; if its operation record is lost, LMNOPhone SHALL re-apply the full lockdown policy. If the battery is below 5% and not charging at that moment, the commit SHALL wait until the phone is charging or above 5%.

LMNOPhone SHALL restore every change it recorded, and read each one back:
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

Before removing Device Owner, LMNOPhone SHALL confirm that another home app and the system dialer resolve.

LMNOPhone SHALL report "Off" only when Device Owner is gone. If LMNOPhone finds it is no longer Device Owner, it SHALL treat turning off as complete.

If turning off fails, LMNOPhone SHALL report the failure and SHALL NOT fall back to a wipe on its own.

#### Scenario: Turn off
- **WHEN** a trusted person confirms turning off with fresh authority, and 24 hours of proven running time pass without a cancel
- **THEN** the phone works as a normal Android phone, with its home app and dialer working, and photos and messages are kept. Device test A2 already confirmed that releasing the phone does not wipe it.

#### Scenario: Turn off cancelled
- **WHEN** the first trusted person starts turning off, and the second trusted person cancels with her PIN 3 hours later
- **THEN** nothing is restored, the lockdown stays, and both are texted

#### Scenario: Interrupted turn-off
- **WHEN** the phone restarts in the middle of turning off
- **THEN** LMNOPhone resumes at the next step that has not been read back, and does not report "Off" until Device Owner is gone
