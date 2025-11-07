package io.ten1010.aipub.projectcontroller.domain.k8s;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public record ProjectNameAndRole(String projectName, ProjectRoleEnum projectRoleEnum) {

}
