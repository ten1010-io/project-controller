# 케이스별 레시피

경로는 `src/main/java/io/ten1010/aipub/projectcontroller/` 기준. 줄 번호는 바뀌므로 **심볼 이름으로 찾는다**.
각 절의 "대표 커밋"을 `git show <sha> -- src/main src/test` 로 열어 미러링하는 게 가장 정확하다.

---

## 케이스 1 — 프로젝트 namespace 공용 (P-Role)

**파일:** `domain/k8s/ProjectApiConstants.java`, `domain/k8s/ReconciliationService.java`

1. `ProjectApiConstants` 에 `XXX_RESOURCE_PLURAL` 상수를 추가한다.
2. `reconcileProjectRoleRules` 의 두 분기에 같은 리소스를 넣는다.
   - `PROJECT_MANAGER` → `.withVerbs("*")`
   - `PROJECT_DEVELOPER` → `.withVerbs(BASIC_VERBS)` (`create, get, watch, list`)
   - 생성자 전용 수정/삭제(케이스 2)와 짝이면 PD 쪽에 한 줄 주석: "수정/삭제는 생성자 본인의 AipubUser Role 에만 붙는다".
3. 각 분기 끝의 `yield List.of(...)` 에 규칙 변수를 **양쪽 다** 추가한다 (한쪽만 넣는 실수가 잦다).

인포머·watch 는 필요 없다 — Project 이벤트로 리컨실된다.

---

## 케이스 2-a — 생성자만 수정/삭제, CRD (U-Role)

**대표 커밋:** AIP-3518 `772c9a6` (WorkloadGroup), FileServer `7ab26f6` (#110)
**전제:** 케이스 1도 함께 적용한다.

| # | 파일 | 할 일 |
|---|---|---|
| 1 | `domain/k8s/ProjectApiConstants.java` | `XXX_RESOURCE_KIND`, `XXX_RESOURCE_PLURAL` |
| 2 | `domain/k8s/dto/V1alpha1Xxx.java`, `V1alpha1XxxList.java` | **metadata 만** 담은 DTO (`@Data`, `apiVersion`/`kind`/`metadata` 셋 다 `@Nullable`). CRD 스키마에 의존하지 않게 spec 은 넣지 않는다 |
| 3 | `domain/k8s/K8sApiProvider.java` | `GenericKubernetesApi<V1alpha1Xxx, V1alpha1XxxList>` 필드 + `createXxxApi(apiClient)` + getter |
| 4 | `informer/SharedInformerFactoryProvider.java` | `registerXxxInformer` — `sharedIndexInformerFor(...)` + `NAMESPACE_TO_OBJECTS_INDEXER_NAME` 인덱서. 생성자의 register 목록에 호출 추가 |
| 5 | `domain/k8s/WorkloadResourceResolver.java` | `resolveResource` 의 `switch (kind)` 에 `case "Xxx" -> "xxxs"` **와** `instanceof V1alpha1Xxx` 폴백 (인포머 오브젝트는 kind 가 비어 있을 수 있다). batch 외 그룹이면 `resolveGroup` 은 기본값 `AIPUB_GROUP` 을 그대로 쓴다 — 다른 그룹이면 분기 추가 |
| 6 | `controller/watch/OnUpdateFilterFactory.java` | `xxxFilter()` — ownerReferences 또는 labels 가 바뀌었을 때만 true |
| 7 | `controller/watch/RequestBuilderFactory.java` | `xxxToAipubUserRoles()` — namespace + `UsernameUtils.getUsername` → `aipubUserRoleNameResolver.resolveRoleName(username)` 로 `Request` 하나. 라벨이 없으면 `List.of()` |
| 8 | `controller/rbac/aipub/AipubUserRoleControllerFactory.java` | `withReadyFunc(...V1alpha1Xxx.class)::hasSynced)` + `.watch(this::createXxxWatch)` + `createXxxWatch` 메서드 (필터·RequestBuilder 연결) |
| 9 | `controller/rbac/aipub/AipubUserRoleReconciler.java` | `Indexer<V1alpha1Xxx>` 필드, 생성자에서 `getExistingSharedIndexInformer` 로 획득, `reconcileInternal` 에서 `byIndex(NAMESPACE_TO_OBJECTS_INDEXER_NAME, ns)` → `workloads.addAll(...)` |

`ReconciliationService.reconcileAipubUserRoleRules` 는 손대지 않는다 — `workloads` 를 돌며 label 의 username 이 일치하면 `buildUpdateDeleteRoleRule(group, resource, name)` 를 만든다.

웹훅: `aipub.ten1010.io` 의 Namespaced 리소스는 `mutating-webhook-user-v2.yaml` 이 `*` 로 이미 label 을 찍는다. 다른 그룹이면 rules 에 추가한다 (이 repo + `aipub-installer` helm 차트).

> 대안 검토 메모: 케이스 2-b 의 `OwnedObjectInformerManager` 는 GVR + `V1PartialObject` 로 동작하므로 CRD 도 `OWNED_TARGETS` 로 흡수할 수 있을 가능성이 있다. 다만 (1) CRD 가 설치되지 않은 클러스터에서 인포머가 실패하는 방식, (2) 2-a 의 `OnUpdateFilter` 없이 이벤트량이 괜찮은지를 검증하지 않았다. 일반화하려면 별도 티켓으로 다룬다.

---

## 케이스 2-b — 생성자만 수정/삭제, 네이티브 리소스 (U-Role)

**대표 커밋:** `c28c91b`, `cf432d1`

1. `domain/k8s/OwnershipPolicy.java` 의 `OWNED_TARGETS` 에 `new ResourceTarget(group, version, plural)` 를 추가한다.
2. **웹훅 rules** — `kubernetes/controller/project-controller/templates/mutating-webhook-user-v2.yaml` 과 `aipub-installer` helm 차트에서 해당 리소스의 CREATE 를 인터셉트하는지 확인한다. 인터셉트하지 않으면 label 이 없어 **생성자 본인도 수정·삭제를 못 한다.**
3. 시스템 컴포넌트가 사용자 오브젝트의 label 을 복사해 파생물을 만드는 타입이면(예: Endpoints) `SYSTEM_DERIVED_LABEL_KEYS` 로 제외한다.
4. 웹훅 인터셉트 대상을 넓히면 시스템 컴포넌트가 만드는 오브젝트까지 걸리는지 확인한다 — 웹훅이 실패하면 생성이 막힌다 (events 는 그래서 제외됨).

---

## 케이스 3 — 프로젝트에 속한 것만 조회 (P-CR, `resourceNames`)

**파일:** `ReconciliationService.reconcileClusterRoleRules(project, role, …)` / **대표 커밋:** AIP-3631 `d15d83f`

공통 형태:

```java
// <리소스> 는 이름이 <출처> 와 같다. Cluster scope 라 list 는 resourceNames 로 좁혀지지 않아 get 만 준다.
if (!names.isEmpty()) {
  V1PolicyRule xxxApiRule = switch (projectRoleEnum) {
    case PROJECT_MANAGER, PROJECT_DEVELOPER -> new V1PolicyRuleBuilder()
        .withApiGroups(ProjectApiConstants.XXX_GROUP)
        .withResources(ProjectApiConstants.XXX_RESOURCE_PLURAL)
        .withResourceNames(names)
        .withVerbs("get")
        .build();
  };
  reconciled.add(xxxApiRule);
}
```

| 하위 | 이름 출처 | 이미 있는 변수 | 추가 작업 |
|---|---|---|---|
| 3-a | 프로젝트명 | `K8sObjectUtils.getName(project)` | 없음 (비지 않으므로 `isEmpty` 가드 불필요) |
| 3-b | 바인딩 노드명 | `nodes` | `isEmpty` 가드 필수 |
| 3-c | 바인딩 NodeGroup/ResourceSet | `nodeGroups`, `resourceSetNames` | 가드 필수. 새 바인딩 종류라면 3-d 처럼 다룬다 |
| 3-d | 다른 리소스에서 계산 | — | 아래 절차 |

### 3-d — 파생 관계 (대표: AIP-34 `618ceef`, PV)

1. **리졸버**: `controller/BoundObjectResolver` 에 `getAllBoundXxx(project)` 를 추가한다(필요하면 판정 로직은 `XxxUtils` 순수 함수로 — 예: `PersistentVolumeUtils.isUnclaimed`).
2. **시그니처**: `reconcileClusterRoleRules(...)` 에 `List<Xxx> readableXxx` 파라미터를 추가한다 → **모든 호출부와 기존 `ReconciliationService*RulesTest` 의 호출을 같이 고친다.**
3. **호출부**: `controller/rbac/member/ClusterRoleReconciler` 에서 리졸버 결과를 넘긴다.
4. **watch**: `ClusterRoleControllerFactory` 에 `withReadyFunc` + `createXxxWatch` (인포머가 없으면 `SharedInformerFactoryProvider` 등록 먼저).
5. **필터**: `OnUpdateFilterFactory` — 이름 집합에 영향을 주는 필드가 바뀔 때만 true.
6. **RequestBuilder**: `RequestBuilderFactory.xxxToProjectRoles(namespacedRole)` — 영향 받는 프로젝트만 큐잉한다. **조회 대상이 아닌 오브젝트는 빈 목록**을 반환해 전 프로젝트로 팬아웃하지 않게 한다 (컨트롤러가 `withWorkerCount(1)` 이라 다른 권한 리컨실이 밀린다). `DefaultControllerWatch.onUpdate` 가 old/new 합집합을 큐잉하므로 전이 수렴성은 유지된다.
7. 이름은 `distinct().sorted()`.

---

## 케이스 4 — PM 만 추가 권한

케이스 1·3 규칙의 `switch` 에서 `PROJECT_MANAGER` 와 `PROJECT_DEVELOPER` 를 갈라 verbs 를 다르게 주거나, `if (projectRoleEnum == ProjectRoleEnum.PROJECT_MANAGER)` 블록에 규칙을 추가한다 (예: 멤버 userresourcequotas get — `boundAipubUsers` 이름을 정렬해서 사용, 비면 생략).

---

## 케이스 5 — 사용자 본인 cluster 리소스 (U-CR)

`ReconciliationService.reconcileClusterRoleRules(V1alpha1AipubUser)` 의 `List.of(...)` 에 `withResourceNames(K8sObjectUtils.getName(aipubUser))` + `get` 규칙을 추가한다. 리소스 이름이 username 과 1:1 일 때만 해당한다. PM 이 멤버 것을 봐야 하면 케이스 4도 같이 적용한다.

---

## 케이스 6 — 공용 카탈로그

`reconcileClusterRoleRules(project, …)` 에 `resourceNames` 없이 `get, list`. **추가하기 전에** 다른 프로젝트 정보(claimRef, 경로, 사용자명 등)가 spec/status 에 담겨 있지 않은지 확인한다 — 담겨 있으면 케이스 3 으로 좁혀야 한다 (PV 가 이 이유로 AIP-34 에서 3-d 로 옮겨졌다).

---

## 케이스 7 — create 전용 Review/Validation API

- 로그인 사용자 전원(프로젝트 무관): `kubernetes/controller/project-controller/templates/cluster-role.yaml` 의 `aipub-member.project.aipub.ten1010.io` 에 `create` 규칙을 추가하고, **`aipub-installer` helm 차트의 같은 매니페스트도 같이** 고친다. 정적이라 리컨실러로 회수되지 않는다.
- 프로젝트 멤버에게만: P-CR 에 `create` 규칙(예: tcpportvalidations).

---

## 케이스 8 — 서브리소스

상위 리소스와 같은 규칙의 `withResources(...)` 에 `"xxx/log"`, `"xxx/status"` 처럼 명시한다. 와일드카드 `"*"` verbs 라도 서브리소스는 따로 적어야 한다.

---

## 테스트

위치: `src/test/java/io/ten1010/aipub/projectcontroller/`. 이름은 `<상황>_<기대>`, `@DisplayName` 은 선택. `SharedInformerFactory` 를 가짜로 세우지 않고 `ReconciliationService` 를 직접 호출한다.

| 케이스 | 본뜰 테스트 | 꼭 넣을 단언 |
|---|---|---|
| 1 / 2-a | `domain/k8s/ReconciliationServiceWorkloadGroupRulesTest` | PD = BASIC_VERBS, PM = `*`, 본인 소유 → `resourceNames=[name]` + update/patch/delete, 타인 소유·label 없음 → 규칙 없음, kind 누락 시 `instanceof` 폴백 |
| 2-a watch | `controller/watch/WorkloadGroupOwnerTransferWatchTest` | 소유자 label 변경 시 이전·새 소유자 Role 둘 다 큐잉 |
| 2-b | `domain/k8s/ReconciliationServiceOwnedRulesTest` | |
| 3 | `domain/k8s/ReconciliationServiceInfinibandRulesTest` | get 만 (list·쓰기 없음), resourceNames 정확, **이름 목록이 비면 규칙 생략**, 반복 리컨실 동일 |
| 3-d | `domain/k8s/ReconciliationServicePersistentVolumeRulesTest`, `controller/watch/PersistentVolumeRequestBuilderTest` | 정렬·중복 제거 멱등성, 조회 대상이 아닌 오브젝트는 빈 Request |
| 4 | `domain/k8s/ReconciliationServiceCueueRulesTest` | PM/PD verbs 차이 |

클러스터가 필요한 확인(`kubectl auth can-i`, 실제 리컨실 결과)은 PR Test plan 의 사람 확인 항목으로 남긴다.
