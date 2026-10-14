# Design

The package manager supplies the current version name and code. Application startup observes the code in device-protected `home_preferences`, using separate `release_current` and `release_previous` keys and synchronous `commit()`. A same-version restart leaves both unchanged. A changed version replaces current and retains its old value as previous. Failed storage makes no historical claim. No install timestamps are inferred.

A checked-in Kotlin release catalogue holds shipped notes, with inclusive ranges for old grouped releases. The production mapper shows a since-last section only for a known older version and known current release. Unknown and downgraded versions use latest-only. UI offers a vertically scrolling Older versions view. Each build must have a catalogue entry, enforced by a host test reading the build version.

Default shell identity and requests are empty; PIN remains reachable with neutral copy. The family list is never a trusted-person source. Voice host reads saved Family data without mutating it; synthetic fixtures exist only in tests and private Compose previews. The retired practice-send marker cannot select any recipient.
