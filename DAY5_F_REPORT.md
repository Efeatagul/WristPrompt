# Day 5 Phase F — consolidated evidence — 2026-10-30

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.**
D5-F only. Physical UX remains PENDING END-OF-DAY PHYSICAL TEST in D5-G.
No physical-device testing, commit or push. Starting tree
was clean at `5add22d`; D5-A–E production architecture is the measurement baseline.
D5-G and D6 remain unstarted. Only synthetic fixtures on the dynamically verified
WristPrompt_Watch7 AVD, native 454 × 454 resolution, were used. No real inference,
account mutation, app-data clearing, uninstall, signing change or external action.

## B. Performance baseline

Built the unchanged committed debug tree before production editing. Baseline APK
SHA-256 `719781e23daa64b3ce647298711dc448c925d2ab8721e3960f9aa4c216718782`.
Thirteen canonical workloads cover the requested baseline; LATEST_AVAILABLE is an
interaction in long/streaming Chat, not a catalog identifier. An initial temporary
runner mistakenly tried that nonexistent name and stopped; its six completed
samples were retained, and only the remaining seven workloads were resumed.
Baseline collection renamed no scenario and introduced no alias. Two later
debug-only stress additions are documented below. New Chat/navigation stress is separate.

Method: fixture readiness with fresh token, matching PID and RESUMED Activity;
2 s settling, six 250 ms touch swipes, 1 s wait, three meminfo samples 0.5 s apart.
Fine-grained streaming additionally waited 14 s; its final ACK was COMPLETED.
STREAMING_LONG remained RECEIVING at its final ACK, so its three samples are an
active/growing workload window, not a fixed-text steady-state comparison. No
within-row average is used or active-stream value relabeled as completed output. Readiness
is not first-frame/startup timing. Two baseline groups have different PIDs because
the resumed temporary runner reinstalled the identical APK. Each three-sample row
uses the same fixture/process (with active streaming growing as noted); unrelated workloads/processes are not averaged together.

The initial swipe window started near the fixed control band on Chat and yielded
only 13–17 rendered frames for several answers. Those low-count frame results do
not establish transcript-scroll performance. Dedicated repeated rotary windows
below supply actual scrolling evidence. Debug ACK disk writes, extra collectors,
synthetic provider timing and keep-screen-on distort release/battery comparisons.

## C. Memory / PSS / heap

VERIFIED ON EMULATOR. PSS samples in MiB; all other columns in KiB. Java is the
meminfo App Summary resident Java bucket, not Java live-object allocation.
Native/code/stack/other/system columns show the third matched sample. Graphics
was **0 in every sample**; emulator attribution does not establish zero GPU cost.
For interpretation, Android distinguishes PSS residency from heap allocation; see
[Android memory tools](https://developer.android.com/topic/performance/memory/guide/tools-overview).
Full three-sample bucket/managed-allocation values are preserved in
[DAY5_F_METRICS.json](DAY5_F_METRICS.json).

| Workload | PSS samples MiB | Java samples KiB | Native | Code | Stack | Private other | System |
| --- | --- | --- | --- | --- | --- | --- | --- |
| EMPTY_CHAT | 78.56 / 75.43 / 75.12 | 16392 / 12664 / 12688 | 9344 | 35296 | 1172 | 5084 | 13336 |
| ONE_TURN_COMPLETED | 76.83 / 76.13 / 75.88 | 13840 / 13036 / 12796 | 9636 | 35284 | 1156 | 4996 | 13837 |
| MULTI_TURN_CHAT | 77.02 / 76.12 / 75.82 | 13968 / 13056 / 12804 | 9612 | 35284 | 1156 | 4920 | 13865 |
| DENSE_LONG_ANSWER | 77.32 / 76.46 / 76.09 | 14124 / 13288 / 13040 | 9584 | 35280 | 1156 | 4940 | 13917 |
| STREAMING_LONG | 78.66 / 77.19 / 76.71 | 14932 / 13432 / 12972 | 9740 | 35288 | 1156 | 5016 | 14379 |
| STREAMING_FINE_GRAINED | 77.79 / 77.32 / 77.32 | 13296 / 12864 / 12864 | 10088 | 35288 | 1156 | 4808 | 14967 |
| MARKDOWN_BASIC | 77.42 / 75.42 / 75.39 | 15180 / 12748 / 12724 | 9332 | 35288 | 1152 | 5556 | 13148 |
| MARKDOWN_LONG | 76.41 / 76.05 / 75.77 | 13704 / 13276 / 13032 | 9572 | 35296 | 1160 | 4952 | 13579 |
| MARKDOWN_CODE | 76.88 / 76.10 / 75.54 | 14076 / 13276 / 12776 | 9576 | 35296 | 1180 | 4900 | 13625 |
| MARKDOWN_TABLE | 76.75 / 75.98 / 75.70 | 13812 / 13032 / 12780 | 9664 | 35276 | 1180 | 4904 | 13712 |
| MANY_MODELS | 77.92 / 76.75 / 76.69 | 14112 / 12996 / 13008 | 9928 | 35344 | 1208 | 5016 | 14028 |
| THOUSAND_MODELS | 78.69 / 77.27 / 77.27 | 14460 / 13092 / 13096 | 10448 | 35356 | 1208 | 4780 | 14239 |
| ERROR_429 | 78.44 / 77.59 / 77.16 | 14144 / 13276 / 12884 | 10480 | 35340 | 1184 | 4804 | 14319 |

Within-state settling reduces the first sample in many rows. Code residency is
about 35,300 KiB, larger than either Java or native summary bucket. This is not a
leak classification. Managed heap allocation/size/free values are separate in the
metrics artifact. No HPROF, credential-bearing dump or forced-GC mechanism was used. Transient
Activity/ViewRoot counts of two settled back to one in both idle/return sample
series; no monotonic Activity retention was observed.
After all 12 cycles and the lifecycle checks, fresh EMPTY_CHAT in the same process
settled at **89,092 / 87,653 / 87,136 KiB PSS** and **14,712 / 13,404 / 13,136 KiB Java**.
After 1,000 Models → Back to Chat: **88,657 / 87,604 / 87,352 KiB PSS**, Java
**14,536 / 13,488 / 13,260 KiB**. These are warmer post-navigation states, not
averaged with the initial idle baseline. No forced GC was used. Final cold-process idle after a separate emulator-only force-stop/relaunch:
**76,744 / 74,903 / 75,163 KiB PSS** (74.95 / 73.15 / 73.40 MiB), Java
**14,592 / 12,564 / 12,572 KiB**, native **9,096 / 9,172 / 9,172 KiB**.
This is cold process, not cold installation or a release build. No credential
storage or app data was cleared.

## D. Historical D5-A PSS question

The old ~78 → ~85–88 MiB observation **does not reproduce as an elevated current
D5-A–E warm baseline**: current empty Chat samples are 78.56 / 75.43 / 75.12 MiB;
all initial baseline states span 75.12–78.69 MiB; final cold-process idle spans
73.15–74.95 MiB. This does **not** recreate the historical
before/after experiment and cannot attribute its difference to theme, navigation,
harness, later Markdown or GC. No old APK/toolchain/GC state was substituted into
a causal comparison. The old 78 MiB number is not an optimization target. Current
settling and repeated-cycle evidence are more useful than comparing isolated old
snapshots. A historical causal explanation remains unresolved, not a proven fix.

After broad navigation/reset/lifecycle warmup, current settled idle does reach
**85.09–87.00 MiB PSS**, similar numerically to the old post-D5-A values. Compared
with the initial third empty sample, its third sample has +448 KiB Java summary,
+4,564 KiB native and +4,676 KiB system (shared/runtime accounting), +620 KiB code.
That is a different exercised process state, not a matched pre/post architecture
regression. It specifically cautions against calling the initial lower samples a
historical regression “resolution” or treating all extra residency as chat objects.

## E. Streaming performance

VERIFIED AUTOMATICALLY. New consolidated coalescer + Markdown-worker stress:
65,000 deltas / 6,600 virtual ms → **66 streaming flushes**, **68 segmentations**
including setup/Stop, **68 plain projection calls** (Stop/reset included),
**81,684 projected source units**, zero growing-answer parses,
one terminal partial parse, maximum pending flush **1**, maximum active parse **1**.
Stop publishes synchronously at **0 virtual ms** before parser work. Twenty-four
rich/reset cycles produce 24 parses and zero plain/rich cache bytes after each
reset. Existing completed-terminal/replacement tests independently establish immediate
plain terminal publication and stale delayed/Markdown rejection. The worker has
one active conversion and one replaceable pending snapshot (conflated Channel),
not a per-delta queue. Tests advance 10 s idle with **zero publications**.

In the baseline emulator capture, STREAMING_LONG was still RECEIVING with
18 flushes / 19 segmentations; STREAMING_FINE_GRAINED had completed with
172 flushes / 173 segmentations. The fine fixture emits **11,200** one-character
deltas (56-unit phrase × 200), not the separate 65,000-delta JVM stress. These
wall-run counts include setup/terminal scheduling and are not a measured 172/second.

These are deterministic scheduling bounds, not hardware millisecond measurements.
Emulator touch Stop/Latest is tested separately; no end-to-end physical Stop latency
is inferred. Metrics contain counts only, no prompt/response text.

`StreamResult` still uses `output += event.text`. New exact-output diagnostic at
65,536 characters: one-character chunks **591 ms**, 64-character chunks **8 ms**,
512-character chunks **1 ms** on the host JVM in the complete run (focused earlier
run: 577 / 8 / 1 ms). Quadratic copying exists in the pathological single-character
case. No production accumulation change: these timings do not isolate a material
watch UI bottleneck; canonical immutable full output is still consumed per delta,
so merely adding StringBuilder would not eliminate snapshot copying. The output
limit, authoritative replacement, ownership and completed-only replay stay intact.

## F. Frame / jank findings

VERIFIED ON EMULATOR: 30 measured windows, ten workloads × three fresh fixture
launches, one-second settle, gfxinfo reset, twelve actual rotary SCROLL,-8 events
with 120 ms gaps, then 300 ms settle. Same final APK, native resolution/font 1.0;
TalkBack disabled and no build/test jobs running during this pass. No release or
physical frame claim. Actual rotary movement was also checked visually outside
timing windows; neither screenshot/XML capture nor heap sampling occurred inside
the frame window.

| Workload | Frames, trials 1 / 2 / 3 | Janky %, trials 1 / 2 / 3 | P90 ms, trials 1 / 2 / 3 |
| --- | --- | --- | --- |
| DENSE_LONG_ANSWER | 136 / 141 / 140 | 0.74% / 0.71% / 0.71% | 16 / 16 / 25 |
| MARKDOWN_LONG | 139 / 133 / 138 | 1.44% / 3.76% / 1.45% | 32 / 32 / 30 |
| MARKDOWN_LIST_LONG | 135 / 138 / 132 | 1.48% / 0.72% / 1.52% | 23 / 23 / 24 |
| MARKDOWN_TABLE | 137 / 138 / 142 | 1.46% / 1.45% / 0.70% | 21 / 21 / 16 |
| MARKDOWN_TABLE_OVER_LIMIT | 142 / 134 / 134 | 0.70% / 0.75% / 0.75% | 22 / 22 / 21 |
| MARKDOWN_CODE_LONG_LINE | 136 / 142 / 140 | 0.74% / 0.70% / 0.71% | 21 / 16 / 16 |
| MARKDOWN_CODE_STRESS | 137 / 136 / 135 | 1.46% / 1.47% / 1.48% | 22 / 22 / 22 |
| THOUSAND_MODELS | 140 / 137 / 138 | 0.71% / 1.46% / 2.90% | 32 / 29 / 32 |
| STREAMING_FINE_GRAINED | 141 / 137 / 150 | 0.71% / 2.19% / 0.00% | 28 / 27 / 26 |
| STREAMING_LONG | 140 / 142 / 141 | 1.43% / 0.70% / 0.71% | 23 / 20 / 17 |

No severe sustained stall or ANR reproduced. The earlier tiny-window 84.62%
fine-stream jank did not reproduce: controlled fine-stream trials were 0–2.19%.
Across all controlled windows jank was 0–3.76%, with 2,000-line code stable near
1.5%. Long Markdown and 1,000 Models have repeatable higher P90 (about 29–32 ms)
than some plain windows (16–25 ms). These are real observed debug-AVD costs,
not proof of hardware regression or proof that every missed deadline is host noise.
Without a matched release/device trace, attribution between Compose text/layout,
AVD runtime/GPU and host scheduling remains unresolved. No product hotspot was
isolated that warrants a speculative parser/layout rewrite. This is functional
scroll acceptance with timing debt, not a universal frame-budget guarantee.

**REPRODUCIBLE WRISTPROMPT UI COST:** long Markdown/model end-to-end AVD timing
is repeatable in the table, but no material production Main-thread hotspot or
serious stall was isolated. **EMULATOR/HOST NOISE:** the anomalous tiny-window
percentage is unreproduced; a specific host-load cause is not proved. Do not label
all measured UI cost “noise” or treat that one outlier as an app regression.

An initial post-launch five-second idle window recorded 21 frames while flushes
remained 1; after settling, a second five-second window without relaunch/interaction
recorded **0 frames / 0 janky frames**, flushes **1 → 1**. The transient startup
frames were not classified as a periodic idle loop. Source/virtual-time checks
independently establish no new presentation timer when inactive.
Initial low-frame touch samples are retained in metrics but excluded from scroll
acceptance. In particular 11/13 janky frames in one fine-stream window is too small
and poorly targeted to call a product regression. No host-load cause is assumed
without evidence; unresolved emulator/runtime variability is stated as uncertainty.

## G. Main-thread / recomposition review

VERIFIED BY TEST/CODE INSPECTION. Markdown parsing, AST conversion and annotated
span building occur in MarkdownWorker's Dispatchers.Default context. Main performs
bounded plain row/range projection and publication. Completed plain ranges and
rich blocks are reused; flushes rebuild bounded row/key lists, not all transcript
text. ChatScreen does build a key list per publication; this is not zero Main work.
No per-frame model transformation: indexed lazy items read the existing model list
with slug keys and service order. History has the existing 12-attempt/context budgets.

WearShell observes distinct busy/recovery/send gates; ChatScreen alone collects
fast-changing presentation. Account/Settings/Diagnostics do not collect partial
text. DebugScenarioActivity additionally collects full chat state and writes ACKs,
so its overhead is not a production recomposition measurement. No Compose
recomposition tracing/count claim is made. Canonical delta notifications still
reach Main; coalescing limits presentation work, not every canonical update. Plain
metrics are opt-in for tests/debug; existing MarkdownMetrics counts/timing run per
conversion in production too, not on an idle ticker. No measured unnecessary-work hotspot
justifies changing these boundaries. OpenAiHttp executes blocking HTTP/decoding
on Dispatchers.IO. No Room/database exists.

## H. Cache / retention review

Plain cache: 262,144 estimated metadata bytes; rich cache: 262,144 accounted source/
text/link/object bytes. These are accounting budgets, not measured allocator caps.
Current canonical text and active projection can coexist; there is no unbounded
history of full partial strings. Completed rows reference canonical ranges; current
stream retains settled slices and bounded tail. CommonMark AST remains local to
conversion; no AST or TextLayoutResult is cached. MarkdownWorker also retains one
ready snapshot and at most active/newest pending work, so cache limits alone do not
bound the entire process heap.

24-cycle new regression asserts cache reuse by block identity, budget enforcement,
unchanged canonical source, and zero cache accounting after each New Chat-style
reset across Markdown-heavy, 30,000-character code and 100-row table workloads.
Existing model/account reset and worker-race tests reject obsolete projections.
Eviction changes display only, never canonical conversation or completed replay.
Emulator retention measurements follow below.

## I. Idle battery / background review

No D5 presentation/parser idle timer, polling, refresh loop, connectivity observer,
background sync, service, WorkManager, wake lock, keep-screen-on or animation loop
was introduced. The 100 ms delay is event-triggered and canceled on pause; Markdown
worker suspends on its channel. Production manifest retains only existing Data
Layer listener service. Existing pending-auth expiry delay and explicit revocation
backoff predate D5 and are not new chat idle work. Debug host's keep-screen-on and
ACK writing are explicitly excluded from a battery claim. No battery-life, thermal
or energy measurement is established by source review or emulator PSS.

## J. Accessibility / semantics

VERIFIED BY TEST/CODE INSPECTION, using the native
[Compose semantics contract](https://developer.android.com/develop/ui/compose/accessibility/semantics):
native buttons expose labels/actions; current model
has an explicit Choose model description; model rows expose full authoritative
text and Compose selected=true plus a checkmark (selection is not color-only).
The selected single-model button exports checked=true/checkable=true in the Android
node dump. A temporary UI assertion expecting XML selected=true was invalid;
[Compose maps non-tab selection to checked/state-description](https://android.googlesource.com/platform/frameworks/support/+/b5b47505f867f69a06b5b43598b3b4de7fa2e14a/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt),
so the test helper was corrected without changing production model semantics.
Latest has explicit description even when rendered as an arrow; Stop remains a
separate target. Ask launches platform input; Send remains explicit draft review.
Error/recovery uses one manual action. Decorative brand icon has null description;
rule/quote decoration adds no spoken text.

Markdown headings expose heading + level; code says horizontal scroll; list markers
retain numbering/source order; table first cells are bold with label/value text
in order; underlined link text has an explicit Inspect links button opening local
inspection. These inert spans are not executable browser-link nodes. Generic shell
headings use larger text but ShellText does not currently set native heading
semantics; table record titles similarly use visual emphasis, not heading().
No speculative redesign is made; heading navigation depth belongs in D5-G checks.

No liveRegion or explicit announce call exists on streaming text, preventing a
new 10 Hz app-driven announcement stream. There is also **no dedicated terminal
announcement**; native status/action changes are exposed, but audible automatic
completion/Stop delivery is not established by code or JVM tests. Do not label
that part a spoken-accessibility pass.

## K. TalkBack

**VERIFIED BY TEST/CODE INSPECTION** for labels, selected state, ordering/source
structure and no app-driven per-chunk live announcements. Emulator TalkBack service
was installed and successfully bound; a screenshot shows its green focus on the
app's Back button. Initial activation opened Wear onboarding. After dismissing it
and relaunching Models, repeated traversal gestures returned to Wear system UI/
watch face rather than providing a reliable complete in-app traversal. Settings
were restored exactly (enabled services null, accessibility/touch exploration 0).

**BLOCKED BY EMULATOR LIMITATION / CAUSE UNRESOLVED** for complete spoken traversal,
selected-model speech and audible terminal/Stop announcements. No captured audio
or physical TalkBack acceptance. A focused app button/service binding is not a
full screen-reader pass. Native selected-state node evidence remains valid.

## L. 2x font

28 targeted canonical fixtures captured at 1.0 and 2.0: empty/multi-turn/dense Chat,
Markdown basic/long/200-item list/code/long-line/open fence/table/over-limit/link,
3/40/1,000/long-name model lists and major recovery/setup/storage/limit states.
Top and four rotary-step views captured. Key screenshots are visually reviewed;
readiness alone is not visual acceptance. Separate interactions exercise Stop,
Latest, Settings/Diagnostics, local link inspection and recovery at both sizes.
No font-size suppression; native wrapping/scrolling remain. At 2x the title/model
and ordinary labels may wrap inside words; offscreen rows can be brought to the
center. Long model names retain full semantics while visual rows intentionally
ellipsis after three lines. No promise to show every long label simultaneously.
Font scale is restored to 1.0 after each runner.

## M. Long recovery label reproduction / fix

Reproduced at 2x ERROR_401_REAUTH with Latest visible: equal half-width controls
showed `Acc / ount / & …`, hiding most of the recovery destination and making the
control band unnecessarily tall. This is more than cosmetic word breaking.
Narrow fix: large-font Latest arrow gets a fixed **48 dp** width and retains its
minimum height/description; the large-font primary ACCOUNT label becomes **Account**.
Normal-font wording, full Account & Connection screen title, action identity,
callbacks and route are unchanged. No text shrinking, horizontal label scroll,
custom layout, fallback or new action. Regression checks plan/API error mappings
still route to ACCOUNT and other action labels stay unchanged.

Matched after screenshot still wraps `Accou / nt`, but shows the complete word
without ellipsis. The button is shorter and the screen has more usable reading
space. Final exact label remains in native semantics. This residual word break
is cosmetic/readable debt for D5-G, not a claim of perfect typography.

## N. Rotary / round-screen

VERIFIED ON EMULATOR: actual rotary moves long plain/Markdown/list/code/table/
1,000-model and secondary/error screens. Final-build normal/2x Stop/Latest tests
use 20 gated chunks, explicit Stop → CANCELLED/excluded, then Latest. Horizontal
long-code drags visibly move the text at both sizes; subsequent rotary reaches
“After the code.” Two thousand code lines also reach the following prose with
rotary; no vertical child-scroll trap or crash was observed. Final 2x list-end
capture shows Item 200, with the number/body reachable by a small upward scroll.
Explicit Send from a long synthetic draft changes generation count 0 → 1 only on
the tap, at both fonts. Repeated frame timing is reported in F. Native resolution retained;
no global inset redesign. Shared TransformingLazyColumn has one vertical owner;
code uses horizontal touch scrolling only. Circular-edge clipping of rows entering
or leaving the viewport is expected; action/header rows must be scrollable into
readable center positions. No physical rotary-feel claim.

## O. Code / table / models stress

Code fixture covers long line and open/many-line fence; parser regression also
covers 65,000-unit unbroken source and exact CR/LF/terminal code preservation.
The new 24-cycle test includes 2,000 code lines. Debug-only MARKDOWN_CODE_STRESS
also exercises mixed prose, a 2,040-unit line and 2,000 code lines through the
actual production renderer at both fonts; rotary reaches the following prose. Code remains in 2,048-unit segments,
no syntax highlighting, no nested vertical scroller. Table records preserve
header/value order; 110-row table uses exact plain-source fallback; 100-row bound
is tested. 1,000 model list is indexed lazy content with stable slug keys and
service order, no metadata/search/filtering. Emulator lists have 3, 40 and 1,000
models; the added SINGLE_MODEL fixture also renders a one-row selected list at both fonts.
Single-model catalog/name behavior and all 1/3/40/1,000 fixture counts pass unit tests.

## P. Lifecycle / reset stress

VERIFIED ON EMULATOR: 12 full long-answer → Models → Back → Settings → Back →
New Chat cycles, alternating dense plain, Markdown-heavy, many-line/open-fence code
and streaming/Stop. Every reset asserts messages=0, chars=0, busy=false. No old
partial returned. Across cycles 1–12 PSS was 84,892–92,092 KiB, Java summary
13,624–18,532 KiB; final cycle 89,233 / 15,520 KiB. Cycles 3/7/11 (same code case)
Java 14,600 / 14,636 / 14,588 KiB; cycles 4/8/12 (same streaming/Stop case)
15,508 / 15,532 / 15,520 KiB. Native after warmup stayed approximately 13,740–13,968
KiB. Total PSS rises during screen/font/runtime warmup and does not return to the
initial idle baseline; that retained residency is not attributed to a leak. The
matched repeated end states do not show monotonic Java retention.

Actual Activity recreation of completed Markdown, dense completed output and
active streaming retained the same fixture owner/generation=1 and increased
creation count. Reading had been moved with rotary; exact pixel-identical anchor
restoration was not separately asserted. A gated background stream advanced from
38 to 191 canonical characters with presentation flush count held at 2; returning
resumed observedChars=191, flushes=3, generations=1. This establishes latest-snapshot
presentation without missed-frame/request replay, not physical background survival.

Process restart: **4/4 PASS — VERIFIED ON EMULATOR**, actual am-kill (no force-stop
fallback) after idle, completed, active-before-output and partial state. Each has
a new PID, fixture owner and process owner; startup is EMPTY with zero messages,
chars, draft and generations, busy=false, exactly one synthetic discovery and the
expected synthetic explicit billing/model. No inference replay. A separate final
force-stop/cold EMPTY_CHAT sample is a stronger package-stop measurement, not
substituted for these four process-death results. Existing full-run D4 ownership,
process reconstruction, connectivity, recovery, epoch/cancellation and model reset
tests pass. Process-memory chat remains intentionally non-durable. No background
request or visual-frame replay architecture added.

## Q. Production fixes

Only the reproduced large-font primary recovery label/control-band fix described
in M. Two small production files; no auth, credential, storage, signing, transport,
Data Layer, billing, replay, Markdown parser, model picker or navigation redesign.
No parser/streaming/cache performance optimization was justified by the current measurements.

## R. Automated validation

Full matrix ran once before the subsequently reproduced label correction:
**459 tests / 52 suites, zero failures/errors/skips**; debug/release assemble PASS;
lint **0 errors / 6 pre-existing warnings**. Focused final correction verification:
**40 tests / 4 suites**, zero failures/errors/skips (ErrorPresentation 16,
ConsolidatedPresentation 3, ShellInteraction 9, ShellPresentation 12), and both
builds/lint PASS again. The final debug-fixture addition was separately verified by **18 DebugScenarios
tests**, debug assemble and a final lint component. Final inventory is **461 tests
/ 52 suites**; a full 461-test rerun is **not** claimed. This follows the requested narrow
rerun policy. Initial focused D5-F stress run: 3 tests PASS.

Full matrix includes D5 navigation, plain/coalescer (22/14), Markdown (42), models
(4), error (15 before final extra regression), D4 lifecycle (8), reconstruction
(11), connectivity transitions (7), connectivity credentials (2), persistence
recovery (13), plus all other Wear suites. No mobile/shared changes; mobile not run.
Python: **40 tests PASS**, syntax **13 files PASS**, rerun after the two catalog
entries/count expectations changed. Final source/manifest/DEX isolation **PASS**,
including the 77-fixture catalog. Full release build was not repeated for debug-only
fixture additions; the corrected production release already passed.

Commands actually run with DEVELOPMENT.md JDK/SDK/cache/signing environment:

```sh
./gradlew :wear:assembleDebug --console=plain
./gradlew :wear:testDebugUnitTest --tests '*ConsolidatedPresentationTest' --console=plain
./gradlew :wear:testDebugUnitTest --rerun :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
./gradlew :wear:testDebugUnitTest --tests '*ErrorPresentationTest' --tests '*ConsolidatedPresentationTest' --tests '*ShellInteractionTest' --tests '*ShellPresentationTest' :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/check_scenario_isolation.py
./gradlew :wear:testDebugUnitTest --tests '*DebugScenariosTest' :wear:assembleDebug --console=plain
./gradlew :wear:lintDebug --console=plain
python3 tools/wear_scenarios.py --all
python3 tools/wear_process_restart.py --all
git --no-pager diff --check
```

Initial sandbox build/ADB attempts failed on the existing Gradle cache lock and
local ADB listener permissions. Approved retries used the unchanged persistent
toolchain/key and verified emulator. No dependency/toolchain update or signing
identity change. Temporary Python measurement scripts are investigation artifacts,
not new repository benchmark infrastructure. Two debug-only fixtures and catalog
metadata were added; no benchmark framework or production instrumentation.

## S. Complete emulator catalog

**77 total / 77 PASS / 0 FAIL / 0 BLOCKED — VERIFIED ON EMULATOR**, run once
on the final installed debug APK after stabilization. Each fixture had a fresh
launch token, matching live PID and RESUMED host. Watch-only cases additionally
check zero bridge counters. Generic catalog readiness is not visual or complete
interaction acceptance for all 77.

Separate targeted evidence: 28 fixture captures at each of 1x/2x, actual rotary,
recovery/Account action without inference/reload, Settings/Diagnostics/local links,
selected single-model list, Item 200 end check, 1,000-model stress, long-line and
2,000-line code, explicit draft Send, gated Stop/Latest, actual recreation,
background latest-snapshot collection, reset cycles and 30 frame windows. Screens
and key interactions are actually inspected; not every pixel of every long answer
was exhaustively reviewed. TalkBack spoken traversal is separately limited in K.
All font/accessibility settings restored; no physical tests.

## T. Release build characterization

Minification and resource shrinking are not enabled; no R8/shrinking change.
Unsigned release has dependency-merged `assets/dexopt/baseline.prof` (13,926 bytes)
and `baseline.profm` (664 bytes); debug has neither. No application-specific profile
or measured startup improvement is claimed. Final debug APK **36,235,813 bytes**; unsigned release **27,902,726 bytes**.
Exact final hashes/profile metadata are in DAY5_F_METRICS.json.

Historical D5-C documented debug size +189,259 bytes (+0.53%) and release +207,631
(+0.75%) when Markdown was introduced. Those are recorded prior measurements,
not a newly rebuilt dependency-only comparison. No large APK growth attributable
to the Markdown dependency was established; it was a modest measured addition.

Release synthetic flow profiling cannot use DebugScenarioActivity: fixtures and
manifest entry are absent, and the current release APK is unsigned. No release
credential use, signing, installation or weakened isolation was introduced to
obtain comparable synthetic flows. Release validates packaging/dependency/profile
facts; debug validates fixture behavior and scheduling bounds. Runtime debug vs
release speed/PSS equivalence is **not measured**. Release-device profiling stays
later RC hardening/owner-authorized hardware work.

## U. Battery / memory final assessment

**No meaningful monotonic Java/Activity retention or serious repeatable UI stall
was found in the bounded D5-F stress evidence.** Caches obey accounting limits and
release on reset in tests; emulator Activity/ViewRoot counts settle to one, repeated
matched cycle Java values remain stable, and settled idle has zero publications/
frames. Warm native/system residency is higher after broad use and is recorded
rather than optimized toward an old single PSS number. This is a bounded evidence
assessment, not proof against every leak or allocator high-water behavior.

No new D5 hidden idle work was found. Battery efficiency is supported architecturally,
not measured in joules/runtime. No physical battery/heat/lifetime or release-device
performance claim. Remaining timing/energy/readability questions form the targeted
D5-G checklist.

## V. Known debt

- Emulator/host-dependent frame timing; no release-device profile or energy test.
- Historical pre/post-D5-A PSS causality unresolved; no historical APK rebuild.
- 2x labels/title may break within words; long model names visually ellipsize.
- Dedicated audible terminal announcement and full spoken traversal not established.
- Active Markdown intentionally plain until terminal; stale bounded CPU parse can
  finish after pause and be rejected. Evicted rich history may display plain.
- Native RemoteInput keyboard confirmation remains the earlier emulator limitation;
  deterministic draft/result/Send safety tests pass. No platform input rewrite.
- Physical readability, rotary feel, TalkBack and subjective responsiveness remain
  PENDING END-OF-DAY PHYSICAL TEST in D5-G. Day 4 auth/GMS/RC debt stays separate.

## W. Targeted D5-G physical checklist — do not execute in D5-F

- Default/max-font Galaxy Watch readability: Chat/model/billing, model names and
  Account/recovery labels; center rows using touch/rotary.
- Long plain/Markdown/list scrolling; streamed answer while reading upward;
  explicit Latest/Stop; plain-to-rich terminal transition.
- Code horizontal touch + vertical rotary, table labels/values and inert link details.
- Models/Account/Settings/Diagnostics navigation and explicit recovery action.
- If practical, TalkBack selected model/headings/link inspection, Stop/Latest and
  terminal/interrupt announcements without chunk spam.
- Subjective responsiveness, heat and obvious battery drain during ordinary use.

No destructive storage/auth/radio/usage-exhaustion tests and no repeat of the full
Day 4 hardware matrix. D5-G requires its own authorization.

## X. Files changed

- ui/WearShell.kt — large-font Latest target width and primary recovery wording.
- ui/ErrorPresentation.kt — large-font primary-only Account label.
- ui/ErrorPresentationTest.kt — label/action regression.
- ui/ConsolidatedPresentationTest.kt — three synthetic bounded-work/reset tests.
- DAY5_F_REPORT.md, DAY5_F_METRICS.json — durable evidence.
- DAY5_STATUS.md, ROADMAP.md — status only.
- debug/DebugScenarios.kt, debug/MarkdownFixtures.kt — two bounded isolated stress fixtures.
- testDebug/DebugScenariosTest.kt — 77-count/catalog assertions and code-stress bounds.
- tools/wear_catalog.py, tools/test_wear_scenarios.py, WEAR_SCENARIOS.md — catalog metadata/count documentation.

Kotlin paths are under wear/src/{main,debug,test,testDebug}/kotlin/
io/github/grassified/wristprompt. No DECISIONS change: no new durable architecture
choice. WEAR_SCENARIOS and catalog/count tests describe two added debug-only stress fixtures;
no existing scenario meanings or ACK schema changed.

## Y. Suggested commit message

`fix(wear): validate Day 5 UX and clarify large-font recovery`

No commit or push performed.

Temporary raw evidence: /tmp/d5f/ (meminfo buckets, frame reports and UI captures),
/tmp/d5f-{baseline,baseline-rest,focused,final-automated,label-validation,fixtures,
python-final,final-lint,isolation-77,retention,settled,stress-ui-final,
stress-remaining,horizontal-center,talkback-app,frames,idle,catalog,process,cold}.log.
These paths are temporary; DAY5_F_METRICS.json and this report preserve the measured
summaries. Temporary runner failures from a nonexistent scenario identifier,
XML selected-vs-checked expectation and offscreen number/text targeting were
corrected and affected cases completed; none is recorded as an app failure/pass
without its final evidence. Final whitespace results are recorded in DAY5_STATUS.md.
