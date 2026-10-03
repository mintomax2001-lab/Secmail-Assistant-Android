# PROJECT_CONTEXT.md
# Secmail Assistant Mobile — Actual History / Evidence

## 1. Current identity

- App: Secmail Assistant
- Android package: `com.mintomax.secmailprobe`
- Target mail app: Secmail
- Target package confirmed: `com.uusafe.secmail`
- Secmail tested version recorded in project history: `3.11.6.4`
- Current source/build version: `0.7`
- versionCode: `8`
- compileSdk/targetSdk: 35
- minSdk: 26
- Java: 17
- Main dependencies in v0.7:
  - ML Kit Language ID 17.0.6
  - ML Kit Translate 17.0.3
  - AndroidX Core 1.15.0

## 2. Actual v0.7 source structure

Confirmed from source ZIP:

- `MainActivity.java`
- `ProbeAccessibilityService.java`
- `MailDbHelper.java`
- `MailSnapshot.java`
- `SavedMailRecord.java`
- `ImportantInfoExtractor.java`
- `TranslationHelper.java`
- `GptBatchHelper.java`
- AndroidManifest
- Accessibility service config
- FileProvider paths
- Gradle build files

Approximate Java size: ~3.1K LOC.

## 3. Version history

### v0.1 — Accessibility probe
Implemented:
- raw Accessibility UI-tree capture
- initial app probing

Issue:
- early package-target logic could match the Assistant itself

### v0.2 — Structured reader
Implemented:
- target Secmail package
- Subject / Sender / To / Date / Body extraction
- SQLite storage
- local important/action/schedule extraction

Confirmed finding:
- opened mail body is exposed through Accessibility/WebView; OCR not required in tested setup

### v0.3 — Saved-mail / traversal foundation
Implemented/prototyped:
- saved-mail UX
- batch traversal concept
- de-dup fingerprint
- stable release signing baseline introduced

Stable signing certificate SHA-256:
`fff94b22e58344208b7c98c7c69cc10291fa21ec646407cd092a363de1ad470f`

### v0.4 — Local delete + GPT review
Implemented:
- per-mail local delete
- delete-all confirmation
- explicit GPT share/review
- latest-message sanitization
- no auto-send

### v0.5 — Action Inbox foundation
Implemented:
- persistent `mail_index`
- processed/unprocessed independent from read/unread
- latest-message separate from full thread
- search
- local workflow states
- Tech Marketing tags
- conservative collector guards

### v0.6 — Progress + Translation
Implemented:
- discovery → collection two-stage UX
- foreground Accessibility overlay
- optional on-device Korean translation using ML Kit
- Collection → Triage → Action UX

Build issue:
- Java local variable name collision caused compile failure
- corrected build later succeeded

### v0.6.1 — Collector engine stabilization
Implemented in source/history:
- self-scheduled state-machine continuation
- processed only after detail capture/controlled failure
- retries
- dispatchGesture fallback
- failed rows not persisted as processed
- collector limits raised
- GPT direct-launch hotfix
- conditional web-search guidance in GPT prompt

Stable APK SHA-256:
`15c1789baf4ef20c91c3a775489ad392de946ac2a43e64d0147df497cd909da6`

### v0.7 — Batch GPT Pack + Result Import
Confirmed in actual source:

`GptBatchHelper.java` implements:
- `createMailPack(...)`
- schema `mobile-mail-pack-v1`
- max ~5000 chars latest_message per mail
- semiconductor-aware GPT prompt
- FileProvider-based JSON share
- explicit ChatGPT package handoff
- clipboard JSON read
- JSON cleanup/import
- per-mail result update

`MailDbHelper.java` v5 schema contains:
- ai_class
- ai_priority
- ai_summary
- ai_next_action
- ai_reason
- ai_reply_draft
- ai_reply_language
- ai_confidence
- ai_analyzed_at

`MainActivity.java` contains:
- GPT 전체분석 NO API section
- GPT 분석팩 만들기
- GPT 결과 가져오기
- analysis-scope selection
- result import dialog
- AI summary rendering
- 추천 회신
- copy reply
- ChatGPT re-review

Manifest contains:
- `androidx.core.content.FileProvider`
- query for ChatGPT package
- Accessibility service

Build evidence:
- GitHub Actions run: `36830534032`
- package: `com.mintomax.secmailprobe`
- versionCode: `8`
- versionName: `0.7`
- artifact ID: `11147147408`
- CI debug APK SHA-256:
  `785164575fe41604e62cdd30c32c3cabcc84f758053cba7a5033bf30411e8388`
- stable APK SHA-256:
  `30a2e19e044f6c1f3b053fd1291f2415181646acf4e22ceb4e3baf6e8a04712c`

## 4. Actual Accessibility findings

Confirmed IDs/structures previously observed:
- `root`
- `mail_list_view`
- `mail_subject_textview`
- `mail_sender_name_tv`
- `mail_date_textview`
- `to_area_general_tv`
- `quick_reply_bar_input_edittext`
- reply/forward controls

Mail body observed through WebView hierarchy including:
- `contentDiv`
- `mailContentContainer`
- `main-body`

Important:
`mail_list_view` may appear on detail screen too. It is not sufficient by itself to classify Inbox.

## 5. Implementation status classification

### Implemented in source
- Accessibility collector
- local SQLite
- local workflow classifier
- local topic tags
- search
- Waiting/Done manual state
- ML Kit translation
- batch MAIL_PACK generation
- ChatGPT explicit share
- GPT JSON import
- AI summary/priority/next action/reply fields
- recommended reply copy
- Secmail open/share flow
- FileProvider

### Build-validated
- v0.7 compiles/packages successfully in GitHub Actions
- package/version verified in CI
- stable APK signing produced/verified in prior workflow

### Runtime-validated before v0.7
- v0.6.1 collector was reported as working sufficiently to proceed
- Accessibility reads real opened mail content
- earlier versions proved local capture/storage

### To Confirm on v0.7 device
- overwrite install from v0.6.1
- DB migration/preservation
- existing collector regression
- MAIL_PACK file sharing to ChatGPT on target Android build
- returned JSON import from clipboard/dialog
- AI fields mapped to correct mail
- long/large batch behavior
- recommended reply UX
- translation/search/delete regressions

## 6. Known build/release incident classes

Observed over project history:
- wrong version/source selection
- workflow/YAML issues
- Java compile regression
- dependency duplicate classes
- signing continuity
- runtime-only Accessibility/UI behavior
- download/artifact handoff friction

These are now explicit release gates, not ad-hoc fixes.

## 7. Current repository issue for Codex

GitHub repository `mintomax2001-lab/AI-CrossCheck-Android` main root currently stores multiple versioned source ZIPs:
- v0.1
- v0.2
- v0.3
- v0.4
- v0.5
- v0.6
- v0.6.1
- v0.7

The build workflow unzips the selected source.

This works for CI, but is suboptimal as the long-term Codex development model because the active source is not directly tracked as normal source files.

Recommended migration:
- unpack current v0.7 into a normal tracked Android source tree
- use Git commits/tags for history
- keep ZIP/APK as release artifacts/backups, not primary editable source
- preserve old ZIPs until migration is verified
