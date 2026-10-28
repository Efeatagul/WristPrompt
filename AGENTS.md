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
- Documentation ownership: SPEC = what must ship; ROADMAP = sequence/gates;
  DECISIONS = choices, versions, risks, evidence; AGENTS = working conventions.
