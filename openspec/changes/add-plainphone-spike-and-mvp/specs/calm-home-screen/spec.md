## ADDED Requirements

### Requirement: Plain home screen
The home screen SHALL show:
- the time and date
- Phone and Messages
- the other approved apps, with large text labels and no icons, in an order the trusted person chooses

It SHALL NOT show a search bar, feed, widgets, badges or suggestions.

There are two exceptions. When they apply, the home screen SHALL show one of these lines:
- the monthly-check line, "Ask [trusted person] to do the monthly phone check" (approved-apps);
- "LMNOPhone needs attention" (device-lockdown).

Neither line SHALL push Phone or Messages off-screen at the largest font size.

#### Scenario: First look
- **WHEN** the user unlocks the phone
- **THEN** they see the time, the date, Phone, Messages and their other approved apps, and nothing else unless one of the two notice lines applies

#### Scenario: Overdue monthly check at largest font
- **WHEN** the monthly check is more than 7 days overdue and text size is at its largest
- **THEN** the notice line shows, and Phone and Messages are still visible without scrolling

### Requirement: Phone and Messages are always present
Phone and Messages SHALL always be on the home screen. They SHALL NOT be removable through trusted-person controls.

#### Scenario: Trusted person views the app list
- **WHEN** the trusted person views the app list in trusted-person mode
- **THEN** Phone and Messages are marked "always on" and have no remove action

### Requirement: No emergency control on the home screen
The home screen SHALL NOT show an Emergency or 9-1-1 button, tile or gesture target (Beaux, Telegram 4487). The user calls 9-1-1 through the routes in essential-calling-and-texting. LMNOPhone MAY offer an "Emergency 9-1-1" entry inside More, or behind a long-press. It SHALL NOT be reachable by one tap from the home screen.

#### Scenario: Accidental tap on the home screen
- **WHEN** the user taps anywhere on the home screen once
- **THEN** no emergency dialer or emergency call starts

### Requirement: Built-in basic settings without Android Settings
LMNOPhone SHALL provide its own settings screen, using Device Owner APIs, and SHALL NOT use Android Settings activities or panels. It SHALL cover:
- joining and forgetting Wi-Fi networks
- Bluetooth pairing
- volume and ringtone
- screen brightness
- text size
- battery level

Every label SHALL be in plain language. Any Android setting outside this list SHALL be reachable only through a supervised Settings session (trusted-person-controls).

#### Scenario: Join home Wi-Fi
- **WHEN** the user opens Settings → Wi-Fi in LMNOPhone, picks a network and types the password
- **THEN** the phone connects, and Android Settings never appears

#### Scenario: Build 28 read-only Wi-Fi preview
- **WHEN** the user opens Phone settings → Wi-Fi in build 28
- **THEN** the page shows connection state and at most ten distinct visible nearby names, strongest first, refreshed while visible
- **AND** unreadable names use the U7 fallback copy and Wi-Fi off has no list
- **AND** no network can be joined or forgotten, Location and saved networks remain unchanged, and Android Settings never appears
- **AND** the only new permission grant is ACCESS_FINE_LOCATION to LMNOPhone itself through Device Owner, if needed

### Requirement: Readable by default
All LMNOPhone screens SHALL:
- follow the system font scale
- meet WCAG 2.2 AA contrast
- use touch targets of at least 48dp
- have one primary action

#### Scenario: Large text
- **WHEN** the text size is set to the largest option
- **THEN** every LMNOPhone screen still shows its primary action without horizontal scrolling
