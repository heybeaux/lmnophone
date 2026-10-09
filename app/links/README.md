<!-- SPDX-License-Identifier: Apache-2.0 -->
# `:links` (lane C, PLA-49)

Pure Kotlin/JVM. No `android.*`. Spec: `openspec/changes/add-plainphone-spike-and-mvp/specs/web-containment/spec.md`, design D4.

## API
- `BrowserClassifier(PlainphoneIdentity).classify(facts, catalog, neverHide, inventoryKnown): Classification`
  - Order: Plainphone (exact package and signer set), never-hide, then the probe heuristic (http **and** https for
    `plainphone-probe.invalid`) and the catalog.
  - A browser-like never-hide package is `NEVER_HIDE_CONFLICT`: reported, never hidden.
  - `hide`, `heldSystem` (system matches on a build with no inventory), `conflicts`, `review`, `notApprovable`.
- `BrowserCatalog.bundled()`: `src/main/resources/org/plainphone/links/browser-catalog.tsv`, versioned; every entry
  has a kind, a status (`EMULATOR`, `DEVICE`, `UNVERIFIED`) and a source. `REVIEW` notes disclose in-app WebView browsing.
- `LinkDecider(linkCatcher).decide(intent: IntentFacts, approved): LinkDecision`
  - `OpenIn(pkg)`, `BlockedScreen(WEB_LINK | NOT_APPROVED | CHOOSER)`, `NoHandler(...)`.
  - `approved` = approved apps plus lock-task UI exceptions.
  - `intent:` URIs are reconstructed by `IntentUri.parse`, following `Intent.parseUri(..., URI_INTENT_SCHEME)`
    (raw or Chrome-style sanitised). `S.browser_fallback_url` is never followed.
  - `IntentUri` is a port of AOSP `Intent.parseUriInternal` and `getIntentOld`, `ComponentName.unflattenFromString`
    and `Uri.decode`. A `SEL;` selector is kept only when the outer intent has no package. With no `#` the result is a
    plain `VIEW` of the `intent:` data. A `#` without `#Intent;` is the old format, which can name a component.
    Unknown keys and unparsable typed extras are malformed. It is stricter than Android in one place: a package value
    that can't be a package name is malformed, which also ends in `NoHandler`.
- `IntentFacts.action` and `IntentFacts.customTabs` are context only. `LinkDecider` doesn't read them; their effect
  is already in `resolvers`.
- `PackageFacts` and `IntentFacts` are plain data that lane D fills from PackageManager.

## Notes for lane D
- `IntentFacts.resolvers` must be what `queryIntentActivities` returns for the intent as dispatched (hidden packages
  never appear).
- A visible generic web handler (an approved http-only app, or a never-hide conflict) makes Android show a chooser,
  which lock task blocks (`BlockedScreen(CHOOSER)`). A persistent preferred activity for `VIEW http/https` to the
  link-catcher fixes it; set `IntentFacts.linkCatcherPreferred` once it exists. Spike evidence: `spikes/emulator/RESULT.md`
  Q4 link matrix and PLA-23 V3.
- Rows marked `Evidence.Unverified` in the tests need your emulator run.

## Tests
`./gradlew :links:test` once the skeleton lands. Until then (standalone build, temporary `core-shim/`):
`cd app/links && ../../spikes/emulator/apps/gradlew -p . :links:test`
