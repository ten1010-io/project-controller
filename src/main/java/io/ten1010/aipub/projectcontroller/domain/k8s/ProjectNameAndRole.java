package io.ten1010.aipub.projectcontroller.domain.k8s;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ProjectNameAndRole {

    private final String projectName;
    private final ProjectRoleEnum projectRoleEnum;

}
