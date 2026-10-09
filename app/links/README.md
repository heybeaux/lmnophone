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
  - `OpenIn(pkg)`, `BlockedScreen(WEB_LINK | NOT_APPROVED | CHOOSER)`,
    `NoHandler(NO_RESOLVER | TARGET_NOT_RESOLVABLE | MALFORMED_INTENT_URI | INCONSISTENT_FACTS)`.
  - The decision is "which activity Android launches": the only candidate, Android's reported pick
    (`IntentFacts.resolved`), a verified App Link for a web intent, or the link-catcher when it is preferred. Two or
    more candidates with no pick are a chooser, even inside one package. `WEB_LINK` means Android picks the
    link-catcher; it is never inferred from the URL alone.
  - Contradictory facts (a target that disagrees with the resolvers, the parsed URI or `resolved`) fail closed as
    `INCONSISTENT_FACTS`.
  - `approved` = approved apps plus lock-task UI exceptions.
  - `intent:` and `android-app:` URIs are reconstructed by `IntentUri.parse`, following `Intent.parseUri(..., URI_INTENT_SCHEME)`
    (raw or Chrome-style sanitised). `S.browser_fallback_url` is never followed.
  - `IntentUri` is a port of AOSP `Intent.parseUriInternal` (including `android-app:`) and `getIntentOld`, `ComponentName.unflattenFromString`
    and `Uri.decode`. A `SEL;` selector is kept only when the outer intent has no package. With no `#` the result is a
    plain `VIEW` of the `intent:` data. A `#` without `#Intent;` is the old format, which can name a component.
    Unknown keys and unparsable typed extras are malformed. It is stricter than Android in one place: a package value
    that can't be a package name is malformed, which also ends in `NoHandler`.
  - A selector's own `package=` does not restrict resolution: `ComputerEngine.queryIntentActivitiesInternal` reads the
    package from the outer intent before it swaps in the selector (android-15.0.0_r1).
- `IntentFacts.action` is read only to tell a web intent (`ACTION_VIEW` plus an exact `http`/`https` scheme, as in
  `Intent.isWebIntent`). `IntentFacts.customTabs` is context only.
- `PackageFacts` and `IntentFacts` are plain data that lane D fills from PackageManager.
- `PackageFacts.signingCerts` and `PlainphoneIdentity.signingCerts` use lane A's `org.plainphone.core.CertDigest`
  (the seam recorded on `main` at `0b7061e`). The standalone `core-shim/` copies that type until the real `:core` lands.

## Notes for lane D
- `IntentFacts.resolvers` must be what `queryIntentActivities` returns for the intent as dispatched: one entry per
  activity, hidden packages absent, and the link-catcher present exactly when it matches. Set `explicitComponent`
  for an explicit component (not `setPackage`). Fill `resolved` from `resolveActivity(intent, MATCH_DEFAULT_ONLY)`
  (`ResolverActivity` is `Resolution.Chooser`) so saved preferences and priorities are honoured.
- A visible generic web handler (an approved http-only app, or a never-hide conflict) makes Android show a chooser,
  which lock task blocks (`BlockedScreen(CHOOSER)`). A persistent preferred activity for `VIEW http/https` to the
  link-catcher fixes it; set `IntentFacts.linkCatcherPreferred` once it exists. Spike evidence: `spikes/emulator/RESULT.md`
  Q4 link matrix and PLA-23 V3.
- Rows marked `Evidence.Unverified` in the tests need your emulator run.

## Limits (not containment proof)
- `ALLOWED` and `NOT_APPROVED` are classifier and policy results, not proof that an app cannot browse. An app with
  its own URL bar or WebView that answers neither probe scheme, or an https-only browser, is `ALLOWED` unless the
  catalog lists it. The approval UI must still show the unreviewed-app warning and disclose the WebView route.
- `approved` must be the effective lock-task set. Packages that share a UID with a lock-task package are eligible
  too; lane D expands the set and keeps it separate from trusted-person approval.
- Catalog `REVIEW` notes carry no reviewed app version yet; the versioned web-leak notes come with task 5.2.
- `android-app:` package, scheme and authority decoding assumes `encodeAppIntent` is enabled. AOSP Android 15's
  `Uri.decodeIfNeeded` leaves encoded values unchanged when that feature flag is off; this parser always decodes
  them. Lane D must check percent-encoded forms against the emulator build before claiming parseUri parity.

## Tests
`./gradlew :links:test` once the skeleton lands. Until then (standalone build, temporary `core-shim/`):
`cd app/links && ../../spikes/emulator/apps/gradlew -p . :links:test`
