package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class LabelConstants {

    public static final String PROJECT_MANAGED_KEY = ProjectApiConstants.PROJECT_GROUP + "/" + "project-managed";
    public static final String ISOLATION_MODE_KEY = ProjectApiConstants.PROJECT_GROUP + "/" + "isolation-mode";

    private LabelConstants() {
    }

}
