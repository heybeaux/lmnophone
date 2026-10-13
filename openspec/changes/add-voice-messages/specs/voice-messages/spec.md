## ADDED Requirements

### Requirement: Family list managed by the trusted person
The trusted person SHALL be able to turn voice messages on and off, and add, change, reorder and remove family people (name, spoken name, phone number, picture or letter) in trusted-person mode only. A voice message SHALL only be sent to a person on this list. Sending SHALL NOT need any per-message approval, and the trusted person SHALL NOT be able to see, list or play sent, received or unsent messages.

#### Scenario: Sending to a listed person needs no approval
- **WHEN** the user records a message for Sam, who is on the family list, and taps Send
- **THEN** it is sent via the chosen transport (see PLA-453) without asking the trusted person

#### Scenario: Removing a person
- **WHEN** the trusted person removes Jo from the list
- **THEN** Jo no longer appears on the Family screen and any unsent message for Jo is erased

### Requirement: Usable without reading
Every step of choosing a person, recording, checking, sending and hearing a reply SHALL be possible without reading, spelling or typing. Each person SHALL be shown by picture (or letter) and name, and each screen SHALL speak a short prompt when it opens. Every action symbol SHALL be paired with its word. Buttons on these screens SHALL be at least 72 dp tall, and buttons with different actions SHALL NOT be placed side by side.

#### Scenario: Hearing who the message is for
- **WHEN** the user taps Sam's picture on the Family screen
- **THEN** Sam's screen opens and the phone says "Sam. Tap Record to talk to Sam."

#### Scenario: Repeating the prompt
- **WHEN** the user taps the person's picture on any voice-message screen
- **THEN** the phone says that screen's prompt or the person's name again

### Requirement: One recipient per message
A message SHALL be for exactly one family person, chosen before recording, and that person's picture and name SHALL be shown on every screen until the message is sent or deleted. The recipient SHALL NOT change except by going back to the Family screen, which keeps the recording as that person's unsent message.

#### Scenario: Going back keeps the recipient
- **WHEN** the user has recorded a message for Sam and taps Back, then taps Jo
- **THEN** Jo's screen opens with no recording, and Sam's message stays unsent for Sam

### Requirement: Recording is explicit and visible
Recording SHALL start only after the user taps Record, a spoken prompt and an audible start tone; the microphone SHALL open after the tone. While recording, the screen SHALL show a Recording band and a sound level bar. LMNOPhone SHALL NOT use the microphone at any other time or in the background.

#### Scenario: Start cue
- **WHEN** the user taps Record
- **THEN** the phone says "Talk to Sam after the beep.", plays the start tone, and only then starts recording

#### Scenario: No silent microphone
- **WHEN** no Recording screen is showing
- **THEN** LMNOPhone holds no open microphone

### Requirement: Recording stops on interruption and never sends
Recording SHALL stop on Stop, Back, Home, an incoming or outgoing call, the screen turning off, the phone locking, another app opening, or the longest length (proposed 2 minutes, confirmed for the chosen transport). A recording of at least 1 second SHALL be kept as an unsent message; a shorter one SHALL be dropped. No recording SHALL be sent except by the user tapping Send or Try again.

#### Scenario: Call during recording
- **WHEN** a call arrives while the user is recording for Sam
- **THEN** recording stops at once, the call screen shows as usual, and the recording is kept unsent for Sam

#### Scenario: Longest length
- **WHEN** a recording reaches the longest length
- **THEN** it stops, plays the stop tone and shows the review screen, and nothing is sent

#### Scenario: Leaving the app
- **WHEN** the user presses Home while recording
- **THEN** recording stops and the message is kept unsent, not sent

### Requirement: Review before sending
After recording stops, the user SHALL be able to listen, record again, delete (after a spoken confirmation) or send. Record again SHALL replace the earlier recording only when the new one is stopped and at least 1 second long.

#### Scenario: Record again is interrupted
- **WHEN** the user taps Record again and a call arrives half a second later
- **THEN** the earlier recording is kept

### Requirement: Truthful send feedback
After Send, the phone SHALL show and say "Sending to {person}", then "Sent to {person}" only when the chosen transport (see PLA-453) reports the message was accepted, or "Not sent" when it reports failure, each with a distinct tone. It SHALL NOT claim delivery or that the person heard it unless the transport reports that. With no phone signal, it SHALL say so and keep the message. A failed or unsent message SHALL be kept until the user taps Try again; LMNOPhone SHALL NOT resend by itself.

#### Scenario: Failed send
- **WHEN** the transport reports that the message to Sam failed
- **THEN** the phone shows "Not sent to Sam", says "The message didn't send. It's still on the phone. Tap Try again.", and keeps the message

#### Scenario: No signal
- **WHEN** the user taps Send with no phone signal
- **THEN** the phone shows and says "No phone signal", keeps the message, and does not send it when signal returns

### Requirement: Unsent message retention
LMNOPhone SHALL keep at most one unsent message per family person, in its own private storage, never backed up or sent elsewhere. It SHALL erase an unsent message when it is sent, deleted, replaced, its person is removed, voice messages are turned off, or 7 days after it was recorded. After a successful send it SHALL erase its own copy. About this phone SHALL state this rule.

#### Scenario: Returning to an unsent message
- **WHEN** the user taps Sam and an unsent message for Sam exists
- **THEN** the phone shows "Your message to Sam isn't sent yet" and offers Listen, Record again, Delete and Send

#### Scenario: Expiry
- **WHEN** an unsent message is 7 days old
- **THEN** it is erased

### Requirement: Microphone unavailable
When the microphone is turned off for the phone, or LMNOPhone may not use it, the screen SHALL say so in words and speech, say that calls and texts still work, and name the trusted person to ask. LMNOPhone SHALL NOT show Android's microphone permission request to the user; only the trusted-person step grants it.

#### Scenario: Permission lost
- **WHEN** the user taps Record and LMNOPhone isn't allowed to use the microphone
- **THEN** nothing records and the phone shows and says "Voice messages need Sam"

### Requirement: Hearing family replies
If the chosen transport (see PLA-453) lets LMNOPhone read voice replies, a reply from a family person SHALL appear as "New message" with the Play symbol on that person's card, and the user SHALL be able to play it, play it again and reply with Record. Replies from anyone not on the list SHALL NOT appear on these screens.

#### Scenario: Play and reply
- **WHEN** Sam has sent a voice message and the user opens Sam's screen
- **THEN** the phone says "Sam sent you a message", Listen plays it, Play again repeats it, and Record starts a reply to Sam

### Requirement: Narrow picture and symbol exception
Pictures and symbols SHALL appear only on the voice-message screens and on the single Family row on the home screen. They SHALL be black, white and grey, and every symbol SHALL be paired with its word. All other screens SHALL keep the text-only look.

#### Scenario: Home stays text only
- **WHEN** voice messages are on
- **THEN** the Family row shows its symbol and every other home row shows text only

### Requirement: Practice build labelled
A build that cannot send SHALL show "Practice – not sent" on every voice-message screen, say "This is practice. Messages are not sent." once on the Family screen, and SHALL NOT show a Send button.

#### Scenario: Practice review screen
- **WHEN** the user stops a recording in the practice build
- **THEN** the review screen offers Listen, Record again and Delete, and no Send
