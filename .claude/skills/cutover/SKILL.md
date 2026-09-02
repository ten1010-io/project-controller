---
name: cutover
description: "project-controller develop -> main 버전 컷오버(승격) 절차를 docs/branch-strategy.md 정책대로 안내/수행하는 스킬. '컷오버', '버전 승격', 'develop을 main으로', '새 버전 릴리즈' 등의 요청 시 사용."
---

# 버전 컷오버 (develop → main)

`docs/branch-strategy.md`의 2.1/2.2절 정책을 실제로 실행하는 절차. 이 문서를 신뢰의 원천으로 삼는다 — 정책이 바뀌면 이 스킬보다 그 문서를 먼저 갱신할 것.

핵심 원칙 (반드시 지킬 것):
- **컷오버 시점에 `released/x.y.z` 브랜치를 만들지 않는다.** 태그만 찍는다. 브랜치는 나중에 그 버전에 실제로 hotfix가 필요해졌을 때만 만든다.
- 태그·푸시·PR 생성은 전부 원격 저장소에 영향을 주는 되돌리기 어려운 작업이다 — 각 단계 실행 전 사용자에게 무엇을 할지 보여주고 확인받는다. 한 번의 컷오버 요청이 이후 컷오버까지 자동 승인하는 게 아니다.

## 절차

1. **사전 상태 확인**
   - `git status --short`로 작업 트리가 깨끗한지 확인. 더러우면 사용자에게 알리고 중단.
   - `git fetch origin main develop --tags`로 최신 상태 동기화.
   - `git log origin/main..origin/develop --oneline`로 이번 컷오버에 포함될 커밋 목록을 사용자에게 보여준다.

2. **버전 번호 확인**
   - `build.gradle` 의 `version` 은 `0.1.0-SNAPSHOT` 같은 플레이스홀더이고 실제 릴리즈 버전과 무관하다 — 버전은 태그로 관리한다. 따라서 **사용자에게 이번에 승격할 버전 번호를 직접 물어봐야 한다** (예: "5.2.0"). 추측하지 말 것.
   - 기존 태그 네이밍이 `v5.0.0`처럼 `v` 접두사가 붙은 것과 `1.9.0`처럼 안 붙은 것이 섞여 있음(`git tag -l`로 확인 가능) — 가장 최근 태그들의 접두사 관례를 확인하고 사용자에게 어느 쪽을 쓸지 확인.

3. **현재 main에 태그 찍기**
   - `git tag --points-at origin/main`으로 이미 태그가 있는지 먼저 확인 — 있으면 이 단계는 건너뛰고 사용자에게 알린다(중복 태그 방지).
   - 없으면 `git tag <버전> origin/main` (또는 annotated: `git tag -a <버전> -m "<버전>" origin/main`) 생성 계획을 사용자에게 보여주고 확인받은 뒤 실행, `git push origin <버전>`으로 푸시.
   - 이 태그가 바로 `check-cutover-tag` GitHub Actions job(`.github/workflows/enforce-merge-source-branch.yml`)이 검사하는 대상이다 — 이 단계를 건너뛰면 이후 PR의 CI가 FAIL로 표시된다(단, GitHub Free 플랜이라 머지 자체를 막지는 못함, 시각적 신호일 뿐).

4. **develop → main PR 생성**
   - `gh pr create --base main --head develop --title "..." --body "..."` 실행 계획을 사용자에게 보여주고 확인받은 뒤 생성. PR 본문에 이번 컷오버로 승격되는 버전과 방금 찍은 태그를 명시.
   - `enforce-merge-source-branch.yml`의 `check-source-branch`(head가 develop이므로 통과)와 `check-cutover-tag`(방금 태그 찍었으므로 통과) 둘 다 자동으로 돈다 — 결과를 사용자에게 알려준다.

5. **머지 후 안내(참고용, 이 스킬이 직접 수행하지 않음)**
   - PR이 실제로 머지되는 시점은 사용자의 리뷰/승인 이후이므로 이 스킬은 머지 자체를 수행하지 않는다.
   - 머지되면 `develop`에 이미 있던 `enforce-merge-source-branch.yml`이 `main`에도 자동으로 반영된다(별도 작업 불필요) — `docs/branch-strategy.md` 5번 섹션의 관련 TODO가 이 시점에 자연히 해소됨을 사용자에게 알려줘도 좋다.
   - **`released/<이전버전>` 브랜치는 지금 만들지 않는다.** 나중에 그 버전에 실제로 hotfix 요청이 들어오면 그때 `git checkout -b released/<이전버전> <이전버전 태그>`로 만든다(`docs/branch-strategy.md` 3.2절 0번 단계).

## 하지 말아야 할 것

- 태그 없이 PR부터 만들지 말 것(2단계/3단계를 건너뛰지 말 것).
- 컷오버 시점에 `released/*` 브랜치를 선제적으로 만들지 말 것 — 이건 명시적으로 폐기된 관례(git-flow 방식과의 핵심 차이).
- 버전 번호를 파일이나 커밋 메시지에서 추측하지 말 것 — 반드시 사용자에게 확인.
