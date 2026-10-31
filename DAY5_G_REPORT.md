# Day 5 Phase G — owner physical evidence — 2026-10-31

**PHYSICAL ACCEPTANCE FAILED / INCOMPLETE DUE TO REPRODUCED UX DEFECTS.**
Owner-reported real Galaxy Watch 7 observations, supplied in this chat. These
results are not agent-executed tests. D5-G remains open; D6 is NOT STARTED.

| Check | Supplied physical evidence |
| --- | --- |
| P1 | First impression acceptable; current shell acceptable. A future compact-interface preference does not authorize a redesign. |
| P2 | Active streaming, compact prompt, left-aligned answer, density and natural scrolling work. Normal completion removes the answer. Active plain text is intentional D5-C behavior. |
| P3 | Reader position preserved, no forced autoscroll; Latest appears and returns to bottom; Stop separate. Same completion disappearance. |
| P4 | Stop cancels transport; formatted partial remains with stopped/excluded copy. No retry/duplicates; responsive. |
| P5 | Normal completed Markdown disappears; terminal Markdown cannot be accepted. |
| P6 | Not exercised because completed Markdown is broken. |
| P7 | Not exercised because completed Markdown is broken. |
| P8 | PASS: model picker stable and matches desired UX. |
| P9 | Account/Settings/Diagnostics navigation good. Inference test completes, but displayed proof seems inconsistent with expected WristPrompt works.; exact mismatch/attempt not supplied. |
| P10 | PASS: error/recovery UX works. |
| P11 | Maximum-font Chat header/Menu malformed; Menu wraps/fragments and placement/hierarchy are broken. |
| P12 | Hardware responsive, mildly warm, judged normal by owner. No measured battery-life result. |
| P13 | TalkBack not exercised; outside D5-H. |

Additional defect: app-wide vertical text gaps/paragraph-like chunks within a
continuous sentence, including outside assistant Markdown.

Follow-up clarification: owner has not specifically checked navigation away/back
after disappearance, and thinks it stays missing. This is uncertain, not a
verified navigation or canonical-state result. No raw authenticated payload,
credential, or physical device inspection was requested or collected.

Targeted remediation and required retest: [DAY5_H_REPORT.md](DAY5_H_REPORT.md).
