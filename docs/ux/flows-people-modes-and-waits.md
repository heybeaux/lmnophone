# Two trusted people, self-managed mode, 24-hour waits and privacy

Owner: Product & UX Designer. Task: [PLA-42](/PLA/issues/PLA-42), which is task 3.1 of
`openspec/changes/add-copy-test-product-decisions/tasks.md`. Beaux approved that change on 2026-10-08 (PLA-30, card
`a9a11371`).
Status: **draft for the Verifier** ([PLA-16](/PLA/issues/PLA-16)). Nothing here is app code.

Words, rules and placeholders are in [README.md](README.md). This file adds:

| Placeholder | Means |
|---|---|
| `{name}` | the trusted person whose PIN or recovery code was used (in self-managed mode, the user) |
| `{other}` | the other trusted person, when there are two |
| `{everyone}` | every trusted person's name: "Sam", or "Sam and Jo" |
| `{tps}` | the same, joined with "or": "Sam", or "Sam or Jo" |
| `{new}` | the person being added, or taking over |
| `{change}` | what a waiting change does, from the [table in M3](#m3-changes-that-wait-24-hours) |
| `{code}` | the cancel code for one waiting change (format: AE's choice; digits only, so phones don't autocorrect it) |

**Spec abbreviations:** TPC = trusted-person-controls, GS = guided-setup, CHS = calm-home-screen, DL =
device-lockdown, all in the `add-copy-test-product-decisions` change. C1–C9 = sections of that change's `design.md`.

**Where things live.** Screens that already had a home were changed in place:
- setup (mode choice, two people, privacy steps): [flows-setup.md](flows-setup.md);
- "What Plainphone can't stop" and turning off: [flows-limits-and-off.md](flows-limits-and-off.md);
- the user's side of a waiting change (U9) and the countdown (U10): [flows-user.md](flows-user.md);
- the PIN screen and the trusted-person menu: [flows-trusted-person.md](flows-trusted-person.md).

This file has everything new, the exact-strings index ([M0](#m0-exact-strings-and-where-they-come-from)) and the
strings I think should change ([flags](#flags-strings-i-think-should-change)).

---

## M0. Exact strings, and where they come from

These are fixed by the spec or the design. They are used **word for word** in these files. The engineer copies
them from here. "Draft" in the source column means the design gave a draft and asked UX to finish it; the finished
wording is in the screen named.

| String | Where it shows | Source |
|---|---|---|
| Who are you? | PIN screen, two trusted people ([M1](#m1-who-are-you)) | C1 ("Who are you? First trusted person / Second trusted person"); TPC "up to two trusted people" |
| You are equals. Either of you can approve apps, or turn Plainphone off. | Setup, second trusted person ([S2c](flows-setup.md#s2-whos-who-step-2-of-8)) | C1. **Flag 1** |
| Plainphone will turn off after {time}. Tap to cancel. | Home notice line | CHS |
| A change to trusted people is waiting. Tap to cancel. | Home notice line | CHS |
| A change to how this phone is set up is waiting. Tap to cancel. | Home notice line | CHS (PLA-39) |
| Opening up in {n} minutes | Home notice line, U10a | CHS |
| Ready – tap to start | Home notice line, U10b | CHS |
| Ask [trusted person] to do the monthly phone check | Home notice line | CHS. **Flag 2** for two people |
| Time for your monthly phone check | Home notice line, self-managed | CHS (PLA-39) |
| Plainphone needs attention | Home notice line; "Needs you" | CHS; DL |
| Already in progress | U9e; [M3d](#m3d-the-change-on-the-trusted-person-side) | TPC "Commit"; C2 |
| Cancelled | U9d | TPC ("before Plainphone shows 'Cancelled'") |
| Time while the phone is off doesn't count. | U9a, [M3](#m3-changes-that-wait-24-hours), [M7](#m7-self-managed-mode) | C2 ("Screens say: …"); TPC clock |
| Skip the wait and turn Plainphone off now? | Skip screen ([M3e](#m3e-skip-the-wait-with-a-recovery-code)) | C2; TPC recovery-code scenario |
| Reset protection not updated – call the helper | Trusted-person "Needs you" ([M4](#m4-a-change-is-stuck)) | DL; TPC |
| Until this is fixed, we can't say whose Google account a wipe would ask for. | M4 | C3 |
| Stop this change | M4 | C3 |
| Plainphone recovered from an interrupted change. … | [M5](#m5-after-an-interrupted-change) | C2, **draft**, finished in M5 |
| Plainphone closes this at {time} | Session screen, self-managed ([M7f](#m7f-while-it-is-open)) | C4 ("Custody wording") |
| Stay with the phone until you tap Done. | Session screen, supervised (unchanged) | TPC session contract |
| Charge the phone first. | [M10](#m10-battery-and-the-clock) | C7 |
| friction, not a lock | Self-managed setup ([S1b](flows-setup.md#s1-how-is-this-phone-looked-after-step-1-of-8)), [M7](#m7-self-managed-mode), L1 | GS scenario "Self-managed setup"; TPC disclosure scenario |
| In self-managed mode, you hold the PIN. Plainphone adds waits and limits so that distraction takes effort. It can't stop you, because you are in charge. You can always remove it by waiting at least 24 hours of the phone being on, or by wiping the phone. | S1b, L1 (self-managed) | C4. **Flag 3** adds the spec's "longer if…" |
| Plainphone has no server of its own, no analytics and no account. It sends texts to your trusted people through your carrier, connects to your helper's computer over home Wi-Fi only to update itself, and keeps its history on the phone. But this phone runs Google's Android with a Google account, and Google collects some data that Plainphone can't stop. The privacy steps from setup reduce it. | L1 item 11 | C9. **Flag 4** for self-managed |
| Plainphone will turn off after at least 24 hours of the phone being on. To stop it, open the phone and tap Cancel{, or reply CANCEL {code}}. | Start text ([M9](#m9-texts)) | C2, **draft**, finished in M9 |
| CANCEL {code} | Reply format, only if Q15 passes | C2; TPC |

The privacy-step names (Web & App Activity, Timeline, ad personalisation, advertising ID) are Google's and
Android's own labels, so the helper can find them. They are not Plainphone words.

### Flags: strings I think should change

None of these is changed on screen yet. The Lead decides; each has a recommendation.

1. **"approve" in the equals note.** C1's sentence says "Either of you can approve apps". The [word
   list](README.md#words-we-use-and-words-we-never-show) keeps "approve" off every screen; trusted people see
   "Add". **Recommendation:** "You are equals. Either of you can add apps, or turn Plainphone off." Same meaning.
2. **The monthly line with two trusted people.** CHS gives "Ask [trusted person] to do the monthly phone check".
   With two people there are two names. **Recommendation:** fill the placeholder with `{tps}`: "Ask Sam or Jo to do
   the monthly phone check". This fills the placeholder; it doesn't reword the line.
3. **Self-managed disclosure is missing "longer if…".** C4's sentence says "at least 24 hours of the phone being
   on". GS and TPC both require "(longer if the change is cancelled or interrupted)". **Recommendation, used on
   screen:** C4's sentence with the spec's words added: "…by waiting at least 24 hours of the phone being on
   (longer if the change is cancelled or interrupted), or by wiping the phone." This follows the spec, which
   outranks the design draft.
4. **The Google line in self-managed mode.** C9's sentence says "sends texts to your trusted people". A
   self-managed phone has none. **Recommendation:** in self-managed mode, replace that clause with "sends texts
   only to your helper, if one was entered at setup," (or drop it if there is no helper number). See [Open
   question 3](#open-questions-for-the-lead).
5. **"Ready – tap to start" uses an en dash.** Keep it; noted only because some keyboards in the codebase may
   turn it into a hyphen. The engineer copies the character.

---

## M1. "Who are you?"

**Spec:** TPC "A phone has up to two trusted people, as equals" (the PIN screen asks first; checks against that
person only; equal PINs allowed and never compared); TPC "Trusted-person PIN has an exact lockout schedule" (one
PIN's lockout doesn't affect another); C1.

Shown before [T1a](flows-trusted-person.md#t1a-enter-pin) whenever a PIN is asked for **and the phone has two
trusted people**. With one trusted person, or in self-managed mode, there is nobody to choose, so it is skipped and
T1a opens straight away.

### M1a. Choose

```
┌──────────────────────────────┐
│ Who are you?                 │
│                              │
│ ┌──────────────────────────┐ │
│ │ Sam                      │ │  ← two equal rows, no primary button
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ Jo                       │ │
│ └──────────────────────────┘ │
│                              │
│ Go back                      │
└──────────────────────────────┘
```

- **Title:** Who are you?
- **Rows:** one per trusted person, first name only, in the order set at setup. Same size, same style.
- **Secondary:** Go back
- **Then:** T1a with that person's name: "{name}'s PIN".
- **Notes:**
  - Never show which person used the phone last, and never pre-select one.
  - An entry is checked against the chosen person's PIN only. A wrong entry counts only against that person.
  - **Never say two PINs match.** If someone picks "Jo" and types Sam's PIN, the answer is the usual "That PIN
    isn't right. Try again." Nothing else.
  - The trusted-person band then reads "For {name}" ([design-tokens.md](design-tokens.md#trusted-person-mode-band)),
    so the user can see whose PIN is in use.

### M1b. One person is waiting after too many tries

T1b, with the person's name in the title, plus one line naming the other person:

- **Title:** Too many tries for {name}'s PIN
- **Body:** Wait {time} before trying again. You can use {name}'s recovery code now instead. {other}'s PIN still
  works.
  Calls, texts and Emergency still work.
- **Primary:** Use recovery code
- **Secondary:** Choose someone else → M1a
- **Notes:** "{other}'s PIN still works" only with two people (spec scenario "One person locked out").

### M1c. Recovery code, with two people

T1c is unchanged, except:
- the title is "{name}'s recovery code";
- after the new PIN is set: "Only your PIN and code changed. {other}'s PIN and code are the same as before."
- **If a change is waiting that was started with this person's PIN**, before the code is checked:
  "A change is waiting that was started with your PIN: {change}. Setting a new PIN cancels it." This is true
  (TPC "Owner replaces a seen PIN") and stops the cancel coming as a surprise.
- Resetting a PIN this way **never** skips a wait, and no screen on this path mentions skipping. Skipping is its
  own screen ([M3e](#m3e-skip-the-wait-with-a-recovery-code)).

**Change PIN** (trusted-person menu) gets the same warning line when a change started with that PIN is waiting.

---

## M2. Trusted people

**Spec:** TPC "Changing who the trusted people are needs a current trusted person"; C3.

### M2a. The page ("Trusted people" in the trusted-person menu)

- **Title:** Trusted people
- **Body:** People who look after {user}'s phone. Each has their own PIN and recovery code. You are equals:
  either of you can add apps, open the Play Store, or turn Plainphone off.
  (With one person: "{name} looks after {user}'s phone.")
- One box per person: **{name}**, second line their number.
- **Rows** (menu, no primary button):

  | Row | Shown when | Small line |
  |---|---|---|
  | **Add a second trusted person** | One person | Takes 24 hours, and {name} gets a text |
  | **Hand your place to someone else** | Always | Takes 24 hours |
  | **Remove {other}** | Two people | Takes 24 hours, and {other} gets a text |
  | **Switch to {user} looking after this phone** | Always | Takes 24 hours ([M6](#m6-switching-mode)) |

- **Notes:** there is no "Remove {name}" for yourself and no remove at all with one person: Plainphone never removes
  the last trusted person (TPC). "Hand your place" covers leaving.

### M2b. Before it starts (one screen per kind)

| Kind | Title | Body |
|---|---|---|
| Add | Add a second trusted person? | {new} will look after {user}'s phone with you, as an equal. {new} needs to be here now, with their own phone or computer, for about 15 minutes. |
| Hand over | Hand your place to someone else? | {new} will take your place. Your PIN and recovery code will stop working. {new} needs to be here now, with their own phone or computer, for about 15 minutes. |
| Remove | Remove {other}? | {other} will no longer look after {user}'s phone. {other}'s PIN and recovery code will stop working. |

Then, on every kind, the same block:
"This waits 24 hours of the phone being on. Time while the phone is off doesn't count. {everyone} will get a text now,
and anyone holding the phone can cancel until then."

- **Primary:** Continue
- **Secondary:** Not now
- "{new}… their own phone": a pronoun for `{new}`, who isn't named yet on this screen. Once named, screens repeat the
  name.

### M2c. The new person sets up (add and hand over)

The same screens as setup, in order, in the new person's hands. Each is the setup screen named, with `{tp}` = `{new}`:

1. **Hand the phone to {new}** — name and mobile number ([S2](flows-setup.md#s2-whos-who-step-2-of-8) fields).
2. PIN, then recovery code, shown once ([S3a–S3b](flows-setup.md#s3-pins-and-recovery-codes-step-3-of-8)).
3. Account number from Google, read back, two account checks, and "Is this Google account less than 3 days old?"
   ([S5b–S5d](flows-setup.md#s5-googles-reset-protection-step-5-of-8)). Setup guide Part H.
4. Test text to {new} ([S4d](flows-setup.md#s4-connect-and-privacy-step-4-of-8)): "Did {new} get it?"
5. **Hand the phone back to {name}**: "{name}, check with {new}" — the three ticks from S5d, plus "☐ This Google
   account is at least 3 days old." This last tick is required here (TPC), unlike at first setup.

Then:
- **Title:** Ready to start the wait
- **Body:** {change}. It happens after at least 24 hours of the phone being on. Until then, {new}'s PIN does
  nothing, and anyone holding the phone can cancel.
- **Primary:** Start the 24-hour wait → fresh PIN (M1, T1a) → [M3c](#m3c-started)
- **Secondary:** Don't start (discards everything {new} entered)

---

## M3. Changes that wait 24 hours

**Spec:** TPC "Waiting changes take 24 hours, one at a time, and are recoverable"; CHS notice line; C2, C6.

The four kinds, and the words used for each everywhere (U9, texts, history):

| Kind | `{change}` | Done (past tense, for texts) |
|---|---|---|
| Turning off | turn Plainphone off | Plainphone is now off on {user}'s phone. |
| Adding a trusted person | add {new} as a trusted person | {new} is now a trusted person for {user}'s phone. |
| Handing over | hand {name}'s place to {new} | {new} has taken {name}'s place for {user}'s phone. |
| Removing a trusted person | remove {other} as a trusted person | {other} is no longer a trusted person for {user}'s phone. |
| Supervised → self-managed | switch to {user} looking after this phone | {user} now looks after this phone. |
| Self-managed → supervised | switch to {new} looking after this phone | {new} now looks after this phone. |
| Self-managed: shorter wait | shorten the wait before opening up to {n} minutes | The wait before opening up is now {n} minutes. |
| Self-managed: more openings | allow {n} openings a day | You can now open up {n} times a day. |

(U9 already uses the first column, with slightly older wording for two rows; it is updated to this table.)

### M3a. Only one at a time

Shown when someone tries to start a second waiting change (TPC "One at a time").

- **Title:** Another change is waiting
- **Body:** Only one change can wait at a time. Waiting now: {change}. Cancel it, or wait until it's done, then
  start this one.
  If the other change is already being made: "Waiting now" becomes "Being made now", and "Cancel it, or" is left
  out.
- **Primary:** OK
- **Secondary:** See the waiting change → [M3d](#m3d-the-change-on-the-trusted-person-side)

### M3b. Starting

Every kind starts with the confirmations it already had, then a fresh PIN (M1 → T1a). The last confirmation always
names the wait:
"This happens after at least 24 hours of the phone being on. Time while the phone is off doesn't count."
Turning off has its own two confirmations ([L4](flows-limits-and-off.md#l4-turning-plainphone-off)).

If saving fails, nothing starts: "This didn't start. Nothing has changed. Try again." **OK**. (TPC "If the write
fails, the change SHALL NOT start.")

### M3c. Started

- **Title:** Waiting 24 hours
- **Body:** {change}: this happens after about {time}, if the phone stays on. {everyone} will get a text now.
  Anyone holding the phone can cancel it until then, from the home screen.
- **Primary:** OK
- **Notes:** `{time}` as on the home line ("21:15 tomorrow"). "Get a text now" is true once queued; delivery is
  shown in History ([M8](#m8-history)).

### M3d. The change, on the trusted-person side

On the trusted-person front page, a waiting change is the **first** "Needs you" item: **"Waiting: {change}"**.
It opens:

- **Title:** A change is waiting
- **Box:** **{change}.** Started with {name}'s PIN, {day} at {time}.
- **Body:** It happens after about {time}, if the phone stays on. Time while the phone is off doesn't count.
- **Primary:** Cancel this change → U9b, then U9d. (The trusted person already entered a PIN to get here; Cancel
  itself needs none.)
- **Secondary:** Skip the wait → [M3e](#m3e-skip-the-wait-with-a-recovery-code) (supervised mode only; never in
  self-managed mode)
- **When it is being made:** title **Already in progress**, body "This change is being made now. It can't be
  cancelled at this point." No Cancel, no Skip. If it is stuck, [M4](#m4-a-change-is-stuck) instead.

### M3e. Skip the wait with a recovery code

**Spec:** TPC "Skip"; TPC "Recovery code has its own counter" (skip only through a screen that names the change);
C2. Supervised mode only.

1. **Title:** Skip the wait and turn Plainphone off now? (for turning off). For other kinds: "Skip the wait and
   {change} now?"
   **Body:** Skipping needs your recovery code, not your PIN. You'll then choose a new PIN and get a new recovery
   code. {everyone} will get a text saying you skipped the wait.
   **Primary:** Enter recovery code · **Secondary:** Don't skip
2. The recovery-code screen (T1c, M1c), titled "{name}'s recovery code".
3. **Title:** Skip the wait? **Body:** {change} will start now. It can't be cancelled after this.
   **Primary:** Yes, skip the wait · **Secondary:** No, keep waiting
4. Choose a new PIN (typed twice), then the new recovery code, shown once (S3b).
5. **Title:** Started. **Body:** {change} is being made now. **Primary:** OK.
   For turning off, this goes straight to L4d ("Turning Plainphone off…").

- **Notes:** steps 3–4 are saved together in one write (TPC). Until then, nothing is skipped and the old PIN and code
  still work. If the phone restarts in between, the wait is still on and the old PIN and code work.
- **Who can skip:** either trusted person, including the one being removed (it's their code, and their own
  authority; C2).

### M3f. When Plainphone cancels it itself

Before the change is made, if a PIN it depends on is replaced, or the people or the mode change, Plainphone cancels
it at once (TPC "Dependencies"). The user sees only that the line goes away. Texts in [M9](#m9-texts). In
History: "Cancelled: the PIN that started it was changed" or "Cancelled: the trusted people or the set-up changed".

---

## M4. A change is stuck

**Spec:** TPC "Changing who the trusted people are" ("Completing", "Stopping a stuck change"); DL "Reset protection
names…"; C3.

When a change of people or mode can't update Google's reset protection, it stays half-way and keeps trying. The
people in charge don't change.

**"Needs you" item (exact):** Reset protection not updated – call the helper

- **Title:** Reset protection not updated – call the helper
- **Body:**
  Plainphone is trying to {change}, but Google's reset protection didn't accept the new list yet. Plainphone keeps
  trying.
  Until this works, nothing changes: the trusted people are still {everyone}, with the same PINs. Calls, texts and Emergency work.
  Until this is fixed, we can't say whose Google account a wipe would ask for.
- **Primary:** OK
- **Secondary:** Stop this change → fresh PIN (M1 → T1a) → "Stop this change? Plainphone puts reset protection back
  for {everyone}. {new}'s details are thrown away." **Stop it** / **Keep trying** → "Stopping…" → "Stopped. Nothing
  changed." **OK**.
- **Notes:**
  - "Stop this change" shows only while it can still be stopped (C3). Once the new people are in charge, it's gone.
  - If stopping can't finish: "Still stopping. Plainphone keeps trying, and won't start the change again. Call
    {helper}." **OK**. The item stays.
  - The PIN-free Cancel never stops a stuck change. Only a current trusted person's PIN does (in self-managed mode,
    the user's PIN).
  - The user sees "Plainphone needs attention" on the home screen (U3), with no detail beyond "Plainphone is
    finishing a change and needs {helper} to look at it." (new U3 `{reason}` row).
  - **Supervised → self-managed is stuck:** the body is as above. The Stop confirmation says "{user}'s new PIN is
    thrown away" instead of "{new}'s details are thrown away".

### M4a. Self-managed → supervised is stuck

[PLA-130](/PLA/issues/PLA-130). There are no trusted people yet: the user still holds the PIN, and {new} has no say
until the change is done. So M4 changes for this one kind. The item shows at the top of **Your settings**
([M7a](#m7a-your-settings)), with the same exact text, and the user's PIN opens it.

- **Title:** Reset protection not updated – call the helper
- **Body:**
  Plainphone is trying to switch to {new} looking after this phone, but Google's reset protection didn't accept
  {new}'s account yet. Plainphone keeps trying.
  Until this works, nothing changes: you still look after this phone, with the same PIN, waits and limits. Calls,
  texts and Emergency work.
  Until this is fixed, we can't say whose Google account a wipe would ask for.
- **Primary:** OK
- **Secondary:** Stop this change → your PIN (T1a, self-managed) → "Stop this change? Plainphone puts reset
  protection back for your Google account. {new}'s PIN and details are thrown away, and you keep looking after
  this phone." **Stop it** / **Keep trying** → "Stopping…" → "Stopped. Nothing changed. You still look after this
  phone." **OK**.
- **Notes:** the other M4 notes apply ("Still stopping…" unchanged). {new}'s PIN can't stop it: it does nothing
  until the change is done. **No texts** for stuck or stopped in this case: there are no trusted people to send
  them to, and {new} gets no texts until the change is done ([M9](#m9-texts),
  [Open question 4](#open-questions-for-the-lead)).

### Repair (credential store damaged)

**Spec:** TPC "Lost operation record" (Repair); C2.

When Plainphone can't even read who is in charge, it holds still and shows "Plainphone needs attention".

- **Trusted-person "Needs you" item:** Plainphone needs repair
- **Title:** Plainphone needs repair
- **Body:** Plainphone couldn't finish checking who looks after this phone, so it's holding everything as it is.
  Calls, texts and Emergency work. Repair tries again and shows what went wrong. It doesn't change anything else.
- **Primary:** Repair → PIN → "Repaired. {summary from M5}." or "Repair didn't finish: {plain reason}. Call
  {helper} and show this screen."
- **Secondary:** Details for the helper

---

## M5. After an interrupted change

**Spec:** TPC "Lost operation record" ("text every trusted person the people, mode and state it recovered. The
text SHALL NOT say the change was cancelled or didn't finish"); TPC scenario "Record lost after the credential
patch"; C2 (draft); PLA-35 R2.

This shows when Plainphone lost its record of a change part-way, and set itself back to a known state from what it
could read. It **never** guesses whether the change happened. It says who is in charge now.

### M5a. Trusted-person "Needs you" item and page (one time, until OK)

- **Item:** Plainphone recovered from an interrupted change
- **Title:** Plainphone recovered from an interrupted change
- **Body:**
  Trusted people now: {everyone}. (self-managed: "{user} looks after this phone.")
  Plainphone is on, with all its protections. If you were changing something, check whether it happened before
  you start it again.
- **Primary:** OK
- **Secondary:** See trusted people → M2a

### M5b. If turning off had already finished

Plainphone can tell only one thing for certain: if it is no longer in charge of the phone, turning off finished.
It shows L4e ("Plainphone is off") and sends the "off" text in M9. Nothing else.

### M5c. The user

No screen of its own. The home line, if it was showing a waiting change, goes away. The user never sees "recovered".

**Words never used on M5:** "cancelled", "didn't finish", "failed", "lost", "undone". Those would be guesses (R2).

---

## M6. Switching mode

**Spec:** TPC "Switching between modes is a waiting change in both directions"; GS (self-managed account checks);
C5.

### M6a. Supervised → self-managed (started by a trusted person)

From M2a, **Switch to {user} looking after this phone**.

1. **Title:** Let {user} look after this phone?
   **Body:**
   {user} will hold the PIN instead of {everyone}. Plainphone keeps everything as it is, but {user} can open
   things up after a 10-minute wait, twice a day. It's friction, not a lock: {user} can always remove it by waiting
   24 hours, or by wiping the phone.
   This waits 24 hours of the phone being on. {everyone} will get a text now, and anyone can cancel until then.
   **Primary:** Continue · **Secondary:** Not now
2. **Hand the phone to {user}.** {user} sets a PIN and gets a recovery code (S3a–S3b, worded for the user: "You'll
   use it to open things up and change your settings").
3. {user}'s account number from Google (S5b–S5d, for {user}'s own account; setup guide Part H).
4. Fresh PIN from a trusted person → M3c.

Until it completes, {user}'s new PIN does nothing. A trusted person's recovery code can skip it (M3e).

### M6b. Self-managed → supervised (started by the user)

From "Your settings" ([M7a](#m7a-your-settings)), **Let someone look after this phone**.

1. **Title:** Let someone look after this phone?
   **Body:**
   A trusted person will hold the PIN instead of you, and add apps with you.
   This waits 24 hours of the phone being on, and nothing can make it shorter. Until then, your waits and limits stay
   as they are, and the new person's PIN does nothing.
   **Primary:** Continue · **Secondary:** Not now
2. The new person sets up: M2c steps 1–4, in their hands.
3. **Hand the phone back to {user}**: the four ticks from M2c step 5, about {new}'s account.
4. Your PIN → M3c, without "{everyone} will get a text" (there's nobody to text yet).

- **Notes:** no skip, in any form (C5). U9 shows "You asked to switch to {new} looking after this phone", and the user
  can cancel.

### M6c. A user on a supervised phone

There's no switch for them, because they have no PIN. If they ask ("can I look after it myself?"), About this phone
says: "To look after this phone yourself, ask {tps}. It takes 24 hours."

---

## M7. Self-managed mode

**Spec:** TPC "Self-managed mode is friction, not a lock"; TPC "Elevation in self-managed mode is a single-use
grant…"; TPC session contract (custody wording); CHS (rows 3 and 4); C4.

The same calm phone. The user holds the PIN. The band on these screens reads **"Your settings"**
([design-tokens.md](design-tokens.md#trusted-person-mode-band)). "Opening up" is the user's word for an elevation:
the Play Store, full phone settings, adding a hidden app, or a Plainphone update, for a few minutes.

### M7a. Your settings

More shows **Your settings** where a supervised phone shows "For {tp}", and **Add an app** where it shows "Ask for an
app" (TPC "App requests": no request flow). Both ask for the user's PIN.

- **Title:** Your settings
- **Line under the title, always:** You hold the PIN. Plainphone adds waits so that distraction takes effort. It's
  friction, not a lock.
- **Openings line:** "{left} of {limit} openings left" (or "No openings left until about {time}").
- **Menu:**

  | Row | Small line | Opens |
  |---|---|---|
  | **Add an app** | Opens the Play Store, after a {wait}-minute wait | M7b (Play) |
  | **Add a hidden app** | An app already on the phone, after a {wait}-minute wait | M7b (Approve) |
  | **Apps on this phone** | Order, remove, what each can use | T5. Remove is instant. |
  | **Check for updates** | Last done: {date} | M7b (Play), then P3 |
  | **Open full phone settings** | For 5 minutes, after a {wait}-minute wait | M7b (Settings) |
  | **Waits and limits** | Wait {wait} minutes · {limit} a day | M7g |
  | **History** | The last 90 days | M8 |
  | **What Plainphone can't stop** | | L1, self-managed version |
  | **Install a Plainphone update** | Only with {helper}, when {helper} asks | M7b (Update), then P6 |
  | **Change PIN** | | T1-style |
  | **Let someone look after this phone** | Takes 24 hours | M6b |
  | **Turn Plainphone off** | Takes 24 hours | L4 |

- **Primary:** Done – back to the home screen

### M7b. Before opening up

- **Title (by kind):** Open the Play Store? · Open full phone settings? · Add a hidden app? · Install a Plainphone
  update?
- **Body:**
  First there's a {wait}-minute wait. Then you tap Start, and {kind} is open for {deadline} minutes.
  This uses 1 of your {left} openings left today. Restarting the phone stops the wait, and nothing is used up.
- **Primary:** Start the wait → PIN → U10a
- **Secondary:** Not now
- **Add a hidden app** first shows the list of hidden apps to pick one (name and developer as Android reports them).
  The picked app is named on U10b and on the add screen. The grant can only add that app.
- `{kind}` (U10): "the Play Store", "full phone settings", "adding {app}", "a Plainphone update". `{deadline}`: 10, 5,
  5, 10.

### M7c. No openings left

TPC scenario "Daily limit reached".
- **Title:** No openings left for now
- **Body:** You've used your {limit} openings. The next one is available after about {time}, if the phone stays on.
  Restarting the phone doesn't change this.
- **Primary:** OK
- With the limit set to 0: "Openings are set to 0 a day. You can change that in Waits and limits; making it higher
  takes 24 hours."

### M7d. One at a time

TPC "At most one countdown or grant". "**Something is already opening.** {kind} is counting down (or: is open
until {time}). Finish or stop that first." **OK** · **See it** → U10a.

### M7e. Countdown and Start

U10a and U10b in [flows-user.md](flows-user.md#u10-opening-up-self-managed-mode-only). Start confirms the kind again
(TPC step 2). If Start can't save: "This didn't open. Nothing was used up. Try again." **OK**.

### M7f. While it is open

The session screens (P2) with self-managed custody wording, and no "stay with the phone":

- **Notification / return bar:** Plainphone closes this at {time}
- **Back in Plainphone after the Play Store** (Play grant): "**Add what you installed.** You can add each app you
  installed until {time}." Then T4 for each app. After {time}: "**Too late to add {app}.** The time ran out, so
  {app} stays hidden. You can add it with another opening." **OK** (TPC "Approval after the deadline").
- **Add a hidden app:** T4 for the picked app. A second app can't be added on the same opening: "**This opening is
  used up.** It was for one app. Use another opening to add {app}." **OK**.
- **Plainphone update:** P6. If the update isn't ready by {time}: "**The update didn't install.** It wasn't ready
  before {time}. Nothing changed. Try again with another opening." **OK**.
- **After closing**, the protected-state check as usual (P4/P5). Not ready: "Not ready – call the helper" (the
  spec's non-handback state, worded the same in both modes).

### M7g. Waits and limits

TPC "Never permanent"; C4 table "Stricter at once, looser with friction".

- **Title:** Waits and limits
- **Body:** Longer waits and fewer openings start straight away. Shorter waits and more openings take 24 hours of
  the phone being on, like other changes that open things up.
- **Wait before opening up:** − / + buttons, 10 minutes to 24 hours.
- **Openings a day:** − / + buttons, 0 to 3.
- **Primary:** Save → PIN →
  - stricter: "Saved. This applies now, including to a wait that's already running." **OK**;
  - looser: M3b/M3c with `{change}` from the M3 table.
- **Notes:** no setting removes the wait or the limit, or makes an opening last longer. The − button stops at 10
  minutes and the + at 3, without explanation.

### M7h. Recovery code

T1c, with one more line on the code screen: "This code only resets your PIN. It doesn't skip any wait." If a change
is waiting: "Setting a new PIN cancels the change that's waiting: {change}." (TPC scenario "Recovery code in
self-managed mode").

### M7i. Monthly check

The home line is **Time for your monthly phone check**. It opens U2 with: "Once a month, update your apps so they
keep working well. It takes about 10 minutes and uses one opening of the Play Store. Nothing is wrong with your
phone." **Primary:** Start the check → M7b (Play) → P3. **Secondary:** Later.

---

## M8. History

**Spec:** TPC "Texts go to every trusted person, and actions are kept in a history list" (90 days; who, what, when,
text status; no app content, URLs or message text); C1.

Trusted-person menu row **History**, small line "Changes on this phone, last 90 days". Self-managed: in Your
settings.

- **Title:** History
- **Line under the title:** Changes made on this phone in the last 90 days. It doesn't include messages, websites or
  what happens inside apps.
- **One row per entry, newest first:**

  ```
  Thu 8 Oct, 21:40
  Added Spotify
  With Sam's PIN
  Texts: Sam ✓  Jo – waiting
  ```

  - Line 2, the action: "Added {app}", "Removed {app}", "Changed what {app} can use", "Opened the Play Store",
    "Opened full phone settings", "Installed a Plainphone update", "Started: {change}", "Cancelled: {change}",
    "Skipped the wait: {change}", "Done: {change}", "Stopped: {change}", "Recovered from an interrupted change",
    "Opened up: {kind}" (self-managed).
  - Line 3, the credential: "With {name}'s PIN", "With {name}'s recovery code", "On the phone, without a PIN"
    (cancels), "By a text from {name}" (cancel replies), "By Plainphone" (cancelled on its own, recovered). Never
    "by {name}": a PIN shows who knew it, not who typed it (C1, "Authority is a credential").
  - Line 4, each recipient: ✓ sent, "– waiting", "– didn't send". Words and ticks, no colour.
- **Primary:** Done
- **Notes:** a record, not a control: rows can't be deleted or tapped to undo. Ignored cancel replies (wrong code)
  appear as "A text said CANCEL with an old code. Nothing changed." With line 3 "From {name}'s number".

---

## M9. Texts

**Spec:** TPC "Texts go to every trusted person…" (every trusted person, including the one who acted; waiting-change
texts at once); TPC "Waiting changes" (start text; cancel code only if Q15 passes; lost-record text); C1, C2.

Every text goes to **every** trusted person. The person whose PIN or code was used gets the **receipt** version,
which says "your" and adds what to do if it wasn't them. In self-managed mode there are no trusted people, so none
of these are sent.

`{reply}` is ", or reply CANCEL {code}" **only if** Q15 passes. Otherwise it is empty, and no text or screen mentions
replies (tasks.md 1.1).

| When | To the others | Receipt (to {name}) |
|---|---|---|
| A waiting change starts | `Plainphone: {name}'s PIN was used to start a change on {user}'s phone: {change}. It will happen after at least 24 hours of the phone being on. To stop it, open the phone and tap Cancel{reply}.` | `Plainphone: your PIN was used to start a change on {user}'s phone: {change}. It will happen after at least 24 hours of the phone being on. If that wasn't you, tap Cancel on the phone and change your PIN.` |
| …turning off (start) | `Plainphone: {name}'s PIN was used to start turning Plainphone off on {user}'s phone. Plainphone will turn off after at least 24 hours of the phone being on. To stop it, open the phone and tap Cancel{reply}.` | as above, "your PIN", with "If that wasn't you, tap Cancel on the phone and change your PIN." |
| Cancelled on the phone, no PIN | `Plainphone: someone holding {user}'s phone cancelled this change: {change}. Nothing changed.` | (same to all) |
| Cancelled with a PIN | `Plainphone: {name}'s PIN cancelled this change on {user}'s phone: {change}. Nothing changed.` | `…your PIN cancelled…` |
| Cancelled by a reply (Q15 only) | `Plainphone: a text from {name}'s number cancelled this change on {user}'s phone: {change}. Nothing changed.` | `…a text from your number…` |
| Cancelled by Plainphone (a PIN it depended on changed) | `Plainphone: this change on {user}'s phone was cancelled because the PIN that started it was changed: {change}. Nothing changed.` | (same to all) |
| Cancelled by Plainphone (people or mode changed) | `Plainphone: this change on {user}'s phone was cancelled because the trusted people or the set-up changed: {change}. Nothing changed.` | (same to all) |
| Wait skipped | `Plainphone: {name}'s recovery code was used to skip the wait on {user}'s phone. This is happening now: {change}.` | `Plainphone: your recovery code was used to skip the wait on {user}'s phone. This is happening now: {change}. If that wasn't you, call {helper}.` |
| Done | `Plainphone: done on {user}'s phone. {done}` (`{done}` from the M3 table) | (same to all) |
| Stuck (M4) | `Plainphone: a change on {user}'s phone is stuck and needs {helper}: {change}. Nothing has changed yet: the trusted people are still {everyone}. Calls, texts and Emergency work.` | (same to all) |
| Stopped (M4) | `Plainphone: {name}'s PIN stopped this change on {user}'s phone: {change}. Nothing changed.` | `…your PIN stopped…` |
| Recovered (M5) | `Plainphone recovered from an interrupted change on {user}'s phone. Trusted people now: {everyone}. Plainphone is on, with all its protections. If you were changing something, check on the phone whether it happened before you start it again.` | (same to all) |
| Recovered, already off (M5b) | `Plainphone: Plainphone is now off on {user}'s phone.` | (same to all) |
| App added, removed, permissions changed | `Plainphone: {name}'s PIN was used on {user}'s phone: {action}.` (`{action}` = M8 line 2, e.g. "Added Spotify") | `Plainphone: your PIN was used on {user}'s phone: {action}. If that wasn't you, change your PIN.` |
| Play Store or settings opened | `Plainphone: {name}'s PIN opened {kind} on {user}'s phone for {n} minutes.` | `…your PIN opened…` with "If that wasn't you, change your PIN." |
| Plainphone update installed | `Plainphone: {name}'s PIN installed a Plainphone update on {user}'s phone.` | `…your PIN installed…` |
| 10 wrong entries of one PIN | `Plainphone: {name}'s PIN on {user}'s phone was entered wrong 10 times. If that wasn't {name}, have a look together when you can.` | `Plainphone: your PIN on {user}'s phone was entered wrong 10 times. If that wasn't you, have a look together when you can.` |

**Stuck and Stopped when switching from self-managed to supervised** ([M4a](#m4a-self-managed--supervised-is-stuck)):
neither text is sent. "The trusted people are still {everyone}" would be untrue, and there is nobody to send it to.
The user sees M4a on the phone. When the change is done, the Done text goes to {new} as usual.

**Texts in [README.md](README.md#texts-plainphone-sends) that change with two people:** the app request ("To add it,
one of you needs to be with {user} and the phone. On the phone: More, then 'For {everyone}'.") and the monthly
check ("…with {user}'s phone in your hands…", unchanged) go to everyone. The rest are unchanged; "to {tp}" means
every trusted person.

**Receipts go out straight away.** C1 allows routine receipts to be combined into one daily text. I recommend not
doing it for the MVP: the receipt is how someone notices their PIN was used, so a day's delay costs the one thing
it is for. Volume is small (a few a month). See [Open question 2](#open-questions-for-the-lead).

**Length.** Waiting-change texts can run past 160 characters with long names; Android sends them as one message in
parts. No text depends on being one part.

---

## M10. Battery and the clock

**Battery guard** (DL "Every unhide is saved at once"; C7). When a session, an opening, adding an app or turning off
would start below 5% and not charging:
- **Title:** Charge the phone first.
- **Body:** The battery is below 5%. Plug the phone in, then try again. Calls, texts and Emergency still work.
- **Primary:** OK
- Never shown for anything that keeps 9-1-1 working: those repairs are never delayed.
- A turn-off whose wait ends below 5%: U9a adds "Plainphone will turn off once the phone is charging or above 5%."
  The home line keeps its wording.

**The clock** (TPC "Waits, countdowns and daily counts use one proven running-time clock"). Every screen that shows
a 24-hour wait or a "next opening" time says "Time while the phone is off doesn't count." (or "if the phone stays
on" next to an estimate). Times are estimates and never shown as a countdown on the user's screens
([README](README.md#principles-for-every-screen) principle 4).

---

## Open questions for the Lead

1. **One trusted person: show "Who are you?"** The spec says the PIN screen asks which trusted person is entering
   it. With one person there is no choice to make. **Recommendation:** skip M1a when there is one trusted person or
   in self-managed mode. Nothing is checked differently.
2. **Daily batching of receipts.** C1 allows it. **Recommendation:** send each receipt at once (M9).
3. **Texts in self-managed mode.** With no trusted people, nobody gets waiting-change texts, and Open Question 7
   ("a notice-only contact") is "not now". The helper number from setup still gets device texts (unknown phone
   software, needs the helper). **Recommendation:** keep a helper number in self-managed setup (optional), use Flag
   4's wording, and send nothing else.
4. **Self-managed → supervised: does the incoming person get texts during the wait?** They have no authority yet,
   and the spec sends texts to trusted people only. **Recommendation:** no, for the MVP; the user and the new person
   set it up together, in person.
5. **Cancel code format.** **Recommendation:** 5 digits, never reused on the phone (TPC), no letters, so
   autocorrect can't change it. AE's call.
