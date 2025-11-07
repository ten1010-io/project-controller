package io.ten1010.aipub.projectcontroller.controller.rbac.aipub;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleBindingBuilder;
import io.kubernetes.client.openapi.models.V1RoleRef;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.AipubUserRoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.RoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ProjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.RoleUtils;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AipubUserRoleBindingReconciler extends AbstractReconciler {

  private final KeyResolver keyResolver;
  private final NamespaceNameResolver namespaceNameResolver;
  private final RoleNameResolver projectRoleNameResolver;
  private final AipubUserRoleNameResolver roleNameResolver;
  private final ReconciliationService reconciliationService;
  private final Indexer<V1RoleBinding> RoleBindingIndexer;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final Indexer<V1alpha1AipubUser> userIndexer;
  private final RbacAuthorizationV1Api rbacAuthorizationV1Api;

  public AipubUserRoleBindingReconciler(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    this.keyResolver = new KeyResolver();
    this.namespaceNameResolver = new NamespaceNameResolver();
    this.projectRoleNameResolver = new RoleNameResolver();
    this.roleNameResolver = new AipubUserRoleNameResolver();
    this.reconciliationService = reconciliationService;
    this.RoleBindingIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1RoleBinding.class)
        .getIndexer();
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.userIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1AipubUser.class)
        .getIndexer();
    this.rbacAuthorizationV1Api = new RbacAuthorizationV1Api(k8sApiProvider.getApiClient());
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    log.info("AipubUserRoleBindingReconciler reconcile request");
    String roleKey = new RequestHelper(this.keyResolver).resolveKey(request);
    Optional<V1RoleBinding> roleBindingOpt = Optional.ofNullable(
        this.RoleBindingIndexer.getByKey(roleKey));
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
      if (roleBindingOpt.isPresent()) {
        deleteRoleBinding(roleBindingOpt.get());
        log.info("point 1");
        return new Result(true, Duration.ofSeconds(5));
      }
      return new Result(false);
    }

    if (projectOpt.isEmpty()) {
      if (roleBindingOpt.isPresent()) {
        deleteRoleBinding(roleBindingOpt.get());
        return new Result(false);
      }
      return new Result(false);
    }

    List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(
        roleBindingOpt.orElse(null), userOpt.get());
    V1RoleRef reconciledRoleRef = this.reconciliationService.reconcileRoleRef(userOpt.get());
    List<RbacV1Subject> subjects = this.reconciliationService.reconcileSubjects(userOpt.get());

    if (roleBindingOpt.isPresent()) {
      String projNameFromRoleName = K8sObjectUtils.getName(projectOpt.get());
      String roleNamespace = K8sObjectUtils.getNamespace(roleBindingOpt.get());
      String projNameFromNamespace = this.namespaceNameResolver.resolveProjectName(roleNamespace);
      if (!projNameFromRoleName.equals(projNameFromNamespace)) {
        deleteRoleBinding(roleBindingOpt.get());
        return new Result(false);
      }
      System.out.println();
      if (ProjectUtils.getStatusAllBoundAipubUsers(projectOpt.get()).stream()
          .noneMatch(e -> e.equals(username))) {
        deleteRoleBinding(roleBindingOpt.get());
        return new Result(false);
      }
      return reconcileExistingRoleBinding(roleBindingOpt.get(), reconciledReferences,
          reconciledRoleRef, subjects);
    }

    if (!K8sObjectUtils.isTerminating(projectOpt.get())) {
      if (roleBindingOpt.isEmpty()) {
        if (ProjectUtils.getStatusAllBoundAipubUsers(projectOpt.get()).stream()
            .anyMatch(e -> e.equals(username))) {
          return reconcileNoExistingRoleBinding(request.getNamespace(), request.getName(),
              reconciledReferences, reconciledRoleRef, subjects);
        }
      }
    }

    return new Result(false);
  }

  private Result reconcileNoExistingRoleBinding(
      String namespace,
      String objName,
      List<V1OwnerReference> reconciledReferences,
      V1RoleRef reconciledRoleRef,
      List<RbacV1Subject> subjects) throws ApiException {
    V1RoleBinding roleBinding = new V1RoleBindingBuilder()
        .withNewMetadata()
        .withName(objName)
        .withOwnerReferences(reconciledReferences)
        .endMetadata()
        .withRoleRef(reconciledRoleRef)
        .withSubjects(subjects)
        .build();
    createRoleBinding(namespace, roleBinding);
    log.info("point 3");
    return new Result(true, Duration.ofSeconds(5));
  }

  private Result reconcileExistingRoleBinding(
      V1RoleBinding roleBinding,
      List<V1OwnerReference> reconciledReferences,
      V1RoleRef reconciledRoleRef,
      List<RbacV1Subject> reconciledSubjects) throws ApiException {
    boolean ownersEqual = Set.copyOf(K8sObjectUtils.getOwnerReferences(roleBinding))
        .equals(Set.copyOf(reconciledReferences));
    boolean roleRefEqual = java.util.Objects.equals(roleBinding.getRoleRef(), reconciledRoleRef);
    boolean subjectsEqual = Set.copyOf(RoleUtils.getSubjects(roleBinding))
        .equals(Set.copyOf(reconciledSubjects));

    if (ownersEqual && roleRefEqual && subjectsEqual) {
      return new Result(false);
    }
    V1RoleBinding edited = new V1RoleBindingBuilder(roleBinding)
        .editMetadata()
        .withOwnerReferences(reconciledReferences)
        .endMetadata()
        .withRoleRef(reconciledRoleRef)
        .withSubjects(reconciledSubjects)
        .build();
    updateRoleBinding(K8sObjectUtils.getNamespace(roleBinding), K8sObjectUtils.getName(roleBinding),
        edited);
    return new Result(false);
  }

  private void createRoleBinding(String namespace, V1RoleBinding roleBinding) throws ApiException {
    this.rbacAuthorizationV1Api
        .createNamespacedRoleBinding(namespace, roleBinding)
        .execute();
  }

  private void updateRoleBinding(String namespace, String objName, V1RoleBinding roleBinding)
      throws ApiException {
    this.rbacAuthorizationV1Api
        .replaceNamespacedRoleBinding(objName, namespace, roleBinding)
        .execute();
  }

  private void deleteRoleBinding(String namespace, String objName) throws ApiException {
    this.rbacAuthorizationV1Api
        .deleteNamespacedRoleBinding(objName, namespace)
        .execute();
  }

  private void deleteRoleBinding(V1RoleBinding roleBinding) throws ApiException {
    deleteRoleBinding(K8sObjectUtils.getNamespace(roleBinding),
        K8sObjectUtils.getName(roleBinding));
  }

}
