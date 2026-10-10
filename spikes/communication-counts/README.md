# Communication counts — THROWAWAY

Emulator-only feasibility probe for C1–C7. Never import into the shipping app.
Only fake test calls/texts. Queries project `_id` and log aggregate counts, flags,
exception class and monotonic timestamps; never content, addresses or dates.

The privacy contract was written first. Initial `:probe:testDebugUnitTest`
failed with `Unresolved reference QueryPlan` (2026-10-10).

Build from this folder with JDK 17 and ANDROID_HOME:
`./gradlew :probe:testDebugUnitTest :probe:assembleDebug`.

Owned AVDs: `pla291-a35` / `emulator-5572`, `pla291-a36` / `emulator-5574`.
Do not run these scripts against a physical phone or another team's emulator.
