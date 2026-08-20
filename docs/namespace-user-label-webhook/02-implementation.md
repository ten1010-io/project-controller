# 구현 — userrelationship 웹훅의 cluster-scoped Namespace 확장

## 변경 파일
| 파일 | 변경 |
|---|---|
| `kubernetes/controller/project-controller/templates/java-webhook-configuration.yaml` | userrelationship 웹훅 rules에 `apiGroups [""] / v1 / namespaces / CREATE / scope: Cluster` 추가 |
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

## 네임스페이스 3분류 (모니터링 팀 사용 기준)
1. `project.aipub.ten1010.io/project` 라벨 → **project가 생성한 네임스페이스**
2. `aipub.ten1010.io/username` 라벨 (예: `aipubadmin`) → **사용자가 직접 생성한 네임스페이스** (이번 작업으로 부여)
3. 둘 다 없음 → **시스템 네임스페이스**

사용자 생성 라벨은 요청자가 **aipub-member 또는 aipub-admin**(AipubUser CR 존재)일 때 붙는다 — admin 토큰에는 `oidc:aipub-member` 그룹이 없고 k8s RBAC에서도 `oidc:aipub-admin`/`oidc:aipub-member`가 별개 그룹이므로(클러스터10 실측: member 검사만으로는 aipubadmin이 만든 ns가 시스템으로 분류되는 버그), Namespace에 한해 admin도 라벨 대상에 포함했다. namespaced 리소스는 기존 quota/소유권 동작 보존을 위해 member만 유지. AipubUser CR 없는 admin은 거부하지 않고 라벨 없이 허용. kubeconfig 인증서/SA로 생성된 네임스페이스는 라벨이 없어 3번(시스템)으로 분류된다.

## 핵심 불변식
- namespaced 리소스의 기존 동작(allowlist 스킵, controller ownerReference 라벨 전파) 불변.
- Namespace 판별은 kind 기반 — `request.namespace`는 Namespace CREATE에서 자신의 이름으로 채워지므로 신뢰 불가.
- `UserLabelSynchronizer`(주기 보정)는 워크로드 8종 전용으로 이번 범위에서 확장하지 않음 — Namespace는 웹훅 단일 경로.
