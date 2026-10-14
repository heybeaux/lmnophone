## 1. Implement and verify
- [x] 1.1 Checkpoint tests before runtime changes.
- [x] 1.2 Remove live fixtures and false results; preserve real saved data.
- [x] 1.3 Add startup version tracking and installed-release About.
- [x] 1.4 Run affected host suites and inspect large-text screenshots.
- [x] 1.5 Hand off pushed branch for one review and driver installation.

## Verification and delivery handoff

- Tests-first checkpoint: `1ea6568d` (test sources only); runtime implementation: `4002e9f8`; screenshot and scroll verification: `c9efe321`.
- `:ui:test`: 1,215 tests per debug/release variant passed.
- `:device:test`: 1,081 tests per debug/release variant passed.
- `:plainphone:test`: 300 tests per variant; debug and all four control variants passed. Release has one inherited failure: `VoiceSendRouteTest.theDebugBuildTypeTurnsFamilySendingOn` asserts true even when built against the deliberately disabled release gate. Both that test and `src/release/.../VoiceSendBuild.kt` are unchanged from base `acdffedb`; no check was disabled.
- Focused `:ui:verifyPaparazziDebug --tests org.plainphone.ui.AboutScreenScreenshotTest` passed: 30 inspected screenshots at 360×640, scales 1.0/1.3/1.5/1.8/2.0, with scroll reachability assertions. `openspec validate --all`: five changes passed.
- `:plainphone:assembleDebug` passed: version 49 / 0.1.0-preview-49, `org.plainphone`, `testOnly=true`. SHA-256: `712001a5d3c747d60342779b21bc54915f06055e8668c0bbde939ee652d9f135`.
- Built and host-exercised only. No emulator or physical-device commands, install, live-message send or call. The Lead routes the one focused opposite-model review and existing sole driver. Device installation/readback and real upgrade persistence exercise belong to that delivery step.
- Remaining fixture names: test sources, private Compose `@Preview` functions, and explanatory code comments only. Runtime Family data is preserved, including a saved person named Sam; it is never used as trusted-person identity. Old practice drafts are not migrated to guessed recipients or deleted by this cleanup.
