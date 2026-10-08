# Plainphone: what we're asking Beaux to approve

Draft, 2026-10-08. Prepared by the Plainphone Lead. It goes to Beaux only after the Verifier's cross-model review passes.

## The short version

We want to build Plainphone as **one Android app** on a normal, Google-certified phone. The app is two things at once:
- the phone's **Device Owner**: Android's built-in "this phone is managed" mode, the same mechanism Wisephone uses on Samsung;
- the phone's **home screen**.

A trusted person decides which apps the phone can open. Everything else is hidden and blocked. Calls, texts and 9-1-1 always work.

We are **not** building our own version of Android. And we are **not** shipping a simple launcher, because a launcher alone is too easy to get around.

**What approval allows:**
1. Finish the **spike**: throwaway tests on emulators and the spare Motorola only, answering the questions in `docs/feasibility-questions.md`.
2. If the go/no-go checks pass, **build the MVP** for your phone and then the trusted person's, as specified in `openspec/changes/add-plainphone-spike-and-mvp/`.

**What approval does not allow:**
- touching any phone other than the spare without your separate OK;
- publishing anything;
- spending money;
- placing a live 9-1-1 call.

## What we know already (from the spare Motorola)

- Our app can take over as Device Owner on a certified Android 15 phone using a cable. Google's new block on unapproved management apps did not stop the cable method.
- It can be removed again without wiping the phone.
- A fresh phone installs games and Google apps by itself within minutes of joining Wi-Fi. So Plainphone does its clean-up before the phone connects to anything.
- Android's "block other screens" setting is essential. Without it, approved apps could open settings, photos and the share menu. With it, those are blocked. But permission pop-ups are blocked too, so the trusted person decides each app's permissions when approving it.

## Decisions we need from you

1. **Phone model.** The spare Motorola stops getting security updates in March 2027, so it's for testing only. We suggest a Pixel 9a or newer: 7 years of updates and the fewest manufacturer extras to lock down. A Samsung A-series also works but has more extras. Buy new, or reuse?
2. **Trusted person and backup person, for each phone.** If you and the trusted person are each other's trusted person, we recommend a third person as backup. Each needs a Google account that is at least 3 days old, and that they can sign in to from a device other than the Plainphone phones.
3. **License.** We recommend **Apache-2.0**. It lets us reuse Google's and Headwind's open-source code.
4. **Must-have apps on each phone.** For example: your bank, maps, ride-share, Signal or WhatsApp. We test these before anything else, because if they break, the plan changes.
5. **Carrier and plan for each phone.** It needs data so that group texts with iPhones and picture messages work away from Wi-Fi.
6. **Who keeps the signing key** that Plainphone updates are signed with, and who keeps its offline backup. If the key is lost, Plainphone can't be updated without setting the phone up again.

## Limits we need you to accept (or reject)

These are things Plainphone **can't** fully stop. We'd rather say so now.

- **Web pages inside approved apps.** If an approved app has its own built-in web pages (help pages, articles, sign-in screens), those still work. We give each app a plain "web leak" note so the trusted person can decide.
- **Wi-Fi that needs a sign-in page** (hotels, hospitals, ferries, some workplaces) won't work. Mobile data and normal Wi-Fi are fine. Calls and 9-1-1 don't depend on Wi-Fi. If either of you relies on that kind of Wi-Fi, or on Wi-Fi calling where there is no cell signal, tell us.
- **The custody limit.** When the trusted person unlocks the Play Store or Settings for a few minutes, they must stay with the phone. If Plainphone itself broke during those minutes, which is rare, that unlock would stay open until a helper fixes the phone. The phone is never handed back until everything checks out. If you don't accept this, we have to find a different mechanism first, and that becomes a go/no-go question.
- **Background activity.** An app that gets installed without approval can't be **opened**. But if hiding it fails, it might run in the background for a while.
- **A hardware-button wipe.** Someone holding the phone can always wipe it from the recovery menu. Reset protection then requires the trusted person's Google account, so the phone can't simply be set up fresh. Plainphone has to be set up again, and anything not backed up is lost.
- **Phone software updates.** Each Android update needs a short review from us. Until then, the phone runs in a safe mode that keeps everything locked and keeps emergency features working. You and the helper get a text when an update is coming. This is an **ongoing job** for the team for as long as the phones are in use.
- **Emergency alerts (Alert Ready).** We check that alerts actually appear on the real phone before handing it over. If we can't observe one, the phone stays with us, or you separately accept a narrower promise for that phone.

## What happens after you approve

1. The spike continues: lock task over a full day, Play updates in the background, the escape list, and Motorola's extras.
2. If anything fails a go/no-go check, we come back with a revised plan before writing MVP code.
3. When you pick a phone model, we ask separately before setting up your phone.
4. Your phone first, two weeks of use, then the trusted person's, each with a separate OK from you.
