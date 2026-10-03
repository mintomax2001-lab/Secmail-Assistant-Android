# Secmail Assistant v0.7 — Batch GPT Pack + Result Import

## Product change
v0.7 keeps the validated v0.6.1 collector and adds a PC-style post-collection flow:

`Secmail Inbox → Accessibility collection → Local DB → MAIL_PACK.json → ChatGPT batch analysis → JSON result import → Action Inbox → recommended reply → user sends in Secmail`

No OpenAI API key is used. The user explicitly chooses the mail range and shares the generated JSON file to the ChatGPT Android app.

## New in v0.7

### 1) GPT 전체분석 card
The main screen now has `GPT 전체분석 · NO API` with:
- `GPT 분석팩 만들기`
- `GPT 결과 가져오기`
- analyzed / unanalyzed counts
- latest imported Inbox Brief preview

### 2) Batch scope selection
`GPT 분석팩 만들기` lets the user choose:
- 미분석 메일
- 최근 50건
- 최근 100건
- 전체 저장 메일 (최대 200건)

The pack includes subject, sender, recipients, latest-message segment, local tags, workflow candidate, importance, thread key and fingerprint id. Attachments are not included.

### 3) Android FileProvider share
The pack is written to app cache as `Secmail_MAIL_PACK_*.json` and shared through Android `ACTION_SEND` to `com.openai.chatgpt` with a semiconductor-specific analysis prompt. If ChatGPT is unavailable, Android share chooser is used.

### 4) Structured GPT result import
ChatGPT is instructed to return JSON with:
- overall Inbox Brief
- top actions / reply highlights / know-now / risks
- per-mail `reply | action | schedule | fyi`
- `P1 | P2 | P3`
- Korean summary
- next action
- reason / check-needed
- sendable reply draft in counterpart language
- confidence

The user copies the JSON result and taps `GPT 결과 가져오기`. The app reads clipboard text into an editable import dialog, applies results by fingerprint, and updates Action Inbox.

### 5) AI fields stored locally
DB version 5 adds:
- ai_class
- ai_priority
- ai_summary
- ai_next_action
- ai_reason
- ai_reply_draft
- ai_reply_language
- ai_confidence
- ai_analyzed_at

Existing v0.6.1 data is migrated in place.

### 6) Action Inbox AI display
Saved-mail cards now show GPT priority, summary and recommended next action when available.
If GPT produced a reply draft, the mail button becomes `추천 회신`.

### 7) Recommended reply handoff
`추천 회신` shows the AI draft. `복사 + Secmail 열기` copies the draft to Android clipboard and opens Secmail. The app does not auto-send.

## Semiconductor prompt profile
The batch prompt explicitly preserves Foundry / Fabless / BCD / PMIC / LDMOS / HV Device / PDK / MPW / NTO / tape-out / wafer / yield / reliability / qualification / FA / ESD / SOA / CoM / pricing / capacity / VOC / GDS / lot / split terminology.
It instructs GPT not to invent process capability, price, schedule, yield, customer commitment, owner, due date or technical results.

## Device test checklist
1. Install stable-signed v0.7 over stable v0.6.1.
2. Confirm existing saved mail remains available after DB migration.
3. Run `Inbox 동기화` and confirm collector still advances n/N.
4. Tap `GPT 분석팩 만들기` → choose `미분석 메일` or `최근 50건`.
5. Confirm ChatGPT Android opens with a JSON file attached and analysis prompt.
6. Send the prompt in ChatGPT and wait for JSON-only response.
7. Copy the complete JSON response.
8. Return to Secmail Assistant → `GPT 결과 가져오기` → `적용`.
9. Confirm analyzed count increases and cards show GPT summary / priority / next action.
10. Open a reply-needed mail → `추천 회신` → `복사 + Secmail 열기`.
11. Paste into Secmail, review, then send manually.
12. Mark workflow Waiting / Done as appropriate.

## Security boundary
- Batch sharing is explicit user action only.
- User chooses the range every time.
- Attachments are not automatically included.
- No OpenAI API key / API billing.
- No automatic external send.
- Company/organization policy may restrict sharing email contents with external AI; only use where permitted.
