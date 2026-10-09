**Revision 2.** Revision 1 (commit `c6f2289`) was reviewed in PLA-31: request changes, F1–F11 (5 high, 5 medium, 1 low). The response table is at the end.

## Context

The approved plan (`add-plainphone-spike-and-mvp`, design D2 and D6) has one trusted person per phone. That person holds the PIN and a recovery code kept off the phone. A **supervised session** is the only way the lockdown is relaxed, and it is trusted-person custody. Turning off needs a fresh PIN and two confirmations.

Beaux's answers to his copy-test questions (card `c60170d6`, Telegram 4481) change three things:
- who can relax the lockdown (two trusted people, or the user themselves in a self-managed mode);
- how fast the lockdown can be removed (a 24-hour wait);
- what setup does about Google's data collection.

Inputs reused here:
- the session contract (trusted-person-controls, design D2);
- the PLA-22 boot loop and **mitigation B**: right after unhiding a package, flip one of Plainphone's own components with `PackageManager.SYNCHRONOUS`, which makes PackageManager save its package settings at once (`spikes/bootloop-repro/RESULT.md`, Q8). Evidence so far: 3 of 3 boots on API 35, including a reboot at 0 s after the combined operation. It has not been tested on API 36, and it doesn't cover power loss *between* the unhide and the flip. PLA-25 is reviewing it. `SYNCHRONOUS` exists from API 30 ([PackageManager reference](https://developer.android.com/reference/android/content/pm/PackageManager#SYNCHRONOUS)); it serialises package settings at once, and is not a transaction with the preceding device-policy call;
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

**The PIN screen asks who is entering it** ("Who are you? Beaux / the trusted person"), then checks the PIN against that person's PIN only, and counts a failure against that person only. Two people may happen to pick the same PIN; that is allowed, and Plainphone never compares them or says so. Someone holding the phone can still run down both people's lockouts one after the other; one lockout never affects the other person's PIN or either recovery code.

**Powers.** Either trusted person can do every trusted-person action alone. Neither can see, change or reset the other's PIN or recovery code. A recovery code resets only its owner's PIN.

**Texts go to both** (Beaux: "Both get texts"). Every privileged action and every notice is texted to **every** trusted person, including the one who did it. For the person who acted, the text is a receipt; it also tells them if someone else used their PIN. Each text has its own delivery status (waiting, sent, gave up), shown in trusted-person mode. A failed text never changes what is enforced.

**What leaves the phone.** Plainphone has no server, no analytics and no account. The only things it sends are these texts, through the carrier: requests (with the user's own words), notices and receipts. UX may combine routine receipts into one daily text, as long as waiting-change texts go at once.

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

### C2. Waiting changes: one at a time, crash-safe, re-checked at the end

**Which actions are waiting changes:**
1. turning Plainphone off;
2. changing who is a trusted person (add, hand over, remove);
3. switching mode, in either direction (C5);
4. in self-managed mode, loosening the friction (a shorter wait, more elevations a day).

**One at a time (F2).** At most **one** waiting change is pending on the phone, of any kind. Starting another while one is pending shows the pending one, with Cancel. This removes every question about two changes completing in different orders.

**Generations (F2).** Plainphone keeps two counters in device-protected storage:
- the **credential generation** of each PIN holder, which goes up whenever that person's PIN or recovery code is replaced (including through recovery);
- the **structure generation**, which goes up whenever the set of trusted people or the mode changes.

A waiting change records its own request id, the starter's credential generation and the structure generation. When it matures, Plainphone checks both again. If either has changed, the change is **cancelled** and everyone is texted. So:
- an owner who replaces a PIN that a child saw also cancels anything the child started with it;
- a change based on an old set of people can't restore a removed person or overwrite newer credentials;
- a turn-off started in one mode can't complete in another.

**Starting (F3).** A fresh PIN (or, in self-managed mode, the user's PIN), plus the confirmations each action already needs. Then **one atomic write** to device-protected storage holds:
- the wait record: request id, kind, pending details (for example the incoming person's PIN hash and number), which credential started it, both generations, and the clock reading from C2's counter;
- the text queue entries for the start notice, with the recipient list fixed at that moment.

Only after that write succeeds does Plainphone show "waiting" or send anything. If the write fails, the change doesn't start, and Plainphone says so. On every start, Plainphone sends any queued text not yet marked sent, so a crash can't lose the notice. Delivery is still not a condition for anything.

**Counting time (F7).** All waits, countdowns, deadlines and daily counts use one clock: a **proven running time** counter, P.
- P is stored as one atomic record: the total proven so far, the boot id, and the `SystemClock.elapsedRealtime()` reading at the last checkpoint ([SystemClock](https://developer.android.com/reference/android/os/SystemClock); it counts deep sleep and ignores wall-clock changes).
- **Checkpoint, same boot:** add the elapsed time since the last checkpoint, store the new reading. One atomic write. Repeated process starts in one boot never count the same interval twice, because each checkpoint moves the reading forward.
- **Checkpoint, new boot:** the time between the last checkpoint and the reboot is unknown and is **not** counted. The new boot counts from its own start: the first checkpoint adds `elapsedRealtime()` (time since this boot), and stores the new boot id.
- Checkpoints happen at least every 5 minutes, and at each start, end, debit and cancel.
- A wait matures when P minus its start reading is at least 24 hours. A debit expires when P minus its reading is at least 24 hours. A countdown or deadline inside one boot uses `elapsedRealtime()` directly, and a reboot ends it (the session contract revokes it; a countdown lapses without a debit).
- Wall-clock time is for display only. Time-zone and clock changes do nothing.
- The result: restarts, a switched-off phone and lost checkpoints can only lengthen a wait or keep a debit counted for longer. Screens say: "Time while the phone is off doesn't count."
- **Damaged P:** Plainphone writes a fresh P starting at zero and **re-stamps** every pending wait and every debit at zero, so each counts as just started. Nothing is refunded and nothing matures early; a pending change keeps its place and simply waits longer. If even that write fails, no wait matures and no elevation starts until it succeeds, and "Plainphone needs attention" shows.

**Texts at the start**, draft wording, finalised by UX:
> Plainphone: {name} asked to turn Plainphone off on {user}'s phone. It will turn off after 24 hours of the phone being on. To stop it, open the phone and tap Cancel{, or reply CANCEL}.

The reply part appears only if Q15 passes.

**Cancelling (F3, F8).** Any of these cancels the pending change:
- either trusted person, on the phone, with their PIN;
- anyone holding the phone, without a PIN (cancelling only keeps the phone as it is);
- a reply of `CANCEL` from a trusted person's number on record, **only if Q15 passes**. Because only one change can be pending, the reply cancels that one. A reply that arrives when nothing is pending, or after the change committed, does nothing and is logged. Duplicate replies do nothing more. A forged sender can only cancel.

A cancel is written durably as a **cancelled** state on the request before Plainphone says "Cancelled". Every scheduled callback checks the request's state and id before acting, so a stale timer can't complete a cancelled request. The cancel is texted to every trusted person, with who cancelled ("the trusted person", "someone holding the phone", "by text").

**Where Cancel is always reachable (F8).** The home-screen notice line (C8) is one place, but not the only one. Any pending change is also shown, with a PIN-free Cancel, on:
- the "Plainphone needs attention" screen;
- Plainphone's own settings screen, which the user can always open.

**Skipping (F2).** In supervised mode, a trusted person's recovery code skips the wait. The skip screen names the exact pending change ("Skip the wait and turn Plainphone off now?"), and the person confirms that change. Entering a recovery code to reset a forgotten PIN never skips anything. Using a code to skip counts as a recovery-code use: that person's PIN and code are replaced, which also raises their credential generation; the skipped change is checked against the generation it was confirmed under. In self-managed mode, nothing in Plainphone skips a wait.

**The commit point (F3).** When the wait matures and the re-check passes, Plainphone writes the request's state as **committing**, in one atomic write, and starts a separate **operation journal** for that change. From then on:
- Cancel is no longer offered; screens say "Already in progress".
- The operation journal resumes after any restart, step by step, reading each step back, until it is **completed**. Then everyone is texted.
- The operation journal is kept in two copies. If one is damaged, the other is used. If both are damaged, Plainphone keeps whichever credential set and policy are valid on disk, shows "Plainphone needs attention", texts everyone, and refuses new waiting changes until a helper looks at it. It never guesses a half-done state into a finished one.

**Missing, damaged, failed (F3).**
- **No record:** nothing is pending.
- **Damaged record while pending:** the change is cancelled, the phone stays as it was, everyone on the last known list is texted.
- **Damaged record while committing:** resume from the operation journal (above).
- **Failed write while starting:** the change doesn't start.
- **Failed write while cancelling:** Plainphone doesn't say "Cancelled" and retries; if the write still fails, the change does not commit either, and "needs attention" shows.

**When the wait ends.** The change goes ahead by itself after the re-check, then texts everyone. It doesn't wait for anyone to come back and confirm.

**During the wait** the phone stays fully locked down, and every other action works as normal.

### C3. Changing who the trusted people are

All three changes need a current trusted person's PIN. **The user alone can never change who the trusted people are.**

- **Add a second trusted person:** a current trusted person's PIN. Then the new person, in person, enters their name and number, sets their PIN, receives their recovery code (shown once), enters their reset-protection `userId` using the guide's People API steps on their own device, and confirms a test text. The helper or the current trusted person confirms the new person's account can be signed in to from another device, its second factor isn't only on a Plainphone phone, and it is at least 72 hours old.
- **Hand your role over:** the outgoing person's own PIN or recovery code, then the same steps for the incoming person.
- **Remove the other person:** a current trusted person's PIN. Plainphone never removes the last trusted person; that would be a switch to self-managed (C5) or turning off (C6).

**Why these wait 24 hours.** A child who saw a PIN could otherwise add themselves, with their own PIN, and keep control even after the PIN is changed. With the wait, every trusted person gets a text and a day to cancel, and replacing the seen PIN cancels the request (C2).

**Pending credentials** are stored in the wait record and grant nothing until the change completes.

**Completing (F4).** The operation journal runs these steps in order, each read back:
1. set the reset-protection list to the **new** set of IDs, send `FRP_CONFIG_CHANGED` to Play services, read the list back;
2. only when step 1 reads back correctly: replace the credential set (old set → new set) in one atomic write, and raise the structure generation;
3. clear the outgoing person's data; mark completed; text everyone.

If step 1 doesn't read back, the change **stays committing**: the old trusted people stay in charge with their old credentials, Plainphone retries step 1 on every policy pass, trusted-person mode shows "Reset protection not updated – call the helper", and nothing reports the change as done. There is no automatic exception. A helper can cancel the stuck change with a trusted person's PIN, which puts the reset-protection list back to the old IDs (read back) before cancelling.

After a restart at any point, either the old set or the new set works, never neither, and the reset-protection list read back always matches the set in charge once the change is completed.

### C4. Self-managed mode: ADR option D as a mode, friction not a lock

**What it is.** The same Plainphone app, Device Owner, lockdown, emergency floor and restrictions as supervised mode. The only difference is who can relax it: the user holds the PIN, and every loosening goes through friction. ADR 0001 rejected option D (launcher only) because it is "friction, not a lock", and said it wins "only if Beaux and the trusted person decide they want voluntary friction instead". Beaux has now asked for exactly that, as a choice per phone. Self-managed mode keeps the Device Owner, so the friction can't be skipped by changing the home app, safe mode or adb, as it could with a launcher.

**Said openly.** At setup and on "What Plainphone can't stop": "In self-managed mode, you hold the PIN. Plainphone adds waits and limits so that distraction takes effort. It can't stop you, because you are in charge. You can always remove it by waiting 24 hours, or by wiping the phone."

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

**Elevation (F6).** An elevation is a **single-use grant** with an id, one kind, and a deadline:

| Kind | What it allows | Deadline |
|---|---|---|
| Play | A Play session; approving apps installed during it, in Plainphone, before the deadline | 10 minutes |
| Settings | A Settings session | 5 minutes |
| Approve | Approving one app that is already installed but hidden; no packages added to lock task | 5 minutes |
| Update | One Plainphone update from the helper's computer (the approved plan's updater, unchanged: open sessions closed and read back first, then exact bytes, signer and version checks) | 10 minutes |

Steps:
1. **PIN, then the countdown.** The user enters the PIN and picks the kind. A countdown runs (default **10 minutes**). The phone works normally meanwhile. The user can cancel at any time.
2. **Start.** When the countdown ends, the notice line says "Ready – tap to start". The user taps Start within 5 minutes and confirms the kind again. Otherwise it lapses, with no turn used.
3. **Debit.** On Start, Plainphone writes the debit and the grant's journal entry in one atomic write, before anything is relaxed. Starts are serialized, so a double tap can't get two.
4. **Use.** The grant allows only its kind, until its deadline. It follows the session contract: end triggers, protected-state check, revoke on every start, damaged journal revokes everything, mitigation B at every unhide.
5. **End.** The grant is used up at its deadline, on Done, or on any Plainphone restart. An approval or update screen open after the deadline can't complete; the installed app stays hidden until another elevation.

**One at a time.** At most one countdown or grant exists. A second request shows the one in progress.

**Daily limit.** At most **2** debits in any 24 hours of proven running time (default). Debits age on C2's clock. A debit is never cleared by a crash, a reboot or a failed write; a damaged debit list is replaced by a fresh one counting as **no turns left**, written durably, ageing from now. If that write fails, no elevation starts.

**Never permanent, with one stated exception.** No setting removes the wait or the limit, or lengthens a deadline. The wait can be set from 10 minutes to 24 hours, and the limit from 0 to 3 a day. A stricter setting applies at once, including to a countdown already running (it lengthens). A looser setting is a waiting change. *Exception:* if Plainphone can't run while a grant is open, nothing revokes it automatically (the custody limit), and here nobody is watching. That is disclosed.

Changes made *during* an elevation persist, as they would for a trusted person: an approved app stays approved. The same web-containment rules apply; general browsers stay hidden whatever the mode.

**App requests.** In self-managed mode there is no one to ask. "Ask for an app" becomes "Add an app", which starts a Play elevation.

**Monthly check.** It costs one Play elevation. The home-screen line asks the user.

**Custody wording.** "Stay with the phone until you tap Done" becomes "Plainphone closes this at {time}".

**Reset protection (F10).** The list names the **user's own** Google `userId`, plus an optional backup person. It is never left empty. Setup checks the same things for the user's account as for a trusted person's: it can be signed in to from another device, and its second factor isn't only on this phone. After a recovery-mode wipe, setup asks for a listed account, so the user can set the phone up again without Plainphone (subject to Q10). A stranger without a listed account's password can't. The disclosure says this: a wipe erases the phone, but it is a way out that doesn't wait 24 hours.

The user's own account is signed in on the phone, as normal. The rule that no listed account is signed in on the phone applies to other people's accounts in supervised mode.

**Recovery code.** The user gets one, for a forgotten PIN. It resets the PIN only. It does **not** skip a countdown, the daily limit or any waiting change, because the user holds it. *Beaux's rule 3 ("the recovery code skips the wait") was about a code a trusted person keeps off the phone. This change asks Beaux to confirm the narrower rule for self-managed mode.*

### C5. Switching modes (F1)

**Both directions are waiting changes.** Revision 1 made self-managed → supervised instant, as "stricter". PLA-31 F1 showed that a self-managed user can enrol a second identity they control as the "trusted person" and get its PIN and recovery code at once, skipping every wait and limit. Plainphone can't tell whether the incoming trusted person is someone else, so the switch can't be treated as stricter.

- **Supervised → self-managed:** started with a trusted person's PIN. The user sets their PIN, receives their recovery code and enters their own `userId` (with the account checks in C4) before the wait starts. Either trusted person can cancel. A trusted person's recovery code skips it.
- **Self-managed → supervised:** started with the user's PIN. The incoming trusted person sets up as in C3. The wait is 24 hours. Nothing skips it. The user can cancel. **During the wait, self-managed rules stay in force**, the pending credentials grant nothing, and existing debits and countdowns carry on.
- Completion follows C3's order: new reset-protection list read back first, then the credential swap.
- A user on a supervised phone has no PIN, so they can't start either switch.

The honest summary for self-managed users: whatever route they take (turn off, or switch to a trusted person they control), getting full control back takes at least 24 hours of the phone being on, or a wipe.

### C6. Turning off waits 24 hours

The existing off switch keeps everything it has (fresh authority, two plain confirmations, the full restore list read back, the successor home app and dialer check, journaled and resumable, never a silent wipe) and gains the waiting-change rules from C2 in front of it. Its operation journal is the existing turn-off journal.

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
2. the pending waiting change: "Plainphone will turn off after {time}. Tap to cancel." or "A change to trusted people is waiting. Tap to cancel.";
3. "Opening up in {n} minutes" / "Ready – tap to start" (C4);
4. the monthly-check line (approved-apps).

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
- "What Plainphone can't stop" gains (F9): "Plainphone has no server, no analytics and no account. It sends texts only to your trusted people, through your carrier, and keeps its history on the phone. But this phone runs Google's Android with a Google account, and Google collects some data that Plainphone can't stop. The privacy steps from setup reduce it."

### Who can relax the lockdown (summary for ADR 0001)

| Way | Supervised mode | Self-managed mode |
|---|---|---|
| Play or Settings session | Either trusted person's PIN; custody | The user's PIN, a 10-minute countdown, then a single-use grant; at most 2 a day; no custody |
| Approve an app | Either trusted person's PIN | The user's PIN, then an elevation |
| Turn off | A trusted person's PIN, then 24 h (anyone can cancel); their recovery code skips | The user's PIN, then 24 h; nothing skips |
| Change trusted people | A trusted person's PIN, then 24 h; their recovery code skips | Not applicable |
| Switch mode | A trusted person's PIN, then 24 h; their recovery code skips | The user's PIN, then 24 h; nothing skips |
| Web and Wi-Fi sign-in windows | PLA-17 (separate change) | PLA-17 (separate change) |
| Recovery-mode wipe | Reset protection asks for a trusted (or backup) person's account | Reset protection asks for the user's own (or backup) account |
| Plainphone can't run during a session | Relaxation stays until a helper fixes it (accepted custody limit) | The same, with nobody watching |

## Risks / Trade-offs

- **[A trusted person can always override the other]** → by design (Beaux: "either can approve"). Receipts to both, the history list and the waits make it visible. Said plainly at setup.
- **[A PIN proves knowledge, not identity]** → the waits, receipts and generation checks exist because of this. A self-managed user can enrol a second identity as "trusted person"; that is now a 24-hour waiting change like everything else.
- **[Self-managed mode doesn't stop a determined user]** → by design, and said openly. The Device Owner keeps the friction from being skipped by safe mode, adb or a launcher change. A wipe is always a way out.
- **[More texts]** → receipts to both people for every action. UX may batch routine receipts daily; waiting-change texts go at once.
- **[One pending change at a time]** → a child can start a decoy change, but anyone can cancel it at once, and the trusted person is texted.
- **[Cancel by text can be forged]** → it can only cancel the pending change. A trusted person whose turn-off is blocked by forged cancels can use their recovery code to skip.
- **[Time while the phone is off doesn't count]** → a wait can last longer than 24 hours. Disclosed. Never shorter.
- **[A stuck handover]** → if reset protection can't be updated, the change stays in progress with the old people in charge, until it works or a helper cancels it.
- **[Unhide-to-flush gap]** → a power loss between an unhide and the mitigation B flip can still boot-loop the phone. Narrowed, measured in task 1.3, disclosed, and on the target-phone checklist (approved plan task 6.3).
- **[Reset protection with two or three IDs is untested]** → Q10 grows to cover it, including changed lists and failed updates. Google documents configurable account lists, changing them over time, and the broadcast ([FRP guidance](https://developer.android.com/work/dpc/security#factory_reset_protection)); whether this build enforces three IDs is a device test.
- **[`RECEIVE_SMS` for cancel replies]** → non-default SMS apps can receive `SMS_RECEIVED_ACTION` ([Telephony reference](https://developer.android.com/reference/android/provider/Telephony)), but `RECEIVE_SMS` is hard-restricted and needs installer allowlisting as well as a runtime grant ([permission reference](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)). Not tested. Q15 decides; without it, cancel is on the phone only, and no copy mentions replies.

## Migration Plan

- The approved change archives first; this change's deltas then apply to the archived specs.
- No phones are in use, so there is no data to migrate. The MVP builds the two-person model from the start.
- Tasks in `tasks.md` are added to the MVP build ([PLA-15](/PLA/issues/PLA-15)) once Beaux approves.

## Open Questions (for Beaux, with the Lead's default)

These are the Lead's additions, beyond the literal answers on card `c60170d6`. Beaux approves or changes them with this change.
1. **Waits on changing trusted people and on switching mode (both directions).** Default: yes, 24 hours, same as turning off.
2. **Recovery code in self-managed mode.** Default: resets the PIN only, skips nothing.
3. **Friction numbers.** Default: 10-minute countdown, 5-minute Start window, 2 elevations a day (settable 0–3), stricter at once.
4. **Receipts.** Default: every action is texted to both people, including the one who did it.
5. **Anyone holding the phone can cancel a pending change.** Default: yes.
6. **Cancel by text reply.** Default: yes, if Q15 passes. Otherwise on the phone only.
7. **A notice-only contact in self-managed mode** (someone who gets the texts but has no PIN). Default: not now.
8. **History list kept 90 days on the phone.** Default: yes.

## Review response (PLA-31, revision 1 → 2)

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
