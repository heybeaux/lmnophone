**Status:** draft for cross-model review, then Beaux's approval. Nothing here is built until Beaux approves it.

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
  - Every privileged action is texted to each trusted person who didn't do it. App requests go to both.
  - **Disagreements:** the two are equals. For actions the other person can undo later (approving or removing an app, opening a session), the last action stands, and both see it in a shared history. For actions that can't be undone by the other person afterwards, a 24-hour wait applies and either can cancel (next bullet).
- **One 24-hour wait rule** for actions that can't be undone afterwards:
  - turning Plainphone off;
  - changing who is a trusted person (adding one, handing your role to someone else, removing the other one);
  - switching a supervised phone to self-managed;
  - in self-managed mode, loosening the waits or limits.
  - A PIN starts the wait. Texts go out at once. Either trusted person can cancel, on the phone or by replying to the text (if the spike shows replies can be received). Anyone holding the phone can also cancel. In supervised mode, a trusted person's **recovery code** skips the wait.
  - The wait is written down before anything else happens and survives restarts. Restarts and clock changes can only make it longer, never shorter. If the record is damaged, the change is cancelled and the phone stays locked down.
  - **Why the handover waits too:** someone who has seen a PIN could otherwise make themselves a trusted person for good. The user alone can never change who the trusted people are.
- **Self-managed mode (ADR 0001 option D, as a per-phone mode).**
  - Same app, same lockdown at rest. The user holds the PIN and is their own trusted person.
  - **It is friction, not a lock.** The user can always get through, by waiting. Plainphone says so in plain words at setup and on "What Plainphone can't stop".
  - **Opening up ("elevation")** reuses the supervised-session contract (journal first, deadline, revoked on every start, protected-state check, and PLA-22's mitigation B), plus friction:
    - a **10-minute wait** after the PIN before it starts, then one more tap to confirm;
    - at most **2 elevations in any 24 hours**, counted before anything opens;
    - the same deadlines as supervised sessions (Play 10 min, Settings 5 min), with no extending;
    - no setting makes elevation permanent or removes the wait or the limit. Making the phone stricter takes effect at once. Making it looser takes the 24-hour wait.
  - **Reset protection** names the user's own Google account (and optionally a backup person). A recovery-mode wipe then gives the user a plain phone. That matches the "friction, not a lock" promise and is stated openly.
  - **The recovery code** resets the PIN only. In self-managed mode it does **not** skip any wait, because the user holds it. *This is the Lead's reading of Beaux's rule 3, which was given for trusted people; Beaux confirms it with this change.*
  - **Switching modes:** supervised → self-managed follows the turn-off rule (a trusted person's PIN, then the 24-hour wait, cancellable; their recovery code skips it). Self-managed → supervised is stricter and takes effect once the new trusted person has set up their PIN, code and number.
- **Turning off waits 24 hours.** The existing off switch (fresh PIN, two confirmations, full restore list, journaled) gains the wait. When the wait ends, turning off proceeds by itself and texts everyone. The home screen shows a line while a turn-off is pending.
- **Privacy steps during setup.** After the user signs in to Google, the guide walks the helper and the user through: pausing Web & App Activity, turning Timeline off, turning ad personalisation off, and deleting the advertising ID. Each is recorded as done or skipped; Plainphone can't check them. "What Plainphone can't stop" gains a line about Google's data collection.
- **Mitigation B becomes part of the session contract**: every session start that unhides a package forces PackageManager to save at once (PLA-22), and a session that would unhide a package doesn't start on a nearly flat battery unless charging.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `trusted-person-controls`: two trusted people, the 24-hour wait rule, self-managed mode and elevation, per-person PIN and recovery code, notices to both, mitigation B in the session contract, turn-off wait, disclosure lines.
- `guided-setup`: choosing the mode, setting up one or two trusted people, reset-protection IDs per mode, privacy steps, handover check items.
- `calm-home-screen`: one notice line at a time, with new lines for a pending turn-off or change and for self-managed elevation.
- `device-lockdown`: reset protection lists each trusted person (or the user in self-managed mode) and is updated on every change.

## Impact

- **Order:** this change applies on top of `add-plainphone-spike-and-mvp`, which is approved but not archived. That change archives first. The MVP build ([PLA-15](/PLA/issues/PLA-15)) waits on this change's approval.
- **ADR 0001:** notes added on option D as a mode, and on who can relax the lockdown. The architecture (option A) is unchanged.
- **New feasibility questions (emulator):**
  - Q15: can Plainphone, which is not the default SMS app, receive replies to cancel a pending wait, with `RECEIVE_SMS` granted by the Device Owner?
  - Q10 grows: reset protection with two or three IDs, and after the list changes.
  - Q12 grows: the elevation start path, the wait records and the daily count across reboots and power loss.
  - Mitigation B rests on 3 emulator trials (PLA-22). It is re-tested before release (PLA-25 is reviewing it).
- **Texts:** more texts go out (to two people, and for every action). No servers, no new paid services.
- **Not changed:** the emergency promises, the web rules, the updater, the custody limit for supervised sessions, the device approval rules. The web window and Wi-Fi sign-in stay in PLA-17.
