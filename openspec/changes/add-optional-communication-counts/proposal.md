**Status:** revision 5 (Lead, PLA-287; Verifier APPROVE WITH CHANGES on revision 4, PLA-301, items applied). Previously revision 4 (resolves PLA-297 findings 1–3, after PLA-295 findings 1–6 in revision 3). Previously revision 2 (Lead, PLA-287). Scope approved by Beaux (Telegram 3152, recorded in TEAM-POLICY.md); no separate concept approval is needed. Spike PLA-291 answered C1–C7: feasible for SMS/MMS and missed calls; RCS not verified. UI built and reviewed (PLA-292). Next: cross-model review of this delta, host slice, then preview.

## Why

Beaux asked for an optional setting that shows two quiet counts on Home: missed calls on Phone and unread messages on Messages. Both must be black and white and calm. Today `calm-home-screen` forbids all badges. This change makes one bounded exception for these two counts only. Every other app count stays excluded.

## What Changes

- **One switch in LMNOPhone Settings**: "Show missed calls and unread texts on Home". It is **off** by default, it is saved, and the user can change it without a PIN. It is a personal display choice. It does not change app approval, trusted-person authority or any lockdown policy.
- **On:** Home shows a plain number with Phone (missed calls) and with Messages (unread texts) when that count is above zero and reliable. Zero shows nothing.
- **Off:** both numbers disappear at once, and LMNOPhone stops reading call and message records. It keeps the two permissions, because revoking them would restart Home; Settings says so.
- **Reliable or nothing.** Each time Home appears it starts with no numbers; a number shows only from a fresh read during which LMNOPhone received no change notice, an old number is hidden if a refresh hasn't finished 1 second after a change notice, and no shown number comes from a read that started more than 13 seconds earlier. Messages counts SMS and MMS in one read, never as a sum of two. The count comes from Android's own call log and text-message store, not from notifications. When the count can't be read (no permission, phone not yet unlocked, the store missing or an error), no number shows. A guessed or saved old number is never shown.
- **Privacy.** LMNOPhone reads only the fields needed to count (row ID, the provider's fixed SMS/MMS transport label, call type, the "new" or "read" flags, message box), and uses only how many rows match. It never reads, logs, stores or sends message text, phone numbers, names or dates for this feature.
- **Coverage is stated, not assumed.** Messages counts cover SMS and MMS in Android's text-message store. Chat (RCS) messages and other apps aren't supported and may be missing (design D7). Settings says so under the switch. Counts work only with the phone's standard Phone and Messages apps (design D4). Third-party messaging apps are never counted.
- Home visuals: no colour, animation, subtitle, new border or divider. Accessibility names include the count ("Phone, 2 missed calls"). Large-text layout and touch targets stay as they are.

## Impact

- Spec: `calm-home-screen` (modified "Plain home screen"; added "Optional missed-call and unread-text counts"; modified "Built-in basic settings").
- Code: `ui` (Home row number, Settings switch, accessibility), host app (count source, permission handling, change observers), manifest (`READ_CALL_LOG`, `READ_SMS`).
- Permissions: two hard-restricted Android permissions. The installer must allowlist them (adb install does by default); the Device Owner then grants them to itself and checks the grant (design D5).
- Not touched: emergency floor and routes, app approval, custody, preview-3 (PLA-289) scope.
