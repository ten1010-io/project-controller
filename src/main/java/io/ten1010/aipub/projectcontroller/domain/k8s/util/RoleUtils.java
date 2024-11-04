package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.openapi.models.*;

import java.util.List;

public abstract class RoleUtils {

    public static List<V1PolicyRule> getRules(V1ClusterRole object) {
        if (object.getRules() == null) {
            return List.of();
        }
        return object.getRules();
    }

    public static List<V1PolicyRule> getRules(V1Role object) {
        if (object.getRules() == null) {
            return List.of();
        }
        return object.getRules();
    }

    public static List<RbacV1Subject> getSubjects(V1ClusterRoleBinding object) {
        if (object.getSubjects() == null) {
            return List.of();
        }
        return object.getSubjects();
    }

    public static List<RbacV1Subject> getSubjects(V1RoleBinding object) {
        if (object.getSubjects() == null) {
            return List.of();
        }
        return object.getSubjects();
    }

}
