package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.kubernetes.client.openapi.models.RbacV1SubjectBuilder;

public abstract class RbacSubjectUtils {

    public static final String K8S_RBAC_API_GROUP = "rbac.authorization.k8s.io";
    public static final String USER_KIND = "User";

    private static String buildName(String oidcIssuerUrl, String oidcUsername) {
        return oidcIssuerUrl + "#" + oidcUsername;
    }

    public static RbacV1Subject buildSubject(String oidcIssuerUrl, String oidcUsername) {
        return new RbacV1SubjectBuilder()
                .withApiGroup(K8S_RBAC_API_GROUP)
                .withKind(USER_KIND)
                .withName(buildName(oidcIssuerUrl, oidcUsername))
                .build();
    }

    public static boolean isUserSubject(RbacV1Subject subject) {
        return K8S_RBAC_API_GROUP.equals(subject.getApiGroup()) && USER_KIND.equals(subject.getKind());
    }

}
