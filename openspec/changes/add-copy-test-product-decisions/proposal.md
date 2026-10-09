**Status:** revision 2, after cross-model review PLA-31 (request changes, F1–F11; responses in `design.md`). Goes to re-review, then Beaux's approval. Nothing here is built until Beaux approves it.

## Why

Beaux ran the copy test himself (PLA-12, Telegram 4473/4474) and raised five product questions. The Lead's answers are in PLA-14's `product-questions` document. Beaux chose on 2026-10-08 at 20:05 PDT (card `c60170d6`, Telegram 4481):

1. **Up to two trusted people from the start.** Each has their own PIN and lockout. Both get texts. Either can approve. **Plus a self-managed option**: for someone with no trusted person, or who doesn't want one, the user can raise their own permissions for a limited time.
2. **Web window:** the user opens it up to 5 times a day, 5 minutes each, with an option to extend. *Not in this change.* It is one mechanism with the Wi-Fi sign-in window and is handled in PLA-17.
3. **Turning Plainphone off:** PIN, then a **24-hour wait** with a text to the trusted person(s), who can cancel. The recovery code skips the wait.
4. **Google tracking:** privacy steps during setup and a plain note. Stay on stock Android.

These change what the approved plan (`add-plainphone-spike-and-mvp`) promises about who can relax the lockdown, so they need their own change, a cross-model review and Beaux's approval before the MVP builds them.

## What Changes

- **Up to two trusted people per phone.**
  - Each has their own name, phone number, PIN, lockout counter, recovery code and reset-protection Google ID.
  - Either can do every trusted-person action alone. Neither can see or change the other's PIN.
  - **Texts go to both**, including a receipt to the person who acted, so a PIN used by someone else shows up. App requests go to both.
  - **Disagreements:** the two are equals. For actions the other person can put back later (approving or removing an app, opening a session), the last action stands, and both see it in a shared history. For actions that can't be put back, a 24-hour wait applies and anyone can cancel (next bullet).
- **One 24-hour wait rule ("waiting changes")** for actions that can't be put back:
  - turning Plainphone off;
  - changing who is a trusted person (adding one, handing your role to someone else, removing the other one);
  - switching mode, **in either direction**;
  - in self-managed mode, loosening the waits or limits.
  - **One at a time.** Only one waiting change can be pending on the phone.
  - A PIN starts it. The record and the texts are saved together before anything is shown, so a crash can't lose the notice. Either trusted person, or anyone holding the phone, can cancel; by text reply too if the spike (Q15) shows replies can be received. In supervised mode, a trusted person's **recovery code** skips the wait, on a screen that names the change.
  - **Re-checked before it happens.** If the PIN that started it has since been replaced, or the set of people or the mode has changed, the change is cancelled. So replacing a PIN someone saw also cancels anything they started.
  - Time is counted on one "proven running time" clock. Restarts, switching the phone off and clock changes can only make a wait longer.
  - **Why changes to people and mode wait too:** someone who has seen a PIN could otherwise make themselves a trusted person for good. And a self-managed user could otherwise enrol a second identity they control as "trusted person" and skip every limit (PLA-31 F1).
- **Self-managed mode (ADR 0001 option D, as a per-phone mode).**
  - Same app, same lockdown at rest. The user holds the PIN and is their own trusted person.
  - **It is friction, not a lock.** The user can always get through: by waiting 24 hours, or by wiping the phone. Plainphone says so in plain words at setup and on "What Plainphone can't stop".
  - **Opening up ("elevation")** is a single-use grant of one kind (Play, Settings, approve one app, or one Plainphone update), under the supervised-session contract (journal first, deadline, revoked on every start, protected-state check, mitigation B), plus friction:
    - a **10-minute countdown** after the PIN, then one more tap to start;
    - at most **2 elevations in any 24 hours**, counted before anything opens;
    - deadlines of 5 or 10 minutes, with no extending; one at a time;
    - no setting makes elevation permanent or removes the countdown or the limit. Stricter settings apply at once; looser ones wait 24 hours.
  - **Reset protection** names the user's own Google account (and optionally a backup person). A recovery-mode wipe then erases the phone and lets the user set it up without Plainphone. Stated openly.
  - **The recovery code** resets the PIN only. In self-managed mode it does **not** skip any wait, because the user holds it. *This is the Lead's reading of Beaux's rule 3, which was given for trusted people; Beaux confirms it with this change.*
- **Turning off waits 24 hours.** The existing off switch (fresh PIN, two confirmations, full restore list, journaled) gains the wait. When the wait ends and passes its re-check, turning off proceeds by itself and texts everyone. The home screen shows a line while it is pending, and Cancel is also on the "needs attention" screen and in Plainphone's settings.
- **Privacy steps during setup.** After the user signs in to Google, the guide walks the helper and the user through: pausing Web & App Activity, turning Timeline off, turning ad personalisation off, and deleting the advertising ID. Each is recorded as done or skipped; Plainphone can't check them. "What Plainphone can't stop" gains an accurate line: Plainphone has no server, analytics or account; it sends texts only to the trusted people; Google still collects data Plainphone can't stop.
- **Mitigation B at every unhide** (PLA-22): sessions, approvals, repairs, the emergency floor and turning off. Discretionary unhides don't start on a nearly flat battery; emergency repairs are never delayed. A power loss in the short gap between the unhide and the save can still boot-loop the phone; that is measured and disclosed.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `trusted-person-controls`: two trusted people, texts to both, the proven running-time clock, waiting changes, changing trusted people, self-managed mode and elevation grants, switching modes, per-person PIN and recovery code, the session contract's unhide step, turn-off wait, recovery for both modes, disclosure lines.
- `guided-setup`: choosing the mode, setting up one or two trusted people, reset-protection IDs per mode, privacy steps, handover check items.
- `calm-home-screen`: one notice line at a time, with new lines for a pending turn-off or change and for self-managed elevation.
- `device-lockdown`: every unhide is saved at once (mitigation B); reset protection lists each trusted person (or the user in self-managed mode) and is updated, read back, before new credentials take effect.

## Impact

- **Order:** this change applies on top of `add-plainphone-spike-and-mvp`, which is approved but not archived. That change archives first. The MVP build ([PLA-15](/PLA/issues/PLA-15)) waits on this change's approval.
- **ADR 0001:** notes added on option D as a mode, and on who can relax the lockdown. The architecture (option A) is unchanged.
- **New feasibility questions (emulator):**
  - Q15: can Plainphone, which is not the default SMS app, receive replies to cancel a pending wait, with `RECEIVE_SMS` granted by the Device Owner?
  - Q10 grows: reset protection with two or three IDs, after the list changes, and when an update fails.
  - Q12 grows: the clock, waiting changes, generations and elevation grants, with fault injection at every write.
  - Mitigation B rests on 3 emulator trials on API 35 (PLA-22). Task 1.3 re-tests it on API 35 and 36 at every unhide call site and measures the unhide-to-save gap (PLA-25 is reviewing it).
- **Texts:** more texts go out (to two people, and for every action). No servers, no new paid services.
- **Not changed:** the emergency promises, the web rules, the updater, the custody limit for supervised sessions, the device approval rules. The web window and Wi-Fi sign-in stay in PLA-17.
