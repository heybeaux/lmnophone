## MODIFIED Requirements

### Requirement: Plain home screen
The home screen SHALL show:
- the time and date
- Phone and Messages
- the other approved apps, with large text labels and no icons, in an order the trusted person chooses

It SHALL NOT show a search bar, feed, widgets, badges or suggestions. The only counts allowed are the optional missed-call and unread-text counts on Phone and Messages (Requirement: Optional missed-call and unread-text counts). No other app SHALL ever show a count on the home screen.

There are two exceptions. When they apply, the home screen SHALL show one of these lines:
- the monthly-check line, "Ask [trusted person] to do the monthly phone check" (approved-apps);
- "LMNOPhone needs attention" (device-lockdown).

Neither line SHALL push Phone or Messages off-screen at the largest font size.

#### Scenario: First look
- **WHEN** the user unlocks the phone
- **THEN** they see the time, the date, Phone, Messages and their other approved apps, and nothing else unless one of the two notice lines applies or the user has turned on counts

#### Scenario: Overdue monthly check at largest font
- **WHEN** the monthly check is more than 7 days overdue and text size is at its largest
- **THEN** the notice line shows, and Phone and Messages are still visible without scrolling

#### Scenario: Another app has unread items
- **WHEN** counts are on and an approved app other than Phone or Messages has notifications
- **THEN** that app's row shows no number

### Requirement: Built-in basic settings without Android Settings
LMNOPhone SHALL provide its own settings screen, using Device Owner APIs, and SHALL NOT use Android Settings activities or panels. It SHALL cover:
- joining and forgetting Wi-Fi networks
- Bluetooth pairing
- volume and ringtone
- screen brightness
- text size
- battery level
- one switch, "Show missed calls and unread texts on Home", off by default

Every label SHALL be in plain language. Any Android setting outside this list SHALL be reachable only through a supervised Settings session (trusted-person-controls).

#### Scenario: Join home Wi-Fi
- **WHEN** the user opens Settings → Wi-Fi in LMNOPhone, picks a network and types the password
- **THEN** the phone connects, and Android Settings never appears

## ADDED Requirements

### Requirement: Optional missed-call and unread-text counts
LMNOPhone SHALL offer one switch that controls both counts. It SHALL be off on a new install and on upgrade from a build without it. Its value SHALL survive restarts and in-place updates. The user SHALL be able to change it without a PIN, and changing it SHALL NOT change app approval, trusted-person authority or any lockdown policy.

When the switch is on, the home screen SHALL show:
- next to Phone, the number of missed calls the user has not yet acknowledged in the Phone app;
- next to Messages, the number of unread SMS and MMS messages in Android's text-message store.

Each number SHALL:
- come from a fresh read of Android's call log or text-message store, never from notifications and never from a saved value;
- be shown only when it is above zero and was read successfully;
- be plain digits in the existing ink colour and typeface, inside the existing row and touch target, with no colour, shape, border, divider, subtitle or animation;
- show "99+" above 99.

When a count can't be read (switch off, permission missing, phone not unlocked since boot, any read error or timeout, or the Phone or Messages role holder isn't the supported app), that number SHALL NOT be shown.

Each time Home becomes visible it SHALL start with no numbers, and SHALL show a number only from a read that started after it became visible and finished before LMNOPhone received any change notice. When LMNOPhone receives a change notice while Home is visible, an old number SHALL be hidden if a fresh one is not shown within 1 second of that notice. Independently, while Home is visible, no number SHALL be shown that comes from a read started more than 13 seconds ago.

For this feature LMNOPhone SHALL query only row IDs, the provider's fixed SMS/MMS transport label, and the type, new, read and message-box fields, and SHALL use only the number of rows returned, never a row's values. It SHALL NOT read, log, store or send message text, phone numbers, contact names or dates.

The Settings screen SHALL state under the switch that texts and picture messages are counted, that chat (RCS) messages and other apps aren't supported and may be missing, and that LMNOPhone keeps permission to check calls and texts after the switch is turned off.

The Messages number SHALL come from one read that covers SMS and MMS together at a single moment. If that read fails, the Messages number SHALL NOT be shown. A number added up from separate reads SHALL NEVER be shown.

The accessibility name of the Phone and Messages rows SHALL include the count when one is shown (for example "Phone, 2 missed calls"). Touch targets and large-text behaviour SHALL stay as they are.

#### Scenario: Default
- **WHEN** LMNOPhone is newly installed, or updated from a build that doesn't have this switch
- **THEN** the switch is off and Home shows no numbers

#### Scenario: Update keeps the choice
- **WHEN** the switch is on (or off) and LMNOPhone is updated in place
- **THEN** the switch is still on (or off) after the update, and no count was saved

#### Scenario: Messages changes during a read
- **WHEN** texts are marked read and picture messages arrive while LMNOPhone is reading the two stores
- **THEN** Messages shows only a total that existed at one moment, because SMS and MMS are counted in one read

#### Scenario: Different Messages app
- **WHEN** the default text app is changed to another app, or the standard Messages app is disabled
- **THEN** the Messages number disappears and Settings says counts work only with the standard apps

#### Scenario: Missed call
- **WHEN** counts are on and a call is missed
- **THEN** Phone shows "1" while Home is visible, without restarting LMNOPhone

#### Scenario: Refresh too slow
- **WHEN** Phone shows "2" and LMNOPhone receives a change notice, but the new read hasn't finished 1 second after the notice
- **THEN** the number is hidden until a fresh read finishes

#### Scenario: Change notice late or missing
- **WHEN** the call log changes while Home is visible, and Android delivers the change notice late or not at all
- **THEN** within 13 seconds Home shows the new number or no number

#### Scenario: Missed call acknowledged
- **WHEN** the user views the missed call in the Phone app's call history, taps or swipes the missed-call notification, or calls back, and returns Home
- **THEN** Phone shows no number for that call

#### Scenario: Declined call or voicemail
- **WHEN** the user declines a call, or a caller leaves voicemail without a missed-call entry
- **THEN** the missed-call count doesn't change

#### Scenario: Unread texts
- **WHEN** counts are on and two texts arrive in one conversation and one in another
- **THEN** Messages shows "3"; after the user opens one conversation it shows the remaining unread count

#### Scenario: Notification dismissed
- **WHEN** the user swipes away a message or missed-call notification without opening it
- **THEN** the count follows the system record (it changes only if the Phone or Messages app marks the item read or acknowledged)

#### Scenario: Switch off
- **WHEN** the user turns the switch off
- **THEN** both numbers disappear at once, LMNOPhone stops reading the call log and text store, and Home does not restart

#### Scenario: Permission lost
- **WHEN** counts are on and LMNOPhone loses access to the call log or text store
- **THEN** that number disappears, Settings says it can't be read right now, and nothing else on Home changes

#### Scenario: Restart
- **WHEN** the phone restarts with counts on
- **THEN** no number shows before unlock, and after unlock the numbers come from a fresh read

#### Scenario: Largest text
- **WHEN** counts are on, both are above 99 and text size is at its largest
- **THEN** Phone and Messages show "99+" in full, nothing is cut off, and Phone and Messages are still visible without scrolling
