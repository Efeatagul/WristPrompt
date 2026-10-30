# WristPrompt engineering rules

## Read before changing files

- Read this file and the relevant parts of [PROJECT_SPEC.md](PROJECT_SPEC.md),
  [ROADMAP.md](ROADMAP.md), and [DECISIONS.md](DECISIONS.md). Inspect existing code
  and working-tree changes before editing.
- Briefly state the intended change and its security-sensitive boundaries.
- Make the smallest coherent change. Preserve user work; avoid unrelated refactors.
- Respect the requested phase boundary. Do not start the next phase automatically.
- Do not commit, push, publish, deploy, upload, create releases, or change external
  services without explicit user authorization.

## Implementation conventions

- Use Kotlin, Gradle Kotlin DSL, Compose, coroutines/Flow, ViewModel, OkHttp,
  kotlinx.serialization, Preferences DataStore, and manual dependency injection.
- Start with only `:wear` and `:mobile`. Module ownership and proposed versions
  live in DECISIONS.md. Add a module only for demonstrated duplication.
- Use Wear Material 3 on watch and Android Material 3 on phone. Prefer native
  round-screen layouts, rotary scrolling, and large tap targets.
- Keep UI state immutable; use sealed states where they prevent invalid flows.
  Keep protocol constants centralized. Never copy OAuth logic into UI code.
- Avoid large files, generic frameworks, speculative providers, and unnecessary
  caching. Do not add Hilt, Koin, Retrofit, Ktor, Room, RxJava, or an MVI framework
  without a concrete need and a recorded decision.
- Pin stable versions. Verify primary release/compatibility docs before changing
  dependencies. Document any required prerelease and its reason.

## Security and protocol changes

- PROJECT_SPEC.md owns the release-blocking security requirements. Apply them
  from the first vertical slice; never use insecure temporary token storage.
- Recheck the linked official OpenAI docs before changing OAuth or inference.
  Never infer endpoints/parameters from Codex internals or memory.
- Use synthetic credentials in tests. Keep real credentials, callback URLs,
  authenticated payloads, and unsanitized exceptions out of diagnostic output.
- A change to authentication, storage, signing, transfer, or billing must include
  relevant negative tests and identify remaining device checks.
- Never silently change the selected authentication/billing path.
- Route bridge diagnostics through `BridgeLog.warn(BridgeDiagnostic)` only. Do not
  add payload, arbitrary-text, or Throwable logging overloads. Keep protocol DTO
  `toString()` output redacted and parser failures free of raw input/cause data.

## Verification and handoff

- Run focused unit tests, lint where practical, and builds for every affected app.
  After scaffolding, the baseline is:
  `./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:lintDebug :mobile:lintDebug :wear:assembleDebug :mobile:assembleDebug`.
- Run `git diff --check`. If Git metadata or tooling is missing, report the exact
  limitation and perform an appropriate file-level check; do not claim a pass.
- Do not add implementation-mirroring tests for trivial edits. Protocol tests
  and failure-path tests are required as scheduled in ROADMAP.md.
- Report changed files, commands actually run and results, manual checks still
  required, unresolved risks, and a suggested commit message.
- Distinguish unit, emulator, and physical-device evidence. Do not check off a
  milestone based on compilation alone. Update the roadmap only with evidence.
- For the rest of the six-day sprint, label results VERIFIED AUTOMATICALLY,
  VERIFIED PHYSICALLY, PENDING END-OF-DAY PHYSICAL TEST, or BLOCKED. Unit/emulator
  evidence never establishes physical behavior. Continue automated tests, builds,
  lint/static checks and git diff --check after implementation tasks.
- Batch non-blocking device checks into the concise end-of-day checklist in
  DAY2_STATUS.md (or that day's status document); do not require device tests after
  every task. Continue within authorized scope when automated evidence is strong,
  the next work does not depend on the result, and failure cannot risk credential
  corruption, data loss or irreversible state. Ordinary UI/error/state/recovery
  changes normally wait until end of day.
- Ask for immediate manual evidence only when continuing is materially unsafe or
  likely to waste substantial work; explain the dependency/risk. Examples include
  signing changes, credential-format migration, destructive storage/Keystore work,
  fundamental authentication changes, registration invalidation risk, or required
  release/debug behavior differences. A pending physical check is not itself a blocker.
- Never clear real app data, uninstall WristPrompt, revoke a real session, delete
  credentials, invalidate Keystore keys or intentionally exhaust usage without
  explicit authorization for that exact action. Never run destructive physical
  tests automatically. Phase boundaries still apply; deferred tests do not
  authorize starting the next task.
- Documentation ownership: SPEC = what must ship; ROADMAP = sequence/gates;
  DECISIONS = choices, versions, risks, evidence; AGENTS = working conventions.

## Public developer / Codex handoff

WristPrompt is a native, backend-free OpenAI Wear OS client with Continue with
ChatGPT and a separate API-key billing mode. Normal inference is watch-direct
after setup. Read [docs/CODEX_HANDOFF.md](docs/CODEX_HANDOFF.md) first for current
state and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for ownership.

### Repository and build environment

- `:wear`: chat, model/account state, protected credentials, refresh and inference.
- `:mobile`: browser OAuth, loopback callback, setup and protected handoff; no chat.
- `shared/src/main`, `shared/src/test`, `shared/src/debug`: compiled into both apps,
  not a third module. Debug source sets must never enter release.
- `tools/`: synthetic emulator acceptance; `docs/`: public developer guides.
- Full JDK 17 (`JAVA_HOME=/path/to/jdk17`), SDK at `$HOME/Android/Sdk`, Platform
  `android-37.0`, Build Tools `36.0.0`; wrapper Gradle 9.6.0, AGP 9.4.1,
  Kotlin 2.4.21. Keep local properties, SDK/cache paths and signing keys out of Git.
- A new developer builds a matched phone/watch pair locally. Never obtain owner
  credentials or replace owner keys to make an emulator work.

### Commands and validation cadence

```sh
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew :wear:assembleRelease :mobile:assembleRelease
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest
./gradlew :wear:lintDebug :mobile:lintDebug
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/wear_scenarios.py --list
python3 tools/wear_scenarios.py --install --smoke
python3 tools/wear_scenarios.py --all
python3 tools/check_scenario_isolation.py
git diff --check
```

These are instructions, not claims that commands ran in this checkout/session.
Scenario isolation requires both Wear debug and release APKs first. Catalog and
restart/connectivity/phone-absence commands are in [docs/TESTING.md](docs/TESTING.md).
Use focused affected validation while editing and one comprehensive affected pass
after stabilization. Do not repeatedly rerun the full matrix. Documentation-only
changes require repository/security/link/whitespace checks, not full D5 acceptance.

### Architectural invariants

- Responses: `store=false`, `stream=true`, no `previous_response_id`.
- Only `response.completed` establishes success. Replay includes completed whole
  exchanges only; cancelled, failed and interrupted attempts stay excluded.
- Stop cancels actual HTTP transport. No automatic inference or model retry.
- Billing routes remain explicit; no credential fallback. The phone is not the
  normal inference proxy; the watch directly contacts OpenAI after setup.
- No foreground service, WorkManager, wake lock, connectivity observer or polling.
- Keep debug scenarios excluded from release; never weaken capability gating for
  the paired-emulator GMS limitation.

### Day 5 UX invariants

- Chat-first UI; dense left-aligned assistant answers and compact user messages.
- Approximately 100 ms event-triggered presentation coalescing; no forced
  autoscroll, Latest is explicit and Stop takes priority.
- Native Markdown; no WebView, inactive raw HTML, no remote image fetching.
- Model picker shows only authoritative display name and selected state; no
  speed/quality/reasoning/capability metadata or inferred ranking.

### Persistence and next phase

Conversation state is MEMORY ONLY. D6 must keep DISPLAY TRANSCRIPT separate from
RESPONSES API REPLAY CONTEXT, including opaque validated continuation/reasoning
items where applicable. Do not serialize D5 UI projection objects as the future
persistent schema. No storage write per streaming token.

D5-F — PASS WITH KNOWN DEBT.
D5-G — PHYSICAL ACCEPTANCE PENDING.
D6 — NOT STARTED.

The owner will supply D5-G P1–P13 results. Record only supplied evidence in
DAY5_STATUS.md, a new DAY5_G_REPORT.md and ROADMAP.md. Do not fabricate or infer
physical results. Then, after Day 5 closure and separate authorization, D6-0 is a
read-only persistence architecture audit. Do not start it automatically.

Use VERIFIED AUTOMATICALLY, VERIFIED ON EMULATOR and VERIFIED PHYSICALLY distinctly.
Physical results require owner/device evidence; pending physical checks are not
resolved by compilation. Respect the destructive-test restrictions above.

WristPrompt code is GPL-3.0-only with the narrow, exact-artifact/version permission
in [LICENSE_EXCEPTION.md](LICENSE_EXCEPTION.md). Preserve canonical LICENSE and
identify the exception when describing project licensing; do not invent an SPDX
exception identifier. New dependency versions require a license/exception scope
review. Future WristPrompt-owned contributions are expected under the base license
plus this exception; third-party code retains its original licenses.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Publication is separately gated
by [docs/PUBLICATION_AUDIT.md](docs/PUBLICATION_AUDIT.md).
