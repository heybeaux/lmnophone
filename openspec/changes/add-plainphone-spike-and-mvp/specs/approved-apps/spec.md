## ADDED Requirements

### Requirement: Unapproved apps cannot be opened
Every installed app that is not on the allowlist SHALL be hidden with `setApplicationHidden`. Three groups are exempt: the never-hide list, the Play Store, and a reviewed list of packages that cannot be hidden without breaking the phone.

**What is promised.** An unapproved app cannot be **opened**, from the first interactive frame after unlock. It is not promised that the app is never installed, or never runs any code.

**When Plainphone hides apps.** Plainphone SHALL hide new packages when it receives the package-added broadcast. It SHALL also check every package against the allowlist at boot and every 15 minutes. Lock task SHALL stop an unapproved app from opening even if hiding it fails.

**Background code.** If hiding fails, the app may run background code, and no time limit is promised. This SHALL be disclosed.

**Preinstalled apps.** Apps installed at first boot or later by preinstall services (for example `com.dti.motorola`) SHALL be treated like any other unapproved install.

#### Scenario: Unapproved app installed by any path
- **WHEN** a package not on the allowlist is installed, whether pushed from the Play website, by a preinstall service, or preloaded
- **THEN** no tested entry point can open it: deep link, share target, notification, or explicit intent

#### Scenario: Hide call fails
- **WHEN** hiding a new package fails
- **THEN** Plainphone retries, lock task still prevents the app opening, and trusted-person mode shows "Plainphone needs attention"

### Requirement: Approved apps keep receiving updates, and the trusted person checks monthly
Plainphone SHALL keep the Play Store installed, enabled and signed in to the user's own account, and outside the lock-task UI list except during a Play session. Setup SHALL include a step where the helper confirms Play auto-update is on.

**Per-app record.** Plainphone SHALL record each approved app's version and last update time.

**Monthly check.** Once a month, Plainphone SHALL text the trusted person: "Time for the monthly update check on [user]'s phone." The check SHALL be a Play session that opens Play's update list, where Play itself shows any updates that are waiting.

**Play services warning.** If Google Play services has not updated in 30 days, Plainphone SHALL also text the trusted person.

**What Plainphone can claim.** Plainphone SHALL NOT claim to know whether a newer version exists.

#### Scenario: Background update of an approved app
- **WHEN** Play is offering a newer version of an approved app and auto-update is on
- **THEN** the app updates without the user opening Play, and stays approved and visible. Q2 sets the test conditions, which include an unattended run.

#### Scenario: One essential app stuck
- **WHEN** one approved app keeps failing to update while others update normally
- **THEN** the next monthly check shows the waiting update in Play's own list, during a session the trusted person controls

#### Scenario: Approved app opens a Play Store link
- **WHEN** an approved app opens a `market://` or Play web link, in a new task or the same task
- **THEN** the Play Store does not open

### Requirement: Play sessions add and approve apps
Adding an app SHALL happen in a Play session, which follows the supervised-session contract in trusted-person-controls. Approving a newly installed app SHALL need a fresh PIN entry, and SHALL bind to the app's package name and signing-certificate digest. Events that arrive after a session has ended SHALL be ignored. An app installed during a session SHALL stay hidden until it is approved.

#### Scenario: Add an app
- **WHEN** the trusted person starts a Play session, installs an app, and approves it with a fresh PIN
- **THEN** the app appears on the home screen, and Play cannot be reached again

#### Scenario: Trusted person walks away
- **WHEN** the screen turns off, or the deadline passes, during a Play session
- **THEN** the session ends, and any app installed in the session stays hidden until it is approved

### Requirement: Removing an app is immediate and reversible
After a fresh PIN entry, the trusted person SHALL be able to remove an app from the allowlist. Plainphone SHALL hide the app, not uninstall it.

#### Scenario: Remove then re-approve
- **WHEN** the trusted person removes an app and later approves it again
- **THEN** the app comes back with its previous data
