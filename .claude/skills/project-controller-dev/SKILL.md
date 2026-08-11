---
name: project-controller-dev
description: "project-controller(AIPub 플랫폼의 프로젝트/테넌트 경계 관리 Kubernetes 컨트롤러) 프로젝트의 기능 개발, 리컨실러/웹훅 추가, 버그 수정을 에이전트 팀으로 조율하는 오케스트레이터. 코드베이스 분석 → 구현 → 검증의 전체 개발 파이프라인을 자동화. '새 리컨실러 추가', '컨트롤러 구현', '뮤테이팅 웹훅 핸들러 추가', 'CRD 필드 추가', 'RBAC 정책 변경', '프로젝트 컨트롤러 기능 개발' 등의 요청 시 반드시 이 스킬을 사용할 것. '다시 실행', '재실행', '결과 개선', '이전 결과 기반으로', '부분 수정', '하네스 재실행', '이어서 해줘', '계속 진행해줘' 등 후속 작업 요청에도 사용할 것. 단순한 단일 파일 수정이나 코드 리뷰가 아닌, 리컨실러/웹훅/RBAC에 걸친 구현 작업에 특히 유용."
---

# Project Controller Dev Orchestrator

project-controller(Kubernetes 리컨실러/뮤테이팅 웹훅 기반 컨트롤러) 프로젝트의 개발 파이프라인을 에이전트 팀으로 조율하는 오케스트레이터.

## 실행 모드: 에이전트 팀

파이프라인 + 생성-검증 복합 패턴. 분석 → 구현 ↔ 검증의 피드백 루프를 팀원 간 직접 통신으로 실현한다.

## 에이전트 구성

| 팀원 | 에이전트 타입 | 역할 | 출력 |
|------|-------------|------|------|
| analyst | codebase-analyst | 코드베이스 분석, 패턴 질의 응답 | `_workspace/01_analyst_report.md` |
| implementer | controller-implementer | 리컨실러/웹훅/RBAC 구현 | 소스 파일 + `_workspace/02_implementer_changes.md` |
| qa | qa-validator | 빌드/테스트/컨벤션/멱등성/RBAC/웹훅 안전성 검증 | `_workspace/03_qa_report.md` |

## 팀 통신 구조

```
analyst ──분석 결과──→ implementer
analyst ←──패턴 질문── implementer
analyst ←──컨벤션 질문── qa

implementer ──구현 완성 알림──→ qa (점진적 QA)
implementer ←──빌드/테스트 에러── qa
implementer ──수정 완료──→ qa (재검증 요청)

리더 ←── 각 팀원의 진행 보고
```

핵심: implementer ↔ qa 간 직접 피드백 루프로, 리더를 거치지 않고 빠르게 문제를 해결한다.

## 워크플로우

### Phase 0: 컨텍스트 감사

워크플로우 시작 전 기존 작업 상태를 확인하여 실행 모드를 결정한다.

1. `_workspace/` 디렉토리 존재 여부 확인
2. 존재 여부에 따라 실행 모드 분기:
   - `_workspace/` **미존재** → **초기 실행** — Phase 1부터 전체 실행
   - `_workspace/` **존재** + 사용자가 부분 수정 요청 → **부분 재실행** — 기존 분석 보고서 활용, 해당 에이전트만 재호출. Phase 2에서 재호출 대상 에이전트의 프롬프트를 조정한다 (예: analyst 미스폰 시 implementer 프롬프트에서 "analyst로부터 수신" 대기를 제거하고 기존 `01_analyst_report.md`를 직접 읽도록 지시).
   - `_workspace/` **존재** + 사용자가 새 기능 요청 → **새 실행** — 아래 "히스토리 로테이션" 절차로 이전 실행 내용을 보존한 후 Phase 1부터 실행
3. 부분 재실행 시 기존 산출물(`01_analyst_report.md`, `02_implementer_changes.md`, `03_qa_report.md`)을 읽어 맥락을 파악

**히스토리 로테이션**: `_workspace`는 `${PROJECT_HOME}/_workspace`(프로젝트 루트 기준 로컬 디렉토리)다.
```bash
mv _workspace "_workspace_prev_$(date +%Y%m%d_%H%M%S)"
mkdir _workspace
```
`_workspace`를 개인 환경에서 다른 위치(예: 클라우드 동기화 폴더)에 심볼릭 링크로 연결해 여러 머신에 공유하는 건 개인의 선택이며, 이 스킬의 관심사가 아니다.

### Phase 1: 준비
1. 사용자 입력 분석 — 어떤 리컨실러/웹훅 핸들러/RBAC 변경이 필요한지 파악
2. 작업 디렉토리에 `_workspace/` 생성 (Phase 0에서 이미 존재하면 건너뜀)
3. 변경 범위 판단 — 단일 파일(리컨실러 1개 또는 핸들러 1개)인지, 여러 컴포넌트에 걸친 변경인지

**단일 파일 변경** (기존 리컨실러 1개 또는 웹훅 핸들러 1개만 수정, 새 CRD/Factory 배선 불필요): 분석 작업을 경량화하고 implementer + qa 2인 팀으로 구성 가능.

### Phase 2: 팀 구성

```
TeamCreate(
  team_name: "project-controller-dev-team",
  members: [
    {
      name: "analyst",
      agent_type: "codebase-analyst",
      model: "opus",
      prompt: "project-controller 코드베이스를 분석하라.

요청: {사용자 요청}

.claude/agents/codebase-analyst.md를 읽고 역할과 프로토콜을 따르라.
_workspace/01_analyst_report.md에 분석 보고서를 작성하라.

분석 완료 후 implementer에게 SendMessage로 핵심 발견을 요약 전달하라.
이후 implementer와 qa의 패턴 질문에 응답하라."
    },
    {
      name: "implementer",
      agent_type: "controller-implementer",
      model: "opus",
      prompt: "project-controller 코드를 구현하라.

요청: {사용자 요청}

.claude/agents/controller-implementer.md를 읽고 역할과 프로토콜을 따르라.
analyst로부터 분석 결과를 수신한 뒤 구현을 시작하라.

리컨실러/웹훅 핸들러/RBAC 컨트롤러 등 논리적 단위가 완성될 때마다 qa에게 SendMessage로 검증 요청하라.
qa의 피드백을 수신하면 즉시 수정하고 재검증 요청하라.
변경 목록을 _workspace/02_implementer_changes.md에 기록하라."
    },
    {
      name: "qa",
      agent_type: "qa-validator",
      model: "opus",
      prompt: "project-controller 코드 품질과 안전성을 검증하라.

요청에 대한 구현이 진행 중이다.

.claude/agents/qa-validator.md를 읽고 역할과 검증 절차를 따르라.
implementer로부터 구현 완료 알림을 수신하면 즉시 검증을 시작하라(빌드 → 테스트 → 컨벤션 → 멱등성/RBAC/웹훅 안전성 → 경계면 교차 비교).

문제 발견 시 implementer에게 SendMessage로 구체적 수정 요청을 보내라.
단순 컴파일 에러는 직접 수정하라.
최종 결과를 _workspace/03_qa_report.md에 기록하라."
    }
  ]
)
```

### Phase 3: 작업 등록

```
TaskCreate(tasks: [
  {
    title: "코드베이스 분석",
    description: "기존 리컨실러/웹훅/RBAC 패턴 탐색, 영향 범위 식별, 참고 코드 수집",
    assignee: "analyst"
  },
  {
    title: "리컨실러/웹훅/RBAC 구현",
    description: "CRD 스펙 변경, 리컨실러/Factory 배선, 웹훅 핸들러, RBAC 컨트롤러 구현",
    assignee: "implementer",
    depends_on: ["코드베이스 분석"]
  },
  {
    title: "테스트 작성",
    description: "단위 테스트 작성 (기존에 없던 리컨실러 테스트 포함)",
    assignee: "implementer",
    depends_on: ["리컨실러/웹훅/RBAC 구현"]
  },
  {
    title: "QA 검증",
    description: "빌드 + 테스트 + 컨벤션 + 멱등성/RBAC/웹훅 안전성 + 경계면 교차 비교",
    assignee: "qa",
    depends_on: ["리컨실러/웹훅/RBAC 구현", "테스트 작성"]
  }
])
```

> 작업 구성은 사용자 요청에 따라 조정한다. 웹훅 변경이 없으면 관련 검증 항목을 건너뛰고, RBAC 변경이 없으면 RBAC 검증도 건너뛴다.

### Phase 4: 팀 실행 및 모니터링

**실행 방식:** 팀원들이 자체 조율

팀원들은 작업 의존성에 따라 순차/병렬로 작업을 수행한다:
1. analyst가 분석 → implementer에게 SendMessage로 결과 전달
2. implementer가 단위별로 구현 → qa에게 완성 알림
3. qa가 점진적으로 검증 → 문제 발견 시 implementer에게 직접 피드백
4. implementer가 수정 → qa에게 재검증 요청

**리더 모니터링:**
- 팀원이 유휴 상태가 되면 자동 알림 수신
- TaskGet으로 전체 진행률 확인
- 특정 팀원이 막히면 SendMessage로 개입

**BMAD 연동 진입 판단:** implementer는 변경 파일 3개 이상/새 CRD·리컨실러 추가 시 `bmad-quick-dev`, AC 명시된 스토리는 `bmad-dev-story`로 진입할 수 있다(`controller-implementer.md`의 "BMAD 연동" 참조). qa는 RBAC 정책 변경, 새 웹훅 핸들러, 여러 리컨실러에 걸친 대규모 변경 시 직접 검증에 더해 `bmad-code-review`(3-에이전트 병렬 리뷰)를 추가로 수행할 수 있다(`qa-validator.md`의 "BMAD 연동" 참조). 단일 파일 수정 등 소규모 변경은 기존 방식(직접 구현·직접 검증)을 그대로 사용한다.

### Phase 5: 결과 수집 및 보고
1. 모든 작업 완료 대기 (TaskGet으로 확인)
2. `_workspace/03_qa_report.md` Read
3. QA 종합 판정 확인:
   - **PASS**: 사용자에게 변경 목록 + 검증 결과 요약 보고
   - **FAIL**: 미해결 문제를 분석하고 implementer에게 SendMessage로 추가 수정 요청. 최대 1회 재시도 후에도 FAIL이면 사용자에게 문제 상세 보고.
4. 대규모 변경이었다면 `bmad-code-review` 결과(수행했을 경우)를 QA 보고서 요약에 함께 포함한다.
5. **Phase 6 완료 전까지 팀원 종료 요청·최종 사용자 보고를 하지 않는다** — QA PASS는 작업 종료 조건이 아니라 Phase 6 진입 조건이다.

### Phase 6: 문서화 (필수 — 생략 불가)

**QA PASS 시 예외 없이 실행한다.** 변경 규모(단일 파일 여부, 경량 2인 팀 여부)와 무관하게 항상 수행 — "이번엔 간단하니 생략" 판단을 리더가 임의로 내리지 않는다. `_workspace/`는 로컬(공유 폴더로 여러 머신에 동기화되긴 하지만) 스크래치 공간이지 git으로 관리되는 영구 기록이 아니므로, 이 단계를 생략하면 분석 근거·시행착오(피해야 할 함정)·검증 방법론이 영구히 유실된다 (aipub-backend에서 이 문제가 실제로 발생해 2026-07-30 Phase 6 필수화를 도입한 전례를 그대로 반영).

QA PASS 후 `${PROJECT_HOME}/docs/{작업명}/` 디렉토리를 생성하고 다음 4개 문서를 작성한다.

**작업명 규칙**: 사용자 요청에서 핵심 키워드를 kebab-case로 변환 (예: "ImageHub 리컨실러 재시도 로직 추가" → `imagehub-reconciler-retry`)

**문서 구성**:

| 파일 | 내용 |
|------|------|
| `01-plan.md` | 계획 문서 — 개요, 배경, 설계 결정(검토한 대안 포함), 변경 범위 |
| `02-implementation.md` | 구현 문서 — 변경 파일 목록, CRD 스펙 변경(있다면), 리컨실러/웹훅 흐름 다이어그램 |
| `03-qa-report.md` | QA 보고서 — 빌드/테스트 결과, 컨벤션 검증 체크리스트, 멱등성/RBAC/웹훅 안전성 검증 결과, 발견 및 수정된 이슈 |
| `04-pr.md` | PR 문구 — Title, Summary, Changes, Test plan(체크리스트) |

**작성 원칙**:
- 각 문서는 자체 완결적이어야 한다. 다른 문서를 참조하지 않고도 해당 관점에서 작업을 이해할 수 있어야 한다.
- QA 보고서는 `_workspace/03_qa_report.md`의 내용을 기반으로 정리한다.
- PR 문구는 GitHub PR 생성 시 바로 복사해서 사용할 수 있는 형태로 작성한다.
- CRD 스펙을 변경했다면 `kubernetes/examples/*.yaml` 예시도 함께 갱신됐는지 구현 문서에서 확인 표기한다.

### Phase 7: 피드백 및 진화

작업 완료 후 하네스 자체의 개선 기회를 포착한다.

1. **사용자 피드백 수집**: "결과에서 개선할 부분이 있나요? 워크플로우에 바꾸고 싶은 점이 있으면 알려주세요." — 피드백이 없으면 넘어간다.
2. **피드백 반영 경로**:

| 피드백 유형 | 수정 대상 |
|-----------|----------|
| 분석 깊이/범위 | `codebase-analyst.md` |
| 구현 컨벤션/패턴 | `controller-implementer.md` |
| QA 검증 범위/방식 | `qa-validator.md` |
| 워크플로우 순서/팀 구성 | 이 SKILL.md |

3. **CLAUDE.md 변경 이력 갱신**: 하네스 파일을 수정한 경우 CLAUDE.md의 하네스 변경 이력 테이블에 날짜, 변경 내용, 대상, 사유를 기록한다.

**자동 진화 트리거** (사용자 요청 없이도 제안):
- 같은 유형의 QA 피드백이 2회 이상 반복될 때
- 에이전트가 반복적으로 실패하는 패턴이 발견될 때
- 사용자가 오케스트레이터를 우회하여 수동으로 작업하는 것이 관찰될 때

## 데이터 흐름

```
[리더: Phase 0 컨텍스트 감사] → 초기/부분 재실행/새 실행 판단
        ↓
[analyst] 코드베이스 분석 → _workspace/01_analyst_report.md
        ↓ SendMessage
[implementer] 리컨실러/웹훅/RBAC 구현 → 단위별 완성 알림
        ↓ SendMessage                        ↑ 재검증 요청
[qa] 빌드/테스트/컨벤션/멱등성/RBAC/웹훅 검증 ─┘
        ↓ PASS
[리더] 결과 종합 보고 → Phase 6 문서화(필수) → Phase 7 피드백
```

## 에러 핸들링

- 빌드 실패 1회 재시도: implementer가 즉시 수정 후 qa 재검증. 2회 연속 실패 시 리더가 개입해 원인을 재분석.
- 팀원 무응답/유휴: 리더가 TaskGet으로 상태 확인 후 SendMessage로 재개 지시.
- QA FAIL 최대 1회 재시도 후에도 FAIL: 사용자에게 미해결 문제 상세 보고, 임의로 PASS 처리하지 않는다.

## 테스트 시나리오

**정상 흐름**: "ImageHub 리컨실러에 재연결 재시도 로직 추가해줘" → Phase 0(초기 실행 판단) → analyst 분석 → implementer 구현(단일 파일이면 경량 2인 팀) → qa 검증(PASS) → Phase 6 문서화 → Phase 7 피드백 수집.

**에러 흐름**: qa가 리컨실 루프 멱등성 위반 발견(재시도 로직이 매 호출마다 카운터를 무조건 증가시켜 무한 재시도 가능) → implementer에게 구체적 수정 요청 → implementer 수정 → qa 재검증 PASS → Phase 6 진행.
