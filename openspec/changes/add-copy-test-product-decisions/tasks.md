Owners and rules are the same as in `add-plainphone-spike-and-mvp/tasks.md`:
- **AE**: Android Platform Engineer (Claude). **UX**: Product & UX Designer (Claude). **VE**: Verifier & Escape Artist (OpenAI). **Lead**: Plainphone Lead.
- Every task written by a Claude agent is reviewed by VE.
- Nothing here starts until Beaux approves this change. The build tasks join the MVP build (PLA-15).
- Real phones need Beaux's approval for each specific phone.

## 1. Feasibility (emulator only, throwaway code in `spikes/`)

- [ ] 1.1 AE: Q15. Can Plainphone, which is not the default SMS app, receive an incoming text with `RECEIVE_SMS` (hard-restricted: installer allowlisting plus the Device Owner grant), on API 35 and 36 Google Play images? Include: allowlisting as provisioned by the setup script and kept across a Plainphone self-update, the permission kept after a Settings session, two SIMs and subscription handling, normalized sender numbers, the phone locked and before first unlock, and a negative control with the permission denied. Emulator injection is not proof of carrier delivery; the target-phone checklist re-checks it. Result decides whether cancel by text is built; if not, remove reply wording everywhere.
- [ ] 1.2 AE: Q12 extension, with fault injection at every write boundary (kill and `emu kill` before and after each atomic write):
  - the proven running-time clock: repeated process deaths in one boot, repeated boots with lost checkpoints, deep sleep, clock and time-zone jumps, two debits of different ages, a Start exactly at the window's end, and a damaged P (re-stamp at zero; quarantine write failing). Assert no interval is counted twice and no debit is refunded;
  - waiting changes: crash between confirm and the combined record-plus-text-queue write; resend of queued texts after restart; cancel with a crash before and after the cancelled state is written; a stale timer after cancel; damaged record while pending and while committing; both operation-journal copies damaged;
  - generations: a PIN replaced during a pending handover; a recovery-code PIN reset during a pending turn-off (must not skip); a second waiting change refused while one is pending;
  - elevation: countdown killed; Start double-tapped; a stricter countdown applied mid-countdown; a second countdown refused.
- [ ] 1.3 AE: Mitigation B at every unhide call site (session and elevation start, approval during a Play session, protected-state repair, emergency floor including locked boot, turning off), on API 35 and 36, at least 10 power-loss trials each at 0–5 s after the flip. Separately measure the unhide-to-flip gap with an injected pause between the two calls, and report the loop rate inside it. Check the battery guard refuses discretionary unhides and never delays emergency repair. Fold in PLA-25's findings.
- [ ] 1.4 AE: Q10 extension (planned now, run with Q10 on the target phone under its board-approved procedure): reset protection with three `userId` values; after the list changes (the outgoing account no longer passes); a failed update during a handover leaving the old people in charge; and the self-managed list naming the user's own account.
- [ ] 1.5 VE: Review 1.1–1.3. Try to shorten a wait, refund a turn, complete a cancelled or stale change, or start a waiting change from outside the PIN path.

## 2. MVP build (added to PLA-15)

- [ ] 2.1 AE: Trusted-person model: one or two people, each with their own PIN, lockout, recovery code, number, `userId` and credential generation. "Who are you?" on the PIN screen. Unit tests first: with distinct PINs, an entry under one name never uses the other verifier or counter; equal PINs are allowed and never compared.
- [ ] 2.2 AE: Texts to every trusted person including the actor, per-recipient delivery status, a durable text queue resent on start, and the 90-day history list in device-protected storage.
- [ ] 2.3 AE: The proven running-time clock, and the waiting-change controller: one pending change; record and text queue in one write; generations re-checked at commit; durable cancelled state checked by every callback; cancel paths (PIN, anyone on the phone, needs-attention screen, Plainphone settings, and text reply if Q15 passed); recovery-code skip that names the change, supervised only; commit point and two-copy operation journal; missing, damaged and failed cases.
- [ ] 2.4 AE: Changing trusted people (add, hand over, remove) with pending credentials and account checks; completion order: new reset-protection list read back, then the credential swap; a stuck change keeps the old people in charge and can be cancelled, restoring the old list.
- [ ] 2.5 AE: Turning off behind the 24-hour wait, then the existing turn-off journal (task 4.8 of the approved plan).
- [ ] 2.6 AE: Self-managed mode: the stricter/looser table; elevation as a single-use grant (kinds Play, Settings, Approve, Update; countdown; Start; debit and journal in one write; one at a time; used up on deadline, Done or restart; approvals after the deadline refused; updater unchanged); durable daily count; settable limits; no request flow; monthly check through a Play elevation; recovery code resets the PIN only.
- [ ] 2.7 AE: Switching modes, both directions as waiting changes. Test: a self-managed user with no turns left enrols a second identity they control as trusted person; the new PIN and code grant nothing for 24 hours of proven running time, and self-managed rules hold meanwhile.
- [ ] 2.8 AE: "Every unhide is saved at once" (device-lockdown) at every call site, with the real component flip, readback, failure handling, and the battery guard for discretionary unhides only.
- [ ] 2.9 AE: Home-screen notice line priority and tap-to-cancel, plus the pending change and Cancel on the needs-attention screen and in Plainphone's settings.
- [ ] 2.10 AE: Setup: mode choice, one or two trusted people, per-mode reset-protection list with account checks for every listed account (including the user's own in self-managed mode), privacy-step record, per-person test texts in the handover check.

## 3. Words and guide

- [ ] 3.1 UX: Plain copy for: "Who are you?", the equals note at setup, notices, waiting-change texts and their cancel replies, the home-screen lines, self-managed setup ("friction, not a lock"), the elevation countdown, the new "What Plainphone can't stop" lines, and the privacy steps. Update `docs/ux/` flows.
- [ ] 3.2 UX + AE: Setup guide: mode choice, People API steps for each account holder, the privacy steps with exact paths per supported build, confirmed in the dry run (task 5.3 of the approved plan).

## 4. Verification

- [ ] 4.1 VE: Escape-artist pass on the new paths, on the emulator, with these named cases at least: become a trusted person without one's PIN; enrol a second identity to leave self-managed mode early; keep a pending change alive after the seen PIN is replaced; complete a cancelled or stale change; shorten or skip a wait without a recovery code; refund an elevation turn; use a grant after its deadline or after a restart; run two grants or two waiting changes at once; hide the Cancel path; start a waiting change by text or intent; complete a handover without the reset-protection update; and abuse the history list or texts.
- [ ] 4.2 Lead: Update the "What Plainphone can't stop" list and the approval packet, and get Beaux's written acceptance of any new leftover risk before handover.
