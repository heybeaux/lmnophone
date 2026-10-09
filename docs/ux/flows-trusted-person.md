# Flows for the trusted person

Words, rules and placeholders are in [README.md](README.md). The trusted person is `{tp}`; the phone belongs to
`{user}`. Everything here happens on `{user}`'s phone, in `{tp}`'s hands.

**Tone.** `{tp}` is looking after a phone for someone they care about. The words are about helping `{user}`,
never about watching them. The mode's own name on screen is **"Looking after {user}'s phone"**.

**Fresh PIN.** trusted-person-controls requires a fresh PIN (or recovery code) for each of: adding an app,
removing an app, starting any session, installing a Plainphone update, turning Plainphone off. Entering the mode
also needs the PIN. The first time a second PIN is asked for, one line explains why:
"Plainphone asks for your PIN for each change, so nobody else can make one if the phone is put down."

---

## T1. PIN entry

**Spec:** trusted-person-controls (PIN lockout schedule; recovery code; fresh authority).

### T1a. Enter PIN

- **Title:** {tp}'s PIN
- **Body:** Enter your PIN to look after {user}'s phone.
- **Keypad:** large digits, 6 or more. Dots, not digits, as they are typed.
- **Primary:** Continue
- **Secondary:** Forgot PIN? Use recovery code
- **Wrong PIN (1–4):** "That PIN isn't right. Try again." No count shown.
- **Notes:** `FLAG_SECURE`. Emergency is still reachable: Home always returns to the home screen.

### T1b. Waiting after too many tries

- **Title:** Too many tries
- **Body:** Wait {time} before trying again. You can use the recovery code now instead (the long code you wrote
  down when Plainphone was set up).
  Calls, texts and Emergency still work.
- **Primary:** Use recovery code
- **Secondary:** OK
- **Notes:** `{time}` is "30 seconds", "1 minute", "2 minutes"… "1 hour" (doubling from failure 5, capped at 1
  hour). It counts down in whole minutes only (seconds below 1 minute) and survives a restart. Typing during the
  wait does nothing: the keypad is hidden. From failure 10, the SMS in the
  [texts table](README.md#texts-plainphone-sends) is sent once.

### T1c. Recovery code

- **Title:** Enter recovery code
- **Body:** This is the long code you wrote down when Plainphone was set up. Upper and lower case don't matter.
- **Primary:** Continue
- **Then:** "Choose a new PIN" (6 or more digits, typed twice), then "Here is your new recovery code" (shown once,
  same screen as setup [S2b](flows-setup.md#s2-pin-and-recovery-code-step-2-of-7)). The old PIN and old code stop working at
  the same moment.
- **Note on screen, once:** "This code can't get past Google's reset protection if the phone is ever wiped. Only
  your Google account can do that."
- **Notes:** separate counter and the same waiting schedule as the PIN. A PIN wait never blocks this screen.

## T2. Looking after {user}'s phone (front page)

**Spec:** trusted-person-controls; approved-apps; device-lockdown (software changed); calm-home-screen
(always-present apps).

A menu page. At the top, a short **"Needs you"** list, only when something is waiting. Below it, the menu.

### "Needs you" items (in this order; each opens its own screen)

| Item text | When | Opens |
|---|---|---|
| **Not ready to hand back – call the helper** | A protection check failed after a session or update | [P5](flows-sessions.md#p5-not-ready-to-hand-back) |
| **Plainphone needs attention** | Same conditions as the home line | [L3](flows-limits-and-off.md#l3-plainphone-needs-attention-trusted-person-view) |
| **Phone software changed – review pending** | The phone is on software Plainphone hasn't reviewed | [L2](flows-limits-and-off.md#l2-phone-software-changed--review-pending) |
| **{n} requests from {user}** | Unanswered app requests | T3 |
| **Check for updates – due** (or "…overdue by {n} days") | From the due date | [P3](flows-sessions.md#p3-monthly-check) (the same path as the menu row) |
| **{app} didn't update** (one row per app) | Recorded at a monthly check, not yet resolved | T6 |
| **An app was installed that you haven't added** | Any install outside a session | T7 |
| **Texts to you aren't sending** | Plainphone's SMS permission check failed | T8 |

### Menu

| Row | Small line | Opens |
|---|---|---|
| **Add an app** | Opens the Play Store for 10 minutes | [P1](flows-sessions.md#p1-before-a-session) (Play) |
| **Apps on this phone** | Change the order, remove, what each can use | T5 |
| **Requests from {user}** | (count, if any) | T3 |
| **Check for updates** | Last done: {date} | [P3](flows-sessions.md#p3-monthly-check) |
| **Open full phone settings** | For 5 minutes, for things Phone settings doesn't have, like the screen lock | [P1](flows-sessions.md#p1-before-a-session) (Settings) |
| **What Plainphone can't stop** | Read this before adding apps | [L1](flows-limits-and-off.md#l1-what-plainphone-cant-stop) |
| **Install a Plainphone update** | Only with {helper}, when {helper} asks | [P6](flows-sessions.md#p6-install-a-plainphone-update) |
| **Change PIN** | (none) | T1-style: old PIN, new PIN twice |
| **Turn Plainphone off** | Makes this an ordinary phone | [L4](flows-limits-and-off.md#l4-turning-plainphone-off) |

Under the title, one line, always shown: "This is for adding apps and keeping the phone working. You can't see
{user}'s messages, photos, or what {user} does in apps." (Cold readers in both roles said the trusted-person side
felt like surveillance without it; see [copy-test.md](copy-test.md).)

A **Done** button is pinned at the bottom: "Done – back to {user}'s home screen". It is the only primary action.

## T3. Requests from {user}

**Spec:** trusted-person-controls ("shown as 'From [user]:' in plain text").

Each request is one card:

```
From the trusted person:
"the bus times app, to check the ferry bus"
Thursday 8 Oct, 10:40 · Text sent to you ✓      (or: "Text didn't send")
[ Find it in the Play Store ]   [ Not now ]
```

- The quoted words are shown exactly as typed, in plain text, inside quotation marks, never as a link.
- **Find it in the Play Store** starts a Play session (P1). The request is attached to the session, but the
  approval is for whatever app actually gets installed (T4b).
- **Not now** asks: "Remove this request? {user} won't be told." → **Remove** / **Keep it**.
  (The user is not notified by Plainphone; `{tp}` tells them in person. This keeps Plainphone from being a
  messenger of "no".)

## T4. Adding an app (after it is installed in a Play session)

**Spec:** approved-apps (Play sessions; permissions at approval); web-containment (web-leak notes);
trusted-person-controls (misleading request).

Shown when `{tp}` leaves the Play Store and a new app was installed in that session. If more than one was
installed, each goes through T4 in turn.

### T4a. Add {app}?

```
┌──────────────────────────────┐
│ Add Spotify?                 │
│ ◉ Spotify: Music and Podcasts│
│   Made by Spotify AB         │
│                              │
│ Web pages inside this app    │
│ ┌──────────────────────────┐ │
│ │ (web-leak note, 2–4      │ │
│ │  short lines)            │ │
│ │ Checked for version {v}. │ │
│ └──────────────────────────┘ │
│ More about this app ›        │
│ ┌──────────────────────────┐ │
│ │        Next              │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

- **Title:** Add {app}?
- **Body:**
  - App name and developer **as Android and the Play Store report them for the installed app**, never the
    request text.
  - If this session came from a request and the installed app's name doesn't contain the requested words:
    "{user} asked for: "{request}". This app is {app}, made by {developer}. Make sure it's the one {user} meant."
  - "Web pages inside this app": the catalog note ([starter-catalog.md](starter-catalog.md)) and one of these
    status lines (exact text from web-containment):

    | Case | Line |
    |---|---|
    | Reviewed for this version or newer | "Checked for version {reviewed}." |
    | App is newer than the reviewed version | **"Reviewed for an older version"**, then "Checked for version {reviewed}. This is version {installed}." |
    | No catalog entry | **"Not reviewed yet – this app may open web pages inside it"** |

    Under "Not reviewed yet", one more line (Beaux asked who reviews, and how to ask): "The Plainphone
    team checks apps for this and writes a note for each one. They haven't checked {app}. To ask them to, tell
    {helper}."
- **Primary:** Next
- **Secondary:** Don't add → "{app} stays hidden. It can't be opened." **OK**.

### T4b. Not reviewed yet: extra confirmation

Only when there is no catalog entry (web-containment, "Approving an unreviewed app").

- **Title:** Add an app nobody has checked?
- **Body:** The Plainphone team hasn't checked whether {app} can show web pages inside it. Some apps can show any
  website, which would let {user} reach websites this phone normally keeps out. If you're not sure, wait: ask
  {helper} to get it checked first. You can also add it now and remove it later.
- **Primary:** Add anyway
- **Secondary:** Don't add

### T4c. What can {app} use?

**Spec:** approved-apps ("For each permission, the trusted person chooses Allow or Deny").

```
What can Spotify use?
Choose together with {user}. Spotify
won't ask about these later. To change
them, you do it together, in "Apps on
this phone".

Microphone
For voice search.
 ( Allow )  ( Don't allow )

Nearby devices
To play on speakers and headphones.
 ( Allow )  ( Don't allow )

Notifications
To show what's playing.
 ( Allow )  ( Don't allow )

[        Next        ]
```

- **Title:** What can {app} use?
- **Body:** Choose together with {user}. {app} won't ask {user} about these later, and {user} can't change them
  alone. If {user} wants a change, you change it together, with the phone in your hands: "Apps on this phone" →
  {app} → "What {app} can use". If you choose "Don't allow", the parts of {app} that need it won't work.
- **Copy-test change (round 3, Beaux):** the earlier body said "{user} won't be asked later" and "You can change this
  later" without saying who or how. Beaux read it as {user} being locked out and having to bring
  the phone to {tp}. That is how it works, so the screen now says it plainly. The change path depends on
  [Open question 4](README.md#open-questions-for-the-lead).
- One block per Android permission group the app requests. Each block: plain name, one line of why (from the
  catalog if there is an entry, otherwise the generic line below), and two large choice buttons.
- **Nothing is pre-selected.** Next stays disabled until every block has a choice. (A forced choice is slower,
  but it means nothing is allowed by accident.)
- **Primary:** Next
- **If Android won't let Plainphone switch a permission on** (Q1 will tell us for camera, microphone and
  location): the block shows "Android doesn't let Plainphone switch this on. Parts of {app} that need it won't
  work." with no buttons.

Plain names for permission groups:

| Android group | Name on screen | Generic "why" line |
|---|---|---|
| Camera | Camera | Take photos and videos |
| Microphone | Microphone | Record sound, such as voice messages |
| Location | Location (where the phone is) | Show things near you, or let {app} know where you are |
| Contacts | Contacts | See and change the people in your contacts |
| Phone | Phone calls | Make calls and see who is calling |
| Call logs | Call history | See who you called and who called you |
| SMS | Text messages | Read or send texts |
| Calendar | Calendar | See and add events |
| Nearby devices | Nearby devices | Connect to speakers, headphones and watches |
| Photos and videos / Music and audio / Files | Photos and videos · Music and audio | See the photos, videos or music on this phone |
| Notifications | Notifications | Show alerts at the top of the screen |
| Physical activity / Body sensors | Movement and health sensors | Count steps or read heart rate |

### T4d. Confirm

- **Title:** Add {app} to {user}'s phone?
- **Body:** It will appear on the home screen, below the other apps. Allowed: {list}. Not allowed: {list}.
- **Primary:** Add {app}
- **Then:** PIN (T1a, fresh). Then "Done. {app} is on the home screen." with **OK**.
- **Notes:** approval is bound to package name and signing-certificate digest (approved-apps). If the PIN step
  is abandoned, the app stays hidden.

## T5. Apps on this phone

**Spec:** calm-home-screen ("always on"); approved-apps (remove is immediate and reversible).

A list in home-screen order. Phone, Messages and Emergency are at the top, each marked **"Always on"**, with no
remove action and no order buttons. Every other app has **Move up** and **Move down** buttons (not dragging, which
is hard at large text sizes) and opens its own page when tapped:

**{app} page**
- Version {installed} · Added {date} · Last updated {date}
- Web-leak note, with the status line from T4a
- "What {app} can use" (current choices) → **Change what {app} can use** (fresh PIN; reuses T4c;
  [Open question 4](README.md#open-questions-for-the-lead))
- **Primary:** Remove from {user}'s phone

**Remove confirm**
- **Title:** Remove {app}?
- **Body:** {app} will disappear from the home screen straight away. Nothing is deleted: if you add it again
  later, {user}'s things in it will still be there.
- **Primary:** Remove
- **Then:** PIN (fresh). "Removed."

## T6. An app didn't update

**Spec:** approved-apps ("Unresolved failures SHALL stay listed in trusted-person mode").

- **Title:** {app} didn't update
- **Body:** At the monthly check on {date}, you noted that {app} didn't update. The usual fixes are: try again at
  the next monthly check, or ask {helper}.
  {app} still opens. Plainphone can't tell whether a newer version exists.
- **Primary:** It's sorted now
- **Secondary:** Keep on the list

## T7. An app was installed that you haven't added

**Spec:** approved-apps ("Unapproved apps cannot be opened"; preinstall pushes).

- **Title:** An app appeared that you haven't added
- **Body:** {app}, made by {developer}, was installed on {date}. It wasn't added in a Play Store session, so it's
  hidden and can't be opened. Phones sometimes install apps on their own.
- **Primary:** Leave it hidden
- **Secondary:** Add it → T4 (with a fresh PIN)
- **Notes:** if hiding failed, the title becomes "An app appeared that Plainphone couldn't hide" and the body adds:
  "It still can't be opened. It might run in the background. Ask {helper} to remove it." The item also counts
  as "Plainphone needs attention".

## T8. Texts to you aren't sending

**Spec:** trusted-person-controls (protected-state table: `SEND_SMS` row, "trusted person told; requests still
listed").

- **Title:** Texts to you aren't sending
- **Body:** Plainphone can't send texts from this phone right now, so you may not hear about {user}'s requests or
  the monthly check. Requests are still saved here.
  Calls and texts that {user} sends still work.
- **Primary:** Try a test text
- **Secondary:** Ask {helper} (shows the helper details)
