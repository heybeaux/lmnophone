# Flows for the person using the phone

Words, rules and placeholders are in [README.md](README.md). Screen IDs (U1, U2…) are for reviewers and the
Android engineer; they never appear on screen.

Each screen is written as:
- **Title**, **Body**, **Primary** (the one pinned button), **Secondary** (optional text link);
- **Spec** (the requirement it satisfies);
- **Notes** for the engineer, including behaviour at the largest text size.

---

## U1. Home screen

**Spec:** calm-home-screen (Plain home screen; Phone and Messages always present; No emergency control on the home
screen; Readable by default); essential-calling-and-texting (9-1-1 always reachable).

### Layout, top to bottom

The look (font, black and white, sizes) is in [design-tokens.md](design-tokens.md); the mockups are in
[mockups/plainphone-screens.html](mockups/plainphone-screens.html). The screen has a fixed top block and an app list
that sits low, where a thumb reaches, with white space between them. The words on this screen are unchanged.

1. **Time and date**, one line each. At 200% the date may shorten to "Thu 8 Oct".
2. **Notice line** (only when one applies, see below), in a light grey box under the date.
3. **No Emergency button.** Beaux, Telegram 4487: *"I don't want users bumping it by accident."* No tap anywhere on
   the home screen starts the emergency dialer. 9-1-1 is dialled from **Phone** (the last name on the screen), from
   **More → Emergency 9-1-1** (U4), from the lock screen's "Emergency call", and, if Q14 confirms it works on this
   phone, by pressing the power button 5 times. About this phone (U8) says how.
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
│ ┌──────────────────────────┐ │
│ │ Ask Sam to do the        │ │  ← notice line (grey box), only when one applies
│ │ monthly phone check    › │ │
│ └──────────────────────────┘ │
│                              │  ← white space
│ Spotify                      │
│ Slack                        │
│ Camera                       │
│ Messages                     │
│ Phone                        │  ← 9-1-1 is dialled here
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
│                              │
│ ┌──────────────────────────┐ │
│ │ Ask Sam to do the        │ │
│ │ monthly phone check    › │ │
│ └──────────────────────────┘ │
│ Slack             (scrolls ↑)│
│ Camera                       │
│ Messages                     │
│ Phone                        │
│ ╭──────────────────────────╮ │
│ │ More ^                   │ │
│ ╰──────────────────────────╯ │
└──────────────────────────────┘
```

**Rule (calm-home-screen, "Overdue monthly check at largest font"):** the time, date and the notice line are a
fixed block at the top; Messages, Phone and More are a fixed block at the bottom. Neither scrolls. Only the
other apps scroll, in the space between. The notice line is at most 3 lines; if longer it ends with "…" (the
full text is on the screen it opens). At 200% More drops its second line. All text, the time included, grows with
the text size and never shrinks. If the screen is short, the layout gives way in a fixed order (time and date on
one line, notice cut to 2 lines, and so on), never shrinking Messages, Phone or More. The order and the
smallest supported screen (360 × 640 dp) are in
[design-tokens.md, "When the screen is short"](design-tokens.md#when-the-screen-is-short).

### The notice line

**Spec:** calm-home-screen (notice line, in the `add-copy-test-product-decisions` change); trusted-person-controls
("Waiting changes").

At most one line shows. When several apply, the **first** in this table wins:

| # | Line (exact text) | When | Tapping it opens |
|---|---|---|---|
| 1 | **LMNOPhone needs attention** | Anything device-lockdown or approved-apps says shows it: a hide that failed, the safe fallback in use, a failed post-update check, a damaged running-time record, and (recommended) unknown phone software | U3 (which also shows a waiting change, if there is one) |
| 2 | **LMNOPhone will turn off after {time}. Tap to cancel.** | Turning off is a waiting change (24 hours of the phone being on) | U9 |
| 2 | **A change to trusted people is waiting. Tap to cancel.** | Adding, handing over or removing a trusted person is waiting | U9 |
| 2 | **A change to how this phone is set up is waiting. Tap to cancel.** | Switching mode, or (self-managed) shortening the countdown or raising the daily limit, is waiting. Exact text from the spec ([PLA-39](/PLA/issues/PLA-39)). | U9 |
| 3 | **Opening up in {n} minutes** · **Ready – tap to start** | Self-managed mode only: an elevation countdown is running, or has ended and is waiting for Start | U10 |
| 4 | **Ask {tp} to do the monthly phone check** | Only when the check is due (see the note below). With two trusted people, `{tp}` is "Sam or Jo" ([Flag 2](flows-people-modes-and-waits.md#flags-strings-i-think-should-change)). In self-managed mode: **Time for your monthly phone check** (exact text from the spec) | U2 (self-managed: [M7i](flows-people-modes-and-waits.md#m7i-monthly-check)) |

Only one waiting change can exist at a time (trusted-person-controls), so row 2 is never more than one line.

`{tp}` is the trusted person's name. `{time}` is when the wait is expected to end, as "21:15 tomorrow" or "21:15
Saturday". The wait counts only time while the phone is on, so U9 says the time can move later. Lines 1, 3 and 4
are labels with no full stop. The waiting-change lines keep the spec's sentence punctuation.

**Examples of the order:**
- A turn-off is waiting and the monthly check is due: only the turn-off line shows (spec scenario "Turn-off pending
  and monthly check overdue").
- A turn-off is waiting and LMNOPhone needs attention: "LMNOPhone needs attention" shows. U3 shows the waiting
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

## U3. "LMNOPhone needs attention" (from the notice line)

**Bounded presentation for PLA-377 / PLA-92 wiring:** the approved task uses
the title **LMNOPhone needs attention**, L3's sentence **Calls, texts and
Emergency still work.**, and **OK**. Pending changes use the exact Home notice
strings above in a grey box, with **Cancel this change** pinned above OK so it
stays reachable at 200% on 360×640 dp. Cancel opens U9b without a PIN. Committing
uses **Already in progress** and U9e's body (the turn-off variant when applicable),
with no Cancel. The cause/helper detail presentation below remains later wiring.

**Spec:** calm-home-screen notice; device-lockdown (baseline, unknown build); approved-apps (hide failed).

- **Title:** Something needs checking
- **Body:**
  LMNOPhone, the program that keeps this phone simple, needs {tp} or {helper} to look at it. {reason}
  Your phone is fine to use: calls, texts, Emergency and your apps all work. You haven't done anything wrong.
- `{reason}` is one plain sentence, from this list (the first that applies):

  | Cause | `{reason}` |
  |---|---|
  | Unknown phone software (recommended, Open question 3) | "The phone updated its own software, and LMNOPhone hasn't been checked with it yet." |
  | A hide failed | "An app on this phone couldn't be tidied away." |
  | Failed post-update check | "A check after an update didn't finish." |
  | Safe fallback in use | "LMNOPhone is running in a simpler, safer way until it's looked at." |
  | Running-time record couldn't be saved (trusted-person-controls, "Damaged, missing or inconsistent P") | "LMNOPhone couldn't save its timer, so any waiting change is paused." |
  | A change is stuck, or LMNOPhone needs repair ([M4](flows-people-modes-and-waits.md#m4-a-change-is-stuck)) | "LMNOPhone is finishing a change and needs {helper} to look at it." |
- **If a change is waiting** (it's hidden from the home screen while this screen's line shows): a grey box under
  the body, the same box as on U9:
  **"Waiting: {what}"** (`{what}` as on U9: "LMNOPhone will turn off after {time}", "A change to trusted people",
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

**Spec:** calm-home-screen (built-in settings; "No emergency control on the home screen" allows an entry in More);
trusted-person-controls (requests; trusted-person mode); essential-calling-and-texting (emergency intent).

A menu page: the Emergency row, a 16 dp gap, three large rows, then a plain text link. No primary button.

| Row | Small line under it | Opens |
|---|---|---|
| **Emergency 9-1-1** (first, set apart by a gap) | Opens the dialer with 9-1-1 typed in | Android's dialer with 9-1-1 typed in (`ACTION_DIAL`, `tel:911`, sent to the system dialer). Never a call by itself: the user presses the dialer's call button. No PIN, no confirm screen. |
| **Phone settings** | Wi-Fi, sound, text size | U7 |
| **Ask for an app** | {tp} adds apps with you | U5 |
| **For {tp}** | Where {tp} adds apps, with a PIN | Trusted-person PIN ([T1](flows-trusted-person.md#t1-pin-entry)) |
| About this phone (text link at the bottom) | (none) | U8 |

**Two trusted people:** the row reads **For Sam and Jo**, small line "Where Sam or Jo add apps, with a PIN"; it
opens "Who are you?" ([M1](flows-people-modes-and-waits.md#m1-who-are-you)).

**Self-managed mode** ([M7a](flows-people-modes-and-waits.md#m7a-your-settings)): **Ask for an app** becomes **Add an
app** (small line "Opens the Play Store, after a {wait}-minute wait"), and **For {tp}** becomes **Your settings**
(small line "With your PIN"). Both ask for the user's PIN.

**Why here, and first:** Beaux took the button off the home screen so it can't be bumped (Telegram 4487). Here it
takes a deliberate tap on More, then on the row, then on call, so a stray tap never reaches a call. It's first
because someone looking for help in a hurry reads from the top. There's no long-press shortcut on the home screen:
nobody would find it in an emergency.

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
- **While sending:** the button is disabled and reads "Sending…" (as in U5e), so one tap sends one text.

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

**Spec:** "If an SMS fails, LMNOPhone SHALL say so and keep the request listed."

- **Title:** This didn't send
- **Body:** The phone may have no signal right now. Your request is saved, and {tp} will see it on this phone.
- **Primary:** Try again
- **Secondary:** OK, leave it saved
- **While sending:** "Try again" is disabled and reads "Sending…". "OK, leave it saved" stays enabled: leaving
  undoes nothing.

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
  - **Where that line goes:** directly under the title, above "It goes to:", at every text size and with one or
    two trusted people. "It goes to:" stays a visible line. Placed there, the line is visible without scrolling
    whatever the host length, the number of trusted people or the text size, and the person reads the caution
    before the address it is about. (PLA-109 review, 2026-10-09: under the host box, it was cut off at 180%,
    with "Sam and Jo", and with long hosts.)

    ```
    ┌──────────────────────────────┐
    │ This link can't open here    │
    │ This address uses unusual    │
    │ letters. It may not be what  │
    │ it looks like.               │
    │ It goes to:                  │
    │ ┌──────────────────────────┐ │
    │ │ xn--pypal-4ve.com        │ │
    │ └──────────────────────────┘ │
    │ This phone is set up …       │
    └──────────────────────────────┘
    ```
  - Input over 2,000 characters: replace the body with "This link is too long to show." and remove "Send".
  - The screen has no route into trusted-person mode, More or settings.

### U6b. Confirm sending

- **Title:** Look at this link with {tp}?
- **Body:** {tp} will get this text: then the exact SMS text from the [texts table](README.md#texts-lmnophone-sends),
  shown in a grey box, including `{host}{path}` with the userinfo, query and fragment already removed.
- **Primary:** Send
- **Secondary:** Don't send
- **After sending:** "Sent to {tp}." with **OK**. If it fails: "This didn't send. The phone may have no signal."
  with **OK**. (No retry queue: the link is not saved, to keep this screen isolated.)
- **While sending:** once Send is tapped, **both** buttons are disabled until the phone reports sent or failed.
  "Don't send" can't be honoured once the text is on its way, so it must not be offered. The disabled Send
  button reads **"Sending…"** (already approved: U5e), so a greyed-out button doesn't look broken.
- **Title change (PLA-47 review, 2026-10-09):** the first title, "Send this link to {tp}, to look at together?",
  took 3 lines at 200% on the smallest screen. The new one keeps the "together" wording the copy test asked for
  ([copy-test.md](copy-test.md): "Send to Sam" felt like reporting), and matches the button that opens it
  ("Look at it with {tp}"). The body already says it goes as a text.
- **Two trusted people:** on U6 and U6b, `{tp}` is `{everyone}` ("Sam and Jo"), because the text goes to both.
  "Look at this link with Sam and Jo?" may take 3 lines at 200%; that's allowed (see `title` in
  [design-tokens.md](design-tokens.md#type-scale)).

## U7. Phone settings (LMNOPhone's own)

**Spec:** calm-home-screen ("Built-in basic settings without Android Settings"); web-containment (Wi-Fi sign-in).

Menu page: **Wi-Fi**, **Bluetooth**, **Sound**, **Brightness**, **Text size**, **Battery**.
Each opens one simple screen. Android Settings never appears.

**When a change is waiting**, the page starts with a grey box, above the rows: **"A change is waiting"**, second
line `{what}` (as on U9), and `›`. It opens U9. This is the place the waiting change and its PIN-free Cancel can
always be found (trusted-person-controls, "Where Cancel is reachable"), whatever the home screen's line shows.
Spoken label: **A change is waiting, {what}. Opens the change.**

| Screen | Content | Primary |
|---|---|---|
| Wi-Fi | "Connected to {network}" or "Not connected". Below, nearby networks by name. Tapping one asks for "Wi-Fi password". A connected network shows **Forget this network**. | Connect (on the password screen) |
| Bluetooth | "Paired with: {device}". "Pair a new device" lists nearby devices. | Pair (on the device screen) |
| Sound | Ring volume, media volume (large slider with − and + buttons), "Ringtone" chooser | Done |
| Brightness | One slider with − and + buttons; "Adjust automatically" on/off | Done |
| Text size | A sample sentence, then − and + buttons, then "Bigger text may push some apps' words off the edge. LMNOPhone's own screens will still fit." | Done |
| Battery | "Battery: 64%". "Charging" or "Not charging". No graphs. | (none: information page, Back only) |

### Optional Home counts (approved communication-counts slice)

More → **Phone settings** offers one PIN-free switch, off by default:
**Show missed calls and unread texts on Home**.

Under it: **Counts texts and picture messages. Chat (RCS) messages and other apps aren't supported, so they may be missing from the number.**
If either Home entry does not launch the installed, enabled standard role holder, show:
**Counts work only with the phone's standard Phone and Messages apps.**

When on but unavailable, show the applicable line(s):
- **LMNOPhone can't read missed calls right now**
- **LMNOPhone can't read unread texts right now**

Home shows a separate plain number only on Phone and Messages, for positive,
reliable host counts. Null and zero show nothing; above 99 displays **99+**.
Spoken names use the full count: **Phone, 1 missed call** / **Phone, 3 missed calls**;
**Messages, 1 unread text** / **Messages, 4 unread texts**. Without a count the
spoken names remain **Phone** and **Messages**. Other app rows have no count.

The initial UI slice includes Back, this switch and its coverage/status text only.
The host maps `MoreEvent.OpenPhoneSettings` to this screen and owns persistence,
permissions and count sources; the other settings remain separate work.

### Build 20 U7 subset

Brightness and Battery appear above the existing counts switch. Wi-Fi, Bluetooth,
Sound and Text size remain outside this preview. Brightness shows the confirmed
percentage, a slider from 10% to 100%, − and +, **Adjust automatically**, and
**Done**. Manual controls are disabled while automatic adjustment is on.
Battery has **‹ Phone settings** for Back, the level and the charging line.

Accepted by the Lead on PLA-395: this preview uses **Brightness couldn't be changed.** on a failed write
or read, and **Battery level isn't available.** for an unknown level. Unknown
charging status has no charging line. No requested value is presented as saved
until Android reads it back.

### Build 21 U7 subset

Phone settings rows are **Brightness**, **Sound**, **Battery**, then the unchanged
counts switch. More's Phone settings subtitle is **Brightness, sound, battery**.
Sound shows **Ring volume** and **Media volume**, each with a large slider and
**−** / **+**, then **Done**. Values are Android's confirmed stream steps. Ring
has a minimum of 1; media may reach 0. A failed write or read shows
**Volume couldn't be changed.** The page does not change a pre-existing silent
or vibrate mode. The **Ringtone** chooser is outside this build: another app's
picker must not open from lock task.

Brightness hides the percentage while **Adjust automatically** is on and shows
it again when off. Battery shows **Charged** when Android reports full.

### Build 27 Text size subset

Approved in [PLA-420](/PLA/issues/PLA-420). Phone settings rows are **Brightness**,
**Sound**, **Text size**, **Battery**, then the unchanged counts switch. More's
subtitle is **Brightness, sound, text size, battery**.

Text size shows **‹ Phone settings**, **Text size**, the sample sentence
**This is how big your text will be.**, the confirmed percentage, **−** and
**+**, then **Bigger text may push some apps' words off the edge. LMNOPhone's own
screens will still fit.**, and **Done**. Spoken labels are **Smaller text** and
**Bigger text**. Failure copy: **Text size couldn't be changed.**

Steps are 100, 115, 130, 150, 180 and 200 percent. Other existing values are shown
unchanged; each control chooses the nearest listed step in that direction.
Only Android's read-back value is shown. Without the settings-write grant, both
controls are disabled and the failure line appears. This preview requires a
one-time tester grant by adb; setup integration remains open. Android Settings
never opens from this page.

### Build 28 read-only Wi-Fi subset

Approved in [PLA-421](/PLA/issues/PLA-421). Rows are **Wi-Fi**, **Brightness**,
**Sound**, **Text size**, **Battery**, then the counts switch. More's subtitle is
**Wi-Fi, brightness, sound, text size, battery**.

Wi-Fi shows **‹ Phone settings**, **Wi-Fi**, then **Connected to {network}**,
**Connected to Wi-Fi** when connected but unnamed, or **Not connected**.
Below **Nearby networks**, show at most ten distinct non-hidden names, strongest
first. Rows are read-only. If the list cannot be read, show **Nearby networks
can't be shown right now.** Then show **Joining a network comes in a later test
build.** When off, show **Wi-Fi is off.** with no list. Refresh while visible.
There is no connect, password, Forget, or Settings launch in this build.

The app may grant itself ACCESS_FINE_LOCATION through its Device Owner. It never
changes Location mode, saved networks, or Wi-Fi state. Names remain on screen
only, with Android's outer quotes and unknown-name marker removed. Text size's
sample sentence is **This is how big your text will be.** (updated above).

### Build 29 read-only Wi-Fi status

Approved in [PLA-424](/PLA/issues/PLA-424). Under the connection line, show the
exact Wi-Fi message below when Android reports a sign-in page, or after at least
10 seconds of observed unvalidated connectivity without a sign-in page. Fresh
connections and validated connections show no internet status line. This subset
has no Forget or OK action and no separate message screen.

If the overall status read fails, show **Wi-Fi status can't be read right now.**
with no connection claim or nearby list. Location mode remains unchanged.

### Build 30 read-only Bluetooth subset

Approved in [PLA-428](/PLA/issues/PLA-428). Rows are **Wi-Fi**, **Bluetooth**,
**Brightness**, **Sound**, **Text size**, **Battery**, then the unchanged counts
switch. More's subtitle is **Wi-Fi, Bluetooth, brightness, sound, text size, battery**.

The page shows **‹ Phone settings**, **Bluetooth**, then **Paired with:** and up
to ten paired device names, sorted case-insensitively, only when Bluetooth is
fully on. Missing names show **Unnamed device**; no devices shows **No paired
devices.** Every other adapter state or no adapter shows **Bluetooth is off.**
A failed read (including missing permission) shows **Bluetooth status can't be
read right now.** with no on/off claim or list. Always show **Pairing a new
device comes in a later test build.** Refresh only while visible.

The only new runtime grant is the app's own BLUETOOTH_CONNECT through Device
Owner. No discovery, pairing, unpairing, connection, Bluetooth-state writes,
Location use or system UI. Device names stay on screen only; evidence records
counts. Pairing remains a later slice.

### Build 22 Sound corrections

Approved in [PLA-404](/PLA/issues/PLA-404). Failure lines appear below the stream
that failed and clear when Sound opens. The page reads both volumes once a second
while visible; Brightness also refreshes so outside changes do not leave stale values.

When silent or vibrate is on, ring controls are disabled and say:
**Ring volume can't be changed while the phone is on silent or vibrate.**
Media stays usable. The page never changes ringer mode or Do Not Disturb.

Visible buttons stay **−** and **+**. Spoken labels are **Lower ring volume**,
**Raise ring volume**, **Lower media volume**, **Raise media volume**,
**Lower brightness**, **Raise brightness**. Slider labels are **Ring volume**,
**Media volume**, **Brightness**.

### Wi-Fi messages (exact text, from web-containment)

| When | Message | Action |
|---|---|---|
| The network reports a sign-in page | **This Wi-Fi needs a sign-in page, which LMNOPhone doesn't allow. Use mobile data or another Wi-Fi.** | Primary: **Forget this network** · Secondary: OK |
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
  LMNOPhone is the program that keeps this phone simple. {tp} helps choose which apps are on it, together with
  you, with the phone in {tp}'s hands. "For {tp}" needs {tp}'s PIN so that nobody else can change your phone.
  {tp} can't read your messages, see where you are, or see what you do in your apps. LMNOPhone has no server of
  its own, no analytics and no account. It connects to {helper}'s computer over home Wi-Fi only to update itself.
  LMNOPhone sends {tp} a text only when you choose to ask for an app or share a link, when the monthly check is
  due, when LMNOPhone needs a check or a phone update is coming, when {tp}'s PIN is used to change something on
  this phone, when a big change (like turning LMNOPhone off) starts, is cancelled or finishes, or if {tp}'s PIN is
  typed wrongly many times (so {tp} knows if someone is guessing it).
  The phone keeps a list of changes from the last 90 days. It doesn't include your messages, websites or what you
  do in apps.
- **Primary:** OK
- **Then, a section with its own heading: "To call 9-1-1"**
  - Tap **Phone**, type 9-1-1, and press call.
  - Or tap **More**, then **Emergency 9-1-1**.
  - From the lock screen, tap **Emergency call**. Show this line only when the host reports a secure lock (`KeyguardManager.isDeviceSecure`).
  - {sos_line}
  `{sos_line}` is "Or press the power button 5 times quickly." **only if** feasibility question Q14 confirms the
  Emergency SOS gesture works on this phone while LMNOPhone is running. Otherwise the line is left out.
- **Two or more trusted people:** `{everyone}` joins the names with "and". Keep the privacy paragraph unchanged with these names and add the existing equals line after the first sentence: "{everyone} are equals: either can add apps with you." Use these paragraphs instead of the one-person choices and texts paragraphs (approved in [PLA-392](/PLA/issues/PLA-392)):
  - Choices: {everyone} help choose which apps are on it, together with you, with the phone in their hands. "For {everyone}" needs a PIN from one of them so that nobody else can change your phone.
  - Texts: {product} sends {everyone} a text only when you choose to ask for an app or share a link, when the monthly check is due, when {product} needs a check or a phone update is coming, when one of their PINs is used to change something on this phone, when a big change (like turning {product} off) starts, is cancelled or finishes, or if one of their PINs is typed wrongly many times (so they know if someone is guessing it).
- **Self-managed version of the body:** LMNOPhone is the program that keeps this phone simple. You hold its PIN.
  It adds waits before you can open things up, so that distraction takes effort. It's friction, not a lock.
  LMNOPhone has no server of its own, no analytics and no account. It connects to {helper}'s computer over home
  Wi-Fi only to update itself. It sends a text to {helper} only if the phone needs fixing. (No helper: "It doesn't
  send texts.")
- **Secondary:** Who to call for help → "{helper}: {helper phone}. {tp}: {tp phone}."
- **Notes:** if any approved app is itself a tracking app (for example a family-location app), this screen does
  not cover it; that app's own notice applies. LMNOPhone makes no claim about what other apps send.

## U9. A change is waiting

**Spec:** trusted-person-controls ("Waiting changes take 24 hours, one at a time, and are recoverable": Cancel,
while pending; Where Cancel is reachable; Commit); calm-home-screen (notice line, scenario "User cancels a pending
turn-off").

Opened from the home-screen notice line (row 2 in [U1](#the-notice-line)), from the box on U3, and from the box at
the top of Phone settings (U7). **No PIN anywhere on these screens**: anyone holding the phone can cancel.

`{name}` is the person whose PIN started it (in self-managed mode, "your"). `{everyone}` is every trusted person's
name ("Sam", or "Sam and Jo"). `{change}` is one of these (the full list, with the words for texts, is in
[M3](flows-people-modes-and-waits.md#m3-changes-that-wait-24-hours)):

| Kind | `{change}` |
|---|---|
| Turning off | turn LMNOPhone off |
| Adding a trusted person | add {name} as a trusted person |
| Handing a role over | hand {name}'s place to {new} |
| Removing a trusted person | remove {other} as a trusted person |
| Switching mode | switch to {user} looking after this phone · switch to {new} looking after this phone |
| Self-managed: shorter countdown | shorten the wait before opening up to {n} minutes |
| Self-managed: higher daily limit | allow {n} openings a day |

### U9a. The waiting change

- **Title:** LMNOPhone will turn off (for turning off) · A change is waiting (for everything else)
- **Box** (grey): **{Change}.** Second line: "Started with {name}'s PIN, {day} at {time}." (Self-managed:
  "You started this {day} at {time}.")
- **Why not "{name} asked":** a PIN shows that someone knew it, not who typed it (design C1, "Authority is a
  credential, not a person"). If a child used Sam's PIN, "Sam asked" would be untrue. Changed in
  [PLA-42](/PLA/issues/PLA-42).
- **Body:**
  It will happen after {time}, once the phone has been on for 24 hours. Time while the phone is off doesn't
  count, so this can move later.
  Anyone holding this phone can cancel it. No PIN is needed. {everyone} will get a text either way.
- **Primary:** Cancel this change → U9b
- **Secondary:** Keep it waiting → back
- **Notes:** for turning off, add one line before "Anyone…": "Until then, LMNOPhone works as usual." Only one
  waiting change can exist, so this screen never lists two.

### U9b. Confirm

- **Title:** Cancel this change?
- **Body:** This won't happen: {change}. {everyone} will get a text saying it was cancelled. To do it later,
  someone has to start it again, and the 24 hours start again.
- **Primary:** Yes, cancel it → U9d (or U9c if saving is slow)
- **Secondary:** No, keep it waiting → U9a

(In self-managed mode, "{everyone} will get a text…" is left out: there's nobody to text.)

### U9c. Still cancelling (only if saving fails)

The spec forbids showing "Cancelled" before it is saved. While saving, the primary button reads "Cancelling…"
and can't be pressed. If saving fails:

- **Title:** Still cancelling
- **Body:** LMNOPhone couldn't save this yet and is trying again. The change won't happen while this is
  unfinished.
- **Primary:** OK → home. The notice line now reads "Cancelling…" and opens this screen until it's saved.

### U9d. Cancelled

- **Title:** Cancelled
- **Body:** This won't happen: {change}. {everyone} will get a text about it.
- **Primary:** OK → home. The notice line has gone.
- **Notes:** "will get" and not "got": the texts are queued, and delivery doesn't change anything (trusted-person
  controls).

### U9e. Already in progress

From the moment the wait ends and LMNOPhone starts making the change, Cancel is no longer offered.

- **Title:** Already in progress
- **Body:** This change is being made now. It can't be cancelled at this point.
  For turning off: "LMNOPhone is turning off now. It can't be cancelled at this point. Calls, texts and Emergency
  keep working."
- **Primary:** OK

**If LMNOPhone cancels it itself** (for example, a PIN it depended on was changed), the notice line simply goes
away and everyone is texted, as the spec says. The user sees nothing else.

## U10. Opening up (self-managed mode only)

**Spec:** trusted-person-controls ("Elevation in self-managed mode is a single-use grant"); calm-home-screen
(notice line, row 3).

These are the two screens behind the home-screen line while a countdown runs. How the user starts an opening (PIN,
choosing what to open) is [M7b](flows-people-modes-and-waits.md#m7b-before-opening-up). `{kind}` is "the Play Store",
"full phone settings", "adding {app}" or "an LMNOPhone update". `{left}` is how many openings are left today.
For adding a hidden app and for an update, the titles read "Adding {app} in {n} minutes" / "Ready to add {app}" and
"Update in {n} minutes" / "Ready to install the update", and the first sentence of U10a reads "You can add {app}
when the countdown ends." / "You can install the update when the countdown ends." The home lines stay as the spec
gives them.

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

Approved count permission disclosure (design revision 2, D7):
"LMNOPhone keeps permission to check calls and texts after you turn this off. It never reads what they say."
