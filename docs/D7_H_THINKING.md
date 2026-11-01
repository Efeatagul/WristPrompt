# D7-H — native thinking controls (#18)

Implementation scope: Wear only. Owner Galaxy Watch / paired-device acceptance is
intentionally deferred with #5, #7 and remaining D7 work to the consolidated pass
before/as part of #12. #18 remains open; this document never establishes physical PASS.

## Capability contract (verified 2026-11-01)

The [Responses reference](https://developers.openai.com/api/reference/resources/responses/methods/create)
and [reasoning guide](https://developers.openai.com/api/docs/guides/reasoning)
define top-level `reasoning.effort`. A general API enum is not per-model support.
`OpenAiThinkingCapabilities` therefore contains an audited exact-ID table for the
ordinary API-key Responses route, using these official model pages:

| Exact model ID / source | Native values, in documented order |
| --- | --- |
| [gpt-6-astra](https://developers.openai.com/api/docs/models/gpt-6-astra) | low, medium, high, xhigh, max |
| [gpt-6.1-sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol) | low, medium, high, xhigh, max |
| [gpt-6-sol](https://developers.openai.com/api/docs/models/gpt-6-sol) | none, low, medium, high, xhigh, max |
| [gpt-6-luna](https://developers.openai.com/api/docs/models/gpt-6-luna) | none, low, medium, high, xhigh, max |
| [gpt-5.5](https://developers.openai.com/api/docs/models/gpt-5.5) | none, low, medium, high, xhigh |
| [gpt-5.4](https://developers.openai.com/api/docs/models/gpt-5.4) | none, low, medium, high, xhigh |
| [gpt-5.2](https://developers.openai.com/api/docs/models/gpt-5.2) | none, low, medium, high, xhigh |
| [gpt-5.1](https://developers.openai.com/api/docs/models/gpt-5.1) | none, low, medium, high |
| [gpt-5](https://developers.openai.com/api/docs/models/gpt-5) | minimal, low, medium, high |

Only models returned by the active credential's discovery remain selectable. The
table does not create model availability. No prefix, suffix, marketing-name or
family matching occurs, including for dated snapshots. Unreviewed IDs have only
Default; extending the table requires verifying that exact ID's official contract.
This explicit reviewed subset is not an exhaustive enum. Provider changes require
a new contract review; existing HTTP errors remain visible with no retry/substitution.

The [API-key model catalog](https://developers.openai.com/api/reference/resources/models/methods/list)
contains IDs/basic metadata, not native effort capabilities. The
[ChatGPT-plan contract](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
defines `models[].slug`, `display_name`, `visibility`, service order, and public
`/v1/responses` inference, but does not establish a per-model effort list for that
route. **Plan models therefore expose Default only**, even if their slug matches an
API table entry. Undocumented `supported_reasoning_levels` metadata and Codex
behavior are not used as contracts. No live owner credentials were inspected or
used to probe support. This is a safe product limitation, not proof that plan
models cannot reason or that the server rejects every explicit level.

## Durable semantics and validation

Snapshot v2 adds `InferenceConfiguration(version=1, controls=List<NativeControl>)`.
Each control stores `key`, exact native `value`, `provider`, `model`, and `route`
strings; the containing conversation's existing owner scope supplies account
ownership. An empty list means **Default / omitted**. It is distinct from native
`none` and `medium`. Future control keys/providers/native strings fit the durable
shape; no new providers or settings framework are implemented. Size, duplicate
keys and control-character validation remain bounded; diagnostic string forms
are redacted. A synthetic `ultra-Future` contract tests non-enum representation.

V1 records without configuration decode to Default, and the next checkpoint writes
v2. SQLite schema/tables and atomic revision ownership stay unchanged. No data is
cleared, no token writes are added, and failed configuration saves retain the
previous selection. A selection before the first message creates an empty local
chat checkpoint so process restart can restore it. Configuration changes on an
existing chat use its `ConversationWriter`; completed replay is unchanged.

A model change reads that model's capability. It rebinds the previous explicit
string only if the same provider/route/control supports that exact string;
otherwise it visibly resets to Default. The existing model-change conversation
reset policy remains in force. Same-model catalog refresh, startup and history
reopen retain stale explicit values visibly and require an explicit valid choice
(or Default) before Send. Offline read-only history remains read-only.

Validation occurs in the ViewModel before beginning a turn, in the session after
bearer/catalog revalidation immediately before dispatch, and in the OpenAI adapter
against the route's audited contract. Unsupported values or provider/model/route
ownership mismatches fail with a static thinking-level error. They are never
ignored, mapped or sent. A fresh-bearer capability change also blocks dispatch.

## Wire and UI behavior

For both current public Responses routes, Default omits the **entire `reasoning`
field**. For supported API-key models, an explicit value becomes
`"reasoning":{"effort":"<exact native string>"}`. No trimming, case folding,
semantic mapping, guessed defaults, backend-api endpoints or billing fallback.
The plan route currently rejects every explicit selection locally because its
capability list is unavailable. Native reasoning continuation items in replay
remain opaque, validated and unchanged. Configuration is never fake user/system
content or inserted into replay. `store=false`, `stream=true`, real transport
Stop, completed-only replay, failure exclusion and explicit Send remain intact.
The pre-existing diagnostic Test inference action remains a separate explicit
proof action using its unchanged Default request.

The model picker keeps all model rows first, in service order, with authoritative
name + selected state only. Thinking level follows the complete model list, with
Current, Default, omission explanation and exact native choices. Its rows use the
existing round-screen lazy list/touch/rotary scrolling; controls never dispatch.
No model badges/rankings or alternate effort abstractions are added.

## Evidence

**VERIFIED AUTOMATICALLY** (2026-11-01):

- `./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug`
  with the documented JDK 17: PASS, 530 unit tests, debug/release APKs, lint with
  zero errors and six warnings in unchanged build/manifest/drawable inputs.
- `python3 tools/check_scenario_isolation.py`: PASS; fixtures absent from release
  manifests/DEX and no debug credential/storage/network dependency.
- `python3 -m unittest discover -s tools -p 'test_*.py'`: PASS, 40 tests.
- `git diff --check`, changed-document local links and added-production
  secret/prompt/logging scan: PASS.
- Focused new suites cover exact capability/serialization strings (including
  `ultra-Future`), ownership rejection, bearer-refresh invalidation, both-route
  omission, v1 migration, restart/history restoration, later-turn replay identity,
  failed configuration saves and zero inference on selections/restoration.
  Existing transport Stop, excluded exchanges, completion-save failure and
  account/model/billing isolation regressions also pass in the Wear suite.

No mobile/shared production boundary or dependency changed, so mobile builds and
unrelated D7 suites were not run. Release remains unsigned, as before.
**VERIFIED ON EMULATOR** (2026-11-01): isolated temporary `WristPrompt_Watch7`,
454×454 round Wear API 36, synthetic `THINKING_LEVELS` and `THINKING_LONG_LIST`.
At font scales 1.0 and 1.3, Default was visibly distinct from native choices,
and touch selected native `none`/`ultra-Future`;
model 2's narrower capability visibly reset the selection to Default. Accessibility
snapshots established models-first ordering and explicit rotary events changed
visible list content. At 1.3, Thinking level remained reachable after all 40 models.
Debug reports recorded zero inference generations throughout. Screenshots were
visually inspected for readable controls and round-screen clipping. These are
synthetic UI checks, not live provider or physical evidence. A fresh temporary
emulator data directory preserved the existing installation and signing keys.

Reproduce the fixtures with `python3 tools/wear_scenarios.py --scenario THINKING_LEVELS`
and `--scenario THINKING_LONG_LIST`; set emulator font scale to 1.0/1.3, traverse
with touch and `adb shell input rotaryencoder scroll --axis SCROLL,-3`, and inspect
the debug REPORT acknowledgement. The one-off driver also verified selection,
reset and `generations=0` using UI accessibility snapshots. Screenshots and the
driver were local verification artifacts, not shipped app code.

Physical acceptance is deferred to #12, including real plan/API capability behavior,
round Galaxy Watch touch/rotary at normal/max font, process restart/history restore,
later-turn selection and real Stop. No physical tests were performed for this task.
