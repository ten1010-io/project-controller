package io.ten1010.aipub.projectcontroller.controller.rbac.aipub;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1AggregationRule;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBuilder;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.AipubUserRoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.RoleUtils;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class AipubUserClusterRoleReconciler extends AbstractReconciler {

  private final KeyResolver keyResolver;
  private final AipubUserRoleNameResolver roleNameResolver;
  private final ReconciliationService reconciliationService;
  private final Indexer<V1ClusterRole> clusterRoleIndexer;
  private final Indexer<V1alpha1AipubUser> userIndexer;
  private final RbacAuthorizationV1Api rbacAuthorizationV1Api;

  public AipubUserClusterRoleReconciler(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    this.keyResolver = new KeyResolver();
    this.roleNameResolver = new AipubUserRoleNameResolver();
    this.reconciliationService = reconciliationService;
    this.clusterRoleIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1ClusterRole.class)
        .getIndexer();
    this.userIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1AipubUser.class)
        .getIndexer();
    this.rbacAuthorizationV1Api = new RbacAuthorizationV1Api(k8sApiProvider.getApiClient());
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    Optional<String> userNameOpt = this.roleNameResolver.resolveAipubUserName(request.getName());
    if (userNameOpt.isEmpty()) {
      return new Result(false);
    }
    String userName = userNameOpt.get();

    String roleKey = new RequestHelper(this.keyResolver).resolveKey(request);
    Optional<V1ClusterRole> roleOpt = Optional.ofNullable(
        this.clusterRoleIndexer.getByKey(roleKey));
    String userKey = this.keyResolver.resolveKey(userName);
    Optional<V1alpha1AipubUser> userOpt = Optional.ofNullable(this.userIndexer.getByKey(userKey));

    if (userOpt.isEmpty()) {
      if (roleOpt.isPresent()) {
        V1ClusterRole role = roleOpt.get();
        deleteRole(K8sObjectUtils.getName(role));
        return new Result(true, Duration.ofSeconds(5));
      }
      return new Result(false);
    }

    List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(
        roleOpt.orElse(null), userOpt.get());
    List<V1PolicyRule> reconciledRules = this.reconciliationService.reconcileClusterRoleRules(
        userOpt.get());
    V1AggregationRule reconciledAggregationRule = this.reconciliationService.reconcileClusterRoleAggregationRule(
        userOpt.get());

    if (roleOpt.isPresent()) {
      return reconcileExistingRole(roleOpt.get(), reconciledReferences, reconciledRules,
          reconciledAggregationRule);
    }

    if (!K8sObjectUtils.isTerminating(userOpt.get())) {
      return reconcileNoExistingRole(request.getName(), reconciledReferences, reconciledRules,
          reconciledAggregationRule);
    }

    return new Result(false);
  }

  private Result reconcileNoExistingRole(
      String objName,
      List<V1OwnerReference> reconciledReferences,
      List<V1PolicyRule> reconciledRules,
      @Nullable V1AggregationRule reconciledAggregationRule) throws ApiException {
    V1ClusterRole role = new V1ClusterRoleBuilder()
        .withNewMetadata()
        .withName(objName)
        .withOwnerReferences(reconciledReferences)
        .endMetadata()
        .withRules(reconciledRules)
        .withAggregationRule(reconciledAggregationRule)
        .build();
    createRole(role);
    return new Result(true, Duration.ofSeconds(5));
  }

  private Result reconcileExistingRole(
      V1ClusterRole clusterRole,
      List<V1OwnerReference> reconciledReferences,
      List<V1PolicyRule> reconciledRules,
      @Nullable V1AggregationRule reconciledAggregationRule) throws ApiException {
    if (Set.copyOf(K8sObjectUtils.getOwnerReferences(clusterRole))
        .equals(Set.copyOf(reconciledReferences)) &&
        RoleUtils.getRules(clusterRole).equals(reconciledRules) &&
        Objects.equals(clusterRole.getAggregationRule(), reconciledAggregationRule)) {
      return new Result(false);
    }
    V1ClusterRole edited = new V1ClusterRoleBuilder(clusterRole)
        .editMetadata()
        .withOwnerReferences(reconciledReferences)
        .endMetadata()
        .withRules(reconciledRules)
        .withAggregationRule(reconciledAggregationRule)
        .build();
    updateRole(K8sObjectUtils.getName(clusterRole), edited);
    return new Result(false);
  }

  private void createRole(V1ClusterRole clusterRole) throws ApiException {
    this.rbacAuthorizationV1Api
        .createClusterRole(clusterRole)
        .execute();
  }

  private void updateRole(String objName, V1ClusterRole clusterRole) throws ApiException {
    this.rbacAuthorizationV1Api
        .replaceClusterRole(objName, clusterRole)
        .execute();
  }

  private void deleteRole(String objName) throws ApiException {
    this.rbacAuthorizationV1Api
        .deleteClusterRole(objName)
        .execute();
  }

}
