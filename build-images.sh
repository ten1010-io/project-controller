#!/usr/bin/env bash
script_path=$(dirname "$0")

./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=<HARBOR_REGISTRY>/project-controller:1.7.5
