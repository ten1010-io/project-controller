package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class K8sGroupConstants {

    public static final String SYSTEM_AUTHENTICATED_GROUP_NAME = "system:authenticated";

    public static final String SYSTEM_SERVICEACCOUNTS_GROUP_NAME = "system:serviceaccounts";

    public static final String SYSTEM_MASTERS_GROUP_NAME = "system:masters";

    public static final String AIPUB_ADMIN_GROUP_NAME = "aipub-admin";
    public static final String AIPUB_MEMBER_GROUP_NAME = "aipub-member";

    private K8sGroupConstants() {
    }

}
