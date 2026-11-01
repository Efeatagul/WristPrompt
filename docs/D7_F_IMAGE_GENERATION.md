# D7-F image generation — blocked implementation checkpoint

Issue #11 remains OPEN. This is a contract/capability checkpoint, **not** completed
implementation or automated/emulator acceptance. One focused implementation
attempt hit the Stop/completion persistence boundary below. Following the owner's
explicit stop rule, the unfinished dispatch, schema, storage and viewer changes
were removed before checkpointing. No generation action is exposed. PR #29 was
inspected only; its branch/commit is not included and it was not modified/merged.

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

## Reproduced blocker: Stop during completion persistence

The existing `ConversationWriter.write` intentionally runs under `NonCancellable`.
It also rejects a later snapshot with shorter replay, protecting a completed
exchange from a late Stop checkpoint. `InferenceViewModel.send` explicitly records
that completion wins once the terminal event has been handled. This is existing
text behavior, not a newly introduced generation defect.

The attempted generation integration reused those same owners. A deterministic
latch test did the following, without sleeps, real files, network or credentials:

1. Prepare a pending exchange and a completed generated-image exchange.
2. Enter completion `save` and suspend it on a latch.
3. Cancel the completion's owning Job, as Stop does; queue an excluded/stopped
   checkpoint under the writer's mutex.
4. Release completion saving and join both operations.
5. Require that durable replay excludes the generated completion.

The required assertion **FAILED**: `Stop must keep generated completion out of
durable replay`. Completion committed despite cancellation; the subsequent stopped
snapshot was discarded by the shorter-replay guard. The attempted integration
compiled, but this required race did not pass. No failed test is relabeled PASS.

`ImageGenerationCommitBoundaryTest` retains a passing **characterization** of that
unchanged policy using text-only canonical data, so this checkpoint needs no unsafe
generated-image schema. It asserts that cancelled completion still wins; it is
explicitly NOT acceptance of the requested generation Stop behavior. Invert the
replay assertion to reproduce the incompatible requirement.

A pre-save coroutine/epoch check cannot fix cancellation after entry to the
non-cancellable save. The unresolved work is a commit-boundary ownership design
that satisfies strict D7-F Stop without rolling back committed text exchanges or
splitting SQLite/file ownership. The focused attempt did not resolve that design;
per the owner's instruction, it stops here rather than changing the shared durable
commit policy speculatively. No transport Stop behavior was weakened.

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
