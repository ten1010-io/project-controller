#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CLUSTER_NAME="project-controller-dev"
IMAGE_NAME="project-controller"
IMAGE_TAG="dev"
KEYCLOAK_CERT_DIR="$SCRIPT_DIR/certs/keycloak"
CONTROLLER_CERT_DIR="$SCRIPT_DIR/certs/controller"

# ============================================================
# Step 1: Generate Keycloak TLS certificates
# ============================================================
echo "=== Step 1: Generate Keycloak TLS certificates ==="
if [ -f "$KEYCLOAK_CERT_DIR/tls.crt" ]; then
  echo "Keycloak certs already exist, skipping"
else
  mkdir -p "$KEYCLOAK_CERT_DIR"

  # CA
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -keyout "$KEYCLOAK_CERT_DIR/ca.key" \
    -out "$KEYCLOAK_CERT_DIR/ca.crt" \
    -subj "/CN=keycloak-ca" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,digitalSignature,keyEncipherment,keyCertSign" 2>/dev/null

  # Server cert
  openssl req -newkey rsa:2048 -nodes \
    -keyout "$KEYCLOAK_CERT_DIR/tls.key" \
    -out "$KEYCLOAK_CERT_DIR/tls.csr" \
    -subj "/CN=localhost" 2>/dev/null

  openssl x509 -req -CA "$KEYCLOAK_CERT_DIR/ca.crt" -CAkey "$KEYCLOAK_CERT_DIR/ca.key" \
    -CAcreateserial -in "$KEYCLOAK_CERT_DIR/tls.csr" \
    -out "$KEYCLOAK_CERT_DIR/tls.crt" -days 365 \
    -extfile <(printf "subjectAltName=DNS:localhost\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth") 2>/dev/null

  echo "Keycloak certs generated in $KEYCLOAK_CERT_DIR"
fi

# ============================================================
# Step 2: Create Kind cluster with OIDC config
# ============================================================
echo ""
echo "=== Step 2: Create Kind cluster ==="
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  echo "Cluster '${CLUSTER_NAME}' already exists, skipping"
else
  # Generate kind config with absolute CA cert path
  sed "s|__OIDC_CA_PATH__|${KEYCLOAK_CERT_DIR}/ca.crt|g" \
    "$SCRIPT_DIR/kind-config.yaml" > "$SCRIPT_DIR/kind-config-resolved.yaml"

  kind create cluster --name "$CLUSTER_NAME" --config "$SCRIPT_DIR/kind-config-resolved.yaml"
  rm "$SCRIPT_DIR/kind-config-resolved.yaml"
fi
kubectl cluster-info --context "kind-${CLUSTER_NAME}"

# ============================================================
# Step 3: Deploy Keycloak
# ============================================================
echo ""
echo "=== Step 3: Deploy Keycloak ==="
# Create namespace
kubectl apply -f "$SCRIPT_DIR/keycloak/keycloak.yaml" --dry-run=client -o yaml | kubectl apply -f - 2>/dev/null || true

# Create TLS secret for Keycloak
kubectl -n keycloak create secret generic keycloak-tls \
  --from-file=tls.crt="$KEYCLOAK_CERT_DIR/tls.crt" \
  --from-file=tls.key="$KEYCLOAK_CERT_DIR/tls.key" \
  --dry-run=client -o yaml | kubectl apply -f -

# Create realm ConfigMap
kubectl -n keycloak create configmap keycloak-realm \
  --from-file=realm-init.json="$SCRIPT_DIR/realm-init.json" \
  --dry-run=client -o yaml | kubectl apply -f -

# Deploy Keycloak
kubectl apply -f "$SCRIPT_DIR/keycloak/keycloak.yaml"

echo "Waiting for Keycloak to be ready..."
kubectl -n keycloak rollout status deployment/keycloak --timeout=180s

# ============================================================
# Step 3b: Setup Keycloak testuser and retrieve client secret
# ============================================================
echo ""
echo "=== Step 3b: Setup Keycloak testuser ==="
KEYCLOAK_URL="https://localhost:30443"
KC_CURL="curl --cacert $KEYCLOAK_CERT_DIR/ca.crt -sf"

# Wait for Keycloak OIDC endpoint
echo "Waiting for Keycloak OIDC endpoint..."
until $KC_CURL "$KEYCLOAK_URL/realms/aipub" > /dev/null 2>&1; do
  echo "  Not ready yet, retrying in 3s..."
  sleep 3
done

# Get admin token
ADMIN_TOKEN=$($KC_CURL \
  -d 'client_id=admin-cli&username=admin&password=admin&grant_type=password' \
  "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "ERROR: Failed to get admin token" >&2
  exit 1
fi

# Create testuser (ignore 409 if already exists)
HTTP_CODE=$($KC_CURL -o /dev/null -w "%{http_code}" \
  -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","firstName":"Test","lastName":"User","enabled":true,"email":"testuser@example.com","emailVerified":true}' \
  "$KEYCLOAK_URL/admin/realms/aipub/users" 2>/dev/null) || HTTP_CODE="000"
case $HTTP_CODE in
  201) echo "  testuser created" ;;
  409) echo "  testuser already exists" ;;
  *)   echo "  WARNING: testuser creation returned HTTP $HTTP_CODE" ;;
esac

# Get testuser ID
TESTUSER_ID=$($KC_CURL \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KEYCLOAK_URL/admin/realms/aipub/users?username=testuser" \
  | jq -r '.[0].id')

# Set testuser password
$KC_CURL -X PUT \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"password","value":"testpass","temporary":false}' \
  "$KEYCLOAK_URL/admin/realms/aipub/users/$TESTUSER_ID/reset-password"
echo "  testuser password set"

# Get aipub-member role
ROLE_JSON=$($KC_CURL \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KEYCLOAK_URL/admin/realms/aipub/roles/aipub-member")
ROLE_ID=$(echo "$ROLE_JSON" | jq -r '.id')
ROLE_NAME=$(echo "$ROLE_JSON" | jq -r '.name')

# Assign aipub-member role to testuser
$KC_CURL -o /dev/null \
  -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "[{\"id\":\"$ROLE_ID\",\"name\":\"$ROLE_NAME\"}]" \
  "$KEYCLOAK_URL/admin/realms/aipub/users/$TESTUSER_ID/role-mappings/realm" 2>/dev/null || true
echo "  aipub-member role assigned to testuser"

# Get k8s client UUID and regenerate secret
CLIENT_UUID=$($KC_CURL \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KEYCLOAK_URL/admin/realms/aipub/clients?clientId=k8s" \
  | jq -r '.[0].id')
CLIENT_SECRET=$($KC_CURL \
  -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KEYCLOAK_URL/admin/realms/aipub/clients/$CLIENT_UUID/client-secret" \
  | jq -r '.value')
echo "  k8s client secret: $CLIENT_SECRET"

# Add audience mapper to k8s client (required for K8s OIDC validation)
$KC_CURL -o /dev/null \
  -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"audience-k8s","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper","config":{"included.client.audience":"k8s","id.token.claim":"false","access.token.claim":"true","introspection.token.claim":"true"}}' \
  "$KEYCLOAK_URL/admin/realms/aipub/clients/$CLIENT_UUID/protocol-mappers/models" 2>/dev/null || true
echo "  audience mapper added to k8s client"

# ============================================================
# Step 4: Build Java application
# ============================================================
echo ""
echo "=== Step 4: Build Java application ==="
cd "$PROJECT_ROOT"
./mvnw -DskipTests clean package -q
cd "$SCRIPT_DIR"

# ============================================================
# Step 5: Build Docker image and load into Kind
# ============================================================
echo ""
echo "=== Step 5: Build Docker image ==="
docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" -f - "$PROJECT_ROOT" <<'DOCKERFILE'
FROM eclipse-temurin:21-jre
COPY target/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
DOCKERFILE

kind load docker-image "${IMAGE_NAME}:${IMAGE_TAG}" --name "$CLUSTER_NAME"

# ============================================================
# Step 6: Generate controller TLS certificates
# ============================================================
echo ""
echo "=== Step 6: Generate controller TLS certificates ==="
if [ -f "$CONTROLLER_CERT_DIR/tls.p12" ]; then
  echo "Controller certs already exist, skipping"
else
  mkdir -p "$CONTROLLER_CERT_DIR"

  # CA
  openssl req -config "$SCRIPT_DIR/ca.conf" -newkey rsa:2048 -x509 -days 3650 \
    -keyout "$CONTROLLER_CERT_DIR/ca.key" -out "$CONTROLLER_CERT_DIR/ca.crt" -set_serial 0 2>/dev/null

  # TLS cert
  openssl req -newkey rsa:2048 -config "$SCRIPT_DIR/tls.conf" \
    -keyout "$CONTROLLER_CERT_DIR/tls.key" -out "$CONTROLLER_CERT_DIR/tls.csr" 2>/dev/null
  openssl x509 -req -CA "$CONTROLLER_CERT_DIR/ca.crt" -CAkey "$CONTROLLER_CERT_DIR/ca.key" \
    -CAserial "$CONTROLLER_CERT_DIR/.srl" -CAcreateserial \
    -in "$CONTROLLER_CERT_DIR/tls.csr" -extfile "$SCRIPT_DIR/tls.ext" \
    -out "$CONTROLLER_CERT_DIR/tls.crt" -days 365 2>/dev/null
  openssl pkcs12 -export -inkey "$CONTROLLER_CERT_DIR/tls.key" -in "$CONTROLLER_CERT_DIR/tls.crt" \
    -out "$CONTROLLER_CERT_DIR/tls.p12" -passout pass:""

  echo "Controller certs generated in $CONTROLLER_CERT_DIR"
fi

# ============================================================
# Step 7: Inject caBundle and deploy controller
# ============================================================
echo ""
echo "=== Step 7: Deploy controller ==="
CA_BUNDLE=$(base64 < "$CONTROLLER_CERT_DIR/ca.crt")
sed "s|caBundle: <CA_BUNDLE>|caBundle: ${CA_BUNDLE}|g" \
  "$SCRIPT_DIR/patches.yaml" > "$SCRIPT_DIR/patches-resolved.yaml"
cp "$SCRIPT_DIR/patches-resolved.yaml" "$SCRIPT_DIR/patches.yaml"
rm "$SCRIPT_DIR/patches-resolved.yaml"

cp "$CONTROLLER_CERT_DIR/tls.p12" "$SCRIPT_DIR/tls.p12"

kubectl kustomize "$SCRIPT_DIR" --load-restrictor LoadRestrictionsNone | kubectl apply -f -

echo "Waiting for controller to be ready..."
kubectl -n project-controller rollout status deployment/project-controller --timeout=120s

# ============================================================
# Step 8: Apply test resources
# ============================================================
echo ""
echo "=== Step 8: Apply test resources ==="
kubectl apply -f "$SCRIPT_DIR/test-resources.yaml"

# ============================================================
# Done
# ============================================================
echo ""
echo "=== Setup complete! ==="
echo ""
echo "k8s client secret: $CLIENT_SECRET"
echo ""

# Setup OIDC context (cert-free, token only)
OIDC_TOKEN=$(curl --cacert "$KEYCLOAK_CERT_DIR/ca.crt" -sf \
  "https://localhost:30443/realms/aipub/protocol/openid-connect/token" \
  -d "client_id=k8s&client_secret=${CLIENT_SECRET}&username=testuser&password=testpass&grant_type=password" \
  | jq -r '.access_token')
kubectl config set-credentials oidc-testuser --token="$OIDC_TOKEN" >/dev/null 2>&1
kubectl config set-context oidc --cluster="kind-${CLUSTER_NAME}" --user=oidc-testuser --namespace=test-ns >/dev/null 2>&1
echo "OIDC context 'oidc' created (user: oidc-testuser, namespace: test-ns)"
echo ""
echo "Test OIDC user:"
echo "  kubectl --context=oidc auth whoami"
echo ""
echo "Refresh OIDC token (5분 만료):"
echo "  TOKEN=\$(curl -sk https://localhost:30443/realms/aipub/protocol/openid-connect/token \\"
echo "    -d 'client_id=k8s&client_secret=${CLIENT_SECRET}&username=testuser&password=testpass&grant_type=password' \\"
echo "    | jq -r '.access_token') && kubectl config set-credentials oidc-testuser --token=\"\$TOKEN\""
echo ""
echo "Create a workspace deployment (as OIDC user):"
echo "  kubectl --context=oidc create deployment test-workspace --image=nginx"
echo ""
echo "Verify ownerReference:"
echo "  kubectl -n test-ns get deployment test-workspace -o jsonpath='{.metadata.ownerReferences}' | jq ."
echo ""
echo "Verify labels:"
echo "  kubectl -n test-ns get deployment test-workspace -o jsonpath='{.metadata.labels}' | jq ."
echo ""
echo "Controller logs:"
echo "  kubectl -n project-controller logs deployment/project-controller -f"
