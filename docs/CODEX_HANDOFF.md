# Codex handoff

This file and the linked repository documents are sufficient for a fresh developer
or Codex account. No previous chat, owner account token, debug key or private local
configuration is required to understand the project. Build a separate matched
phone/watch debug pair; preserve owner installations and keys.

## Current state

D5-F — PASS WITH KNOWN DEBT.
D5-G — PHYSICAL ACCEPTANCE PENDING.
D6 — NOT STARTED.

WristPrompt is an OpenAI-only, local-first/backend-free native Wear OS client:
ChatGPT OAuth and explicit API-key billing, account model discovery, watch-direct
streaming, real Stop, multi-turn memory chat, chat-first D5 UI, approximately 100 ms
coalesced presentation, explicit Latest, native Markdown/tables/code, a name-only
model picker, Account/Settings/Diagnostics and concise recovery actions.
Conversation history is memory-only. No persistent conversation manager exists.

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

## Latest recorded evidence, not rerun for this handoff

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
- D5-G physical UX and physical spoken TalkBack pending.
- Release-device profiling/battery/runtime equivalence pending; historical D5-A PSS causality unresolved.
- Cosmetic readable large-font word breaks remain.
- Passing source-publication gates and completed history remediation are tracked in [PUBLICATION_AUDIT.md](PUBLICATION_AUDIT.md).

## Next action

The owner will execute D5-G on Galaxy Watch 7 and supply P1–P13. Do not invent results,
number an unreceived result set or execute physical tests automatically. Record the
owner's exact scope in DAY5_STATUS.md, a new DAY5_G_REPORT.md and ROADMAP.md. Recommend
remediation only for reproducible material defects, then close Day 5 if supported.

After Day 5, the next developer task is **D6-0: READ-ONLY persistence architecture
audit**, separately authorized. Likely goals: conversation list, reopen/continue,
rename/delete, New Chat, optional pin/favorite, persistent completed exchanges,
versioned migrations and account/model/billing compatibility. Select no database or
schema before the audit. Reconcile current SPEC memory-only scope at that time.
Later RC/release hardening follows D6. Do not start D6 as part of this handoff.

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
