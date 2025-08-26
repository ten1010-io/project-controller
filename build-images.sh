#!/usr/bin/env bash
script_path=$(dirname "$0")

./mvnw clean compile jib:dockerBuild -Dimage=ten1010io/project-controller:0.1.0-SNAPSHOT
