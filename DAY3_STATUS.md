# Day 3 status — 2026-10-29

Phase A (readable Wear settings/proof shell) is implemented. Phase B was
authorized for selected-model diagnosis and a demonstrated fix only; its current
result is recorded below. Day 2 remains PASS WITH KNOWN DEBT. D3-C is unstarted;
this is not production chat or a completed Day 3 gate.

## Phase A scope and evidence

Main screen: fixed explicit billing header, concise connection/proof status,
active ChatGPT display identity when applicable, selected model and proof actions.
Model catalog, connection management, diagnostics/recovery guidance and live
proof output have separate scrollable surfaces with Back actions/system Back.
Stop remains outside the scrolling content on all proof/secondary surfaces while
busy. The plan welcome notice remains an explicit confirmation surface.

VERIFIED AUTOMATICALLY: 11 new presentation tests cover plan/API identity and
billing/model visibility, independent settings-action visibility, credential/scope/
storage gates, mode mismatch, Stop reachability during transitions, signed-out
remote outcome wording, failed/expired setup recovery, concise exhaustive problem
mapping, terminal-state distinctions and exact Unicode/text chunk preservation.
These are JVM presentation/state tests, not rendered Compose interaction tests.
Compose hierarchy/callback inspection separately confirms API setup, mode choices,
account selection/addition, sign-out, Manage usage, key removal, model selection,
reload/test and live output routes remain wired to their existing actions.

Validation uses DEVELOPMENT.md's persistent JDK/SDK/cache and unchanged signing
identity. Commands and final results are recorded below after validation. No
mobile/shared implementation, authentication, refresh, sign-out, credential store,
model persistence or inference protocol changes. No physical/emulator execution,
install, live request, credential operation, commit or push.

## One consolidated end-of-day physical check

- [ ] PENDING END-OF-DAY PHYSICAL TEST — On Galaxy Watch 7, check circular-screen
  readability (including larger font), touch/rotary scrolling and Back across
  proof/settings/models/output/details; confirm account, active plan/API billing
  and selected model are understandable, model selection and settings actions
  reachable, Stop stays reachable during streaming/output scrolling, and stopped/
  incomplete/error summaries plus Details remain readable. Preserve data/signing;
  do not repeat sign-out, remove real keys, exhaust usage or buy API credits just
  to check presentation. Use existing results or synthetic evidence for destructive
  and unavailable states. Physical PASS requires explicit owner evidence.

## Deferred scope

- Terra -> Sol model restoration: D3-B investigation below; not reproduced on
  current code, unchanged and unresolved. No physical resolution is claimed.
- Native text input/in-memory multi-turn chat: D3-3; unstarted.
- Streaming chat/Stop integration: later Day 3; current fixed proof only.
- Transient reauthorization exchange/context-expiry anomaly: tracked debt, no
  claimed cause/fix; preserve retry from watch and phone guidance. Day 5 disposition.
- Real successful paid BYOK: BLOCKED by no funded credential; existing synthetic
  success coverage retained. No purchase requested.

## Automated commands/results

Executed with the persistent environment:

```sh
JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9 \
ANDROID_HOME=$HOME/Android/Sdk \
ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android \
GRADLE_USER_HOME=$HOME/.gradle \
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:lintDebug --console=plain
git diff --check
```

Also executed :wear:compileDebugKotlin and focused :wear:testDebugUnitTest
--tests 'io.github.grassified.wristprompt.ui.ShellPresentationTest'. Focused tests
first caught a missing signing-out presentation guard; corrected and all 11 pass.
Full suite: 189 tests, zero failures/errors/skips; debug build and lint pass.
Lint retains only the four prior warning instances (two AGP advisories, exported
service, logo vector). Removed 27 unused strings from the retired dense screen;
no lint suppression. Existing SDK Platform-Tools/license warning remains.

The initial sandboxed Gradle launch could not open the read-only persistent cache
lock; authorized execution with that existing cache succeeded. No toolchain,
dependency, license or signing configuration changed. Wear APK verified with:

```sh
JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9 \
$HOME/Android/Sdk/build-tools/36.0.0/apksigner verify --print-certs \
wear/build/outputs/apk/debug/wear-debug.apk
```

Certificate retains DEVELOPMENT.md's Day 1 SHA-256 fingerprint. The verifier's
Java native-access advisory is non-fatal. Changed/new text whitespace and Wear
strings XML parsing pass, including untracked new files. No mobile/shared code
changed, so mobile tests/build/lint were not rerun. No rendered emulator or
physical layout/interaction result is claimed.

Suggested commit: `fix: make Wear proof and settings screens readable on round displays`.


## Phase B — selected-model restoration diagnosis (2026-10-29)

**VERIFIED AUTOMATICALLY:** 15 new integration tests pass against unchanged
production code. **Historical Terra -> restart -> Sol: NOT REPRODUCED; root cause
UNRESOLVED.** No demonstrated failing boundary exists to justify a production
fix. This does not close the historical physical defect or the D3-B fix gate.
No physical retest is requested during D3-B. D3-C remains unstarted.

### Exact startup and restoration sequence

1. MainActivity obtains WatchAuthViewModel, ApiKeySettingsViewModel and
   InferenceViewModel through the existing Application container. Auth launches
   profiles.load(); settings launches keys.load(). Both are asynchronous; source
   order does not imply completion order. Initial profile state is disconnected;
   initial API state is unloaded with a placeholder ChatGPT-plan mode.
2. WatchProfiles decrypts/validates the saved vault under its mutex and publishes
   selected issued-client identity and generation. ApiKeyRepository independently
   decrypts the API vault and publishes persisted mode, mode revision and key
   revision. The installation host is separate Preferences DataStore metadata;
   model preferences are not in DataStore.
3. CredentialRouter combines both StateFlows on the Application scope. Usability
   is gated by loaded billing state plus a connected eligible profile for plan
   mode, or configured key for API mode. Profile loading is not required for API
   discovery. Temporary initial states do not write model preferences.
4. InferenceViewModel collects access-state changes: cancel operation, increment
   epoch, clear prepared catalog/UI state, then load models only when usable.
   Equal access states are deduplicated. Repeated loadModels calls while an
   operation is active are ignored; completed reloads rediscover normally.
5. InferenceSession obtains a current explicit-mode credential lease. Router's
   current() rereads the persisted mode and checks its revision. Session validates
   the lease before/after discovery. Provider parses the current account's plan
   models[].slug/display_name in service order, or API data[].id as slug.
6. After discovery, InferenceViewModel awaits router.selectedModel(lease): plan
   reads selectedModels[issuedClientId]; API reads its separate model field. It
   checks epoch after both asynchronous boundaries before publishing READY.
7. Exact saved-slug membership wins regardless of service order or display label.
   An absent or missing saved slug selects the first fresh service entry in UI
   state only. The saved preference is retained, not overwritten by that fallback.
   An empty catalog fails with NO_MODELS instead of inventing a selection.
8. WearShell renders the matching slug's display name/check mark. Its only model
   write callback is the explicit model button -> proof.select(model.slug).
   select() validates membership, awaits durable router/repository persistence,
   then publishes the choice if its epoch is current. There is no index-based,
   composition-triggered, default-selection or restoration writeback path.

Before/after: production sequence above is unchanged. Only regression coverage
and this evidence record were added. Credential formats, generation/revision
semantics, storage, OAuth, inference protocol, UI and capability routing remain
unchanged. DECISIONS.md is unchanged because no durable rule changed.

### Integration evidence and limits

ModelRestorationIntegrationTest reconstructs protected-record wrappers,
WatchProfiles, ApiKeyRepository, CredentialRouter, InferenceSession and
InferenceViewModel. The main scenario explicitly selects Terra via the ViewModel,
awaits durable save, closes the old scopes, reconstructs all objects, discovers
Sol first/Terra second, and verifies both selected slug and UI presentation.

The fixture uses production GcmRecord encryption with ciphertext files and new
record wrappers, production vault serialization and production OpenAiProvider
catalog parsing through synthetic OkHttp responses. A fixed test-only AES key
survives wrapper recreation; this does not emulate Android Keystore, AtomicFile,
actual process death, Compose interactions or provider network behavior. No
real record, key, account, token or endpoint request was used. Coroutine gates
control delayed discovery/preference completion without arbitrary sleeps.

New cases (15):

1. Explicit Terra selection, durable encrypted save and full reconstruction.
2. Terra/Sol and Sol/Terra fresh service order, without default writeback.
3. Repeated/concurrent reload invocation cannot replace Terra or write defaults.
4. Delayed profile restoration: no premature discovery.
5. Delayed billing restoration: no discovery under placeholder plan mode.
6. Delayed discovery plus repeated reload: no temporary selection/writeback.
7. Repeated startup load publications plus delayed preference read.
8. Saved model absent: deterministic first-entry fallback, preference retained,
   and restored again when it reappears.
9. No saved model: first-entry default, no persisted implicit choice.
10. Different issued client: no inherited Terra; returning client restores Terra.
11. Persisted API mode before profile load, then independent plan/API preferences.
12. Cancelled late preference callback cannot overwrite a new account selection.
13. Identical display labels/case-distinct slugs still use the exact saved slug.
14. Cancelled late discovery cannot overwrite a new account selection.
15. Synthetic sign-out/same-client reauthorization retains Terra.

Existing suites additionally cover stale generation/model writes, same-email
registration separation, invalid storage, billing revisions, late model results,
failed preference writes and lease validation. Together the 54 focused tests pass.
No temporary/default preference overwrite or identity normalization defect was
found. Absence of a reproduction under these schedules does not exclude an
untested lifecycle/interleaving or historical build/catalog/registration change;
none is asserted as the cause.

### Validation actually run

Using DEVELOPMENT.md's persistent JAVA_HOME, ANDROID_HOME, ANDROID_USER_HOME and
GRADLE_USER_HOME without changing tooling, dependencies or signing:

```sh
./gradlew :wear:testDebugUnitTest \
  --tests 'io.github.grassified.wristprompt.openai.ModelRestorationIntegrationTest'
./gradlew :wear:testDebugUnitTest \
  --tests 'io.github.grassified.wristprompt.openai.ModelRestorationIntegrationTest' \
  --tests 'io.github.grassified.wristprompt.openai.InferenceViewModelTest' \
  --tests 'io.github.grassified.wristprompt.openai.CredentialRouterTest' \
  --tests 'io.github.grassified.wristprompt.auth.WatchProfilesTest' \
  --tests 'io.github.grassified.wristprompt.auth.ApiKeyRepositoryTest' \
  --tests 'io.github.grassified.wristprompt.openai.InferenceSessionTest'
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:lintDebug --console=plain
git diff --check
```

Final focused run: 54 tests (15 new integration + 39 existing), zero failures,
errors or skips. Full Wear suite: 204 tests, zero failures/errors/skips. Debug
assemble and lint: PASS (build output up-to-date where unchanged). Lint retains
five existing warning instances, zero errors. Mobile/shared files are unchanged;
mobile tests/build/lint were not rerun and prior counts are not new evidence.
The first test-authoring run failed JUnit initialization due to non-void Kotlin
expression-body test methods; explicit Unit return types corrected the fixture.
No production failure was demonstrated by that initialization error. An initial
sandbox launch could not write the persistent Gradle cache lock; authorized
execution with the same cache succeeded.

No emulator/device operation, CapabilityClient experiment, real inference,
credential operation, commit or push. Emulator auth remains NOT VERIFIABLE ON
CURRENT EMULATOR LAB; its investigation was not reopened. Keep the existing
consolidated acceptance policy, with no new routine physical-test requirement.

Suggested commit: `test: cover model restoration across encrypted startup reconstruction`.
