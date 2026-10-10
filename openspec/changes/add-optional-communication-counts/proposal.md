**Status:** revision 1 (Lead, PLA-287). Scope approved by Beaux (Telegram 3152, recorded in TEAM-POLICY.md); no separate concept approval is needed. Needs: the data-source spike (C1–C7 below), a cross-model review of this delta, then build.

## Why

Beaux asked for an optional setting that shows two quiet counts on Home: missed calls on Phone and unread messages on Messages. Both must be black and white and calm. Today `calm-home-screen` forbids all badges. This change makes one bounded exception for these two counts only. Every other app count stays excluded.

## What Changes

- **One switch in LMNOPhone Settings**: "Show missed calls and unread texts on Home". It is **off** by default, it is saved, and the user can change it without a PIN. It is a personal display choice. It does not change app approval, trusted-person authority or any lockdown policy.
- **On:** Home shows a plain number with Phone (missed calls) and with Messages (unread texts) when that count is above zero and reliable. Zero shows nothing.
- **Off:** both numbers disappear at once, and LMNOPhone stops reading call and message records.
- **Reliable or nothing.** The count comes from Android's own call log and text-message store, not from notifications. When the count can't be read (no permission, phone not yet unlocked, the store missing or an error), no number shows. A guessed or saved old number is never shown.
- **Privacy.** LMNOPhone reads only the fields needed to count (row ID, call type, the "new" or "read" flags, message box). It never reads, logs, stores or sends message text, phone numbers, names or dates for this feature.
- **Coverage is stated, not assumed.** Messages counts cover SMS and MMS in Android's text-message store. Chat (RCS) messages kept only inside the Messages app are **not** counted unless the spike shows they are in that store. Settings says so under the switch. Third-party messaging apps are never counted.
- Home visuals: no colour, animation, subtitle, new border or divider. Accessibility names include the count ("Phone, 2 missed calls"). Large-text layout and touch targets stay as they are.

## Impact

- Spec: `calm-home-screen` (modified "Plain home screen"; added "Optional missed-call and unread-text counts"; modified "Built-in basic settings").
- Code: `ui` (Home row number, Settings switch, accessibility), host app (count source, permission handling, change observers), manifest (`READ_CALL_LOG`, `READ_SMS`).
- Permissions: two hard-restricted Android permissions. How LMNOPhone gets them is spike question C1.
- Not touched: emergency floor and routes, app approval, custody, preview-3 (PLA-289) scope.
