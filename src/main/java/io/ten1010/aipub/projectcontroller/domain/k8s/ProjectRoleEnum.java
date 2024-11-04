package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Getter
public enum ProjectRoleEnum {

    PROJECT_ADMIN("project-admin"), PROJECT_DEVELOPER("project-developer");

    private static final Map<String, ProjectRoleEnum> STR_TO_ENUM;

    static {
        STR_TO_ENUM = new HashMap<>();
        for (ProjectRoleEnum e : ProjectRoleEnum.values()) {
            STR_TO_ENUM.put(e.getStr(), e);
        }
    }

    public static Optional<ProjectRoleEnum> getEnum(String str) {
        ProjectRoleEnum parsed = STR_TO_ENUM.get(str.toLowerCase());
        return Optional.ofNullable(parsed);
    }

    public static boolean memberHasRole(V1alpha1ProjectMember member, ProjectRoleEnum projRoleEnum) {
        if (member.getRole() == null) {
            return false;
        }
        Optional<ProjectRoleEnum> enumOpt = ProjectRoleEnum.getEnum(member.getRole());
        if (enumOpt.isEmpty()) {
            return false;
        }

        return enumOpt.get().equals(projRoleEnum);
    }

    private final String str;

}
