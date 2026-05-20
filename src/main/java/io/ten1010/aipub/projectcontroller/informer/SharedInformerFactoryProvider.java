package io.ten1010.aipub.projectcontroller.informer;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ClusterRoleBindingList;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.kubernetes.client.openapi.models.V1ClusterRoleList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ResourceQuota;
import io.kubernetes.client.openapi.models.V1ResourceQuotaList;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleBindingList;
import io.kubernetes.client.openapi.models.V1RoleList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.CallGeneratorParams;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1Workspace;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubJob;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubVolume;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ChainJob;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Operation;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1SftpServer;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.LabelUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.NodeGroupUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ProjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ResourceSetUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SharedInformerFactoryProvider {

  private static final long DEFAULT_RESYNC_PERIOD = 0;

  private final KeyResolver keyResolver;
  private final K8sApiProvider k8sApiProvider;
  private final List<InformerRegistrar> registrars;

  public SharedInformerFactoryProvider(K8sApiProvider k8sApiProvider,
      List<InformerRegistrar> registrars) {
    this.keyResolver = new KeyResolver();
    this.k8sApiProvider = k8sApiProvider;
    this.registrars = registrars;
  }

  public SharedInformerFactory createSharedInformerFactory() {
    SharedInformerFactory informerFactory = new SharedInformerFactory(
        this.k8sApiProvider.getApiClient());
    registerProjectInformer(informerFactory);
    registerAipubUserInformer(informerFactory);
    registerNodeGroupInformer(informerFactory);
    registerImageHubInformer(informerFactory);
    registerResourceSetInformer(informerFactory);
    registerNamespaceInformer(informerFactory);
    registerNodeInformer(informerFactory);
    registerClusterRoleInformer(informerFactory);
    registerClusterRoleBindingInformer(informerFactory);
    registerRoleInformer(informerFactory);
    registerRoleBindingInformer(informerFactory);
    registerResourceQuotaInformer(informerFactory);
    registerSecretInformer(informerFactory);
    registerOperationInformer(informerFactory);
    registerWorkspaceInformer(informerFactory);
    registerAipubVolumeInformer(informerFactory);
    registerAipubJobInformer(informerFactory);
    registerChainJobInformer(informerFactory);
    registerSftpServerInformer(informerFactory);
    registerPodInformer(informerFactory);
    this.registrars.forEach(e -> e.registerInformer(informerFactory));

    return informerFactory;
  }

  private void registerProjectInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1alpha1Project> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getProjectApi(),
        V1alpha1Project.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.AIPUB_USER_NAME_TO_PROJECTS_INDEXER_NAME,
        project -> ProjectUtils.getSpecMembers(project).stream()
            .map(V1alpha1ProjectMember::getAipubUser)
            .filter(Objects::nonNull)
            .toList()));
    informer.addIndexers(Map.of(
        IndexerConstants.NODE_GROUP_NAME_TO_PROJECTS_INDEXER_NAME,
        ProjectUtils::getSpecBindingNodeGroups));
    informer.addIndexers(Map.of(
        IndexerConstants.NODE_NAME_TO_PROJECTS_INDEXER_NAME,
        ProjectUtils::getSpecBindingNodes));
    informer.addIndexers(Map.of(
        IndexerConstants.IMAGE_HUB_NAME_TO_PROJECTS_INDEXER_NAME,
        ProjectUtils::getSpecBindingImageHubs));
  }

  private void registerAipubUserInformer(SharedInformerFactory informerFactory) {
    informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getAipubUserApi(),
        V1alpha1AipubUser.class,
        DEFAULT_RESYNC_PERIOD);
  }

  private void registerNodeGroupInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1alpha1NodeGroup> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getNodeGroupApi(),
        V1alpha1NodeGroup.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.ALLOW_ALL_DAEMON_SETS_TO_NODE_GROUPS_INDEXER_NAME,
        nodeGroup -> NodeGroupUtils.getSpecAllowAllDaemonSets(nodeGroup) ?
            List.of(IndexerConstants.TRUE_VALUE) :
            List.of(IndexerConstants.FALSE_VALUE)));
    informer.addIndexers(Map.of(
        IndexerConstants.ALLOWED_NAMESPACE_TO_NODE_GROUPS_INDEXER_NAME,
        NodeGroupUtils::getSpecAllowedNamespaces));
    informer.addIndexers(Map.of(
        IndexerConstants.ALLOWED_DAEMON_SET_TO_NODE_GROUPS_INDEXER_NAME,
        nodeGroup -> NodeGroupUtils.getSpecAllowedDaemonSets(nodeGroup).stream()
            .filter(ref -> Objects.nonNull(ref.getNamespace()))
            .filter(ref -> Objects.nonNull(ref.getName()))
            .map(ref -> this.keyResolver.resolveKey(ref.getNamespace(), ref.getName()))
            .toList()));
    informer.addIndexers(Map.of(
        IndexerConstants.ENABLE_NODE_SELECTOR_TO_NODE_GROUPS_INDEXER_NAME,
        nodeGroup -> NodeGroupUtils.isNodeSelectorEnabled(nodeGroup) ?
            List.of(IndexerConstants.TRUE_VALUE) :
            List.of(IndexerConstants.FALSE_VALUE)));
    informer.addIndexers(Map.of(
        IndexerConstants.NODE_NAME_TO_NODE_GROUPS_INDEXER_NAME,
        NodeGroupUtils::getSpecNodes));
  }

  private void registerImageHubInformer(SharedInformerFactory informerFactory) {
    informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getImageHubApi(),
        V1alpha1ImageHub.class,
        DEFAULT_RESYNC_PERIOD);
  }

  private void registerResourceSetInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1alpha1ResourceSet> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getResourceSetApi(),
        V1alpha1ResourceSet.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.NODE_NAME_TO_RESOURCE_SETS_INDEXER_NAME,
        ResourceSetUtils::getSpecNodeNames));
  }

  private void registerNamespaceInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new CoreV1Api(apiClient).listNamespace()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1Namespace.class,
        V1NamespaceList.class);
  }

  private void registerNodeInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    SharedIndexInformer<V1Node> informer = informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new CoreV1Api(apiClient).listNode()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1Node.class,
        V1NodeList.class);
    informer.addIndexers(Map.of(
        IndexerConstants.LABEL_STRING_TO_OBJECTS_INDEXER_NAME,
        obj -> {
          Map<String, String> labels = K8sObjectUtils.getLabels(obj);
          return LabelUtils.getLabelStrings(labels);
        }));
  }

  private void registerClusterRoleInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new RbacAuthorizationV1Api(apiClient).listClusterRole()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1ClusterRole.class,
        V1ClusterRoleList.class);
  }

  private void registerClusterRoleBindingInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    SharedIndexInformer<V1ClusterRoleBinding> informer = informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new RbacAuthorizationV1Api(
            apiClient).listClusterRoleBinding()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1ClusterRoleBinding.class,
        V1ClusterRoleBindingList.class);
    informer.addIndexers(Map.of(
        IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME,
        binding -> binding.getSubjects() == null ? List.of() :
            binding.getSubjects().stream()
                .filter(s -> "User".equals(s.getKind()))
                .map(RbacV1Subject::getName)
                .filter(Objects::nonNull)
                .toList()));
    informer.addIndexers(Map.of(
        IndexerConstants.SUBJECT_GROUP_TO_BINDINGS_INDEXER_NAME,
        binding -> binding.getSubjects() == null ? List.of() :
            binding.getSubjects().stream()
                .filter(s -> "Group".equals(s.getKind()))
                .map(RbacV1Subject::getName)
                .filter(Objects::nonNull)
                .toList()));
  }

  private void registerRoleInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    SharedIndexInformer<V1Role> informer = informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new RbacAuthorizationV1Api(
            apiClient).listRoleForAllNamespaces()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1Role.class,
        V1RoleList.class);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
  }

  private void registerRoleBindingInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    SharedIndexInformer<V1RoleBinding> informer = informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new RbacAuthorizationV1Api(
            apiClient).listRoleBindingForAllNamespaces()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1RoleBinding.class,
        V1RoleBindingList.class);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
    informer.addIndexers(Map.of(
        IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME,
        binding -> binding.getSubjects() == null ? List.of() :
            binding.getSubjects().stream()
                .filter(s -> "User".equals(s.getKind()))
                .map(RbacV1Subject::getName)
                .filter(Objects::nonNull)
                .toList()));
    informer.addIndexers(Map.of(
        IndexerConstants.SUBJECT_GROUP_TO_BINDINGS_INDEXER_NAME,
        binding -> binding.getSubjects() == null ? List.of() :
            binding.getSubjects().stream()
                .filter(s -> "Group".equals(s.getKind()))
                .map(RbacV1Subject::getName)
                .filter(Objects::nonNull)
                .toList()));
  }

  private void registerResourceQuotaInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new CoreV1Api(apiClient).listResourceQuotaForAllNamespaces()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1ResourceQuota.class,
        V1ResourceQuotaList.class);
  }

  private void registerSecretInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new CoreV1Api(apiClient).listSecretForAllNamespaces()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1Secret.class,
        V1SecretList.class);
  }

  private void registerOperationInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1alpha1Operation> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getOperationApi(),
        V1alpha1Operation.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
  }

  private void registerWorkspaceInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1Workspace> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getWorkspaceApi(),
        V1Workspace.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
  }

  private void registerAipubVolumeInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1alpha1AipubVolume> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getAipubVolumeApi(),
        V1alpha1AipubVolume.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
  }

  private void registerAipubJobInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1alpha1AipubJob> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getAipubJobApi(),
        V1alpha1AipubJob.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
  }

  private void registerChainJobInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1alpha1ChainJob> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getChainJobApi(),
        V1alpha1ChainJob.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
  }

  private void registerSftpServerInformer(SharedInformerFactory informerFactory) {
    SharedIndexInformer<V1alpha1SftpServer> informer = informerFactory.sharedIndexInformerFor(
        this.k8sApiProvider.getSftpServerApi(),
        V1alpha1SftpServer.class,
        DEFAULT_RESYNC_PERIOD);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
  }

  private void registerPodInformer(SharedInformerFactory informerFactory) {
    ApiClient apiClient = this.k8sApiProvider.getApiClient();
    SharedIndexInformer<V1Pod> informer = informerFactory.sharedIndexInformerFor(
        (CallGeneratorParams params) -> new CoreV1Api(apiClient).listPodForAllNamespaces()
            .resourceVersion(params.resourceVersion)
            .watch(params.watch)
            .timeoutSeconds(params.timeoutSeconds)
            .buildCall(null),
        V1Pod.class,
        V1PodList.class);
    informer.addIndexers(Map.of(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
        obj -> List.of(K8sObjectUtils.getNamespace(obj))));
    informer.addIndexers(Map.of(
        IndexerConstants.NODE_NAME_TO_POD_INDEXER_NAME,
        obj -> WorkloadUtils.getNodeName(obj).map(List::of).orElse(List.of())));
  }

}
