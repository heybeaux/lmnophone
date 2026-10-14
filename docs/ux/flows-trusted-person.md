# Flows for the trusted person

Words, rules and placeholders are in [README.md](README.md). The trusted person is `{tp}`; the phone belongs to
`{user}`. Everything here happens on `{user}`'s phone, in `{tp}`'s hands.

**Tone.** `{tp}` is looking after a phone for someone they care about. The words are about helping `{user}`,
never about watching them. The mode's own name on screen is **"Looking after {user}'s phone"**.

**Two trusted people, and self-managed mode.** A phone can have one or two trusted people, who are equals, or be
self-managed. The screens for that (Who are you?, trusted people, 24-hour waits, the stuck-change and recovered
screens, self-managed settings and History) are in [flows-people-modes-and-waits.md](flows-people-modes-and-waits.md).
With two people, `{tp}` below is the person who chose their name on "Who are you?".

**Fresh PIN.** trusted-person-controls requires a fresh PIN (or recovery code) for each of: adding an app,
removing an app, starting any session, installing an LMNOPhone update, turning LMNOPhone off, starting or skipping
any waiting change, and changing self-managed waits and limits. Entering the mode
also needs the PIN. The first time a second PIN is asked for, one line explains why:
"LMNOPhone asks for your PIN for each change, so nobody else can make one if the phone is put down."

**Long names at the largest text size** ([PLA-130](/PLA/issues/PLA-130)). At 200% text on a 360 × 640 dp screen, a
very long name or app name can fill the black band and the title, so the space left between the band and the
pinned buttons is shorter than the title. The rule, for every task screen with pinned buttons:

> **If the whole title and at least one line of the body don't fit between the band and the pinned buttons, the
> whole page scrolls.** The band, title, body and buttons then scroll together as one page, with the buttons right
> after the body. Otherwise the buttons stay pinned, as usual.

- Measure with the real words, at the current text size and display size, when the screen opens. Check again only
  if the text size, display size or rotation changes, never while scrolling, so the page doesn't jump.
- A whole-page screen opens at the top, with the band showing. Nothing is cut or made smaller. The buttons keep
  their size and order (the secondary link above the primary button). Android's own scrollbar shows there's more.
- On the PIN screens the keypad counts as body.
- The home screen has its own rule ([design-tokens.md](design-tokens.md#when-the-screen-is-short)).

---

## T1. PIN entry

**Spec:** trusted-person-controls (PIN lockout schedule; recovery code; fresh authority; two trusted people).

With two trusted people, every PIN entry starts with **Who are you?**
([M1](flows-people-modes-and-waits.md#m1-who-are-you)). Each person's lockout and recovery code are their own
([M1b](flows-people-modes-and-waits.md#m1b-one-person-is-waiting-after-too-many-tries),
[M1c](flows-people-modes-and-waits.md#m1c-recovery-code-with-two-people)).

### T1a. Enter PIN

- **Title:** {tp}'s PIN
- **Body:** Enter your PIN to look after {user}'s phone.
- **Keypad:** large digits, 6 or more. Dots, not digits, as they are typed.
- **Primary:** Continue
- **Secondary:** Forgot PIN? Use recovery code
- **Wrong PIN (1–4):** "That PIN isn't right. Try again." No count shown.
- **Notes:** `FLAG_SECURE`. 9-1-1 is still reachable: Home always returns to the home screen, where Phone is.
- **Self-managed mode** (the user holds the PIN; no "Who are you?"):
  - **Title:** Your PIN
  - **Body:** Enter your PIN to make changes on this phone.
  - Everything else as above.

### T1b. Waiting after too many tries

- **Title:** Too many tries
- **Body:** Wait {time} before trying again. You can use the recovery code now instead (the long code you wrote
  down when LMNOPhone was set up).
  Calls, texts and Emergency still work.
- **Primary:** Use recovery code
- **Secondary:** OK
- **Notes:** `{time}` is "30 seconds", "1 minute", "2 minutes"… "1 hour" (doubling from failure 5, capped at 1
  hour). It counts down in whole minutes only (seconds below 1 minute) and survives a restart. Typing during the
  wait does nothing: the keypad is hidden. From failure 10, the SMS in the
  [texts table](README.md#texts-lmnophone-sends) is sent once.

### T1c. Recovery code

- **Title:** Enter recovery code
- **Body:** This is the long code you wrote down when LMNOPhone was set up. Upper and lower case don't matter.
- **Primary:** Continue
- **Then:** "Choose a new PIN" (6 or more digits, typed twice), then "Here is your new recovery code" (shown once,
  same screen as setup [S3b](flows-setup.md#s3-pins-and-recovery-codes-step-3-of-8)). The old PIN and old code stop working at
  the same moment.
- **Note on screen, once:** "This code can't get past Google's reset protection if the phone is ever wiped. Only
  your Google account can do that."
- **Wrong code (1–4):** "That code isn't right. Check it against what you wrote down, and try again." No count
  shown.
- **Notes:** separate counter and the same waiting schedule as the PIN. A PIN wait never blocks this screen.

### T1d. Waiting after too many recovery-code tries

The recovery code's own lockout ([PLA-130](/PLA/issues/PLA-130)). Same tone as T1b.

- **Title:** Too many recovery code tries
- **Body:**
  Wait {time} before trying the recovery code again. Then check it against the code you wrote down, one letter at
  a time. {pin line}
  Calls, texts and Emergency still work. If the code is lost, call {helper}.
- **`{pin line}`, Primary and Secondary:**

  | When | `{pin line}` | Primary | Secondary |
  |---|---|---|---|
  | The PIN is not waiting | Your PIN still works, if you remember it. | Enter PIN instead → T1a | OK |
  | The PIN is waiting too | You can try your PIN again in {pin time}. | OK | (none) |
  | Reached from Skip the wait ([M3e](flows-people-modes-and-waits.md#m3e-skip-the-wait-with-a-recovery-code)) | Nothing was skipped. The change is still waiting. | OK → M3d | (none) |

- **Two trusted people:** title "Too many tries for {name}'s recovery code". `{pin line}` says "{name}'s PIN", and
  one more sentence is added: "{other}'s PIN still works." The secondary becomes **Choose someone else** → M1a.
- **Self-managed mode:** as for one person. The Skip row never applies (no skipping in self-managed mode).
- **Notes:** `{time}` and `{pin time}` work as in T1b (doubling from failure 5, capped at 1 hour; whole minutes
  only; survives a restart). The code field is hidden during the wait. It never locks for good: after the wait,
  the code can always be tried again. **OK** goes back to where the PIN screen was opened from; Home always goes to
  the home screen. No text is sent for wrong recovery codes (the 10-wrong-entries text is for PINs only).

## T2. Looking after {user}'s phone (front page)

**Spec:** trusted-person-controls; approved-apps; device-lockdown (software changed); calm-home-screen
(always-present apps).

A menu page. At the top, a short **"Needs you"** list, only when something is waiting. Below it, the menu.

### "Needs you" items (in this order; each opens its own screen)

| Item text | When | Opens |
|---|---|---|
| **Waiting: {change}** | A change is waiting 24 hours, or being made | [M3d](flows-people-modes-and-waits.md#m3d-the-change-on-the-trusted-person-side) |
| **Reset protection not updated – call the helper** | A change of people or mode is stuck | [M4](flows-people-modes-and-waits.md#m4-a-change-is-stuck) |
| **LMNOPhone needs repair** | LMNOPhone can't read who is in charge | [M4, Repair](flows-people-modes-and-waits.md#repair-credential-store-damaged) |
| **LMNOPhone recovered from an interrupted change** | Once, after a lost change record | [M5](flows-people-modes-and-waits.md#m5-after-an-interrupted-change) |
| **Not ready to hand back – call the helper** | A protection check failed after a session or update | [P5](flows-sessions.md#p5-not-ready-to-hand-back) |
| **LMNOPhone needs attention** | Same conditions as the home line | [L3](flows-limits-and-off.md#l3-lmnophone-needs-attention-trusted-person-view) |
| **Phone software changed – review pending** | The phone is on software LMNOPhone hasn't reviewed | [L2](flows-limits-and-off.md#l2-phone-software-changed--review-pending) |
| **{n} requests from {user}** | Unanswered app requests | T3 |
| **Check for updates – due** (or "…overdue by {n} days") | From the due date | [P3](flows-sessions.md#p3-monthly-check) (the same path as the menu row) |
| **{app} didn't update** (one row per app) | Recorded at a monthly check, not yet resolved | T6 |
| **An app was installed that you haven't added** | Any install outside a session | T7 |
| **Texts to you aren't sending** | LMNOPhone's SMS permission check failed | T8 |

### Menu

| Row | Small line | Opens |
|---|---|---|
| **Add an app** | Opens the Play Store for 10 minutes | [P1](flows-sessions.md#p1-before-a-session) (Play) |
| **Apps on this phone** | Change the order, remove, what each can use | T5 |
| **Requests from {user}** | (count, if any) | T3 |
| **Check for updates** | Last done: {date} | [P3](flows-sessions.md#p3-monthly-check) |
| **Open full phone settings** | For 5 minutes, for things Phone settings doesn't have, like the screen lock | [P1](flows-sessions.md#p1-before-a-session) (Settings) |
| **What LMNOPhone can't stop** | Read this before adding apps | [L1](flows-limits-and-off.md#l1-what-lmnophone-cant-stop) |
| **Trusted people** | {everyone} | [M2](flows-people-modes-and-waits.md#m2-trusted-people) |
| **History** | Changes on this phone, last 90 days | [M8](flows-people-modes-and-waits.md#m8-history) |
| **Install an LMNOPhone update** | Only with {helper}, when {helper} asks | [P6](flows-sessions.md#p6-install-an-lmnophone-update) |
| **Change PIN** | (none) | T1-style: old PIN, new PIN twice |
| **New recovery code** | (none) | Fresh PIN, then T1c/S3b code shown once; the old code stops working |
| **Turn LMNOPhone off** | Makes this an ordinary phone, after 24 hours | [L4](flows-limits-and-off.md#l4-turning-lmnophone-off) |

Under the title, one line, always shown: "This is for adding apps and keeping the phone working. You can't see
{user}'s messages, photos, or what {user} does in apps." (Cold readers in both roles said the trusted-person side
felt like surveillance without it; see [copy-test.md](copy-test.md).)

In self-managed mode this page is **Your settings** ([M7a](flows-people-modes-and-waits.md#m7a-your-settings)).

A **Done** button is pinned at the bottom: "Done – back to {user}'s home screen". It is the only primary action.

## T3. Requests from {user}

**Spec:** trusted-person-controls ("shown as 'From [user]:' in plain text").

Each request is one card:

```
From the partner:
"the bus times app, to check the ferry bus"
Thursday 8 Oct, 10:40 · Text sent to you ✓      (or: "Text didn't send")
[ Find it in the Play Store ]   [ Not now ]
```

- The quoted words are shown exactly as typed, in plain text, inside quotation marks, never as a link.
- **Find it in the Play Store** starts a Play session (P1). The request is attached to the session, but the
  approval is for whatever app actually gets installed (T4b).
- **Not now** asks: "Remove this request? {user} won't be told." → **Remove** / **Keep it**.
  (The user is not notified by LMNOPhone; `{tp}` tells them in person. This keeps LMNOPhone from being a
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

    Under "Not reviewed yet", one more line (Beaux asked who reviews, and how to ask): "The LMNOPhone
    team checks apps for this and writes a note for each one. They haven't checked {app}. To ask them to, tell
    {helper}."
- **Primary:** Next
- **Secondary:** Don't add → "{app} stays hidden. It can't be opened." **OK**.

### T4b. Not reviewed yet: extra confirmation

Only when there is no catalog entry (web-containment, "Approving an unreviewed app").

- **Title:** Add an app nobody has checked?
- **Body:** The LMNOPhone team hasn't checked whether {app} can show web pages inside it. Some apps can show any
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
- **Self-managed body** ([PLA-130](/PLA/issues/PLA-130)). In self-managed mode these choices can't be changed in one
  step ([PLA-121](/PLA/issues/PLA-121)). The way to change them is to remove the app, then add it again and choose
  again. So the body is two blocks:
  - "Choose what {app} can use. {app} won't ask you about these later. If you choose "Don't allow", the parts of
    {app} that need it won't work."
  - "To change these later, remove {app} in "Apps on this phone", then add it again with "Add a hidden app". That
    takes a {wait}-minute wait and one opening. Your things in {app} stay."
- **Copy-test change (round 3, Beaux):** the earlier body said "{user} won't be asked later" and "You can change this
  later" without saying who or how. Beaux read it as {user} being locked out and having to bring
  the phone to {tp}. That is how it works, so the screen now says it plainly. The change path depends on
  [Open question 4](README.md#open-questions-for-the-lead).
- One block per Android permission group the app requests. Each block: plain name, one line of why (from the
  catalog if there is an entry, otherwise the generic line below), and two large choice buttons.
- **Nothing is pre-selected.** Next stays disabled until every block has a choice. (A forced choice is slower,
  but it means nothing is allowed by accident.)
- **Primary:** Next
- **If Android won't let LMNOPhone switch a permission on** (Q1 will tell us for camera, microphone and
  location): the block shows "Android doesn't let LMNOPhone switch this on. Parts of {app} that need it won't
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

A list in home-screen order. Messages and Phone are at the bottom, as on the home screen, each marked **"Always on"**, with no
remove action and no order buttons. Every other app has **Move up** and **Move down** buttons (not dragging, which
is hard at large text sizes) and opens its own page when tapped:

**{app} page**
- Version {installed} · Added {date} · Last updated {date}
- Web-leak note, with the status line from T4a
- "What {app} can use" (current choices) → **Change what {app} can use** (fresh PIN; reuses T4c;
  [Open question 4](README.md#open-questions-for-the-lead)). Supervised mode only.
- **Self-managed:** "What {app} can use" shows the current choices with no change button, and one line under them:
  "To change these, remove {app}, then add it again with "Add a hidden app" and choose again."
- **Primary:** Remove from {user}'s phone (self-managed: **Remove {app}**)

**Remove confirm**
- **Title:** Remove {app}?
- **Body:** {app} will disappear from the home screen straight away. Nothing is deleted: if you add it again
  later, {user}'s things in it will still be there.
  Self-managed: "{app} will disappear from the home screen straight away. Nothing is deleted: if you add it again
  with "Add a hidden app", your things in it will still be there."
- **Primary:** Remove
- **Then:** PIN (fresh; self-managed: the user's PIN, no wait and no opening used). "Removed."

### T5 preview subset

The first preview includes order, installed version, and fresh-PIN removal.
It omits permissions, web-leak notes, dates, self-managed wording, and adding
hidden apps. The detail metadata is **Version {installed}**.
If saving fails before replacement, reuse the existing save-failure sentence:
**"That didn't save. Nothing changed. Try again."** Return to the list with
**Back**. A failed post-save check uses the existing **LMNOPhone needs attention**
screen; it must not say "Removed." or "Nothing changed."

## T6. An app didn't update

**Spec:** approved-apps ("Unresolved failures SHALL stay listed in trusted-person mode").

- **Title:** {app} didn't update
- **Body:** At the monthly check on {date}, you noted that {app} didn't update. The usual fixes are: try again at
  the next monthly check, or ask {helper}.
  {app} still opens. LMNOPhone can't tell whether a newer version exists.
- **Primary:** It's sorted now
- **Secondary:** Keep on the list

## T7. An app was installed that you haven't added

**Spec:** approved-apps ("Unapproved apps cannot be opened"; preinstall pushes).

- **Title:** An app appeared that you haven't added
- **Body:** {app}, made by {developer}, was installed on {date}. It wasn't added in a Play Store session, so it's
  hidden and can't be opened. Phones sometimes install apps on their own.
- **Primary:** Leave it hidden
- **Secondary:** Add it → T4 (with a fresh PIN)
- **Notes:** if hiding failed, the title becomes "An app appeared that LMNOPhone couldn't hide" and the body adds:
  "It still can't be opened. It might run in the background. Ask {helper} to remove it." The item also counts
  as "LMNOPhone needs attention".

## T8. Texts to you aren't sending

**Spec:** trusted-person-controls (protected-state table: `SEND_SMS` row, "trusted person told; requests still
listed").

- **Title:** Texts to you aren't sending
- **Body:** LMNOPhone can't send texts from this phone right now, so you may not hear about {user}'s requests or
  the monthly check. Requests are still saved here.
  Calls and texts that {user} sends still work.
- **Primary:** Try a test text
- **Secondary:** Ask {helper} (shows the helper details)


### T4 preview: add an installed hidden app

Authorized scope: [PLA-517](/PLA/issues/PLA-517). At the end of T5, **Add a hidden app**
opens eligible hidden apps in name order, with monochrome app icons. Empty list:
"There are no hidden apps to add." Unreadable list: "Can't check the apps right now".
Both have **Back**. T4a, T4b, T4d and a fresh app-bound PIN follow. T4d omits the
Allowed/Not allowed sentence because permissions stay as Android has them now.
App details, Remove confirmation and T4d have **Back**. T4a/T4b retain **Don't add**.
No developer is shown unless reported; there is no More about this app route in
this preview. Recovery code shows the existing unsupported-feature explanation.

## T3 production request subset and recipient setup (PLA-525)

Lead decision on PLA-525: PIN settings → **Who gets requests**, with a fresh PIN
before reading the number. Optional **Name (optional)**, required **Phone number**.
A new or changed recipient requires **Continue**, then **Send requests to this
person?**, with the entered name (if any) and number, then **Save**, then a fresh
PIN before the durable write. Details show the saved name/number, **Change** and
**Remove**. Removal asks **Stop sending app requests? Saved requests will stay on
this phone.**, then **Remove** and a fresh PIN. A failed save says **The change
could not be saved. Try again.** Back or leaving the activity writes nothing.

The Family picker is omitted from this bounded slice. No Family entry is selected
implicitly. This setting does not alter PIN credentials or trusted-person authority.
The Requests menu shows saved text, day and the truthful U5 send result. Empty:
**No requests saved on this phone.** No Find, Add, Remove-request or reply action.

The neutral SMS template, when the phone user's name is not configured, is:
`LMNOPhone: An app was requested: “{request}”. To add it, you need to be with the phone. On the phone: More, then “PIN settings”, then “Requests”.`
Copy additions are included in the same focused Product & UX review as the code.
