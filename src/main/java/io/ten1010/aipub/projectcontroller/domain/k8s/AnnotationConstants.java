package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class AnnotationConstants {

    public static final String BOUND_PROJECTS_KEY = ProjectApiConstants.PROJECT_GROUP + "/" + "bound-projects";
    public static final String BOUND_NODE_GROUPS_KEY = ProjectApiConstants.PROJECT_GROUP + "/" + "bound-node-groups";
    public static final String BOUND_NODES_KEY = ProjectApiConstants.PROJECT_GROUP + "/" + "bound-nodes";

    private AnnotationConstants() {
    }

}
