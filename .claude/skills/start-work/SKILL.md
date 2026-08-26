---
name: start-work
description: 작업 시작 시 분기 기준(develop / main)을 물어보고 작업 브랜치를 딴다. develop/main 에 있는 상태로 코드를 고치기 시작할 때, 또는 사용자가 "브랜치 따고 시작", "작업 브랜치 만들어" 라고 할 때 사용.
---

# 작업 브랜치 분기

이 저장소는 `develop`/`main` 에서 직접 작업하지 않는다. 한 작업 = 한 브랜치 = 한 PR 이다.
`.claude/settings.json` 의 PreToolUse 훅이 보호 브랜치에서 파일 수정·커밋을 시도할 때 확인을 요청한다.

## 절차

1. 현재 상태 확인 — 브랜치와 미커밋 변경:

   ```bash
   git rev-parse --abbrev-ref HEAD && git status --short
   ```

   이미 `develop`/`main` 이 아닌 작업 브랜치면 그대로 진행한다. 새로 딸 필요 없다.

2. **분기 기준을 사용자에게 묻는다** (AskUserQuestion, 임의로 정하지 않는다):

   - `develop` — 일반 기능·수정. PR base 도 `develop`. 대부분 이쪽이다.
   - `main` — 배포본 긴급 수정. 이때 브랜치 이름은 반드시 `hotfix/*` 여야 한다
     (`main` 은 `develop` 또는 `hotfix/*` 소스만 머지 허용,
     `.github/workflows/enforce-merge-source-branch.yml`).

   기준이 정해지면 원격 최신 상태를 받아 그 위에서 딴다:

   ```bash
   git fetch upstream && git checkout -b <브랜치명> upstream/<develop|main>
   ```

3. 브랜치 이름은 `<타입>/<작업내용>`. 타입은 `feat`, `fix`, `chore`, `docs`
   (`main` 기준이면 `hotfix`). 지라 티켓 키는 **사용자가 준 경우에만** 넣는다 —
   티켓을 찾아서 임의로 붙이지 않는다.

4. 미커밋 변경이 있으면 `checkout -b` 가 그대로 새 브랜치로 가져간다. 충돌하면
   `git stash push -u` 로 옮긴 뒤 새 브랜치에서 `git stash pop`.

5. 작업 범위가 둘 이상으로 나뉜다면 브랜치를 만들기 전에 하나로 갈지 나눌지 확인한다.
   나중에 쪼개거나 합치면 열려 있는 PR 이 닫힌다.

## 하지 말 것

- `develop`/`main` 에 직접 커밋
- 원격 브랜치 rename 으로 이름 정리 — 열린 PR 이 닫힌다. 이름이 잘못됐으면 새 브랜치를
  만들고 PR 을 다시 여는 편이 예측 가능하다.
