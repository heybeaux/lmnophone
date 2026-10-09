# Plainphone words and flows

Owner: Product & UX Designer. Task: [PLA-12](/PLA/issues/PLA-12), covering tasks 5.1, 5.2 and 5.3 in
`openspec/changes/add-plainphone-spike-and-mvp/tasks.md`.
Status: **draft for the Verifier.** Nothing here is app code. Every screen traces to a spec requirement; where the
spec is silent, the gap is listed under [Open questions](#open-questions-for-the-lead).

## Files

| File | What it covers |
|---|---|
| [flows-user.md](flows-user.md) | The person using the phone: home screen and its two notice lines, More, ask for an app, blocked link, Wi-Fi messages, Plainphone's own settings, "About this phone" |
| [flows-trusted-person.md](flows-trusted-person.md) | Trusted-person mode: PIN and recovery code, the mode's front page, requests, adding an app and choosing its permissions, removing an app |
| [flows-sessions.md](flows-sessions.md) | Supervised sessions ("Stay with the phone until you tap Done"), the monthly check, Plainphone updates from the helper's computer |
| [flows-limits-and-off.md](flows-limits-and-off.md) | "What Plainphone can't stop", "Phone software changed – review pending", "Plainphone needs attention", turning Plainphone off |
| [flows-setup.md](flows-setup.md) | On-phone setup (steps 1–7) and the handover check |
| [starter-catalog.md](starter-catalog.md) | Web-leak notes for Slack, Spotify, Telegram, Phone, Messages and Camera, each with the version reviewed |
| [copy-test.md](copy-test.md) | How the copy was tested on people who had never seen it, and what they said |
| [../setup-guide.md](../setup-guide.md) | The written guide for the helper (task 5.3) |

## Who we are designing for

There are three roles. The same real person can hold more than one.

| Role | Who, for the first two phones | What they need from Plainphone |
|---|---|---|
| **The person using the phone** ("the user") | the trusted person on her phone; Beaux on his | A calm phone that does the basics without thinking. A clear way to ask for things. Never to feel watched. |
| **The trusted person** | Beaux for the trusted person's phone; the trusted person for Beaux's | To say yes or no to apps, with the phone in their hands, and to know honestly what they can't control. |
| **The helper** | Beaux, for both phones | A guide they can follow without having written it, and a phone that says plainly when something is wrong. |

**Design for strangers.** Assume the reader has never heard of Plainphone, has never opened Android settings,
and is reading at the largest text size. Beaux and the trusted person are not technical; neither is anyone who might copy this.

**Support, not surveillance.** The trusted person approves apps. They do not watch. Plainphone sends no data
anywhere except the few texts listed below, and the user can read exactly what those are on the phone
("About this phone", in [flows-user.md](flows-user.md#u8-about-this-phone)). Every trusted-person action happens
**on the user's phone, with the phone in the trusted person's hands**, so the user always knows when it happens.

## Principles for every screen

1. **One primary action per task screen.** It is a full-width button pinned to the bottom of the screen, so it
   stays visible at any text size. At most one secondary action, shown as a text link above it. Menu pages
   (home, More, the trusted-person front page) are lists of equal choices and have no primary button; see
   [Open question 1](#open-questions-for-the-lead).
2. **Plain words.** Short sentences. Name people by first name. No Android words (see the word list).
3. **Say what still works.** Any message about a problem says that calls, texts and Emergency still work, when
   that's true.
4. **Calm.** No red except the Emergency button and the one "turn off" confirmation. No exclamation marks.
   No countdown timers on the user's screens. No badges.
5. **No pronouns for named people.** Copy repeats the name ("Ask Beaux") instead of guessing "he" or "she".
6. **Honest.** Never say "safe", "secure", "protected" or "blocked forever". Say what Plainphone does.

### Readable at the largest text size

Android 14 and later scale text up to 200%. Every screen in these files was written to these limits:

| Element | Limit at normal size | Behaviour at 200% |
|---|---|---|
| Screen title | 6 words | Wraps to at most 2 lines |
| Body text | 3 short sentences per block, 2 blocks | Scrolls vertically; never horizontally |
| Button label | 3 words | Wraps inside the button; the button grows taller, never wider than the screen |
| List row (app, setting) | Icon + 1–3 words | Icon stays 48dp; label wraps under it if needed |
| Primary button | Pinned to the bottom, full width, at least 56dp tall | Stays pinned; content above scrolls |

Touch targets are at least 48dp, and contrast meets WCAG 2.2 AA (calm-home-screen, "Readable by default").
The home-screen layout at 200% is mocked up in [flows-user.md](flows-user.md#u1-home-screen).

## Words we use, and words we never show

The left column must never appear on a screen the user or trusted person sees. The setup guide may use a few of
them, once, with an explanation, where the helper must type or tap them on a real Android screen.

| Never on screen | Say instead |
|---|---|
| Device Owner, device admin, MDM, managed device | "Plainphone looks after this phone" |
| allowlist, whitelist, approved list | "apps on this phone", "added apps" |
| approve (to the user) | "add" (the trusted person sees "Add" too; "approve" is only in these docs) |
| lock task, kiosk, pinning | (nothing; the user never needs to know) |
| package, APK, build, fingerprint, OTA | "app", "Plainphone update", "phone software update" |
| policy, restriction, readback | "protections", "checks" |
| adb, USB debugging (outside the guide) | "the helper's computer" |
| supervised session | "open the Play Store for 10 minutes", "open full phone settings for 5 minutes" |
| FRP, factory reset protection | "Google's reset protection" (trusted person and helper only) |
| `userId`, People API (outside the guide) | "the account number from Google" |
| runtime permission | "what [app] can use" |
| captive portal | "Wi-Fi that needs a sign-in page" |
| WebView, in-app browser | "web pages inside the app" |
| inventory, never-hide, floor | (nothing; helper-facing details only) |

Placeholders in copy: `{user}` is the phone user's first name, `{tp}` the trusted person's first name, `{helper}`
the helper's first name, `{app}` the app's name as Android reports it.

## Texts Plainphone sends

These are the only messages Plainphone sends. All go from the user's phone, by SMS, to fixed numbers entered at
setup. The user can read this list on the phone ("About this phone").

| When | To | Text | Spec |
|---|---|---|---|
| The user asks for an app | {tp} | `Plainphone: {user} asked for an app: "{request}". To add it, you need to be with {user} and the phone. On the phone: More, then "For {tp}".` | trusted-person-controls, app requests |
| The user forwards a blocked link | {tp} | `Plainphone: {user} would like to look at this link with you: {host}{path}` | web-containment, isolated screen |
| Monthly check is due (retried daily up to 7 days if sending fails; never duplicated) | {tp} | `Plainphone: time for {user}'s monthly phone check. It takes about 10 minutes, with {user}'s phone in your hands. On the phone: More, then "For {tp}", then "Monthly check".` | approved-apps, monthly check |
| Google Play services unchanged for 30 days | {tp} | `Plainphone: Google Play services on {user}'s phone hasn't updated in 30 days. This is often fine. Please look at it during the next monthly check.` | approved-apps; design D3 |
| 10 wrong PIN entries | {tp} | `Plainphone: the PIN on {user}'s phone was entered wrong 10 times. If that wasn't you, have a look together when you can.` | trusted-person-controls, lockout |
| A phone software update is pending | {tp} and {helper} | `A phone software update is coming. Plainphone needs a review.` (exact text required by spec) | device-lockdown, system updates |
| The phone is on software Plainphone hasn't reviewed | {tp} and {helper} | `Plainphone: {user}'s phone has new phone software that hasn't been reviewed yet. Calls, texts and Emergency work. The helper needs to install a reviewed Plainphone update.` | device-lockdown, unknown build |
| Both policy copies broken on unknown software | {tp} and {helper} | `Plainphone: {user}'s phone needs the helper. Calls, texts and Emergency work. Please don't wait more than a few days.` | device-lockdown, policy storage |
| Setup test text | {tp} | `Plainphone test from {user}'s phone. If you got this, tell the helper "yes".` | guided-setup, handover check |

## Open questions for the Lead

These are places where the spec and good copy pull in different directions, or the spec is silent. Each has a
recommendation; none changes a promise.

1. **"Nothing else" on the home screen.** calm-home-screen's "First look" scenario says the user sees the time,
   date, Emergency, Phone, Messages and their apps, "and nothing else". But the user also needs a way to ask for
   an app, reach Plainphone's own settings, and hand the phone to the trusted person. **Recommendation:** one
   last tile, "More", below the apps, styled like an app tile. Amend the scenario to "…and their other approved
   apps, then More". Also confirm that "one primary action" applies to task screens, not menu pages.
2. **Helper and trusted person are the same person.** On the trusted person's phone, Beaux is both. guided-setup says the
   helper confirms "the trusted person, and not the helper, has the recovery code". That can't be satisfied as
   written. **Recommendation:** when the two are the same person, the setup screen asks instead that the code is
   kept **away from the phone and away from the user** ([flows-setup.md](flows-setup.md#s2-pin-and-recovery-code-step-2-of-7)).
3. **Home line on unknown phone software.** The spec puts "Phone software changed – review pending" in
   trusted-person mode only. **Recommendation:** also show "Plainphone needs attention" on the user's home screen
   while the build is unknown, so the user knows to mention it. It is calm and true.
4. **Changing an app's permissions later.** The spec only covers choosing permissions at approval.
   **Recommendation:** an app's page in trusted-person mode offers "Change what {app} can use", with a fresh PIN.
   It reuses the approval screen and `setPermissionGrantState`.
5. **Where "Done" lives in a session.** The session ends when the trusted person leaves the Play Store or
   Settings. So the Done button is on Plainphone's closing screen, after the checks pass, and means "checked;
   hand the phone back". A notification during the session gives a one-tap way back. The AE should confirm in Q1
   that the `NOTIFICATIONS` lock-task feature shows Plainphone's own notification during a session.
6. **Turning off when the helper is the trusted person.** No change needed; noted only because "two plain
   confirmations" plus a PIN is the same for both.
7. **Mail apps that need to add an account (Outlook).** PLA-5 B-Q4 (Outlook 5.2639.2) found that adding a
   Google account to Outlook fails under lockdown: it hands off to Google Play services, which is blocked, with no
   web fallback. A Microsoft account isn't tested yet, and Plainphone also blocks account changes after setup
   (`DISALLOW_MODIFY_ACCOUNTS`). No supervised session lifts either ([starter-catalog.md](starter-catalog.md#outlook-microsoft)).
   Outlook's Privacy & Cookies web leak is a separate matter, already accepted by Beaux (ADR 0001 Risk 3).
   **Recommendation:** ask Beaux which account the trusted person uses in Outlook. If it is her Gmail address, the Gmail app
   alone may cover her need, which is the calmest answer. If it is an Outlook.com or work address, wait for the
   signed-in B-Q4 result; if that fails too, the Lead decides between a new, narrow "add an account" session (a
   spec change, with its own tests) and leaving Outlook off.
