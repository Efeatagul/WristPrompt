# Day 5 status — 2026-10-30

Current phase: **D5-B — PASS WITH KNOWN DEBT**, with automated and emulator
evidence recorded below. D5-C/D/E/F/G and D6 remain unstarted; physical UX is
deferred to D5-G. The D5-A section is the historical phase-A handoff, including
its then-current scope statements and measurements.

# Day 5 Phase A — chat-first shell — 2026-10-30

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.**
D5-A only: shell, retained shallow navigation, primary actions and static Wear
visual foundation. Physical UX remains PENDING END-OF-DAY PHYSICAL TEST in D5-G.
D5-B/C/D/E/F/G and D6 have not started. No commit or push.

D5-0 was delivered in chat; no separate repository audit report existed. Read
AGENTS, SPEC, ROADMAP, relevant DECISIONS, Day 3/4 reports/status, scenario guide,
production shell/theme/input/state wiring and relevant regression tests before
editing. Day 4 remains PASS WITH KNOWN DEBT. Existing uncommitted D4-H changes
were preserved byte-for-byte, including the untracked sign-out integration test.

## B. Before / after hierarchy

Before: fixed billing banner + Compose/Send/Stop above the list; generic chat
contained New Chat, model, connection and Details buttons. Proof/output/details
occupied multiple prominent routes. Navigation Back always jumped to Chat, and
re-entering a keyed list could lose its reading position.

After: scrolling title/model/compact billing/Menu header; measured Ask or Send
below the viewport, replaced by Stop whenever existing work is busy. Menu owns
administrative destinations. Diagnostics retain all existing proof/details
capability, with proof output inline. Message text alignment, 80-character chunks,
spacing, terminal/context labels and error guidance are intentionally unchanged.

## C. Final navigation map

```
Chat
├── Models
├── Composer / explicit draft review
└── Menu
    ├── New Chat
    ├── Account & Connection
    └── Settings & About
        └── Diagnostics
            └── Inference proof + inline proof output
```

Models entered from Chat returns to Chat. Other Back actions return along the
visited path; direct recovery routes return to their caller. Revisiting an
ancestor truncates the path rather than accumulating duplicates. OUTPUT is an
internal compatibility alias to proof, with no separate menu entry.

The same ShellNavigationViewModel retains destination, bounded return path,
input marker and per-surface reading anchors through retained Activity recreation.
Fresh ViewModelStore/process starts Chat with fresh reading state. No navigation
library, SavedStateHandle, database, file or conversation restoration was added.
Welcome notice has separate scroll state so it cannot displace Chat's anchor.

## D–E. Chat shell and action policy

- Empty: title, authoritative model, route disclosure, Menu and restrained Ask
  invitation. Setup/model problems retain actionable secondary navigation.
- Idle conversation: existing transcript; header scrolls away. Ask stays reachable.
- Draft review: existing draft and edit/discard controls; fixed route disclosure
  immediately above explicit Send, including when a long draft is scrolled.
- Streaming/catalog/proof busy: fixed Stop on Chat and every secondary surface.
  No forced autoscroll or new animation. Existing generation cancellation owns it.
- Secondary navigation: no generation, catalog reload or reset. New Chat is the
  explicit exception, calling the existing reset/cancellation behavior.

Ask launches the existing RemoteInput flow when draft is empty. A retained draft
returns to review without being replaced. Results still update draft only;
revision, single-use, recreation disposal and separate Send remain unchanged.
The actual platform chooser offers keyboard/voice without a custom voice stack.

## F. EdgeButton decision

**REJECTED for D5-A.** The pinned API supports the scaffold slot, but its morphing,
scroll-dependent visibility is unnecessary for the chosen consistently reachable
Ask control. A conventional content-sized Material Button keeps a minimum 48 dp
control target, measured outside scrolling content. Stop uses the same reliable
placement. No custom animated substitute or dependency was added.

## G–H. Theme and billing

WristPromptTheme centralizes OLED black, neutral dark surfaces, white foreground,
restrained teal and native Material 3 typography/shapes. Production MainActivity
and synthetic debug host share it. No fonts/assets, gradients, dynamic effects,
theme setting or continuous animation was added.

Chat shows Using ChatGPT plan or OpenAI API · API billing adjacent to its model.
It scrolls with the header. Composer repeats the current explicit disclosure in
the measured Send area. Unknown/loading/storage-error modes never infer a route.
First-use plan confirmation and existing explicit mode controls remain.

At fontScale > 1.2, the header stacks title/Menu/model/billing in lazy rows. The
initial 2x test caught clipping in a compact TextButton; the final model control
uses a content-sized native Button and the large-font layout stacks instead of
shrinking type. Primary controls remain minimum 48 dp. Answer rendering is not
changed by the responsive shell.

## I–J. Account/settings/diagnostics and production changes

| File | D5-A change |
| --- | --- |
| wear/MainActivity.kt | Use centralized Wear theme; same ViewModels/owners. |
| ui/WearShell.kt | Full viewport routing; narrow busy/Send projections; retained reading anchors; one primary action; inline proof output. |
| ui/ChatScreen.kt (new) | Scrolling adaptive header and composer shell; current transcript collection/cache moved into the chat subtree. |
| ui/ShellNavigationViewModel.kt | Bounded Back path and primitive reading anchors; fresh-owner reset. |
| ui/ShellInteraction.kt (new) | Testable shell event wiring to existing input/reset/send/stop owners; pure primary-action/billing presentation. |
| ui/WristPromptTheme.kt (new) | Static Wear Material 3 palette. |
| ui/ConnectionItems.kt | Heading renamed Account & Connection only. |

Paths above are under wear/src/main/kotlin/io/github/grassified/wristprompt.
No OpenAI/provider/session/conversation/credential/bridge implementation changed.
Connection retains sign-in, account selection/addition/sign-out, API setup/change/
removal, explicit route and Manage usage. Diagnostic fields stay allowlisted.

Chat's complete state is read in ChatScreen, draft in ComposerScreen. Only a
distinct busy projection reaches the shell; Send uses a distinct eligibility
projection. Security gating remains current. Existing account/setup flows remain
low-frequency shell inputs. No sampling, token coalescing, parser or network
observer was added. Proof-only streaming remains on the existing proof path.

## K. Lightweight performance baseline / comparison

Named WristPrompt_Watch7 only, debug APK, synthetic data, same five workloads.
Before layout editing, installed the existing D4 APK because the snapshot's
installed package lacked the canonical scenario Activity. Data/signing preserved.
No Android Studio or benchmark dependency. Each workload launches a fresh fixture,
resets gfxinfo, waits for readiness, samples memory/frames, and uses six identical
touch swipes for long output/streaming/models. Wake emulator before each launch.

The first baseline attempt was interrupted during input injection; a complete
five-case rerun is the baseline. Timings are retained-process Activity/fixture
readiness, NOT cold app startup, first usable frame, release or battery evidence.

| Synthetic workload | Ready ms before → after | Janky frames before → after | PSS MiB before → after |
| --- | --- | --- | --- |
| EMPTY_CHAT | 243 → 228 | 1 (4.55%) → 2 (9.09%) | 78.0 → 87.0 |
| ONE_TURN_COMPLETED | 189 → 244 | 1 (4.35%) → 2 (6.67%) | 78.0 → 85.8 |
| LONG_ASSISTANT_OUTPUT | 252 → 251 | 3 (1.60%) → 3 (1.56%) | 78.0 → 86.2 |
| STREAMING_LONG | 179 → 231 | 7 (3.98%) → 6 (2.86%) | 78.0 → 85.5 |
| MANY_MODELS | 248 → 230 | 9 (4.31%) → 6 (3.23%) | 78.0 → 88.1 |

Final anchor-only implementation pass: /tmp/d5a-after-final/summary.json. One-turn
and streaming readiness rose by 55/52 ms in this pass while empty/models fell;
there are too few trials and differing frame counts to establish causality or
meet a statistical regression budget. PSS is consistently higher (about 85–88
MiB versus 78 MiB); this remains an explicit D5-F memory/performance debt. The
anchor-only navigation change avoids retaining disposed layout objects but is
not claimed to explain or eliminate the PSS difference.

Initial D5 candidate measurements showed PSS approximately 83–104 MiB versus about 78 MiB in
the before pass, with falling values across later workloads. Code/native/Java and
GC/warmup state are confounders; no causal explanation or memory-regression pass
is claimed. The bounded comparison cannot establish battery cost or absence of a
leak. D5-F must investigate repeatable memory growth and use matched profiles.
No hidden idle timer/background architecture exists in the source changes.

Debug host writes ACKs/has extra collectors; it is not release-performance truth.
Raw temporary evidence: /tmp/d5a-before, /tmp/d5a-after and final-pass paths below.
Temporary artifacts are not durable evidence; this report preserves summary data.

## L. Automated verification — VERIFIED AUTOMATICALLY

| Check | Result |
| --- | --- |
| Full Wear JVM suite | 358 tests / 46 suites; 0 failures/errors/skips (343 baseline + 15 new). |
| Focused shell/lifecycle/reconstruction/composer/sign-out/HTTP cancellation | 64 tests / 8 suites; 0 failures/errors/skips. |
| ShellInteractionTest (new) | 9 tests. |
| ShellNavigationViewModelTest | 8 tests (2 existing + 6 new). |
| Wear assembleDebug / assembleRelease | PASS / PASS; release unsigned, no publication. |
| Wear lintDebug | 0 errors / 6 pre-existing warnings. |
| Source/manifest/DEX debug isolation | PASS. |
| Python tools tests | 40 passed; existing tools unchanged. |
| Python AST | 13 files passed. |
| git diff --check / changed-new text | PASS; 18 text files checked, including four preserved pre-existing files. |
| Debug APK signing fingerprint | Matches the existing DEVELOPMENT.md SHA-256 ending 79d37061. |
| Existing D4-H user work | Exact starting diff and untracked sign-out test preserved. |

Full mobile suite not rerun: no mobile/shared source/dependency changes, as allowed
by this phase's instruction. Day 4's 70-test mobile result remains historical.

Commands actually run with DEVELOPMENT.md's existing JDK/SDK/cache/signing env:

```sh
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
./gradlew :wear:testDebugUnitTest --rerun \
  --tests '*ShellInteractionTest' --tests '*ShellNavigationViewModelTest' \
  --tests '*ShellPresentationTest' --tests '*ComposerInteractionTest' \
  --tests '*LifecycleOwnershipTest' --tests '*ProcessReconstructionTest' \
  --tests '*SignOutPresentationTest' --tests '*StreamingHttpCancellationTest' --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_scenario_isolation.py
git diff --check
```

Full counts were captured before focused filtering replaced test XML. Focused
suites: interaction 9, navigation 8, presentation 12, composer 6, lifecycle 8,
process reconstruction 11, sign-out presentation 2, HTTP cancellation 8.
Initial lint found two unobserved composition StateFlow.value reads; fixed via
remembered source/initial snapshots, not suppression. Full final validation passed.

## M. Emulator acceptance — named WristPrompt_Watch7 only

| Check | Disposition / evidence |
| --- | --- |
| All 43 canonical fixtures | PASS — VERIFIED ON EMULATOR: fresh ACK/readiness, matching PID, resumed host; not 43 visual acceptance claims. |
| EMPTY_CHAT, CHATGPT_AUTHENTICATED, API_KEY_CONFIGURED | PASS — VERIFIED ON EMULATOR: scrolling header, route disclosure, Ask. |
| ONE_TURN_COMPLETED, MULTI_TURN_CHAT | PASS — VERIFIED ON EMULATOR: existing transcript under shell. |
| STREAMING_LONG, STOPPED_PARTIAL | PASS — VERIFIED ON EMULATOR: reading viewport, fixed Stop/Ask and partial exclusion. |
| MANY_MODELS, MODEL_UNAVAILABLE | PASS — VERIFIED ON EMULATOR: picker/reload reachable; rows unchanged. |
| SIGNED_OUT, REAUTH_REQUIRED_PHONE_ABSENT | PASS — VERIFIED ON EMULATOR: account/recovery screen; synthetic fixture only. |
| ERROR_429 | PASS — VERIFIED ON EMULATOR: existing error/context guidance retained. |
| RECREATED_COMPLETED_STATE | PASS — VERIFIED ON EMULATOR: retained completed fixture under shared theme. |
| Chat → Models → Back | PASS — VERIFIED ON EMULATOR: viewport/conversation retained, no reload/send. |
| Chat → Menu → Account → Back → Chat | PASS — VERIFIED ON EMULATOR. |
| Menu → Settings → Diagnostics → Back chain | PASS — VERIFIED ON EMULATOR. |
| Ask → platform chooser/keyboard | PASS — VERIFIED ON EMULATOR. |
| Native keyboard confirmation → returned draft | BLOCKED — EMULATOR LIMITATION / CAUSE UNRESOLVED: system confirmation retained text in a collapsed 2-pixel panel; cause unresolved. Not counted as app draft/Send acceptance. |
| Explicit Send / stale-result rejection | VERIFIED AUTOMATICALLY via production event adapter/composer/session tests; no rendered native-result PASS inferred. |

Final targeted checks on the final build passed: Models return preserves visible
viewport and completed conversation with discoveries=1/generations=1; Menu New
Chat clears messages without replay; Stop from Settings cancels/excludes the
active synthetic attempt; actual pending-composer Activity recreation increments
creations to 2 while draft/generations remain zero; at 2x font the model affordance
remains reachable by scrolling and Stop yields CANCELLED/excluded/busy=false.
Deep reading was separately exercised on the final build: 20 gated synthetic
chunks, actual rotary scroll, then real Activity recreation. The visible viewport
was identical afterward, creations >= 3, generations=1, and the same request was
still busy; explicit Stop ended it. Font scale checked at 1.0. Log:
/tmp/d5a-reading-recreation.log.

Logs: /tmp/d5a-targeted-final.log, /tmp/d5a-final-font-rotary.log and
/tmp/d5a-final-catalog.log. All final catalog launches used the same installed APK.
 Generic catalog launch
is not a substitute for interaction testing. No physical auth or CapabilityClient
investigation was performed; the earlier emulator/GMS limitation remains separate.

## N. Accessibility / rotary / large font

PASS — VERIFIED ON EMULATOR: actual rotaryencoder SCROLL events move Chat, Models,
Account & Connection, Settings and Diagnostics. Existing shared scaffold/list
state supports it; no nested vertical list was introduced.

2.0 font-scale stress exercised empty/completed Chat, Ask, Models, Account,
Composer, Menu, Settings, Diagnostics and streaming Stop. Content scrolls; controls
retain native scaling and non-color-only text. Font setting restored to 1.0.
This is an emulator stress setting, not proof of Galaxy Watch maximum-font UX.
TalkBack action labels/model destination semantics reviewed; no actual TalkBack
session or physical accessibility pass is claimed.

## O. Release / security invariants

No credential routing/fallback, OAuth, credential format/storage, Data Layer,
inference/session ownership, protocol/replay, refresh/sign-out, dependency or
signing changes. store=false, explicit input history, no previous_response_id,
completed-only replay, partial exclusion and actual transport cancellation remain.
No conversation/draft persistence, Room, Markdown, new provider, web/tools/MCP,
images, custom speech/TTS, polling, retry loop, service, WorkManager or wake lock.
D4-H signed-out identity redaction unchanged. Debug theme/host remain debug-only;
source/manifest/DEX checks pass. No physical action or real inference request.

## P. Known debt / phase boundaries

- D5-B: dense/left-aligned answer rendering, 80-character chunk removal, streaming
  coalescing and Latest. None implemented here.
- D5-C: Markdown/code/tables/links. Not implemented.
- D5-D: model/account/settings row redesign; ONLY authoritative name + selected
  state in model rows, no speculative metadata. Existing rows preserved.
- D5-E: error/recovery copy. Existing guidance still says Connection / settings;
  the actual destination is now under Menu → Account & Connection.
- D6: persistent transcript/replay architecture, storage selection/implementation.
  No storage abstraction/schema chosen here.
- D5-F: release-comparable performance and memory characterization; no battery-life
  improvement or absence of regression inferred from this debug smoke.
- Native keyboard confirmation roundtrip remains unverified on this emulator;
  input launch and deterministic result/revision/Send tests pass. No speculative
  platform input change made.
- Day 4 debt unchanged: Terra-specific restoration and historical reauthorization,
  natural phone-absent token refresh, funded BYOK evidence, destructive storage
  and full release/hardware gates. Nothing here claims those resolved.

## D5-G concise deferred physical checklist

**PENDING END-OF-DAY PHYSICAL TEST — no hardware action in D5-A.**

- Default/max-font Chat header/Ask, long reading and model affordance.
- Touch/rotary and Back through Models, Account, Settings and Diagnostics.
- Keyboard return → draft review → explicit Send; recreation discards stale input.
- Fixed Stop while reading and on secondary screens; honest partial state.
- Plan/API disclosure before Send and useful setup/recovery routes.
- Ordinary memory/latency sanity on Galaxy Watch 7; later release comparison.

No sign-out, key removal, revocation, forced expiry, data clearing, uninstall or
usage exhaustion is part of this checklist without exact separate permission.

## Q. Files changed in D5-A

Production: MainActivity.kt; ui/ChatScreen.kt (new), ConnectionItems.kt,
ShellNavigationViewModel.kt, ShellInteraction.kt (new), WearShell.kt,
WristPromptTheme.kt (new). Debug: DebugScenarioActivity.kt (theme only).
Tests: ShellNavigationViewModelTest.kt; ShellInteractionTest.kt (new).
Docs: DAY5_STATUS.md (new), ROADMAP.md, DECISIONS.md, WEAR_SCENARIOS.md.

Pre-existing, not changed by this phase: WatchProfiles.kt, ShellPresentation.kt,
ShellPresentationTest.kt and untracked SignOutPresentationTest.kt.

## R. Suggested commit

`feat(wear): introduce chat-first shell and shallow navigation`

No commit/push. Only D5-A is implemented. Do not start D5-B automatically.


# Day 5 Phase B — dense plain text and streaming presentation — 2026-10-30

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.**
Only D5-B implemented. No physical testing, commit or push. No Markdown,
persistence, authentication, inference protocol or billing changes. Existing
D5-A and Day 4 evidence remains historical; this phase does not close their
physical/release gates.

## B. Before / after message presentation

Before: centered 80-character chunks, 10 dp between every chunk, separate role
rows, partial/context labels and a permanent completion row. Streaming updated
that renderer from every collected canonical delta.

After: compact dark user surfaces (15 sp, 8 dp inset), flat start-aligned assistant
text (16 sp / 20 sp), near-full 24 dp safe side insets, no assistant bubble or
permanent Completed row. Exchanges start with 10 dp separation; internal reading
segments add no gap. Paragraph/newline whitespace belongs to the original text.
No system font shrinking, Markdown interpretation or link activation.

Stopped, interrupted, incomplete and failed remain distinct, retaining partial
text and the explicit exclusion notice. Older excluded user messages keep a
compact exclusion label after another attempt starts. Existing recovery routes
and specific problem categories remain; broader copy redesign is D5-E.

## C. Final plain-text architecture

`canonical ChatState → retained ChatPresentationOwner / PresentationCoalescer →
PlainTextProjection → stable bounded lazy rows → Compose`.

Canonical conversation/session/ViewModel source is unchanged. The new owner never
calls Send, Stop, discovery or any network/storage API. It is Activity-owned,
retained across navigation/recreation, and starts empty in a new process. Chat
visibility and STARTED lifecycle enable projection; inactive UI cancels its pending
flush. Foreground return projects the newest snapshot once, not missed frames.
A hidden New Chat/account/model reset also clears cached rows promptly.

Completed rows reference ranges in the authoritative canonical string, avoiding
another whole-history text copy. Settled streaming rows retain only copied slices,
plus one unfinished tail; old full partial strings are not retained. Completed
projection cache is capped at **256 KiB estimated metadata** (128 bytes per row/key,
64 per message). It stores no layout result, styled full answer, credential,
network state or replay copy. Current streaming slices are separately bounded by
the existing 65,536 UTF-16-unit output limit. Reset/owner clear releases them.

Normal row budget: 512 UTF-16 units, choosing the last newline within the bounded
window; multiple short paragraphs may share a row. Long paragraphs use safe
character boundaries. Surrogates, combining marks, ZWJ/modifier/tag emoji and flags
are protected. One unusually long grapheme may exceed the normal budget rather
than be corrupted. No artificial paragraph gap is added at an internal cut.
A matching row-boundary newline is represented by layout; accessibility retains
the original text, and final trailing newlines/carriage returns remain intact.

The row projection is the D5-C seam. No Markdown AST, parser, syntax class,
dependency, HTML or WebView was added.

## D. Streaming coalescing design

- First visible text: immediate publication, no initial 100 ms wait.
- Subsequent text-only changes: one event-triggered 100 ms delayed job. The latest
  canonical snapshot wins; new deltas do not postpone an existing deadline.
- Pending work: maximum one Job; no queue or continuously running ticker.
- Terminal/Stop/failure/reset/attempt or metadata changes: cancel pending work and
  publish synchronously when observed. Identical canonical snapshot publication
  is deduplicated, including visibility resume.
- Reconciliation: final canonical text is authoritative. Matching settled
  boundaries/keys survive; copied settled slices become ranges of that final
  string. Corrected text replaces incompatible projections.
- Stale rejection: scheduling generation and existing monotonic local user/attempt
  identity are checked against newest canonical state, including its busy flag.
  Main-thread synchronous projection cannot finish later and overwrite a newer
  terminal. Existing inference epoch/lease guards remain authoritative.

## E. Density result — actual emulator observation

PASS — VERIFIED ON EMULATOR on WristPrompt_Watch7. Long answer text is visibly
wider, flat and left-aligned in English, with fewer empty gaps and no repeated
role/completion rows. The compact user surface is visually distinct. Paragraphs,
long tokens and mixed Turkish/Arabic/Chinese/emoji render and scroll. RTL remains
content-direction aware through start alignment.

This is qualitative emulator acceptance, not a measured physical reading-speed or
battery improvement. Normal scroll clipping at the circular viewport boundary is
expected; no central safe-width overflow or control overlap was observed.

## F. Auto-scroll / Latest policy

Incoming text never calls a scroll action. Upward reading remains where the user
left it. Latest appears only when transcript content exists below the viewport,
and hides at the current end. It is user-triggered and shares the existing fixed
primary-action band, preserving a separate minimum 48 dp Stop/Ask target.
At fontScale > 1.2 it uses a downward arrow with accessible label Latest, avoiding
clipped large text beside Stop. No decorative animation.

Wear scrollToItem centers a row; the explicit Latest action therefore finishes
with one bounded scroll to the actual bottom of a tall last row. No follow loop.
Reading restoration uses logical row key plus offset, with index fallback only if
that key disappeared. Native lazy keys preserve position while rows are added.

## G. Stable keys / content types / recomposition

User keys use local message ID; answer keys use monotonic user/attempt ID plus
segment index. Tail and settled versions share a logical key. Status, excluded
message and recovery keys are stable. Shell header/setup/guidance keys are fixed.
No current-list-index-only, text-hash or composition-random key.

Content types: USER, ASSISTANT, TAIL, STATUS, RECOVERY, plus header/action shell
rows. Completed cache rows and streaming settled rows retain reference identity
during text deltas. ChatHeader is a separate composable with unchanged inputs.
The shell observes only distinct busy/Send eligibility, and does not collect
canonical transcript text. No blanket @Immutable annotations were introduced.
Recomposition counts were not instrumented; these are structural boundaries and
unit identity checks, not a claim that every Compose internals call was measured.

## H. Performance results

Deterministic virtual-time burst: **65,000 text deltas over 6,500 virtual ms →
66 publications, 67 segmentation calls, 81,195 UTF-16 units submitted to segment
projection** (including the tiny user message). Maximum pending flush = 1.
The final emulator fine-grained run reported 2,211 received characters with 38
publications, 39 segmentation calls and 10,536 projected units at its mid-stream
sample. While Home/backgrounded, canonical characters advanced 2,550 → 3,449
while publications stayed **43 → 43**, segmentations **44 → 44**, projected units
**12,101 → 12,101**. Resume/recreation retained one generation; final synthetic
Stop retained 5,725 partial characters with 82 publications / 83 segmentation
calls / 25,982 projected units. These are event/IO/scheduler-dependent debug
samples, not fixed throughput or real network latency guarantees.

The previous plain UI had no cadence limiter; a collector able to observe all
those deltas could request 65,000 transcript updates. This is a structural
comparison, not a measured 65,000-frame emulator baseline.

A separate 1,000-delta / 1,000-virtual-ms continuous-stream test publishes 11 times,
including the immediate first text. Advancing another 10 seconds while idle adds
zero publications. First text and terminal/Stop publication delay is **0 virtual
ms** in deterministic tests; real display latency was not instrumented.
Completed large history is not resegmented per delta; terminal reconciliation
checks the settled prefix once and segments only its remaining tail.

Matched debug emulator comparison: committed D5-A `f5400a9` was built in an
isolated temporary source copy, then D5-B restored. Same named AVD, signing key,
five fixtures, one warm-up pass and three recorded passes per build. Each uses
six actual rotary events, resetting gfxinfo after fixture readiness. Medians:

| Workload | P90 frame ms, A → B | Janky frames %, A → B | PSS MiB, A → B |
| --- | ---: | ---: | ---: |
| ONE_TURN_COMPLETED | 25 → 22 | 2.25 → 1.10 | 78.81 → 79.41 |
| MULTI_TURN_CHAT | 32 → 21 | 2.27 → 1.12 | 79.89 → 80.13 |
| LONG_ASSISTANT_OUTPUT | 27 → 25 | 2.15 → 1.12 | 81.63 → 81.56 |
| STREAMING_LONG | 32 → 32 | 2.06 → 3.30 | 79.76 → 80.27 |
| STOPPED_PARTIAL | 23 → 25 | 2.17 → 2.15 | 79.42 → 80.07 |

The streaming jank increase is unresolved noisy debug evidence: 1–4 janky frames
before versus 2–6 after, approximately 90 frames/sample, overlapping ranges,
unchanged median P90. It does not establish a causal regression or a release pass.
No median P90/PSS increase above 10% was observed; STOPPED P90 rises about 8.7%.
No obvious freeze/rotary regression was observed in targeted interactions.
Release-comparable frame/GC/allocation characterization remains D5-F; do not infer
battery life or physical performance from these samples.

Sixteen dense-answer → actual New Chat cycles: sampled reset PSS **79.55–81.02
MiB**, first 81.02 and last 80.06 MiB. Sampled Java Heap PSS **12,924–14,032 KiB**,
first 14,032 and last 12,924. Completed generations and empty resets were asserted
each cycle. No sustained growth in this bounded run; not a proof against all
leaks. No heap dump/forced collection or allocation attribution was performed.

Safe metrics are opt-in only for debug/tests. The debug acknowledgement contains
counts, never content, and fine-delta disk writes are event-throttled. No heavy
benchmark dependency or release instrumentation was added.

Evidence retained locally under /tmp/d5b-matched-before, /tmp/d5b-matched-after,
/tmp/d5b-cycles and /tmp/d5b-acceptance; these temporary files are not committed
artifacts and may be removed by the host.

## I. String accumulation decision

**Retained `output += event.text` in StreamResult unchanged.** Existing output
limits/ownership/terminal behavior remain authoritative. Fine-delta integration,
near-limit virtual streaming and memory/frame samples did not attribute a material
runtime bottleneck specifically to that operation. Cumulative string copying
remains a plausible risk; no claim of zero allocation cost or asymptotic efficiency.
An accumulator change needs actual attribution and equivalent protocol tests;
that change was not justified by current presentation evidence.

## J / R. Production changes and files changed

All Kotlin paths below are under
`wear/src/main/kotlin/io/github/grassified/wristprompt/` unless noted.

| File | Final D5-B change |
| --- | --- |
| ui/ChatPresentation.kt (new) | Plain-text range/slice rows, bounded cache, safe segmentation, terminal metadata, Latest predicates and opt-in metric sink. |
| ui/ChatPresentationOwner.kt (new) | Retained lifecycle-visible adapter, event-triggered coalescer and stale/terminal reconciliation. |
| ui/ChatReadingPosition.kt (new) | Logical key/offset restoration and explicit end-of-last-row jump. |
| ui/ChatScreen.kt | Observe presentation subtree; stable typed shell rows; isolated header; Latest event binding. Composer behavior unchanged. |
| ui/ChatItems.kt | Flat assistant / compact user renderer; exact accessible text; transcript-local metadata. Recovery routing unchanged. |
| ui/ShellNavigationViewModel.kt | Primitive logical chat anchor and Latest visibility/event; no layout or canonical request owner. |
| ui/WearShell.kt | Optional metric injection; zero transcript spacing option; Latest shares measured primary band with Stop priority. |
| debug/.../DebugScenarios.kt | Four additive credential/network-free fixtures; explicit repeat/reset acceptance commands. |
| debug/.../DebugScenarioActivity.kt | Retained aggregate metrics, forced safe REPORT and bounded event-triggered debug acknowledgement IO. |
| test/.../ui/ChatPresentationTest.kt (new) | 22 projection/Unicode/cache/terminal/Latest/anchor tests. |
| test/.../ui/PresentationCoalescerTest.kt (new) | 14 scheduler/ownership/visibility/recreation/reset/burst tests. |
| testDebug/.../DebugScenariosTest.kt | Catalog count 47; existing every-fixture preparation test covers additions. |
| tools/wear_catalog.py | Classify four added names; original smoke set retained. |
| tools/wear_adb.py | Allowlist three numeric presentation fields. |
| tools/test_wear_scenarios.py | Expected catalog count 47. |
| tools/test_wear_harness.py | Recognize the report method's explicit force parameter in the existing field-allowlist check. |
| DAY5_STATUS.md | Current D5-B evidence and historical D5-A handoff. |
| WEAR_SCENARIOS.md | 47-name catalog, new fixtures, metrics/commands and acceptance limits. |
| ROADMAP.md | D5-B status/evidence only; later phases remain unstarted. |
| DECISIONS.md | D25 plain-text projection/cadence/anchor/cache architecture and D5-C seam. |

No MainActivity, theme, ShellInteraction, InferenceViewModel, InferenceSession,
ChatConversation, provider, SSE, shared/mobile, dependency or credential source
was changed. Working tree was clean before D5-B. No unrelated user changes.

## K. Automated test results

**VERIFIED AUTOMATICALLY:**

- Focused D5-B tests: **36 / 2 suites**, included in the focused selection below.
- Explicit focused shell/lifecycle/reconstruction/streaming/cancellation/chat/model
  selection: **162 / 16 suites**, zero failures.
- Full Wear suite: **394 / 48 suites**, zero failures/errors/skips; D5-A baseline
  was 358 / 46, so 36 tests and 2 suites are added.
- Existing relevant coverage includes shell navigation 8, shell interactions 9,
  shell presentation 12, composer 6, lifecycle 8, reconstruction 11, streaming
  races 13, real OkHttp cancellation adapter 8, API-key cancellation 3, session 5,
  chat ViewModel 12, conversation/context 13, chat transport 3 and model
  restoration 15. These are synthetic/JVM tests, not physical/network proof.
- `:wear:assembleDebug`, `:wear:assembleRelease`, `:wear:lintDebug`: PASS.
  Lint: **0 errors, 6 existing warnings** (two newer-version notices, exported
  service, long vector path, two debug-activity recents warnings).
- `python tools/check_scenario_isolation.py`: PASS, including debug/release
  manifest/DEX and all 47 names absent from release.
- `python -m unittest discover -s tools -p 'test_*.py'`: **40 tests**, PASS.
- `python -m py_compile tools/*.py`: PASS.
- `git diff --check` and changed/new-file whitespace checks: PASS.
- Mobile suite/build not rerun: no shared/mobile/dependency change.

Build/test environment: existing JDK 17.0.21+9, existing SDK, existing
ANDROID_USER_HOME debug signing identity. No dependency/environment update.
Full and focused logs: /tmp/d5b-final-validation.log and
/tmp/d5b-focused-final.log. Test metrics are in the JUnit system-out record.

## L. Emulator acceptance matrix

All results below are **PASS — VERIFIED ON EMULATOR**, limited to synthetic
production-renderer fixtures on WristPrompt_Watch7:

| Fixture/check | Actually exercised |
| --- | --- |
| ONE_TURN_COMPLETED | Compact user, flat answer, Latest reaches end/hides, Ask reachable. |
| MULTI_TURN_CHAT | Stable distinct exchange rows, Latest and rotary. |
| LONG_ASSISTANT_OUTPUT | Dense long reading, native rotary, Latest. |
| STREAMING_LONG | Scroll upward while text arrives; no forced return; Latest and Stop coexist; Stop retains excluded partial. |
| STOPPED_PARTIAL | Compact stopped/exclusion metadata and recovery; Latest/Ask. |
| DENSE_LONG_ANSWER | Near-limit 200-paragraph lazy reading and repeated New Chat cycles. |
| STREAMING_FINE_GRAINED | Coalesced counter updates, Stop, Home/resume, retained Activity recreation; one generation throughout. |
| LONG_UNBROKEN_TEXT | Long URL/token fallback scrolls without crash or network action. |
| UNICODE_LONG_RESPONSE | Mixed language/RTL, combining marks and emoji; scroll and Latest. |
| 2× font stress | Dense, unbroken, Unicode and streaming fixtures; Latest arrow and separate Stop/Ask target. |
| Retained long-reading recreation | Same visible long-answer text before/after actual Activity recreation; Models/Back retains one generation/completion/discovery. |

All **47/47** catalog entries also launched successfully on the final installed
APK (matching readiness/PID/RESUMED). This is separate from the interaction matrix
and does not establish visual correctness for every row. No physical PASS claim.
Final log: /tmp/d5b-final-catalog.log.

## M. Accessibility / rotary / max-font result

Native rotary input exercised actual transcript movement, alongside touch taps.
Normal and 2.0 font scales are exercised without shrinking type. Large-font
Latest is an arrow with the accessible label Latest; Stop keeps its own target.
User messages expose Your message plus original accessible text. Content remains
scrollable; no nested vertical list. Emulator font restored to 1.0 afterward.
Actual long-reading recreation and Models/Back evidence: /tmp/d5b-reading-final.json.
No actual TalkBack session, OEM maximum-font or physical accessibility pass is
claimed. Existing native keyboard confirmation debt remains outside this phase.

## N. Battery / memory review

No idle timer, polling, service, foreground service, WorkManager, wake lock,
per-frame animation, connectivity observer, automatic refresh or forced scrolling
was added. Scheduling happens only after arriving stream data, stops when inactive
or terminal, and holds one pending Job. Closed range projection cache and current
stream slices are bounded. Reading positions retain primitives only. Unit reset
and hidden reset checks release old cache; emulator reset sampling is bounded
supporting evidence, not a universal leak/battery guarantee.

## O. Security / architecture invariants

OAuth, credential format/storage, signing, Data Layer routing, account/model
validation, explicit billing mode and replay semantics are unchanged. No credential
fallback. Existing SSE/session ownership, serialization, stale epoch/lease guards,
actual OkHttp Call cancellation and terminal completion rules remain authoritative.
Only completed exchanges are replayed. No persistence, Room/database, TTS, speech
stack, other provider, web/tools/MCP, images, HTML or network feature. Debug fixture
and metric reporting remain release-isolated; no real request or physical action.

## P. Known debt

- D5-A memory characterization remains provisional; this separate matched debug
  run does not retroactively establish its earlier PSS change as causal.
- Streaming jank is noisy and slightly higher in the matched debug samples;
  release-mode frame/GC/allocation characterization remains deferred to D5-F.
- No direct emulator first-photon/terminal-to-frame latency or allocation
  attribution was collected. Scheduler immediacy is virtual-test evidence.
- A pathological single huge grapheme exceeds the normal segment budget to
  preserve text. Extremely long paragraphs may wrap at internal segment cuts;
  no extra gap/text is inserted.
- Physical UX acceptance remains **PENDING END-OF-DAY PHYSICAL TEST — D5-G**.
- Actual TalkBack, physical max-font, native keyboard confirmation and existing
  Day 4 auth/refresh/BYOK/Keystore/release debt are not closed by D5-B.

## Q. Deferred to D5-C

Native Markdown; headings; emphasis; lists; code blocks; tables; links; malformed
Markdown handling. Also no D5-D model/account/settings redesign, D5-E broad error
copy redesign, D5-F consolidated characterization, D5-G physical tests or D6
persistent conversations was begun.

## S. Suggested commit message

`feat(wear): add dense plain-text chat and coalesced streaming presentation`

No commit or push performed.
