Owners:
- **AE**: Android Platform Engineer (Claude)
- **UX**: Product & UX Designer (Claude)
- **VE**: Verifier & Escape Artist (OpenAI)
- **Lead**: LMNOPhone Lead

Rules:
- Every task written by a Claude agent is reviewed by VE.
- Nothing here starts until Beaux approves this proposal.
- Real phones need Beaux's approval for each specific phone.

Test IDs and gates are listed in `docs/verification-matrix.md`.

## 1. Spike setup (emulator only, throwaway code in `spikes/`)

- [ ] 1.1 AE: Google Play emulator images: API 35, API 36 or newest, and API 37 if available. Record exact setup commands and build IDs in `spikes/README.md`.
- [ ] 1.2 AE: A minimal throwaway Device Owner app in `spikes/dpc/`, plus a negative-control harness. The Q2 update source is preinstalled Google apps rolled back with `pm uninstall-system-updates`. Nothing is published to Play.
- [x] 1.3 Lead: Beaux's must-have apps are Slack, Spotify, and maybe Telegram (Telegram 4446). Q4 tests them on the spare, and Telegram's in-app browser goes on the web-leak list. The partner's must-have apps are Gmail and Outlook (Telegram 4448/4451); Q4 tests them too, with web-leak notes on how each opens links in mail.

## 2. Spike: go/no-go questions (answer before any MVP code)

- [x] 2.1a AE: Q3 on the device. adb setup works on the spare Motorola XT2415 / Android 15 (PLA-5 A1), and the way back needs no wipe (A2).
- [ ] 2.1b AE: Q3 emulator re-check on Google Play images.
- [ ] 2.2 AE: Q1, a normal day under lock task, with the block flag on and off, plus the UI exceptions list with negative tests.
- [ ] 2.3 AE: Q2, updates (S1–S4, S7, and the API 37 constant).
- [ ] 2.4 AE: Q4, must-have apps, browser classifier, link matrix, Wi-Fi sign-in page, reachable system screens.
- [ ] 2.5 VE: Review 2.1–2.4. Falsify each conclusion and check the negative controls. Add new escape routes to the threat list.
- [x] 2.6 Lead: Apply the go/no-go list in ADR 0001. If the architecture or the promises change, write a revised ADR and get Beaux's approval before section 4.
  - First pass, 2026-10-09 (PLA-14): **not passed yet**. No item failed. #1, #2, #3 and #5 are inconclusive pending report corrections and missing evidence (reviews in `docs/reviews/`). #7 passed (custody limit accepted with the plan). #4 and #6 are checked before handover. New risk: the unhide/power-loss boot loop (PLA-22).
  - **Outcome, 2026-10-09 (PLA-14): GO for the MVP build, on the emulator and as test-only builds on the spare Motorola.**
    - No go/no-go item failed, and the architecture and promises are unchanged, so there is no revised ADR.
    - Beaux directed the build first (Telegram 4496/4498, 4509). He accepted the leftover boot-loop risk on the spare, with a recovery erase allowed there only (card d3be49b8).
    - Items not yet shown are now **handover conditions** (task 6.3):
      - #2: 72 hours of unattended updates on the MVP test build (PLA-24/PLA-27);
      - #3/#5: signed-in app checks and per-app web-leak acceptance;
      - #4: Q7/Q10;
      - #6: setup time;
      - the boot-loop risk on the target phone.

## 3. Spike: required before handover

- [ ] 3.1 AE: Q5 escape baseline, plus the Verifier's vectors F1–F14.
- [ ] 3.2 AE: Q6, boot, replacement and policy damage.
- [ ] 3.3 AE: Q7, turning off: resumable, and without a wipe.
- [ ] 3.4 AE: Q10, reset protection with the trusted person's `userId`.
- [ ] 3.5 AE: Q12, for both Play and Settings sessions.
  - Revocation runs first on every start where LMNOPhone can run (G0).
  - With a build that crashes on every start: measure what stays reachable and report it against the custody limit Beaux accepted.
  - The protected-state check restores protections.
  - The local-Wi-Fi update fixtures pass (R4).
- [ ] 3.6 AE: Q14, emergency behavioral gate with a negative control. Q8, texting and calling defaults.
- [ ] 3.7 AE: Optional Q9 and Q11 if cheap. Otherwise record why they were deferred.
- [ ] 3.8 VE: Escape-artist pass on the emulator against E01–E29 and F1–F14. Every bypass gets reproduction steps.
- [ ] 3.9 Lead: Close the spike. Update the design and the "What LMNOPhone can't stop" list, and get Beaux's written acceptance of every leftover risk.

## 4. MVP: core app (`app/`)

- [ ] 4.1 AE: Project skeleton (Kotlin, Compose, minSdk 30), Device Owner receiver, license headers (after Beaux's license decision).
- [ ] 4.2 AE: Policy engine as a pure function. Never-hide guard, lock-task UI list, apply order, readback. Unit tests first.
- [ ] 4.3 AE: Policy storage: atomic writes, schema check, last-known-good, Direct Boot copy. Re-apply on boot, package events, every 15 minutes and after actions.
- [ ] 4.4 AE: Emergency behavioral release gate, with the negative-control build.
- [ ] 4.5 AE: Lock task with the block flag, and the home screen (calm-home-screen).
- [ ] 4.6 AE: Browser classifier, hiding, link matrix, and the isolated blocked-link screen (web-containment).
- [ ] 4.7 AE: Play session state machine, catching unapproved installs, staleness tracking (approved-apps).
- [ ] 4.8 AE: PIN and recovery code (separate counters), fresh authority, requests, supervised session contract and journal, LMNOPhone update over local Wi-Fi, turning off with the full restore list (trusted-person-controls).
- [ ] 4.9 AE: LMNOPhone's own settings screens (Wi-Fi, Bluetooth, volume, brightness, text size, battery).
  - [x] Build 30: read-only Bluetooth state and paired devices (approved PLA-428); pairing remains deferred.
- [x] 4.9a Preview 28: read-only Wi-Fi state/nearby names, U7 fallback copy, and corrected Text size sample (PLA-421). Joining/forgetting and other unfinished settings remain in 4.9.
- [x] 4.9b Preview 31: in-app built-in Ringtone chooser, bounded previews, settings-write/read-back checks and U7 copy (PLA-431). Other unfinished settings remain in 4.9.
- [ ] 4.10 AE: Setup flow, preinstall sweep before Wi-Fi, reset-protection IDs with a backup person, key attestation check, handover self-check (guided-setup).
- [ ] 4.11 AE: Build inventory for each supported build, the emergency-safe baseline, the locked-boot path, and the first-frame lock task invariant (device-lockdown).
- [ ] 4.12 AE: Monthly check state machine (stored due date, retries, home-screen line) and the SMS permission setup (approved-apps).
- [ ] 4.13 AE: Runtime emergency floor; unknown-build mode; `createPostponeInstallPolicy` and `onSystemUpdatePending` texts; the "Phone software changed" list (device-lockdown).
- [ ] 4.14 AE: Permission choices at approval, applied with `setPermissionGrantState` (approved-apps).

## 7. Ongoing support (after handover)

- [ ] 7.1 AE + VE: For each new system build on a supported phone, prepare and review its inventory, then ship it as a signed local update. Re-run the emergency and handover checks on that build. The Lead tracks it.

## 5. MVP: words and flows

- [ ] 5.1 UX: Plain-language flows and copy for: home, request an app, blocked link, trusted-person mode, "What LMNOPhone can't stop", turning off, setup. Human copy test: Beaux's pass on PLA-12 (Telegram 4473/4474) counts for this stage (Telegram 4477), and his wording fixes are applied. The next human check is Beaux using the real build on the spare Motorola. No outside testers until Beaux decides he's comfortable asking others.
- [ ] 5.1b UX: Visual pass (PLA-36, Beaux, Telegram 4485). Black, white and grey only on the home screen and all LMNOPhone screens. Lexend font and the GC-inspired palette. A text-only home screen like Beaux's reference image. No Emergency button on the home screen (Telegram 4487); 9-1-1 through Phone, the lock screen and the power-button SOS gesture, with an optional entry inside More. AE builds the MVP to this spec, and Beaux judges it on the real build (no mockup review).
- [ ] 5.2 UX: Starter catalog with web-leak notes for each version, covering the two initial users' apps.
- [ ] 5.3 UX + AE: `docs/setup-guide.md` and the checking script, including how to find the trusted person's `userId`. Dry-run them on the emulator, done by someone who did not write them.

## 6. MVP: verification and handover

- [ ] 6.1 VE: Full escape-artist review and code review of the MVP on the emulator. Verdict: approve, request changes, or blocked.
- [ ] 6.2 AE: Fix every confirmed finding, or Lead records it as a leftover risk accepted by Beaux.
- [ ] 6.3 Lead: (Includes the gate 2.6 carry-overs: #2 unattended updates on the MVP build, #3/#5 signed-in app checks and web-leak acceptance, #4, #6, and the boot-loop risk on that phone.) Ask Beaux to approve the target phone model and one unit, and to record acceptance of the custody limit and the leftover risks. Include the checklist to complete before handover: Q3 on that build; Q10 with a board-approved recovery-wipe procedure; Q13 manufacturer extras; Q14 device emergency checks (no live call unless there is an agreed procedure); the PLA-22 unhide/power-loss mitigation on that build (Q6, with a board-approved recovery-wipe fallback); RCS on the carrier; iPhone group texts; Wi-Fi calling on ordinary Wi-Fi with the policy on, including that the carrier's IMS / Carrier Services packages are never hidden (Telegram 4481: used sometimes); must-have apps, signed in by Beaux himself on the first real LMNOPhone build (PLA-20 deferral, card `2d9260a3`), using the **carry-over checklist** in `spikes/device-moto/RESULT.md` § B-Q4 signed in: Telegram's in-app browser, Web tabs and mini apps first; Outlook's Privacy & Cookies web view after sign-in and after sign-out; links in one Gmail HTML test message; outside links, notifications, sign-out and sign-in again for Slack, Spotify, Telegram and Outlook. Each app's web-leak note stays "before sign-in only" until then.
- [ ] 6.4 Lead: After two weeks on Beaux's phone, ask Beaux to approve the partner's phone.

## Authorized build 32 Bluetooth discovery subset ([PLA-434](/PLA/issues/PLA-434))
- [x] Probe Location-off discovery and lock-task pairing between two private emulators.
- [x] Tests first: bounded nearby list, filtering, retry/end/error and lifecycle cancellation.
- [x] Own SCAN grant/readback, Bluetooth-only routes and pairing-not-ready device screen.
- [x] Changed screenshot checks at 1.0/1.3/1.5/1.8/2.0, with every changed image inspected.
- [x] In-place emulator upgrade, discovery exercise, no-call emergency and unchanged-setting readbacks.
- [ ] Single Lead review and in-place spare installation.

## Approved build 33 Wi-Fi subset (PLA-437)

- [x] Deterministic Location ownership, cleanup and join outcome controllers, tests first.
- [x] DO join adapter and native open/personal/unsupported join presentation.
- [x] Probe temporary Location, connection retention and Location-off reconnection on API 35.
- [x] Complete affected goldens and in-place emulator upgrade gate.
- [ ] Lead opposite-model review and in-place spare install/readback.

### Authorized tap-time Forget preview (PLA-448 / PLA-460)
- [x] Restore connected Forget and calls-first message navigation on delivered preview 36.
- [x] Open owned Location at tap time; bound identity acquisition; remove only the connected validated ID; verify the remaining set and disconnection; clean up using the existing call-aware controller.
- [x] Cover Location-off, no-ID, duplicate taps, delayed identity, failed readback and cleanup with red-first JVM tests.
- [x] Verify five font scales for changed screens, plus join/Bluetooth, navigation, emergency and update-postpone regressions.
- [x] Prove connected-only removal and Location-off cleanup on a private API 35 emulator; no spare access or live calls.

### Authorized T5 app-list preview ([PLA-514](/PLA/issues/PLA-514))

- [x] Tests first: production Home order persistence, floor refusal, fresh PIN, Back/pause cancellation, failed atomic save and failed re-apply.
- [x] Trusted-person T5 list, installed-version page and Remove confirmation, with fixed Messages/Phone and live floor guards.
- [x] Atomically preserve order across process restart and in-place upgrades; remove from starter list then re-apply, without uninstalling.
- [x] Inspect list/details/confirmation goldens at 360×640 dp and five scales through 200%; record existing baseline failures separately.
- [x] Private read-only API 35 emulator: reorder/Home, reboot, wrong PIN/Back, removal/hidden readback, data-preserving setup-command restoration and in-place upgrade. No calls or spare access.
- [x] Opposite-model review on [PLA-515](/PLA/issues/PLA-515): PASS for `7761bf65..71941c47`, with three nonblocking Low findings recorded for the Lead.
- [ ] Lead-routed spare installation on the latest release.
