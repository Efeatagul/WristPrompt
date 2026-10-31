# Codex handoff

This file and the linked repository documents are sufficient for a fresh developer
or Codex account. No previous chat, owner account token, debug key or private local
configuration is required to understand the project. Build a separate matched
phone/watch debug pair; preserve owner installations and keys.

## Current state

**D5: PASS WITH KNOWN DEBT — COMPLETE.**
D6-B/C implementation is present on contributor branches; consolidated D6-D
acceptance is pending. D7 is NOT STARTED.
Final owner Galaxy Watch 7 retest: R1–R5/R7–R10 PASS; R6 structured Markdown
Wear table rendering FAIL / non-blocking known debt. TalkBack remains untested.

WristPrompt is an OpenAI-only, local-first/backend-free native Wear OS client:
ChatGPT OAuth and explicit API-key billing, account model discovery, watch-direct
streaming, real Stop, multi-turn memory chat, chat-first D5 UI, approximately 100 ms
coalesced presentation, explicit Latest, native Markdown/tables/code, a name-only
model picker, Account/Settings/Diagnostics and concise recovery actions.
Local persistent history is implemented by D6-B/C; detailed acceptance and remaining
Wear/physical checks are recorded in D6_B_STORAGE.md and D6_C_HISTORY.md.

## Read and build

Read [../AGENTS.md](../AGENTS.md), [../PROJECT_SPEC.md](../PROJECT_SPEC.md),
[../ROADMAP.md](../ROADMAP.md), relevant [../DECISIONS.md](../DECISIONS.md),
[ARCHITECTURE.md](ARCHITECTURE.md) and [TESTING.md](TESTING.md).
Use full JDK 17, SDK Platform android-37.0 / Build Tools 36.0.0 and the wrapper:

```sh
export JAVA_HOME="/path/to/jdk17"
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest
```

`:wear` owns normal inference; `:mobile` owns setup. `shared/src` compiles into
both apps without a third module. See TESTING.md for lint/release/scenario commands.
Do not change dependency pins merely to match another machine's global tools.

## D5-H current evidence — 2026-10-31

[../DAY5_G_REPORT.md](../DAY5_G_REPORT.md) records only supplied Galaxy Watch 7
observations; [../DAY5_H_REPORT.md](../DAY5_H_REPORT.md) owns remediation evidence.
An empty terminal output array no longer erases accumulated text/replay; explicit
terminal text still wins. Canonical completion already commits atomically before
partial clear; no session/coalescer ownership redesign was needed. Active output
stays plain. Empty rich conversion falls back to source. Shell/Markdown spacing
now distinguishes logical blocks from bounded continuations. At large font the
title has an intentional word break and Menu a full-width row. Diagnostics labels
the expected versus actual test output and local current attempt; no credentials.
The owner's precise terminal payload was not collected: the decoder failure is
synthetically proven, the exact original wire shape remains unproven; the owner now confirms symptom recovery.

Full Wear run 470 tests / 53 suites passes, with a later narrow header/stale-result
check rather than another full run. Builds/lint/isolation and focused emulator
checks pass. See the report for exact counts, final changes and limitations.

## Historical evidence before D5-H, not rerun for this handoff

Original evidence commit IDs map to sanitized history in [HISTORY_REWRITE.md](HISTORY_REWRITE.md).
Source: [../DAY5_STATUS.md](../DAY5_STATUS.md) and
[../DAY5_F_REPORT.md](../DAY5_F_REPORT.md), sections N–Q.

- Full Wear run: 459 tests / 52 suites, zero failures/errors/skips.
- Final narrow correction: 40 tests / 4 suites; added debug fixture run: 18 tests /
  1 suite. Final inventory 461 / 52 is covered across runs; no full 461 rerun claimed.
- Wear debug/release builds PASS; final debug lint 0 errors / 6 existing warnings;
  source/manifest/DEX scenario release isolation PASS; Python 40 tests and 13-file
  syntax PASS. Mobile/shared unchanged in D5-F and not rerun; preserved mobile
  baseline is 70 tests from D4-G.
- Emulator: 77/77 readiness, 0 FAIL / 0 BLOCKED, with separately scoped normal/2x
  captures and interaction checks; 4/4 am-kill process restart checks.
- Thirty rotary windows: 0–3.76% debug-AVD jank. Twelve reset/navigation cycles:
  no monotonic matched Java retention. This does not prove hardware performance,
  battery life or universal frame budgets. Safe aggregate metrics remain in
  [../DAY5_F_METRICS.json](../DAY5_F_METRICS.json).

## Rules that must survive future work

Responses `store=false`, `stream=true`, no `previous_response_id`; success requires
`response.completed`. Replay contains completed whole exchanges only; excluded
partial/failed/cancelled attempts remain display-only. Preserve validated opaque
reasoning/continuation items in replay. Stop cancels HTTP. Keep billing explicit,
no credential fallback, no automatic inference/model retry. Normal inference is
watch-direct. Keep debug scenarios out of release and preserve capability checks.
No foreground service, WorkManager, wake lock or connectivity observer/polling.
Native Markdown only; raw HTML inert and remote images unfetched. No forced
scrolling, explicit Latest, Stop priority; model rows contain authoritative names
and selected state only.

D5 UI projections/caches are disposable. DISPLAY TRANSCRIPT differs from RESPONSES
API REPLAY CONTEXT. D6 needs separate durable/versioned records and no per-token
writes; do not persist UI projection objects as its schema.

## Known debt

- Historical Terra → Sol restoration anomaly, unreproduced/unresolved.
- Historical transient reauthorization anomaly, unresolved, not reproduced in current acceptance.
- Natural physical phone-absent real-token refresh not observed.
- Funded BYOK success not physically verified.
- Paired-emulator CapabilityClient synchronization limitation; keep production gating.
- Destructive Keystore/AtomicFile interruption not physically tested.
- Known physical D5 debt: readable Markdown table content does not produce the intended structured Wear records. Non-blocking for D6.
- Spoken physical TalkBack remains untested and is not required for D5 closure.
- Release-device profiling/battery/runtime equivalence pending; historical D5-A PSS causality unresolved.
- Final owner R9 confirms acceptable physical maximum-font header/Menu.
- Passing source-publication gates and completed history remediation are tracked in [PUBLICATION_AUDIT.md](PUBLICATION_AUDIT.md).

## Next contributor action

D6-C history/startup/rename/delete work is recorded in [D6_C_HISTORY.md](D6_C_HISTORY.md).
Continue with D6-D consolidated acceptance after C integration. Upstream #14 may
still await maintainer merge; fork main contains the tested B integration. Keep
upstream and fork merge evidence distinct. Wear/physical pending results remain open.

D6-B canonical checkpoint/store work is documented in [D6_B_STORAGE.md](D6_B_STORAGE.md).
The contributor is authorized to continue D6/D7 without per-issue approval, and to
prepare dependent branches while upstream merges are pending. This overrides the
historical phase-stop directions below for this development run. Upstream merges
still require actual GitHub write permission; do not bypass repository protections.

D6-A was merged upstream as `78d5cc2`; its design is in
[D6_A_PERSISTENCE_ARCHITECTURE.md](D6_A_PERSISTENCE_ARCHITECTURE.md), refined by B's
bounded SQLite checkpoint decision. Next is D6-D consolidated validation, followed
by D7-A/B presets and D7-C/D/E multi-image/composer work in dependency order.
See [D6/D7 backlog](D6_D7_BACKLOG.md) for issue scopes and branch suggestions.

Monday (2026-11-02) priority: stability, then (1) D6 persistence, (2) custom prompts,
(3) multi-image input, (4) integration/release sanity, (5) image generation stretch.
Image generation must not block or destabilize the core build.

Presets are durable named USER prompt text authored on the phone, synced to watch,
and explicitly inserted into its draft. User may edit/combine text and images;
non-empty draft requires insert/replace/cancel. No hidden master/system prompt,
no automatic inference; the watch reviews and explicitly presses Send.

Use Android Photo Picker multi-select → normalize/resize/compress → app-private
WristPrompt-managed library → bounded asset/data sync → watch thumbnails and
multiple selection/removal. Do not browse the entire phone gallery remotely.
One user message supports text + N images; unsupported model/route errors clearly,
with no model switch or billing fallback. Extend the existing Responses stack.

D6 schema anticipates Text and ImageAttachment. DISPLAY TRANSCRIPT != RESPONSES
API REPLAY CONTEXT; preserve opaque validated continuation/reasoning where needed.
No Compose persistence or per-token writes. Commit completed canonical exchanges
transactionally or equivalently safely; binaries normally live in private files
referenced by durable metadata. Exact schema belongs to D6-A.

Use a feature branch and open a PR for each implementation issue; do not push
implementation directly to main. Keep PR scope to the issue, without mixing D6/D7
phases. State tests actually run and distinguish VERIFIED AUTOMATICALLY, VERIFIED
ON EMULATOR and VERIFIED PHYSICALLY. Never claim physical PASS without owner
results; never run destructive owner-device tests without exact authorization.

## License and publication

WristPrompt code: GNU GPL version 3 only, base SPDX GPL-3.0-only, with the
[narrow Google Play services linking exception](../LICENSE_EXCEPTION.md). It covers
four exact artifact versions, not arbitrary proprietary libraries or future
upgrades. There is no SPDX-listed identifier for this project-specific exception.
Canonical [../LICENSE](../LICENSE) is unchanged. Third-party licenses/notices
remain intact; see [../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
Commercial use is permitted under the applicable GPLv3 obligations. WristPrompt
and its modifications remain strong copyleft with source obligations preserved.
Source publication gates pass; the target is grassified/WristPrompt. See
[PUBLICATION_AUDIT.md](PUBLICATION_AUDIT.md) for audit scope and history boundaries.
