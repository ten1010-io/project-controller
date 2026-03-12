# Plan: User Label Mutation 기능 Java 포팅

Python `aipub-admission-controller`의 `UserLabelMutateService`를 Java `resource-group-controller`에 이식한다.

---

## 목표

네임스페이스 범위 오브젝트 생성(CREATE) 시 admission webhook에서 아래 라벨을 자동으로 주입한다.

| 라벨 키 | 값 |
|---|---|
| `aipub.ten1010.io/username` | AipubUser 리소스의 `metadata.name` |
| `aipub.ten1010.io/userid` | AipubUser 리소스의 `spec.id` |

---

## 분석: Python vs Java 현황

### Python 로직 요약

1. `operation == CREATE` 이고 `namespace != null` 인 경우에만 동작
2. `userInfo.groups`에 `oidc:aipub-member` 포함 여부 확인
3. `userInfo.username`을 `:` 기준 split → 마지막 세그먼트 = AipubUser 이름
4. AipubUser CR 조회 → `metadata.name`(username), `spec.id`(userid) 추출
5. Fallback: 직접 식별 실패 시 owner 오브젝트의 라벨을 복사
6. JSON Patch로 `metadata/labels`에 두 라벨 추가

### Java 기존 인프라 (재사용 가능)

| 기존 클래스 | 역할 |
|---|---|
| `UserInfoAnalyzer` | `isAipubMember()` 체크 + AipubUser 인덱서 조회 (이미 구현됨, `// todo--` 주석 있음) |
| `UserInfoAnalysis` | username, groups, aipubUser 래핑 |
| `AbstractReviewHandler<T>` | `canHandle()` (타입 매칭), `getRequestObject()`, `createJsonNode()` |
| `V1AdmissionReviewUtils.allow(review, patch)` | patch 포함 응답 생성 |
| `JsonPatchBuilder` / `JsonPatchOperationBuilder` | JSON Patch 구성 |
| `K8sGroupConstants.AIPUB_MEMBER_GROUP_NAME` | `"oidc:aipub-member"` 상수 |
| `MutatingConfiguration` | 핸들러 Bean 등록 위치 |

> `UserInfoAnalyzer`에 이미 `// todo--` 주석이 있는 것으로 보아, username 파싱 로직이 미완성 상태임. 이번 작업에서 완성.

---

## 구현 계획

### Step 1. 라벨 상수 추가

**파일**: `domain/k8s/ProjectApiConstants.java` (기존 파일에 추가)

```java
public static final String USERNAME_LABEL_KEY = "aipub.ten1010.io/username";
public static final String USER_ID_LABEL_KEY  = "aipub.ten1010.io/userid";
```

---

### Step 2. UserInfoAnalyzer의 username 파싱 완성

**파일**: `mutating/service/UserInfoAnalyzer.java`

현재 `// todo--` 주석이 있는 부분이 `LabelUtils.getValueOfLabelString(username)`으로 `:` 파싱을 하고 있으나,
Python 로직(`username.split(":")[-1]`)과 정확히 맞는지 검증 필요.

Python: `"oidc:john"` → split(":") → last → `"john"`
Java 현재: `LabelUtils.getValueOfLabelString("oidc:john")` → `"john"` ✓ (동일)

단, `"john"` (delimiter 없는 경우) 처리 방어 로직 추가:
```java
// username에 ":" 가 없으면 username 전체를 AipubUser 이름으로 사용
String aipubUserName = username.contains(":")
    ? LabelUtils.getValueOfLabelString(username)
    : username;
String aipubUserKey = this.keyResolver.resolveKey(aipubUserName);
```

---

### Step 3. UserLabelReviewHandler 신규 생성

**파일**: `mutating/service/UserLabelReviewHandler.java`

기존 `AbstractReviewHandler<T>`는 특정 K8s 타입에 바인딩되어 있어, 모든 오브젝트에 적용하는 이 핸들러는
`ReviewHandler` 인터페이스를 직접 구현한다.

```
[canHandle 조건]
- request.operation == "CREATE"
- request.namespace != null (네임스페이스 범위 오브젝트)

[handle 로직]
1. review.request.userInfo 추출
2. UserInfoAnalyzer.analyze(userInfo) 호출
3. analysis.isAipubMember() == false → V1AdmissionReviewUtils.allow(review) (라벨 없이 통과)
4. aipubUser 없음 → owner 라벨 fallback 시도 (Step 4)
5. 라벨 주입:
   - "/metadata/labels/aipub.ten1010.io~1username" = aipubUser.metadata.name
   - "/metadata/labels/aipub.ten1010.io~1userid"   = aipubUser.spec.id
   (JSON Pointer에서 '/'는 '~1'로 이스케이프)
6. V1AdmissionReviewUtils.allow(review, jsonPatch)

[에러 처리]
- aipubUser.spec.id == null → reject(review, 500, "user id not found")
```

> **JSON Pointer 이스케이프 주의**: label key의 `/`는 JSON Patch path에서 `~1`로 변환해야 함.
> 예: `/metadata/labels/aipub.ten1010.io~1username`

---

### Step 4. Owner Fallback 로직 (선택적)

Python의 fallback: aipub-member가 아닌 사용자(ServiceAccount 등)가 만드는 오브젝트는
부모 오브젝트(ownerReferences[controller=true])의 라벨을 복사.

Java 구현 방법:
- `review.request.object`의 `ownerReferences`에서 `controller: true` 인 항목 찾기
- `SharedInformerFactory`의 해당 타입 인덱서에서 부모 오브젝트 조회
- 부모의 `metadata.labels`에서 `username`, `userid` 라벨 복사

> 초기 구현에서는 Step 3까지만 구현하고 fallback은 향후 추가로 분리 가능.

---

### Step 5. MutatingConfiguration에 Bean 등록

**파일**: `configuration/MutatingConfiguration.java`

```java
@Bean
public UserLabelReviewHandler userLabelReviewHandler(
    SharedInformerFactory sharedInformerFactory) {
  return new UserLabelReviewHandler(new UserInfoAnalyzer(sharedInformerFactory));
}
```

`CompositeReviewHandler`는 `List<ReviewHandler>`를 주입받으므로 자동으로 포함됨.

---

### Step 6. 핸들러 실행 순서 확인

`CompositeReviewHandler`가 핸들러를 순차 실행할 경우, `UserLabelReviewHandler`는
기존 `PodReviewHandler` 등과 **같은 리뷰를 처리하게 됨**.

현재 각 핸들러는 `canHandle()`로 타입을 구분하지만, `UserLabelReviewHandler`는 타입 무관하게 동작.
Pod CREATE 시 `PodReviewHandler`와 `UserLabelReviewHandler` 모두 실행될 수 있음.

`CompositeReviewHandler` 동작 확인 필요:
- 첫 번째 매칭 핸들러만 실행하는지 (단락 평가)
- 모든 매칭 핸들러를 순차 실행하는지

→ 모두 실행하는 방식이면 OK. 단락 평가 방식이면 `UserLabelReviewHandler`를 먼저 등록하거나
`CompositeReviewHandler`를 수정해야 함.

---

## 신규/수정 파일 목록

| 파일 | 작업 |
|---|---|
| `domain/k8s/ProjectApiConstants.java` | 라벨 상수 2개 추가 |
| `mutating/service/UserInfoAnalyzer.java` | `// todo--` 부분 완성, 방어 로직 추가 |
| `mutating/service/UserLabelReviewHandler.java` | **신규 생성** |
| `configuration/MutatingConfiguration.java` | `userLabelReviewHandler` Bean 추가 |

---

## kind에서 검증하는 방법

### 구조

```
kind 클러스터 (Docker 컨테이너)
├── K8s API 서버
├── project-controller Deployment  ← 앱이 여기서 실행됨
│   └── Service (ClusterIP :8443)
├── MutatingWebhookConfiguration   ← Service를 가리킴
└── cert-manager                   ← TLS 인증서 자동 발급
```

Admission Webhook은 K8s API 서버가 HTTPS로 콜백하므로, 앱을 kind 클러스터 **안에** Deployment로 배포해야 한다.
`app.k8s.kube-config.mode: IN_CLUSTER`로 동작하며, `ServiceAccount` + RBAC으로 클러스터 리소스에 접근한다.

---

### Step 1. kind 클러스터 생성

```bash
kind create cluster --name dev
kubectl cluster-info --context kind-dev
```

---

### Step 2. cert-manager 설치

cert-manager가 webhook 서버의 TLS 인증서를 자동 발급/갱신한다.

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml
kubectl wait --namespace cert-manager \
  --for=condition=Available deployment/cert-manager-webhook \
  --timeout=120s
```

---

### Step 3. CRD 설치

AipubUser, Project 등 커스텀 리소스 CRD를 먼저 설치해야 앱이 뜰 때 informer가 초기화된다.

```bash
kubectl apply -f <crd-manifests>/
```

---

### Step 4. Docker 이미지 빌드 후 kind에 로드

kind는 로컬 Docker 데몬과 별개의 이미지 저장소를 가지므로, `kind load`로 직접 주입해야 한다.

```bash
# 빌드 (Spring Boot buildpack 사용)
./mvnw spring-boot:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName=project-controller:dev

# kind 클러스터에 이미지 로드
kind load docker-image project-controller:dev --name dev
```

---

### Step 5. 배포 매니페스트 작성

아래 리소스를 `k8s-dev/` 디렉터리에 작성한다.

**`k8s-dev/rbac.yaml`** — 앱이 클러스터 리소스를 읽고 쓸 수 있도록 권한 부여

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: project-controller
  namespace: default
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: project-controller
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin          # 개발용. 운영에서는 최소 권한으로 교체
subjects:
  - kind: ServiceAccount
    name: project-controller
    namespace: default
```

**`k8s-dev/cert.yaml`** — cert-manager로 자체 서명 인증서 발급

```yaml
apiVersion: cert-manager.io/v1
kind: Issuer
metadata:
  name: selfsigned-issuer
  namespace: default
spec:
  selfSigned: {}
---
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: project-controller-tls
  namespace: default
spec:
  secretName: project-controller-tls
  issuerRef:
    name: selfsigned-issuer
  dnsNames:
    - project-controller.default.svc
    - project-controller.default.svc.cluster.local
```

**`k8s-dev/deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: project-controller
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app: project-controller
  template:
    metadata:
      labels:
        app: project-controller
    spec:
      serviceAccountName: project-controller
      containers:
        - name: project-controller
          image: project-controller:dev
          imagePullPolicy: Never          # kind load로 주입한 이미지 사용
          ports:
            - containerPort: 8080
          env:
            - name: SERVER_SSL_ENABLED
              value: "true"
            - name: SERVER_PORT
              value: "8443"
            - name: SERVER_SSL_KEY_STORE_TYPE
              value: PKCS12
            - name: SERVER_SSL_KEY_STORE
              value: /tls/keystore.p12
            - name: SERVER_SSL_KEY_STORE_PASSWORD
              value: ""                   # cert-manager Secret의 실제 값으로 교체
            - name: APP_K8S_VERIFY_SSL
              value: "false"
            - name: APP_K8S_KUBE_CONFIG_MODE
              value: IN_CLUSTER
            - name: APP_AIPUB_ENABLED
              value: "false"
          volumeMounts:
            - name: tls
              mountPath: /tls
              readOnly: true
      volumes:
        - name: tls
          secret:
            secretName: project-controller-tls
---
apiVersion: v1
kind: Service
metadata:
  name: project-controller
  namespace: default
spec:
  selector:
    app: project-controller
  ports:
    - port: 443
      targetPort: 8443
```

> **TLS 키스토어 포맷 참고**: cert-manager가 발급한 Secret은 `tls.crt` / `tls.key` PEM 파일이다.
> Spring Boot는 기본적으로 PKCS12 keystore를 기대하므로, init container로 변환하거나
> `SERVER_SSL_CERTIFICATE` / `SERVER_SSL_CERTIFICATE_PRIVATE_KEY` 환경변수(Spring Boot 3.1+)를 사용하면 PEM 직접 지정 가능하다.

**`k8s-dev/webhook.yaml`**

```yaml
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
metadata:
  name: user-label-webhook
  annotations:
    cert-manager.io/inject-ca-from: default/project-controller-tls  # CA 자동 주입
webhooks:
  - name: user-label.aipub.ten1010.io
    admissionReviewVersions: ["v1"]
    clientConfig:
      service:
        name: project-controller
        namespace: default
        path: /v1/admission-reviews
        port: 443
    rules:
      - operations: ["CREATE"]
        apiGroups: ["*"]
        apiVersions: ["*"]
        resources: ["*"]
        scope: Namespaced
    sideEffects: None
    failurePolicy: Ignore        # 개발 중에는 Ignore — 앱이 죽어도 오브젝트 생성 가능
    namespaceSelector:
      matchExpressions:
        - key: kubernetes.io/metadata.name
          operator: NotIn
          values: ["kube-system", "kube-public", "kube-node-lease", "cert-manager"]
```

---

### Step 6. 배포

```bash
kubectl apply -f k8s-dev/rbac.yaml
kubectl apply -f k8s-dev/cert.yaml
kubectl apply -f k8s-dev/deployment.yaml
kubectl apply -f k8s-dev/webhook.yaml

# 앱 기동 확인
kubectl rollout status deployment/project-controller
kubectl logs -f deployment/project-controller
```

---

### Step 7. 테스트

```bash
# 테스트용 네임스페이스 생성
kubectl create namespace test-ns

# AipubUser CR 생성 (webhook이 이 CR을 조회해 라벨 값을 결정)
kubectl apply -f - <<EOF
apiVersion: project.aipub.ten1010.io/v1alpha1
kind: AipubUser
metadata:
  name: testuser
spec:
  id: "user-001"
EOF

# 테스트 오브젝트 생성
# (userInfo.username이 "oidc:testuser" 형태여야 라벨이 주입됨)
kubectl run test-pod --image=nginx -n test-ns

# 라벨 확인
kubectl get pod test-pod -n test-ns -o jsonpath='{.metadata.labels}' | jq
# 기대 결과:
# {
#   "aipub.ten1010.io/username": "testuser",
#   "aipub.ten1010.io/userid": "user-001"
# }

# webhook 동작 로그 확인
kubectl logs deployment/project-controller | grep -i "user-label\|admission"
```

---

### 이미지 재빌드 시 반복 절차

코드 수정 후 재배포:

```bash
./mvnw spring-boot:build-image -DskipTests \
  -Dspring-boot.build-image.imageName=project-controller:dev
kind load docker-image project-controller:dev --name dev
kubectl rollout restart deployment/project-controller
```

---

### 검증 체크리스트

- [ ] `oidc:aipub-member` 그룹 유저 생성 오브젝트 → 라벨 주입됨
- [ ] `oidc:aipub-admin` / ServiceAccount 생성 오브젝트 → 라벨 없이 통과
- [ ] cluster-scoped 오브젝트(ClusterRole 등) → webhook 미호출 (scope: Namespaced)
- [ ] AipubUser CR 없는 상태에서 aipub-member 생성 → 적절한 에러 반환
- [ ] webhook 로그에서 JSON Patch 내용 확인

---

## 참고: Python 대비 생략 항목 (향후 과제)

| Python 기능 | 상태 |
|---|---|
| Owner 라벨 Fallback | Step 4로 분리, 초기 구현에서 제외 가능 |
| User Label 보호 Validation webhook | 별도 `ValidateReviewHandler` 구현 필요 |
| Pod 라벨 동기화 주기 작업 | 별도 background task 구현 필요 |
| Workload 라벨 (`workload-name`, `workload-kind`) | 별도 `WorkloadLabelReviewHandler` 구현 필요 |
