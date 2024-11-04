package io.ten1010.aipub.projectcontroller.domain.k8s;

import java.util.Optional;

public class AipubUserRoleNameResolver {

    private static final String DELIMITER = ":";
    private static final String INFIX = "aipub-user";

    public String resolveRoleName(String aipubUserName) {
        if (aipubUserName.contains(DELIMITER)) {
            throw new IllegalArgumentException();
        }
        return ProjectApiConstants.GROUP + DELIMITER + INFIX + DELIMITER + aipubUserName;
    }

    public Optional<String> resolveAipubUserName(String roleName) {
        String[] tokens = roleName.split(DELIMITER);
        if (tokens.length != 3) {
            return Optional.empty();
        }
        if (!tokens[0].equals(ProjectApiConstants.GROUP)) {
            return Optional.empty();
        }
        if (!tokens[1].equals(INFIX)) {
            return Optional.empty();
        }
        return Optional.of(tokens[2]);
    }

}
