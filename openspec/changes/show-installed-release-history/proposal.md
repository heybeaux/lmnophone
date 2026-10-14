# Show installed release history and truthful runtime data

## Why
The installed preview uses sample identities and request history. About describes unimplemented behavior instead of the changes in the installed release.

## What Changes
- Use saved Family data and neutral wording when identity is absent; never infer a trusted person from Family.
- App requests without a configured transport have an honest setup state and cannot claim a send.
- About shows installed package metadata, shipped release notes, older notes and changes since the previous observed version.
- Record version observation at application startup with one durable preference write; first observation has no historical claim.

## Impact
UI, application mapping, voice recipient defaults, and release-note maintenance. No policy, emergency route, recipient setup, real-send transport or device authorization changes.

## Authorization
Beaux Telegram3664 and the assigned PLA-528 implementation scope authorize this increment. No further proposal approval is pending.
