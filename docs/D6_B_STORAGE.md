# D6-B storage implementation

Issue #3; based on merged D6-A (`78d5cc2`). Physical acceptance remains pending.

## Implemented boundaries

The watch writes an inert begin checkpoint before dispatch, then one atomic
completed checkpoint after response.completed. Streaming tokens and unsent drafts
are never written. Stop writes an excluded display checkpoint without replay.
Process restoration converts pending attempts to excluded/interrupted display;
neither a Job nor a request is restored. Completion storage failure does not
publish a successful exchange. Completed opaque replay items survive the codec.

SQLite lives in app-private storage with the existing database backup exclusions.
All access uses Dispatchers.IO and a mutex. No new runtime dependency, foreground
service, scheduler, wake lock, network retry or billing fallback was introduced.
The per-conversation writer retains revision ownership across lifecycle cancellation.
A committed completion cannot be overwritten by a late Stop checkpoint.

## D6-A schema refinement

The logical typed Conversation/Message/ContentPart model is implemented as bounded
versioned domain records, with separate SQLite `display` and `replay` columns in
one conversation row. SQLite commits both columns and metadata in one transaction.
This deliberately replaces D6-A's proposed normalized per-message tables: at most
12 attempts are loaded as a unit, no cross-message query is currently required,
and whole bounded checkpoints keep crash consistency and JSON/opaque fidelity
simple. This is not a serialization of Compose/UI state or rendered Markdown.
Text and ImageAttachment are explicit sealed parts. Actual image/file support,
reference accounting and its migration belong to D7-C/D.

Schema version 1 has no prior conversation DB to migrate. Unknown newer schema,
unsupported upgrade, corrupt records, corrupt DB and disk-write failures stop
access without reset/deletion. A custom corruption handler prevents SQLite's
default destructive recovery. Never add fallbackToDestructiveMigration behavior.
Future migrations require version-specific fixtures; no fictitious v0 migration.

Initial logical content budget: 100 live conversations, 32 MiB combined canonical
display/replay, 1 MiB per entry, 120-character title. Existing request/attempt/context
bounds remain. At most 1,000 owner bindings and 10,000 total UUID records including
minimal tombstones; a full store fails clearly rather than silently evicting.
Tombstones prevent stale saves from resurrecting a deleted conversation.

## Identity and privacy

Random local owner UUIDs map from a SHA-256 digest of the verified registration
identity (not its tokens). API keys receive a random per-provisioning scope inside
the existing encrypted vault. Existing v1 vaults gain that optional field lazily,
without changing their secret, key revision or selected billing mode; replacement
and removal rotate/clear it. A numeric key revision alone is insufficient because
credential recovery could restart a counter. Negative tests cover old-format vaults,
failed scope persistence, stale leases and key replacement. No raw identifiers,
credentials or authenticated payloads are saved in chat metadata or diagnostics.

Chat content is app-private, not additionally encrypted by this schema. Users can
put sensitive content into prompts; do not claim credential-grade chat encryption.
Existing credential encryption and backup restrictions remain intact.

## Validation

Focused pure tests cover completed serialization/continuation, opaque replay,
interruption and Stop exclusion, malformed/future/oversized input, mismatched replay,
duplicate IDs, attempt bounds, revision failures, redaction and token-write exclusion.
ViewModel tests cover two writes despite many deltas, no inference on begin-storage
failure, no durable success on completion-storage failure and excluded Stop snapshots.

The androidTest-only `ConversationStorageInstrumentation` uses a separate device-protected test namespace in the disposable target app,
synthetic records and no credentials/network. It exercises real SQLite
recreation, interrupted/completed restore, owner isolation, stale/model-rebinding
rejection, rename/delete/no resurrection, future-version preservation and corruption
preservation. It deletes only that synthetic test DB; the normal credential-protected chat DB
and credential files are outside this namespace.
Phone-emulator storage results do not establish Wear UX, paired transfer or physical
acceptance. Exact executed outcomes are recorded in the PR after validation.

History UI, selection and startup reopen belong to D6-C. This issue supplies the
store and canonical restore primitives, not a claim that D6-C is already delivered.

### Executed gate — 2026-10-31

VERIFIED AUTOMATICALLY: final focused seven-suite run, 81 tests, zero failures
or errors; Wear assembleDebug and assembleDebugAndroidTest passed; lintDebug
passed with 0 errors and 5 warnings; staged whitespace checks passed.
The suites are ConversationPersistenceTest, DurableChatViewModelTest,
ChatConversationTest, ChatViewModelTest, ApiKeyRepositoryTest, WatchProfilesTest
and ErrorPresentationTest. No full-suite or release-build result is claimed here.

VERIFIED ON EMULATOR: the final matched APK/test pair returned explicit PASS
for the real SQLite lifecycle/rollback/owner-isolation/deletion/future-version/
corruption-preservation gate on the disposable API 36.1 phone lab. This is storage
integration evidence, not Wear screen/rotary, pairing or physical acceptance.
Initial harness attempts failed because instrumentation cannot use another UID's
private directory; the final harness preserves the supplied device-protected test
namespace. One new account-switch fixture initially attempted reauthorization
instead of explicit account addition; the corrected fixture passes without any
production authentication downgrade. A lint analyzer run during a source edit
failed internally; the final stable-source lint pass above supersedes that run.

OWNER PHYSICAL ACCEPTANCE PENDING: completed chat restart/continue (with D6-C),
Stop/interruption, account/model/billing isolation, storage-full/recovery UX and
normal/max-font history interaction. No real credential or destructive hardware
test was performed.
