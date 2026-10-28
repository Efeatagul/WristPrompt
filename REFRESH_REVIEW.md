# Day 2 Phase C — refresh audit and recovery hardening

## Final evidence update — 2026-10-29

The owner has physically resolved the upgrade timing regression after the focused fix. Exact refresh telemetry was not supplied. Final Day 2 evidence and the superseding policy are in DAY2_REPORT.md / REFRESH_REGRESSION_REVIEW.md.


> Superseded timing assessment, 2026-10-29: the blanket opaque-hint expiry guard
> caused a real upgrade regression. See REFRESH_REGRESSION_REVIEW.md for the
> official cookbook-linked Sign-in devkit evidence, supported numeric/ISO parsing,
> missing-hint compatibility and focused retest. The older guard's test results
> below are historical, not evidence that its blocking policy was correct.


Date: 2026-10-28. Implementation scope: existing ChatGPT-plan renewal only.
Physical refresh: **PENDING END-OF-DAY PHYSICAL TEST**. D2-1 A/B/C remain
VERIFIED PHYSICALLY; those results do not prove refresh occurred. The Terra → Sol
selection defect remains low-priority Day 3 debt and was not addressed here.

## Official contract review

Reviewed current official HTML documentation:

- [Accounts/sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions): registration association, renewal form and session serialization.
- [Token reference](https://developers.openai.com/siwc/token-sharing-open-source/token-reference): expiry/replacement fields and lifetimes.
- [Errors/recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery): terminal token codes, invalid client and temporary failure distinctions.
- [Preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations): existing inference restrictions remain unchanged.

No endpoint/resource/scope conflict requires an authentication architecture change.
The saved registration supplies its own issued client and refresh token; the
form retains API resource audience and omits scope. Tokens remain watch-owned.

**BLOCKED — documented earliest-refresh interpretation:** the token reference
names earliest_refresh_at but supplies no type, unit, encoding or minimum-refresh
formula. The targeted official-domain search found no definition. Do not infer
Unix seconds/milliseconds, ISO timestamps or a scheduling contract from the field
name, JWT payloads, Codex internals or real credential captures. This is a source
contract limitation, not a failed physical test.

Previously that field was stored opaquely and ignored. Now a non-null constraint
prevents an early rotating request: the existing bearer remains usable until its
ordinary expiry, then CONFIGURATION / REFRESH_TIMING_UNDOCUMENTED is returned
without sending a grant, writing intent, deleting tokens or asking for repeated
sign-in. Missing/JSON-null metadata uses the existing 60-second margin. Actual
"before earliest" versus "earliest allowed" semantics cannot be verified until
OpenAI defines the representation. This availability limitation is explicit; the
implementation does not claim to support an invented timestamp encoding.

## Existing state machine before this change

1. WatchProfiles.credential selected a profile while holding one vault mutex.
2. TokenExpiry calculated savedAt + expiresIn * 1000 and refreshed within 60 seconds.
   WatchProfiles had an injected time function; OpenAiRefresh separately timestamped
   responses with System.currentTimeMillis. earliestRefreshAt was not interpreted.
3. Under the same mutex, it saved a refreshPending client marker before the request.
4. Network, validation and commit ran in a bounded NonCancellable section. Success
   replaced the whole CredentialProfile and cleared the marker in one vault write.
5. Other callers waited on that global lock, including work for other registrations.
6. A known terminal code cleared access/refresh material and retained registration,
   host and ID-token hint. invalid_client was already a configuration error.
7. Definitive non-200 errors cleared intent with a five-second manual retry gate.
   Network/protocol failures, outer timeout and process death preserved the intent;
   later use required reauthorization rather than replaying the old rotating token.
8. The live UI interpreted any durable intent as reauthorization-required, even
   while the original grant was still safely running.

Useful existing invariants were retained: no auto-generation replay, no automatic
new client, account switch, API billing fallback, refresh on phone, token logger,
plaintext persistence or mixed-field credential update.

## Defects and bounded corrections

| Finding | Correction |
| --- | --- |
| Network I/O held the global vault mutex | Per-client refresh mutex; short vault lock only for snapshot, durable intent and commit |
| Releasing that lock would allow old snapshots to overwrite unrelated changes | Re-read latest vault at commit and patch only the original matching registration; stale account callers receive STALE_ACCOUNT |
| All network failures were treated as possibly transmitted | Refresh-only OkHttp EventListener records requestHeadersStart; DNS/connect failures before that point are definitely unsent, all later loss remains conservative |
| A live refresh looked like a crashed/uncertain refresh | Runtime refreshing set distinguishes live work from durable orphan intent; no new persisted schema |
| Time sources differed | Shared injectable SessionClock for expiry checks, retry gates and response receipt timestamps; dispatcher injectable for virtual-time timeout tests |
| Earliest metadata silently ignored | Explicit no-guess guard described above; unresolved interpretation labeled BLOCKED |
| No safe physical observation of actual renewal | Static session-renewed banner appears only after the validated full tuple is durably committed; no IDs or token fields exposed |

No credential-file migration, key operation or registration replacement is performed.
No model-preference logic was changed. Same-registration browser authorization is
blocked while its refresh runs; selection/other registrations remain responsive.
A successful old-account renewal is persisted to that account even if selection
changed, but cannot publish a usable lease for the newly selected account.

## Current outcomes

| Outcome | Token/intent handling | Caller/UI state |
| --- | --- | --- |
| Fresh token | No refresh/write | Existing selected ChatGPT-plan lease |
| Near expiry, no non-null timing constraint | Save intent; one grant per client; peers wait and re-read the committed result | REFRESHING, not a false reauthorization state |
| Successful response | Validate identity/host/retained hint; atomically replace access+refresh+type+expiry+scopes+receipt metadata; clear intent | New lease; static renewal banner |
| Caller cancelled after grant begins | Bounded non-cancellable grant/commit completes; cancelled caller receives no late UI result | Other callers use durable rotated tuple |
| Definitely unsent DNS/connect failure | Clear intent; preserve entire profile | NETWORK, five-second explicit retry gate |
| Trustworthy 429/5xx or other HTTP rejection | Preserve profile; clear intent; bounded manual retry | TEMPORARY or classified error; invalid_client is CONFIGURATION |
| Confirmed terminal refresh code | Clear unusable access/refresh material; retain client/host/identity/scopes/ID hint and account preference | REAUTHORIZATION REQUIRED for that saved registration |
| Sent request, lost/timeout response; malformed success; unexpected cancellation/association mismatch | Keep protected profile and durable intent; never replay uncertain token | REAUTHORIZATION REQUIRED; remains a saved account |
| Intent write fails | No request sent | Protected-storage error |
| Post-grant write fails before commit | Old complete tuple plus intent remains; no partial tuple published | Storage error; recreation sees uncertain intent |
| Atomic write completes but reports uncertainty | Readback/recreation can use only a complete new tuple, never mixed fields | Storage error for original caller; subsequent read decides safely |
| Process death during rotation | Durable intent survives; no optimistic replay | Explicit saved-registration reauthorization |
| Unknown non-null earliest constraint and expired bearer | No grant or token mutation | CONFIGURATION, not signed out or token-invalid |

Terminal codes covered: invalid_grant, invalid_refresh_token, token_expired,
refresh_token_expired, refresh_token_invalidated and refresh_token_reused.
invalid_client does not delete tokens or silently register another client.

Only safe machine categories, booleans and static messages leave this path. Raw
exceptions, provider messages, forms, token bodies and credentials are discarded
from errors. Account/client association and billing mode remain explicit.

## Automated matrix

All cases use synthetic credentials; expiry tests use fake SessionClock values.
Timeout/concurrent scheduling uses virtual coroutine time and deferred barriers,
not real sleeps for token ageing. Real loopback transport tests contain no tokens
and distinguish an unsent DNS failure from a request whose response was lost.

| Cases | Evidence label |
| --- | --- |
| Fresh/no refresh; exact near-expiry boundary; expiry and backward clock | VERIFIED AUTOMATICALLY |
| Omitted/JSON-null earliest metadata permits renewal; unknown metadata defers while bearer valid and blocks before sending when expired | VERIFIED AUTOMATICALLY |
| Actual timestamp-format "not yet allowed" / "allowed" interpretation | BLOCKED — official encoding unspecified |
| Issued client/saved refresh/resource form, no requested scope/client secret; injected receipt time | VERIFIED AUTOMATICALLY |
| Complete rotation; scope retention/reduction; every persisted tuple wholly old or wholly new | VERIFIED AUTOMATICALLY |
| Concurrent same-account callers share one durable rotation; another account progresses while first waits | VERIFIED AUTOMATICALLY |
| Definitely unsent network error; sent/lost response; five-second gate; 429/500/503 | VERIFIED AUTOMATICALLY |
| Each terminal code; invalid_client separate; account/host/hint preservation | VERIFIED AUTOMATICALLY |
| Malformed success and wrong registration; outer timeout; cancellation; pre/post-grant and uncertain completed writes | VERIFIED AUTOMATICALLY |
| Process-style reconstruction; no rotating-token replay; no implicit billing/account change; secret-redacted failures | VERIFIED AUTOMATICALLY |
| Natural device refresh, completion and unchanged mapping without phone | PENDING END-OF-DAY PHYSICAL TEST |

## End-of-day physical acceptance

Use the concise checklist in DAY2_STATUS.md. Keep the current registration and
same debug signing identity. After natural token ageing, use watch Wi-Fi with the
phone unavailable; reload models and run one proof. Require an observed session
renewal indication, inference completion, no browser authorization, no new client
and unchanged saved-account/client suffix. Record a static category if it fails.
Do not infer refresh from elapsed time or a successful fresh-token inference alone.
Do not print raw IDs, edit expiry, delete credentials, revoke sessions or change
Keystore state. No production expiry control was added. No physical test is
required immediately; independent safe work remains subject to authorized phases.

## Remaining limits

The unknown earliest metadata format is unresolved and may block expired sessions
that include it; preserved credentials are not evidence that it is safe to ignore.
No physical refresh PASS is claimed. Android Keystore/atomic filesystem durability
under real process loss remains device evidence, not JVM proof. Synchronization
assumes the current single application process and shared Application repository;
adding android:process or multiple independent vault writers needs a new review.

## Validation and changed files

VERIFIED AUTOMATICALLY, final gate with persistent DEVELOPMENT.md environment:

```sh
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --no-daemon --console=plain
```

BUILD SUCCESSFUL. All 122 Wear tests passed, zero failures/errors/skips (108
baseline + 14 additional tests). Mobile/shared sources are unaffected; its unit
task was up-to-date with 55 prior passing cases. Both APK builds and both lint
tasks passed. Lint: zero errors and unchanged three warnings per app (Wear wrapper
advisory / mobile target advisory, exported listener, official logo vector).
Existing SDK platform-tools/license warning remains; no suppression was added.
Both APK signatures verify against the unchanged Day 1 certificate fingerprint.

Ran git diff --check, text whitespace/final-newline scan, production-diff review,
private-file candidate checks and a values-suppressed credential/key/JWT scan.
No suspicious secret-like candidate or private signing/config file was found.
No physical tests, real credentials, SDK/key modifications, staging, commit or push.

Changed production files (all under wear): MainActivity.kt (static renewal notice
only), WristPromptApplication.kt (shared clock wiring), auth/WatchProfiles.kt,
openai/ApiFailure.kt, OpenAiHttp.kt, OpenAiProvider.kt, RefreshProtocol.kt and
res/values/strings.xml. Tests: OpenAiProviderTest.kt, RefreshProtocolTest.kt,
WatchRefreshTest.kt and new RefreshTransportTest.kt. Documents: this report,
DECISIONS.md, ROADMAP.md, DAY2_STATUS.md and a Phase C pointer in PERSISTENCE_TESTING.md.

Suggested commit: `fix: harden registration-scoped refresh and conservative recovery`.
