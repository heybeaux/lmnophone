## ADDED Requirements

### Requirement: No browsers or search apps
Plainphone SHALL hide every installed web browser, the Google app, Gemini, and any app store other than the Play Store. A package counts as a browser if it handles `ACTION_VIEW` for `http`/`https` with `CATEGORY_BROWSABLE` and is not Plainphone. The trusted person SHALL NOT be able to approve these packages in the MVP.

#### Scenario: Browser installed by an update
- **WHEN** a system update adds or re-enables a browser
- **THEN** Plainphone hides it within one policy pass

### Requirement: Links open a blocked-link screen
Plainphone SHALL handle `http`/`https` VIEW intents. It SHALL show a calm screen: the link's site name, "This link is blocked", and an option to send the link to the trusted person by text. It SHALL NOT render the page or provide Custom Tabs.

#### Scenario: Tap a link in Messages
- **WHEN** the user taps a web link in a text message
- **THEN** the blocked-link screen shows the site name and offers "Send to [trusted person]"

### Requirement: Web-leak notes on approved apps
Each app in Plainphone's built-in catalog SHALL carry a plain-language web-leak note. The trusted person SHALL see that note before approving the app. An app with no catalog entry SHALL show "Not reviewed yet".

#### Scenario: Approving an unreviewed app
- **WHEN** the trusted person approves an app with no catalog entry
- **THEN** they first see "Not reviewed yet – this app may open web pages inside it" and must confirm
