# D6-D persistence acceptance — 2026-11-01

Issue #5; partial acceptance on main baseline `7beb72f`. No production behavior,
credential, route, dependency or schema change. D6-D remains open; D7 was not started.

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
| Limits/recovery | VERIFIED ON EMULATOR: 100 live rows accepted, 101st rejected without eviction, existing row update works at capacity, deletion permits another save | 32 MiB total, 1 MiB entry boundary, 1,000 owners, 10,000 total IDs and actual disk-full recovery not consolidated here |
| Corruption | VERIFIED ON EMULATOR: malformed row rejected without rewrite, healthy row remains readable; corrupt database and newer schema preserved without destructive fallback | Full UI recovery and interrupted disk-write acceptance |
| Cleanup/orphans | VERIFIED ON EMULATOR: deletion removes live canonical content/accounting but retains required tombstone | No attachment files exist in D6; binary orphan cleanup belongs to D7-C/D |

## Commands and outcomes

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

## Handoff / remaining gate

No owner physical result was supplied. Batch Galaxy Watch restart/reopen/explicit
continuation, Stop/interruption, model/account/billing isolation, normal/max-font
history/rename/rotary and recovery UX. Never clear owner data, corrupt Keystore,
revoke a session or intentionally exhaust real storage/usage to complete this gate.

Complete the outstanding nonphysical limits and recovery matrix before declaring
#5 blocked only on owner evidence. This PR deliberately does not close #5 and does
not authorize another phase. Suggested commit: `test: expand D6 persistence recovery acceptance`.
