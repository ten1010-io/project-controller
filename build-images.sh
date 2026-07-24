#!/usr/bin/env bash
script_path=$(dirname "$0")

./gradlew bootBuildImage --imageName=<HARBOR_REGISTRY>/project-controller:1.7.5
