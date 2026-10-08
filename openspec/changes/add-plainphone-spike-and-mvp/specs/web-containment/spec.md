## ADDED Requirements

### Requirement: General browsers, search and the Wi-Fi sign-in page are hidden
Plainphone SHALL classify a package as a **general browser** when either is true:
- it resolves `ACTION_VIEW` + `CATEGORY_BROWSABLE` for `https://plainphone-probe.invalid/`
- it qualifies for the browser role

Packages that only handle links to their own verified domains (App Links) are not general browsers.

Plainphone SHALL hide:
- general browsers
- the Google app
- Gemini
- app stores other than the Play Store
- the Wi-Fi sign-in page package (CaptivePortalLogin)

The trusted person SHALL NOT be able to approve general browsers, search apps or other app stores. They MAY allow the Wi-Fi sign-in page for one supervised session.

#### Scenario: Browser arrives with an update
- **WHEN** a system update adds or re-enables a general browser
- **THEN** Plainphone hides it within 15 minutes, and lock task stops it from opening in the meantime

#### Scenario: Banking app with its own links
- **WHEN** the trusted person approves a bank app whose only web intent filter is its own verified domain
- **THEN** the classifier does not treat it as a browser, and it can be approved

#### Scenario: Hotel Wi-Fi
- **WHEN** the phone joins a network that needs a sign-in page
- **THEN** no sign-in page opens unless the trusted person starts a supervised Wi-Fi sign-in session

### Requirement: Link handling follows a fixed matrix
Plainphone SHALL hold the browser role and verify it at handover.
- Verified App Links of approved apps SHALL open those apps.
- Every other `http`/`https` link SHALL open Plainphone's blocked-link screen.
- `intent:` URIs, custom schemes and explicit components SHALL open only when they resolve to an approved app or a lock-task UI exception.
- Plainphone SHALL NOT provide Custom Tabs.

#### Scenario: Tap a web link in Messages
- **WHEN** the user taps a link to a site with no approved app
- **THEN** the blocked-link screen shows the site's real host and offers "Send to [trusted person]"

#### Scenario: Tap an approved app's link
- **WHEN** the user taps a verified link for an approved app's domain
- **THEN** the approved app opens, not a browser

### Requirement: Blocked-link screen is isolated and safe
The blocked-link screen SHALL be a separate exported activity, with no access to admin functions. It SHALL ignore all extras. It SHALL parse the URL, and show only its registrable host, rendering confusable and bidirectional characters visibly. Before display or forwarding, it SHALL remove userinfo, query and fragment.

Forwarding SHALL need the user's confirmation, and SHALL go only to the trusted person's fixed number. Input longer than 2,000 characters SHALL be refused.

#### Scenario: Deceptive link
- **WHEN** the user taps `https://trusted.example@attacker.invalid/login?token=abc`
- **THEN** the screen shows "attacker.invalid", and any forwarded text omits the userinfo and the token

### Requirement: Web-leak notes are per app version
Each catalog entry SHALL carry a plain-language web-leak note and the app version it was reviewed against. The trusted person SHALL see the note before approving. An app updated past its reviewed version SHALL show "Reviewed for an older version". An app with no entry SHALL show "Not reviewed yet – this app may open web pages inside it", and approving it SHALL need an extra confirmation.

#### Scenario: Approving an unreviewed app
- **WHEN** the trusted person approves an app with no catalog entry
- **THEN** they see the "Not reviewed yet" warning, and must confirm it before approving
