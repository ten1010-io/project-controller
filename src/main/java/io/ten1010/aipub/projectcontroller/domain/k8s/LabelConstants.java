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
  public static final String ALLOWLISTED_KEY =
      ProjectApiConstants.PROJECT_GROUP + "/" + "allowlisted";
  public static final String WORKLOAD_NAME_KEY =
      ProjectApiConstants.AIPUB_GROUP + "/" + "workload-name";
  public static final String WORKLOAD_KIND_KEY =
      ProjectApiConstants.AIPUB_GROUP + "/" + "workload-kind";
  public static final String IMAGE_REGISTRY_ROBOT_ID_KEY =
      ProjectApiConstants.PROJECT_GROUP + "/" + "image-registry-robot-id";
  /**
   * ClusterVolume 컨트롤러가 자기가 만든 자식(복제/앵커 PVC·PV)에 붙이는 라벨. 값은 부모
   * ClusterVolume 의 이름이다. 편입된 원본 PVC 에는 이 라벨 대신 {@code claimed-by} 가 붙으며
   * 그것은 남의 오브젝트라 소유자 라벨 전파 대상이 아니다.
   */
  public static final String CLUSTER_VOLUME_OWNER_KEY =
      ProjectApiConstants.CLUSTER_VOLUME_RESOURCE_PLURAL + "." + ProjectApiConstants.AIPUB_GROUP
          + "/" + "owner";

  private LabelConstants() {
  }

}
