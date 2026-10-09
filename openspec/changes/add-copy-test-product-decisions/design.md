## Context

The approved plan (`add-plainphone-spike-and-mvp`, design D2 and D6) has one trusted person per phone. That person holds the PIN and a recovery code kept off the phone. A **supervised session** is the only way the lockdown is relaxed, and it is trusted-person custody. Turning off needs a fresh PIN and two confirmations.

Beaux's answers to his copy-test questions (card `c60170d6`, Telegram 4481) change three things:
- who can relax the lockdown (two trusted people, or the user themselves in a self-managed mode);
- how fast the lockdown can be removed (a 24-hour wait);
- what setup does about Google's data collection.

Inputs reused here:
- the session contract (trusted-person-controls, design D2);
- the PLA-22 boot loop and **mitigation B**: right after unhiding a package, flip one of Plainphone's own components with `PackageManager.SYNCHRONOUS`, which forces PackageManager to save at once. It booted 3 of 3 on the emulator, including a reboot at 0 s (`spikes/bootloop-repro/RESULT.md`, Q8). PLA-25 is reviewing it;
- the durable count and conservative ageing rules from the Wi-Fi sign-in recommendation (`docs/wifi-signin-recommendation.md`, section 2, revision 3, PLA-17).

## Goals / Non-Goals

**Goals:**
- Two trusted people who are equals, with clear rules when they disagree.
- A self-managed mode that is honest: friction, not a lock.
- One wait rule, applied the same way to every change that can't be undone afterwards.
- Every new relaxation path reuses the session contract. No new way to relax the lockdown without a journal and a deadline.
- Privacy steps in setup, and a plain note.

**Non-Goals:**
- The web window and Wi-Fi sign-in window (PLA-17).
- A two-person rule (both must agree). Beaux chose "either can approve".
- Any server, remote control or remote unlock. Texts are notices; the only thing a text can do is cancel a pending wait (C2), and only if Q15 passes.
- GrapheneOS, LineageOS or any non-stock base.
- Changing the custody limit Beaux accepted for supervised sessions.

## Decisions

### C1. Two trusted people, as equals

**Each trusted person has their own:**
- name and phone number;
- PIN, stored as today (PBKDF2-HMAC-SHA256, at least 600,000 iterations, 16-byte salt);
- PIN lockout counter and delay;
- recovery code (128 bits, hash only) with its own counter;
- reset-protection Google `userId`.

**The PIN screen asks who is entering it** ("Who are you? Beaux / the trusted person"), then takes the PIN. This keeps each person's lockout separate, and stops one entry from being tried against two PINs. A lockout on one person never blocks the other person, or either recovery code.

**Powers.** Either trusted person can do every trusted-person action alone. Neither can see, change or reset the other's PIN or recovery code. A recovery code resets only its owner's PIN.

**Notices.** Every privileged action is texted to each trusted person who didn't do it, for example "Plainphone: the trusted person approved Spotify on Beaux's phone." App requests, the monthly check, lockout warnings, "needs attention" and system-update notices go to both. Each text has its own delivery status (waiting, sent, gave up), shown in trusted-person mode. A failed text never changes what is enforced.

**Disagreements.** The two are equals, and the rules follow from one question: *can the other person undo it afterwards?*

| Action | Can the other undo it later? | Rule |
|---|---|---|
| Approve or remove an app, change its permissions | Yes | Takes effect at once. Last action stands. Texted to the other. Both see it in the history list. |
| Open a Play or Settings session | Yes (the protected-state check restores protections) | At once, as today. Texted to the other. |
| Install a Plainphone update | Partly (no rollback) | At once, as today. Texted to the other. |
| Turn Plainphone off | No | 24-hour wait (C2). The other can cancel. |
| Add a trusted person, hand your role over, remove the other person | No | 24-hour wait (C2). The other can cancel. |
| Switch the phone to self-managed | No | 24-hour wait (C2). The other can cancel. |

**What the waits protect against.** They protect against someone who has seen a PIN, for example a child. They don't protect one trusted person from the other: a trusted person who uses their recovery code skips the wait. Plainphone says this plainly to both people at setup: "You are equals. Either of you can approve apps, or turn Plainphone off."

**History list.** Trusted-person mode keeps a 90-day list of privileged actions: who, what, when, and text status. No app content, URLs or message text. It is in device-protected storage, so it survives reboots. It is a record, not a control.

### C2. One 24-hour wait rule

**Which actions wait** (the "waiting changes"):
1. turning Plainphone off;
2. changing who is a trusted person (add, hand over, remove);
3. switching a supervised phone to self-managed;
4. in self-managed mode, loosening the friction (a shorter wait, more elevations a day).

**Starting.** A fresh PIN (or, in self-managed mode, the user's PIN), plus the plain confirmations each action already needs.

**Order of writes.** Plainphone writes a **wait record** to device-protected storage, with a checksum, *before* it queues any text or shows "waiting". The record holds: the kind of change, any pending details (for example the incoming person's PIN hash and number), who started it, the boot id, and elapsed time at the start.

**Counting time.** The wait ends when Plainphone can prove 24 hours have passed. It uses the same conservative ageing as PLA-17:
- Plainphone saves its elapsed time every 5 minutes.
- After a reboot, it counts only time it can prove: up to the last save before the reboot, plus time since the new boot.
- So restarts, a phone that is switched off, and clock or time-zone changes can only make the wait longer, never shorter. The screens say so: "Time while the phone is off doesn't count."

**Texts at the start** go to every trusted person (none in self-managed mode, see the open questions). Draft wording, finalised by UX:
> Plainphone: {name} asked to turn Plainphone off on {user}'s phone. It will turn off after 24 hours. To stop it, reply CANCEL, or open trusted-person mode on the phone.

**Cancelling.** Any of these cancels a pending change:
- either trusted person, on the phone, with their PIN;
- anyone holding the phone, without a PIN (cancelling only makes the phone stricter, so it needs no authority);
- a reply of `CANCEL` from a trusted person's number on record, **if Q15 shows Plainphone can receive it**. A forged sender can only cancel, never start or speed up anything. Without Q15, cancel is on the phone only, and the text says so.

A cancel is texted to every trusted person, with who cancelled (or "someone holding the phone", or "by text").

**Skipping.** In supervised mode, a trusted person's recovery code skips the wait, at the start or during it. That counts as a recovery-code use: the code and that person's PIN are replaced together, as today. In self-managed mode, nothing skips the wait (C4).

**When the wait ends.** The change goes ahead by itself, then texts everyone. It doesn't wait for anyone to come back and confirm, so it works when the starter is away.

**Damaged or missing record.** If the wait record fails its checksum, Plainphone treats the change as **cancelled**: the phone stays as it was. It texts every trusted person. This is the safe direction.

**One at a time.** At most one waiting change of each kind. Starting a second one while one is pending shows the pending one instead. After a cancel, a new start begins a fresh 24 hours.

**During the wait** the phone stays fully locked down, and every other action works as normal. The home screen shows one plain line (C8), so the user always knows, and can cancel if they didn't start it.

### C3. Changing who the trusted people are

All three changes need a current trusted person's PIN. **The user alone can never change who the trusted people are.**

- **Add a second trusted person:** a current trusted person's PIN. Then the new person, in person, enters their name and number, sets their PIN, receives their recovery code (shown once; the helper or the current trusted person confirms the new person has it), and enters their reset-protection `userId` using the guide's People API steps on their own device. A test text must reach them and be confirmed.
- **Hand your role over:** the outgoing person's PIN, then the same steps for the incoming person. The outgoing person's PIN, code and `userId` are removed when the change completes.
- **Remove the other person:** a current trusted person's PIN. Plainphone never removes the last trusted person; that would be a switch to self-managed (C5) or turning off (C6).

**Why these wait 24 hours.** A child who saw a PIN could otherwise add themselves, with their own PIN, and keep control even after the PIN is changed. With the wait, the current trusted people get a text and a day to cancel.

**Pending credentials.** The incoming person's PIN hash, code hash, number and `userId` are stored in the wait record. They grant nothing until the wait ends.

**Completing.** In one journaled step, Plainphone:
1. writes the new set of trusted people;
2. sets the new reset-protection list, sends `FRP_CONFIG_CHANGED` to Play services, and reads it back;
3. only then removes the outgoing person's PIN, code and `userId`.

After a restart mid-way, either the old set or the new set works, never neither. If the reset-protection readback fails, the change completes for PINs and texts, and trusted-person mode shows "Reset protection not updated – call the helper". The same 72-hour rule for new Google accounts applies as at setup.

### C4. Self-managed mode: ADR option D as a mode, friction not a lock

**What it is.** The same Plainphone app, Device Owner, lockdown, emergency floor and restrictions as supervised mode. The only difference is who can relax it: the user holds the PIN, and every loosening goes through friction. ADR 0001 rejected option D (launcher only) because it is "friction, not a lock" and wins "only if Beaux and the trusted person decide they want voluntary friction instead". Beaux has now asked for exactly that, as a choice per phone. Self-managed mode keeps the Device Owner, so the friction can't be skipped by changing the home app, safe mode or adb, as it could with a launcher.

**Said openly.** At setup and on "What Plainphone can't stop": "In self-managed mode, you hold the PIN. Plainphone adds waits and limits so that distraction takes effort. It can't stop you, because you are in charge."

**Stricter at once, looser with friction.**

| Action in self-managed mode | Rule |
|---|---|
| Remove an app, lengthen the wait, lower the daily limit | PIN, at once |
| Open a Play or Settings session (to add an app, change a setting, run the monthly check) | PIN, then **elevation** (below) |
| Approve an app that is already installed but hidden | PIN, then elevation |
| Install a Plainphone update | PIN, then elevation |
| Shorten the wait, raise the daily limit | PIN, then the 24-hour wait (C2) |
| Turn off, or switch to supervised | Turn off: PIN, then the 24-hour wait. Switch to supervised: see C5 |

**Elevation** is a supervised session started by the user. It follows the whole session contract (journal before anything relaxes, end triggers, deadline, protected-state check, revoke on every start, damaged journal revokes everything, mitigation B), plus:
1. **A wait before it starts.** After the PIN, a countdown of **10 minutes** (default). The phone works normally meanwhile. When the countdown ends, the home screen line says it's ready, and the user taps "Start" within 5 minutes, or it lapses without using up a turn. The user can cancel at any time.
2. **A daily limit.** At most **2 elevations in any 24 hours** (default). A turn is counted, durably, when the user taps Start, before anything is relaxed. A count is never cleared by a crash, reboot or failed write. Ageing uses the same conservative rule as C2. A damaged count is treated as "no turns left", and starts a fresh 24 hours from when it was found.
3. **Deadlines** as in the session table: Play 10 minutes, Settings 5 minutes. No extending. Another elevation costs another turn and another wait.
4. **Never permanent.** No setting removes the wait or the limit, or makes a session last longer. The user can choose a wait between 10 minutes and 24 hours, and a limit between 0 and 3 a day. Changes towards stricter apply at once; towards looser, after the 24-hour wait.

Changes made *during* an elevation persist, as they would for a trusted person: an approved app stays approved. That is the point of opening up, and the same web-containment rules apply (general browsers stay hidden whatever the mode).

**App requests.** In self-managed mode there is no one to ask. "Ask for an app" becomes "Add an app", which starts an elevation.

**Monthly check.** It costs one elevation. The home-screen line asks the user, not a trusted person.

**Custody.** There's no second person, so the custody wording ("Stay with the phone until you tap Done") becomes "Plainphone closes this at {time}". The custody limit (if Plainphone can't run, a relaxation stays) applies with nobody watching. That fits a mode where the user is in charge anyway. It is disclosed.

**Reset protection.** The list names the **user's own** Google `userId`, plus an optional backup person. It is never left empty, because an explicit list can be read back. After a recovery-mode wipe, the user can set the phone up again with their own account, without Plainphone. That is the honest consequence of a friction mode: the wipe gives the user nothing they couldn't get by waiting 24 hours. It still stops a thief.

**Recovery code.** The user gets one, for a forgotten PIN. It resets the PIN only. It does **not** skip the elevation wait, the daily limit or any 24-hour wait, because the user holds it. *Beaux's rule 3 ("the recovery code skips the wait") was about a code a trusted person keeps off the phone. This change asks Beaux to confirm the narrower rule for self-managed mode.*

### C5. Switching modes

- **Supervised → self-managed** is a waiting change (C2): a trusted person's PIN, then the 24-hour wait, cancellable by either trusted person; a trusted person's recovery code skips it. During setup of the switch, the user sets their PIN and gets their recovery code, and their own `userId` is entered. These are pending until the wait ends. When it ends, the trusted people's PINs, codes and IDs are removed, and the reset-protection list is replaced as in C3.
- **Self-managed → supervised** makes the phone stricter, so it takes effect at once: the user's PIN, then the new trusted person sets up as in C3, in person, with a confirmed test text. The user's PIN and code are then removed.
- The user in a supervised phone has no PIN, so they can't start either switch.

### C6. Turning off waits 24 hours

The existing off switch keeps everything it has (fresh authority, two plain confirmations, the full restore list read back, the successor home app and dialer check, journaled and resumable, never a silent wipe) and gains the 24-hour wait from C2 in front of it.

- **Supervised:** a trusted person's PIN starts the wait; either can cancel; a trusted person's recovery code skips it.
- **Self-managed:** the user's PIN starts the wait; the user can cancel; nothing skips it.
- When the wait ends, the turn-off journal starts and runs as today.
- A flat battery doesn't turn Plainphone off: Android keeps the restrictions and hidden apps across a restart (PLA-5 A6). The copy keeps that fact.

### C7. Mitigation B is part of every session start

- Every session start that **unhides** a package (Play, Settings, elevation, and later the PLA-17 windows) applies mitigation B straight after the unhide, then reads the hidden state back, before the session screen appears.
- Plainphone does not start a session that would unhide a package when the battery is below **5% and not charging**. It says: "Charge the phone first." This narrows the PLA-22 window; it doesn't close it.
- Mitigation B rests on 3 emulator trials. It is re-tested in the MVP's Q6 and Q12 runs, and on the target phone before handover (task 6.3 of the approved plan).
- Self-managed elevation is started by the user, alone, possibly often, so it is exposed to the PLA-22 race more often than supervised sessions. The daily limit caps this at 2 starts a day by default.

### C8. One home-screen line at a time

The home screen shows at most **one** notice line. When several apply, the most urgent wins:
1. "Plainphone needs attention" (device-lockdown);
2. "Plainphone will turn off at {time}. Tap to cancel." (C6), or "A change to trusted people is waiting until {time}. Tap to cancel." (C3, C5);
3. "Opening up starts in {n} minutes" / "Ready – tap to start" (C4);
4. the monthly-check line (approved-apps).

As today, no line pushes Emergency, Phone or Messages off-screen at the largest font size.

### C9. Privacy steps in setup

During on-phone setup, straight after the user signs in to Google (step 3), the guide and the setup screen walk the helper and the user through four steps:
1. pause **Web & App Activity** in the Google account;
2. turn **Timeline** (location history) off;
3. turn **ad personalisation** off (My Ad Center);
4. delete the phone's **advertising ID**.

- This happens before lock task starts, so the system and Google account screens are reachable.
- The exact screen paths depend on the build and on Google's account pages. The guide records them for each supported build, and the setup dry run (task 5.3 of the approved plan) confirms them. They are not promised here.
- Plainphone can't read Google account settings. Each step is recorded as **done** or **skipped** by the helper. The handover check shows the record and doesn't gate "Ready" on it.
- "What Plainphone can't stop" gains: "Plainphone doesn't send anything anywhere. But this phone runs Google's Android with a Google account, and Google collects some data that Plainphone can't stop. The privacy steps from setup reduce it."

### Who can relax the lockdown (summary for ADR 0001)

| Way | Supervised mode | Self-managed mode |
|---|---|---|
| Play or Settings session | Either trusted person's PIN; custody | The user's PIN, then a 10-minute wait; at most 2 a day; no custody |
| Approve an app | Either trusted person's PIN | The user's PIN, then elevation |
| Turn off | A trusted person's PIN, then 24 h (either can cancel); their recovery code skips | The user's PIN, then 24 h; nothing skips |
| Change trusted people or mode | A trusted person's PIN, then 24 h; their recovery code skips | To supervised: at once |
| Web and Wi-Fi sign-in windows | PLA-17 (separate change) | PLA-17 (separate change) |
| Recovery-mode wipe | Reset protection asks for a trusted person's account | Reset protection asks for the user's own account |

## Risks / Trade-offs

- **[A trusted person can always override the other]** → by design (Beaux: "either can approve"). Notices, the history list and the waits make it visible. Said plainly at setup.
- **[Self-managed mode doesn't stop a determined user]** → by design, and said openly. The Device Owner keeps the friction from being skipped by safe mode, adb or a launcher change.
- **[More texts]** → texts to two people for every action may annoy. UX may batch low-importance notices (for example a daily summary) as long as waiting-change texts go at once. Open question below.
- **[Cancel by text can be forged]** → it can only cancel, never start or speed up. A trusted person whose turn-off is blocked by forged cancels can use their recovery code.
- **[Time while the phone is off doesn't count]** → a wait can last longer than 24 hours. Disclosed. Never shorter.
- **[User-started elevation is exposed to the PLA-22 race more often]** → mitigation B, the battery guard, the daily limit, and a re-test before release.
- **[Reset protection with two or three IDs is untested]** → Q10 grows to cover it. Any documented limit on the number of accounts is unknown; Q10 tests three.
- **[`RECEIVE_SMS` for cancel replies]** → a restricted permission, granted by the Device Owner like `SEND_SMS`. Not tested. Q15 decides; without it, cancel is on the phone only.

## Migration Plan

- The approved change archives first; this change's deltas then apply to the archived specs.
- No phones are in use, so there is no data to migrate. The MVP builds the two-person model from the start.
- Tasks in `tasks.md` are added to the MVP build ([PLA-15](/PLA/issues/PLA-15)) once Beaux approves.

## Open Questions (for Beaux, with the Lead's default)

1. **Recovery code in self-managed mode.** Default: resets the PIN only, skips nothing (C4).
2. **Friction numbers.** Default: 10-minute wait, 2 elevations a day, user can make it stricter at once. Change the defaults if you like.
3. **A notice-only contact in self-managed mode** (someone who gets the texts but has no PIN). Default: not now; easy to add later.
4. **Cancel by text reply.** Default: yes, if Q15 passes. Otherwise on the phone only.
