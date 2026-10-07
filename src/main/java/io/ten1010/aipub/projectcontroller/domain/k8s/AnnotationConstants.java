package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class AnnotationConstants {

  public static final String BOUND_PROJECTS_KEY =
      ProjectApiConstants.PROJECT_GROUP + "/" + "bound-projects";
  public static final String BOUND_NODE_GROUPS_KEY =
      ProjectApiConstants.PROJECT_GROUP + "/" + "bound-node-groups";
  public static final String BOUND_NODES_KEY =
      ProjectApiConstants.PROJECT_GROUP + "/" + "bound-nodes";
  /** Coaster 가 이 값을 그대로 Cueue 의 spec.nodeSelector 로 동기화한다. */
  public static final String CUEUE_NODE_SELECTOR_KEY =
      "cueue." + ProjectApiConstants.COASTER_GROUP + "/" + "node-selector";

  private AnnotationConstants() {
  }

}
