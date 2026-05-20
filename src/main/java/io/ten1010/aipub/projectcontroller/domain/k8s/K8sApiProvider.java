package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1Workspace;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1WorkspaceList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubJob;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubJobList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUserList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubVolume;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubVolumeList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ChainJob;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ChainJobList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHubList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroupList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Operation;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1OperationList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSetList;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1SftpServer;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1SftpServerList;
import lombok.Getter;

@Getter
public class K8sApiProvider {

  private final ApiClient apiClient;
  private final GenericKubernetesApi<V1alpha1Project, V1alpha1ProjectList> projectApi;
  private final GenericKubernetesApi<V1alpha1AipubUser, V1alpha1AipubUserList> aipubUserApi;
  private final GenericKubernetesApi<V1alpha1NodeGroup, V1alpha1NodeGroupList> nodeGroupApi;
  private final GenericKubernetesApi<V1alpha1ImageHub, V1alpha1ImageHubList> imageHubApi;
  private final GenericKubernetesApi<V1alpha1ResourceSet, V1alpha1ResourceSetList> resourceSetApi;
  private final GenericKubernetesApi<V1Workspace, V1WorkspaceList> workspaceApi;
  private final GenericKubernetesApi<V1alpha1AipubJob, V1alpha1AipubJobList> aipubJobApi;
  private final GenericKubernetesApi<V1alpha1ChainJob, V1alpha1ChainJobList> chainJobApi;
  private final GenericKubernetesApi<V1alpha1Operation, V1alpha1OperationList> operationApi;
  private final GenericKubernetesApi<V1alpha1AipubVolume, V1alpha1AipubVolumeList> aipubVolumeApi;
  private final GenericKubernetesApi<V1alpha1SftpServer, V1alpha1SftpServerList> sftpServerApi;

  public K8sApiProvider(ApiClient apiClient) {
    this.apiClient = apiClient;
    this.projectApi = createProjectApi(apiClient);
    this.aipubUserApi = createAipubUserApi(apiClient);
    this.nodeGroupApi = createNodeGroupApi(apiClient);
    this.imageHubApi = createImageHubApi(apiClient);
    this.resourceSetApi = createResourceSetApi(apiClient);
    this.workspaceApi = createWorkspaceApi(apiClient);
    this.aipubJobApi = createAipubJobApi(apiClient);
    this.chainJobApi = createChainJobApi(apiClient);
    this.operationApi = createOperationApi(apiClient);
    this.aipubVolumeApi = createAipubVolumeApi(apiClient);
    this.sftpServerApi = createSftpServerApi(apiClient);
  }

  private static GenericKubernetesApi<V1alpha1Project, V1alpha1ProjectList> createProjectApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1Project.class,
        V1alpha1ProjectList.class,
        ProjectApiConstants.PROJECT_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.PROJECT_RESOURCE_PLURAL,
        apiClient);
  }

  private static GenericKubernetesApi<V1alpha1AipubUser, V1alpha1AipubUserList> createAipubUserApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1AipubUser.class,
        V1alpha1AipubUserList.class,
        ProjectApiConstants.PROJECT_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.AIPUB_USER_RESOURCE_PLURAL,
        apiClient);
  }

  private static GenericKubernetesApi<V1alpha1NodeGroup, V1alpha1NodeGroupList> createNodeGroupApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1NodeGroup.class,
        V1alpha1NodeGroupList.class,
        ProjectApiConstants.PROJECT_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.NODE_GROUP_RESOURCE_PLURAL,
        apiClient);
  }

  private static GenericKubernetesApi<V1alpha1ImageHub, V1alpha1ImageHubList> createImageHubApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1ImageHub.class,
        V1alpha1ImageHubList.class,
        ProjectApiConstants.PROJECT_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.IMAGE_HUB_RESOURCE_PLURAL,
        apiClient);
  }

  private static GenericKubernetesApi<V1alpha1ResourceSet, V1alpha1ResourceSetList> createResourceSetApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1ResourceSet.class,
        V1alpha1ResourceSetList.class,
        ProjectApiConstants.AIPUB_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.RESOURCE_SET_RESOURCE_PLURAL,
        apiClient
    );
  }

  private static GenericKubernetesApi<V1Workspace, V1WorkspaceList> createWorkspaceApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1Workspace.class,
        V1WorkspaceList.class,
        ProjectApiConstants.AIPUB_GROUP,
        ProjectApiConstants.VERSION_V1,
        ProjectApiConstants.WORKSPACE_RESOURCE_PLURAL,
        apiClient
    );
  }

  private static GenericKubernetesApi<V1alpha1AipubJob, V1alpha1AipubJobList> createAipubJobApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1AipubJob.class,
        V1alpha1AipubJobList.class,
        ProjectApiConstants.AIPUB_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.AIPUB_JOB_RESOURCE_PLURAL,
        apiClient
    );
  }

  private static GenericKubernetesApi<V1alpha1ChainJob, V1alpha1ChainJobList> createChainJobApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1ChainJob.class,
        V1alpha1ChainJobList.class,
        ProjectApiConstants.AIPUB_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.CHAIN_JOB_RESOURCE_PLURAL,
        apiClient
    );
  }

  private static GenericKubernetesApi<V1alpha1Operation, V1alpha1OperationList> createOperationApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1Operation.class,
        V1alpha1OperationList.class,
        ProjectApiConstants.AIPUB_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.OPERATION_RESOURCE_PLURAL,
        apiClient
    );
  }

  private static GenericKubernetesApi<V1alpha1AipubVolume, V1alpha1AipubVolumeList> createAipubVolumeApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1AipubVolume.class,
        V1alpha1AipubVolumeList.class,
        ProjectApiConstants.AIPUB_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.AIPUB_VOLUME_RESOURCE_PLURAL,
        apiClient
    );
  }

  private static GenericKubernetesApi<V1alpha1SftpServer, V1alpha1SftpServerList> createSftpServerApi(
      ApiClient apiClient) {
    return new GenericKubernetesApi<>(
        V1alpha1SftpServer.class,
        V1alpha1SftpServerList.class,
        ProjectApiConstants.AIPUB_GROUP,
        ProjectApiConstants.VERSION_V1ALPHA1,
        ProjectApiConstants.SFTP_SERVER_RESOURCE_PLURAL,
        apiClient
    );
  }

}
