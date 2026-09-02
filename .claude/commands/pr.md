---
description: PR 본문(Summary/Changes/Test plan) 초안 작성 후 gh pr create (base develop)
---

현재 브랜치를 GitHub Pull Request 로 올립니다. 본문은 아래 고정 구조(`## Summary` / `## Changes` / `## Test plan`)로 작성합니다.

> ⚠️ **이 리포에는 `pr-auto.yml` 이 없습니다** (`secrets.SECRET` 미설정). aipub-backend 와 달리 담당자·리뷰어·
> 마일스톤이 자동 부여되지 않으므로 **PR 생성 후 GitHub 에서 직접 지정**해야 합니다.
> `/pr` 은 본문만 작성하고 관련 플래그를 붙이지 않습니다 — 자동/수동 경로가 섞이면 무엇이 누락됐는지
> 알기 어려워지므로, 지정은 사람이 한 번에 합니다.

## 절차

1. **사전 조건 확인** (병렬):

   - `git status` — 워킹 트리 클린 여부 (`-uall` 금지)
   - `git branch --show-current` — 현재 브랜치명
   - `git log --oneline origin/develop..HEAD` — base(`develop`) 대비 새 커밋 목록
   - `git diff --stat origin/develop..HEAD` — 전체 변경 규모
   - `git diff --name-status origin/develop..HEAD` — 변경 파일(모듈별 Changes 작성용)
   - `git remote get-url origin` — GitHub 리포 확인
   - `gh pr view --json number,url 2>/dev/null` — 동일 브랜치의 기존 PR 존재 여부

   > 스택 PR(다른 PR 위에 쌓는 경우)이면 base 가 `develop` 이 아니므로, 위 비교 대상(`origin/develop`)을 실제 base 브랜치로 바꾼다.

2. **중단 조건**

   - 워킹 트리에 커밋되지 않은 변경 → 사용자에게 알리고 `/commit` 또는 stash 안내 후 중단
   - 현재 브랜치가 `main` / `develop` → 중단 (브랜치 만들어서 다시 시도 안내 — 브랜치 전략상 `feat/*`·`fix/*` 등에서 PR)
   - base 대비 커밋이 0개 → 중단 (PR 올릴 변경 없음)
   - 동일 브랜치에 이미 PR 있음 → URL 보여주고 "수정/추가 푸시" 또는 "중단" 사용자 선택

3. **티켓 ID 추출 + Jira 링크 구성**

   - 브랜치명이 `AIP-{번호}` 를 포함하면(`feat/AIP-749-2`, `fix/AIP-2465` 등) 정규식으로 ID 자동 추출 (예: `AIP-749`)
   - 아니면 사용자에게 묻기 (없으면 생략)
   - 제목과 본문 Summary 에 마크다운 링크 형태로 넣기: `[AIP-1234](https://ten1010.atlassian.net/browse/AIP-1234)`

4. **본문 초안 작성** — 아래 고정 구조를 따른다:

   ```markdown
   ## Summary

   - <무엇을·왜, 한국어, 동사 마무리로 3~6 bullet>
   - 관련 티켓: [AIP-1234](https://ten1010.atlassian.net/browse/AIP-1234)

   ## Changes

   <`git diff --name-status origin/develop..HEAD` 기반, **모듈별**로 묶어 WHY 한 줄씩>

   - **리컨실러**: <변경 요지>
   - **웹훅**: <변경 요지>
   - **RBAC**: <변경 요지>
   - (생성물은 "(생성물)" 로 묶기)

   ## Test plan

   - [ ] `./gradlew compileJava compileTestJava test`
   - [ ] (RBAC 변경 시) `kubectl get clusterrole <역할> -o yaml` / `kubectl auth can-i ... --as=oidc:<유저>`
   - [ ] <사람이 확인할 재현 절차 1~2개>

   ## 🚀 Release Version

   <단계 5 에서 받은 값 (예: v5.2.0). 같은 이름의 열린 마일스톤에 사람이 직접 연결한다>
   ```

   작성 규칙:
   - **Changes 는 diff 근거로만** — 추측 금지. 변경된 모듈/파일만 기술.
   - **Test plan 체크박스는 전부 빈 `[ ]` 로만** 출력 — Claude 가 체크하거나 사유 주석을 달지 않는다 (실제 실행/체크는 사람 몫).
   - **PR 본문에 Claude/Anthropic 생성 표기를 넣지 않는다** — `🤖 Generated with Claude Code` 푸터, `Co-Authored-By` 트레일러 모두 금지.

5. **사용자 입력 질문 (필수)** — `AskUserQuestion` 으로 아래를 함께 묻는다 (인자로 이미 받은 항목은 생략):

   **(a) Release Version** — 단일선택:
   - 최근 태그/마일스톤을 참고해 후보 제시 (예: `v5.1.0` / `v5.2.0` / "Other" 직접 입력)
   - ⚠️ **추정 금지** — 반드시 사용자에게 물어서 받음. 마일스톤 수동 연결의 근거가 이 값이다.

   **(b) Draft vs Ready** — 단일선택 (`--draft`/`--ready` 인자 있으면 생략):
   - **Ready for review** — 바로 리뷰 요청 가능한 상태
   - **Draft** — 작업 중임을 표시. 나중에 GitHub 에서 "Ready for review" 로 전환

   - 사용자가 답하기 전에는 다음 단계로 넘어가지 않음.

6. **제목 작성**

   - 단일 커밋이면 그 커밋 제목 그대로 사용
   - 복수 커밋이면 가장 큰 변경 기준으로 한국어 Conventional Commits 형식 (50자 이내)
   - 티켓 ID 가 있으면 제목 끝에 `(AIP-1234)` 추가

7. **최종 본문 조립 + 사용자 확인 — 필수**

   - 5 단계에서 받은 Release Version 값을 본문에 채워 완성된 초안 출력
   - 제목 + 본문 초안을 보여주고 최종 confirm 받기
   - **승인 전에는 절대 PR 생성 금지**

8. **PR 생성**

   - `git push -u origin <branch>` (없으면 자동 push)
   - **본문은 임시 파일로 전달** — Write 툴로 본문을 `/tmp/pr-body.md` 에 **raw markdown 그대로** 쓰고
     `gh pr create --base develop --title "..." --body-file /tmp/pr-body.md` 로 생성한다.
     HEREDOC/`--body "..."` 로 인라인 전달하지 않는다.
   - ⚠️ **본문을 외곽 코드펜스(```)로 감싸지 않는다** — 외곽 펜스가 들어가면 GitHub 가 PR 본문 전체를 코드블록으로 렌더해 마크다운이 죽는다. (Mermaid 등 본문 **내부** 펜스는 유지)
   - **base 브랜치는 `develop` 이 기본** — 인자로 `--base <branch>` 가 오면(스택 PR 등) 그 값으로 override
   - draft 여부: 5(b) 의 선택(또는 `--draft`/`--ready`) → Draft 면 `gh pr create --draft`, Ready 면 플래그 없이 생성
   - ⚠️ 라벨·담당자·리뷰어·마일스톤 플래그는 붙이지 않음 — PR 생성 후 사람이 GitHub 에서 지정

9. **완료 보고**

   - 생성된 PR URL 출력
   - **담당자·리뷰어·마일스톤은 자동 부여되지 않는다** — 사용자에게 GitHub 에서 직접 지정하라고 안내한다
   - ⚠️ 브랜치 전략 검사 워크플로우(`enforce-merge-source-branch.yml`)가 소스→base 패턴을 초록/빨강으로 표시한다
     (GitHub Free 라 강제는 안 되고 시각 신호). base 가 `develop` 이면 `feat/*`·`fix/*`·`chore/*`·`docs/*`·`refactor/*`·`hotfix/*` 에서 올려야 통과.
   - ⚠️ 로컬 훅(`.claude/hooks/require-pr-metadata.sh`)이 `gh pr create` 직전에 Release Version 과
     (대형 PR 이면) "여기만은 꼭 봐주세요" 섹션을 검사해 누락 시 차단한다.

## 규칙

- **Release Version 은 절대 추정 금지** — 항상 `AskUserQuestion` 으로 물어서 받기. auto mode 라도 이 항목은 인터럽트.
- **Test plan 체크박스 자동 체크·사유 주석 금지** — 전부 빈 `[ ]` 로만.
- **`gh pr create` 에 라벨·담당자·리뷰어·마일스톤 플래그를 붙이지 않는다** — 생성 후 사람이 GitHub 에서 한 번에 지정한다.
- **PR 본문에 Claude/Anthropic 생성 표기 금지** — 푸터·`Co-Authored-By` 모두.
- **Changes 는 diff 근거로만** — 추측 금지.
- **검증 명령은 `./gradlew compileJava compileTestJava test`** — 단일 프로젝트라 모듈 접두사가 없다.
- **워킹 트리에 커밋 안 된 변경이 있으면 PR 생성 금지** — 먼저 `/commit`.
- **본문 외곽 코드펜스 금지** — raw markdown 을 `--body-file /tmp/pr-body.md` 로 전달.
- **Force push 금지** / **`--no-verify` 금지**.
- **시크릿 노출 금지** — diff 에 `.env`, 토큰, 키 포함되어 있으면 경고하고 중단.
- **base 브랜치는 `develop` 기본** — 스택 PR 이면 `--base` 로 실제 base 명시.
- 한국어 본문, 마침표 없는 동사 마무리 (커밋 규칙과 동일).

## 인자

- 인자 없음 → 대화형으로 모든 항목 수집
- `--draft` / `--ready` → draft/ready 지정 (5(b) 질문 생략)
- `--base <branch>` → base 브랜치 명시 (기본: `develop`)
- `--title "..."` → 제목 직접 지정 (Summary 분석 스킵)

$ARGUMENTS
