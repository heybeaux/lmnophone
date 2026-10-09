## ADDED Requirements

### Requirement: Browser classification is a heuristic plus a catalog review
Plainphone SHALL check identities in this order, before classifying anything:
1. **Plainphone itself is never classified.** It is identified by exact package name and signing certificate.
2. **Never-hide packages always take precedence.** If one of them also looks like a browser, the conflict SHALL be reported for review and SHALL NOT be hidden automatically.

A package counts as a **general browser** if either of these is true:
- `queryIntentActivities` with `MATCH_ALL` lists it for both `http` and `https` `ACTION_VIEW` + `BROWSABLE` intents to `plainphone-probe.invalid`. This is a local resolver probe only. Plainphone SHALL never fetch that URL.
- It appears in the reviewed catalog of known browsers.

An app that only handles links for its own verified domains (App Links) is not a general browser.

On a build with no inventory, Plainphone SHALL hide only user-installed (non-system) general browsers. Newly classified system packages stay unhidden. They cannot be opened in the foreground unless Android treats them as lock-task-eligible because they share a UID with an allowed package. That is a disclosed residual risk (device-lockdown).

Plainphone SHALL hide:
- general browsers
- the Google app and Gemini
- app stores other than the Play Store

The trusted person SHALL NOT be able to approve any of these.

#### Scenario: Classifier fixtures
- **WHEN** the classifier runs on Plainphone, a general browser, a bank app with only verified-domain links, an app that only handles http, an app with an in-app URL bar but no web intent filter, and the never-hide packages
- **THEN** only the general browser is hidden. The URL-bar app is flagged for catalog review. Plainphone and the never-hide packages are untouched.

#### Scenario: Browser arrives with an update
- **WHEN** a system update adds or re-enables a general browser
- **THEN** Plainphone hides it within 15 minutes, and lock task stops it from opening in the meantime

### Requirement: Outside links go to the blocked-link screen; in-app web views are not intercepted
Plainphone SHALL hold the browser role, and the handover check SHALL confirm it.

| Link | Result |
|---|---|
| An approved app's verified App Link | Opens that app |
| Any other outside `http`/`https` link that Android sends to a browser | Plainphone's blocked-link screen |
| An explicit intent aimed at a hidden browser | Fails, because there is no handler |
| `intent:` URIs and custom schemes | Open only if they lead to an approved app or a lock-task UI exception |

Plainphone SHALL NOT provide Custom Tabs.

Navigation inside an approved app's own WebView is not intercepted. This is disclosed as a leftover risk.

#### Scenario: Tap a web link in Messages
- **WHEN** the user taps a link to a site that has no approved app
- **THEN** the blocked-link screen shows the site's real host and offers "Send to [trusted person]"

#### Scenario: Tap an approved app's link
- **WHEN** the user taps a verified link for an approved app's domain
- **THEN** the approved app opens

### Requirement: The blocked-link screen is isolated and safe
The blocked-link screen SHALL be its own exported activity, with no access to admin functions, and SHALL ignore every extra it receives.

**Showing the link:**
- It SHALL parse the URL and show only the registrable host.
- Confusable and bidirectional characters SHALL be shown visibly.
- Before showing or forwarding, it SHALL remove the userinfo, query and fragment.
- Input longer than 2,000 characters SHALL be refused.

**Forwarding.** Forwarding SHALL need the user's confirmation, and SHALL go only to the trusted person's fixed number.

#### Scenario: Deceptive link
- **WHEN** the user taps `https://trusted.example@attacker.invalid/login?token=abc`
- **THEN** the screen shows "attacker.invalid", and forwarded text leaves out the userinfo and the token

### Requirement: Wi-Fi networks that need a sign-in page are unsupported
Plainphone SHALL hide the Wi-Fi sign-in page app (CaptivePortalLogin). Plainphone SHALL NOT offer any session that shows it.

This requirement holds for the MVP. Beaux approved a later Wi-Fi sign-in window (Telegram 4481, PLA-17). It will replace this requirement only through a follow-up OpenSpec change that he approves before it is built.

When the connected network reports `NET_CAPABILITY_CAPTIVE_PORTAL`, Plainphone SHALL show: "This Wi-Fi needs a sign-in page, which Plainphone doesn't allow. Use mobile data or another Wi-Fi." When a connected Wi-Fi stays unvalidated, with no internet and no portal detected, Plainphone SHALL show: "This Wi-Fi isn't reaching the internet." Both messages SHALL offer "Forget this network".

Plainphone SHALL NOT gate the emergency UI on Wi-Fi. It does not promise a radio connection when there is neither mobile service nor usable Wi-Fi. Beaux SHALL accept this trade-off as part of the leftover-risk acceptance. Checked against their use (Telegram 4481): they rely on Wi-Fi calling sometimes. Wi-Fi calling on ordinary Wi-Fi SHALL work with Plainphone's policy on; this is checked before handover.

#### Scenario: Hotel Wi-Fi
- **WHEN** the phone joins a network that needs a sign-in page, with no mobile data
- **THEN** no sign-in page opens, the message above is shown, the network can be forgotten, and the Emergency button still opens the dialer

#### Scenario: Home Wi-Fi
- **WHEN** the phone joins an ordinary WPA2 home network
- **THEN** it connects and has internet access with CaptivePortalLogin hidden

### Requirement: Web-leak notes are tied to an app version
Each catalog entry SHALL carry a plain-language web-leak note, along with the app version it was reviewed against.

The trusted person SHALL see the note before approving the app.

If an app has updated past its reviewed version, Plainphone SHALL show "Reviewed for an older version".

If an app has no catalog entry, Plainphone SHALL show "Not reviewed yet – this app may open web pages inside it", and approving the app SHALL need an extra confirmation.

#### Scenario: Approving an unreviewed app
- **WHEN** the trusted person approves an app with no catalog entry
- **THEN** they see the "Not reviewed yet" warning and must confirm it
