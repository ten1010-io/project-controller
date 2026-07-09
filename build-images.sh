#!/usr/bin/env bash
script_path=$(dirname "$0")

./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=registry.ten1010.io:8443/project-controller:1.7.5
