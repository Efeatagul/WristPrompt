# Day 5 Phase A — chat-first shell — 2026-10-30

## A. Executive result

**PASS WITH KNOWN DEBT — VERIFIED AUTOMATICALLY / VERIFIED ON EMULATOR.**
D5-A only: shell, retained shallow navigation, primary actions and static Wear
visual foundation. Physical UX remains PENDING END-OF-DAY PHYSICAL TEST in D5-G.
D5-B/C/D/E/F/G and D6 have not started. No commit or push.

D5-0 was delivered in chat; no separate repository audit report existed. Read
AGENTS, SPEC, ROADMAP, relevant DECISIONS, Day 3/4 reports/status, scenario guide,
production shell/theme/input/state wiring and relevant regression tests before
editing. Day 4 remains PASS WITH KNOWN DEBT. Existing uncommitted D4-H changes
were preserved byte-for-byte, including the untracked sign-out integration test.

## B. Before / after hierarchy

Before: fixed billing banner + Compose/Send/Stop above the list; generic chat
contained New Chat, model, connection and Details buttons. Proof/output/details
occupied multiple prominent routes. Navigation Back always jumped to Chat, and
re-entering a keyed list could lose its reading position.

After: scrolling title/model/compact billing/Menu header; measured Ask or Send
below the viewport, replaced by Stop whenever existing work is busy. Menu owns
administrative destinations. Diagnostics retain all existing proof/details
capability, with proof output inline. Message text alignment, 80-character chunks,
spacing, terminal/context labels and error guidance are intentionally unchanged.

## C. Final navigation map

```
Chat
├── Models
├── Composer / explicit draft review
└── Menu
    ├── New Chat
    ├── Account & Connection
    └── Settings & About
        └── Diagnostics
            └── Inference proof + inline proof output
```

Models entered from Chat returns to Chat. Other Back actions return along the
visited path; direct recovery routes return to their caller. Revisiting an
ancestor truncates the path rather than accumulating duplicates. OUTPUT is an
internal compatibility alias to proof, with no separate menu entry.

The same ShellNavigationViewModel retains destination, bounded return path,
input marker and per-surface reading anchors through retained Activity recreation.
Fresh ViewModelStore/process starts Chat with fresh reading state. No navigation
library, SavedStateHandle, database, file or conversation restoration was added.
Welcome notice has separate scroll state so it cannot displace Chat's anchor.

## D–E. Chat shell and action policy

- Empty: title, authoritative model, route disclosure, Menu and restrained Ask
  invitation. Setup/model problems retain actionable secondary navigation.
- Idle conversation: existing transcript; header scrolls away. Ask stays reachable.
- Draft review: existing draft and edit/discard controls; fixed route disclosure
  immediately above explicit Send, including when a long draft is scrolled.
- Streaming/catalog/proof busy: fixed Stop on Chat and every secondary surface.
  No forced autoscroll or new animation. Existing generation cancellation owns it.
- Secondary navigation: no generation, catalog reload or reset. New Chat is the
  explicit exception, calling the existing reset/cancellation behavior.

Ask launches the existing RemoteInput flow when draft is empty. A retained draft
returns to review without being replaced. Results still update draft only;
revision, single-use, recreation disposal and separate Send remain unchanged.
The actual platform chooser offers keyboard/voice without a custom voice stack.

## F. EdgeButton decision

**REJECTED for D5-A.** The pinned API supports the scaffold slot, but its morphing,
scroll-dependent visibility is unnecessary for the chosen consistently reachable
Ask control. A conventional content-sized Material Button keeps a minimum 48 dp
control target, measured outside scrolling content. Stop uses the same reliable
placement. No custom animated substitute or dependency was added.

## G–H. Theme and billing

WristPromptTheme centralizes OLED black, neutral dark surfaces, white foreground,
restrained teal and native Material 3 typography/shapes. Production MainActivity
and synthetic debug host share it. No fonts/assets, gradients, dynamic effects,
theme setting or continuous animation was added.

Chat shows Using ChatGPT plan or OpenAI API · API billing adjacent to its model.
It scrolls with the header. Composer repeats the current explicit disclosure in
the measured Send area. Unknown/loading/storage-error modes never infer a route.
First-use plan confirmation and existing explicit mode controls remain.

At fontScale > 1.2, the header stacks title/Menu/model/billing in lazy rows. The
initial 2x test caught clipping in a compact TextButton; the final model control
uses a content-sized native Button and the large-font layout stacks instead of
shrinking type. Primary controls remain minimum 48 dp. Answer rendering is not
changed by the responsive shell.

## I–J. Account/settings/diagnostics and production changes

| File | D5-A change |
| --- | --- |
| wear/MainActivity.kt | Use centralized Wear theme; same ViewModels/owners. |
| ui/WearShell.kt | Full viewport routing; narrow busy/Send projections; retained reading anchors; one primary action; inline proof output. |
| ui/ChatScreen.kt (new) | Scrolling adaptive header and composer shell; current transcript collection/cache moved into the chat subtree. |
| ui/ShellNavigationViewModel.kt | Bounded Back path and primitive reading anchors; fresh-owner reset. |
| ui/ShellInteraction.kt (new) | Testable shell event wiring to existing input/reset/send/stop owners; pure primary-action/billing presentation. |
| ui/WristPromptTheme.kt (new) | Static Wear Material 3 palette. |
| ui/ConnectionItems.kt | Heading renamed Account & Connection only. |

Paths above are under wear/src/main/kotlin/io/github/grassified/wristprompt.
No OpenAI/provider/session/conversation/credential/bridge implementation changed.
Connection retains sign-in, account selection/addition/sign-out, API setup/change/
removal, explicit route and Manage usage. Diagnostic fields stay allowlisted.

Chat's complete state is read in ChatScreen, draft in ComposerScreen. Only a
distinct busy projection reaches the shell; Send uses a distinct eligibility
projection. Security gating remains current. Existing account/setup flows remain
low-frequency shell inputs. No sampling, token coalescing, parser or network
observer was added. Proof-only streaming remains on the existing proof path.

## K. Lightweight performance baseline / comparison

Named WristPrompt_Watch7 only, debug APK, synthetic data, same five workloads.
Before layout editing, installed the existing D4 APK because the snapshot's
installed package lacked the canonical scenario Activity. Data/signing preserved.
No Android Studio or benchmark dependency. Each workload launches a fresh fixture,
resets gfxinfo, waits for readiness, samples memory/frames, and uses six identical
touch swipes for long output/streaming/models. Wake emulator before each launch.

The first baseline attempt was interrupted during input injection; a complete
five-case rerun is the baseline. Timings are retained-process Activity/fixture
readiness, NOT cold app startup, first usable frame, release or battery evidence.

| Synthetic workload | Ready ms before → after | Janky frames before → after | PSS MiB before → after |
| --- | --- | --- | --- |
| EMPTY_CHAT | 243 → 228 | 1 (4.55%) → 2 (9.09%) | 78.0 → 87.0 |
| ONE_TURN_COMPLETED | 189 → 244 | 1 (4.35%) → 2 (6.67%) | 78.0 → 85.8 |
| LONG_ASSISTANT_OUTPUT | 252 → 251 | 3 (1.60%) → 3 (1.56%) | 78.0 → 86.2 |
| STREAMING_LONG | 179 → 231 | 7 (3.98%) → 6 (2.86%) | 78.0 → 85.5 |
| MANY_MODELS | 248 → 230 | 9 (4.31%) → 6 (3.23%) | 78.0 → 88.1 |

Final anchor-only implementation pass: /tmp/d5a-after-final/summary.json. One-turn
and streaming readiness rose by 55/52 ms in this pass while empty/models fell;
there are too few trials and differing frame counts to establish causality or
meet a statistical regression budget. PSS is consistently higher (about 85–88
MiB versus 78 MiB); this remains an explicit D5-F memory/performance debt. The
anchor-only navigation change avoids retaining disposed layout objects but is
not claimed to explain or eliminate the PSS difference.

Initial D5 candidate measurements showed PSS approximately 83–104 MiB versus about 78 MiB in
the before pass, with falling values across later workloads. Code/native/Java and
GC/warmup state are confounders; no causal explanation or memory-regression pass
is claimed. The bounded comparison cannot establish battery cost or absence of a
leak. D5-F must investigate repeatable memory growth and use matched profiles.
No hidden idle timer/background architecture exists in the source changes.

Debug host writes ACKs/has extra collectors; it is not release-performance truth.
Raw temporary evidence: /tmp/d5a-before, /tmp/d5a-after and final-pass paths below.
Temporary artifacts are not durable evidence; this report preserves summary data.

## L. Automated verification — VERIFIED AUTOMATICALLY

| Check | Result |
| --- | --- |
| Full Wear JVM suite | 358 tests / 46 suites; 0 failures/errors/skips (343 baseline + 15 new). |
| Focused shell/lifecycle/reconstruction/composer/sign-out/HTTP cancellation | 64 tests / 8 suites; 0 failures/errors/skips. |
| ShellInteractionTest (new) | 9 tests. |
| ShellNavigationViewModelTest | 8 tests (2 existing + 6 new). |
| Wear assembleDebug / assembleRelease | PASS / PASS; release unsigned, no publication. |
| Wear lintDebug | 0 errors / 6 pre-existing warnings. |
| Source/manifest/DEX debug isolation | PASS. |
| Python tools tests | 40 passed; existing tools unchanged. |
| Python AST | 13 files passed. |
| git diff --check / changed-new text | PASS; 18 text files checked, including four preserved pre-existing files. |
| Debug APK signing fingerprint | Matches the existing DEVELOPMENT.md SHA-256 ending 79d37061. |
| Existing D4-H user work | Exact starting diff and untracked sign-out test preserved. |

Full mobile suite not rerun: no mobile/shared source/dependency changes, as allowed
by this phase's instruction. Day 4's 70-test mobile result remains historical.

Commands actually run with DEVELOPMENT.md's existing JDK/SDK/cache/signing env:

```sh
./gradlew :wear:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :wear:lintDebug --console=plain
./gradlew :wear:testDebugUnitTest --rerun \
  --tests '*ShellInteractionTest' --tests '*ShellNavigationViewModelTest' \
  --tests '*ShellPresentationTest' --tests '*ComposerInteractionTest' \
  --tests '*LifecycleOwnershipTest' --tests '*ProcessReconstructionTest' \
  --tests '*SignOutPresentationTest' --tests '*StreamingHttpCancellationTest' --console=plain
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_scenario_isolation.py
git diff --check
```

Full counts were captured before focused filtering replaced test XML. Focused
suites: interaction 9, navigation 8, presentation 12, composer 6, lifecycle 8,
process reconstruction 11, sign-out presentation 2, HTTP cancellation 8.
Initial lint found two unobserved composition StateFlow.value reads; fixed via
remembered source/initial snapshots, not suppression. Full final validation passed.

## M. Emulator acceptance — named WristPrompt_Watch7 only

| Check | Disposition / evidence |
| --- | --- |
| All 43 canonical fixtures | PASS — VERIFIED ON EMULATOR: fresh ACK/readiness, matching PID, resumed host; not 43 visual acceptance claims. |
| EMPTY_CHAT, CHATGPT_AUTHENTICATED, API_KEY_CONFIGURED | PASS — VERIFIED ON EMULATOR: scrolling header, route disclosure, Ask. |
| ONE_TURN_COMPLETED, MULTI_TURN_CHAT | PASS — VERIFIED ON EMULATOR: existing transcript under shell. |
| STREAMING_LONG, STOPPED_PARTIAL | PASS — VERIFIED ON EMULATOR: reading viewport, fixed Stop/Ask and partial exclusion. |
| MANY_MODELS, MODEL_UNAVAILABLE | PASS — VERIFIED ON EMULATOR: picker/reload reachable; rows unchanged. |
| SIGNED_OUT, REAUTH_REQUIRED_PHONE_ABSENT | PASS — VERIFIED ON EMULATOR: account/recovery screen; synthetic fixture only. |
| ERROR_429 | PASS — VERIFIED ON EMULATOR: existing error/context guidance retained. |
| RECREATED_COMPLETED_STATE | PASS — VERIFIED ON EMULATOR: retained completed fixture under shared theme. |
| Chat → Models → Back | PASS — VERIFIED ON EMULATOR: viewport/conversation retained, no reload/send. |
| Chat → Menu → Account → Back → Chat | PASS — VERIFIED ON EMULATOR. |
| Menu → Settings → Diagnostics → Back chain | PASS — VERIFIED ON EMULATOR. |
| Ask → platform chooser/keyboard | PASS — VERIFIED ON EMULATOR. |
| Native keyboard confirmation → returned draft | BLOCKED — EMULATOR LIMITATION / CAUSE UNRESOLVED: system confirmation retained text in a collapsed 2-pixel panel; cause unresolved. Not counted as app draft/Send acceptance. |
| Explicit Send / stale-result rejection | VERIFIED AUTOMATICALLY via production event adapter/composer/session tests; no rendered native-result PASS inferred. |

Final targeted checks on the final build passed: Models return preserves visible
viewport and completed conversation with discoveries=1/generations=1; Menu New
Chat clears messages without replay; Stop from Settings cancels/excludes the
active synthetic attempt; actual pending-composer Activity recreation increments
creations to 2 while draft/generations remain zero; at 2x font the model affordance
remains reachable by scrolling and Stop yields CANCELLED/excluded/busy=false.
Deep reading was separately exercised on the final build: 20 gated synthetic
chunks, actual rotary scroll, then real Activity recreation. The visible viewport
was identical afterward, creations >= 3, generations=1, and the same request was
still busy; explicit Stop ended it. Font scale checked at 1.0. Log:
/tmp/d5a-reading-recreation.log.

Logs: /tmp/d5a-targeted-final.log, /tmp/d5a-final-font-rotary.log and
/tmp/d5a-final-catalog.log. All final catalog launches used the same installed APK.
 Generic catalog launch
is not a substitute for interaction testing. No physical auth or CapabilityClient
investigation was performed; the earlier emulator/GMS limitation remains separate.

## N. Accessibility / rotary / large font

PASS — VERIFIED ON EMULATOR: actual rotaryencoder SCROLL events move Chat, Models,
Account & Connection, Settings and Diagnostics. Existing shared scaffold/list
state supports it; no nested vertical list was introduced.

2.0 font-scale stress exercised empty/completed Chat, Ask, Models, Account,
Composer, Menu, Settings, Diagnostics and streaming Stop. Content scrolls; controls
retain native scaling and non-color-only text. Font setting restored to 1.0.
This is an emulator stress setting, not proof of Galaxy Watch maximum-font UX.
TalkBack action labels/model destination semantics reviewed; no actual TalkBack
session or physical accessibility pass is claimed.

## O. Release / security invariants

No credential routing/fallback, OAuth, credential format/storage, Data Layer,
inference/session ownership, protocol/replay, refresh/sign-out, dependency or
signing changes. store=false, explicit input history, no previous_response_id,
completed-only replay, partial exclusion and actual transport cancellation remain.
No conversation/draft persistence, Room, Markdown, new provider, web/tools/MCP,
images, custom speech/TTS, polling, retry loop, service, WorkManager or wake lock.
D4-H signed-out identity redaction unchanged. Debug theme/host remain debug-only;
source/manifest/DEX checks pass. No physical action or real inference request.

## P. Known debt / phase boundaries

- D5-B: dense/left-aligned answer rendering, 80-character chunk removal, streaming
  coalescing and Latest. None implemented here.
- D5-C: Markdown/code/tables/links. Not implemented.
- D5-D: model/account/settings row redesign; ONLY authoritative name + selected
  state in model rows, no speculative metadata. Existing rows preserved.
- D5-E: error/recovery copy. Existing guidance still says Connection / settings;
  the actual destination is now under Menu → Account & Connection.
- D6: persistent transcript/replay architecture, storage selection/implementation.
  No storage abstraction/schema chosen here.
- D5-F: release-comparable performance and memory characterization; no battery-life
  improvement or absence of regression inferred from this debug smoke.
- Native keyboard confirmation roundtrip remains unverified on this emulator;
  input launch and deterministic result/revision/Send tests pass. No speculative
  platform input change made.
- Day 4 debt unchanged: Terra-specific restoration and historical reauthorization,
  natural phone-absent token refresh, funded BYOK evidence, destructive storage
  and full release/hardware gates. Nothing here claims those resolved.

## D5-G concise deferred physical checklist

**PENDING END-OF-DAY PHYSICAL TEST — no hardware action in D5-A.**

- Default/max-font Chat header/Ask, long reading and model affordance.
- Touch/rotary and Back through Models, Account, Settings and Diagnostics.
- Keyboard return → draft review → explicit Send; recreation discards stale input.
- Fixed Stop while reading and on secondary screens; honest partial state.
- Plan/API disclosure before Send and useful setup/recovery routes.
- Ordinary memory/latency sanity on Galaxy Watch 7; later release comparison.

No sign-out, key removal, revocation, forced expiry, data clearing, uninstall or
usage exhaustion is part of this checklist without exact separate permission.

## Q. Files changed in D5-A

Production: MainActivity.kt; ui/ChatScreen.kt (new), ConnectionItems.kt,
ShellNavigationViewModel.kt, ShellInteraction.kt (new), WearShell.kt,
WristPromptTheme.kt (new). Debug: DebugScenarioActivity.kt (theme only).
Tests: ShellNavigationViewModelTest.kt; ShellInteractionTest.kt (new).
Docs: DAY5_STATUS.md (new), ROADMAP.md, DECISIONS.md, WEAR_SCENARIOS.md.

Pre-existing, not changed by this phase: WatchProfiles.kt, ShellPresentation.kt,
ShellPresentationTest.kt and untracked SignOutPresentationTest.kt.

## R. Suggested commit

`feat(wear): introduce chat-first shell and shallow navigation`

No commit/push. Only D5-A is implemented. Do not start D5-B automatically.
