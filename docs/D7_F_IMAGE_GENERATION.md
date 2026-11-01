# D7-F image generation — implementation in progress

Issue #11 remains OPEN. Continuing checkpoint 7a66710 on the existing branch.
The ownership analysis below resolves the original planning blocker; implementation
and new acceptance evidence are recorded as they complete. PR #29 is out of scope.

## Official contract audit — 2026-11-01

The following official pages were opened and reviewed during this task:

- [Image tool contract](https://developers.openai.com/api/docs/guides/tools-image-generation):
  Responses supports `image_generation` with forced `tool_choice`. `action: generate`
  prevents editing. The tool's `model` is distinct from the selected top-level
  model. The documented support list includes the four reviewed IDs below.
- [Image generation guide](https://developers.openai.com/api/docs/guides/image-generation):
  Responses and Images API are available; Responses fits the existing session.
  Completed `image_generation_call.result` carries base64. The guide also documents
  continuation by image call ID and streaming final output. Organization verification
  may be required. A privacy-preserving `safety_identifier` is recommended, optional.
- [SIWC preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations):
  image generation is explicitly unsupported. Image **input** permission does not
  authorize generation. No SIWC workaround or private endpoint is permitted.
- [SIWC models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference):
  use the selected account's current catalog/bearer and public Responses endpoint.
  Catalog membership alone is not a generation capability declaration.
- [GPT-6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol) and
  [GPT Image 2.5 Flare](https://developers.openai.com/api/docs/models/gpt-image-2.5-flare)
  establish separate mainline/tool models. The latter supports the Responses tool.
- [API pricing](https://developers.openai.com/api/docs/pricing): mainline inference
  and image generation have API usage costs. This is separate API billing; no
  promise that a ChatGPT subscription pays for it. No exact per-image estimate is
  presented or inferred from dimensions alone.
- [Cancel response](https://developers.openai.com/api/reference/resources/responses/methods/cancel):
  that endpoint applies only to responses created with `background: true`.
  WristPrompt does not enable background generation. Cancelling a foreground HTTP
  call must not be described as proof of provider-side cancellation or refunded usage.

API-key generation is documented in principle; **no generation route is enabled in
this checkpoint**. SIWC is contractually unavailable. This issue is blocked on the
local cancellation/persistence integration, not on universal provider unavailability.

## Retained capability and request plumbing

`AiModel.imageGeneration` holds an optional provider/model/route-scoped capability
using strings, independently of Thinking and image input. This is runtime catalog
metadata; no provider-specific durable enum or schema migration is introduced.

`OpenAiProvider.models` enriches only API-key catalog entries whose exact IDs are
`gpt-6.1-sol`, `gpt-6-astra`, `gpt-5.5`, or `gpt-5.4`. Unknown IDs, case variants,
snapshot suffixes, descriptive display names, unreviewed aliases and plan entries
have no generation capability. This is a conservative reviewed subset, not the
entire provider-supported catalog. Future additions need another official audit.
The eventual session must still enforce active catalog membership and ownership.

`OpenAiGeneration.request` is an undispatched request builder. It requires matching
reviewed capability identity and nonblank user text of at most 8,192 characters.
Input photos are rejected rather than repurposed. Validation has no network,
credential selection, route selection or model selection side effects.

Its reviewed representation is `POST /v1/responses` with the selected top-level
model, explicit input, `store=false`, `stream=true`, and:

```json
{
  "tools": [{
    "type": "image_generation",
    "model": "gpt-image-2.5-flare",
    "action": "generate",
    "size": "1024x1024",
    "quality": "low",
    "output_format": "jpeg",
    "output_compression": 80
  }],
  "tool_choice": {"type": "image_generation"}
}
```

The builder omits reasoning effort and `previous_response_id`. Stored Thinking is
not accepted or changed by this operation. The whole request retains the existing
262,144-byte text body bound. Static unsupported/prompt errors are available to a
future caller. No composer, navigation, gesture or provider stream calls the builder.

## Completion / Stop ownership conclusion — 2026-11-01

The original inverse race expectation was an over-strong planning requirement,
not an image provider requirement. #11's published scope requires safe cancellation;
it does not require rolling back an owned or committed completion. Text and image
operations use ONE ownership rule. The writer's NonCancellable transaction,
shorter-replay guard, revision checks and tombstones remain authoritative.

The Main-confined state machine is:

- RUNNING: pending display exchange, no replay; Stop invalidates epoch and cancels HTTP.
- TERMINAL_RECEIVED: session has received `response.completed`, but must finish
  terminal validation, provider exit, lease and owning-Job checks. Stop can still win.
- PREPARING (images only): validate/normalize and stage an unowned file candidate.
  Stop still wins; no completed attachment or replay exists.
- COMMIT_OWNED: on Main, after the last epoch/owner validation, synchronous
  `ChatConversation.complete` validates and installs the canonical candidate and
  `completionOwner = run` is set with no intervening suspension. **This is the
  completion-versus-Stop linearization point.** Stop is now a no-op. It cannot cancel
  the commit or advance its epoch. Receiving/validating a provider event alone,
  entering SQLite, and SQLite success are different events.
- COMPLETED: atomic durable save succeeded; only now publish completed display.
- FAILED: save failed; restore pre-completion replay, preserve visible partial as
  unsaved/excluded, expose CHAT_STORAGE; no automatic retry.
- STOPPED: Stop acquired ownership before COMMIT_OWNED; late callbacks cannot
  acquire commit ownership, and the excluded checkpoint has no new replay.

Commit ownership is irrevocable **against Stop**, conditional on successful storage.
Durable success becomes a fact only when the transaction atomically succeeds. These
are two distinct boundaries. No fully committed transaction is ever undone.

Existing text already installed its completion synchronously before a protected save,
and its writer rejects the later shorter stopped snapshot. A deterministic test
preserves this characterization. The original six-step blocked-save/inverse-replay
test crosses the ownership point before cancelling, so its correct expectation is
completion wins. Making Stop win there would change text semantics, permit replay
regression and undermine completion-save guarantees; compensation is prohibited.
The generated-image provider lifecycle does not require such a rollback.

Focused inspection did find a real UI/failure race: Stop could see unpublished busy
UI while the canonical candidate was already completed, cancel the operation and
advance epoch; a subsequent save failure would then skip in-memory rollback. The
small shared ViewModel correction ignores Stop for its current completion owner.
It preserves the existing durable winner, and ensures a later storage failure
cannot leave failed completion in replay. ConversationWriter and SQLite semantics
are unchanged. Account/New Chat destruction still invalidates publication and never
revives old UI. Session Job, lease and ViewModel epoch checks reject stale callbacks;
duplicate terminals are ignored; actual HTTP cancellation before ownership remains.

Deterministic tests use deferred barriers, not sleeps: terminal received before
session return → Stop wins; owned save blocked → completion wins; owned save fails
after Stop → CHAT_STORAGE and prior replay; cancelled writer rejects shorter replay.
Existing text late/duplicate callback and real transport-cancellation tests remain
part of the gate. Generation will use the same finalization path and tests.

## Unimplemented boundaries and proposed limits

The following are requirements/design candidates, **not implemented acceptance**:

- Output: one completed image result; at most 2 MiB decoded provider JPEG, at most
  1280 pixels per edge/1,638,400 pixels. Bound SSE frames and cumulative response
  before buffering; strict base64, actual format/dimensions/full decode, reject
  corruption/truncation and unsupported formats before promotion.
- Storage: reuse D7-C normalization to canonical JPEG at most 768 KiB/1280 edge
  plus a 160-edge/24 KiB thumbnail. Write recognized temporary files, fsync and
  atomically promote validated files. Keep them conversation-owned, outside the
  imported library; use opaque typed references and bounded integrity metadata.
  Library deletion must never reach generated files. No binaries/base64 in SQLite,
  DataStore or conversation JSON. Deletion/orphan policy must follow the resolved
  commit boundary; conservative reservations alone do not establish Stop acceptance.
- Replay: official image-call-ID continuation exists, but the checkpoint does not
  add it to canonical replay. Durable `store=false` continuation/reasoning semantics
  still need implementation and tests. No fake textual tool result or automatic
  local-image re-upload is substituted. Existing completed-only replay is unchanged.
- Viewer: fullscreen aspect fit, 1–5x bounded pinch zoom/pan, Close/system Back,
  predictable reopen, safe recreation, ephemeral viewport state, round-screen and
  normal/max-font checks. Viewer/gestures must have zero inference authority.
- Errors: output corruption/size/dimensions, missing result, write/save failure,
  stale callbacks, cancellation, restart/reopen and cleanup still need acceptance.

## Security and evidence

All fixtures use synthetic identities/tokens and a fake store. No live API calls,
owner credential access, physical device operation or data clearing occurred.
Capability diagnostics are redacted; errors use static categories. No new
credential/authentication, billing fallback, storage schema, dependencies, manifest
permissions or mobile/shared production changes are retained.

VERIFIED AUTOMATICALLY, final checkpoint:

```sh
./gradlew :wear:testDebugUnitTest --tests 'io.github.grassified.wristprompt.openai.*' --tests '*ErrorPresentationTest' --tests '*ModelItemsTest' :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --no-daemon --console=plain
python3 tools/check_scenario_isolation.py
git diff --check
```

Using the documented JDK 17/Android SDK: PASS, **347 tests / 41 suites**, zero
failures/errors/skips; Wear debug/release assemblies; debug lint zero errors /
10 warnings. Includes seven new contract/capability tests and one commit-policy
characterization. Existing affected text, D7-D/E composer/image-input, Thinking,
completed replay, transport cancellation and account/billing/credential isolation
tests passed. The full Wear suite and mobile tasks were not run: shared/mobile
production, dependency and durable-storage owners remain unchanged.

Scenario source/manifest/DEX isolation, 36 changed-document local links, XML parsing,
focused added-source logging/privacy inspection and whitespace checks passed.
The initial generated-image Stop requirement failed as documented; checkpoint
contracts/characterization passing does not establish generation acceptance.

Changed production files are limited to `ImageGeneration.kt`, the `AiModel` runtime
capability field, API catalog enrichment and static error categories/presentation.
No generation endpoint is dispatched. Suggested/used commit message:
`feat(wear): checkpoint image generation contracts and document Stop commit blocker`.

VERIFIED ON EMULATOR: **NOT RUN for this checkpoint**. No generation row, viewer,
pinch/pan, rotary, max-font, restart/reopen or live-provider result is claimed.
The incomplete production path was removed before emulator acceptance.

VERIFIED PHYSICALLY: **NOT TESTED; intentionally deferred**. Remaining consolidated
owner/live acceptance: actual supported API-key generation, Galaxy Watch 7 pinch/
pan and round ergonomics, Stop, restart/reopen, account/billing/model correctness,
and zero automatic generation. Keep #11 open with #1/#5/#7/#8/#9/#10/#18/#12 for
that eventual pass; this checkpoint also has nonphysical implementation remaining.
Do not refresh/merge PR #29, start #1 or resume #12 from this checkpoint.
