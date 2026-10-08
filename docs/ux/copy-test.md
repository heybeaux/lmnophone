# Copy test: what people who had never seen it said

Task 5.1 says: "Test them on someone who has never seen them." This file records who read the copy, what they
said, and what changed.

## Summary

| Round | Readers | Status |
|---|---|---|
| 1 | Two **AI cold readers** (fresh Claude Sonnet agents with no project context), one per role | Done 2026-10-08. 25+ problems found; most fixed |
| 2 | Two **new** AI cold readers, same questions, revised copy | Done 2026-10-08. Fixes confirmed; a few more fixed |
| 3 | **A real person** who has never seen Plainphone, arranged by Scout | **Not done yet.** Requested on [PLA-12](/PLA/issues/PLA-12); packet ready in [copy-test-packet.md](copy-test-packet.md) |

**Be clear about the limits of rounds 1 and 2.** AI readers are not people. They were told to role-play a
non-technical reader, answer only from the screens, and be blunt, and they had never seen the documents. But they
are the same model family as the author (Anthropic), so they may share blind spots. We tried cross-model readers
first: the local Codex CLI (OpenAI) returned `401 Unauthorized` and the Gemini CLI returned "Invalid auth method
selected", so neither ran, and no credentials were touched. **The human test in round 3 is still needed before
the copy can be called tested.**

## Method

- The readers saw only the words on the screens, not the design notes, with neutral names: Margaret uses the
  phone, Sam looks after it, Alex is the helper.
- **Phone-user reader:** "you are 68, you've had a basic flip phone and an old Android phone, you've never
  changed a phone setting." 8 screens: home, the monthly-check line, "needs attention", More, Ask for an app,
  blocked link, the Wi-Fi message, About this phone. 9 questions.
- **Trusted-person reader:** "you are 55, you use a smartphone daily for texts, photos and banking, but you've
  never set up parental controls." Margaret is their spouse. 11 screens: the trusted-person front page, a
  request, opening the Play Store, adding an app (unreviewed) with permissions, the closing check, "What
  Plainphone can't stop", "Phone software changed – review pending", the monthly check, turning off, the PIN
  wait. 11 questions.
- Questions and screens are in [copy-test-packet.md](copy-test-packet.md) (the round-2 version, with the
  round-3 fixes applied).

## Round 1: what they said, and what changed

### Phone user

| What the reader said (quoted or close paraphrase) | Change |
|---|---|
| Blocked link: "it doesn't say whether the link was dangerous or only not allowed." "Links only open when they belong to one of your apps": "I don't follow this." | Body now: "This phone is set up without web pages, to keep it simple. That doesn't mean the link is bad." The apps sentence now gives an example (Spotify). |
| "Send to Sam" "feels like I'm reporting a friend's text"; the forwarded text ("tried to open a link that isn't allowed") "sounds like I'm being reported for breaking a rule". | Link renamed "Look at it with Sam". The text to Sam is now "Margaret would like to look at this link with you: …". |
| "Needs attention" and "found something": "too vague, and it sounds like a virus." | "Plainphone, the program that keeps this phone simple, needs Sam to check one of its settings. It isn't a virus, and you haven't done anything wrong." |
| "Monthly phone check… with the phone in Sam's hands": "reads like the phone is being taken away." | "Sam needs your phone for about 10 minutes to update your apps, so they keep working well." |
| "Use mobile data": "I'd have no idea how to do that." "Forget this network": doesn't know what it does. | Added plain lines under the (spec-fixed) message: what "Forget this network" does, what mobile data is, and "you can still call when the phone has signal (the bars at the top)". |
| "Someone enters the wrong PIN many times: who? It sounds like a break-in." | "…if Sam's PIN is typed wrongly many times (so Sam knows if someone is guessing it)." |
| "Needs Sam's PIN" under "For Sam": "the phone has a section I'm shut out of." | Row now reads "Where Sam adds apps, with a PIN". About this phone explains the PIN protects the user's phone. |
| "Plainphone: I don't know what it is." | About this phone and "needs attention" both say "Plainphone is the program that keeps this phone simple." |
| Ask for an app: should be told "more plainly up front" that Sam adds it. | Body now starts "Sam adds apps to this phone, together with you." |

### Trusted person

| What the reader said | Change |
|---|---|
| After opening the Play Store, the screens "never tell me how to search for or install the app, and they never say I must press Home." | The Play Store screen now says: search, tap Install, wait for Open, then go back to the home screen (swipe up). "Press Home" removed: many phones have no Home button. |
| "The screen says 'tap Done,' but the notification says 'Tap here when you're finished,' so I don't know which one counts." | Notification now "Tap here to finish"; the start screen says "Plainphone checks the phone, then shows Done." |
| "Not ready to hand back": "I'm not told whether to tap 'Check again' first." "Helper" and "Alex" "read like two people." | "Tap Check again once; if this screen comes back, call Alex, your helper." |
| "Not reviewed yet… never says what harm that does." | The extra confirmation now says it "would let Margaret reach websites this phone normally keeps out. If you're not sure, ask Alex first." |
| Permissions: "Nothing says what breaks if I say no", "'Where the phone is' is a strange label", and "the screens never suggest involving Margaret". | "Choose together with Margaret… If you choose 'Don't allow', the parts of Transit that need it won't work. You can change this later." Label now "Location (where the phone is)". |
| Software changed: "'be a little more careful about handing it over' is useless because it doesn't say how." | Now: "Margaret can keep using the phone as normal… There's nothing you need to do. Alex has been sent a text…", then the residual risk in one sentence. |
| Monthly check: "I don't know where to 'note' a failed app… or whether the 10-minute window runs out mid-update." | "Afterwards, Plainphone asks which apps didn't update… Updates carry on even after the Play Store closes." |
| Limits list: "factory reset", "background", "backup person" unclear; "sign-in page" unclear; the alert line "pointless and confusing". | Reworded: "(a factory reset)", "run unseen", "the backup person you chose at setup", "asks you to sign in or accept terms on a page first", "We checked that alerts appear on this phone ({date})". |
| Menu: "Order" unclear; "Open full phone settings" "never says why I'd want it"; Plainphone update "how, and when?" | "Change the order…"; "for things Phone settings doesn't have, like the screen lock"; "Only with Alex, when Alex asks". |

## Round 2: did the fixes work?

New readers, same questions, revised copy.

**Confirmed fixed.** The trusted-person reader walked through adding an app correctly: search, install, wait for
"Open", swipe up, add it, check, Done. They understood when to hand the phone back, that the phone is fine to use
after a software change ("Margaret can keep using the phone as normal, and I don't need to do anything"), and
that turning off keeps photos. The phone-user reader no longer felt reported by the link screen. The forwarded
text was "plain and says what happens". They understood "needs attention" calmly ("it isn't a virus and calls
still work, which calms me down"), and could still call at a hotel.

**Fixed after round 2:**
- "Sam borrows your phone is odd, because it's my phone" → "Sam needs your phone for about 10 minutes".
- "'When you're together' is unclear; I might wait weeks" → "Next time you're together, Sam can add it on this
  phone."
- Both trusted-person readers felt it looked like surveillance from Sam's side ("Nothing says that I can't see her
  messages, photos or browsing"). Added a line under the front page title: "This is for adding apps and keeping
  the phone working. You can't see Margaret's messages, photos, or what Margaret does in apps."
- "Recovery code… without any explanation of where to find it" → "(the long code you wrote down when Plainphone
  was set up)".
- "'The round picture' is vague" → "your Google picture or initial".

**Heard, not changed, and why:**

| Feedback | Why not changed |
|---|---|
| "Ask Sam to do the monthly phone check" "sounds like a chore I'm supposed to do" (both rounds) | The exact text is required by approved-apps and calm-home-screen. **For the Lead:** consider "Monthly phone check due – Sam will help". |
| "Forget this network" "sounds scary and permanent" | Exact label required by web-containment. Explained by the line beneath it. |
| "Not ready to hand back" wording is scary | Exact text required by trusted-person-controls. Body says calls and Emergency still work. |
| 5 requests a day "feels like being rationed" | Limit is in trusted-person-controls. The message is as neutral as we can make it. |
| "Plainphone", "web pages", "link", "app", "update" are unclear to the 68-year-old persona | These are the product name and everyday words most smartphone users know. Round 3 (a real person) will show whether this persona was realistic. |
| The limits list is long and doesn't say which items matter most | The spec requires every item. The trusted person reads it once at setup, with the helper there to explain. |
| Holding the phone every time an app is added "could make her feel supervised like a child" | The approved design requires custody. The copy now says "choose together" and states what the trusted person can't see. |

## Round 3: a real person (pending)

- **Who:** to be arranged by Scout. Someone who has never seen Plainphone and is not technical. Ideally one person
  per role.
- **What:** [copy-test-packet.md](copy-test-packet.md), about 30 minutes, with no explanation given first.
- **Record here:** first name or initials (or "anonymous"), age range, phone comfort (1–5), date, who ran it,
  their answers, and the changes made.
