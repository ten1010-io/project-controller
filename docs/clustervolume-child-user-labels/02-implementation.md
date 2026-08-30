# 구현 — ClusterVolume 자식(PVC/PV)에 소유자 라벨 전파

브랜치 `feat/clustervolume-child-user-labels`, base `upstream/develop` (`87011f8`, #112 직후).

## 변경 파일

| 파일 | 변경 |
|---|---|
| `src/main/java/.../domain/k8s/LabelConstants.java` | `CLUSTER_VOLUME_OWNER_KEY` = `clustervolumes.aipub.ten1010.io/owner` (= `CLUSTER_VOLUME_RESOURCE_PLURAL + "." + AIPUB_GROUP + "/owner"`) |
| `src/main/java/.../mutating/V1AdmissionReviewUtils.java` | `isPersistentVolumeRequest(request)`, `isPersistentVolumeClaimRequest(request)` — core 그룹(`""`/null) + kind 판별. cluster-scoped 는 `request.namespace` 가 비어 오므로 kind 로 판별(기존 `isClusterVolumeRequest` 와 동일 근거) |
| `src/main/java/.../mutating/service/UserLabelReviewHandler.java` | 아래 상세 |
| `kubernetes/controller/project-controller/templates/mutating-webhook-user-v2.yaml` | 두 번째 웹훅 엔트리 추가(아래) |
| `src/test/java/.../mutating/service/UserLabelReviewHandlerTest.java` | 12건 추가 + `ApiClient` 목 필드화/`getBasePath()` 스텁 + okhttp Response 스텁 헬퍼 |
| `src/test/java/.../mutating/service/UserOwnerReviewHandlerTest.java` | 1건 추가 |

CRD 스펙 변경 없음(`kubernetes/examples` 갱신 해당 없음).

## `UserLabelReviewHandler` 상세

### `canHandle`
cluster-scoped 허용 집합에 PV 추가: `isNamespaceRequest || isClusterVolumeRequest || isPersistentVolumeRequest`. 나머지는 기존대로 `request.namespace` 유무.

### `handle`
- `clusterScopedRequest` 판정에 PV 포함 → allowlist 스킵을 우회(적용할 ns 가 없다). 라벨 대상 판정(`labelSubject`)은 변경 없음: 멤버, 또는 Namespace 에 한해 admin.
- 비멤버 분기를 하나로 합쳤다. 기존에는 `else if (clusterScopedRequest) → 무변경 허용` / `else → getLabelsFromOwner` 두 갈래였고, 이제 `resolveOwnerLabels(request, clusterScopedRequest)` 한 곳에서 우선순위를 결정한다. 예외 처리(500 거부)와 null 처리(무변경 허용)는 기존 코드 그대로.

### 새 private 메서드
```
resolveOwnerLabels(request, clusterScoped)
  ├─ isClusterVolumeChildCandidate(request)  # core PVC 또는 PV kind 일 때만 (QA 반영: 다른 kind 가
  │  │                                       #  라벨을 상속해도 CV 경로를 타지 않음)
  │  └─ getClusterVolumeOwnerName(object)   # metadata.labels[owner] 텍스트, DNS-1123 label 형식만
  │       │                                 #  (QA 반영: URL path 에 들어가므로 형식 검증) 아니면 null
  │       └─ != null → getLabelsFromClusterVolume(name)
  │                    └─ fetchClusterScopedObject("aipub.ten1010.io/v1alpha1", "clustervolumes", name)
  │                         → null(404) → null / 아니면 extractUserLabels(cv, ...)
  ├─ clusterScoped → null                    # Namespace/CV/PV 에는 controller owner 경로 없음
  └─ getLabelsFromOwner(object, namespace)   # 기존 경로 (마지막 라벨 추출만 extractUserLabels 로 위임)

extractUserLabels(ownerObject, description)  # username/userid 둘 다 문자열이어야 반환, 아니면 null
fetchObject(...)            → fetchObjectByPath(prefix + /namespaces/{ns}/{plural}/{name})
fetchClusterScopedObject(...) → fetchObjectByPath(prefix + /{plural}/{name})
apiPathPrefix(apiVersion)   # "/apis/" + group/version, core 면 "/api/" + version
fetchObjectByPath(path)     # 기존 fetchObject 본문 그대로: 404 → null, 그 외 비성공 → RuntimeException
```

## 웹훅 흐름

```
CV 컨트롤러 SA ──CREATE PVC(ns=proj-a, labels.owner=cv-1)──▶ apiserver
   └─ userrelationship-v2 웹훅 (persistentvolumeclaims rule, namespaceSelector 통과)
        └─ AipubAdmissionReviewController → UserOwnerReviewHandler(비멤버 → 무변경)
                                          → UserLabelReviewHandler
                                               analyzeV2 → 비멤버
                                               resolveOwnerLabels: owner=cv-1
                                                 GET /apis/aipub.ten1010.io/v1alpha1/clustervolumes/cv-1
                                                 labels.username=alice, userid=u-1
                                               patch: add /metadata/labels/aipub.ten1010.io~1username=alice
                                                      add /metadata/labels/aipub.ten1010.io~1userid=u-1
   ▶ 저장된 PVC 에 username/userid 라벨 → OwnedObjectInformerManager 가 감지
   ▶ AipubUserRoleReconciler 가 alice 의 proj-a Role 에 resourceNames=[<pvc>] update/patch/delete 추가

CV 컨트롤러 SA ──CREATE PV(labels.owner=cv-1)──▶ apiserver
   └─ userrelationship-v2-clustervolume-pv 웹훅 (persistentvolumes Cluster rule,
      objectSelector owner Exists 통과)
        └─ UserOwnerReviewHandler.canHandle=false (ownerReference 없음 — 사용자 삭제 시 PV GC 방지)
           UserLabelReviewHandler: 위와 동일 → username/userid 라벨
   ▶ PV 는 OwnershipPolicy 대상이 아니라 RBAC 변화 없음

CSI 프로비저너 ──CREATE PV(라벨 없음)──▶ objectSelector 불일치 → 웹훅 미경유
```

## 웹훅 설정 (`mutating-webhook-user-v2.yaml` 두 번째 엔트리)
```yaml
- name: userrelationship-v2-clustervolume-pv.project-controller.project.aipub.ten1010.io
  clientConfig: (기존과 동일 — service project-controller/project-controller, path /api/v1/userrelationship/mutate)
  failurePolicy: Fail
  namespaceSelector: { }
  objectSelector:
    matchExpressions:
      - key: clustervolumes.aipub.ten1010.io/owner
        operator: Exists
  rules:
    - apiGroups: [ "" ]
      operations: [ "CREATE" ]
      resources: [ "persistentvolumes" ]
      scope: "Cluster"
```
`java-webhook-configuration.yaml` 은 #112 에서 userrelationship 정의가 제거되어 수정 대상이 아니다. 프로덕션 `aipub-installer` helm 차트에는 같은 엔트리를 별도 PR 로 추가한다.

## 배포 순서
컨트롤러 이미지 롤아웃 → 웹훅 apply(`DEPLOY.md` 3단계). 순서를 바꾸면 `failurePolicy: Fail` 이라 owner 라벨이 붙은 PV CREATE 가 구버전 컨트롤러에서는 `canHandle` 이 전부 false → `allowMerging` 으로 통과하므로 실제로는 막히지 않지만, 라벨이 안 붙는 구간이 생긴다.

## 2차: 리컨실러 (transfer 대응)

| 파일 | 변경 |
|---|---|
| `domain/k8s/dto/V1alpha1ClusterVolume.java`, `V1alpha1ClusterVolumeList.java` | metadata 만 있는 DTO (신규) |
| `domain/k8s/K8sApiProvider.java` | `clusterVolumeApi` 필드 + `createClusterVolumeApi()` |
| `domain/k8s/util/ClusterVolumeUtils.java` | `getOwnerClusterVolumeName(obj)` — owner 라벨 값 (신규) |
| `informer/IndexerConstants.java` | `CLUSTER_VOLUME_OWNER_TO_OBJECTS_INDEXER_NAME` |
| `informer/SharedInformerFactoryProvider.java` | `registerClusterVolumeInformer`, `registerClusterVolumeChildPersistentVolumeClaimInformer`, `registerClusterVolumeChildPersistentVolumeInformer` — 뒤 둘은 `labelSelector=clustervolumes.aipub.ten1010.io/owner` + owner → 자식 인덱스 |
| `controller/watch/OnUpdateFilterFactory.java` | `labelsFilter()`, `ownerLabelsFilter()` |
| `controller/watch/RequestBuilderFactory.java` | `clusterVolumeChildToClusterVolume()` |
| `controller/cr/ClusterVolumeChildLabelPatcher.java` (인터페이스), `CoreV1ClusterVolumeChildLabelPatcher.java` | `PatchUtils.patch(..., PATCH_FORMAT_JSON_MERGE_PATCH)` 로 `{"metadata":{"labels":{k:v|null}}}` |
| `controller/cr/ClusterVolumeChildLabelReconciler.java` | 아래 |
| `controller/cr/ClusterVolumeChildLabelControllerFactory.java` | `clustervolume-child-label-controller`, worker 1, readyFunc/watch 3종 |
| `configuration/ControllerConfiguration.java` | `clusterVolumeChildLabelController` 빈 |

### 리컨실 흐름 (키 = CV 이름)
```
reconcileInternal(Request(null, cvName))
  cv = cvIndexer.getByKey(cvName)            # 없으면 no-op (삭제 중/삭제됨 — finalizer/GC 가 자식 정리)
  cvLabels = cv.metadata.labels
  for pvc in pvcIndexer.byIndex(OWNER_IDX, cvName):
      allowlist ns → skip; terminating → skip
      diff = computeLabelDiff(cvLabels, pvc)   # username/userid 두 키만; CV 값 != 자식 값이면 (키 → CV 값|null)
      diff 비면 skip; 아니면 patcher.patchPersistentVolumeClaimLabels(ns, name, diff)  # 404 는 무시
  for pv in pvIndexer.byIndex(OWNER_IDX, cvName): 동일 (allowlist 검사 없음)
  return Result(false)
```
실패: 404 외 ApiException 은 전파 → `AbstractReconciler` 가 409 는 5초, 그 외 60초 후 재큐잉.

### 이벤트 → 큐
| 이벤트 | 필터 | 요청 |
|---|---|---|
| CV add | — | `Request(null, cv.name)` — 기동 시 전체 소급 |
| CV update | `ownerLabelsFilter` (username/userid 변경) | 동일 |
| PVC/PV add·delete | — | owner 라벨 값 → `Request(null, owner)`; 라벨 없으면 없음 |
| PVC/PV update | `labelsFilter` (라벨 맵 변경) | 동일 |

### transfer 시나리오
```
백엔드 ─ CV cv-1 라벨 username: alice → bob ─▶ apiserver
  CV 인포머 onUpdate (ownerLabelsFilter 통과) ─▶ queue: cv-1
  reconcile(cv-1): 복제 PVC(proj-a, proj-b), 복제 PV, 앵커 PV 각각 diff={username: bob, userid: u-bob}
     ─▶ merge patch ─▶ OwnedObjectInformerManager 가 PVC 라벨 변경 감지
     ─▶ alice 의 개인 Role 에서 PVC 규칙 제거, bob 의 개인 Role 에 추가 (기존 RBAC 리컨실 경로)
```
