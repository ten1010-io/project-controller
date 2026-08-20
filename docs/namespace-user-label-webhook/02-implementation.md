# 구현 — userrelationship 웹훅의 cluster-scoped Namespace 확장

## 변경 파일
| 파일 | 변경 |
|---|---|
| `kubernetes/controller/project-controller/templates/java-webhook-configuration.yaml` | userrelationship 웹훅 rules에 `apiGroups [""] / v1 / namespaces / CREATE, UPDATE / scope: Cluster` 추가 |
| `src/main/java/.../mutating/dto/V1AdmissionReviewRequest.java` | `oldObject` 필드 추가 (UPDATE 시 라벨 변경 감지용) |
| `src/main/java/.../mutating/service/UserLabelGuardReviewHandler.java` | **신규** — Namespace UPDATE에서 username/userid 라벨 변경 시 admin 아닌 member를 403 거부 |
| `src/main/java/.../configuration/MutatingConfiguration.java` | `aipubReviewHandlers`에 가드 핸들러 추가 |
| `src/test/java/.../UserLabelGuardReviewHandlerTest.java` | **신규** — 가드 케이스 8건 |
| `src/main/java/.../mutating/V1AdmissionReviewUtils.java` | `isNamespaceRequest(V1AdmissionReviewRequest)` 정적 헬퍼 추가 (core group + kind=Namespace 판별) |
| `src/main/java/.../mutating/service/UserLabelReviewHandler.java` | `canHandle`에 Namespace 허용 분기, `handle`에서 Namespace는 allowlist 스킵 우회 + 비멤버 Namespace는 무변경 allow |
| `src/main/java/.../mutating/service/UserOwnerReviewHandler.java` | `canHandle`에서 Namespace 명시 제외 |
| `src/test/java/.../UserLabelReviewHandlerTest.java` | Namespace 케이스 5건 추가 |
| `src/test/java/.../UserOwnerReviewHandlerTest.java` | Namespace 제외 케이스 1건 추가 |

CRD 스펙 변경 없음 → `kubernetes/examples/*.yaml` 갱신 불필요.

## 웹훅 흐름 (Namespace CREATE)
```
kubectl create ns foo (사용자 토큰)
  → apiserver admission
  → namespaceSelector: 오브젝트 자신의 kubernetes.io/metadata.name 라벨에 평가
     (kube-system 등 제외 목록이면 웹훅 미호출)
  → POST /api/v1/userrelationship/mutate (AipubAdmissionReviewController)
     canHandle 순회: UserOwnerReviewHandler → false (Namespace 명시 제외)
                     UserLabelReviewHandler  → true
  → UserLabelReviewHandler.handle
     ├ isNamespaceRequest=true → allowlist 스킵 우회 (라벨은 항상 주입 시도)
     ├ AipubUser 멤버        → username/userid 라벨 JSON Patch, allow
     ├ 멤버인데 AipubUser 無 → reject 400 / spec.id 無 → reject 500
     └ 비멤버                → 무변경 allow (owner 전파 경로 없음)
```

## 웹훅 흐름 (Namespace UPDATE — 라벨 가드)
```
kubectl label/edit ns foo (사용자 토큰)
  → POST /api/v1/userrelationship/mutate
     canHandle 순회: UserOwnerReviewHandler → false (CREATE 전용)
                     UserLabelReviewHandler  → false (CREATE 전용)
                     UserLabelGuardReviewHandler → true (UPDATE + Namespace)
  → UserLabelGuardReviewHandler.handle
     ├ oldObject vs object 의 username/userid 라벨 비교 → 변경 없음 → allow
     ├ 변경 있음 + member ∧ ¬admin → reject 403 "Only aipub admin can modify user label: ..."
     └ 변경 있음 + admin 또는 비멤버 → allow
```

## 핵심 불변식
- namespaced 리소스의 기존 동작(allowlist 스킵, controller ownerReference 라벨 전파) 불변.
- Namespace 판별은 kind 기반 — `request.namespace`는 Namespace CREATE에서 자신의 이름으로 채워지므로 신뢰 불가.
- `UserLabelSynchronizer`(주기 보정)는 워크로드 8종 전용으로 이번 범위에서 확장하지 않음 — Namespace는 웹훅 단일 경로.
