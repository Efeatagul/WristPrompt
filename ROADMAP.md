# Six-day release plan

## Current Day 2 gate — 2026-10-29

**PASS WITH KNOWN DEBT.** Owner-confirmed physical refresh-regression restoration,
ChatGPT-plan completed proof, normal sign-out/reauthorization with a transient
anomaly, API 401/no-ChatGPT-fallback, explicit mode switching/back-to-plan and
cancellation are recorded in DAY2_PHYSICAL_ACCEPTANCE.md and DAY2_REPORT.md.
Remote revocation result, precise refresh telemetry and an exact per-mode restart
matrix are not inferred. Offline sign-out is SKIPPED / NOT REQUIRED FOR DAY 2
PHYSICAL GATE. D2-1 prior physical PASS remains valid and is not repeated.

Successful real BYOK inference: **BLOCKED — no funded OpenAI API credential
available for physical verification**. External testing constraint accepted for
this stage; synthetic successful-BYOK coverage remains mandatory. No purchase or
real API key required. Day 2 does not fail for this constraint, and physical paid
API success is not claimed.

Day 3 development and consolidated automated/emulator acceptance: **PASS WITH
KNOWN DEBT**, D3-A through D3-G complete for that scope. See DAY3_REPORT.md for
272 Wear / 70 mobile / 8 Python tests, both debug/release builds and lint,
release isolation, and all 23 emulator scenarios. Physical RC gate remains open;
funded BYOK and capability-dependent emulator auth constraints are not app failures.
Historical restoration and reauthorization anomalies remain unresolved.

## Final Day 4 gate — 2026-10-29

**PASS WITH KNOWN DEBT — COMPLETE.** D4-A through D4-H automated, emulator and
targeted physical acceptance are complete. Owner-reported P1–P10 results, including
the successful fixed-D4-H-APK P9 sign-out/OAuth/handoff/models/inference retest, are
recorded in DAY4_REPORT.md section 11 and DAY4_STATUS.md's final matrix.

Latest D4-H baseline: Wear 343 tests / 45 suites, zero failures/errors/skips;
Wear debug/release builds PASS; lint zero errors/six existing warnings; release
scenario isolation and whitespace checks PASS. Preserved D4-G baseline: mobile
70 tests, Python 40 tests, emulator catalog 43/43, smoke 11/11, process restart
4/4 am-kill and 4/4 force-stop, synthetic connectivity 6/6, phone absence 6/6.
Mobile/Python were not rerun during D4-H; final closure reran no test/build/emulator.

P1: PASS — PHYSICAL, WITH INTERMITTENT PRIOR INTERRUPTION NOT REPRODUCED.
Best-effort continuation remains; no foreground service/Ongoing Activity or new
background architecture was justified. P2 screen-off interruption satisfies the
best-effort contract. P6 proves exact GPT-5.6 Luna only, not Terra.

Known debt remains non-blocking for Day 4: historical Terra -> Sol anomaly is
unreproduced/unresolved; Terra-specific physical retest is deferred unless naturally
useful later. Historical transient reauthorization anomaly remains unresolved and
was not reproduced during current physical acceptance. Natural phone-absent real
token refresh was not physically observed (automated watch-only coverage passes;
no forced expiry). Funded BYOK success is unverified without funded API credits.
Paired-emulator CapabilityClient synchronization remains a GMS/emulator limitation;
production physical Data Layer handoff works. Destructive Keystore/AtomicFile
interruption tests were intentionally not physically performed.

Historical Day 4 recommendation was release/RC hardening. The explicit D5
authorization below supersedes that schedule: UX 2.0 + Markdown, followed by
D6 persistence architecture/implementation and later release hardening.

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

- [x] Watch-only serialized refresh, atomic rotation, expiry/clock-skew tests,
  restart recovery, terminal vs transient errors, and sign-out/revocation.
- [x] Test rejected state/nonce/signatures/scopes/identity, registration isolation,
  key loss, transfer replay, lost ACK, disconnect, and late-profile results.
- [x] Add protected BYOK with explicit mode choice; reuse streaming transport.
  VERIFIED AUTOMATICALLY; physical API 401/no-fallback PASS; real successful BYOK BLOCKED by no funded credential.
- [x] Handle admission errors, eligibility, model failures, and usage limits (VERIFIED AUTOMATICALLY; do not exhaust real usage).

Gate: PASS WITH KNOWN DEBT for Day 2. Both paths are tested automatically;
ChatGPT success and API-failure isolation are physically verified. Successful paid
API physical inference is externally BLOCKED and explicitly exempted for this
stage. Refresh/sign-out/backup safeguards have source/test evidence; remote
revocation outcome and exact refresh telemetry are not claimed as physical facts.

## Day 3 — watch chat

Development acceptance: **PASS WITH KNOWN DEBT**. Evidence: DAY3_REPORT.md;
phase-local history: DAY3_STATUS.md.

- [x] D3-A readable round-screen shell, billing header and reachable fixed actions:
  VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR at default font settings.
- [x] D3-B restoration investigation/test hardening: 15 integration tests pass;
  exact model identity, reconstruction/reorder/fallback and mode/account isolation.
- [ ] Historical Terra -> Sol physical anomaly: unreproduced and unresolved;
  no speculative fix or physical resolution claimed.
- [x] D3-C arbitrary text chat, bounded in-memory completed-only explicit replay,
  native input, New Chat and account/model/billing context boundaries.
- [x] D3-D generation/cleanup serialization, actual transport Stop, partial
  exclusion and stale/duplicate-event rejection: VERIFIED AUTOMATICALLY;
  synthetic rendered streaming/Stop VERIFIED ON EMULATOR.
- [x] D3-E current auth/model/billing/error transitions and safe recovery guidance.
- [x] D3-F debug-only harness, 23 emulator scenarios, launcher and release isolation.
- [x] D3-G complete both-app automated validation, emulator acceptance and reporting.
- [x] Post-D3 sanity — PASS — PHYSICAL (owner report): current debug APK updates
  on Galaxy Watch 7 / Android phone, real ChatGPT-plan handoff, account-specific
  models and normal relaunch/restoration, arbitrary multi-turn chat, streaming,
  Stop/next request, New Chat, offline recovery and usable long-response scrolling.
  This does not resolve the historical Terra -> Sol or reauthorization anomalies.
- [ ] Checks 7 and 9 — NOT PHYSICALLY EXERCISED — COVERED BY AUTOMATED/EMULATOR
  ACCEPTANCE: cancelled-exchange exclusion from actual subsequent server context,
  and model-change conversation-context boundary.
- [ ] DEFERRED — FINAL RC: final sign-out/reauth/revocation outcome, rotary/larger
  fonts, process death, background/screen-off, untested account/billing boundaries,
  interrupted-stream recovery, remaining lifecycle/network and
  release-build checks. Successful funded BYOK remains unverified/constrained;
  no purchase is required. See DAY3_REPORT.md for physical evidence boundaries.

The Day 3 checklist above is a historical RC handoff. Day 4 P1–P10 now close
the overlapping targeted sign-out/reauthentication, rotary/font, process restart,
background/screen-off and network checks; remote revocation, actual server-context
boundaries and release-specific acceptance remain deferred.

The original physical Day 3 release gate remains **DEFERRED FINAL PHYSICAL RC**,
not checked off by development acceptance. Routine UI/state acceptance uses the
emulator; final hardware/real-network checklist is in DAY3_REPORT.md. Capability-
dependent production auth is NOT VERIFIABLE ON CURRENT EMULATOR LAB. D3-G's paired
Data Layer smoke was BLOCKED — ENVIRONMENT (required pair unavailable); earlier
capability isolation evidence remains unchanged. No production fallback.

## Day 4 — lifecycle, resilience and device acceptance — COMPLETE

**PASS WITH KNOWN DEBT.** Phase counts below are historical; the final baseline
and targeted physical acceptance are recorded above. Deferred RC evidence and
optional voice are not open Day 4 closure gates.

- [x] D4-0 ownership audit (supplied complete) and D4-A retained Activity/background/
  final ViewModel-clear hardening: 288 Wear / 8 Python tests, Wear debug/release
  builds, lint and release isolation pass; six lifecycle/composer/terminal scenarios
  pass emulator acceptance. Screen-off evidence is Dozing/resume, with Activity
  still RESUMED during measurement, not physical uninterrupted execution.
- [x] D4-A physical P1 background acceptance (prior intermittent interruption not
  reproduced) and P2 accepted best-effort screen-off interruption; no new lifecycle change.
- [x] D4-B fresh encrypted object-graph reconstruction and actual emulator process
  restart acceptance: durable identity/billing/model restoration, empty chat/no
  replay, conservative refresh/sign-out recovery; 303 Wear / 16 Python tests pass.
- [x] D4-B targeted P3 physical process restart: account/billing/model restored,
  empty non-durable chat, no replay, exact RESTART WORKS. Full reboot/Keystore/
  release-build lifecycle evidence remains separate deferred RC scope.
- [x] D4-C network transitions: VERIFIED AUTOMATICALLY and six synthetic scenarios
  VERIFIED ON EMULATOR; owner-reported P5 real network-loss/recovery PASS — PHYSICAL.
  See DAY4_STATUS.md and DAY4_REPORT.md for scope.

- [x] D4-D phone-independence audit/integrated regression and six synthetic
  presentation scenarios: VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.
  No production change required; phone is setup/reauth companion, not inference proxy.
- [x] D4-D P4 physical watch Wi-Fi with phone Bluetooth disconnected: model
  operations, plan inference, follow-up, Stop and New Chat pass. Natural token
  refresh remains unobserved physically; automated coverage passes, no forced expiry.
- [x] D4-E persistence/recovery edge cases: VERIFIED AUTOMATICALLY, 137 focused /
  340 full Wear tests, 70 mobile tests, both Wear builds/lint/release isolation.
  No production change required; final durability matrix in DAY4_STATUS.md.
- [x] D4-E targeted P3/P6 persistence acceptance: account/billing/model restored,
  GPT-5.6 Luna only. Destructive Keystore/AtomicFile tests intentionally not run;
  full reboot/platform interruption evidence remains deferred RC scope.
- [x] D4-F harness consolidation: VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR;
  43-name catalog, bounded 11-case smoke, shared safe ADB helpers and separate
  specialized runners. See WEAR_SCENARIOS.md and DAY4_STATUS.md; targeted physical gate closed.
- [x] D4-G consolidated automated/emulator validation and DAY4_REPORT.md.
- [x] D4-H narrow P9 presentation remediation and deterministic pre-fix reproduction.
- [x] Owner-reported P3 process restoration, P4 watch Wi-Fi/phone Bluetooth absent,
  P5 network recovery, P6 GPT-5.6 Luna, P7 rotary, P8 maximum font, P10 API separation.
- [x] D4-H fixed-APK P9: PASS — PHYSICAL AFTER REMEDIATION. Old account name/ID
  clears; Continue appears; phone OAuth, watch handoff, current presentation, models
  and real inference pass. Historical transient reauthorization anomaly unresolved
  and not reproduced. Day 4 targeted physical acceptance complete.

- Optional platform voice input deferred/cut from Day 4; no implementation started.
  Voice-specific permission/recognizer checks are outside the completed text-chat scope.
- [x] Targeted physical lifecycle/network/phone-independence checks P1–P5 and
  rotary/maximum-font usability P7/P8 accepted; no uninterrupted screen-off guarantee.
  Remaining release-specific evidence stays deferred to RC hardening.

Gate: reliable foreground text chat without voice; no hidden phone dependency
for inference/refresh after setup. Document intentional lifecycle limits.

## Day 5 — WristPrompt UX 2.0 + Markdown

**D5 COMPLETE — PASS WITH KNOWN DEBT.** Owner Galaxy Watch 7 retest R1–R10
closes physical revalidation with R6 structured table rendering as non-blocking
known debt. TalkBack remains untested, not required for closure; historical debt
remains open. DAY5_G_REPORT.md and DAY5_H_REPORT.md preserve initial and final evidence.
VERIFIED AUTOMATICALLY, VERIFIED ON EMULATOR and VERIFIED PHYSICALLY remain distinct.
D6 and D7 are NOT STARTED. This handoff implements neither phase.

- [x] D5-0: read-only UX/performance architecture audit and phase plan.
- [x] D5-A: chat-first shell, shallow retained navigation, static Wear theme,
  compact billing disclosure and reachable Ask/Send/Stop. PASS WITH KNOWN DEBT;
  358 Wear tests, debug/release builds, lint and isolation pass; 43/43 emulator
  launches, targeted routes/rotary/2x font pass. Native confirmation and memory
  comparison limits remain explicit in DAY5_STATUS.md; hardware deferred to D5-G.
- [x] D5-B: dense plain-text range/slice rows, replace transcript 80-character
  chunks, event-triggered 100 ms coalescing and explicit Latest. PASS WITH KNOWN
  DEBT — 394 Wear tests / 48 suites, 162 focused / 16 suites, debug/release/lint,
  isolation and 40 Python tests pass. Emulator long/Unicode/Latest/Stop/rotary/
  lifecycle/2x-font acceptance and bounded reset/memory sampling are recorded in
  DAY5_STATUS.md. Streaming frame variance and release profiling remain D5-F;
  physical UX remains D5-G. No Markdown or persistence.
- [x] D5-C: bounded native CommonMark/GFM table records, inert local link inspection,
  plain streaming current-answer tail and off-Main terminal reconciliation.
  PASS WITH KNOWN DEBT — 436 Wear tests / 49 suites; debug/release/lint/isolation,
  40 Python tests and 69/69 emulator launches pass. Targeted native rendering,
  horizontal code/rotary, Stop/Latest and 2x font acceptance recorded in
  DAY5_C_REPORT.md. Frame/PSS measurements remain bounded debug evidence;
  release profiling stays D5-F and physical Markdown UX stays D5-G.
- [x] D5-D: model/account/settings presentation. Model rows show ONLY discovered
  display name (raw ID when that is all the service supplies) and selected state.
  No speed/quality/reasoning/capability/description/ranking metadata.
  PASS WITH KNOWN DEBT — 441 Wear tests / 50 suites; builds/lint/isolation and
  40 Python tests pass. Focused Watch7 rotary/2x/1,000-model and secondary-route
  evidence in DAY5_D_REPORT.md; heap/accessibility consolidation stays D5-F,
  physical acceptance stays D5-G.
- [x] D5-E: pure error/loading/recovery presentation and explicit existing actions.
  PASS WITH KNOWN DEBT — 456 Wear tests / 51 suites; final copy subset 17 tests;
  debug/release/lint/isolation, 40 Python tests pass. 19 targeted Watch7 fixtures
  at normal/2x font, actual recovery taps and rotary/Stop/Latest evidence in
  DAY5_E_REPORT.md. No physical testing; D5-F/G remain separate.
- [x] D5-F: consolidated accessibility/performance/scenario acceptance. PASS WITH
  KNOWN DEBT — 459-test full run plus final 40/18-test focused components, builds,
  lint/isolation/Python pass; 77/77 emulator readiness, normal/2x interactions,
  30 rotary frame windows, 12 reset cycles and 4/4 process restarts. One narrow
  large-font recovery fix. DAY5_F_REPORT.md / DAY5_F_METRICS.json distinguish
  timing/memory evidence from spoken TalkBack and physical/release-device debt.
- [x] D5-G: physical acceptance closed after final owner revalidation, PASS WITH
  KNOWN DEBT. Initial failed observations remain in DAY5_G_REPORT.md.
- [x] D5-H: automated/emulator remediation complete; full Wear 470-test run plus
  final narrow checks, builds/lint/isolation and focused emulator evidence recorded
  in DAY5_H_REPORT.md; no new suite rerun for this handoff.
- [x] D5-H physical revalidation: owner R1–R5/R7–R10 PASS; R6 table presentation
  FAIL / KNOWN DEBT, non-blocking. Spoken TalkBack untested; no battery-life claim.

Gate: preserve inference/auth/account/billing/composer safety; verify automated
and emulator results separately from physical evidence. D5 is not release freeze.
No persistent conversation storage or new background infrastructure in D5.

## Day 6 — persistent conversations — implementation merged; physical acceptance pending

D6-A–C are merged on main. D6-D nonphysical acceptance is complete; owner
Galaxy Watch physical acceptance remains pending in #5. PR #21 remains open/draft.

- [ ] D6-A: FIRST task, read-only architecture audit and decision/implementation
  plan before broad production changes. Select storage, schema/versioning,
  transactions, compatibility ownership, migrations, recovery and cleanup.
- [ ] D6-B: approved durable conversations, completed exchanges and metadata;
  typed Text/ImageAttachment-ready content parts; atomic commits and tests.
- [ ] D6-C: history, New Chat, reopen/continue, rename/delete, restart restoration;
  pin/favorite optional only if trivial.
- [ ] D6-D: lifecycle/migration/recovery acceptance, separate emulator/physical evidence.

DISPLAY TRANSCRIPT != RESPONSES API REPLAY CONTEXT. Preserve validated opaque
continuation/reasoning items. No persisted Compose objects or per-token writes.
Image binaries belong in app-private files with durable metadata references,
not large base64 database rows. D6-A owns the exact schema/storage choice.
No image-input implementation in D6-A; no database selected in this handoff.

## Day 7 — companion productivity + multimodal input + final integration

D7-A phone presets are merged via PR #22 and VERIFIED AUTOMATICALLY; manual phone checks
remain pending in [docs/D7_A_PRESETS.md](docs/D7_A_PRESETS.md). D7-B sync/insertion is merged via PR #23; #7 remains open for paired/physical checks in
[docs/D7_B_PRESET_SYNC.md](docs/D7_B_PRESET_SYNC.md). D7-C imported photo library,
Asset synchronization and multi-selection are implemented with automated/synthetic
emulator evidence in [docs/D7_C_PHOTO_LIBRARY.md](docs/D7_C_PHOTO_LIBRARY.md).
#8 remains open for paired physical acceptance deferred to #12. D7-D image input and
durable attachment replay are implemented; see [docs/D7_D_IMAGE_INPUT.md](docs/D7_D_IMAGE_INPUT.md).
#9 stays open for live-route/physical acceptance deferred to #12. D7-E combined composer implementation/automated acceptance is complete; see
[docs/D7_E_COMPOSER.md](docs/D7_E_COMPOSER.md). #10 remains open for physical
acceptance deferred to #12. D7-F generation and bounded Wear viewer are implemented on the continuation branch; automated/emulator acceptance is complete. See [D7_F_IMAGE_GENERATION.md](docs/D7_F_IMAGE_GENERATION.md) for shared completion ownership, route limits and evidence. #11 remains open for intentionally deferred owner physical/live acceptance. PR #29, #1 and #12 are outside this continuation.

- [ ] D7-A: durable named custom USER prompt presets managed on phone (list,
  create/edit/rename/delete, multiline editor); no hidden always-on system prompt.
- [ ] D7-B: sync presets to watch; explicit insertion/editing in composer;
  non-empty draft uses insert/replace/cancel, never silent overwrite or auto-send.
- [ ] D7-C: multiple Android Photo Picker import, normalization/compression,
  app-private managed library and thumbnails; bounded Wear Data Layer asset/data
  sync, stale/replay protection, cleanup; watch multi-select/removal. No full
  remote phone-gallery browser or broad permission when Picker is sufficient.
- [ ] D7-D: existing Responses stack supports text + N images in one user message;
  explicit unsupported route/model error, no automatic switching/billing fallback.
- [ ] D7-E: presets + edits + text + multiple attachments + watch review/explicit Send.
- [ ] D7-F: image generation and fullscreen zoom/pan viewer — STRETCH, defer if core unstable.
- [ ] D7-G: final integration, physical Galaxy Watch acceptance and Monday alpha readiness.

Order: D6-A → B → C → D. D7-A and C may begin once D6-A establishes persistence
and attachment boundaries. D7-B depends on A; D7-D on C + attachment-capable D6-B;
D7-E on B + D; D7-G gates core integration. D7-F never blocks core readiness.
Monday target: 2026-11-02. Prioritize stability/persistence, custom prompts,
multi-image input, integration/release sanity, then image generation stretch.
This schedule is not implementation authorization in this documentation-only pass.
Use feature branches and issue-scoped PRs; never push implementation directly to main.
Document actual checks and separate emulator from owner physical evidence.

## Later phase — RC / release hardening and freeze — NOT STARTED

- [ ] Focused protocol/security regressions, both-app lint/build and release smoke.
- [ ] Manifests, exported components, diagnostics, backups, crypto, signing,
  dependency provenance/licenses, R8/shrinking/profile review.
- [ ] Review GPL-3.0-only LICENSE/public handoff, privacy policy, store assets/setup instructions,
  known limits and accurate Play Data safety declarations.
- [ ] Current Play/Wear requirements and delivery signing relationship.
- [ ] Physical release acceptance, remaining known-debt disposition and release notes.

Publishing/uploading/production signing/external releases require explicit
permission. Automated or debug-emulator acceptance does not close hardware gates.

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


## Day 2 Phase B — persistence verification (2026-10-28)

Authorized scope: restart persistence and watch-direct inference only. Baseline
Phase A persistent-toolchain evidence is in DAY2_BASELINE.md.

- [x] Trace protected registration/scopes/token/expiry/host storage and watch-direct transport.
- [x] Persist per-registration model preference and revalidate against a current catalog.
- [x] Add synthetic repository/file recreation, account isolation and failure-path tests.
- [x] VERIFIED PHYSICALLY: A — app restart retains same account/plan, requests no reauthentication and completes inference (user-confirmed 2026-10-28).
- [x] VERIFIED PHYSICALLY: B — authenticated state survives watch reboot and inference completes (user-confirmed 2026-10-28).
- [x] VERIFIED PHYSICALLY: C — phone fully powered off, watch on direct Wi-Fi; catalog loads and inference completes (user-confirmed 2026-10-28).
- [ ] PENDING END-OF-DAY PHYSICAL TEST: natural-expiry renewal without phone; separate from inference success.
- [ ] VERIFIED PHYSICALLY — PARTIAL: explicit model restoration; Terra changed to Sol after restart despite Terra remaining available. Real, non-blocking defect deferred to Day 3 model-picker/chat work.

**D2-1 overall PASS:** authentication/account persistence, watch reboot persistence,
phone-independent networking, direct model discovery and direct inference. Exact
host bytes were not physically compared; storage-abstraction stability remains
VERIFIED AUTOMATICALLY. Model restoration is separately PARTIAL and is not fully
verified. See PERSISTENCE_TESTING.md for evidence and DAY2_STATUS.md for the concise
end-of-day checklist. Testing policy lives in AGENTS.md: batch non-blocking checks,
never infer physical success from automation, and preserve safety/phase boundaries.
No BYOK, sign-out, refresh redesign or D2-2 implementation is authorized by this
status update.


## Day 2 Phase C — refresh/recovery audit (D2-2)

- [x] VERIFIED AUTOMATICALLY: current official contracts reviewed; existing rotating-grant state machine mapped.
- [x] VERIFIED AUTOMATICALLY: per-registration refresh locking, atomic latest-vault patching and stale-account protection.
- [x] VERIFIED AUTOMATICALLY: shared fakeable clock, definitely-unsent transport distinction, conservative uncertain outcomes and terminal/configuration failure matrix.
- [x] VERIFIED AUTOMATICALLY: numeric Unix-seconds / ISO hint handling checked against the official cookbook-linked Sign-in devkit; malformed values remain conservative. Focused upgrade regression physically RESOLVED; see REFRESH_REGRESSION_REVIEW.md.
- [ ] PENDING END-OF-DAY PHYSICAL TEST: natural renewal with same registration, no phone consent/new client and successful inference.

See REFRESH_REVIEW.md and DAY2_STATUS.md. No immediate device gate, credential
schema migration, real token manipulation, sign-out, BYOK or model-restoration fix.


## Day 2 Phase D — explicit sign-out and revocation

- [x] VERIFIED AUTOMATICALLY: authoritative discovery-backed revocation, bounded retries and honest confirmed/unconfirmed outcomes.
- [x] VERIFIED AUTOMATICALLY: durable sign-out gate, refresh/inference/catalog cancellation coordination, token clearing and metadata/host retention.
- [x] VERIFIED AUTOMATICALLY: same-client reauthorization without retained ID hint; idempotence, storage-failure and process-recreation cases.
- [x] VERIFIED PHYSICALLY: normal sign-out / intended-account reauthorization PASS WITH TRANSIENT REAUTHORIZATION ANOMALY. Exact post-reauth client suffix/remote result not supplied. Offline sign-out SKIPPED / NOT REQUIRED FOR DAY 2 PHYSICAL GATE.

See SIGN_OUT_REVIEW.md and the combined DAY2_STATUS.md checklist. No immediate
real sign-out or revocation was performed. No BYOK, Day 3 chat or model-debt fix.

## Day 2 Phase E — protected BYOK and explicit billing mode

VERIFIED AUTOMATICALLY: separate protected API-key vault, explicit persisted mode,
phone-to-watch versioned context-bound transfer and persistence ACK, API model-list
test/decoder, shared Responses stream and no automatic billing fallback. Scope
and test matrix: BYOK_REVIEW.md. Real API request, invalid-key/no-fallback and mode
restart checks are PENDING END-OF-DAY PHYSICAL TEST in DAY2_STATUS.md. No production
chat, provider, vision or Day 3 model-restoration work was included. Do not treat
API model listing alone as a completed inference or physical acceptance gate.


## Day 2 Phase F — connection and billing clarity

VERIFIED AUTOMATICALLY: explicit fixed active-mode indicator, separate account
and API-key settings, plan/API billing explanations, setup-only companion and
usage-management guidance/action. Existing per-mode model preferences are
validated against current catalogs; mode-switch regression coverage added.
162 Wear / 64 mobile tests, both debug builds, both lint tasks and git diff
whitespace checks pass. PENDING END-OF-DAY PHYSICAL TEST: consolidated connection,
transfer/usage and independent-billing acceptance in DAY2_STATUS.md.
No physical gate is checked off; Terra → Sol restoration, final chat UI and voice
remain deferred. This does not authorize Day 3 implementation.


## Day 2 Phase G — targeted failure matrix

VERIFIED AUTOMATICALLY: all eleven targeted cases have synthetic audit/test
coverage, with stale phone-result, expired retry, cross-context proof and current
plan-error diagnostic fixes. Full matrix/security findings: DAY2_FAILURE_MATRIX.md.
172 Wear / 69 mobile tests, both debug builds, both lint tasks and git diff
whitespace checks pass. Physical transfer/storage/recovery/billing acceptance is
PENDING END-OF-DAY PHYSICAL TEST in DAY2_STATUS.md; no physical gate is checked
off. Existing earliest_refresh_at interpretation remains BLOCKED. No Day 3 chat,
voice or model-restoration work is authorized by this phase.


## Final Day 2 evidence attribution

The earlier dated Phase C–G pending statements above are implementation handoff
history. Current owner-supplied results and evidence limits are authoritative in
DAY2_REPORT.md / DAY2_PHYSICAL_ACCEPTANCE.md. No repeat acceptance session or
Day 3 implementation is authorized by recording this stage result.

## D6-B implementation — 2026-10-31

Canonical SQLite checkpoints and completed-only durable replay are implemented on
the D6-B feature branch; see docs/D6_B_STORAGE.md for scope and evidence boundaries.
History/reopen UI remains D6-C. Consolidated lifecycle/physical acceptance is D6-D;
no D6 completion or physical PASS is claimed here.

## D6-C history implementation — 2026-10-31

Chats/New Chat/open/continue/rename/delete and inert startup restoration are
implemented on the contributor C branch. Automated gates and scoped phone-emulator
interaction evidence are recorded in docs/D6_C_HISTORY.md. Wear round/max-font/
rotary and owner physical results remain pending; do not promote phone screenshots
to Wear or physical acceptance. Next gate: D6-D consolidated persistence acceptance.
