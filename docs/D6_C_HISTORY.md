# D6-C conversation history

Issue #4, rebuilt on merged D6-B (#16, superseding #14), including the
completion-storage-failure display recovery. Original D6-C implementation: @Efeatagul.

## Behavior

Menu > Chats lists at most 100 saved conversations belonging to the selected local
owner. Open restores canonical display and separate validated completed replay;
continuation always requires explicit Send, current credential ownership, matching
billing route and original model. Incompatible chats open read-only without selecting
a model, account or billing path. Changing a model retains the existing reset
convention; explicitly reopen the matching saved chat afterwards.

New Chat resets only the current view/draft, not saved history. Renamed titles
survive recreation. Delete presents explicit scope/confirmation, removes that chat's
content and retains the D6-B minimal tombstone against resurrection. Deleting the
active chat clears its view. Stale input/open/rename results cannot change another
account's history or replace a subsequently created chat.

Startup restores the latest local chat inertly; pending attempts become excluded.
Local verified account identity resolves before model discovery, so history can
remain readable offline. Signing out or changing owner hides the previous history.
No credential refresh/network is required merely to obtain this local identity;
normal send/catalog ownership checks are retained. An offline or incompatible
conversation is read-only until the original model can be validated normally.
Unsent drafts and active requests do not survive process death.

## UI and bounded work

Native Wear Material 3/lazy ShellList, existing font scaling and rotary ownership;
no navigation redesign, WebView, automatic request, scroll or model fallback.
Rename uses the existing native text-input contract with a distinct Chat name label
and a single-use pending target. Missing input activity produces a local error.
Read-only reason/busy observation is distinct-filtered, preserving D5's coalesced
transcript pipeline rather than recomposing it for each token.

A writer barrier makes history reads/rename/delete wait for queued terminal saves,
including immediate Stop followed by reopen; its regression test passes.

History metadata is bounded/validated before display, including truncated SQL
projection for malformed oversized metadata. Database IO remains off Main.
`SAVED_CHATS_HISTORY` is a debug-only, purely in-memory 25-chat fixture. It has no
Android/credential storage or HTTP transport; the real SQLite gate stays separate.
The catalog has 84 scenarios; historical D5-H reports remain unchanged.

## Executed evidence — 2026-10-31

VERIFIED AUTOMATICALLY:

- First focused history/storage/navigation run passed. New history suite: 9 cases
  for explicit continuation, restart/rename/delete, wrong model, wrong billing,
  offline reading, New Chat, owner changes, stale open and oversized rename.
- Final narrow run: 47 tests / 4 suites, zero failures/errors (history 10, debug
  fixtures 20, prior physical-remediation regressions 8, shell interaction 9).
- Wear assembleDebug, assembleRelease and lintDebug pass. Lint has 0 errors and
  5 warnings. An earlier new StateFlow composition warning was fixed by observing
  the distinct flow without reading `.value` inside composition; no lint suppression.
- Python tooling: 40 tests pass using UTF-8 mode and a writable disposable temp
  folder. One POSIX-only expected path in a mock test now uses pathlib for Windows.
- Source/manifest/debug/release DEX isolation passes for all 84 identifiers.
- git diff --check passes. No new dependency or credential-format change in C.

VERIFIED ON EMULATOR (disposable API 36.1 phone lab, not a Wear visual claim):

- Real SQLite gate re-ran with the D6-C app APK: explicit PASS for durable lifecycle,
  revision rollback, owner isolation, deletion, future schema and corrupt-file
  preservation. Test storage is a separate synthetic namespace.
- Actual UI launch into 25 synthetic chats; open displays the selected saved
  question and native Markdown answer. Menu returns to history.
- Delete confirmation is visible; Cancel preserves the selected chat; confirmed
  delete removes only that row and leaves the adjacent saved chat available.
- Readiness reports remain `generations=0`, with zero companion requests/lookups.
  No authenticated or real API request occurred.

PENDING: Wear round-screen normal/max-font clipping/rotary and native rename input
on a watch. A fresh official Wear image is being provisioned under the already
accepted SDK license. Batch this with D6-D/integration acceptance; phone screenshots
do not establish Wear UI or physical behavior. Owner physical acceptance remains
pending, including restart/reopen/continue and account/model/billing boundaries.
