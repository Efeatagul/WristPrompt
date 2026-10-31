# Architecture

## Ownership

`:wear` owns chat, account/model selection, protected credentials, refresh and
inference. `:mobile` owns setup/browser OAuth and credential handoff. Shared Kotlin
protocol, safe diagnostics and storage helpers compile into both modules from
`shared/src`; there is no shared Gradle module. `tools/` drives synthetic debug
fixtures; `docs/` is the developer handoff. Manual application containers wire
ViewModels, repositories and providers without a dependency-injection framework.

## Setup and credentials

Watch → Wearable Data Layer → phone → system browser → phone loopback OAuth
callback → validated token exchange → protected handoff → watch secure storage.

Watch generates an installation host UUID. Capability-based peer selection and
expiring node/request/registration bindings reject wrong-node, stale and replayed
transfers. ACK follows atomic encrypted watch storage; lost-ACK retry must not
replace newly refreshed tokens. The phone is never the refresh owner after acceptance.
API keys use a separate setup/record path and are not persisted by the phone.

Keystore-backed AES-GCM and atomic no-backup files protect credentials. Preferences
DataStore holds appropriate non-secret preferences/setup state, not plaintext
credentials. Selected billing mode is explicit: ChatGPT plan or OpenAI API key.
CredentialRouter never falls back based on whichever secret is available. Account,
mode and credential generation checks reject stale actions across asynchronous work.

Models are discovered for the active credential. The picker preserves service order
and shows authoritative names plus selected state only; availability is not a promise
of inference access. See PROJECT_SPEC.md and DECISIONS.md for protocol details.

## Inference and chat pipeline

Watch UI / InferenceViewModel → InferenceSession → credential router/repositories
and OpenAiProvider → OpenAiHttp → SSE decoding → canonical chat state → coalesced
presentation → native Markdown rows.

Normal inference is Watch → OpenAI directly. The phone is not an inference proxy.
Requests use Responses with `store=false`, `stream=true` and explicit input context;
no `previous_response_id`. InferenceSession serializes generation, tracks queued
and active owning Jobs, validates credential/catalog ownership after asynchronous
work and rejects late progress. OpenAiHttp runs blocking I/O off Main, disables
redirects and authenticated transport retries, closes response resources and
cancels the actual OkHttp Call on cancellation. No automatic generation/model retry.
Only `response.completed` establishes success; EOF, incomplete, failed and cancelled
attempts never masquerade as completion.

ChatConversation owns process-memory display state and a separate JSON replay
history. Only whole completed exchanges enter replay. Failed/cancelled/interrupted
attempts may remain visible as excluded/partial display content. Validated opaque
reasoning `encrypted_content` can belong to replay without being display text;
unsupported tool output is rejected rather than invoked or silently replayed.
Current bounds include 8,192 prompt characters, 12 attempts and 196,608 context bytes.

## Lifecycle and presentation

Retained ViewModels own work across ordinary Activity recreation. Process death
loses chat/replay and active work; startup does not replay an old request. Settings,
credentials and model preference are distinct persistent state. Screen-off/background
continuation remains best effort: no foreground service, WorkManager, wake lock,
connectivity observer or polling was added.

ChatPresentationOwner derives a disposable UI projection from canonical state.
Event-triggered coalescing is approximately 100 ms for active text; Stop/terminal
boundaries publish immediately. Latest is explicit; there is no forced autoscroll.
MarkdownWorker converts bounded terminal snapshots off Main with replaceable pending
work, version checks and bounded caches. Current streaming text stays plain; completed
and retained terminal partial content can reconcile to native rich rows. UI caches,
row keys and ranges are neither canonical replay nor a durable schema.

Markdown uses CommonMark/GFM tables and Compose, without WebView. Raw HTML is inert;
remote images are not fetched. Links have local inspection rather than automatic
navigation. Code supports horizontal touch with vertical rotary; tables use bounded
Wear-adapted records/fallbacks. See DAY5_C_REPORT.md for limits and failure fallback.

## Future persistence boundary

D6-B implements the canonical store/checkpoint boundary described in
[D6_B_STORAGE.md](D6_B_STORAGE.md). History/startup selection is D6-C; the remaining
paragraphs below describe the historical D5 baseline and original D6-A plan.

Local D6-A design is recorded in [D6_A_PERSISTENCE_ARCHITECTURE.md](D6_A_PERSISTENCE_ARCHITECTURE.md).
It selects platform SQLite, typed content and separate transactional replay; no
production storage is implemented or accepted by this documentation change.

D6 is NOT STARTED. Durable DISPLAY TRANSCRIPT and RESPONSES REPLAY CONTEXT require
separate versioned records and compatibility decisions; do not serialize D5
presentation objects. No storage write per streaming token. The next developer
step is D6-A, a read-only architecture audit first. D5 is complete with known debt.
D6 must anticipate Text/ImageAttachment content parts, app-private binary files,
transactional completed exchanges and separate replay records. D7 adds phone-owned
presets and an imported photo library; normal inference remains watch-direct.
See CODEX_HANDOFF.md and D6_D7_BACKLOG.md for authorized future product scope.

See [../PROJECT_SPEC.md](../PROJECT_SPEC.md), [../DECISIONS.md](../DECISIONS.md),
[TESTING.md](TESTING.md) and [CODEX_HANDOFF.md](CODEX_HANDOFF.md).
