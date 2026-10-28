# WristPrompt — Day 1 report and Day 2 recommendations

Date: 2026-10-28 (Europe/Istanbul). Assumption: today closes Day 1 of the original
six-day plan, leaving five execution days. Scope is v0.1.0 in PROJECT_SPEC.md;
ROADMAP.md owns milestone status. This is a handoff snapshot, not a new feature
request or authorization to begin Day 2.

## Assessment

**Day 1 vertical-slice gate: PASS.** The user’s 19:53 photographs show both exact
WristPrompt works. output and Observed: response.completed, plus the app’s explicit
success state. Together with the earlier physical sign-in/handoff and model-picker
photos, this proves the intended chain on Galaxy Watch 7 and its paired Android
phone. Production reliability and complete release security still require work.

## What was delivered

| Area | Day 1 result |
| --- | --- |
| Foundation | Buildable :wear and :mobile applications, same application identity/signing relationship, Kotlin/Compose and manual injection. Watch owns inference; phone remains a setup companion. |
| Authentication bridge | Wear-owned persistent UUIDv4 host ID; paired-node discovery; versioned, bounded requests; pending node/request/host binding and durable watch acknowledgement. |
| Continue with ChatGPT | Phone browser and loopback callback, PKCE/state/nonce, signed ID-token validation, issued-client persistence, verified identity and explicit plan-scope checks. |
| Credentials | Watch Keystore-backed AES-GCM vault, atomic persistence, backup exclusions and redacted diagnostics; no plaintext token fallback. Phone handoff copies stay in bounded process memory. |
| Inference | Account model discovery/selection and a fixed public Responses request with store=false and stream=true. Watch streams text and requires response.completed for success. |
| Recovery groundwork | Serialized watch refresh, atomic rotating-token replacement, conservative uncertain-refresh recovery, cancellation and basic account/usage errors implemented. Physical hardening remains open. |

The application is still a developer proof screen. General chat, text entry,
conversation context, functional BYOK and sign-out/revocation are remaining release
work. Voice is optional. No backend, analytics or extra provider was added.

## Evidence and limits

- **Physical, user-confirmed:** watch-to-phone request; browser authorization and
  loopback return; acknowledged credential handoff; saved account/plan state;
  account model loading/selection; exact streamed proof output and completion.
  Watch identifies as SM_L310; supplied ADB output identifies the phone as SM_S721B.
  Device OS/browser versions have not yet been recorded.
- **Automation:** latest reports contain 99 Wear tests and 55 mobile tests with
  no failures/errors/skips. The latest implementation run executed Wear tests;
  unchanged mobile tests were up-to-date. Both debug builds and lint tasks passed.
  Lint has zero errors and three existing warnings per app. Debug certificates
  match. Detailed commands and warning explanations are in DAY1_REVIEW.md.
- **Earlier emulators:** application launch and unpaired failure/persistence smoke
  checks. These do not establish paired OAuth or hardware-backed key failure.
- **Still unverified physically:** deliberate expiry/refresh and uncertain rotation,
  watch reboot/host-ID continuity, phone independence, sign-out/revocation, lost ACK,
  key loss/restore, interrupted streams, usage-limit recovery and account-change races.
  The successful session alone does not close these checks.

No tests or application builds were rerun to write this report. Existing reports
were inspected; documentation whitespace/XML/link checks were run separately.

## Issues found and resolved

1. The phone callback listener’s per-read timeout could outlive its attempt budget.
   Header reads now respect the absolute deadline; regression coverage was added.
2. The 256 KiB buffered body limit rejected the real account model catalog.
   Catalog decoding now streams picker fields with an independent 8 MiB budget,
   strict UTF-8 and model/field limits, preserving service order.
3. An HTTP 200 response without a usable content type was rejected before stream
   parsing. Absent Content-Type now proceeds through bounded SSE validation;
   explicitly wrong/invalid types remain rejected. Exact completion is still required.
4. Generic errors obscured these causes. Static categories and safe status codes
   now support diagnosis without exposing tokens, headers or response bodies.

Decisions D08/D09 record the interoperability changes. The final user acceptance
confirms the corrected path works; it does not establish every server variation.

## Recommended Day 2 order

| Priority | Work | Exit evidence |
| --- | --- | --- |
| 1 | Stabilize the development setup and preserve the existing debug signing key securely outside temporary storage and Git. Record OS/browser versions and the successful build baseline. | Repeatable build after a new shell; both apps retain compatible signing. |
| 2 | Repeat the plan proof after watch app restart/reboot, then with the phone offline and watch Wi-Fi available. Explicitly verify natural near-expiry refresh. Fix observed failures before adding another auth mode. | Same registration/host identity retained; watch can renew and complete inference without phone authorization when the grant is valid. |
| 3 | Implement sign-out/revocation and finish recovery handling. Preserve registration metadata for later sign-in, clear local secrets, cancel stale work and clearly report unconfirmed remote revocation. | Focused negative tests and physical sign-out/reauthorization; failed recovery never silently changes account or billing. |
| 4 | Add the required OpenAI API-key fallback with explicit API billing selection and protected storage. Share Responses streaming; keep account-catalog decoding appropriate to the selected credential mode. | BYOK survives restart and completes a real streamed request independently; invalid key and plan failure never trigger automatic fallback. |
| 5 | Close the targeted failure matrix: lost ACK, disconnect, cancellation, stale account results, malformed payloads, storage/key failure and limits. Reuse existing tests; avoid architecture expansion. | Unit/build/lint/diff checks pass, physical results recorded and unresolved release blockers named. |

Refresh/rotation and many negative tests already exist: harden and verify them,
rather than rewrite them. Test destructive key-loss cases only on a disposable
installation. Start natural expiry observation early while implementing the other
items. Keep production chat and optional voice in their later scheduled days.

**Day 2 gate:** both explicit billing modes work independently; restart/refresh
recovery and sign-out work; secrets remain protected; failure and cancellation
states are honest. A UI screenshot alone is insufficient for these recovery gates.

## Protecting the six-day release

Keep Day 3 for watch-native text chat, local context and a usable Stop action;
Day 4 for physical edge cases and essential UX, with voice only if on schedule;
Day 5 for security/release hardening, license/privacy/setup assets and signed
release-build checks; Day 6 for feature freeze, fixes and authorized publication.
Preserve required authentication, BYOK, streaming and security. Cut voice and
cosmetic polish first. The remaining schedule is tight; do not add providers,
conversation databases, frameworks or new optional features.

Check Play Console production eligibility at the start of Day 2. For personal
accounts created after November 13, 2023, Google currently requires at least 12
closed-test participants continuously opted in for 14 days before applying for
production access. If that applies and is not already satisfied, a new Play
production launch cannot fit this six-day window. See [Google’s testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
Even eligible accounts can face reviews of seven days or longer; see [publishing guidance](https://support.google.com/googleplay/android-developer/answer/9859751?hl=en).
The project’s actual account eligibility has not been inspected.

Recommendation: target a documented GitHub v0.1.0 release with signed APKs on Day 6 and
track Play submission/availability separately if external gates prevent the same
date. This distribution recommendation needs agreement; it is not a silent change
to the release requirement. The repository is private, all project files remain
untracked and there are no commits yet. An authorized source-control checkpoint,
license and explicit publication authorization are required before public release.

## Report handoff

Documentation changed: DAY1_REPORT.md, ROADMAP.md, DAY1_REVIEW.md and README.md.
No application code, dependencies, credentials or external services changed.
No commit, push or publication performed.

Suggested documentation commit: docs: close Day 1 acceptance and plan Day 2 hardening.
