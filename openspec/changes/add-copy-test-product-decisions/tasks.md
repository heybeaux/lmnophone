Owners and rules are the same as in `add-plainphone-spike-and-mvp/tasks.md`:
- **AE**: Android Platform Engineer (Claude). **UX**: Product & UX Designer (Claude). **VE**: Verifier & Escape Artist (OpenAI). **Lead**: Plainphone Lead.
- Every task written by a Claude agent is reviewed by VE.
- Nothing here starts until Beaux approves this change. The build tasks join the MVP build (PLA-15).
- Real phones need Beaux's approval for each specific phone.

## 1. Feasibility (emulator only, throwaway code in `spikes/`)

- [ ] 1.1 AE: Q15. Can Plainphone, which is not the default SMS app, receive an incoming text with `RECEIVE_SMS` granted by the Device Owner (`setPermissionGrantState`), on API 35 and 36 Google Play images? Include: restricted-permission allowlisting, two SIMs, the phone locked and before first unlock, and a negative control with the permission denied. Result decides whether cancel by text is built.
- [ ] 1.2 AE: Q12 extension. The elevation path and the wait records: kill Plainphone and power off (`emu kill`) during the elevation countdown, right after Start, and mid-session; reboot during a 24-hour wait; damage each record. Check that turns are never refunded, waits never shorten, a damaged wait record cancels its change, and a damaged count gives "no turns left".
- [ ] 1.3 AE: Mitigation B in the session start path, with the battery guard. Repeat PLA-22's power-loss trials on the MVP's real start path (at least 10 trials at 0–5 s, on API 35 and 36). Fold in PLA-25's findings.
- [ ] 1.4 AE: Q10 extension (planned now, run with Q10 on the target phone under its board-approved procedure): reset protection with three `userId` values, and after the list changes.
- [ ] 1.5 VE: Review 1.1–1.3. Try to shorten a wait, refund a turn, or start a waiting change from outside the PIN path.

## 2. MVP build (added to PLA-15)

- [ ] 2.1 AE: Trusted-person model: one or two people, each with their own PIN, lockout, recovery code, number and `userId`. "Who are you?" on the PIN screen. Unit tests first, including one PIN never checked against the other.
- [ ] 2.2 AE: Notices to every trusted person, per-recipient delivery status, and the 90-day history list in device-protected storage.
- [ ] 2.3 AE: Waiting changes: wait record written first, conservative counting across reboots, cancel paths (PIN, anyone on the phone, and text reply if Q15 passed), recovery-code skip in supervised mode only, damaged record cancels, one per kind.
- [ ] 2.4 AE: Changing trusted people (add, hand over, remove) with pending credentials, the journaled completion and the reset-protection update order.
- [ ] 2.5 AE: Turning off behind the 24-hour wait, then the existing turn-off journal (task 4.8 of the approved plan).
- [ ] 2.6 AE: Self-managed mode: the stricter/looser table, elevation (countdown, Start, durable daily count, deadlines, settable limits), no request flow, monthly check through elevation, recovery code resets the PIN only.
- [ ] 2.7 AE: Switching modes in both directions.
- [ ] 2.8 AE: Session contract step 3: mitigation B after every unhide, readback, and the battery guard.
- [ ] 2.9 AE: Home-screen notice line priority and tap-to-cancel for pending changes.
- [ ] 2.10 AE: Setup: mode choice, one or two trusted people, per-mode reset-protection list, privacy-step record, per-person test texts in the handover check.

## 3. Words and guide

- [ ] 3.1 UX: Plain copy for: "Who are you?", the equals note at setup, notices, waiting-change texts and their cancel replies, the home-screen lines, self-managed setup ("friction, not a lock"), the elevation countdown, the new "What Plainphone can't stop" lines, and the privacy steps. Update `docs/ux/` flows.
- [ ] 3.2 UX + AE: Setup guide: mode choice, People API steps for each account holder, the privacy steps with exact paths per supported build, confirmed in the dry run (task 5.3 of the approved plan).

## 4. Verification

- [ ] 4.1 VE: Escape-artist pass on the new paths, on the emulator: become a trusted person without one's PIN, shorten or skip a wait without a recovery code, refund an elevation turn, make elevation permanent, start a waiting change by text or intent, and abuse the history list or notices.
- [ ] 4.2 Lead: Update the "What Plainphone can't stop" list and the approval packet, and get Beaux's written acceptance of any new leftover risk before handover.
