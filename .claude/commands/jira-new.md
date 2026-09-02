---
description: 새 Jira 이슈를 대화로 정리해 Atlassian MCP 로 생성 (AIP 프로젝트)
---

한 줄 요청을 받아 Jira 이슈 필드로 정리한 뒤 `mcp__claude_ai_Atlassian__createJiraIssue` 로 생성합니다.
(이 파일은 `/jira-prd`·`/jira-fix` 등 다른 Jira 커맨드가 참조하는 **Jira 규칙의 단일 출처(SoT)** 입니다.)

## 고정 값

- `cloudId`: `ten1010.atlassian.net`
- `projectKey`: `AIP`
- `issueTypeName`: **기본 `일반 작업`(Task)**. 사용자가 **"버그"라고 명시할 때만** `버그`.
  그 외에는 버그성 작업이라도 `일반 작업`으로 만든다. (FE 의 `스토리` 자동 선택은 쓰지 않음)
- **Team**(`customfield_10001`): backend 팀 `java` = `"0343808e-53c6-40f7-95a2-777a97bdf178"`.
  **문자열 ID 를 직접 전달**한다 — 객체 `{ "id": ... }` 로 주면 400. (FE 는 별도 팀 ID 를 씀)

## 필드 매핑

`createJiraIssue` 의 `additional_fields` 로 커스텀 필드 전달. (`summary`/`description`/`assignee_account_id` 는 최상위 인자)

| 필드         | 키                    | 처리                                                                                    |
| ------------ | --------------------- | --------------------------------------------------------------------------------------- |
| 요약         | `summary`             | **`[JA]` prefix 로 시작** (아래 규칙). 50자 이내, 동사로 끝맺음                          |
| 설명         | `description`         | 버그: 현상/재현/기대, 작업: 배경/요구사항/완료조건                                       |
| 스프린트     | `customfield_10020`   | 스프린트 **ID(정수)**. **물어봄** — default 현재 활성 스프린트                          |
| Story Points | `customfield_10028`   | 정수. **물어봄**                                                                        |
| 시작 날짜    | `customfield_10015`   | 오늘 날짜 (`YYYY-MM-DD`) 자동                                                           |
| 기한         | `duedate`             | 현재 스프린트 종료일 (`YYYY-MM-DD`) 자동                                                |
| 수정 버전    | `fixVersions`         | `[{ "name": "5.1.0" }]` 형태. **물어봄**                                                |
| 우선순위     | `priority`            | `{ "name": "Medium" }`. 값: Highest/High/Medium/Low/Lowest. **물어봄** — default Medium |
| 담당자       | `assignee_account_id` | **물어봄** — default 나 자신 (박대권 `712020:0c358c7c-9edc-4f94-a280-eb0980e951b8`)      |
| 팀           | `customfield_10001`   | **자동** — `java` 팀 ID `"0343808e-53c6-40f7-95a2-777a97bdf178"` (문자열 직접 전달)             |
| 에픽/상위    | `customfield_10014`   | **기본 비움**. 절차에서 "연결할 상위 에픽이 있는지" 물어보고, 있을 때만 그 키를 넣는다     |

### 요약 prefix 규칙

- **모든 `summary` 는 `[JA]` 로 시작**한다.
- 기존 카테고리 태그(`[보안]`, `[성능]` 등)가 있으면 **그 앞에** 둔다 — 예: `[JA] [보안] 토큰 서명 검증 추가`.
- FE 의 `[FE]` 컨벤션은 쓰지 않는다.

## 절차

1. **요청 파악** — 아래 `$ARGUMENTS` 가 비어 있으면 **여기서 멈추고** 이슈 요지를 물어봄
   (문서의 예시·직전 대화 내용을 요지로 추측해 진행 금지). 비어 있지 않을 때만 2단계 진행.

2. **현재 스프린트 조회** (스프린트/기한 default 용)

   - `searchJiraIssuesUsingJql` 로 `project = AIP AND sprint in openSprints()` 1건 조회 →
     `customfield_10020` 중 `state: "active"` 항목의 `id`(스프린트 ID) 와 `endDate` 추출.
   - `endDate` → KST 날짜로 변환해 `duedate` 기본값.

3. **담당자 default** — 인자에 없으면 `mcp__claude_ai_Atlassian__atlassianUserInfo` 로 내 accountId 사용.

4. **물어보기** (한 번에) — 스프린트(default 현재), Story Point, 수정 버전, 우선순위(default Medium), 담당자(default 나),
   **연결할 상위 에픽이 있는지**(default 없음 — 없으면 에픽 비움). 나머지(시작=오늘, 기한=스프린트 종료일, 팀=java)는 자동.

5. **초안 확인** — 정리된 필드 표를 보여주고 사용자 확인.

6. **중복 확인 후 생성** — `mcp__claude_ai_Atlassian__searchJiraIssuesUsingJql` 로 동일/유사 summary 가 이미 있는지 확인한 뒤,
   없을 때만 `mcp__claude_ai_Atlassian__createJiraIssue` 호출. 생성 후 이슈 키(`AIP-xxxx`)·URL 보고.

7. **상위 연결 검증** — 상위 에픽을 설정했다면 `mcp__claude_ai_Atlassian__getJiraIssue` 로 parent 가 실제로 연결됐는지 확인.

8. **후속** (선택) — 바로 작업하려면 `/jira-fix AIP-xxxx`.

## 규칙

- MCP 미연결 시: 정리된 필드를 출력하고 수동 생성 안내 (임의 생성 금지).
- 생성은 비가역 — 반드시 초안 확인 후 호출.
- **MCP 타임아웃 함정** — `createJiraIssue` 가 무응답으로 timeout 나도 **서버에는 실제로 생성됐을 수 있다**.
  timeout 후 **맹목적으로 재시도하지 말고**, `searchJiraIssuesUsingJql` 로 방금 만들려던 이슈가 이미 생성됐는지
  먼저 확인한다. 없을 때만 재생성한다 (중복 생성 방지).

## 인자

- 인자 있음 → 이슈 요지 (자유 텍스트)
- 인자 없음 → 절차 1 에서 중단하고 요지 요청 (추측 생성 금지)

$ARGUMENTS
