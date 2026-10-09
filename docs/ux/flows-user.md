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

1. **Time and date**, one line each. At 200% the date may shorten to "Thu 8 Oct".
2. **Emergency 9-1-1**: full-width, red, the only red on the screen. Taps open the system dialer with 911
   already typed (`ACTION_DIAL`, not a call). The user still presses the green call button.
   - **A bump never calls.** Small grey line inside the button, under "9-1-1": **"Opens the dialer"**. Beaux's
     copy-test pilot worried about bumping it by accident; the dialer is already the confirm step, so no extra
     confirm screen is added (it would slow a real emergency).
   - **Spacing:** at least 16 dp of empty space between Emergency and Phone, more than between any other two
     tiles, so a thumb reaching for Phone doesn't land on Emergency. No long-press or swipe actions on it.
3. **Phone** and **Messages**: full-width tiles, always there.
4. **Notice line** (only when one applies, see below). Never above items 2 and 3.
5. **The other added apps**, one per row, in the order {tp} chose. Large icon, name beside it.
6. **More**: last row, same style as an app, with a small line under the name: **"Ask for an app, settings,
   help"**. In the pilot, the reader looked for a way to ask for an app and found it only after a while. See
   [Open question 1](README.md#open-questions-for-the-lead).

Nothing else: no search bar, no widgets, no badges, no dots, no suggestions, no feeds, no swipe-up drawer.

### Normal text size

```
┌──────────────────────────────┐
│ 10:42                        │
│ Thursday 8 October           │
│ ┌──────────────────────────┐ │
│ │      Emergency 9-1-1     │ │  ← red
│ │      Opens the dialer    │ │
│ └──────────────────────────┘ │
│                              │  ← extra gap
│ ┌──────────────────────────┐ │
│ │ ☎  Phone                 │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ ✉  Messages              │ │
│ └──────────────────────────┘ │
│  Ask Beaux to do the monthly │  ← notice line (grey, not red)
│  phone check            ›    │
│ ─────────────────────────────│
│  ◉ Spotify                   │
│  ◉ Slack                     │
│  ◉ Camera                    │
│  ◉ More                      │
│    Ask for an app, settings, │
│    help                      │
└──────────────────────────────┘
```

### Largest text size (200%)

```
┌──────────────────────────────┐
│ 10:42                        │
│ Thu 8 Oct                    │
│ ┌──────────────────────────┐ │
│ │       Emergency          │ │
│ │       9-1-1              │ │
│ │    Opens the dialer      │ │
│ └──────────────────────────┘ │
│                              │
│ ┌──────────────────────────┐ │
│ │ ☎  Phone                 │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ ✉  Messages              │ │
│ └──────────────────────────┘ │
│  Ask Beaux to do the         │
│  monthly phone check   ›     │
│ ─────────────────── (scroll) │
└──────────────────────────────┘
```

**Rule (calm-home-screen, "Overdue monthly check at largest font"):** items 1–4 are a fixed block that never
scrolls. The notice line is at most 3 lines at 200%; if longer it ends with "…" (the full text is on the screen
it opens). Only the app list below scrolls. Engineer: check the fixed block fits in the smallest supported screen
height at 200% before the app list; if it doesn't, shrink the time first, never the three buttons.

### The two notice lines

Only one shows at a time. If both apply, "Plainphone needs attention" wins.

| Line (exact text) | When | Tapping it opens |
|---|---|---|
| **Ask {tp} to do the monthly phone check** | The monthly check is more than 7 days overdue (approved-apps) | U2 |
| **Plainphone needs attention** | Anything device-lockdown or approved-apps says shows it: a hide that failed, the safe fallback in use, a failed post-update check, and (recommended) unknown phone software | U3 |

The spec text for the monthly line is "Ask [trusted person] to do the monthly phone check"; `{tp}` is that name.
No full stop on either line: they are labels, not sentences.

**The monthly line is rare.** It shows only when the check is more than 7 days late, which means {tp} has
already had texts about it and hasn't done it yet. Most months the user never sees it. The copy-test packet
showed it without saying so, and the pilot reader asked why something monthly takes space on the main screen.
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
- **Primary:** OK
- **Secondary:** What should I do?
  → "Show this screen to {tp} or {helper} next time you see them. You don't need to do anything else."
- **Notes:** Never show error codes here. The detail is in trusted-person mode (T2) and in the helper details.
  If `{helper}` and `{tp}` are the same person, say the name once.
- **Copy-test change (pilot):** the earlier body said "It isn't a virus". The pilot reader then thought it *was*
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

The pilot reader asked: "I don't know if he got it or not." So each saved request shows its status:

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

**Copy-test change (pilot):** the earlier screen led with the sign-in page and put calls last. The pilot reader
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
  due, or if {tp}'s PIN is typed wrongly many times (so {tp} knows if someone is guessing it).
- **Primary:** OK
- **Secondary:** Who to call for help → "{helper}: {helper phone}. {tp}: {tp phone}."
- **Notes:** if any approved app is itself a tracking app (for example a family-location app), this screen does
  not cover it; that app's own notice applies. Plainphone makes no claim about what other apps send.
