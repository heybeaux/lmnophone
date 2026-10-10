# LMNOPhone words and flows

Owner: Product & UX Designer. Task: [PLA-12](/PLA/issues/PLA-12), covering tasks 5.1, 5.2 and 5.3 in
`openspec/changes/add-plainphone-spike-and-mvp/tasks.md`.
Status: **draft for the Verifier.** Nothing here is app code. Every screen traces to a spec requirement; where the
spec is silent, the gap is listed under [Open questions](#open-questions-for-the-lead).

## Files

| File | What it covers |
|---|---|
| [flows-user.md](flows-user.md) | The person using the phone: home screen and its notice line, More, ask for an app, blocked link, Wi-Fi messages, LMNOPhone's own settings, "About this phone", cancelling a waiting change (U9), the self-managed countdown (U10) |
| [flows-trusted-person.md](flows-trusted-person.md) | Trusted-person mode: PIN and recovery code, the mode's front page, requests, adding an app and choosing its permissions, removing an app |
| [flows-sessions.md](flows-sessions.md) | Supervised sessions ("Stay with the phone until you tap Done"), the monthly check, LMNOPhone updates from the helper's computer |
| [flows-limits-and-off.md](flows-limits-and-off.md) | "What LMNOPhone can't stop", "Phone software changed – review pending", "LMNOPhone needs attention", turning LMNOPhone off |
| [flows-setup.md](flows-setup.md) | On-phone setup (steps 1–8: mode, people, PINs, connect and privacy steps, reset protection, apps, limits, unplug) and the handover check |
| [flows-people-modes-and-waits.md](flows-people-modes-and-waits.md) | Two trusted people ("Who are you?"), changes that wait 24 hours (start, cancel, skip, stuck, recovered), switching mode, self-managed mode, History, every text, and the exact-strings index with flags ([PLA-42](/PLA/issues/PLA-42)) |
| [starter-catalog.md](starter-catalog.md) | Web-leak notes for Slack, Spotify, Telegram, Phone, Messages and Camera, each with the version reviewed |
| [design-tokens.md](design-tokens.md) | The look: Lexend, black and white only, sizes, spacing, components; mockups in [mockups/](mockups/plainphone-screens.html) ([PLA-36](/PLA/issues/PLA-36)) |
| [copy-test.md](copy-test.md) | How the copy was tested on people who had never seen it, and what they said |
| [../setup-guide.md](../setup-guide.md) | The written guide for the helper (task 5.3) |

## Who we are designing for

There are three roles. The same real person can hold more than one.

| Role | Who, for the first two phones | What they need from LMNOPhone |
|---|---|---|
| **The person using the phone** ("the user") | the partner on her phone; Beaux on his | A calm phone that does the basics without thinking. A clear way to ask for things. Never to feel watched. |
| **The trusted person** (one or two per phone, as equals) | Beaux for the partner's phone; the partner for Beaux's | To say yes or no to apps, with the phone in their hands, and to know honestly what they can't control. |
| **The helper** | Beaux, for both phones | A guide they can follow without having written it, and a phone that says plainly when something is wrong. |

A phone can also be **self-managed**: the user holds the PIN and is their own trusted person. LMNOPhone then adds
waits and limits, and says plainly that it is friction, not a lock.

**Design for strangers.** Assume the reader has never heard of LMNOPhone, has never opened Android settings,
and is reading at the largest text size. The two initial users are not technical; neither is anyone who might copy this.

**Support, not surveillance.** The trusted person approves apps. They do not watch. LMNOPhone sends no data
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
4. **Calm.** Black, white and grey only: no colour anywhere, including the Emergency 9-1-1 row in More and the turn-off confirmation ([design-tokens.md](design-tokens.md)). No exclamation marks.
   No countdown timers on the user's screens. No badges.
5. **No pronouns for named people.** Copy repeats the name ("Ask Beaux") instead of guessing "he" or "she".
6. **Honest.** Never say "safe", "secure", "protected" or "blocked forever". Say what LMNOPhone does.

### Readable at the largest text size

Android 14 and later scale text up to 200%. Every screen in these files was written to these limits:

| Element | Limit at normal size | Behaviour at 200% |
|---|---|---|
| Screen title | 6 words | Fits 2 lines; a long name may make it 3. Never cut |
| Body text | 3 short sentences per block, 2 blocks | Scrolls vertically; never horizontally |
| Button label | 3 words | Wraps inside the button; the button grows taller, never wider than the screen |
| List row (app, setting) | 1–3 words, no icon | Label wraps; the row grows taller, at least 56dp |
| Primary button | Pinned to the bottom, full width, at least 56dp tall | Stays pinned; content above scrolls |

Touch targets are at least 48dp, and contrast meets WCAG 2.2 AA (calm-home-screen, "Readable by default").
The home-screen layout at 200% is mocked up in [flows-user.md](flows-user.md#u1-home-screen) and in [design-tokens.md](design-tokens.md#when-the-screen-is-short).

## Words we use, and words we never show

The left column must never appear on a screen the user or trusted person sees. The setup guide may use a few of
them, once, with an explanation, where the helper must type or tap them on a real Android screen.

| Never on screen | Say instead |
|---|---|
| Device Owner, device admin, MDM, managed device | "LMNOPhone looks after this phone" |
| allowlist, whitelist, approved list | "apps on this phone", "added apps" |
| approve (to the user) | "add" (the trusted person sees "Add" too; "approve" is only in these docs) |
| lock task, kiosk, pinning | (nothing; the user never needs to know) |
| package, APK, build, fingerprint, OTA | "app", "LMNOPhone update", "phone software update" |
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

**Counts, names and days are filled in by the screens, not built elsewhere** (PLA-47, 2026-10-09). The words around
a value belong in the app's string resources, so they can be checked against these files and translated later.
- **Counts:** any `{n}` takes the singular when it is 1: "Opening up in 1 minute", "1 app", "overdue by 1 day".
  Use Android `plurals`. Only the singular changes; the rest of the copy stays exact.
- **Two names:** the screens receive the names (one or two, in the order they were added) and join them: `{tps}` with
  "or" ("Sam or Jo"), `{everyone}` with "and" ("Sam and Jo"). One name stands alone. With no names, show nothing that
  needs a name and never crash; that is a bug upstream.
- **`{time}` for when a wait ends:** the screens receive the clock time (already in the phone's 12- or 24-hour style,
  like the home clock) and the day, and add the day word: "21:15 tomorrow" for the next day; "21:15 Saturday" for 2 to
  6 days ahead; "21:15 Thu 15 Oct" for 7 days or more (the wait only counts time while the phone is on). A 24-hour wait
  can't end the same day; if a time is ever for today, show the clock time alone.
- **Keeping things together on one line:** a screen may use a non-breaking hyphen (U+2011) or non-breaking space
  where a line break would split something that must be read whole: "9‑1‑1", a PIN or cancel code, "{n} min".
  This doesn't count as changing the copy. TalkBack must still read it as written ("nine one one"); if it reads the
  character oddly, set the spoken text to the plain version.

## Texts LMNOPhone sends

These are the only messages LMNOPhone sends. All go from the user's phone, by SMS, to fixed numbers entered at
setup. The user can read this list on the phone ("About this phone"). **"{tp}" in this table means every trusted
person**, one or two. The texts for changes that wait 24 hours, for every use of a PIN (receipts), and for stuck
and recovered changes are in [M9](flows-people-modes-and-waits.md#m9-texts). A self-managed phone sends only the
texts addressed to {helper}.

| When | To | Text | Spec |
|---|---|---|---|
| The user asks for an app | {tp} | `LMNOPhone: {user} asked for an app: "{request}". To add it, you need to be with {user} and the phone. On the phone: More, then "For {tp}".` With two trusted people: `…To add it, one of you needs to be with {user} and the phone. On the phone: More, then "For Sam and Jo".` | trusted-person-controls, app requests |
| The user forwards a blocked link | {tp} | `LMNOPhone: {user} would like to look at this link with you: {host}{path}` | web-containment, isolated screen |
| Monthly check is due (retried daily up to 7 days if sending fails; never duplicated) | {tp} | `LMNOPhone: time for {user}'s monthly phone check. It takes about 10 minutes, with {user}'s phone in your hands. On the phone: More, then "For {tp}", then "Monthly check".` | approved-apps, monthly check |
| Google Play services unchanged for 30 days | {tp} | `LMNOPhone: Google Play services on {user}'s phone hasn't updated in 30 days. This is often fine. Please look at it during the next monthly check.` | approved-apps; design D3 |
| 10 wrong PIN entries | {tp} | `LMNOPhone: the PIN on {user}'s phone was entered wrong 10 times. If that wasn't you, have a look together when you can.` With two trusted people, the per-person version in [M9](flows-people-modes-and-waits.md#m9-texts). | trusted-person-controls, lockout |
| A phone software update is pending | {tp} and {helper} | `A phone software update is coming. LMNOPhone needs a review.` (exact text required by spec) | device-lockdown, system updates |
| The phone is on software LMNOPhone hasn't reviewed | {tp} and {helper} | `LMNOPhone: {user}'s phone has new phone software that hasn't been reviewed yet. Calls, texts and Emergency work. The helper needs to install a reviewed LMNOPhone update.` | device-lockdown, unknown build |
| Both policy copies broken on unknown software | {tp} and {helper} | `LMNOPhone: {user}'s phone needs the helper. Calls, texts and Emergency work. Please don't wait more than a few days.` | device-lockdown, policy storage |
| Setup test text | {tp} | `LMNOPhone test from {user}'s phone. If you got this, tell the helper "yes".` | guided-setup, handover check |

## Open questions for the Lead

These are places where the spec and good copy pull in different directions, or the spec is silent. Each has a
recommendation; none changes a promise.

1. **"Nothing else" on the home screen.** calm-home-screen's "First look" scenario says the user sees the time,
   date, Phone, Messages and their other approved apps, "and nothing else" (Emergency is no longer on the home
   screen: Beaux, Telegram 4487). But the user also needs a way to ask for
   an app, reach LMNOPhone's own settings, and hand the phone to the trusted person. **Recommendation:** one
   last control, "More", below the apps. **Visual direction settled in [PLA-285](/PLA/issues/PLA-285):**
   centered plain text, no subtitle, caret, border or decorative Home divider rules; retain its 64 dp touch target.
   The remaining spec wording suggestion is "…and their other approved apps, then More".
   "One primary action" applies to task screens, not menu pages.
2. **Helper and trusted person are the same person.** On the partner's phone, Beaux is both. guided-setup says the
   helper confirms "the trusted person, and not the helper, has the recovery code". That can't be satisfied as
   written. **Recommendation:** when the two are the same person, the setup screen asks instead that the code is
   kept **away from the phone and away from the user** ([flows-setup.md](flows-setup.md#s3-pins-and-recovery-codes-step-3-of-8)).
3. **Home line on unknown phone software.** The spec puts "Phone software changed – review pending" in
   trusted-person mode only. **Recommendation:** also show "LMNOPhone needs attention" on the user's home screen
   while the build is unknown, so the user knows to mention it. It is calm and true.
4. **Changing an app's permissions later.** The spec only covers choosing permissions at approval.
   **Recommendation:** an app's page in trusted-person mode offers "Change what {app} can use", with a fresh PIN.
   It reuses the approval screen and `setPermissionGrantState`.
5. **Where "Done" lives in a session.** The session ends when the trusted person leaves the Play Store or
   Settings. So the Done button is on LMNOPhone's closing screen, after the checks pass, and means "checked;
   hand the phone back". A notification during the session gives a one-tap way back. The AE should confirm in Q1
   that the `NOTIFICATIONS` lock-task feature shows LMNOPhone's own notification during a session.
6. **Turning off when the helper is the trusted person.** No change needed; noted only because "two plain
   confirmations" plus a PIN is the same for both.
7. **Mail apps that need to add an account (Outlook).** PLA-5 B-Q4 (Outlook 5.2639.2) found that adding a
   Google account to Outlook fails under lockdown: it hands off to Google Play services, which is blocked, with no
   web fallback. A Microsoft account isn't tested yet, and LMNOPhone also blocks account changes after setup
   (`DISALLOW_MODIFY_ACCOUNTS`). No supervised session lifts either ([starter-catalog.md](starter-catalog.md#outlook-microsoft)).
   Outlook's Privacy & Cookies web leak is a separate matter, already accepted by Beaux (ADR 0001 Risk 3).
   **Recommendation:** ask Beaux which account the partner uses in Outlook. If it is her Gmail address, the Gmail app
   alone may cover her need, which is the calmest answer. If it is an Outlook.com or work address, wait for the
   signed-in B-Q4 result; if that fails too, the Lead decides between a new, narrow "add an account" session (a
   spec change, with its own tests) and leaving Outlook off.

**From Beaux's copy test (round 3, [copy-test.md](copy-test.md#round-3-beaux-the-human-copy-test-for-this-stage)).** These are
product decisions, not wording. The copy doesn't promise any of them. **Status:** the Lead has answered them in the
[PLA-14 product-questions document](/PLA/issues/PLA-14#document-product-questions), and Beaux is choosing there.
The trusted-person and web-window flows wait for his choices. Copy already changed from the Lead's answers:
Screen J (flat battery), "can't stop" item 11 (Google), and one "Check for updates" path.

*Questions 8 and 11 are answered: Beaux chose two trusted people, a self-managed mode and a 24-hour wait (card
`c60170d6`), and approved the change in PLA-30. The copy is in
[flows-people-modes-and-waits.md](flows-people-modes-and-waits.md). They stay below as a record.*

8. **Changing the trusted person, or more than one.** Beaux asked "Can I change this person? Can I have
   multiple people in Sam's role?" The MVP has one trusted person, and no screen to change who it is.
   **Recommendation:** for the MVP, changing who it is means {helper} sets the phone up again; About this phone
   then says so in one line. More than one trusted person is a later change, with its own spec.
9. **Web for a few minutes.** "I would perhaps prefer the ability to turn web browsing on for 2-5 minutes." This
   is the same need as the Wi-Fi sign-in page ([PLA-17](/PLA/issues/PLA-17), [wifi-signin-override.md](wifi-signin-override.md)).
   **Recommendation:** decide both together, as one time-limited "open for a few minutes" mechanism, so the user
   learns one thing. If the answer is no, the blocked-link screen stays as it is.
10. **Automating the monthly app updates.** "Can we not automate this? Or make it simpler for me to navigate to?"
    Automatic updates conflict with the approved "block installs, update only in a supervised window" design.
    **Recommendation:** keep the design; ask the AE whether LMNOPhone can open the Play Store straight on its
    "Updates available" list, which removes the three steps of directions on the monthly-check screen.
11. **Turning LMNOPhone off as a way round.** "What if a kid has this phone and uses this as a bypass method?"
    Off needs the PIN or recovery code, so only someone who has those can do it. **Recommendation:** no change
    for the MVP (the two initial users are adults). For a child's phone, consider a waiting period or {helper}'s
    confirmation, as a later spec change.

