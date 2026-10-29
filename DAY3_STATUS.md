# Day 3 status — 2026-10-29

Phase A is implemented. Phase B is complete as investigation/test hardening;
its historical physical anomaly remains unresolved. Phase C text chat/context is
implemented below. Phase D streaming/Stop ownership is hardened below. Day 2
remains PASS WITH KNOWN DEBT. D3-E is unstarted; this is not a completed Day 3
physical/release gate.

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
characteristics before install -r. Only emulator-5554 was targeted. Physical
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
