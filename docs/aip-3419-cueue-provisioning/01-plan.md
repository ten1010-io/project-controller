# Cueue 모델·권한·프로비저닝 라벨 — 계획

- Jira: [AIP-3419](https://ten1010.atlassian.net/browse/AIP-3419) (상위 에픽 AIP-1565)
- 브랜치: `feat/AIP-3419` (base `develop`)
- 참고: [Cueue(AIPub 5.2 우선순위 기능)](https://ten1010.atlassian.net/wiki/spaces/AP/pages/1586135104/Cueue+AIPub+5.2)

## 1. 배경

AIPub 5.2 코스터 신버전에서 기존 `SchedulingQueue` CR(`schedulingqueues.coaster.ten1010.io`)이
삭제되고 **`Cueue`**(`cueues.coaster.ten1010.io`, Cluster scope)가 그 자리를 대신한다. 별도 coaster
스케줄러도 없어져 Pod 의 `spec.schedulerName` 은 `default-scheduler` 다.

Cueue 는 **프로젝트마다 하나씩 프로젝트와 같은 이름으로 Coaster 가 자동 생성**한다. 그 전제 조건을
project-controller 가 만들어 줘야 한다 — Coaster 는 네임스페이스에 붙은 라벨을 보고 Cueue 를 만들고,
어노테이션에 적힌 라벨 셀렉터로 큐의 영향권 노드를 고른다. 프로젝트 역할별 Cueue 접근 권한도
project-controller 의 ClusterRole 리컨실러가 부여해야 한다.

착수 시점에 `origin/develop`(a23eea8) 에는 `cueue` 문자열이 한 건도 없었다.

코스터측 선행 작업은 완료돼 있다 — AIP-3352(Namespace ↔ Cueue 프로비저닝), AIP-3353(네임스페이스별
auto-enqueue).

## 2. 범위

| # | 항목 | 비고 |
|---|---|---|
| 1 | Cueue DTO + 상수 | 모델 정의만. 리컨실·소유하지 않는다 |
| 2 | 프로젝트 역할별 RBAC 규칙 | 매니저 `get`/`patch`/`update`, 멤버 `get` |
| 3-1 | 노드에 프로젝트 바인딩 라벨 | `project-name.aipub.ten1010.io/<proj>: ""` |
| 3-2 | 네임스페이스 라벨 + 어노테이션 | `provisioning-enabled` / `node-selector` |
| 4 | `kubernetes/examples/crd.yaml` 에 Cueue CRD 반영 | |

**범위 밖**

- `cueue.coaster.ten1010.io/auto-enqueue` 라벨 — 프로젝트별 on/off 운영 설정이라 Project 에서 파생할
  수 있는 값이 아니다. AIPub 어드민이 직접 설정한다(`aipub-admin` 그룹이 `cluster-admin` 에 바인딩돼
  있어 별도 권한 작업이 필요 없다). 컨트롤러는 읽지도 쓰지도 않는다.
- Helm 차트 RBAC — 컨트롤러 ServiceAccount 가 이미 `*/*/*` 라 추가할 것이 없다.
- 어드민 ClusterRole — `cluster-admin` 바인딩이라 Cueue 가 이미 포함된다.

## 3. 설계 결정

### 3.1 `list` 를 부여하지 않는다

문서 §9 는 매니저·멤버 모두에게 `get`/`list` 를 적어뒀지만 `list` 는 넣지 않았다.

- **RBAC 로 표현이 안 된다.** `list` 는 컬렉션 요청이라 `resourceNames` 로 좁혀지지 않는다. 규칙에
  넣어도 매칭되지 않아 거부되고, 동작시키려면 `resourceNames` 를 떼야 하는데 Cueue 가 Cluster scope
  라 **전체 Cueue(= 전체 프로젝트 목록)가 모든 멤버에게 노출**된다.
- **필요하지도 않다.** 프론트는 k8s LIST 를 쓰지 않고 `UserAuthorityReview` 권한 분석으로 사용자가
  접근 가능한 리소스를 집계해 화면에서 쓴다.
- 기존 `gpuConfigs`·`nodeResources` 규칙도 같은 이유로 `get` 만 준다 — 선례와 일관된다.

검토한 대안: ① `list` 를 클러스터 전역 허용 → 정보 노출로 기각. ② 백엔드가 자기 SA 로 조회해
필터링 중계 → 백엔드 작업이 추가로 필요하고 프론트 구조상 불필요해 기각.

### 3.2 노드 라벨에 `isProjectManaged` 게이트를 둔다

`reconcileNodeLabels` 는 접두로 기존 `project-name.*` 키를 걷어낸 뒤, `project-managed` 가 아니면
그대로 반환하고, 관리 노드일 때만 바인딩된 프로젝트 키를 채운다.

- `BoundObjectResolver.getAllBoundProjects(V1Node)` 는 `project-managed` 라벨을 보지 않는다. 즉
  **비관리 노드도 바인딩된 것으로 잡힌다.**
- `reconcileNodeAnnotations` 는 바로 그 이유로 bound-projects 어노테이션을 게이트하고 있다. 새 라벨은
  그 어노테이션과 **동일한 성격의 프로젝트 파생 상태**이므로 동작을 맞췄다.
- 제거를 게이트 **앞**에 둬서, 노드가 프로젝트 관리에서 빠질 때 기존 라벨이 회수된다. 게이트를 먼저
  두면 라벨이 영구히 남는다.

트레이드오프: `project-managed=false` 인데 프로젝트에 바인딩된 노드는 라벨을 받지 못한다. 플랫폼이
프로젝트 관리 대상이 아니라고 선언한 노드이므로 의도된 동작이다. 되돌리려면 게이트 3줄을 빼면 된다.

### 3.3 인포머·`K8sApiProvider` 에 Cueue 를 등록하지 않는다

project-controller 는 Cueue 를 **읽지도 만들지도 않는다** — 모델 정의와 권한 부여만 한다. 이 리포에는
스킴/ModelMapper 일괄 등록 지점이 없고 DTO 는 `K8sApiProvider` 와 `SharedInformerFactoryProvider` 에
손으로 적는 구조라, 적지 않으면 그냥 존재하지 않는 것으로 동작한다. `V1alpha1CueueList` 도 만들지
않았다.

### 3.4 `V1LabelSelector`·`V1Condition` 을 재사용한다

`client-java-api:27.0.0` 의 두 타입이 Cueue CRD 스키마와 필드가 정확히 일치한다. 리포에 `RbacV1Subject`
·`V1JobTemplateSpec` 재사용 선례가 있다.

### 3.5 `priority` 기본값에 관여하지 않는다

CRD 스키마의 `default: 1` 은 apiserver 가 채우는 값이고, Confluence §3 필드표의 `3000` 은 FE 가 큐를
만들거나 패치할 때 쓰는 값으로 **FE ↔ Coaster 사이의 약속**이다. 레이어가 달라 모순이 아니며 어느
쪽도 project-controller 가 판단할 일이 아니다. DTO 는 `Integer` 로 매핑만 하고 기본값을 하드코딩하거나
보정하지 않는다.

## 4. 알려진 함정

1. **`OnUpdateFilterFactory.projectNamespaceFilter()` 에 annotation 비교가 필요하다.** informer resync
   period 가 `0` 이라 주기적 재리컨실이 없다. 비교가 빠지면 누가 `node-selector` 어노테이션을 지워도
   Project ADD/DELETE 나 재기동 전까지 복구되지 않는다 — 조용히 깨진다.
2. **ClusterRole 룰 비교가 `List.equals`(순서 비교)다.** 규칙을 리스트 중간에 삽입하므로 배포 직후
   기존 프로젝트 ClusterRole 이 1회 UPDATE 된다. 의도된 1회성이다.
3. **`LabelUtils.getKeyOfLabelString` 을 이 경로에 쓰면 안 된다.** 라벨 값이 빈 문자열이라
   `"key:".split(":")` 길이가 1 이 되어 `IllegalArgumentException` 이 난다.
4. **이 접두를 NodeGroup `spec.nodeSelector` 에 쓰면 테넌트 경계가 넓어진다.**
   `getBoundNodeGroupsByNodeSelector` 가 부분집합 판정이라, 프로젝트 A 의 키를 selector 로 가진
   NodeGroup 을 프로젝트 B 에 바인딩하면 A 의 노드가 B 에도 전이 바인딩된다. 상수 Javadoc 에 금지를
   명시했다.
