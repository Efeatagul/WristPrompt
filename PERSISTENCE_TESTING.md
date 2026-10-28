# Day 2 Phase B — persistence and phone independence

D2-1 overall: **PASS — VERIFIED PHYSICALLY**, user-confirmed on 2026-10-28.
Authentication/account persistence, watch reboot persistence, phone-independent
networking, direct discovery and direct inference passed. Explicit model-selection
restoration is **PARTIAL**; see the separate defect below. Unit/emulator evidence
is not physical evidence. No physical state changes were performed by the agent.

## User-confirmed physical results — 2026-10-28

| Test | Evidence label | User-confirmed result |
| --- | --- | --- |
| A — app restart | VERIFIED PHYSICALLY | PASS: same ChatGPT account and plan access remained; no reauthentication requested; inference completed successfully |
| B — watch reboot | VERIFIED PHYSICALLY | PASS: authenticated state survived reboot; inference completed successfully |
| C — phone unavailable | VERIFIED PHYSICALLY | PASS: phone completely powered off, watch directly on Wi-Fi; model catalog loaded and inference completed without phone |
| Explicit model restoration | VERIFIED PHYSICALLY | PARTIAL: GPT-6.1 Terra had been selected; restart selected GPT-6.1 Sol although Terra remained in the catalog |
| Storage-abstraction host stability | VERIFIED AUTOMATICALLY | DataStore close/reopen test passed; no physical host hash comparison was supplied |
| Natural-expiry renewal without phone | PENDING END-OF-DAY PHYSICAL TEST | Not established by the successful inference results alone |

The Terra → Sol observation is a real, non-blocking persistence defect, deferred
as low-priority Day 3 model-picker/chat technical debt. Automated selection tests
do not override this physical result. Root cause is not yet established; no product
code fix is made in this documentation task. A/B/C do not require another manual
session now. The remaining concise checklist is in [DAY2_STATUS.md](DAY2_STATUS.md).
No raw host/client/token records were requested to confirm these results.

## State/storage assessment

| State | Classification | Persisted location |
| --- | --- | --- |
| ext_agent_host_id | Sensitive non-secret opaque installation identifier | App-private Preferences DataStore `files/datastore/installation.preferences_pb`, key `ext_agent_host_id`; backup excluded |
| Issued client_id and issuer/subject | Sensitive non-secret registration identity | Encrypted `no_backup/chatgpt_profiles.enc`, inside each CredentialProfile |
| Host/client association | Sensitive non-secret | Same profile's hostId and RegistrationIdentity; profiles must share one installation host |
| Granted scopes | Sensitive non-secret authorization metadata | Same encrypted profile; plan scope and resource.invoke required independently of connected identity |
| Access and refresh tokens | Secret | Same encrypted profile, no plaintext fallback |
| Retained ID token / reauthorization hint | Secret | Same encrypted profile |
| Expiry, savedAt, earliestRefreshAt | Sensitive non-secret session metadata | Same encrypted profile; expiresAt derived with checked arithmetic; opaque earliestRefreshAt retained but not used to schedule |
| Name/email/verification flag | Sensitive non-secret personal metadata | Same encrypted profile, display-only labels; email never merges registrations |
| Active account / confirmed plan notice | Ordinary preference with account association | Encrypted vault selectedClientId / confirmedPlanClients |
| Selected model | Ordinary preference scoped to registration | Encrypted vault selectedModels map keyed by issued client_id |
| Pending login, accepted-transfer digest, generation, refresh intent | Sensitive non-secret transaction metadata | Same encrypted vault; prevents replay/stale result and uncertain refresh reuse |

EncryptedRecord uses Android Keystore AES-GCM, fresh randomized IV, associated data,
AtomicFile ciphertext writes and readback. Keys are app-local; app backups and
cloud/device transfer are disabled/excluded. Tokens never enter Preferences
DataStore. UI state includes account labels/plan state only, not token contents.

Before exchange the phone persists issued-client metadata in its separate
EncryptedRecord (`issued_registrations`); handoff tokens remain phone memory only. The
watch's accepted profile is authoritative after successful storage ACK.

## Reconstruction and bounded changes

MainActivity creates WatchAuthViewModel, whose init calls profiles.load(). The
new Application/repository reloads and validates the encrypted vault; connected
identity and plan permission are separate. InferenceViewModel loads a current
catalog when a usable selected account is reconstructed. The intended and
unit-tested behavior is to restore the saved model only if the current account
catalog still lists it (physical restoration is PARTIAL above); otherwise the first
current service-ordered entry is selected. No stored catalog or credential lease
is reused across process death. A legacy vault without selectedModels defaults
to an empty map; the new preference does not require replacing credentials.

Explicit model choices are saved before publication with client/generation
checks; late writes cannot select a model for another account. Missing vault means
unconnected; unreadable/corrupt/invalid vault means protected-storage error, with
no overwrite/reset. Validation also rejects dangling selected-client/model keys
and mixed installation-host records. Host mismatch during a new login fails
closed. Startup now avoids automatic discovery for an already uncertain session.
Refresh intent publication and the refresh algorithm remain unchanged.

HostIdRepository atomically generates one UUIDv4 only when the preference is
absent, reuses it after repository/DataStore recreation, and rejects invalid
stored values without regeneration. No activity/process/reboot hook replaces it;
no hardware-derived identity was added. Deleting app data or losing only the host
file is outside ordinary restart: a new ID can be generated, but existing-profile
host checks reject reauthorization with that different ID rather than mixing
registrations. Do not clear data to investigate persistence.

Freshness is determined on credential use by the existing TokenExpiry logic:
refresh within 60 seconds of expiry, fail on excessive backward clock skew.
WatchProfiles still owns serialized refresh, durable rotation and uncertain-grant
markers. No refresh algorithm or endpoint changed. A genuinely expired/revoked or
uncertain session may require explicit phone reauthorization; that is distinct
from an ordinary fresh-token restart. Do not force-stop during a refresh to test
normal restart persistence.

Phase C follow-up: the prior paragraphs describe the D2-1 implementation snapshot.
[REFRESH_REVIEW.md](REFRESH_REVIEW.md) supersedes the refresh-lock/clock/timing
assessment: registration-scoped locks, a shared injectable clock, a distinction
between live and uncertain renewal, and a no-guess guard for undocumented earliest
metadata. D2-1 physical results and the separate model-restoration defect remain
unchanged; no physical refresh PASS has been supplied.

## Phone independence trace

WristPromptApplication wires InferenceSession directly to WatchProfiles.credential,
OpenAiRefresh and OpenAiProvider using watch OkHttp. There is no WearConnectivity
or phone RPC dependency in discovery/refresh/inference:

- Watch → `GET https://api.openai.com/v1/models`
- Watch → `POST https://auth.openai.com/api/accounts/oauth/token` when renewal is needed
- Watch → `POST https://api.openai.com/v1/responses`

The phone bridge is used only after explicit Continue with ChatGPT to authorize
or reauthorize. Normal phone absence cannot erase profiles or select another
billing mode. Official contracts rechecked 2026-10-28:
[accounts/sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions),
[models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference).
The optional markdown fetch failed; the full HTML sources were read successfully.

## Manual preparation

Use DEVELOPMENT.md's persistent environment. Build/install only the watch update
with its existing certificate; mobile is unchanged. Installation is optional user
work, not something executed by the agent:

```sh
adb devices -l
wristprompt_watch_serial=WATCH_SERIAL
adb -s "$wristprompt_watch_serial" install -r wear/build/outputs/apk/debug/wear-debug.apk
```

Never use uninstall, clear-data, key deletion or a different signing key. Start
with a successful saved ChatGPT-plan account, no pending authorization, no active
inference/refresh, correct watch time and confirmed working internet. Record the
saved account label/client suffix and explicitly select a non-first listed model
if available; wait until Model shows the selection. Each inference consumes plan
usage. Do not tap Continue with ChatGPT during normal restart tests.

Optional host-continuity measurement (debug build, your trusted local terminal):
this hashes ONLY the host-only DataStore file without displaying/exporting its
contents. Record the hash privately before and after A/B/C; no raw host ID or
credential record is collected. If run-as/ADB fails, do not interpret the empty
stream hash as a result. Reconnect the watch's current wireless ADB port after a
reboot if needed.

```sh
(
  set -o pipefail
  adb -s "$wristprompt_watch_serial" shell run-as io.github.grassified.wristprompt test -s files/datastore/installation.preferences_pb &&
  adb -s "$wristprompt_watch_serial" exec-out run-as io.github.grassified.wristprompt cat files/datastore/installation.preferences_pb | sha256sum
)
```

This file hash measures unchanged host preference bytes, not registration identity
or cryptographic proof of a session. Account suffix continuity and successful
inference provide separate behavioral evidence. Never read chatgpt_profiles.enc
or Android Keystore material for this procedure.

## TEST A — watch app/process restart

1. With the already authenticated watch idle, record account label, plan indicator,
   selected model and optional host-file hash.
2. Manually force-stop ONLY WristPrompt, then reopen it. You may run these exact
   commands yourself; the agent does not run them:

   ```sh
   adb -s "$wristprompt_watch_serial" shell am force-stop io.github.grassified.wristprompt
   adb -s "$wristprompt_watch_serial" shell am start -W -n io.github.grassified.wristprompt/.MainActivity
   ```

3. Require the same saved account and **Connected with ChatGPT / Using ChatGPT plan**
   without phone consent. Let models load, or tap Reload models once. The explicitly
   chosen model should return if still listed. A removed model may select the first
   current entry; record that distinction.
4. Tap Test inference once. Require streamed `WristPrompt works.`, **Observed:
   response.completed**, and the explicit exact-output success message.
5. Compare the optional host-file hash; record PASS/FAIL and time. A swipe from
   Recents alone does not establish process-death evidence.

## TEST B — manual watch reboot

1. Record the same baseline with WristPrompt idle. Reboot the watch yourself from
   its system power menu; no ADB reboot is requested or executed by the agent.
2. Unlock and allow networking to reconnect, then launch WristPrompt normally.
3. Repeat A3–A5. Require the same registration label and plan state, stable optional
   host hash, valid model selection and completed proof without phone consent.
4. Record watch model/API and time. If a session genuinely requires reauthorization,
   record the displayed state; do not report restart persistence as passed.

## TEST C — phone unavailable, watch internet independent

1. Finish credential handoff and ensure no pending sign-in. Manually power off the
   paired phone (stronger evidence than merely closing WristPrompt). Leave it off
   until completion. Do not unpair devices.
2. On the watch manually enable/connect Wi-Fi to a working internet network. Verify
   internet access independently if possible. No automatic network/radio change
   is performed by the agent.
3. Launch WristPrompt and tap Reload models to demonstrate a new request. Require
   the saved account, plan status and a current catalog; use the saved selection
   only after it has been revalidated by that catalog.
4. Complete one explicit Test inference with the same required streamed output,
   response.completed and success message. There must be no phone bridge or browser
   prompt. Record phone-off status and watch Wi-Fi connection alongside the result.
5. Optional renewal evidence: while the phone remains off and watch internet stays
   working, allow natural token expiry (about an hour), reload models and repeat.
   Do not edit tokens, expiry timestamps or clock. Without this observation, a
   fresh-token success proves phone-independent inference, not physical refresh.
6. Restore the phone/network settings manually after recording the result.

## Result record and remaining risks

For each A/B/C report date/time, watch model/API, same account suffix yes/no,
plan indication, model restored/fallback, optional host hash same/different,
exact-output and response.completed yes/no, and any static failure category/HTTP
status. Keep private account labels out of public reports. Do not provide tokens,
headers, raw host IDs, callback URLs, app data or arbitrary exception output.

A/B/C are VERIFIED PHYSICALLY from the user's confirmation above. Their successful
results establish normal persistence and phone-independent discovery/inference,
not destructive key-loss/restore behavior or natural-expiry renewal. Natural
renewal is PENDING END-OF-DAY PHYSICAL TEST; exact physical host-byte continuity
was not separately measured. JVM encrypted-file tests use a synthetic JVM AES key,
not Android Keystore. A missing host file, key loss, app-data deletion/restore,
revocation and uncertain in-flight rotation are separate recovery conditions.
BYOK, sign-out, refresh redesign and chat features are outside this phase.

## Automated evidence — 2026-10-28

Using DEVELOPMENT.md's persistent JDK/SDK/cache/key environment, ran the following
baseline twice during implementation, including after the final startup guard
and encrypted-file recreation test:

```sh
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug :wear:lintDebug :mobile:lintDebug --no-daemon --console=plain
```

VERIFIED AUTOMATICALLY (prior implementation run): BUILD SUCCESSFUL. All 108 Wear tests executed with zero failures,
errors or skips (99 baseline + 9 new tests). The mobile application and shared
code are unchanged; mobile assemble/lint passed with up-to-date artifacts. Mobile
unit tests were not rerun in this phase. Both lint reports have zero errors and
three existing warnings each: Wear wrapper advisory / mobile target advisory,
exported Data Layer service and logo vector path. Existing missing/unaccepted SDK
platform-tools warning remains; system ADB is available. No suppression added.

Coverage includes actual Preferences DataStore close/reopen host-ID stability,
invalid host failure, synthetic vault recreation, saved identity/scopes/hint,
per-client model persistence, same-email registration isolation, stale model/host
rejection, missing/invalid/unreadable records, current-catalog selection validation,
storage-write failure and restored uncertain-session startup. An additional test
recreates an encrypted file-backed SecretRecord with a synthetic JVM AES key,
runs catalog/completion through a fake provider without a phone dependency, then
checks corrupt ciphertext fails closed. Existing expiry/rotation/process-death
intent tests continue to pass; this is not a new refresh design or live inference.

Read-only ADB commands: devices -l, phone getprop ro.product.model,
getprop ro.build.version.sdk and version-only dumpsys package filter. Only phone
SM-S721B / API 35 / WristPrompt 0.1.0 code 1 was available; no watch was listed.
No install, launch, force-stop, reboot or network toggle was executed by the agent.

Ran git diff --check, reviewed the production diff, and checked non-ignored text
for trailing whitespace/final newlines and secret-like credential/key/JWT values;
all checks passed without values being printed. No files staged, committed or pushed.

Changed files: WatchProfiles.kt, InferenceViewModel.kt, MainActivity.kt,
WatchProfilesTest.kt, InferenceViewModelTest.kt, DECISIONS.md, ROADMAP.md,
and this document. Suggested commit:
`fix: preserve watch model selection and verify restart persistence`.


## Documentation update verification — 2026-10-28

VERIFIED AUTOMATICALLY: git diff --check and changed-document whitespace checks
passed. Source-file hashes were compared before/after this documentation update;
no product/test source changed. Existing uncommitted implementation changes were
preserved. Builds/unit tests/lint were not rerun for this documentation-only task;
the automated implementation evidence above remains attributed to its prior run.
No D2-2 work, physical actions, staging, commit or push occurred.
