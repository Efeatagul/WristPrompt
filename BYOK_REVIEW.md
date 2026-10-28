# Day 2 Phase E — explicit OpenAI API-key mode

## Final evidence update — 2026-10-29

VERIFIED PHYSICALLY: fake-key handoff/configuration, explicit separate API billing, HTTP 401 and API -> ChatGPT no-fallback; explicit return to plan succeeds. Fake configuration is not a valid key. Successful real BYOK: BLOCKED — no funded OpenAI API credential available for physical verification. This is an external constraint, not code failure. Automated successful-BYOK evidence remains required; see DAY2_REPORT.md. No purchase requested.


Implemented 2026-10-28. This phase adds protected BYOK setup and the fixed proof
flow, not production chat. Physical BYOK results remain PENDING END-OF-DAY
PHYSICAL TEST in DAY2_STATUS.md. No real key was used by automated tests, and no
real credential/device was changed by the agent.

## Current official contract

Reviewed against current official OpenAI documentation:

- [List models](https://developers.openai.com/api/reference/resources/models/methods/list):
  bearer-authenticated GET /v1/models, data[].id. The API response does not promise
  Responses/text support for every returned model. Do not invent capability flags.
- [Text generation](https://developers.openai.com/api/docs/guides/text) and
  [streaming Responses](https://developers.openai.com/api/docs/guides/streaming-responses):
  reuse POST /v1/responses, stream=true, store=false, the existing SSE parser and
  response.completed success requirement. Failed/incomplete/interrupted is not success.
- [Error codes](https://developers.openai.com/api/docs/guides/error-codes):
  401 is API authentication/access rejection; 429 may mean rate or quota/credit
  limits; server/network failures do not change credential mode.
- [Production key safety](https://developers.openai.com/api/docs/guides/production-best-practices):
  keys should be protected outside exposed client code. User-owned local BYOK is
  an explicit advanced backend-free tradeoff, not server-side key isolation.

The large Responses create-reference page could not be fetched by the docs tool;
its official text/streaming guides above were used. No OAuth endpoint, registration,
refresh contract or existing credential record format is changed in this phase.

## Architecture and storage

CredentialRouter selects exactly CHATGPT_PLAN or OPENAI_API_KEY from the persisted
mode, never from whichever credential happens to exist. Existing installations
start in CHATGPT_PLAN, preserving the previous billing path. Only the watch mode
buttons persist a different selection. CredentialLease carries mode and revision;
callers reject leases from an older mode, even after switching away and back.
Errors, sign-out, receipt of a key and key removal do not select a different mode.

ChatGPT profiles remain in the existing chatgpt_profiles encrypted record. API key,
mode, API-specific model preference and bounded transfer context live separately
in api_key.enc, using EncryptedRecord's Android Keystore AES-256-GCM and AtomicFile,
durability/readback verification, no-backup directory and distinct key/AAD alias.
Only metadata reaches UI StateFlow. OAuth storage/signing/host ID is not migrated.
API deletion atomically writes a keyless record, invalidates its generation and
clears pending/accepted transfers. It leaves API mode selected, showing API key
not configured. It cannot revoke/delete OAuth. ChatGPT sign-out cannot delete the
API key or switch billing. A storage failure blocks inference and must not be
reported as successful deletion; encrypted old bytes may remain until a write
succeeds. This is logical protected-secret deletion, not a claim of flash erasure
or revocation of the user's OpenAI API key.

The phone masks entry, uses password keyboard settings and FLAG_SECURE, and has
no key persistence/SavedStateHandle/clipboard/backend/logging path. Format sanity
is only 8–8192 printable non-space ASCII characters with a bounded encoded envelope;
it does not prove validity. ACK, expiry, context replacement or ViewModel disposal
clears unnecessary key/envelope state. Retry retains the same in-memory envelope
only for the short setup lifetime. Arrays are wiped when released. Kotlin/Compose
Strings, IME memory, OS compromise and live process memory cannot be reliably
zeroized; local encryption does not eliminate client-side exposure.

## Transfer and concurrency

Distinct v1 /api-key/start and /api-key/transfer message paths reuse Data Layer's
matching package/certificate trust and existing eligible-peer discovery. Setup is
explicitly requested on the watch for its stable host, random request and selected
phone node. Phone entry requires that eligible watch's fresh context. Transfer
is canonical, at most 12 KiB, version/UUID/key bounded, and must match the watch's
saved node/request/host and two-minute lifetime. Unsolicited, mismatched, stale or
modified replay payloads fail without raw data or parser causes.

Secure persistence precedes ACK. An exact lost-ACK retry uses its saved digest and
receipt without rewriting the key; a changed transfer or deleted context fails.
Phone accepts only matching request/transfer ACK, retaining the same bounded retry
on a lost/bad ACK and clearing entry after success. Receipt and storage alone
never activate API billing. This bounds retries; it is not permanent cross-device
revocation/replay tracking. No new Data Layer logging channel was added.

Mode changes cancel active proof work; API removal/replacement cancels API work
only. All catalog, inference progress and terminal results check lease generation
and mode revision. Late results cannot publish after removal/switch. ChatGPT
refresh/sign-out retain their existing per-registration coordination. API leases
never read/refresh OAuth. Independent encrypted-record mutexes never span network
requests, cancellation or each other.

## API behavior and limits

The existing OpenAiHttp/OpenAiProvider/Responses transport is reused. ChatGPT's
proven account-specific catalog decoder is preserved; API catalog uses data[].id
with IDs as labels, no invented capabilities. The watch offers a real Test API key
/ reload API models action. A successful GET establishes model-list access only,
not generation permission/quota for every ID. Empty lists and unsupported model
errors are safe failures; there is no hard-coded list of supposedly supported IDs.
API catalog is bounded by the shared 256 KiB response limit and 1000 model IDs.

API Responses uses only that API key's bearer credential, the selected model and
fixed prompt `Reply exactly: BYOK path works.`. API 401, 429, permission/model,
network, server, malformed and terminal-stream failures stay in API mode, with
static sanitized diagnostics. There are no automatic billable retries or billing
fallback. HTTPS endpoints are fixed to OpenAI; redirects, payload logging and
HTTP credential interceptors remain disabled.

## Automated evidence

Synthetic fixtures are intentionally named synthetic-user-key or clearly-fake-placeholder,
not live-looking keys. Tests cover AES-GCM persistence/recreation, explicit mode
and model persistence, removal/replay invalidation, atomic ACK ordering/storage
failure, corrupt/tampered storage, node/request/host/expiry validation, exact ACK
retry, phone clearing/retry/timeout, storage separation and ChatGPT sign-out.
Router cases cover no fallback in either direction and old mode-lease rejection.
Provider cases cover bearer/request shape, API/plan catalog separation, 401/429/5xx/
network and unsupported models, shared streaming completion and unsuccessful
terminal/interrupted outcomes. Cancellation tests cover discovery and late
inference output after deletion/mode change, and independent ChatGPT cancellation.

Final command counts/results are recorded in DAY2_STATUS.md. These establish
VERIFIED AUTOMATICALLY behavior only; Android Keystore/Data Layer/IME/UI operation
and actual billed requests still require the end-of-day physical session.

Prior earliest_refresh_at contract limitation and Terra → Sol restoration debt
remain unchanged. No real sign-out, refresh-token mutation, key transfer, API
request, deployment, commit or push was performed by the agent.

## Commands actually run and final results

With the established persistent JDK/SDK environment:

```sh
JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9 \
ANDROID_HOME=$HOME/Android/Sdk \
ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android \
GRADLE_USER_HOME=$HOME/.gradle \
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:lintDebug :mobile:lintDebug :wear:assembleDebug :mobile:assembleDebug
```

Final result: BUILD SUCCESSFUL, 161 Wear + 64 mobile tests, zero failures/errors/
skips; lint zero errors and three prior warning categories per module. Initial
existing-suite compile gate passed. The first expanded run exposed a JUnit
non-void test-signature error; signatures were corrected and the full matrix
rerun successfully. Later cancellation tests also passed in the final full run.
No runtime/application test failure is omitted from these final counts.

Additional checks actually run: `git status --short`, `git diff --stat`,
`git diff --check`, targeted `rg`/file/diff inspection, Python XML result/lint
summaries, candidate private-artifact path inspection using `git ls-files
--cached --others --exclude-standard -z`, and both APK verifications:

```sh
JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9 $HOME/Android/Sdk/build-tools/36.0.0/apksigner verify --print-certs wear/build/outputs/apk/debug/wear-debug.apk
JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9 $HOME/Android/Sdk/build-tools/36.0.0/apksigner verify --print-certs mobile/build/outputs/apk/debug/mobile-debug.apk
```

Both certificates match Day 1; no signing key/configuration change. Gradle's
existing Platform-Tools/license warning remains. No private artifact candidate
was found among tracked/stageable file paths. No staging, commit or push.

## Files changed

- `BYOK_REVIEW.md`
- `DAY2_STATUS.md`
- `DECISIONS.md`
- `README.md`
- `ROADMAP.md`
- `mobile/src/main/kotlin/io/github/grassified/wristprompt/MainActivity.kt`
- `mobile/src/main/kotlin/io/github/grassified/wristprompt/WristPromptApplication.kt`
- `mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/ApiKeyInbox.kt`
- `mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/ApiKeySetupUi.kt`
- `mobile/src/main/kotlin/io/github/grassified/wristprompt/auth/PhoneApiKeyViewModel.kt`
- `mobile/src/main/kotlin/io/github/grassified/wristprompt/bridge/AuthBridgeListenerService.kt`
- `mobile/src/test/kotlin/io/github/grassified/wristprompt/auth/PhoneApiKeyTest.kt`
- `shared/src/main/kotlin/io/github/grassified/wristprompt/auth/ApiKeyProtocol.kt`
- `shared/src/test/kotlin/io/github/grassified/wristprompt/auth/ApiKeyProtocolTest.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/MainActivity.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/WristPromptApplication.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/auth/ApiKeyRepository.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/auth/ApiKeySettingsViewModel.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/auth/ApiKeySetupBridge.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/auth/ProfileListenerService.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/ApiFailure.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/ApiModelCatalog.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/CredentialRouter.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/InferenceProofUi.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/InferenceSession.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/InferenceViewModel.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/OpenAiHttp.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/OpenAiProvider.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/RefreshProtocol.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/ResponsesProtocol.kt`
- `wear/src/main/kotlin/io/github/grassified/wristprompt/openai/SseReader.kt`
- `wear/src/main/res/values/strings.xml`
- `wear/src/test/kotlin/io/github/grassified/wristprompt/auth/ApiKeyRepositoryTest.kt`
- `wear/src/test/kotlin/io/github/grassified/wristprompt/openai/ApiKeyCancellationTest.kt`
- `wear/src/test/kotlin/io/github/grassified/wristprompt/openai/ApiKeyProviderTest.kt`
- `wear/src/test/kotlin/io/github/grassified/wristprompt/openai/CredentialRouterTest.kt`
- `wear/src/test/kotlin/io/github/grassified/wristprompt/openai/InferenceViewModelTest.kt`
