# Day 2 — one end-of-day physical acceptance session

Galaxy Watch 7 + paired Android phone · prepared 2026-10-28

This is the single execution procedure for today's remaining physical checks.
Older reports remain evidence/history; do not execute their checklists as well.
Until you report results, these checks are PENDING END-OF-DAY PHYSICAL TEST.

D2-1 remains PASS — VERIFIED PHYSICALLY. Do not repeat watch reboot,
authentication-only app restart, or phone-powered-off discovery/inference.
The two app-process restarts below test the newly added billing-mode persistence,
not D2-1 again. No watch reboot or phone power cycle is needed.

## Before starting

- Run the final Day 2 Phase G watch AND phone builds, updated in place with the
  existing signing identity. If already installed, do not reinstall. Do not
  uninstall, downgrade, clear app data or change signing to solve installation.
- Have both devices charged, unlocked, paired, and on working networks. Keep the
  watch on working Wi-Fi. Phone stays powered on for the entire session.
- Begin with the saved usable ChatGPT account, idle, without pending OAuth or
  refresh. Keep the same account throughout. Note its displayed client suffix
  privately as `A`; report only SAME/CHANGED afterward.
- Have your real API key available from your own secure source, and access to
  that API account's normal billing. If an existing watch key cannot be entered
  again, skip the replacement/removal tests rather than losing its configuration.
- Record the session start time, installed build provenance (final Phase G APKs,
  not just version 0.1.0), watch/phone OS versions and initial active mode.
- Note whether “ChatGPT session renewed on this watch” is ALREADY visible. It is
  a runtime flag, not a timestamp or proof of a second renewal.
- Only report static errors, HTTP status, selected mode/model and terminal state.
  No keys, tokens, callback URLs, full client/host IDs, auth dumps or browser URLs.
  Phone screenshots may be blocked intentionally; text observations suffice.

If unexpected protected-storage errors, account/client changes or automatic
billing switching occur, record them and stop dependent tests. Do not repair by
clearing data, deleting keys or repeated OAuth. A service outage can leave a test
PENDING/BLOCKED without proving a product defect.

## 1. ChatGPT smoke, with conditional natural-refresh evidence

**Prerequisites:** existing saved account A; correct device time; watch internet;
ChatGPT plan explicitly selected. Do this before any sign-out or reauthorization.

**Action:** open WristPrompt and note any newly appearing renewal indication.
Tap **Reload models** once if discovery has not already completed. Select an
available model and tap **Test inference** once. The built-in prompt is
`Reply with exactly: WristPrompt works.`; there is no chat text field to fill.

**Expected / PASS:** catalog loads for A, billing stays **ChatGPT plan / Using
ChatGPT plan**, text streams, and **Observed: response.completed** plus exact
`WristPrompt works.` appear. Account/client suffix stays A. Partial text or a
catalog alone is not a passed inference.

**Natural refresh sub-result:** count this SAME request as a refresh test only
if natural token ageing actually triggers renewal and a NEW “ChatGPT session
renewed on this watch” indication is observed, with A unchanged, completed
inference and no phone/browser reauthorization. Leave the phone app/browser
unused during this step; no power-off test is needed. This verifies watch-owned
renewal without phone authorization, not a new phone-powered-off test.

If the token is fresh, age is unknown, or the banner was already present and
cannot be attributed to this attempt, record **PENDING — refresh not observed**.
Do not wait indefinitely or make repeated requests to force it. If you already
know the token needs more real time and want that evidence today, postpone this
whole session until later BEFORE signing out; reauthorization resets the session.
Never edit time, token files or expiry. `REFRESH_TIMING_UNDOCUMENTED` means
**BLOCKED — existing contract limitation**; do not loop sign-in to call it a pass.

**FAIL:** missing/wrong terminal success, new registration, required phone OAuth
for an otherwise refreshable session, credential loss, or a billing switch.
Classify unrelated connectivity/service failures separately.

**Report:** smoke PASS/FAIL; model; streamed/exact/completed yes/no; A SAME/CHANGED;
refresh PASS/PENDING/BLOCKED/FAIL; banner BEFORE/NEW/ABSENT; phone OAuth yes/no.

## 2. Connection clarity and Manage usage — no inference

**Prerequisites:** step 1's connected account; phone available.

**Action:** scroll settings with touch/rotary. Check the fixed active-mode label,
account display and **Using ChatGPT plan**. Tap watch **Manage usage**, then open
WristPrompt on the phone and tap **Manage ChatGPT usage**.

**Expected / PASS:** active mode remains readable while scrolling; safe account
metadata appears. Watch gives phone instructions; the phone action opens ChatGPT
usage settings. Companion is setup-only and explicitly describes API billing as
separate. The watch does NOT promise automatic phone-browser opening.

**FAIL:** unreadable/incorrect active billing, secret display, misleading API-plan
wording, or a usable phone browser action that navigates to the wrong destination.
If no browser is installed, record the fallback message and that navigation is
untested. Do not provoke a usage limit or change any account usage settings.

**Report:** label/readability PASS/FAIL; usage destination OK/FAIL/UNTESTED;
any misleading wording. Check the equivalent API label during later steps.

## 3. Disconnected handoff, using a fake key only

**Prerequisites:** retain ChatGPT A; keep ChatGPT mode selected. Both devices
initially reachable; real key not entered. Know how to restore phone radios.

**Action:** on watch tap **Use OpenAI API key** (or **Change key**). On phone,
wait for key entry, type `clearly-fake-placeholder`. Temporarily isolate the
PHONE: enable airplane mode and explicitly ensure Bluetooth and Wi-Fi are OFF
(they may remain enabled in airplane mode). Keep watch Wi-Fi on. Tap phone
**Transfer to requesting watch** once.

**Expected / PASS:** within roughly the 15-second RPC budget plus modest UI
scheduling delay, phone reports missing acknowledgement/retry, not success.
Entry is hidden/cleared; no indefinite sending spinner. ChatGPT A and the selected
ChatGPT mode remain intact. Allow up to 30 seconds to observe this; record a
longer wait rather than repeatedly tapping.

Restore the phone's previous radios promptly. Within the two-minute setup
window, tap **Retry secure transfer** once. Require acknowledged watch storage
without re-entering the key. If the window expires, require an expiry state,
request setup again on watch and enter the same FAKE value once more.

**FAIL:** success claimed without delivery, endless sending, loss of A, automatic
mode selection, or retry corruption. If isolation did not actually prevent
communication, mark the disconnect sub-test **PENDING**, not failed.

**Report:** timeout/failure message and elapsed time; retry ACK yes/no; entry
cleared yes/no; A and mode unchanged yes/no; expired/restarted context if needed.
This is disconnect/retry evidence. It does NOT establish a precisely lost ACK
after watch persistence; that case remains covered by deterministic unit tests.

## 4. Invalid API authentication and independent fake-key removal

**Prerequisites:** fake key from step 3 acknowledged; ChatGPT A still usable.

**Action:** explicitly choose **OpenAI API**. Tap **Test API key / reload API
models** once. After the failure, tap **Remove key** while API remains selected.
Do not enter a real key yet.

**Expected / PASS:** clean API-authentication rejection, normally HTTP 401, with
no ChatGPT request/output or automatic mode switch. After removal, API remains
selected and says key not configured. The saved ChatGPT account remains listed.
Then explicitly choose **ChatGPT plan**; A remains connected.

**FAIL:** fake key accepted as valid, automatic ChatGPT proof/fallback, misleading
plan-usage error for API billing, OAuth account loss, or mode switched by removal.
A network/503 error does not prove invalid-key handling: record it as untested
until a genuine authentication rejection can be observed, without retry loops.

**Report:** error/status; invalid-key PASS/FAIL/PENDING; no fallback yes/no;
mode after removal; A preserved yes/no. Only the fake local key was removed;
do not revoke a real API key in the OpenAI dashboard.

## 5. Real BYOK setup, API-mode restart and completed inference

**Prerequisites:** phone reconnected; A saved; ChatGPT mode selected; your real key
available privately. This includes the first of two billing-mode app restarts.

**Action:** request key setup on watch, enter your real key in the phone's masked
field and transfer it. Require **Secure watch storage acknowledged** and cleared
entry. Before touching the mode selector, verify receipt DID NOT change the
selected ChatGPT mode. Explicitly select **OpenAI API**.

With no operation running, force-stop ONLY the watch WristPrompt app via Android
app settings or your existing trusted ADB workflow, then reopen it. Swiping away
alone is not reliable process-restart evidence. No reboot, uninstall or clear-data.
Require API mode to return. Tap **Test API key / reload API models**, select a
model you know supports Responses text for your API account, then **Test inference**.
The built-in prompt is exactly `Reply exactly: BYOK path works.`.

**Expected / PASS:** both credentials remain available; fixed **OpenAI API /
separate API billing** indication; streamed `BYOK path works.`;
**Observed: response.completed** and exact-output success. API catalog access
alone is insufficient. Phone has no visible retained key entry.

**FAIL:** automatic mode switch on transfer/restart, unexpected OAuth dependency,
lost key/account, wrong billing label, or false completed success. Unsupported
model/quota/service failures are not a BYOK inference pass; record and diagnose
without deliberately consuming quota or blindly trying many models.

**Report:** transfer ACK/entry-cleared yes/no; mode before/after restart;
API model; streamed/exact/completed yes/no; result/error. This is the required
small real API-billed proof request; no artificial paid error states are needed.

## 6. Both credentials: mode switching, current catalog, plan-mode restart

**Prerequisites:** step 5; both credentials configured; no active generation.

**Action:** explicitly switch to **ChatGPT plan**. Verify its account-specific
catalog/model label replaces the API catalog. If discovery is still running,
switch once to API and back to plan; old results must not replace the current
mode's catalog. Do not repeatedly race fast requests just to catch the window.

When idle, force-stop/reopen ONLY WristPrompt once more. Require ChatGPT mode
and A to return. No inference is necessary here; step 8 supplies the next plan
proof. Switch back to API and leave it selected for step 7.

**Expected / PASS:** each explicit mode and matching billing label/catalog is
clear; both saved credentials coexist; second restart preserves ChatGPT mode.
A model preference is usable only within its current catalog. A Terra → Sol
restart change is the known separate Day 3 defect, not a new D2-1 auth failure.

**FAIL:** mode silently changes, an old catalog visibly replaces the new one, or
an unavailable model is presented as ready under the other mode. If discovery
finishes too quickly to race, report stale-race observation as PENDING; ordinary
switch/persistence results can still pass. No second account is required.

**Report:** both modes clear yes/no; API/plan restart results; current catalog
consistent yes/no; race observed/too fast; model-restoration issue if noticed.

## 7. Normal online ChatGPT sign-out, with API credentials preserved

**Prerequisites:** both credentials configured; API selected; working watch
network; no active refresh/setup/request. Note A before the action.

**Action:** tap **Sign out of ChatGPT** for saved A. This intentionally signs out
that ChatGPT session. Wait for completion. Record the exact remote outcome.
Check API remains selected/configured. Now explicitly choose **ChatGPT plan**.

**Expected / PASS:** local sign-out finishes; A stays saved but signed out;
ChatGPT inference is unavailable. With plan selected, no automatic API fallback
occurs despite a configured real key. API key remains configured and mode changes
only when you explicitly choose it.

Report remote outcome separately: **CONFIRMED** or **REMOTE REVOCATION NOT
CONFIRMED**. Honest UNCONFIRMED plus completed local cleanup can pass local
sign-out safety; it does not verify remote revocation. With idle refresh state,
network work should normally finish within its 15-second safeguard; allow about
30 seconds for UI observation, then report a stuck state. Do not kill the app
mid-cleanup or repeatedly tap Sign out.

**FAIL:** plan requests remain usable, API key disappears, automatic billing
fallback, false remote-success claim, or local storage failure reported as success.

**Report:** local PASS/FAIL; remote CONFIRMED/UNCONFIRMED; elapsed time; A retained;
API mode/key preserved; plan unavailable/no fallback yes/no. Do not disconnect the
app in ChatGPT settings just to change UNCONFIRMED into a different test outcome.

## 8. Same-account reauthorization, cancellation and recovery

**Prerequisites:** step 7; phone/watch connected; saved A selected; ChatGPT mode
selected. Use A, not **Add another ChatGPT account**.

**Action:** tap watch **Continue with ChatGPT**; on phone tap its sign-in button,
finish browser authorization with the SAME ChatGPT account/workspace, and wait
for acknowledged handoff. Check the saved account/client suffix is still A and
no duplicate registration appeared. If browser account choice is wrong, cancel
instead of consenting with another identity.

Reload models if needed. Tap **Test inference**, then the existing developer
**Stop** as soon as it appears while loading/streaming. This control exists today;
there is no final Day 3 Stop UI to test. If the tiny proof completes before you
can stop it, record cancellation PENDING/TOO FAST rather than calling it failure
or repeatedly spending requests. That completed request can satisfy reauth proof.

If Stop catches the request, expect **Stopped. Partial output is not a completed
proof.** No later output/completion may turn that stopped generation into success.
Wait briefly (about 10 seconds) for visible late results. Then tap **Reload
models**, select an available model and **Test inference** once to finish with
streamed `WristPrompt works.` and **Observed: response.completed**.

**Expected / PASS:** reauthorization reuses A, acknowledged handoff succeeds,
API key remains configured, mode stays plan and a fresh plan proof completes.
Cancellation separately passes only if Stop was actually observed, late success
did not appear, and explicit retry works without another login.

**FAIL:** new/mismatched registration without your choice, silently changed mode,
API credential loss, completed status fabricated after Stop, or cancellation
corrupting authentication. Stop does not promise zero server-side usage.

**Report:** same A/duplicate yes/no; handoff ACK; reauth completed proof result;
Stop CAUGHT/TOO FAST/FAIL; partial text/completion after Stop; recovery proof result.
This leaves both modes configured and ChatGPT usable with only ONE planned
ChatGPT reauthorization in the main sequence.

## 9. Optional offline sign-out — default SKIP to avoid another login

This tests meaningful offline cleanup, but adds one more deliberate ChatGPT
sign-out and reauthorization. It is not needed to repeat D2-1 and is excluded from
the minimum session. Run only if that extra browser recovery is acceptable and
step 8 was uncomplicated; otherwise report PENDING — recovery burden avoided.

**Prerequisites:** working real API key retained; known-good same-account recovery;
no active request/refresh; record A; select API to check sign-out leaves it selected.

**Action:** isolate the WATCH using airplane mode and ensure watch Bluetooth,
Wi-Fi and LTE/mobile data (if present) are all OFF. This prevents phone-proxy
internet too. Tap **Sign out of ChatGPT** once. Record result, then explicitly
select plan and verify it cannot infer. Restore all prior watch network settings.
Select saved A, **Continue with ChatGPT**, finish same-account phone/browser setup,
explicitly select plan and run one successful plan proof to restore usability.

**Expected / PASS:** bounded local sign-out; **REMOTE REVOCATION NOT CONFIRMED**
and disconnect guidance; A retained; API key/mode unchanged until manual selection;
no plan-to-API fallback. After restoring networks, same A reauthorization and
completed proof work.

**FAIL:** false confirmed remote revocation while fully isolated, indefinite
cleanup, credential/mode crossover, or failed recovery attributable to the app.
Network restoration/account-service problems are reported separately.

**Report:** SKIPPED or local result/elapsed; remote outcome; API preserved; same-A
recovery and completed proof result. Do not leave the session appearing remotely
revoked when the UI explicitly says it was unconfirmed.

## What is intentionally not repeated or physically provoked

- D2-1's authenticated restart/reboot and phone-powered-off proofs stay PASS.
- No watch reboot, phone power cycle, second account creation or repeated OAuth
  for timing races. The planned two app restarts cover only the new mode setting.
- No key invalidation, app-data reset, uninstall, storage corruption, token/clock
  editing, API-key revocation, usage exhaustion or induced rotating-refresh loss.
- Lost-ACK-after-persistence, precise stale-result races and storage/Keystore
  faults retain their deterministic Phase G test evidence. A natural radio
  disconnect is not proof of every one of those cases.
- No new reproduction of Terra → Sol, artificial usage/eligibility errors,
  production chat, voice, or broad release/backup testing in this session.

Successful physical observations become VERIFIED PHYSICALLY only after your
report. Untested/too-fast/not-due items remain PENDING END-OF-DAY PHYSICAL TEST;
the existing refresh metadata contract issue remains BLOCKED. Do not turn an
incomplete session into an overall PASS by assuming the omitted results.

## Paste-back result template

```text
DAY 2 EOD — date/time:
Build: final Phase G watch+phone installed in place YES/NO/UNSURE
Watch OS / phone OS:
D2-1 prior physical PASS retained; no reboot/phone-off retest.

1 Plan smoke: PASS/FAIL; model=; streamed/exact/completed=; account A SAME/CHANGED
  Refresh: PASS/PENDING/BLOCKED/FAIL; banner BEFORE/NEW/ABSENT; phone OAuth YES/NO
2 Mode readability: PASS/FAIL; Manage usage OK/FAIL/UNTESTED
3 Disconnect: PASS/PENDING/FAIL; timeout seconds=; retry ACK=; entry cleared=;
  A/mode unchanged=; expired request restarted YES/NO
4 Fake key: PASS/PENDING/FAIL; HTTP/error=; no ChatGPT fallback=;
  fake removal kept API mode=; A preserved=
5 Real BYOK: PASS/FAIL/PENDING; ACK/cleared=; API model=;
  streamed/exact/completed=; API mode after restart=
6 Both modes/catalogs: PASS/FAIL; plan mode after restart=;
  stale race OBSERVED/TOO FAST; model-restoration note=
7 Online sign-out: local PASS/FAIL; remote CONFIRMED/UNCONFIRMED;
  seconds=; A retained=; API preserved=; plan blocked/no fallback=
8 Reauth: PASS/FAIL; same A=; duplicate account YES/NO; completed proof=;
  Stop CAUGHT/TOO FAST/FAIL; no late completion=; recovery proof=
9 Offline sign-out: SKIPPED/PASS/FAIL; remote=; API preserved=; same-A recovery=
Final: active mode=; ChatGPT usable=; API configured=; radios restored=
Static errors / unresolved items (no credentials):
```

Prepared from AGENTS.md, ROADMAP.md, DAY2_STATUS.md, PERSISTENCE_TESTING.md,
DAY2_BASELINE.md, REFRESH_REVIEW.md, SIGN_OUT_REVIEW.md, BYOK_REVIEW.md and
DAY2_FAILURE_MATRIX.md, with exact current UI actions checked against source.
This document is preparation, not a claim that any physical test has run.
