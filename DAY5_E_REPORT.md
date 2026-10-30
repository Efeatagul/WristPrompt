# Day 5 Phase E — error/loading/recovery UX — 2026-10-30

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.**

D5-E only. Presentation mapping, concise copy and one context-specific Chat action
replace duplicate engineering guidance. Underlying domain/transport, authentication,
storage, billing, replay and model semantics are preserved. No physical testing,
commit or push. D5-F/G and D6 remain unstarted. Final evidence below distinguishes
automated tests from synthetic emulator presentation.

## B. Final error / recovery matrix

Automatic behavior for **every row: none**. Actions require an explicit tap.
Ask means compose/review/Send, never resend or regeneration. Account & Connection
is navigation only. Manage usage opens the existing usage guidance in Diagnostics;
it does not start OAuth or automatically contact a phone. Reload models invokes
the existing manual command only with usable current access.

| Condition | Title | Explanation | Action |
| --- | --- | --- | --- |
| Signed out / no plan connection | Sign in to continue | — | Account & Connection |
| Grant requires reauthorization | Sign in again to continue | — | Account & Connection |
| Reauthorization already in progress | Finish sign-in on phone | — | Account & Connection |
| Missing API key | Set up an API key to continue | — | Account & Connection |
| Rejected API key | API key rejected | Check your OpenAI API key. | Account & Connection |
| API rate/credit/billing classification | API usage unavailable | Check your OpenAI API account or billing. | Account & Connection |
| Plan usage limit | ChatGPT usage limit reached | — | Manage usage |
| Plan usage unavailable | ChatGPT usage unavailable | — | Manage usage |
| Plan eligibility | ChatGPT plan not eligible | — | Manage usage |
| Plan disabled | ChatGPT plan usage disabled | — | Account & Connection |
| Permission / admission failure | Account access unavailable | Check the selected account's access. | Account & Connection |
| Temporary service failure | Service unavailable | Try again with a new message later. | Ask |
| Network before output | Couldn't connect | Check the watch's internet connection. | Ask |
| Unavailable model | Model unavailable | — | Reload models |
| Account/catalog stale | Account changed | Reload models to continue. | Reload models |
| Protected storage failure | Secure storage unavailable | Requests are blocked to protect your account. | Diagnostics |
| Stream interrupted | Response interrupted | This response won't be used for future replies. | Ask |
| Incomplete terminal | Response incomplete | This response won't be used for future replies. | Ask |
| User Stop | Response stopped | This response won't be used for future replies. | Ask |
| Renewal not yet usable | Account temporarily unavailable | Start a new message later. | Ask |
| Configuration | Account needs attention | — | Account & Connection |
| Invalid request | Message or model unavailable | Review a new message or choose a model. | Ask |
| Response protocol failure | Couldn't read the response | Start a new message later. | Ask |
| Unclassified failure | Couldn't send message | — | Ask |
| Catalog loading | Loading models… | — | No recovery action; existing Stop can cancel loading |
| Catalog network/service/protocol failure | Couldn't load models | — | Reload models |
| Bounded conversation/context limit | Start a new chat to continue | — | New Chat |
| Completed response | No status row | Finished answer only | Normal Ask |
| Markdown rendering fallback | No error row | Exact source through plain fallback | Normal Ask |

For a nonterminal-category failure after partial output, retain that category's
copy and append the same exclusion note. Do not infer a lost network when the
classification only establishes interruption. No invented reset times/quotas.
At the conversation limit, New Chat takes precedence over Ask; a failed/stopped
final attempt still retains its own terminal label and exclusion note as well as
the new-chat requirement. Shared permission wording intentionally does not claim
that an API 403 is a ChatGPT failure. Route-specific key and usage categories stay
distinct; the explicit current billing disclosure is unchanged.

## C. Completed / stopped / interrupted / incomplete presentation

Completed chat already had no permanent Completed row; regression coverage now
asserts it explicitly. Only response.completed remains success. Partial text is
preserved without an empty assistant card before output. A single terminal row
contains the title and exclusion note; the redundant recovery prose row is gone.
Primary Ask opens the existing composer. No previous prompt is automatically sent.
A twelfth stopped/failed attempt preserves both terminal and context-limit meaning.
Inference proof remains a technical diagnostic screen and retains proof outcomes.

## D. Auth / reauth UX

Chat setup distinguishes Sign in to continue from Sign in again to continue.
Both route to Account & Connection. OAuth starts only through the existing explicit
Continue action there. Selected account labels are replaced by Sign-in required
while reauthorization/storage/sign-out makes them unusable; authenticated shell
identity remains hidden by the existing D4-H gates. Account selection semantics,
retained registration, sign-out and revocation safety information are preserved.
No setup request is started by merely viewing a failure.

## E. ChatGPT plan vs API error UX

API key rejected and API usage unavailable are separate from ChatGPT usage limit
reached. No subscription-coverage suggestion, automatic mode change or credential
fallback. Bare plan admission 401 remains PERMISSION; it is not relabelled as a
terminal refresh failure. A generic permission error uses account-neutral wording
because API 403 can share that taxonomy. No domain classification was changed.

## F. Model loading / unavailable UX

Static Loading models… replaces proof/checking copy on normal model surfaces.
Catalog network/service/protocol failure is Couldn't load models. Model unavailable
retains its own title. Remove the duplicate empty-catalog warning. The model screen
has one recovery command, gated by current access/busy state. Existing name-only
rows, order, selection semantics and manual model selection are untouched.
No automatic reload, switch, observer or discovery loop.

## G. Storage / security failure UX

Secure storage unavailable / Requests are blocked to protect your account.
Diagnostics is the recovery destination, with no promise that it repairs storage.
Account & Connection also exposes that explanation and Diagnostics when blocked.
Actual storage, Keystore, fail-closed access and credential records are unchanged.
Synthetic storage fixture never opens a store or starts inference/discovery.

## H. Conversation-limit UX

Start a new chat to continue with explicit New Chat. Existing completed answers
remain visible. No silent truncation, auto-reset or new persistence. Composer also
shows the same requirement and a reachable explicit New Chat action.

## I. Markdown failure / fallback UX

Existing D5-C parser/worker/cache architecture is untouched. A new integrated
presentation test forces conversion failure after successful completion and asserts
exact source fallback, completed stage, no error/status row and no recovery action.
Stopped/interrupted/incomplete terminal metadata remains separate from rich blocks.

## J. Error presentation architecture

ErrorPresentation.kt owns exhaustive ApiProblem -> title/explanation/action mapping,
plus pure chat-terminal and model-catalog projections. RecoveryAction contains
labels for existing explicit commands. ShellPresentation adds setup/security
presentation metadata while retaining all existing access eligibility conditions.
Chat's fixed action uses setup/security priority before chat/catalog recovery.
ChatPresentation carries recovery metadata through unchanged Markdown copies.
No UI strings were moved into transport/domain code. Diagnostics keep existing
allowlisted category/status/HTTP details; no raw body, exception or new logging.
Durable presentation boundary recorded in DECISIONS D28.

## K. Performance / battery review

No dependency, timer, polling, observer, animation, background retry, service,
WorkManager or wake lock added. No D5-B cadence/ownership or D5-C parsing changes.
The fixed action observes a distinct recovery-action projection, not transcript
text. Normal stream deltas yield null metadata without reparsing Markdown or
rebuilding model resources. Terminal strings are bounded pure presentation work.
This is a structural review, not measured battery/performance evidence; profiling,
heap characterization and release/debug comparison remain D5-F.

## L. Automated test results — VERIFIED AUTOMATICALLY

Full Wear: **456 tests / 51 suites, zero failures/errors/skips**. D5-D baseline was
441 / 50; ErrorPresentationTest adds 15 tests. Existing deliberate-copy assertions
were updated in ErrorTransition, PhoneIndependence, ChatPresentation,
ShellPresentation and debug fixture tests; their behavioral assertions remain.

| Requested regression group | Tests in full run |
| --- | ---: |
| D5-E ErrorPresentationTest | 15 |
| ApiProblem FailureMatrix + ErrorTransition | 5 |
| D4-C ConnectivityTransitions + ConnectivityCredential | 9 |
| D4-D PhoneIndependence | 6 |
| D4-E PersistenceRecovery + ProtectedFailureMatrix + ModelRestorationIntegration | 32 |
| D4-H WatchSignOut + SignOutPresentation | 15 |
| D5-B ChatPresentation + PresentationCoalescer | 36 |
| D5-C MarkdownProjection | 42 |
| D5-D ModelItems + ShellPresentation | 16 |
| DebugScenarios (includes every-fixture preparation for 75 IDs) | 17 |

Debug/release builds pass. Release is unsigned and local only. Lint: **zero
errors/fatals, six existing warnings**. Release source/manifest/DEX scenario
isolation passes. Python: **40 tests**, syntax **13 files**, pass. Mobile tests
were not run: no shared/mobile/dependency change. git diff --check and all **26 changed/new text files** pass whitespace checks,
including preserved D5-D files.

Validation cadence: focused tests during implementation. The first full run had
455 passes and one obsolete Authentication required assertion. After correcting
that assertion, its six-test suite passed, then one full green 456-test run was
recorded. No full emulator catalog run. The final account-neutral permission-copy
correction was verified by the 17-test ErrorPresentation/ErrorTransition subset
and affected debug/release/lint checks; the full suite was not repeated for that
string-only correction. Retained green-full XML: /tmp/d5e-green-full-results.

Commands actually run using DEVELOPMENT.md's existing JDK/SDK/cache/signing env:

```sh
./gradlew :wear:testDebugUnitTest --tests '*ErrorPresentationTest' --tests '*ChatPresentationTest' --tests '*ShellPresentationTest' --tests '*DebugScenariosTest' --tests '*ErrorTransitionTest' --console=plain
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
./gradlew :wear:testDebugUnitTest --tests '*PhoneIndependenceTest' :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
./gradlew :wear:testDebugUnitTest --console=plain
./gradlew :wear:testDebugUnitTest --tests '*ErrorPresentationTest' --tests '*ErrorTransitionTest' :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_scenario_isolation.py
git diff --check
```

Python syntax used ast.parse for all 13 tools/*.py files, avoiding cache writes.
Initial sandbox restrictions prevented Gradle cache locking and ADB sockets;
permitted elevated runs used the existing environment. No cache/key migration.
Logs: /tmp/d5e-narrow.log, /tmp/d5e-final-validation.log,
/tmp/d5e-final-repair.log, /tmp/d5e-final-tests.log,
/tmp/d5e-permission-validation.log, /tmp/d5e-python.log.

## M. Emulator acceptance

**VERIFIED ON EMULATOR — synthetic production-renderer fixtures only**, named
WristPrompt_Watch7, dynamically selected WATCH_SERIAL. Existing signing/data
preserved with install -r. No entire-catalog run or physical PASS claim.

Nineteen targeted fixtures at both font scales: **38/38 launch/readiness checks**,
matching current PID and RESUMED state, with hierarchy and before/end captures.
Reviewed failure/terminal/model screenshots and text confirm:

| Cases | Observed result |
| --- | --- |
| ERROR_OFFLINE / ERROR_503 | Concise standalone failure, Ask, no assistant card. |
| SIGNED_OUT / REAUTH_REQUIRED_PHONE_ABSENT | Direct Chat check distinguishes sign-in/reauth, Account action, no authenticated identity or bridge calls. |
| ERROR_401_REAUTH | Permission remains distinct from terminal reauth. Final neutral Account access unavailable rechecked at both scales. |
| ERROR_401_API_KEY / ERROR_429 / ERROR_API_LIMIT | Distinct key/plan/API copy and appropriate Account or Manage usage action. |
| MODEL_UNAVAILABLE / MODEL_DISCOVERY_OFFLINE | One manual Reload models; no duplicate empty-catalog warning. |
| STOPPED_PARTIAL / NETWORK_LOST_DURING_STREAM / INCOMPLETE_PARTIAL | Partial retained; distinct terminal title and exclusion note, Ask. |
| CONVERSATION_LIMIT | Retained completed answers, new-chat requirement and New Chat action. |
| INTERRUPTED_MARKDOWN / STOPPED_MARKDOWN | Rich partial text plus separate exclusion/terminal metadata. |
| SECURE_STORAGE_FAILURE | Fail-closed explanation and Diagnostics; zero discoveries/generations. |
| ONE_TURN_COMPLETED / MODELS_LOADING | No completion row; one static catalog-loading label with existing Stop. |

Main screenshot pass preceded the final permission string correction only. That
affected fixture and auth navigation were recaptured on the final APK at both
scales; unchanged screens remain evidence for identical presentation code.

Actual UI action checks on the final APK:

- Reload models: discovery 1 -> 2, generation remains 0; model becomes selected.
- Ask after network failure: composer/platform input opens, generation remains 1;
  no resend. No text was entered or sent.
- New Chat at limit: messages -> 0; generation remains 12.
- Rotary moves from header/user rows into streaming answer; Stop stays reachable.
  Stop leaves CANCELLED, 1,200 partial characters, excluded=1, assistants=0,
  generations=1, completions=0. Latest reaches stopped/exclusion metadata.
- Secure-storage Diagnostics reachable at both font scales; discoveries=0,
  generations=0. Usage action opens existing Diagnostics guidance without bridge
  requests; rejected API key opens Account & Connection without mode changes.

Evidence: /tmp/d5e-emulator.log and /tmp/d5e-emulator; /tmp/d5e-actions.log,
/tmp/d5e-final-actions.log, /tmp/d5e-settled.log and /tmp/d5e-actions. Temporary acceptance scripts:
python3 /tmp/d5e_emulator.py, /tmp/d5e-actions.py, /tmp/d5e-final-actions.py.
These are bounded synthetic presentation/interaction checks, not real radio,
phone OAuth, billing/quota or protected hardware-storage tests.

## N. 2× font / rotary result

Normal and 2.0 font-scale screens use native scrolling and full font scaling.
Titles/explanations remain reachable by scrolling, and fixed actions remain
reachable. Native rotary moved the viewport at both scales; normal-font streaming
also retained Stop/Latest through terminal transition. At 2x, long Account &
Connection and Diagnostics labels wrap, sometimes within a word; no automatic
font shrink or permanent action clipping was introduced. Large labels take more
vertical space, so title and explanation can require separate viewport positions.
This is emulator evidence, not physical max-font/TalkBack acceptance. Font scale
was restored to 1.0 after each run. One transient black capture was replaced by a
settled reauth capture; hierarchy evidence and stable captures are distinguished.

## O. Security / routing invariants

No production auth, credential/storage, router, Data Layer, OpenAI/provider,
protocol, session/ViewModel, conversation, manifest, dependency or signing source
changed. No automatic inference retry/discovery/OAuth/billing fallback. Existing
completed-only context, cancellation, generation/lease guards and phone-independent
inference remain authoritative. No real app data was cleared; debug installation
uses install -r. No physical device operation. Safe Diagnostics are preserved.

## P. Production changes

All production edits are in wear/.../ui: new ErrorPresentation metadata;
ShellPresentation setup gates/copy; ChatPresentation terminal copy and recovery
metadata; ChatItems removes old scattered guidance; ChatScreen concise setup and
conversation-limit action; WearShell recovery command wiring, fixed action and
model-catalog status; ConnectionItems storage destination and unusable identity.
No model-row redesign or request owner changes.

## Q. Known debt

Native 2x long action labels can wrap within words; no custom typography shrinking
was added. No TalkBack/physical maximum-font, hardware storage failure or real network/billing
acceptance claim. Existing proof/diagnostic technical detail remains intentionally
secondary. Manage usage retains the existing guidance screen rather than adding a
new handoff. Round-screen content can leave the viewport during scrolling; this is
not treated as permanent clipping. Historical D4 and D5-A–D release/device debt
remains. Temporary emulator evidence is local and may be removed by the host.

## R. Deferred to D5-F/G

D5-F: consolidated performance profiling, heap characterization, release/debug
comparison, accessibility/TalkBack and full scenario acceptance. D5-G: physical
Galaxy Watch UX, including maximum-font/rotary and failure recovery. D6 persistent
chat/history remains unstarted. No pending physical check blocks this presentation
phase or authorizes the next phase.

## S. Files changed

D5-E additions: DAY5_E_REPORT.md; ui/ErrorPresentation.kt;
test/.../ui/ErrorPresentationTest.kt.

D5-E modified production UI: ChatItems.kt, ChatPresentation.kt, ChatScreen.kt,
ShellPresentation.kt, ConnectionItems.kt, WearShell.kt.

D5-E modified test/debug/tool files: openai/ErrorTransitionTest.kt,
openai/PhoneIndependenceTest.kt, ui/ChatPresentationTest.kt,
ui/ShellPresentationTest.kt, debug/openai/DebugScenarios.kt,
debug/openai/DebugScenarioActivity.kt, testDebug/openai/DebugScenariosTest.kt,
tools/wear_catalog.py, tools/test_wear_scenarios.py, tools/wear_phone_absence.py.

Docs: DAY5_STATUS.md, ROADMAP.md status only, WEAR_SCENARIOS.md, DECISIONS.md D28.
Kotlin paths are under wear/src/{main,test,debug,testDebug}/kotlin/
io/github/grassified/wristprompt/.

Pre-existing uncommitted D5-D work remains, including ModelItems.kt,
ModelItemsTest.kt and DAY5_D_REPORT.md. This phase does not claim those files as
D5-E additions. Shared modified files retain the D5-D changes.

## T. Suggested commit message

`feat(wear): polish error loading and explicit recovery UX`

No commit or push performed.
