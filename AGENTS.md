# AGENTS.md
# Mobile-specific Codex Rules

Read `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `ROADMAP.md`, and `BUILD_TEST_PACKAGING.md`.

- Preserve the v0.6.1/v0.7 collector behavior unless task explicitly changes it.
- Secmail foreground Accessibility is the current collector.
- Never use `mail_list_view` alone as proof of Inbox screen.
- Processed/unprocessed is independent of Secmail read/unread.
- Original full mail and latest-message should remain separate.
- No security/MDM bypass.
- No automatic external upload.
- No auto-send.
- GPT batch sharing is explicit user action.
- No OpenAI API dependency unless explicitly approved.
- Dependency changes require transitive dependency review.
- Preserve stable signing identity.
- Build success != device runtime validation.
- v0.7 runtime stabilization precedes major refactor.
