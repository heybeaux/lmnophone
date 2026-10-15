# Build 61 TalkBack exercise on the spare (PLA-592)

TalkBack 17.0.1 on the Moto spare (API 35), 2026-10-15 00:04–00:19Z. Speech is the spare's
audio output recorded with scrcpy and transcribed locally with Whisper (`tb.sh` + `ex.sh`),
so the `.srt` files are what TalkBack spoke. PinAccess screens are FLAG_SECURE, so there are
no screenshots. TalkBack was driven with `input keyevent TAB` (keyboard focus); screens were
opened with TalkBack off and TalkBack was then turned on, because injected taps on this run
did not activate buttons with TalkBack on (see "Not exercised").

Heard on the spare:
- **Enter PIN** (pin-enter, pin-wrong*): the field reads "Password, edit box, PIN"; after
  typing four digits it reads "Password, four characters, edit box, PIN" and typing spoke
  "Bullet", never a digit. Tab also reached "Forgot PIN? Use recovery code, Button".
- **More** (menu): one stop each, no arrows. Emergency was focused only, never activated.
- **PIN settings menu** (pin-menu): "Add a hidden app, Button", "Apps on this phone, Button",
  "Requests from the person using this phone, Button", "Who gets requests, Button",
  "Change PIN, Button", "New recovery code, Button", "Done, back to the home screen,
  Button", "Voice messages, Button". One stop per row, no arrows.
- **Apps on this phone** (apps): "1 of 4, Maps, Button", "1 of 4, Move Maps down, Button",
  "2 of 4, Camera, Button", "2 of 4, Move Camera up, Button", "Add a hidden app", "Go back".
  Move buttons are read with the app's name and its position.
- **Camera's page** (camera): buttons "Go back", "Remove Camera". The "What Camera can use"
  rows are plain text, not Tab stops, so TalkBack did not read them here; the screen text
  (uiautomator) is "Camera: Allowed", "Location (where the phone is): Not allowed", ... with
  no tick characters.

Not exercised on the spare (host-tested only, PLA-590 androidTests):
- Wrong-PIN message announced once: with TalkBack on, injected single and double taps on
  Continue and Tab/DPAD + Enter did not submit the PIN (Continue was not reached by Tab).
  The PIN was not submitted wrong, so no wrong-try count was used.
- Wait page spoken once while counting down (PLA-591 N1): needs a lockout; not attempted.
- Add a hidden app choices "..., selected"; Requests / Who gets requests field names;
  New recovery code spelled one character at a time (code not rotated).

Whisper artefacts: "LmnaPhone"/"L.M. Nafon" = "LMNOPhone"; "Double" = cut "Double tap to
activate"; repeated lines over long silences.
