package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class FinalizersConstants {

    public static final String PROJECT_FINALIZER = "finalizer" + "." + ProjectApiConstants.GROUP;
    public static final String FOREGROUND_DELETION = "foregroundDeletion";

    private FinalizersConstants() {
    }

}
