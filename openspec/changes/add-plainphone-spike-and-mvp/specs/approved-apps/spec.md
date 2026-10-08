## ADDED Requirements

### Requirement: Unapproved apps cannot be opened
Every installed app that is not on the allowlist SHALL be hidden with `setApplicationHidden`. This does not apply to:
- the never-hide list;
- the Play Store;
- a reviewed list of Android packages that cannot be hidden without breaking the phone.

The guarantee is that an unapproved app cannot be **opened**. It is not that one cannot be installed, or never runs code. Plainphone SHALL hide new packages on the package-added broadcast, and reconcile all packages at boot and every 15 minutes. Lock task SHALL stop an unapproved app from opening even if hiding fails. The time from install to hidden SHALL be measured and recorded (test S7).

#### Scenario: Unapproved app is installed by any path
- **WHEN** a package not on the allowlist is installed, for example pushed from the Play website
- **THEN** it never appears on the home screen, and no tested entry point (deep link, share target, notification, explicit intent) can open it

#### Scenario: Hide call fails
- **WHEN** hiding a new package fails
- **THEN** Plainphone retries, lock task still prevents launch, and trusted-person mode shows "Plainphone needs attention"

### Requirement: Approved apps keep receiving updates, and staleness is visible
Plainphone SHALL keep the Play Store installed, enabled and signed in to the user's own account. The Play Store SHALL NOT be on the lock-task UI list outside a supervised session.

Setup SHALL include a step where the helper confirms Play auto-update is on. Trusted-person mode SHALL show when each approved app was last updated. If neither an approved app nor Google Play services has updated for 30 days, it SHALL show "Updates may be stuck" and offer a supervised update session.

#### Scenario: Background update of an approved app
- **WHEN** Play has a newer version of an approved app and auto-update is on
- **THEN** the app updates without the user opening the Play Store, and stays approved and visible (test Q2 defines the test conditions and time limit)

#### Scenario: Updates stop
- **WHEN** no approved app or Play services update for 30 days
- **THEN** trusted-person mode shows "Updates may be stuck" with a "Check updates" action

#### Scenario: Approved app opens a Play Store link
- **WHEN** an approved app opens a `market://` or Play web link, in a new task or the same task
- **THEN** the Play Store does not open

### Requirement: Supervised Play session is a bounded, authenticated state machine
Starting a Play session SHALL require a fresh PIN or recovery-code entry. Starting it SHALL record a session token and a 10-minute deadline, measured on elapsed time since boot.

The session SHALL end on whichever comes first:
- the deadline
- the screen turning off
- Plainphone restarting
- a reboot
- leaving the Play Store
- tapping Done

Ending SHALL remove the Play Store from lock task, restore any lifted restriction, and read the policy back before the home screen is shown. Approving a newly installed app SHALL require a fresh PIN entry. The approval SHALL bind the package name and signing-certificate digest. Events that arrive for an ended session SHALL be ignored.

#### Scenario: Add an app
- **WHEN** the trusted person starts a session, installs an app, and approves it with a fresh PIN
- **THEN** the app appears on the home screen, and the Play Store is unreachable again

#### Scenario: Trusted person walks away
- **WHEN** the screen turns off, or 10 minutes pass, during a session
- **THEN** the session ends, the Play Store is unreachable, and any app installed in the session stays hidden until approved with a fresh PIN

#### Scenario: Plainphone restarts mid-session
- **WHEN** Plainphone's process is killed during a session
- **THEN** on restart the session is closed, the policy is restored, and no stale session can be resumed

### Requirement: Removing an app is immediate and reversible
The trusted person SHALL be able to remove an app from the allowlist after a fresh PIN entry. Plainphone SHALL hide the app, not uninstall it.

#### Scenario: Remove then re-approve
- **WHEN** the trusted person removes an app and later approves it again
- **THEN** the app returns with its previous data
