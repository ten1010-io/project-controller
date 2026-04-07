# UserAuthorityReview Python → Java 포팅 — 코드 변경점 상세

## 1. Mutate Handler

### 1.1 진입점: `mutate()` → `canHandle()` + `handle()`

**Python** (`mutate/user_authority_review.py:301-339`)
```python
def mutate(self, request):
    output = MutateServiceOutput(allowed=True)
    if request.operation != "CREATE":       # skip
        return output
    if request.kind.kind != self._review_kind:  # skip
        return output
    aipub_user_name = self._aipub_user_service.get_aipub_user_name(request)
    if aipub_user_name is None:             # 400 reject
        ...
    user = self._user_informer.get(name=aipub_user_name)
    if user is None:                        # 400 reject
        ...
    # deserialize → _get_status → set_status → set_dummy_owner → serialize
```

**Java** (`UserAuthorityReviewMutateHandler.java:77-158`)
```java
canHandle(): operation == "CREATE" && kind == "UserAuthorityReview"
handle():
  analyzeV2() → isAipubMember? → getAipubUser? → parse spec.resources
  → computeStatus() → JSON Patch (add /status, add /metadata/ownerReferences)
```

**변경점:**
- Python은 `get_aipub_user_name()` + `user_informer.get()` 2단계. Java는 `analyzeV2()` 1단계로 통합
- Python은 전체 object 교체 (Serializer.serialize). Java는 JSON Patch (`add /status`, `add /metadata/ownerReferences`)
- Python의 `analyzeV2`에 해당하는 부분은 `UserInfoAnalyzer.analyzeV2()`로 별도 클래스. 기존 UserLabel/UserOwner와 동일한 패턴 사용
- Python은 `_get_status`가 `output` 파라미터를 직접 변경 (side effect). Java는 `StatusResult` record를 반환하여 순수 함수 스타일

---

### 1.2 `_get_status()` → `computeStatus()`

**Python** (`mutate/user_authority_review.py:142-288`)

6단계:
1. namespace 목록 수집 + spec.resources 파싱/검증
2. ClusterRoleBinding 수집 → ClusterRole rules 분석
3. RoleBinding 수집 → Role/ClusterRole rules 분석
4. `_convert_asterisk_get()` 호출
5. `_get_aipub_role()` 호출
6. `UserAuthorityReviewStatus` 생성

**Java** (`UserAuthorityReviewMutateHandler.java:161-317`)

동일 6단계. 차이:

| 단계 | Python | Java | 차이 |
|------|--------|------|------|
| 1 | `set(user_authority_review.spec.resources)` | `new HashSet<>(requestResources)` | 동일 (중복 제거) |
| 1 | `splitted = request_resource.split("/")` | `parts = requestResource.split("/")` | 동일 |
| 1 | `len(splitted) == 2/3` + else → 400 | `parts.length < 2 \|\| > 3` → 400 | Java가 더 명시적 |
| 2 | `get_all_by_index("user"/"group")` → O(1) | `list()` 전체 순회 → O(n) | **성능 차이** |
| 3 | 동일 | 동일 | **성능 차이** |
| 4 | `_convert_asterisk_get()` | `convertAsteriskGet()` + try/catch → 400 | Java는 에러 시 reject |
| 5 | `_get_aipub_role(request, aipub_user_name)` | `isAdmin` inline + `getAipubProjectRoles()` | 분리하여 처리 |
| 6 | `UserAuthorityReviewStatus(...)` | `StatusResult(...)` record | Java는 내부 record 사용 |

---

### 1.3 `_is_cluster_admin()` → `isClusterAdminRule()`

**Python** (`mutate/user_authority_review.py:57-64`)
```python
def _is_cluster_admin(self, rule):
    if "*" not in rule.api_groups: return False
    if "*" not in rule.resources: return False
    if "*" not in rule.verbs: return False
    return True
```

**Java** (`UserAuthorityReviewMutateHandler.java:320-328`)
```java
private boolean isClusterAdminRule(V1PolicyRule rule) {
    if (rule.getApiGroups() == null || !rule.getApiGroups().contains("*")) return false;
    if (rule.getResources() == null || !rule.getResources().contains("*")) return false;
    return rule.getVerbs() != null && rule.getVerbs().contains("*");
}
```

**변경점:** Java는 null 체크 추가. Python의 `rule.api_groups`는 SDK에서 항상 list 보장. Java의 `V1PolicyRule`은 nullable.

---

### 1.4 `_get_group_resources()` → `getGroupResources()`

**Python** (`mutate/user_authority_review.py:85-104`)
```python
groups = target_groups if "*" in rule.api_groups else target_groups.intersection(set(rule.api_groups))
resources = target_resources if "*" in rule.resources else target_resources.intersection(set(rule.resources))
for group in groups:
    for resource in resources:
        if self._api_resource_manager.is_exist(f"{group}/{resource}"):
            group_resources.add(f"{group}/{resource}")
```

**Java** (`UserAuthorityReviewMutateHandler.java:330-351`)
```java
Set<String> groups = ruleApiGroups.contains("*") ? targetGroups : intersection(targetGroups, new HashSet<>(ruleApiGroups));
Set<String> resources = ruleResources.contains("*") ? targetResources : intersection(targetResources, new HashSet<>(ruleResources));
// 동일 루프
```

**변경점:** 없음. 1:1 동일 로직. `intersection()` 유틸 메서드로 분리.

---

### 1.5 `_add_rule_to_authorities()` → `addRuleToAuthorities()`

**Python** (`mutate/user_authority_review.py:106-123`)
```python
if rule.resource_names:
    if "*" in rule.resource_names: resource_names = ["*"]
    else: resource_names = rule.resource_names
else:
    resource_names = ["*"]
if "*" in rule.verbs: authoritiy.add_all(resource_names)
else:
    for verb in rule.verbs: authoritiy.add(verb, resource_names)
```

**Java** (`UserAuthorityReviewMutateHandler.java:354-378`)
```java
if (rule.getResourceNames() != null && !rule.getResourceNames().isEmpty()) {
    resourceNames = rule.getResourceNames().contains("*") ? List.of("*") : rule.getResourceNames();
} else {
    resourceNames = List.of("*");
}
// 동일 verb 처리
```

**변경점:**
- Python: `if rule.resource_names:` → falsy 체크 (None 또는 빈 리스트 모두 false)
- Java: `!= null && !isEmpty()` → 동일 의미. null-safe

---

### 1.6 `_convert_asterisk_get()` → `convertAsteriskGet()`

**Python** (`mutate/user_authority_review.py:125-140`)
```python
all_object_names = self._api_resource_manager.get_all_object_names(
    group_resource=group_resource, namespace=namespace
)
authority_status.set(verb="get", value=all_object_names)
```

**Java** (`UserAuthorityReviewMutateHandler.java:381-396`)
```java
List<String> objectNames = this.apiResourceDiscovery.getAllObjectNames(groupResource, namespace);
status.setGet(objectNames);
```

**변경점:**
- Python은 예외 발생 시 상위로 전파 → 500. Java는 `computeStatus()`에서 try/catch → `StatusResult.error()` → 400 reject
- Python의 `set(verb, value)`는 범용. Java의 `setGet()`은 get 전용 (실제로 `set()`이 get에서만 호출되므로 OK)

---

### 1.7 `_get_aipub_role()` → `getAipubProjectRoles()` + inline isAdmin

**Python** (`mutate/user_authority_review.py:66-83`)
```python
is_admin = self._aipub_admin_group in request.user_info.groups
for project in self._project_informer.get_all():
    for member in project.spec.members:
        if member.aipub_user != aipub_user_name: continue
        if aipub_user_name not in project.status.all_bound_aipub_users: continue
        projects.append(AIPubProjectRole(name=project.metadata.name, role=member.role))
        break
return AIPubRole(is_admin=is_admin, projects=projects)
```

**Java** (`UserAuthorityReviewMutateHandler.java:314,440-462`)
```java
boolean isAdmin = groups.contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME);
// getAipubProjectRoles():
for (V1alpha1Project project : projectIndexer.list()) {
    // null 체크 3번 (spec, members, status, allBoundAipubUsers)
    if (!project.getStatus().getAllBoundAipubUsers().contains(aipubUserName)) continue;
    for (V1alpha1ProjectMember member : project.getSpec().getMembers()) {
        if (aipubUserName.equals(member.getAipubUser())) { ... break; }
    }
}
```

**변경점:**
- Python: `allBoundAipubUsers` 체크를 member 루프 안에서 수행
- Java: `allBoundAipubUsers` 체크를 **member 루프 바깥**에서 먼저 수행 (early return)
- **동작 차이 없음**: Python도 `aipub_user_name not in project.status.all_bound_aipub_users` 체크 후 continue → member 루프의 나머지 skip. Java는 project 레벨에서 먼저 skip

---

### 1.8 `_set_dummy_owner_reference()` → `buildDummyOwnerReferences()`

**Python** (`mutate/user_authority_review.py:290-299`)
```python
owner_reference = V1OwnerReference(
    api_version="v1", controller=True, kind="Node", name="dummy", uid="d-u-m-m-y"
)
user_authority_review.metadata.owner_references = [owner_reference]
```

**Java** (`UserAuthorityReviewMutateHandler.java:511-522`)
```java
ownerRef.put("apiVersion", "v1");
ownerRef.put("controller", true);
ownerRef.put("kind", "Node");
ownerRef.put("name", "dummy");
ownerRef.put("uid", "d-u-m-m-y");
```

**변경점:** 값 동일. Python은 object 속성 직접 설정, Java는 JSON Patch로 주입.

---

### 1.9 CRB/RB subject 매칭: indexed lookup → full scan

**Python** (`mutate/user_authority_review.py:195-202`)
```python
binding_list = self._clusterrolebinding_informer.get_all_by_index(index_name="user", key=username)
for group in groups:
    binding_list.extend(self._clusterrolebinding_informer.get_all_by_index(index_name="group", key=group))
```

**Java** (`UserAuthorityReviewMutateHandler.java:399-417`)
```java
for (V1ClusterRoleBinding crb : clusterRoleBindingIndexer.list()) {
    for (RbacV1Subject subject : crb.getSubjects()) {
        if ("User".equals(subject.getKind()) && username.equals(subject.getName())) { result.add(crb); break; }
        if ("Group".equals(subject.getKind()) && groups.contains(subject.getName())) { result.add(crb); break; }
    }
}
```

**변경점:**
- Python: informer에 user/group index 등록 → O(1) lookup
- Java: 전체 binding list 순회 → O(n)
- **기능 동일, 성능 차이만 있음**. 대규모 클러스터에서 binding이 많을 경우 성능 이슈 가능

---

### 1.10 nonResourceURLs 처리

**Python** (`mutate/user_authority_review.py:211`)
```python
if rule.non_resource_ur_ls is not None:
    continue
```

**Java** (`UserAuthorityReviewMutateHandler.java:238`)
```java
if (rule.getNonResourceURLs() != null && !rule.getNonResourceURLs().isEmpty()) {
    continue;
}
```

**변경점:** Python은 `is not None`만 체크 (빈 리스트도 skip 안 함). Java는 `!= null && !isEmpty()` (빈 리스트도 skip). 실제로 K8s는 nonResourceURLs가 설정되면 항상 비어있지 않으므로 **동작 차이 없음**.

---

## 2. Validate Handler

### 2.1 `validate()` → `canHandle()` + `handle()`

**Python** (`validate/user_authority_review.py:12-36`)
```python
def validate(self, request):
    if request.operation != "CREATE": return output  # skip
    if request.kind.kind != self._kind: return output  # skip
    owner_references = request.obj["metadata"].get("ownerReferences")
    if owner_references is None: → 400
    if len(owner_references) != 1: → 400
    if owner_references[0]["name"] != "dummy": → 400
    return output  # allowed
```

**Java** (`UserAuthorityReviewValidateHandler.java:21-62`)
```java
canHandle(): operation == "CREATE" && kind == "UserAuthorityReview"
handle():
  ownerRefs = object.path("metadata").path("ownerReferences")
  if (!ownerRefs.isArray()) → 400
  if (ownerRefs.size() != 1) → 400
  if (!"dummy".equals(ownerRefs.get(0).path("name").asText(""))) → 400
  → allow
```

**변경점:**
- Python: `request.obj["metadata"].get("ownerReferences")` → None이면 reject
- Java: `object.path("metadata").path("ownerReferences")` → MissingNode이면 `isArray()=false` → reject
- Python: `owner_references[0]["name"]` → KeyError 가능. Java: `asText("")` → 안전 (빈 문자열)
- **동작 동일**

---

## 3. RBACAuthorityStatus

### 3.1 모델 매핑

**Python** (`models/user_authority_review.py:24-158`)

| verb | Python 타입 | JSON key | 초기값 |
|------|-----------|---------|--------|
| get | `set` → `list[str]` | "get" | `set()` |
| list | `bool` | "list" | `False` |
| watch | `set` → `list[str]` | "watch" | `set()` |
| patch | `set` → `list[str]` | "patch" | `set()` |
| update | `set` → `list[str]` | "update" | `set()` |
| create | `bool` | "create" | `False` |
| delete | `set` → `list[str]` | "delete" | `set()` |
| deletecollection | `bool` | "deletecollection" | `False` |

**Java** (`RBACAuthorityStatus.java`) — 동일 구조

**변경점:** 없음. 1:1 매핑.

### 3.2 `add()` / `addAll()` / `set()`

- `add(verb, resourceNames)`: bool verb는 `["*"]`일 때만 true 설정, list verb는 union
- `addAll(resourceNames)`: 모든 verb에 add
- `set(verb, value)`: Python은 범용 setter. Java는 `setGet(value)` 전용 (실제 호출이 get에서만 발생)

---

## 4. ApiResourceDiscovery 변경점

### 4.1 `getAllObjectNames()` 추가

**Python** (`api_resource_manager.py:102-127`) → **Java** (`ApiResourceDiscovery.java:158-204`)

| 항목 | Python | Java |
|------|--------|------|
| core group 체크 | `group == "" or group == "core"` | `group.isEmpty() \|\| "core".equals(group)` |
| ns + non-namespaced | `raise Exception()` | `throw RuntimeException(...)` |
| groupVersion 없음 | `KeyError` (dict) | `throw RuntimeException(...)` |
| API 호출 실패 | 예외 전파 | `throw RuntimeException(...)` |

### 4.2 `asBoolean()` → `booleanValue()`

`buildSnapshot()`의 `resource.path("namespaced")` 파싱에서 변경. `asBoolean()`은 문자열 "true"도 coercion하지만, `booleanValue()`는 JSON boolean만 처리.

### 4.3 `asText()` → `textValue()`

`buildSnapshot()`의 `name`, `kind`, `groupName`, `groupVersion` 파싱에서 변경. 프로젝트 규칙 준수.

---

## 5. Webhook YAML

### 5.1 Mutating

| 항목 | Python 원본 | Java |
|------|------------|------|
| name | `userauthorityreview.admission-controller.aipub.ten1010.io` | `userauthorityreview-mutate.project-controller.project.aipub.ten1010.io` |
| path | `/mutate` | `/api/v1/userauthorityreview/mutate` |
| service.name | `admission-controller` | `project-controller` |
| service.namespace | `aipub` | `project-controller` |
| apiGroups | `aipub.ten1010.io` | `aipub.ten1010.io` ✅ |
| resources | `userauthorityreviews` | `userauthorityreviews` ✅ |
| operations | `CREATE` | `CREATE` ✅ |
| scope | `*` | `*` ✅ |
| failurePolicy | `Fail` | `Fail` ✅ |
| namespaceSelector | 없음 | `{ }` (동일) ✅ |

### 5.2 Validating

동일 패턴. path만 `/validate` → `/api/v1/userauthorityreview/validate`.

---

## 6. Configuration 변경

### 6.1 `MutatingConfiguration.java`

추가된 bean:
- `UserInfoAnalyzer` — `@Bean`으로 분리 (기존 inline 생성 제거)
- `userAuthorityReviewMutateHandlers` — `@Qualifier`, `List<ReviewHandler>` 반환
- `userAuthorityReviewValidateHandlers` — `@Qualifier`, `List<ReviewHandler>` 반환

---

## 7. 미포팅 항목 (별도 기능)

| Python 기능 | 상태 | 이유 |
|------------|------|------|
| `UserLabelSyncronizer` (300초 주기 Pod label 동기화) | ✅ 포팅 완료 | `UserLabelSynchronizer.java`로 이미 존재 |
| `_update_configmap` (API resource → ConfigMap 기록) | 미포팅 | Python의 `APIResourceManager`가 갱신 시 `api-resources` ConfigMap에 kind→groupResource 매핑 기록. 외부 시스템이 이 ConfigMap을 읽는 경우 필요. 의존 여부 확인 필요 |
| CRB/RB informer user/group index | 미포팅 | Python은 informer에 user/group index 등록하여 O(1) 조회. Java는 `list()` 전체 순회 O(n)으로 대체. 기능 동일, 성능 차이만 |

---

## 8. 테스트 커버리지 (55 tests)

| 클래스 | 건수 | 커버 범위 |
|-------|------|---------|
| `RBACAuthorityStatusTest` | 10 | add/addAll/setGet/wildcard/bool verb/unknown verb |
| `UserAuthorityReviewMutateHandlerTest` | 24 | canHandle 4, reject 7, success 4, RBAC rules 7, AipubRole 2 |
| `UserAuthorityReviewValidateHandlerTest` | 8 | canHandle 3, ownerRef 검증 5 |
| `ApiResourceDiscoveryTest` | 13 | booleanValue 3, core alias 6, basic discovery 4 |
