# 계획 — ClusterVolume 자식(PVC/PV)에 소유자 라벨 전파

## 개요
`ClusterVolume`(CV, `aipub.ten1010.io/v1alpha1`, cluster-scoped) 컨트롤러가 CV 하나당 여러 개 만드는 자식 오브젝트 — 대상 네임스페이스별 **복제 PVC/PV**, `volume.storageClassName` 경로의 **앵커 PVC/PV** — 에 부모 CV 의 소유자 라벨 `aipub.ten1010.io/username` / `aipub.ten1010.io/userid` 를 전파한다. #112 로 CV 자신에는 라벨이 붙지만 자식에는 아무 소유자 표식이 없어, GUI 가 "내 볼륨의 PVC" 를 찾을 수 없고 소유자가 자기 복제 PVC 를 관리(update/delete)할 RBAC 도 생기지 않았다.

## 배경 — CV 자식의 구조 (CRD `clustervolumes.aipub.ten1010.io` 기준)
| 자식 | 스코프 | 위치 | 생성자 | 표식 |
|---|---|---|---|---|
| 복제 PVC | namespaced | 각 대상 ns (`spec.namespaces`) | CV 컨트롤러 SA | `clustervolumes.aipub.ten1010.io/owner=<CV명>` + CV ownerReference |
| 복제 PV | cluster | — | CV 컨트롤러 SA (정적 PV, Retain) | 같음 |
| 앵커 PVC | namespaced | 시스템 ns | CV 컨트롤러 SA | 같음 |
| 앵커 PV | cluster | — | **CSI 프로비저너** (동적) | 컨트롤러가 사후 라벨링 |
| 편입 원본 PVC (`claimRef`) | namespaced | 원본 ns | 사용자/타 주체 | `claimed-by` 라벨 — **남의 오브젝트, 전파 대상 아님** |

- CR 이름은 DNS label(63자) 로 제한되어 자식 이름·라벨 값으로 쓰인다 → `owner` 라벨 값 = CV `metadata.name`.
- 자식의 CV ownerReference 는 GC 백스톱이지만, CV 가 cluster-scoped 라 기존 `UserLabelReviewHandler.getLabelsFromOwner`(namespaced 부모만 조회) 경로로는 닿지 않는다.
- CV `create` 는 백엔드 SA 전용이지만 admin 은 cluster-admin 바인딩이라 직접 만들 수 있다. CV 의 소유자 라벨은 웹훅(멤버 생성 시 — #112, **admin 생성 시 — 이번 추가**) 또는 백엔드가 직접 찍는다. 자식 전파는 "CV 에 라벨이 있으면 복사" 만 책임진다.

## 설계 결정 (검토한 대안 포함)

### 채택: userrelationship 웹훅 CREATE 시점 전파 + owner 라벨로 부모 조회
- 기존 소유권 모델("멤버가 만든 오브젝트의 표식은 CREATE 웹훅이 찍는다", CLAUDE.md)과 같은 경로. 라벨 결정 로직의 단일 원천인 `UserLabelReviewHandler` 안에서 해결.
- 비멤버(CV 컨트롤러 SA) 생성 경로에 부모 탐색 우선순위를 둔다: ① `clustervolumes.aipub.ten1010.io/owner` 라벨 → CV cluster-scoped GET → username/userid 복사, ② (namespaced 만) 기존 controller ownerReference 경로, ③ 없으면 라벨 없이 허용.
- 멤버가 직접 만든 오브젝트는 owner 라벨이 있어도 생성자 본인 라벨이 우선 — 기존 계약 유지, CV 조회 없음.

### 채택(2차): CV 인포머 + 리컨실러로 자식 라벨 동기화 — transfer 대응
- 1차 리뷰에서 "소유권 이전(transfer)에도 대응해야 한다" 는 요구가 추가됐다. CREATE 시점 웹훅은 이후 CV 라벨 변경을 따라갈 수 없으므로 리컨실러(`ClusterVolumeChildLabelReconciler`)를 추가했다.
- 불변식: **자식(PVC/PV)의 username/userid 두 키 = CV 의 두 키**. CV 에 없는 키는 자식에서도 제거한다.
- 트리거(모두 키 = CV 이름): CV 소유자 라벨 변경(`ownerLabelsFilter`), 자식 생성/라벨 변경(`labelsFilter` + owner 라벨 → CV 요청 빌더), 기동 시 CV onAdd(기존 자식 소급).
- 자식 인포머는 `clustervolumes.aipub.ten1010.io/owner` 라벨 셀렉터로 서버사이드 필터 — 클러스터 전체 PVC/PV 를 캐시하지 않는다. 대가로 이 팩토리의 `V1PersistentVolumeClaim`/`V1PersistentVolume` 인포머는 CV 자식만 담는다(재사용 시 주의, 주석 명시).
- 패치는 `metadata.labels` 만 JSON merge patch — CV 컨트롤러의 owner 라벨·spec 과 충돌하지 않는다.
- 웹훅 CREATE 경로는 유지한다: 생성 즉시 라벨이 있어야 하는 소비자(RBAC 규칙 생성 지연 최소화)를 위한 즉시성 확보. 두 경로는 같은 값을 쓰므로 충돌하지 않는다.
- 대안(기각): CV 컨트롤러 저장소에서 자식 생성 시 복사 — 저장소가 로컬에 없고, transfer 시 자식 갱신 로직도 그쪽에 필요해져 결국 리컨실러가 된다.

### PV 웹훅은 별도 엔트리 + `objectSelector: owner Exists`
- PV 는 cluster-scoped 라 기존 v2 웹훅에 rule 만 추가하면 **클러스터의 모든 PV 생성**(CSI 프로비저너·시스템 컴포넌트 포함, namespaceSelector 미평가)이 `failurePolicy: Fail` 웹훅을 경유한다 → project-controller 장애가 클러스터 전체 PV 프로비저닝 장애로 번진다.
- `objectSelector` 는 rule 단위가 아니라 웹훅 단위라, 같은 MutatingWebhookConfiguration 안에 두 번째 웹훅을 두고 `clustervolumes.aipub.ten1010.io/owner Exists` 로 CV 컨트롤러가 만든 PV 만 인터셉트한다. path·failurePolicy·timeout 은 동일.
- PVC 는 기존 `persistentvolumeclaims` Namespaced rule 이 이미 인터셉트하므로 rule 변경 없음.

### AipubUser ownerReference 는 PV 에 붙이지 않는다
- AipubUser 와 PV 모두 cluster-scoped 라 참조가 성립하므로, 붙이면 사용자 삭제 시 CV 의 복제/앵커 PV 가 GC 로 지워진다. `UserOwnerReviewHandler.canHandle` 은 PV 에 대해 이미 false — 테스트로 고정한다.

### 실패 계약
- CV 404 / CV 에 소유자 라벨 없음 → 라벨 없이 허용(자식 생성을 막지 않는다).
- 그 외 API 오류 → 500 거부. 기존 controller owner 조회와 같은 계약(fail-closed). owner 라벨이 없는 오브젝트는 조회 자체를 하지 않으므로 일반 PV/PVC 에는 영향 없다.

## RBAC 영향 분석
- PVC 는 `OwnershipPolicy.OWNED_TARGETS` → 라벨이 붙은 복제 PVC 는 `AipubUserRoleReconciler` 가 소유자 개인 Role 에 `resourceNames` 단위 update/patch/delete 를 만든다. CV 소유자가 자기 볼륨의 PVC 를 관리하는 것으로 의도에 맞고, 복제 PVC 삭제는 컨트롤러 자가치유로 재생성되어 데이터 안전(CRD 계약).
- `AipubUserRoleReconciler` 는 allowlist ns·Project 없는 ns 를 스킵 → 시스템 ns 의 앵커 PVC 에는 라벨이 붙어도 Role 이 생기지 않는다. 실제로는 시스템 ns 가 namespaceSelector 제외/allowlist 라 웹훅이 라벨을 붙이지 않는다.
- PV 는 OWNED_TARGETS 가 아니다 → 라벨은 조회/표시용, 권한 부여 없음. 프로젝트 Role 의 PV `get/list` 는 그대로.

## 알려진 한계
- 웹훅 단독으로는 앵커 PV(CSI 생성, 사후 라벨링)·기존 자식·transfer 를 못 다루지만, 2차 리컨실러가 세 경우를 모두 커버한다(사후 라벨링 시 PV 인포머 onAdd, 기동 시 CV onAdd, CV 라벨 변경 이벤트).
- allowlist 네임스페이스 안의 PVC(예: 시스템 ns 의 앵커 PVC 가 allowlist 인 경우)는 웹훅·리컨실러 모두 라벨을 붙이지 않는다(기존 정책 유지).
- CV 컨트롤러가 자식 update 시 `metadata.labels` 전체를 자기 것으로 덮어쓰는 구현이라면 리컨실러와 라벨 핑퐁이 생길 수 있다 — 개발 클러스터 실측 항목.
- 프로덕션 웹훅 설정은 `aipub-installer` helm 차트(`mutating-webhook.yaml`) 에 같은 엔트리를 추가하는 별도 PR 이 필요하다.

## 변경 범위
- 웹훅: `domain/k8s/LabelConstants.java`, `mutating/V1AdmissionReviewUtils.java`, `mutating/service/UserLabelReviewHandler.java`, `kubernetes/.../mutating-webhook-user-v2.yaml`
- 리컨실러: `dto/V1alpha1ClusterVolume(+List)`, `K8sApiProvider`, `SharedInformerFactoryProvider`, `IndexerConstants`, `util/ClusterVolumeUtils`, `watch/OnUpdateFilterFactory`, `watch/RequestBuilderFactory`, `controller/cr/ClusterVolumeChildLabel{Reconciler,ControllerFactory,Patcher}`, `CoreV1ClusterVolumeChildLabelPatcher`, `ControllerConfiguration`
- 테스트 4개 파일(웹훅 13건 + 리컨실러/워치 17건)
