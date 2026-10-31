# D6/D7 implementation backlog

D5 is PASS WITH KNOWN DEBT — COMPLETE. D6-A–C are merged through #16/#17;
D6-D acceptance and D7 implementation remain pending. This document is planning,
not permission to implement subsequent phases.

[Live open backlog](https://github.com/grassified/WristPrompt/issues?q=is%3Aissue%20is%3Aopen).
Issue titles below are canonical duplicate-detection keys. Resolve the live issue by
its exact title before creating another issue.

| Task / exact issue title | Suggested branch | Depends on |
| --- | --- | --- |
| D5 debt: render Markdown tables as Wear-friendly structured records | `d5/table-records` | Independent; table debt does not block D6 |
| D6-A: audit and design persistent conversation architecture | `d6/a-persistence-audit` | Independent; table debt does not block D6 |
| D6-B: implement durable conversation and message storage | `d6/b-storage` | D6-A |
| D6-C: add conversation history, reopen, continue, rename and delete | `d6/c-chat-history` | D6-B |
| D6-D: persistence lifecycle, migration and recovery acceptance | `d6/d-persistence-acceptance` | D6-B, D6-C |
| D7-A: persistent custom prompt presets on phone | `d7/a-prompt-presets` | D6-A |
| D7-B: sync custom prompt presets to watch and insert them into composer | `d7/b-prompt-watch-sync` | D7-A |
| D7-C: import and synchronize a multi-image WristPrompt photo library | `d7/c-photo-library` | D6-A |
| D7-D: support text plus multiple image attachments in Responses inference | `d7/d-image-input` | D7-C, D6-B |
| D7-E: integrate prompt presets with multi-image composer UX | `d7/e-composer-integration` | D7-B, D7-D |
| D7-F: optional image generation and zoomable image viewer | `d7/f-image-generation` | D6-D, D7-E |
| D7-G: final integration, physical acceptance and Monday alpha readiness | `d7/g-alpha-readiness` | D6-D, D7-E, D7-H |
| [D7-H: provider-native thinking/reasoning controls](https://github.com/grassified/WristPrompt/issues/18) | `codex/d7-h-thinking-controls` | Merged D6-B, D6-C; independent of photo library/presets |

D7-A and D7-C can begin after D6-A establishes persistence/attachment boundaries.
D7-F waits for stable core persistence/presets/multi-image input and never gates G.
D7-G (#12) requires all core D6-A–D, D7-A–E and D7-H (#18). Pin/favorite is optional only if trivial.

Monday 2026-11-02 priority: stability → persistent chats → custom prompts →
multi-image input → provider-native thinking controls → integration/release sanity → image-generation stretch.

Use feature branches and issue-scoped PRs, never implementation directly on main.
Document actual tests, with automated/emulator/owner physical evidence separate.
No physical PASS without owner results. No unrelated phases in a PR; no automatic
external publication or destructive owner-device tests.
