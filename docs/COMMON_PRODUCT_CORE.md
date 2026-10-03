# COMMON_PRODUCT_CORE.md
# Shared Product Core — Mobile + PC

## Product goal

`Mail Source → Collect → Normalize → Triage → Action → Waiting → Done`

목적은 일반 메일 클라이언트를 다시 만드는 것이 아니라, 많은 메일을 실제 업무 queue로 변환하는 것이다.

사용자가 한 화면에서 알아야 할 것:
- Reply Needed
- My Action
- Waiting
- Schedule
- FYI
- Done

## Semantic model

### Action Type
- `reply`: 상대가 답변/확인/의사결정을 기대
- `action`: 내부 확인/조치/협업 필요
- `schedule`: 일정/회의/방문/마감 관리가 핵심
- `fyi`: 즉시 Action 불필요

### Workflow Status
- `open`
- `waiting`
- `done`

Waiting/Done은 content classification이 아니라 lifecycle state다.

## GPT result contract

권장 공통 구조:

```json
{
  "overview_ko": "",
  "top_actions": [],
  "reply_highlights": [],
  "know_now": [],
  "risks": [],
  "items": [
    {
      "id": "",
      "class": "reply|action|schedule|fyi",
      "priority": "P1|P2|P3",
      "summary_ko": "",
      "next_action_ko": "",
      "reason_ko": "",
      "reply_draft": "",
      "reply_language": "ko|en|zh|other",
      "confidence": 0.0
    }
  ]
}
```

## Semiconductor profile

다음 domain term의 의미를 보존한다.

Foundry, Fabless, IDM, BCD, PMIC, LDMOS, HV device, DTI, Junction Isolation,
PDK, MPW, NTO, tape-out, GDS, wafer, lot, split, yield, WAT,
reliability, qualification, HAST, HTST, ESD, HBM, SOA, latch-up,
CoM, pricing, capacity, loading, VOC, roadmap, proposal, 2nd source.

메일 원문에 없는:
- process capability
- price
- schedule
- yield
- owner
- due date
- customer commitment
- technical result

을 생성하지 않는다. 불명확하면 `[확인 필요]`.

## AI integration principle

현재 기본 방향:
`Local Mail Pack → User explicit share to ChatGPT → Structured JSON → Local import`

OpenAI API는 현재 필수 dependency가 아니다.

향후 API가 승인되더라도 transport layer만 교체하고 schema/workflow는 유지한다.

## Safety boundary

- No auto-send.
- No credential extraction.
- No traffic interception.
- No security bypass.
- No automatic attachment upload.
- GPT 결과는 advisory.
- 원문이 authoritative source.
