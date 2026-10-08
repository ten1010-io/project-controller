# Cueue 권한·프로비저닝 라벨 — QA 보고서

- Jira: [AIP-3419](https://ten1010.atlassian.net/browse/AIP-3419)
- 브랜치: `feat/AIP-3419` (base `develop` a23eea8)
- **종합 판정: PASS**

## 1. 빌드·테스트

| 항목 | 결과 |
|---|---|
| `./gradlew compileJava compileTestJava test` | **BUILD SUCCESSFUL** |
| 테스트 | **275 tests / 0 failures / 0 errors / 0 skipped** |
| 그중 이번 작업 | 24건 (Cueue 룰 4 + 프로비저닝 14 + watch 필터 6) |
| `crd.yaml` | multi-doc 8건 파싱 OK, 마지막이 `cueues.coaster.ten1010.io` |

검증자가 처음 돌렸을 때 전 태스크가 `UP-TO-DATE`(Gradle 캐시)로 떠서 `clean` 을 붙여 재실행해
실제 컴파일·테스트를 확인했다.

## 2. 컨벤션 체크리스트

`.claude/rules/controller-code-style.md` 기준.

| 항목 | 결과 |
|---|---|
| 들여쓰기 2칸, 탭 없음 | PASS |
| 후행 공백 없음 | PASS |
| 추가된 줄 100자 이하 | PASS |
| 필드 접근 `this.` | PASS |
| 주석 한국어, 1줄 위주 | PASS |
| **주석에 Jira 키 없음** | PASS (0건) |
| 로그·예외 메시지 영어 | PASS (신규 로그 없음) |
| nullable 은 `org.jspecify.annotations.Nullable` | PASS |
| enum switch 식에 `default` 없음 | PASS |
| 테스트 네이밍 `<상황>_<기대>` 2단 | PASS |
| 최소 diff (무관한 재포맷 없음) | PASS (임포트 추가 외 포맷 변경 0) |

**직접 수정한 이슈: 없음.** 컴파일 에러 0, 컨벤션 위반 0.

## 3. 멱등성

| 경로 | 결과 | 근거 |
|---|---|---|
| 노드 라벨 | PASS | 같은 입력 2회 → 동일 결과. 순서 바꿔도 동일 |
| 네임스페이스 라벨 | PASS | 동일 |
| 네임스페이스 어노테이션 | PASS | 동일 |
| RBAC 룰 | PASS | 동일 |

**핵심 확인** — `NamespaceReconciler.reconcileExistingNamespace` 가 annotations 를 **비교 조건과
빌더 양쪽 모두**에 반영했다. 한쪽만 했으면 매 주기 UPDATE 가 돌거나(비교 누락) 반대로 영원히
반영되지 않는다(빌더 누락). 생성 경로(`reconcileNoExistingNamespace`)에도 `.withAnnotations()` 가
들어가 있다.

라벨·어노테이션은 `Map.equals` 비교라 순서 무관. 새로 추가된 List 경로는 없다.

**ClusterRole 1회 UPDATE** — 룰을 리스트 중간에 삽입해 `List.equals` 순서 비교가 깨지므로 배포 직후
기존 프로젝트 ClusterRole 이 1회 갱신된다. 의도된 1회성이며 이후 멱등이다.

## 4. 파괴적 동작

| 항목 | 결과 |
|---|---|
| 접두 매칭이 지나치게 넓지 않은가 | PASS — 접두가 `/` 로 끝나 `project-name.aipub.ten1010.io.something/...` 을 잡지 않는다 |
| 모르는 어노테이션 보존 | PASS — `new HashMap<>(existing)` 패턴. `last-applied-configuration` 안전 |
| `auto-enqueue` 보존 | PASS — 테스트로 고정 |
| allowlist 네임스페이스 skip | PASS — `reconcileInternal` 최상단 early return 이 annotation 계산보다 위에 그대로 있다 |
| project == null / terminating 처리 | PASS — 기존 라벨 처리와 일관 |
| 바인딩 해제 시 라벨 제거 | PASS — 테스트로 고정 |

## 5. RBAC

| 항목 | 결과 |
|---|---|
| 매니저 `get`/`patch`/`update` | PASS |
| 멤버 `get` | PASS |
| **양쪽 다 `list`/`watch`/`deletecollection` 없음** | PASS |
| `resourceNames` = 프로젝트 이름 | PASS |
| switch 식에 `default` 없음 | PASS |
| 기존 룰 무변경 | PASS — diff 가 +18줄 추가뿐 |

ClusterRole 은 매 리컨실마다 룰을 전량 교체하므로 기존 규칙을 건드리면 클러스터에서도 사라진다.
diff 상 기존 규칙에 손댄 곳이 없음을 확인했다.

## 6. 교차 영향

### 6.1 NodeGroup 매칭 — 영향 없음 (코드로 결론)

`BoundObjectResolver.getBoundNodeGroupsByNodeSelector` 는
`labelStringsSet.containsAll(selectorLabelStrings)` 부분집합 판정이라 라벨 추가는 매칭을 **넓히기만**
한다. 넓어지려면 NodeGroup 이 정확히 그 키를 selector 에 써야 하는데, 이 접두는 워크스페이스 5개
repo 전체 grep **0건**인 신규 키다. → 기존 NodeGroup 영향 0.

다만 **새 결합이 생긴다**: 앞으로 `nodeSelector: {project-name.aipub.ten1010.io/A: ""}` 인 NodeGroup 을
프로젝트 B 에 바인딩하면 A 의 노드가 B 에도 전이 바인딩되어 테넌트 경계가 넓어진다. 수렴은 보장된다
(단조 증가 → 고정점, 진동 없음). → **`PROJECT_NAME_KEY_PREFIX` Javadoc 에 금지를 명시해 조치 완료.**

### 6.2 웹훅 — 영향 없음

신규 라벨·어노테이션이 어떤 webhook `rules` 에도 걸리지 않는다. `namespaceSelector` 는 전부
`kubernetes.io/metadata.name` 기준이다. `namespaces` 웹훅이 UPDATE 를 가로채지만 allowlist 가드와
reserved 삭제 가드뿐이라 리컨실러와 충돌하지 않는다. 노드를 인터셉트하는 웹훅은 없다.

### 6.3 watch 필터

- `projectNamespaceFilter()` 는 네임스페이스 watch 한 곳에서만 쓰인다.
- 노드 쪽 `nodeFilter()` 는 이미 labels 를 비교하므로 3-1 에 "조용히 안 고쳐짐" 버그는 없다. 다만
  이 필터를 10곳 이상 컨트롤러가 공유해 배포 직후 1회 리컨실 버스트가 있다(전부 no-op).

### 6.4 프론트

메모리 `rbac-resourcenames-ui-contract` 대로 프론트는 `UserAuthorityReview` + 이름별 GET 폴백이 있어
**aipub-web 변경 불필요**. `UserAuthorityReviewMutateHandler` 도 리소스 비의존 구현이라 수정 불필요.

## 7. QA 후 리더가 추가 조치한 것

QA 가 미해결로 넘긴 항목 중 2건을 반영했다.

1. **`PROJECT_NAME_KEY_PREFIX` Javadoc 에 NodeGroup `spec.nodeSelector` 사용 금지 명시** (§6.1)
2. **`ProjectNamespaceFilterTest` 신규 작성 (6건)** — `projectNamespaceFilter()` 의 annotation 비교가
   테스트로 고정돼 있지 않던 공백을 메웠다. 분석 단계에서 "조용히 깨지는 버그"로 지목된 지점이라
   회귀 방지 가치가 크다. 커버: node-selector 제거/값 변조, provisioning-enabled 제거, ownerReference
   제거, 무변경 시 차단, 무관한 어노테이션 추가.

## 8. 사람이 클러스터에서 확인할 항목

단위 테스트로 흉내 낼 수 없어 PR Test plan 으로 넘긴다.

- [ ] `kubectl auth can-i get cueues/<proj> --as=oidc:<매니저>` → yes
- [ ] `kubectl auth can-i patch cueues/<proj> --as=oidc:<매니저>` → yes
- [ ] `kubectl auth can-i list cueues --as=oidc:<매니저>` → **no**
- [ ] `kubectl auth can-i get cueues/<proj> --as=oidc:<멤버>` → yes
- [ ] `kubectl auth can-i patch cueues/<proj> --as=oidc:<멤버>` → **no**
- [ ] 프로젝트 네임스페이스에 `provisioning-enabled` 라벨과 `node-selector` 어노테이션이 붙는지
- [ ] 바인딩된 노드에 `project-name.aipub.ten1010.io/<proj>` 라벨이 붙는지
- [ ] Coaster 가 프로젝트와 같은 이름의 Cueue 를 실제로 생성하는지
- [ ] 배포 직후 프로젝트 ClusterRole 1회 UPDATE 후 더 이상 갱신이 돌지 않는지
