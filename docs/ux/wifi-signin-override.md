# Wi-Fi sign-in pages: two ways to allow them for 5 minutes

Owner: Product & UX Designer. Task: [PLA-19](/PLA/issues/PLA-19), for the Lead's recommendation in
[PLA-17](/PLA/issues/PLA-17). Status: **draft for the Lead and the Verifier.** This compares two variants. It
does not recommend one. Nothing here changes the MVP: until Beaux approves a recommendation, Wi-Fi that needs a
sign-in page stays unsupported (web-containment, design D4).

Words, rules and placeholders are in [README.md](README.md). Screen IDs (W0, WA1, WB1…) are for reviewers and
never appear on screen. On screen this is never called a "session", a "portal" or an "override". It is called
what it does: "the sign-in page".

## What the probe found, and what it means for the screens

From `spikes/wifi-signin/RESULT.md` ([PLA-18](/PLA/issues/PLA-18)). Emulator only, API 35, one run.

| Probe finding | What the screens must do |
|---|---|
| Q7: Plainphone can tell when the connected Wi-Fi needs a sign-in page. | The way in is only offered then. Both variants check again just before opening. |
| Q4: while it is open, **the sign-in page can reach the open web**: links, redirects, Google Search. Downloads and other apps are blocked. A phone-number link opens the Phone app. | Both start screens say so, plainly. The 5-minute limit is the only limit on browsing. |
| Q5: the page closes itself about 1 second after the Wi-Fi starts working. | The user doesn't need to find a "Done" on the sign-in page. Plainphone's "Connected" screen follows by itself. |
| Q5: "Use this network as is" and "Continue anyway" also close it, even though the Wi-Fi may still not work. | After closing, Plainphone checks the internet. If it doesn't work, it shows the existing "isn't reaching the internet" message ([W4](#w4-closed-but-the-wi-fi-still-doesnt-work)). |
| Q3: Plainphone can't open the sign-in page itself yet. Android's own "Sign in to Wi-Fi network" notification can open it. Once dismissed, that notification only comes back after the Wi-Fi reconnects. | The "Use sign-in page" button needs a working launch path. The copy for each possible path is in [How the page opens](#how-the-page-opens). |
| Q6a: if Plainphone stops mid-session, the page stays open with no time limit. Pressing Home restarts Plainphone, which closes the page within a second. | "Swipe up from the bottom to stop" is both the normal way out and the recovery. Both start screens say it. |
| Q6b: **one reboot mid-session sent the emulator into a boot loop.** | No wording fixes this. See [Open question 1](#open-questions-for-the-lead). |

## Who sees what

| | Variant A: {tp}'s PIN | Variant B: {user} starts it, with limits |
|---|---|---|
| Who starts it | {tp}, with the phone in {tp}'s hands, fresh PIN | {user}, no PIN |
| When it is offered | Only when the connected Wi-Fi needs a sign-in page | Same |
| How long | Up to 5 minutes. Ends as soon as the Wi-Fi works | Same |
| How often | No limit beyond the PIN | Suggested: 3 times in any 24 hours ([limits](#wb0-the-limits)) |
| What {tp} is told | Nothing: {tp} was there | A text each time, sent when there's signal, plus a list on {tp}'s front page |
| {user} alone, {tp} not there | **Doesn't work.** [WA2](#wa2-tp-isnt-here) says so and offers mobile data. An optional "trip pass" ([WA5](#wa5-optional-a-trip-pass-for-travelling-alone)) covers planned trips | Works |
| Spec change needed | web-containment (a session that shows the sign-in page) | web-containment, and trusted-person-controls / design D2 rule 1 (a session without the PIN), with a new ADR |

The ferry case is the hard one. On a ferry {user} is often alone, often has no mobile signal (which is why Wi-Fi
matters), and so **can't phone {tp} for help or receive a text**. Any design that needs {tp} at that moment fails
there.

---

## W0. The way in (both variants)

**Today** (flows-user U7, web-containment exact text):

> This Wi-Fi needs a sign-in page, which Plainphone doesn't allow. Use mobile data or another Wi-Fi.
> Primary: **Forget this network** · Secondary: OK

With either variant, "which Plainphone doesn't allow" is no longer true, so the spec sentence has to change.

### Where the message appears

1. On Plainphone's Wi-Fi screen (U7), straight after joining a network.
2. When the phone rejoins a known network by itself (the ferry on the next trip), as a **notice line** on the home
   screen: **This Wi-Fi needs a sign-in page**. "Plainphone needs attention" still wins if both apply. Tapping the
   line opens W0. The line goes away when the phone leaves that Wi-Fi or the Wi-Fi starts working.

Android also shows its own "Sign in to Wi-Fi network" notification. Outside the 5 minutes, tapping it does nothing
and it disappears (Q3a). Plainphone can't remove it. The W0 message is the one to follow.

### W0 copy

```
┌──────────────────────────────┐
│ This Wi-Fi needs a sign-in   │
│ page                         │
│                              │
│ "BCF Wi-Fi" asks you to sign │
│ in or accept terms on a page │
│ first. {Variant line}        │
│                              │
│ Or use mobile data or        │
│ another Wi-Fi. You can still │
│ call when the phone has      │
│ signal.                      │
│                              │
│ Forget this network ›        │
│ ┌──────────────────────────┐ │
│ │     Use sign-in page     │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

- **Title:** This Wi-Fi needs a sign-in page
- **Body, block 1:** "{network}" asks you to sign in or accept terms on a page first. Then the variant line:
  - **A:** {tp} can open the page for you for 5 minutes, with {tp}'s PIN.
  - **B:** You can open the page for 5 minutes. {tp} will get a text to say so.
  - **B, no tries left:** You've opened sign-in pages 3 times today. You can open one again at {time}.
- **Body, block 2:** Or use mobile data or another Wi-Fi. You can still call when the phone has signal.
- **Primary:** Use sign-in page (B with no tries left: no primary; **Forget this network** becomes the primary)
- **Secondary:** Forget this network
- **Back** (top left) closes the message. The old **OK** goes, to keep one secondary action.
- The three explainer lines under the U7 Wi-Fi messages stay as they are.
- **Spec sentence, proposed:** "This Wi-Fi needs a sign-in page." The rest is variant copy.

---

## How the page opens

The probe hasn't found how Plainphone opens the sign-in page itself (Q3c, untested). The screens differ by path.
The AE confirms which one works; the Lead picks.

| Path | What happens after the start screen | Extra copy |
|---|---|---|
| **L1.** Plainphone reconnects the Wi-Fi, Android shows its notification again, Plainphone opens it (untested) | The sign-in page appears | None |
| **L2.** Plainphone reconnects the Wi-Fi; the person taps Android's notification | A Plainphone screen: **Tap "Sign in to Wi-Fi network"** / "Pull down from the very top of the screen, then tap **Sign in to Wi-Fi network**. It can take up to 30 seconds to appear." If it hasn't appeared after 30 seconds: "It didn't appear. The sign-in page has closed. Try again from the Wi-Fi screen." (In B this try is not counted.) | As shown |
| **L3.** Plainphone shows the sign-in page in its own window, on that Wi-Fi only (untested) | The sign-in page appears inside Plainphone, with a thin bar at the top: "Wi-Fi sign-in · open until {time} · **Stop**" | The bar. Plainphone could also keep the page to the sign-in site; that is the AE's call |

L2 is the hardest for a stranger: it relies on a gesture and an Android label we don't control. If L2 is the path
we take, Beaux checks it on the real build on the spare Motorola.

## During the 5 minutes (both variants)

Plainphone can't draw over Android's sign-in page (L1, L2), so, as in Play and Settings sessions (P2), it uses one
ongoing notification:

- **Notification:** "Wi-Fi sign-in open until {time} · Tap here to stop"
- **With 1 minute left:** "Wi-Fi sign-in closes at {time}. Finish now, or tap here to stop."
- Tapping it, swiping up to the home screen, or Back out of the page ends it and goes to [W2](#w2-closing).
- A phone-number link on the page opens the Phone app. That also ends it. So does an incoming call.

This uses a clock time ("until 2:35"), not "{n} min left", because in variant B the person seeing it is {user},
and the user's screens have no countdowns (README, principle 4). The status bar clock is visible during the page.
No sounds.

---

## Variant A: {tp}'s PIN

### WA1. Start (after "Use sign-in page")

- **Title:** Open the sign-in page for 5 minutes
- **Body, block 1:** **Stay with the phone until you tap Done.**
- **Body, block 2:** Follow the steps on the page, for example accept the terms or type a room number. When the
  Wi-Fi works, the page closes by itself. To stop sooner, swipe up from the bottom of the screen.
- **Body, block 2b:** The page also closes if the screen turns off, a call comes in, or after 5 minutes.
- **Body, block 3:** Sign-in pages can show other websites while they're open. Don't hand the phone over while
  it's open.
- **Primary:** Hand to {tp} → the next screen
- **Secondary:** {tp} isn't here → WA2

Then a short hand-over screen (for {tp}):

- **Title:** {tp}, open the sign-in page?
- **Body:** {user}'s Wi-Fi needs a sign-in page. Your PIN opens it for 5 minutes.
- **Primary:** Enter PIN → T1a (fresh PIN). Then the page opens (see [How the page opens](#how-the-page-opens)).

If {user} is filling in the page with {tp} beside them, that is fine. "Stay with the phone" means {tp} stays
there until Done.

### WA2. {tp} isn't here

- **Title:** This needs {tp}
- **Body, block 1:** Only {tp} can open a sign-in page, with the phone in {tp}'s hands. {tp} can't do it from
  {tp}'s own phone.
- **Body, block 2:** Until then, mobile data works wherever there's signal. You can still call and text when the
  phone has signal.
- **Primary:** OK (back to the home screen)
- **Secondary:** Forget this network
- With the optional trip pass ([WA5](#wa5-optional-a-trip-pass-for-travelling-alone)) switched on, WA2 isn't shown;
  the B-style start screen is.

Plainphone should **never** suggest reading the PIN over the phone. The same PIN adds apps. Spoken PINs get
reused, and the [Open question 3](#open-questions-for-the-lead) asks whether A pushes people to do that.

### WA3. Closing (A)

[W2](#w2-closing), then [W3](#w3-connected) or [W5](#w5-time-ran-out-before-sign-in-finished), with **Done** as
the primary. Done is {tp}'s hand-back, as in P4b.

### WA4. Time ran out (A)

[W5](#w5-time-ran-out-before-sign-in-finished), with:
- **Primary:** Try again → WA1's hand-over screen and a fresh PIN.
- **Secondary:** Forget this network.

### WA5. Optional: a trip pass for travelling alone

This is an option the Lead can add to A for the ferry case. It keeps {tp}'s decision with the phone in {tp}'s
hands, but makes it **ahead of time**.

**In trusted-person mode** (a new row on T2, "Wi-Fi sign-in pages"):

- **Title:** Let {user} open sign-in pages
- **Body:** For trips. Until the date you pick, {user} can open Wi-Fi sign-in pages alone, for 5 minutes at a
  time, up to 3 times a day. You'll get a text each time. Sign-in pages can show other websites while they're
  open.
- **Choice:** "Until" with 1, 3 or 7 days (no longer). Shows the end date: "Until Sunday 11 October, 9 pm".
- **Primary:** Allow → T1a (fresh PIN). Then "Done. {user} can open sign-in pages until {date}." with **OK**.
- While a pass is on, T2 shows "Sign-in pages allowed until {date}" with **End now** (no PIN needed to end it).

**While the pass is on**, {user} sees variant B's screens and limits ([WB1](#wb1-start)–[WB5](#wb5-tps-front-page)).
When it ends, A's screens come back.

Not covered: a trip {tp} didn't plan for.

---

## Variant B: {user} starts it, with limits

### WB0. The limits

Suggested starting values. The Lead can change any number; the copy uses `{n}` and `{time}`.

| Limit | Suggested | Why |
|---|---|---|
| Only when the connected Wi-Fi needs a sign-in page | Always | Q7 shows Plainphone can tell. Checked again just before the page opens; if the Wi-Fi has changed, "This Wi-Fi doesn't need a sign-in page any more." with **OK** |
| Time per try | 5 minutes, ends as soon as the Wi-Fi works | Beaux's ask; Q5 shows a working sign-in ends it within about a second |
| Tries | **3 in any 24 hours** | One trip, a retry, the trip back. It keeps the open-web time to 15 minutes a day at most. Counted over 24 hours, not "today", so changing the phone clock doesn't reset it. The screens still say "today", which is plainer |
| What counts as a try | Every time the page opens | Simple to explain. In L2, a try where Android's notification never appeared is not counted |
| No extending, no back-to-back | A new try needs a new tap on W0 | Keeps each try a deliberate act |
| {tp} told | A text each time, plus a list on {tp}'s front page | {tp} knows without watching |
| {tp} can switch it off | In trusted-person mode, fresh PIN | {tp} stays in charge of the phone's rules |
| More tries | {tp} can open one more with the PIN (variant A's screens), when {tp} is there | The limit never blocks {tp} |

### WB1. Start

```
┌──────────────────────────────┐
│ Open the sign-in page for 5  │
│ minutes                      │
│                              │
│ Stay with the phone until    │
│ you tap Done.                │
│                              │
│ Follow the steps on the page.│
│ When the Wi-Fi works, the    │
│ page closes by itself.       │
│                              │
│ {tp} will get this text:     │
│ ┌──────────────────────────┐ │
│ │ Plainphone: a Wi-Fi      │ │
│ │ sign-in page was opened  │ │
│ │ on the trusted person's phone…       │ │
│ └──────────────────────────┘ │
│ Not now ›                    │
│ ┌──────────────────────────┐ │
│ │    Open sign-in page     │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

- **Title:** Open the sign-in page for 5 minutes
- **Body, block 1:** **Stay with the phone until you tap Done.**
- **Body, block 2:** Follow the steps on the page, for example accept the terms or type a room number. When the
  Wi-Fi works, the page closes by itself. To stop sooner, swipe up from the bottom of the screen. It also closes
  if the screen turns off, a call comes in, or after 5 minutes.
- **Body, block 3:** Sign-in pages can show other websites while they're open. This is try {n} of 3 today.
- **Body, block 4:** {tp} will get this text: then the exact text below, in a grey box (as in U6, sharing a link).
- **Primary:** Open sign-in page. Then the page opens (see [How the page opens](#how-the-page-opens)).
- **Secondary:** Not now

At 200% this screen scrolls; the primary stays pinned. It is the longest start screen in Plainphone. The order
puts "stay with the phone" and "how to finish" before the text box, so a reader who stops early still knows what
to do.

### WB2. The text to {tp}

New row for the [texts table](README.md#texts-plainphone-sends) and for "About this phone" (U8):

| When | To | Text |
|---|---|---|
| {user} opens a Wi-Fi sign-in page (variant B, or a trip pass) | {tp} | `Plainphone: a Wi-Fi sign-in page was opened on {user}'s phone, for up to 5 minutes. Sign-in pages can show other websites. This was {n} of 3 today. Nothing for you to do.` |

- **Sending:** the text is queued when the page opens, and sent as soon as the phone has signal. On a ferry with
  no signal it may arrive hours later. It retries for 7 days, like the monthly check text, and is never sent
  twice. The page **does not wait** for the text: waiting would make B useless where it's needed most.
- **No network name, place or time of day in the text.** A network name like "BCF Wi-Fi" or a hotel's name says
  where {user} is, and Plainphone promises that {tp} can't see where {user} is (U8). See
  [Open question 4](#open-questions-for-the-lead).
- "Nothing for you to do" is there so the text reads as information, not an alarm.

U8's sentence about texts gains: "…or when you open a Wi-Fi sign-in page."

### WB3. Closing (B)

[W2](#w2-closing), then [W3](#w3-connected) or [W5](#w5-time-ran-out-before-sign-in-finished), with **Done** as
the primary. {user} taps it.

### WB4. Time ran out (B)

[W5](#w5-time-ran-out-before-sign-in-finished), with:
- **Primary:** Try again (if tries are left) → WB1, which shows the new count ("This is try 2 of 3 today").
- **Secondary:** Forget this network.
- **No tries left:** no Try again. The body adds: "You can open one again at {time}. If {tp} is with you, {tp}
  can open it with the PIN." Primary: **OK**.

### WB5. {tp}'s front page

In trusted-person mode (T2), a new row: **Wi-Fi sign-in pages**. It opens:

- **Title:** Wi-Fi sign-in pages
- **Body:** {user} can open a sign-in page for 5 minutes, up to 3 times a day. You get a text each time.
- **List:** "Opened {n} times in the last 30 days." Then one line per time: "Thu 8 Oct, 2:30 pm, about 4 minutes".
  No network names (same reason as WB2). Kept for 30 days.
- **Switch:** "Let {user} open sign-in pages" on/off. Changing it needs a fresh PIN.
- **Primary:** Done

{user} can see the same list from "About this phone" (U8 → "Wi-Fi sign-in pages used"). Nothing on the list is
hidden from {user}. That keeps it support, not surveillance.

**Default for the switch:** set during setup. One extra question in step 7 of setup, asked of {tp}:
"Let {user} open Wi-Fi sign-in pages alone? For hotels, ferries and hospitals. Up to 3 times a day, 5 minutes
each. You'll get a text each time." Yes / Not now. (A setup change; see [Open question 5](#open-questions-for-the-lead).)

---

## Shared closing screens

### W2. Closing

Same as P4a: **Checking the phone…** / "This takes a few seconds." No buttons. The protected-state check runs as
after any session, even though the sign-in page can't change settings: one rule for every session. After the
check, Plainphone tests whether the Wi-Fi reaches the internet, then shows W3, W4 or W5.

If the check fails: P5, "Not ready to hand back – call the helper", unchanged. In variant B, {user} sees P5; its
body already tells the reader to keep the phone and call {helper}.

### W3. Connected

- **Title:** Connected to {network}
- **Body:** The sign-in page has closed. The Wi-Fi is working.
- **Primary:** Done → home screen
- Shown when the page closed because the Wi-Fi started working.
- If the page closed for another reason (screen off, a call, Home) and the Wi-Fi works anyway, W3 shows too.
- If the screen turned off, W3 (or W4, W5) shows at the next unlock, as P4b does.

### W4. Closed, but the Wi-Fi still doesn't work

When the page closed by itself (Q5) but the internet still doesn't work, for example after "Use this network as
is":

- **Title:** This Wi-Fi isn't reaching the internet (the existing U7 message)
- **Body:** The sign-in page has closed, but the Wi-Fi still isn't working. To try the sign-in page again, forget
  this network and join it again.
- **Primary:** Forget this network
- **Secondary:** OK
- **Note:** Android now treats this Wi-Fi as "use as is", so it won't report a sign-in page again until the phone
  rejoins. That is why the copy says forget and rejoin. In B this used a try.

### W5. Time ran out before sign-in finished

- **Title:** The sign-in page closed
- **Body, block 1:** 5 minutes went by before the Wi-Fi started working. Anything you typed on the page isn't
  saved.
- **Body, block 2:** Or use mobile data or another Wi-Fi. You can still call when the phone has signal.
- **Primary / Secondary:** per variant (WA4, WB4).
- **If the Wi-Fi starts working after all** in the next minute (the sign-in went through just as time ran out),
  the screen changes to W3 by itself. Plainphone keeps watching that Wi-Fi; it doesn't reopen anything.
- The same screen, with "The sign-in page closed" and without the first sentence of block 1, is used when the page
  closed early (screen off, a call, Home) and the Wi-Fi isn't working.

---

## Changes this would need elsewhere

For whichever variant is chosen. Listed so the Lead can size it; none is made here.

| Where | Change | A | B |
|---|---|---|---|
| web-containment, "Wi-Fi networks that need a sign-in page are unsupported" | Allow a sign-in session; new exact message sentence (W0) | Yes | Yes |
| trusted-person-controls, custody contract; design D2 rule 1 | A third session kind; new close condition "the Wi-Fi works". B: a session started without the PIN, with limits | Yes | Yes, plus ADR and Beaux's re-approval |
| README word list | Add "portal", "override" and "captive" to "never on screen"; "the sign-in page" instead | Yes | Yes |
| README texts table, U8 | WB2 text; U8 sentence | Trip pass only | Yes |
| U1 notice lines | Third line, "This Wi-Fi needs a sign-in page" | Yes | Yes |
| T2 front page | Trip pass row (WA5) | Optional | WB5 row |
| Setup, step 7 | Switch default question | No | Yes |
| "What Plainphone can't stop" (flows-limits-and-off) | Replace "Wi-Fi with a sign-in page … won't work" with: "Wi-Fi sign-in pages — When a Wi-Fi needs a sign-in page, it can be opened for 5 minutes. While it's open, it can show other websites." | Yes | Yes |
| Human check | Beaux checks W0, WA1/WB1, W5, and L2 if used, on the real build on the spare Motorola. No outside testers for now (Telegram 4477) | Yes | Yes |

## Open questions for the Lead

1. **The reboot boot loop (Q6b).** If a reboot during the 5 minutes can stop the phone starting, no wording makes
   that acceptable for {user} alone on a ferry. Variant B makes it more likely: more sessions, with nobody
   technical nearby, and a frustrated person may hold the power button. A line such as "Don't turn the phone off
   while the page is open" is not a fix and isn't in the copy. This needs the AE's answer before either variant
   goes to Beaux.
2. **Launch path.** L1 and L3 are untested; L2 relies on Android's notification label and a pull-down gesture.
   The W0 → start → page flow depends on which one works.
3. **Will variant A push people to share the PIN?** In the alone case A has a dead end. A tired traveller who can
   phone {tp} may be told the PIN. That PIN also adds apps. The trip pass (WA5) is one answer for planned trips.
4. **Network name in the text and the list.** Left out on purpose (it shows where {user} is). If Beaux and the trusted person
   would rather see it, that is their choice, but U8 then needs to say "{tp} gets the Wi-Fi's name".
5. **B's default.** On or off after setup? The copy assumes {tp} is asked at setup. Off by default makes B behave
   like A until {tp} turns it on.
6. **Variant B and P5.** If the closing check fails in B, {user} sees "Not ready to hand back – call the helper"
   alone. The words still work ("keep the phone with you and call {helper}"), but "hand back" makes less sense
   when nobody handed the phone over. A B-specific title, "Plainphone needs the helper", would read better; it
   changes an exact spec string.
