---
description: 이번 대화의 재사용 가능한 지식을 파일 기반 메모리에 저장
---

이 대화에서 메모리에 저장할 만한 내용을 찾아서 파일 기반 메모리 시스템에 저장해주세요.

## 저장 기준

- ✅ 코드에서 드러나지 않는 **결정의 맥락과 이유**
- ✅ 사용자의 선호/피드백 — 특히 반복되거나 강조된 것
- ✅ 프로젝트 방향 / 진행 중 이니셔티브 / 마감·freeze 일정 / Jira 티켓 상태
- ✅ 외부 시스템 참조 (Jira AIP 프로젝트, Harbor/K8s 클러스터, Grafana/OpenSearch 대시보드 등)
- ✅ project-controller 특이점 / 함정 (예: 리컨실 스킵 판정이 `List.equals` 순서 비교라 컬렉션 정렬이 필수, 인포머 미등록
  리소스는 `getExistingSharedIndexInformer` 가 null 이라 기동 시 NPE, `kubectl auth can-i` 없이는 RBAC 검증 불가 등)

## 저장하지 않는 것

- ❌ 코드를 읽으면 알 수 있는 것 (필드 추가/삭제, DTO 변경, 디렉터리 구조)
- ❌ git log / git blame 으로 알 수 있는 것 (누가 언제 무엇을 변경)
- ❌ 디버깅 솔루션의 fix 자체 (코드와 커밋 메시지에 이미 있음)
- ❌ CLAUDE.md / docs/ / context/ 에 이미 문서화된 내용
- ❌ 이번 대화에만 유효한 작업 상태 (in-progress, todo)

## 메모리 시스템 규격

- **위치**: Claude Code 가 이 프로젝트에 할당한 메모리 디렉터리 (`~/.claude/projects/<프로젝트 슬러그>/memory/`).
  세션 시스템 프롬프트에 실제 경로가 명시되므로 **그 경로를 쓰고 추측하지 않는다.**
  워크스페이스 루트에서 `claude` 를 실행하는 관례라 메모리가 한곳으로 모인다 (상위 `aipub-workspace/CLAUDE.md` 참고).
- **주제 하나당 파일 하나**: `<kebab-topic>.md`
- 각 파일은 **frontmatter + 본문**:
  ```markdown
  ---
  name: <kebab-topic>
  description: <한 줄 요약 (검색·인덱스용)>
  metadata:
    node_type: memory
    type: <user | feedback | project | reference>
    modified: <ISO8601 타임스탬프>
    originSessionId: <현재 세션 ID>
  ---

  <본문 — 왜/맥락/함정/적용법. 관련 메모리는 [[다른-메모리-name]] 로 링크>
  ```
- `type` 선택 기준:
  - `user` — 사용자 개인 선호/작업 방식
  - `feedback` — 반복 지적된 개선점 (예: [[feedback-explicit-git-add]])
  - `project` — 프로젝트 사실/제약/계획 (예: [[no-db-migration-tool]])
  - `reference` — 재사용 참조 정보 (접속 방법, 트래커 사용법 등)
- 관련 메모리는 본문에서 `[[대상-name]]` 위키링크로 연결한다.

## 절차

1. 대화 이력에서 위 기준에 맞는 항목 후보를 추린다.
2. 사용자에게 후보 목록을 보여주고 저장할 것을 선택받는다 (또는 인자가 명확하면 인자 우선).
3. 적절한 `type` 결정.
4. 메모리 디렉토리에 개별 파일(`<kebab-topic>.md`)을 위 규격으로 작성:
   - 같은 주제 파일이 이미 있으면 새로 만들지 말고 **갱신** (`modified` 타임스탬프 갱신).
5. **`MEMORY.md` 인덱스에 한 줄 추가** — 적절한 섹션(진행 중/완료/Someday 등)에 `[제목](파일.md)` 링크 + 한 줄 요약.

## 인자

- 인자 있음 → 해당 내용을 우선 저장
- 인자 없음 → 대화에서 후보 추출 후 사용자 확인

$ARGUMENTS
