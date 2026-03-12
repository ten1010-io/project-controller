#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLUSTER_NAME="project-controller-dev"

echo "Deleting kind cluster '${CLUSTER_NAME}'..."
kind delete cluster --name "$CLUSTER_NAME" 2>/dev/null || true

echo "Cleaning up generated files..."
rm -rf "$SCRIPT_DIR/certs"
rm -f "$SCRIPT_DIR/tls.p12"
rm -f "$SCRIPT_DIR/kind-config-resolved.yaml"

# Restore patches.yaml caBundle placeholders
sed 's|caBundle: .*|caBundle: <CA_BUNDLE>|g' \
  "$SCRIPT_DIR/patches.yaml" > "$SCRIPT_DIR/patches-clean.yaml"
cp "$SCRIPT_DIR/patches-clean.yaml" "$SCRIPT_DIR/patches.yaml"
rm "$SCRIPT_DIR/patches-clean.yaml"

echo "Cleanup complete."
