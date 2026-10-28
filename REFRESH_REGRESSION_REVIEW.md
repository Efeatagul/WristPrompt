# D2-2 refresh-policy upgrade regression — 2026-10-29

## Final physical disposition — 2026-10-29

**VERIFIED PHYSICALLY — RESOLVED.** Owner reports the focused in-place retest
retained the same real registration/account and ChatGPT mode, without destructive
reauthorization; models loaded, WristPrompt works. streamed, response.completed
was observed and exact output matched. No exact token metadata, renewal time,
renewal-banner observation or additional refresh telemetry is inferred.
This closes the specific regression blocker. DAY2_REPORT.md now records Day 2
PASS WITH KNOWN DEBT; the prior STOPPED/pending/retest instructions below are
historical. No repeat focused retest or sign-out cycle is requested.


Day 2 physical acceptance is STOPPED. Only the focused retest below is requested.
No sign-out, reauthentication, credential extraction/reset, device action, BYOK
implementation change, new feature, commit or push was performed.

## A. Root cause

D2-2 added a blanket rejection for every non-null earliestRefreshAt after the
saved access-token expiry. Day 1 already accepted and retained this field
opaquely. The new policy changed how an unchanged valid credential record was
interpreted. It was an availability regression, not a demonstrated storage,
identity, signing or credential-migration failure.

History inspected: 54cf1df (Day 1 working checkpoint), d30ad5f (D2-1 physical
acceptance), 9719e71 (contains D2-2 refresh policy) and 2f375f3 (latest acceptance
procedure). The relevant 54cf1df..9719e71 diff adds the timing guard without
adding a required profile field. Working tree was clean before this fix.

## B. Why the reported real session reached this category

Exact path: Wear startup reconstructs WatchProfiles -> explicit plan router ->
InferenceSession.catalog -> WatchProfiles.credential -> TokenExpiry.needsRefresh.
The old guard throws REFRESH_TIMING_UNDOCUMENTED BEFORE a refresh intent write,
network grant, model GET or inference POST when:

1. the repository accepts the selected saved profile and its known expiry;
2. that profile is neither already reauthorization-required nor marked with an
   uncertain refresh intent (those paths fail earlier with another category);
3. earliestRefreshAt is present and not JSON null;
4. the watch's current clock is at or beyond savedAtEpochMs + expiresIn * 1000.

The failure then reaches InferenceViewModel with an empty model list/selection.
Account identity and plan scope remain visible because this failure does not
clear the encrypted record or change selection/billing.

These are code-path implications of the user's physical category, not a dump of
the real record. Its exact metadata value/type, actual token validity at OpenAI
and clock accuracy have NOT been inspected. No real encrypted record was read.
A missing optional field alone CANNOT reach this branch: both absent and JSON-null
hints already bypassed it. Day 1's exchange and refresh serializers both allowed
this field and stored a provider-returned value. Known saved receipt/expiry and
client/account/host metadata were already part of the old profile schema.

User evidence: in-place update, same signing, preserved data; account/plan/mode
remain correct with no automatic login, but models/inference fail. This establishes
VERIFIED PHYSICALLY — FAIL for the latest build's plan usability. Historical D2-1
persistence/phone independence PASS remains historical evidence, not proof that
the regressed build works.

## C. Official interpretation and source limitation

Read current official pages on 2026-10-29:
- [Token reference](https://developers.openai.com/siwc/token-sharing-open-source/token-reference)
- [Accounts/sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
- [Errors/recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
- [Registration/sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [OpenAI cookbook integration](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt)

The markdown retrieval returned unsupported-content errors; HTML pages were read.
The prose pages still do not specify earliest_refresh_at's representation. They
specify renewal near access-token expiry with the saved issued client, current
refresh token and resource, serialized refresh and replacement credentials.
The sign-in example persists known receipt/expiry without requiring an additional
locally-derived timing value. It does not establish that absent metadata is unsafe.

To resolve the prose gap, followed the cookbook's explicit official Sign-in
implementation links, rather than using Codex internals or third-party clients:
[OpenAI Sign-in devkit packages/local/src/index.ts](https://github.com/openai/sign-in-with-chatgpt-devkit/blob/main/packages/local/src/index.ts),
credentialsForRequest, read on 2026-10-29. It converts numeric earliestRefreshAt
from Unix seconds to milliseconds, parses string date values, defers a future
constraint while the bearer is valid, and reports refresh-not-ready when expired
but still prohibited. Missing timing does not mandate reauthentication. This is
first-party implementation evidence linked by the official cookbook; it is not
an independently versioned normative field schema.

The correction implements that numeric/date distinction with a stricter safe
subset for strings (ISO instants). It does NOT copy the devkit's permissive
fallback for invalid date values. No millisecond/relative-time guessing and no
fabricated earliest_refresh_at. Numeric strings are not silently numeric seconds.

## D. Fix and policy

Only Wear expiry classification/parsing and its static error display changed:

| Persisted timing | Behavior |
| --- | --- |
| Known expiry, hint absent or JSON null | Existing compatibility path: ordinary expiry-minus-60-seconds policy; no new metadata required |
| Supported numeric Unix seconds or ISO instant | Respect both near-expiry threshold and earliest constraint |
| Earliest is in the future, bearer valid | Use existing bearer only until known expiry; no grant sent |
| Bearer expired, earliest still future | REFRESH_NOT_READY; preserve credentials and wait; no refresh intent/grant/reauth |
| Earliest passed, refresh needed | Enter the existing serialized atomic refresh path |
| Non-null unrecognized/overflow/invalid timing, bearer valid | Continue existing bearer until known expiry |
| Non-null unrecognized timing at expiry, or missing known expiry | Fail closed with a static configuration category; no invented timing or refresh |
| Existing durable uncertain-rotation marker | Existing conservative reauthorization-required state remains; compatibility cannot bypass it |

Fractional numeric/date precision rounds UP to a millisecond, never granting an
early fractional refresh. Parsing errors retain no raw input/cause in diagnostics.
The new refresh-not-ready category is distinct from invalid credentials and does
not launch OAuth or change account/billing.

Unchanged: WatchProfiles per-registration locking, short vault mutex, durable
intent before grant, bounded non-cancellable exchange/commit, latest-vault patch,
atomic tuple replacement, sent/unsent handling, terminal failures, uncertain
rotation/replay policy, scopes, endpoint/form, sign-out, BYOK and signing.

## E. Compatibility / migration

No schema change or eager migration. Older records decode with the existing null
default. Already-persisted supported non-null hints are interpreted in memory.
A valid bearer requires no writes; eligible refresh performs only the established
intent + protected atomic token replacement. No registration is recreated,
identity merged, mode selected or credential metadata rewritten at load.

Successful refresh still replaces returned expiry/hint with the new token tuple;
missing timing in that new response remains missing, never synthesized. A genuinely
unsupported real value may still block at expiry; this report does not claim
its exact representation is known or physical restoration has passed.

## F. Regression tests

New RefreshUpgradeTest (six test methods):
- Old serialized vault with optional hint key omitted, valid synthetic client,
  identity/host, tokens, known expiry and model preference; reconstruction before,
  near and at expiry completes fake catalog/inference in plan mode with no OAuth,
  no mode switch, no fresh-token write and one durable rotation when due.
- Old non-null numeric and ISO hints that have passed renew successfully rather
  than hitting the blanket undocumented-timing failure.
- Explicit future numeric/ISO hints use valid access, send/write nothing at
  expired-but-prohibited time, and renew only at the allowed boundary.
- Malformed, boolean/object, negative and overflowing hints preserve bytes and
  fail closed at expiry, without a rotation marker or discarded credentials.
- Fractional boundaries round up; no ms/string-number guessing.
- Missing known expiry and an old uncertain-rotation record cannot use the
  compatibility path to bypass safety.

Existing tests were corrected to use truly unsupported metadata rather than
calling numeric 123 or a valid ISO future time “undocumented.” All existing
concurrency, cancellation, atomic-write, uncertain-outcome, replay, account and
billing tests remain in the full suite. Synthetic tokens only; no paid requests.

## G. Automated evidence

VERIFIED AUTOMATICALLY:

```sh
JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9 \
ANDROID_HOME=$HOME/Android/Sdk \
ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android \
GRADLE_USER_HOME=$HOME/.gradle \
./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:lintDebug :mobile:lintDebug :wear:assembleDebug :mobile:assembleDebug
git diff --check
```

BUILD SUCCESSFUL. 178 Wear / 69 mobile tests, zero failures/errors/skips. All
refresh/session suites are included. Both debug builds/lint pass, zero lint
errors. Wear has four warning instances in three prior categories (two AGP
version advisories, exported service, vector path); mobile has three prior
warning categories. Existing SDK platform-tools/license warning remains.

Both APKs verified with apksigner and retain the existing SHA-256 certificate:
`fd8491b6238368a04d58bd0653c35ff54a3a17fc9b31e53a6af7110579d37061`.
No dependency, SDK, license or signing configuration changed. Main-source search
found only the existing enum-only BridgeLog logger, no raw timing/token logger.
No emulator/device operation or live auth/refresh/inference ran.

Changed product files: wear openai/RefreshProtocol.kt, openai/ApiFailure.kt,
openai/InferenceProofUi.kt and res/values/strings.xml. Changed tests:
RefreshProtocolTest.kt, WatchRefreshTest.kt; new RefreshUpgradeTest.kt.
Documentation: this review, REFRESH_REVIEW.md, DAY2_STATUS.md, ROADMAP.md,
DECISIONS.md and DAY2_PHYSICAL_ACCEPTANCE.md. Mobile/BYOK/sign-out code untouched.

## H. One exact physical retest — preserve the existing registration

Run ONLY this retest, not the earlier full acceptance sequence. Keep the saved
account, ChatGPT-plan mode and working watch internet. Do not touch credentials.
The phone app does not need an update for this Wear-only fix.

1. Rebuild locally using the existing environment/signing identity:

   ```sh
   cd $HOME/projects/WristPrompt
   export JAVA_HOME=$HOME/.local/share/wristprompt/jdk-17.0.21+9
   export ANDROID_HOME=$HOME/Android/Sdk
   export ANDROID_USER_HOME=$HOME/.local/share/wristprompt/android
   export GRADLE_USER_HOME=$HOME/.gradle
   ./gradlew :wear:assembleDebug :mobile:assembleDebug
   ```

2. Select your already-connected watch's ADB serial (substitute the placeholder)
   and update in place. These are instructions for you; the agent did not run them:

   ```sh
   adb devices -l
   wristprompt_watch_serial='REPLACE_WITH_WATCH_ADB_SERIAL'
   adb -s "$wristprompt_watch_serial" install -r $HOME/projects/WristPrompt/wear/build/outputs/apk/debug/wear-debug.apk
   adb -s "$wristprompt_watch_serial" shell am start -W -n io.github.grassified.wristprompt/.MainActivity
   ```

   If install reports a signing/version conflict, stop and report it. Do not
   uninstall, downgrade or clear data as a workaround.

3. Confirm the SAME saved account/client suffix, ChatGPT-plan mode and no browser
   authorization request. Tap Reload models once (startup may already load them).
   Require a usable catalog/selected model and no REFRESH_TIMING_UNDOCUMENTED for
   supported existing metadata. Do not tap Continue with ChatGPT or Sign out.
4. Select an available model and tap Test inference once. Require streamed
   `WristPrompt works.`, Observed: response.completed and exact-output success.
   Note whether “ChatGPT session renewed on this watch” appears.

PASS requires same registration, usable catalog, completed plan inference and no
reauthentication or automatic billing change. A new refresh-not-ready state must
be reported as pending/blocked by a future constraint, not worked around with
login. If anything fails, report the static category/status only and stop this
retest; do not edit or export real metadata. Physical fix remains PENDING until
this result is supplied.

```text
Refresh regression retest — time:
In-place install: PASS/FAIL
Same saved account/client suffix: YES/NO
Active mode: ChatGPT plan / other
Automatic phone authorization requested: YES/NO
Catalog usable + model selected: YES/NO
Streamed WristPrompt works.: YES/NO
Observed response.completed + success: YES/NO
Session renewed banner: YES/NO
Static failure category / HTTP status, if any:
```

## I. Remaining risk / acceptance constraints

The real stored hint's exact value and any server-side terminal revocation cannot
be determined from the UI evidence alone. Supported values are fixed/tested;
unknown representations still deliberately block expired refresh. The focused
retest is necessary before calling this real-session regression resolved.

Broad acceptance remains STOPPED; no reauthentication/sign-out/data reset is
requested. The eligible ChatGPT plan is the physical inference path available.
Real successful BYOK inference is **BLOCKED — no funded OpenAI API credential
available for physical verification**. This is a testing constraint, not a
product-code failure. No purchase or real API key is required. Existing successful
BYOK fake-transport tests continue to pass. Fake-key no-fallback testing can be
considered only after the owner resumes broader acceptance, not during this retest.

## J. Suggested commit

`fix: interpret legacy ChatGPT refresh timing without blocking valid sessions`
