# Day 5 Phase H — targeted physical-failure remediation — 2026-10-31

## Final owner physical revalidation — 2026-10-31

**D5 = PASS WITH KNOWN DEBT — COMPLETE.** D6 = NOT STARTED. D7 = NOT STARTED.
VERIFIED PHYSICALLY means owner-reported Galaxy Watch 7 evidence, not agent-run tests.
This final retest supersedes the earlier failed/incomplete closure status without
rewriting its observations or promoting unexercised checks to PASS.

| Retest | Owner result |
| --- | --- |
| R1 | PASS — normal completed response remains visible. |
| R2 | PASS — terminal Markdown works correctly and looks good. |
| R3 | PASS — reader position stable; Latest appears appropriately and works. |
| R4 | PASS — Stop cancels immediately; retained partial is identified as partial/excluded. |
| R5 | PASS — completed code blocks work very well; horizontal touch movement feels natural, like inspecting code in an IDE. |
| R6 | FAIL / KNOWN DEBT — five-distribution content remains readable and understandable, but does not visibly render as intended structured Wear table/stacked records. Non-blocking for D5 and D6. |
| R7 | PASS — artificial paragraph/text gaps are gone. |
| R8 | PASS — diagnostic inference works again; expected/actual proof consistency appears correct. |
| R9 | PASS — maximum-font header/Menu now acceptable. |
| R10 | PASS — UI, navigation and hardware responsiveness remain smooth. |

Spoken physical TalkBack was not exercised and is not required for Day 5 closure.
Hardware was mildly warm, judged normal by the owner. No scientific battery-life
claim is made. Existing historical known debt remains open. No authenticated
hardware trace was collected; the precise original terminal wire shape remains
unproven. Automated and emulator evidence below remains historical, not rerun.

## Historical record before final retest

## A. Result

**PASS — PHYSICAL REVALIDATION REQUIRED.**
D5-H automated/emulator remediation complete. **PHYSICAL REVALIDATION PENDING.**
D5-F remains PASS WITH KNOWN DEBT. D5-G remains PHYSICAL ACCEPTANCE FAILED /
INCOMPLETE DUE TO REPRODUCED UX DEFECTS. Day 5 is open; D6 is NOT STARTED.
Owner evidence is recorded separately in [DAY5_G_REPORT.md](DAY5_G_REPORT.md).
No physical-device operations, real inference, credential mutation or deployment.

## B–C. Completion disappearance and terminal Markdown

A deterministic decoder → StreamResult → ChatConversation → presentation test
failed before the fix: two text deltas followed by
`{"type":"response.completed","response":{"status":"completed","output":[]}}`
produced an empty completed assistant and an empty assistant replay contribution.
The regression was an expected-text-versus-empty-string assertion failure.

`ResponseEvents.decode` flattened the empty array and called `joinToString`,
turning **no final output items** into an explicit empty-string replacement.
`StreamResult` correctly preferred that purported final replacement; conversation
completion atomically appended it and cleared partial output. The plain and
Markdown projections therefore received empty canonical text. This is upstream
of the renderer, not a demonstrated async cache/attempt-publication race. Stop
retained the accumulated text because it did not take this completed-event path.

The decoder now treats an empty output-item summary like the already supported
omitted-output summary: no replacement text or structured replay array. The
existing accumulator supplies the exact streamed text; existing conversation
completion commits it and constructs the completed assistant replay message.
A supplied nonempty final item array keeps the prior authoritative text and opaque
item/reasoning validation path. Explicit final text, including an explicitly empty
text value, still wins; the fix does not replace real terminal text with preview.

The same regression now passes, including exact canonical text, nonempty final
Markdown and next-turn replay. A second test traverses the actual InferenceSession,
InferenceViewModel and retained ChatPresentationOwner with multiple timed deltas
and a gated completion; every observed terminal publication contains the answer.
It checks all required Markdown block families, one request, no duplicate canonical
assistant and unchanged current answer after draft changes. Another owner test
proves a nonempty final replacement wins over a longer segmented stream.

A separate rich-conversion edge case could yield zero blocks for nonempty
reference-definition-only source. It now falls back to complete source, as does
a synthetic empty parser result. Parser failures remain display fallback only.
Stale-result ownership guards remain unchanged and have an added old-completed →
new-completed regression. No per-token Markdown or owner/session refactor.

**Evidence limit:** no authenticated hardware payload was captured. The empty-array
failure is proven in current code and matches the symptom; attributing the owner's
specific failure to that exact wire shape remains an inference. Normal full-output
terminal fixtures already worked before this fix. Physical revalidation must
confirm the owner's actual path; this report does not invent a captured trace.

Official contracts rechecked: [Responses streaming events](https://developers.openai.com/api/reference/resources/responses/streaming-events),
[SIWC models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference),
[preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).
Only response.completed establishes success. The official examples provide full
terminal output; they do not prove what the owner's request returned. Endpoints,
request fields, authentication and billing routes are unchanged.

## D. Text gaps / segmentation

Two actual sources were found:

- Shell `readableChunks` split text around 80 characters and emitted every slice
  as an independent lazy item with 10 dp spacing. This affected supporting text
  and proof output throughout the app, even without source paragraph breaks.
- Rich conversion split one logical block at 2,048 UTF-16 units, but every piece
  received paragraph/heading padding; code pieces also repeated surface padding.

Shell segments are now bounded at 512 Unicode-safe units and distinguish a new
paragraph from an internal continuation. A thin ShellListScope adapter gives
ordinary logical items their existing spacing and continuations zero spacing.
Buttons/model rows retain their logical spacing, keys, native components and
callbacks. Explanatory paragraph text is left aligned. Link inspection uses the
same segmentation primitive. No giant answer-in-one-item rendering was introduced.

Markdown blocks carry preceding/following continuation flags. Only real block
starts receive block padding; code surfaces receive outside padding only at the
logical block edges. A long no-newline fixture spans multiple internal segments
but has one block start; a real blank line gives two starts in both shell and
Markdown projections. Source characters remain exact in shell/canonical state.
Real paragraph and Markdown block spacing is retained; spacing is not globally
removed. Plain Chat already used zero lazy spacing and remains unchanged.

## E. Maximum-font header

At 2.0 font scale, the existing stacked Menu still inherited a content-sized
button width. Menu now occupies a full-width independent row with at least 48 dp
height and a single-line label. The large-font brand title deliberately breaks
at `Wrist / Prompt`, retaining native title typography and full WristPrompt
semantic label. Normal-font header source/layout is unchanged.

VERIFIED ON EMULATOR: final normal/2x captures reviewed; Menu label node bounds
`[153,198][302,263]` pixels at 2x, with no fragmented label or title/action overlap.
Actual tap opens Menu and exposes New Chat. Font scale restored to 1.0.
No system-font suppression, navigation redesign or model-picker changes.

## F. Diagnostics proof

The existing proof means **text delta observed + response.completed + exact output
match**. ChatGPT plan expects `WristPrompt works.`; explicit API mode still expects
`BYOK path works.`. It is not a hash/request identifier or the latest chat reply.
The empty-summary decoder defect could also erase this output and cause mismatch.
No separate stale-output cache was found.

The UI now labels diagnostic-only scope, expected output, actual test output,
completion observation and the local current test attempt. The attempt uses the
existing epoch; new tests clear old output/completion, and model/account changes
clear the result. State string output is redacted. Existing route/model and
progress/completion state remain in one ProofState; no alternate proof semantics.

A deterministic two-attempt test cancels the first, completes the second with the
exact expected text, then invokes a late old callback. Current output, completion,
attempt, explicit route and selected model remain unchanged. The debug fixture
runs two successful tests. VERIFIED ON EMULATOR: second attempt succeeds and the
exact `WristPrompt works.` output is reachable under the completion label.
Physical P9's precise mismatch was not supplied, so owner confirmation is pending.

## G–H. Changes and focused tests

Eight PhysicalRemediationTest cases cover exact preservation/replay, the live
owner and full session/ViewModel/owner transition, canonical replacement, all
requested terminal Markdown syntax, Stop/interruption exclusion, logical paragraph
boundaries, explicit empty-versus-missing values, nonempty rich fallback and stale
completed-result rejection. InferenceViewModelTest adds the diagnostic ownership
and redaction regression. DebugScenariosTest adds live sparse-summary replay and
second diagnostic-attempt coverage. Existing worker/coalescer/error tests provide
reset, failure, queued publication, old ownership and cancellation regressions.

Focused iterations ran only affected test classes and debug builds. The failing
empty-summary test is recorded in `/tmp/d5h-sparse-repro.log`; passing focused runs
are `/tmp/d5h-fix.log`, `/tmp/d5h-focused2.log`. Temporary paths are not durable
artifacts; this report preserves their findings. An initial fixture timing assertion
was corrected to advance virtual time for the two diagnostic attempts.

## I. Emulator evidence

VERIFIED ON EMULATOR only, dynamically discovered **WristPrompt_Watch7**. The
attached physical device was ignored. No complete catalog run.

| Focused case | Observed evidence |
| --- | --- |
| STREAM_TO_COMPLETED_PLAIN | Empty-summary completion; canonical terminal stage and visible retained plain output. |
| STREAM_TO_COMPLETED_MARKDOWN | Same live decoder path; retained native heading/bold/italic/inline-code/quote/rule after completion. |
| STREAM_TO_COMPLETED_LONG | Long live completion retains output and one request; continuation layout remains dense. |
| STOPPED_MARKDOWN | CANCELLED/excluded retained rich partial; explicit Stop additionally exercised on active open-syntax fixture. |
| MARKDOWN_CODE | Completed native code surface visible. |
| MARKDOWN_TABLE | Completed ordered Luna/Sol records and label/value content visible; no grid or picker metadata. |
| SINGLE_LONG_PARAGRAPH | No blank-line source; continuous dense paragraph; bounded continuation structure checked deterministically. |
| REAL_MULTI_PARAGRAPH | Distinct real paragraphs retain block separation. |
| EMPTY_CHAT at 2x | Menu readable, independent and tappable; intentional two-line title; no overlap. |
| DIAGNOSTIC_PROOF_CURRENT_ATTEMPT | Two synthetic tests; current completion and exact expected output reachable. |

Normal-font EMPTY_CHAT baseline also reviewed. Final correction rechecked only
normal/2x header, proof and sparse-completed Markdown. Additional final-build interactions: explicit Stop on active open-syntax Markdown
produces CANCELLED/excluded=1; long sparse-completion stream remains at the same
reading area through completion (before/after captures reviewed), then explicit
Latest reaches the end and disappears, with generations=1/no retry. This is
emulator interaction evidence, not physical rotary/reader-feel evidence. Captures and
metadata are temporary under `/tmp/d5h-visual`, `/tmp/d5h-emulator-results.json`.
Readiness alone is not described as visual acceptance; listed key captures were
actually reviewed. This does not establish physical rendering or battery behavior.

## J. Automated/build/lint results

VERIFIED AUTOMATICALLY:

- Full Wear suite run **once: 470 tests / 53 suites**, 0 failures/errors/skips.
  Includes D5-B plain/coalescer 22/14, D5-C Markdown 42, D5-E errors 16,
  D5-F consolidated 3, shell 12, diagnostics 11 and debug fixtures 19.
- A subsequent reviewed 2x title correction and extra stale-completed regression:
  **29 tests / 3 suites** (PhysicalRemediation 8, ShellPresentation 12,
  ShellInteraction 9), all pass. Final inventory is **471 / 53** covered across
  runs; no full 471-test rerun is claimed.
- Wear assembleDebug and assembleRelease PASS. Final lintDebug **0 errors /
  6 pre-existing warnings**. No dependency, toolchain or signing-key change.
- Final release source/manifest/DEX scenario isolation PASS, including all 83
  identifiers and the six new debug-only remediation fixtures.
- Python **40 tests PASS** after catalog metadata/count changes.
- git diff --check and changed/new text whitespace checks PASS before commit.
  Mobile/shared source unchanged; mobile tests intentionally not rerun.

Final full command:

```sh
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
```

Final narrow correction command:

```sh
./gradlew :wear:testDebugUnitTest --tests '*PhysicalRemediationTest' --tests '*ShellPresentationTest' --tests '*ShellInteractionTest' :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_scenario_isolation.py
git diff --check
```

Used existing DEVELOPMENT.md JDK/SDK/cache/signing environment. Sandbox cache
writes needed escalation; no toolchain replacement. Emulator startup required a
persistent terminal; no wipe/re-pair or owner installation change. The first cold
boot briefly foregrounded Wear onboarding; affected fixture was relaunched before
recording actual screen evidence. Build logs: `/tmp/d5h-final-validation.log`,
`/tmp/d5h-final-header.log`. No repeated full Wear/catalog/Python matrix.

## K–L. Performance and regression review

No production timer, per-token Markdown parse, persistence, background worker,
polling, HTTP retry, request, cache or service was added. Existing bounded
coalescer and worker are untouched. Segments remain lazy and bounded. Continuation
metadata and one small row wrapper add minor layout/object work; this was not
profiled as a battery/energy improvement. No hardware battery or thermal retest.
Owner's responsive/mildly-warm P12 evidence is historical supplied evidence.

Review and passing suites preserve real HTTP cancellation, explicit Latest/no
forced autoscroll, completed-only replay, excluded partials, stale ownership,
model names/selection, account/billing/error routing and no credential fallback.
No WebView, remote images, privileged link launch, protected-header output,
credential/logging/storage migration, production diagnostic payload logging,
licensing or publication-history change. Active streaming stays plain. No D6.

## M. Files changed

Production paths below are under `wear/src/main/kotlin/io/github/grassified/wristprompt/`:

- `openai/ResponsesProtocol.kt`: distinguish empty terminal summary from replacement.
- `openai/InferenceViewModel.kt`: current diagnostic attempt metadata and redacted state string.
- `ui/MarkdownProjection.kt`, `ui/MarkdownText.kt`: logical continuation metadata/padding and empty-rich fallback.
- `ui/ShellTextSegments.kt`, `ui/WearShell.kt`: bounded shell segments, logical spacing adapter and labelled proof.
- `ui/ChatScreen.kt`: responsive maximum-font title/Menu only.
- `ui/ChatItems.kt`, `ui/ConnectionItems.kt`, `ui/ModelItems.kt`: mechanical receiver wiring for the spacing adapter; row semantics/content/actions unchanged.
- `ui/LinkInspection.kt`: reuse logical shell segmentation.
- Wear tests: new `ui/PhysicalRemediationTest.kt`, updated `openai/InferenceViewModelTest.kt`, `ui/ShellPresentationTest.kt`, debug `openai/DebugScenariosTest.kt`.
- Debug-only `DebugScenarios.kt`, `MarkdownFixtures.kt`: six synthetic fixtures.
- `tools/wear_catalog.py`, `tools/test_wear_scenarios.py`, `WEAR_SCENARIOS.md`: 83-entry catalog and focused usage.
- `DAY5_STATUS.md`, `DAY5_G_REPORT.md`, `DAY5_H_REPORT.md`, `ROADMAP.md`, `DECISIONS.md`, `docs/CODEX_HANDOFF.md`: evidence, boundaries and handoff.

## N–O. Commit and push

Owner authorized one focused commit:
`fix(wear): preserve completed responses and repair physical UX regressions`.
Commit hash and actual normal `origin main` push result are reported in the final
execution handoff after the gates pass. This committed report does not preclaim
a successful future push. No force push, tag, GitHub Release or Day 5 closure.

## P. Owner physical retest — affected checks only

1. **P2/P3/P5:** normal completion retains exact full answer; plain streaming becomes
   terminal Markdown; reading upward stays preserved; explicit Latest returns to end.
2. **P4 regression:** Stop retains formatted partial/excluded copy, remains responsive
   and does not retry/duplicate.
3. **Affected P6/P7 content:** completed code and table reading/navigation now that
   terminal output survives. Earlier results were unexercised, not passed.
4. **Text gaps:** one continuous long paragraph looks continuous in Chat and shell/
   supporting text; real blank-line paragraphs and Markdown blocks retain spacing.
5. **P11:** maximum-font title/Menu readable and separate; Menu opens; normal-font
   compact header remains acceptable. Do not reduce the system font to make it pass.
6. **P9:** repeat diagnostic test; expected and actual output, completion label,
   attempt, route and model correspond to the current test. Prior output cannot win.

No repeat of unrelated model-picker/error acceptance, P12 performance campaign,
TalkBack/P13, destructive storage/auth tests or the Day 4 hardware matrix.
