Owners:
- **AE**: Android Platform Engineer (Claude)
- **UX**: Product & UX Designer (Claude)
- **VE**: Verifier & Escape Artist (OpenAI)
- **Lead**: Plainphone Lead

Rules:
- Every task written by a Claude agent is reviewed by VE.
- Nothing here starts until Beaux approves this proposal.
- Real phones need Beaux's approval for each specific phone.

Test IDs and gates are listed in `docs/verification-matrix.md`.

## 1. Spike setup (emulator only, throwaway code in `spikes/`)

- [ ] 1.1 AE: Google Play emulator images: API 35, API 36 or newest, and API 37 if available. Record exact setup commands and build IDs in `spikes/README.md`.
- [ ] 1.2 AE: A minimal throwaway Device Owner app in `spikes/dpc/`, plus a test app for Q2 that we publish (repeatable update source) and a negative-control harness.
- [ ] 1.3 Lead: Get each phone's must-have app list from Beaux (design Open Question 4) for Q4.

## 2. Spike: go/no-go questions (answer before any MVP code)

- [ ] 2.1 AE: Q3, setup on Google Play images. Run first; it is cheap.
- [ ] 2.2 AE: Q1, a normal day under lock task, with the block flag on and off, plus the UI exceptions list with negative tests.
- [ ] 2.3 AE: Q2, updates (S1–S4, S7, and the API 37 constant).
- [ ] 2.4 AE: Q4, must-have apps, browser classifier, link matrix, Wi-Fi sign-in page, reachable system screens.
- [ ] 2.5 VE: Review 2.1–2.4. Falsify each conclusion and check the negative controls. Add new escape routes to the threat list.
- [ ] 2.6 Lead: Apply the go/no-go list in ADR 0001. If the architecture or the promises change, write a revised ADR and get Beaux's approval before section 4.

## 3. Spike: required before handover

- [ ] 3.1 AE: Q5 escape baseline, plus the Verifier's vectors F1–F14.
- [ ] 3.2 AE: Q6, boot, replacement and policy damage.
- [ ] 3.3 AE: Q7, turning off: resumable, and without a wipe.
- [ ] 3.4 AE: Q10, reset protection with the trusted person's `userId`.
- [ ] 3.5 AE: Q12, Play session and maintenance mode cannot be escaped or left open.
- [ ] 3.6 AE: Q14, emergency behavioral gate with a negative control. Q8, texting and calling defaults.
- [ ] 3.7 AE: Optional Q9 and Q11 if cheap. Otherwise record why they were deferred.
- [ ] 3.8 VE: Escape-artist pass on the emulator against E01–E29 and F1–F14. Every bypass gets reproduction steps.
- [ ] 3.9 Lead: Close the spike. Update the design and the "What Plainphone can't stop" list, and get Beaux's written acceptance of every leftover risk.

## 4. MVP: core app (`app/`)

- [ ] 4.1 AE: Project skeleton (Kotlin, Compose, minSdk 30), Device Owner receiver, license headers (after Beaux's license decision).
- [ ] 4.2 AE: Policy engine as a pure function. Never-hide guard, lock-task UI list, apply order, readback. Unit tests first.
- [ ] 4.3 AE: Policy storage: atomic writes, schema check, last-known-good, Direct Boot copy. Re-apply on boot, package events, every 15 minutes and after actions.
- [ ] 4.4 AE: Emergency behavioral release gate, with the negative-control build.
- [ ] 4.5 AE: Lock task with the block flag, and the home screen (calm-home-screen).
- [ ] 4.6 AE: Browser classifier, hiding, link matrix, and the isolated blocked-link screen (web-containment).
- [ ] 4.7 AE: Play session state machine, catching unapproved installs, staleness tracking (approved-apps).
- [ ] 4.8 AE: PIN, recovery code, fresh authority, requests, maintenance mode, turning off (trusted-person-controls).
- [ ] 4.9 AE: Plainphone's own settings screens (Wi-Fi, Bluetooth, volume, brightness, text size, battery).
- [ ] 4.10 AE: Setup flow, reset protection, key attestation check, handover self-check (guided-setup).

## 5. MVP: words and flows

- [ ] 5.1 UX: Plain-language flows and copy for: home, request an app, blocked link, trusted-person mode, "What Plainphone can't stop", turning off, setup. Test them on someone who has never seen them.
- [ ] 5.2 UX: Starter catalog with web-leak notes for each version, covering Beaux and the trusted person's apps.
- [ ] 5.3 UX + AE: `docs/setup-guide.md` and the checking script, including how to find the trusted person's `userId`. Dry-run them on the emulator, done by someone who did not write them.

## 6. MVP: verification and handover

- [ ] 6.1 VE: Full escape-artist review and code review of the MVP on the emulator. Verdict: approve, request changes, or blocked.
- [ ] 6.2 AE: Fix every confirmed finding, or Lead records it as a leftover risk accepted by Beaux.
- [ ] 6.3 Lead: Ask Beaux to approve one specific phone. Include the device checklist: Q3, Q10, Q13 manufacturer extras, the 9-1-1 procedure with carrier and E-Comm, RCS on the carrier, iPhone group texts, must-have apps, recovery-mode wipe.
- [ ] 6.4 Lead: After two weeks on Beaux's phone, ask Beaux to approve the trusted person's phone.
