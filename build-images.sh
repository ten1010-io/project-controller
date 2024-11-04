#!/usr/bin/env bash
script_path=$(dirname "$0")

./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=ten1010io/project-controller:0.1.0-SNAPSHOT
