## Slice 2b — authorized family and Messages route

Scope: PLA-500, under the parent A+B and delivery-first decisions. Six family people supersedes the older UX draft's eight. No photo import, real MMS sending, reply ingestion or reordering in this slice.

- [x] Private credential-encrypted, no-backup family store; immediate add/edit/remove; remove old drafts before changing a recipient number or removing a person.
- [x] Existing PIN menu hosts family editing, microphone enable, confirmation, and off/erasure. Pause clears editor state and authority.
- [x] Production Home mapper sets Family only for enabled, populated lists. More practice stays available only without Family.
- [x] Family picture/voice list, spoken names and restored places use live family identities. Sending remains absent with practice labels.
- [x] Explicit Google Messages SENDTO route resolves the current recipient by opaque id; no message content or automatic send.
- [ ] Opposite-model review and any focused delta corrections.
- [ ] Lead integration with slice 2a and sole-driver spare installation (outside this issue's device authority).

## Slice 2c — hearing family picture-message replies

Scope: PLA-527, requirement "Hearing family replies". Picture-message (MMS) voice replies only; chat (RCS) replies stay in Messages.

- [x] Read the MMS inbox with READ_SMS only; family senders only (normalized number match); audio parts only; latest 5, oldest first.
- [x] New message with the Play symbol on the family card (V1) and spoken "{person} sent you a message."; Listen / Stop listening / Play again on the person page (V2, V12a).
- [x] Played-state in private no-backup storage keyed by a hashed message id; erased with the send record and drafts on remove, turn-off or a number change.
- [ ] Opposite-model review and any focused delta corrections.
- [ ] Lead-routed spare installation (outside this issue's device authority).
