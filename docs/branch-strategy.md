# Project Controller 브랜치 전략

- 대상 저장소: `ten1010-io/project-controller`
- **정책 원본은 `aipub-backend` 의 `docs/branch-strategy.md` 다.** 이 리포는 그 정책을 그대로 따르며, 이 문서는 실무에 필요한 부분만 옮겨 둔 요약이다. 정책이 갱신되면 aipub-backend 쪽이 기준이고 여기를 맞춘다.
- 과거에 쓰던 `staging/project` 기본 브랜치 방식은 폐기됐다 (레포가 `resource-group-controller` → `project-controller` 로 이관되면서 정리).

---

## 1. 브랜치 종류와 역할

| 브랜치 | 역할 |
|---|---|
| `main` | 현재 서비스 중인(고객 배포) 버전. 항상 "최신 릴리즈"를 가리킴 |
| `develop` | 다음 버전 개발 브랜치 |
| `feature/*`, `feat/*` | 신규 기능 개발 브랜치. `develop` 으로만 머지 |
| `fix/*` | `develop` 대상 버그 수정 브랜치. `develop` 으로만 머지 |
| `chore/*` | 빌드/CI/의존성 등 기능과 무관한 잡무 브랜치. `develop` 으로만 머지 |
| `docs/*` | 문서 전용 변경 브랜치. `develop` 으로만 머지 |
| `refactor/*` | 동작 변경 없는 구조 개선 브랜치. `develop` 으로만 머지 |
| `hotfix/*` | 서비스 중인 버전(`main` 또는 특정 `released/x.y.z`) 대상 긴급 수정 브랜치 |
| `released/x.y.z` | 과거 버전 중 **실제로 hotfix 가 필요해진 버전에 한해** 해당 태그에서 파생되는 유지보수 브랜치 |
| 태그 `vX.Y.Z` | 모든 릴리즈/hotfix 배포 시점에 예외 없이 찍는 불변 스냅샷 |

브랜치명은 `{type}/{key}` 를 쓴다 (예: `feat/AIP-34`). 한 티켓이 PR 을 여러 개 가질 수 있고, 이미 머지된 브랜치명과 충돌하면 접미사로 구분한다 (예: `feat/AIP-34-crd`).

작업 시작:

```bash
git fetch origin develop && git checkout -b <type>/<key> origin/develop
```

---

## 2. 버전 전환(cutover)

### 2.1 태그는 매번, `released/*` 브랜치는 필요할 때만

컷오버마다 `released/x.y.z` 브랜치를 자동으로 만들지 않는다. 태그는 예외 없이 찍는다.

새 버전 개발이 시작되는 시점:

1. `develop` 을 `main` 에 머지하기 직전, 교체될 `main` 에 태그(`vX.Y.Z`)를 먼저 찍는다 — **예외 없이 매번.**
2. `develop` 을 `main` 에 머지 → `main` 이 새 서비스 브랜치가 된다.
3. `develop` 은 계속 다음 버전 개발 브랜치로 사용한다.
4. 옛 버전에 **실제로 hotfix 요청이 들어오는 시점에만** 그 태그에서 브랜치를 판다: `git checkout -b released/5.0.0 v5.0.0`.

`.github/workflows/enforce-merge-source-branch.yml` 의 `check-cutover-tag` 잡이 1번을 검사한다 — `develop -> main` PR 에서 현재 `main` HEAD 에 태그가 없으면 실패로 표시된다.

### 2.2 지원 종료 시 정리

지원이 끝난 `released/x.y.z` 브랜치는 삭제한다. 태그가 같은 커밋을 불변으로 가리키므로 히스토리 유실은 없고, 필요하면 태그에서 다시 브랜치를 팔 수 있다.

---

## 3. hotfix 백포트

브랜치 전체를 순차 머지하지 않는다 — `released/*`·`main`·`develop` 은 히스토리가 크게 달라 충돌이 급증하고, 다른 브랜치에서 이미 다르게 고쳐진 버그와 부딪힐 수 있다.

1. `released/x.y.z` 가 없으면 태그에서 판다.
2. `hotfix/*` 를 그 브랜치에서 생성하고 **그 브랜치에만 정식 머지** → 태그 → 배포.
3. 다른 브랜치에는 해당 커밋만 `git cherry-pick` 으로 개별 적용한다.

---

## 4. 머지 소스 제한

GitHub 브랜치 보호는 머지 대상(base)만 제어할 뿐 소스(head) 브랜치를 패턴으로 제한하지 못한다. 그래서 Actions 로 검증한다.

### 4.1 허용 규칙

| base | 허용되는 head |
|---|---|
| `main` | `develop`, `hotfix/*` |
| `develop` | `hotfix/*`, `feature/*`, `feat/*`, `fix/*`, `chore/*`, `docs/*`, `refactor/*` |
| `released/*` | `hotfix/*` |

### 4.2 워크플로우

`.github/workflows/enforce-merge-source-branch.yml` 이 두 잡으로 구성된다.

- **`check-source-branch`**: 4.1 규칙표를 그대로 검사.
- **`check-cutover-tag`**: `develop -> main` PR 에서만 동작하며 2.1 의 "컷오버 전 태그 먼저"를 검사.

> 조직(`ten1010-io`)이 GitHub Free 플랜이라 private 저장소에서는 branch protection/ruleset 을 걸 수 없다. 따라서 이 체크는 머지를 강제로 막지 못하고 PR 화면의 시각 신호로만 동작한다. Team 이상으로 업그레이드되면 Required status check 로 등록해 강제할 수 있다.

---

## 5. aipub-backend 와의 차이 (미적용 항목)

`.github/workflows/pr-auto.yml` (작성자 배정·리뷰어 자동 요청, Release Version → 마일스톤 연동, 대형 PR 리뷰 포인트 강제)은 **아직 도입하지 않았다.** 이 리포에 `secrets.SECRET`(PAT)이 없어서다. 시크릿을 추가하면 aipub-backend 의 파일을 그대로 가져오면 된다.
