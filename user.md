# 쿠버네티스 유저 권한 Webhook 개발 계획

## 개요

AIPub 유저가 워크로드 오브젝트를 생성할 때 소유자 정보(label, ownerReference)를 자동으로 주입하고, 비인가 변경을 차단하는 Mutating/Validating Webhook을 구현한다.

> Python 참조 구현체(`/Users/rook/PycharmProjects/aipub-admission-controller`)의 설계를 기준으로 한다.

---

## 1. Label/Annotation 키 정의

`aipub.ten1010.io` 도메인을 사용한다 (기존 `LabelConstants.OBJECT_OWN_USERNAME_KEY`와 동일 컨벤션).

| 구분 | Key | 값 | 설명 |
|---|---|---|---|
| Label | `aipub.ten1010.io/username` | AipubUser name (e.g. `alice`) | 소유자 AipubUser 이름 |
| Label | `aipub.ten1010.io/userid` | AipubUser.spec.id 값 | AIPub 내부 유저 ID |
| Annotation | `user.aipub.ten1010.io/transfer` | 새 AipubUser name | 이관 트리거 annotation |

**Username 파싱**: K8s username `oidc:aipub-member:alice`에서 마지막 토큰 `alice`를 추출하여 AipubUser CR name 및 label 값으로 사용한다. 기존 `LabelUtils.getValueOfLabelString(username)` 패턴과 동일하다.

**상수 위치**: 기존 상수 클래스에 추가한다.

```java
// LabelConstants.java — OBJECT_OWN_USERNAME_KEY는 이미 존재
// 기존: OBJECT_OWN_USERNAME_KEY = "aipub.ten1010.io/username"
public static final String OBJECT_OWN_USERID_KEY =
    ProjectApiConstants.AIPUB_GROUP + "/" + "userid";
```

```java
// AnnotationConstants.java 추가
public static final String USER_TRANSFER_KEY = "user.aipub.ten1010.io/transfer";
```

---

## 2. 대상 리소스

Mutating/Validating 모두 동일한 워크로드 리소스에 적용한다.

- `Deployment` (apps/v1)
- `StatefulSet` (apps/v1)
- `DaemonSet` (apps/v1)
- `Job` (batch/v1)
- `CronJob` (batch/v1)
- `Pod` (core/v1)

> Pod는 직접 생성 / 컨트롤러 생성 여부에 따라 처리 방식이 다르다 (섹션 3-5 참조).

---

## 3. Mutating Webhook

### 3-1. UserInfoAnalysis 개선

`UserInfoAnalysis`에 편의 메서드를 추가한다.

```java
// owner label/ownerReference 설정 가능 여부
// → oidc:aipub-member이고 AipubUser CR이 존재하는 경우
public boolean isOwnable() {
    return isAipubMember() && getAipubUser().isPresent();
}
```

각 ReviewHandler에서 `userInfoAnalyzer.analyze(userInfo).isOwnable()`로 분기한다.

> **관련 파일**: `mutating/service/UserInfoAnalysis.java`, `mutating/service/UserInfoAnalyzer.java`

---

### 3-2. CREATE 시 Owner Label 주입

**동작 조건**: `operation == "CREATE"` && `analysis.isOwnable() == true`

**주입 방식**: `JsonPatchBuilder`로 `/metadata/labels` 패치

```
PATCH add /metadata/labels/aipub.ten1010.io~1username = <aipubuser-name>
PATCH add /metadata/labels/aipub.ten1010.io~1userid   = <spec.id>
```

> `~1`은 JSON Pointer에서 `/`를 이스케이프한 표현

공통 로직은 유틸 클래스로 추출한다:

```java
// 신규: mutating/service/UserOwnerPatchBuilder.java
public class UserOwnerPatchBuilder {
    public static void addOwnerLabelPatches(
        JsonPatchBuilder builder, UserInfoAnalysis analysis, JsonNode object) { ... }
    public static void addOwnerReferencePatch(
        JsonPatchBuilder builder, V1alpha1AipubUser aipubUser, JsonNode object) { ... }
}
```

> **주의**: `metadata.labels`가 null일 경우 `add /metadata/labels` 오브젝트를 먼저 패치해야 한다.

---

### 3-3. CREATE 시 OwnerReference 설정

실제 `ownerReference`를 추가한다 (단, GC 제어 플래그는 비활성).

```json
{
  "apiVersion": "project.aipub.ten1010.io/v1alpha1",
  "kind": "AipubUser",
  "name": "<aipubuser-name>",
  "uid": "<aipubuser-uid>",
  "controller": false,
  "blockOwnerDeletion": false
}
```

```
PATCH add /metadata/ownerReferences/- = <위 ownerReference 객체>
```

> **K8s 제약 참고**: 클러스터 스코프 리소스(`V1alpha1AipubUser`)를 namespaced 리소스의 ownerReference로 지정하면 K8s GC는 동작하지 않는다. 소유 관계 추적 목적으로만 사용한다.
> `metadata.ownerReferences`가 null일 경우 빈 배열로 초기화 후 append한다.

---

### 3-4. 이관 (Transfer — Owner 변경)

**트리거**: UPDATE 요청 시 `user.aipub.ten1010.io/transfer: <new-aipubuser-name>` annotation이 존재하면 이관 처리한다.

**권한**: 아래 중 하나에 해당하면 이관 허용

- `system:masters` 또는 `kubeadm:cluster-admins`
- `oidc:aipub-admin`
- 해당 오브젝트가 속한 Project의 ProjectManager

**처리 흐름**:

```
UPDATE 요청 수신
  │
  ├─ transfer annotation 없음 → 기존 로직 (allow)
  │
  └─ transfer annotation 있음
       ├─ 권한 검증 → 실패 시 REJECT 403
       └─ 권한 있음
            ├─ 새 AipubUser CR 조회 (informer에서)
            ├─ label replace (username, userid)
            ├─ ownerReferences replace (기존 AipubUser → 새 AipubUser)
            └─ transfer annotation remove (patch)
```

```
PATCH replace /metadata/labels/aipub.ten1010.io~1username  = <new-name>
PATCH replace /metadata/labels/aipub.ten1010.io~1userid    = <new-id>
PATCH remove  /metadata/annotations/user.aipub.ten1010.io~1transfer
PATCH replace /metadata/ownerReferences                    = [<기존 non-AipubUser refs>, <new-owner-ref>]
```

> `V1AdmissionReviewRequest`에 `oldObject` 필드 추가 필요:
> ```java
> @Nullable
> private JsonNode oldObject;
> ```

---

### 3-5. Pod — parent workload label 상속

컨트롤러(Deployment 등)가 생성한 Pod의 경우 `userInfo`는 컨트롤러 SA이므로, parent workload에서 user label을 상속한다.

```
Pod CREATE 요청
  │
  ├─ ownerReferences에 controller:true 항목 없음 (직접 생성)
  │    └─ userInfo 기반 label/ownerReference 주입 (3-2, 3-3과 동일)
  │
  └─ ownerReferences에 controller:true 항목 있음 (컨트롤러 생성)
       ├─ owner kind/name/namespace 추출
       ├─ informer indexer에서 parent workload 조회
       ├─ parent의 aipub.ten1010.io/username, userid label 복사
       └─ Pod에 label add patch 적용
```

parent workload가 informer에 없거나 user label이 없으면 label 주입 생략 (allow만).

> `PodReviewHandler`는 이미 `PodNodesResolver`를 통해 parent workload를 조회하는 패턴이 있어 참고 가능.

---

## 4. Validating Webhook (신규)

### 4-1. 새 엔드포인트 및 컨트롤러

기존 Mutating 엔드포인트(`/api/v1/admissionreviews`)와 별개로 신규 생성한다.

```
POST /api/v1/validating-admissionreviews
```

**신규 클래스** (`validating/` 패키지에 생성):

- `validating/ValidatingAdmissionReviewController.java` — `@RestController`, 기존 `AdmissionReviewController`와 동일 패턴
- `validating/service/ValidatingReviewHandler.java` — 인터페이스 (기존 `ReviewHandler`와 동일 구조)
- `validating/service/AbstractValidatingReviewHandler.java` — 추상 기반 클래스
- `validating/service/CompositeValidatingReviewHandler.java` — 핸들러 라우팅
- `validating/service/ValidatingAdmissionReviewService.java` — 서비스 레이어
- `configuration/ValidatingConfiguration.java` — 빈 등록

> DTO (`V1AdmissionReview`, `V1AdmissionReviewRequest` 등)와 유틸 (`V1AdmissionReviewUtils`)은 기존 `mutating/dto/`, `mutating/` 패키지의 것을 공유한다. 필요 시 공통 패키지로 이동을 검토한다.

---

### 4-2. User Label 보호 Validating Handler

**역할**: `oidc:aipub-member` 유저가 owner label을 직접 변경하는 것을 차단한다.

**보호 대상**: label만 보호, ownerReference 변경은 차단하지 않는다.

- `aipub.ten1010.io/username`
- `aipub.ten1010.io/userid`

**동작 흐름**:

```
UPDATE 요청 수신
  │
  ├─ oidc:aipub-admin 포함         → allow (관리자)
  ├─ system:serviceaccounts 포함   → allow (컨트롤러 SA)
  ├─ system:masters 포함           → allow
  │
  ├─ oidc:aipub-member 아님        → allow (일반 유저)
  │
  └─ oidc:aipub-member 인 경우
       ├─ username label 변경 감지  → REJECT 403
       ├─ userid label 변경 감지    → REJECT 403
       └─ 변경 없음                 → allow
```

**신규 클래스**:
- `validating/service/WorkloadUserLabelValidatingHandler.java` — 모든 대상 워크로드 공통 처리

---

## 5. 구현 순서

```
Step 1.  상수 추가
         - LabelConstants에 OBJECT_OWN_USERID_KEY 추가
         - AnnotationConstants에 USER_TRANSFER_KEY 추가

Step 2.  V1AdmissionReviewRequest에 oldObject 필드 추가

Step 3.  UserInfoAnalysis에 isOwnable() 메서드 추가

Step 4.  UserOwnerPatchBuilder 유틸 클래스 신규 구현
         (label add/replace, ownerReference add/replace, transfer annotation remove)

Step 5.  누락 워크로드 ReviewHandler 신규 생성
         (StatefulSetReviewHandler, DaemonSetReviewHandler, JobReviewHandler, CronJobReviewHandler)
         - 기존 DeploymentReviewHandler 패턴 참고
         - toleration/affinity/imagePullSecrets 뮤테이션 포함 여부 결정

Step 6.  각 워크로드 ReviewHandler에 CREATE 시 label/ownerReference 주입 로직 추가
         (DeploymentReviewHandler, StatefulSetReviewHandler, DaemonSetReviewHandler,
          JobReviewHandler, CronJobReviewHandler, PodReviewHandler)

Step 7.  PodReviewHandler에 parent workload label 상속 로직 추가

Step 8.  각 워크로드 ReviewHandler에 UPDATE 시 이관(Transfer) 로직 추가

Step 9.  MutatingConfiguration에 신규 핸들러 빈 등록
         (UserInfoAnalyzer 빈 + 신규 워크로드 핸들러 빈)

Step 10. validating/ 패키지 신규 구현
         (ValidatingAdmissionReviewController, ValidatingReviewHandler 인터페이스,
          AbstractValidatingReviewHandler, CompositeValidatingReviewHandler,
          ValidatingAdmissionReviewService)

Step 11. WorkloadUserLabelValidatingHandler 구현

Step 12. ValidatingConfiguration에 빈 등록

Step 13. 테스트 작성
```

---

## 6. 주요 고려사항

### 기존 오브젝트 처리
- Webhook 도입 이전에 생성된 오브젝트에는 owner label이 없을 수 있다.
- Validating handler에서 oldObject에 label이 없고 newObject에도 없으면 통과.
- 레거시 오브젝트에 label을 소급 적용하는 마이그레이션 작업은 별도로 검토.

### 컨트롤러 ServiceAccount 식별
- `system:serviceaccounts` 그룹에 속하는 요청자는 컨트롤러로 간주.
- 더 엄격하게 제한할 경우: `system:serviceaccounts:<controller-namespace>:<controller-sa-name>` 형태로 특정 SA만 허용.

### Label 값의 K8s 유효성
- K8s label 값은 최대 63자, 영숫자/`-`/`_`/`.`만 허용.
- username에서 파싱한 AipubUser name만 사용하므로 (e.g. `alice`), 대부분의 경우 유효하다.
- 특수문자가 포함될 가능성이 있으면 인코딩 전략 필요.

### Kubernetes Webhook 설정
- `MutatingWebhookConfiguration`: 대상 리소스 추가, `operations: ["CREATE", "UPDATE"]`
- `ValidatingWebhookConfiguration`: 신규 등록, `/api/v1/validating-admissionreviews` 경로, `operations: ["CREATE", "UPDATE"]`

### 백그라운드 동기화 (구현 범위 외)
- Python 구현체에는 300초 주기 Pod user label 동기화 태스크가 존재한다.
- 이 컨트롤러에서는 webhook + controller reconciler 패턴으로 대체 가능하다.
- 추후 필요 시 별도로 추가한다.

---

## 7. 파일 구조 (신규/변경)

```
src/main/java/.../
├── domain/k8s/
│   ├── LabelConstants.java                    [변경] OBJECT_OWN_USERID_KEY 추가
│   └── AnnotationConstants.java               [변경] USER_TRANSFER_KEY 추가
├── mutating/
│   ├── dto/
│   │   └── V1AdmissionReviewRequest.java      [변경] oldObject 필드 추가
│   └── service/
│       ├── UserInfoAnalysis.java              [변경] isOwnable() 추가
│       ├── UserOwnerPatchBuilder.java          [신규] label/ownerReference patch 유틸
│       ├── DeploymentReviewHandler.java        [변경] CREATE/UPDATE owner 처리
│       ├── StatefulSetReviewHandler.java       [신규] Deployment 패턴 기반
│       ├── DaemonSetReviewHandler.java         [신규] Deployment 패턴 기반
│       ├── JobReviewHandler.java               [신규] Deployment 패턴 기반
│       ├── CronJobReviewHandler.java           [신규] Deployment 패턴 기반
│       └── PodReviewHandler.java              [변경] CREATE owner + parent 상속 처리
├── validating/                                 [신규 패키지]
│   ├── ValidatingAdmissionReviewController.java   [신규]
│   └── service/
│       ├── ValidatingReviewHandler.java           [신규] 인터페이스
│       ├── AbstractValidatingReviewHandler.java   [신규]
│       ├── CompositeValidatingReviewHandler.java  [신규]
│       ├── ValidatingAdmissionReviewService.java  [신규]
│       └── WorkloadUserLabelValidatingHandler.java [신규]
└── configuration/
    ├── MutatingConfiguration.java             [변경] 신규 핸들러 빈 등록
    └── ValidatingConfiguration.java           [신규] validating 빈 등록
```
