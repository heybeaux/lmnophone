# LMNOPhone delivery roadmap

**Canonical copy.** This file is the one source of truth for what the team is building next. The Paperclip roadmap issue (PLA-507) shows a short view that links back here.

- **Revision:** r4, 2026-10-13 20:30 UTC (13:30 Pacific). See the [change log](#change-log) for the source commit of each revision.
- **Owner:** Plainphone Lead. Updated after every install, scope or priority change, missed commitment and new blocker.
- **Times** are Pacific (America/Vancouver) unless marked UTC.
- **Device:** all test builds go to the one approved spare test phone, in place, by its one driver (the Android Platform Engineer). Build numbers are assigned by that driver when a build is cut. Builds 46 and 47 are expected, not reserved.

## Words used here

| Word | Meaning |
|---|---|
| **Built** | The APK exists and its checks passed. Nothing more. |
| **Installed** | The driver installed it in place on the spare and read back the version, Device Owner, lock task and 911. |
| **Exercised** | The driver ran the new behaviour on the spare and recorded the result. |
| **Tested by Beaux** | Beaux tried it himself and said so. Only he can move a build to this state. |
| **Committed** | The team promises this scope by this time. Missing it is recorded below, never quietly moved. |
| **Planned** | The intended order and a best-guess target. It may change, and each change is logged. |
| **Blocked** | It can't start until the named item happens. |

## Now / Next / Later

- **Now (on the spare):** build 45, installed 2026-10-13 19:39 UTC and exercised: the family list with a Home "Family" row and "Messages with …". It also has everything from build 44: Change PIN, calm Home, optional call/message counts, Phone settings (Wi-Fi join and forget, Bluetooth view and search, brightness, sound, ringtone, text size, About), PIN setup and entry, start and cancel a turn-off wait, and voice-message **practice** (recorded locally, never sent).
- **Next (committed):** build 46, real voice-message Send (PLA-513, being built now); then build 47, Apps on this phone (PLA-514, being written in parallel).
- **Later (planned, in order):** adding a hidden app, "Ask for an app", the recovery code, the notice line, changing what an app can use, a supervised Play Store session, the full give-back on turn-off, and the final app ID.

## Next ten builds

Slots 1 and 2 are commitments. Slots 3 to 10 are a deliberate order with honest targets; they are not promises yet. A slot becomes a commitment when it is next in line and its prerequisites are met.

| Slot | Build | What Beaux gets | Tasks | Owner (author) | Reviewer | Depends on | How Beaux can test it | Target (Pacific) and confidence | State |
|---|---|---|---|---|---|---|---|---|---|
| 1 | **46** (expected) | **Real voice-message sending.** On a family person's page: record → Review → **Send**. The phone sends it as a normal picture message (MMS) and says **Sending**, **Sent**, **Not sent** (with the reason) or **Not confirmed**. It never claims "delivered". The practice label goes away for people who can be sent to. | PLA-513 (build); PLA-499 + PLA-508 (engine, reviewed) | Android Platform Engineer (integration glue and driver) | Verifier (opposite model), the integration range only; the engine is already approved | Build 45 installed (done); engine approved (done). Real sending needs a working SIM in the spare (H1). Without one, the build is still useful: Send must say **Not sent** truthfully, and the driver checks that with airplane mode on so nothing can leave. | With a working SIM: record a short message to a family entry with **your own number**, tap Send, see "Sent", and hear it on your phone. Without a SIM: tap Send and see "Not sent" with a plain reason. | **Wed 14 Oct, 09:00–13:00.** Medium-high: started 13:15 Pacific Tue, the driver is free, and the review is of glue only. | **Committed** (in progress) |
| 2 | **47** (expected) | **Apps on this phone (T5).** The trusted person sees the apps in Home order (Phone and Messages marked "Always on"), moves apps up or down, opens an app's page (name, version), and removes an app from Home with a fresh PIN (nothing is deleted). First real control of the app list from the phone itself; today the list is fixed at setup. | PLA-514 | UI Engineer (one author: screens and the starter-list wiring) | Product & UX Designer (Claude) | None outside the team. Written in parallel with slot 1, off the driver's desk until install. | Trusted-person menu → Apps on this phone. Move an app down and check Home follows, also after a restart. Remove Calculator with your PIN, check it leaves Home. Adding it back is slot 3 (until then the driver restores it). | **Wed 14 Oct, 17:00–21:00.** Medium-low: no code yet; started Tue 13:20 Pacific. | **Committed** |
| 3 | driver assigns | **Add an app that is already on the phone ("Add a hidden app", T4, T7).** A hidden, already-installed app can be added: "Add {app}?", then "What can {app} use?", then Confirm. It appears on Home. | No task yet (opens when slot 2 is in review). | UI Engineer + Policy Engineer | Claude reviewer | Slot 2 | Trusted-person menu → Add an app → pick a hidden app (for example Calculator) → choose what it can use → Confirm. It appears on Home and opens. | **Thu 15 Oct.** Low. | Planned |
| 4 | driver assigns | **Ask for an app (U5) and Requests (T3).** The person types a short request on More › Ask for an app; it goes by text to the trusted person. The trusted person sees it under Requests. | No task yet. | UI Engineer + Link & Browser Engineer (SMS sending is the slot-1 engine's sibling) | Claude reviewer | Slot 1 (send permission and SMS path); slot 3 to act on a request | More › Ask for an app → type "Calculator please" → Send. Your phone gets the text. Then on the trusted-person menu, Requests shows it. | **Thu 15 – Fri 16 Oct.** Low. | Planned |
| 5 | driver assigns | **Recovery code (T1c, T1d).** "Use recovery code" on the PIN screen stops saying "Not in this test build yet". A trusted person who forgets the PIN can set a new one with the recovery code. | No task yet. Builds on PLA-494's PIN work. | Android Platform Engineer | Verifier | Build 44 (done) | On the PIN screen → Use recovery code → enter the code you were given at setup → choose a new PIN. | **Fri 16 Oct.** Low. | Planned |
| 6 | driver assigns | **The notice line and "Needs you" (U1, U3, T2).** Home's notice line and the trusted person's "Needs you" items open real screens instead of "Not in this test build yet". | No task yet. | UI Engineer | Claude reviewer | Slots 2–4 give it something to show | Start a waiting change; check the Home notice line opens the waiting change, and the trusted-person front page lists it under "Needs you". | **Fri 16 – Sat 17 Oct.** Low. | Planned |
| 7 | driver assigns | **Change what an app can use (T5 app page → T4c).** The app page from slot 2 gains "What {app} can use" and **Change what {app} can use** with a fresh PIN. | No task yet. Left out of slot 2 on purpose to keep it small. | UI Engineer + Policy Engineer | Claude reviewer | Slots 2 and 3 (the T4c screens) | Apps on this phone → Camera → Change what Camera can use → turn the microphone off → PIN. Camera's microphone permission reads back off. | **Sat 17 – Mon 19 Oct.** Low. | Planned |
| 8 | driver assigns | **Supervised Play Store session.** The trusted person opens a short, supervised Play Store session to install a new app, then adds it (slot 3 screens). | No task yet. Spec already approved. | Android Platform Engineer | Verifier | Slot 3; Beaux signs in to his Google account on the spare himself (human action H2). | Trusted-person menu → Add an app → Get a new app → install a free app in Play → it comes back to "Add {app}?". | **Mon 19 Oct.** Low: needs H2 first. | Blocked on H2 (when its turn comes) |
| 9 | driver assigns | **Turning LMNOPhone off gives the phone back in full (MVP item 9).** After the turn-off wait, every hidden app, permission and setting comes back and the phone is an ordinary Android phone again. | No task yet. | Android Platform Engineer | Verifier | Slots 1–8 settled. Proving it on the spare removes LMNOPhone, so it is done once, right before the slot-10 set-up (decision D2). | Turn LMNOPhone off with your PIN, wait it out, then check every app is back in the normal launcher and Settings works as usual. | **Tue 20 Oct or later.** Low. | Planned |
| 10 | driver assigns (new app ID) | **Final app ID (`dev.heybeaux.lmnophone`).** Needed before LMNOPhone goes on any phone other than the spare. It can't install over the old ID, so the spare is set up again once. | PLA-204 | Android Platform Engineer | Verifier | Slot 9 (the spare is already given back in full); the reset is planned with Beaux (decision D2) | After the set-up, Home and every earlier test still work, and Settings › Apps shows the new name. | **Tue 20 Oct or later.** Low. | Planned |

### Not in the ten, on purpose

- **Bluetooth pairing inside LMNOPhone:** deferred. Android refuses the pairing chooser and the pairing dialog inside lock task (PLA-494, PLA-495, PLA-496). The only ways left need a board decision (D1).
- **PIN Repair for waiting records** (PLA-401): parked; the preview design can't recover those records.
- **Mode switch screens** (PLA-446), **update gates** (PLA-381), **battery and data savings** (PLA-123, PLA-125), **timed windows** (PLA-32, PLA-33), **iOS research** (PLA-124): after the usable MVP.
- **Publication cleanup** (PLA-152, PLA-309): runs in parallel and never holds a build.

## Usable MVP

The MVP is usable when one person could use the spare as their everyday phone with a trusted person looking after it, and every item below is **tested by Beaux** on the spare:

1. Calls, texts and the camera work from a calm Home; only approved apps show. **(Installed.)**
2. 911 is always reachable. **(More › Emergency is checked to be exactly 911, with zero calls, on every install.)**
3. Phone settings the person needs: Wi-Fi, Bluetooth view and search, brightness, sound, ringtone, text size, About. **(Installed.)**
4. Voice messages to family for someone who can't read or write: record, send, a truthful result, replies in Messages. **(Practice and family list installed; sending slot 1.)**
5. The trusted person's PIN: set, change, recover. **(Set and change installed; recover slot 5.)**
6. The trusted person controls the apps: see, hide, add, and install new ones in a supervised Play session. **(Slots 2, 3, 7, 8.)**
7. The person can ask for an app, and the trusted person sees the request. **(Slot 4.)**
8. Waiting changes with Cancel, shown on Home. **(Start and cancel installed; notice line slot 6.)**
9. Turning LMNOPhone off gives the phone back in full. **(The wait is installed; the full restore is not yet proven on the spare; slot 9.)**
10. Restarts and in-place updates keep everything, and the app ID is final. **(In-place updates proven on every build; app ID slot 10.)**

## Decisions and actions for Beaux

Only things the team can't decide or do itself. None of them blocks slots 1 and 2.

| # | What | Why it matters | Needed by | Team's suggestion |
|---|---|---|---|---|
| H1 | **Put a working SIM in the spare** (action). The spare's SIM was out of service on 2026-10-11. | Real voice messages and "Ask for an app" texts can't leave the phone without one. | Slot 1 (the build still ships without it and shows "Not sent"). | Any SIM with texting and MMS. Test sends go only to your own phone. |
| H2 | **Sign in to your Google account on the spare** (action, you do it yourself). | The supervised Play session needs it. Agents never handle the password. | Slot 8. | Do it when slot 9 is next; we'll ask once, then. |
| D1 | **Bluetooth pairing:** let the system pairing dialog show inside LMNOPhone, or keep pairing out. | Allowing it opens a small Settings window inside the locked phone. | Not needed for the MVP. | Keep it out for now; revisit after the MVP. |
| D2 | **When to set the spare up again** for the final app ID. | The new ID can't install over the old one; the spare's LMNOPhone settings start fresh. | Slots 9 and 10. | Once, right after slot 8 is tested: prove the full give-back (slot 9), then set up with the new ID (slot 10). |

## Delivered history

Install times are UTC, from each build's `release_notes` case (PLA-C numbers). "Tested by Beaux" is only filled in when he has said so.

| Build | What it added | Installed (UTC) | Evidence | Exercised by driver | Tested by Beaux |
|---|---|---|---|---|---|
| 45 | Family list (trusted person adds and removes people), Home "Family" row, each person's page with practice recording and "Messages with {name}" | 2026-10-13 19:39 | PLA-C123; PLA-505 | Yes (fictional Sam; Back from Messages takes two presses, and Messages keeps an empty thread: both known limits) | Not recorded |
| 44 | Change PIN on the trusted-person menu; a waiting change started with the old PIN is cancelled | 2026-10-13 18:51 | PLA-C122; PLA-494 | Yes (spare: change, old refused, change back; the waiting-change cancel on the emulator) | Not recorded |
| 43 | Wi-Fi Forget asks first; Bluetooth Pair page searches again on return | 2026-10-13 14:42 | PLA-C120 | Yes (Forget confirm itself only on emulator) | Not recorded |
| 42 | Voice practice people list stays silent after a text-size change | 2026-10-13 09:57 | PLA-C119 | Yes | Not recorded |
| 41 | Voice practice keeps its place after a text-size change | 2026-10-13 08:36 | PLA-C118 | Yes | Not recorded |
| 40 | Voice practice 1b: review layout, the picture says the name | 2026-10-13 06:23 | PLA-C117 | Yes | Not recorded |
| 39 | Voice messages practice (local, never sent) | 2026-10-13 03:59 | PLA-C116 | Yes | Not recorded |
| 38 | Voice practice (row missing; replaced by 39) | 2026-10-13 | PLA-C115 | Partly | Not recorded |
| 33–37 | Wi-Fi joining and forgetting with Location on only briefly; system-update postpone; even row spacing | 2026-10-12 | PLA-C106, C109, C110, C111, C114 | Yes | Not recorded |
| 27–32 | Text size, Wi-Fi status, Bluetooth view and search, ringtone chooser | 2026-10-12 | PLA-C93 to C104 | Yes | Not recorded |
| 23–26 | Waiting changes in Phone settings, start and cancel a turn-off wait, LMNOPhone name, containment fixes | 2026-10-12 | PLA-C83, C86, C91; PLA-411 | Yes | Not recorded |
| 17–22 | PIN setup and entry, About this phone, brightness and battery, sound | 2026-10-12 | PLA-C68 to C81 (notes still in review) | Yes | Not recorded |
| 2–16 | Device Owner and lock task, hiding apps, calm Home, optional counts, boot and package checks | 2026-10-10 to 10-12 | PLA-C2 to C65 | Yes | Not recorded |

## How this roadmap is kept

- **After every install:** move the slot to Delivered history with its install time and evidence, then pull the next slot up. If the next slot's prerequisites are met, it becomes a commitment, so there are always two.
- **When a commitment slips or changes:** add a change-log line with the old scope and time, the new scope and time, the reason and the owner. Never edit the old promise away.
- **Hourly delivery check:** the Lead compares this file with the build on the spare and the open tasks, and fixes any drift. No meeting, no extra review.
- **Small fixes** ride along with the next planned build when they are safe. No build is cut just to change the number.
- **Nothing moves to "Tested by Beaux"** unless Beaux says so.

## Change log

| When (UTC) | Revision | Change | Old | New | Reason | Owner |
|---|---|---|---|---|---|---|
| 2026-10-13 18:50 | r1 | First roadmap. | — | Slots 1–10 as above. | Beaux asked for a committed rolling roadmap (PLA-507). The proposed builds 44–53 from the operator were not adopted as commitments; the family list ships before real sending because it is ready and sending still needs its review and a SIM. | Lead |
| 2026-10-13 18:50 | r1 | The README's old dated table is replaced by this file. Its unmet targets are recorded here, not dropped. | README (written 2026-10-09/10): preview-3 "trusted person" by **Tue 13 Oct** (PIN and recovery code, Ask for an app by text, adding an app and choosing what it can use, supervised Play session); preview-4 "links and waits" by Thu 15 Oct (blocked links and browser rules, two trusted people, 24-hour waits with Cancel, notice line, self-managed mode); test-1 "full custody contract" by Sat 17 Oct. | Of preview-3, only PIN setup and entry shipped (builds 17+); Change PIN is slot 1; recovery code slot 7; Ask for an app slot 6; adding an app slots 4–5; Play session slot 9. Of preview-4, waits with Cancel shipped (builds 23–24); notice line is slot 8; blocked links, two trusted people and self-managed mode are after the MVP list. Test-1's full restore is MVP item 9, not yet in a slot. | **Missed.** The team moved to many small device builds (Beaux, 2026-10-11) and spent 12–13 Oct on Phone settings and voice messages, which Beaux asked for. Those dates were never re-promised. | Lead |
| 2026-10-13 19:15 | r2 | Build 44 installed on the spare. | Slot 1 committed for Tue 13 Oct 14:00–16:00 Pacific. | Installed 18:51 UTC (11:51 Pacific), ahead of the window; exercise, safety diff and notes still running. Slot 2 is now unblocked (its prerequisite was slot 1 installed). | Install recorded (PLA-494). | Lead |
| 2026-10-13 19:25 | r3 | Slot 3 reviewer changed (hourly check, PLA-510). | Review PLA-508 by the Android Platform Engineer, queued behind build 44's exercise and build 45. | Review PLA-508 by the Product & UX Designer (Claude, idle), starting now in parallel. Target window unchanged. | The sole driver would have started the review only after build 45; moving it keeps slot 3 ready when slot 2 installs. Still one opposite-model review. | Lead |
| 2026-10-13 20:30 | r4 | Build 45 installed and exercised (hourly check, PLA-512); slots pulled up. | Slot 2 (family list) committed for Tue 13 Oct 17:00–20:00 Pacific. Slot 3 (sending) and slot 4 (Apps on this phone) planned; slot 4's task was to open when slot 3 reached review. Slots 5–10: add a hidden app, Ask for an app, recovery code, notice line, Play session, app ID. | Build 45 installed 19:39 UTC (12:39 Pacific), before its window; build 44's exercise also finished. Sending is now slot 1, committed for Wed 14 Oct 09:00–13:00 (PLA-513, started). Apps on this phone is slot 2, committed for Wed 14 Oct 17:00–21:00 (PLA-514), opened now instead of at slot-1 review. Two items added to keep ten: slot 7 "Change what an app can use" (split out of slot 2 to keep it small) and slot 9 the full give-back on turn-off (MVP item 9, promised in r1). The rest keep their order. | Both slot-1 prerequisites were met (build 45 installed, engine review approved). The UI Engineer was idle and slot 2 doesn't need the driver until install, so waiting would only delay it. | Lead |
