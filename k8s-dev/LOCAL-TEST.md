# Local Kind Test - UserRelationship v2 Webhook (E2E)

End-to-end test for the Java v2 webhook that adds `-v2` suffixed user labels and annotation-based owner references on resource CREATE by an OIDC-authenticated aipub-member user.

## What gets tested

| Feature | Python (original) | Java v2 (this test) |
|---------|-------------------|---------------------|
| User labels | `aipub.ten1010.io/username`, `aipub.ten1010.io/userid` | `aipub.ten1010.io/username-v2`, `aipub.ten1010.io/userid-v2` |
| Owner reference | `metadata.ownerReferences[]` entry | `metadata.annotations["aipub.ten1010.io/owner-reference-v2"]` (JSON) |
| Label propagation | Labels copied from parent owner | v2 labels copied from parent owner |

## Prerequisites

- [kind](https://kind.sigs.k8s.io/)
- [Docker](https://docs.docker.com/get-docker/)
- kubectl, jq, openssl
- Java 21 Temurin (`sdk use java 21.0.9-tem`)

## Quick Start

```bash
cd k8s-dev
./setup.sh
```

This script does everything: creates kind cluster, deploys Keycloak + OIDC, builds Java app, generates TLS certs, deploys controller + webhook to `aipub` namespace, creates test AipubUser and namespace, and configures OIDC kubectl context.

After setup completes, jump to [Verify Mutations](#verify-mutations).

## Architecture

```
kubectl --context=oidc create deployment test-workspace --image=nginx
  │
  ▼
K8s API Server (OIDC auth: oidc:testuser, groups: [oidc:aipub-member])
  │
  ├─► MutatingWebhookConfiguration (userrelationship-v2)
  │     │
  │     ▼
  │   project-controller.aipub.svc:8080
  │   POST /api/v1/userrelationship/mutate
  │     │
  │     ├─► UserOwnerReviewHandler
  │     │     → adds annotation: aipub.ten1010.io/owner-reference-v2 = {ownerRef JSON}
  │     │
  │     └─► UserLabelReviewHandler
  │           → adds label: aipub.ten1010.io/username-v2 = testuser
  │           → adds label: aipub.ten1010.io/userid-v2 = test-user-001
  │
  ▼
Deployment created with v2 labels + annotation
  │
  ▼
K8s creates ReplicaSet (as system:serviceaccount)
  │
  ├─► UserLabelReviewHandler (non-member path)
  │     → owner propagation: copies v2 labels from parent Deployment
  │
  ▼
ReplicaSet also gets v2 labels (propagated from Deployment)
```

## Manual Steps (if not using setup.sh)

### 1. Generate Keycloak TLS certificates

```bash
cd k8s-dev
mkdir -p certs/keycloak

# CA
openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout certs/keycloak/ca.key -out certs/keycloak/ca.crt \
  -subj "/CN=keycloak-ca" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment,keyCertSign"

# Server cert (SAN=localhost)
openssl req -newkey rsa:2048 -nodes \
  -keyout certs/keycloak/tls.key -out certs/keycloak/tls.csr \
  -subj "/CN=localhost"

openssl x509 -req -CA certs/keycloak/ca.crt -CAkey certs/keycloak/ca.key \
  -CAcreateserial -in certs/keycloak/tls.csr -out certs/keycloak/tls.crt -days 365 \
  -extfile <(printf "subjectAltName=DNS:localhost\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth")
```

### 2. Create Kind cluster with OIDC

```bash
sed "s|__OIDC_CA_PATH__|$(pwd)/certs/keycloak/ca.crt|g" kind-config.yaml > kind-config-resolved.yaml
kind create cluster --name project-controller-dev --config kind-config-resolved.yaml
```

API server OIDC configuration:
- `--oidc-issuer-url=https://localhost:30443/realms/aipub`
- `--oidc-client-id=k8s`
- `--oidc-username-prefix=oidc:` → Keycloak `testuser` becomes K8s `oidc:testuser`
- `--oidc-groups-prefix=oidc:` → Keycloak `aipub-member` becomes K8s `oidc:aipub-member`

### 3. Deploy Keycloak

```bash
kubectl create namespace keycloak
kubectl -n keycloak create secret generic keycloak-tls \
  --from-file=tls.crt=certs/keycloak/tls.crt \
  --from-file=tls.key=certs/keycloak/tls.key
kubectl -n keycloak create configmap keycloak-realm \
  --from-file=realm-init.json=realm-init.json
kubectl apply -f keycloak/keycloak.yaml
kubectl -n keycloak rollout status deployment/keycloak --timeout=180s
```

After Keycloak is ready, `setup.sh` automatically:
- Creates `testuser` with password `testpass`
- Assigns `aipub-member` realm role
- Retrieves `k8s` client secret for OIDC token requests

### 4. Build and load controller image

```bash
cd ..  # project root
./mvnw -DskipTests clean package
docker build -t project-controller:dev -f - . <<'EOF'
FROM eclipse-temurin:21-jre
COPY target/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
EOF
kind load docker-image project-controller:dev --name project-controller-dev
cd k8s-dev
```

### 5. Generate controller TLS certificates

The TLS cert SAN must match `project-controller.aipub.svc` (service DNS in `aipub` namespace).

```bash
mkdir -p certs/controller

openssl req -config ca.conf -newkey rsa:2048 -x509 -days 3650 \
  -keyout certs/controller/ca.key -out certs/controller/ca.crt -set_serial 0

openssl req -newkey rsa:2048 -config tls.conf \
  -keyout certs/controller/tls.key -out certs/controller/tls.csr

openssl x509 -req -CA certs/controller/ca.crt -CAkey certs/controller/ca.key \
  -CAserial certs/controller/.srl -CAcreateserial \
  -in certs/controller/tls.csr -extfile tls.ext \
  -out certs/controller/tls.crt -days 365

openssl pkcs12 -export -inkey certs/controller/tls.key -in certs/controller/tls.crt \
  -out certs/controller/tls.p12 -passout pass:""

cp certs/controller/tls.p12 tls.p12
```

### 6. Deploy controller to `aipub` namespace

```bash
# Inject caBundle and deploy via kustomize
CA_BUNDLE=$(base64 < certs/controller/ca.crt)
sed -i '' "s|caBundle: <CA_BUNDLE>|caBundle: ${CA_BUNDLE}|g" patches.yaml

kubectl kustomize . --load-restrictor LoadRestrictionsNone | kubectl apply -f -
kubectl -n aipub rollout status deployment/project-controller --timeout=120s
```

### 7. Create test resources

```bash
kubectl apply -f test-resources.yaml
```

Creates:
- `AipubUser` named `testuser` (spec.id: `test-user-001`) — the informer cache source
- Namespace `test-ns`
- `RoleBinding` granting `oidc:testuser` admin in `test-ns`

## Verify Mutations

### Setup OIDC context

`setup.sh` automatically creates the `oidc` kubectl context. Token expires in 5 minutes. Refresh:

```bash
TOKEN=$(curl -sk https://localhost:30443/realms/aipub/protocol/openid-connect/token \
  -d 'client_id=k8s&client_secret=<CLIENT_SECRET>&username=testuser&password=testpass&grant_type=password' \
  | jq -r '.access_token')
kubectl config set-credentials oidc-testuser --token="$TOKEN"
```

### Test 1: Create deployment as OIDC user (aipub-member)

```bash
kubectl --context=oidc create deployment test-workspace --image=nginx
```

### Test 2: Check v2 labels

```bash
kubectl -n test-ns get deployment test-workspace -o jsonpath='{.metadata.labels}' | jq .
```

Expected:

```json
{
  "app": "test-workspace",
  "aipub.ten1010.io/username-v2": "testuser",
  "aipub.ten1010.io/userid-v2": "test-user-001"
}
```

### Test 3: Check v2 owner reference annotation

```bash
kubectl -n test-ns get deployment test-workspace -o jsonpath='{.metadata.annotations}' | jq .
```

Expected:

```json
{
  "aipub.ten1010.io/owner-reference-v2": "{\"apiVersion\":\"project.aipub.ten1010.io/v1alpha1\",\"blockOwnerDeletion\":false,\"controller\":false,\"kind\":\"AipubUser\",\"name\":\"testuser\",\"uid\":\"<uid>\"}"
}
```

### Test 4: Check label propagation to child resources

When the Deployment controller creates a ReplicaSet (as a service account, not OIDC user), the UserLabel handler copies v2 labels from the parent Deployment:

```bash
# ReplicaSet v2 labels
kubectl -n test-ns get replicaset -l app=test-workspace \
  -o jsonpath='{.items[0].metadata.labels}' | jq .

# Pod v2 labels (propagated through ReplicaSet)
kubectl -n test-ns get pods -l app=test-workspace \
  -o jsonpath='{.items[0].metadata.labels}' | jq .
```

### Test 5: Check GVK exception (Commit resources should be skipped)

The `UserOwnerReviewHandler` skips `aipub.ten1010.io/v1alpha1/Commit` and `aipub.ten1010.io/v1/Commit` — no owner reference annotation should be added for these.

### Test 6: Non-member user (system service accounts)

Resources created by service accounts (no `oidc:aipub-member` group) should:
- **Not** get owner reference annotation
- **Get** v2 labels propagated from parent owner (if parent has them)
- **Not** get v2 labels (if parent doesn't have them — allowed without mutation)

### Controller logs

```bash
kubectl -n aipub logs deployment/project-controller -f
```

Look for:
- `Aipub admission review request received`
- `UserLabel handle: user=..., namespace=..., operation=...`
- `UserLabel: direct aipub member, username=..., userid=...`
- `UserLabel: propagated from owner, username=..., userid=...`

## Emergency: Remove v2 webhook

If the v2 webhook causes problems (e.g., CREATE operations fail):

```bash
# Remove v2 webhook immediately
kubectl delete mutatingwebhookconfiguration \
  userrelationship-v2.project-controller.project.aipub.ten1010.io

# Verify removal
kubectl get mutatingwebhookconfiguration | grep v2
```

This does NOT affect the Python admission-controller or the original Java webhooks.

## Cleanup

```bash
./cleanup.sh
```

Or manually:

```bash
kind delete cluster --name project-controller-dev
rm -rf certs tls.p12
sed -i '' 's|caBundle: .*|caBundle: <CA_BUNDLE>|g' patches.yaml
```

## Troubleshooting

**Keycloak not ready:**
```bash
kubectl -n keycloak logs deployment/keycloak
```

**Token request fails:**
```bash
curl -sk https://localhost:30443/realms/aipub/.well-known/openid-configuration | jq .issuer
```

**API server OIDC not working (401 with valid token):**
```bash
docker exec project-controller-dev-control-plane \
  cat /var/log/containers/kube-apiserver-*.log | grep oidc
```

**Controller pod not starting:**
```bash
kubectl -n aipub describe pod -l app=project-controller
kubectl -n aipub logs deployment/project-controller
```

**Webhook not intercepting requests:**
```bash
# Check webhook is registered
kubectl get mutatingwebhookconfiguration | grep v2

# Check webhook config details
kubectl get mutatingwebhookconfiguration \
  userrelationship-v2.project-controller.project.aipub.ten1010.io -o yaml
```

**Webhook rejects all requests (failurePolicy: Fail):**
```bash
# Quick fix: remove webhook
kubectl delete mutatingwebhookconfiguration \
  userrelationship-v2.project-controller.project.aipub.ten1010.io

# Then check controller logs for the error
kubectl -n aipub logs deployment/project-controller --tail=200
```
