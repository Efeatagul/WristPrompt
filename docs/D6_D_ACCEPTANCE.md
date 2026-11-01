# D6-D persistence acceptance — 2026-11-01

Issue #5; implementation/nonphysical acceptance and automated/emulator evidence
are complete. The original evidence below used main baseline `7beb72f`; the
compatibility refresh onto `62592c7` is recorded separately. This PR changes no
production behavior, credentials, routes, dependencies or schema. Issue #5 stays
open only for owner physical restart/reopen/continuation/UX acceptance, intentionally
deferred to the consolidated end-of-D7 physical pass before/as part of #12.
No physical PASS is claimed.

## Evidence matrix

| Boundary | Evidence collected in this session | Remaining |
| --- | --- | --- |
| Process restart | VERIFIED ON EMULATOR: externally observed app PID termination with `am kill`, then independent instrumentation reload | Owner restart/reopen/continue |
| Force-stop | VERIFIED ON EMULATOR: separate `am force-stop`, observed termination, independent reload | Not equivalent to natural process death |
| Completed, interrupted, cancelled replay | VERIFIED ON EMULATOR: all three seeded checkpoints survive both termination methods; restoration is idle/draft-free; explicit continuation has exactly the completed exchange plus new prompt | Owner explicit Send/Stop UX |
| Opaque completed replay | VERIFIED AUTOMATICALLY: ConversationPersistenceTest preserves opaque reasoning JSON exactly | Real-provider physical continuation |
| History/reopen/model/account/billing | VERIFIED AUTOMATICALLY: ConversationHistoryTest covers startup, explicit continuation, mismatch read-only, offline history, account changes and stale open; VERIFIED ON EMULATOR: SQLite rejects model/route/owner rebinding and wrong-owner rename/delete | Owner UI boundaries |
| Failure/exclusion/write ownership | VERIFIED AUTOMATICALLY: persistence and durable ViewModel suites cover failed begin/completion, Stop, revision rollback, late Stop and no per-token writes | Physical interruption is pending |
| Schema/migration | VERIFIED ON EMULATOR: clean v1 creation/reopen; future SQLite schema rejected and preserved. Codec unknown-version rejection passes automatically | No predecessor conversation DB exists; no fictitious v0 migration claimed. Real future migrations need versioned fixtures |
| Delete/no resurrection | VERIFIED ON EMULATOR: tombstone rejects stale save; SQL confirms live display/replay cleared and byte accounting released | No secure-erasure claim for SQLite free pages/WAL; owner confirmation UX |
| Limits | VERIFIED ON EMULATOR: exact 1 MiB UTF-8 entry accepted, +1 byte rejected without changing saved data; exact 32 MiB accepted, additional entry rejected, deletion releases capacity; 1,000 owners accepted, next rejected, existing owner stable; 10,000 total UUIDs including tombstones accepted, next rejected, update/delete still work at cap, deletion does not release UUID or permit resurrection | No silent eviction or tombstone pruning; physical storage UX pending |
| Disk-full recovery | VERIFIED ON EMULATOR: actual SQLiteFullException under SQLite page cap; store save rejects, previous canonical entry/revision remains unchanged; lifting cap permits same-revision retry and successful reload | SQLite page-cap fault injection, not physical filesystem exhaustion |
| Interrupted-write recovery | VERIFIED ON EMULATOR: external SIGKILL while dirty transaction/journal exists; reopen restores original display/replay/revisions, uncommitted table absent, integrity_check returns ok | Physical power loss/filesystem faults not claimed |
| Corruption | VERIFIED ON EMULATOR: malformed row rejected without rewrite, healthy row remains readable; corrupt database and newer schema preserved without destructive fallback | Owner recovery UX |
| Cleanup/orphans | VERIFIED ON EMULATOR: deletion removes live canonical content/accounting but retains required tombstone | No attachment files exist in D6; binary orphan cleanup belongs to D7-C/D |

## Earlier PR checkpoint — commands and outcomes

VERIFIED AUTOMATICALLY:

```sh
./gradlew :wear:testDebugUnitTest :wear:lintDebug :wear:assembleDebug :wear:assembleDebugAndroidTest --console=plain
```

PASS: 503 tests / 56 suites, zero failures/errors; debug and instrumentation APKs
built; lint 0 errors / 6 warnings. After adding checkpoint-only instrumentation
modes, the affected `:wear:assembleDebugAndroidTest :wear:lintDebug` gate passed.
Production and unit sources did not change; the full unit gate was not repeated.
`git diff --check` passed. Mobile and release isolation were not rerun: only
androidTest and this report changed; no debug/main/release source boundary changed.

VERIFIED ON EMULATOR: API 36, round 454×454 `WristPrompt_Watch7`, new temporary
userdata/cache, no snapshots. The existing AVD installation rejected replacement
because of a signing mismatch; it was preserved, not uninstalled or cleared.
Matched locally built Wear debug and androidTest APKs installed on the fresh image.

After verifying emulator identity and readiness, run on that disposable serial:

```sh
adb -s "$serial" install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s "$serial" install -r wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk
runner=io.github.grassified.wristprompt.test/io.github.grassified.wristprompt.storage.ConversationStorageInstrumentation
adb -s "$serial" shell am instrument -w "$runner"
```

Explicit PASS: SQLite lifecycle, revision rollback, owner isolation, deletion,
live-count limit, failed-insert preservation, record recovery, future schema and
corrupt-file preservation. The gate uses only its separate device-protected test
namespace, with synthetic text and no credentials/network.

For each method (`kill`, then separately `force-stop`), the executed restart sequence
was the following, with host-side assertions that a live PID existed and subsequently
terminated (20-second bounded poll, no kill-method fallback):

```sh
adb -s "$serial" shell am instrument -w -e checkpoint seed "$runner"
adb -s "$serial" shell am start -W -n io.github.grassified.wristprompt/.MainActivity
adb -s "$serial" shell pidof io.github.grassified.wristprompt
adb -s "$serial" shell input keyevent KEYCODE_HOME
# After the app backgrounds, execute one method, then verify pidof is empty.
adb -s "$serial" shell am "$method" io.github.grassified.wristprompt
adb -s "$serial" shell am instrument -w -e checkpoint verify "$runner"
```

Both returned explicit `PASS: restart checkpoint seed` and `PASS: restart checkpoint
verify`. This proves persisted canonical data across externally terminated app
processes; it does not prove production startup navigation, live HTTP continuation,
Wear rename/rotary/font behavior, physical process death or physical force-stop.
No claim of opaque replay in the emulator checkpoint fixture: that remains unit evidence.

## Remaining-scope acceptance run

Only androidTest and this report changed in this continuation. No production fix
was needed. The earlier 503-unit-test/debug-build evidence above remains applicable;
it was not rerun or presented as a new run.

VERIFIED AUTOMATICALLY: final focused gate passed:

```sh
./gradlew :wear:assembleDebugAndroidTest :wear:lintDebug --console=plain
git diff --check
```

Lint remains 0 errors / 6 warnings. The matched updated test APK was installed on
the same separate temporary API 36 Wear image described above. VERIFIED ON EMULATOR:

```sh
adb -s "$serial" shell am instrument -w -e checkpoint limits "$runner"
```

Explicit `PASS: restart checkpoint limits` covers the byte/owner/UUID boundaries
and SQLite-full recovery in the matrix. The 9,999-row pre-boundary fixture uses a
single test-only SQLite transaction to insert valid minimal tombstones efficiently;
the final insert, rejection, update, deletion and resurrection checks use production
store APIs. Total-byte fixtures contain actual bounded canonical UTF-8 content,
not spoofed byte counters. A test-only reflection helper obtains the SQLite helper
connection without adding a production fault-injection API.

Disk-full injection caps the database at its current page count. A direct probe
first confirms SQLiteFullException; the production store then rejects a large
update without changing the prior entry/revision. Raising the cap allows the same
update to commit exactly one revision. No device filesystem was filled.

Interrupted-write host sequence (verified disposable emulator only):

```sh
# Terminal A: intentionally waits after emitting READY.
adb -s "$serial" shell am instrument -w -e checkpoint interrupt-write "$runner"
# Terminal B: after READY: uncommitted synthetic transaction
pid=$(adb -s "$serial" shell pidof io.github.grassified.wristprompt)
adb -s "$serial" shell run-as io.github.grassified.wristprompt kill -9 "$pid"
# Verify the old process has terminated before reopening.
adb -s "$serial" shell am instrument -w -e checkpoint verify-interrupted-write "$runner"
```

The executed host driver validated the named emulator/debug package, waited at most
45 seconds for READY, required a single live PID, sent SIGKILL, waited at most 20
seconds for termination and required explicit verification PASS. The fixture
writes both display/replay and revision in an uncommitted transaction, adds a 4 MiB
probe to dirty/spill pages, and checks that its rollback journal exists before
READY. Reopen confirms all three seeded records/revisions, excluded-attempt replay,
absence of the uncommitted table and `PRAGMA integrity_check = ok`.

Initial harness-only attempts failed because ContextWrapper did not capture the
platform helper connection and a capacity fixture reused an already-deleted UUID.
Those fixtures were corrected; final focused limits/full and interrupted-write
runs passed. These were not production storage bugs. Failed initial runs are not
counted as acceptance evidence. No full Wear-suite repeat was needed.

## Handoff / remaining gate

No owner physical result was supplied. Batch Galaxy Watch restart/reopen/explicit
continuation, Stop/interruption, model/account/billing isolation, normal/max-font
history/rename/rotary and recovery UX. Never clear owner data, corrupt Keystore,
revoke a session or intentionally exhaust real storage/usage to complete this gate.

All requested implementation/nonphysical limits/recovery acceptance and
automated/emulator evidence are complete. PR #21 is eligible for review/merge
independently of the intentionally deferred physical acceptance. Issue #5 must
remain open for that owner evidence in the consolidated end-of-D7/#12 pass. No production startup/navigation, actual provider transport, device
power-loss or physical storage-exhaustion result is inferred from these fixtures.
This refresh starts no further issue and changes no production persistence behavior.

## Merge-readiness compatibility refresh — 2026-11-01

Refreshed PR #21 onto latest main `62592c7` with a normal merge into its published
branch. The two original acceptance commits and all newer main changes are
preserved; no history rewrite or force-push is needed. The resulting PR diff is
strictly this document and `ConversationStorageInstrumentation.kt`; production
files are identical to main. Main's newer snapshot codec is covered by this pass.

VERIFIED AUTOMATICALLY, using the documented JDK/Android environment:

```sh
./gradlew :wear:assembleDebugAndroidTest :wear:lintDebug :wear:testDebugUnitTest --tests '*openai.ConversationPersistenceTest' --tests '*openai.DurableChatViewModelTest' --tests '*openai.ConversationHistoryTest' --console=plain
git diff --check
```

PASS: 26 tests / 3 suites, zero failures/errors; instrumentation APK built; lint
0 errors / 8 warnings. No unrelated D7 suites were run. Local document links and
the final two-file scope were checked.

VERIFIED ON EMULATOR: dynamically selected, named disposable `WristPrompt_Watch7`;
matched debug and test APKs installed without clearing/uninstalling the app.
The default SQLite gate and `checkpoint limits`, `seed`, and `verify` each returned
an explicit PASS. These rerun real SQLite lifecycle/isolation/corruption/limits,
SQLITE_FULL rollback/recovery and independently reopened completed/interrupted/
cancelled snapshots against current main. External SIGKILL and separate process
termination evidence above remain historical and are not claimed as rerun here.
Only the separate synthetic device-protected test database was changed.

Implementation/nonphysical acceptance and automated/emulator evidence are complete.
PR #21 need not remain draft waiting for physical evidence. Issue #5 must remain
open after merge: only owner physical restart/reopen/continuation/UX acceptance
remains, intentionally deferred to the consolidated end-of-D7 pass before/as part
of #12. No physical tests were performed and no physical PASS is claimed.
