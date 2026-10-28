# Day 2 Phase D — ChatGPT sign-out and renewable-session revocation

## Final physical disposition — 2026-10-29

**VERIFIED PHYSICALLY — PASS WITH TRANSIENT REAUTHORIZATION ANOMALY.** Normal
sign-out was exercised. First recovery showed watch authorization failure and
phone code-exchange failure; another setup context expired. Force-restart/reopen
and retry eventually restored the same intended account, plan use, models and
completed inference. Exact cause is unresolved; no clean-pass claim, no reported
remote revocation outcome and no separate reauth client-suffix comparison.

Current state/expiry/callback/exchange guards and tests disclose no concrete unsafe
transition explaining this event. New synthetic failed-exchange -> fresh-request
recovery test passes without product-code changes; see DAY2_REPORT.md section C.
Track Day 3 triage / Day 5 reliability disposition, not a Day 3 blocker. Offline
sign-out is SKIPPED / NOT REQUIRED FOR DAY 2 PHYSICAL GATE. Prior deferred-session
instructions below are historical; no extra real sign-out cycle is requested.


Status: implementation/test evidence is VERIFIED AUTOMATICALLY. Real sign-out,
revocation and re-login are PENDING END-OF-DAY PHYSICAL TEST. No device sign-out,
real revocation request, app-data deletion or credential/key manipulation was run.
D2-1 acceptance remains intact; model-selection restoration is still Day 3 debt.

## Current authoritative contract

Reviewed on 2026-10-28:

- [Accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
- [Registration reuse and hints](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [Errors and recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
- [Public OIDC discovery](https://auth.openai.com/.well-known/openid-configuration)

Discovery currently advertises revocation and public-client authentication. The
implementation fetches it at sign-out; the revocation path is not hard-coded.
It validates the issuer, requires an HTTPS endpoint on the exact trusted issuer
origin (default/443 port, no userinfo/query/fragment), and checks public-client
support if advertised. Redirects remain disabled. Missing, malformed or unsafe
metadata never receives a token and results in unconfirmed remote revocation.

The revocation form contains only the saved refresh token, refresh-token hint and
issued client ID. No dynamic registration, client secret, access/ID token or
Authorization header is sent. Only an empty HTTP 200 confirms success, including
for an already-invalid token. Network failures and 5xx get at most one retry with
250 ms backoff. Discovery has a 3-second call timeout; each revoke call 4 seconds;
the complete revoker has a 12-second budget, with a 15-second repository safeguard.
Other statuses, nonempty 200, missing endpoint and timeout are unconfirmed.

## State machine

| Transition | Durable/current behavior | Evidence label |
| --- | --- | --- |
| Usable selected ChatGPT registration → sign-out requested | Capture selected client/generation; persist signOutPending, invalidate generation, pending login and duplicate handoff ACK state; block new leases | VERIFIED AUTOMATICALLY |
| Pending → stop session work | Cancel only that ChatGPT registration's active catalog/inference jobs; callbacks validate the lease before publication | VERIFIED AUTOMATICALLY |
| Pending → wait for refresh | Wait on the same per-client refresh lock without holding the vault lock; existing bounded rotation commits safely | VERIFIED AUTOMATICALLY |
| Wait complete → attempt revocation | Snapshot only issued client and latest known refresh token; discover/validate endpoint, then bounded attempt | VERIFIED AUTOMATICALLY |
| Remote success → local clear | Atomically clear access/refresh/ID token material and expiry/type/earliest fields; retain registration, record confirmed result | VERIFIED AUTOMATICALLY |
| Unconfirmed remote result → local clear | Same local clear; persist UNCONFIRMED and show disconnect-in-ChatGPT-settings guidance | VERIFIED AUTOMATICALLY |
| Unknown rotation → revocation of older token returns 200 | Still UNCONFIRMED: an unknown replacement may exist | VERIFIED AUTOMATICALLY |
| Process-style reconstruction finds abandoned sign-out | Complete local clear, mark UNCONFIRMED; never resume a usable credential or blindly replay network work | VERIFIED AUTOMATICALLY |
| Local write cannot complete | Storage error, no local-success claim; in-memory/durable gates block leases and recovery retries cleanup | VERIFIED AUTOMATICALLY |
| Repeated sign-out | Return stored outcome; no network call or credential recreation. A stale queued action cannot target a later reauthorized generation | VERIFIED AUTOMATICALLY |

An empty/no-renewable-token account is cleared locally without inventing remote
confirmation. Repeated CONFIRMED refers to the previously completed sign-out,
not a newly performed network action. If a crash loses an in-flight confirmation,
startup conservatively reports UNCONFIRMED after cleanup.

## Coordination and safety boundaries

The vault mutex is never held while cancelling requests, waiting for refresh or
performing discovery/revocation. Per-client sign-out serialization prevents
duplicate revocations; the existing refresh mutex prevents revoking a token just
before a known replacement is stored. Refresh can commit a newer tuple while
sign-out waits, but the durable sign-out gate and changed generation prevent that
tuple from becoming an inference lease. Sign-out then revokes the newer token.

UI sign-out stops the proof operation immediately. InferenceSession also tracks
per-operation jobs and cancels only the target registration; it validates leases
before discovery, stream events and completion. A deliberately noncancellable
fake provider cannot publish a late success after sign-out. Active HTTP calls
cancel through the existing coroutine/OkHttp cancellation path. Cancellation
cannot promise zero server-side usage for a generation already submitted.

Sign-out's bounded cleanup section survives caller cancellation. New browser
attempts/account selection are blocked while cleanup is pending; stale handoff
results cannot reinstall tokens. If the process dies, a fresh repository clears
abandoned sign-out before publishing authenticated state. A live repository load
does not mistake its own active sign-out job for an abandoned one.

No keys are deleted. The encrypted vault gains default-empty pending/result
metadata; existing records load without migration or token replacement. Only an
explicit sign-out (or recovery of that explicit intent) clears secrets. Empty ID
hint is permitted only in a tokenless, reauthorization-required local profile;
credential-transfer validation still rejects it. The transfer wire shape/version
is unchanged. Older application versions are not a supported rollback after a
new vault record has been written; do not downgrade an authenticated install.

## Retained registration data and reauthorization

After sign-out the selected saved account remains labeled in the picker, but is
explicitly signed out and cannot infer. Retained: issued client ID, validated
issuer/subject, host association, name/email/verification metadata, historical
scopes, selected-model preference and plan-notice preference. The independent
installation DataStore host ID and Keystore encryption key are untouched.

Cleared: access token, refresh token, retained ID token (empty local sentinel),
token type, expiresIn and earliestRefreshAt. There is no plaintext credential
fallback or persisted revocation envelope. Managed-runtime strings may remain
until garbage collection; no forensic memory/flash erasure is claimed.

Continue with ChatGPT on that selected saved account supplies its issued client
and stable host with fresh OAuth values and no ID-token hint/name hint. The phone
still validates the returned identity against the saved registration. A successful
handoff removes the signed-out result and activates the validated credentials.
Choosing Add another account remains an explicit different action; sign-out does
not perform it automatically or switch account/billing. No BYOK is implemented;
all cancellation and vault changes target ChatGPT registration state only.

## Automated tests

All network tests use synthetic interceptors/fakes, never the real revoke endpoint.
Coverage: empty-200 success/already-invalid response contract, exact minimal form,
discovered path, malformed/missing/untrusted discovery, temporary network loss,
HTTP timeout, bounded 5xx retry, nonempty-200/other statuses, local-only sign-out,
all token deletion, metadata/host retention, same-client reauthorization without
ID hint, repeated/concurrent sign-out, caller cancellation, active catalog/stream,
refresh race/latest-token revocation, uncertain rotation, abandoned sign-out,
local write failures and late handoff rejection. Profile DTOs/snapshots remain
redacted; errors do not propagate raw response bodies or exceptions.

## Physical session — deferred

Use DAY2_STATUS.md's one end-of-day checklist. Complete natural refresh checks
before signing out so the aged registration is not unnecessarily disturbed.
Then normal sign-out → same-account reauthorization → offline sign-out, with
optional online reauthorization afterward to restore a usable session. Explicit
user interaction during that session performs those actions; none were triggered
by the agent. Do not clear data, uninstall, delete keys or use a different signing
identity. Observe confirmed versus REMOTE REVOCATION NOT CONFIRMED honestly.

## Remaining device limits

Actual discovery/revocation availability, platform network cancellation, OEM
storage persistence and browser account selection after hint deletion remain
PENDING END-OF-DAY PHYSICAL TEST. A potentially rotated but unknown token cannot
be recovered optimistically; disconnect guidance is necessary for unconfirmed
remote outcomes. Local storage failure blocks success and may require another
cleanup attempt. The earlier earliest-refresh contract limitation remains; it
is independent of revocation and is not silently bypassed here.


## Validation and handoff

VERIFIED AUTOMATICALLY, using DEVELOPMENT.md's persistent toolchain/key:

```sh
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --no-daemon --console=plain
```

Final gate BUILD SUCCESSFUL: Wear 141 and mobile 57 tests, zero failures/errors/
skips. Both test tasks executed during this phase; unchanged mobile results were
up-to-date on the final repeat. Both debug APKs built and signatures verified with
the unchanged Day 1 SHA-256 certificate. Lint: zero errors, existing three warnings
per app (Wear wrapper / mobile target advisory, exported listener, logo vector).
Existing SDK platform-tools/license warning remains; no suppression added.

Ran git diff --check, reviewed sign-out/transfer/session diffs, and checked text
whitespace and secret-like credential/key/JWT patterns without printing values.
No signing/config/capture file became a commit candidate. No staging, commit,
push, device operation or real revocation request. Earlier uncommitted Phase C
changes were preserved.

Phase D files: shared auth/CredentialProfile.kt, auth/ProfileProtocol.kt and
ProfileProtocolTest.kt; mobile OAuthTransactionTest.kt; Wear MainActivity.kt,
WristPromptApplication.kt, auth/WatchProfiles.kt, new auth/OpenAiRevocation.kt,
bridge/WatchAuthViewModel.kt, openai/InferenceSession.kt, values/strings.xml,
new WatchSignOutTest.kt and OpenAiRevocationTest.kt; DAY2_STATUS.md, ROADMAP.md,
DECISIONS.md, README.md and this report.

Suggested commit: `feat: add safe ChatGPT sign-out and bounded session revocation`.
