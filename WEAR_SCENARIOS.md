# Wear debug scenario acceptance — canonical guide

Current D6-C catalog: **84 scenarios**. `SAVED_CHATS_HISTORY` adds a synthetic
25-chat history with open/rename/delete and explicit continuation; it performs no
real inference or file persistence. Use it for round-screen/normal/max-font history
interaction. Actual SQLite lifecycle evidence is a separate instrumentation gate.
Historical D5-H catalog: **83 scenarios**: D5-F’s 77 plus six targeted remediation fixtures.
D5-C added 22, D5-D added two model fixtures, and D5-E added four failure/limit fixtures.
The D5-B inventory below has 47 entries; the D5-C inventory appends 22.
This guide owns the current catalog and commands. Historical
phase evidence and limitations remain in [DAY4_STATUS.md](DAY4_STATUS.md);
[DAY3_REPORT.md](DAY3_REPORT.md) records prior presentation acceptance.

## D5-A shell compatibility — 2026-10-30

All 43 canonical identifiers and fixture meanings remain unchanged. The debug
host now uses WristPromptTheme, matching production. Chat has a scrolling
WristPrompt/model/billing/Menu header and fixed Ask/Stop; Ask opens platform input
when no draft exists, then returns to explicit draft review/Send. Composer
recreation still requires Write message to open another composition-owned input.

Connection is labelled Account & Connection. Chat Menu reaches account actions
and Settings & About → Diagnostics → inference proof with inline output. Back
returns through the visited path. Reading positions are retained in the existing
navigation owner, not restored into a fresh process. The old OUTPUT destination
is only an internal compatibility alias to proof, with no separate menu entry.

D5-A adds no scenarios or ACK fields. Readiness still does not prove visual
correctness, rotary, input interaction or hardware behavior. D5 acceptance and
before/after performance limitations are recorded separately in DAY5_STATUS.md.

## Prerequisites and commands

Use the existing JDK/SDK/signing environment in [DEVELOPMENT.md](DEVELOPMENT.md).
Boot the existing **WristPrompt_Watch7** AVD; Android Studio is unnecessary.
Exactly one online watch emulator must be present. Every scenario runner checks
`emulator-*`, watch characteristics and the expected AVD name, excludes physical
devices, and rejects zero/ambiguous watches. Serial numbers are discovered anew.
Run one acceptance command at a time: they share one Activity and acknowledgement.

```sh
export JAVA_HOME="$HOME/.local/share/wristprompt/jdk-17.0.21+9"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_USER_HOME="$HOME/.local/share/wristprompt/android"
./gradlew :wear:assembleDebug
python3 tools/wear_scenarios.py --list
python3 tools/wear_scenarios.py --list --category lifecycle
python3 tools/wear_scenarios.py --install --smoke
python3 tools/wear_scenarios.py --scenario STREAMING_LONG
python3 tools/wear_scenarios.py --scenario ERROR_429 --screenshot /tmp/error_429.png
python3 tools/wear_scenarios.py --all
python3 tools/wear_scenarios.py --all --category recovery
python3 tools/wear_process_restart.py --all
python3 tools/wear_process_restart.py --all --method force-stop
python3 tools/wear_connectivity.py --screenshots /tmp/wristprompt-connectivity
python3 tools/wear_phone_absence.py --screenshots /tmp/wristprompt-phone-absence
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/check_scenario_isolation.py
git diff --check
```

`--install` updates the existing debug APK with `install -r`, preserving app data.
Without it, an installed debuggable package is required. Build/install errors fail
closed. No uninstall, data clearing, credential access or physical-device operation.
Screenshots always use the selected watch. Generic screenshots capture the current
frame; they do not establish visual correctness or settled compositor state.

## Harness architecture and evidence contract

- `wear_scenarios.py`: primary launcher; `--list`, single scenario, 11-case bounded
  `--smoke`, explicit 77-case non-destructive `--all`, category filtering, screenshots.
  Full and smoke runs verify launch, accepted scenario, fresh readiness, RESUMED
  Activity and matching live PID; no OCR, golden images or visual acceptance claim.
  Watch-only scenarios additionally assert bridge lookups/requests are both zero.
- `wear_catalog.py`: category/smoke metadata, exhaustively checked against the
  `DebugScenario` enum. The enum owns public names; this guide owns their meaning.
- `wear_adb.py`: SDK ADB resolution, 30-second command timeout/output capture,
  unique watch discovery, package/debug checks, acknowledgement parser and screencap.
- `wear_process_restart.py`: explicit SEED, background, `am kill`, retained-task
  resume; PID, fixture owner and process owner must all change. It reports before/
  after counts; fresh messages/generations/partials/draft are zero and busy=false.
  `--method force-stop` is a **stronger package-stop/cold-launch** check, never a
  fallback for `am kill`. It preserves installed app data. Durable reconstruction
  is covered by JVM tests; these emulator fixtures are deliberately in memory.
- `wear_connectivity.py`: six **synthetic provider-failure/recovery** sequences;
  restoration alone must not replay, explicit send/reload recovers, failed context
  stays excluded. It does **not toggle radios or network interfaces**.
- `wear_phone_absence.py`: six synthetic phone-absence cases, zero-bridge counters,
  fresh emulator processes and existing rendered-text checks. Its optional stable
  screencap loop is retained; no UI-testing framework was added.
- `emulator_data_layer.py`: separate two-named-AVD, credential-free diagnostic probe.
  It shares ADB execution but keeps its two-device identity checks. It is outside
  D4-F acceptance and was not run; the CapabilityClient investigation stays closed.

The debug manifest alone exposes `DebugScenarioActivity`. `ScenarioOwner` retains
one fixture/production VM across Activity recreation and clears it on final owner
clear. Synthetic account/billing metadata, empty-bearer transient lease and provider
never enter secure repositories, HTTP or production Data Layer. Model callbacks
remain memory-only. Real connection/account/key controls are inert. No production
behavior, credentials, mode, durable model preferences or storage formats change.

Each launch supplies a random 32-hex `launch_id`. The app-private fixed
`debug-scenario-ready.txt` starts with `LAUNCH_ID SCENARIO READY`; READY means
fixture preparation finished, not successful inference. Existing safe metadata
includes generation/discovery/completion/message/character counts, busy/terminal
stage, lifecycle/collection status, fixture/process owners and PID, static model/
billing/error enums, command revision and bridge counters. Owners are random
fixture identifiers, never account IDs. No credentials, token/key values, account
IDs, headers, prompts or response text appear. Shared parsing rejects unknown,
duplicate or malformed fields without echoing contents; tests compare emitted
field names to the explicit safe allowlist. The acknowledgement schema and debug
Activity are unchanged in D4-F.

The Activity-lifetime debug-only receiver checks the launch ID before fixed
commands. Use the dynamically discovered watch serial and current launch ID:

```sh
adb -s EMULATOR_SERIAL shell am broadcast \
  -a io.github.grassified.wristprompt.DEBUG_LIFECYCLE \
  -p io.github.grassified.wristprompt \
  --es launch_id LAUNCH_ID --es command ADVANCE
```

Lifecycle commands: ADVANCE, COMPLETE, RECREATE, STOP; process: SEED;
connectivity: RESTORE_NETWORK, NEXT_REQUEST, RELOAD_MODELS, FAIL_STOP.
Lifecycle streams wait at explicit gates. Background with Home and return to the
existing Activity using reorder-to-front (`am start -f 0x20000`); compare owner,
generation, creation and observedChars metadata. SCREEN_OFF_STYLE_RESUME omits
debug keep-screen-on/turn-screen-on; emulator dozing is not hardware screen-off
continuation evidence. Composer recreation discards pending external input.
Generic launch/full acceptance does not execute these lifecycle command sequences.

## D5-B baseline inventory (47 retained scenarios)

All 47 baseline names below remain manually launchable, useful, and mutate only synthetic
in-memory application state (plus the fixed non-secret acknowledgement file).
Those three properties apply to **every row**. All remain public; none removed or
renamed. “Special” identifies additional acceptance operations beyond generic
launch/readiness. “Overlap” records similar initial states without discarding
ownership or historical regression coverage.

Category keys: presentation = DAY 3 PRESENTATION / CHAT; auth = AUTH / BILLING;
model = MODEL; streaming = STREAMING / CANCELLATION; error = ERROR;
lifecycle = ACTIVITY LIFECYCLE; process = PROCESS RESTART;
connectivity = CONNECTIVITY; phone = PHONE INDEPENDENCE;
recovery = RECOVERY / PERSISTENCE PRESENTATION.

| Canonical name | Category | Smoke | Special acceptance | Overlap / reason retained | Purpose / initial state |
| --- | --- | --- | --- | --- | --- |
| SIGNED_OUT | auth | Yes | None | Distinct presentation/state | Inaccessible inference; Connection/settings, inert sign-in action |
| CHATGPT_AUTHENTICATED | auth | No | None | Same empty chat; generic versus absent-companion/bridge-spy contract | Synthetic connected account, explicit plan billing, ready chat |
| API_KEY_CONFIGURED | auth | No | None | Distinct presentation/state | Synthetic configured display state, explicit API billing, ready chat |
| MODELS_LOADING | model | No | None | Distinct presentation/state | Provider catalog suspended until cancellation; real LOADING/Stop |
| MODELS_LOADED | model | No | None | Distinct presentation/state | Three models, production picker |
| MANY_MODELS | model | Yes | None | Distinct presentation/state | Forty models, production picker |
| MODEL_UNAVAILABLE | model | No | None | Distinct presentation/state | NO_MODELS failure, selection cleared, Send blocked; Reload succeeds synthetically |
| EMPTY_CHAT | presentation | No | None | Same empty chat; generic versus absent-companion/bridge-spy contract | Real empty conversation/composer entry |
| LONG_USER_PROMPT | presentation | No | None | Distinct presentation/state | 8,192-character draft, at the inclusive product input bound; Composer |
| ONE_TURN_COMPLETED | presentation | No | None | Distinct presentation/state | One production completed exchange |
| MULTI_TURN_CHAT | presentation | Yes | None | Distinct presentation/state | Four completed exchanges, replay input sizes 1/3/5/7 |
| LONG_ASSISTANT_OUTPUT | presentation | No | None | Distinct presentation/state | Eighty completed sections |
| STREAMING_SHORT | streaming | No | None | Distinct presentation/state | Three sections emitted in bounded chunks at 400 ms intervals |
| STREAMING_LONG | streaming | Yes | None | Distinct presentation/state | Eighty sections emitted through the same real streaming/session path |
| STOPPED_PARTIAL | streaming | Yes | None | Distinct presentation/state | Real send/delta/Stop; partial retained, user excluded, next send available |
| ERROR_401_REAUTH | error | No | None | Distinct presentation/state | Production bare plan 401 mapping: PERMISSION; account/plan permission guidance |
| ERROR_401_API_KEY | error | No | None | Distinct presentation/state | Production API-mode 401 mapping: INVALID_API_KEY, no fallback |
| ERROR_429 | error | Yes | None | Distinct presentation/state | Production plan USAGE_LIMIT/Manage usage guidance; no reset timestamp |
| ERROR_503 | error | No | None | Distinct presentation/state | Production TEMPORARY guidance; account state retained |
| ERROR_OFFLINE | error | No | None | Static error presentation; connectivity runner adds no-replay assertions | Production NETWORK category; explicit compose/send retry path |
| LONG_ERROR_MESSAGE | error | No | None | Distinct presentation/state | TEMPORARY failure plus repeated static production recovery copy, chunked by shared renderer |
| RECREATED_COMPLETED_STATE | recovery | No | Automatic Activity recreation | Terminal fixture plus retained Activity ownership | Completes once, automatically recreates Activity with same retained owner |
| RECREATED_CANCELLED_STATE | recovery | Yes | Automatic Activity recreation | Terminal fixture plus retained Activity ownership | Stops once, automatically recreates; cancellation/partial remain, no new generation |
| ACTIVE_STREAM_RECREATION | lifecycle | Yes | Lifecycle commands / platform input | Distinct presentation/state | One gated stream; automatic retained Activity recreation; production Stop and renderer |
| BACKGROUNDED_STREAM | lifecycle | No | Lifecycle commands / platform input | Distinct presentation/state | One gated stream; Home allowed; latest output observed on foreground return |
| SCREEN_OFF_STYLE_RESUME | lifecycle | No | Lifecycle commands / platform input | Distinct presentation/state | Gated stream with no debug keep-screen-on or turn-screen-on request; emulator sleep/resume |
| COMPOSER_RECREATION | lifecycle | No | Lifecycle commands / platform input | Distinct presentation/state | Empty production Composer; open system text input and recreate the host; returned text deliberately discarded |
| PROCESS_RESTART_IDLE | process | Yes | Process runner: seed/terminate/resume | Distinct presentation/state | Fresh fixture is empty; explicit SEED selects debug-2 in memory; restart returns to default debug-1 |
| PROCESS_RESTART_AFTER_COMPLETED | process | No | Process runner: seed/terminate/resume | Distinct presentation/state | Explicit SEED creates one completed synthetic exchange; a new fixture never seeds itself |
| PROCESS_RESTART_DURING_ACTIVE | process | No | Process runner: seed/terminate/resume | Distinct presentation/state | Explicit SEED starts one request before any delta; suspended until owner/process ends |
| PROCESS_RESTART_AFTER_PARTIAL | process | No | Process runner: seed/terminate/resume | Distinct presentation/state | Explicit SEED starts one request and emits a partial; suspended until owner/process ends |
| NETWORK_LOST_BEFORE_SEND | connectivity | No | Connectivity runner: explicit restore/recover | Same initial failure; preserved pre-send versus explicit-recovery entrypoints | NETWORK, FAILED, visible excluded user attempt, no assistant |
| NETWORK_LOST_DURING_STREAM | connectivity | No | Connectivity runner: explicit restore/recover | Distinct presentation/state | INTERRUPTED, retained partial, excluded attempt, no completion |
| NETWORK_RECOVERED | connectivity | No | Connectivity runner: explicit restore/recover | Same initial failure; preserved pre-send versus explicit-recovery entrypoints | Same pre-dispatch failure, held until commands |
| MODEL_DISCOVERY_OFFLINE | connectivity | No | Connectivity runner: explicit restore/recover | Same initial failure; preserved offline versus explicit-reload entrypoints | NETWORK, failed discovery, no catalog/selection |
| MODEL_DISCOVERY_RECOVERED | connectivity | No | Connectivity runner: explicit restore/recover | Same initial failure; preserved offline versus explicit-reload entrypoints | Same discovery failure, held until commands |
| STOP_NETWORK_RACE | connectivity | No | Connectivity runner: explicit restore/recover | Distinct presentation/state | Partial stream waits at a deferred loss gate |
| PHONE_ABSENT | phone | No | Phone runner: bridge-zero/rendering | Same empty chat; generic versus absent-companion/bridge-spy contract | Same valid authenticated state with no inappropriate phone blocker |
| WATCH_ONLY_AUTHENTICATED | phone | Yes | Phone runner: bridge-zero/rendering | Same empty chat; generic versus absent-companion/bridge-spy contract | Usable local plan state, companion unavailable, normal model/Compose access |
| WATCH_ONLY_CHAT | phone | No | Phone runner: bridge-zero/rendering | Distinct presentation/state | Two completed synthetic turns; follow-up includes completed context |
| WATCH_ONLY_REFRESHED | phone | No | Phone runner: bridge-zero/rendering | Distinct presentation/state | One synthetic lease-renewal marker before catalog; normal authenticated state |
| REAUTH_REQUIRED_PHONE_ABSENT | phone | Yes | Phone runner: bridge-zero/rendering | Distinct presentation/state | REAUTHORIZE, no model/inference; existing Authentication required / Connection recovery UI |
| API_KEY_WATCH_ONLY | phone | No | Phone runner: bridge-zero/rendering | Distinct presentation/state | Configured API route, visible separate billing, completed synthetic chat |

| DENSE_LONG_ANSWER | presentation | No | Latest / rotary / New Chat | D5-B plain-text fixture | 200 paragraphs near the current output limit; production range projection and lazy rows |
| LONG_UNBROKEN_TEXT | presentation | No | Latest / rotary | D5-B plain-text fixture | 60,023-character synthetic URL/token; bounded layout, no network action |
| UNICODE_LONG_RESPONSE | presentation | No | Latest / rotary | D5-B plain-text fixture | Turkish, Arabic, Chinese, combining accents, ZWJ family, flags and skin tone sequences |
| STREAMING_FINE_GRAINED | streaming | No | Latest / Stop / lifecycle | D5-B plain-text fixture | 11,000 one-character deltas at nominal 1 ms intervals; production ViewModel/session/presentation; no real transport |

Category counts: presentation 8, auth 3, model 4, streaming 4, error 6,
lifecycle 4, process 4, connectivity 6, phone 6, recovery 2 = **47**.
The smoke set is exactly the 11 “Yes” rows; process smoke checks only the fresh
fixture, while the specialized runner verifies actual PID changes.

No scenario consolidation was justified: removing visually similar public
entrypoints would discard named baseline acceptance paths while saving no fixture
logic (the implementations already share their provider/VM setup). Recreated
completed/cancelled states test retained ownership; process cases test fresh
ownership. Generic authenticated state differs from an absent-companion spy.
Offline errors differ from restore/no-replay command sequences. Recovered/offline
pairs deliberately share initial states and receive the same full transition
assertions; they remain compatible public names, not newly added fixtures.

ERROR_401_REAUTH is a historical public name: bare plan admission 401 still maps
to PERMISSION, not terminal renewal failure. REAUTH_REQUIRED_PHONE_ABSENT covers
actual reauthorization presentation. WATCH_ONLY_REFRESHED is synthetic renewal
presentation only; actual refresh/atomicity boundaries are tested on the JVM.
No additional recovery fixture was needed for D4-E: secure-state faults,
reconstruction and refresh/sign-out atomicity remain integration-test coverage.

## Release isolation, labels and limitations

The checker requires Activity/classes, acknowledgement filename and command action
in debug manifest/DEX and absent from release; all 69 public names must be absent
from release DEX. It rejects production runtime scenario/debug-condition references
and harness dependencies on real credentials/repositories/storage/network clients.
Synthetic fixture sources cannot import Android/file storage; source and binary
checks complement the every-scenario preparation/negative tests. Release manifests
cannot resolve the scenario Activity. This is static/binary evidence, not a claim
of launching the release APK on a physical device.

- **VERIFIED AUTOMATICALLY**: JVM/host tests, builds, lint and source/binary checks.
- **VERIFIED ON EMULATOR**: actually executed ADB/fixture acceptance, bounded by
  each runner's assertions. Readiness/liveness is not visual or real-network proof.
- **PHYSICAL / VERIFIED PHYSICALLY**: only separately collected hardware evidence.
  No Day 4 physical acceptance is performed in D4-F.
- **BLOCKED — EMULATOR LIMITATION**: capability-dependent production paired auth
  handoff remains NOT VERIFIABLE ON CURRENT EMULATOR LAB. See
  [EMULATOR_CAPABILITY_INVESTIGATION.md](EMULATOR_CAPABILITY_INVESTIGATION.md).

No real radio-transition, real OpenAI request, funded BYOK-success, production
Data Layer auth/handoff or hardware Keystore/AtomicFile result follows from these
fixtures. Screen-off continuation remains best effort. Physical lifecycle/network/
watch-only and persistence acceptance awaits D4-G / targeted hardware session.
Terra -> Sol restoration and transient reauthorization anomalies remain open;
this consolidation changes neither their evidence nor disposition.

## D5-B presentation acceptance — 2026-10-30

The original 43 public names and 11-name smoke set are preserved; four additive
fixtures bring the catalog to 47. These use the same production chat renderer,
canonical conversation/session and synthetic provider, with no credentials or
network. D5-C Markdown fixtures are intentionally absent.

Latest is explicit and appears while transcript content lies below the viewport.
It shares the fixed Ask/Stop band. At large font it uses a downward arrow with the
accessible label Latest, preserving Stop's separate target. Latest centers the
last row then finishes at its bottom, including rows taller than the viewport.
There is no follow timer or automatic scroll on incoming text.

Presentation fixtures also accept the existing explicit NEXT_REQUEST command
(after New Chat), STOP and RECREATE through DEBUG_LIFECYCLE with the current
launch_id. REPORT refreshes only the safe acknowledgement, without generation.
The acknowledgement adds aggregate presentationFlushes, presentationSegments and
presentationChars; it never contains prompt/answer text. Counters belong to the
retained debug owner and survive recreation. Fine-grained acknowledgements are
event-throttled to 100 ms for unchanged stages; terminal/forced REPORT is immediate.
This is no idle timer and adds no production reporting/storage.

DAY5_STATUS.md owns D5-B exact test counts, actual emulator interactions,
performance samples and limitations. These fixtures establish neither real
OpenAI latency/usage nor physical watch behavior. Physical UX remains D5-G.

## D5-C native Markdown — 2026-10-30

All 47 baseline identifiers and the 11-case smoke set are preserved. The following
22 credential-free/network-free fixtures bring the catalog to **69**. Category
`markdown` selects only these additions. They use the production conversation,
coalescer, retained Markdown worker and native renderer. ACK readiness still does
not prove rich conversion or visual acceptance; DAY5_C_REPORT.md records the
separate actual interaction/visual checks. No new ACK fields or content logging.

| Name | Purpose |
| --- | --- |
| MARKDOWN_BASIC | Compact headings, bold/italic/code, quote, divider, ordered and unordered lists, Unicode/RTL. |
| MARKDOWN_LONG | Long structured answer with headings and native inline spans. |
| MARKDOWN_CODE | Monospace fence, indentation, original newlines and ordinary surrounding text. |
| MARKDOWN_CODE_LONG_LINE | Horizontal touch scroll inside normal vertical transcript. |
| MARKDOWN_OPEN_FENCE | Terminal unclosed fence and retained literal code tail. |
| MARKDOWN_TABLE | First-column headings with ordered label/value records; unrelated to model picker. |
| MARKDOWN_TABLE_MALFORMED | Missing header/cells and excess cells; exact plain fallback preserves excess. |
| MARKDOWN_TABLE_OVER_LIMIT | 110 rows force complete segmented plain-source fallback. |
| MARKDOWN_LINK | Underlined labels; explicit internal destination/host inspection. |
| MARKDOWN_BLOCKED_LINK | Inert script/intent/file/data destinations. |
| MARKDOWN_RAW_HTML | Script/image/anchor/style markup remains literal text. |
| MARKDOWN_IMAGE | Alt text only; no image loading or destination action. |
| MARKDOWN_DEEP_NESTING | Parser depth limits and bounded visual list/quote depth. |
| MARKDOWN_DELIMITER_STRESS | Repeated malformed delimiters; bounded parsing/fallback. |
| STREAMING_MARKDOWN | Eight-character chunks at 100 ms; current answer plain until terminal rich reconciliation. |
| STREAMING_MARKDOWN_OPEN_SYNTAX | Open emphasis/fence, then suspended until explicit Stop. |
| INTERRUPTED_MARKDOWN | Retained partial Markdown, real production FAILED/INTERRUPTED and exclusion. |
| STOPPED_MARKDOWN | Actual production Stop; partial retained and excluded. |
| TERMINAL_MARKDOWN_REPLACEMENT | Terminal authoritative replacement differs from delta preview and wins. |
| MARKDOWN_LIST_LONG | 200 ordered list items with inline styles and wrapping. |
| MARKDOWN_MALFORMED | Alternative named malformed-input workload; shares deliberate delimiter-stress data. |
| MARKDOWN_LONG_URL | Oversized destination triggers exact segmented plain fallback. |

All Markdown fixtures accept REPORT, STOP, RECREATE and explicit NEXT_REQUEST via
the existing launch-bound debug receiver. NEXT_REQUEST still requires explicit
user/harness action and does not bypass the production busy guard. New Chat is
available through the production Menu. No external browser action exists.

The settled prefix consists of previously completed answers; an active answer
uses D5-B's plain Unicode-safe segments. There is no streaming full-document parse
or blank-line settlement heuristic. Terminal reconciliation runs on Default with
one active conversion and one replaceable queued snapshot. Pause/identity checks
reject stale results. Parser budgets, cache accounting, rich fallback and exact
dependency/license details are recorded in DECISIONS.md.

D5-C acceptance performs **no physical tests**. Physical readability/accessibility
and performance remain D5-G; release profiling remains D5-F. `LATEST_AVAILABLE`
was not a pre-existing scenario identifier: explicit Latest is exercised using
long/streaming fixtures and the unchanged D5-B control, without inventing an alias.

## D5-D picker boundaries

Two additional offline debug-only fixtures (71 total):

| Scenario | Category | Behavior |
| --- | --- | --- |
| THOUSAND_MODELS | model | 1,000 synthetic discovered names in service order; first selected; production lazy picker. |
| LONG_MODEL_NAMES | model | Three long synthetic authoritative names; production three-line wrapping and selected semantics. |

MODEL PICKER POLICY: Only authoritative model name + selected state.
No speculative metadata. MANY_MODELS remains 40 rows; MODELS_LOADED remains three.
No actual model metadata, credentials, HTTP or protected persistence is involved.
D5-D evidence/limitations are in DAY5_D_REPORT.md; no physical acceptance implied.

## D5-E error/loading/recovery fixtures — 2026-10-30

Existing IDs are preserved. Reuse ERROR_OFFLINE, ERROR_503, ERROR_401_REAUTH
(bare plan 401 = permission, not terminal reauth), ERROR_401_API_KEY, ERROR_429,
MODEL_UNAVAILABLE, MODEL_DISCOVERY_OFFLINE, STOPPED_PARTIAL,
NETWORK_LOST_DURING_STREAM, SIGNED_OUT, REAUTH_REQUIRED_PHONE_ABSENT,
INTERRUPTED_MARKDOWN, STOPPED_MARKDOWN, ONE_TURN_COMPLETED and MODELS_LOADING.
LONG_ERROR_MESSAGE now uses the same concise production failure as ERROR_503;
its former debug-only repeated engineering warning was removed intentionally.

Four additive debug-only IDs fill presentation gaps:

| Fixture | Deterministic state |
| --- | --- |
| ERROR_API_LIMIT | Explicit API-key mode, API_LIMIT, no ChatGPT billing/fallback. |
| INCOMPLETE_PARTIAL | Delta then INCOMPLETE, partial retained and excluded. |
| CONVERSATION_LIMIT | Twelve completed synthetic turns through the real owner; New Chat required. |
| SECURE_STORAGE_FAILURE | Synthetic unusable access plus storage-error presentation; zero store access, discovery or generation. |

Every-fixture JVM preparation covers all 75. The D5-E emulator run targets these
four plus the reused cases above; it does not run the entire catalog or close
D5-F full acceptance. Phone-absence rendered-copy assertion now expects
Sign in again to continue; behavior assertions are unchanged. Copy, visual/font,
rotary/action evidence and limitations are recorded in DAY5_E_REPORT.md.

## D5-F stress additions — 2026-10-30

- `SINGLE_MODEL`: Models opens with exactly one authoritative synthetic name, selected,
  and the existing explicit Reload models action. No new picker behavior.
- `MARKDOWN_CODE_STRESS`: completed mixed prose, a 2,040-unit line and 2,000 code
  lines (under the existing 65,536-unit output bound), followed by prose. Uses the
  same bounded Markdown conversion and native horizontal-only code surfaces.

All earlier identifiers/meanings are retained. These fixtures remain debug-only,
with no credential/repository/network use. D5-F performance/accessibility evidence,
readiness-versus-interaction limits and the physical D5-G checklist are in
[DAY5_F_REPORT.md](DAY5_F_REPORT.md). No new ACK fields or lifecycle commands.

## D5-H focused remediation — 2026-10-31

Use `--list --category remediation` to list the six added fixtures:
STREAM_TO_COMPLETED_PLAIN, STREAM_TO_COMPLETED_MARKDOWN, STREAM_TO_COMPLETED_LONG,
SINGLE_LONG_PARAGRAPH, REAL_MULTI_PARAGRAPH, DIAGNOSTIC_PROOF_CURRENT_ATTEMPT.
The three stream fixtures feed a terminal empty-output summary through the real
ResponseEvents decoder, rather than injecting a preassembled final string.
Diagnostics runs two synthetic tests and shows the second successful attempt.
Reuse STOPPED_MARKDOWN, MARKDOWN_CODE and MARKDOWN_TABLE; use EMPTY_CHAT at font
scale 2.0 for the header. Do not run the full catalog for this phase.

Readiness is not terminal or visual acceptance: wait for completion, inspect the
answer, and verify the Menu action at maximum font. Restore font scale afterward.
No physical-device operations. Evidence: [DAY5_H_REPORT.md](DAY5_H_REPORT.md).
