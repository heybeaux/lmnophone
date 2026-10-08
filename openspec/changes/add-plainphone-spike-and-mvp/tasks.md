Owners:
- **AE**: Android Platform Engineer (Claude)
- **UX**: Product & UX Designer (Claude)
- **VE**: Verifier & Escape Artist (OpenAI)
- **Lead**: Plainphone Lead

Every Claude-authored task is reviewed by VE. Nothing here starts until Beaux approves this proposal. Real phones need Beaux's approval for each specific phone.

## 1. Spike setup (emulator only, throwaway code in `spikes/`)

- [ ] 1.1 AE: Create Google Play emulator images (API 35 and newest) and a minimal test Device Owner app in `spikes/dpc/`, labelled throwaway. Record the exact setup commands in `spikes/README.md`.
- [ ] 1.2 AE: Pick two sample third-party apps for the tests: one that uses OAuth/Custom Tabs, one that shows a WebView. Prefer apps from Beaux's list (design.md, Open Question 4).

## 2. Spike: make-or-break questions (answer before any MVP code)

- [ ] 2.1 AE: Q1, a full normal day under lock task, including 911 with and without the keyguard feature. Write `spikes/q1/RESULT.md`.
- [ ] 2.2 AE: Q2, app updates (tests S1–S4 and S7). Write `spikes/q2/RESULT.md`.
- [ ] 2.3 AE: Q3, adb and QR provisioning of an unapproved Device Owner on a Google Play image. Write `spikes/q3/RESULT.md`.
- [ ] 2.4 VE: Review 2.1–2.3. Try to break each conclusion, and add any escape route found to the threat list.
- [ ] 2.5 Lead: Update ADR 0001 and design.md with the results. If the architecture changes, ask Beaux to re-approve before section 4.

## 3. Spike: design-shaping questions

- [ ] 3.1 AE: Q4, web containment (browser role, Custom Tabs and sign-in flows, in-task Settings deep links, hiding the Google app).
- [ ] 3.2 AE: Q5, the escape baseline (E01, E04, E05, E06, E12, E13, E15, E21, E25, E29).
- [ ] 3.3 AE: Q6, state survives reboot and updates. Q7, turning off without a wipe. Q8, default SMS app and SMS/MMS under lock task.
- [ ] 3.4 AE: Q9–Q11 if cheap (DNS filter, factory reset protection, GrapheneOS emulator). Otherwise write down why they were deferred.
- [ ] 3.5 VE: Escape-artist pass on the emulator against the full threat list (E01–E29). Every bypass gets reproduction steps.
- [ ] 3.6 Lead: Close the spike. Final design update, plus the residual-risk list for the "What Plainphone can't stop" screen.

## 4. MVP: core app (`app/`)

- [ ] 4.1 AE: Project skeleton (Kotlin, Compose, minSdk 30), the Device Owner receiver, Apache-2.0 headers (pending Beaux's license decision).
- [ ] 4.2 AE: Policy engine as a pure function from policy file to a list of Device Owner calls, with protected-package refusal (D4, D8). Unit tests first.
- [ ] 4.3 AE: Policy applier, re-run on boot, on package events, hourly and after trusted-person actions. Covers device-lockdown requirements.
- [ ] 4.4 AE: Emergency-access instrumented test as a release gate (essential-calling-and-texting).
- [ ] 4.5 AE: Lock task setup, and the home screen backed by the allowlist (calm-home-screen).
- [ ] 4.6 AE: Blocked-link screen and browser/search hiding (web-containment).
- [ ] 4.7 AE: Supervised Play session, catching unapproved installs, hide on remove (approved-apps).
- [ ] 4.8 AE: Trusted-person PIN, recovery code, requests by SMS, turning off (trusted-person-controls).
- [ ] 4.9 AE: Built-in settings: Wi-Fi, Bluetooth, volume, brightness, text size, battery.

## 5. MVP: words and flows

- [ ] 5.1 UX: Plain-language flows and copy for: home screen, request an app, blocked link, trusted-person mode, "What Plainphone can't stop", turning off, and on-phone setup. Test the copy on someone who has never seen it.
- [ ] 5.2 UX: Starter catalog with web-leak notes for the apps on Beaux and the trusted person's lists.
- [ ] 5.3 UX + AE: `docs/setup-guide.md` and the setup script. Dry-run them on the emulator by someone who did not write them.

## 6. MVP: verification and handover

- [ ] 6.1 VE: Full escape-artist review of the MVP on the emulator, plus a code review of 4.x. Verdict: approve, request changes, or blocked.
- [ ] 6.2 AE: Fix every confirmed finding, or the Lead records it as an accepted residual risk with Beaux's agreement.
- [ ] 6.3 Lead: Ask Beaux to approve one real phone for a first install, with the real-device checklist: Q3 adb/QR allowlist, 911 test procedure, RCS with the carrier, iPhone group texts, banking apps, recovery wipe and factory reset protection.
- [ ] 6.4 Lead: After approval and two weeks on Beaux's phone, ask Beaux to approve the trusted person's phone.
