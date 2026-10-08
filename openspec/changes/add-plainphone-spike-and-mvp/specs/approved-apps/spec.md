## ADDED Requirements

### Requirement: Only approved apps are usable
Every installed app that is not on the allowlist SHALL be hidden with `setApplicationHidden`. Exceptions: protected packages, the Play Store, and Android packages that hiding would break, recorded as a reviewed list in policy.

#### Scenario: Unapproved app is installed by any path
- **WHEN** a package that is not on the allowlist is added, for example pushed from the Play website
- **THEN** Plainphone hides it on the package-added event, and it never appears on the home screen

### Requirement: Approved apps keep receiving updates
Plainphone SHALL keep the Play Store installed, enabled and signed in, so it can update approved apps and Google Play services in the background. Plainphone SHALL NOT keep `DISALLOW_INSTALL_APPS` set outside a bounded operation of its own. The Play Store SHALL NOT be reachable by the user outside a supervised session.

#### Scenario: Background update of an approved app
- **WHEN** a newer version of an approved app is published on Play
- **THEN** the app updates without anyone opening the Play Store, and stays approved and visible afterwards

#### Scenario: Approved app opens a Play Store link
- **WHEN** an approved app opens a `market://` or Play web link
- **THEN** the Play Store does not open

### Requirement: Trusted person adds apps in a supervised session
Adding an app SHALL require the trusted-person PIN. Plainphone SHALL then open the Play Store as a temporary lock task package. The session SHALL end when the trusted person leaves the Play Store, presses Done, or after 10 minutes, whichever comes first. A newly installed app SHALL stay hidden until the trusted person confirms "Allow" in Plainphone.

#### Scenario: Add an app
- **WHEN** the trusted person enters the PIN, picks "Add an app", installs an app, and taps Allow
- **THEN** the app appears on the home screen and the Play Store becomes unreachable again

#### Scenario: Session times out
- **WHEN** 10 minutes pass in a supervised session
- **THEN** Plainphone returns to the home screen, removes the Play Store from lock task, and keeps any unconfirmed new app hidden

### Requirement: Removing an app is immediate and reversible
The trusted person SHALL be able to remove an app from the allowlist. Plainphone SHALL hide it, not uninstall it, so its data survives if it is approved again.

#### Scenario: Remove then re-approve
- **WHEN** the trusted person removes an app and later approves it again
- **THEN** the app returns with its previous data
