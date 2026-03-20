package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class LabelConstants {

  public static final String PROJECT_MANAGED_KEY =
      ProjectApiConstants.PROJECT_GROUP + "/" + "project-managed";
  public static final String ISOLATION_MODE_KEY =
      ProjectApiConstants.PROJECT_GROUP + "/" + "isolation-mode";
  public static final String OBJECT_OWN_USERNAME_KEY =
      ProjectApiConstants.AIPUB_GROUP + "/" + "username";
  public static final String OBJECT_OWN_USERID_KEY =
      ProjectApiConstants.AIPUB_GROUP + "/" + "userid";
  public static final String PROJECT_LABEL_KEY =
      ProjectApiConstants.PROJECT_GROUP + "/" + "project";
  public static final String OBJECT_OWN_USERNAME_V2_KEY =
      OBJECT_OWN_USERNAME_KEY + "-v2";
  public static final String OBJECT_OWN_USERID_V2_KEY =
      OBJECT_OWN_USERID_KEY + "-v2";

  private LabelConstants() {
  }

}
