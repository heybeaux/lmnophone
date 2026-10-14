# Build 59 TalkBack exercise on the spare (PLA-582)

TalkBack 17.0.1 on the Moto spare (API 35), 2026-10-14 21:48–22:32Z. Speech is the spare's
audio output recorded with scrcpy and transcribed locally with Whisper (`tb.sh`), so the
`.srt` files are what TalkBack actually spoke. Whisper repeats lines at the end of long
silent stretches (about.srt, ask.srt); those repeats are transcription artefacts.

How TalkBack was driven: injected swipes and Alt/Meta key combinations do not reach
TalkBack on this device (kb1, kb3: nothing moved). Keyboard focus does: each `input
keyevent TAB` moves focus to the next focusable item and TalkBack speaks it (kb2). So
these runs cover every focusable stop (rows, buttons, switches) but not plain text or
the screen heading, which are not keyboard focusable. Screens were opened with plain
injected taps, which reach the app as normal clicks with TalkBack on.

- kb2 (Home): "Maps, button, in list", "Messages, button", "Phone, button", "More, button".
  No counts spoken: the counts setting is on, no count is shown.
- more: Home, "Emergency 911. Opens the dialer with 911 typed in. Button", Phone settings,
  Ask for an app, PIN settings, Practice voice messages, About this phone. One stop each,
  no arrows. Emergency reads as one button; it was not activated.
- ps (Phone settings): More, Wi-Fi, Bluetooth, Brightness, Sound, Text size, Battery, each
  "button"; then "On, Show missed calls and unread texts on Home, switch". No arrows.
  The heading was not reachable with Tab.
- about: "Older versions, button", "OK, button".
- ask (Ask for an app): "Go back, button", "OK, button".
- bt (Bluetooth > Pair a new device): "Looking for nearby devices" spoken once as it
  appeared. Screenshot not kept: it lists nearby third-party device names.
- ps-2.0-bottom.png: Phone settings at text size 2.0, scrolled to the end; last text ends
  at y=2220, above the nav bar (2280). Font put back to 1.0.
