package io.ten1010.aipub.projectcontroller.domain.k8s;

import java.util.Optional;

public class RoleNameResolver {

    private static final String DELIMITER = ":";

    private static String getProjectRoleString(ProjectRoleEnum projectRoleEnum) {
        return projectRoleEnum.getStr();
    }

    public String resolveRoleName(String projectName, ProjectRoleEnum projectRoleEnum) {
        if (projectName.contains(DELIMITER)) {
            throw new IllegalArgumentException();
        }
        return ProjectApiConstants.GROUP + DELIMITER + getProjectRoleString(projectRoleEnum) + DELIMITER + projectName;
    }

    public Optional<ProjectNameAndRole> resolveProjectName(String roleName) {
        String[] tokens = roleName.split(DELIMITER);
        if (tokens.length != 3) {
            return Optional.empty();
        }
        if (!tokens[0].equals(ProjectApiConstants.GROUP)) {
            return Optional.empty();
        }
        Optional<ProjectRoleEnum> roleEnumOpt = ProjectRoleEnum.getEnum(tokens[1]);
        if (roleEnumOpt.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ProjectNameAndRole(tokens[2], roleEnumOpt.get()));
    }

}
