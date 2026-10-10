# Copy test: what people who had never seen it said

Task 5.1 says: "Test them on someone who has never seen them." This file records who read the copy, what they
said, and what changed.

## Summary

| Round | Readers | Status |
|---|---|---|
| 1 | Two **AI cold readers** (fresh Claude Sonnet agents with no project context), one per role | Done 2026-10-08. 25+ problems found; most fixed |
| 2 | Two **new** AI cold readers, same questions, revised copy | Done 2026-10-08. Fixes confirmed; a few more fixed |
| 3 | **Beaux**, both roles, interactive mockups of the packet. **This is the human copy test for this stage** (board directive, Telegram 4477) | Done 2026-10-08, relayed by Scout (Telegram 4473/4474). Wording fixes applied; product questions answered by the Lead on PLA-14 |
| Next | **Beaux using the real build** on the spare Motorola | When the MVP build is ready. Outside testers only later, and only if Beaux asks |

**Be clear about the limits of rounds 1 and 2.** AI readers are not people. They were told to role-play a
non-technical reader, answer only from the screens, and be blunt, and they had never seen the documents. But they
are the same model family as the author (Anthropic), so they may share blind spots. We tried cross-model readers
first: the local Codex CLI (OpenAI) returned `401 Unauthorized` and the Gemini CLI returned "Invalid auth method
selected", so neither ran, and no credentials were touched. Round 3 replaced
the planned test with someone new. Beaux decided (Telegram 4477): *"Get it in front of me before I feel
comfortable asking other people."* His pass counts as the human copy test for this stage. He knows the project
and is a confident phone user (5/5), so it can't show what a stranger would misread; the next check is Beaux on
the real build, and outside testers come later only at his call (tasks.md, task 5.1).

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

## Round 3: Beaux (the human copy test for this stage)

- **Who:** Beaux (board), age 30–44, phone comfort 5/5. He ran it himself on 2026-10-08, using an interactive
  version of [copy-test-packet.md](copy-test-packet.md) (same screens and questions, with tappable mockups).
  Answers relayed by Scout on [PLA-12](/PLA/issues/PLA-12) (Telegram 4473/4474).
- **Status:** counts as the human copy test for this stage (Beaux, Telegram 4477; Lead on PLA-12). It found
  real failures even from someone who knows the project: Screens 3, 7, C and H were misread.

### Phone user (Margaret)

| What he said | Change |
|---|---|
| Q1: "I was confused by 'Ask Sam to do the monthly phone check'. … 'Request App/Access' could be an alternative. Oh, it was under More." | The original revision added a subtitle. **Superseded by the approved [PLA-285](/PLA/issues/PLA-285) visual feedback:** centered plain-text More, without a subtitle, caret or border ([U1](flows-user.md#u1-home-screen)). |
| Q2: "I'm not sure why it's on the main screen if it's used so infrequently." | It shows only when the check is due; U1 and the packet now say so. The Lead may make the check event-driven (PLA-24). For {tp}, it is now one path, "Check for updates". The home line's wording is spec-fixed; see "Heard, not changed". |
| Q3: "This could get very restrictive… I would perhaps prefer the ability to turn web browsing on for 2-5 minutes." | Product question for the Lead ([README](README.md#open-questions-for-the-lead), question 9). |
| Q4: "It looks like it sends Sam a message. I don't know if he got it or not." | New list of requests under the box, with "Sam's phone got it ✓" when the network confirms delivery ([U5e](flows-user.md#u5e-your-requests-below-the-box-on-u5a)). It never claims Sam has read it. |
| Q5 (hotel Wi-Fi): "I don't think I'll be able to make calls from it." **The screen failed its main job.** | Title is now "Calls and texts still work", first; the spec sentence follows; the mobile-data definition is gone ([U7](flows-user.md#wi-fi-messages-exact-text-from-web-containment)). |
| Q6: "Can I change this person? Can I have multiple people in Sam's role?" | Product question for the Lead (question 8). |
| Q7: "it mentions a virus, so now I'm thinking it's a virus." "No clear description as to what's wrong." | "Virus" removed. The screen now gives a plain reason, such as "An app on this phone couldn't be tidied away" ([U3](flows-user.md#u3-plainphone-needs-attention-from-the-notice-line)). |
| Q8: "'Emergency 9-1-1' on the main screen makes me worried I'll bump it by accident." | A bump only opens the dialer; it never calls. The button now says so ("Opens the dialer"), with a wider gap before Phone. No extra confirm screen, which would slow a real emergency. **Superseded:** Beaux took the button off the home screen (Telegram 4487). 9-1-1 is now dialled from Phone, More → Emergency 9-1-1, or the lock screen ([U1](flows-user.md#u1-home-screen)). |
| Q8: "'This phone is set up without web pages': what if I need them?" | Same as Q3: product question 9. |
| Q9: bossy, babyish or watched? "No, I don't think so." | — |

### Trusted person (Sam)

| What he said | Change |
|---|---|
| Q1: "This feels right… The '1 request from Margaret' callout makes this relatively easy to follow." | — |
| Q2 (what to do while the Play Store is open): "I don't know." | The do and don't now sit right under "Stay with the phone until you tap Done", with the reason; the notification adds "Keep the phone with you" ([P1](flows-sessions.md#p1-before-a-session)). |
| Q4: "I'm not sure who is meant to be reviewing and how I can request a review." | "Not reviewed yet" gets a line: the Plainphone team checks apps; to ask for a check, tell Alex ([T4a, T4b](flows-trusted-person.md#t4a-add-app)). |
| Q5: "It seems like Margaret can't change permissions later, and if she does, she'll need to physically bring the phone to me." | That is how it works, so the screen now says it plainly, with the path ([T4c](flows-trusted-person.md#t4c-what-can-app-use)). |
| Q6: "I'm most worried about Google (due to tracking etc) and full settings access." | New "can't stop" item 11, in the Lead's words: "Plainphone itself sends nothing anywhere. But the phone still runs Google's services, which collect data…" ([L1](flows-limits-and-off.md#l1-what-plainphone-cant-stop)). Full settings is item 3 already. |
| Q7 (software changed): "It sounds like Margaret will have elevated permissions, maybe." | Rewritten: "Nothing has changed for Margaret"; the risk is now "a gap" ([L2b](flows-limits-and-off.md#l2b-the-page)). |
| Q8: "Can we not automate this? Or make it simpler for me to navigate to?" | One "Check for updates" path for {tp}: the "Needs you" item and the menu row open the same screen. Automating it is product question 10. |
| Q9: "What if her phone goes flat? What if a kid has this phone and uses this as a bypass method?" | Turn-off screen now says "A flat battery doesn't turn it off: when the phone starts again, Plainphone is back" (Lead, from PLA-5 A6) ([L4a](flows-limits-and-off.md#l4a-what-happens)). The bypass worry is product question 11; any waiting period waits for Beaux's answer. |
| Q10: "'Apps on this phone' feels a little invasive, but if we have the right type of relationship, it wouldn't be an issue." | Noted. The page only lists apps {tp} added, with no usage data. No change. |
| Q11 (rewordings): "I don't know." | — |

### Heard, not changed (round 3)

| Feedback | Why not changed |
|---|---|
| The monthly line's wording confused him (Q1, Q2), as it did the AI readers in rounds 1 and 2 | Exact text required by approved-apps and calm-home-screen. **For the Lead, again:** "Monthly phone check due – Sam will help" tested better in every round. |
| "Not reviewed yet – this app may open web pages inside it" | Exact text from web-containment. Explained by the new line under it. |

## What's next

- **Beaux on the real build**, on the spare Motorola, when the MVP is ready. Record what he says here, in the
  same format.
- **Outside testers:** only if Beaux asks for them, once he's comfortable with the product. The packet
  ([copy-test-packet.md](copy-test-packet.md)) is kept up to date for that.
