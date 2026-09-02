---
description: Jira 티켓(AIP-xxx) ID/URL 을 받아 project-controller-dev 스킬 트리거
---

Jira 티켓 ID 또는 URL 을 받아 티켓 내용을 파악한 뒤 `backend-dev` 스킬로 수정 작업을 시작합니다.

## 절차

1. **티켓 ID 추출**

   - 인자가 `AIP-1234` 형태 → 그대로 사용
   - 인자가 URL (`https://.../browse/AIP-1234`) → 정규식으로 ID 추출
   - 인자가 없으면 사용자에게 티켓 ID 요청

2. **티켓 내용 조회**

   - Atlassian MCP 가 연결되어 있으면 (`mcp__claude_ai_Atlassian__*`, 예: `getJiraIssue`) 해당 도구로 조회
   - 연결되어 있지 않으면 사용자에게 티켓 내용 (제목 + 본문 + 첨부 요약) 을 붙여달라고 요청

3. **브랜치 확인**

   - 현재 브랜치명이 브랜치 전략 패턴(`feat/*`·`feature/*`·`fix/*`·`chore/*`·`hotfix/*`)이고 `AIP-{번호}` 를 포함하는지 확인
   - 아니면(특히 `main`/`develop` 이면) 사용자에게 `develop` 기준 브랜치 생성 권고:
     ```
     git checkout develop && git pull
     git checkout -b fix/AIP-1234-{설명-kebab}
     ```
     (버그 수정은 `fix/*`, 신규 작업은 `feat/*`. 상세: `docs/branch-strategy.md`)
   - 강제 전환은 하지 않음 (사용자가 결정)

4. **요청서 작성**

   - 티켓 제목 + 핵심 요구사항을 1~2 문단으로 정리
   - 영향 영역 추정 (리컨실러 / 컨트롤러·와치 / 뮤테이팅 웹훅 / RBAC 정책 / 인포머 / CRD / 공통) —
     모호하면 미해결 질문에 적음
   - CRD 스펙 변경 필요 여부, 영향 받을 리소스 타입(Project·AipubUser·ClusterVolume 등)과
     교차 리포 영향(aipub-web 권한 표시, aipub-installer 차트) 후보 나열

5. **`project-controller-dev` 스킬 트리거**

   - `_workspace/00_request.md` 에 다음 형식으로 기록 (project-controller-dev 팀이 참고할 입력):

     ```
     # AIP-1234

     ## 티켓 제목
     ...

     ## 요구사항
     ...

     ## 추정 영향 범위
     - 영역: 리컨실러, RBAC 정책
     - CRD 변경: 필요/불필요
     - 리소스 타입: Project, ClusterVolume
     - 교차 리포 영향: aipub-web 권한 표시

     ## 미해결 질문
     - ...
     ```

   - `backend-dev` 가 analyst / implementer / qa 3인 팀(단일 모듈이면 implementer + qa 경량 2인)으로 처리.

6. **완료 후**
   - 한국어 Conventional Commits 메시지 초안 (`/commit` 활용 가능):
     ```
     fix: 알림 읽음 처리 gRPC API 예외 수정
     ```
   - PR 본문/footer 에 `Refs: AIP-1234` 추가 권고 (`/pr` 활용 가능, base 는 `develop`)

## 규칙

- 티켓 정보가 부족해서 추정이 30% 이상이면 **반드시** 사용자에게 1회 확인.
- 영향 범위가 너무 넓으면 (5개 이상 모듈/도메인) 단계 분할 제안.
- 새 리소스 타입/에러 상황이 생기면 `ErrorCodeTypeEnum` 에 `{RESOURCE}_NOT_FOUND` 등 에러 코드 추가가 필요할 수 있음 — backend-dev 에 전달.
- DB 스키마·proto 변경이 있으면 backend-dev 의 문서화(Phase 6) 및 마이그레이션 절차(별도 운영) 필요를 명시.

## 인자

- `AIP-1234` — 티켓 ID
- `https://.../browse/AIP-1234` — 티켓 URL
- (인자 없음) — 사용자에게 ID 요청

$ARGUMENTS
