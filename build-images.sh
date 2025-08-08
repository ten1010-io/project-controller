#!/usr/bin/env bash
script_path=$(dirname "$0")

./mvnw clean

./mvnw spring-boot:build-image
#  -Dspring-boot.build-image.builder=amazoncorretto:17-alpine \
#  -Dspring-boot.build-image.runImage=amazoncorretto:17-alpine \
#  -Dspring-boot.build-image.imagePlatform=linux/arm64 \
#  -Dspring-boot.build-image.cleanCache=true
#  -Dspring-boot.build-image.imageName=ten1010io/project-controller:0.1.0-SNAPSHOT
