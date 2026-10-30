# Day 5 Phase C — native Markdown — 2026-10-30

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.**
D5-C only. Native Markdown improves completed/retained-partial reading while
canonical text, completed-only replay, Stop, explicit billing and inference
ownership remain unchanged. Current-answer rich styling waits until terminal
reconciliation; completed answers form the stable rich prefix. No physical test,
commit or push was performed. D5-D/E/F/G and D6 remain unstarted.

## B. Parser / dependency decision

Official Maven Central artifacts, both pinned to **0.31.0**:

- `org.commonmark:commonmark`: 220,558-byte JAR.
- `org.commonmark:commonmark-ext-gfm-tables`: 24,482-byte JAR.

Total JAR footprint: **245,040 bytes**. Both resolve together; no unrelated fork
or optional extension. Core has no runtime dependency; tables uses the same core.
The [exact upstream README](https://github.com/commonmark/commonmark-java/blob/commonmark-parent-0.31.0/README.md)
states Java 11+ and best-effort Android API 19+; both JAR class versions are 55.
Current app: JDK 17 / minSdk 30. Debug, release and actual emulator execution supply
local compatibility evidence, not a guarantee for all Android hardware.

BSD-2-Clause. THIRD_PARTY_NOTICES.md contains the upstream notice/conditions;
D7/RC must ensure it accompanies distributed binaries. Repository LICENSE is
unchanged. This release configuration does **not enable R8 minification**; a
successful release build establishes D8/desugaring/packaging compatibility, not an
R8 optimization test. D5-C does not change release configuration.

## C. Markdown presentation architecture

`ChatState canonical text → MarkdownProjection → MarkdownBlock/AnnotatedString
→ native Compose text`.

ChatPresentationOwner retains one worker while Chat is STARTED. The existing
coalescer immediately publishes first/terminal/reset plain presentation, then
submits a snapshot through a conflated channel. One consumer converts on
Dispatchers.Default; no coroutine per token and no unbounded queue. Main handles
ownership/publication only. The synchronous parser is bounded, not forcibly
interruptible; obsolete active work finishes and is rejected.

Parser AST references never enter conversation, replay, UI state or persistence.
The worker cache stores source identity and finalized immutable native blocks,
accounting at most 262,144 bytes. Accounting includes source/display/link text and
object allowances; it is not an exact heap measurement. One active and one newest
pending request also retain bounded canonical snapshots. Reset removes ready
presentation immediately and clears the worker cache when consumed; final owner
cancellation clears it. Hidden work cannot publish; resume reconstructs the latest
snapshot. Eviction cannot change inference/context.

## D. Supported / unsupported syntax

Supported: paragraphs, bold, italic/nested styles, compact H1–H6, ordered/unordered
lists, inline code, fenced/indented code, blockquotes, horizontal rules, inert
links and stacked GFM table records. Native headings expose heading semantics and
level. H1/H2/H3+ use 19/17/16sp; normal font scaling is preserved. Visual list/
quote nesting is bounded; source order and ordered starts remain intact.

No task widgets, strikethrough, footnotes, alerts, front matter, LaTeX, Mermaid,
media, syntax highlighting, browser handoff or active HTML. Unsupported/malformed
syntax uses parser text or complete plain-source fallback. Raw image syntax has
textual alt representation only.

## E. Streaming Markdown strategy

Completed answers are the settled prefix, reused from the bounded cache. The
entire active answer remains in D5-B's bounded Unicode-safe plain segments. This
conservative policy avoids treating blank lines as proof against future reference
links, list/table continuation, open fences or unresolved delimiters. It does not
provide incremental rich formatting inside the current answer; that is an
explicit presentation tradeoff.

No growing-answer full parse occurs per raw delta or per streaming flush. Evicted
history stays in the existing plain projection during streaming. At completion,
Stop, interruption, incomplete or failure-with-partial, reconcile authoritative
canonical final/replacement/retained partial text. Rich failure keeps exact plain
source and cannot change a completed inference into a failure or trigger retry.

A publication must match the exact current canonical ChatState identity and wanted
visible snapshot. Reset, attempt changes, account/model clearing, pause and owner
clear reject old results, including when canonical state changes before its UI
collector runs. Existing request epochs/replay semantics are untouched; there is
no new independent inference epoch.

## F. Table design

First column is each record's heading; remaining columns become label/value rows
in original order. Blank headers become `Column N`; absent values become `(empty)`.
Eight columns / 100 body rows / 16,384 table-source units / 808 parser cells bound
adaptation. Excess cells, which normal GFM would discard, deliberately trigger
whole-answer exact segmented plain fallback. Oversized/malformed tables lose no
source rows. Table demo speed/quality labels never reach model-picker rows.

## G. Code block design

Bounded 2,048-unit native monospace segments share neutral dark surfaces. Fence
language is only a plain label. Original indentation/newlines are retained;
source spans restore CR/LF separators and avoid synthesized trailing newlines for
unclosed fences. Long lines have horizontal touch scroll, with no child vertical
scroll or rotary handler. No execution, highlighting dependency or network action.
Extreme sources exceeding rich budgets fall back to exact segmented plain source.

## H. Link / HTML / image security policy

Underlined link labels expose an explicit **Inspect links** control. A separate
local lazy list displays full label/destination and safely derived host; even
HTTP(S) has no launch action. URI parsing performs no DNS, redirect or preview
lookup. Userinfo, malformed hosts and every non-HTTP(S) scheme remain classified
non-actionable. Deceptive labels do not conceal the inspected destination.

Raw HTML is literal native text. No WebView/HTML intermediate or script execution.
Images become `[Image: alt text]`; no image loader, bitmap decode, destination
fetch or automatic external intent. Parser exceptions are caught without content
or raw exception logging. No Markdown data reaches authentication/billing/context
or privileged intent boundaries.

## I. Adversarial input limits

Parser block/inline nesting: 16/16. Conversion depth: 32. Rich blocks: 512.
Styled/code segment: 2,048 UTF-16 units, extended only to preserve one unusually
long grapheme. Rich answer source: 65,536 units. Visible list depth: three.
Inline block: eight links, each destination at most 2,048 units. Cache: 262,144
accounted bytes. No streaming parse candidate is created.

Deep nesting, delimiter spam, huge tables, long URLs/tokens and malformed input
have deterministic tests. Any rich budget/conversion failure falls back to all
original source in safe segments; canonical assistant text is never truncated.

## J. Performance results

Final full-run deterministic metrics:

- 10,000 burst deltas: **zero growing-answer parses during streaming, one final
  full parse**, measured maximum active parse = one; pending channel capacity one.
- Unchanged D5-B workload: **65,000 deltas / 6,500 virtual ms → 66 flushes,
  67 segmentation calls, 81,195 projected UTF-16 units**. Maximum pending flush one.
- Eight matched JVM content workloads: **eight parses, 22,087 source units,
  one rich fallback** (oversized destination). No output contents logged.
- Cache accounting is tested at ≤262,144 bytes, including eviction and reset.
  Exact on-emulator cache occupancy/eviction counters were not exported.

| Matched JVM workload | Source units | Plain projection ms | Rich parse + conversion ms |
| --- | ---: | ---: | ---: |
| MARKDOWN_BASIC | 32 | 0.151 | 1.571 |
| MARKDOWN_LONG | 4,000 | 0.059 | 19.694 |
| MARKDOWN_CODE | 2,007 | 0.050 | 6.742 |
| MARKDOWN_TABLE | 616 | 0.045 | 10.006 |
| MARKDOWN_LIST_LONG | 1,400 | 0.063 | 8.726 |
| MARKDOWN_MALFORMED | 2,000 | 0.177 | 11.307 |
| MARKDOWN_DEEP_NESTING | 2,001 | 0.218 | 0.657 |
| MARKDOWN_LONG_URL | 10,031 | 0.570 | 2.599 |

These are single host-JVM samples with different work/representation, not watch
latency or a claim that parsing is as cheap as plain segmentation. Timing includes
parse and conversion together. Terminal/Stop/interruption reconciliation is
covered deterministically; separate terminal-only latency, GC counts and tracing
were not instrumented. Open-fence/streaming workloads perform zero rich parses
until terminal state; their timing is deliberately absent from the parse table.

Four matched emulator workloads used the same order, four identical touch swipes,
font 1.0, debug signing, AVD and system metrics. Initial warm-process PSS increased
10–16% after running the broad Markdown catalog. This triggered a diagnostic
repeat with each version installed and its synthetic process freshly started:

| Workload | Fresh PSS KiB before → after | Fresh Java heap KiB before → after | Fresh janky frames before → after |
| --- | ---: | ---: | --- |
| MULTI_TURN_CHAT | 79,385 → 80,471 | 16,140 → 16,208 | 10/95 (10.53%) → 21/91 (23.08%) |
| STREAMING_LONG | 79,820 → 81,334 | 15,136 → 15,572 | 16/85 (18.82%) → 15/100 (15.00%) |
| LONG_UNBROKEN_TEXT | 79,601 → 84,942 | 14,304 → 17,924 | 8/94 (8.51%) → 8/106 (7.55%) |
| DENSE_LONG_ANSWER | 79,527 → 82,526 | 13,696 → 15,088 | 3/94 (3.19%) → 3/106 (2.83%) |

Fresh-process PSS differences were approximately 1.4–6.7%, so the initial >10%
PSS increase did not reproduce under matched process warmup. This suggests a
warmup/GC confounder; it does not establish a cause. The initial matched jank
counts were 19→4, 9→5, 4→4 and 3→4 respectively, opposite to the fresh multi-turn
sample. No consistent frame-regression conclusion is supported by these small,
noisy debug samples. The fresh multi-turn worsening remains explicit D5-F debt;
no automated frame-performance PASS is claimed. Interactive rotary/touch remained
usable in the separate targeted session.

**16 real production New Chat cycles**, same synthetic Markdown fixture/owner:
each cycle regenerated then reset through Menu; ACK confirmed messages=0 and
busy=false. Java-heap samples: **13,124–13,240 KiB**, first→last 13,124→13,224.
PSS: **81,836–82,604 KiB**, first→last 81,836→82,604. Heap stayed near 13.2 MiB
after warmup; the small PSS increase and bounded sampling do not prove absence of
all leaks. No forced GC, heap dump, real credential mutation or physical action.

| Artifact | Before bytes | After bytes | Impact |
| --- | ---: | ---: | ---: |
| Debug APK | 36,042,218 | 36,231,477 | +189,259 (+0.53%) |
| Release unsigned APK | 27,678,711 | 27,886,342 | +207,631 (+0.75%) |

Temporary raw evidence: `/tmp/d5c-validation.log`, `/tmp/d5c-{before,after}-profile.json`,
`/tmp/d5c-{before-fresh,after-fresh}-profile.json`, `/tmp/d5c-reset.json`, and
`/tmp/d5c-repeat-profile.log`. Summaries here are durable; temporary files are not.
Release profiling remains D5-F. No physical battery-cost claim follows.

## K. Automated validation

Implementation iterations ran only Markdown/worker, coalescer and fixture tests,
plus focused debug compilation/builds for visual checking. The complete requested
validation matrix was run once after implementation stabilized. Exact final
counts and results:

| Check | Final result |
| --- | --- |
| Full Wear JVM suite | **436 tests / 49 suites**, zero failures/errors/skips. |
| MarkdownProjectionTest | **42 tests**, included in the full run. |
| Existing coalescing / plain presentation / debug fixtures | **14 / 22 / 16 tests**, included in the full run. |
| Named shell, Markdown, streaming/race/cancellation, context and lifecycle subset | **192 tests / 14 suites**, extracted from the full run; no additional full rerun. |
| Wear assembleDebug / assembleRelease | PASS / PASS. |
| Debug signing | Existing SHA-256 `fd8491b6238368a04d58bd0653c35ff54a3a17fc9b31e53a6af7110579d37061` retained; APK signature verification passes. |
| Wear lintDebug | Zero errors/fatal; six existing warnings. |
| Release scenario isolation | PASS: source, manifest and DEX; all 69 names absent from release. |
| Python tools | **40 tests**, zero failures. |
| Python syntax | **13 files** parsed successfully. |
| git diff --check / changed-new whitespace | PASS; all **23 changed/new text files** checked. |

Commands actually run for the final matrix, using DEVELOPMENT.md's persistent
JDK/SDK/cache/signing environment:

```sh
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_scenario_isolation.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/wear_scenarios.py --install --all
git diff --check
```

Python syntax used `ast.parse` for all 13 tool files; new/changed text whitespace
was checked separately, including untracked files. Focused iteration tests caught
and fixed JVM dispatcher-teardown races and CommonMark's omission of empty source
spans before the final complete run. No full suite/lint/release/catalog/Python
matrix was repeatedly run after small edits. Mobile/shared runtime code and mobile
resolved dependencies did not change, so mobile tests were not rerun. Its prior
70-test result is historical, not fresh evidence.

## L. Emulator acceptance matrix

Only the verified **WristPrompt_Watch7 / WATCH_SERIAL** was used. The attached
physical phone was ignored. Generic catalog success means fresh readiness,
matching live PID and RESUMED host; it does not mean every screen was visually
reviewed. Final catalog: **69/69 PASS — VERIFIED ON EMULATOR** for readiness/liveness,
executed once on the final build. The 22 Markdown views were additionally captured
in a targeted session; selected screenshots and interactions were actually reviewed.

| Scenario / interaction | Disposition and scope |
| --- | --- |
| MARKDOWN_BASIC / LONG | PASS — VERIFIED ON EMULATOR: compact native headings/spans, flat assistant text, scrollable density. |
| MARKDOWN_CODE / CODE_LONG_LINE | PASS — VERIFIED ON EMULATOR: monospace neutral surface; actual horizontal drag moves code; subsequent rotary reaches surrounding prose. |
| MARKDOWN_TABLE | PASS — VERIFIED ON EMULATOR: Luna/Sol records and ordered labels, no grid or model-picker changes. |
| MARKDOWN_LINK | PASS — VERIFIED ON EMULATOR: explicit Inspect links opens local Back/details list; full URL/host shown; debug activity remains top-resumed. |
| MARKDOWN_RAW_HTML / IMAGE | PASS — VERIFIED ON EMULATOR: visible literal script markup and textual alt fallback. No fetch path exists in source. |
| MARKDOWN_DEEP_NESTING | PASS — VERIFIED ON EMULATOR: bounded quote treatment plus safe literal over-depth markers; scrolling usable. |
| STREAMING_MARKDOWN / OPEN_SYNTAX | PASS — VERIFIED ON EMULATOR: plain live syntax; terminal rich reconciliation / explicit Stop. |
| INTERRUPTED_MARKDOWN / STOPPED_MARKDOWN | PASS — VERIFIED ON EMULATOR: retained formatted partial; Stop/context-exclusion labels reachable. |
| TERMINAL_MARKDOWN_REPLACEMENT | PASS — VERIFIED ON EMULATOR: authoritative replacement heading wins over preview. |
| Malformed/table-over-limit/blocked-link/delimiter/long-list/long-URL fixtures | PASS — VERIFIED ON EMULATOR for launch/readiness; deterministic parser/security tests establish exact adaptation/fallback. Not a full visual review of every row. |
| MULTI_TURN_CHAT / STREAMING_LONG / LONG_UNBROKEN_TEXT | PASS — VERIFIED ON EMULATOR for final catalog and matched gesture workloads; existing context/cancellation/Latest unit coverage passes. |
| Explicit Latest | PASS — VERIFIED ON EMULATOR: reaches transcript end, including large-font table records and terminal states; hides when at end. |

`LATEST_AVAILABLE` was not an existing public identifier; the retained Latest
control was exercised using long and streaming fixtures. No alias or renamed
baseline fixture was introduced. No emulator limitation blocked D5-C presentation;
capability-dependent authentication remains the unrelated existing emulator limit.
Screenshots: `/tmp/d5c-visual/`, `/tmp/d5c-interactions/`,
`/tmp/d5c-link-{inspection,destination}-real.png`.

## M. Maximum font / rotary

**PASS — VERIFIED ON EMULATOR** for actual rotary SCROLL events in Markdown
and after horizontal code scrolling. Vertical navigation reaches prose after the
code surface. No nested vertical scroller was added.

2.0 font-scale stress exercised headings, code, table records, link labels,
retained terminal states and active streaming. More scrolling is required;
Latest remains a separately labelled arrow and actual Stop produces CANCELLED.
Table end/terminal labels and Ask remain reachable. Font scale was restored to
**1.0**. This is emulator stress, not physical maximum-font or TalkBack acceptance;
physical Markdown UX remains D5-G.

Handoff-continuation limitation: the final extra 2x long-list end capture was not
visually inspected before temporary captures disappeared. The named emulator was
no longer connected when that one check was retried. Normal-font list rendering,
list numbering/depth tests and earlier large-font heading/code/table/terminal
checks remain recorded; final long-list visual stress is pending D5-F/G. No full
validation matrix was repeated during continuation. Retained XML reconfirmed
436 tests / 49 suites and final whitespace checks passed all 23 changed/new files.

## N. Battery / memory review

Source review: no polling, idle parse timer, background loop, service, wake lock,
WorkManager, image fetch, browser fetch or new network path. A worker suspended
on its channel consumes no polling wakeups. Work follows visible snapshot changes;
terminal/clear/resume are explicit events. ASTs are local and released after
conversion; cache and parser budgets are bounded. Heap/PSS observations below do
not prove absence of all leaks or establish hardware battery behavior.

## O. Security review

VERIFIED AUTOMATICALLY by dependency/source review and regression tests: raw HTML
inert; images textual; links inspect-only; blocked schemes inert; no content logs;
no privileged Markdown intents; parser failure isolated from transport success.
OAuth, credentials, signing, Data Layer, explicit billing selection, request
ownership and completed-only replay implementation are unchanged. No restrictive
plain-text-only model instruction was found; no model prompt edit was needed.
Release source/manifest/DEX scenario isolation is checked separately below.

## P. Production changes, file by file

All Kotlin paths below are under `wear/src/main/kotlin/io/github/grassified/wristprompt/ui/`.

| File | Change |
| --- | --- |
| MarkdownProjection.kt | Bounded parser/AST adapter, immutable block/span/link model, exact plain fallback, tables, cache and safe aggregate metrics. |
| MarkdownWorker.kt | One conflated worker, Default dispatcher, identity/visibility rejection and cache lifetime. |
| MarkdownText.kt | Compact native block rendering; heading semantics, code horizontal scrolling, inert inspect action. |
| LinkInspection.kt | Local destination/host text in a bounded lazy list. |
| ChatPresentation.kt | Optional disposable Markdown block on an existing transcript row. |
| ChatPresentationOwner.kt | Wire worker after the unchanged cadence scheduler; visibility/resume/reset handling. |
| ChatItems.kt | Block content types and native Markdown rendering for assistant rows. |
| ChatScreen.kt | Open/dismiss local link inspection; reset dismisses stale inspection. |
| wear/build.gradle.kts | Add core and official matching tables extension to Wear only. |
| gradle/libs.versions.toml | Exact 0.31.0 aliases; existing dependency pins unchanged. |

## Q. Known debt

- Current-answer rich formatting waits for terminal reconciliation; completed
  answers supply the settled prefix. This is conservative and intentional.
- Cache eviction may return older answers to plain presentation. No streaming
  reparsing is used to refill evicted history.
- GFM excess-cell tables use complete plain-source fallback rather than invented
  record semantics. Oversized links/tables/source/block counts likewise fall back.
- Parser CPU work is bounded but not interruptible mid-call; stale results cannot
  publish. Timing counters are combined parse/conversion measurements.
- Debug frame/PSS evidence has limited repetitions and GC/warmup confounders.
  Release-mode profiling and consolidated accessibility remain D5-F.
- Physical Markdown readability/performance/accessibility remains D5-G.
- Third-party notice delivery and actual R8/release hardening remain D7/RC.
- Existing funded-BYOK, capability-emulator and historical Day 4 debts are unchanged.

## R. Deferred to D5-D

Final model/account/settings presentation. Model rows still show only authoritative
display name and selection state, with no speed/quality/reasoning/capability data.
No D5-D/E/F/G implementation, persistence, provider, TTS or speech stack was started.

## S. Files changed

Production files: ten rows in section P. Additional files:

- `wear/src/debug/kotlin/io/github/grassified/wristprompt/openai/MarkdownFixtures.kt`
- `wear/src/debug/kotlin/io/github/grassified/wristprompt/openai/DebugScenarios.kt`
- `wear/src/test/kotlin/io/github/grassified/wristprompt/ui/MarkdownProjectionTest.kt`
- `wear/src/test/kotlin/io/github/grassified/wristprompt/ui/PresentationCoalescerTest.kt`
- `wear/src/testDebug/kotlin/io/github/grassified/wristprompt/openai/DebugScenariosTest.kt`
- `tools/wear_catalog.py`, `tools/test_wear_scenarios.py`
- `THIRD_PARTY_NOTICES.md`, `DECISIONS.md`, `ROADMAP.md`, `WEAR_SCENARIOS.md`,
  `DAY5_STATUS.md`, `DAY5_C_REPORT.md`.

Existing fixture IDs remain intact; 22 additions raise the catalog from 47 to 69.
Test constructor changes inject the scheduler into the new worker rather than
letting JVM Main-dispatcher teardown race a Default-dispatcher cleanup.

## T. Suggested commit message

`feat(wear): add bounded native Markdown chat presentation`

No commit or push performed.
