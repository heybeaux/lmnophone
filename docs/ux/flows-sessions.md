# Supervised sessions, the monthly check, and Plainphone updates

Words, rules and placeholders are in [README.md](README.md).

A **supervised session** is the only time Plainphone opens up the phone (trusted-person-controls, "Supervised
sessions follow one custody contract"; design D2). On screen it is never called a "session". It is called what it
does: "Open the Play Store for 10 minutes" or "Open full phone settings for 5 minutes".

The idea the copy must get across, in this order:
1. The phone is open for a short time, so **stay with it**.
2. It closes by itself if you put it down, a call comes in, or time runs out.
3. Some pages in there can show the web.
4. Plainphone checks everything before you hand it back.

---

## P1. Before a session

**Spec:** trusted-person-controls (custody contract items 1, 3, 7; disclosure of web content in sessions).

| | Play Store | Full phone settings |
|---|---|---|
| **Title** | Open the Play Store for 10 minutes | Open full phone settings for 5 minutes |
| **Body, block 1** | Stay with the phone until you tap Done. | Stay with the phone until you tap Done. |
| **Body, block 2** | Search for the app, tap **Install**, and wait until it says **Open**. Then go back to the home screen (swipe up from the bottom of the screen). Plainphone checks the phone, then shows **Done**. | Make the change, then go back to the home screen (swipe up from the bottom of the screen). Plainphone checks the phone, then shows **Done**. |
| **Body, block 2b** | The Play Store also closes if the screen turns off, a call comes in, or after 10 minutes. | Settings also closes if the screen turns off, a call comes in, or after 5 minutes. |
| **Body, block 3** | Play Store help and account pages can show web pages. Don't hand the phone over while it's open. | Settings help and account pages can show web pages. Don't hand the phone over while it's open. |
| **Body, block 4** (only for Settings) | — | Some changes get put back afterwards, such as the texting app or the keyboard. Ordinary changes, like ringtone or Wi-Fi, are kept. |
| **Primary** | Open the Play Store | Open settings |
| **Then** | PIN (fresh) | PIN (fresh) |

"Stay with the phone until you tap Done." is the exact sentence from the spec. It is block 1, on its own line, in
bold.

For the monthly check, P1's Play Store version is replaced by [P3b](#p3b-start-the-check).

## P2. During a session

Plainphone cannot draw over the Play Store or Settings, so it uses one ongoing notification:

- **Notification:** "Play Store open · {n} min left · Tap here to finish" (or "Settings open · …"). The first draft
  said "when you're finished", and the cold reader couldn't tell whether that or "Done" was the step that counted.
- At 2 minutes left, the same notification updates to: "Play Store closes in 2 minutes".
- Tapping it, or pressing Home, ends the session and goes to P4.

The notification is the only way Plainphone talks during a session. No sounds. The AE confirms in Q1 that the
notification shows while lock task is on ([Open question 5](README.md#open-questions-for-the-lead)).

## P3. Monthly check

**Spec:** approved-apps ("Approved apps keep receiving updates, and the monthly check cannot silently lapse");
design D3.

### P3a. What starts it

- The text on the due date ([texts table](README.md#texts-plainphone-sends)).
- "Monthly check due" in "Needs you" on the front page (T2), from the due date.
- After 7 days overdue, the user's home line "Ask {tp} to do the monthly phone check".

### P3b. Start the check

- **Title:** Monthly check
- **Body:**
  You'll open the Play Store's list of updates and update anything that's waiting. Afterwards, Plainphone asks
  which apps didn't update. About 10 minutes. Updates carry on even after the Play Store closes.
  **Stay with the phone until you tap Done.**
  In the Play Store: tap your Google picture or initial at the top right, then **Manage apps & device**, then
  **Updates available**. Tap **Update all** if you see it.
- **Primary:** Start the check
- **Secondary:** Not now
- **Then:** PIN (fresh), then the Play Store opens (a 10-minute Play session).
- **Notes:** the Play Store steps quote Play's own labels as of October 2026. They change; the AE checks them on
  the target build in Q2 and updates this screen. If the Play services 30-day condition applies, add one line:
  "Google Play services hasn't updated in 30 days. This is often fine. If it's still the same next month, mention
  it to {helper}." Never say the phone is unsafe.

### P3c. Back from the Play Store

- **Title:** Did everything update?
- **Body:** Look at what the Play Store showed you.
- **Primary:** Yes, all up to date
- **Secondary:** Some apps didn't update → P3d

### P3d. Which apps didn't update?

- **Title:** Which apps didn't update?
- **Body:** Tick each one. They'll stay on your list until they're sorted.
- A checklist of the apps on this phone (Phone and Messages included, plus "Google Play services" and "Something
  else").
- **Primary:** Next

### P3e. Confirm the check

- **Title:** Confirm the monthly check
- **Body:** Enter your PIN to confirm you looked at the Play Store's update list. The next check will be due on
  {date}.
- **Primary:** Confirm
- **Then:** PIN (fresh). The check counts as done only now (approved-apps, "Completion"). The home line goes
  away. Then P4.
- **If the session ended before this** (screen off, time ran out, a call): the check is **not** recorded.
  P4 runs as usual, then: "The monthly check isn't finished. You can start it again from the front page."

## P4. Closing: checking the phone

**Spec:** custody contract items 4 and 8; the protected-state check.

### P4a. Checking

- **Title:** Checking the phone…
- **Body:** This takes a few seconds. Keep the phone with you.
- No buttons. A simple progress indicator, no percentage.

### P4b. All good

- **Title:** Everything is back in place
- **Body:** The Play Store is closed again. You can hand the phone back to {user}.
  If anything was put back, one line per item, for example: "The texting app was changed, so Plainphone put
  Messages back. Your other changes were kept."
- **Primary:** Done
- **Notes:** this is the "Done" that the session copy refers to. Tapping it returns to the home screen. If the
  session ended because the screen turned off, this screen appears the next time the phone is unlocked.

Plain names for restored items (from the protected-state table):

| Invariant | Line on P4b |
|---|---|
| Default SMS | "The texting app was changed, so Plainphone put Messages back." |
| Permitted input methods | "A keyboard was added, so Plainphone switched it off. The usual keyboard still works." |
| Permitted accessibility services | "An accessibility app was switched on, so Plainphone switched it off." |
| Approved apps' permissions | "{app}'s permissions were changed, so Plainphone set them back to what you chose." |
| Plainphone `SEND_SMS` | "Plainphone's permission to text you was changed. It's been put back." (if repair fails: T8) |
| Browser role, restrictions, lock task, never-hide, alerts | Never shown on P4b. If repaired silently, nothing to say; if repair fails, P5. |

## P5. Not ready to hand back

**Spec:** "non-handback state": the trusted person sees "Not ready to hand back – call the helper".

```
┌──────────────────────────────┐
│ Not ready to hand back –     │
│ call the helper              │
│                              │
│ Something didn't go back the │
│ way it should. Keep the      │
│ phone with you and call      │
│ Beaux.                       │
│                              │
│ Calls, texts and Emergency   │
│ still work.                  │
│                              │
│ Details for the helper ›     │
│ ┌──────────────────────────┐ │
│ │       Check again        │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

- **Title:** Not ready to hand back – call the helper (exact)
- **Body:** Something didn't go back the way it should. Keep the phone with you. Tap **Check again** once; if this
  screen comes back, call {helper}, your helper.
  Calls, texts and Emergency still work.
- **Primary:** Check again
- **Secondary:** Details for {helper} → the failed rows by name (this is the one screen where invariant names
  such as "Lock-task packages" may appear, under a heading "For {helper}").
- **Notes:** this screen comes back after every unlock until a check passes. Home still goes to the home screen
  (Emergency must always be reachable), but the home screen shows "Plainphone needs attention". If the emergency
  alert readback is unavailable on this build, the helper details say "Emergency alert settings can't be checked
  on this phone software" and the phone is held by the helper; it is never shown as passing.

When `{helper}` and `{tp}` are the same person, the body reads "Keep the phone with you. You'll need a computer to
fix this; see the setup guide, 'When something goes wrong'."

## P6. Install a Plainphone update

**Spec:** trusted-person-controls ("Plainphone updates come from the helper's computer over local Wi-Fi…";
"No rollback"); design D6.

### P6a. Before you start

- **Title:** Install a Plainphone update
- **Body:**
  {helper} needs to be here with a computer on the same Wi-Fi. {helper} will tell you the address to type.
  Plainphone checks that the update really comes from the Plainphone team before installing it.
  If an update goes badly wrong, there's no undo. The fix is the last-resort reset in the setup guide.
- **Primary:** Next
- **Then:** PIN (fresh).

### P6b. The address

- **Title:** Type the address from {helper}
- **Body:** It looks like `192.168.1.20:8080`. {helper}'s computer shows it.
- **Text box:** "Address"
- **Primary:** Get the update

### P6c. Getting and checking

- **Title:** Getting the update…
- **Body:** Keep the phone near the Wi-Fi. This can take up to 2 minutes.
- On success: P6d. On any failure, one of these (with **OK**; nothing has changed):

| Failure | Message |
|---|---|
| Can't reach the address, timeout, redirect | "Couldn't get the update from that address. Check it with {helper}. Nothing has changed." |
| Too big (over 100 MB) | "That file is too big to be a Plainphone update. Nothing has changed." |
| Wrong package, bad signature, wrong signer | "That file isn't a genuine Plainphone update, so it wasn't installed. Nothing has changed. Tell {helper}." |
| Same or older version | "This phone already has that version or a newer one. Nothing has changed." |

### P6d. Install

- **Title:** Install update {version}?
- **Body:** This update is from the Plainphone team. The phone will be busy for a minute. Calls and Emergency
  still work.
- **Primary:** Install
- **Then:** "Installing…" → Plainphone restarts → the handover self-check runs → "Plainphone is up to date"
  with **Done**, or P5 / "Plainphone needs attention" if the self-check fails.
