# ROADMAP.md
# Secmail Assistant Mobile Roadmap

## P0 — v0.7 runtime stabilization

Do not start major redesign until these pass:
1. v0.6.1 → v0.7 overwrite install
2. DB retained
3. collector works
4. Mail Pack generated
5. Mail Pack shared to ChatGPT
6. GPT result JSON imported
7. correct mail/result mapping
8. Reply/Action/Schedule/FYI distribution
9. recommended reply visible/copyable
10. no regression in translation/search/delete/Waiting/Done

## P1 — Repository normalization for Codex

Move from ZIP-as-primary-source to tracked source tree.

Goal:
- Codex can inspect/edit/build normal source directly
- Git diff reflects real code changes
- tags/releases replace source ZIP as primary history

Preserve old ZIPs until verified.

## P1 — Batch GPT UX refinement

Reduce friction:
- clearer export/import status
- schema/version validation
- missing-ID diagnostics
- partial import behavior
- source snippet with AI recommendation
- identify already analyzed vs updated mail

## P1 — Action Inbox quality

Improve:
- Today view
- reply/action separation
- waiting lifecycle clarity
- customer/project/thread grouping
- priority confidence
- explainable reason

## P2 — Architecture cleanup

Only after runtime stabilization:
- reduce MainActivity responsibilities
- separate UI/view-model/service-like orchestration
- make GPT schema shared with PC
- tests for JSON import and mail classification
- DB migration tests

## P3 — Sent / follow-up intelligence

If Sent folder becomes reliably accessible:
- sent-side thread matching
- automatic Waiting
- reopen on new inbound reply
- follow-up aging

Do not fake this before data is reliably available.

## P4 — Direct mail integration

Only if authorized and technically available:
- replace Accessibility collector
- retain normalized schema, GPT result schema and Action Inbox
