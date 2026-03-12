# Plan: Port UserOwnerReference Mutation to Java Project

## Goal

Port the `UserOwnerReferenceMutateService` from `aipub-admission-controller` (Python) into `resource-group-controller` (Java/Spring Boot). On CREATE of namespaced resources, the webhook injects an `ownerReference` pointing to the requesting user's `AipubUser` custom resource.

---

## Source Behavior (user_owner.py)

1. **Trigger**: Only on `CREATE` operations for namespaced resources
2. **Skip if**: Non-namespaced resource, or GVK is in an exception list
3. **Resolve user**: Extract username from `request.userInfo.username`, verify user is in `aipub-member` group, split by delimiter to get AipubUser name
4. **Fetch CR**: Lookup `AipubUser` cluster-scoped custom resource by name via K8s API
5. **Mutate**: Append an `ownerReference` to the object:
   ```json
   {
     "apiVersion": "<aipubUser.apiVersion>",
     "blockOwnerDeletion": false,
     "controller": false,
     "kind": "<aipubUser.kind>",
     "name": "<aipubUser.metadata.name>",
     "uid": "<aipubUser.metadata.uid>"
   }
   ```
6. **Allow without mutation** if user cannot be resolved (not a member, no username, etc.)

---

## Design Decisions

### Separate webhook endpoint vs. extending existing one

**Decision: Separate endpoint** (`/admissionreviews/userowner`)

- The existing `/admissionreviews` endpoint handles ResourceGroup scheduling (affinity/tolerations) for specific workload types only
- UserOwner mutation applies to **all namespaced resource types** on CREATE — a fundamentally different scope
- Separate webhook configs in K8s allow independent failure policies, resource rules, and lifecycle
- Keeps the two concerns cleanly separated

### AipubUser CR lookup strategy

**Decision: Direct API call** (not shared informer)

- The AipubUser set is small and lookups happen only on CREATE — low frequency
- Avoids adding informer infrastructure and cache management for a simple lookup
- Use `CustomObjectsApi.getClusterCustomObject()` from the K8s Java client

### JSON patch strategy

**Decision: Add a new `AddJsonPatchElement`** alongside existing `ReplaceJsonPatchElement`

- ownerReferences may not exist yet on the object, so we need an "add" op (not "replace")
- JSON Patch `add` with path `/metadata/ownerReferences/-` appends to array
- Need to handle the case where `ownerReferences` is null (create the array first)

---

## Implementation Steps

### Step 1: Configuration — AipubUser properties

**File**: New `src/main/java/io/ten1010/groupcontroller/mutating/userowner/AipubUserProperties.java`

Spring `@ConfigurationProperties` class holding:
- `aipubUserApiGroup` (default: `project.aipub.ten1010.io`)
- `aipubUserApiVersion` (default: `v1alpha1`)
- `aipubUserApiPlural` (default: `aipubusers`)
- `aipubUserApiKind` (default: `AipubUser`)
- `aipubUserMemberGroup` (default: `oidc:aipub-member`)
- `aipubUserNameDelimiter` (default: `:`)
- `addOwnerExceptGvkList` (default: `aipub.ten1010.io/v1alpha1/Commit,aipub.ten1010.io/v1/Commit`)

### Step 2: AipubUser service

**File**: New `src/main/java/io/ten1010/groupcontroller/mutating/userowner/AipubUserService.java`

Methods:
- `Optional<String> resolveAipubUserName(V1AdmissionReviewRequest request)` — extracts username from userInfo, checks group membership, splits by delimiter
- `Map<String, Object> getAipubUser(String name)` — calls `CustomObjectsApi.getClusterCustomObject()`, returns raw map

### Step 3: Add JSON patch element

**File**: New `src/main/java/io/ten1010/groupcontroller/mutating/AddJsonPatchElement.java`

Same pattern as `ReplaceJsonPatchElement` but with `op = "add"`.

### Step 4: UserOwner admission review service

**File**: New `src/main/java/io/ten1010/groupcontroller/mutating/userowner/UserOwnerAdmissionReviewService.java`

Core logic:
```
review(request):
  if operation != CREATE → allow
  if namespace is null → allow
  if GVK in exception list → allow
  userName = resolveAipubUserName(request)
  if userName is null → allow
  aipubUser = getAipubUser(userName)
  if not found → reject 400
  if API error → reject 500
  build JSON patch to add ownerReference
  return patch response
```

JSON patch construction:
- Read existing `ownerReferences` from `request.object`
- If null/missing: patch with `add` at `/metadata/ownerReferences` with `[{ownerRef}]`
- If present: patch with `add` at `/metadata/ownerReferences/-` with `{ownerRef}`

### Step 5: Controller endpoint

**File**: New `src/main/java/io/ten1010/groupcontroller/mutating/userowner/UserOwnerAdmissionReviewController.java`

```java
@RestController
@RequestMapping("/admissionreviews/userowner")
public class UserOwnerAdmissionReviewController {
    @PostMapping
    public ResponseEntity<V1AdmissionReview> create(@RequestBody V1AdmissionReview review) { ... }
}
```

### Step 6: Spring configuration

**File**: Extend `MutatingConfiguration.java` or create new `UserOwnerMutatingConfiguration.java`

Register beans:
- `CustomObjectsApi` (from K8s client)
- `AipubUserService`
- `UserOwnerAdmissionReviewService`

### Step 7: Kubernetes webhook manifest

**File**: New `kubernetes/controller/templates/mutating-webhook-userowner.yaml`

```yaml
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
metadata:
  name: userowner.resource-group.ten1010.io
webhooks:
  - name: userowner.resource-group.ten1010.io
    admissionReviewVersions: ["v1"]
    clientConfig:
      caBundle: <CA_BUNDLE>
      service:
        namespace: resource-group-controller
        name: resource-group-controller
        path: /admissionreviews/userowner
        port: 8080
    failurePolicy: Ignore
    operations: ["CREATE"]
    rules:
      - apiGroups: ["*"]
        apiVersions: ["*"]
        operations: ["CREATE"]
        resources: ["*"]
        scope: Namespaced
    sideEffects: None
    timeoutSeconds: 10
```

### Step 8: Tests

**File**: New `src/test/java/io/ten1010/groupcontroller/mutating/userowner/UserOwnerAdmissionReviewServiceTest.java`

Test cases:
- Non-CREATE operation → allow without patch
- Non-namespaced resource → allow without patch
- GVK in exception list → allow without patch
- User not in member group → allow without patch
- User in member group, AipubUser found → patch with ownerReference
- User in member group, AipubUser not found (404) → reject
- Object already has ownerReferences → append to existing array
- Object has no ownerReferences → create array with single entry

---

## File Summary

| # | File | Action |
|---|------|--------|
| 1 | `mutating/userowner/AipubUserProperties.java` | **New** |
| 2 | `mutating/userowner/AipubUserService.java` | **New** |
| 3 | `mutating/AddJsonPatchElement.java` | **New** |
| 4 | `mutating/userowner/UserOwnerAdmissionReviewService.java` | **New** |
| 5 | `mutating/userowner/UserOwnerAdmissionReviewController.java` | **New** |
| 6 | `configuration/UserOwnerMutatingConfiguration.java` | **New** |
| 7 | `kubernetes/controller/templates/mutating-webhook-userowner.yaml` | **New** |
| 8 | `test/.../userowner/UserOwnerAdmissionReviewServiceTest.java` | **New** |
| 9 | `src/main/resources/application.properties` | **Edit** — add aipubuser config defaults |

---

## Risks & Notes

- **Performance**: Direct API call per CREATE request adds latency. If this becomes a bottleneck, can migrate to a shared informer with lister cache later.
- **Failure policy**: Set to `Ignore` so webhook failures don't block resource creation.
- **Scope**: The webhook catches ALL namespaced CREATE operations. The exception list provides an escape hatch for specific GVKs.
- **ownerReference semantics**: `controller=false, blockOwnerDeletion=false` — this is a non-controlling reference, so it won't trigger garbage collection cascading deletes. It's purely for tracking ownership.
