# ARCHITECTURE.md
# Secmail Assistant Mobile — Current Architecture

## Current flow

```text
Secmail foreground UI
        ↓
ProbeAccessibilityService
        ↓
MailSnapshot / parsing
        ↓
ImportantInfoExtractor
        ↓
MailDbHelper (SQLite)
        ↓
MainActivity Action Inbox
        ├─ local search/filter/state
        ├─ TranslationHelper (ML Kit)
        └─ GptBatchHelper
             ├─ MAIL_PACK.json
             ├─ FileProvider
             ├─ ACTION_SEND → ChatGPT
             └─ JSON clipboard/import → DB
```

## Component responsibilities

### ProbeAccessibilityService
Owns:
- Secmail foreground Accessibility observation
- inbox discovery
- batch traversal
- detail detection
- row candidate extraction
- safe open/retry/back
- capture
- progress overlay

Do not move GPT/business logic into this service.

### MailDbHelper
Owns:
- SQLite persistence
- migrations
- processed mail index
- workflow fields
- GPT fields
- search/filter query support

DB_VERSION in current source: 5.

### ImportantInfoExtractor
Owns deterministic local heuristics:
- key point/action/schedule extraction
- importance
- basic workflow candidate
- topic tags/latest-message helper logic

It is not authoritative AI analysis.

### TranslationHelper
Owns:
- language ID
- Korean translation
- ML Kit model download
- storing translated text

Original mail remains authoritative.

### GptBatchHelper
Owns:
- Mail Pack generation
- GPT prompt
- FileProvider share
- clipboard helper
- JSON result parsing/import

It does not directly call OpenAI API.

### MainActivity
Currently owns a large amount of UI and orchestration.
This is functional but increasingly large (~1000 lines) and may become a future refactor target.

Do not refactor it before v0.7 runtime is stabilized.

## Architecture invariants

1. Secmail is source of truth.
2. Assistant must not delete/send Secmail mail.
3. Accessibility collection requires Secmail foreground.
4. Processed/unprocessed is independent from read/unread.
5. latest_message and full thread remain separate.
6. GPT share is explicit user action.
7. GPT import never overwrites Waiting/Done with AI content class.
8. Original text is authoritative over translation/AI.
9. No hidden mail-server/API connection unless separately authorized.

## Long-term architecture direction

After v0.7 stabilization:

- introduce shared MailPack/Result schema version module
- split oversized UI/orchestration from MainActivity
- strengthen thread identity
- improve source/provenance snippets
- eventually use authorized direct mail API if available, while keeping downstream Action Inbox model
