># Local Kind Test - UserOwner & UserLabel Mutation

Test ownerReference and label (`aipub.ten1010.io/username`, `aipub.ten1010.io/userid`) injection on resource CREATE by an OIDC-authenticated aipub-member user.

## Prerequisites

- [kind](https://kind.sigs.k8s.io/)
- [Docker](https://docs.docker.com/get-docker/)
- kubectl, jq, openssl
- Java 21 + Maven (or `./mvnw`)

## Quick Start

```bash
cd k8s-dev
./setup.sh
```

Then jump to [Verify Mutations](#verify-mutations).

## Manual Steps

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

The API server is configured to trust Keycloak as an OIDC provider.

```bash
# Generate kind config with absolute cert path
sed "s|__OIDC_CA_PATH__|$(pwd)/certs/keycloak/ca.crt|g" kind-config.yaml > kind-config-resolved.yaml

kind create cluster --name project-controller-dev --config kind-config-resolved.yaml
```

Key OIDC flags set on the API server:
- `--oidc-issuer-url=https://localhost:30443/realms/aipub`
- `--oidc-client-id=k8s`
- `--oidc-username-prefix=oidc:` (Keycloak username `testuser` becomes `oidc:testuser` in K8s)
- `--oidc-groups-prefix=oidc:` (Keycloak group `aipub-member` becomes `oidc:aipub-member` in K8s)

### 3. Deploy Keycloak in Kind

```bash
# Create TLS secret
kubectl create namespace keycloak
kubectl -n keycloak create secret generic keycloak-tls \
  --from-file=tls.crt=certs/keycloak/tls.crt \
  --from-file=tls.key=certs/keycloak/tls.key

# Create realm config
kubectl -n keycloak create configmap keycloak-realm \
  --from-file=realm-init.json=realm-init.json

# Deploy
kubectl apply -f keycloak/keycloak.yaml

# Wait for ready
kubectl -n keycloak rollout status deployment/keycloak --timeout=180s
```

Keycloak is exposed via NodePort 30443 on localhost. The realm `aipub` is auto-imported with:
- Client: `k8s` (confidential, direct access grants, service account enabled)
- Realm roles: `aipub-member`, `aipub-admin`
- User: `testuser` is created via Admin API after Keycloak starts (see setup.sh Step 3b)

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

### 6. Deploy controller

```bash
# Inject caBundle into patches.yaml
CA_BUNDLE=$(base64 < certs/controller/ca.crt)
sed -i '' "s|caBundle: <CA_BUNDLE>|caBundle: ${CA_BUNDLE}|g" patches.yaml

# Deploy
kubectl apply -k .
kubectl -n project-controller rollout status deployment/project-controller --timeout=120s
```

### 7. Create test resources

```bash
kubectl apply -f test-resources.yaml
```

This creates:
- `AipubUser` named `testuser` (spec.id: `test-user-001`)
- Namespace `test-ns`
- `RoleBinding` granting `oidc:testuser` admin in `test-ns`

## Verify Mutations

### Setup OIDC context

`setup.sh`가 자동으로 `oidc` context를 생성합니다. 토큰 만료(5분) 시 갱신:

```bash
TOKEN=$(curl -sk https://localhost:30443/realms/aipub/protocol/openid-connect/token \
  -d 'client_id=k8s&client_secret=<CLIENT_SECRET>&username=testuser&password=testpass&grant_type=password' \
  | jq -r '.access_token')
kubectl config set-credentials oidc-testuser --token="$TOKEN"
```

Verify token contents (optional):

```bash
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | awk '{while(length%4)$0=$0"=";print}' | base64 -d 2>/dev/null | jq .
```

You should see `"preferred_username": "testuser"` and `"groups": ["aipub-member", ...]` (realm roles mapped via `oidc-usermodel-realm-role-mapper`).

> **Note**: `--token` 플래그는 Kind kubeconfig의 client-certificate 인증에 의해 무시됩니다. 반드시 `--context=oidc`를 사용하세요.

### Create a workspace

```bash
kubectl --context=oidc create deployment test-workspace --image=nginx
```

### Check ownerReference

```bash
kubectl -n test-ns get deployment test-workspace -o jsonpath='{.metadata.ownerReferences}' | jq .
```

Expected: ownerReference pointing to AipubUser `testuser`:

```json
[
  {
    "apiVersion": "project.aipub.ten1010.io/v1alpha1",
    "kind": "AipubUser",
    "name": "testuser",
    "uid": "<uid>",
    "controller": false,
    "blockOwnerDeletion": false
  }
]
```

### Check labels

```bash
kubectl -n test-ns get deployment test-workspace -o jsonpath='{.metadata.labels}' | jq .
```

Expected labels include:

```json
{
  "aipub.ten1010.io/username": "testuser",
  "aipub.ten1010.io/userid": "test-user-001"
}
```

### Check child resource label propagation

When the Deployment controller creates a ReplicaSet (as a service account, not an OIDC user), the UserLabel handler copies labels from the owner Deployment:

```bash
# Check ReplicaSet labels
kubectl -n test-ns get replicaset -l app=test-workspace -o jsonpath='{.items[0].metadata.labels}' | jq .

# Check Pod labels (propagated through ReplicaSet)
kubectl -n test-ns get pods -l app=test-workspace -o jsonpath='{.items[0].metadata.labels}' | jq .
```

### Controller logs

```bash
kubectl -n project-controller logs deployment/project-controller -f
```

Look for `Aipub admission review request received` log entries.

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
kubectl -n keycloak describe pod -l app=keycloak
```

**Token request fails:**
```bash
# Check Keycloak is reachable
curl -sk https://localhost:30443/realms/aipub/.well-known/openid-configuration | jq .issuer
```

**API server OIDC not working (401 even with valid token):**
```bash
# Check API server logs for OIDC errors
docker exec project-controller-dev-control-plane cat /var/log/containers/kube-apiserver-*.log | grep oidc
```

**Controller pod not starting:**
```bash
kubectl -n project-controller describe pod -l app=project-controller
kubectl -n project-controller logs deployment/project-controller
```
