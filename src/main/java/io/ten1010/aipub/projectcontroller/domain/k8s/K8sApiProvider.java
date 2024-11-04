package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.*;
import lombok.Getter;

@Getter
public class K8sApiProvider {

    private static GenericKubernetesApi<V1alpha1Project, V1alpha1ProjectList> createProjectApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1alpha1Project.class,
                V1alpha1ProjectList.class,
                ProjectApiConstants.GROUP,
                ProjectApiConstants.VERSION,
                ProjectApiConstants.PROJECT_RESOURCE_PLURAL,
                apiClient);
    }

    private static GenericKubernetesApi<V1alpha1AipubUser, V1alpha1AipubUserList> createAipubUserApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1alpha1AipubUser.class,
                V1alpha1AipubUserList.class,
                ProjectApiConstants.GROUP,
                ProjectApiConstants.VERSION,
                ProjectApiConstants.AIPUB_USER_RESOURCE_PLURAL,
                apiClient);
    }

    private static GenericKubernetesApi<V1alpha1NodeGroup, V1alpha1NodeGroupList> createNodeGroupApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1alpha1NodeGroup.class,
                V1alpha1NodeGroupList.class,
                ProjectApiConstants.GROUP,
                ProjectApiConstants.VERSION,
                ProjectApiConstants.NODE_GROUP_RESOURCE_PLURAL,
                apiClient);
    }

    private static GenericKubernetesApi<V1alpha1ImageNamespace, V1alpha1ImageNamespaceList> createImageNamespaceApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1alpha1ImageNamespace.class,
                V1alpha1ImageNamespaceList.class,
                ProjectApiConstants.GROUP,
                ProjectApiConstants.VERSION,
                ProjectApiConstants.IMAGE_NAMESPACE_RESOURCE_PLURAL,
                apiClient);
    }

    private final ApiClient apiClient;

    private final GenericKubernetesApi<V1alpha1Project, V1alpha1ProjectList> projectApi;
    private final GenericKubernetesApi<V1alpha1AipubUser, V1alpha1AipubUserList> aipubUserApi;
    private final GenericKubernetesApi<V1alpha1NodeGroup, V1alpha1NodeGroupList> nodeGroupApi;
    private final GenericKubernetesApi<V1alpha1ImageNamespace, V1alpha1ImageNamespaceList> imageNamespaceApi;

    public K8sApiProvider(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.projectApi = createProjectApi(apiClient);
        this.aipubUserApi = createAipubUserApi(apiClient);
        this.nodeGroupApi = createNodeGroupApi(apiClient);
        this.imageNamespaceApi = createImageNamespaceApi(apiClient);
    }

}
