# Day 1 engineering review — 2026-10-28

**Current Day 1 vertical-slice status: PASS.** User-confirmed physical acceptance
at 19:53 Europe/Istanbul on 2026-10-28 shows exact WristPrompt works. output,
Observed: response.completed and the explicit success state. The earlier review
was PARTIAL; the chronological follow-ups below document how the blockers closed.
Refresh, key-loss, interrupted handoff and other recovery checks remain open.
See [DAY1_REPORT.md](DAY1_REPORT.md) for the handoff and Day 2 recommendations.
The audit table below reflects the initial engineering review; subsequent user
acceptance is recorded separately and does not imply every failure path passed.

Rechecked official [registration](https://developers.openai.com/siwc/token-sharing-open-source/sign-in),
[sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions),
[models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference),
[preview limits](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations),
[error recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery),
and [Android wearable authentication](https://developer.android.com/training/wearables/apps/auth-wear).
No material contract conflict found in the reviewed paths.

## Audit conclusions

| Area | Finding |
| --- | --- |
| Gradle/project | Two pinned application modules; checksum-pinned wrapper. compileSdk 37 / targetSdk 36 deviation already justified in DECISIONS.md. Temporary SDK/JDK had disappeared; restore a permanent toolchain before daily work. |
| Module separation | Phone owns browser authorization and handoff; watch owns profiles, refresh, catalog and inference. Shared source directories avoid protocol duplication without another module. |
| Data Layer | Capability discovery chooses one node; versioned, bounded canonical payloads; no broadcast secrets or durable token DataItems. Matching package/signature and Play services Binder trust remain device/delivery checks. |
| Host ID | Atomic DataStore generation of one opaque UUIDv4 URI; corruption fails closed. Backup exclusions prevent identity cloning through app backup. |
| OAuth | Official public authorization/token endpoints, initial dynamic registration, exact loopback URI, browser opened after binding. No RemoteAuthClient redirect substitution or private ChatGPT endpoint. |
| PKCE/state/nonce | SecureRandom values per transaction, S256, constant-time state comparison before success/error processing. |
| ID token | Nimbus verifies RS256 against pinned issuer JWKS; issuer, audience/azp, nonce, times, returning identity and optional access-token hash checked. No decode-only identity trust. |
| Issued client ID | Saved before exchange in encrypted phone metadata; watch retains verified registration. Initial incomplete registration can retry its issued ID. Additional-account interruption recovery still warrants a physical check. |
| Scopes | Token-response scopes govern plan permission. Identity-only sign-in cannot authorize inference. |
| Storage | Keystore AES-GCM, random IV, associated data, atomic ciphertext write/readback, no plaintext fallback; separate registration identities. Real Keystore/key-loss behavior is unverified. |
| Transfer | Pending node/request/host/generation binding; expiring transfer ID and payload digest; ACK only after encrypted persistence; identical retry ACK cannot reinstall stale tokens. |
| Refresh | Watch alone refreshes under a mutex; atomic rotating-token replacement. Persisted intent makes an uncertain/process-death outcome require reauthorization rather than replaying an old grant. This conservatively trades availability for safety. |
| Models | Account catalog, displayable entries only, service ordering/name/slug; refreshed bearer invalidates the earlier catalog. No assumed production model. |
| Responses | Public API, only model/input/store=false/stream=true; bounded UTF-8 SSE framing and output. |
| Terminal events | Deltas alone and [DONE]/EOF are not success. Completed, failed, incomplete, interrupted and cancelled remain distinct; proof also checks exact output. |
| Errors | Sanitized enums/messages; limits never switch billing paths. OAuth discovery parse failures currently fall through to a generic storage message: misleading, but fail closed. Improve classification during recovery hardening. |
| Secret leakage | No credential/payload logging, HTTP logger, analytics or plaintext preference storage found. DTO stringification is redacted; bridge logging accepts enum diagnostics only. |
| Backup | Both apps disable backups and exclude all documented domains for cloud/device transfer; ciphertext also lives in noBackupFilesDir. OEM restore behavior needs hardware evidence. |
| Lifecycle | Phone tokens/transaction stay in memory; phone process death requires a new sign-in. Watch persists pending transfer/refresh intent; stale results are invalidated. Test browser backgrounding, lost ACK, restarts and network transitions physically. |
| Complexity | No DI/database/navigation framework. WatchProfiles serializes the entire vault, including refresh; acceptable for this proof, but bound stalls need device testing. Unused CompanionViewModel is small cleanup debt. |

## Security findings, in severity order

1. **Medium, fixed in review:** Loopback SO_TIMEOUT limited each read rather than
   the whole attempt. A local peer sending partial/slow headers could exceed the
   browser deadline. Header reads now use the remaining monotonic attempt budget;
   a regression test sends incomplete headers with a short deadline. No token
   disclosure or authentication bypass was found in this path.
2. **Release-blocking verification gap, not a confirmed vulnerability:** actual
   signed Data Layer handoff, Keystore persistence/key failure, and OAuth on an
   Android phone have not been demonstrated on the paired physical installation.
3. **Known release work:** sign-out/revocation is not implemented; scheduled for
   Day 2. Do not release v0.1 without it. Phone process death deliberately drops
   unacknowledged in-memory credentials; no automatic revocation of a possibly
   accepted watch session.

## Explicit repository search

Searched all non-ignored Git commit candidates for every requested term.
`Authorization`, `Bearer`, `access_token`, `refresh_token`, and `id_token` occur in
legitimate protocol/network declarations, documentation and synthetic tests.
`api_key` names the disabled fallback UI; it contains no key. `Log.` hits include
BridgeLog calls; the sole Android logging sink is the enum-only Log.w wrapper.
OpenAI endpoints are centralized constants (plus independent test assertions and
documentation). No real model is hard-coded into production selection/inference.
No occurrences of `sk-`, `println`, `printStackTrace`, `HttpLoggingInterceptor`,
`SharedPreferences`, `EncryptedSharedPreferences`, `TODO` or `FIXME` were found
before adding this report. Occurrences in this paragraph are audit labels only.
No legitimate protocol code was deleted to silence the search.

## Verification

Executed with restored temporary Temurin 17.0.21, official API 37.0 platform and
Build Tools 36.0.0, and a temporary Gradle cache. Download checksums verified.
No project dependency versions or SDK targets changed.

```sh
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --no-daemon --console=plain
./gradlew :wear:testDebugUnitTest --rerun :mobile:testDebugUnitTest --rerun --no-daemon --console=plain
```

Both commands: BUILD SUCCESSFUL. The second explicitly executed both test tasks:
wear 80 + mobile 55 = 135 executions (112 distinct tests; 23 shared execute in
both modules), zero failures/errors/skips, including the new deadline regression.
Both debug APKs assembled; apksigner verify --print-certs passed for each and
reported identical SHA-256 signing-certificate digests. Lint has zero errors and
three warnings per app: exported Data Layer listener, official logo vector path,
and wrapper-version advisory (watch) / target-API advisory (phone). Export is
intentional for Play services; do not add an incompatible permission merely to
silence it. Existing version/SDK choices remain documented in DECISIONS.md.

Earlier attempts failed before tests because the former temporary JDK/SDK was
absent; restored package metadata required correction before the successful run.
The minimal restored SDK still warns that platform-tools is absent; system adb
is installed. No warning suppressions were added. `adb devices -l` reports no
attached devices. No emulator or physical-device test was performed in review.

`git diff --check` passed. Since all project files remain untracked, also checked
every non-ignored text candidate for trailing whitespace/final newlines and parsed
all 16 XML candidates successfully. No files staged, committed or pushed.

## Remaining acceptance and next work

Use BRIDGE_TESTING.md and INFERENCE_TESTING.md. On Galaxy Watch 7 + paired Android
phone: authorize from watch, confirm phone browser callback and storage ACK,
observe plan permission, load/select a service model, send the fixed proof prompt,
and observe both `WristPrompt works.` and `response.completed`. Repeat after watch
restart/refresh, interrupt transfer/network, and verify failure/limit states
without billing fallback. Record device/OS/browser versions without credentials.

First Day 2 task: establish a permanent toolchain and close this physical Day 1
gate before adding BYOK or chat features. Then prioritize sign-out/revocation and
recovery classification. Keep release signing alignment and backup/key-loss
checks in the remaining five-day plan. Avoid architectural expansion.

## Subsequent user evidence — 2026-10-28

User screenshots and install/launch output confirm the phone browser loopback
callback and phone success after watch storage acknowledgement. Watch displays
its saved account, Connected with ChatGPT and Using ChatGPT plan. This establishes
the physical happy-path sign-in/handoff gate; it does not establish restart
persistence, key-loss behavior, refresh or successful inference. Actual ADB device
models are watch SM_L310 and phone SM_S721B.

The watch reports a protocol error while loading models, Model: Not selected,
and a disabled Test inference button. No request reached the inference acceptance
step. The exact cause is not established by these images. Added static diagnostic
categories for response size/UTF-8 and catalog JSON/list/entry/duplicate failures;
these retain fail-closed behavior and contain no raw response or account data.
The model contract remains the officially documented models[]/visibility/name/slug
contract. Do not guess an alternative endpoint or expose credential records to
investigate this failure.

The user's shell build failed because the selected Java 25 installation is a
runtime without javac. The subsequent successful ADB installs therefore used
existing APKs. A full JDK is required to build the diagnostic update. Continue
with install -r and the same signing key; do not clear this authenticated install.

Follow-up validation: ran the full both-app unit/build/lint command recorded above,
then reran :wear:testDebugUnitTest :wear:lintDebug :wear:assembleDebug
:mobile:assembleDebug after the final UTF-8 negative test. Both commands succeeded.
Wear now has 84 passing tests; mobile's 55-test task was up-to-date in this follow-up.
Both APK builds and lint tasks passed (existing warnings remain). Verified the
updated watch APK signature matches the earlier debug certificate. git diff
--check and untracked text/XML checks passed. The previous watch ADB port refused
connections; no authenticated device state was read or changed. Root cause of the
physical catalog failure remains pending the static-category result on hardware.

## Catalog size fix — 2026-10-28

The user reported BODY_TOO_LARGE on physical hardware after installing the
static-category build. Their Java setup now builds successfully. Successful model
catalogs now decode directly from the response stream using picker-only DTOs,
with an independent 8 MiB decompressed byte limit. The 256 KiB token/error-body
limit and SSE-frame bounds remain unchanged. Strict streaming UTF-8, cancellation,
model count, visible-field validity, ordering and duplicate-ID checks remain.
Decision D08 records the tradeoff; no credentials were read/exported or replaced.

Initial negative tests caught the library wrapping byte-limit errors and accepting
malformed UTF-8; the bounded input wrapper now retains only a static failure
category and validates UTF-8 across reads. Tests cover catalogs above 256 KiB,
skipped metadata, fragmented reads, malformed/trailing JSON, invalid Unicode,
size/model-count bounds, cancellation, transport errors and visible-ID duplicates.
Physical catalog loading and the completed inference proof still require retry.

Final catalog-fix verification: :wear:testDebugUnitTest (91 tests, zero failures,
errors or skips), both assembleDebug and both lintDebug tasks passed after the
parser corrections. The unchanged mobile test task was up-to-date with 55 prior
passing cases. Reran Wear tests/lint after the final Unicode edge cases. Verified
the new watch APK signature matches the previous debug certificate. Ran git diff
--check plus untracked text/XML checks successfully. No device install or live
inference was performed by the agent. Suggested commit: fix: stream large
account model catalogs within bounded memory.

## Physical catalog success and inference diagnosis — 2026-10-28

The user confirms the updated model catalog loads on Galaxy Watch 7. Photographs
show service names, selection and an enabled Test inference action. This closes
the physical discovery/selection check. The next test fails with the generic
protocol message before any confirmed text/completion. Its root cause is not
established by the pictures; no raw HTTP response or credential was collected.

Rechecked official models/inference and streaming-event documentation. The minimal
request still matches the documented contract. Extended the existing static
failure categories to distinguish JSON/HTML/other/missing stream content type,
invalid event JSON/type/shape, conflicting SSE names, line/frame/output limits and
unexpected client exceptions. UI shows a bounded HTTP status when available.
No arbitrary response fields, provider messages, exceptions or headers are shown.
Corrected malformed SSE UTF-8 to a protocol failure rather than a network error.
Generic UI wording now says WristPrompt could not process the response, avoiding
an unsupported claim that the server is necessarily at fault. No architecture
change, credential change, speculative parser fallback or automatic replay.

Next physical step: install the watch update with the same signing key and -r,
retain the saved account, reload/select a model and explicitly test once. Report
only Failure category and HTTP status if present. Keep completion acceptance open.

Validation for inference diagnostics: ran
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --no-daemon --console=plain
with the existing full JDK/SDK/cache. BUILD SUCCESSFUL; Wear 95 tests passed with
zero failures/errors/skips; unchanged mobile tests were up-to-date (55 prior
passing cases). Both builds and lint tasks passed. APK signature still matches
the previous debug key. git diff --check and untracked whitespace/XML checks
passed. No live inference was run and no device credentials were accessed.
Suggested commit: fix: classify Responses stream failures without leaking payloads.

## Headerless Responses compatibility — 2026-10-28

User evidence narrows the remaining rejection to RESPONSE_MISSING_TYPE at HTTP
200. The client stopped before reading stream events; no actual response body was
collected. Updated OpenAiProvider to pass a missing Content-Type through the
existing bounded SSE reader. An explicitly wrong/invalid content type still
fails. Required completion, exact proof text, failed/incomplete/interrupted
semantics, request fields, selected credentials and transport protections remain.
Decision D09 records this interoperability choice. Inference is not marked passed.

Changed OpenAiProvider.kt and its focused tests, plus DECISIONS.md, ROADMAP.md,
INFERENCE_TESTING.md and this review. Device install/retry is still required.

Validation: ran the full both-app unit/build/lint command recorded above using
the existing full JDK/SDK/cache. BUILD SUCCESSFUL; 99 Wear tests passed with no
failures/errors/skips; unchanged mobile tests were up-to-date (55 prior cases).
Both debug builds and lint tasks passed. Verified the watch APK retains the same
debug certificate. git diff --check and untracked text/XML checks passed. No
physical inference or credential access was performed by the agent. Suggested
commit: fix: validate headerless Responses streams before rejecting them.

## Final physical acceptance — 2026-10-28, 19:53 Europe/Istanbul

The user explicitly confirmed the inference works and supplied photos showing
Success: response.completed received and exact test output matched., the exact
WristPrompt works. text and Observed: response.completed. Combined with earlier
physical sign-in/handoff and model-selection evidence, this closes the Day 1
vertical-slice gate. It does not establish refresh/rotation, revocation, account
isolation under failures, lost-ACK recovery or key-loss/backup behavior on hardware.
No raw credentials or private response payloads were copied into this record.
