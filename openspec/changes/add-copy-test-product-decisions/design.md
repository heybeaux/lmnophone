**Revision 4.** Revision 1 (commit `c6f2289`) was reviewed in PLA-31: request changes, F1–F11. Revision 2 (`b634c67`) was re-reviewed in PLA-34: request changes, N1–N6. Revision 3 (`d7b9b31`) was re-reviewed in PLA-35: **approve with changes**, R1–R4, applied here with its acceptance cases added to the tasks. Response tables are at the end.

## Context

The approved plan (`add-plainphone-spike-and-mvp`, design D2 and D6) has one trusted person per phone. That person holds the PIN and a recovery code kept off the phone. A **supervised session** is the only way the lockdown is relaxed, and it is trusted-person custody. Turning off needs a fresh PIN and two confirmations.

Beaux's answers to his copy-test questions (card `c60170d6`, Telegram 4481) change three things:
- who can relax the lockdown (two trusted people, or the user themselves in a self-managed mode);
- how fast the lockdown can be removed (a 24-hour wait);
- what setup does about Google's data collection.

Inputs reused here:
- the session contract (trusted-person-controls, design D2);
- the PLA-22 boot loop and **mitigation B**: right after unhiding a package, flip one of Plainphone's own components with `PackageManager.SYNCHRONOUS`, which asks PackageManager to write its package settings straight away. It is a best-effort trigger: the caller does not wait for the queued settings write and its sync to finish, so nothing guarantees that the write survives a power cut (`spikes/bootloop-repro/RESULT.md`, Q8; on Android 15, `PackageManagerService` calls the one-argument `Settings.writePackageRestrictions`, which posts the write with `sync=false`; the posted write later syncs through `ResilientAtomicFile`/`FileUtils.sync`; this is build-specific evidence, PLA-241 finding 6). The first spike (historical) booted 3 of 3 times on API 35, including a reboot at 0 s after the combined operation; API 36 was covered later by PLA-25, below. It never covers power loss *between* the unhide and the flip. PLA-25 reviewed it (request changes): 25 of 25 corrected trials booted on API 35 and 36, but Android queues the flush's disk write, so B is best effort, not a guarantee; a user restriction and the lock-task list were also lost after a power cut, so boot-time re-apply and read-back stays the real protection (device-lockdown delta). `SYNCHRONOUS` exists from API 30 ([PackageManager reference](https://developer.android.com/reference/android/content/pm/PackageManager#SYNCHRONOUS); the SDK's `platforms/android-36/data/api-versions.xml` lists it as `since="30"`), which is our `minSdk`, so no supported build lacks it. It requests the settings write, but the caller does not wait for it, and it is not a transaction with the preceding device-policy call;
- the durable count and conservative ageing ideas from the Wi-Fi sign-in recommendation (`docs/wifi-signin-recommendation.md`, section 2, PLA-17), made exact here in C2.

## Goals / Non-Goals

**Goals:**
- Two trusted people who are equals, with clear rules when they disagree.
- A self-managed mode that is honest: friction, not a lock.
- One wait rule, applied the same way to every change that can't be undone afterwards, and safe across crashes.
- Every new relaxation path reuses the session contract. No new way to relax the lockdown without a journal and a deadline.
- Privacy steps in setup, and an accurate note.

**Non-Goals:**
- The web window and Wi-Fi sign-in window (PLA-17).
- A two-person rule (both must agree). Beaux chose "either can approve".
- Any server, remote control or remote unlock. Texts are notices. The only thing a text can do is cancel the pending change (C2), and only if Q15 passes.
- GrapheneOS, LineageOS or any non-stock base.
- Changing the custody limit Beaux accepted for supervised sessions.

**Authority is a credential, not a person.** Plainphone can't tell who is holding the phone. A PIN or recovery code proves that someone knows it, not who they are. Everything below is written with that in mind: the waits and the receipts are there because a PIN can be seen.

## Decisions

### C1. Two trusted people, as equals

**Each trusted person has their own:**
- name and phone number;
- PIN, stored as today (PBKDF2-HMAC-SHA256, at least 600,000 iterations, 16-byte salt);
- PIN lockout counter and delay;
- recovery code (128 bits, hash only) with its own counter;
- reset-protection Google `userId`.

**The PIN screen asks who is entering it** ("Who are you? First trusted person / Second trusted person"), then checks the PIN against that person's PIN only, and counts a failure against that person only. Two people may happen to pick the same PIN; that is allowed, and Plainphone never compares them or says so. Someone holding the phone can still run down both people's lockouts one after the other; one lockout never affects the other person's PIN or either recovery code.

**Powers.** Either trusted person can do every trusted-person action alone. Neither can see, change or reset the other's PIN or recovery code. A recovery code resets only its owner's PIN.

**Texts go to both** (Beaux: "Both get texts"). Every privileged action and every notice is texted to **every** trusted person, including the one who did it. For the person who acted, the text is a receipt; it also tells them if someone else used their PIN. Each text has its own delivery status (waiting, sent, gave up), shown in trusted-person mode. A failed text never changes what is enforced.

**What leaves the phone (N6).** Plainphone has no Plainphone-run server, no analytics or telemetry, and no account. It sends texts to the trusted people through the carrier: requests (with the user's own words), notices and receipts. When the helper updates Plainphone, it connects over local Wi-Fi to the helper's computer to download the update (approved plan, R4). Its history stays on the phone. UX may combine routine receipts into one daily text, as long as waiting-change texts go at once.

**Disagreements.** The two are equals. The rule follows from one question: *can the other person put it back afterwards?*

| Action | Can the other put it back later? | Rule |
|---|---|---|
| Approve or remove an app, change its permissions | Yes, though anything already seen in the app isn't undone | Takes effect at once. Last action stands. Texted to both. In the history list. |
| Open a Play or Settings session | Yes: the protected-state check restores protections | At once, as today. Texted to both. |
| Install a Plainphone update | No: there is no rollback (approved plan, R4) | At once, as today. Texted to both. |
| Turn Plainphone off | No | Waiting change (C2). |
| Add a trusted person, hand your role over, remove the other person | No | Waiting change (C2). |
| Switch mode, either direction | No | Waiting change (C2). |

**What the waits protect against.** Someone who has seen a PIN, for example a child. They don't protect one trusted person from the other: a trusted person who uses their recovery code skips the wait. Plainphone says this plainly to both people at setup: "You are equals. Either of you can approve apps, or turn Plainphone off."

**History list.** Trusted-person mode keeps a 90-day list of privileged actions and waiting changes: who (which credential), what, when, and text status. No app content, URLs or message text. It is kept in device-protected storage, so it survives reboots. It is a record, not a control.

### C2. Waiting changes: one operation record, one at a time, re-checked, recoverable

**Which actions are waiting changes:**
1. turning Plainphone off;
2. changing who is a trusted person (add, hand over, remove);
3. switching mode, in either direction (C5);
4. in self-managed mode, loosening the friction (a shorter countdown, more elevations a day).

**Two stores (N1).** Revision 3 keeps the whole mechanism in two kinds of record, both in device-protected storage:
- the **credential store**: the mode, each PIN holder's slot (name, number, PIN hash, code hash, lockout counters, `userId`, credential generation), the backup person's `userId` if any, the current self-managed friction settings, and the structure generation. It is always written as one atomic record, and it is the durable source for who is in charge (R1).
- the **operation record**: one per waiting change. It holds everything the change needs (below) and its **state**. It is written as one atomic record with a **revision number** that goes up on every write, and kept in **two copies**. Plainphone reads both and uses the valid copy with the highest revision. A checksum shows a copy is intact; the revision shows which is newest.

**The operation record holds:** the request id; the kind; the immutable inputs (for example the incoming person's PIN hash, code hash, number and `userId`, and the exact credential patch, see C3); the **dependencies** (the slot ids it touches, each with its credential generation, and the structure generation); which credential started it; a **cancel code** for text replies (C2, "Cancelling"), unique to this request and never reused on this phone; the clock reading and clock epoch (below); the state; and the text queue obligations for the current state, with recipients fixed when they were added.

**States.**

| State | Meaning | Cancel |
|---|---|---|
| pending | waiting for 24 hours of proven running time | anyone (PIN-free on the phone; reply with the code if Q15 passed) |
| committing | going forward, step by step | not offered ("Already in progress"), except the stuck-change rollback in C3 |
| rolling back | undoing the steps already done | not offered |
| completed | terminal | – |
| cancelled | terminal | – |

**One at a time (F2, N1).** One operation holds the **slot** from the moment it is created until it reaches a terminal state, whether pending, committing or rolling back. No other waiting change can start meanwhile; trying shows the one in progress. So a stuck or retrying operation can never interleave with another.

**Every state change is one write.** Moving to a new state, and queuing the texts that go with it (start, cancel, skip, completion), happen in the same atomic write of the operation record. Only after that write succeeds does Plainphone show the new state. On every start, Plainphone sends any queued text not yet marked sent. Delivery is never a condition for anything.

**Every step is idempotent and checked.** A committing or rolling-back operation runs its steps in order. Each step can be repeated safely and is read back. Every retry, timer and policy pass first re-reads the operation record and acts only if the request id and the direction (forward or back) are still the ones it was scheduled for. A rollback never turns back into a forward run because of a restart: the direction is part of the durable state.

**Generations (F2, N2).** Each slot's credential generation goes up whenever that person's PIN or recovery code is replaced. The structure generation goes up whenever the set of people or the mode changes. An operation depends only on the slots it touches: the starter's slot, and any slot it removes or replaces. Another person's PIN change doesn't affect it, and the patch in C3 never rewrites that person's slot.
- **Before commit**, if any dependency changes, the operation is **cancelled at once** (not only at maturity), and everyone is texted. So replacing a PIN someone saw cancels anything they started with it.
- **After commit**, the change is past the point of return. Replacing a PIN then doesn't stop it, and the change's patch preserves the newest credentials of every slot it doesn't remove. The screens and texts say so: "Already in progress."

**Starting.** A fresh PIN (or, in self-managed mode, the user's PIN), plus the confirmations each action already needs. Plainphone then creates the operation record in state pending, with the start texts queued, in one write. If the write fails, the change doesn't start.

**Counting time (F7, N3).** All waits and daily counts use one clock: a **proven running time** counter, P.
- P is stored as one atomic record: the **epoch**, the total proven so far in this epoch, the boot id, and the `SystemClock.elapsedRealtime()` reading at the last checkpoint ([SystemClock](https://developer.android.com/reference/android/os/SystemClock); monotonic within a boot, counts deep sleep, ignores wall-clock changes).
- **Checkpoint, same boot:** add the elapsed time since the last checkpoint, store the new reading. One atomic write. Repeated process starts never count the same interval twice.
- **Checkpoint, new boot:** the time between the last checkpoint and the reboot is not counted. The first checkpoint adds the time since this boot started, and stores the new boot id.
- Checkpoints happen at least every 5 minutes, and at each state change and debit.
- Every record that stores a P reading also stores the epoch it was taken in. **A reading from any other epoch counts as taken at the very start of the current epoch.** So mixed records can never mature early.
- **Damaged, missing or inconsistent P** (bad checksum, unreadable boot id, or `elapsedRealtime()` lower than the stored reading in the same boot): Plainphone writes a new epoch with total 0, anchored at the current boot id and the **current** `elapsedRealtime()`, not at the start of the boot. Every pending wait and every debit then counts from that moment. Nothing matures early and nothing is refunded. If that write fails, no wait matures and no elevation starts until it succeeds, and "Plainphone needs attention" shows.
- A wait matures when P has grown by at least 24 hours since its reading. A debit expires the same way.
- Countdowns, Start windows and grant deadlines use `elapsedRealtime()` within one boot; a reboot ends them. A clock repair doesn't touch them.
- Wall-clock time is for display only. Screens say: "Time while the phone is off doesn't count."

**Texts at the start**, draft wording, finalised by UX:
> Plainphone: {name} asked to turn Plainphone off on {user}'s phone. It will turn off after at least 24 hours of the phone being on. To stop it, open the phone and tap Cancel{, or reply CANCEL {code}}.

The reply part appears only if Q15 passes.

**Cancelling a pending change (F3, F8, N5).**
- Either trusted person, on the phone, with their PIN.
- Anyone holding the phone, without a PIN (cancelling only keeps the phone as it is).
- A reply of `CANCEL` followed by that request's **cancel code**, from a trusted person's number on record, **only if Q15 passes**. A reply whose code doesn't match the pending request, or matches a request that is no longer pending, does nothing and is logged. A reply can't start, skip, approve or shorten anything.
- The cancel is written as the cancelled state, with its texts queued, before Plainphone says "Cancelled". If that write fails, Plainphone doesn't say "Cancelled", retries, and doesn't commit while the cancel is unresolved.

**Where Cancel is always reachable (F8).** The home-screen notice line (C8), the "Plainphone needs attention" screen, and Plainphone's own settings screen.

**Denial of progress, said openly (N5).** Anyone holding the phone can cancel, and someone who knows a PIN can keep starting decoy changes. Neither gains anything, but either can delay a legitimate change. In supervised mode a trusted person can use their recovery code to skip; in self-managed mode there is no escape from someone who keeps the phone. This is on "What Plainphone can't stop".

**Skipping (F2, N2).** In supervised mode, a trusted person's recovery code skips the wait. The skip screen names the exact pending change ("Skip the wait and turn Plainphone off now?"). The person enters their code, confirms, and sets a new PIN. Then **one atomic write** of the credential store and the operation record does all of this together: replaces that person's PIN and code, raises their generation, records the skipper, and moves the operation to committing. The skipper's authority replaces the starter's, so the starter's generation is not re-checked; the other dependencies are. Entering a recovery code only to reset a forgotten PIN never skips anything (and, being a credential replacement, it cancels anything that PIN started). In self-managed mode, nothing in Plainphone skips a wait.

**Commit.** When the wait matures, Plainphone re-checks the dependencies and the mode against the current credential store. If they hold, one write moves the operation to committing, with the forward steps listed. Otherwise it is cancelled.

**Lost or damaged operation record (N1, R1, R2).** If no valid copy of the operation record can be read (both damaged, both missing, or unreadable), Plainphone can't know what kind of change it was. So it **reconciles the same way whatever the kind**, from surviving state only:
- **If Plainphone is no longer Device Owner,** turning off had already finished its last step. The existing rule applies: Plainphone reports "Off". Nothing can undo that.
- **Otherwise,** with the credential store as the truth:
  - re-apply the complete active policy (the normal policy pass), so a half-done turn-off ends locked down, not half open;
  - set the reset-protection list to every PIN holder's `userId` in the credential store plus the backup person's, send the broadcast, and read it back;
  - use the friction settings stored in the credential store. Pending credentials and pending friction values are never adopted.
- That either finishes a change of people or mode whose credential patch was already applied, or undoes one whose patch wasn't. Both end consistent.
- **The notice reports what was recovered, not a guess about the change.** Draft: "Plainphone recovered from an interrupted change. Trusted people now: {names}. Mode: {mode}. Plainphone is on and locked down. If you were changing something, check whether it happened before you start it again." It never says the change was cancelled or didn't finish, because it can't know.
- Then it frees the slot and logs it in the history.
- **If the credential store itself is invalid,** or reconciling can't read back, the slot stays held, "Plainphone needs attention" shows, and nothing is guessed. A current PIN holder can run **Repair** with their PIN: it retries exactly this reconcile and shows what failed. A PIN used for Repair authorises only that; it never skips a wait, starts a change or adopts pending values. Calls, Emergency and alerts are never gated on any of this.

**When the wait ends.** The change goes ahead by itself after the re-check, then texts everyone. It doesn't wait for anyone to come back and confirm.

**During the wait** the phone stays fully locked down, and every other action works as normal.

### C3. Changing who the trusted people are

All three changes need a current trusted person's PIN. **The user alone can never change who the trusted people are.**

- **Add a second trusted person:** a current trusted person's PIN. Then the new person, in person, enters their name and number, sets their PIN, receives their recovery code (shown once), enters their reset-protection `userId` using the guide's People API steps on their own device, and confirms a test text. The helper or the current trusted person confirms the new person's account can be signed in to from another device, its second factor isn't only on a Plainphone phone, and it is at least 72 hours old.
- **Hand your role over:** the outgoing person's own PIN or recovery code, then the same steps for the incoming person.
- **Remove the other person:** a current trusted person's PIN. Plainphone never removes the last trusted person; that would be a switch to self-managed (C5) or turning off (C6).

**Why these wait 24 hours.** A child who saw a PIN could otherwise add themselves, with their own PIN, and keep control even after the PIN is changed. With the wait, every trusted person gets a text and a day to cancel, and replacing the seen PIN cancels the request (C2).

**The patch (N2).** The operation record holds a **patch**, not a whole new credential set: "add slot Y", "remove slot X", "replace slot X with slot Y", or "set mode to M, remove slots X…, add slot Y". Applying it touches only those slots. Every other slot keeps whatever it holds at that moment, including a PIN changed during the wait.

**Completing (F4, N1).** Forward steps, each idempotent and read back:
1. set the reset-protection list to the IDs the credential store will hold after the patch, plus the backup person's, send `FRP_CONFIG_CHANGED` to Play services, read the list back;
2. only when step 1 reads back correctly: apply the patch to the credential store in one atomic write, raising the structure generation;
3. move to completed, with the completion texts queued, in one write.

**If step 1 won't read back,** the operation stays committing and retries on every policy pass. The old people stay in charge with their current credentials. Trusted-person mode shows "Reset protection not updated – call the helper". Nothing reports the change as done.

**Stuck-change rollback.** While step 1 is failing (step 2 not yet done), any current trusted person can choose "Stop this change" with their PIN, from the needs-attention screen. Plainphone:
1. writes the rolling-back state first (so a restart continues backwards, never forwards);
2. sets the reset-protection list back to the IDs of the current credential store, and reads it back;
3. moves to cancelled, with texts queued.

If the rollback's readback also fails, the operation stays rolling back, retries, and shows "needs attention"; the people in charge don't change. Once step 2 has happened, "Stop this change" is no longer offered: the change only goes forward.

**While reset protection is uncertain** (a write that hasn't read back), Plainphone doesn't claim which list a wipe would enforce. Trusted-person mode says: "Until this is fixed, we can't say whose Google account a wipe would ask for." Q10 tests the failure cases on the target phone.

After a restart at any point, either the old people or the new people are in charge, never neither, and their reset-protection list reads back once the operation is terminal.

### C4. Self-managed mode: ADR option D as a mode, friction not a lock

**What it is.** The same Plainphone app, Device Owner, lockdown, emergency floor and restrictions as supervised mode. The only difference is who can relax it: the user holds the PIN, and every loosening goes through friction. ADR 0001 rejected option D (launcher only) because it is "friction, not a lock", and said it wins "only if the two initial users decide they want voluntary friction instead". Beaux has now asked for exactly that, as a choice per phone. Self-managed mode keeps option A's controls (ADR "Controls the design relies on": restrictions such as `DISALLOW_SAFE_BOOT` and `DISALLOW_DEBUGGING_FEATURES`, the persistent Home pin, lock task), so the friction is meant to resist changing the home app, safe mode or adb, which a launcher alone can't. These are intended controls, verified per build: for example `no_config_default_apps` did not take effect on the spare Motorola, where the Home pin did the work (PLA-5 A4/A6).

**Said openly.** At setup and on "What Plainphone can't stop": "In self-managed mode, you hold the PIN. Plainphone adds waits and limits so that distraction takes effort. It can't stop you, because you are in charge. You can always remove it by waiting at least 24 hours of the phone being on, or by wiping the phone."

**Stricter at once, looser with friction.**

| Action in self-managed mode | Rule |
|---|---|
| Remove an app, lengthen the wait, lower the daily limit | PIN, at once |
| Open a Play or Settings session (to add an app, change a setting, run the monthly check) | PIN, then an **elevation** |
| Approve an app that is already installed but hidden | PIN, then an elevation |
| Install a Plainphone update | PIN, then an elevation |
| Shorten the wait, raise the daily limit | Waiting change |
| Turn off | Waiting change |
| Switch to supervised | Waiting change (C5) |

**Elevation (F6, N4).** An elevation is a **grant**: a bounded piece of authority with an id, one kind, a deadline and a binding. A grant is not the same as the **package access** it may open. Play and Settings grants open a supervised session (package access to Play or Settings) under the session contract. The grant itself lives in Plainphone and can outlast that package access, but never its own deadline.

| Kind | Package access | What the grant allows | Bound to | Deadline |
|---|---|---|---|---|
| Play | A Play session (`com.android.vending`) | installing in Play; then, back in Plainphone, approving **each** app installed during that session, until the deadline (R3) | each approval to the installed package and its signer, as in the base rules | 10 minutes |
| Settings | A Settings session | changing settings | – | 5 minutes |
| Approve | None | approving **one** already-installed hidden app | the package and signer the user picks; used up by that approval | 5 minutes |
| Update | None | **one** Plainphone update, through the approved plan's updater unchanged | the verified staged bytes and version; used up by that install | 10 minutes |

Steps:
1. **PIN, then the countdown.** The user enters the PIN and picks the kind. That PIN entry is the fresh authority for the grant. A countdown runs (default **10 minutes**). The phone works normally meanwhile. The user can cancel at any time.
2. **Start.** When the countdown ends, the notice line says "Ready – tap to start". The user taps Start within 5 minutes and confirms the kind again. Otherwise it lapses, with no turn used.
3. **Debit.** On Start, Plainphone writes the debit and the grant (with its session journal entry, for Play and Settings) in one atomic write, before anything is relaxed. Starts are serialized, so a double tap can't get two.
4. **Package access ends** on the session contract's triggers: Done, screen off, an incoming call, leaving the session's packages, or the deadline. Ending package access runs the protected-state check as usual. For a Play grant, returning to Plainphone to approve what was installed ends Play access but keeps the grant until its deadline.
5. **The grant ends** at its deadline, on Done, or on any Plainphone restart. A restart never extends or restores it.
6. **Last check before the irreversible step.** Plainphone checks the grant (still live, right kind, not used up, binding matches) immediately before the step that can't be undone: the approval write, or the PackageInstaller commit for an update. The check and its effect happen together: an Approve or Update grant is used up by its one action; a Play grant records that package's approval and stays live for the other apps installed in that session until its deadline. If the check fails, nothing happens. An install committed before the deadline is allowed to finish after it, with no rollback promised.
7. **Updates.** The base updater's first step closes any open supervised session and reads the policy back. That closes package access, not the Update grant, which has no package access of its own.

**One at a time.** At most one countdown or grant exists. A second request shows the one in progress.

**Daily limit.** At most **2** debits in any 24 hours of proven running time (default). Debits age on C2's clock. A debit is never cleared by a crash, a reboot or a failed write; a damaged debit list is replaced by a fresh one counting as **no turns left**, written durably, ageing from now. If that write fails, no elevation starts.

**Never permanent, with one stated exception.** No setting removes the wait or the limit, or lengthens a deadline. The wait can be set from 10 minutes to 24 hours, and the limit from 0 to 3 a day. A stricter setting applies at once, including to a countdown already running (it lengthens). A looser setting is a waiting change. *Exception:* if Plainphone can't run while a grant is open, nothing revokes it automatically (the custody limit), and here nobody is watching. That is disclosed.

Changes made *during* an elevation persist, as they would for a trusted person: an approved app stays approved. The same web-containment rules apply; general browsers stay hidden whatever the mode.

**App requests.** In self-managed mode there is no one to ask. "Ask for an app" becomes "Add an app", which starts a Play elevation.

**Monthly check.** It costs one Play elevation. The home-screen line asks the user.

**Custody wording.** "Stay with the phone until you tap Done" becomes "Plainphone closes this at {time}".

**Reset protection (F10).** The list names the **user's own** Google `userId`, plus an optional backup person. It is never left empty. Setup checks the same things for the user's account as for a trusted person's: it can be signed in to from another device, and its second factor isn't only on this phone. After a recovery-mode wipe, setup asks for a listed account, so the user can set the phone up again without Plainphone (subject to Q10). A stranger without a listed account's password can't. The disclosure says this: a wipe erases the phone, but it is a way out that doesn't wait 24 hours.

The user's own account is signed in on the phone, as normal. In supervised mode, no trusted person's or backup person's account is signed in on the phone; in self-managed mode, no backup person's account is (device-lockdown delta).

**Recovery code.** The user gets one, for a forgotten PIN. It resets the PIN only. It does **not** skip a countdown, the daily limit or any waiting change, because the user holds it. *Beaux's rule 3 ("the recovery code skips the wait") was about a code a trusted person keeps off the phone. This change asks Beaux to confirm the narrower rule for self-managed mode.*

### C5. Switching modes (F1)

**Both directions are waiting changes.** Revision 1 made self-managed → supervised instant, as "stricter". PLA-31 F1 showed that a self-managed user can enrol a second identity they control as the "trusted person" and get its PIN and recovery code at once, skipping every wait and limit. Plainphone can't tell whether the incoming trusted person is someone else, so the switch can't be treated as stricter.

- **Supervised → self-managed:** started with a trusted person's PIN. The user sets their PIN, receives their recovery code and enters their own `userId` (with the account checks in C4) before the wait starts. Either trusted person can cancel. A trusted person's recovery code skips it.
- **Self-managed → supervised:** started with the user's PIN. The incoming trusted person sets up as in C3. The wait is 24 hours. Nothing skips it. The user can cancel. **During the wait, self-managed rules stay in force**, the pending credentials grant nothing, and existing debits and countdowns carry on.
- Completion follows C3's order: new reset-protection list read back first, then the credential swap.
- A user on a supervised phone has no PIN, so they can't start either switch.

The honest summary for self-managed users: whatever route they take (turn off, or switch to a trusted person they control), getting full control back takes at least 24 hours of proven running time (longer if it is cancelled, a PIN is replaced, the battery is nearly flat, or a fault interrupts it), or a wipe.

### C6. Turning off waits 24 hours

The existing off switch keeps everything it has (fresh authority, two plain confirmations, the full restore list read back, the successor home app and dialer check, journaled and resumable, never a silent wipe) and gains the waiting-change rules from C2 in front of it. Its forward steps (in the operation record's committing state) are the existing turn-off steps, each read back. A turn-off is never rolled back; if its record is lost, the normal policy pass re-applies the lockdown (C2).

- **Supervised:** a trusted person's PIN starts the wait; anyone can cancel; a trusted person's recovery code skips it.
- **Self-managed:** the user's PIN starts the wait; the user can cancel; nothing skips it.
- If the battery is below 5% and the phone isn't charging when the wait matures, the commit waits until it is charging or above 5%, because turning off unhides many apps (C7).
- A flat battery doesn't turn Plainphone off: Android keeps the restrictions and hidden apps across a restart (PLA-5 A6). The copy keeps that fact.

### C7. Mitigation B at every unhide (F5)

**Where.** Every place Plainphone unhides a package applies mitigation B straight after the unhide:
- session and elevation starts;
- approving an app (including one installed during a Play session);
- the protected-state check and the never-hide guard;
- the emergency floor, including on unknown builds and the locked-boot path;
- turning off.

**How.**
- The flip uses one of Plainphone's own components, changing its enabled state for real (a no-op that changes nothing would not force a save), with `DONT_KILL_APP | SYNCHRONOUS`.
- Plainphone checks that the call returned and that the component state reads back as changed, then puts it back the same way.
- If the flip fails, Plainphone retries once, logs it, and shows "Plainphone needs attention". The unhide itself is not undone, because it may be an emergency repair.

**Battery guard.** Discretionary unhides (sessions, elevations, approvals, turning off) don't start below **5% battery unless charging**; Plainphone says "Charge the phone first." Emergency repairs (the floor, the never-hide guard, the protected-state check) are **never** delayed.

**What this does and doesn't promise.**
- After the flip completes, the hidden state is saved; a power loss after that point is expected to boot normally. That is shown on API 35 (3 of 3). Task 1.3 repeats it on API 35 and 36, on every call site, with at least 10 trials each.
- A power loss in the short gap **between** the unhide and the flip can still cause the PLA-22 boot loop, which needs a recovery wipe. Mitigation B narrows the window; it doesn't close it. Task 1.3 measures that gap with an injected pause, and the disclosure says it plainly.
- The battery guard reduces how often a discretionary unhide happens near a flat battery. It doesn't prevent a hard reset.
- Self-managed elevations are started by the user, alone, possibly often, so they meet this race more often than supervised sessions. The daily limit caps it at 2 starts a day by default.

### C8. One home-screen line at a time

The home screen shows at most **one** notice line. When several apply, the most urgent wins:
1. "Plainphone needs attention" (device-lockdown);
2. the pending waiting change: "Plainphone will turn off after {time}. Tap to cancel.", "A change to trusted people is waiting. Tap to cancel." or "A change to how this phone is set up is waiting. Tap to cancel." (a mode switch, or a self-managed shorter countdown or higher daily limit);
3. "Opening up in {n} minutes" / "Ready – tap to start" (C4);
4. the monthly-check line (approved-apps); in self-managed mode, "Time for your monthly phone check".

When line 1 hides line 2, the pending change and its Cancel are on the "needs attention" screen and on Plainphone's settings screen (C2). As today, no line pushes Emergency, Phone or Messages off-screen at the largest font size.

### C9. Privacy steps in setup

During on-phone setup, straight after the user signs in to Google, the guide and the setup screen walk the helper and the user through four steps:
1. pause **Web & App Activity** in the Google account;
2. turn **Timeline** (location history) off;
3. turn **ad personalisation** off (My Ad Center);
4. delete the phone's **advertising ID**.

- This happens before lock task starts, so the system and Google account screens are reachable.
- The exact screen paths depend on the build and on Google's account pages. The guide records them for each supported build, and the setup dry run (task 5.3 of the approved plan) confirms them. They are not promised here.
- Plainphone can't read Google account settings. Each step is recorded as **done** or **skipped** by the helper. The handover check shows the record and doesn't gate "Ready" on it.
- "What Plainphone can't stop" gains (F9, N6): "Plainphone has no server of its own, no analytics and no account. It sends texts to your trusted people through your carrier, connects to your helper's computer over home Wi-Fi only to update itself, and keeps its history on the phone. But this phone runs Google's Android with a Google account, and Google collects some data that Plainphone can't stop. The privacy steps from setup reduce it."

### Who can relax the lockdown (summary for ADR 0001)

| Way | Supervised mode | Self-managed mode |
|---|---|---|
| Play or Settings session | Either trusted person's PIN; custody | The user's PIN, a 10-minute countdown, then a Play or Settings grant that opens the session; at most 2 grants a day; no custody |
| Approve an app | Either trusted person's PIN | The user's PIN, then a Play grant (apps installed in it) or an Approve grant (one hidden app) |
| Install a Plainphone update | Either trusted person's PIN | The user's PIN, then an Update grant |
| Turn off | A trusted person's PIN, then 24 h (anyone can cancel); their recovery code skips | The user's PIN, then 24 h; nothing skips |
| Change trusted people | A trusted person's PIN, then 24 h; their recovery code skips | Not applicable |
| Switch mode | A trusted person's PIN, then 24 h; their recovery code skips | The user's PIN, then 24 h; nothing skips |
| Web and Wi-Fi sign-in windows | PLA-17 (separate change) | PLA-17 (separate change) |
| Recovery-mode wipe | Reset protection asks for a trusted (or backup) person's account | Reset protection asks for the user's own (or backup) account |
| Plainphone can't run during a session | Relaxation stays until a helper fixes it (accepted custody limit) | The same, with nobody watching |

## Risks / Trade-offs

- **[A trusted person can always override the other]** → by design (Beaux: "either can approve"). Receipts to both, the history list and the waits make it visible. Said plainly at setup.
- **[A PIN proves knowledge, not identity]** → the waits, receipts and generation checks exist because of this. A self-managed user can enrol a second identity as "trusted person"; that is now a 24-hour waiting change like everything else.
- **[Self-managed mode doesn't stop a determined user]** → by design, and said openly. Option A's controls (restrictions, Home pin, lock task) are meant to keep the friction from being skipped by safe mode, adb or a launcher change; they are verified per build, and safe mode is not yet tested on the spare (PLA-5 A4). A wipe is always a way out.
- **[More texts]** → receipts to both people for every action. UX may batch routine receipts daily; waiting-change texts go at once.
- **[One change in progress at a time; denial of progress]** → someone with a PIN can keep starting decoy changes, and anyone holding the phone can keep cancelling. Neither gains anything, but either can delay a legitimate change. A supervised trusted person can skip with their recovery code; a self-managed user has no escape from someone who keeps their phone. Disclosed.
- **[Cancel by text can be forged]** → it can only cancel, and only the request whose cancel code it quotes. A trusted person whose turn-off is blocked by forged cancels can use their recovery code to skip.
- **[Time while the phone is off doesn't count]** → a wait can last longer than 24 hours. Disclosed. Never shorter.
- **[A stuck handover]** → if reset protection can't be updated, the change stays in progress with the old people in charge, until it works or a trusted person stops it (rollback, C3). While the write is uncertain, Plainphone doesn't claim which account a wipe would ask for.
- **[Recovery when Plainphone can't run covers app failures only]** → calls from the lock screen and system dialer assume Android itself is running. The PLA-22 system boot loop is different: there is no working dialer until a recovery wipe.
- **[Unhide-to-flush gap]** → a power loss between an unhide and the mitigation B flip can still boot-loop the phone. Narrowed, measured in task 1.3, disclosed, and on the target-phone checklist (approved plan task 6.3).
- **[Reset protection with two or three IDs is untested]** → Q10 grows to cover it, including changed lists and failed updates. Google documents configurable account lists, changing them over time, and the broadcast ([FRP guidance](https://developer.android.com/work/dpc/security#factory_reset_protection)); whether this build enforces three IDs is a device test.
- **[`RECEIVE_SMS` for cancel replies]** → non-default SMS apps can receive `SMS_RECEIVED_ACTION` ([Telephony reference](https://developer.android.com/reference/android/provider/Telephony)), but `RECEIVE_SMS` is hard-restricted and needs installer allowlisting as well as a runtime grant ([permission reference](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)). Not tested. Q15 decides; without it, cancel is on the phone only, and no copy mentions replies.

## Migration Plan

- The approved change archives first; this change's deltas then apply to the archived specs.
- No phones are in use, so there is no data to migrate. The MVP builds the two-person model from the start.
- Tasks in `tasks.md` are added to the MVP build ([PLA-15](/PLA/issues/PLA-15)) once Beaux approves.

## Open Questions (for Beaux, with the Lead's default)

These are the Lead's additions, beyond the literal answers on card `c60170d6`. Beaux approves or changes them with this change. **He accepted all eight defaults** when he approved the change (2026-10-08 21:57 PDT, card `a9a11371`).
1. **Waits on changing trusted people and on switching mode (both directions).** Default: yes, 24 hours, same as turning off.
2. **Recovery code in self-managed mode.** Default: resets the PIN only, skips nothing.
3. **Friction numbers.** Default: 10-minute countdown, 5-minute Start window, 2 elevations a day (settable 0–3), stricter at once.
4. **Receipts.** Default: every action is texted to both people, including the one who did it.
5. **Anyone holding the phone can cancel a pending change.** Default: yes.
6. **Cancel by text reply.** Default: yes, if Q15 passes. Otherwise on the phone only.
7. **A notice-only contact in self-managed mode** (someone who gets the texts but has no PIN). Default: not now.
8. **History list kept 90 days on the phone.** Default: yes.

## Review response (PLA-31, revision 1 → 2)

*Superseded in part by revision 3: the "wait record" and "operation journal" became one operation record, and CANCEL replies now carry a per-request code. See the PLA-34 table below.*

| Finding | Where addressed |
|---|---|
| F1 instant switch to supervised skips friction | C5: both directions are waiting changes; self-managed rules stay in force during the wait; nothing skips it. TPC "Switching between modes". Tasks 2.7, 4.1 |
| F2 stale and concurrent authority | C2: one pending change at a time; request id; credential and structure generations re-checked at commit; recovery-code skip names the change; PIN reset never skips. TPC "Waiting changes". Tasks 1.2, 2.3 |
| F3 crash-safe notice and cancel | C2: wait record and text queue in one write; resend on start; durable cancelled state checked by callbacks; commit point and a two-copy operation journal; missing, damaged and failed cases separated. Tasks 1.2, 2.3–2.5 |
| F4 failed reset-protection update | C3: new list read back before the credential swap; otherwise the change stays in progress with the old people in charge; no automatic exception; helper cancel restores the old list. Device-lockdown delta. Tasks 1.4, 2.4 |
| F5 mitigation B overclaim and coverage | C7: every unhide call site; real component flip with readback; failure handling; battery guard for discretionary unhides only; the unhide-to-flip gap disclosed; scenario limited to after the flip. Task 1.3 |
| F6 elevation lifecycle | C4: single-use grant with id, kind and deadline; kinds Play, Settings, Approve, Update; one at a time; consumed on deadline, Done or restart; updater unchanged. Tasks 2.6, 4.1 |
| F7 clock recurrence | C2: one proven-running-time counter with an atomic checkpoint tuple; each interval counted once; uncertain gaps dropped; damaged counter re-stamps everything at zero. Task 1.2 |
| F8 cancel reachability | C2, C8: Cancel also on the needs-attention screen and Plainphone's settings; one pending change makes CANCEL unambiguous; late and duplicate replies do nothing. Calm-home-screen delta |
| F9 "sends nothing" | C1, C9: no server, analytics or account; texts to trusted people through the carrier; local history. TPC disclosure |
| F10 self-managed recovery | C4: account checks apply to the user's account; signed-in rule scoped; recovery requirement updated for both modes; bounded claim subject to Q10. Guided-setup, device-lockdown and TPC deltas |
| F11 equal PINs | C1: equal PINs allowed and never compared; scenario uses distinct PINs; one counter never affects another. TPC scenario, task 2.1 |
| Fidelity: "both get texts" | C1: texts go to both, including the actor (receipts) |
| Fidelity: reversible actions | C1 table: viewed content isn't undone; updates have no rollback |
| Fidelity: added rules | Open questions 1–8 list them for Beaux |
| ADR "never permanent" | ADR note now states the custody-limit exception |

## Review response (PLA-34, revision 2 → 3)

| Finding | Where addressed |
|---|---|
| N1 commit journal and rollback | C2: one operation record per change, revision-numbered, two copies, highest valid revision wins; states pending, committing, rolling back, completed, cancelled; the slot is held until a terminal state; every state change and its texts in one write; idempotent steps that re-check request id and direction; lost record reconciles to the credential store, with a PIN-authorised Repair if that fails. C3: stuck-change rollback writes its direction first. TPC "Waiting changes", "Changing who the trusted people are". Tasks 1.2, 2.3, 2.4 |
| N2 whole-set swap | C2, C3: an operation-specific patch touches only its own slots; dependencies are exactly those slots plus the structure generation; dependency changes cancel at once before commit; after commit, the patch keeps newer credentials; recovery-code skip is one atomic write that records the skipper. Tasks 1.2, 2.3 |
| N3 clock repair | C2: clock epochs; a reading from another epoch counts as taken at the start of the current one; repair anchored at the current `elapsedRealtime()`; inconsistent readings treated as damaged; grants and countdowns untouched. TPC clock requirement. Task 1.2 |
| N4 grant versus session | C4: grant separate from package access; per-kind bindings; returning from Play keeps the grant until its deadline; the updater closes package access, not the grant; authority checked and used up together just before the irreversible step. TPC elevation requirement. Task 2.6 |
| N5 delayed CANCEL | C2: a per-request cancel code in the text; replies that don't match the pending request do nothing; denial of progress disclosed. Tasks 1.1, 4.1 |
| N6 updater traffic | C1, C9: no Plainphone-run server, analytics or account; texts through the carrier; local Wi-Fi to the helper's computer for updates. TPC |
| Consistency: debits during a mode-switch wait | TPC scenario now says no elevation **through the pending credentials**; ordinary quota ageing continues |
| Consistency: self-managed PIN recovery during a wait | Cancels the pending change at once (C2 generations); TPC scenario updated |
| Consistency: who stops a stuck handover | Any current trusted person, with their PIN, from the needs-attention screen (C3); never the PIN-free Cancel |
| Consistency: signed-in rule | C4 uses the device-lockdown wording |
| Consistency: "always remove by waiting 24 hours" | "at least 24 hours of proven running time", with what can lengthen it, in C4, C5, the proposal, setup, the disclosure and the ADR note |
| Consistency: elevation is not always a session | C4 and the ADR note: Play and Settings grants open a session; Approve and Update don't. Irreversibility headline names the signed-update exception |
| Evidence: Device Owner containment | C4 cites the ADR controls table, marks them as intended and verified per build, and notes `no_config_default_apps` didn't take effect on the spare (PLA-5 A4/A6) |
| Evidence: recovery scenario | Scoped to app-only failures; the PLA-22 system boot loop is excluded (TPC recovery requirement, risks) |

## Review response (PLA-35, revision 3 → 4)

PLA-35 verdict: **approve with changes**. No new high finding.

| Finding | Where addressed |
|---|---|
| R1 lost-record recovery target | C2: one reconcile whatever the kind; "Off" if Device Owner is already gone; otherwise the full policy pass, the full reset-protection list including the backup, and the stored friction settings; the credential store now holds the backup `userId` and friction settings; invalid store means hold and report, never guess; Repair authorises only the reconcile. C3 step 1 includes the backup. TPC "Waiting changes", device-lockdown |
| R2 truthful recovery notice | C2: the notice reports the people, mode and state recovered and never claims the change didn't finish. TPC; task 3.1 |
| R3 Play approvals | C4, TPC: a Play grant approves each app installed in its session until the deadline; Approve and Update are used up by their one action. Task 2.6 adds the two-app Play case |
| R4 copy | TPC disclosure names the update connection; the C4 risk line qualified; the proposal names the signed-update exception; "pending only" next to Cancel; the session-contract headline and ADR say package access follows the session contract while approval, update and turn-off follow their own rules; turn-off scenario says proven running time |
| Implementation attacks | Added to tasks 1.2, 2.3, 2.6 and 4.1 as named acceptance cases: stale sole replica, replay after patch, skip races, Repair, epochs, the irreversible-step pauses, cancel-code reuse |

