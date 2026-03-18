#!/usr/bin/env bash
set -euo pipefail

if [ -z "${1:-}" ]; then
  echo "Usage: ./image_push.sh <tag>"
  echo "Example: ./image_push.sh 1.5.0"
  exit 1
fi

TAG="$1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Load harbor config
if [ -f "$SCRIPT_DIR/harbor/.env" ]; then
  set -a
  source "$SCRIPT_DIR/harbor/.env"
  set +a
fi

if [ -z "${HARBOR_REGISTRY:-}" ]; then
  echo "HARBOR_REGISTRY not set in harbor/.env"
  exit 1
fi
REGISTRY="${HARBOR_REGISTRY}"
IMAGE="${REGISTRY}/project-controller/project-controller:${TAG}"

echo "=== Login to Harbor ==="
if [ -n "${HARBOR_USERNAME:-}" ] && [ -n "${HARBOR_PASSWORD:-}" ]; then
  echo "${HARBOR_PASSWORD}" | docker login "${REGISTRY}" -u "${HARBOR_USERNAME}" --password-stdin
else
  echo "HARBOR_USERNAME or HARBOR_PASSWORD not set in harbor/.env"
  exit 1
fi

echo ""
echo "=== Build jar ==="
./mvnw -DskipTests clean package -q

echo ""
echo "=== Build image (linux/amd64) ==="
docker buildx build --platform linux/amd64 \
  -t "${IMAGE}" \
  -f Dockerfile . \
  --load

echo ""
echo "=== Push image ==="
docker push "${IMAGE}"

echo ""
echo "Done: ${IMAGE}"
