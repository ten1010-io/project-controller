# 구현 — PR #87(AIP-2389 allowlist) 리뷰 후속 수정

## 변경 파일

### main

| 파일 | 변경 |
|---|---|
| `mutating/service/ProjectReviewHandler.java` | reserved/allowlist 이름 hard block을 `OPERATION_CREATE`일 때만 수행. UPDATE는 통과(finalizer 제거 교착 방지). |
| `domain/k8s/NamespaceAllowlistResolver.java` | 판정 값을 `ALLOWLISTED_VALUE = "true"` 상수로 내재화(equalsIgnoreCase 유지). |
| `domain/k8s/NamespaceAllowlistValueEnum.java` | 삭제 (FALSE/getEnum 미사용 dead code). |
| `controller/workload/PodReconciler.java` | `NamespaceAllowlistResolver` 생성자 주입으로 전환. |
| `controller/workload/PodControllerFactory.java` | resolver 파라미터 추가, PodReconciler에 전달. |
| `configuration/ControllerConfiguration.java` | `podController` 빈에 resolver 주입 배선. |
| `controller/workload/WorkloadControllerFactory.java` | `OnUpdateFilterFactory`/`RequestBuilderFactory` 필드화, `createNamespaceWatch` 인라인 생성 제거. |
| `controller/rbac/member/ClusterRoleReconciler.java` `ClusterRoleBindingReconciler.java` | reconcile마다 생성하던 `NamespaceNameResolver` 필드화. |
| `aip-2389-allowlist-scheduling.html` | 삭제(임시 분석 문서). |

### test

| 파일 | 내용 |
|---|---|
| `ProjectReviewHandlerTest` | UPDATE가 reserved/allowlist hard block을 통과하는 회귀 테스트 2건 추가. |
| `PodReviewHandlerTest` (신규) | allowlist toleration 패치 주입, 제외 라벨보다 allowlist 우선, 일반 ns 제외 무패치. |
| `DeploymentReviewHandlerTest` (신규) | 동일 3케이스, 경로 `/spec/template/spec/tolerations`. |
| `WorkloadControllerReconcilerTest` (신규) | Exists 쌍 reconcile, 제외 라벨보다 allowlist 우선, 일반 ns 제외 스킵, controller ownerRef 스킵. |

## ProjectReviewHandler 흐름 (수정 후)

```
handle(review)
 ├─ operation == CREATE ?
 │   ├─ reserved 이름   → 409 거부 (예외 없음)
 │   └─ allowlist 이름  → 409 거부 (예외 없음)
 ├─ system admin group  → 허용   ← UPDATE는 여기부터 시작 (finalizer 제거 통과)
 └─ 기존 project 존재 시 project-manager 검사 / spec 변경 거부 (기존 동작 유지)
```

CRD 스펙 변경 없음 — `kubernetes/examples/*.yaml` 갱신 불필요.
