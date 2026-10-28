# Day 2 Phase G — targeted authentication/inference failure matrix

## Final evidence update — 2026-10-29

Final owner evidence now verifies ChatGPT success, API 401/no-fallback and cancellation, with a transient reauth anomaly. Successful real BYOK remains externally BLOCKED. Original pending rows below are historical; see DAY2_REPORT.md.


Scope: failure handling and regression tests only, 2026-10-28. Prior Day 2 work
remains preserved. No provider, production chat, voice, credential migration,
signing change, external service change, real credential action, commit or push.

## Evidence and limits

PASS and FIXED below mean VERIFIED AUTOMATICALLY using synthetic credentials,
fake transport/stores, coroutine scheduling, a fake phone retry clock and existing
JVM loopback tests. They do not establish Android Keystore, AtomicFile/filesystem
or paired Data Layer behavior on the Galaxy Watch. Physical items are consolidated
in DAY2_STATUS.md. No paid requests, quota exhaustion or device operations ran.

Read AGENTS.md, PROJECT_SPEC.md, ROADMAP.md, DECISIONS.md, DAY1_REPORT.md,
DAY1_REVIEW.md, DAY2_STATUS.md and current auth/bridge/storage/inference code/tests.

Official OpenAI documentation rechecked:
- [Errors and recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
- [Models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)

## Failure matrix

| Case | Result | Automated evidence / remaining boundary |
| --- | --- | --- |
| 1. Lost credential ACK | PASS; FIXED late/expired phone result state | WatchProfilesTest lost-ACK/recreation/payload-change tests; WatchRefreshTest duplicate cannot roll back rotated tokens; PhoneAuthViewModelTest retry uses same transfer and never claims success without ACK; ApiKeyRepositoryTest and ProtectedFailureMatrixTest uncertain commit retries do not rewrite. Old deliveries cannot replace newer credentials. Paired lost-ACK behavior PENDING END-OF-DAY. |
| 2. Disconnect during setup | PASS; FIXED expired retry result | WatchAuthBridgeTest virtual bounded receipt timeout; PhoneAuthViewModelTest disconnected setup/expired transfer; PhoneApiKeyTest timeout/retry/expiry. WatchProfiles preserves the previous credential after a failed new setup. Phone stores no plaintext recovery envelope. Actual disconnect/reconnect PENDING END-OF-DAY. |
| 3. Inference cancellation | PASS | OpenAiProviderTest cancels the underlying OkHttp call and unblocks a fake read; InferenceViewModelTest rejects late completion, keeps partial text incomplete and permits explicit catalog retry. No credential mutation or final Stop UI added. Hardware interaction PENDING END-OF-DAY. |
| 4. Stale account/auth result | FIXED | PhoneAuthViewModel now checks cancellation/current request before accepting context, exchanging-result publication or ACK success. Verifies returned client/host before transfer. Phone OAuth/API tests include non-cooperative late ACK/failure. Existing WatchProfiles tests bind client/issuer/subject, invalidate pending setup and keep equal-email registrations separate. |
| 5. Stale model catalog | FIXED | New InferenceViewModelTest late discovery across account and mode changes cannot replace the current catalog. FailureMatrixTest rejects a stale prepared catalog before rediscovery/POST even when the same model ID exists in both contexts. Existing per-mode preference test checks membership and independent restoration. |
| 6. Protected storage failure | PASS | ProtectedFailureMatrixTest injects encrypted read failure, write failure, staged replace failure, post-commit uncertainty, missing/wrong key, InvalidKeyException (simulated invalidation) and KeyStoreException (simulated unavailability). Both stores fail closed with no ACK; failed replace keeps previous ciphertext. Repository-owned plaintext byte arrays are wiped. Actual Android platform behavior remains PENDING END-OF-DAY/disposable test evidence. |
| 7. Key loss | PASS in simulation | Both repositories load unavailable, issue no lease and perform no reset/write under missing/wrong/unavailable key faults. Static UI now says protected credentials are unavailable, unrecoverable here, and require reauthorization/reconfiguration once storage is available. No real key was invalidated or deleted. |
| 8. ChatGPT plan errors | FIXED | FailureMatrixTest exercises all seven requested subscription_sharing codes plus both documented chatpass permission codes, before streaming and as response.failed. Keeps safe status/code/shape/request ID; provider messages are excluded. No automatic retry, billing fallback, usage estimate or reset time. Usage-limit action remains Manage usage. |
| 9. Admission 401 / 403 / 503 | PASS; FIXED route categorization | Direct detail-shaped 401/403 remain permission/admission failures, not proof of revoked refresh credentials. 503 remains temporary. invalid_user requires diagnosis; terminal refresh codes remain the separate reauthorization path. route_not_supported maps to configuration and unsupported_capability to invalid request. Temporary failures preserve both credential records. |
| 10. Responses terminal state | PASS | SseReaderTest, OpenAiProviderTest and InferenceSessionTest cover completed, failed, incomplete, malformed/unterminated stream, DONE and EOF. Partial text alone never succeeds. HTTP 200 for response.failed remains HTTP 200 in diagnostics; no synthetic 4xx/5xx status is invented from the code. |
| 11. Billing-mode invariant | FIXED stale-action gap; PASS regression matrix | CredentialRouterTest invokes failures under both configured credentials and both explicitly selected modes, checks only the selected lease is used and both protected records/mode remain unchanged. Stale account/mode/revision/generation proof actions now fail before POST rather than being retargeted to another credential. |

## Code fixes

- `mobile/.../auth/PhoneAuthViewModel.kt`: current-request/cancellation guards,
  returned-client/host validation, injectable clock, captured expiry and honest
  expired-retry failure. Existing encrypted issued-client store and OAuth contract
  are unchanged.
- `mobile/.../auth/PhoneApiKeyViewModel.kt`: cancelled late timeout/exception cannot
  overwrite a newer setup state. Existing bounded envelope remains memory-only.
- `mobile/.../MainActivity.kt`: names the exchange argument for clock injection.
- `wear/.../openai/InferenceSession.kt`: reject changed account, generation, mode
  or mode revision before using an old catalog. Same-context token refresh still
  rediscovers with the new bearer and validates model membership.
- `wear/.../openai/ApiFailure.kt`: explicit current error categories; safe response
  context helper. `OpenAiProvider.kt` attaches actual HTTP status/request ID to
  streamed failures. `InferenceViewModel.kt` retains safe code/shape/request ID in
  failure state, clearing them on a fresh operation; no raw provider body retained.
- `wear/src/main/res/values/strings.xml`: honest protected-storage/key-loss and
  generic configuration failure wording, without promising recovery of lost keys.

No credential schema, encryption primitive, Android key alias, transfer format,
OAuth endpoint/parameter, model-restoration behavior or billing-selection policy
was changed.

## Tests added (15 test methods)

Wear (10):
- `openai/FailureMatrixTest.kt` (new): plan codes before/during streams, admission
  status distinctions/redaction, stale catalog cannot retarget requests.
- `auth/ProtectedFailureMatrixTest.kt` (new): replacement/read/key fault matrix,
  uncertain API commit/idempotent ACK/new-key replay, failed setup/newer plan.
- `openai/InferenceViewModelTest.kt`: non-cooperative late catalog and explicit
  recovery after cancellation.
- `openai/CredentialRouterTest.kt`: both-billing-path error preservation matrix.

Mobile (5):
- `auth/PhoneAuthViewModelTest.kt`: superseded exchange/ACK, disconnect failure,
  expired lost-ACK retry, mismatched exchanged client rejection.
- `auth/PhoneApiKeyTest.kt`: superseded late ACK and exception.

Existing terminal, transport-cancellation, identity/JWT/scope, refresh, sign-out,
replay and encryption tests were rerun with the complete app suites. Storage
fault tests operate at SecretRecord's persistence boundary with real JVM AES-GCM;
they do not claim to exercise Android's AtomicFile or hardware key invalidation.
The first expanded run caught an overly strict test assertion about a coroutine's
sanitized exception wrapper; assertions now inspect the safe cause chain. The
replacement test also explicitly closes the failed pending setup before proving
that the previous plan credential remains usable.

## Security search

Searched all main Kotlin/XML source in wear, mobile and shared (a superset of
changed source), emitting filenames/line locations only for matches:
- logging/printing/stack traces/HttpLoggingInterceptor;
- plaintext file/prefs writes, saved-state fields and DataStore usage;
- Authorization/Bearer construction and all bridge diagnostic calls.

Only BridgeLog's allowlisted `Log.w` exists; it accepts BridgeDiagnostic, with no
arbitrary text/payload/Throwable overload. No println, printStackTrace, HTTP
logging interceptor, raw auth request/response logger or plaintext secret
persistence was found. Authorization headers are constructed only for trusted
HTTPS transport. AtomicFile receives encrypted bytes; DataStore stores opaque
host/setup metadata. Protocol DTOs/credential leases and parser errors remain
redacted/sanitized. No discovered secret values were printed. This is source and
synthetic-test evidence, not a claim of runtime/OEM forensic verification.

## Validation

Established persistent JDK/SDK/cache environment from BYOK_REVIEW.md:

```sh
JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9 \
ANDROID_HOME=$HOME/Android/Sdk \
ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android \
GRADLE_USER_HOME=$HOME/.gradle \
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:lintDebug :mobile:lintDebug :wear:assembleDebug :mobile:assembleDebug
git diff --check
```

VERIFIED AUTOMATICALLY: BUILD SUCCESSFUL; 172 Wear and 69 mobile tests, zero
failures/errors/skips. Both debug builds and both lint tasks pass; zero lint
errors and unchanged three warning categories per app. git diff --check passes.
The existing SDK Platform-Tools/license warning remains; no SDK, license,
dependency or signing configuration was changed. No emulator or physical test
was run. DAY2_STATUS.md records the consolidated handoff.

## Release limitations

No newly found Phase G defect is intentionally left unresolved. Paired transfer,
physical Keystore, natural refresh and real independent billing acceptance remain
PENDING END-OF-DAY PHYSICAL TEST; they are not replaced by unit/build evidence.
The previously documented unknown earliest_refresh_at encoding remains BLOCKED
pending authoritative specification. No timestamp interpretation was guessed.
Terra → Sol model restoration remains Day 3 debt. No immediate hardware test is
needed to evaluate these Phase G fixes; no real session/key/data was modified.

Suggested commit: `fix: close Day 2 auth and inference failure matrix`.
