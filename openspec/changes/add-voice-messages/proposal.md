**Status:** draft (UX, [PLA-456](/PLA/issues/PLA-456)). Scope authorized by Beaux (Telegram 3474, [PLA-453](/PLA/issues/PLA-453)); under the delivery-first policy (Telegram 3215) this change is not gated by a separate plan or architecture review. The transport is open: requirements that depend on it say "via the chosen transport (see PLA-453)".

## Why

Beaux asked for voice messages so that a person who can't read or write can talk with their family. Today LMNOPhone is text only, and sending anything needs reading and typing in the Messages app. This change adds a way to record, check and send a short voice message to a family person chosen by picture, and to hear their replies, with nothing to read, spell or type.

## What Changes

- **New capability `voice-messages`**: a Family list managed by the trusted person; choosing a person by picture and spoken name; explicit Record and Stop with a start tone before the microphone opens; listen, record again, delete; a deliberate Send; truthful sending, sent and not-sent feedback, seen and heard.
- **Microphone only while recording.** It stops on Stop, Back, Home, a call, screen off, lock, another app or the longest length. Nothing is ever sent automatically.
- **Unsent messages** are kept on the phone, one per family person, and erased after 7 days (proposed), or sooner if sent, deleted or replaced.
- **No per-message approval and no monitoring.** The trusted person manages the list only, and can't see or hear messages.
- **A narrow picture and symbol exception** to the text-only rule, for these screens and one Home row ("Family") only. Greyscale, every symbol paired with a word and spoken text. Rationale: `docs/ux/README.md`, "Pictures and symbols in voice messages only".
- **A practice build** (local prototype, PLA-457) that can't send is labelled "Practice – not sent" on every screen and shows no Send button.

## Impact

- Specs: new `voice-messages`. `calm-home-screen` "Plain home screen" gains one bounded exception (the Family row's symbol); the Lead folds that into the requirement when this change is merged with `add-optional-communication-counts`.
- Copy and look: `docs/ux/flows-voice-messages.md`, `docs/ux/README.md`, `docs/ux/design-tokens.md`, mockups in `docs/ux/voice-messages/`.
- Code (later): `ui` (screens, symbols, tones), host app (recorder lifecycle, text-to-speech, storage with expiry, transport), manifest (`RECORD_AUDIO`, granted by the Device Owner from the trusted-person step).
- Not touched: emergency floor and routes, app approval, Messages and Phone staying on Home.
