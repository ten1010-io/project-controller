# Dev Log: User Label Mutation Webhook 구현

## 목표

K8s 오브젝트 CREATE 시 Mutating Webhook이 아래 두 label을 자동 주입한다.

| Label Key | 값 |
|---|---|
| `aipub.ten1010.io/username` | AipubUser CR의 `metadata.name` |
| `aipub.ten1010.io/userid` | AipubUser CR의 `spec.id` |

대상: `oidc:aipub-member` 그룹 유저가 namespaced 오브젝트를 CREATE할 때만 적용.

---

## 구현 내용

### 변경 파일

**`domain/k8s/LabelConstants.java`**
- `OBJECT_OWN_USERID_KEY = "aipub.ten1010.io/userid"` 상수 추가

**`mutating/service/UserInfoAnalyzer.java`**
- `// todo--` 주석으로 미완성이던 username 파싱 로직 완성
- `"oidc:aipub-member:alice"` → `:` split → 마지막 토큰 `"alice"` 추출
- `:` 없는 경우 username 전체를 그대로 사용하는 방어 로직 추가

**`mutating/V1AdmissionReviewUtils.java`**
- `allow(review)`: response가 이미 있으면 새로 만들지 않고 idempotent하게 동작하도록 변경
- `allow(review, patch)`: 기존 patch에 새 patch operations를 누적(merge)하는 방식으로 변경 → 여러 핸들러가 순차 실행되어도 patch가 덮어쓰이지 않음

**`mutating/service/CompositeReviewHandler.java`**
- 기존: 첫 번째 매칭 핸들러만 실행 (단락 평가)
- 변경: 모든 매칭 핸들러를 순차 실행 (chain all) → `UserLabelReviewHandler`와 기존 `DeploymentReviewHandler` 등이 공존 가능

### 신규 파일

**`mutating/service/UserLabelReviewHandler.java`**
- `ReviewHandler` 인터페이스 직접 구현 (특정 K8s 타입에 종속 안 됨)
- `canHandle()`: `operation == "CREATE"` && `namespace != null`
- `handle()` 흐름:
  1. `oidc:aipub-member` 아니면 → allow (라벨 없이 통과)
  2. AipubUser CR 없으면 → allow
  3. `spec.id` null이면 → reject 500
  4. `metadata.labels` 없으면 빈 object로 init patch 추가
  5. `aipub.ten1010.io/username`, `aipub.ten1010.io/userid` label add patch 적용

**`configuration/MutatingConfiguration.java`**
- `userLabelReviewHandler` Bean 추가

---

## kind 클러스터 테스트 환경 구성

### 사전 조건 (완료)

```bash
# kind 클러스터: local (이미 존재)
kind get clusters
# → local

# cert-manager 설치
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml
kubectl wait --namespace cert-manager --for=condition=Available deployment/cert-manager-webhook --timeout=120s

# CRD 설치
kubectl apply -f kubernetes/examples/crd.yaml
kubectl apply -f kubernetes/controller/project-controller/templates/crd.yaml
```

### 이미지 빌드 및 로드

```bash
# Java 21 필요 (sdkman 사용)
source ~/.sdkman/bin/sdkman-init.sh && sdk use java 21.0.9-tem

./mvnw spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=project-controller:dev
kind load docker-image project-controller:dev --name local
```

### 배포 매니페스트 (`k8s-dev/`)

| 파일 | 내용 |
|---|---|
| `rbac.yaml` | ServiceAccount + cluster-admin ClusterRoleBinding |
| `cert.yaml` | cert-manager self-signed Issuer + Certificate (commonName 필수) |
| `deployment.yaml` | Deployment (PEM TLS) + Service (443→8443) |
| `webhook.yaml` | MutatingWebhookConfiguration (namespaced CREATE, failurePolicy: Ignore) |

```bash
kubectl apply -f k8s-dev/rbac.yaml
kubectl apply -f k8s-dev/cert.yaml
kubectl apply -f k8s-dev/deployment.yaml
kubectl apply -f k8s-dev/webhook.yaml
```

### 트러블슈팅 이력

| 문제 | 원인 | 해결 |
|---|---|---|
| `cert.yaml` 적용 오류 | 첫 줄 `rapiVersion` 오타 | `apiVersion`으로 수정 |
| `Empty issuer DN not allowed` | self-signed 인증서에 subject DN 없음 | `cert.yaml` Certificate에 `commonName: project-controller.default.svc` 추가 |
| Maven 컴파일 에러 (Java 17) | mvnw가 Java 17 사용 | sdkman으로 Java 21 전환 후 컴파일 |
| `IllegalArgumentException` in `LabelUtils` | `oidc:aipub-member:testuser`는 `:` 기준 3개 토큰 → `getValueOfLabelString`이 2개만 허용 | `lastIndexOf(":")` 방식으로 마지막 토큰만 추출하도록 수정 |
| `Forbidden` (impersonation 유저) | `oidc:aipub-member` 그룹에 K8s RBAC 권한 없음 | 테스트용 ClusterRoleBinding 추가 |

---

## 테스트 방법

### 기본 확인 (label 주입 안 되는 케이스)

```bash
# 일반 kubectl 유저는 oidc:aipub-member 그룹이 아니므로 label 주입 안 됨
kubectl run test-pod --image=nginx -n test-ns
kubectl get pod test-pod -n test-ns -o jsonpath='{.metadata.labels}' | jq .
# → {"run": "test-pod"} (label 없음 = 정상)
```

### 실제 label 주입 테스트 (impersonation 사용)

```bash
# 테스트 네임스페이스 생성
kubectl create namespace test-ns

# AipubUser CR 생성
kubectl apply -f - <<EOF
apiVersion: project.aipub.ten1010.io/v1alpha1
kind: AipubUser
metadata:
  name: testuser
spec:
  id: "user-001"
EOF

# impersonation 유저에게 테스트용 RBAC 권한 부여
kubectl create clusterrolebinding aipub-member-test \
  --clusterrole=edit \
  --group=oidc:aipub-member
so 
# oidc:aipub-member:testuser로 impersonation해서 Pod 생성
kubectl run test-pod-member --image=nginx -n test-ns \
  --as=oidc:aipub-member:testuser \
  --as-group=oidc:aipub-member

# label 확인
kubectl get pod test-pod-member -n test-ns -o jsonpath='{.metadata.labels}' | jq .
# 실제 결과:
# {
#   "aipub.ten1010.io/userid": "user-001",
#   "aipub.ten1010.io/username": "testuser",
#   "run": "test-pod-member"
# }

# webhook 로그 확인
kubectl logs deployment/project-controller | grep -i "admission\|label\|user"
```

### 재배포 절차 (코드 수정 후)

```bash
source ~/.sdkman/bin/sdkman-init.sh && sdk use java 21.0.9-tem
./mvnw spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=project-controller:dev
kind load docker-image project-controller:dev --name local
kubectl rollout restart deployment/project-controller
```

---

## 보안 이슈 점검 결과

### 🔴 CRITICAL
- `k8s-dev/rbac.yaml` — `cluster-admin` 과도한 권한. 프로덕션에서는 최소 권한 ClusterRole로 교체 (`kubernetes/controller/project-controller/templates/cluster-role.yaml` 참고)

### 🟠 HIGH
- `UserInfoAnalyzer.java` — `Objects.requireNonNull(aipubUser)`로 AipubUser 없으면 NPE → 500. `failurePolicy: Fail` 환경에서 오브젝트 생성 차단
- `k8s-dev/webhook.yaml` — `namespaceSelector`에서 앱 자신의 네임스페이스(`default`) 미제외 → webhook 무한 루프 위험
- `PodReviewHandler.java` — `_getNodes(pod) // todo` 미완성 코드 존재

### 🟡 MEDIUM
- `mutating-webhook-configuration.yaml` — `caBundle: <CA_BUNDLE>` 플레이스홀더 미치환 시 TLS 검증 실패
- `k8s-dev/deployment.yaml` — `APP_K8S_VERIFY_SSL: "false"` → 프로덕션에서는 반드시 `true`
- `kustomization.yaml` — `APP_AIPUB_PASSWORD=asdasdasd` 평문 패스워드 Git 커밋

### 🔵 LOW
- `UserLabelReviewHandler.java` — `spec.id` label 값 유효성 검증 없음 (K8s label: 최대 63자, `[a-zA-Z0-9._-]`만 허용, [공식문서](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/))
- `k8s-dev/` — dev/prod 설정 혼용 위험

### label 값 유효성 실패 재현

```bash
# spec.id에 @ 포함된 AipubUser 생성
kubectl apply -f - <<EOF
apiVersion: project.aipub.ten1010.io/v1alpha1
kind: AipubUser
metadata:
  name: baduser
spec:
  id: "user@company.com"
EOF

# Workspace 생성 시도 → K8s가 label validation 단계에서 거부
kubectl apply -f - --as=oidc:aipub-member:baduser --as-group=oidc:aipub-member <<EOF
apiVersion: aipub.ten1010.io/v1
kind: Workspace
metadata:
  name: bad-workspace
  namespace: test-ns
spec:
  replicas: 1
  ssh:
    port: 2222
    publicKey: "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC test"
  template:
    spec:
      containers:
        - name: bad-workspace
          image: nginx
EOF
# 결과: metadata.labels: Invalid value: "user@company.com": a valid label must be...
```

> Python 구현체(`user_label.py`)에도 동일하게 validation 없음. `spec.id`는 외부 시스템(Keycloak 등)에서 받아오는 자유 형식 값이므로 주입 전 검증 필요.

---

## Workspace CR로 label 주입 테스트

Pod 외에 namespaced CR로도 동일하게 동작함을 확인.

```bash
# Workspace CR 생성 (impersonation)
kubectl apply -f - --as=oidc:aipub-member:testuser --as-group=oidc:aipub-member <<EOF
apiVersion: aipub.ten1010.io/v1
kind: Workspace
metadata:
  name: test-workspace
  namespace: test-ns
spec:
  replicas: 1
  ssh:
    port: 2222
    publicKey: "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC test"
  template:
    spec:
      containers:
        - name: test-workspace
          image: nginx
EOF

# label 확인
kubectl get workspace test-workspace -n test-ns -o jsonpath='{.metadata.labels}' | jq .
# 결과:
# {
#   "aipub.ten1010.io/userid": "user-001",
#   "aipub.ten1010.io/username": "testuser"
# }
```

> Pod와 달리 기본 label이 없어서 webhook이 주입한 label만 명확하게 확인 가능.

---

## OIDC 설정 조사

### 실제 운영 클러스터 OIDC 확인 명령어

```bash
# kube-apiserver OIDC 설정 확인
kubectl get pod -n kube-system -l component=kube-apiserver \
  -o jsonpath='{.items[0].spec.containers[0].command}' \
  | tr ',' '\n' | grep -i oidc
```

### 실제 운영 클러스터 결과

```
--oidc-issuer-url=https://aipub-keycloak.cluster4.idc1.ten1010.io/realms/aipub
--oidc-client-id=k8s
--oidc-username-claim=preferred_username   ← Keycloak 로그인 아이디
--oidc-groups-claim=groups
--oidc-username-prefix=oidc:               ← K8s username = "oidc:{preferred_username}"
--oidc-groups-prefix=oidc:                ← K8s group = "oidc:{group}"
--oidc-ca-file=/etc/kubernetes/pki/aipub-ca.crt
```

OIDC provider는 **Keycloak**. `preferred_username: alice` → K8s username: `oidc:alice` → webhook 파싱 후 `alice` → AipubUser CR name.

---

## 로컬 OIDC 환경 구성 (kind + Keycloak)

실제 운영과 동일한 OIDC 인증 흐름을 로컬에서 재현하기 위한 설정.

### 구조

```
Docker
├── kind cluster (local)
│   └── kube-apiserver
│       └── oidc-issuer-url: https://host.docker.internal:8443
│
└── docker-compose
    └── keycloak (HTTPS :8443, self-signed TLS)
```

### 파일 구조

```
k8s-dev/
├── gen-certs.sh          # CA + Keycloak self-signed TLS 인증서 생성
├── docker-compose.yaml   # Keycloak 26.3.3 (HTTPS, realm 자동 import)
├── realm-init.json       # aipub realm 설정 (aipub-backend에서 복사)
├── setup-keycloak.sh     # realm import + client secret 재발급 (HTTPS용 CA cert 사용)
├── kind-config.yaml      # OIDC 설정 + CA cert 마운트 포함
└── certs/                # gen-certs.sh 실행 후 생성
    ├── ca.crt            # kind control-plane에 마운트되는 CA
    ├── keycloak.crt      # SAN: host.docker.internal, localhost
    └── keycloak.key
```

### 실행 순서

```bash
# 1. self-signed 인증서 생성 (최초 1회)
chmod +x k8s-dev/gen-certs.sh
./k8s-dev/gen-certs.sh
# → k8s-dev/certs/ 아래 ca.crt, keycloak.crt, keycloak.key 생성

# 2. kind 클러스터 생성 (OIDC 설정 + CA cert 마운트 포함)
# → 기존 클러스터가 있으면 먼저 삭제
kind delete cluster --name local
kind create cluster --config k8s-dev/kind-config.yaml
# → control-plane 노드에 ca.crt 마운트
# → kube-apiserver에 OIDC 설정 자동 적용 (oidc-issuer-url: https://localhost:30443/realms/aipub)

# 3. Keycloak TLS Secret 및 realm ConfigMap 생성
kubectl create secret tls keycloak-tls \
  --cert=k8s-dev/certs/keycloak.crt \
  --key=k8s-dev/certs/keycloak.key

kubectl create configmap keycloak-realm \
  --from-file=realm-init.json=k8s-dev/realm-init.json

# 4. Keycloak pod 배포 (realm-init.json 자동 import)
kubectl apply -f k8s-dev/keycloak.yaml
kubectl wait --for=condition=Available deployment/keycloak --timeout=120s
# → NodePort 30443으로 접근 가능 (https://localhost:30443)
# → realm "aipub", client "k8s" 자동 생성

# 5. realm 설정 완료 및 client secret 확인
chmod +x k8s-dev/setup-keycloak.sh
./k8s-dev/setup-keycloak.sh
# → client-secret 출력됨. 이후 단계에서 <CLIENT_SECRET> 자리에 사용
```

---

## OIDC groups claim 매핑 구조

Keycloak의 realm role이 어떻게 K8s의 group으로 인식되는지 정리한다.

### 전체 흐름

```
realm-init.json (Keycloak)              kind-config.yaml (kube-apiserver)
───────────────────────────              ─────────────────────────────────
protocolMapper:                          oidc-groups-claim: groups
  oidc-usermodel-realm-role-mapper       oidc-groups-prefix: "oidc:"
claim.name: "groups"
         │                                        │
         └──── JWT: {"groups":["aipub-member"]} ───┘
                                                  │
                                       K8s group: oidc:aipub-member
```

### 1. Keycloak 측: realm role → OIDC token의 groups claim

**출처:** `k8s-dev/realm-init.json` (line 1356-1371)

```json
{
  "name": "groups",
  "protocol": "openid-connect",
  "protocolMapper": "oidc-usermodel-realm-role-mapper",
  "config": {
    "multivalued": "true",
    "id.token.claim": "true",
    "access.token.claim": "true",
    "claim.name": "groups",
    "jsonType.label": "String"
  }
}
```

- `protocolMapper: oidc-usermodel-realm-role-mapper` — 유저에게 할당된 **realm role** 목록을 가져옴
- `claim.name: "groups"` — JWT 토큰에 `groups`라는 이름의 claim으로 삽입
- `id.token.claim: "true"` — id_token에 포함 (kube-apiserver가 id_token을 검증함)

즉 Keycloak **group이 아니라 realm role**을 OIDC groups claim에 매핑한다. `realm-init.json`에서도 `"groups": []` (비어있음)이고, `aipub-member`는 realm role로 정의되어 있다 (line 81).

### 2. kube-apiserver 측: OIDC token의 groups claim → K8s group

**출처:** `k8s-dev/kind-config.yaml` (kubeadmConfigPatches)

```yaml
oidc-groups-claim: groups      # JWT에서 "groups" claim을 읽음
oidc-groups-prefix: "oidc:"    # 읽은 값에 "oidc:" 접두사 추가
```

- kube-apiserver가 OIDC id_token의 `groups` claim 값(`["aipub-member"]`)을 읽어서
- `oidc:` prefix를 붙여 K8s group `oidc:aipub-member`로 인식

### 3. Java controller 측: K8s group 체크

**출처:** `src/.../mutating/service/UserInfoAnalyzer.java` (line 40-42)

```java
public static boolean isAipubMember(List<String> groups) {
    return groups.contains(K8sGroupConstants.AIPUB_MEMBER_GROUP_NAME);
    // "oidc:aipub-member"
}
```

**출처:** `src/.../mutating/service/UserLabelReviewHandler.java` (line 48-51)

```java
if (!analysis.isAipubMember()) {
    V1AdmissionReviewUtils.allow(review);
    return;  // aipub-member가 아니면 라벨 주입 건너뜀
}
```

### 결론

테스트 시 Keycloak **group**이 아니라 **realm role** `aipub-member`를 유저에게 할당해야 한다.
HTTP API: `POST /admin/realms/{realm}/users/{userId}/role-mappings/realm`
(자세한 순서는 `k8s-dev/keycloak-setup.http` 참고)

---

## End-to-End 테스트: Keycloak 유저 생성 → Workspace 생성 → 라벨 확인

전체 흐름: Keycloak에 실제 유저를 만들고 → AipubUser CRD로 등록하고 → 그 유저 자격으로 kubectl 인증 후 → Workspace를 생성해서 → username/userid 라벨이 자동 주입되는지 확인한다.

### 전제 조건

- `k8s-dev/gen-certs.sh` 실행 완료 (certs/ 디렉토리 존재)
- kind 클러스터 실행 중 (`kind-config.yaml` 기반, OIDC 설정 포함)
- Keycloak docker-compose 실행 중 (`setup-keycloak.sh` 완료)
- `kubectl`, `curl`, `jq` 설치됨
- controller 이미지 빌드 완료 (`project-controller:dev`)

---

### Step 1. Keycloak에 테스트 유저 생성

Keycloak Admin Console(`https://localhost:8443`) 또는 REST API로 유저를 생성한다.

```bash
# admin token 발급
TOKEN=$(curl --cacert k8s-dev/certs/ca.crt -sf \
  -d "client_id=admin-cli" \
  -d "username=aipubadmin" \
  -d "password=Ten1010!!" \
  -d "grant_type=password" \
  "https://localhost:8443/realms/master/protocol/openid-connect/token" \
  | jq -r '.access_token')

# 유저 생성 (username: testuser, password: Test1234!)
curl --cacert k8s-dev/certs/ca.crt -sf \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "enabled": true,
    "credentials": [{"type":"password","value":"Test1234!","temporary":false}]
  }' \
  "https://localhost:8443/admin/realms/aipub/users"

# 생성된 유저 ID 확인
USER_ID=$(curl --cacert k8s-dev/certs/ca.crt -sf \
  -H "Authorization: Bearer $TOKEN" \
  "https://localhost:8443/admin/realms/aipub/users?username=testuser" \
  | jq -r '.[0].id')
echo "User ID: $USER_ID"

# aipub-member 그룹 ID 확인
GROUP_ID=$(curl --cacert k8s-dev/certs/ca.crt -sf \
  -H "Authorization: Bearer $TOKEN" \
  "https://localhost:8443/admin/realms/aipub/groups?search=aipub-member" \
  | jq -r '.[0].id')
echo "Group ID: $GROUP_ID"

# 유저를 aipub-member 그룹에 추가
curl --cacert k8s-dev/certs/ca.crt -sf \
  -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  "https://localhost:8443/admin/realms/aipub/users/${USER_ID}/groups/${GROUP_ID}"
echo "testuser → aipub-member 그룹 추가 완료"
```

---

### Step 2. AipubUser CRD 등록

controller가 username → AipubUser를 조회해서 userid를 라벨에 주입하므로, K8s에도 AipubUser를 만들어야 한다.

```bash
# AipubUser 생성 (name은 Keycloak username과 일치, spec.id는 Keycloak User ID)
kubectl apply -f - <<EOF
apiVersion: project.aipub.ten1010.io/v1alpha1
kind: AipubUser
metadata:
  name: testuser
spec:
  id: "${USER_ID}"
EOF

# 확인
kubectl get aipubuser testuser
```
/
> `spec.id`는 위에서 확인한 Keycloak User ID (UUID 형식)이다.
> K8s label value 제약(최대 63자, `[a-zA-Z0-9._-]`)에 맞는 값이어야 한다. UUID는 36자이므로 안전하다.

---

### Step 3. RBAC — aipub-member 그룹에 K8s 권한 부여

OIDC 인증된 유저가 Workspace를 생성하려면 해당 namespace에 create 권한이 있어야 한다.

```bash
# aipub-member 그룹에 namespace 내 편집 권한 부여 (테스트용)
kubectl create clusterrolebinding aipub-member-edit \
  --clusterrole=edit \
  --group=oidc:aipub-member \
  --dry-run=client -o yaml | kubectl apply -f -
```

---

### Step 4. controller 빌드 및 배포

```bash
# Java 21 확인
java -version  # openjdk 21.x.x

# controller 이미지 빌드 (kind 클러스터에 직접 로드)
./mvnw clean package -DskipTests
docker build -t project-controller:dev .
kind load docker-image project-controller:dev --name local

# CRD 등록 (kubernetes/examples/crd.yaml)
kubectl apply -f kubernetes/examples/crd.yaml

# controller 배포 (k8s-dev/ 매니페스트)
kubectl apply -f k8s-dev/rbac.yaml
kubectl apply -f k8s-dev/cert.yaml
# cert-manager가 인증서를 발급할 때까지 잠시 대기 (보통 10-30초)
kubectl wait --for=condition=Ready certificate/project-controller-tls --timeout=60s
kubectl apply -f k8s-dev/deployment.yaml
kubectl apply -f k8s-dev/webhook.yaml

# controller Pod 실행 확인
kubectl get pods -l app=project-controller
kubectl logs -l app=project-controller --tail=20
```

---

### Step 5. kubectl OIDC 인증 설정

testuser의 Keycloak 토큰을 받아서 kubectl context를 구성한다.

```bash
# testuser 토큰 발급 (Resource Owner Password Grant — 개발용)
CLIENT_SECRET="<setup-keycloak.sh 에서 출력된 값>"

RESPONSE=$(curl --cacert k8s-dev/certs/ca.crt -sf \
  -d "client_id=k8s" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "username=testuser" \
  -d "password=Test1234!" \
  -d "grant_type=password" \
  "https://localhost:8443/realms/aipub/protocol/openid-connect/token")

ID_TOKEN=$(echo $RESPONSE | jq -r '.id_token')
REFRESH_TOKEN=$(echo $RESPONSE | jq -r '.refresh_token')
echo "ID Token: ${ID_TOKEN:0:50}..."

# kubectl user 등록
kubectl config set-credentials testuser-oidc \
  --auth-provider=oidc \
  --auth-provider-arg=idp-issuer-url=https://localhost:8443/realms/aipub \
  --auth-provider-arg=client-id=k8s \
  --auth-provider-arg=client-secret=${CLIENT_SECRET} \
  --auth-provider-arg=id-token=${ID_TOKEN} \
  --auth-provider-arg=refresh-token=${REFRESH_TOKEN} \
  --auth-provider-arg=idp-certificate-authority=./k8s-dev/certs/ca.crt

# context 등록
kubectl config set-context testuser-context \
  --cluster=kind-local \
  --user=testuser-oidc

# 인증 확인 (oidc:testuser 로 인식되면 성공)
kubectl --context=testuser-context auth whoami
# 출력 예: Username: oidc:testuser, Groups: [oidc:aipub-member ...]
```

---

### Step 6. Workspace 생성 (testuser 자격)

Workspace는 namespaced 리소스이므로 먼저 namespace가 필요하다.

```bash
# Workspace를 생성할 namespace 준비 (admin 권한으로)
kubectl create namespace test-ns

# testuser 자격으로 Workspace 생성
kubectl --context=testuser-context apply -f - <<EOF
apiVersion: aipub.ten1010.io/v1
kind: Workspace
metadata:
  name: my-workspace
  namespace: test-ns
spec:
  replicas: 1
  image: ubuntu:22.04
EOF
```

---

### Step 7. 라벨 주입 확인

```bash
# Workspace에 username/userid 라벨이 붙었는지 확인
kubectl get workspace my-workspace -n test-ns -o jsonpath='{.metadata.labels}' | jq .

# 기대 출력:
# {
#   "aipub.ten1010.io/username": "testuser",
#   "aipub.ten1010.io/userid": "<USER_ID>"
# }

# 또는 describe로 확인
kubectl describe workspace my-workspace -n test-ns | grep -A5 Labels
```

라벨이 붙어 있으면 mutation webhook이 정상 동작하는 것이다.

---

### 트러블슈팅

| 증상 | 원인 및 해결 |
|------|-------------|
| `auth whoami` 에서 `oidc:` prefix 없이 username 출력 | kube-apiserver OIDC 설정 미적용 → kind 클러스터 재생성 필요 |
| `Forbidden` on Workspace create | RBAC 미설정 → Step 3 재확인 |
| 라벨이 안 붙음 (Workspace 생성은 성공) | controller 로그 확인 (`kubectl logs -l app=project-controller`) |
| `No handler found for review` 에러 | Workspace가 `namespaced && CREATE` 조건 충족인지 확인 |
| `AipubUser not found` NPE | Step 2에서 AipubUser 이름이 Keycloak username과 정확히 일치하는지 확인 |
| cert-manager 인증서 발급 안됨 | cert-manager 설치 여부 확인: `kubectl get pods -n cert-manager` |

### kind-config.yaml 핵심 설정

```yaml
kubeadmConfigPatches:
  - |
    kind: ClusterConfiguration
    apiServer:
      extraArgs:
        oidc-issuer-url: https://host.docker.internal:8443/realms/aipub
        oidc-client-id: k8s
        oidc-username-claim: preferred_username
        oidc-groups-claim: groups
        oidc-username-prefix: "oidc:"
        oidc-groups-prefix: "oidc:"
        oidc-ca-file: /etc/kubernetes/pki/keycloak-ca.crt  # gen-certs.sh로 생성한 CA
```

> `host.docker.internal`: Mac/Windows Docker Desktop에서 kind 컨테이너 → 호스트 머신 → docker-compose Keycloak으로 연결되는 경로.

---

## 다음 구현 과제 (user.md 기준)

- [ ] OwnerReference 주입 (CREATE 시)
- [ ] 소유권 이관 (Transfer) — UPDATE 시 `user.aipub.ten1010.io/transfer` annotation 트리거
- [ ] Pod parent workload label 상속
- [ ] Validating Webhook — owner label 직접 변경 차단
- [ ] StatefulSet, DaemonSet, Job, CronJob ReviewHandler 추가