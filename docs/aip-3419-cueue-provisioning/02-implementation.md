# Cueue 권한·프로비저닝 라벨 — 구현

- Jira: [AIP-3419](https://ten1010.atlassian.net/browse/AIP-3419)
- 브랜치: `feat/AIP-3419` (base `develop` a23eea8)
- 규모: 11개 파일, +775 / -9

## 1. 변경 파일

### 신규 — 테스트 (3)

| 파일 | 건수 |
|---|---|
| `domain/k8s/ReconciliationServiceCueueRulesTest.java` | 4 |
| `domain/k8s/ReconciliationServiceCueueProvisioningTest.java` | 14 |
| `controller/watch/ProjectNamespaceFilterTest.java` | 6 |

### 수정 (8)

| 파일 | 변경 |
|---|---|
| `domain/k8s/ProjectApiConstants.java` | `CUEUE_RESOURCE_PLURAL` |
| `domain/k8s/LabelConstants.java` | `PROJECT_NAME_KEY_PREFIX`, `CUEUE_PROVISIONING_ENABLED_KEY/VALUE` |
| `domain/k8s/AnnotationConstants.java` | `CUEUE_NODE_SELECTOR_KEY` |
| `domain/k8s/ReconciliationService.java` | `cueuesApiRule`, `reconcileNodeLabels` 시그니처, `reconcileNamespaceLabels`, `reconcileNamespaceAnnotations` 신설 |
| `controller/cluster/NodeReconciler.java` | `reconcileNodeLabels` 에 `boundProjects` 전달 |
| `controller/cluster/NamespaceReconciler.java` | annotation 리컨실 배선(양쪽 경로) |
| `controller/watch/OnUpdateFilterFactory.java` | `projectNamespaceFilter()` annotation 비교 |
| `kubernetes/examples/crd.yaml` | Cueue CRD append (+172줄) |

## 2. CRD 스펙

`kubernetes/examples/crd.yaml` 맨 끝에 `cueues.coaster.ten1010.io` 를 추가했다. Confluence §1 원문
스키마 그대로이고, 같은 파일의 다른 `coaster.ten1010.io` 그룹 CRD 와 형식을 맞췄다. multi-doc 8건으로
파싱되며 마지막 문서가 Cueue 다.

요지: Cluster scope, `v1alpha1`, `status` 서브리소스, shortName `cq`, printer column 4개
(Priority / Policy / Head-Blocked / Age).

> project-controller 가 이 CRD 를 설치하지는 않는다 — Coaster 가 소유한다. 이 파일은 예시·참조용이다.

## 3. 리컨실 흐름

### 3.1 노드 라벨

```
NodeReconciler.reconcileInternal
  └ boundProjects = BoundObjectResolver.getAllBoundProjects(node)   // 직접 + NodeGroup 경유
  └ reconcileNodeLabels(node, boundProjects)
        ① 기존 project-managed / isolation-mode 처리 (변경 없음)
        ② reconciled.keySet().removeIf(k -> k.startsWith("project-name.aipub.ten1010.io/"))
        ③ if (!NodeUtils.isProjectManaged(node)) return reconciled;   ← 게이트
        ④ boundProjects 마다 "project-name.aipub.ten1010.io/<proj>" → "" put
  └ 라벨·어노테이션·테인트가 모두 같으면 UPDATE 생략
```

②를 ③ 앞에 둔 것이 핵심이다 — 노드가 프로젝트 관리에서 빠지면 기존 라벨이 회수된다.

### 3.2 네임스페이스 라벨·어노테이션

```
NamespaceReconciler.reconcileInternal
  └ allowlist 네임스페이스면 early return (기존 분기, annotation 계산보다 위)
  └ reconcileNamespaceLabels(ns, project)
        remove(project-label), remove(provisioning-enabled)
        project == null 이면 반환
        put(project-label), put(provisioning-enabled = "true")
  └ reconcileNamespaceAnnotations(ns, project)        ← 신설
        remove(cueue node-selector)
        project == null 이면 반환
        put(node-selector = "project-name.aipub.ten1010.io/<proj>")
  └ reconcileExistingNamespace: ownerRefs·labels·annotations 를 모두 비교 → 다를 때만 replace
  └ reconcileNoExistingNamespace: 생성 시 labels·annotations 함께 주입
```

둘 다 `new HashMap<>(existing)` 으로 시작해 자기 키만 remove/put 하므로 **모르는 라벨·어노테이션은
보존**된다. `auto-enqueue` 와 `kubectl.kubernetes.io/last-applied-configuration` 이 살아남는 근거다.

### 3.3 watch 필터

`projectNamespaceFilter()` 가 ownerReferences + labels 에 더해 **annotations 를 비교**한다. informer
resync period 가 `0` 이라 이게 빠지면 어노테이션 훼손이 복구되지 않는다.

노드 쪽은 `nodeFilter()` 가 이미 labels 를 비교하고 있어 추가 작업이 없었다.

### 3.4 RBAC

`gpuConfigsApiRule` 바로 뒤에 `cueuesApiRule` 을 넣었다. `ProjectRoleEnum` 이 2개뿐이라 switch 식에
`default` 를 두지 않았다 — 값이 늘면 컴파일 에러로 잡힌다.

| 역할 | apiGroup | resource | resourceNames | verbs |
|---|---|---|---|---|
| `PROJECT_MANAGER` | `coaster.ten1010.io` | `cueues` | 프로젝트 이름 | `get`, `patch`, `update` |
| `PROJECT_DEVELOPER` | `coaster.ten1010.io` | `cueues` | 프로젝트 이름 | `get` |

`ClusterRoleReconciler` 가 룰 리스트를 `List.equals`(순서 비교)로 판정하므로, 중간 삽입 때문에 배포
직후 기존 프로젝트 ClusterRole 이 **1회 UPDATE** 된다. 이후에는 멱등이다.

## 4. 범위 밖으로 둔 것

- `cueue.coaster.ten1010.io/auto-enqueue` — 코드에서 읽지도 쓰지도 않는다. 보존만 테스트로 고정.
- Helm 차트 RBAC — 컨트롤러 SA 가 이미 `*/*/*` 라 변경 없음. 이 리포와 aipub-installer 양쪽 확인.
- 어드민 ClusterRole — `aipub-admin` 그룹이 `cluster-admin` 바인딩이라 변경 없음.
- `aipub-web` — 프론트는 `UserAuthorityReview` + 이름별 GET 폴백 구조라 변경 불필요.
