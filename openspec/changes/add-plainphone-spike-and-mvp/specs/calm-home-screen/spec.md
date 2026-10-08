## ADDED Requirements

### Requirement: Plain home screen
The home screen SHALL show:
- the time and date
- an Emergency button
- Phone and Messages
- the other approved apps, with large labels and icons, in an order the trusted person chooses

It SHALL NOT show a search bar, feed, widgets, badges or suggestions.

#### Scenario: First look
- **WHEN** the user unlocks the phone
- **THEN** they see the time, the date, Emergency, Phone, Messages and their other approved apps, and nothing else

### Requirement: Phone, Messages and Emergency are always present
Phone, Messages and Emergency SHALL always be on the home screen. They SHALL NOT be removable through trusted-person controls.

#### Scenario: Trusted person views the app list
- **WHEN** the trusted person views the app list in trusted-person mode
- **THEN** Phone, Messages and Emergency are marked "always on" and have no remove action

### Requirement: Built-in basic settings without Android Settings
Plainphone SHALL provide its own settings screen, using Device Owner APIs, and SHALL NOT use Android Settings activities or panels. It SHALL cover:
- joining and forgetting Wi-Fi networks
- Bluetooth pairing
- volume and ringtone
- screen brightness
- text size
- battery level

Every label SHALL be in plain language. Any setting that cannot be built this way SHALL be listed as a helper task done in maintenance mode.

#### Scenario: Join home Wi-Fi
- **WHEN** the user opens Settings → Wi-Fi in Plainphone, picks a network and types the password
- **THEN** the phone connects, and Android Settings never appears

### Requirement: Readable by default
All Plainphone screens SHALL:
- follow the system font scale
- meet WCAG 2.2 AA contrast
- use touch targets of at least 48dp
- have one primary action

#### Scenario: Large text
- **WHEN** the text size is set to the largest option
- **THEN** every Plainphone screen still shows its primary action without horizontal scrolling
