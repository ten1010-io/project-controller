# UserAuthorityReview Webhook — Python → Java 포팅 결과

## 개요

Python `aipub-admission-controller`의 `user_authority_review` mutate/validate 로직을 Java(`project-controller`)로 포팅.
K8s admission webhook으로 `UserAuthorityReview` CREATE 시 RBAC 권한을 분석하여 status에 주입하고, validate에서 mutation 성공 여부를 검증합니다.

## 아키텍처

```
kubectl create UserAuthorityReview (spec.resources: ["apps/deployments", ...])
  │
  ▼
MutatingWebhookConfiguration
  → /api/v1/userauthorityreview/mutate
  → UserAuthorityReviewMutateHandler
    1. 사용자 검증 (aipub member + AipubUser 존재)
    2. spec.resources 파싱 및 검증
    3. ClusterRoleBinding/RoleBinding 수집 → RBAC rule 분석
    4. status (isClusterAdmin, aipubRole, authorities) 계산
    5. JSON Patch: /status + /metadata/ownerReferences(dummy) 주입
  │
  ▼
ValidatingWebhookConfiguration
  → /api/v1/userauthorityreview/validate
  → UserAuthorityReviewValidateHandler
    1. ownerReferences 존재 여부 확인
    2. 정확히 1개 + name="dummy" 검증
    → mutation이 정상 수행되었음을 확인
```

## 파일 구조

```
src/main/java/.../mutating/
├── UserAuthorityReviewMutateController.java    # /api/v1/userauthorityreview/mutate
├── UserAuthorityReviewValidateController.java  # /api/v1/userauthorityreview/validate
└── service/
    ├── UserAuthorityReviewMutateHandler.java   # mutate 핵심 로직
    ├── UserAuthorityReviewValidateHandler.java  # validate 핵심 로직
    └── RBACAuthorityStatus.java                # verb별 권한 상태 모델

kubernetes/.../templates/
├── mutating-webhook-user-authority-review.yaml
└── validating-webhook-user-authority-review.yaml

configuration/
└── MutatingConfiguration.java  # bean 등록 추가
```

## 메서드 매핑 — Mutate

### Entry Point

| Python | Java | 설명 |
|--------|------|------|
| `UserAuthorityReviewMutateService.mutate()` | `UserAuthorityReviewMutateHandler.handle()` | 진입점. 사용자 검증 → status 계산 → JSON Patch 응답 |
| `mutate()` 내 `request.operation != "CREATE"` 체크 | `canHandle()` | CREATE + kind=UserAuthorityReview 필터링 |

### 사용자 검증

| Python | Java | 설명 |
|--------|------|------|
| `self._aipub_user_service.get_aipub_user_name(request)` | `userInfoAnalyzer.analyzeV2(request.getUserInfo())` | OIDC username에서 aipub user name 추출 |
| `self._user_informer.get(name=aipub_user_name)` | `analysis.getAipubUser()` | AipubUser 리소스 조회 (informer cache) |

### Status 계산

| Python | Java | 설명 |
|--------|------|------|
| `_get_status(request, output, aipub_user_name, user_authority_review)` | `computeStatus(request, aipubUserName, resources)` | RBAC 권한 분석 전체 흐름 |

#### `computeStatus()` 내부 흐름

```
1. 전체 Namespace 목록 수집 (namespaceIndexer.list())
2. spec.resources 파싱 및 검증
   - "group/resource" → cluster-scoped or 전 namespace 확장
   - "group/resource/namespace" → 특정 namespace
   - 존재하지 않는 group/resource → 400 에러
3. ClusterRoleBinding 처리
   - username/group으로 CRB 수집
   - CRB → ClusterRole → rules 분석
   - cluster-admin 감지 (* in apiGroups, resources, verbs)
4. RoleBinding 처리
   - username/group으로 RB 수집
   - RB → Role/ClusterRole → rules 분석
   - namespaced 리소스만 처리
5. asterisk get 변환
6. AIPub role 계산 (admin 여부, project 멤버십)
```

### RBAC Rule 분석

| Python | Java | 설명 |
|--------|------|------|
| `_is_cluster_admin(rule)` | `isClusterAdminRule(rule)` | `*` in apiGroups + resources + verbs → cluster admin |
| `_get_group_resources(rule, target_groups, target_resources)` | `getGroupResources(rule, targetGroups, targetResources)` | rule의 apiGroups/resources와 요청 대상의 교집합 계산 |
| `_add_rule_to_authorities(authorities, request_resources, rule)` | `addRuleToAuthorities(authorities, requestResources, rule)` | rule의 verbs/resourceNames를 authority에 병합 |
| `_convert_asterisk_get(authorities)` | `convertAsteriskGet(authorities)` | get=["*"] + list=false → 실제 object name 목록으로 변환 (TODO) |

### Role Binding 수집

| Python | Java | 설명 |
|--------|------|------|
| `clusterrolebinding_informer.get_all_by_index("user", username)` | `getClusterRoleBindingsForUser(username, groups)` | 전체 CRB 순회하며 subject 매칭 |
| `clusterrolebinding_informer.get_all_by_index("group", group)` | (위 메서드에 통합) | group 매칭도 동일 메서드에서 처리 |
| `rolebinding_informer.get_all_by_index("user", username)` | `getRoleBindingsForUser(username, groups)` | 전체 RB 순회하며 subject 매칭 |

> **참고**: Python은 informer의 index를 사용하여 O(1) 조회하지만, Java는 전체 list를 순회합니다.
> 성능 최적화가 필요한 경우 indexer에 user/group 인덱스를 추가할 수 있습니다.

### AIPub Role 계산

| Python | Java | 설명 |
|--------|------|------|
| `_get_aipub_role(request, aipub_user_name)` | `getAipubProjectRoles(aipubUserName)` + `groups.contains(AIPUB_ADMIN_GROUP_NAME)` | admin 여부는 groups에서, project role은 informer에서 |
| `self._project_informer.get_all()` 순회 | `projectIndexer.list()` 순회 | 전체 Project에서 member 매칭 |
| `project.spec.members` + `project.status.all_bound_aipub_users` 검증 | 동일 로직 | member 존재 + bound 여부 확인 |

### 응답 빌드

| Python | Java | 설명 |
|--------|------|------|
| `user_authority_review.set_status(status)` | `buildStatusNode(statusResult)` → JSON Patch `/status` | Python은 전체 object 교체, Java는 JSON Patch |
| `_set_dummy_owner_refernece(user_authority_review)` | `buildDummyOwnerReferences()` → JSON Patch `/metadata/ownerReferences` | dummy ownerRef: `{kind: Node, name: dummy, uid: d-u-m-m-y}` |
| `Serializer.serialize(user_authority_review)` → `output.to_allowed(mutated_obj)` | `V1AdmissionReviewUtils.allow(review, patchBuilder.build())` | Python은 전체 object 반환, Java는 JSON Patch 반환 |

## 메서드 매핑 — Validate

| Python | Java | 설명 |
|--------|------|------|
| `UserAuthorityReviewValidateService.validate()` | `UserAuthorityReviewValidateHandler.handle()` | 진입점 |
| `request.operation != "CREATE"` 체크 | `canHandle()` | CREATE + kind=UserAuthorityReview 필터링 |
| `request.obj["metadata"].get("ownerReferences")` | `object.path("metadata").path("ownerReferences")` | ownerReferences 조회 |
| `len(owner_references) != 1` | `ownerRefs.size() != 1` | 정확히 1개 검증 |
| `owner_references[0]["name"] != "dummy"` | `ownerRefs.get(0).path("name").asText("") != "dummy"` | dummy name 검증 |

## 메서드 매핑 — RBACAuthorityStatus

| Python | Java | 설명 |
|--------|------|------|
| `RBACAuthorityStatus.__init__()` | `new RBACAuthorityStatus()` | 모든 verb를 빈 상태로 초기화 |
| `add(verb, resource_names)` | `add(verb, resourceNames)` | 특정 verb에 resourceNames 병합 |
| `add_all(resource_names)` | `addAll(resourceNames)` | 모든 verb에 resourceNames 병합 |
| `set(verb, value)` | `setGet(value)` | 특정 verb 값 교체 (get 전용) |
| `@property get` → `["*"]` or list | `getGet()` → `List.of("*")` or `List.copyOf()` | `*` 포함 시 축약 |

### Verb 타입

| Verb | Python 타입 | Java 타입 | 설명 |
|------|------------|-----------|------|
| `get` | `set` → `list[str]` | `Set<String>` → `List<String>` | resource name 목록 |
| `list` | `bool` | `boolean` | 전체 목록 조회 가능 여부 |
| `watch` | `set` → `list[str]` | `Set<String>` → `List<String>` | resource name 목록 |
| `patch` | `set` → `list[str]` | `Set<String>` → `List<String>` | resource name 목록 |
| `update` | `set` → `list[str]` | `Set<String>` → `List<String>` | resource name 목록 |
| `create` | `bool` | `boolean` | 생성 가능 여부 |
| `delete` | `set` → `list[str]` | `Set<String>` → `List<String>` | resource name 목록 |
| `deletecollection` | `bool` | `boolean` | 일괄 삭제 가능 여부 |

## Webhook Configuration

| 항목 | Mutating | Validating |
|------|----------|-----------|
| MWC/VWC 이름 | `userauthorityreview-mutate.project-controller.project.aipub.ten1010.io` | `userauthorityreview-validate.project-controller.project.aipub.ten1010.io` |
| Path | `/api/v1/userauthorityreview/mutate` | `/api/v1/userauthorityreview/validate` |
| Resource | `aipub.ten1010.io/userauthorityreviews` | 동일 |
| Operation | CREATE | CREATE |
| failurePolicy | Fail | Fail |
| Scope | `*` (cluster-wide) | `*` |
| namespaceSelector | 없음 (전체) | 없음 (전체) |

## 의존성

| Component | 용도 |
|-----------|------|
| `UserInfoAnalyzer` | OIDC username → AipubUser 매핑 |
| `ApiResourceDiscovery` | group/resource 존재 여부, namespaced 여부 조회 |
| `SharedInformerFactory` | Namespace, CRB, CR, RB, Role, Project informer |

## 알려진 제한사항

1. **`convertAsteriskGet` 미완성**: Python은 `get=["*"]` + `list=false`일 때 `APIResourceManager.get_all_object_names()`로 실제 object name 목록을 가져오지만, Java의 `ApiResourceDiscovery`에는 해당 메서드가 없어 현재 `["*"]`를 유지합니다.

2. **RoleBinding 조회 성능**: Python은 informer의 user/group index를 활용하여 O(1) 조회하지만, Java는 전체 list를 순회합니다. 대규모 클러스터에서 성능 이슈가 있을 수 있으며, indexer에 커스텀 인덱스를 추가하여 최적화할 수 있습니다.
