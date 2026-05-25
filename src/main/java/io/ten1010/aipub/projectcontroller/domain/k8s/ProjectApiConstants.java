package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class ProjectApiConstants {

  public static final String PROJECT_GROUP = "project.aipub.ten1010.io";
  public static final String AIPUB_GROUP = "aipub.ten1010.io";
  public static final String COASTER_GROUP = "coaster.ten1010.io";
  public static final String VERSION_V1ALPHA1 = "v1alpha1";
  public static final String VERSION_V1 = "v1";
  public static final String PROJECT_API_VERSION = PROJECT_GROUP + "/" + VERSION_V1ALPHA1;
  public static final String AIPUB_API_VERSION = AIPUB_GROUP + "/" + VERSION_V1ALPHA1;
  public static final String COASTER_API_VERSION = COASTER_GROUP + "/" + VERSION_V1ALPHA1;

  public static final String PROJECT_RESOURCE_KIND = "Project";
  public static final String PROJECT_RESOURCE_PLURAL = "projects";

  public static final String NODE_GROUP_RESOURCE_KIND = "NodeGroup";
  public static final String NODE_GROUP_RESOURCE_PLURAL = "nodegroups";

  public static final String AIPUB_USER_RESOURCE_KIND = "AipubUser";
  public static final String AIPUB_USER_RESOURCE_PLURAL = "aipubusers";

  public static final String IMAGE_HUB_RESOURCE_KIND = "ImageHub";
  public static final String IMAGE_HUB_RESOURCE_PLURAL = "imagehubs";

  public static final String IMAGE_REVIEW_RESOURCE_KIND = "ImageReview";
  public static final String IMAGE_REVIEW_RESOURCE_PLURAL = "imagereviews";

  public static final String RESOURCE_SET_RESOURCE_KIND = "ResourceSet";
  public static final String RESOURCE_SET_RESOURCE_PLURAL = "resourcesets";

  public static final String NODE_RESOURCE_RESOURCE_KIND = "NodeResources";
  public static final String NODE_RESOURCE_RESOURCE_PLURAL = "noderesources";

  public static final String TCP_PORT_VALIDATION_RESOURCE_KIND = "TCPPortValidation";
  public static final String TCP_PORT_VALIDATION_RESOURCE_PLURAL = "tcpportvalidations";

  public static final String HOST_PATH_VALIDATION_RESOURCE_KIND = "HostPathValidation";
  public static final String HOST_PATH_VALIDATION_RESOURCE_PLURAL = "hostpathvalidations";

  public static final String USER_WORKSPACE_RECLAIM_RESOURCE_KIND = "UserWorkspaceReclaim";
  public static final String USER_WORKSPACE_RECLAIM_RESOURCE_PLURAL = "userworkspacereclaims";

  public static final String USER_RESOURCE_QUOTA_RESOURCE_KIND = "UserResourceQuota";
  public static final String USER_RESOURCE_QUOTA_RESOURCE_PLURAL = "userresourcequotas";

  public static final String GPU_QUOTA_RESOURCE_KIND = "GpuQuota";
  public static final String GPU_QUOTA_RESOURCE_PLURAL = "gpuquotas";

  public static final String GPU_CONFIG_RESOURCE_KIND = "GPUConfig";
  public static final String GPU_CONFIG_RESOURCE_PLURAL = "gpuconfigs";

  public static final String NODE_GPU_USAGE_RESOURCE_KIND = "NodeGPUUsage";
  public static final String NODE_GPU_USAGE_RESOURCE_PLURAL = "nodegpuusages";

  public static final String WORKSPACE_RESOURCE_KIND = "Workspace";
  public static final String WORKSPACE_RESOURCE_PLURAL = "workspaces";

  public static final String AIPUB_JOB_RESOURCE_KIND = "AIPubJob";
  public static final String AIPUB_JOB_RESOURCE_PLURAL = "aipubjobs";

  public static final String CHAIN_JOB_RESOURCE_KIND = "ChainJob";
  public static final String CHAIN_JOB_RESOURCE_PLURAL = "chainjobs";

  public static final String OPERATION_RESOURCE_KIND = "Operation";
  public static final String OPERATION_RESOURCE_PLURAL = "operations";

  public static final String AIPUB_VOLUME_RESOURCE_KIND = "AIPubVolume";
  public static final String AIPUB_VOLUME_RESOURCE_PLURAL = "aipubvolumes";

  public static final String SFTP_SERVER_RESOURCE_KIND = "SFTPServer";
  public static final String SFTP_SERVER_RESOURCE_PLURAL = "sftpservers";

  private ProjectApiConstants() {
  }

}
