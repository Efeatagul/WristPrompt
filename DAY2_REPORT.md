# Day 2 final evidence and release-gate review — 2026-10-29

## A. Day 2 status: PASS WITH KNOWN DEBT

The Day 2 stage gate is accepted using owner-supplied physical evidence plus the
current complete automated suites. This is not v0.1 release readiness and does
not start Day 3. Successful paid BYOK cannot be physically verified without a
funded API credential; the owner explicitly accepts strong automated success-path
evidence plus physical invalid-key/no-fallback evidence for this stage.

No product code or architecture decision changed during this final review. One
synthetic phone recovery regression test was added. Prior uncommitted refresh-fix
work was preserved. No device action, live request, sign-out, credential change,
commit, push, publication or Day 3 implementation was performed by the agent.

## B. Physical evidence — owner report, not agent device execution

Devices: Samsung Galaxy Watch 7 and Samsung Galaxy Note 10+. Latest Day 2 debug
builds installed in place with the same signing identity; app data preserved.
Earlier records mentioning a different phone describe earlier sessions, not this
final acceptance pair. Exact OS versions/build hashes were not supplied here.

| Test | Result / evidence boundary |
| --- | --- |
| D2-1 authentication persistence, watch reboot and phone-off Wi-Fi discovery/inference | PASS — VERIFIED PHYSICALLY, prior accepted results retained; no repeat needed |
| Upgrade/REFRESH_TIMING_UNDOCUMENTED regression | PASS — VERIFIED PHYSICALLY RESOLVED after focused fix; same real registration/account, no destructive reauthentication, plan mode retained, models usable, streamed WristPrompt works., response.completed and exact output |
| ChatGPT-plan inference | PASS — VERIFIED PHYSICALLY; connected identity, explicit plan mode, account model selection, GPT-6.1 Sol, streamed exact output and response.completed |
| Normal sign-out and same-intended-account recovery | PASS WITH TRANSIENT REAUTHORIZATION ANOMALY — VERIFIED PHYSICALLY; first exchange failure and an expired setup context, then successful recovery after force-restart/reopen and retry |
| Exact issued-client reuse specifically after sign-out | VERIFIED AUTOMATICALLY; the final physical report confirms intended account, not an independent before/after client-ID comparison for this particular cycle |
| Remote revocation outcome during normal sign-out | NOT REPORTED; no physical CONFIRMED/UNCONFIRMED value is invented from eventual login success |
| Fake-key handoff/configuration and API authentication rejection | PASS — VERIFIED PHYSICALLY; explicitly fake credential, separate API mode/billing, HTTP 401 and explicit API-authentication rejection |
| API failure -> ChatGPT no-fallback | PASS — VERIFIED PHYSICALLY for tested failure direction; UI said ChatGPT was not used, no ChatGPT response, no automatic switch |
| Switch back to ChatGPT after API failure | PASS — VERIFIED PHYSICALLY; explicit plan selection restored known-good completed ChatGPT inference |
| Successful real paid BYOK inference | BLOCKED — no funded OpenAI API credential available for physical verification |
| Mode/state persistence | Switching and ordinary app restart were exercised physically; no per-mode restart matrix or paid API success is inferred beyond the reported observations |
| Developer cancellation and subsequent authentication usability | PASS — VERIFIED PHYSICALLY; running inference stopped, “Stopped. Partial output is not a completed proof.”; ChatGPT path remained usable afterward |
| Offline sign-out | SKIPPED / NOT REQUIRED FOR DAY 2 PHYSICAL GATE; automated evidence remains separate |
| Detailed natural-refresh telemetry / exact renewal time / token tuple | NOT OBSERVED in supplied evidence; physical usability regression resolved, no additional refresh telemetry claimed |
| Round-screen layout | KNOWN DEFECT / TECHNICAL DEBT — overlapping/clipped/dense diagnostics; functions and billing state were usable in reported tests, no reported security-critical state ambiguity |

Fake-key storage means configured data, not a valid/funded API credential. No
purchase, real API key, quota exhaustion or extra physical sign-out is requested.

## C. Reauthorization anomaly

Observed sequence, without normalization into a clean pass:
1. Normal sign-out was exercised.
2. Watch reported authorization failure on phone. Phone reported “OpenAI could
   not exchange the code. Check your network and retry from your watch.”
3. Another setup context/attempt expired; connection did not immediately recover.
4. Force-restarting/reopening WristPrompt and retrying eventually connected the
   same intended account, enabled plan use, loaded models and completed inference.

**Exact root cause is UNRESOLVED.** AuthProblem.EXCHANGE is deliberately broad:
OpenAiAuthorization.request/exchange maps network failure, non-200 HTTP, timeout,
oversize/invalid response and token decoding to this safe category. The evidence
has no HTTP status, exchange stage/timing or callback correlation. It does not
prove a duplicate code, stale transaction, provider outage or lifecycle failure.

Plausible areas audited:
- PhoneAuthViewModel.start obtains a fresh current watch context; constructs a
  new OAuthTransaction with fresh PKCE/state/nonce and a bound loopback listener.
- Returning context binds the saved issued client/issuer/subject; sign-out removes
  the retained ID hint, not registration identity. OpenAiAuthorization and
  IdTokenVerifier revalidate returned identity before protected handoff.
- A new watch request cancels the prior phone job/envelope/expiry job. Explicit
  current-request and cancellation checks reject late exchange/ACK publication.
- LoopbackListener consumes one callback; the surrounding use closes it. Code
  exchange is not automatically retried. Retry transfer resends the same validated
  envelope and does not exchange the authorization code again.
- Initial bridge metadata is fresh for two minutes; browser wait is bounded at
  five minutes; watch OAuth transaction/handoff is bounded at ten minutes. A
  subsequent attempt using an old phone setup request can fail freshness even
  when a different watch pending lifetime has not elapsed. This bounded lifetime
  distinction is a plausible recovery friction point, not a proven cause here.
- ViewModel survives ordinary activity recreation; phone process death drops
  memory-only OAuth/pending-token state. Old browser tabs cannot reconstruct that
  state. Reopening/fresh watch setup is a known conservative recovery path.
- Watch prepare/accept rejects sign-out-pending, wrong node/request/host,
  mismatched registration, changed generation and expired results.

No concrete unsafe transition or deterministic cause was found. No speculative
product fix or broad auth redesign was made. New test
PhoneAuthViewModelTest.exchangeFailureThenFreshWatchRequestRecoversWithNewOAuthTransaction
injects one exchange failure, then a fresh request and successful handoff. It
checks fresh state/nonce/verifier, different synthetic codes, retained client
identity, no ID hint after sign-out-style context, no failed-attempt transfer and
only the new request acknowledged. Existing superseded-result and expiry tests
remain. This tests bounded recovery, not the real anomaly's cause.

Classification: Medium reliability/diagnostic debt for Day 3 triage and Day 5
release hardening. It does not block Day 3: final recovery succeeded and no unsafe
state was established. Before v0.1 freeze, triage safe stage/status diagnostics
and disposition the issue; recurrent failure or credential/identity inconsistency
would block release. No additional physical sign-out cycle is required now.

## D. Refresh status

The blanket opaque-hint expiry rejection was a real D2-2 regression. The focused
fix interprets numeric Unix seconds and ISO date strings, honors future constraints,
keeps missing/null hints compatible and malformed hints conservative. It changes
no encrypted schema, registration, billing selection or rotation durability.

**VERIFIED PHYSICALLY — RESOLVED:** focused in-place retest restored model loading
and completed exact plan inference with the same registration and no destructive
reauthorization. This closes the specific usability blocker. It does not claim
an observed rotation timestamp, renewal banner, exact token metadata or an
independently repeated phone-off refresh test.

VERIFIED AUTOMATICALLY: legacy reconstruction, safe access until expiry, due/future
constraints, precision, malformed timing, per-client serialization, atomic tuple
replacement, cancellation, sent/unsent failures, conservative uncertain outcomes
and recreation/replay protection. Unknown unsupported hint representations remain
fail-closed at expired access. REFRESH_REGRESSION_REVIEW.md owns detailed history.

## E–I. Path and operation status

- ChatGPT plan: PASS physically and automatically, including successful Sol proof.
- BYOK architecture/success path: VERIFIED AUTOMATICALLY with fake credentials,
  fake HTTP and deterministic streamed completion; separate protected store,
  same public Responses transport, mode-bearing leases and model validation.
- Invalid-key physical handling and API -> ChatGPT no-fallback: PASS.
- Real successful BYOK inference: **BLOCKED — no funded OpenAI API credential
  available for physical verification**. External test constraint, not code failure.
- Billing invariants: both directions VERIFIED AUTOMATICALLY; physical PASS is
  specifically the tested API-failure direction plus explicit return to plan.
- Sign-out/reauthorization: PASS WITH TRANSIENT REAUTHORIZATION ANOMALY. Remote
  revocation result and exact reauth client suffix are not supplied physical facts.
- Cancellation: physical semantic PASS with continued auth usability; actual
  OkHttp cancellation and late-event rejection separately VERIFIED AUTOMATICALLY.

## Auth / concurrency / storage audit

| Boundary or race | Current protection and evidence |
| --- | --- |
| Issued client/account/host | Fresh PKCE/state/nonce; callback client equality; signed OIDC issuer/audience/subject/nonce checks; full registration equality before replacement; UUID host DataStore continuity; equal emails never merge registrations |
| Refresh and inference | Per-client refresh mutex; current bearer/profile and generation checks; renewed bearer triggers fresh catalog; stale lease/output is rejected; WatchRefreshTest and InferenceSessionTest |
| Refresh and sign-out | Durable sign-out generation/gate before cancellation; wait on same refresh lock outside vault lock; revoke latest known tuple; uncertain replacement cannot become confirmed revocation; WatchSignOutTest |
| Sign-out and inference | Block new leases; cancel session operations; generation/token checks reject late events; no completed result fabricated |
| Sign-out and immediate reauth | Prepare blocked while cleanup pending; local tuple clear retains identity/client/host; new OAuth removes signed-out result only after bound valid transfer |
| Old OAuth and new OAuth | New request cancels old phone operation; request/cancellation checks before publish; one loopback callback per listener; no automatic code-exchange replay; superseded-result/recovery tests |
| Callback and stale/expired watch request | Watch pending node/request/host/identity/generation/expiry check is authoritative; phone timeout/expiry does not claim watch success without ACK |
| Model discovery and account/mode switch | InferenceViewModel epoch clears old state; InferenceSession rejects changed client/generation/mode/revision even with identical model slug; catalog revalidated against selected credential |
| API setup and mode switch | Explicit watch mode commit invalidates pending API setup; transfer acceptance requires current bound pending context; stale transfer fails rather than silently configuring/activating another mode |
| Cancellation and Responses terminals | Underlying call cancel closes/unblocks reads; epoch and current-lease checks reject late updates; only documented completed event succeeds; failed/incomplete/EOF/partial remain distinct |
| Handoff lost ACK / replay | Durable encrypted persistence before ACK; identical transfer/digest idempotent without reinstalling old tokens; expired/modified/new-context payload rejected; phone ACK required for success |
| Interrupted sign-out | Durable intent; reconstruction clears local secrets and reports remote unconfirmed instead of resurrecting a usable session |
| Protected-store failures | AES-GCM/Keystore, randomized IV and AAD, AtomicFile ciphertext/readback; no plaintext fallback; fake read/write/replace/invalidated-key tests; actual destructive key-loss testing excluded |
| Backups/logging | allowBackup=false; explicit cloud/device-transfer exclusions; noBackupFilesDir credentials; phone persists only encrypted issued-client metadata and non-secret request metadata; enum-only BridgeLog |

Manual DI has one Application repository per process. There is no declared
separate android:process; multi-process writers would require another audit.
Encryption/byte wiping does not promise forensic erasure of managed strings,
IME memory or compromised-device memory. Physical storage/backup fault injection
was not performed; stage acceptance does not convert those into physical PASS.

## OpenAI contract comparison

Current official sources rechecked 2026-10-29:
[registration](https://developers.openai.com/siwc/token-sharing-open-source/sign-in),
[accounts/sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions),
[token reference](https://developers.openai.com/siwc/token-sharing-open-source/token-reference),
[errors/recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery),
[models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference).
Refresh representation evidence remains the first-party Sign-in devkit linked
by the official cookbook, recorded in REFRESH_REGRESSION_REVIEW.md; no Codex
internal endpoint or inferred registration mechanism was substituted.

Implementation retains issued-client binding, stable host, scope gating, exact
loopback redirect, trusted OIDC validation, public refresh/resource form and
serialized atomic rotating replacements. Revocation is trusted-discovery-backed,
bounded, local cleanup is explicit, and unconfirmed remote outcome stays honest.
Direct 401/403 admission alone does not erase a session; terminal refresh codes
are distinct. Temporary availability errors preserve credentials. Plan errors
never route to API billing, infer reset timestamps or replay a generation.
Responses uses store=false/stream=true, public /v1/responses and current models;
only response.completed establishes success. The current proof is not production
multi-turn chat. Unsupported-capability error.param is not retained in the current
safe diagnostic state; category/code/status/request-ID and manual recovery remain
available. Diagnostic specificity is a low-priority support debt, not fallback.

## J. Security findings / severity

- Critical: none found in reviewed code/tests and targeted searches.
- High: none found; no credential crossover, silent bill change, plaintext secret
  persistence, unsafe TLS override, secret logger or token backup path identified.
- Medium: unexplained transient reauthorization reliability/diagnostic anomaly;
  no demonstrated security exploit or unsafe transition. Track before freeze.
- Low: dense/clipped Wear diagnostics and broad safe error categories hinder
  diagnosis. UI is Day 3 work and becomes release-blocking if identity, billing,
  sign-out or Stop cannot be understood/used reliably. Current physical evidence
  shows the tested billing states/actions were recognizable.

Security/static search scanned main and test Kotlin/XML for printing/stack traces,
HTTP logging, persistence/saved state, TLS overrides and live-looking secret
literals. Output contained only paths/line numbers. Only enum-only BridgeLog's
Log.w matched logging; test-only synthetic file writes and production ciphertext
write/host/non-secret inbox updates explain persistence matches. No TLS-bypass or
live-looking key/JWT/private-key literal matched. Git tracked/stageable path scan
found no private signing/encrypted/local configuration artifact. No secret values
or actual auth/device records were printed. This is not binary/OEM forensics.

## K. Automated validation

Run with the existing persistent JDK/SDK/cache environment:

```sh
JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9 \
ANDROID_HOME=$HOME/Android/Sdk \
ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android \
GRADLE_USER_HOME=$HOME/.gradle \
./gradlew :wear:testDebugUnitTest --rerun :mobile:testDebugUnitTest --rerun :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug
git diff --check
```

VERIFIED AUTOMATICALLY: BUILD SUCCESSFUL; **178 Wear / 70 mobile tests**, zero
failures/errors/skips. Both complete unit tasks executed explicitly. Both debug
build tasks and both lint tasks pass; zero lint errors. Wear reports four warning
instances in three prior categories (two AGP version advisories, exported service,
vector path); mobile retains three prior categories. Existing SDK Platform-Tools/
license warning remains; no SDK/license/dependency/signing setting was changed.
A prior review run also passed the new phone test. git diff --check passes.
Both APKs were independently verified again with the existing Build Tools
apksigner verify --print-certs command and retain SHA-256
fd8491b6238368a04d58bd0653c35ff54a3a17fc9b31e53a6af7110579d37061.
The verifier emitted a non-fatal Java native-access advisory; verification passed.
Product-source and DECISIONS.md hash comparison verifies no changes by this
review; earlier uncommitted refresh-fix changes are preserved. No architecture
choice changed, so DECISIONS.md is intentionally not edited here.

Changed in this final review: mobile PhoneAuthViewModelTest.kt only in test code;
DAY2_PHYSICAL_ACCEPTANCE.md, DAY2_STATUS.md, DAY2_REPORT.md, ROADMAP.md,
SIGN_OUT_REVIEW.md, REFRESH_REGRESSION_REVIEW.md, REFRESH_REVIEW.md,
DAY2_FAILURE_MATRIX.md and BYOK_REVIEW.md for current evidence attribution.

## L. Release blockers

No remaining demonstrated Day 2 critical/high auth or billing-safety defect.
The physical refresh regression blocker is closed. Missing funded API credentials
are an external verification constraint accepted for Day 2, not a code failure.
No new physical sign-out cycle is requested by this review.

v0.1 is NOT release-ready: native chat/local-context work is still unimplemented;
Wear layout must be corrected and checked for critical-state readability; model
restoration remains defective; reauth anomaly requires triage/disposition before
freeze; later release-build/privacy/backup/platform edge checks remain scheduled.
No paid API physical success, exact refresh telemetry or remote revocation
confirmation is manufactured to close those evidence gaps.

## M. Debt carried forward

1. Terra -> Sol explicit model restoration: prior physical PARTIAL, not fixed or
   disproved by successful Sol use; Day 3 model-picker work.
2. Real Wear overlapping/clipped/dense diagnostics: observed, Day 3 first priority;
   preserve clear account/billing/cancellation state while reducing density.
3. Transient sign-out -> reauthorization exchange failure/context expiry:
   unresolved cause; bounded safe recovery demonstrated, new synthetic recovery
   coverage. Day 3 triage, Day 5 hardening/disposition; do not claim a clean pass.
4. Support diagnostics: broad phone exchange category, no structured error.param;
   improve only sanitized stage/status information if authorized later.
5. Physical evidence limits: exact per-mode restart matrix, remote revocation
   result, detailed natural-refresh timing and destructive storage/Keystore faults
   were not established by this session. No repeat is demanded now.

## N. Exact next Day 3 order — plan only

1. Fix round-screen spacing/clipping/text density first; make account, active
   billing path and primary actions readable, keep developer details secondary.
2. Diagnose/fix Terra -> Sol restoration; verify independent mode/account model
   preferences against fresh catalogs and runtime recreation.
3. Build native watch text input and minimal in-memory multi-turn conversation
   state, supplying context with store=false; phone remains setup-only.
4. Connect existing streaming transport and honest Stop/partial/failed/incomplete/
   completed states to that chat; prevent late generation updates.
5. Integrate model/account/explicit-mode transitions with safe conversation-state
   boundaries and no silent billing fallback; retain Manage usage/error guidance.
6. Run focused and full automated checks, then one combined meaningful end-of-day
   physical chat/rotary/input/cancellation session. Triage the reauth anomaly with
   safe evidence if encountered; no automatic extra sign-out cycle or API purchase.

Do not start any of this until Day 3 work is explicitly authorized. Voice,
providers, publication and broad auth redesign are not part of this review.

## O. Suggested commit

`test: record Day 2 physical acceptance and auth gate review`
