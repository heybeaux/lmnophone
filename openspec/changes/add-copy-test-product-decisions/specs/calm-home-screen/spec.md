## MODIFIED Requirements

### Requirement: Plain home screen
The home screen SHALL show:
- the time and date
- an Emergency button
- Phone and Messages
- the other approved apps, with large labels and icons, in an order the trusted person (or, in self-managed mode, the user) chooses

It SHALL NOT show a search bar, feed, widgets, badges or suggestions.

The one exception is a single **notice line**. The home screen SHALL show at most one notice line at a time. When several apply, it SHALL show the first that applies in this order:
1. "Plainphone needs attention" (device-lockdown);
2. a pending waiting change (trusted-person-controls), for example "Plainphone will turn off at {time}. Tap to cancel." or "A change to trusted people is waiting until {time}. Tap to cancel.";
3. in self-managed mode, an elevation in progress: "Opening up in {n} minutes" or "Ready – tap to start";
4. the monthly-check line, "Ask [trusted person] to do the monthly phone check" (approved-apps). In self-managed mode it asks the user.

Tapping a pending-change line SHALL let anyone holding the phone cancel that change, without a PIN.

The notice line SHALL NOT push Emergency, Phone or Messages off-screen at the largest font size.

#### Scenario: First look
- **WHEN** the user unlocks the phone
- **THEN** they see the time, the date, Emergency, Phone, Messages and their other approved apps, and nothing else unless a notice line applies

#### Scenario: Overdue monthly check at largest font
- **WHEN** the monthly check is more than 7 days overdue and text size is at its largest
- **THEN** the notice line shows, and Emergency, Phone and Messages are still visible without scrolling

#### Scenario: Turn-off pending and monthly check overdue
- **WHEN** a turn-off is waiting and the monthly check is overdue
- **THEN** only the turn-off line shows

#### Scenario: User cancels a pending turn-off
- **WHEN** the user taps "Plainphone will turn off at {time}. Tap to cancel." and confirms
- **THEN** the turn-off is cancelled, every trusted person is texted, and the line goes away
