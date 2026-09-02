---
description: 현재 변경을 qa-validator 에이전트로 리뷰 (정합성·빌드·테스트·컨벤션)
---

현재 변경 사항을 `qa-validator` 에이전트로 리뷰해주세요. 빌드·테스트·Spotless 포맷·컨벤션 준수·모듈 간 정합성을 검증합니다.

## 절차

1. 병렬로 실행:
   - `git status` — 변경 파일 파악 (`-uall` 금지)
   - `git diff` — 실제 변경 내용
   - (인자가 있으면) 해당 PR/브랜치 `git log` 로 컨텍스트
2. 변경이 너무 적거나 (1~2줄) 검증 가치가 낮으면 사용자 확인 후 간단 검증만 권고.
3. `_workspace/02_implementer_changes.md` 가 없는 경우 (직접 작성된 변경 또는 사용자 수동 수정), 임시로 작성:
   - 변경 파일 목록 (모듈별)
   - 변경 요약
4. **`qa-validator` 에이전트 호출** (subagent_type: qa-validator):
   - `.claude/agents/qa-validator.md` 의 역할·2-Tier QA 전략을 따르게 한다.
   - 검증 범위: 빌드/컴파일 → 단위 테스트 → 컨벤션(`.claude/rules/controller-code-style.md`) → 리컨실 루프 멱등성·RBAC 최소 권한·웹훅 안전성.
   - **빌드 검증 명령** — 단일 프로젝트라 모듈 접두사가 없다:
     ```
     ./gradlew compileJava compileTestJava test
     ```
     단일 클래스만: `./gradlew :test --tests "*ClassName*"` (서브프로젝트 `common-*` 에도 test 태스크가 있어
     `--tests` 필터를 루트에 걸면 서브프로젝트에서 "No tests found" 로 실패한다 — `:test` 로 루트만 지정할 것).
   - 산출: `_workspace/03_qa_report.md`
   - **대규모 변경**(리컨실러·컨트롤러·웹훅 신규 추가 / CRD 필드 변경 / RBAC 정책 변경)이면 추가로 `bmad-code-review`(3-에이전트 병렬 리뷰)를 옵션으로 수행할 수 있다.
5. 보고서를 종합해서 사용자에게 한 화면에 보고. **아래 마크다운 포맷** 을 따른다 (터미널에서 표·이모지 배지가 렌더되어 한눈에 스캔됨).

   배지 규칙:

   - 판정: `✅ PASS` / `⚠️ PARTIAL` / `❌ FAIL`
   - 심각도: `🔴 BLOCKER` / `🟠 MAJOR` / `🟡 MINOR` / `⚪ NIT`
   - 검증 명령은 인라인 코드 배지로: `compile ✅` `test 152/152 ✅` (실패 시 ❌)

   ```markdown
   ## 🔍 리뷰 결과 — <대상: PR #번호 / 워킹트리 / 커밋해시>

   | 영역          |   판정   | 핵심         |
   | ------------- | :------: | ------------ |
   | QA (정합성)   | ✅ PASS  | <한 줄 요약> |

   ---

   #### ✅ QA — 빌드·테스트·컨벤션·정합성

   `compile ✅` `test 152/152 ✅`

   - 🟡 **MINOR** <이슈> (`file:line`) → <수정 방향>
   - ⚪ **NIT** <이슈> (`file:line`)

   #### 🎯 통합 권고

   1. **(MAJOR)** <우선순위 높은 수정>
   2. **(MINOR)** <후속>

   상세: `_workspace/03_qa_report.md`
   ```

   포맷 규칙:

   - 이슈가 없으면 `- 이슈 없음` 한 줄로.
   - 각 이슈는 심각도 높은 순으로 정렬.
   - 검증 명령이 실패하면 해당 배지를 ❌ 로 바꾸고, 실패 출력의 핵심만 카드에 짧게 인용.

## 규칙

- 자체 코드 수정은 최소화 — 단순 포맷/import 에러만 직접 수정하고, 그 외 발견 사항은 보고만. 로직 수정은 후속 요청에서 `controller-implementer` 에게.
- 리컨실 루프를 건드린 변경이면 **멱등성**을 반드시 확인 — 같은 입력에 대해 두 번째 리컨실이 UPDATE 를 내지 않아야 한다
  (`reconcileExistingRole` 류가 `List.equals` 순서 비교로 스킵 여부를 판단하므로, 컬렉션 정렬 누락이 무한 갱신을 만든다).

## 인자

- 인자 없음 → 현재 워킹 트리 변경 리뷰
- `<PR번호>` → `gh pr diff <PR번호>` 로 PR 변경 리뷰
- `<커밋해시>` → 해당 커밋의 변경 리뷰

$ARGUMENTS
