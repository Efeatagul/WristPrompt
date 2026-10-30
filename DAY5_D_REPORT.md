# Day 5 Phase D — secondary UX — 2026-10-30

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.**
D5-D implementation only. Automated and emulator results are recorded below;
physical acceptance remains D5-G. No commit/push, physical testing, protocol,
storage, routing, Markdown or streaming-cadence change.

## B. Final model picker design

Choose model, name-only lazy rows, explicit checkmark and native selected semantics,
then explicit Reload models. Removed duplicated selected-model summary and healthy
proof status above the picker. Loading/problem status remains. Native 48 dp minimum
buttons, three-line wrapping with ellipsis, full accessible text, no marquee.
Selected row uses muted teal; other rows use the existing neutral surface.

## C. Model data / display policy

**MODEL PICKER POLICY: Only authoritative model name + selected state.
No speculative metadata.** API IDs remain their own display names. A blank display
label defensively renders its slug. Discovery validation and both catalog decoders
are unchanged. No metadata catalog, ranking, subtitle, recommendation or capability
inference. Indexed lazy content reads the existing list directly, uses slug-based
keys and a shared content type, preserving service order without mapping/rebuilding
a parallel presentation catalog.

## D. Account & Connection design

Title → current explicit billing disclosure → Billing route controls → ChatGPT
account/status/actions → OpenAI API key/status/actions. Removed the duplicate proof
status before this screen. Continue, select/add account, Manage usage, sign-out,
reauthentication, API setup/change/removal and explicit route callbacks are unchanged.
API mutation buttons now name the API key explicitly. Existing recovery strings
remain unchanged; comprehensive recovery-copy work belongs to D5-E.

## E. Settings & About design

About WristPrompt, installed package version (remembered package lookup), concise
memory-only conversation explanation and Diagnostics. Explanation uses existing
bounded paragraph rows for large fonts. No inactive settings or future-feature
controls. No new persistence or preference.

## F. Diagnostics design

Still entered through Settings; proof and inline proof output retained. Adds the
same explicit billing disclosure alongside existing model/account and allowlisted
status/category/HTTP details. No raw payload, credential record or exception path
added. Full proof output is existing content, not a new diagnostic logger.

## G. Billing presentation

Using ChatGPT plan / OpenAI API · API billing. Explicit route buttons remain and
are never driven by credential presence. No subscription/API credit equivalence,
implicit credential fallback or routing change.

## H. Navigation / state result

D5-A navigation owner, Back path, retained anchors and Chat model affordance are
unchanged. Opening Models calls navigation only; Reload and selection remain
explicit. Same-model reload and changed-model reset retain existing ViewModel
contracts. Fresh process still starts Chat; retained Activity owner keeps its path.

## I. Performance / battery review

No new collector, timer, polling, service, network call, auto-refresh or animation.
The list registers one indexed lazy interval rather than one item declaration per
model. Chat streaming still reaches the shell only at the existing busy boundary;
D5-B cadence and D5-C projection are untouched. No hardware battery claim or
release-mode performance conclusion. Consolidated heap/frame profiling stays D5-F.

## J. Automated test results

Final sequential matrix: **441 tests / 50 suites, zero failures/errors/skips**.
Focused coverage extracted from that run: ModelItems 4, ShellNavigation 8,
ShellInteraction 9, ShellPresentation 12, SignOutPresentation 2, ChatViewModel 12,
ModelRestorationIntegration 15, ProcessReconstruction 11, DebugScenarios 17;
90 tests total. D5-B coalescer 14 and D5-C MarkdownProjection 42 also pass.
Selection/reset/reload/identity/routing regression coverage reuses existing tests;
UI semantics/action wiring/secrets/inactive-settings additionally reviewed in source,
not claimed as new instrumented Compose tests.

Debug/release builds PASS. Lint: zero errors, six existing warnings. Release
scenario source/manifest/DEX isolation PASS for 71 fixtures. Python 40 tests PASS;
13 Python files parse. Mobile/shared code unchanged, mobile suite not rerun.
Final git diff/whitespace results recorded in DAY5_STATUS.md.

Commands actually run with DEVELOPMENT.md JDK/SDK/signing environment:

```sh
./gradlew :wear:testDebugUnitTest --tests '*ModelItemsTest' --console=plain
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
./gradlew :wear:compileDebugKotlin :wear:compileReleaseKotlin --rerun-tasks --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_scenario_isolation.py
git diff --check
```

Cadence exception: the first complete matrix overlapped a refresh after a visual
color correction and added boundary fixtures. An older Settings string in the APK
revealed stale output. The earlier job was stopped; both variants were explicitly
recompiled, then one sequential final matrix passed in 36 seconds. Only final
sequential artifacts/results establish this handoff. No repeated full emulator
catalog. Temporary logs: /tmp/d5d-sequential-final.log, /tmp/d5d-python.log.

## K. Emulator acceptance

WristPrompt_Watch7 only, dynamically verified name. Offline synthetic fixtures;
no real authentication, credential changes or physical device operations.
Ready/PID/resumed checks passed for MODELS_LOADED, CHATGPT_AUTHENTICATED,
API_KEY_CONFIGURED, SIGNED_OUT, MODEL_UNAVAILABLE, MANY_MODELS,
THOUSAND_MODELS, LONG_MODEL_NAMES and ONE_TURN_COMPLETED (nine distinct fixtures).
Screenshots reviewed for selected/name-only rows, distinct API billing, signed-out
hierarchy, long names, Settings, Diagnostics and returned Chat. Actual rotary in
the 1,000-row list reached rows 308–311; this is responsiveness evidence, not a
frame-time benchmark or proof that all rows were rendered.

Settings → Diagnostics → Back chain uses actual UI actions. Returned Chat retains
two completed messages, discoveries=1 and generations=1. Source/D5-A tests establish
Models navigation remains side-effect-free; no new exhaustive reading-anchor visual
measurement is claimed. The final Settings capture confirms the corrected text.
Font 2.0 long-name scrolling reaches Reload; Account scrolling reaches API setup.
Font scale restored to 1.0. Full labels are available in semantics; visual names
can ellipsize at three lines. No assertion of full long-name visibility at 2x.

One warm synthetic 1,000-model sample: PSS 82,443 KiB; Java heap 15,228 KiB.
No crash or ANR observed during rotary gestures. This single sample cannot establish
absence of a heap spike, leak, battery cost or performance regression; D5-F owns
matched profiling. Temporary captures/logs: /tmp/d5d-final-*.png,
/tmp/d5d-stress.log, /tmp/d5d-final-nav.log.

## L. Max-font / rotary / accessibility

Selected checkmark plus selected semantics; full model label remains accessible
when visually ellipsized. Native controls retain font scaling and minimum touch
height. Actual emulator rotary/2x checks recorded with final acceptance below.
TalkBack and physical accessibility remain D5-F/G, not inferred from source checks.

## M. Security / identity review

Credential/router/auth/bridge implementations and durable registration identity
are untouched. Existing D4-H integration tests cover redaction during sign-out,
reconstruction, retained registration and restored identity after reauthentication,
including no automatic API fallback. Diagnostics changes add only the existing
billing presentation string. No secrets, raw protected storage or new logging.

## N. Production changes

ModelItems.kt adds indexed lazy name-only rows and selection semantics. WearShell.kt
uses those rows, simplifies picker status, reads installed version, chunks Settings
explanation and shows billing in Diagnostics. ConnectionItems.kt organizes billing
hierarchy and labels API actions explicitly. No other production changes.

## O. Known debt

Three-line visual names can ellipsize at large font; accessibility retains full
text. Broad performance/memory and physical accessibility conclusions remain open.
Historical D5-A/B/C and capability-dependent emulator authentication debts remain.

## P. Deferred to D5-E/F/G

D5-E general recovery copy; D5-F release profiling, heap characterization and
consolidated accessibility; D5-G Galaxy Watch physical acceptance. D6 persistence
is unstarted. No physical PASS claim.

## Q. Files changed

- wear/src/main/kotlin/io/github/grassified/wristprompt/ui/ModelItems.kt
- wear/src/main/kotlin/io/github/grassified/wristprompt/ui/WearShell.kt
- wear/src/main/kotlin/io/github/grassified/wristprompt/ui/ConnectionItems.kt
- wear/src/test/kotlin/io/github/grassified/wristprompt/ui/ModelItemsTest.kt
- wear/src/debug/kotlin/io/github/grassified/wristprompt/openai/DebugScenarios.kt
- wear/src/testDebug/kotlin/io/github/grassified/wristprompt/openai/DebugScenariosTest.kt
- tools/wear_catalog.py
- tools/test_wear_scenarios.py
- DAY5_D_REPORT.md, DAY5_STATUS.md, WEAR_SCENARIOS.md, DECISIONS.md, ROADMAP.md

## R. Suggested commit message

`feat(wear): polish model picker and secondary account screens`

No commit or push performed.
