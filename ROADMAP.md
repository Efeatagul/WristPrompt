# Six-day release plan

Day numbers are execution stages, not a claim that work has started. A failed
release gate blocks progression; retain evidence without credentials. Scope and
security requirements live in PROJECT_SPEC.md; choices and risks in DECISIONS.md.

## Preparation — 2026-10-28

- [x] Inspect workspace and available toolchain; record limitations.
- [x] Verify official protocol and stable dependency documentation.
- [x] Create engineering rules, specification, roadmap, and decision log.
- [x] Provision a temporary full JDK/Android SDK and initialize an empty local Git repository.

Day 1 vertical-slice gate: **PASS**, confirmed by the user on 2026-10-28.
Physical sign-in/handoff, model discovery and exact streamed output through
response.completed are confirmed. Recovery/security hardening checks remain open.
The handoff summary and Day 2 recommendations are in [DAY1_REPORT.md](DAY1_REPORT.md).
The Day 1 engineering review is recorded in [DAY1_REVIEW.md](DAY1_REVIEW.md).
Do not begin BYOK, production chat, or Day 2 automatically.

## Day 1 Phase A — application foundation

- [x] Two Kotlin/Gradle application modules with matching application identity.
- [x] Minimal round-screen Wear Material 3 onboarding with disabled auth actions.
- [x] Minimal phone Material 3 companion setup shell; no chat.
- [x] Data Layer dependency/capability discovery structure; no messages or tokens.
- [x] Backup/cleartext restrictions, ignore rules, and checksum-pinned wrapper.
- [x] Create the private GitHub repository and local origin without commits/pushes.
- [x] Build/lint both applications and verify cold launches on API 36 phone/Wear emulators.

Phase A evidence belongs in DECISIONS.md. Completing this foundation does not
complete the full Day 1 OAuth/inference gate below.

## Day 1 Phase B — authentication-start bridge

- [x] Persist one random UUIDv4 Wear host URI with atomic Preferences DataStore updates.
- [x] Enable Continue with ChatGPT to discover one companion and send a bounded request.
- [x] Receive/validate requests on phone and acknowledge durable non-secret metadata.
- [x] Surface watch progress/unavailable/error states and phone receipt state.
- [x] Centralize protocol and allowlisted diagnostics; reject malformed/expired inputs.
- [x] Add host persistence, codec, redaction, node-selection, and receiver unit tests.
- [x] Run both test suites, debug builds, and lint.
- [x] Smoke-test unpaired emulator feedback and persisted host identity after restart.
- [x] User-confirmed paired delivery on Galaxy Watch 7; phone and watch screenshots show successful setup (2026-10-28).

Manual steps are in BRIDGE_TESTING.md. Phase B alone has no OAuth/credential transfer;
the receipt confirms setup metadata only. Phase B unit/build evidence is recorded
in DECISIONS.md separately from paired-device acceptance.

## Day 1 Phase C — OpenAI authorization and protected handoff

- [x] Recheck current OpenAI and Android authentication/transfer documentation.
- [x] Phone user action, 127.0.0.1 callback, PKCE/state/nonce and exact OSS endpoints.
- [x] Persist issued client before exchange; verify signed identity and granted scopes.
- [x] Keep selected registrations separate; reuse issued ID and retained ID-token hint.
- [x] Versioned profile transfer, pending node/request/host binding, idempotent storage ACK.
- [x] Watch Keystore AES-GCM storage; phone keeps tokens only in bounded process memory.
- [x] Watch account/plan state and minimal phone success/failure/retry UI.
- [x] Complete Phase C test/build/lint/diff checks; smoke-test unpaired emulator launches.
- [x] User-confirmed browser OAuth and acknowledged handoff on Galaxy Watch 7 + paired Android phone (2026-10-28).
- [ ] Device-check Keystore persistence/key failure and interrupted/lost-ACK handoff.

Phase C alone adds no inference, BYOK, refresh, or sign-out. Phase D follows below.
See BRIDGE_TESTING.md for physical acceptance and known lifecycle limits.

## Day 1 Phase D — watch inference proof

- [x] Recheck official model catalog, Responses preview/events and refresh/error contracts.
- [x] Load protected plan credentials; serialize and atomically persist refresh rotation.
- [x] Preserve registrations on failure; block reuse of uncertain rotating grants.
- [x] Discover displayable account models in server order and select a service slug.
- [x] Send only supported request fields and stream text with distinct terminal states.
- [x] Exact-output proof success requires streamed text and response.completed.
- [x] Show plan-usage failures, preserve billing choice, expose a Manage usage hook.
- [x] Add focused protocol, refresh, transport, cancellation and UI-state unit tests.
- [x] Pass both-app test/build/lint/diff checks and record actual results.
- [x] User-confirmed model catalog loading and selection on Galaxy Watch 7 (2026-10-28).
- [x] User confirmed WristPrompt works., response.completed and the exact-output success state on Galaxy Watch 7 (2026-10-28, 19:53 Europe/Istanbul).
- [ ] Physical refresh/restart, limit handling, network interruption and account-change checks.

INFERENCE_TESTING.md owns exact acceptance steps. Synthetic tests do not satisfy
the physical gate. No BYOK, chat polish, sign-out, or Day 2 work is authorized.

## Day 1 engineering review

- [x] Audit project, authentication/storage/transfer, refresh/inference and diagnostic boundaries.
- [x] Search all requested security terms; classify legitimate protocol/test occurrences.
- [x] Fix callback reads exceeding the attempt deadline; add a regression test.
- [x] Rerun both unit suites (135 executions), assemble both debug apps, lint both apps,
  verify matching debug certificates, and check all untracked text plus git diff whitespace.
- [x] Close the paired physical vertical-slice gate with user-confirmed exact output and response.completed; recovery checks remain open.

See DAY1_REVIEW.md for findings, exact commands, limitations and next work.
No architecture decision changed during review; DECISIONS.md is unchanged.

## Day 1 — prove the real vertical slice

- [x] Bootstrap only `:wear` and `:mobile`; resolve pinned stable dependencies,
  inspect transitive versions/minSdk, and build/lint both apps.
- [x] Establish matching debug identity/signing and paired-node discovery.
- [x] Persist Wear host ID (unit-tested); physically initiate phone browser/loopback OAuth from watch.
- [x] Validate state, code response, ID token, and granted plan permission;
  persist registration and protect credentials from the first successful login.
- [x] Transfer the validated profile with node/request binding and storage ACK.
- [x] Watch discovers account models and streams a real Responses request to
  `response.completed`, using OAuth directly against the public API.
- [x] Unit-test PKCE, state checks, callback parsing, profile serialization,
  request serialization, redaction, and stream parsing introduced today.

Gate: demonstrate the entire chain on Galaxy Watch 7 + paired phone with an
eligible consenting account. A populated model picker, mock stream, or BYOK-only
request is insufficient. Record OS/browser versions and failures. If official
guidance requires an architecture change, stop and explain it before proceeding.

## Day 2 — harden authentication and recovery

- [ ] Watch-only serialized refresh, atomic rotation, expiry/clock-skew tests,
  restart recovery, terminal vs transient errors, and sign-out/revocation.
- [ ] Test rejected state/nonce/signatures/scopes/identity, registration isolation,
  key loss, transfer replay, lost ACK, disconnect, and late-profile results.
- [ ] Add protected BYOK with explicit mode choice; reuse streaming transport.
- [ ] Handle admission errors, eligibility, model failures, and usage limits.

Gate: both billing paths work independently; refresh/revocation and backup
exclusions have evidence; no secret leakage or silent billing fallback.

## Day 3 — watch chat

- [ ] Round-screen chat, text entry, account/model pickers, local context,
  visible billing path, first-use confirmation, and usage-management action.
- [ ] Stop cancels transport; partial/error/completed states stay accurate.
- [ ] Rotation/profile change/disposal cannot append stale output.

Gate: real multi-turn chats on both paths, rotary/touch operation, model/account
switches, cancellation, and interrupted-stream recovery on physical watch.

## Day 4 — optional voice and device edge cases

- [ ] Add platform voice input only if earlier gates are green; otherwise cut it.
- [ ] Check permission denial, recognizer absence, phone absence, process death,
  screen-off/background behavior, reconnect, Wi-Fi/Bluetooth transitions, and low
  connectivity on Galaxy Watch 7.
- [ ] Polish readability, tap targets, rotary focus, and accessibility.

Gate: reliable foreground text chat without voice; no hidden phone dependency
for inference/refresh after setup. Document intentional lifecycle limits.

## Day 5 — release hardening

- [ ] Complete focused protocol/security regression tests and both-app lint/build.
- [ ] Review manifests, exported components, diagnostics, backups, crypto, signing,
  dependency provenance/licenses, and release shrinker behavior.
- [ ] Add README, Apache-2.0 LICENSE, privacy policy, branding/store assets, setup
  instructions, known limits, and accurate Play Data safety declarations.
- [ ] Verify current Play/Wear requirements and final delivery signing relationship.

Gate: no unresolved security blockers; reproducible release candidates locally;
physical-device results and unverified items clearly listed.

## Day 6 — feature freeze

- [ ] Bug fixes only; rerun relevant tests/lint/build after changes.
- [ ] Physical-device acceptance on both auth paths and release-build smoke test.
- [ ] Prepare local release notes, release artifacts, and Play submission checklist.

Gate: required features and security pass; optional voice may be absent. Publishing,
uploading, signing with production credentials, and external releases require
explicit authorization. Preparation is not publication or store acceptance.

## Schedule and evidence policy

Preserve ChatGPT sign-in, BYOK, reliable streaming, and credential security. Cut
optional voice/polish first. If required gates fail, delay release rather than
relabel a partial application v0.1.0.

Every task report distinguishes unit-tested, emulator-tested, physical-device-
tested, and manual/unverified behavior. Required automated coverage includes
PKCE, state, callback parsing, credential serialization, expiry, redaction,
Responses serialization, and stream events. Device-dependent Keystore, browser,
Data Layer, signing, and rotary behavior need device evidence.

## Physical test follow-up — 2026-10-28

User screenshots confirm phone loopback return, phone success after storage ACK,
watch saved-account selection and Using ChatGPT plan. ADB identifies watch SM_L310
and phone SM_S721B. This confirms the happy sign-in/handoff path only; restart,
key-loss, refresh and failure-path checks remain open. Model loading shows a
protocol failure with no model selected, so inference cannot start. Added static
failure categories to diagnose bounded-body/UTF-8/catalog-shape failures without
logging provider bodies or credentials. No catalog-contract fallback was added.

User’s next physical result: BODY_TOO_LARGE during model loading. Replace the
256 KiB buffered catalog path with bounded streaming decoding (decision D08).
Keep physical model/inference acceptance open until the updated APK passes.

Latest user photographs confirm populated service model choices, a selected
model and enabled Test inference. The subsequent test shows a generic protocol
failure and no completion proof. Added static response-type/SSE failure categories
and safe HTTP status display to identify the remaining failure. Corrected malformed
SSE UTF-8 classification; no request fields, endpoint, billing or terminal-success
rules changed. Physical inference remains open.

The next physical result is RESPONSE_MISSING_TYPE with HTTP 200. The MIME guard
rejected the response before reading its body. Accept an absent Content-Type only
through strict bounded SSE parsing (D09); preserve explicit MIME rejection and
the terminal-event proof gate. Retry on hardware; inference remains unconfirmed.

Final user evidence at 19:53 Europe/Istanbul confirms exact WristPrompt works.
output, Observed: response.completed and the explicit success state. Day 1’s
physical vertical-slice gate is closed. Earlier blocked entries above record the
investigation history. Remaining physical refresh/recovery/security checks belong
to Day 2 hardening and remain unchecked. No Day 2 implementation has started.
