# Day 3 final acceptance — 2026-10-29

## A. Executive result: PASS WITH KNOWN DEBT

D3-A through D3-F development and D3-G consolidated automated/emulator acceptance
are complete. This is a development-stage result, not physical release acceptance.
No Day 4 work, production repair, credential operation, commit or push occurred.
This pass changed documentation only; no new architecture decision was needed.

Reviewed the clean starting tree at `1c44070`, all eight Day 3 commits from
`d672eca` through `1c44070` (including the two emulator investigations), relevant
production/UI/provider/transport/session/router code, integrated tests, debug
fixtures/Activity, and all three scenario/diagnostic tools. Earlier phase sections
in DAY3_STATUS.md and dated Day 2/investigation records remain historical evidence.

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

## B. D3-A through D3-F outcomes

| Phase | Final disposition |
| --- | --- |
| A | Wear shell/readability implemented; fixed billing/actions and scrollable surfaces accepted on emulator at default fonts. Hardware font/rotary checks deferred. |
| B | Investigation/test hardening complete; all 15 integrated restoration tests pass. Historical physical model-restoration anomaly remains unreproduced and unresolved; integrated regression coverage passes. |
| C | Arbitrary text composer and one bounded in-memory conversation implemented; explicit completed-only replay, New Chat and context boundaries pass. Native keyboard return evidence is recorded in D3-C; synthetic chat rendering reconfirmed in G. |
| D | Serialized generation ownership, actual transport cancellation, partial exclusion and stale-event rejection pass regression coverage; emulator Stop/streaming evidence remains distinct from real-network behavior. |
| E | Auth/model/billing/error transition coverage passes; explicit recovery guidance and round-screen clipping reviewed on emulator. |
| F | All 23 offline debug scenarios pass readiness/liveness and rendered-screen/scroll review; release manifest/DEX and source/dependency isolation pass. |

## C. Final automated results — VERIFIED AUTOMATICALLY

All Gradle tasks were rerun, rather than accepting cached test results. Final:
**BUILD SUCCESSFUL, 194 tasks executed**, with the unchanged DEVELOPMENT.md JDK,
SDK, Gradle cache and signing environment.

| Check | Result |
| --- | --- |
| Full Wear JVM suite | 272 tests; 0 failures, 0 errors, 0 skipped |
| Full mobile JVM suite (includes shared tests) | 70 tests; 0 failures, 0 errors, 0 skipped |
| Integrated model restoration subset, included above | 15 tests; 0 failures/errors/skips |
| Python launcher suite | 8 tests; PASS |
| Wear/mobile assembleDebug | Both PASS |
| Wear/mobile assembleRelease | Both PASS; unsigned APKs, no production signing or publication |
| Wear/mobile lintDebug | Both PASS; zero errors; Wear 6 warnings, mobile 3 |
| Scenario merged-manifest / APK DEX / source dependency isolation | PASS |
| All tools/*.py AST syntax | PASS |
| git diff --check | PASS |
| Changed/new UTF-8 text trailing whitespace, newline and conflict-marker checks | PASS |
| Both debug APK signature verification | PASS; original matching SHA-256 ending 79d37061 retained |

Wear warnings: two AndroidGradlePluginVersion, one ExportedService, one VectorPath,
two WearRecents (debug diagnostic/scenario Activities). Mobile: OldTargetApi,
ExportedService, VectorPath. These match D3-F / the existing mobile baseline;
none suppressed. Existing SDK/license and Java native-access advisories do not
establish a failed build or changed signing identity.

Commands actually run:

```sh
export JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android
export GRADLE_USER_HOME=$HOME/.gradle
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest \
  :wear:assembleDebug :mobile:assembleDebug :wear:assembleRelease \
  :mobile:assembleRelease :wear:lintDebug :mobile:lintDebug \
  --rerun-tasks --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/check_scenario_isolation.py
python3 tools/wear_scenarios.py --list
python3 tools/wear_scenarios.py --smoke
python3 tools/emulator_data_layer.py
git diff --check
```

Python AST parsing, XML test/lint count extraction, APK signature verification
using build-tools/36.0.0/apksigner, and explicit changed/new text checks were also
run. Initial sandbox Gradle cache locking and ADB socket access failed; approved
host execution succeeded with existing tools. No dependency, SDK, license or key
was changed. Temporary synthetic screenshot/interaction helpers live only in /tmp.

### Restoration, chat, streaming and transitions

ModelRestorationIntegrationTest reconstructs encrypted wrappers/repositories,
router/session/ViewModel with synthetic disk keys and intercepted catalogs. It
confirms saved identity across reconstruction/reorder, deterministic missing-model
fallback without preference writeback, account/API isolation, and repeated-load
safety. It is not Android Keystore or physical-process evidence.

ChatConversationTest, ChatViewModelTest and ChatTransportTest confirm blank send
rejection, arbitrary/Unicode input, exactly-once user/assistant append, explicit
request history, New Chat, boundary invalidation, no persistent chat and exclusion
of failed/cancelled/incomplete turns. Limits remain 8192 UTF-16 input units,
12 attempts and 192 KiB serialized replay threshold plus one bounded completion.
Opaque encrypted reasoning and assistant phase/order are retained under D18;
unsupported/non-replayable output fails closed. Requests retain `store=false`,
`stream=true`, and omit `previous_response_id`.

StreamingRaceTest and StreamingHttpCancellationTest confirm lazy Job assignment
before execution, one active owner, mutex-serialized generation/cleanup,
Call.cancel(), response-body closure/unblocked reads, retained partial output,
completed-only finalization, duplicate/late event rejection, idle Stop no-op and
immediate follow-up waiting for cleanup. Intercepted real OkHttp Calls are used;
these tests do not prove a real OpenAI socket/radio interaction.

TransitionTest/ErrorTransitionTest plus existing router/auth/provider suites cover
signed out/authenticated/reauth gates, account/generation/revision changes,
plan/API separation, stale discovery/preference work, missing model blocking Send,
same-model reload preservation and actual-model-change clearing. IO/offline,
plan permission failures, API 401, 429, 5xx, malformed responses and cancellation
retain distinct recovery. Transient failures preserve credentials. Stale callbacks
cannot restore cleared context. The public ERROR_401_REAUTH fixture deliberately
maps a bare plan admission 401 to PERMISSION; it does not prove expired OAuth.

## D. Final emulator acceptance matrix — VERIFIED ON EMULATOR

Existing WristPrompt_Watch7 dynamically identified by AVD name/watch
characteristics; launcher excludes physical devices. All 23 names were listed and
all 23 passed fresh launch ACK + process liveness. Each was then relaunched for
initial and touch-scrolled screenshot inspection. No serious overlap/clipping or
crash was observed; clipped edge rows scroll into view below fixed controls.
Screenshots contain only synthetic content: `/tmp/d3g-visual/` (ephemeral).

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

Billing, Compose/Send/Stop, model selection, user/assistant roles, excluded partials
and long content remain readable in the inspected default-font emulator surfaces.
Short streaming visibly completes; long streaming retains reachable fixed Stop.
Fresh D3-G deep swipes reached Synthetic model 40 / Reload models and the long-
error Connection/settings / Details footer. A fresh tap on Stop during synthetic
long streaming removed Stop, restored Compose and retained the excluded turn.
Both recreation launches returned fresh ACKs with generations=1 and respectively
COMPLETED / CANCELLED. No process persistence or real network generation is
inferred. Regression tests additionally verify clean next-send replay. No hardware/rotary/larger-font result is inferred.

## E. Data Layer emulator status

**D3-G smoke: BLOCKED — ENVIRONMENT.** The runner reported:
`Both named WristPrompt AVDs must be booted and available.` It stopped before
mutating either app. The watch is available; the required pair is not. No lab
repair, pairing, AVD reset or renewed capability investigation was attempted.

Earlier paired evidence is retained, not claimed as freshly rerun: nearby nodes,
matching package/signatures, bidirectional credential-free RPC, correctly packaged
static capabilities and successful local dynamic registration, but empty remote
capability/getAllCapabilities results. Production stops correctly at
NODE_EXISTS_CAPABILITY_MISSING. Classification remains **UNRESOLVED — STRONGLY
ISOLATED TO GMS/EMULATOR**, not a demonstrated Day 3 app regression.
Production capability-dependent watch -> phone auth is **NOT VERIFIABLE ON
CURRENT EMULATOR LAB**. No production fallback was introduced.

## F. Targeted security and code-quality review

- Critical/high: no new finding in the reviewed Day 3 diff, production call paths,
  diagnostics searches, test results and release isolation outputs.
- Medium reliability debt, not a demonstrated security vulnerability: historical
  transient reauthorization exchange/context-expiry anomaly remains unresolved;
  safe diagnostic triage/disposition required before freeze (Day 5).
- Low: existing debug-only WearRecents warnings; no release scenario inclusion.
  Existing broad safe diagnostics can limit diagnosis; no raw-error logging added.

Search/review covered token/key/Authorization and authenticated body logging,
prompt diagnostics, arbitrary Throwable/exception presentation, persistence,
stale account/billing/model boundaries, fallback, production routing and unbounded
history. The only production Android logging sink remains enum-only BridgeLog.
Authorization headers occur at intended HTTPS request construction, not logging.
Conversation DTOs/completed output diagnostics are redacted; raw provider bodies
and exception messages are not used as recovery copy. No new chat disk store,
SavedStateHandle, HTTP logger, unsafe TLS adapter or plaintext secret path found.

No TODO/FIXME, dead preview implementation, duplicated scenario enum, commented
experiment or hardcoded production emulator serial was found in the targeted
search. StreamingPreviewActivity references are historical documentation or
negative isolation checks, not live code. Test serial constants are synthetic
launcher fixtures. The only Day 3 Gradle changes attach shared debug sources;
no new dependency or production Data Layer change. No cleanup/refactor justified.
This is a targeted source/output review, not a claim of exhaustive security proof.

## G. Architecture invariants

- Explicit billing route; no credential fallback on absence or failure.
- Capability-based production discovery remains fail closed; no NodeClient bypass.
- Credential schemas, encrypted storage, signing configuration and OAuth unchanged.
- One bounded in-memory conversation; only current completed exchanges enter replay.
- New Chat/account/generation/billing/model boundaries clear incompatible context.
- Stop cancels the actual transport; cleanup serialized before following generation.
- Debug scenarios have no secure-store, durable model-preference or HTTP dependency;
  empty transient bearer is synthetic, never persisted. Only fixed non-secret ACK
  metadata is written. Connection/key/account actions are inert.
- Debug Activity/classes present in debug, absent from release manifest and DEX;
  production source cannot navigate to debug entrypoints. Routing untouched.

## H. Known debt / unresolved items

1. Historical Terra -> Sol physical model-restoration anomaly remains unreproduced
   and unresolved; integrated regression coverage passes. No speculative fix.
2. Transient reauthorization anomaly remains historical/unresolved despite eventual
   recovery; no proven cause or new physical resolution.
3. Successful funded BYOK inference remains BLOCKED/deferred by the absence of
   funded API credentials. Prior physical fake-key 401/no-fallback evidence and
   current synthetic success/failure coverage remain valid. No purchase required.
4. Emulator CapabilityClient limitation remains as section E; today's unavailable
   pair adds no new capability evidence and does not invalidate earlier isolation.
5. Release/hardware checks below remain open. Passing unit/emulator tests does not
   close the roadmap's physical release gate.

## I. Deferred final physical RC checklist

The post-D3 sanity section above records physical PASS for debug installation,
real authentication handoff, model discovery/normal relaunch, arbitrary multi-turn
chat, streaming/Stop, New Chat and offline recovery. Do not treat these paths as
entirely physically unverified. Full RC acceptance remains open:

- [ ] Checks 7 and 9: actual subsequent server-side context after cancellation,
      and model-change conversation-context boundary (automated/emulator covered).
- [ ] Final sign-out/reauthorization and honest remote-revocation outcome.
- [ ] Rotary, larger fonts, remaining screen/action and hardware-specific checks.
- [ ] Untested account/billing boundary checks and interrupted-stream recovery.
- [ ] Process death, background/screen-off and remaining RC lifecycle/network/
      storage/signing checks; destructive credential/key tests require separate
      exact authorization. Current debug sanity is not release-build acceptance.
- [ ] Successful funded BYOK inference remains unverified unless a funded
      credential becomes available voluntarily; no purchase required.

## J–L. Documentation, roadmap and Git

Added DAY3_REPORT.md; DAY3_STATUS.md now points to this final evidence and labels
its older phase handoffs/checklist as historical/deferred. ROADMAP.md records
completed A–G development/automatic/emulator scope separately from the still-open
physical RC gate and unresolved restoration debt. WEAR_SCENARIOS.md points to
final acceptance. Historical investigation, SPEC, DECISIONS and Day 2 evidence
were reviewed; their facts/contracts are unchanged. No new decision record.

Starting tree: clean at 1c44070. Final intended changes are these four Markdown
files only, unstaged. No product/test/tool change, commit, push or publication.

## M. Day 4 handoff — recommendation only

Start with the existing foreground text-chat lifecycle/device-edge-case audit:
process death intentionally clears chat, retained Activity state, background /
screen-off behavior, phone absence/reconnect and network transitions. Separate
synthetic/emulator evidence from hardware RC checks and preserve protected state.
Optional voice should remain deferred while required real-network/hardware gates
are open; do not treat a Day 3 development pass as physical release readiness.
Day 4 is not started or authorized by this report.

## N. Suggested final Day 3 commit

`test: consolidate Day 3 acceptance evidence and release debt`
