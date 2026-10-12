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

#### Scenario: Build 30 read-only Bluetooth preview
- **WHEN** the user opens Phone settings → Bluetooth in build 30
- **THEN** STATE_ON shows at most ten paired device names sorted case-insensitively, with the U7 unnamed and empty fallbacks
- **AND** every other state or no adapter shows off, and read failure shows unavailable without any state claim or list
- **AND** the page refreshes while visible and always states that pairing comes in a later test build
- **AND** the app may grant only its own BLUETOOTH_CONNECT permission and never scans, pairs, unpairs, connects, changes Bluetooth, uses Location, or opens system UI
- **AND** device names remain ephemeral display data; recorded evidence contains counts only

#### Scenario: Build 31 built-in ringtone preview
- **WHEN** the user opens Sound → Ringtone in build 31
- **THEN** only built-in internal TYPE_RINGTONE entries are shown, sorted by title ignoring case, with the current entry selected when present
- **AND** tapping previews at the existing volume/mode for at most about five seconds, stopping any earlier preview and stopping on leave
- **AND** Done saves only a changed selection, reads the actual RINGTONE setting back, and returns to Sound only on a matching read-back; failure stays with the U7 failure line
- **AND** an absent settings-write grant disables Done but permits preview; no selection leaves the current ringtone unchanged
- **AND** no new permission, external picker, Android Settings, custom ringtone, Silent choice, notification sound or alarm sound is added

### Requirement: Readable by default
All LMNOPhone screens SHALL:
- follow the system font scale
- meet WCAG 2.2 AA contrast
- use touch targets of at least 48dp
- have one primary action

#### Scenario: Large text
- **WHEN** the text size is set to the largest option
- **THEN** every LMNOPhone screen still shows its primary action without horizontal scrolling

### Requirement: Build 32 Bluetooth discovery preview
The authorized preview SHALL offer nearby Bluetooth discovery only while
Bluetooth is fully on, with BLUETOOTH_SCAN marked neverForLocation and no
Location use. It SHALL NOT change Bluetooth, add a system package to lock task,
or attempt pairing while the pairing confirmation is known to be inaccessible.

#### Scenario: Discover nearby devices
- **WHEN** the user opens Pair a new device
- **THEN** discovery starts and named unpaired devices are sorted ignoring case, deduplicated by identity and limited to twenty
- **AND** completion offers Look again with U7 empty/error copy as appropriate
- **AND** leaving or pausing cancels discovery and clears its observations

#### Scenario: Pairing not ready
- **WHEN** the user selects a nearby device
- **THEN** its name and the U7 pairing-not-ready message appear with Back only
- **AND** no bond request, system Settings launch or lock-task change occurs

### Requirement: Build 33 temporary Location Wi-Fi joining
The approved build-33 preview SHALL offer native Wi-Fi joining for open and
WPA2/WPA3 personal networks, without system Settings, hidden networks or Forget.
The explicit Show nearby networks action MAY enable Location after durably
recording ownership. Only owned Location SHALL be disabled after join success,
leaving the flow, five minutes, or process restart. An active or unknown call
state SHALL defer disabling until idle. Every Location write SHALL be read back;
failed restoration SHALL retain the marker and show the approved failure copy.

#### Scenario: Join with temporary Location
- **WHEN** Location is off and the person taps Show nearby networks and joins
- **THEN** the confirmed network appears as connected and owned Location turns off
- **AND** an already-on Location setting and existing saved configurations are preserved

#### Scenario: Join failure and cancellation
- **WHEN** joining fails or takes 30 seconds
- **THEN** the password screen shows the approved retry line
- **AND** leaving clears ephemeral credentials and restores owned Location once idle
