# CODEX_FIRST_AUDIT_PROMPT.md

AGENTS.md, PROJECT_CONTEXT.md, ARCHITECTURE.md, ROADMAP.md 및 Build/Test 문서를 먼저 읽어라.

그 다음 현재 Repository/working directory 전체를 실제 Source 기준으로 audit하라.

아직 코드를 수정하지 마라.

다음을 확인하라.

1. 실제 Repository/File 구조
2. Mobile / PC 중 현재 작업 대상
3. 현재 Version / Package
4. Build 방법 및 Toolchain
5. 문서에 적힌 현재 Version과 Source의 대응 관계
6. GitHub Actions / CI 또는 Local build 구조
7. 구현된 기능
8. 계획만 있고 구현되지 않은 기능
9. Placeholder / dead code / obsolete residue
10. Context 문서와 Source가 다른 부분
11. Known Issue
12. Unknown / To Confirm
13. Dependency / packaging / signing risk
14. Runtime에서만 확인 가능한 항목
15. 가장 먼저 안정화해야 할 항목

판단 원칙:
- 추정하지 말 것
- Source Code와 Build evidence를 우선할 것
- 문서가 틀리면 문서를 틀렸다고 명시할 것
- Build 성공과 Runtime 검증을 구분할 것
- 보존해야 할 working behavior를 먼저 식별할 것

Audit 결과는 다음 구조로 작성하라.

## Executive Summary
## Confirmed Current State
## Implemented
## Planned / Placeholder
## Source-vs-Docs Mismatch
## Build / Dependency / Packaging Risks
## Runtime Risks
## Unknown / To Confirm
## Preserve / Do Not Break
## First Stabilization Priority

Audit 완료 후에만 다음 구현 작업을 제안하라.
