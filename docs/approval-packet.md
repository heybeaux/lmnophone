# Plainphone: what we're asking Beaux to approve

Revision 3, 2026-10-08. Prepared by the Plainphone Lead (Claude). Cross-model review by the Verifier & Escape Artist (OpenAI model) across PLA-3, PLA-4, PLA-7, PLA-8, PLA-9 and PLA-10. Final verdict: **approve with changes**; the changes are applied here, and none needed re-review.

## The short version

We want to build Plainphone as **one Android app** on a normal, Google-certified phone. The app is two things at once:
- the phone's **Device Owner**, Android's built-in "this phone is managed" mode. Wisephone uses the same kind of control on Samsung.
- the phone's **home screen**.

A trusted person decides which apps the phone can open.

**What we design for:**
- **Apps.** Apps that aren't approved can't be opened. That includes apps installed without approval.
- **Emergencies and calls.** Plainphone is designed never to get in the way of 9-1-1, calls, texts or emergency alerts.

These are requirements we test against. They are **not yet proven** on the phone you'll use. That needs your chosen model and build, and your carrier.

**What we are not building:**
- our own version of Android;
- a simple launcher, because one is too easy to get around.

**What approval allows:**
1. Finish the **spike**: throwaway tests on emulators and the spare Motorola, answering the questions in `docs/feasibility-questions.md`.
2. If the go/no-go checks pass, **build the MVP** described in `openspec/changes/add-plainphone-spike-and-mvp/`, first for your phone and then the trusted person's.

**What approval does not allow.** Each of these needs its own separate OK:
- touching any phone other than the spare;
- a wipe from the recovery menu on any phone (and never on the spare);
- adding a screen-lock PIN on the spare for testing;
- handing a phone over for daily use;
- publishing anything;
- spending money;
- placing a live 9-1-1 call;
- any exception to the reset-protection or alert checks.

## What we know so far (spare Motorola, Android 15, test-only build)

- **Setup by cable works.** Our test app took over as Device Owner using a USB cable. Google's new block on unapproved management apps did not stop that method on this phone. The QR-code method was not tried, and each new phone model has to be checked again.
- **It can be removed without a wipe.** This was the throwaway test version. Whether the real "turn Plainphone off" restores everything is still to be tested.
- **Fresh phones install apps by themselves.** Within minutes of joining Wi-Fi, a fresh phone installed 7 apps on its own (4 games and 3 Google apps), with Facebook preloaded. So Plainphone does its clean-up before the phone connects to anything.
- **One Android setting is essential.** It stops other screens opening inside an approved app.
  - Without it, an approved app could open the App info screen (and from there, permission settings), the share menu, and Google Photos.
  - With it on, App info and the share menu were blocked. The Photos route hasn't been re-tested with it on yet.
  - It also blocks permission pop-ups. So the plan is for the trusted person to choose each app's permissions when approving it. That plan **isn't tested yet**.
- **Not tested yet:**
  - restarting the phone in locked mode;
  - the power menu;
  - emergency calling from the lock screen;
  - real calls and texts (there is no SIM in the spare);
  - attaching a photo in Messages.

## Decisions we need from you

1. **Phone model.** The spare Motorola stops getting security updates in March 2027, so it's for testing only.
   - **Candidates:** a Pixel 9a or newer (7 years of updates, fewest manufacturer extras), or a Samsung A-series (more extras to lock down).
   - Either one only counts as supported after its exact model and software build pass our checks.
   - Buy new phones, or reuse existing ones?
2. **Trusted person and backup person, for each phone.**
   - If you and the trusted person are each other's trusted person, we recommend a third person as backup.
   - Each needs a Google account, with a second sign-in factor (like a phone code), that they can use from a device **other than** either Plainphone phone.
   - Each Plainphone phone also has a recovery code. The trusted person must keep it somewhere other than the Plainphone phones.
3. **License.** We recommend **Apache-2.0**. It lets us reuse Google's and Headwind's open-source code.
4. **Must-have apps on each phone.** For example: your bank, maps, ride-share, Signal or WhatsApp. We test these first. If one of them breaks, the plan changes.
5. **Carrier and plan for each phone.** It needs a data plan, so that group texts with iPhones and picture messages work away from Wi-Fi.
6. **Who does the helper work.** Someone needs to:
   - set up the phones;
   - deliver Plainphone updates from a laptop on your home Wi-Fi;
   - help if a phone needs recovery.
7. **Who keeps the signing key** for Plainphone updates, and who keeps its offline backup.
   - If the key is lost, Plainphone can't be updated without setting each phone up again.
   - If the key is stolen, Plainphone has to be turned off and set up again on every phone.

## Ongoing work this creates

- **Trusted person, monthly.** Do a short update check in the Play Store, with a PIN. If it's more than a week overdue, the phone's home screen shows a reminder.
  - They also follow up on any app that failed to update.
  - When an app changes a lot, they re-read its "web leak" note.
- **Helper and our team, after every Android update.** Each Android update produces a new phone build. For each one:
  - we prepare and review that build's list of protected system apps;
  - we send it as a signed Plainphone update;
  - the helper installs it from a laptop on your Wi-Fi;
  - before normal support resumes, we run the handover self-check and re-run the emergency and alert checks on that build.

  Android lets us **postpone** an update by up to 30 days, but some security updates may install anyway. So not every update can wait for our review. This lasts as long as the phones are in use.
- **Helper, occasionally.** Install Plainphone's own updates, and help with recovery.

## Limits we need you to accept (or reject)

These are things Plainphone **can't** fully stop or promise. We'd rather say so now.

- **Web pages inside apps and some system screens.** Some screens can still show web pages:
  - approved apps with their own built-in pages (help pages, articles, sign-in screens);
  - Google account and help screens, keyboard features (like GIF search) and file pickers, wherever testing finds them reachable.

  Each app gets a plain "web leak" note so the trusted person can decide.
- **Wi-Fi that needs a sign-in page** (hotels, hospitals, ferries, some workplaces) won't work. Mobile data and normal Wi-Fi are fine. The emergency button doesn't depend on Wi-Fi. But no phone can call without either a cell signal or working Wi-Fi. If either of you relies on sign-in Wi-Fi, or on Wi-Fi calling where there's no cell signal, tell us.
- **The custody limit.** The trusted person can unlock the Play Store or Settings for a few minutes, and must stay with the phone while it's unlocked. During that time, Play Store and Settings help and account pages can show web content. If Plainphone stops working while unlocked, the unlock stays open until a helper repairs the phone. That could be from a crash, a bad update, or a storage fault. The phone is never handed back until everything checks out. If you don't accept this, we have to find a different mechanism first, and that becomes a go/no-go question.
- **Background activity.** An app installed without approval can't be **opened**. But if hiding it fails, it may run in the background **without a guaranteed time limit**.
- **After an Android update, before our review.** Plainphone keeps the phone in locked mode and is designed to preserve emergency access; that build still needs the checks above. It doesn't hide newly appeared system apps until we've reviewed them. Two gaps remain until the review is done:
  - an update could add a new route inside an app that is already allowed;
  - an update could add an app that Android groups with an allowed one (they share an identity).

  We accept that temporary risk only with your agreement.
- **A hardware-button wipe.** Anyone holding the phone can wipe it from the recovery menu.
  - Reset protection should then require the trusted person's (or backup person's) Google account. That isn't proven until we test it on your phone model, which needs its own OK.
  - Plainphone has to be set up again afterwards, and anything not backed up is lost.
- **A broken Plainphone update.** There is no promised rollback. A signed update that fails badly may need the recovery-menu wipe above.
- **Emergency alerts (Alert Ready).** Before handing a phone over, we check that an alert actually appears on that phone's exact software build. If we can't see one, either:
  - the phone stays with us; or
  - you separately accept a narrower promise for **that build only**.

  That separate exception records what we could not verify, including whether we proved that our test catches a disabled alert display. It applies only to alerts on that exact build. An exception for one build never carries over to the next.

## What happens after you approve

1. The spike continues: lock task over a full day, Play updates in the background, the escape list, and Motorola's extras. All of it stays within the spare-phone rules: no recovery wipes there, and no screen-lock PIN without your direction.
2. If anything fails a go/no-go check, we come back with a revised plan before writing MVP code.
3. When you pick a phone model, we ask separately before touching your phone.
4. Your phone first, after all the handover checks. Then two weeks of use. Then the trusted person's. Each step needs a separate OK from you.
