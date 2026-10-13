# Voice messages: flows for a person who doesn't read or write

Owner: Product & UX Designer. Task: [PLA-456](/PLA/issues/PLA-456), part of [PLA-453](/PLA/issues/PLA-453) (Beaux,
Telegram 3474). Spec: `openspec/changes/add-voice-messages/`. Mockups: [voice-messages/](voice-messages/).
Status: **draft copy for the local prototype ([PLA-457](/PLA/issues/PLA-457)) and the Lead.** Sending depends on the
transport the Lead chooses ([PLA-458](/PLA/issues/PLA-458)); every "sent" below means what that transport can truly
report, nothing more.
Addendum [PLA-501](/PLA/issues/PLA-501): Beaux chose **A + B**. LMNOPhone's own screens send voice messages as
picture messages (MMS, A), and chat (RCS) stays on in Messages (B). Changes: **Messages with {person}** on V2, V12
split by reply kind, and the "Sent" / replies line for the trusted person (VT1, VT4).

Words, placeholders and the never-say list are in [README.md](README.md). Screen IDs (V1, V2…) never appear on
screen. Each screen gives:
- **On screen**: the exact words, as in the other flow files. Engineers copy them word for word.
- **Spoken**: the exact words LMNOPhone says aloud (text-to-speech), and when.
- **Sounds**: the short tones. These are not speech and carry no words.
- **Symbols**: from the [picture and symbol exception](README.md#pictures-and-symbols-in-voice-messages-only). Every
  symbol always sits next to its word.

Sample people: **Alex** uses the phone (`{user}`), **Sam** is the trusted person (`{tp}`), and the family list holds
**Sam** and **Jo**. Never a real person's name, face or number, in docs, mockups, tests or demo data.
`{person}` is the family person this message is for.

---

## Who this is for, and what changed

The person using the phone may not read or write. Everything they need to do here works by **looking at
pictures, hearing words, and tapping large buttons**. Nothing asks them to read, spell or type. The words on screen
are still there, for people who can read and for the trusted person.

The journey Beaux proposed, kept with four changes:

1. Pick a family person by their **picture** → 2. hear their **name** → 3. tap **Record** → 4. tap **Stop** →
5. **Listen**, **Record again** or **Delete** → 6. tap **Send** → 7. hear and see **Sending**, **Sent** or **Not sent**.
Plus: **hear a family person's message** and play it again.

Changes from the proposal, and why:
- **A tone before recording, and a different tone when it stops.** A person who doesn't read needs to *hear* that the
  phone is listening. The start tone plays *before* the microphone opens, so it isn't in the message.
- **Leaving never loses a recording and never sends it.** Back, Home, a call, the screen turning off: recording
  stops and the message is **kept, not sent** (a "message you didn't send", V9). Only **Delete** throws it away.
- **Tapping the picture says the words again.** Every screen speaks once when it opens. Tapping the person's picture
  (or the top of the screen) repeats it. No extra "help" button.
- **No confirmation screen before Send.** The person already tapped Stop and saw the review screen; one more
  question adds a choice without adding safety. The Send button is the one black button, far from Delete, and it
  says the person's name.

## Rules that apply to every screen here

- **One person per message.** The person is chosen on V1 and shown (picture and name) on every screen until the
  message is sent or deleted. There is no way to change who a message is for except going **Back** to V1, which
  keeps the recording as that person's unsent message.
- **The microphone is on only while the Recording screen (V3) is showing.** Never before the start tone, never in
  the background, never on any other screen. Android's own microphone dot shows while it runs.
- **Never sent automatically.** Not when Stop is tapped, not when the person lets go of a button, not on leaving, not
  when the longest length is reached, not when signal comes back. Only a tap on **Send** (or **Try again**) sends.
- **Speech:** spoken through the phone's media sound, at the phone's media volume, in Android's text-to-speech
  voice. Each screen speaks once when it opens. Speech never plays during a phone call, never while recording, and
  stops at once if the person taps anything. If the phone is in silent mode, LMNOPhone still speaks on these screens
  (it's the only way the person knows what is happening); the side buttons change the volume.
- **Sizes:** buttons at least 72 dp tall here (larger than the usual 56 dp), person pictures at least 120 dp. Text
  follows the phone's text size, as everywhere. Two buttons that do different things are never side by side; they
  are stacked, with space between.
- **No badges, no colour, no countdown.** Black, white and grey only, Lexend only.
- **No one else listens.** The trusted person can't hear, list or count the messages. LMNOPhone sends nothing to
  anyone except the message itself, to the person it was recorded for.

## Symbols used (exact list)

| Symbol | Drawn as | Always paired with |
|---|---|---|
| Record | a filled black circle | "Record", "Record again" |
| Stop | a filled black square | "Stop" |
| Play | a filled black triangle pointing right | "Listen", "Play again", "Listen to {person}" |
| Send | an arrow pointing right, inside a circle | "Send to {person}", "Try again" |
| Delete | a cross (✕) | "Delete" |
| Back | `‹` (already in use across LMNOPhone) | "Back" |
| Family (Home row only) | two simple head-and-shoulders outlines | "Family" |
| Messages (V2 only) | a speech-bubble outline | "Messages with {person}" |

Person pictures: a photo chosen by the trusted person, or, with no photo, the person's **first letter** in a large
black circle (white letter). Pictures are shown in **black and white** (greyscale) in LMNOPhone, like everything else.

---

## V0. The way in: "Family" on the home screen

**On screen:** a home-screen row, directly **above Messages**, reading **Family**, with the Family symbol before the
word. Same `app` text size as the other rows.

**Spoken:** nothing on Home. Opening V1 speaks.

**Notes**
- Only shown when the trusted person has turned voice messages on (VT1) and added at least one person.
- It always sits in the same place, directly above Messages, so the person learns it by place as well as by picture.
- This is the **one symbol on the home screen**. Every other home row stays text only.
- The row's accessibility name is "Family, voice messages".

---

## V1. Choose a person

**On screen**
- Title: **Who do you want to talk to?**
- A list of large cards, one per family person, in the order the trusted person set. Each card: the person's picture,
  and their name under it. If that person has sent a message not yet played, the card also shows the Play symbol and
  **New message**. If there is an unsent message for that person, the card shows **Not sent yet**.
- Bottom: **‹ Back** (text link, goes Home).

**Spoken** (when the screen opens): **"Who do you want to talk to?"**
If any card shows New message, add, once per person: **"Sam sent you a message."** (one sentence per person, in card
order).

**Tapping a card:** opens V2 for that person. It doesn't record or send anything.

**Notes**
- One column of cards, each the full width, picture at least 120 dp. At 200% text the name wraps under the picture;
  cards grow taller; the list scrolls vertically.
- With exactly one family person, V1 is still shown (so the person always hears who it's for), with that one card.
- Card accessibility name: "Sam" or "Sam, new message" or "Sam, message not sent yet".

---

## V2. A person's screen

**On screen**
- The person's picture, large, at the top, and their name under it in `title`.
- Directly under the name, always: a large outline button **Messages with Sam**, with the Messages symbol before
  the words.
- If Sam has sent a message not yet played: a large outline button **▶ Listen to Sam**, under Messages with Sam and
  above the black button (see V12).
- Black button (pinned to the bottom): **● Record**
- **‹ Back** (text link above it).

Order, top to bottom: picture, name, **Messages with Sam**, (**▶ Listen to Sam**), **‹ Back**, **● Record**.
Messages with Sam is always the button nearest the name, and never next to Record, so it is learned by place and
can't be hit when reaching for Record. At least 24 dp between buttons, as everywhere here.

**Spoken** (when the screen opens): **"Sam. Tap Record to talk to Sam, or Messages to see what Sam sent."**
If Sam has a new message: **"Sam. Sam sent you a message. Tap Listen to hear it, or Record to talk to Sam."**

**Tapping the picture or name:** says **"Sam."** again.

**Tapping ● Record:** goes to V3. If the microphone can't be used, goes to V10 instead and never starts.

**Tapping Messages with Sam:** says **"Opening Messages."**, then opens the phone's Messages app straight at the
conversation with Sam's number (the number from VT2). Back in Messages, or Family on Home, returns here, and V2
speaks again as it opens. If Messages can't open, Alex stays on V2 and sees **Messages didn't open.**, spoken
**"Messages didn't open. Ask Sam for help."** (`{tp}`).

**Notes**
- If there is already an unsent message for Sam, this screen is not shown: V9 shows instead. V9 has no Messages
  button (it is about the unsent message); Messages on Home still works.
- **Messages with Sam is always shown**, whether or not anything new is there. LMNOPhone can't see chat (RCS)
  messages, so it can't know. Nothing in LMNOPhone ever says "no new messages".
- Accessibility name: "Messages with Sam, opens Messages".
- Messages is Google's app, not LMNOPhone's: LMNOPhone doesn't speak there, and the words there are Google's. Alex
  plays a voice message there with its own play button. The trusted person shows this once (VT4).
- It opens only the Messages app that the Home **Messages** row already opens. It adds no new app and no new way
  out of LMNOPhone.

---

## V3. Recording

**What happens, in order, after ● Record is tapped**
1. Spoken: **"Talk to Sam after the beep."**
2. Sound: **start tone** (one short rising tone) and one short vibration.
3. The microphone opens. The screen below appears at the same moment.

**On screen**
- A black band across the top, under the status bar: **● Recording** (paper on ink, like the "For Sam" band).
- The person's picture (smaller, about 96 dp) and **To Sam**.
- A sound bar that moves with the person's voice, so they can see the phone hears them. It's a plain black bar in a
  grey track. With Android's "Remove animations" on, it doesn't move; the band and tone still show recording.
- A plain line that fills slowly from left to right as the message gets longer (no numbers, no countdown). When it
  is full, the message is at its longest (V4 note).
- Black button: **■ Stop**

**Spoken:** nothing while recording (the speaker would be recorded).

**Tapping ■ Stop:** sound: **stop tone** (one short falling tone). Goes to V4.

**Recording stops, and the message is kept, not sent, when:** Back is pressed; Home is pressed; a call comes in or
the person makes a call; the screen turns off; another app opens; the phone is locked; or the longest length is
reached. In every case: the stop tone plays (unless a call is ringing), and next time the person sees V9 for Sam.
Exception: the longest length goes straight to V4 with its own words (see below).

**Too short:** if the person taps Stop less than 1 second after the tone, nothing is kept. Spoken: **"That was too
short. Tap Record to try again."** Back to V2 (or V4 if there was an earlier recording for Sam, which stays).

**Longest length** (proposed: **2 minutes**, to fit phone-network picture messages; the Lead confirms the limit for
the chosen transport): recording stops by itself with the stop tone and goes to V4. Spoken on V4 instead of the
usual words: **"That's as long as a message can be. Listen, record again, or send to Sam."**

---

## V4. Listen, record again, delete or send

**On screen**
- The person's picture and **To Sam**.
- Large outline button: **▶ Listen** (while playing it reads **■ Stop listening**).
- Outline button: **● Record again**
- Outline button: **✕ Delete**
- Black button (pinned to the bottom): **➤ Send to Sam**

The three outline buttons are stacked, full width, with a 16 dp gap. Delete is the lowest outline button, then a 32 dp
gap, then Send, so the two are never next to each other without a gap.

**Spoken** (when the screen opens after Stop): **"Listen, record again, or send to Sam."**

**▶ Listen:** plays the recording from the start through the phone's speaker (or earpiece if the phone is held to
the ear, as Android decides). When it ends, the button goes back to ▶ Listen. Speech doesn't repeat.

**● Record again:** same as V3. The new recording **replaces this one only when it is stopped** and is at least 1
second long. If it is too short or interrupted in its first second, this one stays.

**✕ Delete:** goes to V4a.

**➤ Send to Sam:** goes to V5. In the practice build there is no Send button (see V14).

### V4a. Delete this message?

**On screen**
- Title: **Delete your message to Sam?**
- Outline button: **Keep it** (goes back to V4)
- Black button: **✕ Delete**

**Spoken:** **"Delete your message to Sam? Tap Delete, or Keep it."**

**After ✕ Delete:** the recording is erased from the phone. Spoken: **"Deleted."** Back to V2.

---

## V5. Sending

**On screen**
- Picture and **To Sam**.
- Title: **Sending to Sam**
- No buttons except **‹ Back** (leaving doesn't stop the send; the result shows next time on V1/V2).

**Spoken:** **"Sending to Sam."**

**Notes**
- Shown from the tap until the transport reports a result. Nothing on this screen moves except, at most, a slow fade.
- If the phone has no phone signal at the moment Send is tapped, V5 is skipped and V8 shows.

---

## V6. Sent

**On screen**
- Picture and **To Sam**.
- Title: **Sent to Sam ✓**
- Black button: **Done** (goes to V1).

**Sounds:** **sent tone** (two short rising notes).
**Spoken:** **"Sent to Sam."**

**Notes**
- "Sent" means only what the transport can truly report: the phone network accepted the message (PLA-453). It
  **never** means "Sam heard it" or "Sam got it", unless the transport reports delivery and the Lead approves copy for
  it. So the copy never says "delivered", "received" or "heard".
- After 10 seconds with no tap, the screen goes back to V1 by itself. (This is not a send; the send is already done.)
- LMNOPhone erases its own copy of the recording once it's sent (see [What is kept](#what-is-kept-and-for-how-long)).

---

## V7. Not sent

**On screen**
- Picture and **To Sam**.
- Title: **Not sent to Sam**
- Body: **Your message is still on this phone.**
- Black button: **➤ Try again**
- Outline button above it: **▶ Listen**
- Text link: **‹ Back** (keeps the message as unsent; V9 next time)

**Sounds:** **not-sent tone** (two short falling notes). **Spoken:** **"The message didn't send. It's still on the
phone. Tap Try again."**

**➤ Try again:** goes to V5. LMNOPhone never tries again by itself.

**If it fails again on the second try**, the body adds a second sentence for the trusted person: **If this keeps
happening, ask Sam to look at the phone.** Spoken adds: **"If this keeps happening, ask Sam for help."** (`{tp}` is
the trusted person, who may or may not be the person the message was for.)

---

## V8. No phone signal

Shown instead of V5 when Send is tapped with no mobile service, airplane mode on, or (if the chosen transport needs
it) no mobile data.

**On screen**
- Picture and **To Sam**.
- Title: **No phone signal**
- Body: **Your message is kept on this phone. Try again when there is signal.**
- Black button: **➤ Try again**
- Text link: **‹ Back**

**Spoken:** **"There's no phone signal. Your message is kept. Try again later."**

**Notes**
- Recording and listening still work with no signal. Only sending needs it.
- If the transport needs mobile data (not just signal), the same screen and words are used. The person can't fix
  either; the words "phone signal" cover both.
- The message is kept as an unsent message (V9). It is **not** sent when signal comes back (see Question 2).

---

## V9. A message you didn't send

Shown instead of V2 when the person taps a family person who has an unsent message: after an interruption, after
Back, after No signal or Not sent.

**On screen**
- Picture and **To Sam**.
- Title: **Your message to Sam isn't sent yet**
- The same buttons as V4: **▶ Listen**, **● Record again**, **✕ Delete**, and the black **➤ Send to Sam**.

**Spoken:** **"Your message to Sam isn't sent yet. Listen, send, or delete."**

**Notes**
- At most **one** unsent message per family person. Record again replaces it (V4 rule).
- After **7 days** it is erased (see below). There's no screen about this; it's in About this phone.

---

## V10. The microphone can't be used

Two cases. In both, nothing records, and the person can't fix it alone.

### V10a. Microphone turned off for the whole phone

Android's microphone switch (in quick settings) is off. Android shows its own question first ("Unblock device
microphone?"). If the person doesn't unblock it, or can't read it, LMNOPhone shows:

**On screen**
- Title: **The microphone is turned off**
- Body: **Ask Sam to turn it on. Calls and texts still work.**
- Black button: **‹ Back**

**Spoken:** **"The microphone is turned off. Ask Sam for help."**

**What Sam does:** swipe down from the top of the screen and tap **Microphone access** to turn it on. (Android's own
words; the setup guide shows it.)

### V10b. LMNOPhone isn't allowed to use the microphone

Voice messages are on, but Android says LMNOPhone may not use the microphone (the grant was lost or refused).

**On screen**
- Title: **Voice messages need Sam**
- Body: **Ask Sam to set up voice messages again. Calls and texts still work.**
- Black button: **‹ Back**

**Spoken:** **"Voice messages need Sam. Ask Sam for help."**

**What Sam does:** More → **For Sam** → PIN → **Voice messages** → **Turn on voice messages**. LMNOPhone gives itself
the microphone again and checks it worked (VT1). If it still can't, VT1 says so in Sam's words.

**Notes**
- LMNOPhone never shows Android's "Allow LMNOPhone to record audio?" question to the person using the phone. The
  microphone is allowed only from VT1, by the trusted person.
- If the microphone is only busy (another app holds it) and recording can't start, V10 isn't shown, because busy is
  short-lived. Spoken: **"The microphone is busy. Try again in a moment."** and the person stays on V2.

---

## V11. Interrupted

A call arrives (or the person starts a call) while recording or listening.

- **Recording:** stops at once. No tone (the ringtone is playing). What was recorded is kept as unsent (V9) if it
  is at least 1 second long; shorter is dropped.
- **Listening:** stops at once.
- Android's call screen takes over as usual. LMNOPhone never delays or hides a call.
- **After the call**, LMNOPhone does **not** reopen by itself. When the person next opens Family and taps Sam, V9
  shows with its usual words. If the recording was shorter than 1 second, V2 shows as usual.

The same applies when the screen turns off, the phone locks, or the person presses Home or opens another app while
recording.

---

## V12. A message from a family person

Replies come in two kinds (PLA-501, A + B). Which kind arrives is chosen by Sam's phone and the network, not by
Alex or LMNOPhone:

| Reply kind | Usually from | Where Alex hears it |
|---|---|---|
| Picture message (MMS) with a voice recording | Phones where chat is off or can't be used at that moment | **In LMNOPhone**: V1 shows **New message**, V2 shows **▶ Listen to Sam** (V12a) |
| Chat message (RCS) | Phones with chat (RCS) on, which most Android phones have: most family replies | **Only in Messages**: V2 → **Messages with Sam**, then Messages' own play button |

**When either arrives:** the Messages app's usual notification sound plays (LMNOPhone adds no sound of its own). The
Family row on Home doesn't change (no badges).
- **Picture message:** on V1, Sam's card shows the Play symbol and **New message**; V12a below.
- **Chat message:** nothing in LMNOPhone changes, because LMNOPhone can't see it. Alex finds it with **Messages with
  Sam** on V2 ("…or Messages to see what Sam sent."). LMNOPhone never says there is nothing new.

What Alex is told stays simple and true: Listen plays what LMNOPhone has; Messages with Sam shows everything Sam
sent. Alex isn't asked to know which kind a reply is. The longer explanation is for the trusted person (VT4).

### V12a. Listen to Sam

On V2 for Sam, the outline button **▶ Listen to Sam** sits above **● Record**.

**On screen while playing:** the button reads **■ Stop listening**. Sam's picture stays at the top.

**Spoken before it plays:** **"Sam's message."** Then the message plays.

**When it ends:** spoken **"Tap Play again to hear it again, or Record to talk to Sam."** The button reads **▶ Play
again**. **● Record** stays below it, so replying is one tap.

**Notes**
- With several unplayed messages from Sam, Listen plays them oldest first, one after another, with "Sam's next
  message." between. Play again replays the last one heard.
- Only the latest 5 messages from Sam can be played here. Older ones stay in the Messages app.
- A voice message from someone **not** on the family list never shows here. It stays in the Messages app.
- A chat (RCS) reply never shows here, even from Sam. It is only in Messages (Messages with Sam).
- Picture messages without sound (a photo) aren't shown here in this version.

---

## V13. Practice build (local prototype, PLA-457)

The first build can't send yet. It must not look like it sends.

**On screen, on every voice-message screen:** a black band at the very top: **Practice – not sent** (en dash, as in
"Phone software changed – review pending"). On V3 the band reads **● Recording – practice**.

**V4 in practice:** no Send button. The black button is **▶ Listen**; the outline buttons are **● Record again** and
**✕ Delete**. V5–V8 are never shown. Spoken on V4 in practice: **"Listen, or record again."** (V9 in practice:
**"Your message to Sam isn't sent yet. Listen, or delete."**)

**Spoken, once, when V1 opens:** **"This is practice. Messages are not sent."** (before "Who do you want to talk to?").

**Family people in practice:** sample people only (Sam, Jo), with synthetic pictures or letters. No real numbers.

**Practice recordings** follow the same rule as unsent messages: kept for 7 days, then erased, and never leave the
phone.

---

## What is kept, and for how long

**Proposed retention** (for the Lead and Beaux; Question 1):

| What | Where | Kept until |
|---|---|---|
| An unsent message (after Stop, Back, an interruption, No signal or Not sent) | LMNOPhone's own private storage on the phone, one per family person | Sent, deleted, replaced by Record again, or **7 days** after it was recorded, whichever is first. Then erased. |
| A sent message | Wherever the chosen transport keeps sent messages (for picture messages, the phone's own message store, like any text) | As long as that store keeps it. LMNOPhone erases **its own** copy as soon as the send succeeds. |
| A message from a family person | The Messages app (picture messages also in the phone's message store, which LMNOPhone reads to play them; chat messages only in Messages) | As the Messages app keeps it. LMNOPhone keeps no copy. |
| A recording shorter than 1 second | Nowhere | Dropped at once. |

- Nothing is backed up, uploaded or sent anywhere else. No one can listen to an unsent message except on this phone,
  on the Family screens.
- The trusted person can't see or play unsent, sent or received messages from trusted-person mode.
- **Why 7 days:** long enough for an interrupted message to survive a weekend away or a few days without signal;
  short enough that a forgotten recording doesn't sit on the phone for months. A recording can hold private things the
  person didn't mean to keep.
- Turning voice messages off (VT1) erases all unsent messages at once, after the trusted person confirms.

**About this phone (U8) gains one section**, only when voice messages are on:
- Heading: **Voice messages**
- Body: **Voice messages go only to the family people Sam added. A message you don't send is kept on this phone for 7
  days, then erased. No one else can hear it.**

---

## Trusted-person steps (brief)

These are in trusted-person mode ("For Sam" band, PIN first), with the phone in Sam's hands, as everywhere else.
The person using the phone can see everything that happens.

### VT1. Voice messages (For Sam menu row)

- Row: **Voice messages**, second line **Off** or **On – 2 family people**.
- Screen title: **Voice messages for Alex**
- Body (off): **Alex can send and hear short voice messages, using pictures, without reading. Only people you add
  here can get them. You don't approve each message.**
- Black button: **Turn on voice messages**. LMNOPhone allows itself the microphone and checks it. If the check
  fails: **LMNOPhone couldn't turn on the microphone. Try again, or ask {helper}.**
- When on: the list of family people (picture and name, `›`), then **Add a person**, then text link **Turn off voice
  messages** (confirm: **Turn off voice messages? Messages Alex hasn't sent will be erased.** / **Turn off**).
- When on, under Add a person, in `body`: **"Sent" means the phone company accepted the message, not that it was
  heard. Replies may show only in Messages; Alex opens them with "Messages with" on each person's screen.** Then the
  text link **How replies work ›** (VT4).

### VT2. Add or change a family person

Fields, one per screen:
1. **Name** (typed by Sam): **Their name, as Alex says it**. Below: **▶ Hear it** plays the name in the phone's voice.
   If it sounds wrong, Sam can spell it the way it sounds (e.g. "Jo" → "Joe"); the shown name and the spoken name are
   kept separately.
2. **Phone number**: **Their mobile number**. Messages go only to this number.
3. **Picture**: **Take a photo**, **Choose a photo** (from this phone's photos, during this step only), or **Use a
   letter** (the default). The picture is shown in black and white.
4. Confirm: **Add Sam to Alex's family list?** / **Add**. Order: drag to reorder on VT1 (the order Alex sees).

- At most **8** family people (more makes V1 hard to scan). The list is the only check: once a person is on it, Alex
  sends to them without asking anyone.
- Removing a person erases any unsent message for them.
- Adding or removing a person is **not** a waiting change: it can only narrow or widen who Alex talks to by voice,
  and Sam is there with the phone. (Lead: confirm this fits trusted-person-controls.)

### VT4. How replies work

Reached from VT1 (text link). Read only; nothing to set.
- Title: **How replies work**
- Body:
  **Alex's messages go as picture messages, through the phone company. "Sent" means the phone company accepted
  it. It doesn't mean it arrived or was heard.**

  **Family can reply in two ways. A voice reply sent as a picture message plays on Alex's Family screens. A reply
  sent as a chat message (RCS), which Android phones with chat turned on in Messages usually send, shows only in the
  Messages app. LMNOPhone can't see or play those.**

  **To hear them, Alex taps "Messages with" on that person's screen. Messages opens at that person's conversation,
  and Alex taps the play button there. Show Alex this once.**
- Text link: **‹ Back** (to VT1).

### VT3. Text Sam gets

None. Voice messages add no LMNOPhone text to the trusted person. Sam is not told when Alex sends or gets a message.

---

## Exact strings (for string resources)

Placeholders: `{person}` (family person's shown name), `{person_spoken}` (spoken name, may differ), `{tp}`,
`{user}`, `{helper}`, `{n}`. In spoken strings, use `{person_spoken}`.

| ID | On screen | Spoken |
|---|---|---|
| V0 row | Family | – |
| V1 title | Who do you want to talk to? | Who do you want to talk to? |
| V1 new | New message | {person} sent you a message. |
| V1 unsent | Not sent yet | – |
| V2 | {person} · Messages with {person} · ● Record · ‹ Back | {person}. Tap Record to talk to {person}, or Messages to see what {person} sent. |
| V2 with new | ▶ Listen to {person} | {person}. {person} sent you a message. Tap Listen to hear it, or Record to talk to {person}. |
| V2 picture tap | – | {person}. |
| V2 messages · `voice_open_messages` (new) | Messages with {person} | Opening Messages. (`voice_say_opening_messages`, new) |
| V2 messages, TalkBack · `voice_open_messages_desc` (new) | – | Messages with {person}, opens Messages |
| V2 messages failed · `voice_messages_failed` (new) | Messages didn't open. | Messages didn't open. Ask {tp} for help. (`voice_say_messages_failed`, new) |
| V3 before | – | Talk to {person} after the beep. |
| V3 band | ● Recording | – |
| V3 to | To {person} | – |
| V3 button | ■ Stop | – |
| V3 too short | – | That was too short. Tap Record to try again. |
| V3 busy | – | The microphone is busy. Try again in a moment. |
| V4 | ▶ Listen · ■ Stop listening · ● Record again · ✕ Delete · ➤ Send to {person} | Listen, record again, or send to {person}. |
| V4 longest | – | That's as long as a message can be. Listen, record again, or send to {person}. |
| V4a | Delete your message to {person}? · Keep it · ✕ Delete | Delete your message to {person}? Tap Delete, or Keep it. |
| V4a after | – | Deleted. |
| V5 | Sending to {person} | Sending to {person}. |
| V6 | Sent to {person} ✓ · Done | Sent to {person}. |
| V7 | Not sent to {person} · Your message is still on this phone. · ➤ Try again | The message didn't send. It's still on the phone. Tap Try again. |
| V7 again | If this keeps happening, ask {tp} to look at the phone. | If this keeps happening, ask {tp} for help. |
| V8 | No phone signal · Your message is kept on this phone. Try again when there is signal. · ➤ Try again | There's no phone signal. Your message is kept. Try again later. |
| V9 | Your message to {person} isn't sent yet | Your message to {person} isn't sent yet. Listen, send, or delete. |
| V10a | The microphone is turned off · Ask {tp} to turn it on. Calls and texts still work. | The microphone is turned off. Ask {tp} for help. |
| V10b | Voice messages need {tp} · Ask {tp} to set up voice messages again. Calls and texts still work. | Voice messages need {tp}. Ask {tp} for help. |
| V12a | ▶ Listen to {person} · ■ Stop listening · ▶ Play again | {person}'s message. / {person}'s next message. / Tap Play again to hear it again, or Record to talk to {person}. |
| V13 band | Practice – not sent · ● Recording – practice | This is practice. Messages are not sent. |
| V13 review | (V4 without Send; black button ▶ Listen) | Listen, or record again. / Your message to {person} isn't sent yet. Listen, or delete. |
| V13 More row · `more_voice`, `more_voice_note` | Practice voice messages · Record and listen. Nothing is sent. | – (TalkBack reads the row) |
| V2 hear name · `voice_hear_name` | Hear the name | {person_spoken}. (`voice_say_name`) |
| V2 hear name, TalkBack · `voice_hear_name_action` (new) | – | hear the name (click label: TalkBack says "Double-tap to hear the name") |
| V13 notice · `voice_notice_max` | That's as long as a message can be. | `voice_say_max` (V13 review row: "That's as long as a message can be. Listen, or record again.") |
| V13 notice · `voice_notice_nothing` | That was too short. Try again. | `voice_say_too_short` (V3 too short) |
| V13 notice · `voice_notice_deleted` | Deleted. | `voice_say_deleted` (V4a after) |
| V13 notice · `voice_notice_busy` | The microphone is busy. Try again in a moment. | `voice_say_busy` (V3 busy) |
| V13 notice · `voice_notice_interrupted` | Recording stopped. | – (V11: nothing is spoken; next time V9 speaks) |
| V13 notice · `voice_notice_play` | This message could not play. | This message could not play. Record again, or delete. (`voice_say_play`, new) |
| V13 microphone · `voice_try_again` | ● Try again | – (tapping it is the same as ● Record) |
| V13 V10a short · `voice_mic_off` | The microphone is turned off. Ask for help to turn it on. Calls and texts still work. | The microphone is turned off. Ask {tp} for help. (`voice_say_mic_off`) / no name known: The microphone is turned off. Ask for help. (`voice_say_mic_off_short`, new) |
| V13 V10b short · `voice_mic_not_allowed` | Voice messages aren't set up. Ask for help. Calls and texts still work. | Voice messages need {tp}. Ask {tp} for help. (`voice_say_mic_not_allowed`) / no name known: Voice messages aren't set up. Ask for help. (`voice_say_mic_not_allowed_short`, new) |
| V13 microphone failed · `voice_mic_error` | The microphone didn't work. Try again. | The microphone didn't work. Tap Try again. (`voice_say_mic_error`, new) |
| VT1 replies · `voice_tp_sent_meaning` (new) | "Sent" means the phone company accepted the message, not that it was heard. Replies may show only in Messages; {user} opens them with "Messages with" on each person's screen. · How replies work › | – |
| VT4 · `voice_tp_replies_title`, `voice_tp_replies_body` (new) | How replies work · (three paragraphs, VT4) | – |
| U8 | Voice messages · Voice messages go only to the family people {tp} added. A message you don't send is kept on this phone for 7 days, then erased. No one else can hear it. | – |

The symbols (●, ■, ▶, ➤, ✕, and the Messages speech bubble) in this table stand for the drawn symbols; they are **not** characters in the string.
The string is the word only ("Record"); the screen draws the symbol before it. `‹` and `✓` are characters, as
elsewhere.

With two trusted people, `{tp}` is "Sam or Jo" (README, "Two names").

**Practice-build rows (resource names, preview 39).** Rows keyed to a resource name cover strings the practice
build needs before the full design exists:

- **More row.** Only until the Home "Family" row (V0) exists; then it goes. The person using the phone can't read
  it; the trusted person opens it for them, so it names the feature plainly.
- **Hear the name.** The picture stays the main tap target (V2 picture tap). "Hear the name" is a small link under
  the name for anyone who doesn't know the picture can be tapped; both do the same thing. The picture keeps its
  description `voice_picture` ("Picture of {person}") and gets the click label `voice_hear_name_action`, so TalkBack
  says "Picture of Sam. Double-tap to hear the name." Hide the link from TalkBack if the picture carries the action,
  so the name isn't offered twice.
- **Notices on screen.** Short facts, with no "Tap …" instruction, because the buttons are right below. The spoken
  words keep the instruction. "That was too short. Try again." fits both V2 (Record) and V4 (Record again), where the
  earlier recording stays.
- **Microphone screen.** In practice, the black button is **● Try again** (same as Record), not V10's ‹ Back,
  because ‹ Back is already at the top. If the microphone still can't be used, the same screen and words come back.
- **Short V10a/V10b.** The words on screen never name the trusted person in practice, which keeps them short at
  large text sizes. The spoken words name them (`{tp}`) when the phone knows the name, and use the short form when
  it doesn't. V10b's on-screen words say "aren't set up" rather than "not allowed": that is what the person can act
  on.
- **"(new)"** marks a resource the build doesn't have yet. Until it exists, the build keeps its current behaviour.

## Questions for Beaux (batched by the Lead)

1. **Unsent messages are erased after 7 days.** Is 7 right, or would you prefer shorter (1 day) or longer (30 days)?
2. **No automatic resend.** After "No phone signal" or "Not sent", the message waits until Alex taps Try again. The
   alternative is to send it by itself when signal returns, which is easier but means a message can go out hours
   later, when Alex may have changed their mind. Recommendation: tap only.
3. **How much of the phone should use pictures?** This design adds one picture row, "Family", to Home, and pictures
   only inside voice messages. A person who can't read still needs to find Phone and Messages by place. Is that
   enough for now, or should a nonreader's whole Home use pictures (a separate, bigger change)?
