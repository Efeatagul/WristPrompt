# Day 3 status — 2026-10-29

**D3-G final result: PASS WITH KNOWN DEBT.** D3-A through D3-F development and
consolidated automated/emulator acceptance are complete. Current exact results,
all 23 scenario dispositions, security review, known debt and Day 4 handoff are in
[DAY3_REPORT.md](DAY3_REPORT.md). Wear 272 / mobile 70 / Python 8 tests pass;
both debug/release builds, lint and release isolation pass. Physical RC remains
open; post-D3 targeted physical PASS is recorded below, while funded BYOK success
is unverified. D3-G Data Layer smoke was environment-blocked because both
required AVDs were not available.

The phase sections below preserve dated implementation/handoff evidence. Their
“unstarted”, “pending” and prior counts describe that phase's handoff, not today's
final status. The earlier end-of-day hardware checklist is now batched into final
physical RC under the current acceptance instruction; it is not a new Day 3 gate.
No production change, commit/push or Day 4 work in D3-G.

## POST-D3 PHYSICAL SANITY ACCEPTANCE — 2026-10-29

Owner-reported targeted sanity pass on a physical Galaxy Watch 7 and physical
Android phone, using current Day 3 debug builds, the existing compatible signing
identity, real ChatGPT-plan authentication and real OpenAI network inference.
These are supplied physical results, not device tests executed by this
documentation update. Full RC physical acceptance is not claimed.

| Check | Result |
| --- | --- |
| 1. APK update/install on both devices | PASS — PHYSICAL |
| 2. Real Wear Data Layer / Continue with ChatGPT authentication handoff | PASS — PHYSICAL |
| 3. Real account-specific model discovery and normal restoration/relaunch | PASS — PHYSICAL |
| 4. Arbitrary multi-turn chat | PASS — PHYSICAL |
| 5. Incremental streaming response | PASS — PHYSICAL |
| 6. Stop/cancellation behavior | PASS — PHYSICAL |
| 7. Cancelled-exchange exclusion from a subsequent request's actual server-side context | NOT PHYSICALLY EXERCISED — COVERED BY AUTOMATED/EMULATOR ACCEPTANCE |
| 8. New Chat behavior | PASS — PHYSICAL |
| 9. Model-change conversation-context boundary | NOT PHYSICALLY EXERCISED — COVERED BY AUTOMATED/EMULATOR ACCEPTANCE |
| 10. Offline/error recovery | PASS — PHYSICAL |

Owner observations confirm real messages/responses, long-response rendering and
scrolling, partial output shown as unfinished/excluded after Stop, return to
Compose, and a subsequent request working normally. Offline/network recovery
worked after connectivity returned without unintended sign-out or credential
loss. Owner-reported real-watch screenshots support usable round-screen chat /
streaming presentation; no independent screenshot inspection is claimed here.
Visible exclusion labeling and successful follow-up do not establish check 7.

**DEFERRED — FINAL RC:** checks 7 and 9 still need physical exercise; final
sign-out/reauthorization and remote-revocation outcome, rotary, larger fonts,
process death, background/screen-off and other lifecycle/network/storage/signing
checks remain open. Successful funded BYOK inference remains unverified; no
purchase is required. Current debug sanity results do not establish release-build
or full RC acceptance.

Normal restoration in this pass does not resolve the historical Terra -> Sol
physical anomaly: it remains unreproduced/unresolved. The transient reauthorization
anomaly also remains unresolved. The CapabilityClient limitation remains isolated
to the current emulator/GMS environment; real handoff success does not repair or
reclassify that lab. Day 3 remains PASS WITH KNOWN DEBT. Day 4 is not started.

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

## Historical end-of-day checklist — deferred to final physical RC

- [ ] DEFERRED FINAL PHYSICAL RC — On Galaxy Watch 7, check circular-screen
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
- Native text input/in-memory multi-turn chat: D3-C foundation implemented below.
- Streaming/Stop ownership: D3-D evidence below. Live-network and consolidated
  physical acceptance remain separate; no routine physical test is requested.
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
fix. This does not establish physical resolution. D3-B is complete as an
investigation/test-hardening phase with the anomaly retained as debt. No physical
retest was requested during D3-B. D3-C had not started at that handoff.

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


## Phase C — arbitrary Wear text chat and in-memory context (2026-10-29)

**VERIFIED AUTOMATICALLY:** normal arbitrary text reaches Responses with explicit
multi-turn context. **VERIFIED ON EMULATOR (partial acceptance):** chat/composer
entry, system keyboard entry using on-screen keys, returned draft review and
cancel-to-composer behavior. No real inference or physical-device evidence is
claimed. D3-D was unstarted at the D3-C handoff; the section below records its
current evidence. The D3-B investigation is not reopened;
historical Terra -> Sol remains unresolved physical debt.

### Architecture and context rules

- InferenceViewModel owns one ChatConversation and immutable ChatState. Existing
  model restoration, credential router and shared session remain authoritative.
- ChatMessage carries local ID, role, text and pending/completed/excluded status.
  Partial assistant text is separate from completed assistant messages. Old failed
  user turns stay visibly marked excluded even after a later request completes.
- Visible user text is appended once at Send. Only a completed, current-lease
  exchange commits its user item and assistant output items to replay history.
  Opaque encrypted reasoning and assistant phase are retained in service order,
  never rendered as reasoning or logged. Failed, stopped, incomplete and interrupted
  exchanges are excluded from future history; no fabricated assistant or retry.
- New Chat clears draft, messages and history. Distinct account/access-state,
  credential-generation, billing mode/revision and selected-model changes clear
  context. Same-model reload preserves it. A model fallback after failed catalog
  reload also clears context. Late input results carry a context revision and
  cannot restore a draft into a different account/model/New Chat context.
- Conversation content is never persisted. Activity ViewModel destruction or
  process death may clear it; ordinary retained-ViewModel recreation keeps it.
- Prompt limit: 8192 UTF-16 code units. Limit: 12 submitted attempts, including
  failures. Explicit serialized replay/input threshold: 192 KiB UTF-8; after the
  last bounded completion crosses it, show the completed exchange and require
  New Chat. No automatic truncation, eviction, compaction or server continuation.
  Completed replay output is at most 256 KiB; total replay remains bounded by
  the input threshold plus one completion. D18 records the durable rule.

### Proof-to-chat and UI flow

Chat is the default surface. Billing stays in a compact measured header; current
model, message roles and statuses are readable in the scrolling conversation.
Compose is outside scrolling content. Composer uses Write/Replace draft to open
the platform Wear RemoteInput/keyboard, followed by full scrollable draft review
and a separate pinned Send button. Blank/whitespace/oversized input cannot Send.
Cancellation retains the previous draft. Local composition is available before
sign-in, while Send still requires the existing usable account/model gate.
Missing system input activity reports a static message without raw exceptions.
Platform keyboard voice options are platform behavior, not an added app feature.

New Chat and connection/model actions are reachable from the chat list. The
model picker now includes Reload models for direct recovery without the proof
screen; Send requires a selected model as well as the existing credential gate. Different
models start a new conversation. Existing settings, plan confirmation, usage
management and proof surfaces remain reachable. The fixed proof lives behind
Details -> Inference proof. It still tests exact output; normal chat completion
uses response.completed without requiring the proof sentence.

AiProvider/OpenAiProvider gain an explicit-input overload. InferenceSession shares
the existing lease, catalog, billing-revision, SSE and cancellation validation for
both proof and chat. ResponsesProtocol serializes the supplied history with only
model/input/store/stream and retains completed output items for replay. No
previous_response_id or new endpoint. OpenAiHttp, credential formats/stores,
CredentialRouter, OAuth and Data Layer routing are unchanged. StreamResult gains
bounded completed-output retention; no new SSE framing or Stop transport design.

### Automated evidence

New tests: **28** across ChatConversationTest (13), ChatViewModelTest (12) and
ChatTransportTest (3). They cover arbitrary/Unicode/long prompts; empty/whitespace
rejection; first/second-turn payload/order; exactly-once messages and duplicate
submit; completed-only history; failure/Stop/incomplete behavior; New Chat;
account/billing/generation/revision resets and late results; model changes and
failed-reload fallback; preserved restoration; process reconstruction; both-mode
HTTP routing/no fallback; replayed reasoning/phase; context limits; redaction;
and rejection of tool/non-assistant/incomplete/unreplayable output items.

Final focused gate: **84 tests**, zero failures/errors/skips. Includes the new
chat tests and existing InferenceSessionTest, InferenceViewModelTest,
ModelRestorationIntegrationTest, OpenAiProviderTest, ResponsesProtocolTest and
SseReaderTest. Final full Wear suite: **232 tests**, zero failures/errors/skips.
:wear:assembleDebug and :wear:lintDebug: PASS, zero lint errors and the same five
warning instances (two AGP advisories, ExportedService, VectorPath, WearRecents).
No suppression, dependency, SDK/license or signing change. No shared/mobile code
changed; mobile tests/build/lint were not rerun and prior results are not new
physical or automated evidence for this phase.

Commands run using DEVELOPMENT.md's persistent JDK/SDK/cache/signing environment:

```sh
./gradlew :wear:compileDebugKotlin --console=plain
./gradlew :wear:testDebugUnitTest \
  --tests 'io.github.grassified.wristprompt.openai.Chat*Test' \
  --tests 'io.github.grassified.wristprompt.openai.InferenceSessionTest' \
  --tests 'io.github.grassified.wristprompt.openai.InferenceViewModelTest' \
  --tests 'io.github.grassified.wristprompt.openai.ModelRestorationIntegrationTest' \
  --tests 'io.github.grassified.wristprompt.openai.OpenAiProviderTest' \
  --tests 'io.github.grassified.wristprompt.openai.ResponsesProtocolTest' \
  --tests 'io.github.grassified.wristprompt.openai.SseReaderTest' --console=plain
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:lintDebug --console=plain
git diff --check
```

Also assembled Wear separately during layout/input iterations and checked new-file
whitespace/final newlines. Early test authoring caught a fixture using copy() on a
non-data CredentialLease, then an incorrect loop assertion retaining the prior
iteration's conversation; those fixture issues were corrected before final gates.
All real HTTP adapters in tests use synthetic interceptors; no provider request
or real credential was used.

### Emulator acceptance and evidence boundaries

Used only existing WristPrompt_Watch7, verified by AVD name and watch emulator
characteristics before APK update. Started the existing AVD headlessly with
-no-snapshot-save, updated in place with install -r using the existing debug key;
no uninstall, data clearing, re-pair, AVD recreation, capability probe or physical
operation. A physical device appeared in the initial inventory and was never
selected. An elapsed emulator timer overlay interrupted one screenshot; that
capture is not accepted as chat/layout evidence.

VERIFIED ON EMULATOR: compact round-screen chat header, Compose -> composer,
visible pinned disabled Send without authentication, initially visible Write
message, platform text-input choices and keyboard, on-screen key entry returning
text to Replace draft/review state, and cancellation returning to composer.
Hardware-key text injection displayed text in Gboard but did not return it to
RemoteInput; actual on-screen key taps did return a draft. This is recorded as an
input-automation limitation, not a claimed app/provider/capability defect.

Not verified on emulator: enabled Send, submitted user/assistant conversation
rendering, follow-up responses and real multi-turn inference. This lab has no
usable credential; production capability-dependent auth remains NOT VERIFIABLE
ON CURRENT EMULATOR LAB. Those positive state/transport paths are tested
synthetically on JVM, not claimed as emulator results. Long returned drafts,
full conversation scrolling, rotary and New Chat interaction require later
consolidated acceptance; their domain/state behavior is covered automatically.
No new debug scenario injection/harness was added ahead of D3-F.

### Scope/debt retained

D3-D still owns detailed incremental presentation races, Stop semantics and
cancellation-generation hardening, scroll-follow behavior and consolidated
streaming acceptance. This phase only reuses existing cancellation and epoch/
lease checks necessary to protect chat context. No voice, image, tools, MCP,
provider, database, sync, persistent history or authentication/billing redesign.

The historical Terra -> Sol observation is not fixed or disproved. Reauthorization
anomaly and funded-BYOK physical-success constraint remain unchanged. No routine
physical test was requested, milestone checked off, commit made or push performed.

Suggested commit: `feat: add Wear text chat with bounded in-memory conversation context`.


## Phase D — streaming, Stop and request races (2026-10-29)

**VERIFIED AUTOMATICALLY:** 249 Wear tests pass, including 17 new streaming/race
and integrated HTTP-cancellation tests. **VERIFIED ON EMULATOR:** synthetic
streaming/Stop/clean follow-up through the actual ViewModel/session and shared
chat renderer. No production authentication, live OpenAI inference or physical
behavior is inferred. D3-E is unstarted. No commit or push.

### Baseline findings and corrections

The first new StreamingRaceTest run against committed D3-C reproduced four
failures (11 tests, four failed): idle Stop mutated proof state; independent
session generation callers overlapped; Stop -> immediate Send entered the next
provider before the cancelled provider exited; duplicate completion callbacks
turned a completed result into a failure. D3-C's epoch rejection already protected
many UI late-result cases, and its HTTP cancellation adapter already worked.
Do not misreport those existing protections as newly implemented or broken.

- InferenceViewModel now assigns a lazy coroutine Job before starting it, keeping
  operation ownership valid under immediate/reentrant execution. Epoch remains
  the existing request identity. Duplicate Send stays blocked synchronously.
- Stop while idle is a no-op. Active Stop increments the epoch, cancels the Job,
  marks the chat cancelled and its user turn excluded, and retains partial text.
  It no longer changes unrelated idle proof state when stopping chat.
- InferenceSession registers queued and active generation Jobs, serializes
  generation under one mutex, and checks the captured owning Job around lease
  checks, event acceptance, progress and final publication. NonCancellable fake
  callbacks cannot bypass cancellation. Both active and queued work respond to
  session/account cancellation. A following prompt can enter STARTING immediately
  but waits for old provider/transport cleanup before entering its provider.
- First completed terminal wins; later provider callbacks cannot duplicate text,
  finalize a second assistant message or replace completion with a failed event.
  The actual provider already stops parsing at its first terminal event.
- Whole completed exchanges still enter context exactly once. Failed, incomplete,
  interrupted and cancelled exchanges stay excluded. Stop -> B tests verify B's
  input contains only B when no earlier completed exchange exists.

### Stream/event and transport audit

response.created/in-progress, reasoning/item notifications and unknown well-formed
non-text events are informational and do not append text or establish completion.
They no longer trigger unnecessary UI progress publication. Completed output items
and opaque replay data remain retained from response.completed as in D3-C.

Ordered output_text.delta events append once, including legitimately repeated
text chunks. No content-based deduplication would be correct for repeated words.
Only response.completed establishes success. response.failed preserves safe error
categories; incomplete, malformed/type-conflicting events, premature EOF and IO
interruption remain failures with partial output retained. Cancellation remains
CancellationException, rather than being converted to a network error.

OpenAiHttp, OpenAiProvider, ResponsesProtocol, SseReader and ChatConversation are
unchanged in this phase. The existing HTTP await finally calls Call.cancel()
while the IO child may be blocked reading; response use closes the body before
the session releases its generation lock. Integrated tests use the actual OkHttp
Call with synthetic blocking/intercepted bodies, not a real OpenAI connection or
physical radio. They confirm cancelled Call state, unblocked read, closed body,
retained partial state and a successful next prompt in both billing modes. They
also test Stop before response headers arrive. No rotating-token behavior changed.

### Wear rendering and performance

Stop remains outside scrolling content while busy, disappears afterward and
requires no navigation to reach. Long partial output remains chunked/scrollable.
No auto-scroll or scroll-follow infrastructure was added; user scrolling is not
continually overridden. Per-delta state updates retain the same message-list
instance (verified over 2000 identical text deltas). Completed-message chunks are
cached by that list, avoiding repeat full-history text splitting on every delta.

The first cache implementation introduced a renderer race: deferred lazy content
read a newer delegated ChatState while holding an older chunk map. The emulator
reproduced a NoSuchElementException at ChatItems' cache lookup. Both production
and preview now capture immutable renderedChat alongside the matching cache.
Repeated streaming/Stop/follow-up acceptance after this correction no longer
crashed. This is an emulator-discovered and repaired D3-D integration defect,
not a claimed prior D3-C defect. No raw crash messages/payloads were printed;
inspection emitted exception classes and app frame names only.

### Tests and final automatic results

New StreamingRaceTest: 13 tests covering idle Stop, immediate next send and
single-provider occupancy, late delta/completion/failure after Stop, stale errors,
multiple ViewModel owners, duplicate Send/pre-dispatch Stop, duplicate terminal,
partial failures, 2000 ordered repeated deltas with unchanged history-list
identity, New Chat epochs, NonCancellable callbacks, queued cancellation and
ViewModel disposal.

New StreamingHttpCancellationTest: four tests covering actual Call cancellation/
blocked read/body closure/clean B request in both modes; Stop before headers;
EOF/malformed/malformed terminal/incomplete/server-failed SSE after partial text;
and lifecycle events plus unread trailing events after completion. Existing D3-C
context, D3-B restoration, Day 2 credential/billing/cancellation and protocol
suites remain passing.

Final focused gate: **85 tests**, zero failures/errors/skips. Full Wear suite:
**249 tests**, zero failures/errors/skips. :wear:assembleDebug, :wear:lintDebug and
:wear:processReleaseMainManifest PASS. Lint: zero errors, five unchanged warning
instances (two AGP advisories, ExportedService, VectorPath and the existing debug
DataLayerDiagnosticsActivity WearRecents warning). The new preview uses noHistory
with excludeFromRecents and adds no final lint warning. The merged release
manifest was explicitly checked and excludes StreamingPreviewActivity.

Commands run with DEVELOPMENT.md's persistent JDK/SDK/cache/signing environment:

```sh
./gradlew :wear:testDebugUnitTest \
  --tests 'io.github.grassified.wristprompt.openai.StreamingRaceTest' --console=plain
./gradlew :wear:testDebugUnitTest \
  --tests 'io.github.grassified.wristprompt.openai.Streaming*Test' \
  --tests 'io.github.grassified.wristprompt.openai.Chat*Test' \
  --tests 'io.github.grassified.wristprompt.openai.Inference*Test' \
  --tests 'io.github.grassified.wristprompt.openai.ApiKeyCancellationTest' \
  --tests 'io.github.grassified.wristprompt.openai.OpenAiProviderTest' \
  --tests 'io.github.grassified.wristprompt.openai.SseReaderTest' --console=plain
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:lintDebug \
  :wear:processReleaseMainManifest --console=plain
git diff --check
```

Also assembled during debug-preview iterations and checked new text files for
trailing whitespace and final newlines. No shared/mobile implementation changed;
mobile tests/build/lint were not rerun and previous results are not current-run
evidence. No dependency, signing identity, SDK/license or storage change.

### Debug-only emulator acceptance

Used existing WristPrompt_Watch7, confirmed by AVD name and emulator/watch
characteristics before install -r. Only WATCH_SERIAL was targeted. Physical
devices were not selected. No uninstall/data wipe/re-pair/AVD recreation/capability
probe. Earlier returned-clock captures followed preview renderer crashes and
were discarded; they are not attributed to GMS or the capability limitation.

StreamingPreviewActivity is an explicitly launched standalone debug activity.
It constructs a synthetic lease, fake in-process provider and real
InferenceViewModel/InferenceSession; it shares ShellList/chatItems with production.
No Application credential store, real key, OAuth, bridge or network client is
used. It accepts no external prompt/scenario extras. It keeps only its own visible
window awake and is excluded from release. Its provider deliberately attempts a
late completion 1.2 seconds after cancellation; owning-Job rejection prevents it
from becoming a visible completed message.

VERIFIED ON EMULATOR: incremental long output, manual scrolling with fixed Stop
reachable, mid-stream Stop, stopped/excluded state, and a following short response
completed with input items: 1 (cancelled exchange omitted), without a stale late
completion replacing it. A further stopped partial remained visibly marked
excluded after waiting past the 1.2-second late callback. Activity.recreate kept
the completed conversation/response without starting another generation.
Screenshots/UI hierarchies contain synthetic text only.
This verifies shared rendering and actual VM/session behavior with a fake source;
it does not verify real HTTP transport/radio cancellation on a device. Actual Call
cancellation has separate automated evidence above.

Production capability-dependent authentication remains NOT VERIFIABLE ON CURRENT
EMULATOR LAB. No production emulator fallback was added. No routine physical test
is requested. Real-service latency/network loss and physical touch/rotary behavior
remain for consolidated acceptance.

### Retained debt / boundaries

D3-E is not started. No OAuth, billing, credential format/store, Data Layer,
provider feature, attachment, voice, web/tool/MCP or persistent-history work.
Historical Terra -> Sol remains unresolved physical debt; reauthorization anomaly,
funded-BYOK physical-success constraint and emulator capability limitation remain.
Stop cannot promise zero provider-side usage after local cancellation. A next
request may remain STARTING while bounded previous cleanup/refresh finishes;
there is no automatic retry or overlap to hide that wait.

Suggested commit: `fix: harden chat streaming ownership and Stop races`.

## Phase E — chat/auth/model/billing/error transitions (2026-10-29)

**VERIFIED AUTOMATICALLY:** 263 Wear tests, zero failures/errors/skips (249 D3-D
baseline + 14 new tests). D3-E only. No commit/push, physical-device operation,
credential migration, production Data Layer change or D3-F harness work.

### A. Explicit transition matrix

These are meaningful domain states, not independent persisted flags. Existing
immutable ChatStage/ProofStage, repository access state and owning coroutine Job
remain authoritative; no second state machine or saved busy flag was introduced.

| Domain | States and allowed transitions | Forbidden combinations / enforcement |
| --- | --- | --- |
| Authentication | Signed out/unusable -> explicit watch setup pending -> validated authenticated profile. Authenticated -> serialized refresh -> renewed usable profile, transient retry gate, or reauth required. Explicit sign-out -> signing out -> signed out with revocation outcome. | UI connection alone never authorizes inference: current lease, account generation, mode/revision and plan permission must pass. Pending setup, storage failure, unusable/uncertain grants and sign-out cannot dispatch. |
| Billing | Persisted ChatGPT plan <-> API key only through explicit selection; every selection revises mode ownership. | No error/credential absence selects another mode. Catalog, selected model and replay context cannot cross an incompatible revision, even when switching back to the same mode. |
| Models | No catalog -> loading -> fresh catalog + restored exact slug, or deterministic first current entry if saved slug is absent; empty discovery -> NO_MODELS; discovery failure -> FAILED with no selection. | Old catalog cannot publish after account/mode/epoch changes or after its lease becomes stale during preference read. A model removed during bearer rediscovery or explicitly rejected as model_not_found invalidates selection; reload is required before another Send. No implicit preference writeback. |
| Chat | Empty -> composing -> starting -> receiving -> completed, failed or cancelled. Starting may directly complete/fail/cancel. Editing a terminal draft -> composing; New Chat -> empty. | Only a current completed whole exchange enters replay. Cancelled/failed/incomplete attempts cannot later become completed. Draft editing clears the old error category; it never sends. |
| Request | Idle -> starting/queued -> active -> terminal; Stop/access invalidation -> cancelling/cleanup -> terminal. New work may queue while old cleanup finishes. | One provider generation under the session mutex. Terminal chat completion is published only after provider return/resource cleanup and final lease validation. Visible Stop/STARTING may end immediately on cancellation while old transport cleanup finishes; no new provider overlaps it. |
| Error/recovery | A safe error is terminal for that attempt. Explicit compose/send or reload can recover when guards permit. Cancellation is a separate terminal state. | No automatic generation replay, OAuth loop, billing fallback, raw response display, or busy state left behind by shared-session cancellation. |
| Recreation | Activity recreation retains its ViewModels and active work. New ViewModel/process starts with empty chat/idle request and reloads protected settings/catalog. | No SavedStateHandle/chat persistence, restored busy flag, recreated request, duplicate message, or old callback into a new conversation owner. UI-local navigation/input launcher metadata may reset; a lost keyboard result is not automatically resent. |

Authentication detail: same-account **replacement** and different-account selection
increment repository generation, invalidating model/context/work. Ordinary successful
bearer rotation preserves the access identity/generation and conversation; the
session rediscovers with the renewed bearer before inference. Transient refresh
503/429 and definitely-unsent IO preserve the token tuple and use the existing
cooldown. An ambiguously transmitted rotating grant remains unusable under the
existing durable refresh-intent rule; this is not ordinary transient sign-out and
was not redesigned. Terminal refresh errors retain registration identity for
reauthorization. Pending/failed/expired phone setup remains owned by existing auth
ViewModels, never reconstructed from chat state.

### B–C. Concrete findings and hardening

Code inspection identified these gaps; the new deterministic tests verify their
corrected behavior. No claim that they caused the historical physical anomalies.

1. Shared InferenceSession cancellation previously rethrew from proof/catalog
   operations without ending LOADING/STREAMING. Current-epoch cancellation now
   publishes CANCELLED, retains partial proof text and allows explicit recovery.
   Stop's own epoch and newer operations still reject obsolete cancellation.
2. Chat rejected a missing selected slug after token renewal, but kept the old
   ready/selected catalog visible. NO_MODELS now invalidates prepared catalog,
   selection and Ready status for both chat/proof. Reload restores a deterministic
   current model. Replay remains dormant until reload; switching to a different
   restored model clears it under D3-C. Same-model reload preserves it.
3. Lease validity was checked after discovery but not after the asynchronous saved
   preference read. InferenceSession.validate rechecks the lease and cancellation
   before publication. Catalog provider boundaries also explicitly check owning
   coroutine cancellation.
4. Access collectors can be queued behind a UI action. Send/test/selection now
   synchronously compare the latest access StateFlow's usable/client/generation/
   mode/revision against the prepared lease. No old user turn is appended while
   awaiting the access collector; session's durable checks remain mandatory.
5. Auth-required/stale/storage/key-missing failures now invalidate prepared model
   state and clear incompatible replay even if repository notification has not yet
   arrived. This touches no stored credentials. Transient request failures keep
   valid completed context and model selection.
6. Exact model_not_found now maps to existing NO_MODELS instead of generic invalid
   request. Chat has short class-specific recovery guidance through existing
   navigation; editing a draft removes the old error category.
7. Emulator scrolling exposed list content drawing over the fixed action button.
   ShellList now clips to its allocated viewport. Longer recovery guidance uses
   the existing lossless independently scrollable text chunks. No navigation or
   round-screen redesign.

### D–F. Error mapping, context and recovery

| Evidence / category | Safe presentation | Explicit recovery; credential effect |
| --- | --- | --- |
| Offline/pre-stream IO: NETWORK | Network error | Restore connection, compose/send again; no credential deletion. |
| Mid-stream IO/EOF: INTERRUPTED | Stream interrupted; partial excluded | Compose another attempt; no replay of unfinished exchange. |
| No usable plan authentication: REAUTHORIZE; missing API key: API_KEY_MISSING | Authentication required / API key not configured | Connection/settings -> sign-in or API setup for the selected mode. No fallback. |
| Terminal/uncertain plan renewal: REAUTHORIZE; pending setup: REAUTHORIZING | Authentication required / finish sign-in on phone | Existing watch setup with retained registration. No automatic OAuth retry. |
| API 401: INVALID_API_KEY | API key rejected; no fallback | Configure/replace the API key explicitly. Never tries plan credentials. |
| ChatGPT admission 401: PERMISSION | Permission not accepted | Check selected account and plan permission. A bare inference 401 alone does not delete credentials or establish terminal refresh failure. Never tries API credentials. |
| 429 inference: USAGE_LIMIT / API_LIMIT | ChatGPT usage limit / API rate or credit limit | Existing Manage usage for plan; check API usage/credits for key. No invented reset time or automatic retry. |
| Generic 5xx: TEMPORARY; specific usage-unavailable code retained | Temporarily unavailable / usage unavailable | Retry later explicitly; preserve credentials and completed context. |
| Empty/missing selected model or model_not_found: NO_MODELS | No models available | Choose model -> Reload models. No Send until a current selection exists. |
| Cancellation | Stopped, partial/excluded; no ordinary failure | Compose/send again or New Chat. Cleanup serialization and duplicate guards still apply. |
| Malformed/unexpected response: PROTOCOL; INCOMPLETE distinct | Response could not be processed / response incomplete | Safe generic category, no raw body or exception text. Explicit fresh attempt; unfinished exchange excluded. |

Existing specific eligibility, configuration, plan-disabled and protected-storage
categories remain intact. API-specific errors never advertise ChatGPT fallback.
No extra buttons or automatic retry were introduced. Existing Compose, Choose
model/Reload, Connection/settings, Manage usage and New Chat remain the recovery
paths. Storage failure requires recovery of protected storage; New Chat cannot
bypass it. Cancelled and ordinary failures do not become completed context.

D3-C/D3-D invariants retained: clear on distinct account/access state, generation,
mode/revision, actual selected-model change/fallback and New Chat. Preserve on
ordinary retry, transient service failure, same-model reload and retained-VM
Activity recreation. Late callbacks fail owning-Job/current-lease/epoch checks and
cannot repopulate cleared context. Process death intentionally loses messages,
drafts and replay; persisted credential/model preferences restore independently.

### G–H. Files, tests and exact automated evidence

Production:
- openai/InferenceViewModel.kt — access guards, terminal cancellation, model and
  incompatible-context invalidation, delayed preference validation.
- openai/InferenceSession.kt — reusable lease validation and catalog cancellation.
- openai/ApiFailure.kt — exact model_not_found classification.
- openai/ChatConversation.kt — remove stale error when editing a draft.
- ui/ChatItems.kt — safe recovery guidance using existing scrollable chunks.
- ui/WearShell.kt — clip list viewport below fixed controls.

Tests:
- New TransitionTest.kt: 12 tests (several table-driven), controlled gates/Main
  test scheduler, no sleep-based races. Covers shared catalog/proof/chat cancellation
  and recovery, missing model after refresh, delayed-preference lease invalidation,
  pre-collector access changes, reauth/context clearing, transient recovery,
  cancelled-owner reconstruction, stale draft error, model mapping, and active
  generation/access/revision invalidation with late completion attempts.
- New ErrorTransitionTest.kt: 2 tests. Real OpenAiProvider/OpenAiHttp with synthetic
  intercepted responses exercises 401/429/503/IO/malformed SSE in **both** modes,
  single-call/no-fallback and sanitized failure checks, plus recovery guidance.
- ApiKeyProviderTest.kt updates one expected model_not_found category to NO_MODELS.
  Existing routing/record-immutability, refresh, D3-B restoration, D3-C context and
  D3-D race/HTTP cancellation tests remain passing.

Documentation: DAY3_STATUS.md and durable rule D20 in DECISIONS.md. ROADMAP.md
unchanged: phase evidence belongs here; no Day 3 physical/release gate checked.

Commands actually run using DEVELOPMENT.md's unchanged persistent environment:

```sh
export JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android
export GRADLE_USER_HOME=$HOME/.gradle
./gradlew :wear:testDebugUnitTest --tests '*TransitionTest' --console=plain
./gradlew :wear:testDebugUnitTest --tests '*openai.*' --tests '*auth.*' --tests '*ui.*' --console=plain
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:lintDebug \
  :wear:processReleaseMainManifest --console=plain
git diff --check
```

First transition-only run: 10 tests PASS, before two additional cancellation/
invalidation tests were added. Initial broader run: 233 tests, one failure from
an existing model_not_found expectation; updated for the intentional mapping.
Final focused gate: **235 tests**, zero failures/errors/skips. Final full gate:
**263 tests**, zero failures/errors/skips. Wear assembleDebug/lintDebug and release
manifest processing PASS. Lint: zero errors, five unchanged warning instances
(two AGP advisories, ExportedService, VectorPath, WearRecents). Release manifest
excludes StreamingPreviewActivity. No suppression/dependency/toolchain/signing
change. Sandbox could not acquire the persistent cache's read-only lock; approved
execution with the existing cache succeeded. No mobile/shared code changed, so
mobile tests/build/lint are NOT RERUN, not claimed as current evidence.

Changed/new UTF-8 text checked for trailing whitespace and final newlines in
addition to git diff --check. Tests use synthetic credentials and intercepted
transport only, with no provider/service requests or raw authenticated logging.

### I. Emulator acceptance — current D3-E run

Used only existing **WristPrompt_Watch7**, verified with WATCH_SERIAL's AVD name
and `emulator,nosdcard,watch` characteristics. Updated in place with `adb -s
WATCH_SERIAL install -r` using the existing debug signing identity. No uninstall,
data clear, re-pair, AVD recreation, physical target, CapabilityClient probe or
real authentication request. ADB required approved host access because the
sandbox could not connect to its local socket. Phone emulator not needed.

| State/check | Current evidence |
| --- | --- |
| Signed out / no selected model | VERIFIED ON EMULATOR — actual MainActivity shows Using ChatGPT plan, Model: Not selected, Authentication required and Compose; no busy/Stop. |
| Authenticated-like synthetic state | VERIFIED ON EMULATOR — existing standalone preview discovers its fixed synthetic model through the actual VM/session. This does not exercise production authenticated account/settings UI or credential storage. |
| Models loading | NOT EXERCISED as a rendered state; deterministic JVM coverage only. |
| Model unavailable / reauth required | NOT EXERCISED on emulator; deterministic JVM coverage only. |
| Streaming / stopped | VERIFIED ON EMULATOR — fixed Stop while synthetic generation runs; stopped partial output stays marked excluded after the late-callback interval. |
| Recovery guidance / round readability | VERIFIED ON EMULATOR — unfinished-context explanation and Compose guidance readable by scrolling; no content draws above the viewport into the fixed button after clipping fix. |
| Recreation after Stop | VERIFIED ON EMULATOR — existing Recreate screen action retained stopped/unfinished state and Next prompt, with no revived Stop or generation. |
| Clean completion after Stop/recreation | VERIFIED ON EMULATOR — Next prompt completed; rendered answer says input items: 1, so cancelled exchange was excluded. No old completion replaced it. |
| API-key 401 / 429 / 503 / offline | NOT EXERCISED on emulator; real HTTP adapter with synthetic intercepted failures covered automatically in both billing modes. |
| Long error text / error-specific recovery navigation | NOT EXERCISED on emulator. Existing preview has no error scenarios; no D3-F harness added. Longer general recovery guidance/partial output scrolling is verified above. |
| Real production auth/inference | NOT EXERCISED; capability-dependent authentication remains NOT VERIFIABLE ON CURRENT EMULATOR LAB. |

Current-run synthetic screenshots/UI hierarchy checks were kept under /tmp
(d3e-final-streaming.png, d3e-final-stopped.png, d3e-cancelled-recreated.png,
d3e-recovered-status.png). Initial pre-fix scrolling captures demonstrated content
over the fixed button and are not accepted as final layout evidence. No physical
verification is claimed. Existing consolidated end-of-day physical checklist stays
pending; no routine physical testing was requested or performed.

### J–L. Security, remaining debt and phase boundary

- Explicit routing and no credential fallback retained; router/repositories unchanged.
- No credential-format/storage/encryption/signing or production Data Layer change.
- Current generation, mode revision, operation epoch, owning Job and transport
  cancellation checks retained. store=false and explicit completed-only request
  history retained. No prompt/token/header/raw-response/unsanitized exception logging.
- No inference request fields, endpoint, provider, feature, attachment/image/voice,
  web/tool/MCP integration or persistent chat history added. Test/debug mechanisms
  remain synthetic and separate from real credentials; preview excluded from release.
- Historical Terra -> Sol physical restoration anomaly remains UNRESOLVED debt;
  not reproduced, no claimed fix. All 15 existing restoration tests still pass.
- Transient reauthorization exchange/context-expiry anomaly remains UNRESOLVED;
  this phase does not establish its cause or resolution. Day 5 disposition remains.
- Real successful funded-BYOK verification remains BLOCKED by no funded credential;
  no purchase/key/device test requested. Synthetic success and failure routing pass.
- Emulator CapabilityClient limitation unchanged; investigation not reopened.
- Remaining acceptance: real network/device transitions, full error-state round
  rendering, rotary/font-scale checks and production auth with a consenting account
  remain separate from automatic/synthetic evidence. Local Stop cannot promise zero
  server-side usage; a next request can wait for existing bounded cleanup/refresh.

**D3-F deferred:** debug scenario harness, expanded emulator scenarios and scenario
automation remain unstarted. Existing preview was used without modification.
D3-E completion does not authorize D3-F or establish a Day 3 physical/release gate.

### M. Suggested commit

`fix: harden chat auth model billing and error transitions`

No commit or push performed.

## Phase F — debug scenarios and emulator acceptance automation

**D3-F implemented — VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.** This is
phase-local evidence, not D3-G consolidated acceptance or a physical release gate.
D3-G remains unstarted. No commit/push, production auth, funded API request,
physical-device test, AVD reset/re-pair or capability investigation was performed.

### Harness architecture and security boundary

One debug entrypoint, `openai.DebugScenarioActivity`, replaces the former
StreamingPreviewActivity. `DebugScenarios` provides offline synthetic catalogs,
access/display metadata and provider events to the real InferenceViewModel,
InferenceSession and ChatConversation. Streaming, completion, exclusion, Stop and
next-send use those production state machines. ScenarioOwner prepares once and
retains the VM across actual Activity.recreate; final disposal cancels its scope.
No process persistence is simulated or added.

Production-only changes are the small presentation extraction in WearShell.kt
and callback parameters in ConnectionItems.kt. The existing wrapper forwards the
same real states/actions; debug supplies inert connection actions instead of
constructing real auth/key ViewModels. Both use the same screen, chunking, clipping,
model picker, composer and fixed controls. A default-null supplemental guidance
input lets LONG_ERROR_MESSAGE stress the same renderer using repeated static
production error/recovery copy. No production behavior, OAuth, billing decision,
credential storage/format, inference protocol, Data Layer routing or dependency
changed. No fake parallel UI or production emulator fallback exists.

**VERIFIED AUTOMATICALLY — release/storage isolation:**

- Scenario Activity and fixture/owner classes exist in debug merged manifest/APK
  DEX and are absent from release merged manifest/APK DEX. Old preview absent too.
- No production source references either debug entrypoint. Release cannot resolve
  the Activity; there is no production navigation/invocation path.
- Harness has no secure record/repository, serializable credential, HTTP-client,
  preference-store or production Application dependency. The production Application
  stores are lazy and not accessed by this host. No real Authorization header.
- The transient CredentialLease required by InferenceSession has an empty bearer,
  passed only to the synthetic provider. It is never converted to a persistable
  CredentialProfile, API-key credential or OAuth token set.
- Model callbacks are default memory-only/no-op; synthetic selection cannot write
  real model preferences. Connection/account/key/sign-out actions are inert.
- Only scenario disk write: fixed non-secret debug-scenario-ready.txt containing
  validated launch identifier, enum, generation count and chat stage. No payload,
  real credential, account identifier or exception diagnostics are emitted.
- tools/check_scenario_isolation.py enforces manifest/DEX/source/dependency checks.
  This is dependency/source/output evidence, not a claimed live Keystore trace.

### Developer interface and automation

Full fixture mapping and developer guide: WEAR_SCENARIOS.md.

```sh
python3 tools/wear_scenarios.py --list
python3 tools/wear_scenarios.py --scenario STREAMING_LONG
python3 tools/wear_scenarios.py --scenario ERROR_429 --screenshot /tmp/error_429.png
python3 tools/wear_scenarios.py --install --scenario EMPTY_CHAT
python3 tools/wear_scenarios.py --smoke
```

The launcher discovers online emulator serials, checks watch characteristics and
requires exactly one watch. It prints the AVD identity; no boot-order/serial
constant or Pixel Watch association is needed. Missing/ambiguous watch, unknown
scenario, missing package, non-debuggable package and missing Activity/ready ACK
fail clearly. Physical devices are excluded. Optional install is same-signed -r,
never uninstall/data clear. Each new launch clears the old Activity task only.
A fresh validated nonce binds the fixed app-private ready ACK to this launch;
process liveness is also required. Smoke iterates all 23 names. It is a readiness
check, not automatic visual recognition. Screenshots use screencap; no OCR,
goldens, UI testing framework or new dependency was added.

ERROR_401_REAUTH retains its requested public name but deliberately uses the
production bare ChatGPT admission 401 -> PERMISSION mapping, with plan/account
permission guidance. It does not invent terminal refresh expiry. ERROR_401_API_KEY
uses INVALID_API_KEY and separate API billing/no fallback. LONG_ERROR_MESSAGE is
static safe guidance repetition, not unsanitized remote error text. MODEL_UNAVAILABLE
starts with no selection and blocked Send; explicit Reload recovers synthetically.
MODELS_LOADING deliberately holds catalog discovery pending Stop, without a timeout.

### Automated commands and exact counts

Persistent JDK/SDK/signing configuration unchanged. Commands actually run:

```sh
export JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9
export ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android
./gradlew :wear:compileDebugKotlin --console=plain
./gradlew :wear:testDebugUnitTest --tests '*DebugScenariosTest' --console=plain
./gradlew :wear:testDebugUnitTest --tests '*DebugScenariosTest' \
  --tests '*ModelRestorationIntegrationTest' --tests '*Chat*Test' \
  --tests '*Streaming*Test' --tests '*TransitionTest' \
  --tests '*InferenceSessionTest' --tests '*InferenceViewModelTest' \
  --tests '*ApiKeyCancellationTest' :wear:assembleDebug --console=plain
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_wear_scenarios.py'
python3 tools/check_scenario_isolation.py
python3 tools/wear_scenarios.py --install --smoke
python3 tools/wear_scenarios.py --scenario STREAMING_LONG --screenshot /tmp/d3f-stream.png
git diff --check
```

**VERIFIED AUTOMATICALLY:**

| Check | Result |
| --- | --- |
| New focused DebugScenariosTest | 9 tests; zero failures/errors/skips |
| Combined D3-B–F focused run | 101 tests; zero failures/errors/skips |
| Final full Wear suite | 272 tests; zero failures/errors/skips (263 existing + 9 new) |
| Restoration integration included | 15 tests PASS |
| Chat conversation/ViewModel/transport included | 13 + 12 + 3 = 28 tests PASS |
| Streaming race/HTTP cancellation included | 13 + 4 = 17 tests PASS |
| Transition/error transition included | 12 + 2 = 14 tests PASS |
| Inference session/ViewModel; API cancellation included | 5 + 10 + 3 = 18 tests PASS |
| Python launcher unit suite | 8 tests PASS, no ADB contacted |
| Debug/release assembly and debug lint | PASS; release APK unsigned, no signing/publishing claim |
| Release manifest/DEX and forbidden dependency checks | PASS |
| Three new Python files AST syntax | PASS |
| git diff --check and changed/new text whitespace | PASS |

Nine scenario tests cover enum round-trip/unknown closed failure, all 23 fixture
preparations, distinct 401 and production 429/503/offline mapping, unavailable
model Send guard/reload recovery, real streaming cancellation, completed history,
partial exclusion/next context and retained-owner no-regeneration. Tests use the
coroutine test scheduler/gates, no wall-clock sleeps. Host tests cover dynamic
serial use, physical exclusion, no/ambiguous watches, unknown names, missing package
and release run-as rejection. Source/output isolation checks separately cover
credential/model persistence dependencies and release component absence.

Lint: zero errors, **six warning instances** (two AGP advisories, ExportedService,
VectorPath, two WearRecents). One new WearRecents instance is the debug scenario
Activity: noHistory was removed so native input return/retained recreation remain
possible. Existing diagnostic Activity has the other instance. No suppression or
production manifest change. Initial sandbox Gradle read-only cache-lock failure
was retried successfully with approved access to the existing cache. The initial
isolation check ran before release assembly completed and found no release APK;
it passed after assembly. No test failure occurred. Mobile/shared code unchanged;
mobile suite/build/lint NOT RERUN and not claimed as current evidence.

### Emulator acceptance matrix

Existing WristPrompt_Watch7 was dynamically verified as a watch and selected
(current serial WATCH_SERIAL, not encoded in the launcher). A connected physical
ADB device was ignored. Debug APK installed in place; no app data/credentials were
cleared. All 23 scenarios passed fresh ACK + liveness smoke, including a repeat on
the final rebuilt debug APK. Initial/scrolled synthetic screenshots for all 23
were reviewed, with touch scrolling and process checks after each scenario.

| Scenario | Result |
| --- | --- |
| SIGNED_OUT | PASS — VERIFIED ON EMULATOR |
| CHATGPT_AUTHENTICATED | PASS — VERIFIED ON EMULATOR |
| API_KEY_CONFIGURED | PASS — VERIFIED ON EMULATOR |
| MODELS_LOADING | PASS — VERIFIED ON EMULATOR |
| MODELS_LOADED | PASS — VERIFIED ON EMULATOR |
| MANY_MODELS | PASS — VERIFIED ON EMULATOR |
| MODEL_UNAVAILABLE | PASS — VERIFIED ON EMULATOR |
| EMPTY_CHAT | PASS — VERIFIED ON EMULATOR |
| LONG_USER_PROMPT | PASS — VERIFIED ON EMULATOR |
| ONE_TURN_COMPLETED | PASS — VERIFIED ON EMULATOR |
| MULTI_TURN_CHAT | PASS — VERIFIED ON EMULATOR |
| LONG_ASSISTANT_OUTPUT | PASS — VERIFIED ON EMULATOR |
| STREAMING_SHORT | PASS — VERIFIED ON EMULATOR |
| STREAMING_LONG | PASS — VERIFIED ON EMULATOR |
| STOPPED_PARTIAL | PASS — VERIFIED ON EMULATOR |
| ERROR_401_REAUTH | PASS — VERIFIED ON EMULATOR |
| ERROR_401_API_KEY | PASS — VERIFIED ON EMULATOR |
| ERROR_429 | PASS — VERIFIED ON EMULATOR |
| ERROR_503 | PASS — VERIFIED ON EMULATOR |
| ERROR_OFFLINE | PASS — VERIFIED ON EMULATOR |
| LONG_ERROR_MESSAGE | PASS — VERIFIED ON EMULATOR |
| RECREATED_COMPLETED_STATE | PASS — VERIFIED ON EMULATOR |
| RECREATED_CANCELLED_STATE | PASS — VERIFIED ON EMULATOR |

No observed crashes or serious overlap. Shared scroll viewport clips content
below fixed Compose/Send/Stop; partial rows at viewport edges are scrollable.
Deep picker scroll reached Synthetic model 40 and Reload models. Long error copy
scrolled to final Connection/settings and Details actions. A direct tap on fixed
Stop during long streaming removed Stop, restored Compose and retained partial
output with exclusion labeling. Short stream completed normally. Both actual
Activity recreation scenarios acknowledged generations=1 with COMPLETED/CANCELLED
respectively; no revived Stop or duplicate send. Next-send context exclusion is
additionally established by the production-path unit test, not inferred from a
screenshot. No rotary/font-scale/physical behavior is inferred from touch checks.

Screenshots/contact sheets are ephemeral under /tmp/d3f-acceptance,
/tmp/d3f-top-contact.png, /tmp/d3f-scroll-contact.png and /tmp/d3f-stream.png.
They contain synthetic content only. The deeper scrolling/Stop checks used
throwaway /tmp helpers, not a new permanent UI automation framework.

### Changed files and remaining boundary

- Debug: replace StreamingPreviewActivity.kt with DebugScenarioActivity.kt and
  DebugScenarios.kt; update debug AndroidManifest.xml entry.
- Tools: wear_scenarios.py, check_scenario_isolation.py, test_wear_scenarios.py.
- Tests: wear/src/testDebug/.../DebugScenariosTest.kt.
- Production: only ui/WearShell.kt and ui/ConnectionItems.kt presentation injection
  extraction described above; all original callbacks preserved.
- Documentation: this status, WEAR_SCENARIOS.md, durable debug-isolation policy D21
  in DECISIONS.md. ROADMAP.md gates unchanged.

Production capability-dependent authentication remains
**NOT VERIFIABLE ON CURRENT EMULATOR LAB**. Classification remains
**UNRESOLVED — STRONGLY ISOLATED TO GMS/EMULATOR**; investigation not reopened.
No physical verification, real OAuth or funded-BYOK success verification. Historical
Terra -> Sol physical anomaly and transient reauthorization exchange/context-expiry
anomaly remain unresolved debt, without a claimed fix. Real network/capability
routing, large-font/rotary behavior and final consolidated acceptance remain
outside this synthetic harness evidence.

**Deferred to D3-G:** final Day 3 consolidated report/acceptance and disposition
of its remaining checks. No D3-G work started; no next phase is authorized by this
D3-F record. Existing consolidated physical checklist remains pending without a
new routine physical test request.

Suggested commit: `test: add debug Wear scenarios and emulator acceptance launcher`

No commit or push performed.
