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
  /**
   * 노드가 어떤 프로젝트에 바인딩됐는지 표시하는 라벨 키의 접두. 뒤에 프로젝트 이름이 붙고 값은 빈
   * 문자열이라 키 자체로만 의미를 갖는다 — Coaster 가 Cueue 의 nodeSelector 로 쓴다.
   *
   * <p>이 접두로 시작하는 키를 NodeGroup 의 {@code spec.nodeSelector} 에 쓰지 않는다. 노드 매칭이
   * 부분집합 판정이라, 프로젝트 A 의 키를 selector 로 가진 NodeGroup 을 프로젝트 B 에 바인딩하면
   * A 의 노드가 B 에도 전이 바인딩돼 테넌트 경계가 넓어진다.
   */
  public static final String PROJECT_NAME_KEY_PREFIX =
      "project-name." + ProjectApiConstants.AIPUB_GROUP + "/";
  public static final String CUEUE_PROVISIONING_ENABLED_KEY =
      "cueue." + ProjectApiConstants.COASTER_GROUP + "/" + "provisioning-enabled";
  public static final String CUEUE_PROVISIONING_ENABLED_VALUE = "true";

  private LabelConstants() {
  }

}
