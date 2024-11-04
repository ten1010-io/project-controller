package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class TaintConstants {

    public static final String PROJECT_MANAGED_KEY = ProjectApiConstants.GROUP + "/" + "project-managed";

    public static final String NO_SCHEDULE_EFFECT = "NoSchedule";
    public static final String NO_EXECUTE_EFFECT = "NoExecute";

    private TaintConstants() {
    }

}
