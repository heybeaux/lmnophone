## ADDED Requirements

### Requirement: Plain home screen
The home screen SHALL show the time, the date, an Emergency button, and a list of approved apps with large labels and icons in an order the trusted person chooses. It SHALL NOT show a search bar, feed, widgets, badges, or suggestions.

#### Scenario: First look
- **WHEN** the user unlocks the phone
- **THEN** they see the time, Phone, Messages, Emergency and their approved apps, and nothing else

### Requirement: Phone, Messages and Emergency are always present
Phone, Messages and Emergency SHALL always be on the home screen and SHALL NOT be removable through trusted-person controls.

#### Scenario: Trusted person tries to remove Phone
- **WHEN** the trusted person views the app list in trusted-person mode
- **THEN** Phone, Messages and Emergency are shown as "always on" with no remove action

### Requirement: Built-in basic settings
Because Android Settings is blocked, Plainphone SHALL provide its own settings screen. It SHALL cover joining and forgetting Wi-Fi networks, Bluetooth pairing, volume and ringtone, screen brightness, text size, and battery level. Every label SHALL be plain language, with no Android terms.

#### Scenario: Join home Wi-Fi
- **WHEN** the user opens Settings → Wi-Fi in Plainphone, picks a network and types the password
- **THEN** the phone connects, without Android Settings ever appearing

### Requirement: Readable by default
All Plainphone screens SHALL follow the system font scale. They SHALL meet WCAG 2.2 AA contrast. Touch targets SHALL be at least 48dp. Each screen SHALL have one primary action.

#### Scenario: Large text
- **WHEN** text size is set to the largest option
- **THEN** every Plainphone screen still shows its primary action without horizontal scrolling
