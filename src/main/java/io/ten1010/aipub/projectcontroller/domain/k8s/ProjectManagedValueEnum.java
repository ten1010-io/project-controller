package io.ten1010.aipub.projectcontroller.domain.k8s;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Getter
public enum ProjectManagedValueEnum {

    TRUE("true"), FALSE("false");

    private static final Map<String, ProjectManagedValueEnum> STR_TO_ENUM;

    static {
        STR_TO_ENUM = new HashMap<>();
        for (ProjectManagedValueEnum e : ProjectManagedValueEnum.values()) {
            STR_TO_ENUM.put(e.getStr(), e);
        }
    }

    public static Optional<ProjectManagedValueEnum> getEnum(String str) {
        ProjectManagedValueEnum parsed = STR_TO_ENUM.get(str.toLowerCase());
        return Optional.ofNullable(parsed);
    }

    private final String str;

}
