# Flows for the person using the phone

Words, rules and placeholders are in [README.md](README.md). Screen IDs (U1, U2…) are for reviewers and the
Android engineer; they never appear on screen.

Each screen is written as:
- **Title**, **Body**, **Primary** (the one pinned button), **Secondary** (optional text link);
- **Spec** (the requirement it satisfies);
- **Notes** for the engineer, including behaviour at the largest text size.

---

## U1. Home screen

**Spec:** calm-home-screen (Plain home screen; Phone, Messages and Emergency always present; Readable by default).

### Layout, top to bottom

The look (font, black and white, sizes) is in [design-tokens.md](design-tokens.md); the mockups are in
[mockups/plainphone-screens.html](mockups/plainphone-screens.html). The screen has a fixed top block and an app list
that sits low, where a thumb reaches, with white space between them. The words on this screen are unchanged.

1. **Time and date**, one line each. At 200% the date may shorten to "Thu 8 Oct".
2. **Emergency 9-1-1**: a black pill with white words, the only filled shape on the screen. No colour. Taps open
   the system dialer with 911 already typed (`ACTION_DIAL`, not a call). The user still presses the dialer's call
   button.
   - **A bump never calls.** Second line inside the button, under "9-1-1": **"Opens the dialer"**. Beaux, in his
     copy test, worried about bumping it by accident; the dialer is already the confirm step, so no extra
     confirm screen is added (it would slow a real emergency).
   - **Spacing:** no other control within 48 dp above or below it. Because the app list sits low, the real gap to
     the first app name is usually far larger, so a thumb reaching for Phone can't land on Emergency. No
     long-press or swipe actions on it.
3. **Notice line** (only when one applies, see below), in a light grey box under Emergency.
4. **The other added apps**, names only, no icons, in the order {tp} chose (or the user, in self-managed mode).
   Large type, one per row, left-aligned.
5. **Messages**, then **Phone**: always there, always the last two names, nearest the thumb (as in Beaux's
   reference image).
6. **More**: an outlined bar across the bottom, "More ^", with a small line under it: **"Ask for an app,
   settings, help"**. It sits where Beaux's reference image has a search field; there is no search field. In his
   copy test, Beaux looked for a way to ask for an app and found it only after a while. See
   [Open question 1](README.md#open-questions-for-the-lead).

Nothing else: no icons, no search bar, no widgets, no badges, no dots, no suggestions, no feeds, no swipe-up
drawer.

### Normal text size

```
┌──────────────────────────────┐
│ 10:42                        │
│ Thursday 8 October           │
│                              │
│ ╭──────────────────────────╮ │
│ │     Emergency 9-1-1      │ │  ← black, white words
│ │     Opens the dialer     │ │
│ ╰──────────────────────────╯ │
│                              │  ← at least 48 dp
│ ┌──────────────────────────┐ │
│ │ Ask Beaux to do the      │ │  ← notice line (grey box), only when due
│ │ monthly phone check    › │ │
│ └──────────────────────────┘ │
│                              │  ← white space
│ Spotify                      │
│ Slack                        │
│ Camera                       │
│ Messages                     │
│ Phone                        │
│ ╭──────────────────────────╮ │
│ │ More ^                   │ │
│ │ Ask for an app, settings,│ │
│ │ help                     │ │
│ ╰──────────────────────────╯ │
└──────────────────────────────┘
```

### Largest text size (200%)

```
┌──────────────────────────────┐
│ 10:42                        │
│ Thu 8 Oct                    │
│ ╭──────────────────────────╮ │
│ │     Emergency 9-1-1      │ │
│ │     Opens the dialer     │ │
│ ╰──────────────────────────╯ │
│                              │
│ ┌──────────────────────────┐ │
│ │ Ask Beaux to do the      │ │
│ │ monthly phone check    › │ │
│ └──────────────────────────┘ │
│ Camera            (scrolls ↑)│
│ Messages                     │
│ Phone                        │
│ ╭──────────────────────────╮ │
│ │ More ^                   │ │
│ ╰──────────────────────────╯ │
└──────────────────────────────┘
```

**Rule (calm-home-screen, "Overdue monthly check at largest font"):** the time, date, Emergency and the notice line
are a fixed block at the top; Messages, Phone and More are a fixed block at the bottom. Neither scrolls. Only the
other apps scroll, in the space between. The notice line is at most 3 lines; if longer it ends with "…" (the
full text is on the screen it opens). At 200% More drops its second line. All text, the time included, grows with
the text size and never shrinks. If the screen is short, the layout gives way in a fixed order (time and date on
one line, notice cut to 2 lines, and so on), never shrinking Emergency, Messages, Phone or More. The order and the
smallest supported screen (360 × 640 dp) are in
[design-tokens.md, "When the screen is short"](design-tokens.md#when-the-screen-is-short).

### The notice line

**Spec:** calm-home-screen (notice line, in the `add-copy-test-product-decisions` change); trusted-person-controls
("Waiting changes").

At most one line shows. When several apply, the **first** in this table wins:

| # | Line (exact text) | When | Tapping it opens |
|---|---|---|---|
| 1 | **Plainphone needs attention** | Anything device-lockdown or approved-apps says shows it: a hide that failed, the safe fallback in use, a failed post-update check, a damaged running-time record, and (recommended) unknown phone software | U3 (which also shows a waiting change, if there is one) |
| 2 | **Plainphone will turn off after {time}. Tap to cancel.** | Turning off is a waiting change (24 hours of the phone being on) | U9 |
| 2 | **A change to trusted people is waiting. Tap to cancel.** | Adding, handing over or removing a trusted person is waiting | U9 |
| 2 | **A change to how this phone is set up is waiting. Tap to cancel.** | Switching mode, or (self-managed) shortening the countdown or raising the daily limit, is waiting. *Wording proposed; the spec gives exact text only for the two above.* | U9 |
| 3 | **Opening up in {n} minutes** · **Ready – tap to start** | Self-managed mode only: an elevation countdown is running, or has ended and is waiting for Start | U10 |
| 4 | **Ask {tp} to do the monthly phone check** | Only when the check is due (see the note below). In self-managed mode: **Time for your monthly phone check** (*wording proposed; the spec says only that it asks the user*) | U2 |

Only one waiting change can exist at a time (trusted-person-controls), so row 2 is never more than one line.

`{tp}` is the trusted person's name. `{time}` is when the wait is expected to end, as "21:15 tomorrow" or "21:15
Saturday". The wait counts only time while the phone is on, so U9 says the time can move later. Lines 1, 3 and 4
are labels with no full stop. The waiting-change lines keep the spec's sentence punctuation.

**Examples of the order:**
- A turn-off is waiting and the monthly check is due: only the turn-off line shows (spec scenario "Turn-off pending
  and monthly check overdue").
- A turn-off is waiting and Plainphone needs attention: "Plainphone needs attention" shows. U3 shows the waiting
  turn-off with its Cancel (spec scenario "Turn-off pending under 'needs attention'"). The waiting change and its
  Cancel are also always in Phone settings (U7).
- From the moment the wait ends and the change is actually being made, the line reads **Already in progress** and
  has no Cancel (trusted-person-controls, "Commit"). Tapping it opens U9 in its "Already in progress" state.

**The monthly line is rare.** It shows only when the check is due. Today's spec (approved-apps) says "more
than 7 days past its due date"; the Lead is making the check event-driven if [PLA-24](/PLA/issues/PLA-24) shows
updates arrive on their own, and the copy then still holds. Most of the time the user never sees it. The copy-test packet
showed it without saying so, and Beaux asked why something monthly takes space on the main screen.
The packet now says when it appears.

## U2. "About the monthly check" (from the notice line)

**Spec:** approved-apps ("Tapping it SHALL only explain what the check is; it SHALL NOT open Play").

- **Title:** The monthly phone check
- **Body:**
  Once a month, {tp} needs your phone for about 10 minutes to update your apps, so they keep working well.
  Nothing is wrong with your phone. Everything still works until then.
- **Primary:** OK
- **Notes:** No PIN. No link to Play. Back and OK both return home.

## U3. "Plainphone needs attention" (from the notice line)

**Spec:** calm-home-screen notice; device-lockdown (baseline, unknown build); approved-apps (hide failed).

- **Title:** Something needs checking
- **Body:**
  Plainphone, the program that keeps this phone simple, needs {tp} or {helper} to look at it. {reason}
  Your phone is fine to use: calls, texts, Emergency and your apps all work. You haven't done anything wrong.
- `{reason}` is one plain sentence, from this list (the first that applies):

  | Cause | `{reason}` |
  |---|---|
  | Unknown phone software (recommended, Open question 3) | "The phone updated its own software, and Plainphone hasn't been checked with it yet." |
  | A hide failed | "An app on this phone couldn't be tidied away." |
  | Failed post-update check | "A check after an update didn't finish." |
  | Safe fallback in use | "Plainphone is running in a simpler, safer way until it's looked at." |
  | Running-time record couldn't be saved (trusted-person-controls, "Damaged, missing or inconsistent P") | "Plainphone couldn't save its timer, so any waiting change is paused." |
- **If a change is waiting** (it's hidden from the home screen while this screen's line shows): a grey box under
  the body, the same box as on U9:
  **"Waiting: {what}"** (`{what}` as on U9: "Plainphone will turn off after {time}", "A change to trusted people",
  "A change to how this phone is set up"), with the text link **Cancel this change** → U9b. No PIN. Once the change
  is being made, the box reads "Already in progress" and has no link.
- **Primary:** OK
- **Secondary:** What should I do?
  → "Show this screen to {tp} or {helper} next time you see them. You don't need to do anything else."
- **Notes:** Never show error codes here. The detail is in trusted-person mode (T2) and in the helper details.
  If `{helper}` and `{tp}` are the same person, say the name once.
- **Copy-test change (round 3, Beaux):** the earlier body said "It isn't a virus". Beaux then thought it *was*
  a virus: naming the fear planted it. The word is gone, and the screen now says what is wrong in plain terms.

## U4. More

**Spec:** calm-home-screen (built-in settings); trusted-person-controls (requests; trusted-person mode).

A menu page: three large rows, then a plain text link. No primary button.

| Row | Small line under it | Opens |
|---|---|---|
| **Phone settings** | Wi-Fi, sound, text size | U7 |
| **Ask for an app** | {tp} adds apps with you | U5 |
| **For {tp}** | Where {tp} adds apps, with a PIN | Trusted-person PIN ([T1](flows-trusted-person.md#t1-pin-entry)) |
| About this phone (text link at the bottom) | (none) | U8 |

## U5. Ask for an app

**Spec:** trusted-person-controls ("App requests are untrusted text sent to a fixed recipient").

### U5a. Write the request

- **Title:** Ask for an app
- **Body:** {tp} adds apps to this phone, together with you. Tell {tp} which app you'd like and what it's for.
  {tp} gets a text.
- **Text box:** label "Your message", hint "For example: the bus times app, to check the ferry bus". Counter
  under the box only after 200 characters: "80 characters left".
- **Primary:** Send to {tp}
- **Notes:** 280-character limit (enforced, with the counter). The button is disabled until there is at least
  one letter. The keyboard must not cover the button: the screen scrolls so both are visible.

### U5b. Sent

- **Title:** Sent to {tp}
- **Body:** Next time you're together, {tp} can add it on this phone. Your request is saved here too, and you
  can see below when {tp}'s phone has it.
- **Primary:** OK

### U5e. Your requests (below the box on U5a)

Beaux asked: "I don't know if he got it or not." So each saved request shows its status:

| Status | When |
|---|---|
| "Sending…" | Until Android reports the text sent |
| "Sent" | Sent, no delivery report yet |
| **"{tp}'s phone got it ✓"** | The network's delivery report arrived (`deliveryIntent`) |
| "Didn't send – Try again" | Sending failed (U5c) |
| "Added ✓" or "Not added" | After {tp} deals with it in trusted-person mode |

- Each row: the first line of the request, the day, and the status. Newest first, at most 10; older ones drop
  off.
- **Never say {tp} has read it or seen it.** A delivery report means the text reached {tp}'s phone, nothing
  more. Some carriers don't send delivery reports; then the row stays "Sent", which is still true.

### U5c. Didn't send

**Spec:** "If an SMS fails, Plainphone SHALL say so and keep the request listed."

- **Title:** This didn't send
- **Body:** The phone may have no signal right now. Your request is saved, and {tp} will see it on this phone.
- **Primary:** Try again
- **Secondary:** OK, leave it saved

### U5d. Five requests today

- **Title:** That's 5 requests today
- **Body:** You can ask again tomorrow. Your requests from today are saved for {tp}.
- **Primary:** OK

## U6. Blocked link

**Spec:** web-containment (outside links; isolated and safe blocked-link screen).

Opens when the user taps a web link that doesn't belong to an added app, from Messages, Slack, an email and so on.

### U6a. The link can't open

```
┌──────────────────────────────┐
│ This link can't open here    │
│                              │
│ It goes to:                  │
│ ┌──────────────────────────┐ │
│ │ attacker.invalid         │ │  ← host only, large, plain font
│ └──────────────────────────┘ │
│ This phone is set up without │
│ web pages, to keep it        │
│ simple. That doesn't mean    │
│ the link is bad.             │
│                              │
│ Look at it with Beaux        │  ← secondary (text link)
│ ┌──────────────────────────┐ │
│ │         Go back          │ │  ← primary
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

- **Title:** This link can't open here
- **Body:** It goes to: **{host}** — This phone is set up without web pages, to keep it simple. That doesn't mean
  the link is bad. (Links to your own apps, like Spotify, still open in the app.)
- **Primary:** Go back
- **Secondary:** Look at it with {tp}
- **Why "Go back" is primary:** the calm choice is to carry on. Sending is there, but not pushed.
- **Copy-test change:** the first draft said "Links only open when they belong to one of your apps". The cold
  reader didn't follow it and asked whether the link was dangerous ([copy-test.md](copy-test.md)).
- **Notes (all from the spec):**
  - Show the **registrable host only** (`attacker.invalid` for
    `https://trusted.example@attacker.invalid/login?token=abc`).
  - If the host contains letters from more than one alphabet, look-alike characters, or right-to-left marks,
    show the host as plain ASCII (punycode) and add one line: "This address uses unusual letters. It may not be
    what it looks like."
  - Input over 2,000 characters: replace the body with "This link is too long to show." and remove "Send".
  - The screen has no route into trusted-person mode, More or settings.

### U6b. Confirm sending

- **Title:** Send this link to {tp}, to look at together?
- **Body:** {tp} will get this text: then the exact SMS text from the [texts table](README.md#texts-plainphone-sends),
  shown in a grey box, including `{host}{path}` with the userinfo, query and fragment already removed.
- **Primary:** Send
- **Secondary:** Don't send
- **After sending:** "Sent to {tp}." with **OK**. If it fails: "This didn't send. The phone may have no signal."
  with **OK**. (No retry queue: the link is not saved, to keep this screen isolated.)

## U7. Phone settings (Plainphone's own)

**Spec:** calm-home-screen ("Built-in basic settings without Android Settings"); web-containment (Wi-Fi sign-in).

Menu page: **Wi-Fi**, **Bluetooth**, **Sound**, **Brightness**, **Text size**, **Battery**.
Each opens one simple screen. Android Settings never appears.

**When a change is waiting**, the page starts with a grey box, above the rows: **"A change is waiting"**, second
line `{what}` (as on U9), and `›`. It opens U9. This is the place the waiting change and its PIN-free Cancel can
always be found (trusted-person-controls, "Where Cancel is reachable"), whatever the home screen's line shows.

| Screen | Content | Primary |
|---|---|---|
| Wi-Fi | "Connected to {network}" or "Not connected". Below, nearby networks by name. Tapping one asks for "Wi-Fi password". A connected network shows **Forget this network**. | Connect (on the password screen) |
| Bluetooth | "Paired with: {device}". "Pair a new device" lists nearby devices. | Pair (on the device screen) |
| Sound | Ring volume, media volume (large slider with − and + buttons), "Ringtone" chooser | Done |
| Brightness | One slider with − and + buttons; "Adjust automatically" on/off | Done |
| Text size | A sample sentence, then − and + buttons, then "Bigger text may push some apps' words off the edge. Plainphone's own screens will still fit." | Done |
| Battery | "Battery: 64%". "Charging" or "Not charging". No graphs. | (none: information page, Back only) |

### Wi-Fi messages (exact text, from web-containment)

| When | Message | Action |
|---|---|---|
| The network reports a sign-in page | **This Wi-Fi needs a sign-in page, which Plainphone doesn't allow. Use mobile data or another Wi-Fi.** | Primary: **Forget this network** · Secondary: OK |
| Connected, but no internet and no sign-in page detected | **This Wi-Fi isn't reaching the internet.** | Primary: **Forget this network** · Secondary: OK |

**Layout, in this order** (the spec sentence stays exactly as written; only what's around it changed):
1. **Title, large:** "Calls and texts still work"
2. Under it: "when the phone has signal (the bars at the top of the screen)."
3. The spec message, in normal text.
4. One line: "Forget this network stops the phone trying to join this Wi-Fi."
5. Primary **Forget this network**, secondary **OK**.

**Copy-test change (round 3, Beaux):** the earlier screen led with the sign-in page and put calls last. Beaux
came away thinking "I don't think I'll be able to make calls from it." Calls now come first, and the mobile-data
explanation was dropped to keep it short. (We still don't promise more: no phone can call with neither a mobile
signal nor working Wi-Fi.)

## U8. About this phone

**Spec:** supports "support, not surveillance" ([README.md](README.md#who-we-are-designing-for)); no requirement
mandates it. It restates facts already in the design (no monitoring, no servers, the fixed SMS list).

- **Title:** About this phone
- **Body:**
  Plainphone is the program that keeps this phone simple. {tp} helps choose which apps are on it, together with
  you, with the phone in {tp}'s hands. "For {tp}" needs {tp}'s PIN so that nobody else can change your phone.
  {tp} can't read your messages, see where you are, or see what you do in your apps. Plainphone doesn't send
  information anywhere.
  Plainphone sends {tp} a text only when you choose to ask for an app or share a link, when the monthly check is
  due, when Plainphone needs a check or a phone update is coming, when a big change (like turning Plainphone off)
  starts, is cancelled or finishes, or if {tp}'s PIN is typed wrongly many times (so {tp} knows if someone is
  guessing it).
- **Primary:** OK
- **Secondary:** Who to call for help → "{helper}: {helper phone}. {tp}: {tp phone}."
- **Notes:** if any approved app is itself a tracking app (for example a family-location app), this screen does
  not cover it; that app's own notice applies. Plainphone makes no claim about what other apps send.

## U9. A change is waiting

**Spec:** trusted-person-controls ("Waiting changes take 24 hours, one at a time, and are recoverable": Cancel,
while pending; Where Cancel is reachable; Commit); calm-home-screen (notice line, scenario "User cancels a pending
turn-off").

Opened from the home-screen notice line (row 2 in [U1](#the-notice-line)), from the box on U3, and from the box at
the top of Phone settings (U7). **No PIN anywhere on these screens**: anyone holding the phone can cancel.

`{who}` is the person who asked (in self-managed mode, "You"). `{everyone}` is every trusted person's name ("Sam",
or "Sam and Jo"). `{change}` is one of:

| Kind | `{change}` |
|---|---|
| Turning off | turn Plainphone off |
| Adding a trusted person | add {name} as a trusted person |
| Handing a role over | hand {name}'s place over to {new name} |
| Removing a trusted person | remove {name} as a trusted person |
| Switching mode | switch to you looking after your own phone · switch to {tp} looking after this phone |
| Self-managed: shorter countdown | shorten the wait before opening up to {n} minutes |
| Self-managed: higher daily limit | allow {n} openings a day |

### U9a. The waiting change

- **Title:** Plainphone will turn off (for turning off) · A change is waiting (for everything else)
- **Box** (grey): **{who} asked to {change}.** Second line: "Asked {day} at {time}."
- **Body:**
  It will happen after {time}, once the phone has been on for 24 hours. Time while the phone is off doesn't
  count, so this can move later.
  Anyone holding this phone can cancel it. No PIN is needed. {everyone} will get a text either way.
- **Primary:** Cancel this change → U9b
- **Secondary:** Keep it waiting → back
- **Notes:** for turning off, add one line before "Anyone…": "Until then, Plainphone works as usual." Only one
  waiting change can exist, so this screen never lists two.

### U9b. Confirm

- **Title:** Cancel this change?
- **Body:** {Who} won't {change}. {everyone} will get a text saying it was cancelled. To do it later, someone has to
  ask again, and the 24 hours start again.
- **Primary:** Yes, cancel it → U9d (or U9c if saving is slow)
- **Secondary:** No, keep it waiting → U9a

(In self-managed mode the body starts "This won't happen." instead of "{Who} won't {change}.")

### U9c. Still cancelling (only if saving fails)

The spec forbids showing "Cancelled" before it is saved. While saving, the primary button reads "Cancelling…"
and can't be pressed. If saving fails:

- **Title:** Still cancelling
- **Body:** Plainphone couldn't save this yet and is trying again. The change won't happen while this is
  unfinished.
- **Primary:** OK → home. The notice line now reads "Cancelling…" and opens this screen until it's saved.

### U9d. Cancelled

- **Title:** Cancelled
- **Body:** {Who} won't {change}. {everyone} will get a text about it.
- **Primary:** OK → home. The notice line has gone.
- **Notes:** "will get" and not "got": the texts are queued, and delivery doesn't change anything (trusted-person
  controls).

### U9e. Already in progress

From the moment the wait ends and Plainphone starts making the change, Cancel is no longer offered.

- **Title:** Already in progress
- **Body:** This change is being made now. It can't be cancelled at this point.
  For turning off: "Plainphone is turning off now. It can't be cancelled at this point. Calls, texts and Emergency
  keep working."
- **Primary:** OK

**If Plainphone cancels it itself** (for example, a PIN it depended on was changed), the notice line simply goes
away and everyone is texted, as the spec says. The user sees nothing else.

## U10. Opening up (self-managed mode only)

**Spec:** trusted-person-controls ("Elevation in self-managed mode is a single-use grant"); calm-home-screen
(notice line, row 3).

These are the two screens behind the home-screen line while a countdown runs. How the user starts an opening (PIN,
choosing the Play Store or full phone settings) belongs with the self-managed flows, which aren't drawn yet. `{kind}`
is "the Play Store" or "full phone settings". `{left}` is how many openings are left today.

### U10a. Counting down (line: "Opening up in {n} minutes")

- **Title:** Opening up in {n} minutes
- **Body:** {Kind} opens when the countdown ends. Then you'll have 5 minutes to tap Start. If you don't, it closes
  again and doesn't count. Your phone works as usual meanwhile. Restarting the phone stops the countdown.
- **Primary:** OK → home
- **Secondary:** Stop the countdown → home. Nothing is used up.

### U10b. Ready (line: "Ready – tap to start")

- **Title:** Ready to open {kind}
- **Body:** Tap Start within {m} minutes. This uses 1 of your {left} openings left today.
- **Primary:** Start → opens {kind}
- **Secondary:** Not now → home. If Start isn't tapped in time, it closes and doesn't count.
