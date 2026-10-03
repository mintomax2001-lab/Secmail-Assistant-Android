# SOURCE_EVIDENCE.md
# Evidence used to produce v2.0

## Mobile actual source
Inspected:
- `Secmail_Reader_Probe_v0.7_source.zip`
- SHA-256:
  `83a05daddc937fce1f20762be95b050624710d140759810853de4109fe41a137`

Stable APK:
- `Secmail_Reader_Probe_v0.7_STABLE.apk`
- SHA-256:
  `30a2e19e044f6c1f3b053fd1291f2415181646acf4e22ceb4e3baf6e8a04712c`

GitHub:
- repository: `mintomax2001-lab/AI-CrossCheck-Android`
- v0.7 source is present in main
- build workflow selects latest versioned source ZIP using `sort -V`

## PC actual source
Inspected:
- `Outlook_Action_Inbox_PC_v0.2.1_NO_API.zip`
- SHA-256:
  `901ec1dbd7192eba8b4e645346cf681792763afaaf6a0567b50ea0462a1d4701`

Previous portable baseline:
- `Outlook_Action_Inbox_PC_v0.1.1_PORTABLE.zip`
- SHA-256:
  `129be2bb644817a6f6384a7a5457a5ad8cc9713fc5e36a978e1c1e8691e5d99b`

## Documentation references for Codex operating model
- OpenAI: Codex AGENTS.md / instruction hierarchy
- OpenAI: ExecPlans / PLANS.md
- OpenAI: Harness engineering / repository knowledge as system of record
- OpenAI: long-horizon tasks and milestone validation
- GitHub: repository/agent instruction files
