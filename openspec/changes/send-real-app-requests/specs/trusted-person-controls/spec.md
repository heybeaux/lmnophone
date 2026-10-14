## ADDED Requirements

### Requirement: Preview app requests use an explicit recipient and truthful results
LMNOPhone SHALL send an app request only to the explicit recipient saved in Who
gets requests after a fresh PIN. It SHALL preserve the request text, day and send
result across restart and in-place update. It SHALL retain the five-attempt daily
limit. It SHALL NOT infer a recipient from Family or claim Delivered.

#### Scenario: No recipient configured
- **WHEN** the user opens Ask for an app without a saved number
- **THEN** the not-set-up state is shown and no SMS is sent

#### Scenario: Partial or interrupted SMS send
- **WHEN** all parts do not have confirmed sent results
- **THEN** the result is Sending temporarily, Not sent for a definite complete failure, or Not confirmed for a partial or uncertain send
- **AND** no automatic retry occurs

#### Scenario: Changing who gets requests
- **WHEN** the PIN is wrong or the editor leaves before authorization completes
- **THEN** the saved recipient remains unchanged

#### Scenario: Requests list after restart
- **WHEN** the trusted person opens Requests after a restart or in-place update
- **THEN** saved text, day and results are listed without action controls
