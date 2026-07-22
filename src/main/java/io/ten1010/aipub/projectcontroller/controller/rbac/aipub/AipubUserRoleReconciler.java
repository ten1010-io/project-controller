package io.ten1010.aipub.projectcontroller.controller.rbac.aipub;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBuilder;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.AipubUserRoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.RoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1beta1Workspace;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubVolume;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ChainJob;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageBuild;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Operation;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1SftpServer;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ProjectUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

public class AipubUserRoleReconciler extends AbstractReconciler {

  private final KeyResolver keyResolver;
  private final NamespaceNameResolver namespaceNameResolver;
  private final RoleNameResolver projectRoleNameResolver;
  private final AipubUserRoleNameResolver roleNameResolver;
  private final ReconciliationService reconciliationService;
  private final Indexer<V1Role> roleIndexer;
  private final Indexer<V1alpha1AipubUser> userIndexer;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final Indexer<V1beta1Workspace> v1beta1WorkspaceIndexer;
  private final Indexer<V1alpha1ChainJob> chainJobIndexer;
  private final Indexer<V1Job> jobIndexer;
  private final Indexer<V1CronJob> cronJobIndexer;
  private final Indexer<V1alpha1Operation> operationIndexer;
  private final Indexer<V1alpha1AipubVolume> aipubVolumeIndexer;
  private final Indexer<V1alpha1SftpServer> sftpServerIndexer;
  private final Indexer<V1alpha1ImageBuild> imageBuildIndexer;
  private final BoundObjectResolver boundObjectResolver;
  private final RbacAuthorizationV1Api rbacAuthorizationV1Api;

  public AipubUserRoleReconciler(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService
  ) {
    this.keyResolver = new KeyResolver();
    this.namespaceNameResolver = new NamespaceNameResolver();
    this.projectRoleNameResolver = new RoleNameResolver();
    this.roleNameResolver = new AipubUserRoleNameResolver();
    this.reconciliationService = reconciliationService;
    this.roleIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Role.class)
        .getIndexer();
    this.userIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1AipubUser.class)
        .getIndexer();
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.v1beta1WorkspaceIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1beta1Workspace.class)
        .getIndexer();
    this.chainJobIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1ChainJob.class)
        .getIndexer();
    this.jobIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Job.class)
        .getIndexer();
    this.cronJobIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1CronJob.class)
        .getIndexer();
    this.operationIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Operation.class)
        .getIndexer();
    this.aipubVolumeIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1AipubVolume.class)
        .getIndexer();
    this.sftpServerIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1SftpServer.class)
        .getIndexer();
    this.imageBuildIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1ImageBuild.class)
        .getIndexer();
    this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
    this.rbacAuthorizationV1Api = new RbacAuthorizationV1Api(k8sApiProvider.getApiClient());
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    String roleKey = new RequestHelper(this.keyResolver).resolveKey(request);
    Optional<V1Role> roleOpt = Optional.ofNullable(this.roleIndexer.getByKey(roleKey));
    String projKey = this.keyResolver.resolveKey(
        this.namespaceNameResolver.resolveProjectName(request.getNamespace()));
    Optional<V1alpha1Project> projectOpt = Optional.ofNullable(
        this.projectIndexer.getByKey(projKey));

    Optional<String> aipubUserNameOpt = this.roleNameResolver.resolveAipubUserName(
        request.getName());
    if (aipubUserNameOpt.isEmpty()) {
      return new Result(false);
    }

    String username = aipubUserNameOpt.get();
    Optional<V1alpha1AipubUser> userOpt = Optional.ofNullable(
        this.userIndexer.getByKey(this.keyResolver.resolveKey(username)));
    if (userOpt.isEmpty()) {
      if (roleOpt.isPresent()) {
        deleteRole(roleOpt.get());
        return new Result(true, Duration.ofSeconds(5));
      }
      return new Result(false);
    }

    if (projectOpt.isEmpty()) {
      if (roleOpt.isPresent()) {
        deleteRole(roleOpt.get());
        return new Result(false);
      }
      return new Result(false);
    }

    List<KubernetesObject> workloads = new ArrayList<>();
    List<V1beta1Workspace> v1beta1Workspaces = this.v1beta1WorkspaceIndexer.byIndex(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME, request.getNamespace());
    List<V1alpha1ChainJob> chainJobs = this.chainJobIndexer.byIndex(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME, request.getNamespace());
    List<V1Job> jobs = this.jobIndexer.byIndex(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME, request.getNamespace());
    List<V1CronJob> cronJobs = this.cronJobIndexer.byIndex(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME, request.getNamespace());
    List<V1alpha1Operation> operations = this.operationIndexer.byIndex(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME, request.getNamespace());
    List<V1alpha1AipubVolume> aipubVolumes = this.aipubVolumeIndexer.byIndex(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME, request.getNamespace());
    List<V1alpha1SftpServer> sftpServers = this.sftpServerIndexer.byIndex(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME, request.getNamespace());
    List<V1alpha1ImageBuild> imageBuilds = this.imageBuildIndexer.byIndex(
        IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME, request.getNamespace());
    workloads.addAll(v1beta1Workspaces);
    workloads.addAll(chainJobs);
    workloads.addAll(jobs);
    workloads.addAll(cronJobs);
    workloads.addAll(operations);
    workloads.addAll(aipubVolumes);
    workloads.addAll(sftpServers);
    workloads.addAll(imageBuilds);

    List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(
        roleOpt.orElse(null), userOpt.get());
    List<V1PolicyRule> reconciledRules = this.reconciliationService.reconcileAipubUserRoleRules(
        userOpt.get(), projectOpt.get(), workloads);

    if (roleOpt.isPresent()) {
      String projNameFromRoleName = K8sObjectUtils.getName(projectOpt.get());
      String roleNamespace = K8sObjectUtils.getNamespace(roleOpt.get());
      String projNameFromNamespace = this.namespaceNameResolver.resolveProjectName(roleNamespace);
      if (!projNameFromRoleName.equals(projNameFromNamespace)) {
        deleteRole(roleOpt.get());
        return new Result(false);
      }
      if (ProjectUtils.getStatusAllBoundAipubUsers(projectOpt.get()).stream()
          .noneMatch(e -> e.equals(username))) {
        deleteRole(roleOpt.get());
        return new Result(false);
      }
      return reconcileExistingRole(roleOpt.get(), reconciledReferences, reconciledRules);
    }

    if (!K8sObjectUtils.isTerminating(projectOpt.get())) {
      if (roleOpt.isEmpty()) {
        if (ProjectUtils.getStatusAllBoundAipubUsers(projectOpt.get()).stream()
            .anyMatch(e -> e.equals(username))) {
          return reconcileNoExistingRole(request.getNamespace(), request.getName(),
              reconciledReferences, reconciledRules);
        }
      }
    }

    return new Result(false);
  }

  private Result reconcileNoExistingRole(
      String namespace,
      String objName,
      List<V1OwnerReference> reconciledReferences,
      List<V1PolicyRule> reconciledRules) throws ApiException {
    V1Role role = new V1RoleBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .withName(objName)
        .withOwnerReferences(reconciledReferences)
        .endMetadata()
        .withRules(reconciledRules)
        .build();
    createRole(namespace, role);
    return new Result(true, Duration.ofSeconds(5));
  }

  private Result reconcileExistingRole(
      V1Role role,
      List<V1OwnerReference> reconciledReferences,
      List<V1PolicyRule> reconciledRules) throws ApiException {
    boolean ownersEqual = Set.copyOf(K8sObjectUtils.getOwnerReferences(role))
        .equals(Set.copyOf(reconciledReferences));
    boolean rulesEqual =
        new java.util.HashSet<>(Optional.ofNullable(role.getRules()).orElse(List.of()))
            .equals(new java.util.HashSet<>(reconciledRules));
    if (ownersEqual && rulesEqual) {
      return new Result(false);
    }
    V1Role edited = new V1RoleBuilder(role)
        .editMetadata()
        .withOwnerReferences(reconciledReferences)
        .endMetadata()
        .withRules(reconciledRules)
        .build();
    updateRole(K8sObjectUtils.getNamespace(role), K8sObjectUtils.getName(role), edited);
    return new Result(false);
  }

  private void createRole(String namespace, V1Role role) throws ApiException {
    this.rbacAuthorizationV1Api
        .createNamespacedRole(namespace, role)
        .execute();
  }

  private void updateRole(String namespace, String objName, V1Role role) throws ApiException {
    this.rbacAuthorizationV1Api
        .replaceNamespacedRole(objName, namespace, role)
        .execute();
  }

  private void deleteRole(String namespace, String objName) throws ApiException {
    this.rbacAuthorizationV1Api
        .deleteNamespacedRole(objName, namespace)
        .execute();
  }

  private void deleteRole(V1Role object) throws ApiException {
    deleteRole(K8sObjectUtils.getNamespace(object), K8sObjectUtils.getName(object));
  }
}
