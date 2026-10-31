# D6-A: durable conversation architecture

2026-10-31. Issue #2. Design only; no storage implementation or device acceptance.
Audited baseline: `e9c946e0824e563a984f8c018108713e934195da`.

## Existing owners and failure paths

- `wear/.../openai/ChatConversation.kt` owns mutable canonical memory state,
  draft, attempt count, local Long message IDs, pending user and separate JSON
  replay history. `begin` adds a pending display user; `progress` changes only
  partial text; `complete` validates output items, appends a whole replay exchange
  and commits completed display messages. `fail` excludes the user from replay
  and retains partial display text. `reset` discards conversation memory.
- `InferenceViewModel.kt` owns that conversation and StateFlows. It serializes UI
  work with an operation Job, epoch and inputRevision. New Chat cancels work and
  resets memory. Access changes and selected-model changes clear context; catalog
  restoration also clears incompatible context. `onCleared` invalidates callbacks
  and cancels active work. Activity recreation retains the ViewModel; process
  death loses everything. No restart currently resends a request.
- `InferenceSession.kt` owns the generation mutex and active Jobs. It validates
  credential client, generation, explicit billing mode and mode revision before
  dispatch and validates ownership throughout streaming. Credential refresh may
  refresh discovery; it does not change billing or retry inference automatically.
- `ChatPresentationOwner`/Markdown projections are disposable derived state.
  Never serialize Compose rows, parser objects, scroll positions or caches into
  the canonical conversation schema.
- `shared/.../storage/EncryptedRecord.kt` protects credentials with Keystore,
  AES-GCM and AtomicFile in no-backup storage. Keep this format/key lifecycle
  unchanged. It has a roughly 1 MB bound and is not a conversation database.
- Both backup exclusion policy and Wear manifest must continue excluding local
  data. Existing `allowBackup=false` is not permission to loosen extraction rules.

DISPLAY TRANSCRIPT != RESPONSES API REPLAY CONTEXT. Assistant display text cannot
reconstruct opaque validated reasoning/encrypted continuation items.

## Decision and alternatives

Use platform SQLite through `SQLiteOpenHelper`, a small repository interface,
manual injection and a serialized off-Main dispatcher. Use transactions and
foreign keys; bind all values, never concatenate user strings into SQL.

Room offers compile-time query checking and migration support and is Android's
general recommendation. For this bounded two-app project, platform SQLite avoids
introducing Room/KSP and new dependency/version/license resolution. This tradeoff
requires real SQLite integration tests; fake repositories alone are insufficient.
Reconsider Room if queries/schema complexity grows. Do not downgrade toolchain pins
to make a new library build.

Preferences DataStore remains appropriate for small non-secret selection hints,
not transcript collections, referential integrity or indexed history. An atomic
JSON file per conversation could commit whole snapshots but makes paging, global
quotas and attachment reference accounting harder; reject it for the durable core.
Do not reuse the credential record and rewrite all conversations on each change.

No new runtime library is selected. Platform APIs introduce no new linked Maven
artifact. Preserve GPL-3.0-only plus the exact existing linking exception and all
notices. Any later Room/SQLite-driver/encryption-library addition requires a fresh
artifact/version and transitive license review; this decision grants none.

Primary references reviewed for this decision:
[SQLiteOpenHelper](https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper),
[Room](https://developer.android.com/training/data-storage/room),
[DataStore](https://developer.android.com/topic/libraries/architecture/datastore).

## Schema version 1

Use stable random UUIDs for durable entities; never use a wall clock as an ID.
Ordering uses monotonically increasing per-conversation sequence numbers, not time.
Dates are metadata only. All constraints are also validated when decoding records.

| Record | Required fields and relationships |
| --- | --- |
| conversation | id PK, title (1..120 characters), createdAt, updatedAt, revision, ownerScope, billingRoute, modelSlug, replayFormatVersion, attemptCount |
| exchange | id PK, conversationId FK cascade, ordinal UNIQUE within conversation, terminalStatus, createdAt |
| message | id PK, exchangeId FK cascade, ordinal UNIQUE within exchange, role USER/ASSISTANT, status COMPLETED/EXCLUDED/INTERRUPTED |
| content_part | messageId FK cascade, ordinal composite PK, kind TEXT/IMAGE_ATTACHMENT, text or attachmentId FK; exactly one variant |
| replay_exchange | exchangeId PK/FK cascade, version, ordered canonical input/output JSON arrays; completed whole exchanges only |
| attachment | id PK, safe relative file ID, sha256, mime, width, height, byteCount, origin, state STAGING/READY/DELETING, createdAt |
| preset (D7-A) | id PK, name, text, revision, deleted tombstone, libraryRevision; phone-owned bounded records |

Indexes: conversations `(ownerScope, updatedAt, id)` for bounded keyset history;
exchange `(conversationId, ordinal)`; message/part ordered parent indexes; attachment
references indexed for cleanup. No raw credential, token, key or external URI is
stored here. Preset table belongs to D7-A's migration, not D6-B implementation.

`Text(text)` and `ImageAttachment(attachmentId)` are sealed canonical content parts.
Image metadata lives separately; binary/base64 never goes in a database row. Reject
unknown part/replay versions rather than silently dropping context. Display-only
excluded partials use the same parts but have no replay row. Preserve JSON item
arrays semantically, including validated opaque items; do not flatten them to text.
Validate reloaded replay through a shared allowlist matching `complete` before use.

## Ownership and continuation

Create a random local ownerScope associated with each protected registration or
API-key installation, without deriving it from secret bytes or logging an account
identifier. Persist the association separately from conversation text. Token refresh
does not change this identity. A newly provisioned credential must not adopt an old
scope just because a model name matches. Missing/ambiguous bindings fail closed.

Conversation records retain ownerScope, explicit billing route and exact model slug.
Before reopen-for-continuation, resolve current credentials and catalog and compare
all three. Runtime generation and modeRevision still gate asynchronous work; they
are not persistent identity and must not be used to authorize a restored request.
Mismatches allow local read-only history with a clear reason; no automatic route,
account or model selection. Signing out cancels active work and hides that owner's
history from active use; it does not silently delete durable chats. Local deletion
is an explicit separate action. Restart never restores a credential lease or Job.

## Transaction and lifecycle boundaries

1. Create metadata lazily for a real conversation. Draft edits and token progress
   do not write the database. Keep drafts transient for D6; process death discards
   an unsent draft with a clear history/composer state, never sends it.
2. At begin, atomically record an attempt marker and user content as INTERRUPTED
   candidate, with no replay. This costs one write per attempt and permits truthful
   process-death display. No assistant streaming tokens are persisted.
3. At `response.completed`, construct/validate an immutable completion candidate.
   One transaction updates the user status, inserts assistant typed parts and replay
   exchange, updates counters/revision and clears the marker. Publish durable success
   only after commit. Rollback leaves the previous complete replay untouched.
4. Stop immediately cancels HTTP, independently of the writer. Its bounded terminal
   snapshot may be saved once as EXCLUDED display-only. EOF/error is not completion.
   A crash before this write restores the attempt as INTERRUPTED without lost
   completed exchanges. No replay row is created for either case.
5. Serialize terminal operations. If Stop/access change wins before the commit
   boundary, reject the stale completion. A committed exchange is immutable;
   later cancellation does not undo it. UI publication still checks epoch/context.
   Durable commit must not be cancelled midway by a navigation Job: use a bounded
   repository-owned transaction, not detached unbounded background work.
6. Startup recovers attempt markers as display-only interrupted records. Rebuild
   memory from typed display records and separate validated completed replay.
   Revalidate ownership before continuation. Never restore STARTING/RECEIVING or send.

Repository mutations accept expected conversation revision and owner binding;
rename/delete/completion collisions cannot resurrect deleted records. All database
open/read/write/migration and file IO execute off Main. UI observes immutable
snapshots; no foreground service, WorkManager, polling or per-token writes.

## Attachments, budgets and deletion

Files live under app-private no-backup storage, addressed by generated safe IDs,
never incoming paths. Stage bounded validated bytes, flush/rename to final private
file, then transactionally reference them. A crash before the reference leaves an
orphan for bounded startup cleanup; never reference a half-written file. D7-C must
set/test image decode, dimension, byte, library and transfer limits before shipping.
No broad storage permission, automatic upload or MessageClient image payload.

Preserve current 8,192 prompt-character, 12-attempt and 196,608 context-byte bounds.
Initial durable limits: 100 conversations and 32 MiB canonical transcript/replay
data per app, checked transactionally; title 120 characters. Refuse new writes with
an actionable storage-full message, never evict history silently. D7 must account
for actual serialized image payload bytes as well as text/context limits.

Delete conversation rows and references in one transaction. File unlink follows
commit only if neither a library item nor another message nor an active draft/send
lease references the file. File failures leave a retryable deletion marker. Cleanup
is bounded at startup and after explicit deletion/import, not a repeating worker.
Missing/corrupt attachments display an unavailable marker and block continuation;
never silently omit an image from replay. Library deletion must retain chat-owned
files or clearly reject removal while referenced.

## Migration, privacy and recovery

Use explicit incremental `onUpgrade` migrations in a transaction, test every
supported starting version with real database fixtures. There is no existing
conversation database to migrate in D6 v1; do not fabricate a v0 import. Preserve
credential and DataStore formats. Unknown newer versions open no writable store;
show unsupported-version recovery, never destructive downgrade/reset.

SQLite's default destructive corruption handler is unsuitable. Install an explicit
error handler that preserves DB/journal/WAL files, closes access and reports a
sanitized storage error. Do not delete/recreate on open/read failure. Quarantine or
reset requires an explicit recovery action with data-loss scope; credentials remain
untouched. Disk-full, malformed rows and interrupted migration fail closed. No
authenticated payloads, preset text, image bytes or exceptions containing them in logs.

Conversation text/images are app-private local data, not encrypted by this proposed
SQLite schema. Communicate this privacy boundary; do not advertise credential-grade
Keystore encryption for chat history. Keep backup/cloud/device-transfer exclusions
for database and files. No automatic cloud conversation sync. Do not store secrets
in conversation metadata; users may intentionally put sensitive text in prompts.

## Implementation and negative-test gates

- D6-B: pure typed codec/validation, real SQLite repository and migration fixtures,
  manual container injection, completion candidate and attempt/terminal integration.
  Test create/reopen/repository recreation, opaque replay fidelity, excluded attempts,
  crash before/after commit, rollback/disk-full, no token writes, limits, invalid rows,
  future version, deletion races, binding ambiguity and corruption preservation.
- D6-C: bounded history, New Chat, rename/delete, read-only incompatible reopen,
  safe continue; test stale selection callbacks, account/model/billing changes,
  process restart, empty/long history, large font and rotary separately.
- D6-D: consolidated migration/lifecycle/force-stop and am-kill emulator evidence;
  credential lifecycle regression, retained completed replay, attachment orphan
  fixtures and storage cleanup. Real SQLite and device filesystem results are
  required in addition to deterministic fake-owner tests.
- D7-A/B: bounded phone presets, revisions/tombstones and safe peer transfer;
  draft Insert/Replace/Cancel; no inference until explicit Send.
- D7-C/D/E: private imported image library, safe transfer and shared inference
  serialization; authoritative supported-route validation; combined reviewed draft.
- D7-G: full relevant tests/builds/lint/release isolation and truthful owner physical
  checklist. Optional generation and independent D5 table debt stay separately tracked.

## Evidence and current blockers

This is a source audit and architecture proposal, not a completed D6 implementation.
No emulator scenarios, lint or APK assembly ran for this documentation edit.
No physical result is claimed. Owner physical acceptance remains pending.
The connected contributor account has pull-only access upstream, so upstream merges
require the maintainer. The contributor fork and upstream PR #13 now exist.
Java 17 was downloaded from Eclipse Temurin and SHA-256 verified. SDK 37 was
downloaded from Google's official repository and SHA-1 checked against its package
metadata. Its current SDK license differed from the existing accepted hash; the
contributor explicitly read and accepted the terms before workspace setup.
`gradlew.bat --version` passed with Gradle 9.6.0 and Temurin 17.0.20.1.
A focused baseline `gradlew.bat :wear:testDebugUnitTest --tests '*ChatConversationTest'
--console=plain --no-daemon` passed, compiling Wear main and unit-test sources.
This validates existing memory-only chat behavior, not the proposed persistence.
Preserve project pins rather than downgrading them.
